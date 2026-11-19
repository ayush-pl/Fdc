package com.fdc.nutrition.ui.onboarding

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Eco
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fdc.nutrition.domain.model.*
import com.fdc.nutrition.ui.components.*
import com.fdc.nutrition.ui.navigation.CollectMessages
import com.fdc.nutrition.ui.profile.TargetFormFields

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SetupScreen(onDone: () -> Unit, vm: SetupViewModel = hiltViewModel()) {
    CollectMessages(vm.messages)
    var pickDob by remember { mutableStateOf(false) }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Eco, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text("Step ${vm.step + 1} of 4", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
                LinearProgressIndicator(progress = { (vm.step + 1) / 4f }, modifier = Modifier.fillMaxWidth())
                when (vm.step) {
                    0 -> {
                        Text("Welcome! Let's set up your profile", style = MaterialTheme.typography.headlineSmall)
                        Text("Everything stays on your device. An account is optional.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedTextField(vm.name, { vm.name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("setup_name"))
                        OutlinedButton(onClick = { pickDob = true }, modifier = Modifier.fillMaxWidth()) { Text("Date of birth: ${vm.dob?.let { Fmt.long(it) } ?: "not set"}") }
                        Text("Sex (used only by the energy equation)", style = MaterialTheme.typography.titleSmall)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Sex.entries.forEach { s -> FilterChip(vm.sex == s, { vm.sex = s }, { Text(s.displayName) }) }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            NumberField(vm.height, { vm.height = it }, "Height", Modifier.weight(1f).testTag("setup_height"), suffix = vm.lengthUnit.symbol)
                            SingleChoiceSegmentedButtonRow { LengthUnit.entries.forEachIndexed { i, u -> SegmentedButton(vm.lengthUnit == u, { vm.lengthUnit = u }, SegmentedButtonDefaults.itemShape(i, 2)) { Text(u.symbol) } } }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            NumberField(vm.weight, { vm.weight = it }, "Current weight", Modifier.weight(1f).testTag("setup_weight"), suffix = vm.weightUnit.symbol)
                            SingleChoiceSegmentedButtonRow { WeightUnit.entries.forEachIndexed { i, u -> SegmentedButton(vm.weightUnit == u, { vm.weightUnit = u }, SegmentedButtonDefaults.itemShape(i, 2)) { Text(u.symbol) } } }
                        }
                    }
                    1 -> {
                        Text("Your goal", style = MaterialTheme.typography.headlineSmall)
                        Goal.entries.forEach { g ->
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(vm.goal == g, { vm.goal = g }, Modifier.testTag("goal_${g.name}")); Text(g.displayName)
                            }
                        }
                        if (vm.goal == Goal.LOSE || vm.goal == Goal.GAIN) {
                            Text("Weekly change", style = MaterialTheme.typography.titleSmall)
                            val rates = if (vm.goal == Goal.LOSE) listOf(0.25, 0.5, 0.75, 1.0) else listOf(0.25, 0.5)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { rates.forEach { r -> FilterChip(vm.weeklyChange == r, { vm.weeklyChange = r }, { Text(Fmt.weight(r, vm.weightUnit, 2) + "/wk") }) } }
                            if (vm.goal == Goal.LOSE && vm.weeklyChange > 0.75) Text("Faster loss is harder to sustain. Targets are capped at safe minimums.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        NumberField(vm.targetWeight, { vm.targetWeight = it }, "Target weight (optional)", Modifier.fillMaxWidth(), suffix = vm.weightUnit.symbol)
                        Text("Activity level", style = MaterialTheme.typography.titleSmall)
                        ActivityLevel.entries.forEach { a ->
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(vm.activity == a, { vm.activity = a })
                                Column { Text(a.displayName); Text(a.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            }
                        }
                    }
                    2 -> {
                        Text("Diet & allergies", style = MaterialTheme.typography.headlineSmall)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { DietaryPreference.entries.forEach { d -> FilterChip(vm.diet == d, { vm.diet = d }, { Text(d.displayName) }) } }
                        Text("Allergies", style = MaterialTheme.typography.titleSmall)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Allergens.ALL.forEach { a -> FilterChip(a in vm.allergies, { if (a in vm.allergies) vm.allergies.remove(a) else vm.allergies.add(a) }, { Text(Allergens.displayName(a)) }) }
                        }
                        Banner("Allergen information is only shown when the data source provides it. Foods without allergen data are marked \"unknown\", never \"allergen-free\".", BannerKind.INFO)
                    }
                    else -> {
                        Text("Your daily targets", style = MaterialTheme.typography.headlineSmall)
                        vm.estimateText?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        vm.warnings.forEach { Banner(it, BannerKind.WARNING) }
                        Banner("These are estimates from population equations, not medical advice. You can change any value now or later.", BannerKind.INFO)
                        TargetFormFields(vm.targets)
                    }
                }
                vm.errors.forEach { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
            Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (vm.step > 0) OutlinedButton(onClick = vm::back, modifier = Modifier.weight(1f)) { Text("Back") }
                if (vm.step < 3) Button(onClick = vm::next, modifier = Modifier.weight(1f).testTag("setup_next")) { Text("Continue") }
                else Button(onClick = { vm.finish(onDone) }, enabled = !vm.saving, modifier = Modifier.weight(1f).testTag("setup_finish")) { Text("Start tracking") }
            }
        }
    }
    if (pickDob) DatePickerDialogField(vm.dob ?: java.time.LocalDate.now().minusYears(30), { vm.dob = it }, { pickDob = false }, allowFuture = false)
}
