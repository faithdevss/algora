package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val duelingDqnContent = TopicContent(
    topicId = "dueling_dqn",
    whatIsIt = listOf(
        "Dueling DQN restructures the Q-network into two streams — one estimating the state's value, the other the advantage of each action — then recombines them into Q-values.",
        "This lets the network learn which states are valuable without having to learn the effect of every action there, which helps when many actions barely matter.",
    ),
    steps = listOf(
        StepCard(1, "Shared Feature Trunk", "A common backbone processes the state.", 0xFF818CF8),
        StepCard(2, "Two Heads", "One head outputs the scalar state value V(s); the other outputs an advantage A(s,a) per action.", 0xFF60A5FA),
        StepCard(3, "Recombine", "Q(s,a) = V(s) + (A(s,a) − mean advantage), so the split is identifiable.", 0xFF10B981),
        StepCard(4, "Learn Efficiently", "State value is learned once per state, not re-learned through every action.", 0xFFF59E0B),
    ),
    formulas = listOf(
        FormulaEntry("Recombination", "Q = V(s) + (A(s,a) − meanₐ A(s,a))", "Subtracting the mean keeps V and A separable."),
        FormulaEntry("Value stream", "V(s)", "How good the state is overall."),
        FormulaEntry("Advantage", "A(s,a)", "How much better action a is than average."),
    ),
    notationKey = listOf(
        NotationEntry("V(s)", "state-value stream"),
        NotationEntry("A(s,a)", "advantage stream"),
        NotationEntry("mean subtraction", "identifiability constraint on the split"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Dueling head combination (PyTorch)",
            accentColor = 0xFF6366F1,
            code = """
                features = self.trunk(state)
                v = self.value_head(features)          # (B, 1)
                a = self.adv_head(features)            # (B, num_actions)
                q = v + (a - a.mean(dim=1, keepdim=True))
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.RlTrainingPlayer,
    applications = listOf(
        ApplicationCard("game", 0xFF818CF8, "Atari Gains", "Improved performance especially where many actions have similar value."),
        ApplicationCard("chip", 0xFF60A5FA, "Rainbow Component", "One of the architectural improvements folded into Rainbow DQN."),
        ApplicationCard("bulb", 0xFF10B981, "Efficient Value Learning", "Learns state values without exhaustively probing every action."),
    ),
    takeaways = listOf(
        "Dueling DQN splits Q into a state-value and an advantage stream.",
        "It learns state values efficiently when most actions are near-equivalent.",
        "Subtracting the mean advantage makes the two streams identifiable.",
        "It's purely an architecture change, compatible with Double DQN and replay.",
    ),
    crossLinks = listOf(
        CrossLink("dqn", "DQN"),
        CrossLink("rainbow_dqn", "Rainbow DQN"),
    ),
)
