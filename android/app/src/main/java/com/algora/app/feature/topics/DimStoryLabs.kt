package com.algora.app.feature.topics

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.algora.app.core.ui.theme.IBMPlexMono
import com.algora.app.core.ui.theme.SimColors
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

// ── Dimensionality-reduction storyboards ─────────────────────────────────────
// PCA, SVD, incremental PCA, kernel PCA, ICA, factor analysis, t-SNE, UMAP and LLE. Each is one card (a
// scatter, strips, a curve, loading bars, tiles or a table), the arithmetic of the step, chips and a
// headline, then a step track, a back-and-action row, a button or a stepper. Every number is computed
// from the seeded data below; the generator is a plain LCG so the iOS port reproduces it exactly.

internal val dimStoryTopicIds = setOf("pca", "svd", "incremental_pca", "kernel_pca", "ica", "factor_analysis", "tsne", "umap", "lle")

private val DBlue = Color(0xFF4F7FE0)
private val DOrange = Color(0xFFF08A3C)
private val DGreen = Color(0xFF3F9A62)
private val DPink = Color(0xFFD6457A)
private val DViolet = SimColors.Answer
private val NotYet = Color(0xFF4B5160)
private val LinkGrey = Color(0xFF6B7280)
private val ClusterColors = listOf(DBlue, DOrange, DGreen)

// ── Formatting and small math ──

private fun num(v: Double, d: Int = 2): String {
    var p = 1L
    repeat(d) { p *= 10 }
    val r = (abs(v) * p + 0.5).toLong()
    val body = if (d == 0) "${r / p}" else "${r / p}." + (r % p).toString().padStart(d, '0')
    return if (v < 0 && r != 0L) "−$body" else body
}

private fun pct(v: Double, d: Int = 1) = num(v * 100, d) + "%"

private class DsRng(private var state: Long) {
    fun u(): Double {
        state = (state * 1103515245L + 12345L) and 0x7fffffffL
        return state / 2147483648.0
    }

    fun g(): Double {
        val a = max(u(), 1e-12)
        val b = u()
        return sqrt(-2 * ln(a)) * cos(2 * PI * b)
    }
}

private class DsP(val x: Double, val y: Double)

private fun boundsOf(pts: List<DsP>, pad: Double) =
    (pts.minOf { it.x } - pad to pts.maxOf { it.x } + pad) to (pts.minOf { it.y } - pad to pts.maxOf { it.y } + pad)

/** Sample covariance (n − 1) of 2-D points: mean, then cxx, cxy, cyy. */
private class Cov2(val mx: Double, val my: Double, val a: Double, val b: Double, val c: Double) {
    private val tr = (a + c) / 2
    private val d = sqrt(((a - c) / 2) * ((a - c) / 2) + b * b)
    val l1 = tr + d
    val l2 = tr - d
    /** Angle of the top eigenvector. */
    val angle = 0.5 * atan2(2 * b, a - c)
}

private fun cov2(pts: List<DsP>): Cov2 {
    val n = pts.size
    val mx = pts.sumOf { it.x } / n
    val my = pts.sumOf { it.y } / n
    val a = pts.sumOf { (it.x - mx) * (it.x - mx) } / (n - 1)
    val b = pts.sumOf { (it.x - mx) * (it.y - my) } / (n - 1)
    val c = pts.sumOf { (it.y - my) * (it.y - my) } / (n - 1)
    return Cov2(mx, my, a, b, c)
}

private fun axisGap(a: Double, b: Double): Double {
    val d = abs(a - b) * 180 / PI % 180
    return min(d, 180 - d)
}

private fun sq(v: Double) = v * v

private fun sqDist(a: DoubleArray, b: DoubleArray) = a.indices.sumOf { sq(a[it] - b[it]) }

private fun corr(a: List<Double>, b: List<Double>): Double {
    val ma = a.average()
    val mb = b.average()
    return a.indices.sumOf { (a[it] - ma) * (b[it] - mb) } / sqrt(a.sumOf { sq(it - ma) } * b.sumOf { sq(it - mb) })
}

// ── Model ──

private class DsDot(val p: DsP, val color: Color, val r: Float = 3.5f, val ring: Color? = null, val ringR: Float = 0f, val ringWidth: Float = 2f)

private class DsSeg(val a: DsP, val b: DsP, val color: Color, val dashed: Boolean = false, val width: Float = 1.5f)

private sealed interface DsBlock

private class DsPlot(
    val dots: List<DsDot>,
    val xr: Pair<Double, Double>,
    val yr: Pair<Double, Double>,
    val segs: List<DsSeg> = emptyList(),
    val aspect: Float = 1.75f,
    /** Equal scale on both axes, so angles stay true. */
    val uniform: Boolean = true,
) : DsBlock

private class DsStrip(val label: String, val values: List<Double>, val colors: List<Color>)

private class DsStrips(val strips: List<DsStrip>) : DsBlock

private class DsCurve(val caption: String, val ys: List<Double>, val marker: Int, val markerLabel: String) : DsBlock

private class DsCell(val label: String, val value: String, val fill: StoryTone? = null, val ink: StoryTone? = null, val muted: Boolean = false)

private class DsCells(val cells: List<DsCell>) : DsBlock

private class DsRow(val cells: List<String>, val ink: StoryTone? = null, val fill: StoryTone? = null, val muted: Boolean = false)

private class DsTable(val header: List<String>, val rows: List<DsRow>, val weights: List<Float>) : DsBlock

private class DsFormula(val lines: List<String>) : DsBlock

private class DsLoad(val name: String, val lambda: Double?, val highlight: Boolean = false)

private class DsLoadings(val rows: List<DsLoad>) : DsBlock

private class DsFrame(
    val headline: String,
    val body: String,
    val blocks: List<DsBlock>,
    val legend: List<Triple<Color, SwatchStyle, String>> = emptyList(),
    val chips: List<LabChip> = emptyList(),
    val action: String = "",
)

private class DsParam(val name: String, val symbol: String, val values: List<Double>, val initial: Int, val format: (Double) -> String)

private data class DsState(val tab: Int = 0, val param: Int = 0, val flag: Int = 0, val index: Int = 0)

private enum class DsControl {
    /** A step track with the step's action beside a back button. */
    Track,

    /** A step track over a stepper that re-runs the story, no action row. */
    TrackParam,

    /** Segmented tabs over a back-and-action row. */
    Steps,

    /** A stepper, and a button when [DsLab.button] is set. */
    Button,
}

private class DsLab(
    val control: DsControl,
    val tabs: List<String> = emptyList(),
    val param: DsParam? = null,
    val button: ((DsState) -> String)? = null,
    val onButton: (DsState) -> DsState = { it },
    val frames: (DsState) -> List<DsFrame>,
) {
    val initial get() = DsState(0, param?.initial ?: 0, 0, 0)
}

// ── PCA data, shared by PCA, SVD and incremental PCA ──

private val pcaData: List<DsP> by lazy {
    val r = DsRng(9)
    List(18) {
        val x = -2.0 + 4.0 * it / 17 + r.g() * 0.22
        val y = 0.6 * x + r.g() * 0.13
        DsP(x, y)
    }
}

private fun unit(angle: Double) = DsP(cos(angle), sin(angle))

private fun along(c: DsP, u: DsP, t: Double) = DsP(c.x + u.x * t, c.y + u.y * t)

private fun project(p: DsP, c: DsP, u: DsP): Double = (p.x - c.x) * u.x + (p.y - c.y) * u.y

private val pcaLegend = listOf(
    Triple(DBlue, SwatchStyle.Dot, "Data"), Triple(DGreen, SwatchStyle.Dot, "Projection"),
    Triple(DViolet, SwatchStyle.Line, "PC1"), Triple(SimColors.Grey, SwatchStyle.DashedLine, "PC2"),
)

private fun matrix2(cv: Cov2) = "C = [${num(cv.a, 3)}, ${num(cv.b, 3)}; ${num(cv.b, 3)}, ${num(cv.c, 3)}]"

