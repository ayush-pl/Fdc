package com.fdc.nutrition.domain.model

import java.time.Instant
import java.time.LocalDate

enum class MacroMethod { PERCENTAGE, GRAMS, CUSTOM }
enum class TargetOrigin { CALCULATED, CUSTOM }

data class MacroSplit(val proteinPct: Double, val carbPct: Double, val fatPct: Double) {
    val total: Double get() = proteinPct + carbPct + fatPct
    val isValid: Boolean get() = listOf(proteinPct, carbPct, fatPct).all { it in 0.0..100.0 } && kotlin.math.abs(total - 100.0) < 0.01
    companion object { val DEFAULT = MacroSplit(25.0, 50.0, 25.0) }
}

/**
 * Daily nutrition targets. Targets are versioned by [effectiveFrom] so historical analytics compare
 * each day against the target that applied on that day.
 */
data class NutritionTargets(
    val id: String,
    val effectiveFrom: LocalDate,
    val values: Map<Nutrient, Double>,
    val waterMl: Double,
    val macroMethod: MacroMethod,
    val macroSplit: MacroSplit?,
    val origin: TargetOrigin,
    val createdAt: Instant,
) {
    val calories: Double? get() = values[Nutrient.ENERGY]
    operator fun get(n: Nutrient): Double? = values[n]
}
