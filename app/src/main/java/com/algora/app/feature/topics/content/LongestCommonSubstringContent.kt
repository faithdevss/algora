package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val longestCommonSubstringContent = TopicContent(
    topicId = "longest_common_substring",
    whatIsIt = listOf(
        "The longest common substring of two strings is the longest run of characters that appears contiguously in both. The word doing the work is contiguous — it is what separates this problem from longest common subsequence, which allows gaps, and the two answers can differ enormously on the same input.",
        "The table is nearly the LCS table with one clause removed. dp[i][j] is the length of the longest common suffix of the first i characters of one string and the first j of the other. On a character match it is dp[i−1][j−1] + 1; on a mismatch it is zero, not the maximum of its neighbours. That single change is the contiguity requirement made mechanical: a mismatch ends the run rather than carrying the best result so far forward. Because runs can end anywhere, the answer is the largest value anywhere in the table, not the bottom-right corner — reading the corner is the standard mistake, and it silently returns the length of the common suffix instead.",
        "Cost is O(n·m) time and, if you only need the length, O(min(n, m)) space by keeping one row. Recovering the substring itself needs the position of the maximum, which one extra pair of variables tracks during the fill. For very large inputs the DP is not the best available: a generalised suffix automaton or a suffix tree over both strings solves it in O(n + m), and binary searching the answer length with rolling hashes gives O((n + m) log min(n, m)) with far less code. The DP remains the right default because it is short, exact, and generalises directly — to k strings, to allowing a bounded number of mismatches, to weighted characters.",
    ),
    steps = listOf(
        StepCard(1, "Define the Cell as a Suffix Length", "dp[i][j] is the longest common suffix of the two prefixes, not the best answer so far.", 0xFFEC4899),
        StepCard(2, "Pad with a Zero Row and Column", "An empty prefix shares nothing, which makes the diagonal lookup safe at the edges.", 0xFF3B82F6),
        StepCard(3, "Extend on a Match", "a[i−1] == b[j−1] → dp[i][j] = dp[i−1][j−1] + 1. The run grows by one.", 0xFF10B981),
        StepCard(4, "Reset to Zero on a Mismatch", "Not max(up, left) — that is LCS. Zero here is what enforces contiguity.", 0xFFEF4444),
        StepCard(5, "Track the Running Maximum", "Record the best value and where it occurred; the answer is not at the corner.", 0xFFF59E0B),
        StepCard(6, "Recover by Walking Back", "From the maximum cell, step back diagonally that many characters to read the substring.", 0xFF8B5CF6),
    ),
    formulas = listOf(
        FormulaEntry("Recurrence", "dp[i][j] = dp[i−1][j−1] + 1 if aᵢ = bⱼ, else 0", "The `else 0` is the entire difference from LCS."),
        FormulaEntry("Answer", "max over all cells", "Not dp[n][m] — that is only the common suffix."),
        FormulaEntry("Time", "O(n · m)", "One constant-time cell per pair of prefixes."),
        FormulaEntry("Space", "O(min(n, m))", "One rolling row suffices for the length alone."),
        FormulaEntry("Suffix automaton", "O(n + m)", "The asymptotically better route, at considerably more code."),
        FormulaEntry("Binary search + hashing", "O((n + m) log min(n, m))", "Guess a length, hash all windows of it, look for a collision."),
    ),
    notationKey = listOf(
        NotationEntry("substring", "a contiguous run of characters"),
        NotationEntry("subsequence", "characters in order but not necessarily adjacent — a different problem"),
        NotationEntry("dp[i][j]", "longest common suffix of a[0..i) and b[0..j)"),
        NotationEntry("rolling row", "the single array the 2-D table collapses to when only the length is needed"),
        NotationEntry("generalised suffix tree", "a suffix structure built over both strings at once"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "The table, and recovering the substring",
            accentColor = 0xFFEC4899,
            code = """
                fun longestCommonSubstring(a: String, b: String): String {
                    val dp = Array(a.length + 1) { IntArray(b.length + 1) }
                    var best = 0
                    var endInA = 0

                    for (i in 1..a.length) {
                        for (j in 1..b.length) {
                            if (a[i - 1] == b[j - 1]) {
                                dp[i][j] = dp[i - 1][j - 1] + 1
                                if (dp[i][j] > best) {
                                    best = dp[i][j]
                                    endInA = i          // the run ends here — remember where
                                }
                            }
                            // else: dp[i][j] stays 0. A mismatch ends the run outright, and
                            // that is the only line separating this from LCS.
                        }
                    }
                    return a.substring(endInA - best, endInA)
                }

                longestCommonSubstring("abcdxy", "zabcdw")   // "abcd"
            """.trimIndent(),
        ),
        CodeBlock(
            title = "Substring against subsequence, on one input",
            accentColor = 0xFF8B5CF6,
            code = """
                // LCS carries the best answer forward on a mismatch instead of resetting.
                fun longestCommonSubsequence(a: String, b: String): Int {
                    val dp = Array(a.length + 1) { IntArray(b.length + 1) }
                    for (i in 1..a.length) for (j in 1..b.length) {
                        dp[i][j] = if (a[i - 1] == b[j - 1]) dp[i - 1][j - 1] + 1
                                   else maxOf(dp[i - 1][j], dp[i][j - 1])   // ← the difference
                    }
                    return dp[a.length][b.length]
                }

                val a = "abcde"
                val b = "aXbXcXdXe"
                longestCommonSubstring(a, b).length     // 1  — every run is broken by an X
                longestCommonSubsequence(a, b)          // 5  — "abcde", ignoring the gaps

                // Same two strings, answers of 1 and 5. This is why "substring" and
                // "subsequence" are not interchangeable in a problem statement.
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.DpGridVisualizer,
    applications = listOf(
        ApplicationCard("browser", 0xFFEC4899, "Plagiarism & Diff", "Shared passages between documents, and the anchor blocks a diff algorithm builds its alignment around."),
        ApplicationCard("chip", 0xFF3B82F6, "Bioinformatics", "Conserved regions in DNA or protein sequences, where a contiguous match means something a gapped one does not."),
        ApplicationCard("bulb", 0xFF8B5CF6, "Deduplication", "Finding the largest identical block between two files or two versions of a binary."),
    ),
    takeaways = listOf(
        "One clause separates this from LCS: a mismatch resets the cell to zero instead of carrying the best neighbour forward.",
        "The answer is the maximum anywhere in the table; reading the bottom-right corner gives the common suffix instead.",
        "O(n·m) time, and O(min(n, m)) space if only the length is wanted.",
        "Suffix automata solve it in O(n + m) and hashing with binary search in O((n+m) log n) — the DP wins on brevity and on how easily it generalises.",
    ),
    crossLinks = listOf(
        CrossLink("longest_common_subsequence", "Longest Common Subsequence"),
        CrossLink("edit_distance", "Edit Distance"),
        CrossLink("suffix_tree", "Suffix Tree / Suffix Array"),
    ),
)
