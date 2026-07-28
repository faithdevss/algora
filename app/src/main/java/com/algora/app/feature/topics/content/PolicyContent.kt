package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val policyContent = TopicContent(
    topicId = "policy",
    whatIsIt = listOf(
        "A policy π is the agent's behaviour: a rule that turns a state into an action. It is the only thing that gets deployed — value functions, models and replay buffers are scaffolding used to build a good π, then thrown away.",
        "Policies come in two shapes. A deterministic policy a = μ(s) commits to one action per state. A stochastic policy π(a|s) returns a distribution and samples from it. The stochastic form is not merely a softer version: it is required whenever the optimal behaviour is genuinely random. In rock-paper-scissors any deterministic policy is beaten outright, and in a partially observable maze where two corridors look identical, a deterministic policy that turns left in both can be trapped forever while a coin flip escapes.",
        "The two great algorithm families are defined by how they get to π. Value-based methods (Q-learning, DQN) never represent a policy at all — they learn Q and read the policy off it as argmax. Policy-gradient methods (REINFORCE, PPO) parameterize π directly and push its parameters up the reward gradient. Actor-critic keeps both. Knowing which family you are in tells you immediately whether continuous actions are easy or awkward.",
    ),
    steps = listOf(
        StepCard(1, "Pick the Representation", "A table for small discrete problems; a neural network mapping state to action logits or to a mean and spread.", 0xFF818CF8),
        StepCard(2, "Deterministic or Stochastic", "Stochastic if the task is adversarial, partially observable, or you need exploration built into the policy itself.", 0xFF60A5FA),
        StepCard(3, "Act", "Deterministic: take μ(s). Stochastic: sample a ~ π(·|s), which explores for free.", 0xFF8B5CF6),
        StepCard(4, "Evaluate It", "Run it and measure the return. A policy is only meaningful together with the value it achieves.", 0xFF10B981),
        StepCard(5, "Improve It", "Either act greedily on a better value estimate, or move π's parameters along the policy gradient.", 0xFFF59E0B),
        StepCard(6, "Repeat to a Fixed Point", "Evaluate, improve, repeat. When improvement stops changing π, it is optimal for that value function.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Stochastic policy", "π(a|s) = P(aₜ = a | sₜ = s)", "A distribution over actions per state."),
        FormulaEntry("Deterministic policy", "a = μ(s)", "One action per state, used by DDPG and TD3."),
        FormulaEntry("Greedy policy", "π(s) = argmaxₐ Q(s,a)", "How value-based methods produce behaviour."),
        FormulaEntry("Optimal policy", "π* = argmaxπ Vπ(s) ∀s", "At least one deterministic π* always exists for a finite MDP."),
        FormulaEntry("Softmax policy", "π(a|s) ∝ exp(h(s,a)/τ)", "Turns preferences into probabilities; τ controls how sharp."),
    ),
    notationKey = listOf(
        NotationEntry("π", "policy — a stochastic mapping from states to actions"),
        NotationEntry("μ", "deterministic policy, by convention"),
        NotationEntry("π*", "an optimal policy"),
        NotationEntry("θ", "policy parameters, when π is a network (πθ)"),
        NotationEntry("on-policy", "learning about the policy currently generating the data"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "The three ways to produce an action",
            accentColor = 0xFFF97316,
            code = """
                import torch, torch.nn as nn

                # 1. Greedy from a value function — no policy object exists.
                def greedy(q_net, s):
                    return q_net(s).argmax(dim=-1)

                # 2. Stochastic categorical policy (discrete actions).
                class CategoricalPolicy(nn.Module):
                    def __init__(self, obs_dim, n_actions):
                        super().__init__()
                        self.net = nn.Sequential(nn.Linear(obs_dim, 64), nn.Tanh(),
                                                 nn.Linear(64, n_actions))

                    def forward(self, s):
                        return torch.distributions.Categorical(logits=self.net(s))

                # 3. Gaussian policy (continuous actions) — mean from the net, spread learned.
                class GaussianPolicy(nn.Module):
                    def __init__(self, obs_dim, act_dim):
                        super().__init__()
                        self.mu = nn.Sequential(nn.Linear(obs_dim, 64), nn.Tanh(),
                                                nn.Linear(64, act_dim))
                        self.log_std = nn.Parameter(torch.zeros(act_dim))

                    def forward(self, s):
                        return torch.distributions.Normal(self.mu(s), self.log_std.exp())

                dist = policy(state)
                action = dist.sample()              # exploration is built in
                logp = dist.log_prob(action)        # what the policy gradient needs
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.RlGridWorld,
    applications = listOf(
        ApplicationCard("robot", 0xFFF97316, "Continuous Control", "A Gaussian policy outputs joint torques directly — argmax over a continuous action space is not available, which is why robotics leans policy-gradient."),
        ApplicationCard("game", 0xFF818CF8, "Adversarial Games", "Optimal play in matching pennies or poker is provably stochastic; a deterministic policy is exploitable by construction."),
        ApplicationCard("chip", 0xFF10B981, "Language Models", "An LLM's next-token distribution is a stochastic policy over tokens — which is what makes RLHF a policy-gradient problem."),
    ),
    takeaways = listOf(
        "The policy is the deployed artifact; everything else is scaffolding used to find it.",
        "Stochastic policies are mandatory for adversarial and partially observable problems, not just convenient.",
        "Value-based methods derive π by argmax; policy-gradient methods learn π directly.",
        "A finite MDP always has an optimal deterministic policy — but only if it is fully observable.",
    ),
    crossLinks = listOf(
        CrossLink("value_function", "Value Function (V)"),
        CrossLink("reinforce", "REINFORCE"),
        CrossLink("ppo", "PPO"),
        CrossLink("pomdp", "Partially Observable MDP"),
    ),
)
