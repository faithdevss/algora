package com.algora.app.core.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
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
    // Every epoch-day the app was opened, trimmed to a rolling window — the streak counter alone
    // cannot say *which* days were active, which the Progress week strip needs.
    val ACTIVE_DAYS = stringSetPreferencesKey("active_epoch_days")
    val SRS = stringSetPreferencesKey("srs_state")
    // How many never-seen cards were introduced, and on which day — drives the daily new-card cap.
    val NEW_CARDS_DAY = longPreferencesKey("new_cards_epoch_day")
    val NEW_CARDS_COUNT = intPreferencesKey("new_cards_count")
    // Finished quiz runs (see QuizAttempt). A quiz used to score you and forget, so nothing could
    // say what you keep getting wrong.
    val QUIZ_ATTEMPTS = stringSetPreferencesKey("quiz_attempts")
    // The daily drill's chosen problem, pinned to the day it was chosen. Without pinning, solving it
    // would immediately reroll the pick — the drill would never show as finished.
    val DRILL_DAY = longPreferencesKey("drill_epoch_day")
    val DRILL_PROBLEM = stringPreferencesKey("drill_problem_id")
    // The epoch day the Play review flow was launched. Present = already asked; the prompt is
    // one-shot regardless of what the user did with it, since Play reports no outcome and
    // rate-limits the flow anyway.
    val REVIEW_PROMPT_DAY = longPreferencesKey("review_prompt_epoch_day")
    // Study reminder (core/notify): the user's opt-out, the day the last reminder was posted (so a
    // lapsed user is nudged once a week, not once a day), and whether the Android 13+ notification
    // permission has been asked for — the system only shows that dialog once.
    val REMINDERS_ENABLED = booleanPreferencesKey("study_reminders_enabled")
    val LAST_REMINDER_DAY = longPreferencesKey("last_reminder_epoch_day")
    val NOTIF_PERMISSION_ASKED = booleanPreferencesKey("notification_permission_asked")
    // Banked streak freezes, earned by watching a rewarded ad. Spent automatically on a one-day gap
    // so a single missed day doesn't reset the streak counter.
    val STREAK_FREEZES = intPreferencesKey("streak_freezes")
}

/** A user can never bank more freezes than this, so the reward stays worth watching an ad for. */
const val MAX_STREAK_FREEZES = 2
