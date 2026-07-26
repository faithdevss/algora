package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val doubleDqnContent = TopicContent(
    topicId = "double_dqn",
    whatIsIt = listOf(
        "Double DQN fixes Q-learning's tendency to overestimate action-values by separating the choice of the best next action from the evaluation of its value.",
        "The single max in standard DQN both picks and scores the next action with the same noisy estimates, systematically inflating targets; Double DQN decouples the two.",
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
    ),
    crossLinks = listOf(
        CrossLink("dqn", "DQN"),
        CrossLink("target_networks", "Target Networks"),
    ),
)
