package com.algora.app.feature.topics

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.algora.app.core.ui.theme.SimColors
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.pow

// ── RL training player ───────────────────────────────────────────────────────
// The DQN family. Each frame's numbers come from an experiment that is actually run when the frames
// are built — tabular Q-learning on a small chain MDP, Double Q-learning on the classic
// overestimation MDP, replay buffers sampled uniformly or by TD error, and so on. Nothing here is a
// hand-drawn "what it would look like" curve; if a claim in a status line is wrong, the plot next
// to it is wrong too, which is the point.
//
// Render parts: labelled curve plots, signed bar rows, and a state-chain strip for the MDP itself.

private class RlCurve(val label: String, val values: List<Float>, val color: Color)

private class RlPlot(
    val label: String,
    val curves: List<RlCurve>,
    val yRange: ClosedFloatingPointRange<Float>,
    val xLabel: String,
)

private class RlBar(val label: String, val values: List<Float>, val color: Color, val captions: List<String> = emptyList())

private class RlTrainFrame(
    val status: String,
    val plot: RlPlot? = null,
    val bars: List<RlBar> = emptyList(),
    val chain: List<Float>? = null,
    val chainLabel: String? = null,
    val readout: String? = null,
)

private class RlTrainConfig(
    val intro: String,
    val legend: List<Pair<Color, String>>,
    val build: () -> List<RlTrainFrame>,
)

private val BaselineColor = SimColors.Grey
private val ImprovedColor = SimColors.Green
private val WarnColor = Color(0xFFEC4899)
private val AccentColor = SimColors.Blue
private val HighlightColor = Color(0xFF7C3AED)

/** Deterministic LCG — every launch has to reproduce the numbers quoted in the status lines. */
private class RlRng(private var state: Int) {
    fun next(): Float {
        state = state * 1103515245 + 12345
        return ((state ushr 16) and 0x7fff) / 32767f
    }

    fun nextInt(bound: Int) = (next() * bound).toInt().coerceIn(0, bound - 1)
}

// ── The chain environment ────────────────────────────────────────────────────
// States 0..5, actions 0 = left, 1 = right. Reward 1 on reaching state 5, 0 everywhere else. The
// only way to learn anything is to stumble into the goal, so credit has to propagate back six
// states — which is exactly what makes replay and prioritisation visible.

private const val CHAIN_STATES = 6
private const val GOAL = CHAIN_STATES - 1
private const val GAMMA = 0.95f

private class Transition(val state: Int, val action: Int, val reward: Float, val next: Int, val done: Boolean)

private fun stepChain(state: Int, action: Int): Transition {
    val next = if (action == 1) (state + 1).coerceAtMost(GOAL) else (state - 1).coerceAtLeast(0)
    val done = next == GOAL
    return Transition(state, action, if (done) 1f else 0f, next, done)
}

/** Optimal action values, available in closed form because the chain is deterministic. */
private val optimalQ: List<Float> = (0 until CHAIN_STATES).map { s -> GAMMA.pow(GOAL - s - 1) }

private fun qError(q: Array<FloatArray>): Float =
    (0 until GOAL).map { abs(q[it][1] - optimalQ[it]) }.average().toFloat()

private class ChainResult(val error: List<Float>, val q: Array<FloatArray>, val visits: IntArray)

/**
 * One Q-learning run over the chain. [replay] reuses a buffer of past transitions, [prioritised]
 * samples that buffer by |TD error| instead of uniformly, and [targetLag] freezes the bootstrap
 * target for that many updates.
 */
private fun runChain(
    seed: Int,
    episodes: Int = 60,
    alpha: Float = 0.5f,
    epsilon: Float = 0.3f,
    replay: Boolean = false,
    prioritised: Boolean = false,
    replaysPerStep: Int = 4,
): ChainResult {
    val rng = RlRng(seed)
    val q = Array(CHAIN_STATES) { FloatArray(2) }
    val visits = IntArray(CHAIN_STATES)
    val buffer = mutableListOf<Transition>()
    val error = mutableListOf<Float>()

    fun update(t: Transition) {
        val target = t.reward + if (t.done) 0f else GAMMA * max(q[t.next][0], q[t.next][1])
        q[t.state][t.action] += alpha * (target - q[t.state][t.action])
    }

    fun tdError(t: Transition): Float {
        val target = t.reward + if (t.done) 0f else GAMMA * max(q[t.next][0], q[t.next][1])
        return abs(target - q[t.state][t.action])
    }

    repeat(episodes) {
        var state = 0
        var steps = 0
        while (steps < 40) {
            steps++
            visits[state]++
            val action = if (rng.next() < epsilon) rng.nextInt(2)
            else if (q[state][1] >= q[state][0]) 1 else 0
            val transition = stepChain(state, action)
            update(transition)
            buffer += transition
            if (buffer.size > 200) buffer.removeAt(0)

            if (replay) {
                repeat(replaysPerStep) {
                    val sample = if (prioritised) {
                        // Rank-free proportional sampling: draw a few candidates and keep the one
                        // with the largest TD error. Same effect, no sum-tree needed.
                        (0 until 4).map { buffer[rng.nextInt(buffer.size)] }.maxByOrNull { tdError(it) }!!
                    } else {
                        buffer[rng.nextInt(buffer.size)]
                    }
                    update(sample)
                }
            }

            state = transition.next
            if (transition.done) break
        }
        error += qError(q)
    }
    return ChainResult(error, q, visits)
}

