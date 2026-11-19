package com.fdc.nutrition.domain.calc

import com.fdc.nutrition.domain.model.ActivityLevel
import com.fdc.nutrition.domain.model.Goal
import com.fdc.nutrition.domain.model.MacroSplit
import com.fdc.nutrition.domain.model.Nutrient
import com.fdc.nutrition.domain.model.Sex
import kotlin.math.roundToInt

data class EnergyInputs(
    val ageYears: Int,
    val sex: Sex,
    val heightCm: Double,
    val weightKg: Double,
    val activity: ActivityLevel,
    val goal: Goal,
    /** Desired weekly change in kg (always positive; direction comes from [goal]). */
    val weeklyChangeKg: Double,
)

data class EnergyEstimate(
    val bmrKcal: Double,
    val tdeeKcal: Double,
    val targetKcal: Double,
    /** Daily adjustment applied to TDEE (negative for a deficit). */
    val adjustmentKcal: Double,
    val warnings: List<String>,
)

data class MacroGrams(val proteinG: Double, val carbG: Double, val fatG: Double) {
    val kcal: Double get() = NutritionCalculator.energyFromMacros(proteinG, carbG, fatG)
}

/**
 * Energy and nutrient target estimation.
 *
 * Assumptions (documented in docs/NUTRITION_METHODS.md):
 * - Resting energy: Mifflin–St Jeor (1990). For "prefer not to say" the midpoint of the male (+5)
 *   and female (−161) constants (−78) is used.
 * - Total energy: BMR × standard activity factor (1.2–1.9).
 * - 1 kg of body weight change ≈ 7,700 kcal (a common approximation; real-world change is non-linear).
 * - Safety: weight-loss rate is capped at 1 kg/week, the deficit at 25% of TDEE, and the target never
 *   drops below max(BMR, 1,500 kcal for male / 1,200 kcal for female / 1,350 kcal unspecified).
 *   Gain surplus is capped at 500 kcal/day.
 * These are population estimates, not medical advice.
 */
object TargetCalculator {
    const val KCAL_PER_KG = 7700.0
    const val MAX_LOSS_KG_PER_WEEK = 1.0
    const val MAX_GAIN_KG_PER_WEEK = 0.5
    const val MAX_DEFICIT_FRACTION = 0.25
    const val MAX_SURPLUS_KCAL = 500.0

    fun mifflinStJeor(weightKg: Double, heightCm: Double, ageYears: Int, sex: Sex): Double {
        val base = 10 * weightKg + 6.25 * heightCm - 5 * ageYears
        return base + when (sex) { Sex.MALE -> 5.0; Sex.FEMALE -> -161.0; Sex.UNSPECIFIED -> -78.0 }
    }

    fun minimumCalories(sex: Sex) = when (sex) { Sex.MALE -> 1500.0; Sex.FEMALE -> 1200.0; Sex.UNSPECIFIED -> 1350.0 }

    fun validate(i: EnergyInputs): List<String> = buildList {
        if (i.ageYears !in 18..100) add("Estimates are designed for adults aged 18–100.")
        if (i.heightCm !in 120.0..230.0) add("Height must be between 120 and 230 cm.")
        if (i.weightKg !in 30.0..300.0) add("Weight must be between 30 and 300 kg.")
        if (i.weeklyChangeKg < 0) add("Weekly change cannot be negative.")
    }

    fun estimate(i: EnergyInputs): EnergyEstimate {
        val errors = validate(i).filter { !it.startsWith("Estimates") }
        require(errors.isEmpty()) { errors.joinToString(" ") }
        val warnings = mutableListOf<String>()
        if (i.ageYears < 18) warnings += "These equations are intended for adults; please consult a professional."
        val bmr = mifflinStJeor(i.weightKg, i.heightCm, i.ageYears, i.sex)
        val tdee = bmr * i.activity.factor
        var adjustment = 0.0
        when (i.goal) {
            Goal.LOSE -> {
                var rate = i.weeklyChangeKg
                if (rate > MAX_LOSS_KG_PER_WEEK) { rate = MAX_LOSS_KG_PER_WEEK; warnings += "Weight-loss rate limited to 1 kg per week." }
                var deficit = rate * KCAL_PER_KG / 7.0
                val maxDeficit = tdee * MAX_DEFICIT_FRACTION
                if (deficit > maxDeficit) { deficit = maxDeficit; warnings += "Deficit limited to 25% of estimated energy needs." }
                adjustment = -deficit
            }
            Goal.GAIN -> {
                var rate = i.weeklyChangeKg
                if (rate > MAX_GAIN_KG_PER_WEEK) { rate = MAX_GAIN_KG_PER_WEEK; warnings += "Weight-gain rate limited to 0.5 kg per week." }
                adjustment = minOf(rate * KCAL_PER_KG / 7.0, MAX_SURPLUS_KCAL)
            }
            Goal.MAINTAIN, Goal.CUSTOM -> Unit
        }
        var target = tdee + adjustment
        val floor = maxOf(bmr, minimumCalories(i.sex))
        if (i.goal == Goal.LOSE && target < floor) {
            target = floor
            adjustment = target - tdee
            warnings += "Target raised to a safe minimum of ${floor.roundToInt()} kcal."
        }
        target = (target / 10.0).roundToInt() * 10.0
        return EnergyEstimate(bmr, tdee, target, adjustment, warnings)
    }

