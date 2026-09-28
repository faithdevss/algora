package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val sacContent = TopicContent(
    topicId = "sac",
    whatIsIt = listOf(
        "Soft Actor-Critic (SAC) is an off-policy actor-critic that maximizes reward plus policy entropy, so the agent stays exploratory and robust rather than collapsing to a single deterministic action.",
        "The entropy bonus is baked into the objective, and a learnable temperature automatically balances exploration against exploitation.",
        "The distinction from DDPG and TD3 is where the randomness lives. Those add exploration noise on top of a deterministic policy — an external hack the objective knows nothing about. SAC's policy is stochastic and its entropy is part of what is being maximized, so exploration anneals itself as the agent grows confident, rather than following a schedule you have to tune.",
    ),
    steps = listOf(
        StepCard(1, "Maximum-Entropy Objective", "Reward the policy for both high return and high entropy (randomness).", 0xFF818CF8),
        StepCard(2, "Stochastic Actor", "The policy outputs a distribution; actions are sampled and reparameterized for gradients.", 0xFF60A5FA),
        StepCard(3, "Squash Into Bounds", "A tanh maps the Gaussian sample into the action range, with a log-determinant correction so the log-probability stays exact.", 0xFF8B5CF6),
        StepCard(4, "Twin Soft Critics", "Two Q-networks (min target) include the entropy term in their bootstrap.", 0xFF10B981),
        StepCard(5, "Auto-Tune Temperature", "A learnable α adjusts the entropy weight to hit a target entropy.", 0xFFF59E0B),
        StepCard(6, "Soft-Update the Targets", "Polyak averaging moves the target critics slowly, exactly as in DDPG.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Objective", "E[Σ rₜ + α·H(π(·|sₜ))]", "Reward plus entropy."),
        FormulaEntry("Soft target", "y = r + γ(minᵢ Qᵢ′ − α·logπ)", "Entropy folded into the target."),
        FormulaEntry("Temperature", "α auto-tuned", "Balances exploration and exploitation."),
        FormulaEntry("Actor loss", "E[α·logπ(a|s) − min Qᵢ(s,a)]", "Maximize value while staying random."),
        FormulaEntry("Temperature loss", "−α·(logπ + H̄)", "Drives entropy toward the target H̄ = −dim(A)."),
        FormulaEntry("Tanh correction", "logπ = logN(u) − Σ log(1 − tanh²(u))", "Required, or the log-probability is simply wrong."),
    ),
    notationKey = listOf(
        NotationEntry("H(π)", "policy entropy"),
        NotationEntry("α", "temperature — the entropy weight"),
        NotationEntry("reparameterization", "trick for low-variance stochastic gradients"),
        NotationEntry("H̄", "target entropy, conventionally −dim(action space)"),
        NotationEntry("τ", "Polyak coefficient for the soft target update"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "SAC soft critic target",
            accentColor = 0xFF6366F1,
            code = """
                a2, logp2 = actor.sample(s2)                    # reparameterized
                q_next = torch.min(target_q1(s2, a2), target_q2(s2, a2)) - alpha * logp2
                y = r + gamma * q_next * (1 - done)
            """.trimIndent(),
        ),
        CodeBlock(
            title = "Sampling with the tanh correction, and the α update",
            accentColor = 0xFF10B981,
            code = """
                def sample(self, state):
                    mean, log_std = self.net(state).chunk(2, dim=-1)
                    std = log_std.clamp(-20, 2).exp()

                    normal = torch.distributions.Normal(mean, std)
                    u = normal.rsample()                        # reparameterized: gradients flow through
                    action = torch.tanh(u)                      # squash into [-1, 1]

                    # Change of variables for tanh — omit this and log_prob is wrong.
                    log_prob = normal.log_prob(u) - torch.log(1 - action.pow(2) + 1e-6)
                    return action, log_prob.sum(-1, keepdim=True)

                # Temperature is learned, not tuned: push entropy toward -dim(A).
                alpha_loss = -(log_alpha * (log_prob + target_entropy).detach()).mean()
                alpha_optimizer.zero_grad(); alpha_loss.backward(); alpha_optimizer.step()
                alpha = log_alpha.exp()
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.PolicyGradientPlayer,
    applications = listOf(
        ApplicationCard("robot", 0xFF818CF8, "Real-World Robotics", "Its sample efficiency and stability suit learning on physical robots."),
        ApplicationCard("game", 0xFF60A5FA, "Continuous Benchmarks", "State-of-the-art on MuJoCo continuous-control suites."),
        ApplicationCard("bulb", 0xFF10B981, "Robust Exploration", "The entropy objective keeps the policy from prematurely collapsing."),
        ApplicationCard("chip", 0xFFF59E0B, "Multi-Modal Solutions", "When several action sequences are equally good, a stochastic policy keeps all of them instead of arbitrarily committing to one."),
    ),
    takeaways = listOf(
        "SAC maximizes reward plus entropy for robust, exploratory policies.",
        "It's off-policy, stochastic, and uses twin soft critics.",
        "Exploration is part of the objective, not noise bolted onto a deterministic actor.",
        "An auto-tuned temperature removes a key hyperparameter headache.",
        "The tanh log-probability correction is easy to omit and quietly breaks the entropy term when you do.",
        "It's a top choice for continuous control alongside TD3.",
    ),
    crossLinks = listOf(
        CrossLink("td3", "TD3"),
        CrossLink("max_entropy_rl", "Maximum Entropy RL"),
        CrossLink("ddpg", "DDPG"),
        CrossLink("experience_replay", "Experience Replay"),
    ),
)
