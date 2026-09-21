package com.algora.app.core.analytics

import com.algora.app.core.data.CategoryRegistry
import com.algora.app.core.data.model.Topic

/**
 * The app's event log, and the only thing screens are allowed to call.
 *
 * Firebase gives retention cohorts and screen views for free, but not *why* a cohort left, so this
 * layer exists to answer one question: which surfaces a learner actually reaches before they stop
 * coming back. The event set is deliberately small — every event below is one a retention decision
 * already depends on (see the typed functions at the bottom of this file for what each one is for).
 * Adding a "might be interesting" event costs a Firebase parameter slot and buys nothing.
 *
 * `log` is raw on purpose: nothing calls it directly except the typed extensions below, so event
 * names and parameter keys live in exactly one place and cannot be misspelled at a call site.
 */
interface Analytics {

    /** Values may be String, Int, Long or Double — anything else is dropped by the Firebase client. */
    fun log(event: String, params: Map<String, Any> = emptyMap())

    /** Sticky segmentation, attached to every subsequent event. Kept to the two below. */
    fun setUserProperty(name: String, value: String)
}

// Event names. Firebase rules: <=40 chars, letters/digits/underscore, no `firebase_`/`ga_` prefix.
object Event {
    const val TOPIC_OPENED = "topic_opened"
    const val TOPIC_COMPLETED = "topic_completed"
    const val LOCKED_TOPIC_HIT = "locked_topic_hit"
    const val PAYWALL_VIEW = "paywall_view"
    const val QUIZ_COMPLETED = "quiz_completed"
    const val DRILL_OPENED = "drill_opened"
    const val DRILL_COMPLETED = "drill_completed"
    const val REVIEW_SESSION = "review_session"
    const val STREAK_DAY = "streak_day"
    const val REMINDER_OPENED = "reminder_opened"
}

// Parameter keys. Same <=40 char rule; string values are truncated to 100 by the Firebase client.
object Param {
    const val TOPIC_ID = "topic_id"
    const val SECTION = "section"
    const val TIER = "tier"
    const val ACCESS = "access"
    const val QUIZ_ID = "quiz_id"
    const val SCORE_PCT = "score_pct"
    const val SECONDS = "seconds"
    const val CARDS = "cards"
    const val MODE = "mode"
    const val STREAK = "streak"
    const val KIND = "kind"
}

// User properties. Retention questions are always "for whom", and these are the two splits the app
// can actually act on: buyers vs free, and which half of the content someone lives in.
object UserProperty {
    const val IS_PREMIUM = "is_premium"
    const val APP_MODE = "app_mode"
}

/** How a reader got at a topic's content — the denominator every paywall question needs. */
enum class TopicAccessKind(val value: String) {
    FREE("free"),
    OWNED("owned"),
    AD_UNLOCKED("ad_unlocked"),
}

// A topic's section (DATA_STRUCTURES, ML, …) rather than its raw category, so the funnel groups the
// way the nav does. Unknown only if a topic outlives its category, which CategoryIntegrityTest bars.
private fun Topic.sectionName(): String =
    CategoryRegistry.find(categoryId)?.section?.name ?: "unknown"

private fun Topic.tier(): String = if (isPremium) "premium" else "free"

// --- The typed surface. Each function is one question. ---

/** Which topics get opened at all — the head of every content funnel. */
fun Analytics.topicOpened(topic: Topic, access: TopicAccessKind) = log(
    Event.TOPIC_OPENED,
    mapOf(
        Param.TOPIC_ID to topic.id,
        Param.SECTION to topic.sectionName(),
        Param.TIER to topic.tier(),
        Param.ACCESS to access.value,
    ),
)

/** Opened vs finished, per topic: where readers stall inside the content. */
fun Analytics.topicCompleted(topic: Topic) = log(
    Event.TOPIC_COMPLETED,
    mapOf(Param.TOPIC_ID to topic.id, Param.SECTION to topic.sectionName()),
)

/** Which specific locks bite. Names the topics worth moving across the free/premium line. */
fun Analytics.lockedTopicHit(topic: Topic) = log(
    Event.LOCKED_TOPIC_HIT,
    mapOf(Param.TOPIC_ID to topic.id, Param.SECTION to topic.sectionName()),
)

/** Paywall reach, as the denominator for purchases. No source yet — nothing routes one in. */
fun Analytics.paywallView() = log(Event.PAYWALL_VIEW)

/**
 * Finished quiz runs. No separate "is this the drill" flag: the daily drill runs under one fixed
 * quiz id, so splitting it out is a filter on `quiz_id` — and it must always be split out, because
 * only sit-down sets carry the exit interstitial. Pooling the two would hide whatever that ad costs.
 */
fun Analytics.quizCompleted(quizId: String, scorePct: Int, seconds: Int) = log(
    Event.QUIZ_COMPLETED,
    mapOf(Param.QUIZ_ID to quizId, Param.SCORE_PCT to scorePct, Param.SECONDS to seconds),
)

/** Drill reach vs drill finish — the habit loop's own funnel, and the reminder's target. */
fun Analytics.drillOpened() = log(Event.DRILL_OPENED)

fun Analytics.drillCompleted() = log(Event.DRILL_COMPLETED)

/** Flashcard sessions and their size. `mode` splits real due work from studying ahead. */
fun Analytics.reviewSession(cardsReviewed: Int, studyingAhead: Boolean) = log(
    Event.REVIEW_SESSION,
    mapOf(
        Param.CARDS to cardsReviewed,
        Param.MODE to if (studyingAhead) "study_ahead" else "due",
    ),
)

/** Streak length at each active day: the retention curve the app itself can see. */
fun Analytics.streakDay(streak: Int) = log(Event.STREAK_DAY, mapOf(Param.STREAK to streak))

/**
 * A notification that was actually tapped. Posting is not the outcome that matters — a reminder
 * nobody opens is a reminder that costs uninstalls for nothing — and `kind` says which of the
 * nudges earned the tap.
 */
fun Analytics.reminderOpened(kind: String) = log(Event.REMINDER_OPENED, mapOf(Param.KIND to kind))
