package com.algora.app.core.data.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Attempts live in a DataStore *set* of strings, so the encoding carries the whole record: get the
// separator handling wrong and a run either vanishes, merges with another, or loses the wrong-answer
// indices that the "retry missed questions" button is built on.
class QuizAttemptTest {

    private val attempt = QuizAttempt(atEpochSec = 1_770_000_000L, correct = 7, total = 10, seconds = 214, wrongIndices = listOf(2, 5, 9))

    @Test
    fun `round trips through the preference encoding`() {
        val (id, parsed) = parseQuizAttempt(attempt.serialize("faang_set"))!!
        assertEquals("faang_set", id)
        assertEquals(attempt, parsed)
    }

    @Test
    fun `a perfect run encodes an empty wrong list`() {
        val perfect = attempt.copy(correct = 10, wrongIndices = emptyList())
        val (_, parsed) = parseQuizAttempt(perfect.serialize("graphs_quiz"))!!
        assertEquals(emptyList<Int>(), parsed.wrongIndices)
        assertEquals(100, parsed.percent)
    }

    @Test
    fun `malformed entries parse to null rather than throwing`() {
        assertNull(parseQuizAttempt("faang_set|not-a-number|7|10|214|2"))
        assertNull(parseQuizAttempt("faang_set|1|2|3"))
        assertNull(parseQuizAttempt(""))
    }

    @Test
    fun `percent guards a zero-question quiz`() {
        assertEquals(0, QuizAttempt(1L, 0, 0, 0, emptyList()).percent)
    }

    // Two runs finished the same day with the same score are different runs; the set would collapse
    // them if the timestamp were a day rather than a second.
    @Test
    fun `same-day runs stay distinct entries`() {
        val a = attempt.serialize("faang_set")
        val b = attempt.copy(atEpochSec = attempt.atEpochSec + 400).serialize("faang_set")
        assertEquals(2, setOf(a, b).size)
        assertEquals(attempt.day, parseQuizAttempt(b)!!.second.day)
    }

    @Test
    fun `newestFirst orders by timestamp and best takes the maximum`() {
        val older = attempt.copy(atEpochSec = attempt.atEpochSec - 86_400, correct = 9)
        val history = listOf(attempt, older).newestFirst()
        assertEquals(attempt.atEpochSec, history.first().atEpochSec)
        assertEquals(90, history.bestPercent)
        assertEquals(9, history.bestCorrect)
        assertEquals(0, emptyList<QuizAttempt>().bestPercent)
    }

    // Mirrors SettingsRepository.recordQuizAttempt's trim: newest QUIZ_ATTEMPTS_PER_QUIZ survive.
    @Test
    fun `history trims to the cap keeping the newest`() {
        val runs = (1..8).map { attempt.copy(atEpochSec = attempt.atEpochSec + it * 3600L, correct = it) }
        val kept = runs.newestFirst().take(QUIZ_ATTEMPTS_PER_QUIZ)
        assertEquals(QUIZ_ATTEMPTS_PER_QUIZ, kept.size)
        assertEquals(8, kept.first().correct)
        assertTrue(kept.none { it.correct <= 3 })
    }

    // A set that grew from 5 to 15 questions must not report an old perfect run as "Best 5/15".
    @Test
    fun `history from a shorter version of the set is ignored`() {
        val old = attempt.copy(correct = 5, total = 5)
        val current = attempt.copy(atEpochSec = attempt.atEpochSec + 60, correct = 9, total = 15)
        val history = listOf(old, current).ofLength(15)
        assertEquals(listOf(current), history)
        assertEquals(9, history.bestCorrect)
        assertEquals(0, listOf(old).ofLength(15).bestPercent)
    }
}
