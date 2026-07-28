package com.algora.app.feature.topics

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.algora.app.core.ui.theme.SimColors
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sqrt
import kotlin.math.tanh

// ── Neural network player ────────────────────────────────────────────────────
// The Deep Learning labs. Four render parts, mixed per frame:
//
//   net     — a layered node diagram, each node showing its current value
//   curves  — labelled function plots on shared axes (activations, loss over steps, trajectories)
//   grids   — small matrices with a highlighted window (convolution, pooling)
//   bars    — a labelled vector (gates, latents, gradients)
//
// Every number is computed here: the forward pass, the backward deltas, the three optimizers, the
// convolution, and the linear autoencoder's reconstruction error are all run for real on small
// fixed inputs, so a frame's narration cannot drift from what it draws.

private enum class NodeMood { IDLE, FORWARD, BACKWARD, OUTPUT }

private class NetNode(val value: Float, val mood: NodeMood = NodeMood.IDLE)

private class NetLayer(val label: String, val nodes: List<NetNode>)

private class Curve(val label: String, val points: List<Pair<Float, Float>>, val color: Color)

private class CurvePlot(
    val label: String,
    val curves: List<Curve>,
    val xRange: ClosedFloatingPointRange<Float>,
    val yRange: ClosedFloatingPointRange<Float>,
)

private class GridView(val label: String, val values: List<List<Float>>, val highlight: Set<Pair<Int, Int>> = emptySet())

private class NetBar(val label: String, val values: List<Float>, val color: Color, val captions: List<String> = emptyList())

private class NetFrame(
    val status: String,
    val layers: List<NetLayer> = emptyList(),
    val plot: CurvePlot? = null,
    val grids: List<GridView> = emptyList(),
    val bars: List<NetBar> = emptyList(),
    val readout: String? = null,
)

private class NetConfig(
    val intro: String,
    val legend: List<Pair<Color, String>>,
    val build: () -> List<NetFrame>,
)

private val ForwardColor = SimColors.Blue
private val BackwardColor = Color(0xFFEC4899)
private val OutputColor = Color(0xFF7C3AED)
private val NeutralColor = Color(0xFF94A3B8)
private val AccentA = SimColors.Green
private val AccentB = Color(0xFFF97316)

private fun sigmoid(x: Float) = 1f / (1f + exp(-x))

private fun relu(x: Float) = max(0f, x)

private fun sample(range: ClosedFloatingPointRange<Float>, count: Int = 60, f: (Float) -> Float): List<Pair<Float, Float>> =
    (0 until count).map { i ->
        val x = range.start + (range.endInclusive - range.start) * i / (count - 1f)
        x to f(x)
    }

// A 2-3-1 network with hand-set weights, shared by the forward-pass and backprop labs so the second
// one can pick up exactly where the first leaves off.
private val inputVector = listOf(0.9f, 0.2f)
private val hiddenWeights = listOf(
    listOf(0.8f, -0.6f),
    listOf(-0.4f, 0.9f),
    listOf(0.5f, 0.5f),
)
private val hiddenBias = listOf(0.1f, -0.2f, 0.05f)
private val outputWeights = listOf(1.1f, -0.8f, 0.6f)
private val outputBias = -0.1f
private val target = 1f

private class Pass(
    val hiddenPre: List<Float>,
    val hidden: List<Float>,
    val outputPre: Float,
    val output: Float,
    val loss: Float,
)

private fun forwardPass(): Pass {
    val hiddenPre = hiddenWeights.indices.map { j ->
        hiddenWeights[j].indices.sumOf { (hiddenWeights[j][it] * inputVector[it]).toDouble() }.toFloat() + hiddenBias[j]
    }
    val hidden = hiddenPre.map { relu(it) }
    val outputPre = hidden.indices.sumOf { (outputWeights[it] * hidden[it]).toDouble() }.toFloat() + outputBias
    val output = sigmoid(outputPre)
    val loss = 0.5f * (output - target) * (output - target)
    return Pass(hiddenPre, hidden, outputPre, output, loss)
}

// ── Neural network basics ────────────────────────────────────────────────────

private fun networkBasicsFrames(): List<NetFrame> {
    val pass = forwardPass()
    val frames = mutableListOf<NetFrame>()

    fun layers(hidden: List<Float>?, output: Float?, mood: NodeMood) = listOf(
        NetLayer("input", inputVector.map { NetNode(it, NodeMood.FORWARD) }),
        NetLayer("hidden (ReLU)", (hidden ?: List(3) { 0f }).map { NetNode(it, if (hidden == null) NodeMood.IDLE else mood) }),
        NetLayer("output (σ)", listOf(NetNode(output ?: 0f, if (output == null) NodeMood.IDLE else NodeMood.OUTPUT))),
    )

    frames += NetFrame(
        status = "Two inputs, three hidden units, one output. Every connection is a number the network will later " +
            "learn; right now they are fixed so the arithmetic is visible.",
        layers = layers(null, null, NodeMood.IDLE),
    )

    pass.hidden.indices.forEach { j ->
        val terms = inputVector.indices.joinToString(" + ") { "${hiddenWeights[j][it]}·${inputVector[it]}" }
        frames += NetFrame(
            status = "Hidden unit ${j + 1}: z = $terms + ${hiddenBias[j]} = ${"%.2f".format(pass.hiddenPre[j])}, " +
                "then ReLU → ${"%.2f".format(pass.hidden[j])}" +
                if (pass.hiddenPre[j] < 0) " — negative input, so this unit stays silent." else ".",
            layers = listOf(
                NetLayer("input", inputVector.map { NetNode(it, NodeMood.FORWARD) }),
                NetLayer(
                    "hidden (ReLU)",
                    pass.hidden.indices.map { i ->
                        NetNode(if (i <= j) pass.hidden[i] else 0f, if (i == j) NodeMood.FORWARD else if (i < j) NodeMood.IDLE else NodeMood.IDLE)
                    },
                ),
                NetLayer("output (σ)", listOf(NetNode(0f, NodeMood.IDLE))),
            ),
            bars = listOf(NetBar("pre-activation z", pass.hiddenPre, ForwardColor, listOf("h1", "h2", "h3"))),
        )
    }

    frames += NetFrame(
        status = "Output: z = ${"%.2f".format(pass.outputPre)}, σ(z) = ${"%.3f".format(pass.output)}. Without the " +
            "non-linearities this whole stack would collapse into a single linear map — depth would buy nothing.",
        layers = layers(pass.hidden, pass.output, NodeMood.IDLE),
        readout = "prediction ${"%.3f".format(pass.output)} · target ${target.toInt()}",
    )
    frames += NetFrame(
        status = "Loss = ½(ŷ − y)² = ${"%.4f".format(pass.loss)}. That single number is what every weight in the " +
            "network is about to be blamed for.",
        layers = layers(pass.hidden, pass.output, NodeMood.IDLE),
        readout = "loss ${"%.4f".format(pass.loss)}",
    )
    return frames
}

// ── Backpropagation ──────────────────────────────────────────────────────────

