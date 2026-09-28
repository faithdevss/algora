package com.algora.app.core.notify

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// The reminder runs on a background worker that fires roughly daily, so an off-by-one in this gate
// is the difference between one nudge a week and one every morning. Pinned here rather than
// discovered on a user's device.
class StudyReminderTest {

    private val today = 20_000L

    @Test
    fun `an active user is never reminded`() {
        assertFalse(StudyReminder.shouldRemind(lastActiveDay = today, lastReminderDay = 0L, today = today))
        assertFalse(StudyReminder.shouldRemind(lastActiveDay = today - 6, lastReminderDay = 0L, today = today))
    }

    @Test
    fun `seven days of silence is the trigger`() {
        assertTrue(StudyReminder.shouldRemind(lastActiveDay = today - 7, lastReminderDay = 0L, today = today))
        assertTrue(StudyReminder.shouldRemind(lastActiveDay = today - 40, lastReminderDay = 0L, today = today))
    }

    @Test
    fun `an install never opened is not reminded`() {
        assertFalse(StudyReminder.shouldRemind(lastActiveDay = null, lastReminderDay = 0L, today = today))
    }

    @Test
    fun `a lapsed user is nudged weekly, not daily`() {
        val lastActive = today - 30
        assertFalse(StudyReminder.shouldRemind(lastActive, lastReminderDay = today - 1, today = today))
        assertFalse(StudyReminder.shouldRemind(lastActive, lastReminderDay = today - 6, today = today))
        assertTrue(StudyReminder.shouldRemind(lastActive, lastReminderDay = today - 7, today = today))
    }

    @Test
    fun `returning after a reminder stops the next one`() {
        // Opened the app yesterday, reminded eight days ago: active again, so nothing is due.
        assertFalse(StudyReminder.shouldRemind(lastActiveDay = today - 1, lastReminderDay = today - 8, today = today))
    }

    @Test
    fun `the message escalates with time away`() {
        val week = StudyReminder.message(7)
        val fortnight = StudyReminder.message(14)
        val month = StudyReminder.message(45)
        assertEquals(3, setOf(week, fortnight, month).size)
        assertTrue(week.isNotBlank() && fortnight.isNotBlank() && month.isNotBlank())
    }
}