private fun pcaLab(): DsLab = DsLab(DsControl.Track) {
    val pts = pcaData
    val cv = cov2(pts)
    val mean = DsP(cv.mx, cv.my)
    val u1 = unit(cv.angle)
    val u2 = DsP(-u1.y, u1.x)
    val reach = pts.maxOf { abs(project(it, mean, u1)) } * 1.15
    val (xr, yr) = boundsOf(pts, 0.35)
    val keep = cv.l1 / (cv.l1 + cv.l2)
    val pc1 = DsSeg(along(mean, u1, -reach), along(mean, u1, reach), DViolet, width = 2f)
    val pc2 = DsSeg(along(mean, u2, -reach * 0.35), along(mean, u2, reach * 0.35), SimColors.Grey, dashed = true, width = 1.2f)
    val proj = pts.map { along(mean, u1, project(it, mean, u1)) }
    val dataDots = pts.map { DsDot(it, DBlue) }
    val meanDot = DsDot(mean, Color.Transparent, 0f, ring = Color.White, ringR = 6f, ringWidth = 2f)
    val lambdas = "λ₁ = {v:${num(cv.l1, 3)}}  λ₂ = ${num(cv.l2, 3)}"
    // Centred copies for the covariance step, so the axes cross at the origin.
    val centred = pts.map { DsP(it.x - mean.x, it.y - mean.y) }
    val (cxr, cyr) = boundsOf(centred, 0.35)
    val scores = pts.map { project(it, mean, u1) }
    listOf(
        DsFrame(
            "18 points where x and y {rise together}.",
            "PCA looks for the direction the data varies most along. First it centres the cloud on its mean.",
            listOf(DsPlot(dataDots + meanDot, xr, yr), DsFormula(listOf("mean = (${num(cv.mx)}, ${num(cv.my)})"))),
            listOf(Triple(DBlue, SwatchStyle.Dot, "Data"), Triple(Color.White, SwatchStyle.Ring, "Mean")),
            action = "Centre Data",
        ),
        DsFrame(
            "Covariance {${num(cv.b, 3)}}: x and y move together.",
            "The diagonal holds each variance; the off-diagonal says how strongly the two co-vary.",
            listOf(
                DsPlot(
                    centred.map { DsDot(it, DBlue) }, cxr, cyr,
                    listOf(
                        DsSeg(DsP(cxr.first, 0.0), DsP(cxr.second, 0.0), SimColors.Grey.copy(alpha = 0.5f), width = 1f),
                        DsSeg(DsP(0.0, cyr.first), DsP(0.0, cyr.second), SimColors.Grey.copy(alpha = 0.5f), width = 1f),
                    ),
                ),
                DsFormula(listOf(matrix2(cv))),
            ),
            listOf(Triple(DBlue, SwatchStyle.Dot, "Centred data")),
            action = "Find Axes",
        ),
        DsFrame(
            "PC1 runs along the cloud, with λ₁ = {v:${num(cv.l1, 3)}}.",
            "The eigenvectors of C are the new axes; each eigenvalue is the variance along its axis.",
            listOf(DsPlot(dataDots, xr, yr, listOf(pc2, pc1)), DsFormula(listOf(matrix2(cv), lambdas))),
            pcaLegend.filter { it.third != "Projection" },
            action = "Drop PC2",
        ),
        DsFrame(
            "PC1 keeps {${pct(keep)}} of the variance.",
            "Dropping PC2 loses only the short gaps drawn between each point and its projection.",
            listOf(
                DsPlot(
                    dataDots + proj.map { DsDot(it, DGreen, 3f) }, xr, yr,
                    listOf(pc2, pc1) + pts.indices.map { DsSeg(pts[it], proj[it], SimColors.Grey, width = 1f) },
                ),
                DsFormula(listOf(matrix2(cv), lambdas)),
            ),
            pcaLegend,
            action = "Project Points",
        ),
        DsFrame(
            "Each point is now {one number}: its PC1 score.",
            "18 × 2 values became 18 × 1, and the scores still span the cloud's full length.",
            listOf(
                DsPlot(
                    scores.map { DsDot(DsP(it, 0.0), DGreen) }, -reach to reach, -0.6 to 0.6,
                    listOf(DsSeg(DsP(-reach, 0.0), DsP(reach, 0.0), DViolet, width = 2f)), aspect = 5f,
                ),
                DsFormula(listOf("z = (x − mean) · v₁,  range ${num(scores.min())} to ${num(scores.max())}")),
            ),
            listOf(Triple(DGreen, SwatchStyle.Dot, "PC1 score"), Triple(DViolet, SwatchStyle.Line, "PC1")),
            action = "Start Over",
        ),
    )
}

// ── SVD ──

private fun svdLab(): DsLab = DsLab(DsControl.Steps, tabs = listOf("Rank 1", "Rank 2")) { s ->
    val pts = pcaData
    val n = pts.size
    val cv = cov2(pts)
    val mean = DsP(cv.mx, cv.my)
    val u1 = unit(cv.angle)
    val u2 = DsP(-u1.y, u1.x)
    val s1 = sqrt(cv.l1 * (n - 1))
    val s2 = sqrt(cv.l2 * (n - 1))
    val (xr, yr) = boundsOf(pts, 0.35)
    val rank = s.tab + 1
    val fits = if (rank == 1) pts.map { along(mean, u1, project(it, mean, u1)) } else pts
    val energy = if (rank == 1) s1 * s1 / (s1 * s1 + s2 * s2) else 1.0
    val error = if (rank == 1) sqrt(pts.indices.sumOf { sq(pts[it].x - fits[it].x) + sq(pts[it].y - fits[it].y) }) else 0.0
    // v₁ and v₂ drawn from the centre, 2 standard deviations long, so their lengths compare like σ₁ and σ₂.
    val v1 = DsSeg(mean, along(mean, u1, 2 * sqrt(cv.l1)), DViolet, width = 2.5f)
    val v2 = DsSeg(mean, along(mean, u2, 2 * sqrt(cv.l2)), SimColors.Active, width = 2.5f)
    val centre = DsDot(mean, Color.White, 3f, ring = Color.White, ringR = 1.5f, ringWidth = 1.5f)
    val fitName = "Rank-$rank fit"
    val legend = listOf(
        Triple(DViolet, SwatchStyle.Line, "v₁ (length ∝ σ₁)"), Triple(SimColors.Active, SwatchStyle.Line, "v₂"), Triple(DGreen, SwatchStyle.Dot, fitName),
    )
    val formula = DsFormula(listOf("X = U Σ Vᵀ, σ₁ = {v:${num(s1, 3)}}, σ₂ = {${num(s2, 3)}}", "σ₁² / (n−1) = ${num(cv.l1, 3)} = λ₁ of PCA"))
    val darkData = DBlue.copy(alpha = 0.75f)
    listOf(
        DsFrame(
            "X holds {18 rows × 2 columns}, centred on the mean.",
            "SVD factors any matrix into a rotation, a stretch and a rotation, with no covariance matrix needed.",
            listOf(DsPlot(pts.map { DsDot(it, DBlue) } + centre, xr, yr), DsFormula(listOf("X = U Σ Vᵀ"))),
            listOf(Triple(DBlue, SwatchStyle.Dot, "Data"), Triple(Color.White, SwatchStyle.Dot, "Centre")),
            action = "Decompose",
        ),
        DsFrame(
            if (rank == 1) "Keeping σ₁ alone rebuilds {${pct(energy)}} of the data's energy."
            else "Keeping σ₁ and σ₂ rebuilds {100%} of the data's energy.",
            if (rank == 1) "On centred data these are PCA's axes, found without forming the covariance matrix."
            else "A 2-column matrix has only two singular values, so rank 2 is X itself.",
            listOf(DsPlot(pts.map { DsDot(it, darkData) } + fits.map { DsDot(it, DGreen, 3f) } + centre, xr, yr, listOf(v1, v2)), formula),
            legend,
            action = "Reconstruct",
        ),
        DsFrame(
            if (rank == 1) "The rank-1 error is exactly σ₂: {${num(error, 3)}}." else "Rank 2 rebuilds X exactly: error {m:0.000}.",
            if (rank == 1) "No rank-1 matrix gets closer. The residual is the dropped v₂ direction, drawn as gaps."
            else "Compression only pays when you keep fewer singular values than there are columns.",
            listOf(
                DsPlot(
                    pts.map { DsDot(it, darkData) } + fits.map { DsDot(it, DGreen, 3f) }, xr, yr,
                    listOf(v1) + if (rank == 1) pts.indices.map { DsSeg(pts[it], fits[it], SimColors.Grey, width = 1f) } else emptyList(),
                ),
                DsFormula(listOf("‖X − X$rank‖ = ${num(error, 3)}" + if (rank == 1) " = σ₂" else "")),
            ),
            legend.filter { it.third != "v₂" },
            action = "Start Over",
        ),
    )
}

// ── Incremental PCA ──

private val streamOrder: List<Int> by lazy {
    val r = DsRng(13)
    val o = MutableList(18) { it }
    for (i in 17 downTo 1) {
        val j = (r.u() * (i + 1)).toInt()
        val t = o[i]
        o[i] = o[j]
        o[j] = t
    }
    o
}

