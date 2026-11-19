package com.fdc.nutrition.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.fdc.nutrition.MainActivity
import com.fdc.nutrition.R
import com.fdc.nutrition.data.db.entity.ReminderType
import com.fdc.nutrition.data.repository.DiaryRepository
import com.fdc.nutrition.data.repository.PlanRepository
import com.fdc.nutrition.data.repository.ProfileRepository
import com.fdc.nutrition.data.repository.WaterRepository
import com.fdc.nutrition.domain.calc.effectiveOn
import com.fdc.nutrition.domain.model.Meal
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Schedules each enabled reminder as a unique one-off job at its next time of day; the worker
 * re-schedules itself for the following day. At most one notification per reminder per day, and
 * reminders are skipped when they're no longer needed (e.g. breakfast already logged).
 */
@Singleton
class ReminderScheduler @Inject constructor(@ApplicationContext private val context: Context, private val profiles: ProfileRepository) {
    private val wm get() = WorkManager.getInstance(context)

    fun createChannel() {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, context.getString(R.string.channel_reminders), NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = context.getString(R.string.channel_reminders_desc)
        })
    }

    suspend fun rescheduleAll() = profiles.reminders().forEach { schedule(it.type, it.enabled, it.hour, it.minute) }

    fun schedule(type: ReminderType, enabled: Boolean, hour: Int, minute: Int, now: LocalDateTime = LocalDateTime.now()) {
        val name = "reminder_${type.name}"
        if (!enabled) { wm.cancelUniqueWork(name); return }
        var next = now.toLocalDate().atTime(LocalTime.of(hour, minute))
        if (!next.isAfter(now)) next = next.plusDays(1)
        val req = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(Duration.between(now, next))
            .setInputData(workDataOf(ReminderWorker.KEY_TYPE to type.name))
            .addTag("reminder")
            .build()
        wm.enqueueUniqueWork(name, ExistingWorkPolicy.REPLACE, req)
    }

    companion object { const val CHANNEL = "reminders" }
}

@HiltWorker
class ReminderWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val diary: DiaryRepository,
    private val water: WaterRepository,
    private val plans: PlanRepository,
    private val profiles: ProfileRepository,
    private val scheduler: ReminderScheduler,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val type = runCatching { ReminderType.valueOf(inputData.getString(KEY_TYPE) ?: "") }.getOrNull() ?: return Result.success()
        val cfg = profiles.reminders().firstOrNull { it.type == type }
        if (cfg == null || !cfg.enabled) return Result.success()
        val today = LocalDate.now()
        val text = message(type, today)
        if (text != null) notify(type, text)
        scheduler.schedule(type, true, cfg.hour, cfg.minute, LocalDateTime.now().plusMinutes(1))
        return Result.success()
    }

    /** Returns the notification text, or null when the reminder isn't needed today. */
    private suspend fun message(type: ReminderType, today: LocalDate): String? {
        val entries = diary.range(today, today)
        return when (type) {
            ReminderType.BREAKFAST -> if (entries.any { it.mealId == Meal.BREAKFAST }) null else "Had breakfast? Log it in a few taps."
            ReminderType.LUNCH -> if (entries.any { it.mealId == Meal.LUNCH }) null else "Don't forget to log your lunch."
            ReminderType.DINNER -> if (entries.any { it.mealId == Meal.DINNER }) null else "Log your dinner to complete today's diary."
            ReminderType.WATER -> {
                val prefs = profiles.preferences()
                val target = profiles.targets().effectiveOn(today)?.waterMl ?: return null
                val drunk = WaterRepository.hydrationTotal(water.range(today, today), prefs.countBeveragesAsWater)
                if (drunk >= target * 0.6) null else "You've had ${drunk.toInt()} ml of water so far today."
            }
            ReminderType.MEAL_PLANNING -> if (plans.observeRange(today.plusDays(1), today.plusDays(1)).first().isNotEmpty()) null else "Plan tomorrow's meals?"
            ReminderType.DAILY_REVIEW -> if (entries.isEmpty()) "Nothing logged today yet." else "Review today's nutrition."
        }
    }

    private fun notify(type: ReminderType, text: String) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val intent = PendingIntent.getActivity(applicationContext, type.ordinal, Intent(applicationContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP), PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(applicationContext, ReminderScheduler.CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground).setContentTitle(type.title).setContentText(text)
            .setContentIntent(intent).setAutoCancel(true).setPriority(NotificationCompat.PRIORITY_DEFAULT).build()
        runCatching { NotificationManagerCompat.from(applicationContext).notify(1000 + type.ordinal, n) }
    }

    companion object { const val KEY_TYPE = "type" }
}
