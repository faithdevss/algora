package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val actorCriticContent = TopicContent(
    topicId = "actor_critic",
    whatIsIt = listOf(
        "Actor-critic methods pair a policy (the actor) with a value estimator (the critic): the actor picks actions, and the critic evaluates them to give a lower-variance learning signal.",
        "By replacing REINFORCE's noisy episode return with the critic's estimate, they learn faster and can update every step instead of every episode.",
    ),
    steps = listOf(
        StepCard(1, "Actor Acts", "The policy network chooses an action from the current state.", 0xFF818CF8),
        StepCard(2, "Critic Evaluates", "A value network estimates V(s), giving a bootstrapped assessment.", 0xFF60A5FA),
        StepCard(3, "Compute the Advantage", "Advantage = TD target − V(s): was the action better than expected?", 0xFF10B981),
        StepCard(4, "Update Both", "The actor ascends the advantage-weighted policy gradient; the critic descends its TD error.", 0xFFF59E0B),
    ),
    formulas = listOf(
        FormulaEntry("Advantage", "A = r + γV(s′) − V(s)", "TD error as an advantage estimate."),
        FormulaEntry("Actor update", "θ += α·∇logπ(a|s)·A", "Advantage-weighted policy gradient."),
        FormulaEntry("Critic update", "minimize (r + γV(s′) − V(s))²", "TD regression."),
    ),
    notationKey = listOf(
        NotationEntry("actor", "the policy π(a|s)"),
        NotationEntry("critic", "the value estimator V(s)"),
        NotationEntry("A(s,a)", "advantage of an action over the baseline"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Actor-critic update (PyTorch sketch)",
            accentColor = 0xFF6366F1,
            code = """
                advantage = (r + gamma * critic(s2) - critic(s)).detach()
                actor_loss  = -(log_prob * advantage)
                critic_loss = (r + gamma * critic(s2) - critic(s)).pow(2)
                (actor_loss + critic_loss).backward()
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.PolicyGradientPlayer,
    applications = listOf(
        ApplicationCard("robot", 0xFF818CF8, "Continuous Control", "The actor-critic template underlies DDPG, TD3, SAC, and PPO."),
        ApplicationCard("game", 0xFF60A5FA, "Game Agents", "A2C/A3C and PPO agents master complex games with this structure."),
        ApplicationCard("bulb", 0xFF10B981, "Variance Reduction", "The critic tames the noise that plagues pure policy gradients."),
    ),
    takeaways = listOf(
        "Actor-critic combines a policy (actor) with a value function (critic).",
        "The critic's advantage estimate cuts the variance of the policy gradient.",
        "It enables per-step online updates instead of waiting for full episodes.",
        "Nearly all modern policy-gradient algorithms are actor-critic variants.",
    ),
    crossLinks = listOf(
        CrossLink("reinforce", "REINFORCE"),
        CrossLink("a2c", "A2C"),
    ),
)
