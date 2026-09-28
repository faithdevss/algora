package com.algora.app.feature.topics

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
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
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

// ── Offline & imitation RL player ────────────────────────────────────────────
// Learning from a fixed dataset, or from demonstrations, instead of from your own exploration.
// Every number a frame states is produced by an experiment run when the frames are built: fitted
// Q-iteration over a real logged dataset, behaviour cloning and DAgger on a slippery corridor,
// occupancy matching against a discriminator, max-entropy IRL, and a Bradley-Terry reward model
// fitted to preferences that were labelled on what a rater can actually see.
//
// Three environments are shared across the nine labs, so the comparisons between them are fair:
//   A. a six-state chain carrying a rarely-sampled mean-zero lottery action  (offline_rl, cql)
//   B. a 3x8 corridor where every move slips a row  (imitation_learning, gail, irl)
//   C. the chain again, with a per-step cost  (decision_transformer, rlhf)
//
// Render parts: labelled curve plots, signed bar rows, the corridor grid, and small count tables.

private class OffCurve(val label: String, val values: List<Float>, val color: Color)

private class OffPlot(
    val label: String,
    val curves: List<OffCurve>,
    val yRange: ClosedFloatingPointRange<Float>,
    val xLabel: String,
)

private class OffBar(
    val label: String,
    val values: List<Float>,
    val color: Color,
    val captions: List<String> = emptyList(),
)

/** One corridor cell: [shade] drives the fill alpha, [glyph] is drawn on top. */
private class GridCell(val shade: Float, val glyph: String, val color: Color)

private class OffGrid(val label: String, val cells: List<GridCell>)

private class OffTable(
    val label: String,
    val columnLabels: List<String>,
    val rowLabels: List<String>,
    val rows: List<List<String>>,
    val highlight: Set<Int> = emptySet(),   // row * columns + column
)

private class OffFrame(
    val status: String,
    val plot: OffPlot? = null,
    val bars: List<OffBar> = emptyList(),
    val grid: OffGrid? = null,
    val table: OffTable? = null,
    val readout: String? = null,
)

private class OffConfig(
    val intro: String,
    val legend: List<Pair<Color, String>>,
    val build: () -> List<OffFrame>,
)

private val BaselineColor = SimColors.Grey
private val ImprovedColor = SimColors.Green
private val WarnColor = Color(0xFFEC4899)
private val AccentColor = SimColors.Blue
private val HighlightColor = Color(0xFF7C3AED)

private fun fmt(x: Double, digits: Int = 3) = "%.${digits}f".format(x)

/** Deterministic LCG — every launch has to reproduce the numbers quoted in the status lines. */
private class OffRng(private var state: Int) {
    fun next(): Double {
        state = state * 1103515245 + 12345
        return (((state ushr 16) and 0x7fff).toDouble()) / 32767.0
    }

    fun nextInt(bound: Int) = (next() * bound).toInt().coerceIn(0, bound - 1)
}

// ═════════════════════════════════════════════════════════════════════════════
// Environment A — the chain with a lottery action.
// States 0..5, goal at 5. Actions: 0 = left, 1 = right, 2 = gamble (stay put and
// collect +0.9 with probability 0.1, −0.1 otherwise — expected reward exactly 0).
// The lottery is what makes a *finite* dataset dangerous: a couple of lucky pulls
// are enough to make a worthless action look good, and offline there is no way to
// pull it again and find out otherwise.
// ═════════════════════════════════════════════════════════════════════════════

private const val CH_N = 6
private const val CH_GOAL = 5
private const val CH_NA = 3
private const val CH_GAMMA = 0.95

private fun chStep(s: Int, a: Int, rng: OffRng): Triple<Double, Int, Boolean> = when (a) {
    0 -> Triple(0.0, max(s - 1, 0), false)
    1 -> {
        val ns = min(s + 1, CH_GOAL)
        Triple(if (ns == CH_GOAL) 1.0 else 0.0, ns, ns == CH_GOAL)
    }
    else -> Triple(if (rng.next() < 0.1) 0.9 else -0.1, s, false)
}

/** Exact action values, by value iteration on the known dynamics. */
private val chTrueQ: Array<DoubleArray> = run {
    var q = Array(CH_N) { DoubleArray(CH_NA) }
    repeat(600) {
        val nq = Array(CH_N) { DoubleArray(CH_NA) }
        for (s in 0 until CH_GOAL) {
            nq[s][0] = CH_GAMMA * q[max(s - 1, 0)].max()
            val ns = min(s + 1, CH_GOAL)
            nq[s][1] = if (ns == CH_GOAL) 1.0 else CH_GAMMA * q[ns].max()
            nq[s][2] = CH_GAMMA * q[s].max()
        }
        q = nq
    }
    q
}

private class Trans(val s: Int, val a: Int, val r: Double, val ns: Int, val done: Boolean)

/** Behaviour policy: mostly right, [eps] dithering, [pGamble] lottery pulls, capped reach. */
private fun collect(
    seed: Int,
    episodes: Int,
    eps: Double,
    pGamble: Double = 0.08,
    cap: Int = -1,
): List<Trans> {
    val rng = OffRng(seed)
    val out = mutableListOf<Trans>()
    repeat(episodes) {
        var s = 0
        for (t in 0 until 25) {
            var a = if (rng.next() < pGamble) 2 else if (rng.next() < eps) rng.nextInt(2) else 1
            if (cap >= 0 && s >= cap && a == 1) a = 0
            val (r, ns, d) = chStep(s, a, rng)
            out += Trans(s, a, r, ns, d)
            s = ns
            if (d) break
        }
    }
    return out
}

/** A left-biased logger: right is present in the data, but it is the minority action. */
private fun collectLeftBiased(seed: Int, episodes: Int, pRight: Double): List<Trans> {
    val rng = OffRng(seed)
    val out = mutableListOf<Trans>()
    repeat(episodes) {
        var s = 0
        for (t in 0 until 25) {
            val a = if (rng.next() < 0.08) 2 else if (rng.next() < pRight) 1 else 0
            val (r, ns, d) = chStep(s, a, rng)
            out += Trans(s, a, r, ns, d)
            s = ns
            if (d) break
        }
    }
    return out
}

/**
 * Fitted Q-iteration over a fixed dataset. [cqlBeta] adds the conservative penalty: every update
 * pushes the whole action distribution down by its softmax weight and pushes the action the data
 * actually took back up.
 */
private fun fqi(
    data: List<Trans>,
    cqlBeta: Double = 0.0,
    sweeps: Int = 300,
    alpha: Double = 0.05,
): Array<DoubleArray> {
    val q = Array(CH_N) { DoubleArray(CH_NA) }
    repeat(sweeps) {
        for (t in data) {
            val target = t.r + if (t.done) 0.0 else CH_GAMMA * q[t.ns].max()
            q[t.s][t.a] += alpha * (target - q[t.s][t.a])
            if (cqlBeta > 0.0) {
                val m = q[t.s].max()
                val e = DoubleArray(CH_NA) { exp(q[t.s][it] - m) }
                val z = e.sum()
                for (b in 0 until CH_NA) q[t.s][b] -= alpha * cqlBeta * (e[b] / z)
                q[t.s][t.a] += alpha * cqlBeta
            }
        }
    }
    return q
}

/** The same algorithm, allowed to keep interacting — the contrast the offline labs need. */
private fun onlineQ(seed: Int, transitions: Int): Array<DoubleArray> {
    val rng = OffRng(seed)
    val q = Array(CH_N) { DoubleArray(CH_NA) }
    var s = 0
    var n = 0
    while (n < transitions) {
        val a = if (rng.next() < 0.3) rng.nextInt(CH_NA) else greedyAction(q, s)
        val (r, ns, d) = chStep(s, a, rng)
        n++
        q[s][a] += 0.1 * (r + (if (d) 0.0 else CH_GAMMA * q[ns].max()) - q[s][a])
        s = if (d) 0 else ns
    }
    return q
}

private fun greedyAction(q: Array<DoubleArray>, s: Int): Int {
    var best = 0
    for (a in 1 until CH_NA) if (q[s][a] > q[s][best]) best = a
    return best
}

/** Deployed discounted return of the greedy policy, averaged over runs. */
private fun chReturn(q: Array<DoubleArray>, runs: Int = 400): Double {
    var total = 0.0
    for (i in 0 until runs) {
        val rng = OffRng(9000 + i * 7)
        var s = 0
        var g = 1.0
        for (t in 0 until 60) {
            val (r, ns, d) = chStep(s, greedyAction(q, s), rng)
            total += g * r
            g *= CH_GAMMA
            s = ns
            if (d) break
        }
    }
    return total / runs
}

private fun behaviourReturn(eps: Double, pGamble: Double = 0.08, runs: Int = 400): Double {
    var total = 0.0
    for (i in 0 until runs) {
        val rng = OffRng(4100 + i * 13)
        var s = 0
        var g = 1.0
        for (t in 0 until 60) {
            val a = if (rng.next() < pGamble) 2 else if (rng.next() < eps) rng.nextInt(2) else 1
            val (r, ns, d) = chStep(s, a, rng)
            total += g * r
            g *= CH_GAMMA
            s = ns
            if (d) break
        }
    }
    return total / runs
}