private fun incrementalLab(): DsLab = DsLab(DsControl.Track) {
    val pts = pcaData
    val full = cov2(pts)
    val (xr, yr) = boundsOf(pts, 0.35)
    val fullMean = DsP(full.mx, full.my)
    val fu = unit(full.angle)
    val reach = pts.maxOf { abs(project(it, fullMean, fu)) } * 1.15
    val batchLine = DsSeg(along(fullMean, fu, -reach), along(fullMean, fu, reach), SimColors.Grey, dashed = true, width = 1.2f)
    val sizes = listOf(4, 8, 12, 16, 18)
    val errors = sizes.map { m -> axisGap(cov2(streamOrder.take(m).map { pts[it] }).angle, full.angle) }
    val kept = DsFormula(listOf("kept: n, mean (2), Σxxᵀ (3) = {6 numbers}"))
    val legend = listOf(
        Triple(DBlue, SwatchStyle.Dot, "Seen"), Triple(NotYet, SwatchStyle.Dot, "Not yet"),
        Triple(DViolet, SwatchStyle.Line, "Running PC1"), Triple(SimColors.Grey, SwatchStyle.DashedLine, "Batch PC1"),
    )
    fun cells(current: Int) = DsCells(
        sizes.indices.map { b ->
            when {
                b < current -> DsCell("batch ${b + 1}", "${num(errors[b], 1)}°")
                b == current -> DsCell("batch ${b + 1}", "${num(errors[b], 1)}°", fill = StoryTone.Active, ink = StoryTone.Active)
                else -> DsCell("batch ${b + 1}", "—", muted = true)
            }
        },
    )
    fun plot(seen: Int): DsPlot {
        val seenSet = streamOrder.take(seen).toSet()
        val dots = pts.indices.map { DsDot(pts[it], if (it in seenSet) DBlue else NotYet) }
        if (seen == 0) return DsPlot(dots, xr, yr, listOf(batchLine))
        val cv = cov2(seenSet.map { pts[it] })
        val m = DsP(cv.mx, cv.my)
        val u = unit(cv.angle)
        val ts = seenSet.map { project(pts[it], m, u) }
        val run = DsSeg(along(m, u, ts.min() - 0.3), along(m, u, ts.max() + 0.3), DViolet, width = 2.5f)
        return DsPlot(dots + DsDot(m, Color.Transparent, 0f, ring = SimColors.Active, ringR = 7f, ringWidth = 2f), xr, yr, listOf(batchLine, run))
    }
    val frames = mutableListOf(
        DsFrame(
            "18 rows will arrive {4 at a time}.",
            "Batch PCA needs every row in memory. Incremental PCA keeps running sums instead.",
            listOf(plot(0), cells(-1), kept),
            legend.filter { it.third != "Running PC1" },
            action = "First Batch",
        ),
    )
    sizes.forEachIndexed { b, m ->
        val last = b == sizes.lastIndex
        frames += DsFrame(
            if (last) "After all 18 rows, the running PC1 is {${num(errors[b], 1)}°} off."
            else "After $m of 18 rows, the running PC1 is {${num(errors[b], 1)}°} off.",
            if (last) "The sums are exact, so the streamed answer equals batch PCA."
            else "Memory stays at 6 numbers however many rows stream past.",
            listOf(plot(m), cells(b), kept),
            legend,
            action = if (last) "Compare" else "Next Batch",
        )
    }
    frames += DsFrame(
        "The same axis as batch PCA, from {6 numbers} instead of 36.",
        "With d columns the kept state is 1 + d + d(d+1)/2 numbers, however long the stream runs.",
        listOf(plot(18), cells(sizes.size), kept),
        legend,
        action = "Start Over",
    )
    frames
}

// ── Kernel PCA ──

private val ringData: List<DsP> by lazy {
    val r = DsRng(4)
    List(50) {
        val a = r.u() * 2 * PI
        val rad = (if (it < 20) 1.0 else 3.0) + r.g() * 0.15
        DsP(rad * cos(a), rad * sin(a))
    }
}

private const val InnerCount = 20

/** Best accuracy of one threshold on a 1-D score, taking whichever side is the inner ring. */
private fun thresholdAccuracy(scores: List<Double>): Double {
    val n = scores.size
    val order = scores.indices.sortedBy { scores[it] }
    var best = 0
    for (cut in 0..n) {
        val low = order.take(cut).toSet()
        val c = (0 until n).count { (it in low) == (it < InnerCount) }
        best = max(best, max(c, n - c))
    }
    return best.toDouble() / n
}

private fun kernelPc1(pts: List<DsP>, gamma: Double): List<Double> {
    val n = pts.size
    val k = Array(n) { i -> DoubleArray(n) { j -> exp(-gamma * (sq(pts[i].x - pts[j].x) + sq(pts[i].y - pts[j].y))) } }
    val rowMean = DoubleArray(n) { k[it].sum() / n }
    val grand = rowMean.sum() / n
    val centred = Array(n) { i -> DoubleArray(n) { j -> k[i][j] - rowMean[i] - rowMean[j] + grand } }
    val e = jacobiEigen(centred)
    return List(n) { e.vectors[0][it] * sqrt(max(e.values[0], 1e-12)) }
}

private fun gammaText(g: Double) = num(g, if (g < 0.1) 2 else if (g < 1) 1 else 0)

private fun kernelLab(): DsLab {
    val gammas = listOf(0.05, 0.1, 0.2, 0.5, 1.0, 2.0, 5.0, 10.0)
    val pts = ringData
    val cv = cov2(pts)
    val mean = DsP(cv.mx, cv.my)
    val u = unit(cv.angle)
    val linear = pts.map { project(it, mean, u) }
    val linAcc = thresholdAccuracy(linear)
    val (xr, yr) = boundsOf(pts, 0.3)
    val colors = pts.indices.map { if (it < InnerCount) DBlue else DOrange }
    val kernels = HashMap<Int, List<Double>>()
    fun unitScale(v: List<Double>): List<Double> {
        val top = max(v.maxOf { abs(it) }, 1e-12)
        return v.map { it / top }
    }
    return DsLab(DsControl.Button, param = DsParam("Kernel width", "γ", gammas, 3, ::gammaText)) { s ->
        val g = gammas[s.param]
        val kp = kernels.getOrPut(s.param) { kernelPc1(pts, g) }
        val acc = thresholdAccuracy(kp)
        val good = acc >= 0.95
        val gText = gammaText(g)
        val line = DsSeg(along(mean, u, -3.4), along(mean, u, 3.4), DViolet, dashed = true, width = 1.5f)
        listOf(
            DsFrame(
                "Linear PC1 separates {w:${pct(linAcc, 0)}}; kernel PC1 separates {${if (good) "m" else "w"}:${pct(acc, 0)}}.",
                when {
                    good -> "No straight axis splits concentric rings. The kernel measures closeness instead, so radius becomes the axis."
                    g < 0.5 -> "At γ = $gText the kernel is so wide that every point looks alike, and kernel PC1 is nearly linear again."
                    else -> "At γ = $gText the kernel only sees each point's nearest neighbours, so PC1 picks out local patches, not rings."
                },
                listOf(
                    DsPlot(pts.indices.map { DsDot(pts[it], colors[it]) }, xr, yr, listOf(line), aspect = 1.9f),
                    DsStrips(listOf(DsStrip("linear PC1", unitScale(linear), colors), DsStrip("kernel PC1 (γ = $gText)", unitScale(kp), colors))),
                    DsFormula(listOf("k(x, x′) = exp(−γ‖x − x′‖²)")),
                ),
                listOf(Triple(DBlue, SwatchStyle.Dot, "Inner ring"), Triple(DOrange, SwatchStyle.Dot, "Outer ring"), Triple(DViolet, SwatchStyle.DashedLine, "Linear PC1")),
                listOf(
                    LabChip("linear", pct(linAcc, 0), tint = StoryTone.Warn),
                    if (good) LabChip("kernel", pct(acc, 0), good = true) else LabChip("kernel", pct(acc, 0), tint = StoryTone.Warn),
                ),
            ),
        )
    }
}

// ── ICA ──

private val icaSources: List<DsP> by lazy {
    val r = DsRng(26)
    val s3 = sqrt(3.0)
    List(150) {
        val a = (r.u() * 2 - 1) * s3
        val b = (r.u() * 2 - 1) * s3
        DsP(a, b)
    }
}

private val icaMix = arrayOf(doubleArrayOf(1.0, 0.6), doubleArrayOf(0.4, 1.0))

private fun kurtosisOf(v: List<Double>): Double {
    val m = v.average()
    val m2 = v.sumOf { sq(it - m) } / v.size
    val m4 = v.sumOf { sq(sq(it - m)) } / v.size
    return m4 / (m2 * m2) - 3
}

private fun rotate(z: List<DsP>, deg: Double): List<DsP> {
    val t = deg * PI / 180
    val c = cos(t)
    val s = sin(t)
    return z.map { DsP(c * it.x + s * it.y, -s * it.x + c * it.y) }
}

private fun nonGaussianity(z: List<DsP>, deg: Double): Double {
    val r = rotate(z, deg)
    return abs(kurtosisOf(r.map { it.x })) + abs(kurtosisOf(r.map { it.y }))
}

