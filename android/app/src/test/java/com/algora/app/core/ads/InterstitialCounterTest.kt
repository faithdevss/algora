package com.algora.app.core.ads

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.algora.app.core.data.settings.SettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

// The gate is only as good as the counters feeding it: a daily cap that fails to roll over, or a
// quiz counter that trims like QUIZ_ATTEMPTS does, would silently change the ad load.
class InterstitialCounterTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun newRepository(): SettingsRepository {
        val dataStore = PreferenceDataStoreFactory.create(
            produceFile = { tempFolder.newFile("test.preferences_pb") },
        )
        return SettingsRepository(dataStore)
    }

    private val dayMs = 86_400_000L
    private val today = 19_676L
    private val noonToday = today * dayMs + dayMs / 2

    @Test
    fun shown_interstitials_accumulate_within_a_day() = runTest {
        val repository = newRepository()

        repository.recordInterstitialShown(noonToday)
        repository.recordInterstitialShown(noonToday + 300_000L)

        assertEquals(2, repository.interstitialsShownToday(today).first())
    }

    @Test
    fun the_cap_rolls_over_at_the_day_boundary() = runTest {
        val repository = newRepository()

        repository.recordInterstitialShown(noonToday)

        assertEquals(1, repository.interstitialsShownToday(today).first())
        assertEquals(0, repository.interstitialsShownToday(today + 1).first())
    }

    @Test
    fun the_last_shown_timestamp_is_the_most_recent_one() = runTest {
        val repository = newRepository()

        repository.recordInterstitialShown(noonToday)
        repository.recordInterstitialShown(noonToday + 300_000L)

        assertEquals(noonToday + 300_000L, repository.interstitialLastShownAtMs.first())
    }

    @Test
    fun finished_quizzes_count_for_the_life_of_the_install() = runTest {
        val repository = newRepository()

        repeat(12) { repository.recordQuizFinished() }

        assertEquals(12, repository.quizzesFinished.first())
    }

    @Test
    fun the_first_open_day_is_stamped_once_and_never_moves() = runTest {
        val repository = newRepository()

        repository.ensureFirstOpenDay(today)
        repository.ensureFirstOpenDay(today + 9)

        assertEquals(today, repository.firstOpenDay.first())
    }

    @Test
    fun a_fresh_install_has_no_first_open_day_yet() = runTest {
        assertEquals(null, newRepository().firstOpenDay.first())
    }
}
