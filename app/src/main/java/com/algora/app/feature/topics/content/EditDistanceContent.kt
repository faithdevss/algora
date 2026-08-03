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

internal val editDistanceContent = TopicContent(
    topicId = "edit_distance",
    figure = Figure(
        caption = "sun → sat, and the marked cell has three ways in rather than LCS's two: the diagonal " +
            "is a substitution, the cell above a deletion, the cell to the left an insertion. All three " +
            "cost one, so the cell is 1 + the cheapest of them — unless the characters match, in which " +
            "case the diagonal is carried through free and nothing is added at all. The borders are not " +
            "zeros either: row 0 counts 0..n because turning an empty string into a prefix of length n " +
            "costs n insertions. The corner is 2, which is u→a and n→t.",
        shape = FigureShape.Grid(
            rows = listOf(
                listOf("0", "1", "2", "3"),
                listOf("1", "0", "1", "2"),
                listOf("2", "1", "1", "2"),
                listOf("3", "2", "2", "2"),
            ),
            rowHeaders = listOf("ø", "s", "u", "n"),
            colHeaders = listOf("ø", "s", "a", "t"),
            marks = listOf(
                FigureCell(3, 3, FigureTone.Accent),
                FigureCell(2, 2, FigureTone.Primary),
                FigureCell(2, 3, FigureTone.Muted),
                FigureCell(3, 2, FigureTone.Muted),
            ),
            arrows = listOf(
                FigureArrow(2, 2, 3, 3),
                FigureArrow(2, 3, 3, 3, FigureTone.Muted),
                FigureArrow(3, 2, 3, 3, FigureTone.Muted),
            ),
        ),
    ),
    whatIsIt = listOf(
        "Edit distance (Levenshtein distance) is the minimum number of single-character insertions, deletions, or substitutions needed to turn one string into another.",
        "Like LCS it's a two-string DP over prefixes, but each cell minimizes a cost rather than maximizing a length.",
    ),
    steps = listOf(
        StepCard(1, "Define the State", "dp[i][j] = the edit distance between the first i chars of A and first j chars of B.", 0xFF10B981),
        StepCard(2, "Match Costs Nothing", "If A[i] == B[j], carry the diagonal cost unchanged.", 0xFF3B82F6),
        StepCard(3, "Otherwise +1", "Take the cheapest of insert, delete, or substitute — each adds one to a neighbor.", 0xFFF59E0B),
        StepCard(4, "Seed the Borders", "Row 0 and column 0 are 0..n: the cost of building from an empty string.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Match", "dp[i][j] = dp[i−1][j−1]", "Characters agree — no edit."),
        FormulaEntry("Edit", "1 + min(dp[i−1][j], dp[i][j−1], dp[i−1][j−1])", "Delete, insert, or substitute."),
        FormulaEntry("Time / Space", "O(m · n)", "Space reducible to O(min(m, n)) with two rows."),
    ),
    notationKey = listOf(
        NotationEntry("m, n", "lengths of the two strings"),
        NotationEntry("insert/delete/substitute", "the three unit-cost operations"),
        NotationEntry("dp[i][j]", "min edits between the two prefixes"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Levenshtein distance — tabulation",
            accentColor = 0xFF6366F1,
            code = """
                fun editDistance(a: String, b: String): Int {
                    val dp = Array(a.length + 1) { IntArray(b.length + 1) }
                    for (i in 0..a.length) dp[i][0] = i
                    for (j in 0..b.length) dp[0][j] = j
                    for (i in 1..a.length) for (j in 1..b.length) {
                        dp[i][j] = if (a[i - 1] == b[j - 1]) {
                            dp[i - 1][j - 1]
                        } else {
                            1 + minOf(dp[i - 1][j], dp[i][j - 1], dp[i - 1][j - 1])
                        }
                    }
                    return dp[a.length][b.length]
                }
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.DpGridVisualizer,
    applications = listOf(
        ApplicationCard("search", 0xFF10B981, "Spell Check & Autocorrect", "Suggestions rank candidate words by how few edits reach the typed word."),
        ApplicationCard("flask", 0xFF3B82F6, "DNA Alignment", "Scoring mutations between genetic sequences is a weighted edit distance."),
        ApplicationCard("link", 0xFFF59E0B, "Fuzzy Matching", "Deduplication and record linkage treat near-identical strings as matches within a distance threshold."),
    ),
    takeaways = listOf(
        "Edit distance minimizes insert/delete/substitute operations via a two-string DP.",
        "Matching characters inherit the diagonal; any edit costs one plus the best neighbor.",
        "It runs in O(m · n), reducible to O(min(m, n)) space with rolling rows.",
        "It's the cost-minimizing sibling of LCS and the backbone of fuzzy string matching.",
    ),
    crossLinks = listOf(
        CrossLink("longest_common_subsequence", "Longest Common Subsequence"),
        CrossLink("string", "String"),
    ),
)
