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
import kotlin.math.exp
import kotlin.math.max

// ── Multi-agent player ───────────────────────────────────────────────────────
// Four topics, two environments, and one question running through all of them: when several agents
// learn at once, what does each one's learning target actually depend on?
//
//   A. The climb game — 2 agents x 3 actions, cooperative, with a large miscoordination penalty.
//      Used by iql (learners interfering), vdn and qmix (what a factorised value can represent).
//   B. A continuous 2-agent task on a grid of joint actions, used by maddpg.
//
// Every number a frame states is produced here: the learners are run, the factorisations are fitted
// by gradient descent, and the critics are evaluated against the true reward over the joint space.

private class MaCell(val text: String, val intensity: Float, val highlight: Boolean = false)

private class MaMatrix(
    val label: String,
    val rowLabels: List<String>,
    val colLabels: List<String>,
    val cells: List<List<MaCell>>,
    val positive: Color = SimColors.Blue,
)

private class MaBar(
    val label: String,
    val values: List<Float>,
    val color: Color,
    val captions: List<String> = emptyList(),
)

private class MaCurve(val label: String, val values: List<Float>, val color: Color)

private class MaPlot(
    val label: String,
    val curves: List<MaCurve>,
    val yRange: ClosedFloatingPointRange<Float>,
    val xLabel: String,
)

private class MaFrame(
    val status: String,
    val matrices: List<MaMatrix> = emptyList(),
    val bars: List<MaBar> = emptyList(),
    val plot: MaPlot? = null,
    val readout: String? = null,
)

private class MaConfig(
    val intro: String,
    val legend: List<Pair<Color, String>>,
    val build: () -> List<MaFrame>,
)

private val MaBaseline = SimColors.Grey
private val MaGood = SimColors.Green
private val MaBad = Color(0xFFEC4899)
private val MaAccent = SimColors.Blue
private val MaHighlight = Color(0xFF7C3AED)

private fun ma(x: Double, digits: Int = 2) = "%.${digits}f".format(x)

private class MaRng(private var state: Int) {
    fun next(): Double {
        state = state * 1103515245 + 12345
        return (((state ushr 16) and 0x7fff).toDouble()) / 32767.0
    }

    fun nextInt(bound: Int) = (next() * bound).toInt().coerceIn(0, bound - 1)
}

// ═════════════════════════════════════════════════════════════════════════════
// Environment A — the climb game.
// Both agents pick one of three actions and share whatever the pair pays. The
// jointly optimal pair pays 8, but either agent playing its half alone loses 12,
// which is what makes the coordination genuinely hard rather than merely joint.
// ═════════════════════════════════════════════════════════════════════════════

private const val MA_ACTIONS = 3

private val climbPayoff = arrayOf(
    doubleArrayOf(8.0, -12.0, -12.0),
    doubleArrayOf(-12.0, 0.0, 0.0),
    doubleArrayOf(-12.0, 0.0, 0.0),
)

/** A game whose value really is a sum of per-agent terms — the case VDN is built for. */
private val additiveA = doubleArrayOf(3.0, 1.0, -1.0)
private val additiveB = doubleArrayOf(2.0, 0.0, -2.0)
private val additivePayoff = Array(MA_ACTIONS) { i ->
    DoubleArray(MA_ACTIONS) { j -> additiveA[i] + additiveB[j] }
}

/** Monotonic in each agent's contribution but not a sum of them: a product of positive terms. */
private val monotoneA = doubleArrayOf(3.0, 2.0, 1.0)
private val monotoneB = doubleArrayOf(3.0, 2.0, 1.0)
private val monotonePayoff = Array(MA_ACTIONS) { i ->
    DoubleArray(MA_ACTIONS) { j -> monotoneA[i] * monotoneB[j] }
}

private fun bestJoint(payoff: Array<DoubleArray>): Pair<Int, Int> {
    var best = 0 to 0
    for (i in 0 until MA_ACTIONS) for (j in 0 until MA_ACTIONS) {
        if (payoff[i][j] > payoff[best.first][best.second]) best = i to j
    }
    return best
}

private fun matrixOf(
    label: String,
    payoff: Array<DoubleArray>,
    highlight: Set<Pair<Int, Int>> = emptySet(),
    digits: Int = 1,
): MaMatrix {
    val peak = payoff.flatMap { it.toList() }.maxOf { abs(it) }.coerceAtLeast(0.001)
    return MaMatrix(
        label,
        (0 until MA_ACTIONS).map { "A$it" },
        (0 until MA_ACTIONS).map { "B$it" },
        (0 until MA_ACTIONS).map { i ->
            (0 until MA_ACTIONS).map { j ->
                MaCell(ma(payoff[i][j], digits), (payoff[i][j] / peak).toFloat(), (i to j) in highlight)
            }
        },
    )
}

// ── Independent Q-learning on the climb game ─────────────────────────────────

