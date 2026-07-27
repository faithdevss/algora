package com.algora.app.feature.interviewprep.quiz

import com.algora.app.core.data.TopicRegistry
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
        assertTrue("Expected at least 16 quizzes, found ${QuizRegistry.all.size}", QuizRegistry.all.size >= 16)
    }
}
