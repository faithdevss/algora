package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val cartpoleContent = TopicContent(
    topicId = "cartpole",
    whatIsIt = listOf(
        "CartPole is the classic control benchmark: balance a pole hinged on a cart by pushing the cart left or right, keeping the pole upright as long as possible.",
        "With a 4-dimensional continuous observation and two discrete actions, it's the 'hello world' of deep RL — small enough to solve in minutes, rich enough to be non-trivial.",
    ),
    steps = listOf(
        StepCard(1, "Observe the State", "Cart position and velocity, pole angle and angular velocity — four numbers.", 0xFF818CF8),
        StepCard(2, "Push Left or Right", "Two discrete actions apply a fixed force to the cart.", 0xFF60A5FA),
        StepCard(3, "Reward per Timestep", "Earn +1 for every step the pole stays balanced.", 0xFF10B981),
        StepCard(4, "Episode Ends", "Terminates if the pole falls too far or the cart leaves the track.", 0xFFF59E0B),
    ),
    formulas = listOf(
        FormulaEntry("Observation", "[x, ẋ, θ, θ̇]", "Continuous 4-vector."),
        FormulaEntry("Actions", "{left, right}", "Two discrete forces."),
        FormulaEntry("Solved", "avg reward ≥ 475/500", "The standard success threshold."),
    ),
    notationKey = listOf(
        NotationEntry("θ", "pole angle from vertical"),
        NotationEntry("x", "cart position on the track"),
        NotationEntry("episode return", "steps balanced (capped at 500)"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "CartPole with Gymnasium",
            accentColor = 0xFF6366F1,
            code = """
                import gymnasium as gym

                env = gym.make("CartPole-v1")
                obs, info = env.reset()
                action = env.action_space.sample()      # 0 = left, 1 = right
                obs, reward, terminated, truncated, info = env.step(action)
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.EnvironmentPlayer,
    applications = listOf(
        ApplicationCard("book", 0xFF818CF8, "Deep RL Hello-World", "The first environment to validate a DQN or policy-gradient implementation."),
        ApplicationCard("robot", 0xFF60A5FA, "Control Baseline", "A stand-in for classic inverted-pendulum control problems."),
        ApplicationCard("chart", 0xFF10B981, "Fast Iteration", "Trains in minutes, making it ideal for prototyping and debugging."),
    ),
    takeaways = listOf(
        "CartPole balances a pole via two discrete actions over a 4-D observation.",
        "It's the standard first test for deep RL algorithms.",
        "Reward is +1 per step; 'solved' means near-maximal average return.",
        "Its speed makes it perfect for prototyping before harder benchmarks.",
    ),
    crossLinks = listOf(
        CrossLink("dqn", "DQN"),
        CrossLink("mountain_car", "Mountain Car"),
    ),
)
