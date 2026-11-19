package com.fdc.nutrition.data.backup

import com.fdc.nutrition.data.db.entity.*
import com.fdc.nutrition.domain.model.*
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate

/*
 * Portable, versioned representations used for JSON backups and cloud sync payloads.
 * Dates are ISO-8601 strings, timestamps are epoch milliseconds, nutrient maps are keyed by
 * Nutrient.key (e.g. "protein_g") and contain only known values.
 */

typealias NutrientMap = Map<String, Double>

fun NutrientColumns.toMap(): NutrientMap = toDomain().values.mapKeys { it.key.key }
fun NutrientMap.toColumns(): NutrientColumns = NutrientColumns.from(Nutrients(mapNotNull { (k, v) -> Nutrient.fromKey(k)?.let { it to v } }.toMap()))

@Serializable data class ServingDto(val id: String, val unit: String, val label: String, val amount: Double, val isDefault: Boolean, val portionKind: String, val source: String?, val sortOrder: Int)
@Serializable data class FoodDto(
    val id: String, val name: String, val brand: String?, val categoryId: String, val basis: String, val densityGPerMl: Double?, val source: String,
    val sourceRef: String?, val dataQuality: String, val preparation: String, val isArchived: Boolean, val dietClass: String, val allergens: String,
    val allergenInfoKnown: Boolean, val notes: String?, val nutrients: NutrientMap, val servings: List<ServingDto>, val barcodes: List<String>,
    val createdAt: Long, val updatedAt: Long,
)
@Serializable data class DiaryEntryDto(
    val id: String, val date: String, val mealId: String, val type: String, val foodId: String?, val recipeId: String?, val name: String, val brand: String?,
    val quantity: Double, val unit: String, val unitLabel: String, val amountPerUnit: Double, val basis: String, val referenceAmount: Double,
    val snapshot: NutrientMap, val dataQuality: String, val preparation: String, val note: String?, val plannedSourceKey: String?,
    val loggedAt: Long, val createdAt: Long, val updatedAt: Long,
)
@Serializable data class MealDto(val id: String, val name: String, val sortOrder: Int, val isDefault: Boolean, val isArchived: Boolean)
@Serializable data class MealNoteDto(val date: String, val mealId: String, val note: String, val updatedAt: Long)
@Serializable data class DayLogDto(val date: String, val markedComplete: Boolean, val note: String?, val updatedAt: Long)
@Serializable data class PortionDto(
    val type: String, val foodId: String?, val recipeId: String?, val name: String, val brand: String?, val quantity: Double, val unit: String, val unitLabel: String,
    val amountPerUnit: Double, val basis: String, val referenceAmount: Double, val nutrients: NutrientMap, val dataQuality: String,
)
@Serializable data class SavedMealDto(val id: String, val name: String, val defaultMealId: String?, val createdAt: Long, val updatedAt: Long, val items: List<PortionDto>)
@Serializable data class IngredientDto(val id: String, val foodId: String, val quantity: Double, val unit: String, val unitLabel: String, val amountPerUnit: Double, val sortOrder: Int, val note: String?)
@Serializable data class RecipeDto(
    val id: String, val name: String, val description: String?, val instructions: String?, val servings: Double, val finalWeightGrams: Double?,
    val isFavourite: Boolean, val isArchived: Boolean, val createdAt: Long, val updatedAt: Long, val ingredients: List<IngredientDto>,
)
@Serializable data class PlannedMealDto(val id: String, val date: String, val mealId: String, val item: PortionDto, val recurrenceId: String?, val loggedEntryId: String?, val isSkipped: Boolean, val note: String?, val createdAt: Long, val updatedAt: Long)
@Serializable data class RecurrenceDto(val id: String, val mealId: String, val item: PortionDto, val daysOfWeekMask: Int, val startDate: String, val endDate: String?, val isActive: Boolean, val createdAt: Long)
@Serializable data class MealPlanItemDto(val id: String, val dayOffset: Int, val mealId: String, val item: PortionDto)
@Serializable data class MealPlanDto(val id: String, val name: String, val isFavourite: Boolean, val notes: String?, val createdAt: Long, val updatedAt: Long, val items: List<MealPlanItemDto>)
@Serializable data class TargetDto(val id: String, val effectiveFrom: String, val values: NutrientMap, val waterMl: Double, val macroMethod: String, val proteinPct: Double?, val carbPct: Double?, val fatPct: Double?, val origin: String, val createdAt: Long)
@Serializable data class WaterDto(val id: String, val date: String, val amountMl: Double, val beverage: String, val loggedAt: Long)
@Serializable data class BodyDto(val id: String, val date: String, val type: String, val customName: String?, val value: Double, val note: String?, val createdAt: Long, val updatedAt: Long)
@Serializable data class ProfileDto(
    val displayName: String, val dateOfBirth: String?, val heightCm: Double?, val sex: String, val currentWeightKg: Double?, val startWeightKg: Double?,
    val targetWeightKg: Double?, val activityLevel: String, val goal: String, val weeklyChangeKg: Double, val dietaryPreference: String, val allergies: String,
    val restrictions: String?, val createdAt: Long, val updatedAt: Long,
)
@Serializable data class FavouriteDto(val foodId: String, val createdAt: Long)

