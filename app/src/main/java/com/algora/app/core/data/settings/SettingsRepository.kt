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

    /**
     * `null` means "Auto" — the accent follows the active app mode (see `AppMode.accent`). That is
     * also the default for a fresh install, so DSA opens indigo and AI opens pink without the user
     * choosing anything. An explicit pick is stored by id and overrides the mode in both modes.
     */
    val accent: Flow<AccentColor?> = dataStore.data.map { prefs ->
        when (val id = prefs[SettingsKeys.ACCENT]) {
            null, AccentColor.AUTO_ID -> null
            else -> AccentColor.fromId(id)
        }
    }

    suspend fun setAccent(accent: AccentColor?) {
        dataStore.edit { prefs -> prefs[SettingsKeys.ACCENT] = accent?.id ?: AccentColor.AUTO_ID }
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

    /** Epoch days the app was opened, within the retained window. */
    val activeDays: Flow<Set<Long>> =
        dataStore.data.map { prefs ->
            (prefs[SettingsKeys.ACTIVE_DAYS] ?: emptySet()).mapNotNull { it.toLongOrNull() }.toSet()
        }

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

            // Grading a card with no prior state is what "introducing a new card" means; the counter
            // resets whenever the stored day is not today.
            if (prev == null) {
                val storedDay = prefs[SettingsKeys.NEW_CARDS_DAY]
                val introducedToday = if (storedDay == today) prefs[SettingsKeys.NEW_CARDS_COUNT] ?: 0 else 0
                prefs[SettingsKeys.NEW_CARDS_DAY] = today
                prefs[SettingsKeys.NEW_CARDS_COUNT] = introducedToday + 1
            }
        }
    }

    /** Finished quiz runs, keyed by quiz id (= the topic id that renders the quiz), newest first. */
    val quizAttempts: Flow<Map<String, List<QuizAttempt>>> =
        dataStore.data.map { prefs ->
            (prefs[SettingsKeys.QUIZ_ATTEMPTS] ?: emptySet())
                .mapNotNull { parseQuizAttempt(it) }
                .groupBy({ it.first }, { it.second })
                .mapValues { (_, attempts) -> attempts.newestFirst() }
        }

    // Records one finished run and trims that quiz's history to the newest QUIZ_ATTEMPTS_PER_QUIZ.
    // Other quizzes' entries are left untouched, so the set stays a flat write.
    suspend fun recordQuizAttempt(quizId: String, attempt: QuizAttempt) {
        dataStore.edit { prefs ->
            val existing = (prefs[SettingsKeys.QUIZ_ATTEMPTS] ?: emptySet()).mapNotNull { parseQuizAttempt(it) }
            val (mine, others) = existing.partition { it.first == quizId }
            val kept = (mine.map { it.second } + attempt).newestFirst().take(QUIZ_ATTEMPTS_PER_QUIZ)
            prefs[SettingsKeys.QUIZ_ATTEMPTS] =
                (others.map { (id, a) -> a.serialize(id) } + kept.map { it.serialize(quizId) }).toSet()
        }
    }

    /** The problem the daily drill picked, and the day it picked it. Null before the first drill. */
    val drillProblem: Flow<Pair<Long, String>?> =
        dataStore.data.map { prefs ->
            val day = prefs[SettingsKeys.DRILL_DAY]
            val problemId = prefs[SettingsKeys.DRILL_PROBLEM]
            if (day == null || problemId == null) null else day to problemId
        }

    suspend fun setDrillProblem(day: Long, problemId: String) {
        dataStore.edit { prefs ->
            prefs[SettingsKeys.DRILL_DAY] = day
            prefs[SettingsKeys.DRILL_PROBLEM] = problemId
        }
    }

    // New cards introduced on `today`. Any other stored day means the allowance has rolled over.
    fun newCardsIntroduced(today: Long): Flow<Int> =
        dataStore.data.map { prefs ->
            if (prefs[SettingsKeys.NEW_CARDS_DAY] == today) prefs[SettingsKeys.NEW_CARDS_COUNT] ?: 0 else 0
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

            val history = (prefs[SettingsKeys.ACTIVE_DAYS] ?: emptySet()).mapNotNull { it.toLongOrNull() }
            prefs[SettingsKeys.ACTIVE_DAYS] = (history + today)
                .filter { it > today - ACTIVITY_HISTORY_DAYS }
                .map { it.toString() }
                .toSet()
        }
    }
}

// Only recent days are ever rendered; keeping more would grow the preference set without bound.
private const val ACTIVITY_HISTORY_DAYS = 60L
