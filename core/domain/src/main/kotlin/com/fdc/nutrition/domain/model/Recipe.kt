package com.fdc.nutrition.domain.model

import java.time.Instant

data class RecipeIngredient(
    val id: String,
    val foodId: String,
    val foodName: String,
    val quantity: Double,
    val unit: QuantityUnit,
    val unitLabel: String,
    val amountPerUnit: Double,
    val basis: NutrientBasis,
    /** The ingredient food's current nutrients per 100 basis units. */
    val nutrientsPer100: Nutrients,
    val preparation: PreparationState,
    val densityGPerMl: Double? = null,
    val sortOrder: Int = 0,
    val note: String? = null,
) {
    val baseAmount: Double get() = quantity * amountPerUnit
    /** Mass in grams contributed to the recipe, if it can be determined. */
    val weightGrams: Double? get() = when (basis) {
        NutrientBasis.GRAM -> baseAmount
        NutrientBasis.MILLILITRE -> densityGPerMl?.let { baseAmount * it }
        NutrientBasis.SERVING -> null
    }
}

data class Recipe(
    val id: String,
    val name: String,
    val description: String? = null,
    val instructions: String? = null,
    val servings: Double,
    /** Weight of the finished, cooked dish if the user weighed it. */
    val finalWeightGrams: Double? = null,
    val imageUri: String? = null,
    val isFavourite: Boolean = false,
    val isArchived: Boolean = false,
    val ingredients: List<RecipeIngredient>,
    val createdAt: Instant,
    val updatedAt: Instant,
)
