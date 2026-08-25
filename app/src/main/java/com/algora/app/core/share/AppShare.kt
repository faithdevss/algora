package com.algora.app.core.share

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import com.algora.app.BuildConfig

/**
 * "Tell a friend" — an install link and nothing else. No topic text ever rides along: the library is
 * what the paid tier sells, and a share that quoted a page would hand it out for free.
 */
object AppShare {

    val playStoreUrl: String = "https://play.google.com/store/apps/details?id=${BuildConfig.APPLICATION_ID}"

    // The Play listing's short description, kept in step with docs/store/listing.md so the message
    // says the same thing as the page it lands on.
    private const val PITCH = "Data structures, algorithms, ML, DL, NLP and RL — runnable labs, fully offline."

    val message: String = "Algora\n$PITCH\n\n$playStoreUrl"

    fun share(context: Context) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Algora")
            putExtra(Intent.EXTRA_TEXT, message)
        }
        // A chooser rather than a bare ACTION_SEND: a default the user set for some other share
        // should not silently swallow this one.
        val chooser = Intent.createChooser(send, "Share Algora")
        if (context !is Activity) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        // A device with no share target at all is possible; swallowing beats crashing on an invite.
        try {
            context.startActivity(chooser)
        } catch (_: ActivityNotFoundException) {
            // No handler — nothing useful to do.
        }
    }
}
