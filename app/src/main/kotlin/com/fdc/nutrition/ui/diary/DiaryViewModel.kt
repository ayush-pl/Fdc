package com.fdc.nutrition.ui.diary

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.fdc.nutrition.data.db.entity.DayLogEntity
import com.fdc.nutrition.data.repository.*
import com.fdc.nutrition.domain.calc.DayStatusCalculator
import com.fdc.nutrition.domain.calc.NutrientAggregator
import com.fdc.nutrition.domain.calc.NutrientTotals
import com.fdc.nutrition.domain.calc.effectiveOn
import com.fdc.nutrition.domain.model.*
import com.fdc.nutrition.ui.BaseViewModel
import com.fdc.nutrition.ui.home.MealSummary
import com.fdc.nutrition.ui.navigation.DiaryRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import java.time.LocalDate
import javax.inject.Inject

data class DiaryState(
    val date: LocalDate = LocalDate.now(),
    val loading: Boolean = true,
    val meals: List<MealSummary> = emptyList(),
    val allMeals: List<Meal> = emptyList(),
    val totals: NutrientTotals = NutrientTotals.EMPTY,
    val target: NutritionTargets? = null,
    val notes: Map<String, String> = emptyMap(),
    val waterMl: Double = 0.0,
    val planned: List<PlannedMeal> = emptyList(),
    val dayLog: DayLogEntity? = null,
    val status: DayLogStatus = DayLogStatus.EMPTY,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DiaryViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val diary: DiaryRepository,
    private val water: WaterRepository,
    private val plans: PlanRepository,
    profiles: ProfileRepository,
) : BaseViewModel() {
    private val route = runCatching { savedState.toRoute<DiaryRoute>() }.getOrNull()
    private val _date = MutableStateFlow(route?.date?.let(LocalDate::ofEpochDay) ?: LocalDate.now())
    val date: StateFlow<LocalDate> = _date

    val state: StateFlow<DiaryState> = _date.flatMapLatest { d ->
        combine(
            combine(diary.observeEntries(d), diary.observeMeals(), diary.observeMealNotes(d)) { e, m, n -> Triple(e, m, n) },
            water.observeForDate(d), profiles.observeTargets(), profiles.observePreferences(),
            combine(plans.observeRange(d, d), diary.observeDayLog(d)) { p, l -> p to l },
        ) { (entries, meals, notes), w, targets, prefs, (planned, log) ->
            DiaryState(
                date = d, loading = false, allMeals = meals,
                meals = meals.map { m -> entries.filter { it.mealId == m.id }.let { MealSummary(m, it, NutrientAggregator.sumEntries(it)) } } +
                    entries.filter { e -> meals.none { it.id == e.mealId } }.groupBy { it.mealId }.map { (id, l) -> MealSummary(Meal(id, "Other", 99, false), l, NutrientAggregator.sumEntries(l)) },
                totals = NutrientAggregator.sumEntries(entries), target = targets.effectiveOn(d), notes = notes,
                waterMl = WaterRepository.hydrationTotal(w, prefs.countBeveragesAsWater), planned = planned.filter { !it.isLogged },
                dayLog = log, status = DayStatusCalculator.status(entries, log?.markedComplete == true),
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DiaryState())

    fun setDate(d: LocalDate) { _date.value = d }

    fun delete(e: DiaryEntry) = launchSafe { diary.delete(e.id) }
    fun restore(e: DiaryEntry) = launchSafe { diary.restore(e) }
    fun duplicate(e: DiaryEntry) = launchSafe { diary.duplicate(e.id); message("Duplicated ${e.name}") }
    fun move(e: DiaryEntry, mealId: String) = launchSafe { diary.moveEntries(listOf(e.id), mealId); message("Moved to ${state.value.allMeals.firstOrNull { it.id == mealId }?.name}") }
    fun copyMealFrom(fromDate: LocalDate, fromMeal: String, toMeal: String) = launchSafe {
        val n = diary.copyMeal(fromDate, fromMeal, _date.value, toMeal)
        message(if (n == 0) "Nothing to copy from that meal" else "Copied $n items")
    }
    fun copyMealTo(mealId: String, toDate: LocalDate) = launchSafe { val n = diary.copyMeal(_date.value, mealId, toDate, mealId); message("Copied $n items") }
    fun saveMeal(mealId: String, name: String) = launchSafe { diary.saveMealAsFavourite(_date.value, mealId, name); message("Saved \"$name\" to your meals") }
    fun setNote(mealId: String, note: String) = launchSafe { diary.setMealNote(_date.value, mealId, note) }
    fun setComplete(c: Boolean) = launchSafe { diary.setDayComplete(_date.value, c) }
    fun addMeal(name: String) = launchSafe { diary.addCustomMeal(name); message("Added meal \"$name\"") }
    fun logPlanned(p: PlannedMeal) = launchSafe { plans.logPlanned(p.id); message("Logged ${p.name}") }
}
