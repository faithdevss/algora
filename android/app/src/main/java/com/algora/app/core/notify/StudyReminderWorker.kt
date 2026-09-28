package com.algora.app.core.notify

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.algora.app.core.data.settings.SettingsRepository
import com.algora.app.core.data.settings.settingsDataStore
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * Runs about once a day and posts the reminder only when the user has actually gone quiet.
 *
 * Checking daily rather than scheduling a one-shot seven days out keeps the logic in one place: the
 * decision is re-derived from stored state every run, so an app open, a settings toggle or a
 * reinstall cannot leave a stale alarm behind.
 */
class StudyReminderWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val settings = SettingsRepository(applicationContext.settingsDataStore)
        if (!settings.remindersEnabled.first()) return Result.success()

        val today = System.currentTimeMillis() / 86_400_000L
        val lastActive = settings.lastActiveDay.first()
        val lastReminder = settings.lastReminderDay.first()

        if (StudyReminder.shouldRemind(lastActive, lastReminder, today)) {
            StudyReminder.post(applicationContext, daysAway = today - (lastActive ?: today))
            // Recorded even if the post was dropped for want of permission — otherwise a device with
            // notifications denied would re-evaluate and "post" on every single run.
            settings.markReminderPosted(today)
        }
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "study_reminder"

        /**
         * Enqueues the daily check. KEEP means re-opening the app does not reset the timer, so the
         * schedule survives however often the user launches.
         */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<StudyReminderWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(1, TimeUnit.DAYS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
