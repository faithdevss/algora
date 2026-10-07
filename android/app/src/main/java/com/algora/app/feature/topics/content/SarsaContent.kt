package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.Figure
import com.algora.app.core.data.model.FigureCell
import com.algora.app.core.data.model.FigureShape
import com.algora.app.core.data.model.FigureTone
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val sarsaContent = TopicContent(
    topicId = "sarsa",
    figure = Figure(
        caption = "The page's cliff lab after 400 training episodes at ε = 0.1, both greedy routes " +
            "on one 4×6 grid. Q = Q-learning's route, S = SARSA's. Q-learning walks the row directly " +
            "above the cliff — 7 steps, the shortest there is — because its target assumes every " +
            "later move is greedy. SARSA climbs to the top row and takes 11, because its target " +
            "includes the 10% of moves that are random, and from the edge a random ↓ costs −100. " +
            "The cliff shows up in SARSA's values first: the edge cells it avoids are worth −11, " +
            "−14 and −15 to it against −6, −5 and −4 to Q-learning. And the detour pays while " +
            "exploration is still on — over the last 100 training episodes SARSA averages a return " +
            "of −21 against Q-learning's −26, because Q-learning keeps stepping off the edge it " +
            "believes is safe.",
        shape = FigureShape.Grid(
            rows = listOf(
                listOf("S", "S", "S", "S", "·", "·"),
                listOf("S", "·", "·", "S", "S", "S"),
                listOf("Q S", "Q", "Q", "Q", "Q", "Q S"),
                listOf("start", "cliff", "cliff", "cliff", "cliff", "goal"),
            ),
            marks = listOf(
                FigureCell(0, 0, FigureTone.Accent), FigureCell(0, 1, FigureTone.Accent),
                FigureCell(0, 2, FigureTone.Accent), FigureCell(0, 3, FigureTone.Accent),
                FigureCell(1, 0, FigureTone.Accent), FigureCell(1, 3, FigureTone.Accent),
                FigureCell(1, 4, FigureTone.Accent), FigureCell(1, 5, FigureTone.Accent),
                FigureCell(2, 1, FigureTone.Primary), FigureCell(2, 2, FigureTone.Primary),
                FigureCell(2, 3, FigureTone.Primary), FigureCell(2, 4, FigureTone.Primary),
                FigureCell(3, 1, FigureTone.Warn), FigureCell(3, 2, FigureTone.Warn),
                FigureCell(3, 3, FigureTone.Warn), FigureCell(3, 4, FigureTone.Warn),
            ),
        ),
    ),
    whatIsIt = listOf(
        "SARSA is TD learning applied to Q, named after the five things one update touches: sₜ, aₜ, rₜ₊₁, sₜ₊₁, aₜ₊₁. It moves Q(s,a) toward r + γQ(s′,a′), where a′ is the action the agent is genuinely going to take next — sampled from the same ε-greedy policy it is following.",
        "That last clause is the entire difference from Q-learning, which uses maxₐ′ Q(s′,a′) instead. One term, and it changes what is being learned. SARSA is on-policy: it estimates the value of the policy actually running, exploration included, so the cost of the occasional random action is baked into its numbers. Q-learning is off-policy: it estimates the value of the greedy policy regardless of what generated the data, so exploration is invisible to it.",
        "On a cliff-walking problem the consequence is stark and easy to reproduce. Q-learning learns the optimal path — which hugs the cliff edge, because with no exploration that is genuinely shortest — and then keeps falling off, because in reality it does explore. SARSA sees the −100 leak back into the cells beside the cliff through the exploratory actions that occasionally land there, and takes the longer, safer detour. Q-learning's policy is optimal for an agent that never slips; SARSA's is better for the agent that actually exists. Neither is wrong, and as ε → 0 the two answers converge.",
    ),
    steps = listOf(
        StepCard(1, "Choose a From s", "Pick an action ε-greedily and commit to it before the update.", 0xFF6366F1),
        StepCard(2, "Act and Observe", "Take a, receive r, land in s′.", 0xFF818CF8),
        StepCard(3, "Choose a′ From s′", "Sample the next action with the same policy — including its randomness.", 0xFF60A5FA),
        StepCard(4, "Bootstrap Off a′", "Target is r + γQ(s′,a′): the action to be taken, not the best one available.", 0xFF10B981),
        StepCard(5, "Update and Shift", "Q(s,a) += α·δ, then carry a′ into the next iteration — it is not re-sampled.", 0xFFF59E0B),
        StepCard(6, "Decay ε to Converge", "Convergence to Q* requires exploration that vanishes but visits everything infinitely often.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("SARSA update", "Q(s,a) ← Q(s,a) + α[r + γQ(s′,a′) − Q(s,a)]", "a′ is sampled from the behaviour policy."),
        FormulaEntry("Q-learning update", "Q(s,a) ← Q(s,a) + α[r + γ maxₐ′ Q(s′,a′) − Q(s,a)]", "The one-term difference."),
        FormulaEntry("What SARSA learns", "Qπ for the ε-greedy π", "Value of the policy actually being run."),
        FormulaEntry("What Q-learning learns", "Q* for the greedy policy", "Value of a policy it does not follow."),
        FormulaEntry("Expected SARSA", "target = r + γ Σₐ′ π(a′|s′)Q(s′,a′)", "Averages over a′ instead of sampling it — less variance."),
        FormulaEntry("GLIE", "ε → 0, all pairs visited ∞ often", "The condition for convergence to Q*."),
    ),
    notationKey = listOf(
        NotationEntry("on-policy", "learning about the policy generating the data"),
        NotationEntry("off-policy", "learning about a different policy than the one generating the data"),
        NotationEntry("a′", "the next action, actually sampled and actually taken"),
        NotationEntry("behaviour policy", "the policy collecting experience"),
        NotationEntry("GLIE", "Greedy in the Limit with Infinite Exploration"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "SARSA and Q-learning, one line apart",
            accentColor = 0xFF6366F1,
            code = """
                def sarsa(env, episodes=4000, alpha=0.5, gamma=1.0, eps=0.1):
                    Q = np.zeros((env.n_states, env.n_actions))

                    def pick(s):
                        if np.random.rand() < eps:
                            return np.random.randint(env.n_actions)
                        return int(Q[s].argmax())

                    for _ in range(episodes):
                        s = env.reset()
                        a = pick(s)                       # chosen BEFORE the loop
                        done = False
                        while not done:
                            s2, r, done = env.step(a)
                            a2 = pick(s2)                 # the action actually to be taken
                            # SARSA:      bootstrap off Q[s2, a2]  -- on-policy
                            # Q-learning: bootstrap off Q[s2].max() -- off-policy
                            target = r + (0.0 if done else gamma * Q[s2, a2])
                            Q[s, a] += alpha * (target - Q[s, a])
                            s, a = s2, a2                 # a2 is CARRIED, not re-sampled
                    return Q
            """.trimIndent(),
        ),
        CodeBlock(
            title = "Expected SARSA: keep on-policy, drop the sampling noise",
            accentColor = 0xFF10B981,
            code = """
                # Sampling a' adds variance for no benefit when pi is known explicitly.
                # Averaging over it instead is still on-policy, but strictly lower variance —
                # and it often outperforms both SARSA and Q-learning.
                def expected_sarsa_target(Q, s2, r, done, gamma, eps):
                    if done:
                        return r
                    n = len(Q[s2])
                    probs = np.full(n, eps / n)
                    probs[Q[s2].argmax()] += 1.0 - eps        # the epsilon-greedy distribution
                    return r + gamma * float(probs @ Q[s2])

                # Note: set eps = 0 in that distribution and the expectation collapses to a
                # max — Expected SARSA becomes Q-learning exactly.
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.RlGridWorld,
    applications = listOf(
        ApplicationCard("robot", 0xFF6366F1, "Safety-Critical Control", "When exploration can damage real hardware, learning the value of the policy you are actually running is the safer choice."),
        ApplicationCard("map", 0xFF818CF8, "Cliff Walking", "Sutton & Barto's Example 6.6, and still the clearest demonstration that on-policy and off-policy answer different questions."),
        ApplicationCard("chart", 0xFF10B981, "Online Systems", "A live recommender's exploration has real cost, so on-policy value estimates reflect what it will actually earn."),
    ),
    takeaways = listOf(
        "SARSA bootstraps off the action it will actually take; Q-learning bootstraps off the best one.",
        "That single term makes SARSA on-policy: it prices in the cost of its own exploration.",
        "On a cliff, Q-learning learns the optimal-but-risky path and keeps falling; SARSA takes the safer detour.",
        "Expected SARSA averages over a′ instead of sampling it, and collapses to Q-learning when ε = 0.",
    ),
    crossLinks = listOf(
        CrossLink("q_learning", "Q-Learning (off-policy)"),
        CrossLink("td_learning", "Temporal Difference (TD) Learning"),
        CrossLink("exploration_exploitation", "Exploration vs Exploitation"),
        CrossLink("q_function", "Q-Function (Q)"),
    ),
)
