package fr.prix3d.app

import org.junit.Assert.assertEquals
import org.junit.Test

class PriceCalculatorTest {

    private val eps = 1e-6

    @Test
    fun calculComplet() {
        val r = PriceCalculator.compute(
            PrintInputs(
                quantity = 2,
                weightGrams = 100.0,          // 0,1 kg
                filamentPricePerKg = 20.0,    // -> 2,00 €
                printHours = 5.0,
                printMinutes = 0.0,
                printerPowerWatts = 200.0,    // 0,2 kW × 5 h × 0,25 -> 0,25 €
                electricityPricePerKwh = 0.25,
                printerPrice = 500.0,         // 0,10 €/h
                printerLifetimeHours = 5000.0,
                maintenancePerHour = 0.10,    // (0,10 + 0,10) × 5 -> 1,00 €
                laborMinutes = 30.0,          // 0,5 h × 20 -> 10,00 €
                laborRatePerHour = 20.0,
                failureRatePercent = 10.0,    // 10 % de 3,25 -> 0,325 €
                extraCosts = 1.0,
                marginPercent = 50.0,
                vatPercent = 20.0,
            )
        )
        assertEquals(2.0, r.material, eps)
        assertEquals(0.25, r.electricity, eps)
        assertEquals(1.0, r.machine, eps)
        assertEquals(10.0, r.labor, eps)
        assertEquals(0.325, r.failure, eps)
        assertEquals(14.575, r.costPrice, eps)
        assertEquals(21.8625, r.priceExclTax, eps)
        assertEquals(26.235, r.priceInclTax, eps)
        assertEquals(26.3, r.roundedPriceInclTax, eps)
        assertEquals(52.47, r.totalInclTax, eps)
    }

    @Test
    fun saisieAvecVirguleEtVide() {
        assertEquals(12.5, PriceCalculator.parse("12,5"), eps)
        assertEquals(12.5, PriceCalculator.parse(" 12.5 "), eps)
        assertEquals(0.0, PriceCalculator.parse(""), eps)
        assertEquals(0.0, PriceCalculator.parse("abc"), eps)
    }

    @Test
    fun dureeDeVieNulleNePlantePas() {
        val r = PriceCalculator.compute(PrintInputs(printHours = 2.0, printerPrice = 300.0))
        assertEquals(0.0, r.machine, eps)
        assertEquals(1, r.quantity)
    }
}