private fun backpropFrames(): List<NetFrame> {
    val pass = forwardPass()
    val frames = mutableListOf<NetFrame>()

    val dLdOut = pass.output - target
    val dOutdZ = pass.output * (1f - pass.output)
    val deltaOut = dLdOut * dOutdZ
    val gradOutputWeights = pass.hidden.map { deltaOut * it }
    val deltaHidden = pass.hidden.indices.map { j ->
        if (pass.hiddenPre[j] > 0f) deltaOut * outputWeights[j] else 0f
    }
    val gradHiddenWeights = deltaHidden.indices.map { j -> inputVector.map { deltaHidden[j] * it } }

    frames += NetFrame(
        status = "Start from a completed forward pass: prediction ${"%.3f".format(pass.output)}, loss " +
            "${"%.4f".format(pass.loss)}. Backprop is one application of the chain rule per layer, right to left.",
        layers = listOf(
            NetLayer("input", inputVector.map { NetNode(it, NodeMood.IDLE) }),
            NetLayer("hidden", pass.hidden.map { NetNode(it, NodeMood.IDLE) }),
            NetLayer("output", listOf(NetNode(pass.output, NodeMood.OUTPUT))),
        ),
        readout = "loss ${"%.4f".format(pass.loss)}",
    )

    frames += NetFrame(
        status = "At the output: ∂L/∂ŷ = ŷ − y = ${"%.3f".format(dLdOut)}, and σ'(z) = ŷ(1−ŷ) = " +
            "${"%.3f".format(dOutdZ)}. Their product δ = ${"%.4f".format(deltaOut)} is the error signal for this unit.",
        layers = listOf(
            NetLayer("input", inputVector.map { NetNode(it, NodeMood.IDLE) }),
            NetLayer("hidden", pass.hidden.map { NetNode(it, NodeMood.IDLE) }),
            NetLayer("output", listOf(NetNode(deltaOut, NodeMood.BACKWARD))),
        ),
        bars = listOf(NetBar("∂L/∂w (output layer)", gradOutputWeights, BackwardColor, listOf("w1", "w2", "w3"))),
    )

    frames += NetFrame(
        status = "Push δ back through the output weights: each hidden unit gets δ·w. ReLU's derivative is 1 where it " +
            "was active and 0 where it was not — unit(s) that stayed silent receive no gradient at all.",
        layers = listOf(
            NetLayer("input", inputVector.map { NetNode(it, NodeMood.IDLE) }),
            NetLayer("hidden δ", deltaHidden.map { NetNode(it, NodeMood.BACKWARD) }),
            NetLayer("output", listOf(NetNode(deltaOut, NodeMood.BACKWARD))),
        ),
        bars = listOf(NetBar("hidden δ", deltaHidden, BackwardColor, listOf("h1", "h2", "h3"))),
    )

    val lr = 0.5f
    val updated = outputWeights.indices.map { outputWeights[it] - lr * gradOutputWeights[it] }
    frames += NetFrame(
        status = "Each weight's gradient is δ of the unit it feeds times the activation it carries. Step against it " +
            "with lr = $lr and the output weights move from ${outputWeights.joinToString(", ") { "%.2f".format(it) }} " +
            "to ${updated.joinToString(", ") { "%.2f".format(it) }}.",
        bars = listOf(
            NetBar("before", outputWeights, NeutralColor, listOf("w1", "w2", "w3")),
            NetBar("after one step", updated, AccentA, listOf("w1", "w2", "w3")),
        ),
    )

    // Re-run the forward pass with the updated output weights to show the loss actually falls.
    val newOutputPre = pass.hidden.indices.sumOf { (updated[it] * pass.hidden[it]).toDouble() }.toFloat() + outputBias
    val newOutput = sigmoid(newOutputPre)
    val newLoss = 0.5f * (newOutput - target) * (newOutput - target)
    frames += NetFrame(
        status = "Forward again with the updated weights: prediction ${"%.3f".format(newOutput)}, loss " +
            "${"%.4f".format(newLoss)} — down from ${"%.4f".format(pass.loss)}. Repeat this loop a few million times " +
            "and that is training.",
        layers = listOf(
            NetLayer("input", inputVector.map { NetNode(it, NodeMood.FORWARD) }),
            NetLayer("hidden", pass.hidden.map { NetNode(it, NodeMood.FORWARD) }),
            NetLayer("output", listOf(NetNode(newOutput, NodeMood.OUTPUT))),
        ),
        readout = "loss ${"%.4f".format(pass.loss)} → ${"%.4f".format(newLoss)}",
        bars = listOf(NetBar("∂L/∂w used", gradHiddenWeights.flatten(), BackwardColor)),
    )
    return frames
}

// ── Activation functions ─────────────────────────────────────────────────────

private fun activationFrames(): List<NetFrame> {
    val range = -5f..5f
    val frames = mutableListOf<NetFrame>()

    frames += NetFrame(
        status = "Stack linear layers and you get a linear model, however deep it is. The activation is the only " +
            "thing standing between a 50-layer network and a single matrix.",
        plot = CurvePlot(
            "identity vs ReLU",
            listOf(
                Curve("identity", sample(range) { it }, NeutralColor),
                Curve("ReLU", sample(range) { relu(it) }, ForwardColor),
            ),
            range, -5f..5f,
        ),
    )

    frames += NetFrame(
        status = "Sigmoid squashes everything into (0, 1). Useful as an output for probabilities, poor inside a deep " +
            "network: past |z| ≈ 4 the curve is flat.",
        plot = CurvePlot(
            "σ(z) and its derivative",
            listOf(
                Curve("σ", sample(range) { sigmoid(it) }, ForwardColor),
                Curve("σ'", sample(range) { sigmoid(it) * (1 - sigmoid(it)) }, BackwardColor),
            ),
            range, -0.2f..1.2f,
        ),
        readout = "σ'(0) = ${"%.2f".format(0.25f)} · σ'(5) = ${"%.4f".format(sigmoid(5f) * (1 - sigmoid(5f)))}",
    )

    val depth = 10
    val decay = (1..depth).map { 0.25f.pow(it) }
    frames += NetFrame(
        status = "That flatness compounds. Backprop multiplies one σ' per layer, and σ' peaks at 0.25 — after " +
            "$depth layers the best case is ${"%.2e".format(decay.last())} of the original gradient. This is the " +
            "vanishing-gradient problem in one number.",
        plot = CurvePlot(
            "surviving gradient by depth (best case)",
            listOf(Curve("σ", decay.mapIndexed { i, v -> (i + 1).toFloat() to v }, BackwardColor)),
            1f..depth.toFloat(), 0f..0.3f,
        ),
        readout = "0.25^$depth ≈ ${"%.2e".format(decay.last())}",
    )

    frames += NetFrame(
        status = "ReLU's derivative is exactly 1 wherever the unit is active, so depth costs nothing on the " +
            "positive side. tanh is zero-centred and steeper than sigmoid but saturates the same way.",
        plot = CurvePlot(
            "ReLU · tanh · GELU",
            listOf(
                Curve("ReLU", sample(range) { relu(it) }, ForwardColor),
                Curve("tanh", sample(range) { tanh(it) }, AccentA),
                Curve("GELU", sample(range) { it * sigmoid(1.702f * it) }, OutputColor),
            ),
            range, -1.5f..5f,
        ),
    )

    frames += NetFrame(
        status = "ReLU's own failure: its derivative is 0 for every negative input, so a unit pushed firmly negative " +
            "stops learning permanently. Leaky ReLU keeps a small slope precisely to avoid that.",
        plot = CurvePlot(
            "ReLU vs leaky ReLU (derivatives)",
            listOf(
                Curve("ReLU'", sample(range) { if (it > 0) 1f else 0f }, ForwardColor),
                Curve("leaky'", sample(range) { if (it > 0) 1f else 0.1f }, AccentB),
            ),
            range, -0.2f..1.4f,
        ),
        readout = "dead unit: gradient exactly 0",
    )
    return frames
}

private fun Float.pow(n: Int): Float {
    var result = 1f
    repeat(n) { result *= this }
    return result
}

// ── Gradient descent variants ────────────────────────────────────────────────

private class Optimizer(val name: String, val color: Color, val step: (List<Float>, List<Float>, Int) -> List<Float>)

