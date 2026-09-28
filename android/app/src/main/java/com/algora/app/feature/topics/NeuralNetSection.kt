package com.algora.app.feature.topics

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
private val NeutralColor = SimColors.Grey
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

// ── Evaluation metrics (phase 9, batch B9) ───────────────────────────────────
// The six metric topics whose subject is a curve rather than a picture of the data: the two ranking
// summaries, the two probability/agreement corrections, and the two loss functions. All of them read
// MetricsMath.kt, and the classification four read the same score vector the PointCloudPlayer labs
// draw — so ROC AUC 0.969 and F1 0.569 are two readings of one model.

private fun rocCurveFrames(): List<NetFrame> {
    val frames = mutableListOf<NetFrame>()
    val roc = ScoredLab.rocCurve.map { it.first.toFloat() to it.second.toFloat() }
    val diagonal = listOf(0f to 0f, 1f to 1f)
    val half = ScoredLab.cellsAt(0.5)

    frames += NetFrame(
        status = "A ROC curve plots one point per threshold: true positive rate against false positive rate. At " +
            "t = 0.5 this model sits at FPR ${"%.3f".format(half.falsePositiveRate)}, TPR " +
            "${"%.3f".format(half.recall)} — one operating point out of the thousand the score vector allows.",
        plot = CurvePlot(
            "TPR against FPR",
            listOf(
                Curve("chance", diagonal, NeutralColor),
                Curve("this threshold", listOf(half.falsePositiveRate.toFloat() to half.recall.toFloat()), OutputColor),
            ),
            0f..1f, 0f..1f,
        ),
        readout = "one threshold, one point",
    )

    frames += NetFrame(
        status = "Sweeping every threshold traces the curve. The diagonal is a model that has learned nothing — a " +
            "coin flip trades TPR for FPR one for one — so the distance above it is the whole signal. This curve " +
            "reaches TPR ${"%.2f".format(roc.first { it.first >= 0.1f }.second)} at 10% false positives.",
        plot = CurvePlot(
            "ROC",
            listOf(Curve("chance", diagonal, NeutralColor), Curve("model", roc, ForwardColor)),
            0f..1f, 0f..1f,
        ),
        readout = "${ScoredLab.rocCurve.size} operating points",
    )

    frames += NetFrame(
        status = "What makes it useful is what it does *not* depend on: the curve is a function of the ranking " +
            "only, so it does not move when the threshold moves and it does not move when the class balance " +
            "changes. That independence is also the trap — a curve this good on a 9% positive rate can coexist " +
            "with precision most teams would reject.",
        plot = CurvePlot(
            "ROC, with the 0.5 operating point marked",
            listOf(
                Curve("chance", diagonal, NeutralColor),
                Curve("model", roc, ForwardColor),
                Curve("t = 0.5", listOf(half.falsePositiveRate.toFloat() to half.recall.toFloat()), OutputColor),
            ),
            0f..1f, 0f..1f,
        ),
        readout = "threshold-free by construction",
    )

    val pr = ScoredLab.prCurve.map { it.first.toFloat() to it.second.toFloat() }
    val atRecall = ScoredLab.prCurve.filter { it.first >= 0.8 }.maxByOrNull { it.second }
    frames += NetFrame(
        status = "Here is the same model's precision-recall curve, which does depend on the base rate. At 80% " +
            "recall the best precision available is ${"%.3f".format(atRecall?.second ?: 0.0)} — so finding four " +
            "fifths of the positives means ${"%.0f".format((1 - (atRecall?.second ?: 0.0)) * 100)}% of the flags " +
            "are wrong. The ROC curve above cannot show you that, because false positives are measured against " +
            "${ScoredLab.negatives} negatives rather than against the flags.",
        plot = CurvePlot(
            "precision against recall",
            listOf(
                Curve("base rate", listOf(0f to (ScoredLab.positives.toFloat() / ScoredLab.scored.size), 1f to (ScoredLab.positives.toFloat() / ScoredLab.scored.size)), NeutralColor),
                Curve("model", pr, AccentB),
            ),
            0f..1f, 0f..1f,
        ),
        readout = "precision ${"%.3f".format(atRecall?.second ?: 0.0)} at recall 0.80",
    )

    frames += NetFrame(
        status = "So: use ROC to compare rankers and to choose an operating point when both classes matter, and " +
            "use precision-recall when the positive class is rare and the flags have a cost. On balanced data the " +
            "two tell the same story; on this data they do not, and the PR curve is the honest one.",
        plot = CurvePlot(
            "both curves, same model",
            listOf(Curve("ROC", roc, ForwardColor), Curve("PR", pr, AccentB), Curve("chance", diagonal, NeutralColor)),
            0f..1f, 0f..1f,
        ),
        readout = "same scores, two verdicts",
    )
    return frames
}

private fun aucFrames(): List<NetFrame> {
    val frames = mutableListOf<NetFrame>()
    val roc = ScoredLab.rocCurve.map { it.first.toFloat() to it.second.toFloat() }

    frames += NetFrame(
        status = "AUC is the area under that curve: ${"%.4f".format(ScoredLab.auc)} here, by the trapezoid rule " +
            "over ${ScoredLab.rocCurve.size} operating points. One number for a whole curve, and it needs no " +
            "threshold — which is why it is the default for comparing models before anyone has decided how the " +
            "model will be used.",
        plot = CurvePlot(
            "area under the ROC curve",
            listOf(Curve("chance = 0.5", listOf(0f to 0f, 1f to 1f), NeutralColor), Curve("model", roc, ForwardColor)),
            0f..1f, 0f..1f,
        ),
        readout = "AUC ${"%.4f".format(ScoredLab.auc)}",
    )

    frames += NetFrame(
        status = "It also has an exact probabilistic meaning, which is the more useful way to hold it: AUC is the " +
            "probability that a randomly chosen positive outranks a randomly chosen negative. Computed that way — " +
            "over all ${ScoredLab.positives} × ${ScoredLab.negatives} = " +
            "${ScoredLab.positives * ScoredLab.negatives} pairs — it comes to " +
            "${"%.4f".format(ScoredLab.aucByRanking)}, the same number the area gives.",
        bars = listOf(
            NetBar("AUC by area", listOf(ScoredLab.auc.toFloat()), ForwardColor, listOf("trapezoid")),
            NetBar("AUC by pair counting", listOf(ScoredLab.aucByRanking.toFloat()), AccentA, listOf("${ScoredLab.positives * ScoredLab.negatives} pairs")),
        ),
        readout = "two definitions, one number",
    )

    frames += NetFrame(
        status = "Being a ranking statistic is what AUC costs. Any monotone transform of the scores leaves the " +
            "ranking untouched, so it leaves AUC untouched to the digit — this lab pushes every score towards 0 or " +
            "1 and AUC stays ${"%.4f".format(ScoredLab.overconfidentAuc)} while log loss rises from " +
            "${"%.4f".format(ScoredLab.logLoss())} to ${"%.4f".format(ScoredLab.logLoss(ScoredLab.overconfident))}. " +
            "AUC cannot see calibration at all.",
        bars = listOf(
            NetBar("AUC", listOf(ScoredLab.auc.toFloat(), ScoredLab.overconfidentAuc.toFloat()), ForwardColor, listOf("original", "distorted")),
            NetBar("log loss", listOf(ScoredLab.logLoss().toFloat(), ScoredLab.logLoss(ScoredLab.overconfident).toFloat()), BackwardColor, listOf("original", "distorted")),
        ),
        readout = "identical AUC, ${"%.0f".format((ScoredLab.logLoss(ScoredLab.overconfident) / ScoredLab.logLoss() - 1) * 100)}% worse log loss",
    )

    frames += NetFrame(
        status = "And on an imbalanced problem AUC reads generously. ${"%.3f".format(ScoredLab.auc)} sounds close " +
            "to solved, while average precision — the same curve's PR counterpart — is " +
            "${"%.3f".format(ScoredLab.averagePrecision)} and precision at 80% recall is " +
            "${"%.3f".format(ScoredLab.prCurve.filter { it.first >= 0.8 }.maxOf { it.second })}. The false " +
            "positives are being divided by ${ScoredLab.negatives} negatives, which is a large denominator.",
        bars = listOf(
            NetBar(
                "three summaries of one model",
                listOf(ScoredLab.auc.toFloat(), ScoredLab.averagePrecision.toFloat(), ScoredLab.prCurve.filter { it.first >= 0.8 }.maxOf { it.second }.toFloat()),
                OutputColor,
                listOf("AUC", "avg precision", "P@R=0.8"),
            ),
        ),
        readout = "${"%.3f".format(ScoredLab.auc)} vs ${"%.3f".format(ScoredLab.averagePrecision)}",
    )

    frames += NetFrame(
        status = "So AUC answers exactly one question well — does this model rank better than that one — and three " +
            "questions badly: is it calibrated, how does it do at the operating point I will use, and is it good " +
            "enough on a rare class. Report it with average precision, and never ship a threshold chosen from it.",
        bars = listOf(
            NetBar("AUC answers", listOf(1f, 0f, 0f, 0f), AccentA, listOf("ranking", "calibration", "operating point", "rare class")),
        ),
        readout = "one question, well",
    )
    return frames
}

private fun logLossFrames(): List<NetFrame> {
    val frames = mutableListOf<NetFrame>()
    val loss = ScoredLab.logLoss()
    val error = ScoredLab.confidentError

    frames += NetFrame(
        status = "Log loss scores the probability, not the decision: −log p for a positive, −log(1 − p) for a " +
            "negative, averaged. On this model it is ${"%.4f".format(loss)}. Nothing is thresholded, so a " +
            "prediction of 0.51 and one of 0.99 are two different answers rather than the same one.",
        plot = CurvePlot(
            "−log p, the cost of a prediction on a positive case",
            listOf(Curve("−log p", (1..99).map { (it / 100f) to (-kotlin.math.ln(it / 100.0)).toFloat() }, BackwardColor)),
            0f..1f, 0f..5f,
        ),
        readout = "log loss ${"%.4f".format(loss)}",
    )

    frames += NetFrame(
        status = "The shape of that curve is the whole behaviour. It is unbounded as p → 0, so one confident " +
            "mistake can dominate an average over a thousand cases: the worst single prediction here contributes " +
            "${"%.3f".format(error.worstContribution)} against a mean contribution of " +
            "${"%.4f".format(error.meanContribution)} — ${"%.0f".format(error.worstContribution / error.meanContribution)}× " +
            "— from a case scored ${"%.3f".format(error.worstScore)} that was in fact positive.",
        bars = listOf(
            NetBar("contribution to the mean", listOf(error.meanContribution.toFloat(), error.worstContribution.toFloat()), BackwardColor, listOf("average case", "worst case")),
        ),
        readout = "${"%.0f".format(error.worstContribution / error.meanContribution)}× the average, from one case",
    )

    frames += NetFrame(
        status = "Which makes log loss the metric that notices calibration. Push this model's scores towards 0 and " +
            "1 by a monotone transform — the ranking is unchanged, so AUC stays at " +
            "${"%.4f".format(ScoredLab.overconfidentAuc)} exactly — and log loss goes " +
            "${"%.4f".format(loss)} → ${"%.4f".format(ScoredLab.logLoss(ScoredLab.overconfident))}. Overconfidence " +
            "is invisible to every ranking metric and expensive here.",
        bars = listOf(
            NetBar("log loss", listOf(loss.toFloat(), ScoredLab.logLoss(ScoredLab.overconfident).toFloat()), BackwardColor, listOf("original", "overconfident")),
            NetBar("AUC", listOf(ScoredLab.auc.toFloat(), ScoredLab.overconfidentAuc.toFloat()), NeutralColor, listOf("original", "overconfident")),
        ),
        readout = "the metric that can tell them apart",
    )

    val calibration = ScoredLab.calibration()
    frames += NetFrame(
        status = "It does not tell you *how* the probabilities are wrong, though — for that, bin them. This " +
            "model's upper bins are under-confident: cases it scores around " +
            "${"%.2f".format(calibration.first { it.lower >= 0.4 }.predicted)} come true " +
            "${"%.0f".format(calibration.first { it.lower >= 0.4 }.observed * 100)}% of the time. A reliability " +
            "curve above the diagonal means the model is too cautious, and log loss will punish that as surely as " +
            "it punishes overconfidence.",
        plot = CurvePlot(
            "observed rate against predicted probability",
            listOf(
                Curve("perfect", listOf(0f to 0f, 1f to 1f), NeutralColor),
                Curve("model", calibration.map { it.predicted.toFloat() to it.observed.toFloat() }, ForwardColor),
            ),
            0f..1f, 0f..1f,
        ),
        readout = "reliability, in ten bins",
    )

    frames += NetFrame(
        status = "The gentler alternative is the Brier score, the mean squared error of the probability: " +
            "${"%.4f".format(ScoredLab.brier())} here, rising to " +
            "${"%.4f".format(ScoredLab.brier(ScoredLab.overconfident))} under the same distortion. It is bounded, " +
            "so one catastrophic prediction cannot swamp it — which makes it more robust and less sensitive, and " +
            "the choice between them is exactly that trade.",
        bars = listOf(
            NetBar("log loss", listOf(loss.toFloat(), ScoredLab.logLoss(ScoredLab.overconfident).toFloat()), BackwardColor, listOf("original", "overconfident")),
            NetBar("Brier", listOf(ScoredLab.brier().toFloat(), ScoredLab.brier(ScoredLab.overconfident).toFloat()), AccentA, listOf("original", "overconfident")),
        ),
        readout = "unbounded and sensitive, or bounded and blunt",
    )
    return frames
}

private fun kappaFrames(): List<NetFrame> {
    val frames = mutableListOf<NetFrame>()
    val half = ScoredLab.cellsAt(0.5)
    val majority = ScoredLab.cellsAt(0.999)

    frames += NetFrame(
        status = "Cohen's kappa asks what accuracy is worth after subtracting the agreement two raters would reach " +
            "by chance: κ = (observed − expected) / (1 − expected), where expected comes from the two sets of " +
            "marginals. On this matrix observed agreement is ${"%.3f".format(half.accuracy)} and kappa is " +
            "${"%.3f".format(half.kappa)}.",
        bars = listOf(
            NetBar("agreement", listOf(half.accuracy.toFloat(), half.kappa.toFloat()), ForwardColor, listOf("observed", "chance-corrected")),
        ),
        readout = "accuracy ${"%.3f".format(half.accuracy)} → κ ${"%.3f".format(half.kappa)}",
    )

    frames += NetFrame(
        status = "The correction bites hardest exactly where accuracy is most flattering. A model that predicts " +
            "\"negative\" for everything scores accuracy ${"%.3f".format(majority.accuracy)} on this data — and " +
            "kappa ${"%.3f".format(majority.kappa)}, because chance agreement between \"always negative\" and the " +
            "true labels is already ${"%.3f".format(majority.accuracy)}. Kappa reports what accuracy conceals: " +
            "nothing was learned.",
        bars = listOf(
            NetBar("predict-all-negative", listOf(majority.accuracy.toFloat(), majority.kappa.toFloat()), BackwardColor, listOf("accuracy", "kappa")),
            NetBar("the trained model", listOf(half.accuracy.toFloat(), half.kappa.toFloat()), ForwardColor, listOf("accuracy", "kappa")),
        ),
        readout = "accuracy ${"%.3f".format(majority.accuracy)}, κ ${"%.3f".format(majority.kappa)}",
    )

    val sweep = ScoredLab.thresholds.map { it to ScoredLab.cellsAt(it) }
    frames += NetFrame(
        status = "Across thresholds, kappa moves where accuracy does not. Accuracy spans " +
            "${"%.3f".format(sweep.minOf { it.second.accuracy })}–${"%.3f".format(sweep.maxOf { it.second.accuracy })} " +
            "while kappa spans ${"%.3f".format(sweep.minOf { it.second.kappa })}–${"%.3f".format(sweep.maxOf { it.second.kappa })}. " +
            "A metric with range is a metric you can optimise against.",
        plot = CurvePlot(
            "accuracy and kappa against threshold",
            listOf(
                Curve("accuracy", sweep.map { it.first.toFloat() to it.second.accuracy.toFloat() }, NeutralColor),
                Curve("kappa", sweep.map { it.first.toFloat() to it.second.kappa.toFloat() }, OutputColor),
            ),
            0f..1f, 0f..1f,
        ),
        readout = "kappa has ${"%.1f".format((sweep.maxOf { it.second.kappa } - sweep.minOf { it.second.kappa }) / (sweep.maxOf { it.second.accuracy } - sweep.minOf { it.second.accuracy }))}× the range",
    )

    frames += NetFrame(
        status = "Its close relative is the Matthews correlation coefficient, which uses all four cells too and " +
            "reads ${"%.3f".format(half.matthews)} here against kappa's ${"%.3f".format(half.kappa)}. Both are " +
            "chance-corrected and both are symmetric in the classes; MCC is the one most commonly recommended for " +
            "imbalanced binary problems, and on this matrix they agree closely.",
        bars = listOf(
            NetBar("chance-corrected metrics", listOf(half.kappa.toFloat(), half.matthews.toFloat()), AccentA, listOf("Cohen's κ", "MCC")),
            NetBar("uncorrected", listOf(half.accuracy.toFloat(), half.f1.toFloat()), NeutralColor, listOf("accuracy", "F1")),
        ),
        readout = "κ ${"%.3f".format(half.kappa)}, MCC ${"%.3f".format(half.matthews)}",
    )

    frames += NetFrame(
        status = "One caution the interpretation tables never carry: κ depends on the marginals, so the same " +
            "quality of agreement scores differently at different base rates and two kappas from different " +
            "datasets are not comparable. Use it to compare models on one dataset, and never to claim a model is " +
            "\"substantially better than chance\" in the abstract.",
        bars = listOf(
            NetBar("what κ compares", listOf(1f, 0f), OutputColor, listOf("models on one dataset", "across datasets")),
        ),
        readout = "chance-corrected, not base-rate-free",
    )
    return frames
}

