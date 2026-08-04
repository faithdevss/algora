package com.algora.app.core.data.entitlement

// How a topic may be read right now. Everything premium-related in the UI keys off this.
sealed interface TopicAccess {
    // Not a premium topic — always readable.
    data object Open : TopicAccess

    // Premium purchased: everything is readable, forever, ad-free.
    data object Owned : TopicAccess

    // Temporarily readable because the user watched a rewarded ad for this specific topic.
    data class AdUnlocked(val expiresAt: Long) : TopicAccess

    // Premium topic, no purchase, no live ad unlock — show the paywall.
    data object Locked : TopicAccess
}

const val AD_UNLOCK_DURATION_MS = 6L * 60 * 60 * 1000

// Pure resolution used by both the repository and its unit test. `unlocks` maps topicId → expiry.
fun accessOf(
    isPremiumTopic: Boolean,
    premiumOwned: Boolean,
    unlocks: Map<String, Long>,
    topicId: String,
    now: Long,
): TopicAccess = when {
    !isPremiumTopic -> TopicAccess.Open
    premiumOwned -> TopicAccess.Owned
    else -> unlocks[topicId]?.takeIf { it > now }?.let { TopicAccess.AdUnlocked(it) } ?: TopicAccess.Locked
}

// Serialization for the preference set, matching the SRS convention in core/data/settings.
fun serializeUnlock(topicId: String, expiresAt: Long): String = "$topicId|$expiresAt"

fun parseUnlock(entry: String): Pair<String, Long>? {
    val parts = entry.split("|")
    if (parts.size != 2) return null
    val expiry = parts[1].toLongOrNull() ?: return null
    return parts[0] to expiry
}