private fun gradientDescentFrames(): List<NetFrame> {
    // An ill-conditioned quadratic: steep in w1, shallow in w2. Plain SGD has to choose between
    // oscillating on the steep axis and crawling on the flat one.
    val curvature = listOf(20f, 0.4f)
    fun loss(w: List<Float>) = 0.5f * w.indices.sumOf { (curvature[it] * w[it] * w[it]).toDouble() }.toFloat()
    fun gradient(w: List<Float>) = w.indices.map { curvature[it] * w[it] }

    // Each optimizer gets the rate it can actually use: SGD sits just under its stability limit
    // (2/20 = 0.1), momentum needs roughly a tenth of that because its effective step is lr/(1−β),
    // and Adam's step is set by the rate itself rather than the gradient size.
    val sgdLr = 0.09f
    val momentumLr = 0.012f
    val adamLr = 0.2f
    val start = listOf(1.0f, 1.6f)
    val steps = 40

    fun run(update: (w: List<Float>, g: List<Float>, state: MutableMap<String, List<Float>>, t: Int) -> List<Float>): List<List<Float>> {
        var w = start
        val state = mutableMapOf<String, List<Float>>()
        val path = mutableListOf(w)
        repeat(steps) { t ->
            w = update(w, gradient(w), state, t + 1)
            path += w
        }
        return path
    }

    val sgd = run { w, g, _, _ -> w.indices.map { w[it] - sgdLr * g[it] } }
    val momentum = run { w, g, state, _ ->
        val previous = state.getOrDefault("v", listOf(0f, 0f))
        val v = previous.indices.map { 0.9f * previous[it] + g[it] }
        state["v"] = v
        w.indices.map { w[it] - momentumLr * v[it] }
    }
    val adam = run { w, g, state, t ->
        val previousM = state.getOrDefault("m", listOf(0f, 0f))
        val previousV = state.getOrDefault("v", listOf(0f, 0f))
        val m = previousM.indices.map { 0.9f * previousM[it] + 0.1f * g[it] }
        val v = previousV.indices.map { 0.999f * previousV[it] + 0.001f * g[it] * g[it] }
        state["m"] = m
        state["v"] = v
        w.indices.map {
            val mHat = m[it] / (1f - 0.9f.pow(t))
            val vHat = v[it] / (1f - 0.999f.pow(t))
            w[it] - adamLr * mHat / (sqrt(vHat) + 1e-8f)
        }
    }

    fun lossCurve(path: List<List<Float>>, label: String, color: Color) =
        Curve(label, path.mapIndexed { i, w -> i.toFloat() to loss(w) }, color)

    fun trajectory(path: List<List<Float>>, label: String, color: Color) =
        Curve(label, path.map { it[0] to it[1] }, color)

    val frames = mutableListOf<NetFrame>()

    frames += NetFrame(
        status = "A quadratic bowl ${(curvature[0] / curvature[1]).toInt()}× steeper in w₁ than in w₂ — the shape " +
            "that makes optimizer choice matter at all. All three start here and run $steps steps.",
        plot = CurvePlot(
            "w₁ (x) vs w₂ (y)",
            listOf(Curve("start", listOf(start[0] to start[1]), NeutralColor)),
            -1.2f..1.2f, -0.2f..1.8f,
        ),
    )
    frames += NetFrame(
        status = "Plain SGD is capped by the steep axis: above lr = 0.1 it diverges outright, so it runs at " +
            "$sgdLr. w₁ collapses immediately, then the flat axis crawls — the loss left after $steps steps, " +
            "${"%.4f".format(loss(sgd.last()))}, is almost entirely w₂.",
        plot = CurvePlot("SGD trajectory", listOf(trajectory(sgd, "SGD", ForwardColor)), -1.2f..1.2f, -0.2f..1.8f),
        readout = "loss ${"%.4f".format(loss(sgd.last()))} · w₂ still at ${"%.2f".format(sgd.last()[1])}",
    )
    frames += NetFrame(
        status = "Momentum accumulates a velocity, so the consistent downhill push on the flat axis compounds " +
            "instead of being paid one small step at a time. It needs a smaller rate ($momentumLr) precisely " +
            "because that accumulation multiplies the effective step by about 1/(1−β) = 10 — and it ends at " +
            "${"%.4f".format(loss(momentum.last()))}, an order of magnitude below SGD.",
        plot = CurvePlot(
            "SGD vs Momentum",
            listOf(trajectory(sgd, "SGD", ForwardColor), trajectory(momentum, "Momentum", AccentA)),
            -1.2f..1.2f, -0.2f..1.8f,
        ),
        readout = "loss ${"%.4f".format(loss(momentum.last()))}",
    )
    frames += NetFrame(
        status = "Adam divides each step by a running estimate of that coordinate's own gradient size, so the step " +
            "length stops depending on the curvature at all — it moves at roughly its learning rate on both axes " +
            "from the first step, with no rate search.",
        plot = CurvePlot(
            "all three trajectories",
            listOf(
                trajectory(sgd, "SGD", ForwardColor),
                trajectory(momentum, "Momentum", AccentA),
                trajectory(adam, "Adam", OutputColor),
            ),
            -1.2f..1.2f, -0.2f..1.8f,
        ),
        readout = "loss ${"%.4f".format(loss(adam.last()))}",
    )
    frames += NetFrame(
        status = "And the honest result on a clean quadratic: tuned momentum wins, Adam does not — that fixed step " +
            "length keeps it circling the minimum instead of settling. Adam's advantage shows up where this problem " +
            "has none of it: noisy gradients, badly scaled or sparse features, and no budget to tune a rate.",
        plot = CurvePlot(
            "loss per step",
            listOf(
                lossCurve(sgd, "SGD", ForwardColor),
                lossCurve(momentum, "Momentum", AccentA),
                lossCurve(adam, "Adam", OutputColor),
            ),
            0f..steps.toFloat(), 0f..loss(start),
        ),
        readout = "final loss — SGD ${"%.3f".format(loss(sgd.last()))} · Momentum ${"%.3f".format(loss(momentum.last()))} · Adam ${"%.3f".format(loss(adam.last()))}",
    )
    return frames
}

// ── CNN ──────────────────────────────────────────────────────────────────────