private fun giniImpurityFrames(): List<NetFrame> {
    val frames = mutableListOf<NetFrame>()
    val curve = ImpurityLab.impurityCurve()
    val xs = (0 until curve.size).map { it.toFloat() / (curve.size - 1) }

    frames += NetFrame(
        status = "Gini impurity is the probability that two items drawn from a node have different labels: " +
            "1 − Σpᵢ². It is 0 when a node is pure and ${"%.1f".format(ImpurityLab.giniMaximum)} at a 50/50 split " +
            "on a binary problem — which is its maximum, where entropy's is " +
            "${"%.1f".format(ImpurityLab.entropyMaximum)}.",
        plot = CurvePlot(
            "impurity against class balance",
            listOf(
                Curve("Gini", xs.zip(curve.map { it.first.toFloat() }), ForwardColor),
                Curve("entropy (bits)", xs.zip(curve.map { it.second.toFloat() }), AccentB),
                Curve("misclassification", xs.zip(curve.map { it.third.toFloat() }), NeutralColor),
            ),
            0f..1f, 0f..1.05f,
        ),
        readout = "Gini ≤ ${"%.1f".format(ImpurityLab.giniMaximum)}, entropy ≤ ${"%.1f".format(ImpurityLab.entropyMaximum)}",
    )

    frames += NetFrame(
        status = "A tree uses it as a *gain*: the parent's impurity minus the weighted impurity of the children. " +
            "On a parent of ${ImpurityLab.parentPositive + ImpurityLab.parentNegative} split ${ImpurityLab.parentPositive}/${ImpurityLab.parentNegative}, " +
            "four candidate splits score " +
            ImpurityLab.scores.joinToString(", ") { "${it.split.name.take(1)} ${"%.3f".format(it.giniGain)}" } +
            " — and the split that separates the classes completely wins with ${"%.3f".format(ImpurityLab.scores.maxOf { it.giniGain })}.",
        bars = listOf(
            NetBar("Gini gain", ImpurityLab.scores.map { it.giniGain.toFloat() }, ForwardColor, ImpurityLab.scores.map { it.split.name.take(1) }),
            NetBar("entropy gain", ImpurityLab.scores.map { it.entropyGain.toFloat() }, AccentB, ImpurityLab.scores.map { it.split.name.take(1) }),
        ),
        readout = "same ranking, different units",
    )

    val tied = ImpurityLab.tiedForError
    frames += NetFrame(
        status = "The third curve is the one that explains why trees are not grown on error rate. Splits " +
            "${tied.first.split.name.take(1)} and ${tied.second.split.name.take(1)} produce *identical* " +
            "misclassification gain — ${"%.3f".format(tied.first.errorGain)} both — while Gini prefers " +
            "${tied.second.split.name.take(1)} at ${"%.3f".format(tied.second.giniGain)} over " +
            "${"%.3f".format(tied.first.giniGain)}. Error rate is piecewise linear, so it cannot see progress " +
            "that does not yet change a majority vote.",
        bars = listOf(
            NetBar("misclassification gain", ImpurityLab.scores.map { it.errorGain.toFloat() }, NeutralColor, ImpurityLab.scores.map { it.split.name.take(1) }),
            NetBar("Gini gain", ImpurityLab.scores.map { it.giniGain.toFloat() }, ForwardColor, ImpurityLab.scores.map { it.split.name.take(1) }),
        ),
        readout = "tied at ${"%.3f".format(tied.first.errorGain)}, separated by Gini",
    )

    frames += NetFrame(
        status = "Gini and entropy, by contrast, almost never disagree — both are strictly concave, so both reward " +
            "the same kind of progress. On these four splits they rank identically. The practical difference is " +
            "cost: Gini needs no logarithm, which is why CART defaults to it and why it is the default in " +
            "scikit-learn.",
        plot = CurvePlot(
            "Gini against entropy/2, rescaled to compare shape",
            listOf(
                Curve("Gini", xs.zip(curve.map { it.first.toFloat() }), ForwardColor),
                Curve("entropy / 2", xs.zip(curve.map { (it.second / 2).toFloat() }), AccentB),
            ),
            0f..1f, 0f..0.55f,
        ),
        readout = "same shape, no logarithm",
    )

    frames += NetFrame(
        status = "Worth keeping separate from a metric, though: Gini impurity is a *splitting criterion*, not an " +
            "evaluation metric, and it is unrelated to the Gini coefficient from economics (which is 2·AUC − 1 in " +
            "this context, a ranking summary). Two different things with one name, and only one of them is what a " +
            "tree computes at every node.",
        bars = listOf(
            NetBar("Gini impurity, at a node", listOf(ImpurityLab.gini(ImpurityLab.parentPositive, ImpurityLab.parentNegative).toFloat()), ForwardColor, listOf("1 − Σp²")),
            NetBar("Gini coefficient, from AUC", listOf((2 * ScoredLab.auc - 1).toFloat()), AccentB, listOf("2·AUC − 1")),
        ),
        readout = "same name, different objects",
    )
    return frames
}

private fun hingeLossFrames(): List<NetFrame> {
    val frames = mutableListOf<NetFrame>()
    val hinge = MarginLab.curve(MarginLab::hinge).map { it.first.toFloat() to it.second.toFloat() }
    val logistic = MarginLab.curve(MarginLab::logistic).map { it.first.toFloat() to it.second.toFloat() }
    val zeroOne = MarginLab.curve(MarginLab::zeroOne).map { it.first.toFloat() to it.second.toFloat() }
    val active = MarginLab.active

    frames += NetFrame(
        status = "Hinge loss is max(0, 1 − y·f(x)): zero once a prediction is correct *by a margin of 1*, and " +
            "linear before that. The quantity on the x-axis is the margin, not the probability — hinge loss is " +
            "defined on a decision value, which is why it belongs to SVMs rather than to logistic regression.",
        plot = CurvePlot(
            "loss against margin y·f(x)",
            listOf(
                Curve("hinge", hinge, ForwardColor),
                Curve("0-1 loss", zeroOne, NeutralColor),
            ),
            -2f..3f, 0f..3f,
        ),
        readout = "zero past a margin of 1",
    )

    frames += NetFrame(
        status = "It exists because the loss anyone actually wants — 0-1 loss, the grey step — is not " +
            "differentiable and not convex, so nothing can be optimised against it. Hinge is the tightest convex " +
            "upper bound that is also an upper bound on 0-1 loss, which is the sense in which minimising it is a " +
            "principled stand-in for minimising errors.",
        plot = CurvePlot(
            "three losses on the same margin",
            listOf(
                Curve("0-1 loss", zeroOne, NeutralColor),
                Curve("hinge", hinge, ForwardColor),
                Curve("logistic", logistic, AccentB),
            ),
            -2f..3f, 0f..3f,
        ),
        readout = "convex surrogates for a step function",
    )

    frames += NetFrame(
        status = "The difference that matters between hinge and logistic is not the shape, it is the zero. Hinge " +
            "is exactly 0 past the margin, so those examples contribute no gradient at all — measured on this " +
            "model's ${active.total} margins, only ${active.hingeActive} still have any hinge loss, against " +
            "${active.logisticActive} that still have logistic loss. An SVM's solution depends on " +
            "${active.hingeActive} points; a logistic fit depends on all ${active.total}.",
        bars = listOf(
            NetBar(
                "examples the loss still cares about",
                listOf(active.hingeActive.toFloat(), active.logisticActive.toFloat()),
                ForwardColor,
                listOf("hinge", "logistic"),
            ),
        ),
        readout = "${active.hingeActive} of ${active.total} against ${active.logisticActive} of ${active.total}",
    )

    frames += NetFrame(
        status = "Priced as a gradient: at a margin of 1 the hinge gradient is exactly " +
            "${"%.0f".format(kotlin.math.abs(MarginLab.hingeGradient(1.0)))} and stays there, while the logistic " +
            "gradient is ${"%.3f".format(MarginLab.logisticGradientAt(1.0))} and never reaches zero — " +
            "${"%.1e".format(MarginLab.logisticGradientAt(10.0))} even at a margin of 10. Logistic regression " +
            "keeps pushing correct points further from the boundary forever; that is why it needs regularization " +
            "to stop.",
        plot = CurvePlot(
            "gradient magnitude against margin",
            listOf(
                Curve("hinge", MarginLab.curve({ kotlin.math.abs(MarginLab.hingeGradient(it)) }).map { it.first.toFloat() to it.second.toFloat() }, ForwardColor),
                Curve("logistic", MarginLab.curve({ MarginLab.logisticGradientAt(it) }).map { it.first.toFloat() to it.second.toFloat() }, AccentB),
            ),
            -2f..3f, 0f..1.05f,
        ),
        readout = "one stops, one does not",
    )

    frames += NetFrame(
        status = "Two consequences worth carrying. Hinge loss gives you support vectors — a sparse solution " +
            "determined by a few points — and it gives you no probabilities, because a margin is not a " +
            "likelihood; anything probabilistic from an SVM comes from a separate calibration step. Squared hinge " +
            "is the smooth variant, and it trades the sparsity back for differentiability everywhere.",
        plot = CurvePlot(
            // Squared hinge reaches (1 − (−2))² = 9 at the left edge, so the axis has to hold 9 and
            // not 3 — caught by the frame guard rather than by looking at the picture.
            "hinge against squared hinge",
            listOf(
                Curve("hinge", hinge, ForwardColor),
                Curve("squared hinge", MarginLab.curve(MarginLab::squaredHinge).map { it.first.toFloat() to it.second.toFloat() }, OutputColor),
            ),
            -2f..3f, 0f..9.2f,
        ),
        readout = "sparse and non-smooth, or smooth and dense",
    )
    return frames
}

// ── C7 · Generative deep learning ────────────────────────────────────────────
// Five of the batch's labs. Every number below comes from `GenerativeMath.kt`, which is pinned by
// `GenerativeMathTest` — nothing here is a literal typed twice.

private fun vaeFrames(): List<NetFrame> {
    val frames = mutableListOf<NetFrame>()
    val variance = VaeLab.gradientVarianceAtEight

    frames += NetFrame(
        status = "A VAE's encoder stops emitting a point and starts emitting a distribution: a mean and a " +
            "variance for each latent dimension. Six inputs, four latent dimensions, six outputs — but the " +
            "middle layer now carries two numbers per unit, not one.",
        layers = listOf(
            NetLayer("input", List(VaeLab.DATA_DIM) { NetNode(0f, NodeMood.FORWARD) }),
            NetLayer("μ, log σ²", List(VaeLab.LATENT_DIM) { NetNode(0f, NodeMood.IDLE) }),
            NetLayer("output", List(VaeLab.DATA_DIM) { NetNode(0f, NodeMood.IDLE) }),
        ),
    )
    frames += NetFrame(
        status = "Sampling z from that distribution is not differentiable, so the randomness is moved into an " +
            "input instead: z = μ + σ⊙ε with ε ~ N(0, I). The usual explanation stops here, and it is " +
            "incomplete — the alternative is not impossible, it is just unusable.",
        bars = listOf(
            NetBar("ε ~ N(0, I)", listOf(0.4f, -1.1f, 0.7f, -0.3f), AccentB, listOf("ε1", "ε2", "ε3", "ε4")),
        ),
        readout = "z = μ + σ⊙ε",
    )
    frames += NetFrame(
        status = "The score-function estimator differentiates through a sample perfectly well and is unbiased: " +
            "on this objective it recovers ${"%.2f".format(variance.scoreFunctionMean)} against a true gradient of " +
            "${"%.1f".format(variance.trueGradient)}, and the reparameterized one recovers " +
            "${"%.2f".format(variance.reparameterizedMean)}. Both are correct on average. Only one is usable.",
        bars = listOf(
            NetBar(
                "estimator mean (true = 2.0)",
                listOf(variance.reparameterizedMean.toFloat(), variance.scoreFunctionMean.toFloat()),
                ForwardColor,
                listOf("reparam", "score"),
            ),
        ),
        readout = "both unbiased",
    )
    frames += NetFrame(
        status = "The variances are ${"%.2f".format(variance.reparameterizedVariance)} and " +
            "${"%.1f".format(variance.scoreFunctionVariance)} — a factor of ${"%.0f".format(variance.ratio)} at eight " +
            "dimensions. And the gap is not a constant: the reparameterized variance is flat in dimension and the " +
            "other grows without bound, which is the whole reason one of them scales to a real latent space.",
        plot = autoPlot(
            "gradient variance against latent dimension",
            listOf(
                Curve(
                    "reparameterized",
                    VaeLab.varianceDims.map { it.toFloat() to VaeLab.REPARAMETERIZED_VARIANCE_CLOSED_FORM.toFloat() },
                    ForwardColor,
                ),
                Curve(
                    "score-function",
                    VaeLab.varianceDims.map { it.toFloat() to VaeLab.scoreFunctionVarianceClosedForm(it).toFloat() },
                    BackwardColor,
                ),
            ),
            1f..16f,
        ),
        readout = "×7.5 at d=1 · ×95 at d=8 · ×316 at d=16",
    )

    val atOne = VaeLab.trained.getValue(1.0)
    frames += NetFrame(
        status = "The KL term does not merely smooth the latent space — it switches dimensions off. Trained on data " +
            "with exactly two underlying factors but given four latent dimensions, a plain VAE at β = 1 keeps " +
            "${atOne.activeUnits}. The other two collapse onto the prior and the decoder ignores them entirely.",
        bars = listOf(
            NetBar(
                "KL per dimension, β = 1",
                atOne.perDimensionKl.map { it.toFloat() },
                OutputColor,
                List(VaeLab.LATENT_DIM) { "z${it + 1}" },
            ),
        ),
        readout = "${atOne.activeUnits} of ${VaeLab.LATENT_DIM} active · threshold ${VaeLab.ACTIVE_THRESHOLD} nats",
    )

    val low = VaeLab.trained.getValue(0.001)
    val high = VaeLab.trained.getValue(4.0)
    frames += NetFrame(
        status = "β is the dial, and it is coarser than it looks. At β = 0.001 all ${low.activeUnits} dimensions stay " +
            "alive; at β = 4 only ${high.activeUnits} does, and reconstruction error has gone from " +
            "${"%.3f".format(low.reconstructionRmse)} to ${"%.3f".format(high.reconstructionRmse)} — it is eating a " +
            "real factor by then.",
        bars = listOf(
            NetBar("β = 0.001", low.perDimensionKl.map { it.toFloat() }, AccentA, List(VaeLab.LATENT_DIM) { "z${it + 1}" }),
            NetBar("β = 4", high.perDimensionKl.map { it.toFloat() }, AccentB, List(VaeLab.LATENT_DIM) { "z${it + 1}" }),
        ),
    )
    frames += NetFrame(
        status = "Across the whole sweep the active count sits at two — the data's true factor count — from β = 0.05 " +
            "all the way to β = 2.0, a fortyfold range. What β buys smoothly is not dimensionality but " +
            "reconstruction error, which climbs monotonically the entire way.",
        plot = autoPlot(
            "reconstruction error against β",
            listOf(
                Curve(
                    "RMSE",
                    VaeLab.betaSweep.map { b -> b.toFloat() to VaeLab.trained.getValue(b).reconstructionRmse.toFloat() },
                    OutputColor,
                ),
            ),
            0f..4f,
        ),
        readout = "active units 4 · 3 · 2 · 2 · 2 · 2 · 2 · 1",
    )
    return frames
}

private fun dcganFrames(): List<NetFrame> {
    val frames = mutableListOf<NetFrame>()

    frames += NetFrame(
        status = "A transposed convolution builds a bigger output by writing each input value into a kernel-sized " +
            "window of it. How many times a given output position gets written is decided by two integers, and it " +
            "can be counted before any image, any weight or any training exists.",
        readout = "O = (I − 1)·s + k − 2p",
    )

    fun coverageFrame(kernel: Int, stride: Int, note: String): NetFrame {
        val counts = DcganLab.interiorCoverage(kernel, stride, 16).take(16)
        return NetFrame(
            status = note,
            bars = listOf(
                NetBar(
                    "writes per output position · k=$kernel s=$stride",
                    counts.map { it.toFloat() },
                    if (DcganLab.isUniform(kernel, stride)) AccentA else AccentB,
                ),
            ),
            readout = "distinct counts ${DcganLab.interiorCoverage(kernel, stride, 16).toSet().sorted()} · " +
                "k mod s = ${kernel % stride}",
        )
    }

    frames += coverageFrame(
        4, 2,
        "DCGAN's choice: kernel 4, stride 2. Every interior position is written exactly twice. The coverage is flat, " +
            "so no position is systematically brighter than its neighbour.",
    )
    frames += coverageFrame(
        3, 2,
        "Kernel 3, stride 2 — one number different. The counts now alternate 1, 2, 1, 2 forever. Every other output " +
            "position receives half the contributions of its neighbour, and that is the checkerboard artefact.",
    )
    frames += coverageFrame(
        5, 2,
        "Kernel 5, stride 2 alternates 2, 3. The ratio is gentler than 1:2 but the periodicity is identical, which is " +
            "why the artefact shows up as a faint weave rather than a hard grid.",
    )
    frames += coverageFrame(
        4, 3,
        "Kernel 4, stride 3 runs 1, 1, 2 — period three instead of two. The failure is the same kind, and the " +
            "condition covering all of these cases is simply whether the stride divides the kernel.",
    )
    frames += NetFrame(
        status = "No amount of training removes any of this. The weights can scale a contribution but cannot change " +
            "how many contributions arrive, so a badly chosen pair of integers is a permanent property of the layer. " +
            "Kernel 6 with stride 2 is uniform at 3, and kernel 6 with stride 3 is uniform at 2 — divisibility is the " +
            "whole rule.",
        bars = listOf(
            NetBar("k=6 s=2", DcganLab.interiorCoverage(6, 2, 16).take(12).map { it.toFloat() }, AccentA),
            NetBar("k=6 s=3", DcganLab.interiorCoverage(6, 3, 16).take(12).map { it.toFloat() }, AccentA),
        ),
        readout = "uniform ⟺ k mod s = 0",
    )

    val layers = DcganLab.generator
    frames += NetFrame(
        status = "The parameter table is lopsided in a way the architecture diagram hides. The generator is " +
            "${DcganLab.generatorParameters} parameters taking a 100-dimensional z to 64×64×3, and the single " +
            "transposed convolution from 1024 to 512 channels at 8×8 is 66% of it. The layer that actually emits the " +
            "pixels is ${layers.last().parameters} parameters — under 0.05%.",
        bars = listOf(
            NetBar(
                "share of the generator",
                layers.map { (it.parameters.toDouble() / DcganLab.generatorParameters).toFloat() },
                OutputColor,
                listOf("dense", "1024→512", "512→256", "256→128", "128→3"),
            ),
        ),
        readout = "capacity follows channels, not pixels",
    )
    return frames
}

