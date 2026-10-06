package com.algora.app.feature.topics

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.math.tanh

// ── Deep-learning storyboard frames ──────────────────────────────────────────
// The fourteen labs drawn by DeepStoryLabs.kt. Every number on screen is computed here: the neuron in
// closed form, the deep stacks by a real forward and backward pass over seeded weights, the activation
// facts by a fine grid or a numeric integral. The generator is a plain LCG so the iOS port
// (DeepStoryFrames.swift) reproduces the same numbers.

internal fun deepLab(topicId: String): DkLab = cnnLab(topicId) ?: detectLab(topicId) ?: rnnLab(topicId) ?: transformerLab(topicId) ?: modernLab(topicId) ?: optimLab(topicId) ?: genLab(topicId) ?: when (topicId) {
    "biological_neuron" -> neuronLab()
    "neural_network_basics" -> feedforwardLab()
    "vanishing_gradient" -> vanishingLab()
    "exploding_gradient" -> explodingLab()
    "sigmoid" -> sigmoidLab()
    "tanh" -> tanhLab()
    "relu" -> reluLab()
    "leaky_relu" -> leakyLab()
    "prelu" -> preluLab()
    "elu" -> eluLab()
    "selu" -> seluLab()
    "swish" -> swishLab()
    "gelu" -> geluLab()
    else -> softmaxLab()
}

// ── Formatting and small math ──

private fun n(v: Double, d: Int = 2) = dkNum(v, d)

/** Up to [d] decimals with trailing zeros dropped: 1.5, 0.35, −2. */
private fun t(v: Double, d: Int = 2): String {
    val s = dkNum(v, d)
    return if ('.' in s) s.trimEnd('0').trimEnd('.') else s
}

private const val SupDigits = "⁰¹²³⁴⁵⁶⁷⁸⁹"
private fun sup(k: Int): String = (if (k < 0) "⁻" else "") + abs(k).toString().map { SupDigits[it - '0'] }.joinToString("")

/** 7.2e6, 2.8e−6. */
private fun sci(v: Double): String {
    if (v == 0.0) return "0"
    var e = floor(log10(abs(v))).toInt()
    var m = floor(abs(v) / 10.0.pow(e) * 10 + 0.5) / 10
    if (m >= 10) {
        m /= 10
        e += 1
    }
    val body = dkNum(m, 1) + "e" + (if (e < 0) "−" else "") + abs(e)
    return if (v < 0) "−$body" else body
}

/** Plain between 0.01 and 1000, scientific outside. */
private fun mag(v: Double): String = if (abs(v) >= 0.01 && abs(v) < 1000) n(v) else sci(v)

/** Two significant figures with thousands separators: 360,000. */
private fun grouped(v: Double): String {
    if (v < 100) return n(v, 0)
    val e = floor(log10(v)).toInt() - 1
    val r = (floor(v / 10.0.pow(e) + 0.5) * 10.0.pow(e)).toLong()
    return r.toString().reversed().chunked(3).joinToString(",").reversed()
}

private fun grid(a: Double, b: Double, step: Double): List<Double> =
    List(((b - a) / step).roundToInt() + 1) { ((a + it * step) * 1000).roundToInt() / 1000.0 }

/** f sampled across [a, b], with x = 0 included so a kink lands on a sample. */
private fun curve(a: Double, b: Double, count: Int = 160, f: (Double) -> Double): List<DkP> {
    val xs = (0..count).map { a + (b - a) * it / count }.toMutableList()
    if (a < 0 && b > 0) xs += 0.0
    return xs.sorted().map { DkP(it, f(it)) }
}

private fun sig(z: Double) = 1.0 / (1.0 + exp(-z))

private fun line(ink: DkInk, label: String) = DkLegend(ink, SwatchStyle.Line, label)
private fun dashed(ink: DkInk, label: String) = DkLegend(ink, SwatchStyle.DashedLine, label)
private fun swatch(ink: DkInk, label: String) = DkLegend(ink, SwatchStyle.Fill, label)

/** Decade ticks over a log10 range: every decade, or every other one when the range is wide. */
private fun logTicks(lo: Double, hi: Double): List<Pair<Double, String>> {
    val top = floor(hi).toInt()
    val bottom = ceil(lo).toInt()
    val step = if (top - bottom >= 6) 2 else 1
    return (top downTo bottom step step).map { k -> k.toDouble() to if (k == 0) "1" else "1e" + (if (k < 0) "−" else "") + abs(k) }
}

private fun logRange(values: List<Double>): Pair<Double, Double> {
    val lo = values.min()
    val hi = values.max()
    val pad = max(0.35, (hi - lo) * 0.06)
    return (lo - pad) to (hi + pad)
}

/** z as a headline writes it: 3, −1.5, 0.75. */
private fun zt(z: Double) = t(z)

/** e raised to −z: e⁻³ for a whole number, e^(−0.5) otherwise. */
private fun eNeg(z: Double): String = if (z == floor(z)) "e" + sup(-z.toInt()) else "e^(${zt(-z)})"