private fun counts(data: List<Trans>): Array<IntArray> {
    val c = Array(CH_N) { IntArray(CH_NA) }
    for (t in data) c[t.s][t.a]++
    return c
}

private val chActionNames = listOf("left", "right", "gamble")

// ═════════════════════════════════════════════════════════════════════════════
// Environment B — a 3x8 corridor. The expert walks the middle row to the far end.
// Every move slips one row up or down with probability 0.25, so execution is noisy
// even though the demonstrations are clean. That gap is the whole imitation story.
// ═════════════════════════════════════════════════════════════════════════════

private const val ROWS = 3
private const val COLS = 8
private const val MID = 1
private const val HOR = 11
private const val SLIP = 0.25
private const val LN_NA = 3   // 0 = right, 1 = up, 2 = down

private fun cell(r: Int, c: Int) = r * COLS + c
private fun rowOf(s: Int) = s / COLS
private fun colOf(s: Int) = s % COLS
private val LN_GOAL = cell(MID, COLS - 1)
private val LN_START = cell(MID, 0)

private fun detMove(s: Int, a: Int): Int {
    var r = rowOf(s)
    var c = colOf(s)
    when (a) {
        0 -> c = min(c + 1, COLS - 1)
        1 -> r = max(r - 1, 0)
        else -> r = min(r + 1, ROWS - 1)
    }
    return cell(r, c)
}

/** Slip-aware transition distribution as (nextState, probability) pairs. */
private fun lnTrans(s: Int, a: Int, wall: Int = -1): List<Pair<Int, Double>> {
    val base = detMove(s, a)
    val out = HashMap<Int, Double>()
    for ((dr, p) in listOf(0 to (1 - SLIP), -1 to SLIP / 2, 1 to SLIP / 2)) {
        val r = (rowOf(base) + dr).coerceIn(0, ROWS - 1)
        var ns = cell(r, colOf(base))
        if (ns == wall) ns = s
        out[ns] = (out[ns] ?: 0.0) + p
    }
    return out.toList()
}

private fun expertAction(s: Int) = if (rowOf(s) < MID) 2 else if (rowOf(s) > MID) 1 else 0

private const val TEMP = 0.15
private const val LN_GAMMA = 0.98
private const val STEP_COST = 0.25

/**
 * Max-entropy soft value iteration. The goal is terminal with value 0, and every step carries a
 * cost — without one, the entropy bonus makes wandering forever worth more than finishing.
 */
private fun softVi(reward: (Int, Int) -> Double, wall: Int = -1, sweeps: Int = 80): Array<DoubleArray> {
    var v = DoubleArray(ROWS * COLS)
    var pol = Array(ROWS * COLS) { DoubleArray(LN_NA) { 1.0 / LN_NA } }
    repeat(sweeps) {
        val nv = DoubleArray(ROWS * COLS)
        val np = Array(ROWS * COLS) { DoubleArray(LN_NA) }
        for (s in 0 until ROWS * COLS) {
            if (s == LN_GOAL) {
                nv[s] = 0.0
                np[s] = DoubleArray(LN_NA) { 1.0 / LN_NA }
                continue
            }
            val q = DoubleArray(LN_NA) { a ->
                reward(s, a) + LN_GAMMA * lnTrans(s, a, wall).sumOf { (ns, p) -> p * v[ns] }
            }
            val m = q.max()
            val e = DoubleArray(LN_NA) { exp((q[it] - m) / TEMP) }
            val z = e.sum()
            nv[s] = m + TEMP * ln(z)
            for (a in 0 until LN_NA) np[s][a] = e[a] / z
        }
        v = nv
        pol = np
    }
    return pol
}

private fun stateReward(rs: (Int) -> Double, wall: Int = -1): (Int, Int) -> Double =
    { s, a -> lnTrans(s, a, wall).sumOf { (ns, p) -> p * rs(ns) } - STEP_COST }

/** Expected state-action visitation over the horizon, normalised to sum to 1. */
private fun occupancy(pol: Array<DoubleArray>, wall: Int = -1): HashMap<Int, Double> {
    var d = DoubleArray(ROWS * COLS)
    d[LN_START] = 1.0
    val occ = HashMap<Int, Double>()
    repeat(HOR) {
        val nd = DoubleArray(ROWS * COLS)
        for (s in 0 until ROWS * COLS) {
            val ps = d[s]
            if (ps <= 0.0 || s == LN_GOAL) continue
            for (a in 0 until LN_NA) {
                val key = s * LN_NA + a
                occ[key] = (occ[key] ?: 0.0) + ps * pol[s][a]
                for ((ns, p) in lnTrans(s, a, wall)) nd[ns] += ps * pol[s][a] * p
            }
        }
        d = nd
    }
    val total = occ.values.sum()
    val out = HashMap<Int, Double>()
    for ((k, v) in occ) out[k] = v / total
    return out
}

private fun tvDistance(p: Map<Int, Double>, q: Map<Int, Double>): Double {
    val keys = p.keys + q.keys
    return 0.5 * keys.sumOf { abs((p[it] ?: 0.0) - (q[it] ?: 0.0)) }
}

/** Per-state visitation, marginalising the action out — what IRL actually matches. */
private fun stateVisits(occ: Map<Int, Double>, wall: Int = -1): DoubleArray {
    val feat = DoubleArray(ROWS * COLS)
    for ((k, v) in occ) for ((ns, p) in lnTrans(k / LN_NA, k % LN_NA, wall)) feat[ns] += v * p
    return feat
}

private fun laneSuccess(
    pol: Array<DoubleArray>,
    runs: Int = 800,
    wall: Int = -1,
    hor: Int = HOR,
): Double {
    var ok = 0
    for (i in 0 until runs) {
        val rng = OffRng(3000 + i * 11)
        var s = LN_START
        for (t in 0 until hor) {
            var u = rng.next()
            var a = LN_NA - 1
            var acc = 0.0
            for (j in 0 until LN_NA) {
                acc += pol[s][j]
                if (u <= acc) { a = j; break }
            }
            u = rng.next()
            acc = 0.0
            for ((ns, p) in lnTrans(s, a, wall)) {
                acc += p
                if (u <= acc) { s = ns; break }
            }
            if (s == LN_GOAL) { ok++; break }
        }
    }
    return ok.toDouble() / runs
}

/** Success plus the two drift statistics that quantify compounding error. */
private class DriftStats(val success: Double, val drifted: Double, val successGivenDrift: Double)

private fun driftStats(pol: Array<DoubleArray>, runs: Int = 800): DriftStats {
    var ok = 0
    var drifted = 0
    var driftOk = 0
    for (i in 0 until runs) {
        val rng = OffRng(3000 + i * 11)
        var s = LN_START
        var leftRow = false
        var reached = false
        for (t in 0 until HOR) {
            var u = rng.next()
            var a = LN_NA - 1
            var acc = 0.0
            for (j in 0 until LN_NA) {
                acc += pol[s][j]
                if (u <= acc) { a = j; break }
            }
            u = rng.next()
            acc = 0.0
            for ((ns, p) in lnTrans(s, a)) {
                acc += p
                if (u <= acc) { s = ns; break }
            }
            if (rowOf(s) != MID) leftRow = true
            if (s == LN_GOAL) { ok++; reached = true; break }
        }
        if (leftRow) {
            drifted++
            if (reached) driftOk++
        }
    }
    return DriftStats(
        ok.toDouble() / runs,
        drifted.toDouble() / runs,
        if (drifted == 0) 0.0 else driftOk.toDouble() / drifted,
    )
}

private fun deterministicPolicy(action: (Int) -> Int) =
    Array(ROWS * COLS) { s -> DoubleArray(LN_NA) { a -> if (a == action(s)) 1.0 else 0.0 } }

private val expertPolicy = deterministicPolicy { expertAction(it) }

/** Clean, slip-free expert demonstrations — the only states a cloner ever sees. */
private fun laneDemos(n: Int): List<Pair<Int, Int>> {
    val out = mutableListOf<Pair<Int, Int>>()
    repeat(n) {
        var s = LN_START
        for (t in 0 until HOR) {
            out += s to expertAction(s)
            s = detMove(s, expertAction(s))
            if (s == LN_GOAL) break
        }
    }
    return out
}

private class BcPolicy(val policy: Array<DoubleArray>, val covered: Set<Int>, val fallback: Int)

/**
 * Behaviour cloning. On a state the demonstrations never reached the cloner has nothing to go on,
 * so it emits the action that dominates the dataset overall — which is what a trained network does
 * off-distribution, and is exactly how the drift compounds.
 */
private fun behaviourClone(pairs: List<Pair<Int, Int>>): BcPolicy {
    val cnt = HashMap<Int, IntArray>()
    val total = IntArray(LN_NA)
    for ((s, a) in pairs) {
        cnt.getOrPut(s) { IntArray(LN_NA) }[a]++
        total[a]++
    }
    val fallback = total.indices.maxByOrNull { total[it] }!!
    val pol = Array(ROWS * COLS) { s ->
        val c = cnt[s]
        val a = if (c == null) fallback else c.indices.maxByOrNull { c[it] }!!
        DoubleArray(LN_NA) { if (it == a) 1.0 else 0.0 }
    }
    return BcPolicy(pol, cnt.keys, fallback)
}

// ═════════════════════════════════════════════════════════════════════════════
// Environment C — the chain with a per-step cost, for return conditioning and RLHF.
// ═════════════════════════════════════════════════════════════════════════════

