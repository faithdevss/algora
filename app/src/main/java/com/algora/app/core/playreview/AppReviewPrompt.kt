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
 *
 * Progress alone turned out to be a high bar — 15% of a track is ~21 DSA or ~54 AI topics, beyond
 * what the free tier opens — so two more moments qualify, each one where the learner has just seen
 * the app work for them: coming back on [ACTIVE_DAYS_THRESHOLD] different days, and finishing a timed
 * quiz with [QUIZ_SCORE_THRESHOLD_PERCENT]% or more. Whichever comes first asks; all share one
 * "already asked" record, so it is still once per install.
 */
object AppReviewPrompt {

    /** Percentage of the active track that must be complete before the prompt is offered. */
    const val PROGRESS_THRESHOLD_PERCENT = 15

    /** Distinct days with the app opened (within the retained history) that also qualify. */
    const val ACTIVE_DAYS_THRESHOLD = 5

    /** A timed quiz scored at or above this is a good moment to ask, right on the results. */
    const val QUIZ_SCORE_THRESHOLD_PERCENT = 80

    /**
     * Pure gates, kept separate from the Play call so the policy is testable.
     * `promptedDay` is null until the flow has been launched once.
     */
    fun shouldAsk(progressPercent: Int, promptedDay: Long?, activeDays: Int = 0): Boolean =
        promptedDay == null &&
            (progressPercent >= PROGRESS_THRESHOLD_PERCENT || activeDays >= ACTIVE_DAYS_THRESHOLD)

    fun shouldAskAfterQuiz(scorePercent: Int, promptedDay: Long?): Boolean =
        promptedDay == null && scorePercent >= QUIZ_SCORE_THRESHOLD_PERCENT

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
        activeDays: Int = 0,
    ) {
        if (!shouldAsk(progressPercent, promptedDay, activeDays)) return
        launch(activity, settings)
    }

    /** The results-screen moment: a timed quiz just went well. Same one-time record as [maybeAsk]. */
    suspend fun maybeAskAfterQuiz(
        activity: Activity,
        settings: SettingsRepository,
        scorePercent: Int,
        promptedDay: Long?,
    ) {
        if (!shouldAskAfterQuiz(scorePercent, promptedDay)) return
        launch(activity, settings)
    }

    private suspend fun launch(activity: Activity, settings: SettingsRepository) {
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
