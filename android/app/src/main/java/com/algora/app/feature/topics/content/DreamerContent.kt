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

internal val dreamerContent = TopicContent(
    topicId = "dreamer",
    figure = Figure(
        caption = "The page's lab, a tabular stand-in for Dreamer's learned latent model so every number " +
            "can be checked: the same real interaction per episode, and the number of episodes " +
            "until the value error falls below 0.05. Learning model-free, every update costs a real " +
            "step, and it takes 60 episodes. Training the actor and critic on 5 imagined steps from " +
            "the world model per real step takes 15; on 20, it takes 7. The real data is used only " +
            "to keep improving the model — the behaviour itself is learned in imagination. Dreamer " +
            "V1 to V3 scale exactly this to pixels with a recurrent latent model, and share the " +
            "limit every model-based method has: the policy is optimal for the model, and will " +
            "exploit whatever the model gets wrong.",
        shape = FigureShape.Plot(
            bars = listOf(
                FigureBar("model-free", 1f, FigureTone.Warn),
                FigureBar("imagine × 5", 0.25f, FigureTone.Primary),
                FigureBar("imagine × 20", 0.117f, FigureTone.Accent),
            ),
            yLabel = "episodes to converge, 0 to 60",
        ),
    ),
    whatIsIt = listOf(
        "Dreamer learns its behaviour in imagination. It trains a world model from real experience — in the real system, a recurrent network over a compact latent state learned from pixels — and then trains an actor and a critic entirely on trajectories imagined inside that model, never on real steps directly. Real interaction is only used to keep improving the model.",
        "The lab uses a tabular stand-in so every number is checkable, and holds the real interaction per episode fixed while varying how much imagined training happens per step. Model-free, every bit of learning costs a real step, and it takes 60 episodes for the value error to fall below 0.05. With 5 imagined updates per real step it takes 15 episodes; with 20, it takes 7. The real steps are unchanged — only the thinking per step went up.",
        "The successive versions scale the idea rather than change it: DreamerV1 learned continuous latents from pixels, V2 switched to discrete latents and reached human level on Atari, and V3 used one fixed set of hyperparameters across very different domains, including collecting diamonds in Minecraft from scratch. The limit is the one every model-based method shares: the policy is optimal for the model, and anything the model gets wrong, the policy will exploit.",
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
        "In the lab, with the same real interaction, convergence takes 60 episodes model-free, 15 with 5 imagined updates per step and 7 with 20.",
    ),
    crossLinks = listOf(
        CrossLink("world_models", "World Models"),
        CrossLink("mbpo", "MBPO"),
    ),
)
