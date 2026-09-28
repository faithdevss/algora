package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val mujocoContent = TopicContent(
    topicId = "mujoco",
    whatIsIt = listOf(
        "MuJoCo is a fast physics engine whose simulated robots — hoppers, walkers, cheetahs, humanoids — are the standard benchmark for continuous-control RL.",
        "Agents output real-valued joint torques from continuous observations, making these tasks the proving ground for algorithms like PPO, TD3, and SAC.",
    ),
    steps = listOf(
        StepCard(1, "Continuous Observations", "Joint angles, velocities, and body positions form a real-valued state vector.", 0xFF818CF8),
        StepCard(2, "Continuous Actions", "Actions are torques applied to each joint, in a bounded continuous range.", 0xFF60A5FA),
        StepCard(3, "Reward Locomotion", "Reward forward velocity while penalizing energy and falling.", 0xFF10B981),
        StepCard(4, "Learn to Move", "The agent discovers gaits — walking, running, hopping — from scratch.", 0xFFF59E0B),
    ),
    formulas = listOf(
        FormulaEntry("Action space", "continuous torques", "Bounded real vector per joint."),
        FormulaEntry("Reward", "forward speed − control cost", "Move efficiently without falling."),
        FormulaEntry("Tasks", "Hopper, Walker, Cheetah, Humanoid", "Increasing difficulty."),
    ),
    notationKey = listOf(
        NotationEntry("torque", "continuous force applied at a joint"),
        NotationEntry("gait", "the learned pattern of locomotion"),
        NotationEntry("continuous control", "real-valued action spaces"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "A MuJoCo task with Gymnasium",
            accentColor = 0xFF6366F1,
            code = """
                import gymnasium as gym

                env = gym.make("HalfCheetah-v4")
                obs, info = env.reset()                  # continuous state vector
                action = env.action_space.sample()       # continuous joint torques
                obs, reward, term, trunc, info = env.step(action)
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.EnvironmentPlayer,
    applications = listOf(
        ApplicationCard("robot", 0xFF818CF8, "Continuous-Control Benchmark", "The standard suite for PPO, TD3, and SAC evaluation."),
        ApplicationCard("game", 0xFF60A5FA, "Locomotion Research", "Learning gaits for simulated legged robots."),
        ApplicationCard("chip", 0xFF10B981, "Sim-to-Real", "A stepping stone toward transferring policies to physical robots."),
    ),
    takeaways = listOf(
        "MuJoCo provides the standard continuous-control locomotion benchmarks.",
        "Its tasks use continuous observations and joint-torque actions.",
        "PPO, TD3, and SAC are routinely measured on its Hopper–Humanoid suite.",
        "It bridges toward real-robot control via sim-to-real transfer.",
    ),
    crossLinks = listOf(
        CrossLink("sac", "SAC"),
        CrossLink("ppo", "PPO"),
    ),
)
