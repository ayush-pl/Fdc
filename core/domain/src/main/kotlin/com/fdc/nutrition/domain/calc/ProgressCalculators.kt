package com.fdc.nutrition.domain.calc

import com.fdc.nutrition.domain.model.BodyMeasurement
import com.fdc.nutrition.domain.model.DayLogStatus
import com.fdc.nutrition.domain.model.DiaryEntry
import com.fdc.nutrition.domain.model.MeasurementType
import com.fdc.nutrition.domain.model.Nutrient
import com.fdc.nutrition.domain.model.NutritionTargets
import com.fdc.nutrition.domain.model.TargetKind
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import kotlin.math.abs

data class WeightPoint(val date: LocalDate, val kg: Double)
data class WeeklyAverage(val weekStart: LocalDate, val average: Double, val count: Int)

data class WeightProgress(
    val startKg: Double?,
    val currentKg: Double?,
    val targetKg: Double?,
    val changeKg: Double?,
    /** 0..100 progress from start toward target; null when not computable. */
    val progressPercent: Double?,
    val weeklyAverages: List<WeeklyAverage>,
    /** Difference between the latest two weekly averages; smoother than day-to-day changes. */
    val weeklyTrendKg: Double?,
    val points: List<WeightPoint>,
)

object WeightProgressCalculator {
    fun calculate(records: List<BodyMeasurement>, profileStartKg: Double?, targetKg: Double?): WeightProgress {
        val points = records.filter { it.type == MeasurementType.WEIGHT }
            .sortedWith(compareBy({ it.date }, { it.createdAt }))
            .map { WeightPoint(it.date, it.value) }
        val start = profileStartKg ?: points.firstOrNull()?.kg
        val current = points.lastOrNull()?.kg
        val change = if (start != null && current != null) current - start else null
        val progress = if (start != null && current != null && targetKg != null && abs(targetKg - start) > 1e-9) {
            ((start - current) / (start - targetKg) * 100.0).coerceIn(0.0, 100.0)
        } else null
        val weekly = weeklyAverages(points)
        val trend = if (weekly.size >= 2) weekly.last().average - weekly[weekly.size - 2].average else null
        return WeightProgress(start, current, targetKg, change, progress, weekly, trend, points)
    }

    fun weeklyAverages(points: List<WeightPoint>): List<WeeklyAverage> =
        points.groupBy { it.date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) }
            .toSortedMap()
            .map { (week, pts) -> WeeklyAverage(week, pts.map { it.kg }.average(), pts.size) }
}

object DayStatusCalculator {
    /**
     * COMPLETE when the user marked the day complete or at least three distinct meals were logged;
     * PARTIAL when there is at least one entry; otherwise EMPTY.
     */
    fun status(entries: List<DiaryEntry>, markedComplete: Boolean): DayLogStatus = when {
        markedComplete -> DayLogStatus.COMPLETE
        entries.map { it.mealId }.distinct().size >= 3 -> DayLogStatus.COMPLETE
        entries.isNotEmpty() -> DayLogStatus.PARTIAL
        else -> DayLogStatus.EMPTY
    }
}

/** Picks the target version that applied on [date] (latest effectiveFrom ≤ date, else the earliest). */
fun List<NutritionTargets>.effectiveOn(date: LocalDate): NutritionTargets? =
    filter { !it.effectiveFrom.isAfter(date) }.maxByOrNull { it.effectiveFrom } ?: minByOrNull { it.effectiveFrom }

data class DaySummary(val date: LocalDate, val totals: NutrientTotals, val entryCount: Int, val waterMl: Double, val target: NutritionTargets?) {
    val isLogged: Boolean get() = entryCount > 0
}

data class FoodFrequency(val name: String, val count: Int, val totalKcal: Double)
data class MealCombo(val mealId: String, val foods: List<String>, val count: Int)

data class AnalyticsReport(
    val from: LocalDate,
    val to: LocalDate,
    val totalDays: Int,
    val loggedDays: Int,
    /** Averages over logged days only (unlogged days are not counted as zero intake). */
    val averages: Map<Nutrient, Double?>,
    val averageCoverage: Map<Nutrient, Double>,
    val averageWaterMl: Double?,
    val averageCalorieDelta: Double?,
    /** Share of logged days whose calories were within ±10% of that day's target. */
    val calorieConsistency: Double?,
    /** Share of logged days that met each nutrient target (GOAL: ≥100%, LIMIT: ≤100%, BALANCE: within ±10%). */
    val targetAchievement: Map<Nutrient, Double>,
    val averageEntriesPerDay: Double?,
    val mealFrequency: Map<String, Int>,
    val topFoods: List<FoodFrequency>,
    val topMeals: List<MealCombo>,
    val weeklyCalories: List<WeeklyAverage>,
    val days: List<DaySummary>,
)

