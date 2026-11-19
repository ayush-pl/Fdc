package com.fdc.nutrition.ui.food

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import androidx.navigation.toRoute
import com.fdc.nutrition.data.repository.FoodRepository
import com.fdc.nutrition.domain.model.Food
import com.fdc.nutrition.domain.model.Nutrient
import com.fdc.nutrition.ui.BaseViewModel
import com.fdc.nutrition.ui.components.*
import com.fdc.nutrition.ui.navigation.BackTopBar
import com.fdc.nutrition.ui.navigation.CompareRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CompareViewModel @Inject constructor(savedState: SavedStateHandle, private val foods: FoodRepository) : BaseViewModel() {
    private val route = savedState.toRoute<CompareRoute>()
    var a by mutableStateOf<Food?>(null); var b by mutableStateOf<Food?>(null)
    var picking by mutableStateOf<Int?>(null)
    var query by mutableStateOf(""); var results by mutableStateOf<List<Food>>(emptyList())
    private var job: Job? = null
    init { viewModelScope.launch { a = route.a?.let { foods.getFood(it) }; b = route.b?.let { foods.getFood(it) }; if (a == null) picking = 0 else if (b == null) picking = 1 } }
    fun search(q: String) { query = q; job?.cancel(); job = viewModelScope.launch { delay(250); results = if (q.trim().length >= 2) foods.search(q, limit = 40) else emptyList() } }
    fun pick(f: Food) { viewModelScope.launch { val full = foods.getFood(f.id); if (picking == 0) a = full else b = full; picking = null; query = ""; results = emptyList() } }
}

@Composable
fun CompareFoodsScreen(nav: NavHostController, vm: CompareViewModel = hiltViewModel()) {
    Scaffold(topBar = { BackTopBar("Compare foods", nav) }, contentWindowInsets = WindowInsets.statusBars) { pad ->
        Column(Modifier.padding(pad).padding(horizontal = 16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Slot(vm.a, "First food", Modifier.weight(1f)) { vm.picking = 0 }
                Slot(vm.b, "Second food", Modifier.weight(1f)) { vm.picking = 1 }
            }
            Spacer(Modifier.height(8.dp))
            if (vm.picking != null) {
                FoodSearchField(vm.query, vm::search, {}, placeholder = "Search food to compare")
                LazyColumn { items(vm.results, key = { it.id }) { f -> FoodRow(f, { vm.pick(f) }) } }
            } else {
                Text("Per 100 g/ml", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                LazyColumn {
                    items(Nutrient.entries) { n ->
                        val va = vm.a?.nutrientsPer100?.get(n); val vb = vm.b?.nutrientsPer100?.get(n)
                        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                            Text(n.displayName, Modifier.weight(1.2f), style = MaterialTheme.typography.bodyMedium)
                            val higher = if (va != null && vb != null && va != vb) (if (va > vb) 0 else 1) else -1
                            Text(Fmt.nutrient(va, n), Modifier.weight(1f), textAlign = TextAlign.End, fontWeight = if (higher == 0) FontWeight.Bold else FontWeight.Normal)
                            Text(Fmt.nutrient(vb, n), Modifier.weight(1f), textAlign = TextAlign.End, fontWeight = if (higher == 1) FontWeight.Bold else FontWeight.Normal)
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun Slot(f: Food?, hint: String, modifier: Modifier, onClick: () -> Unit) {
    AppCard(modifier.clickable(onClick = onClick)) {
        Text(f?.name ?: hint, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(if (f == null) "Tap to choose" else "${Fmt.kcal(f.nutrientsPer100.energyKcal)} kcal/100 ${f.basis.symbol}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