private const val DT_COST = 0.05

private fun dtNext(s: Int, a: Int) = if (a == 1) min(s + 1, CH_GOAL) else max(s - 1, 0)

private class DtTraj(val steps: List<Triple<Int, Int, Double>>, val ret: Double)

private fun dtTrajectory(pRight: Double, rng: OffRng): DtTraj {
    var s = 0
    val steps = mutableListOf<Triple<Int, Int, Double>>()
    var ret = 0.0
    for (t in 0 until 25) {
        val a = if (rng.next() < pRight) 1 else 0
        val ns = dtNext(s, a)
        val r = (if (ns == CH_GOAL) 1.0 else 0.0) - DT_COST
        steps += Triple(s, a, r)
        ret += r
        s = ns
        if (ns == CH_GOAL) break
    }
    return DtTraj(steps, ret)
}

private val DT_BUCKETS = doubleArrayOf(-0.4, -0.1, 0.15, 0.35, 0.55, 0.68)

private fun dtBucket(r: Double): Int {
    for (i in DT_BUCKETS.indices) if (r <= DT_BUCKETS[i]) return i
    return DT_BUCKETS.size
}

private class DtDataset(
    val trajs: List<DtTraj>,
    val conditioned: HashMap<Int, IntArray>,
    val cloned: HashMap<Int, IntArray>,
)

/** A deliberately mixed dataset: mostly poor trajectories, a few good ones. */
private fun dtDataset(seed: Int): DtDataset {
    val rng = OffRng(seed)
    val trajs = mutableListOf<DtTraj>()
    repeat(150) { trajs += dtTrajectory(0.30, rng) }
    repeat(60) { trajs += dtTrajectory(0.60, rng) }
    repeat(40) { trajs += dtTrajectory(0.95, rng) }
    val cond = HashMap<Int, IntArray>()
    val clone = HashMap<Int, IntArray>()
    for (t in trajs) {
        var rtg = t.ret
        for ((s, a, r) in t.steps) {
            cond.getOrPut(s * 16 + dtBucket(rtg)) { IntArray(2) }[a]++
            clone.getOrPut(s) { IntArray(2) }[a]++
            rtg -= r
        }
    }
    return DtDataset(trajs, cond, clone)
}

private fun dtRollout(ds: DtDataset, target: Double): Pair<Double, Int> {
    var s = 0
    var rtg = target
    var got = 0.0
    var steps = 0
    for (t in 0 until 25) {
        val c = ds.conditioned[s * 16 + dtBucket(rtg)]
        val a = if (c == null || c[0] + c[1] == 0) 1 else if (c[0] > c[1]) 0 else 1
        val ns = dtNext(s, a)
        val r = (if (ns == CH_GOAL) 1.0 else 0.0) - DT_COST
        got += r
        rtg -= r
        s = ns
        steps++
        if (ns == CH_GOAL) break
    }
    return got to steps
}

private fun dtCloneRollout(ds: DtDataset): Pair<Double, Int> {
    var s = 0
    var got = 0.0
    var steps = 0
    for (t in 0 until 25) {
        val c = ds.cloned[s]
        val a = if (c == null) 1 else if (c[0] > c[1]) 0 else 1
        val ns = dtNext(s, a)
        got += (if (ns == CH_GOAL) 1.0 else 0.0) - DT_COST
        s = ns
        steps++
        if (ns == CH_GOAL) break
    }
    return got to steps
}

// ── RLHF ─────────────────────────────────────────────────────────────────────
// A "dash" action advances two states at once. It also carries a cost that never appears in
// anything a rater sees, which is the only reason the reward model can be wrong about it.

private const val DASH_P = 0.35
private const val DASH_COST = 0.8
private val REF_POLICY = doubleArrayOf(0.40, 0.45, 0.15)   // left / right / dash

private fun rlhfNext(s: Int, a: Int) = when (a) {
    0 -> max(s - 1, 0)
    1 -> min(s + 1, CH_GOAL)
    else -> min(s + 2, CH_GOAL)
}

private class PrefSegment(val steps: List<Triple<Int, Int, Int>>, val observed: Double, val truth: Double)

private fun rlhfRollout(pol: Array<DoubleArray>, rng: OffRng): PrefSegment {
    var s = 0
    val steps = mutableListOf<Triple<Int, Int, Int>>()
    var obs = 0.0
    var truth = 0.0
    for (t in 0 until 20) {
        val u = rng.next()
        var a = 2
        var acc = 0.0
        for (j in 0 until 3) {
            acc += pol[s][j]
            if (u <= acc) { a = j; break }
        }
        val ns = rlhfNext(s, a)
        val o = (if (ns == CH_GOAL) 1.0 else 0.0) - DT_COST
        obs += o
        truth += o - (if (a == 2 && rng.next() < DASH_P) DASH_COST else 0.0)
        steps += Triple(s, a, ns)
        s = ns
        if (ns == CH_GOAL) break
    }
    return PrefSegment(steps, obs, truth)
}

private fun segFeatures(seg: PrefSegment) = doubleArrayOf(
    seg.steps.count { it.second == 2 }.toDouble(),
    seg.steps.count { it.second == 1 }.toDouble(),
    if (seg.steps.isNotEmpty() && seg.steps.last().third == CH_GOAL) 1.0 else 0.0,
)

private class RewardModel(val w: DoubleArray, val agreement: Double, val segments: List<PrefSegment>)

/** Bradley-Terry fit to pairwise comparisons, labelled on the observable return only. */
private fun fitRewardModel(seed: Int): RewardModel {
    val rng = OffRng(seed)
    val mixes = listOf(0.25 to 0.05, 0.5 to 0.10, 0.7 to 0.15, 0.85 to 0.10, 0.6 to 0.30)
    val segs = mutableListOf<PrefSegment>()
    repeat(500) {
        val (pr, pd) = mixes[rng.nextInt(mixes.size)]
        val pol = Array(CH_N) { doubleArrayOf(max(0.0, 1 - pr - pd), pr, pd) }
        segs += rlhfRollout(pol, rng)
    }
    val pairs = mutableListOf<Pair<DoubleArray, DoubleArray>>()
    repeat(1500) {
        val i = rng.nextInt(segs.size)
        val j = rng.nextInt(segs.size)
        if (i == j || segs[i].observed == segs[j].observed) return@repeat
        var better = if (segs[i].observed > segs[j].observed) i else j
        var worse = if (better == i) j else i
        if (rng.next() < 0.10) {   // raters are not perfect
            val t = better; better = worse; worse = t
        }
        pairs += segFeatures(segs[better]) to segFeatures(segs[worse])
    }
    val w = DoubleArray(3)
    repeat(8000) {
        val g = DoubleArray(3)
        for ((fb, fw) in pairs) {
            var d = 0.0
            for (k in 0 until 3) d += w[k] * (fb[k] - fw[k])
            val p = 1.0 / (1.0 + exp(-d))
            for (k in 0 until 3) g[k] += (1 - p) * (fb[k] - fw[k])
        }
        for (k in 0 until 3) w[k] += 0.02 * g[k] / pairs.size
    }
    var hit = 0
    var total = 0
    repeat(3000) {
        val i = rng.nextInt(segs.size)
        val j = rng.nextInt(segs.size)
        if (i == j || segs[i].observed == segs[j].observed) return@repeat
        total++
        var mi = 0.0
        var mj = 0.0
        val fi = segFeatures(segs[i])
        val fj = segFeatures(segs[j])
        for (k in 0 until 3) {
            mi += w[k] * fi[k]
            mj += w[k] * fj[k]
        }
        if ((mi > mj) == (segs[i].observed > segs[j].observed)) hit++
    }
    return RewardModel(w, hit.toDouble() / total, segs)
}

private fun proxyReward(w: DoubleArray, a: Int, ns: Int) =
    (if (a == 2) w[0] else 0.0) + (if (a == 1) w[1] else 0.0) + (if (ns == CH_GOAL) w[2] else 0.0)

/** KL-anchored optimisation: the solution is pi_ref(a|s)·exp(Q/beta), renormalised. */
private fun klOptimise(w: DoubleArray, beta: Double, sweeps: Int = 800): Array<DoubleArray> {
    var v = DoubleArray(CH_N)
    var pol = Array(CH_N) { doubleArrayOf(1.0 / 3, 1.0 / 3, 1.0 / 3) }
    repeat(sweeps) {
        val nv = DoubleArray(CH_N)
        val np = Array(CH_N) { DoubleArray(3) }
        for (s in 0 until CH_N) {
            if (s == CH_GOAL) {
                nv[s] = 0.0
                np[s] = doubleArrayOf(1.0 / 3, 1.0 / 3, 1.0 / 3)
                continue
            }
            val q = DoubleArray(3) { a -> proxyReward(w, a, rlhfNext(s, a)) + 0.99 * v[rlhfNext(s, a)] }
            val m = q.max()
            val z = DoubleArray(3) { REF_POLICY[it] * exp((q[it] - m) / beta) }
            val zs = z.sum()
            nv[s] = m + beta * ln(zs)
            for (a in 0 until 3) np[s][a] = z[a] / zs
        }
        v = nv
        pol = np
    }
    return pol
}

private class RlhfEval(val proxy: Double, val truth: Double, val dashes: Double)

