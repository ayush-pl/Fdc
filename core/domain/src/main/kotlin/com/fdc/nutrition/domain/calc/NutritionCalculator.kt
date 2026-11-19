package com.fdc.nutrition.domain.calc

import com.fdc.nutrition.domain.model.DiaryEntry
import com.fdc.nutrition.domain.model.Food
import com.fdc.nutrition.domain.model.Nutrient
import com.fdc.nutrition.domain.model.Nutrients
import com.fdc.nutrition.domain.model.PlannedMeal
import kotlin.math.abs
import kotlin.math.roundToLong

object NutritionCalculator {
    /** consumed nutrient = (consumed amount / reference amount) × nutrient per reference amount. */
    fun forAmount(perReference: Nutrients, amount: Double, referenceAmount: Double = 100.0): Nutrients {
        require(referenceAmount > 0) { "Reference amount must be positive" }
        require(amount.isFinite() && amount >= 0) { "Amount must be a non-negative number" }
        return perReference.scale(amount / referenceAmount)
    }

    fun forFood(food: Food, option: ServingOption, quantity: Double): Nutrients =
        forAmount(food.nutrientsPer100, option.baseAmount(quantity))

    /** Energy implied by macros using Atwater general factors (4/4/9 kcal per g). */
    fun energyFromMacros(proteinG: Double, carbG: Double, fatG: Double): Double = proteinG * 4 + carbG * 4 + fatG * 9

    /**
     * Returns a warning when declared energy differs from macro-derived energy by more than
     * [tolerance] (fraction). Used to validate user-entered foods; alcohol/polyols can cause
     * legitimate differences, so this is advisory only.
     */
    fun energyMismatch(n: Nutrients, tolerance: Double = 0.2): String? {
        val kcal = n.energyKcal ?: return null
        val p = n.proteinG ?: return null
        val c = n.carbohydrateG ?: return null
        val f = n.fatG ?: return null
        val derived = energyFromMacros(p, c, f)
        if (kcal < 20 && derived < 20) return null
        val ref = maxOf(kcal, derived)
        return if (abs(kcal - derived) / ref > tolerance)
            "Calories (${kcal.roundToLong()} kcal) don't match macros (≈${derived.roundToLong()} kcal from 4/4/9 kcal per g). Please double-check the label."
        else null
    }
}

/** Per-nutrient total with coverage: how many contributing items actually had data. */
data class NutrientTotal(val nutrient: Nutrient, val amount: Double, val knownCount: Int, val itemCount: Int) {
    val missingCount: Int get() = itemCount - knownCount
    /** 0..1; 1 means every item had a value. With no items, coverage is 1 (nothing missing). */
    val coverage: Double get() = if (itemCount == 0) 1.0 else knownCount.toDouble() / itemCount
    val isComplete: Boolean get() = knownCount == itemCount
    /** True when no item had data at all — the total must then be shown as unknown, never 0. */
    val isUnknown: Boolean get() = itemCount > 0 && knownCount == 0
    val valueOrNull: Double? get() = if (isUnknown) null else amount
}

class NutrientTotals(val itemCount: Int, private val totals: Map<Nutrient, NutrientTotal>) {
    operator fun get(n: Nutrient): NutrientTotal = totals[n] ?: NutrientTotal(n, 0.0, 0, itemCount)
    val calories: Double get() = this[Nutrient.ENERGY].amount
    fun known(n: Nutrient): Double? = this[n].valueOrNull
    fun asNutrients(): Nutrients = Nutrients(Nutrient.entries.mapNotNull { n -> this[n].valueOrNull?.let { n to it } }.toMap())
    companion object { val EMPTY = NutrientTotals(0, emptyMap()) }
}

/**
 * Sums nutrients across many items without ever treating missing data as zero: the sum includes
 * only known values and each total reports how many items lacked data.
 */
object NutrientAggregator {
    fun sum(items: List<Nutrients>): NutrientTotals {
        val totals = Nutrient.entries.associateWith { n ->
            var sum = 0.0
            var known = 0
            items.forEach { item -> item[n]?.let { sum += it; known++ } }
            NutrientTotal(n, sum, known, items.size)
        }
        return NutrientTotals(items.size, totals)
    }

    fun sumEntries(entries: List<DiaryEntry>): NutrientTotals = sum(entries.map { it.nutrients })
    fun sumPlanned(items: List<PlannedMeal>): NutrientTotals = sum(items.map { it.nutrients })
}

/** Rounding for presentation only — internal values keep full precision. */
object NutrientFormat {
    fun round(value: Double, nutrient: Nutrient): Double = when {
        nutrient == Nutrient.ENERGY -> Math.round(value).toDouble()
        abs(value) >= 100 -> Math.round(value).toDouble()
        abs(value) >= 10 -> Math.round(value * 10) / 10.0
        else -> Math.round(value * 100) / 100.0
    }

    fun format(value: Double?, nutrient: Nutrient, withUnit: Boolean = true): String {
        if (value == null) return "—"
        val r = round(value, nutrient)
        val s = if (r == Math.floor(r) && abs(r) < 1e15) r.toLong().toString() else r.toString().trimEnd('0').trimEnd('.')
        return if (withUnit) "$s ${nutrient.unit.symbol}" else s
    }

    fun number(value: Double, maxDecimals: Int = 1): String {
        val factor = Math.pow(10.0, maxDecimals.toDouble())
        val r = Math.round(value * factor) / factor
        return if (r == Math.floor(r)) r.toLong().toString() else r.toString()
    }
}
