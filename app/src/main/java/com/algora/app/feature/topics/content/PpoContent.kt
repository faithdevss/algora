package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val ppoContent = TopicContent(
    topicId = "ppo",
    whatIsIt = listOf(
        "Proximal Policy Optimization (PPO) keeps policy updates close to the old policy using a clipped objective — capturing TRPO's stability with far simpler first-order optimization.",
        "It's the default policy-gradient algorithm in modern RL, prized for being robust, easy to tune, and effective across discrete and continuous tasks — including RLHF for LLMs.",
        "The problem it solves is specific: a policy-gradient step that is too large can move the policy somewhere its own collected data no longer describes, and the run never recovers. TRPO prevented that with a hard KL constraint and second-order optimization. PPO gets most of the same protection by refusing to reward the objective for ratios that stray outside [1−ε, 1+ε] — a clip, evaluated with ordinary backprop.",
    ),
    steps = listOf(
        StepCard(1, "Collect a Rollout", "Gather trajectories with the current policy and compute GAE advantages.", 0xFF818CF8),
        StepCard(2, "Probability Ratio", "Compute r = π_new/π_old for each action taken.", 0xFF60A5FA),
        StepCard(3, "Clip the Objective", "Cap the ratio to [1−ε, 1+ε] so beneficial moves can't overshoot.", 0xFF10B981),
        StepCard(4, "Take the Pessimistic Branch", "The min() of clipped and unclipped means clipping only ever removes upside — a bad action can still be pushed down without limit.", 0xFFF59E0B),
        StepCard(5, "Multiple Epochs", "Reuse the same batch for several gradient epochs before discarding it.", 0xFFEC4899),
        StepCard(6, "Add the Auxiliary Terms", "Total loss = clipped policy loss + value loss − entropy bonus, the last keeping the policy from collapsing early.", 0xFF8B5CF6),
    ),
    formulas = listOf(
        FormulaEntry("Clipped objective", "min(r·A, clip(r, 1−ε, 1+ε)·A)", "Pessimistic bound on the surrogate."),
        FormulaEntry("Ratio", "r = π_new(a|s) / π_old(a|s)", "How much the policy changed."),
        FormulaEntry("ε", "≈ 0.1–0.2", "Clip range — the effective trust region."),
        FormulaEntry("Full loss", "L = L_clip − c₁·L_value + c₂·H(π)", "c₁ ≈ 0.5, c₂ ≈ 0.01 are the usual weights."),
        FormulaEntry("Advantage (GAE)", "Aₜ = Σ (γλ)ˡ δₜ₊ₗ", "λ ≈ 0.95 trades bias against variance."),
        FormulaEntry("Asymmetry", "A > 0 → capped at 1+ε;  A < 0 → not capped", "Good actions are limited, bad ones are not."),
    ),
    notationKey = listOf(
        NotationEntry("r", "importance-sampling ratio"),
        NotationEntry("ε", "clipping range"),
        NotationEntry("A", "advantage estimate (usually GAE)"),
        NotationEntry("H(π)", "policy entropy — the exploration bonus"),
        NotationEntry("epochs", "how many times one batch is reused before being discarded"),
        NotationEntry("KL", "divergence from the old policy, monitored as an early-stop signal"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "PPO clipped loss (PyTorch)",
            accentColor = 0xFF6366F1,
            code = """
                ratio = torch.exp(new_log_prob - old_log_prob)
                clipped = torch.clamp(ratio, 1 - eps, 1 + eps)
                loss = -torch.min(ratio * adv, clipped * adv).mean()
            """.trimIndent(),
        ),
        CodeBlock(
            title = "The full update loop",
            accentColor = 0xFF10B981,
            code = """
                # One PPO iteration: collect a rollout, then reuse it for several epochs.
                states, actions, old_log_probs, advantages, returns = rollout(policy, env, steps=2048)
                advantages = (advantages - advantages.mean()) / (advantages.std() + 1e-8)

                for epoch in range(4):                       # batch reuse — the sample-efficiency win
                    for batch in minibatches(states, size=64):
                        log_prob, value, entropy = policy.evaluate(batch.states, batch.actions)
                        ratio = torch.exp(log_prob - batch.old_log_probs)

                        unclipped = ratio * batch.advantages
                        clipped = torch.clamp(ratio, 1 - eps, 1 + eps) * batch.advantages
                        policy_loss = -torch.min(unclipped, clipped).mean()

                        value_loss = F.mse_loss(value, batch.returns)
                        loss = policy_loss + 0.5 * value_loss - 0.01 * entropy.mean()

                        optimizer.zero_grad()
                        loss.backward()
                        nn.utils.clip_grad_norm_(policy.parameters(), 0.5)   # separate from the ratio clip
                        optimizer.step()

                    # Too many epochs on one batch drifts far from pi_old; bail out when it does.
                    if approx_kl(log_prob, batch.old_log_probs) > 0.015:
                        break
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.PolicyGradientPlayer,
    applications = listOf(
        ApplicationCard("robot", 0xFF818CF8, "Robotics & Control", "The workhorse for locomotion, manipulation, and continuous control."),
        ApplicationCard("book", 0xFF60A5FA, "RLHF for LLMs", "PPO is the classic optimizer that aligns language models to human feedback."),
        ApplicationCard("game", 0xFF10B981, "Game Agents", "OpenAI Five and many game-playing agents are PPO-based."),
        ApplicationCard("chip", 0xFFF59E0B, "Distributed Training", "On-policy rollouts parallelize cleanly across many environment workers, which is how PPO scales."),
    ),
    takeaways = listOf(
        "PPO clips the probability ratio to keep updates near the old policy.",
        "It gets TRPO-like stability with simple first-order optimization.",
        "It reuses each batch for several epochs, improving sample efficiency.",
        "The min() makes the objective pessimistic: clipping removes upside but never caps the penalty on a bad action.",
        "Advantage normalization and gradient clipping matter as much as the clip itself — most PPO failures are implementation details, not the algorithm.",
        "Its robustness made it the default policy-gradient method — including for RLHF.",
    ),
    crossLinks = listOf(
        CrossLink("trpo", "TRPO"),
        CrossLink("rlhf", "RLHF"),
        CrossLink("gae", "GAE"),
        CrossLink("actor_critic", "Actor-Critic"),
    ),
)