private fun evalRlhf(w: DoubleArray, pol: Array<DoubleArray>, runs: Int = 800): RlhfEval {
    var proxy = 0.0
    var truth = 0.0
    var dashes = 0.0
    for (i in 0 until runs) {
        val rng = OffRng(7700 + i * 13)
        var s = 0
        for (t in 0 until 20) {
            val u = rng.next()
            var a = 2
            var acc = 0.0
            for (j in 0 until 3) {
                acc += pol[s][j]
                if (u <= acc) { a = j; break }
            }
            val ns = rlhfNext(s, a)
            proxy += proxyReward(w, a, ns)
            truth += (if (ns == CH_GOAL) 1.0 else 0.0) - DT_COST -
                (if (a == 2 && rng.next() < DASH_P) DASH_COST else 0.0)
            if (a == 2) dashes += 1.0
            s = ns
            if (ns == CH_GOAL) break
        }
    }
    return RlhfEval(proxy / runs, truth / runs, dashes / runs)
}

// ═════════════════════════════════════════════════════════════════════════════
// Shared frame helpers
// ═════════════════════════════════════════════════════════════════════════════

private fun coverageTable(data: List<Trans>, highlightRare: Boolean = true): OffTable {
    val c = counts(data)
    val rows = (0 until CH_GOAL).map { s -> (0 until CH_NA).map { c[s][it].toString() } }
    val highlight = if (!highlightRare) emptySet() else buildSet {
        for (s in 0 until CH_GOAL) add(s * CH_NA + 2)
    }
    return OffTable(
        "transitions logged per state and action",
        chActionNames,
        (0 until CH_GOAL).map { "s$it" },
        rows,
        highlight,
    )
}

private fun qBars(q: Array<DoubleArray>, action: Int, label: String, color: Color) = OffBar(
    label,
    (0 until CH_GOAL).map { q[it][action].toFloat() },
    color,
    (0 until CH_GOAL).map { "s$it" },
)

private fun laneGrid(
    label: String,
    shade: (Int) -> Float,
    glyph: (Int) -> String,
    color: (Int) -> Color = { AccentColor },
) = OffGrid(label, (0 until ROWS * COLS).map { GridCell(shade(it), glyph(it), color(it)) })

private val actionArrows = listOf("→", "↑", "↓")

// ═════════════════════════════════════════════════════════════════════════════
// offline_rl
// ═════════════════════════════════════════════════════════════════════════════

private fun offlineRlFrames(): List<OffFrame> {
    val data = collect(seed = 3, episodes = 40, eps = 0.5)
    val behaviour = behaviourReturn(0.5)
    val naive = fqi(data)
    val naiveReturn = chReturn(naive)
    val optimal = chReturn(chTrueQ)
    val gambleErr = (0 until CH_GOAL).maxOf { naive[it][2] - chTrueQ[it][2] }
    val gambleCounts = (0 until CH_GOAL).sumOf { counts(data)[it][2] }

    // the same algorithm, allowed to keep interacting
    val onlineSame = onlineQ(3, data.size)
    val onlineEight = onlineQ(3, data.size * 8)
    val onlineErr = (0 until CH_GOAL).maxOf { onlineSame[it][2] - chTrueQ[it][2] }

    // stitching, on a dataset with no lottery pulls at all
    val cleanData = collect(seed = 3, episodes = 40, eps = 0.7, pGamble = 0.0)
    val stitched = chReturn(fqi(cleanData))
    val cleanBehaviour = behaviourReturn(0.7, 0.0)

    // truncated coverage
    val capped = collect(seed = 3, episodes = 40, eps = 0.5, pGamble = 0.0, cap = 3)
    val cappedQ = fqi(capped)

    val frames = mutableListOf<OffFrame>()

    frames += OffFrame(
        status = "Offline RL starts from a log somebody else produced. This one holds ${data.size} transitions " +
            "from a behaviour policy that mostly walks right, dithers sometimes, and occasionally pulls a " +
            "lottery action worth exactly nothing in expectation. No further interaction is allowed.",
        table = coverageTable(data),
        readout = "behaviour policy scores ${fmt(behaviour)}; the best possible is ${fmt(optimal)}",
    )
    frames += OffFrame(
        status = "First, what offline RL buys you. On a log with no lottery pulls, the behaviour policy scores " +
            "${fmt(cleanBehaviour)} — but fitted Q-iteration over its transitions reaches ${fmt(stitched)}, the " +
            "optimum. It stitches the good fragments of mediocre trajectories into a policy nobody demonstrated. " +
            "This is the reason not to just clone the data.",
        bars = listOf(
            OffBar(
                "discounted return",
                listOf(cleanBehaviour.toFloat(), stitched.toFloat(), optimal.toFloat()),
                AccentColor,
                listOf("behaviour", "offline RL", "optimal"),
            ),
        ),
        readout = "${fmt(cleanBehaviour)} → ${fmt(stitched)} from the very same transitions",
    )
    frames += OffFrame(
        status = "Now put the lottery back. It was pulled only $gambleCounts times across the whole log, and a couple " +
            "of those pulls happened to pay. Fitted Q-iteration takes the maximum over its own estimates, so those " +
            "lucky samples become the value of the action — overestimated by ${fmt(gambleErr)} at worst.",
        bars = listOf(
            qBars(naive, 2, "learned Q(s, gamble)", WarnColor),
            qBars(chTrueQ, 2, "true Q(s, gamble)", BaselineColor),
        ),
        readout = "greedy policy: " + (0 until CH_GOAL).joinToString(", ") { "s$it→${chActionNames[greedyAction(naive, it)]}" },
    )
    frames += OffFrame(
        status = "Deployed, that policy scores ${fmt(naiveReturn)} against the optimum's ${fmt(optimal)}. The failure " +
            "is not that offline learning is slow. Run the same algorithm online on the identical " +
            "${data.size}-transition budget and its lottery estimate is not inflated at all — it sits " +
            "${fmt(onlineErr)} *below* the truth, because every extra pull drags a lucky estimate back towards the " +
            "mean. Online, more interaction eventually fixes it (${data.size * 8} transitions here). Offline, no " +
            "amount of compute does, because the corrective pulls are not in the log and never will be.",
        bars = listOf(
            OffBar(
                "discounted return",
                listOf(naiveReturn.toFloat(), chReturn(onlineEight).toFloat(), optimal.toFloat()),
                AccentColor,
                listOf("offline", "online ×8", "optimal"),
            ),
            OffBar(
                "worst overestimate of the lottery",
                listOf(gambleErr.toFloat(), onlineErr.toFloat()),
                WarnColor,
                listOf("offline", "online"),
            ),
        ),
        readout = "the error is not slower — it is uncorrectable",
    )
    frames += OffFrame(
        status = "The harder limit is coverage. Log a behaviour policy that never walks past s3 and the far end of " +
            "the chain simply is not in the data: every Q(s, right) stays at " +
            "${fmt(cappedQ[0][1], 2)}, and the learned policy scores ${fmt(chReturn(cappedQ))}. Conservatism, " +
            "pessimism and clever penalties all help with actions the data covers badly. Nothing recovers an " +
            "outcome the data never recorded.",
        bars = listOf(qBars(cappedQ, 1, "learned Q(s, right) from the truncated log", BaselineColor)),
        readout = "states visited: s0–s${capped.maxOf { it.s }} of s0–s$CH_GOAL",
    )
    return frames
}

// ═════════════════════════════════════════════════════════════════════════════
// cql
// ═════════════════════════════════════════════════════════════════════════════

