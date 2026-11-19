package com.fdc.nutrition.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.fdc.nutrition.domain.model.QuantityUnit
import java.time.Instant
import java.time.LocalDate

@Entity(tableName = "recipes", indices = [Index("name"), Index("is_archived")])
data class RecipeEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String?,
    val instructions: String?,
    val servings: Double,
    @ColumnInfo(name = "final_weight_g") val finalWeightGrams: Double?,
    @ColumnInfo(name = "image_uri") val imageUri: String?,
    @ColumnInfo(name = "is_favourite") val isFavourite: Boolean,
    @ColumnInfo(name = "is_archived") val isArchived: Boolean,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
    @ColumnInfo(name = "updated_at") val updatedAt: Instant,
)

@Entity(
    tableName = "recipe_ingredients",
    foreignKeys = [
        ForeignKey(entity = RecipeEntity::class, parentColumns = ["id"], childColumns = ["recipe_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = FoodEntity::class, parentColumns = ["id"], childColumns = ["food_id"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index("recipe_id"), Index("food_id")],
)
data class RecipeIngredientEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "recipe_id") val recipeId: String,
    @ColumnInfo(name = "food_id") val foodId: String,
    val quantity: Double,
    val unit: QuantityUnit,
    @ColumnInfo(name = "unit_label") val unitLabel: String,
    @ColumnInfo(name = "amount_per_unit") val amountPerUnit: Double,
    @ColumnInfo(name = "sort_order") val sortOrder: Int,
    val note: String?,
)

/** A reusable, favouritable weekly meal plan template. */
@Entity(tableName = "meal_plans")
data class MealPlanEntity(
    @PrimaryKey val id: String,
    val name: String,
    @ColumnInfo(name = "is_favourite") val isFavourite: Boolean,
    val notes: String?,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
    @ColumnInfo(name = "updated_at") val updatedAt: Instant,
)

@Entity(
    tableName = "meal_plan_items",
    foreignKeys = [ForeignKey(entity = MealPlanEntity::class, parentColumns = ["id"], childColumns = ["plan_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("plan_id")],
)
data class MealPlanItemEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "plan_id") val planId: String,
    /** 0 = first day of the plan (Monday when applied to a week). */
    @ColumnInfo(name = "day_offset") val dayOffset: Int,
    @ColumnInfo(name = "meal_id") val mealId: String,
    @Embedded val item: PortionColumns,
)

/**
 * A food planned for a date. Planned rows never count as consumed; logging creates a separate
 * diary entry and stores its id in [loggedEntryId].
 */
@Entity(
    tableName = "planned_meals",
    indices = [Index("date"), Index(value = ["recurrence_id", "date"], unique = true), Index("logged_entry_id")],
)
data class PlannedMealEntity(
    @PrimaryKey val id: String,
    val date: LocalDate,
    @ColumnInfo(name = "meal_id") val mealId: String,
    @Embedded val item: PortionColumns,
    @ColumnInfo(name = "recurrence_id") val recurrenceId: String?,
    @ColumnInfo(name = "logged_entry_id") val loggedEntryId: String?,
    /** A materialised recurring occurrence the user removed; kept so it isn't recreated. */
    @ColumnInfo(name = "is_skipped") val isSkipped: Boolean,
    val note: String?,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
    @ColumnInfo(name = "updated_at") val updatedAt: Instant,
)

@Entity(tableName = "plan_recurrences")
data class PlanRecurrenceEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "meal_id") val mealId: String,
    @Embedded val item: PortionColumns,
    /** Bit 0 = Monday … bit 6 = Sunday. */
    @ColumnInfo(name = "days_of_week") val daysOfWeekMask: Int,
    @ColumnInfo(name = "start_date") val startDate: LocalDate,
    @ColumnInfo(name = "end_date") val endDate: LocalDate?,
    @ColumnInfo(name = "is_active") val isActive: Boolean,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
)
