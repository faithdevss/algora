package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.Figure
import com.algora.app.core.data.model.FigureNode
import com.algora.app.core.data.model.FigureShape
import com.algora.app.core.data.model.FigureTone
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

// Interview-prep pattern guide. Uses the standard 7-section template; cross-links to the Algorithms
// topic that implements the technique.
internal val backtrackingPatternContent = TopicContent(
    topicId = "backtracking_pattern",
    figure = Figure(
        caption = "Choose, recurse, un-choose. Every edge is a decision, every dead end is pruned the " +
            "moment the running sum passes the target, and the un-choose on the way back out is what " +
            "keeps siblings from inheriting each other's state.",
        shape = FigureShape.Tree(
            nodes = listOf(
                FigureNode("[]", null, FigureTone.Primary),
                FigureNode("2", 0, FigureTone.Primary),
                FigureNode("3", 0),
                FigureNode("2,2", 1, FigureTone.Primary),
                FigureNode("2,3", 1, FigureTone.Accent),
                FigureNode("3,3", 2, FigureTone.Warn),
                FigureNode("2,2,2", 3, FigureTone.Warn),
            ),
        ),
    ),
    whatIsIt = listOf(
        "Backtracking builds a candidate one decision at a time and abandons a branch the moment it cannot lead to a valid answer. The shape is always the same: choose, recurse, un-choose.",
        "What separates a passing answer from a timing-out one is pruning — the constraint check that kills a subtree before it is explored, and the ordering that makes those checks fire early.",
    ),
    steps = listOf(
        StepCard(1, "Spot the Signal", "\"All combinations / permutations / ways\", or a puzzle with constraints and no formula for the answer.", 0xFFF59E0B),
        StepCard(2, "Choose", "Append one candidate to the partial solution and mark whatever it consumes as used.", 0xFF3B82F6),
        StepCard(3, "Prune, Then Recurse", "Test the constraint first. If the partial state is already invalid or over budget, return without recursing.", 0xFFEF4444),
        StepCard(4, "Un-choose", "Undo the append and the marking on the way out, so siblings start from a clean state.", 0xFF10B981),
    ),
    formulas = listOf(
        FormulaEntry("Upper bound", "O(b^d)", "b choices per level, depth d — pruning is what keeps you far below it."),
        FormulaEntry("Space", "O(d)", "Recursion depth plus the partial solution being carried."),
        FormulaEntry("Output-sensitive cost", "O(answers × d)", "Copying each completed solution out is its own term."),
    ),
    notationKey = listOf(
        NotationEntry("path", "the partial solution built so far"),
        NotationEntry("start", "index that prevents reusing earlier choices"),
        NotationEntry("b, d", "branching factor and recursion depth"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Combination sum with pruning (Python)",
            accentColor = 0xFFF59E0B,
            code = """
                def combination_sum(nums, target):
                    nums.sort()                       # lets the prune below fire early
                    out, path = [], []

                    def walk(start, remaining):
                        if remaining == 0:
                            out.append(path[:])       # copy — path keeps mutating
                            return
                        for i in range(start, len(nums)):
                            if nums[i] > remaining:
                                break                 # sorted: everything after is worse
                            path.append(nums[i])      # choose
                            walk(i, remaining - nums[i])
                            path.pop()                # un-choose

                    walk(0, target)
                    return out
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.RecursionTreeVisualizer,
    applications = listOf(
        ApplicationCard("game", 0xFF3B82F6, "Constraint Puzzles", "N-Queens, Sudoku, crossword and word-search fills."),
        ApplicationCard("functions", 0xFF10B981, "Enumeration Problems", "Combination sum, palindrome partitioning, letter combinations of a phone number."),
        ApplicationCard("map", 0xFF8B5CF6, "Path Enumeration", "All routes through a maze or graph under a budget or visit rule."),
    ),
    takeaways = listOf(
        "Choose / recurse / un-choose — the un-choose is what makes it backtracking rather than DFS with copies.",
        "Copy the path when recording a solution; the live list is about to change.",
        "Sorting first often unlocks a `break` that removes an entire subtree.",
        "`start` (not a visited set) is what prevents duplicate combinations.",
    ),
    crossLinks = listOf(
        CrossLink("n_queens", "N-Queens (Algorithms)"),
        CrossLink("subset_sum", "Subset Sum (Algorithms)"),
        CrossLink("subsets_pattern", "Subsets & Combinations"),
    ),
)