private fun cqlFrames(): List<OffFrame> {
    val data = collect(seed = 3, episodes = 40, eps = 0.5)
    val betas = listOf(0.0, 0.1, 0.3, 1.0, 3.0, 10.0)
    val runs = betas.map { it to fqi(data, cqlBeta = it) }
    val returns = runs.map { chReturn(it.second) }
    val gambleErr = runs.map { r -> (0 until CH_GOAL).maxOf { r.second[it][2] - chTrueQ[it][2] } }
    val rightErr = runs.map { r -> (0 until CH_GOAL).sumOf { r.second[it][1] - chTrueQ[it][1] } / CH_GOAL }
    val naive = runs[0].second
    val fixed = runs[1].second

    // the other failure: pessimism on a log whose own behaviour is poor
    val leftData = collectLeftBiased(3, 60, 0.35)
    val leftBetas = listOf(0.0, 0.1, 0.3, 1.0, 3.0)
    val leftReturns = leftBetas.map { chReturn(fqi(leftData, cqlBeta = it)) }
    val leftCounts = counts(leftData)

    val frames = mutableListOf<OffFrame>()

    frames += OffFrame(
        status = "Plain fitted Q-iteration on this log overrates the rarely-pulled lottery by " +
            "${fmt(gambleErr[0])} and ends up choosing it. CQL's fix is not to estimate it better — it is to " +
            "refuse to trust it.",
        bars = listOf(
            qBars(naive, 2, "learned Q(s, gamble)", WarnColor),
            qBars(chTrueQ, 2, "true Q(s, gamble)", BaselineColor),
        ),
        readout = "deployed return ${fmt(returns[0])} against an optimum of ${fmt(chReturn(chTrueQ))}",
    )
    frames += OffFrame(
        status = "Each update now does two extra things: it pushes the whole action distribution at that state " +
            "down by its softmax weight, and pushes the action the data actually took back up. Actions the log " +
            "rarely contains take the push down far more often than they take the push up.",
        table = coverageTable(data),
        readout = "α weights that push-down; the gamble column is what it targets",
    )
    frames += OffFrame(
        status = "At α = ${betas[1]} the overestimate collapses from ${fmt(gambleErr[0])} to ${fmt(gambleErr[1])} and " +
            "the deployed policy jumps from ${fmt(returns[0])} to ${fmt(returns[1])} — the optimum. Every α from " +
            "${betas[1]} to ${betas.last()} recovers the same optimal policy here, so on this problem the penalty " +
            "costs nothing at all.",
        plot = OffPlot(
            "deployed return by conservatism weight",
            listOf(OffCurve("return", returns.map { it.toFloat() }, ImprovedColor)),
            -0.1f..1.0f,
            "α = ${betas.joinToString(" · ")}",
        ),
        bars = listOf(
            OffBar(
                "worst overestimate of the lottery",
                gambleErr.map { it.toFloat() },
                WarnColor,
                betas.map { fmt(it, 1) },
            ),
        ),
        readout = "return ${fmt(returns[0])} → ${fmt(returns[1])}, overestimate ${fmt(gambleErr[0])} → ${fmt(gambleErr[1])}",
    )
    frames += OffFrame(
        status = "One claim worth checking rather than repeating: that CQL learns values which lower-bound the " +
            "truth. Measured here, they do not. The penalty is *relative* — it pushes rare actions down and data " +
            "actions up — so the absolute level drifts upward with α, and by α = ${betas.last()} the data action is " +
            "overvalued by ${fmt(rightErr.last())}. The guarantee in the paper is on the expected value under the " +
            "learned policy, with a large enough α, not on every entry of the table.",
        bars = listOf(
            OffBar(
                "mean error in Q(s, right), the data's own action",
                rightErr.map { it.toFloat() },
                HighlightColor,
                betas.map { fmt(it, 1) },
            ),
        ),
        readout = "the ordering is conservative; the numbers are not lower bounds",
    )
    frames += OffFrame(
        status = "And the cost of α shows up as soon as the log itself is poor. On a left-biased log — right is " +
            "present but outnumbered ${leftCounts[0][0]} to ${leftCounts[0][1]} at the start state — plain " +
            "fitted Q-iteration stitches its way to ${fmt(leftReturns[0])}, while every α from ${leftBetas[1]} up " +
            "drags the policy back onto the behaviour it was told to stay near and scores ${fmt(leftReturns[1])}. " +
            "α is not a safety dial with a good default: which way it helps depends on whether your danger is " +
            "trusting unsupported actions or refusing supported ones.",
        bars = listOf(
            OffBar(
                "return on the left-biased log, by α",
                leftReturns.map { it.toFloat() },
                AccentColor,
                leftBetas.map { fmt(it, 1) },
            ),
        ),
        readout = "same algorithm, opposite conclusion, because the data changed",
    )
    return frames
}

// ═════════════════════════════════════════════════════════════════════════════
// decision_transformer
// ═════════════════════════════════════════════════════════════════════════════

private fun decisionTransformerFrames(): List<OffFrame> {
    val ds = dtDataset(11)
    val rets = ds.trajs.map { it.ret }
    val best = rets.max()
    val (cloneRet, cloneSteps) = dtCloneRollout(ds)
    val targets = listOf(-0.2, 0.0, 0.2, 0.4, 0.55, 0.68, 0.9)
    val achieved = targets.map { dtRollout(ds, it) }

    val frames = mutableListOf<OffFrame>()

    frames += OffFrame(
        status = "A deliberately mixed log: ${ds.trajs.size} trajectories, most of them poor. Returns run from " +
            "${fmt(rets.min(), 2)} to ${fmt(best, 2)} with a mean of ${fmt(rets.average(), 2)}. Cloning it copies " +
            "the average — the majority action at most states is the wrong one, so the cloned policy scores " +
            "${fmt(cloneRet, 2)} and never finishes inside $cloneSteps steps.",
        bars = listOf(
            OffBar(
                "return",
                listOf(rets.min().toFloat(), rets.average().toFloat(), cloneRet.toFloat(), best.toFloat()),
                AccentColor,
                listOf("worst", "mean", "cloned", "best"),
            ),
        ),
        readout = "behaviour cloning on mixed data inherits the mixture",
    )
    frames += OffFrame(
        status = "The Decision Transformer changes the question. Rather than \"what action follows this state\", " +
            "it learns \"what action follows this state *when the rest of the trajectory earned R*\". Each step is " +
            "tokenised as (return-to-go, state, action), and the return-to-go is decremented by the reward as the " +
            "rollout proceeds.",
        table = OffTable(
            "actions taken at s2, by the return the trajectory went on to earn",
            listOf("left", "right"),
            listOf("low", "mid", "high"),
            listOf(
                (ds.conditioned[2 * 16 + 1] ?: IntArray(2)).map { it.toString() },
                (ds.conditioned[2 * 16 + 3] ?: IntArray(2)).map { it.toString() },
                (ds.conditioned[2 * 16 + 6] ?: IntArray(2)).map { it.toString() },
            ),
            setOf(2 * 2 + 1),
        ),
        readout = "the same state, opposite action, depending on the return it is conditioned on",
    )
    frames += OffFrame(
        status = "So the target return becomes the control knob. Prompt it with a low return and it reproduces " +
            "dithering; prompt it high and it walks straight to the goal. Nothing was optimised — the model only " +
            "ever predicted actions.",
        plot = OffPlot(
            "achieved return against the target it was prompted with",
            listOf(
                OffCurve("achieved", achieved.map { it.first.toFloat() }, ImprovedColor),
                OffCurve("target", targets.map { it.toFloat() }, BaselineColor),
            ),
            -0.4f..1.0f,
            "target " + targets.joinToString(" · ") { fmt(it, 2) },
        ),
        readout = targets.indices.joinToString("  ") { "${fmt(targets[it], 2)}→${fmt(achieved[it].first, 2)}" },
    )
    frames += OffFrame(
        status = "The ceiling is the honest part. Asking for ${fmt(targets.last(), 2)} — more than any trajectory in " +
            "the log achieved — returns ${fmt(achieved.last().first, 2)}, exactly the dataset's best. Conditioning " +
            "retrieves the behaviour that earned a return; it cannot invent behaviour that earns more. That is the " +
            "trade for dropping value functions and Bellman backups entirely.",
        bars = listOf(
            OffBar(
                "achieved return",
                listOf(achieved[1].first.toFloat(), achieved[4].first.toFloat(), achieved.last().first.toFloat(), best.toFloat()),
                ImprovedColor,
                listOf("ask 0.0", "ask 0.55", "ask 0.90", "data best"),
            ),
        ),
        readout = "asks above ${fmt(best, 2)} all return ${fmt(best, 2)} — the log is the ceiling",
    )
    return frames
}

// ═════════════════════════════════════════════════════════════════════════════
// imitation_learning
// ═════════════════════════════════════════════════════════════════════════════

