package com.algora.app.feature.topics

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.algora.app.core.ui.theme.IBMPlexMono
import com.algora.app.core.ui.theme.SimColors
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.sqrt
import kotlin.math.tanh

// ── Neural network storyboards ───────────────────────────────────────────────
// RBM, Deep Belief Networks, Batch Normalization, Backpropagation, Autoencoders, Dropout, Activation
// Functions, MLP and Gradient Descent Variants as step-by-step storyboards: one figure in the card (a
// layered net, two bar panels, a curve plot, or XOR's two planes over its truth table), the arithmetic of
// the step in a formula strip, a legend of what is on screen, then chips and a headline. Every number
// is computed from the small fixed weights and inputs below.

internal val neuralStoryTopicIds = setOf(
    "restricted_boltzmann_machines",
    "deep_belief_networks",
    "batch_normalization",
    "backpropagation",
    "autoencoders",
    "dropout",
    "activation_functions",
    "mlp",
    "gradient_descent_variants",
)

/** Current is the yellow "being computed"; Off a silent unit (ring); Ghost a slot not filled yet (dashed). */
private enum class NnTone { Blue, Green, Violet, Current, Off, Ghost, Dropped, Warn, Band }

private enum class NnEdgeTone { Faint, Blue, Green, Red, RedDashed, Current }

private class NnBadge(val text: String, val warn: Boolean)

private class NnNode(
    val x: Float,
    val y: Float,
    val text: String,
    val tone: NnTone,
    val r: Float,
    val badge: NnBadge? = null,
    // Muted text to the node's right (a dropped unit's value).
    val side: String? = null,
)

private class NnEdge(val from: Int, val to: Int, val tone: NnEdgeTone, val label: String? = null)

/** A shaded funnel between two columns (an autoencoder's encoder and decoder). */
private class NnBand(val x0: Float, val top0: Float, val bottom0: Float, val x1: Float, val top1: Float, val bottom1: Float)

private class NnLabel(val text: String, val align: TextAlign)

private class NnPill(val text: String, val tone: NnTone)

private sealed interface NnScene

private class NetScene(
    val height: Int,
    val nodes: List<NnNode>,
    val edges: List<NnEdge>,
    val headers: List<NnLabel> = emptyList(),
    val footers: List<NnLabel> = emptyList(),
    val pills: List<NnPill> = emptyList(),
    val bands: List<NnBand> = emptyList(),
) : NnScene

/** A null value is a slot still to fill, drawn dashed on the zero line. */
private class NnBar(val value: Double?, val tone: NnTone)

private class BarsScene(
    val raw: List<NnBar>,
    val rawMax: Double,
    // μ and σ: a band from μ − σ to μ + σ with a dashed line at μ.
    val band: Pair<Double, Double>?,
    val lowerLabel: String,
    val lower: List<NnBar>,
    val lowerScale: Double,
) : NnScene

private class NnCurve(val segments: List<List<Pair<Double, Double>>>, val tone: NnTone)

private class NnDot(val x: Double, val y: Double, val tone: NnTone, val hollow: Boolean = false, val label: String? = null)

private class PlotScene(
    val height: Int,
    val xRange: ClosedFloatingPointRange<Double>,
    val yRange: ClosedFloatingPointRange<Double>,
    val curves: List<NnCurve>,
    val dots: List<NnDot> = emptyList(),
    val axes: Boolean = true,
    val refY: Double? = null,
    val probe: Pair<Double, String>? = null,
    // Loss contours as ellipses around the origin: half-width along x, and x-to-y half-width ratio.
    val contours: List<Double> = emptyList(),
    val contourAspect: Double = 1.0,
    val header: String? = null,
) : NnScene

private class XorPoint(val x: Int, val y: Int, val filled: Boolean, val ring: Boolean = false)

private enum class XorCellTone { Idle, Row, One, Current, Empty }

private class XorScene(
    val input: List<XorPoint>,
    val hidden: List<XorPoint>,
    val inputLine: Boolean,
    val hiddenLine: Boolean,
    val rows: List<List<Pair<String, XorCellTone>>>,
) : NnScene

private class NnChip(val key: String, val value: String, val dot: NnTone? = null, val tone: StoryTone = StoryTone.Idle)

private class NnFrame(
    val headline: String,
    val body: String,
    val scene: NnScene,
    val formula: String? = null,
    val legend: List<Pair<NnTone, String>> = emptyList(),
    val chips: List<NnChip> = emptyList(),
)

private class NnTab(val label: String, val frames: List<NnFrame>)

// ── Shared math and formatting ──

private fun sigmoid(z: Double) = 1.0 / (1.0 + exp(-z))

/** Fixed decimals, rounded half away from zero the same way on both platforms; a minus is "−". */
private fun fx(v: Double, d: Int = 2): String {
    var p = 1L
    repeat(d) { p *= 10 }
    val r = floor(abs(v) * p + 0.5).toLong()
    val body = if (d == 0) "${r / p}" else "${r / p}." + (r % p).toString().padStart(d, '0')
    return if (v < 0 && r != 0L) "−$body" else body
}

/** A weight as written: 0.9, −0.72, 1.2. */
private fun wt(v: Double): String = fx(v, 2).trimEnd('0').trimEnd('.')

/** Terms joined as a sum: "0.9 − 0.4 + 0.3", each positive term optionally wrapped ("{p:0.9}"). */
private fun sumText(terms: List<Double>, mark: (String) -> String = { it }): String =
    terms.mapIndexed { i, t ->
        val a = mark(wt(abs(t)))
        when {
            i == 0 -> if (t < 0) "−$a" else a
            t < 0 -> " − $a"
            else -> " + $a"
        }
    }.joinToString("")

private fun vec(values: List<Double>, d: Int = 2) = values.joinToString(", ", "[", "]") { fx(it, d) }

private val subDigits = "₀₁₂₃₄₅₆₇₈₉"
private fun sub(n: Int) = n.toString().map { subDigits[it - '0'] }.joinToString("")

private fun spread(n: Int, top: Float = 0f, bottom: Float = 1f) =
    List(n) { i -> if (n == 1) (top + bottom) / 2 else top + (bottom - top) * i / (n - 1) }

// ── Restricted Boltzmann Machine ──

private val rbmW = listOf(
    listOf(0.9, -0.6, 0.2),
    listOf(-0.4, 0.8, -0.9),
    listOf(0.5, -0.7, 0.4),
    listOf(-0.8, -0.3, 0.6),
    listOf(-0.2, -0.9, 0.7),
    listOf(0.3, 1.1, -0.5),
)
private val rbmA = listOf(0.1, 0.2, -0.3, -0.2, -0.1, 0.0)
private val rbmB = listOf(-0.72, -0.3, 0.1)
private val rbmV = listOf(1, 1, 0, 0, 0, 1)
private val rbmU = listOf(0.31, 0.64, 0.58)

private fun rbmScene(
    visible: List<Pair<String, NnTone>>,
    hidden: List<Pair<String, NnTone>>,
    lit: (Int, Int) -> NnEdgeTone?,
): NetScene {
    val vy = spread(6)
    val hy = spread(3, 0.12f, 0.88f)
    val nodes = visible.mapIndexed { i, (t, tone) -> NnNode(0.1f, vy[i], t, tone, 17f) } +
        hidden.mapIndexed { j, (t, tone) -> NnNode(0.9f, hy[j], t, tone, 20f) }
    val edges = (0 until 6).flatMap { i -> (0 until 3).map { j -> NnEdge(i, 6 + j, lit(i, j) ?: NnEdgeTone.Faint) } }
    return NetScene(
        250, nodes, edges,
        headers = listOf(NnLabel("VISIBLE v · 6", TextAlign.Start), NnLabel("HIDDEN h · 3", TextAlign.End)),
    )
}

