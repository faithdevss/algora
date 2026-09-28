package com.algora.app.core.notify

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.algora.app.core.data.settings.SettingsRepository
import com.algora.app.core.data.settings.settingsDataStore
import com.algora.app.feature.practice.daily.DAILY_DRILL_QUIZ_ID
import com.algora.app.feature.review.DAILY_NEW_CARD_LIMIT
import com.algora.app.feature.review.allReviewCards
import com.algora.app.feature.review.reviewDeck
import com.algora.app.feature.review.reviewCounts
import java.time.LocalTime
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first

/**
 * Runs once a day at the user's chosen time and asks [DailyReminder] whether there is anything to
 * say. Every input is re-read on each run, so a reminder can never describe stale work.
 */
class DailyReminderWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val settings = SettingsRepository(applicationContext.settingsDataStore)
        // Cheap exit before the card scan below, which walks every authored takeaway.
        if (!settings.dailyReminderEnabled.first()) return Result.success()

        val today = System.currentTimeMillis() / 86_400_000L
        val introducedToday = settings.newCardsIntroduced(today).first()
        val srs = settings.srs.first()
        val counts = reviewCounts(
            cards = reviewDeck(allReviewCards(), srs),
            srs = srs,
            today = today,
            newAllowance = (DAILY_NEW_CARD_LIMIT - introducedToday).coerceAtLeast(0),
        )
        // The drill's question step is the one whose completion is recorded rather than derived, so
        // it stands in for the whole drill here — the screen itself can work the other two out from
        // live state, but a worker has no business rebuilding the day's problem pick to find out.
        val drillAttempts = settings.quizAttempts.first()[DAILY_DRILL_QUIZ_ID].orEmpty()

        val state = DailyReminder.State(
            // Checked above; carried anyway so the policy is complete on its own terms and can be
            // tested as one thing rather than as a rule plus a caller that remembers to apply it.
            enabled = true,
            lastActiveDay = settings.lastActiveDay.first(),
            lastReminderDay = settings.lastDailyReminderDay.first(),
            cardsWaiting = counts.waiting,
            drillDoneToday = drillAttempts.any { it.day == today },
            streak = settings.streak.first(),
            today = today,
        )

        val nudge = DailyReminder.nudgeFor(state) ?: return Result.success()
        DailyReminder.post(applicationContext, nudge, state.streak, state.cardsWaiting)
        // Recorded even if the post was dropped for want of permission, for the same reason
        // StudyReminderWorker does it: otherwise a device with notifications denied re-evaluates
        // and "posts" on every run.
        settings.markDailyReminderPosted(today)
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "daily_reminder"
        private const val MINUTES_PER_DAY = 24 * 60

        /**
         * Minutes to wait for the next occurrence of `targetMinuteOfDay`. Pure so the wrap-around is
         * testable; a target that is exactly now waits a whole day rather than firing immediately,
         * which is what stops a re-schedule at the appointed minute from posting on the spot.
         */
        fun delayMinutes(nowMinuteOfDay: Int, targetMinuteOfDay: Int): Long {
            val diff = (targetMinuteOfDay - nowMinuteOfDay).mod(MINUTES_PER_DAY)
            return if (diff == 0) MINUTES_PER_DAY.toLong() else diff.toLong()
        }

        /**
         * Enqueues the daily run.
         *
         * `replaceExisting` is false from app launch — re-enqueuing on every open would keep moving
         * the alarm — and true from Settings, where the whole point is that a newly picked time
         * takes effect tonight rather than after the current schedule happens to lapse.
         */
        fun schedule(context: Context, minuteOfDay: Int, replaceExisting: Boolean = false) {
            val now = LocalTime.now()
            val delay = delayMinutes(now.hour * 60 + now.minute, minuteOfDay)
            val request = PeriodicWorkRequestBuilder<DailyReminderWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(delay, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                if (replaceExisting) {
                    ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE
                } else {
                    ExistingPeriodicWorkPolicy.KEEP
                },
                request,
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
