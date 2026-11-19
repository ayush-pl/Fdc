package com.fdc.nutrition.ui.profile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.fdc.nutrition.data.repository.ProfileRepository
import com.fdc.nutrition.domain.model.MacroMethod
import com.fdc.nutrition.domain.model.TargetOrigin
import com.fdc.nutrition.ui.BaseViewModel
import com.fdc.nutrition.ui.components.*
import com.fdc.nutrition.ui.navigation.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TargetsViewModel @Inject constructor(private val profiles: ProfileRepository) : BaseViewModel() {
    val form = TargetFormState()
    var info by mutableStateOf<String?>(null)
    var warnings by mutableStateOf<List<String>>(emptyList())
    var errors by mutableStateOf<List<String>>(emptyList())
    var origin = TargetOrigin.CUSTOM
    init { viewModelScope.launch { profiles.observeCurrentTarget().first()?.let { form.load(it); origin = it.origin } ?: recalculate() } }

    fun recalculate() = launchSafe {
        val p = profiles.profile() ?: run { message("Complete your profile first"); return@launchSafe }
        profiles.propose(p).onSuccess {
            form.apply(it); origin = TargetOrigin.CALCULATED; warnings = it.estimate.warnings
            info = "BMR ${Fmt.kcal(it.estimate.bmrKcal)} kcal × activity ${p.activityLevel.factor} = ${Fmt.kcal(it.estimate.tdeeKcal)} kcal; goal adjustment ${Fmt.kcal(it.estimate.adjustmentKcal)} kcal/day."
        }.onFailure { message(it.message ?: "Couldn't calculate") }
    }

    fun save(onDone: () -> Unit) {
        val v = form.values()
        errors = profiles.validateTargets(v, form.waterMl(), form.method, form.split)
        if (errors.isNotEmpty()) return
        launchSafe {
            profiles.saveTargets(v, form.waterMl(), form.method, form.split.takeIf { form.method == MacroMethod.PERCENTAGE }, origin)
            message("Targets saved — they apply from today; past days keep their previous targets"); onDone()
        }
    }
}

@Composable
fun TargetsScreen(nav: NavHostController, vm: TargetsViewModel = hiltViewModel()) {
    CollectMessages(vm.messages)
    Scaffold(topBar = { BackTopBar("Daily targets", nav) }, contentWindowInsets = WindowInsets.statusBars,
        bottomBar = { Button(onClick = { vm.save { nav.popBackStack() } }, modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp).height(52.dp).testTag("save_targets")) { Text("Save targets") } }) { pad ->
        Column(Modifier.padding(pad).imePadding().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = vm::recalculate, modifier = Modifier.fillMaxWidth()) { Text("Recalculate from my profile") }
            vm.info?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            vm.warnings.forEach { Banner(it, BannerKind.WARNING) }
            Banner("Estimates use the Mifflin–St Jeor equation and standard activity factors. They're a starting point, not medical advice — every value can be changed.", BannerKind.INFO)
            TargetFormFields(vm.form, showMicros = true)
            vm.errors.forEach { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            Spacer(Modifier.height(16.dp))
        }
    }
}
