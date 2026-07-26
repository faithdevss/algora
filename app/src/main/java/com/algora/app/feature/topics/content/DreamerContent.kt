package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val dreamerContent = TopicContent(
    topicId = "dreamer",
    whatIsIt = listOf(
        "Dreamer is a model-based RL agent that learns a latent world model and trains an actor-critic entirely on imagined trajectories rolled out inside that model.",
        "Because gradients flow through the differentiable latent dynamics, it learns efficiently, and by Dreamer V3 a single configuration solves tasks across many domains.",
    ),
    steps = listOf(
        StepCard(1, "Learn a Latent Model", "A recurrent state-space model (RSSM) predicts latent dynamics, rewards, and observations.", 0xFF818CF8),
        StepCard(2, "Imagine Rollouts", "Roll the model forward in latent space to generate long imagined trajectories.", 0xFF60A5FA),
        StepCard(3, "Actor-Critic in Imagination", "Train the policy and value function purely on those imagined rollouts.", 0xFF10B981),
        StepCard(4, "Act & Refine", "Deploy the policy in the real environment; new data improves the world model.", 0xFFF59E0B),
    ),
    formulas = listOf(
        FormulaEntry("RSSM", "latent dynamics + reward model", "A learned differentiable simulator."),
        FormulaEntry("Imagined return", "critic over dreamed rollouts", "Policy learns without real steps."),
        FormulaEntry("Dreamer V3", "one config, many domains", "Robust defaults across tasks."),
    ),
    notationKey = listOf(
        NotationEntry("RSSM", "recurrent state-space model"),
        NotationEntry("imagination", "latent rollouts used for policy learning"),
        NotationEntry("V1–V3", "successive Dreamer generations"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Dreamer's imagination loop (sketch)",
            accentColor = 0xFF6366F1,
            code = """
                # 1) Fit the world model on real experience.
                world_model.train(replay_buffer)
                # 2) Imagine latent rollouts and train actor-critic on them.
                traj = world_model.imagine(policy, horizon=15)
                actor_critic.update(traj)   # no real environment steps here
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.GameSearchPlayer,
    applications = listOf(
        ApplicationCard("game", 0xFF818CF8, "Broad Benchmarks", "Dreamer V3 solved Atari, control, and even Minecraft with one configuration."),
        ApplicationCard("robot", 0xFF60A5FA, "Sample-Efficient Control", "Learns from limited real interaction by training in imagination."),
        ApplicationCard("chart", 0xFF10B981, "Robust Defaults", "Reduced the per-task tuning that plagues most RL."),
    ),
    takeaways = listOf(
        "Dreamer trains an actor-critic on imagined latent rollouts from a learned world model.",
        "Differentiable latent dynamics make its imagination-based learning efficient.",
        "Dreamer V3's single configuration generalizes across many domains.",
        "It's a leading demonstration that model-based RL can be both general and sample-efficient.",
    ),
    crossLinks = listOf(
        CrossLink("world_models", "World Models"),
        CrossLink("mbpo", "MBPO"),
    ),
)
