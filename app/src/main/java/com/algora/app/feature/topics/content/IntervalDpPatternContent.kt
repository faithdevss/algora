package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

// Interview-prep pattern guide. DP over ranges rather than prefixes: the state is a segment and the
// transition picks the last (or first) thing to happen inside it.
internal val intervalDpPatternContent = TopicContent(
    topicId = "interval_dp_pattern",
    whatIsIt = listOf(
        "Interval DP indexes the state by a range [i, j] instead of a prefix, and decides what happens *last* inside it — which split point, which balloon bursts, which pair of parentheses closes.",
        "The order of filling matters: every range depends on shorter ranges inside it, so iterate by length, shortest first. Matrix chain multiplication, burst balloons and palindrome partitioning are all this shape.",
    ),
    steps = listOf(
        StepCard(1, "Spot the Signal", "Merging or removing elements where the cost depends on the neighbours that remain — chains, stones, balloons, parenthesisation, palindromic ranges.", 0xFFF59E0B),
        StepCard(2, "State the Range", "dp[i][j] = the answer for the segment i..j, treated in isolation. Decide up front whether the ends are inclusive.", 0xFF3B82F6),
        StepCard(3, "Choose the Last Operation", "dp[i][j] = best over k in (i, j) of dp[i][k] + dp[k][j] + cost(i, k, j). The k that acts last is what you enumerate.", 0xFFEF4444),
        StepCard(4, "Iterate by Length", "Loop length from 2 upward, then i, deriving j = i + length. Filling row-major reads cells that are not written yet.", 0xFF10B981),
    ),
    formulas = listOf(
        FormulaEntry("Recurrence", "dp[i][j] = min/max over k of dp[i][k] + dp[k][j] + cost", "One split point enumerated per range."),
        FormulaEntry("Time / Space", "O(n³) / O(n²)", "n² ranges × n split points."),
        FormulaEntry("Palindrome ranges", "dp[i][j] = dp[i+1][j-1] and s_i = s_j", "Ends match and the inside already qualifies."),
    ),
    notationKey = listOf(
        NotationEntry("dp[i][j]", "best answer for the segment from i to j"),
        NotationEntry("k", "split point, or the element handled last"),
        NotationEntry("len", "current range width being filled"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Burst balloons, filled by length (Python)",
            accentColor = 0xFFF59E0B,
            code = """
                def max_coins(nums):
                    a = [1] + nums + [1]                   # virtual 1s at both ends
                    n = len(a)
                    dp = [[0] * n for _ in range(n)]       # dp[i][j]: open range (i, j)
                    for length in range(2, n):             # shortest ranges first
                        for i in range(n - length):
                            j = i + length
                            for k in range(i + 1, j):      # k bursts last, so i and j survive
                                gain = a[i] * a[k] * a[j] + dp[i][k] + dp[k][j]
                                dp[i][j] = max(dp[i][j], gain)
                    return dp[0][n - 1]
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.DpGridVisualizer,
    applications = listOf(
        ApplicationCard("chip", 0xFF3B82F6, "Optimal Parenthesisation", "Matrix chain order, query planning and expression evaluation order."),
        ApplicationCard("translate", 0xFF10B981, "Palindrome Partitioning", "Minimum cuts and longest palindromic subsequence over ranges."),
        ApplicationCard("game", 0xFF8B5CF6, "Merge & Removal Games", "Stone merging, balloon bursting and card-removal games with neighbour-dependent scores."),
    ),
    takeaways = listOf(
        "The state is a range, and the transition is \"what happens last inside it\".",
        "Fill by increasing length — a row-major sweep reads unwritten cells.",
        "Sentinels at both ends remove the boundary special-casing from the cost term.",
        "O(n³) is expected here; n is small in these problems precisely because of that.",
    ),
    crossLinks = listOf(
        CrossLink("matrix_chain_multiplication", "Matrix Chain Multiplication (Algorithms)"),
        CrossLink("grid_dp_pattern", "Grid & Sequence DP"),
        CrossLink("memo_recursion_pattern", "Top-down Memoization"),
    ),
)