private fun rbmTabs(): List<NnTab> {
    val v = rbmV.map { it.toDouble() }
    val pre = (0 until 3).map { j -> rbmB[j] + (0 until 6).sumOf { i -> rbmW[i][j] * v[i] } }
    val ph = pre.map(::sigmoid)
    val h = (0 until 3).map { j -> if (rbmU[j] < ph[j]) 1 else 0 }
    val pv = (0 until 6).map { i -> sigmoid(rbmA[i] + (0 until 3).sumOf { j -> rbmW[i][j] * h[j] }) }
    val ph2 = (0 until 3).map { j -> sigmoid(rbmB[j] + (0 until 6).sumOf { i -> rbmW[i][j] * pv[i] }) }
    val err = (0 until 6).sumOf { (v[it] - pv[it]).let { d -> d * d } } / 6
    val on = (0 until 6).filter { rbmV[it] == 1 }
    val hOn = (0 until 3).filter { h[it] == 1 }
    val visibleData = rbmV.map { "$it" to if (it == 1) NnTone.Blue else NnTone.Off }
    val hiddenSample = h.map { "$it" to if (it == 1) NnTone.Blue else NnTone.Off }
    val ghosts3 = List(3) { "" to NnTone.Ghost }
    val ghosts6 = List(6) { "" to NnTone.Ghost }
    val names = listOf("one", "two", "three", "four", "five", "six")

    val up = mutableListOf(
        NnFrame(
            "The visible layer holds one training example: {p:six binary units}.",
            "Every visible unit links to every hidden unit and to nothing in its own layer: 18 weights in all.",
            rbmScene(visibleData, ghosts3) { _, _ -> null },
            "v = {p:${rbmV.joinToString(", ", "[", "]")}}",
            listOf(NnTone.Blue to "On", NnTone.Off to "Off", NnTone.Ghost to "Not computed"),
        ),
    )
    val upHeads = listOf(
        "Hidden unit 1 reads the {p:${names[on.size - 1]} visible units} that are on. Nothing else feeds it.",
        "Hidden unit 2 reads the same ${names[on.size - 1]} units through its own weights: {${fx(ph[1])}}.",
        "Hidden unit 3's weights on those units are mostly negative, so p falls to {${fx(ph[2])}}.",
    )
    val upBodies = listOf(
        "No links inside a layer, so every hidden unit is one sigmoid and the whole layer samples in parallel.",
        "The wiring is the same for every hidden unit. The weights decide what each one detects.",
        "Its bias of ${wt(rbmB[2])} barely matters against a ${wt(pre[2] - rbmB[2])} pull from the weights.",
    )
    for (j in 0 until 3) {
        val hidden = (0 until 3).map { k ->
            when {
                k < j -> fx(ph[k]) to NnTone.Violet
                k == j -> fx(ph[k]) to NnTone.Current
                else -> "" to NnTone.Ghost
            }
        }
        val terms = sumText(on.map { rbmW[it][j] }) { "{p:$it}" }
        up += NnFrame(
            upHeads[j], upBodies[j],
            rbmScene(visibleData, hidden) { i, k -> if (k == j && rbmV[i] == 1) NnEdgeTone.Blue else null },
            "p(h${sub(j + 1)}|v) = σ( $terms ${if (rbmB[j] < 0) "−" else "+"} ${wt(abs(rbmB[j]))} ) = σ(${fx(pre[j])}) = {${fx(ph[j])}}",
            listOfNotNull(
                NnTone.Current to "Current",
                NnTone.Blue to "On · feeds h${sub(j + 1)}",
                NnTone.Off to "Off",
                if (j > 0) NnTone.Violet to "p(h|v)" else null,
            ),
        )
    }
    val offUnit = (0 until 3).firstOrNull { h[it] == 0 }
    up += NnFrame(
        "Each probability becomes a coin flip: {p:h = ${h.joinToString(", ", "[", "]")}}.",
        offUnit?.let { "Unit ${it + 1} drew ${fx(rbmU[it])}, above its ${fx(ph[it])}, so it stays off. The next half-step reads these 0s and 1s." }
            ?: "Every unit drew below its probability. The next half-step reads these 0s and 1s.",
        rbmScene(visibleData, hiddenSample) { _, _ -> null },
        "u = ${vec(rbmU)} → h = {p:${h.joinToString(", ", "[", "]")}}",
        listOf(NnTone.Blue to "On", NnTone.Off to "Off"),
    )
    up += NnFrame(
        "One matrix product does the whole layer: {v:p(h|v) = σ(Wᵀv + b)}.",
        "Hidden units never talk to each other, so they are independent given v. That is the \"restricted\" in RBM.",
        rbmScene(visibleData, ph.map { fx(it) to NnTone.Violet }) { i, _ -> if (rbmV[i] == 1) NnEdgeTone.Blue else null },
        "σ(Wᵀv + b) = {v:${vec(ph)}}",
        listOf(NnTone.Blue to "On", NnTone.Off to "Off", NnTone.Violet to "p(h|v)"),
    )

    val downTerms = sumText(hOn.map { rbmW[0][it] }) { "{p:$it}" }
    val preV0 = rbmA[0] + hOn.sumOf { rbmW[0][it] }
    val recon = pv.map { fx(it) to NnTone.Violet }
    val down = listOf(
        NnFrame(
            "Now run it backwards: the sample {p:h = ${h.joinToString(", ", "[", "]")}} rebuilds the visible layer.",
            "The same 18 weights are used, transposed. There is no separate decoder.",
            rbmScene(ghosts6, hiddenSample) { _, _ -> null },
            "h = {p:${h.joinToString(", ", "[", "]")}}",
            listOf(NnTone.Blue to "On", NnTone.Off to "Off", NnTone.Ghost to "Not computed"),
        ),
        NnFrame(
            "Visible unit 1 reads the hidden units that are on: {${fx(pv[0])}}.",
            "It was ${rbmV[0]} in the data, so a value ${if (rbmV[0] == 1) "above" else "below"} 0.5 is the right direction.",
            rbmScene(listOf(fx(pv[0]) to NnTone.Current) + ghosts6.drop(1), hiddenSample) { i, j ->
                if (i == 0 && h[j] == 1) NnEdgeTone.Blue else null
            },
            "p(v₁|h) = σ( $downTerms ${if (rbmA[0] < 0) "−" else "+"} ${wt(abs(rbmA[0]))} ) = σ(${fx(preV0)}) = {${fx(pv[0])}}",
            listOf(NnTone.Current to "Current", NnTone.Blue to "On · feeds v₁", NnTone.Off to "Off"),
        ),
        NnFrame(
            "The reconstruction {v:v′} leans the right way on all six units.",
            "Error so far: mean((v − v′)²) = ${fx(err)}. Training exists to push this down.",
            rbmScene(recon, hiddenSample) { _, _ -> null },
            "v′ = {v:${vec(pv)}}",
            listOf(NnTone.Violet to "Reconstruction", NnTone.Blue to "On", NnTone.Off to "Off"),
        ),
        NnFrame(
            "One more upward pass gives the {v:negative phase}: p(h|v′).",
            "Contrastive divergence stops after this single step (CD-1) instead of running the chain to equilibrium.",
            rbmScene(recon, ph2.map { fx(it) to NnTone.Violet }) { _, _ -> null },
            "p(h|v′) = {v:${vec(ph2)}}",
            listOf(NnTone.Violet to "Model's own values"),
        ),
        NnFrame(
            "Each weight moves by {data minus reconstruction}.",
            "w₁₁ grows because v₁ and h₁ were on together more in the data than in the model's own reconstruction.",
            rbmScene(listOf(fx(pv[0]) to NnTone.Current) + recon.drop(1), listOf(fx(ph2[0]) to NnTone.Current) + ph2.drop(1).map { fx(it) to NnTone.Violet }) { i, j ->
                if (i == 0 && j == 0) NnEdgeTone.Current else null
            },
            "Δw₁₁ = 0.1 × (${rbmV[0]}·${fx(ph[0])} − ${fx(pv[0])}·${fx(ph2[0])}) = {${if (0.1 * (v[0] * ph[0] - pv[0] * ph2[0]) >= 0) "+" else ""}${fx(0.1 * (v[0] * ph[0] - pv[0] * ph2[0]))}}",
            listOf(NnTone.Current to "Weight being updated", NnTone.Violet to "Reconstruction"),
        ),
    )
    return listOf(NnTab("v → h", up), NnTab("h → v", down))
}

// ── Deep Belief Network ──

private val dbnW1 = listOf(
    listOf(-1.5, 2.6, -1.4),
    listOf(-1.6, 2.4, -1.7),
    listOf(-1.5, 2.2, -1.5),
    listOf(2.3, -2.5, -2.0),
    listOf(2.4, -2.4, -2.1),
    listOf(2.2, -2.6, -2.2),
)
private val dbnW2 = listOf(listOf(-2.0, 2.9, -0.3), listOf(2.4, -2.2, 0.5))
private val dbnB2 = listOf(-0.9, -0.4)
private val dbnUntrained = listOf(-0.08, 0.2, 0.04)

private fun dbnScene(
    v: List<Int>,
    h1: List<Pair<String, NnTone>>,
    h2: List<Pair<String, NnTone>>,
    pills: List<NnPill>,
    lower: (Int, Int) -> NnEdgeTone?,
    upper: (Int, Int) -> NnEdgeTone?,
    frozen: Boolean,
): NetScene {
    val vy = spread(6)
    val h1y = spread(3, 0.12f, 0.88f)
    val h2y = listOf(0.3f, 0.62f)
    val vTone = { bit: Int -> if (frozen) NnTone.Green else if (bit == 1) NnTone.Blue else NnTone.Off }
    val nodes = v.mapIndexed { i, bit -> NnNode(0.06f, vy[i], "$bit", vTone(bit), 15f) } +
        h1.mapIndexed { j, (t, tone) -> NnNode(0.5f, h1y[j], t, tone, 19f) } +
        h2.mapIndexed { k, (t, tone) -> NnNode(0.92f, h2y[k], t, tone, 19f) }
    val edges = (0 until 6).flatMap { i -> (0 until 3).map { j -> NnEdge(i, 6 + j, lower(i, j) ?: NnEdgeTone.Faint) } } +
        (0 until 3).flatMap { j -> (0 until 2).mapNotNull { k -> upper(j, k)?.let { NnEdge(6 + j, 9 + k, it) } } }
    return NetScene(
        220, nodes, edges,
        footers = listOf(NnLabel("v · 6", TextAlign.Start), NnLabel("h¹ · 3", TextAlign.Center), NnLabel("h² · 2", TextAlign.End)),
        pills = pills,
    )
}

private fun dbnTabs(): List<NnTab> {
    val a = listOf(1, 1, 1, 0, 0, 0)
    val b = listOf(0, 0, 0, 1, 1, 1)
    fun h1(v: List<Int>) = (0 until 3).map { j -> sigmoid((0 until 6).sumOf { i -> dbnW1[i][j] * v[i] }) }
    fun h2(h: List<Double>) = (0 until 2).map { k -> sigmoid(dbnB2[k] + (0 until 3).sumOf { j -> dbnW2[k][j] * h[j] }) }
    val raw = dbnUntrained.map(::sigmoid)
    val ha = h1(a)
    val hb = h1(b)
    val topA = h2(ha)
    val topB = h2(hb)
    val strongest = ha.indices.maxBy { ha[it] }
    val ghosts2 = List(2) { "" to NnTone.Ghost }
    val frames = listOf(
        NnFrame(
            "Greedy step 1: train {p:RBM 1} on the raw data, exactly like a lone RBM.",
            "At its random starting weights every hidden unit sits near 0.5. It has learned nothing yet.",
            dbnScene(
                a, raw.map { fx(it) to NnTone.Blue }, ghosts2,
                listOf(NnPill("RBM 1 · training", NnTone.Blue), NnPill("RBM 2 · waiting", NnTone.Off)),
                { _, _ -> NnEdgeTone.Blue }, { _, _ -> null }, frozen = false,
            ),
            "h¹ = σ(W₁ᵀv) = {p:${vec(raw)}}",
            listOf(NnTone.Blue to "Training", NnTone.Off to "Off", NnTone.Ghost to "Not trained yet"),
        ),
        NnFrame(
            "After CD-1 training, hidden unit ${strongest + 1} fires for this pattern: {${fx(ha[strongest])}}.",
            "The other two units have learned other patterns and stay near 0.",
            dbnScene(
                a, ha.mapIndexed { j, p -> fx(p) to if (j == strongest) NnTone.Current else NnTone.Blue }, ghosts2,
                listOf(NnPill("RBM 1 · trained", NnTone.Blue), NnPill("RBM 2 · waiting", NnTone.Off)),
                { i, j -> if (j == strongest && a[i] == 1) NnEdgeTone.Blue else null }, { _, _ -> null }, frozen = false,
            ),
            "h¹ = σ(W₁ᵀv) = [" + ha.mapIndexed { j, p -> if (j == strongest) "{${fx(p)}}" else fx(p) }.joinToString(", ") + "]",
            listOf(NnTone.Current to "Current", NnTone.Blue to "Trained", NnTone.Off to "Off"),
        ),
        NnFrame(
            "RBM 1 is frozen. Its hidden activations are now {the only data} RBM 2 ever sees.",
            "Greedy stacking: each layer learns features of the layer below, one RBM at a time.",
            dbnScene(
                a, ha.map { fx(it) to NnTone.Green }, listOf(fx(topA[0]) to NnTone.Current, "" to NnTone.Ghost),
                listOf(NnPill("RBM 1 · frozen", NnTone.Green), NnPill("RBM 2 · training", NnTone.Blue)),
                { _, _ -> NnEdgeTone.Green }, { _, k -> if (k == 0) NnEdgeTone.Blue else null }, frozen = true,
            ),
            "RBM 2 data = h¹ = [ " + ha.joinToString(" , ") { "{p:${fx(it)}}" } + " ]",
            listOf(NnTone.Current to "Current", NnTone.Blue to "Training", NnTone.Green to "Frozen"),
        ),
        NnFrame(
            "A different class lights {the other top unit}: h² = ${vec(topB)}.",
            "No label was used anywhere. The top layer separates the classes on structure alone, which is why DBNs made good pretraining.",
            dbnScene(
                b, hb.map { fx(it) to NnTone.Green }, listOf(fx(topB[0]) to NnTone.Green, fx(topB[1]) to NnTone.Current),
                listOf(NnPill("RBM 1 · frozen", NnTone.Green), NnPill("RBM 2 · frozen", NnTone.Green)),
                { _, _ -> NnEdgeTone.Green }, { _, k -> if (k == 1) NnEdgeTone.Blue else NnEdgeTone.Green }, frozen = true,
            ),
            "class A → ${vec(topA)}   class B → [${fx(topB[0])}, {${fx(topB[1])}}]",
            listOf(NnTone.Current to "Current", NnTone.Blue to "Feeds it", NnTone.Green to "Frozen"),
        ),
    )
    return listOf(NnTab("", frames))
}

