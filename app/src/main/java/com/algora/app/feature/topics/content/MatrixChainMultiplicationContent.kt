package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val matrixChainMultiplicationContent = TopicContent(
    topicId = "matrix_chain_multiplication",
    whatIsIt = listOf(
        "Matrix chain multiplication finds the cheapest way to parenthesize a product of matrices — the result is identical, but the order of multiplications drastically changes the scalar-multiplication count.",
        "It's the canonical interval DP: solve every subchain, and for each, try every split point, combining the two halves' costs plus the cost of joining them.",
    ),
    steps = listOf(
        StepCard(1, "Dimensions, Not Values", "Only the matrix dimensions matter; a chain A₁…Aₙ is given as a size array p where Aᵢ is p[i−1] × p[i].", 0xFF10B981),
        StepCard(2, "Interval State", "dp[i][j] = the minimum cost to multiply matrices i through j.", 0xFF3B82F6),
        StepCard(3, "Try Every Split", "For each split k between i and j, cost = dp[i][k] + dp[k+1][j] + joining cost.", 0xFFF59E0B),
        StepCard(4, "Grow by Length", "Fill intervals shortest to longest so every sub-answer is ready.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Join cost", "p[i−1] · p[k] · p[j]", "Scalar multiplications to combine the two halves."),
        FormulaEntry("Transition", "dp[i][j] = min over k of (dp[i][k] + dp[k+1][j] + join)", "Best split point."),
        FormulaEntry("Time / Space", "O(n³) / O(n²)", "n² intervals, each trying up to n splits."),
    ),
    notationKey = listOf(
        NotationEntry("n", "number of matrices in the chain"),
        NotationEntry("p[]", "dimension array; Aᵢ is p[i−1] × p[i]"),
        NotationEntry("dp[i][j]", "min cost to multiply matrices i..j"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Matrix chain — interval DP",
            accentColor = 0xFF6366F1,
            code = """
                // p has size n+1; matrix i is p[i-1] x p[i], for i in 1..n.
                fun matrixChainOrder(p: IntArray): Int {
                    val n = p.size - 1
                    val dp = Array(n + 1) { IntArray(n + 1) }
                    for (len in 2..n) {
                        for (i in 1..n - len + 1) {
                            val j = i + len - 1
                            dp[i][j] = Int.MAX_VALUE
                            for (k in i until j) {
                                val cost = dp[i][k] + dp[k + 1][j] + p[i - 1] * p[k] * p[j]
                                if (cost < dp[i][j]) dp[i][j] = cost
                            }
                        }
                    }
                    return dp[1][n]
                }
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.DpGridVisualizer,
    applications = listOf(
        ApplicationCard("chip", 0xFF10B981, "Query & Expression Optimization", "Databases and compilers reorder chained operations to minimize intermediate cost, the same idea."),
        ApplicationCard("flask", 0xFF3B82F6, "Scientific Computing", "Ordering large matrix products cuts runtime in ML and physics pipelines dramatically."),
        ApplicationCard("book", 0xFFF59E0B, "Teaching Interval DP", "It's the standard example of DP over intervals with a split point."),
    ),
    takeaways = listOf(
        "Parenthesization doesn't change the result but hugely changes the multiplication cost.",
        "It's interval DP: solve every subchain, trying each split point.",
        "It runs in O(n³) time and O(n²) space over the dimension array.",
        "The 'best split of an interval' pattern reappears in optimal BST and polygon triangulation.",
    ),
    crossLinks = listOf(
        CrossLink("longest_common_subsequence", "Longest Common Subsequence"),
        CrossLink("knapsack_01", "0/1 Knapsack"),
    ),
)
