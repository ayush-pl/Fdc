package com.fdc.nutrition.domain

import com.fdc.nutrition.domain.calc.*
import com.fdc.nutrition.domain.model.*
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalDate

class AggregationTest {
    private val day = LocalDate.of(2026, 1, 10)
    private val dal = Nutrients.of(Nutrient.ENERGY to 116.0, Nutrient.PROTEIN to 9.0, Nutrient.CARBOHYDRATE to 20.0, Nutrient.FAT to 0.4, Nutrient.FIBRE to 7.9)

    @Test fun `daily totals add up entries across meals`() {
        val entries = listOf(
            Fx.entry(day, Meal.LUNCH, "Rice", 150.0),
            Fx.entry(day, Meal.LUNCH, "Dal", 200.0, dal),
            Fx.entry(day, Meal.DINNER, "Rice", 100.0),
        )
        val t = NutrientAggregator.sumEntries(entries)
        assertThat(t[Nutrient.ENERGY].amount).isWithin(1e-9).of(195.0 + 232.0 + 130.0)
        assertThat(t[Nutrient.PROTEIN].amount).isWithin(1e-9).of(4.05 + 18.0 + 2.7)
        assertThat(t[Nutrient.ENERGY].isComplete).isTrue()
    }

    @Test fun `missing nutrient data is reported as partial coverage, not zero`() {
        val entries = listOf(Fx.entry(day, name = "Rice", grams = 100.0), Fx.entry(day, name = "Dal", grams = 100.0, per100 = dal))
        val fibre = NutrientAggregator.sumEntries(entries)[Nutrient.FIBRE]
        assertThat(fibre.amount).isWithin(1e-9).of(7.9)
        assertThat(fibre.knownCount).isEqualTo(1)
        assertThat(fibre.missingCount).isEqualTo(1)
        assertThat(fibre.coverage).isEqualTo(0.5)
    }

    @Test fun `entirely unknown nutrient is null rather than zero`() {
        val t = NutrientAggregator.sumEntries(listOf(Fx.entry(day)))
        assertThat(t[Nutrient.VITAMIN_D].isUnknown).isTrue()
        assertThat(t.known(Nutrient.VITAMIN_D)).isNull()
        assertThat(t.asNutrients().has(Nutrient.VITAMIN_D)).isFalse()
    }

    @Test fun `quantity change recalculates from the stored snapshot`() {
        val e = Fx.entry(day, grams = 150.0)
        assertThat(e.nutrients.energyKcal!!).isWithin(1e-9).of(195.0)
        val edited = e.copy(quantity = 200.0)
        assertThat(edited.nutrients.energyKcal!!).isWithin(1e-9).of(260.0)
    }

    @Test fun `historical snapshot is independent of later food changes`() {
        val e = Fx.entry(day, grams = 100.0, per100 = Fx.rice.nutrientsPer100)
        val changedFood = Fx.rice.copy(nutrientsPer100 = Fx.rice.nutrientsPer100.with(Nutrient.ENERGY, 999.0))
        assertThat(changedFood.nutrientsPer100.energyKcal).isEqualTo(999.0)
        assertThat(e.nutrients.energyKcal).isEqualTo(130.0)
    }

    @Test fun `quick add calorie only entries do not invent macros`() {
        val q = Fx.entry(day, name = "Quick", grams = 1.0, per100 = Nutrients.of(Nutrient.ENERGY to 300.0))
            .copy(type = EntryType.QUICK_ADD, referenceAmount = 1.0, basis = NutrientBasis.SERVING, unit = QuantityUnit.SERVING)
        assertThat(q.isCalorieOnly).isTrue()
        val t = NutrientAggregator.sumEntries(listOf(q, Fx.entry(day)))
        assertThat(t.calories).isWithin(1e-9).of(430.0)
        assertThat(t[Nutrient.PROTEIN].missingCount).isEqualTo(1)
        assertThat(t[Nutrient.PROTEIN].amount).isWithin(1e-9).of(2.7)
    }

    @Test fun `day status rules`() {
        assertThat(DayStatusCalculator.status(emptyList(), false)).isEqualTo(DayLogStatus.EMPTY)
        assertThat(DayStatusCalculator.status(listOf(Fx.entry(day)), false)).isEqualTo(DayLogStatus.PARTIAL)
        assertThat(DayStatusCalculator.status(listOf(Fx.entry(day)), true)).isEqualTo(DayLogStatus.COMPLETE)
        val three = listOf(Meal.BREAKFAST, Meal.LUNCH, Meal.DINNER).map { Fx.entry(day, it) }
        assertThat(DayStatusCalculator.status(three, false)).isEqualTo(DayLogStatus.COMPLETE)
    }
}
