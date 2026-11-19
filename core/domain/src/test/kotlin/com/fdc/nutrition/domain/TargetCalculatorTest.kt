package com.fdc.nutrition.domain

import com.fdc.nutrition.domain.calc.*
import com.fdc.nutrition.domain.model.*
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

class TargetCalculatorTest {
    private val base = EnergyInputs(30, Sex.MALE, 180.0, 80.0, ActivityLevel.MODERATE, Goal.MAINTAIN, 0.0)

    @Test fun `mifflin st jeor matches published formula`() {
        // 10*80 + 6.25*180 - 5*30 + 5 = 1780
        assertThat(TargetCalculator.mifflinStJeor(80.0, 180.0, 30, Sex.MALE)).isWithin(1e-9).of(1780.0)
        // 10*60 + 6.25*165 - 5*25 - 161 = 1345.25
        assertThat(TargetCalculator.mifflinStJeor(60.0, 165.0, 25, Sex.FEMALE)).isWithin(1e-9).of(1345.25)
        assertThat(TargetCalculator.mifflinStJeor(60.0, 165.0, 25, Sex.UNSPECIFIED)).isWithin(1e-9).of(1428.25)
    }

    @Test fun `maintenance equals TDEE rounded to 10 kcal`() {
        val e = TargetCalculator.estimate(base)
        assertThat(e.tdeeKcal).isWithin(1e-9).of(1780.0 * 1.55)
        assertThat(e.targetKcal).isEqualTo(2760.0)
    }

    @Test fun `weight loss uses 7700 kcal per kg deficit`() {
        val e = TargetCalculator.estimate(base.copy(goal = Goal.LOSE, weeklyChangeKg = 0.5))
        assertThat(e.adjustmentKcal).isWithin(1e-6).of(-550.0)
        assertThat(e.targetKcal).isEqualTo(2210.0)
        assertThat(e.warnings).isEmpty()
    }

    @Test fun `extreme deficits are capped and floored`() {
        val small = EnergyInputs(40, Sex.FEMALE, 155.0, 50.0, ActivityLevel.SEDENTARY, Goal.LOSE, 2.0)
        val e = TargetCalculator.estimate(small)
        assertThat(e.targetKcal).isAtLeast(TargetCalculator.minimumCalories(Sex.FEMALE))
        assertThat(e.targetKcal).isAtLeast(e.bmrKcal - 10)
        assertThat(e.warnings).isNotEmpty()
    }

    @Test fun `gain surplus is capped`() {
        val e = TargetCalculator.estimate(base.copy(goal = Goal.GAIN, weeklyChangeKg = 2.0))
        assertThat(e.adjustmentKcal).isAtMost(500.0)
    }

    @Test fun `percentage macros are consistent with calories`() {
        val m = TargetCalculator.macrosFromSplit(2200.0, MacroSplit(25.0, 50.0, 25.0))
        assertThat(m.proteinG).isWithin(1e-9).of(137.5)
        assertThat(m.carbG).isWithin(1e-9).of(275.0)
        assertThat(m.fatG).isWithin(1e-9).of(2200 * 0.25 / 9)
        assertThat(m.kcal).isWithin(1e-6).of(2200.0)
    }

    @Test fun `invalid macro split rejected`() {
        assertThrows(IllegalArgumentException::class.java) { TargetCalculator.macrosFromSplit(2000.0, MacroSplit(30.0, 30.0, 30.0)) }
    }

    @Test fun `gram based macros report consistency`() {
        val g = MacroGrams(150.0, 250.0, 65.0) // = 600 + 1000 + 585 = 2185 kcal
        assertThat(TargetCalculator.gramConsistency(2200.0, g)).isWithin(1e-9).of(-15.0 / 2200.0)
    }

    @Test fun `invalid inputs rejected`() {
        assertThrows(IllegalArgumentException::class.java) { TargetCalculator.estimate(base.copy(heightCm = 20.0)) }
    }

    @Test fun `default micronutrient targets follow reference tables`() {
        val f = TargetCalculator.defaultNutrientTargets(2000.0, Sex.FEMALE, 30)
        assertThat(f[Nutrient.IRON]).isEqualTo(18.0)
        assertThat(f[Nutrient.FIBRE]).isEqualTo(28.0)
        assertThat(f[Nutrient.SODIUM]).isEqualTo(2300.0)
        val m = TargetCalculator.defaultNutrientTargets(2500.0, Sex.MALE, 60)
        assertThat(m[Nutrient.IRON]).isEqualTo(8.0)
        assertThat(m[Nutrient.MAGNESIUM]).isEqualTo(420.0)
    }
}
