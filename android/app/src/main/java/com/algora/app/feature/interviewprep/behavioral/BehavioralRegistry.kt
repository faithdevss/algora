package com.algora.app.feature.interviewprep.behavioral

// Maps a topic id to its behavioral bank, mirroring QuizRegistry. Topics listed here render
// BehavioralScreen instead of the 7-section template.
object BehavioralRegistry {
    private val banks: Map<String, BehavioralBank> = mapOf(
        "behavioral_question_bank" to behavioralBank,
    )

    fun get(topicId: String): BehavioralBank? = banks[topicId]
}