private fun stableDiffusionFrames(): List<NetFrame> {
    val frames = mutableListOf<NetFrame>()

    fun lg(v: Double) = log10(v).toFloat()

    frames += NetFrame(
        status = "Stable Diffusion runs the entire noising and denoising process on a compressed latent rather than " +
            "on pixels. A 512×512×3 image is ${LatentDiffusionLab.pixelElements} elements; the 64×64×4 latent is " +
            "${LatentDiffusionLab.latentElements}. That is the whole idea, and it is a factor of " +
            "${"%.0f".format(LatentDiffusionLab.elementRatio)}.",
        bars = listOf(
            NetBar(
                "log₁₀ elements",
                listOf(lg(LatentDiffusionLab.pixelElements.toDouble()), lg(LatentDiffusionLab.latentElements.toDouble())),
                ForwardColor,
                listOf("512×512×3", "64×64×4"),
            ),
        ),
        readout = "${"%.0f".format(LatentDiffusionLab.elementRatio)}× fewer elements",
    )
    frames += NetFrame(
        status = "The saving in the attention layers is far larger, because attention is quadratic in token count and " +
            "the token count is exactly what shrank: ${LatentDiffusionLab.pixelTokens} spatial tokens become " +
            "${LatentDiffusionLab.latentTokens}. Self-attention compares every token with every other, so the pair " +
            "count falls by ${"%.0f".format(LatentDiffusionLab.attentionRatio)}× — the square of the 64× in tokens.",
        bars = listOf(
            NetBar(
                "log₁₀ self-attention pairs",
                listOf(
                    lg(LatentDiffusionLab.pixelAttentionPairs.toDouble()),
                    lg(LatentDiffusionLab.latentAttentionPairs.toDouble()),
                ),
                BackwardColor,
                listOf("pixel", "latent"),
            ),
        ),
        readout = "${LatentDiffusionLab.pixelAttentionPairs} → ${LatentDiffusionLab.latentAttentionPairs}",
    )
    frames += NetFrame(
        status = "Cross-attention behaves completely differently and the two are easy to conflate. It compares image " +
            "tokens against ${LatentDiffusionLab.TEXT_TOKENS} text tokens, so it is linear in image tokens and the " +
            "same compression buys only ${"%.0f".format(LatentDiffusionLab.crossAttentionRatio)}×. The headline " +
            "number belongs to self-attention alone.",
        bars = listOf(
            NetBar(
                "log₁₀ saving",
                listOf(lg(LatentDiffusionLab.attentionRatio), lg(LatentDiffusionLab.crossAttentionRatio)),
                AccentB,
                listOf("self", "cross"),
            ),
        ),
        readout = "${"%.0f".format(LatentDiffusionLab.attentionRatio)}× against ${"%.0f".format(LatentDiffusionLab.crossAttentionRatio)}×",
    )
    frames += NetFrame(
        status = "Add a ${LatentDiffusionLab.DDIM_STEPS}-step DDIM schedule in place of DDPM's " +
            "${LatentDiffusionLab.DDPM_STEPS} and the self-attention work over a full sampling run falls by " +
            "${"%.0f".format(LatentDiffusionLab.samplingRatio)}×. That is the number that moved image generation off " +
            "a cluster and onto a consumer graphics card.",
        bars = listOf(
            NetBar(
                "log₁₀ pairs per sampling run",
                listOf(lg(LatentDiffusionLab.pixelSamplingPairs), lg(LatentDiffusionLab.latentSamplingPairs)),
                OutputColor,
                listOf("pixel · 1000 steps", "latent · 50 steps"),
            ),
        ),
        readout = "${"%.0f".format(LatentDiffusionLab.samplingRatio)}× over a full run",
    )
    frames += NetFrame(
        status = "A checkpoint is three networks and only one of them is denoised. The VAE and the text encoder run " +
            "once each, at the start and the end; the UNet — ${"%.0f".format(LatentDiffusionLab.unetShare * 100)}% of " +
            "the ${LatentDiffusionLab.totalParametersMillions}M parameters — runs once per step, fifty times, and " +
            "twice that with guidance.",
        bars = listOf(
            NetBar(
                "parameters (M)",
                LatentDiffusionLab.components.map { it.second.toFloat() },
                ForwardColor,
                listOf("VAE", "CLIP", "UNet"),
            ),
        ),
        readout = "runs once · once · ${LatentDiffusionLab.DDIM_STEPS} times",
    )
    frames += NetFrame(
        status = "The characteristic failure is set before the loop starts. Whatever the autoencoder could not fit " +
            "into 4 channels at one-eighth resolution — small faces, hands, text — is already gone from the latent " +
            "the UNet is handed, and no number of sampling steps recovers information that was discarded at encode " +
            "time.",
        readout = "f = ${LatentDiffusionLab.DOWNSAMPLE} · ${LatentDiffusionLab.LATENT_CHANNELS} channels",
    )
    return frames
}

private fun styleTransferFrames(): List<NetFrame> {
    val frames = mutableListOf<NetFrame>()
    val features = StyleTransferLab.featureMap(channels = 6, positions = 10)
    val shuffled = StyleTransferLab.shuffleColumns(features)
    val gramA = StyleTransferLab.gram(features)
    val gramB = StyleTransferLab.gram(shuffled)

    fun grid(label: String, m: Array<DoubleArray>) =
        GridView(label, m.map { row -> row.map { it.toFloat() } })

    frames += NetFrame(
        status = "Style transfer trains nothing. VGG is frozen and the image itself is the optimized variable — the " +
            "pixels are the parameters. Everything rests on how the two losses read a feature map, so start with " +
            "one: six channels across ten spatial positions.",
        grids = listOf(grid("feature map F · channels × positions", features)),
    )
    frames += NetFrame(
        status = "Now shuffle the spatial positions into a completely different arrangement. Every value is still " +
            "present and every value has moved. To any human, and to the content loss, this is a different picture.",
        grids = listOf(grid("F with its columns permuted", shuffled)),
    )
    frames += NetFrame(
        status = "The Gram matrix is G = F Fᵀ, and entry (i, j) is channel i times channel j summed over every " +
            "spatial position. It measures which features co-occur. Because the sum runs over positions, it records " +
            "nothing at all about where they occurred.",
        grids = listOf(grid("Gram of F", gramA)),
    )
    frames += NetFrame(
        status = "This is the Gram matrix of the shuffled map. It is not similar to the one before it — it is the " +
            "same matrix. A permutation reorders the terms of a sum and a sum does not care, so this is an identity " +
            "rather than an approximation that happens to hold on this input.",
        grids = listOf(grid("Gram of the permuted F", gramB)),
        readout = "style loss ${"%.1e".format(StyleTransferLab.styleLoss(features, shuffled))}",
    )
    frames += NetFrame(
        status = "Scored: the style loss between the original and its shuffle is floating-point zero, and the content " +
            "loss between the same two is ${"%.3f".format(StyleTransferLab.contentLoss(features, shuffled))}. That " +
            "gap is the entire division of labour — the content term is the only thing in the objective that knows " +
            "where anything is.",
        bars = listOf(
            NetBar(
                "loss under a spatial permutation",
                listOf(
                    StyleTransferLab.styleLoss(features, shuffled).toFloat(),
                    StyleTransferLab.contentLoss(features, shuffled).toFloat(),
                ),
                AccentB,
                listOf("style", "content"),
            ),
        ),
    )
    frames += NetFrame(
        status = "This is what the algorithm means by \"style\": co-occurrence statistics with the geometry deleted. " +
            "It transfers brushwork and palette faithfully because those really are position-independent, and it " +
            "cannot transfer composition at all. At VGG-19's conv4_1 the Gram is only a " +
            "${"%.2f".format(StyleTransferLab.compression)}× reduction in size — the useful property was never " +
            "compression, it was invariance.",
        readout = "${StyleTransferLab.featureValues} values → ${StyleTransferLab.gramUniqueEntries} entries",
    )
    return frames
}

private fun deepFakeFrames(): List<NetFrame> {
    val frames = mutableListOf<NetFrame>()
    val sweep = DeepFakeLab.sweepResults
    val aligned = sweep.first()

    frames += NetFrame(
        status = "One shared encoder and two identity-specific decoders. Both identities' faces go through the same " +
            "trunk; each decoder is trained only on its own person. The swap is what happens when you break that " +
            "pairing at inference and send A's code to B's decoder.",
        layers = listOf(
            NetLayer("face A", List(4) { NetNode(0f, NodeMood.FORWARD) }),
            NetLayer("shared encoder", List(DeepFakeLab.LATENT) { NetNode(0f, NodeMood.FORWARD) }),
            NetLayer("decoder B", List(4) { NetNode(0f, NodeMood.OUTPUT) }),
        ),
        readout = "D_B(E(x_A))",
    )
    frames += NetFrame(
        status = "Nothing in the loss says the code should carry expression and not identity. That separation is a " +
            "hoped-for consequence of the encoder being shared and the decoders not being, so the first thing to " +
            "establish is a bar the swap has to clear: always emitting B's average face, which ignores the input " +
            "entirely and scores ${"%.3f".format(aligned.meanFaceBaselineRmse)}.",
        readout = "baseline ${"%.3f".format(aligned.meanFaceBaselineRmse)}",
    )
    frames += NetFrame(
        status = "Give each identity its own encoder and the swap scores " +
            "${"%.3f".format(aligned.independentEncoderRmse)} — worse than the baseline. Each encoder orders and " +
            "scales its directions by its own identity's variance, so the same expression lands on different numbers " +
            "and the far decoder reads a different expression. Sharing the encoder scores " +
            "${"%.3f".format(aligned.sharedEncoderRmse)}.",
        bars = listOf(
            NetBar(
                "swap error",
                listOf(
                    aligned.sharedEncoderRmse.toFloat(),
                    aligned.independentEncoderRmse.toFloat(),
                    aligned.meanFaceBaselineRmse.toFloat(),
                ),
                OutputColor,
                listOf("shared", "independent", "mean face"),
            ),
        ),
        readout = "independent encoders lose to doing nothing",
    )
    frames += NetFrame(
        status = "And this is not a reconstruction problem. Both arrangements rebuild their own identity's faces " +
            "essentially exactly — own-domain error is ${"%.3f".format(DeepFakeLab.ownDomainRmse())}. A model that " +
            "reconstructs perfectly can still swap worse than a constant, so reconstruction quality tells you nothing " +
            "about whether the swap will work.",
        readout = "own-domain rebuild ≈ 0 either way",
    )
    val plotted = sweep.filter { it.degrees <= 60.0 }
    frames += NetFrame(
        status = "Sharing is necessary and not sufficient. A shared code only means the same thing to both decoders " +
            "if the two identities' expression manifolds sit similarly in face space. Rotate them apart and the swap " +
            "degrades continuously: ${plotted.joinToString(" · ") { "${it.degrees.toInt()}° ${"%.3f".format(it.sharedEncoderRmse)}" }}.",
        plot = autoPlot(
            "swap error against manifold angle",
            listOf(
                Curve("shared encoder", plotted.map { it.degrees.toFloat() to it.sharedEncoderRmse.toFloat() }, ForwardColor),
                Curve("mean-face baseline", plotted.map { it.degrees.toFloat() to it.meanFaceBaselineRmse.toFloat() }, NeutralColor),
            ),
            0f..60f,
        ),
        readout = "crosses the baseline past 60°",
    )
    frames += NetFrame(
        status = "At 90°, where the manifolds are orthogonal, the decoder's implied inverse becomes singular and the " +
            "output diverges outright. There is no training fix anywhere on that curve — the information the far " +
            "decoder needs is simply not in the code. This is the measurable form of a thing every practitioner " +
            "reports: swaps work between people who already look and move alike.",
        readout = "90° · singular · no schedule repairs it",
    )
    return frames
}

// ── D6 · Fine-tuning, PEFT, LoRA, quantization, DPO, long context ────────────
// Six labs on numbers computed in FineTuneMath.kt. Three of them are accounting arguments and read
// as bar charts; three are experiments and read as curves over a swept parameter.

private fun fineTuningFullFrames(): List<NetFrame> {
    val frames = mutableListOf<NetFrame>()
    val counts = FineTuneLab.exampleCounts
    val small = FineTuneLab.regimes(8)
    val large = FineTuneLab.regimes(256)

    frames += NetFrame(
        status = "Three ways to use a downstream dataset, trained for real on the same examples. Pretraining here is " +
            "multi-task — one body, ${FineTuneLab.upstreamTasks} heads — because that is what forces the body to keep " +
            "a representation worth reusing. Feature extraction trains ${FineTuneLab.headParams} weights; full " +
            "fine-tuning trains ${FineTuneLab.fullParams}.",
        layers = listOf(
            NetLayer("input", List(4) { NetNode(0f, NodeMood.FORWARD) }),
            NetLayer("pretrained body", List(FineTuneLab.hidden) { NetNode(0f, NodeMood.IDLE) }),
            NetLayer("new head", List(1) { NetNode(0f, NodeMood.OUTPUT) }),
        ),
        readout = "${FineTuneLab.headParams} trainable vs ${FineTuneLab.fullParams}",
    )

    frames += NetFrame(
        status = "At 8 examples the frozen body wins and it is not close: ${"%.3f".format(small[1].downstreamLoss)} " +
            "against ${"%.3f".format(small[2].downstreamLoss)} for full fine-tuning, with training from scratch a " +
            "distant ${"%.3f".format(small[0].downstreamLoss)}. ${FineTuneLab.fullParams} parameters and 8 examples " +
            "is not a fit, it is memorisation — and the pretrained body is the only thing standing between the model " +
            "and that.",
        bars = listOf(
            NetBar(
                "test loss at 8 examples",
                small.map { it.downstreamLoss.toFloat() },
                BackwardColor,
                listOf("scratch", "head only", "full"),
            ),
        ),
        readout = "frozen features win by ${"%.1f".format(small[2].downstreamLoss / small[1].downstreamLoss)}×",
    )

    frames += NetFrame(
        status = "The crossover is at ${FineTuneLab.crossoverExamples()} examples, and it is a real crossing rather " +
            "than a slow convergence: past it, full fine-tuning pulls away and the frozen body flattens out. At 256 " +
            "examples full fine-tuning reaches ${"%.4f".format(large[2].downstreamLoss)} while the frozen body is " +
            "stuck at ${"%.4f".format(large[1].downstreamLoss)} — ${"%.0f".format(large[1].downstreamLoss / large[2].downstreamLoss)}× " +
            "worse, and no amount of extra data moves it.",
        plot = autoPlot(
            "test loss against downstream examples",
            listOf(
                Curve("head only", counts.map { it.toFloat() to FineTuneLab.regimes(it)[1].downstreamLoss.toFloat() }, ForwardColor),
                Curve("full fine-tune", counts.map { it.toFloat() to FineTuneLab.regimes(it)[2].downstreamLoss.toFloat() }, BackwardColor),
                Curve("from scratch", counts.map { it.toFloat() to FineTuneLab.regimes(it)[0].downstreamLoss.toFloat() }, NeutralColor),
            ),
            4f..256f,
        ),
        readout = "crossover at ${FineTuneLab.crossoverExamples()} examples",
    )

    frames += NetFrame(
        status = "The flat line is the point. A frozen body has a ceiling, and it is set by whether the downstream " +
            "answer is a linear read-out of features chosen for other tasks. Training from scratch reaches that same " +
            "ceiling at 256 examples (${"%.4f".format(large[0].downstreamLoss)} against " +
            "${"%.4f".format(large[1].downstreamLoss)}) — which is a useful way to know when to unfreeze: when " +
            "scratch catches your frozen model, the features were the constraint.",
        bars = listOf(
            NetBar(
                "test loss at 256 examples",
                large.map { it.downstreamLoss.toFloat() },
                OutputColor,
                listOf("scratch", "head only", "full"),
            ),
        ),
        readout = "scratch has caught the frozen model",
    )

    frames += NetFrame(
        status = "And full fine-tuning charges for it in a currency the downstream metric never shows. Score the " +
            "original ${FineTuneLab.upstreamTasks} pretraining tasks with their own heads after the body has moved: " +
            "loss goes from ${"%.4f".format(FineTuneLab.pretrainedUpstreamLoss)} to " +
            "${"%.4f".format(FineTuneLab.regimes(256)[2].upstreamLoss!!)} — " +
            "${"%.0f".format(FineTuneLab.forgettingRatio(256))}× worse. Nothing about the downstream numbers hints " +
            "at this, which is why catastrophic forgetting keeps being discovered in production.",
        plot = autoPlot(
            "upstream loss after fine-tuning, ÷ pretrained",
            listOf(
                Curve("full fine-tune", counts.map { it.toFloat() to FineTuneLab.forgettingRatio(it).toFloat() }, BackwardColor),
                Curve("head only", counts.map { it.toFloat() to 1f }, ForwardColor),
            ),
            4f..256f,
        ),
        readout = "${"%.0f".format(FineTuneLab.forgettingRatio(256))}× worse upstream, invisible downstream",
    )

    frames += NetFrame(
        status = "The other bill is memory, and it is not the weights. Mixed-precision Adam holds " +
            "${FineTuneLab.trainableBytesPerParam} bytes per *trainable* parameter — fp16 weight and gradient, an " +
            "fp32 master copy, and two moment buffers — against ${FineTuneLab.frozenBytesPerParam} for a frozen one. " +
            "A 7B model fully fine-tuned needs ${bytesToGb(FineTuneLab.trainingBytes(7_000_000_000, 7_000_000_000))} GB " +
            "of state; with 20M trainable it needs ${bytesToGb(FineTuneLab.trainingBytes(7_000_000_000, 20_000_000))} GB. " +
            "That gap is the whole reason PEFT exists.",
        bars = listOf(
            NetBar(
                "training state, GB (7B model)",
                listOf(
                    bytesToGb(FineTuneLab.trainingBytes(7_000_000_000, 7_000_000_000)).toFloat(),
                    bytesToGb(FineTuneLab.trainingBytes(7_000_000_000, 20_000_000)).toFloat(),
                ),
                OutputColor,
                listOf("all trainable", "20M trainable"),
            ),
        ),
        readout = "${FineTuneLab.trainableBytesPerParam} bytes per trainable parameter",
    )
    return frames
}

