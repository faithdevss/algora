package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val alphaZeroContent = TopicContent(
    topicId = "alphazero",
    whatIsIt = listOf(
        "AlphaZero learns to play Go, chess, and shogi at superhuman level from scratch — no human games, just the rules and self-play.",
        "It uses a single network with a policy head and a value head, trained so its raw predictions match the improved decisions that MCTS produces, in a self-reinforcing loop.",
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
    ),
    crossLinks = listOf(
        CrossLink("alphago", "AlphaGo"),
        CrossLink("muzero", "MuZero"),
    ),
)
