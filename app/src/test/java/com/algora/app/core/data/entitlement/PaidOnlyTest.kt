package com.algora.app.core.data.entitlement

import com.algora.app.core.data.TopicRegistry
import com.algora.app.feature.algorithms.AlgorithmsTopics
import com.algora.app.feature.analysis.AnalysisTopics
import com.algora.app.feature.datastructures.DataStructuresTopics
import com.algora.app.feature.deeplearning.DeepLearningTopics
import com.algora.app.feature.machinelearning.MachineLearningTopics
import com.algora.app.feature.nlp.NlpTopics
import com.algora.app.feature.reinforcementlearning.ReinforcementLearningTopics
import com.algora.app.feature.interviewprep.InterviewPrepCategories
import com.algora.app.feature.interviewprep.InterviewPrepTopics
import com.algora.app.feature.interviewprep.quiz.QuizRegistry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// Practice is purchase-only; the rest of the library keeps the rewarded-ad unlock. A slip either way
// is costly — an ad-opened interview set gives away what Premium is sold on, and a paid-only
// Learning topic silently removes the free route a user was promised.
class PaidOnlyTest {

    @Test
    fun `every premium quiz set is purchase-only`() {
        val adOpenable = QuizRegistry.all.map { it.first }
            .filter { TopicRegistry.find(it)?.isPremium == true }
            .filterNot { PaidOnly.isPaidOnlyTopic(it) }
        assertTrue("Premium quiz sets an ad could still open: $adOpenable", adOpenable.isEmpty())
    }

    @Test
    fun `interview primers and the behavioral bank are purchase-only`() {
        listOf("behavioral_question_bank", "system_design_primer", "ml_system_design_primer").forEach {
            assertTrue("$it should be purchase-only", PaidOnly.isPaidOnlyTopic(it))
        }
    }

    @Test
    fun `pattern guides keep the ad unlock`() {
        val paidOnly = InterviewPrepTopics.topics
            .filter { it.categoryId == InterviewPrepCategories.patterns.id }
            .filter { PaidOnly.isPaidOnlyTopic(it.id) }
            .map { it.id }
        assertTrue("Pattern guides wrongly made purchase-only: $paidOnly", paidOnly.isEmpty())
    }

    @Test
    fun `learning topics that share a name with a problem group keep the ad unlock`() {
        // Problem-group ids reuse these Learning topic ids, which is why groups are gated at their
        // call sites instead of through PaidOnly.
        listOf("sliding_window", "binary_search", "prefix_sum", "dynamic_programming").forEach {
            assertFalse("$it should stay ad-unlockable", PaidOnly.isPaidOnlyTopic(it))
        }
    }

    @Test
    fun `flagship lessons are purchase-only and are real premium topics`() {
        val problems = PaidOnly.flagshipLessonIds.filter { id ->
            val topic = TopicRegistry.find(id)
            topic == null || !topic.isPremium || !PaidOnly.isPaidOnlyTopic(id)
        }
        // A free topic in this list would be a no-op; a missing id would be a typo gating nothing.
        assertTrue("Flagship ids that are missing, free, or not gated: $problems", problems.isEmpty())
    }

    // The purchase has to buy something an ad cannot: every Learning section keeps at least a fifth of
    // its lessons purchase-only. Adding lessons to a section without adding to the list trips this.
    @Test
    fun `every learning section is at least 20 percent purchase-only`() {
        val sections = mapOf(
            "Data Structures" to DataStructuresTopics.topics,
            "Algorithms" to AlgorithmsTopics.topics,
            "Analysis" to AnalysisTopics.topics,
            "Machine Learning" to MachineLearningTopics.topics,
            "Deep Learning" to DeepLearningTopics.topics,
            "NLP" to NlpTopics.topics,
            "Reinforcement Learning" to ReinforcementLearningTopics.topics,
        )
        val short = sections.mapNotNull { (name, topics) ->
            val paidOnly = topics.count { it.isPremium && PaidOnly.isPaidOnlyTopic(it.id) }
            "$name: $paidOnly of ${topics.size}".takeIf { paidOnly * 5 < topics.size }
        }
        assertTrue("Sections under 20% purchase-only: $short", short.isEmpty())
    }

    @Test
    fun `fundamentals keep the ad unlock as a taste of premium`() {
        listOf("hash_table", "heap", "two_pointer", "merge_sort", "logistic_regression", "attention").forEach {
            assertFalse("$it should stay ad-unlockable", PaidOnly.isPaidOnlyTopic(it))
        }
    }

    @Test
    fun `an ad unlock on a purchase-only topic grants nothing`() {
        // Mirrors EntitlementRepository.adUnlocks, which drops paid-only ids before accessOf sees them.
        val now = 1_000L
        val stored = mapOf("faang_set" to now + 3_600_000L, "graph" to now + 3_600_000L)
        val unlocks = stored.filterKeys { !PaidOnly.isPaidOnlyTopic(it) }
        assertTrue(accessOf(true, false, unlocks, "faang_set", now) is TopicAccess.Locked)
        assertTrue(accessOf(true, false, unlocks, "graph", now) is TopicAccess.AdUnlocked)
    }
}
