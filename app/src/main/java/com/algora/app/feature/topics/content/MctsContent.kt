package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val mctsContent = TopicContent(
    topicId = "mcts",
    whatIsIt = listOf(
        "Monte Carlo Tree Search (MCTS) plans by incrementally building a search tree, using random simulations to estimate how good each move is and focusing effort on the most promising branches.",
        "It balances exploring untried moves against exploiting known-good ones via the UCB rule, and needs no hand-crafted evaluation function — random rollouts suffice.",
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
    ),
    crossLinks = listOf(
        CrossLink("alphazero", "AlphaZero"),
        CrossLink("ucb", "UCB"),
        // DSA ↔ AI bridge: the search tree is explored with graph-traversal machinery.
        CrossLink("graph", "Graph (Traversal)"),
    ),
)
