package com.fdc.nutrition.ui.home

import androidx.lifecycle.viewModelScope
import com.fdc.nutrition.data.db.dao.DayEntryStats
import com.fdc.nutrition.data.repository.*
import com.fdc.nutrition.domain.calc.NutrientAggregator
import com.fdc.nutrition.domain.calc.NutrientTotals
import com.fdc.nutrition.domain.calc.WeightProgress
import com.fdc.nutrition.domain.calc.effectiveOn
import com.fdc.nutrition.domain.model.*
import com.fdc.nutrition.ui.BaseViewModel
import com.fdc.nutrition.ui.suggestedMeal
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import java.time.LocalDate
import javax.inject.Inject

data class MealSummary(val meal: Meal, val entries: List<DiaryEntry>, val totals: NutrientTotals)

data class HomeState(
    val loading: Boolean = true,
    val date: LocalDate = LocalDate.now(),
    val profile: UserProfile? = null,
    val prefs: UserPreferences = UserPreferences(),
    val target: NutritionTargets? = null,
    val totals: NutrientTotals = NutrientTotals.EMPTY,
    val meals: List<MealSummary> = emptyList(),
    val waterMl: Double = 0.0,
    val recent: List<RecentItem> = emptyList(),
    val week: List<Pair<LocalDate, Double?>> = emptyList(),
    val weekTargets: List<Double?> = emptyList(),
    val weight: WeightProgress? = null,
    val suggestedMeal: String = Meal.LUNCH,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val diary: DiaryRepository,
    private val water: WaterRepository,
    profiles: ProfileRepository,
    analytics: AnalyticsRepository,
) : BaseViewModel() {
    private val today = MutableStateFlow(LocalDate.now())

    private val dayData = today.flatMapLatest { d ->
        combine(diary.observeEntries(d), diary.observeMeals(), water.observeForDate(d), diary.observeDayStats(d.minusDays(6), d)) { e, m, w, s -> DayBundle(d, e, m, w, s) }
    }

    val state: StateFlow<HomeState> = combine(
        dayData, profiles.observeProfile(), profiles.observeTargets(), profiles.observePreferences(),
        combine(diary.observeRecent(8), analytics.observeWeightProgress()) { r, w -> r to w },
    ) { day, profile, targets, prefs, (recent, weight) ->
        val stats = day.stats.associateBy { it.date }
        val week = (6 downTo 0).map { day.date.minusDays(it.toLong()) }
        HomeState(
            loading = false, date = day.date, profile = profile, prefs = prefs, target = targets.effectiveOn(day.date),
            totals = NutrientAggregator.sumEntries(day.entries),
            meals = day.meals.map { m -> day.entries.filter { it.mealId == m.id }.let { MealSummary(m, it, NutrientAggregator.sumEntries(it)) } },
            waterMl = WaterRepository.hydrationTotal(day.water, prefs.countBeveragesAsWater),
            recent = recent, week = week.map { it to stats[it]?.kcal }, weekTargets = week.map { targets.effectiveOn(it)?.calories },
            weight = weight, suggestedMeal = suggestedMeal(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeState())

    fun refreshDate() { today.value = LocalDate.now() }

    fun addWater(ml: Double) = launchSafe { water.add(state.value.date, ml); message("Added ${ml.toInt()} ml of water") }

    fun relog(item: RecentItem, mealId: String) = launchSafe {
        diary.relog(item.entry, state.value.date, mealId)
        message("Added ${item.entry.name}")
    }

    private data class DayBundle(val date: LocalDate, val entries: List<DiaryEntry>, val meals: List<Meal>, val water: List<WaterEntry>, val stats: List<DayEntryStats>)
}
