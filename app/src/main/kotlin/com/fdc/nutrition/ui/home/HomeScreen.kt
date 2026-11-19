package com.fdc.nutrition.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.fdc.nutrition.domain.model.Nutrient
import com.fdc.nutrition.ui.components.*
import com.fdc.nutrition.ui.navigation.*
import com.fdc.nutrition.ui.theme.LocalMacroPalette
import java.time.LocalTime

@Composable
fun HomeScreen(nav: NavHostController, vm: HomeViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    CollectMessages(vm.messages)
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refreshDate() }
    val palette = LocalMacroPalette.current
    val dateArg = s.date.toArg()

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { nav.navigate(AddFoodRoute(dateArg, s.suggestedMeal)) },
                icon = { Icon(Icons.Filled.Add, null) }, text = { Text("Add food") },
                containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("home_add_food"),
            )
        },
        contentWindowInsets = WindowInsets.statusBars,
    ) { pad ->
        if (s.loading) { LoadingBox(Modifier.padding(pad)); return@Scaffold }
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item {
                val hour = LocalTime.now().hour
                val greet = when { hour < 12 -> "Good morning"; hour < 17 -> "Good afternoon"; else -> "Good evening" }
                Column {
                    Text(Fmt.long(s.date), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("$greet${s.profile?.displayName?.let { ", $it" } ?: ""}", style = MaterialTheme.typography.headlineMedium)
                }
            }
            if (s.target == null) item {
                Banner("Set your daily targets to see what's remaining.", BannerKind.INFO) { TextButton(onClick = { nav.navigate(TargetsRoute) }) { Text("Set up") } }
            }
            item {
                AppCard(onClick = { nav.navigate(DiaryRoute(dateArg)) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CalorieRing(s.totals.calories, s.target?.calories, Modifier.testTag("calorie_ring"), size = 150.dp)
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            StatLine("Target", s.target?.calories?.let { "${Fmt.kcal(it)} kcal" } ?: "Not set")
                            StatLine("Eaten", "${Fmt.kcal(s.totals.calories)} kcal", Modifier.testTag("home_consumed"))
                            StatLine("Remaining", s.target?.calories?.let { "${Fmt.kcal(it - s.totals.calories)} kcal" } ?: "—")
                            if (s.totals[Nutrient.ENERGY].missingCount > 0) Text("Some entries have no calorie data", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        NutrientProgressRow(Nutrient.PROTEIN, s.totals[Nutrient.PROTEIN], s.target?.get(Nutrient.PROTEIN), palette.protein, compact = true)
                        NutrientProgressRow(Nutrient.CARBOHYDRATE, s.totals[Nutrient.CARBOHYDRATE], s.target?.get(Nutrient.CARBOHYDRATE), palette.carbs, compact = true)
                        NutrientProgressRow(Nutrient.FAT, s.totals[Nutrient.FAT], s.target?.get(Nutrient.FAT), palette.fat, compact = true)
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    QuickAction("Scan", Icons.Outlined.QrCodeScanner, Modifier.weight(1f)) { nav.navigate(ScannerRoute(dateArg, s.suggestedMeal)) }
                    QuickAction("Quick add", Icons.Outlined.Bolt, Modifier.weight(1f)) { nav.navigate(QuickAddRoute(dateArg, s.suggestedMeal)) }
                    QuickAction("Recipes", Icons.Outlined.MenuBook, Modifier.weight(1f)) { nav.navigate(RecipesRoute) }
                    QuickAction("Plan", Icons.Outlined.EventNote, Modifier.weight(1f)) { nav.navigate(PlannerRoute()) }
                }
            }
            item {
                val goal = s.target?.waterMl?.takeIf { it > 0 }
                AppCard(onClick = { nav.navigate(WaterRoute(dateArg)) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.WaterDrop, null, tint = palette.water)
                        Spacer(Modifier.width(8.dp))
                        Text("Water", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        Text("${Fmt.litres(s.waterMl)}${goal?.let { " / ${Fmt.litres(it)}" } ?: ""}", style = MaterialTheme.typography.titleSmall, modifier = Modifier.testTag("home_water"))
                    }
                    Spacer(Modifier.height(8.dp))
                    LinearBar(goal?.let { (s.waterMl / it).toFloat() } ?: 0f, palette.water)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        s.prefs.waterQuickAmountsMl.take(4).forEach { ml ->
                            AssistChip(onClick = { vm.addWater(ml.toDouble()) }, label = { Text("+$ml ml") }, modifier = Modifier.testTag("water_$ml"))
                        }
                    }
                }
            }
            item { SectionHeader("Today's meals", action = { TextButton(onClick = { nav.navigate(DiaryRoute(dateArg)) }) { Text("Open diary") } }) }
            items(s.meals, key = { it.meal.id }) { m ->
                AppCard(onClick = { nav.navigate(DiaryRoute(dateArg)) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(m.meal.name, style = MaterialTheme.typography.titleSmall)
                            Text(if (m.entries.isEmpty()) "Nothing logged" else m.entries.joinToString { it.name }.take(70),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        }
                        Text("${Fmt.kcal(m.totals.calories)} kcal", style = MaterialTheme.typography.labelLarge)
                        IconButton(onClick = { nav.navigate(AddFoodRoute(dateArg, m.meal.id)) }, modifier = Modifier.testTag("add_to_${m.meal.id}")) { Icon(Icons.Filled.Add, "Add to ${m.meal.name}") }
                    }
                }
            }
            if (s.recent.isNotEmpty()) {
                item { SectionHeader("Log again") }
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(s.recent, key = { it.entry.id }) { r ->
                            ElevatedCard(Modifier.width(170.dp)) {
                                Column(Modifier.padding(12.dp)) {
                                    Text(r.entry.name, style = MaterialTheme.typography.labelLarge, maxLines = 2, minLines = 2)
                                    Text("${Fmt.qty(r.entry.quantity)} ${r.entry.unitLabel} · ${Fmt.kcal(r.entry.nutrients.energyKcal)} kcal", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                                    Spacer(Modifier.height(6.dp))
                                    FilledTonalButton(onClick = { vm.relog(r, s.suggestedMeal) }, contentPadding = PaddingValues(horizontal = 12.dp), modifier = Modifier.height(34.dp)) { Icon(Icons.Filled.Add, null, Modifier.size(16.dp)); Text("Add") }
                                }
                            }
                        }
                    }
                }
            }
            item {
                AppCard(onClick = { nav.navigate(NutritionRoute) }) {
                    BarChart(s.week.map { ChartPoint(it.first.dayOfWeek.name.take(3).lowercase().replaceFirstChar { c -> c.uppercase() }, it.second) }, palette.calories,
                        title = "This week's calories", target = s.weekTargets.lastOrNull(), overColor = palette.over, height = 120.dp, valueFormat = { Fmt.kcal(it) })
                    val logged = s.week.mapNotNull { it.second }
                    val consistent = s.week.indices.count { i -> val v = s.week[i].second; val t = s.weekTargets[i]; v != null && t != null && kotlin.math.abs(v - t) <= t * 0.1 }
                    Text("${logged.size}/7 days logged · $consistent within ±10% of target", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item {
                val w = s.weight
                AppCard(onClick = { nav.navigate(BodyRoute) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.MonitorWeight, null, tint = palette.weight)
                        Spacer(Modifier.width(8.dp))
                        Text("Weight", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        Text(Fmt.weight(w?.currentKg, s.prefs.weightUnit), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    }
                    if (w?.targetKg != null && w.progressPercent != null) {
                        Spacer(Modifier.height(8.dp))
                        LinearBar((w.progressPercent!! / 100).toFloat(), palette.weight)
                        Text("${Fmt.num(w.progressPercent!!, 0)}% of the way to ${Fmt.weight(w.targetKg, s.prefs.weightUnit)} · change ${w.changeKg?.let { (if (it > 0) "+" else "") + Fmt.weight(it, s.prefs.weightUnit) } ?: "—"}",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else Text(if (w?.currentKg == null) "Add your first weigh-in" else "Set a target weight in your profile", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun StatLine(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun QuickAction(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface, modifier = modifier,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(4.dp))
            Text(label, style = MaterialTheme.typography.labelMedium)
        }
    }
}
