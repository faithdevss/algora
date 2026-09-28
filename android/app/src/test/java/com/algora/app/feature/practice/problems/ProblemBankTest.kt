package com.algora.app.feature.practice.problems

import com.algora.app.core.data.TopicRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// The bank's value is that every "knowledge you need" row opens something real. These guard the
// links and the registry's own consistency, since both are hand-authored data.
class ProblemBankTest {

    @Test
    fun `the bank meets its minimum size and every group has a difficulty ramp`() {
        assertTrue("Expected at least 75 problems, found ${ProblemRegistry.all.size}", ProblemRegistry.all.size >= 75)

        // forPattern sorts by difficulty; a group whose order regressed would break the ramp.
        val unsorted = ProblemRegistry.patterns.filter { pattern ->
            val ordinals = ProblemRegistry.forPattern(pattern.id).map { it.difficulty.ordinal }
            ordinals != ordinals.sorted()
        }
        assertTrue("Patterns not ordered easy → hard: ${unsorted.map { it.id }}", unsorted.isEmpty())
    }

    @Test
    fun `every prerequisite points at a topic that exists`() {
        val broken = ProblemRegistry.all.flatMap { problem ->
            problem.prerequisites
                .filter { TopicRegistry.find(it.topicId) == null }
                .map { "${problem.id} -> ${it.topicId}" }
        }
        assertTrue("Prerequisites with no matching topic: $broken", broken.isEmpty())
    }

    @Test
    fun `every cross-link points at a topic that exists`() {
        val broken = ProblemRegistry.all
            .mapNotNull { problem -> problem.linkedTopicId?.let { problem.id to it } }
            .filter { (_, topicId) -> TopicRegistry.find(topicId) == null }
        assertTrue("Cross-links with no matching topic: $broken", broken.isEmpty())
    }

    @Test
    fun `every pattern points at a topic that exists`() {
        val broken = ProblemRegistry.patterns.filter { TopicRegistry.find(it.topicId) == null }
        assertTrue("Patterns with no matching topic: ${broken.map { it.id }}", broken.isEmpty())
    }

    @Test
    fun `problem ids are unique`() {
        val duplicates = ProblemRegistry.all.groupBy { it.id }.filterValues { it.size > 1 }.keys
        assertTrue("Duplicate problem ids: $duplicates", duplicates.isEmpty())
        assertEquals(ProblemRegistry.all.size, ProblemRegistry.all.map { it.id }.toSet().size)
    }

    @Test
    fun `every problem belongs to a declared pattern and every pattern has problems`() {
        val patternIds = ProblemRegistry.patterns.map { it.id }.toSet()
        val orphans = ProblemRegistry.all.filter { it.patternId !in patternIds }.map { it.id }
        assertTrue("Problems with an unknown patternId: $orphans", orphans.isEmpty())

        val empty = ProblemRegistry.patterns.filter { ProblemRegistry.forPattern(it.id).isEmpty() }
        assertTrue("Patterns with no problems: ${empty.map { it.id }}", empty.isEmpty())
    }

    @Test
    fun `every problem is solvable content — prompt, hints, approach and solution present`() {
        val incomplete = ProblemRegistry.all.filter {
            it.prompt.isBlank() || it.hints.isEmpty() || it.approach.isEmpty() ||
                it.solutionCode.isBlank() || it.prerequisites.isEmpty()
        }.map { it.id }
        assertTrue("Problems missing required content: $incomplete", incomplete.isEmpty())
    }
}
