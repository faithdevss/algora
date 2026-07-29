package com.algora.app.feature.practice.daily

import com.algora.app.core.data.model.Difficulty
import com.algora.app.core.data.settings.QuizAttempt
import com.algora.app.feature.interviewprep.quiz.Quiz
import com.algora.app.feature.interviewprep.quiz.QuizQuestion
import com.algora.app.feature.practice.problems.PracticeProblem
import kotlin.random.Random

// One sampled question, kept with the quiz it came from so a miss can be traced back to a real set.
data class DrillQuestion(val quizId: String, val index: Int, val question: QuizQuestion)

// Today's practice set: recall (handled by the SM-2 queue itself), one problem, and a short mixed
// quiz. Deterministic in `day` — reopening the tab must not reroll the set, or "1 of 3 done" would
// describe work the learner never saw.
data class DailyDrill(
    val day: Long,
    val problem: PracticeProblem?,
    val questions: List<DrillQuestion>,
) {
    val hasWork: Boolean get() = problem != null || questions.isNotEmpty()
}

// Questions per drill. Short on purpose: the drill has to be finishable in one sitting, or the
// streak it feeds becomes a chore.
const val DRILL_QUESTION_COUNT = 5

// The synthetic quiz's id. Fixed rather than per-day, so its attempts trim like any other quiz's
// instead of leaving one dead entry per day in the preference set.
const val DAILY_DRILL_QUIZ_ID = "daily_drill"

// Solved-count thresholds for the problem step's difficulty ramp.
private const val EASY_UNTIL_SOLVED = 10
private const val MEDIUM_UNTIL_SOLVED = 40

fun buildDailyDrill(
    day: Long,
    problems: List<PracticeProblem>,
    solvedIds: Set<String>,
    quizzes: List<Pair<String, Quiz>>,
    attempts: Map<String, List<QuizAttempt>>,
): DailyDrill = DailyDrill(
    day = day,
    problem = pickProblem(day, problems, solvedIds),
    questions = pickQuestions(day, quizzes, attempts),
)

/**
 * One unsolved problem, at the difficulty the learner has earned. Falls back to any unsolved problem
 * when that difficulty is exhausted, and to null only when the whole bank is solved.
 */
fun pickProblem(day: Long, problems: List<PracticeProblem>, solvedIds: Set<String>): PracticeProblem? {
    val unsolved = problems.filter { it.id !in solvedIds }
    if (unsolved.isEmpty()) return null

    val solvedCount = problems.count { it.id in solvedIds }
    val target = when {
        solvedCount < EASY_UNTIL_SOLVED -> Difficulty.BEGINNER
        solvedCount < MEDIUM_UNTIL_SOLVED -> Difficulty.INTERMEDIATE
        else -> Difficulty.ADVANCED
    }
    val candidates = unsolved.filter { it.difficulty == target }.ifEmpty { unsolved }
    // Sorted by id first so the pick depends on the day alone, not on the order content files
    // happen to be aggregated in.
    return candidates.sortedBy { it.id }[Random(day).nextInt(candidates.size)]
}

/**
 * Questions you got wrong before come first — that is the whole point of keeping wrong indices — and
 * the rest of the set is filled with questions no attempt has covered yet.
 */
fun pickQuestions(
    day: Long,
    quizzes: List<Pair<String, Quiz>>,
    attempts: Map<String, List<QuizAttempt>>,
    count: Int = DRILL_QUESTION_COUNT,
): List<DrillQuestion> {
    if (quizzes.isEmpty() || count <= 0) return emptyList()

    // Only the newest attempt per quiz counts: an index fixed on the last run is not still missed.
    val missedKeys = attempts.mapValues { (_, runs) -> runs.firstOrNull()?.wrongIndices.orEmpty().toSet() }

    val missed = mutableListOf<DrillQuestion>()
    val fresh = mutableListOf<DrillQuestion>()
    quizzes.forEach { (quizId, quiz) ->
        val wrong = missedKeys[quizId].orEmpty()
        quiz.questions.forEachIndexed { i, question ->
            val entry = DrillQuestion(quizId, i, question)
            if (i in wrong) missed += entry else fresh += entry
        }
    }

    // One generator for both draws, so the fill is not correlated with the missed pick.
    val random = Random(day)
    val picked = missed.shuffled(random).take(count).toMutableList()
    if (picked.size < count) {
        picked += fresh.shuffled(random).take(count - picked.size)
    }
    return picked
}

// The sampled questions as a runnable Quiz. 45s per question matches the pace of the authored sets
// (a 10-question set gets 8 minutes), and the title says what it is on the results screen.
fun DailyDrill.asQuiz(): Quiz = Quiz(
    id = DAILY_DRILL_QUIZ_ID,
    title = "Daily drill",
    description = "Mixed questions, weighted towards what you have missed before.",
    timeLimitSeconds = questions.size * 45,
    questions = questions.map { it.question },
)
