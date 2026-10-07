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

internal val a3cContent = TopicContent(
    topicId = "a3c",
    figure = Figure(
        caption = "A3C against A2C with eight workers each, as the page's lab compares them. The noise " +
            "in the gradient is the same, 0.298 for one worker falling to 0.112 for eight, because " +
            "it comes from averaging independent rollouts, not from running them out of step. What " +
            "asynchrony changes is the other two rows. Each A3C worker pushes its gradient " +
            "whenever it finishes, computed from parameters that may already be out of date when " +
            "it lands, so gradients arrive slightly stale — more so with more workers. What it " +
            "buys is throughput on many CPU cores where workers finish at different times. On a " +
            "GPU the synchronous version wins: all actors batch into one forward pass and no " +
            "gradient is ever stale, which is why A2C became the default.",
        shape = FigureShape.Grid(
            rows = listOf(
                listOf("0.112", "0.112"),
                listOf("fresh", "may be stale"),
                listOf("GPU batch", "many CPU cores"),
            ),
            rowHeaders = listOf("gradient sd, 8", "gradients", "suits"),
            colHeaders = listOf("A2C (sync)", "A3C (async)"),
            marks = listOf(
                FigureCell(1, 1, FigureTone.Warn),
                FigureCell(1, 0, FigureTone.Accent),
            ),
        ),
    ),
    whatIsIt = listOf(
        "A3C — asynchronous advantage actor-critic — came first. Each worker pulls a copy of the shared parameters, runs its own rollout, computes an advantage actor-critic gradient and pushes it back to the shared model whenever it is ready, with no waiting for the slowest worker. It trained Atari agents on CPUs without a replay buffer, because many workers in different states already decorrelate the updates.",
        "The lab separates what the parallelism does from what the asynchrony does. Averaging 8 workers' gradients cuts the noise from 0.298 to 0.112, exactly as in A2C — the variance reduction comes from averaging independent rollouts, not from running them out of step. What asynchrony adds is throughput on hardware where workers finish at different times.",
        "It also adds a cost. A worker computes its gradient from parameters that may already be out of date by the time its update lands, so gradients arrive slightly wrong, and the staleness grows with the number of workers. That is why the synchronous version won: on a GPU, batching every actor into one forward pass is faster than running them separately, and the gradients are never stale. Same algorithm, better fit for the hardware.",
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