private fun episodesToConverge(error: List<Float>, threshold: Float = 0.05f): Int =
    error.indexOfFirst { it < threshold }.let { if (it < 0) error.size else it + 1 }

// ── Experience replay ────────────────────────────────────────────────────────

private fun replayFrames(): List<RlTrainFrame> {
    val online = runChain(seed = 5, replay = false)
    val withReplay = runChain(seed = 5, replay = true)
    val frames = mutableListOf<RlTrainFrame>()

    frames += RlTrainFrame(
        status = "A six-state chain: reward 1 only at the far end, nothing anywhere else. Value has to travel back " +
            "six states from a single lucky arrival, so how transitions are reused is the whole story.",
        chain = optimalQ,
        chainLabel = "optimal Q(s, right) — what the agent has to learn",
    )
    frames += RlTrainFrame(
        status = "Online learning uses each transition once, in the order it happened. Consecutive updates are " +
            "strongly correlated — the agent sees the same short stretch of the chain over and over within an episode.",
        plot = RlPlot("mean |Q − Q*|", listOf(RlCurve("online", online.error, BaselineColor)), 0f..0.6f, "episode"),
        readout = "error after ${online.error.size} episodes: ${"%.3f".format(online.error.last())}",
    )
    frames += RlTrainFrame(
        status = "A replay buffer stores transitions and re-samples them at random. Each experience is used several " +
            "times, and consecutive updates no longer come from the same part of the chain.",
        plot = RlPlot(
            "mean |Q − Q*|",
            listOf(
                RlCurve("online", online.error, BaselineColor),
                RlCurve("replay ×4", withReplay.error, ImprovedColor),
            ),
            0f..0.6f, "episode",
        ),
        readout = "final error ${"%.3f".format(online.error.last())} → ${"%.3f".format(withReplay.error.last())}",
    )
    frames += RlTrainFrame(
        status = "Below 0.05 mean error takes ${episodesToConverge(online.error)} episodes online and " +
            "${episodesToConverge(withReplay.error)} with replay. The environment interactions are identical — only " +
            "the number of gradient steps per interaction changed, which is why replay is described as sample efficiency.",
        bars = listOf(
            RlBar(
                "episodes to mean error < 0.05",
                listOf(episodesToConverge(online.error).toFloat(), episodesToConverge(withReplay.error).toFloat()),
                AccentColor,
                listOf("online", "replay"),
            ),
        ),
        readout = "same experience, ${4}× the updates",
    )
    return frames
}

// ── Target networks ──────────────────────────────────────────────────────────

/** Q-learning on the chain where the bootstrap value comes from a copy refreshed every [lag] updates. */
private fun runChainWithTarget(seed: Int, lag: Int, episodes: Int = 60, alpha: Float = 0.5f, epsilon: Float = 0.3f): List<Float> {
    val rng = RlRng(seed)
    val q = Array(CHAIN_STATES) { FloatArray(2) }
    var frozen = Array(CHAIN_STATES) { q[it].copyOf() }
    val error = mutableListOf<Float>()
    var updates = 0

    repeat(episodes) {
        var state = 0
        var steps = 0
        while (steps < 40) {
            steps++
            val action = if (rng.next() < epsilon) rng.nextInt(2)
            else if (q[state][1] >= q[state][0]) 1 else 0
            val t = stepChain(state, action)
            val target = t.reward + if (t.done) 0f else GAMMA * max(frozen[t.next][0], frozen[t.next][1])
            q[t.state][t.action] += alpha * (target - q[t.state][t.action])
            updates++
            if (updates % max(lag, 1) == 0) frozen = Array(CHAIN_STATES) { q[it].copyOf() }
            state = t.next
            if (t.done) break
        }
        error += qError(q)
    }
    return error
}