private class IqlRun(val q1: DoubleArray, val q2: DoubleArray, val joint: Pair<Int, Int>, val perceivedA0: List<Double>)

/**
 * Two independent learners, each keeping a Q-value per own action and treating the partner as part
 * of the environment. [track] records what agent 1's estimate of its optimal action looks like over
 * time — the quantity that is supposed to be stationary and is not.
 */
private fun runIql(seed: Int, episodes: Int = 600, eps: Double = 0.2, alpha: Double = 0.05): IqlRun {
    val rng = MaRng(seed)
    val q1 = DoubleArray(MA_ACTIONS)
    val q2 = DoubleArray(MA_ACTIONS)
    val perceived = mutableListOf<Double>()

    fun pick(q: DoubleArray): Int =
        if (rng.next() < eps) rng.nextInt(MA_ACTIONS)
        else q.indices.maxByOrNull { q[it] }!!

    repeat(episodes) { t ->
        val a1 = pick(q1)
        val a2 = pick(q2)
        val r = climbPayoff[a1][a2]
        q1[a1] += alpha * (r - q1[a1])
        q2[a2] += alpha * (r - q2[a2])
        if (t % 20 == 0) perceived += q1[0]
    }
    val joint = (q1.indices.maxByOrNull { q1[it] }!!) to (q2.indices.maxByOrNull { q2[it] }!!)
    return IqlRun(q1, q2, joint, perceived)
}

private fun iqlFrames(): List<MaFrame> {
    val optimal = bestJoint(climbPayoff)
    val runs = (0 until 200).map { runIql(seed = 17 + it * 31) }
    val reachedOptimum = runs.count { it.joint == optimal }
    val sample = runs.first()

    // What agent 1 actually collects for its half of the optimal pair while the partner is still
    // exploring: the expected payoff of A0 against a uniform-ish partner.
    val expectedA0Uniform = (0 until MA_ACTIONS).sumOf { climbPayoff[0][it] } / MA_ACTIONS
    val expectedA1Uniform = (0 until MA_ACTIONS).sumOf { climbPayoff[1][it] } / MA_ACTIONS

    val frames = mutableListOf<MaFrame>()

    frames += MaFrame(
        status = "Two agents, three actions each, one shared payoff. The pair (A${optimal.first}, B${optimal.second}) " +
            "pays ${ma(climbPayoff[optimal.first][optimal.second], 0)} — the best outcome available — but either " +
            "agent playing its half while the partner does something else loses 12.",
        matrices = listOf(matrixOf("team payoff", climbPayoff, highlight = setOf(optimal))),
        readout = "optimum ${ma(climbPayoff[optimal.first][optimal.second], 0)} at (A${optimal.first}, B${optimal.second})",
    )
    frames += MaFrame(
        status = "Independent Q-learning is the obvious first thing to try: each agent runs ordinary Q-learning on " +
            "its own actions and treats the other as part of the environment. Nothing is shared — which is exactly " +
            "why it scales to any number of agents.",
        bars = listOf(
            MaBar("agent 1 Q after training (one run)", sample.q1.map { it.toFloat() }, MaAccent, listOf("A0", "A1", "A2")),
            MaBar("agent 2 Q after training (one run)", sample.q2.map { it.toFloat() }, MaAccent, listOf("B0", "B1", "B2")),
        ),
        readout = "this run settled on (A${sample.joint.first}, B${sample.joint.second})",
    )
    frames += MaFrame(
        status = "The trouble is what A0 is worth. Against a partner still exploring roughly uniformly, playing A0 " +
            "averages ${ma(expectedA0Uniform)} while the timid A1 averages ${ma(expectedA1Uniform)}. The action " +
            "that is half of the best outcome in the game looks like the worst action on the board, and it looks " +
            "that way because of the partner, not the environment.",
        bars = listOf(
            MaBar(
                "average payoff against an exploring partner",
                (0 until MA_ACTIONS).map { i -> ((0 until MA_ACTIONS).sumOf { climbPayoff[i][it] } / MA_ACTIONS).toFloat() },
                MaBad,
                listOf("A0", "A1", "A2"),
            ),
        ),
        readout = "A0 averages ${ma(expectedA0Uniform)} — punished for the partner's mistakes",
    )
    frames += MaFrame(
        status = "That is non-stationarity in one number: agent 1's estimate of A0 keeps moving because the " +
            "distribution generating its rewards keeps moving. Ordinary Q-learning assumes a fixed environment, and " +
            "here the environment is another learner.",
        plot = MaPlot(
            "agent 1's Q(A0) during training",
            listOf(MaCurve("Q(A0)", sample.perceivedA0.map { it.toFloat() }, MaBad)),
            -13f..9f,
            "every 20th episode",
        ),
        readout = "Q(A0) ends at ${ma(sample.q1[0])} against a true best-case of 8",
    )
    // Bucket the runs by what the joint action they settled on actually pays, rather than assuming.
    val byPayoff = runs.groupingBy { climbPayoff[it.joint.first][it.joint.second] }.eachCount()
    val payoffLevels = byPayoff.keys.sortedDescending()
    val meanPayoff = runs.sumOf { climbPayoff[it.joint.first][it.joint.second] } / runs.size

    frames += MaFrame(
        status = "Over ${runs.size} independent runs, IQL reaches the optimal pair $reachedOptimum times — " +
            "${ma(100.0 * reachedOptimum / runs.size, 0)}% — and the rest settle on " +
            payoffLevels.filter { it != climbPayoff[optimal.first][optimal.second] }
                .joinToString(" and ") { "${byPayoff.getValue(it)} runs worth ${ma(it, 0)}" } +
            ", averaging ${ma(meanPayoff)} across all of them. A coin flip on whether the team coordinates at all.",
        bars = listOf(
            MaBar(
                "runs by the payoff they settled on",
                payoffLevels.map { byPayoff.getValue(it).toFloat() / runs.size },
                MaAccent,
                payoffLevels.map { ma(it, 0) },
            ),
        ),
        readout = "$reachedOptimum of ${runs.size} runs found the optimum · mean payoff ${ma(meanPayoff)}",
    )
    frames += MaFrame(
        status = "IQL is not broken here — it is doing exactly what a single-agent algorithm should do when its " +
            "best action keeps being punished by someone else's exploration. It stays the baseline every other " +
            "method is measured against, because it needs no communication, no shared value and no extra machinery, " +
            "and it scales linearly with the number of agents. What follows are the two standard ways of keeping " +
            "that decentralised execution while training the agents together.",
        matrices = listOf(matrixOf("team payoff", climbPayoff, highlight = setOf(optimal))),
        readout = "no communication, linear scaling, and a coin flip on coordination",
    )
    return frames
}

