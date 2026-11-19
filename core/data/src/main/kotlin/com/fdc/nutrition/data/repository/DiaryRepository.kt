package com.fdc.nutrition.data.repository

import androidx.room.withTransaction
import com.fdc.nutrition.data.db.AppDatabase
import com.fdc.nutrition.data.db.entity.DayLogEntity
import com.fdc.nutrition.data.db.entity.MealEntity
import com.fdc.nutrition.data.db.entity.MealNoteEntity
import com.fdc.nutrition.data.db.entity.SavedMealEntity
import com.fdc.nutrition.data.db.entity.SavedMealItemEntity
import com.fdc.nutrition.data.db.toDomain
import com.fdc.nutrition.data.db.toEntity
import com.fdc.nutrition.data.db.toPortion
import com.fdc.nutrition.data.db.nutrientsConsumed
import com.fdc.nutrition.data.sync.ChangeTracker
import com.fdc.nutrition.data.sync.EntityType
import com.fdc.nutrition.data.util.TimeProvider
import com.fdc.nutrition.domain.calc.Ids
import com.fdc.nutrition.domain.calc.RecipeCalculator
import com.fdc.nutrition.domain.calc.ServingConverter
import com.fdc.nutrition.domain.calc.ServingOption
import com.fdc.nutrition.domain.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

data class SavedMeal(val id: String, val name: String, val defaultMealId: String?, val items: List<SavedMealItemEntity>) {
    val kcal: Double get() = items.sumOf { it.item.nutrientsConsumed().energyKcal ?: 0.0 }
}

/** A food recently logged, with the quantity used last time for one-tap re-logging. */
data class RecentItem(val entry: DiaryEntry, val timesLogged: Int)

/** Recipe logging options: per-serving, or by weight when the recipe weight is known. */
data class RecipeLogBasis(val basis: NutrientBasis, val referenceAmount: Double, val nutrients: Nutrients, val options: List<ServingOption>)

