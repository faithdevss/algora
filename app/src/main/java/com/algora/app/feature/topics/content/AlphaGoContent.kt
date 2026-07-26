package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val alphaGoContent = TopicContent(
    topicId = "alphago",
    whatIsIt = listOf(
        "AlphaGo was the first program to beat a world champion at Go, combining deep neural networks with Monte Carlo Tree Search.",
        "It bootstrapped from human expert games via supervised learning, then improved through self-play reinforcement learning — a policy network to suggest moves and a value network to judge positions.",
    ),
    steps = listOf(
        StepCard(1, "Supervised Policy", "Train a policy network to imitate expert human moves.", 0xFF818CF8),
        StepCard(2, "Self-Play RL", "Improve the policy by playing itself, learning from wins and losses.", 0xFF60A5FA),
        StepCard(3, "Value Network", "Train a network to predict the winner from a board position.", 0xFF10B981),
        StepCard(4, "Guided MCTS", "At play time, MCTS uses the policy to focus search and the value net to evaluate leaves.", 0xFFF59E0B),
    ),
    formulas = listOf(
        FormulaEntry("Search guidance", "policy prior + value eval", "Networks steer and truncate MCTS."),
        FormulaEntry("Leaf value", "mix(value net, rollout)", "Blended position evaluation."),
        FormulaEntry("Training", "supervised → self-play RL", "Human bootstrap, then self-improvement."),
    ),
    notationKey = listOf(
        NotationEntry("policy network", "suggests promising moves"),
        NotationEntry("value network", "estimates win probability"),
        NotationEntry("MCTS", "the tree search the networks guide"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "How the networks guide search",
            accentColor = 0xFF6366F1,
            code = """
                # Policy network narrows the branching factor:
                priors = policy_net(board)          # P(move | board)
                # Value network replaces expensive full rollouts at leaves:
                leaf_value = value_net(leaf_board)  # P(win | board)
                # MCTS combines both to choose the strongest move.
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.GameSearchPlayer,
    applications = listOf(
        ApplicationCard("game", 0xFF818CF8, "Superhuman Go", "Defeated Lee Sedol in 2016, a landmark AI milestone."),
        ApplicationCard("bulb", 0xFF60A5FA, "Neural-Guided Search", "Showed how deep nets can tame enormous search spaces."),
        ApplicationCard("robot", 0xFF10B981, "Blueprint for AlphaZero", "Its architecture led directly to the fully self-taught AlphaZero."),
    ),
    takeaways = listOf(
        "AlphaGo fused deep policy/value networks with MCTS to master Go.",
        "It learned first from human games, then improved via self-play.",
        "The networks made a previously intractable search space searchable.",
        "AlphaZero removed the human-data bootstrap entirely.",
    ),
    crossLinks = listOf(
        CrossLink("mcts", "MCTS"),
        CrossLink("alphazero", "AlphaZero"),
    ),
)
