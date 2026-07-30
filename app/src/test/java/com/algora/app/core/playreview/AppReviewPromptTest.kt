package com.algora.app.core.playreview

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// The prompt is one-shot and progress-gated. Getting either half wrong is a Play-policy problem
// (nagging) or a lost rating, and neither is visible in a normal debug run — FakeReviewManager shows
// no UI — so the gate is pinned here.
class AppReviewPromptTest {

    @Test
    fun `no prompt below the progress threshold`() {
        assertFalse(AppReviewPrompt.shouldAsk(progressPercent = 0, promptedDay = null))
        assertFalse(AppReviewPrompt.shouldAsk(progressPercent = 14, promptedDay = null))
    }

    @Test
    fun `prompts at and above the threshold when never asked`() {
        assertTrue(AppReviewPrompt.shouldAsk(AppReviewPrompt.PROGRESS_THRESHOLD_PERCENT, promptedDay = null))
        assertTrue(AppReviewPrompt.shouldAsk(progressPercent = 60, promptedDay = null))
    }

    @Test
    fun `never prompts twice`() {
        assertFalse(AppReviewPrompt.shouldAsk(progressPercent = 90, promptedDay = 20_000L))
    }

    @Test
    fun `the threshold is the documented 15 percent`() {
        assertTrue(AppReviewPrompt.PROGRESS_THRESHOLD_PERCENT == 15)
    }
}
