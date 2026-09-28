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

    // Latest right/wrong per quiz question, from any kind of run (see QuestionResult.kt).
    val QUESTION_RESULTS = stringSetPreferencesKey("question_results")
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
    // The daily reminder (core/notify/DailyReminder): its own opt-out, the local time of day the
    // user picked for it, and the day it last posted. Separate from the keys above because the two
    // reminders answer different questions — one nudges a lapsed learner back, the other protects a
    // streak that is still alive — and a user may well want one without the other.
    val DAILY_REMINDER_ENABLED = booleanPreferencesKey("daily_reminder_enabled")
    val DAILY_REMINDER_MINUTE = intPreferencesKey("daily_reminder_minute_of_day")
    val LAST_DAILY_REMINDER_DAY = longPreferencesKey("last_daily_reminder_epoch_day")
    // Banked streak freezes, earned by watching a rewarded ad. Spent automatically on a one-day gap
    // so a single missed day doesn't reset the streak counter.
    val STREAK_FREEZES = intPreferencesKey("streak_freezes")
    // The epoch day this install first opened the app. ACTIVE_DAYS cannot answer this — it is a
    // trimmed rolling window, so on an old install its earliest day is the window edge, not the
    // install. The quiz-exit interstitial's grace period needs the real one.
    val FIRST_OPEN_DAY = longPreferencesKey("first_open_epoch_day")
    // Lifetime count of completed *full* quiz runs. QUIZ_ATTEMPTS cannot stand in: it is trimmed to
    // the newest few per quiz, so it shrinks as the user does more.
    val QUIZZES_FINISHED = intPreferencesKey("quizzes_finished")
    // Quiz-exit interstitials shown, and on which day — the rolling daily cap, same shape as
    // NEW_CARDS_DAY/COUNT. LAST_MS is separate because the spacing rule is minutes, not days.
    val INTERSTITIAL_DAY = longPreferencesKey("interstitial_epoch_day")
    val INTERSTITIAL_COUNT = intPreferencesKey("interstitial_count")
    val INTERSTITIAL_LAST_MS = longPreferencesKey("interstitial_last_shown_ms")
}

/** A user can never bank more freezes than this, so the reward stays worth watching an ad for. */
const val MAX_STREAK_FREEZES = 2
