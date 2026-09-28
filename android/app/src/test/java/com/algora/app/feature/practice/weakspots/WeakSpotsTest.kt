package com.algora.app.feature.practice.weakspots

import com.algora.app.core.data.model.Difficulty
import com.algora.app.core.data.settings.QuizAttempt
import com.algora.app.core.data.settings.questionKey
import com.algora.app.feature.interviewprep.quiz.Quiz
import com.algora.app.feature.interviewprep.quiz.QuizQuestion
import com.algora.app.feature.interviewprep.quiz.QuizRegistry
import com.algora.app.feature.review.quizMistakeCards
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WeakSpotsTest {

    private fun q(tag: String, prompt: String = tag) = QuizQuestion(
        prompt = prompt,
        options = listOf("a", "b"),
        correctIndex = 0,
        patternTag = tag,
        difficulty = Difficulty.BEGINNER,
        explanation = "e",
    )

    private val quizA = Quiz("a", "A", "", listOf(q("Heap", "a0"), q("Scenario · Heap", "a1"), q("Graphs", "a2"), q("Graphs", "a3")))
    private val quizB = Quiz("b", "B", "", listOf(q("Picture · Heap", "b0"), q("Graphs", "b1"), q("Story · Hashing", "b2")))
    private val quizzes = listOf("a" to quizA, "b" to quizB)

    @Test
    fun `framing prefixes collapse into one pattern`() {
        assertEquals("Heap", normalizeTag("Scenario · Heap"))
        assertEquals("Heap", normalizeTag("Picture · Heap"))
        assertEquals("Hashing", normalizeTag("Story · Hashing"))
        assertEquals("Top-K / Heap", normalizeTag("Top-K / Heap"))
    }

    @Test
    fun `stats group answers by normalised tag`() {
        val results = mapOf(
            questionKey("a", 0) to false, questionKey("a", 1) to false, questionKey("b", 0) to true,
            questionKey("a", 2) to true,
        )
        val stats = tagStats(results, quizzes).associateBy { it.tag }
        assertEquals(TagStat("Heap", answered = 3, correct = 1), stats["Heap"])
        assertEquals(TagStat("Graphs", answered = 1, correct = 1), stats["Graphs"])
        assertTrue("unanswered tags are not counted", "Hashing" !in stats)
    }

    @Test
    fun `weakest tags need enough answers and must be below the threshold`() {
        val stats = listOf(
            TagStat("Heap", answered = 5, correct = 1),
            TagStat("Graphs", answered = 2, correct = 0),
            TagStat("Hashing", answered = 10, correct = 9),
            TagStat("DP", answered = 4, correct = 2),
        )
        assertEquals(listOf("Heap", "DP"), weakestTags(stats).map { it.tag })
    }

    @Test
    fun `old timed attempts backfill results, only for positions they covered`() {
        // The set had 3 questions when this attempt ran; question 3 was added later.
        val attempts = mapOf("a" to listOf(QuizAttempt(1L, correct = 2, total = 3, seconds = 60, wrongIndices = listOf(1))))
        val merged = effectiveResults(stored = mapOf(questionKey("a", 0) to false), quizzes = quizzes, attempts = attempts)
        assertEquals(false, merged[questionKey("a", 0)]) // stored result wins over the backfill
        assertEquals(false, merged[questionKey("a", 1)])
        assertEquals(true, merged[questionKey("a", 2)])
        assertTrue(questionKey("a", 3) !in merged)
    }

    @Test
    fun `the drill takes missed questions first, then unseen, then already right`() {
        val results = mapOf(questionKey("a", 0) to true, questionKey("a", 1) to false)
        val picked = pickWeakSpotQuestions(setOf("Heap"), quizzes, results, count = 3, random = Random(1))
        assertEquals(listOf("a1", "b0", "a0"), picked.map { it.question.prompt })
        assertTrue(picked.all { normalizeTag(it.question.patternTag) == "Heap" })
    }

    @Test
    fun `the drill never pads with other patterns`() {
        val picked = pickWeakSpotQuestions(setOf("Hashing"), quizzes, emptyMap(), count = 10)
        assertEquals(listOf("b2"), picked.map { it.question.prompt })
    }

    @Test
    fun `mistake cards resolve to their real question and skip dead keys`() {
        val (quizId, quiz) = QuizRegistry.all.first()
        val question = quiz.questions[0]
        val cards = quizMistakeCards(listOf("quiz:${questionKey(quizId, 0)}", "quiz:no_such_quiz#0", "graph#1", "quiz:$quizId#9999"))
        assertEquals(1, cards.size)
        assertEquals(question.prompt, cards[0].prompt)
        assertEquals(question.options[question.correctIndex], cards[0].takeaway)
        assertEquals(question.explanation, cards[0].detail)
    }
}
