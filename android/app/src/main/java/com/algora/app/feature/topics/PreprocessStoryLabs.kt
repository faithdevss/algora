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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.algora.app.core.ui.theme.CategoryAccents
import com.algora.app.core.ui.theme.IBMPlexMono
import com.algora.app.core.ui.theme.SimColors
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.random.Random

// ── Preprocessing storyboards ────────────────────────────────────────────────
// Outlier detection, imputation, label and one-hot encoding, z-score and min-max scaling, SMOTE,
// chi-square selection and RFE. Each is one card of figures (a plot, a strip, a table or bars), the
// arithmetic of the current setting, chips and a headline, then a picker, a stepper and one action, or
// a step track. Every number is computed from the fixed, seeded data below.

internal val preprocessStoryTopicIds = setOf(
    "outlier_detection", "missing_value_imputation", "label_encoding", "one_hot_encoding",
    "z_score_standardization", "min_max_normalization", "smote", "chi_square_selection", "rfe",
)

private val ClassPink = CategoryAccents.Pink
private val IncomeOrange = Color(0xFFF08A3C)
private val DotGrey = Color(0xFFCBD0DA)
private val ColourDots = listOf(Color(0xFFD9534F), Color(0xFF3F9A62), Color(0xFF4F7FE0), Color(0xFFE0B23F))

// ── Formatting and small math ──

private fun px(v: Double, d: Int = 2): String {
    var p = 1L
    repeat(d) { p *= 10 }
    val r = (abs(v) * p + 0.5).toLong()
    val body = if (d == 0) "${r / p}" else "${r / p}." + (r % p).toString().padStart(d, '0')
    return if (v < 0 && r != 0L) "−$body" else body
}

private fun pct(v: Double, d: Int = 1) = px(v * 100, d) + "%"

private class PpRng(seed: Int) {
    private val random = Random(seed)
    fun u() = random.nextDouble()
    fun int(n: Int) = random.nextInt(n)
    fun normal(): Double {
        val a = max(random.nextDouble(), 1e-12)
        val b = random.nextDouble()
        return sqrt(-2 * ln(a)) * cos(2 * PI * b)
    }
}

private fun ppMean(v: List<Double>) = v.sum() / v.size

private fun ppVar(v: List<Double>): Double {
    val m = ppMean(v)
    return v.sumOf { (it - m) * (it - m) } / (v.size - 1)
}

private fun ppSd(v: List<Double>) = sqrt(ppVar(v))

private fun ppCorr(a: List<Double>, b: List<Double>): Double {
    val ma = ppMean(a)
    val mb = ppMean(b)
    val cov = a.indices.sumOf { (a[it] - ma) * (b[it] - mb) }
    return cov / sqrt(a.sumOf { (it - ma) * (it - ma) } * b.sumOf { (it - mb) * (it - mb) })
}

private fun ppQuantile(v: List<Double>, q: Double): Double {
    val s = v.sorted()
    val at = q * (s.size - 1)
    val lo = at.toInt()
    val hi = min(lo + 1, s.size - 1)
    return s[lo] + (at - lo) * (s[hi] - s[lo])
}

private fun ppMedian(v: List<Double>) = ppQuantile(v, 0.5)

/** Least squares with an intercept first: Gaussian elimination on the normal equations. */
private fun ppLeastSquares(x: List<DoubleArray>, y: List<Double>): DoubleArray {
    val d = x[0].size + 1
    fun row(i: Int, j: Int) = if (j == 0) 1.0 else x[i][j - 1]
    val m = Array(d) { r -> DoubleArray(d + 1) { c -> if (c < d) y.indices.sumOf { row(it, r) * row(it, c) } + (if (r == c) 1e-9 else 0.0) else y.indices.sumOf { row(it, r) * y[it] } } }
    for (c in 0 until d) {
        val p = (c until d).maxBy { abs(m[it][c]) }
        val t = m[c]; m[c] = m[p]; m[p] = t
        for (r in 0 until d) if (r != c) {
            val f = m[r][c] / m[c][c]
            for (j in c..d) m[r][j] -= f * m[c][j]
        }
    }
    return DoubleArray(d) { m[it][d] / m[it][it] }
}

private fun ppPredict(w: DoubleArray, row: DoubleArray) = w[0] + row.indices.sumOf { w[it + 1] * row[it] }

// ── Model ──

private class PpDot(val x: Double, val y: Double, val color: Color, val r: Float = 3f, val ring: Color? = null, val hollow: Boolean = false)

private class PpSeg(val x0: Double, val y0: Double, val x1: Double, val y1: Double, val color: Color, val dashed: Boolean = false, val width: Float = 2f)

private sealed interface PpBlock

private class PpPlot(
    val dots: List<PpDot>,
    val segs: List<PpSeg>,
    val xRange: Pair<Double, Double>,
    val yRange: Pair<Double, Double>,
    val aspect: Float,
    /** Category names under equal-width slots across the plot ("red=0"). */
    val slots: List<String> = emptyList(),
    val caption: String? = null,
) : PpBlock

/** The outlier strip: the clean values as a beeswarm, then the extremes stacked in their own panel. */
private class PpStrip(val clean: List<Double>, val jitter: List<Double>, val extremes: Int, val caught: Boolean, val extremeLabel: String) : PpBlock

private class PpRow(val cells: List<String>, val ink: StoryTone? = null, val fill: StoryTone? = null, val muted: Boolean = false)

private class PpTable(val header: List<String>, val rows: List<PpRow>, val weights: List<Float>) : PpBlock

private class PpFormula(val lines: List<String>) : PpBlock

/** "Raw units ……… age 0.0%" over a bar split into age (blue) and income (orange). */
private class PpShare(val label: String, val ageShare: Double)

private class PpShareBars(val rows: List<PpShare>) : PpBlock

private class PpAccBars(val rows: List<Triple<String, Double, Boolean>>) : PpBlock

private class PpOneHot(val columns: List<String>, val rows: List<String>, val current: Int, val dropped: Boolean) : PpBlock

private class PpWeight(val name: String, val value: Double, val smallest: Boolean)

private class PpWeights(val rows: List<PpWeight>, val top: Double) : PpBlock

private class PpFrame(
    val headline: String,
    val body: String,
    val blocks: List<PpBlock>,
    val legend: List<Triple<Color, SwatchStyle, String>> = emptyList(),
    val chips: List<LabChip> = emptyList(),
    val action: String = "",
)

private class PpParam(val name: String, val symbol: String, val values: List<Int>, val initial: Int, val format: (Int) -> String = { "$it" })

private data class PpState(val tab: Int = 0, val param: Int = 0, val flag: Int = 0)

private class PpLab(
    val tabs: List<String> = emptyList(),
    val startTab: Int = 0,
    val param: PpParam? = null,
    val button: ((PpState) -> String)? = null,
    val onButton: (PpState) -> PpState = { it },
    /** A step track over the frames instead of a picker, stepper and button. */
    val stepped: Boolean = false,
    val frames: (PpState) -> List<PpFrame>,
) {
    val initial get() = PpState(startTab, param?.initial ?: 0, 0)
}

// ── Outlier detection ──

private const val EXTREME = 4000.0

private val outlierClean: List<Double> by lazy {
    val r = PpRng(61)
    List(60) { 65 + r.u() * 6 }
}

/** Each clean value's vertical place in the strip, in [−1, 1]. */
private val outlierJitter: List<Double> by lazy {
    val r = PpRng(62)
    List(outlierClean.size) { r.u() * 2 - 1 }
}

private class PpRules(val mean: Double, val sd: Double, val z: Double, val zFlags: Int, val upper: Double, val iqrFlags: Int, val madFlags: Int)

