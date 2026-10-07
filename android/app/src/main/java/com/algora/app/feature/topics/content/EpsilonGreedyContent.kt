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

internal val epsilonGreedyContent = TopicContent(
    topicId = "epsilon_greedy",
    figure = Figure(
        caption = "The page's lab: four slot machines paying 30%, 50%, 45% and 72% of the time, 200 " +
            "pulls, and the share of pulls that went to the best one, averaged over 50 runs. At " +
            "ε = 0.01 the agent almost never explores, so whichever arm paid out first keeps the " +
            "lead — in the lab's own run it pulls the worst arm 194 times and the best arm once — " +
            "and only 36% of pulls land on the 72% machine. Exploring 10% of the time raises that to " +
            "65%; 30% raises it to 69% and earns the most, 0.625 per pull, against a best possible " +
            "0.720. Exploration costs reward every time it fires, and ε-greedy spends it blindly, " +
            "on arms already known to be bad as often as on promising ones — which is what UCB, " +
            "Thompson sampling and softmax exploration improve on.",
        shape = FigureShape.Plot(
            bars = listOf(
                FigureBar("ε = 0.01", 0.36f, FigureTone.Warn),
                FigureBar("ε = 0.1", 0.65f, FigureTone.Primary),
                FigureBar("ε = 0.3", 0.69f, FigureTone.Accent),
            ),
            yLabel = "share of pulls on the best arm",
        ),
    ),
    whatIsIt = listOf(
        "ε-greedy is the simplest exploration rule there is: with probability ε take a random action, otherwise take the one whose estimate is currently best. It needs nothing but a running average per action, which is why it is the default in DQN and nearly every first implementation — and its simplicity is also its weakness, because the random draws are blind: a known-bad action gets explored exactly as often as a promising one.",
        "The lab plays four slot machines that pay out 30%, 50%, 45% and 72% of the time, for 200 pulls. With ε = 0.01 the agent almost never explores, so whichever arm got lucky early keeps the lead: in the lab's run it pulls A, the worst arm but the first to pay, 194 times out of 200, and the best arm D only once. Averaged over 50 runs, ε = 0.01 puts 36% of pulls on D, ε = 0.1 puts 65%, and ε = 0.3 puts 69% and earns the most, 0.625 per pull against a best possible of 0.720.",
        "The trade never goes away. Exploration costs reward every time it fires — at ε = 0.3, almost a third of pulls are spent on arms already known to be worse — but without it an early lucky estimate can hold the lead forever, because the only way to correct an estimate is to sample the action again. That is why ε is usually decayed over training, and why smarter rules such as UCB, Thompson sampling and Boltzmann exploration spend the exploration budget on actions that might actually be better.",
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
        "In the lab (arms paying 30–72%), ε = 0.01 puts 36% of pulls on the best arm, ε = 0.1 65%, ε = 0.3 69%.",
    ),
    crossLinks = listOf(
        CrossLink("ucb", "UCB"),
        CrossLink("q_learning", "Q-Learning (off-policy)"),
    ),
)
