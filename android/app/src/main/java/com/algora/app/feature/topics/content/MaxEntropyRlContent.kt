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

internal val maxEntropyRlContent = TopicContent(
    topicId = "max_entropy_rl",
    figure = Figure(
        caption = "How the entropy weight α reshapes a policy over two nearly tied actions worth 1.0 " +
            "and 0.9, using the maximum-entropy optimum π(a) ∝ exp(Q(a)/α) from the page's lab. " +
            "Ordinary RL (α → 0) is winner-take-all: 100 / 0, a policy betting that its own value " +
            "estimates are right to a tenth. At α = 0.05 the lab's split is 88 / 12 — the better " +
            "action dominates, but the alternative stays alive. At α = 0.2 it is 62 / 38, and at " +
            "α = 0.5 nearly even, 55 / 45. α is the exchange rate between reward and randomness, " +
            "and it has a price: on the continuous task in the SAC lab, α = 0.2 gives up 0.10 of " +
            "expected reward. What it buys is exploration that is part of the objective and a " +
            "policy that degrades gracefully when its estimates — or the environment — turn out " +
            "wrong.",
        shape = FigureShape.Grid(
            rows = listOf(
                listOf("100%", "0%"),
                listOf("88%", "12%"),
                listOf("62%", "38%"),
                listOf("55%", "45%"),
            ),
            rowHeaders = listOf("α → 0 (greedy)", "α = 0.05", "α = 0.2", "α = 0.5"),
            colHeaders = listOf("Q = 1.0", "Q = 0.9"),
            marks = listOf(
                FigureCell(0, 1, FigureTone.Warn),
                FigureCell(1, 1, FigureTone.Accent),
            ),
        ),
    ),
    whatIsIt = listOf(
        "Standard RL maximises expected return, and its optimal policy is deterministic: one best action per state, every alternative discarded. Maximum-entropy RL changes the objective to return plus α times the policy's entropy, E[Σ rₜ + α·H(π(·|sₜ))], so the agent is paid for staying random where randomness costs little.",
        "The optimal policy for that objective is not greedy but Boltzmann: π(a) ∝ exp(Q(a)/α). The lab shows what that does to two nearly tied actions, worth 1.0 and 0.9: at α = 0.05 they split 88 / 12 instead of winner-take-all, larger α spreads the policy further, and α → 0 recovers the greedy policy. A policy that commits fully to a 1.0-versus-0.9 difference is betting that its own value estimates are right to a tenth; the entropy term keeps that bet hedged until the evidence separates them.",
        "α is the exchange rate between reward and randomness, and the cost is real: on the continuous task in the SAC lab, α = 0.2 gives up 0.10 of expected reward. What it buys is exploration that is part of the objective rather than bolted on, and robustness — policies that keep alternatives alive degrade more gracefully when the environment shifts. SAC is the practical algorithm built on this objective, with α tuned automatically to hit a target entropy.",
    ),
    steps = listOf(
        StepCard(1, "Augment the Objective", "Add α·H(π) to the return: value both reward and unpredictability.", 0xFF818CF8),
        StepCard(2, "Soft Value Functions", "Value functions incorporate the entropy term into their bootstrapping.", 0xFF60A5FA),
        StepCard(3, "Softmax Policy", "The optimal policy becomes a Boltzmann distribution over soft Q-values.", 0xFF10B981),
        StepCard(4, "Temperature Trade-Off", "α tunes how much exploration is rewarded versus pure reward-seeking.", 0xFFF59E0B),
    ),
    formulas = listOf(
        FormulaEntry("Objective", "J = E[Σ rₜ + α·H(π(·|sₜ))]", "Reward plus entropy."),
        FormulaEntry("Optimal policy", "π*(a|s) ∝ exp(Q_soft(s,a)/α)", "Boltzmann over soft Q-values."),
        FormulaEntry("Limit", "α→0 recovers standard RL", "Entropy weight controls the trade-off."),
    ),
    notationKey = listOf(
        NotationEntry("H(π)", "policy entropy"),
        NotationEntry("α", "temperature weighting entropy"),
        NotationEntry("soft value", "value including the entropy bonus"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Entropy-augmented return",
            accentColor = 0xFF6366F1,
            code = """
                # Standard reward plus an entropy bonus at each step.
                augmented_reward = reward + alpha * policy_entropy(state)
                # As alpha -> 0, this reduces to ordinary reward maximization.
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.PolicyGradientPlayer,
    applications = listOf(
        ApplicationCard("robot", 0xFF818CF8, "Robust Policies", "Entropy keeps behaviors flexible and resilient to perturbations."),
        ApplicationCard("bulb", 0xFF60A5FA, "Better Exploration", "The framework that motivates SAC and soft Q-learning."),
        ApplicationCard("game", 0xFF10B981, "Multimodal Solutions", "Captures several near-optimal strategies instead of one."),
    ),
    takeaways = listOf(
        "Max-entropy RL adds an entropy bonus so agents stay as random as success allows.",
        "It yields soft value functions and a Boltzmann-form optimal policy.",
        "The temperature α trades exploration against pure reward.",
        "SAC and soft Q-learning are its practical instantiations.",
        "In the lab two actions worth 1.0 and 0.9 split 88 / 12 at α = 0.05 instead of 100 / 0 under greedy RL.",
    ),
    crossLinks = listOf(
        CrossLink("sac", "SAC"),
        CrossLink("boltzmann_exploration", "Boltzmann Exploration"),
    ),
)
