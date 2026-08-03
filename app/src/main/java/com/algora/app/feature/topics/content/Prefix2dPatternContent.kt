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

// Interview-prep pattern guide. Prefix sums in two dimensions: inclusion-exclusion turns any
// submatrix query into four array lookups.
internal val prefix2dPatternContent = TopicContent(
    topicId = "prefix_2d_pattern",
    figure = Figure(
        caption = "P[r][c] is the sum of the whole rectangle from the origin to that cell, so any " +
            "submatrix is four lookups: the big rectangle, minus the strip above, minus the strip left, " +
            "plus the corner that was subtracted twice. 28 − 8 − 9 + 3 = 14.",
        shape = FigureShape.Grid(
            rows = listOf(
                listOf("0", "0", "0", "0", "0"),
                listOf("0", "3", "3", "4", "8"),
                listOf("0", "8", "14", "18", "24"),
                listOf("0", "9", "17", "21", "28"),
            ),
            rowHeaders = listOf("0", "r0", "r1", "r2"),
            colHeaders = listOf("0", "c0", "c1", "c2", "c3"),
            marks = listOf(
                FigureCell(3, 4, FigureTone.Accent),
                FigureCell(1, 4, FigureTone.Warn),
                FigureCell(3, 1, FigureTone.Warn),
                FigureCell(1, 1, FigureTone.Primary),
            ),
        ),
    ),
    whatIsIt = listOf(
        "A 2D prefix table stores, at each cell, the sum of the whole rectangle from the origin to it. Any submatrix sum is then four lookups: the big rectangle, minus the strip above, minus the strip to the left, plus the corner that was subtracted twice.",
        "That inclusion-exclusion is the pattern. Build costs O(rows · cols) once; every query afterwards is O(1), which is what makes counting submatrices with a given sum feasible at all.",
    ),
    steps = listOf(
        StepCard(1, "Spot the Signal", "Repeated rectangle queries on a static grid — region sums, counting matrices meeting a threshold, or an image-style average filter.", 0xFFF59E0B),
        StepCard(2, "Pad by One", "Allocate (rows + 1) × (cols + 1) with a zero row and column. The padding removes every boundary check from the query.", 0xFF3B82F6),
        StepCard(3, "Build with Inclusion-Exclusion", "P[r][c] = grid[r-1][c-1] + P[r-1][c] + P[r][c-1] − P[r-1][c-1]. The overlap is added back exactly once.", 0xFFEF4444),
        StepCard(4, "Query the Same Way", "sum(r1..r2, c1..c2) = P[r2+1][c2+1] − P[r1][c2+1] − P[r2+1][c1] + P[r1][c1].", 0xFF10B981),
    ),
    formulas = listOf(
        FormulaEntry("Build", "O(rows · cols)", "One sweep; each cell reads three neighbours."),
        FormulaEntry("Query", "O(1)", "Four lookups regardless of the rectangle's size."),
        FormulaEntry("Max submatrix sum", "O(rows² · cols)", "Fix a row pair, compress the columns, then run Kadane on the strip."),
    ),
    notationKey = listOf(
        NotationEntry("P[r][c]", "sum of the rectangle from (0,0) to (r-1, c-1)"),
        NotationEntry("padding", "the extra zero row and column at index 0"),
        NotationEntry("inclusion-exclusion", "subtract the two strips, add the doubly-removed corner back"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "2D prefix table and range query (Python)",
            accentColor = 0xFFF59E0B,
            code = """
                def build_prefix(grid):
                    rows, cols = len(grid), len(grid[0])
                    P = [[0] * (cols + 1) for _ in range(rows + 1)]    # padded with zeros
                    for r in range(1, rows + 1):
                        for c in range(1, cols + 1):
                            P[r][c] = (grid[r - 1][c - 1]
                                       + P[r - 1][c] + P[r][c - 1]
                                       - P[r - 1][c - 1])              # overlap counted twice
                    return P

                def region_sum(P, r1, c1, r2, c2):         # inclusive corners
                    return (P[r2 + 1][c2 + 1]
                            - P[r1][c2 + 1]                # strip above
                            - P[r2 + 1][c1]                # strip to the left
                            + P[r1][c1])                   # corner removed twice
            """.trimIndent(),
        ),
    ),
    // PathfindingGrid paints cells but never labels them, and this pattern is entirely about the
    // numbers in the cells. DpGridVisualizer draws a headed, valued table.
    simulation = SimulationType.DpGridVisualizer,
    applications = listOf(
        ApplicationCard("Image", 0xFF3B82F6, "Integral Images", "Box filters and Haar features evaluate any window in constant time."),
        ApplicationCard("chart", 0xFF10B981, "Heatmap Queries", "Totals over map tiles, sales regions or pixel blocks."),
        ApplicationCard("target", 0xFF8B5CF6, "Submatrix Search", "Counting submatrices summing to a target by pairing rows with a hash map."),
    ),
    takeaways = listOf(
        "Four lookups per query — memorise the sign pattern: big − above − left + corner.",
        "Pad with a zero row and column; every off-by-one bug here comes from unpadded indexing.",
        "Static grids only. If cells change, use a 2D Fenwick tree instead.",
        "Fixing a row pair reduces most 2D problems to the 1D pattern you already know.",
    ),
    crossLinks = listOf(
        CrossLink("prefix_sum_pattern", "Prefix Sum Pattern"),
        CrossLink("range_query_pattern", "Range Query Structures"),
        CrossLink("running_best_pattern", "Running Best (Kadane)"),
    ),
)