private fun imitationFrames(): List<OffFrame> {
    val expertRate = laneSuccess(expertPolicy)
    val demoCounts = listOf(5, 20, 80, 320)
    val clones = demoCounts.map { behaviourClone(laneDemos(it)) }
    val rates = clones.map { laneSuccess(it.policy) }
    val bc = clones[1]
    val drift = driftStats(bc.policy)

    // DAgger: relabel the states the learner actually reaches
    val aggregated = laneDemos(5).toMutableList()
    var current = behaviourClone(aggregated)
    val daggerRates = mutableListOf(laneSuccess(current.policy))
    val daggerCoverage = mutableListOf(current.covered.size)
    repeat(4) { iteration ->
        val rng = OffRng(600 + iteration * 17)
        repeat(20) {
            var s = LN_START
            for (t in 0 until HOR) {
                aggregated += s to expertAction(s)
                var u = rng.next()
                var a = LN_NA - 1
                var acc = 0.0
                for (j in 0 until LN_NA) {
                    acc += current.policy[s][j]
                    if (u <= acc) { a = j; break }
                }
                u = rng.next()
                acc = 0.0
                for ((ns, p) in lnTrans(s, a)) {
                    acc += p
                    if (u <= acc) { s = ns; break }
                }
                if (s == LN_GOAL) break
            }
        }
        current = behaviourClone(aggregated)
        daggerRates += laneSuccess(current.policy)
        daggerCoverage += current.covered.size
    }
    val daggerDrift = driftStats(current.policy)

    val frames = mutableListOf<OffFrame>()

    frames += OffFrame(
        status = "A ${ROWS}×$COLS corridor. The expert walks the middle row to the far end, and every move slips one " +
            "row up or down with probability ${fmt(SLIP, 2)}. Following the expert's own rule succeeds " +
            "${fmt(expertRate, 3)} of the time — the slips alone cost the rest.",
        grid = laneGrid(
            "the expert's route",
            shade = { if (rowOf(it) == MID) 0.85f else 0.06f },
            glyph = {
                when (it) {
                    LN_START -> "S"
                    LN_GOAL -> "G"
                    else -> if (rowOf(it) == MID) actionArrows[expertAction(it)] else ""
                }
            },
            color = { if (rowOf(it) == MID) ImprovedColor else BaselineColor },
        ),
        readout = "expert success ${fmt(expertRate, 3)}",
    )
    frames += OffFrame(
        status = "Demonstrations are clean: the expert shows the task without slipping. So the cloner only ever sees " +
            "the ${bc.covered.size} states along the middle row, out of ${ROWS * COLS}. Two thirds of the corridor " +
            "is a blank it has no label for, and off there it falls back on the action that dominates its dataset " +
            "— \"${actionArrows[bc.fallback]}\", which walks along the wrong row rather than back to the right one.",
        grid = laneGrid(
            "states the demonstrations cover",
            shade = { if (it in bc.covered) 0.85f else 0.06f },
            glyph = { if (it in bc.covered) actionArrows[expertAction(it)] else "·" },
            color = { if (it in bc.covered) AccentColor else BaselineColor },
        ),
        readout = "${bc.covered.size} of ${ROWS * COLS} states demonstrated",
    )
    frames += OffFrame(
        status = "Deployed into the slippery corridor the clone scores ${fmt(drift.success, 3)} against the expert's " +
            "${fmt(expertRate, 3)}. The mechanism is visible in the split: ${fmt(drift.drifted, 2)} of episodes " +
            "leave the demonstrated row at least once, and once off it the success rate is only " +
            "${fmt(drift.successGivenDrift, 2)}. One slip puts the policy somewhere it was never taught, and its " +
            "own mistake carries it further out.",
        bars = listOf(
            OffBar(
                "success rate",
                listOf(drift.success.toFloat(), drift.successGivenDrift.toFloat(), expertRate.toFloat()),
                AccentColor,
                listOf("clone", "clone after drift", "expert"),
            ),
        ),
        readout = "${fmt(drift.drifted * 100, 0)}% of episodes drift off the demonstrated states",
    )
    frames += OffFrame(
        status = "The instinctive fix — collect more demonstrations — does nothing. Going from ${demoCounts.first()} " +
            "to ${demoCounts.last()} demonstrations leaves coverage at exactly ${clones.last().covered.size} states " +
            "and the success rate at ${fmt(rates.last(), 3)}. More data from the expert's distribution cannot " +
            "describe states the expert's distribution never reaches.",
        bars = listOf(
            OffBar(
                "success rate by number of demonstrations",
                rates.map { it.toFloat() },
                BaselineColor,
                demoCounts.map { it.toString() },
            ),
        ),
        readout = "${demoCounts.last()} demonstrations, still ${clones.last().covered.size}/${ROWS * COLS} states covered",
    )
    frames += OffFrame(
        status = "DAgger changes which states get labelled: run the learner, and ask the expert what it should have " +
            "done at the states the *learner* reached. Coverage goes ${daggerCoverage.first()} → " +
            "${daggerCoverage.last()} states and the success rate ${fmt(daggerRates.first(), 3)} → " +
            "${fmt(daggerRates.last(), 3)}, matching the expert. Recovery from a mistake is precisely the skill no " +
            "flawless demonstration contains.",
        grid = laneGrid(
            "states labelled after DAgger",
            shade = { if (it in current.covered) 0.85f else 0.06f },
            glyph = { if (it in current.covered) actionArrows[expertAction(it)] else "·" },
            color = { if (it in current.covered) ImprovedColor else BaselineColor },
        ),
        plot = OffPlot(
            "success rate per DAgger round",
            listOf(OffCurve("DAgger", daggerRates.map { it.toFloat() }, ImprovedColor)),
            0f..1f,
            "round",
        ),
        readout = "after drift: ${fmt(drift.successGivenDrift, 2)} → ${fmt(daggerDrift.successGivenDrift, 2)}",
    )
    return frames
}

// ═════════════════════════════════════════════════════════════════════════════
// gail
// ═════════════════════════════════════════════════════════════════════════════

private class GailRun(
    val tv: List<Double>,
    val accuracy: List<Double>,
    val success: List<Double>,
    val policy: Array<DoubleArray>,
)

private fun runGail(iterations: Int = 25): GailRun {
    val expOcc = occupancy(expertPolicy)
    var agOcc = occupancy(Array(ROWS * COLS) { DoubleArray(LN_NA) { 1.0 / LN_NA } })
    var policy = expertPolicy
    val tv = mutableListOf<Double>()
    val acc = mutableListOf<Double>()
    val success = mutableListOf<Double>()
    repeat(iterations) {
        val disc = { s: Int, a: Int ->
            val e = expOcc[s * LN_NA + a] ?: 0.0
            val g = agOcc[s * LN_NA + a] ?: 0.0
            (e + 1e-3) / (e + g + 2e-3)
        }
        // The symmetric logit reward. The usual −log(1−D) form is positive everywhere, which on a
        // task you can end pays the agent to survive instead of finish.
        policy = softVi({ s: Int, a: Int ->
            val d = disc(s, a)
            ln(max(1e-6, d)) - ln(max(1e-6, 1 - d))
        })
        val fresh = occupancy(policy)
        // Average the occupancies instead of jumping to the best response — the same fictitious-play
        // damping the self-play lab needs, and for the same reason.
        val merged = HashMap<Int, Double>()
        for (k in expOcc.keys + agOcc.keys + fresh.keys) {
            merged[k] = 0.75 * (agOcc[k] ?: 0.0) + 0.25 * (fresh[k] ?: 0.0)
        }
        agOcc = merged
        var a = 0.0
        for ((k, v) in expOcc) if (disc(k / LN_NA, k % LN_NA) > 0.5) a += 0.5 * v
        for ((k, v) in agOcc) if (disc(k / LN_NA, k % LN_NA) <= 0.5) a += 0.5 * v
        tv += tvDistance(agOcc, expOcc)
        acc += a
        success += laneSuccess(policy)
    }
    return GailRun(tv, acc, success, policy)
}

private fun gailFrames(): List<OffFrame> {
    val expOcc = occupancy(expertPolicy)
    val bc = behaviourClone(laneDemos(20))
    val bcTv = tvDistance(occupancy(bc.policy), expOcc)
    val bcRate = laneSuccess(bc.policy)
    val run = runGail()
    val expertRate = laneSuccess(expertPolicy)
    val wall = cell(MID, 4)
    val gailWalled = laneSuccess(run.policy, wall = wall, hor = 16)
    val bcWalled = laneSuccess(bc.policy, wall = wall, hor = 16)

    val frames = mutableListOf<OffFrame>()

    frames += OffFrame(
        status = "GAIL drops the idea of copying actions and matches distributions instead. The object it compares " +
            "is occupancy: how often each state-action pair is visited over an episode. The clone's occupancy sits " +
            "${fmt(bcTv, 3)} away from the expert's in total variation — that distance, not the per-state action " +
            "accuracy, is what its ${fmt(bcRate, 3)} success rate reflects.",
        grid = laneGrid(
            "expert occupancy",
            shade = { s -> ((0 until LN_NA).sumOf { expOcc[s * LN_NA + it] ?: 0.0 } * 9).toFloat().coerceIn(0f, 1f) },
            glyph = { if (it == LN_GOAL) "G" else if (it == LN_START) "S" else "" },
            color = { ImprovedColor },
        ),
        readout = "TV(expert, clone) = ${fmt(bcTv, 3)}",
    )
    frames += OffFrame(
        status = "A discriminator is fitted to tell expert visits from agent visits, and the agent is then trained " +
            "to maximise how expert-like it looks. At the start the discriminator separates them almost perfectly " +
            "— ${fmt(run.accuracy.first(), 2)} accuracy at a total-variation distance of ${fmt(run.tv.first(), 3)}.",
        plot = OffPlot(
            "occupancy distance from the expert",
            listOf(OffCurve("TV distance", run.tv.map { it.toFloat() }, WarnColor)),
            0f..0.7f,
            "adversarial round",
        ),
        readout = "round 0: TV ${fmt(run.tv.first(), 3)}, discriminator ${fmt(run.accuracy.first(), 2)}",
    )
    frames += OffFrame(
        status = "As the agent closes the gap the discriminator's job gets harder, and its accuracy falls to " +
            "${fmt(run.accuracy.last(), 2)} — chance. The distance ends at ${fmt(run.tv.last(), 3)} and the agent " +
            "succeeds ${fmt(run.success.last(), 3)} of the time against the clone's ${fmt(bcRate, 3)} and the " +
            "expert's ${fmt(expertRate, 3)}, from the same demonstrations the clone had.",
        plot = OffPlot(
            "the adversarial game",
            listOf(
                OffCurve("TV distance", run.tv.map { it.toFloat() }, WarnColor),
                OffCurve("discriminator accuracy", run.accuracy.map { it.toFloat() }, AccentColor),
                OffCurve("success rate", run.success.map { it.toFloat() }, ImprovedColor),
            ),
            0f..1f,
            "adversarial round",
        ),
        readout = "TV ${fmt(run.tv.first(), 3)} → ${fmt(run.tv.last(), 3)}; discriminator " +
            "${fmt(run.accuracy.first(), 2)} → ${fmt(run.accuracy.last(), 2)}",
    )
    frames += OffFrame(
        status = "Two things this cost. GAIL fixed the drift the clone could not because it *interacted* with the " +
            "corridor — it visited the off-row states and learned what to do there, using no reward but plenty of " +
            "environment access the clone never needed. And what it learned is a policy, not a purpose: put a wall " +
            "in the middle row and it scores ${fmt(gailWalled, 3)}, below even the clone's ${fmt(bcWalled, 3)}. " +
            "Matching the expert's distribution matches it in the world it was recorded in.",
        bars = listOf(
            OffBar(
                "success rate, original corridor",
                listOf(bcRate.toFloat(), run.success.last().toFloat(), expertRate.toFloat()),
                ImprovedColor,
                listOf("clone", "GAIL", "expert"),
            ),
            OffBar(
                "success rate, wall added",
                listOf(bcWalled.toFloat(), gailWalled.toFloat()),
                WarnColor,
                listOf("clone", "GAIL"),
            ),
        ),
        readout = "occupancy matching does not survive a changed environment",
    )
    return frames
}

