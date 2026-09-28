package com.algora.app.feature.interviewprep

import com.algora.app.feature.topics.content.TopicContentProvider
import org.junit.Assert.assertTrue
import org.junit.Test

// Which pattern guides are expected to carry a figure. Shape conformance — bands inside their strip,
// spans inside their axis, trees with one root — moved to FigureShapeTest when the figure layer began
// covering Data Structures and Algorithms, since those rules are not pattern-specific.
class FigureCoverageTest {

    private val patternTopics = InterviewPrepTopics.topics.filter {
        it.categoryId == InterviewPrepCategories.patterns.id
    }

    // Every pattern guide now has a figure; the set stays as the seam the batches were tracked
    // through, and `no pending entry already has a figure` keeps it honest if it is repopulated.
    private val pendingFigures = emptySet<String>()

    @Test
    fun `every pattern guide has a figure`() {
        val missing = patternTopics.map { it.id }
            .filterNot { it in pendingFigures }
            .filter { TopicContentProvider.get(it)?.figure == null }
        assertTrue("Pattern guides with no figure: $missing", missing.isEmpty())
    }

    @Test
    fun `no pending entry already has a figure`() {
        val stale = pendingFigures.filter { TopicContentProvider.get(it)?.figure != null }
        assertTrue("Figured but still listed as pending: $stale", stale.isEmpty())
    }

    @Test
    fun `every pending entry is a real pattern topic`() {
        val ids = patternTopics.map { it.id }.toSet()
        assertTrue(
            "Pending ids that are not pattern topics: ${pendingFigures.filterNot { it in ids }}",
            pendingFigures.all { it in ids },
        )
    }

}
