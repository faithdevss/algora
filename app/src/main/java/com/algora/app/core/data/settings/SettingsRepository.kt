package com.algora.app.core.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SettingsRepository(private val dataStore: DataStore<Preferences>) {

    val themeMode: Flow<ThemeMode> = dataStore.data.map { prefs ->
        when (prefs[SettingsKeys.THEME_MODE]) {
            "light" -> ThemeMode.LIGHT
            "dark" -> ThemeMode.DARK
            else -> ThemeMode.SYSTEM
        }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { prefs ->
            prefs[SettingsKeys.THEME_MODE] = when (mode) {
                ThemeMode.LIGHT -> "light"
                ThemeMode.DARK -> "dark"
                ThemeMode.SYSTEM -> "system"
            }
        }
    }

    val accent: Flow<AccentColor> = dataStore.data.map { prefs ->
        AccentColor.fromId(prefs[SettingsKeys.ACCENT])
    }

    suspend fun setAccent(accent: AccentColor) {
        dataStore.edit { prefs -> prefs[SettingsKeys.ACCENT] = accent.id }
    }

    val bookmarks: Flow<Set<String>> =
        dataStore.data.map { prefs -> prefs[SettingsKeys.BOOKMARKS] ?: emptySet() }

    suspend fun toggleBookmark(topicId: String) {
        dataStore.edit { prefs ->
            val current = prefs[SettingsKeys.BOOKMARKS] ?: emptySet()
            prefs[SettingsKeys.BOOKMARKS] = if (topicId in current) current - topicId else current + topicId
        }
    }

    val lastOpened: Flow<String?> =
        dataStore.data.map { prefs -> prefs[SettingsKeys.LAST_OPENED] }

    suspend fun setLastOpened(topicId: String) {
        dataStore.edit { prefs -> prefs[SettingsKeys.LAST_OPENED] = topicId }
    }

    val streak: Flow<Int> =
        dataStore.data.map { prefs -> prefs[SettingsKeys.STREAK_COUNT] ?: 0 }

    val srs: Flow<Map<String, SrsCard>> =
        dataStore.data.map { prefs ->
            (prefs[SettingsKeys.SRS] ?: emptySet()).mapNotNull { parseSrs(it) }.toMap()
        }

    // Records an SM-2 review of one card and reschedules it.
    suspend fun reviewCard(cardKey: String, quality: Int) {
        val today = System.currentTimeMillis() / 86_400_000L
        dataStore.edit { prefs ->
            val set = (prefs[SettingsKeys.SRS] ?: emptySet()).toMutableSet()
            val existingEntry = set.firstOrNull { it.substringBefore('|') == cardKey }
            val prev = existingEntry?.let { parseSrs(it)?.second }
            existingEntry?.let { set.remove(it) }
            set.add(sm2(prev, quality, today).serialize(cardKey))
            prefs[SettingsKeys.SRS] = set
        }
    }

    // Consecutive-day streak: same day → no change, yesterday → +1, any gap → reset to 1.
    suspend fun recordActivityToday() {
        val today = System.currentTimeMillis() / 86_400_000L
        dataStore.edit { prefs ->
            val lastDay = prefs[SettingsKeys.STREAK_LAST_DAY]
            val count = prefs[SettingsKeys.STREAK_COUNT] ?: 0
            val newCount = when {
                lastDay == null -> 1
                lastDay == today -> count.coerceAtLeast(1)
                lastDay == today - 1 -> count + 1
                else -> 1
            }
            prefs[SettingsKeys.STREAK_COUNT] = newCount
            prefs[SettingsKeys.STREAK_LAST_DAY] = today
        }
    }
}