private fun targetNetworkFrames(): List<RlTrainFrame> {
    val noTarget = runChainWithTarget(seed = 7, lag = 1)
    val lag20 = runChainWithTarget(seed = 7, lag = 20)
    val lag50 = runChainWithTarget(seed = 7, lag = 50)

    // What a shared approximator does to its own targets: one weight covers the whole chain, so a
    // single update at one state moves the predicted value of every other state too.
    val features = (0 until CHAIN_STATES).map { (it + 1) / 5f }
    var w = 0f
    val before = features.map { w * it }
    w += 0.5f * (1f - w * features[4]) * features[4]
    val after = features.map { w * it }
    val shifts = features.indices.map { after[it] - before[it] }

    val frames = mutableListOf<RlTrainFrame>()

    frames += RlTrainFrame(
        status = "Bootstrapping means an update's target is computed from the same estimates being updated: " +
            "Q(s,a) ← r + γ·max Q(s′,·). Nothing else in supervised learning works this way — the labels move.",
        chain = optimalQ,
        chainLabel = "Q*(s, right) on the chain",
    )
    frames += RlTrainFrame(
        status = "First, the uncomfortable measurement: on this tabular chain a target network is pure cost. " +
            "Refreshing every update converges in ${episodesToConverge(noTarget)} episodes, freezing for 20 takes " +
            "${episodesToConverge(lag20)}, and freezing for 50 takes ${episodesToConverge(lag50)}. Stale targets are " +
            "slower targets.",
        plot = RlPlot(
            "mean |Q − Q*|",
            listOf(
                RlCurve("no target net", noTarget, ImprovedColor),
                RlCurve("refresh every 20", lag20, AccentColor),
                RlCurve("refresh every 50", lag50, WarnColor),
            ),
            0f..0.6f, "episode",
        ),
        readout = "${episodesToConverge(noTarget)} → ${episodesToConverge(lag20)} → ${episodesToConverge(lag50)} episodes",
    )
    frames += RlTrainFrame(
        status = "So why does DQN need one? Because a table updates one cell while a network updates a shared " +
            "function. Give this chain a single shared weight and one update at s4 moves the predicted value of " +
            "every state at once — including the state whose value that update was aiming at.",
        bars = listOf(
            RlBar("change in Q(s) after one update at s4", shifts, WarnColor, (0 until CHAIN_STATES).map { "s$it" }),
        ),
        readout = "one update, ${shifts.count { abs(it) > 1e-4f }} state values moved",
    )
    frames += RlTrainFrame(
        status = "That is the feedback loop a target network cuts: freeze a copy of the weights, compute every " +
            "target from the copy, and each batch becomes ordinary regression against fixed labels. It buys nothing " +
            "on a table, and it is what keeps a deep Q-network from chasing its own tail on Atari.",
        bars = listOf(
            RlBar(
                "episodes to converge, tabular chain",
                listOf(episodesToConverge(noTarget).toFloat(), episodesToConverge(lag20).toFloat(), episodesToConverge(lag50).toFloat()),
                AccentColor,
                listOf("no target", "lag 20", "lag 50"),
            ),
        ),
        readout = "an empirical stabiliser, not a convergence guarantee",
    )
    return frames
}

// ── Double DQN ───────────────────────────────────────────────────────────────

private class BiasResult(val single: List<Float>, val double: List<Float>, val leftShare: Pair<Float, Float>)

/**
 * The standard overestimation MDP: state A offers a certain 0 (right, ending the episode) or a move
 * to B (left), from which every action pays a mean-zero random reward. The true value of going left
 * is 0, but max over noisy estimates is positive, so plain Q-learning prefers left for a long time.
 */
private fun overestimationRun(): BiasResult {
    val actionsAtB = 8
    val runs = 40
    val episodes = 60

    fun run(double: Boolean, seed: Int): Pair<List<Float>, Float> {
        val rng = RlRng(seed)
        val qA = FloatArray(2)
        val qB1 = FloatArray(actionsAtB)
        val qB2 = FloatArray(actionsAtB)
        val alpha = 0.1f
        val history = mutableListOf<Float>()
        var leftCount = 0
        repeat(episodes) {
            val goLeft = if (rng.next() < 0.1f) rng.next() < 0.5f else qA[0] > qA[1]
            if (goLeft) leftCount++
            if (goLeft) {
                val action = rng.nextInt(actionsAtB)
                // Mean-zero reward: expected value of state B is exactly 0.
                val reward = (rng.next() - 0.5f) * 2f
                if (double) {
                    if (rng.next() < 0.5f) qB1[action] += alpha * (reward - qB1[action])
                    else qB2[action] += alpha * (reward - qB2[action])
                } else {
                    qB1[action] += alpha * (reward - qB1[action])
                }
                val bootstrap = if (double) {
                    val best = qB1.indices.maxByOrNull { qB1[it] }!!
                    qB2[best]
                } else {
                    qB1.maxOrNull()!!
                }
                qA[0] += alpha * (bootstrap - qA[0])
            } else {
                qA[1] += alpha * (0f - qA[1])
            }
            history += qA[0]
        }
        return history to leftCount.toFloat() / episodes
    }

    val singleRuns = (0 until runs).map { run(double = false, seed = 17 + it * 7) }
    val doubleRuns = (0 until runs).map { run(double = true, seed = 17 + it * 7) }
    fun mean(list: List<Pair<List<Float>, Float>>) =
        (0 until episodes).map { e -> list.map { it.first[e] }.average().toFloat() }

    return BiasResult(
        mean(singleRuns),
        mean(doubleRuns),
        singleRuns.map { it.second }.average().toFloat() to doubleRuns.map { it.second }.average().toFloat(),
    )
}

