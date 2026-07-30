package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

// Interview-prep pattern guide. Uses the standard 7-section template; cross-links to the Algorithms
// topic that implements the technique.
internal val subsetsPatternContent = TopicContent(
    topicId = "subsets_pattern",
    whatIsIt = listOf(
        "Every element faces one binary decision — in or out — so the power set of n items has 2ⁿ members, and any enumeration is a walk over those decisions.",
        "Two equivalent framings show up in interviews: a recursive include/exclude tree, and an iterative pass that doubles the answer list by cloning it with each new element appended. Duplicates in the input are what turn this from a template into a real question.",
    ),
    steps = listOf(
        StepCard(1, "Spot the Signal", "\"All subsets / combinations of size k / power set\", usually with n small (≤ 20).", 0xFFF59E0B),
        StepCard(2, "Decide Per Element", "At index i: recurse having taken a[i], then recurse having skipped it. Depth n, 2ⁿ leaves.", 0xFF3B82F6),
        StepCard(3, "Handle Duplicates", "Sort, then skip a[i] when a[i] == a[i-1] and i > start — that one guard removes repeated subsets.", 0xFFEF4444),
        StepCard(4, "Or Iterate by Doubling", "Start with [[]]; for each element, append copies of every existing subset with it added.", 0xFF10B981),
    ),
    formulas = listOf(
        FormulaEntry("Subsets", "2ⁿ", "Each element is independently in or out."),
        FormulaEntry("Combinations of size k", "C(n, k) = n! / (k!(n−k)!)", "Fixing the size prunes the tree at depth k."),
        FormulaEntry("Total work", "O(n · 2ⁿ)", "2ⁿ subsets, each costing O(n) to copy out."),
    ),
    notationKey = listOf(
        NotationEntry("n", "number of input elements"),
        NotationEntry("start", "first index still available to pick"),
        NotationEntry("mask", "bitmask alternative: bit i set means element i is in"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Subsets with duplicate handling (Python)",
            accentColor = 0xFFF59E0B,
            code = """
                def subsets(nums):
                    nums.sort()                       # duplicates must be adjacent
                    out, path = [], []

                    def walk(start):
                        out.append(path[:])
                        for i in range(start, len(nums)):
                            if i > start and nums[i] == nums[i - 1]:
                                continue              # same value already tried at this depth
                            path.append(nums[i])
                            walk(i + 1)
                            path.pop()

                    walk(0)
                    return out
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.RecursionTreeVisualizer,
    applications = listOf(
        ApplicationCard("functions", 0xFF3B82F6, "Feature Selection", "Trying every subset of features on a small candidate set."),
        ApplicationCard("chip", 0xFF10B981, "Config Enumeration", "All valid flag combinations for exhaustive testing."),
        ApplicationCard("target", 0xFF8B5CF6, "Subset-Sum Family", "Partition equal subset sum, target sum, and their DP rewrites."),
    ),
    takeaways = listOf(
        "2ⁿ is the floor for enumerating everything — this pattern is only viable for small n.",
        "Record the subset on entry, not at a leaf; every node of the tree is a valid subset.",
        "Sort plus the `i > start && a[i] == a[i-1]` guard is the standard duplicate fix.",
        "For n ≤ 20, the bitmask form (loop masks 0..2ⁿ−1) is shorter than the recursion.",
    ),
    crossLinks = listOf(
        CrossLink("subsets_bitmask", "Subsets using Bitmask (Algorithms)"),
        CrossLink("permutation_generation", "Permutation Generation (Algorithms)"),
        CrossLink("backtracking_pattern", "Backtracking Pattern"),
    ),
)
