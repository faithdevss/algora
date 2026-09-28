package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.Figure
import com.algora.app.core.data.model.FigureBand
import com.algora.app.core.data.model.FigurePointer
import com.algora.app.core.data.model.FigureShape
import com.algora.app.core.data.model.FigureTone
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val fibonacciDpContent = TopicContent(
    topicId = "fibonacci_dp",
    figure = Figure(
        caption = "The same numbers the naive recursion recomputed exponentially, each written exactly " +
            "once. There are only n distinct subproblems no matter how many times the recursion asked " +
            "for them, so filling left to right is linear — and the direction is the only thing that " +
            "differs from top-down memoisation, which fills the same cells in the order the calls " +
            "happen to reach them. The band is the second observation: a cell reads nothing but its two " +
            "predecessors, so the array can be thrown away and two variables kept instead, which is why " +
            "the practical version is O(1) space.",
        shape = FigureShape.Strip(
            cells = listOf("0", "1", "1", "2", "3", "5", "8"),
            bands = listOf(FigureBand(4, 5, "all state kept", FigureTone.Accent)),
            pointers = listOf(FigurePointer(6, "F(6)=8")),
        ),
    ),
    whatIsIt = listOf(
        "Fibonacci by dynamic programming computes F(n) in linear time by storing each result instead of recomputing it — the fix for naive recursion's exponential blowup.",
        "It's the gateway example for the two DP styles: top-down memoization (recurse, but cache) and bottom-up tabulation (fill a table from the base cases up).",
    ),
    steps = listOf(
        StepCard(1, "Identify Overlap", "Naive recursion recomputes F(n−2) many times — those repeated subproblems are the target.", 0xFF10B981),
        StepCard(2, "Memoize (Top-Down)", "Recurse as usual but store each F(n) the first time; return the cached value on repeat calls.", 0xFF3B82F6),
        StepCard(3, "Tabulate (Bottom-Up)", "Fill an array left to right: each entry is the sum of the two before it.", 0xFFF59E0B),
        StepCard(4, "Shrink the State", "Only the last two values matter, so two variables replace the whole table — O(1) space.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Recurrence", "F(n) = F(n−1) + F(n−2)", "The same relation, now computed once each."),
        FormulaEntry("Time", "O(n)", "Each subproblem solved a single time."),
        FormulaEntry("Space", "O(n) → O(1)", "Table, or just two rolling variables."),
    ),
    notationKey = listOf(
        NotationEntry("dp[i]", "the stored value of F(i)"),
        NotationEntry("memoization", "top-down caching of recursive results"),
        NotationEntry("tabulation", "bottom-up filling of a DP table"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Bottom-up, O(1) space",
            accentColor = 0xFF6366F1,
            code = """
                fun fib(n: Int): Long {
                    if (n < 2) return n.toLong()
                    var prev = 0L
                    var curr = 1L
                    repeat(n - 1) {
                        val next = prev + curr
                        prev = curr
                        curr = next
                    }
                    return curr
                }
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.DpGridVisualizer,
    applications = listOf(
        ApplicationCard("book", 0xFF10B981, "Learning DP", "It's the canonical first lesson in both memoization and tabulation."),
        ApplicationCard("chip", 0xFF3B82F6, "State Compression", "Reducing an O(n) table to O(1) variables is a reusable DP optimization pattern."),
        ApplicationCard("chart", 0xFFF59E0B, "Counting Problems", "Many stair-climbing and tiling counts follow the exact same two-term recurrence."),
    ),
    takeaways = listOf(
        "Storing subproblem results turns exponential recursion into linear DP.",
        "Top-down memoization and bottom-up tabulation are two routes to the same answer.",
        "When only recent states matter, roll the table into a few variables for O(1) space.",
        "Fibonacci is the minimal example of DP's core idea: solve each subproblem once.",
    ),
    crossLinks = listOf(
        CrossLink("fibonacci_recursive", "Fibonacci"),
        CrossLink("coin_change", "Coin Change"),
    ),
)