private fun doubleDqnFrames(): List<RlTrainFrame> {
    val result = overestimationRun()
    val frames = mutableListOf<RlTrainFrame>()

    frames += RlTrainFrame(
        status = "The classic setup: from the start state, going right ends the episode with 0. Going left reaches a " +
            "state whose eight actions all pay mean-zero random rewards — so the true value of going left is exactly 0.",
        chain = listOf(0f, 0f),
        chainLabel = "true Q(start, ·) — both actions are worth 0",
    )
    frames += RlTrainFrame(
        status = "Plain Q-learning bootstraps with max over its own noisy estimates. The maximum of several noisy " +
            "zeros is positive, so the start state's left action is valued above its true 0 — averaged over 40 runs it " +
            "peaks at ${"%.3f".format(result.single.maxOrNull()!!)}.",
        plot = RlPlot(
            "Q(start, left) — true value is 0",
            listOf(RlCurve("Q-learning", result.single, WarnColor)),
            -0.1f..0.35f, "episode",
        ),
        readout = "peak overestimate ${"%.3f".format(result.single.maxOrNull()!!)}",
    )
    frames += RlTrainFrame(
        status = "Double Q-learning keeps two estimates: one picks the action, the other scores it. The noise that " +
            "made an action look best is no longer the noise used to value it, so the bias largely cancels.",
        plot = RlPlot(
            "Q(start, left) — true value is 0",
            listOf(
                RlCurve("Q-learning", result.single, WarnColor),
                RlCurve("Double Q-learning", result.double, ImprovedColor),
            ),
            -0.1f..0.35f, "episode",
        ),
        readout = "peak ${"%.3f".format(result.single.maxOrNull()!!)} → ${"%.3f".format(result.double.maxOrNull()!!)}",
    )
    frames += RlTrainFrame(
        status = "The consequence is behavioural, not cosmetic: the inflated value makes the agent take the pointless " +
            "left action ${"%.0f".format(result.leftShare.first * 100)}% of the time, against " +
            "${"%.0f".format(result.leftShare.second * 100)}% for Double Q-learning. In DQN the same trick reuses the " +
            "target network as the second estimator, so it costs nothing.",
        bars = listOf(
            RlBar(
                "share of episodes taking the worthless action",
                listOf(result.leftShare.first, result.leftShare.second),
                AccentColor,
                listOf("Q-learning", "Double"),
            ),
        ),
    )
    return frames
}

// ── Dueling DQN ──────────────────────────────────────────────────────────────

private fun duelingFrames(): List<RlTrainFrame> {
    val learned = runChain(seed = 11, replay = true)
    val q = learned.q
    val stateValue = (0 until GOAL).map { max(q[it][0], q[it][1]) }
    val advantageRight = (0 until GOAL).map { q[it][1] - (q[it][0] + q[it][1]) / 2f }
    val advantageLeft = (0 until GOAL).map { q[it][0] - (q[it][0] + q[it][1]) / 2f }
    val frames = mutableListOf<RlTrainFrame>()

    frames += RlTrainFrame(
        status = "Take the Q-table a trained agent ends up with on the chain. Q(s, a) mixes two different questions: " +
            "how good is this state at all, and how much does the choice of action matter here.",
        bars = listOf(
            RlBar("Q(s, left)", (0 until GOAL).map { q[it][0] }, BaselineColor, (0 until GOAL).map { "s$it" }),
            RlBar("Q(s, right)", (0 until GOAL).map { q[it][1] }, AccentColor, (0 until GOAL).map { "s$it" }),
        ),
    )
    frames += RlTrainFrame(
        status = "Split it: V(s) is the state's own worth, running ${"%.2f".format(stateValue.minOrNull()!!)} to " +
            "${"%.2f".format(stateValue.maxOrNull()!!)} across the chain. That is the bulk of every Q value — mostly " +
            "an answer to \"how close am I to the goal\", repeated inside both actions.",
        bars = listOf(RlBar("V(s)", stateValue, HighlightColor, (0 until GOAL).map { "s$it" })),
    )
    frames += RlTrainFrame(
        status = "What is left is the advantage A(s, a): at most " +
            "${"%.3f".format(advantageRight.maxOf { abs(it) })} in magnitude here, an order of magnitude below V, and " +
            "yet it is the only part that decides the action. A dueling head estimates V once per state instead of " +
            "relearning it inside every action's value.",
        bars = listOf(
            RlBar("A(s, left)", advantageLeft, BaselineColor, (0 until GOAL).map { "s$it" }),
            RlBar("A(s, right)", advantageRight, ImprovedColor, (0 until GOAL).map { "s$it" }),
        ),
    )
    frames += RlTrainFrame(
        status = "One catch: V + A is not identifiable — add 5 to V, subtract 5 from every A, and Q is unchanged. " +
            "Dueling networks pin it down by forcing the advantages to have zero mean, which is exactly how the bars " +
            "above were computed.",
        bars = listOf(
            RlBar(
                "A(s, left) + A(s, right)",
                (0 until GOAL).map { advantageLeft[it] + advantageRight[it] },
                HighlightColor,
                (0 until GOAL).map { "s$it" },
            ),
        ),
        readout = "advantages sum to zero at every state, by construction",
    )
    return frames
}

// ── Prioritized replay ───────────────────────────────────────────────────────

private fun prioritizedFrames(): List<RlTrainFrame> {
    val uniform = runChain(seed = 3, replay = true, prioritised = false)
    val prioritised = runChain(seed = 3, replay = true, prioritised = true)
    val frames = mutableListOf<RlTrainFrame>()

    frames += RlTrainFrame(
        status = "On the chain, almost every stored transition has a TD error of zero — the reward is 0 and both " +
            "value estimates already agree. Sampling those teaches nothing.",
        chain = optimalQ,
        chainLabel = "optimal Q(s, right): only the last transition carries reward at first",
    )
    frames += RlTrainFrame(
        status = "Uniform replay spends most of its updates on those zero-error transitions. The information has to " +
            "diffuse backwards one state per lucky sample.",
        plot = RlPlot("mean |Q − Q*|", listOf(RlCurve("uniform replay", uniform.error, BaselineColor)), 0f..0.6f, "episode"),
        readout = "final error ${"%.3f".format(uniform.error.last())}",
    )
    frames += RlTrainFrame(
        status = "Prioritized replay samples in proportion to |TD error|, so the frontier where value is actually " +
            "changing gets replayed and the settled part of the chain does not.",
        plot = RlPlot(
            "mean |Q − Q*|",
            listOf(
                RlCurve("uniform replay", uniform.error, BaselineColor),
                RlCurve("prioritized", prioritised.error, ImprovedColor),
            ),
            0f..0.6f, "episode",
        ),
        readout = "${episodesToConverge(uniform.error)} → ${episodesToConverge(prioritised.error)} episodes to error < 0.05",
    )
    frames += RlTrainFrame(
        status = "The catch is that sampling by error is no longer sampling the distribution the agent actually " +
            "experiences, so real implementations correct it with importance-sampling weights — a bias/variance trade " +
            "made deliberately.",
        bars = listOf(
            RlBar(
                "episodes to mean error < 0.05",
                listOf(episodesToConverge(uniform.error).toFloat(), episodesToConverge(prioritised.error).toFloat()),
                AccentColor,
                listOf("uniform", "prioritized"),
            ),
        ),
    )
    return frames
}

