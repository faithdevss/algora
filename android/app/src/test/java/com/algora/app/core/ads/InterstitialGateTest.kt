package com.algora.app.core.ads

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// The quiz-exit interstitial is the app's only non-opt-in ad, so every rule that keeps it rare is
// worth pinning. The gate is pure, so all of it is testable without Compose or the AdMob SDK.
class InterstitialGateTest {

    private val now = 1_700_000_000_000L

    // A user who clears every rule: past both warm-ups, nothing shown today, no review prompt.
    private val eligible = InterstitialState(
        isPremium = false,
        quizzesFinished = INTERSTITIAL_FREE_QUIZZES + 1,
        daysSinceInstall = INTERSTITIAL_MIN_INSTALL_DAYS,
        shownToday = 0,
        lastShownAtMs = 0L,
        reviewPromptedToday = false,
    )

    @Test
    fun a_warmed_up_free_user_is_shown_one() {
        assertTrue(shouldShowInterstitial(eligible, now))
    }

    @Test
    fun premium_is_never_shown_one() {
        assertFalse(shouldShowInterstitial(eligible.copy(isPremium = true), now))
    }

    @Test
    fun the_first_free_quizzes_are_untaxed_and_the_next_is_not() {
        assertFalse(shouldShowInterstitial(eligible.copy(quizzesFinished = INTERSTITIAL_FREE_QUIZZES), now))
        assertTrue(shouldShowInterstitial(eligible.copy(quizzesFinished = INTERSTITIAL_FREE_QUIZZES + 1), now))
    }

    @Test
    fun a_fresh_install_is_untaxed_however_many_quizzes_it_finishes() {
        val bingeing = eligible.copy(daysSinceInstall = 0L, quizzesFinished = 40)

        assertFalse(shouldShowInterstitial(bingeing, now))
        assertFalse(shouldShowInterstitial(bingeing.copy(daysSinceInstall = INTERSTITIAL_MIN_INSTALL_DAYS - 1), now))
        assertTrue(shouldShowInterstitial(bingeing.copy(daysSinceInstall = INTERSTITIAL_MIN_INSTALL_DAYS), now))
    }

    @Test
    fun back_to_back_quizzes_yield_one_ad_not_two() {
        val justShown = eligible.copy(shownToday = 1, lastShownAtMs = now - 30_000L)

        assertFalse(shouldShowInterstitial(justShown, now))
        assertTrue(shouldShowInterstitial(justShown, now + INTERSTITIAL_MIN_GAP_MS))
    }

    @Test
    fun the_daily_cap_is_hard() {
        // Spacing satisfied, so only the cap can refuse this one.
        val spacedOut = eligible.copy(lastShownAtMs = now - INTERSTITIAL_MIN_GAP_MS * 10)

        assertTrue(shouldShowInterstitial(spacedOut.copy(shownToday = INTERSTITIAL_DAILY_CAP - 1), now))
        assertFalse(shouldShowInterstitial(spacedOut.copy(shownToday = INTERSTITIAL_DAILY_CAP), now))
        assertFalse(shouldShowInterstitial(spacedOut.copy(shownToday = INTERSTITIAL_DAILY_CAP + 5), now))
    }

    @Test
    fun the_play_review_prompt_owns_the_day_it_fires() {
        assertFalse(shouldShowInterstitial(eligible.copy(reviewPromptedToday = true), now))
    }

    // A device clock moved backwards would otherwise leave lastShownAtMs in the future and suppress
    // every ad until real time caught up. Treat it as stale rather than as a live cooldown.
    @Test
    fun a_timestamp_in_the_future_does_not_suppress_forever() {
        assertTrue(shouldShowInterstitial(eligible.copy(shownToday = 1, lastShownAtMs = now + 86_400_000L), now))
    }
}
