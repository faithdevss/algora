package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.Figure
import com.algora.app.core.data.model.FigureBand
import com.algora.app.core.data.model.FigureShape
import com.algora.app.core.data.model.FigureTone
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val countingSortContent = TopicContent(
    topicId = "counting_sort",
    figure = Figure(
        caption = "No comparisons at all: count how many of each value there are, prefix-sum the counts " +
            "to get final positions, then place each element. O(n + k) — linear in the input *and* the " +
            "value range, so it wins only while k stays small. Walking the input backwards is what keeps " +
            "it stable, which is what radix sort then depends on.",
        shape = FigureShape.Strip(
            cells = listOf("0", "2", "3", "1", "0", "1"),
            bands = listOf(FigureBand(2, 2, "three 2s", FigureTone.Accent)),
            aux = listOf("0", "2", "5", "6", "6", "7"),
            auxLabel = "prefix-summed counts — where each value's block ends",
        ),
    ),
    whatIsIt = listOf(
        "Counting sort orders integers without comparing them: it counts how many times each key appears, then uses those counts to place every element directly into its final position.",
        "It beats the O(n log n) comparison-sort barrier by exploiting a small, known range of keys — but only when that range k is not much larger than n.",
    ),
    steps = listOf(
        StepCard(1, "Count Occurrences", "Tally how many times each key value appears in a count array of size k.", 0xFF10B981),
        StepCard(2, "Prefix-Sum the Counts", "Accumulate the counts so each entry becomes the ending index for that key.", 0xFF3B82F6),
        StepCard(3, "Place Into Output", "Walk the input (right to left for stability) and drop each element at its computed index.", 0xFFF59E0B),
        StepCard(4, "Copy Back", "The output array is now sorted; copy it over the input if needed.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Time", "O(n + k)", "One pass to count, one to place; k is the size of the key range."),
        FormulaEntry("Space", "O(n + k)", "A count array of size k plus an output array of size n."),
        FormulaEntry("Best when", "k = O(n)", "Degrades badly once the key range dwarfs the element count."),
    ),
    notationKey = listOf(
        NotationEntry("n", "number of elements"),
        NotationEntry("k", "size of the key range (max − min + 1)"),
        NotationEntry("count[]", "tally of occurrences per key, later prefix-summed"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Counting sort (non-negative keys)",
            accentColor = 0xFF6366F1,
            code = """
                fun countingSort(a: IntArray, k: Int): IntArray {
                    val count = IntArray(k)
                    for (x in a) count[x]++
                    for (i in 1 until k) count[i] += count[i - 1]
                    val out = IntArray(a.size)
                    for (i in a.indices.reversed()) {
                        val x = a[i]
                        out[--count[x]] = x   // right-to-left keeps it stable
                    }
                    return out
                }
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.SortingVisualizer,
    applications = listOf(
        ApplicationCard("chart", 0xFF10B981, "Sorting Small-Range Keys", "Ages, exam scores, or byte values — anything with a bounded integer range sorts in linear time."),
        ApplicationCard("chip", 0xFF3B82F6, "Radix Sort Subroutine", "Radix sort runs counting sort on each digit, relying on its stability."),
        ApplicationCard("trend", 0xFFF59E0B, "Histograms & Tallies", "The count step alone answers frequency questions used in analytics and image processing."),
    ),
    takeaways = listOf(
        "Counting sort runs in O(n + k) by counting keys instead of comparing them.",
        "It only pays off when the key range k is comparable to n — huge ranges waste memory.",
        "Processing input right-to-left keeps it stable, which is why radix sort depends on it.",
        "It works on integers (or things mappable to a small integer key), not arbitrary comparables.",
    ),
    crossLinks = listOf(
        CrossLink("radix_sort", "Radix Sort"),
        CrossLink("merge_sort", "Merge Sort"),
    ),
)
