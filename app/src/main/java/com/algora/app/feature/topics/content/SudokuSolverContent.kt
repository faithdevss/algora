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

internal val sudokuSolverContent = TopicContent(
    topicId = "sudoku_solver",
    figure = Figure(
        caption = "Drawn 4×4 so it fits, but the rule is the 9×9 one: a cell is the intersection of three " +
            "constraint sets. The marked cell loses 1 to its row, 4 to its column and 3 to its 2×2 box — " +
            "one digit survives, so the solver writes 2 and recurses without branching at all. That is " +
            "the whole difference between this and blind backtracking: filling the most-constrained " +
            "empty cell first turns most of the search into forced moves, and only genuinely ambiguous " +
            "cells ever cost a branch to undo.",
        shape = FigureShape.Grid(
            rows = listOf(
                listOf("?", "1", "·", "·"),
                listOf("·", "3", "·", "·"),
                listOf("4", "·", "·", "·"),
                listOf("·", "·", "·", "·"),
            ),
            marks = listOf(
                FigureCell(0, 0, FigureTone.Accent),
                FigureCell(0, 1, FigureTone.Warn),
                FigureCell(1, 1, FigureTone.Warn),
                FigureCell(2, 0, FigureTone.Warn),
            ),
        ),
    ),
    whatIsIt = listOf(
        "A Sudoku solver fills a 9×9 grid so every row, column, and 3×3 box contains the digits 1–9 exactly once, using constraint-based backtracking.",
        "It finds an empty cell, tries each digit the constraints allow, recurses, and backtracks whenever a choice leads to a dead end.",
    ),
    steps = listOf(
        StepCard(1, "Find an Empty Cell", "Scan for the next blank square to fill.", 0xFF10B981),
        StepCard(2, "Try Valid Digits", "For 1–9, keep only digits not already in that row, column, or box.", 0xFF3B82F6),
        StepCard(3, "Recurse", "Tentatively write a digit and solve the rest of the grid from there.", 0xFFF59E0B),
        StepCard(4, "Backtrack", "If no digit works downstream, erase the cell and try the next candidate.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Worst case", "exponential", "The constraint graph is NP-complete in the general n²×n² case."),
        FormulaEntry("In practice", "fast", "Constraints prune so aggressively that 9×9 puzzles solve near-instantly."),
        FormulaEntry("Validity check", "O(1) per cell", "Track used digits per row, column, and box as bitmasks."),
    ),
    notationKey = listOf(
        NotationEntry("cell", "one of the 81 squares to fill"),
        NotationEntry("constraint", "row/column/box uniqueness rule"),
        NotationEntry("candidate", "a digit currently legal for a cell"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Sudoku backtracking core",
            accentColor = 0xFF6366F1,
            code = """
                fun solve(board: Array<CharArray>): Boolean {
                    for (r in 0 until 9) for (c in 0 until 9) {
                        if (board[r][c] != '.') continue
                        for (d in '1'..'9') {
                            if (!isValid(board, r, c, d)) continue
                            board[r][c] = d                 // choose
                            if (solve(board)) return true   // recurse
                            board[r][c] = '.'               // undo
                        }
                        return false                        // no digit worked here
                    }
                    return true                             // all cells filled
                }
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.RecursionTreeVisualizer,
    applications = listOf(
        ApplicationCard("game", 0xFF10B981, "Puzzle Solving & Generation", "The same solver both checks uniqueness and generates new puzzles by carving from a full grid."),
        ApplicationCard("target", 0xFF3B82F6, "Constraint Propagation", "Pairing backtracking with propagation (only-choice, naked pairs) is a mini constraint solver."),
        ApplicationCard("chip", 0xFFF59E0B, "Exact-Cover Modeling", "Sudoku reduces to exact cover, solvable by Knuth's Algorithm X / Dancing Links."),
    ),
    takeaways = listOf(
        "Sudoku is backtracking with per-cell constraints: try, recurse, undo.",
        "Aggressive constraint pruning makes an exponential search practically instant on 9×9.",
        "Bitmask tracking of used digits makes each validity check O(1).",
        "Adding constraint propagation cuts the search tree dramatically before backtracking even starts.",
    ),
    crossLinks = listOf(
        CrossLink("n_queens", "N-Queens"),
        CrossLink("subset_sum", "Subset Sum"),
    ),
)
