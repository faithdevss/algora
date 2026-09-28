package com.algora.app.core.data.model

enum class Section {
    // DSA mode
    DATA_STRUCTURES, ALGORITHMS, ANALYSIS, INTERVIEW_PREP,
    // AI mode (reserved for Phase 5)
    ML, DL, NLP, RL,
}

data class Category(
    val id: String,
    val name: String,
    val section: Section,
    val accentColor: Long,
    val iconName: String,
)
