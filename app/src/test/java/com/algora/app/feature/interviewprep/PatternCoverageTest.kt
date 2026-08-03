package com.algora.app.feature.interviewprep

import com.algora.app.core.data.TopicRegistry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.feature.topics.arrayWalkTopicIds
import com.algora.app.feature.topics.bitBoardTopicIds
import com.algora.app.feature.topics.content.TopicContentProvider
import com.algora.app.feature.topics.dpGridTopicIds
import com.algora.app.feature.topics.gameSearchTopicIds
import com.algora.app.feature.topics.graphAlgoTopicIds
import com.algora.app.feature.topics.hashingVisualizerTopicIds
import com.algora.app.feature.topics.linkedStructureTopicIds
import com.algora.app.feature.topics.pathfindingTopicIds
import com.algora.app.feature.topics.recursionTreeTopicIds
import com.algora.app.feature.topics.searchVisualizerTopicIds
import com.algora.app.feature.topics.sortingVisualizerTopicIds
import com.algora.app.feature.topics.treeVisualizerTopicIds
import org.junit.Assert.assertTrue
import org.junit.Test

// ContentCoverageTest deliberately skips Interview Prep, because most of that section's rows open a
// quiz rather than the 7-section template. The pattern guides are the exception — every one is an
// authored topic page — so they get the same guarantees here: content exists, the lab runs, and the
// chips under Key Takeaways lead somewhere.
class PatternCoverageTest {

    private val patternTopics = InterviewPrepTopics.topics.filter {
        it.categoryId == InterviewPrepCategories.patterns.id
    }

    @Test
    fun `every pattern guide has authored content`() {
        val missing = patternTopics.map { it.id }.filter { TopicContentProvider.get(it) == null }
        assertTrue("Pattern topics with no content: $missing", missing.isEmpty())
    }

    @Test
    fun `no pattern guide falls through to the coming-soon simulation`() {
        val coming = patternTopics
            .map { it.id }
            .filter { TopicContentProvider.get(it)?.simulation == SimulationType.NotYetAvailable }
        assertTrue("Pattern topics with no simulation: $coming", coming.isEmpty())
    }

    // Every simulation widget resolves its content by topic id and silently falls back to another
    // topic's config when there is none — `walkConfigs[topicId] ?: walkConfigs.getValue("two_pointer")`.
    // A pattern guide that lands on that fallback ships a lab about a different algorithm entirely
    // (knapsack playing Fibonacci, patience sorting playing two-pointer), which the NotYetAvailable
    // check above cannot see. This maps each config-driven widget to the ids it actually knows.
    private val configuredIds: Map<SimulationType, Set<String>> = mapOf(
        SimulationType.ArrayWalkPlayer to arrayWalkTopicIds,
        SimulationType.BitBoardPlayer to bitBoardTopicIds,
        SimulationType.DpGridVisualizer to dpGridTopicIds,
        SimulationType.GameSearchPlayer to gameSearchTopicIds,
        SimulationType.GraphAlgorithmPlayer to graphAlgoTopicIds,
        SimulationType.HashingVisualizer to hashingVisualizerTopicIds,
        SimulationType.LinkedStructurePlayer to linkedStructureTopicIds,
        SimulationType.PathfindingGrid to pathfindingTopicIds,
        SimulationType.RecursionTreeVisualizer to recursionTreeTopicIds,
        SimulationType.SearchVisualizer to searchVisualizerTopicIds,
        SimulationType.SortingVisualizer to sortingVisualizerTopicIds,
        SimulationType.TreeVisualizer to treeVisualizerTopicIds,
    )

    // Guides still waiting for their own config, authored batch by batch. The list only shrinks —
    // `no pending entry already has a config` fails the moment one is written and left here.
    private val pendingConfigs = setOf(
        "bit_trie_pattern",
        "bitmask_state_pattern",
        "composite_design_pattern",
        "dag_dp_pattern",
        "expression_stack_pattern",
        "game_theory_dp_pattern",
        "graph_coloring_pattern",
        "hash_counting_pattern",
        "meet_in_middle_pattern",
        "memo_recursion_pattern",
        "modified_binary_search_pattern",
        "shortest_path_pattern",
    )

    private fun hasOwnConfig(topicId: String): Boolean {
        val simulation = TopicContentProvider.get(topicId)?.simulation ?: return false
        return topicId in configuredIds[simulation].orEmpty()
    }

    @Test
    fun `no pattern guide falls back to another topic's simulation`() {
        val fallbacks = patternTopics
            .map { it.id }
            .filterNot { it in pendingConfigs }
            .filterNot { hasOwnConfig(it) }
            .map { "$it -> ${TopicContentProvider.get(it)?.simulation}" }
        assertTrue(
            "Pattern guides rendering another topic's simulation: $fallbacks",
            fallbacks.isEmpty(),
        )
    }

    @Test
    fun `no pending entry already has a config`() {
        val stale = pendingConfigs.filter { hasOwnConfig(it) }
        assertTrue("Configured but still listed as pending: $stale", stale.isEmpty())
    }

    @Test
    fun `every pending entry is a real pattern topic`() {
        val ids = patternTopics.map { it.id }.toSet()
        val unknown = pendingConfigs.filterNot { it in ids }
        assertTrue("Pending ids that are not pattern topics: $unknown", unknown.isEmpty())
    }

    @Test
    fun `every pattern cross-link points at a topic that exists`() {
        val broken = patternTopics.flatMap { topic ->
            TopicContentProvider.get(topic.id)?.crossLinks.orEmpty()
                .filter { TopicRegistry.find(it.topicId) == null }
                .map { "${topic.id} -> ${it.topicId}" }
        }
        assertTrue("Pattern cross-links with no matching topic: $broken", broken.isEmpty())
    }

    @Test
    fun `pattern content ids match the topic they are registered under`() {
        val mismatched = patternTopics
            .mapNotNull { topic -> TopicContentProvider.get(topic.id)?.let { topic.id to it.topicId } }
            .filter { (topicId, contentId) -> topicId != contentId }
        assertTrue("Pattern content registered under the wrong id: $mismatched", mismatched.isEmpty())
    }
}
