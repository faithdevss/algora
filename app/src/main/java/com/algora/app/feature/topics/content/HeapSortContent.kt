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

internal val heapSortContent = TopicContent(
    topicId = "heap_sort",
    figure = Figure(
        caption = "Two regions in one array: a max-heap on the left, the finished tail on the right. Each " +
            "step swaps the root — the largest remaining — into the tail and sifts the new root down. " +
            "O(n log n) guaranteed with O(1) extra space, but its cache behaviour and lack of stability " +
            "are why quicksort usually wins in practice.",
        shape = FigureShape.Strip(
            cells = listOf("7", "5", "6", "2", "8", "9"),
            bands = listOf(
                FigureBand(0, 3, "heap region"),
                FigureBand(4, 5, "sorted", FigureTone.Muted),
            ),
            pointers = listOf(FigurePointer(0, "root"), FigurePointer(3, "swap to")),
        ),
    ),
    whatIsIt = listOf(
        "Heap sort turns the array into a max-heap, then repeatedly extracts the largest element to the end, shrinking the heap until the array is sorted.",
        "It combines the O(1) max access of a heap with in-place array storage — no extra memory and a guaranteed O(n log n) worst case.",
    ),
    steps = listOf(
        StepCard(1, "Build a Max-Heap", "Heapify the array in place so the largest element sits at the root (index 0).", 0xFF10B981),
        StepCard(2, "Swap Max to the End", "Swap the root with the last heap element — the max is now in its final sorted spot.", 0xFF3B82F6),
        StepCard(3, "Shrink & Sift Down", "Reduce the heap size by one and sift the new root down to restore the heap property.", 0xFFF59E0B),
        StepCard(4, "Repeat", "Keep extracting the max until the heap holds one element and the array is sorted.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Time (all cases)", "O(n log n)", "n extractions, each an O(log n) sift-down."),
        FormulaEntry("Build heap", "O(n)", "Bottom-up heapify is linear, not n log n."),
        FormulaEntry("Space", "O(1)", "Sorts fully in place — no auxiliary array."),
    ),
    notationKey = listOf(
        NotationEntry("n", "number of elements"),
        NotationEntry("i", "node index; children at 2i+1 and 2i+2"),
        NotationEntry("sift-down", "moving a node down to restore the heap property"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Heap sort",
            accentColor = 0xFF6366F1,
            code = """
                fun heapSort(a: IntArray) {
                    val n = a.size
                    for (i in n / 2 - 1 downTo 0) siftDown(a, i, n)
                    for (end in n - 1 downTo 1) {
                        a[0] = a[end].also { a[end] = a[0] }
                        siftDown(a, 0, end)
                    }
                }

                private fun siftDown(a: IntArray, start: Int, size: Int) {
                    var i = start
                    while (true) {
                        val l = 2 * i + 1; val r = 2 * i + 2
                        var largest = i
                        if (l < size && a[l] > a[largest]) largest = l
                        if (r < size && a[r] > a[largest]) largest = r
                        if (largest == i) break
                        a[i] = a[largest].also { a[largest] = a[i] }
                        i = largest
                    }
                }
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.SortingVisualizer,
    applications = listOf(
        ApplicationCard("check", 0xFF10B981, "Guaranteed Worst Case", "When O(n log n) must hold even on adversarial input, heap sort avoids quicksort's O(n²) risk."),
        ApplicationCard("chip", 0xFF3B82F6, "Memory-Constrained Systems", "Its O(1) extra space suits embedded and kernel code that can't allocate a merge buffer."),
        ApplicationCard("target", 0xFFF59E0B, "Introsort Fallback", "Hybrid sorts start with quicksort and switch to heap sort when recursion goes too deep."),
    ),
    takeaways = listOf(
        "Heap sort is O(n log n) in every case and sorts in place with O(1) extra memory.",
        "It relies on the heap property: repeatedly extract the max and shrink the heap.",
        "It is not stable and has poor cache locality, so quicksort often beats it in wall-clock time.",
        "Its predictable worst case makes it the safety net inside hybrid sorts like introsort.",
    ),
    crossLinks = listOf(
        CrossLink("heap", "Heap (Min / Max)"),
        CrossLink("quick_sort", "Quick Sort"),
    ),
)
