package com.fdc.nutrition.ui.diary

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.fdc.nutrition.domain.model.*
import com.fdc.nutrition.ui.components.*
import com.fdc.nutrition.ui.home.MealSummary
import com.fdc.nutrition.ui.navigation.*
import com.fdc.nutrition.ui.theme.LocalMacroPalette
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiaryScreen(nav: NavHostController, vm: DiaryViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val date by vm.date.collectAsStateWithLifecycle()
    CollectMessages(vm.messages)
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    var addMeal by remember { mutableStateOf(false) }
    val palette = LocalMacroPalette.current

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Diary") }, actions = {
                IconButton(onClick = { nav.navigate(CalendarRoute) }) { Icon(Icons.Outlined.CalendarMonth, "History calendar") }
                IconButton(onClick = { nav.navigate(PlannerRoute()) }) { Icon(Icons.Outlined.EventNote, "Meal planner") }
            }, colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background))
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { nav.navigate(AddFoodRoute(date.toArg())) }, modifier = Modifier.testTag("diary_add")) { Icon(Icons.Filled.Add, "Add food") }
        },
        contentWindowInsets = WindowInsets.statusBars,
    ) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(16.dp, 0.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { DateNavigator(date, vm::setDate) }
            item {
                AppCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${Fmt.kcal(s.totals.calories)} kcal", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.testTag("diary_total_kcal"))
                            Text(s.target?.calories?.let { "of ${Fmt.kcal(it)} · ${Fmt.kcal(it - s.totals.calories)} remaining" } ?: "No target set",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Pill(when (s.status) { DayLogStatus.COMPLETE -> "Complete"; DayLogStatus.PARTIAL -> "Partial"; DayLogStatus.EMPTY -> "Empty" },
                            if (s.status == DayLogStatus.COMPLETE) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.height(8.dp))
                    LinearBar(s.target?.calories?.let { (s.totals.calories / it).toFloat() } ?: 0f, palette.calories)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        listOf(Nutrient.PROTEIN to palette.protein, Nutrient.CARBOHYDRATE to palette.carbs, Nutrient.FAT to palette.fat, Nutrient.FIBRE to palette.fibre).forEach { (n, c) ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                val t = s.totals[n]
                                Text(if (t.isUnknown) "—" else "${Fmt.grams(t.amount)} g", style = MaterialTheme.typography.titleSmall, color = c, modifier = Modifier.testTag("diary_total_${n.key}"))
                                Text(n.displayName + if (t.missingCount > 0 && !t.isUnknown) "*" else "", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    if (Nutrient.MACROS.any { s.totals[it].missingCount > 0 } && s.totals.itemCount > 0) Text("* Some entries don't include this nutrient, so the total is partial.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(s.dayLog?.markedComplete == true, vm::setComplete, Modifier.testTag("day_complete"))
                        Text("Mark day as fully logged", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        TextButton(onClick = { nav.navigate(WaterRoute(date.toArg())) }) { Icon(Icons.Outlined.WaterDrop, null, Modifier.size(16.dp), tint = palette.water); Text(" ${Fmt.litres(s.waterMl)}") }
                    }
                }
            }
            if (s.planned.isNotEmpty()) item {
                AppCard {
                    Text("Planned (not yet eaten)", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.tertiary)
                    Text("Planned food isn't counted until you log it.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    s.planned.forEach { p ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${s.allMeals.firstOrNull { it.id == p.mealId }?.name ?: ""}: ${p.name} · ${Fmt.qty(p.quantity)} ${p.unitLabel}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            TextButton(onClick = { vm.logPlanned(p) }) { Text("Log") }
                        }
                    }
                }
            }
            items(s.meals, key = { it.meal.id }) { m ->
                MealCard(m, s, nav, vm, onDelete = { e ->
                    vm.delete(e)
                    scope.launch { if (snackbar.showSnackbar("Deleted ${e.name}", "Undo", duration = SnackbarDuration.Short) == SnackbarResult.ActionPerformed) vm.restore(e) }
                })
            }
            item { TextButton(onClick = { addMeal = true }) { Icon(Icons.Filled.Add, null); Text("Add custom meal") } }
        }
    }
    if (addMeal) TextInputDialog("New meal", "Meal name", "", { vm.addMeal(it) }) { addMeal = false }
}