    /** Percentage-based macros. Grams are derived from the calorie target so totals are consistent. */
    fun macrosFromSplit(calories: Double, split: MacroSplit): MacroGrams {
        require(split.isValid) { "Macro percentages must each be 0–100 and sum to 100%" }
        return MacroGrams(
            proteinG = calories * split.proteinPct / 100.0 / 4.0,
            carbG = calories * split.carbPct / 100.0 / 4.0,
            fatG = calories * split.fatPct / 100.0 / 9.0,
        )
    }

    /** Percent of calories from each macro for gram-based targets (may not sum to 100). */
    fun splitFromGrams(calories: Double, m: MacroGrams): MacroSplit = MacroSplit(
        proteinPct = m.proteinG * 4 / calories * 100, carbPct = m.carbG * 4 / calories * 100, fatPct = m.fatG * 9 / calories * 100,
    )

    /** Difference between calories implied by gram targets and the calorie target, as a fraction. */
    fun gramConsistency(calories: Double, m: MacroGrams): Double = (m.kcal - calories) / calories

    /**
     * Default daily targets for fibre, sugar, sodium and micronutrients for adults, from NIH Office of
     * Dietary Supplements RDA/AI tables and the Dietary Guidelines for Americans 2020–2025
     * (fibre 14 g/1,000 kcal; added sugars and saturated fat < 10% of energy; sodium 2,300 mg).
     * For an unspecified sex, the higher of the male/female adequacy values is used.
     */
    fun defaultNutrientTargets(calories: Double, sex: Sex, ageYears: Int): Map<Nutrient, Double> {
        fun pick(male: Double, female: Double) = when (sex) { Sex.MALE -> male; Sex.FEMALE -> female; Sex.UNSPECIFIED -> maxOf(male, female) }
        val older = ageYears >= 51
        return mapOf(
            Nutrient.FIBRE to (calories / 1000.0 * 14.0),
            Nutrient.ADDED_SUGARS to (calories * 0.10 / 4.0),
            Nutrient.SATURATED_FAT to (calories * 0.10 / 9.0),
            Nutrient.SODIUM to 2300.0,
            Nutrient.POTASSIUM to pick(3400.0, 2600.0),
            Nutrient.CALCIUM to when {
                sex == Sex.FEMALE && older -> 1200.0
                ageYears >= 71 -> 1200.0
                sex == Sex.UNSPECIFIED && older -> 1200.0
                else -> 1000.0
            },
            Nutrient.IRON to if (older) 8.0 else pick(8.0, 18.0),
            Nutrient.MAGNESIUM to if (ageYears <= 30) pick(400.0, 310.0) else pick(420.0, 320.0),
            Nutrient.ZINC to pick(11.0, 8.0),
            Nutrient.VITAMIN_A to pick(900.0, 700.0),
            Nutrient.VITAMIN_C to pick(90.0, 75.0),
            Nutrient.VITAMIN_D to if (ageYears >= 71) 20.0 else 15.0,
            Nutrient.VITAMIN_E to 15.0,
            Nutrient.VITAMIN_K to pick(120.0, 90.0),
            Nutrient.VITAMIN_B12 to 2.4,
            Nutrient.FOLATE to 400.0,
        )
    }

    /** Default drinking-water goal (EFSA adequate intakes for total water, ~80% from drinks). */
    fun defaultWaterMl(sex: Sex): Double = when (sex) { Sex.MALE -> 2500.0; Sex.FEMALE -> 2000.0; Sex.UNSPECIFIED -> 2250.0 }
}
