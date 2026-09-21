package com.algora.app.core.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.algora.app.MainActivity
import com.algora.app.R
import com.algora.app.core.nav.DailyDrillRoute
import com.algora.app.core.nav.ReviewRoute

/**
 * The daily nudge, and the app's second notification — the one that protects a habit rather than
 * recovering a lost one.
 *
 * StudyReminder only speaks after a week of silence, by which point the streak is long gone and the
 * SM-2 queue has become a wall. This one fires at a time the user picks, on a day they have not
 * opened the app, and only while the habit is still recoverable: past [MAX_GAP_DAYS] days away it
 * shuts up and leaves the weekly lapsed reminder to it. The two can therefore never post on the
 * same day — one needs a gap of at most three days, the other at least seven.
 *
 * Every rule lives in [nudgeFor], which is pure, so the whole policy is testable without a device.
 */
object DailyReminder {

    /** Beyond this many days away the user is lapsed, not slipping; the weekly reminder takes over. */
    const val MAX_GAP_DAYS = 3L

    /** Below this a streak is not yet worth defending, and the claim would sound desperate. */
    const val STREAK_AT_RISK_MIN = 3

    const val CHANNEL_ID = "daily_reminders"
    private const val NOTIFICATION_ID = 4202

    /** What today's nudge is about. Null when there is nothing honest to say. */
    enum class Nudge { STREAK_AT_RISK, CARDS_WAITING, DRILL_WAITING }

    /**
     * Everything the decision depends on, read once by the worker.
     *
     * `lastActiveDay` is the last day the app was *opened* — which is also what the streak counts,
     * so the two agree about whether today is already safe.
     */
    data class State(
        val enabled: Boolean,
        val lastActiveDay: Long?,
        val lastReminderDay: Long,
        val cardsWaiting: Int,
        val drillDoneToday: Boolean,
        val streak: Int,
        val today: Long,
    )

    fun nudgeFor(state: State): Nudge? {
        if (!state.enabled) return null
        // Never opened: there is no habit yet to protect, and no work behind the notification.
        val lastActive = state.lastActiveDay ?: return null
        // Already here today. The streak is safe and they have seen whatever is waiting.
        if (lastActive >= state.today) return null
        if (state.today - lastActive > MAX_GAP_DAYS) return null
        // One a day, whatever the worker does — a retried or rescheduled run must not post twice.
        if (state.lastReminderDay >= state.today) return null

        return when {
            // The strongest true thing the app can say, so it wins when it is available.
            state.streak >= STREAK_AT_RISK_MIN -> Nudge.STREAK_AT_RISK
            state.cardsWaiting > 0 -> Nudge.CARDS_WAITING
            !state.drillDoneToday -> Nudge.DRILL_WAITING
            // Nothing due and the drill already done: silence is the correct notification.
            else -> null
        }
    }

    /** Where tapping it should land. A streak is defended fastest from the drill's three steps. */
    fun routeFor(nudge: Nudge): String = when (nudge) {
        Nudge.CARDS_WAITING -> ReviewRoute.ROUTE
        Nudge.STREAK_AT_RISK, Nudge.DRILL_WAITING -> DailyDrillRoute.ROUTE
    }

    /**
     * Title and body. The counts are in the text on purpose: "7 cards waiting" is a specific,
     * finishable amount of work, where "come back and study" is a guilt trip with no end to it.
     */
    fun title(nudge: Nudge, streak: Int, cardsWaiting: Int): String = when (nudge) {
        Nudge.STREAK_AT_RISK -> "Your $streak-day streak ends tonight"
        Nudge.CARDS_WAITING -> "$cardsWaiting card${if (cardsWaiting == 1) "" else "s"} waiting"
        Nudge.DRILL_WAITING -> "Today's drill is waiting"
    }

    fun message(nudge: Nudge, cardsWaiting: Int): String = when (nudge) {
        Nudge.STREAK_AT_RISK -> if (cardsWaiting > 0) {
            "$cardsWaiting card${if (cardsWaiting == 1) "" else "s"} to review — a few minutes keeps it alive."
        } else {
            "One drill is enough to keep it going."
        }
        Nudge.CARDS_WAITING -> "A short recall session now beats a backlog later."
        Nudge.DRILL_WAITING -> "One problem and a five-question set — about five minutes."
    }

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Daily study reminder",
            // Default rather than the lapsed reminder's Low: a nudge that must arrive before
            // midnight is worthless if it only ever appears silently in a shade nobody pulls down.
            // Its own channel, so a user can silence this one and keep the weekly one, or vice versa.
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "A nudge at your chosen time when cards are due or the drill is unfinished."
        }
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    /** Posts the nudge. No-op without the runtime permission, so the caller need not branch. */
    fun post(context: Context, nudge: Nudge, streak: Int, cardsWaiting: Int) {
        if (!StudyReminder.hasPermission(context)) return
        ensureChannel(context)

        val body = message(nudge, cardsWaiting)
        val open = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            // Straight to the work. Landing on Home and making the user find the queue is most of
            // the reason a reminder gets dismissed instead of followed.
            .putExtra(MainActivity.EXTRA_ROUTE, routeFor(nudge))
            .putExtra(MainActivity.EXTRA_REMINDER_KIND, nudge.name.lowercase())
        val pending = PendingIntent.getActivity(
            context,
            // A request code of its own, or this would overwrite the lapsed reminder's intent.
            NOTIFICATION_ID,
            open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title(nudge, streak, cardsWaiting))
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // Permission revoked between the check and the post. Nothing to do.
        }
    }
}
