package fr.prix3d.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Prix3DTheme {
                CalculatorScreen()
            }
        }
    }
}

/** Champs de saisie. Les valeurs par défaut sont des valeurs courantes (FDM / PLA). */
enum class Field(
    val label: String,
    val unit: String,
    val default: String,
    val numeric: Boolean = true,
    val integer: Boolean = false,
) {
    NAME("Nom de la pièce", "", "", numeric = false),
    QUANTITY("Quantité", "pcs", "1", integer = true),

    WEIGHT("Poids de la pièce (supports inclus)", "g", "50"),
    FILAMENT_PRICE("Prix de la bobine", "€/kg", "20"),

    PRINT_HOURS("Durée d'impression – heures", "h", "3", integer = true),
    PRINT_MINUTES("Durée d'impression – minutes", "min", "0", integer = true),
    POWER("Puissance moyenne de l'imprimante", "W", "150"),
    KWH_PRICE("Prix de l'électricité", "€/kWh", "0,25"),

    PRINTER_PRICE("Prix d'achat de l'imprimante", "€", "400"),
    LIFETIME("Durée de vie estimée", "h", "5000"),
    MAINTENANCE("Entretien (buse, plateau…)", "€/h", "0,05"),

    LABOR_MINUTES("Temps de travail (préparation, finition)", "min", "15"),
    LABOR_RATE("Taux horaire", "€/h", "20"),

    FAILURE("Majoration pour échecs", "%", "10"),
    EXTRAS("Frais supplémentaires (emballage, peinture…)", "€", "0"),
    MARGIN("Marge bénéficiaire (sur le coût)", "%", "50"),
    VAT("TVA (0 si franchise en base)", "%", "20"),
}

private val sections: List<Pair<String, List<Field>>> = listOf(
    "Pièce" to listOf(Field.NAME, Field.QUANTITY),
    "Matière" to listOf(Field.WEIGHT, Field.FILAMENT_PRICE),
    "Impression & électricité" to listOf(Field.PRINT_HOURS, Field.PRINT_MINUTES, Field.POWER, Field.KWH_PRICE),
    "Machine" to listOf(Field.PRINTER_PRICE, Field.LIFETIME, Field.MAINTENANCE),
    "Main d'œuvre" to listOf(Field.LABOR_MINUTES, Field.LABOR_RATE),
    "Frais, marge & TVA" to listOf(Field.FAILURE, Field.EXTRAS, Field.MARGIN, Field.VAT),
)

private const val PREFS = "prix3d"

private fun Map<Field, String>.num(f: Field) = PriceCalculator.parse(this[f] ?: "")

private fun Map<Field, String>.toInputs() = PrintInputs(
    quantity = num(Field.QUANTITY).toInt(),
    weightGrams = num(Field.WEIGHT),
    filamentPricePerKg = num(Field.FILAMENT_PRICE),
    printHours = num(Field.PRINT_HOURS),
    printMinutes = num(Field.PRINT_MINUTES),
    printerPowerWatts = num(Field.POWER),
    electricityPricePerKwh = num(Field.KWH_PRICE),
    printerPrice = num(Field.PRINTER_PRICE),
    printerLifetimeHours = num(Field.LIFETIME),
    maintenancePerHour = num(Field.MAINTENANCE),
    laborMinutes = num(Field.LABOR_MINUTES),
    laborRatePerHour = num(Field.LABOR_RATE),
    failureRatePercent = num(Field.FAILURE),
    extraCosts = num(Field.EXTRAS),
    marginPercent = num(Field.MARGIN),
    vatPercent = num(Field.VAT),
)

