package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val pomdpContent = TopicContent(
    topicId = "pomdp",
    whatIsIt = listOf(
        "A Partially Observable MDP is an MDP where the agent never sees the state. It receives an observation o ~ O(·|s) that is some lossy function of it — a camera that cannot see behind a wall, a sensor with noise, a poker hand that hides the opponent's cards.",
        "The consequence is sharper than it first sounds: the observation is not Markov, so everything built on top of it breaks. Two genuinely different states can produce identical observations, and any policy mapping observations to actions must treat them identically. In a corridor where every tile looks the same, a deterministic reactive policy that goes left will go left in all of them — and if left is wrong in one, it is stuck there forever. This is why a POMDP can require a stochastic policy even though its underlying MDP has an optimal deterministic one.",
        "The principled fix is the belief state: a probability distribution b(s) over which state you are actually in, updated by Bayes' rule from each observation. A POMDP over states is exactly an MDP over beliefs — which restores all the theory, at the price of a continuous, high-dimensional state space that is intractable to plan in for anything but toy problems. So deep RL approximates it: give the policy a recurrent network or a stack of recent frames, and let it learn its own compressed belief. DRQN, R2D2 and the memory in every serious partially observed agent are all doing this.",
    ),
    steps = listOf(
        StepCard(1, "Add the Observation Model", "The tuple grows from (S,A,P,R,γ) to (S,A,P,R,Ω,O,γ) — observations and their likelihoods.", 0xFF818CF8),
        StepCard(2, "Notice Aliasing", "Distinct states share an observation. A reactive policy cannot tell them apart, ever.", 0xFFEC4899),
        StepCard(3, "Maintain a Belief", "Track b(s), the posterior over states given the whole history of actions and observations.", 0xFF60A5FA),
        StepCard(4, "Update by Bayes", "Predict forward through the dynamics, multiply by the observation likelihood, renormalize.", 0xFF8B5CF6),
        StepCard(5, "Plan Over Beliefs", "The belief MDP is a proper MDP, so the theory returns — but its state space is continuous.", 0xFF10B981),
        StepCard(6, "Approximate in Practice", "Frame-stack or an RNN/transformer hidden state is a learned belief. Add stochasticity to escape aliasing.", 0xFFF59E0B),
    ),
    formulas = listOf(
        FormulaEntry("POMDP tuple", "(S, A, P, R, Ω, O, γ)", "Ω is the observation set, O(o|s′,a) the observation model."),
        FormulaEntry("Belief update", "b′(s′) ∝ O(o|s′,a) Σₛ P(s′|s,a) b(s)", "Bayes filter: predict, weight, renormalize."),
        FormulaEntry("Belief MDP", "Vπ(b) over the simplex Δ(S)", "A POMDP is an MDP whose states are beliefs."),
        FormulaEntry("Value structure", "V*(b) = maxα ⟨α, b⟩", "Piecewise-linear and convex in b — the basis of point-based solvers."),
        FormulaEntry("Complexity", "Finite-horizon POMDP is PSPACE-complete", "Why exact solutions do not scale."),
    ),
    notationKey = listOf(
        NotationEntry("o", "observation — what the agent actually receives"),
        NotationEntry("Ω", "observation space"),
        NotationEntry("O(o|s′,a)", "observation model, the sensor's likelihood"),
        NotationEntry("b(s)", "belief — posterior probability of being in s"),
        NotationEntry("aliasing", "two distinct states producing the same observation"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "The Bayes filter, exactly",
            accentColor = 0xFF6366F1,
            code = """
                import numpy as np

                def belief_update(b, a, o, P, O):
                    \"\"\"b: (n_states,) prior. Returns the posterior after taking a and seeing o.
                    P[s, a, s2] transition, O[s2, a, o] observation likelihood.\"\"\"
                    predicted = P[:, a, :].T @ b          # push the belief through the dynamics
                    weighted = O[:, a, o] * predicted     # weight by how likely o was in each state
                    total = weighted.sum()
                    if total == 0:                        # observation impossible under this belief
                        raise ValueError("inconsistent observation")
                    return weighted / total               # renormalize back to a distribution
            """.trimIndent(),
        ),
        CodeBlock(
            title = "The deep-RL approximation: let the network hold the belief",
            accentColor = 0xFF10B981,
            code = """
                import torch, torch.nn as nn

                class RecurrentQNetwork(nn.Module):
                    \"\"\"DRQN. The LSTM hidden state IS the learned belief — nothing hand-derives
                    b(s). It is carried across the episode and reset only on env reset, which is
                    why replay must store whole sequences rather than single transitions.\"\"\"
                    def __init__(self, obs_dim, n_actions, hidden=128):
                        super().__init__()
                        self.encoder = nn.Sequential(nn.Linear(obs_dim, hidden), nn.ReLU())
                        self.memory = nn.LSTM(hidden, hidden, batch_first=True)
                        self.head = nn.Linear(hidden, n_actions)

                    def forward(self, obs_seq, state=None):
                        x = self.encoder(obs_seq)
                        x, state = self.memory(x, state)
                        return self.head(x), state
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.RlGridWorld,
    applications = listOf(
        ApplicationCard("robot", 0xFF818CF8, "Robot Localization", "A robot with noisy range sensors runs exactly this belief filter; particle filters are its sampled approximation."),
        ApplicationCard("game", 0xFF60A5FA, "Imperfect-Information Games", "Poker and StarCraft hide the opponent's state, so optimal play is stochastic and memory is mandatory."),
        ApplicationCard("flask", 0xFF10B981, "Medical Treatment", "The true disease state is never observed — only tests and symptoms — so treatment planning is a POMDP over beliefs."),
    ),
    takeaways = listOf(
        "In a POMDP the observation is not the state, so it is not Markov and reactive policies fail.",
        "Aliased states force stochastic policies, even though the underlying MDP has a deterministic optimum.",
        "The belief state restores the Markov property — a POMDP is an MDP over the belief simplex.",
        "Exact belief planning is PSPACE-complete, so deep RL learns a belief instead: frame stacks or recurrent memory.",
    ),
    crossLinks = listOf(
        CrossLink("mdp", "MDP"),
        CrossLink("state_action_reward", "State, Action, Reward"),
        CrossLink("policy", "The Policy (π)"),
        CrossLink("lstm_gru", "LSTMs / GRUs"),
        CrossLink("starcraft", "StarCraft II"),
    ),
)
