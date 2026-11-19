package com.fdc.nutrition.ui.body

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.fdc.nutrition.data.repository.AnalyticsRepository
import com.fdc.nutrition.data.repository.BodyRepository
import com.fdc.nutrition.data.repository.ProfileRepository
import com.fdc.nutrition.domain.model.*
import com.fdc.nutrition.ui.BaseViewModel
import com.fdc.nutrition.ui.components.*
import com.fdc.nutrition.ui.navigation.*
import com.fdc.nutrition.ui.theme.LocalMacroPalette
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@HiltViewModel
class BodyViewModel @Inject constructor(private val body: BodyRepository, analytics: AnalyticsRepository, private val profiles: ProfileRepository) : BaseViewModel() {
    val records = body.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val progress = analytics.observeWeightProgress().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val prefs = profiles.observePreferences().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserPreferences())
    val profile = profiles.observeProfile().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun save(id: String?, date: LocalDate, type: MeasurementType, value: Double, custom: String?, note: String?, onDone: () -> Unit) = launchSafe {
        body.save(id, date, type, value, custom, note); message("Saved"); onDone()
    }
    fun delete(m: BodyMeasurement) = launchSafe { body.delete(m.id); message("Deleted") }
    fun setTarget(kg: Double) = launchSafe { profile.value?.let { profiles.saveProfile(it.copy(targetWeightKg = kg)) } }
}

