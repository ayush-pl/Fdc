package com.fdc.nutrition.domain

import com.fdc.nutrition.domain.calc.*
import com.fdc.nutrition.domain.model.*
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalDate

class ProgressAndAnalyticsTest {
    private fun w(date: LocalDate, kg: Double) = BodyMeasurement(date.toString(), date, MeasurementType.WEIGHT, value = kg, createdAt = Fx.NOW)

    @Test fun `weight progress toward a loss target`() {
        val d0 = LocalDate.of(2026, 1, 5) // Monday
        val recs = listOf(w(d0, 90.0), w(d0.plusDays(3), 89.0), w(d0.plusDays(7), 88.0), w(d0.plusDays(9), 87.0))
        val p = WeightProgressCalculator.calculate(recs, profileStartKg = null, targetKg = 80.0)
        assertThat(p.startKg).isEqualTo(90.0)
        assertThat(p.currentKg).isEqualTo(87.0)
        assertThat(p.changeKg).isEqualTo(-3.0)
        assertThat(p.progressPercent!!).isWithin(1e-9).of(30.0)
        assertThat(p.weeklyAverages.map { it.average }).containsExactly(89.5, 87.5).inOrder()
        assertThat(p.weeklyTrendKg).isEqualTo(-2.0)
    }

    @Test fun `weight progress toward a gain target and clamping`() {
        val d = LocalDate.of(2026, 2, 1)
        val p = WeightProgressCalculator.calculate(listOf(w(d, 60.0), w(d.plusDays(1), 66.0)), 60.0, 65.0)
        assertThat(p.progressPercent).isEqualTo(100.0)
        val back = WeightProgressCalculator.calculate(listOf(w(d, 60.0), w(d.plusDays(1), 59.0)), 60.0, 65.0)
        assertThat(back.progressPercent).isEqualTo(0.0)
    }

    @Test fun `no records gives empty progress`() {
        val p = WeightProgressCalculator.calculate(emptyList(), null, 70.0)
        assertThat(p.currentKg).isNull(); assertThat(p.progressPercent).isNull()
    }

    @Test fun `target versions apply by effective date`() {
        val t1 = Fx.target(LocalDate.of(2026, 1, 1), 2000.0)
        val t2 = Fx.target(LocalDate.of(2026, 1, 15), 1800.0)
        assertThat(listOf(t1, t2).effectiveOn(LocalDate.of(2026, 1, 10))).isEqualTo(t1)
        assertThat(listOf(t1, t2).effectiveOn(LocalDate.of(2026, 1, 20))).isEqualTo(t2)
        assertThat(listOf(t1, t2).effectiveOn(LocalDate.of(2025, 12, 1))).isEqualTo(t1)
    }

    @Test fun `analytics average only logged days and compute consistency`() {
        val from = LocalDate.of(2026, 3, 2)
        val to = from.plusDays(6)
        // Day 1: 2000 kcal (rice 1538.46 g ≈ 2000), Day 2: 1000 kcal, others unlogged
        val e1 = Fx.entry(from, grams = 2000.0 / 1.3)
        val e2 = Fx.entry(from.plusDays(1), grams = 1000.0 / 1.3)
        val r = AnalyticsCalculator.report(from, to, listOf(e1, e2), mapOf(from to 1500.0), listOf(Fx.target(from, 2000.0)))
        assertThat(r.totalDays).isEqualTo(7)
        assertThat(r.loggedDays).isEqualTo(2)
        assertThat(r.averages[Nutrient.ENERGY]!!).isWithin(1e-6).of(1500.0)
        assertThat(r.calorieConsistency).isEqualTo(0.5)
        assertThat(r.averageCalorieDelta!!).isWithin(1e-6).of(-500.0)
        assertThat(r.averageWaterMl).isEqualTo(1500.0)
        assertThat(r.averages[Nutrient.VITAMIN_C]).isNull()
        assertThat(r.topFoods.first().name).isEqualTo("Rice")
        assertThat(r.topFoods.first().count).isEqualTo(2)
    }

    @Test fun `target achievement respects goal vs limit semantics`() {
        assertThat(AnalyticsCalculator.achieved(TargetKind.GOAL, 31.0, 30.0)).isTrue()
        assertThat(AnalyticsCalculator.achieved(TargetKind.LIMIT, 2400.0, 2300.0)).isFalse()
        assertThat(AnalyticsCalculator.achieved(TargetKind.BALANCE, 2150.0, 2000.0)).isTrue()
        assertThat(AnalyticsCalculator.achieved(TargetKind.BALANCE, 2300.0, 2000.0)).isFalse()
    }

    @Test fun `diet compatibility never guesses with unknown data`() {
        val unknown = Fx.rice.copy(vegetarian = Tristate.UNKNOWN)
        assertThat(DietCompatibility.forDiet(unknown, DietaryPreference.VEGETARIAN)).isEqualTo(Compatibility.UNKNOWN)
        val (status, _) = DietCompatibility.allergenStatus(unknown, setOf("milk"))
        assertThat(status).isEqualTo(Compatibility.UNKNOWN)
        val paneer = Fx.rice.copy(allergens = setOf("milk"), allergenInfoKnown = true)
        assertThat(DietCompatibility.allergenStatus(paneer, setOf("milk")).first).isEqualTo(Compatibility.INCOMPATIBLE)
    }
}
