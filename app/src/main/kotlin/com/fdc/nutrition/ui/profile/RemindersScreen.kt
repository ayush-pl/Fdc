package com.fdc.nutrition.ui.profile

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.fdc.nutrition.data.db.entity.ReminderEntity
import com.fdc.nutrition.data.repository.ProfileRepository
import com.fdc.nutrition.ui.BaseViewModel
import com.fdc.nutrition.ui.components.*
import com.fdc.nutrition.ui.navigation.*
import com.fdc.nutrition.work.ReminderScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class RemindersViewModel @Inject constructor(private val profiles: ProfileRepository, private val scheduler: ReminderScheduler) : BaseViewModel() {
    val reminders = profiles.observeReminders().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    fun set(r: ReminderEntity, enabled: Boolean = r.enabled, hour: Int = r.hour, minute: Int = r.minute) = launchSafe {
        profiles.saveReminder(r.type, enabled, hour, minute)
        scheduler.schedule(r.type, enabled, hour, minute)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemindersScreen(nav: NavHostController, vm: RemindersViewModel = hiltViewModel()) {
    val list by vm.reminders.collectAsStateWithLifecycle()
    CollectMessages(vm.messages)
    var pending by remember { mutableStateOf<ReminderEntity?>(null) }
    var editing by remember { mutableStateOf<ReminderEntity?>(null) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> pending?.let { if (granted) vm.set(it, enabled = true) }; pending = null }
    Scaffold(topBar = { BackTopBar("Reminders", nav) }, contentWindowInsets = WindowInsets.statusBars) { pad ->
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { Text("Reminders are optional and off by default. Each fires at most once a day, and is skipped if you've already logged that meal or reached your water goal.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(list, key = { it.type.name }) { r ->
                AppCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(r.type.title, style = MaterialTheme.typography.titleSmall)
                            TextButton(onClick = { editing = r }, contentPadding = PaddingValues(0.dp)) { Text("%02d:%02d".format(r.hour, r.minute)) }
                        }
                        Switch(r.enabled, { on ->
                            if (on && Build.VERSION.SDK_INT >= 33) { pending = r; permission.launch(Manifest.permission.POST_NOTIFICATIONS) } else vm.set(r, enabled = on)
                        })
                    }
                }
            }
        }
    }
    editing?.let { r ->
        val state = rememberTimePickerState(r.hour, r.minute, true)
        AlertDialog(onDismissRequest = { editing = null }, confirmButton = { TextButton({ vm.set(r, hour = state.hour, minute = state.minute); editing = null }) { Text("OK") } },
            dismissButton = { TextButton({ editing = null }) { Text("Cancel") } }, text = { TimePicker(state) })
    }
}