private fun dpoFrames(): List<NetFrame> {
    val frames = mutableListOf<NetFrame>()
    val betas = listOf(0.1, 0.2, 0.5, 1.0, 2.0, 5.0)
    val trajectory = DpoLab.dpoTrajectory(0.1)
    val cycle = DpoLab.preferenceCycles().first()

    frames += NetFrame(
        status = "One prompt, ${DpoLab.responses.size} candidate responses, and a reference policy whose mode is " +
            "\"${DpoLab.responses[DpoLab.reference.indices.maxBy { DpoLab.reference[it] }]}\" at " +
            "${"%.0f".format(DpoLab.reference.max() * 100)}% — the base model's failure mode, and the reason there is " +
            "anything to align. Its expected quality is ${"%.3f".format(DpoLab.expectedQuality(DpoLab.reference))} " +
            "against a best possible ${"%.1f".format(DpoLab.bestPossibleQuality)}.",
        bars = listOf(
            NetBar("π_ref", DpoLab.reference.map { it.toFloat() }, NeutralColor, DpoLab.responses.map { it.take(9) }),
            NetBar("true quality", DpoLab.quality.map { it.toFloat() }, AccentA, DpoLab.responses.map { it.take(9) }),
        ),
        readout = "reference quality ${"%.3f".format(DpoLab.expectedQuality(DpoLab.reference))}",
    )

    frames += NetFrame(
        status = "${DpoLab.pairs.size} pairs, ${DpoLab.annotatorsPerPair} annotators each, majority vote. " +
            "${DpoLab.mislabelledPairs().size} of the ${DpoLab.pairs.size} come back contradicting the ground truth — " +
            "not a lot, and enough. Those two flips create a preference cycle: " +
            "${DpoLab.responses[cycle.first]} > ${DpoLab.responses[cycle.second]} > ${DpoLab.responses[cycle.third]} > " +
            "${DpoLab.responses[cycle.first]}.",
        bars = listOf(
            NetBar(
                "fitted reward",
                DpoLab.rewardModel.map { it.toFloat() },
                BackwardColor,
                DpoLab.responses.map { it.take(9) },
            ),
        ),
        readout = "${DpoLab.mislabelledPairs().size} flipped labels · 1 cycle",
    )

    frames += NetFrame(
        status = "A Bradley-Terry reward assigns one number per response, so no setting of its parameters can " +
            "represent a cycle — it is forced to give all three the same reward " +
            "(${"%.3f".format(DpoLab.rewardModel[cycle.first])}), and it caps out at " +
            "${"%.0f".format(DpoLab.rewardAccuracy() * 100)}% accuracy on its own training preferences. More data does " +
            "not help. More parameters do not help. The cycle is in the labels.",
        bars = listOf(
            NetBar(
                "fitted reward",
                DpoLab.rewardModel.map { it.toFloat() },
                BackwardColor,
                DpoLab.responses.map { it.take(9) },
            ),
            NetBar("true quality", DpoLab.quality.map { it.toFloat() }, AccentA, DpoLab.responses.map { it.take(9) }),
        ),
        readout = "reward accuracy caps at ${"%.0f".format(DpoLab.rewardAccuracy() * 100)}%",
    )

    frames += NetFrame(
        status = "RLHF's second stage maximises E[r̂] − β·KL(π‖π_ref), which on a tabular policy has the closed form " +
            "π ∝ π_ref·exp(r̂/β). β is the whole dial: at β = 5 the policy barely moves (KL " +
            "${"%.3f".format(DpoLab.kl(DpoLab.rlhfPolicy(5.0)))}), at β = 0.1 it is pinned to the reward's argmax " +
            "(KL ${"%.3f".format(DpoLab.kl(DpoLab.rlhfPolicy(0.1)))}).",
        plot = autoPlot(
            "quality and divergence against β",
            listOf(
                Curve("expected quality", betas.map { it.toFloat() to DpoLab.expectedQuality(DpoLab.rlhfPolicy(it)).toFloat() }, AccentA),
                Curve("KL from reference", betas.map { it.toFloat() to DpoLab.kl(DpoLab.rlhfPolicy(it)).toFloat() }, BackwardColor),
            ),
            0.1f..5f,
        ),
        readout = "β trades divergence against reward",
    )

    frames += NetFrame(
        status = "DPO throws the reward model away and descends the pairwise logistic loss on the policy itself, " +
            "using β·log(π/π_ref) as an implicit reward. Over ${trajectory.last().step} steps the loss falls from " +
            "${"%.4f".format(trajectory.first().loss)} to ${"%.4f".format(trajectory.last().loss)} while the policy " +
            "moves KL ${"%.3f".format(trajectory.last().kl)} from the reference.",
        plot = autoPlot(
            "DPO trajectory",
            listOf(
                Curve("expected quality", trajectory.map { it.step.toFloat() to it.quality.toFloat() }, AccentA),
                Curve("KL from reference", trajectory.map { it.step.toFloat() to it.kl.toFloat() }, BackwardColor),
                Curve("DPO loss", trajectory.map { it.step.toFloat() to it.loss.toFloat() }, ForwardColor),
            ),
            0f..trajectory.last().step.toFloat(),
        ),
        readout = "one stage instead of two",
    )

    val (matchedBeta, gap) = DpoLab.matchedPolicyGap(0.1, trajectory.last().step)
    frames += NetFrame(
        status = "And it lands in the same place. Find the β whose closed-form RLHF policy sits at the DPO run's own " +
            "divergence — β = ${"%.3f".format(matchedBeta)} — and the two policies agree to " +
            "${"%.0e".format(gap.coerceAtLeast(1e-16))} on every response. That is the DPO theorem, and it is why the " +
            "reward model was never load-bearing: it was a parameterisation of the policy all along.",
        bars = listOf(
            NetBar("DPO policy", trajectory.last().policy.map { it.toFloat() }, ForwardColor, DpoLab.responses.map { it.take(9) }),
            NetBar("RLHF closed form", DpoLab.rlhfPolicy(matchedBeta).map { it.toFloat() }, BackwardColor, DpoLab.responses.map { it.take(9) }),
        ),
        readout = "identical at matched KL",
    )

    frames += NetFrame(
        status = "Which makes the interesting number the one neither method changes. Both converge to quality " +
            "${"%.3f".format(DpoLab.alignmentCeiling())} while the best response is worth " +
            "${"%.1f".format(DpoLab.bestPossibleQuality)} — " +
            "${"%.0f".format((1 - DpoLab.alignmentCeiling() / DpoLab.bestPossibleQuality) * 100)}% of the available " +
            "quality left on the table by two mislabelled pairs out of ${DpoLab.pairs.size}. No β, no optimizer and " +
            "no choice between RLHF and DPO recovers it. The ceiling is the annotation, not the algorithm.",
        bars = listOf(
            NetBar(
                "expected quality",
                listOf(
                    DpoLab.expectedQuality(DpoLab.reference).toFloat(),
                    DpoLab.alignmentCeiling().toFloat(),
                    DpoLab.bestPossibleQuality.toFloat(),
                ),
                OutputColor,
                listOf("reference", "aligned", "best possible"),
            ),
        ),
        readout = "${"%.0f".format((1 - DpoLab.alignmentCeiling() / DpoLab.bestPossibleQuality) * 100)}% lost to the labels",
    )
    return frames
}

private fun peftFrames(): List<NetFrame> {
    val frames = mutableListOf<NetFrame>()
    val methods = PeftLab.methods
    val full = methods.first()
    val lora = methods.first { it.name.startsWith("LoRA") }

    frames += NetFrame(
        status = "One concrete model — BERT-base's config, ${PeftLab.layers} layers of width ${PeftLab.dModel}, " +
            "${"%.1f".format(PeftLab.totalParams / 1_000_000.0)}M parameters — and six ways to tune it. Every count " +
            "below is arithmetic over that config rather than a figure copied from a paper.",
        layers = listOf(
            NetLayer("frozen", List(6) { NetNode(0f, NodeMood.IDLE) }),
            NetLayer("adapter", List(2) { NetNode(0f, NodeMood.BACKWARD) }),
            NetLayer("frozen", List(6) { NetNode(0f, NodeMood.IDLE) }),
        ),
        readout = "${"%.1f".format(PeftLab.totalParams / 1_000_000.0)}M parameters",
    )

    frames += NetFrame(
        status = "The trainable-parameter headline, which is what every PEFT paper leads with. IA³ trains " +
            "${"%.3f".format(methods.last().sharePercent(PeftLab.totalParams))}% of the model; LoRA at rank " +
            "${PeftLab.loraRank} on the query and value projections trains " +
            "${"%.3f".format(lora.sharePercent(PeftLab.totalParams))}%; adapters at bottleneck " +
            "${PeftLab.adapterBottleneck} train ${"%.2f".format(methods[3].sharePercent(PeftLab.totalParams))}%.",
        bars = listOf(
            NetBar(
                "trainable share, % (log₁₀)",
                methods.map { log10(it.sharePercent(PeftLab.totalParams).toFloat()) },
                ForwardColor,
                listOf("full", "BitFit", "LoRA", "adapter", "prefix", "IA³"),
            ),
        ),
        readout = "LoRA: ${"%.3f".format(lora.sharePercent(PeftLab.totalParams))}% trainable",
    )

    frames += NetFrame(
        status = "Optimizer state follows that headline exactly, because it is charged per trainable parameter: " +
            "${bytesToGb(PeftLab.stateBytes(full))} GB for full fine-tuning against " +
            "${bytesToGb(PeftLab.stateBytes(lora))} GB for LoRA. If parameter state were the whole bill, the headline " +
            "would be the answer.",
        bars = listOf(
            NetBar(
                "weights + gradients + Adam, GB",
                methods.map { bytesToGb(PeftLab.stateBytes(it)).toFloat() },
                BackwardColor,
                listOf("full", "BitFit", "LoRA", "adapter", "prefix", "IA³"),
            ),
        ),
        readout = "${bytesToGb(PeftLab.stateBytes(full))} GB → ${bytesToGb(PeftLab.stateBytes(lora))} GB",
    )

    frames += NetFrame(
        status = "It is not the whole bill. Activations — everything the backward pass has to keep from the forward " +
            "pass — are ${bytesToGb(PeftLab.activationBytes)} GB at batch ${PeftLab.batch} and sequence " +
            "${PeftLab.seqLen}, and freezing a weight does not remove them: the gradient still has to flow *through* " +
            "the frozen layers to reach the adapter beneath them. Add that term and LoRA's total goes from " +
            "${bytesToGb(PeftLab.totalTrainingBytes(full))} GB to ${bytesToGb(PeftLab.totalTrainingBytes(lora))} GB — " +
            "a ${"%.1f".format(PeftLab.totalTrainingBytes(full).toDouble() / PeftLab.totalTrainingBytes(lora))}× saving, " +
            "not a ${"%.0f".format(full.trainable.toDouble() / lora.trainable)}× one.",
        bars = listOf(
            NetBar(
                "total training memory, GB",
                methods.map { bytesToGb(PeftLab.totalTrainingBytes(it)).toFloat() },
                OutputColor,
                listOf("full", "BitFit", "LoRA", "adapter", "prefix", "IA³"),
            ),
        ),
        readout = "${"%.1f".format(PeftLab.totalTrainingBytes(full).toDouble() / PeftLab.totalTrainingBytes(lora))}× less memory",
    )

    frames += NetFrame(
        status = "Priced as a ratio of ratios, the headline overstates the memory saving by " +
            "${"%.0f".format(PeftLab.headlineOverstatement(lora))}× for LoRA and " +
            "${"%.0f".format(PeftLab.headlineOverstatement(methods.last()))}× for IA³ — and the more extreme the " +
            "method, the worse the overstatement, because every method is converging on the same activation floor. " +
            "That floor is why gradient checkpointing and a smaller batch are still the levers that matter.",
        bars = listOf(
            NetBar(
                "headline ÷ actual saving",
                methods.map { PeftLab.headlineOverstatement(it).toFloat() },
                BackwardColor,
                listOf("full", "BitFit", "LoRA", "adapter", "prefix", "IA³"),
            ),
        ),
        readout = "the activation floor is ${bytesToGb(PeftLab.activationBytes)} GB",
    )

    frames += NetFrame(
        status = "What PEFT does buy unambiguously is the artefact. A fully fine-tuned BERT is " +
            "${"%.0f".format(PeftLab.totalParams * 2 / 1_048_576.0)} MB per task; a LoRA adapter is " +
            "${"%.1f".format(lora.trainable * 2 / 1_048_576.0)} MB, and it merges back into the base at inference so " +
            "it costs no extra latency. Fifty tasks is fifty adapters and one copy of the weights.",
        bars = listOf(
            NetBar(
                "shipped artefact, MB",
                listOf((PeftLab.totalParams * 2 / 1_048_576.0).toFloat(), (lora.trainable * 2 / 1_048_576.0).toFloat()),
                AccentA,
                listOf("full copy", "LoRA adapter"),
            ),
        ),
        readout = "${"%.0f".format(full.trainable.toDouble() / lora.trainable)}× smaller to store and serve",
    )
    return frames
}

private fun loraFrames(): List<NetFrame> {
    val frames = mutableListOf<NetFrame>()
    val sweep = LoraLab.rankSweep
    val spectrum = LoraLab.updateSpectrum

    frames += NetFrame(
        status = "LoRA's claim is that the *update* is low-rank, not the model. So: fine-tune a ${LoraLab.dim}×" +
            "${LoraLab.dim} map for real, take ΔW = W − W₀, and look at it. Its singular values fall off a cliff " +
            "after three (${spectrum.take(5).joinToString(", ") { "%.2f".format(it) }}), giving an effective rank of " +
            "${"%.1f".format(LoraLab.effectiveRank(spectrum))} — against " +
            "${"%.1f".format(LoraLab.effectiveRank(LoraLab.baseSpectrum))} for the pretrained weights themselves.",
        bars = listOf(
            NetBar("ΔW singular values", spectrum.take(10).map { it.toFloat() }, BackwardColor),
            NetBar("W₀ singular values", LoraLab.baseSpectrum.take(10).map { it.toFloat() }, NeutralColor),
        ),
        readout = "effective rank ${"%.1f".format(LoraLab.effectiveRank(spectrum))} vs ${"%.1f".format(LoraLab.effectiveRank(LoraLab.baseSpectrum))}",
    )

    frames += NetFrame(
        status = "Three directions carry ${"%.1f".format(LoraLab.energyAtRank(3) * 100)}% of the update's energy. " +
            "That is the whole hypothesis, and it is a statement about the fine-tuning *task* — a narrow adaptation " +
            "moves the weights along few directions — rather than about transformers.",
        plot = autoPlot(
            "energy captured by rank",
            listOf(Curve("cumulative energy", (1..12).map { it.toFloat() to LoraLab.energyAtRank(it).toFloat() }, ForwardColor)),
            1f..12f,
        ),
        readout = "${"%.1f".format(LoraLab.energyAtRank(3) * 100)}% in 3 of ${LoraLab.dim} directions",
    )

    frames += NetFrame(
        status = "Now train the adapters rather than reading the spectrum: W₀ + (α/r)·BA with B starting at zero, so " +
            "the adapter is a no-op at step 0. Rank ${sweep[2].rank} closes " +
            "${"%.0f".format(LoraLab.gapClosed(sweep[2]) * 100)}% of the gap to full fine-tuning with " +
            "${sweep[2].trainable} of ${LoraLab.dim * LoraLab.dim} parameters; rank ${sweep[3].rank} closes " +
            "${"%.0f".format(LoraLab.gapClosed(sweep[3]) * 100)}%.",
        bars = listOf(
            NetBar(
                "gap to full fine-tuning closed",
                sweep.map { LoraLab.gapClosed(it).toFloat() },
                AccentA,
                sweep.map { "r=${it.rank}" },
            ),
        ),
        readout = "rank ${sweep[2].rank}: ${"%.0f".format(LoraLab.gapClosed(sweep[2]) * 100)}% for ${"%.0f".format(100.0 * sweep[2].trainable / (LoraLab.dim * LoraLab.dim))}% of the weights",
    )

    frames += NetFrame(
        status = "And the trained losses sit just above what the spectrum says they must. Eckart-Young puts a floor " +
            "under any rank-r adapter — the tail singular values it cannot represent — and the sweep tracks that floor " +
            "from above at every rank (${sweep.take(4).joinToString(", ") { "${"%.3f".format(it.loss)} vs ${"%.3f".format(LoraLab.predictedLossAtRank(it.rank))}" }}). " +
            "The low-rank story is not a heuristic; it is a theorem with a learning rate attached.",
        plot = autoPlot(
            "trained loss against the Eckart-Young floor",
            listOf(
                Curve("trained LoRA", sweep.map { it.rank.toFloat() to it.loss.toFloat() }, ForwardColor),
                Curve("spectrum floor", sweep.map { it.rank.toFloat() to LoraLab.predictedLossAtRank(it.rank).toFloat() }, NeutralColor),
            ),
            1f..LoraLab.dim.toFloat(),
        ),
        readout = "trained ≥ floor at every rank",
    )

    frames += NetFrame(
        status = "α is not cosmetic. The update passes through the α/r scale going in and coming back, so the " +
            "effective step size on BA grows as (α/r)² — at r = 1 that is 256× the step at r = 16. Left at a single " +
            "learning rate, every rank below 16 in this sweep diverged to NaN. This is the concrete content of the " +
            "paper's remark that tuning α is roughly like tuning the learning rate.",
        bars = listOf(
            NetBar(
                "diverges at a fixed learning rate",
                LoraLab.ranks.map { if (LoraLab.divergesAtFixedRate(it)) 1f else 0f },
                BackwardColor,
                LoraLab.ranks.map { "r=$it" },
            ),
        ),
        readout = "effective step scales as (α/r)²",
    )

    frames += NetFrame(
        status = "QLoRA adds the other half: freeze the base in 4-bit and keep the adapters in full precision. " +
            "Quantizing W₀ alone costs ${"%.4f".format(LoraLab.quantizedBaseLoss - LoraLab.baseLoss)} of loss. Train " +
            "rank-${LoraLab.ranks[2]} adapters on top of the quantized base and the result is " +
            "${"%.4f".format(LoraLab.qloraFit(LoraLab.ranks[2]).loss)} against " +
            "${"%.4f".format(sweep[2].loss)} for adapters on the full-precision base — " +
            "${"%.0f".format((1 - (LoraLab.qloraFit(LoraLab.ranks[2]).loss - sweep[2].loss) / (LoraLab.quantizedBaseLoss - LoraLab.baseLoss)) * 100)}% " +
            "of the quantization damage absorbed by the adapters.",
        bars = listOf(
            NetBar(
                "test loss",
                listOf(
                    LoraLab.baseLoss.toFloat(),
                    LoraLab.quantizedBaseLoss.toFloat(),
                    LoraLab.qloraFit(LoraLab.ranks[2]).loss.toFloat(),
                    sweep[2].loss.toFloat(),
                ),
                OutputColor,
                listOf("W₀", "NF4 W₀", "QLoRA", "LoRA"),
            ),
        ),
        readout = "the adapters absorb the quantization error",
    )
    return frames
}

