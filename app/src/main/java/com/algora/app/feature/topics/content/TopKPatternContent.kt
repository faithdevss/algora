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
internal val topKPatternContent = TopicContent(
    topicId = "top_k_pattern",
    whatIsIt = listOf(
        "The top-K pattern finds the K largest, smallest, or most-frequent items without fully sorting the input, by keeping only a size-K heap as it scans.",
        "Reach for it whenever a problem asks for 'the K best' of something — you need the top handful, not a total ordering, so a full sort is wasted work.",
    ),
    steps = listOf(
        StepCard(1, "Spot the Signal", "The question wants the K largest / smallest / most-frequent, and K is much smaller than n.", 0xFFF59E0B),
        StepCard(2, "Pick the Opposite Heap", "For the K largest, keep a MIN-heap of size K: its root is the weakest of your current winners.", 0xFF3B82F6),
        StepCard(3, "Push, then Cap", "Add each element; if the heap exceeds K, pop the root. Anything worse than the root never survives.", 0xFF8B5CF6),
        StepCard(4, "Drain the Heap", "What remains are the K best. Total cost is O(n log K) — cheaper than O(n log n) when K ≪ n.", 0xFF10B981),
    ),
    formulas = listOf(
        FormulaEntry("Time (heap)", "O(n log K)", "n pushes/pops against a heap capped at K."),
        FormulaEntry("Time (quickselect)", "O(n) avg", "Partition-based selection beats the heap when all K come at once."),
        FormulaEntry("Space", "O(K)", "Only the K candidates are ever held."),
    ),
    notationKey = listOf(
        NotationEntry("K", "how many top items are wanted"),
        NotationEntry("n", "size of the input"),
        NotationEntry("root", "heap top — the current cut-off among the winners"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "K largest with a size-K min-heap (Python)",
            accentColor = 0xFFF59E0B,
            code = """
                import heapq

                def k_largest(nums, k):
                    heap = []                    # min-heap of the current winners
                    for x in nums:
                        heapq.heappush(heap, x)
                        if len(heap) > k:
                            heapq.heappop(heap)  # drop the weakest
                    return sorted(heap, reverse=True)
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.ArrayWalkPlayer,
    applications = listOf(
        ApplicationCard("trend", 0xFF10B981, "Leaderboards & Trending", "Top-K scores, hottest search terms, or most-active users from a huge stream."),
        ApplicationCard("search", 0xFF3B82F6, "K Nearest Neighbours", "Keeping the K closest points/vectors while scanning a large dataset once."),
        ApplicationCard("chip", 0xFF8B5CF6, "Streaming Analytics", "Maintaining top-K over an unbounded feed in bounded O(K) memory."),
    ),
    takeaways = listOf(
        "Use the opposite heap: min-heap for the K largest, max-heap for the K smallest.",
        "The heap root is the cut-off — anything worse is discarded on sight.",
        "O(n log K) beats sorting's O(n log n) whenever K is much smaller than n.",
        "Need all K at once (not streaming)? Quickselect gives O(n) average.",
    ),
    crossLinks = listOf(
        CrossLink("top_k_elements", "Top-K Elements (Algorithms)"),
        CrossLink("heap", "Heap / Priority Queue"),
        CrossLink("faang_set", "Practice: FAANG Set"),
    ),
)
