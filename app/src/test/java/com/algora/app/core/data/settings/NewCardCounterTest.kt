package com.algora.app.core.data.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

// The daily new-card allowance only works if "introduced today" counts first-time grades and nothing
// else, and rolls over on a new day.
class NewCardCounterTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun newRepository(): SettingsRepository {
        val dataStore = PreferenceDataStoreFactory.create(
            produceFile = { tempFolder.newFile("test.preferences_pb") },
        )
        return SettingsRepository(dataStore)
    }

    private val today = System.currentTimeMillis() / 86_400_000L

    @Test
    fun grading_an_unseen_card_counts_as_introducing_it() = runTest {
        val repository = newRepository()

        repository.reviewCard("array#0", 4)
        repository.reviewCard("array#1", 5)

        assertEquals(2, repository.newCardsIntroduced(today).first())
    }

    @Test
    fun regrading_the_same_card_does_not_count_twice() = runTest {
        val repository = newRepository()

        repository.reviewCard("array#0", 2)
        repository.reviewCard("array#0", 4)
        repository.reviewCard("array#0", 5)

        assertEquals(1, repository.newCardsIntroduced(today).first())
    }

    @Test
    fun the_allowance_rolls_over_on_a_different_day() = runTest {
        val repository = newRepository()
        repository.reviewCard("array#0", 4)

        // Yesterday's tally must not consume today's allowance.
        assertEquals(0, repository.newCardsIntroduced(today + 1).first())
    }

    @Test
    fun introduced_count_starts_at_zero() = runTest {
        assertEquals(0, newRepository().newCardsIntroduced(today).first())
    }
}
