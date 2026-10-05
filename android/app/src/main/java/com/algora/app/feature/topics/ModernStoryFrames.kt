package com.algora.app.feature.topics

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tanh

// ── Modern-architecture and training-technique storyboard frames ─────────────
// KANs, neural ODEs, GCNs, GATs, capsule networks, Siamese networks, layer and group normalisation,
// early stopping and data augmentation, drawn by DeepStoryLabs.kt. Every fit, routing round and
// accuracy is run here: the MLP is trained with Adam, the spline solved by least squares, the
// regression by gradient descent. Seeds come from the same plain LCG as the other labs, so the iOS
// port (ModernStoryFrames.swift) reproduces the numbers.

internal val modernStoryTopicIds = setOf(
    "kan", "neural_odes", "gcn", "gat", "capsule_networks", "siamese_networks",
    "layer_normalization", "group_normalization", "early_stopping", "data_augmentation",
)

internal fun modernLab(topicId: String): DkLab? = when (topicId) {
    "kan" -> kanLab()
    "neural_odes" -> odeLab()
    "gcn" -> gcnLab()
    "gat" -> gatLab()
    "capsule_networks" -> capsuleLab()
    "siamese_networks" -> siameseLab()
    "layer_normalization" -> layerNormLab()
    "group_normalization" -> groupNormLab()
    "early_stopping" -> earlyStopLab()
    "data_augmentation" -> augmentLab()
    else -> null
}

private fun n(v: Double, d: Int = 2) = dkNum(v, d)

private fun pct(share: Double, d: Int = 0) = n(share * 100, d) + "%"

private fun legend(ink: DkInk, label: String, style: SwatchStyle = SwatchStyle.Fill) = DkLegend(ink, style, label)

private fun stepActions(frames: List<DkFrame>, action: String) =
    frames.mapIndexed { i, f -> DkFrame(f.header, f.stage, f.legend, f.formula, f.headline, f.body, f.chips, if (i == frames.lastIndex) "Start Over" else action) }

private fun plain(frames: List<DkFrame>) = frames.map { DkFrame(it.header, it.stage, it.legend, it.formula, it.headline, it.body, it.chips, "Next") }

private class ModRng(seed: Long) {
    private var s = seed
    fun u(): Double {
        s = (s * 1103515245L + 12345L) and 0x7fffffffL
        return s / 2147483648.0
    }

    fun g(): Double {
        val a = max(u(), 1e-12)
        val b = u()
        return sqrt(-2 * ln(a)) * cos(2 * PI * b)
    }
}

// ── KAN vs MLP ──

private val kanX = List(100) { -2 + 4.0 * it / 99 }
private fun kanTarget(x: Double) = sin(5 * x) * exp(-x * x / 2)
private val kanY = kanX.map { kanTarget(it) }

private fun rmse(pred: List<Double>) = sqrt(pred.indices.sumOf { (pred[it] - kanY[it]).pow(2) } / pred.size)

/** A 1-6-1 tanh MLP (19 weights) trained by full-batch Adam; its predictions on [kanX]. */
private fun trainMlp(seed: Long): List<Double> {
    val rng = ModRng(seed)
    val h = 6
    val p = DoubleArray(3 * h + 1)
    for (j in 0 until h) {
        p[j] = rng.g() * 3
        p[h + j] = rng.g()
        p[2 * h + j] = rng.g() * 0.5
    }
    p[3 * h] = 0.0
    val m = DoubleArray(p.size)
    val v = DoubleArray(p.size)
    fun predict(x: Double) = (0 until h).sumOf { p[2 * h + it] * tanh(p[it] * x + p[h + it]) } + p[3 * h]
    val g = DoubleArray(p.size)
    val acts = DoubleArray(h)
    val count = kanX.size.toDouble()
    for (t in 1..3000) {
        g.fill(0.0)
        for (i in kanX.indices) {
            val x = kanX[i]
            var out = 0.0
            for (j in 0 until h) { acts[j] = tanh(p[j] * x + p[h + j]); out += p[2 * h + j] * acts[j] }
            val err = out + p[3 * h] - kanY[i]
            val d = 2 * err / count
            for (j in 0 until h) {
                val back = d * p[2 * h + j] * (1 - acts[j] * acts[j])
                g[j] += back * x
                g[h + j] += back
                g[2 * h + j] += d * acts[j]
            }
            g[3 * h] += d
        }
        val c1 = 1 - 0.9.pow(t)
        val c2 = 1 - 0.999.pow(t)
        for (k in p.indices) {
            m[k] = 0.9 * m[k] + 0.1 * g[k]
            v[k] = 0.999 * v[k] + 0.001 * g[k] * g[k]
            p[k] -= 0.02 * (m[k] / c1) / (sqrt(v[k] / c2) + 1e-8)
        }
    }
    return kanX.map { predict(it) }
}

/** A piecewise-linear spline on [knots] uniform knots, fitted by least squares; returns knot values. */
private fun fitSpline(knots: Int): List<Double> {
    val step = 4.0 / (knots - 1)
    fun basis(x: Double, k: Int) = max(0.0, 1 - abs((x - (-2 + k * step)) / step))
    val a = Array(knots) { DoubleArray(knots) }
    val b = DoubleArray(knots)
    kanX.forEachIndexed { i, x ->
        for (r in 0 until knots) {
            val br = basis(x, r)
            if (br == 0.0) continue
            b[r] += br * kanY[i]
            for (c in 0 until knots) a[r][c] += br * basis(x, c)
        }
    }
    // Gaussian elimination with partial pivoting.
    for (col in 0 until knots) {
        val piv = (col until knots).maxBy { abs(a[it][col]) }
        val tmp = a[col]; a[col] = a[piv]; a[piv] = tmp
        val tb = b[col]; b[col] = b[piv]; b[piv] = tb
        for (r in col + 1 until knots) {
            val f = a[r][col] / a[col][col]
            for (c in col until knots) a[r][c] -= f * a[col][c]
            b[r] -= f * b[col]
        }
    }
    val sol = DoubleArray(knots)
    for (r in knots - 1 downTo 0) sol[r] = (b[r] - (r + 1 until knots).sumOf { a[r][it] * sol[it] }) / a[r][r]
    return sol.toList()
}

