package com.fdc.nutrition.ui.diary

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import androidx.navigation.toRoute
import com.fdc.nutrition.data.repository.DiaryRepository
import com.fdc.nutrition.domain.calc.ServingOption
import com.fdc.nutrition.domain.model.*
import com.fdc.nutrition.ui.BaseViewModel
import com.fdc.nutrition.ui.components.*
import com.fdc.nutrition.ui.food.NutrientTable
import com.fdc.nutrition.ui.navigation.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.ZoneId
import javax.inject.Inject

@HiltViewModel
class EntryEditViewModel @Inject constructor(savedState: SavedStateHandle, private val diary: DiaryRepository) : BaseViewModel() {
    private val id = savedState.toRoute<EntryEditRoute>().entryId
    var entry by mutableStateOf<DiaryEntry?>(null)
    var options by mutableStateOf<List<ServingOption>>(emptyList())
    var option by mutableStateOf<ServingOption?>(null)
    var quantity by mutableStateOf(""); var mealId by mutableStateOf(""); var note by mutableStateOf("")
    var date by mutableStateOf(java.time.LocalDate.now())
    val meals = diary.observeMeals().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init { viewModelScope.launch { load() } }
    private suspend fun load() {
        val e = diary.entry(id) ?: return
        entry = e; quantity = Fmt.qty(e.quantity); mealId = e.mealId; note = e.note ?: ""; date = e.date
        options = diary.optionsForEntry(e)
        option = options.firstOrNull { it.unit == e.unit && kotlin.math.abs(it.amountPerUnit - e.amountPerUnit) < 1e-9 } ?: options.first()
    }

    /** Preview nutrition recalculated from the entry's own snapshot. */
    fun preview(): Nutrients? {
        val e = entry ?: return null
        val q = Fmt.parse(quantity)?.takeIf { it > 0 } ?: return null
        val o = option ?: return null
        return e.copy(quantity = q, amountPerUnit = o.amountPerUnit).nutrients
    }

    fun save(onDone: () -> Unit) = launchSafe {
        val q = Fmt.parse(quantity) ?: throw IllegalArgumentException("Enter a valid quantity")
        diary.updateEntry(id, quantity = q, option = option, mealId = mealId, date = date, note = note)
        message("Entry updated"); onDone()
    }
    fun duplicate(onDone: () -> Unit) = launchSafe { diary.duplicate(id); message("Duplicated"); onDone() }
    fun delete(onDone: () -> Unit) = launchSafe { diary.delete(id); message("Entry deleted"); onDone() }
    fun refresh() = launchSafe { diary.refreshSnapshot(id); load(); message("Nutrition updated from the current food data") }
}

@Composable
fun EntryEditScreen(nav: NavHostController, vm: EntryEditViewModel = hiltViewModel()) {
    val meals by vm.meals.collectAsStateWithLifecycle()
    CollectMessages(vm.messages)
    var confirmRefresh by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var pickDate by remember { mutableStateOf(false) }
    Scaffold(topBar = { BackTopBar("Edit entry", nav) }, contentWindowInsets = WindowInsets.statusBars,
        bottomBar = { Button(onClick = { vm.save { nav.popBackStack() } }, modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp).height(52.dp).testTag("save_entry")) { Text("Save changes") } }) { pad ->
        val e = vm.entry ?: run { LoadingBox(Modifier.padding(pad)); return@Scaffold }
        val n = vm.preview()
        Column(Modifier.padding(pad).imePadding().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(e.name, style = MaterialTheme.typography.titleLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { QualityBadge(e.dataQuality); if (e.preparation != PreparationState.NOT_APPLICABLE) Pill(e.preparation.displayName, MaterialTheme.colorScheme.secondary) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField(vm.quantity, { vm.quantity = it }, "Quantity", Modifier.weight(0.4f).testTag("edit_quantity"))
                UnitDropdown(vm.options, vm.option, { vm.option = it }, Modifier.weight(0.6f))
            }
            Text("Meal", style = MaterialTheme.typography.titleSmall)
            MealChips(meals, vm.mealId, { vm.mealId = it })
            TextButton(onClick = { pickDate = true }) { Text("Date: ${Fmt.day(vm.date)}") }
            OutlinedTextField(vm.note, { vm.note = it }, label = { Text("Note") }, modifier = Modifier.fillMaxWidth())
            AppCard {
                Text("${Fmt.kcal(n?.energyKcal)} kcal", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.testTag("edit_live_kcal"))
                NutrientTable(n ?: Nutrients.EMPTY)
            }
            Banner("Nutrition snapshot saved when logged (${Fmt.long(e.loggedAt.atZone(ZoneId.systemDefault()).toLocalDate())}). Later changes to the food or recipe don't alter this entry.", BannerKind.INFO)
            if (e.type != EntryType.QUICK_ADD) OutlinedButton(onClick = { confirmRefresh = true }, modifier = Modifier.fillMaxWidth()) { Text("Apply current food data (correction)") }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { vm.duplicate { nav.popBackStack() } }, modifier = Modifier.weight(1f)) { Text("Duplicate") }
                OutlinedButton(onClick = { confirmDelete = true }, modifier = Modifier.weight(1f), colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Delete") }
            }
        }
    }
    if (confirmRefresh) ConfirmDialog("Update this entry's nutrition?", "This replaces the saved snapshot with the food's current values. Use it only to correct a mistake.", "Update", false, vm::refresh, { confirmRefresh = false })
    if (confirmDelete) ConfirmDialog("Delete entry?", "This removes it from your diary.", "Delete", true, { vm.delete { nav.popBackStack() } }, { confirmDelete = false })
    if (pickDate) DatePickerDialogField(vm.date, { vm.date = it }, { pickDate = false })
}
