package com.algora.app.core.data

import com.algora.app.core.data.model.SimulationType
import com.algora.app.feature.algorithms.AlgorithmsTopics
import com.algora.app.feature.datastructures.DataStructuresTopics
import com.algora.app.feature.deeplearning.DeepLearningTopics
import com.algora.app.feature.machinelearning.MachineLearningTopics
import com.algora.app.feature.nlp.NlpTopics
import com.algora.app.feature.reinforcementlearning.ReinforcementLearningTopics
import com.algora.app.feature.topics.content.TopicContentProvider
import org.junit.Assert.assertTrue
import org.junit.Test

// The catalog's promise is that every browsable topic opens real content with a runnable lab, and
// that every link out of that content lands somewhere. All of it is hand-authored, so a topic added
// to a section list without its content file — or with a simulation id no widget configures — would
// otherwise only show up by tapping through the app.
class ContentCoverageTest {

    // Analysis is excluded deliberately: its entries are tools (AnalysisToolRegistry), not topics
    // with a 7-section detail page.
    private val browsableTopics = DataStructuresTopics.topics + AlgorithmsTopics.topics +
        MachineLearningTopics.topics + DeepLearningTopics.topics + NlpTopics.topics +
        ReinforcementLearningTopics.topics

    @Test
    fun `every browsable topic has authored content`() {
        val missing = browsableTopics.map { it.id }.filter { TopicContentProvider.get(it) == null }
        assertTrue("Topics with no content: $missing", missing.isEmpty())
    }

    @Test
    fun `no browsable topic falls through to the coming-soon simulation`() {
        val coming = browsableTopics
            .map { it.id }
            .filter { TopicContentProvider.get(it)?.simulation == SimulationType.NotYetAvailable }
        assertTrue("Topics with no simulation: $coming", coming.isEmpty())
    }

    @Test
    fun `every prerequisite edge points at a topic that exists`() {
        val broken = browsableTopics.flatMap { topic ->
            PrerequisiteGraph.prereqsOf(topic.id)
                .filter { TopicRegistry.find(it) == null }
                .map { "${topic.id} -> $it" }
        }
        assertTrue("Prerequisites with no matching topic: $broken", broken.isEmpty())
    }

    @Test
    fun `every cross-link points at a topic that exists`() {
        val broken = TopicContentProvider.all.flatMap { (topicId, content) ->
            content.crossLinks
                .filter { TopicRegistry.find(it.topicId) == null }
                .map { "$topicId -> ${it.topicId}" }
        }
        assertTrue("Cross-links with no matching topic: $broken", broken.isEmpty())
    }
}
