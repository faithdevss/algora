package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.Figure
import com.algora.app.core.data.model.FigurePoint
import com.algora.app.core.data.model.FigureSeries
import com.algora.app.core.data.model.FigureShape
import com.algora.app.core.data.model.FigureTone
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val mctsContent = TopicContent(
    topicId = "mcts",
    figure = Figure(
        caption = "The page's lab: Monte Carlo tree search on a tic-tac-toe position where X has five " +
            "legal moves and only one holds the draw, and the share of all visits that have gone " +
            "to that move as the number of simulations grows. After 10 simulations visits are " +
            "spread nearly evenly — 22% on the best move, little better than the 20% of picking at " +
            "random — and after 30 it is still 21%. Then UCB's selection starts doing its job: " +
            "moves whose random playouts tend to end well get chosen again, their statistics " +
            "sharpen, and the search concentrates — 41% after 67 simulations, 57% after 200, 78% " +
            "after 1,000. A single random playout is a crude evaluator; averaged over hundreds, " +
            "the statistics rank the moves correctly, and the most-visited move is the one played.",
        shape = FigureShape.Plot(
            series = listOf(
                FigureSeries(
                    "random choice, 20%",
                    listOf(FigurePoint(0f, 0.2f), FigurePoint(1f, 0.2f)),
                    tone = FigureTone.Muted,
                    dashed = true,
                ),
                FigureSeries(
                    "share of visits on the best move",
                    listOf(FigurePoint(0.000f, 0.220f), FigurePoint(0.239f, 0.210f), FigurePoint(0.413f, 0.410f), FigurePoint(0.651f, 0.570f), FigurePoint(0.761f, 0.660f), FigurePoint(1.000f, 0.780f)),
                    tone = FigureTone.Accent,
                ),
            ),
            markers = listOf(
                FigurePoint(0f, 0.22f, "10 sims: 22%", FigureTone.Muted),
                FigurePoint(1f, 0.78f, "1,000: 78%"),
            ),
            xLabel = "simulations, 10 → 1,000 (log)",
            yLabel = "visits on the best move",
        ),
    ),
    whatIsIt = listOf(
        "Monte Carlo tree search plans by simulation. From the current position it repeats four steps: select a path down the tree by an upper-confidence rule, expand one new node, play a quick random game from there to the end, and back the result up the path. Visit counts accumulate on the moves whose simulations go well, and the most-visited move is played. It needs no hand-written evaluation function — only the rules and the ability to play games out.",
        "The lab runs it on a tic-tac-toe position where X has five legal moves and only one holds the draw against perfect play. After 10 simulations the visits are spread almost evenly and the best move has only 22%. As simulations accumulate, UCB keeps sending the search back to moves whose results look good: 41% of visits on the best move after 67 simulations, 57% after 200, 66% after 333 and 78% after 1,000. Random playouts are crude evaluators individually, but averaged over many games they rank the moves correctly.",
        "The selection rule balances exploiting moves that have won with exploring moves tried rarely — the same UCB formula used for bandits, applied at every node (UCT). MCTS is anytime: stop it whenever and it returns its best guess so far. Its weakness is the random playout, which on a game the size of Go is nearly noise; AlphaGo and AlphaZero replaced the playouts with learned value and policy networks and kept the search.",
    ),
    steps = listOf(
        StepCard(1, "Selection", "From the root, follow the UCB-best children down to a not-fully-expanded node.", 0xFF818CF8),
        StepCard(2, "Expansion", "Add one new child node for an untried move.", 0xFF60A5FA),
        StepCard(3, "Simulation", "Play out a random (or policy-guided) rollout to a terminal result.", 0xFF10B981),
        StepCard(4, "Backpropagation", "Propagate the outcome back up, updating each visited node's visit count and value.", 0xFFF59E0B),
    ),
    formulas = listOf(
        FormulaEntry("UCB for trees", "Q(v) + c·√(ln N / n(v))", "Exploitation plus exploration bonus."),
        FormulaEntry("Value estimate", "wins / visits", "Averaged rollout outcomes per node."),
        FormulaEntry("Anytime", "more time = better move", "Quality improves with simulation budget."),
    ),
    notationKey = listOf(
        NotationEntry("rollout", "a simulated playout to a terminal state"),
        NotationEntry("N, n(v)", "parent and child visit counts"),
        NotationEntry("c", "exploration constant in UCT"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "MCTS iteration (structure)",
            accentColor = 0xFF6366F1,
            code = """
                def mcts(root, iterations):
                    for _ in range(iterations):
                        leaf   = select(root)        # UCB down the tree
                        child  = expand(leaf)        # add a new node
                        result = simulate(child)     # random rollout
                        backpropagate(child, result) # update visits & values
                    return best_child(root)          # most-visited move
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.GameSearchPlayer,
    applications = listOf(
        ApplicationCard("game", 0xFF818CF8, "Board Games", "The planning core of Go, chess, and general game-playing engines."),
        ApplicationCard("robot", 0xFF60A5FA, "AlphaGo/AlphaZero", "MCTS guided by neural networks powered superhuman game play."),
        ApplicationCard("map", 0xFF10B981, "Planning & Scheduling", "Sequential decision problems with large branching factors."),
    ),
    takeaways = listOf(
        "MCTS builds a search tree via select–expand–simulate–backpropagate.",
        "UCT balances exploration and exploitation using visit statistics.",
        "It's anytime and needs no evaluation function — rollouts stand in.",
        "Pairing it with learned policy/value networks produced AlphaGo and AlphaZero.",
        "In the lab the best move gets 22% of visits after 10 simulations and 78% after 1,000 — random playouts, averaged, rank moves correctly.",
    ),
    crossLinks = listOf(
        CrossLink("alphazero", "AlphaZero"),
        CrossLink("ucb", "UCB"),
        // DSA ↔ AI bridge: the search tree is explored with graph-traversal machinery.
        CrossLink("graph", "Graph (Traversal)"),
    ),
)
