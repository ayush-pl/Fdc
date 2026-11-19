package com.fdc.nutrition.ui.plan

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import androidx.navigation.toRoute
import com.fdc.nutrition.data.db.entity.MealPlanEntity
import com.fdc.nutrition.data.repository.*
import com.fdc.nutrition.domain.calc.NutrientAggregator
import com.fdc.nutrition.domain.calc.effectiveOn
import com.fdc.nutrition.domain.model.*
import com.fdc.nutrition.ui.BaseViewModel
import com.fdc.nutrition.ui.components.*
import com.fdc.nutrition.ui.diary.TextInputDialog
import com.fdc.nutrition.ui.navigation.*
import com.fdc.nutrition.ui.suggestedMeal
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import javax.inject.Inject

data class PlannerState(
    val weekStart: LocalDate = PlanRepository.weekStart(LocalDate.now()),
    val items: Map<LocalDate, List<PlannedMeal>> = emptyMap(),
    val meals: List<Meal> = emptyList(),
    val targets: List<NutritionTargets> = emptyList(),
    val templates: List<MealPlanEntity> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PlannerViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val plans: PlanRepository,
    val foods: FoodRepository,
    val recipes: RecipeRepository,
    diary: DiaryRepository,
    profiles: ProfileRepository,
) : BaseViewModel() {
    private val route = runCatching { savedState.toRoute<PlannerRoute>() }.getOrNull()
    val week = MutableStateFlow(PlanRepository.weekStart(route?.weekStart?.let(LocalDate::ofEpochDay) ?: LocalDate.now()))

    val state = week.onEach { plans.materialize(it, it.plusDays(6)) }.flatMapLatest { w ->
        combine(plans.observeRange(w, w.plusDays(6)), diary.observeMeals(), profiles.observeTargets(), plans.observeTemplates()) { items, meals, targets, templates ->
            PlannerState(w, items.groupBy { it.date }, meals, targets, templates)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PlannerState())

    fun shiftWeek(d: Long) { week.value = week.value.plusWeeks(d) }
    fun planFood(date: LocalDate, meal: String, f: Food, q: Double, o: com.fdc.nutrition.domain.calc.ServingOption) = launchSafe { plans.planFood(date, meal, f.id, q, o) }
    fun planRecipe(date: LocalDate, meal: String, r: RecipeSummary, q: Double) = launchSafe { plans.planRecipe(date, meal, r.recipe.id, q) }
    fun log(p: PlannedMeal) = launchSafe { plans.logPlanned(p.id); message("Logged ${p.name} as eaten") }
    fun logDay(d: LocalDate) = launchSafe { val n = plans.logDay(d); message("Logged $n planned items as eaten") }
    fun remove(p: PlannedMeal) = launchSafe { plans.removePlanned(p.id) }
    fun duplicate(p: PlannedMeal, to: LocalDate) = launchSafe { plans.duplicatePlanned(p.id, to); message("Duplicated to ${Fmt.day(to)}") }
    fun move(p: PlannedMeal, to: LocalDate) = launchSafe { plans.updatePlanned(p.id, date = to); message("Moved to ${Fmt.day(to)}") }
    fun copyDay(from: LocalDate, to: LocalDate) = launchSafe { val n = plans.copyDay(from, to); message("Copied $n items to ${Fmt.day(to)}") }
    fun moveDay(from: LocalDate, to: LocalDate) = launchSafe { val n = plans.moveDay(from, to); message("Moved $n items to ${Fmt.day(to)}") }
    fun saveTemplate(name: String) = launchSafe { plans.saveWeekAsTemplate(week.value, name, true); message("Saved plan \"$name\"") }
    fun applyTemplate(t: MealPlanEntity) = launchSafe { val n = plans.applyTemplate(t.id, week.value); message("Added $n items from ${t.name}") }
    fun deleteTemplate(t: MealPlanEntity) = launchSafe { plans.deleteTemplate(t.id) }
    fun addRecurring(meal: String, f: Food, q: Double, o: com.fdc.nutrition.domain.calc.ServingOption, days: Set<DayOfWeek>) = launchSafe {
        plans.addRecurringFood(meal, f.id, q, o, days, LocalDate.now(), null); plans.materialize(week.value, week.value.plusDays(6)); message("Recurring plan added")
    }
}

@Composable
fun PlannerScreen(nav: NavHostController, vm: PlannerViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    CollectMessages(vm.messages)
    var pickFor by remember { mutableStateOf<Pair<LocalDate, String>?>(null) }
    var recurring by remember { mutableStateOf(false) }
    var saveTemplate by remember { mutableStateOf(false) }
    var templates by remember { mutableStateOf(false) }
    var datePick by remember { mutableStateOf<Pair<String, (LocalDate) -> Unit>?>(null) }
    var menu by remember { mutableStateOf(false) }

    Scaffold(topBar = {
        BackTopBar("Meal planner", nav) {
            IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Planner options") }
            DropdownMenu(menu, { menu = false }) {
                DropdownMenuItem({ Text("Save week as plan") }, { menu = false; saveTemplate = true })
                DropdownMenuItem({ Text("Apply saved plan…") }, { menu = false; templates = true })
                DropdownMenuItem({ Text("Add recurring item…") }, { menu = false; recurring = true })
            }
        }
    }, contentWindowInsets = WindowInsets.statusBars) { pad ->
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(16.dp, 0.dp, 16.dp, 32.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { vm.shiftWeek(-1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous week") }
                    Text("${Fmt.short(s.weekStart)} – ${Fmt.short(s.weekStart.plusDays(6))}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    IconButton(onClick = { vm.shiftWeek(1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next week") }
                }
                Banner("Planned meals are separate from your diary. Nothing is counted as eaten until you tap Log.", BannerKind.INFO)
            }
            items((0..6).map { s.weekStart.plusDays(it.toLong()) }, key = { it.toString() }) { d ->
                val list = s.items[d].orEmpty()
                val totals = NutrientAggregator.sumPlanned(list)
                val target = s.targets.effectiveOn(d)?.calories
                var dayMenu by remember { mutableStateOf(false) }
                AppCard(Modifier.testTag("plan_day_$d")) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${d.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())}, ${Fmt.short(d)}", style = MaterialTheme.typography.titleSmall)
                            Text("Planned: ${Fmt.kcal(totals.calories)} kcal${target?.let { " of ${Fmt.kcal(it)}" } ?: ""} · P ${Fmt.grams(totals.known(Nutrient.PROTEIN))} C ${Fmt.grams(totals.known(Nutrient.CARBOHYDRATE))} F ${Fmt.grams(totals.known(Nutrient.FAT))}",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { pickFor = d to suggestedMeal() }, modifier = Modifier.testTag("plan_add_$d")) { Icon(Icons.Filled.Add, "Plan food") }
                        Box {
                            IconButton(onClick = { dayMenu = true }) { Icon(Icons.Filled.MoreVert, "Day options") }
                            DropdownMenu(dayMenu, { dayMenu = false }) {
                                if (list.any { !it.isLogged }) DropdownMenuItem({ Text("Log whole day as eaten") }, { dayMenu = false; vm.logDay(d) })
                                DropdownMenuItem({ Text("Copy day to…") }, { dayMenu = false; datePick = "copy" to { to -> vm.copyDay(d, to) } })
                                DropdownMenuItem({ Text("Move day to…") }, { dayMenu = false; datePick = "move" to { to -> vm.moveDay(d, to) } })
                            }
                        }
                    }
                    s.meals.forEach { m ->
                        val ofMeal = list.filter { it.mealId == m.id }
                        if (ofMeal.isNotEmpty()) {
                            Text(m.name, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 6.dp))
                            ofMeal.forEach { p -> PlannedRow(p, vm, onDate = { label, cb -> datePick = label to cb }) }
                        }
                    }
                }
            }
        }
    }
    pickFor?.let { (d, mealDefault) ->
        var meal by remember { mutableStateOf(mealDefault) }
        FoodPickerDialog(
            title = "Plan for ${Fmt.day(d)}", searchFoods = { vm.foods.search(it, limit = 60) }, loadFood = { vm.foods.getFood(it) }, onDismiss = { pickFor = null },
            onPickFood = { f, q, o -> vm.planFood(d, meal, f, q, o) }, searchRecipes = { vm.recipes.search(it) }, onPickRecipe = { r, q -> vm.planRecipe(d, meal, r, q) },
            header = { MealChips(s.meals, meal, { meal = it }) },
        )
    }
    if (recurring) RecurringDialog(vm, s.meals) { recurring = false }
    if (saveTemplate) TextInputDialog("Save this week as a plan", "Plan name", "My week", { vm.saveTemplate(it) }) { saveTemplate = false }
    if (templates) AlertDialog(onDismissRequest = { templates = false }, title = { Text("Saved plans") }, text = {
        Column {
            if (s.templates.isEmpty()) Text("No saved plans yet. Plan a week, then use \"Save week as plan\".")
            s.templates.forEach { t -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text((if (t.isFavourite) "★ " else "") + t.name, Modifier.weight(1f)); TextButton({ vm.applyTemplate(t); templates = false }) { Text("Apply") }; TextButton({ vm.deleteTemplate(t) }) { Text("Delete") }
            } }
        }
    }, confirmButton = { TextButton({ templates = false }) { Text("Close") } })
    datePick?.let { (_, cb) -> DatePickerDialogField(s.weekStart, cb, { datePick = null }) }
}

