package com.algora.app.feature.algorithms

import com.algora.app.core.data.model.Category
import com.algora.app.core.data.model.Section

// Colors/icons pulled verbatim from docs/design/Algora.dc.html's cats() for Algorithms.
//
// The mock ships nine numbered groups and labels the last one '9–11 · Miscellaneous & Advanced' —
// its own record that it folded three taxonomy groups into one bucket rather than that those groups
// do not exist. Phase 10 unfolds them (Math & Number Theory, String Algorithms, Bit Manipulation)
// and adds Computational Geometry, which docs/topics.algo.md lists and the mock never had a slot
// for. Colors and icons continue the mock's cats() ramp cycling; no new icon assets.
object AlgorithmsCategories {
    val sorting = Category("algo_sorting", "1 · Sorting", Section.ALGORITHMS, 0xFF3B82F6, "stack")
    val searching = Category("algo_searching", "2 · Searching", Section.ALGORITHMS, 0xFF06B6D4, "search")
    val recursion = Category("algo_recursion", "3 · Recursion & Backtracking", Section.ALGORITHMS, 0xFF8B5CF6, "share")
    val divideConquer = Category("algo_divide_conquer", "4 · Divide and Conquer", Section.ALGORITHMS, 0xFFF59E0B, "chip")
    val greedy = Category("algo_greedy", "5 · Greedy Algorithms", Section.ALGORITHMS, 0xFF10B981, "trend")
    val dynamicProgramming = Category("algo_dp", "6 · Dynamic Programming", Section.ALGORITHMS, 0xFFEC4899, "stack")
    val graphAlgorithms = Category("algo_graph", "7 · Graph Algorithms", Section.ALGORITHMS, 0xFF3B82F6, "share")
    val pathfinding = Category("algo_pathfinding", "8 · Pathfinding", Section.ALGORITHMS, 0xFF06B6D4, "map")
    val math = Category("algo_math", "9 · Math & Number Theory", Section.ALGORITHMS, 0xFFF59E0B, "chip")
    val strings = Category("algo_strings", "10 · String Algorithms", Section.ALGORITHMS, 0xFFEC4899, "browser")
    val bits = Category("algo_bits", "11 · Bit Manipulation", Section.ALGORITHMS, 0xFF10B981, "stack")
    val geometry = Category("algo_geometry", "12 · Computational Geometry", Section.ALGORITHMS, 0xFF06B6D4, "globe")

    // Renumbered from 9 now that 9 through 12 exist as their own groups. The id is unchanged, so
    // nothing that references a topic in it moves.
    val miscAdvanced = Category("algo_misc", "13 · Miscellaneous & Advanced", Section.ALGORITHMS, 0xFF8B5CF6, "chip")

    val all = listOf(
        sorting, searching, recursion, divideConquer, greedy, dynamicProgramming, graphAlgorithms,
        pathfinding, math, strings, bits, geometry, miscAdvanced,
    )
}