private fun outlierRules(k: Int): PpRules {
    val data = outlierClean + List(k) { EXTREME }
    val mean = ppMean(data)
    val sd = ppSd(data)
    val z = (EXTREME - mean) / sd
    val q1 = ppQuantile(data, 0.25)
    val q3 = ppQuantile(data, 0.75)
    val upper = q3 + 1.5 * (q3 - q1)
    val med = ppMedian(data)
    val mad = ppMedian(data.map { abs(it - med) })
    val zPrime = 0.6745 * abs(EXTREME - med) / mad
    return PpRules(mean, sd, z, if (z > 3) k else 0, upper, if (EXTREME > upper) k else 0, if (zPrime > 3.5) k else 0)
}

private fun outlierLab(): PpLab {
    val ks = (1..12).toList()
    val mask = ks.first { outlierRules(it).zFlags == 0 }
    return PpLab(
        param = PpParam("Extreme values", "k", ks, ks.indexOf(mask)),
        frames = { s ->
            val k = ks[s.param]
            val r = outlierRules(k)
            val hidden = r.zFlags == 0
            fun tone(flags: Int) = if (flags == k) StoryTone.Done else StoryTone.Warn
            val rows = listOf(
                PpRow(listOf("z-score", "|z| > 3", "${r.zFlags} / $k"), tone(r.zFlags), if (hidden) StoryTone.Warn else null),
                PpRow(listOf("IQR", "> ${px(r.upper, 1)}", "${r.iqrFlags} / $k"), tone(r.iqrFlags), if (r.iqrFlags < k) StoryTone.Warn else null),
                PpRow(listOf("MAD", "|z′| > 3.5", "${r.madFlags} / $k"), tone(r.madFlags), if (r.madFlags < k) StoryTone.Warn else null),
            )
            val zText = if (hidden) "{w:${px(r.z)}} < 3" else "{m:${px(r.z)}} > 3"
            val (headline, body) = when {
                hidden -> "With $k extremes, z-score {w:flags none}: they inflate σ and hide each other." to
                    "IQR and MAD use quartiles and medians, which $k points can't move."
                k == 1 -> "One extreme: z-score {m:flags it} at z = ${px(r.z)}." to
                    "Add more: each extreme inflates σ for the others, until at k = $mask z-score sees none."
                else -> "With $k extremes, z-score still {m:flags all $k}." to
                    "σ is already ${px(r.sd, 0)}, up from ${px(ppSd(outlierClean))}. At k = $mask the extremes hide each other."
            }
            listOf(
                PpFrame(
                    headline,
                    body,
                    listOf(
                        PpStrip(outlierClean, outlierJitter, k, !hidden, px(EXTREME, 0)),
                        PpFormula(listOf("z = (${px(EXTREME, 0)} − ${px(r.mean, 0)}) / ${px(r.sd, 0)} = $zText")),
                        PpTable(listOf("rule", "cut-off", "flags"), rows, listOf(1f, 1.3f, 0.8f)),
                    ),
                    legend = listOf(
                        Triple(DotGrey, SwatchStyle.Dot, "Normal"),
                        if (hidden) Triple(SimColors.Red, SwatchStyle.Ring, "Missed outlier") else Triple(SimColors.Green, SwatchStyle.Ring, "Flagged outlier"),
                    ),
                ),
            )
        },
    )
}

// ── Missing value imputation ──

private class PpImpute(val x: List<Double>, val y: List<Double>, val u: List<Double>)

private val imputeData: PpImpute by lazy {
    val r = PpRng(29)
    val x = mutableListOf<Double>()
    val y = mutableListOf<Double>()
    repeat(200) {
        val xi = 5 + 2 * r.normal()
        val noise = r.normal()
        x += xi
        y += 1.1 * xi + 0.45 * noise
    }
    PpImpute(x, y, List(200) { r.u() })
}

private class PpStrategy(val name: String, val rows: Int, val variance: Double, val corr: Double)

private fun imputeLab(): PpLab {
    val rates = listOf(10, 20, 30, 40, 50, 60)
    val d = imputeData
    val tabs = listOf("Drop rows", "Mean", "Regression")
    return PpLab(
        tabs = tabs,
        startTab = 1,
        param = PpParam("Missing", "%", rates, rates.indexOf(30)),
        frames = { s ->
            val rate = rates[s.param] / 100.0
            val missing = d.u.map { it < rate }
            val kept = d.x.indices.filter { !missing[it] }
            val observedY = kept.map { d.y[it] }
            val mean = ppMean(observedY)
            val w = ppLeastSquares(kept.map { doubleArrayOf(d.x[it]) }, observedY)
            val meanY = d.y.indices.map { if (missing[it]) mean else d.y[it] }
            val regY = d.y.indices.map { if (missing[it]) w[0] + w[1] * d.x[it] else d.y[it] }
            val strategies = listOf(
                PpStrategy("Complete", 200, ppVar(d.y), ppCorr(d.x, d.y)),
                PpStrategy("Drop rows", kept.size, ppVar(observedY), ppCorr(kept.map { d.x[it] }, observedY)),
                PpStrategy("Mean", 200, ppVar(meanY), ppCorr(d.x, meanY)),
                PpStrategy("Regression", 200, ppVar(regY), ppCorr(d.x, regY)),
            )
            val rows = strategies.mapIndexed { i, st ->
                PpRow(listOf(st.name, "${st.rows}", px(st.variance), px(st.corr, 3)), fill = if (i == s.tab + 1) StoryTone.Answer else null, muted = i == 0)
            }
            val xLo = d.x.min() - 0.4
            val xHi = d.x.max() + 0.4
            val yLo = d.y.min() - 0.8
            val yHi = d.y.max() + 0.8
            val violet = SimColors.Answer
            val holes = d.x.indices.filter { missing[it] }
            val observed = kept.map { PpDot(d.x[it], d.y[it], DotGrey, 2.8f) }
            val (dots, segs) = when (s.tab) {
                0 -> observed + holes.map { PpDot(d.x[it], d.y[it], SimColors.Grey, 2.8f, hollow = true) } to emptyList()
                1 -> observed + holes.map { PpDot(d.x[it], mean, violet, 3.2f) } to listOf(PpSeg(xLo, mean, xHi, mean, violet, dashed = true, width = 1.5f))
                else -> observed + holes.map { PpDot(d.x[it], regY[it], violet, 3.2f) } to
                    listOf(PpSeg(xLo, w[0] + w[1] * xLo, xHi, w[0] + w[1] * xHi, violet, dashed = true, width = 1.5f))
            }
            val complete = strategies[0]
            val picked = strategies[s.tab + 1]
            val (headline, body) = when (s.tab) {
                0 -> "Dropping keeps {${kept.size} of 200} rows, and variance stays near ${px(complete.variance)}." to
                    "Fine when values go missing at random, as here. If missingness depends on y, the rows left are biased."
                1 -> "Mean fill keeps all 200 rows, but variance drops from ${px(complete.variance)} to {${px(picked.variance)}}." to
                    "The ${holes.size} imputed values sit on one line, so they weaken the x–y link. Regression fill keeps it."
                else -> "Regression fill keeps all 200 rows and the x–y link: corr {${px(picked.corr, 3)}}." to
                    "Filled values sit exactly on the line, so the link is slightly overstated and variance still dips to ${px(picked.variance)}."
            }
            listOf(
                PpFrame(
                    headline,
                    body,
                    listOf(
                        PpPlot(dots, segs, xLo to xHi, yLo to yHi, 1.95f),
                        PpTable(listOf("strategy", "rows", "var(y)", "corr"), rows, listOf(1.5f, 0.8f, 0.9f, 0.9f)),
                    ),
                    legend = listOf(
                        Triple(DotGrey, SwatchStyle.Dot, "Observed"),
                        if (s.tab == 0) Triple(SimColors.Grey, SwatchStyle.Ring, "Dropped") else Triple(violet, SwatchStyle.Dot, "Imputed"),
                    ),
                ),
            )
        },
    )
}

