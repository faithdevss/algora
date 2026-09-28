package com.algora.app.core.data

import com.algora.app.feature.topics.content.TopicContentProvider
import org.junit.Assert.assertTrue
import org.junit.Test

// The Simulations tab groups its 150+ rows by the topic's category and that category's section. A
// lab whose topic or category cannot be resolved would silently fall into the wrong section header,
// which is invisible from the code and only shows up by scrolling the tab.
class SimulationCatalogTest {

    private val runnable = TopicContentProvider.runnableSimulations

    @Test
    fun `every runnable simulation resolves to a topic`() {
        val missing = runnable.map { it.first }.filter { TopicRegistry.find(it) == null }
        assertTrue("Simulations whose topic is not in any section list: $missing", missing.isEmpty())
    }

    @Test
    fun `every runnable simulation resolves to a category`() {
        val orphans = runnable.mapNotNull { (topicId, _) ->
            val topic = TopicRegistry.find(topicId) ?: return@mapNotNull null
            if (CategoryRegistry.find(topic.categoryId) == null) "$topicId -> '${topic.categoryId}'" else null
        }
        assertTrue("Simulations whose category is unknown — these group under the wrong section:\n$orphans", orphans.isEmpty())
    }
}