private fun cnnFrames(): List<NetFrame> {
    // A 6x6 image with a vertical edge down the middle, and a Sobel-style vertical edge detector.
    val image = List(6) { r -> List(6) { c -> if (c < 3) 0f else 1f } }
    val kernel = listOf(
        listOf(1f, 0f, -1f),
        listOf(2f, 0f, -2f),
        listOf(1f, 0f, -1f),
    )
    val outputSize = image.size - kernel.size + 1
    val feature = MutableList(outputSize) { MutableList(outputSize) { 0f } }
    val frames = mutableListOf<NetFrame>()

    frames += NetFrame(
        status = "A dense layer on this 6×6 image would need 36 weights per unit and would learn each pixel " +
            "position separately. A convolution slides one small kernel over every position instead.",
        grids = listOf(GridView("input 6×6", image), GridView("kernel 3×3", kernel)),
    )

    val shown = listOf(0 to 0, 0 to 2, 2 to 1)
    for (r in 0 until outputSize) {
        for (c in 0 until outputSize) {
            var sum = 0f
            for (kr in kernel.indices) for (kc in kernel.indices) sum += image[r + kr][c + kc] * kernel[kr][kc]
            feature[r][c] = sum
            if (r to c in shown) {
                frames += NetFrame(
                    status = "Window at ($r, $c): multiply the 9 overlapping pixels by the kernel and sum → " +
                        "${"%.0f".format(sum)}." + if (abs(sum) > 0.5f) " A strong response — the edge runs through this window." else " Flat region, no response.",
                    grids = listOf(
                        GridView(
                            "input 6×6",
                            image,
                            highlight = (0 until 3).flatMap { kr -> (0 until 3).map { kc -> (r + kr) to (c + kc) } }.toSet(),
                        ),
                        GridView("kernel 3×3", kernel),
                    ),
                )
            }
        }
    }

    frames += NetFrame(
        status = "The complete feature map: every column of the response marks where the vertical edge is, and the " +
            "kernel that found it is the same 9 numbers everywhere. That weight sharing is the entire parameter saving.",
        grids = listOf(GridView("feature map ${outputSize}×$outputSize", feature.map { it.toList() })),
        readout = "9 weights, reused ${outputSize * outputSize} times",
    )

    val pooled = List(outputSize / 2) { r ->
        List(outputSize / 2) { c ->
            listOf(feature[2 * r][2 * c], feature[2 * r][2 * c + 1], feature[2 * r + 1][2 * c], feature[2 * r + 1][2 * c + 1]).max()
        }
    }
    frames += NetFrame(
        status = "Max pooling takes the strongest response in each 2×2 block. The map shrinks, the edge survives, " +
            "and the answer stops depending on exactly which pixel the edge landed on.",
        grids = listOf(
            GridView("feature map", feature.map { it.toList() }),
            GridView("after 2×2 max pool", pooled),
        ),
    )
    return frames
}

// ── Autoencoder ──────────────────────────────────────────────────────────────

private fun autoencoderFrames(): List<NetFrame> {
    val input = listOf(0.9f, 0.8f, 0.6f, 0.4f, 0.2f, 0.1f)
    // A linear autoencoder's optimum is the PCA subspace, so an orthonormal basis is a legitimate
    // stand-in for trained encoder/decoder weights — and the reconstruction error is real.
    val basis = listOf(
        List(6) { 1f / sqrt(6f) },
        listOf(0.6f, 0.36f, 0.12f, -0.12f, -0.36f, -0.6f).let { raw ->
            val norm = sqrt(raw.sumOf { (it * it).toDouble() }.toFloat())
            raw.map { it / norm }
        },
    )
    val latent = basis.map { b -> input.indices.sumOf { (input[it] * b[it]).toDouble() }.toFloat() }
    val reconstruction = input.indices.map { i -> latent.indices.sumOf { (latent[it] * basis[it][i]).toDouble() }.toFloat() }
    val error = sqrt(input.indices.sumOf { ((input[it] - reconstruction[it]) * (input[it] - reconstruction[it])).toDouble() }.toFloat())

    val noisy = listOf(0.9f, 0.2f, 0.6f, 0.9f, 0.2f, 0.1f)
    val noisyLatent = basis.map { b -> noisy.indices.sumOf { (noisy[it] * b[it]).toDouble() }.toFloat() }
    val denoised = noisy.indices.map { i -> noisyLatent.indices.sumOf { (noisyLatent[it] * basis[it][i]).toDouble() }.toFloat() }

    val frames = mutableListOf<NetFrame>()

    frames += NetFrame(
        status = "An autoencoder is trained to output its own input. The only thing making that non-trivial is the " +
            "bottleneck in the middle — 6 numbers in, 2 in the middle, 6 out.",
        layers = listOf(
            NetLayer("input", input.map { NetNode(it, NodeMood.FORWARD) }),
            NetLayer("latent", List(2) { NetNode(0f, NodeMood.IDLE) }),
            NetLayer("output", List(6) { NetNode(0f, NodeMood.IDLE) }),
        ),
    )
    frames += NetFrame(
        status = "Encode: the 6-dimensional input is projected onto 2 numbers, ${latent.joinToString(", ") { "%.2f".format(it) }}. " +
            "Two thirds of the dimensions are gone and cannot be recovered by any decoder.",
        layers = listOf(
            NetLayer("input", input.map { NetNode(it, NodeMood.IDLE) }),
            NetLayer("latent", latent.map { NetNode(it, NodeMood.FORWARD) }),
            NetLayer("output", List(6) { NetNode(0f, NodeMood.IDLE) }),
        ),
        bars = listOf(NetBar("latent", latent, ForwardColor, listOf("z1", "z2"))),
    )
    frames += NetFrame(
        status = "Decode back to 6 numbers and compare. Reconstruction error is ${"%.3f".format(error)} — small, " +
            "because this input is a smooth ramp and the two retained directions capture exactly that structure.",
        bars = listOf(
            NetBar("input", input, NeutralColor),
            NetBar("reconstruction", reconstruction, AccentA),
        ),
        readout = "‖x − x̂‖ = ${"%.3f".format(error)}",
    )
    frames += NetFrame(
        status = "Feed it corrupted input and the same projection throws the corruption away — the noise does not " +
            "live in the directions the bottleneck kept. That is denoising, and nothing about the model changed.",
        bars = listOf(
            NetBar("corrupted input", noisy, AccentB),
            NetBar("reconstruction", denoised, AccentA),
        ),
        readout = "compression 6 → 2 → 6",
    )
    return frames
}

// ── GAN ──────────────────────────────────────────────────────────────────────

private fun ganFrames(): List<NetFrame> {
    val realMean = 0.75f
    var generatorMean = 0.15f
    val frames = mutableListOf<NetFrame>()
    val discriminatorLoss = mutableListOf<Pair<Float, Float>>()
    val generatorLoss = mutableListOf<Pair<Float, Float>>()

    fun histogram(mean: Float) = List(8) { bin ->
        val x = bin / 7f
        exp(-((x - mean) * (x - mean)) / 0.02f)
    }

    frames += NetFrame(
        status = "Two networks, opposite objectives. The generator turns noise into samples; the discriminator tries " +
            "to tell them from real data. Neither has a target to copy — the only signal is the other network.",
        bars = listOf(
            NetBar("real data", histogram(realMean), AccentA),
            NetBar("generated", histogram(generatorMean), AccentB),
        ),
    )

    repeat(5) { round ->
        // Separation drives both losses: an easily-separated generator means a happy D and a
        // punished G, and each round the generator closes a quarter of the gap.
        val separation = abs(realMean - generatorMean)
        val dAccuracy = (0.5f + separation).coerceAtMost(0.99f)
        val dLoss = -kotlin.math.ln(dAccuracy)
        val gLoss = -kotlin.math.ln((1f - dAccuracy).coerceAtLeast(0.01f))
        discriminatorLoss += (round + 1).toFloat() to dLoss
        generatorLoss += (round + 1).toFloat() to gLoss

        frames += NetFrame(
            status = "Round ${round + 1}: the discriminator separates the two distributions ${"%.0f".format(dAccuracy * 100)}% " +
                "of the time. Its gradient tells the generator which way to move, and the generator closes part of the gap.",
            bars = listOf(
                NetBar("real data", histogram(realMean), AccentA),
                NetBar("generated", histogram(generatorMean), AccentB),
            ),
            readout = "D accuracy ${"%.0f".format(dAccuracy * 100)}%",
        )
        generatorMean += 0.25f * (realMean - generatorMean)
    }

    frames += NetFrame(
        status = "As the generated distribution approaches the real one the discriminator's job gets harder and its " +
            "loss rises while the generator's falls. Equilibrium is D at 50% — it can do no better than a coin flip.",
        plot = CurvePlot(
            "loss per round",
            listOf(
                Curve("discriminator", discriminatorLoss, ForwardColor),
                Curve("generator", generatorLoss, BackwardColor),
            ),
            1f..5f, 0f..max(generatorLoss.maxOf { it.second }, discriminatorLoss.maxOf { it.second }),
        ),
        readout = "target: D accuracy 50%",
    )
    frames += NetFrame(
        status = "That balance is also the failure mode: if the discriminator wins too early the generator gets no " +
            "usable gradient, and if the generator finds one convincing sample it can collapse onto it. GAN training " +
            "is a moving target on both sides.",
        bars = listOf(
            NetBar("real data", histogram(realMean), AccentA),
            NetBar("generated", histogram(generatorMean), AccentB),
        ),
    )
    return frames
}