// ── Label encoding ──

private val colours = listOf("red", "green", "blue", "yellow")
private val colourEffects = listOf(10.0, 2.0, 9.0, 1.0)

/** (colour, target, jitter) rows: twelve per colour, the target set by the colour and not by any order. */
private val colourRows: List<Triple<Int, Double, Double>> by lazy {
    val r = PpRng(23)
    colours.indices.flatMap { c ->
        List(12) {
            val y = colourEffects[c] + (r.u() * 2 - 1) * 0.9
            val jitter = (r.u() - 0.5) * 0.44
            Triple(c, y, jitter)
        }
    }
}

/** Code orders the Shuffle action cycles through: codes[colour]. */
private val codeOrders = listOf(listOf(0, 1, 2, 3), listOf(1, 0, 3, 2), listOf(2, 1, 3, 0), listOf(2, 3, 0, 1))

private fun labelEncodingLab(): PpLab {
    val rows = colourRows
    val means = colours.indices.map { c -> ppMean(rows.filter { it.first == c }.map { it.second }) }
    val mseOneHot = rows.sumOf { (it.second - means[it.first]).let { e -> e * e } } / rows.size
    val yLo = rows.minOf { it.second } - 0.8
    val yHi = rows.maxOf { it.second } + 0.8
    return PpLab(
        tabs = listOf("Label code", "One-hot"),
        button = { "Shuffle Codes" },
        onButton = { it.copy(flag = (it.flag + 1) % codeOrders.size) },
        frames = { s ->
            val codes = codeOrders[s.flag]
            val w = ppLeastSquares(rows.map { doubleArrayOf(codes[it.first].toDouble()) }, rows.map { it.second })
            val mseCode = rows.sumOf { (it.second - w[0] - w[1] * codes[it.first]).let { e -> e * e } } / rows.size
            val bySlot = (0..3).map { slot -> codes.indexOf(slot) }
            val labelTab = s.tab == 0
            val dots = rows.map { PpDot(codes[it.first] + it.third, it.second, ColourDots[it.first], 3.2f) }
            val meanSegs = colours.indices.map { c ->
                PpSeg(codes[c] - 0.3, means[c], codes[c] + 0.3, means[c], Color.White, dashed = true, width = 2f)
            }
            val line = PpSeg(-0.35, w[0] - 0.35 * w[1], 3.35, w[0] + 3.35 * w[1], SimColors.Answer, width = 3f)
            val (c0, c1, c2) = Triple(colours[bySlot[0]], colours[bySlot[1]], colours[bySlot[2]])
            val between = (means[bySlot[1]] - means[bySlot[0]]) * (means[bySlot[2]] - means[bySlot[1]]) > 0
            val ratio = (mseCode / mseOneHot).roundToInt()
            val (headline, body) = when {
                !labelTab -> "One-hot gives each colour its own column, so the fit is each colour's {mean}." to
                    "MSE ${px(mseOneHot, 3)} whatever order the codes take. The ${ratio}× gap was the made-up order."
                between -> "This order puts $c1 {between} $c0 and $c2, and by luck the data agrees." to
                    "The line fits better, MSE ${px(mseCode)}, but the order is arbitrary: the next shuffle breaks it."
                else -> "Codes 0–3 imply $c1 sits {between} $c0 and $c2. The data disagrees." to
                    "A straight line on the codes misses every colour, ${ratio}× worse than one-hot."
            }
            val sign = if (w[1] < 0) "−" else "+"
            listOf(
                PpFrame(
                    headline,
                    body,
                    listOf(
                        PpPlot(dots, if (labelTab) meanSegs + line else meanSegs, -0.5 to 3.5, yLo to yHi, 2.0f, slots = bySlot.mapIndexed { slot, c -> "${colours[c]}=$slot" }),
                        PpFormula(listOf(if (labelTab) "y = ${px(w[0])} $sign ${px(abs(w[1]))} × code" else "ŷ = mean of the row's colour")),
                    ),
                    legend = (if (labelTab) listOf(Triple(SimColors.Answer, SwatchStyle.Line, "Fit on the code")) else emptyList()) +
                        Triple(Color.White, SwatchStyle.DashedLine, "One-hot fit (per-colour mean)"),
                    chips = listOf(
                        LabChip("MSE code", px(mseCode), tint = StoryTone.Warn),
                        LabChip("MSE one-hot", px(mseOneHot, 3), good = true),
                    ),
                ),
            )
        },
    )
}

// ── One-hot encoding ──

private val oneHotRows = listOf("green", "green", "yellow", "red", "blue", "red")

private fun oneHotLab(): PpLab = PpLab(
    stepped = true,
    frames = {
        val chips = listOf(LabChip("columns", "1 → 4"), LabChip("drop first", "3 left", tint = StoryTone.Path))
        val legend = listOf(
            Triple(SimColors.Active, SwatchStyle.Fill, "Current row"),
            Triple(SimColors.Answer, SwatchStyle.Fill, "Encoded 1"),
            Triple(SimColors.Tint, SwatchStyle.Fill, "Not yet"),
        )
        val encode = oneHotRows.indices.map { i ->
            val colour = oneHotRows[i]
            val vector = colours.joinToString(", ") { if (it == colour) "{1}" else "0" }
            val seen = oneHotRows.take(i).contains(colour)
            PpFrame(
                "Row ${i + 1} is {$colour}, so only the $colour column gets a 1.",
                if (seen) "Same colour, same vector: every $colour row encodes identically, whatever row it sits in."
                else "Each colour gets its own column, so no order is implied. Dropping one column avoids a redundant fourth.",
                listOf(PpOneHot(colours, oneHotRows, i, false), PpFormula(listOf("$colour → [$vector]   one 1 per row"))),
                legend,
                chips,
                if (i < oneHotRows.lastIndex) "Encode Row ${i + 2}" else "Drop First Column",
            )
        }
        encode + PpFrame(
            "Drop the {red} column: red becomes [0, 0, 0].",
            "Three columns still tell four colours apart. The fourth was redundant: it always equals 1 minus the other three.",
            listOf(PpOneHot(colours, oneHotRows, oneHotRows.size, true), PpFormula(listOf("red → [0, 0, 0]   blue → [0, {1}, 0]"))),
            legend,
            chips,
            "Start Over",
        )
    },
)

// ── Feature scaling (z-score and min-max) ──

private enum class Scaling { Raw, MinMax, ZScore }

private class PpScaler(private val lo: DoubleArray, private val hi: DoubleArray, private val mu: DoubleArray, private val sd: DoubleArray) {
    fun apply(f: DoubleArray, s: Scaling) = when (s) {
        Scaling.Raw -> f
        Scaling.MinMax -> DoubleArray(2) { (f[it] - lo[it]) / (hi[it] - lo[it]) }
        Scaling.ZScore -> DoubleArray(2) { (f[it] - mu[it]) / sd[it] }
    }
    fun lo(i: Int) = lo[i]
    fun hi(i: Int) = hi[i]
    fun mu(i: Int) = mu[i]
    fun sd(i: Int) = sd[i]
}

