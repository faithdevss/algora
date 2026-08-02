package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

// Interview-prep pattern guide. The two-sequence / grid DP family: LCS, edit distance and
// path-counting all fill the same table with different cell rules.
internal val gridDpPatternContent = TopicContent(
    topicId = "grid_dp_pattern",
    whatIsIt = listOf(
        "Grid DP indexes a table by two positions — two string prefixes, or a row and column of a matrix — and fills each cell from its neighbours above and to the left. The answer lands in the far corner.",
        "Longest common subsequence, edit distance, unique paths and minimum path sum are the same table with different cell rules: match-or-branch for sequences, accumulate for paths.",
    ),
    steps = listOf(
        StepCard(1, "Spot the Signal", "Two sequences compared position by position, or movement through a grid restricted to right/down — with a best or count question.", 0xFFF59E0B),
        StepCard(2, "Define the Cell", "dp[i][j] = the answer for the first i of A and the first j of B (or for reaching cell (i, j)). Fix the meaning before the recurrence.", 0xFF3B82F6),
        StepCard(3, "Split Match from Mismatch", "Characters equal → extend the diagonal, dp[i-1][j-1] + 1. Not equal → take the best of the neighbours you are allowed to move from.", 0xFFEF4444),
        StepCard(4, "Seed and Sweep", "Fill row 0 and column 0 with the empty-prefix answers, then sweep rows left to right so every dependency is already written.", 0xFF10B981),
    ),
    formulas = listOf(
        FormulaEntry("LCS", "dp[i][j] = dp[i-1][j-1] + 1 if a_i = b_j, else max(dp[i-1][j], dp[i][j-1])", "Diagonal on a match, best neighbour otherwise."),
        FormulaEntry("Edit distance", "dp[i][j] = 1 + min(dp[i-1][j], dp[i][j-1], dp[i-1][j-1])", "Delete, insert, replace — cost 0 diagonal on a match."),
        FormulaEntry("Time / Space", "O(mn) / O(min(m, n))", "Two rolling rows suffice unless the path must be reconstructed."),
    ),
    notationKey = listOf(
        NotationEntry("m, n", "lengths of the two sequences, or grid dimensions"),
        NotationEntry("dp[i][j]", "answer for prefixes of length i and j"),
        NotationEntry("↖ ↑ ←", "diagonal, up and left transitions into a cell"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "LCS length and edit distance (Python)",
            accentColor = 0xFFF59E0B,
            code = """
                def lcs(a, b):
                    m, n = len(a), len(b)
                    dp = [[0] * (n + 1) for _ in range(m + 1)]
                    for i in range(1, m + 1):
                        for j in range(1, n + 1):
                            if a[i - 1] == b[j - 1]:
                                dp[i][j] = dp[i - 1][j - 1] + 1          # extend the match
                            else:
                                dp[i][j] = max(dp[i - 1][j], dp[i][j - 1])
                    return dp[m][n]

                def edit_distance(a, b):
                    m, n = len(a), len(b)
                    dp = [[0] * (n + 1) for _ in range(m + 1)]
                    for i in range(m + 1):
                        for j in range(n + 1):
                            if i == 0 or j == 0:
                                dp[i][j] = i + j                          # empty prefix costs its length
                            elif a[i - 1] == b[j - 1]:
                                dp[i][j] = dp[i - 1][j - 1]
                            else:
                                dp[i][j] = 1 + min(dp[i - 1][j], dp[i][j - 1], dp[i - 1][j - 1])
                    return dp[m][n]
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.DpGridVisualizer,
    applications = listOf(
        ApplicationCard("code", 0xFF3B82F6, "Diff Tools", "git diff is an LCS over lines; the unmatched remainder is the patch."),
        ApplicationCard("translate", 0xFF10B981, "Fuzzy Matching", "Spell-check and record deduplication rank candidates by edit distance."),
        ApplicationCard("map", 0xFF8B5CF6, "Path Counting", "Unique paths and minimum path sum on a grid with blocked cells."),
    ),
    takeaways = listOf(
        "Two indices in the state means a table — draw the small case before coding.",
        "The match branch moves diagonally; the mismatch branch takes the best legal neighbour.",
        "Row 0 and column 0 encode the empty-prefix answers and are where most bugs live.",
        "Keep the full table only when you must recover the alignment, otherwise roll to two rows.",
    ),
    crossLinks = listOf(
        CrossLink("longest_common_subsequence", "Longest Common Subsequence (Algorithms)"),
        CrossLink("edit_distance", "Edit Distance (Algorithms)"),
        CrossLink("knapsack_dp_pattern", "0/1 Knapsack DP Pattern"),
    ),
)