private val kanRuns: Triple<List<List<Double>>, List<Double>, List<Double>> by lazy {
    val mlps = listOf(1L, 2L, 3L).map { trainMlp(it) }
    val knots = fitSpline(20)
    val step = 4.0 / 19
    val spline = kanX.map { x -> knots.indices.sumOf { k -> knots[k] * max(0.0, 1 - abs((x - (-2 + k * step)) / step)) } }
    Triple(mlps, spline, knots)
}

private fun kanLab(): DkLab = DkLab(DkControl.Tabs, tabs = listOf("MLP", "KAN", "Both"), initialTab = 2) { tab, _ ->
    val (mlps, spline, knots) = kanRuns
    val mlpErr = mlps.map { rmse(it) }
    val kanErr = rmse(spline)
    val lo = mlpErr.min()
    val hi = mlpErr.max()
    val target = DkLine(kanX.map { DkP(it, kanTarget(it)) }, DkInk.Grey)
    val mlpLine = DkLine(kanX.mapIndexed { i, x -> DkP(x, mlps[0][i]) }, DkInk.Pink)
    val kanLine = DkLine(kanX.mapIndexed { i, x -> DkP(x, spline[i]) }, DkInk.Blue, dashed = true)
    val knotDots = knots.mapIndexed { k, v -> DkDot(DkP(-2 + k * 4.0 / 19, v), DkInk.Blue, 3.5f) }
    fun plot(showKnots: Boolean = false) = DkPlot(
        -2.05 to 2.05, -1.15 to 1.15, emptyList(), "x = −2", "+2",
        listOf(target) + (if (tab != 1) listOf(mlpLine) else emptyList()) + (if (tab != 0) listOf(kanLine) else emptyList()),
        dots = if (showKnots && tab != 0) knotDots else emptyList(), rules = listOf(DkRule(0.0, DkInk.Grey, thin = true)), axis = false,
    )
    val header = "fit to sin(5x)·e^(−x²/2) · 100 points"
    val legend = listOf(legend(DkInk.Grey, "Target", SwatchStyle.Line)) +
        (if (tab != 1) listOf(legend(DkInk.Pink, "MLP 1-6-1, seed 1", SwatchStyle.Line)) else emptyList()) +
        (if (tab != 0) listOf(legend(DkInk.Blue, "KAN edge, 20 knots", SwatchStyle.DashedLine)) else emptyList())
    val mlpLine1 = "MLP 1-6-1 (19 weights), 3 seeds · RMSE ${n(lo, 3)}–${n(hi, 3)}"
    val kanLine1 = "KAN edge, 20 knots · RMSE {${n(kanErr, 3)}} every time"
    val similar = hi < kanErr * 3 && lo > kanErr / 3
    val frames = listOf(
        DkFrame(
            header, plot(), legend,
            when (tab) {
                0 -> listOf("h = tanh(w·x + b) for 6 units, y = Σ v·h + c", "seed 1 · RMSE {${n(mlpErr[0], 3)}}")
                1 -> listOf("y = φ(x), φ a spline through 20 knots", "RMSE {${n(kanErr, 3)}}")
                else -> listOf(mlpLine1, kanLine1)
            },
            when (tab) {
                0 -> "A 1-6-1 MLP with {19} weights fits it to RMSE ${n(mlpErr[0], 3)}."
                1 -> "One learnable spline edge, {20} numbers, fits it to RMSE ${n(kanErr, 3)}."
                else -> if (similar) "Same budget, similar error, but the KAN fit is {the same every time}."
                else "Same budget, and the KAN fit is {${n(lo / kanErr, 1)}×} closer and the same every time."
            },
            when (tab) {
                0 -> "Fixed tanh curves on the nodes, learned weights on the edges: it has to bend six S-curves into four wiggles."
                1 -> "A KAN moves the learning onto the edge: the curve itself is the parameter, a value at each knot."
                else -> "Its learnable part is the spline on the edge, so fitting it is least squares. The MLP's error varies ${n(hi / lo, 1)}× across random seeds."
            },
        ),
        DkFrame(
            header, plot(), legend, listOf(mlpLine1, kanLine1),
            if (tab == 1) "The spline fit is {least squares}: no seed, no learning rate." else "Three seeds give RMSE {${n(lo, 3)}–${n(hi, 3)}}.",
            if (tab == 1) "With the knots fixed the problem is linear in the knot values, so there is exactly one best answer."
            else "Gradient descent on a non-convex loss lands somewhere different from each random start.",
        ),
        DkFrame(
            header, plot(showKnots = true), legend + if (tab != 0) listOf(legend(DkInk.Blue, "Knot", SwatchStyle.Dot)) else emptyList(),
            listOf("KAN: a learned φ on every edge, sums at nodes", "MLP: a fixed activation on every node, weights on edges"),
            "A KAN edge is a curve you can {read off}.",
            "That interpretability is the main claim for KANs. The cost: splines on every edge make large KANs slower to train than MLPs.",
        ),
    )
    stepActions(frames, "Next")
}

// ── Neural ODEs ──

private val odeSteps = listOf(1, 2, 4, 8, 16, 32)

