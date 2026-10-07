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

internal val rndContent = TopicContent(
    topicId = "rnd",
    figure = Figure(
        caption = "The page's lab: the same sparse-reward maze with a noisy TV, 30 runs per method, " +
            "counting how many runs ever reach the goal. Undirected ε-greedy never does. Curiosity " +
            "that predicts raw pixels gets stuck on the TV (20% of steps) and reaches it once; ICM's " +
            "learned features escape the trap but spread novelty slowly, 4 runs. RND predicts the " +
            "output of a fixed random network instead of the next frame, so every observation, " +
            "even each of the TV's ten channels, eventually becomes predictable — the TV's bonus " +
            "falls to 0.07 — and 15 runs reach the goal with 7.5% of steps on the TV. Exact visit " +
            "counting does best, 21, but it needs states that repeat; RND gets close without " +
            "that requirement, which is what lets it work on raw images.",
        shape = FigureShape.Plot(
            bars = listOf(
                FigureBar("ε-greedy", 0f, FigureTone.Muted),
                FigureBar("ICM pixels", 0.048f, FigureTone.Warn),
                FigureBar("ICM features", 0.190f, FigureTone.Primary),
                FigureBar("RND", 0.714f, FigureTone.Accent),
                FigureBar("exact counts", 1f, FigureTone.Muted),
            ),
            yLabel = "runs reaching the goal, 0 to 21 of 30",
        ),
    ),
    whatIsIt = listOf(
        "Random Network Distillation keeps \"bonus = prediction error\" but changes what is predicted. A target network with fixed random weights maps each observation to a vector; a predictor network is trained to match it on the observations the agent visits. The bonus is the predictor's error: large for observations it has rarely seen, shrinking roughly as 1/√n with each sighting.",
        "Because the target is a fixed function of the observation, everything eventually becomes predictable — even the noisy TV that traps a pixel-predicting curiosity module. Ten random channels are ten observations to learn, not an endless supply of surprise: in the lab the TV's bonus falls to 0.07. RND explores the whole maze instead of circling one cell — run 1 sees 38 of 40 cells and reaches the goal in 3 episodes — and across 30 runs 15 reach the goal, with 7.5% of steps on the TV.",
        "The comparison in the lab puts that in context. ε-greedy never reaches the goal (0 of 30 runs); a pixel curiosity model reaches it once and spends 20% of its time on the TV; ICM's features, 4 times. Exact visit counting does best, 21 of 30, but it needs states that repeat. RND nearly matches it without that requirement, which is why it was the first method to make real progress on Montezuma's Revenge.",
    ),
    steps = listOf(
        StepCard(1, "Fix a Random Target", "A randomly-initialized target network is frozen and never trained.", 0xFF818CF8),
        StepCard(2, "Train a Predictor", "A second network learns to match the target's output on visited states.", 0xFF60A5FA),
        StepCard(3, "Error = Novelty", "Prediction error is high on unfamiliar states, low on well-visited ones.", 0xFF10B981),
        StepCard(4, "Reward Novelty", "Use the error as an intrinsic reward encouraging exploration.", 0xFFF59E0B),
    ),
    formulas = listOf(
        FormulaEntry("Intrinsic reward", "r_i = ‖predictor(s) − target(s)‖²", "Distillation error as novelty."),
        FormulaEntry("Target", "fixed random network", "Never updated — a stable prediction goal."),
        FormulaEntry("Advantage", "no dynamics model", "Simpler and noise-robust vs ICM."),
    ),
    notationKey = listOf(
        NotationEntry("target network", "frozen random feature extractor"),
        NotationEntry("predictor", "trained to imitate the target"),
        NotationEntry("distillation error", "the novelty signal"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "RND novelty bonus",
            accentColor = 0xFF6366F1,
            code = """
                with torch.no_grad():
                    target_feat = target_net(state)      # fixed, random
                pred_feat = predictor_net(state)         # trained to match
                intrinsic_reward = (pred_feat - target_feat).pow(2).mean()
                # predictor is trained to minimize this on visited states.
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.ExplorationPlayer,
    applications = listOf(
        ApplicationCard("game", 0xFF818CF8, "Montezuma's Revenge", "RND achieved breakthrough scores on this notorious hard-exploration game."),
        ApplicationCard("robot", 0xFF60A5FA, "Sparse-Reward Control", "A drop-in novelty bonus for exploration-starved tasks."),
        ApplicationCard("bulb", 0xFF10B981, "Simple & Robust", "No dynamics model needed, and it resists the noisy-TV problem."),
    ),
    takeaways = listOf(
        "RND scores novelty by prediction error against a fixed random target network.",
        "High error means an unfamiliar state; low error means a well-visited one.",
        "It needs no dynamics or inverse model, making it simpler than ICM.",
        "It delivered landmark results on hard-exploration Atari games.",
        "In the lab RND reaches the goal in 15 of 30 runs (7.5% of steps on the noisy TV), against 0 for ε-greedy and 1 for pixel curiosity.",
    ),
    crossLinks = listOf(
        CrossLink("icm", "Curiosity-Driven (ICM)"),
        CrossLink("intrinsic_motivation", "Intrinsic Motivation"),
    ),
)
