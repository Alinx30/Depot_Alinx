package fr.prix3d.app

import kotlin.math.ceil
import kotlin.math.max

/** Données saisies pour une pièce imprimée en 3D. */
data class PrintInputs(
    val quantity: Int = 1,
    val designHours: Double = 0.0,
    val designMinutes: Double = 0.0,
    val designRatePerHour: Double = 0.0,
    val weightGrams: Double = 0.0,
    val filamentPricePerKg: Double = 0.0,
    val printHours: Double = 0.0,
    val printMinutes: Double = 0.0,
    val printerPowerWatts: Double = 0.0,
    val electricityPricePerKwh: Double = 0.0,
    val printerPrice: Double = 0.0,
    val printerLifetimeHours: Double = 0.0,
    val maintenancePerHour: Double = 0.0,
    val laborMinutes: Double = 0.0,
    val laborRatePerHour: Double = 0.0,
    val failureRatePercent: Double = 0.0,
    val extraCosts: Double = 0.0,
    val marginPercent: Double = 0.0,
    val vatPercent: Double = 0.0,
)

/** Détail du prix, pour UNE pièce (sauf les champs "total"). */
data class PriceBreakdown(
    val design: Double,
    val designTotal: Double,
    val material: Double,
    val electricity: Double,
    val machine: Double,
    val labor: Double,
    val failure: Double,
    val extras: Double,
    val costPrice: Double,
    val margin: Double,
    val priceExclTax: Double,
    val vat: Double,
    val priceInclTax: Double,
    val roundedPriceInclTax: Double,
    val quantity: Int,
    val totalExclTax: Double,
    val totalInclTax: Double,
)

object PriceCalculator {

    fun compute(input: PrintInputs): PriceBreakdown {
        val qty = max(1, input.quantity)

        // Conception (modélisation 3D) : faite une seule fois pour toute la commande,
        // puis répartie sur le nombre de pièces.
        val designHoursTotal = max(0.0, input.designHours) + max(0.0, input.designMinutes) / 60.0
        val designTotal = designHoursTotal * input.designRatePerHour
        val design = designTotal / qty

        val hours = max(0.0, input.printHours) + max(0.0, input.printMinutes) / 60.0

        // Matière : poids (g) -> kg × prix de la bobine au kg
        val material = input.weightGrams / 1000.0 * input.filamentPricePerKg

        // Électricité : puissance (kW) × durée (h) × prix du kWh
        val electricity = input.printerPowerWatts / 1000.0 * hours * input.electricityPricePerKwh

        // Usure machine : amortissement de l'imprimante + entretien, par heure d'impression
        val depreciationPerHour =
            if (input.printerLifetimeHours > 0) input.printerPrice / input.printerLifetimeHours else 0.0
        val machine = (depreciationPerHour + input.maintenancePerHour) * hours

        // Main d'œuvre : préparation, découpe (slicing), post-traitement…
        val labor = input.laborMinutes / 60.0 * input.laborRatePerHour

        // Majoration pour impressions ratées, appliquée aux coûts de production
        val failure = (material + electricity + machine) * input.failureRatePercent / 100.0

        val extras = input.extraCosts
        val costPrice = design + material + electricity + machine + labor + failure + extras

        val margin = costPrice * input.marginPercent / 100.0
        val priceExclTax = costPrice + margin
        val vat = priceExclTax * input.vatPercent / 100.0
        val priceInclTax = priceExclTax + vat

        // Prix conseillé : arrondi aux 10 centimes supérieurs
        val rounded = ceil(priceInclTax * 10.0 - 1e-9) / 10.0

        return PriceBreakdown(
            design = design,
            designTotal = designTotal,
            material = material,
            electricity = electricity,
            machine = machine,
            labor = labor,
            failure = failure,
            extras = extras,
            costPrice = costPrice,
            margin = margin,
            priceExclTax = priceExclTax,
            vat = vat,
            priceInclTax = priceInclTax,
            roundedPriceInclTax = rounded,
            quantity = qty,
            totalExclTax = priceExclTax * qty,
            totalInclTax = priceInclTax * qty,
        )
    }

    /** Convertit une saisie utilisateur ("12,5", "12.5", "") en nombre. */
    fun parse(text: String): Double =
        text.trim().replace(" ", "").replace(',', '.').toDoubleOrNull() ?: 0.0
}