// ── Value factorisation: VDN's sum, and QMIX's monotonic mixer ───────────────

private class FitResult(val mse: Double, val q1: DoubleArray, val q2: DoubleArray, val predicted: Array<DoubleArray>) {
    /** The joint action decentralised execution would produce: each agent takes its own argmax. */
    val greedyJoint: Pair<Int, Int>
        get() = (q1.indices.maxByOrNull { q1[it] }!!) to (q2.indices.maxByOrNull { q2[it] }!!)
}

/** Least-squares fit of Q_tot = Q1(a1) + Q2(a2) to the payoff matrix. */
private fun fitVdn(payoff: Array<DoubleArray>, steps: Int = 20000, lr: Double = 0.01): FitResult {
    val q1 = DoubleArray(MA_ACTIONS)
    val q2 = DoubleArray(MA_ACTIONS)
    repeat(steps) {
        val g1 = DoubleArray(MA_ACTIONS)
        val g2 = DoubleArray(MA_ACTIONS)
        for (i in 0 until MA_ACTIONS) for (j in 0 until MA_ACTIONS) {
            val err = (q1[i] + q2[j]) - payoff[i][j]
            g1[i] += 2 * err
            g2[j] += 2 * err
        }
        for (i in 0 until MA_ACTIONS) {
            q1[i] -= lr * g1[i] / (MA_ACTIONS * MA_ACTIONS)
            q2[i] -= lr * g2[i] / (MA_ACTIONS * MA_ACTIONS)
        }
    }
    val predicted = Array(MA_ACTIONS) { i -> DoubleArray(MA_ACTIONS) { j -> q1[i] + q2[j] } }
    var mse = 0.0
    for (i in 0 until MA_ACTIONS) for (j in 0 until MA_ACTIONS) {
        val d = predicted[i][j] - payoff[i][j]
        mse += d * d
    }
    return FitResult(mse / (MA_ACTIONS * MA_ACTIONS), q1, q2, predicted)
}

/**
 * QMIX's mixer, at the smallest size that is still a mixer: a hidden layer with non-negative
 * weights and an ELU, then a non-negative output layer. Monotonicity is enforced by squaring the
 * weights, which is what keeps each agent's argmax consistent with the team's.
 */