private val scaler: PpScaler by lazy {
    val t = FeatureScalingLab.train
    val cols = (0..1).map { i -> t.map { it.features[i] } }
    PpScaler(
        DoubleArray(2) { cols[it].min() },
        DoubleArray(2) { cols[it].max() },
        DoubleArray(2) { ppMean(cols[it]) },
        DoubleArray(2) { ppSd(cols[it]) },
    )
}

private fun neighbours(query: DoubleArray, s: Scaling, k: Int = 5): List<Int> {
    val q = scaler.apply(query, s)
    return FeatureScalingLab.train.indices.sortedBy { i ->
        val p = scaler.apply(FeatureScalingLab.train[i].features, s)
        (p[0] - q[0]) * (p[0] - q[0]) + (p[1] - q[1]) * (p[1] - q[1])
    }.take(k)
}

/** Upper-half test rows (clear of the axis caption) whose five nearest neighbours change once scaled. */
private val scalingQueries: List<Int> by lazy {
    FeatureScalingLab.test.indices.filter { i ->
        val f = FeatureScalingLab.test[i].features
        f[1] > scaler.mu(1) && neighbours(f, Scaling.Raw).toSet() != neighbours(f, Scaling.MinMax).toSet()
    }.take(6)
}

private fun scalingPlot(query: DoubleArray?, raw: List<Int>, scaled: List<Int>, caption: String?): PpPlot {
    val t = FeatureScalingLab.train
    val dots = t.map { PpDot(it.features[0], it.features[1], if (it.label == 1) ClassPink else SimColors.Blue, 2.8f) }
    val segs = mutableListOf<PpSeg>()
    if (query != null) {
        raw.forEach { segs += PpSeg(query[0], query[1], t[it].features[0], t[it].features[1], SimColors.Grey, dashed = true, width = 1.2f) }
        scaled.forEach { segs += PpSeg(query[0], query[1], t[it].features[0], t[it].features[1], SimColors.Answer, width = 2f) }
    }
    val q = query?.let { listOf(PpDot(it[0], it[1], SimColors.Active, 5f)) }.orEmpty()
    return PpPlot(dots + q, segs, scaler.lo(0) - 2 to scaler.hi(0) + 2, scaler.lo(1) - 6000 to scaler.hi(1) + 6000, 2.1f, caption = caption)
}

private val scalingTabs = listOf("Raw", "Min-max", "Z-score")

private fun zScoreLab(): PpLab {
    val shares = FeatureScalingLab.distanceShares.map { it.second[0] }
    val acc = FeatureScalingLab.results.map { it.accuracy }
    val rows = listOf(0, 3, 7, 12, 18, 25)
    return PpLab(
        tabs = scalingTabs,
        startTab = 2,
        button = { "Pick Another Row" },
        onButton = { it.copy(flag = (it.flag + 1) % rows.size) },
        frames = { s ->
            val f = FeatureScalingLab.test[rows[s.flag]].features
            val income = f[1]
            val formula = when (s.tab) {
                0 -> "income: ${px(income, 0)} dollars, age: ${px(f[0], 0)} years"
                1 -> "income: (${px(income, 0)} − ${px(scaler.lo(1), 0)}) / (${px(scaler.hi(1), 0)} − ${px(scaler.lo(1), 0)}) = {v:${px(scaler.apply(f, Scaling.MinMax)[1])}}"
                else -> "income: (${px(income, 0)} − ${px(scaler.mu(1), 0)}) / ${px(scaler.sd(1), 0)} = {v:${px(scaler.apply(f, Scaling.ZScore)[1])}}"
            }
            val incomeRaw = pct(1 - shares[0])
            val (headline, body) = when (s.tab) {
                0 -> "Raw, income owns {$incomeRaw} of the distance." to
                    "A dollar of income counts as much as a year of age, so k-NN reads income alone. Accuracy: ${px(acc[0], 3)}."
                1 -> "Raw, income owns $incomeRaw of the distance. Min-max gives age {${pct(shares[1])}}." to
                    "Accuracy moves from ${px(acc[0], 3)} to ${px(acc[1], 3)}. Min-max uses the extremes, so one outlier would squeeze every other value."
                else -> "Raw, income owns {$incomeRaw} of the distance. Z-scored, the split is near even." to
                    "Accuracy moves from ${px(acc[0], 3)} to ${px(acc[2], 3)} with the same model."
            }
            val bars = listOf(PpShare("Raw units", shares[0])) + when (s.tab) {
                1 -> listOf(PpShare("Min-max", shares[1]))
                2 -> listOf(PpShare("Z-scored", shares[2]))
                else -> emptyList()
            }
            listOf(
                PpFrame(
                    headline,
                    body,
                    listOf(scalingPlot(f, emptyList(), emptyList(), null), PpShareBars(bars), PpFormula(listOf(formula))),
                    legend = listOf(Triple(SimColors.Blue, SwatchStyle.Fill, "Age share"), Triple(IncomeOrange, SwatchStyle.Fill, "Income share")),
                ),
            )
        },
    )
}

private fun minMaxLab(): PpLab {
    val acc = FeatureScalingLab.results.map { it.accuracy }
    return PpLab(
        tabs = scalingTabs,
        startTab = 1,
        button = { "Pick Another Query" },
        onButton = { it.copy(flag = (it.flag + 1) % max(scalingQueries.size, 1)) },
        frames = { s ->
            val f = FeatureScalingLab.test[scalingQueries.getOrElse(s.flag) { 0 }].features
            val scaling = Scaling.values()[s.tab]
            val raw = neighbours(f, Scaling.Raw)
            val scaled = if (scaling == Scaling.Raw) emptyList() else neighbours(f, scaling)
            val age = f[0]
            val formula = when (scaling) {
                Scaling.Raw -> "d² = Δage² + Δincome²: income is ~${px((scaler.hi(1) - scaler.lo(1)) / (scaler.hi(0) - scaler.lo(0)), 0)}× wider"
                Scaling.MinMax -> "age′ = (${px(age, 0)} − ${px(scaler.lo(0), 0)}) / (${px(scaler.hi(0), 0)} − ${px(scaler.lo(0), 0)}) = {v:${px(scaler.apply(f, scaling)[0])}}"
                Scaling.ZScore -> "age′ = (${px(age, 0)} − ${px(scaler.mu(0), 1)}) / ${px(scaler.sd(0), 1)} = {v:${px(scaler.apply(f, scaling)[0])}}"
            }
            val (headline, body) = when (scaling) {
                Scaling.Raw -> "Raw, held-out accuracy is only {${px(acc[0], 3)}}." to
                    "The five nearest neighbours are picked by income alone; age barely moves the distance."
                else -> "Scaling lifts held-out accuracy from ${px(acc[0], 3)} to {${px(acc[scaling.ordinal], 3)}}." to
                    "In raw units income dominates the distance, so the neighbours ignore age."
            }
            val legend = listOf(Triple(SimColors.Active, SwatchStyle.Dot, "Query")) +
                (if (scaling == Scaling.Raw) emptyList() else listOf(Triple(SimColors.Answer, SwatchStyle.Line, "Neighbours, scaled"))) +
                Triple(SimColors.Grey, SwatchStyle.DashedLine, "Neighbours, raw")
            listOf(
                PpFrame(
                    headline,
                    body,
                    listOf(
                        scalingPlot(f, raw, scaled, "age → ↑ income"),
                        PpAccBars(scalingTabs.indices.map { Triple(scalingTabs[it], acc[it], it == s.tab) }),
                        PpFormula(listOf(formula)),
                    ),
                    legend = legend,
                ),
            )
        },
    )
}

// ── SMOTE ──