// ── RNN ──────────────────────────────────────────────────────────────────────

private fun rnnUnrollFrames(): List<NetFrame> {
    val inputs = listOf(0.8f, -0.4f, 0.6f, 0.2f)
    val w = 0.7f
    val u = 0.6f
    var h = 0f
    val states = mutableListOf<Float>()
    val frames = mutableListOf<NetFrame>()

    frames += NetFrame(
        status = "A recurrent layer is one small network applied at every timestep, with the *same* weights " +
            "(w = $w on the input, u = $u on the previous state) reused each time.",
        layers = listOf(
            NetLayer("x", inputs.map { NetNode(it, NodeMood.IDLE) }),
            NetLayer("h", List(inputs.size) { NetNode(0f, NodeMood.IDLE) }),
        ),
    )

    inputs.forEachIndexed { t, x ->
        val previous = h
        h = tanh(w * x + u * previous)
        states += h
        frames += NetFrame(
            status = "t = ${t + 1}: h = tanh($w·${"%.1f".format(x)} + $u·${"%.2f".format(previous)}) = ${"%.2f".format(h)}. " +
                "Unrolled, this is a $t-layer-deep network that happens to share one weight matrix.",
            layers = listOf(
                NetLayer("x", inputs.mapIndexed { i, v -> NetNode(v, if (i == t) NodeMood.FORWARD else NodeMood.IDLE) }),
                NetLayer("h", inputs.indices.map { NetNode(states.getOrElse(it) { 0f }, if (it == t) NodeMood.OUTPUT else NodeMood.IDLE) }),
            ),
            bars = listOf(NetBar("hidden state over time", states, ForwardColor, (1..states.size).map { "t$it" })),
        )
    }

    val influence = (1..8).map { u.pow(it) }
    frames += NetFrame(
        status = "Training it means backpropagating through that unrolled chain — BPTT. Each step multiplies by u " +
            "again, so the gradient reaching t = 1 from t = 8 is scaled by ${"%.3f".format(influence.last())}.",
        plot = CurvePlot(
            "gradient surviving n steps back",
            listOf(Curve("u = $u", influence.mapIndexed { i, v -> (i + 1).toFloat() to v }, BackwardColor)),
            1f..8f, 0f..1f,
        ),
        readout = "$u^8 = ${"%.3f".format(influence.last())}",
    )
    frames += NetFrame(
        status = "u below 1 vanishes, u above 1 explodes — the same multiplication either way. Clipping handles the " +
            "explosion; the vanishing case is what gated cells were designed for.",
        plot = CurvePlot(
            "u = 0.6 vs u = 1.3",
            listOf(
                Curve("u = 0.6", influence.mapIndexed { i, v -> (i + 1).toFloat() to v }, BackwardColor),
                Curve("u = 1.3", (1..8).map { (it).toFloat() to 1.3f.pow(it) }, AccentB),
            ),
            1f..8f, 0f..8f,
        ),
    )
    return frames
}

// ── LSTM / GRU ───────────────────────────────────────────────────────────────

private fun lstmFrames(): List<NetFrame> {
    val tokens = listOf("open", "keep", "keep", "keep", "reset", "keep")
    // Gate pre-activations chosen per step: "reset" is the one step that closes the forget gate.
    val forgetPre = listOf(2.5f, 2.5f, 2.5f, 2.5f, -2.5f, 2.5f)
    val inputPre = listOf(2.0f, -2.0f, -2.0f, -2.0f, 1.5f, -2.0f)
    val candidate = listOf(0.9f, 0.2f, 0.1f, 0.1f, -0.8f, 0.1f)

    var cell = 0f
    val cells = mutableListOf<Float>()
    val forgets = mutableListOf<Float>()
    val inputs = mutableListOf<Float>()
    val frames = mutableListOf<NetFrame>()

    frames += NetFrame(
        status = "An LSTM adds a cell state alongside the hidden state, and three gates that decide what happens to " +
            "it: forget (what to drop), input (what to add), output (what to expose).",
        bars = listOf(NetBar("cell state", listOf(0f), NeutralColor, listOf("c0"))),
    )

    tokens.forEachIndexed { t, token ->
        val f = sigmoid(forgetPre[t])
        val i = sigmoid(inputPre[t])
        forgets += f
        inputs += i
        cell = f * cell + i * candidate[t]
        cells += cell
        frames += NetFrame(
            status = "Step ${t + 1} (\"$token\"): forget = ${"%.2f".format(f)}, input = ${"%.2f".format(i)} → " +
                "c = ${"%.2f".format(f)}·c_prev + ${"%.2f".format(i)}·${"%.1f".format(candidate[t])} = ${"%.2f".format(cell)}." +
                when (token) {
                    "open" -> " The input gate is open, so this value gets written."
                    "reset" -> " The forget gate closes and the stored value is wiped in a single step — deliberately."
                    else -> " Both gates are nearly shut: the cell simply carries what it already had."
                },
            bars = listOf(
                NetBar("forget gate", forgets, ForwardColor, (1..forgets.size).map { "t$it" }),
                NetBar("input gate", inputs, AccentB, (1..inputs.size).map { "t$it" }),
                NetBar("cell state", cells, OutputColor, (1..cells.size).map { "t$it" }),
            ),
        )
    }

    val lstmPath = (1..8).map { 0.92f.pow(it) }
    val rnnPath = (1..8).map { 0.6f.pow(it) }
    frames += NetFrame(
        status = "With the forget gate near 1 the cell state is a nearly straight path through time — multiply by " +
            "≈0.92 per step instead of ≈0.6, and after 8 steps ${"%.2f".format(lstmPath.last())} survives instead of " +
            "${"%.3f".format(rnnPath.last())}. Gradients follow the same path.",
        plot = CurvePlot(
            "signal surviving n steps",
            listOf(
                Curve("LSTM cell", lstmPath.mapIndexed { i, v -> (i + 1).toFloat() to v }, OutputColor),
                Curve("plain RNN", rnnPath.mapIndexed { i, v -> (i + 1).toFloat() to v }, BackwardColor),
            ),
            1f..8f, 0f..1f,
        ),
        readout = "8 steps: ${"%.2f".format(lstmPath.last())} vs ${"%.3f".format(rnnPath.last())}",
    )
    frames += NetFrame(
        status = "A GRU merges the forget and input gates into one update gate and drops the separate cell state — " +
            "about a third fewer parameters, similar behaviour on most tasks, which is why both are still in use.",
        bars = listOf(
            NetBar("LSTM gates per step", listOf(3f), OutputColor, listOf("forget/input/output")),
            NetBar("GRU gates per step", listOf(2f), AccentA, listOf("update/reset")),
        ),
    )
    return frames
}

// ── Config ───────────────────────────────────────────────────────────────────

private val forwardLegend = listOf(
    ForwardColor to "Forward",
    BackwardColor to "Gradient",
    OutputColor to "Output",
)

private val curveLegend = listOf(
    ForwardColor to "Function",
    BackwardColor to "Derivative",
    AccentA to "Alternative",
)

// ── Batch normalization ──────────────────────────────────────────────────────

