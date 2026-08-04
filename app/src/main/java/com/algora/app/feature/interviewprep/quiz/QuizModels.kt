package com.algora.app.feature.interviewprep.quiz

import com.algora.app.core.data.model.Difficulty
import kotlin.random.Random

// A single multiple-choice practice question, tagged by pattern, with an optional cross-link back to
// the Algorithms topic it drills. Questions used to carry a companyTag rendering a "Google"/"Meta"
// chip; these are authored questions, not transcripts, so the chip claimed a provenance that does
// not exist — and within a set it only ever repeated what the set's own title already said.
data class QuizQuestion(
    val prompt: String,
    val options: List<String>,
    val correctIndex: Int,
    val patternTag: String,
    val difficulty: Difficulty,
    val explanation: String,
    val linkedTopicId: String? = null,
    val linkedTopicLabel: String? = null,
)

// One budget for every set, so a five-question premium set can no longer be more generous per
// question than the free mock it is sold against. The floor keeps a one-question "retry missed" run
// from being a six-second scramble.
internal const val SECONDS_PER_QUESTION = 45

// A timed set of questions. timeLimitSeconds drives the countdown; expiry auto-submits. It is
// derived rather than authored: a hand-written limit is one more field to keep in step with the
// question list, and it drifted (84s/question for the company sets against 40s for the subject
// quizzes) before it was derived here.
data class Quiz(
    val id: String,
    val title: String,
    val description: String,
    val questions: List<QuizQuestion>,
) {
    val timeLimitSeconds: Int get() = (questions.size * SECONDS_PER_QUESTION).coerceAtLeast(60)
}

// Every question in the bank is authored with the correct option written first, which reads well in
// source but means an unshuffled run is aced by always tapping option A. QuizRunner permutes each
// question once per attempt so the position carries no signal; the answer is tracked by identity,
// not index, so the explanation and the results screen still point at the right option.
internal fun Quiz.withShuffledOptions(random: Random = Random.Default): Quiz =
    copy(questions = questions.map { it.withShuffledOptions(random) })

internal fun QuizQuestion.withShuffledOptions(random: Random = Random.Default): QuizQuestion {
    // A malformed question would map its answer to -1 and mark every choice wrong; QuizBankTest
    // rejects those, so here it just passes through untouched.
    if (correctIndex !in options.indices) return this
    val order = options.indices.shuffled(random)
    return copy(
        options = order.map { options[it] },
        correctIndex = order.indexOf(correctIndex),
    )
}
