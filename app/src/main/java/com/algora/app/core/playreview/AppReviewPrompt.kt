package com.algora.app.core.playreview

import android.app.Activity
import android.content.Context
import com.algora.app.BuildConfig
import com.algora.app.core.data.settings.SettingsRepository
import com.google.android.play.core.ktx.launchReview
import com.google.android.play.core.ktx.requestReview
import com.google.android.play.core.review.ReviewManager
import com.google.android.play.core.review.ReviewManagerFactory
import com.google.android.play.core.review.testing.FakeReviewManager

/**
 * Play In-App Review, asked once a learner has actually invested in the app.
 *
 * Play's policy is explicit that the flow must not be triggered by a button or a nag: it is offered
 * at a point where the user has formed an opinion, and never blocks anything. Completing 15% of the
 * active track is that point — enough topics finished that the rating means something, early enough
 * that the user is still around to give one. The flow is also quota-limited by Play, so it may show
 * nothing at all; the app gets no signal either way and must not assume a rating happened.
 */
object AppReviewPrompt {

    /** Percentage of the active track that must be complete before the prompt is offered. */
    const val PROGRESS_THRESHOLD_PERCENT = 15

    /**
     * Pure gate, kept separate from the Play call so the policy is testable.
     * `promptedDay` is null until the flow has been launched once.
     */
    fun shouldAsk(progressPercent: Int, promptedDay: Long?): Boolean =
        promptedDay == null && progressPercent >= PROGRESS_THRESHOLD_PERCENT

    // Debug builds get Play's FakeReviewManager, matching how core/billing and core/ads swap in
    // fakes: the real flow needs a Play-installed build and would fail on every dev device.
    private fun manager(context: Context): ReviewManager =
        if (BuildConfig.DEBUG) FakeReviewManager(context) else ReviewManagerFactory.create(context)

    /**
     * Launches the review flow if the gate passes, then records that it was asked.
     *
     * The record is written only after the flow returns, so a failed request (offline, sideloaded
     * build, Play unavailable) leaves the prompt available for a later session rather than burning
     * the one chance. Any failure is swallowed — a rating prompt must never take the screen down.
     */
    suspend fun maybeAsk(
        activity: Activity,
        settings: SettingsRepository,
        progressPercent: Int,
        promptedDay: Long?,
    ) {
        if (!shouldAsk(progressPercent, promptedDay)) return
        try {
            val manager = manager(activity)
            val info = manager.requestReview()
            manager.launchReview(activity, info)
            settings.markReviewPrompted()
        } catch (_: Exception) {
            // Includes ReviewException and the not-installed-from-Play case. Nothing to recover.
        }
    }
}