// ── Batch Normalization ──

private val bnX = listOf(6.8, 8.9, 5.9, 10.4, 7.7, 9.0, 6.2, 9.6)
private const val BN_GAMMA = 1.2
private const val BN_BETA = 0.3

private fun bnTabs(): List<NnTab> {
    val n = bnX.size
    val mu = bnX.sum() / n
    val sd = sqrt(bnX.sumOf { (it - mu) * (it - mu) } / n)
    val xh = bnX.map { (it - mu) / sd }
    val y = xh.map { BN_GAMMA * it + BN_BETA }
    val focus = 3
    val rawMax = 11.5
    fun raw(current: Int? = null) = bnX.mapIndexed { i, v -> NnBar(v, if (i == current) NnTone.Current else NnTone.Blue) }
    val empty = List(n) { NnBar(null, NnTone.Ghost) }
    val scale = 1.8
    val muChips = listOf(NnChip("μ", fx(mu)), NnChip("σ", fx(sd)))
    val norm = "x̂ = (x − μ) / σ"
    val baseLegend = listOf(NnTone.Current to "Current", NnTone.Blue to "Raw", NnTone.Green to "Normalized", NnTone.Band to "μ ± σ")
    val muRun = 0.9 * 7.5 + 0.1 * mu
    val frames = listOf(
        NnFrame(
            "A mini-batch of eight: {p:one feature} from eight different samples.",
            "The values sit around 8 with a spread of a few units. The next layer would have to adapt to that scale.",
            BarsScene(raw(), rawMax, null, norm, empty, scale),
            "x = {p:[${bnX.joinToString(", ") { fx(it, 1) }}]}",
            listOf(NnTone.Blue to "Raw"),
        ),
        NnFrame(
            "The batch mean is {v:${fx(mu)}} and its spread σ is {v:${fx(sd)}}.",
            "Both are computed across the batch, separately for every feature.",
            BarsScene(raw(), rawMax, mu to sd, norm, empty, scale),
            "μ = Σx / $n = {v:${fx(mu)}}   σ = √(Σ(x − μ)² / $n) = {v:${fx(sd)}}",
            listOf(NnTone.Blue to "Raw", NnTone.Band to "μ ± σ"),
            muChips,
        ),
        NnFrame(
            "Sample ${focus + 1} is ${fx(xh[focus])} σ ${if (xh[focus] >= 0) "above" else "below"} the batch mean, so it normalizes to {${fx(xh[focus])}}.",
            "μ and σ are taken across the batch, per feature. At inference, running averages replace them.",
            BarsScene(
                raw(focus), rawMax, mu to sd, norm,
                xh.mapIndexed { i, v -> if (i < focus) NnBar(v, NnTone.Green) else if (i == focus) NnBar(v, NnTone.Current) else NnBar(null, NnTone.Ghost) },
                scale,
            ),
            "x̂${sub(focus + 1)} = ( {${wt(bnX[focus])}} − {p:${fx(mu)}} ) / {v:${fx(sd)}} = {${fx(xh[focus])}}",
            baseLegend,
            muChips,
        ),
        NnFrame(
            "Now the batch has {m:mean 0 and σ 1}, whatever scale it arrived in.",
            "Add 100 to every input and these eight bars would not move.",
            BarsScene(raw(), rawMax, mu to sd, norm, xh.map { NnBar(it, NnTone.Green) }, scale),
            "mean(x̂) = {m:${fx(xh.sum() / n)}}   std(x̂) = {m:${fx(sqrt(xh.sumOf { it * it } / n))}}",
            listOf(NnTone.Blue to "Raw", NnTone.Green to "Normalized", NnTone.Band to "μ ± σ"),
            muChips,
        ),
        NnFrame(
            "Learned {v:γ and β} rescale it, so the layer can undo the normalization if that helps.",
            "With γ = σ and β = μ the original values come back exactly. Normalizing never removes what the network can express.",
            BarsScene(
                raw(focus), rawMax, mu to sd, "y = γ x̂ + β",
                y.mapIndexed { i, v -> NnBar(v, if (i == focus) NnTone.Current else NnTone.Violet) }, scale,
            ),
            "y${sub(focus + 1)} = {v:${wt(BN_GAMMA)}} × ${fx(xh[focus])} + {v:${wt(BN_BETA)}} = {${fx(y[focus])}}",
            listOf(NnTone.Current to "Current", NnTone.Blue to "Raw", NnTone.Violet to "After γ, β", NnTone.Band to "μ ± σ"),
            listOf(NnChip("γ", wt(BN_GAMMA)), NnChip("β", wt(BN_BETA))),
        ),
        NnFrame(
            "At inference there is no batch. A {running average} of μ and σ stands in.",
            "It is updated on every training step, so one test example is normalized the same way every time.",
            BarsScene(raw(), rawMax, mu to sd, norm, xh.map { NnBar(it, NnTone.Green) }, scale),
            "μ_run ← 0.9 × 7.50 + 0.1 × ${fx(mu)} = {${fx(muRun)}}",
            listOf(NnTone.Blue to "Raw", NnTone.Green to "Normalized", NnTone.Band to "μ ± σ"),
            listOf(NnChip("μ_run", fx(muRun), tone = StoryTone.Active)),
        ),
    )
    return listOf(NnTab("", frames))
}

// ── Backpropagation ──

private val bpX = listOf(0.9, 0.2)
private val bpW1 = listOf(listOf(0.6, 0.4), listOf(-0.5, 0.3), listOf(0.3, 0.2))
private val bpW2 = listOf(1.2, 0.8, 0.6)
private const val BP_B2 = -0.73
private const val BP_TARGET = 0.6
private const val BP_ETA = 0.5

private fun bpScene(
    hidden: List<Pair<String, NnTone>>,
    out: Pair<String, NnTone>,
    badges: List<NnBadge?> = List(4) { null },
    edgeTone: (Int) -> NnEdgeTone,
    edgeLabel: (Int) -> String,
): NetScene {
    val nodes = listOf(
        NnNode(0.1f, 0.3f, fx(bpX[0]), NnTone.Blue, 20f),
        NnNode(0.1f, 0.72f, fx(bpX[1]), NnTone.Blue, 20f),
    ) + hidden.mapIndexed { j, (t, tone) -> NnNode(0.5f, spread(3)[j], t, tone, 20f, badge = badges[j]) } +
        NnNode(0.88f, 0.5f, out.first, out.second, 20f, badge = badges[3])
    val edges = (0 until 2).flatMap { i -> (0 until 3).map { j -> NnEdge(i, 2 + j, NnEdgeTone.Faint) } } +
        (0 until 3).map { j -> NnEdge(2 + j, 5, edgeTone(j), edgeLabel(j)) }
    return NetScene(
        230, nodes, edges,
        headers = listOf(NnLabel("INPUT", TextAlign.Start), NnLabel("HIDDEN · ReLU", TextAlign.Center), NnLabel("OUTPUT", TextAlign.End)),
    )
}

private fun bpTabs(): List<NnTab> {
    val pre = bpW1.map { w -> w[0] * bpX[0] + w[1] * bpX[1] }
    val h = pre.map { maxOf(0.0, it) }
    val z = (0 until 3).sumOf { bpW2[it] * h[it] } + BP_B2
    val out = sigmoid(z)
    val d = out - BP_TARGET
    val dh = (0 until 3).map { j -> if (pre[j] > 0) d * bpW2[j] else 0.0 }
    val grad = h.map { d * it }
    val w2n = (0 until 3).map { bpW2[it] - BP_ETA * grad[it] }
    val b2n = BP_B2 - BP_ETA * d
    val w1n = (0 until 3).map { j -> (0 until 2).map { k -> bpW1[j][k] - BP_ETA * dh[j] * bpX[k] } }
    val h2 = w1n.map { w -> maxOf(0.0, w[0] * bpX[0] + w[1] * bpX[1]) }
    val out2 = sigmoid((0 until 3).sumOf { w2n[it] * h2[it] } + b2n)
    val silent = (0 until 3).first { pre[it] <= 0 }
    fun hid(tones: (Int) -> NnTone = { if (pre[it] > 0) NnTone.Blue else NnTone.Off }) = h.mapIndexed { j, v -> fx(v) to tones(j) }
    val weights = { j: Int -> "w ${wt(bpW2[j])}" }
    val forward = { j: Int -> if (pre[j] > 0) NnEdgeTone.Blue else NnEdgeTone.Faint }
    val dBadge = NnBadge("δ ${fx(d)}", true)
    val hBadges = dh.mapIndexed { j, v -> if (pre[j] > 0) NnBadge("δ ${fx(v)}", true) else NnBadge("δ 0", false) }
    val legend = listOf(NnTone.Blue to "Forward value", NnTone.Violet to "Output", NnTone.Off to "Silent")
    val frames = listOf(
        NnFrame(
            "Forward pass: the network predicts {v:${fx(out)}}. The target is ${fx(BP_TARGET)}.",
            "Hidden unit ${silent + 1}'s weighted sum is ${fx(pre[silent])}, so ReLU outputs exactly 0. Remember that.",
            bpScene(hid(), fx(out) to NnTone.Violet, edgeTone = forward, edgeLabel = weights),
            "ŷ = σ(${(0 until 3).joinToString(" + ") { "${wt(bpW2[it])}·${fx(h[it])}" }} − ${wt(-BP_B2)}) = {v:${fx(out)}}",
            legend,
        ),
        NnFrame(
            "The output is too low by ${fx(-d)}, so its error is {w:δ = ${fx(d)}}.",
            "With a sigmoid output and cross-entropy loss, δ is simply prediction minus target.",
            bpScene(hid(), fx(out) to NnTone.Violet, listOf(null, null, null, dBadge), forward, weights),
            "δₒ = ŷ − y = ${fx(out)} − ${fx(BP_TARGET)} = {w:${fx(d)}}",
            listOf(NnTone.Blue to "Forward value", NnTone.Warn to "Gradient δ", NnTone.Off to "Silent"),
        ),
        NnFrame(
            "The output's error {w:δ = ${fx(d)}} flows back along each weight. Unit 1 gets ${fx(d)} × ${wt(bpW2[0])}.",
            "Unit ${silent + 1} never fired, so ReLU′ is 0 and it receives no gradient at all.",
            bpScene(
                hid { if (it == 0) NnTone.Current else if (pre[it] > 0) NnTone.Blue else NnTone.Off },
                fx(out) to NnTone.Violet, hBadges + dBadge,
                { j -> if (pre[j] > 0) NnEdgeTone.Red else NnEdgeTone.RedDashed }, weights,
            ),
            "δh₁ = {w:${fx(d)}} × w {p:${wt(bpW2[0])}} × ReLU′ {p:1} = {${fx(dh[0])}}",
            listOf(NnTone.Current to "Current", NnTone.Blue to "Forward value", NnTone.Warn to "Gradient δ", NnTone.Off to "Silent"),
        ),
        NnFrame(
            "A weight's gradient is the δ it feeds times the value it carries: {w:${fx(grad[0], 3)}} for w₁.",
            "The weight from the silent unit carried 0, so its gradient is 0 too. Input weights get δh × x the same way.",
            bpScene(
                hid { if (it == 0) NnTone.Current else if (pre[it] > 0) NnTone.Blue else NnTone.Off },
                fx(out) to NnTone.Violet, List(3) { null } + dBadge,
                { j -> if (pre[j] > 0) NnEdgeTone.Red else NnEdgeTone.RedDashed },
                { j -> "∂ ${fx(grad[j], 3)}" },
            ),
            "∂L/∂w₁ = δₒ × h₁ = ${fx(d)} × ${fx(h[0])} = {w:${fx(grad[0], 3)}}",
            listOf(NnTone.Current to "Current", NnTone.Blue to "Forward value", NnTone.Warn to "Gradient", NnTone.Off to "Silent"),
        ),
        NnFrame(
            "One update later the prediction moves from ${fx(out)} to {v:${fx(out2)}}, toward ${fx(BP_TARGET)}.",
            "Every weight stepped against its gradient at η = ${wt(BP_ETA)}. Repeat over many examples and that is training.",
            bpScene(
                h2.mapIndexed { j, v -> fx(v) to if (v > 0) NnTone.Blue else NnTone.Off },
                fx(out2) to NnTone.Current, edgeTone = { j -> if (h2[j] > 0) NnEdgeTone.Blue else NnEdgeTone.Faint },
                edgeLabel = { j -> "w ${fx(w2n[j])}" },
            ),
            "w₁ ← ${wt(bpW2[0])} − ${wt(BP_ETA)} × (${fx(grad[0], 3)}) = {${fx(w2n[0])}}",
            listOf(NnTone.Current to "New prediction", NnTone.Blue to "Forward value", NnTone.Off to "Silent"),
        ),
    )
    return listOf(NnTab("", frames))
}

