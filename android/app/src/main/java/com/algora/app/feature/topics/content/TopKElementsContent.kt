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

internal val topKElementsContent = TopicContent(
    topicId = "top_k_elements",
    figure = Figure(
        caption = "Only k elements are ever held. Each arriving value is compared against the weakest of " +
            "them and either takes its place or is dropped on the spot — and the structure holding them " +
            "is a *min*-heap even though the question asks for the largest, which is the part that gets " +
            "written backwards. The root being the smallest of the best three is exactly what makes the " +
            "comparison O(1). That gives O(n log k) time and O(k) space, against a full sort's " +
            "O(n log n) and O(n), and it is the only version that works at all when the input is a " +
            "stream too long to store. Two boundaries: when k approaches n the heap stops helping and " +
            "sorting wins, and when the whole array is in memory and only the k-th element is wanted, " +
            "quickselect gets it in O(n) expected time — at the cost of reordering the input.",
        shape = FigureShape.Stacks(
            columns = listOf(
                FigureStack(
                    label = "heap, k = 3",
                    entries = listOf("5", "8", "9"),
                    tone = FigureTone.Accent,
                    note = "smallest on top — the one an arrival is measured against",
                ),
                FigureStack(
                    label = "dropped",
                    entries = listOf("4", "2", "1"),
                    tone = FigureTone.Warn,
                    note = "lost to the root and never stored",
                ),
            ),
        ),
    ),
    whatIsIt = listOf(
        "The Top-K problem asks for the k largest (or smallest, or most frequent) elements of a collection — without paying to fully sort everything.",
        "A size-k heap is the standard tool: keep only the best k seen so far, evicting the weakest as better elements arrive, for O(n log k) time.",
    ),
    steps = listOf(
        StepCard(1, "Keep a Size-k Heap", "Use a min-heap of size k when seeking the k largest (its root is the weakest kept).", 0xFF10B981),
        StepCard(2, "Push Each Element", "Add the next element to the heap.", 0xFF3B82F6),
        StepCard(3, "Evict the Weakest", "If the heap exceeds k, pop the root, discarding the smallest of the kept set.", 0xFFF59E0B),
        StepCard(4, "Heap Holds the Answer", "After one pass, the heap contains exactly the top k elements.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Heap approach", "O(n log k)", "n pushes, each an O(log k) heap operation."),
        FormulaEntry("Quickselect approach", "O(n) average", "Partition to the k-th boundary, no ordering within."),
        FormulaEntry("Space", "O(k)", "Only the running top-k set is stored."),
    ),
    notationKey = listOf(
        NotationEntry("k", "how many top elements to return"),
        NotationEntry("n", "total number of elements"),
        NotationEntry("min-heap root", "the weakest of the currently kept k"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Top-K largest with a min-heap",
            accentColor = 0xFF6366F1,
            code = """
                fun topK(a: IntArray, k: Int): List<Int> {
                    val heap = java.util.PriorityQueue<Int>()   // min-heap
                    for (x in a) {
                        heap.add(x)
                        if (heap.size > k) heap.poll()          // drop the smallest kept
                    }
                    return heap.toList()
                }
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.ArrayWalkPlayer,
    applications = listOf(
        ApplicationCard("trend", 0xFF10B981, "Leaderboards & Trending", "Top scores, trending hashtags, and most-viewed items are streaming top-K queries."),
        ApplicationCard("search", 0xFF3B82F6, "Search Ranking", "Returning the k best-matching results keeps only a heap of candidates, not a full sort."),
        ApplicationCard("chart", 0xFFF59E0B, "Top Frequencies", "Pairing a frequency map with a size-k heap yields the k most common elements."),
    ),
    takeaways = listOf(
        "A size-k heap finds the top k in O(n log k) using O(k) space.",
        "Quickselect finds them in O(n) average time when the data fits in memory and can be reordered.",
        "The heap approach shines on streams, where n is unbounded and only k is retained.",
        "For 'most frequent', combine a hash-map count with the same heap idea.",
    ),
    crossLinks = listOf(
        CrossLink("heap", "Heap (Min / Max)"),
        CrossLink("quickselect", "Quickselect"),
    ),
)
