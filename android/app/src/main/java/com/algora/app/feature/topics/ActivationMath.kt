package com.algora.app.feature.topics

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sqrt
import kotlin.math.tanh

// ── Activation functions ─────────────────────────────────────────────────────
// The estimators behind the ten C2 labs. Each activation is defined once, with its derivative, and
// every claim the labs make about it is measured by running signal through a real stack rather than
// quoted from a table: saturation fractions, dead-unit counts, how mean and variance drift with
// depth, and how much gradient survives twelve layers.
//
// The self-normalising claim for SELU is the one this file exists to make checkable. It is usually
// stated as a property of the constants; here it is a measurement over twenty layers, next to the
// same measurement for every other activation on identical data.

internal class Activation(
    val name: String,
    val f: (Double) -> Double,
    val df: (Double) -> Double,
    /** Init standard deviation this activation is designed for, given a fan-in. */
    val initFor: (Int) -> Double = { sqrt(2.0 / it) },
)

private fun sigmoidOf(z: Double) = 1.0 / (1.0 + exp(-z))

/** Φ(x), the standard normal CDF, via the Abramowitz-Stegun erf approximation. GELU needs it exactly. */
internal fun normalCdf(x: Double): Double {
    val t = 1.0 / (1.0 + 0.2316419 * abs(x))
    val d = 0.3989422804014327 * exp(-x * x / 2.0)
    val p = d * t * (0.319381530 + t * (-0.356563782 + t * (1.781477937 + t * (-1.821255978 + t * 1.330274429))))
    return if (x >= 0) 1.0 - p else p
}

private fun normalPdf(x: Double) = 0.3989422804014327 * exp(-x * x / 2.0)

// SELU's constants are not chosen for aesthetics: they are the fixed point of the mean/variance map,
// solved numerically in the original paper. Nothing else works.
internal const val SeluLambda = 1.0507009873554805
internal const val SeluAlpha = 1.6732632423543772

internal val sigmoidActivation = Activation(
    "Sigmoid", ::sigmoidOf, { val s = sigmoidOf(it); s * (1 - s) }, { sqrt(1.0 / it) },
)
internal val tanhActivation = Activation(
    "Tanh", { tanh(it) }, { 1.0 - tanh(it) * tanh(it) }, { sqrt(1.0 / it) },
)
internal val reluActivation = Activation(
    "ReLU", { max(0.0, it) }, { if (it > 0) 1.0 else 0.0 },
)
internal fun leakyReluActivation(slope: Double = 0.01) = Activation(
    "Leaky ReLU", { if (it > 0) it else slope * it }, { if (it > 0) 1.0 else slope },
)
internal fun eluActivation(alpha: Double = 1.0) = Activation(
    "ELU", { if (it > 0) it else alpha * (exp(it) - 1) }, { if (it > 0) 1.0 else alpha * exp(it) },
)
internal val seluActivation = Activation(
    "SELU",
    { if (it > 0) SeluLambda * it else SeluLambda * SeluAlpha * (exp(it) - 1) },
    { if (it > 0) SeluLambda else SeluLambda * SeluAlpha * exp(it) },
    // LeCun normal. SELU's fixed point is derived assuming exactly this, and it does not hold under He.
    { sqrt(1.0 / it) },
)
internal fun swishActivation(beta: Double = 1.0) = Activation(
    "Swish",
    { it * sigmoidOf(beta * it) },
    { val s = sigmoidOf(beta * it); s + beta * it * s * (1 - s) },
)
internal val geluActivation = Activation(
    "GELU",
    { it * normalCdf(it) },
    { normalCdf(it) + it * normalPdf(it) },
)

/** The tanh approximation shipped in BERT and GPT-2, kept because it is what the weights were trained with. */
internal fun geluTanhApproximation(x: Double): Double =
    0.5 * x * (1.0 + tanh(sqrt(2.0 / PI) * (x + 0.044715 * x * x * x)))

