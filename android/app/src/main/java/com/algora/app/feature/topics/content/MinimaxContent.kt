package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.Figure
import com.algora.app.core.data.model.FigureBar
import com.algora.app.core.data.model.FigureShape
import com.algora.app.core.data.model.FigureTone
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val minimaxContent = TopicContent(
    topicId = "minimax",
    figure = Figure(
        caption = "The page's lab: valuing a tic-tac-toe position with X to move and five legal " +
            "squares by perfect play on both sides. Minimax scores each move by the worst reply " +
            "the opponent can make — square 2 looks fine until O answers on square 6 and wins, so " +
            "it is worth −1 — and finds that only square 6 holds the draw. Doing that by brute " +
            "force means examining 186 positions. Alpha-beta pruning returns the identical value " +
            "and move after 88, 53% fewer, because once one reply is known to refute a move the " +
            "remaining replies to it cannot change the decision and are never searched. With good " +
            "move ordering the saving grows with depth — on chess-sized trees it is the difference " +
            "between feasible and impossible.",
        shape = FigureShape.Plot(
            bars = listOf(
                FigureBar("minimax", 1f, FigureTone.Warn),
                FigureBar("alpha-beta", 0.473f, FigureTone.Accent),
            ),
            yLabel = "positions examined, 0 to 186",
        ),
    ),
    whatIsIt = listOf(
        "Minimax values a game position by assuming both players play perfectly. The player to move picks the move with the highest value; the opponent, replying, picks the lowest; and so on down to the end of the game, where a win, draw or loss is scored +1, 0 or −1. The value at the root is the result of perfect play by both sides, and the move that achieves it is the minimax move.",
        "The lab searches a tic-tac-toe position with X to move and five legal squares. Playing square 2 looks reasonable, but O answers with square 6 and wins, so square 2 is worth −1 whatever else happens — the minimum over O's replies. Scoring every move that way, only square 6 holds the draw (value 0); every other move lets O win. The full search examines 186 positions to establish that.",
        "Alpha-beta pruning reaches the identical answer after examining only 88 positions — 53% fewer — by skipping branches that cannot change the decision: once one reply is known to refute a move, the other replies to it need not be searched. On chess-sized trees that saving is the difference between feasible and impossible, and with good move ordering alpha-beta searches roughly twice as deep in the same time. Real game programs add a depth limit and an evaluation function at the leaves; MCTS replaces exhaustive search with sampling.",
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
        "In the lab only one of five moves holds the draw; minimax examines 186 positions to prove it, alpha-beta 88 for the same answer.",
    ),
    crossLinks = listOf(
        CrossLink("mcts", "MCTS"),
        CrossLink("self_play", "Self-Play"),
        // DSA ↔ AI bridge: the game tree is walked by the same backtracking as N-Queens.
        CrossLink("n_queens", "N-Queens (backtracking)"),
    ),
)