private fun icaLab(): DsLab = DsLab(DsControl.Track) {
    val src = icaSources
    val mixed = src.map { DsP(icaMix[0][0] * it.x + icaMix[0][1] * it.y, icaMix[1][0] * it.x + icaMix[1][1] * it.y) }
    // Whiten: rotate onto the covariance's eigenvectors, then scale each axis to unit variance.
    val n = mixed.size
    val mx = mixed.sumOf { it.x } / n
    val my = mixed.sumOf { it.y } / n
    val a = mixed.sumOf { sq(it.x - mx) } / n
    val b = mixed.sumOf { (it.x - mx) * (it.y - my) } / n
    val c = mixed.sumOf { sq(it.y - my) } / n
    val tr = (a + c) / 2
    val dd = sqrt(sq((a - c) / 2) + b * b)
    val th = 0.5 * atan2(2 * b, a - c)
    val eu = DsP(cos(th), sin(th))
    val ev = DsP(-sin(th), cos(th))
    val white = mixed.map {
        DsP(((it.x - mx) * eu.x + (it.y - my) * eu.y) / sqrt(tr + dd), ((it.x - mx) * ev.x + (it.y - my) * ev.y) / sqrt(tr - dd))
    }
    val curve = (0..90).map { nonGaussianity(white, it.toDouble()) }
    val best = (0 until 90).maxBy { curve[it] }
    val out = rotate(white, best.toDouble())
    val s1 = src.map { it.x }
    val s2 = src.map { it.y }
    val o1 = out.map { it.x }
    val o2 = out.map { it.y }
    val straight = abs(corr(o1, s1)) + abs(corr(o2, s2))
    val swapped = abs(corr(o1, s2)) + abs(corr(o2, s1))
    val cs = if (straight >= swapped) listOf(abs(corr(o1, s1)), abs(corr(o2, s2))) else listOf(abs(corr(o1, s2)), abs(corr(o2, s1)))
    val corrChip = LabChip("|corr| with sources", "${num(cs[0], 3)}, ${num(cs[1], 3)}", good = true)
    fun square(p: List<DsP>, pad: Double): Pair<Pair<Double, Double>, Pair<Double, Double>> {
        val m = p.maxOf { max(abs(it.x), abs(it.y)) } + pad
        return (-m to m) to (-m to m)
    }
    val t = best * PI / 180
    val reach = 2.6
    val dirs = listOf(
        DsSeg(DsP(-cos(t) * reach, -sin(t) * reach), DsP(cos(t) * reach, sin(t) * reach), SimColors.Active, width = 2f),
        DsSeg(DsP(sin(t) * reach, -cos(t) * reach), DsP(-sin(t) * reach, cos(t) * reach), SimColors.Active, width = 2f),
    )
    val axes = listOf(
        DsSeg(DsP(-reach, 0.0), DsP(reach, 0.0), SimColors.Active, width = 2f),
        DsSeg(DsP(0.0, -reach), DsP(0.0, reach), SimColors.Active, width = 2f),
    )
    val (wx, wy) = square(white, 0.2)
    val (sx, sy) = square(src, 0.2)
    val mixedC = mixed.map { DsP(it.x - mx, it.y - my) }
    val (mxr, myr) = square(mixedC, 0.2)
    val curveBlock = DsCurve("non-Gaussianity vs rotation angle", curve, best, "$best°")
    listOf(
        DsFrame(
            "Two hidden sources, each {uniform} and independent.",
            "ICA's job is to recover them from mixtures alone, without knowing how they were mixed.",
            listOf(DsPlot(src.map { DsDot(it, DGreen, 2.8f) }, sx, sy, uniform = false), DsFormula(listOf("s₁, s₂ ~ Uniform(−√3, √3)"))),
            listOf(Triple(DGreen, SwatchStyle.Dot, "Hidden sources")),
            action = "Mix",
        ),
        DsFrame(
            "The sensors only see {mixtures}: a slanted cloud.",
            "Each reading blends both sources, so neither one is a source on its own.",
            listOf(DsPlot(mixedC.map { DsDot(it, DPink, 2.8f) }, mxr, myr, uniform = false), DsFormula(listOf("x = A s,  A = [1.0, 0.6; 0.4, 1.0]"))),
            listOf(Triple(DPink, SwatchStyle.Dot, "Mixture")),
            action = "Whiten",
        ),
        DsFrame(
            "Whitening removes all correlation: {cov = I}.",
            "This is where PCA stops. Every rotation of it is still uncorrelated, so correlation cannot pick the angle.",
            listOf(DsPlot(white.map { DsDot(it, DPink, 2.8f) }, wx, wy, uniform = false), DsFormula(listOf("z = Λ^−½ Eᵀ (x − mean),  cov(z) = I"))),
            listOf(Triple(DPink, SwatchStyle.Dot, "Whitened mixture")),
            action = "Search Angles",
        ),
        DsFrame(
            "Whitened, only a rotation is left. ICA picks {$best°}.",
            "That angle makes the outputs least Gaussian, and they match the hidden sources.",
            listOf(DsPlot(white.map { DsDot(it, DPink, 2.8f) }, wx, wy, dirs, uniform = false), curveBlock),
            listOf(Triple(DPink, SwatchStyle.Dot, "Whitened mixture"), Triple(SimColors.Active, SwatchStyle.Line, "ICA directions")),
            listOf(corrChip),
            action = "Rotate",
        ),
        DsFrame(
            "Rotated by $best°, the outputs {are the sources}.",
            "Only order and sign stay unknown. Mixing makes signals more Gaussian; ICA undoes it.",
            listOf(DsPlot(out.map { DsDot(it, DGreen, 2.8f) }, wx, wy, axes, uniform = false), curveBlock),
            listOf(Triple(DGreen, SwatchStyle.Dot, "Recovered sources"), Triple(SimColors.Active, SwatchStyle.Line, "ICA directions")),
            listOf(corrChip),
            action = "Start Over",
        ),
    )
}

// ── Factor analysis ──

private val faLoadingsTrue = listOf(0.85, 0.40, 0.85)

private val faCorrelation: Array<DoubleArray> by lazy {
    val r = DsRng(10)
    val rows = List(300) {
        val f = r.g()
        DoubleArray(3) { k -> faLoadingsTrue[k] * f + r.g() * sqrt(1 - sq(faLoadingsTrue[k])) }
    }
    val n = rows.size
    val m = DoubleArray(3) { k -> rows.sumOf { it[k] } / n }
    val sd = DoubleArray(3) { k -> sqrt(rows.sumOf { sq(it[k] - m[k]) } / n) }
    Array(3) { i -> DoubleArray(3) { j -> rows.sumOf { (it[i] - m[i]) * (it[j] - m[j]) } / n / (sd[i] * sd[j]) } }
}

private fun faLab(): DsLab = DsLab(DsControl.Track) {
    val r = faCorrelation
    val r12 = r[0][1]
    val r13 = r[0][2]
    val r23 = r[1][2]
    val fa = listOf(sqrt(r12 * r13 / r23), sqrt(r12 * r23 / r13), sqrt(r13 * r23 / r12))
    val e = jacobiEigen(r)
    val pca = List(3) { abs(e.vectors[0][it]) * sqrt(e.values[0]) }
    val names = listOf("x1", "x2", "x3")
    val w = listOf(1.3f, 1f, 1f, 1f)
    val model = "xᵢ = λᵢ f + εᵢ, Var(εᵢ) = ψᵢ"
    val legend = listOf(Triple(DViolet, SwatchStyle.Fill, "Shared (λ²)"), Triple(SimColors.Grey.copy(alpha = 0.5f), SwatchStyle.Fill, "Unique (ψ)"))
    val loadTable = DsTable(
        listOf("") + names,
        listOf(DsRow(listOf("FA λ") + fa.map { num(it) }), DsRow(listOf("PCA") + pca.map { num(it) }, muted = true)),
        w,
    )
    fun reproduced(l: List<Double>) = listOf(l[0] * l[1], l[0] * l[2], l[1] * l[2])
    val observed = listOf(r12, r13, r23)
    val pcaMiss = reproduced(pca).indices.maxOf { abs(reproduced(pca)[it] - observed[it]) }
    listOf(
        DsFrame(
            "x1 and x3 correlate {${num(r13)}}; x2 barely joins in.",
            "Three measured variables, 300 samples. One hidden factor f could explain every correlation at once.",
            listOf(
                DsTable(
                    listOf("r") + names,
                    names.indices.map { i -> DsRow(listOf(names[i]) + (0 until 3).map { j -> num(r[i][j]) }, muted = i == 1) },
                    w,
                ),
            ),
            action = "Model",
        ),
        DsFrame(
            "Each variance splits into {shared λ²} and unique ψ.",
            "For standardised data λ² + ψ = 1. PCA has no ψ: it treats all variance as shared.",
            listOf(DsLoadings(names.map { DsLoad(it, null) }), DsFormula(listOf(model, "rᵢⱼ = λᵢ λⱼ"))),
            legend,
            action = "Solve λ₁",
        ),
        DsFrame(
            "x1 loads {${num(fa[0])}} on the factor.",
            "Three correlations, three loadings: one factor is solved exactly from ratios of r.",
            listOf(
                DsLoadings(listOf(DsLoad("x1", fa[0], true), DsLoad("x2", null), DsLoad("x3", null))),
                DsFormula(listOf(model, "λ₁ = √(r₁₂ r₁₃ / r₂₃) = {${num(fa[0])}}")),
            ),
            legend,
            action = "Solve λ₂",
        ),
        DsFrame(
            "x2 is mostly its own noise: {ψ = ${num(1 - sq(fa[1]))}}.",
            "PCA has no ψ, so it gives x2 a loading of ${num(pca[1])}, counting noise as signal.",
            listOf(
                DsLoadings(listOf(DsLoad("x1", fa[0]), DsLoad("x2", fa[1], true), DsLoad("x3", fa[2]))),
                DsFormula(listOf(model, "λ₂ = √(r₁₂ r₂₃ / r₁₃) = {${num(fa[1])}}")),
                loadTable,
            ),
            legend,
            action = "Fit Loadings",
        ),
        DsFrame(
            "FA's loadings rebuild every correlation {exactly}.",
            "PCA's loadings miss by up to ${num(pcaMiss)}, because they also try to explain each variable's own noise.",
            listOf(
                DsLoadings(names.indices.map { DsLoad(names[it], fa[it]) }),
                DsTable(
                    listOf("", "r₁₂", "r₁₃", "r₂₃"),
                    listOf(
                        DsRow(listOf("observed") + observed.map { num(it) }),
                        DsRow(listOf("FA λλ") + reproduced(fa).map { num(it) }, ink = StoryTone.Done),
                        DsRow(listOf("PCA") + reproduced(pca).map { num(it) }, muted = true),
                    ),
                    w,
                ),
            ),
            legend,
            action = "Compare",
        ),
        DsFrame(
            "FA models the {shared} variance; PCA the total.",
            "The data came from loadings ${faLoadingsTrue.joinToString(", ") { num(it) }}. Use FA when variables are noisy measures of a hidden trait.",
            listOf(
                DsLoadings(names.indices.map { DsLoad(names[it], fa[it]) }),
                DsTable(
                    listOf("") + names,
                    listOf(
                        DsRow(listOf("true λ") + faLoadingsTrue.map { num(it) }, ink = StoryTone.Done),
                        DsRow(listOf("FA λ") + fa.map { num(it) }),
                        DsRow(listOf("PCA") + pca.map { num(it) }, muted = true),
                    ),
                    w,
                ),
            ),
            legend,
            action = "Start Over",
        ),
    )
}