private class ActRng(private var state: Int) {
    fun next(): Double {
        state = state * 1103515245 + 12345
        return ((state ushr 16) and 0x7fff) / 32767.0
    }

    fun gaussian(): Double {
        val u1 = next().coerceAtLeast(1e-9)
        val u2 = next()
        return sqrt(-2.0 * ln(u1)) * cos(2.0 * PI * u2)
    }
}

// ── Depth propagation ────────────────────────────────────────────────────────

internal class LayerStats(
    val layer: Int,
    val mean: Double,
    val std: Double,
    /** Fraction of units whose derivative is below 0.01 — saturated, or dead. */
    val stuckFraction: Double,
    /** Fraction of units that are exactly zero. Only meaningful for the ReLU family. */
    val zeroFraction: Double,
)

internal class PropagationResult(
    val perLayer: List<LayerStats>,
    /** Frobenius norm of ∂L/∂W per layer, input side first. */
    val gradientPerLayer: List<Double>,
) {
    val gradientRatio: Double get() = gradientPerLayer.last() / max(gradientPerLayer.first(), 1e-300)
}

/**
 * Runs a batch through an `layers`-deep stack of the given activation and reports what happened at
 * every layer, forward and backward. Weight init defaults to whatever the activation was designed
 * for, which is the only fair way to compare them — judging SELU under He init or ReLU under LeCun
 * would measure the initialisation rather than the activation.
 */
internal fun propagate(
    activation: Activation,
    layers: Int = 20,
    width: Int = 32,
    batch: Int = 64,
    initScale: Double? = null,
    seed: Int = 71,
): PropagationResult {
    val rng = ActRng(seed)
    val scale = initScale ?: activation.initFor(width)
    val w = Array(layers) { Array(width) { DoubleArray(width) { rng.gaussian() * scale } } }
    var a = Array(batch) { DoubleArray(width) { rng.gaussian() } }
    val target = Array(batch) { DoubleArray(width) { rng.gaussian() * 0.3 } }

    val activations = ArrayList<Array<DoubleArray>>(layers + 1).apply { add(a) }
    val pre = ArrayList<Array<DoubleArray>>(layers)
    val stats = mutableListOf<LayerStats>()

    repeat(layers) { l ->
        val z = Array(batch) { b ->
            DoubleArray(width) { j -> (0 until width).sumOf { i -> w[l][j][i] * a[b][i] } }
        }
        pre += z
        a = Array(batch) { b -> DoubleArray(width) { activation.f(z[b][it]) } }
        activations += a

        val flat = a.flatMap { it.toList() }
        val mean = flat.average()
        val std = sqrt(flat.sumOf { (it - mean) * (it - mean) } / flat.size)
        val derivatives = z.flatMap { row -> row.map { activation.df(it) } }
        stats += LayerStats(
            layer = l + 1,
            mean = mean,
            std = std,
            stuckFraction = derivatives.count { abs(it) < 0.01 }.toDouble() / derivatives.size,
            zeroFraction = flat.count { it == 0.0 }.toDouble() / flat.size,
        )
    }

    // One backward pass, so the gradient side is measured on the same run as the forward side.
    var delta = Array(batch) { b ->
        DoubleArray(width) { j -> (a[b][j] - target[b][j]) * activation.df(pre[layers - 1][b][j]) }
    }
    val gradientNorms = DoubleArray(layers)
    for (l in layers - 1 downTo 0) {
        var sum = 0.0
        for (b in 0 until batch) for (j in 0 until width) for (i in 0 until width) {
            val g = delta[b][j] * activations[l][b][i]
            sum += g * g
        }
        gradientNorms[l] = sqrt(sum)
        if (l > 0) {
            delta = Array(batch) { b ->
                DoubleArray(width) { i ->
                    (0 until width).sumOf { j -> w[l][j][i] * delta[b][j] } * activation.df(pre[l - 1][b][i])
                }
            }
        }
    }
    return PropagationResult(stats, gradientNorms.toList())
}

