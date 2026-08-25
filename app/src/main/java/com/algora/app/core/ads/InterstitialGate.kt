package com.algora.app.core.ads

/**
 * Who may be shown a quiz-exit interstitial, and how often.
 *
 * Kept as a pure function with no Compose and no SDK so the whole policy is unit-testable — the same
 * split AppReviewPrompt.shouldAsk uses for the review flow.
 *
 * The numbers below are judgement, not measurement: there is no analytics backend, so they cannot be
 * A/B tested. They are deliberately conservative, because loosening them later is a much safer move
 * than tightening them after churn. The user who finishes quizzes is also the user closest to buying
 * the lifetime IAP, and an uninstall is worth less than any eCPM.
 */
data class InterstitialState(
    val isPremium: Boolean = false,
    /** Lifetime count of *completed full* quiz runs. Missed-only retries are not runs. */
    val quizzesFinished: Int = 0,
    val daysSinceInstall: Long = 0L,
    val shownToday: Int = 0,
    /** 0 when an interstitial has never been shown. */
    val lastShownAtMs: Long = 0L,
    val reviewPromptedToday: Boolean = false,
)

/** Completed quizzes that stay ad-free no matter what. The next one is the first that may show. */
const val INTERSTITIAL_FREE_QUIZZES = 3

/** A day-0 user has not decided the app is worth anything yet; ads before that buy an uninstall. */
const val INTERSTITIAL_MIN_INSTALL_DAYS = 2L

const val INTERSTITIAL_DAILY_CAP = 3

const val INTERSTITIAL_MIN_GAP_MS = 3L * 60 * 1000

fun shouldShowInterstitial(state: InterstitialState, nowMs: Long): Boolean = when {
    // The purchase's stated benefit. Non-negotiable.
    state.isPremium -> false

    // Both warm-ups must pass. A user who installs and then does nothing for a week still gets
    // INTERSTITIAL_FREE_QUIZZES untaxed finishes, and a user who binges on day one still gets two
    // calendar days.
    state.daysSinceInstall < INTERSTITIAL_MIN_INSTALL_DAYS -> false
    state.quizzesFinished <= INTERSTITIAL_FREE_QUIZZES -> false

    // AppReviewPrompt is also full-screen and is one-shot for the life of the install. Two modals
    // stacked on one exit reads as broken, and a poisoned review ask costs more than one impression.
    state.reviewPromptedToday -> false

    state.shownToday >= INTERSTITIAL_DAILY_CAP -> false

    // Stops a back-to-back quiz session from becoming an ad session. A negative difference means the
    // device clock moved backwards, which would otherwise suppress ads until it caught up — treat
    // the stored timestamp as stale instead.
    (nowMs - state.lastShownAtMs) in 0 until INTERSTITIAL_MIN_GAP_MS -> false

    else -> true
}