// ── t-SNE and UMAP share 45 points in 5-D: two tight clusters and one loose one ──

private val clusters5: List<DoubleArray> by lazy {
    val r = DsRng(5)
    fun blob(n: Int, c: List<Double>, sd: Double) = List(n) { DoubleArray(5) { d -> c[d] + r.g() * sd } }
    blob(15, listOf(0.0, 0.0, 0.0, 0.0, 0.0), 0.25) + blob(15, listOf(3.0, 1.0, 0.0, 0.0, 0.0), 0.25) + blob(15, listOf(1.0, 7.5, 0.0, 0.0, 0.0), 1.45)
}

private val groupA = 0 until 15
private val groupC = 30 until 45

/** Root-mean-square distance to the group's centroid. */
private fun spread(pts: List<DoubleArray>, idx: IntRange): Double {
    val d = pts[0].size
    val m = DoubleArray(d) { k -> idx.sumOf { pts[it][k] } / idx.count() }
    return sqrt(idx.sumOf { sqDist(pts[it], m) } / idx.count())
}

private fun clusterLegend() = listOf(Triple(DBlue, SwatchStyle.Dot, "Tight A"), Triple(DOrange, SwatchStyle.Dot, "Tight B"), Triple(DGreen, SwatchStyle.Dot, "Loose C"))

private class TsneRun(val y: List<DoubleArray>, val kl: Double)

private fun runTsne(x: List<DoubleArray>, perplexity: Double, seed: Long, iterations: Int = 500): TsneRun {
    val n = x.size
    val d = Array(n) { i -> DoubleArray(n) { j -> sqDist(x[i], x[j]) } }
    val cond = Array(n) { DoubleArray(n) }
    val target = ln(perplexity)
    for (i in 0 until n) {
        var lo = 0.0
        var hi = Double.POSITIVE_INFINITY
        var beta = 1.0
        var row = DoubleArray(n)
        var s = 1.0
        for (step in 0 until 60) {
            row = DoubleArray(n) { j -> if (j == i) 0.0 else exp(-d[i][j] * beta) }
            s = max(row.sum(), 1e-300)
            val h = ln(s) + beta * (0 until n).sumOf { d[i][it] * row[it] } / s
            if (abs(h - target) < 1e-6) break
            if (h > target) {
                lo = beta
                beta = if (hi == Double.POSITIVE_INFINITY) beta * 2 else (beta + hi) / 2
            } else {
                hi = beta
                beta = (beta + lo) / 2
            }
        }
        for (j in 0 until n) cond[i][j] = row[j] / s
    }
    val p = Array(n) { i -> DoubleArray(n) { j -> (cond[i][j] + cond[j][i]) / (2 * n) } }
    val r = DsRng(seed)
    val y = List(n) { doubleArrayOf(r.g() * 1e-2, r.g() * 1e-2) }
    val v = List(n) { DoubleArray(2) }
    val q = Array(n) { DoubleArray(n) }
    for (it in 0 until iterations) {
        val ex = if (it < 100) 4.0 else 1.0
        val mom = if (it < 100) 0.5 else 0.8
        var z = 0.0
        for (i in 0 until n) for (j in 0 until n) {
            if (i != j) {
                q[i][j] = 1 / (1 + sq(y[i][0] - y[j][0]) + sq(y[i][1] - y[j][1]))
                z += q[i][j]
            }
        }
        for (i in 0 until n) {
            var gx = 0.0
            var gy = 0.0
            for (j in 0 until n) {
                if (i == j) continue
                val m = (ex * p[i][j] - q[i][j] / z) * q[i][j]
                gx += 4 * m * (y[i][0] - y[j][0])
                gy += 4 * m * (y[i][1] - y[j][1])
            }
            v[i][0] = mom * v[i][0] - 50.0 * gx
            v[i][1] = mom * v[i][1] - 50.0 * gy
        }
        for (i in 0 until n) {
            y[i][0] += v[i][0]
            y[i][1] += v[i][1]
        }
    }
    var z = 0.0
    for (i in 0 until n) for (j in 0 until n) if (i != j) {
        q[i][j] = 1 / (1 + sq(y[i][0] - y[j][0]) + sq(y[i][1] - y[j][1]))
        z += q[i][j]
    }
    var kl = 0.0
    for (i in 0 until n) for (j in 0 until n) if (i != j && p[i][j] > 1e-12) kl += p[i][j] * ln(p[i][j] / (q[i][j] / z))
    return TsneRun(y, kl)
}

private fun embeddedDots(y: List<DoubleArray>) = y.indices.map { DsDot(DsP(y[it][0], y[it][1]), ClusterColors[it / 15]) }

private fun embeddingBounds(y: List<DoubleArray>): Pair<Pair<Double, Double>, Pair<Double, Double>> {
    val pts = y.map { DsP(it[0], it[1]) }
    val span = max(pts.maxOf { it.x } - pts.minOf { it.x }, pts.maxOf { it.y } - pts.minOf { it.y })
    return boundsOf(pts, span * 0.06)
}

private fun spreadTable(method: String, mapA: Double, mapC: Double): DsTable {
    val a = spread(clusters5, groupA)
    val c = spread(clusters5, groupC)
    return DsTable(
        listOf("spread", "cluster A", "loose C", "ratio"),
        listOf(
            DsRow(listOf("original 5-D", num(a), num(c), num(c / a, 1) + "×")),
            DsRow(listOf(method, num(mapA), num(mapC), num(mapC / mapA, 1) + "×"), ink = StoryTone.Active, fill = StoryTone.Active),
        ),
        listOf(1.6f, 1.1f, 1f, 0.8f),
    )
}

private fun tsneLab(): DsLab {
    val perps = listOf(2.0, 5.0, 10.0, 20.0, 30.0)
    val runs = HashMap<Pair<Int, Int>, TsneRun>()
    return DsLab(
        DsControl.Button,
        param = DsParam("Perplexity", "perp", perps, 2) { "${it.roundToInt()}" },
        button = { "Run Again" },
        onButton = { it.copy(flag = (it.flag + 1) % 5) },
    ) { s ->
        val perp = perps[s.param]
        val run = runs.getOrPut(s.param to s.flag) { runTsne(clusters5, perp, s.flag + 1L) }
        val ya = spread(run.y, groupA)
        val yc = spread(run.y, groupC)
        val oa = spread(clusters5, groupA)
        val oc = spread(clusters5, groupC)
        val (xr, yr) = embeddingBounds(run.y)
        val p = perp.roundToInt()
        listOf(
            DsFrame(
                "C is ${num(oc / oa, 1)}× wider than A in 5-D, but only {${num(yc / ya, 1)}×} on the map.",
                when {
                    p <= 2 -> "At perplexity 2 each point listens to about 2 neighbours, so clusters shatter into strands."
                    p >= 30 -> "Perplexity 30 is twice a cluster's size, so neighbourhoods spill across clusters and runs disagree."
                    else -> "Perplexity sets a neighbour count, not a distance, so cluster size is not preserved."
                },
                listOf(DsPlot(embeddedDots(run.y), xr, yr, aspect = 1.9f), spreadTable("t-SNE map", ya, yc)),
                clusterLegend(),
                listOf(LabChip("iteration", "500"), LabChip("KL", num(run.kl), tint = StoryTone.Answer)),
            ),
        )
    }
}

// ── UMAP ──

private class UmapGraph(val neighbours: List<List<Int>>, val rho: DoubleArray, val sigma: DoubleArray, val directed: Array<DoubleArray>, val sym: Array<DoubleArray>)

private fun dist5(a: DoubleArray, b: DoubleArray) = sqrt(sqDist(a, b))

private fun fuzzyGraph(x: List<DoubleArray>, k: Int): UmapGraph {
    val n = x.size
    val nb = List(n) { i -> (0 until n).filter { it != i }.sortedWith(compareBy<Int>({ dist5(x[i], x[it]) }, { it })).take(k) }
    val target = ln(k.toDouble()) / ln(2.0)
    val rho = DoubleArray(n)
    val sigma = DoubleArray(n)
    val w = Array(n) { DoubleArray(n) }
    for (i in 0 until n) {
        val ds = nb[i].map { dist5(x[i], x[it]) }
        val r = ds[0]
        var lo = 0.0
        var hi = Double.POSITIVE_INFINITY
        var s = 1.0
        for (step in 0 until 64) {
            val tot = ds.sumOf { exp(-max(it - r, 0.0) / s) }
            if (abs(tot - target) < 1e-6) break
            if (tot > target) {
                hi = s
                s = (lo + hi) / 2
            } else {
                lo = s
                s = if (hi == Double.POSITIVE_INFINITY) s * 2 else (lo + hi) / 2
            }
        }
        rho[i] = r
        sigma[i] = s
        nb[i].forEachIndexed { a, j -> w[i][j] = exp(-max(ds[a] - r, 0.0) / s) }
    }
    val sym = Array(n) { i -> DoubleArray(n) { j -> w[i][j] + w[j][i] - w[i][j] * w[j][i] } }
    return UmapGraph(nb, rho, sigma, w, sym)
}

