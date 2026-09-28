package com.algora.app.feature.interviewprep.behavioral

// A behavioral interview prompt with coaching — no single "correct answer", unlike a QuizQuestion.
data class BehavioralQuestion(
    val prompt: String,
    val category: String,      // Leadership, Conflict, Failure, …
    val assesses: String,      // what the interviewer is really probing
    val starTip: String,       // how to shape a STAR answer for this prompt
)

data class BehavioralBank(
    val id: String,
    val title: String,
    val description: String,
    val questions: List<BehavioralQuestion>,
)
