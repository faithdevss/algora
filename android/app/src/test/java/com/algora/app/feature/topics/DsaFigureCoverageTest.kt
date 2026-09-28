package com.algora.app.feature.topics

import com.algora.app.feature.algorithms.AlgorithmsTopics
import com.algora.app.feature.datastructures.DataStructuresTopics
import com.algora.app.feature.topics.content.TopicContentProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 11 coverage: every Data Structures and Algorithms topic opens with a figure.
 *
 * While the phase was in flight this held a `pendingFigures` set that shrank by one batch per commit.
 * All 122 are done, so the set is gone and the assertion is the plain one — which is also the useful
 * one from here on, since it fails the moment a topic is added without a figure. Shape conformance
 * for those figures lives in [FigureShapeTest].
 */
class DsaFigureCoverageTest {

    private val dsaTopics = DataStructuresTopics.topics + AlgorithmsTopics.topics

    @Test
    fun `every DSA topic has a figure`() {
        val missing = dsaTopics.map { it.id }
            .filter { TopicContentProvider.get(it)?.figure == null }
        assertTrue("DSA topics with no figure: $missing", missing.isEmpty())
    }

    @Test
    fun `the section is still the size the phase was scoped against`() {
        // Not a style rule — it is what tells a reader of the doc that "122 topics" is still true,
        // and what makes the count in the phase plan verifiable rather than folklore.
        assertEquals(122, dsaTopics.size)
    }
}
