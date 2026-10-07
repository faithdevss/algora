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

internal val alphaZeroContent = TopicContent(
    topicId = "alphazero",
    figure = Figure(
        caption = "The page's lab: AlphaZero's PUCT search on a tic-tac-toe position, guided by a policy " +
            "prior and a value from one network, with no random playouts. Each move is scored " +
            "Q + 1.5·P·√N / (1 + n): the backed-up value plus an exploration term weighted by the " +
            "prior. With a prior that gives the best move 20%, search puts 63% of its visits there " +
            "after 50 simulations and 86% after 400 — the prior decides where to look first, the " +
            "values decide where to stay. With a misleading prior that gives the best move only 4%, " +
            "search starts slower (41% at 50) but value backups still pull 83% of visits to the " +
            "winner by 400. AlphaZero then trains the network's policy to match these visit " +
            "counts: search improves the policy, and the better policy improves the next search.",
        shape = FigureShape.Grid(
            rows = listOf(
                listOf("20%", "63%", "86%"),
                listOf("4%", "41%", "83%"),
            ),
            rowHeaders = listOf("good prior", "misleading prior"),
            colHeaders = listOf("prior on best", "visits, 50 sims", "visits, 400 sims"),
            marks = listOf(
                FigureCell(0, 2, FigureTone.Accent),
                FigureCell(1, 0, FigureTone.Warn),
                FigureCell(1, 2, FigureTone.Accent),
            ),
        ),
    ),
    whatIsIt = listOf(
        "AlphaZero is one network and one search, learned from nothing but self-play. The network takes a position and outputs a policy prior — which moves look promising — and a value — who is likely to win. PUCT search uses both: it scores each move by its backed-up value plus an exploration term weighted by the prior, Q + c·P·√N / (1 + n), with no random playouts at all. The same algorithm mastered chess, shogi and Go.",
        "The lab runs PUCT on a tic-tac-toe position where the prior gives the best move 20%. After 50 simulations the search has put 63% of its visits there, and after 400, 86%: the prior decides where to look first, and the backed-up values decide where to stay. Even a misleading prior that favours the wrong moves only slows the search — 41% after 50 simulations — and value backups still pull 83% of visits to the winner by 400.",
        "That is the engine of AlphaZero's training. The search's visit distribution is a better policy than the network's prior, so the network is trained to match it, and its value head to predict the eventual game result; the improved network then guides better searches. Self-play is policy improvement, repeated. MuZero went one step further and learned the game's rules too.",
    ),
    steps = listOf(
        StepCard(1, "One Network, Two Heads", "A shared network outputs a move policy and a position value.", 0xFF818CF8),
        StepCard(2, "MCTS as Policy Improvement", "Search with the network produces stronger move probabilities than the raw policy.", 0xFF60A5FA),
        StepCard(3, "Self-Play Data", "The agent plays itself, recording MCTS move distributions and game outcomes.", 0xFF10B981),
        StepCard(4, "Train Toward Search", "The network learns to predict MCTS's policy and the game result, then improves and repeats.", 0xFFF59E0B),
    ),
    formulas = listOf(
        FormulaEntry("Loss", "(z − v)² − πᵀ log p + c‖θ‖²", "Value error + policy cross-entropy + reg."),
        FormulaEntry("Targets", "π = MCTS visits, z = game result", "Search-improved policy and outcome."),
        FormulaEntry("Loop", "self-play ↔ train", "Each improves the other."),
    ),
    notationKey = listOf(
        NotationEntry("p, v", "network policy and value heads"),
        NotationEntry("π", "MCTS visit-count policy (the target)"),
        NotationEntry("z", "final game outcome (+1/−1)"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "AlphaZero training target",
            accentColor = 0xFF6366F1,
            code = """
                # Self-play produces (state, pi, z): MCTS policy and game result.
                p, v = net(state)
                loss = (z - v) ** 2 - (pi * torch.log(p)).sum() + c * l2(net)
                # The network learns to imitate its own search and predict outcomes.
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.GameSearchPlayer,
    applications = listOf(
        ApplicationCard("game", 0xFF818CF8, "Multi-Game Mastery", "One algorithm reached superhuman Go, chess, and shogi from scratch."),
        ApplicationCard("bulb", 0xFF60A5FA, "Tabula Rasa Learning", "Proved strong play needs no human data — only self-play."),
        ApplicationCard("flask", 0xFF10B981, "Beyond Games", "Its template inspired applications in matrix multiplication and chip design."),
    ),
    takeaways = listOf(
        "AlphaZero learns from self-play alone, with no human games.",
        "MCTS acts as a policy-improvement operator the network learns to imitate.",
        "A single two-headed network replaces AlphaGo's separate policy/value nets.",
        "MuZero extends it to learn the rules (dynamics) as well.",
        "In the lab PUCT search turns a 20% prior on the best move into 63% of visits after 50 simulations and 86% after 400.",
    ),
    crossLinks = listOf(
        CrossLink("alphago", "AlphaGo"),
        CrossLink("muzero", "MuZero"),
    ),
)
