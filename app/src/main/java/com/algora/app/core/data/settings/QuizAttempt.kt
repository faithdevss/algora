package com.algora.app.core.data.settings

// One finished run of a quiz. Persisted as "quizId|atEpochSec|correct|total|seconds|wrongIndices"
// in the QUIZ_ATTEMPTS preference set — same shape as SrsCard's encoding, all integers so no
// locale-dependent formatting can creep in.
//
// The timestamp is a second, not an epoch-day: DataStore stores these in a *set*, so two runs of the
// same quiz on the same day scoring the same would collapse into one entry if the day were the only
// thing separating them.
//
// `wrongIndices` are positions into the quiz's own question list, which is what makes a
// wrong-answers-only retry possible without storing the questions themselves.
data class QuizAttempt(
    val atEpochSec: Long,
    val correct: Int,
    val total: Int,
    val seconds: Int,
    val wrongIndices: List<Int>,
) {
    val percent: Int get() = if (total == 0) 0 else correct * 100 / total
    val day: Long get() = atEpochSec / 86_400L
}

// Only the most recent runs of each quiz are kept. Older ones answer no question the newest ones
// cannot — and an unbounded set would grow with every retry.
const val QUIZ_ATTEMPTS_PER_QUIZ = 5

fun QuizAttempt.serialize(quizId: String): String =
    "$quizId|$atEpochSec|$correct|$total|$seconds|${wrongIndices.joinToString(",")}"

fun parseQuizAttempt(entry: String): Pair<String, QuizAttempt>? {
    // limit = 6 so the trailing index list keeps its own commas and a malformed tail cannot add
    // fields; a quiz id containing '|' would still break, which is why ids are plain snake_case.
    val parts = entry.split("|", limit = 6)
    if (parts.size != 6) return null
    return try {
        parts[0] to QuizAttempt(
            atEpochSec = parts[1].toLong(),
            correct = parts[2].toInt(),
            total = parts[3].toInt(),
            seconds = parts[4].toInt(),
            wrongIndices = parts[5].split(",").mapNotNull { it.trim().toIntOrNull() },
        )
    } catch (e: NumberFormatException) {
        null
    }
}

// Newest first, so "last attempt" is `first()` and trimming to the cap is `take(n)`.
fun List<QuizAttempt>.newestFirst(): List<QuizAttempt> = sortedByDescending { it.atEpochSec }

// Runs recorded before a set grew were scored out of a different total. Showing them would turn an old
// 5/5 into "Best 5/15", so a set's history only counts runs of its current length. The stored runs
// are left alone — they simply age out of the per-quiz cap.
fun List<QuizAttempt>.ofLength(questionCount: Int): List<QuizAttempt> = filter { it.total == questionCount }

val List<QuizAttempt>.bestPercent: Int get() = maxOfOrNull { it.percent } ?: 0

val List<QuizAttempt>.bestCorrect: Int get() = maxByOrNull { it.percent }?.correct ?: 0
