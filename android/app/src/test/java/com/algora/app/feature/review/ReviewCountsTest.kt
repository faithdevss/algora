package com.algora.app.feature.review

import com.algora.app.core.data.settings.SrsCard
import org.junit.Assert.assertEquals
import org.junit.Test

// The whole point of merging the two decks is that a graded card stops coming back daily. These pin
// down which bucket a card falls into, since the Practice row and the queue both read from it.
class ReviewCountsTest {

    private val today = 100L

    private fun cards(vararg keys: String) = keys.map { ReviewCard(it, "Topic", "Takeaway") }

    private fun srs(vararg entries: Pair<String, Long>): Map<String, SrsCard> =
        entries.associate { (key, dueDay) ->
            key to SrsCard(reps = 1, ef = 2.5, intervalDays = 1, dueDay = dueDay)
        }

    @Test
    fun `cards never graded count as new`() {
        val counts = reviewCounts(cards("a", "b"), emptyMap(), today)
        assertEquals(2, counts.new)
        assertEquals(0, counts.due)
        assertEquals(0, counts.scheduled)
        assertEquals(2, counts.waiting)
    }

    @Test
    fun `a card scheduled for the future is not waiting`() {
        val counts = reviewCounts(cards("a"), srs("a" to today + 6), today)
        assertEquals(0, counts.waiting)
        assertEquals(1, counts.scheduled)
    }

    @Test
    fun `a card due today or overdue is waiting`() {
        val counts = reviewCounts(cards("a", "b"), srs("a" to today, "b" to today - 3), today)
        assertEquals(2, counts.due)
        assertEquals(0, counts.scheduled)
    }

    @Test
    fun `new cards beyond the daily allowance are held back`() {
        val counts = reviewCounts(cards("a", "b", "c", "d", "e"), emptyMap(), today, newAllowance = 2)
        assertEquals(2, counts.new)
        assertEquals(3, counts.newHeldBack)
        assertEquals(2, counts.waiting)
    }

    @Test
    fun `an exhausted allowance holds every new card back but leaves due cards alone`() {
        val counts = reviewCounts(cards("seen", "fresh"), srs("seen" to today), today, newAllowance = 0)
        assertEquals(1, counts.due)
        assertEquals(0, counts.new)
        assertEquals(1, counts.newHeldBack)
        assertEquals(1, counts.waiting)
    }

    @Test
    fun `an allowance larger than the new pile holds nothing back`() {
        val counts = reviewCounts(cards("a", "b"), emptyMap(), today, newAllowance = 20)
        assertEquals(2, counts.new)
        assertEquals(0, counts.newHeldBack)
    }

    @Test
    fun `mixed deck splits into due, new and scheduled`() {
        val counts = reviewCounts(
            cards("due", "overdue", "later", "fresh"),
            srs("due" to today, "overdue" to today - 1, "later" to today + 30),
            today,
        )
        assertEquals(2, counts.due)
        assertEquals(1, counts.new)
        assertEquals(1, counts.scheduled)
        assertEquals(3, counts.waiting)
    }
}