@Singleton
class DiaryRepository @Inject constructor(
    private val db: AppDatabase,
    private val foods: FoodRepository,
    private val recipes: RecipeRepository,
    private val time: TimeProvider,
    private val changes: ChangeTracker,
) {
    private val dao = db.diaryDao()

    fun observeMeals(): Flow<List<Meal>> = dao.observeMeals().map { l -> l.map { it.toDomain() } }
    fun observeEntries(date: LocalDate): Flow<List<DiaryEntry>> = dao.observeForDate(date).map { l -> l.map { it.toDomain() } }
    fun observeEntry(id: String): Flow<DiaryEntry?> = dao.observe(id).map { it?.toDomain() }
    fun observeRange(from: LocalDate, to: LocalDate): Flow<List<DiaryEntry>> = dao.observeRange(from, to).map { l -> l.map { it.toDomain() } }
    suspend fun range(from: LocalDate, to: LocalDate): List<DiaryEntry> = dao.range(from, to).map { it.toDomain() }
    suspend fun entry(id: String): DiaryEntry? = dao.get(id)?.toDomain()
    fun observeMealNotes(date: LocalDate): Flow<Map<String, String>> = dao.observeMealNotes(date).map { l -> l.associate { it.mealId to it.note } }
    fun observeDayLog(date: LocalDate) = dao.observeDayLog(date)
    fun observeDayLogs(from: LocalDate, to: LocalDate) = dao.observeDayLogs(from, to)
    fun observeDayStats(from: LocalDate, to: LocalDate) = dao.observeDayStats(from, to)

    fun observeRecent(limit: Int = 20): Flow<List<RecentItem>> = dao.observeRecent(300).map { list ->
        val counts = list.groupingBy { it.foodId ?: "r:${it.recipeId}" }.eachCount()
        list.distinctBy { it.foodId ?: "r:${it.recipeId}" }.take(limit).map { RecentItem(it.toDomain(), counts[it.foodId ?: "r:${it.recipeId}"] ?: 1) }
    }

    fun observeFrequent(limit: Int = 12): Flow<List<RecentItem>> = dao.observeRecent(500).map { list ->
        val groups = list.groupBy { it.foodId ?: "r:${it.recipeId}" }
        groups.values.sortedByDescending { it.size }.take(limit).map { RecentItem(it.first().toDomain(), it.size) }
    }

    private fun validateQuantity(q: Double) {
        if (!(q > 0 && q.isFinite())) throw ValidationException("Quantity must be greater than zero.")
        if (q > 100_000) throw ValidationException("Quantity is unrealistically large.")
    }

    private suspend fun requireMeal(mealId: String) {
        if (dao.allMeals().none { it.id == mealId }) throw ValidationException("Unknown meal")
    }

    /** Logs a food with a nutrition snapshot taken from the food's current definition. */
    suspend fun logFood(date: LocalDate, mealId: String, foodId: String, quantity: Double, option: ServingOption, note: String? = null, plannedKey: String? = null): String {
        validateQuantity(quantity)
        val food = foods.getFood(foodId) ?: throw ValidationException("Food not found")
        val now = time.now()
        val entry = DiaryEntry(
            id = Ids.new(), date = date, mealId = mealId, type = EntryType.FOOD, foodId = food.id, recipeId = null, name = food.name, brand = food.brand,
            quantity = quantity, unit = option.unit, unitLabel = option.label, amountPerUnit = option.amountPerUnit, basis = food.basis,
            referenceAmount = 100.0, nutrientsPerReference = food.nutrientsPer100, dataQuality = food.dataQuality, preparation = food.preparation,
            note = note?.ifBlank { null }, plannedSourceKey = plannedKey, loggedAt = now, createdAt = now, updatedAt = now,
        )
        insert(entry)
        return entry.id
    }

    suspend fun recipeLogBasis(recipeId: String): RecipeLogBasis {
        val recipe = recipes.getRecipe(recipeId) ?: throw ValidationException("Recipe not found")
        val n = RecipeCalculator.calculate(recipe)
        val per100 = n.per100g
        val servingW = n.servingWeightG
        return if (per100 != null && servingW != null) {
            RecipeLogBasis(NutrientBasis.GRAM, 100.0, per100, listOf(
                ServingOption("serving", QuantityUnit.SERVING, "serving (${"%.0f".format(servingW)} g)", servingW, PortionKind.PREPARED_SERVING, true),
                ServingOption("u:GRAM", QuantityUnit.GRAM, "g", 1.0, PortionKind.PREPARED_SERVING, false),
            ))
        } else RecipeLogBasis(NutrientBasis.SERVING, 1.0, n.perServing, listOf(ServingOption("serving", QuantityUnit.SERVING, "serving", 1.0, PortionKind.PREPARED_SERVING, true)))
    }

    /** Logs servings (or grams) of a recipe with a snapshot of its current calculated nutrition. */
    suspend fun logRecipe(date: LocalDate, mealId: String, recipeId: String, quantity: Double, option: ServingOption? = null, plannedKey: String? = null): String {
        validateQuantity(quantity)
        val recipe = recipes.getRecipe(recipeId) ?: throw ValidationException("Recipe not found")
        val b = recipeLogBasis(recipeId)
        val opt = option ?: b.options.first()
        val now = time.now()
        val entry = DiaryEntry(
            id = Ids.new(), date = date, mealId = mealId, type = EntryType.RECIPE, foodId = null, recipeId = recipe.id, name = recipe.name, brand = null,
            quantity = quantity, unit = opt.unit, unitLabel = opt.label, amountPerUnit = opt.amountPerUnit, basis = b.basis,
            referenceAmount = b.referenceAmount, nutrientsPerReference = b.nutrients, dataQuality = DataQuality.CALCULATED,
            preparation = PreparationState.PREPARED, note = null, plannedSourceKey = plannedKey, loggedAt = now, createdAt = now, updatedAt = now,
        )
        insert(entry)
        return entry.id
    }

    /**
     * Quick calorie logging. Only the values the user enters are stored — unspecified macros stay
     * unknown, so the entry is clearly calorie-only.
     */
    suspend fun quickAdd(date: LocalDate, mealId: String, name: String?, kcal: Double, proteinG: Double? = null, carbG: Double? = null, fatG: Double? = null): String {
        if (!(kcal >= 0 && kcal <= 10_000)) throw ValidationException("Calories must be between 0 and 10,000.")
        listOfNotNull(proteinG, carbG, fatG).forEach { if (it < 0 || it > 2000) throw ValidationException("Macros must be between 0 and 2,000 g.") }
        val now = time.now()
        val entry = DiaryEntry(
            id = Ids.new(), date = date, mealId = mealId, type = EntryType.QUICK_ADD, foodId = null, recipeId = null,
            name = name?.trim()?.ifBlank { null } ?: "Quick add", brand = null, quantity = 1.0, unit = QuantityUnit.SERVING, unitLabel = "entry",
            amountPerUnit = 1.0, basis = NutrientBasis.SERVING, referenceAmount = 1.0,
            nutrientsPerReference = Nutrients.of(Nutrient.ENERGY to kcal, Nutrient.PROTEIN to proteinG, Nutrient.CARBOHYDRATE to carbG, Nutrient.FAT to fatG),
            dataQuality = DataQuality.USER_ENTERED, loggedAt = now, createdAt = now, updatedAt = now,
        )
        insert(entry)
        return entry.id
    }

    /** One-tap re-log of a recent item with its previous quantity, using the current food definition. */
    suspend fun relog(recent: DiaryEntry, date: LocalDate, mealId: String): String = when (recent.type) {
        EntryType.FOOD -> logFood(date, mealId, recent.foodId!!, recent.quantity, optionFor(recent))
        EntryType.RECIPE -> logRecipe(date, mealId, recent.recipeId!!, recent.quantity, recipeLogBasis(recent.recipeId!!).options.firstOrNull { it.unit == recent.unit })
        EntryType.QUICK_ADD -> duplicate(recent.id, date, mealId)
    }

    private fun optionFor(e: DiaryEntry) = ServingOption("entry", e.unit, e.unitLabel, e.amountPerUnit, isFoodSpecific = true)

    private suspend fun insert(entry: DiaryEntry) {
        requireMeal(entry.mealId)
        db.withTransaction { dao.insert(entry.toEntity()); changes.upserted(EntityType.DIARY_ENTRY, entry.id) }
    }

    /** Changes quantity/unit/meal/date/note. Nutrition is recalculated from the stored snapshot. */
    suspend fun updateEntry(id: String, quantity: Double? = null, option: ServingOption? = null, mealId: String? = null, date: LocalDate? = null, note: String? = null) {
        quantity?.let { validateQuantity(it) }
        mealId?.let { requireMeal(it) }
        db.withTransaction {
            val e = dao.get(id)?.toDomain() ?: throw ValidationException("Entry not found")
            val updated = e.copy(
                quantity = quantity ?: e.quantity,
                unit = option?.unit ?: e.unit, unitLabel = option?.label ?: e.unitLabel, amountPerUnit = option?.amountPerUnit ?: e.amountPerUnit,
                mealId = mealId ?: e.mealId, date = date ?: e.date, note = note?.ifBlank { null } ?: if (note != null) null else e.note,
                updatedAt = time.now(),
            )
            dao.update(updated.toEntity())
            changes.upserted(EntityType.DIARY_ENTRY, id)
        }
    }

    /** Quick-add entries can have their calories/macros corrected directly. */
    suspend fun updateQuickAdd(id: String, name: String?, kcal: Double, proteinG: Double?, carbG: Double?, fatG: Double?) {
        if (!(kcal >= 0 && kcal <= 10_000)) throw ValidationException("Calories must be between 0 and 10,000.")
        db.withTransaction {
            val e = dao.get(id)?.toDomain() ?: throw ValidationException("Entry not found")
            require(e.type == EntryType.QUICK_ADD)
            dao.update(e.copy(name = name?.trim()?.ifBlank { null } ?: e.name,
                nutrientsPerReference = Nutrients.of(Nutrient.ENERGY to kcal, Nutrient.PROTEIN to proteinG, Nutrient.CARBOHYDRATE to carbG, Nutrient.FAT to fatG),
                updatedAt = time.now()).toEntity())
            changes.upserted(EntityType.DIARY_ENTRY, id)
        }
    }

    /**
     * Explicit correction: replaces an entry's snapshot with the food/recipe's *current* nutrition.
     * Historical entries are never changed automatically.
     */
    suspend fun refreshSnapshot(id: String) {
        val e = dao.get(id)?.toDomain() ?: throw ValidationException("Entry not found")
        val updated = when (e.type) {
            EntryType.FOOD -> { val f = foods.getFood(e.foodId!!) ?: throw ValidationException("Food no longer exists"); e.copy(nutrientsPerReference = f.nutrientsPer100, name = f.name, brand = f.brand, dataQuality = f.dataQuality, referenceAmount = 100.0, basis = f.basis) }
            EntryType.RECIPE -> { val b = recipeLogBasis(e.recipeId!!); val opt = b.options.firstOrNull { it.unit == e.unit } ?: b.options.first(); e.copy(nutrientsPerReference = b.nutrients, referenceAmount = b.referenceAmount, basis = b.basis, amountPerUnit = opt.amountPerUnit, unitLabel = opt.label) }
            EntryType.QUICK_ADD -> return
        }
        db.withTransaction { dao.update(updated.copy(updatedAt = time.now()).toEntity()); changes.upserted(EntityType.DIARY_ENTRY, id) }
    }

    suspend fun duplicate(id: String, date: LocalDate? = null, mealId: String? = null): String {
        val e = dao.get(id)?.toDomain() ?: throw ValidationException("Entry not found")
        val now = time.now()
        val copy = e.copy(id = Ids.new(), date = date ?: e.date, mealId = mealId ?: e.mealId, plannedSourceKey = null, loggedAt = now, createdAt = now, updatedAt = now)
        insert(copy)
        return copy.id
    }

    suspend fun delete(id: String) {
        db.withTransaction {
            db.planDao().clearLoggedEntry(id)
            dao.deleteById(id)
            changes.deleted(EntityType.DIARY_ENTRY, id)
        }
    }

    suspend fun restore(entry: DiaryEntry) = insert(entry)

    /** Copies every entry from one meal to another date/meal in a single transaction. */
    suspend fun copyMeal(fromDate: LocalDate, fromMealId: String, toDate: LocalDate, toMealId: String): Int {
        requireMeal(toMealId)
        return db.withTransaction {
            val src = dao.forMeal(fromDate, fromMealId)
            val now = time.now()
            val copies = src.map { it.copy(id = Ids.new(), date = toDate, mealId = toMealId, plannedSourceKey = null, loggedAt = now, createdAt = now, updatedAt = now) }
            dao.insertAll(copies)
            copies.forEach { changes.upserted(EntityType.DIARY_ENTRY, it.id) }
            copies.size
        }
    }

    suspend fun moveEntries(ids: List<String>, toMealId: String, toDate: LocalDate? = null) {
        requireMeal(toMealId)
        db.withTransaction { ids.forEach { updateEntry(it, mealId = toMealId, date = toDate) } }
    }

    suspend fun setMealNote(date: LocalDate, mealId: String, note: String) {
        db.withTransaction {
            if (note.isBlank()) dao.deleteMealNote(date, mealId) else dao.upsertMealNote(MealNoteEntity(date, mealId, note.trim(), time.now()))
            changes.upserted(EntityType.MEAL_NOTE, "$date|$mealId")
        }
    }

    suspend fun setDayComplete(date: LocalDate, complete: Boolean) {
        db.withTransaction { dao.upsertDayLog(DayLogEntity(date, complete, null, time.now())); changes.upserted(EntityType.DAY_LOG, date.toString()) }
    }

    suspend fun addCustomMeal(name: String): String {
        if (name.isBlank()) throw ValidationException("Meal name is required.")
        val id = "custom_" + Ids.new().take(8)
        val order = (dao.allMeals().maxOfOrNull { it.sortOrder } ?: 4) + 1
        db.withTransaction { dao.upsertMeal(MealEntity(id, name.trim(), order, false)); changes.upserted(EntityType.MEAL, id) }
        return id
    }

    suspend fun archiveMeal(id: String) {
        val m = dao.allMeals().firstOrNull { it.id == id } ?: return
        if (m.isDefault) throw ValidationException("Default meals can't be removed.")
        db.withTransaction { dao.upsertMeal(m.copy(isArchived = true)); changes.upserted(EntityType.MEAL, id) }
    }

    // ---- saved (favourite) meals
    fun observeSavedMeals(): Flow<List<SavedMeal>> = kotlinx.coroutines.flow.combine(dao.observeSavedMeals(), dao.observeAllSavedMealItems()) { meals, items ->
        val by = items.groupBy { it.savedMealId }
        meals.map { SavedMeal(it.id, it.name, it.defaultMealId, by[it.id].orEmpty()) }
    }

    suspend fun saveMealAsFavourite(date: LocalDate, mealId: String, name: String): String {
        if (name.isBlank()) throw ValidationException("Name is required.")
        val entries = dao.forMeal(date, mealId).map { it.toDomain() }
        if (entries.isEmpty()) throw ValidationException("This meal has no foods to save.")
        val id = Ids.new()
        val now = time.now()
        db.withTransaction {
            dao.saveMealTemplate(SavedMealEntity(id, name.trim(), mealId, now, now), entries.mapIndexed { i, e -> SavedMealItemEntity(Ids.new(), id, e.toPortion(), i) })
            changes.upserted(EntityType.SAVED_MEAL, id)
        }
        return id
    }

    suspend fun logSavedMeal(savedMealId: String, date: LocalDate, mealId: String): Int = db.withTransaction {
        val items = dao.savedMealItems(savedMealId)
        val now = time.now()
        val entries = items.map { s ->
            val p = s.item
            DiaryEntry(Ids.new(), date, mealId, p.type, p.foodId, p.recipeId, p.name, p.brand, p.quantity, p.unit, p.unitLabel, p.amountPerUnit,
                p.basis, p.referenceAmount, p.nutrients.toDomain(), p.dataQuality, loggedAt = now, createdAt = now, updatedAt = now)
        }
        dao.insertAll(entries.map { it.toEntity() })
        entries.forEach { changes.upserted(EntityType.DIARY_ENTRY, it.id) }
        entries.size
    }

    suspend fun deleteSavedMeal(id: String) = db.withTransaction { dao.deleteSavedMeal(id); changes.deleted(EntityType.SAVED_MEAL, id) }

    /** Units available when editing an entry: the food's current options, or the entry's own unit. */
    suspend fun optionsForEntry(e: DiaryEntry): List<ServingOption> = when (e.type) {
        EntryType.FOOD -> e.foodId?.let { foods.getFood(it) }?.let { ServingConverter.optionsFor(it) } ?: listOf(optionFor(e))
        EntryType.RECIPE -> runCatching { recipeLogBasis(e.recipeId!!).options }.getOrDefault(listOf(optionFor(e)))
        EntryType.QUICK_ADD -> listOf(optionFor(e))
    }.let { opts -> if (opts.any { it.unit == e.unit && it.amountPerUnit == e.amountPerUnit }) opts else listOf(optionFor(e)) + opts }
}
