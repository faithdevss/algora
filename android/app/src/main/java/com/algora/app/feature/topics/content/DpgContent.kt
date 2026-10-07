package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.Figure
import com.algora.app.core.data.model.FigurePoint
import com.algora.app.core.data.model.FigureSeries
import com.algora.app.core.data.model.FigureShape
import com.algora.app.core.data.model.FigureTone
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val dpgContent = TopicContent(
    topicId = "dpg",
    figure = Figure(
        caption = "The deterministic policy gradient on a one-dimensional action, as in the page's " +
            "lab: a critic surface Q(a) with its optimum at a = 0.45 (drawn as an illustrative " +
            "smooth hill), and a policy that outputs one action, starting at −0.80. There is no max " +
            "over a continuous action to take, so DPG asks the critic which way is uphill and " +
            "follows the chain rule, ∇θ J = ∇θ μ(s) · ∂Q/∂a. In the lab that walks the action from " +
            "−0.80 to 0.45 in 18 steps without sampling a single action, so the gradient carries no " +
            "sampling noise at all. The same property is the catch: a policy that always outputs " +
            "one action explores nothing, so noise has to be added to its actions by hand — and " +
            "the actor will happily climb into any spot where the critic is wrong, which is the " +
            "overestimation TD3 later fixes.",
        shape = FigureShape.Plot(
            series = listOf(
                FigureSeries(
                    "critic Q(a)",
                    listOf(
                        FigurePoint(0.000f, 0.004f), FigurePoint(0.050f, 0.136f), FigurePoint(0.100f, 0.259f),
                        FigurePoint(0.150f, 0.373f), FigurePoint(0.200f, 0.477f), FigurePoint(0.250f, 0.572f),
                        FigurePoint(0.300f, 0.658f), FigurePoint(0.350f, 0.733f), FigurePoint(0.400f, 0.800f),
                        FigurePoint(0.450f, 0.857f), FigurePoint(0.500f, 0.904f), FigurePoint(0.550f, 0.942f),
                        FigurePoint(0.600f, 0.970f), FigurePoint(0.650f, 0.989f), FigurePoint(0.700f, 0.999f),
                        FigurePoint(0.750f, 0.999f), FigurePoint(0.800f, 0.989f), FigurePoint(0.850f, 0.970f),
                        FigurePoint(0.900f, 0.942f), FigurePoint(0.950f, 0.904f), FigurePoint(1.000f, 0.857f),
                    ),
                ),
            ),
            markers = listOf(
                FigurePoint(0.1f, 0.259f, "start −0.80", FigureTone.Warn),
                FigurePoint(0.725f, 1.000f, "optimum 0.45"),
            ),
            xLabel = "action a, −1 to +1",
            yLabel = "Q(a)",
        ),
    ),
    whatIsIt = listOf(
        "The deterministic policy gradient answers a question DQN cannot: what to do when the action is a real number. A Q-network chooses by taking the max over actions, and there is no max over an uncountable set. A stochastic policy gradient would sample actions and average; DPG instead makes the policy deterministic, a = μ(s), and asks the critic which way is uphill.",
        "Its update is the chain rule through the critic: ∇θ J = ∇θ μ(s) · ∂Q/∂a, evaluated at the action the policy chose. The lab follows it on a one-dimensional critic surface whose optimum is at a = 0.45: starting from a = −0.80 and following ∂Q/∂a, the policy reaches 0.45 in 18 steps without ever sampling an action. Because no action is sampled, the gradient has no sampling variance at all — the expectation over actions collapses to a single evaluation, which is the deterministic policy gradient theorem's payoff.",
        "The bill arrives elsewhere: a deterministic policy explores nothing. Exploration noise has to be added to the actions by hand, which makes the method off-policy, and in practice it needs the replay buffer and target networks DQN introduced to stay stable. That combination is DDPG; TD3 then fixes DDPG's overestimation.",
    ),
    steps = listOf(
        StepCard(1, "Deterministic Actor", "The policy μ(s) maps a state to one continuous action, not a distribution.", 0xFF818CF8),
        StepCard(2, "Critic Learns Q", "A Q-network estimates the value of state-action pairs.", 0xFF60A5FA),
        StepCard(3, "Gradient Through the Critic", "∇Q with respect to the action tells the actor which way to move.", 0xFF10B981),
        StepCard(4, "Chain Rule Update", "Backprop that action gradient into the actor's parameters.", 0xFFF59E0B),
    ),
    formulas = listOf(
        FormulaEntry("DPG theorem", "∇J = E[∇_a Q(s,a)|_{a=μ(s)} · ∇_θ μ(s)]", "Chain rule through the critic."),
        FormulaEntry("Deterministic policy", "a = μ(s; θ)", "One action per state."),
        FormulaEntry("Why it helps", "no action integral", "Efficient in continuous, high-dim action spaces."),
    ),
    notationKey = listOf(
        NotationEntry("μ(s)", "the deterministic policy"),
        NotationEntry("Q(s,a)", "the critic's action-value"),
        NotationEntry("∇_a Q", "action-gradient guiding the actor"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Deterministic policy gradient (actor loss)",
            accentColor = 0xFF6366F1,
            code = """
                # Maximize the critic's Q at the actor's chosen action.
                actor_loss = -critic(s, actor(s)).mean()
                actor_loss.backward()   # gradient flows Q -> action -> actor params
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.PolicyGradientPlayer,
    applications = listOf(
        ApplicationCard("robot", 0xFF818CF8, "Continuous Control", "Enables policy gradients for robot joints and continuous actuators."),
        ApplicationCard("chip", 0xFF60A5FA, "Basis for DDPG", "DDPG is DPG made deep, with replay and target networks."),
        ApplicationCard("bulb", 0xFF10B981, "Efficient Gradients", "Avoids the costly action integral of stochastic policy gradients."),
    ),
    takeaways = listOf(
        "DPG learns a deterministic policy, ideal for continuous action spaces.",
        "Its gradient flows through the critic's Q with respect to the action.",
        "It skips the expensive integral over actions that stochastic PG needs.",
        "DDPG and TD3 descend from the deterministic-gradient idea; SAC borrows its backprop-through-Q mechanism for a stochastic, reparameterized policy.",
        "In the lab, following ∂Q/∂a moves the action from −0.80 to the optimum 0.45 in 18 steps, with no sampled actions at all.",
    ),
    crossLinks = listOf(
        CrossLink("ddpg", "DDPG"),
        CrossLink("actor_critic", "Actor-Critic"),
    ),
)
