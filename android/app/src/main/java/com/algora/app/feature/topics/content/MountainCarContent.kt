package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val mountainCarContent = TopicContent(
    topicId = "mountain_car",
    whatIsIt = listOf(
        "Mountain Car is a classic exploration challenge: an underpowered car in a valley must build momentum by rocking back and forth to reach a hilltop goal.",
        "Its reward is sparse — a penalty every step until the goal — so naive agents never stumble on success, making it a favorite test of exploration and reward shaping.",
    ),
    steps = listOf(
        StepCard(1, "Underpowered Engine", "The car can't climb directly; it must swing to gain energy.", 0xFF818CF8),
        StepCard(2, "Observe Position & Velocity", "A 2-D continuous state describes where the car is and how fast.", 0xFF60A5FA),
        StepCard(3, "Act", "Push left, right, or coast (or apply continuous force in the continuous variant).", 0xFF10B981),
        StepCard(4, "Sparse Reward", "−1 each step until the flag is reached — exploration is the whole difficulty.", 0xFFF59E0B),
    ),
    formulas = listOf(
        FormulaEntry("Observation", "[position, velocity]", "Continuous 2-vector."),
        FormulaEntry("Reward", "−1 per step until goal", "Sparse; encourages fast solutions."),
        FormulaEntry("Challenge", "momentum-building", "Requires temporally-extended strategy."),
    ),
    notationKey = listOf(
        NotationEntry("position", "car's location along the track"),
        NotationEntry("velocity", "car's speed and direction"),
        NotationEntry("sparse reward", "signal only near the goal"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Mountain Car with Gymnasium",
            accentColor = 0xFF6366F1,
            code = """
                import gymnasium as gym

                env = gym.make("MountainCar-v0")         # discrete actions: 0,1,2
                obs, info = env.reset()                  # [position, velocity]
                obs, reward, term, trunc, info = env.step(2)   # push right
                # reward is -1 each step until the flag — exploration-hard.
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.EnvironmentPlayer,
    applications = listOf(
        ApplicationCard("bulb", 0xFF818CF8, "Exploration Testbed", "A canonical benchmark for exploration strategies and reward shaping."),
        ApplicationCard("robot", 0xFF60A5FA, "Momentum Control", "Models underactuated systems that need energy-building maneuvers."),
        ApplicationCard("chart", 0xFF10B981, "Sparse-Reward Study", "Exposes how agents cope when rewards are rare."),
    ),
    takeaways = listOf(
        "Mountain Car needs momentum-building, not direct action, to reach the goal.",
        "Its sparse per-step penalty makes exploration the core difficulty.",
        "It's a standard benchmark for exploration methods and reward shaping.",
        "Both discrete and continuous-force variants exist.",
    ),
    crossLinks = listOf(
        CrossLink("intrinsic_motivation", "Intrinsic Motivation"),
        CrossLink("cartpole", "CartPole"),
    ),
)