// ═════════════════════════════════════════════════════════════════════════════
// irl
// ═════════════════════════════════════════════════════════════════════════════

private class IrlRun(val weights: DoubleArray, val error: List<Double>, val success: List<Double>, val policy: Array<DoubleArray>)

/** Max-entropy IRL: plan under the current reward, compare visitation, move the reward. */
private fun runIrl(expFeat: DoubleArray, iterations: Int = 120, init: DoubleArray? = null): IrlRun {
    val w = init?.copyOf() ?: DoubleArray(ROWS * COLS)
    val error = mutableListOf<Double>()
    val success = mutableListOf<Double>()
    var policy = expertPolicy
    repeat(iterations) { i ->
        policy = softVi(stateReward({ s -> w[s] }))
        val feat = stateVisits(occupancy(policy))
        val l1 = (0 until ROWS * COLS).sumOf { abs(expFeat[it] - feat[it]) }
        if (i % 10 == 0) {
            error += l1
            success += laneSuccess(policy)
        }
        for (s in 0 until ROWS * COLS) w[s] += 2.0 * (expFeat[s] - feat[s])
    }
    val finalFeat = stateVisits(occupancy(policy))
    error += (0 until ROWS * COLS).sumOf { abs(expFeat[it] - finalFeat[it]) }
    success += laneSuccess(policy)
    return IrlRun(w, error, success, policy)
}

private fun irlFrames(): List<OffFrame> {
    val expOcc = occupancy(expertPolicy)
    val expFeat = stateVisits(expOcc)
    val run = runIrl(expFeat)
    val w = run.weights

    // a second run from a different starting reward
    val init = DoubleArray(ROWS * COLS).also {
        val rng = OffRng(4242)
        for (s in it.indices) it[s] = (rng.next() - 0.5) * 4.0
    }
    val second = runIrl(expFeat, init = init)
    val rewardGap = (0 until ROWS * COLS).maxOf { abs(w[it] - second.weights[it]) }
    val policyGap = (0 until ROWS * COLS).maxOf { s ->
        (0 until LN_NA).maxOf { abs(second.policy[s][it] - run.policy[s][it]) }
    }

    val wall = cell(MID, 4)
    val bc = behaviourClone(laneDemos(20))
    val bcWalled = laneSuccess(bc.policy, wall = wall, hor = 16)
    val irlWalled = laneSuccess(softVi(stateReward({ s -> w[s] }, wall), wall), wall = wall, hor = 16)
    val trueWalled = laneSuccess(
        softVi(stateReward({ s -> if (s == LN_GOAL) 3.0 else 0.0 }, wall), wall),
        wall = wall,
        hor = 16,
    )
    val peak = (0 until ROWS * COLS).maxByOrNull { w[it] }!!
    val wMax = w[peak]

    val frames = mutableListOf<OffFrame>()

    frames += OffFrame(
        status = "IRL asks a different question of the same demonstrations: not which action the expert took, but " +
            "what objective would make those actions optimal. The only thing it matches is expected state " +
            "visitation — how much time the expert spends where.",
        grid = laneGrid(
            "expert state visitation",
            shade = { (expFeat[it] * 6).toFloat().coerceIn(0f, 1f) },
            glyph = { if (it == LN_GOAL) "G" else if (it == LN_START) "S" else "" },
            color = { AccentColor },
        ),
        readout = "match these counts and you have matched the behaviour",
    )
    frames += OffFrame(
        status = "The loop: guess a reward, plan the max-entropy optimal policy under it, compare that policy's " +
            "visitation to the expert's, and move the reward up where the expert went more often. Visitation error " +
            "falls from ${fmt(run.error.first(), 3)} to ${fmt(run.error.last(), 3)} and the resulting policy's " +
            "success rate climbs ${fmt(run.success.first(), 3)} → ${fmt(run.success.last(), 3)}.",
        plot = OffPlot(
            "visitation mismatch and the policy it implies",
            listOf(
                OffCurve("feature-count error", run.error.map { it.toFloat() }, WarnColor),
                OffCurve("success rate", run.success.map { it.toFloat() }, ImprovedColor),
            ),
            0f..1f,
            "every 10th iteration",
        ),
        readout = "error ${fmt(run.error.first(), 3)} → ${fmt(run.error.last(), 3)}",
    )
    frames += OffFrame(
        status = "The recovered reward peaks at the goal cell (${fmt(wMax, 2)}) and slopes up along the corridor " +
            "towards it. Nobody wrote that down — it was inferred from where the expert chose to spend its time.",
        grid = laneGrid(
            "recovered reward",
            shade = { (w[it] / wMax).toFloat().coerceIn(0f, 1f) },
            glyph = { if (it == LN_GOAL) "G" else "" },
            color = { HighlightColor },
        ),
        readout = "peak at row ${rowOf(peak)}, column ${colOf(peak)} = ${fmt(wMax, 2)}",
    )
    frames += OffFrame(
        status = "IRL is ill-posed, and the second run shows it concretely: started from a different random reward, " +
            "it lands on weights that differ by up to ${fmt(rewardGap, 2)} — yet the policies they imply differ by " +
            "at most ${fmt(policyGap, 3)} in action probability and succeed at ${fmt(second.success.last(), 3)} " +
            "against ${fmt(run.success.last(), 3)}. Many rewards explain the same behaviour equally well; " +
            "max-entropy IRL picks the least committal of them.",
        bars = listOf(
            OffBar(
                "recovered reward along the middle row, two runs",
                (0 until COLS).map { w[cell(MID, it)].toFloat() },
                HighlightColor,
                (0 until COLS).map { "c$it" },
            ),
            OffBar(
                "second run",
                (0 until COLS).map { second.weights[cell(MID, it)].toFloat() },
                BaselineColor,
                (0 until COLS).map { "c$it" },
            ),
        ),
        readout = "different rewards, the same behaviour",
    )
    frames += OffFrame(
        status = "Why bother recovering a reward at all: put a wall in the middle of the corridor and re-plan. The " +
            "clone walks into it and scores ${fmt(bcWalled, 3)}; planning against the *recovered* reward routes " +
            "around it for ${fmt(irlWalled, 3)}, close to the ${fmt(trueWalled, 3)} you get from the true reward. " +
            "A policy is an answer to one world. A reward is an answer to the question, and it still applies when " +
            "the world changes.",
        grid = laneGrid(
            "the changed corridor",
            shade = { if (it == wall) 1f else if (rowOf(it) == MID) 0.35f else 0.08f },
            glyph = { if (it == wall) "✕" else if (it == LN_GOAL) "G" else if (it == LN_START) "S" else "" },
            color = { if (it == wall) WarnColor else AccentColor },
        ),
        bars = listOf(
            OffBar(
                "success after the wall appears",
                listOf(bcWalled.toFloat(), irlWalled.toFloat(), trueWalled.toFloat()),
                ImprovedColor,
                listOf("clone", "IRL re-plan", "true reward"),
            ),
        ),
        readout = "${fmt(bcWalled, 3)} → ${fmt(irlWalled, 3)} by re-planning against an inferred objective",
    )
    return frames
}

// ═════════════════════════════════════════════════════════════════════════════
// rlhf
// ═════════════════════════════════════════════════════════════════════════════

