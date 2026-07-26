package com.algora.app.core.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore

// Engagement/preferences store, kept separate from the completion-progress store (Phase 0/7).
val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "algora_settings")

enum class ThemeMode { SYSTEM, LIGHT, DARK }

object SettingsKeys {
    val THEME_MODE = stringPreferencesKey("theme_mode")
    val ACCENT = stringPreferencesKey("accent_color")
    val BOOKMARKS = stringSetPreferencesKey("bookmarks")
    val LAST_OPENED = stringPreferencesKey("last_opened_topic")
    val STREAK_COUNT = intPreferencesKey("streak_count")
    val STREAK_LAST_DAY = longPreferencesKey("streak_last_epoch_day")
    val SRS = stringSetPreferencesKey("srs_state")
}