// ── Autoencoders ──

private val aeX = listOf(0.90, 0.80, 0.60, 0.40, 0.20, 0.10)
private val aeNoise = listOf(0.08, -0.14, 0.11, -0.12, 0.10, -0.07)

/** A linear 6 → 2 → 6 autoencoder at its optimum: encode onto a level and a slope, decode back. */
private fun aeRun(x: List<Double>): Pair<List<Double>, List<Double>> {
    val level = List(6) { 1 / sqrt(6.0) }
    val ramp = listOf(5.0, 3.0, 1.0, -1.0, -3.0, -5.0).map { it / sqrt(70.0) }
    val z = listOf(level, ramp).map { b -> x.indices.sumOf { x[it] * b[it] } }
    return z to x.indices.map { z[0] * level[it] + z[1] * ramp[it] }
}

private fun mse(a: List<Double>, b: List<Double>) = a.indices.sumOf { (a[it] - b[it]) * (a[it] - b[it]) } / a.size

private fun aeScene(
    input: List<Pair<String, NnTone>>,
    code: List<Pair<String, NnTone>>,
    output: List<Pair<String, NnTone>>,
): NetScene {
    val y6 = spread(6)
    val y2 = listOf(0.34f, 0.66f)
    val nodes = input.mapIndexed { i, (t, tone) -> NnNode(0.08f, y6[i], t, tone, 14f) } +
        code.mapIndexed { k, (t, tone) -> NnNode(0.5f, y2[k], t, tone, 20f) } +
        output.mapIndexed { i, (t, tone) -> NnNode(0.92f, y6[i], t, tone, 14f) }
    val edges = (0 until 6).flatMap { i -> (0 until 2).map { k -> NnEdge(i, 6 + k, NnEdgeTone.Faint) } } +
        (0 until 2).flatMap { k -> (0 until 6).map { i -> NnEdge(6 + k, 8 + i, NnEdgeTone.Faint) } }
    return NetScene(
        240, nodes, edges,
        headers = listOf(NnLabel("INPUT x", TextAlign.Start), NnLabel("CODE · 2", TextAlign.Center), NnLabel("OUTPUT x̂", TextAlign.End)),
        bands = listOf(NnBand(0.08f, 0f, 1f, 0.5f, 0.34f, 0.66f), NnBand(0.5f, 0.34f, 0.66f, 0.92f, 0f, 1f)),
    )
}

private fun aeTabs(): List<NnTab> {
    val (z, xh) = aeRun(aeX)
    val loss = mse(aeX, xh)
    val errs = aeX.indices.map { abs(aeX[it] - xh[it]) }
    val worst = errs.indices.maxBy { errs[it] }
    val xt = aeX.indices.map { aeX[it] + aeNoise[it] }
    val (zt, xht) = aeRun(xt)
    val inLoss = mse(xt, aeX)
    val outLoss = mse(xht, aeX)
    val input = aeX.map { fx(it) to NnTone.Blue }
    val noisy = xt.map { fx(it) to NnTone.Warn }
    val ghost2 = List(2) { "" to NnTone.Ghost }
    val ghost6 = List(6) { "" to NnTone.Ghost }
    val code = z.map { fx(it) to NnTone.Violet }
    val out = xh.map { fx(it) to NnTone.Green }
    val legend = listOf(NnTone.Blue to "Input", NnTone.Violet to "Code", NnTone.Green to "Reconstruction")
    val plain = listOf(
        NnFrame(
            "Six numbers go in. The middle layer has room for only {v:two}.",
            "Nothing but the shape forces compression: the output is trained to match the input.",
            aeScene(input, ghost2, ghost6),
            "x = {p:${vec(aeX)}}",
            listOf(NnTone.Blue to "Input", NnTone.Ghost to "Not computed"),
        ),
        NnFrame(
            "The encoder squeezes x into a two-number code, {v:z = ${vec(z)}}.",
            "The first number tracks the overall level, the second the downward slope.",
            aeScene(input, code, ghost6),
            "z = Eᵀx = {v:${vec(z)}}",
            legend.take(2) + (NnTone.Ghost to "Not computed"),
        ),
        NnFrame(
            "Six numbers squeezed through two come back within {m:${fx(errs.max())}} of where they started.",
            "To fit in two units, the encoder has to keep the structure and drop the rest.",
            aeScene(input, code, out),
            "loss = mean((x − x̂)²) = {m:${fx(loss, 4)}}",
            legend,
        ),
        NnFrame(
            "What two numbers cannot hold is lost: unit ${worst + 1} comes back as {${fx(xh[worst])}}, not ${fx(aeX[worst])}.",
            "x is not exactly a straight ramp, and the code only has room for a level and a slope.",
            aeScene(
                input.mapIndexed { i, p -> if (i == worst) p.first to NnTone.Current else p }, code,
                out.mapIndexed { i, p -> if (i == worst) p.first to NnTone.Current else p },
            ),
            "|x${sub(worst + 1)} − x̂${sub(worst + 1)}| = |${fx(aeX[worst])} − ${fx(xh[worst])}| = {${fx(errs[worst])}}",
            listOf(NnTone.Current to "Largest miss") + legend,
        ),
    )
    val noiseMax = aeNoise.maxOf { abs(it) }
    val ratio = inLoss / outLoss
    val denoise = listOf(
        NnFrame(
            "Now corrupt the input: every value is nudged by up to {w:${fx(noiseMax)}}.",
            "The target stays the clean x. The network never sees it at the input.",
            aeScene(noisy, ghost2, ghost6),
            "loss(x̃, x) = mean((x̃ − x)²) = {w:${fx(inLoss, 4)}}",
            listOf(NnTone.Warn to "Corrupted input", NnTone.Ghost to "Not computed"),
        ),
        NnFrame(
            "The code barely moves: {v:${vec(zt)}} against a clean ${vec(z)}.",
            "Noise that doesn't line up with a level or a slope has nowhere to go in two numbers.",
            aeScene(noisy, zt.map { fx(it) to NnTone.Violet }, ghost6),
            "z = Eᵀx̃ = {v:${vec(zt)}}",
            listOf(NnTone.Warn to "Corrupted input", NnTone.Violet to "Code", NnTone.Ghost to "Not computed"),
        ),
        NnFrame(
            "The output lands back near the clean x: the error falls {m:about ${fx(ratio, 0)}×}.",
            "Squeezing through the bottleneck throws away the directions the noise lives in.",
            aeScene(noisy, zt.map { fx(it) to NnTone.Violet }, xht.map { fx(it) to NnTone.Green }),
            "loss(x̂, x) = {m:${fx(outLoss, 4)}}  vs  ${fx(inLoss, 4)} in",
            listOf(NnTone.Warn to "Corrupted input", NnTone.Violet to "Code", NnTone.Green to "Reconstruction"),
        ),
        NnFrame(
            "A denoising autoencoder is trained on exactly this: {w:noisy} in, {m:clean} out.",
            "Learning to undo corruption forces features that describe the data rather than copy the input.",
            aeScene(noisy, zt.map { fx(it) to NnTone.Violet }, aeX.map { fx(it) to NnTone.Green }),
            "minimize mean((x − dec(enc(x̃)))²)",
            listOf(NnTone.Warn to "Corrupted input", NnTone.Violet to "Code", NnTone.Green to "Clean target"),
        ),
    )
    return listOf(NnTab("Plain", plain), NnTab("Denoising", denoise))
}

// ── Dropout ──

private val dropHidden = listOf(0.8, 0.8, 0.4, 1.6, 0.9, 0.2)
private val dropIn = listOf(1.0, 0.6)

/** Three passes' masks per number of dropped units (1 to 3 of 6). */
private val dropMasks = mapOf(
    1 to listOf(setOf(1), setOf(4), setOf(2)),
    2 to listOf(setOf(1, 5), setOf(0, 3), setOf(2, 4)),
    3 to listOf(setOf(1, 3, 5), setOf(0, 2, 4), setOf(0, 1, 5)),
)

internal const val DROPOUT_MIN = 1
internal const val DROPOUT_MAX = 3

private fun dropScene(values: List<Pair<String, NnTone>>, dropped: Set<Int>, sides: List<String?>, out: Pair<String, NnTone>): NetScene {
    val hy = spread(6)
    val nodes = listOf(
        NnNode(0.1f, 0.3f, fx(dropIn[0]), NnTone.Blue, 21f),
        NnNode(0.1f, 0.7f, fx(dropIn[1]), NnTone.Blue, 21f),
    ) + values.mapIndexed { j, (t, tone) -> NnNode(0.5f, hy[j], t, tone, 16f, side = sides[j]) } +
        NnNode(0.88f, 0.5f, out.first, out.second, 21f)
    val edges = (0 until 2).flatMap { i -> (0 until 6).filter { it !in dropped }.map { j -> NnEdge(i, 2 + j, NnEdgeTone.Faint) } } +
        (0 until 6).filter { it !in dropped }.map { j -> NnEdge(2 + j, 8, NnEdgeTone.Blue) }
    return NetScene(
        240, nodes, edges,
        headers = listOf(NnLabel("INPUT", TextAlign.Start), NnLabel("HIDDEN · 6", TextAlign.Center), NnLabel("OUTPUT", TextAlign.End)),
    )
}