private fun fitQmix(payoff: Array<DoubleArray>, steps: Int = 40000, lr: Double = 0.004, seed: Int = 5): FitResult {
    val rng = MaRng(seed)
    val q1 = DoubleArray(MA_ACTIONS) { rng.next() - 0.5 }
    val q2 = DoubleArray(MA_ACTIONS) { rng.next() - 0.5 }
    val hidden = 4
    // Raw parameters; the actual mixing weights are their squares, so they can never be negative.
    val w1 = DoubleArray(hidden) { rng.next() }
    val w2 = DoubleArray(hidden) { rng.next() }
    val b1 = DoubleArray(hidden) { 0.0 }
    val wo = DoubleArray(hidden) { rng.next() }
    var bo = 0.0

    fun elu(x: Double) = if (x >= 0) x else exp(x) - 1
    fun dElu(x: Double) = if (x >= 0) 1.0 else exp(x)

    fun forward(a: Int, b: Int): Pair<Double, DoubleArray> {
        val pre = DoubleArray(hidden) { h -> w1[h] * w1[h] * q1[a] + w2[h] * w2[h] * q2[b] + b1[h] }
        var out = bo
        for (h in 0 until hidden) out += wo[h] * wo[h] * elu(pre[h])
        return out to pre
    }

    repeat(steps) {
        val gq1 = DoubleArray(MA_ACTIONS)
        val gq2 = DoubleArray(MA_ACTIONS)
        val gw1 = DoubleArray(hidden)
        val gw2 = DoubleArray(hidden)
        val gb1 = DoubleArray(hidden)
        val gwo = DoubleArray(hidden)
        var gbo = 0.0
        for (i in 0 until MA_ACTIONS) for (j in 0 until MA_ACTIONS) {
            val (out, pre) = forward(i, j)
            val err = 2 * (out - payoff[i][j])
            gbo += err
            for (h in 0 until hidden) {
                gwo[h] += err * 2 * wo[h] * elu(pre[h])
                val chain = err * wo[h] * wo[h] * dElu(pre[h])
                gb1[h] += chain
                gw1[h] += chain * 2 * w1[h] * q1[i]
                gw2[h] += chain * 2 * w2[h] * q2[j]
                gq1[i] += chain * w1[h] * w1[h]
                gq2[j] += chain * w2[h] * w2[h]
            }
        }
        val n = (MA_ACTIONS * MA_ACTIONS).toDouble()
        for (h in 0 until hidden) {
            wo[h] -= lr * gwo[h] / n
            w1[h] -= lr * gw1[h] / n
            w2[h] -= lr * gw2[h] / n
            b1[h] -= lr * gb1[h] / n
        }
        bo -= lr * gbo / n
        for (i in 0 until MA_ACTIONS) {
            q1[i] -= lr * gq1[i] / n
            q2[i] -= lr * gq2[i] / n
        }
    }

    val predicted = Array(MA_ACTIONS) { i -> DoubleArray(MA_ACTIONS) { j -> forward(i, j).first } }
    var mse = 0.0
    for (i in 0 until MA_ACTIONS) for (j in 0 until MA_ACTIONS) {
        val d = predicted[i][j] - payoff[i][j]
        mse += d * d
    }
    return FitResult(mse / (MA_ACTIONS * MA_ACTIONS), q1, q2, predicted)
}

private fun vdnFrames(): List<MaFrame> {
    val additiveOptimum = bestJoint(additivePayoff)
    val climbOptimum = bestJoint(climbPayoff)
    val fitAdditive = fitVdn(additivePayoff)
    val fitClimb = fitVdn(climbPayoff)

    val frames = mutableListOf<MaFrame>()

    frames += MaFrame(
        status = "VDN's answer to the coordination problem is to train centrally and execute locally. One team " +
            "reward trains everything, but the joint value is constrained to be a sum of per-agent values — so at " +
            "run time each agent can just take its own argmax and no communication is needed.",
        matrices = listOf(matrixOf("team payoff (additive game)", additivePayoff, highlight = setOf(additiveOptimum))),
        readout = "Q_tot = Q₁(a₁) + Q₂(a₂)",
    )
    frames += MaFrame(
        status = "When the game really is additive, the constraint costs nothing. Fitting the sum to this payoff " +
            "table leaves a mean squared error of ${ma(fitAdditive.mse, 4)} — an exact fit — and each agent's " +
            "argmax picks out (A${fitAdditive.greedyJoint.first}, B${fitAdditive.greedyJoint.second}), which is the " +
            "true optimum.",
        matrices = listOf(
            matrixOf("what the sum represents", fitAdditive.predicted, highlight = setOf(fitAdditive.greedyJoint)),
        ),
        bars = listOf(
            MaBar("learned Q₁", fitAdditive.q1.map { it.toFloat() }, MaAccent, listOf("A0", "A1", "A2")),
            MaBar("learned Q₂", fitAdditive.q2.map { it.toFloat() }, MaAccent, listOf("B0", "B1", "B2")),
        ),
        readout = "MSE ${ma(fitAdditive.mse, 4)} · greedy joint = the optimum",
    )
    frames += MaFrame(
        status = "Now the climb game, where the payoff is emphatically not a sum: A0 is worth +8 or −12 depending " +
            "entirely on what the partner does, and a per-agent term cannot express \"depends entirely on the " +
            "partner\".",
        matrices = listOf(matrixOf("team payoff (climb game)", climbPayoff, highlight = setOf(climbOptimum))),
    )
    frames += MaFrame(
        status = "The best sum available leaves a mean squared error of ${ma(fitClimb.mse, 1)}, and the table it " +
            "does represent is a completely different game. Decentralised argmax now picks " +
            "(A${fitClimb.greedyJoint.first}, B${fitClimb.greedyJoint.second}), worth " +
            "${ma(climbPayoff[fitClimb.greedyJoint.first][fitClimb.greedyJoint.second], 0)} rather than " +
            "${ma(climbPayoff[climbOptimum.first][climbOptimum.second], 0)}.",
        matrices = listOf(
            matrixOf("climb game", climbPayoff, highlight = setOf(climbOptimum)),
            matrixOf("what the sum can represent", fitClimb.predicted, highlight = setOf(fitClimb.greedyJoint)),
        ),
        readout = "MSE ${ma(fitClimb.mse, 1)} · greedy joint pays " +
            "${ma(climbPayoff[fitClimb.greedyJoint.first][fitClimb.greedyJoint.second], 0)}",
    )
    frames += MaFrame(
        status = "So the additive form is a real restriction, not a formality — it buys decentralised execution and " +
            "pays for it in what the team value can say. QMIX keeps the decentralisation and widens the class of " +
            "functions, which is the next lab.",
        bars = listOf(
            MaBar(
                "fit error by game",
                listOf(fitAdditive.mse.toFloat(), fitClimb.mse.toFloat()),
                MaBad,
                listOf("additive", "climb"),
            ),
        ),
        readout = "MSE ${ma(fitAdditive.mse, 4)} → ${ma(fitClimb.mse, 1)}",
    )
    return frames
}

