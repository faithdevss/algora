package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val nQueensContent = TopicContent(
    topicId = "n_queens",
    whatIsIt = listOf(
        "The N-Queens problem asks you to place N queens on an N×N chessboard so that no two attack each other — no shared row, column, or diagonal.",
        "It's the poster child for backtracking: place a queen, recurse to the next row, and if you hit a dead end, undo the last placement and try the next square.",
    ),
    steps = listOf(
        StepCard(1, "Place Row by Row", "Put exactly one queen in each row, deciding its column.", 0xFF10B981),
        StepCard(2, "Check Safety", "Before placing, verify no earlier queen shares the column or either diagonal.", 0xFF3B82F6),
        StepCard(3, "Recurse Forward", "If safe, place the queen and recurse into the next row.", 0xFFF59E0B),
        StepCard(4, "Backtrack", "If no column is safe, remove the previous queen and try its next option — pruning whole branches.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Search space (naive)", "O(Nᴺ)", "N column choices for each of N rows before pruning."),
        FormulaEntry("With pruning", "≪ Nᴺ", "Safety checks cut off invalid branches early."),
        FormulaEntry("Diagonal check", "constant per placement", "Track occupied columns and both diagonal sets."),
    ),
    notationKey = listOf(
        NotationEntry("N", "board size and number of queens"),
        NotationEntry("backtrack", "undo a choice and try the next alternative"),
        NotationEntry("prune", "abandon a branch that can't lead to a solution"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "N-Queens via backtracking",
            accentColor = 0xFF6366F1,
            code = """
                fun solveNQueens(n: Int): Int {
                    val cols = HashSet<Int>()
                    val diag = HashSet<Int>()      // row - col
                    val anti = HashSet<Int>()      // row + col
                    var count = 0

                    fun place(row: Int) {
                        if (row == n) { count++; return }
                        for (col in 0 until n) {
                            if (col in cols || (row - col) in diag || (row + col) in anti) continue
                            cols += col; diag += row - col; anti += row + col
                            place(row + 1)
                            cols -= col; diag -= row - col; anti -= row + col   // undo
                        }
                    }
                    place(0)
                    return count
                }
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.RecursionTreeVisualizer,
    applications = listOf(
        ApplicationCard("target", 0xFF10B981, "Constraint Satisfaction", "It models any 'assign values without violating constraints' problem — the CSP template."),
        ApplicationCard("chip", 0xFF3B82F6, "Scheduling & Layout", "Backtracking with pruning drives timetabling, register allocation, and VLSI placement."),
        ApplicationCard("book", 0xFFF59E0B, "Teaching Backtracking", "Its clear place/check/undo loop is the standard introduction to backtracking search."),
    ),
    takeaways = listOf(
        "N-Queens is solved by backtracking: place, recurse, and undo on failure.",
        "Pruning invalid placements early is what makes the exponential search tractable.",
        "Tracking occupied columns and diagonals turns each safety check into O(1).",
        "The same place/check/undo skeleton solves Sudoku, subset-sum, and other CSPs.",
    ),
    crossLinks = listOf(
        CrossLink("sudoku_solver", "Sudoku Solver"),
        CrossLink("subset_sum", "Subset Sum"),
        // DSA ↔ AI bridge: backtracking over a search tree is exactly game-tree minimax's shape.
        CrossLink("minimax", "Minimax (game-tree search)"),
    ),
)