private fun odeLab(): DkLab = DkLab(DkControl.StepperOnly, stepper = DkStepper("steps", odeSteps.map { it.toDouble() }, odeSteps.indexOf(4)) { n(it, 0) }) { _, p ->
    val steps = odeSteps[p]
    val h = 2.0 / steps
    val euler = List(steps + 1) { k -> DkP(k * h, (1 - h).pow(k)) }
    val rkFactor = 1 - h + h * h / 2 - h.pow(3) / 6 + h.pow(4) / 24
    val rk = List(steps + 1) { k -> DkP(k * h, rkFactor.pow(k)) }
    val exact = exp(-2.0)
    val eErr = abs(euler.last().y - exact)
    val rErr = abs(rk.last().y - exact)
    val exactLine = DkLine((0..100).map { DkP(it * 0.02, exp(-it * 0.02)) }, DkInk.Green)
    fun plot(withRk: Boolean) = DkPlot(
        0.0 to 2.0, -0.04 to 1.05, listOf(1.0 to "1", 0.5 to "0.5", 0.0 to "0"), "t = 0", "t = 2",
        listOf(exactLine, DkLine(euler, DkInk.Pink)),
        dots = euler.map { DkDot(it, DkInk.Pink, 3.5f) } + if (withRk) rk.map { DkDot(it, DkInk.Violet, 4f) } else emptyList(),
        axis = false,
    )
    val header = "dz/dt = −z, z(0) = 1"
    val legend = listOf(legend(DkInk.Green, "Exact e^−t", SwatchStyle.Line), legend(DkInk.Pink, "Euler = $steps residual block${if (steps > 1) "s" else ""}"), legend(DkInk.Violet, "RK4"))
    val eulerLine = "Euler: z ← z + h·f(z) = z·(1 − ${n(h, if (h < 0.1) 4 else 2).trimEnd('0').trimEnd('.')}) → z(2) = ${n(euler.last().y, 4)}"
    plain(
        listOf(
            DkFrame(
                header, plot(false), legend.take(2), listOf(eulerLine, "exact e^−2 = ${n(exact, 4)}"),
                "Each residual block is one {Euler step}: z ← z + h·f(z).",
                "A ResNet with $steps block${if (steps > 1) "s" else ""} follows the curve in $steps straight jump${if (steps > 1) "s" else ""} of size h = ${n(h, 3)}.",
            ),
            DkFrame(
                header, plot(true), legend, listOf(eulerLine, "exact ${n(exact, 4)} · Euler error ${n(eErr, 4)} · RK4 {${n(rErr, 5)}}"),
                "${if (steps == 1) "One ResNet-style step misses" else "$steps ResNet-style steps miss"} by {${pct(eErr / exact)}}; RK4 by ${pct(rErr / exact, 1)}.",
                "x + f(x) is one Euler step. A neural ODE learns f and lets an adaptive solver choose how many steps to take.",
            ),
            DkFrame(
                header, plot(true), legend, listOf("depth → continuous time: dz/dt = f(z, t; θ)", "adjoint method: memory constant in the number of steps"),
                "A neural ODE has {no fixed depth}.",
                "The solver takes as many steps as accuracy needs, more where the dynamics change fast. Gradients come from solving a second ODE backwards.",
            ),
        ),
    )
}

// ── Graphs: GCN and GAT ──

private val gPos = listOf(0.12f to 0.12f, 0.12f to 0.88f, 0.45f to 0.5f, 0.62f to 0.5f, 0.92f to 0.12f, 0.92f to 0.88f)
private val gEdges = listOf(0 to 1, 0 to 2, 1 to 2, 2 to 3, 3 to 4, 3 to 5, 4 to 5)
private val gX = listOf(1.96, 2.11, 1.80, -2.09, -1.95, -2.02)

private fun neighbours(i: Int) = gEdges.mapNotNull { (a, b) -> if (a == i) b else if (b == i) a else null }

private fun deg(i: Int) = neighbours(i).size + 1

private fun gcnStep(x: List<Double>): List<Double> =
    x.indices.map { i -> (listOf(i) + neighbours(i)).sumOf { j -> x[j] / sqrt(deg(i).toDouble() * deg(j)) } }

private fun gcnLab(): DkLab = DkLab(DkControl.Track) { _, _ ->
    val h1 = gcnStep(gX)
    val h2 = gcnStep(h1)
    fun stage(values: List<Double>, hot: Int?) = DkGraph(
        gPos.mapIndexed { i, (x, y) -> DkGNode(x, y, "$i", n(values[i]), if (i < 3) DkInk.Blue else DkInk.Orange, ring = i == hot) },
        gEdges.map { (a, b) -> DkGEdge(a, b, hot = hot != null && (a == hot || b == hot), weight = 0.4) },
    )
    fun sumLine(i: Int): List<String> {
        val terms = (listOf(i) + neighbours(i)).joinToString(" + ") { j -> "${n(1 / sqrt(deg(i).toDouble() * deg(j)), 3)}·${n(gX[j])}" }
        return listOf("h${"₀₁₂₃₄₅"[i]} = $terms", "= {${n(h1[i])}} · weights 1/√(dᵢdⱼ) incl. self-loop")
    }
    fun gap(v: List<Double>) = v.take(3).average() - v.drop(3).average()
    val header = "feature 1 after one GCN layer"
    val legend = listOf(legend(DkInk.Blue, "Triangle A"), legend(DkInk.Orange, "Triangle B"), legend(DkInk.Yellow, "Edges into the node", SwatchStyle.Line))
    val frames = listOf(
        DkFrame(
            "feature 1 before any layer", stage(gX, null), legend.take(2), listOf("x = ${gX.joinToString(", ", "[", "]") { n(it) }}", "two triangles joined by the bridge 2 — 3"),
            "Two {triangles} share one bridge edge.",
            "Triangle A's nodes carry about +2, triangle B's about −2. A GCN layer lets each node look at its neighbours.",
        ),
        DkFrame(
            "$header · node 0 highlighted", stage(h1, 0), legend, sumLine(0),
            "Node 0 averages with its triangle: {${n(gX[0])} → ${n(h1[0])}}.",
            "All its neighbours agree with it, so averaging changes little.",
        ),
        DkFrame(
            "$header · node 2 highlighted", stage(h1, 2), legend, sumLine(2),
            "The bridge node 2 is pulled toward B: {${n(gX[2])} → ${n(h1[2])}}.",
            "Each node averages itself with its neighbours. Within-triangle nodes grow alike; across the bridge the gap between the triangles' means shrinks from ${n(gap(gX))} to ${n(gap(h1))}.",
        ),
        DkFrame(
            "$header · node 3 highlighted", stage(h1, 3), legend, sumLine(3),
            "Node 3 is pulled toward A just as much: {${n(gX[3])} → ${n(h1[3])}}.",
            "The normalisation 1/√(dᵢdⱼ) weights a neighbour less when either end has many links.",
        ),
        DkFrame(
            header, stage(h1, null), legend.take(2), listOf("H′ = D^−½ (A + I) D^−½ H W", "gap between triangle means: ${n(gap(gX))} → {${n(gap(h1))}}"),
            "One layer, every node at once: {Â·X}.",
            "It's one sparse matrix product. A learned W (identity here) then mixes features, and a nonlinearity follows.",
        ),
        DkFrame(
            "feature 1 after two GCN layers", stage(h2, null), legend.take(2), listOf("two layers: Â²X reaches 2 hops", "gap: ${n(gap(h1))} → {${n(gap(h2))}}"),
            "A second layer {blurs} the two triangles further.",
            "Each layer widens the receptive field by one hop but also averages away differences. Stack too many and every node looks the same: oversmoothing.",
        ),
    )
    stepActions(frames, "Next Node")
}

