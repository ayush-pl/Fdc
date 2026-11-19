package com.fdc.nutrition.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.fdc.nutrition.data.sync.SyncEngine
import com.fdc.nutrition.data.sync.SyncOutcome
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncScheduler @Inject constructor(@ApplicationContext private val context: Context) {
    private val wm get() = WorkManager.getInstance(context)
    private val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    fun schedulePeriodic() {
        wm.enqueueUniquePeriodicWork("sync_periodic", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<SyncWorker>(6, TimeUnit.HOURS).setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES).build())
    }

    fun syncNow() {
        wm.enqueueUniqueWork("sync_now", ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<SyncWorker>().setConstraints(constraints).setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build())
    }

    fun cancel() { wm.cancelUniqueWork("sync_periodic"); wm.cancelUniqueWork("sync_now") }
}

@HiltWorker
class SyncWorker @AssistedInject constructor(@Assisted context: Context, @Assisted params: WorkerParameters, private val engine: SyncEngine) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = when (val r = engine.sync()) {
        is SyncOutcome.Success, SyncOutcome.NotConfigured -> Result.success()
        is SyncOutcome.Failed -> if (r.retryable && runAttemptCount < 5) Result.retry() else Result.failure()
    }
}
