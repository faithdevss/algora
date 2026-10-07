package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.Figure
import com.algora.app.core.data.model.FigureCell
import com.algora.app.core.data.model.FigureShape
import com.algora.app.core.data.model.FigureTone
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val duelingDqnContent = TopicContent(
    topicId = "dueling_dqn",
    figure = Figure(
        caption = "The page's lab: Q-values for both actions on its six-state chain at γ = 0.9, and " +
            "the dueling split of each state into a value V(s) — the mean of the two Qs — and the " +
            "advantage of moving right, A = Q(s, right) − V(s). The two Q columns are nearly equal " +
            "in every state, because most of each number is how good it is to be there; the column " +
            "that actually decides the action is the small one on the right, 5.3% of V at the start " +
            "(and only 0.5% at γ = 0.99 in the lab's second tab). A plain DQN learns each Q " +
            "separately and only updates the action it took. The dueling head learns V from every " +
            "sample, whichever action produced it, so the large shared part is learned fast and the " +
            "advantage stream only has to resolve the sliver that chooses.",
        shape = FigureShape.Grid(
            rows = listOf(
                listOf("0.59", "0.53", "0.56", "0.03"),
                listOf("0.66", "0.53", "0.59", "0.07"),
                listOf("0.73", "0.59", "0.66", "0.07"),
                listOf("0.81", "0.66", "0.73", "0.08"),
                listOf("0.90", "0.73", "0.81", "0.09"),
                listOf("1.00", "0.81", "0.91", "0.09"),
            ),
            rowHeaders = listOf("s0", "s1", "s2", "s3", "s4", "s5"),
            colHeaders = listOf("Q right", "Q left", "V(s)", "A right"),
            marks = listOf(
                FigureCell(0, 3, FigureTone.Accent),
                FigureCell(0, 2, FigureTone.Primary),
            ),
        ),
    ),
    whatIsIt = listOf(
        "Dueling DQN changes the network's head, not the learning rule. Instead of producing one number per action directly, it splits into two streams — one estimating the value of the state, V(s), the other the advantage of each action, A(s, a) — and recombines them as Q(s, a) = V(s) + A(s, a) − mean A. The subtraction keeps the split identifiable: without it, any constant could move freely between the two streams.",
        "The lab shows why the split helps on its six-state chain. Q(s, right) and Q(s, left) are nearly equal in every state — 0.59 and 0.53 at the start — because most of each Q-value is \"how good is it to be here\" and only a sliver is \"which action\". At the start the advantage of the better action is 5.3% of the state's value; at γ = 0.99 it shrinks to 0.5%, a difference easy to drown in noise if it has to be learned inside one number per action.",
        "With the dueling head, an update for one action also moves V(s), so every sample teaches the value of the state, and the advantage stream only has to learn the small part that decides the action. Plain DQN, by contrast, updates only the Q-value of the action actually taken. It is purely an architecture change, so it combines freely with Double DQN, prioritized replay and the rest of the Rainbow ingredients.",
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
        "In the lab the choice of action is worth 5.3% of a state's value at γ = 0.9 and only 0.5% at γ = 0.99 — the part a separate advantage stream protects.",
    ),
    crossLinks = listOf(
        CrossLink("dqn", "DQN"),
        CrossLink("rainbow_dqn", "Rainbow DQN"),
    ),
)