// ── Noisy nets ───────────────────────────────────────────────────────────────

private class ExploreResult(val reached: Int, val meanSteps: Float, val visits: IntArray, val error: List<Float>)

/**
 * [perEpisodeNoise] switches between the two exploration styles: ε-greedy re-rolls at every step,
 * while parameter noise draws one perturbation per episode and commits to the policy it implies.
 */
private fun exploreChain(seed: Int, perEpisodeNoise: Boolean, episodes: Int = 60): ExploreResult {
    val rng = RlRng(seed)
    val q = Array(CHAIN_STATES) { FloatArray(2) }
    val visits = IntArray(CHAIN_STATES)
    val error = mutableListOf<Float>()
    var reached = 0
    var totalSteps = 0

    repeat(episodes) {
        val bias = if (perEpisodeNoise) (rng.next() - 0.5f) * 0.6f else 0f
        var state = 0
        var steps = 0
        while (steps < 40) {
            steps++
            visits[state]++
            val action = if (perEpisodeNoise) {
                if (q[state][1] + bias >= q[state][0]) 1 else 0
            } else {
                if (rng.next() < 0.3f) rng.nextInt(2) else if (q[state][1] >= q[state][0]) 1 else 0
            }
            val t = stepChain(state, action)
            val target = t.reward + if (t.done) 0f else GAMMA * max(q[t.next][0], q[t.next][1])
            q[t.state][t.action] += 0.5f * (target - q[t.state][t.action])
            state = t.next
            if (t.done) {
                reached++
                totalSteps += steps
                break
            }
        }
        error += qError(q)
    }
    return ExploreResult(reached, if (reached == 0) 0f else totalSteps.toFloat() / reached, visits, error)
}

private fun noisyNetFrames(): List<RlTrainFrame> {
    val dithering = exploreChain(seed = 9, perEpisodeNoise = false)
    val noisy = exploreChain(seed = 9, perEpisodeNoise = true)
    val frames = mutableListOf<RlTrainFrame>()

    frames += RlTrainFrame(
        status = "ε-greedy re-rolls the dice at every single step. It reaches the goal in all " +
            "${dithering.reached} of 60 episodes, but takes ${"%.1f".format(dithering.meanSteps)} steps to do it — " +
            "the minimum is 5, and the rest is the agent undoing its own random moves.",
        bars = listOf(
            RlBar("state visits, ε-greedy", dithering.visits.map { it.toFloat() }, BaselineColor, (0 until CHAIN_STATES).map { "s$it" }),
        ),
        readout = "${dithering.reached}/60 episodes reached the goal in ${"%.1f".format(dithering.meanSteps)} steps",
    )
    frames += RlTrainFrame(
        status = "Noisy nets put the randomness in the weights and draw it once per episode, so the agent commits to " +
            "one perturbed policy and follows it. When the perturbation points the right way it walks straight to the " +
            "goal: ${"%.1f".format(noisy.meanSteps)} steps, against ${"%.1f".format(dithering.meanSteps)}.",
        bars = listOf(
            RlBar("ε-greedy", dithering.visits.map { it.toFloat() }, BaselineColor, (0 until CHAIN_STATES).map { "s$it" }),
            RlBar("parameter noise", noisy.visits.map { it.toFloat() }, ImprovedColor, (0 until CHAIN_STATES).map { "s$it" }),
        ),
        readout = "mean steps to goal ${"%.1f".format(dithering.meanSteps)} → ${"%.1f".format(noisy.meanSteps)}",
    )
    frames += RlTrainFrame(
        status = "Commitment cuts both ways, and the visit counts show it: when the draw points the wrong way the " +
            "agent spends the entire episode stuck at the start, so it only finishes ${noisy.reached} of 60 episodes " +
            "against ε-greedy's ${dithering.reached}. Consistent exploration is a bet, not a free win.",
        bars = listOf(
            RlBar(
                "episodes that reached the goal",
                listOf(dithering.reached.toFloat(), noisy.reached.toFloat()),
                AccentColor,
                listOf("ε-greedy", "parameter noise"),
            ),
        ),
        readout = "${dithering.visits[0]} vs ${noisy.visits[0]} visits to the start state",
    )
    frames += RlTrainFrame(
        status = "The real argument for noisy nets is that the noise scale is a learned parameter: where action " +
            "values are already clear the network can shrink its own noise, and where they are not it stays " +
            "exploratory — with no ε schedule to hand-tune per environment.",
        plot = RlPlot(
            "mean |Q − Q*|",
            listOf(
                RlCurve("ε-greedy", dithering.error, BaselineColor),
                RlCurve("parameter noise", noisy.error, ImprovedColor),
            ),
            0f..0.6f, "episode",
        ),
        readout = "final error ${"%.3f".format(dithering.error.last())} vs ${"%.3f".format(noisy.error.last())}",
    )
    return frames
}

