package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val atariContent = TopicContent(
    topicId = "atari",
    whatIsIt = listOf(
        "The Atari 2600 suite (ALE) is the landmark deep-RL benchmark: dozens of classic games where an agent learns from raw screen pixels and the game score alone.",
        "It defined modern deep RL — DQN's mastery of Atari from pixels launched the field, and the 57-game suite remains the standard yardstick for general game-playing agents.",
    ),
    steps = listOf(
        StepCard(1, "Pixels In", "The observation is the raw game frame (usually stacked and grayscaled).", 0xFF818CF8),
        StepCard(2, "Discrete Controls", "Actions are the joystick/button combinations of the Atari controller.", 0xFF60A5FA),
        StepCard(3, "Score as Reward", "The in-game score change is the reward signal.", 0xFF10B981),
        StepCard(4, "Compare to Humans", "Scores are normalized against human players across the 57-game suite.", 0xFFF59E0B),
    ),
    formulas = listOf(
        FormulaEntry("Observation", "84×84 stacked frames", "Preprocessed pixels."),
        FormulaEntry("Suite", "Atari-57", "The standard set of games."),
        FormulaEntry("Metric", "human-normalized score", "0 = random, 100 = human."),
    ),
    notationKey = listOf(
        NotationEntry("ALE", "Arcade Learning Environment"),
        NotationEntry("frame stacking", "stacking recent frames for motion"),
        NotationEntry("human-normalized", "score relative to human play"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Atari with Gymnasium",
            accentColor = 0xFF6366F1,
            code = """
                import gymnasium as gym

                env = gym.make("ALE/Breakout-v5")
                obs, info = env.reset()                  # raw RGB frame
                obs, reward, term, trunc, info = env.step(env.action_space.sample())
                # Standard pipelines grayscale, resize to 84x84, and stack 4 frames.
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.EnvironmentPlayer,
    applications = listOf(
        ApplicationCard("game", 0xFF818CF8, "Deep RL Benchmark", "DQN, Rainbow, and successors are measured on Atari-57."),
        ApplicationCard("image", 0xFF60A5FA, "Learning from Pixels", "Tests visual representation learning for control."),
        ApplicationCard("chart", 0xFF10B981, "Generality Test", "One agent, many games — a probe of general competence."),
    ),
    takeaways = listOf(
        "Atari (ALE) is the benchmark that launched deep RL, learning from pixels and score.",
        "The 57-game suite tests a single agent's generality.",
        "Frames are preprocessed and stacked; scores are human-normalized.",
        "DQN and Rainbow are the canonical agents evaluated on it.",
    ),
    crossLinks = listOf(
        CrossLink("dqn", "DQN"),
        CrossLink("cnn", "CNNs"),
    ),
)
