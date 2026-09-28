package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val minimaxContent = TopicContent(
    topicId = "minimax",
    whatIsIt = listOf(
        "Minimax is the classic algorithm for two-player zero-sum games: one player maximizes the score while the opponent minimizes it, and each assumes the other plays optimally.",
        "It searches the game tree to find the move that guarantees the best outcome against a perfect adversary, with alpha-beta pruning cutting away branches that can't affect the result.",
    ),
    steps = listOf(
        StepCard(1, "Build the Game Tree", "Alternate MAX and MIN levels for the two players' turns.", 0xFF818CF8),
        StepCard(2, "Evaluate Leaves", "Score terminal (or depth-limited) positions with a value function.", 0xFF60A5FA),
        StepCard(3, "Back Up Values", "MAX nodes take the max child, MIN nodes the min, propagating up.", 0xFF10B981),
        StepCard(4, "Prune with Alpha-Beta", "Skip branches that can't change the decision, often halving the search depth cost.", 0xFFF59E0B),
    ),
    formulas = listOf(
        FormulaEntry("Value", "MAX: max child · MIN: min child", "Optimal play from both sides."),
        FormulaEntry("Naive cost", "O(bᵈ)", "Branching factor b, depth d."),
        FormulaEntry("With α-β", "≈ O(b^(d/2))", "Effectively doubles reachable depth."),
    ),
    notationKey = listOf(
        NotationEntry("MAX / MIN", "the maximizing and minimizing players"),
        NotationEntry("α, β", "best guaranteed values for MAX and MIN"),
        NotationEntry("ply", "one player's move (a tree level)"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Minimax with alpha-beta pruning",
            accentColor = 0xFF6366F1,
            code = """
                def minimax(node, depth, alpha, beta, maximizing):
                    if depth == 0 or node.is_terminal():
                        return node.value()
                    if maximizing:
                        value = -inf
                        for child in node.children():
                            value = max(value, minimax(child, depth-1, alpha, beta, False))
                            alpha = max(alpha, value)
                            if alpha >= beta: break        # beta cutoff
                        return value
                    else:
                        value = inf
                        for child in node.children():
                            value = min(value, minimax(child, depth-1, alpha, beta, True))
                            beta = min(beta, value)
                            if alpha >= beta: break        # alpha cutoff
                        return value
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.GameSearchPlayer,
    applications = listOf(
        ApplicationCard("game", 0xFF818CF8, "Classic Game AI", "Chess, checkers, and tic-tac-toe engines search with minimax + alpha-beta."),
        ApplicationCard("target", 0xFF60A5FA, "Adversarial Planning", "Any two-party zero-sum decision with an opposing optimizer."),
        ApplicationCard("bulb", 0xFF10B981, "Worst-Case Guarantees", "Guarantees the best outcome against a perfect opponent."),
    ),
    takeaways = listOf(
        "Minimax solves two-player zero-sum games by alternating max and min.",
        "Alpha-beta pruning skips provably irrelevant branches, roughly doubling search depth.",
        "It assumes an optimal opponent, giving worst-case guarantees.",
        "For huge trees like Go, MCTS with learned evaluation replaces exhaustive minimax.",
    ),
    crossLinks = listOf(
        CrossLink("mcts", "MCTS"),
        CrossLink("self_play", "Self-Play"),
        // DSA ↔ AI bridge: the game tree is walked by the same backtracking as N-Queens.
        CrossLink("n_queens", "N-Queens (backtracking)"),
    ),
)
