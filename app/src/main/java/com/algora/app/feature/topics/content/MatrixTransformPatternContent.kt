package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

// Interview-prep pattern guide. Index arithmetic, not algorithms — the whole difficulty is stating
// the coordinate mapping and respecting the boundaries.
internal val matrixTransformPatternContent = TopicContent(
    topicId = "matrix_transform_pattern",
    whatIsIt = listOf(
        "Matrix manipulation questions are index arithmetic in disguise: rotate in place, spiral out, set rows and columns to zero, search a sorted grid. There is no clever algorithm to recall — only a coordinate mapping to get right.",
        "Two habits solve most of them. Express the transform as a composition of simple ones (rotate = transpose, then reverse each row), and shrink the working region with explicit boundaries rather than tracking a visited grid.",
    ),
    steps = listOf(
        StepCard(1, "Spot the Signal", "Rotate, transpose, spiral order, zero-out propagation, or a staircase search on a row- and column-sorted matrix.", 0xFFF59E0B),
        StepCard(2, "Write the Mapping", "State where (r, c) lands. A 90° clockwise rotation sends (r, c) to (c, n-1-r) — write it down before touching the loops.", 0xFF3B82F6),
        StepCard(3, "Compose Simple Moves", "Transpose then reverse rows gives the clockwise rotation in place, with no temporary matrix and no four-way cycle swap.", 0xFFEF4444),
        StepCard(4, "Shrink by Boundaries", "For spiral traversal, keep top/bottom/left/right and move them inward after each edge — re-checking top ≤ bottom before the reverse passes.", 0xFF10B981),
    ),
    formulas = listOf(
        FormulaEntry("Rotate 90° CW", "(r, c) → (c, n-1-r)", "Transpose, then reverse each row."),
        FormulaEntry("Rotate 90° CCW", "(r, c) → (n-1-c, r)", "Transpose, then reverse each column."),
        FormulaEntry("Time / Space", "O(rows · cols) / O(1)", "In-place transforms need no second matrix."),
    ),
    notationKey = listOf(
        NotationEntry("(r, c)", "row and column of a cell"),
        NotationEntry("n", "side length of a square matrix"),
        NotationEntry("top/bottom/left/right", "shrinking boundaries of the unvisited region"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "In-place rotation and spiral order (Python)",
            accentColor = 0xFFF59E0B,
            code = """
                def rotate_clockwise(m):                   # square, in place
                    n = len(m)
                    for r in range(n):
                        for c in range(r + 1, n):
                            m[r][c], m[c][r] = m[c][r], m[r][c]    # transpose
                    for row in m:
                        row.reverse()                              # then mirror

                def spiral(m):
                    if not m:
                        return []
                    top, bottom, left, right = 0, len(m) - 1, 0, len(m[0]) - 1
                    out = []
                    while top <= bottom and left <= right:
                        for c in range(left, right + 1):
                            out.append(m[top][c])
                        top += 1
                        for r in range(top, bottom + 1):
                            out.append(m[r][right])
                        right -= 1
                        if top <= bottom:                          # guard the reverse passes
                            for c in range(right, left - 1, -1):
                                out.append(m[bottom][c])
                            bottom -= 1
                        if left <= right:
                            for r in range(bottom, top - 1, -1):
                                out.append(m[r][left])
                            left += 1
                    return out
            """.trimIndent(),
        ),
    ),
    // PathfindingGrid has no per-cell labels, so a rotation would be invisible on it. DpGridVisualizer
    // shows the values moving under the same coordinates.
    simulation = SimulationType.DpGridVisualizer,
    applications = listOf(
        ApplicationCard("Image", 0xFF3B82F6, "Image Transforms", "Rotation, mirroring and transposition on a pixel buffer without a copy."),
        ApplicationCard("game", 0xFF10B981, "Board Games", "Rotating tetromino pieces or normalising board orientations."),
        ApplicationCard("TableChart", 0xFF8B5CF6, "Grid Search", "Staircase search from the top-right of a sorted matrix: O(rows + cols)."),
    ),
    takeaways = listOf(
        "Write the coordinate mapping down first — every bug here is an index bug.",
        "Compose simple transforms; transpose-then-reverse beats a hand-rolled four-cell cycle.",
        "Boundaries beat a visited grid for spirals, but re-check them before the reverse passes on non-square input.",
        "In-place is usually the follow-up question — plan for O(1) extra space from the start.",
    ),
    crossLinks = listOf(
        CrossLink("array", "Array (Data Structures)"),
        CrossLink("matrix_islands_pattern", "Matrix Traversal (Islands)"),
        CrossLink("modified_binary_search_pattern", "Modified Binary Search"),
    ),
)
