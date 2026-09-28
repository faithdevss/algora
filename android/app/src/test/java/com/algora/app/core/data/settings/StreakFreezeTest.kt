package com.algora.app.core.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

// A missed day resets the streak counter unless a banked freeze covers exactly that one-day gap.
class StreakFreezeTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun newStore(): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(produceFile = { tempFolder.newFile("test.preferences_pb") })

    private val today = System.currentTimeMillis() / 86_400_000L

    // Seeds "last active `daysAgo` days ago, streak already at `streakCount`" without waiting real days.
    private suspend fun seedLastActive(store: DataStore<Preferences>, daysAgo: Long, streakCount: Int) {
        store.edit { prefs ->
            prefs[SettingsKeys.STREAK_LAST_DAY] = today - daysAgo
            prefs[SettingsKeys.STREAK_COUNT] = streakCount
        }
    }

    @Test
    fun banking_a_freeze_caps_at_the_maximum() = runTest {
        val repository = SettingsRepository(newStore())

        repeat(MAX_STREAK_FREEZES + 3) { repository.addStreakFreeze() }

        assertEquals(MAX_STREAK_FREEZES, repository.streakFreezes.first())
    }

    @Test
    fun a_one_day_gap_with_a_freeze_available_spends_it_and_keeps_the_streak() = runTest {
        val store = newStore()
        val repository = SettingsRepository(store)
        seedLastActive(store, daysAgo = 2, streakCount = 5)
        repository.addStreakFreeze()

        repository.recordActivityToday()

        assertEquals(6, repository.streak.first())
        assertEquals(0, repository.streakFreezes.first())
    }

    @Test
    fun a_one_day_gap_with_no_freeze_resets_the_streak() = runTest {
        val store = newStore()
        val repository = SettingsRepository(store)
        seedLastActive(store, daysAgo = 2, streakCount = 5)

        repository.recordActivityToday()

        assertEquals(1, repository.streak.first())
    }

    @Test
    fun a_two_day_gap_resets_the_streak_even_with_a_freeze_banked() = runTest {
        val store = newStore()
        val repository = SettingsRepository(store)
        seedLastActive(store, daysAgo = 3, streakCount = 5)
        repository.addStreakFreeze()

        repository.recordActivityToday()

        assertEquals(1, repository.streak.first())
        assertEquals(1, repository.streakFreezes.first())
    }

    @Test
    fun opening_the_app_again_the_same_day_does_not_touch_freezes() = runTest {
        val store = newStore()
        val repository = SettingsRepository(store)
        seedLastActive(store, daysAgo = 0, streakCount = 3)
        repository.addStreakFreeze()

        repository.recordActivityToday()

        assertEquals(3, repository.streak.first())
        assertEquals(1, repository.streakFreezes.first())
    }
}
