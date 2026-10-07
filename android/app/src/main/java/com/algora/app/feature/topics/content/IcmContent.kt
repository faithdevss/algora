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

internal val icmContent = TopicContent(
    topicId = "icm",
    figure = Figure(
        caption = "The page's lab: a maze with a \"noisy TV\" — a cell that shows a random channel " +
            "0–9 on every visit — and two ways of measuring curiosity, each over 30 runs. A forward " +
            "model that predicts raw observations can never predict the TV, so its error and its " +
            "bonus stay at 0.90 while every other cell grows boring: the agent spends 20% of all " +
            "steps staring at it, and only 1 run of 30 ever reaches the goal. ICM measures the " +
            "error in a learned feature space instead, where the features come from an inverse " +
            "model trained to tell which action was taken — and the TV's channel is not something " +
            "any action controls, so it drops out. TV time falls to 3.0%. The trap is gone, though " +
            "novelty spreads slowly through this maze and 4 of 30 runs reach the goal.",
        shape = FigureShape.Grid(
            rows = listOf(
                listOf("20%", "3.0%"),
                listOf("1 / 30", "4 / 30"),
            ),
            rowHeaders = listOf("steps on the TV", "runs reaching G"),
            colHeaders = listOf("predict pixels", "ICM features"),
            marks = listOf(
                FigureCell(0, 0, FigureTone.Warn),
                FigureCell(0, 1, FigureTone.Accent),
            ),
        ),
    ),
    whatIsIt = listOf(
        "The Intrinsic Curiosity Module pays an agent for being surprised. A forward model predicts the next state from the current state and action, and the size of its prediction error is added to the reward: wherever the model is wrong, the agent is curious and goes there, and once it has learned a region the bonus fades. That turns exploration of a reward-less maze into a task with a dense signal.",
        "Predicting raw observations has a known trap, and the lab builds it: a \"noisy TV\" cell that shows a random channel 0–9 on every visit. Normal cells become predictable after a few visits — the error falls from 1.00 towards zero — but the TV's next frame is pure chance, so its error stays at 0.90 forever and so does its bonus. A pixel-level forward model gets hooked: in one run it spends 24% of all steps on that single cell, and across 30 runs only 1 ever reaches the goal.",
        "ICM's fix is to predict in a learned feature space instead. An inverse model is trained to predict which action was taken between two states, and the features it needs are only those the agent's actions can affect — the TV's random channel is not one of them. Measured in those features, TV time drops from 20% to 3.0%. The trap is gone, though per-action novelty spreads slowly through this maze, and 4 of 30 runs reach the goal — the gap RND and count bonuses close.",
    ),
    steps = listOf(
        StepCard(1, "Encode States", "A learned encoder maps observations to features φ(s).", 0xFF818CF8),
        StepCard(2, "Inverse Model", "Predict the action from φ(s) and φ(s′) — this forces features to capture controllable dynamics.", 0xFF60A5FA),
        StepCard(3, "Forward Model", "Predict φ(s′) from φ(s) and the action; its error is the curiosity signal.", 0xFF10B981),
        StepCard(4, "Reward the Surprise", "Intrinsic reward = forward-model prediction error in feature space.", 0xFFF59E0B),
    ),
    formulas = listOf(
        FormulaEntry("Intrinsic reward", "r_i = ½‖φ̂(s′) − φ(s′)‖²", "Forward-prediction error."),
        FormulaEntry("Inverse model", "predict a from φ(s), φ(s′)", "Keeps features action-relevant."),
        FormulaEntry("Noise robustness", "ignores noise the agent cannot affect", "Robust to uncontrollable noise; noise the agent can control remains a known failure mode (Burda et al. 2018)."),
    ),
    notationKey = listOf(
        NotationEntry("φ(s)", "learned feature encoding of a state"),
        NotationEntry("forward model", "predicts next features"),
        NotationEntry("inverse model", "predicts the action taken"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "ICM curiosity reward",
            accentColor = 0xFF6366F1,
            code = """
                phi, phi_next = encoder(s), encoder(s2)
                pred_next = forward_model(phi, action)
                intrinsic_reward = 0.5 * ((pred_next - phi_next) ** 2).sum()
                # Inverse model (predict action from phi, phi_next) shapes the features.
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.ExplorationPlayer,
    applications = listOf(
        ApplicationCard("game", 0xFF818CF8, "Sparse-Reward Games", "Enabled progress on exploration-hard games from curiosity alone."),
        ApplicationCard("robot", 0xFF60A5FA, "Skill Acquisition", "Drives agents to interact with controllable parts of their world."),
        ApplicationCard("bulb", 0xFF10B981, "Noise-Robust Curiosity", "Feature learning filters out irrelevant randomness."),
    ),
    takeaways = listOf(
        "ICM rewards forward-model prediction error in a learned feature space.",
        "An inverse model keeps features focused on action-controllable dynamics.",
        "This design is robust to the noisy-TV distraction from noise the agent cannot influence; a noisy TV the agent controls can still fool it.",
        "It's a leading prediction-error form of intrinsic motivation.",
        "In the lab a pixel-prediction bonus spends 20% of steps on a noisy TV; ICM's learned features cut that to 3.0%.",
    ),
    crossLinks = listOf(
        CrossLink("intrinsic_motivation", "Intrinsic Motivation"),
        CrossLink("rnd", "Random Network Distillation"),
    ),
)
