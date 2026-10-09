package com.algora.app.core.data.entitlement

import com.algora.app.core.data.TopicRegistry
import com.algora.app.feature.algorithms.AlgorithmsTopics
import com.algora.app.feature.analysis.AnalysisTopics
import com.algora.app.feature.datastructures.DataStructuresTopics
import com.algora.app.feature.deeplearning.DeepLearningTopics
import com.algora.app.feature.interviewprep.InterviewPrepTopics
import com.algora.app.feature.machinelearning.MachineLearningTopics
import com.algora.app.feature.nlp.NlpTopics
import com.algora.app.feature.reinforcementlearning.ReinforcementLearningTopics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// The business model is a ratio, so it is tested as one: about 2% free, 5% ad-unlockable, the rest
// purchase-only. A typo'd id would silently gate nothing, and a new batch of lessons would quietly
// dilute the free tiers, so both are caught here.
class AccessTiersTest {

    private val allIds: Set<String> = (
        DataStructuresTopics.topics + AlgorithmsTopics.topics + AnalysisTopics.topics +
            InterviewPrepTopics.topics + MachineLearningTopics.topics + DeepLearningTopics.topics +
            NlpTopics.topics + ReinforcementLearningTopics.topics
        ).map { it.id }.toSet()

    @Test
    fun `every tiered id is a real topic`() {
        val missing = (AccessTiers.freeIds + AccessTiers.adUnlockIds).filter { TopicRegistry.find(it) == null }
        assertTrue("Tier ids with no topic: $missing", missing.isEmpty())
    }

    @Test
    fun `free and ad-unlock tiers do not overlap`() {
        assertTrue(AccessTiers.freeIds.intersect(AccessTiers.adUnlockIds).isEmpty())
    }

    @Test
    fun `free tier is about 2 percent and ad tier about 5 percent`() {
        val total = allIds.size.toDouble()
        val free = AccessTiers.freeIds.size / total
        val ad = AccessTiers.adUnlockIds.size / total
        assertTrue("Free tier is ${"%.1f".format(free * 100)}% of $total topics", free in 0.015..0.03)
        assertTrue("Ad tier is ${"%.1f".format(ad * 100)}% of $total topics", ad in 0.04..0.06)
    }

    @Test
    fun `free tier is split between DSA and AI`() {
        val dsa = (DataStructuresTopics.topics + AlgorithmsTopics.topics).map { it.id }.toSet()
        val ai = (MachineLearningTopics.topics + DeepLearningTopics.topics + NlpTopics.topics +
            ReinforcementLearningTopics.topics).map { it.id }.toSet()
        assertEquals(6, AccessTiers.freeIds.count { it in dsa })
        assertEquals(6, AccessTiers.freeIds.count { it in ai })
    }

    @Test
    fun `isPremium follows the table`() {
        AccessTiers.freeIds.forEach { assertFalse("$it should be free", TopicRegistry.find(it)!!.isPremium) }
        AccessTiers.adUnlockIds.forEach { assertTrue("$it should be premium", TopicRegistry.find(it)!!.isPremium) }
        // Spot checks from the old model that are now paid: interview rounds and a former free lesson.
        assertTrue(TopicRegistry.find("faang_set")!!.isPremium)
        assertTrue(TopicRegistry.find("tower_of_hanoi")!!.isPremium)
    }

    @Test
    fun `only the ad tier can be opened by an ad`() {
        AccessTiers.adUnlockIds.forEach { assertFalse("$it should be ad-unlockable", PaidOnly.isPaidOnlyTopic(it)) }
        listOf("faang_set", "dijkstras_algorithm", "transformers", "behavioral_question_bank").forEach {
            assertTrue("$it should be purchase-only", PaidOnly.isPaidOnlyTopic(it))
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
