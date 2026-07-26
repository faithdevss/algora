package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val rainbowDqnContent = TopicContent(
    topicId = "rainbow_dqn",
    whatIsIt = listOf(
        "Rainbow DQN combines six independent improvements to DQN into a single agent that substantially outperforms any of them alone.",
        "It's the empirical answer to 'which DQN tricks actually stack?' — and the ablation showed each component contributes.",
    ),
    steps = listOf(
        StepCard(1, "Double + Dueling", "Decoupled action selection plus separate value/advantage streams.", 0xFF818CF8),
        StepCard(2, "Prioritized Replay", "Sample high-TD-error transitions more often.", 0xFF60A5FA),
        StepCard(3, "Multi-Step + Distributional", "n-step returns for faster credit assignment, plus C51's return distribution.", 0xFF10B981),
        StepCard(4, "Noisy Nets", "Learned, state-dependent exploration replacing ε-greedy.", 0xFFF59E0B),
    ),
    formulas = listOf(
        FormulaEntry("Components", "6 combined", "Double, Dueling, PER, Multi-step, Distributional, Noisy."),
        FormulaEntry("n-step return", "Σ γᵏ rₜ₊ₖ + γⁿ maxQ", "Faster reward propagation than 1-step."),
        FormulaEntry("Result", "SOTA on Atari-57", "Beats every single-component variant."),
    ),
    notationKey = listOf(
        NotationEntry("n-step", "multi-step bootstrapped returns"),
        NotationEntry("ablation", "removing one component to measure its effect"),
        NotationEntry("Atari-57", "the 57-game benchmark suite"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "The six Rainbow ingredients",
            accentColor = 0xFF6366F1,
            code = """
                # Rainbow = DQN + these six improvements:
                #   1. Double DQN            (less overestimation)
                #   2. Dueling architecture  (value + advantage streams)
                #   3. Prioritized replay    (TD-error-weighted sampling)
                #   4. Multi-step returns    (n-step targets)
                #   5. Distributional RL     (C51 return distribution)
                #   6. Noisy Nets            (learned exploration)
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.RlTrainingPlayer,
    applications = listOf(
        ApplicationCard("game", 0xFF818CF8, "Atari Benchmark", "Rainbow set a strong value-based standard on Atari-57."),
        ApplicationCard("chip", 0xFF60A5FA, "Strong Baseline", "A common reference agent for evaluating new value-based methods."),
        ApplicationCard("bulb", 0xFF10B981, "Integration Study", "Demonstrates that carefully chosen improvements compose."),
    ),
    takeaways = listOf(
        "Rainbow fuses six DQN improvements into one strong agent.",
        "The ablation confirmed each component adds value.",
        "It became the go-to value-based baseline on Atari.",
        "Its lesson — combine complementary tricks — recurs across modern RL.",
    ),
    crossLinks = listOf(
        CrossLink("dqn", "DQN"),
        CrossLink("c51", "Distributional RL (C51)"),
    ),
)