private fun quantizationFrames(): List<NetFrame> {
    val frames = mutableListOf<NetFrame>()
    val errors = QuantLab.errors()

    fun histogram(values: DoubleArray, bins: Int = 24): List<Float> {
        val lo = -4.0
        val hi = 4.0
        val counts = IntArray(bins)
        values.forEach { v ->
            val b = (((v - lo) / (hi - lo)) * bins).toInt().coerceIn(0, bins - 1)
            counts[b]++
        }
        return counts.map { it.toFloat() / values.size }
    }

    frames += NetFrame(
        status = "${QuantLab.weightCount} weights, normally distributed, which is what trained weights actually look " +
            "like. Quantizing means replacing each one with the nearest of a small set of levels and storing the " +
            "index instead of the number. Everything below is measured against this tensor.",
        bars = listOf(NetBar("weight histogram", histogram(QuantLab.weights), NeutralColor)),
        readout = "${QuantLab.weightCount} weights, fp32",
    )

    frames += NetFrame(
        status = "int8 with a single absmax scale is nearly lossless — MSE ${"%.6f".format(errors[0].second)}, " +
            "${"%.1f".format(QuantLab.snrDb(QuantLab.weights, QuantLab.schemes[0].quantize(QuantLab.weights)))} dB. " +
            "int4 with the same scheme is not: ${"%.4f".format(errors[1].second)}, which is " +
            "${"%.0f".format(errors[1].second / errors[0].second)}× worse. Sixteen levels over the whole range is " +
            "simply not many, and most of them land where almost no weights are.",
        bars = listOf(
            NetBar(
                "quantization MSE (log₁₀)",
                errors.map { log10(it.second.toFloat()) },
                BackwardColor,
                listOf("int8", "int4", "NF4", "int4/64", "NF4/64"),
            ),
        ),
        readout = "int4 per-tensor: ${"%.0f".format(errors[1].second / errors[0].second)}× the int8 error",
    )

    frames += NetFrame(
        status = "NF4 spends the same 4 bits differently: its 16 levels are the quantiles of a normal distribution, " +
            "so they crowd where the weights are. On the same tensor that is MSE ${"%.4f".format(errors[2].second)} " +
            "against int4's ${"%.4f".format(errors[1].second)} — " +
            "${"%.0f".format((1 - errors[2].second / errors[1].second) * 100)}% less error for free, because the " +
            "levels are a constant table rather than anything learned.",
        bars = listOf(
            NetBar("NF4 levels", QuantLab.nf4Levels.map { it.toFloat() }, AccentA),
            NetBar("uniform int4 levels", QuantLab.int4Levels.map { it.toFloat() }, NeutralColor),
        ),
        readout = "${"%.0f".format((1 - errors[2].second / errors[1].second) * 100)}% less error at the same width",
    )

    frames += NetFrame(
        status = "Then one weight goes to 20σ. A single absmax scale is set by the largest magnitude in the tensor, " +
            "so that one outlier stretches the whole grid and every other weight is quantized more coarsely — the " +
            "error on the ${QuantLab.weightCount - 1} innocent weights rises " +
            "${"%.0f".format(QuantLab.outlierPenalty(QuantLab.schemes[0]))}× for int8 and " +
            "${"%.0f".format(QuantLab.outlierPenalty(QuantLab.schemes[1]))}× for int4. This is why LLM.int8() exists, " +
            "and it is an activation problem before it is a weight problem.",
        bars = listOf(
            NetBar(
                "error on the other weights, ÷ clean",
                QuantLab.schemes.map { QuantLab.outlierPenalty(it).toFloat() },
                BackwardColor,
                listOf("int8", "int4", "NF4", "int4/64", "NF4/64"),
            ),
        ),
        readout = "one weight in ${QuantLab.weightCount} costs ${"%.0f".format(QuantLab.outlierPenalty(QuantLab.schemes[0]))}×",
    )

    frames += NetFrame(
        status = "Blockwise scaling fixes it structurally rather than by tuning. One absmax per 64 weights confines " +
            "an outlier to its own block: the penalty falls from " +
            "${"%.0f".format(QuantLab.outlierPenalty(QuantLab.schemes[1]))}× to " +
            "${"%.1f".format(QuantLab.outlierPenalty(QuantLab.schemes[3]))}×, and the clean-tensor error improves too " +
            "(${"%.4f".format(errors[3].second)} against ${"%.4f".format(errors[1].second)}). The cost is one extra " +
            "scale per block — ${"%.2f".format(2.0 * 8 / 64)} bits per weight.",
        bars = listOf(
            NetBar(
                "outlier penalty, per-tensor vs blockwise",
                listOf(
                    QuantLab.outlierPenalty(QuantLab.schemes[1]).toFloat(),
                    QuantLab.outlierPenalty(QuantLab.schemes[3]).toFloat(),
                    QuantLab.outlierPenalty(QuantLab.schemes[2]).toFloat(),
                    QuantLab.outlierPenalty(QuantLab.schemes[4]).toFloat(),
                ),
                OutputColor,
                listOf("int4", "int4/64", "NF4", "NF4/64"),
            ),
        ),
        readout = "blockwise: ${"%.1f".format(QuantLab.outlierPenalty(QuantLab.schemes[4]))}× instead of ${"%.0f".format(QuantLab.outlierPenalty(QuantLab.schemes[2]))}×",
    )

    frames += NetFrame(
        status = "Weight error is a proxy; what matters is what the layer *outputs*. On a real trained matrix, " +
            "quantizing to int8 moves the outputs by ${"%.6f".format(QuantLab.outputDrift(QuantLab.int8Levels, null))} " +
            "and to 4-bit by ${"%.4f".format(QuantLab.outputDrift(QuantLab.int4Levels, 16))}. Worth measuring rather " +
            "than inferring: an earlier version of this lab scored quantization by task loss and found NF4 \"better " +
            "than fp16\", because it was watching an under-fitted model being nudged at random.",
        bars = listOf(
            NetBar(
                "output drift (log₁₀)",
                listOf(
                    log10(QuantLab.outputDrift(QuantLab.int8Levels, null).toFloat()),
                    log10(QuantLab.outputDrift(QuantLab.int4Levels, null).toFloat()),
                    log10(QuantLab.outputDrift(QuantLab.int4Levels, 16).toFloat()),
                ),
                ForwardColor,
                listOf("int8", "int4", "int4/16"),
            ),
        ),
        readout = "measure the outputs, not the weights",
    )

    frames += NetFrame(
        status = "The reason anyone accepts any of this: a 7B model is " +
            "${bytesToGb(QuantLab.bytesForParams(7_000_000_000, 16))} GB in fp16, " +
            "${bytesToGb(QuantLab.bytesForParams(7_000_000_000, 8))} GB in int8 and " +
            "${bytesToGb(QuantLab.bytesForParams(7_000_000_000, 4))} GB in NF4 — the difference between needing a " +
            "data-centre card and fitting on a laptop.",
        bars = listOf(
            NetBar(
                "7B weights, GB",
                listOf(16, 8, 4).map { bytesToGb(QuantLab.bytesForParams(7_000_000_000, it)).toFloat() },
                AccentA,
                listOf("fp16", "int8", "NF4"),
            ),
        ),
        readout = "${bytesToGb(QuantLab.bytesForParams(7_000_000_000, 16))} GB → ${bytesToGb(QuantLab.bytesForParams(7_000_000_000, 4))} GB",
    )
    return frames
}

private fun longContextFrames(): List<NetFrame> {
    val frames = mutableListOf<NetFrame>()
    val lengths = LongContextLab.lengths
    val windows = LongContextLab.advertisedVersusEffective(1_048_576)

    frames += NetFrame(
        status = "A context window has three prices and the advertised number mentions none of them. Config here is " +
            "LLaMA-2-7B's — ${LongContextLab.layers} layers, ${LongContextLab.heads} heads, head dimension " +
            "${LongContextLab.headDim} — so every figure is checkable against a real model.",
        layers = listOf(
            NetLayer("tokens", List(8) { NetNode(0f, NodeMood.FORWARD) }),
            NetLayer("KV cache", List(8) { NetNode(0f, NodeMood.BACKWARD) }),
            NetLayer("output", List(1) { NetNode(0f, NodeMood.OUTPUT) }),
        ),
        readout = "7B parameters, ${bytesToGb(LongContextLab.params * 2)} GB of weights",
    )

    frames += NetFrame(
        status = "Price one is the KV cache, and it is linear in the length and paid at every decode step. At 4k " +
            "tokens it is ${bytesToGb(LongContextLab.kvCacheBytes(4_096))} GB — already a sixth of the weights. At 1M " +
            "it is ${bytesToGb(LongContextLab.kvCacheBytes(1_048_576))} GB, which is " +
            "${"%.0f".format(LongContextLab.kvCacheBytes(1_048_576).toDouble() / (LongContextLab.params * 2))}× the " +
            "model itself.",
        plot = autoPlot(
            "KV cache, GB (log₁₀ tokens)",
            listOf(
                Curve("multi-head", lengths.map { log10(it.toFloat()) to bytesToGb(LongContextLab.kvCacheBytes(it)).toFloat() }, BackwardColor),
            ),
            log10(4_096f)..log10(1_048_576f),
        ),
        readout = "${bytesToGb(LongContextLab.kvCacheBytes(1_048_576))} GB of cache at 1M tokens",
    )

    frames += NetFrame(
        status = "Which is what grouped-query attention is for, and it is the cheapest win in the stack: share one " +
            "K/V head across a group of query heads and the cache divides by the group size. " +
            "${LongContextLab.attentionVariants[1].first} takes 1M tokens from " +
            "${bytesToGb(LongContextLab.kvCacheBytes(1_048_576))} GB to " +
            "${bytesToGb(LongContextLab.kvCacheBytes(1_048_576, 8))} GB; multi-query takes it to " +
            "${bytesToGb(LongContextLab.kvCacheBytes(1_048_576, 1))} GB.",
        bars = listOf(
            NetBar(
                "KV cache at 1M tokens, GB",
                LongContextLab.attentionVariants.map { bytesToGb(LongContextLab.kvCacheBytes(1_048_576, it.second)).toFloat() },
                OutputColor,
                listOf("MHA", "GQA-8", "MQA"),
            ),
        ),
        readout = "${"%.0f".format(LongContextLab.kvCacheBytes(1_048_576, LongContextLab.heads).toDouble() / LongContextLab.kvCacheBytes(1_048_576, 1))}× from the same trick",
    )

    frames += NetFrame(
        status = "Price two is prefill arithmetic, and this is where the \"attention is quadratic\" warning finally " +
            "bites. At 4k tokens attention is only " +
            "${"%.0f".format(LongContextLab.attentionShare(4_096) * 100)}% of the FLOPs — the FFN dominates, which is " +
            "why quadratic attention was ignorable for years. The crossover is at " +
            "${LongContextLab.flopCrossover()} tokens, essentially exactly 6·d for this model, and by 1M attention is " +
            "${"%.0f".format(LongContextLab.attentionShare(1_048_576) * 100)}% of the bill.",
        plot = autoPlot(
            "attention's share of prefill FLOPs",
            listOf(
                Curve(
                    "attention share",
                    (10..20).map { log2 -> log2.toFloat() to LongContextLab.attentionShare(1L shl log2).toFloat() },
                    BackwardColor,
                ),
                Curve("half", (10..20).map { it.toFloat() to 0.5f }, NeutralColor),
            ),
            10f..20f,
        ),
        readout = "crossover at ${LongContextLab.flopCrossover()} tokens ≈ 6·d",
    )

    frames += NetFrame(
        status = "Price three is the one that decides architecture. Stuffing 1M tokens into the window costs " +
            "${"%.0f".format(LongContextLab.stuffingOverhead(1_048_576))}× the prefill of retrieving " +
            "${LongContextLab.retrievedPassages} passages of ${LongContextLab.passageTokens} tokens and reading only " +
            "those. Long context and retrieval are not competitors on capability — they are the same capability at " +
            "four orders of magnitude difference in price, and the reason to stuff the window is that retrieval " +
            "missed, not that stuffing is better.",
        plot = autoPlot(
            "prefill cost ÷ retrieving 5 passages (log₁₀)",
            listOf(
                Curve("stuffing overhead", lengths.map { log10(it.toFloat()) to log10(LongContextLab.stuffingOverhead(it).toFloat()) }, BackwardColor),
            ),
            log10(4_096f)..log10(1_048_576f),
        ),
        readout = "${"%.0f".format(LongContextLab.stuffingOverhead(1_048_576))}× at 1M tokens",
    )

    frames += NetFrame(
        status = "And the window a model advertises is not the window its heads use. Under ALiBi's standard slope " +
            "schedule the position bias alone drives a head's weight below 1% of the nearest token's at " +
            "${windows.first().second} tokens for the steepest head and ${windows.last().second} for the shallowest. " +
            "Half the heads in this schedule cannot see past ${windows[3].second} tokens no matter how long the " +
            "window is — which is why \"effective context\" is measured, not declared.",
        bars = listOf(
            NetBar(
                "effective window per head (log₁₀ tokens)",
                windows.map { log10(it.second.toFloat()) },
                ForwardColor,
                windows.indices.map { "h$it" },
            ),
        ),
        readout = "advertised 1M, ${windows.first().second}–${windows.last().second} per head",
    )
    return frames
}

// ── D6 · MMLU ────────────────────────────────────────────────────────────────
// The one D6 metric that is not a scoring function but a benchmark, so it belongs on the widget that
// draws curves rather than the one that draws tokens. Every figure comes from `MmluLab`; the
// standard errors are binomial, computed at the benchmark's real size and at one subject's.

private fun mmluFrames(): List<NetFrame> {
    val frames = mutableListOf<NetFrame>()
    val top = MmluLab.models[0]
    val second = MmluLab.models[1]
    val third = MmluLab.models[2]
    val weak = MmluLab.models[3]

    frames += NetFrame(
        status = "MMLU is ${MmluLab.totalQuestions} four-way multiple-choice questions across 57 subjects, and " +
            "it is quoted as a single accuracy. The first thing that number needs is its floor: with " +
            "${MmluLab.options} options, a model that has learned nothing scores " +
            "${"%.2f".format(MmluLab.chance)}. Reported ${"%.3f".format(weak.reported)} therefore is not " +
            "\"${"%.0f".format(weak.reported * 100)}% of the way there\" — it is " +
            "${"%.3f".format(MmluLab.chanceCorrected(weak.reported))} of the way from guessing to perfect.",
        bars = listOf(
            NetBar(
                "reported accuracy",
                MmluLab.models.map { it.reported.toFloat() },
                ForwardColor,
                MmluLab.models.map { it.name.removePrefix("Model ") },
            ),
            NetBar(
                "chance-corrected",
                MmluLab.models.map { MmluLab.chanceCorrected(it.reported).toFloat() },
                AccentA,
                MmluLab.models.map { it.name.removePrefix("Model ") },
            ),
        ),
        readout = "chance floor ${"%.2f".format(MmluLab.chance)}, not 0",
    )

    val seFull = MmluLab.standardError(top.reported, MmluLab.totalQuestions)
    val sepFull = MmluLab.separation(top.reported, second.reported, MmluLab.totalQuestions)
    frames += NetFrame(
        status = "The second thing it needs is its error bar. ${MmluLab.totalQuestions} questions at " +
            "${"%.3f".format(top.reported)} accuracy carry a binomial standard error of " +
            "${"%.4f".format(seFull)} — about ${"%.1f".format(seFull * 100)} of a percentage point. So the gap " +
            "between ${top.name}'s ${"%.3f".format(top.reported)} and ${second.name}'s " +
            "${"%.3f".format(second.reported)} is **${"%.2f".format(sepFull)} standard errors**: not a " +
            "difference, a coin flip. ${third.name}'s ${"%.3f".format(third.reported)} is " +
            "${"%.1f".format(MmluLab.separation(top.reported, third.reported, MmluLab.totalQuestions))} SE back, " +
            "which is real.",
        plot = autoPlot(
            "accuracy ± 2 SE at ${MmluLab.totalQuestions} questions",
            listOf(
                Curve(
                    "reported",
                    MmluLab.models.mapIndexed { i, m -> i.toFloat() to m.reported.toFloat() },
                    ForwardColor,
                ),
                Curve(
                    "+2 SE",
                    MmluLab.models.mapIndexed { i, m ->
                        i.toFloat() to (m.reported + 2 * MmluLab.standardError(m.reported, MmluLab.totalQuestions)).toFloat()
                    },
                    NeutralColor,
                ),
                Curve(
                    "−2 SE",
                    MmluLab.models.mapIndexed { i, m ->
                        i.toFloat() to (m.reported - 2 * MmluLab.standardError(m.reported, MmluLab.totalQuestions)).toFloat()
                    },
                    NeutralColor,
                ),
            ),
            0f..(MmluLab.models.size - 1).toFloat(),
        ),
        readout = "A vs B: ${"%.2f".format(sepFull)} SE — inside the noise",
    )

    val seSubject = MmluLab.standardError(top.reported, MmluLab.subjectQuestions)
    val sepSubject = MmluLab.separation(top.reported, second.reported, MmluLab.subjectQuestions)
    frames += NetFrame(
        status = "Subject-level claims are far worse, because the error bar scales with 1/√n and the subjects " +
            "are small. A ${MmluLab.subjectQuestions}-question subject has standard error " +
            "${"%.4f".format(seSubject)} — ${"%.0f".format(seSubject / seFull)}× the full benchmark's. The same " +
            "${"%.3f".format(top.reported)} against ${"%.3f".format(second.reported)} comparison is now " +
            "${"%.2f".format(sepSubject)} SE apart. \"Model A is better at abstract algebra\" needs a gap of " +
            "about ${"%.2f".format(2 * seSubject * 1.41)} to mean anything, and almost none are that large.",
        bars = listOf(
            NetBar(
                "standard error",
                listOf(seFull.toFloat(), seSubject.toFloat()),
                BackwardColor,
                listOf("full (${MmluLab.totalQuestions})", "subject (${MmluLab.subjectQuestions})"),
            ),
            NetBar(
                "A vs B separation, SE",
                listOf(sepFull.toFloat(), sepSubject.toFloat()),
                AccentB,
                listOf("full", "subject"),
            ),
        ),
        readout = "${"%.2f".format(sepSubject)} SE at subject level",
    )

    val pairs = MmluLab.indistinguishableSubjectPairs()
    val totalPairs = MmluLab.subjects.size * (MmluLab.subjects.size - 1) / 2
    frames += NetFrame(
        status = "Run that over a real subject table and the ranking mostly evaporates. Across " +
            "${MmluLab.subjects.size} subjects at their actual sizes, **${pairs.size} of $totalPairs pairs** are " +
            "within two standard errors of each other — including " +
            "${pairs.first().first.name} at ${"%.3f".format(pairs.first().first.accuracy)} against " +
            "${pairs.first().second.name} at ${"%.3f".format(pairs.first().second.accuracy)}, whose ordering " +
            "reverses on resampling. A per-subject bar chart draws ${MmluLab.subjects.size} bars of which many " +
            "differences are decoration.",
        bars = listOf(
            NetBar(
                "subject accuracy",
                MmluLab.subjects.map { it.accuracy.toFloat() },
                ForwardColor,
                MmluLab.subjects.map { "${it.questions}" },
            ),
            NetBar(
                "2 SE for that subject",
                MmluLab.subjects.map { (2 * MmluLab.standardError(it.accuracy, it.questions)).toFloat() },
                NeutralColor,
                MmluLab.subjects.map { "${it.questions}" },
            ),
        ),
        readout = "${pairs.size} of $totalPairs subject pairs are not distinguishable",
    )

    val micro = MmluLab.microAverage()
    val macro = MmluLab.macroAverage()
    frames += NetFrame(
        status = "Then there is the averaging, which nobody states. Subject sizes range from " +
            "${MmluLab.subjects.minOf { it.questions }} to ${MmluLab.subjects.maxOf { it.questions }} questions " +
            "here, so weighting every *question* equally and weighting every *subject* equally are different " +
            "numbers: micro ${"%.4f".format(micro)}, macro ${"%.4f".format(macro)}. That " +
            "${"%.2f".format(abs(macro - micro) * 100)}-point " +
            "spread is ${"%.0f".format(abs(macro - micro) / abs(top.reported - second.reported) * 100)}% of the " +
            "gap the leaderboard uses to rank its top two models — a reporting choice worth most of the " +
            "difference being reported.",
        bars = listOf(
            NetBar(
                "averaging choice",
                listOf(micro.toFloat(), macro.toFloat()),
                OutputColor,
                listOf("micro (per question)", "macro (per subject)"),
            ),
            NetBar(
                "for scale: the A–B gap",
                listOf((top.reported - second.reported).toFloat(), abs(macro - micro).toFloat()),
                AccentB,
                listOf("A − B", "macro − micro"),
            ),
        ),
        readout = "${"%.4f".format(micro)} or ${"%.4f".format(macro)}, same predictions",
    )

    val contamination = listOf(0.0, 0.05, 0.10, 0.20)
    frames += NetFrame(
        status = "And the failure no error bar catches: contamination. If a fraction of the test set appeared " +
            "in pretraining, those questions are answered from memory. A model whose real ability is " +
            "0.550 reports ${"%.3f".format(MmluLab.contaminatedScore(0.55, 0.10))} at 10% contamination and " +
            "${"%.3f".format(MmluLab.contaminatedScore(0.55, 0.20))} at 20% — read the other way, a reported " +
            "0.700 implies a true ${"%.3f".format(MmluLab.trueAbility(0.70, 0.20))} if a fifth of the benchmark " +
            "leaked. Contamination moves the score by more than every honest gap on the leaderboard, and it is " +
            "invisible from the score alone.",
        plot = autoPlot(
            "reported score against contamination",
            listOf(
                Curve(
                    "true ability 0.55",
                    contamination.map { it.toFloat() to MmluLab.contaminatedScore(0.55, it).toFloat() },
                    BackwardColor,
                ),
                Curve(
                    "true ability 0.70",
                    contamination.map { it.toFloat() to MmluLab.contaminatedScore(0.70, it).toFloat() },
                    ForwardColor,
                ),
            ),
            0f..0.2f,
        ),
        readout = "0.550 reports ${"%.3f".format(MmluLab.contaminatedScore(0.55, 0.20))} at 20% leaked",
    )

    frames += NetFrame(
        status = "None of this makes MMLU useless — it makes it a measurement with a resolution. It separates " +
            "capability *tiers* cleanly: ${top.name} against ${weak.name} is " +
            "${"%.0f".format(MmluLab.separation(top.reported, weak.reported, MmluLab.totalQuestions))} standard " +
            "errors, which no reporting choice can manufacture. It does not separate neighbours, it does not " +
            "support subject-level claims at these sample sizes, and it cannot see contamination at all. Quote " +
            "it with its interval, its averaging method and its date, or quote it as a tier.",
        bars = listOf(
            NetBar(
                "separation from A, in SE",
                MmluLab.models.drop(1).map { MmluLab.separation(top.reported, it.reported, MmluLab.totalQuestions).toFloat() },
                ForwardColor,
                MmluLab.models.drop(1).map { it.name.removePrefix("Model ") },
            ),
        ),
        readout = "tiers yes, ranks no",
    )
    return frames
}

