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

internal val twoPointerPatternContent = TopicContent(
    topicId = "two_pointer_pattern",
    figure = Figure(
        caption = "Sorted input turns one comparison into a decision about a whole row of pairs: too " +
            "small and every pair with l is out, too large and every pair with r is. That is what " +
            "collapses the O(n²) double loop into one pass.",
        shape = FigureShape.Strip(
            cells = listOf("2", "3", "5", "8", "11", "14"),
            bands = listOf(FigureBand(0, 5, "still possible", FigureTone.Muted)),
            pointers = listOf(FigurePointer(0, "l"), FigurePointer(5, "r")),
        ),
    ),
    whatIsIt = listOf(
        "The two-pointer pattern walks an array or string with two indices instead of one, letting you answer pair/range questions in a single O(n) pass with O(1) space.",
        "The pointers either close in from both ends or chase each other from the same side, depending on what the problem needs.",
    ),
    steps = listOf(
        StepCard(1, "Place the Pointers", "Opposite ends for sorted-pair problems; both at the start for in-place partitioning.", 0xFF3B82F6),
        StepCard(2, "Compare & Decide", "Read both positions and decide which pointer to move based on the target condition.", 0xFF8B5CF6),
        StepCard(3, "Move Inward or Forward", "Converging pointers shrink the range; same-direction pointers advance the fast one and conditionally the slow one.", 0xFFF59E0B),
        StepCard(4, "Stop at the Crossing", "Finish when the pointers meet or cross — every element is touched at most once.", 0xFF10B981),
    ),
    formulas = listOf(
        FormulaEntry("Time", "O(n)", "One linear sweep — often after an O(n log n) sort."),
        FormulaEntry("Space", "O(1)", "Just the two index variables."),
        FormulaEntry("Brute force it replaces", "O(n²)", "Checking every pair with nested loops."),
    ),
    notationKey = listOf(
        NotationEntry("i, j", "the two pointers"),
        NotationEntry("n", "length of the input"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Two-sum on a sorted array (Python)",
            accentColor = 0xFF3B82F6,
            code = """
                def two_sum_sorted(a, target):
                    i, j = 0, len(a) - 1
                    while i < j:
                        s = a[i] + a[j]
                        if s == target:
                            return (i, j)
                        if s < target:
                            i += 1      # need a bigger sum
                        else:
                            j -= 1      # need a smaller sum
                    return None
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.ArrayWalkPlayer,
    applications = listOf(
        ApplicationCard("search", 0xFF3B82F6, "Pair & Triplet Sums", "Two-sum / three-sum on sorted data, container-with-most-water."),
        ApplicationCard("share", 0xFF8B5CF6, "In-Place Rearrangement", "Move zeros, remove duplicates, partition around a pivot without extra memory."),
        ApplicationCard("help", 0xFFF59E0B, "Palindrome Checks", "Compare characters from both ends walking inward."),
    ),
    takeaways = listOf(
        "Turns pair-finding on sorted data from O(n²) into O(n).",
        "Converging pointers suit sorted arrays; same-direction pointers suit in-place filters.",
        "O(1) space is the headline advantage over a hash-map approach.",
        "Often the follow-up optimization after a brute-force nested loop.",
    ),
    crossLinks = listOf(
        CrossLink("two_pointer", "Two Pointer Technique (Algorithms)"),
        CrossLink("fast_slow_pointers", "Fast & Slow Pointers"),
    ),
)