/** The first two principal components of the 5-D points. */
private val clusterPcaView: List<DsP> by lazy {
    val x = clusters5
    val n = x.size
    val m = DoubleArray(5) { k -> x.sumOf { it[k] } / n }
    val c = Array(5) { i -> DoubleArray(5) { j -> x.sumOf { (it[i] - m[i]) * (it[j] - m[j]) } / (n - 1) } }
    val e = jacobiEigen(c)
    x.map { p -> DsP((0 until 5).sumOf { (p[it] - m[it]) * e.vectors[0][it] }, (0 until 5).sumOf { (p[it] - m[it]) * e.vectors[1][it] }) }
}

private const val UmapA = 1.577
private const val UmapB = 0.895

/** Full-gradient UMAP layout from the PCA view; returns the layout after each epoch in [snaps]. */
private fun layoutUmap(sym: Array<DoubleArray>, snaps: List<Int>): Map<Int, List<DoubleArray>> {
    val n = sym.size
    val view = clusterPcaView
    val m = view.maxOf { max(abs(it.x), abs(it.y)) }
    val y = view.map { doubleArrayOf(it.x / m * 10, it.y / m * 10) }
    val epochs = snaps.max()
    val out = HashMap<Int, List<DoubleArray>>()
    if (0 in snaps) out[0] = y.map { it.copyOf() }
    fun clip(v: Double) = max(-4.0, min(4.0, v))
    for (e in 0 until epochs) {
        val alpha = 1.0 - e.toDouble() / epochs
        val g = Array(n) { DoubleArray(2) }
        for (i in 0 until n) for (j in 0 until n) {
            if (i == j) continue
            val dx = y[i][0] - y[j][0]
            val dy = y[i][1] - y[j][1]
            val d2 = dx * dx + dy * dy
            val w = sym[i][j]
            if (w > 0 && d2 > 0) {
                val c = -2 * UmapA * UmapB * d2.pow(UmapB - 1) / (1 + UmapA * d2.pow(UmapB)) * w
                g[i][0] += clip(c * dx)
                g[i][1] += clip(c * dy)
            }
            val c = 2 * UmapB / ((0.001 + d2) * (1 + UmapA * d2.pow(UmapB))) * (1 - w) * 0.1
            g[i][0] += clip(c * dx)
            g[i][1] += clip(c * dy)
        }
        for (i in 0 until n) {
            y[i][0] += alpha * g[i][0] * 0.1
            y[i][1] += alpha * g[i][1] * 0.1
        }
        if (e + 1 in snaps) out[e + 1] = y.map { it.copyOf() }
    }
    return out
}

private fun umapLab(): DsLab {
    val ks = listOf(4.0, 5.0, 6.0, 8.0, 10.0, 12.0, 15.0)
    return DsLab(DsControl.TrackParam, param = DsParam("Neighbours", "k", ks, 3) { "${it.roundToInt()}" }) { s ->
        val k = ks[s.param].roundToInt()
        val x = clusters5
        val g = fuzzyGraph(x, k)
        val view = clusterPcaView
        val (vx, vy) = boundsOf(view, 0.6)
        val viewDots = view.indices.map { DsDot(view[it], ClusterColors[it / 15]) }
        val tight = 0
        val loose = 30
        val log2k = ln(k.toDouble()) / ln(2.0)
        val snaps = listOf(0, 10, 50, 200)
        val layouts = layoutUmap(g.sym, snaps)
        val edges = (0 until x.size).flatMap { i -> (i + 1 until x.size).filter { j -> g.sym[i][j] > 0 }.map { j -> i to j } }
        fun rowOf(name: String, i: Int, tone: StoryTone) =
            DsRow(listOf(name, num(g.rho[i]), num(g.sigma[i], 3), num(g.neighbours[i].sumOf { g.directed[i][it] })), ink = tone)
        val sigmaTable = DsTable(listOf("point", "ρ", "σ", "Σw"), listOf(rowOf("tight A", tight, StoryTone.Path), rowOf("loose C", loose, StoryTone.Done)), listOf(1.4f, 1f, 1f, 1f))
        val legendLinks = clusterLegend() + Triple(LinkGrey, SwatchStyle.Line, "Neighbour link")
        fun layoutFrame(epoch: Int, headline: String, body: String, action: String = ""): DsFrame {
            val y = layouts.getValue(epoch)
            val (xr, yr) = embeddingBounds(y)
            val ratio = spread(y, groupC) / spread(y, groupA)
            return DsFrame(
                headline, body,
                listOf(DsPlot(embeddedDots(y), xr, yr, aspect = 1.9f), DsFormula(listOf("attract ∝ 1 / (1 + a d²ᵇ),  a = 1.58, b = 0.90"))),
                clusterLegend(),
                listOf(LabChip("epoch", "$epoch"), LabChip("C / A spread", num(ratio, 1) + "×", tint = StoryTone.Answer)),
                action,
            )
        }
        val final = layouts.getValue(200)
        listOf(
            DsFrame(
                "Each point links to its {k = $k} nearest neighbours in 5-D.",
                "Shown on a PCA view. The loose cluster's links are long, the tight clusters' short.",
                listOf(DsPlot(viewDots, vx, vy, (0 until x.size).flatMap { i -> g.neighbours[i].map { j -> DsSeg(view[i], view[j], LinkGrey.copy(alpha = 0.55f), width = 1f) } }, aspect = 1.9f)),
                legendLinks,
                listOf(LabChip("links", "${x.size * k}")),
            ),
            DsFrame(
                "A tight and a loose point both reach {Σw = ${num(log2k)}}.",
                "ρ is subtracted first, so every point's nearest link weighs 1, however sparse its area.",
                listOf(
                    DsPlot(
                        viewDots + listOf(tight, loose).map { DsDot(view[it], Color.Transparent, 0f, ring = SimColors.Active, ringR = 7f, ringWidth = 2f) },
                        vx, vy,
                        listOf(tight, loose).flatMap { i -> g.neighbours[i].map { j -> DsSeg(view[i], view[j], SimColors.Grey.copy(alpha = 0.8f), width = 1f) } },
                        aspect = 1.9f,
                    ),
                    sigmaTable,
                    DsFormula(listOf("w = exp(−(d − ρ) / σ),  Σw = log₂ k = {${num(log2k, if (log2k % 1.0 == 0.0) 0 else 2)}}")),
                ),
                clusterLegend() + Triple(SimColors.Active, SwatchStyle.Ring, "Compared"),
            ),
            DsFrame(
                "Merged both ways, the graph keeps {${edges.size}} weighted edges.",
                "An edge counts if either end picked the other. This fuzzy graph is UMAP's picture of the 5-D data.",
                listOf(
                    DsPlot(viewDots, vx, vy, edges.map { (i, j) -> DsSeg(view[i], view[j], LinkGrey.copy(alpha = (0.2 + 0.7 * g.sym[i][j]).toFloat()), width = 1f) }, aspect = 1.9f),
                    DsFormula(listOf("w = wᵢⱼ + wⱼᵢ − wᵢⱼ · wⱼᵢ")),
                ),
                legendLinks,
                listOf(LabChip("edges", "${edges.size}")),
            ),
            layoutFrame(0, "The layout starts from this view and runs {200 epochs}.", "Every edge pulls its two ends together; every other pair pushes apart."),
            layoutFrame(10, "After {10 epochs} each cluster pulls in around its own links.", "Attraction only acts along edges, and the three clusters share almost none."),
            layoutFrame(50, "After {50 epochs} the loose cluster has closed up.", "Its points were far apart in 5-D, but their graph edges weigh as much as anyone's."),
            layoutFrame(200, "After {200 epochs} the layout has settled.", "The step size decays to zero, so the last epochs only polish."),
            DsFrame(
                "C is ${num(spread(x, groupC) / spread(x, groupA), 1)}× wider than A in 5-D, but {${num(spread(final, groupC) / spread(final, groupA), 1)}×} in UMAP.",
                "Like t-SNE, UMAP keeps who neighbours whom, not how spread out each group is.",
                listOf(DsPlot(embeddedDots(final), embeddingBounds(final).first, embeddingBounds(final).second, aspect = 1.9f), spreadTable("UMAP map", spread(final, groupA), spread(final, groupC))),
                clusterLegend(),
            ),
        )
    }
}

// ── LLE ──

private class Spiral(val pts: List<DsP>, val theta: List<Double>)

private val spiral: Spiral by lazy {
    val r = DsRng(3)
    val n = 80
    val pts = mutableListOf<DsP>()
    val th = mutableListOf<Double>()
    for (i in 0 until n) {
        val t = 1.2 + 2 * PI * 1.5 * (i / (n - 1.0))
        val rad = 0.35 * t
        val x = rad * cos(t) + r.g() * 0.01
        val y = 0.62 * rad * sin(t) + r.g() * 0.01
        // Mirrored so the arms cross at the lower right.
        pts += DsP(-x, y)
        th += t
    }
    Spiral(pts, th)
}