private val netConfigs = mapOf(
    "mmlu" to NetConfig(
        intro = "The benchmark as a measurement: its chance floor, its binomial error bar at full and subject " +
            "size, the averaging choice nobody states, and what contamination does to all of it.",
        legend = listOf(
            ForwardColor to "Reported",
            NeutralColor to "±2 SE",
            AccentB to "Choice, not capability",
        ),
        build = ::mmluFrames,
    ),
    "fine_tuning_full" to NetConfig(
        intro = "Three regimes trained on the same downstream data at six dataset sizes, plus the upstream tasks " +
            "scored again afterwards — which is where full fine-tuning's real bill shows up.",
        legend = listOf(
            ForwardColor to "Head only",
            BackwardColor to "Full fine-tune",
            NeutralColor to "From scratch",
        ),
        build = ::fineTuningFullFrames,
    ),
    "dpo" to NetConfig(
        intro = "Both alignment pipelines run end to end on the same preferences — a fitted reward model plus its " +
            "closed-form optimum, and DPO's direct descent — then the ceiling that neither of them clears.",
        legend = listOf(
            AccentA to "True quality",
            BackwardColor to "Fitted reward",
            ForwardColor to "Policy",
        ),
        build = ::dpoFrames,
    ),
    "peft" to NetConfig(
        intro = "Exact parameter and memory accounting for six tuning methods over BERT-base's config. The headline " +
            "and the number that decides what fits are not the same number.",
        legend = listOf(
            ForwardColor to "Trainable share",
            BackwardColor to "Memory",
            AccentA to "Artefact size",
        ),
        build = ::peftFrames,
    ),
    "lora_qlora" to NetConfig(
        intro = "A real fine-tuning update decomposed, then adapters trained at six ranks and scored against the " +
            "floor its spectrum puts under them.",
        legend = listOf(
            BackwardColor to "Update spectrum",
            ForwardColor to "Trained LoRA",
            AccentA to "Gap closed",
        ),
        build = ::loraFrames,
    ),
    "quantization" to NetConfig(
        intro = "Five schemes on ${QuantLab.weightCount} real-shaped weights, scored by error, by what one outlier " +
            "does to them, and by how far the layer's outputs actually move.",
        legend = listOf(
            NeutralColor to "Original",
            BackwardColor to "Error",
            AccentA to "Levels",
        ),
        build = ::quantizationFrames,
    ),
    "long_context" to NetConfig(
        intro = "The three bills a context window runs up — cache bytes, prefill arithmetic, and the retrieval it is " +
            "being used instead of — on LLaMA-2-7B's configuration.",
        legend = listOf(
            BackwardColor to "Cost",
            OutputColor to "Cache",
            ForwardColor to "Effective window",
        ),
        build = ::longContextFrames,
    ),
    "vae" to NetConfig(
        intro = "Both gradient estimators run on the same objective — one is unusable — and then a real VAE trained " +
            "at eight values of β, counting the latent dimensions each one leaves alive.",
        legend = listOf(
            ForwardColor to "Reparameterized",
            BackwardColor to "Score-function",
            OutputColor to "KL per dimension",
        ),
        build = ::vaeFrames,
    ),
    "dcgan" to NetConfig(
        intro = "Transposed-convolution coverage counted for five kernel/stride pairs. The checkerboard artefact is " +
            "visible as arithmetic before any image exists.",
        legend = listOf(
            AccentA to "Uniform coverage",
            AccentB to "Uneven — checkerboard",
            OutputColor to "Parameter share",
        ),
        build = ::dcganFrames,
    ),
    "stable_diffusion" to NetConfig(
        intro = "The whole argument for latent diffusion is a division, done here rather than described — including " +
            "the part where self- and cross-attention save wildly different amounts.",
        legend = listOf(
            ForwardColor to "Elements",
            BackwardColor to "Attention pairs",
            OutputColor to "Parameters",
        ),
        build = ::stableDiffusionFrames,
    ),
    "neural_style_transfer" to NetConfig(
        intro = "A feature map, the same map with its positions shuffled, and the two Gram matrices — which are not " +
            "similar but identical.",
        legend = listOf(
            ForwardColor to "Feature map",
            AccentB to "Permuted",
            OutputColor to "Gram",
        ),
        build = ::styleTransferFrames,
    ),
    "deepfakes" to NetConfig(
        intro = "The swap scored against the bar it has to clear, and then swept across the angle between the two " +
            "identities' expression manifolds.",
        legend = listOf(
            ForwardColor to "Shared encoder",
            AccentB to "Independent",
            NeutralColor to "Mean-face baseline",
        ),
        build = ::deepFakeFrames,
    ),
    "roc_curve" to NetConfig(
        intro = "One point per threshold, then the precision-recall curve of the same model — which does not agree with it on a 9% base rate.",
        legend = listOf(
            ForwardColor to "ROC",
            AccentB to "Precision-recall",
            NeutralColor to "Chance",
        ),
        build = ::rocCurveFrames,
    ),
    "auc" to NetConfig(
        intro = "0.969 by area and by pair counting, unchanged by a monotone distortion that ruins the probabilities. One question answered well, three badly.",
        legend = listOf(
            ForwardColor to "AUC",
            BackwardColor to "Log loss",
            AccentA to "By pair counting",
        ),
        build = ::aucFrames,
    ),
    "log_loss" to NetConfig(
        intro = "The metric that reads probabilities. One confidently wrong case contributes 13x the average, and a monotone distortion AUC cannot see costs 64%.",
        legend = listOf(
            BackwardColor to "Log loss",
            ForwardColor to "Calibration",
            AccentA to "Brier",
        ),
        build = ::logLossFrames,
    ),
    "cohens_kappa" to NetConfig(
        intro = "Accuracy 0.912 with kappa 0.000, on the same predictions — what chance correction does to a majority-class model.",
        legend = listOf(
            ForwardColor to "Kappa",
            NeutralColor to "Accuracy",
            AccentA to "MCC",
        ),
        build = ::kappaFrames,
    ),
    "gini_impurity" to NetConfig(
        intro = "The splitting criterion, its two rivals, and the pair of splits misclassification rate cannot tell apart.",
        legend = listOf(
            ForwardColor to "Gini",
            AccentB to "Entropy",
            NeutralColor to "Misclassification",
        ),
        build = ::giniImpurityFrames,
    ),
    "hinge_loss" to NetConfig(
        intro = "Zero past the margin — measured, that leaves 110 of 1,000 examples with any gradient at all, against logistic loss's 1,000.",
        legend = listOf(
            ForwardColor to "Hinge",
            AccentB to "Logistic",
            NeutralColor to "0-1 loss",
        ),
        build = ::hingeLossFrames,
    ),
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
    "data_augmentation" to NetConfig(
        intro = "One canonical pose versus five orientations, trained into a nearest-centroid classifier -- 67% " +
            "accuracy against 95% on the identical noisy test set.",
        legend = listOf(ForwardColor to "1 pose", AccentA to "5 poses"),
        build = ::dataAugmentationFrames,
    ),
    "early_stopping" to NetConfig(
        intro = "Three curves from one training run -- training loss, noisy validation loss, and the true risk " +
            "nobody gets to see during training -- and what stopping early actually buys.",
        legend = listOf(ForwardColor to "Train", OutputColor to "Validation", BackwardColor to "True risk"),
        build = ::earlyStoppingFrames,
    ),
    "layer_normalization" to NetConfig(
        intro = "The same operation over two different axes -- batch versus features -- and what that axis " +
            "choice does at batch size 1 and to batch-composition independence.",
        legend = listOf(NeutralColor to "Raw", ForwardColor to "BatchNorm", OutputColor to "LayerNorm"),
        build = ::layerNormalizationFrames,
    ),
    "group_normalization" to NetConfig(
        intro = "One formula, checked as an identity at both endpoints -- LayerNorm at G=1, InstanceNorm at G=8 -- " +
            "and the batch-independence it inherits at every G in between.",
        legend = listOf(ForwardColor to "G=1 (LayerNorm)", OutputColor to "G=2", BackwardColor to "G=8 (InstanceNorm)"),
        build = ::groupNormalizationFrames,
    ),
    "capsule_networks" to NetConfig(
        intro = "Three votes, two agreeing -- routing-by-agreement reweights them round by round, which a fixed " +
            "pooling operation has no mechanism to do.",
        legend = listOf(ForwardColor to "3 rounds", OutputColor to "10 rounds", BackwardColor to "Naive average"),
        build = ::capsuleFrames,
    ),
    "neural_odes" to NetConfig(
        intro = "A ResNet's residual step is one Euler step of a continuous dynamics -- verified convergence " +
            "orders against a closed-form solution, and an exact memory count against the adjoint method.",
        legend = listOf(AccentA to "Exact", BackwardColor to "Euler", OutputColor to "RK4 / adjoint"),
        build = ::neuralOdeFrames,
    ),
    "kan" to NetConfig(
        intro = "The same wiggly target, the same 16-parameter budget -- a fixed-shape MLP against a learnable " +
            "per-edge function, fit and scored for real.",
        legend = listOf(NeutralColor to "Target", BackwardColor to "MLP", ForwardColor to "KAN"),
        build = ::kanFrames,
    ),
    "restricted_boltzmann_machines" to NetConfig(
        intro = "One weight matrix, no within-layer links, trained by CD-1 -- reconstruction error and hidden-unit " +
            "class separation, both measured before and after training with no label ever used.",
        legend = listOf(ForwardColor to "Trained", NeutralColor to "Untrained", BackwardColor to "Class B"),
        build = ::rbmFrames,
    ),
    "deep_belief_networks" to NetConfig(
        intro = "Two RBMs stacked and trained greedily, one layer at a time -- the top layer's class separation, " +
            "against the identical architecture left at its random initial weights.",
        legend = listOf(ForwardColor to "Forward", OutputColor to "Top layer"),
        build = ::dbnFrames,
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
    // ── C8 · Optimizers & Training ────────────────────────────────────────────
    "momentum" to NetConfig(
        intro = "The same ill-conditioned bowl gradient_descent_variants runs, swept over beta at one fixed rate " +
            "— where accumulating a velocity wins, and where it starts overshooting instead.",
        legend = listOf(
            NeutralColor to "beta = 0.5",
            ForwardColor to "beta = 0.9",
            BackwardColor to "beta = 0.99",
        ),
        build = ::momentumFrames,
    ),
    "adagrad" to NetConfig(
        intro = "A dense feature and a sparse one, run 2,000 steps: the per-parameter rate that keeps the rare " +
            "feature's boost — and never resets, so it keeps shrinking long after it should stop mattering.",
        legend = listOf(
            ForwardColor to "Dense feature",
            AccentB to "Sparse feature",
        ),
        build = ::adaGradFrames,
    ),
    "rmsprop" to NetConfig(
        intro = "The identical dense/sparse stream, with a moving average instead of a running sum — AdaGrad's " +
            "stall is gone, and so is most of its memory for the rare feature.",
        legend = listOf(
            ForwardColor to "RMSprop",
            NeutralColor to "AdaGrad",
        ),
        build = ::rmsPropFrames,
    ),
    "adam" to NetConfig(
        intro = "One constant gradient, corrected and not: the bias-corrected step ratio is exactly right at " +
            "every step, and the uncorrected one wanders before settling to the same place.",
        legend = listOf(
            ForwardColor to "Corrected",
            BackwardColor to "Uncorrected",
        ),
        build = ::adamFrames,
    ),
    "adamw" to NetConfig(
        intro = "Two parameters with different gradient histories, one weight-decay-only step each: L2-in-Adam " +
            "decays them unequally, AdamW decays them by exactly the same fraction.",
        legend = listOf(
            BackwardColor to "L2-in-Adam",
            ForwardColor to "AdamW",
        ),
        build = ::adamWFrames,
    ),
    "lr_schedulers" to NetConfig(
        intro = "Decay is not free on a clean bowl — and it is exactly what a persistent disturbance needs, " +
            "shrinking the steady-state loss floor the way lr-squared predicts.",
        legend = listOf(
            NeutralColor to "Constant",
            ForwardColor to "Decayed",
        ),
        build = ::lrSchedulerFrames,
    ),
    "cross_entropy_loss" to NetConfig(
        intro = "Softmax and cross-entropy together: the gradient by the clean formula and the same gradient the " +
            "long way through softmax's own Jacobian, matched to the digit.",
        legend = listOf(
            ForwardColor to "p − y (direct)",
            AccentB to "Via Jacobian",
        ),
        build = ::crossEntropyLossFrames,
    ),
    "kl_divergence" to NetConfig(
        intro = "One P, one Q, both directions of the divergence — then the direction that decides whether a " +
            "single Gaussian fit to a bimodal target covers both modes or commits to one.",
        legend = listOf(
            ForwardColor to "P (target)",
            AccentB to "Forward-KL fit",
            BackwardColor to "Reverse-KL fit",
        ),
        build = ::klDivergenceFrames,
    ),
)

// ── C9 · Regularization + Specialized ────────────────────────────────────────

private fun dataAugmentationFrames(): List<NetFrame> {
    fun grid(g: Array<IntArray>) = GridView("pattern", g.map { row -> row.map { it.toFloat() } })
    val l = DataAugmentationLab.canonicalL
    val rot90 = DataAugmentationLab.rotate90(l)
    val rot180 = DataAugmentationLab.rotate90(rot90)
    val rot270 = DataAugmentationLab.rotate90(rot180)
    val flipped = DataAugmentationLab.flipH(l)
    val r = DataAugmentationLab.result
    return listOf(
        NetFrame(status = "One canonical pose -- the entire training set for a centroid classifier.", grids = listOf(grid(l))),
        NetFrame(status = "Rotated 90 degrees -- a pose the unaugmented centroid never saw.", grids = listOf(grid(rot90))),
        NetFrame(status = "Rotated 180 degrees.", grids = listOf(grid(rot180))),
        NetFrame(status = "Rotated 270 degrees.", grids = listOf(grid(rot270))),
        NetFrame(status = "Flipped horizontally -- the fifth and last augmentation.", grids = listOf(grid(flipped))),
        NetFrame(
            status = "Tested on noisy versions of every orientation: one pose trains a centroid that gets " +
                "${"%.0f".format(r.unaugmentedAccuracy * 100)}% right. All five orientations train one that gets " +
                "${"%.0f".format(r.augmentedAccuracy * 100)}% right -- same rule, same test set.",
            bars = listOf(NetBar("accuracy", listOf(r.unaugmentedAccuracy.toFloat(), r.augmentedAccuracy.toFloat()), ForwardColor, listOf("1 pose", "5 poses"))),
        ),
    )
}

private fun earlyStoppingFrames(): List<NetFrame> {
    val epochs = EarlyStoppingLab.run.epochs.filter { it.step % 100 == 0 }
    val xRange = 0f..4000f
    val yRange = 0f..0.025f
    val trainCurve = Curve("train", epochs.map { it.step.toFloat() to it.trainMse.toFloat() }, ForwardColor)
    val valCurve = Curve("validation", epochs.map { it.step.toFloat() to it.validationMse.toFloat() }, OutputColor)
    val trueCurve = Curve("true risk", epochs.map { it.step.toFloat() to it.testTrueRiskMse.toFloat() }, BackwardColor)
    val best = EarlyStoppingLab.run.bestValidationEpoch
    val final = EarlyStoppingLab.run.finalEpoch
    return listOf(
        NetFrame(
            status = "A degree-9 polynomial, trained by gradient descent on 20 noisy points. Training loss falls " +
                "for the entire 4,000-step run -- exactly why it cannot be the stopping signal.",
            plot = CurvePlot("loss", listOf(trainCurve), xRange, yRange),
        ),
        NetFrame(
            status = "Validation loss (never used to update a weight) bottoms out at step 120 -- then jitters " +
                "within about 0.3% of that floor for the rest of the run.",
            plot = CurvePlot("loss", listOf(trainCurve, valCurve), xRange, yRange),
        ),
        NetFrame(
            status = "True risk -- error against the clean function, never directly observable during training -- " +
                "bottoms at step 100 and climbs monotonically after, ending 37% higher at step 4,000.",
            plot = CurvePlot("loss", listOf(trainCurve, valCurve, trueCurve), xRange, yRange),
        ),
        NetFrame(
            status = "Stopping at validation's minimum (step 120): true risk 0.0047. Training to the end (step " +
                "4,000): true risk 0.0064 -- 37% worse, even though validation loss barely moved.",
            bars = listOf(NetBar("true risk", listOf(best.testTrueRiskMse.toFloat(), final.testTrueRiskMse.toFloat()), BackwardColor, listOf("stop at 120", "train to 4000"))),
        ),
    )
}

private fun layerNormalizationFrames(): List<NetFrame> {
    val batch8 = LayerNormalizationLab.batch(8, seed = 42)
    val raw = batch8[0]
    val bn = LayerNormalizationLab.batchNormalize(batch8)[0]
    val ln = LayerNormalizationLab.layerNormalizeRow(raw)
    val captions = (1..raw.size).map { "f$it" }
    val single = LayerNormalizationLab.batch(1, seed = 9)
    val bnSingle = LayerNormalizationLab.batchNormalize(single)[0]
    val lnSingle = LayerNormalizationLab.layerNormalizeRow(single[0])
    val jitters = LayerNormalizationLab.jitterSweep.map { size -> LayerNormalizationLab.batchStatisticJitter(size, feature = 2).toFloat() }
    return listOf(
        NetFrame(status = "One row from a batch of 8 -- six features at very different scales.", bars = listOf(NetBar("raw", raw.map { it.toFloat() }, NeutralColor, captions))),
        NetFrame(status = "BatchNorm: each feature normalized against its own mean/variance across all 8 rows in the batch.", bars = listOf(NetBar("batch-normalized", bn.map { it.toFloat() }, ForwardColor, captions))),
        NetFrame(status = "LayerNorm: the same row normalized across its own six features instead -- no other row is ever read.", bars = listOf(NetBar("layer-normalized", ln.map { it.toFloat() }, OutputColor, captions))),
        NetFrame(
            status = "The identical input, alone in a batch of size 1. BatchNorm's variance -- one value against " +
                "itself -- is exactly zero, so every feature normalizes to nothing.",
            bars = listOf(NetBar("BatchNorm at batch size 1", bnSingle.map { it.toFloat() }, BackwardColor, captions)),
        ),
        NetFrame(status = "LayerNorm on the same lone input: unaffected, because it was never reading the batch axis.", bars = listOf(NetBar("LayerNorm at batch size 1", lnSingle.map { it.toFloat() }, OutputColor, captions))),
        NetFrame(
            status = "A small batch's own estimate of its statistic jitters draw to draw: 0.549 at batch 4 down " +
                "to 0.137 at batch 64 -- a 4x drop matching root-16 exactly. LayerNorm never estimates anything " +
                "from the batch at all.",
            bars = listOf(NetBar("estimate jitter", jitters, BackwardColor, LayerNormalizationLab.jitterSweep.map { "n=$it" })),
        ),
    )
}

private fun groupNormalizationFrames(): List<NetFrame> {
    val s = GroupNormalizationLab.sample(seed = 7)
    val raw0 = s[0].map { it.toFloat() }
    val ln = GroupNormalizationLab.layerNormEquivalent(s)[0].map { it.toFloat() }
    val instance = GroupNormalizationLab.instanceNormEquivalent(s)[0].map { it.toFloat() }
    val g2 = GroupNormalizationLab.groupNormalize(s, 2)[0].map { it.toFloat() }
    val captions = (1..s[0].size).map { "s$it" }
    return listOf(
        NetFrame(status = "Channel 0's six spatial activations, one sample -- eight channels total, grouped in pairs by scale.", bars = listOf(NetBar("raw", raw0, NeutralColor, captions))),
        NetFrame(status = "G = 1 (every channel, one group): exactly LayerNorm -- verified identical to 10^-12.", bars = listOf(NetBar("G=1 (LayerNorm)", ln, ForwardColor, captions))),
        NetFrame(status = "G = 2 (this channel's own pair): GroupNorm's actual setting here.", bars = listOf(NetBar("G=2", g2, OutputColor, captions))),
        NetFrame(status = "G = 8 (one channel per group): exactly InstanceNorm -- the other endpoint, also verified identical.", bars = listOf(NetBar("G=8 (InstanceNorm)", instance, BackwardColor, captions))),
        NetFrame(
            status = "Every group size in between inherits LayerNorm's batch-independence: recomputing this " +
                "sample's own channels gives the same answer regardless of batch size, because nothing here reads " +
                "the batch axis at all.",
            readout = "batch-independent at every G",
        ),
    )
}

private fun capsuleFrames(): List<NetFrame> {
    val rounds3 = CapsuleLab.rounds
    val final10 = CapsuleLab.route(10).last()
    val frames = mutableListOf<NetFrame>()
    frames += NetFrame(
        status = "Three lower capsules cast votes for digit capsule A. Two roughly agree; the third points " +
            "nearly the opposite way. Routing starts every vote at equal weight.",
        bars = listOf(NetBar("routing weight to A", rounds3[0].cA.map { it.toFloat() }, ForwardColor, listOf("vote 1", "vote 2", "vote 3"))),
    )
    rounds3.forEachIndexed { i, r ->
        frames += NetFrame(
            status = "Round ${i + 1} of 3: agreement with the emerging output raises or lowers each vote's " +
                "weight. |vA| = ${"%.3f".format(CapsuleLab.norm(r.vA))}.",
            bars = listOf(NetBar("routing weight to A", r.cA.map { it.toFloat() }, ForwardColor, listOf("vote 1", "vote 2", "vote 3"))),
        )
    }
    frames += NetFrame(
        status = "Run ten rounds instead of three: the disagreeing vote's weight collapses to " +
            "${"%.3f".format(final10.cA[2])} -- functionally voted out.",
        bars = listOf(NetBar("routing weight to A (10 rounds)", final10.cA.map { it.toFloat() }, OutputColor, listOf("vote 1", "vote 2", "vote 3"))),
    )
    frames += NetFrame(
        status = "A naive unweighted average of all three votes points 24.4 degrees off the two agreeing votes' " +
            "true consensus direction. Three rounds of routing narrows that to 16.7 degrees; ten rounds narrows " +
            "it to 6.1 degrees.",
        bars = listOf(NetBar("angle off true agreement (degrees)", listOf(24.4f, 16.7f, 6.1f), BackwardColor, listOf("naive avg", "3 rounds", "10 rounds"))),
    )
    return frames
}

private fun neuralOdeFrames(): List<NetFrame> {
    val exactPts = NeuralOdeLab.exactTrajectory(200).map { (t, z) -> t.toFloat() to z.toFloat() }
    val eulerCoarse = NeuralOdeLab.eulerTrajectory(5).map { (t, z) -> t.toFloat() to z.toFloat() }
    val eulerFine = NeuralOdeLab.eulerTrajectory(80).map { (t, z) -> t.toFloat() to z.toFloat() }
    val xRange = 0f..2f
    val yRange = 0f..1f
    val stepLabels = NeuralOdeLab.stepCounts.map { "n=$it" }
    return listOf(
        NetFrame(
            status = "dz/dt = -z has a closed-form solution: z(t) = z0 e^-t. A ResNet's residual step is exactly " +
                "one Euler step of some dynamics -- here shown on the one case where the true answer is known exactly.",
            plot = CurvePlot("z(t)", listOf(Curve("exact", exactPts, AccentA)), xRange, yRange),
        ),
        NetFrame(
            status = "Euler's method with only 5 steps: visibly off the true curve -- each step's error compounds into the next.",
            plot = CurvePlot("z(t)", listOf(Curve("exact", exactPts, AccentA), Curve("euler, 5 steps", eulerCoarse, BackwardColor)), xRange, yRange),
        ),
        NetFrame(
            status = "80 steps closes most of the gap -- but the error only fell by 16x for 16x the steps: first-order convergence, exactly as predicted.",
            plot = CurvePlot("z(t)", listOf(Curve("exact", exactPts, AccentA), Curve("euler, 80 steps", eulerFine, BackwardColor)), xRange, yRange),
        ),
        NetFrame(
            status = "Euler's error at t=2, across five doublings of step count: each doubling roughly halves it -- verified, not assumed.",
            bars = listOf(NetBar("Euler error", NeuralOdeLab.eulerErrors.map { it.toFloat() }, BackwardColor, stepLabels)),
        ),
        NetFrame(
            status = "RK4's error, same five step counts: each doubling cuts it by roughly 16x -- fourth-order convergence, already orders of magnitude more accurate at equal step count.",
            bars = listOf(NetBar("RK4 error", NeuralOdeLab.rk4Errors.map { it.toFloat() }, OutputColor, stepLabels)),
        ),
        NetFrame(
            status = "The memory account: a 50-layer ResNet with a 64-dimensional state stores every layer's " +
                "activation for backprop -- 3,200 numbers. The adjoint method solves a second ODE backward " +
                "instead, needing only the state and its adjoint: 128 numbers, regardless of depth.",
            bars = listOf(NetBar("stored floats", listOf(NeuralOdeLab.resNetStoredFloats.toFloat(), NeuralOdeLab.adjointStoredFloats.toFloat()), ForwardColor, listOf("ResNet (50 layers)", "adjoint (O(1))"))),
        ),
    )
}

private fun kanFrames(): List<NetFrame> {
    val xRange = -2f..2f
    val yRange = -1.3f..1.3f
    val samplePoints = (-40..40).map { it / 20f }
    val targetCurve = Curve("target", samplePoints.map { x -> x to KanLab.target(x.toDouble()).toFloat() }, NeutralColor)
    val mlpCurve = Curve("MLP fit (16 params)", samplePoints.map { x -> x to KanLab.trainedMlp.predict(x.toDouble()).toFloat() }, BackwardColor)
    val kanCurve = Curve("KAN fit (16 params)", samplePoints.map { x -> x to KanLab.trainedKan.predict(x.toDouble()).toFloat() }, ForwardColor)
    return listOf(
        NetFrame(status = "The target: sin(5x)·e^(−x²/2) -- several oscillations across a small range.", plot = CurvePlot("f(x)", listOf(targetCurve), xRange, yRange)),
        NetFrame(status = "A 5-hidden-unit tanh MLP -- 16 learnable numbers, all in the edge weights -- fit to 40 samples.", plot = CurvePlot("f(x)", listOf(targetCurve, mlpCurve), xRange, yRange)),
        NetFrame(
            status = "A KAN-style layer with 16 movable knot values -- the same parameter budget, spent directly " +
                "on the curve's shape instead of on weights combining a fixed shape.",
            plot = CurvePlot("f(x)", listOf(targetCurve, kanCurve), xRange, yRange),
        ),
        NetFrame(
            status = "Held-out test error, same 16 parameters both models: MLP 0.166, KAN 0.0030 -- about 55x lower.",
            bars = listOf(NetBar("test MSE", listOf(KanLab.mlpTestMse.toFloat(), KanLab.kanTestMse.toFloat()), BackwardColor, listOf("MLP", "KAN"))),
        ),
    )
}

// ── B10 · Restricted Boltzmann Machines + Deep Belief Networks ──────────────

private fun rbmFrames(): List<NetFrame> {
    val sampleA = RbmLab.trainData.first { it.second }.first
    val untrainedHiddenA = RbmLab.untrainedRbm.hiddenProbs(sampleA)
    val trainedHiddenA = RbmLab.trainedRbm.hiddenProbs(sampleA)
    val meanA = RbmLab.meanHiddenActivation(RbmLab.trainedRbm, isClassA = true)
    val meanB = RbmLab.meanHiddenActivation(RbmLab.trainedRbm, isClassA = false)
    val untrainedErr = RbmLab.reconstructionError(RbmLab.untrainedRbm)
    val trainedErr = RbmLab.reconstructionError(RbmLab.trainedRbm)

    return listOf(
        NetFrame(
            status = "Six visible units, three hidden units, one weight matrix -- no visible-visible or " +
                "hidden-hidden links at all. That restriction is what keeps both conditionals simple sigmoids.",
            layers = listOf(
                NetLayer("visible (6)", sampleA.map { NetNode(it.toFloat(), NodeMood.FORWARD) }),
                NetLayer("hidden (3)", untrainedHiddenA.map { NetNode(it.toFloat(), NodeMood.IDLE) }),
            ),
        ),
        NetFrame(
            status = "Before training: this class-A example's hidden activations, from random weights -- all " +
                "close to 0.5, carrying no information about which class produced them.",
            bars = listOf(NetBar("hidden activation (untrained)", untrainedHiddenA.map { it.toFloat() }, NeutralColor, listOf("h1", "h2", "h3"))),
        ),
        NetFrame(
            status = "After 60 epochs of CD-1 -- one up-down-up pass per example, no labels ever used -- the " +
                "same example's hidden activations: two units strongly on, none of it told what a 'class' is.",
            bars = listOf(NetBar("hidden activation (trained)", trainedHiddenA.map { it.toFloat() }, ForwardColor, listOf("h1", "h2", "h3"))),
        ),
        NetFrame(
            status = "Averaged over every class-A example versus every class-B example: units 1 and 2 fire for " +
                "A and not B, unit 3 runs the other way. Three unlabeled hidden units learned the data's structure.",
            bars = listOf(
                NetBar("mean hidden, class A", meanA.map { it.toFloat() }, ForwardColor, listOf("h1", "h2", "h3")),
                NetBar("mean hidden, class B", meanB.map { it.toFloat() }, BackwardColor, listOf("h1", "h2", "h3")),
            ),
        ),
        NetFrame(
            status = "Reconstruction error -- how much a sample changes after one up-down pass -- falls from " +
                "0.498 (untrained, chance-level) to 0.170 (CD-1 trained).",
            bars = listOf(NetBar("reconstruction error", listOf(untrainedErr.toFloat(), trainedErr.toFloat()), BackwardColor, listOf("untrained", "trained"))),
        ),
        NetFrame(
            status = "And the class separation those hidden units carry: 0.06 before training, 1.33 after -- " +
                "over twentyfold, with no label used anywhere in the process.",
            bars = listOf(NetBar("hidden-layer class separation", listOf(RbmLab.separation(RbmLab.untrainedRbm).toFloat(), RbmLab.separation(RbmLab.trainedRbm).toFloat()), OutputColor, listOf("untrained", "trained"))),
        ),
    )
}

private fun dbnFrames(): List<NetFrame> {
    val greedy = DbnLab.greedy
    val random = DbnLab.random
    return listOf(
        NetFrame(
            status = "Two RBMs stacked: 6 visible units to 3 to 2. Trained greedily -- the first RBM trains on " +
                "the raw data, and the second trains on the first's hidden activations, one layer at a time.",
            layers = listOf(
                NetLayer("visible (6)", RbmLab.prototypeA.map { NetNode(it.toFloat(), NodeMood.FORWARD) }),
                NetLayer("layer 1 (3)", greedy.first.hiddenProbs(RbmLab.prototypeA).map { NetNode(it.toFloat(), NodeMood.FORWARD) }),
                NetLayer("layer 2 (2)", greedy.second.hiddenProbs(greedy.first.hiddenProbs(RbmLab.prototypeA)).map { NetNode(it.toFloat(), NodeMood.OUTPUT) }),
            ),
        ),
        NetFrame(
            status = "The top layer's class separation, measured with no supervised signal used anywhere: " +
                "${"%.2f".format(DbnLab.greedySeparation)} after greedy layer-wise pretraining.",
            bars = listOf(NetBar("top-layer separation", listOf(DbnLab.greedySeparation.toFloat()), ForwardColor, listOf("greedy pretrained"))),
        ),
        NetFrame(
            status = "The identical architecture, left at its random initial weights -- no pretraining at all: " +
                "separation ${"%.3f".format(DbnLab.randomSeparation)}, over 300x smaller.",
            bars = listOf(NetBar("top-layer separation", listOf(DbnLab.greedySeparation.toFloat(), DbnLab.randomSeparation.toFloat()), ForwardColor, listOf("greedy pretrained", "random init"))),
        ),
        NetFrame(
            status = "That gap is the entire argument for greedy pretraining: an untrained deep stack's top " +
                "layer carries essentially no information about the categories, because random weights compose " +
                "into more random weights. A greedily pretrained one already has most of the separation a " +
                "supervised pass would otherwise have to discover from nothing.",
            readout = "${"%.0f".format(DbnLab.greedySeparation / DbnLab.randomSeparation)}x head start, before any label is used",
        ),
    )
}

// ── C8 · Optimizers & Training ────────────────────────────────────────────────

private fun momentumFrames(): List<NetFrame> {
    val runs = MomentumLab.runs
    fun trajectory(beta: Double, color: Color) =
        Curve("β=$beta", runs.getValue(beta).path.map { it[0].toFloat() to it[1].toFloat() }, color)

    val shown = listOf(0.5, 0.9, 0.99)
    val allPoints = shown.flatMap { runs.getValue(it).path }
    val pad = 0.15f
    val xLo = allPoints.minOf { it[0] }.toFloat() - pad
    val xHi = allPoints.maxOf { it[0] }.toFloat() + pad
    val yLo = allPoints.minOf { it[1] }.toFloat() - pad
    val yHi = allPoints.maxOf { it[1] }.toFloat() + pad
    val r5 = runs.getValue(0.5)
    val r9 = runs.getValue(0.9)
    val r99 = runs.getValue(0.99)

    return listOf(
        NetFrame(
            status = "The same ill-conditioned bowl gradient_descent_variants runs, one fixed rate, beta swept. " +
                "beta=0.5: a little accumulated velocity -- final loss ${"%.4f".format(r5.finalLoss)}.",
            plot = CurvePlot("w1 vs w2", listOf(trajectory(0.5, NeutralColor)), xLo..xHi, yLo..yHi),
        ),
        NetFrame(
            status = "beta=0.9: the consistent downhill push on the flat axis compounds instead of being paid " +
                "one small step at a time. Final loss ${"%.5f".format(r9.finalLoss)} -- ${"%.0f".format(r5.finalLoss / r9.finalLoss)}x below beta=0.5.",
            plot = CurvePlot("w1 vs w2", listOf(trajectory(0.5, NeutralColor), trajectory(0.9, ForwardColor)), xLo..xHi, yLo..yHi),
        ),
        NetFrame(
            status = "beta=0.99, same rate: it no longer damps in 60 steps. Its first overshoot back past zero " +
                "reaches ${"%.3f".format(r99.maxAbsW1AfterStep5)} of the starting distance -- beta=0.9's overshoot only " +
                "reaches ${"%.3f".format(r9.maxAbsW1AfterStep5)}. Final loss ${"%.3f".format(r99.finalLoss)}, ${"%.0f".format(r99.finalLoss / r9.finalLoss)}x worse than beta=0.9.",
            plot = CurvePlot(
                "w1 vs w2",
                listOf(trajectory(0.5, NeutralColor), trajectory(0.9, ForwardColor), trajectory(0.99, BackwardColor)),
                xLo..xHi, yLo..yHi,
            ),
        ),
        NetFrame(
            status = "Final loss after 60 steps, same rate throughout every run: beta=0.9 wins by more than an " +
                "order of magnitude over both neighbors -- beta=0.5 under-accelerates, beta=0.99 overshoots.",
            bars = listOf(
                NetBar(
                    "final loss",
                    MomentumLab.BETAS.map { runs.getValue(it).finalLoss.toFloat() },
                    ForwardColor,
                    MomentumLab.BETAS.map { "β=$it" },
                ),
            ),
        ),
    )
}

private fun adaGradFrames(): List<NetFrame> {
    val history = AdaGradLab.history
    val denseCurvePoints = history.filterIndexed { i, _ -> i % 20 == 0 }.map { it.t.toFloat() to it.effRateDense.toFloat() }
    val at200 = AdaGradLab.at(200)
    val at2000 = AdaGradLab.at(2000)

    return listOf(
        NetFrame(
            status = "A dense feature (gradient magnitude 1, every step) beside a sparse one (magnitude 2, one " +
                "step in ten). AdaGrad divides the rate by the square root of every squared gradient seen so far " +
                "-- it never resets.",
            plot = CurvePlot(
                "effective rate, dense feature",
                listOf(Curve("dense", denseCurvePoints, ForwardColor)),
                0f..AdaGradLab.TOTAL_STEPS.toFloat(), 0f..(AdaGradLab.LR.toFloat() + 0.02f),
            ),
        ),
        NetFrame(
            status = "By step 200: the dense feature has accumulated G=${"%.0f".format(at200.gAccumDense)} (200 " +
                "steps of magnitude-1²), the sparse one G=${"%.0f".format(at200.gAccumSparse)} (20 firings of " +
                "magnitude-2²) -- fewer, bigger gradients still sum to less here.",
            bars = listOf(NetBar("accumulated G", listOf(at200.gAccumDense.toFloat(), at200.gAccumSparse.toFloat()), NeutralColor, listOf("dense", "sparse"))),
        ),
        NetFrame(
            status = "Which flips into the effective rate: dense settles to ${"%.4f".format(at200.effRateDense)}, " +
                "sparse keeps ${"%.4f".format(at200.effRateSparse)} -- ${"%.2f".format(at200.effRateSparse / at200.effRateDense)}x the " +
                "dense rate, right where AdaGrad is supposed to help a rare feature.",
            bars = listOf(NetBar("effective rate at t=200", listOf(at200.effRateDense.toFloat(), at200.effRateSparse.toFloat()), ForwardColor, listOf("dense", "sparse"))),
        ),
        NetFrame(
            status = "But the accumulator only grows. By step 2,000 the dense rate has shrunk further, to " +
                "${"%.4f".format(at2000.effRateDense)} -- exactly lr/sqrt(t), because a constant unit gradient makes " +
                "G=t. The rate keeps falling even after the loss it is meant to drive has flattened out.",
            bars = listOf(NetBar("dense effective rate", listOf(at200.effRateDense.toFloat(), at2000.effRateDense.toFloat()), BackwardColor, listOf("t=200", "t=2,000"))),
        ),
    )
}

private fun rmsPropFrames(): List<NetFrame> {
    val rms200 = RmsPropLab.at(200)
    val rms2000 = RmsPropLab.at(2000)
    val ada200 = AdaGradLab.at(200)
    val ada2000 = AdaGradLab.at(2000)
    val rmsCurve = RmsPropLab.history.filterIndexed { i, _ -> i % 20 == 0 }.map { it.t.toFloat() to it.effRateDense.toFloat() }
    val adaCurve = AdaGradLab.history.filterIndexed { i, _ -> i % 20 == 0 }.map { it.t.toFloat() to it.effRateDense.toFloat() }

    return listOf(
        NetFrame(
            status = "The identical dense/sparse stream, but the accumulator is now an exponential moving " +
                "average (gamma=0.9) instead of a running sum -- it can go back down as well as up.",
            plot = CurvePlot(
                "dense effective rate: RMSprop vs AdaGrad",
                listOf(Curve("RMSprop", rmsCurve, ForwardColor), Curve("AdaGrad", adaCurve, NeutralColor)),
                0f..RmsPropLab.TOTAL_STEPS.toFloat(), 0f..1.6f,
            ),
        ),
        NetFrame(
            status = "By step 2,000 RMSprop's dense rate has settled at ${"%.4f".format(rms2000.effRateDense)} -- " +
                "essentially lr itself -- and stays there. AdaGrad's has shrunk to ${"%.4f".format(ada2000.effRateDense)}, " +
                "${"%.1f".format(rms2000.effRateDense / ada2000.effRateDense)}x smaller for the identical gradient stream.",
            bars = listOf(NetBar("dense rate at t=2,000", listOf(rms2000.effRateDense.toFloat(), ada2000.effRateDense.toFloat()), ForwardColor, listOf("RMSprop", "AdaGrad"))),
        ),
        NetFrame(
            status = "The cost: right after a sparse firing at t=200, RMSprop keeps only " +
                "${"%.2f".format(rms200.effRateSparse / rms200.effRateDense)}x the dense rate for the sparse feature -- AdaGrad " +
                "keeps ${"%.2f".format(ada200.effRateSparse / ada200.effRateDense)}x. The EMA forgets the sparse feature's " +
                "boost between firings instead of accumulating it forever.",
            bars = listOf(
                NetBar(
                    "sparse/dense rate ratio at t=200",
                    listOf((rms200.effRateSparse / rms200.effRateDense).toFloat(), (ada200.effRateSparse / ada200.effRateDense).toFloat()),
                    BackwardColor,
                    listOf("RMSprop", "AdaGrad"),
                ),
            ),
        ),
    )
}

private fun adamFrames(): List<NetFrame> {
    val h = AdamLab.history
    val uncorrectedPoints = h.map { it.t.toFloat() to it.uncorrectedRatio.toFloat() }
    val correctedPoints = h.map { it.t.toFloat() to it.correctedRatio.toFloat() }
    val peak = h.maxBy { it.uncorrectedRatio }
    val at1 = AdamLab.at(1)
    val at100 = AdamLab.at(100)

    return listOf(
        NetFrame(
            status = "A single constant gradient (g=2.0), run through Adam's two moving averages. The bias-" +
                "corrected step ratio m̂/√v̂ is exactly ${"%.3f".format(at1.correctedRatio)} at step 1 and every step " +
                "after -- an identity, not an approximation, because the correction exactly recovers a constant " +
                "input at any t.",
            plot = CurvePlot(
                "step ratio: corrected vs uncorrected",
                listOf(Curve("corrected", correctedPoints, ForwardColor), Curve("uncorrected", uncorrectedPoints, BackwardColor)),
                0f..AdamLab.TOTAL_STEPS.toFloat(), 0f..(peak.uncorrectedRatio.toFloat() + 0.5f),
            ),
        ),
        NetFrame(
            status = "Without correction, the same ratio starts at ${"%.3f".format(at1.uncorrectedRatio)}, peaks at " +
                "${"%.3f".format(peak.uncorrectedRatio)} around step ${peak.t}, and is still ${"%.3f".format(at100.uncorrectedRatio)} " +
                "by step 100 -- it got to roughly the right place only after first swinging more than 3x past it.",
            readout = "uncorrected peak ${"%.2f".format(peak.uncorrectedRatio)} at t=${peak.t} vs the true ratio of 1.000",
        ),
    )
}

private fun adamWFrames(): List<NetFrame> {
    val large = AdamWLab.resultLargeV
    val small = AdamWLab.resultSmallV
    val vRatio = AdamWLab.vLargeHistory / AdamWLab.vSmallHistory

    return listOf(
        NetFrame(
            status = "Two parameters, warmed up 300 steps with gradient magnitude 5 (large history) and 0.5 " +
                "(small history) -- their second-moment accumulators land ${"%.0f".format(vRatio)}x apart. Now one " +
                "weight-decay-only step, both methods.",
            bars = listOf(NetBar("accumulated v", listOf(AdamWLab.vLargeHistory.toFloat(), AdamWLab.vSmallHistory.toFloat()), NeutralColor, listOf("large-v param", "small-v param"))),
        ),
        NetFrame(
            status = "L2-in-Adam: the decay term is folded into the gradient, so it gets divided by √v same as " +
                "any gradient would. The small-v parameter decays ${"%.1f".format(small.stepL2 / large.stepL2)}x faster " +
                "than the large-v one -- identical weight decay, unequal effect.",
            bars = listOf(NetBar("L2-in-Adam decay step", listOf(large.stepL2.toFloat(), small.stepL2.toFloat()), BackwardColor, listOf("large-v param", "small-v param"))),
        ),
        NetFrame(
            status = "AdamW: the decay term never goes through v at all. Both parameters shrink by exactly " +
                "lr·wd = ${"%.3f".format(large.stepDecoupled)} -- a ratio of ${"%.3f".format(small.stepDecoupled / large.stepDecoupled)}, " +
                "not ${"%.1f".format(small.stepL2 / large.stepL2)}.",
            bars = listOf(NetBar("AdamW decay step", listOf(large.stepDecoupled.toFloat(), small.stepDecoupled.toFloat()), ForwardColor, listOf("large-v param", "small-v param"))),
        ),
    )
}

private fun lrSchedulerFrames(): List<NetFrame> {
    val constant = LrSchedulerLab.constantPath
    val stepD = LrSchedulerLab.stepDecayPath
    val cosine = LrSchedulerLab.cosinePath
    val warm = LrSchedulerLab.warmupCosinePath
    fun lossAt(step: Int, path: List<DoubleArray>) = LrSchedulerLab.loss(path[step])
    fun tailCurve(path: List<DoubleArray>, label: String, color: Color) =
        Curve(label, (10..LrSchedulerLab.STEPS).map { it.toFloat() to lossAt(it, path).toFloat() }, color)

    val frames = mutableListOf<NetFrame>()
    frames += NetFrame(
        status = "The decaying schedules below start hot -- lr=0.09, close to the steep axis's own stability " +
            "limit of 0.1 -- which is safe only because they immediately decay away from it. Step 1 loss: " +
            "constant (lr=0.05, conservative throughout) ${"%.3f".format(lossAt(1, constant))}; the three that " +
            "start at 0.09 overshoot to ${"%.2f".format(lossAt(1, stepD))}–${"%.2f".format(lossAt(1, warm))} before recovering.",
        bars = listOf(
            NetBar(
                "loss after step 1",
                listOf(lossAt(1, constant).toFloat(), lossAt(1, stepD).toFloat(), lossAt(1, cosine).toFloat(), lossAt(1, warm).toFloat()),
                BackwardColor,
                listOf("constant", "step decay", "cosine", "warmup+cosine"),
            ),
        ),
    )
    frames += NetFrame(
        status = "From step 10 on, all four have recovered from that transient. Step decay (halve every 20 " +
            "steps) actually finishes lowest here: ${"%.4f".format(lossAt(LrSchedulerLab.STEPS, stepD))} against " +
            "constant's ${"%.4f".format(lossAt(LrSchedulerLab.STEPS, constant))}.",
        plot = CurvePlot(
            "loss per step, steps 10-60",
            listOf(tailCurve(constant, "constant", NeutralColor), tailCurve(stepD, "step decay", ForwardColor)),
            10f..LrSchedulerLab.STEPS.toFloat(), 0f..0.4f,
        ),
    )
    frames += NetFrame(
        status = "Cosine (to 0) and warmup+cosine both finish worse than constant here -- " +
            "${"%.4f".format(lossAt(LrSchedulerLab.STEPS, cosine))} and ${"%.4f".format(lossAt(LrSchedulerLab.STEPS, warm))} -- because " +
            "they shrink the rate before the flat axis has finished using it. Decay is not free on a landscape " +
            "with no noise to justify it.",
        plot = CurvePlot(
            "loss per step, steps 10-60",
            listOf(
                tailCurve(constant, "constant", NeutralColor),
                tailCurve(cosine, "cosine", BackwardColor),
                tailCurve(warm, "warmup+cosine", AccentB),
            ),
            10f..LrSchedulerLab.STEPS.toFloat(), 0f..0.4f,
        ),
    )
    val constAvg = LrSchedulerLab.constantTailAvg
    val cosAvg = LrSchedulerLab.cosineTailAvg
    val lrRatioSquared = (LrSchedulerLab.PERTURBED_LR_HIGH / LrSchedulerLab.PERTURBED_LR_LOW) *
        (LrSchedulerLab.PERTURBED_LR_HIGH / LrSchedulerLab.PERTURBED_LR_LOW)
    frames += NetFrame(
        status = "Add a fixed disturbance every step instead -- standing in for gradient noise -- and the story " +
            "flips: a rate decayed from ${"%.2f".format(LrSchedulerLab.PERTURBED_LR_HIGH)} to " +
            "${"%.2f".format(LrSchedulerLab.PERTURBED_LR_LOW)} shrinks the steady-state loss floor to " +
            "${"%.6f".format(cosAvg)}, against a constant rate's ${"%.6f".format(constAvg)} -- a " +
            "${"%.1f".format(constAvg / cosAvg)}x reduction, close to the (lr ratio)² = ${"%.1f".format(lrRatioSquared)} the " +
            "floor's own scaling law predicts.",
        readout = "noise floor: constant ${"%.6f".format(constAvg)} vs decayed ${"%.6f".format(cosAvg)}",
    )
    return frames
}

private fun crossEntropyLossFrames(): List<NetFrame> {
    val correct = CrossEntropyLab.confidentCorrect
    val wrong = CrossEntropyLab.misclassified

    return listOf(
        NetFrame(
            status = "Logits (2.0, 1.0, 0.1); softmax turns them into p = (${"%.3f".format(correct.p[0])}, " +
                "${"%.3f".format(correct.p[1])}, ${"%.3f".format(correct.p[2])}). True class 0, the model's own " +
                "favorite: loss ${"%.4f".format(correct.loss)}.",
            bars = listOf(NetBar("softmax probabilities", correct.p.map { it.toFloat() }, ForwardColor, listOf("class 0", "class 1", "class 2"))),
        ),
        NetFrame(
            status = "The gradient dL/dz by the clean formula p−y, against the same gradient computed the long " +
                "way through softmax's own Jacobian: max difference ${"%.2e".format(correct.maxGradDiff)} -- the " +
                "identity, not an approximation, which is why the combination is used everywhere instead of " +
                "computing that Jacobian at every step.",
            bars = listOf(
                NetBar("p − y (direct)", correct.gradDirect.map { it.toFloat() }, ForwardColor, listOf("z0", "z1", "z2")),
                NetBar("via Jacobian", correct.gradViaJacobian.map { it.toFloat() }, AccentB, listOf("z0", "z1", "z2")),
            ),
        ),
        NetFrame(
            status = "Same logits, true class 2 instead -- the one the model likes least. Loss jumps to " +
                "${"%.4f".format(wrong.loss)}, ${"%.2f".format(wrong.loss / correct.loss)}x higher, for an identical " +
                "prediction that just happened to be pointed the wrong way.",
            bars = listOf(NetBar("loss", listOf(correct.loss.toFloat(), wrong.loss.toFloat()), BackwardColor, listOf("true class 0", "true class 2"))),
        ),
    )
}

private fun klNormalPdf(x: Float, mu: Float, sigma: Float): Float {
    val z = (x - mu) / sigma
    return (1f / (sigma * sqrt(2f * Math.PI.toFloat()))) * exp(-0.5f * z * z)
}

private fun klBimodalPdf(x: Float): Float = 0.5f * klNormalPdf(x, -2.5f, 0.8f) + 0.5f * klNormalPdf(x, 2.5f, 0.8f)

private fun klDivergenceFrames(): List<NetFrame> {
    val targetCurve = Curve("P (target)", sample(-6f..6f, 80) { klBimodalPdf(it) }, ForwardColor)
    val forward = KlDivergenceLab.forwardFit
    val reverse = KlDivergenceLab.reverseFit
    val yHi = 0.6f

    return listOf(
        NetFrame(
            status = "P=(0.5, 0.3, 0.15, 0.05), Q=uniform. KL(P‖Q)=${"%.4f".format(KlDivergenceLab.klPQ)}, " +
                "KL(Q‖P)=${"%.4f".format(KlDivergenceLab.klQP)} -- same two distributions, order swapped, different " +
                "number. Not a distance.",
            bars = listOf(NetBar("KL divergence", listOf(KlDivergenceLab.klPQ.toFloat(), KlDivergenceLab.klQP.toFloat()), ForwardColor, listOf("P‖Q", "Q‖P"))),
        ),
        NetFrame(
            status = "And the identity cross-entropy(P,Q) = entropy(P) + KL(P‖Q): ${"%.4f".format(KlDivergenceLab.entropyP)} " +
                "+ ${"%.4f".format(KlDivergenceLab.klPQ)} = ${"%.4f".format(KlDivergenceLab.entropyP + KlDivergenceLab.klPQ)}, matching " +
                "cross-entropy computed directly: ${"%.4f".format(KlDivergenceLab.crossEntropyPQ)}.",
            readout = "H(P) + KL(P‖Q) = ${"%.4f".format(KlDivergenceLab.entropyP + KlDivergenceLab.klPQ)} — matches CE(P,Q) exactly",
        ),
        NetFrame(
            status = "A bimodal target, fit by a single Gaussian, grid-searched in each direction. Forward " +
                "KL(P‖Q) is minimized by μ=${"%.1f".format(forward.mu)}, σ=${"%.1f".format(forward.sigma)} -- wide, covering " +
                "both modes at once instead of committing to either.",
            plot = CurvePlot(
                "target vs forward-KL fit",
                listOf(targetCurve, Curve("forward-KL fit", sample(-6f..6f, 80) { klNormalPdf(it, forward.mu.toFloat(), forward.sigma.toFloat()) }, AccentB)),
                -6f..6f, 0f..yHi,
            ),
        ),
        NetFrame(
            status = "Reverse KL(Q‖P) is minimized by μ=${"%.1f".format(reverse.mu)}, σ=${"%.1f".format(reverse.sigma)} instead " +
                "-- narrow, locked onto a single mode exactly -- and it costs more reverse-KL (${"%.4f".format(reverse.divergence)}) " +
                "than the forward fit costs forward-KL (${"%.4f".format(forward.divergence)}), even though it looks like the tighter fit.",
            plot = CurvePlot(
                "target vs reverse-KL fit",
                listOf(targetCurve, Curve("reverse-KL fit", sample(-6f..6f, 80) { klNormalPdf(it, reverse.mu.toFloat(), reverse.sigma.toFloat()) }, BackwardColor)),
                -6f..6f, 0f..yHi,
            ),
        ),
    )
}

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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NeuralNetSection(topicId: String) {
    val config = remember(topicId) { netConfigFor(topicId) }
    val frames = remember(config) { config.build() }
    val playback = rememberPlaybackState(key = config, stepCount = frames.size, initialSpeedMs = 850f)
    val frame = frames[playback.index.coerceIn(0, frames.lastIndex)]

    // Laid out like the other redesigned labs (docs/ios-design/Simulations iOS.html): the intro above
    // the card; the network, plot, grids, bars and legend in it; the readout as chips and the step's
    // narration under it. A frame draws only the panels it carries, so the first one opens the card.
    Column(modifier = Modifier.fillMaxWidth()) {
        LabIntro(config.intro, Modifier.padding(bottom = 12.dp))

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (frame.layers.isNotEmpty()) LayerDiagram(frame.layers)
                frame.plot?.let { PlotCanvas(it) }
                frame.grids.forEach { grid -> MatrixView(grid) }
                frame.bars.forEach { bar -> NetBars(bar) }

                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    config.legend.forEach { (color, label) -> NetLegend(color, label) }
                }
            }
        }

        frame.readout?.let { ReadoutChips(it, Modifier.padding(top = 16.dp)) }
        LabNarration(frame.status, Modifier.padding(top = 16.dp))

        PlaybackTransport(playback, captions = frames.map { it.status })
    }
}

