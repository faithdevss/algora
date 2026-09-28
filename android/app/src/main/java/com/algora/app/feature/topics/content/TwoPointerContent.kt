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

internal val twoPointerContent = TopicContent(
    topicId = "two_pointer",
    figure = Figure(
        caption = "Sorted input is the precondition, and the pair sums to the target 10. At every step " +
            "the comparison decides which pointer moves, and the decision is forced: a sum that is too " +
            "small can only be fixed by moving the left pointer right, a sum that is too big only by " +
            "moving the right pointer left. Each move therefore discards an entire row or column of " +
            "the pair matrix a nested loop would have visited, which is why one linear pass replaces " +
            "O(n²) and why no memory is needed to remember what was ruled out. The other half of the " +
            "technique is the same-direction variant — one pointer reading and one writing, at " +
            "different speeds — which is how in-place removal, deduplication and cycle detection are " +
            "done, and it needs no sorted input at all.",
        shape = FigureShape.Strip(
            cells = listOf("1", "3", "4", "6", "8", "11"),
            bands = listOf(
                FigureBand(0, 1, "too small"),
                FigureBand(2, 3, "sum = 10", FigureTone.Accent),
                FigureBand(4, 5, "too big", FigureTone.Warn),
            ),
            pointers = listOf(
                FigurePointer(2, "l"),
                FigurePointer(3, "r"),
            ),
        ),
    ),
    whatIsIt = listOf(
        "The two-pointer technique scans a sequence with two indices that move under a rule, replacing a nested double loop with a single linear pass.",
        "The pointers either converge from both ends (opposite-direction) or chase each other in one direction (same-direction), depending on the problem.",
    ),
    steps = listOf(
        StepCard(1, "Place Two Pointers", "Start them at both ends, or both at the front, depending on the pattern.", 0xFF10B981),
        StepCard(2, "Evaluate the Pair", "Check the condition on the current pair — a sum, a match, or a gap.", 0xFF3B82F6),
        StepCard(3, "Move One Pointer", "Advance the pointer that makes progress toward the goal (e.g. raise the low end to increase a sum).", 0xFFF59E0B),
        StepCard(4, "Converge", "Continue until the pointers meet or cross, having covered all useful pairs in O(n).", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Time", "O(n)", "Each pointer moves forward at most n steps total."),
        FormulaEntry("Space", "O(1)", "Just two indices."),
        FormulaEntry("Precondition", "often sorted", "Opposite-direction pair problems usually need sorted input."),
    ),
    notationKey = listOf(
        NotationEntry("lo, hi", "converging pointers from both ends"),
        NotationEntry("slow, fast", "same-direction chasing pointers"),
        NotationEntry("n", "sequence length"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Two-sum on a sorted array",
            accentColor = 0xFF6366F1,
            code = """
                fun twoSumSorted(a: IntArray, target: Int): Pair<Int, Int>? {
                    var lo = 0
                    var hi = a.size - 1
                    while (lo < hi) {
                        val sum = a[lo] + a[hi]
                        when {
                            sum == target -> return lo to hi
                            sum < target -> lo++    // need a bigger sum
                            else -> hi--            // need a smaller sum
                        }
                    }
                    return null
                }
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.ArrayWalkPlayer,
    applications = listOf(
        ApplicationCard("target", 0xFF10B981, "Pair & Triplet Sums", "Two-sum, three-sum, and container-with-most-water all collapse to converging pointers."),
        ApplicationCard("link", 0xFF3B82F6, "In-Place Array Edits", "Removing duplicates or partitioning uses a slow write pointer and a fast read pointer."),
        ApplicationCard("history", 0xFFF59E0B, "Merging & Comparing", "Merging two sorted lists or comparing sequences advances one pointer per step."),
    ),
    takeaways = listOf(
        "Two pointers replace a nested loop with one O(n) pass and O(1) space.",
        "Opposite-direction pointers usually require sorted data; same-direction ones don't.",
        "Move the pointer that makes measurable progress toward the condition.",
        "Sliding window and fast-slow cycle detection are specialized two-pointer patterns.",
    ),
    crossLinks = listOf(
        CrossLink("two_pointer_pattern", "Two Pointer Technique"),
        CrossLink("sliding_window", "Sliding Window"),
    ),
)
