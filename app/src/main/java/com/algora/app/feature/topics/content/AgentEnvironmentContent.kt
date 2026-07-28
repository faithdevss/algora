package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val agentEnvironmentContent = TopicContent(
    topicId = "agent_environment",
    whatIsIt = listOf(
        "Reinforcement learning splits the world in two. The agent is the part you are training — it chooses actions. The environment is everything else — it receives those actions and answers with a new observation and a scalar reward. The loop between them is the only channel either side has.",
        "The boundary is not physical, it is a boundary of control. A robot's motors are part of the environment, not the agent, because the agent cannot change how a motor responds to a command — it can only send the command and see what happened. The useful rule: anything the agent cannot alter arbitrarily is environment, even if it lives inside the same robot.",
        "This is what makes RL different from supervised learning. There is no dataset of correct actions. The agent generates its own data by acting, so a bad early policy produces bad data, which produces a bad policy — and the reward signal is the only correction available. Every algorithm in this section is a different answer to \"how do you climb out of that?\"",
    ),
    steps = listOf(
        StepCard(1, "Draw the Boundary", "Decide what the agent controls. Everything else — physics, other players, the reward rule itself — is environment.", 0xFFF97316),
        StepCard(2, "Agent Observes", "The environment emits an observation of its state. In a fully observable problem that observation is the state.", 0xFF818CF8),
        StepCard(3, "Agent Acts", "The agent picks an action from the action space, using whatever policy it currently has.", 0xFF60A5FA),
        StepCard(4, "Environment Responds", "It applies its transition dynamics, moves to a new state, and emits a reward for what just happened.", 0xFF10B981),
        StepCard(5, "Repeat Until Terminal", "The loop runs until the episode ends — a goal, a failure, or a step limit. Then it resets.", 0xFFF59E0B),
        StepCard(6, "Learn From the Stream", "The agent updates from the transitions it collected. Its new behaviour changes what it collects next.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("The loop", "aₜ ~ π(·|sₜ) → (sₜ₊₁, rₜ₊₁) ~ env", "One turn of agent then environment."),
        FormulaEntry("Transition", "P(s′|s,a)", "The environment's dynamics — usually unknown to the agent."),
        FormulaEntry("Trajectory", "τ = s₀,a₀,r₁,s₁,a₁,r₂,…", "The whole recorded interaction."),
        FormulaEntry("Objective", "max E[Σ rₜ]", "The agent wants total reward, not per-step reward."),
    ),
    notationKey = listOf(
        NotationEntry("sₜ", "state at time t"),
        NotationEntry("aₜ", "action taken at time t"),
        NotationEntry("rₜ₊₁", "reward for that action — indexed t+1 because it arrives with the next state"),
        NotationEntry("τ", "trajectory, one episode's full record"),
        NotationEntry("episode", "one run from reset to terminal state"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "The interaction loop (Gymnasium)",
            accentColor = 0xFFF97316,
            code = """
                import gymnasium as gym

                env = gym.make("CliffWalking-v0")
                obs, info = env.reset(seed=0)

                total = 0.0
                for t in range(200):
                    action = agent.act(obs)                    # the agent's only output
                    obs, reward, terminated, truncated, info = env.step(action)
                    agent.observe(reward, obs, terminated)     # the agent's only input
                    total += reward
                    if terminated or truncated:
                        obs, info = env.reset()
                        break

                # Note what the agent never sees: P(s'|s,a), the reward function, or the
                # environment's internal variables. Only the stream of (obs, reward).
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.RlGridWorld,
    applications = listOf(
        ApplicationCard("robot", 0xFFF97316, "Robotics", "The controller is the agent; the arm, the payload and gravity are environment. The boundary sits at the command interface, not at the robot's chassis."),
        ApplicationCard("game", 0xFF818CF8, "Game Playing", "The opponent is part of the environment. That is why self-play works — you improve the environment by improving the agent."),
        ApplicationCard("chart", 0xFF10B981, "Recommenders", "The user is the environment. Actions change what data you collect next, which is exactly the feedback loop supervised learning does not have."),
    ),
    takeaways = listOf(
        "The agent/environment split is a boundary of control, not of hardware.",
        "Only two things cross it: an action out, an observation and reward in.",
        "The agent generates its own training data, so early behaviour shapes what it can ever learn.",
        "Rewards are indexed t+1 because they arrive with the next state, not with the action.",
    ),
    crossLinks = listOf(
        CrossLink("state_action_reward", "State, Action, Reward"),
        CrossLink("mdp", "MDP"),
        CrossLink("grid_world", "Grid World"),
    ),
)