// ── C51 / distributional ─────────────────────────────────────────────────────

private fun distributionalFrames(): List<RlTrainFrame> {
    val atoms = 9
    val support = (0 until atoms).map { -1f + 2f * it / (atoms - 1) }

    // A state whose return is genuinely bimodal: a risky action that pays +1 or −1, and a safe one
    // that always pays a small positive amount. Their means are close; their distributions are not.
    val risky = listOf(0.5f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0.5f)
    val safeAtom = support.indices.minByOrNull { abs(support[it] - 0.25f) }!!
    val safe = List(atoms) { if (it == safeAtom) 1f else 0f }
    fun expectation(p: List<Float>) = p.indices.sumOf { (p[it] * support[it]).toDouble() }.toFloat()

    val frames = mutableListOf<RlTrainFrame>()
    frames += RlTrainFrame(
        status = "Standard Q-learning stores one number per action: the expected return. Here the risky action's " +
            "expectation is ${"%.2f".format(expectation(risky))} and the safe one's is ${"%.2f".format(expectation(safe))} — " +
            "on that basis the safe action simply wins.",
        bars = listOf(
            RlBar("expected return", listOf(expectation(risky), expectation(safe)), AccentColor, listOf("risky", "safe")),
        ),
    )
    frames += RlTrainFrame(
        status = "But those expectations hide completely different worlds. C51 keeps a probability over a fixed grid " +
            "of ${atoms} return values instead of collapsing to the mean.",
        bars = listOf(
            RlBar("P(return) — risky", risky, WarnColor, support.map { "%.1f".format(it) }),
            RlBar("P(return) — safe", safe, ImprovedColor, support.map { "%.1f".format(it) }),
        ),
        readout = "same axis, ${atoms} atoms from −1 to +1",
    )
    frames += RlTrainFrame(
        status = "The risky action is a coin flip between the best and worst outcomes on the board; the safe one is " +
            "a near-certainty. Any risk-sensitive policy needs that distinction, and the mean cannot express it.",
        bars = listOf(
            RlBar("P(return) — risky", risky, WarnColor, support.map { "%.1f".format(it) }),
            RlBar("P(return) — safe", safe, ImprovedColor, support.map { "%.1f".format(it) }),
        ),
        readout = "risky: 50% at −1, 50% at +1",
    )
    frames += RlTrainFrame(
        status = "The learning update projects the shifted, discounted target distribution back onto the fixed atom " +
            "grid — that projection is the whole algorithm. Even agents that only ever act on the mean train faster " +
            "this way: predicting a distribution is a richer signal than predicting one number.",
        bars = listOf(
            RlBar(
                "target support after γ = $GAMMA discount",
                support.map { GAMMA * it },
                HighlightColor,
                support.map { "%.1f".format(GAMMA * it) },
            ),
        ),
    )
    return frames
}

// ── DQN ──────────────────────────────────────────────────────────────────────

private fun dqnFrames(): List<RlTrainFrame> {
    val plain = runChain(seed = 21, replay = false)
    val withReplay = runChain(seed = 21, replay = true)
    val frames = mutableListOf<RlTrainFrame>()

    frames += RlTrainFrame(
        status = "DQN is Q-learning with a function approximator in place of the table — plus the two fixes that " +
            "make that combination stable at all: a replay buffer and a target network.",
        chain = optimalQ,
        chainLabel = "Q*(s, right) on the chain",
    )
    frames += RlTrainFrame(
        status = "Without them, the combination of bootstrapping, off-policy updates and function approximation is " +
            "the \"deadly triad\": correlated consecutive samples and a target that moves with every update.",
        plot = RlPlot("mean |Q − Q*|", listOf(RlCurve("online Q-learning", plain.error, BaselineColor)), 0f..0.6f, "episode"),
        readout = "final error ${"%.3f".format(plain.error.last())}",
    )
    frames += RlTrainFrame(
        status = "Replay decorrelates the updates and reuses each transition; the target network makes each batch an " +
            "ordinary regression against a fixed label. Same environment, same interactions, " +
            "${episodesToConverge(plain.error)} → ${episodesToConverge(withReplay.error)} episodes to converge.",
        plot = RlPlot(
            "mean |Q − Q*|",
            listOf(
                RlCurve("online", plain.error, BaselineColor),
                RlCurve("replay + target", withReplay.error, ImprovedColor),
            ),
            0f..0.6f, "episode",
        ),
        readout = "final error ${"%.3f".format(plain.error.last())} → ${"%.3f".format(withReplay.error.last())}",
    )
    frames += RlTrainFrame(
        status = "The learned greedy policy: right at every state, values rising towards the goal. On Atari the same " +
            "loop runs on raw pixels — the algorithm is unchanged, the approximator is a conv net.",
        bars = listOf(
            RlBar("learned Q(s, right)", (0 until GOAL).map { withReplay.q[it][1] }, ImprovedColor, (0 until GOAL).map { "s$it" }),
            RlBar("optimal Q(s, right)", (0 until GOAL).map { optimalQ[it] }, BaselineColor, (0 until GOAL).map { "s$it" }),
        ),
    )
    return frames
}

