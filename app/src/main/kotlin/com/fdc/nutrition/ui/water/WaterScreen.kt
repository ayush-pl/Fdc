package com.fdc.nutrition.ui.water

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import androidx.navigation.toRoute
import com.fdc.nutrition.data.repository.ProfileRepository
import com.fdc.nutrition.data.repository.WaterRepository
import com.fdc.nutrition.domain.calc.effectiveOn
import com.fdc.nutrition.domain.model.BeverageType
import com.fdc.nutrition.domain.model.UserPreferences
import com.fdc.nutrition.domain.model.WaterEntry
import com.fdc.nutrition.ui.BaseViewModel
import com.fdc.nutrition.ui.components.*
import com.fdc.nutrition.ui.navigation.*
import com.fdc.nutrition.ui.theme.LocalMacroPalette
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class WaterState(val date: LocalDate = LocalDate.now(), val entries: List<WaterEntry> = emptyList(), val total: Double = 0.0, val target: Double? = null,
    val prefs: UserPreferences = UserPreferences(), val history: List<Pair<LocalDate, Double?>> = emptyList(), val monthAvg: Double? = null)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class WaterViewModel @Inject constructor(savedState: SavedStateHandle, private val water: WaterRepository, private val profiles: ProfileRepository) : BaseViewModel() {
    val date = MutableStateFlow(runCatching { savedState.toRoute<WaterRoute>().date }.getOrNull()?.let(LocalDate::ofEpochDay) ?: LocalDate.now())
    val state = date.flatMapLatest { d ->
        combine(water.observeForDate(d), water.observeRange(d.minusDays(29), d), profiles.observeTargets(), profiles.observePreferences()) { e, month, t, p ->
            val daily = WaterRepository.dailyTotals(month, p.countBeveragesAsWater)
            WaterState(d, e, WaterRepository.hydrationTotal(e, p.countBeveragesAsWater), t.effectiveOn(d)?.waterMl, p,
                (6 downTo 0).map { d.minusDays(it.toLong()) }.map { it to daily[it] }, daily.values.takeIf { it.isNotEmpty() }?.average())
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), WaterState())

    fun add(ml: Double, type: BeverageType = BeverageType.WATER) = launchSafe { water.add(date.value, ml, type) }
    fun remove(e: WaterEntry) = launchSafe { water.remove(e.id) }
}

@Composable
fun WaterScreen(nav: NavHostController, vm: WaterViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    CollectMessages(vm.messages)
    val palette = LocalMacroPalette.current
    var custom by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(BeverageType.WATER) }
    val timeFmt = remember { DateTimeFormatter.ofPattern("HH:mm") }
    Scaffold(topBar = { BackTopBar("Water", nav) }, contentWindowInsets = WindowInsets.statusBars) { pad ->
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { DateNavigator(s.date, { vm.date.value = it }) }
            item {
                AppCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ProgressRing(s.target?.let { (s.total / it).toFloat() } ?: 0f, palette.water, size = 130.dp, overColor = palette.water) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(Fmt.litres(s.total), style = MaterialTheme.typography.titleLarge, modifier = Modifier.testTag("water_total"))
                                Text(s.target?.let { "of ${Fmt.litres(it)}" } ?: "no goal", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        Spacer(Modifier.width(16.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            s.prefs.waterQuickAmountsMl.chunked(2).forEach { row ->
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { row.forEach { ml -> FilledTonalButton(onClick = { vm.add(ml.toDouble()) }, modifier = Modifier.testTag("water_add_$ml")) { Text("+$ml") } } }
                            }
                        }
                    }
                    if (!s.prefs.countBeveragesAsWater) Text("Only plain water counts toward the goal. Change this in Settings.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item {
                AppCard {
                    Text("Custom amount", style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        NumberField(custom, { custom = it }, "Amount", Modifier.weight(1f).testTag("water_custom"), suffix = "ml")
                        DropdownField("Drink", BeverageType.entries, type, { it.displayName }, { type = it }, Modifier.weight(1f))
                    }
                    Button(onClick = { Fmt.parse(custom)?.let { vm.add(it, type); custom = "" } }, enabled = Fmt.parse(custom)?.let { it > 0 } == true, modifier = Modifier.fillMaxWidth().testTag("water_custom_add")) { Text("Add") }
                }
            }
            item { AppCard { BarChart(s.history.map { ChartPoint(it.first.dayOfWeek.name.take(2), it.second) }, palette.water, title = "Last 7 days", target = s.target, valueFormat = { Fmt.litres(it) }, height = 120.dp)
                Text("30-day average on days with entries: ${s.monthAvg?.let { Fmt.litres(it) } ?: "—"}", style = MaterialTheme.typography.bodySmall) } }
            if (s.entries.isNotEmpty()) item { Text("Entries", style = MaterialTheme.typography.titleSmall) }
            items(s.entries, key = { it.id }) { e ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(e.loggedAt.atZone(ZoneId.systemDefault()).format(timeFmt), style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(56.dp))
                    Text("${Fmt.ml(e.amountMl)} · ${e.beverage.displayName}${if (e.beverage != BeverageType.WATER && !s.prefs.countBeveragesAsWater) " (not counted)" else ""}", Modifier.weight(1f))
                    IconButton(onClick = { vm.remove(e) }) { Icon(Icons.Filled.Delete, "Remove entry") }
                }
            }
        }
    }
}
