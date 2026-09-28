package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val epsilonGreedyContent = TopicContent(
    topicId = "epsilon_greedy",
    whatIsIt = listOf(
        "Epsilon-greedy is the simplest exploration strategy: with probability ε pick a random action, otherwise pick the current best — balancing trying new things against exploiting what works.",
        "It's the default exploration rule in Q-learning and DQN precisely because it's trivial to implement and surprisingly effective.",
    ),
    steps = listOf(
        StepCard(1, "Flip a Biased Coin", "With probability ε, explore; with probability 1−ε, exploit.", 0xFF818CF8),
        StepCard(2, "Explore Randomly", "On explore, choose a uniformly random action.", 0xFF60A5FA),
        StepCard(3, "Exploit Greedily", "On exploit, choose the action with the highest estimated value.", 0xFF10B981),
        StepCard(4, "Decay Epsilon", "Anneal ε from high (explore early) toward low (exploit once confident).", 0xFFF59E0B),
    ),
    formulas = listOf(
        FormulaEntry("Action rule", "random w.p. ε, else argmaxₐ Q(s,a)", "The explore/exploit split."),
        FormulaEntry("Decay", "ε ← max(ε_min, ε·decay)", "Reduce exploration over time."),
        FormulaEntry("Regret", "linear if ε fixed", "Constant ε keeps exploring forever."),
    ),
    notationKey = listOf(
        NotationEntry("ε", "probability of a random action"),
        NotationEntry("exploit", "take the current best action"),
        NotationEntry("annealing", "decaying ε across training"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Epsilon-greedy action selection",
            accentColor = 0xFF6366F1,
            code = """
                import random

                def select_action(Q, state, epsilon, actions):
                    if random.random() < epsilon:
                        return random.choice(actions)      # explore
                    return max(actions, key=lambda a: Q[state][a])   # exploit
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.BanditExplorer,
    applications = listOf(
        ApplicationCard("game", 0xFF818CF8, "Q-Learning & DQN", "The standard exploration mechanism for value-based agents."),
        ApplicationCard("target", 0xFF60A5FA, "A/B & Bandits", "A simple baseline for balancing exploration in online experiments."),
        ApplicationCard("bulb", 0xFF10B981, "Baseline Strategy", "The first thing to try before fancier exploration schemes."),
    ),
    takeaways = listOf(
        "Epsilon-greedy explores randomly with probability ε and exploits otherwise.",
        "Decaying ε shifts from exploration early to exploitation later.",
        "It's simple and robust but undirected — it explores blindly, not by uncertainty.",
        "UCB and Thompson sampling explore more intelligently by tracking uncertainty.",
    ),
    crossLinks = listOf(
        CrossLink("ucb", "UCB"),
        CrossLink("q_learning", "Q-Learning (off-policy)"),
    ),
)