private class PpSmote(
    val majority: List<Pair<Double, Double>>,
    val minority: List<Pair<Double, Double>>,
    val synthetic: List<Triple<Int, Int, Double>>,
    val points: List<Pair<Double, Double>>,
    val before: Pair<Double, Double>,
    val after: Pair<Double, Double>,
)

private val smoteData: PpSmote by lazy {
    val r = PpRng(31)
    fun blob(n: Int, cx: Double, cy: Double, sx: Double, sy: Double) = List(n) {
        val x = cx + r.normal() * sx
        val y = cy + r.normal() * sy
        x to y
    }
    val majority = blob(60, 0.0, 0.0, 1.3, 0.9)
    val minority = blob(6, 0.9, 0.9, 0.45, 0.45)
    val testMajority = blob(60, 0.0, 0.0, 1.3, 0.9)
    val testMinority = blob(10, 0.9, 0.9, 0.45, 0.45)
    fun d2(a: Pair<Double, Double>, b: Pair<Double, Double>) = (a.first - b.first) * (a.first - b.first) + (a.second - b.second) * (a.second - b.second)
    val g = PpRng(53)
    val synthetic = List(24) {
        val o = g.int(minority.size)
        val near = minority.indices.filter { it != o }.sortedBy { d2(minority[it], minority[o]) }.take(5)
        val n = near[g.int(near.size)]
        Triple(o, n, g.u())
    }
    val points = synthetic.map { (o, n, lam) ->
        (minority[o].first + lam * (minority[n].first - minority[o].first)) to (minority[o].second + lam * (minority[n].second - minority[o].second))
    }
    fun score(minorityTrain: List<Pair<Double, Double>>): Pair<Double, Double> {
        val train = majority.map { it to 0 } + minorityTrain.map { it to 1 }
        val test = testMajority.map { it to 0 } + testMinority.map { it to 1 }
        var tp = 0
        var fp = 0
        test.forEach { (p, label) ->
            val votes = train.sortedBy { d2(it.first, p) }.take(3).count { it.second == 1 }
            if (votes >= 2) { if (label == 1) tp++ else fp++ }
        }
        return tp.toDouble() / testMinority.size to (if (tp + fp == 0) 0.0 else tp.toDouble() / (tp + fp))
    }
    PpSmote(majority, minority, synthetic, points, score(minority), score(minority + points))
}

private fun smoteLab(): PpLab = PpLab(
    stepped = true,
    frames = {
        val d = smoteData
        val all = d.majority + d.minority + d.points
        val xr = all.minOf { it.first } - 0.3 to all.maxOf { it.first } + 0.3
        val yr = all.minOf { it.second } - 0.3 to all.maxOf { it.second } + 0.3
        val base = d.majority.map { PpDot(it.first, it.second, SimColors.Blue, 2.8f) }
        fun minority() = d.minority.map { PpDot(it.first, it.second, ClassPink, 4f) }
        val chips = listOf(
            LabChip("recall", "${px(d.before.first)} → ${px(d.after.first)}", good = true),
            LabChip("precision", "${px(d.before.second)} → ${px(d.after.second)}", tint = StoryTone.Warn),
        )
        val legend = listOf(
            Triple(ClassPink, SwatchStyle.Dot, "Minority (${d.minority.size})"),
            Triple(SimColors.Answer, SwatchStyle.Dot, "Synthetic"),
            Triple(SimColors.Active, SwatchStyle.Dot, "Being made"),
        )
        val intro = PpFrame(
            "{${d.minority.size}} minority points against ${d.majority.size}: k-NN rarely votes minority.",
            "Held-out recall is only ${px(d.before.first)}. Copying rows would just stack duplicates on the same spots.",
            listOf(PpPlot(base + minority(), emptyList(), xr, yr, 1.75f), PpFormula(listOf("minority : majority = ${d.minority.size} : ${d.majority.size}"))),
            legend.take(1),
            chips,
            "Make a Synthetic",
        )
        val making = (0 until 4).map { j ->
            val (o, n, lam) = d.synthetic[j]
            val origin = d.minority[o]
            val nb = d.minority[n]
            val p = d.points[j]
            val made = d.points.take(j).map { PpDot(it.first, it.second, SimColors.Answer, 3.5f) }
            PpFrame(
                "SMOTE places a new point {${px(lam * 100, 0)}%} of the way to a minority neighbour.",
                if (j == 0) "It fills the minority region instead of copying rows. Recall rises; some precision is the price."
                else "Each synthetic point sits on a segment between two real minority points, never outside their span.",
                listOf(
                    PpPlot(
                        base + minority() + made + PpDot(origin.first, origin.second, ClassPink, 4f, ring = SimColors.Active) + PpDot(p.first, p.second, SimColors.Active, 4.5f),
                        listOf(PpSeg(origin.first, origin.second, nb.first, nb.second, SimColors.Active, dashed = true, width = 1.2f)),
                        xr,
                        yr,
                        1.75f,
                    ),
                    PpFormula(listOf("x_new = x + λ(x_nn − x), λ = {${px(lam)}}", "= (${px(p.first)}, ${px(p.second)})")),
                ),
                legend,
                chips,
                "Next Synthetic",
            )
        }
        val done = PpFrame(
            "{${d.points.size}} synthetic points later, recall rises from ${px(d.before.first)} to ${px(d.after.first)}.",
            "Precision moves from ${px(d.before.second)} to ${px(d.after.second)}: the filled region now claims a few majority points too.",
            listOf(
                PpPlot(base + d.points.map { PpDot(it.first, it.second, SimColors.Answer, 3.5f) } + minority(), emptyList(), xr, yr, 1.75f),
                PpFormula(listOf("minority : majority = ${d.minority.size + d.points.size} : ${d.majority.size}")),
            ),
            legend.take(2),
            chips,
            "Start Over",
        )
        listOf(intro) + making + done
    },
)

// ── Chi-square selection and RFE ──

private val featureNames = listOf("useful", "duplicate", "noise", "xorA", "xorB")

/** `useful` tracks y, `duplicate` tracks useful, `noise` is unrelated, and y is really xorA ⊕ xorB. */
private val selectionData: List<Pair<IntArray, Int>> by lazy {
    val r = PpRng(67)
    List(400) {
        val a = r.int(2)
        val b = r.int(2)
        val y = if (r.u() < 0.1) 1 - (a xor b) else a xor b
        val useful = if (r.u() < 0.15) 1 - y else y
        val duplicate = if (r.u() < 0.06) 1 - useful else useful
        val noise = r.int(2)
        intArrayOf(useful, duplicate, noise, a, b) to y
    }
}

private fun contingency(feature: (IntArray) -> Int): Array<IntArray> {
    val c = Array(2) { IntArray(2) }
    selectionData.forEach { (f, y) -> c[feature(f)][y] += 1 }
    return c
}

private fun chiSquare(c: Array<IntArray>): Double {
    val total = c.sumOf { it.sum() }.toDouble()
    var chi = 0.0
    for (i in 0..1) for (j in 0..1) {
        val expected = c[i].sum() * (c[0][j] + c[1][j]) / total
        if (expected > 0) chi += (c[i][j] - expected) * (c[i][j] - expected) / expected
    }
    return chi
}