// ── Rainbow ──────────────────────────────────────────────────────────────────

private fun rainbowFrames(): List<RlTrainFrame> {
    val base = runChain(seed = 31, replay = false)
    val replay = runChain(seed = 31, replay = true)
    val prioritised = runChain(seed = 31, replay = true, prioritised = true)
    val bias = overestimationRun()
    val frames = mutableListOf<RlTrainFrame>()

    frames += RlTrainFrame(
        status = "Rainbow is not a new algorithm — it is DQN with six independent improvements switched on at once, " +
            "each fixing a different failure.",
        bars = listOf(
            RlBar(
                "episodes to converge on the chain",
                listOf(
                    episodesToConverge(base.error).toFloat(),
                    episodesToConverge(replay.error).toFloat(),
                    episodesToConverge(prioritised.error).toFloat(),
                ),
                AccentColor,
                listOf("plain", "+replay", "+priority"),
            ),
        ),
    )
    frames += RlTrainFrame(
        status = "Each component targets a distinct problem: Double DQN the overestimation bias " +
            "(${"%.3f".format(bias.single.maxOrNull()!!)} → ${"%.3f".format(bias.double.maxOrNull()!!)} peak here), prioritized " +
            "replay the wasted updates, dueling the redundant relearning of state value, noisy nets the exploration " +
            "schedule, and distributional heads the information thrown away by averaging.",
        bars = listOf(
            RlBar("peak overestimate", listOf(bias.single.maxOrNull()!!, bias.double.maxOrNull()!!), WarnColor, listOf("DQN", "Double")),
        ),
    )
    frames += RlTrainFrame(
        status = "They compose because they touch different parts of the agent — the target computation, the sampling " +
            "distribution, the network head, the exploration rule. Rainbow's ablation study is the evidence: removing " +
            "prioritized replay or multi-step returns hurts most, removing dueling barely registers.",
        plot = RlPlot(
            "mean |Q − Q*| on the chain",
            listOf(
                RlCurve("plain", base.error, BaselineColor),
                RlCurve("+ replay", replay.error, AccentColor),
                RlCurve("+ prioritized", prioritised.error, ImprovedColor),
            ),
            0f..0.6f, "episode",
        ),
        readout = "${episodesToConverge(base.error)} → ${episodesToConverge(replay.error)} → ${episodesToConverge(prioritised.error)} episodes",
    )
    frames += RlTrainFrame(
        status = "The lesson generalises past DQN: most large jumps in RL benchmark scores have come from stacking " +
            "small, well-understood fixes rather than from one new idea.",
        bars = listOf(
            RlBar(
                "components",
                listOf(1f, 1f, 1f, 1f, 1f, 1f),
                HighlightColor,
                listOf("double", "prio", "duel", "multi-step", "distrib", "noisy"),
            ),
        ),
    )
    return frames
}

// ── Config ───────────────────────────────────────────────────────────────────

private val comparisonLegend = listOf(
    BaselineColor to "Baseline",
    ImprovedColor to "Improved",
    WarnColor to "Failure mode",
)

private val rlConfigs = mapOf(
    "dqn" to RlTrainConfig(
        intro = "Q-learning on a six-state chain, run for real — with and without the two stabilisers that turn it " +
            "into DQN.",
        legend = comparisonLegend,
        build = ::dqnFrames,
    ),
    "experience_replay" to RlTrainConfig(
        intro = "The same interactions, replayed or not. The gap in the curves is sample efficiency, measured rather " +
            "than asserted.",
        legend = comparisonLegend,
        build = ::replayFrames,
    ),
    "target_networks" to RlTrainConfig(
        intro = "The moving-target problem in its smallest form: one bootstrapped value chasing itself, with and " +
            "without a frozen copy.",
        legend = comparisonLegend,
        build = ::targetNetworkFrames,
    ),
    "double_dqn" to RlTrainConfig(
        intro = "The classic overestimation MDP, averaged over 40 runs. Both actions are truly worth 0 — watch what " +
            "the max operator does to one of them.",
        legend = comparisonLegend,
        build = ::doubleDqnFrames,
    ),
    "dueling_dqn" to RlTrainConfig(
        intro = "A real learned Q-table split into V and A, including why the split needs the zero-mean constraint " +
            "to be well defined.",
        legend = listOf(
            HighlightColor to "V(s)",
            ImprovedColor to "A(s, right)",
            BaselineColor to "A(s, left)",
        ),
        build = ::duelingFrames,
    ),
    "prioritized_replay" to RlTrainConfig(
        intro = "Uniform sampling against TD-error sampling on a sparse-reward chain, where most stored transitions " +
            "carry no information at all.",
        legend = comparisonLegend,
        build = ::prioritizedFrames,
    ),
    "noisy_nets" to RlTrainConfig(
        intro = "Per-step dithering against a perturbation held for a whole episode — the difference shows up in how " +
            "deep into the chain the agent ever gets.",
        legend = comparisonLegend,
        build = ::noisyNetFrames,
    ),
    "c51" to RlTrainConfig(
        intro = "Two actions with nearly the same expected return and completely different return distributions. " +
            "The mean cannot tell them apart; a distribution can.",
        legend = listOf(
            WarnColor to "Risky",
            ImprovedColor to "Safe",
            HighlightColor to "Discounted support",
        ),
        build = ::distributionalFrames,
    ),
    "rainbow_dqn" to RlTrainConfig(
        intro = "The components measured one at a time on the same toy problem, then the argument for why they " +
            "compose.",
        legend = comparisonLegend,
        build = ::rainbowFrames,
    ),
)

