package com.fdc.nutrition.data.backup

import androidx.room.withTransaction
import com.fdc.nutrition.data.db.AppDatabase
import com.fdc.nutrition.data.sync.ChangeTracker
import com.fdc.nutrition.data.sync.EntityType
import com.fdc.nutrition.data.util.TimeProvider
import kotlinx.serialization.json.*
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

enum class ConflictStrategy { KEEP_EXISTING, REPLACE_WITH_BACKUP }

data class RestorePreview(
    val exportedAt: String,
    val total: Int,
    val new: Int,
    val identical: Int,
    val conflicting: Int,
    val invalid: List<String>,
    val byType: Map<String, Int>,
)

data class RestoreResult(val inserted: Int, val replaced: Int, val keptExisting: Int, val unchanged: Int, val invalid: List<String>)

class BackupFormatException(message: String) : Exception(message)

/**
 * JSON backup and restore. Restore validates the whole file first, then merges inside a single
 * transaction: new records are added, identical ones skipped, and records that differ from local
 * data are only replaced when the user explicitly chooses [ConflictStrategy.REPLACE_WITH_BACKUP].
 */
@Singleton
class BackupRepository @Inject constructor(
    private val db: AppDatabase,
    private val codec: RecordCodec,
    private val time: TimeProvider,
    private val changes: ChangeTracker,
) {
    private val json get() = codec.json
    private val pretty = Json { prettyPrint = true; encodeDefaults = true; explicitNulls = true }

    suspend fun export(appVersion: String): String {
        val data = db.withTransaction {
            suspend fun <T> all(type: String, decode: (JsonElement) -> T): List<T> = codec.allIds(type).mapNotNull { codec.read(type, it) }.map(decode)
            BackupData(
                profile = codec.read(EntityType.PROFILE, "me")?.let { json.decodeFromJsonElement(ProfileDto.serializer(), it) },
                targets = all(EntityType.TARGET) { json.decodeFromJsonElement(TargetDto.serializer(), it) },
                meals = all(EntityType.MEAL) { json.decodeFromJsonElement(MealDto.serializer(), it) },
                foods = all(EntityType.FOOD) { json.decodeFromJsonElement(FoodDto.serializer(), it) },
                userServings = db.recordDao().userServingsOnDatabaseFoods().groupBy { it.foodId }.mapValues { (_, l) -> l.map { it.toDto() } },
                favourites = all(EntityType.FAVOURITE) { json.decodeFromJsonElement(FavouriteDto.serializer(), it) },
                diary = all(EntityType.DIARY_ENTRY) { json.decodeFromJsonElement(DiaryEntryDto.serializer(), it) },
                mealNotes = all(EntityType.MEAL_NOTE) { json.decodeFromJsonElement(MealNoteDto.serializer(), it) },
                dayLogs = all(EntityType.DAY_LOG) { json.decodeFromJsonElement(DayLogDto.serializer(), it) },
                savedMeals = all(EntityType.SAVED_MEAL) { json.decodeFromJsonElement(SavedMealDto.serializer(), it) },
                recipes = all(EntityType.RECIPE) { json.decodeFromJsonElement(RecipeDto.serializer(), it) },
                plannedMeals = all(EntityType.PLANNED_MEAL) { json.decodeFromJsonElement(PlannedMealDto.serializer(), it) },
                recurrences = all(EntityType.RECURRENCE) { json.decodeFromJsonElement(RecurrenceDto.serializer(), it) },
                mealPlans = all(EntityType.MEAL_PLAN) { json.decodeFromJsonElement(MealPlanDto.serializer(), it) },
                water = all(EntityType.WATER) { json.decodeFromJsonElement(WaterDto.serializer(), it) },
                body = all(EntityType.BODY) { json.decodeFromJsonElement(BodyDto.serializer(), it) },
            )
        }
        return pretty.encodeToString(BackupFile.serializer(), BackupFile(appVersion = appVersion, exportedAt = time.now().toString(), data = data))
    }

    private data class Rec(val type: String, val id: String, val payload: JsonElement)

    /** Parses and validates a backup. Throws [BackupFormatException] if the file is not a usable backup. */
    fun parse(text: String): BackupFile {
        val file = try { json.decodeFromString(BackupFile.serializer(), text) } catch (e: Exception) {
            throw BackupFormatException("This file isn't a valid FDC Nutrition backup (${e.message?.take(120)}).")
        }
        if (file.format != BackupFile.FORMAT) throw BackupFormatException("Unrecognised backup format \"${file.format}\".")
        if (file.schemaVersion > BackupFile.SCHEMA_VERSION) throw BackupFormatException("This backup was made by a newer app version (schema ${file.schemaVersion}). Please update the app.")
        return file
    }

    private fun records(d: BackupData): List<Rec> = buildList {
        fun <T> addAll(type: String, items: List<T>, ser: kotlinx.serialization.KSerializer<T>, id: (T) -> String) = items.forEach { add(Rec(type, id(it), json.encodeToJsonElement(ser, it))) }
        addAll(EntityType.FOOD, d.foods, FoodDto.serializer()) { it.id }
        addAll(EntityType.MEAL, d.meals, MealDto.serializer()) { it.id }
        addAll(EntityType.RECIPE, d.recipes, RecipeDto.serializer()) { it.id }
        addAll(EntityType.FAVOURITE, d.favourites, FavouriteDto.serializer()) { it.foodId }
        addAll(EntityType.DIARY_ENTRY, d.diary, DiaryEntryDto.serializer()) { it.id }
        addAll(EntityType.MEAL_NOTE, d.mealNotes, MealNoteDto.serializer()) { "${it.date}|${it.mealId}" }
        addAll(EntityType.DAY_LOG, d.dayLogs, DayLogDto.serializer()) { it.date }
        addAll(EntityType.SAVED_MEAL, d.savedMeals, SavedMealDto.serializer()) { it.id }
        addAll(EntityType.PLANNED_MEAL, d.plannedMeals, PlannedMealDto.serializer()) { it.id }
        addAll(EntityType.RECURRENCE, d.recurrences, RecurrenceDto.serializer()) { it.id }
        addAll(EntityType.MEAL_PLAN, d.mealPlans, MealPlanDto.serializer()) { it.id }
        addAll(EntityType.TARGET, d.targets, TargetDto.serializer()) { it.id }
        addAll(EntityType.WATER, d.water, WaterDto.serializer()) { it.id }
        addAll(EntityType.BODY, d.body, BodyDto.serializer()) { it.id }
        d.profile?.let { add(Rec(EntityType.PROFILE, "me", json.encodeToJsonElement(ProfileDto.serializer(), it))) }
    }

    /** Semantic validation of one record; returns an error message or null. */
    private fun validate(r: Rec): String? = runCatching {
        fun finite(vararg v: Double?) = v.filterNotNull().all { it.isFinite() }
        fun nonNeg(m: Map<String, Double>) = m.values.all { it.isFinite() && it >= 0 }
        when (r.type) {
            EntityType.FOOD -> json.decodeFromJsonElement(FoodDto.serializer(), r.payload).let { it.toEntity(); require(it.name.isNotBlank() && nonNeg(it.nutrients)) { "invalid values" }; it.servings.forEach { s -> s.toEntity(it.id); require(s.amount > 0) } }
            EntityType.DIARY_ENTRY -> json.decodeFromJsonElement(DiaryEntryDto.serializer(), r.payload).let { it.toEntity(); require(it.quantity > 0 && it.amountPerUnit > 0 && it.referenceAmount > 0 && nonNeg(it.snapshot)) { "invalid quantity or nutrients" } }
            EntityType.MEAL -> json.decodeFromJsonElement(MealDto.serializer(), r.payload).let { require(it.name.isNotBlank()) }
            EntityType.MEAL_NOTE -> json.decodeFromJsonElement(MealNoteDto.serializer(), r.payload).toEntity()
            EntityType.DAY_LOG -> json.decodeFromJsonElement(DayLogDto.serializer(), r.payload).toEntity()
            EntityType.SAVED_MEAL -> json.decodeFromJsonElement(SavedMealDto.serializer(), r.payload).items.forEach { require(it.quantity > 0 && nonNeg(it.nutrients)); it.toColumns() }
            EntityType.RECIPE -> json.decodeFromJsonElement(RecipeDto.serializer(), r.payload).let { it.toEntity(); require(it.servings > 0 && finite(it.finalWeightGrams)); it.ingredients.forEach { i -> require(i.quantity > 0); i.toEntity(it.id) } }
            EntityType.PLANNED_MEAL -> json.decodeFromJsonElement(PlannedMealDto.serializer(), r.payload).let { it.toEntity(); require(it.item.quantity > 0) }
            EntityType.RECURRENCE -> json.decodeFromJsonElement(RecurrenceDto.serializer(), r.payload).toEntity()
            EntityType.MEAL_PLAN -> json.decodeFromJsonElement(MealPlanDto.serializer(), r.payload).let { it.items.forEach { i -> i.item.toColumns() } }
            EntityType.TARGET -> json.decodeFromJsonElement(TargetDto.serializer(), r.payload).let { it.toEntity(); require(nonNeg(it.values) && it.waterMl >= 0) }
            EntityType.WATER -> json.decodeFromJsonElement(WaterDto.serializer(), r.payload).let { it.toEntity(); require(it.amountMl > 0 && it.amountMl.isFinite()) }
            EntityType.BODY -> json.decodeFromJsonElement(BodyDto.serializer(), r.payload).let { it.toEntity(); require(it.value > 0 && it.value.isFinite()) }
            EntityType.PROFILE -> json.decodeFromJsonElement(ProfileDto.serializer(), r.payload).toEntity()
            EntityType.FAVOURITE -> json.decodeFromJsonElement(FavouriteDto.serializer(), r.payload)
        }
        null
    }.getOrElse { "${r.type} ${r.id}: ${it.message ?: it::class.simpleName}" }

    private enum class Status { NEW, IDENTICAL, CONFLICT }

    private suspend fun status(r: Rec): Status {
        val local = codec.read(r.type, r.id)
        if (r.type == EntityType.TARGET && local == null) {
            // A different local target version on the same date also counts as a conflict.
            val date = LocalDate.parse(r.payload.jsonObject["effectiveFrom"]!!.jsonPrimitive.content)
            if (db.profileDao().targetFor(date) != null) return Status.CONFLICT
        }
        return when {
            local == null -> Status.NEW
            normalise(local) == normalise(r.payload) -> Status.IDENTICAL
            else -> Status.CONFLICT
        }
    }

    private fun normalise(e: JsonElement): String = json.encodeToString(JsonElement.serializer(), e)

    suspend fun preview(text: String): RestorePreview {
        val file = parse(text)
        val recs = records(file.data)
        val invalid = recs.mapNotNull { validate(it) }
        val valid = recs.filter { validate(it) == null }
        val statuses = db.withTransaction { valid.map { status(it) } }
        return RestorePreview(file.exportedAt, recs.size, statuses.count { it == Status.NEW }, statuses.count { it == Status.IDENTICAL },
            statuses.count { it == Status.CONFLICT }, invalid, recs.groupingBy { it.type }.eachCount())
    }

    suspend fun restore(text: String, strategy: ConflictStrategy = ConflictStrategy.KEEP_EXISTING): RestoreResult {
        val file = parse(text)
        val recs = records(file.data)
        val invalid = mutableListOf<String>()
        var inserted = 0; var replaced = 0; var kept = 0; var unchanged = 0
        db.withTransaction {
            for (r in recs) {
                validate(r)?.let { invalid += it; continue }
                when (status(r)) {
                    Status.NEW -> { codec.write(r.type, r.payload); changes.upserted(r.type, r.id); inserted++ }
                    Status.IDENTICAL -> unchanged++
                    Status.CONFLICT -> if (strategy == ConflictStrategy.REPLACE_WITH_BACKUP) { codec.write(r.type, r.payload); changes.upserted(r.type, r.id); replaced++ } else kept++
                }
            }
            val dao = db.foodDao()
            file.data.userServings.forEach { (foodId, servings) ->
                if (db.recordDao().foodExists(foodId) > 0) {
                    val existing = dao.servings(foodId).map { it.id }.toSet()
                    dao.upsertServings(servings.filter { it.id !in existing }.map { it.toEntity(foodId) })
                }
            }
        }
        return RestoreResult(inserted, replaced, kept, unchanged, invalid)
    }

    /** Deletes all personal data while keeping the bundled food database. */
    suspend fun deleteAllUserData() = db.withTransaction {
        val r = db.recordDao()
        r.clearDiary(); r.clearMealNotes(); r.clearDayLogs(); r.clearSavedMeals(); r.clearPlanned(); r.clearRecurrences(); r.clearMealPlans()
        r.clearRecipes(); r.clearWater(); r.clearBody(); r.clearTargets(); r.clearProfile(); r.clearPreferences(); r.clearFavourites()
        r.clearSearchHistory(); r.clearUserServings(); r.clearCustomFoods(); r.unarchiveDatabaseFoods(); r.clearCustomMeals()
        r.clearSyncQueue(); r.clearSyncConflicts(); r.clearSyncVersions(); r.clearSyncMetadata()
        changes.enabled = false
    }
}