private fun chiSquareLab(): PpLab {
    val scores = featureNames.indices.map { i -> chiSquare(contingency { it[i] }) }
    val order = featureNames.indices.sortedByDescending { scores[it] }
    val pair = chiSquare(contingency { it[3] xor it[4] })
    val pairAcc = selectionData.count { (f, y) -> (f[3] xor f[4]) == y } / selectionData.size.toDouble()
    val xorB = contingency { it[4] }
    val ks = (1..5).toList()
    return PpLab(
        param = PpParam("Keep top", "k", ks, 1),
        button = { if (it.flag == 0) "Test Feature Pairs" else "Show Single Features" },
        onButton = { it.copy(flag = 1 - it.flag) },
        frames = { s ->
            val k = ks[s.param]
            val kept = order.take(k).toSet()
            val rows = order.map { i ->
                val name = featureNames[i]
                val blind = i >= 3 && i !in kept
                when {
                    i in kept -> PpRow(listOf(name, px(scores[i], 1), "✓"), StoryTone.Done, StoryTone.Done)
                    blind -> PpRow(listOf(name, px(scores[i], 1), "blind spot"), StoryTone.Active, StoryTone.Active)
                    else -> PpRow(listOf(name, px(scores[i], 1), "—"), muted = true)
                }
            }
            val bothXor = 3 in kept && 4 in kept
            val (headline, body) = when {
                s.flag == 1 -> "Build the pair feature and χ² scores it {${px(pair, 1)}}, top of the table." to
                    "χ² only tests the columns you give it. An interaction has to be engineered before it can be selected."
                bothXor -> "At k = $k both xor features survive, but only because {nearly everything} does." to
                    "χ² still ranks them beside noise. They are kept by accident, not for their signal."
                else -> "χ² scores each feature {alone}, so xorA and xorB look like noise." to
                    "Together they predict y ${pct(pairAcc, 0)} of the time." + if (1 in kept) " It also keeps the near-duplicate." else ""
            }
            val table = PpTable(
                listOf("feature", "χ²", "keep"),
                (if (s.flag == 1) listOf(PpRow(listOf("xorA ⊕ xorB", px(pair, 1), "pair"), StoryTone.Answer, StoryTone.Answer)) else emptyList()) + rows,
                listOf(1.3f, 1f, 1f),
            )
            listOf(
                PpFrame(
                    headline,
                    body,
                    listOf(
                        table,
                        PpFormula(listOf("xorB vs y: [[${xorB[0][0]}, ${xorB[0][1]}], [${xorB[1][0]}, ${xorB[1][1]}]]", "xorA ⊕ xorB predicts y: {v:${pct(pairAcc, 0)}}")),
                    ),
                    legend = listOf(Triple(SimColors.Green, SwatchStyle.Fill, "Selected"), Triple(Color(0xFF8A7440), SwatchStyle.Fill, "Missed pair")),
                    chips = listOf(LabChip("model fits", "0"), LabChip("k", "$k", tint = StoryTone.Answer)),
                ),
            )
        },
    )
}

private class PpRound(val remaining: List<Int>, val weights: List<Double>, val mse: Double, val smallest: Int)

private val rfeRounds: List<PpRound> by lazy {
    val y = selectionData.map { it.second.toDouble() }
    var remaining = featureNames.indices.toList()
    val rounds = mutableListOf<PpRound>()
    while (true) {
        val x = selectionData.map { (f, _) -> DoubleArray(remaining.size) { f[remaining[it]].toDouble() } }
        val w = ppLeastSquares(x, y)
        val mse = x.indices.sumOf { (y[it] - ppPredict(w, x[it])).let { e -> e * e } } / x.size
        val weights = remaining.indices.map { abs(w[it + 1]) }
        val smallest = weights.indices.minBy { weights[it] }
        rounds += PpRound(remaining, weights, mse, smallest)
        if (remaining.size == 1) break
        remaining = remaining.filterIndexed { i, _ -> i != smallest }
    }
    rounds
}

private fun rSquaredAlone(i: Int): Double {
    val r = ppCorr(selectionData.map { it.first[i].toDouble() }, selectionData.map { it.second.toDouble() })
    return r * r
}

private fun rfeLab(): PpLab = PpLab(
    stepped = true,
    frames = {
        val rounds = rfeRounds.filter { it.remaining.size > 1 }
        val last = rfeRounds.last()
        val top = rfeRounds.maxOf { r -> r.weights.max() }
        val legend = listOf(Triple(SimColors.Active, SwatchStyle.Fill, "Smallest |w|: dropped"), Triple(SimColors.Blue, SwatchStyle.Fill, "Kept"))
        fun bars(r: PpRound, mark: Boolean) = PpWeights(r.remaining.indices.map { PpWeight(featureNames[r.remaining[it]], r.weights[it], mark && it == r.smallest) }, top)
        val first = rounds[0]
        val intro = PpFrame(
            "RFE fits a linear model on all {${first.remaining.size}} features and reads each |w|.",
            "The features are 0/1, so the weights are comparable. Each round drops the smallest and refits.",
            listOf(bars(first, false), PpFormula(listOf("fit 1: y ~ all ${featureNames.size} features"))),
            legend.drop(1),
            listOf(LabChip("MSE", px(first.mse, 4), tint = StoryTone.Path), LabChip("fits", "1")),
            "Find Smallest",
        )
        val steps = rounds.mapIndexed { n, r ->
            val drop = r.remaining[r.smallest]
            val name = featureNames[drop]
            val alone = rSquaredAlone(drop)
            val before = rounds.take(n).map { featureNames[it.remaining[it.smallest]] }
            val (headline, body) = when {
                alone > 0.3 -> "{$name} is dropped though alone it explains ${pct(alone, 0)} of y." to
                    "Its near-copy \"${if (name == "duplicate") "useful" else "duplicate"}\" already carries the signal, so RFE sees it as redundant."
                drop >= 3 -> "{$name} has the smallest |w|, so it goes." to
                    "A linear model can't express XOR, so the pair looks as empty to RFE as it does to χ²."
                else -> "{$name} has the smallest |w|, so it goes." to "Noise carries no signal; its weight sits near zero."
            }
            PpFrame(
                headline,
                body,
                listOf(
                    bars(r, true),
                    PpFormula(
                        (if (before.isEmpty()) emptyList() else listOf("dropped: ${before.joinToString(", ")}")) +
                            "round ${n + 1}: drop {$name}, |w| = ${px(r.weights[r.smallest], 3)}",
                    ),
                ),
                legend,
                listOf(LabChip("MSE", px(r.mse, 4), tint = StoryTone.Path), LabChip("R² alone", px(alone))),
                "Drop Smallest",
            )
        }
        val kept = featureNames[last.remaining[0]]
        val done = PpFrame(
            "Only {$kept} is left after ${rounds.size + 1} fits.",
            "RFE pays one model fit per round; χ² needed none. Neither saw the xor pair, because a linear model can't express it.",
            listOf(bars(last, false), PpFormula(listOf("dropped: ${rounds.joinToString(", ") { featureNames[it.remaining[it.smallest]] }}"))),
            legend.drop(1),
            listOf(LabChip("MSE", px(last.mse, 4), tint = StoryTone.Path), LabChip("fits", "${rounds.size + 1}")),
            "Start Over",
        )
        listOf(intro) + steps + done
    },
)

private fun preprocessLab(topicId: String): PpLab = when (topicId) {
    "missing_value_imputation" -> imputeLab()
    "label_encoding" -> labelEncodingLab()
    "one_hot_encoding" -> oneHotLab()
    "z_score_standardization" -> zScoreLab()
    "min_max_normalization" -> minMaxLab()
    "smote" -> smoteLab()
    "chi_square_selection" -> chiSquareLab()
    "rfe" -> rfeLab()
    else -> outlierLab()
}