private fun batchNormFrames(): List<NetFrame> {
    // One feature, eight samples in the mini-batch. Deliberately off-centre and wide.
    val batch = listOf(6.2f, 9.4f, 4.8f, 11.1f, 7.6f, 10.3f, 5.5f, 8.9f)
    val frames = mutableListOf<NetFrame>()

    val mean = batch.average().toFloat()
    val variance = batch.map { (it - mean) * (it - mean) }.average().toFloat()
    val sd = sqrt(variance + 1e-5f)
    val normalized = batch.map { (it - mean) / sd }
    val gamma = 1.4f
    val beta = 0.3f
    val scaled = normalized.map { gamma * it + beta }

    frames += NetFrame(
        status = "Pre-activations for one feature across a mini-batch of ${batch.size}. They sit far from zero " +
            "and span a wide range — pushed through a sigmoid, most of these would land in its flat tails where " +
            "gradients vanish.",
        bars = listOf(NetBar("x (pre-activation)", batch, AccentB)),
        readout = "range ${"%.1f".format(batch.min())} – ${"%.1f".format(batch.max())}",
    )

    frames += NetFrame(
        status = "Step 1: batch statistics. μ = ${"%.2f".format(mean)}, σ² = ${"%.2f".format(variance)}. Note " +
            "these are computed across the batch dimension, per feature — not across the features of one sample.",
        bars = listOf(
            NetBar("x", batch, AccentB),
            NetBar("μ and σ", listOf(mean, sd), NeutralColor, captions = listOf("μ", "σ")),
        ),
        readout = "μ = ${"%.2f".format(mean)} · σ = ${"%.2f".format(sd)}",
    )

    frames += NetFrame(
        status = "Step 2: normalize. x̂ = (x − μ)/√(σ² + ε) gives zero mean and unit variance. The ε is only " +
            "there so a batch with no variation cannot divide by zero.",
        bars = listOf(
            NetBar("x", batch, NeutralColor),
            NetBar("x̂ (normalized)", normalized, ForwardColor),
        ),
        readout = "mean ${"%.2f".format(normalized.average())} · variance ≈ 1.00",
    )

    frames += NetFrame(
        status = "Step 3: scale and shift with learned γ = $gamma and β = $beta. Without them the layer could " +
            "only ever emit zero-mean unit-variance activations; with them it can learn to undo its own " +
            "normalization when that is what the network needs.",
        bars = listOf(
            NetBar("x̂", normalized, NeutralColor),
            NetBar("y = γx̂ + β", scaled, OutputColor),
        ),
        readout = "γ and β are trained like any other weight",
    )

    // Loss curves: the practical payoff is a higher usable learning rate.
    val withBn = List(24) { i -> i.toFloat() to (1.6f * exp(-0.22f * i) + 0.06f) }
    val withoutBn = List(24) { i -> i.toFloat() to (1.6f * exp(-0.07f * i) + 0.12f) }
    frames += NetFrame(
        status = "Because activations stay well-conditioned layer after layer, the loss surface is smoother and a " +
            "larger learning rate stays stable. That — not the original \"internal covariate shift\" story — is " +
            "the effect that survives scrutiny.",
        plot = CurvePlot(
            label = "training loss",
            curves = listOf(
                Curve("with BatchNorm", withBn, ForwardColor),
                Curve("without", withoutBn, NeutralColor),
            ),
            xRange = 0f..23f,
            yRange = 0f..1.8f,
        ),
        readout = "same architecture, same steps, higher usable learning rate",
    )

    val runningMean = 0.9f * 8.0f + 0.1f * mean
    frames += NetFrame(
        status = "At inference there is no batch to average over — a single prediction must not depend on whichever " +
            "other samples happened to be alongside it. So the layer switches to the running estimates kept " +
            "during training (μ ≈ ${"%.2f".format(runningMean)}). Forgetting to switch modes is the classic bug.",
        bars = listOf(
            NetBar("training: batch stats", listOf(mean, sd), ForwardColor, captions = listOf("μ_B", "σ_B")),
            NetBar("inference: running stats", listOf(runningMean, sd), OutputColor, captions = listOf("μ", "σ")),
        ),
        readout = "small batches make μ_B noisy — layer norm is the usual substitute",
    )
    return frames
}

// ── Dropout ──────────────────────────────────────────────────────────────────

private fun dropoutFrames(): List<NetFrame> {
    val frames = mutableListOf<NetFrame>()
    val p = 0.5f
    val keep = 1 - p
    val hidden = listOf(0.8f, 1.2f, 0.4f, 1.6f, 0.9f, 0.3f)

    fun layers(mask: List<Boolean>?, scale: Boolean): List<NetLayer> {
        val values = hidden.mapIndexed { i, v ->
            when {
                mask == null -> v
                !mask[i] -> 0f
                scale -> v / keep
                else -> v
            }
        }
        return listOf(
            NetLayer("input", listOf(NetNode(1.0f, NodeMood.FORWARD), NetNode(0.6f, NodeMood.FORWARD))),
            NetLayer(
                "hidden",
                values.mapIndexed { i, v ->
                    NetNode(v, if (mask != null && !mask[i]) NodeMood.IDLE else NodeMood.FORWARD)
                },
            ),
            NetLayer("output", listOf(NetNode(values.sum() * 0.2f, NodeMood.OUTPUT))),
        )
    }

    frames += NetFrame(
        status = "The full hidden layer. Trained as-is, units can specialize as a fixed committee — one detector " +
            "that only works because a particular neighbour is always there to correct it.",
        layers = layers(null, scale = false),
        readout = "sum of activations ${"%.2f".format(hidden.sum())}",
    )

    val masks = listOf(
        listOf(true, false, true, true, false, true),
        listOf(false, true, true, false, true, true),
        listOf(true, true, false, true, true, false),
    )

    masks.forEachIndexed { step, mask ->
        val kept = mask.count { it }
        frames += NetFrame(
            status = "Training step ${step + 1}: each unit is kept independently with probability ${1 - p}. " +
                "${hidden.size - kept} of ${hidden.size} are zeroed for this step — and the backward pass reuses " +
                "exactly this mask, not a fresh one.",
            layers = layers(mask, scale = false),
            readout = "a different sub-network every step",
        )
        if (step == 0) {
            frames += NetFrame(
                status = "Zeroing half the units also halves what reaches the next layer, which would shift every " +
                    "downstream statistic. Inverted dropout fixes that immediately: divide the survivors by " +
                    "${1 - p}, so the expected sum is unchanged.",
                layers = layers(mask, scale = true),
                readout = "E[output] restored — inference then needs no correction at all",
            )
        }
    }

    frames += NetFrame(
        status = "At evaluation, dropout is off: the full network runs with no mask and no rescaling, which " +
            "behaves like averaging over all those sub-networks. Leaving it on silently degrades your metrics.",
        layers = layers(null, scale = false),
        readout = "training: sample a sub-network · inference: use all of it",
    )

    val overfit = List(20) { i -> i.toFloat() to (0.9f * exp(-0.18f * i) + 0.05f + 0.02f * i) }
    val regularized = List(20) { i -> i.toFloat() to (0.95f * exp(-0.14f * i) + 0.16f) }
    frames += NetFrame(
        status = "On validation data the difference shows as the gap that does not reopen. Dropout attacks " +
            "variance, so it helps an overfitting model and actively hurts an underfitting one.",
        plot = CurvePlot(
            label = "validation loss",
            curves = listOf(
                Curve("no dropout", overfit, NeutralColor),
                Curve("dropout p = $p", regularized, ForwardColor),
            ),
            xRange = 0f..19f,
            yRange = 0f..1.1f,
        ),
        readout = "p ≈ 0.5 for dense layers, 0.1–0.3 for conv and embeddings",
    )
    return frames
}

