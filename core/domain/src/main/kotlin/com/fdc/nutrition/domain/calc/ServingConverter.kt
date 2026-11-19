package com.fdc.nutrition.domain.calc

import com.fdc.nutrition.domain.model.Food
import com.fdc.nutrition.domain.model.NutrientBasis
import com.fdc.nutrition.domain.model.PortionKind
import com.fdc.nutrition.domain.model.QuantityUnit
import com.fdc.nutrition.domain.model.ServingSize
import com.fdc.nutrition.domain.model.UnitDimension

/**
 * A unit a quantity can be logged in for a specific food, with the amount of the food's basis
 * (g, ml or servings) that one unit represents.
 */
data class ServingOption(
    val key: String,
    val unit: QuantityUnit,
    val label: String,
    val amountPerUnit: Double,
    val portionKind: PortionKind = PortionKind.AS_LISTED,
    val isFoodSpecific: Boolean,
) {
    fun baseAmount(quantity: Double): Double = quantity * amountPerUnit
}

sealed class ConversionError(message: String) : Exception(message) {
    class UnsupportedUnit(unit: QuantityUnit, basis: NutrientBasis) :
        ConversionError("${unit.displayName} can't be used for this food without a food-specific serving size (nutrients are per ${basis.symbol}).")
    class InvalidQuantity(q: Double) : ConversionError("Quantity must be a positive number (was $q).")
}

/**
 * Builds the list of units available for a food and converts quantities to basis amounts.
 *
 * Rules (never assume universal sizes for household measures):
 * 1. Food-specific servings are always offered and take priority.
 * 2. Mass units are offered for GRAM-basis foods, or for MILLILITRE-basis foods with a known density.
 * 3. Volume units (ml, L, fl oz, cup, tbsp, tsp) are offered for MILLILITRE-basis foods, or for
 *    GRAM-basis foods with a known density. Cup/tbsp/tsp use US standard volumes.
 * 4. Pieces, slices, bowls, plates and generic servings come **only** from food-specific servings.
 */
object ServingConverter {
    private val massUnits = listOf(QuantityUnit.GRAM, QuantityUnit.KILOGRAM, QuantityUnit.OUNCE, QuantityUnit.POUND)
    private val volumeUnits = listOf(QuantityUnit.MILLILITRE, QuantityUnit.LITRE, QuantityUnit.FLUID_OUNCE, QuantityUnit.CUP, QuantityUnit.TABLESPOON, QuantityUnit.TEASPOON)

    fun optionsFor(food: Food): List<ServingOption> = options(food.basis, food.servings, food.densityGPerMl)

    fun options(basis: NutrientBasis, servings: List<ServingSize>, densityGPerMl: Double? = null): List<ServingOption> {
        val result = mutableListOf<ServingOption>()
        servings.sortedByDescending { it.isDefault }.forEach {
            if (it.amount > 0 && it.amount.isFinite())
                result += ServingOption("s:${it.id}", it.unit, it.label, it.amount, it.portionKind, isFoodSpecific = true)
        }
        val density = densityGPerMl?.takeIf { it > 0 && it.isFinite() }
        when (basis) {
            NutrientBasis.GRAM -> {
                massUnits.forEach { result += standard(it, it.standardFactor!!) }
                if (density != null) volumeUnits.forEach { result += standard(it, it.standardFactor!! * density) }
            }
            NutrientBasis.MILLILITRE -> {
                volumeUnits.forEach { result += standard(it, it.standardFactor!!) }
                if (density != null) massUnits.forEach { result += standard(it, it.standardFactor!! / density) }
            }
            NutrientBasis.SERVING -> if (servings.none { it.unit == QuantityUnit.SERVING }) {
                result += ServingOption("u:SERVING", QuantityUnit.SERVING, "serving", 1.0, PortionKind.PREPARED_SERVING, isFoodSpecific = false)
            }
        }
        // Food-specific cup/tbsp definitions win over derived ones with the same unit.
        val specificUnits = result.filter { it.isFoodSpecific }.map { it.unit }.toSet()
        return result.filter { it.isFoodSpecific || it.unit !in specificUnits || it.unit.dimension == UnitDimension.MASS }
    }

    private fun standard(unit: QuantityUnit, amountPerUnit: Double) =
        ServingOption("u:${unit.name}", unit, unit.symbol, amountPerUnit, isFoodSpecific = false)

    /** Converts a quantity in [unit] to the food's basis amount, or fails if no conversion is known. */
    fun toBaseAmount(quantity: Double, unit: QuantityUnit, basis: NutrientBasis, servings: List<ServingSize>, densityGPerMl: Double? = null): Result<Double> {
        if (!quantity.isFinite() || quantity <= 0) return Result.failure(ConversionError.InvalidQuantity(quantity))
        val option = options(basis, servings, densityGPerMl).firstOrNull { it.unit == unit }
            ?: return Result.failure(ConversionError.UnsupportedUnit(unit, basis))
        return Result.success(option.baseAmount(quantity))
    }

    fun defaultOption(food: Food): ServingOption {
        val opts = optionsFor(food)
        return opts.firstOrNull { it.isFoodSpecific && food.servings.any { s -> s.isDefault && "s:${s.id}" == it.key } }
            ?: opts.first { !it.isFoodSpecific && (it.unit == QuantityUnit.GRAM || it.unit == QuantityUnit.MILLILITRE || it.unit == QuantityUnit.SERVING) }
    }
}

/** Unit conversions for body metrics. */
object BodyUnits {
    const val KG_PER_LB = 0.45359237
    const val CM_PER_INCH = 2.54
    fun kgToLb(kg: Double) = kg / KG_PER_LB
    fun lbToKg(lb: Double) = lb * KG_PER_LB
    fun cmToIn(cm: Double) = cm / CM_PER_INCH
    fun inToCm(inch: Double) = inch * CM_PER_INCH
}