/** Builds every frame each control can reach (every tab, stepper value and press of the action). */
internal fun preprocessStoryFrameCount(topicId: String): Int {
    val lab = preprocessLab(topicId)
    val states = if (lab.stepped) listOf(lab.initial) else buildList {
        for (t in 0 until max(lab.tabs.size, 1)) for (p in 0 until (lab.param?.values?.size ?: 1)) {
            var st = PpState(t, p, 0)
            repeat(6) {
                add(st)
                st = lab.onButton(st)
            }
        }
    }
    return states.sumOf { s ->
        val frames = lab.frames(s)
        require(frames.isNotEmpty()) { "$topicId built no frames at $s" }
        frames.forEach { f ->
            require(f.headline.isNotBlank() && f.body.isNotBlank()) { "$topicId has no narration at $s" }
            require(f.blocks.isNotEmpty()) { "$topicId drew nothing at $s" }
            if (lab.stepped) require(f.action.isNotBlank()) { "$topicId has a step without an action" }
            f.blocks.filterIsInstance<PpPlot>().forEach { p ->
                require(p.dots.all { it.x.isFinite() && it.y.isFinite() }) { "$topicId drew a non-finite dot at $s" }
            }
        }
        frames.size
    }
}

// ── Lab ──

@Composable
internal fun PreprocessStorySection(topicId: String) {
    val lab = remember(topicId) { preprocessLab(topicId) }
    if (lab.stepped) SteppedSection(topicId, lab) else InteractiveSection(topicId, lab)
}

@Composable
private fun SteppedSection(topicId: String, lab: PpLab) {
    val frames = remember(topicId) { lab.frames(lab.initial) }
    val playback = rememberPlaybackState(key = topicId, stepCount = frames.size, initialSpeedMs = 1000f)
    val frame = frames[playback.index.coerceIn(0, frames.lastIndex)]
    Column(modifier = Modifier.fillMaxWidth()) {
        FrameBody(frame)
        PlaybackTransport(playback, captions = frames.map { storyPlain(it.headline) }, action = { frames[it.coerceIn(0, frames.lastIndex)].action })
    }
}

@Composable
private fun InteractiveSection(topicId: String, lab: PpLab) {
    var state by remember(topicId) { mutableStateOf(lab.initial) }
    val frame = remember(state) { lab.frames(state).first() }
    val dock = LocalLabDock.current

    val controls: @Composable () -> Unit = {
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (lab.tabs.isNotEmpty()) LabSegments(lab.tabs, state.tab) { state = state.copy(tab = it) }
            lab.param?.let { p ->
                LabParamStepper(LabParam(p.name, p.name, p.symbol, p.format(p.values[state.param]), state.param > 0, state.param < p.values.lastIndex)) { d ->
                    state = state.copy(param = (state.param + d).coerceIn(0, p.values.lastIndex))
                }
            }
            lab.button?.let { label ->
                LabButton(label(state), primary = true, modifier = Modifier.fillMaxWidth()) { state = lab.onButton(state) }
            }
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        FrameBody(frame)
        if (dock == null) {
            HorizontalDivider(modifier = Modifier.padding(top = 16.dp, bottom = 14.dp), color = MaterialTheme.colorScheme.outline)
            controls()
        }
    }

    if (dock != null) {
        SideEffect {
            dock.controls = controls
            dock.navAction = LabNavAction(Icons.Filled.Refresh, "Reset") { state = lab.initial }
        }
        DisposableEffect(dock) {
            onDispose {
                dock.controls = null
                dock.navAction = null
            }
        }
    }
}

@Composable
private fun FrameBody(frame: PpFrame) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            frame.blocks.forEach { block ->
                when (block) {
                    is PpPlot -> PlotView(block)
                    is PpStrip -> StripView(block)
                    is PpTable -> TableView(block)
                    is PpFormula -> FormulaView(block.lines)
                    is PpShareBars -> ShareBarsView(block)
                    is PpAccBars -> AccBarsView(block)
                    is PpOneHot -> OneHotView(block)
                    is PpWeights -> WeightsView(block)
                }
            }
            if (frame.legend.isNotEmpty()) StoryLegendRow(frame.legend, Modifier.padding(top = 2.dp))
        }
    }
    LabChips(frame.chips, Modifier.padding(top = 16.dp))
    LabStoryNarration(frame.headline, frame.body, Modifier.padding(top = 16.dp))
}

// ── Rendering ──

private fun Color.dim(f: Float) = copy(alpha = alpha * f)

private fun Modifier.stage() = this.clip(RoundedCornerShape(12.dp)).background(Color.Black.copy(alpha = 0.16f))

@Composable
private fun FormulaView(lines: List<String>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SimColors.Tint, RoundedCornerShape(10.dp))
            .padding(vertical = 10.dp, horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        lines.forEach { line ->
            Text(
                storyAnnotated(line),
                fontFamily = IBMPlexMono,
                fontSize = 13.sp,
                lineHeight = 20.sp,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
            )
        }
    }
}

