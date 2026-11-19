package com.fdc.nutrition.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.fdc.nutrition.domain.model.DataQuality
import com.fdc.nutrition.domain.model.DietClass
import com.fdc.nutrition.domain.model.FoodSource
import com.fdc.nutrition.domain.model.NutrientBasis
import com.fdc.nutrition.domain.model.PortionKind
import com.fdc.nutrition.domain.model.PreparationState
import com.fdc.nutrition.domain.model.QuantityUnit
import java.time.Instant

@Entity(tableName = "food_categories")
data class FoodCategoryEntity(
    @PrimaryKey val id: String,
    val name: String,
    val emoji: String,
    @ColumnInfo(name = "sort_order") val sortOrder: Int,
)

@Entity(
    tableName = "foods",
    foreignKeys = [ForeignKey(entity = FoodCategoryEntity::class, parentColumns = ["id"], childColumns = ["category_id"])],
    indices = [
        Index("name"), Index("category_id"), Index("is_archived"), Index("is_popular"),
        Index(value = ["source", "source_ref"]), Index("brand"),
    ],
)
data class FoodEntity(
    @PrimaryKey val id: String,
    val name: String,
    val brand: String?,
    @ColumnInfo(name = "category_id") val categoryId: String,
    val basis: NutrientBasis,
    @ColumnInfo(name = "density_g_per_ml") val densityGPerMl: Double?,
    val source: FoodSource,
    @ColumnInfo(name = "source_ref") val sourceRef: String?,
    @ColumnInfo(name = "data_quality") val dataQuality: DataQuality,
    val preparation: PreparationState,
    @ColumnInfo(name = "is_custom") val isCustom: Boolean,
    @ColumnInfo(name = "is_archived") val isArchived: Boolean,
    @ColumnInfo(name = "is_popular") val isPopular: Boolean,
    @ColumnInfo(name = "diet_class") val dietClass: DietClass,
    /** Comma-separated allergen tags. */
    val allergens: String,
    @ColumnInfo(name = "allergen_info_known") val allergenInfoKnown: Boolean,
    /** Extra search terms such as regional names (e.g. "dahi" for yogurt). */
    val aliases: String?,
    @ColumnInfo(name = "original_name") val originalName: String?,
    val notes: String?,
    @ColumnInfo(name = "seed_version") val seedVersion: Int?,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
    @ColumnInfo(name = "updated_at") val updatedAt: Instant,
    @ColumnInfo(name = "source_updated_at") val sourceUpdatedAt: Instant?,
)

/** Full-text index over food names, brands and aliases for fast search. */
@Fts4(contentEntity = FoodEntity::class)
@Entity(tableName = "foods_fts")
data class FoodFtsEntity(
    val name: String,
    val brand: String?,
    val aliases: String?,
)

@Entity(
    tableName = "food_nutrients",
    foreignKeys = [ForeignKey(entity = FoodEntity::class, parentColumns = ["id"], childColumns = ["food_id"], onDelete = ForeignKey.CASCADE)],
)
data class FoodNutrientsEntity(
    @PrimaryKey @ColumnInfo(name = "food_id") val foodId: String,
    @Embedded val nutrients: NutrientColumns,
)

@Entity(
    tableName = "food_servings",
    foreignKeys = [ForeignKey(entity = FoodEntity::class, parentColumns = ["id"], childColumns = ["food_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("food_id")],
)
data class FoodServingEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "food_id") val foodId: String,
    val unit: QuantityUnit,
    val label: String,
    /** Basis amount (g/ml) represented by one unit. */
    val amount: Double,
    @ColumnInfo(name = "is_default") val isDefault: Boolean,
    @ColumnInfo(name = "portion_kind") val portionKind: PortionKind,
    val source: String?,
    @ColumnInfo(name = "sort_order") val sortOrder: Int,
)

@Entity(
    tableName = "food_barcodes",
    foreignKeys = [ForeignKey(entity = FoodEntity::class, parentColumns = ["id"], childColumns = ["food_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("food_id")],
)
data class FoodBarcodeEntity(
    @PrimaryKey val barcode: String,
    @ColumnInfo(name = "food_id") val foodId: String,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
)

@Entity(
    tableName = "food_favourites",
    foreignKeys = [ForeignKey(entity = FoodEntity::class, parentColumns = ["id"], childColumns = ["food_id"], onDelete = ForeignKey.CASCADE)],
)
data class FoodFavouriteEntity(
    @PrimaryKey @ColumnInfo(name = "food_id") val foodId: String,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
)

@Entity(tableName = "search_history", indices = [Index(value = ["query"], unique = true)])
data class SearchHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val query: String,
    @ColumnInfo(name = "last_used_at") val lastUsedAt: Instant,
)

/** A food row joined with its nutrient values. */
data class FoodWithNutrients(
    @Embedded val food: FoodEntity,
    @Embedded val nutrients: NutrientColumns,
)
