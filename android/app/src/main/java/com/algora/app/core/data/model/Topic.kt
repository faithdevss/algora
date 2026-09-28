package com.algora.app.core.data.model

enum class Difficulty { BEGINNER, INTERMEDIATE, ADVANCED }

data class Topic(
    val id: String,
    val name: String,
    val categoryId: String,
    val tagline: String,
    val description: String,
    val iconName: String,
    val accentColor: Long,
    val isPremium: Boolean = false,
    val difficulty: Difficulty? = null,
)
