package com.fdc.nutrition.ui.onboarding

import androidx.compose.runtime.*
import com.fdc.nutrition.data.repository.ProfileRepository
import com.fdc.nutrition.domain.model.*
import com.fdc.nutrition.ui.BaseViewModel
import com.fdc.nutrition.ui.components.Fmt
import com.fdc.nutrition.ui.profile.TargetFormState
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class SetupViewModel @Inject constructor(private val profiles: ProfileRepository) : BaseViewModel() {
    var step by mutableIntStateOf(0)
    var name by mutableStateOf("")
    var dob by mutableStateOf<LocalDate?>(LocalDate.now().minusYears(30))
    var sex by mutableStateOf(Sex.UNSPECIFIED)
    var height by mutableStateOf("")
    var weight by mutableStateOf("")
    var targetWeight by mutableStateOf("")
    var weightUnit by mutableStateOf(WeightUnit.KG)
    var lengthUnit by mutableStateOf(LengthUnit.CM)
    var activity by mutableStateOf(ActivityLevel.LIGHT)
    var goal by mutableStateOf(Goal.MAINTAIN)
    var weeklyChange by mutableDoubleStateOf(0.5)
    var diet by mutableStateOf(DietaryPreference.NON_VEGETARIAN)
    val allergies = mutableStateListOf<String>()
    val targets = TargetFormState()
    var estimateText by mutableStateOf<String?>(null)
    var warnings by mutableStateOf<List<String>>(emptyList())
    var errors by mutableStateOf<List<String>>(emptyList())
    var saving by mutableStateOf(false)

    fun buildProfile(): UserProfile = UserProfile(
        displayName = name.trim(), dateOfBirth = dob, heightCm = Fmt.parse(height)?.let { Fmt.lengthToCm(it, lengthUnit) }, sex = sex,
        currentWeightKg = Fmt.parse(weight)?.let { Fmt.weightToKg(it, weightUnit) }, startWeightKg = null,
        targetWeightKg = Fmt.parse(targetWeight)?.let { Fmt.weightToKg(it, weightUnit) }, activityLevel = activity, goal = goal,
        weeklyChangeKg = if (goal == Goal.LOSE || goal == Goal.GAIN) weeklyChange else 0.0, dietaryPreference = diet, allergies = allergies.toSet(),
        restrictions = null, createdAt = Instant.now(), updatedAt = Instant.now(),
    )

    fun next() {
        errors = emptyList()
        when (step) {
            0 -> {
                val p = buildProfile()
                val e = profiles.validateProfile(p).toMutableList()
                if (p.heightCm == null) e += "Please enter your height."
                if (p.currentWeightKg == null) e += "Please enter your current weight."
                if (e.isNotEmpty()) { errors = e; return }
            }
            1 -> {
                val p = buildProfile()
                val e = profiles.validateProfile(p)
                if (e.isNotEmpty()) { errors = e; return }
                recalculate()
            }
        }
        step++
    }

    fun back() { if (step > 0) step-- ; errors = emptyList() }

    fun recalculate() {
        val r = profiles.propose(buildProfile())
        r.onSuccess {
            targets.apply(it)
            estimateText = "Estimated resting energy (Mifflin–St Jeor): ${Fmt.kcal(it.estimate.bmrKcal)} kcal · with activity: ${Fmt.kcal(it.estimate.tdeeKcal)} kcal"
            warnings = it.estimate.warnings
        }.onFailure { errors = listOf(it.message ?: "Couldn't calculate targets") }
    }

    fun finish(onDone: () -> Unit) {
        if (saving) return
        val values = targets.values()
        val err = profiles.validateTargets(values, targets.waterMl(), targets.method, targets.split)
        if (err.isNotEmpty()) { errors = err; return }
        saving = true
        launchSafe {
            try {
                profiles.saveProfile(buildProfile())
                profiles.updatePreferences { it.copy(weightUnit = weightUnit, lengthUnit = lengthUnit) }
                profiles.saveTargets(values, targets.waterMl(), targets.method, targets.split.takeIf { targets.method == MacroMethod.PERCENTAGE },
                    if (goal == Goal.CUSTOM) TargetOrigin.CUSTOM else TargetOrigin.CALCULATED)
                onDone()
            } finally { saving = false }
        }
    }
}