/**
 * Fraction of units that are dead across the *whole* batch — never active for any input, so their
 * gradient is exactly zero for every example and no update can ever revive them. This is the real
 * dying-ReLU statistic; a unit that is off for one input is not dead, it is just off.
 */
internal fun deadUnitFraction(
    activation: Activation,
    width: Int = 32,
    batch: Int = 64,
    initScale: Double,
    biasShift: Double = 0.0,
    seed: Int = 83,
): Double {
    val rng = ActRng(seed)
    val w = Array(width) { DoubleArray(width) { rng.gaussian() * initScale } }
    val b = DoubleArray(width) { biasShift }
    val x = Array(batch) { DoubleArray(width) { rng.gaussian() } }
    var dead = 0
    for (j in 0 until width) {
        val everActive = (0 until batch).any { s ->
            val z = (0 until width).sumOf { i -> w[j][i] * x[s][i] } + b[j]
            abs(activation.df(z)) > 1e-9
        }
        if (!everActive) dead++
    }
    return dead.toDouble() / width
}

/**
 * Fraction of pre-activations landing in the flat tails, as a function of how wide the incoming
 * signal is. Saturation is not a property of sigmoid alone — it is what sigmoid does *once the
 * pre-activations get large*, which is why the same activation can look fine in a shallow net and
 * stall a deep or badly-initialised one.
 */
internal fun saturationAtScale(activation: Activation, preActivationStd: Double, samples: Int = 20000, seed: Int = 113): Double {
    val rng = ActRng(seed)
    var stuck = 0
    repeat(samples) {
        if (abs(activation.df(rng.gaussian() * preActivationStd)) < 0.01) stuck++
    }
    return stuck.toDouble() / samples
}

/**
 * The dying-ReLU mechanism, run rather than described: train one layer with plain SGD at a given
 * learning rate and count the units that end up inactive for every input in the batch. A unit in
 * that state has gradient exactly zero for every example, so no future update can revive it — the
 * death is permanent, and it is caused by the step size rather than by the initialisation.
 */
internal class DyingReluResult(val deadFraction: Double, val finalLoss: Double, val deadOverTime: List<Double>)

internal fun dyingRelu(
    activation: Activation = reluActivation,
    learningRate: Double,
    steps: Int = 120,
    width: Int = 32,
    batch: Int = 64,
    seed: Int = 127,
): DyingReluResult {
    val rng = ActRng(seed)
    val w = Array(width) { DoubleArray(width) { rng.gaussian() * sqrt(2.0 / width) } }
    val b = DoubleArray(width)
    val x = Array(batch) { DoubleArray(width) { rng.gaussian() } }
    val target = Array(batch) { DoubleArray(width) { rng.gaussian() * 0.5 } }
    val deadOverTime = mutableListOf<Double>()
    var loss = 0.0

    fun deadCount(): Int = (0 until width).count { j ->
        (0 until batch).none { s ->
            abs(activation.df((0 until width).sumOf { i -> w[j][i] * x[s][i] } + b[j])) > 1e-9
        }
    }

    repeat(steps) { step ->
        loss = 0.0
        val gw = Array(width) { DoubleArray(width) }
        val gb = DoubleArray(width)
        for (s in 0 until batch) {
            for (j in 0 until width) {
                val z = (0 until width).sumOf { i -> w[j][i] * x[s][i] } + b[j]
                val out = activation.f(z)
                val err = out - target[s][j]
                loss += err * err
                val d = 2.0 * err * activation.df(z) / (batch * width)
                gb[j] += d
                for (i in 0 until width) gw[j][i] += d * x[s][i]
            }
        }
        loss /= (batch * width)
        for (j in 0 until width) {
            b[j] -= learningRate * gb[j]
            for (i in 0 until width) w[j][i] -= learningRate * gw[j][i]
        }
        if (step % 10 == 0) deadOverTime += deadCount().toDouble() / width
    }
    return DyingReluResult(deadCount().toDouble() / width, loss, deadOverTime)
}

