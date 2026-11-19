package com.fdc.nutrition.data.backup

import androidx.room.withTransaction
import com.fdc.nutrition.data.db.AppDatabase
import com.fdc.nutrition.data.db.entity.*
import com.fdc.nutrition.data.sync.EntityType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads and writes individual user records as JSON (DTO) payloads. Writes go straight to the DAOs
 * so applying remote/backup data is not itself recorded as a local change.
 * Writes are foreign-key safe: references to foods/recipes that don't exist locally are cleared
 * (diary entries keep their nutrition snapshot) rather than failing.
 */
@Singleton
class RecordCodec @Inject constructor(private val db: AppDatabase) {
    val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = true }
    private val rec get() = db.recordDao()

    suspend fun read(type: String, id: String): JsonElement? = when (type) {
        EntityType.FOOD -> db.foodDao().get(id)?.takeIf { it.food.isCustom }?.let { json.encodeToJsonElement(foodDto(it.food, it.nutrients, db.foodDao().servings(id), db.foodDao().barcodes(id))) }
        EntityType.FAVOURITE -> rec.favourite(id)?.let { json.encodeToJsonElement(FavouriteDto(it.foodId, it.createdAt.toEpochMilli())) }
        EntityType.DIARY_ENTRY -> db.diaryDao().get(id)?.let { json.encodeToJsonElement(it.toDto()) }
        EntityType.MEAL -> rec.meal(id)?.let { json.encodeToJsonElement(it.toDto()) }
        EntityType.MEAL_NOTE -> id.split('|').let { (d, m) -> rec.mealNote(LocalDate.parse(d), m) }?.let { json.encodeToJsonElement(it.toDto()) }
        EntityType.DAY_LOG -> rec.dayLog(LocalDate.parse(id))?.let { json.encodeToJsonElement(it.toDto()) }
        EntityType.SAVED_MEAL -> rec.savedMeal(id)?.let { m -> json.encodeToJsonElement(SavedMealDto(m.id, m.name, m.defaultMealId, m.createdAt.toEpochMilli(), m.updatedAt.toEpochMilli(), db.diaryDao().savedMealItems(id).map { it.item.toDto() })) }
        EntityType.RECIPE -> db.recipeDao().get(id)?.let { json.encodeToJsonElement(it.toDto(db.recipeDao().ingredients(id))) }
        EntityType.PLANNED_MEAL -> db.planDao().getPlanned(id)?.let { json.encodeToJsonElement(it.toDto()) }
        EntityType.RECURRENCE -> rec.recurrence(id)?.let { json.encodeToJsonElement(it.toDto()) }
        EntityType.MEAL_PLAN -> db.planDao().getMealPlan(id)?.let { json.encodeToJsonElement(it.toDto(db.planDao().mealPlanItems(id))) }
        EntityType.TARGET -> rec.target(id)?.let { json.encodeToJsonElement(it.toDto()) }
        EntityType.WATER -> db.waterDao().get(id)?.let { json.encodeToJsonElement(it.toDto()) }
        EntityType.BODY -> db.bodyDao().get(id)?.let { json.encodeToJsonElement(it.toDto()) }
        EntityType.PROFILE -> db.profileDao().profile()?.let { json.encodeToJsonElement(it.toDto()) }
        else -> null
    }

    suspend fun allIds(type: String): List<String> = when (type) {
        EntityType.FOOD -> rec.customFoodIds()
        EntityType.FAVOURITE -> rec.favourites().map { it.foodId }
        EntityType.DIARY_ENTRY -> rec.diaryIds()
        EntityType.MEAL -> rec.mealIds()
        EntityType.MEAL_NOTE -> rec.mealNoteIds()
        EntityType.DAY_LOG -> rec.dayLogDates().map { it.toString() }
        EntityType.SAVED_MEAL -> rec.savedMealIds()
        EntityType.RECIPE -> rec.recipeIds()
        EntityType.PLANNED_MEAL -> rec.plannedIds()
        EntityType.RECURRENCE -> rec.recurrenceIds()
        EntityType.MEAL_PLAN -> rec.mealPlanIds()
        EntityType.TARGET -> rec.targetIds()
        EntityType.WATER -> rec.waterIds()
        EntityType.BODY -> rec.bodyIds()
        EntityType.PROFILE -> listOfNotNull(db.profileDao().profile()?.let { "me" })
        else -> emptyList()
    }

    private suspend fun foodOrNull(id: String?) = id?.takeIf { rec.foodExists(it) > 0 }
    private suspend fun recipeOrNull(id: String?) = id?.takeIf { rec.recipeExists(it) > 0 }
    private suspend fun safePortion(p: PortionColumns) = p.copy(foodId = foodOrNull(p.foodId), recipeId = recipeOrNull(p.recipeId))
    private suspend fun ensureMeal(id: String) { if (rec.meal(id) == null) db.diaryDao().upsertMeal(MealEntity(id, id.replace('_', ' ').replaceFirstChar { it.uppercase() }, 50, false)) }

    /** Upserts a record from its JSON payload. */
    suspend fun write(type: String, payload: JsonElement) = db.withTransaction {
        when (type) {
            EntityType.FOOD -> { val f = json.decodeFromJsonElement<FoodDto>(payload)
                db.foodDao().insertFoodGraph(f.toEntity(), FoodNutrientsEntity(f.id, f.nutrients.toColumns()), f.servings.map { it.toEntity(f.id) }, f.barcodes.map { FoodBarcodeEntity(it, f.id, Instant.ofEpochMilli(f.updatedAt)) }) }
            EntityType.FAVOURITE -> { val f = json.decodeFromJsonElement<FavouriteDto>(payload); if (foodOrNull(f.foodId) != null) db.foodDao().addFavourite(FoodFavouriteEntity(f.foodId, Instant.ofEpochMilli(f.createdAt))) }
            EntityType.DIARY_ENTRY -> { val e = json.decodeFromJsonElement<DiaryEntryDto>(payload).toEntity(); ensureMeal(e.mealId)
                db.diaryDao().upsert(e.copy(foodId = foodOrNull(e.foodId), recipeId = recipeOrNull(e.recipeId))) }
            EntityType.MEAL -> db.diaryDao().upsertMeal(json.decodeFromJsonElement<MealDto>(payload).toEntity())
            EntityType.MEAL_NOTE -> db.diaryDao().upsertMealNote(json.decodeFromJsonElement<MealNoteDto>(payload).toEntity())
            EntityType.DAY_LOG -> db.diaryDao().upsertDayLog(json.decodeFromJsonElement<DayLogDto>(payload).toEntity())
            EntityType.SAVED_MEAL -> { val m = json.decodeFromJsonElement<SavedMealDto>(payload); rec.deleteSavedMealItems(m.id)
                db.diaryDao().saveMealTemplate(SavedMealEntity(m.id, m.name, m.defaultMealId, Instant.ofEpochMilli(m.createdAt), Instant.ofEpochMilli(m.updatedAt)),
                    m.items.mapIndexed { i, p -> SavedMealItemEntity("${m.id}:$i", m.id, safePortion(p.toColumns()), i) }) }
            EntityType.RECIPE -> { val r = json.decodeFromJsonElement<RecipeDto>(payload)
                val existingImage = db.recipeDao().get(r.id)?.imageUri
                db.recipeDao().save(r.toEntity().copy(imageUri = existingImage), r.ingredients.filter { foodOrNull(it.foodId) != null }.map { it.toEntity(r.id) }) }
            EntityType.PLANNED_MEAL -> { val p = json.decodeFromJsonElement<PlannedMealDto>(payload).toEntity(); db.planDao().upsertPlanned(p.copy(item = safePortion(p.item))) }
            EntityType.RECURRENCE -> { val r = json.decodeFromJsonElement<RecurrenceDto>(payload).toEntity(); db.planDao().upsertRecurrence(r.copy(item = safePortion(r.item))) }
            EntityType.MEAL_PLAN -> { val m = json.decodeFromJsonElement<MealPlanDto>(payload)
                db.planDao().saveMealPlan(m.toEntity(), m.items.map { MealPlanItemEntity(it.id, m.id, it.dayOffset, it.mealId, safePortion(it.item.toColumns())) }) }
            EntityType.TARGET -> { val t = json.decodeFromJsonElement<TargetDto>(payload).toEntity(); rec.deleteTargetsOnDateExcept(t.effectiveFrom, t.id); db.profileDao().upsertTarget(t) }
            EntityType.WATER -> db.waterDao().upsert(json.decodeFromJsonElement<WaterDto>(payload).toEntity())
            EntityType.BODY -> db.bodyDao().upsert(json.decodeFromJsonElement<BodyDto>(payload).toEntity())
            EntityType.PROFILE -> db.profileDao().upsertProfile(json.decodeFromJsonElement<ProfileDto>(payload).toEntity())
            else -> Unit
        }
    }

    /** Deletes a record locally. Foods/recipes still referenced by history are archived instead. */
    suspend fun delete(type: String, id: String) = db.withTransaction {
        when (type) {
            EntityType.FOOD -> if (db.foodDao().referenceCount(id) > 0) db.foodDao().setArchived(id, true, Instant.now()) else db.foodDao().deleteFood(id)
            EntityType.FAVOURITE -> db.foodDao().removeFavourite(id)
            EntityType.DIARY_ENTRY -> { db.planDao().clearLoggedEntry(id); db.diaryDao().deleteById(id) }
            EntityType.MEAL_NOTE -> id.split('|').let { (d, m) -> db.diaryDao().deleteMealNote(LocalDate.parse(d), m) }
            EntityType.DAY_LOG -> rec.deleteDayLog(LocalDate.parse(id))
            EntityType.SAVED_MEAL -> db.diaryDao().deleteSavedMeal(id)
            EntityType.RECIPE -> if (db.recipeDao().referenceCount(id) > 0) db.recipeDao().get(id)?.let { db.recipeDao().upsert(it.copy(isArchived = true)) } else db.recipeDao().delete(id)
            EntityType.PLANNED_MEAL -> db.planDao().deletePlanned(id)
            EntityType.RECURRENCE -> rec.deleteRecurrence(id)
            EntityType.MEAL_PLAN -> db.planDao().deleteMealPlan(id)
            EntityType.TARGET -> rec.deleteTarget(id)
            EntityType.WATER -> db.waterDao().delete(id)
            EntityType.BODY -> db.bodyDao().delete(id)
            else -> Unit
        }
    }
}
