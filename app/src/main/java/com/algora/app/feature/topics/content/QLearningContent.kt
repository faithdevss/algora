package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val qLearningContent = TopicContent(
    topicId = "q_learning",
    whatIsIt = listOf(
        "Q-learning learns the value of taking each action in each state — the Q-function — directly from experience, without knowing the environment's dynamics.",
        "It's off-policy: it learns the optimal action-values while behaving with an exploratory policy, bootstrapping each estimate from the best next action.",
    ),
    steps = listOf(
        StepCard(1, "Initialize Q", "Start a table (or estimate) Q(s,a) arbitrarily.", 0xFF818CF8),
        StepCard(2, "Act & Observe", "Choose an action (often ε-greedy), then see the reward and next state.", 0xFF60A5FA),
        StepCard(3, "TD Update", "Move Q(s,a) toward reward plus the discounted best next Q-value.", 0xFF10B981),
        StepCard(4, "Repeat to Convergence", "With enough exploration, Q converges to the optimal action-values.", 0xFFF59E0B),
    ),
    formulas = listOf(
        FormulaEntry("Update", "Q(s,a) += α[r + γ·maxₐ′Q(s′,a′) − Q(s,a)]", "TD error toward the greedy target."),
        FormulaEntry("Off-policy target", "maxₐ′ Q(s′,a′)", "Learns optimal values regardless of behavior."),
        FormulaEntry("TD error", "r + γ·maxQ(s′,·) − Q(s,a)", "How wrong the current estimate is."),
    ),
    notationKey = listOf(
        NotationEntry("Q(s,a)", "expected return of action a in state s"),
        NotationEntry("α", "learning rate"),
        NotationEntry("off-policy", "learns optimal policy while behaving differently"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Tabular Q-learning update",
            accentColor = 0xFF6366F1,
            code = """
                # After taking action a in state s, observing reward r and next state s2:
                best_next = max(Q[s2][a2] for a2 in actions)
                td_target = r + gamma * best_next
                Q[s][a] += alpha * (td_target - Q[s][a])
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.RlGridWorld,
    applications = listOf(
        ApplicationCard("game", 0xFF818CF8, "Game Playing", "Learns optimal play in small games and gridworlds from trial and error."),
        ApplicationCard("robot", 0xFF60A5FA, "Control Tasks", "Discrete-action control where dynamics are unknown."),
        ApplicationCard("chart", 0xFF10B981, "Foundation for DQN", "Deep Q-Networks extend it to high-dimensional states with a neural net."),
    ),
    takeaways = listOf(
        "Q-learning learns optimal action-values from experience, model-free.",
        "It's off-policy: the max in the target learns the best policy regardless of behavior.",
        "It needs sufficient exploration (e.g. ε-greedy) to converge.",
        "Tabular Q-learning doesn't scale — DQN swaps the table for a neural network.",
    ),
    crossLinks = listOf(
        CrossLink("mdp", "MDP"),
        CrossLink("dqn", "DQN"),
    ),
)