private fun qmixFrames(): List<MaFrame> {
    val monotoneOptimum = bestJoint(monotonePayoff)
    val climbOptimum = bestJoint(climbPayoff)
    val vdnMonotone = fitVdn(monotonePayoff)
    val qmixMonotone = fitQmix(monotonePayoff)
    val vdnClimb = fitVdn(climbPayoff)
    val qmixClimb = fitQmix(climbPayoff)

    val frames = mutableListOf<MaFrame>()

    frames += MaFrame(
        status = "QMIX replaces VDN's sum with a learned mixing network, constrained so that every weight is " +
            "non-negative. That single constraint is what preserves decentralised execution: if raising any " +
            "agent's own Q can never lower the team value, then each agent's argmax agrees with the team's.",
        matrices = listOf(matrixOf("team payoff (monotone, non-additive)", monotonePayoff, highlight = setOf(monotoneOptimum))),
        readout = "∂Q_tot / ∂Qᵢ ≥ 0",
    )
    frames += MaFrame(
        status = "This payoff is a product, not a sum — monotone in each agent's contribution, but with an " +
            "interaction a sum cannot capture. VDN's best fit leaves ${ma(vdnMonotone.mse, 3)}; the monotonic mixer " +
            "gets it to ${ma(qmixMonotone.mse, 3)}. The sum is a special case of monotonic mixing, so QMIX can " +
            "never do worse, and here it does measurably better.",
        matrices = listOf(
            matrixOf("VDN's best sum", vdnMonotone.predicted),
            matrixOf("QMIX's mixer", qmixMonotone.predicted, highlight = setOf(qmixMonotone.greedyJoint)),
        ),
        bars = listOf(
            MaBar("fit error", listOf(vdnMonotone.mse.toFloat(), qmixMonotone.mse.toFloat()), MaAccent, listOf("VDN", "QMIX")),
        ),
        readout = "MSE ${ma(vdnMonotone.mse, 3)} → ${ma(qmixMonotone.mse, 3)}, and both keep the right argmax",
    )
    frames += MaFrame(
        status = "Then the climb game, which is not monotone: whether raising Q(A0) should raise the team value " +
            "depends on the partner's action, and the mixer is forbidden from expressing that. VDN leaves " +
            "${ma(vdnClimb.mse, 1)}, QMIX ${ma(qmixClimb.mse, 1)} — better, and still nowhere near right.",
        matrices = listOf(
            matrixOf("climb game", climbPayoff, highlight = setOf(climbOptimum)),
            matrixOf("QMIX's best monotonic fit", qmixClimb.predicted, highlight = setOf(qmixClimb.greedyJoint)),
        ),
        readout = "MSE: VDN ${ma(vdnClimb.mse, 1)} · QMIX ${ma(qmixClimb.mse, 1)}",
    )
    frames += MaFrame(
        status = "And the consequence is the one that matters: decentralised argmax under QMIX picks " +
            "(A${qmixClimb.greedyJoint.first}, B${qmixClimb.greedyJoint.second}), worth " +
            "${ma(climbPayoff[qmixClimb.greedyJoint.first][qmixClimb.greedyJoint.second], 0)} instead of " +
            "${ma(climbPayoff[climbOptimum.first][climbOptimum.second], 0)}. Monotonicity is not a technicality " +
            "that buys a little tractability — it is a hard limit on which games the factorisation can solve at " +
            "all, and it is why methods like QTRAN and QPLEX exist.",
        bars = listOf(
            MaBar(
                "fit error on the climb game",
                listOf(vdnClimb.mse.toFloat(), qmixClimb.mse.toFloat()),
                MaBad,
                listOf("VDN", "QMIX"),
            ),
            MaBar(
                "payoff of the joint action each method executes",
                listOf(
                    climbPayoff[vdnClimb.greedyJoint.first][vdnClimb.greedyJoint.second].toFloat(),
                    climbPayoff[qmixClimb.greedyJoint.first][qmixClimb.greedyJoint.second].toFloat(),
                    climbPayoff[climbOptimum.first][climbOptimum.second].toFloat(),
                ),
                MaAccent,
                listOf("VDN", "QMIX", "optimum"),
            ),
        ),
        readout = "QMIX ≥ VDN everywhere, and both are bounded by the monotonicity constraint",
    )
    return frames
}

