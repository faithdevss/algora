package com.algora.app.feature.interviewprep

import com.algora.app.core.data.TopicRegistry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.feature.topics.content.TopicContentProvider
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
