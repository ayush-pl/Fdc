package com.fdc.nutrition.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fdc.nutrition.data.repository.ProfileRepository
import com.fdc.nutrition.data.seed.FoodSeeder
import com.fdc.nutrition.data.seed.SeedState
import com.fdc.nutrition.domain.model.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AppState(
    val loaded: Boolean = false,
    val isSetUp: Boolean = false,
    val seed: SeedState = SeedState.Idle,
    val preferences: UserPreferences = UserPreferences(),
)

@HiltViewModel
class AppViewModel @Inject constructor(profile: ProfileRepository, private val seeder: FoodSeeder) : ViewModel() {
    val state: StateFlow<AppState> = combine(profile.observeIsSetUp(), seeder.state, profile.observePreferences()) { setUp, seed, prefs ->
        AppState(true, setUp, seed, prefs)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, AppState())

    fun retrySeed() { viewModelScope.launch { seeder.ensureSeeded() } }
}