private fun leaky(z: Double) = if (z > 0) z else 0.2 * z

private fun gatWeights(x: List<Double>, i: Int): Map<Int, Double> {
    val js = listOf(i) + neighbours(i)
    val e = js.map { j -> leaky(0.4 * x[i] + 0.9 * x[j]) }
    val m = e.max()
    val ex = e.map { exp(it - m) }
    return js.mapIndexed { k, j -> j to ex[k] / ex.sum() }.toMap()
}

private fun gatLab(): DkLab = DkLab(DkControl.Tabs, tabs = listOf("GCN", "GAT"), initialTab = 1) { tab, _ ->
    val gat = tab == 1
    val centre = 2
    val moved = gX.mapIndexed { i, v -> if (i == 3) v + 3 else v }
    fun weights(x: List<Double>): Map<Int, Double> =
        if (gat) gatWeights(x, centre) else (listOf(centre) + neighbours(centre)).associateWith { j -> 1 / sqrt(deg(centre).toDouble() * deg(j)) }
    val before = weights(gX)
    val after = weights(moved)
    fun stage(w: Map<Int, Double>, perturbed: Boolean) = DkGraph(
        gPos.mapIndexed { i, (x, y) ->
            DkGNode(x, y, "$i", w[i]?.let { n(it) }, if (i == 3 && perturbed) DkInk.Green else null, ring = i == centre)
        },
        gEdges.map { (a, b) ->
            val other = if (a == centre) b else if (b == centre) a else null
            DkGEdge(a, b, hot = other != null, weight = other?.let { w[it] } ?: 0.0)
        },
    )
    val kind = if (gat) "GAT" else "GCN"
    val gcnW = 1 / sqrt(deg(centre).toDouble() * deg(3))
    val header = "${if (gat) "attention" else "weights"} from node 2${if (gat) "" else ", fixed by degree"}"
    val legend = listOf(legend(DkInk.Yellow, "Centre", SwatchStyle.Ring), legend(DkInk.Green, "Perturbed neighbour"), legend(DkInk.Yellow, "Edge width = weight", SwatchStyle.Line))
    val frames = listOf(
        DkFrame(
            header, stage(before, false), listOf(legend[0], legend[2]),
            if (gat) listOf("e_ij = LeakyReLU(a·[h_i ‖ h_j]) · α = softmax over neighbours", "weight on 3: {${n(before[3]!!, 3)}}")
            else listOf("w_ij = 1/√(dᵢdⱼ)", "weight on 3: {${n(before[3]!!, 3)}}"),
            if (gat) "GAT {scores} each neighbour from the two nodes' features." else "GCN's weights come from {degrees} alone.",
            if (gat) "Node 3's feature (${n(gX[3])}) is unlike node 2's, so its edge scores low." else "Two nodes with the same number of links always get the same say, whatever they carry.",
        ),
        DkFrame(
            "${header} after shifting node 3's feature by +3", stage(after, true), legend,
            listOf("GCN weight on 3: ${n(gcnW, 3)} before and after", "$kind weight on 3: ${n(before[3]!!, 3)} → {${n(after[3]!!, 3)}}"),
            if (gat) "GAT moves node 3's weight from ${n(before[3]!!)} to {${n(after[3]!!)}}; GCN can't move it."
            else "GCN's weight on node 3 stays {${n(gcnW, 3)}}: the change is ignored.",
            if (gat) "Attention scores each edge from the two nodes' features, so a neighbour that changes gets a different say."
            else "The aggregation still sees node 3's new value, but how much it counts never changes. Switch to GAT to compare.",
        ),
        DkFrame(
            "${header} after shifting node 3's feature by +3", stage(after, true), legend,
            listOf("multi-head GAT: K heads, outputs concatenated", "cost: one score per edge, O(|E|)"),
            if (gat) "Real GATs run {several heads} and concatenate them." else "GCN is {cheaper}: no scores to compute.",
            if (gat) "Like transformer attention restricted to the graph's edges: each head can favour a different kind of neighbour."
            else "On graphs where neighbours really are interchangeable, the fixed weights do just as well.",
        ),
    )
    stepActions(frames, "Next")
}

// ── Capsule networks: routing by agreement ──

private val votesA = listOf(1.0 to 0.35, 0.9 to 0.5, -0.75 to 0.7)
private val votesB = listOf(-0.3 to 0.4, 0.2 to -0.5, -0.9 to 0.8)

private fun squash(s: Pair<Double, Double>): Pair<Double, Double> {
    val n2 = s.first * s.first + s.second * s.second
    val k = n2 / (1 + n2) / sqrt(n2 + 1e-12)
    return s.first * k to s.second * k
}

private class Routing(val shareA: List<Double>, val vA: Pair<Double, Double>)

private fun route(rounds: Int): Routing {
    val bA = DoubleArray(3)
    val bB = DoubleArray(3)
    fun shares() = (0 until 3).map { i -> 1 / (1 + exp(bB[i] - bA[i])) }
    fun outputs(c: List<Double>): Pair<Pair<Double, Double>, Pair<Double, Double>> {
        val sA = (0 until 3).fold(0.0 to 0.0) { acc, i -> (acc.first + c[i] * votesA[i].first) to (acc.second + c[i] * votesA[i].second) }
        val sB = (0 until 3).fold(0.0 to 0.0) { acc, i -> (acc.first + (1 - c[i]) * votesB[i].first) to (acc.second + (1 - c[i]) * votesB[i].second) }
        return squash(sA) to squash(sB)
    }
    repeat(rounds) {
        val (vA, vB) = outputs(shares())
        for (i in 0 until 3) {
            bA[i] += votesA[i].first * vA.first + votesA[i].second * vA.second
            bB[i] += votesB[i].first * vB.first + votesB[i].second * vB.second
        }
    }
    val c = shares()
    return Routing(c, outputs(c).first)
}

