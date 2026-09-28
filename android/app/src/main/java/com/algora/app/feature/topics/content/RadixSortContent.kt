package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.Figure
import com.algora.app.core.data.model.FigureCell
import com.algora.app.core.data.model.FigureShape
import com.algora.app.core.data.model.FigureTone
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val radixSortContent = TopicContent(
    topicId = "radix_sort",
    figure = Figure(
        caption = "One stable pass per digit, least significant first. Each pass only orders by that " +
            "digit, and stability is what preserves the previous passes' work — swap in an unstable " +
            "inner sort and the whole thing collapses. d passes over n items, so O(d · n) with no " +
            "comparison between whole keys ever made.",
        shape = FigureShape.Grid(
            rows = listOf(
                listOf("170", "45", "75", "90", "802"),
                listOf("170", "90", "802", "45", "75"),
                listOf("802", "45", "170", "75", "90"),
                listOf("45", "75", "90", "170", "802"),
            ),
            rowHeaders = listOf("input", "by 1s", "by 10s", "by 100s"),
            marks = listOf(
                FigureCell(1, 0, FigureTone.Primary),
                FigureCell(1, 1, FigureTone.Primary),
                FigureCell(2, 0, FigureTone.Accent),
                FigureCell(3, 4, FigureTone.Accent),
            ),
        ),
    ),
    whatIsIt = listOf(
        "Radix sort orders numbers by processing them one digit at a time, using a stable sort (usually counting sort) on each digit from least significant to most significant.",
        "Because each digit-pass is stable, sorting the last digit and working up to the first leaves the whole array correctly ordered — no comparisons between full values.",
    ),
    steps = listOf(
        StepCard(1, "Start at the Least Significant Digit", "Sort all numbers by their ones digit first.", 0xFF10B981),
        StepCard(2, "Stable-Sort Each Digit", "Use counting sort so equal digits keep the order fixed by earlier passes.", 0xFF3B82F6),
        StepCard(3, "Move to the Next Digit", "Repeat for tens, hundreds, and so on toward the most significant digit.", 0xFFF59E0B),
        StepCard(4, "Fully Sorted", "After the last (highest) digit pass, the array is sorted end to end.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Time", "O(d · (n + b))", "d digit-passes, each a counting sort over base b."),
        FormulaEntry("Space", "O(n + b)", "Counting-sort buffers reused per pass."),
        FormulaEntry("Effective when", "d is small", "Linear if the number of digits d is roughly constant."),
    ),
    notationKey = listOf(
        NotationEntry("n", "number of elements"),
        NotationEntry("d", "number of digits in the largest value"),
        NotationEntry("b", "base / radix (10 for decimal, 256 for bytes)"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "LSD radix sort (base 10)",
            accentColor = 0xFF6366F1,
            code = """
                fun radixSort(a: IntArray) {
                    val max = a.maxOrNull() ?: return
                    var exp = 1
                    var cur = a.copyOf()
                    while (max / exp > 0) {
                        val count = IntArray(10)
                        for (x in cur) count[(x / exp) % 10]++
                        for (i in 1 until 10) count[i] += count[i - 1]
                        val out = IntArray(cur.size)
                        for (i in cur.indices.reversed()) {
                            val d = (cur[i] / exp) % 10
                            out[--count[d]] = cur[i]
                        }
                        cur = out
                        exp *= 10
                    }
                    cur.copyInto(a)
                }
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.SortingVisualizer,
    applications = listOf(
        ApplicationCard("chip", 0xFF10B981, "Fixed-Width Keys", "Integers, fixed-length IDs, and dates sort in linear time when their digit count is bounded."),
        ApplicationCard("link", 0xFF3B82F6, "String Sorting", "MSD radix sort orders strings and is used in suffix-array construction."),
        ApplicationCard("trend", 0xFFF59E0B, "Large Numeric Datasets", "It outpaces comparison sorts on huge arrays of same-width numbers with a small radix."),
    ),
    takeaways = listOf(
        "Radix sort avoids comparisons, sorting digit by digit in O(d · (n + b)).",
        "It leans entirely on a stable per-digit sort — counting sort is the usual choice.",
        "It is linear only when the digit count d stays small relative to n.",
        "It applies to fixed-width keys (integers, strings), not arbitrary comparable objects.",
    ),
    crossLinks = listOf(
        CrossLink("counting_sort", "Counting Sort"),
        CrossLink("quick_sort", "Quick Sort"),
    ),
)
