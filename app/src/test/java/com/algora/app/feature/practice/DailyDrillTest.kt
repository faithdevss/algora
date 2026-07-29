package com.algora.app.feature.practice

import com.algora.app.core.data.model.Difficulty
import com.algora.app.core.data.settings.QuizAttempt
import com.algora.app.feature.interviewprep.quiz.QuizRegistry
import com.algora.app.feature.practice.daily.DAILY_DRILL_QUIZ_ID
import com.algora.app.feature.practice.daily.DRILL_QUESTION_COUNT
import com.algora.app.feature.practice.daily.asQuiz
import com.algora.app.feature.practice.daily.buildDailyDrill
import com.algora.app.feature.practice.daily.pickProblem
import com.algora.app.feature.practice.daily.pickQuestions
import com.algora.app.feature.practice.problems.ProblemRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// The drill's whole contract is "the same set all day, a different one tomorrow, never work you have
// already done". None of that is visible from the UI until a day has passed, so it is pinned here.
class DailyDrillTest {

    private val day = 20_600L
    private val problems = ProblemRegistry.all
    private val quizzes = QuizRegistry.all

    @Test
    fun `the same day builds the same drill`() {
        val a = buildDailyDrill(day, problems, emptySet(), quizzes, emptyMap())
        val b = buildDailyDrill(day, problems, emptySet(), quizzes, emptyMap())
        assertEquals(a.problem?.id, b.problem?.id)
        assertEquals(a.questions, b.questions)
    }

    // A drill that rerolled overnight would be indistinguishable from one that never advanced.
    @Test
    fun `consecutive days differ`() {
        val today = buildDailyDrill(day, problems, emptySet(), quizzes, emptyMap())
        val tomorrow = buildDailyDrill(day + 1, problems, emptySet(), quizzes, emptyMap())
        assertNotEquals(today.questions, tomorrow.questions)
    }

    @Test
    fun `never picks a solved problem`() {
        var solved = emptySet<String>()
        repeat(25) { offset ->
            val picked = pickProblem(day + offset, problems, solved)!!
            assertTrue(picked.id !in solved)
            solved = solved + picked.id
        }
    }

    @Test
    fun `a fully solved bank yields no problem rather than a repeat`() {
        assertNull(pickProblem(day, problems, problems.map { it.id }.toSet()))
    }

    @Test
    fun `difficulty ramps with the solved count`() {
        assertEquals(Difficulty.BEGINNER, pickProblem(day, problems, emptySet())!!.difficulty)

        val fifteenSolved = problems.take(15).map { it.id }.toSet()
        assertEquals(Difficulty.INTERMEDIATE, pickProblem(day, problems, fifteenSolved)!!.difficulty)

        val fiftySolved = problems.take(50).map { it.id }.toSet()
        assertEquals(Difficulty.ADVANCED, pickProblem(day, problems, fiftySolved)!!.difficulty)
    }

    @Test
    fun `questions are drawn up to the count`() {
        val questions = pickQuestions(day, quizzes, emptyMap())
        assertEquals(DRILL_QUESTION_COUNT, questions.size)
        assertEquals(DRILL_QUESTION_COUNT, questions.distinct().size)
        // Every sampled question must still resolve inside the quiz it claims to come from.
        questions.forEach { drilled ->
            val quiz = quizzes.first { it.first == drilled.quizId }.second
            assertEquals(quiz.questions[drilled.index], drilled.question)
        }
    }

    @Test
    fun `previously missed questions come first`() {
        val (quizId, quiz) = quizzes.first { it.second.questions.size >= 4 }
        val missed = listOf(0, 1, 2, 3)
        val attempts = mapOf(
            quizId to listOf(
                QuizAttempt(atEpochSec = day * 86_400, correct = 0, total = quiz.questions.size, seconds = 60, wrongIndices = missed),
            ),
        )
        val picked = pickQuestions(day, quizzes, attempts)
        val fromMissed = picked.count { it.quizId == quizId && it.index in missed }
        assertEquals(missed.size, fromMissed)
        assertEquals(DRILL_QUESTION_COUNT, picked.size)
    }

    // Only the newest run counts: a question fixed on the last attempt is no longer "missed".
    @Test
    fun `an older attempt's misses are ignored`() {
        val (quizId, quiz) = quizzes.first { it.second.questions.size >= 4 }
        val attempts = mapOf(
            quizId to listOf(
                QuizAttempt(day * 86_400, quiz.questions.size, quiz.questions.size, 60, emptyList()),
                QuizAttempt((day - 3) * 86_400, 0, quiz.questions.size, 60, listOf(0, 1, 2, 3)),
            ),
        )
        val picked = pickQuestions(day, quizzes, attempts)
        val stale = picked.count { it.quizId == quizId && it.index in listOf(0, 1, 2, 3) }
        // Nothing is *forbidden* from being drawn again, but it can no longer be prioritised, so a
        // full drill of exactly the stale four would be a bug.
        assertTrue(stale < DRILL_QUESTION_COUNT)
    }

    @Test
    fun `no accessible quizzes yields no questions`() {
        assertEquals(emptyList<Any>(), pickQuestions(day, emptyList(), emptyMap()))
    }

    @Test
    fun `the drill renders as a runnable quiz`() {
        val quiz = buildDailyDrill(day, problems, emptySet(), quizzes, emptyMap()).asQuiz()
        assertEquals(DAILY_DRILL_QUIZ_ID, quiz.id)
        assertEquals(DRILL_QUESTION_COUNT, quiz.questions.size)
        assertTrue(quiz.timeLimitSeconds > 0)
    }
}
