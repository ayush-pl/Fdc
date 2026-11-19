package com.fdc.nutrition.data.repository

import androidx.room.withTransaction
import com.fdc.nutrition.data.db.AppDatabase
import com.fdc.nutrition.data.db.entity.MealPlanEntity
import com.fdc.nutrition.data.db.entity.MealPlanItemEntity
import com.fdc.nutrition.data.db.entity.NutrientColumns
import com.fdc.nutrition.data.db.entity.PlanRecurrenceEntity
import com.fdc.nutrition.data.db.entity.PlannedMealEntity
import com.fdc.nutrition.data.db.entity.PortionColumns
import com.fdc.nutrition.data.db.toDomain
import com.fdc.nutrition.data.sync.ChangeTracker
import com.fdc.nutrition.data.sync.EntityType
import com.fdc.nutrition.data.util.TimeProvider
import com.fdc.nutrition.domain.calc.Ids
import com.fdc.nutrition.domain.calc.ServingOption
import com.fdc.nutrition.domain.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject
import javax.inject.Singleton

data class MealPlanTemplate(val id: String, val name: String, val isFavourite: Boolean, val itemCount: Int)

/**
 * Meal planning. Planned items are stored separately from the diary and never count as consumed;
 * [logPlanned] is the only path from a plan to the diary and it is idempotent.
 */
