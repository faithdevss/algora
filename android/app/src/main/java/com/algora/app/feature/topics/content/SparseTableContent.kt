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

internal val sparseTableContent = TopicContent(
    topicId = "sparse_table",
    figure = Figure(
        caption = "Precompute the answer for every power-of-two window, then any range is covered by two " +
            "of them — overlapping in the middle, which is fine because min and max do not care about " +
            "double counting. O(n log n) to build, O(1) per query, and nothing can ever be updated: the " +
            "overlap trick is exactly what a sum could not use.",
        shape = FigureShape.Grid(
            rows = listOf(
                listOf("3", "1", "4", "1", "5", "9"),
                listOf("1", "1", "1", "1", "5", "—"),
                listOf("1", "1", "1", "1", "—", "—"),
            ),
            rowHeaders = listOf("len 1", "len 2", "len 4"),
            colHeaders = listOf("i0", "i1", "i2", "i3", "i4", "i5"),
            marks = listOf(
                FigureCell(2, 1, FigureTone.Accent),
                FigureCell(2, 2, FigureTone.Accent),
            ),
        ),
    ),
    whatIsIt = listOf(
        "A sparse table precomputes the answer for every interval whose length is a power of two, so a range query is resolved by combining just two of them.",
        "It only works for idempotent operations — min, max, gcd, bitwise and/or — because the two intervals overlap and the overlap must not be double-counted. In exchange it beats a segment tree on queries: O(1) instead of O(log n), at the cost of being read-only.",
    ),
    steps = listOf(
        StepCard(1, "Level 0 Is the Array", "table[0][i] = a[i] — every interval of length 1.", 0xFF8B5CF6),
        StepCard(2, "Double Each Level", "table[k][i] combines two length-2^(k−1) blocks starting at i and i + 2^(k−1).", 0xFF3B82F6),
        StepCard(3, "Pick the Largest Fitting Power", "For query [l, r], let k = ⌊log₂(r − l + 1)⌋.", 0xFFF59E0B),
        StepCard(4, "Cover the Range With Two Blocks", "Combine table[k][l] and table[k][r − 2^k + 1]; they overlap, which idempotence makes harmless.", 0xFF10B981),
    ),
    formulas = listOf(
        FormulaEntry("Build", "table[k][i] = op(table[k−1][i], table[k−1][i + 2^(k−1)])", "Each level is built from the one below."),
        FormulaEntry("Query", "op(table[k][l], table[k][r − 2^k + 1]),  k = ⌊log₂(r−l+1)⌋", "Two lookups, constant time."),
        FormulaEntry("Build cost", "O(n log n) time and space", "log n levels of n entries."),
        FormulaEntry("Requirement", "op must be idempotent: op(x, x) = x", "Sums do not qualify — use a prefix sum or segment tree instead."),
    ),
    notationKey = listOf(
        NotationEntry("table[k][i]", "answer for a[i .. i + 2^k − 1]"),
        NotationEntry("k", "level index — the log of the block length"),
        NotationEntry("idempotent", "combining a value with itself changes nothing"),
        NotationEntry("[l, r]", "inclusive query range"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Range-minimum sparse table",
            accentColor = 0xFF6366F1,
            code = """
                class SparseTableMin(a: IntArray) {
                    private val log = IntArray(a.size + 1)
                    private val table: Array<IntArray>

                    init {
                        // log[i] = floor(log2(i)), filled by recurrence.
                        for (i in 2..a.size) log[i] = log[i / 2] + 1
                        val levels = log[a.size] + 1
                        table = Array(levels) { IntArray(a.size) }
                        a.copyInto(table[0])
                        for (k in 1 until levels) {
                            val len = 1 shl k
                            for (i in 0..a.size - len) {
                                table[k][i] = minOf(table[k - 1][i], table[k - 1][i + (len shr 1)])
                            }
                        }
                    }

                    // Inclusive [l, r] in O(1). The two blocks overlap — fine for min.
                    fun query(l: Int, r: Int): Int {
                        val k = log[r - l + 1]
                        return minOf(table[k][l], table[k][r - (1 shl k) + 1])
                    }
                }
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.TreeVisualizer,
    applications = listOf(
        ApplicationCard("chart", 0xFF8B5CF6, "Immutable Analytics", "Fixed logs answer \"minimum latency in this window\" without rebuilding anything."),
        ApplicationCard("share", 0xFF3B82F6, "LCA in O(1)", "The Euler tour of a tree reduces lowest-common-ancestor queries to range minimum."),
        ApplicationCard("target", 0xFF10B981, "Competitive Programming", "The default choice whenever data is static and queries vastly outnumber it."),
    ),
    takeaways = listOf(
        "Two overlapping power-of-two blocks cover any range — that is why queries are O(1).",
        "Idempotence is mandatory; overlapping blocks would double-count a sum.",
        "The structure is read-only: any update means a full O(n log n) rebuild.",
        "Choose a segment tree when updates are needed, a sparse table when they are not.",
    ),
    crossLinks = listOf(
        CrossLink("segment_tree", "Segment Tree"),
        CrossLink("fenwick_tree", "Fenwick Tree (BIT)"),
        CrossLink("lca", "Lowest Common Ancestor"),
    ),
)
