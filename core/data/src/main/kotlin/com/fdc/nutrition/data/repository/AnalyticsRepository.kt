package com.fdc.nutrition.data.repository

import com.fdc.nutrition.data.util.TimeProvider
import com.fdc.nutrition.domain.calc.AnalyticsCalculator
import com.fdc.nutrition.domain.calc.AnalyticsReport
import com.fdc.nutrition.domain.calc.WeightProgress
import com.fdc.nutrition.domain.calc.WeightProgressCalculator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

enum class AnalyticsRange(val label: String, val days: Long?) {
    WEEK("7 days", 7), MONTH("30 days", 30), QUARTER("3 months", 91), HALF("6 months", 182), YEAR("12 months", 365), ALL("All time", null), CUSTOM("Custom", null);
}

/** Builds analytics strictly from stored records — nothing is hard-coded or estimated. */
@Singleton
class AnalyticsRepository @Inject constructor(
    private val diary: DiaryRepository,
    private val water: WaterRepository,
    private val body: BodyRepository,
    private val profile: ProfileRepository,
    private val time: TimeProvider,
    private val db: com.fdc.nutrition.data.db.AppDatabase,
) {
    suspend fun resolve(range: AnalyticsRange, customFrom: LocalDate? = null, customTo: LocalDate? = null): Pair<LocalDate, LocalDate> {
        val today = time.today()
        return when (range) {
            AnalyticsRange.CUSTOM -> (customFrom ?: today.minusDays(6)) to (customTo ?: today)
            AnalyticsRange.ALL -> (db.diaryDao().firstDate() ?: today) to today
            else -> today.minusDays(range.days!! - 1) to today
        }
    }

    fun observeReport(from: LocalDate, to: LocalDate): Flow<AnalyticsReport> =
        combine(diary.observeRange(from, to), water.observeRange(from, to), profile.observeTargets(), profile.observePreferences()) { entries, w, targets, prefs ->
            AnalyticsCalculator.report(from, to, entries, WaterRepository.dailyTotals(w, prefs.countBeveragesAsWater), targets)
        }

    fun observeWeightProgress(): Flow<WeightProgress> =
        combine(body.observeWeights(), profile.observeProfile()) { w, p -> WeightProgressCalculator.calculate(w, p?.startWeightKg, p?.targetWeightKg) }
}