@Composable
fun BodyScreen(nav: NavHostController, vm: BodyViewModel = hiltViewModel()) {
    val records by vm.records.collectAsStateWithLifecycle()
    val p by vm.progress.collectAsStateWithLifecycle()
    val prefs by vm.prefs.collectAsStateWithLifecycle()
    CollectMessages(vm.messages)
    val palette = LocalMacroPalette.current
    var editing by remember { mutableStateOf<BodyMeasurement?>(null) }
    var adding by remember { mutableStateOf(false) }
    var chartType by remember { mutableStateOf(MeasurementType.WEIGHT) }
    val fmt = remember { DateTimeFormatter.ofPattern("d/M") }
    fun display(m: BodyMeasurement) = when { m.type == MeasurementType.WEIGHT -> Fmt.weight(m.value, prefs.weightUnit); m.type == MeasurementType.BODY_FAT -> "${Fmt.num(m.value)} %"; else -> Fmt.length(m.value, prefs.lengthUnit) }
    Scaffold(topBar = { BackTopBar("Body", nav) }, floatingActionButton = { FloatingActionButton(onClick = { adding = true }, modifier = Modifier.testTag("add_measurement")) { Icon(Icons.Filled.Add, "Add record") } }, contentWindowInsets = WindowInsets.statusBars) { pad ->
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(16.dp, 0.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                AppCard {
                    Text("Weight progress", style = MaterialTheme.typography.titleSmall)
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Stat("Current", Fmt.weight(p?.currentKg, prefs.weightUnit), "body_current")
                        Stat("Start", Fmt.weight(p?.startKg, prefs.weightUnit), "body_start")
                        Stat("Target", Fmt.weight(p?.targetKg, prefs.weightUnit), "body_target")
                        Stat("Change", p?.changeKg?.let { (if (it > 0) "+" else "") + Fmt.weight(it, prefs.weightUnit) } ?: "—", "body_change")
                    }
                    p?.progressPercent?.let { LinearBar((it / 100).toFloat(), palette.weight); Text("${Fmt.num(it, 0)}% toward target", style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("body_progress")) }
                    p?.weeklyAverages?.lastOrNull()?.let { Text("This week's average: ${Fmt.weight(it.average, prefs.weightUnit)} (${it.count} weigh-ins)", style = MaterialTheme.typography.bodySmall) }
                    p?.weeklyTrendKg?.let { Text("Change vs previous week's average: ${(if (it > 0) "+" else "") + Fmt.weight(it, prefs.weightUnit, 2)}", style = MaterialTheme.typography.bodySmall) }
                    Text("Single weigh-ins fluctuate by 1–2 kg with hydration and meals; weekly averages show the real trend.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { items(MeasurementType.entries.filter { it != MeasurementType.CUSTOM }) { t -> FilterChip(chartType == t, { chartType = t }, { Text(t.displayName) }) } }
            }
            item {
                AppCard {
                    val pts = records.filter { it.type == chartType }.sortedBy { it.date }.map {
                        ChartPoint(it.date.format(fmt), when { chartType == MeasurementType.WEIGHT -> Fmt.weightValue(it.value, prefs.weightUnit); chartType.isLength -> Fmt.lengthValue(it.value, prefs.lengthUnit); else -> it.value })
                    }
                    LineChart(pts, palette.weight, title = chartType.displayName, target = if (chartType == MeasurementType.WEIGHT) p?.targetKg?.let { Fmt.weightValue(it, prefs.weightUnit) } else null, valueFormat = { Fmt.num(it) })
                    if (chartType == MeasurementType.WEIGHT && (p?.weeklyAverages?.size ?: 0) >= 2) BarChart(p!!.weeklyAverages.map { ChartPoint(it.weekStart.format(fmt), Fmt.weightValue(it.average, prefs.weightUnit)) }, palette.weight.copy(alpha = 0.7f), title = "Weekly averages", valueFormat = { Fmt.num(it) }, height = 100.dp)
                }
            }
            item { Text("History", style = MaterialTheme.typography.titleSmall) }
            if (records.isEmpty()) item { Text("No records yet. Tap + to add your weight or measurements.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(records, key = { it.id }) { m ->
                Row(Modifier.fillMaxWidth().clickable { editing = m }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(Fmt.short(m.date), Modifier.width(64.dp), style = MaterialTheme.typography.labelMedium)
                    Text(if (m.type == MeasurementType.CUSTOM) m.customName ?: "Custom" else m.type.displayName, Modifier.weight(1f))
                    Text(display(m), style = MaterialTheme.typography.titleSmall)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
    if (adding || editing != null) MeasurementDialog(editing, prefs, onDismiss = { adding = false; editing = null },
        onDelete = { editing?.let(vm::delete); editing = null }) { id, date, type, value, custom, note -> vm.save(id, date, type, value, custom, note) { adding = false; editing = null } }
}

@Composable
private fun Stat(label: String, value: String, tag: String) {
    Column { Text(value, style = MaterialTheme.typography.titleSmall, modifier = Modifier.testTag(tag)); Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
}

@Composable
private fun MeasurementDialog(existing: BodyMeasurement?, prefs: UserPreferences, onDismiss: () -> Unit, onDelete: () -> Unit, onSave: (String?, LocalDate, MeasurementType, Double, String?, String?) -> Unit) {
    var type by remember { mutableStateOf(existing?.type ?: MeasurementType.WEIGHT) }
    fun toDisplay(v: Double, t: MeasurementType) = when { t == MeasurementType.WEIGHT -> Fmt.weightValue(v, prefs.weightUnit); t.isLength -> Fmt.lengthValue(v, prefs.lengthUnit); else -> v }
    var value by remember { mutableStateOf(existing?.let { Fmt.num(toDisplay(it.value, it.type), 2) } ?: "") }
    var date by remember { mutableStateOf(existing?.date ?: LocalDate.now()) }
    var custom by remember { mutableStateOf(existing?.customName ?: "") }
    var note by remember { mutableStateOf(existing?.note ?: "") }
    var pick by remember { mutableStateOf(false) }
    val unit = when { type == MeasurementType.WEIGHT -> prefs.weightUnit.symbol; type == MeasurementType.BODY_FAT -> "%"; else -> prefs.lengthUnit.symbol }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (existing == null) "Add record" else "Edit record") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            DropdownField("Type", MeasurementType.entries, type, { it.displayName }, { type = it })
            if (type == MeasurementType.CUSTOM) OutlinedTextField(custom, { custom = it }, label = { Text("Name (e.g. neck)") }, singleLine = true)
            NumberField(value, { value = it }, "Value", Modifier.fillMaxWidth().testTag("measurement_value"), suffix = unit)
            OutlinedButton({ pick = true }) { Text("Date: ${Fmt.day(date)}") }
            OutlinedTextField(note, { note = it }, label = { Text("Note") }, singleLine = true)
        }
    }, confirmButton = {
        TextButton(onClick = {
            Fmt.parse(value)?.let { v ->
                val canonical = when { type == MeasurementType.WEIGHT -> Fmt.weightToKg(v, prefs.weightUnit); type.isLength -> Fmt.lengthToCm(v, prefs.lengthUnit); else -> v }
                onSave(existing?.id, date, type, canonical, custom, note)
            }
        }, modifier = Modifier.testTag("measurement_save")) { Text("Save") }
    }, dismissButton = {
        Row { if (existing != null) TextButton(onClick = onDelete) { Text("Delete", color = MaterialTheme.colorScheme.error) }; TextButton(onClick = onDismiss) { Text("Cancel") } }
    })
    if (pick) DatePickerDialogField(date, { date = it }, { pick = false }, allowFuture = false)
}
