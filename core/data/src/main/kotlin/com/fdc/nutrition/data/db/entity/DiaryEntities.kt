package com.fdc.nutrition.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.fdc.nutrition.domain.model.DataQuality
import com.fdc.nutrition.domain.model.EntryType
import com.fdc.nutrition.domain.model.NutrientBasis
import com.fdc.nutrition.domain.model.PreparationState
import com.fdc.nutrition.domain.model.QuantityUnit
import java.time.Instant
import java.time.LocalDate

@Entity(tableName = "meals")
data class MealEntity(
    @PrimaryKey val id: String,
    val name: String,
    @ColumnInfo(name = "sort_order") val sortOrder: Int,
    @ColumnInfo(name = "is_default") val isDefault: Boolean,
    @ColumnInfo(name = "is_archived") val isArchived: Boolean = false,
)

/**
 * A logged food. Foreign keys to foods/recipes use RESTRICT so a referenced food can never be
 * deleted — it must be archived. Nutrition is a snapshot (`s_` columns) per reference amount.
 */
@Entity(
    tableName = "diary_entries",
    foreignKeys = [
        ForeignKey(entity = MealEntity::class, parentColumns = ["id"], childColumns = ["meal_id"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = FoodEntity::class, parentColumns = ["id"], childColumns = ["food_id"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = RecipeEntity::class, parentColumns = ["id"], childColumns = ["recipe_id"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [
        Index("date"), Index(value = ["date", "meal_id"]), Index("food_id"), Index("recipe_id"), Index("meal_id"),
        Index(value = ["planned_source_key"], unique = true),
    ],
)
data class DiaryEntryEntity(
    @PrimaryKey val id: String,
    val date: LocalDate,
    @ColumnInfo(name = "meal_id") val mealId: String,
    val type: EntryType,
    @ColumnInfo(name = "food_id") val foodId: String?,
    @ColumnInfo(name = "recipe_id") val recipeId: String?,
    val name: String,
    val brand: String?,
    val quantity: Double,
    val unit: QuantityUnit,
    @ColumnInfo(name = "unit_label") val unitLabel: String,
    @ColumnInfo(name = "amount_per_unit") val amountPerUnit: Double,
    val basis: NutrientBasis,
    @ColumnInfo(name = "reference_amount") val referenceAmount: Double,
    @Embedded(prefix = "s_") val snapshot: NutrientColumns,
    @ColumnInfo(name = "data_quality") val dataQuality: DataQuality,
    val preparation: PreparationState,
    val note: String?,
    /** Set when created from a planned meal; unique so a plan can't be logged twice. */
    @ColumnInfo(name = "planned_source_key") val plannedSourceKey: String?,
    @ColumnInfo(name = "logged_at") val loggedAt: Instant,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
    @ColumnInfo(name = "updated_at") val updatedAt: Instant,
)

@Entity(tableName = "meal_notes", primaryKeys = ["date", "meal_id"])
data class MealNoteEntity(
    val date: LocalDate,
    @ColumnInfo(name = "meal_id") val mealId: String,
    val note: String,
    @ColumnInfo(name = "updated_at") val updatedAt: Instant,
)

@Entity(tableName = "day_logs")
data class DayLogEntity(
    @PrimaryKey val date: LocalDate,
    @ColumnInfo(name = "marked_complete") val markedComplete: Boolean,
    val note: String?,
    @ColumnInfo(name = "updated_at") val updatedAt: Instant,
)

/** A favourite meal template ("My usual breakfast"). Items carry their own nutrition snapshot. */
@Entity(tableName = "saved_meals")
data class SavedMealEntity(
    @PrimaryKey val id: String,
    val name: String,
    @ColumnInfo(name = "default_meal_id") val defaultMealId: String?,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
    @ColumnInfo(name = "updated_at") val updatedAt: Instant,
)

@Entity(
    tableName = "saved_meal_items",
    foreignKeys = [ForeignKey(entity = SavedMealEntity::class, parentColumns = ["id"], childColumns = ["saved_meal_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("saved_meal_id")],
)
data class SavedMealItemEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "saved_meal_id") val savedMealId: String,
    @Embedded val item: PortionColumns,
    @ColumnInfo(name = "sort_order") val sortOrder: Int,
)

/** Shared "what & how much" columns for templates, plans and recurrences. */
data class PortionColumns(
    val type: EntryType,
    @ColumnInfo(name = "food_id") val foodId: String?,
    @ColumnInfo(name = "recipe_id") val recipeId: String?,
    val name: String,
    val brand: String?,
    val quantity: Double,
    val unit: QuantityUnit,
    @ColumnInfo(name = "unit_label") val unitLabel: String,
    @ColumnInfo(name = "amount_per_unit") val amountPerUnit: Double,
    val basis: NutrientBasis,
    @ColumnInfo(name = "reference_amount") val referenceAmount: Double,
    @Embedded(prefix = "p_") val nutrients: NutrientColumns,
    @ColumnInfo(name = "data_quality") val dataQuality: DataQuality,
)
