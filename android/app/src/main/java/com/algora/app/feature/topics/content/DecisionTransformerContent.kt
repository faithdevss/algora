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

internal val decisionTransformerContent = TopicContent(
    topicId = "decision_transformer",
    figure = Figure(
        caption = "The page's lab: a decision transformer trained on 250 mostly poor trajectories " +
            "(returns −1.25 to 0.75), asked for a range of target returns, and what it actually " +
            "achieved. For targets from 0.00 to 0.75 the conditioning works exactly — ask for 0.25 " +
            "and it gets 0.25 — because it picks the action that logged trajectories with that much " +
            "return still to come took from each state. Ask for −0.50 and it gets −1.25: a target " +
            "can be matched only by behaviour the log actually contains. Beyond the data it " +
            "saturates — asking for 1.00 or 2.00 returns 0.75, the best logged return. It can " +
            "stitch together the best behaviour it has seen; it cannot invent better. Plain " +
            "behaviour cloning, for comparison, copies the majority action and scores −1.25; " +
            "return conditioning gives the filtering of \"clone only the good trajectories\" for free.",
        shape = FigureShape.Plot(
            series = listOf(
                FigureSeries(
                    "achieved = requested",
                    listOf(FigurePoint(0f, 0.273f), FigurePoint(0.5f, 0.727f)),
                    tone = FigureTone.Muted,
                    dashed = true,
                ),
                FigureSeries(
                    "achieved",
                    listOf(
                        FigurePoint(0f, 0f), FigurePoint(0.2f, 0.455f), FigurePoint(0.3f, 0.545f),
                        FigurePoint(0.4f, 0.636f), FigurePoint(0.5f, 0.727f), FigurePoint(0.6f, 0.727f),
                        FigurePoint(1f, 0.727f),
                    ),
                    tone = FigureTone.Accent,
                ),
            ),
            markers = listOf(
                FigurePoint(0.5f, 0.727f, "best logged 0.75"),
                FigurePoint(1f, 0.727f, "asked 2.00", FigureTone.Warn),
                FigurePoint(0f, 0f, "asked −0.5: −1.25", FigureTone.Warn),
            ),
            xLabel = "requested return, −0.5 → 2.0",
            yLabel = "achieved return, −1.25 to 1.5",
        ),
    ),
    whatIsIt = listOf(
        "The Decision Transformer recasts RL as sequence modelling. It trains a transformer on logged trajectories written as (return-to-go, state, action) tokens, and learns to predict the action that came next. To act, you tell it the return you want — the return-to-go — and it outputs the action that logged trajectories with that much return still to come took from here, decrementing the target after each reward. There is no value function and no Bellman backup.",
        "The lab uses a deliberately mixed log: 250 trajectories from random walkers with different biases, returns from −1.25 to 0.75, mean −0.59, most of them poor. Behaviour cloning copies the majority action in each state; near the start that is ←, so the clone oscillates and scores −1.25, the worst outcome in the data. Conditioning on return changes that: ask for 0.00 and it achieves 0.00; ask for 0.25, 0.50 or 0.75 and it hits each exactly — though a low target like −0.50 lands on −1.25, since it can only reproduce returns the log contains. Targets beyond the data saturate — asking for 2.00 returns 0.75, the best logged return.",
        "That is both the appeal and the limit. Return conditioning gives the filtering of \"clone only the good trajectories\" for free — here it matches cloning the top 10%, both at 0.75 — and it can stitch good behaviour together, but it cannot invent behaviour better than the best it has seen, and it struggles in stochastic environments where a high return was luck rather than skill. Dynamic-programming methods like CQL can exceed the data; sequence models trade that for stability and simplicity.",
    ),
    steps = listOf(
        StepCard(1, "Tokenize Trajectories", "Represent each trajectory as a sequence of (return-to-go, state, action) tokens.", 0xFF818CF8),
        StepCard(2, "Autoregressive Training", "Train a causal Transformer to predict actions from the preceding tokens.", 0xFF60A5FA),
        StepCard(3, "Condition on Return", "At test time, prompt it with a target return-to-go.", 0xFF10B981),
        StepCard(4, "Generate Actions", "It outputs actions that, per the training data, tend to reach that return.", 0xFFF59E0B),
    ),
    formulas = listOf(
        FormulaEntry("Sequence", "(R̂₁,s₁,a₁, R̂₂,s₂,a₂, …)", "Return-to-go, state, action tokens."),
        FormulaEntry("Objective", "predict aₜ | tokens<ₜ", "Supervised action prediction."),
        FormulaEntry("Control via prompt", "condition on target R̂", "Desired return steers behavior."),
    ),
    notationKey = listOf(
        NotationEntry("return-to-go", "sum of future rewards, R̂ₜ"),
        NotationEntry("causal Transformer", "attends only to past tokens"),
        NotationEntry("sequence modeling", "RL cast as next-token prediction"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Decision Transformer input",
            accentColor = 0xFF6366F1,
            code = """
                # Interleave returns-to-go, states, actions as one token sequence.
                tokens = interleave(returns_to_go, states, actions)
                pred_actions = transformer(tokens)          # causal, predicts next action
                # At test: set the first return-to-go to the desired target.
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.OfflineRlPlayer,
    applications = listOf(
        ApplicationCard("robot", 0xFF818CF8, "Offline Control", "Competitive on offline-RL benchmarks with no dynamic programming."),
        ApplicationCard("book", 0xFF60A5FA, "RL-as-Sequence", "Brings the Transformer toolbox and scaling to decision making."),
        ApplicationCard("chart", 0xFF10B981, "Return-Conditioned Behavior", "Dial performance by choosing the target return."),
    ),
    takeaways = listOf(
        "Decision Transformer treats RL as return-conditioned sequence modeling.",
        "It predicts actions autoregressively — no value functions or Bellman updates.",
        "Prompting with a target return-to-go steers the generated behavior.",
        "It ports Transformer scaling and simplicity into offline RL.",
        "In the lab cloning the mixed log scores −1.25; conditioning on return hits targets from 0.00 to 0.75 exactly and saturates at the best logged 0.75.",
    ),
    crossLinks = listOf(
        CrossLink("transformers", "Transformers"),
        CrossLink("offline_rl", "Offline RL"),
    ),
)