// Square swatches at the size the other redesigned labs use.
@Composable
private fun NetLegend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(11.dp).background(color, RoundedCornerShape(3.dp)))
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 7.dp),
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
    val surface = MaterialTheme.colorScheme.surface
    val onSurface = MaterialTheme.colorScheme.onSurface
    val idleFill = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.16f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
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
                            strokeWidth = 1.5.dp.toPx(),
                        )
                    }
                }
            }

            layers.forEachIndexed { l, layer ->
                layer.nodes.forEachIndexed { i, node ->
                    val center = centerOf(l, i, layer.nodes.size)
                    // An idle neuron is flat grey with dark text, like an idle node in the tree and call
                    // tree labs; only neurons the step is about take a colour. The opaque base keeps
                    // the edges from showing through the translucent grey.
                    val idle = node.mood == NodeMood.IDLE
                    drawCircle(color = surface, radius = radius, center = center)
                    drawCircle(color = if (idle) idleFill else moodColor(node.mood), radius = radius, center = center)
                    val text = measurer.measure("%.2f".format(node.value), valueStyle)
                    drawText(
                        text,
                        color = if (idle) onSurface else Color.White,
                        topLeft = Offset(center.x - text.size.width / 2f, center.y - text.size.height / 2f),
                    )
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
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
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
                    drawLine(axisColor, Offset(0f, zero.y), Offset(size.width, zero.y), strokeWidth = 1.5.dp.toPx())
                }
                if (0f in plot.xRange) {
                    val zero = place(0f, plot.yRange.start)
                    drawLine(axisColor, Offset(zero.x, 0f), Offset(zero.x, size.height), strokeWidth = 1.5.dp.toPx())
                }

                plot.curves.forEach { curve ->
                    if (curve.points.size == 1) {
                        val p = curve.points.first()
                        drawCircle(curve.color, radius = 7.dp.toPx(), center = place(p.first, p.second))
                        return@forEach
                    }
                    curve.points.zipWithNext { a, b ->
                        drawLine(
                            color = curve.color,
                            start = place(a.first, a.second),
                            end = place(b.first, b.second),
                            strokeWidth = 4.dp.toPx(),
                        )
                    }
                    // Trajectories read better with their endpoint marked.
                    val last = curve.points.last()
                    drawCircle(curve.color, radius = 5.dp.toPx(), center = place(last.first, last.second))
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
                                        style = Stroke(width = 4.dp.toPx()),
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
            drawLine(axis, Offset(0f, mid), Offset(size.width, mid), strokeWidth = 1.5.dp.toPx())
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
