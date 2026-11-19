package com.fdc.nutrition.domain.model

import java.time.Instant
import java.time.LocalDate

/** A meal slot in the diary. Default slots are seeded; users may add custom ones. */
data class Meal(val id: String, val name: String, val sortOrder: Int, val isDefault: Boolean, val isArchived: Boolean = false) {
    companion object {
        const val BREAKFAST = "breakfast"
        const val MORNING_SNACK = "morning_snack"
        const val LUNCH = "lunch"
        const val EVENING_SNACK = "evening_snack"
        const val DINNER = "dinner"
        val DEFAULTS = listOf(
            Meal(BREAKFAST, "Breakfast", 0, true),
            Meal(MORNING_SNACK, "Morning snack", 1, true),
            Meal(LUNCH, "Lunch", 2, true),
            Meal(EVENING_SNACK, "Evening snack", 3, true),
            Meal(DINNER, "Dinner", 4, true),
        )
    }
}

enum class EntryType { FOOD, RECIPE, QUICK_ADD }

/**
 * A logged diary entry. Nutrition is stored as an immutable **snapshot** taken when the entry was
 * logged ([nutrientsPerReference] per [referenceAmount] of [basis]), so later edits to the food or
 * recipe never change history. Changing [quantity] recalculates from the snapshot.
 */
data class DiaryEntry(
    val id: String,
    val date: LocalDate,
    val mealId: String,
    val type: EntryType,
    val foodId: String?,
    val recipeId: String?,
    val name: String,
    val brand: String?,
    val quantity: Double,
    val unit: QuantityUnit,
    val unitLabel: String,
    /** Amount in [basis] units represented by one [unit]. */
    val amountPerUnit: Double,
    val basis: NutrientBasis,
    val referenceAmount: Double,
    val nutrientsPerReference: Nutrients,
    val dataQuality: DataQuality,
    val preparation: PreparationState = PreparationState.NOT_APPLICABLE,
    val note: String? = null,
    val plannedSourceKey: String? = null,
    val loggedAt: Instant,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    /** Quantity converted to basis units (g, ml or servings). */
    val baseAmount: Double get() = quantity * amountPerUnit

    /** Nutrients actually consumed for this entry. */
    val nutrients: Nutrients get() = nutrientsPerReference.scale(baseAmount / referenceAmount)

    val isCalorieOnly: Boolean get() = type == EntryType.QUICK_ADD &&
        nutrientsPerReference.values.keys == setOf(Nutrient.ENERGY)
}