private fun dropoutTabs(k: Int): List<NnTab> {
    val kept = 6 - k
    val scale = 6.0 / kept
    val p = "$k/6".let { if (k == 2) "1/3" else if (k == 3) "1/2" else it }
    val expected = dropHidden.sum()
    val words = listOf("None", "One", "Two", "Three", "Four", "Five", "Six")
    val masks = dropMasks.getValue(k)
    fun choose(n: Int, r: Int): Int = if (r == 0) 1 else choose(n - 1, r - 1) * n / r
    val combos = choose(6, k)
    // Every mask with k units off, each kept unit scaled: the mean output is exactly the full sum.
    val allMasks = (0 until 64).filter { Integer.bitCount(it) == k }
    val meanOut = allMasks.sumOf { m -> (0 until 6).filter { (m shr it) and 1 == 0 }.sumOf { dropHidden[it] * scale } } / allMasks.size
    fun pass(mask: Set<Int>, scaled: Boolean): Pair<NetScene, Double> {
        val f = if (scaled) scale else 1.0
        val outV = (0 until 6).filter { it !in mask }.sumOf { dropHidden[it] * f }
        val values = dropHidden.mapIndexed { j, v -> if (j in mask) "" to NnTone.Dropped else fx(v * f) to NnTone.Blue }
        val sides = dropHidden.mapIndexed { j, v -> if (j in mask) fx(v * f) else null }
        return dropScene(values, mask, sides, fx(outV) to NnTone.Violet) to outV
    }
    val full = dropScene(dropHidden.map { fx(it) to NnTone.Blue }, emptySet(), List(6) { null }, fx(expected) to NnTone.Violet)
    val legendFull = listOf(NnTone.Blue to "Active", NnTone.Violet to "Output")
    val legendDrop = listOf(NnTone.Blue to "Kept, scaled", NnTone.Dropped to "Dropped", NnTone.Violet to "Output")
    fun chip(v: Double) = listOf(NnChip("Σ expected → this pass", "${fx(expected)} → ${fx(v)}"))
    val (raw1, rawOut1) = pass(masks[0], scaled = false)
    val passes = masks.map { pass(it, scaled = true) }
    val frames = listOf(
        NnFrame(
            "Without dropout, all {p:six hidden units} feed the output: {v:${fx(expected)}}.",
            "That sum is what the network produces at test time, so training should hit it on average.",
            full, null, legendFull, listOf(NnChip("Σ expected", fx(expected))),
        ),
        NnFrame(
            "Pass 1: a random mask switches {w:${words[k].lowercase()} of six} units off.",
            "Each unit is dropped with probability p = $p, independently, on every training step. Unscaled, the output falls to ${fx(rawOut1)}.",
            raw1, null, listOf(NnTone.Blue to "Kept", NnTone.Dropped to "Dropped", NnTone.Violet to "Output"), chip(rawOut1),
        ),
        NnFrame(
            "{w:${words[k]}} of six units ${if (k == 1) "is" else "are"} off. The ${words[kept].lowercase()} left are scaled by {1/(1 − p) = ${wt(scale)}}.",
            "No unit can rely on a neighbour, so each learns to stand alone.",
            passes[0].first, null, legendDrop, chip(passes[0].second),
        ),
        NnFrame(
            "Pass 2: a {different mask}, a different thinned network: {v:${fx(passes[1].second)}}.",
            "Every step trains one of the $combos sub-networks with $k unit${if (k == 1) "" else "s"} off. They all share the same weights.",
            passes[1].first, null, legendDrop, chip(passes[1].second),
        ),
        NnFrame(
            "Pass 3 gives {v:${fx(passes[2].second)}}. Each pass lands somewhere different.",
            "The noise is the point: the network cannot memorize one exact path through its units.",
            passes[2].first, null, legendDrop, chip(passes[2].second),
        ),
        NnFrame(
            "Averaged over all $combos masks, the scaled output is exactly {v:${fx(meanOut)}}.",
            "Passes of ${passes.joinToString(", ") { fx(it.second) }} scatter around the full network's sum. That is what 1/(1 − p) buys.",
            full, "mean over $combos masks = {v:${fx(meanOut)}} = Σ expected", legendFull,
            listOf(NnChip("mean of $combos masks", fx(meanOut), tone = StoryTone.Answer)),
        ),
        NnFrame(
            "At test time nothing is dropped and {m:nothing is scaled}: ${fx(expected)}.",
            "Training already scaled up the kept units (\"inverted\" dropout), so inference uses the weights as they are.",
            full, null, legendFull, listOf(NnChip("inference", fx(expected), tone = StoryTone.Done)),
        ),
    )
    return listOf(NnTab("", frames))
}

// ── Activation functions ──

private val geluK = sqrt(2 / Math.PI)
private fun gelu(x: Double) = 0.5 * x * (1 + tanh(geluK * (x + 0.044715 * x * x * x)))
private fun geluPrime(x: Double): Double {
    val t = tanh(geluK * (x + 0.044715 * x * x * x))
    return 0.5 * (1 + t) + 0.5 * x * (1 - t * t) * geluK * (1 + 3 * 0.044715 * x * x)
}
private fun tanhPrime(x: Double) = 1 - tanh(x) * tanh(x)

private val actProbes = listOf(-2.0, -0.5, 0.5, 1.5, 3.0)

private fun activationTabs(): List<NnTab> {
    val xs = (0..160).map { -4.0 + it * 0.05 }
    fun curve(f: (Double) -> Double, tone: NnTone) = NnCurve(listOf(xs.map { it to f(it) }), tone)
    val relu = { x: Double -> maxOf(0.0, x) }
    val reluPrime = NnCurve(listOf(xs.filter { it <= 0 }.map { it to 0.0 }, xs.filter { it >= 0 }.map { it to 1.0 }), NnTone.Blue)
    val fnCurves = listOf(curve(relu, NnTone.Blue), curve(::tanh, NnTone.Green), curve(::gelu, NnTone.Violet))
    val dCurves = listOf(reluPrime, curve(::tanhPrime, NnTone.Green), curve(::geluPrime, NnTone.Violet))
    val fnHeads = listOf(
        "At x = −2, ReLU outputs {exactly 0}, and tanh is already near its floor of −1.",
        "GELU dips {below zero} here, to ${fx(gelu(-0.5))}, where ReLU is flat at 0.",
        "Just past zero all three rise: ReLU {${fx(0.5)}}, tanh ${fx(tanh(0.5))}, GELU ${fx(gelu(0.5))}.",
        "At x = 1.5 tanh has bent to {${fx(tanh(1.5))}}, while ReLU keeps going to 1.50.",
        "By x = 3, GELU and ReLU agree at {${fx(gelu(3.0))}} and tanh is pinned at ${fx(tanh(3.0))}.",
    )
    val fnBodies = listOf(
        "A negative input switches a ReLU unit off completely. GELU lets a little through.",
        "That small negative bump makes GELU smooth at zero, which ReLU is not.",
        "Near zero, tanh is almost the identity and GELU is about half of x.",
        "tanh squashes everything into (−1, 1). ReLU passes large values through unchanged.",
        "For large inputs GELU becomes ReLU. The difference lives only near zero.",
    )
    val dHeads = listOf(
        "Below zero, {ReLU′ = 0}: no gradient flows back through a unit that is off.",
        "tanh′ is {${fx(tanhPrime(-0.5))}} here, but ReLU′ is still exactly 0.",
        "Past zero, {ReLU′ = 1}. tanh′ has already fallen to ${fx(tanhPrime(0.5))}.",
        "At x = 1.5, ReLU′ is exactly 1. tanh′ has already dropped to {${fx(tanhPrime(1.5))}}.",
        "At x = 3, tanh′ is {w:${fx(tanhPrime(3.0))}}: a saturated tanh unit has stopped learning.",
    )
    val dBodies = listOf(
        "tanh′ is ${fx(tanhPrime(-2.0))}, and GELU′ is even slightly negative, ${fx(geluPrime(-2.0))}.",
        "A unit whose input stays negative for every example is a dead ReLU: it never updates again.",
        "GELU′ is ${fx(geluPrime(0.5))} and still rising. It overshoots 1 before it settles.",
        "Ten layers of ${fx(tanhPrime(1.5))} multiply the gradient down to almost nothing. ReLU passes it on unchanged.",
        "ReLU′ and GELU′ are both about 1. This is why deep networks moved to ReLU-style activations.",
    )
    fun frames(derivative: Boolean) = actProbes.mapIndexed { i, x ->
        val values = if (derivative) listOf(if (x > 0) 1.0 else 0.0, tanhPrime(x), geluPrime(x)) else listOf(relu(x), tanh(x), gelu(x))
        val tones = listOf(NnTone.Blue, NnTone.Green, NnTone.Violet)
        val names = if (derivative) listOf("ReLU′", "tanh′", "GELU′") else listOf("ReLU", "tanh", "GELU")
        NnFrame(
            if (derivative) dHeads[i] else fnHeads[i],
            if (derivative) dBodies[i] else fnBodies[i],
            PlotScene(
                200, -4.0..4.0, if (derivative) -0.3..1.3 else -1.3..3.3,
                if (derivative) dCurves else fnCurves,
                dots = values.mapIndexed { k, v -> NnDot(x, v, tones[k]) },
                refY = 1.0,
                probe = x to "x = ${wt(x)}",
            ),
            null,
            names.mapIndexed { k, n -> tones[k] to n } + (NnTone.Current to "Probe x"),
            values.mapIndexed { k, v -> NnChip(names[k], fx(v), dot = tones[k]) },
        )
    }
    return listOf(NnTab("Function", frames(false)), NnTab("Derivative", frames(true)))
}

// ── Multi-layer perceptron (XOR) ──

