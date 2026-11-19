package com.fdc.nutrition.ui.diary

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.fdc.nutrition.data.repository.DiaryRepository
import com.fdc.nutrition.data.repository.SavedMeal
import com.fdc.nutrition.ui.BaseViewModel
import com.fdc.nutrition.ui.components.EmptyState
import com.fdc.nutrition.ui.food.SavedMealRow
import com.fdc.nutrition.ui.navigation.BackTopBar
import com.fdc.nutrition.ui.navigation.CollectMessages
import com.fdc.nutrition.ui.suggestedMeal
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class SavedMealsViewModel @Inject constructor(private val diary: DiaryRepository) : BaseViewModel() {
    val meals = diary.observeSavedMeals().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    fun log(m: SavedMeal) = launchSafe { diary.logSavedMeal(m.id, LocalDate.now(), m.defaultMealId ?: suggestedMeal()); message("Logged ${m.name} today") }
    fun delete(m: SavedMeal) = launchSafe { diary.deleteSavedMeal(m.id) }
}

@Composable
fun SavedMealsScreen(nav: NavHostController, vm: SavedMealsViewModel = hiltViewModel()) {
    val meals by vm.meals.collectAsStateWithLifecycle()
    CollectMessages(vm.messages)
    Scaffold(topBar = { BackTopBar("Saved meals", nav) }, contentWindowInsets = WindowInsets.statusBars) { pad ->
        LazyColumn(Modifier.padding(pad)) {
            if (meals.isEmpty()) item { EmptyState(Icons.Outlined.BookmarkBorder, "No saved meals", "Save a meal from the diary to log it again in one tap.") }
            items(meals, key = { it.id }) { m ->
                Row { Box(Modifier.weight(1f)) { SavedMealRow(m) { vm.log(m) } }; IconButton(onClick = { vm.delete(m) }) { Icon(Icons.Filled.Delete, "Delete saved meal") } }
            }
        }
    }
}
