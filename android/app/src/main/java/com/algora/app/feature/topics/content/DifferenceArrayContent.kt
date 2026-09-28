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

internal val differenceArrayContent = TopicContent(
    topicId = "difference_array",
    figure = Figure(
        caption = "Adding 5 to every element from index 1 to 3 touches two cells, not three: add at the " +
            "left end, subtract just past the right end, and the final prefix-sum pass turns those two " +
            "edits into the whole run. k updates therefore cost O(k) rather than O(k · length), with " +
            "one O(n) reconstruction at the end regardless of how many there were. It is the exact " +
            "mirror of the prefix sum — that one makes range queries O(1), this one makes range " +
            "updates O(1) — and the same duality is why a Fenwick tree built over a difference array " +
            "supports both in O(log n). The catch mirrors too: nothing is answerable until the " +
            "reconstruction runs, so this is for a batch of updates followed by reads, not for " +
            "interleaving them. The 2-D version needs four corner edits per rectangle instead of two.",
        shape = FigureShape.Strip(
            cells = listOf("0", "5", "0", "0", "−5", "0"),
            bands = listOf(
                FigureBand(1, 1, "+5 here", FigureTone.Accent),
                FigureBand(4, 4, "−5 after", FigureTone.Warn),
            ),
            aux = listOf("0", "5", "5", "5", "0", "0"),
            auxLabel = "prefix sum of the row above — the array the two edits actually produced",
        ),
    ),
    whatIsIt = listOf(
        "A difference array applies range updates in O(1) each, deferring the real work until a final prefix-sum pass reconstructs the updated array.",
        "It's the mirror image of prefix sums: prefix sums make range queries cheap, difference arrays make range updates cheap.",
    ),
    steps = listOf(
        StepCard(1, "Store Differences", "diff[i] = a[i] − a[i−1]; the original array is the prefix sum of diff.", 0xFF10B981),
        StepCard(2, "Range Add in O(1)", "To add v to a[l..r], do diff[l] += v and diff[r+1] −= v — just two touches.", 0xFF3B82F6),
        StepCard(3, "Batch the Updates", "Apply any number of range updates, each in constant time, without touching the interior.", 0xFFF59E0B),
        StepCard(4, "Reconstruct Once", "Take a prefix sum of diff to materialize the final array in O(n).", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Range add", "diff[l] += v; diff[r+1] −= v", "One constant-time update per range."),
        FormulaEntry("Rebuild", "a[i] = a[i−1] + diff[i]", "Prefix sum restores the array."),
        FormulaEntry("Cost", "O(1) per update, O(n) rebuild", "vs O(n) per update done directly."),
    ),
    notationKey = listOf(
        NotationEntry("diff[i]", "a[i] − a[i−1], the stored difference"),
        NotationEntry("l, r, v", "add v to every element in range l..r"),
        NotationEntry("n", "array length"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Difference array — batched range adds",
            accentColor = 0xFF6366F1,
            code = """
                fun applyRangeUpdates(n: Int, updates: List<Triple<Int, Int, Int>>): IntArray {
                    val diff = IntArray(n + 1)
                    for ((l, r, v) in updates) {        // each update is O(1)
                        diff[l] += v
                        if (r + 1 < diff.size) diff[r + 1] -= v
                    }
                    val result = IntArray(n)
                    var running = 0
                    for (i in 0 until n) { running += diff[i]; result[i] = running }
                    return result
                }
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.ArrayWalkPlayer,
    applications = listOf(
        ApplicationCard("chart", 0xFF10B981, "Interval Booking", "Adding +1 over each reservation's time range, then summing, gives per-slot occupancy counts."),
        ApplicationCard("map", 0xFF3B82F6, "Range Increment Problems", "'Add a value to every element in many ranges' problems collapse to one difference array."),
        ApplicationCard("trend", 0xFFF59E0B, "Traffic & Load Modeling", "Overlapping load intervals accumulate cheaply before a single reconstruction pass."),
    ),
    takeaways = listOf(
        "A difference array makes each range update O(1), paid off by one O(n) rebuild.",
        "It's the dual of prefix sums: updates cheap instead of queries cheap.",
        "Two touches per range (add at l, subtract past r) encode the whole update.",
        "Use it when updates dominate; use prefix sums when queries dominate; a Fenwick tree when both interleave.",
    ),
    crossLinks = listOf(
        CrossLink("prefix_sum", "Prefix Sum"),
        CrossLink("fenwick_tree", "Fenwick Tree (BIT)"),
    ),
)
