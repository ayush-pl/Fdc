package com.fdc.nutrition.ui.nutrition

import androidx.lifecycle.viewModelScope
import com.fdc.nutrition.data.repository.*
import com.fdc.nutrition.domain.calc.AnalyticsReport
import com.fdc.nutrition.domain.calc.NutrientAggregator
import com.fdc.nutrition.domain.calc.NutrientTotals
import com.fdc.nutrition.domain.calc.WeightProgress
import com.fdc.nutrition.domain.calc.effectiveOn
import com.fdc.nutrition.domain.model.Meal
import com.fdc.nutrition.domain.model.NutritionTargets
import com.fdc.nutrition.domain.model.UserPreferences
import com.fdc.nutrition.ui.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import java.time.LocalDate
import javax.inject.Inject

data class DayNutrition(val date: LocalDate, val totals: NutrientTotals, val target: NutritionTargets?)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class NutritionViewModel @Inject constructor(
    private val analytics: AnalyticsRepository,
    diary: DiaryRepository,
    profiles: ProfileRepository,
) : BaseViewModel() {
    val range = MutableStateFlow(AnalyticsRange.WEEK)
    val custom = MutableStateFlow(LocalDate.now().minusDays(13) to LocalDate.now())
    val day = MutableStateFlow(LocalDate.now())

    val report: StateFlow<AnalyticsReport?> = combine(range, custom) { r, c -> r to c }
        .flatMapLatest { (r, c) -> flow { emit(analytics.resolve(r, c.first, c.second)) }.flatMapLatest { (from, to) -> analytics.observeReport(from, to) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val weight: StateFlow<WeightProgress?> = analytics.observeWeightProgress().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val prefs: StateFlow<UserPreferences> = profiles.observePreferences().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserPreferences())
    val meals: StateFlow<List<Meal>> = diary.observeMeals().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val dayNutrition: StateFlow<DayNutrition?> = day.flatMapLatest { d ->
        combine(diary.observeEntries(d), profiles.observeTargets()) { e, t -> DayNutrition(d, NutrientAggregator.sumEntries(e), t.effectiveOn(d)) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
}
