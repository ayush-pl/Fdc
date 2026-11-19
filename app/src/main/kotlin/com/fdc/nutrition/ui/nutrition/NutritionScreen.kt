package com.fdc.nutrition.ui.nutrition

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.fdc.nutrition.data.repository.AnalyticsRange
import com.fdc.nutrition.domain.calc.AnalyticsReport
import com.fdc.nutrition.domain.calc.DaySummary
import com.fdc.nutrition.domain.model.*
import com.fdc.nutrition.ui.components.*
import com.fdc.nutrition.ui.navigation.*
import com.fdc.nutrition.ui.theme.LocalMacroPalette
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NutritionScreen(nav: NavHostController, vm: NutritionViewModel = hiltViewModel()) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Scaffold(topBar = { TopAppBar(title = { Text("Nutrition") }, colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)) }, contentWindowInsets = WindowInsets.statusBars) { pad ->
        Column(Modifier.padding(pad)) {
            SecondaryTabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.background) {
                Tab(tab == 0, { tab = 0 }, text = { Text("Day") }, modifier = Modifier.testTag("nutrition_day_tab"))
                Tab(tab == 1, { tab = 1 }, text = { Text("Trends") }, modifier = Modifier.testTag("nutrition_trends_tab"))
            }
            if (tab == 0) DayTab(vm) else TrendsTab(vm, nav)
        }
    }
}

