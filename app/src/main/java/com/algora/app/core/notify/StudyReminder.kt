package com.algora.app.core.notify

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
import com.algora.app.MainActivity
import com.algora.app.R

/**
 * The lapsed-learner reminder: one notification after a week away, then at most one a week.
 *
 * Deliberately conservative. A learning app that pings daily gets muted or uninstalled, and Play's
 * policy expects a notification like this to be dismissible, opt-out-able and not misrepresented as
 * something urgent. The user can switch it off in Settings, and on Android 13+ it never appears at
 * all without an explicit permission grant.
 */
object StudyReminder {

    /** Days of silence before the reminder is considered due. */
    const val INACTIVE_DAYS = 7L

    /** Minimum days between two reminders, so a long-lapsed user is nudged weekly, not daily. */
    const val REMINDER_COOLDOWN_DAYS = 7L

    const val CHANNEL_ID = "study_reminders"
    private const val NOTIFICATION_ID = 4201

    /**
     * The whole policy, as a pure function so it is testable without a device.
     *
     * `lastActiveDay` is null for an install that has never been opened — nothing to be reminded
     * about. `lastReminderDay` is 0 until the first reminder is posted.
     */
    fun shouldRemind(lastActiveDay: Long?, lastReminderDay: Long, today: Long): Boolean {
        if (lastActiveDay == null) return false
        if (today - lastActiveDay < INACTIVE_DAYS) return false
        // A reminder posted inside the cooldown still counts even if the user ignored it.
        if (lastReminderDay > 0L && today - lastReminderDay < REMINDER_COOLDOWN_DAYS) return false
        return true
    }

    /** Message scales with how long they have been away, so a nudge never reads as canned. */
    fun message(daysAway: Long): String = when {
        daysAway >= 30 -> "It's been a while. Your topics and flashcards are exactly where you left them."
        daysAway >= 14 -> "Two weeks off. A five-minute review session is enough to get the streak going again."
        else -> "A week since your last session — a few flashcards will bring it back fast."
    }

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Study reminders",
            // Low: it appears in the shade without sound or a heads-up interruption.
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Occasional nudge when you have not studied in a while."
        }
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    fun hasPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    /** Posts the reminder. No-op without the runtime permission, so the caller need not branch. */
    fun post(context: Context, daysAway: Long) {
        if (!hasPermission(context)) return
        ensureChannel(context)

        val open = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pending = PendingIntent.getActivity(
            context,
            0,
            open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Pick up where you left off")
            .setContentText(message(daysAway))
            .setStyle(NotificationCompat.BigTextStyle().bigText(message(daysAway)))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()

        // hasPermission() already gated this, but the grant can be revoked between the check and the
        // post, and lint cannot see through the helper either way.
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // Permission revoked mid-flight. Nothing to do — the reminder simply does not appear.
        }
    }
}
