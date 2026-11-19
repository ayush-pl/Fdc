package com.fdc.nutrition.ui.profile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.fdc.nutrition.data.repository.ProfileRepository
import com.fdc.nutrition.domain.model.Nutrient
import com.fdc.nutrition.ui.BaseViewModel
import com.fdc.nutrition.ui.components.*
import com.fdc.nutrition.ui.navigation.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class ProfileHubViewModel @Inject constructor(profiles: ProfileRepository) : BaseViewModel() {
    val profile = profiles.observeProfile().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val target = profiles.observeCurrentTarget().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val prefs = profiles.observePreferences().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), com.fdc.nutrition.domain.model.UserPreferences())
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(nav: NavHostController, vm: ProfileHubViewModel = hiltViewModel()) {
    val p by vm.profile.collectAsStateWithLifecycle()
    val t by vm.target.collectAsStateWithLifecycle()
    val prefs by vm.prefs.collectAsStateWithLifecycle()
    Scaffold(topBar = { TopAppBar(title = { Text("Profile") }, colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)) }, contentWindowInsets = WindowInsets.statusBars) { pad ->
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                AppCard(onClick = { nav.navigate(ProfileEditRoute) }) {
                    Text(p?.displayName ?: "Set up your profile", style = MaterialTheme.typography.titleLarge)
                    Text(listOfNotNull(p?.goal?.displayName, p?.dietaryPreference?.displayName, p?.currentWeightKg?.let { Fmt.weight(it, prefs.weightUnit) }).joinToString(" · "),
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item {
                AppCard(onClick = { nav.navigate(TargetsRoute) }) {
                    Text("Daily targets", style = MaterialTheme.typography.titleSmall)
                    Text(t?.let { "${Fmt.kcal(it.calories)} kcal · P ${Fmt.grams(it[Nutrient.PROTEIN])} g · C ${Fmt.grams(it[Nutrient.CARBOHYDRATE])} g · F ${Fmt.grams(it[Nutrient.FAT])} g · water ${Fmt.litres(it.waterMl)}" } ?: "Not set",
                        style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("profile_targets"))
                }
            }
            item { NavRow("Body weight & measurements", Icons.Outlined.MonitorWeight) { nav.navigate(BodyRoute) } }
            item { NavRow("Water", Icons.Outlined.WaterDrop) { nav.navigate(WaterRoute()) } }
            item { NavRow("Recipes", Icons.Outlined.MenuBook) { nav.navigate(RecipesRoute) } }
            item { NavRow("Meal planner", Icons.Outlined.EventNote) { nav.navigate(PlannerRoute()) } }
            item { NavRow("Saved meals", Icons.Outlined.Bookmarks) { nav.navigate(SavedMealsRoute) } }
            item { NavRow("History calendar", Icons.Outlined.CalendarMonth) { nav.navigate(CalendarRoute) } }
            item { NavRow("Appearance, units & diet", Icons.Outlined.Tune, "settings_row") { nav.navigate(SettingsRoute) } }
            item { NavRow("Reminders", Icons.Outlined.Notifications) { nav.navigate(RemindersRoute) } }
            item { NavRow("Backup, export & data", Icons.Outlined.Storage) { nav.navigate(DataRoute) } }
            item { NavRow("Account & cloud sync (optional)", Icons.Outlined.Cloud) { nav.navigate(SyncRoute) } }
            item { NavRow("About & data sources", Icons.Outlined.Info) { nav.navigate(AboutRoute) } }
        }
    }
}

@Composable
private fun NavRow(label: String, icon: ImageVector, tag: String? = null, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth().let { if (tag != null) it.testTag(tag) else it }) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(14.dp))
            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