private fun mlpTabs(): List<NnTab> {
    val rows = listOf(0 to 0, 0 to 1, 1 to 0, 1 to 1)
    val or = rows.map { (a, b) -> if (a + b - 0.5 > 0) 1 else 0 }
    val and = rows.map { (a, b) -> if (a + b - 1.5 > 0) 1 else 0 }
    val y = rows.indices.map { if (or[it] == 1 && and[it] == 0) 1 else 0 }
    fun scene(hCols: Int, current: Int?, hidden: Boolean, hiddenLine: Boolean = false, inputLine: Boolean = false): XorScene {
        val table = rows.mapIndexed { r, (a, b) ->
            val base = if (r == current) XorCellTone.Row else XorCellTone.Idle
            listOf(
                "$a" to base,
                "$b" to base,
                if (hCols >= 1) "${or[r]}" to base else "" to XorCellTone.Empty,
                if (hCols >= 2) "${and[r]}" to base else "" to XorCellTone.Empty,
                "${y[r]}" to when {
                    r == current -> XorCellTone.Current
                    y[r] == 1 -> XorCellTone.One
                    else -> XorCellTone.Idle
                },
            )
        }
        val input = rows.mapIndexed { r, (a, b) -> XorPoint(a, b, y[r] == 1, r == current) }
        val hid = if (hidden) rows.indices.map { r -> XorPoint(or[r], and[r], y[r] == 1, r == current) } else emptyList()
        return XorScene(input, hid, inputLine, hiddenLine, table)
    }
    val legend = listOf(NnTone.Current to "Current", NnTone.Violet to "y = 1", NnTone.Off to "y = 0")
    val rule = { r: Int -> "y = h₁ AND NOT h₂ = {p:${or[r]}} AND NOT {p:${and[r]}} = {${y[r]}}" }
    val frames = listOf(
        NnFrame(
            "XOR is 1 when {v:exactly one} input is 1.",
            "Plot the four rows: the two 1s sit on opposite corners of the square.",
            scene(0, null, hidden = false), null, legend.drop(1),
        ),
        NnFrame(
            "{w:No single line} separates the violet corners from the hollow ones.",
            "One unit draws one line, so a lone perceptron cannot learn XOR. A hidden layer can.",
            scene(0, null, hidden = false, inputLine = true), "one unit: y = step(w₁x₁ + w₂x₂ + b)  →  {w:no w, b works}", legend.drop(1),
        ),
        NnFrame(
            "Hidden unit 1 computes {p:OR}: it fires if either input is on.",
            "Its line cuts (0,0) off from the other three corners.",
            scene(1, null, hidden = false), "h₁ = step(x₁ + x₂ − 0.5)  →  {p:OR}", legend.drop(1),
        ),
        NnFrame(
            "Hidden unit 2 computes {p:AND}: it fires only when both are on.",
            "A second line, cutting (1,1) off from the rest.",
            scene(2, null, hidden = false), "h₂ = step(x₁ + x₂ − 1.5)  →  {p:AND}", legend.drop(1),
        ),
        NnFrame(
            "Replot each row at (h₁, h₂): (0,1) and (1,0) {land on one point}.",
            "The hidden layer has moved the points. Only three distinct positions are left.",
            scene(2, null, hidden = true), "(x₁, x₂) → (h₁, h₂) = (OR, AND)", legend.drop(1),
        ),
        NnFrame(
            "Row (0,0): neither hidden unit fires, so {y = 0}.",
            "The output unit computes h₁ AND NOT h₂: on for OR, but vetoed by AND.",
            scene(2, 0, hidden = true), rule(0), legend,
        ),
        NnFrame(
            "Row (0,1): OR fires and AND doesn't, so {y = 1}.",
            "Exactly the case XOR wants to be 1.",
            scene(2, 1, hidden = true), rule(1), legend,
        ),
        NnFrame(
            "Row (1,0) lands on the same hidden point as (0,1), so it gets {the same answer}.",
            "Once two inputs share a hidden point, no later layer can tell them apart. Here that is exactly right.",
            scene(2, 2, hidden = true), rule(2), legend,
        ),
        NnFrame(
            "In hidden space, (0,1) and (1,0) land on one point. {A single line} now splits the classes.",
            "Row (1,1) fires both OR and AND, so the output unit turns it off.",
            scene(2, 3, hidden = true, hiddenLine = true), rule(3), legend,
        ),
    )
    return listOf(NnTab("", frames))
}

// ── Gradient descent variants ──

private const val GD_STEEP = 12.0
private val gdStart = -4.0 to 1.2
private val gdCheckpoints = listOf(0, 3, 8, 20, 40)

private fun gdLoss(w: Pair<Double, Double>) = 0.5 * (w.first * w.first + GD_STEEP * w.second * w.second)

private fun gdPaths(steps: Int): List<List<Pair<Double, Double>>> {
    fun grad(w: Pair<Double, Double>) = w.first to GD_STEEP * w.second
    val sgd = mutableListOf(gdStart)
    repeat(steps) {
        val w = sgd.last()
        val g = grad(w)
        sgd += (w.first - 0.15 * g.first) to (w.second - 0.15 * g.second)
    }
    val mom = mutableListOf(gdStart)
    var v = 0.0 to 0.0
    repeat(steps) {
        val w = mom.last()
        val g = grad(w)
        v = (0.85 * v.first + g.first) to (0.85 * v.second + g.second)
        mom += (w.first - 0.03 * v.first) to (w.second - 0.03 * v.second)
    }
    val adam = mutableListOf(gdStart)
    var m = 0.0 to 0.0
    var s = 0.0 to 0.0
    for (t in 1..steps) {
        val w = adam.last()
        val g = grad(w)
        m = (0.9 * m.first + 0.1 * g.first) to (0.9 * m.second + 0.1 * g.second)
        s = (0.999 * s.first + 0.001 * g.first * g.first) to (0.999 * s.second + 0.001 * g.second * g.second)
        val c1 = 1 - Math.pow(0.9, t.toDouble())
        val c2 = 1 - Math.pow(0.999, t.toDouble())
        adam += (w.first - 0.35 * (m.first / c1) / (sqrt(s.first / c2) + 1e-8)) to
            (w.second - 0.35 * (m.second / c1) / (sqrt(s.second / c2) + 1e-8))
    }
    return listOf(sgd, mom, adam)
}

private fun gdTabs(): List<NnTab> {
    val paths = gdPaths(gdCheckpoints.last())
    val tones = listOf(NnTone.Blue, NnTone.Green, NnTone.Violet)
    val names = listOf("SGD", "Mom", "Adam")
    val heads = listOf(
        "Three optimizers leave {the same start} on the same bowl.",
        "SGD {zigzags}: every step overshoots the steep axis and lands on the other side.",
        "Momentum {builds speed} along the flat axis, where every gradient points the same way.",
        "Adam divides each step by that axis's own gradient size, so it moves {the same distance} on the steep and flat axes.",
        "After 40 steps SGD ends lowest, {only because its rate was tuned} to this bowl.",
    )
    val bodies = listOf(
        "The bowl is ${wt(GD_STEEP)} times steeper across than along: the shape that makes plain gradient descent struggle.",
        "Its rate is set just under the point where the steep axis would diverge.",
        "On the steep axis successive gradients cancel, so the velocity there stays small.",
        "Momentum and Adam both carry enough speed to overshoot the minimum and swing back.",
        "Adam used one untuned rate for both axes and still got close. On a real network nobody can tune a rate per axis.",
    )
    val frames = gdCheckpoints.mapIndexed { i, t ->
        val curves = paths.mapIndexed { k, p -> NnCurve(listOf(p.take(t + 1)), tones[k]) }
        val ends = paths.mapIndexed { k, p -> NnDot(p[t].first, p[t].second, tones[k]) }
        NnFrame(
            heads[i], bodies[i],
            PlotScene(
                200, -4.6..1.6, -1.25..1.55, curves,
                dots = listOf(NnDot(gdStart.first, gdStart.second, NnTone.Off, hollow = true, label = "start")) + if (t > 0) ends else emptyList(),
                axes = false,
                contours = listOf(0.7, 1.5, 2.4, 3.4, 4.5, 5.7),
                contourAspect = sqrt(GD_STEEP),
                header = "ITERATION $t · SAME START",
            ),
            null,
            listOf(NnTone.Blue to "SGD", NnTone.Green to "Momentum", NnTone.Violet to "Adam", NnTone.Off to "Loss contour"),
            paths.mapIndexed { k, p -> NnChip(names[k], fx(gdLoss(p[t]), 3), dot = tones[k]) },
        )
    }
    return listOf(NnTab("", frames))
}

// ── Lab ──

private fun neuralStoryTabs(topicId: String, dropped: Int): List<NnTab> = when (topicId) {
    "restricted_boltzmann_machines" -> rbmTabs()
    "deep_belief_networks" -> dbnTabs()
    "batch_normalization" -> bnTabs()
    "backpropagation" -> bpTabs()
    "autoencoders" -> aeTabs()
    "dropout" -> dropoutTabs(dropped)
    "activation_functions" -> activationTabs()
    "mlp" -> mlpTabs()
    else -> gdTabs()
}

internal fun neuralStoryFrameCount(topicId: String): Int =
    (DROPOUT_MIN..DROPOUT_MAX).sumOf { k -> neuralStoryTabs(topicId, k).sumOf { it.frames.size } }

@Composable
internal fun NeuralStorySection(topicId: String) {
    var dropped by rememberSaveable(topicId) { mutableIntStateOf(2) }
    var tab by rememberSaveable(topicId) { mutableIntStateOf(0) }
    val tabs = remember(topicId, dropped) { neuralStoryTabs(topicId, dropped) }
    val frames = tabs[tab.coerceIn(0, tabs.lastIndex)].frames
    val playback = rememberPlaybackState(key = Triple(topicId, tab, dropped), stepCount = frames.size, initialSpeedMs = 1000f)
    val frame = frames[playback.index.coerceIn(0, frames.lastIndex)]

    Column(modifier = Modifier.fillMaxWidth()) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                if (tabs.size > 1) {
                    LabSegments(tabs.map { it.label }, tab, Modifier.padding(bottom = 14.dp)) { tab = it }
                }
                if (topicId == "dropout") {
                    DropRateRow(dropped, Modifier.padding(bottom = 12.dp)) { dropped = (dropped + it).coerceIn(DROPOUT_MIN, DROPOUT_MAX) }
                }
                when (val scene = frame.scene) {
                    is NetScene -> NetView(scene)
                    is BarsScene -> BarsView(scene)
                    is PlotScene -> PlotView(scene)
                    is XorScene -> XorView(scene)
                }
                frame.formula?.let { NnFormula(it, Modifier.padding(top = 12.dp)) }
                StoryLegendRow(
                    frame.legend.map { (tone, label) -> Triple(nnColor(tone), nnSwatch(tone), label) },
                    Modifier.padding(top = 14.dp),
                )
            }
        }
        NnChips(frame.chips, Modifier.padding(top = 16.dp))
        LabStoryNarration(frame.headline, frame.body, Modifier.padding(top = 16.dp))
        PlaybackTransport(playback, captions = frames.map { storyPlain(it.headline) })
    }
}

// ── Rendering ──

@Composable
private fun nnColor(tone: NnTone): Color = when (tone) {
    NnTone.Blue -> SimColors.Blue
    NnTone.Green -> SimColors.Green
    NnTone.Violet -> SimColors.Answer
    NnTone.Current -> SimColors.Active
    NnTone.Warn, NnTone.Dropped -> SimColors.Red
    NnTone.Band -> SimColors.Answer.copy(alpha = 0.45f)
    NnTone.Off, NnTone.Ghost -> MaterialTheme.colorScheme.onSurfaceVariant
}

private fun nnSwatch(tone: NnTone): SwatchStyle = when (tone) {
    NnTone.Off -> SwatchStyle.Ring
    NnTone.Ghost, NnTone.Dropped -> SwatchStyle.Dashed
    else -> SwatchStyle.Fill
}

/** Every tone's fill and the ink on it, resolved once for a Canvas. */
private class NnInks(
    val fill: Map<NnTone, Color>,
    val onFill: Map<NnTone, Color>,
    val muted: Color,
    val onSurface: Color,
    val surface: Color,
    val warnInk: Color,
    val activeInk: Color,
    val violetInk: Color,
)

@Composable
private fun rememberNnInks(): NnInks {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    return NnInks(
        fill = NnTone.entries.associateWith { nnColor(it) },
        onFill = NnTone.entries.associateWith {
            when (it) {
                NnTone.Current -> Color(0xFF1F1A0A)
                NnTone.Off, NnTone.Ghost, NnTone.Dropped -> muted
                NnTone.Warn -> StoryTone.Warn.ink()
                else -> Color.White
            }
        },
        muted = muted,
        onSurface = MaterialTheme.colorScheme.onSurface,
        surface = MaterialTheme.colorScheme.surface,
        warnInk = StoryTone.Warn.ink(),
        activeInk = StoryTone.Active.ink(),
        violetInk = StoryTone.Answer.ink(),
    )
}