fun euros(v: Double): String = String.format(Locale.FRANCE, "%,.2f €", v)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalculatorScreen() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    val values = remember {
        mutableStateMapOf<Field, String>().apply {
            Field.entries.forEach { put(it, prefs.getString(it.name, it.default) ?: it.default) }
        }
    }
    var showReset by remember { mutableStateOf(false) }

    fun update(field: Field, text: String) {
        values[field] = text
        prefs.edit().putString(field.name, text).apply()
    }

    val result = PriceCalculator.compute(values.toInputs())

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Prix d'une pièce 3D") },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ),
                actions = {
                    IconButton(onClick = { shareResult(context, values[Field.NAME].orEmpty(), result) }) {
                        Icon(Icons.Filled.Share, contentDescription = "Partager")
                    }
                    IconButton(onClick = { showReset = true }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Réinitialiser")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ResultCard(result)

            sections.forEach { (title, fields) ->
                SectionCard(title) {
                    fields.forEach { f ->
                        InputField(f, values[f].orEmpty()) { update(f, it) }
                    }
                }
            }

            BreakdownCard(result)
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showReset) {
        AlertDialog(
            onDismissRequest = { showReset = false },
            title = { Text("Réinitialiser ?") },
            text = { Text("Toutes les valeurs reviendront à leurs réglages par défaut.") },
            confirmButton = {
                TextButton(onClick = {
                    Field.entries.forEach { update(it, it.default) }
                    showReset = false
                }) { Text("Réinitialiser") }
            },
            dismissButton = {
                TextButton(onClick = { showReset = false }) { Text("Annuler") }
            },
        )
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

@Composable
private fun InputField(field: Field, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { new ->
            val ok = when {
                !field.numeric -> true
                field.integer -> new.all { it.isDigit() }
                else -> new.all { it.isDigit() || it == ',' || it == '.' }
            }
            if (ok) onChange(new)
        },
        label = { Text(field.label) },
        suffix = if (field.unit.isNotEmpty()) ({ Text(field.unit) }) else null,
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = when {
                !field.numeric -> KeyboardType.Text
                field.integer -> KeyboardType.Number
                else -> KeyboardType.Decimal
            },
            imeAction = ImeAction.Next,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ResultCard(r: PriceBreakdown) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Prix de vente conseillé (TTC)", style = MaterialTheme.typography.titleSmall)
            Text(
                euros(r.roundedPriceInclTax),
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
            )
            Text("par pièce", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
            Text("Coût de revient : ${euros(r.costPrice)}  ·  HT : ${euros(r.priceExclTax)}")
            if (r.quantity > 1) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Total ${r.quantity} pièces : ${euros(r.totalInclTax)} TTC",
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun BreakdownCard(r: PriceBreakdown) {
    SectionCard("Détail du calcul (par pièce)") {
        Line("Matière", r.material)
        Line("Électricité", r.electricity)
        Line("Usure & entretien machine", r.machine)
        Line("Main d'œuvre", r.labor)
        Line("Majoration échecs", r.failure)
        Line("Frais supplémentaires", r.extras)
        HorizontalDivider()
        Line("Coût de revient", r.costPrice, bold = true)
        Line("Marge", r.margin)
        Line("Prix HT", r.priceExclTax, bold = true)
        Line("TVA", r.vat)
        Line("Prix TTC exact", r.priceInclTax, bold = true)
        if (r.quantity > 1) {
            HorizontalDivider()
            Line("Total HT (${r.quantity} pcs)", r.totalExclTax)
            Line("Total TTC (${r.quantity} pcs)", r.totalInclTax, bold = true)
        }
    }
}

@Composable
private fun Line(label: String, value: Double, bold: Boolean = false) {
    val weight = if (bold) FontWeight.Bold else FontWeight.Normal
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontWeight = weight)
        Text(euros(value), fontWeight = weight)
    }
}

private fun shareResult(context: Context, name: String, r: PriceBreakdown) {
    val title = name.ifBlank { "Pièce imprimée en 3D" }
    val text = buildString {
        appendLine(title)
        appendLine("Prix de vente : ${euros(r.roundedPriceInclTax)} TTC / pièce")
        appendLine("(HT : ${euros(r.priceExclTax)}, coût de revient : ${euros(r.costPrice)})")
        if (r.quantity > 1) appendLine("Total ${r.quantity} pièces : ${euros(r.totalInclTax)} TTC")
    }
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, title)
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, "Partager le prix"))
}

private val LightColors = lightColorScheme(
    primary = Color(0xFF1E5AA8),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E3FF),
    onPrimaryContainer = Color(0xFF001B3E),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA9C7FF),
    onPrimary = Color(0xFF003063),
    primaryContainer = Color(0xFF00468C),
    onPrimaryContainer = Color(0xFFD6E3FF),
)

@Composable
fun Prix3DTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
