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

internal val rainbowDqnContent = TopicContent(
    topicId = "rainbow_dqn",
    figure = Figure(
        caption = "Rainbow's six ingredients, each matched to the failure of plain DQN it fixes, with " +
            "the measurement from that technique's own page where the lab has one. Double " +
            "estimation stops a max over noise from inflating values (0.095 → 0.003 on the trap). " +
            "Replay reuses experience (error after 200 steps 0.33 → 0.00). Dueling learns a state's " +
            "value from every action taken in it. Noisy Nets replace coin-flip exploration with " +
            "learned, consistent noise. C51 keeps the whole return distribution, and multi-step " +
            "targets move reward back n states per update. Combined, Rainbow beat every " +
            "single-component variant on Atari — but the paper's ablations found the gains do not " +
            "simply add: prioritized replay and multi-step returns mattered most, and dueling and " +
            "Double barely registered once the others were in.",
        shape = FigureShape.Grid(
            rows = listOf(
                listOf("overestimation", "0.095 → 0.003"),
                listOf("wasted samples", "0.33 → 0.00"),
                listOf("relearning V", "V from every action"),
                listOf("dithering", "learned noise"),
                listOf("averaging risk", "full distribution"),
                listOf("slow propagation", "n-step targets"),
            ),
            rowHeaders = listOf("Double", "Replay", "Dueling", "Noisy", "C51", "Multi-step"),
            colHeaders = listOf("fixes", "in the labs"),
            marks = listOf(
                FigureCell(1, 1, FigureTone.Accent),
                FigureCell(5, 1, FigureTone.Accent),
            ),
        ),
    ),
    whatIsIt = listOf(
        "Rainbow DQN is not a new idea but an answer to an empirical question: which of the separately published fixes to DQN actually stack? It combines six — Double Q-learning, prioritized experience replay, the dueling architecture, multi-step returns, distributional value learning (C51) and Noisy Nets — into one agent, and on Atari it beat every one of them alone.",
        "Each ingredient targets a different failure of plain DQN, and the lab lines them up with the measurements from their own pages: Double estimation cuts the overestimated value of a bad action from 0.095 to 0.003; replay takes the error after 200 steps from 0.33 to 0.00; the dueling head learns the state's value from every action; Noisy Nets replace dithering exploration with consistent, learned noise; C51 keeps the whole return distribution instead of its mean; and multi-step targets carry reward back n steps per update instead of one.",
        "Not all six matter equally. In the Rainbow paper's ablations, removing prioritized replay or multi-step returns hurt the most, distributional learning was close behind, and removing the dueling head or Double Q-learning barely registered once the others were present — the fixes overlap, so each one's marginal value depends on what else is already there. That is the useful lesson beyond the agent itself: improvements measured in isolation do not simply add.",
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
        "The ablation showed most components matter, with PER, multi-step and distributional the most critical and double/dueling small.",
        "It became the go-to value-based baseline on Atari.",
        "Its lesson — combine complementary tricks — recurs across modern RL.",
    ),
    crossLinks = listOf(
        CrossLink("dqn", "DQN"),
        CrossLink("c51", "Distributional RL (C51)"),
    ),
)
