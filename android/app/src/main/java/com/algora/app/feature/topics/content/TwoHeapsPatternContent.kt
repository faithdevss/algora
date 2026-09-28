package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.Figure
import com.algora.app.core.data.model.FigureShape
import com.algora.app.core.data.model.FigureStack
import com.algora.app.core.data.model.FigureTone
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

// Interview-prep pattern guide. Uses the standard 7-section template; cross-links to the Data
// Structures topic that supplies the heap.
internal val twoHeapsPatternContent = TopicContent(
    topicId = "two_heaps_pattern",
    figure = Figure(
        caption = "The stream is split in half: a max-heap of the smaller values and a min-heap of the " +
            "larger ones, sizes kept within one of each other. Both middle candidates are roots, so the " +
            "median is O(1) — here (8 + 9) / 2 = 8.5.",
        shape = FigureShape.Stacks(
            columns = listOf(
                FigureStack(
                    label = "lo — max-heap, root first",
                    entries = listOf("8", "5", "3"),
                    note = "the smaller half",
                ),
                FigureStack(
                    label = "hi — min-heap, root first",
                    entries = listOf("9", "10", "15"),
                    tone = FigureTone.Accent,
                    note = "the larger half",
                ),
            ),
        ),
    ),
    whatIsIt = listOf(
        "Two heaps split a stream in half: a max-heap holding the smaller values and a min-heap holding the larger ones. The two roots sit either side of the median, so the middle of the data is always one or two peeks away.",
        "It answers \"what is the middle / what is the k-th boundary right now\" after every insert, without re-sorting anything. Sorting after each of n inserts is O(n² log n); this is O(n log n) total.",
    ),
    steps = listOf(
        StepCard(1, "Spot the Signal", "A running median, or any question about the boundary between a small half and a large half, asked repeatedly as data arrives.", 0xFFF59E0B),
        StepCard(2, "Split the Halves", "lo = max-heap of the smaller half, hi = min-heap of the larger half. Every element of lo is ≤ every element of hi.", 0xFF3B82F6),
        StepCard(3, "Insert then Rebalance", "Push into lo, move lo's top into hi, then move hi's top back if hi grew larger. Sizes stay within one of each other.", 0xFFEF4444),
        StepCard(4, "Read the Answer", "Odd count → the bigger heap's root. Even count → the mean of the two roots.", 0xFF10B981),
    ),
    formulas = listOf(
        FormulaEntry("Insert", "O(log n)", "One push and at most two pops across the two heaps."),
        FormulaEntry("Query median", "O(1)", "Both candidates are heap roots — no scan, no sort."),
        FormulaEntry("Size invariant", "0 ≤ |lo| - |hi| ≤ 1", "lo may hold the extra element so the odd-count median is lo's root."),
    ),
    notationKey = listOf(
        NotationEntry("lo", "max-heap of the smaller half"),
        NotationEntry("hi", "min-heap of the larger half"),
        NotationEntry("-x", "negation trick that turns Python's min-heap into a max-heap"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Running median (Python)",
            accentColor = 0xFFF59E0B,
            code = """
                import heapq

                class MedianStream:
                    def __init__(self):
                        self.lo = []            # max-heap via negated values
                        self.hi = []            # min-heap

                    def add(self, x):
                        heapq.heappush(self.lo, -x)
                        heapq.heappush(self.hi, -heapq.heappop(self.lo))   # largest of lo -> hi
                        if len(self.hi) > len(self.lo):                    # keep lo the bigger half
                            heapq.heappush(self.lo, -heapq.heappop(self.hi))

                    def median(self):
                        if len(self.lo) > len(self.hi):
                            return -self.lo[0]
                        return (-self.lo[0] + self.hi[0]) / 2
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.ArrayWalkPlayer,
    applications = listOf(
        ApplicationCard("chart", 0xFF3B82F6, "Streaming Statistics", "Median latency or median price over an unbounded feed, updated per event."),
        ApplicationCard("target", 0xFF10B981, "Sliding Window Median", "Same two heaps plus lazy deletion of the element leaving the window."),
        ApplicationCard("users", 0xFF8B5CF6, "Scheduling Splits", "IPO-style problems: one heap for what is affordable now, one for what is not yet."),
    ),
    takeaways = listOf(
        "Two heaps buy O(1) median for O(log n) insert — the sorted order you never fully materialise.",
        "The invariant is everything: max(lo) ≤ min(hi), sizes differing by at most one.",
        "Always push-then-transfer; inserting straight into the \"correct\" heap breaks the ordering invariant.",
        "For a sliding window, pair the heaps with a delete-later map — heaps cannot remove an interior element.",
    ),
    crossLinks = listOf(
        CrossLink("heap", "Heap (Data Structures)"),
        CrossLink("priority_queue_adt", "Priority Queue (Data Structures)"),
        CrossLink("top_k_pattern", "Top-K Pattern"),
    ),
)
