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

internal val mbpoContent = TopicContent(
    topicId = "mbpo",
    figure = Figure(
        caption = "MBPO's rollout-length trade, from the page's lab: how many correct imagined steps a " +
            "k-step model rollout contributes, k·(1 − ε)^k, when each model step is wrong with " +
            "probability ε. Longer rollouts add more data until compounding error takes over — the " +
            "chance a whole rollout is still right is (1 − ε)^k, only 36% after 20 steps even at " +
            "ε = 5%. Each curve peaks near k ≈ −1/ln(1 − ε): about 19 steps for a 5% model error, " +
            "about 9 for 10%, about 4 for 20%, where after 20 steps only 1.2% of rollouts are still " +
            "right. That is MBPO's design rule: the worse the model, the shorter the rollouts, and " +
            "they branch from many real states rather than running long from the start, so " +
            "imagined data stays close to what the model has actually learned.",
        shape = FigureShape.Plot(
            series = listOf(
                FigureSeries(
                    "ε = 0.05",
                    listOf(
                        FigurePoint(0.000f, 0.127f), FigurePoint(0.053f, 0.241f), FigurePoint(0.105f, 0.343f),
                        FigurePoint(0.158f, 0.434f), FigurePoint(0.211f, 0.516f), FigurePoint(0.263f, 0.588f),
                        FigurePoint(0.316f, 0.652f), FigurePoint(0.368f, 0.708f), FigurePoint(0.421f, 0.756f),
                        FigurePoint(0.474f, 0.798f), FigurePoint(0.526f, 0.834f), FigurePoint(0.579f, 0.865f),
                        FigurePoint(0.632f, 0.890f), FigurePoint(0.684f, 0.910f), FigurePoint(0.737f, 0.927f),
                        FigurePoint(0.789f, 0.939f), FigurePoint(0.842f, 0.948f), FigurePoint(0.895f, 0.953f),
                        FigurePoint(0.947f, 0.956f), FigurePoint(1.000f, 0.956f),
                    ),
                    tone = FigureTone.Accent,
                ),
                FigureSeries(
                    "ε = 0.1",
                    listOf(
                        FigurePoint(0.000f, 0.120f), FigurePoint(0.053f, 0.216f), FigurePoint(0.105f, 0.292f),
                        FigurePoint(0.158f, 0.350f), FigurePoint(0.211f, 0.394f), FigurePoint(0.263f, 0.425f),
                        FigurePoint(0.316f, 0.446f), FigurePoint(0.368f, 0.459f), FigurePoint(0.421f, 0.465f),
                        FigurePoint(0.474f, 0.465f), FigurePoint(0.526f, 0.460f), FigurePoint(0.579f, 0.452f),
                        FigurePoint(0.632f, 0.441f), FigurePoint(0.684f, 0.427f), FigurePoint(0.737f, 0.412f),
                        FigurePoint(0.789f, 0.395f), FigurePoint(0.842f, 0.378f), FigurePoint(0.895f, 0.360f),
                        FigurePoint(0.947f, 0.342f), FigurePoint(1.000f, 0.324f),
                    ),
                    tone = FigureTone.Primary,
                ),
                FigureSeries(
                    "ε = 0.2",
                    listOf(
                        FigurePoint(0.000f, 0.107f), FigurePoint(0.053f, 0.171f), FigurePoint(0.105f, 0.205f),
                        FigurePoint(0.158f, 0.218f), FigurePoint(0.211f, 0.218f), FigurePoint(0.263f, 0.210f),
                        FigurePoint(0.316f, 0.196f), FigurePoint(0.368f, 0.179f), FigurePoint(0.421f, 0.161f),
                        FigurePoint(0.474f, 0.143f), FigurePoint(0.526f, 0.126f), FigurePoint(0.579f, 0.110f),
                        FigurePoint(0.632f, 0.095f), FigurePoint(0.684f, 0.082f), FigurePoint(0.737f, 0.070f),
                        FigurePoint(0.789f, 0.060f), FigurePoint(0.842f, 0.051f), FigurePoint(0.895f, 0.043f),
                        FigurePoint(0.947f, 0.037f), FigurePoint(1.000f, 0.031f),
                    ),
                    tone = FigureTone.Warn,
                ),
            ),
            markers = listOf(
                FigurePoint(0.974f, 0.956f, "ε 5%: ≈19"),
                FigurePoint(0.447f, 0.466f, "10%: ≈9", FigureTone.Primary),
                FigurePoint(0.184f, 0.220f, "20%: ≈4", FigureTone.Warn),
            ),
            xLabel = "rollout length k, 1 → 20",
            yLabel = "useful imagined steps, 0 to 7.5",
        ),
    ),
    whatIsIt = listOf(
        "MBPO — Model-Based Policy Optimization — asks how far a learned model should be trusted. Every imagined step can be wrong, and errors compound: a rollout that starts from a slightly wrong state predicts the next one from there. MBPO's answer is to branch many short model rollouts from states the agent has actually visited, and to train an off-policy learner (SAC) on the mix of real and imagined transitions.",
        "The lab puts numbers on the compounding. If each model step is wrong 5% of the time, the chance an imagined rollout is still entirely correct is 0.95^k: 95% after one step, 77% after five, 54% after twelve, and only 36% after twenty — long imagined rollouts are mostly fiction. The useful data a rollout contributes, k·(1 − ε)^k, rises and then falls, peaking near k ≈ −1/ln(1 − ε), about 19 steps at 5% error. Double the error to 10% and the sweet spot halves to about 9 steps; at 20%, it is about 4, and after 20 steps only 1.2% of rollouts are still right.",
        "That is MBPO's design in one formula: the worse the model, the shorter the rollouts should be, and the closer they should start to real data. Branching from many real states keeps imagined data near the distribution the model was trained on, and short horizons stop compounding error from dominating. MBPO matched model-free methods' final performance with a fraction of the real interaction on continuous-control benchmarks.",
    ),
    steps = listOf(
        StepCard(1, "Learn a Model Ensemble", "Train several probabilistic dynamics models to capture uncertainty.", 0xFF818CF8),
        StepCard(2, "Branch Short Rollouts", "From real states in the buffer, roll the model forward only k steps.", 0xFF60A5FA),
        StepCard(3, "Augment the Data", "Add these short imagined transitions to the agent's replay buffer.", 0xFF10B981),
        StepCard(4, "Optimize Model-Free", "Train SAC on the mix of real and model-generated data.", 0xFFF59E0B),
    ),
    formulas = listOf(
        FormulaEntry("Short horizon", "k steps only", "Limits compounding model error."),
        FormulaEntry("Branching", "rollouts start from real states", "Anchors imagination in true data."),
        FormulaEntry("Ensemble", "multiple models", "Uncertainty-aware predictions."),
    ),
    notationKey = listOf(
        NotationEntry("k", "model rollout horizon (short)"),
        NotationEntry("ensemble", "set of dynamics models"),
        NotationEntry("branching", "starting imagined rollouts from real states"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "MBPO short-rollout augmentation",
            accentColor = 0xFF6366F1,
            code = """
                for s in sample_real_states(real_buffer):
                    for _ in range(k):                 # short horizon only
                        a = policy(s)
                        s2, r = model_ensemble.step(s, a)
                        model_buffer.add((s, a, r, s2))
                        s = s2
                sac.update(real_buffer + model_buffer)
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.GameSearchPlayer,
    applications = listOf(
        ApplicationCard("robot", 0xFF818CF8, "Sample-Efficient Control", "Reaches strong continuous-control performance in far fewer real steps."),
        ApplicationCard("chart", 0xFF60A5FA, "Data-Limited RL", "Valuable where each real interaction is expensive or slow."),
        ApplicationCard("bulb", 0xFF10B981, "Model–Model-Free Hybrid", "Marries a learned model's efficiency with SAC's robustness."),
    ),
    takeaways = listOf(
        "MBPO augments a model-free agent with short model-generated rollouts.",
        "Keeping rollouts short avoids the compounding error of a learned model.",
        "Branching from real states anchors imagination in true experience.",
        "It substantially improves sample efficiency over pure model-free RL.",
        "In the lab a 5%-per-step model error leaves only 36% of 20-step rollouts correct; the best rollout length is about 19 steps at 5% error, 9 at 10%, 4 at 20%.",
    ),
    crossLinks = listOf(
        CrossLink("dyna_q", "Dyna-Q"),
        CrossLink("sac", "SAC"),
    ),
)
