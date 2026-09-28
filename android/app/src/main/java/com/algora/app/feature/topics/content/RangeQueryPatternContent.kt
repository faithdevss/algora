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

// Interview-prep pattern guide. Where prefix sums stop — the moment updates interleave with the
// queries — a Fenwick or segment tree takes over.
internal val rangeQueryPatternContent = TopicContent(
    topicId = "range_query_pattern",
    figure = Figure(
        caption = "Prefix sums answer in O(1) but rebuild in O(n) after any write. A Fenwick tree stores " +
            "block aggregates instead — tree[i] covers the i & −i elements ending at i — so a write " +
            "touches log n blocks and a query recombines log n of them.",
        shape = FigureShape.Strip(
            cells = listOf("3", "1", "4", "1", "5", "9", "2", "6"),
            bands = listOf(FigureBand(2, 5, "range queried", FigureTone.Accent)),
            pointers = listOf(FigurePointer(2, "l"), FigurePointer(5, "r")),
            aux = listOf("3", "4", "4", "9", "5", "14", "2", "31"),
            auxLabel = "Fenwick tree[i] — aggregate of the block ending at i",
        ),
    ),
    whatIsIt = listOf(
        "Prefix sums answer range queries in O(1) but need a full O(n) rebuild after any update. When updates and queries interleave, a Fenwick tree or segment tree makes both O(log n) by storing partial aggregates over nested blocks.",
        "The choice follows the operation. Fenwick is small and fast for sums with point updates; a segment tree handles any associative aggregate — min, max, gcd — and, with lazy propagation, range updates too.",
    ),
    steps = listOf(
        StepCard(1, "Spot the Signal", "Mixed updates and range aggregates over the same array, often thousands of each — prefix sums would rebuild on every write.", 0xFFF59E0B),
        StepCard(2, "Pick the Structure", "Sum with point updates → Fenwick. Any associative aggregate, or range updates → segment tree. Static data → prefix sums or a sparse table.", 0xFF3B82F6),
        StepCard(3, "Update the Path", "A point update touches only the O(log n) nodes covering that index — walk up (Fenwick: i += i & -i) recombining as you go.", 0xFFEF4444),
        StepCard(4, "Split the Query", "Decompose [l, r] into O(log n) precomputed blocks. For an invertible op like sum, query(r) - query(l-1) is enough.", 0xFF10B981),
    ),
    formulas = listOf(
        FormulaEntry("Fenwick", "update O(log n), prefix query O(log n)", "Build O(n); index arithmetic i & -i isolates the lowest set bit."),
        FormulaEntry("Segment tree", "update O(log n), range query O(log n)", "Build O(n), space O(4n) for the array layout."),
        FormulaEntry("Prefix sums", "update O(n), query O(1)", "Only wins when the array never changes after the build."),
    ),
    notationKey = listOf(
        NotationEntry("i & -i", "lowest set bit — the size of the block index i covers"),
        NotationEntry("tree[i]", "aggregate of one block of the array"),
        NotationEntry("[l, r]", "the queried range, inclusive"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Fenwick tree (Python)",
            accentColor = 0xFFF59E0B,
            code = """
                class Fenwick:
                    def __init__(self, n):
                        self.n = n
                        self.tree = [0] * (n + 1)      # 1-indexed

                    def add(self, i, delta):           # point update
                        i += 1
                        while i <= self.n:
                            self.tree[i] += delta
                            i += i & -i                # jump to the next covering block

                    def prefix(self, i):               # sum of a[0..i]
                        i += 1
                        total = 0
                        while i > 0:
                            total += self.tree[i]
                            i -= i & -i                # strip the lowest set bit
                        return total

                    def range_sum(self, l, r):
                        return self.prefix(r) - (self.prefix(l - 1) if l else 0)
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.ArrayWalkPlayer,
    applications = listOf(
        ApplicationCard("chart", 0xFF3B82F6, "Live Leaderboards", "Rank and score-range counts while scores keep changing."),
        ApplicationCard("trend", 0xFF10B981, "Counting Inversions", "Sweep right to left, querying how many smaller values were already seen."),
        ApplicationCard("target", 0xFF8B5CF6, "Range Min / Max", "Segment tree over any associative aggregate; lazy propagation for range assignment."),
    ),
    takeaways = listOf(
        "Interleaved updates and queries are the trigger — static arrays never need this.",
        "Fenwick is sum-shaped and tiny; segment trees are general and take range updates via lazy propagation.",
        "Both trade O(1) reads for O(log n) reads to make writes O(log n) instead of O(n).",
        "Subtracting prefixes only works for invertible operations — min and max must be queried by block decomposition.",
    ),
    crossLinks = listOf(
        CrossLink("fenwick_tree", "Fenwick Tree (Data Structures)"),
        CrossLink("segment_tree", "Segment Tree (Data Structures)"),
        CrossLink("prefix_sum_pattern", "Prefix Sum Pattern"),
    ),
)