@Composable
private fun PlotView(plot: PpPlot) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(modifier = Modifier.fillMaxWidth().stage()) {
        Box {
            Canvas(modifier = Modifier.fillMaxWidth().aspectRatio(plot.aspect)) {
                val pad = 10.dp.toPx()
                val (xLo, xHi) = plot.xRange
                val (yLo, yHi) = plot.yRange
                fun at(x: Double, y: Double) = Offset(
                    pad + ((x - xLo) / (xHi - xLo)).toFloat() * (size.width - 2 * pad),
                    size.height - pad - ((y - yLo) / (yHi - yLo)).toFloat() * (size.height - 2 * pad),
                )
                val dash = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx()))
                plot.segs.filter { it.dashed }.forEach { s ->
                    drawLine(s.color, at(s.x0, s.y0), at(s.x1, s.y1), strokeWidth = s.width.dp.toPx(), pathEffect = dash)
                }
                plot.dots.forEach { d ->
                    val c = at(d.x, d.y)
                    if (d.hollow) drawCircle(d.color.copy(alpha = 0.7f), d.r.dp.toPx(), c, style = Stroke(1.2.dp.toPx()))
                    else drawCircle(d.color, d.r.dp.toPx(), c)
                    d.ring?.let { drawCircle(it, (d.r + 4).dp.toPx(), c, style = Stroke(2.dp.toPx())) }
                }
                plot.segs.filter { !it.dashed }.forEach { s ->
                    drawLine(s.color, at(s.x0, s.y0), at(s.x1, s.y1), strokeWidth = s.width.dp.toPx(), cap = StrokeCap.Round)
                }
                // The highlighted dots draw last, over the lines.
                plot.dots.filter { it.color == SimColors.Active }.forEach { d -> drawCircle(d.color, d.r.dp.toPx(), at(d.x, d.y)) }
            }
            plot.caption?.let {
                Text(it, fontSize = 12.sp, color = muted, modifier = Modifier.align(Alignment.BottomStart).padding(start = 12.dp, bottom = 4.dp))
            }
        }
        if (plot.slots.isNotEmpty()) {
            Row(modifier = Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, bottom = 8.dp)) {
                plot.slots.forEach {
                    Text(it, fontFamily = IBMPlexMono, fontSize = 12.sp, color = muted, textAlign = TextAlign.Center, maxLines = 1, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun StripView(strip: PpStrip) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val ring = if (strip.caught) SimColors.Green else SimColors.Red
    Column(modifier = Modifier.fillMaxWidth().stage().padding(4.dp)) {
        Row(modifier = Modifier.fillMaxWidth().height(96.dp), verticalAlignment = Alignment.CenterVertically) {
            Canvas(modifier = Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(10.dp)).background(SimColors.Tint.dim(0.8f))) {
                val lo = strip.clean.min()
                val hi = strip.clean.max()
                val r = 3.4.dp.toPx()
                val pad = 18.dp.toPx()
                val band = size.height / 2 - 2 * r - 6.dp.toPx()
                strip.clean.forEachIndexed { i, v ->
                    val x = pad + ((v - lo) / (hi - lo)).toFloat() * (size.width - 2 * pad)
                    drawCircle(DotGrey, r, Offset(x, size.height / 2 + strip.jitter[i].toFloat() * band))
                }
            }
            Text("···", color = muted, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 8.dp))
            Canvas(modifier = Modifier.width(76.dp).fillMaxHeight().clip(RoundedCornerShape(10.dp)).background(SimColors.Tint.dim(0.8f))) {
                val r = 7.dp.toPx()
                val gap = 3.dp.toPx()
                val perColumn = max(1, ((size.height - 8.dp.toPx()) / (2 * r + gap)).toInt())
                val columns = (strip.extremes + perColumn - 1) / perColumn
                val width = columns * 2 * r + (columns - 1) * gap
                val rows = min(strip.extremes, perColumn)
                val height = rows * 2 * r + (rows - 1) * gap
                repeat(strip.extremes) { i ->
                    val col = i % columns
                    val row = i / columns
                    val c = Offset((size.width - width) / 2 + r + col * (2 * r + gap), (size.height - height) / 2 + r + row * (2 * r + gap))
                    drawCircle(ClassPink, r - 2.dp.toPx(), c)
                    drawCircle(ring, r - 1.dp.toPx(), c, style = Stroke(2.dp.toPx()))
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(top = 6.dp, start = 8.dp, end = 4.dp, bottom = 2.dp)) {
            Text(px(strip.clean.min(), 0), fontFamily = IBMPlexMono, fontSize = 12.sp, color = muted)
            Box(Modifier.weight(1f))
            Text(px(strip.clean.max(), 0), fontFamily = IBMPlexMono, fontSize = 12.sp, color = muted, modifier = Modifier.padding(end = 36.dp))
            Text(strip.extremeLabel, fontFamily = IBMPlexMono, fontSize = 12.sp, color = muted, textAlign = TextAlign.Center, modifier = Modifier.width(76.dp))
        }
    }
}

@Composable
private fun TableView(table: PpTable) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val onSurface = MaterialTheme.colorScheme.onSurface
    fun align(i: Int) = if (i == 0) TextAlign.Start else TextAlign.End
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
            table.header.forEachIndexed { i, h ->
                Text(h, fontSize = 13.sp, color = muted, textAlign = align(i), maxLines = 1, modifier = Modifier.weight(table.weights[i]))
            }
        }
        table.rows.forEach { row ->
            val ink = when {
                row.muted -> muted
                row.ink != null -> row.ink.ink()
                else -> onSurface
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .background(
                        when (row.fill) {
                            null -> Color.Transparent
                            StoryTone.Answer -> SimColors.Answer.copy(alpha = 0.28f)
                            else -> row.fill.color().copy(alpha = 0.18f)
                        },
                        RoundedCornerShape(8.dp),
                    )
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                row.cells.forEachIndexed { i, cell ->
                    Text(
                        cell,
                        fontFamily = if (i == 0) null else IBMPlexMono,
                        fontWeight = if (i == 0) FontWeight.SemiBold else FontWeight.Normal,
                        fontSize = if (i == 0) 16.sp else 15.sp,
                        color = ink,
                        textAlign = align(i),
                        maxLines = 1,
                        modifier = Modifier.weight(table.weights[i]),
                    )
                }
            }
        }
    }
}

@Composable
private fun ShareBarsView(block: PpShareBars) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        block.rows.forEach { row ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(row.label, fontSize = 14.sp, color = muted)
                    Box(Modifier.weight(1f))
                    Text("age ${pct(row.ageShare)}", fontFamily = IBMPlexMono, fontSize = 13.sp, color = muted)
                }
                Canvas(modifier = Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(7.dp))) {
                    val split = (row.ageShare.toFloat() * size.width)
                    drawRect(IncomeOrange)
                    drawRect(SimColors.Blue, size = size.copy(width = split))
                }
            }
        }
    }
}

@Composable
private fun AccBarsView(block: PpAccBars) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        block.rows.forEach { (label, value, on) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    label,
                    fontSize = 15.sp,
                    fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (on) StoryTone.Answer.ink() else muted,
                    modifier = Modifier.width(78.dp),
                )
                Canvas(modifier = Modifier.weight(1f).height(12.dp).clip(RoundedCornerShape(6.dp))) {
                    drawRect(SimColors.Tint)
                    drawRect(if (on) SimColors.Answer else SimColors.Grey.copy(alpha = 0.45f), size = size.copy(width = value.toFloat() * size.width))
                }
                Text(px(value, 3), fontFamily = IBMPlexMono, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(start = 12.dp))
            }
        }
    }
}

@Composable
private fun OneHotView(block: PpOneHot) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val onSurface = MaterialTheme.colorScheme.onSurface
    val labelWeight = 1.25f
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.weight(labelWeight))
            block.columns.forEachIndexed { c, name ->
                val gone = block.dropped && c == 0
                Text(
                    name,
                    fontSize = 14.sp,
                    color = muted.copy(alpha = if (gone) 0.45f else 1f),
                    textDecoration = if (gone) TextDecoration.LineThrough else null,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        block.rows.forEachIndexed { r, colour ->
            val current = r == block.current
            val done = r < block.current
            Row(modifier = Modifier.fillMaxWidth().height(38.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(
                    modifier = Modifier
                        .weight(labelWeight)
                        .fillMaxHeight()
                        .background(if (current) SimColors.Active.copy(alpha = 0.22f) else SimColors.Tint.dim(if (done) 1f else 0.6f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(
                        colour,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = when {
                            current -> StoryTone.Active.ink()
                            done -> onSurface
                            else -> muted.copy(alpha = 0.7f)
                        },
                        maxLines = 1,
                    )
                }
                block.columns.forEachIndexed { c, name ->
                    val one = name == colour
                    val gone = block.dropped && c == 0
                    val fill = when {
                        !current && !done -> SimColors.Tint.dim(0.55f)
                        one && current -> SimColors.Active
                        one -> SimColors.Answer
                        else -> SimColors.Tint.dim(1f)
                    }
                    Box(
                        modifier = Modifier.weight(1f).fillMaxHeight().background(fill.copy(alpha = fill.alpha * (if (gone) 0.35f else 1f)), RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            if (!current && !done) "—" else if (one) "1" else "0",
                            fontFamily = IBMPlexMono,
                            fontSize = 15.sp,
                            fontWeight = if (one && (current || done)) FontWeight.Bold else FontWeight.Normal,
                            color = when {
                                one && current -> Color(0xFF1F1A0A)
                                one && done -> Color.White
                                else -> muted.copy(alpha = if (gone || (!current && !done)) 0.5f else 1f)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WeightsView(block: PpWeights) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        block.rows.forEach { w ->
            val ink = if (w.smallest) StoryTone.Active.ink() else onSurface
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(w.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = ink, maxLines = 1, modifier = Modifier.widthIn(min = 92.dp).width(92.dp))
                Canvas(modifier = Modifier.weight(1f).height(12.dp).clip(RoundedCornerShape(6.dp))) {
                    drawRect(SimColors.Tint)
                    val share = (w.value / block.top).toFloat().coerceIn(0f, 1f)
                    drawRect(if (w.smallest) SimColors.Active else SimColors.Blue, size = size.copy(width = max(share * size.width, 4.dp.toPx())))
                }
                Text(px(w.value, 3), fontFamily = IBMPlexMono, fontSize = 15.sp, color = ink, textAlign = TextAlign.End, modifier = Modifier.width(64.dp))
            }
        }
    }
}
