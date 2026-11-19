package com.fdc.nutrition.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.fdc.nutrition.domain.calc.ServingOption
import com.fdc.nutrition.domain.model.Meal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@Composable
fun NumberField(
    value: String, onValueChange: (String) -> Unit, label: String, modifier: Modifier = Modifier, suffix: String? = null,
    error: String? = null, supporting: String? = null, imeAction: ImeAction = ImeAction.Next, enabled: Boolean = true,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { s -> if (s.length <= 9 && s.all { it.isDigit() || it == '.' || it == ',' }) onValueChange(s) },
        label = { Text(label) }, modifier = modifier, singleLine = true, enabled = enabled,
        suffix = suffix?.let { { Text(it) } }, isError = error != null,
        supportingText = (error ?: supporting)?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = imeAction),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> DropdownField(label: String, options: List<T>, selected: T?, display: (T) -> String, onSelect: (T) -> Unit, modifier: Modifier = Modifier, testTag: String? = null) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = selected?.let(display) ?: "", onValueChange = {}, readOnly = true, label = { Text(label) }, singleLine = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth().let { if (testTag != null) it.testTag(testTag) else it },
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { o -> DropdownMenuItem(text = { Text(display(o)) }, onClick = { onSelect(o); expanded = false }) }
        }
    }
}

@Composable
fun UnitDropdown(options: List<ServingOption>, selected: ServingOption?, onSelect: (ServingOption) -> Unit, modifier: Modifier = Modifier) =
    DropdownField("Unit", options, selected, { o -> if (o.isFoodSpecific) "${o.label} (${Fmt.num(o.amountPerUnit)} ${unitBase(o)})" else o.label }, onSelect, modifier, testTag = "unit_dropdown")

private fun unitBase(o: ServingOption) = if (o.label.contains("ml") || o.unit.name.contains("LITRE")) "ml" else "g"

@Composable
fun MealChips(meals: List<Meal>, selected: String?, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        meals.forEach { m -> FilterChip(selected = m.id == selected, onClick = { onSelect(m.id) }, label = { Text(m.name) }, modifier = Modifier.testTag("meal_${m.id}")) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerDialogField(initial: LocalDate, onPicked: (LocalDate) -> Unit, onDismiss: () -> Unit, allowFuture: Boolean = true) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = allowFuture || utcTimeMillis <= LocalDate.now().atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { state.selectedDateMillis?.let { onPicked(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }; onDismiss() }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) { DatePicker(state) }
}

@Composable
fun DateNavigator(date: LocalDate, onChange: (LocalDate) -> Unit, modifier: Modifier = Modifier, allowFuture: Boolean = true) {
    var picking by remember { mutableStateOf(false) }
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onChange(date.minusDays(1)) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous day") }
        TextButton(onClick = { picking = true }, modifier = Modifier.weight(1f)) {
            Icon(Icons.Outlined.CalendarMonth, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
            Text(Fmt.day(date), style = MaterialTheme.typography.titleMedium)
        }
        IconButton(onClick = { onChange(date.plusDays(1)) }, enabled = allowFuture || date.isBefore(LocalDate.now())) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next day") }
    }
    if (picking) DatePickerDialogField(date, onChange, { picking = false }, allowFuture)
}
