package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val gridWorldContent = TopicContent(
    topicId = "grid_world",
    whatIsIt = listOf(
        "Grid World is the teaching environment of RL: an agent moves on a small grid of cells toward a goal, sometimes dodging pits, using discrete up/down/left/right actions.",
        "Its tiny, fully-known state space makes it perfect for illustrating MDPs, value iteration, and tabular Q-learning without any deep learning.",
    ),
    steps = listOf(
        StepCard(1, "States = Cells", "Each grid cell is a discrete state the agent can occupy.", 0xFF818CF8),
        StepCard(2, "Discrete Actions", "Move in the four cardinal directions (some grids add stochastic slips).", 0xFF60A5FA),
        StepCard(3, "Sparse Rewards", "Reach the goal for a positive reward; pits or steps may carry penalties.", 0xFF10B981),
        StepCard(4, "Solve Exactly", "Small enough for value/policy iteration to compute the optimal policy exactly.", 0xFFF59E0B),
    ),
    formulas = listOf(
        FormulaEntry("State space", "|S| = rows × cols", "Small and fully enumerable."),
        FormulaEntry("Actions", "{up, down, left, right}", "Discrete, often 4."),
        FormulaEntry("Solvable by", "value/policy iteration", "Exact dynamic programming."),
    ),
    notationKey = listOf(
        NotationEntry("cell", "one discrete state"),
        NotationEntry("goal / pit", "terminal reward / penalty states"),
        NotationEntry("slip", "optional stochastic transition"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "A grid-world MDP (sketch)",
            accentColor = 0xFF6366F1,
            code = """
                # States are (row, col) cells; actions move one step.
                actions = ["up", "down", "left", "right"]
                def step(state, action):
                    nxt = move(state, action)               # may slip
                    reward = 1.0 if nxt == GOAL else -0.01
                    done = nxt in (GOAL, *PITS)
                    return nxt, reward, done
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.RlGridWorld,
    applications = listOf(
        ApplicationCard("book", 0xFF818CF8, "Teaching RL", "The standard first environment for MDPs and tabular methods."),
        ApplicationCard("map", 0xFF60A5FA, "Algorithm Debugging", "Small enough to inspect value tables and policies by eye."),
        ApplicationCard("bulb", 0xFF10B981, "Concept Illustration", "Visualizes exploration, discounting, and convergence clearly."),
    ),
    takeaways = listOf(
        "Grid World is the minimal MDP for teaching and debugging RL.",
        "Discrete states and actions make it exactly solvable by dynamic programming.",
        "It's ideal for illustrating value iteration and tabular Q-learning.",
        "Its simplicity is the point — real benchmarks like Atari add the hard parts.",
    ),
    crossLinks = listOf(
        CrossLink("mdp", "MDP"),
        CrossLink("q_learning", "Q-Learning (off-policy)"),
    ),
)
