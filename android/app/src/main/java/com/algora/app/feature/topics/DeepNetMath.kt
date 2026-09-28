package com.algora.app.feature.topics

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sqrt
import kotlin.math.tanh

// ── Deep network fundamentals ────────────────────────────────────────────────
// The estimators behind the four C1 labs. A leaky integrate-and-fire neuron for the biological
// comparison, a real 2-2-1 network trained on XOR by backpropagation, and a deep dense stack whose
// per-layer gradient norms are computed by an actual backward pass rather than by a decay formula.
//
// The last one matters most. Vanishing and exploding gradients are usually taught as an assertion
// about repeated multiplication; here the network is built, run forward, and differentiated, and the
// numbers the frames quote are what came out. `DeepNetMathTest` pins the orderings the copy leans on.

private class NetRng(private var state: Int) {
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

// ── The biological neuron: leaky integrate-and-fire ──────────────────────────

internal class LifTrace(
    /** Membrane potential in mV, sampled every `dt`. */
    val potential: List<Double>,
    /** Times (ms) at which the threshold was crossed. */
    val spikeTimes: List<Double>,
    val dt: Double,
)

internal const val LifRest = -70.0
internal const val LifThreshold = -55.0
internal const val LifReset = -75.0

/**
 * τ dV/dt = −(V − V_rest) + R·I, with a hard threshold, a reset and an absolute refractory period.
 * The simplest neuron model anyone actually uses, and enough to show the two things that matter for
 * the comparison with an artificial unit: integration over time, and an all-or-nothing output.
 */
internal fun lifTrace(
    current: Double,
    milliseconds: Double = 120.0,
    tau: Double = 10.0,
    resistance: Double = 1.0,
    refractory: Double = 2.0,
    dt: Double = 0.1,
): LifTrace {
    val steps = (milliseconds / dt).toInt()
    var v = LifRest
    var refractoryLeft = 0.0
    val potential = mutableListOf<Double>()
    val spikes = mutableListOf<Double>()

    repeat(steps) { i ->
        val t = i * dt
        if (refractoryLeft > 0.0) {
            refractoryLeft -= dt
            v = LifReset
        } else {
            v += dt / tau * (-(v - LifRest) + resistance * current)
            if (v >= LifThreshold) {
                spikes += t
                v = LifReset
                refractoryLeft = refractory
            }
        }
        potential += v
    }
    return LifTrace(potential, spikes, dt)
}

/** Firing rate in Hz for a constant input current — the f–I curve, measured rather than assumed. */
internal fun firingRate(current: Double, milliseconds: Double = 500.0): Double {
    val trace = lifTrace(current, milliseconds)
    return trace.spikeTimes.size / (milliseconds / 1000.0)
}

/**
 * Rheobase: the smallest current that makes the neuron fire at all. Below it the membrane settles
 * short of threshold no matter how long you wait, which is the biological version of a unit that is
 * simply off — and the reason the f–I curve looks like a rectifier.
 */
internal fun rheobase(): Double = (LifThreshold - LifRest)   // with R = 1 and τ cancelling at steady state

// ── Multi-layer perceptron on XOR ────────────────────────────────────────────

internal val xorInputs = listOf(
    doubleArrayOf(0.0, 0.0),
    doubleArrayOf(0.0, 1.0),
    doubleArrayOf(1.0, 0.0),
    doubleArrayOf(1.0, 1.0),
)
internal val xorTargets = doubleArrayOf(0.0, 1.0, 1.0, 0.0)

internal class MlpState(
    val epoch: Int,
    val loss: Double,
    /** Hidden activations for the four XOR inputs. */
    val hidden: List<DoubleArray>,
    val outputs: DoubleArray,
)

internal class MlpRun(
    val history: List<MlpState>,
    val final: MlpState,
    /** True when all four inputs are classified correctly at a 0.5 threshold. */
    val solved: Boolean,
)

/**
 * A 2-2-1 network trained on XOR by full-batch gradient descent, with the hidden non-linearity as a
 * parameter. `linearHidden = true` removes it, which collapses the whole network to a single affine
 * map — and no affine map separates XOR. That is the comparison the lab is built on, and it is run
 * rather than asserted.
 */
internal fun trainXor(
    epochs: Int = 6000,
    learningRate: Double = 0.6,
    linearHidden: Boolean = false,
    hiddenUnits: Int = 2,
    seed: Int = 17,
    snapshotsAt: List<Int> = listOf(0, 200, 1000, 3000, 6000),
): MlpRun {
    val h = hiddenUnits
    val rng = NetRng(seed)
    val w1 = Array(h) { DoubleArray(2) { rng.gaussian() * 0.9 } }
    val b1 = DoubleArray(h) { rng.gaussian() * 0.1 }
    val w2 = DoubleArray(h) { rng.gaussian() * 0.9 }
    var b2 = rng.gaussian() * 0.1

    fun act(z: Double) = if (linearHidden) z else tanh(z)
    fun actGrad(z: Double) = if (linearHidden) 1.0 else 1.0 - tanh(z) * tanh(z)
    fun sigmoidOf(z: Double) = 1.0 / (1.0 + exp(-z))

    val history = mutableListOf<MlpState>()

    fun snapshot(epoch: Int): MlpState {
        val hidden = xorInputs.map { x ->
            DoubleArray(h) { j -> act(w1[j][0] * x[0] + w1[j][1] * x[1] + b1[j]) }
        }
        val outputs = DoubleArray(4) { i ->
            sigmoidOf((0 until h).sumOf { j -> w2[j] * hidden[i][j] } + b2)
        }
        val loss = (0..3).sumOf { i ->
            val p = outputs[i].coerceIn(1e-9, 1 - 1e-9)
            -(xorTargets[i] * ln(p) + (1 - xorTargets[i]) * ln(1 - p))
        } / 4.0
        return MlpState(epoch, loss, hidden, outputs)
    }

    if (0 in snapshotsAt) history += snapshot(0)

    for (epoch in 1..epochs) {
        val gw1 = Array(h) { DoubleArray(2) }
        val gb1 = DoubleArray(h)
        val gw2 = DoubleArray(h)
        var gb2 = 0.0

        xorInputs.forEachIndexed { i, x ->
            val z1 = DoubleArray(h) { j -> w1[j][0] * x[0] + w1[j][1] * x[1] + b1[j] }
            val a1 = DoubleArray(h) { j -> act(z1[j]) }
            val z2 = (0 until h).sumOf { j -> w2[j] * a1[j] } + b2
            val out = sigmoidOf(z2)

            // Cross-entropy with a sigmoid output: the delta is simply (ŷ − y).
            val delta2 = out - xorTargets[i]
            gb2 += delta2
            for (j in 0 until h) {
                gw2[j] += delta2 * a1[j]
                val delta1 = delta2 * w2[j] * actGrad(z1[j])
                gw1[j][0] += delta1 * x[0]
                gw1[j][1] += delta1 * x[1]
                gb1[j] += delta1
            }
        }

        val scale = learningRate / 4.0
        for (j in 0 until h) {
            w1[j][0] -= scale * gw1[j][0]
            w1[j][1] -= scale * gw1[j][1]
            b1[j] -= scale * gb1[j]
            w2[j] -= scale * gw2[j]
        }
        b2 -= scale * gb2

        if (epoch in snapshotsAt) history += snapshot(epoch)
    }

    val final = history.last()
    val solved = (0..3).all { (final.outputs[it] >= 0.5) == (xorTargets[it] >= 0.5) }
    return MlpRun(history, final, solved)
}

/** Loss curve over every epoch, for the plot — cheap enough to recompute at the sampled points. */
internal fun xorLossCurve(
    linearHidden: Boolean,
    epochs: Int = 6000,
    samples: Int = 40,
    hiddenUnits: Int = 2,
    seed: Int = 17,
): List<Pair<Float, Float>> {
    val at = (0..samples).map { it * epochs / samples }
    val run = trainXor(epochs = epochs, linearHidden = linearHidden, hiddenUnits = hiddenUnits, seed = seed, snapshotsAt = at)
    return run.history.map { it.epoch.toFloat() to it.loss.toFloat() }
}

// ── Deep stacks: vanishing and exploding gradients ───────────────────────────

internal enum class DeepActivation { Sigmoid, Tanh, Relu }

internal class DeepGradients(
    /** Frobenius norm of ∂L/∂W per layer, input side first. */
    val perLayer: List<Double>,
    /** Mean absolute activation per layer — the forward-pass side of the same story. */
    val activation: List<Double>,
    val loss: Double,
) {
    val ratio: Double get() = perLayer.last() / max(perLayer.first(), 1e-300)
    fun log10PerLayer(): List<Double> = perLayer.map { log10(max(it, 1e-300)) }
}

/**
 * Builds an L-layer dense stack with the given activation and weight-init scale, runs one forward
 * and one backward pass, and returns the gradient norm at every layer.
 *
 * `initScale` multiplies a unit Gaussian. The named regimes: 1/√n is Xavier (the right choice for
 * tanh), √(2/n) is He (the right choice for ReLU), and anything much larger is the exploding case.
 */
internal fun deepGradients(
    layers: Int = 12,
    width: Int = 16,
    activation: DeepActivation = DeepActivation.Sigmoid,
    initScale: Double = 1.0,
    seed: Int = 29,
): DeepGradients {
    val rng = NetRng(seed)
    val w = Array(layers) { Array(width) { DoubleArray(width) { rng.gaussian() * initScale } } }
    val b = Array(layers) { DoubleArray(width) }
    val x = DoubleArray(width) { rng.gaussian() * 0.5 }
    val target = DoubleArray(width) { rng.gaussian() * 0.5 }

    fun act(z: Double) = when (activation) {
        DeepActivation.Sigmoid -> 1.0 / (1.0 + exp(-z))
        DeepActivation.Tanh -> tanh(z)
        DeepActivation.Relu -> max(0.0, z)
    }

    fun actGrad(z: Double) = when (activation) {
        DeepActivation.Sigmoid -> { val s = 1.0 / (1.0 + exp(-z)); s * (1 - s) }
        DeepActivation.Tanh -> 1.0 - tanh(z) * tanh(z)
        DeepActivation.Relu -> if (z > 0) 1.0 else 0.0
    }

    val a = ArrayList<DoubleArray>(layers + 1).apply { add(x) }
    val z = ArrayList<DoubleArray>(layers)
    repeat(layers) { l ->
        val zl = DoubleArray(width) { j ->
            (0 until width).sumOf { i -> w[l][j][i] * a[l][i] } + b[l][j]
        }
        z += zl
        a += DoubleArray(width) { act(zl[it]) }
    }

    val loss = 0.5 * (0 until width).sumOf { val e = a[layers][it] - target[it]; e * e }

    // Backward pass. delta[l] is ∂L/∂z at layer l; the gradient for W[l] is delta[l] ⊗ a[l-1].
    var delta = DoubleArray(width) { (a[layers][it] - target[it]) * actGrad(z[layers - 1][it]) }
    val norms = DoubleArray(layers)
    for (l in layers - 1 downTo 0) {
        var sum = 0.0
        for (j in 0 until width) for (i in 0 until width) {
            val g = delta[j] * a[l][i]
            sum += g * g
        }
        norms[l] = sqrt(sum)
        if (l > 0) {
            val next = DoubleArray(width) { i ->
                (0 until width).sumOf { j -> w[l][j][i] * delta[j] } * actGrad(z[l - 1][i])
            }
            delta = next
        }
    }

    return DeepGradients(
        perLayer = norms.toList(),
        activation = (1..layers).map { l -> a[l].sumOf { abs(it) } / width },
        loss = loss,
    )
}

/**
 * Global-norm gradient clipping, the standard defence against exploding gradients: if the norm of
 * the whole gradient vector exceeds a threshold, rescale everything by threshold/norm. Rescaling
 * uniformly is the point — it caps the step length without changing the direction.
 */
internal class ClipResult(val scale: Double, val clippedPerLayer: List<Double>, val beforeNorm: Double, val afterNorm: Double)

internal fun clipGlobalNorm(perLayer: List<Double>, threshold: Double): ClipResult {
    val norm = sqrt(perLayer.sumOf { it * it })
    val scale = if (norm > threshold) threshold / norm else 1.0
    val clipped = perLayer.map { it * scale }
    return ClipResult(scale, clipped, norm, sqrt(clipped.sumOf { it * it }))
}

/** The bound the vanishing-gradient argument rests on: max σ'(z) = 0.25, at z = 0. */
internal fun maxSigmoidDerivative(): Double =
    (-60..60).maxOf { val z = it / 10.0; val s = 1.0 / (1.0 + exp(-z)); s * (1 - s) }
