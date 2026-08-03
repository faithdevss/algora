package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.Figure
import com.algora.app.core.data.model.FigureNode
import com.algora.app.core.data.model.FigureShape
import com.algora.app.core.data.model.FigureTone
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val fibonacciRecursiveContent = TopicContent(
    topicId = "fibonacci_recursive",
    figure = Figure(
        caption = "Two calls per node instead of factorial's one, and the second one is the problem: the " +
            "F(3) hanging off the root is the same subtree already computed under F(4), recomputed from " +
            "scratch. Every marked node below is a repeat. The width of this tree roughly multiplies by " +
            "φ ≈ 1.618 per level, so the call count is exponential while the number of *distinct* " +
            "subproblems is only n — which is exactly the gap memoisation closes.",
        shape = FigureShape.Tree(
            nodes = listOf(
                FigureNode("F(5)", null, FigureTone.Accent),
                FigureNode("F(4)", 0, FigureTone.Primary),
                FigureNode("F(3)", 0, FigureTone.Warn),
                FigureNode("F(3)", 1, FigureTone.Primary),
                FigureNode("F(2)", 1, FigureTone.Warn),
                FigureNode("F(2)", 2, FigureTone.Warn),
                FigureNode("F(1)", 2),
            ),
        ),
    ),
    whatIsIt = listOf(
        "The Fibonacci sequence defines each number as the sum of the two before it: F(n) = F(n−1) + F(n−2), starting from F(0)=0 and F(1)=1.",
        "Its naive recursive form is famous for being a trap — it re-solves the same subproblems exponentially many times, motivating memoization and dynamic programming.",
    ),
    steps = listOf(
        StepCard(1, "Two Base Cases", "F(0) = 0 and F(1) = 1 stop the recursion.", 0xFF10B981),
        StepCard(2, "Branch Twice", "Each call spawns two more: F(n−1) and F(n−2).", 0xFF3B82F6),
        StepCard(3, "Overlapping Subproblems", "F(n−2) gets recomputed inside F(n−1) — the same work happens over and over.", 0xFFF59E0B),
        StepCard(4, "Exponential Blowup", "The branching call tree nearly doubles each level, giving ~O(φⁿ) calls.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Definition", "F(n) = F(n−1) + F(n−2)", "With F(0)=0, F(1)=1."),
        FormulaEntry("Naive time", "O(φⁿ) ≈ O(1.618ⁿ)", "Exponential — the call tree explodes."),
        FormulaEntry("Memoized time", "O(n)", "Caching each F(n) collapses the tree to linear."),
    ),
    notationKey = listOf(
        NotationEntry("F(n)", "the n-th Fibonacci number"),
        NotationEntry("φ", "the golden ratio ≈ 1.618, base of the growth"),
        NotationEntry("memoization", "caching results to skip repeated work"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Naive vs memoized Fibonacci",
            accentColor = 0xFF6366F1,
            code = """
                // Naive: exponential — recomputes the same values endlessly.
                fun fib(n: Int): Long =
                    if (n < 2) n.toLong() else fib(n - 1) + fib(n - 2)

                // Memoized: linear — each F(n) is computed once and cached.
                fun fibMemo(n: Int, cache: HashMap<Int, Long> = HashMap()): Long {
                    if (n < 2) return n.toLong()
                    cache[n]?.let { return it }
                    val result = fibMemo(n - 1, cache) + fibMemo(n - 2, cache)
                    cache[n] = result
                    return result
                }
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.RecursionTreeVisualizer,
    applications = listOf(
        ApplicationCard("book", 0xFF10B981, "Why Memoization Matters", "It's the standard example showing how caching turns exponential recursion into linear DP."),
        ApplicationCard("flask", 0xFF3B82F6, "Nature & Math", "Fibonacci numbers appear in phyllotaxis, the golden ratio, and Euclid's GCD analysis."),
        ApplicationCard("chip", 0xFFF59E0B, "Recursion Tree Teaching", "Its branching tree visualizes overlapping subproblems better than any other example."),
    ),
    takeaways = listOf(
        "Naive recursive Fibonacci is O(φⁿ) because it recomputes overlapping subproblems.",
        "Memoization caches each F(n) once, collapsing the cost to O(n).",
        "It is the archetype motivating dynamic programming's 'store, don't recompute' idea.",
        "An iterative two-variable loop computes it in O(n) time and O(1) space.",
    ),
    crossLinks = listOf(
        CrossLink("fibonacci_dp", "Fibonacci (Dynamic Programming)"),
        CrossLink("factorial", "Factorial"),
    ),
)
