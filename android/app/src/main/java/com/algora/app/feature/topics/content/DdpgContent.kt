package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.Figure
import com.algora.app.core.data.model.FigureEdge
import com.algora.app.core.data.model.FigureGraphNode
import com.algora.app.core.data.model.FigureShape
import com.algora.app.core.data.model.FigureTone
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val ddpgContent = TopicContent(
    topicId = "ddpg",
    figure = Figure(
        caption = "Where DQN takes a max over actions, DDPG trains a network to be the max. The " +
            "actor μ proposes one real-valued action, exploration noise is added on top because " +
            "nothing in a deterministic policy explores, and the critic scores the pair. The " +
            "highlighted edge is the algorithm: ∂Q/∂a, the critic's slope with respect to the " +
            "action, is chained back into μ's weights, so the actor climbs whatever surface the " +
            "critic has learned. The critic itself is plain TD regression onto y = r + γQ′(s′, " +
            "μ′(s′)), built from target copies that trail the online networks by τ ≈ 0.005 per " +
            "step. That edge is also the failure mode: an actor pushed uphill on Q finds the places " +
            "Q overvalues before it finds the places that are good, which is the overestimation " +
            "TD3's twin critics exist to cap.",
        shape = FigureShape.Graph(
            nodes = listOf(
                FigureGraphNode("s", 0.05f, 0.50f, FigureTone.Primary),
                FigureGraphNode("actor μ", 0.30f, 0.15f, FigureTone.Primary),
                FigureGraphNode("a + noise", 0.62f, 0.15f),
                FigureGraphNode("critic Q", 0.62f, 0.85f, FigureTone.Primary),
                FigureGraphNode("y", 0.95f, 0.85f, FigureTone.Muted),
                FigureGraphNode("μ′, Q′", 0.95f, 0.30f, FigureTone.Muted),
            ),
            edges = listOf(
                FigureEdge(0, 1, directed = true),
                FigureEdge(1, 2, directed = true),
                FigureEdge(2, 3, directed = true),
                FigureEdge(0, 3, directed = true),
                FigureEdge(3, 1, "∂Q/∂a", directed = true, tone = FigureTone.Accent),
                FigureEdge(5, 4, directed = true),
                FigureEdge(4, 3, "TD target", directed = true),
            ),
        ),
    ),
    whatIsIt = listOf(
        "DDPG (Deep Deterministic Policy Gradient) is an off-policy actor-critic for continuous control that combines the deterministic policy gradient with DQN's replay buffer and target networks.",
        "Think of it as 'DQN for continuous actions': a deterministic actor proposes actions, a critic scores them, and both learn from replayed experience.",
        "The reason a new algorithm was needed at all: DQN picks actions by taking the max over Q for every action, which is impossible when actions are real-valued vectors. DDPG replaces that max with a learned actor — μ(s) *is* the argmax, trained by pushing it uphill on the critic's own gradient ∂Q/∂a.",
    ),
    steps = listOf(
        StepCard(1, "Actor + Critic Networks", "μ(s) outputs actions; Q(s,a) evaluates them, each with a target copy.", 0xFF818CF8),
        StepCard(2, "Off-Policy Replay", "Sample past transitions from a buffer, as in DQN.", 0xFF60A5FA),
        StepCard(3, "Exploration Noise", "Add noise (Ornstein-Uhlenbeck or Gaussian) to the deterministic action for exploration.", 0xFF10B981),
        StepCard(4, "Train the Critic", "Regress Q(s,a) onto the bootstrapped target y — plain TD learning, exactly as in DQN.", 0xFF8B5CF6),
        StepCard(5, "Push the Actor Uphill", "Backpropagate ∂Q/∂a through the critic into the actor's weights; only the actor's optimizer steps, so the critic's weights cannot change; disabling critic gradients just saves compute.", 0xFFEC4899),
        StepCard(6, "Soft Updates", "Slowly Polyak-average the targets toward the online networks.", 0xFFF59E0B),
    ),
    formulas = listOf(
        FormulaEntry("Critic target", "y = r + γ·Q′(s′, μ′(s′))", "Bootstrapped from target actor + critic."),
        FormulaEntry("Actor loss", "−E[Q(s, μ(s))]", "Push the policy toward higher Q."),
        FormulaEntry("Deterministic PG", "∇θ J = E[∇a Q(s,a)|_{a=μ(s)} · ∇θ μ(s)]", "Chain rule through the critic — the whole idea."),
        FormulaEntry("Soft update", "θ′ ← τθ + (1−τ)θ′", "Polyak averaging of targets."),
        FormulaEntry("τ", "≈ 0.005", "Small: the target must move far slower than the online net."),
        FormulaEntry("Overestimation", "E[max Q̂] ≥ max E[Q̂]", "Jensen's inequality — the bias TD3's twin critics attack."),
    ),
    notationKey = listOf(
        NotationEntry("μ(s)", "deterministic actor"),
        NotationEntry("Q(s,a)", "critic"),
        NotationEntry("exploration noise", "added to actions for off-policy exploration"),
        NotationEntry("τ", "Polyak coefficient for the soft target update"),
        NotationEntry("OU noise", "Ornstein-Uhlenbeck — temporally correlated noise for inertial systems"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "DDPG action with exploration noise",
            accentColor = 0xFF6366F1,
            code = """
                action = actor(state)
                action = (action + noise.sample()).clamp(-1.0, 1.0)   # explore
                # Critic trained on y = r + gamma * target_critic(s2, target_actor(s2)).
            """.trimIndent(),
        ),
        CodeBlock(
            title = "One training step, both losses",
            accentColor = 0xFF10B981,
            code = """
                s, a, r, s2, done = replay.sample(batch_size)

                # --- critic: ordinary TD regression, target computed with no gradient ---
                with torch.no_grad():
                    y = r + gamma * (1 - done) * target_critic(s2, target_actor(s2))
                critic_loss = F.mse_loss(critic(s, a), y)
                critic_opt.zero_grad(); critic_loss.backward(); critic_opt.step()

                # --- actor: climb the critic's action-gradient ---
                # Only actor_opt.step() runs, so the critic's weights cannot change here; disabling its
                # gradients only saves compute and avoids stale accumulated gradients.
                for p in critic.parameters():
                    p.requires_grad = False
                actor_loss = -critic(s, actor(s)).mean()
                actor_opt.zero_grad(); actor_loss.backward(); actor_opt.step()
                for p in critic.parameters():
                    p.requires_grad = True

                # --- Polyak: targets trail the online nets ---
                for net, target in ((actor, target_actor), (critic, target_critic)):
                    for p, tp in zip(net.parameters(), target.parameters()):
                        tp.data.copy_(tau * p.data + (1 - tau) * tp.data)
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.PolicyGradientPlayer,
    applications = listOf(
        ApplicationCard("robot", 0xFF818CF8, "Robotic Control", "Continuous joint control for locomotion and manipulation."),
        ApplicationCard("game", 0xFF60A5FA, "Continuous Environments", "MuJoCo and other continuous-action benchmarks."),
        ApplicationCard("finance", 0xFF10B981, "Continuous Decisions", "Portfolio and control problems with real-valued actions."),
        ApplicationCard("chip", 0xFFF59E0B, "Energy & HVAC Control", "Setpoint control over continuous ranges, learned from logged operation rather than a simulator."),
    ),
    takeaways = listOf(
        "DDPG is off-policy actor-critic for continuous actions — DQN's tricks meet DPG.",
        "The actor exists to replace DQN's max over actions, which continuous action spaces make impossible.",
        "It needs explicit exploration noise since the policy is deterministic.",
        "It's sample-efficient but notoriously brittle and hyperparameter-sensitive.",
        "Its central failure mode is Q overestimation: the actor learns to exploit whatever the critic overvalues.",
        "TD3 fixes its overestimation and instability issues.",
    ),
    crossLinks = listOf(
        CrossLink("dpg", "DPG"),
        CrossLink("td3", "TD3"),
        CrossLink("sac", "SAC"),
        CrossLink("target_networks", "Target Networks"),
    ),
)