private fun knn2(pts: List<DsP>, k: Int) =
    List(pts.size) { i -> pts.indices.filter { it != i }.sortedWith(compareBy<Int>({ sq(pts[i].x - pts[it].x) + sq(pts[i].y - pts[it].y) }, { it })).take(k) }

/** Neighbour links (as unordered pairs) that join two different arms of the spiral. */
private fun armJumps(k: Int): Set<Pair<Int, Int>> {
    val (pts, th) = spiral.let { it.pts to it.theta }
    val nb = knn2(pts, k)
    val out = HashSet<Pair<Int, Int>>()
    for (i in pts.indices) for (j in nb[i]) if (abs(th[i] - th[j]) > PI) out += min(i, j) to max(i, j)
    return out
}

private fun solveLinear(a: Array<DoubleArray>, b: DoubleArray): DoubleArray {
    val n = b.size
    val m = Array(n) { i -> a[i].copyOf(n + 1).also { it[n] = b[i] } }
    for (c in 0 until n) {
        val p = (c until n).maxBy { abs(m[it][c]) }
        val t = m[c]
        m[c] = m[p]
        m[p] = t
        for (r in 0 until n) {
            if (r == c) continue
            val f = m[r][c] / m[c][c]
            for (k in c..n) m[r][k] -= f * m[c][k]
        }
    }
    return DoubleArray(n) { m[it][n] / m[it][it] }
}

/** One-dimensional LLE coordinates of the spiral. */
private fun lleCoords(k: Int): List<Double> {
    val pts = spiral.pts
    val n = pts.size
    val nb = knn2(pts, k)
    val w = Array(n) { DoubleArray(n) }
    for (i in 0 until n) {
        val z = nb[i].map { DsP(pts[it].x - pts[i].x, pts[it].y - pts[i].y) }
        val c = Array(k) { a -> DoubleArray(k) { b -> z[a].x * z[b].x + z[a].y * z[b].y } }
        val tr = (0 until k).sumOf { c[it][it] }
        val reg = if (tr > 0) 1e-3 * tr else 1e-3
        for (a in 0 until k) c[a][a] += reg
        val sol = solveLinear(c, DoubleArray(k) { 1.0 })
        val s = sol.sum()
        nb[i].forEachIndexed { a, j -> w[i][j] = sol[a] / s }
    }
    val iw = Array(n) { i -> DoubleArray(n) { j -> (if (i == j) 1.0 else 0.0) - w[i][j] } }
    val m = Array(n) { i -> DoubleArray(n) { j -> (0 until n).sumOf { iw[it][i] * iw[it][j] } } }
    val e = jacobiEigen(m)
    return e.vectors[n - 2].toList()
}

private fun spearman(a: List<Double>): Double {
    val n = a.size
    val order = a.indices.sortedBy { a[it] }
    val rank = IntArray(n)
    order.forEachIndexed { r, i -> rank[i] = r }
    val m = (n - 1) / 2.0
    return abs((0 until n).sumOf { (rank[it] - m) * (it - m) } / (0 until n).sumOf { sq(it - m) })
}

private fun lleLab(): DsLab {
    val ks = listOf(4.0, 6.0, 8.0, 10.0, 12.0, 14.0)
    val jumps = ks.map { armJumps(it.roundToInt()) }
    val coords = HashMap<Int, List<Double>>()
    return DsLab(
        DsControl.Button,
        param = DsParam("Neighbours", "k", ks, 1) { "${it.roundToInt()}" },
        button = { if (it.flag == 0) "Unroll" else "Show Neighbours" },
        onButton = { it.copy(flag = 1 - it.flag) },
    ) { s ->
        val k = ks[s.param].roundToInt()
        val pts = spiral.pts
        val count = jumps[s.param].size
        val cells = DsCells(
            ks.indices.map { i ->
                val c = jumps[i].size
                DsCell(
                    "k ${ks[i].roundToInt()}", "$c",
                    fill = if (i == s.param) (if (c > 0) StoryTone.Warn else StoryTone.Done) else null,
                    ink = if (c == 0) StoryTone.Done else if (i == s.param) StoryTone.Warn else null,
                )
            },
        )
        val (xr, yr) = boundsOf(pts, 0.15)
        if (s.flag == 0) {
            val nb = knn2(pts, k)
            val jump = jumps[s.param]
            val links = HashSet<Pair<Int, Int>>()
            for (i in pts.indices) for (j in nb[i]) links += min(i, j) to max(i, j)
            val segs = links.filter { it !in jump }.map { (i, j) -> DsSeg(pts[i], pts[j], LinkGrey, width = 1.5f) } +
                jump.map { (i, j) -> DsSeg(pts[i], pts[j], SimColors.Red, width = 1.5f) }
            listOf(
                DsFrame(
                    if (count == 0) "At {k = $k}, every link stays on its own arm."
                    else "At {k = $k}, $count ${if (count == 1) "link jumps" else "links jump"} between arms.",
                    if (count == 0) "Each point is rebuilt from $k neighbours on the same stretch of curve, so LLE can unroll it."
                    else "LLE assumes each neighbourhood is flat. These links make two arms one patch, so the unrolled line folds.",
                    listOf(DsPlot(pts.map { DsDot(it, DBlue, 2.8f) }, xr, yr, segs, aspect = 1.6f), cells),
                    listOf(Triple(DBlue, SwatchStyle.Dot, "Spiral"), Triple(LinkGrey, SwatchStyle.Line, "Neighbour link"), Triple(SimColors.Red, SwatchStyle.Line, "Jumps an arm")),
                ),
            )
        } else {
            val y = coords.getOrPut(k) { lleCoords(k) }
            val order = spearman(y)
            val top = y.maxOf { abs(it) }
            val good = order > 0.99
            listOf(
                DsFrame(
                    if (good) "Unrolled, the spiral keeps its order: {m:${num(order)}}." else "Unrolled, the line {w:folds}: order kept only ${num(order)}.",
                    if (good) "Each point keeps the weights that rebuilt it from its neighbours, now in one dimension."
                    else "The cross-arm links tie distant stretches of the curve together, so they land side by side.",
                    listOf(
                        DsPlot(
                            y.indices.map { DsDot(DsP(it.toDouble(), y[it] / top), DBlue, 2.8f) }, -3.0 to pts.size + 2.0, -1.3 to 1.3, aspect = 2.4f, uniform = false,
                        ),
                        DsFormula(listOf("→ position along the spiral,  ↑ LLE coordinate")),
                        cells,
                    ),
                    listOf(Triple(DBlue, SwatchStyle.Dot, "Spiral point")),
                    listOf(if (good) LabChip("order kept", num(order), good = true) else LabChip("order kept", num(order), tint = StoryTone.Warn)),
                ),
            )
        }
    }
}

private fun dimLab(topicId: String): DsLab = when (topicId) {
    "svd" -> svdLab()
    "incremental_pca" -> incrementalLab()
    "kernel_pca" -> kernelLab()
    "ica" -> icaLab()
    "factor_analysis" -> faLab()
    "tsne" -> tsneLab()
    "umap" -> umapLab()
    "lle" -> lleLab()
    else -> pcaLab()
}

/** Builds every frame each control can reach (every tab, stepper value and press of the button). */
internal fun dimStoryFrameCount(topicId: String): Int {
    val lab = dimLab(topicId)
    var total = 0
    for (t in 0 until max(lab.tabs.size, 1)) for (p in 0 until (lab.param?.values?.size ?: 1)) {
        var st = DsState(t, p, 0)
        repeat(if (lab.button != null) 5 else 1) {
            val frames = lab.frames(st)
            require(frames.isNotEmpty()) { "$topicId built no frames at $st" }
            frames.forEach { f ->
                require(f.headline.isNotBlank() && f.body.isNotBlank()) { "$topicId has no narration at $st" }
                require(f.blocks.isNotEmpty()) { "$topicId drew nothing at $st" }
                if (lab.control == DsControl.Track || lab.control == DsControl.Steps) require(f.action.isNotBlank()) { "$topicId has a step without an action at $st" }
                f.blocks.filterIsInstance<DsPlot>().forEach { plot ->
                    require(plot.dots.all { it.p.x.isFinite() && it.p.y.isFinite() }) { "$topicId drew a non-finite dot at $st" }
                }
            }
            total += frames.size
            st = lab.onButton(st)
        }
    }
    return total
}

// ── Lab ──

