package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.Figure
import com.algora.app.core.data.model.FigureBand
import com.algora.app.core.data.model.FigurePointer
import com.algora.app.core.data.model.FigureShape
import com.algora.app.core.data.model.FigureTone
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val stateActionRewardContent = TopicContent(
    topicId = "state_action_reward",
    figure = Figure(
        caption = "The unit of experience is a four-cell window, and it overlaps its neighbour: s₁ " +
            "closes one transition and opens the next, which is why a replay buffer's rows share " +
            "states. The indexing is the point — r₁ is numbered t+1 because it arrives with s₁, " +
            "after the action, not alongside a₀. The agent authors only the a cells; the " +
            "environment answers with the r and s cells. Sufficiency is a decision you make about " +
            "the s cells and not a property you inherit: one Pong frame carries no velocity, so " +
            "the state is four stacked frames, not one.",
        shape = FigureShape.Strip(
            cells = listOf("s₀", "a₀", "r₁", "s₁", "a₁", "r₂", "s₂"),
            bands = listOf(
                FigureBand(0, 3, "one transition (s, a, r, s′)", FigureTone.Primary),
                FigureBand(4, 6, "the next one", FigureTone.Muted),
            ),
            pointers = listOf(
                FigurePointer(2, "scores a₀"),
                FigurePointer(3, "shared"),
            ),
        ),
    ),
    whatIsIt = listOf(
        "Three signals define an RL problem. The state describes the situation, the action is what the agent may do about it, and the reward is a single number saying how good the last transition was. Everything else in RL is machinery for turning a stream of these into a policy.",
        "A state must be sufficient: given sₜ, the past adds nothing about the future. That is the Markov property, and it is a design requirement, not a fact about the world. A single frame of Pong is not a state — it has no velocity — but four stacked frames is. Getting this wrong is the most common reason an agent plateaus for reasons that look like an algorithm bug.",
        "Reward design is where RL projects fail quietly. The reward is the entire specification of what you want; the agent will optimize exactly what you wrote, including the parts you did not mean. Reward each step that moves closer to a goal, and an agent will happily oscillate toward and away from it forever; reward a boat race for hitting checkpoints, and it will farm one respawning checkpoint instead of finishing. State the outcome, not the route you imagine to it.",
    ),
    steps = listOf(
        StepCard(1, "Define the State", "Include everything needed to predict the future. Test it: could a human act well seeing only this?", 0xFF818CF8),
        StepCard(2, "Check the Markov Property", "If the past still helps, the state is incomplete — stack history or add memory.", 0xFF60A5FA),
        StepCard(3, "Define the Action Space", "Discrete (a menu) or continuous (a vector). This choice decides which algorithms are available.", 0xFF8B5CF6),
        StepCard(4, "Define the Reward", "Score the outcome you actually want. Keep it sparse and honest before making it dense and helpful.", 0xFF10B981),
        StepCard(5, "Add a Step Cost", "A small negative per step makes dawdling expensive and turns \"reach the goal\" into \"reach it soon\".", 0xFFF59E0B),
        StepCard(6, "Look for the Exploit", "Ask how a lazy optimizer could farm this reward without doing the task. Fix it before training, not after.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Transition tuple", "(sₜ, aₜ, rₜ₊₁, sₜ₊₁)", "The unit of experience every algorithm consumes."),
        FormulaEntry("Markov property", "P(sₜ₊₁|sₜ,aₜ) = P(sₜ₊₁|s₀…sₜ,a₀…aₜ)", "The present screens off the past."),
        FormulaEntry("Return", "Gₜ = rₜ₊₁ + γrₜ₊₂ + γ²rₜ₊₃ + …", "What is maximized — not the immediate reward."),
        FormulaEntry("Reward function", "R(s,a,s′) → ℝ", "A scalar. Multiple objectives must be weighted into one number."),
    ),
    notationKey = listOf(
        NotationEntry("S", "state space — every situation the agent can be in"),
        NotationEntry("A", "action space, sometimes A(s) if legal actions vary by state"),
        NotationEntry("R", "reward function"),
        NotationEntry("Gₜ", "return — discounted sum of future reward from t"),
        NotationEntry("sparse reward", "reward only at the outcome; hardest case to learn from"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "A state that is actually Markov",
            accentColor = 0xFFF97316,
            code = """
                # Broken: one frame has no velocity, so the agent cannot tell a ball
                # moving left from the same ball moving right.
                state = current_frame                         # NOT Markov

                # Fixed: stack the last k frames so motion is recoverable.
                from collections import deque
                buffer = deque(maxlen=4)

                def observe(frame):
                    buffer.append(frame)
                    while len(buffer) < 4:                    # pad the first steps
                        buffer.appendleft(frame)
                    return np.stack(buffer)                   # Markov enough
            """.trimIndent(),
        ),
        CodeBlock(
            title = "Reward shaping that does not create an exploit",
            accentColor = 0xFF10B981,
            code = """
                # Naive shaping: pays every step that moves closer but charges nothing for stepping
                # back, so oscillating toward and away from the goal pays forever (the classic
                # Randløv & Alstrøm bicycle exploit).
                def bad_reward(s, s_next, goal):
                    return max(0.0, distance(s, goal) - distance(s_next, goal))

                # Potential-based shaping (Ng et al., 1999). Because the bonus telescopes (to
                # -phi(s0) plus a vanishing tail when discounted; exactly zero around a cycle
                # when gamma = 1), it cannot change which policy is optimal — you speed up
                # learning without buying a new exploit.
                def shaped_reward(s, a, s_next, goal, gamma=0.99):
                    base = 1.0 if s_next == goal else -0.01          # the real objective
                    potential = lambda x: -distance(x, goal)
                    return base + gamma * potential(s_next) - potential(s)
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.RlGridWorld,
    applications = listOf(
        ApplicationCard("game", 0xFF818CF8, "Atari From Pixels", "DQN's state is four stacked greyscale frames precisely because one frame is not Markov."),
        ApplicationCard("robot", 0xFF60A5FA, "Robot Control", "State is joint angles plus velocities; actions are torques. Omit velocity and no algorithm can rescue you."),
        ApplicationCard("finance", 0xFF10B981, "Reward Hacking in Practice", "OpenAI's CoastRunners boat learned to loop a respawning checkpoint forever instead of finishing the race — the reward was written for progress, not for winning."),
    ),
    takeaways = listOf(
        "State must be sufficient to predict the future — that is a modelling decision you make, not a property you inherit.",
        "The action space determines which algorithm family you can use: discrete versus continuous is the first fork.",
        "Reward is the whole specification of the task; the agent optimizes it literally.",
        "Use potential-based shaping if you need a denser signal — it provably leaves the optimal policy alone.",
    ),
    crossLinks = listOf(
        CrossLink("agent_environment", "Agent & Environment"),
        CrossLink("discount_factor", "Horizon & Discount Factor (γ)"),
        CrossLink("pomdp", "Partially Observable MDP"),
        CrossLink("rlhf", "RLHF"),
    ),
)