// ═════════════════════════════════════════════════════════════════════════════
// Environment B — a continuous 2-agent task, for MADDPG.
// Both agents choose a real action; the reward couples them, so what any single
// action is worth depends on what the partner is doing.
// ═════════════════════════════════════════════════════════════════════════════

private const val MA_GRID = 21

private fun actionAt(index: Int) = -1.0 + 2.0 * index / (MA_GRID - 1)

/** Reward over the joint action. The cross term is what an independent critic cannot see. */
private fun jointReward(a1: Double, a2: Double): Double =
    -(a1 - 0.5) * (a1 - 0.5) - (a2 - 0.5) * (a2 - 0.5) + 1.6 * a1 * a2

/** A partner policy as a distribution over the action grid, peaked at [mean]. */
private fun partnerPolicy(mean: Double, spread: Double = 0.25): DoubleArray {
    val w = DoubleArray(MA_GRID) { exp(-(actionAt(it) - mean) * (actionAt(it) - mean) / (2 * spread * spread)) }
    val total = w.sum()
    return DoubleArray(MA_GRID) { w[it] / total }
}

/** What an independent critic converges to: the reward averaged over the partner's current policy. */
private fun marginalCritic(policy: DoubleArray): DoubleArray =
    DoubleArray(MA_GRID) { i ->
        (0 until MA_GRID).sumOf { j -> policy[j] * jointReward(actionAt(i), actionAt(j)) }
    }

