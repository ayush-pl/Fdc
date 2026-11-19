package com.fdc.nutrition.ui.profile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.fdc.nutrition.data.repository.ProfileRepository
import com.fdc.nutrition.domain.model.*
import com.fdc.nutrition.ui.BaseViewModel
import com.fdc.nutrition.ui.components.*
import com.fdc.nutrition.ui.navigation.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(private val profiles: ProfileRepository) : BaseViewModel() {
    val prefs = profiles.observePreferences().stateIn(viewModelScope, SharingStarted.Eagerly, UserPreferences())
    val profile = profiles.observeProfile().stateIn(viewModelScope, SharingStarted.Eagerly, null)
    fun update(f: (UserPreferences) -> UserPreferences) = launchSafe { profiles.updatePreferences(f) }
    fun updateProfile(f: (UserProfile) -> UserProfile) = launchSafe { profile.value?.let { profiles.saveProfile(f(it)) } }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(nav: NavHostController, vm: SettingsViewModel = hiltViewModel()) {
    val p by vm.prefs.collectAsStateWithLifecycle()
    val profile by vm.profile.collectAsStateWithLifecycle()
    CollectMessages(vm.messages)
    var amounts by remember(p.waterQuickAmountsMl) { mutableStateOf(p.waterQuickAmountsMl.joinToString(", ")) }
    var usdaKey by remember(p.usdaApiKey) { mutableStateOf(p.usdaApiKey ?: "") }
    Scaffold(topBar = { BackTopBar("Settings", nav) }, contentWindowInsets = WindowInsets.statusBars) { pad ->
        Column(Modifier.padding(pad).imePadding().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            AppCard {
                Text("Appearance", style = MaterialTheme.typography.titleSmall)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    listOf(ThemeMode.LIGHT to "Light", ThemeMode.DARK to "Dark", ThemeMode.SYSTEM to "System").forEachIndexed { i, (m, l) ->
                        SegmentedButton(p.theme == m, { vm.update { it.copy(theme = m) } }, SegmentedButtonDefaults.itemShape(i, 3), modifier = Modifier.testTag("theme_${m.name}")) { Text(l) }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Use wallpaper colours (Android 12+)", Modifier.weight(1f)); Switch(p.dynamicColor, { v -> vm.update { it.copy(dynamicColor = v) } })
                }
            }
            AppCard {
                Text("Units", style = MaterialTheme.typography.titleSmall)
                Text("Calories are always shown in kcal.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DropdownField("Body weight", WeightUnit.entries, p.weightUnit, { it.symbol }, { u -> vm.update { it.copy(weightUnit = u) } }, Modifier.weight(1f))
                    DropdownField("Measurements", LengthUnit.entries, p.lengthUnit, { it.symbol }, { u -> vm.update { it.copy(lengthUnit = u) } }, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DropdownField("Food quantity", listOf(QuantityUnit.GRAM, QuantityUnit.OUNCE), p.defaultSolidUnit, { it.displayName }, { u -> vm.update { it.copy(defaultSolidUnit = u) } }, Modifier.weight(1f))
                    DropdownField("Liquids", listOf(QuantityUnit.MILLILITRE, QuantityUnit.FLUID_OUNCE), p.defaultLiquidUnit, { it.displayName }, { u -> vm.update { it.copy(defaultLiquidUnit = u) } }, Modifier.weight(1f))
                }
            }
            AppCard {
                Text("Hydration", style = MaterialTheme.typography.titleSmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text("Count tea, coffee, milk & juice as water"); Text("Off by default — only plain water counts toward your goal.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    Switch(p.countBeveragesAsWater, { v -> vm.update { it.copy(countBeveragesAsWater = v) } })
                }
                OutlinedTextField(amounts, { amounts = it }, label = { Text("Quick-add amounts (ml)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                TextButton(onClick = { val list = amounts.split(',').mapNotNull { it.trim().toIntOrNull() }.take(6); if (list.isNotEmpty()) vm.update { it.copy(waterQuickAmountsMl = list) } }) { Text("Save amounts") }
            }
            profile?.let { pr ->
                AppCard {
                    Text("Diet & allergies", style = MaterialTheme.typography.titleSmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { DietaryPreference.entries.forEach { d -> FilterChip(pr.dietaryPreference == d, { vm.updateProfile { it.copy(dietaryPreference = d) } }, { Text(d.displayName) }) } }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Allergens.ALL.forEach { a -> FilterChip(a in pr.allergies, { vm.updateProfile { it.copy(allergies = if (a in it.allergies) it.allergies - a else it.allergies + a) } }, { Text(Allergens.displayName(a)) }) }
                    }
                    Text("Foods are flagged when they conflict with your diet or allergies. Foods without allergen data are shown as \"unknown\", never as safe.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            AppCard {
                Text("Online food search", style = MaterialTheme.typography.titleSmall)
                Text("USDA FoodData Central search works with a shared demo key (rate-limited). A free personal key from api.data.gov raises the limit.", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(usdaKey, { usdaKey = it }, label = { Text("USDA API key (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                TextButton(onClick = { vm.update { it.copy(usdaApiKey = usdaKey.trim().ifBlank { null }) } }) { Text("Save key") }
            }
        }
    }
}
