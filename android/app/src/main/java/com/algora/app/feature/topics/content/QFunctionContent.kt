package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val qFunctionContent = TopicContent(
    topicId = "q_function",
    whatIsIt = listOf(
        "The action-value function Qπ(s,a) is the expected return from taking action a in state s and following π afterwards. Where V scores a situation, Q scores a decision — one number per action, per state.",
        "That extra index is what makes Q the workhorse of model-free control. To act well from V you must ask \"where would each action land me?\", which requires the transition model. From Q you simply take the largest of the numbers already sitting in front of you. No model, no lookahead: argmaxₐ Q(s,a) is the greedy policy, computable directly from experience. This is the single reason Q-learning, SARSA, DQN and the entire Rainbow lineage are built on Q rather than V.",
        "The cost is that Q is |A| times larger than V, and the max is what makes it fragile. Every Q-learning target contains maxₐ Q(s′,a), and a max over noisy estimates is biased upward — noise in the wrong direction gets selected. That systematic overestimation is not a bug in an implementation; it is structural, and Double DQN exists specifically to break it. The same max is also why Q-learning does not extend to continuous actions without help: you cannot enumerate the arguments to maximize over.",
    ),
    steps = listOf(
        StepCard(1, "Tabulate by State and Action", "Q is a |S|×|A| table, or a network emitting |A| outputs from one state.", 0xFF818CF8),
        StepCard(2, "Observe a Transition", "Collect (s, a, r, s′) by acting — no transition model is needed.", 0xFF60A5FA),
        StepCard(3, "Form the Target", "r + γ·maxₐ′ Q(s′,a′): the reward you got plus the best you believe follows.", 0xFF8B5CF6),
        StepCard(4, "Move Toward It", "Nudge Q(s,a) by α times the difference between target and estimate — the TD error.", 0xFF10B981),
        StepCard(5, "Act Greedily", "The policy falls straight out as argmaxₐ Q(s,a). No model, no search.", 0xFFF59E0B),
        StepCard(6, "Mind the Max", "The same max that gives you a free policy also biases the estimate upward. Budget for that.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Definition", "Qπ(s,a) = Eπ[Gₜ | sₜ=s, aₜ=a]", "Return after committing to a, then following π."),
        FormulaEntry("Bellman expectation", "Qπ(s,a) = E[r + γ Σₐ′ π(a′|s′)Qπ(s′,a′)]", "The on-policy form — SARSA's target."),
        FormulaEntry("Bellman optimality", "Q*(s,a) = E[r + γ maxₐ′ Q*(s′,a′)]", "The off-policy form — Q-learning's target."),
        FormulaEntry("Greedy policy", "π(s) = argmaxₐ Q(s,a)", "Control without a model, for free."),
        FormulaEntry("Advantage", "A(s,a) = Q(s,a) − V(s)", "How much better than average this action is — the Dueling split."),
    ),
    notationKey = listOf(
        NotationEntry("Q(s,a)", "action value — expected return of taking a in s"),
        NotationEntry("Q*", "optimal action value"),
        NotationEntry("α", "learning rate for the incremental update"),
        NotationEntry("TD error", "δ = target − Q(s,a), the correction signal"),
        NotationEntry("A(s,a)", "advantage; zero on average under the policy"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Tabular Q-learning update",
            accentColor = 0xFF6366F1,
            code = """
                import numpy as np

                Q = np.zeros((n_states, n_actions))

                def update(s, a, r, s_next, done, alpha=0.5, gamma=0.9):
                    # The max is over the NEXT state's actions, and it is taken regardless of
                    # what the agent actually does next. That is what makes this off-policy:
                    # it learns the greedy policy's values while behaving epsilon-greedily.
                    best_next = 0.0 if done else Q[s_next].max()
                    td_target = r + gamma * best_next
                    Q[s, a] += alpha * (td_target - Q[s, a])
                    return td_target - Q[s, a]                    # the TD error

                def act(s, epsilon):
                    if np.random.rand() < epsilon:
                        return np.random.randint(n_actions)
                    return int(Q[s].argmax())                     # policy, straight from Q
            """.trimIndent(),
        ),
        CodeBlock(
            title = "Why the max overestimates, and the fix",
            accentColor = 0xFFEC4899,
            code = """
                # Suppose every true Q(s', a) is exactly 0, but our estimates carry noise.
                # E[max of noisy zeros] > 0 — the max systematically selects the luckiest error.
                estimates = true_values + np.random.normal(0, 1, size=n_actions)
                estimates.max()          # positive on average, even though the truth is 0

                # Double Q-learning breaks the coupling: one network picks the action,
                # the other scores it, so a lucky error in the picker is not also the scorer's.
                a_star  = Q_online(s_next).argmax(dim=1)
                target  = r + gamma * Q_target(s_next).gather(1, a_star.unsqueeze(1)) * (1 - done)
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.RlGridWorld,
    applications = listOf(
        ApplicationCard("game", 0xFF818CF8, "Atari From Pixels", "DQN emits one Q value per joystick action from a single forward pass — the whole policy in one network call."),
        ApplicationCard("network", 0xFF60A5FA, "Dueling Architectures", "Splitting Q into V + A lets the net learn a state is bad without evaluating every action separately."),
        ApplicationCard("chip", 0xFF10B981, "Continuous Control", "argmax over a continuous action space is intractable, so DDPG trains a separate actor to produce it instead."),
    ),
    takeaways = listOf(
        "Q scores decisions, not situations — which is why it yields a policy without a model.",
        "The greedy policy argmaxₐ Q(s,a) is the payoff that makes model-free control possible.",
        "maxₐ Q(s′,a) biases estimates upward by construction; Double DQN decouples selection from evaluation to fix it.",
        "Q does not extend to continuous actions unaided — that gap is what DDPG, TD3 and SAC fill.",
    ),
    crossLinks = listOf(
        CrossLink("value_function", "Value Function (V)"),
        CrossLink("q_learning", "Q-Learning (off-policy)"),
        CrossLink("double_dqn", "Double DQN"),
        CrossLink("dueling_dqn", "Dueling DQN"),
    ),
)