private fun capsuleLab(): DkLab = DkLab(DkControl.StepperOnly, stepper = DkStepper("rounds", (0..5).map { it.toDouble() }, 3) { n(it, 0) }) { _, p ->
    val r = route(p)
    val r0 = route(0)
    fun len(v: Pair<Double, Double>) = sqrt(v.first * v.first + v.second * v.second)
    val votesPlot = DkPlot(
        -0.95 to 1.15, -0.15 to 0.95, emptyList(), "", "",
        votesA.mapIndexed { i, (x, y) -> DkLine(listOf(DkP(0.0, 0.0), DkP(x, y)), if (i == 2) DkInk.Pink else DkInk.Blue) } +
            DkLine(listOf(DkP(0.0, 0.0), DkP(r.vA.first, r.vA.second)), DkInk.Yellow, dashed = true),
        axis = false,
    )
    val rows = DkRows(r.shareA.mapIndexed { i, s -> DkRow("vote ${i + 1}", "", listOf(DkBar(s, if (i == 2) DkInk.Pink else DkInk.Blue, n(s))), inline = true) }, caption = "share routed to A")
    val legend = listOf(legend(DkInk.Blue, "Agreeing votes", SwatchStyle.Line), legend(DkInk.Pink, "Outlier", SwatchStyle.Line), legend(DkInk.Yellow, "Output v_A", SwatchStyle.DashedLine))
    val shares0 = r0.shareA.joinToString(", ") { n(it) }
    val sharesR = r.shareA.joinToString(", ") { n(it) }
    plain(
        listOf(
            DkFrame(
                "votes for capsule A · ${p} round${if (p == 1) "" else "s"} of routing", votesPlot, legend,
                listOf("û_i = W_i · u_i: each lower capsule predicts A's pose", "v_A = squash(Σ c_i·û_i) · |v_A| = {${n(len(r.vA))}}"),
                "Two votes for A {agree}; the third points elsewhere.",
                "A capsule outputs a vector: its direction is the pose, its length the probability the entity is there.",
            ),
            DkFrame(
                "votes for capsule A · share routed to A", rows, legend.take(2),
                listOf("b ← b + û·v · c = softmax over parents A, B", "share to A: $shares0 → {$sharesR}"),
                if (p == 0) "Before routing, every vote splits {50 / 50}." else "The outlier sends only {${pct(r.shareA[2])}} of its vote to A after $p round${if (p == 1) "" else "s"}.",
                "Routing-by-agreement moves each vote toward the parent it agrees with. A's length rises to ${n(len(r.vA))}, against ${n(len(r0.vA))} with equal weights.",
            ),
            DkFrame(
                "votes for capsule A · ${p} round${if (p == 1) "" else "s"} of routing", votesPlot, legend,
                listOf("routing: iterative, no learned weights of its own", "CapsNet on MNIST: 3 rounds"),
                "Routing is {inference-time} clustering of votes.",
                "It replaces max-pooling's \"keep the loudest\" with \"keep what agrees\", which preserves pose. The iterations are why capsules never scaled well.",
            ),
        ),
    )
}

// ── Siamese networks ──

private class SiamesePoint(val cls: Int, val s1: Double, val s2: Double, val noise: Double)

private val siamese: List<SiamesePoint> by lazy {
    val rng = ModRng(321)
    val centres = listOf(2.0 to 1.5, 2.0 to -1.5, -2.0 to 1.5, -2.0 to -1.5)
    centres.flatMapIndexed { c, (x, y) -> List(10) { SiamesePoint(c, x + 0.45 * rng.g(), y + 0.45 * rng.g(), 3 * rng.g()) } }
}

private fun siameseLab(): DkLab = DkLab(DkControl.Tabs, tabs = listOf("Raw", "Embedded"), initialTab = 1) { tab, _ ->
    val pts = siamese
    // The learned metric: each axis weighted by 1/(within-class spread), fit on classes 0–2 only.
    fun within(f: (SiamesePoint) -> Double) = (0..2).flatMap { c -> pts.filter { it.cls == c }.let { g -> val m = g.map(f).average(); g.map { (f(it) - m).pow(2) } } }.average()
    val ws = listOf(1 / sqrt(within { it.s1 }), 1 / sqrt(within { it.s2 }), 1 / sqrt(within { it.noise }))
    val noiseW = ws[2] / ((ws[0] + ws[1]) / 2)
    val w = if (tab == 1) listOf(1.0, 1.0, noiseW) else listOf(1.0, 1.0, 1.0)
    fun dist(a: SiamesePoint, b: SiamesePoint, wt: List<Double>) =
        sqrt((wt[0] * (a.s1 - b.s1)).pow(2) + (wt[1] * (a.s2 - b.s2)).pow(2) + (wt[2] * (a.noise - b.noise)).pow(2))
    fun oneShot(wt: List<Double>): Double {
        val support = (0..3).map { c -> pts.first { it.cls == c } }
        val queries = pts.filter { it.cls == 3 } - support[3]
        return queries.count { q -> support.minBy { dist(q, it, wt) }.cls == 3 }.toDouble() / queries.size
    }
    val rawAcc = oneShot(listOf(1.0, 1.0, 1.0))
    val embAcc = oneShot(listOf(1.0, 1.0, noiseW))
    val inks = listOf(DkInk.Blue, DkInk.Orange, DkInk.Green, DkInk.Pink)
    val span = pts.maxOf { abs(it.s2 + w[2] * it.noise) } * 1.15
    val plot = DkPlot(
        -3.6 to 3.6, -span to span, emptyList(), "", "", emptyList(),
        dots = pts.flatMap { p ->
            val y = p.s2 + w[2] * p.noise
            val d = DkDot(DkP(p.s1, y), inks[p.cls], 4f)
            if (p.cls == 3) listOf(DkDot(DkP(p.s1, y), DkInk.Yellow, 5.5f), d) else listOf(d)
        },
        axis = false,
    )
    val header = if (tab == 1) "embedding fit on classes 0–2 · class 3 never seen" else "raw features · x = signal 1, y = signal 2 + noise"
    val legend = listOf(legend(DkInk.Blue, "Class 0"), legend(DkInk.Orange, "1"), legend(DkInk.Green, "2"), legend(DkInk.Pink, "3, held out"))
    val frames = listOf(
        DkFrame(
            header, plot, legend,
            listOf("3 features: 2 signal axes, 1 noisy axis (σ = 3)", "noise axis weight: {${n(w[2])}×} the signal axes"),
            if (tab == 1) "The learned metric squeezes the {noisy axis} to ${n(noiseW)}×." else "Raw distances are dominated by the {noisy axis}.",
            if (tab == 1) "Both branches of a Siamese network share weights, so they embed every input the same way; training on pairs from classes 0–2 learned to ignore the noise."
            else "The classes separate cleanly on the two signal axes, but the third axis's spread is larger than the gaps between them.",
        ),
        DkFrame(
            header, plot, legend,
            listOf("noise axis weight: ${n(noiseW)}× the signal axes", "1-shot NN on class 3: raw ${pct(rawAcc)} → {embedded ${pct(embAcc)}}"),
            "The unseen class is matched correctly {${pct(if (tab == 1) embAcc else rawAcc)}} of the time.",
            if (tab == 1) "The shared network learned to shrink the noisy feature using classes 0–2 only. That metric transfers to a class it never trained on, which is the point of a Siamese setup."
            else "With one example per class, nearest-neighbour matching in raw space is at the mercy of the noise.",
        ),
        DkFrame(
            header, plot, legend,
            listOf("contrastive loss: pull same-class pairs together", "push different pairs apart beyond a margin m"),
            "Siamese nets learn {a distance}, not classes.",
            "That is why they handle classes they never saw: face verification, signature matching, few-shot learning.",
        ),
    )
    stepActions(frames, "Next")
}

