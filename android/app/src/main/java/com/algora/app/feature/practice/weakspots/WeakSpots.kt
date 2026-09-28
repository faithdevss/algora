package com.algora.app.feature.practice.weakspots

import com.algora.app.core.data.settings.QuizAttempt
import com.algora.app.core.data.settings.questionKey
import com.algora.app.feature.interviewprep.quiz.Quiz
import com.algora.app.feature.practice.daily.DrillQuestion
import kotlin.random.Random

// Weak spots: every answered question grouped by the pattern it tests, weakest first, and a drill
// built from the weakest few. Pure functions over the question-results store, so the ranking and
// the pick are unit-testable without a DataStore.

// The synthetic quiz's id. It is never scored against a history — each drill is a different set —
// so the id exists only to satisfy QuizScreen and analytics.
const val WEAK_SPOT_QUIZ_ID = "weak_spot_drill"

const val WEAK_SPOT_DRILL_SIZE = 10

// A tag needs this many answers before its percentage means anything; one wrong answer out of one
// is not a weak spot.
const val WEAK_SPOT_MIN_ANSWERS = 3

// At or above this, a pattern is not flagged however it ranks — the card should say "nothing weak"
// rather than invent a weakness out of 85%.
const val WEAK_SPOT_THRESHOLD_PERCENT = 80

// "Scenario · Heap", "Picture · Heap" and "Story · Heap" test the same pattern; the prefix says how
// the question is framed, not what it tests.
private val framingPrefixes = listOf("Scenario · ", "Picture · ", "Story · ")

fun normalizeTag(tag: String): String =
    framingPrefixes.firstOrNull { tag.startsWith(it) }?.let { tag.removePrefix(it) } ?: tag

data class TagStat(val tag: String, val answered: Int, val correct: Int) {
    val percent: Int get() = if (answered == 0) 0 else correct * 100 / answered
}

/**
 * The stored results, backfilled from timed attempts made before per-question results existed. Only
 * the newest attempt per quiz is read — an answer fixed on the last run is not still wrong — and only
 * the positions it actually covered, since a set may have grown since (see ofLength).
 */
fun effectiveResults(
    stored: Map<String, Boolean>,
    quizzes: List<Pair<String, Quiz>>,
    attempts: Map<String, List<QuizAttempt>>,
): Map<String, Boolean> {
    val merged = stored.toMutableMap()
    quizzes.forEach { (quizId, quiz) ->
        val newest = attempts[quizId]?.firstOrNull() ?: return@forEach
        val wrong = newest.wrongIndices.toSet()
        for (i in 0 until minOf(newest.total, quiz.questions.size)) {
            merged.putIfAbsent(questionKey(quizId, i), i !in wrong)
        }
    }
    return merged
}

fun tagStats(results: Map<String, Boolean>, quizzes: List<Pair<String, Quiz>>): List<TagStat> {
    val answered = mutableMapOf<String, Int>()
    val correct = mutableMapOf<String, Int>()
    quizzes.forEach { (quizId, quiz) ->
        quiz.questions.forEachIndexed { i, question ->
            val result = results[questionKey(quizId, i)] ?: return@forEachIndexed
            val tag = normalizeTag(question.patternTag)
            answered[tag] = (answered[tag] ?: 0) + 1
            if (result) correct[tag] = (correct[tag] ?: 0) + 1
        }
    }
    return answered.map { (tag, n) -> TagStat(tag, n, correct[tag] ?: 0) }
}

/** Weakest first; ties go to the tag with more evidence behind it. */
fun weakestTags(
    stats: List<TagStat>,
    limit: Int = 3,
    minAnswers: Int = WEAK_SPOT_MIN_ANSWERS,
    threshold: Int = WEAK_SPOT_THRESHOLD_PERCENT,
): List<TagStat> = stats
    .filter { it.answered >= minAnswers && it.percent < threshold }
    .sortedWith(compareBy<TagStat> { it.percent }.thenByDescending { it.answered }.thenBy { it.tag })
    .take(limit)

/**
 * Questions for the drill, from the given tags only: ones answered wrong last time first, then ones
 * never answered, then ones already right — so a drill on a thin tag still fills up rather than
 * coming back half-empty.
 */
fun pickWeakSpotQuestions(
    tags: Set<String>,
    quizzes: List<Pair<String, Quiz>>,
    results: Map<String, Boolean>,
    count: Int = WEAK_SPOT_DRILL_SIZE,
    random: Random = Random.Default,
): List<DrillQuestion> {
    val missed = mutableListOf<DrillQuestion>()
    val unseen = mutableListOf<DrillQuestion>()
    val right = mutableListOf<DrillQuestion>()
    quizzes.forEach { (quizId, quiz) ->
        quiz.questions.forEachIndexed { i, question ->
            if (normalizeTag(question.patternTag) !in tags) return@forEachIndexed
            val entry = DrillQuestion(quizId, i, question)
            when (results[questionKey(quizId, i)]) {
                false -> missed += entry
                null -> unseen += entry
                true -> right += entry
            }
        }
    }
    return (missed.shuffled(random) + unseen.shuffled(random) + right.shuffled(random)).take(count)
}

fun weakSpotQuiz(questions: List<DrillQuestion>, tags: List<String>): Quiz = Quiz(
    id = WEAK_SPOT_QUIZ_ID,
    title = "Weak-spot drill",
    description = "Questions from the patterns you miss most: ${tags.joinToString(", ")}.",
    questions = questions.map { it.question },
)