@Composable
private fun DayTab(vm: NutritionViewModel) {
    val d by vm.dayNutrition.collectAsStateWithLifecycle()
    val date by vm.day.collectAsStateWithLifecycle()
    val palette = LocalMacroPalette.current
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { DateNavigator(date, { vm.day.value = it }) }
        val dn = d ?: return@LazyColumn
        item {
            if (dn.totals.itemCount == 0) Banner("Nothing logged on this day.", BannerKind.INFO)
            else Text("Totals from ${dn.totals.itemCount} entries. Where some entries lack a nutrient, the total is partial and labelled.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val groups = listOf(
            "Energy & macros" to listOf(Nutrient.ENERGY, Nutrient.PROTEIN, Nutrient.CARBOHYDRATE, Nutrient.FAT),
            "Fats, sugar & fibre" to listOf(Nutrient.SATURATED_FAT, Nutrient.TRANS_FAT, Nutrient.FIBRE, Nutrient.SUGARS, Nutrient.ADDED_SUGARS, Nutrient.CHOLESTEROL),
            "Minerals" to Nutrient.entries.filter { it.group == NutrientGroup.MINERAL },
            "Vitamins" to Nutrient.entries.filter { it.group == NutrientGroup.VITAMIN },
        )
        groups.forEach { (title, list) ->
            item(key = title) {
                AppCard {
                    Text(title, style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        list.forEach { n ->
                            val c = when (n) { Nutrient.ENERGY -> palette.calories; Nutrient.PROTEIN -> palette.protein; Nutrient.CARBOHYDRATE -> palette.carbs; Nutrient.FAT -> palette.fat; Nutrient.FIBRE -> palette.fibre; else -> MaterialTheme.colorScheme.primary }
                            NutrientProgressRow(n, if (dn.totals.itemCount == 0) null else dn.totals[n], dn.target?.get(n), c, compact = true)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TrendsTab(vm: NutritionViewModel, nav: NavHostController) {
    val report by vm.report.collectAsStateWithLifecycle()
    val range by vm.range.collectAsStateWithLifecycle()
    val custom by vm.custom.collectAsStateWithLifecycle()
    val weight by vm.weight.collectAsStateWithLifecycle()
    val prefs by vm.prefs.collectAsStateWithLifecycle()
    val meals by vm.meals.collectAsStateWithLifecycle()
    val palette = LocalMacroPalette.current
    var pickFrom by remember { mutableStateOf(false) }
    var pickTo by remember { mutableStateOf(false) }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(AnalyticsRange.entries) { r -> FilterChip(range == r, { vm.range.value = r }, { Text(r.label) }, modifier = Modifier.testTag("range_${r.name}")) }
            }
            if (range == AnalyticsRange.CUSTOM) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton({ pickFrom = true }) { Text("From ${Fmt.short(custom.first)}") }
                OutlinedButton({ pickTo = true }) { Text("To ${Fmt.short(custom.second)}") }
            }
        }
        val r = report ?: run { item { LoadingBox() }; return@LazyColumn }
        item {
            AppCard {
                Text("${Fmt.short(r.from)} – ${Fmt.short(r.to)}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${r.loggedDays} of ${r.totalDays} days logged", style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("trend_logged_days"))
                Text("Averages use logged days only — unlogged days are not counted as zero.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Avg("Calories", r.averages[Nutrient.ENERGY], Nutrient.ENERGY, "trend_avg_kcal")
                    Avg("Protein", r.averages[Nutrient.PROTEIN], Nutrient.PROTEIN, "trend_avg_protein")
                    Avg("Carbs", r.averages[Nutrient.CARBOHYDRATE], Nutrient.CARBOHYDRATE, "trend_avg_carbs")
                    Avg("Fat", r.averages[Nutrient.FAT], Nutrient.FAT, "trend_avg_fat")
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Avg("Fibre", r.averages[Nutrient.FIBRE], Nutrient.FIBRE, "trend_avg_fibre")
                    Column { Text(r.averageWaterMl?.let { Fmt.litres(it) } ?: "—", style = MaterialTheme.typography.titleMedium); Text("Water/day", style = MaterialTheme.typography.labelSmall) }
                    Column { Text(r.averageCalorieDelta?.let { (if (it > 0) "+" else "") + Fmt.kcal(it) } ?: "—", style = MaterialTheme.typography.titleMedium); Text(if ((r.averageCalorieDelta ?: 0.0) > 0) "Avg surplus" else "Avg deficit", style = MaterialTheme.typography.labelSmall) }
                    Column { Text(r.calorieConsistency?.let { "${(it * 100).toInt()}%" } ?: "—", style = MaterialTheme.typography.titleMedium); Text("Within ±10%", style = MaterialTheme.typography.labelSmall) }
                }
            }
        }
        if (r.loggedDays == 0) item { EmptyState(Icons.Outlined.Insights, "No data in this period", "Log some meals to see your trends.") }
        val dense = r.days.size > 31
        val labelFmt = DateTimeFormatter.ofPattern("d/M")
        fun points(sel: (DaySummary) -> Double?) = r.days.map { ChartPoint(it.date.format(labelFmt), if (it.isLogged) sel(it) else null) }
        val target = r.days.lastOrNull()?.target
        item {
            AppCard {
                val pts = points { it.totals.calories }
                if (dense) LineChart(pts, palette.calories, title = "Daily calories", target = target?.calories, valueFormat = { Fmt.kcal(it) }, zeroBased = true)
                else BarChart(pts, palette.calories, title = "Daily calories", target = target?.calories, valueFormat = { Fmt.kcal(it) }, overColor = palette.over)
            }
        }
        item { AppCard { BarChart(r.weeklyCalories.map { ChartPoint(it.weekStart.format(labelFmt), it.average) }, palette.calories, title = "Weekly calorie average", target = target?.calories, valueFormat = { Fmt.kcal(it) }) } }
        listOf(Triple(Nutrient.PROTEIN, palette.protein, "Protein"), Triple(Nutrient.CARBOHYDRATE, palette.carbs, "Carbohydrates"), Triple(Nutrient.FAT, palette.fat, "Fat"), Triple(Nutrient.FIBRE, palette.fibre, "Fibre")).forEach { (n, c, t) ->
            item(key = "chart_$t") { AppCard { LineChart(points { it.totals[n].valueOrNull }, c, title = "$t (g)", target = target?.get(n), valueFormat = { Fmt.num(it, 0) + " g" }) } }
        }
        item { AppCard { BarChart(r.days.map { ChartPoint(it.date.format(labelFmt), it.waterMl.takeIf { w -> w > 0 }) }, palette.water, title = "Water", target = target?.waterMl, valueFormat = { Fmt.litres(it) }) } }
        item {
            AppCard(onClick = { nav.navigate(BodyRoute) }) {
                val pts = weight?.points?.filter { !it.date.isBefore(r.from) && !it.date.isAfter(r.to) }.orEmpty()
                LineChart(pts.map { ChartPoint(it.date.format(labelFmt), Fmt.weightValue(it.kg, prefs.weightUnit)) }, palette.weight, title = "Body weight (${prefs.weightUnit.symbol})",
                    target = weight?.targetKg?.let { Fmt.weightValue(it, prefs.weightUnit) }, valueFormat = { Fmt.num(it) })
                Text("Day-to-day weight varies with water and food volume — the weekly trend is more meaningful.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            AppCard {
                Text("Target completion", style = MaterialTheme.typography.titleSmall)
                Text("Share of logged days meeting each target (goals: reached; limits: not exceeded; calories & macros: within ±10%).", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                if (r.targetAchievement.isEmpty()) Text("No targets or data yet.")
                r.targetAchievement.entries.sortedBy { it.key.ordinal }.forEach { (n, frac) ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 3.dp)) {
                        Text(n.displayName, Modifier.width(120.dp), style = MaterialTheme.typography.bodySmall)
                        LinearBar(frac.toFloat(), MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                        Text(" ${(frac * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(40.dp))
                    }
                }
                val lowCoverage = r.averageCoverage.filter { it.value in 0.0..0.7 && r.loggedDays > 0 }.keys
                if (lowCoverage.isNotEmpty()) Text("Low data coverage: ${lowCoverage.take(6).joinToString { it.displayName }}. These totals may understate intake.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
            }
        }
        item {
            AppCard {
                Text("Eating habits", style = MaterialTheme.typography.titleSmall)
                Text("Average entries per logged day: ${r.averageEntriesPerDay?.let { Fmt.num(it) } ?: "—"}", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(4.dp))
                Text("Meal frequency (days with each meal):", style = MaterialTheme.typography.labelLarge)
                meals.forEach { m -> r.mealFrequency[m.id]?.let { Text("• ${m.name}: $it of ${r.loggedDays} days", style = MaterialTheme.typography.bodySmall) } }
                Spacer(Modifier.height(8.dp))
                Text("Most consumed foods", style = MaterialTheme.typography.labelLarge)
                if (r.topFoods.isEmpty()) Text("—")
                r.topFoods.forEachIndexed { i, f -> Text("${i + 1}. ${f.name} — ${f.count}× · ${Fmt.kcal(f.totalKcal)} kcal total", style = MaterialTheme.typography.bodySmall) }
                if (r.topMeals.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text("Most repeated meals", style = MaterialTheme.typography.labelLarge)
                    r.topMeals.forEach { c -> Text("• ${meals.firstOrNull { it.id == c.mealId }?.name ?: c.mealId}: ${c.foods.joinToString()} (${c.count}×)", style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
    }
    if (pickFrom) DatePickerDialogField(custom.first, { vm.custom.value = it to maxOf(it, custom.second); vm.range.value = AnalyticsRange.CUSTOM }, { pickFrom = false }, allowFuture = false)
    if (pickTo) DatePickerDialogField(custom.second, { vm.custom.value = minOf(custom.first, it) to it; vm.range.value = AnalyticsRange.CUSTOM }, { pickTo = false }, allowFuture = false)
}

@Composable
private fun Avg(label: String, v: Double?, n: Nutrient, tag: String) {
    Column {
        Text(Fmt.nutrient(v, n), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.testTag(tag))
        Text("$label/day", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
