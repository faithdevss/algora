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
internal val kWayMergePatternContent = TopicContent(
    topicId = "k_way_merge_pattern",
    whatIsIt = listOf(
        "K-way merge walks k already-sorted inputs at once. A min-heap holding one candidate per list — the current head of each — always knows which element comes next globally.",
        "The heap never grows past k, so merging n total elements costs O(n log k) rather than the O(n log n) of concatenating everything and sorting.",
    ),
    steps = listOf(
        StepCard(1, "Spot the Signal", "Several sorted lists, arrays or streams, and a question about their merged order.", 0xFFF59E0B),
        StepCard(2, "Seed the Heap", "Push the first element of each list, tagged with which list it came from and its position there.", 0xFF3B82F6),
        StepCard(3, "Pop and Refill", "Pop the smallest, append it to the output, then push that list's next element. The heap stays at size ≤ k.", 0xFF8B5CF6),
        StepCard(4, "Stop on the Answer", "Full merge, kth smallest, or smallest range covering all lists — the loop ends when the question is answered, not when the data runs out.", 0xFF10B981),
    ),
    formulas = listOf(
        FormulaEntry("Time", "O(n log k)", "n pops and pushes against a heap of at most k entries."),
        FormulaEntry("Space", "O(k)", "One in-flight candidate per list."),
        FormulaEntry("Naive alternative", "O(n log n)", "Concatenate then sort — throws away the existing order."),
    ),
    notationKey = listOf(
        NotationEntry("k", "number of sorted inputs"),
        NotationEntry("n", "total elements across all inputs"),
        NotationEntry("(value, list, idx)", "heap entry: what, from where, and how far in"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Merge k sorted lists (Python)",
            accentColor = 0xFFF59E0B,
            code = """
                import heapq

                def merge_k(lists):
                    heap = [(l[0], i, 0) for i, l in enumerate(lists) if l]
                    heapq.heapify(heap)
                    out = []
                    while heap:
                        value, li, idx = heapq.heappop(heap)
                        out.append(value)
                        if idx + 1 < len(lists[li]):
                            heapq.heappush(heap, (lists[li][idx + 1], li, idx + 1))
                    return out
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.ArrayWalkPlayer,
    applications = listOf(
        ApplicationCard("stack", 0xFF3B82F6, "External Sorting", "Merging sorted runs from disk when the data won't fit in memory."),
        ApplicationCard("network", 0xFF10B981, "Log Interleaving", "Combining per-shard, per-service log streams into one timeline."),
        ApplicationCard("target", 0xFF8B5CF6, "Kth Smallest Queries", "Kth element of a sorted matrix, or the smallest range covering k lists."),
    ),
    takeaways = listOf(
        "The heap's size is k, not n — that's where the log k comes from.",
        "Every entry must carry its origin, or you can't refill the list it came from.",
        "Sorted rows and columns (a sorted matrix) are just k sorted lists in disguise.",
        "For the kth smallest, stop after k pops instead of draining everything.",
    ),
    crossLinks = listOf(
        CrossLink("merge_sort", "Merge Sort (Algorithms)"),
        CrossLink("heap", "Heap (Data Structures)"),
        CrossLink("top_k_pattern", "Top-K Pattern"),
    ),
)
