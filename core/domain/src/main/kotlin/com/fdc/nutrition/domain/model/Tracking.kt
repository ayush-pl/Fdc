package com.fdc.nutrition.domain.model

import java.time.Instant
import java.time.LocalDate

enum class BeverageType(val displayName: String) {
    WATER("Water"), TEA("Tea"), COFFEE("Coffee"), MILK("Milk"), JUICE("Juice"), OTHER("Other beverage");
}

data class WaterEntry(
    val id: String,
    val date: LocalDate,
    val amountMl: Double,
    val beverage: BeverageType = BeverageType.WATER,
    val loggedAt: Instant,
)

enum class MeasurementType(val displayName: String, val isLength: Boolean) {
    WEIGHT("Body weight", false), BODY_FAT("Body fat %", false), WAIST("Waist", true), CHEST("Chest", true),
    ARM("Arm", true), THIGH("Thigh", true), HIP("Hip", true), CALF("Calf", true), CUSTOM("Custom", true);
}

/** Body record. Weight is stored in kg, lengths in cm, body fat in percent. */
data class BodyMeasurement(
    val id: String,
    val date: LocalDate,
    val type: MeasurementType,
    val customName: String? = null,
    val value: Double,
    val note: String? = null,
    val createdAt: Instant,
    val updatedAt: Instant = createdAt,
)

/** A food or recipe planned for a future (or past) date. Planned food is never counted as consumed. */
data class PlannedMeal(
    val id: String,
    val date: LocalDate,
    val mealId: String,
    val foodId: String?,
    val recipeId: String?,
    val name: String,
    val quantity: Double,
    val unit: QuantityUnit,
    val unitLabel: String,
    val amountPerUnit: Double,
    val basis: NutrientBasis,
    val referenceAmount: Double,
    val nutrientsPerReference: Nutrients,
    val recurrenceId: String? = null,
    val loggedEntryId: String? = null,
    val note: String? = null,
) {
    val nutrients: Nutrients get() = nutrientsPerReference.scale(quantity * amountPerUnit / referenceAmount)
    val isLogged: Boolean get() = loggedEntryId != null
}

enum class DayLogStatus { COMPLETE, PARTIAL, EMPTY }
