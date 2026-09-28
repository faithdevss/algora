package com.algora.app.core.notify

import com.algora.app.core.notify.DailyReminder.Nudge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// The daily nudge is the app's only *recurring* notification, so every rule that keeps it from
// becoming noise is worth pinning. The policy is pure, so all of it runs without a device.
class DailyReminderTest {

    private val today = 20_000L

    // Slipped one day, streak too short to defend, cards waiting: the ordinary case.
    private val slipping = DailyReminder.State(
        enabled = true,
        lastActiveDay = today - 1,
        lastReminderDay = 0L,
        cardsWaiting = 7,
        drillDoneToday = false,
        streak = 1,
        today = today,
    )

    @Test
    fun cards_waiting_is_the_default_nudge() {
        assertEquals(Nudge.CARDS_WAITING, DailyReminder.nudgeFor(slipping))
    }

    @Test
    fun a_live_streak_outranks_the_card_count() {
        val state = slipping.copy(streak = DailyReminder.STREAK_AT_RISK_MIN)
        assertEquals(Nudge.STREAK_AT_RISK, DailyReminder.nudgeFor(state))
    }

    @Test
    fun a_short_streak_is_not_worth_claiming() {
        val state = slipping.copy(streak = DailyReminder.STREAK_AT_RISK_MIN - 1)
        assertEquals(Nudge.CARDS_WAITING, DailyReminder.nudgeFor(state))
    }

    @Test
    fun the_drill_is_the_fallback_when_no_cards_are_due() {
        val state = slipping.copy(cardsWaiting = 0)
        assertEquals(Nudge.DRILL_WAITING, DailyReminder.nudgeFor(state))
    }

    @Test
    fun nothing_to_do_means_nothing_is_posted() {
        val state = slipping.copy(cardsWaiting = 0, drillDoneToday = true)
        assertNull(DailyReminder.nudgeFor(state))
    }

    @Test
    fun opening_the_app_today_silences_it() {
        assertNull(DailyReminder.nudgeFor(slipping.copy(lastActiveDay = today)))
    }

    @Test
    fun a_fresh_install_that_never_opened_is_never_nudged() {
        assertNull(DailyReminder.nudgeFor(slipping.copy(lastActiveDay = null)))
    }

    @Test
    fun it_is_off_when_the_user_turned_it_off() {
        assertNull(DailyReminder.nudgeFor(slipping.copy(enabled = false)))
    }

    @Test
    fun at_most_one_a_day() {
        assertNull(DailyReminder.nudgeFor(slipping.copy(lastReminderDay = today)))
    }

    // The handover: past the gap this stays quiet and StudyReminder's weekly nudge takes the user.
    @Test
    fun a_lapsed_user_is_left_to_the_weekly_reminder() {
        val edge = slipping.copy(lastActiveDay = today - DailyReminder.MAX_GAP_DAYS)
        assertEquals(Nudge.CARDS_WAITING, DailyReminder.nudgeFor(edge))

        val lapsed = slipping.copy(lastActiveDay = today - DailyReminder.MAX_GAP_DAYS - 1)
        assertNull(DailyReminder.nudgeFor(lapsed))
    }

    // Neither reminder may ever post on the same day as the other: one needs a gap of at most three
    // days, the other of at least seven, and this asserts the two windows cannot overlap.
    @Test
    fun the_two_reminders_cannot_both_fire() {
        (0L..14L).forEach { gap ->
            val daily = DailyReminder.nudgeFor(slipping.copy(lastActiveDay = today - gap)) != null
            val weekly = StudyReminder.shouldRemind(
                lastActiveDay = today - gap,
                lastReminderDay = 0L,
                today = today,
            )
            assert(!(daily && weekly)) { "both reminders fire at a gap of $gap days" }
        }
    }

    @Test
    fun the_card_count_is_in_the_text() {
        assertEquals("7 cards waiting", DailyReminder.title(Nudge.CARDS_WAITING, streak = 1, cardsWaiting = 7))
        assertEquals("1 card waiting", DailyReminder.title(Nudge.CARDS_WAITING, streak = 1, cardsWaiting = 1))
        assertEquals(
            "Your 5-day streak ends tonight",
            DailyReminder.title(Nudge.STREAK_AT_RISK, streak = 5, cardsWaiting = 0),
        )
    }

    @Test
    fun a_streak_nudge_lands_on_the_drill_and_cards_on_the_queue() {
        assertEquals("daily_drill", DailyReminder.routeFor(Nudge.STREAK_AT_RISK))
        assertEquals("daily_drill", DailyReminder.routeFor(Nudge.DRILL_WAITING))
        assertEquals("review", DailyReminder.routeFor(Nudge.CARDS_WAITING))
    }

    @Test
    fun the_delay_wraps_to_tomorrow_when_the_time_has_passed() {
        // 09:00 now, 20:00 target — eleven hours.
        assertEquals(11 * 60L, DailyReminderWorker.delayMinutes(9 * 60, 20 * 60))
        // 21:00 now, 20:00 target — twenty-three.
        assertEquals(23 * 60L, DailyReminderWorker.delayMinutes(21 * 60, 20 * 60))
        // Exactly the appointed minute waits a full day rather than posting on the spot.
        assertEquals(24 * 60L, DailyReminderWorker.delayMinutes(20 * 60, 20 * 60))
    }
}
