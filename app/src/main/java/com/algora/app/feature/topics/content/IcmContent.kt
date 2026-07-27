package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val icmContent = TopicContent(
    topicId = "icm",
    whatIsIt = listOf(
        "The Intrinsic Curiosity Module (ICM) rewards an agent for prediction error in a learned feature space — the agent is curious about outcomes it can't yet predict.",
        "Its clever trick is to learn features that only capture things the agent's actions can affect, so it ignores unpredictable-but-irrelevant noise (the 'noisy TV problem').",
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
        FormulaEntry("Noise robustness", "ignores uncontrollable noise", "Solves the noisy-TV distraction."),
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
        "This design sidesteps the noisy-TV distraction that fools naive curiosity.",
        "It's a leading prediction-error form of intrinsic motivation.",
    ),
    crossLinks = listOf(
        CrossLink("intrinsic_motivation", "Intrinsic Motivation"),
        CrossLink("rnd", "Random Network Distillation"),
    ),
)
