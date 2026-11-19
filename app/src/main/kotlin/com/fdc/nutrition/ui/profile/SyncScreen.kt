package com.fdc.nutrition.ui.profile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.fdc.nutrition.data.repository.ProfileRepository
import com.fdc.nutrition.data.sync.SyncEngine
import com.fdc.nutrition.data.sync.SyncOutcome
import com.fdc.nutrition.data.sync.SyncStatusInfo
import com.fdc.nutrition.domain.model.UserPreferences
import com.fdc.nutrition.ui.BaseViewModel
import com.fdc.nutrition.ui.components.*
import com.fdc.nutrition.ui.navigation.*
import com.fdc.nutrition.work.SyncScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class SyncViewModel @Inject constructor(private val engine: SyncEngine, private val profiles: ProfileRepository, private val scheduler: SyncScheduler) : BaseViewModel() {
    val status = engine.observeStatus().stateIn(viewModelScope, SharingStarted.Eagerly, SyncStatusInfo(null, null, null, 0, emptyList()))
    val prefs = profiles.observePreferences().stateIn(viewModelScope, SharingStarted.Eagerly, UserPreferences())
    var busy by mutableStateOf(false)
    private fun work(b: suspend () -> Unit) = launchSafe { busy = true; try { b() } finally { busy = false } }

    fun saveProject(url: String, key: String) = work {
        val u = url.trim().trimEnd('/')
        if (u.isNotBlank() && !u.startsWith("https://")) error("The project URL must start with https://")
        profiles.updatePreferences { it.copy(supabaseUrl = u.ifBlank { null }, supabaseAnonKey = key.trim().ifBlank { null }) }; message("Project settings saved")
    }
    fun signIn(email: String, password: String, create: Boolean) = work {
        if (!email.contains('@')) error("Enter a valid email"); if (password.length < 6) error("Password must be at least 6 characters")
        message(engine.signIn(email, password, create)); scheduler.schedulePeriodic(); scheduler.syncNow()
    }
    fun syncNow() = work {
        when (val r = engine.sync()) {
            is SyncOutcome.Success -> message("Synced: ${r.pulled} received, ${r.pushed} sent${if (r.conflicts > 0) ", ${r.conflicts} conflicts" else ""}")
            is SyncOutcome.Failed -> message("Sync failed: ${r.message}")
            SyncOutcome.NotConfigured -> message("Configure your Supabase project first")
        }
    }
    fun retry() = work { engine.retryFailed(); syncNow() }
    fun signOut() = work { engine.signOut(); scheduler.cancel(); message("Signed out. Your data stays on this device.") }
    fun deleteAccount() = work { engine.deleteAccount(); scheduler.cancel(); message("Cloud account and cloud data deleted. Local data is unchanged.") }
    fun keepLocal(id: Long) = work { engine.keepLocal(id) }
    fun keepRemote(id: Long) = work { engine.keepRemote(id) }
}

@Composable
fun SyncScreen(nav: NavHostController, vm: SyncViewModel = hiltViewModel()) {
    val s by vm.status.collectAsStateWithLifecycle()
    val prefs by vm.prefs.collectAsStateWithLifecycle()
    CollectMessages(vm.messages)
    var url by remember(prefs.supabaseUrl) { mutableStateOf(prefs.supabaseUrl ?: "") }
    var key by remember(prefs.supabaseAnonKey) { mutableStateOf(prefs.supabaseAnonKey ?: "") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf(false) }
    Scaffold(topBar = { BackTopBar("Account & sync", nav) }, contentWindowInsets = WindowInsets.statusBars) { pad ->
        Column(Modifier.padding(pad).imePadding().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (vm.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            Banner("Optional. The app works fully offline without an account. Sync backs up your data to your own Supabase project and keeps devices in step.", BannerKind.INFO)
            AppCard {
                Text("Supabase project", style = MaterialTheme.typography.titleSmall)
                OutlinedTextField(url, { url = it }, label = { Text("Project URL (https://xyz.supabase.co)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(key, { key = it }, label = { Text("Anon (public) key") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                TextButton(onClick = { vm.saveProject(url, key) }) { Text("Save") }
                Text("Set up the database with docs/SUPABASE_SETUP.md from the project repository.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (s.signedInEmail == null || !prefs.syncEnabled) AppCard {
                Text("Sign in", style = MaterialTheme.typography.titleSmall)
                OutlinedTextField(email, { email = it }, label = { Text("Email") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(password, { password = it }, label = { Text("Password") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { vm.signIn(email, password, false) }, enabled = !vm.busy && prefs.supabaseUrl != null, modifier = Modifier.weight(1f)) { Text("Sign in") }
                    OutlinedButton(onClick = { vm.signIn(email, password, true) }, enabled = !vm.busy && prefs.supabaseUrl != null, modifier = Modifier.weight(1f)) { Text("Create account") }
                }
            } else AppCard {
                Text("Signed in as ${s.signedInEmail}", style = MaterialTheme.typography.titleSmall)
                Text("Last sync: ${s.lastSyncAt?.take(16)?.replace('T', ' ') ?: "never"} · ${s.pending} changes waiting", style = MaterialTheme.typography.bodySmall)
                s.lastError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = vm::syncNow, enabled = !vm.busy, modifier = Modifier.weight(1f)) { Text("Sync now") }
                    OutlinedButton(onClick = vm::retry, enabled = !vm.busy, modifier = Modifier.weight(1f)) { Text("Retry failed") }
                }
                TextButton(onClick = vm::signOut) { Text("Sign out") }
                TextButton(onClick = { confirmDelete = true }) { Text("Delete cloud account", color = MaterialTheme.colorScheme.error) }
            }
            if (s.conflicts.isNotEmpty()) AppCard {
                Text("Conflicts (${s.conflicts.size})", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.error)
                Text("These records were changed both here and on another device. Nothing has been overwritten — choose which version to keep.", style = MaterialTheme.typography.bodySmall)
                s.conflicts.forEach { c ->
                    HorizontalDivider()
                    Text("${c.entityType.replace('_', ' ')} · ${c.entityId.take(18)}", style = MaterialTheme.typography.labelLarge)
                    Text("This device: ${c.localPayload?.take(140) ?: "deleted"}", style = MaterialTheme.typography.bodySmall)
                    Text("Cloud: ${c.remotePayload?.take(140) ?: "deleted"}", style = MaterialTheme.typography.bodySmall)
                    Row { TextButton({ vm.keepLocal(c.id) }) { Text("Keep this device's") }; TextButton({ vm.keepRemote(c.id) }) { Text("Use cloud version") } }
                }
            }
        }
    }
    if (confirmDelete) ConfirmDialog("Delete cloud account?", "Your cloud copy and account will be permanently deleted. Data on this device is kept.", "Delete", true, vm::deleteAccount, { confirmDelete = false })
}
