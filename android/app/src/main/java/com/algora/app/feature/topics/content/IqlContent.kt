package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val iqlContent = TopicContent(
    topicId = "iql",
    whatIsIt = listOf(
        "Independent Q-Learning (IQL) is the simplest multi-agent approach: each agent runs its own Q-learning, treating the other agents as part of the environment.",
        "It's easy and scalable, but the environment looks non-stationary to each learner — as others change their policies, the same action yields different outcomes — which can destabilize learning.",
    ),
    steps = listOf(
        StepCard(1, "One Learner per Agent", "Each agent maintains its own independent Q-function.", 0xFF818CF8),
        StepCard(2, "Treat Others as Environment", "From one agent's view, other agents' behavior is just environment dynamics.", 0xFF60A5FA),
        StepCard(3, "Learn Locally", "Each agent updates its Q-values from its own observations and rewards.", 0xFF10B981),
        StepCard(4, "Face Non-Stationarity", "Because everyone is learning at once, each agent's world keeps shifting.", 0xFFF59E0B),
    ),
    formulas = listOf(
        FormulaEntry("Per-agent update", "Qᵢ(s,aᵢ) += α[r + γ maxQᵢ − Qᵢ]", "Standard Q-learning, run independently."),
        FormulaEntry("Problem", "non-stationarity", "Others' changing policies break the MDP assumption."),
        FormulaEntry("Upside", "scales linearly", "No joint action space to model."),
    ),
    notationKey = listOf(
        NotationEntry("Qᵢ", "agent i's independent Q-function"),
        NotationEntry("non-stationary", "environment changes as co-agents learn"),
        NotationEntry("decentralized", "each agent learns and acts on its own"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Independent Q-learners",
            accentColor = 0xFF6366F1,
            code = """
                # Each agent has its own Q-table; others are treated as environment.
                for i in range(num_agents):
                    a = agents[i].act(obs[i])
                    r, next_obs = env.step_agent(i, a)
                    agents[i].q_update(obs[i], a, r, next_obs[i])
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.MultiAgentPlayer,
    applications = listOf(
        ApplicationCard("users", 0xFF818CF8, "Baseline Multi-Agent RL", "The simplest starting point for cooperative or competitive MARL."),
        ApplicationCard("game", 0xFF60A5FA, "Many-Agent Systems", "Scales to many agents where a joint model would be intractable."),
        ApplicationCard("robot", 0xFF10B981, "Decentralized Control", "Each robot or unit learns from purely local information."),
    ),
    takeaways = listOf(
        "IQL runs independent single-agent Q-learning per agent.",
        "It scales well but suffers from a non-stationary environment.",
        "It's the natural multi-agent baseline to compare against.",
        "Value-decomposition methods (VDN, QMIX) address its coordination weakness.",
    ),
    crossLinks = listOf(
        CrossLink("q_learning", "Q-Learning (off-policy)"),
        CrossLink("vdn", "VDN"),
    ),
)