private fun rlhfFrames(): List<OffFrame> {
    val rm = fitRewardModel(29)
    val betas = listOf(0.02, 0.05, 0.1, 0.25, 0.5, 1.0, 2.0, 4.0, 8.0)
    val evals = betas.map { evalRlhf(rm.w, klOptimise(rm.w, it)) }
    val reference = evalRlhf(rm.w, Array(CH_N) { REF_POLICY.copyOf() })
    val bestTrue = evals.indices.maxByOrNull { evals[it].truth }!!
    val bestProxy = evals.indices.maxByOrNull { evals[it].proxy }!!
    val observedGap = rm.segments.map { it.observed - it.truth }.average()

    val frames = mutableListOf<OffFrame>()

    frames += OffFrame(
        status = "The chain gains a \"dash\" that advances two states at once. It also fails ${fmt(DASH_P, 2)} of the " +
            "time at a cost of ${fmt(DASH_COST, 1)} — a cost that appears in no segment a rater is shown. Across " +
            "the ${rm.segments.size} segments collected here, what the rater sees is on average " +
            "${fmt(observedGap, 3)} better than what actually happened.",
        bars = listOf(
            OffBar(
                "mean return over the collected segments",
                listOf(rm.segments.map { it.observed }.average().toFloat(), rm.segments.map { it.truth }.average().toFloat()),
                AccentColor,
                listOf("as rated", "as it truly was"),
            ),
        ),
        readout = "preferences are labelled on the left bar; the right bar is what we care about",
    )
    frames += OffFrame(
        status = "A Bradley-Terry reward model is fitted to those pairwise comparisons, 10% of which are mislabelled. " +
            "It agrees with the raters on ${fmt(rm.agreement * 100, 1)}% of held-out pairs — by the only metric " +
            "available at training time, a good reward model. It scores dashing at ${fmt(rm.w[0], 3)} and reaching " +
            "the goal at ${fmt(rm.w[2], 3)}.",
        bars = listOf(
            OffBar(
                "learned reward weights",
                listOf(rm.w[0].toFloat(), rm.w[1].toFloat(), rm.w[2].toFloat()),
                HighlightColor,
                listOf("dash", "step right", "reached goal"),
            ),
        ),
        readout = "held-out agreement with rater preferences: ${fmt(rm.agreement * 100, 1)}%",
    )
    frames += OffFrame(
        status = "Optimise against it as hard as possible — a KL weight of ${fmt(betas.first(), 2)} — and the proxy " +
            "reward climbs to its maximum, ${fmt(reference.proxy, 3)} → ${fmt(evals.first().proxy, 3)}. True " +
            "return over the same policy: ${fmt(evals.first().truth, 3)}, which is no better than the untuned " +
            "reference it started from (${fmt(reference.truth, 3)}). All that optimisation pressure went into " +
            "dashing ${fmt(evals.first().dashes, 2)} times an episode, because the reward model has no term for " +
            "what dashing costs.",
        bars = listOf(
            OffBar(
                "reward-model score",
                listOf(reference.proxy.toFloat(), evals.first().proxy.toFloat()),
                HighlightColor,
                listOf("reference", "optimised"),
            ),
            OffBar(
                "true return",
                listOf(reference.truth.toFloat(), evals.first().truth.toFloat()),
                WarnColor,
                listOf("reference", "optimised"),
            ),
        ),
        readout = "the proxy went up; the thing it stands for did not",
    )
    frames += OffFrame(
        status = "Sweeping the KL weight puts the whole problem on one chart, and the two curves peak in different " +
            "places: the proxy is maximised at β = ${fmt(betas[bestProxy], 2)}, the true return at " +
            "β = ${fmt(betas[bestTrue], 2)}. Optimising the measure past that point degrades the thing it was " +
            "measuring. That is Goodhart's law with numbers attached, and it is the entire reason the KL term is " +
            "in the objective.",
        plot = OffPlot(
            "proxy against truth, by KL weight",
            listOf(
                OffCurve("reward-model score", evals.map { it.proxy.toFloat() }, HighlightColor),
                OffCurve("true return", evals.map { it.truth.toFloat() }, ImprovedColor),
            ),
            -0.3f..1.3f,
            "β = " + betas.joinToString(" · ") { fmt(it, 2) },
        ),
        readout = "best proxy at β=${fmt(betas[bestProxy], 2)}, best truth at β=${fmt(betas[bestTrue], 2)}",
    )
    frames += OffFrame(
        status = "At the best setting the policy still improves on the reference — ${fmt(reference.truth, 3)} to " +
            "${fmt(evals[bestTrue].truth, 3)} — and dashes ${fmt(evals[bestTrue].dashes, 2)} times an episode " +
            "instead of ${fmt(evals.first().dashes, 2)}. β is not a safety margin bolted on afterwards; it is the " +
            "admission that the reward model is only trustworthy near the data it was fitted on.",
        bars = listOf(
            OffBar(
                "true return by KL weight",
                evals.map { it.truth.toFloat() },
                ImprovedColor,
                betas.map { fmt(it, 2) },
            ),
            OffBar(
                "dashes per episode",
                evals.map { it.dashes.toFloat() },
                WarnColor,
                betas.map { fmt(it, 2) },
            ),
        ),
        readout = "reference ${fmt(reference.truth, 3)} → best ${fmt(evals[bestTrue].truth, 3)} at β=${fmt(betas[bestTrue], 2)}",
    )
    return frames
}

// ═════════════════════════════════════════════════════════════════════════════
// Config
// ═════════════════════════════════════════════════════════════════════════════

private val comparisonLegend = listOf(
    BaselineColor to "Baseline",
    ImprovedColor to "Improved",
    WarnColor to "Failure mode",
)

private val offlineConfigs = mapOf(
    "offline_rl" to OffConfig(
        intro = "One logged dataset, no further interaction. What that buys you, and the two ways it bites back — " +
            "measured on a chain that hides a worthless lottery action.",
        legend = comparisonLegend,
        build = ::offlineRlFrames,
    ),
    "cql" to OffConfig(
        intro = "A conservative penalty applied to a real overestimate, swept across its strength — including the " +
            "dataset where the same penalty makes things worse.",
        legend = comparisonLegend,
        build = ::cqlFrames,
    ),
    "decision_transformer" to OffConfig(
        intro = "Return-conditioned sequence modelling on a deliberately mixed log: what a target return controls, " +
            "and the ceiling it cannot pass.",
        legend = listOf(
            BaselineColor to "Target",
            ImprovedColor to "Achieved",
            AccentColor to "Dataset",
        ),
        build = ::decisionTransformerFrames,
    ),
    "imitation_learning" to OffConfig(
        intro = "Clean demonstrations, noisy execution. Behaviour cloning's compounding error measured on a " +
            "corridor, then repaired by relabelling the states the learner actually reaches.",
        legend = comparisonLegend,
        build = ::imitationFrames,
    ),
    "gail" to OffConfig(
        intro = "Imitation as a distribution-matching game: a discriminator that separates expert from agent, and " +
            "the occupancy distance closing until it cannot.",
        legend = comparisonLegend,
        build = ::gailFrames,
    ),
    "irl" to OffConfig(
        intro = "Recovering the objective instead of the actions — by matching state visitation — and then the test " +
            "that only a reward can pass: the environment changes.",
        legend = listOf(
            HighlightColor to "Recovered reward",
            ImprovedColor to "Improved",
            WarnColor to "Mismatch",
        ),
        build = ::irlFrames,
    ),
    "rlhf" to OffConfig(
        intro = "A reward model fitted to preferences that were labelled on what a rater can see, then optimised " +
            "against at nine different KL weights. The two curves do not peak in the same place.",
        legend = listOf(
            HighlightColor to "Proxy reward",
            ImprovedColor to "True return",
            WarnColor to "Hidden cost",
        ),
        build = ::rlhfFrames,
    ),
)

private fun offlineConfigFor(topicId: String): OffConfig =
    offlineConfigs[topicId] ?: offlineConfigs.getValue("offline_rl")

// ═════════════════════════════════════════════════════════════════════════════
// UI
// ═════════════════════════════════════════════════════════════════════════════

@Composable
fun OfflineRlSection(topicId: String) {
    val config = remember(topicId) { offlineConfigFor(topicId) }
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

            frame.grid?.let { CorridorGrid(it, modifier = Modifier.padding(top = 12.dp)) }
            frame.table?.let { CountTable(it, modifier = Modifier.padding(top = 12.dp)) }
            frame.plot?.let { OffPlotCanvas(it, modifier = Modifier.padding(top = 12.dp)) }
            frame.bars.forEach { bar -> OffBars(bar, modifier = Modifier.padding(top = 12.dp)) }

            frame.readout?.let { ReadoutChips(it, Modifier.padding(top = 14.dp)) }

            LabCaption(frame.status, Modifier.padding(top = 14.dp))

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                config.legend.forEach { (color, label) -> OffLegend(color, label) }
            }

            PlaybackTransport(playback, captions = frames.map { it.status })
        }
    }
}

@Composable
private fun OffLegend(color: Color, label: String) {
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
private fun CorridorGrid(grid: OffGrid, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(grid.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
                // Cells take their height from the grid's aspect ratio; a weighted row with no
                // intrinsic height renders as an invisible strip.
                .aspectRatio(COLS.toFloat() / ROWS.toFloat()),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            for (r in 0 until ROWS) {
                Row(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    for (c in 0 until COLS) {
                        val cellData = grid.cells[cell(r, c)]
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .background(
                                    cellData.color.copy(alpha = 0.12f + 0.68f * cellData.shade.coerceIn(0f, 1f)),
                                    RoundedCornerShape(6.dp),
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (cellData.glyph.isNotEmpty()) {
                                Text(
                                    cellData.glyph,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CountTable(table: OffTable, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(table.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Box(modifier = Modifier.weight(0.6f))
            table.columnLabels.forEach {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        table.rows.forEachIndexed { r, row ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    table.rowLabels[r],
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(0.6f),
                )
                row.forEachIndexed { c, value ->
                    val marked = (r * table.columnLabels.size + c) in table.highlight
                    Box(modifier = Modifier.weight(1f).padding(horizontal = 2.dp)) {
                        Text(
                            value,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (marked) FontWeight.Bold else FontWeight.Normal,
                            color = if (marked) WarnColor else MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (marked) WarnColor.copy(alpha = 0.14f) else Color.Transparent,
                                    RoundedCornerShape(4.dp),
                                )
                                .padding(vertical = 2.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OffPlotCanvas(plot: OffPlot, modifier: Modifier = Modifier) {
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
private fun OffBars(bar: OffBar, modifier: Modifier = Modifier) {
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
