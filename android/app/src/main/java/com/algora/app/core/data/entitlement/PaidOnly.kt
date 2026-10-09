package com.algora.app.core.data.entitlement

/**
 * Premium topics a rewarded ad never opens: everything outside [AccessTiers.adUnlockIds]. Problem
 * groups are not listed here. Their ids share a namespace with Learning topics (`sliding_window` is
 * both), so they are gated where they are checked — ProblemListScreen and ProblemDetailScreen —
 * rather than by id, which would lock the Learning topic too.
 */
object PaidOnly {
    fun isPaidOnlyTopic(topicId: String): Boolean = !AccessTiers.isAdUnlockable(topicId)
}