@Composable
private fun PlannedRow(p: PlannedMeal, vm: PlannerViewModel, onDate: (String, (LocalDate) -> Unit) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (p.recurrenceId != null) Icon(Icons.Outlined.Repeat, "Recurring", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("${p.name} · ${Fmt.qty(p.quantity)} ${p.unitLabel}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f).padding(start = 4.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("${Fmt.kcal(p.nutrients.energyKcal)} kcal", style = MaterialTheme.typography.labelMedium)
        if (p.isLogged) Icon(Icons.Outlined.CheckCircle, "Logged", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 8.dp))
        else TextButton(onClick = { vm.log(p) }, modifier = Modifier.testTag("plan_log_${p.id}")) { Text("Log") }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Item options") }
            DropdownMenu(menu, { menu = false }) {
                DropdownMenuItem({ Text("Duplicate to…") }, { menu = false; onDate("dup") { vm.duplicate(p, it) } })
                if (!p.isLogged) DropdownMenuItem({ Text("Move to…") }, { menu = false; onDate("move") { vm.move(p, it) } })
                DropdownMenuItem({ Text("Remove") }, { menu = false; vm.remove(p) })
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecurringDialog(vm: PlannerViewModel, meals: List<Meal>, onDismiss: () -> Unit) {
    val days = remember { mutableStateListOf(*DayOfWeek.entries.take(5).toTypedArray()) }
    var meal by remember { mutableStateOf(Meal.BREAKFAST) }
    var pick by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Recurring item") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Repeats every week on:")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) { DayOfWeek.entries.forEach { d -> FilterChip(d in days, { if (d in days) days.remove(d) else days.add(d) }, { Text(d.getDisplayName(TextStyle.SHORT, Locale.getDefault())) }) } }
            MealChips(meals, meal, { meal = it })
        }
    }, confirmButton = { TextButton(onClick = { pick = true }, enabled = days.isNotEmpty()) { Text("Choose food") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
    if (pick) FoodPickerDialog("Recurring food", { vm.foods.search(it, limit = 60) }, { vm.foods.getFood(it) }, { pick = false; onDismiss() }, { f, q, o -> vm.addRecurring(meal, f, q, o, days.toSet()) })
}
