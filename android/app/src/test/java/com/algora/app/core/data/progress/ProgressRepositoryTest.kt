package com.algora.app.core.data.progress

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ProgressRepositoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun newRepository(): ProgressRepository {
        val dataStore = PreferenceDataStoreFactory.create(
            produceFile = { tempFolder.newFile("test.preferences_pb") },
        )
        return ProgressRepository(dataStore)
    }

    @Test
    fun markCompleted_addsTopicIdToFlow() = runTest {
        val repository = newRepository()

        repository.markCompleted("array")

        assertEquals(setOf("array"), repository.completedTopicIds.first())
    }

    @Test
    fun completionsByDay_countsTopicsPerDay() = runTest {
        val repository = newRepository()

        repository.markCompleted("array", day = 20_000L)
        repository.markCompleted("bst", day = 20_000L)
        repository.markCompleted("heap", day = 20_001L)

        assertEquals(mapOf(20_000L to 2, 20_001L to 1), repository.completionsByDay.first())
    }

    @Test
    fun markCompleted_keepsFirstCompletionDay() = runTest {
        val repository = newRepository()

        repository.markCompleted("array", day = 20_000L)
        repository.markCompleted("array", day = 20_005L)

        assertEquals(mapOf(20_000L to 1), repository.completionsByDay.first())
    }

    @Test
    fun markIncomplete_dropsThatTopicsCompletionDay() = runTest {
        val repository = newRepository()
        repository.markCompleted("array", day = 20_000L)
        repository.markCompleted("bst", day = 20_000L)

        repository.markIncomplete("array")

        assertEquals(mapOf(20_000L to 1), repository.completionsByDay.first())
    }

    @Test
    fun markIncomplete_removesTopicIdFromFlow() = runTest {
        val repository = newRepository()
        repository.markCompleted("array")
        repository.markCompleted("bst")

        repository.markIncomplete("array")

        assertEquals(setOf("bst"), repository.completedTopicIds.first())
    }
}
