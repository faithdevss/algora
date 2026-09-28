package com.algora.app.core.data.progress

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import android.content.Context

val Context.progressDataStore: DataStore<Preferences> by preferencesDataStore(name = "algora_progress")

object ProgressKeys {
    val COMPLETED_TOPIC_IDS = stringSetPreferencesKey("completed_topic_ids")
    // "topicId|epochDay" per finished topic — the id set alone cannot say *when* anything was done,
    // which the Progress week strip needs. Written alongside COMPLETED_TOPIC_IDS, never instead of
    // it, so pre-existing completions keep counting.
    val COMPLETED_AT = stringSetPreferencesKey("completed_at_days")
    // Practice-tab problem bank; kept separate from topic completion so the two never collide.
    val SOLVED_PROBLEM_IDS = stringSetPreferencesKey("solved_problem_ids")
}