/** A seeded LCG with Box–Muller normals, the same sequence on both platforms. */
private class DkRng(seed: Long) {
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

/** The standard normal CDF via Abramowitz–Stegun 7.1.26 (error under 1.5e−7). */
private fun phi(x: Double): Double {
    val z = abs(x) / sqrt(2.0)
    val k = 1 / (1 + 0.3275911 * z)
    val erf = 1 - (((((1.061405429 * k - 1.453152027) * k) + 1.421413741) * k - 0.284496736) * k + 0.254829592) * k * exp(-z * z)
    return if (x >= 0) 0.5 * (1 + erf) else 0.5 * (1 - erf)
}

/** E[f(z)] and Var[f(z)] for z ~ N(0, 1), by a fine Riemann sum over ±8. */
private fun normalMoments(f: (Double) -> Double): Pair<Double, Double> {
    val h = 0.001
    var m1 = 0.0
    var m2 = 0.0
    var x = -8.0
    while (x <= 8.0) {
        val w = exp(-x * x / 2) / sqrt(2 * PI) * h
        val v = f(x)
        m1 += v * w
        m2 += v * v * w
        x += h
    }
    return m1 to (m2 - m1 * m1)
}

/** The x in [a, b] (step [h]) where f is smallest. */
private fun argMin(a: Double, b: Double, h: Double, f: (Double) -> Double): Double {
    var best = a
    var bv = f(a)
    var x = a
    while (x <= b) {
        val v = f(x)
        if (v < bv) {
            bv = v
            best = x
        }
        x += h
    }
    return best
}

/** A stepper over input z ("−0.75"). */
private fun zStepper(a: Double, b: Double, step: Double, initial: Double, digits: Int) =
    grid(a, b, step).let { zs -> DkStepper("input z", zs, zs.indexOf(initial)) { n(it, digits) } }

private fun lastAction(i: Int, count: Int, action: String = "Next") = if (i == count - 1) "Start Over" else action

// ── Deep stacks: a real forward and backward pass ──

private class DeepRun(
    /** Mean |activation| at layers 1..L. */
    val act: List<Double>,
    /** Mean |∂L/∂a| at layers 1..L, with ∂L/∂a = 1 at the top. */
    val grad: List<Double>,
    /** ‖∂L/∂W‖ per layer. */
    val wgrad: List<Double>,
    /** Mean f′(z) over every unit, layer and sample. */
    val slope: Double,
    /** Activation variance and mean per layer. */
    val variance: List<Double>,
    val mean: List<Double>,
)

private enum class Act { Sigmoid, Tanh, Relu, Selu }

private const val SeluL = 1.0507009873554805
private const val SeluA = 1.6732632423543772

private fun actF(kind: Act, z: Double): Double = when (kind) {
    Act.Sigmoid -> sig(z)
    Act.Tanh -> tanh(z)
    Act.Relu -> max(0.0, z)
    Act.Selu -> if (z > 0) SeluL * z else SeluL * SeluA * (exp(z) - 1)
}

private fun actD(kind: Act, z: Double): Double = when (kind) {
    Act.Sigmoid -> sig(z) * (1 - sig(z))
    Act.Tanh -> 1 - tanh(z) * tanh(z)
    Act.Relu -> if (z > 0) 1.0 else 0.0
    Act.Selu -> if (z > 0) SeluL else SeluL * SeluA * exp(z)
}

/** [layers] dense layers of [width] units, weights N(0, σw²), no biases, on [batch] standard-normal inputs. */
private fun deepRun(kind: Act, layers: Int, width: Int, sigmaW: Double, seed: Long, batch: Int = 32, backward: Boolean = true): DeepRun {
    val rng = DkRng(seed)
    val w = List(layers) { Array(width) { DoubleArray(width) { rng.g() * sigmaW } } }
    val x = Array(batch) { DoubleArray(width) { rng.g() } }
    val zs = ArrayList<Array<DoubleArray>>()
    val outs = ArrayList<Array<DoubleArray>>()
    var a = x
    for (l in 0 until layers) {
        val z = Array(batch) { b -> DoubleArray(width) { i -> var s = 0.0; for (j in 0 until width) s += w[l][i][j] * a[b][j]; s } }
        a = Array(batch) { b -> DoubleArray(width) { i -> actF(kind, z[b][i]) } }
        zs += z
        outs += a
    }
    val count = (batch * width).toDouble()
    val act = outs.map { o -> o.sumOf { r -> r.sumOf { abs(it) } } / count }
    val mean = outs.map { o -> o.sumOf { r -> r.sum() } / count }
    val variance = outs.mapIndexed { l, o -> o.sumOf { r -> r.sumOf { (it - mean[l]) * (it - mean[l]) } } / count }
    var slopeSum = 0.0
    zs.forEach { z -> z.forEach { r -> r.forEach { slopeSum += actD(kind, it) } } }

    val grad = DoubleArray(layers)
    val wgrad = DoubleArray(layers)
    var g = Array(batch) { DoubleArray(width) { 1.0 } }
    if (backward) for (l in layers - 1 downTo 0) {
        grad[l] = g.sumOf { r -> r.sumOf { abs(it) } } / count
        val delta = Array(batch) { b -> DoubleArray(width) { i -> g[b][i] * actD(kind, zs[l][b][i]) } }
        val prev = if (l == 0) x else outs[l - 1]
        var sq = 0.0
        for (i in 0 until width) for (j in 0 until width) {
            var s = 0.0
            for (b in 0 until batch) s += delta[b][i] * prev[b][j]
            s /= batch
            sq += s * s
        }
        wgrad[l] = sqrt(sq)
        g = Array(batch) { b -> DoubleArray(width) { j -> var s = 0.0; for (i in 0 until width) s += w[l][i][j] * delta[b][i]; s } }
    }
    return DeepRun(act, grad.toList(), wgrad.toList(), slopeSum / (count * layers), variance, mean)
}

private fun logLine(values: List<Double>, ink: DkInk, dashed: Boolean = false, upTo: Int = values.size, from: Int = 0) =
    DkLine((from until upTo).map { DkP(it + 1.0, log10(values[it])) }, ink, dashed, dots = !dashed)

// ── The biological neuron: leaky integrate-and-fire in closed form ──

private const val Tau = 10.0
private const val Rest = -70.0
private const val Thresh = -55.0
private const val Refract = 2.0
private const val RunMs = 120.0
private const val Rheobase = Thresh - Rest

private fun firingRate(i: Double): Double =
    if (i <= Rheobase) 0.0 else 1000.0 / (Tau * ln(i / (i - Rheobase)) + Refract)

private fun neuronLab(): DkLab {
    val currents = grid(8.0, 30.0, 2.0)
    return DkLab(DkControl.Stepper, stepper = DkStepper("current I", currents, currents.indexOf(18.0)) { n(it, 0) }) { _, p ->
        val i = currents[p]
        val vInf = Rest + i
        val fires = vInf > Thresh
        val tStar = if (fires) Tau * ln(i / (i - Rheobase)) else 0.0
        val period = tStar + Refract
        val spikes = if (fires) generateSequence(tStar) { it + period }.takeWhile { it <= RunMs }.toList() else emptyList()
        val rate = firingRate(i)
        fun charge(t0: Double, t: Double) = vInf + (Rest - vInf) * exp(-(t - t0) / Tau)
        val passive = curve(0.0, RunMs, 240) { charge(0.0, it) }
        val train = if (!fires) passive else buildList {
            var t0 = 0.0
            while (true) {
                val end = min(t0 + tStar, RunMs)
                for (k in 0..30) add(DkP(t0 + (end - t0) * k / 30, charge(t0, t0 + (end - t0) * k / 30)))
                if (t0 + tStar > RunMs) break
                add(DkP(t0 + tStar, -40.0))
                add(DkP(t0 + tStar, Rest))
                t0 += period
                add(DkP(min(t0, RunMs), Rest))
                if (t0 >= RunMs) break
            }
        }
        val iText = n(i, 0)
        val vText = "${n(vInf, 0)} mV"
        val ticks = listOf(-40.0 to "−40", -55.0 to "−55", -70.0 to "−70")
        fun trace(lines: List<DkLine>, rules: List<DkRule>, dots: List<DkDot> = emptyList(), bracket: DkBracket? = null) =
            DkPlot(0.0 to RunMs, -73.0 to -37.0, ticks, "0", "120 ms", lines, dots, rules, axis = false, bracket = bracket)
        val vInfRule = DkRule(vInf, DkInk.Grey, thin = true)
        val threshRule = DkRule(Thresh, DkInk.Pink)
        val traceLegend = listOf(line(DkInk.Blue, "V(t)"), dashed(DkInk.Pink, "Threshold −55"), dashed(DkInk.Grey, "V∞ = ${n(vInf, 0)}"))
        val tStarLine = "t* = 10·ln($iText/${n(i - Rheobase, 0)}) = {${n(tStar, 1)} ms}"
        val rateText = "${n(rate, 0)} Hz"
        val maxRate = firingRate(currents.last())
        val fi = curve(0.0, 30.0, 300) { firingRate(it) }
        fun fiPlot(extra: List<DkLine>) = DkPlot(
            0.0 to 30.0, -6.0 to maxRate * 1.1, listOf(100.0 to "100", 50.0 to "50", 0.0 to "0"), "I = 0", "30",
            extra + DkLine(fi, DkInk.Blue), listOf(DkDot(DkP(i, rate))), axis = false, guide = i to "I = $iText",
        )
        listOf(
            DkFrame(
                null, trace(listOf(DkLine(passive, DkInk.Blue)), listOf(vInfRule)),
                listOf(line(DkInk.Blue, "V(t)"), dashed(DkInk.Grey, "V∞ = ${n(vInf, 0)}")),
                listOf("τ·dV/dt = −(V + 70) + I,  τ = 10 ms", "V∞ = −70 + $iText = {$vText}"),
                "Current I = $iText charges the membrane toward {$vText}.",
                "The leak pulls V back to rest at −70 while the input pushes it up; they balance at V∞ = −70 + I. Each τ = 10 ms closes 63% of the gap.",
            ),
            DkFrame(
                null,
                trace(listOf(DkLine(passive, DkInk.Blue)), listOf(threshRule, vInfRule), if (fires) listOf(DkDot(DkP(tStar, Thresh))) else emptyList()),
                traceLegend,
                if (fires) listOf("V∞ = $vText > −55", tStarLine) else listOf("V∞ = $vText < −55", "t* = {never}"),
                if (fires) "It crosses the −55 threshold at {${n(tStar, 1)} ms}." else "It settles at $vText, {short of} the −55 threshold.",
                if (fires) "V∞ sits past threshold, so the climb reaches −55 first, at t* = τ·ln(I / (I − 15))."
                else "Below 15 units of current the leak always wins. 15 is the rheobase: the least current that can ever cause a spike.",
            ),
            DkFrame(
                null,
                trace(
                    listOf(DkLine(train, DkInk.Blue)), listOf(vInfRule, threshRule),
                    bracket = if (spikes.size >= 2) DkBracket(spikes[0], spikes[1], -40.0, "${n(period, 1)} ms") else null,
                ),
                traceLegend,
                if (fires) listOf("V∞ = −70 + $iText = $vText > −55", tStarLine) else listOf("V∞ = −70 + $iText = $vText < −55", "no crossing: {0 spikes}"),
                if (fires) "Above rheobase, the cell fires every {${n(period, 1)} ms}." else "Below rheobase, the cell {never fires}.",
                if (fires) "At $iText units the resting balance sits at $vText, past threshold. It takes ${n(tStar, 1)} ms to climb there, plus 2 ms refractory."
                else "At $iText units the balance point $vText is under threshold, so V creeps up and stops. Raise I past 15.",
                chips = listOf(DkChip("spikes", "${spikes.size}", tint = true), DkChip("rate", rateText)),
            ),
            DkFrame(
                "firing rate f vs input current I", fiPlot(emptyList()),
                listOf(line(DkInk.Blue, "f(I)"), DkLegend(DkInk.Yellow, SwatchStyle.Dot, "I = $iText")),
                listOf("f = 1000 / (t* + 2 ms)", if (fires) "f($iText) = 1000 / ${n(period, 1)} = {$rateText}" else "f($iText) = {0 Hz}"),
                if (fires) "At I = $iText the cell fires at {$rateText}; below 15 it's silent." else "At I = $iText the rate is {0 Hz}: left of the rheobase.",
                "Sweep every current and the rate traces this f–I curve: zero up to the rheobase, then rising steeply and bending over as the refractory period caps it.",
            ),
            DkFrame(
                "firing rate f vs input current I",
                fiPlot(listOf(DkLine(curve(0.0, 30.0, 300) { max(0.0, maxRate / 15 * (it - Rheobase)) }, DkInk.Grey, dashed = true))),
                listOf(line(DkInk.Blue, "neuron f(I)"), dashed(DkInk.Grey, "ReLU")),
                listOf("neuron: f(I) = 0 for I ≤ 15, rising after", "ReLU: {max(0, z)} = 0 for z ≤ 0, rising after"),
                "An artificial unit keeps only the shape: {max(0, z)}.",
                "A ReLU unit outputs its rate at once: no membrane, no spikes, no time. The neuron needed 120 ms of voltage to give the same kind of answer.",
            ),
        )
    }
}

// ── Feedforward networks: one forward pass through 2-3-1 ──

private val ffX = listOf(0.90, 0.20)
private val ffW1 = listOf(listOf(0.9, -0.45), listOf(0.3, 0.35), listOf(-0.5, 0.5))
private val ffW2 = listOf(1.2, -0.7, 0.9)
private const val FfB = -0.3

private fun feedforwardLab(): DkLab = DkLab(DkControl.Track) { _, _ ->
    fun forward(x: List<Double>): Triple<List<Double>, List<Double>, Double> {
        val pre = ffW1.map { w -> w[0] * x[0] + w[1] * x[1] }
        val h = pre.map { max(0.0, it) }
        return Triple(pre, h, (0 until 3).sumOf { ffW2[it] * h[it] } + FfB)
    }
    val (pre, h, z) = forward(ffX)
    val swapped = listOf(ffX[1], ffX[0])
    val (pre2, h2, z2) = forward(swapped)
    val hy = listOf(0f, 0.5f, 1f)

    fun net(x: List<Double>, hidden: List<Pair<String, DkTone>>, notes: List<String?>, out: Pair<String, DkTone>, inState: (Int) -> DkEdgeState, outState: (Int) -> DkEdgeState?, labels: Boolean): DkNet {
        val nodes = listOf(DkNode(0f, 0.27f, n(x[0]), DkTone.Input), DkNode(0f, 0.73f, n(x[1]), DkTone.Input)) +
            hidden.mapIndexed { j, (text, tone) -> DkNode(0.5f, hy[j], text, tone, notes[j]) } +
            DkNode(1f, 0.45f, out.first, out.second)
        val edges = (0 until 3).flatMap { j -> (0 until 2).map { i -> DkEdge(i, 2 + j, ffW1[j][i], inState(j)) } } +
            (0 until 3).map { j ->
                val s = outState(j) ?: DkEdgeState.Faint
                DkEdge(2 + j, 5, ffW2[j], s, if (labels) t(ffW2[j]) else null)
            }
        return DkNet(nodes, edges, listOf(0f to "input", 0.5f to "hidden · ReLU", 1f to "output · σ"))
    }
    val ghost = "?" to DkTone.Ghost
    val signs = listOf(line(DkInk.Blue, "Positive weight"), line(DkInk.Orange, "Negative"))
    val withSilenced = signs + dashed(DkInk.Grey, "Silenced by ReLU")
    val done = h.mapIndexed { j, v -> n(v) to if (pre[j] > 0) DkTone.Hidden else DkTone.Off }
    val silentNote = listOf(null, null, "z = ${n(pre[2])}")
    val outEdges = { j: Int -> if (pre[j] > 0) DkEdgeState.Lit else DkEdgeState.Silenced }
    fun sumOf(x: List<Double>, j: Int) = "${t(ffW1[j][0])}·${n(x[0])} ${if (ffW1[j][1] < 0) "−" else "+"} ${t(abs(ffW1[j][1]))}·${n(x[1])}"
    val y = sig(z)
    val y2 = sig(z2)
    val actions = listOf("Compute h₁", "Compute h₂, h₃", "Weigh Hidden Units", "Apply σ", "Swap Inputs", "Start Over")
    listOf(
        DkFrame(
            null, net(ffX, List(3) { ghost }, List(3) { null }, ghost, { DkEdgeState.Faint }, { null }, false), signs,
            listOf("x = (${n(ffX[0])}, ${n(ffX[1])})", "each hidden unit: h = ReLU(w·x)"),
            "Two inputs, {${n(ffX[0])}} and {${n(ffX[1])}}, enter the network.",
            "Every input connects to every hidden unit. Blue weights are positive, orange negative; thicker means larger.",
        ),
        DkFrame(
            null,
            net(ffX, listOf(n(h[0]) to DkTone.Current, ghost, ghost), List(3) { null }, ghost, { if (it == 0) DkEdgeState.Lit else DkEdgeState.Faint }, { null }, false),
            signs,
            listOf("h₁ = ReLU(${sumOf(ffX, 0)})", "   = ReLU(${n(pre[0])}) = {${n(h[0])}}"),
            "Hidden unit 1 sums its weighted inputs: {${n(h[0])}}.",
            "The ${t(ffW1[0][0])} weight on x₁ dominates; ${t(ffW1[0][1])} on x₂ pulls it down a little. Positive, so ReLU passes it unchanged.",
        ),
        DkFrame(
            null, net(ffX, listOf(n(h[0]) to DkTone.Hidden, n(h[1]) to DkTone.Hidden, n(h[2]) to DkTone.Off), silentNote, ghost, { if (it == 0) DkEdgeState.Faint else DkEdgeState.Lit }, { null }, false),
            signs,
            listOf("h₂ = ReLU(${sumOf(ffX, 1)}) = ${n(h[1])}", "h₃ = ReLU(${sumOf(ffX, 2)}) = ReLU({${n(pre[2])}}) = 0"),
            "Hidden unit 3's sum is {${n(pre[2])}}, so ReLU outputs 0.",
            "Unit 2 is positive and passes ${n(h[1])}. Unit 3 is switched off for this input.",
        ),
        DkFrame(
            null, net(ffX, done, silentNote, "?" to DkTone.Current, { DkEdgeState.Faint }, outEdges, true), withSilenced,
            listOf("z = ${t(ffW2[0])}·${n(h[0])} − ${t(-ffW2[1])}·${n(h[1])} + ${t(ffW2[2])}·${n(h[2])} − ${t(-FfB)}", "  = {${n(z, 3)}}"),
            "Hidden unit 3 adds {nothing} to the output.",
            "Its weighted sum is ${n(pre[2])}, so ReLU sets it to 0 and its ${t(ffW2[2])} weight is skipped. Applying σ next turns z = ${n(z, 3)} into the prediction.",
        ),
        DkFrame(
            null, net(ffX, done, silentNote, n(y) to DkTone.Output, { DkEdgeState.Faint }, outEdges, true), withSilenced,
            listOf("ŷ = σ(z) = 1/(1 + e^(−${n(z, 3)}))", "  = {${n(y, 3)}}"),
            "σ(${n(z, 3)}) = {${n(y, 3)}}: the network's prediction.",
            "Read as a probability, that's ${n(y * 100, 0)}% for class 1. Training adjusts every weight until this matches the label.",
        ),
        DkFrame(
            null,
            net(
                swapped, h2.mapIndexed { j, v -> n(v) to if (pre2[j] > 0) DkTone.Hidden else DkTone.Off },
                listOf("z = ${n(pre2[0])}", null, null), n(y2) to DkTone.Output, { DkEdgeState.Faint },
                { j -> if (pre2[j] > 0) DkEdgeState.Lit else DkEdgeState.Silenced }, true,
            ),
            withSilenced,
            listOf("h = (${h2.joinToString(", ") { n(it) }})", "ŷ = σ(${n(z2, 3)}) = {${n(y2, 3)}}"),
            "Swap the inputs and {unit 1} goes silent instead.",
            "Which units fire depends on the input, so each input runs through its own part of the network. That's how a ReLU net builds a piecewise-linear function.",
        ),
    ).mapIndexed { i, f -> DkFrame(f.header, f.stage, f.legend, f.formula, f.headline, f.body, f.chips, actions[i]) }
}

// ── Vanishing gradients: ten layers, three activations ──

private const val VgLayers = 10
private const val VgWidth = 16
private const val VgSeed = 2024L

private val vgRuns: List<DeepRun> by lazy {
    listOf(
        deepRun(Act.Sigmoid, VgLayers, VgWidth, sqrt(2.0 / (VgWidth + VgWidth)), VgSeed),
        deepRun(Act.Tanh, VgLayers, VgWidth, sqrt(2.0 / (VgWidth + VgWidth)), VgSeed),
        deepRun(Act.Relu, VgLayers, VgWidth, sqrt(2.0 / VgWidth), VgSeed),
    )
}

private fun vanishingLab(): DkLab = DkLab(DkControl.Tabs, tabs = listOf("Sigmoid", "Tanh", "ReLU + He")) { tab, _ ->
    val rel = vgRuns.map { run -> run.grad.map { it / run.grad.last() } }
    val mine = rel[tab]
    val other = if (tab == 2) rel[0] else rel[2]
    val inks = listOf(DkInk.Pink, DkInk.Orange, DkInk.Green)
    val names = listOf("Sigmoid, Xavier", "Tanh, Xavier", "ReLU, He")
    val otherInk = if (tab == 2) DkInk.Pink else DkInk.Green
    val otherName = if (tab == 2) names[0] else names[2]
    val (lo, hi) = logRange((mine + other).map { log10(it) } + 0.0)
    val per = mine[0].pow(1.0 / (VgLayers - 1))
    val slopeName = listOf("σ′", "tanh′", "ReLU′")[tab]
    val ceilingText = listOf("(ceiling 0.25)", "(ceiling 1)", "(active half)")[tab]
    val slopeLine = "mean $slopeName = ${n(vgRuns[tab].slope, 3)} $ceilingText"
    fun plot(upTo: Int, withOther: Boolean, dot: Int) = DkPlot(
        0.6 to VgLayers + 0.4, lo to hi, logTicks(lo, hi), "layer 1", "layer $VgLayers (output)",
        (if (withOther) listOf(logLine(other, otherInk, dashed = true)) else emptyList()) +
            logLine(mine, inks[tab], from = VgLayers - upTo),
        listOf(DkDot(DkP(dot + 1.0, log10(mine[dot])))), axis = false,
    )
    val legendMine = line(inks[tab], names[tab])
    val legendBoth = listOf(legendMine, dashed(otherInk, otherName))
    val header = "gradient size per layer, relative to output"
    val firstText = mag(mine[0])
    val frames = listOf(
        DkFrame(
            header, plot(1, false, VgLayers - 1), listOf(legendMine),
            listOf("g ← Wᵀ(g ⊙ f′(z)), once per layer", "layer $VgLayers: g = {1}"),
            "Backprop starts at the output with gradient {1}.",
            "Each layer it passes back through multiplies it by that layer's weights and by the activation's slope f′(z).",
        ),
        DkFrame(
            header, plot(2, false, VgLayers - 2), listOf(legendMine),
            listOf(slopeLine, "layer ${VgLayers - 1}: {${mag(mine[VgLayers - 2])}}"),
            "One layer back, {${mag(mine[VgLayers - 2])}} of it is left.",
            listOf(
                "σ′ is at most 0.25, and Xavier weights only restore about ×1, so most of the gradient is lost at every layer.",
                "tanh′ reaches 1 at zero, but units pushed toward ±1 pass back much less.",
                "ReLU′ is exactly 1 on active units, and He init doubles the weight variance to make up for the half that are off.",
            )[tab],
        ),
        DkFrame(
            header, plot(VgLayers, true, 0), legendBoth,
            listOf(slopeLine, "per layer ≈ ${n(per, 3)}${sup(VgLayers - 1)} = {$firstText}"),
            "Layer 1 gets {$firstText} of the output's gradient.",
            when (tab) {
                0 -> "Each sigmoid layer shrinks it by about ${n(1 / per, 1)}×. ReLU with He init ends at ${mag(rel[2][0])}, so its first layer still learns."
                1 -> "Each tanh layer shrinks it by about ${n(1 / per, 1)}×: better than sigmoid, still vanishing. ReLU with He init ends at ${mag(rel[2][0])}."
                else -> "Sigmoid with Xavier ends at ${mag(rel[0][0])} on the same inputs. With ReLU the first layer learns about as fast as the last."
            },
        ),
        DkFrame(
            header, plot(VgLayers, true, 0), legendBoth,
            listOf("Δw ∝ η·g, the same η for every layer", "layer 1 / layer $VgLayers = {$firstText}"),
            if (tab == 2) "With ReLU and He init, layer 1 moves at {${n(mine[0])}×} the output's rate."
            else "At one learning rate, layer 1 moves {${grouped(1 / mine[0])}×} slower.",
            when {
                tab == 2 -> "No layer is starved, so all ten learn together. The opposite risk is a gain above 1: see exploding gradients."
                1 / mine[0] < 100 -> "Slower, not stuck: near 0 tanh's slope is close to 1. Saturated units or a deeper stack push it toward sigmoid's numbers."
                else -> "By the time layer $VgLayers has settled, layer 1 has barely left its random start. ReLU, careful init and residual connections fixed this."
            },
        ),
    )
    frames.mapIndexed { i, f -> DkFrame(f.header, f.stage, f.legend, f.formula, f.headline, f.body, f.chips, lastAction(i, frames.size)) }
}

// ── Exploding gradients: twelve ReLU layers, σw on a stepper ──

private const val EgLayers = 12
private const val EgWidth = 16
private const val EgSeed = 77L
private val egSigmas = listOf(0.25, 0.35, 0.5, 0.75, 1.0, 1.5, 2.0)

private fun explodingLab(): DkLab =
    DkLab(DkControl.Stepper, stepper = DkStepper("weight σw", egSigmas, egSigmas.indexOf(1.5)) { t(it) }) { _, p ->
        val sw = egSigmas[p]
        val run = deepRun(Act.Relu, EgLayers, EgWidth, sw, EgSeed)
        val he = deepRun(Act.Relu, EgLayers, EgWidth, sqrt(2.0 / EgWidth), EgSeed)
        val gain = sw * sqrt(EgWidth / 2.0)
        val measured = (run.act.last() / run.act.first()).pow(1.0 / (EgLayers - 1))
        val grow = measured > 1.15
        val shrink = measured < 0.87
        val top = run.act.last()
        val heTop = he.act.last()
        val swText = t(sw)
        val heLine = dashed(DkInk.Green, "He, σw = 0.35")
        val mineLine = line(DkInk.Pink, "σw = $swText")
        fun plot(values: List<Double>, base: List<Double>, mine: Boolean, dot: Int, extra: List<DkLine> = emptyList()): DkPlot {
            val logs = (if (mine) values else emptyList()) + base + extra.flatMap { l -> l.pts.map { 10.0.pow(it.y) } }
            val (lo, hi) = logRange((logs + values + base).map { log10(it) })
            return DkPlot(
                0.6 to EgLayers + 0.4, lo to hi, logTicks(lo, hi), "layer 1", "layer $EgLayers",
                listOf(logLine(base, DkInk.Green, dashed = true)) + (if (mine) listOf(logLine(values, DkInk.Pink)) else emptyList()) + extra,
                if (mine) listOf(DkDot(DkP(dot + 1.0, log10(values[dot])))) else emptyList(), axis = false,
            )
        }
        val actHeader = "mean |activation| per layer · $EgLayers-layer ReLU, width $EgWidth"
        val gainLine = "gain ≈ σw·√(n/2) = $swText·√8 = ${n(gain)}"
        val norm = sqrt(run.wgrad.sumOf { it * it })
        val scale = min(1.0, 1.0 / norm)
        val clipped = run.wgrad.map { it * scale }
        val spread = run.wgrad.max() / run.wgrad.min()
        val g1 = run.grad.first()
        listOf(
            DkFrame(
                actHeader, plot(run.act, he.act, false, EgLayers - 1), listOf(heLine),
                listOf("He: σw = √(2/n) = √(2/16) = 0.35", "gain ≈ σw·√(n/2) = {1.00}"),
                "With He scaling, layer $EgLayers's activations stay at {${mag(heTop)}}.",
                "He init draws weights with σw = √(2/n): ReLU zeroes half the units, so each weight is made √2 larger to keep the signal's size.",
                chips = listOf(DkChip("He", mag(heTop))),
                action = "Next",
            ),
            DkFrame(
                actHeader, plot(run.act, he.act, true, EgLayers - 1), listOf(mineLine, heLine),
                listOf(gainLine, "measured {${n(measured)}×} per layer"),
                when {
                    grow -> "Activations grow ${n(measured, 1)}× per layer, to {${mag(top)}}."
                    shrink -> "Activations shrink to ${n(measured, 2)}× per layer, down to {${mag(top)}}."
                    else -> "Activations hold steady: layer $EgLayers is at {${mag(top)}}."
                },
                when {
                    grow -> "With He scaling the gain is 1 and layer $EgLayers stays at ${mag(heTop)}. Gradients flow back through the same weights, so they explode too."
                    shrink -> "Below He's 0.35 each layer loses signal, and gradients flowing back through the same weights vanish the same way."
                    else -> "Near σw = 0.35 the gain is about 1, so neither activations nor gradients drift."
                },
                chips = listOf(DkChip("layer $EgLayers", mag(top), tint = true), DkChip("He", mag(heTop))),
            ),
            DkFrame(
                "mean |gradient| per layer, backward from layer $EgLayers", plot(run.grad, he.grad, true, 0), listOf(mineLine, heLine),
                listOf("g₁ = W₂ᵀ(… W₁₂ᵀ(g ⊙ ReLU′) …)", "layer 1: {${mag(g1)}}   He: ${mag(he.grad.first())}"),
                when {
                    grow -> "Going back, the gradient grows to {${mag(g1)}} at layer 1."
                    shrink -> "Going back, the gradient shrinks to {${mag(g1)}} at layer 1."
                    else -> "Going back, layer 1's gradient is {${mag(g1)}}, close to the output's."
                },
                when {
                    grow -> "Backprop multiplies by the same weights in reverse, so it compounds by the same gain. One step at a normal learning rate throws layer 1's weights far away."
                    shrink -> "Backprop multiplies by the same weights in reverse, so layer 1 barely learns."
                    else -> "With the gain near 1, every layer gets a usable gradient."
                },
            ),
            DkFrame(
                "‖∂L/∂W‖ per layer, before and after clipping",
                plot(clipped, run.wgrad, true, 0).let { pl ->
                    DkPlot(
                        pl.xr, pl.yr, pl.yTicks, pl.xLeft, pl.xRight,
                        listOf(logLine(run.wgrad, DkInk.Pink, dashed = true), logLine(clipped, DkInk.Blue)), pl.dots, axis = false,
                    )
                },
                listOf(dashed(DkInk.Pink, "before"), line(DkInk.Blue, "clipped to norm 1")),
                listOf("‖g‖ = ${mag(norm)}", "g ← g · min(1, 1/‖g‖) = g · {${mag(scale)}}"),
                if (norm > 1) "Clipping scales every gradient by {${mag(scale)}}, to total norm 1."
                else "The total norm is {${mag(norm)}}, under the clip threshold of 1: nothing changes.",
                if (norm > 1) "It keeps the direction and caps the step, so one bad batch can't wreck the weights. It doesn't fix the scale: the layers are still ${mag(spread)}× apart."
                else "Clipping only acts when the gradient is too large; this one passes untouched.",
            ),
            DkFrame(
                actHeader, plot(run.act, he.act, true, EgLayers - 1), listOf(mineLine, heLine),
                listOf("gain = σw·√8 = 1  ⇒  σw = 1/√8 = {0.35}", gainLine),
                if (grow || shrink) "The real fix is the scale: σw = {0.35} gives gain 1." else "At σw = $swText this is {He init} already.",
                "He init, batch norm and residual connections all keep each layer's gain near 1, so activations and gradients stay in range without clipping." +
                    if (grow || shrink) " Step σw to 0.35 to see it." else "",
                chips = listOf(DkChip("layer $EgLayers", mag(top), tint = true), DkChip("He", mag(heTop))),
            ),
        )
    }

// ── Shared activation-plot pieces ──

/** How much gradient survives n layers at a given slope, against the best case. */
private fun chainPlot(slope: Double, best: Double): DkPlot {
    val ys = (0..10).map { k -> log10(slope.pow(k)) } + (0..10).map { k -> log10(best.pow(k)) }
    val (lo, hi) = logRange(ys + 0.0)
    return DkPlot(
        -0.3 to 10.3, lo to hi, logTicks(lo, hi), "0 layers", "10",
        listOf(
            DkLine((0..10).map { DkP(it.toDouble(), log10(best.pow(it))) }, DkInk.Grey, dashed = true),
            DkLine((0..10).map { DkP(it.toDouble(), log10(slope.pow(it))) }, DkInk.Pink, dots = true),
        ),
        listOf(DkDot(DkP(10.0, log10(slope.pow(10))))), axis = false,
    )
}

// ── Sigmoid ──

private fun sigmoidLab(): DkLab = DkLab(DkControl.Stepper, stepper = zStepper(-4.0, 4.0, 0.5, 3.0, 1)) { _, p ->
    val zs = grid(-4.0, 4.0, 0.5)
    val z = zs[p]
    val s = sig(z)
    val d = s * (1 - s)
    val zT = zt(z)
    val ticks = listOf(1.0 to "1", 0.25 to "0.25", 0.0 to "0")
    val sCurve = DkLine(curve(-4.0, 4.0) { sig(it) }, DkInk.Blue)
    val dCurve = DkLine(curve(-4.0, 4.0) { sig(it) * (1 - sig(it)) }, DkInk.Pink)
    fun plot(lines: List<DkLine>, dots: List<DkP>, at: Double = z, rules: List<DkRule> = emptyList()) = DkPlot(
        -4.0 to 4.0, -0.06 to 1.06, ticks, "−4", "+4", lines, dots.map { DkDot(it) }, rules, guide = at to "z = ${zt(at)}",
    )
    val sLine = "σ($zT) = 1/(1 + ${eNeg(z)}) = ${n(s, 3)}"
    val both = listOf(line(DkInk.Blue, "σ(z)"), line(DkInk.Pink, "σ′(z)"))
    val frames = listOf(
        DkFrame(
            "σ(z)", plot(listOf(sCurve), listOf(DkP(z, s))), listOf(line(DkInk.Blue, "σ(z)")),
            listOf("σ(z) = 1/(1 + e^−z)", "σ($zT) = 1/(1 + ${eNeg(z)}) = {${n(s, 3)}}"),
            "Sigmoid squashes any z into (0, 1): σ($zT) = {${n(s, 3)}}.",
            "Large positive z lands near 1, large negative near 0, and σ(0) is exactly 0.5. That's why it reads as a probability.",
        ),
        DkFrame(
            "σ(z) and σ′(z)", plot(listOf(sCurve, dCurve), listOf(DkP(0.0, 0.5), DkP(0.0, 0.25)), 0.0), both,
            listOf("σ′(z) = σ(z)·(1 − σ(z))", "σ′(0) = 0.5·(1 − 0.5) = {0.25}"),
            "Its slope peaks at {0.25}, at z = 0.",
            "That's the most gradient a sigmoid unit can ever pass back: at best it keeps a quarter.",
        ),
        DkFrame(
            "σ(z) and σ′(z)", plot(listOf(sCurve, dCurve), listOf(DkP(z, s), DkP(z, d))), both,
            listOf(sLine, "σ′($zT) = ${n(s, 3)}·(1 − ${n(s, 3)}) = {${n(d, 3)}}"),
            when {
                z >= 2 -> "At z = $zT the output is nearly 1 and the slope is {${n(d, 3)}}."
                z <= -2 -> "At z = $zT the output is nearly 0 and the slope is {${n(d, 3)}}."
                else -> "At z = $zT the output is ${n(s, 3)} and the slope is {${n(d, 3)}}."
            },
            if (d < 0.1) "A saturated unit passes back under ${ceil(d * 100).toInt()}% of its gradient. Even at the 0.25 peak, five layers leave ${n(0.25.pow(5), 4)}."
            else "Near the middle the slope is close to its 0.25 peak. Even so, five layers at the peak leave ${n(0.25.pow(5), 4)}.",
        ),
        DkFrame(
            "gradient left after n sigmoid layers at z = $zT", chainPlot(d, 0.25),
            listOf(line(DkInk.Pink, "σ′($zT)ⁿ"), dashed(DkInk.Grey, "best case 0.25ⁿ")),
            listOf("σ′($zT)¹⁰ = ${n(d, 3)}¹⁰ = {${sci(d.pow(10))}}", "best case 0.25¹⁰ = ${sci(0.25.pow(10))}"),
            "Ten layers at z = $zT leave {${sci(d.pow(10))}} of the gradient.",
            "Every layer multiplies by its σ′. That's the vanishing gradient: ReLU's slope is 1 when active, so it doesn't shrink.",
        ),
        DkFrame(
            "σ(z) at the output", plot(listOf(sCurve), listOf(DkP(z, s)), rules = listOf(DkRule(0.5, DkInk.Grey, thin = true))),
            listOf(line(DkInk.Blue, "σ(z)"), dashed(DkInk.Grey, "0.5 cut")),
            listOf("p = σ($zT) = {${n(s, 3)}}", "class 1 if p > 0.5 → ${if (s > 0.5) "class 1" else "class 0"}"),
            "At the output, σ turns one logit into a {probability}.",
            "Binary classifiers and LSTM gates still use it: there the (0, 1) range is the point, not a problem.",
        ),
    )
    frames
}

// ── Tanh ──

private fun tanhLab(): DkLab = DkLab(DkControl.Stepper, stepper = zStepper(-4.0, 4.0, 0.5, 1.0, 1)) { _, p ->
    val zs = grid(-4.0, 4.0, 0.5)
    val z = zs[p]
    val th = tanh(z)
    val td = 1 - th * th
    val sd = sig(z) * (1 - sig(z))
    val zT = zt(z)
    val ticks = listOf(1.0 to "1", 0.0 to "0", -1.0 to "−1")
    val tCurve = DkLine(curve(-4.0, 4.0) { tanh(it) }, DkInk.Blue)
    val dCurve = DkLine(curve(-4.0, 4.0) { 1 - tanh(it) * tanh(it) }, DkInk.Pink)
    val sCurve = DkLine(curve(-4.0, 4.0) { sig(it) }, DkInk.Grey, dashed = true)
    val sdCurve = DkLine(curve(-4.0, 4.0) { sig(it) * (1 - sig(it)) }, DkInk.Grey, dashed = true)
    fun plot(lines: List<DkLine>, dots: List<DkP>, at: Double = z) =
        DkPlot(-4.0 to 4.0, -1.1 to 1.1, ticks, "−4", "+4", lines, dots.map { DkDot(it) }, guide = at to "z = ${zt(at)}")
    val stretch = "tanh($zT) = 2σ(${zt(2 * z)}) − 1 = 2·${n(sig(2 * z), 3)} − 1 = ${n(th, 3)}"
    val ratio = td / sd
    val header = "tanh(z), tanh′(z) and sigmoid for scale"
    val all = listOf(line(DkInk.Blue, "tanh"), line(DkInk.Pink, "tanh′"), dashed(DkInk.Grey, "sigmoid, σ′"))
    listOf(
        DkFrame(
            "tanh(z)", plot(listOf(tCurve), listOf(DkP(z, th))), listOf(line(DkInk.Blue, "tanh")),
            listOf("tanh(z) = (eᶻ − e⁻ᶻ)/(eᶻ + e⁻ᶻ)", "tanh($zT) = {${n(th, 3)}}"),
            "tanh maps any z into (−1, 1), centred on {0}.",
            "Negative inputs give negative outputs, so a layer of tanh units averages near 0 instead of near 0.5.",
        ),
        DkFrame(
            "tanh(z) and sigmoid for scale", plot(listOf(sCurve, tCurve), listOf(DkP(z, th))),
            listOf(line(DkInk.Blue, "tanh"), dashed(DkInk.Grey, "sigmoid")),
            listOf("tanh(z) = 2σ(2z) − 1", "tanh($zT) = 2·${n(sig(2 * z), 3)} − 1 = {${n(th, 3)}}"),
            "It is a sigmoid {stretched}: twice as tall, twice as steep.",
            "Shift the doubled sigmoid down by 1 and it lands exactly on tanh.",
        ),
        DkFrame(
            header, plot(listOf(sCurve, sdCurve, tCurve, dCurve), listOf(DkP(z, th), DkP(z, td))), all,
            listOf(stretch, "tanh′($zT) = {${n(td, 3)}} vs σ′($zT) = ${n(sd, 3)}"),
            if (ratio >= 1) "At z = $zT, tanh passes back {${n(ratio, 1)}×} more gradient."
            else "At z = $zT, tanh passes back only {${n(ratio, 2)}×} sigmoid's gradient.",
            if (ratio >= 1) "It is a stretched sigmoid centred on zero, so outputs average near 0 and the next layer's updates aren't all one sign."
            else "Steeper means it also flattens sooner: past about z = 2.4 tanh is more saturated than sigmoid.",
        ),
        DkFrame(
            header, plot(listOf(sCurve, sdCurve, tCurve, dCurve), listOf(DkP(0.0, 0.0), DkP(0.0, 1.0)), 0.0), all,
            listOf("tanh′(0) = 1 − tanh²(0) = {1}", "σ′(0) = 0.25"),
            "Its slope peaks at {1}, four times sigmoid's 0.25.",
            "Near zero a tanh unit passes gradient back undiminished. Saturation still kills it: tanh′(3) = ${n(1 - tanh(3.0) * tanh(3.0), 3)}.",
        ),
        DkFrame(
            "gradient left after n tanh layers at z = $zT", chainPlot(td, 1.0),
            listOf(line(DkInk.Pink, "tanh′($zT)ⁿ"), dashed(DkInk.Grey, "best case 1ⁿ")),
            listOf("tanh′($zT)¹⁰ = {${mag(td.pow(10))}}", "σ′($zT)¹⁰ = ${mag(sd.pow(10))}"),
            "Ten layers at z = $zT leave {${mag(td.pow(10))}} of the gradient.",
            if (td > sd) "Far better than sigmoid's ${mag(sd.pow(10))}, but it still shrinks with depth unless every unit sits near 0. That's why deep nets moved to ReLU."
            else "Out here tanh is even more saturated than sigmoid. Deep nets moved to ReLU, whose active slope is exactly 1.",
        ),
    )
}

// ── ReLU: dead units after a bias shift ──

private val reluShifts = listOf(0.0, -3.0, -4.0)

/** Units (of 64) whose pre-activation is ≤ 0 on all 256 samples, for each bias shift. */
private val reluDead: List<Int> by lazy {
    val rng = DkRng(31)
    val xs = List(256) { DoubleArray(8) { rng.g() } }
    val units = List(64) {
        val scale = 0.9 + 1.2 * rng.u()
        Triple(DoubleArray(8) { rng.g() * scale / sqrt(8.0) }, 0.3 * rng.g(), scale)
    }
    reluShifts.map { shift ->
        units.count { (w, b, _) -> xs.all { x -> (0 until 8).sumOf { w[it] * x[it] } + b + shift <= 0 } }
    }
}

private fun reluLab(): DkLab = DkLab(DkControl.Tabs, tabs = listOf("Bias 0", "−3", "−4")) { tab, _ ->
    val z = -1.5
    val ticks = listOf(4.0 to "4", 1.0 to "1", 0.0 to "0")
    val f = DkLine(curve(-4.0, 4.0) { max(0.0, it) }, DkInk.Blue)
    val d = DkLine(listOf(DkP(-4.0, 0.0), DkP(0.0, 0.0), DkP(0.0, 1.0), DkP(4.0, 1.0)), DkInk.Pink)
    fun plot(lines: List<DkLine>, at: Double, y: Double) =
        DkPlot(-4.0 to 4.0, -0.3 to 4.2, ticks, "−4", "+4", lines, listOf(DkDot(DkP(at, y))), guide = at to "z = ${n(at)}")
    val dead = reluDead[tab]
    val legend = listOf(line(DkInk.Blue, "max(0, z)"), line(DkInk.Pink, "derivative: 1 or 0"))
    val chips = listOf(DkChip("dead units", "$dead of 64", tint = true), DkChip("before", "${reluDead[0]}"))
    val shiftText = t(reluShifts[tab])
    val frames = listOf(
        DkFrame(
            "ReLU", plot(listOf(f), 1.5, 1.5), listOf(line(DkInk.Blue, "max(0, z)")),
            listOf("ReLU(z) = max(0, z)", "ReLU(1.5) = 1.5,  ReLU(−1.5) = {0}"),
            "ReLU passes positive z unchanged and {zeros} the rest.",
            "No exponentials, no saturation on the positive side: one comparison per unit.",
        ),
        DkFrame(
            "ReLU and its derivative", plot(listOf(f, d), z, 0.0), legend,
            listOf("ReLU′(z) = 1 if z > 0, else 0", "ReLU′(−1.5) = {0}"),
            "Its slope is {1 or 0}: no shrinking, no middle.",
            "Active units pass gradient back untouched, which is why deep ReLU nets train. Inactive ones pass back nothing.",
        ),
        DkFrame(
            "ReLU and its derivative", plot(listOf(f, d), z, 0.0), legend, emptyList(),
            if (tab == 0) "With no shift, {$dead of 64} units are dead."
            else "A $shiftText bias shift silences {$dead of 64} units for every input.",
            if (tab == 0) "Every unit is positive on some of the 256 samples, so each still gets gradient. Shift the biases to −3 or −4 to push units off."
            else "Their derivative is 0 on all 256 samples, so no gradient reaches them and they never recover. At ${if (tab == 1) "−4 it's ${reluDead[2]}" else "−3 it's ${reluDead[1]}"}.",
            chips,
        ),
        DkFrame(
            "ReLU and a leaky slope",
            plot(listOf(DkLine(curve(-4.0, 4.0) { if (it > 0) it else 0.1 * it }, DkInk.Grey, dashed = true), f, d), z, 0.0),
            listOf(line(DkInk.Blue, "max(0, z)"), line(DkInk.Pink, "derivative"), dashed(DkInk.Grey, "leaky, slope 0.1")),
            listOf("dead: ReLU′ = 0 on every input", "fix: a small slope below 0, lower η, careful init"),
            if (tab == 0) "Nothing is dead here, but {one bad update} can push units off."
            else "A leaky slope would give those {$dead units} a way back.",
            "A dead unit's gradient is exactly 0, so gradient descent can't revive it. A large learning rate or a big negative bias is the usual cause.",
            chips,
        ),
    )
    frames.mapIndexed { i, fr -> DkFrame(fr.header, fr.stage, fr.legend, fr.formula, fr.headline, fr.body, fr.chips, lastAction(i, frames.size)) }
}

// ── Leaky ReLU ──

private val leakyAlphas = listOf(0.01, 0.1, 0.3)
private const val LeakySteps = 1000

/** A unit knocked to bias −4, trained back toward max(0, x): its bias after every step. */
private fun leakyRecovery(alpha: Double): List<Double> {
    val rng = DkRng(53)
    val xs = List(256) { rng.g() }
    var b = -4.0
    val out = ArrayList<Double>()
    out += b
    repeat(LeakySteps) {
        var g = 0.0
        xs.forEach { x ->
            val z = x + b
            val f = if (z > 0) z else alpha * z
            val df = if (z > 0) 1.0 else alpha
            g += 2 * (f - max(0.0, x)) * df
        }
        b -= 0.5 * g / xs.size
        out += b
    }
    return out
}

private val leakyRuns: List<List<Double>> by lazy { leakyAlphas.map { leakyRecovery(it) } }

private fun leakyLab(): DkLab = DkLab(DkControl.Tabs, tabs = listOf("α = 0.01", "0.1", "0.3")) { tab, _ ->
    val a = leakyAlphas[tab]
    val aT = t(a)
    val z = -1.5
    val fz = a * z
    val ticks = listOf(1.0 to "1", 0.0 to "0")
    val f = DkLine(curve(-3.0, 2.0) { if (it > 0) it else a * it }, DkInk.Blue)
    val d = DkLine(listOf(DkP(-3.0, a), DkP(0.0, a), DkP(0.0, 1.0), DkP(2.0, 1.0)), DkInk.Pink)
    val relu = DkLine(curve(-3.0, 2.0) { max(0.0, it) }, DkInk.Grey, dashed = true)
    fun plot(lines: List<DkLine>, dots: List<DkP>) =
        DkPlot(-3.0 to 2.0, -0.35 to 1.35, ticks, "−3", "+2", lines, dots.map { DkDot(it) }, guide = z to "z = ${n(z)}")
    val legend = listOf(line(DkInk.Blue, "max(αz, z)"), line(DkInk.Pink, "derivative"), dashed(DkInk.Grey, "ReLU"))
    val run = leakyRuns[tab]
    val back = run.indexOfFirst { it >= -1.0 }
    val header = "Leaky ReLU and its derivative · negative side"
    val frames = listOf(
        DkFrame(
            "Leaky ReLU · negative side", plot(listOf(relu, f), listOf(DkP(z, fz))),
            listOf(line(DkInk.Blue, "max(αz, z)"), dashed(DkInk.Grey, "ReLU")),
            listOf("f(z) = max(αz, z)", "f(−1.5) = $aT·(−1.5) = {${t(fz, 3)}}"),
            "Leaky ReLU keeps {αz} below zero instead of 0.",
            "Above zero it is ReLU exactly. Below, a slope of α = $aT lets a little signal through.",
        ),
        DkFrame(
            header, plot(listOf(relu, f, d), listOf(DkP(z, fz))), legend,
            listOf("f(−1.5) = $aT·(−1.5) = ${t(fz, 3)}", "f′(−1.5) = {$aT}  ReLU: 0"),
            "A negative unit still gets {${t(a * 100)}%} of its gradient.",
            "Small, but not zero, so a unit that drifts negative can climb back. Dead units in the ReLU lab's −4 layer: 0.",
        ),
        DkFrame(
            "a unit knocked to bias −4, trained back",
            DkPlot(
                0.0 to LeakySteps.toDouble(), -4.3 to 0.4, listOf(0.0 to "0", -2.0 to "−2", -4.0 to "−4"), "step 0", "$LeakySteps",
                listOf(
                    DkLine(listOf(DkP(0.0, -4.0), DkP(LeakySteps.toDouble(), -4.0)), DkInk.Grey, dashed = true),
                    DkLine(run.mapIndexed { i, v -> DkP(i.toDouble(), v) }.filterIndexed { i, _ -> i % 5 == 0 }, DkInk.Blue),
                ),
                if (back >= 0) listOf(DkDot(DkP(back.toDouble(), run[back]))) else listOf(DkDot(DkP(LeakySteps.toDouble(), run.last()))),
                listOf(DkRule(-1.0, DkInk.Grey, thin = true)), axis = false,
            ),
            listOf(line(DkInk.Blue, "bias, α = $aT"), dashed(DkInk.Grey, "ReLU: stuck")),
            listOf("∂L/∂b ∝ (f − target)·f′,  f′ = $aT below 0", if (back >= 0) "bias past −1 after {$back steps}" else "after $LeakySteps steps: {${n(run.last())}}"),
            if (back >= 0) "With α = $aT the unit climbs back in {$back steps}." else "With α = $aT the unit is still climbing: {${n(run.last())}} after $LeakySteps steps.",
            "Its small slope keeps a gradient flowing, so every step nudges the bias up. A ReLU unit in the same spot gets exactly 0 and stays at −4.",
        ),
        DkFrame(
            header, plot(listOf(DkLine(curve(-3.0, 2.0) { it }, DkInk.Grey, dashed = true), f), listOf(DkP(z, fz))),
            listOf(line(DkInk.Blue, "α = $aT"), dashed(DkInk.Grey, "α = 1: a line")),
            listOf("α = 1 → f(z) = z, no non-linearity", "α = $aT → f(−1.5) = {${t(fz, 3)}}"),
            "Bigger α revives faster but is {less non-linear}.",
            "At α = 1 the unit is a straight line and the network collapses to one linear map. 0.01 is the usual default; PReLU learns α instead.",
        ),
    )
    frames.mapIndexed { i, fr -> DkFrame(fr.header, fr.stage, fr.legend, fr.formula, fr.headline, fr.body, fr.chips, lastAction(i, frames.size)) }
}

// ── PReLU: α learned by gradient descent ──

private class PreluFit(val xs: List<Double>, val ys: List<Double>, val alphas: List<Double>, val grad0: Double)

private val preluFit: PreluFit by lazy {
    val rng = DkRng(11)
    val xs = List(40) { -3 + 6 * rng.u() }
    val ys = xs.map { (if (it > 0) it else 0.25 * it) + 0.08 * rng.g() }
    fun grad(a: Double) = xs.indices.sumOf { i ->
        val x = xs[i]
        val f = if (x > 0) x else a * x
        2 * (f - ys[i]) * (if (x < 0) x else 0.0)
    } / xs.size
    val alphas = ArrayList<Double>()
    var a = 0.01
    alphas += a
    repeat(60) {
        a -= 0.1 * grad(a)
        alphas += a
    }
    PreluFit(xs, ys, alphas, grad(0.01))
}

private fun preluLab(): DkLab = DkLab(DkControl.Track) { _, _ ->
    val fit = preluFit
    val ticks = listOf(3.0 to "3", 0.0 to "0", -1.0 to "−1")
    val start = DkLine(curve(-3.0, 3.0) { if (it > 0) it else 0.01 * it }, DkInk.Orange, dashed = true)
    val relu = DkLine(curve(-3.0, 3.0) { max(0.0, it) }, DkInk.Grey, dashed = true)
    val scatter = fit.xs.indices.map { DkP(fit.xs[it], fit.ys[it]) }
    fun plot(a: Double, showStart: Boolean) = DkPlot(
        -3.0 to 3.0, -1.15 to 3.1, ticks, "−3", "+3",
        listOf(relu) + (if (showStart) listOf(start) else emptyList()) + DkLine(curve(-3.0, 3.0) { if (it > 0) it else a * it }, DkInk.Blue),
        listOf(DkDot(DkP(-2.0, -2 * a))), guide = -2.0 to "z = −2", scatter = scatter,
    )
    val a10 = fit.alphas[10]
    val a20 = fit.alphas[20]
    val a60 = fit.alphas[60]
    val header = "fit to data generated with slope 0.25"
    listOf(
        DkFrame(
            header, plot(0.01, false), listOf(line(DkInk.Blue, "α = 0.010"), dashed(DkInk.Grey, "ReLU")),
            listOf("f(z) = z if z > 0, else α·z", "start: α = {0.010}"),
            "PReLU starts at {α = 0.01}, almost ReLU.",
            "The grey points were made with a negative-side slope of 0.25. α is a parameter, so the network can find that slope itself.",
            action = "Compute Gradient",
        ),
        DkFrame(
            header, plot(0.01, false), listOf(line(DkInk.Blue, "α = 0.010"), dashed(DkInk.Grey, "ReLU")),
            listOf("∂f/∂α = z for z < 0, else 0", "∂L/∂α at α = 0.01: {${n(fit.grad0, 3)}}"),
            "The gradient for α comes only from {negative z}.",
            "Every negative point sits below the nearly flat line, so they all pull α up. Positive points don't involve α at all.",
            action = "Train 10 Epochs",
        ),
        DkFrame(
            header, plot(a10, true), listOf(line(DkInk.Blue, "α = ${n(a10, 3)}"), dashed(DkInk.Orange, "α = 0.01 start"), dashed(DkInk.Grey, "ReLU")),
            listOf("∂f/∂α = z for z < 0, else 0", "α: 0.010 → {${n(a10, 3)}} after 10 epochs"),
            "α climbed from 0.01 to {${n(a10, 3)}} on its own.",
            "It is trained by the same gradient descent as the weights, one number per channel. By epoch 60 it settles at ${n(a60, 3)}.",
            action = "Train 10 Epochs",
        ),
        DkFrame(
            header, plot(a20, true), listOf(line(DkInk.Blue, "α = ${n(a20, 3)}"), dashed(DkInk.Orange, "α = 0.01 start"), dashed(DkInk.Grey, "ReLU")),
            listOf("α after 20 epochs: {${n(a20, 3)}}", "data slope: 0.25 (plus noise)"),
            "After 20 epochs α = {${n(a20, 3)}}, the slope the data was made with.",
            "The positive side never changed: α only shapes z < 0. With one α per channel, each channel can learn its own leak.",
            action = "Start Over",
        ),
    )
}

// ── ELU ──

private fun elu(z: Double) = if (z > 0) z else exp(z) - 1

private fun eluLab(): DkLab = DkLab(DkControl.Stepper, stepper = zStepper(-4.0, 3.0, 0.5, -3.0, 1)) { _, p ->
    val zs = grid(-4.0, 3.0, 0.5)
    val z = zs[p]
    val fz = elu(z)
    val dz = if (z > 0) 1.0 else exp(z)
    val zT = zt(z)
    val ticks = listOf(3.0 to "3", 1.0 to "1", 0.0 to "0", -1.0 to "−1")
    val f = DkLine(curve(-4.0, 3.0) { elu(it) }, DkInk.Blue)
    val d = DkLine(curve(-4.0, 3.0) { if (it > 0) 1.0 else exp(it) }, DkInk.Pink)
    val relu = DkLine(curve(-4.0, 3.0) { max(0.0, it) }, DkInk.Grey, dashed = true)
    fun plot(lines: List<DkLine>, dots: List<DkP>, at: Double = z) =
        DkPlot(-4.0 to 3.0, -1.2 to 3.1, ticks, "−4", "+3", lines, dots.map { DkDot(it) }, guide = at to "z = ${zt(at)}")
    val legend = listOf(line(DkInk.Blue, "ELU"), line(DkInk.Pink, "derivative"), dashed(DkInk.Grey, "ReLU"))
    val header = "ELU and its derivative, α = 1"
    val (eluMean, _) = normalMoments { elu(it) }
    val (reluMean, _) = normalMoments { max(0.0, it) }
    val fLine = if (z > 0) "f($zT) = $zT" else "f($zT) = 1·(${eNeg(-z)} − 1) = ${n(fz, 3)}"
    listOf(
        DkFrame(
            "ELU, α = 1", plot(listOf(relu, f), listOf(DkP(z, fz))), listOf(line(DkInk.Blue, "ELU"), dashed(DkInk.Grey, "ReLU")),
            listOf("f(z) = z if z > 0, else α·(eᶻ − 1)", "f($zT) = {${n(fz, 3)}}"),
            "ELU is z above zero and {α(eᶻ − 1)} below.",
            "The negative side curves smoothly down toward −α instead of stopping flat at 0.",
        ),
        DkFrame(
            "ELU, α = 1", plot(listOf(relu, f), listOf(DkP(z, fz))), listOf(line(DkInk.Blue, "ELU"), dashed(DkInk.Grey, "ReLU")),
            listOf("mean ELU(z), z ~ N(0, 1) = {${n(eluMean)}}", "mean ReLU(z) = ${n(reluMean)}"),
            "Negative outputs pull the mean toward {0}: ${n(eluMean)} against ReLU's ${n(reluMean)}.",
            "A mean near 0 keeps the next layer's updates from all sharing one sign, the same reason tanh beats sigmoid.",
        ),
        DkFrame(
            header, plot(listOf(relu, f, d), listOf(DkP(z, fz), DkP(z, dz))), legend,
            listOf(if (z > 0) "f($zT) = {$zT}" else "f($zT) = 1·(${eNeg(-z)} − 1) = {${n(fz, 3)}}", if (z > 0) "f′($zT) = 1" else "f′($zT) = ${eNeg(-z)} = ${n(dz, 3)}"),
            when {
                z <= -1 -> "At z = $zT the output is already {${n(fz)}}, near its floor of −1."
                z <= 0 -> "At z = $zT the output is {${n(fz)}} and the slope ${n(dz)}."
                else -> "At z = $zT ELU is just z: {${n(fz)}}, slope 1."
            },
            if (z <= 0) "Large negative inputs are capped rather than passed on, and the slope is ${n(dz, 3)} rather than ReLU's flat 0."
            else "On the positive side ELU and ReLU are the same line. They differ only below 0.",
        ),
        DkFrame(
            header, plot(listOf(relu, f, d), listOf(DkP(0.0, 0.0), DkP(0.0, 1.0)), 0.0), legend,
            listOf("f′(0⁻) = e⁰ = 1 = f′(0⁺)", fLine),
            "Smooth at 0: the slope is {1} on both sides.",
            "ReLU's slope jumps from 0 to 1 at zero; ELU's doesn't, which suits gradient descent. The cost is an exp for every negative unit.",
        ),
    )
}

// ── SELU: self-normalizing through twenty layers ──

private const val SeluLayers = 20
private const val SeluWidth = 64

private val seluRuns: Pair<DeepRun, DeepRun> by lazy {
    deepRun(Act.Selu, SeluLayers, SeluWidth, sqrt(1.0 / SeluWidth), 5L, batch = 64, backward = false) to
        deepRun(Act.Tanh, SeluLayers, SeluWidth, sqrt(1.0 / SeluWidth), 5L, batch = 64, backward = false)
}

private fun seluLab(): DkLab = DkLab(DkControl.Track) { _, _ ->
    val (selu, th) = seluRuns
    val fixedPoint = normalMoments { actF(Act.Selu, it) }
    fun plot(upTo: Int) = DkPlot(
        0.5 to SeluLayers + 0.5, -0.06 to 1.35, listOf(1.0 to "1", 0.0 to "0"), "layer 1", "layer $SeluLayers",
        listOf(
            DkLine((0 until upTo).map { DkP(it + 1.0, th.variance[it]) }, DkInk.Grey, dashed = true),
            DkLine((0 until upTo).map { DkP(it + 1.0, selu.variance[it]) }, DkInk.Green, dots = true),
        ),
        listOf(DkDot(DkP(upTo.toDouble(), selu.variance[upTo - 1]), r = 6f)),
        listOf(DkRule(1.0, DkInk.Grey, thin = true)), axis = false,
    )
    val header = "activation variance per layer · width $SeluWidth"
    val legend = listOf(line(DkInk.Green, "SELU"), dashed(DkInk.Grey, "tanh, same init"), dashed(DkInk.Grey, "target 1"))
    val def = "SELU(z) = 1.0507·(z, or 1.6733·(eᶻ − 1))"
    val v = selu.variance
    val frames = listOf(
        DkFrame(
            "SELU(z) = λ·ELU_α(z)",
            DkPlot(
                -3.0 to 3.0, -1.9 to 3.3, listOf(3.0 to "3", 1.0 to "1", 0.0 to "0", -1.0 to "−1"), "−3", "+3",
                listOf(DkLine(curve(-3.0, 3.0) { max(0.0, it) }, DkInk.Grey, dashed = true), DkLine(curve(-3.0, 3.0) { actF(Act.Selu, it) }, DkInk.Green)),
            ),
            listOf(line(DkInk.Green, "SELU"), dashed(DkInk.Grey, "ReLU")),
            listOf("SELU(z) = λz if z > 0, else λα(eᶻ − 1)", "λ = {1.0507},  α = 1.6733"),
            "SELU is ELU scaled by {λ = 1.0507}, with α = 1.6733.",
            "Both constants were solved for, not tuned: they are what makes the next step work.",
        ),
        DkFrame(
            header, plot(5), legend, listOf(def, "layer 5: SELU var {${n(v[4])}},  tanh ${n(th.variance[4])}"),
            "Five layers in, SELU holds variance {${n(v[4])}}.",
            "Same inputs, same weights: the tanh stack is already down to ${n(th.variance[4])}, and every layer takes a little more.",
        ),
        DkFrame(
            header, plot(10), legend,
            listOf("z ~ N(0, 1) → mean ${n(fixedPoint.first, 3)}, var {${n(fixedPoint.second, 3)}}", "layer 10: var ${n(v[9])}"),
            "λ and α make (mean 0, variance 1) a {fixed point}.",
            "Feed SELU inputs with mean 0 and variance 1 through weights of variance 1/n and the outputs come back with the same mean and variance, layer after layer.",
        ),
        DkFrame(
            header, plot(SeluLayers), legend,
            listOf(def, "layer $SeluLayers: mean ${n(selu.mean.last())}, var {${n(v.last())}}"),
            "After $SeluLayers layers SELU still has variance {${n(v.last())}}.",
            "λ and α are the fixed point that maps mean 0, variance 1 to itself. tanh with the same init has drifted to ${n(th.variance.last())}.",
        ),
        DkFrame(
            header, plot(SeluLayers), legend,
            listOf("weights: variance 1/n (LeCun init)", "dropout: alpha dropout only"),
            "The guarantee needs {plain dense layers} and LeCun init.",
            "Self-normalizing assumes weights with variance 1/n. Ordinary dropout, skip connections or batch norm break it, which is why SELU stayed niche.",
        ),
    )
    frames.mapIndexed { i, fr -> DkFrame(fr.header, fr.stage, fr.legend, fr.formula, fr.headline, fr.body, fr.chips, lastAction(i, frames.size)) }
}

// ── Swish ──

private val swishBetas = listOf(0.5, 1.0, 2.0)

private fun swish(z: Double, b: Double) = z * sig(b * z)
private fun swishD(z: Double, b: Double) = sig(b * z) + b * z * sig(b * z) * (1 - sig(b * z))

private fun swishLab(): DkLab = DkLab(DkControl.Tabs, tabs = listOf("β = 0.5", "β = 1", "β = 2"), initialTab = 1) { tab, _ ->
    val b = swishBetas[tab]
    val bT = t(b)
    val ticks = listOf(3.0 to "3", 1.0 to "1", 0.0 to "0")
    val f = DkLine(curve(-4.0, 3.0) { swish(it, b) }, DkInk.Blue)
    val d = DkLine(curve(-4.0, 3.0) { swishD(it, b) }, DkInk.Pink)
    val relu = DkLine(curve(-4.0, 3.0) { max(0.0, it) }, DkInk.Grey, dashed = true)
    fun plot(lines: List<DkLine>, dots: List<DkP>, at: Double, rules: List<DkRule> = emptyList(), xHi: Double = 3.0) =
        DkPlot(-4.0 to xHi, -0.65 to max(3.1, xHi + 0.1), ticks, "−4", "+${t(xHi)}", lines, dots.map { DkDot(it) }, rules, guide = at to "z = ${n(at)}")
    val zMin = argMin(-5.0, 0.0, 0.0005) { swish(it, b) }
    val fMin = swish(zMin, b)
    val zPeak = argMin(0.0, 8.0, 0.0005) { -swishD(it, b) }
    val dPeak = swishD(zPeak, b)
    val header = "Swish z·σ(βz), β = $bT"
    val legend = listOf(line(DkInk.Blue, "Swish"), line(DkInk.Pink, "derivative"), dashed(DkInk.Grey, "ReLU"))
    val minLine = if (b == 1.0) "f = ${n(zMin, 3)}·σ(${n(zMin, 3)}) = ${n(zMin, 3)}·${n(sig(b * zMin), 3)} = {${n(fMin, 4)}}"
    else "f = ${n(zMin, 3)}·σ($bT·(${n(zMin, 3)})) = ${n(zMin, 3)}·${n(sig(b * zMin), 3)} = {${n(fMin, 4)}}"
    // The slope peaks at z = 2.40/β, past the usual +3 when β = 0.5.
    val peakHi = max(3.0, ceil(zPeak + 0.5))
    val wide = listOf(
        DkLine(curve(-4.0, peakHi) { max(0.0, it) }, DkInk.Grey, dashed = true),
        DkLine(curve(-4.0, peakHi) { swishD(it, b) }, DkInk.Pink),
        DkLine(curve(-4.0, peakHi) { swish(it, b) }, DkInk.Blue),
    )
    val frames = listOf(
        DkFrame(
            header, plot(listOf(relu, f), listOf(DkP(2.0, swish(2.0, b))), 2.0), listOf(line(DkInk.Blue, "Swish"), dashed(DkInk.Grey, "ReLU")),
            listOf("f(z) = z·σ(βz)", "f(2) = 2·σ(${t(2 * b)}) = {${n(swish(2.0, b), 3)}}"),
            "Swish multiplies z by its own gate {σ(βz)}.",
            "For large positive z the gate is open and f ≈ z; for large negative z it closes and f → 0. In between it's smooth.",
        ),
        DkFrame(
            header, plot(listOf(relu, d, f), listOf(DkP(zMin, fMin)), zMin), legend,
            listOf("min at z = ${n(zMin, 3)}, f′ = 0", minLine),
            "Swish bottoms out at {${n(fMin, 3)}}, then rises back to 0.",
            "It's smooth and not monotone: slightly negative inputs pass a small negative signal instead of being cut off.",
        ),
        DkFrame(
            header,
            plot(
                swishBetas.filter { it != b }.map { o -> DkLine(curve(-4.0, 3.0) { swish(it, o) }, DkInk.Grey, dashed = true) } + relu + f,
                listOf(DkP(zMin, fMin)), zMin,
            ),
            listOf(line(DkInk.Blue, "β = $bT"), dashed(DkInk.Grey, "other β, ReLU")),
            listOf("β → ∞: σ(βz) → step, f → max(0, z)", "β = 0: f = z/2"),
            when (tab) {
                0 -> "At β = 0.5 Swish is gentle, closer to the {line z/2}."
                1 -> "β = 1 sits between a line and ReLU; this is {SiLU}."
                else -> "At β = 2 Swish is already close to {ReLU}."
            },
            "As β grows σ(βz) sharpens into a step and Swish becomes ReLU; at β = 0 it is exactly z/2. A learnable β lets the network choose.",
        ),
        DkFrame(
            header, plot(wide, listOf(DkP(zPeak, dPeak)), zPeak, listOf(DkRule(1.0, DkInk.Grey, thin = true)), peakHi), legend,
            listOf("f′(z) = σ(βz) + βz·σ(βz)(1 − σ(βz))", "max f′ = {${n(dPeak, 3)}} at z = ${n(zPeak)}"),
            "Its slope peaks at {${n(dPeak, 3)}}, a little over 1.",
            "Unlike ReLU it overshoots a slope of 1 before settling back. Found by automated search, it's used in EfficientNet and many language models.",
        ),
    )
    frames.mapIndexed { i, fr -> DkFrame(fr.header, fr.stage, fr.legend, fr.formula, fr.headline, fr.body, fr.chips, lastAction(i, frames.size)) }
}

// ── GELU ──

private fun gelu(z: Double) = z * phi(z)
private fun geluTanh(z: Double) = 0.5 * z * (1 + tanh(sqrt(2 / PI) * (z + 0.044715 * z * z * z)))

private fun geluLab(): DkLab = DkLab(DkControl.Stepper, stepper = zStepper(-3.0, 3.0, 0.25, -0.75, 2)) { _, p ->
    val zs = grid(-3.0, 3.0, 0.25)
    val z = zs[p]
    val ph = phi(z)
    val g = gelu(z)
    val zT = n(z)
    val ticks = listOf(3.0 to "3", 1.0 to "1", 0.0 to "0")
    val f = DkLine(curve(-3.0, 3.0) { gelu(it) }, DkInk.Blue)
    val gate = DkLine(curve(-3.0, 3.0) { phi(it) }, DkInk.Green, dashed = true)
    val relu = DkLine(curve(-3.0, 3.0) { max(0.0, it) }, DkInk.Grey, dashed = true)
    fun plot(lines: List<DkLine>, dots: List<DkP>, at: Double = z) =
        DkPlot(-3.0 to 3.0, -0.4 to 3.1, ticks, "−3", "+3", lines, dots.map { DkDot(it) }, guide = at to "z = ${n(at)}")
    val header = "GELU z·Φ(z), with the gate Φ"
    val legend = listOf(line(DkInk.Blue, "GELU"), dashed(DkInk.Green, "Φ(z) gate"), dashed(DkInk.Grey, "ReLU"))
    val zDev = argMin(-3.0, 3.0, 0.0005) { -abs(gelu(it) - max(0.0, it)) }
    val dev = abs(gelu(zDev) - max(0.0, zDev))
    var approxErr = 0.0
    var x = -3.0
    while (x <= 3.0) {
        approxErr = max(approxErr, abs(gelu(x) - geluTanh(x)))
        x += 0.001
    }
    val phiLine = "Φ($zT) = P(N(0,1) < $zT) = ${n(ph, 3)}"
    listOf(
        DkFrame(
            "GELU z·Φ(z)", plot(listOf(relu, f), listOf(DkP(z, g))), listOf(line(DkInk.Blue, "GELU"), dashed(DkInk.Grey, "ReLU")),
            listOf("GELU(z) = z·Φ(z)", "GELU($zT) = $zT·${n(ph, 3)} = {${n(g, 3)}}"),
            "GELU weights z by {Φ(z)}, the normal CDF.",
            "Where ReLU makes a hard yes-or-no at 0, GELU scales each input by how likely it is to be large.",
        ),
        DkFrame(
            header, plot(listOf(relu, gate, f), listOf(DkP(z, ph))), legend,
            listOf("Φ($zT) = P(N(0,1) < $zT) = {${n(ph, 3)}}", "Φ(−∞) = 0,  Φ(0) = 0.5,  Φ(∞) = 1"),
            "Φ(z) is the chance a standard normal lands {below z}.",
            "It rises smoothly from 0 to 1, so it works as a soft gate: nearly shut for very negative z, nearly open for positive z.",
        ),
        DkFrame(
            header, plot(listOf(relu, gate, f), listOf(DkP(z, ph), DkP(z, g))), legend,
            listOf(phiLine, "GELU = $zT · ${n(ph, 3)} = {${n(g, 3)}}"),
            "At $zT the unit is kept with probability {${n(ph)}}.",
            "GELU is the expected output if each unit were dropped at random by that probability: dropout and ReLU in one function.",
        ),
        DkFrame(
            "GELU against ReLU", plot(listOf(relu, f), listOf(DkP(zDev, gelu(zDev))), zDev),
            listOf(line(DkInk.Blue, "GELU"), dashed(DkInk.Grey, "ReLU")),
            listOf("max |GELU(z) − ReLU(z)| = {${n(dev, 3)}}", "at z = ${n(zDev)}"),
            "GELU and ReLU differ by at most {${n(dev, 3)}}, at z = ${n(zDev)}.",
            "Far from 0 they agree. Near 0 GELU is smooth and dips slightly negative, so small negative inputs still pass a little signal and gradient.",
        ),
        DkFrame(
            "GELU and its tanh approximation",
            plot(listOf(relu, f, DkLine(curve(-3.0, 3.0) { geluTanh(it) }, DkInk.Orange, dashed = true)), listOf(DkP(z, g))),
            listOf(line(DkInk.Blue, "GELU"), dashed(DkInk.Orange, "tanh approx"), dashed(DkInk.Grey, "ReLU")),
            listOf("0.5z·(1 + tanh(√(2/π)·(z + 0.044715z³)))", "max error on [−3, 3]: {${sci(approxErr)}}"),
            "Most libraries offered a {tanh approximation}.",
            "BERT and GPT-2 computed GELU this way. It avoids the CDF and differs by under ${sci(approxErr)} anywhere on this plot.",
        ),
    )
}

// ── Softmax ──

private val smLogits = listOf(2.0, 1.0, 0.1, -0.5)
private val smTemps = listOf(0.5, 1.0, 2.0)

private fun softmaxLab(): DkLab = DkLab(DkControl.Tabs, tabs = listOf("T = 0.5", "T = 1", "T = 2"), initialTab = 1) { tab, _ ->
    val temp = smTemps[tab]
    val tT = t(temp)
    val scaled = smLogits.map { it / temp }
    val ex = scaled.map { exp(it) }
    val sum = ex.sum()
    val p = ex.map { it / sum }
    val mx = scaled.max()
    val shifted = scaled.map { exp(it - mx) }
    val sumShift = shifted.sum()
    val top = p.indices.maxBy { p[it] }
    val labels = (1..4).map { "class $it" }
    val entropy = -p.sumOf { it * ln(it) }
    val expName = if (temp == 1.0) "e^z" else "e^(z/$tT)"
    val shiftName = if (temp == 1.0) "e^(z − ${t(smLogits.max())})" else "e^((z − ${t(smLogits.max())})/$tT)"
    val header = "vector in, vector out"
    val pText = p.joinToString(" ") { n(it, 3) }
    val legendP = listOf(swatch(DkInk.Grey, "logits"), swatch(DkInk.Blue, "probabilities"), swatch(DkInk.Yellow, "top class"))
    val frames = listOf(
        DkFrame(
            header, DkBars(smLogits, ex, expName, 3, labels, null),
            listOf(swatch(DkInk.Grey, "logits"), swatch(DkInk.Blue, expName)),
            listOf("$expName: ${ex.joinToString(" ") { n(it, 3) }}", "sum {${n(sum, 3)}}"),
            "Softmax first makes every logit positive: {$expName}.",
            "Exponentiating keeps the order and turns gaps into ratios: a logit 1 higher gets e ≈ 2.7× the weight.",
        ),
        DkFrame(
            header, DkBars(smLogits, p, "probabilities p", 3, labels, top), legendP,
            listOf("p = $expName / ${n(sum, 3)}", "p = {$pText}"),
            "Divide by the sum and the four add to {1}.",
            "Every output is positive and they sum to one, so softmax turns any scores into a distribution over the classes.",
        ),
        DkFrame(
            header, DkBars(smLogits, p, "probabilities p", 3, labels, top), legendP,
            listOf("$shiftName: ${shifted.joinToString(" ") { n(it, 3) }}", "sum ${n(sumShift, 3)} → p = {$pText}"),
            "Subtracting the max first gives the same {${n(p[top], 3)}}.",
            "Every output shares one denominator, so raising any logit lowers all the others. The four sum to ${n(p.sum(), 4)}.",
        ),
        DkFrame(
            header, DkBars(smLogits, p, "probabilities p", 3, labels, top), legendP,
            listOf("p = softmax(z / T),  T = $tT", "entropy H = {${n(entropy)}} nats (max ln 4 = ${n(ln(4.0))})"),
            when (tab) {
                0 -> "At T = 0.5 the top class takes {${n(p[top])}}: sharper."
                1 -> "At T = 1 the top class takes {${n(p[top])}}: the model's own odds."
                else -> "At T = 2 the top class drops to {${n(p[top])}}: flatter."
            },
            "Dividing the logits by T before softmax sharpens below 1 and flattens above it. Sampling from a language model uses exactly this knob.",
        ),
    )
    frames.mapIndexed { i, fr -> DkFrame(fr.header, fr.stage, fr.legend, fr.formula, fr.headline, fr.body, fr.chips, lastAction(i, frames.size)) }
}
