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
import kotlin.math.log10
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

    // The plot box is derived from the paths rather than hand-picked. Momentum overshoots past
    // w₁ = −1.2 on its way in, and a fixed box drew that excursion clamped flat against the edge —
    // which read as a deliberate slide along the boundary instead of the overshoot it is.
    val allPaths = sgd + momentum + adam
    val xPad = 0.15f
    val xLo = allPaths.minOf { it[0] } - xPad
    val xHi = allPaths.maxOf { it[0] } + xPad
    val yLo = allPaths.minOf { it[1] } - xPad
    val yHi = allPaths.maxOf { it[1] } + xPad
    val frames = mutableListOf<NetFrame>()

    frames += NetFrame(
        status = "A quadratic bowl ${(curvature[0] / curvature[1]).toInt()}× steeper in w₁ than in w₂ — the shape " +
            "that makes optimizer choice matter at all. All three start here and run $steps steps.",
        plot = CurvePlot(
            "w₁ (x) vs w₂ (y)",
            listOf(Curve("start", listOf(start[0] to start[1]), NeutralColor)),
            xLo..xHi, yLo..yHi,
        ),
    )
    frames += NetFrame(
        status = "Plain SGD is capped by the steep axis: above lr = 0.1 it diverges outright, so it runs at " +
            "$sgdLr. w₁ collapses immediately, then the flat axis crawls — the loss left after $steps steps, " +
            "${"%.4f".format(loss(sgd.last()))}, is almost entirely w₂.",
        plot = CurvePlot("SGD trajectory", listOf(trajectory(sgd, "SGD", ForwardColor)), xLo..xHi, yLo..yHi),
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
            xLo..xHi, yLo..yHi,
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
            xLo..xHi, yLo..yHi,
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
            // states.toList(): the bar must hold a snapshot. Passing the mutable list meant every
            // frame rendered the final four values while its captions were frozen at build time.
            bars = listOf(NetBar("hidden state over time", states.toList(), ForwardColor, (1..states.size).map { "t$it" })),
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
    // 1.3^8 is 8.16, so the old ceiling of 8 clipped the last point of the exploding curve flat —
    // in a frame whose whole subject is that it does not stay flat.
    val exploding = (1..8).map { it.toFloat() to 1.3f.pow(it) }
    frames += NetFrame(
        status = "u below 1 vanishes, u above 1 explodes — the same multiplication either way. Clipping handles the " +
            "explosion; the vanishing case is what gated cells were designed for.",
        plot = CurvePlot(
            "u = 0.6 vs u = 1.3",
            listOf(
                Curve("u = 0.6", influence.mapIndexed { i, v -> (i + 1).toFloat() to v }, BackwardColor),
                Curve("u = 1.3", exploding, AccentB),
            ),
            1f..8f, 0f..(exploding.maxOf { it.second } * 1.05f),
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
            // Snapshots, for the same reason as the RNN lab above.
            bars = listOf(
                NetBar("forget gate", forgets.toList(), ForwardColor, (1..forgets.size).map { "t$it" }),
                NetBar("input gate", inputs.toList(), AccentB, (1..inputs.size).map { "t$it" }),
                NetBar("cell state", cells.toList(), OutputColor, (1..cells.size).map { "t$it" }),
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

// ── C5 · BPTT ────────────────────────────────────────────────────────────────
// Everything here comes out of `BpttLab`, which trains the same 12-unit recurrent classifier at six
// truncation windows on a task with one informative token nine steps before the decision. The lab
// ends on a comparison the plan did not expect to have to draw: a window that never reaches the cue
// solves the task on every seed, and two shorter ones are coin flips.

private fun bpttFrames(): List<NetFrame> {
    val frames = mutableListOf<NetFrame>()
    val example = BpttLab.trainSet.first { it.label == 1 }
    val steps = (1..BpttLab.LENGTH).map { "t$it" }
    val cueName = if (example.label == 1) "B" else "A"

    frames += NetFrame(
        status = "The task: token 1 is A or B, tokens 2–${BpttLab.LENGTH} are noise, and the answer read off the " +
            "last step is which letter started the sequence. One informative input, ${BpttLab.LENGTH - 1} steps " +
            "of nothing, one decision. Forward, this is easy — the state only has to carry a bit.",
        layers = listOf(
            NetLayer(
                "x",
                example.tokens.mapIndexed { index, token ->
                    NetNode(if (index == 0) 1f else 0.25f, if (index == 0) NodeMood.OUTPUT else NodeMood.IDLE)
                },
            ),
            NetLayer("h", List(BpttLab.LENGTH) { NetNode(0f, if (it == BpttLab.LENGTH - 1) NodeMood.FORWARD else NodeMood.IDLE) }),
        ),
        readout = "cue \"$cueName\" at t1, decision at t${BpttLab.LENGTH}",
    )

    val gradient = BpttLab.stateGradient
    val decay = gradient.last() / gradient.first()
    frames += NetFrame(
        status = "Training it means walking the loss back along that chain — backpropagation through time, one " +
            "multiplication by the recurrent matrix per step. Measured at initialization, ‖∂L/∂h‖ leaves the " +
            "output at ${"%.3f".format(gradient.last())} and arrives at t1 as ${"%.4f".format(gradient.first())}: " +
            "${"%.0f".format(decay)}× smaller after ${BpttLab.LENGTH - 1} steps. Nothing is broken — this is what " +
            "repeated multiplication does.",
        plot = CurvePlot(
            "‖∂L/∂h_t‖, output on the right",
            listOf(Curve("gradient", gradient.mapIndexed { i, v -> (i + 1).toFloat() to v.toFloat() }, BackwardColor)),
            1f..BpttLab.LENGTH.toFloat(), 0f..(gradient.max().toFloat() * 1.1f),
        ),
        readout = "${"%.4f".format(gradient.first())} vs ${"%.3f".format(gradient.last())} — ${"%.0f".format(decay)}×",
    )

    val share = BpttLab.matrixShare
    frames += NetFrame(
        status = "∂L/∂W is a sum over steps, and this is each step's share of it. The last step contributes " +
            "${"%.3f".format(share.last())} and t2 contributes ${"%.4f".format(share[1])}. t1 contributes exactly " +
            "zero — not from decay, but because h₀ is the zero vector, so that step's outer product is zero by " +
            "construction. The cue reaches the input matrix and the recurrent matrix never sees it.",
        bars = listOf(
            NetBar("share of ∂L/∂W per step", share.map { it.toFloat() }, BackwardColor, steps),
        ),
        readout = "t1 share = 0, exactly",
    )

    val window = 3
    frames += NetFrame(
        status = "Truncated BPTT cuts the walk short: keep the forward pass, but stop the gradient after k steps. " +
            "At k = $window only the last $window steps are credited, and the training step stores " +
            "${BpttLab.storedActivations(window)} hidden values instead of ${BpttLab.storedActivations(BpttLab.LENGTH)} " +
            "— which is why long sequences are trained this way at all.",
        bars = listOf(
            NetBar(
                "steps receiving gradient at k = $window",
                (1..BpttLab.LENGTH).map { if (it > BpttLab.LENGTH - window) 1f else 0f },
                ForwardColor,
                steps,
            ),
            NetBar("steps receiving gradient, full BPTT", List(BpttLab.LENGTH) { 1f }, NeutralColor, steps),
        ),
        readout = "${BpttLab.storedActivations(window)} stored values vs ${BpttLab.storedActivations(BpttLab.LENGTH)}",
    )

    val agreement = BpttLab.gradientAgreement
    val magnitude = BpttLab.magnitudeShare.toMap()
    frames += NetFrame(
        status = "And the truncated gradient looks almost exactly like the full one. Cosine similarity is " +
            "${"%.3f".format(agreement.first { it.first == 1 }.second)} with a single step of credit and " +
            "${"%.3f".format(agreement.first { it.first == 3 }.second)} at k = 3. It is not even smaller: at k = 3 it " +
            "carries ${"%.1f".format(magnitude.getValue(3) * 100)}% of the full gradient's magnitude, because the " +
            "dropped terms were partly cancelling the ones that remain.",
        plot = CurvePlot(
            "cos(truncated, full)",
            listOf(
                Curve(
                    "agreement",
                    agreement.map { it.first.toFloat() to it.second.toFloat() },
                    AccentA,
                ),
            ),
            1f..BpttLab.LENGTH.toFloat(), 0.9f..1.01f,
        ),
        readout = "99% aligned at k = 3",
    )

    val trained = BpttLab.trained
    frames += NetFrame(
        status = "So train it and see. Same initialization, same data, same 150 epochs — only the window differs. " +
            "Test accuracy: " + trained.joinToString(", ") { "k=${it.window} ${"%.2f".format(it.testAccuracy)}" } +
            ". The two widest windows solve it; the narrow ones sit near the coin flip while fitting the " +
            "training set, which is memorisation of the noise rather than learning the rule.",
        bars = listOf(
            NetBar("test accuracy", trained.map { it.testAccuracy.toFloat() }, OutputColor, trained.map { "k${it.window}" }),
            NetBar("train accuracy", trained.map { it.trainAccuracy.toFloat() }, NeutralColor, trained.map { "k${it.window}" }),
        ),
        readout = "99% gradient agreement, chance-level accuracy",
    )

    // Two windows across three seeds. This is the frame the batch was rewritten around, so it is
    // computed here rather than asserted: 5 and 9 are the pair that separates.
    val reliability = BpttLab.reliability(listOf(5, 9))
    val short = reliability.first { it.window == 5 }
    val long = reliability.first { it.window == 9 }
    frames += NetFrame(
        status = "One seed is not a result, so here are two windows across ${BpttLab.seeds.size} initializations. " +
            "k = 9 solves it ${long.solved}/${BpttLab.seeds.size} times; k = 5 solves it ${short.solved}/${BpttLab.seeds.size}. " +
            "Note what k = 9 is: it never reaches the step the cue enters. It still works — because W is *shared*, " +
            "so the steps inside the window train the same matrix that carries the cue forward outside it.",
        bars = listOf(
            NetBar("k = 9, per seed", long.accuracies.map { it.toFloat() }, OutputColor, BpttLab.seeds.map { "s$it" }),
            NetBar("k = 5, per seed", short.accuracies.map { it.toFloat() }, BackwardColor, BpttLab.seeds.map { "s$it" }),
        ),
        readout = "the window bounds credit assignment, not memory",
    )

    frames += NetFrame(
        status = "Which leaves the honest summary. Truncation is not a graceful approximation with a knob: below " +
            "the length of the dependency, whether it works is a property of the initialization, and the gradient " +
            "similarity that looks so reassuring above says nothing about it. The real repair for long " +
            "dependencies is a cell whose state has a path that does not multiply — which is the LSTM.",
        bars = listOf(
            NetBar("gradient agreement at k = 3", listOf(agreement.first { it.first == 3 }.second.toFloat()), AccentA, listOf("cosine")),
            NetBar("seeds solved at k = 5", listOf(short.solved.toFloat() / BpttLab.seeds.size), BackwardColor, listOf("fraction")),
        ),
        readout = "similarity is not learnability",
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
            yRange = 0f..1.2f,
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

// ── Neural network basics (phase 9, batch C1) ────────────────────────────────
// Four labs on the same widget: a leaky integrate-and-fire neuron beside the artificial unit that
// abstracts it, a real MLP trained on XOR, and the two failure modes of depth. Everything is
// computed in DeepNetMath.kt by an actual forward and backward pass — the gradient decay in
// particular is measured rather than derived from a decay formula, and `DeepNetMathTest` pins the
// orderings the narration depends on.

private fun log10Curve(values: List<Double>, label: String, color: Color): Curve =
    Curve(label, values.mapIndexed { i, v -> (i + 1).toFloat() to log10(max(v, 1e-300)).toFloat() }, color)

private fun biologicalNeuronFrames(): List<NetFrame> {
    val frames = mutableListOf<NetFrame>()
    val rheo = rheobase()

    val quiet = lifTrace(12.0, 120.0)
    frames += NetFrame(
        status = "A real neuron is a leaky capacitor with a threshold. Charge flows in, the membrane potential rises, " +
            "and it leaks back toward its resting −70 mV the whole time. At 12 units of input current the two balance " +
            "at ${"%.0f".format(quiet.potential.last())} mV — below the −55 mV threshold — and the cell never fires, " +
            "however long you wait.",
        plot = CurvePlot(
            "membrane potential (mV) over 120 ms",
            listOf(
                Curve("V(t)", quiet.potential.mapIndexed { i, v -> (i * quiet.dt).toFloat() to v.toFloat() }, ForwardColor),
                Curve("threshold", listOf(0f to LifThreshold.toFloat(), 120f to LifThreshold.toFloat()), BackwardColor),
            ),
            0f..120f,
            -80f..-50f,
        ),
        readout = "0 spikes · rheobase is ${"%.0f".format(rheo)}",
    )

    val firing = lifTrace(20.0, 120.0)
    frames += NetFrame(
        status = "Push the current to 20 and the potential reaches threshold, the cell spikes, and it is immediately " +
            "reset to −75 mV with a 2 ms refractory period during which nothing can happen. ${firing.spikeTimes.size} " +
            "spikes in 120 ms. Note what the output is: not a number, but a time — the spike is identical every time, " +
            "and all the information is in when it happened.",
        plot = CurvePlot(
            "membrane potential (mV) over 120 ms",
            listOf(
                Curve("V(t)", firing.potential.mapIndexed { i, v -> (i * firing.dt).toFloat() to v.toFloat() }, ForwardColor),
                Curve("threshold", listOf(0f to LifThreshold.toFloat(), 120f to LifThreshold.toFloat()), BackwardColor),
            ),
            0f..120f,
            -80f..-50f,
        ),
        readout = "${firing.spikeTimes.size} spikes · ${"%.0f".format(firingRate(20.0))} Hz",
    )

    val currents = (0..24).map { it * 4.0 }
    val rates = currents.map { firingRate(it) }
    frames += NetFrame(
        status = "Sweep the input current and measure the firing rate at each level, and the neuron's whole " +
            "input-output behaviour appears: flat zero up to the rheobase at ${"%.0f".format(rheo)}, then rising and " +
            "bending over as the refractory period starts to bite. Nobody designed that shape — it falls out of a " +
            "leak, a threshold and a reset.",
        plot = CurvePlot(
            "firing rate (Hz) against input current",
            listOf(Curve("f–I curve", currents.indices.map { currents[it].toFloat() to rates[it].toFloat() }, OutputColor)),
            0f..96f,
            0f..260f,
        ),
        readout = "0 Hz below ${"%.0f".format(rheo)} · ${"%.0f".format(rates.last())} Hz at ${currents.last().toInt()}",
    )

    val peak = rates.max()
    frames += NetFrame(
        status = "Now the artificial unit, and the comparison is the point of this topic. It computes Σwᵢxᵢ + b and " +
            "passes it through an activation — and the two standard choices are shaped like the curve above. ReLU is " +
            "the rectifying part, off below a threshold and rising after it. Sigmoid is the saturating part. The f–I " +
            "curve, normalised, sits between them. The abstraction was not arbitrary.",
        plot = CurvePlot(
            "normalised response",
            listOf(
                Curve("f–I (normalised)", currents.indices.map { currents[it].toFloat() to (rates[it] / peak).toFloat() }, OutputColor),
                Curve("ReLU", sample(0f..96f) { ((it - rheo.toFloat()) / 60f).coerceIn(0f, 1f) }, AccentA),
                Curve("sigmoid", sample(0f..96f) { sigmoid((it - 30f) / 12f) }, AccentB),
            ),
            0f..96f,
            0f..1.1f,
        ),
        readout = "one number in, one number out",
    )

    frames += NetFrame(
        status = "And what the abstraction throws away, which is most of it. Time: the artificial unit has no state " +
            "between inputs, so spike timing — which carries real information — has nowhere to live. Dendrites: they " +
            "compute non-linearly before the soma sees anything, so one neuron is closer to a small network than to " +
            "one unit. Neurotransmitters, glia, and the fact that the brain does not run backpropagation, for which " +
            "no biological mechanism has ever been found. \"Neural network\" is a metaphor that stopped being a model " +
            "of the brain around 1960, and it is worth knowing which one you are talking about.",
        layers = listOf(
            NetLayer("inputs", listOf(NetNode(0.9f, NodeMood.FORWARD), NetNode(0.2f, NodeMood.FORWARD), NetNode(0.5f, NodeMood.FORWARD))),
            NetLayer("Σwx+b", listOf(NetNode(0.72f, NodeMood.IDLE))),
            NetLayer("activation", listOf(NetNode(0.67f, NodeMood.OUTPUT))),
        ),
        readout = "a useful caricature, not a simulation",
    )
    return frames
}

private fun mlpFrames(): List<NetFrame> {
    val frames = mutableListOf<NetFrame>()
    val run = trainXor(hiddenUnits = 3, seed = 11)

    frames += NetFrame(
        status = "XOR: output 1 when exactly one input is 1. Four points, and no straight line separates them — " +
            "(0,0) and (1,1) belong together in one class while (0,1) and (1,0) belong together in the other, and " +
            "they sit on opposite diagonals. A single perceptron cannot do this, which is the result that stopped " +
            "the field for most of the 1970s.",
        grids = listOf(
            GridView(
                "x₁, x₂ → y",
                xorInputs.indices.map { listOf(xorInputs[it][0].toFloat(), xorInputs[it][1].toFloat(), xorTargets[it].toFloat()) },
                highlight = setOf(0 to 2, 1 to 2, 2 to 2, 3 to 2),
            ),
        ),
    )

    fun layersAt(state: MlpState, row: Int) = listOf(
        NetLayer("input", xorInputs[row].map { NetNode(it.toFloat(), NodeMood.FORWARD) }),
        NetLayer("hidden (tanh)", state.hidden[row].map { NetNode(it.toFloat(), NodeMood.IDLE) }),
        NetLayer("output (σ)", listOf(NetNode(state.outputs[row].toFloat(), NodeMood.OUTPUT))),
    )

    run.history.forEach { state ->
        val correct = (0..3).count { (state.outputs[it] >= 0.5) == (xorTargets[it] >= 0.5) }
        frames += NetFrame(
            status = if (state.epoch == 0) {
                "The fix is a hidden layer: 2 inputs, 3 tanh units, 1 sigmoid output. At initialisation the weights " +
                    "are random and every output sits near 0.5 — the network has no opinion yet. Loss " +
                    "${"%.4f".format(state.loss)}."
            } else {
                "Epoch ${state.epoch}. Full-batch gradient descent, gradients from backpropagation, no tricks. Loss " +
                    "${"%.4f".format(state.loss)}, $correct of 4 correct. Outputs: " +
                    state.outputs.joinToString { "%.3f".format(it) } + "."
            },
            layers = layersAt(state, 1),
            bars = listOf(NetBar("output per input", state.outputs.map { it.toFloat() }, ForwardColor, listOf("00", "01", "10", "11"))),
            readout = "epoch ${state.epoch} · loss ${"%.4f".format(state.loss)}",
        )
    }

    frames += NetFrame(
        status = "Here is what the hidden layer bought. Each row is one input's coordinates in hidden space — three " +
            "numbers instead of two. The network did not learn XOR directly; it learned a *representation* in which " +
            "XOR is linearly separable, and then solved the easy problem. That is what every layer in every deep " +
            "network is doing, and the reason depth is worth anything at all.",
        grids = listOf(
            GridView(
                "hidden activations, one row per input",
                run.final.hidden.map { row -> row.map { it.toFloat() } },
            ),
        ),
        readout = "final loss ${"%.4f".format(run.final.loss)} · solved: ${if (run.solved) "yes" else "no"}",
    )

    val linear = trainXor(hiddenUnits = 3, seed = 11, linearHidden = true)
    frames += NetFrame(
        status = "Remove the tanh and keep everything else — same architecture, same training, same number of " +
            "weights. The loss stops at ${"%.4f".format(linear.final.loss)} and every output is exactly 0.500. That " +
            "number is ln 2, the loss of a model that predicts a coin flip: a stack of linear maps is a linear map, " +
            "so three hidden units with no non-linearity are worth exactly as much as none. The activation function " +
            "is not a detail bolted onto the architecture — without it there is no architecture.",
        plot = CurvePlot(
            "training loss",
            listOf(
                Curve("tanh hidden", xorLossCurve(false, hiddenUnits = 3, seed = 11), AccentA),
                Curve("linear hidden", xorLossCurve(true, hiddenUnits = 3, seed = 11), BackwardColor),
            ),
            0f..6000f,
            0f..0.8f,
        ),
        readout = "linear: ${"%.4f".format(linear.final.loss)} = ln 2",
    )

    val widths = listOf(2, 3, 4)
    val seeds = listOf(11, 17, 23, 31, 41)
    val solveCounts = widths.map { h -> h to seeds.count { trainXor(hiddenUnits = h, seed = it).solved } }
    frames += NetFrame(
        status = "One last thing, measured rather than assumed. Two hidden units is the textbook minimum for XOR and " +
            "it is genuinely enough — but running the same training from five different random initialisations, it " +
            "converges only ${solveCounts[0].second} times out of ${seeds.size}. Three units: " +
            "${solveCounts[1].second} of ${seeds.size}. Four: ${solveCounts[2].second} of ${seeds.size}. The failures " +
            "are local minima, not bugs. Extra width does not make the network more expressive here; it makes the " +
            "loss surface easier to descend, which is a different and underrated reason real networks are wider than " +
            "they need to be.",
        bars = listOf(
            NetBar(
                "runs solved out of ${seeds.size}",
                solveCounts.map { it.second.toFloat() },
                AccentA,
                widths.map { "$it hidden" },
            ),
        ),
        readout = "expressiveness is not the same as trainability",
    )
    return frames
}

private fun vanishingGradientFrames(): List<NetFrame> {
    val frames = mutableListOf<NetFrame>()
    val maxDerivative = maxSigmoidDerivative()

    frames += NetFrame(
        status = "Backpropagation multiplies. The gradient reaching layer l is a product of every activation " +
            "derivative and weight matrix between l and the loss — so whatever those factors do on average, they do " +
            "it once per layer. Here is the sigmoid and its derivative: the derivative peaks at " +
            "${"%.2f".format(maxDerivative)} at z = 0 and falls away fast on both sides. That maximum is a hard " +
            "ceiling, and it is less than a half.",
        plot = CurvePlot(
            "σ(z) and σ′(z)",
            listOf(
                Curve("σ(z)", sample(-8f..8f) { sigmoid(it) }, ForwardColor),
                Curve("σ′(z)", sample(-8f..8f) { sigmoid(it) * (1 - sigmoid(it)) }, BackwardColor),
            ),
            -8f..8f,
            -0.1f..1.1f,
        ),
        readout = "max σ′ = ${"%.2f".format(maxDerivative)}",
    )

    val sigmoidRun = deepGradients(activation = DeepActivation.Sigmoid, initScale = 1.0)
    frames += NetFrame(
        status = "A 12-layer network, 16 units wide, sigmoid throughout. One forward pass, one backward pass, and " +
            "the Frobenius norm of ∂L/∂W measured at every layer — nothing here is a formula for the decay, it is " +
            "the decay. The bars are the mean activation per layer, which is healthy: the forward pass looks fine, " +
            "and that is exactly why this failure is hard to notice.",
        bars = listOf(
            NetBar(
                "mean |activation| per layer",
                sigmoidRun.activation.map { it.toFloat() },
                ForwardColor,
                (1..12).map { if (it % 3 == 0) "$it" else "" },
            ),
        ),
        readout = "forward pass: no sign of a problem",
    )

    val reluRun = deepGradients(activation = DeepActivation.Relu, initScale = sqrt(2.0 / 16.0))
    frames += NetFrame(
        status = "The backward pass, on a log scale because a linear one would draw eleven of the twelve bars as " +
            "nothing. Sigmoid: layer 1's gradient is ${"%.1e".format(sigmoidRun.perLayer.first())} against layer 12's " +
            "${"%.1e".format(sigmoidRun.perLayer.last())} — a factor of ${"%.0f".format(sigmoidRun.ratio)}. ReLU with " +
            "He initialisation on the identical architecture stays flat to within a factor of " +
            "${"%.1f".format(1.0 / reluRun.ratio)}. The layers nearest the input are the ones that learn slowest, " +
            "and those are the layers that decide what features exist at all.",
        plot = CurvePlot(
            "log₁₀ ‖∂L/∂W‖ by layer",
            listOf(
                log10Curve(sigmoidRun.perLayer, "sigmoid", BackwardColor),
                log10Curve(reluRun.perLayer, "ReLU + He init", AccentA),
            ),
            1f..12f,
            -4f..2f,
        ),
        readout = "sigmoid: ${"%.0f".format(sigmoidRun.ratio)}× weaker at the input",
    )

    val small = deepGradients(activation = DeepActivation.Sigmoid, initScale = 0.25)
    val large = deepGradients(activation = DeepActivation.Sigmoid, initScale = 1.5)
    frames += NetFrame(
        status = "Initialisation cannot rescue it, only trade one problem for another. At an init scale of 0.25 the " +
            "weights themselves shrink the signal too and the ratio worsens to " +
            "${"%.0e".format(small.ratio)}; at 1.5 it improves to ${"%.0f".format(large.ratio)} but the units start " +
            "saturating, where σ′ is near zero anyway. There is no scale at which a deep sigmoid stack propagates " +
            "gradients cleanly, because the ceiling of ${"%.2f".format(maxDerivative)} does not move.",
        plot = CurvePlot(
            "log₁₀ ‖∂L/∂W‖ by layer, three init scales",
            listOf(
                log10Curve(small.perLayer, "scale 0.25", NeutralColor),
                log10Curve(sigmoidRun.perLayer, "scale 1.0", BackwardColor),
                log10Curve(large.perLayer, "scale 1.5", AccentB),
            ),
            1f..12f,
            -8f..2f,
        ),
        readout = "ratios ${"%.0e".format(small.ratio)} · ${"%.0e".format(sigmoidRun.ratio)} · ${"%.0e".format(large.ratio)}",
    )

    frames += NetFrame(
        status = "What actually fixed it, in the order the field found them. ReLU, whose derivative is exactly 1 " +
            "wherever the unit is active, so there is no shrinking factor to compound. He and Xavier initialisation, " +
            "which set the weight scale so the variance is preserved layer to layer. Batch and layer normalisation, " +
            "which re-centre the pre-activations so units stay off the saturated tails. And residual connections, " +
            "which add an identity path so the gradient has a route to the early layers that multiplies by nothing " +
            "at all — that last one is why 152 layers became possible in 2015 when 20 had been hard in 2012.",
        plot = CurvePlot(
            "log₁₀ ‖∂L/∂W‖ by layer",
            listOf(
                log10Curve(sigmoidRun.perLayer, "sigmoid", BackwardColor),
                log10Curve(reluRun.perLayer, "ReLU + He init", AccentA),
            ),
            1f..12f,
            -4f..2f,
        ),
        readout = "the fixes are architectural, not numerical",
    )
    return frames
}

private fun explodingGradientFrames(): List<NetFrame> {
    val frames = mutableListOf<NetFrame>()
    val stable = deepGradients(activation = DeepActivation.Relu, initScale = sqrt(2.0 / 16.0))
    val blown = deepGradients(activation = DeepActivation.Relu, initScale = 1.5)

    frames += NetFrame(
        status = "The same multiplication, running the other way. If the factors between layers average above one " +
            "rather than below it, the product grows with depth instead of shrinking — and unlike vanishing, this " +
            "one is visible in the forward pass. Two 12-layer ReLU stacks, identical except for the weight " +
            "initialisation scale: He at ${"%.2f".format(sqrt(2.0 / 16.0))}, and a careless 1.5.",
        plot = CurvePlot(
            "log₁₀ mean |activation| by layer",
            listOf(
                log10Curve(stable.activation, "He init", AccentA),
                log10Curve(blown.activation, "scale 1.5", BackwardColor),
            ),
            1f..12f,
            -1f..8f,
        ),
        readout = "activations: ${"%.1f".format(blown.activation.first())} → ${"%.1e".format(blown.activation.last())}",
    )

    frames += NetFrame(
        status = "Twelve layers took the mean activation from ${"%.1f".format(blown.activation.first())} to " +
            "${"%.1e".format(blown.activation.last())} — a factor of about " +
            "${"%.0e".format(blown.activation.last() / blown.activation.first())}, compounding at roughly " +
            "${"%.1f".format(Math.pow(blown.activation.last() / blown.activation.first(), 1.0 / 11.0))}× per layer. " +
            "Nothing here is unstable in the numerical-error sense; it is a geometric series doing what geometric " +
            "series do, and thirty layers instead of twelve would overflow a float outright.",
        bars = listOf(
            NetBar(
                "log₁₀ mean |activation| per layer",
                blown.activation.map { log10(max(it, 1e-300)).toFloat() },
                BackwardColor,
                (1..12).map { if (it % 3 == 0) "$it" else "" },
            ),
        ),
        readout = "≈ ${"%.1f".format(Math.pow(blown.activation.last() / blown.activation.first(), 1.0 / 11.0))}× per layer",
    )

    val clip = clipGlobalNorm(blown.perLayer, 5.0)
    frames += NetFrame(
        status = "The gradients follow. Total gradient norm across all twelve layers: " +
            "${"%.1e".format(clip.beforeNorm)}. This is what a NaN loss looks like one step before it happens — the " +
            "update is finite and enormous, the weights land somewhere absurd, the next forward pass overflows, and " +
            "every number in the model becomes NaN at once. The symptom people report is \"the loss went to NaN at " +
            "step 400\"; the cause was here.",
        plot = CurvePlot(
            "log₁₀ ‖∂L/∂W‖ by layer",
            listOf(
                log10Curve(stable.perLayer, "He init", AccentA),
                log10Curve(blown.perLayer, "scale 1.5", BackwardColor),
            ),
            1f..12f,
            -2f..18f,
        ),
        readout = "‖g‖ = ${"%.1e".format(clip.beforeNorm)}",
    )

    frames += NetFrame(
        status = "Gradient clipping, and it is worth being precise about why it works. Compute the global norm over " +
            "every parameter; if it exceeds a threshold, multiply *everything* by threshold/norm. Here that is " +
            "${"%.1e".format(clip.beforeNorm)} down to ${"%.1f".format(clip.afterNorm)}, a scale factor of " +
            "${"%.1e".format(clip.scale)}. Because the same factor is applied everywhere, the direction of the step " +
            "is untouched — only its length is capped. Clipping each parameter separately would not have that " +
            "property, and it is the reason `clip_grad_norm_` is the one people use.",
        bars = listOf(
            NetBar(
                "log₁₀ ‖∂L/∂W‖ after clipping",
                clip.clippedPerLayer.map { log10(max(it, 1e-300)).toFloat() },
                AccentA,
                (1..12).map { if (it % 3 == 0) "$it" else "" },
            ),
        ),
        readout = "‖g‖ ${"%.1e".format(clip.beforeNorm)} → ${"%.1f".format(clip.afterNorm)}, direction unchanged",
    )

    frames += NetFrame(
        status = "Vanishing and exploding are the same phenomenon with the ratio on either side of one, but they " +
            "are not equally bad in practice. Exploding announces itself — a spiking loss, then NaN — and clipping " +
            "plus a smaller initialisation usually fixes it in an afternoon. Vanishing is silent: the loss goes " +
            "down, the model trains, and the early layers simply never learn anything, which looks like a model " +
            "that is not big enough rather than one that is broken. The loud failure is the easier one to have.",
        plot = CurvePlot(
            "log₁₀ ‖∂L/∂W‖ by layer",
            listOf(
                log10Curve(deepGradients(activation = DeepActivation.Sigmoid, initScale = 1.0).perLayer, "vanishing", NeutralColor),
                log10Curve(stable.perLayer, "healthy", AccentA),
                log10Curve(blown.perLayer, "exploding", BackwardColor),
            ),
            1f..12f,
            -4f..18f,
        ),
        readout = "same mechanism, opposite sign",
    )
    return frames
}

// ── Activation functions (phase 9, batch C2) ─────────────────────────────────
// Ten labs sharing one shape: draw the function and its derivative, then measure the property that
// makes this activation worth having and the one that makes it fail. Everything comes from
// ActivationMath.kt, where each activation is defined once with its derivative and every claim is
// run through a real stack rather than quoted from a table.

private fun curveOf(label: String, color: Color, from: Float = -6f, to: Float = 6f, f: (Double) -> Double) =
    Curve(label, sample(from..to, 120) { f(it.toDouble()).toFloat() }, color)

/**
 * Builds a plot whose y range is derived from the curves rather than hand-picked. Hand-picking is
 * what C1 found four bugs in: PlotCanvas clamps rather than skips, so a curve that leaves its
 * declared box is drawn flat along the edge and reads as a real feature of the function.
 */
private fun autoPlot(
    label: String,
    curves: List<Curve>,
    xRange: ClosedFloatingPointRange<Float> = -6f..6f,
): CurvePlot {
    val ys = curves.flatMap { c -> c.points.filter { it.first in xRange }.map { it.second } }
    val lo = ys.min()
    val hi = ys.max()
    val pad = ((hi - lo) * 0.08f).coerceAtLeast(0.05f)
    return CurvePlot(label, curves, xRange, (lo - pad)..(hi + pad))
}

private fun shapePlot(activation: Activation) = autoPlot(
    "${activation.name} and its derivative",
    listOf(
        curveOf(activation.name, ForwardColor) { activation.f(it) },
        curveOf("derivative", BackwardColor) { activation.df(it) },
    ),
)

private fun depthStatsFrame(activation: Activation, status: String): NetFrame {
    val p = propagate(activation)
    return NetFrame(
        status = status,
        plot = CurvePlot(
            "mean and standard deviation of activations, by layer",
            listOf(
                Curve("mean", p.perLayer.map { it.layer.toFloat() to it.mean.toFloat() }, AccentB),
                Curve("std", p.perLayer.map { it.layer.toFloat() to it.std.toFloat() }, ForwardColor),
            ),
            1f..20f,
            -0.6f..2.2f,
        ),
        readout = "layer 20: mean ${"%.3f".format(p.perLayer.last().mean)}, std ${"%.3f".format(p.perLayer.last().std)}",
    )
}

private val saturationWidths = listOf(1.0, 2.0, 4.0, 8.0, 16.0)

private fun saturationFrame(activation: Activation, status: String): NetFrame {
    val values = saturationWidths.map { saturationAtScale(activation, it) }
    return NetFrame(
        status = status,
        bars = listOf(
            NetBar(
                "fraction with derivative below 0.01",
                values.map { it.toFloat() },
                BackwardColor,
                saturationWidths.map { "σ=${it.toInt()}" },
            ),
        ),
        readout = saturationWidths.indices.joinToString(" · ") {
            "σ${saturationWidths[it].toInt()}→${"%.0f".format(values[it] * 100)}%"
        },
    )
}

private fun sigmoidLabFrames(): List<NetFrame> {
    val a = sigmoidActivation
    val frames = mutableListOf<NetFrame>()
    val peak = (-800..800).maxOf { a.df(it / 100.0) }

    frames += NetFrame(
        status = "σ(z) = 1/(1+e⁻ᶻ) squashes any real number into (0, 1), which is why it was the default for " +
            "thirty years and why it is still exactly right as an output for a binary probability. The problem is " +
            "the other curve: σ′ peaks at ${"%.2f".format(peak)} and is small everywhere else.",
        plot = shapePlot(a),
        readout = "max σ′ = ${"%.2f".format(peak)}, at z = 0",
    )

    val mean = meanOutput(a)
    frames += NetFrame(
        status = "It is also not zero-centred. Over standard normal input the mean output is " +
            "${"%.3f".format(mean)} — every activation a downstream unit sees is positive. That makes every weight " +
            "in that unit's gradient share a sign, so the update can only move all-positive or all-negative, and the " +
            "path to the minimum becomes a zig-zag. Tanh exists mostly to fix this one thing.",
        plot = autoPlot(
            "sigmoid against tanh",
            listOf(
                curveOf("sigmoid", ForwardColor) { a.f(it) },
                curveOf("tanh", AccentA) { tanhActivation.f(it) },
            ),
        ),
        readout = "mean output ${"%.3f".format(mean)} — never negative",
    )

    frames += saturationFrame(
        a,
        "Saturation is not a fixed property of the function, it is what happens once the pre-activations get wide. " +
            "At a pre-activation standard deviation of 1 essentially nothing is saturated; at 4 a quarter of units " +
            "are on a flat tail; at 16 more than three quarters. This is why the same activation can look fine in a " +
            "shallow, well-scaled network and stall a deep or badly-initialised one.",
    )

    val deep = propagate(a)
    frames += NetFrame(
        status = "Twenty layers of it, at the LeCun initialisation it is designed for. The gradient at layer 1 is " +
            "${"%.0e".format(deep.gradientRatio)} times weaker than at layer 20 — the σ′ ≤ ${"%.2f".format(peak)} " +
            "ceiling compounding once per layer, exactly as the vanishing-gradient topic describes. No init scale " +
            "escapes it, because the ceiling does not move.",
        plot = CurvePlot(
            "log₁₀ ‖∂L/∂W‖ by layer",
            listOf(log10Curve(deep.gradientPerLayer, "sigmoid", BackwardColor)),
            1f..20f, -14f..2f,
        ),
        readout = "layer 1 is ${"%.0e".format(deep.gradientRatio)}× weaker",
    )

    frames += NetFrame(
        status = "Where it still belongs, which is a shorter list than it used to be and not empty. As the output " +
            "of a binary classifier, where you want a probability and the saturation is the calibration. As a gate " +
            "inside an LSTM or a GRU, where a value in (0, 1) is exactly the \"how much to let through\" semantics " +
            "the architecture needs. As the σ in Swish. What it is no longer is a hidden-layer default.",
        plot = shapePlot(a),
        readout = "output layers and gates — not hidden layers",
    )
    return frames
}

private fun tanhLabFrames(): List<NetFrame> {
    val a = tanhActivation
    val frames = mutableListOf<NetFrame>()

    frames += NetFrame(
        status = "tanh is a rescaled sigmoid — tanh(z) = 2σ(2z) − 1 — and the rescaling buys two things. Its range " +
            "is (−1, 1) rather than (0, 1), and its derivative peaks at 1 rather than at 0.25.",
        plot = shapePlot(a),
        readout = "max tanh′ = 1.00, four times sigmoid's",
    )

    val meanTanh = meanOutput(a)
    val meanSigmoid = meanOutput(sigmoidActivation)
    frames += NetFrame(
        status = "Zero-centred, and measurably so: mean output ${"%.3f".format(meanTanh)} over standard normal " +
            "input against sigmoid's ${"%.3f".format(meanSigmoid)}. Downstream gradients no longer all share a " +
            "sign, so the zig-zag sigmoid causes goes away. This is the whole reason tanh replaced sigmoid as the " +
            "hidden-layer default in the 1990s.",
        plot = autoPlot(
            "tanh against sigmoid",
            listOf(
                curveOf("tanh", ForwardColor) { a.f(it) },
                curveOf("sigmoid", NeutralColor) { sigmoidActivation.f(it) },
            ),
        ),
        readout = "mean ${"%.3f".format(meanTanh)} vs sigmoid's ${"%.3f".format(meanSigmoid)}",
    )

    frames += saturationFrame(
        a,
        "What it does not fix is saturation, and it is in fact worse on that axis than sigmoid: at a pre-activation " +
            "width of 4, tanh has a larger fraction of units on a flat tail, because its curve turns over sooner. A " +
            "derivative that peaks at 1 still spends most of its range well below 1, so a deep stack still multiplies " +
            "by less than one per layer.",
    )

    frames += depthStatsFrame(
        a,
        "Twenty layers, LeCun init. The signal is centred the whole way down — mean stays near zero, which is the " +
            "win — but the standard deviation shrinks steadily, so the network is quietly losing dynamic range with " +
            "depth. Tanh was a real improvement on sigmoid and still not enough to make very deep stacks trainable; " +
            "that took ReLU.",
    )

    frames += NetFrame(
        status = "It did not disappear, though. Every LSTM and GRU still uses tanh for the candidate and output " +
            "transforms, because those want a bounded, zero-centred value and the bounding is load-bearing — an " +
            "unbounded cell update would drift. Bounded output is a feature wherever a value is going to be carried " +
            "across many steps rather than passed straight to the next layer.",
        plot = shapePlot(a),
        readout = "still the default inside gated recurrent cells",
    )
    return frames
}

private fun reluLabFrames(): List<NetFrame> {
    val a = reluActivation
    val frames = mutableListOf<NetFrame>()

    frames += NetFrame(
        status = "max(0, z). No exponentials, no saturation on the positive side, and a derivative that is exactly " +
            "1 wherever the unit is active — so a deep stack multiplies the backward signal by exactly one per " +
            "layer instead of by a quarter. That single property is most of why depth became practical in 2012.",
        plot = shapePlot(a),
        readout = "derivative is exactly 1 or exactly 0",
    )

    val reluStuck = saturationWidths.map { saturationAtScale(a, it) }
    val sigmoidStuck = saturationWidths.map { saturationAtScale(sigmoidActivation, it) }
    frames += NetFrame(
        status = "Half of ReLU's domain has zero derivative, which sounds like sigmoid's problem and is not the " +
            "same thing. The fraction of inputs with a dead derivative is ${"%.0f".format(reluStuck[0] * 100)}% and " +
            "stays there no matter how wide the pre-activations get, because the boundary is at zero and does not " +
            "move. Sigmoid's rises from ${"%.0f".format(sigmoidStuck[0] * 100)}% to " +
            "${"%.0f".format(sigmoidStuck.last() * 100)}% over the same sweep. One is a fixed property of the shape; " +
            "the other is a failure that gets worse as training proceeds.",
        bars = listOf(
            NetBar("ReLU: zero-derivative fraction", reluStuck.map { it.toFloat() }, AccentA, saturationWidths.map { "σ=${it.toInt()}" }),
            NetBar("Sigmoid: same measurement", sigmoidStuck.map { it.toFloat() }, BackwardColor, saturationWidths.map { "σ=${it.toInt()}" }),
        ),
        readout = "constant 50% against a climb to ${"%.0f".format(sigmoidStuck.last() * 100)}%",
    )

    val rates = listOf(1.0, 30.0, 60.0, 100.0)
    val dead = rates.map { dyingRelu(learningRate = it) }
    frames += NetFrame(
        status = "The real failure has a different name. Train one layer with plain SGD and count the units that " +
            "end up inactive for *every* input in the batch: at a learning rate of ${rates[0].toInt()}, none. At " +
            "${rates[1].toInt()}, ${"%.0f".format(dead[1].deadFraction * 100)}%. At ${rates[2].toInt()}, " +
            "${"%.0f".format(dead[2].deadFraction * 100)}%. At ${rates[3].toInt()}, " +
            "${"%.0f".format(dead[3].deadFraction * 100)}% — the entire layer. A large step drives the bias far " +
            "enough negative that the unit stops firing at all.",
        bars = listOf(
            NetBar(
                "dead unit fraction after 120 steps",
                dead.map { it.deadFraction.toFloat() },
                BackwardColor,
                rates.map { "lr ${it.toInt()}" },
            ),
        ),
        readout = "dying ReLU is caused by the step size, not the init",
    )

    val worst = dead.last()
    frames += NetFrame(
        status = "And it is permanent, which is what makes it worse than it first sounds. A unit that is off for " +
            "every input has gradient exactly zero for every example, so no future update can move it — there is " +
            "nothing to descend. Watch when it happens: the deaths are all inside the first ten steps and the " +
            "fraction is flat for the remaining hundred and ten. Leaky ReLU on the identical run kills " +
            "${"%.0f".format(dyingRelu(leakyReluActivation(), learningRate = rates[3]).deadFraction * 100)}%.",
        plot = CurvePlot(
            "dead fraction over training, lr = ${rates[3].toInt()}",
            listOf(
                Curve("ReLU", worst.deadOverTime.mapIndexed { i, v -> (i * 10).toFloat() to v.toFloat() }, BackwardColor),
                Curve(
                    "Leaky ReLU",
                    dyingRelu(leakyReluActivation(), learningRate = rates[3]).deadOverTime
                        .mapIndexed { i, v -> (i * 10).toFloat() to v.toFloat() },
                    AccentA,
                ),
            ),
            0f..110f, -0.05f..1.1f,
        ),
        readout = "dead by step 10, and dead for good",
    )
    return frames
}

private fun leakyReluLabFrames(): List<NetFrame> {
    val slope = 0.01
    val a = leakyReluActivation(slope)
    val frames = mutableListOf<NetFrame>()

    frames += NetFrame(
        status = "One character of difference: max(αz, z) with α = $slope instead of max(0, z). The negative side " +
            "is no longer flat, so its derivative is $slope rather than 0 — small, but not zero, and that is the " +
            "entire point. A unit that has drifted negative still receives a gradient and can still come back.",
        plot = shapePlot(a),
        readout = "negative-side derivative $slope, not 0",
    )

    val rates = listOf(1.0, 30.0, 60.0, 100.0)
    val relu = rates.map { dyingRelu(reluActivation, learningRate = it).deadFraction }
    val leaky = rates.map { dyingRelu(a, learningRate = it).deadFraction }
    frames += NetFrame(
        status = "The same learning-rate sweep the ReLU lab runs. ReLU loses " +
            "${"%.0f".format(relu[1] * 100)}%, ${"%.0f".format(relu[2] * 100)}% and " +
            "${"%.0f".format(relu[3] * 100)}% of its units as the rate climbs. Leaky ReLU loses " +
            "${leaky.joinToString(", ") { "%.0f".format(it * 100) + "%" }} — nothing, at any rate tested. The unit " +
            "cannot become permanently unreachable because the derivative is never exactly zero.",
        bars = listOf(
            NetBar("ReLU dead fraction", relu.map { it.toFloat() }, BackwardColor, rates.map { "lr ${it.toInt()}" }),
            NetBar("Leaky ReLU dead fraction", leaky.map { it.toFloat() }, AccentA, rates.map { "lr ${it.toInt()}" }),
        ),
        readout = "the dying problem is gone outright",
    )

    frames += NetFrame(
        status = "What it costs. The output is no longer exactly zero for negative input, so the representation " +
            "stops being sparse — ReLU's exact zeros are genuinely useful, both as a form of regularisation and for " +
            "the sparse kernels some hardware exploits. And α is a hyperparameter nobody tunes: 0.01 is a number " +
            "from the paper, and there is no principled way to choose it, which is exactly the gap PReLU fills by " +
            "learning it.",
        plot = autoPlot(
            "Leaky ReLU against ReLU, negative side",
            listOf(
                curveOf("Leaky ReLU", ForwardColor, -6f, 2f) { a.f(it) },
                curveOf("ReLU", NeutralColor, -6f, 2f) { reluActivation.f(it) },
            ),
            -6f..2f,
        ),
        readout = "no exact zeros · α is a guess",
    )

    frames += NetFrame(
        status = "The honest summary is that it is a cheap insurance policy rather than an upgrade. Published " +
            "comparisons find the accuracy difference against ReLU small and inconsistent across tasks — the case " +
            "for it is that it removes a specific catastrophic failure at essentially no cost, not that it learns " +
            "better. Reach for it when you have seen dead units, or when you cannot afford to check.",
        plot = shapePlot(a),
        readout = "insurance, not an upgrade",
    )
    return frames
}

private fun preluLabFrames(): List<NetFrame> {
    val frames = mutableListOf<NetFrame>()
    val run = trainPrelu()

    frames += NetFrame(
        status = "PReLU is Leaky ReLU with α promoted from a hyperparameter to a parameter: it is learned by " +
            "gradient descent along with every weight, typically one α per channel. The gradient is available for " +
            "free — ∂out/∂α is just z on the negative side and 0 on the positive side — so the whole change costs " +
            "one number per channel.",
        plot = autoPlot(
            "three values of α",
            listOf(
                curveOf("α = 0 (ReLU)", NeutralColor) { reluActivation.f(it) },
                curveOf("α = 0.01", AccentB) { leakyReluActivation(0.01).f(it) },
                curveOf("α = 0.25 (learned here)", ForwardColor) { leakyReluActivation(0.25).f(it) },
            ),
        ),
        readout = "α is a parameter, not a setting",
    )

    frames += NetFrame(
        status = "Run it against data generated with a true negative slope of 0.25, starting from α = 0 — that is, " +
            "starting as plain ReLU. Gradient descent recovers " +
            "${"%.4f".format(run.alphaHistory.last())} against a true ${"%.2f".format(0.25)}, and the loss goes to " +
            "${"%.4f".format(run.lossHistory.last())}. Nothing about the slope was specified in advance.",
        plot = CurvePlot(
            "α over training",
            listOf(
                Curve("α", run.alphaHistory.mapIndexed { i, v -> i.toFloat() to v.toFloat() }, ForwardColor),
                Curve("true α", listOf(0f to 0.25f, run.alphaHistory.size.toFloat() to 0.25f), NeutralColor),
            ),
            0f..run.alphaHistory.size.toFloat(), -0.05f..0.35f,
        ),
        readout = "α ${"%.2f".format(run.alphaHistory.first())} → ${"%.4f".format(run.alphaHistory.last())}",
    )

    frames += NetFrame(
        status = "The result that made it famous: PReLU was the activation in the 2015 network that first reported " +
            "super-human top-5 accuracy on ImageNet, in the same paper that introduced He initialisation. The two " +
            "belong together — the init derivation accounts for the negative slope, so using PReLU with a variance " +
            "calculation that assumes ReLU leaves the scaling slightly wrong.",
        plot = CurvePlot(
            "loss over training",
            listOf(Curve("MSE", run.lossHistory.mapIndexed { i, v -> i.toFloat() to v.toFloat() }, BackwardColor)),
            0f..run.lossHistory.size.toFloat(), 0f..(run.lossHistory.max().toFloat() * 1.1f),
        ),
        readout = "He init and PReLU are from the same paper",
    )

    frames += NetFrame(
        status = "And the caveat, which is the usual one for extra parameters: on a small dataset a learned α is " +
            "one more thing that can overfit, and the reported gains over Leaky ReLU are modest. It is also worth " +
            "knowing that α is unconstrained — nothing stops it learning a negative value, which would make the " +
            "activation non-monotone, and nothing in the formulation says that is wrong.",
        plot = autoPlot(
            "three values of α",
            listOf(
                curveOf("α = 0", NeutralColor) { reluActivation.f(it) },
                curveOf("α = 0.25", ForwardColor) { leakyReluActivation(0.25).f(it) },
                curveOf("α = −0.2 (nothing forbids it)", BackwardColor) { leakyReluActivation(-0.2).f(it) },
            ),
        ),
        readout = "one parameter per channel, unconstrained",
    )
    return frames
}

private fun eluLabFrames(): List<NetFrame> {
    val a = eluActivation()
    val frames = mutableListOf<NetFrame>()

    frames += NetFrame(
        status = "ELU keeps the identity on the positive side and replaces the negative side with α(eᶻ − 1), which " +
            "is smooth at every point and saturates gently to −α rather than falling away without limit. The " +
            "saturation is deliberate: a bounded negative response makes the unit robust to a large negative input " +
            "rather than propagating it.",
        plot = shapePlot(a),
        readout = "smooth everywhere · bounded below at −α",
    )

    val meanElu = meanOutput(a)
    val meanRelu = meanOutput(reluActivation)
    frames += NetFrame(
        status = "The argument for it is the mean. Over standard normal input ELU averages " +
            "${"%.3f".format(meanElu)} against ReLU's ${"%.3f".format(meanRelu)} — much closer to zero, because the " +
            "negative side contributes something instead of nothing. Activations centred near zero are the same " +
            "property batch normalisation is added to enforce, and ELU gets part of the way there for free.",
        plot = autoPlot(
            "ELU against ReLU",
            listOf(
                curveOf("ELU", ForwardColor) { a.f(it) },
                curveOf("ReLU", NeutralColor) { reluActivation.f(it) },
            ),
        ),
        readout = "mean ${"%.3f".format(meanElu)} vs ReLU's ${"%.3f".format(meanRelu)}",
    )

    frames += saturationFrame(
        a,
        "Its negative saturation does cost something, and the measurement says how much: the fraction of units with " +
            "a near-zero derivative climbs with the pre-activation width the way sigmoid's does, though from a lower " +
            "base and more slowly. ELU is not saturation-free — it trades ReLU's hard zero for a soft floor, and a " +
            "soft floor is still a floor.",
    )

    frames += NetFrame(
        status = "In practice the deciding factor is usually cost rather than accuracy. ELU needs an exponential on " +
            "the negative half, and max(0, z) is a comparison — on a large model that difference is real, and the " +
            "accuracy gain over ReLU with batch normalisation is small. ELU is a good default when you are not " +
            "using normalisation layers, which is a narrower situation now than it was in 2015.",
        plot = shapePlot(a),
        readout = "an exp() per negative unit",
    )
    return frames
}

private fun seluLabFrames(): List<NetFrame> {
    val a = seluActivation
    val frames = mutableListOf<NetFrame>()

    frames += NetFrame(
        status = "SELU is ELU multiplied by λ, with α and λ fixed at " +
            "${"%.4f".format(SeluAlpha)} and ${"%.4f".format(SeluLambda)}. Those are not tuned values — they are " +
            "the solution to a fixed-point equation, chosen so that a layer maps activations with mean 0 and " +
            "variance 1 to activations with mean 0 and variance 1. The claim is that a deep stack normalises itself " +
            "with no normalisation layer at all.",
        plot = shapePlot(a),
        readout = "λ = ${"%.4f".format(SeluLambda)}, α = ${"%.4f".format(SeluAlpha)}",
    )

    val selu = propagate(a)
    frames += depthStatsFrame(
        a,
        "Twenty layers, LeCun normal initialisation, and the claim measured rather than repeated: the mean stays " +
            "within a few hundredths of zero and the standard deviation within a few hundredths of one, the whole " +
            "way down. Layer 20 comes out at mean ${"%.3f".format(selu.perLayer.last().mean)} and std " +
            "${"%.3f".format(selu.perLayer.last().std)}. Nothing is normalising anything; the activation's own " +
            "shape is doing it.",
    )

    val relu = propagate(reluActivation)
    val tanhRun = propagate(tanhActivation)
    frames += NetFrame(
        status = "The same twenty layers for ReLU and tanh, each at the initialisation it is designed for. Both " +
            "lose their signal with depth — ReLU's standard deviation falls to " +
            "${"%.3f".format(relu.perLayer.last().std)} and tanh's to " +
            "${"%.3f".format(tanhRun.perLayer.last().std)}, against SELU's " +
            "${"%.3f".format(selu.perLayer.last().std)}. This is the comparison the paper is about, and it holds.",
        plot = CurvePlot(
            "activation std by layer",
            listOf(
                Curve("SELU", selu.perLayer.map { it.layer.toFloat() to it.std.toFloat() }, AccentA),
                Curve("ReLU", relu.perLayer.map { it.layer.toFloat() to it.std.toFloat() }, NeutralColor),
                Curve("tanh", tanhRun.perLayer.map { it.layer.toFloat() to it.std.toFloat() }, BackwardColor),
            ),
            1f..20f, 0f..1.3f,
        ),
        readout = "std at layer 20: ${"%.3f".format(selu.perLayer.last().std)} · ${"%.3f".format(relu.perLayer.last().std)} · ${"%.3f".format(tanhRun.perLayer.last().std)}",
    )

    val wrongInit = propagate(a, initScale = sqrt(2.0 / 32.0))
    frames += NetFrame(
        status = "And the condition that is easy to miss: the fixed point is derived assuming LeCun normal " +
            "initialisation, variance 1/n. Run the identical SELU stack under He initialisation instead and layer " +
            "20 comes out at mean ${"%.2f".format(wrongInit.perLayer.last().mean)} and std " +
            "${"%.2f".format(wrongInit.perLayer.last().std)} — the self-normalisation is simply gone. The " +
            "initialisation is part of the method, not a detail beside it, and the same goes for the dropout " +
            "variant it requires (alpha-dropout, which preserves mean and variance where ordinary dropout does not).",
        plot = CurvePlot(
            "SELU activation std, two initialisations",
            listOf(
                Curve("LeCun (correct)", selu.perLayer.map { it.layer.toFloat() to it.std.toFloat() }, AccentA),
                Curve("He (wrong)", wrongInit.perLayer.map { it.layer.toFloat() to it.std.toFloat() }, BackwardColor),
            ),
            1f..20f, 0f..6f,
        ),
        readout = "under He init: mean ${"%.2f".format(wrongInit.perLayer.last().mean)}, std ${"%.2f".format(wrongInit.perLayer.last().std)}",
    )

    frames += NetFrame(
        status = "So why is it not everywhere? The property is real and the practice went elsewhere. It holds for " +
            "plain feedforward stacks and not for convolutions, residual connections or attention, where the " +
            "architecture moves the statistics itself; it needs its own initialisation and its own dropout; and " +
            "batch and layer normalisation give the same stability with none of those conditions. SELU is worth " +
            "knowing as a result about what an activation *can* do, and it is rarely the right default.",
        plot = shapePlot(a),
        readout = "a real property, superseded by normalisation layers",
    )
    return frames
}

private fun swishLabFrames(): List<NetFrame> {
    val a = swishActivation()
    val frames = mutableListOf<NetFrame>()
    val minimum = minimumOf({ a.f(it) })
    val peak = (-800..800).maxOf { a.df(it / 100.0) }

    frames += NetFrame(
        status = "Swish is z·σ(βz): the input multiplied by a gate computed from the input itself. It is smooth " +
            "everywhere, unbounded above and bounded below, and — unlike everything before it here — it is not " +
            "monotone. It dips to ${"%.4f".format(minimum.second)} at z = ${"%.3f".format(minimum.first)} before " +
            "coming back up.",
        plot = shapePlot(a),
        readout = "minimum ${"%.4f".format(minimum.second)} at z = ${"%.3f".format(minimum.first)}",
    )

    frames += NetFrame(
        status = "That dip has a consequence worth seeing: the derivative exceeds 1, peaking at " +
            "${"%.4f".format(peak)}. Every activation before this one in the category has a derivative capped at 1 " +
            "or below, so they can only attenuate the backward signal. Swish can amplify it slightly — which is " +
            "part of why it behaves well in very deep stacks, and also why it is not a free win.",
        plot = autoPlot(
            "derivatives compared",
            listOf(
                curveOf("Swish′", ForwardColor) { a.df(it) },
                curveOf("ReLU′", NeutralColor) { reluActivation.df(it) },
            ),
        ),
        readout = "max derivative ${"%.4f".format(peak)} — above 1",
    )

    frames += NetFrame(
        status = "β interpolates between two things you already know. At β → 0 the gate is a constant ½ and Swish " +
            "becomes the linear function z/2; at β → ∞ the gate becomes a step and Swish becomes ReLU exactly. β = " +
            "1 is the usual choice and is what SiLU means; making β learnable is possible and rarely worth it.",
        plot = autoPlot(
            "β = 0.1, 1, 10",
            listOf(
                curveOf("β = 0.1", NeutralColor) { swishActivation(0.1).f(it) },
                curveOf("β = 1", ForwardColor) { swishActivation(1.0).f(it) },
                curveOf("β = 10", AccentB) { swishActivation(10.0).f(it) },
            ),
        ),
        readout = "β → 0 is linear · β → ∞ is ReLU",
    )

    frames += NetFrame(
        status = "It is worth being straight about where it came from. Swish was found by an automated search over " +
            "candidate activation functions, not derived from a property anyone wanted — the explanations for why " +
            "it works were written afterwards. It had also been published twice before under other names (SiL, " +
            "SiLU). The reported gains over ReLU are consistent but small, around a point of ImageNet top-1, and " +
            "it costs a sigmoid per unit. That is the whole case: a modest, reproducible improvement with a real " +
            "compute cost.",
        plot = shapePlot(a),
        readout = "found by search · small consistent gain · costs a sigmoid",
    )
    return frames
}

private fun geluLabFrames(): List<NetFrame> {
    val a = geluActivation
    val frames = mutableListOf<NetFrame>()
    val minimum = minimumOf({ a.f(it) })
    val vsRelu = maxDeviation({ a.f(it) }, { reluActivation.f(it) })
    val vsSwish = maxDeviation({ a.f(it) }, { swishActivation().f(it) })
    val vsApprox = maxDeviation({ a.f(it) }, ::geluTanhApproximation)

    frames += NetFrame(
        status = "GELU is z·Φ(z), the input times the probability that a standard normal draw falls below it. The " +
            "motivation is different from every other activation here: rather than shaping a curve, it asks what " +
            "happens if a unit is kept or dropped at random with probability depending on its own value, and then " +
            "takes the expectation. It is dropout and ReLU merged into one deterministic function.",
        plot = shapePlot(a),
        readout = "z · Φ(z) — a smooth, probabilistic gate",
    )

    frames += NetFrame(
        status = "Shape-wise it is a smoothed ReLU with a dip: minimum ${"%.4f".format(minimum.second)} at z = " +
            "${"%.3f".format(minimum.first)}, and it differs from ReLU by at most " +
            "${"%.4f".format(vsRelu.second)}, at z = ${"%.3f".format(vsRelu.first)}. That is a small difference in " +
            "absolute terms and it is concentrated exactly at the kink, where ReLU is not differentiable — which is " +
            "the part that matters for optimisation.",
        plot = autoPlot(
            "GELU against ReLU",
            listOf(
                curveOf("GELU", ForwardColor) { a.f(it) },
                curveOf("ReLU", NeutralColor) { reluActivation.f(it) },
            ),
        ),
        readout = "max gap from ReLU ${"%.4f".format(vsRelu.second)}, at the kink",
    )

    frames += NetFrame(
        status = "Against Swish, which it closely resembles, the largest gap is ${"%.4f".format(vsSwish.second)} at " +
            "z = ${"%.3f".format(vsSwish.first)} — they are different functions with different derivations that " +
            "landed in almost the same place. Neither has a convincing argument for being better than the other; " +
            "which one a model uses is mostly which paper its architecture descended from.",
        plot = autoPlot(
            "GELU against Swish",
            listOf(
                curveOf("GELU", ForwardColor) { a.f(it) },
                curveOf("Swish (β=1)", AccentB) { swishActivation().f(it) },
            ),
        ),
        readout = "max gap from Swish ${"%.4f".format(vsSwish.second)}",
    )

    frames += NetFrame(
        status = "One practical detail that matters more than it should. Because Φ was expensive, BERT and GPT-2 " +
            "shipped a tanh-based approximation, and it is within ${"%.5f".format(vsApprox.second)} of the exact " +
            "function everywhere. That difference is negligible mathematically and not negligible operationally: " +
            "pretrained weights were fitted with the approximation, so frameworks keep both — `nn.GELU()` and " +
            "`nn.GELU(approximate='tanh')` — and swapping them under a checkpoint shifts its outputs slightly.",
        plot = autoPlot(
            "exact minus tanh approximation, ×10⁴",
            listOf(
                Curve(
                    "difference",
                    sample(-6f..6f, 200) { ((a.f(it.toDouble()) - geluTanhApproximation(it.toDouble())) * 1e4).toFloat() },
                    BackwardColor,
                ),
            ),
        ),
        readout = "max difference ${"%.5f".format(vsApprox.second)}",
    )

    frames += NetFrame(
        status = "GELU is the default in essentially every transformer — BERT, GPT, ViT — and that is the honest " +
            "reason to know it. The empirical case is a small consistent edge over ReLU on those architectures; the " +
            "theoretical case is suggestive rather than decisive. It is the standard because it works slightly " +
            "better and everyone else is using it, which is a legitimate reason and worth naming as such.",
        plot = shapePlot(a),
        readout = "the transformer default",
    )
    return frames
}

private fun softmaxLabFrames(): List<NetFrame> {
    val frames = mutableListOf<NetFrame>()
    val logits = listOf(2.0, 1.0, 0.1, -0.5)
    val labels = listOf("2.0", "1.0", "0.1", "−0.5")

    val base = softmax(logits)
    frames += NetFrame(
        status = "Softmax is the odd one out in this category, and the difference is structural rather than a " +
            "matter of shape: every other activation here maps one number to one number, and softmax maps a whole " +
            "vector to a whole vector. Each output depends on every input, because the denominator is a sum over " +
            "all of them. Logits ${labels.joinToString()} become " +
            "${base.joinToString { "%.3f".format(it) }}, and they sum to exactly 1.",
        bars = listOf(
            NetBar("logits", logits.map { it.toFloat() }, NeutralColor, labels),
            NetBar("probabilities", base.map { it.toFloat() }, ForwardColor, base.map { "%.2f".format(it) }),
        ),
        readout = "sums to ${"%.4f".format(base.sum())}",
    )

    val temperatures = listOf(0.25, 0.5, 1.0, 2.0, 5.0)
    val entropies = temperatures.map { entropyOf(softmax(logits, it)) }
    frames += NetFrame(
        status = "Dividing the logits by a temperature before exponentiating controls how peaked the result is, " +
            "and nothing else about it. At T = ${temperatures.first()} the top class takes " +
            "${"%.1f".format(softmax(logits, temperatures.first())[0] * 100)}% of the mass; at T = " +
            "${temperatures.last().toInt()} it takes only " +
            "${"%.1f".format(softmax(logits, temperatures.last())[0] * 100)}%. Entropy climbs from " +
            "${"%.2f".format(entropies.first())} to ${"%.2f".format(entropies.last())} nats. This is the sampling " +
            "temperature in every text generator, and it is a property of the softmax rather than of the model.",
        plot = CurvePlot(
            "probability of each class against temperature",
            listOf(
                Curve("logit 2.0", temperatures.mapIndexed { i, t -> t.toFloat() to softmax(logits, t)[0].toFloat() }, ForwardColor),
                Curve("logit 1.0", temperatures.mapIndexed { i, t -> t.toFloat() to softmax(logits, t)[1].toFloat() }, AccentB),
                Curve("logit 0.1", temperatures.mapIndexed { i, t -> t.toFloat() to softmax(logits, t)[2].toFloat() }, AccentA),
                Curve("logit −0.5", temperatures.mapIndexed { i, t -> t.toFloat() to softmax(logits, t)[3].toFloat() }, NeutralColor),
            ),
            0.25f..5f, 0f..1.05f,
        ),
        readout = "entropy ${"%.2f".format(entropies.first())} → ${"%.2f".format(entropies.last())} nats",
    )

    val big = listOf(1000.0, 1001.0, 1002.0)
    val naive = softmaxNaive(big)
    val stable = softmax(big)
    frames += NetFrame(
        status = "The implementation detail that is not optional. Computing eᶻ directly on logits of " +
            "${big.joinToString { it.toInt().toString() }} overflows a double and returns " +
            "${naive.joinToString { if (it.isNaN()) "NaN" else "%.3f".format(it) }}. Subtracting the maximum logit " +
            "first changes the result by exactly nothing — the constant cancels between numerator and denominator — " +
            "and gives ${stable.joinToString { "%.3f".format(it) }}. Every library does this internally, which is " +
            "also why you should pass logits to a cross-entropy loss rather than probabilities.",
        bars = listOf(
            NetBar("stable softmax", stable.map { it.toFloat() }, AccentA, stable.map { "%.3f".format(it) }),
        ),
        readout = "naive → NaN · shifted → correct",
    )

    val jacobian = softmaxJacobian(base)
    frames += NetFrame(
        status = "Its derivative is a matrix, not a number: ∂pᵢ/∂zⱼ = pᵢ(δᵢⱼ − pⱼ). Every row sums to zero, which " +
            "says something real — pushing one probability up must pull the others down, because they are " +
            "constrained to sum to 1. Paired with cross-entropy loss the whole matrix collapses to ŷ − y, and that " +
            "cancellation is why the two are always implemented together rather than as separate layers.",
        grids = listOf(
            GridView("Jacobian ∂pᵢ/∂zⱼ", jacobian.map { row -> row.map { it.toFloat() } }),
        ),
        readout = "row sums ${jacobian.joinToString { "%.2f".format(it.sum()) }} — all zero",
    )
    return frames
}

// ── D4 · The feed-forward block ─────────────────────────────────────────────

private fun feedForwardFrames(): List<NetFrame> {
    val frames = mutableListOf<NetFrame>()
    val d = FfnLab.dModel

    frames += NetFrame(
        status = "Every transformer block is attention followed by a position-wise feed-forward network: two " +
            "linear layers with a non-linearity between them, applied to each token independently. Attention " +
            "moves information between positions; the FFN is where each position is transformed on its own. " +
            "It expands ${d} dimensions to ${FfnLab.dFf} and projects back.",
        layers = listOf(
            NetLayer("token (d=$d)", List(4) { NetNode(0.4f + it * 0.1f, NodeMood.FORWARD) }),
            NetLayer("hidden (4d=${FfnLab.dFf})", List(6) { NetNode(if (it % 2 == 0) 0.7f else 0f, if (it % 2 == 0) NodeMood.FORWARD else NodeMood.IDLE) }),
            NetLayer("output (d=$d)", List(4) { NetNode(0.3f + it * 0.1f, NodeMood.OUTPUT) }),
        ),
        readout = "no token talks to another here — that already happened in attention",
    )

    frames += NetFrame(
        status = "The parameter counts are the surprise. Attention's four projections (Q, K, V and output) are " +
            "4d² = ${"%,d".format(FfnLab.attentionParameters())}. The FFN's two matrices are 2 × 4d² = ${"%,d".format(FfnLab.ffnParameters())} — twice as many. " +
            "**Two thirds of a transformer block is the feed-forward network** (${"%.1f".format(FfnLab.ffnShare() * 100)}%), not the attention " +
            "everyone names the architecture after.",
        bars = listOf(
            NetBar(
                "parameters per block (d=$d)",
                listOf(FfnLab.attentionParameters().toFloat(), FfnLab.ffnParameters().toFloat()),
                ForwardColor,
                listOf("attention ${"%,d".format(FfnLab.attentionParameters())}", "FFN ${"%,d".format(FfnLab.ffnParameters())}"),
            ),
        ),
        readout = "FFN share ${"%.1f".format(FfnLab.ffnShare() * 100)}%",
    )

    frames += NetFrame(
        status = "GELU replaced ReLU as the default activation here. It is smooth, and slightly negative for " +
            "small negative inputs instead of exactly zero — so a unit that is nearly off still passes a little " +
            "gradient, and the gate is probabilistic rather than a hard cut at zero.",
        plot = autoPlot(
            "GELU against ReLU",
            listOf(
                curveOf("GELU", ForwardColor, -4f, 4f) { FfnLab.gelu(it) },
                curveOf("ReLU", NeutralColor, -4f, 4f) { FfnLab.relu(it) },
                curveOf("difference", BackwardColor, -4f, 4f) { FfnLab.activationGap(it) },
            ),
            -4f..4f,
        ),
        readout = "the dip below zero near x = −0.5 is the whole difference",
    )

    frames += NetFrame(
        status = "LLaMA and most models after it use SwiGLU instead: a gated unit with *three* matrices rather " +
            "than two. To keep the parameter count unchanged the hidden width drops from 4d to 8/3·d — for " +
            "d = ${FfnLab.llamaDModel} that is ${"%,d".format(FfnLab.swigluHidden(FfnLab.llamaDModel))}, which LLaMA rounds to ${"%,d".format(FfnLab.llamaHidden)} for hardware alignment. If you have ever " +
            "wondered why an FFN width is 11008 rather than 16384, that is the reason.",
        bars = listOf(
            NetBar(
                "FFN parameters at d=${FfnLab.llamaDModel}",
                listOf(
                    FfnLab.ffnParameters(FfnLab.llamaDModel).toFloat(),
                    FfnLab.swigluParameters(FfnLab.llamaDModel, FfnLab.swigluHidden(FfnLab.llamaDModel)).toFloat(),
                ),
                OutputColor,
                listOf("2-matrix 4d", "3-matrix 8/3·d"),
            ),
        ),
        readout = "same budget, different shape",
    )

    frames += NetFrame(
        status = "The interpretation that stuck: an FFN is a key-value memory. Each row of the first matrix is " +
            "a pattern detector over the token's representation, and the corresponding column of the second is " +
            "the value written when it fires. Because activations are sparse — about ${"%.0f".format(FfnLab.activationSparsity() * 100)}% of hidden units are " +
            "near zero for any one token — only a handful of memories are read per token, which is exactly the " +
            "structure mixture-of-experts exploits by splitting the FFN across experts.",
        bars = listOf(
            NetBar(
                "hidden activations for one token",
                List(16) { i -> if (i % 5 == 0) 0.8f - i * 0.02f else 0.02f },
                AccentA,
                List(16) { "" },
            ),
        ),
        readout = "sparse reads — the opening MoE walks through",
    )

    frames += NetFrame(
        status = "And the cost, per token, forward only: ${"%,d".format(FfnLab.ffnFlops())} FLOPs for the FFN against ${"%,d".format(FfnLab.attentionProjectionFlops())} for " +
            "attention's projections. At short sequence lengths the FFN dominates a block's compute as well as " +
            "its parameters; attention's quadratic term only overtakes it once the sequence is long. That is " +
            "why quantisation, pruning and MoE all target the FFN first.",
        bars = listOf(
            NetBar(
                "FLOPs per token",
                listOf(FfnLab.attentionProjectionFlops().toFloat(), FfnLab.ffnFlops().toFloat()),
                BackwardColor,
                listOf("attn projections", "FFN"),
            ),
        ),
        readout = "the block's centre of mass is the FFN",
    )
    return frames
}

// ── D4 · Scaling laws: GPT-3 and GPT-4 ──────────────────────────────────────

private fun scalingFrames(): List<NetFrame> {
    val frames = mutableListOf<NetFrame>()

    frames += NetFrame(
        status = "GPT-3's contribution was not an architectural idea — it is a decoder-only transformer of the " +
            "same shape as GPT-2. It was the demonstration that scale alone produces a qualitatively different " +
            "system: 175B parameters trained on 300B tokens, and a model that performs tasks from a handful of " +
            "examples in the prompt, with no gradient update at all.",
        bars = listOf(
            NetBar(
                "parameters (billions)",
                ScalingLab.models.map { it.parameters.toFloat() },
                ForwardColor,
                ScalingLab.models.map { it.name.split(" ").first() },
            ),
        ),
        readout = "in-context learning arrived as a side effect of size",
    )

    frames += NetFrame(
        status = "The scaling laws are the reason anyone spent that money. Test loss falls as a smooth power " +
            "law in parameters, data and compute over many orders of magnitude — so the return on a bigger run " +
            "is predictable before the run starts. Note what the curve does *not* do: it never flattens into a " +
            "plateau, it just pays less per doubling.",
        plot = autoPlot(
            "loss vs parameters (log scale)",
            listOf(
                curveOf("L(N)", ForwardColor, 0f, 2.5f) { ScalingLab.lossFromParameters(Math.pow(10.0, it)) },
            ),
            0f..2.5f,
        ),
        readout = "x is log₁₀ parameters in billions: 1B → 175B",
    )

    frames += NetFrame(
        status = "Chinchilla then showed GPT-3 had spent the money wrongly. For a fixed compute budget, loss is " +
            "minimised at roughly ${ScalingLab.chinchillaRatio.toInt()} training tokens per parameter — and GPT-3 used ${"%.1f".format(ScalingLab.ratio(ScalingLab.models[0]))}. It was " +
            "not too big; it was undertrained for its size. A 70B model on 1.4T tokens beat it using less " +
            "compute, which reset how every lab afterwards allocated a budget.",
        bars = listOf(
            NetBar(
                "training tokens per parameter",
                ScalingLab.models.map { ScalingLab.ratio(it).toFloat().coerceAtMost(60f) },
                ScalingLab.models.let { OutputColor },
                ScalingLab.models.map { "${it.name.split(" ").first()} ${"%.0f".format(ScalingLab.ratio(it))}" },
            ),
        ),
        readout = "Chinchilla-optimal is ${ScalingLab.chinchillaRatio.toInt()}; bars are capped at 60 to stay readable",
    )

    frames += NetFrame(
        status = "GPT-4's details were never published — no parameter count, no data size, no architecture. " +
            "What its report did contribute was a methodological claim: performance on the final model was " +
            "predicted in advance from runs using 1,000–10,000× less compute. Being able to forecast a frontier " +
            "run's result before committing to it is a real capability, independent of the model.",
        bars = listOf(
            NetBar(
                "compute used to predict the final run (log₁₀ ×)",
                listOf(3f, 4f),
                AccentB,
                listOf("1,000×  less", "10,000× less"),
            ),
        ),
        readout = "predictable scaling is the deliverable, not the parameter count",
    )

    frames += NetFrame(
        status = "Two things followed that the scaling laws do not capture. Instruction tuning and RLHF made " +
            "the models usable — InstructGPT's 1.3B model was preferred to the raw 175B GPT-3 by human raters, " +
            "which is a 100× gap closed by post-training rather than scale. And multimodality, tool use and " +
            "long context became the axes of competition once raw next-token loss stopped being the " +
            "differentiator.",
        bars = listOf(
            NetBar(
                "human preference (schematic)",
                listOf(0.3f, 0.75f),
                AccentA,
                listOf("GPT-3 175B raw", "InstructGPT 1.3B"),
            ),
        ),
        readout = "post-training beat a 100× parameter advantage",
    )
    return frames
}

// ── D4 · LLaMA, Vicuna, and open weights ────────────────────────────────────

private fun llamaFrames(): List<NetFrame> {
    val frames = mutableListOf<NetFrame>()
    val llama1 = ScalingLab.models.first { it.name.startsWith("LLaMA-1") }
    val llama3 = ScalingLab.models.first { it.name.startsWith("LLaMA-3") }
    val mistral = ScalingLab.models.first { it.name.startsWith("Mistral") }

    frames += NetFrame(
        status = "LLaMA's thesis was the inverse of Chinchilla's. Chinchilla asks how to spend a *training* " +
            "budget optimally; LLaMA asks what to do when the model will be served millions of times, so " +
            "inference cost — which depends on parameters alone, not on training tokens — dominates. The " +
            "answer is to train a smaller model far past compute-optimal.",
        bars = listOf(
            NetBar(
                "tokens per parameter",
                listOf(ScalingLab.ratio(llama1).toFloat(), ScalingLab.ratio(llama3).toFloat().coerceAtMost(250f), ScalingLab.ratio(mistral).toFloat().coerceAtMost(250f)),
                ForwardColor,
                listOf("LLaMA-1 ${"%.0f".format(ScalingLab.ratio(llama1))}", "LLaMA-3 ${"%.0f".format(ScalingLab.ratio(llama3))}", "Mistral ${"%.0f".format(ScalingLab.ratio(mistral))}"),
            ),
        ),
        readout = "Chinchilla-optimal is ${ScalingLab.chinchillaRatio.toInt()} — the open models went 10–50× past it",
    )

    frames += NetFrame(
        status = "The consequence is measurable at serving time. Inference costs about 2N FLOPs per token, " +
            "where N is the parameter count — training tokens do not appear in that formula at all. A 7B model " +
            "trained on ${"%.0f".format(mistral.tokens)}B tokens costs ${"%.2e".format(ScalingLab.inferenceFlopsPerToken(mistral))} FLOPs per token to run; a 70B model costs " +
            "${"%.2e".format(ScalingLab.inferenceFlopsPerToken(llama3))}. Ten times the training spend is worth it if you serve enough tokens.",
        bars = listOf(
            NetBar(
                "inference FLOPs per token (×10¹⁰)",
                listOf(
                    (ScalingLab.inferenceFlopsPerToken(mistral) / 1e10).toFloat(),
                    (ScalingLab.inferenceFlopsPerToken(llama3) / 1e10).toFloat(),
                ),
                BackwardColor,
                listOf("7B", "70B"),
            ),
        ),
        readout = "training is paid once; inference is paid per token forever",
    )

    frames += NetFrame(
        status = "The architecture changes are small and have all been adopted elsewhere: pre-normalisation " +
            "with RMSNorm instead of post-LayerNorm, SwiGLU in place of the GELU feed-forward, and rotary " +
            "position embeddings instead of learned ones. None is dramatic; together they are what a 2023 " +
            "transformer looks like, and every open model since copies the set.",
        bars = listOf(
            NetBar(
                "what changed vs the 2017 block",
                listOf(1f, 1f, 1f),
                AccentB,
                listOf("RMSNorm pre-norm", "SwiGLU FFN", "RoPE"),
            ),
        ),
    )

    frames += NetFrame(
        status = "Vicuna is the other half of the story, and it is a story about cost rather than capability: " +
            "fine-tuning LLaMA-13B on ~70K shared ChatGPT conversations, for a few hundred dollars, produced a " +
            "chat model that GPT-4-as-judge rated near the commercial systems of the time. Two caveats got lost " +
            "in the excitement — GPT-4 judging is generous to models that imitate its style, and instruction " +
            "tuning on outputs teaches format far better than it teaches knowledge.",
        bars = listOf(
            NetBar(
                "cost of the fine-tune (log₁₀ USD, schematic)",
                listOf(7f, 2.5f),
                OutputColor,
                listOf("pretrain LLaMA", "Vicuna fine-tune"),
            ),
        ),
        readout = "the base model is the expensive part; alignment is not",
    )

    frames += NetFrame(
        status = "What open weights actually buy: you can run the model where the data is, inspect and " +
            "fine-tune it, and keep using a version after the vendor deprecates it. What they cost: you own " +
            "the serving, the safety layer and the evaluation. \"Open\" is also doing loose work here — LLaMA's " +
            "licence carries use restrictions, and the training data is not released, so this is open *weights* " +
            "rather than open source in the usual sense.",
        bars = listOf(
            NetBar(
                "what you take on",
                listOf(1f, 1f, 1f, 1f),
                NeutralColor,
                listOf("serving", "safety", "evals", "updates"),
            ),
        ),
        readout = "open weights ≠ open source",
    )
    return frames
}

// ── D5 · Chain of thought and self-consistency ──────────────────────────────
// Both calculations are exact. The plurality vote is enumerated over the multinomial rather than
// sampled, which is what makes the case where voting *hurts* checkable rather than anecdotal.

private fun chainOfThoughtFrames(): List<NetFrame> {
    val frames = mutableListOf<NetFrame>()
    val p = CotLab.stepAccuracy
    val q = CotLab.directAccuracy
    val breakEven = CotLab.breakEvenSteps()

    frames += NetFrame(
        status = "Chain of thought trades one hard step for several easy ones. That is a real trade, not a free " +
            "win: if a decomposed step is right ${"%.0f".format(p * 100)}% of the time and the steps have to " +
            "all be right, the chain succeeds with probability pⁿ — which falls off a cliff. Answering " +
            "directly is one step at ${"%.0f".format(q * 100)}%.",
        plot = autoPlot(
            "accuracy vs chain length",
            listOf(
                Curve("chain pⁿ", (1..12).map { it.toFloat() to CotLab.chainAccuracy(it).toFloat() }, ForwardColor),
                Curve("direct answer", (1..12).map { it.toFloat() to q.toFloat() }, AccentB),
            ),
            1f..12f,
        ),
        readout = "p = ${"%.2f".format(p)} per step, direct = ${"%.2f".format(q)}",
    )

    frames += NetFrame(
        status = "The curves cross at $breakEven steps. Below that, decomposing wins — " +
            "${"%.3f".format(CotLab.chainAccuracy(breakEven))} against ${"%.2f".format(q)}. Past it, the chain " +
            "is worse than the guess it replaced, because each additional step is another chance to be wrong " +
            "and nothing checks the earlier ones. \"Let's think step by step\" is not unconditionally good " +
            "advice; it is good advice for problems short enough that pⁿ stays above q.",
        bars = listOf(
            NetBar(
                "chain accuracy by length",
                (1..10).map { CotLab.chainAccuracy(it).toFloat() },
                ForwardColor,
                (1..10).map { "$it" },
            ),
        ),
        readout = "break-even at n = $breakEven",
    )

    frames += NetFrame(
        status = "Self-consistency is the standard repair: sample several independent chains and keep the " +
            "answer most of them reach. The usual justification is that wrong chains go wrong in different " +
            "directions while right ones agree — so the vote concentrates on the truth. That justification is " +
            "a claim about the *errors*, and it is checkable.",
        bars = listOf(
            NetBar(
                "five sampled chains, one answer each",
                listOf(1f, 1f, 0.4f, 1f, 0.4f),
                OutputColor,
                listOf("24", "24", "18", "24", "18"),
            ),
        ),
        readout = "plurality: 24",
    )

    val spread = CotLab.votingCurve(0.40, 4)
    val fixed = CotLab.votingCurve(0.40, 1)
    frames += NetFrame(
        status = "Computed exactly, not sampled. With a per-chain accuracy of 0.40 and wrong answers scattered " +
            "over 4 alternatives, voting over 9 chains lifts accuracy to " +
            "${"%.3f".format(spread.last().second)}. With every wrong chain landing on the *same* wrong " +
            "answer, the identical vote drives accuracy down to ${"%.3f".format(fixed.last().second)} — below " +
            "the single chain it started from. Majority voting amplifies whatever the model does " +
            "consistently, and that includes being consistently wrong.",
        plot = autoPlot(
            "plurality accuracy vs number of samples",
            listOf(
                Curve("4 distinct wrong answers", spread.map { it.first.toFloat() to it.second.toFloat() }, AccentA),
                Curve("1 systematic wrong answer", fixed.map { it.first.toFloat() to it.second.toFloat() }, BackwardColor),
                Curve("single chain", spread.map { it.first.toFloat() to 0.40f }, NeutralColor),
            ),
            1f..9f,
        ),
        readout = "same p, same vote, opposite direction",
    )

    frames += NetFrame(
        status = "The same calculation gives self-consistency's other output: agreement between samples, which " +
            "gets used as a confidence score. It is worth seeing what that number does in the bad case. At " +
            "p = 0.12 with one systematic wrong answer, five sampled chains agree " +
            "${"%.0f".format(CotLab.modalShare(0.12, 5, 1) * 100)}% of the time while the plurality answer is " +
            "right ${"%.1f".format(CotLab.pluralityAccuracy(0.12, 5, 1) * 100)}% of the time. High agreement, " +
            "near-zero accuracy — the signal points the wrong way exactly where you need it.",
        bars = listOf(
            NetBar(
                "reported agreement vs actual accuracy",
                listOf(
                    CotLab.modalShare(0.90, 5, 4).toFloat(),
                    CotLab.pluralityAccuracy(0.90, 5, 4).toFloat(),
                    CotLab.modalShare(0.12, 5, 1).toFloat(),
                    CotLab.pluralityAccuracy(0.12, 5, 1).toFloat(),
                ),
                OutputColor,
                listOf("agree p=.9", "correct", "agree p=.12", "correct"),
            ),
        ),
        readout = "agreement measures consistency, never correctness",
    )

    frames += NetFrame(
        status = "What this leaves. Decomposition helps while the chain is short enough; voting helps when the " +
            "errors are unsystematic; neither introduces a fact the model does not have, and neither can " +
            "detect a step that is wrong in a way the model is confident about. Those are the two jobs that " +
            "go to retrieval and to verification, which is where the rest of this category goes.",
        bars = listOf(
            NetBar(
                "what each mechanism fixes",
                listOf(1f, 1f, 0f, 0f),
                AccentA,
                listOf("long derivations", "random slips", "missing facts", "systematic error"),
            ),
        ),
        readout = "two of four — the other two need evidence, not more sampling",
    )
    return frames
}

// ── D5 · Hallucination mitigation ───────────────────────────────────────────

private fun hallucinationFrames(): List<NetFrame> {
    val frames = mutableListOf<NetFrame>()
    val questions = HallucinationLab.questions
    val scattered = HallucinationLab.scatteredQuestions
    val (threshold, coverage, selective) = HallucinationLab.bestThreshold()

    frames += NetFrame(
        status = "Ten questions, six of which the corpus can support and four it cannot. The model answers all " +
            "ten. Its accuracy on the population is " +
            "${"%.2f".format(HallucinationLab.overallAccuracy())}, and every one of the four unanswerable ones " +
            "gets a fluent, specific, wrong answer — that is what makes this a hallucination rather than an " +
            "error: nothing in the output marks it.",
        bars = listOf(
            NetBar(
                "accuracy per question",
                questions.map { HallucinationLab.accuracy(it).toFloat() },
                ForwardColor,
                questions.indices.map { "${it + 1}" },
            ),
        ),
        readout = "6 supported, 4 not — indistinguishable from the answers alone",
    )

    frames += NetFrame(
        status = "The first mitigation people reach for is the model's own confidence, taken as agreement " +
            "across ${HallucinationLab.samples} sampled chains. Plotted against accuracy it should be a " +
            "diagonal. It is not: the four unsupported questions report confidence between " +
            "${"%.2f".format(questions.filter { !it.supported }.minOf { HallucinationLab.confidence(it) })} and " +
            "${"%.2f".format(HallucinationLab.maxUnsupportedConfidence())} while being right almost never. " +
            "The expected calibration error is " +
            "${"%.3f".format(HallucinationLab.expectedCalibrationError())}.",
        bars = listOf(
            NetBar(
                "reported confidence",
                questions.map { HallucinationLab.confidence(it).toFloat() },
                OutputColor,
                questions.indices.map { "${it + 1}" },
            ),
            NetBar(
                "actual accuracy",
                questions.map { HallucinationLab.accuracy(it).toFloat() },
                ForwardColor,
                questions.indices.map { if (it < 6) "sup" else "—" },
            ),
        ),
        readout = "ECE ${"%.3f".format(HallucinationLab.expectedCalibrationError())}; confidence does not separate the two groups",
    )

    frames += NetFrame(
        status = "So thresholding on it barely works. Sweeping the abstention threshold, the best point that " +
            "still answers at least half the questions is τ = ${"%.1f".format(threshold)}: coverage " +
            "${"%.0f".format(coverage * 100)}%, and accuracy on what it did answer only " +
            "${"%.3f".format(selective)}. Refusing 40% of the questions bought " +
            "${"%.3f".format(selective - HallucinationLab.overallAccuracy())} of accuracy.",
        plot = autoPlot(
            "selective accuracy vs coverage",
            listOf(
                Curve(
                    "confidence threshold",
                    HallucinationLab.abstentionSweep().map { it.second.toFloat() to it.third.toFloat() }.sortedBy { it.first },
                    BackwardColor,
                ),
                Curve(
                    "grounding",
                    listOf(
                        HallucinationLab.groundedCoverage().toFloat() to HallucinationLab.groundedSelectiveAccuracy().toFloat(),
                        1f to HallucinationLab.overallAccuracy().toFloat(),
                    ),
                    AccentA,
                ),
            ),
            0f..1f,
        ),
        readout = "τ = ${"%.1f".format(threshold)} → coverage ${"%.2f".format(coverage)}, accuracy ${"%.3f".format(selective)}",
    )

    frames += NetFrame(
        status = "Grounding answers the same population differently: refuse unless a retrieved passage supports " +
            "the claim. At the same ${"%.0f".format(HallucinationLab.groundedCoverage() * 100)}% coverage it " +
            "scores ${"%.3f".format(HallucinationLab.groundedSelectiveAccuracy())} against the confidence " +
            "threshold's ${"%.3f".format(selective)}. It is not a better threshold — it is a different " +
            "signal, taken from the evidence rather than from the model's agreement with itself.",
        bars = listOf(
            NetBar(
                "selective accuracy at equal coverage",
                listOf(selective.toFloat(), HallucinationLab.groundedSelectiveAccuracy().toFloat()),
                AccentA,
                listOf("confidence τ=${"%.1f".format(threshold)}", "grounded"),
            ),
        ),
        readout = "${"%.3f".format(selective)} → ${"%.3f".format(HallucinationLab.groundedSelectiveAccuracy())} at ${"%.0f".format(coverage * 100)}% coverage",
    )

    val control = HallucinationLab.bestThreshold(scattered)
    frames += NetFrame(
        status = "One property causes all of it. Rerun the identical population with the model's errors " +
            "*scattered* instead of systematic — four different wrong answers instead of the same one — and " +
            "confidence separates the groups cleanly (${"%.2f".format(HallucinationLab.minSupportedConfidence(scattered))} " +
            "supported against ${"%.2f".format(HallucinationLab.maxUnsupportedConfidence(scattered))} " +
            "unsupported), and thresholding scores ${"%.3f".format(control.third)}, matching grounding. " +
            "Self-consistency measures how firmly the model believes something. That is a useful signal " +
            "against random slips and worthless against a confident false belief.",
        bars = listOf(
            NetBar(
                "confidence, unsupported questions",
                questions.filter { !it.supported }.map { HallucinationLab.confidence(it).toFloat() } +
                    scattered.filter { !it.supported }.map { HallucinationLab.confidence(it).toFloat() },
                OutputColor,
                List(4) { "systematic" } + List(4) { "scattered" },
            ),
        ),
        readout = "the signal works exactly where the failure is benign",
    )

    frames += NetFrame(
        status = "So the working stack is layered, and the order matters: retrieve so the fact is present, " +
            "constrain the answer to what the passages support, cite so a claim can be checked, and abstain " +
            "when nothing supports it. Confidence thresholds sit on top of that as a cheap filter for random " +
            "error — not as the mechanism. The failure this lab is built around is not noise, and no amount " +
            "of resampling finds it.",
        bars = listOf(
            NetBar(
                "accuracy by mitigation",
                listOf(
                    HallucinationLab.overallAccuracy().toFloat(),
                    selective.toFloat(),
                    HallucinationLab.groundedSelectiveAccuracy().toFloat(),
                ),
                AccentA,
                listOf("answer everything", "confidence τ", "grounded + abstain"),
            ),
        ),
        readout = "evidence beats agreement",
    )
    return frames
}

private val netConfigs = mapOf(
    "chain_of_thought" to NetConfig(
        intro = "The arithmetic of decomposition, and self-consistency's plurality vote computed exactly — " +
            "including the case where voting over more samples makes accuracy worse.",
        legend = listOf(ForwardColor to "Chain", AccentB to "Direct", BackwardColor to "Systematic error"),
        build = ::chainOfThoughtFrames,
    ),
    "hallucination_mitigation" to NetConfig(
        intro = "Ten questions, four of them unanswerable: what self-consistency confidence reports, what " +
            "abstaining on it actually buys, and the one property of the errors that decides whether it works.",
        legend = listOf(ForwardColor to "Accuracy", OutputColor to "Confidence", AccentA to "Grounded"),
        build = ::hallucinationFrames,
    ),
    "feed_forward" to NetConfig(
        intro = "The two-thirds of a transformer block nobody names the architecture after — its parameter " +
            "share, GELU against ReLU, SwiGLU's three-matrix reshuffle, and the sparsity MoE exploits.",
        legend = listOf(ForwardColor to "Forward", OutputColor to "Parameters", AccentA to "Activations"),
        build = ::feedForwardFrames,
    ),
    "gpt3_gpt4" to NetConfig(
        intro = "Scale as the contribution: the power law, the Chinchilla correction that showed GPT-3 was " +
            "undertrained, and the post-training that beat a 100× parameter advantage.",
        legend = listOf(ForwardColor to "Loss curve", OutputColor to "Token ratio", AccentA to "Preference"),
        build = ::scalingFrames,
    ),
    "llama_vicuna" to NetConfig(
        intro = "The inference-first argument for over-training a small model, the three architecture changes " +
            "everyone copied, and what open weights actually cost you.",
        legend = listOf(ForwardColor to "Token ratio", BackwardColor to "Inference cost", AccentB to "Changes"),
        build = ::llamaFrames,
    ),
    "sigmoid" to NetConfig(
        intro = "The curve, its 0.25 derivative ceiling, the saturation that grows with pre-activation width, and twenty layers of the consequence.",
        legend = listOf(ForwardColor to "σ(z)", BackwardColor to "σ′(z)", AccentA to "tanh"),
        build = ::sigmoidLabFrames,
    ),
    "tanh" to NetConfig(
        intro = "A rescaled sigmoid: zero-centred, derivative peaking at 1 instead of 0.25 — and saturating slightly sooner.",
        legend = listOf(ForwardColor to "tanh(z)", BackwardColor to "derivative", NeutralColor to "sigmoid"),
        build = ::tanhLabFrames,
    ),
    "relu" to NetConfig(
        intro = "Derivative exactly 1 where active, a fixed 50% zero region that does not grow, and the dying-unit failure measured under real SGD.",
        legend = listOf(ForwardColor to "ReLU", BackwardColor to "Dead units", AccentA to "Leaky ReLU"),
        build = ::reluLabFrames,
    ),
    "leaky_relu" to NetConfig(
        intro = "One character of difference from ReLU, run through the same learning-rate sweep that killed ReLU's units.",
        legend = listOf(ForwardColor to "Leaky ReLU", NeutralColor to "ReLU", AccentA to "Dead: none"),
        build = ::leakyReluLabFrames,
    ),
    "prelu" to NetConfig(
        intro = "The negative slope as a learned parameter: α starts at 0 and gradient descent finds the value the data wants.",
        legend = listOf(ForwardColor to "Learned α", NeutralColor to "ReLU", BackwardColor to "Loss"),
        build = ::preluLabFrames,
    ),
    "elu" to NetConfig(
        intro = "A smooth negative branch saturating at −α, and the mean-activation argument that motivates it.",
        legend = listOf(ForwardColor to "ELU", NeutralColor to "ReLU", BackwardColor to "Saturation"),
        build = ::eluLabFrames,
    ),
    "selu" to NetConfig(
        intro = "The self-normalising claim, measured over twenty layers — and what happens to it under the wrong initialisation.",
        legend = listOf(AccentA to "SELU", NeutralColor to "ReLU", BackwardColor to "tanh / wrong init"),
        build = ::seluLabFrames,
    ),
    "swish" to NetConfig(
        intro = "Self-gated and non-monotone: the dip below zero, a derivative above 1, and what β interpolates between.",
        legend = listOf(ForwardColor to "Swish", NeutralColor to "ReLU", AccentB to "β variants"),
        build = ::swishLabFrames,
    ),
    "gelu" to NetConfig(
        intro = "z·Φ(z), measured against ReLU and Swish — plus the tanh approximation the pretrained checkpoints were fitted with.",
        legend = listOf(ForwardColor to "GELU", NeutralColor to "ReLU", AccentB to "Swish"),
        build = ::geluLabFrames,
    ),
    "softmax" to NetConfig(
        intro = "The one activation here that maps a vector to a vector: temperature, the overflow you must avoid, and a Jacobian instead of a derivative.",
        legend = listOf(ForwardColor to "Probabilities", NeutralColor to "Logits", AccentA to "Stable"),
        build = ::softmaxLabFrames,
    ),
    "biological_neuron" to NetConfig(
        intro = "A leaky integrate-and-fire neuron, simulated properly — sub-threshold, firing, and its full f–I curve — beside the artificial unit that abstracts it.",
        legend = listOf(ForwardColor to "Membrane / f–I", BackwardColor to "Threshold", OutputColor to "Response"),
        build = ::biologicalNeuronFrames,
    ),
    "mlp" to NetConfig(
        intro = "A 2-3-1 network actually trained on XOR by backpropagation, then the same network with the non-linearity removed.",
        legend = listOf(ForwardColor to "Forward", AccentA to "With tanh", BackwardColor to "Linear hidden"),
        build = ::mlpFrames,
    ),
    "vanishing_gradient" to NetConfig(
        intro = "Twelve sigmoid layers, one real backward pass, and the gradient norm measured at every layer on a log scale.",
        legend = listOf(ForwardColor to "Forward pass", BackwardColor to "Sigmoid gradient", AccentA to "ReLU + He"),
        build = ::vanishingGradientFrames,
    ),
    "exploding_gradient" to NetConfig(
        intro = "The same stack with a careless initialisation: activations and gradients compounding upward, and what global-norm clipping does about it.",
        legend = listOf(AccentA to "Healthy", BackwardColor to "Exploding", NeutralColor to "Vanishing"),
        build = ::explodingGradientFrames,
    ),
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
    "bptt" to NetConfig(
        intro = "One recurrent classifier trained at six truncation windows on a dependency nine steps long. The " +
            "gradient similarity and the accuracy disagree, and the accuracy is the one that matters.",
        legend = listOf(
            BackwardColor to "Gradient",
            OutputColor to "Learned",
            NeutralColor to "Full BPTT",
        ),
        build = ::bpttFrames,
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

internal val neuralNetTopicIds: Set<String> get() = netConfigs.keys

internal fun neuralNetFrameCount(topicId: String): Int {
    val frames = netConfigFor(topicId).build()
    frames.forEach { frame ->
        // A plot whose curve leaves its declared range is drawn clamped, so a builder that gets its
        // axis wrong looks plausible on screen. C1's log-scale plots span twenty orders of
        // magnitude, which is exactly where that mistake is easy to make.
        frame.plot?.let { plot ->
            val stray = plot.curves.flatMap { it.points }.count { (x, y) ->
                !x.isFinite() || !y.isFinite() ||
                    x !in plot.xRange || y !in (plot.yRange.start - 0.001f)..(plot.yRange.endInclusive + 0.001f)
            }
            require(stray == 0) { "$topicId plots $stray point(s) outside '${plot.label}' declared range" }
        }
        frame.bars.forEach { bar ->
            require(bar.values.all { it.isFinite() }) { "$topicId draws a non-finite bar in '${bar.label}'" }
            require(bar.captions.isEmpty() || bar.captions.size == bar.values.size) {
                "$topicId bar '${bar.label}' has ${bar.captions.size} captions for ${bar.values.size} values"
            }
        }
    }
    return frames.size
}

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