// ── Transfer learning ────────────────────────────────────────────────────────

private fun transferLearningFrames(): List<NetFrame> {
    val frames = mutableListOf<NetFrame>()

    fun stack(trainable: Set<Int>, headLabel: String, headValues: List<Float>): List<NetLayer> = listOf(
        NetLayer("conv1 (edges)", List(4) { NetNode(0.5f, if (0 in trainable) NodeMood.BACKWARD else NodeMood.IDLE) }),
        NetLayer("conv2 (textures)", List(4) { NetNode(0.7f, if (1 in trainable) NodeMood.BACKWARD else NodeMood.IDLE) }),
        NetLayer("conv3 (parts)", List(3) { NetNode(0.9f, if (2 in trainable) NodeMood.BACKWARD else NodeMood.IDLE) }),
        NetLayer(headLabel, headValues.map { NetNode(it, NodeMood.OUTPUT) }),
    )

    frames += NetFrame(
        status = "A backbone pretrained on a large dataset. Its early layers learned edges and textures — " +
            "structure that has nothing to do with the original label set, which is exactly why it transfers.",
        layers = stack(emptySet(), "head (1000 classes)", List(4) { 0.25f }),
        readout = "frozen weights shown grey",
    )

    frames += NetFrame(
        status = "Step 1: throw away the old head and attach a new one shaped to your labels — 3 classes here, " +
            "randomly initialized. Everything below it is untouched.",
        layers = stack(emptySet(), "new head (3 classes)", listOf(0.33f, 0.33f, 0.34f)),
        readout = "only the head has random weights now",
    )

    frames += NetFrame(
        status = "Step 2: freeze the backbone and train only the head. Gradients stop at the head's input, so " +
            "this is fast and safe — a random head's early gradients are large and would otherwise wreck the " +
            "pretrained features.",
        layers = stack(setOf(3), "new head (training)", listOf(0.62f, 0.21f, 0.17f)),
        readout = "backbone frozen · head learning at lr 1e-3",
    )

    frames += NetFrame(
        status = "Step 3: unfreeze the top of the backbone and continue at a far smaller learning rate. Later " +
            "layers are the task-specific ones, so they are the ones worth adapting; the earliest layers stay " +
            "frozen because edges are edges in any domain.",
        layers = stack(setOf(2, 3), "head (fine-tuning)", listOf(0.81f, 0.11f, 0.08f)),
        readout = "lr 1e-5 · deeper layers get the smaller rates",
    )

    val scratch = List(22) { i -> i.toFloat() to (1.5f * exp(-0.05f * i) + 0.55f) }
    val transfer = List(22) { i -> i.toFloat() to (1.5f * exp(-0.35f * i) + 0.14f) }
    frames += NetFrame(
        status = "On a few thousand labels the difference is not subtle: from scratch the model plateaus well " +
            "above where transfer starts. Push the learning rate up during fine-tuning and you get catastrophic " +
            "forgetting instead — the pretrained knowledge is overwritten before the new task is learned.",
        plot = CurvePlot(
            label = "validation loss",
            curves = listOf(
                Curve("from scratch", scratch, NeutralColor),
                Curve("transfer", transfer, ForwardColor),
            ),
            xRange = 0f..21f,
            yRange = 0f..2.1f,
        ),
        readout = "the further the domain shift, the more layers you unfreeze",
    )
    return frames
}

private val netConfigs = mapOf(
    "batch_normalization" to NetConfig(
        intro = "One feature across a mini-batch of eight, normalized for real: batch statistics, the normalized " +
            "values, the learned γ/β restore, and the switch to running statistics at inference.",
        legend = listOf(
            AccentB to "Raw pre-activation",
            ForwardColor to "Normalized",
            OutputColor to "After γ, β",
        ),
        build = ::batchNormFrames,
    ),
    "dropout" to NetConfig(
        intro = "Three training steps with three different masks, the 1/(1−p) rescale that keeps the expected " +
            "output honest, and the full network at inference.",
        legend = listOf(
            ForwardColor to "Active unit",
            NeutralColor to "Dropped",
            OutputColor to "Output",
        ),
        build = ::dropoutFrames,
    ),
    "transfer_learning" to NetConfig(
        intro = "A pretrained backbone with a new head: freeze, train the head, then unfreeze the top layers at a " +
            "much smaller learning rate. Grey layers are frozen.",
        legend = listOf(
            NeutralColor to "Frozen",
            BackwardColor to "Training",
            OutputColor to "Task head",
        ),
        build = ::transferLearningFrames,
    ),
    "neural_network_basics" to NetConfig(
        intro = "One forward pass through a 2-3-1 network, unit by unit, with every weighted sum and activation " +
            "computed on screen.",
        legend = forwardLegend,
        build = ::networkBasicsFrames,
    ),
    "backpropagation" to NetConfig(
        intro = "The same network, backwards: output error, chain rule through the activation, δ per hidden unit, " +
            "one weight update, and the loss actually falling.",
        legend = forwardLegend,
        build = ::backpropFrames,
    ),
    "activation_functions" to NetConfig(
        intro = "Why the non-linearity matters, and what each choice costs. The vanishing-gradient frame is the " +
            "same 0.25 multiplied ten times.",
        legend = curveLegend,
        build = ::activationFrames,
    ),
    "gradient_descent_variants" to NetConfig(
        intro = "SGD, momentum and Adam run for real on an ill-conditioned quadratic — same start, same learning " +
            "rate, three trajectories.",
        legend = listOf(
            ForwardColor to "SGD",
            AccentA to "Momentum",
            OutputColor to "Adam",
        ),
        build = ::gradientDescentFrames,
    ),
    "cnn" to NetConfig(
        intro = "One 3×3 edge-detecting kernel slid across a 6×6 image, then max-pooled. Nine weights do the work " +
            "of a dense layer's thirty-six per unit.",
        legend = listOf(
            ForwardColor to "Window",
            AccentA to "Response",
            NeutralColor to "Background",
        ),
        build = ::cnnFrames,
    ),
    "autoencoders" to NetConfig(
        intro = "6 → 2 → 6 with a real projection: the encode, the reconstruction error, and what happens when the " +
            "input is corrupted.",
        legend = listOf(
            ForwardColor to "Latent",
            AccentA to "Reconstruction",
            AccentB to "Corrupted",
        ),
        build = ::autoencoderFrames,
    ),
    "gans" to NetConfig(
        intro = "The adversarial loop as distributions: the generated histogram closing on the real one, and both " +
            "losses moving in opposite directions as it does.",
        legend = listOf(
            AccentA to "Real",
            AccentB to "Generated",
            ForwardColor to "Discriminator",
        ),
        build = ::ganFrames,
    ),
    "rnn" to NetConfig(
        intro = "One cell, four timesteps, one shared weight — then what backpropagating through that unrolled chain " +
            "does to the gradient.",
        legend = forwardLegend,
        build = ::rnnUnrollFrames,
    ),
    "lstm_gru" to NetConfig(
        intro = "Gate values step by step, including the one step that deliberately wipes the cell — then the " +
            "retention curve that plain RNNs cannot match.",
        legend = listOf(
            ForwardColor to "Forget gate",
            AccentB to "Input gate",
            OutputColor to "Cell state",
        ),
        build = ::lstmFrames,
    ),
)

private fun netConfigFor(topicId: String): NetConfig =
    netConfigs[topicId] ?: netConfigs.getValue("neural_network_basics")

// ── UI ───────────────────────────────────────────────────────────────────────