private fun DrawScope.textAt(
    measurer: TextMeasurer,
    text: String,
    style: TextStyle,
    color: Color,
    at: Offset,
    // 0 centres the text on [at]; −1 ends it there; 1 starts it there.
    anchor: Int = 0,
) {
    val layout = measurer.measure(text, style)
    val x = when (anchor) {
        -1 -> at.x - layout.size.width
        1 -> at.x
        else -> at.x - layout.size.width / 2f
    }
    drawText(layout, color = color, topLeft = Offset(x, at.y - layout.size.height / 2f))
}

@Composable
private fun LabelRow(labels: List<NnLabel>, mono: Boolean, modifier: Modifier = Modifier) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(modifier = modifier.fillMaxWidth()) {
        labels.forEach { label ->
            Text(
                label.text,
                fontFamily = if (mono) IBMPlexMono else null,
                fontSize = 12.sp,
                fontWeight = if (mono) FontWeight.Normal else FontWeight.SemiBold,
                letterSpacing = if (mono) 0.sp else 1.sp,
                color = muted,
                maxLines = 1,
                textAlign = label.align,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun NetView(scene: NetScene) {
    val measurer = rememberTextMeasurer()
    val inks = rememberNnInks()
    if (scene.pills.isNotEmpty()) {
        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            scene.pills.forEach { pill ->
                val (bg, ink) = when (pill.tone) {
                    NnTone.Green -> SimColors.Green.copy(alpha = 0.2f) to StoryTone.Done.ink()
                    NnTone.Blue -> SimColors.Blue.copy(alpha = 0.22f) to StoryTone.Path.ink()
                    else -> SimColors.Tint to MaterialTheme.colorScheme.onSurfaceVariant
                }
                Box(
                    modifier = Modifier.weight(1f).height(34.dp).background(bg, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(pill.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = ink, maxLines = 1)
                }
            }
        }
    }
    if (scene.headers.isNotEmpty()) LabelRow(scene.headers, mono = false, Modifier.padding(bottom = 6.dp))
    Canvas(modifier = Modifier.fillMaxWidth().height(scene.height.dp)) {
        val maxR = scene.nodes.maxOf { it.r }.dp.toPx()
        val badgeRoom = if (scene.nodes.any { it.badge != null }) 22.dp.toPx() else 0f
        val padX = maxR + 2.dp.toPx()
        val padTop = maxR + 2.dp.toPx()
        val padBottom = maxR + 2.dp.toPx() + badgeRoom
        fun at(x: Float, y: Float) = Offset(padX + x * (size.width - 2 * padX), padTop + y * (size.height - padTop - padBottom))
        fun at(n: NnNode) = at(n.x, n.y)

        scene.bands.forEach { b ->
            val path = Path().apply {
                moveTo(at(b.x0, b.top0).x, at(b.x0, b.top0).y - 8.dp.toPx())
                lineTo(at(b.x1, b.top1).x, at(b.x1, b.top1).y - 8.dp.toPx())
                lineTo(at(b.x1, b.bottom1).x, at(b.x1, b.bottom1).y + 8.dp.toPx())
                lineTo(at(b.x0, b.bottom0).x, at(b.x0, b.bottom0).y + 8.dp.toPx())
                close()
            }
            drawPath(path, inks.muted.copy(alpha = 0.08f))
        }

        val labelStyle = TextStyle(fontFamily = IBMPlexMono, fontSize = 11.sp, fontWeight = FontWeight.Medium)
        scene.edges.sortedBy { it.tone != NnEdgeTone.Faint }.forEach { e ->
            val a = at(scene.nodes[e.from])
            val b = at(scene.nodes[e.to])
            val (color, width) = when (e.tone) {
                NnEdgeTone.Faint -> inks.muted.copy(alpha = 0.28f) to 1.dp.toPx()
                NnEdgeTone.Blue -> SimColors.Blue to 2.dp.toPx()
                NnEdgeTone.Green -> SimColors.Green.copy(alpha = 0.45f) to 1.dp.toPx()
                NnEdgeTone.Red -> SimColors.Red to 2.5.dp.toPx()
                NnEdgeTone.RedDashed -> SimColors.Red.copy(alpha = 0.6f) to 1.5.dp.toPx()
                NnEdgeTone.Current -> SimColors.Active to 3.dp.toPx()
            }
            val dash = if (e.tone == NnEdgeTone.RedDashed) PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx())) else null
            drawLine(color, a, b, strokeWidth = width, cap = StrokeCap.Round, pathEffect = dash)
        }
        scene.edges.filter { it.label != null }.forEach { e ->
            val a = at(scene.nodes[e.from])
            val b = at(scene.nodes[e.to])
            val ink = if (e.tone == NnEdgeTone.Red || e.tone == NnEdgeTone.RedDashed) inks.warnInk else inks.muted
            // Beside the edge, on its upper side, on a surface-coloured pill so crossing lines don't cut it.
            val dx = b.x - a.x
            val dy = b.y - a.y
            val len = maxOf(sqrt(dx * dx + dy * dy), 1f)
            var nx = dy / len
            var ny = -dx / len
            if (ny > 0) { nx = -nx; ny = -ny }
            val off = 12.dp.toPx()
            val m = Offset(a.x + dx * 0.45f + nx * off, a.y + dy * 0.45f + ny * off)
            val layout = measurer.measure(e.label!!, labelStyle)
            drawRoundRect(
                inks.surface,
                topLeft = Offset(m.x - layout.size.width / 2f - 3.dp.toPx(), m.y - layout.size.height / 2f),
                size = Size(layout.size.width + 6.dp.toPx(), layout.size.height.toFloat()),
                cornerRadius = CornerRadius(4.dp.toPx()),
            )
            drawText(layout, color = ink, topLeft = Offset(m.x - layout.size.width / 2f, m.y - layout.size.height / 2f))
        }

        scene.nodes.forEach { n ->
            val c = at(n)
            val r = n.r.dp.toPx()
            val textStyle = TextStyle(fontFamily = IBMPlexMono, fontWeight = FontWeight.Bold, fontSize = if (n.r >= 17f) 12.sp else 10.sp)
            when (n.tone) {
                NnTone.Off -> {
                    drawCircle(inks.surface, r, c)
                    drawCircle(inks.muted.copy(alpha = 0.6f), r - 0.75.dp.toPx(), c, style = Stroke(1.5.dp.toPx()))
                }
                NnTone.Ghost, NnTone.Dropped -> {
                    drawCircle(inks.surface, r, c)
                    drawCircle(
                        inks.muted.copy(alpha = 0.5f), r - 0.75.dp.toPx(), c,
                        style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))),
                    )
                    if (n.tone == NnTone.Dropped) {
                        val s = r * 0.38f
                        drawLine(SimColors.Red, Offset(c.x - s, c.y - s), Offset(c.x + s, c.y + s), 2.dp.toPx(), StrokeCap.Round)
                        drawLine(SimColors.Red, Offset(c.x + s, c.y - s), Offset(c.x - s, c.y + s), 2.dp.toPx(), StrokeCap.Round)
                    }
                }
                NnTone.Warn -> {
                    drawCircle(inks.surface, r, c)
                    drawCircle(SimColors.Red.copy(alpha = 0.2f), r, c)
                    drawCircle(SimColors.Red, r - 0.75.dp.toPx(), c, style = Stroke(1.5.dp.toPx()))
                }
                else -> drawCircle(inks.fill.getValue(n.tone), r, c)
            }
            if (n.text.isNotEmpty() && n.tone != NnTone.Ghost && n.tone != NnTone.Dropped) {
                textAt(measurer, n.text, textStyle, inks.onFill.getValue(n.tone), c)
            }
            n.side?.let { textAt(measurer, it, labelStyle, inks.muted, Offset(c.x + r + 5.dp.toPx(), c.y), anchor = 1) }
            n.badge?.let { badge ->
                val layout = measurer.measure(badge.text, labelStyle.copy(fontWeight = FontWeight.Bold))
                val w = layout.size.width + 12.dp.toPx()
                val h = 18.dp.toPx()
                val top = c.y + r + 4.dp.toPx()
                drawRoundRect(
                    if (badge.warn) SimColors.Red.copy(alpha = 0.85f) else inks.muted.copy(alpha = 0.22f),
                    topLeft = Offset(c.x - w / 2, top),
                    size = Size(w, h),
                    cornerRadius = CornerRadius(6.dp.toPx()),
                )
                drawText(
                    layout,
                    color = if (badge.warn) Color.White else inks.muted,
                    topLeft = Offset(c.x - layout.size.width / 2f, top + (h - layout.size.height) / 2f),
                )
            }
        }
    }
    if (scene.footers.isNotEmpty()) LabelRow(scene.footers, mono = true, Modifier.padding(top = 6.dp))
}

@Composable
private fun BarsView(scene: BarsScene) {
    val measurer = rememberTextMeasurer()
    val inks = rememberNnInks()
    val bandLine = StoryTone.Answer.ink()
    Canvas(modifier = Modifier.fillMaxWidth().height(236.dp)) {
        val labelStyle = TextStyle(fontSize = 12.sp)
        val monoStyle = TextStyle(fontFamily = IBMPlexMono, fontSize = 12.sp)
        val n = scene.raw.size
        val gap = 8.dp.toPx()
        val barW = (size.width - gap * (n - 1)) / n
        fun left(i: Int) = i * (barW + gap)

        // Upper panel: the raw values from a shared baseline.
        textAt(measurer, "raw x", labelStyle, inks.muted, Offset(0f, 8.dp.toPx()), anchor = 1)
        val top = 22.dp.toPx()
        val base = size.height * 0.45f
        fun yRaw(v: Double) = base - (v / scene.rawMax).toFloat() * (base - top)
        scene.band?.let { (mu, sd) ->
            drawRect(SimColors.Answer.copy(alpha = 0.2f), Offset(0f, yRaw(mu + sd)), Size(size.width, yRaw(mu - sd) - yRaw(mu + sd)))
            drawLine(
                bandLine, Offset(0f, yRaw(mu)), Offset(size.width, yRaw(mu)), 1.5.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())),
            )
        }
        scene.raw.forEachIndexed { i, bar ->
            val v = bar.value ?: return@forEachIndexed
            drawRoundRect(
                inks.fill.getValue(bar.tone), Offset(left(i), yRaw(v)), Size(barW, base - yRaw(v)),
                cornerRadius = CornerRadius(4.dp.toPx()),
            )
        }
        drawLine(inks.muted.copy(alpha = 0.3f), Offset(0f, base), Offset(size.width, base), 1.dp.toPx())

        // Lower panel: the normalized values around zero, with ±1 marked.
        val lowerTop = size.height * 0.52f
        textAt(measurer, scene.lowerLabel, monoStyle, inks.muted, Offset(0f, lowerTop + 6.dp.toPx()), anchor = 1)
        val zoneTop = lowerTop + 20.dp.toPx()
        val zero = (zoneTop + size.height) / 2
        val unit = ((size.height - zoneTop) / 2 - 2.dp.toPx()) / scene.lowerScale.toFloat()
        listOf(1f to "+1", -1f to "−1").forEach { (s, label) ->
            val y = zero - s * unit
            drawLine(
                inks.muted.copy(alpha = 0.35f), Offset(0f, y), Offset(size.width, y), 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 4.dp.toPx())),
            )
            textAt(measurer, label, TextStyle(fontFamily = IBMPlexMono, fontSize = 10.sp), inks.muted, Offset(size.width, y - 8.dp.toPx()), anchor = -1)
        }
        drawLine(inks.muted.copy(alpha = 0.3f), Offset(0f, zero), Offset(size.width, zero), 1.dp.toPx())
        scene.lower.forEachIndexed { i, bar ->
            val v = bar.value
            if (v == null) {
                val h = 12.dp.toPx()
                drawRoundRect(
                    inks.muted.copy(alpha = 0.45f), Offset(left(i) + 1, zero - h / 2), Size(barW - 2, h),
                    cornerRadius = CornerRadius(3.dp.toPx()),
                    style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 2.dp.toPx()))),
                )
            } else {
                val y = zero - (v * unit).toFloat()
                drawRoundRect(
                    inks.fill.getValue(bar.tone), Offset(left(i), minOf(y, zero)), Size(barW, abs(zero - y).coerceAtLeast(2.dp.toPx())),
                    cornerRadius = CornerRadius(4.dp.toPx()),
                )
            }
        }
    }
}

