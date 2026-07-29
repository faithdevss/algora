package com.algora.app.feature.practice.problems

import com.algora.app.core.data.model.Difficulty

// What the list screen is currently showing. Kept out of the composable so the rules are testable:
// an empty difficulty set means "no difficulty filter", not "show nothing".
data class ProblemFilters(
    val query: String = "",
    val difficulties: Set<Difficulty> = emptySet(),
    val unsolvedOnly: Boolean = false,
) {
    val isActive: Boolean get() = query.isNotBlank() || difficulties.isNotEmpty() || unsolvedOnly
}

/**
 * Filters one pattern's problems. Text matches the title, the pattern's own name and blurb, and the
 * prerequisite labels — so "hash" finds problems that *need* hashing, not only ones with it in the
 * title, which is how someone hunting for practice on a weak topic actually searches.
 */
fun filterProblems(
    problems: List<PracticeProblem>,
    pattern: ProblemPattern?,
    filters: ProblemFilters,
    solvedIds: Set<String>,
): List<PracticeProblem> = problems.filter { problem ->
    val matchesQuery = filters.query.isBlank() ||
        problem.title.contains(filters.query, ignoreCase = true) ||
        pattern?.name?.contains(filters.query, ignoreCase = true) == true ||
        pattern?.blurb?.contains(filters.query, ignoreCase = true) == true ||
        problem.prerequisites.any { it.label.contains(filters.query, ignoreCase = true) }

    val matchesDifficulty = filters.difficulties.isEmpty() || problem.difficulty in filters.difficulties
    val matchesSolved = !filters.unsolvedOnly || problem.id !in solvedIds

    matchesQuery && matchesDifficulty && matchesSolved
}

// The whole bank grouped by pattern, filters applied, empty groups dropped. Patterns keep the
// registry's order so the ramp through the bank is unchanged.
fun filterByPattern(
    filters: ProblemFilters,
    solvedIds: Set<String>,
): List<Pair<ProblemPattern, List<PracticeProblem>>> =
    ProblemRegistry.patterns.mapNotNull { pattern ->
        val hits = filterProblems(ProblemRegistry.forPattern(pattern.id), pattern, filters, solvedIds)
        if (hits.isEmpty()) null else pattern to hits
    }
