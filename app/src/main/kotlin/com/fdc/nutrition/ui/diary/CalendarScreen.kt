package com.fdc.nutrition.ui.diary

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.fdc.nutrition.data.repository.DiaryRepository
import com.fdc.nutrition.data.repository.PlanRepository
import com.fdc.nutrition.data.repository.ProfileRepository
import com.fdc.nutrition.domain.calc.effectiveOn
import com.fdc.nutrition.domain.model.DayLogStatus
import com.fdc.nutrition.ui.BaseViewModel
import com.fdc.nutrition.ui.components.*
import com.fdc.nutrition.ui.navigation.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale
import javax.inject.Inject

data class CalendarDay(val date: LocalDate, val status: DayLogStatus, val kcal: Double?, val planned: Boolean)
data class CalendarState(val month: YearMonth = YearMonth.now(), val days: Map<LocalDate, CalendarDay> = emptyMap(), val target: Double? = null)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CalendarViewModel @Inject constructor(diary: DiaryRepository, plans: PlanRepository, profiles: ProfileRepository) : BaseViewModel() {
    val month = MutableStateFlow(YearMonth.now())
    val state = month.flatMapLatest { m ->
        val from = m.atDay(1); val to = m.atEndOfMonth()
        combine(diary.observeDayStats(from, to), diary.observeDayLogs(from, to), plans.observePlannedDates(from, to), profiles.observeTargets()) { stats, logs, planned, targets ->
            val s = stats.associateBy { it.date }; val l = logs.associateBy { it.date }
            val days = (0 until m.lengthOfMonth()).map { from.plusDays(it.toLong()) }.associateWith { d ->
                val st = s[d]
                val status = when { l[d]?.markedComplete == true -> DayLogStatus.COMPLETE; st == null -> DayLogStatus.EMPTY; st.meals >= 3 -> DayLogStatus.COMPLETE; else -> DayLogStatus.PARTIAL }
                CalendarDay(d, status, st?.kcal, d in planned)
            }
            CalendarState(m, days, targets.effectiveOn(to)?.calories)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CalendarState())
}

@Composable
fun CalendarScreen(nav: NavHostController, vm: CalendarViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val month by vm.month.collectAsStateWithLifecycle()
    Scaffold(topBar = { BackTopBar("History", nav) }, contentWindowInsets = WindowInsets.statusBars) { pad ->
        Column(Modifier.padding(pad).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { vm.month.value = month.minusMonths(1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous month") }
                Text("${month.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${month.year}", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                IconButton(onClick = { vm.month.value = month.plusMonths(1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next month") }
            }
            Row { DayOfWeek.entries.forEach { Text(it.getDisplayName(TextStyle.NARROW, Locale.getDefault()), Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center) } }
            val first = month.atDay(1)
            val offset = first.dayOfWeek.value - 1
            val cells = offset + month.lengthOfMonth()
            (0 until (cells + 6) / 7).forEach { week ->
                Row {
                    (0..6).forEach { col ->
                        val idx = week * 7 + col - offset
                        Box(Modifier.weight(1f).aspectRatio(1f).padding(2.dp), contentAlignment = Alignment.Center) {
                            if (idx in 0 until month.lengthOfMonth()) {
                                val d = first.plusDays(idx.toLong())
                                s.days[d]?.let { DayCell(it, d == LocalDate.now()) { nav.navigate(DiaryRoute(d.toArg())) } }
                            }
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Legend(MaterialTheme.colorScheme.primary, "Complete"); Legend(MaterialTheme.colorScheme.primary.copy(alpha = 0.35f), "Partial")
                Legend(MaterialTheme.colorScheme.surfaceVariant, "No entries"); Legend(MaterialTheme.colorScheme.tertiary, "Planned •")
            }
            val logged = s.days.values.filter { it.status != DayLogStatus.EMPTY }
            AppCard {
                Text("This month", style = MaterialTheme.typography.titleSmall)
                Text("${logged.size} days logged · ${logged.count { it.status == DayLogStatus.COMPLETE }} complete", style = MaterialTheme.typography.bodyMedium)
                logged.mapNotNull { it.kcal }.takeIf { it.isNotEmpty() }?.let { Text("Average on logged days: ${Fmt.kcal(it.average())} kcal${s.target?.let { t -> " (target ${Fmt.kcal(t)})" } ?: ""}", style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}

@Composable
private fun DayCell(d: CalendarDay, today: Boolean, onClick: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    val (bg, fg) = when (d.status) {
        DayLogStatus.COMPLETE -> primary to MaterialTheme.colorScheme.onPrimary
        DayLogStatus.PARTIAL -> primary.copy(alpha = 0.35f) to MaterialTheme.colorScheme.onSurface
        DayLogStatus.EMPTY -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(onClick = onClick, shape = CircleShape, color = bg, contentColor = fg, border = if (today) BorderStroke(2.dp, MaterialTheme.colorScheme.tertiary) else null,
        modifier = Modifier.fillMaxSize().semantics { contentDescription = "${d.date}: ${d.status.name.lowercase()}${if (d.planned) ", planned" else ""}" }) {
        Box(contentAlignment = Alignment.Center) {
            Text("${d.date.dayOfMonth}", style = MaterialTheme.typography.bodyMedium, fontWeight = if (today) FontWeight.Bold else FontWeight.Normal)
            if (d.planned) Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp).size(5.dp).clip(CircleShape).let { it }) { Surface(color = MaterialTheme.colorScheme.tertiary, modifier = Modifier.fillMaxSize()) {} }
        }
    }
}

@Composable
private fun Legend(c: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(shape = CircleShape, color = c, modifier = Modifier.size(10.dp)) {}
        Spacer(Modifier.width(4.dp)); Text(label, style = MaterialTheme.typography.labelSmall)
    }
}
