package com.fdc.nutrition.domain.calc

import com.fdc.nutrition.domain.model.Nutrients
import com.fdc.nutrition.domain.model.Recipe
import com.fdc.nutrition.domain.model.RecipeIngredient

data class RecipeNutrition(
    val totals: NutrientTotals,
    val total: Nutrients,
    val perServing: Nutrients,
    /** Null when the recipe weight cannot be determined (no final weight and ingredients without a gram weight). */
    val per100g: Nutrients?,
    val rawIngredientWeightG: Double?,
    /** Weight used for per-100 g figures: recorded final weight if present, else summed raw ingredient weight. */
    val effectiveWeightG: Double?,
    val usesRecordedFinalWeight: Boolean,
    val servingWeightG: Double?,
    val servings: Double,
)

object RecipeCalculator {
    fun calculate(recipe: Recipe): RecipeNutrition =
        calculate(recipe.ingredients, recipe.servings, recipe.finalWeightGrams)

    fun calculate(ingredients: List<RecipeIngredient>, servings: Double, finalWeightGrams: Double?): RecipeNutrition {
        require(servings > 0 && servings.isFinite()) { "Servings must be a positive number" }
        val contributions = ingredients.map { NutritionCalculator.forAmount(it.nutrientsPer100, it.baseAmount, 100.0) }
        val totals = NutrientAggregator.sum(contributions)
        val total = totals.asNutrients()
        val weights = ingredients.map { it.weightGrams }
        val rawWeight = if (ingredients.isNotEmpty() && weights.all { it != null }) weights.sumOf { it!! } else null
        val recorded = finalWeightGrams?.takeIf { it > 0 && it.isFinite() }
        val effective = recorded ?: rawWeight
        return RecipeNutrition(
            totals = totals,
            total = total,
            perServing = total.scale(1.0 / servings),
            per100g = effective?.takeIf { it > 0 }?.let { total.scale(100.0 / it) },
            rawIngredientWeightG = rawWeight,
            effectiveWeightG = effective,
            usesRecordedFinalWeight = recorded != null,
            servingWeightG = effective?.let { it / servings },
            servings = servings,
        )
    }
}
