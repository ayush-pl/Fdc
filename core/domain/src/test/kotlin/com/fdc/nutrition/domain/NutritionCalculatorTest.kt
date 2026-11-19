package com.fdc.nutrition.domain

import com.fdc.nutrition.domain.calc.*
import com.fdc.nutrition.domain.model.*
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

class NutritionCalculatorTest {
    private val tol = 1e-9

    @Test fun `150 g of cooked rice matches the specification example`() {
        val n = NutritionCalculator.forAmount(Fx.rice.nutrientsPer100, 150.0)
        assertThat(n.energyKcal!!).isWithin(tol).of(195.0)
        assertThat(n.proteinG!!).isWithin(tol).of(4.05)
        assertThat(n.carbohydrateG!!).isWithin(tol).of(42.0)
        assertThat(n.fatG!!).isWithin(tol).of(0.45)
    }

    @Test fun `zero quantity gives zero for known nutrients and keeps unknowns unknown`() {
        val n = NutritionCalculator.forAmount(Fx.rice.nutrientsPer100, 0.0)
        assertThat(n.energyKcal).isEqualTo(0.0)
        assertThat(n[Nutrient.FIBRE]).isNull()
    }

    @Test fun `missing nutrients are not converted to zero when scaling`() {
        val n = NutritionCalculator.forAmount(Fx.rice.nutrientsPer100, 250.0)
        assertThat(n.has(Nutrient.VITAMIN_C)).isFalse()
        assertThat(n[Nutrient.VITAMIN_C]).isNull()
    }

    @Test fun `negative or non-finite amounts are rejected`() {
        assertThrows(IllegalArgumentException::class.java) { NutritionCalculator.forAmount(Fx.rice.nutrientsPer100, -1.0) }
        assertThrows(IllegalArgumentException::class.java) { NutritionCalculator.forAmount(Fx.rice.nutrientsPer100, Double.NaN) }
    }

    @Test fun `non-100 reference amounts scale correctly`() {
        val perServing = Nutrients.of(Nutrient.ENERGY to 250.0)
        assertThat(NutritionCalculator.forAmount(perServing, 1.5, 1.0).energyKcal!!).isWithin(tol).of(375.0)
    }

    @Test fun `strict plus only keeps nutrients known on both sides`() {
        val a = Nutrients.of(Nutrient.ENERGY to 100.0, Nutrient.FIBRE to 2.0)
        val b = Nutrients.of(Nutrient.ENERGY to 50.0)
        val s = a + b
        assertThat(s.energyKcal).isEqualTo(150.0)
        assertThat(s[Nutrient.FIBRE]).isNull()
    }

    @Test fun `energy mismatch warns for inconsistent labels only`() {
        assertThat(NutritionCalculator.energyMismatch(Fx.rice.nutrientsPer100)).isNull()
        val bad = Nutrients.of(Nutrient.ENERGY to 500.0, Nutrient.PROTEIN to 2.0, Nutrient.CARBOHYDRATE to 10.0, Nutrient.FAT to 1.0)
        assertThat(NutritionCalculator.energyMismatch(bad)).isNotNull()
    }

    @Test fun `presentation rounding does not affect stored precision`() {
        assertThat(NutrientFormat.format(4.05, Nutrient.PROTEIN)).isEqualTo("4.05 g")
        assertThat(NutrientFormat.format(195.4, Nutrient.ENERGY)).isEqualTo("195 kcal")
        assertThat(NutrientFormat.format(42.04, Nutrient.CARBOHYDRATE)).isEqualTo("42 g")
        assertThat(NutrientFormat.format(null, Nutrient.IRON)).isEqualTo("—")
    }
}
