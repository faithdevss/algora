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
        "The trade is bias for variance. REINFORCE's return is unbiased but wildly noisy — one lucky episode credits every action in it. The critic's bootstrap is far steadier but wrong early in training, when the critic itself has learned nothing. Everything downstream (A2C, GAE, PPO) is a different point on that dial.",
    ),
    steps = listOf(
        StepCard(1, "Actor Acts", "The policy network chooses an action from the current state.", 0xFF818CF8),
        StepCard(2, "Critic Evaluates", "A value network estimates V(s), giving a bootstrapped assessment.", 0xFF60A5FA),
        StepCard(3, "Compute the Advantage", "Advantage = TD target − V(s): was the action better than expected?", 0xFF10B981),
        StepCard(4, "Detach the Advantage", "The advantage is a weight, not a differentiable path — let gradients flow into it and the actor learns to game the critic.", 0xFF8B5CF6),
        StepCard(5, "Update Both", "The actor ascends the advantage-weighted policy gradient; the critic descends its TD error.", 0xFFF59E0B),
        StepCard(6, "Keep Some Entropy", "Without an entropy bonus the policy tends to collapse onto one action before the critic is accurate enough to judge it.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Advantage", "A = r + γV(s′) − V(s)", "TD error as an advantage estimate."),
        FormulaEntry("Actor update", "θ += α·∇logπ(a|s)·A", "Advantage-weighted policy gradient."),
        FormulaEntry("Critic update", "minimize (r + γV(s′) − V(s))²", "TD regression."),
        FormulaEntry("n-step advantage", "A = Σᵏ γⁱrₜ₊ᵢ + γᵏV(sₜ₊ₖ) − V(sₜ)", "Larger k: more variance, less bias."),
        FormulaEntry("Combined loss", "L = −logπ·A + c₁(TD error)² − c₂H(π)", "Policy, value and entropy in one objective."),
        FormulaEntry("Why a baseline is free", "E[∇logπ(a|s)·b(s)] = 0", "Subtracting V(s) cuts variance without biasing the gradient."),
    ),
    notationKey = listOf(
        NotationEntry("actor", "the policy π(a|s)"),
        NotationEntry("critic", "the value estimator V(s)"),
        NotationEntry("A(s,a)", "advantage of an action over the baseline"),
        NotationEntry("δ", "TD error — the one-step advantage estimate"),
        NotationEntry("bootstrap", "using the critic's own estimate in place of the real return"),
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
        CodeBlock(
            title = "n-step actor-critic, written out",
            accentColor = 0xFF10B981,
            code = """
                def train_step(states, actions, rewards, next_state, done, gamma=0.99, n=5):
                    # Bootstrap from the critic unless the episode actually ended.
                    R = 0.0 if done else critic(next_state).item()

                    returns = []
                    for r in reversed(rewards):          # walk backwards accumulating the n-step return
                        R = r + gamma * R
                        returns.insert(0, R)
                    returns = torch.tensor(returns)

                    values = critic(states).squeeze()
                    advantages = returns - values

                    log_probs = actor(states).log_prob(actions)
                    # .detach(): the advantage weights the update, it is not a path to differentiate.
                    policy_loss = -(log_probs * advantages.detach()).mean()
                    value_loss = advantages.pow(2).mean()
                    entropy = actor(states).entropy().mean()

                    loss = policy_loss + 0.5 * value_loss - 0.01 * entropy
                    optimizer.zero_grad(); loss.backward(); optimizer.step()
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.PolicyGradientPlayer,
    applications = listOf(
        ApplicationCard("robot", 0xFF818CF8, "Continuous Control", "The actor-critic template underlies DDPG, TD3, SAC, and PPO."),
        ApplicationCard("game", 0xFF60A5FA, "Game Agents", "A2C/A3C and PPO agents master complex games with this structure."),
        ApplicationCard("bulb", 0xFF10B981, "Variance Reduction", "The critic tames the noise that plagues pure policy gradients."),
        ApplicationCard("book", 0xFFF59E0B, "RLHF", "The reward model scores completions while a value head plays critic — the same two-network structure."),
    ),
    takeaways = listOf(
        "Actor-critic combines a policy (actor) with a value function (critic).",
        "The critic's advantage estimate cuts the variance of the policy gradient.",
        "Subtracting a state-dependent baseline is free: it reduces variance without biasing the gradient.",
        "It enables per-step online updates instead of waiting for full episodes.",
        "Detach the advantage — a gradient path into it lets the actor optimize the critic instead of the policy.",
        "Nearly all modern policy-gradient algorithms are actor-critic variants.",
    ),
    crossLinks = listOf(
        CrossLink("reinforce", "REINFORCE"),
        CrossLink("a2c", "A2C"),
        CrossLink("gae", "GAE"),
        CrossLink("ppo", "PPO"),
    ),
)
