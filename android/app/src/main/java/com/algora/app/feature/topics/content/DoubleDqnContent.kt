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

internal val doubleDqnContent = TopicContent(
    topicId = "double_dqn",
    figure = Figure(
        caption = "The page's lab trap, averaged over 100 runs: from state A, \"right\" ends the " +
            "episode with reward 0, while \"left\" leads to eight actions that each pay N(−0.1, 1) — " +
            "so left is worth −0.10 and the right answer is always right. The curves are how often " +
            "each learner still chooses left. Q-learning's target takes the max over eight noisy " +
            "estimates, and the max of noise is positive: its estimate of Q(A, left) peaks at " +
            "+0.095, it picks left 83% of the time after 10 episodes and still 32% after 100. " +
            "Double estimation lets one table choose the action and the other score it, so a lucky " +
            "draw cannot grade itself: its estimate peaks at +0.003, and it is down to 36% at " +
            "episode 10 and 6% by episode 100. Both settle low eventually; the difference is how " +
            "long the agent pays for an illusion.",
        shape = FigureShape.Plot(
            series = listOf(
                FigureSeries(
                    "Q-learning",
                    listOf(FigurePoint(0f, 0.83f), FigurePoint(0.333f, 0.76f), FigurePoint(0.667f, 0.32f), FigurePoint(1f, 0.06f)),
                    tone = FigureTone.Warn,
                ),
                FigureSeries(
                    "Double",
                    listOf(FigurePoint(0f, 0.36f), FigurePoint(0.333f, 0.20f), FigurePoint(0.667f, 0.06f), FigurePoint(1f, 0.08f)),
                    tone = FigureTone.Accent,
                ),
            ),
            markers = listOf(
                FigurePoint(0f, 0.83f, "83%", FigureTone.Warn),
                FigurePoint(0f, 0.36f, "36%"),
            ),
            xLabel = "episode 10 · 50 · 100 · 300",
            yLabel = "P(choose the bad action)",
        ),
    ),
    whatIsIt = listOf(
        "Double DQN fixes a bias that is built into Q-learning rather than a bug in any implementation. The target r + γ·max Q(s′, a′) uses the same noisy estimates both to pick the best next action and to score it, and the maximum of several noisy numbers is biased upward — whichever estimate happens to be too high gets selected. Double DQN decouples the two jobs: the online network picks the action, the target network scores it, so lucky noise no longer gets to grade itself.",
        "The lab builds the trap that exposes it. From state A, going right ends the episode with reward 0; going left leads to state B, where all eight actions pay a reward drawn from N(−0.1, 1). Every action in B is slightly bad, so the true value of going left is −0.10 and the right answer is always to go right. Plain Q-learning takes the max over eight noisy estimates in B, and that max looks positive: averaged over 100 runs its estimate of Q(A, left) peaks at +0.095, and after 10 episodes it chooses left 83% of the time, still 32% after 100.",
        "Double estimation removes the optimism. One table selects the best action in B and the other scores it, so the estimate of Q(A, left) peaks at only +0.003 and the agent chooses left 36% of the time after 10 episodes, 20% after 50, and 6% by episode 100 — the fallacy is gone before Q-learning has finished paying for it. In Double DQN the two estimators are simply the online and target networks DQN already has, which is why the change is one line of code and a standard ingredient of every modern value-based agent.",
    ),
    steps = listOf(
        StepCard(1, "Diagnose Overestimation", "maxₐ′ over noisy Q-values is biased upward — errors get selected, not averaged out.", 0xFF818CF8),
        StepCard(2, "Select with the Online Net", "Use the online network to pick the best next action.", 0xFF60A5FA),
        StepCard(3, "Evaluate with the Target Net", "Use the target network to score that chosen action.", 0xFF10B981),
        StepCard(4, "Reduce Bias", "Decoupling selection from evaluation cuts the overestimation, improving stability.", 0xFFF59E0B),
    ),
    formulas = listOf(
        FormulaEntry("DQN target", "r + γ·maxₐ′ Q(s′,a′; θ⁻)", "Select and evaluate with the same net — biased."),
        FormulaEntry("Double DQN", "r + γ·Q(s′, argmaxₐ′Q(s′,a′;θ); θ⁻)", "Online selects, target evaluates."),
        FormulaEntry("Effect", "less overestimation", "More accurate values, better policies."),
    ),
    notationKey = listOf(
        NotationEntry("θ", "online network — selects the action"),
        NotationEntry("θ⁻", "target network — evaluates it"),
        NotationEntry("overestimation bias", "upward error from max over noisy values"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Double DQN target",
            accentColor = 0xFF6366F1,
            code = """
                with torch.no_grad():
                    next_a = policy_net(s2).argmax(1, keepdim=True)   # online selects
                    next_q = target_net(s2).gather(1, next_a)         # target evaluates
                    target = r + gamma * next_q.squeeze() * (1 - done)
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.RlTrainingPlayer,
    applications = listOf(
        ApplicationCard("game", 0xFF818CF8, "Atari Improvements", "Double DQN raised scores over vanilla DQN across the Atari suite."),
        ApplicationCard("chip", 0xFF60A5FA, "Rainbow Component", "One of the six improvements combined in Rainbow DQN."),
        ApplicationCard("bulb", 0xFF10B981, "Value Accuracy", "Anywhere biased Q-values would hurt, the decoupling helps."),
    ),
    takeaways = listOf(
        "Double DQN reduces the overestimation bias of the max operator.",
        "It uses the online net to select and the target net to evaluate the next action.",
        "The change is tiny in code but reliably improves value accuracy.",
        "It's a standard ingredient in modern value-based agents.",
        "In the lab, Q-learning chooses the bad action 83% of the time after 10 episodes; Double estimation 36%, falling to 6% by episode 100.",
    ),
    crossLinks = listOf(
        CrossLink("dqn", "DQN"),
        CrossLink("target_networks", "Target Networks"),
    ),
)