// ── Layer normalisation ──

private val lnRows = listOf(
    listOf(1.0, 4.2, -3.8, 2.4, 5.6, 6.0), listOf(1.2, 1.0, 3.9, -3.8, 6.6, 4.0),
    listOf(0.9, 3.4, -0.7, 6.4, 3.0, 5.8), listOf(4.4, 2.3, 4.3, 7.4, 7.5, 7.9),
)

private fun stats(v: List<Double>): Pair<Double, Double> {
    val m = v.average()
    return m to sqrt(v.sumOf { (it - m).pow(2) } / v.size)
}

private fun layerNormLab(): DkLab = DkLab(DkControl.Tabs, tabs = listOf("BatchNorm", "LayerNorm"), initialTab = 1) { tab, _ ->
    val layer = tab == 1
    val row = 1
    val col = 2
    val sel = if (layer) lnRows[row] else lnRows.map { it[col] }
    val (mu, sd) = stats(sel)
    val normed = sel.map { (it - mu) / sd }
    fun grid(showNormed: Boolean) = DkGrid(
        "", 4, 6,
        lnRows.flatMapIndexed { r, vals ->
            vals.mapIndexed { c, v ->
                val inSel = if (layer) r == row else c == col
                val k = if (layer) c else r
                if (inSel) DkCell(n(if (showNormed) normed[k] else v, if (showNormed) 2 else 1), DkCellTone.Heat, 0.55f) else DkCell(n(v, 1), DkCellTone.Zero)
            }
        },
        boxes = listOf(if (layer) DkBox(row, 0, row, 5) else DkBox(0, col, 3, col)),
        maxCell = 44f, rowLabels = (1..4).map { "x$it" }, colLabels = (1..6).map { "f$it" }, hotRow = if (layer) row else null,
    )
    val header = "batch of 4 · ${if (layer) "LayerNorm reads one row only" else "BatchNorm reads one column across the batch"}"
    val legend = listOf(legend(DkInk.Violet, "Normalised ${if (layer) "row" else "column"}"), legend(DkInk.Slate, "Never read"))
    val what = if (layer) "x2" else "f3"
    val frames = listOf(
        DkFrame(
            header, DkGrids(listOf(listOf(grid(false))), listOf(1f)), legend,
            listOf("μ = ${n(mu)} · σ = ${n(sd)}", "over ${sel.size} values: ${if (layer) "one example's features" else "one feature across the batch"}"),
            "$what is normalised with {its own} mean and spread.",
            if (layer) "BatchNorm would use each column across the 4 rows, so it changes with batch size. LayerNorm works the same with a batch of 1."
            else "Every example's f3 is shifted by the batch's mean, so one example's output depends on which others share its batch.",
        ),
        DkFrame(
            header, DkGrids(listOf(listOf(grid(true))), listOf(1f)), legend,
            listOf("μ = ${n(mu)} · σ = ${n(sd)}", "x̂ = {${normed.joinToString(", ", "[", "]") { n(it) }}}"),
            "After normalising, $what has {mean 0, std 1}.",
            "A learned scale γ and shift β per feature then let the network restore whatever range it needs.",
        ),
        DkFrame(
            header, DkGrids(listOf(listOf(grid(true))), listOf(1f)), legend,
            if (layer) listOf("batch of 1: still 6 values per row", "transformers and RNNs: LayerNorm") else listOf("batch of 1: one value per column → σ = 0", "CNNs with big batches: BatchNorm"),
            if (layer) "LayerNorm needs {no batch}: same at training and inference." else "BatchNorm {breaks} with a batch of 1.",
            if (layer) "That is why every transformer uses it: sequences vary in length and batches can be tiny."
            else "At inference it switches to running averages from training, so train and test behave differently.",
        ),
    )
    stepActions(frames, "Next")
}

// ── Group normalisation ──

private val gnScales = listOf(0.5, 0.5, 1.0, 1.0, 3.0, 3.0, 4.0, 4.0)

private val gnValues: List<List<Double>> by lazy {
    val rng = ModRng(808)
    gnScales.map { s -> List(6) { s * rng.g() + 0.3 * s } }
}

private fun groupNormLab(): DkLab = DkLab(DkControl.Tabs, tabs = listOf("G = 1", "G = 2", "G = 8"), initialTab = 1) { tab, _ ->
    val groups = listOf(1, 2, 8)[tab]
    val per = 8 / groups
    val first = gnValues.take(per).flatten()
    val (mu, sd) = stats(first)
    fun groupNorm(c: Int, v: Double): Double {
        val g = c / per
        val (m, s) = stats(gnValues.subList(g * per, (g + 1) * per).flatten())
        return (v - m) / s
    }
    fun grid(normed: Boolean): DkGrid {
        val vals = gnValues.mapIndexed { c, row -> row.map { if (normed) groupNorm(c, it) else it } }
        val top = vals.flatten().maxOf { abs(it) }
        return DkGrid(
            "", 8, 6,
            vals.flatMap { row -> row.map { v -> DkCell(n(v, 1).let { if (it == "−0.0") "0.0" else it }, if (v >= 0) DkCellTone.Pos else DkCellTone.Neg, (abs(v) / top).toFloat()) } },
            boxes = (0 until groups).map { g -> DkBox(g * per, 0, (g + 1) * per - 1, 5, dashed = g > 0) },
            maxCell = 30f, rowLabels = (0 until 8).map { "c$it" },
        )
    }
    val side = listOf("G = $groups group${if (groups > 1) "s" else ""}", "{group 1: c0–c${per - 1}}") +
        (if (groups == 2) listOf("group 2: c4–c7") else emptyList()) + listOf("", "channel scales", gnScales.joinToString(" · ") { n(it, 1).removeSuffix(".0") })
    val header = "8 channels × 6 positions · one sample"
    val legend = listOf(legend(DkInk.Yellow, "Group being normalised", SwatchStyle.Ring), legend(DkInk.Blue, "Positive"), legend(DkInk.Pink, "Negative"))
    val c0 = gnValues[0].map { (it - mu) / sd }
    val frames = listOf(
        DkFrame(
            header, DkGrids(listOf(listOf(grid(false))), listOf(1.5f), side = side), legend,
            listOf("group 1 (${per * 6} values): μ = ${n(mu)}, σ = ${n(sd)}", "c0 → {${c0.joinToString(", ", "[", "]") { n(it, 1) }}}"),
            when (groups) {
                1 -> "G = 1 normalises all {48 values} together: LayerNorm."
                8 -> "G = 8 gives every channel {its own} statistics: InstanceNorm."
                else -> "Channels with similar scale share {one} mean and σ."
            },
            "G = 1 is LayerNorm over all 48 values; G = 8 is InstanceNorm per channel. Neither needs a batch, which is why detection models use GroupNorm.",
        ),
        DkFrame(
            header, DkGrids(listOf(listOf(grid(true))), listOf(1.5f), side = side), legend,
            listOf("each group → mean 0, std 1", "within a group, channels keep their relative scale"),
            "After normalising, every group sits {on the same scale}.",
            if (groups == 1) "But c0–c3 are now squeezed near 0: one σ for all channels is dominated by the loud ones."
            else "Big-scale and small-scale channels no longer dwarf each other, yet channels inside a group keep their differences.",
        ),
        DkFrame(
            header, DkGrids(listOf(listOf(grid(true))), listOf(1.5f), side = side), legend,
            listOf("Mask R-CNN, small batches: GN beats BN", "default: G = 32 groups"),
            "GroupNorm works at {any batch size}.",
            "Detection and segmentation train with 1–2 images per GPU, where BatchNorm's statistics are noise.",
        ),
    )
    stepActions(frames, "Next")
}

// ── Early stopping ──

private class EarlyRun(val train: List<Double>, val valid: List<Double>)

private val earlyRun: EarlyRun by lazy {
    val rng = ModRng(4242)
    val d = 40
    val nTrain = 30
    val nVal = 30
    val wTrue = List(d) { if (it < 10) rng.g() else 0.0 }
    fun data(rows: Int) = List(rows) { List(d) { rng.g() } }.let { x -> x to x.map { r -> r.indices.sumOf { r[it] * wTrue[it] } + 1.5 * rng.g() } }
    val (xt, yt) = data(nTrain)
    val (xv, yv) = data(nVal)
    val w = DoubleArray(d)
    fun mse(x: List<List<Double>>, y: List<Double>) = x.indices.sumOf { i -> (x[i].indices.sumOf { x[i][it] * w[it] } - y[i]).pow(2) } / x.size
    val train = ArrayList<Double>()
    val valid = ArrayList<Double>()
    for (t in 0..1500) {
        train += mse(xt, yt)
        valid += mse(xv, yv)
        val g = DoubleArray(d)
        for (i in 0 until nTrain) {
            val err = xt[i].indices.sumOf { xt[i][it] * w[it] } - yt[i]
            for (k in 0 until d) g[k] += 2 * err * xt[i][k] / nTrain
        }
        for (k in 0 until d) w[k] -= 0.004 * g[k]
    }
    EarlyRun(train, valid)
}

private val patiences = listOf(10, 20, 50, 100, 200)

private fun earlyStopLab(): DkLab = DkLab(DkControl.StepperOnly, stepper = DkStepper("patience", patiences.map { it.toDouble() }, patiences.indexOf(50)) { n(it, 0) }) { _, p ->
    val run = earlyRun
    val pat = patiences[p]
    val best = run.valid.indices.minBy { run.valid[it] }
    var bestSoFar = 0
    var stop = run.valid.lastIndex
    for (t in run.valid.indices) {
        if (run.valid[t] < run.valid[bestSoFar]) bestSoFar = t
        if (t - bestSoFar >= pat) { stop = t; break }
    }
    val final = run.valid.last()
    val top = max(run.train.first(), run.valid.max())
    val every = 5
    fun curve(v: List<Double>) = v.indices.filter { it % every == 0 }.map { DkP(it.toDouble(), v[it]) }
    fun plot(showVal: Boolean, guide: Int?) = DkPlot(
        0.0 to 1500.0, -top * 0.03 to top * 1.03, listOf(top to n(top, 0), top / 2 to n(top / 2, 0), 0.0 to "0"), "step 0", "1500",
        listOf(DkLine(curve(run.train), DkInk.Blue)) + if (showVal) listOf(DkLine(curve(run.valid), DkInk.Violet)) else emptyList(),
        dots = guide?.let { listOf(DkDot(DkP(it.toDouble(), run.valid[it]), DkInk.Yellow, 6f)) } ?: emptyList(),
        axis = false, guide = guide?.let { it.toDouble() to "" },
    )
    val header = "40 features, 30 training rows · gradient descent"
    val legend = listOf(legend(DkInk.Blue, "Train", SwatchStyle.Line), legend(DkInk.Violet, "Validation", SwatchStyle.Line), legend(DkInk.Yellow, "Stop here", SwatchStyle.Dot))
    plain(
        listOf(
            DkFrame(
                header, plot(false, null), legend.take(1),
                listOf("train MSE: ${n(run.train.first())} → ${n(run.train.last())}", "40 weights, only 30 equations"),
                "Training loss {keeps falling}, all the way to ${n(run.train.last())}.",
                "With more features than rows, gradient descent can fit the training set almost perfectly, noise included.",
            ),
            DkFrame(
                header, plot(true, best), legend,
                listOf("best val {${n(run.valid[best])}} at step $best · final ${n(final)}", "train keeps falling: ${n(run.train[best])} → ${n(run.train.last())}"),
                "Stopping at step $best saves {${pct(1 - run.valid[best] / final)}} of the final validation loss.",
                "With more features than rows, gradient descent eventually fits the noise. Validation, never used for updates, shows when that starts.",
            ),
            DkFrame(
                header, plot(true, bestSoFar), legend,
                listOf("patience $pat: stop at step {$stop}, restore step $bestSoFar", "steps saved: ${1500 - stop} of 1500"),
                "With patience $pat, training halts at step {$stop}.",
                "Patience is how long to wait for a new best before giving up. Too short stops on a noisy blip; too long wastes compute. Either way, keep the best weights.",
            ),
        ),
    )
}

// ── Data augmentation ──

private typealias Pattern = List<List<Double>>

private val letterL: Pattern = List(5) { r -> List(5) { c -> if (c == 0 || r == 4) 1.0 else 0.0 } }
private val letterT: Pattern = List(5) { r -> List(5) { c -> if (r == 0 || c == 2) 1.0 else 0.0 } }

private fun shift(p: Pattern, dx: Int, dy: Int): Pattern = List(5) { r -> List(5) { c -> p.getOrNull(r - dy)?.getOrNull(c - dx) ?: 0.0 } }

private fun centroid(p: Pattern, poses: Boolean): Pattern {
    val set = if (poses) listOf(p, shift(p, 1, 0), shift(p, -1, 0), shift(p, 0, 1), shift(p, 0, -1)) else listOf(p)
    return List(5) { r -> List(5) { c -> set.sumOf { it[r][c] } / set.size } }
}

private fun distance(a: Pattern, b: Pattern) = (0 until 5).sumOf { r -> (0 until 5).sumOf { c -> (a[r][c] - b[r][c]).pow(2) } }

private fun augmentLab(): DkLab = DkLab(DkControl.Tabs, tabs = listOf("1 pose", "5 poses"), initialTab = 1) { tab, _ ->
    fun accuracy(poses: Boolean): Pair<Double, List<String>> {
        val cl = centroid(letterL, poses)
        val ct = centroid(letterT, poses)
        val misses = ArrayList<String>()
        var right = 0
        for ((name, letter) in listOf("L" to letterL, "T" to letterT)) for (dy in -1..1) for (dx in -1..1) {
            val test = shift(letter, dx, dy)
            val guess = if (distance(test, cl) <= distance(test, ct)) "L" else "T"
            if (guess == name) right++ else misses += "$name ${arrow(dx, dy)}"
        }
        return right / 18.0 to misses
    }
    val (acc1, miss1) = accuracy(false)
    val (acc5, miss5) = accuracy(true)
    val poses = tab == 1
    val c = centroid(letterL, poses)
    val test = shift(letterL, 1, 1)
    val stage = DkGrids(
        listOf(
            listOf(DkGrid("L centroid · ${if (poses) 5 else 1} pose${if (poses) "s" else ""}", 5, 5, c.flatten().map { if (it > 0) DkCell(n(it, 1), DkCellTone.Pos, it.toFloat()) else DkCell("", DkCellTone.Empty) }, maxCell = 30f)),
            listOf(DkGrid("test: L shifted ↘ 1 px", 5, 5, test.flatten().map { if (it > 0) DkCell("", DkCellTone.Hot) else DkCell("", DkCellTone.Empty) }, maxCell = 30f)),
        ),
        listOf(1f, 1f),
    )
    val header = "nearest-centroid classifier · classes L and T"
    val legend = listOf(legend(DkInk.Blue, "Averaged centroid"), legend(DkInk.Yellow, "Test pattern"))
    val acc = if (poses) acc5 else acc1
    val misses = if (poses) miss5 else miss1
    val frames = listOf(
        DkFrame(
            header, stage, legend,
            listOf("centroid = mean of the training images", if (poses) "5 poses: original + 4 one-pixel shifts" else "1 pose: just the original"),
            if (poses) "Four shifted copies {smear} the centroid across nearby pixels." else "One pose gives a {sharp} centroid: exactly the training L.",
            "A shifted test letter overlaps the sharp template only partly, and can land closer to the other class.",
        ),
        DkFrame(
            header, stage, legend,
            listOf("test set: both letters at all 9 one-pixel shifts", "accuracy: 1 pose ${pct(acc1)} → 5 poses {${pct(acc5)}}"),
            if (poses) "Four shifted copies lift accuracy from ${pct(acc1)} to {${pct(acc5)}}." else "With one pose, accuracy is {${pct(acc1)}}.",
            if (poses) "No new data was collected. The centroid now smears across nearby pixels, so a shifted L still lands closer to L than to T."
            else "Switch to 5 poses: the same two letters, shifted by one pixel each way, added to training.",
        ),
        DkFrame(
            header, stage, legend,
            listOf("misclassified: ${if (misses.isEmpty()) "none" else misses.joinToString(", ")}", "${misses.size} of 18 test patterns"),
            "Augmentation teaches the {invariance} the test needs.",
            "Shifts, flips, crops and colour jitter each encode a change that shouldn't alter the label. Augment with the wrong one (flipping digits) and you teach a lie.",
        ),
    )
    stepActions(frames, "Next")
}

private fun arrow(dx: Int, dy: Int): String = when {
    dx == 0 && dy == 0 -> "·"
    dx == 0 -> if (dy > 0) "↓" else "↑"
    dy == 0 -> if (dx > 0) "→" else "←"
    dx > 0 -> if (dy > 0) "↘" else "↗"
    else -> if (dy > 0) "↙" else "↖"
}
