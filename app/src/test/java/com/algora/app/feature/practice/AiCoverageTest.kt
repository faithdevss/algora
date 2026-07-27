package com.algora.app.feature.practice

import com.algora.app.feature.deeplearning.DeepLearningTopics
import com.algora.app.feature.interviewprep.InterviewPrepTopics
import com.algora.app.feature.interviewprep.quiz.QuizRegistry
import com.algora.app.feature.machinelearning.MachineLearningTopics
import com.algora.app.feature.nlp.NlpTopics
import com.algora.app.feature.practice.problems.ProblemRegistry
import com.algora.app.feature.reinforcementlearning.ReinforcementLearningTopics
import com.algora.app.feature.review.allReviewCards
import org.junit.Assert.assertTrue
import org.junit.Test

// Every Practice surface must reach AI content, not only DSA. Content is hand-authored, so these
// guard against a surface silently drifting back to DSA-only as the banks grow.
class AiCoverageTest {

    private val aiTopicIds: Set<String> =
        (MachineLearningTopics.topics + DeepLearningTopics.topics + NlpTopics.topics + ReinforcementLearningTopics.topics)
            .map { it.id }
            .toSet()

    @Test
    fun `the problem bank has AI problems in every AI pattern group`() {
        val aiProblems = ProblemRegistry.all.filter { problem ->
            problem.linkedTopicId in aiTopicIds || problem.prerequisites.any { it.topicId in aiTopicIds }
        }
        assertTrue("Expected at least 12 AI problems, found ${aiProblems.size}", aiProblems.size >= 12)

        val aiPatternIds = listOf("ml_foundations", "neural_nets", "nlp_pipeline", "rl_control")
        val empty = aiPatternIds.filter { ProblemRegistry.forPattern(it).isEmpty() }
        assertTrue("AI pattern groups with no problems: $empty", empty.isEmpty())
    }

    @Test
    fun `the quiz catalog has AI quizzes`() {
        val aiQuizzes = QuizRegistry.all.filter { (_, quiz) ->
            quiz.questions.any { it.linkedTopicId in aiTopicIds }
        }
        assertTrue("Expected at least 6 AI quizzes, found ${aiQuizzes.size}", aiQuizzes.size >= 6)
    }

    @Test
    fun `flashcards include cards from every AI section`() {
        val cardTopicIds = allReviewCards().map { it.key.substringBefore('#') }.toSet()

        listOf(
            "ML" to MachineLearningTopics.topics,
            "DL" to DeepLearningTopics.topics,
            "NLP" to NlpTopics.topics,
            "RL" to ReinforcementLearningTopics.topics,
        ).forEach { (section, topics) ->
            val covered = topics.count { it.id in cardTopicIds }
            assertTrue("$section contributes no flashcards", covered > 0)
        }
    }

    @Test
    fun `interview prep has an AI track`() {
        val aiTrack = InterviewPrepTopics.topics.filter { it.categoryId == "interview_ai" }
        assertTrue("Interview Prep has no AI category topics", aiTrack.isNotEmpty())

        val aiQuizTopics = InterviewPrepTopics.topics.filter { topic ->
            QuizRegistry.get(topic.id)?.questions?.any { it.linkedTopicId in aiTopicIds } == true
        }
        assertTrue("Expected at least 6 AI quiz topics in Interview Prep, found ${aiQuizTopics.size}", aiQuizTopics.size >= 6)
    }
}