@Composable
internal fun DimStorySection(topicId: String) {
    val lab = remember(topicId) { dimLab(topicId) }
    var state by remember(topicId) { mutableStateOf(lab.initial) }
    val frames = remember(topicId, state.tab, state.param, state.flag) { lab.frames(state) }
    val dock = LocalLabDock.current

    if (lab.control == DsControl.Track) {
        val playback = rememberPlaybackState(key = topicId, stepCount = frames.size, initialSpeedMs = 1000f)
        Column(modifier = Modifier.fillMaxWidth()) {
            DsBody(frames[playback.index.coerceIn(0, frames.lastIndex)])
            PlaybackTransport(playback, captions = frames.map { storyPlain(it.headline) }, action = { frames[it.coerceIn(0, frames.lastIndex)].action })
        }
        return
    }

    val stepper: @Composable () -> Unit = {
        lab.param?.let { p ->
            LabParamStepper(LabParam(p.name, p.name, p.symbol, p.format(p.values[state.param]), state.param > 0, state.param < p.values.lastIndex)) { d ->
                state = state.copy(param = (state.param + d).coerceIn(0, p.values.lastIndex), index = 0)
            }
        }
    }

    val playback = rememberPlaybackState(key = topicId, stepCount = frames.size, initialSpeedMs = 1000f)
    val index = if (lab.control == DsControl.TrackParam) playback.index.coerceIn(0, frames.lastIndex) else state.index.coerceIn(0, frames.lastIndex)
    val controls: @Composable () -> Unit = {
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            when (lab.control) {
                DsControl.TrackParam -> LabTransportBar(playback, frames.map { storyPlain(it.headline) }, footer = stepper)
                DsControl.Steps -> {
                    LabSegments(lab.tabs, state.tab) { state = state.copy(tab = it, index = 0) }
                    LabBackActionRow(
                        frames[index].action,
                        backEnabled = index > 0,
                        onBack = { state = state.copy(index = index - 1) },
                        onAction = { state = state.copy(index = if (index >= frames.lastIndex) 0 else index + 1) },
                    )
                }
                else -> {
                    stepper()
                    lab.button?.let { label -> LabButton(label(state), primary = true, modifier = Modifier.fillMaxWidth()) { state = lab.onButton(state) } }
                }
            }
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        DsBody(frames[index])
        if (dock == null) {
            HorizontalDivider(modifier = Modifier.padding(top = 16.dp, bottom = 14.dp), color = MaterialTheme.colorScheme.outline)
            controls()
        }
    }

    if (dock != null) {
        SideEffect {
            dock.controls = controls
            dock.navAction = LabNavAction(Icons.Filled.Refresh, "Reset") {
                state = lab.initial
                playback.reset()
            }
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
private fun DsBody(frame: DsFrame) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            frame.blocks.forEach { block ->
                when (block) {
                    is DsPlot -> DsPlotView(block)
                    is DsStrips -> DsStripsView(block)
                    is DsCurve -> DsCurveView(block)
                    is DsCells -> DsCellsView(block)
                    is DsTable -> DsTableView(block)
                    is DsFormula -> DsFormulaView(block.lines)
                    is DsLoadings -> DsLoadingsView(block)
                }
            }
            if (frame.legend.isNotEmpty()) StoryLegendRow(frame.legend, Modifier.padding(top = 2.dp))
        }
    }
    LabChips(frame.chips, Modifier.padding(top = 16.dp))
    LabStoryNarration(frame.headline, frame.body, Modifier.padding(top = 16.dp))
}

// ── Rendering ──

private fun Modifier.dsStage() = this.clip(RoundedCornerShape(12.dp)).background(Color.Black.copy(alpha = 0.16f))

@Composable
private fun DsFormulaView(lines: List<String>) {
    Column(
        modifier = Modifier.fillMaxWidth().background(SimColors.Tint, RoundedCornerShape(10.dp)).padding(vertical = 10.dp, horizontal = 12.dp),
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
private fun DsPlotView(plot: DsPlot) {
    Canvas(modifier = Modifier.fillMaxWidth().aspectRatio(plot.aspect).dsStage()) {
        val pad = 14.dp.toPx()
        val (xLo, xHi) = plot.xr
        val (yLo, yHi) = plot.yr
        val sx = ((size.width - 2 * pad) / (xHi - xLo)).toFloat()
        val sy = ((size.height - 2 * pad) / (yHi - yLo)).toFloat()
        val s = min(sx, sy)
        val kx = if (plot.uniform) s else sx
        val ky = if (plot.uniform) s else sy
        val ox = size.width / 2 - ((xLo + xHi) / 2).toFloat() * kx
        val oy = size.height / 2 + ((yLo + yHi) / 2).toFloat() * ky
        fun at(p: DsP) = Offset(ox + p.x.toFloat() * kx, oy - p.y.toFloat() * ky)
        val dash = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx()))
        plot.segs.forEach { sg ->
            drawLine(sg.color, at(sg.a), at(sg.b), sg.width.dp.toPx(), cap = if (sg.dashed) StrokeCap.Butt else StrokeCap.Round, pathEffect = if (sg.dashed) dash else null)
        }
        plot.dots.forEach { d ->
            val c = at(d.p)
            if (d.r > 0) drawCircle(d.color, d.r.dp.toPx(), c)
            d.ring?.let { drawCircle(it, (d.r + d.ringR).dp.toPx(), c, style = Stroke(d.ringWidth.dp.toPx())) }
        }
    }
}

@Composable
private fun DsStripsView(block: DsStrips) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(modifier = Modifier.fillMaxWidth().dsStage().padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        block.strips.forEach { strip ->
            Text(strip.label, fontSize = 12.sp, color = muted, maxLines = 1)
            Canvas(modifier = Modifier.fillMaxWidth().height(16.dp)) {
                drawRoundRect(SimColors.Tint, cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2))
                val r = 3.5.dp.toPx()
                strip.values.forEachIndexed { i, v ->
                    drawCircle(strip.colors[i], r, Offset(r + 2 + ((v + 1) / 2).toFloat() * (size.width - 2 * r - 4), size.height / 2))
                }
            }
        }
    }
}

@Composable
private fun DsCurveView(curve: DsCurve) {
    val measurer = rememberTextMeasurer()
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Box(modifier = Modifier.fillMaxWidth().height(84.dp).dsStage()) {
        Text(curve.caption, fontSize = 12.sp, color = muted, maxLines = 1, modifier = Modifier.padding(start = 12.dp, top = 6.dp))
        Canvas(modifier = Modifier.fillMaxSize().padding(start = 16.dp, end = 16.dp, top = 26.dp, bottom = 8.dp)) {
            val lo = curve.ys.min()
            val hi = curve.ys.max()
            // The top 16dp stay clear for the marker's label.
            val top = 16.dp.toPx()
            fun at(i: Int, v: Double) = Offset(i.toFloat() / (curve.ys.size - 1) * size.width, size.height - ((v - lo) / max(hi - lo, 1e-9)).toFloat() * (size.height - top))
            val path = Path()
            curve.ys.forEachIndexed { i, v -> if (i == 0) path.moveTo(at(i, v).x, at(i, v).y) else path.lineTo(at(i, v).x, at(i, v).y) }
            drawPath(path, DViolet, style = Stroke(2.dp.toPx()))
            val mx = at(curve.marker, 0.0).x
            drawLine(SimColors.Active, Offset(mx, 0f), Offset(mx, size.height), 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx())))
            drawText(measurer, curve.markerLabel, Offset(mx + 4.dp.toPx(), 0f), TextStyle(color = SimColors.Active, fontSize = 12.sp, fontFamily = IBMPlexMono))
        }
    }
}

@Composable
private fun DsCellsView(block: DsCells) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val onSurface = MaterialTheme.colorScheme.onSurface
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        block.cells.forEach { cell ->
            Column(
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp)
                    .background(cell.fill?.let { it.color().copy(alpha = 0.22f) } ?: SimColors.Tint, RoundedCornerShape(10.dp)),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(cell.label, fontSize = 12.sp, color = muted, maxLines = 1)
                Text(
                    cell.value,
                    fontFamily = IBMPlexMono,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    color = when {
                        cell.muted -> muted
                        cell.ink != null -> cell.ink.ink()
                        else -> onSurface
                    },
                )
            }
        }
    }
}

@Composable
private fun DsTableView(table: DsTable) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val onSurface = MaterialTheme.colorScheme.onSurface
    fun align(i: Int) = if (i == 0) TextAlign.Start else TextAlign.End
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
            table.header.forEachIndexed { i, h -> Text(h, fontSize = 13.sp, color = muted, textAlign = align(i), maxLines = 1, modifier = Modifier.weight(table.weights[i])) }
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
                            StoryTone.Active -> SimColors.Active.copy(alpha = 0.16f)
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
                        fontSize = 15.sp,
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
private fun DsLoadingsView(block: DsLoadings) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val onSurface = MaterialTheme.colorScheme.onSurface
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        block.rows.forEach { row ->
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Text(
                    row.name,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (row.highlight) SimColors.Active else onSurface,
                    modifier = Modifier.width(36.dp).padding(top = 1.dp),
                )
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Canvas(modifier = Modifier.fillMaxWidth().height(18.dp).clip(RoundedCornerShape(4.dp))) {
                        val shared = row.lambda?.let { (it * it).toFloat().coerceIn(0f, 1f) } ?: 0f
                        drawRect(if (row.highlight && row.lambda != null) SimColors.Active.copy(alpha = 0.5f) else SimColors.Grey.copy(alpha = 0.3f))
                        drawRect(DViolet, size = androidx.compose.ui.geometry.Size(size.width * shared, size.height))
                    }
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(row.lambda?.let { "λ ${num(it)}" } ?: "λ ?", fontFamily = IBMPlexMono, fontSize = 12.sp, color = muted, modifier = Modifier.weight(1f))
                        Text(row.lambda?.let { "ψ ${num(1 - it * it)}" } ?: "ψ ?", fontFamily = IBMPlexMono, fontSize = 12.sp, color = muted)
                    }
                }
            }
        }
    }
}
