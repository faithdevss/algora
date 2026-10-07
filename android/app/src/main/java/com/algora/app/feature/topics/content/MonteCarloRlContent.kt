package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val monteCarloRlContent = TopicContent(
    topicId = "monte_carlo_rl",
    whatIsIt = listOf(
        "Monte Carlo methods estimate values the most direct way available: play a complete episode, compute the actual return that followed each state, and average those returns over many episodes. No transition model, no reward function, and — crucially — no bootstrapping. The target is a real observed number, not another estimate.",
        "That makes MC unbiased. The sample return is by definition a draw from the distribution whose mean is Vπ(s), so the estimate converges to the true value with no systematic error at all, and it stays correct even when the state is not really Markov — since it never relies on the next state summarizing anything. The price is variance: a return sums dozens of random rewards and random transitions, so individual samples scatter widely and many episodes are needed to average that noise down.",
        "The structural limitation is the word \"complete\". MC cannot update until an episode terminates, so it is unusable on continuing tasks with no end, and slow on very long ones. It also needs every state visited to be estimated at all, which is why MC control uses exploring starts or an ε-soft policy. Its enduring role in modern RL is as the unbiased end of a spectrum: REINFORCE is Monte Carlo policy-gradient, and GAE's λ parameter dials continuously between MC and one-step TD.",
    ),
    steps = listOf(
        StepCard(1, "Generate a Full Episode", "Follow the policy from a start state until termination, recording states and rewards.", 0xFF6366F1),
        StepCard(2, "Compute Returns Backwards", "Sweep from the end: G ← r + γG. One pass, O(T) instead of O(T²).", 0xFF818CF8),
        StepCard(3, "Credit Each State", "First-visit counts only the first occurrence per episode; every-visit counts them all.", 0xFF60A5FA),
        StepCard(4, "Average the Returns", "V(s) is the running mean of all returns observed from s.", 0xFF10B981),
        StepCard(5, "Guarantee Coverage", "Use exploring starts or an ε-soft policy, or unvisited states are never estimated.", 0xFFF59E0B),
        StepCard(6, "Accept the Trade", "Unbiased and Markov-free, at the cost of high variance and needing episodes to end.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Return", "Gₜ = rₜ₊₁ + γrₜ₊₂ + … + γ^(T−t−1)r_T", "Actual observed, never bootstrapped."),
        FormulaEntry("Estimate", "V(s) = (1/N(s)) Σᵢ Gᵢ(s)", "Sample mean of returns from s."),
        FormulaEntry("Incremental form", "V(s) ← V(s) + (1/N(s))[G − V(s)]", "Same mean, constant memory."),
        FormulaEntry("Constant-α MC", "V(s) ← V(s) + α[G − V(s)]", "Tracks a changing target instead of averaging all history."),
        FormulaEntry("Error", "unbiased; variance ∝ episode length", "The whole trade in one line."),
    ),
    notationKey = listOf(
        NotationEntry("Gₜ", "return actually observed from time t"),
        NotationEntry("N(s)", "number of returns averaged into V(s)"),
        NotationEntry("first-visit", "only the first occurrence of s per episode counts"),
        NotationEntry("every-visit", "all occurrences count; also converges, with correlated samples"),
        NotationEntry("exploring starts", "randomizing the initial state so every state is reachable"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "First-visit Monte Carlo prediction",
            accentColor = 0xFF6366F1,
            code = """
                from collections import defaultdict

                def mc_prediction(policy, env, episodes=10_000, gamma=0.9):
                    returns_sum = defaultdict(float)
                    counts = defaultdict(int)
                    V = defaultdict(float)

                    for _ in range(episodes):
                        # 1. Play out a COMPLETE episode. Nothing can be learned before it ends.
                        trajectory, s, done = [], env.reset(), False
                        while not done:
                            a = policy(s)
                            s2, r, done = env.step(a)
                            trajectory.append((s, r))
                            s = s2

                        # 2. Walk backwards so each G is built in O(1) from the one after it.
                        first_idx = {}
                        for t, (s, _) in enumerate(trajectory):
                            first_idx.setdefault(s, t)   # earliest time each state appears
                        G = 0.0
                        for t in reversed(range(len(trajectory))):
                            s, r = trajectory[t]
                            G = r + gamma * G
                            if first_idx[s] == t:        # first-visit: only the earliest occurrence
                                returns_sum[s] += G
                                counts[s] += 1
                                V[s] = returns_sum[s] / counts[s]
                    return V
            """.trimIndent(),
        ),
        CodeBlock(
            title = "Why the variance is the real cost",
            accentColor = 0xFFEC4899,
            code = """
                # Two sources of noise compound over an episode: which actions the policy
                # sampled, and where the environment sent it. A 100-step episode sums 100
                # random rewards, so a single return can sit far from its own mean.
                returns = [rollout_return(env, policy) for _ in range(1000)]
                print(np.mean(returns), np.std(returns))   # std is often larger than the mean

                # TD(0) replaces the tail of that sum with V(s'), trading the bias of an
                # imperfect estimate for a large cut in variance. GAE's lambda sweeps the
                # whole spectrum between the two.
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.RlGridWorld,
    applications = listOf(
        ApplicationCard("game", 0xFF6366F1, "Blackjack", "Sutton & Barto's canonical MC example: episodes are short, terminal, and the model is awkward to write down."),
        ApplicationCard("trend", 0xFF818CF8, "REINFORCE", "The Monte Carlo policy gradient — full-episode returns, unbiased, and famously high-variance."),
        ApplicationCard("chip", 0xFF10B981, "MCTS Rollouts", "Tree search evaluates a leaf by playing random games out to the end — the same estimator, used inside a planner."),
    ),
    takeaways = listOf(
        "MC learns from complete episodes and averages actual returns — no model, no bootstrapping.",
        "It is unbiased and works even when the state is not truly Markov, because it never leans on a successor estimate.",
        "The cost is variance, and the hard limit is that episodes must terminate.",
        "It is the unbiased end of the spectrum that TD and GAE interpolate along.",
    ),
    crossLinks = listOf(
        CrossLink("td_learning", "Temporal Difference (TD) Learning"),
        CrossLink("reinforce", "REINFORCE"),
        CrossLink("gae", "GAE"),
        CrossLink("mcts", "MCTS"),
        CrossLink("monte_carlo_method", "Monte Carlo Method (DSA)"),
    ),
)