@Composable
private fun MealCard(m: MealSummary, s: DiaryState, nav: NavHostController, vm: DiaryViewModel, onDelete: (DiaryEntry) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf<String?>(null) }
    var pickCopyDate by remember { mutableStateOf<String?>(null) }
    val date = s.date
    AppCard(Modifier.testTag("meal_card_${m.meal.id}")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(m.meal.name, style = MaterialTheme.typography.titleMedium)
                if (m.entries.isNotEmpty()) Text("${Fmt.kcal(m.totals.calories)} kcal · P ${Fmt.grams(m.totals.known(Nutrient.PROTEIN))} · C ${Fmt.grams(m.totals.known(Nutrient.CARBOHYDRATE))} · F ${Fmt.grams(m.totals.known(Nutrient.FAT))}",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("meal_kcal_${m.meal.id}"))
            }
            IconButton(onClick = { nav.navigate(AddFoodRoute(date.toArg(), m.meal.id)) }, modifier = Modifier.testTag("diary_add_${m.meal.id}")) { Icon(Icons.Filled.Add, "Add to ${m.meal.name}") }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "${m.meal.name} options") }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem({ Text("Copy from yesterday") }, { menu = false; vm.copyMealFrom(date.minusDays(1), m.meal.id, m.meal.id) })
                    DropdownMenuItem({ Text("Copy from another day…") }, { menu = false; pickCopyDate = "from" })
                    if (m.entries.isNotEmpty()) {
                        DropdownMenuItem({ Text("Copy to another day…") }, { menu = false; pickCopyDate = "to" })
                        DropdownMenuItem({ Text("Save as favourite meal") }, { menu = false; dialog = "save" })
                    }
                    DropdownMenuItem({ Text(if (s.notes[m.meal.id] == null) "Add note" else "Edit note") }, { menu = false; dialog = "note" })
                    DropdownMenuItem({ Text("Quick add calories") }, { menu = false; nav.navigate(QuickAddRoute(date.toArg(), m.meal.id)) })
                }
            }
        }
        s.notes[m.meal.id]?.let { Text("📝 $it", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 4.dp)) }
        m.entries.forEach { e -> EntryRow(e, s.allMeals, nav, vm, onDelete) }
    }
    when (dialog) {
        "save" -> TextInputDialog("Save meal", "Name", "My ${m.meal.name.lowercase()}", { vm.saveMeal(m.meal.id, it) }) { dialog = null }
        "note" -> TextInputDialog("Meal note", "Note", s.notes[m.meal.id] ?: "", { vm.setNote(m.meal.id, it) }, allowEmpty = true) { dialog = null }
    }
    pickCopyDate?.let { dir ->
        DatePickerDialogField(if (dir == "from") date.minusDays(1) else date.plusDays(1), { d -> if (dir == "from") vm.copyMealFrom(d, m.meal.id, m.meal.id) else vm.copyMealTo(m.meal.id, d) }, { pickCopyDate = null })
    }
}

@Composable
private fun EntryRow(e: DiaryEntry, meals: List<Meal>, nav: NavHostController, vm: DiaryViewModel, onDelete: (DiaryEntry) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    var move by remember { mutableStateOf(false) }
    val n = e.nutrients
    Row(Modifier.fillMaxWidth().clickable { if (e.type == EntryType.QUICK_ADD) nav.navigate(QuickAddRoute(e.date.toArg(), e.mealId, e.id)) else nav.navigate(EntryEditRoute(e.id)) }.padding(vertical = 6.dp).testTag("entry_${e.id}"),
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(e.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                if (e.isCalorieOnly) "Calories only · macros not recorded"
                else "${Fmt.qty(e.quantity)} ${e.unitLabel}${if (e.unit.isPlainMetricOrImperial) "" else " (${Fmt.num(e.baseAmount)} ${e.basis.symbol})"} · P ${Fmt.grams(n.proteinG)} C ${Fmt.grams(n.carbohydrateG)} F ${Fmt.grams(n.fatG)}",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Text("${Fmt.kcal(n.energyKcal)}", style = MaterialTheme.typography.titleSmall, modifier = Modifier.testTag("entry_kcal_${e.id}"))
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Entry options") }
            DropdownMenu(menu, { menu = false }) {
                DropdownMenuItem({ Text("Edit") }, { menu = false; if (e.type == EntryType.QUICK_ADD) nav.navigate(QuickAddRoute(e.date.toArg(), e.mealId, e.id)) else nav.navigate(EntryEditRoute(e.id)) })
                DropdownMenuItem({ Text("Duplicate") }, { menu = false; vm.duplicate(e) })
                DropdownMenuItem({ Text("Move to…") }, { menu = false; move = true })
                DropdownMenuItem({ Text("Delete") }, { menu = false; onDelete(e) }, modifier = Modifier.testTag("delete_entry"))
            }
        }
    }
    if (move) AlertDialog(onDismissRequest = { move = false }, title = { Text("Move to meal") }, text = {
        Column { meals.filter { it.id != e.mealId }.forEach { m -> TextButton(onClick = { vm.move(e, m.id); move = false }) { Text(m.name) } } }
    }, confirmButton = { TextButton(onClick = { move = false }) { Text("Cancel") } })
}

@Composable
fun TextInputDialog(title: String, label: String, initial: String, onSave: (String) -> Unit, allowEmpty: Boolean = false, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text(title) },
        text = { OutlinedTextField(text, { text = it }, label = { Text(label) }, modifier = Modifier.fillMaxWidth().testTag("text_input")) },
        confirmButton = { TextButton(onClick = { onSave(text); onDismiss() }, enabled = allowEmpty || text.isNotBlank()) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