@Composable
private fun PlotView(scene: PlotScene) {
    val measurer = rememberTextMeasurer()
    val inks = rememberNnInks()
    Canvas(modifier = Modifier.fillMaxWidth().height(scene.height.dp)) {
        val padT = if (scene.header != null) 24.dp.toPx() else 6.dp.toPx()
        val padB = if (scene.probe != null) 18.dp.toPx() else 6.dp.toPx()
        val xr = scene.xRange
        val yr = scene.yRange
        fun px(x: Double) = ((x - xr.start) / (xr.endInclusive - xr.start)).toFloat() * size.width
        fun py(y: Double) = padT + (1 - ((y - yr.start) / (yr.endInclusive - yr.start)).toFloat()) * (size.height - padT - padB)
        scene.header?.let {
            textAt(
                measurer, it, TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp),
                inks.muted, Offset(0f, 8.dp.toPx()), anchor = 1,
            )
        }
        clipRect(0f, padT, size.width, size.height - padB) {
            scene.contours.forEach { rx ->
                val ry = rx / scene.contourAspect
                drawOval(
                    inks.muted.copy(alpha = 0.3f),
                    topLeft = Offset(px(-rx), py(ry)),
                    size = Size(px(rx) - px(-rx), py(-ry) - py(ry)),
                    style = Stroke(1.dp.toPx()),
                )
            }
        }
        if (scene.axes) {
            drawLine(inks.muted.copy(alpha = 0.4f), Offset(px(0.0), padT), Offset(px(0.0), size.height - padB), 1.dp.toPx())
            drawLine(inks.muted.copy(alpha = 0.4f), Offset(0f, py(0.0)), Offset(size.width, py(0.0)), 1.dp.toPx())
        }
        scene.refY?.let { ry ->
            drawLine(
                inks.muted.copy(alpha = 0.35f), Offset(0f, py(ry)), Offset(size.width, py(ry)), 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 4.dp.toPx())),
            )
            textAt(measurer, wt(ry), TextStyle(fontFamily = IBMPlexMono, fontSize = 10.sp), inks.muted, Offset(px(0.0) - 5.dp.toPx(), py(ry) - 7.dp.toPx()), anchor = -1)
        }
        scene.probe?.let { (x, label) ->
            drawLine(
                SimColors.Active, Offset(px(x), padT), Offset(px(x), size.height - padB), 1.5.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx())),
            )
            textAt(
                measurer, label, TextStyle(fontFamily = IBMPlexMono, fontSize = 11.sp, fontWeight = FontWeight.Medium),
                inks.activeInk, Offset(px(x) + 4.dp.toPx(), size.height - padB + 9.dp.toPx()), anchor = 1,
            )
        }
        clipRect(0f, padT - 4.dp.toPx(), size.width, size.height - padB + 4.dp.toPx()) {
            scene.curves.forEach { curve ->
                curve.segments.filter { it.size > 1 }.forEach { seg ->
                    val path = Path().apply {
                        moveTo(px(seg[0].first), py(seg[0].second))
                        seg.drop(1).forEach { lineTo(px(it.first), py(it.second)) }
                    }
                    drawPath(path, inks.fill.getValue(curve.tone), style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
                }
            }
        }
        scene.dots.forEach { dot ->
            val c = Offset(px(dot.x), py(dot.y))
            if (dot.hollow) {
                drawCircle(inks.surface, 6.dp.toPx(), c)
                drawCircle(inks.muted, 5.dp.toPx(), c, style = Stroke(2.dp.toPx()))
            } else {
                drawCircle(inks.surface, 7.dp.toPx(), c)
                drawCircle(inks.fill.getValue(dot.tone), 5.dp.toPx(), c)
            }
            dot.label?.let {
                textAt(measurer, it, TextStyle(fontFamily = IBMPlexMono, fontSize = 11.sp), inks.muted, Offset(c.x + 9.dp.toPx(), c.y - 11.dp.toPx()), anchor = 1)
            }
        }
    }
}

@Composable
private fun XorView(scene: XorScene) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        XorPlane("input space", "x₁", "x₂", scene.input, if (scene.inputLine) XorLine.Fail else null, Modifier.weight(1f))
        XorPlane("hidden space", "OR", "AND", scene.hidden, if (scene.hiddenLine) XorLine.Split else null, Modifier.weight(1f))
    }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf("x₁", "x₂", "h₁ OR", "h₂ AND", "y").forEach {
            Text(it, fontFamily = IBMPlexMono, fontSize = 11.sp, color = muted, textAlign = TextAlign.Center, maxLines = 1, modifier = Modifier.weight(1f))
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        scene.rows.forEach { row ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { (text, tone) -> XorCell(text, tone, Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun XorCell(text: String, tone: XorCellTone, modifier: Modifier) {
    val shape = RoundedCornerShape(8.dp)
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val base = modifier.height(34.dp)
    val styled = when (tone) {
        XorCellTone.Idle -> base.background(muted.copy(alpha = 0.2f), shape)
        XorCellTone.Row -> base.background(SimColors.Blue.copy(alpha = 0.22f), shape).border(1.5.dp, SimColors.Blue, shape)
        XorCellTone.One -> base.background(SimColors.Answer, shape)
        XorCellTone.Current -> base.background(SimColors.Active, shape)
        XorCellTone.Empty -> base.dashedOutline(muted.copy(alpha = 0.4f), 8.dp)
    }
    val ink = when (tone) {
        XorCellTone.One -> Color.White
        XorCellTone.Current -> Color(0xFF1F1A0A)
        else -> MaterialTheme.colorScheme.onSurface
    }
    Box(styled, contentAlignment = Alignment.Center) {
        Text(text, fontFamily = IBMPlexMono, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = ink)
    }
}

private enum class XorLine { Fail, Split }

@Composable
private fun XorPlane(title: String, xLabel: String, yLabel: String, points: List<XorPoint>, line: XorLine?, modifier: Modifier) {
    val measurer = rememberTextMeasurer()
    val inks = rememberNnInks()
    Canvas(
        modifier = modifier
            .height(120.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black.copy(alpha = 0.16f)),
    ) {
        val small = TextStyle(fontFamily = IBMPlexMono, fontSize = 10.sp)
        textAt(measurer, title, TextStyle(fontSize = 11.sp), inks.muted, Offset(8.dp.toPx(), 11.dp.toPx()), anchor = 1)
        val ax = size.width * 0.12f
        val ay = size.height * 0.86f
        fun at(x: Double, y: Double) = Offset(size.width * (0.24f + 0.56f * x.toFloat()), size.height * (0.72f - 0.46f * y.toFloat()))
        drawLine(inks.muted.copy(alpha = 0.45f), Offset(ax, size.height * 0.2f), Offset(ax, ay), 1.dp.toPx())
        drawLine(inks.muted.copy(alpha = 0.45f), Offset(ax, ay), Offset(size.width * 0.94f, ay), 1.dp.toPx())
        textAt(measurer, xLabel, small, inks.muted, Offset(size.width * 0.94f, ay + 7.dp.toPx()), anchor = -1)
        textAt(measurer, yLabel, small, inks.muted, Offset(size.width * 0.94f, size.height * 0.2f), anchor = -1)
        line?.let {
            val (a, b, color) = when (it) {
                XorLine.Fail -> Triple(at(-0.2, 0.7), at(0.7, -0.2), SimColors.Red)
                XorLine.Split -> Triple(at(0.3, -0.25), at(1.35, 0.8), SimColors.Active)
            }
            drawLine(color, a, b, 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx())))
        }
        points.forEach { p ->
            val c = at(p.x.toDouble(), p.y.toDouble())
            if (p.filled) {
                drawCircle(SimColors.Answer, 8.dp.toPx(), c)
            } else {
                drawCircle(inks.muted.copy(alpha = 0.8f), 7.dp.toPx(), c, style = Stroke(1.5.dp.toPx()))
            }
        }
        points.filter { it.ring }.forEach { p ->
            drawCircle(SimColors.Active, 12.dp.toPx(), at(p.x.toDouble(), p.y.toDouble()), style = Stroke(2.5.dp.toPx()))
        }
    }
}

@Composable
private fun NnFormula(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(SimColors.Tint, RoundedCornerShape(10.dp))
            .padding(vertical = 10.dp, horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            storyAnnotated(text),
            fontFamily = IBMPlexMono,
            fontSize = 13.sp,
            lineHeight = 19.sp,
            textAlign = TextAlign.Center,
            maxLines = 2,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
        )
    }
}

@Composable
private fun NnChips(chips: List<NnChip>, modifier: Modifier = Modifier) {
    LabChips(chips.map { LabChip(it.key, it.value, it.tone, it.dot?.let { tone -> nnColor(tone) }) }, modifier)
}

/** "Drop rate p = 1/3" with a − | + pill, stepping how many of the six hidden units a pass drops. */
@Composable
private fun DropRateRow(dropped: Int, modifier: Modifier = Modifier, onStep: (Int) -> Unit) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val onSurface = MaterialTheme.colorScheme.onSurface
    val p = when (dropped) {
        2 -> "1/3"
        3 -> "1/2"
        else -> "$dropped/6"
    }
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            buildAnnotatedString {
                append("Drop rate ")
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("p") }
                append("  =  ")
                withStyle(SpanStyle(fontFamily = IBMPlexMono, fontWeight = FontWeight.Bold)) { append(p) }
            },
            fontSize = 16.sp,
            color = onSurface,
            modifier = Modifier.weight(1f),
        )
        Row(
            modifier = Modifier.height(36.dp).background(SimColors.Tint, RoundedCornerShape(10.dp)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            listOf(-1 to "−", 1 to "+").forEachIndexed { i, (delta, glyph) ->
                val enabled = if (delta < 0) dropped > DROPOUT_MIN else dropped < DROPOUT_MAX
                if (i == 1) Box(Modifier.width(1.dp).height(18.dp).background(muted.copy(alpha = 0.35f)))
                Box(
                    modifier = Modifier
                        .size(width = 44.dp, height = 36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(enabled = enabled) { onStep(delta) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(glyph, fontSize = 20.sp, fontWeight = FontWeight.Medium, color = onSurface.copy(alpha = if (enabled) 1f else 0.3f))
                }
            }
        }
    }
}

