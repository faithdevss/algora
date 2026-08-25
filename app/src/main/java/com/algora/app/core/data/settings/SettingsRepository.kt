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

    val streakFreezes: Flow<Int> =
        dataStore.data.map { prefs -> prefs[SettingsKeys.STREAK_FREEZES] ?: 0 }

    // Earned by watching a rewarded ad; capped so the reward stays scarce.
    suspend fun addStreakFreeze() {
        dataStore.edit { prefs ->
            val current = prefs[SettingsKeys.STREAK_FREEZES] ?: 0
            prefs[SettingsKeys.STREAK_FREEZES] = (current + 1).coerceAtMost(MAX_STREAK_FREEZES)
        }
    }

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

    // 0 until the Play review flow has been launched once, then the day it was. A day rather than a
    // boolean so a future re-ask policy has the date it would need. Callers distinguish "not asked"
    // (0) from "still loading" by their own initial value — a null here would conflate the two.
    val reviewPromptedDay: Flow<Long> =
        dataStore.data.map { prefs -> prefs[SettingsKeys.REVIEW_PROMPT_DAY] ?: 0L }

    suspend fun markReviewPrompted(day: Long = System.currentTimeMillis() / 86_400_000L) {
        dataStore.edit { prefs -> prefs[SettingsKeys.REVIEW_PROMPT_DAY] = day }
    }

    // Study reminder state. Reminders default on; the worker reads these on every run, so switching
    // the toggle off silences it even before the scheduled work is cancelled.
    val remindersEnabled: Flow<Boolean> =
        dataStore.data.map { prefs -> prefs[SettingsKeys.REMINDERS_ENABLED] ?: true }

    suspend fun setRemindersEnabled(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[SettingsKeys.REMINDERS_ENABLED] = enabled }
    }

    /** 0 until a reminder has been posted. Keeps a lapsed user to one nudge a week, not one a day. */
    val lastReminderDay: Flow<Long> =
        dataStore.data.map { prefs -> prefs[SettingsKeys.LAST_REMINDER_DAY] ?: 0L }

    suspend fun markReminderPosted(day: Long = System.currentTimeMillis() / 86_400_000L) {
        dataStore.edit { prefs -> prefs[SettingsKeys.LAST_REMINDER_DAY] = day }
    }

    /** The Android 13+ notification dialog is one-shot; asking again does nothing but flicker. */
    val notificationPermissionAsked: Flow<Boolean> =
        dataStore.data.map { prefs -> prefs[SettingsKeys.NOTIF_PERMISSION_ASKED] ?: false }

    suspend fun markNotificationPermissionAsked() {
        dataStore.edit { prefs -> prefs[SettingsKeys.NOTIF_PERMISSION_ASKED] = true }
    }

    /** The last day the app was opened, or null if it never has been. */
    val lastActiveDay: Flow<Long?> =
        dataStore.data.map { prefs -> prefs[SettingsKeys.STREAK_LAST_DAY] }

    // New cards introduced on `today`. Any other stored day means the allowance has rolled over.
    fun newCardsIntroduced(today: Long): Flow<Int> =
        dataStore.data.map { prefs ->
            if (prefs[SettingsKeys.NEW_CARDS_DAY] == today) prefs[SettingsKeys.NEW_CARDS_COUNT] ?: 0 else 0
        }

    /**
     * The epoch day this install first opened the app, or `null` before it ever has.
     *
     * Written once by [ensureFirstOpenDay]; every later call is a no-op, so this is an install date
     * and not a "last seen". An install that predates the key reads as null and is treated by
     * callers as brand new — one extra ad-free grace period on upgrade, which is the harmless
     * direction to be wrong in.
     */
    val firstOpenDay: Flow<Long?> =
        dataStore.data.map { prefs -> prefs[SettingsKeys.FIRST_OPEN_DAY] }

    suspend fun ensureFirstOpenDay(today: Long = System.currentTimeMillis() / 86_400_000L) {
        dataStore.edit { prefs ->
            if (prefs[SettingsKeys.FIRST_OPEN_DAY] == null) prefs[SettingsKeys.FIRST_OPEN_DAY] = today
        }
    }

    /** Lifetime completed full quiz runs. Missed-only retries are not runs and never count here. */
    val quizzesFinished: Flow<Int> =
        dataStore.data.map { prefs -> prefs[SettingsKeys.QUIZZES_FINISHED] ?: 0 }

    suspend fun recordQuizFinished() {
        dataStore.edit { prefs ->
            prefs[SettingsKeys.QUIZZES_FINISHED] = (prefs[SettingsKeys.QUIZZES_FINISHED] ?: 0) + 1
        }
    }

    // Interstitials shown on `today`. Any other stored day means the cap has rolled over, so a stale
    // count can never be mistaken for today's — the same read-side guard newCardsIntroduced uses.
    fun interstitialsShownToday(today: Long): Flow<Int> =
        dataStore.data.map { prefs ->
            if (prefs[SettingsKeys.INTERSTITIAL_DAY] == today) prefs[SettingsKeys.INTERSTITIAL_COUNT] ?: 0 else 0
        }

    /** 0 until an interstitial has been shown; the spacing rule reads it as "never". */
    val interstitialLastShownAtMs: Flow<Long> =
        dataStore.data.map { prefs -> prefs[SettingsKeys.INTERSTITIAL_LAST_MS] ?: 0L }

    // Charged only against an impression that actually happened — a skipped or failed ad must not
    // spend the day's allowance.
    suspend fun recordInterstitialShown(nowMs: Long = System.currentTimeMillis()) {
        val today = nowMs / 86_400_000L
        dataStore.edit { prefs ->
            val shownToday = if (prefs[SettingsKeys.INTERSTITIAL_DAY] == today) {
                prefs[SettingsKeys.INTERSTITIAL_COUNT] ?: 0
            } else {
                0
            }
            prefs[SettingsKeys.INTERSTITIAL_DAY] = today
            prefs[SettingsKeys.INTERSTITIAL_COUNT] = shownToday + 1
            prefs[SettingsKeys.INTERSTITIAL_LAST_MS] = nowMs
        }
    }

    // Consecutive-day streak: same day → no change, yesterday → +1, a single missed day spends a
    // banked freeze (if any) and still counts as +1, any larger gap → reset to 1.
    suspend fun recordActivityToday() {
        val today = System.currentTimeMillis() / 86_400_000L
        dataStore.edit { prefs ->
            val lastDay = prefs[SettingsKeys.STREAK_LAST_DAY]
            val count = prefs[SettingsKeys.STREAK_COUNT] ?: 0
            val freezes = prefs[SettingsKeys.STREAK_FREEZES] ?: 0
            val gap = lastDay?.let { today - it }
            val newCount = when {
                lastDay == null -> 1
                gap == 0L -> count.coerceAtLeast(1)
                gap == 1L -> count + 1
                gap == 2L && freezes > 0 -> {
                    prefs[SettingsKeys.STREAK_FREEZES] = freezes - 1
                    count + 1
                }
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