@Singleton
class PlanRepository @Inject constructor(
    private val db: AppDatabase,
    private val foods: FoodRepository,
    private val diary: DiaryRepository,
    private val time: TimeProvider,
    private val changes: ChangeTracker,
) {
    private val dao = db.planDao()

    fun observeRange(from: LocalDate, to: LocalDate): Flow<List<PlannedMeal>> = dao.observeRange(from, to).map { l -> l.map { it.toDomain() } }
    fun observePlannedDates(from: LocalDate, to: LocalDate): Flow<Set<LocalDate>> = dao.observePlannedDates(from, to).map { it.toSet() }

    private suspend fun portionForFood(foodId: String, quantity: Double, option: ServingOption): PortionColumns {
        if (!(quantity > 0 && quantity.isFinite())) throw ValidationException("Quantity must be greater than zero.")
        val f = foods.getFood(foodId) ?: throw ValidationException("Food not found")
        return PortionColumns(EntryType.FOOD, f.id, null, f.name, f.brand, quantity, option.unit, option.label, option.amountPerUnit, f.basis, 100.0, NutrientColumns.from(f.nutrientsPer100), f.dataQuality)
    }

    private suspend fun portionForRecipe(recipeId: String, quantity: Double, option: ServingOption?): PortionColumns {
        if (!(quantity > 0 && quantity.isFinite())) throw ValidationException("Quantity must be greater than zero.")
        val b = diary.recipeLogBasis(recipeId)
        val opt = option ?: b.options.first()
        val name = db.recipeDao().get(recipeId)?.name ?: throw ValidationException("Recipe not found")
        return PortionColumns(EntryType.RECIPE, null, recipeId, name, null, quantity, opt.unit, opt.label, opt.amountPerUnit, b.basis, b.referenceAmount, NutrientColumns.from(b.nutrients), DataQuality.CALCULATED)
    }

    suspend fun planFood(date: LocalDate, mealId: String, foodId: String, quantity: Double, option: ServingOption): String =
        insert(date, mealId, portionForFood(foodId, quantity, option))

    suspend fun planRecipe(date: LocalDate, mealId: String, recipeId: String, quantity: Double, option: ServingOption? = null): String =
        insert(date, mealId, portionForRecipe(recipeId, quantity, option))

    private suspend fun insert(date: LocalDate, mealId: String, p: PortionColumns, recurrenceId: String? = null): String {
        val now = time.now()
        val e = PlannedMealEntity(Ids.new(), date, mealId, p, recurrenceId, null, false, null, now, now)
        db.withTransaction { dao.insertPlanned(listOf(e)); changes.upserted(EntityType.PLANNED_MEAL, e.id) }
        return e.id
    }

    suspend fun updatePlanned(id: String, quantity: Double? = null, mealId: String? = null, date: LocalDate? = null, option: ServingOption? = null) {
        quantity?.let { if (!(it > 0 && it.isFinite())) throw ValidationException("Quantity must be greater than zero.") }
        db.withTransaction {
            val e = dao.getPlanned(id) ?: throw ValidationException("Planned item not found")
            val item = e.item.copy(quantity = quantity ?: e.item.quantity, unit = option?.unit ?: e.item.unit, unitLabel = option?.label ?: e.item.unitLabel, amountPerUnit = option?.amountPerUnit ?: e.item.amountPerUnit)
            dao.updatePlanned(e.copy(item = item, mealId = mealId ?: e.mealId, date = date ?: e.date, updatedAt = time.now()))
            changes.upserted(EntityType.PLANNED_MEAL, id)
        }
    }

    /** Removes a planned item. Recurring occurrences are marked skipped so they aren't regenerated. */
    suspend fun removePlanned(id: String) = db.withTransaction {
        val e = dao.getPlanned(id) ?: return@withTransaction
        if (e.recurrenceId != null) dao.updatePlanned(e.copy(isSkipped = true, updatedAt = time.now())) else dao.deletePlanned(id)
        changes.upserted(EntityType.PLANNED_MEAL, id)
    }

    suspend fun duplicatePlanned(id: String, toDate: LocalDate? = null, toMeal: String? = null): String {
        val e = dao.getPlanned(id) ?: throw ValidationException("Planned item not found")
        return insert(toDate ?: e.date, toMeal ?: e.mealId, e.item)
    }

    /** Copies all planned items of a day to another day. */
    suspend fun copyDay(from: LocalDate, to: LocalDate): Int = db.withTransaction {
        val items = dao.forDate(from)
        items.forEach { insert(to, it.mealId, it.item) }
        items.size
    }

    /** Moves a whole day's plan (unlogged items) to another date. */
    suspend fun moveDay(from: LocalDate, to: LocalDate): Int = db.withTransaction {
        val items = dao.forDate(from).filter { it.loggedEntryId == null }
        items.forEach { updatePlanned(it.id, date = to) }
        items.size
    }

    /**
     * Explicitly logs a planned item as consumed. Uses a unique planned-source key so the same plan
     * item can never be logged twice. The nutrition snapshot is the planned one.
     */
    suspend fun logPlanned(id: String, asDate: LocalDate? = null): String = db.withTransaction {
        val e = dao.getPlanned(id) ?: throw ValidationException("Planned item not found")
        e.loggedEntryId?.let { existing -> if (db.diaryDao().get(existing) != null) return@withTransaction existing }
        val key = "plan:$id"
        db.diaryDao().byPlannedKey(key)?.let { dao.updatePlanned(e.copy(loggedEntryId = it.id)); return@withTransaction it.id }
        val now = time.now()
        val p = e.item
        val entry = DiaryEntry(Ids.new(), asDate ?: e.date, e.mealId, p.type, p.foodId, p.recipeId, p.name, p.brand, p.quantity, p.unit, p.unitLabel,
            p.amountPerUnit, p.basis, p.referenceAmount, p.nutrients.toDomain(), p.dataQuality, plannedSourceKey = key, loggedAt = now, createdAt = now, updatedAt = now)
        diary.restore(entry)
        dao.updatePlanned(e.copy(loggedEntryId = entry.id, updatedAt = now))
        changes.upserted(EntityType.PLANNED_MEAL, id)
        entry.id
    }

    suspend fun logDay(date: LocalDate): Int {
        val items = dao.forDate(date).filter { it.loggedEntryId == null }
        items.forEach { logPlanned(it.id) }
        return items.size
    }

    // ---- recurrence
    suspend fun addRecurringFood(mealId: String, foodId: String, quantity: Double, option: ServingOption, days: Set<DayOfWeek>, start: LocalDate, end: LocalDate?) =
        addRecurrence(mealId, portionForFood(foodId, quantity, option), days, start, end)

    suspend fun addRecurringRecipe(mealId: String, recipeId: String, quantity: Double, days: Set<DayOfWeek>, start: LocalDate, end: LocalDate?) =
        addRecurrence(mealId, portionForRecipe(recipeId, quantity, null), days, start, end)

    private suspend fun addRecurrence(mealId: String, p: PortionColumns, days: Set<DayOfWeek>, start: LocalDate, end: LocalDate?): String {
        if (days.isEmpty()) throw ValidationException("Choose at least one day of the week.")
        if (end != null && end.isBefore(start)) throw ValidationException("End date must be after start date.")
        val r = PlanRecurrenceEntity(Ids.new(), mealId, p, maskOf(days), start, end, true, time.now())
        db.withTransaction { dao.upsertRecurrence(r); changes.upserted(EntityType.RECURRENCE, r.id) }
        return r.id
    }

    fun observeRecurrences() = dao.observeRecurrences()

    suspend fun stopRecurrence(id: String) = db.withTransaction {
        dao.deactivateRecurrence(id); dao.deleteFutureOccurrences(id, time.today()); changes.upserted(EntityType.RECURRENCE, id)
    }

    /** Materialises recurring occurrences for a date window (idempotent thanks to a unique index). */
    suspend fun materialize(from: LocalDate, to: LocalDate) {
        val recs = dao.activeRecurrences()
        if (recs.isEmpty()) return
        val now = time.now()
        val rows = mutableListOf<PlannedMealEntity>()
        var d = from
        while (!d.isAfter(to)) {
            recs.forEach { r ->
                if (!d.isBefore(r.startDate) && (r.endDate == null || !d.isAfter(r.endDate)) && (r.daysOfWeekMask shr (d.dayOfWeek.value - 1)) and 1 == 1)
                    rows += PlannedMealEntity("rec-${r.id}-$d", d, r.mealId, r.item, r.id, null, false, null, now, now)
            }
            d = d.plusDays(1)
        }
        dao.insertPlannedIgnore(rows)
    }

    // ---- reusable weekly plans
    fun observeTemplates(): Flow<List<MealPlanEntity>> = dao.observeMealPlans()

    /** Saves the week starting [weekStart] as a reusable plan template. */
    suspend fun saveWeekAsTemplate(weekStart: LocalDate, name: String, favourite: Boolean): String {
        if (name.isBlank()) throw ValidationException("Plan name is required.")
        val items = dao.range(weekStart, weekStart.plusDays(6))
        if (items.isEmpty()) throw ValidationException("This week has nothing planned yet.")
        val id = Ids.new()
        val now = time.now()
        db.withTransaction {
            dao.saveMealPlan(MealPlanEntity(id, name.trim(), favourite, null, now, now), items.map {
                MealPlanItemEntity(Ids.new(), id, ChronoUnit.DAYS.between(weekStart, it.date).toInt(), it.mealId, it.item)
            })
            changes.upserted(EntityType.MEAL_PLAN, id)
        }
        return id
    }

    suspend fun applyTemplate(planId: String, weekStart: LocalDate): Int = db.withTransaction {
        val items = dao.mealPlanItems(planId)
        items.forEach { insert(weekStart.plusDays(it.dayOffset.toLong()), it.mealId, it.item) }
        items.size
    }

    suspend fun setTemplateFavourite(planId: String, fav: Boolean) = db.withTransaction {
        dao.getMealPlan(planId)?.let { dao.upsertMealPlan(it.copy(isFavourite = fav, updatedAt = time.now())); changes.upserted(EntityType.MEAL_PLAN, planId) }
    }

    suspend fun deleteTemplate(planId: String) = db.withTransaction { dao.deleteMealPlan(planId); changes.deleted(EntityType.MEAL_PLAN, planId) }

    companion object {
        fun maskOf(days: Set<DayOfWeek>) = days.fold(0) { acc, d -> acc or (1 shl (d.value - 1)) }
        fun daysOf(mask: Int) = DayOfWeek.entries.filter { (mask shr (it.value - 1)) and 1 == 1 }.toSet()
        fun weekStart(d: LocalDate): LocalDate = d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    }
}