@Serializable data class BackupData(
    val profile: ProfileDto? = null,
    val targets: List<TargetDto> = emptyList(),
    val meals: List<MealDto> = emptyList(),
    val foods: List<FoodDto> = emptyList(),
    /** Serving sizes the user added to database (non-custom) foods. */
    val userServings: Map<String, List<ServingDto>> = emptyMap(),
    val favourites: List<FavouriteDto> = emptyList(),
    val diary: List<DiaryEntryDto> = emptyList(),
    val mealNotes: List<MealNoteDto> = emptyList(),
    val dayLogs: List<DayLogDto> = emptyList(),
    val savedMeals: List<SavedMealDto> = emptyList(),
    val recipes: List<RecipeDto> = emptyList(),
    val plannedMeals: List<PlannedMealDto> = emptyList(),
    val recurrences: List<RecurrenceDto> = emptyList(),
    val mealPlans: List<MealPlanDto> = emptyList(),
    val water: List<WaterDto> = emptyList(),
    val body: List<BodyDto> = emptyList(),
)

@Serializable data class BackupFile(
    val format: String = FORMAT,
    val schemaVersion: Int = SCHEMA_VERSION,
    val appVersion: String,
    val exportedAt: String,
    val data: BackupData,
) {
    companion object {
        const val FORMAT = "fdc-nutrition-backup"
        const val SCHEMA_VERSION = 1
    }
}

// ---------------- mappers ----------------
private fun Instant.ms() = toEpochMilli()
private fun ms(v: Long) = Instant.ofEpochMilli(v)
private fun LocalDate.s() = toString()
private fun d(s: String) = LocalDate.parse(s)

fun FoodServingEntity.toDto() = ServingDto(id, unit.name, label, amount, isDefault, portionKind.name, source, sortOrder)
fun ServingDto.toEntity(foodId: String) = FoodServingEntity(id, foodId, QuantityUnit.valueOf(unit), label, amount, isDefault, PortionKind.valueOf(portionKind), source, sortOrder)

fun foodDto(f: FoodEntity, n: NutrientColumns, servings: List<FoodServingEntity>, barcodes: List<String>) = FoodDto(
    f.id, f.name, f.brand, f.categoryId, f.basis.name, f.densityGPerMl, f.source.name, f.sourceRef, f.dataQuality.name, f.preparation.name, f.isArchived,
    f.dietClass.name, f.allergens, f.allergenInfoKnown, f.notes, n.toMap(), servings.map { it.toDto() }, barcodes, f.createdAt.ms(), f.updatedAt.ms(),
)
fun FoodDto.toEntity() = FoodEntity(
    id, name, brand, categoryId, NutrientBasis.valueOf(basis), densityGPerMl, FoodSource.valueOf(source), sourceRef, DataQuality.valueOf(dataQuality),
    PreparationState.valueOf(preparation), isCustom = true, isArchived = isArchived, isPopular = false, dietClass = DietClass.valueOf(dietClass), allergens = allergens,
    allergenInfoKnown = allergenInfoKnown, aliases = null, originalName = null, notes = notes, seedVersion = null, createdAt = ms(createdAt), updatedAt = ms(updatedAt), sourceUpdatedAt = null,
)

fun DiaryEntryEntity.toDto() = DiaryEntryDto(id, date.s(), mealId, type.name, foodId, recipeId, name, brand, quantity, unit.name, unitLabel, amountPerUnit, basis.name,
    referenceAmount, snapshot.toMap(), dataQuality.name, preparation.name, note, plannedSourceKey, loggedAt.ms(), createdAt.ms(), updatedAt.ms())
fun DiaryEntryDto.toEntity() = DiaryEntryEntity(id, d(date), mealId, EntryType.valueOf(type), foodId, recipeId, name, brand, quantity, QuantityUnit.valueOf(unit), unitLabel,
    amountPerUnit, NutrientBasis.valueOf(basis), referenceAmount, snapshot.toColumns(), DataQuality.valueOf(dataQuality), PreparationState.valueOf(preparation), note,
    plannedSourceKey, ms(loggedAt), ms(createdAt), ms(updatedAt))

fun MealEntity.toDto() = MealDto(id, name, sortOrder, isDefault, isArchived)
fun MealDto.toEntity() = MealEntity(id, name, sortOrder, isDefault, isArchived)
fun MealNoteEntity.toDto() = MealNoteDto(date.s(), mealId, note, updatedAt.ms())
fun MealNoteDto.toEntity() = MealNoteEntity(d(date), mealId, note, ms(updatedAt))
fun DayLogEntity.toDto() = DayLogDto(date.s(), markedComplete, note, updatedAt.ms())
fun DayLogDto.toEntity() = DayLogEntity(d(date), markedComplete, note, ms(updatedAt))

