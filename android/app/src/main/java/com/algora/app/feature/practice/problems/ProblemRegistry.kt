package com.algora.app.feature.practice.problems

// Lookup surface for the problem bank, mirroring QuizRegistry's shape. Add problems in
// ProblemContent.kt; nothing here needs to change.
object ProblemRegistry {
    // Aggregated here rather than at file scope: top-level properties initialise in declaration
    // order, so an aggregate living beside its parts is fragile.
    val patterns: List<ProblemPattern> =
        interviewPatterns + corePatterns + structurePatterns + advancedPatterns + aiPatterns
    val all: List<PracticeProblem> =
        interviewPatternProblems + coreTechniqueProblems + extraPatternProblems + structureProblems +
            advancedProblems + aiProblems

    private val byId: Map<String, PracticeProblem> = all.associateBy { it.id }

    // Grouped once up front and sorted easy → hard, so problems added to a group later still land in
    // the right place on the ramp instead of tailing it.
    private val byPattern: Map<String, List<PracticeProblem>> =
        all.groupBy { it.patternId }.mapValues { (_, group) -> group.sortedBy { it.difficulty.ordinal } }

    fun get(problemId: String): PracticeProblem? = byId[problemId]

    fun forPattern(patternId: String): List<PracticeProblem> = byPattern[patternId].orEmpty()

    fun pattern(patternId: String): ProblemPattern? = patterns.find { it.id == patternId }
}
