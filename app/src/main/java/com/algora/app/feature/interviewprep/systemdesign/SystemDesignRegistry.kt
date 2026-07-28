package com.algora.app.feature.interviewprep.systemdesign

const val SYSTEM_DESIGN_PRIMER_ID = "system_design_primer"

// Maps a topic id to its system-design primer, mirroring QuizRegistry / BehavioralRegistry.
object SystemDesignRegistry {
    private val primers: Map<String, SystemDesignPrimer> = mapOf(
        SYSTEM_DESIGN_PRIMER_ID to systemDesignPrimer,
        "ml_system_design_primer" to mlSystemDesignPrimer,
    )

    fun get(topicId: String): SystemDesignPrimer? = primers[topicId]
}
