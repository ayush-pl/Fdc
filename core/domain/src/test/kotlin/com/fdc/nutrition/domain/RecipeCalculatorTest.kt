package com.fdc.nutrition.domain

import com.fdc.nutrition.domain.calc.RecipeCalculator
import com.fdc.nutrition.domain.model.*
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

class RecipeCalculatorTest {
    private fun ing(name: String, grams: Double, vararg n: Pair<Nutrient, Double>) = RecipeIngredient(
        id = name, foodId = name, foodName = name, quantity = grams, unit = QuantityUnit.GRAM, unitLabel = "g", amountPerUnit = 1.0,
        basis = NutrientBasis.GRAM, nutrientsPer100 = Nutrients.of(*n), preparation = PreparationState.RAW,
    )
    // Paneer bhurji example from the spec (values per 100 g are illustrative fixtures)
    private val paneer = ing("Paneer", 150.0, Nutrient.ENERGY to 321.0, Nutrient.PROTEIN to 21.4, Nutrient.CARBOHYDRATE to 3.6, Nutrient.FAT to 25.0, Nutrient.FIBRE to 0.0)
    private val onion = ing("Onion", 50.0, Nutrient.ENERGY to 40.0, Nutrient.PROTEIN to 1.1, Nutrient.CARBOHYDRATE to 9.34, Nutrient.FAT to 0.1, Nutrient.FIBRE to 1.7)
    private val tomato = ing("Tomato", 80.0, Nutrient.ENERGY to 18.0, Nutrient.PROTEIN to 0.88, Nutrient.CARBOHYDRATE to 3.89, Nutrient.FAT to 0.2, Nutrient.FIBRE to 1.2)
    private val oil = ing("Oil", 5.0, Nutrient.ENERGY to 884.0, Nutrient.PROTEIN to 0.0, Nutrient.CARBOHYDRATE to 0.0, Nutrient.FAT to 100.0)

    @Test fun `totals are the sum of ingredient contributions`() {
        val r = RecipeCalculator.calculate(listOf(paneer, onion, tomato, oil), servings = 2.0, finalWeightGrams = null)
        val expectedKcal = 321 * 1.5 + 40 * 0.5 + 18 * 0.8 + 884 * 0.05
        assertThat(r.total.energyKcal!!).isWithin(1e-9).of(expectedKcal)
        assertThat(r.total.proteinG!!).isWithin(1e-9).of(21.4 * 1.5 + 1.1 * 0.5 + 0.88 * 0.8)
        assertThat(r.perServing.energyKcal!!).isWithin(1e-9).of(expectedKcal / 2)
        assertThat(r.rawIngredientWeightG).isEqualTo(285.0)
        assertThat(r.per100g!!.energyKcal!!).isWithin(1e-9).of(expectedKcal / 285.0 * 100)
        assertThat(r.servingWeightG).isEqualTo(142.5)
    }

    @Test fun `recorded final weight is used for per-100 g figures`() {
        val r = RecipeCalculator.calculate(listOf(paneer, onion, tomato, oil), servings = 2.0, finalWeightGrams = 240.0)
        assertThat(r.usesRecordedFinalWeight).isTrue()
        assertThat(r.per100g!!.energyKcal!!).isWithin(1e-9).of(r.total.energyKcal!! / 240.0 * 100)
        assertThat(r.servingWeightG).isEqualTo(120.0)
        // Per-serving nutrition does not depend on weight
        assertThat(r.perServing.energyKcal!!).isWithin(1e-9).of(r.total.energyKcal!! / 2)
    }

    @Test fun `ingredient missing a nutrient is reflected in coverage`() {
        val r = RecipeCalculator.calculate(listOf(paneer, onion, tomato, oil), 2.0, null)
        assertThat(r.totals[Nutrient.FIBRE].missingCount).isEqualTo(1)
    }

    @Test fun `per 100 g is unavailable when weight cannot be determined`() {
        val ml = onion.copy(basis = NutrientBasis.MILLILITRE, densityGPerMl = null)
        val r = RecipeCalculator.calculate(listOf(paneer, ml), 1.0, null)
        assertThat(r.per100g).isNull()
        assertThat(RecipeCalculator.calculate(listOf(paneer, ml), 1.0, 300.0).per100g).isNotNull()
    }

    @Test fun `servings must be positive`() {
        assertThrows(IllegalArgumentException::class.java) { RecipeCalculator.calculate(listOf(paneer), 0.0, null) }
    }
}