fun PortionColumns.toDto() = PortionDto(type.name, foodId, recipeId, name, brand, quantity, unit.name, unitLabel, amountPerUnit, basis.name, referenceAmount, nutrients.toMap(), dataQuality.name)
fun PortionDto.toColumns() = PortionColumns(EntryType.valueOf(type), foodId, recipeId, name, brand, quantity, QuantityUnit.valueOf(unit), unitLabel, amountPerUnit,
    NutrientBasis.valueOf(basis), referenceAmount, nutrients.toColumns(), DataQuality.valueOf(dataQuality))

fun RecipeEntity.toDto(ings: List<RecipeIngredientEntity>) = RecipeDto(id, name, description, instructions, servings, finalWeightGrams, isFavourite, isArchived, createdAt.ms(), updatedAt.ms(),
    ings.map { IngredientDto(it.id, it.foodId, it.quantity, it.unit.name, it.unitLabel, it.amountPerUnit, it.sortOrder, it.note) })
fun RecipeDto.toEntity() = RecipeEntity(id, name, description, instructions, servings, finalWeightGrams, null, isFavourite, isArchived, ms(createdAt), ms(updatedAt))
fun IngredientDto.toEntity(recipeId: String) = RecipeIngredientEntity(id, recipeId, foodId, quantity, QuantityUnit.valueOf(unit), unitLabel, amountPerUnit, sortOrder, note)

fun PlannedMealEntity.toDto() = PlannedMealDto(id, date.s(), mealId, item.toDto(), recurrenceId, loggedEntryId, isSkipped, note, createdAt.ms(), updatedAt.ms())
fun PlannedMealDto.toEntity() = PlannedMealEntity(id, d(date), mealId, item.toColumns(), recurrenceId, loggedEntryId, isSkipped, note, ms(createdAt), ms(updatedAt))
fun PlanRecurrenceEntity.toDto() = RecurrenceDto(id, mealId, item.toDto(), daysOfWeekMask, startDate.s(), endDate?.s(), isActive, createdAt.ms())
fun RecurrenceDto.toEntity() = PlanRecurrenceEntity(id, mealId, item.toColumns(), daysOfWeekMask, d(startDate), endDate?.let(::d), isActive, ms(createdAt))
fun MealPlanEntity.toDto(items: List<MealPlanItemEntity>) = MealPlanDto(id, name, isFavourite, notes, createdAt.ms(), updatedAt.ms(), items.map { MealPlanItemDto(it.id, it.dayOffset, it.mealId, it.item.toDto()) })
fun MealPlanDto.toEntity() = MealPlanEntity(id, name, isFavourite, notes, ms(createdAt), ms(updatedAt))

fun NutritionTargetEntity.toDto() = TargetDto(id, effectiveFrom.s(), values.toMap(), waterMl, macroMethod.name, proteinPct, carbPct, fatPct, origin.name, createdAt.ms())
fun TargetDto.toEntity() = NutritionTargetEntity(id, d(effectiveFrom), values.toColumns(), waterMl, MacroMethod.valueOf(macroMethod), proteinPct, carbPct, fatPct, TargetOrigin.valueOf(origin), ms(createdAt))
fun WaterEntryEntity.toDto() = WaterDto(id, date.s(), amountMl, beverage.name, loggedAt.ms())
fun WaterDto.toEntity() = WaterEntryEntity(id, d(date), amountMl, BeverageType.valueOf(beverage), ms(loggedAt))
fun BodyMeasurementEntity.toDto() = BodyDto(id, date.s(), type.name, customName, value, note, createdAt.ms(), updatedAt.ms())
fun BodyDto.toEntity() = BodyMeasurementEntity(id, d(date), MeasurementType.valueOf(type), customName, value, note, ms(createdAt), ms(updatedAt))
fun UserProfileEntity.toDto() = ProfileDto(displayName, dateOfBirth?.s(), heightCm, sex.name, currentWeightKg, startWeightKg, targetWeightKg, activityLevel.name, goal.name,
    weeklyChangeKg, dietaryPreference.name, allergies, restrictions, createdAt.ms(), updatedAt.ms())
fun ProfileDto.toEntity() = UserProfileEntity(UserProfileEntity.SINGLETON_ID, displayName, dateOfBirth?.let(::d), heightCm, Sex.valueOf(sex), currentWeightKg, startWeightKg,
    targetWeightKg, ActivityLevel.valueOf(activityLevel), Goal.valueOf(goal), weeklyChangeKg, DietaryPreference.valueOf(dietaryPreference), allergies, restrictions, ms(createdAt), ms(updatedAt))
