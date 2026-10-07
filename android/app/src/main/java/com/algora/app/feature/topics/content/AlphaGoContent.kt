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

internal val alphaGoContent = TopicContent(
    topicId = "alphago",
    figure = Figure(
        caption = "The page's lab: 100 MCTS simulations on one tic-tac-toe position, each run judging " +
            "leaf positions a different way, and the share of visits that reach the best move. " +
            "Random playouts get 54%. The \"value network\" runs replace playouts with the exact " +
            "game value plus noise — a stand-in that isolates how evaluator quality steers search: " +
            "47% with no noise, 45% with noise σ = 0.3, 42% with σ = 0.8. The noisier the " +
            "evaluator, the weaker the focus. On a board this small, random games are already a " +
            "decent estimate, so playouts hold their own here; on a 19×19 Go board they are close " +
            "to noise, and AlphaGo's leap was a learned value network good enough to replace them, " +
            "with a policy network narrowing which moves are searched at all.",
        shape = FigureShape.Plot(
            bars = listOf(
                FigureBar("random playouts", 0.54f, FigureTone.Primary),
                FigureBar("value, σ 0", 0.47f, FigureTone.Accent),
                FigureBar("value, σ 0.3", 0.45f, FigureTone.Muted),
                FigureBar("value, σ 0.8", 0.42f, FigureTone.Warn),
            ),
            yLabel = "share of visits on the best move",
        ),
    ),
    whatIsIt = listOf(
        "AlphaGo kept Monte Carlo tree search and replaced its guesswork. Plain MCTS judges a position by playing random games to the end, which on a 19×19 Go board is nearly noise. AlphaGo added a policy network, trained first on human games and then by self-play, to suggest which moves to search, and a value network to judge a position directly instead of finishing the game at random. In 2016 it beat Lee Sedol 4–1.",
        "The lab compares evaluators on a small tic-tac-toe position with 100 simulations each, measuring the share of visits that land on the best move. Random playouts reach 54%. A \"value network\" — here the exact game value plus noise, a stand-in that isolates evaluator quality — gets 47% with no noise, 45% with noise σ = 0.3 and 42% with σ = 0.8: the noisier the evaluator, the weaker the focus. On a board this small random playouts are already a fair estimate, which is why they hold their own here; the point of the value network is the 19×19 board, where they are not.",
        "Search is only as good as its leaf evaluations, and AlphaGo's strength came from learned evaluation good enough to replace rollouts, with the policy network narrowing the search to plausible moves. AlphaGo Zero and AlphaZero then dropped the human games and the rollouts entirely, merging policy and value into one network trained only by self-play.",
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
