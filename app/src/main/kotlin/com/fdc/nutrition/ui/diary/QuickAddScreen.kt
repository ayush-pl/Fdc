package com.fdc.nutrition.ui.diary

import androidx.compose.foundation.layout.*
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
import com.fdc.nutrition.domain.model.Nutrient
import com.fdc.nutrition.ui.BaseViewModel
import com.fdc.nutrition.ui.components.*
import com.fdc.nutrition.ui.navigation.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class QuickAddViewModel @Inject constructor(savedState: SavedStateHandle, private val diary: DiaryRepository) : BaseViewModel() {
    private val route = savedState.toRoute<QuickAddRoute>()
    val date: LocalDate = LocalDate.ofEpochDay(route.date)
    var mealId by mutableStateOf(route.mealId)
    var name by mutableStateOf(""); var kcal by mutableStateOf(""); var protein by mutableStateOf(""); var carbs by mutableStateOf(""); var fat by mutableStateOf("")
    var saving by mutableStateOf(false)
    val editing = route.entryId != null
    val meals = diary.observeMeals().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        route.entryId?.let { id -> viewModelScope.launch {
            diary.entry(id)?.let { e -> name = e.name; mealId = e.mealId; val n = e.nutrientsPerReference
                kcal = n.energyKcal?.let { Fmt.num(it, 0) } ?: ""; protein = n[Nutrient.PROTEIN]?.let { Fmt.num(it) } ?: ""; carbs = n[Nutrient.CARBOHYDRATE]?.let { Fmt.num(it) } ?: ""; fat = n[Nutrient.FAT]?.let { Fmt.num(it) } ?: "" }
        } }
    }

    fun save(onDone: () -> Unit) {
        val k = Fmt.parse(kcal) ?: run { message("Enter calories"); return }
        if (saving) return
        saving = true
        launchSafe {
            try {
                if (route.entryId != null) { diary.updateQuickAdd(route.entryId, name, k, Fmt.parse(protein), Fmt.parse(carbs), Fmt.parse(fat)); diary.updateEntry(route.entryId, mealId = mealId) }
                else diary.quickAdd(date, mealId, name, k, Fmt.parse(protein), Fmt.parse(carbs), Fmt.parse(fat))
                message("Saved"); onDone()
            } finally { saving = false }
        }
    }
    fun delete(onDone: () -> Unit) = launchSafe { route.entryId?.let { diary.delete(it) }; onDone() }
}

@Composable
fun QuickAddScreen(nav: NavHostController, vm: QuickAddViewModel = hiltViewModel()) {
    val meals by vm.meals.collectAsStateWithLifecycle()
    CollectMessages(vm.messages)
    Scaffold(topBar = { BackTopBar(if (vm.editing) "Edit quick entry" else "Quick add · ${Fmt.day(vm.date)}", nav) }, contentWindowInsets = WindowInsets.statusBars) { pad ->
        Column(Modifier.padding(pad).padding(16.dp).imePadding(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            MealChips(meals, vm.mealId, { vm.mealId = it })
            OutlinedTextField(vm.name, { vm.name = it }, label = { Text("Description (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            NumberField(vm.kcal, { vm.kcal = it }, "Calories *", Modifier.fillMaxWidth().testTag("quick_kcal"), suffix = "kcal")
            Text("Macros are optional. Anything you leave blank stays unknown — it is never assumed to be zero.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField(vm.protein, { vm.protein = it }, "Protein", Modifier.weight(1f), suffix = "g")
                NumberField(vm.carbs, { vm.carbs = it }, "Carbs", Modifier.weight(1f), suffix = "g")
                NumberField(vm.fat, { vm.fat = it }, "Fat", Modifier.weight(1f), suffix = "g")
            }
            Button(onClick = { vm.save { nav.popBackStack() } }, enabled = !vm.saving, modifier = Modifier.fillMaxWidth().height(52.dp).testTag("quick_save")) { Text("Save") }
            if (vm.editing) OutlinedButton(onClick = { vm.delete { nav.popBackStack() } }, modifier = Modifier.fillMaxWidth()) { Text("Delete") }
        }
    }
}