// ── PReLU: the slope as a learned parameter ──────────────────────────────────

internal class PreluRun(val alphaHistory: List<Double>, val lossHistory: List<Double>)

/**
 * Fits PReLU's negative slope by gradient descent against a target function that genuinely wants a
 * particular slope, so α converging on it is a result rather than a decoration.
 */
internal fun trainPrelu(trueSlope: Double = 0.25, steps: Int = 400, lr: Double = 0.05, seed: Int = 97): PreluRun {
    val rng = ActRng(seed)
    val xs = List(128) { rng.gaussian() * 2.0 }
    val targets = xs.map { if (it > 0) it else trueSlope * it }

    var alpha = 0.0
    val alphas = mutableListOf(alpha)
    val losses = mutableListOf<Double>()
    repeat(steps) {
        var loss = 0.0
        var grad = 0.0
        xs.indices.forEach { i ->
            val x = xs[i]
            val out = if (x > 0) x else alpha * x
            val err = out - targets[i]
            loss += err * err
            // ∂out/∂α is x on the negative side and 0 on the positive side.
            if (x <= 0) grad += 2 * err * x
        }
        losses += loss / xs.size
        alpha -= lr * grad / xs.size
        alphas += alpha
    }
    return PreluRun(alphas, losses)
}

// ── Softmax ──────────────────────────────────────────────────────────────────

internal fun softmax(logits: List<Double>, temperature: Double = 1.0): List<Double> {
    val scaled = logits.map { it / temperature }
    // Subtracting the max changes nothing mathematically and everything numerically.
    val shifted = scaled.map { it - scaled.max() }
    val exps = shifted.map { exp(it) }
    val total = exps.sum()
    return exps.map { it / total }
}

/** The naive version, kept so the lab can show it overflowing rather than assert that it would. */
internal fun softmaxNaive(logits: List<Double>): List<Double> {
    val exps = logits.map { exp(it) }
    val total = exps.sum()
    return exps.map { it / total }
}

/** ∂pᵢ/∂zⱼ = pᵢ(δᵢⱼ − pⱼ). A full matrix, unlike every element-wise activation here. */
internal fun softmaxJacobian(probabilities: List<Double>): Array<DoubleArray> =
    Array(probabilities.size) { i ->
        DoubleArray(probabilities.size) { j ->
            probabilities[i] * ((if (i == j) 1.0 else 0.0) - probabilities[j])
        }
    }

internal fun entropyOf(probabilities: List<Double>): Double =
    -probabilities.filter { it > 1e-12 }.sumOf { it * ln(it) }

// ── Shape facts the labs quote ───────────────────────────────────────────────

/** Where a non-monotone activation dips below zero, and by how much. Swish and GELU both do. */
internal fun minimumOf(f: (Double) -> Double, from: Double = -6.0, to: Double = 0.0): Pair<Double, Double> {
    var bestX = from
    var bestY = f(from)
    var x = from
    while (x <= to) {
        val y = f(x)
        if (y < bestY) { bestY = y; bestX = x }
        x += 0.0005
    }
    return bestX to bestY
}

internal fun maxDeviation(a: (Double) -> Double, b: (Double) -> Double, from: Double = -6.0, to: Double = 6.0): Pair<Double, Double> {
    var bestX = from
    var best = 0.0
    var x = from
    while (x <= to) {
        val d = abs(a(x) - b(x))
        if (d > best) { best = d; bestX = x }
        x += 0.001
    }
    return bestX to best
}

/** Mean output over a standard normal input — the "is it zero-centred?" number. */
internal fun meanOutput(activation: Activation, seed: Int = 101, samples: Int = 20000): Double {
    val rng = ActRng(seed)
    return (0 until samples).sumOf { activation.f(rng.gaussian()) } / samples
}