object AnalyticsCalculator {
    const val CONSISTENCY_TOLERANCE = 0.10

    fun daySummaries(from: LocalDate, to: LocalDate, entries: List<DiaryEntry>, waterByDate: Map<LocalDate, Double>, targets: List<NutritionTargets>): List<DaySummary> {
        val byDate = entries.groupBy { it.date }
        val days = ChronoUnit.DAYS.between(from, to).toInt()
        return (0..days).map { i ->
            val d = from.plusDays(i.toLong())
            val list = byDate[d].orEmpty()
            DaySummary(d, NutrientAggregator.sumEntries(list), list.size, waterByDate[d] ?: 0.0, targets.effectiveOn(d))
        }
    }

    fun achieved(kind: TargetKind, value: Double, target: Double): Boolean = when (kind) {
        TargetKind.GOAL -> value >= target
        TargetKind.LIMIT -> value <= target
        TargetKind.BALANCE -> abs(value - target) <= target * CONSISTENCY_TOLERANCE
    }

    fun report(from: LocalDate, to: LocalDate, entries: List<DiaryEntry>, waterByDate: Map<LocalDate, Double>, targets: List<NutritionTargets>): AnalyticsReport {
        require(!to.isBefore(from)) { "End date must not be before start date" }
        val inRange = entries.filter { !it.date.isBefore(from) && !it.date.isAfter(to) }
        val days = daySummaries(from, to, inRange, waterByDate, targets)
        val logged = days.filter { it.isLogged }
        val averages = Nutrient.entries.associateWith { n ->
            val vals = logged.mapNotNull { it.totals[n].valueOrNull }
            if (vals.isEmpty()) null else vals.average()
        }
        val coverage = Nutrient.entries.associateWith { n ->
            if (logged.isEmpty()) 0.0 else logged.map { it.totals[n].coverage }.average()
        }
        val waterDays = days.filter { it.waterMl > 0 }
        val deltas = logged.mapNotNull { d -> d.target?.calories?.let { d.totals.calories - it } }
        val consistency = logged.mapNotNull { d -> d.target?.calories?.let { abs(d.totals.calories - it) <= it * CONSISTENCY_TOLERANCE } }
        val achievement = Nutrient.entries.mapNotNull { n ->
            val results = logged.mapNotNull { d ->
                val t = d.target?.get(n) ?: return@mapNotNull null
                val v = d.totals[n].valueOrNull ?: return@mapNotNull null
                achieved(n.targetKind, v, t)
            }
            if (results.isEmpty()) null else n to results.count { it }.toDouble() / results.size
        }.toMap()
        val foodFreq = inRange.groupBy { it.name }.map { (name, list) ->
            FoodFrequency(name, list.size, list.sumOf { it.nutrients.energyKcal ?: 0.0 })
        }.sortedWith(compareByDescending<FoodFrequency> { it.count }.thenByDescending { it.totalKcal }).take(10)
        val combos = inRange.groupBy { it.date to it.mealId }.values
            .filter { it.size >= 2 }
            .map { list -> list.first().mealId to list.map { it.name }.distinct().sorted() }
            .groupingBy { it }.eachCount()
            .filter { it.value >= 2 }
            .map { (k, c) -> MealCombo(k.first, k.second, c) }
            .sortedByDescending { it.count }.take(5)
        val mealFrequency = inRange.groupBy { it.date to it.mealId }.keys.groupingBy { it.second }.eachCount()
        val weekly = logged.groupBy { it.date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) }
            .toSortedMap().map { (w, list) -> WeeklyAverage(w, list.map { it.totals.calories }.average(), list.size) }
        return AnalyticsReport(
            from = from, to = to, totalDays = days.size, loggedDays = logged.size,
            averages = averages, averageCoverage = coverage,
            averageWaterMl = if (waterDays.isEmpty()) null else waterDays.map { it.waterMl }.average(),
            averageCalorieDelta = if (deltas.isEmpty()) null else deltas.average(),
            calorieConsistency = if (consistency.isEmpty()) null else consistency.count { it }.toDouble() / consistency.size,
            targetAchievement = achievement,
            averageEntriesPerDay = if (logged.isEmpty()) null else logged.map { it.entryCount }.average(),
            mealFrequency = mealFrequency, topFoods = foodFreq, topMeals = combos, weeklyCalories = weekly, days = days,
        )
    }
}
