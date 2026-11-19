package com.fdc.nutrition

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.fdc.nutrition.data.repository.ProfileRepository
import com.fdc.nutrition.data.seed.FoodSeeder
import com.fdc.nutrition.data.sync.ChangeTracker
import com.fdc.nutrition.work.ReminderScheduler
import com.fdc.nutrition.work.SyncScheduler
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class FdcApplication : Application(), Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var seeder: FoodSeeder
    @Inject lateinit var profile: ProfileRepository
    @Inject lateinit var changes: ChangeTracker
    @Inject lateinit var reminders: ReminderScheduler
    @Inject lateinit var sync: SyncScheduler

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        reminders.createChannel()
        appScope.launch {
            seeder.ensureSeeded()
            val prefs = profile.observePreferences().first()
            changes.enabled = prefs.syncEnabled
            reminders.rescheduleAll()
            if (prefs.syncEnabled) sync.schedulePeriodic()
        }
    }
}
