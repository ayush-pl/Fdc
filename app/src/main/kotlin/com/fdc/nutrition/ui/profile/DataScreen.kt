package com.fdc.nutrition.ui.profile

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.navigation.NavHostController
import com.fdc.nutrition.BuildConfig
import com.fdc.nutrition.data.backup.*
import com.fdc.nutrition.data.seed.FoodSeeder
import com.fdc.nutrition.ui.BaseViewModel
import com.fdc.nutrition.ui.components.*
import com.fdc.nutrition.ui.navigation.*
import com.fdc.nutrition.work.ReminderScheduler
import com.fdc.nutrition.work.SyncScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class DataViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val backup: BackupRepository,
    private val csv: CsvExporter,
    private val seeder: FoodSeeder,
    private val reminders: ReminderScheduler,
    private val sync: SyncScheduler,
) : BaseViewModel() {
    var busy by mutableStateOf(false)
    var preview by mutableStateOf<RestorePreview?>(null)
    var result by mutableStateOf<String?>(null)
    private var pendingText: String? = null

    private fun work(block: suspend () -> Unit) = launchSafe { busy = true; try { block() } finally { busy = false } }

    fun exportJson(uri: Uri) = work {
        val text = backup.export(BuildConfig.VERSION_NAME)
        withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) } ?: error("Couldn't open file") }
        message("Backup saved")
    }

    fun exportCsv(uri: Uri) = work {
        withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri)?.use { csv.writeZip(it) } ?: error("Couldn't open file") }
        message("CSV export saved")
    }

    fun analyse(uri: Uri) = work {
        val text = withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } } ?: error("Couldn't read file")
        try { preview = backup.preview(text); pendingText = text } catch (e: BackupFormatException) { result = e.message }
    }

    fun restore(strategy: ConflictStrategy) = work {
        val text = pendingText ?: return@work
        val r = backup.restore(text, strategy)
        preview = null; pendingText = null
        result = "Restore complete: ${r.inserted} added, ${r.replaced} replaced, ${r.keptExisting} conflicts kept as they were, ${r.unchanged} already present." +
            if (r.invalid.isNotEmpty()) " ${r.invalid.size} invalid records skipped." else ""
    }

    fun cancelRestore() { preview = null; pendingText = null }

    fun deleteAll(onDone: () -> Unit) = work {
        backup.deleteAllUserData()
        seeder.seedDefaults()
        sync.cancel(); reminders.rescheduleAll()
        message("All personal data deleted"); onDone()
    }
}

@Composable
fun DataScreen(nav: NavHostController, vm: DataViewModel = hiltViewModel()) {
    CollectMessages(vm.messages)
    val today = LocalDate.now()
    val saveJson = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { it?.let(vm::exportJson) }
    val saveCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { it?.let(vm::exportCsv) }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(vm::analyse) }
    var confirmDelete by remember { mutableIntStateOf(0) }
    var replace by remember { mutableStateOf(false) }
    Scaffold(topBar = { BackTopBar("Backup & data", nav) }, contentWindowInsets = WindowInsets.statusBars) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (vm.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            vm.result?.let { Banner(it, BannerKind.INFO) }
            AppCard {
                Text("Export", style = MaterialTheme.typography.titleSmall)
                Text("CSV (zip): food diary with all nutrients, daily totals with data coverage, recipes, meal plans, water and body measurements. Unknown values are left blank.", style = MaterialTheme.typography.bodySmall)
                Button(onClick = { saveCsv.launch("fdc-nutrition-export-$today.zip") }, enabled = !vm.busy, modifier = Modifier.fillMaxWidth().testTag("export_csv")) { Text("Export CSV") }
            }
            AppCard {
                Text("Backup & restore", style = MaterialTheme.typography.titleSmall)
                Text("A JSON backup contains your profile, targets, diary, custom foods, recipes, plans, water and body data.", style = MaterialTheme.typography.bodySmall)
                Button(onClick = { saveJson.launch("fdc-nutrition-backup-$today.json") }, enabled = !vm.busy, modifier = Modifier.fillMaxWidth().testTag("backup_json")) { Text("Create backup") }
                OutlinedButton(onClick = { open.launch(arrayOf("application/json", "text/plain", "*/*")) }, enabled = !vm.busy, modifier = Modifier.fillMaxWidth()) { Text("Restore from backup…") }
                Text("Restoring merges: new records are added and your existing records are never overwritten unless you choose to.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            AppCard {
                Text("Delete data", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.error)
                Text("Removes your profile, diary, custom foods, recipes, plans, water and body records from this device. The built-in food database stays.", style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = { confirmDelete = 1 }, colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error), modifier = Modifier.fillMaxWidth()) { Text("Delete all my data") }
            }
        }
    }
    vm.preview?.let { p ->
        AlertDialog(onDismissRequest = vm::cancelRestore, title = { Text("Restore backup?") }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Backup from ${p.exportedAt.take(10)} · ${p.total} records")
                Text("• ${p.new} new — will be added"); Text("• ${p.identical} already on this device — skipped")
                Text("• ${p.conflicting} differ from your current data")
                if (p.invalid.isNotEmpty()) Text("• ${p.invalid.size} invalid — will be skipped", color = MaterialTheme.colorScheme.error)
                if (p.conflicting > 0) Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(replace, { replace = it }); Text("Replace my current versions of the ${p.conflicting} differing records with the backup's")
                }
            }
        }, confirmButton = { TextButton(onClick = { vm.restore(if (replace) ConflictStrategy.REPLACE_WITH_BACKUP else ConflictStrategy.KEEP_EXISTING) }) { Text("Restore") } },
            dismissButton = { TextButton(onClick = vm::cancelRestore) { Text("Cancel") } })
    }
    if (confirmDelete == 1) ConfirmDialog("Delete all personal data?", "Consider creating a backup first. This can't be undone.", "Continue", true, { confirmDelete = 2 }, { if (confirmDelete == 1) confirmDelete = 0 })
    if (confirmDelete == 2) ConfirmDialog("Are you absolutely sure?", "Everything you've logged will be permanently removed from this device.", "Delete everything", true,
        { vm.deleteAll { nav.navigate(SetupRoute) { popUpTo(0) { inclusive = true } } } }, { confirmDelete = 0 })
}
