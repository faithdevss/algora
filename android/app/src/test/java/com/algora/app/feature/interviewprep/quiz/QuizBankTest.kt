package com.algora.app.feature.interviewprep.quiz

import com.algora.app.core.data.TopicRegistry
import com.algora.app.core.data.model.Difficulty
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// Quizzes are hand-authored data reached through a topic id. These guard the wiring: a quiz whose
// key has no topic is unreachable, and a correctIndex out of range would mark every answer wrong.
class QuizBankTest {

    @Test
    fun `every quiz key resolves to a topic`() {
        val orphans = QuizRegistry.all.map { it.first }.filter { TopicRegistry.find(it) == null }
        assertTrue("Quiz keys with no matching topic: $orphans", orphans.isEmpty())
    }

    @Test
    fun `every quiz id matches its registry key`() {
        val mismatched = QuizRegistry.all.filter { (key, quiz) -> quiz.id != key }.map { it.first }
        assertTrue("Quizzes whose id differs from their key: $mismatched", mismatched.isEmpty())
    }

    @Test
    fun `every question is answerable`() {
        val broken = QuizRegistry.all.flatMap { (key, quiz) ->
            quiz.questions.filter { q ->
                q.options.size < 2 ||
                    q.correctIndex !in q.options.indices ||
                    q.prompt.isBlank() ||
                    q.explanation.isBlank()
            }.map { "$key: ${it.prompt.take(40)}" }
        }
        assertTrue("Malformed questions: $broken", broken.isEmpty())
    }

    @Test
    fun `every question cross-link resolves to a topic`() {
        val broken = QuizRegistry.all.flatMap { (key, quiz) ->
            quiz.questions
                .mapNotNull { it.linkedTopicId }
                .filter { TopicRegistry.find(it) == null }
                .map { "$key -> $it" }
        }
        assertTrue("Question links with no matching topic: $broken", broken.isEmpty())
    }

    @Test
    fun `every quiz has questions and a positive time limit`() {
        val broken = QuizRegistry.all.filter { (_, quiz) ->
            quiz.questions.isEmpty() || quiz.timeLimitSeconds <= 0 || quiz.title.isBlank()
        }.map { it.first }
        assertTrue("Quizzes missing questions, a title or a time limit: $broken", broken.isEmpty())
    }

    @Test
    fun `the catalog has grown past the original four sets`() {
        assertTrue("Expected at least 23 quizzes, found ${QuizRegistry.all.size}", QuizRegistry.all.size >= 23)
    }

    @Test
    fun `no two quizzes share a title`() {
        val duplicates = QuizRegistry.all.groupBy { it.second.title }.filter { it.value.size > 1 }.keys
        assertTrue("Quiz titles used more than once: $duplicates", duplicates.isEmpty())
    }

    // The whole bank is authored correct-first, so the render-time shuffle is the only thing standing
    // between a learner and a perfect score from tapping option A six times. These pin its contract.

    @Test
    fun `shuffling preserves every option and keeps the answer pointing at the same text`() {
        val random = Random(7)
        val broken = QuizRegistry.all.flatMap { (key, quiz) ->
            quiz.questions.zip(quiz.withShuffledOptions(random).questions).mapIndexedNotNull { i, (before, after) ->
                val sameOptions = before.options.sorted() == after.options.sorted()
                val sameAnswer = before.options[before.correctIndex] == after.options[after.correctIndex]
                "$key q$i".takeIf { !sameOptions || !sameAnswer }
            }
        }
        assertTrue("Shuffle lost options or moved the answer: $broken", broken.isEmpty())
    }

    @Test
    fun `shuffling moves the answer off the authored first position`() {
        val questions = QuizRegistry.all.flatMap { it.second.withShuffledOptions(Random(11)).questions }
        val moved = questions.count { it.correctIndex != 0 }
        // Four options shuffled uniformly leaves ~25% still at A; anything near zero means the shuffle
        // silently returned the question as authored.
        assertTrue(
            "Only $moved of ${questions.size} questions moved off option A — shuffle is not permuting",
            moved > questions.size / 2,
        )
    }

    @Test
    fun `shuffling leaves everything except the options untouched`() {
        val broken = QuizRegistry.all.flatMap { (key, quiz) ->
            val shuffled = quiz.withShuffledOptions(Random(3))
            quiz.questions.zip(shuffled.questions).mapIndexedNotNull { i, (before, after) ->
                "$key q$i".takeIf {
                    before.copy(options = emptyList(), correctIndex = 0) !=
                        after.copy(options = emptyList(), correctIndex = 0)
                }
            } + listOfNotNull("$key metadata".takeIf { quiz.copy(questions = emptyList()) != shuffled.copy(questions = emptyList()) })
        }
        assertTrue("Shuffle altered fields it should not touch: $broken", broken.isEmpty())
    }

    @Test
    fun `every quiz gets the same seconds per question`() {
        // The limit is derived, so this cannot drift the way the authored one did — it guards the
        // derivation itself, and the floor that keeps a one-question retry runnable.
        val wrong = QuizRegistry.all.filter { (_, quiz) ->
            quiz.timeLimitSeconds != maxOf(quiz.questions.size * SECONDS_PER_QUESTION, 60)
        }.map { it.first }
        assertTrue("Quizzes not on the shared per-question budget: $wrong", wrong.isEmpty())
    }

    @Test
    fun `no question is repeated across the bank`() {
        // Premium sets used to restate questions from the free mock almost verbatim. Prompts differ
        // enough to slip an exact-match check, so this compares the answer plus its option set.
        val duplicates = QuizRegistry.all
            .flatMap { (key, quiz) -> quiz.questions.map { it.options.sorted() to key } }
            .groupBy({ it.first }, { it.second })
            .filter { it.value.size > 1 }
            .map { "${it.value}: ${it.key.first().take(50)}" }
        assertTrue("Questions sharing an option set across quizzes: $duplicates", duplicates.isEmpty())
    }

    @Test
    fun `a question with an out-of-range answer passes through unshuffled`() {
        val malformed = QuizQuestion(
            prompt = "p",
            options = listOf("a", "b"),
            correctIndex = 5,
            patternTag = "t",
            difficulty = Difficulty.BEGINNER,
            explanation = "e",
        )
        assertEquals(malformed, malformed.withShuffledOptions(Random(1)))
    }
}
