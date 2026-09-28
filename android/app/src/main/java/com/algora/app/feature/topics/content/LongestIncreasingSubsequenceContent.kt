package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.Figure
import com.algora.app.core.data.model.FigurePointer
import com.algora.app.core.data.model.FigureShape
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val longestIncreasingSubsequenceContent = TopicContent(
    topicId = "longest_increasing_subsequence",
    figure = Figure(
        caption = "The aux row is dp[i]: the longest increasing run ending *exactly* at i. Computing one " +
            "cell means scanning every earlier cell with a smaller value, which is where the O(n²) " +
            "comes from, and the answer is the maximum anywhere in that row rather than its last entry " +
            "— the 4 at index 5 happens to be last here, but the 9 could have been a 0 and the answer " +
            "would still be 4. The subsequence itself is 1, 4, 5, 9, and it is not contiguous. The " +
            "O(n log n) version drops dp entirely and keeps a tails array instead — tails[k] is the " +
            "smallest value any length-k+1 run can end on — binary-searching each value into place. " +
            "Its length is the answer, but its contents are not a real subsequence.",
        shape = FigureShape.Strip(
            cells = listOf("3", "1", "4", "1", "5", "9"),
            pointers = listOf(FigurePointer(5, "max = 4")),
            aux = listOf("1", "1", "2", "1", "3", "4"),
            auxLabel = "dp[i] — longest run ending at i, not the answer so far",
        ),
    ),
    whatIsIt = listOf(
        "The longest increasing subsequence (LIS) is the longest run of strictly increasing values you can pull from a sequence while keeping their original order.",
        "It has two classic solutions: an intuitive O(n²) DP, and a slicker O(n log n) method that keeps the smallest possible tail for each length using binary search.",
    ),
    steps = listOf(
        StepCard(1, "DP State", "dp[i] = the length of the longest increasing subsequence ending exactly at index i.", 0xFF10B981),
        StepCard(2, "Extend from the Left", "dp[i] = 1 + max dp[j] over all earlier j with a[j] < a[i].", 0xFF3B82F6),
        StepCard(3, "The n log n Trick", "Maintain 'tails': tails[k] is the smallest possible ending value of an increasing subsequence of length k+1.", 0xFFF59E0B),
        StepCard(4, "Binary Search Insert", "For each value, replace the first tail ≥ it (or append) — the tails array's length is the LIS length.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("DP transition", "dp[i] = 1 + max(dp[j] : j < i, a[j] < a[i])", "Straightforward O(n²)."),
        FormulaEntry("Patience method", "O(n log n)", "Binary-search each element into the tails array."),
        FormulaEntry("Answer", "max dp[i] / len(tails)", "The longest achievable increasing run."),
    ),
    notationKey = listOf(
        NotationEntry("n", "length of the sequence"),
        NotationEntry("dp[i]", "LIS length ending at index i"),
        NotationEntry("tails[k]", "smallest tail of any length-(k+1) increasing subsequence"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "LIS in O(n log n)",
            accentColor = 0xFF6366F1,
            code = """
                fun lengthOfLIS(a: IntArray): Int {
                    val tails = mutableListOf<Int>()
                    for (x in a) {
                        // First index whose tail is >= x (lower_bound).
                        var lo = 0; var hi = tails.size
                        while (lo < hi) {
                            val mid = (lo + hi) / 2
                            if (tails[mid] < x) lo = mid + 1 else hi = mid
                        }
                        if (lo == tails.size) tails.add(x) else tails[lo] = x
                    }
                    return tails.size
                }
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.DpGridVisualizer,
    applications = listOf(
        ApplicationCard("trend", 0xFF10B981, "Trend Analysis", "Finding the longest upward run in prices, scores, or metrics over time."),
        ApplicationCard("chip", 0xFF3B82F6, "Version & Patch Ordering", "Longest compatible ordered chain of items shows up in scheduling and diffing."),
        ApplicationCard("flask", 0xFFF59E0B, "Box/Envelope Stacking", "Nesting problems (Russian-doll envelopes) reduce to LIS after sorting one dimension."),
    ),
    takeaways = listOf(
        "LIS keeps the longest strictly increasing subsequence in original order.",
        "The simple DP is O(n²); the patience/binary-search method is O(n log n).",
        "The tails array trick stores the best (smallest) ending value per subsequence length.",
        "Many nesting and chaining problems reduce to LIS after a sort.",
    ),
    crossLinks = listOf(
        CrossLink("longest_common_subsequence", "Longest Common Subsequence"),
        CrossLink("binary_search", "Binary Search"),
    ),
)
