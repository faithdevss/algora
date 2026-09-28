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

internal val subsetSumContent = TopicContent(
    topicId = "subset_sum",
    figure = Figure(
        caption = "Elements {3, 4, 2} against target 6. Each element is one binary decision — take it and " +
            "the target drops, skip it and the target stands — so the tree is 2ⁿ leaves wide and the " +
            "node label is the whole state. Only one thing prunes: a target that has gone negative can " +
            "never come back, so that branch dies immediately. The success leaf is 4 + 2, reached by " +
            "skipping the first element. The state at every node is just (elements left, target), and " +
            "there are far fewer of those pairs than branches — which is the observation the DP table is built on.",
        shape = FigureShape.Tree(
            nodes = listOf(
                FigureNode("t=6", null, FigureTone.Primary),
                FigureNode("t=3", 0, FigureTone.Primary),
                FigureNode("t=6", 0),
                FigureNode("t=−1", 1, FigureTone.Warn),
                FigureNode("t=3", 1),
                FigureNode("t=2", 2, FigureTone.Primary),
                FigureNode("t=0", 5, FigureTone.Accent),
            ),
        ),
    ),
    whatIsIt = listOf(
        "Subset sum asks whether any subset of a set of numbers adds up to a given target — a fundamental yes/no decision problem.",
        "The recursive view makes a binary choice for each element: include it (subtract from the target) or skip it, then recurse on the rest.",
    ),
    steps = listOf(
        StepCard(1, "Base Cases", "Target 0 means success (empty subset works); no elements left with target ≠ 0 means failure.", 0xFF10B981),
        StepCard(2, "Include the Element", "Take the current number and recurse on the remainder with target − value.", 0xFF3B82F6),
        StepCard(3, "Exclude the Element", "Skip the current number and recurse on the remainder with the same target.", 0xFFF59E0B),
        StepCard(4, "Combine", "The answer is yes if either the include or the exclude branch succeeds.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Naive recursion", "O(2ⁿ)", "Two branches per element — the full subset tree."),
        FormulaEntry("DP (pseudo-poly)", "O(n · target)", "Memoize (index, remaining) states."),
        FormulaEntry("Choice", "include or exclude", "Each element is a binary decision."),
    ),
    notationKey = listOf(
        NotationEntry("n", "number of elements"),
        NotationEntry("target", "the sum being sought"),
        NotationEntry("state", "(index, remaining target) — memoization key"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Subset sum — recursive decision",
            accentColor = 0xFF6366F1,
            code = """
                fun subsetSum(nums: IntArray, target: Int, i: Int = 0): Boolean {
                    if (target == 0) return true               // found a subset
                    if (i == nums.size || target < 0) return false
                    // include nums[i]  OR  skip it
                    return subsetSum(nums, target - nums[i], i + 1) ||
                           subsetSum(nums, target, i + 1)
                }
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.DpGridVisualizer,
    applications = listOf(
        ApplicationCard("finance", 0xFF10B981, "Budget & Partition", "Splitting expenses evenly or hitting an exact spend target is subset sum in disguise."),
        ApplicationCard("chip", 0xFF3B82F6, "Load Balancing", "Partitioning tasks into equal-weight groups reduces to subset-sum decisions."),
        ApplicationCard("lock", 0xFFF59E0B, "Cryptography Roots", "Early knapsack-based cryptosystems were built on the hardness of subset sum."),
    ),
    takeaways = listOf(
        "Subset sum recurses on a binary include/exclude choice for each element.",
        "Naive recursion is O(2ⁿ); memoizing (index, remaining) gives pseudo-polynomial O(n · target).",
        "It is NP-complete, and its structure underlies 0/1 knapsack and set partition.",
        "The include/exclude pattern is the template for most subset-enumeration problems.",
    ),
    crossLinks = listOf(
        CrossLink("knapsack_01", "0/1 Knapsack"),
        CrossLink("n_queens", "N-Queens"),
    ),
)
