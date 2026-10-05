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

private val hiddenBias = listOf(0.1f, -0.2f, 0.05f)
private val target = 1f

private class Pass(
    val hiddenPre: List<Float>,
    val hidden: List<Float>,
    val outputPre: Float,
    val output: Float,
    val loss: Float,
)

// ── Neural network basics ────────────────────────────────────────────────────

// ── Backpropagation ──────────────────────────────────────────────────────────

// ── Activation functions ─────────────────────────────────────────────────────

private fun Float.pow(n: Int): Float {
    var result = 1f
    repeat(n) { result *= this }
    return result
}

// ── Gradient descent variants ────────────────────────────────────────────────

private class Optimizer(val name: String, val color: Color, val step: (List<Float>, List<Float>, Int) -> List<Float>)

// ── CNN ──────────────────────────────────────────────────────────────────────

// ── Autoencoder ──────────────────────────────────────────────────────────────

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

// ── LSTM / GRU ───────────────────────────────────────────────────────────────

// ── C5 · BPTT ────────────────────────────────────────────────────────────────
// Everything here comes out of `BpttLab`, which trains the same 12-unit recurrent classifier at six
// truncation windows on a task with one informative token nine steps before the decision. The lab
// ends on a comparison the plan did not expect to have to draw: a window that never reaches the cue
// solves the task on every seed, and two shorter ones are coin flips.

// ── Config ───────────────────────────────────────────────────────────────────

// ── Batch normalization ──────────────────────────────────────────────────────

// ── Dropout ──────────────────────────────────────────────────────────────────

// ── Transfer learning ────────────────────────────────────────────────────────

// ── Neural network basics (phase 9, batch C1) ────────────────────────────────
// Four labs on the same widget: a leaky integrate-and-fire neuron beside the artificial unit that
// abstracts it, a real MLP trained on XOR, and the two failure modes of depth. Everything is
// computed in DeepNetMath.kt by an actual forward and backward pass — the gradient decay in
// particular is measured rather than derived from a decay formula, and `DeepNetMathTest` pins the
// orderings the narration depends on.

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

private val netConfigs = mapOf(
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
    // ── C8 · Optimizers & Training ────────────────────────────────────────────
)

// ── C9 · Regularization + Specialized ────────────────────────────────────────

// ── B10 · Restricted Boltzmann Machines + Deep Belief Networks ──────────────

// ── C8 · Optimizers & Training ────────────────────────────────────────────────

private fun netConfigFor(topicId: String): NetConfig =
    netConfigs[topicId] ?: netConfigs.getValue("feed_forward")

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
