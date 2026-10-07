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

internal val targetNetworksContent = TopicContent(
    topicId = "target_networks",
    figure = Figure(
        caption = "The page's lab, following one learning target — the label for Q(s4, right), " +
            "0.9 · max Q(s5, ·) — over 3,000 updates, with the frozen target copy refreshed at three " +
            "different periods. With a sync every update there is no target network at all: the " +
            "label is recomputed from the weights being trained, moves on every update, and its " +
            "movement totals 3.46. Refreshing a frozen copy every 10 updates holds the label still " +
            "in flat runs and cuts the movement to 2.11; every 100, to 1.20. On this small problem " +
            "all three still end at about the same error, 0.004–0.005 — what changes is how much " +
            "the target wobbled getting there, and with a deep network that wobble is what turns " +
            "into divergence. Freeze too briefly and the network chases itself; too long and the " +
            "labels go stale. DQN synced every 10,000 steps.",
        shape = FigureShape.Plot(
            bars = listOf(
                FigureBar("sync every 1", 1f, FigureTone.Warn),
                FigureBar("every 10", 0.610f, FigureTone.Primary),
                FigureBar("every 100", 0.347f, FigureTone.Accent),
            ),
            yLabel = "total label movement, 0 to 3.46",
        ),
    ),
    whatIsIt = listOf(
        "A target network is a frozen copy of the Q-network used only to compute the learning target. Q-learning trains Q(s, a) toward r + γ·max Q(s′, ·) — a label built from the very weights being trained. With a table that is merely slow; with shared weights every update moves the labels for every state at once, so the network chases a target that moves because it moved. Freezing a copy, θ⁻, and computing labels from it turns each stretch between syncs into an ordinary regression problem against fixed targets.",
        "The lab follows one label, the target for Q(s4, right) = 0.9 · max Q(s5, ·), over 3,000 updates on a six-state chain. With no target network (a sync every update) the label is different on every update, and its total movement adds up to 3.46. Syncing a frozen copy every 10 updates holds the label still in flat runs with a step at each refresh, cutting the movement to 2.11; every 100 updates, to 1.20. All three end at essentially the same error (0.004–0.005) on this small, well-behaved problem — the difference is how much the target wobbled on the way, which is what turns into divergence once the function approximator is a deep network.",
        "The sync period is a trade between stability and freshness: too short and the network chases itself, too long and it is fitting labels computed from stale weights. DQN copied the weights every 10,000 steps; DDPG, TD3 and SAC instead use a soft update, θ⁻ ← τθ + (1 − τ)θ⁻ with τ around 0.005, which moves the target a little every step rather than in jumps.",
    ),
    steps = listOf(
        StepCard(1, "Keep Two Networks", "An online network being trained and a target network holding frozen weights.", 0xFF818CF8),
        StepCard(2, "Target Computes the Goal", "The TD target uses the target network's Q-values, not the online net's.", 0xFF60A5FA),
        StepCard(3, "Update Slowly", "Copy the online weights into the target every N steps (hard) or blend them each step (soft).", 0xFF10B981),
        StepCard(4, "Stabilize Learning", "A near-stationary target turns the moving-goalpost problem into stable regression.", 0xFFF59E0B),
    ),
    formulas = listOf(
        FormulaEntry("Target", "y = r + γ·maxₐ′ Q(s′,a′; θ⁻)", "θ⁻ are the frozen target params."),
        FormulaEntry("Hard update", "θ⁻ ← θ every N steps", "Periodic copy."),
        FormulaEntry("Soft update", "θ⁻ ← τθ + (1−τ)θ⁻", "Polyak averaging with small τ."),
    ),
    notationKey = listOf(
        NotationEntry("θ⁻", "target-network parameters"),
        NotationEntry("τ", "soft-update rate (Polyak coefficient)"),
        NotationEntry("N", "hard-update interval in steps"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Soft target update (Polyak)",
            accentColor = 0xFF6366F1,
            code = """
                # Blend online weights into the target network each step.
                tau = 0.005
                for tp, op in zip(target_net.parameters(), policy_net.parameters()):
                    tp.data.copy_(tau * op.data + (1 - tau) * tp.data)
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.RlTrainingPlayer,
    applications = listOf(
        ApplicationCard("chip", 0xFF818CF8, "Stable Deep RL", "Target networks are standard in DQN, DDPG, TD3, and SAC."),
        ApplicationCard("bulb", 0xFF60A5FA, "Divergence Prevention", "They tame the 'deadly triad' of bootstrapping, function approximation, and off-policy learning."),
        ApplicationCard("robot", 0xFF10B981, "Continuous Control", "Soft-updated targets are essential to actor-critic stability."),
    ),
    takeaways = listOf(
        "A target network provides stable TD targets by lagging the online network.",
        "It breaks the feedback loop where a network chases its own shifting predictions.",
        "Updates are hard (periodic copy) or soft (Polyak averaging).",
        "Together with replay, it's a core stabilizer of off-policy deep RL.",
        "In the lab, total movement of one label over 3,000 updates is 3.46 with no target network, 2.11 syncing every 10 updates and 1.20 every 100.",
    ),
    crossLinks = listOf(
        CrossLink("dqn", "DQN"),
        CrossLink("double_dqn", "Double DQN"),
    ),
)
