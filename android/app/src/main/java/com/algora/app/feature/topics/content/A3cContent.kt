package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val a3cContent = TopicContent(
    topicId = "a3c",
    whatIsIt = listOf(
        "A3C (Asynchronous Advantage Actor-Critic) runs many worker agents in parallel, each exploring its own environment copy and asynchronously pushing gradients to a shared global network.",
        "The asynchrony itself decorrelates experience — different workers see different states — removing the need for a replay buffer.",
    ),
    steps = listOf(
        StepCard(1, "Global + Local Networks", "A shared global network; each worker keeps a local copy.", 0xFF818CF8),
        StepCard(2, "Independent Exploration", "Workers act in separate environment instances, gathering diverse experience.", 0xFF60A5FA),
        StepCard(3, "Async Gradient Push", "Each worker computes gradients and applies them to the global net, then re-syncs.", 0xFF10B981),
        StepCard(4, "Decorrelation for Free", "Diverse concurrent workers stabilize learning without a replay buffer.", 0xFFF59E0B),
    ),
    formulas = listOf(
        FormulaEntry("Update", "advantage actor-critic", "Same objective as A2C, applied asynchronously."),
        FormulaEntry("Stability source", "worker diversity", "Parallel workers decorrelate updates."),
        FormulaEntry("No replay", "on-policy, async", "Fresh on-policy data from many actors."),
    ),
    notationKey = listOf(
        NotationEntry("global network", "shared parameters all workers update"),
        NotationEntry("asynchronous", "workers update at their own pace"),
        NotationEntry("worker", "an agent + environment instance"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "A3C worker loop (sketch)",
            accentColor = 0xFF6366F1,
            code = """
                while True:
                    local.load_state_dict(global_net.state_dict())   # sync
                    traj = rollout(local, env, n_steps)
                    grads = compute_actor_critic_grads(traj)
                    apply_grads_to(global_net, grads)                # async push
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.PolicyGradientPlayer,
    applications = listOf(
        ApplicationCard("game", 0xFF818CF8, "Atari (CPU era)", "A3C reached strong Atari results using CPU workers, no GPU or replay."),
        ApplicationCard("chip", 0xFF60A5FA, "Distributed RL", "An early template for scaling RL across many parallel actors."),
        ApplicationCard("robot", 0xFF10B981, "Diverse Exploration", "Independent workers cover the state space broadly."),
    ),
    takeaways = listOf(
        "A3C parallelizes actor-critic with asynchronous workers updating a shared network.",
        "Worker diversity decorrelates experience, replacing the replay buffer.",
        "It scales well on CPUs but the asynchrony complicates reproducibility.",
        "A2C showed synchronous updates match it more simply, and often win in practice.",
    ),
    crossLinks = listOf(
        CrossLink("a2c", "A2C"),
        CrossLink("actor_critic", "Actor-Critic"),
    ),
)
