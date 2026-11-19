package com.fdc.nutrition.ui.profile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.fdc.nutrition.data.repository.ProfileRepository
import com.fdc.nutrition.domain.model.*
import com.fdc.nutrition.ui.BaseViewModel
import com.fdc.nutrition.ui.components.*
import com.fdc.nutrition.ui.navigation.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class ProfileEditViewModel @Inject constructor(private val profiles: ProfileRepository) : BaseViewModel() {
    var original: UserProfile? = null
    var prefs by mutableStateOf(UserPreferences())
    var name by mutableStateOf(""); var dob by mutableStateOf<LocalDate?>(null); var sex by mutableStateOf(Sex.UNSPECIFIED)
    var height by mutableStateOf(""); var weight by mutableStateOf(""); var targetWeight by mutableStateOf(""); var startWeight by mutableStateOf("")
    var activity by mutableStateOf(ActivityLevel.LIGHT); var goal by mutableStateOf(Goal.MAINTAIN); var weekly by mutableDoubleStateOf(0.5)
    var diet by mutableStateOf(DietaryPreference.NON_VEGETARIAN); val allergies = mutableStateListOf<String>(); var restrictions by mutableStateOf("")
    var errors by mutableStateOf<List<String>>(emptyList())

    init { viewModelScope.launch {
        prefs = profiles.preferences()
        profiles.profile()?.let { p -> original = p
            name = p.displayName; dob = p.dateOfBirth; sex = p.sex; height = p.heightCm?.let { Fmt.num(Fmt.lengthValue(it, prefs.lengthUnit)) } ?: ""
            weight = p.currentWeightKg?.let { Fmt.num(Fmt.weightValue(it, prefs.weightUnit)) } ?: ""; targetWeight = p.targetWeightKg?.let { Fmt.num(Fmt.weightValue(it, prefs.weightUnit)) } ?: ""
            startWeight = p.startWeightKg?.let { Fmt.num(Fmt.weightValue(it, prefs.weightUnit)) } ?: ""
            activity = p.activityLevel; goal = p.goal; weekly = p.weeklyChangeKg.takeIf { it > 0 } ?: 0.5; diet = p.dietaryPreference; allergies.addAll(p.allergies); restrictions = p.restrictions ?: ""
        }
    } }

    fun save(onDone: () -> Unit) {
        val p = UserProfile(name, dob, Fmt.parse(height)?.let { Fmt.lengthToCm(it, prefs.lengthUnit) }, sex, Fmt.parse(weight)?.let { Fmt.weightToKg(it, prefs.weightUnit) },
            Fmt.parse(startWeight)?.let { Fmt.weightToKg(it, prefs.weightUnit) }, Fmt.parse(targetWeight)?.let { Fmt.weightToKg(it, prefs.weightUnit) }, activity, goal,
            if (goal == Goal.LOSE || goal == Goal.GAIN) weekly else 0.0, diet, allergies.toSet(), restrictions.ifBlank { null }, original?.createdAt ?: Instant.now(), Instant.now())
        errors = profiles.validateProfile(p)
        if (errors.isEmpty()) launchSafe { profiles.saveProfile(p); message("Profile saved"); onDone() }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileEditScreen(nav: NavHostController, vm: ProfileEditViewModel = hiltViewModel()) {
    CollectMessages(vm.messages)
    var pick by remember { mutableStateOf(false) }
    Scaffold(topBar = { BackTopBar("Edit profile", nav) }, contentWindowInsets = WindowInsets.statusBars,
        bottomBar = { Button(onClick = { vm.save { nav.popBackStack() } }, modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp).height(52.dp)) { Text("Save") } }) { pad ->
        Column(Modifier.padding(pad).imePadding().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(vm.name, { vm.name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedButton({ pick = true }, Modifier.fillMaxWidth()) { Text("Date of birth: ${vm.dob?.let { Fmt.long(it) } ?: "not set"}") }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Sex.entries.forEach { s -> FilterChip(vm.sex == s, { vm.sex = s }, { Text(s.displayName) }) } }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField(vm.height, { vm.height = it }, "Height", Modifier.weight(1f), suffix = vm.prefs.lengthUnit.symbol)
                NumberField(vm.weight, { vm.weight = it }, "Current weight", Modifier.weight(1f), suffix = vm.prefs.weightUnit.symbol)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField(vm.startWeight, { vm.startWeight = it }, "Starting weight", Modifier.weight(1f), suffix = vm.prefs.weightUnit.symbol)
                NumberField(vm.targetWeight, { vm.targetWeight = it }, "Target weight", Modifier.weight(1f), suffix = vm.prefs.weightUnit.symbol)
            }
            DropdownField("Goal", Goal.entries, vm.goal, { it.displayName }, { vm.goal = it }, Modifier.fillMaxWidth())
            if (vm.goal == Goal.LOSE || vm.goal == Goal.GAIN) DropdownField("Weekly change", if (vm.goal == Goal.LOSE) listOf(0.25, 0.5, 0.75, 1.0) else listOf(0.25, 0.5), vm.weekly, { Fmt.weight(it, vm.prefs.weightUnit, 2) + " per week" }, { vm.weekly = it }, Modifier.fillMaxWidth())
            DropdownField("Activity", ActivityLevel.entries, vm.activity, { "${it.displayName} — ${it.description}" }, { vm.activity = it }, Modifier.fillMaxWidth())
            Text("Dietary preference", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { DietaryPreference.entries.forEach { d -> FilterChip(vm.diet == d, { vm.diet = d }, { Text(d.displayName) }) } }
            Text("Allergies", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Allergens.ALL.forEach { a -> FilterChip(a in vm.allergies, { if (a in vm.allergies) vm.allergies.remove(a) else vm.allergies.add(a) }, { Text(Allergens.displayName(a)) }) } }
            OutlinedTextField(vm.restrictions, { vm.restrictions = it }, label = { Text("Other restrictions (e.g. no onion/garlic)") }, modifier = Modifier.fillMaxWidth())
            Text("Changing your weight, goal or activity doesn't change targets automatically — recalculate them on the Targets screen.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            vm.errors.forEach { Text(it, color = MaterialTheme.colorScheme.error) }
            Spacer(Modifier.height(16.dp))
        }
    }
    if (pick) DatePickerDialogField(vm.dob ?: LocalDate.now().minusYears(30), { vm.dob = it }, { pick = false }, allowFuture = false)
}