private fun rlConfigFor(topicId: String): RlTrainConfig = rlConfigs[topicId] ?: rlConfigs.getValue("dqn")

// ── UI ───────────────────────────────────────────────────────────────────────

@Composable
fun RlTrainingSection(topicId: String) {
    val config = remember(topicId) { rlConfigFor(topicId) }
    val frames = remember(config) { config.build() }
    val playback = rememberPlaybackState(key = config, stepCount = frames.size, initialSpeedMs = 1100f)
    val frame = frames[playback.index.coerceIn(0, frames.lastIndex)]

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            LabIntro(config.intro)

            frame.chain?.let { ChainStrip(it, frame.chainLabel, modifier = Modifier.padding(top = 12.dp)) }
            frame.plot?.let { RlPlotCanvas(it, modifier = Modifier.padding(top = 12.dp)) }
            frame.bars.forEach { bar -> RlBars(bar, modifier = Modifier.padding(top = 12.dp)) }

            frame.readout?.let { ReadoutChips(it, Modifier.padding(top = 14.dp)) }

            LabCaption(frame.status, Modifier.padding(top = 14.dp))

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                config.legend.forEach { (color, label) -> RlLegend(color, label) }
            }

            PlaybackTransport(playback, captions = frames.map { it.status })
        }
    }
}

@Composable
private fun RlLegend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(10.dp).background(color, CircleShape))
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}

@Composable
private fun ChainStrip(values: List<Float>, label: String?, modifier: Modifier = Modifier) {
    val peak = values.maxOrNull()?.coerceAtLeast(0.001f) ?: 1f
    Column(modifier = modifier.fillMaxWidth()) {
        label?.let {
            Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            values.forEachIndexed { index, value ->
                val fill = AccentColor.copy(alpha = 0.15f + 0.7f * (value / peak).coerceIn(0f, 1f))
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .background(fill, RoundedCornerShape(8.dp))
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("s$index", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    Text("%.2f".format(value), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun RlPlotCanvas(plot: RlPlot, modifier: Modifier = Modifier) {
    val axisColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
    val span = plot.curves.maxOfOrNull { it.values.size } ?: 1

    Column(modifier = modifier.fillMaxWidth()) {
        Text(plot.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                .padding(8.dp),
        ) {
            Canvas(modifier = Modifier.fillMaxWidth().height(150.dp)) {
                fun place(i: Int, value: Float): Offset {
                    val x = if (span <= 1) 0f else i.toFloat() / (span - 1) * size.width
                    val ny = (value - plot.yRange.start) / (plot.yRange.endInclusive - plot.yRange.start)
                    return Offset(x, size.height - ny.coerceIn(-0.05f, 1.05f) * size.height)
                }
                if (0f in plot.yRange) {
                    val zero = place(0, 0f)
                    drawLine(axisColor, Offset(0f, zero.y), Offset(size.width, zero.y), strokeWidth = 1.5.dp.toPx())
                }
                plot.curves.forEach { curve ->
                    curve.values.forEachIndexed { i, value ->
                        if (i == 0) return@forEachIndexed
                        drawLine(
                            color = curve.color,
                            start = place(i - 1, curve.values[i - 1]),
                            end = place(i, value),
                            strokeWidth = 4.dp.toPx(),
                        )
                    }
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            plot.curves.forEach { curve ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(8.dp).background(curve.color, CircleShape))
                    Text(
                        curve.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 3.dp),
                    )
                }
            }
            Text(
                plot.xLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.End,
            )
        }
    }
}

@Composable
private fun RlBars(bar: RlBar, modifier: Modifier = Modifier) {
    val axis = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
    val peak = bar.values.maxOfOrNull { abs(it) }?.coerceAtLeast(0.001f) ?: 1f

    Column(modifier = modifier.fillMaxWidth()) {
        Text(bar.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Canvas(modifier = Modifier.fillMaxWidth().height(46.dp).padding(top = 4.dp)) {
            val slot = size.width / bar.values.size
            val mid = size.height / 2f
            drawLine(axis, Offset(0f, mid), Offset(size.width, mid), strokeWidth = 1.5.dp.toPx())
            bar.values.forEachIndexed { index, value ->
                val height = (abs(value) / peak) * (size.height / 2f - 2f)
                drawRoundRect(
                    color = if (value >= 0f) bar.color else WarnColor,
                    topLeft = Offset(index * slot + slot * 0.2f, if (value >= 0f) mid - height else mid),
                    size = Size(slot * 0.6f, height.coerceAtLeast(1.5f)),
                    cornerRadius = CornerRadius(3f, 3f),
                )
            }
        }
        if (bar.captions.isNotEmpty()) {
            Row(modifier = Modifier.fillMaxWidth()) {
                bar.captions.forEach { caption ->
                    Text(
                        caption,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}
