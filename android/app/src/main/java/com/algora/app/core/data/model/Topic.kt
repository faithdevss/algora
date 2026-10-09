package com.algora.app.core.data.model

import com.algora.app.core.data.entitlement.AccessTiers

enum class Difficulty { BEGINNER, INTERMEDIATE, ADVANCED }

data class Topic(
    val id: String,
    val name: String,
    val categoryId: String,
    val tagline: String,
    val description: String,
    val iconName: String,
    val accentColor: Long,
    val difficulty: Difficulty? = null,
) {
    // Derived from the one tier table, not authored per topic: see AccessTiers.
    val isPremium: Boolean get() = AccessTiers.isPremium(id)
}
