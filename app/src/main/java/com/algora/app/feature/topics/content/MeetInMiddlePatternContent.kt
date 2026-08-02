package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

// Interview-prep pattern guide. The escape hatch when n is too big for 2^n but too small for a
// polynomial algorithm to exist — roughly 30 to 40 items.
internal val meetInMiddlePatternContent = TopicContent(
    topicId = "meet_in_middle_pattern",
    whatIsIt = listOf(
        "Meet in the middle splits the input in half, enumerates every subset of each half separately, and joins the two halves through a sorted list or hash map. Two runs of 2^(n/2) replace one run of 2^n.",
        "At n = 40 that is a million-element enumeration twice instead of a trillion-step one. The trade is memory: you must store one half's results to search them.",
    ),
    steps = listOf(
        StepCard(1, "Spot the Signal", "Subset-sum-flavoured questions with n around 30–40 — too large for 2^n, no polynomial algorithm available, and small enough that 2^(n/2) fits in memory.", 0xFFF59E0B),
        StepCard(2, "Split and Enumerate", "Halve the items. Enumerate all 2^(n/2) subset sums of each half, keeping whatever aggregate the question needs.", 0xFF3B82F6),
        StepCard(3, "Prepare One Side", "Sort the second half's sums (or hash them). For \"closest to target\", also make the list prefix-maximal so a binary search returns the best feasible value.", 0xFFEF4444),
        StepCard(4, "Join Across the Split", "For each sum in the first half, binary-search or look up its partner — target - s for exact hits, or the largest value ≤ target - s.", 0xFF10B981),
    ),
    formulas = listOf(
        FormulaEntry("Time", "O(2^(n/2) · n)", "Two enumerations plus a sort and a binary search per entry."),
        FormulaEntry("Space", "O(2^(n/2))", "One half's results must be materialised to be searched."),
        FormulaEntry("Versus brute force", "2^40 → 2 · 2^20", "About a trillion operations down to a few million."),
    ),
    notationKey = listOf(
        NotationEntry("A, B", "the two halves of the item list"),
        NotationEntry("sums(X)", "all subset sums of half X"),
        NotationEntry("target - s", "the partner value sought in the other half"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Closest subset sum to a target (Python)",
            accentColor = 0xFFF59E0B,
            code = """
                from bisect import bisect_right

                def subset_sums(items):
                    out = [0]
                    for x in items:
                        out += [s + x for s in out]        # 2^len(items) sums
                    return out

                def best_subset_sum(items, target):
                    mid = len(items) // 2
                    left = subset_sums(items[:mid])
                    right = sorted(s for s in subset_sums(items[mid:]) if s <= target)
                    best = 0
                    for s in left:
                        if s > target:
                            continue
                        i = bisect_right(right, target - s) - 1   # largest partner that fits
                        if i >= 0:
                            best = max(best, s + right[i])
                    return best
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.RecursionTreeVisualizer,
    applications = listOf(
        ApplicationCard("chip", 0xFF3B82F6, "Hard Subset Sums", "Exact knapsack answers when the capacity is huge but the item count is not."),
        ApplicationCard("lock", 0xFF10B981, "Cryptanalysis", "Meet-in-the-middle attacks on double encryption — the reason 2DES is not 112-bit secure."),
        ApplicationCard("game", 0xFF8B5CF6, "Puzzle Search", "Bidirectional search on state graphs: expand from start and goal, meet in the middle."),
    ),
    takeaways = listOf(
        "n between 30 and 40 in the constraints is the tell — 2^n is out, 2^(n/2) is comfortable.",
        "Enumerate both halves independently; the only coupling is the join.",
        "Sort or hash one side so the join is O(log) or O(1) per entry, not another linear scan.",
        "You are paying memory for time — say the 2^(n/2) space cost before the interviewer asks.",
    ),
    crossLinks = listOf(
        CrossLink("subset_sum", "Subset Sum (Algorithms)"),
        CrossLink("bitmask_state_pattern", "Bitmask State DP"),
        CrossLink("knapsack_dp_pattern", "0/1 Knapsack DP Pattern"),
    ),
)