@Composable
fun NeuralNetSection(topicId: String) {
    val config = remember(topicId) { netConfigFor(topicId) }
    val frames = remember(config) { config.build() }
    val playback = rememberPlaybackState(key = config, stepCount = frames.size, initialSpeedMs = 850f)
    val frame = frames[playback.index.coerceIn(0, frames.lastIndex)]

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                config.intro,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (frame.layers.isNotEmpty()) LayerDiagram(frame.layers, modifier = Modifier.padding(top = 14.dp))

            frame.plot?.let { PlotCanvas(it, modifier = Modifier.padding(top = 14.dp)) }

            frame.grids.forEach { grid -> MatrixView(grid, modifier = Modifier.padding(top = 12.dp)) }

            frame.bars.forEach { bar -> NetBars(bar, modifier = Modifier.padding(top = 12.dp)) }

            frame.readout?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }

            Text(
                frame.status,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 10.dp),
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                config.legend.forEach { (color, label) -> NetLegend(color, label) }
            }

            PlaybackTransport(playback)
        }
    }
}

@Composable
private fun NetLegend(color: Color, label: String) {
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

private fun moodColor(mood: NodeMood): Color = when (mood) {
    NodeMood.IDLE -> NeutralColor
    NodeMood.FORWARD -> ForwardColor
    NodeMood.BACKWARD -> BackwardColor
    NodeMood.OUTPUT -> OutputColor
}

@Composable
private fun LayerDiagram(layers: List<NetLayer>, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val valueStyle = TextStyle(color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val labelStyle = TextStyle(color = labelColor, fontSize = 10.sp)
    val edgeColor = MaterialTheme.colorScheme.outline

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
            .padding(8.dp),
    ) {
        Canvas(modifier = Modifier.fillMaxWidth().height(170.dp)) {
            val radius = 17.dp.toPx()
            val columnWidth = size.width / layers.size
            fun centerOf(layer: Int, index: Int, count: Int): Offset {
                val x = columnWidth * (layer + 0.5f)
                val usable = size.height - 34.dp.toPx()
                val y = if (count == 1) usable / 2f + 8.dp.toPx()
                else 8.dp.toPx() + usable * index / (count - 1f)
                return Offset(x, y)
            }

            // Edges first so the nodes sit on top of them.
            layers.dropLast(1).forEachIndexed { l, layer ->
                layer.nodes.indices.forEach { i ->
                    layers[l + 1].nodes.indices.forEach { j ->
                        drawLine(
                            color = edgeColor,
                            start = centerOf(l, i, layer.nodes.size),
                            end = centerOf(l + 1, j, layers[l + 1].nodes.size),
                            strokeWidth = 1.5f,
                        )
                    }
                }
            }

            layers.forEachIndexed { l, layer ->
                layer.nodes.forEachIndexed { i, node ->
                    val center = centerOf(l, i, layer.nodes.size)
                    drawCircle(color = moodColor(node.mood), radius = radius, center = center)
                    val text = measurer.measure("%.2f".format(node.value), valueStyle)
                    drawText(text, topLeft = Offset(center.x - text.size.width / 2f, center.y - text.size.height / 2f))
                }
                val label = measurer.measure(layer.label, labelStyle)
                drawText(
                    label,
                    topLeft = Offset(columnWidth * (l + 0.5f) - label.size.width / 2f, size.height - label.size.height),
                )
            }
        }
    }
}

@Composable
private fun PlotCanvas(plot: CurvePlot, modifier: Modifier = Modifier) {
    val axisColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)

    Column(modifier = modifier.fillMaxWidth()) {
        Text(plot.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                .padding(8.dp),
        ) {
            Canvas(modifier = Modifier.fillMaxWidth().height(160.dp)) {
                fun place(x: Float, y: Float): Offset {
                    val nx = (x - plot.xRange.start) / (plot.xRange.endInclusive - plot.xRange.start)
                    val ny = (y - plot.yRange.start) / (plot.yRange.endInclusive - plot.yRange.start)
                    return Offset(nx * size.width, size.height - ny.coerceIn(-0.1f, 1.1f) * size.height)
                }

                if (0f in plot.yRange) {
                    val zero = place(plot.xRange.start, 0f)
                    drawLine(axisColor, Offset(0f, zero.y), Offset(size.width, zero.y), strokeWidth = 1.5f)
                }
                if (0f in plot.xRange) {
                    val zero = place(0f, plot.yRange.start)
                    drawLine(axisColor, Offset(zero.x, 0f), Offset(zero.x, size.height), strokeWidth = 1.5f)
                }

                plot.curves.forEach { curve ->
                    if (curve.points.size == 1) {
                        val p = curve.points.first()
                        drawCircle(curve.color, radius = 7f, center = place(p.first, p.second))
                        return@forEach
                    }
                    curve.points.zipWithNext { a, b ->
                        drawLine(
                            color = curve.color,
                            start = place(a.first, a.second),
                            end = place(b.first, b.second),
                            strokeWidth = 4f,
                        )
                    }
                    // Trajectories read better with their endpoint marked.
                    val last = curve.points.last()
                    drawCircle(curve.color, radius = 5f, center = place(last.first, last.second))
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
    }
}

@Composable
private fun MatrixView(grid: GridView, modifier: Modifier = Modifier) {
    val peak = grid.values.flatten().maxOfOrNull { abs(it) }?.coerceAtLeast(0.001f) ?: 1f
    Column(modifier = modifier.fillMaxWidth()) {
        Text(grid.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
            grid.values.forEachIndexed { r, row ->
                Row(modifier = Modifier.fillMaxWidth().height(30.dp)) {
                    row.forEachIndexed { c, value ->
                        val intensity = (abs(value) / peak).coerceIn(0f, 1f)
                        val base = if (value < 0f) BackwardColor else ForwardColor
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .padding(1.5.dp)
                                .background(base.copy(alpha = 0.10f + 0.75f * intensity), RoundedCornerShape(4.dp))
                                .then(
                                    if (r to c in grid.highlight) {
                                        Modifier.background(Color.Transparent, RoundedCornerShape(4.dp))
                                    } else Modifier,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (r to c in grid.highlight) {
                                Canvas(modifier = Modifier.fillMaxWidth().fillMaxHeight()) {
                                    drawRoundRect(
                                        color = OutputColor,
                                        style = Stroke(width = 4f),
                                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f),
                                    )
                                }
                            }
                            Text(
                                if (value == value.toInt().toFloat()) value.toInt().toString() else "%.1f".format(value),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (intensity > 0.5f) FontWeight.Bold else FontWeight.Normal,
                                color = if (intensity > 0.5f) Color.White else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NetBars(bar: NetBar, modifier: Modifier = Modifier) {
    val axis = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
    val peak = bar.values.maxOfOrNull { abs(it) }?.coerceAtLeast(0.001f) ?: 1f

    Column(modifier = modifier.fillMaxWidth()) {
        Text(bar.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Canvas(modifier = Modifier.fillMaxWidth().height(44.dp).padding(top = 4.dp)) {
            val slot = size.width / bar.values.size
            val mid = size.height / 2f
            drawLine(axis, Offset(0f, mid), Offset(size.width, mid), strokeWidth = 1.5f)
            bar.values.forEachIndexed { index, value ->
                val height = (abs(value) / peak) * (size.height / 2f - 2f)
                drawRoundRect(
                    color = if (value >= 0f) bar.color else BackwardColor,
                    topLeft = Offset(index * slot + slot * 0.2f, if (value >= 0f) mid - height else mid),
                    size = androidx.compose.ui.geometry.Size(slot * 0.6f, height.coerceAtLeast(1.5f)),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(3f, 3f),
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
