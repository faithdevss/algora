package com.algora.app.core.data.progress

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ProgressRepository(private val dataStore: DataStore<Preferences>) {

    val completedTopicIds: Flow<Set<String>> =
        dataStore.data.map { prefs -> prefs[ProgressKeys.COMPLETED_TOPIC_IDS] ?: emptySet() }

    suspend fun markCompleted(topicId: String) {
        dataStore.edit { prefs ->
            val current = prefs[ProgressKeys.COMPLETED_TOPIC_IDS] ?: emptySet()
            prefs[ProgressKeys.COMPLETED_TOPIC_IDS] = current + topicId
        }
    }

    suspend fun markIncomplete(topicId: String) {
        dataStore.edit { prefs ->
            val current = prefs[ProgressKeys.COMPLETED_TOPIC_IDS] ?: emptySet()
            prefs[ProgressKeys.COMPLETED_TOPIC_IDS] = current - topicId
        }
    }

    val solvedProblemIds: Flow<Set<String>> =
        dataStore.data.map { prefs -> prefs[ProgressKeys.SOLVED_PROBLEM_IDS] ?: emptySet() }

    suspend fun setProblemSolved(problemId: String, solved: Boolean) {
        dataStore.edit { prefs ->
            val current = prefs[ProgressKeys.SOLVED_PROBLEM_IDS] ?: emptySet()
            prefs[ProgressKeys.SOLVED_PROBLEM_IDS] = if (solved) current + problemId else current - problemId
        }
    }
}
