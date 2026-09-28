package com.algora.app.core.data.progress

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ProgressRepository(private val dataStore: DataStore<Preferences>) {

    val completedTopicIds: Flow<Set<String>> =
        dataStore.data.map { prefs -> prefs[ProgressKeys.COMPLETED_TOPIC_IDS] ?: emptySet() }

    /** Topics finished per epoch day. Only days with at least one completion appear. */
    val completionsByDay: Flow<Map<Long, Int>> =
        dataStore.data.map { prefs ->
            (prefs[ProgressKeys.COMPLETED_AT] ?: emptySet())
                .mapNotNull { it.substringAfterLast('|').toLongOrNull() }
                .groupingBy { it }
                .eachCount()
        }

    // `day` is a parameter rather than a call to the clock so completion history is testable.
    suspend fun markCompleted(topicId: String, day: Long = todayEpochDay()) {
        dataStore.edit { prefs ->
            val current = prefs[ProgressKeys.COMPLETED_TOPIC_IDS] ?: emptySet()
            prefs[ProgressKeys.COMPLETED_TOPIC_IDS] = current + topicId

            // Re-completing a topic keeps the original date; only the first finish is a data point.
            val log = prefs[ProgressKeys.COMPLETED_AT] ?: emptySet()
            if (log.none { it.substringBeforeLast('|') == topicId }) {
                prefs[ProgressKeys.COMPLETED_AT] = log + "$topicId|$day"
            }
        }
    }

    suspend fun markIncomplete(topicId: String) {
        dataStore.edit { prefs ->
            val current = prefs[ProgressKeys.COMPLETED_TOPIC_IDS] ?: emptySet()
            prefs[ProgressKeys.COMPLETED_TOPIC_IDS] = current - topicId

            val log = prefs[ProgressKeys.COMPLETED_AT] ?: emptySet()
            prefs[ProgressKeys.COMPLETED_AT] = log.filterNot { it.substringBeforeLast('|') == topicId }.toSet()
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

/** Same UTC-day bucketing the streak counter uses, so the two never disagree about "today". */
fun todayEpochDay(): Long = System.currentTimeMillis() / 86_400_000L
