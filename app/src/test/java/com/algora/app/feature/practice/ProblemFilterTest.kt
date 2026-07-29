package com.algora.app.feature.practice

import com.algora.app.core.data.model.Difficulty
import com.algora.app.feature.practice.problems.ProblemFilters
import com.algora.app.feature.practice.problems.ProblemRegistry
import com.algora.app.feature.practice.problems.filterByPattern
import com.algora.app.feature.practice.problems.filterProblems
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// The list screen narrows 100+ problems before showing any, so a wrong predicate silently hides
// practice rather than crashing. These run against the real bank, not fixtures.
class ProblemFilterTest {

    private val solvedNone = emptySet<String>()

    @Test
    fun `no filters shows the whole bank`() {
        val shown = filterByPattern(ProblemFilters(), solvedNone).sumOf { it.second.size }
        assertEquals(ProblemRegistry.all.size, shown)
        assertFalse(ProblemFilters().isActive)
    }

    @Test
    fun `an empty difficulty set means no difficulty filter, not an empty list`() {
        val pattern = ProblemRegistry.patterns.first()
        val problems = ProblemRegistry.forPattern(pattern.id)
        assertEquals(problems, filterProblems(problems, pattern, ProblemFilters(), solvedNone))
    }

    @Test
    fun `difficulty chips are additive`() {
        val easy = filterByPattern(ProblemFilters(difficulties = setOf(Difficulty.BEGINNER)), solvedNone)
            .sumOf { it.second.size }
        val hard = filterByPattern(ProblemFilters(difficulties = setOf(Difficulty.ADVANCED)), solvedNone)
            .sumOf { it.second.size }
        val both = filterByPattern(
            ProblemFilters(difficulties = setOf(Difficulty.BEGINNER, Difficulty.ADVANCED)),
            solvedNone,
        ).sumOf { it.second.size }
        assertTrue(easy > 0 && hard > 0)
        assertEquals(easy + hard, both)
    }

    @Test
    fun `unsolved only drops solved problems`() {
        val solved = ProblemRegistry.all.take(7).map { it.id }.toSet()
        val shown = filterByPattern(ProblemFilters(unsolvedOnly = true), solved).flatMap { it.second }
        assertEquals(ProblemRegistry.all.size - solved.size, shown.size)
        assertTrue(shown.none { it.id in solved })
    }

    @Test
    fun `query matches a title`() {
        val target = ProblemRegistry.all.first()
        val hits = filterByPattern(ProblemFilters(query = target.title), solvedNone).flatMap { it.second }
        assertTrue(hits.any { it.id == target.id })
    }

    // The point of searching prerequisite labels: someone weak on a topic wants the problems that
    // *need* it, which usually do not name it in the title.
    @Test
    fun `query matches a prerequisite label`() {
        val withPrereq = ProblemRegistry.all.first { it.prerequisites.isNotEmpty() }
        val label = withPrereq.prerequisites.first().label
        val hits = filterByPattern(ProblemFilters(query = label), solvedNone).flatMap { it.second }
        assertTrue(hits.any { it.id == withPrereq.id })
    }

    @Test
    fun `a pattern name pulls in its whole group`() {
        val pattern = ProblemRegistry.patterns.first { ProblemRegistry.forPattern(it.id).size > 1 }
        val hits = filterByPattern(ProblemFilters(query = pattern.name), solvedNone)
        val group = hits.first { it.first.id == pattern.id }.second
        assertEquals(ProblemRegistry.forPattern(pattern.id).size, group.size)
    }

    @Test
    fun `filters intersect rather than union`() {
        val filters = ProblemFilters(difficulties = setOf(Difficulty.BEGINNER), unsolvedOnly = true)
        val solved = ProblemRegistry.all.filter { it.difficulty == Difficulty.BEGINNER }.take(3).map { it.id }.toSet()
        val shown = filterByPattern(filters, solved).flatMap { it.second }
        assertTrue(shown.all { it.difficulty == Difficulty.BEGINNER })
        assertTrue(shown.none { it.id in solved })
    }

    @Test
    fun `a query matching nothing yields no groups`() {
        assertTrue(filterByPattern(ProblemFilters(query = "zzz-not-a-problem"), solvedNone).isEmpty())
    }
}
