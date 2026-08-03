package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.Figure
import com.algora.app.core.data.model.FigureArrow
import com.algora.app.core.data.model.FigureCell
import com.algora.app.core.data.model.FigureShape
import com.algora.app.core.data.model.FigureTone
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val longestCommonSubsequenceContent = TopicContent(
    topicId = "longest_common_subsequence",
    figure = Figure(
        caption = "A = ABC down the side, B = ACB across the top, and every cell is one comparison of " +
            "two prefixes. On a match the value comes from the diagonal plus one â the two characters " +
            "pair off and both prefixes shrink. On a mismatch nothing is paid and nothing is added: the " +
            "cell copies the better of its two neighbours, because the answer is a length being " +
            "maximised rather than a cost being minimised. That is the whole difference from edit " +
            "distance, which reads three neighbours and always adds one. The corner reads 2 (AB or AC), " +
            "and walking back along the arrows recovers which characters those were.",
        shape = FigureShape.Grid(
            rows = listOf(
                listOf("0", "0", "0", "0"),
                listOf("0", "1", "1", "1"),
                listOf("0", "1", "1", "2"),
                listOf("0", "1", "2", "2"),
            ),
            rowHeaders = listOf("ø", "A", "B", "C"),
            colHeaders = listOf("ø", "A", "C", "B"),
            marks = listOf(
                FigureCell(3, 3, FigureTone.Accent),
                FigureCell(2, 3, FigureTone.Primary),
                FigureCell(3, 2, FigureTone.Primary),
                FigureCell(1, 2, FigureTone.Muted),
            ),
            arrows = listOf(
                // Diagonal: B == B, so dp[1][2] + 1. The two below are the mismatch, taking the better
                // neighbour rather than paying for the difference.
                FigureArrow(1, 2, 2, 3),
                FigureArrow(2, 3, 3, 3, FigureTone.Muted),
                FigureArrow(3, 2, 3, 3, FigureTone.Muted),
            ),
        ),
    ),
    whatIsIt = listOf(
        "The longest common subsequence (LCS) of two strings is the longest sequence of characters appearing in both in the same order, though not necessarily contiguously.",
        "It's a foundational two-string DP: a table indexed by prefixes of each string, where each cell extends or inherits the best subsequence found so far.",
    ),
    steps = listOf(
        StepCard(1, "Compare Prefixes", "dp[i][j] is the LCS length of the first i chars of A and first j chars of B.", 0xFF10B981),
        StepCard(2, "Match Extends", "If A[i] == B[j], the LCS grows by one: dp[i][j] = dp[i−1][j−1] + 1.", 0xFF3B82F6),
        StepCard(3, "Mismatch Inherits", "Otherwise take the better of dropping a character from A or from B.", 0xFFF59E0B),
        StepCard(4, "Fill & Trace", "Fill the grid row by row; walk back from the corner to reconstruct the actual subsequence.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Match", "dp[i][j] = dp[i−1][j−1] + 1", "When characters agree."),
        FormulaEntry("Mismatch", "dp[i][j] = max(dp[i−1][j], dp[i][j−1])", "Drop one character from either string."),
        FormulaEntry("Time / Space", "O(m · n)", "One cell per prefix pair; space reducible to O(min(m, n))."),
    ),
    notationKey = listOf(
        NotationEntry("m, n", "lengths of the two strings"),
        NotationEntry("subsequence", "in-order but not necessarily contiguous characters"),
        NotationEntry("dp[i][j]", "LCS length of the two prefixes"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "LCS length — tabulation",
            accentColor = 0xFF6366F1,
            code = """
                fun lcs(a: String, b: String): Int {
                    val dp = Array(a.length + 1) { IntArray(b.length + 1) }
                    for (i in 1..a.length) for (j in 1..b.length) {
                        dp[i][j] = if (a[i - 1] == b[j - 1]) {
                            dp[i - 1][j - 1] + 1
                        } else {
                            maxOf(dp[i - 1][j], dp[i][j - 1])
                        }
                    }
                    return dp[a.length][b.length]
                }
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.DpGridVisualizer,
    applications = listOf(
        ApplicationCard("link", 0xFF10B981, "Diff Tools", "Line-based LCS is what git diff and file-compare tools use to align versions."),
        ApplicationCard("flask", 0xFF3B82F6, "Bioinformatics", "Aligning DNA/protein sequences to measure similarity builds on LCS-style DP."),
        ApplicationCard("search", 0xFFF59E0B, "Plagiarism & Similarity", "Comparing documents for shared ordered content uses LCS as a similarity signal."),
    ),
    takeaways = listOf(
        "LCS is the archetypal two-string DP over prefix pairs.",
        "Matching characters extend the diagonal; mismatches inherit the best neighbor.",
        "It runs in O(m · n), reducible to O(min(m, n)) space if only the length is needed.",
        "Diffing, sequence alignment, and edit distance are all close relatives.",
    ),
    crossLinks = listOf(
        CrossLink("edit_distance", "Edit Distance"),
        CrossLink("longest_increasing_subsequence", "Longest Increasing Subsequence"),
    ),
)
