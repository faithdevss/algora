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

internal val jumpSearchContent = TopicContent(
    topicId = "jump_search",
    figure = Figure(
        caption = "Step forward in blocks until the value overshoots, then walk back through one block. " +
            "√n is the block size that balances the two halves of that cost — O(√n) is worse than binary " +
            "search's O(log n), and it exists for storage where jumping *backwards* is the expensive " +
            "move, so a sequential sweep with few rewinds wins.",
        shape = FigureShape.Strip(
            cells = listOf("3", "8", "15", "27", "34", "42", "50", "61", "73"),
            bands = listOf(
                FigureBand(3, 5, "scan back", FigureTone.Accent),
                FigureBand(6, 8, "overshot", FigureTone.Muted),
            ),
            pointers = listOf(FigurePointer(2, "jump"), FigurePointer(5, "jump"), FigurePointer(8, "jump")),
        ),
    ),
    whatIsIt = listOf(
        "Jump search works on a sorted array by leaping ahead in fixed-size blocks until it overshoots the target, then scanning linearly back within the last block.",
        "It sits between linear and binary search: fewer comparisons than linear, but it only ever jumps forward, which suits data that is costly to seek backward through.",
    ),
    steps = listOf(
        StepCard(1, "Pick a Block Size", "Use a step of √n — the size that balances jumps against the final linear scan.", 0xFF10B981),
        StepCard(2, "Jump Forward", "Skip ahead one block at a time while the block's last element is below the target.", 0xFF3B82F6),
        StepCard(3, "Overshoot", "Stop at the first block whose end is ≥ the target — the value must lie inside it.", 0xFFF59E0B),
        StepCard(4, "Linear Scan the Block", "Walk that block element by element to find the target or confirm it's absent.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Time", "O(√n)", "About √n jumps plus a √n scan of the final block."),
        FormulaEntry("Optimal step", "√n", "Minimizes total work; jumps and scan cost balance here."),
        FormulaEntry("Space", "O(1)", "Only index variables are used."),
    ),
    notationKey = listOf(
        NotationEntry("n", "number of elements"),
        NotationEntry("m", "block size, chosen as √n"),
        NotationEntry("prev, step", "start of the current block and jump distance"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Jump search",
            accentColor = 0xFF6366F1,
            code = """
                import kotlin.math.sqrt
                import kotlin.math.min

                fun jumpSearch(a: IntArray, target: Int): Int {
                    val n = a.size
                    val step = sqrt(n.toDouble()).toInt().coerceAtLeast(1)
                    var prev = 0
                    var curr = 0
                    while (curr < n && a[min(curr + step, n) - 1] < target) {
                        prev = curr
                        curr += step
                    }
                    for (i in prev until min(curr + step, n)) {
                        if (a[i] == target) return i
                    }
                    return -1
                }
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.SearchVisualizer,
    applications = listOf(
        ApplicationCard("history", 0xFF10B981, "Costly Backward Seeks", "On media where jumping back is expensive (tape, some disk layouts), forward-only jumps beat binary search's back-and-forth."),
        ApplicationCard("search", 0xFF3B82F6, "Sorted Block Storage", "Indexes stored in fixed-size blocks map naturally onto jump search's block stride."),
        ApplicationCard("chip", 0xFFF59E0B, "Middle-Ground Lookup", "When binary search is awkward but linear is too slow, √n jumping is a simple compromise."),
    ),
    takeaways = listOf(
        "Jump search needs sorted input and runs in O(√n) — between linear O(n) and binary O(log n).",
        "The optimal block size is √n, balancing the number of jumps against the final scan.",
        "It only moves forward, an edge when backward access is expensive.",
        "For random-access memory, binary search's O(log n) is almost always the better choice.",
    ),
    crossLinks = listOf(
        CrossLink("binary_search", "Binary Search"),
        CrossLink("linear_search", "Linear Search"),
    ),
)