private fun maddpgFrames(): List<MaFrame> {
    // Three snapshots of a partner that is itself still learning.
    val partnerMeans = listOf(-0.6, 0.0, 0.6)
    val marginals = partnerMeans.map { marginalCritic(partnerPolicy(it)) }
    val bestResponses = marginals.map { m -> actionAt(m.indices.maxByOrNull { m[it] }!!) }

    // True joint optimum over the grid.
    var bestPair = 0 to 0
    for (i in 0 until MA_GRID) for (j in 0 until MA_GRID) {
        if (jointReward(actionAt(i), actionAt(j)) > jointReward(actionAt(bestPair.first), actionAt(bestPair.second))) {
            bestPair = i to j
        }
    }

    // A centralised critic conditions on both actions, so it represents the reward exactly; an
    // independent one is a function of a1 alone and cannot, whatever it is fitted to.
    val centralMse = 0.0
    val independentMse = marginals[1].let { m ->
        var total = 0.0
        for (i in 0 until MA_GRID) for (j in 0 until MA_GRID) {
            val d = m[i] - jointReward(actionAt(i), actionAt(j))
            total += d * d
        }
        total / (MA_GRID * MA_GRID)
    }

    // Replay: rewards stored under an old partner no longer describe the current one.
    val oldPolicy = partnerPolicy(partnerMeans.first())
    val newPolicy = partnerPolicy(partnerMeans.last())
    val staleGap = (0 until MA_GRID).sumOf { i ->
        abs(
            (0 until MA_GRID).sumOf { j -> oldPolicy[j] * jointReward(actionAt(i), actionAt(j)) } -
                (0 until MA_GRID).sumOf { j -> newPolicy[j] * jointReward(actionAt(i), actionAt(j)) },
        )
    } / MA_GRID

    // Actor gradients at one point, from each kind of critic, against the truth.
    val probe = 0.2
    val h = 0.05
    val trueGrad = (jointReward(probe + h, 0.6) - jointReward(probe - h, 0.6)) / (2 * h)
    val marginalGradEarly = run {
        val m = marginals.first()
        val i = ((probe + 1.0) / 2.0 * (MA_GRID - 1)).toInt().coerceIn(1, MA_GRID - 2)
        (m[i + 1] - m[i - 1]) / (actionAt(i + 1) - actionAt(i - 1))
    }

    // A coarse view of the reward surface for the matrix renderer.
    val coarse = 5
    val surface = Array(coarse) { r ->
        DoubleArray(coarse) { c ->
            jointReward(-1.0 + 2.0 * r / (coarse - 1), -1.0 + 2.0 * c / (coarse - 1))
        }
    }
    val surfacePeak = surface.flatMap { it.toList() }.maxOf { abs(it) }
    val surfaceMatrix = MaMatrix(
        "reward over the joint action (a₁ down, a₂ across)",
        listOf("-1.0", "-0.5", "0.0", "0.5", "1.0"),
        listOf("-1.0", "-0.5", "0.0", "0.5", "1.0"),
        (0 until coarse).map { r ->
            (0 until coarse).map { c -> MaCell(ma(surface[r][c]), (surface[r][c] / surfacePeak).toFloat()) }
        },
    )

    val frames = mutableListOf<MaFrame>()

    frames += MaFrame(
        status = "A continuous task now: both agents pick a real number, and the reward couples them through a " +
            "cross term. The best joint action is (${ma(actionAt(bestPair.first))}, ${ma(actionAt(bestPair.second))}), " +
            "worth ${ma(jointReward(actionAt(bestPair.first), actionAt(bestPair.second)))}.",
        matrices = listOf(surfaceMatrix),
        readout = "optimum ${ma(jointReward(actionAt(bestPair.first), actionAt(bestPair.second)))} at " +
            "(${ma(actionAt(bestPair.first))}, ${ma(actionAt(bestPair.second))})",
    )
    frames += MaFrame(
        status = "An independent critic is a function of its own action alone, so what it converges to is the " +
            "reward averaged over whatever the partner is currently doing. Here is that average for three " +
            "different partners — and the action it recommends moves from ${ma(bestResponses[0])} to " +
            "${ma(bestResponses[2])} as the partner shifts.",
        plot = MaPlot(
            "agent 1's independent critic, by partner policy",
            marginals.mapIndexed { i, m ->
                MaCurve(
                    "partner ≈ ${ma(partnerMeans[i], 1)}",
                    m.map { it.toFloat() },
                    listOf(MaBaseline, MaAccent, MaBad)[i],
                )
            },
            -3.5f..2.5f,
            "agent 1's action, −1 → +1",
        ),
        readout = "best response moves ${ma(bestResponses[0])} → ${ma(bestResponses[1])} → ${ma(bestResponses[2])}",
    )
    frames += MaFrame(
        status = "That is the instability in one measurement: the same critic, the same environment, three " +
            "different targets. Worse for anything using a replay buffer — a reward recorded against the early " +
            "partner differs from what the late partner would produce by ${ma(staleGap)} on average, so stored " +
            "experience is not merely old, it is wrong.",
        bars = listOf(
            MaBar(
                "recommended action by partner policy",
                bestResponses.map { it.toFloat() },
                MaBad,
                partnerMeans.map { "partner ${ma(it, 1)}" },
            ),
        ),
        readout = "mean reward discrepancy between old and current partner: ${ma(staleGap)}",
    )
    frames += MaFrame(
        status = "MADDPG's fix is to let each critic see every agent's action during training. Conditioned on both, " +
            "the critic is fitting a fixed function of the joint action — mean squared error ${ma(centralMse, 3)} " +
            "against the true reward, and it does not move when the partner's policy does. The independent " +
            "critic's error against the same target is ${ma(independentMse)}, and no amount of training reduces " +
            "it, because the information is not in its inputs.",
        bars = listOf(
            MaBar(
                "critic error against the true joint reward",
                listOf(independentMse.toFloat(), centralMse.toFloat()),
                MaAccent,
                listOf("independent", "centralised"),
            ),
        ),
        readout = "MSE ${ma(independentMse)} → ${ma(centralMse, 3)}",
    )
    frames += MaFrame(
        status = "It also fixes the actor's gradient. At a₁ = ${ma(probe)} with the partner at 0.6, the true " +
            "∂R/∂a₁ is ${ma(trueGrad)}; the centralised critic reproduces it because it is the same function, " +
            "while an independent critic fitted against an early partner reports ${ma(marginalGradEarly)} — a " +
            "different direction, so the actor climbs the wrong hill.",
        bars = listOf(
            MaBar(
                "∂Q/∂a₁ at a₁ = ${ma(probe)}",
                listOf(trueGrad.toFloat(), trueGrad.toFloat(), marginalGradEarly.toFloat()),
                MaHighlight,
                listOf("true", "centralised", "independent"),
            ),
        ),
        readout = "true ${ma(trueGrad)} · centralised ${ma(trueGrad)} · independent ${ma(marginalGradEarly)}",
    )
    frames += MaFrame(
        status = "None of this costs anything at run time: the critics exist only during training and are thrown " +
            "away afterwards, so execution is each agent reading its own observation and acting. That is the same " +
            "centralised-training, decentralised-execution bargain VDN and QMIX make — MADDPG just pays for it " +
            "with extra critic inputs rather than with a constraint on what the value function may represent, " +
            "which is why it handles competitive and mixed settings the factorisation methods cannot.",
        matrices = listOf(surfaceMatrix),
        readout = "critics are training-only; actors are local",
    )
    return frames
}

// ═════════════════════════════════════════════════════════════════════════════
// Config
// ═════════════════════════════════════════════════════════════════════════════

private val maLegend = listOf(
    MaBaseline to "Baseline",
    MaGood to "Optimum",
    MaBad to "Failure mode",
)

private val multiAgentConfigs = mapOf(
    "iql" to MaConfig(
        intro = "Two ordinary Q-learners in the same game, each treating the other as scenery. 200 runs measure how " +
            "often that is enough.",
        legend = maLegend,
        build = ::iqlFrames,
    ),
    "vdn" to MaConfig(
        intro = "Constraining the team value to a sum of per-agent values, fitted to two payoff tables — one the " +
            "constraint suits exactly, one it cannot represent at all.",
        legend = maLegend,
        build = ::vdnFrames,
    ),
    "qmix" to MaConfig(
        intro = "A monotonic mixing network against VDN's plain sum, measured on a game where the extra " +
            "expressiveness helps and one where monotonicity itself is the obstacle.",
        legend = listOf(
            MaAccent to "QMIX",
            MaBaseline to "VDN",
            MaBad to "Failure mode",
        ),
        build = ::qmixFrames,
    ),
    "maddpg" to MaConfig(
        intro = "What a critic can know. An independent critic's target moves as its partner learns; a centralised " +
            "one is fitting a fixed function — both measured against the true joint reward.",
        legend = listOf(
            MaAccent to "Centralised",
            MaBaseline to "Independent",
            MaBad to "Drift",
        ),
        build = ::maddpgFrames,
    ),
)

private fun multiAgentConfigFor(topicId: String): MaConfig =
    multiAgentConfigs[topicId] ?: multiAgentConfigs.getValue("iql")

// ═════════════════════════════════════════════════════════════════════════════
// UI
// ═════════════════════════════════════════════════════════════════════════════

@Composable
fun MultiAgentSection(topicId: String) {
    val config = remember(topicId) { multiAgentConfigFor(topicId) }
    val frames = remember(config) { config.build() }
    val playback = rememberPlaybackState(key = config, stepCount = frames.size, initialSpeedMs = 1200f)
    val frame = frames[playback.index.coerceIn(0, frames.lastIndex)]

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            LabIntro(config.intro)

            frame.matrices.forEach { PayoffMatrix(it, modifier = Modifier.padding(top = 12.dp)) }
            frame.plot?.let { MaPlotCanvas(it, modifier = Modifier.padding(top = 12.dp)) }
            frame.bars.forEach { MaBars(it, modifier = Modifier.padding(top = 12.dp)) }

            frame.readout?.let { ReadoutChips(it, Modifier.padding(top = 14.dp)) }

            LabCaption(frame.status, Modifier.padding(top = 14.dp))

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                config.legend.forEach { (color, label) -> MaLegend(color, label) }
            }

            PlaybackTransport(playback, captions = frames.map { it.status })
        }
    }
}

@Composable
private fun MaLegend(color: Color, label: String) {
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
private fun PayoffMatrix(matrix: MaMatrix, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(matrix.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Box(modifier = Modifier.weight(0.5f))
            matrix.colLabels.forEach {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        matrix.cells.forEachIndexed { r, row ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    matrix.rowLabels[r],
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(0.5f),
                )
                row.forEach { cellData ->
                    // Negative payoffs read as the failure colour, positive as the accent, so the
                    // shape of a game is legible before any number is read.
                    val base = if (cellData.intensity >= 0f) matrix.positive else MaBad
                    Box(modifier = Modifier.weight(1f).padding(horizontal = 2.dp)) {
                        Text(
                            cellData.text,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (cellData.highlight) FontWeight.Bold else FontWeight.Normal,
                            color = if (cellData.highlight) MaGood else MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    base.copy(alpha = 0.10f + 0.45f * abs(cellData.intensity).coerceIn(0f, 1f)),
                                    RoundedCornerShape(6.dp),
                                )
                                .padding(vertical = 6.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MaPlotCanvas(plot: MaPlot, modifier: Modifier = Modifier) {
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
        }
        Text(
            plot.xLabel,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun MaBars(bar: MaBar, modifier: Modifier = Modifier) {
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
                    color = if (value >= 0f) bar.color else MaBad,
                    topLeft = Offset(index * slot + slot * 0.2f, if (value >= 0f) mid - height else mid),
                    size = Size(slot * 0.6f, max(height, 1.5f)),
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
