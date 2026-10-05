package com.algora.app.feature.topics

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
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
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

// ── Forecasting and pattern-mining storyboards ───────────────────────────────
// Moving average, AR, ARIMA, SARIMA, Holt-Winters and Prophet all run on one seeded monthly series
// (48 months to fit, 12 held out) and are scored on the held-out year against seasonal naive. Apriori,
// Eclat and FP-growth mine the same ten baskets (AssociationMath). Each is one card, the arithmetic,
// chips and a headline, then either a step track or a panel of tabs, steppers and a button.

internal val seriesStoryTopicIds = setOf(
    "moving_average", "autoregression", "arima", "sarima", "exponential_smoothing", "prophet", "apriori", "eclat", "fp_growth",
)

private val SObserved = Color(0xFFB8BEC9)
private val SViolet = SimColors.Answer
private val SRed = Color(0xFFE5534B)
private val SOrange = Color(0xFFF08A3C)
private val SBlue = Color(0xFF4F7FE0)
private val SGreen = Color(0xFF3F9A62)

private fun fx(v: Double, d: Int = 2): String {
    var p = 1L
    repeat(d) { p *= 10 }
    val r = (abs(v) * p + 0.5).toLong()
    val body = if (d == 0) "${r / p}" else "${r / p}." + (r % p).toString().padStart(d, '0')
    return if (v < 0 && r != 0L) "−$body" else body
}

// ── The series ──

private class SsRng(private var state: Long) {
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

private const val Fit = 48
private const val Horizon = 12

/** Trend 0.5 a month, a yearly swing and its half-year harmonic, and noise. */
private val monthly: List<Double> by lazy {
    val r = SsRng(2)
    List(Fit + Horizon) { t -> 40 + 0.5 * t + 6 * sin(2 * PI * t / 12) + 1.5 * cos(4 * PI * t / 12) + r.g() * 1.8 }
}

private val train get() = monthly.take(Fit)
private val heldOut get() = monthly.drop(Fit)
private val naiveRmse by lazy { forecastRmse(seasonalNaiveForecast(train, Horizon), heldOut) }

/** Undoes [d] lag-1 differences: each level is rebuilt from the last value of the level above it. */
private fun undifference(forecast: List<Double>, series: List<Double>, d: Int): List<Double> {
    val levels = mutableListOf(series)
    repeat(d) { levels += difference(levels.last()) }
    var current = forecast
    for (k in d - 1 downTo 0) {
        var previous = levels[k].last()
        current = current.map { previous += it; previous }
    }
    return current
}

private fun undoSeasonal(steps: List<Double>, series: List<Double>): List<Double> {
    val out = mutableListOf<Double>()
    steps.forEachIndexed { h, step ->
        val i = series.size + h - 12
        out += (if (i < series.size) series[i] else out[i - series.size]) + step
    }
    return out
}

/** Amplitude of the 12-month cycle: least squares on a line plus one sine and cosine. */
private fun seasonalSwing(values: List<Double>): Double {
    val n = values.size
    val a = Array(4) { DoubleArray(5) }
    for (t in 0 until n) {
        val row = doubleArrayOf(1.0, t.toDouble(), sin(2 * PI * t / 12), cos(2 * PI * t / 12))
        for (i in 0 until 4) {
            for (j in 0 until 4) a[i][j] += row[i] * row[j]
            a[i][4] += row[i] * values[t]
        }
    }
    for (c in 0 until 4) {
        val p = (c until 4).maxBy { abs(a[it][c]) }
        val tmp = a[c]; a[c] = a[p]; a[p] = tmp
        for (r in 0 until 4) {
            if (r == c) continue
            val f = a[r][c] / a[c][c]
            for (k in c..4) a[r][k] -= f * a[c][k]
        }
    }
    return hypot(a[2][4] / a[2][2], a[3][4] / a[3][3])
}

/** A forecast drawn from the last fitted month, so it reads as one continuous line. */
private fun forecastLine(forecast: List<Double>, color: Color, dashed: Boolean = false) =
    SsLine(listOf(Fit - 1 to train.last()) + forecast.mapIndexed { h, v -> Fit + h to v }, color, dashed)

// ── Model ──

private class SsLine(val points: List<Pair<Int, Double>>, val color: Color, val dashed: Boolean = false, val width: Float = 2f)

private sealed interface SsBlock

private class SsSeries(val lines: List<SsLine>) : SsBlock

private class SsRow(val cells: List<String>, val ink: StoryTone? = null, val fill: StoryTone? = null, val muted: Boolean = false)

private class SsTable(val header: List<String>, val rows: List<SsRow>, val weights: List<Float>) : SsBlock

private class SsFormula(val lines: List<String>) : SsBlock

private class SsBar(val label: String, val count: Int?, val frequent: Boolean)

private class SsBars(val bars: List<SsBar>) : SsBlock

private class SsNode(val label: String, val parent: Int, val lit: Boolean)

/** Node 0 is the root; [links] is an item's header chain, drawn dashed. */
private class SsTree(val nodes: List<SsNode>, val links: List<Int>) : SsBlock

private class SsGridRow(val label: String, val ids: Set<Int>, val color: Color, val labelColor: Color? = null)

private class SsGrid(val rows: List<SsGridRow>, val resultRow: Boolean) : SsBlock

private class SsFrame(
    val headline: String,
    val body: String,
    val blocks: List<SsBlock>,
    val legend: List<Triple<Color, SwatchStyle, String>> = emptyList(),
    val chips: List<LabChip> = emptyList(),
    val action: String = "",
)

private class SsParam(val name: String, val symbol: String, val count: Int, val initial: Int, val format: (Int) -> String)

private data class SsState(val tab: Int = 0, val a: Int = 0, val b: Int = 0)

private class SsLab(
    /** A step track; otherwise a panel of tabs, steppers and a button over one frame. */
    val track: Boolean,
    val tabs: List<String> = emptyList(),
    val startTab: Int = 0,
    val params: List<SsParam> = emptyList(),
    val button: String? = null,
    val onButton: (SsState) -> SsState = { it },
    val frames: (SsState) -> List<SsFrame>,
) {
    val initial get() = SsState(startTab, params.getOrNull(0)?.initial ?: 0, params.getOrNull(1)?.initial ?: 0)
}

private val observedLegend = Triple(SObserved, SwatchStyle.Dot, "Observed")

private fun naiveRow(vararg middle: String) = SsRow(listOf("seasonal naive") + middle + fx(naiveRmse), muted = true)

/** Green when it beats seasonal naive, red when it doesn't. */
private fun verdict(rmse: Double) = if (rmse < naiveRmse) StoryTone.Done else StoryTone.Warn

// ── Moving average ──

private fun maLab(): SsLab = SsLab(track = false, tabs = listOf("k = 3", "k = 6", "k = 12"), startTab = 2) { s ->
    val ks = listOf(3, 6, 12)
    val k = ks[s.tab]
    fun ma(w: Int) = movingAverage(train, w).mapIndexedNotNull { t, v -> v?.let { t to it } }
    val rawJitter = roughness(train)
    val rawSwing = seasonalSwing(train)
    fun stats(w: Int): Triple<Double, String, Double> {
        val v = ma(w).map { it.second }
        val lag = (w - 1) / 2.0
        return Triple(roughness(v), (if (lag % 1.0 == 0.0) fx(lag, 0) else fx(lag, 1)) + " mo", seasonalSwing(v))
    }
    val (jit, lag, swing) = stats(k)
    val (jit3, lag3, swing3) = stats(3)
    val rows = mutableListOf(SsRow(listOf("raw", fx(rawJitter), "—", fx(rawSwing)), muted = true))
    if (k != 3) rows += SsRow(listOf("k = 3", fx(jit3), lag3, fx(swing3)))
    rows += SsRow(listOf("k = $k", fx(jit), lag, fx(swing)), fill = StoryTone.Answer)
    val lines = (if (k != 3) listOf(SsLine(ma(3), SimColors.Grey, dashed = true, width = 1.5f)) else emptyList()) + SsLine(ma(k), SViolet, width = 2.5f)
    val (headline, body) = when (k) {
        12 -> "A 12-month window cuts the seasonal swing from ${fx(rawSwing, 1)} to {${fx(swing)}}." to
            "What's left is the trend, $lag late. That makes MA a trend estimate, not a forecaster."
        3 -> "A 3-month window cuts jitter from ${fx(rawJitter)} to {${fx(jit)}}, but keeps the season." to
            "The swing only falls to ${fx(swing)}: three months can't average out a year."
        else -> "A $k-month window shrinks the swing to {${fx(swing)}}, $lag late." to
            "Half a season still leaks through. Only a window of a full year, 12, cancels it."
    }
    listOf(
        SsFrame(
            headline, body,
            listOf(SsSeries(lines), SsTable(listOf("series", "jitter", "lag", "season amp."), rows, listOf(1.2f, 1f, 1f, 1.3f))),
            listOf(observedLegend, Triple(SViolet, SwatchStyle.Line, "MA, k = $k")) + if (k != 3) listOf(Triple(SimColors.Grey, SwatchStyle.DashedLine, "MA, k = 3")) else emptyList(),
        ),
    )
}

// ── Autoregression ──

private fun arRmse(p: Int) = forecastRmse(arForecast(fitAr(train, p), train, Horizon), heldOut)

private fun arLab(): SsLab {
    val best = (1..14).minBy { arRmse(it) }
    return SsLab(
        track = false,
        params = listOf(SsParam("Lags", "p", 14, 1) { "${it + 1}" }),
        button = "Best by Held-out",
        onButton = { it.copy(a = best - 1) },
    ) { s ->
        val p = s.a + 1
        val fit = fitAr(train, p)
        val forecast = arForecast(fit, train, Horizon)
        val rmse = forecastRmse(forecast, heldOut)
        val ar2 = arRmse(2)
        val fitted = (p until Fit).map { t -> t to fit.predict(train.take(t)) }
        val rows = mutableListOf(naiveRow("0"))
        if (p != 2) rows += SsRow(listOf("AR(2)", "3", fx(ar2)))
        rows += SsRow(listOf("AR($p)", "${p + 1}", fx(rmse)), fill = StoryTone.Answer)
        val lines = listOf(SsLine(fitted, SViolet)) +
            (if (p != 2) listOf(forecastLine(arForecast(fitAr(train, 2), train, Horizon), SimColors.Grey, dashed = true)) else emptyList()) +
            forecastLine(forecast, SRed)
        listOf(
            SsFrame(
                if (p == 2) "AR(2) forecasts {${fx(rmse)}}, worse than seasonal naive's ${fx(naiveRmse)}."
                else "AR(2) forecasts ${fx(ar2)}; AR($p) forecasts {${fx(rmse)}}.",
                when {
                    p < 12 -> "$p ${if (p == 1) "lag can't" else "lags can't"} reach last year, so the season fades out of the forecast."
                    p == 12 -> "AR has no seasonal term, so reaching 12 months back costs 12 lags."
                    else -> "Past 12, extra lags mostly fit noise: ${p + 1} parameters from 48 months."
                },
                listOf(SsSeries(lines), SsTable(listOf("model", "params", "forecast RMSE"), rows, listOf(1.5f, 0.8f, 1.3f))),
                listOf(Triple(SViolet, SwatchStyle.Line, "AR($p) fit"), Triple(SRed, SwatchStyle.Line, "AR($p) forecast")) +
                    if (p != 2) listOf(Triple(SimColors.Grey, SwatchStyle.DashedLine, "AR(2) forecast")) else emptyList(),
            ),
        )
    }
}

// ── ARIMA ──

private fun arimaLab(): SsLab = SsLab(
    track = false,
    tabs = listOf("d = 0", "d = 1", "d = 2"),
    startTab = 1,
    params = listOf(SsParam("AR order", "p", 6, 1) { "${it + 1}" }),
) { s ->
    val p = s.a + 1
    val d = s.tab
    class Run(val acf: Double, val rows: Int, val forecast: List<Double>, val rmse: Double)
    val runs = (0..2).map { dd ->
        var w = train
        repeat(dd) { w = difference(w) }
        val f = undifference(arForecast(fitAr(w, p), w, Horizon), train, dd)
        Run(lag1Autocorrelation(w), w.size - p, f, forecastRmse(f, heldOut))
    }
    val bestD = (0..2).minBy { runs[it].rmse }
    val others = (0..2).filter { it != d }
    val lines = others.map { forecastLine(runs[it].forecast, SimColors.Grey, dashed = true) } + forecastLine(runs[d].forecast, SRed)
    val table = SsTable(
        listOf("d", "lag-1 ACF", "rows", "forecast RMSE"),
        runs.mapIndexed { i, r ->
            SsRow(listOf("$i", fx(r.acf, 3), "${r.rows}", fx(r.rmse)), ink = if (i == bestD) StoryTone.Done else null, fill = if (i == d) StoryTone.Answer else null)
        },
        listOf(0.6f, 1.2f, 0.8f, 1.4f),
    )
    val (headline, body) = when (d) {
        0 -> "Undifferenced, lag-1 ACF is {${fx(runs[0].acf)}}: the series still trends." to
            "AR($p) has to learn the trend from its own lags, and its forecast sags back toward the mean."
        1 -> "One difference drops lag-1 ACF from ${fx(runs[0].acf)} to {${fx(runs[1].acf)}}." to
            "A second difference pushes it to ${fx(runs[2].acf)}: over-differencing adds structure that isn't there."
        else -> "Two differences overshoot: lag-1 ACF is {w:${fx(runs[2].acf)}}." to
            "Integrating twice compounds every forecast error: RMSE ${fx(runs[2].rmse)} against ${fx(runs[1].rmse)} at d = 1."
    }
    listOf(
        SsFrame(
            headline, body,
            listOf(SsSeries(lines), table),
            listOf(Triple(SRed, SwatchStyle.Line, "ARIMA($p,$d,0)"), Triple(SimColors.Grey, SwatchStyle.DashedLine, "d = ${others.joinToString(", ")}")),
        ),
    )
}

// ── SARIMA ──

private fun sarimaLab(): SsLab = SsLab(
    track = false,
    tabs = listOf("Seasonal diff off", "On (lag 12)"),
    startTab = 1,
    params = listOf(SsParam("AR order", "p", 4, 0) { "${it + 1}" }),
) { s ->
    val p = s.a + 1
    val on = s.tab == 1
    val work = if (on) difference(train, 12) else train
    val fit = fitAr(work, p)
    val steps = arForecast(fit, work, Horizon)
    val forecast = if (on) undoSeasonal(steps, train) else steps
    val rmse = forecastRmse(forecast, heldOut)
    val phis = (1..p).joinToString(", ") { fx(fit.coefficients[it]) }
    val v = if (on) "z" else "y"
    val model = if (p == 1) "${v}ₜ = c + φ ${v}ₜ₋₁ + εₜ" else "${v}ₜ = c + φ₁${v}ₜ₋₁ + … + εₜ"
    val formula = SsFormula(
        listOf(
            (if (on) "zₜ = yₜ − yₜ₋₁₂, " else "") + model,
            "c = ${fx(fit.coefficients[0])}, φ = {v:$phis}, rows ${work.size} of 48",
        ),
    )
    val name = if (on) "Δ₁₂ + AR($p)" else "AR($p)"
    val tone = verdict(rmse)
    listOf(
        SsFrame(
            if (on) "One seasonal difference gets {${fx(rmse)}} with ${p + 2} parameters." else "Without the seasonal difference, AR($p) forecasts {w:${fx(rmse)}}.",
            when {
                !on -> "$p ${if (p == 1) "lag can't" else "lags can't"} reach 12 months back, so the season washes out of the forecast."
                p == 1 -> "Subtracting last year removes the season, so a single AR term models the rest."
                else -> "More AR terms barely move it: the seasonal difference already did the work."
            },
            listOf(
                SsSeries(listOf(forecastLine(forecast, SRed))),
                formula,
                SsTable(listOf("model", "forecast RMSE"), listOf(naiveRow(), SsRow(listOf(name, fx(rmse)), ink = tone, fill = tone)), listOf(2f, 1f)),
            ),
            listOf(observedLegend, Triple(SRed, SwatchStyle.Line, "Forecast")),
        ),
    )
}

// ── Holt-Winters ──

private val smoothingLadder = listOf(
    Triple(0.1, 0.05, 0.1), Triple(0.1, 0.05, 0.3), Triple(0.1, 0.05, 0.5), Triple(0.2, 0.05, 0.3),
    Triple(0.3, 0.1, 0.3), Triple(0.5, 0.1, 0.3), Triple(0.7, 0.2, 0.3), Triple(0.9, 0.3, 0.5),
)

private const val DefaultSmoothing = 4

private fun short(v: Double) = fx(v, if (Math.round(v * 100) % 10 == 0L) 1 else 2).removePrefix("0")

private fun weights(i: Int) = smoothingLadder[i].let { "${short(it.first)} ${short(it.second)} ${short(it.third)}" }

private fun hwFit(i: Int) = smoothingLadder[i].let { holtWinters(train, it.first, it.second, it.third, Horizon) }

private fun smoothingLab(): SsLab {
    val scores = smoothingLadder.indices.map { forecastRmse(hwFit(it).forecast, heldOut) }
    val best = scores.indices.minBy { scores[it] }
    return SsLab(
        track = false,
        params = listOf(SsParam("Smoothing", "αβγ", smoothingLadder.size, DefaultSmoothing, ::weights)),
        button = "Tune on Held-out",
        onButton = { it.copy(a = best) },
    ) { s ->
        val i = s.a
        val fit = hwFit(i)
        val rmse = scores[i]
        val rows = mutableListOf(naiveRow("—"), SsRow(listOf("default", weights(DefaultSmoothing), fx(scores[DefaultSmoothing]))))
        if (i != DefaultSmoothing) {
            rows += SsRow(listOf(if (i == best) "tuned" else "current", weights(i), fx(rmse)), ink = if (i == best) StoryTone.Done else null, fill = if (i == best) StoryTone.Done else StoryTone.Answer)
        }
        val (a, _, _) = smoothingLadder[i]
        listOf(
            SsFrame(
                when (i) {
                    best -> "Tuned, the forecast error is {${fx(rmse)}} against ${fx(naiveRmse)} for seasonal naive."
                    DefaultSmoothing -> "With the default weights the forecast error is {${fx(rmse)}}."
                    else -> "At α β γ = ${weights(i)} the forecast error is {${fx(rmse)}}."
                },
                when {
                    i == best -> "Level, trend and season each get their own smoothing weight."
                    a >= 0.5 -> "A high α lets each month's noise move the level, and the forecast starts from a jolt."
                    else -> "Each weight sets how fast its component forgets the past. Tune them on the held-out year."
                },
                listOf(
                    SsSeries(
                        listOf(
                            SsLine((12 until Fit).map { it to fit.level[it] }, SOrange, dashed = true, width = 1.5f),
                            SsLine((12 until Fit).map { it to fit.fitted[it] }, SViolet),
                            forecastLine(fit.forecast, SRed),
                        ),
                    ),
                    SsTable(listOf("model", "αβγ", "forecast RMSE"), rows, listOf(1.4f, 1.1f, 1.3f)),
                ),
                listOf(Triple(SViolet, SwatchStyle.Line, "Fitted"), Triple(SOrange, SwatchStyle.DashedLine, "Level"), Triple(SRed, SwatchStyle.Line, "Forecast")),
            ),
        )
    }
}

// ── Prophet ──

private fun prophetLab(): SsLab = SsLab(
    track = false,
    params = listOf(SsParam("Changepoints", "n", 9, 4) { "$it" }, SsParam("Fourier order", "K", 5, 2) { "$it" }),
) { s ->
    val n = s.a
    val k = s.b
    val fit = fitProphet(train, n, k, 1.0, Horizon)
    val rmse = forecastRmse(fit.forecast, heldOut)
    val tone = verdict(rmse)
    listOf(
        SsFrame(
            "The additive fit forecasts {${fx(rmse)}} against ${fx(naiveRmse)} for the baseline.",
            when {
                k == 0 -> "With K = 0 there is no s(t), so the forecast is a straight line through the season."
                n == 0 -> "With no changepoints g(t) is one straight line, which is all this steady trend needs."
                else -> "The penalty shrinks unneeded slope changes toward zero, so the trend doesn't chase noise."
            },
            listOf(
                SsSeries(
                    listOf(
                        SsLine((fit.trend + fit.forecastTrend).mapIndexed { t, v -> t to v }, SOrange, dashed = true, width = 1.5f),
                        SsLine(fit.fitted.mapIndexed { t, v -> t to v }, SViolet),
                        SsLine(listOf(Fit - 1 to fit.fitted.last()) + fit.forecast.mapIndexed { h, v -> Fit + h to v }, SRed),
                    ),
                ),
                SsFormula(listOf("y(t) = g(t) + s(t)", "$n changepoints, Fourier order $k, ridge λ = 1")),
                SsTable(
                    listOf("model", "in-sample", "forecast"),
                    listOf(naiveRow("—"), SsRow(listOf("Prophet-style", fx(fit.trainRmse), fx(rmse)), ink = tone, fill = tone)),
                    listOf(1.6f, 1f, 1f),
                ),
            ),
            listOf(Triple(SOrange, SwatchStyle.DashedLine, "Trend g(t)"), Triple(SViolet, SwatchStyle.Line, "g + s"), Triple(SRed, SwatchStyle.Line, "Forecast")),
        ),
    )
}

// ── Pattern mining: shared pieces ──

private fun label(items: Collection<String>) = items.sorted().joinToString("")

private fun freqItems() = basketItems.filter { support(setOf(it)) >= MinSupport }

/** Every frequent itemset of the ten baskets, by size then name. */
private val frequentSets: List<Set<String>> by lazy {
    val items = freqItems()
    val out = mutableListOf<Set<String>>()
    for (mask in 1 until (1 shl items.size)) {
        val set = items.filterIndexed { i, _ -> mask and (1 shl i) != 0 }.toSet()
        if (support(set) >= MinSupport) out += set
    }
    out.sortedWith(compareBy<Set<String>>({ it.size }, { label(it) }))
}

private fun sizesChip() = LabChip("tid-list sizes", freqItems().joinToString(" ") { "$it: ${support(setOf(it))}" })

// ── Apriori ──

private fun bar(set: Set<String>, counted: Boolean = true) = SsBar(label(set), if (counted) support(set) else null, support(set) >= MinSupport)

private fun aprioriLab(): SsLab = SsLab(track = true) {
    val singles = basketItems.map { setOf(it) }
    val l1 = freqItems()
    val dropped = basketItems.filter { it !in l1 }
    val pairs = l1.flatMapIndexed { i, a -> l1.drop(i + 1).map { b -> setOf(a, b) } }
    val l2 = pairs.filter { support(it) >= MinSupport }
    val allPairs = basketItems.size * (basketItems.size - 1) / 2
    // Join pairs that share their first item, then prune any triple with an infrequent pair.
    val joined = l2.flatMapIndexed { i, a ->
        l2.drop(i + 1).filter { b -> a.sorted()[0] == b.sorted()[0] }.map { b -> a + b }
    }.distinct()
    val kept = joined.filter { t -> t.all { x -> (t - x) in l2 } }
    val prunedBy = (joined - kept.toSet()).associateWith { t -> t.map { x -> t - x }.first { it !in l2 } }
    val l3 = kept.filter { support(it) >= MinSupport }
    val l1Line = "L1 = \\{${l1.joinToString(", ")}}  ${dropped.joinToString { "$it dropped (${support(setOf(it))})" }}"
    val l2Line = "L2 = \\{{${l2.joinToString(", ") { label(it) }}}}"
    val legend = listOf(Triple(SGreen, SwatchStyle.Fill, "Frequent"), Triple(SimColors.Grey.copy(alpha = 0.5f), SwatchStyle.Fill, "Below $MinSupport"), Triple(SimColors.Active, SwatchStyle.DashedLine, "Threshold"))
    fun passes(n: Int) = LabChip("baskets read", "$n ${if (n == 1) "pass" else "passes"}")
    val rules = frequentSets.filter { it.size >= 2 }.flatMap { set -> set.map { c -> associationRule(set - c, setOf(c)) } }.sortedByDescending { it.lift }.take(4)
    val top = rules.first()
    listOf(
        SsFrame(
            "Pass 1 counts all {${basketItems.size}} items in one read of the baskets.",
            "Support is the number of baskets holding an itemset. Min support is $MinSupport of ${transactions.size}.",
            listOf(SsBars(singles.map { bar(it) })),
            legend, listOf(passes(1)), "Prune",
        ),
        SsFrame(
            "${dropped.joinToString()} ${if (dropped.size == 1) "is" else "are"} below $MinSupport, so no itemset containing it can be frequent.",
            "That is the Apriori property: every subset of a frequent itemset is frequent too.",
            listOf(SsBars(singles.map { bar(it) }), SsFormula(listOf(l1Line))),
            legend, listOf(passes(1)), "Join Pairs",
        ),
        SsFrame(
            "Joining L1 gives {${pairs.size}} candidate pairs instead of $allPairs.",
            "Each pair is built from two frequent items. None of them has been counted yet.",
            listOf(SsBars(pairs.map { bar(it, counted = false) }), SsFormula(listOf(l1Line, "C2 = \\{${pairs.joinToString(", ") { label(it) }}}"))),
            legend, listOf(LabChip("candidates", "${pairs.size} of $allPairs", tint = StoryTone.Answer), passes(1)), "Count Pairs",
        ),
        SsFrame(
            "${l2.size} of ${pairs.size} pairs reach support $MinSupport.",
            "Only pairs of frequent items are counted, so ${allPairs - pairs.size} pairs with ${dropped.joinToString()} are never tried. Pass 3 tests only ${l3.joinToString { label(it) }}.",
            listOf(SsBars(pairs.map { bar(it) }), SsFormula(listOf(l1Line, l2Line))),
            legend, listOf(LabChip("candidates", "${pairs.size} of $allPairs", tint = StoryTone.Answer), passes(2)), "Next Pass",
        ),
        SsFrame(
            "Of ${joined.size} joined triples, only {${kept.joinToString { label(it) }}} has every pair frequent.",
            prunedBy.entries.joinToString(" ") { (t, pair) -> "${label(t)} is pruned: ${label(pair)} has support ${support(pair)}." } + " Pruning costs no basket reads.",
            listOf(SsBars(kept.map { bar(it, counted = false) }), SsFormula(joined.map { t -> if (t in kept) "${label(t)}: every pair frequent → keep" else "${label(t)}: ${label(prunedBy.getValue(t))} is rare → prune" })),
            legend, listOf(LabChip("candidates", "${kept.size} of ${joined.size}", tint = StoryTone.Answer), passes(2)), "Count Triples",
        ),
        SsFrame(
            "${l3.joinToString { label(it) }} is in {${l3.joinToString { "${support(it)}" }}} baskets: frequent.",
            "Pass 3 read every basket once more, to count a single candidate.",
            listOf(SsBars(kept.map { bar(it) }), SsFormula(listOf("L3 = \\{${l3.joinToString(", ") { label(it) }}}"))),
            legend, listOf(passes(3)), "Join Again",
        ),
        SsFrame(
            "One triple can't be joined into a 4-item set, so Apriori {stops}.",
            "Levels stop when a pass finds fewer than two frequent itemsets to join.",
            listOf(SsBars(kept.map { bar(it) }), SsFormula(listOf("L3 = \\{${l3.joinToString(", ") { label(it) }}},  C4 = ∅"))),
            legend, listOf(passes(3)), "Find Rules",
        ),
        SsFrame(
            "The strongest rule, {${top.label}}, has lift ${fx(top.lift)}.",
            "Confidence is support(both) ÷ support(left). Lift above 1 means the items go together more than chance.",
            listOf(
                SsTable(
                    listOf("rule", "supp", "conf", "lift"),
                    rules.mapIndexed { i, r -> SsRow(listOf(r.label, "${r.support}", fx(r.confidence), fx(r.lift)), fill = if (i == 0) StoryTone.Answer else null) },
                    listOf(1.4f, 0.8f, 0.9f, 0.9f),
                ),
            ),
            chips = listOf(passes(3)),
            action = "Summary",
        ),
        SsFrame(
            "{${frequentSets.size}} frequent itemsets from 3 passes over ${transactions.size} baskets.",
            "Each pass reads every basket once; pruning keeps the candidate lists short.",
            listOf(SsBars(frequentSets.map { bar(it) })),
            legend, listOf(passes(3)), "Start Over",
        ),
    )
}

// ── Eclat ──

private fun tids(set: Set<String>) = transactions.indices.filter { transactions[it].containsAll(set) }.map { it + 1 }.toSet()

private fun braces(ids: Set<Int>) = "\\{${ids.sorted().joinToString(",")}}"

private fun eclatLab(): SsLab = SsLab(track = true) {
    val items = freqItems()
    val legend = listOf(Triple(SBlue, SwatchStyle.Fill, "In tid-list"), Triple(SGreen, SwatchStyle.Fill, "In both"))
    fun row(set: Set<String>) = SsGridRow(label(set), tids(set), SBlue)
    // Each step and what the step before it should call its action.
    val steps = mutableListOf<Pair<SsFrame, String>>()
    steps += SsFrame(
        "Eclat turns the baskets sideways: each item keeps its {basket ids}.",
        "One read of the baskets builds these tid-lists. Support is then just the length of a list.",
        listOf(SsGrid(basketItems.map { row(setOf(it)) }, false)),
        legend, listOf(LabChip("tid-list sizes", basketItems.joinToString(" ") { "$it: ${support(setOf(it))}" })),
    ) to ""
    val rare = basketItems.filter { it !in items }
    steps += SsFrame(
        rare.joinToString { "$it's list has {w:${support(setOf(it))}} ids" } + ", below min support $MinSupport.",
        "Every itemset containing it would have an even shorter list, so it leaves the search.",
        listOf(SsGrid(basketItems.map { if (it in items) row(setOf(it)) else SsGridRow(it, tids(setOf(it)), SimColors.Grey.copy(alpha = 0.45f), SimColors.Grey) }, false)),
        legend, listOf(sizesChip()),
    ) to "Drop Rare Items"
    fun intersect(a: Set<String>, b: Set<String>): Boolean {
        val both = a + b
        val ids = tids(both)
        val ok = ids.size >= MinSupport
        val name = "${label(a)}∩${label(b)}"
        steps += SsFrame(
            when {
                ok && both.size == 2 -> "$name has {${ids.size}} baskets, so ${label(both)} is frequent."
                ok -> "$name = ${braces(ids)}: ${label(both)} has {${ids.size}} baskets."
                else -> "$name has only {w:${ids.size}} ${if (ids.size == 1) "basket" else "baskets"}, so ${label(both)} is dropped."
            },
            when {
                !ok -> "Anything that extends ${label(both)} would be rarer still, so that whole branch is skipped."
                both.size == 2 -> "Eclat stores each item's basket ids. Support is an intersection size, with no rescan."
                else -> "Longer itemsets come from intersecting two frequent siblings that share a prefix, never from the baskets."
            },
            listOf(
                SsGrid(listOf(row(a), row(b), SsGridRow(name, ids, SGreen, SGreen)), true),
                SsFormula(listOf("${braces(tids(a))} ∩ ${braces(tids(b))}", "= ${braces(ids)} → support {${if (ok) "" else "w:"}${ids.size}}")),
            ),
            legend, listOf(sizesChip()),
        ) to "Intersect"
        return ok
    }
    items.forEachIndexed { i, item ->
        val later = items.drop(i + 1)
        if (later.isEmpty()) return@forEachIndexed
        if (later.size > 1) {
            steps += SsFrame(
                if (i == 0) "Depth first: $item is intersected with each later item." else "$item's branch: pair $item with each later item.",
                if (i == 0) "Every itemset starting with $item is checked before moving on to ${items[1]}."
                else "Itemsets with ${items.take(i).joinToString()} were found in earlier branches, so each set is visited once.",
                listOf(SsGrid(items.drop(i).map { row(setOf(it)) }, false)),
                legend, listOf(sizesChip()),
            ) to (if (i == 0) "Start with $item" else "Next Branch")
        }
        val frequent = later.filter { intersect(setOf(item), setOf(it)) }.map { setOf(item, it) }
        frequent.forEachIndexed { j, a -> frequent.drop(j + 1).forEach { b -> intersect(a, b) } }
    }
    steps += SsFrame(
        "{${frequentSets.size}} frequent itemsets, and the baskets were read {once}.",
        "Eclat trades memory for speed: the tid-lists replace every later pass over the data.",
        listOf(SsFormula(frequentSets.groupBy { it.size }.values.map { g -> g.joinToString("  ") { "${label(it)}:${support(it)}" } })),
        legend, listOf(sizesChip()),
    ) to "Summary"
    steps.mapIndexed { i, (f, _) ->
        SsFrame(f.headline, f.body, f.blocks, f.legend, f.chips, if (i < steps.lastIndex) steps[i + 1].second else "Start Over")
    }
}

// ── FP-growth ──

private fun nodeWord(n: Int) = if (n == 1) "1 node gives" else "$n nodes give"

private fun fpLab(): SsLab = SsLab(track = true) {
    val order = fpItemOrder()
    val dropped = basketItems.filter { it !in order }
    val header = order.joinToString("  ") { "$it:${support(setOf(it))}" } + "  " + dropped.joinToString { "($it:${support(setOf(it))} dropped)" }
    val entries = transactions.sumOf { fpSortedTransaction(it).size }
    fun tree(upTo: Int, lit: Set<Int>, links: List<Int> = emptyList()): SsTree {
        val t = buildFpTree(upTo)
        return SsTree(listOf(SsNode("root", -1, false)) + t.nodes.map { SsNode("${it.item}:${it.count}", (it.parent ?: -1) + 1, it.id in lit) }, links.map { it + 1 })
    }
    fun pathOf(upTo: Int): List<Int> {
        val t = buildFpTree(upTo)
        var parent: Int? = null
        val path = mutableListOf<Int>()
        fpSortedTransaction(transactions[upTo - 1]).forEach { item ->
            val node = t.nodes.first { it.item == item && it.parent == parent }
            path += node.id
            parent = node.id
        }
        return path
    }
    val frames = mutableListOf(
        SsFrame(
            "Pass 1 counts items and orders them {${order.joinToString(", ")}}.",
            "Every basket is inserted in this order, so the common items share the top of the tree.",
            listOf(tree(0, emptySet()), SsFormula(listOf(header))),
            chips = listOf(LabChip("entries → nodes", "0 → 0")),
            action = "Insert Basket 1",
        ),
    )
    var entriesSoFar = 0
    for (b in 1..transactions.size) {
        val items = fpSortedTransaction(transactions[b - 1])
        entriesSoFar += items.size
        val before = buildFpTree(b - 1).nodes.size
        val after = buildFpTree(b).nodes.size
        val added = after - before
        val shared = items.size - added
        frames += SsFrame(
            when {
                b == 1 -> "Basket 1 starts the path {${items.joinToString(" → ")}}."
                added == 0 -> "Basket $b follows an existing path: {0} new nodes, ${items.size} counts bumped."
                shared == 0 -> "Basket $b shares nothing, so it starts {${added}} new ${if (added == 1) "node" else "nodes"} from the root."
                else -> "Basket $b shares $shared ${if (shared == 1) "node" else "nodes"} and adds {$added}."
            },
            "${label(transactions[b - 1])} sorts to ${items.joinToString(" ")}" + (if (transactions[b - 1].size > items.size) ", with the rare item left out." else ".") +
                " Shared prefixes are what keep the tree small.",
            listOf(tree(b, pathOf(b).toSet()), SsFormula(listOf(header, "basket $b: ${items.joinToString(" ")}"))),
            listOf(Triple(SimColors.Active, SwatchStyle.Fill, "This basket's path")),
            listOf(LabChip("entries → nodes", "$entriesSoFar → $after", good = true)),
            if (b < transactions.size) "Insert Basket ${b + 1}" else "Mine ${order.last()}",
        )
    }
    val full = buildFpTree()
    val mined = order.reversed()
    mined.forEachIndexed { i, item ->
        val base = conditionalPatternBase(full, item)
        val before = order.take(order.indexOf(item))
        // Every combination of earlier items that reaches min support inside this item's base.
        val found = mutableListOf<Pair<Set<String>, Int>>()
        for (mask in 1 until (1 shl before.size)) {
            val set = before.filterIndexed { j, _ -> mask and (1 shl j) != 0 }.toSet()
            val count = base.filter { it.path.containsAll(set) }.sumOf { it.count }
            if (count >= MinSupport) found += (set + item) to count
        }
        val pairCounts = before.map { other -> other to base.filter { other in it.path }.sumOf { it.count } }
        val baseText = base.joinToString(" ") { (if (it.path.isEmpty()) "∅" else it.path.joinToString("")) + ":" + it.count }
        val nodes = full.header[item].orEmpty()
        frames += SsFrame(
            when {
                before.isEmpty() -> "$item sits at the top, so its pattern base is {empty}."
                found.isEmpty() -> "$item's ${nodeWord(nodes.size)} its pattern base; no pair reaches $MinSupport."
                found.size == 1 -> "$item's ${nodeWord(nodes.size)} its pattern base; only {${label(found[0].first)}} reaches $MinSupport."
                else -> "$item's ${nodeWord(nodes.size)} its pattern base: " + found.joinToString(", ") { "{${label(it.first)}}" } + " reach $MinSupport."
            },
            if (before.isEmpty()) "Every itemset with $item was already found while mining the items below it."
            else "Shared prefixes compress the baskets into ${full.nodes.size} nodes, and mining never rescans them.",
            listOf(
                tree(transactions.size, nodes.toSet(), nodes),
                SsFormula(
                    listOf("$item's pattern base: ${baseText.ifEmpty { "∅" }}") +
                        if (pairCounts.isEmpty()) emptyList()
                        else listOf(pairCounts.joinToString("; ") { (o, c) -> "$o·$item = " + if (c >= MinSupport) "{$c}" else "$c" }),
                ),
            ),
            listOf(Triple(SimColors.Active, SwatchStyle.Fill, "$item node"), Triple(SimColors.Active, SwatchStyle.DashedLine, "Node link")),
            listOf(LabChip("entries → nodes", "$entries → ${full.nodes.size}", good = true)),
            if (i < mined.lastIndex) "Mine Next Item" else "Summary",
        )
    }
    frames += SsFrame(
        "{${frequentSets.size}} frequent itemsets from a tree of ${full.nodes.size} nodes.",
        "The baskets were read twice: once to count items, once to build the tree.",
        listOf(tree(transactions.size, emptySet()), SsFormula(frequentSets.groupBy { it.size }.values.map { g -> g.joinToString("  ") { "${label(it)}:${support(it)}" } })),
        chips = listOf(LabChip("entries → nodes", "$entries → ${full.nodes.size}", good = true)),
        action = "Start Over",
    )
    frames
}

private fun seriesLab(topicId: String): SsLab = when (topicId) {
    "autoregression" -> arLab()
    "arima" -> arimaLab()
    "sarima" -> sarimaLab()
    "exponential_smoothing" -> smoothingLab()
    "prophet" -> prophetLab()
    "apriori" -> aprioriLab()
    "eclat" -> eclatLab()
    "fp_growth" -> fpLab()
    else -> maLab()
}

/** Builds every frame each control can reach (every tab, stepper value and the button). */
internal fun seriesStoryFrameCount(topicId: String): Int {
    val lab = seriesLab(topicId)
    var total = 0
    val first = lab.params.getOrNull(0)?.count ?: 1
    val second = lab.params.getOrNull(1)?.count ?: 1
    val states = (0 until max(lab.tabs.size, 1)).flatMap { t -> (0 until first).flatMap { a -> (0 until second).map { b -> SsState(t, a, b) } } } +
        lab.onButton(lab.initial)
    states.forEach { st ->
        val frames = lab.frames(st)
        require(frames.isNotEmpty()) { "$topicId built no frames at $st" }
        frames.forEach { f ->
            require(f.headline.isNotBlank() && f.body.isNotBlank()) { "$topicId has no narration at $st" }
            require(f.blocks.isNotEmpty()) { "$topicId drew nothing at $st" }
            if (lab.track) require(f.action.isNotBlank()) { "$topicId has a step without an action at $st" }
            f.blocks.filterIsInstance<SsSeries>().forEach { s ->
                require(s.lines.all { l -> l.points.all { it.second.isFinite() } }) { "$topicId drew a non-finite point at $st" }
            }
        }
        total += frames.size
    }
    return total
}

// ── Lab ──

@Composable
internal fun SeriesStorySection(topicId: String) {
    val lab = remember(topicId) { seriesLab(topicId) }
    var state by remember(topicId) { mutableStateOf(lab.initial) }
    val frames = remember(topicId, state) { lab.frames(state) }
    val dock = LocalLabDock.current

    if (lab.track) {
        val playback = rememberPlaybackState(key = topicId, stepCount = frames.size, initialSpeedMs = 1000f)
        Column(modifier = Modifier.fillMaxWidth()) {
            SsBody(frames[playback.index.coerceIn(0, frames.lastIndex)])
            PlaybackTransport(playback, captions = frames.map { storyPlain(it.headline) }, action = { frames[it.coerceIn(0, frames.lastIndex)].action })
        }
        return
    }

    val controls: @Composable () -> Unit = {
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (lab.tabs.isNotEmpty()) LabSegments(lab.tabs, state.tab) { state = state.copy(tab = it) }
            lab.params.forEachIndexed { i, p ->
                val v = if (i == 0) state.a else state.b
                LabParamStepper(LabParam(p.name, p.name, p.symbol, p.format(v), v > 0, v < p.count - 1)) { d ->
                    val next = (v + d).coerceIn(0, p.count - 1)
                    state = if (i == 0) state.copy(a = next) else state.copy(b = next)
                }
            }
            lab.button?.let { label -> LabButton(label, primary = true, modifier = Modifier.fillMaxWidth()) { state = lab.onButton(state) } }
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        SsBody(frames[0])
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
private fun SsBody(frame: SsFrame) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            frame.blocks.forEach { block ->
                when (block) {
                    is SsSeries -> SsSeriesView(block)
                    is SsTable -> SsTableView(block)
                    is SsFormula -> SsFormulaView(block.lines)
                    is SsBars -> SsBarsView(block)
                    is SsTree -> SsTreeView(block)
                    is SsGrid -> SsGridView(block)
                }
            }
            if (frame.legend.isNotEmpty()) StoryLegendRow(frame.legend, Modifier.padding(top = 2.dp))
        }
    }
    LabChips(frame.chips, Modifier.padding(top = 16.dp))
    LabStoryNarration(frame.headline, frame.body, Modifier.padding(top = 16.dp))
}

// ── Rendering ──

private fun Modifier.ssStage() = this.clip(RoundedCornerShape(12.dp)).background(Color.Black.copy(alpha = 0.16f))

@Composable
private fun SsFormulaView(lines: List<String>) {
    Column(
        modifier = Modifier.fillMaxWidth().background(SimColors.Tint, RoundedCornerShape(10.dp)).padding(vertical = 10.dp, horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        lines.forEach { line ->
            Text(storyAnnotated(line), fontFamily = IBMPlexMono, fontSize = 13.sp, lineHeight = 20.sp, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f))
        }
    }
}

@Composable
private fun SsSeriesView(series: SsSeries) {
    val measurer = rememberTextMeasurer()
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val surface = MaterialTheme.colorScheme.surface
    Canvas(modifier = Modifier.fillMaxWidth().aspectRatio(2f).ssStage()) {
        val pad = 14.dp.toPx()
        val lo = monthly.min()
        val hi = monthly.max()
        val span = hi - lo
        val yLo = lo - span * 0.08
        val yHi = hi + span * 0.18
        fun at(t: Int, v: Double) = Offset(pad + (t + 0.5f) / monthly.size * (size.width - 2 * pad), (size.height - pad - ((v - yLo) / (yHi - yLo)).toFloat() * (size.height - 2 * pad)))
        val split = at(Fit, 0.0).x - (size.width - 2 * pad) / monthly.size / 2
        drawLine(muted.copy(alpha = 0.5f), Offset(split, pad / 2), Offset(split, size.height - pad / 2), 1.dp.toPx())
        drawText(measurer, "held out", Offset(split + 4.dp.toPx(), pad / 2), TextStyle(color = muted, fontSize = 11.sp))
        val dash = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx()))
        series.lines.forEach { line ->
            val path = Path()
            line.points.forEachIndexed { i, (t, v) -> val o = at(t, v); if (i == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y) }
            drawPath(path, line.color, style = Stroke(line.width.dp.toPx(), pathEffect = if (line.dashed) dash else null))
        }
        monthly.forEachIndexed { t, v ->
            val o = at(t, v)
            if (t < Fit) {
                drawCircle(SObserved, 3.dp.toPx(), o)
            } else {
                drawCircle(surface, 4.dp.toPx(), o)
                drawCircle(Color.White.copy(alpha = 0.85f), 4.dp.toPx(), o, style = Stroke(1.5.dp.toPx()))
            }
        }
    }
}

@Composable
private fun SsTableView(table: SsTable) {
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
                            StoryTone.Answer -> SimColors.Answer.copy(alpha = 0.22f)
                            else -> row.fill.color().copy(alpha = 0.16f)
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
private fun SsBarsView(block: SsBars) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val onSurface = MaterialTheme.colorScheme.onSurface
    val measurer = rememberTextMeasurer()
    val top = transactions.size.toFloat()
    Column(modifier = Modifier.fillMaxWidth().ssStage().padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 6.dp)) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                block.bars.forEach { bar ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(bar.label, fontFamily = IBMPlexMono, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = if (bar.count == null || bar.frequent) onSurface else muted, maxLines = 1, modifier = Modifier.width(40.dp))
                        Canvas(modifier = Modifier.weight(1f).height(18.dp)) {
                            drawRoundRect(SimColors.Tint, cornerRadius = CornerRadius(4.dp.toPx()))
                            bar.count?.let { c ->
                                drawRoundRect(if (bar.frequent) SGreen else SimColors.Grey.copy(alpha = 0.5f), size = Size(size.width * c / top, size.height), cornerRadius = CornerRadius(4.dp.toPx()))
                            }
                            val x = size.width * MinSupport / top
                            drawLine(SimColors.Active, Offset(x, -3.dp.toPx()), Offset(x, size.height + 3.dp.toPx()), 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx())))
                        }
                        Text(
                            bar.count?.toString() ?: "?",
                            fontFamily = IBMPlexMono, fontSize = 13.sp, textAlign = TextAlign.End,
                            color = if (bar.count != null && bar.frequent) StoryTone.Done.ink() else muted,
                            modifier = Modifier.width(32.dp),
                        )
                    }
                }
            }
        }
        Row {
            Box(Modifier.width(40.dp))
            Canvas(modifier = Modifier.weight(1f).height(16.dp)) {
                drawText(measurer, "min support $MinSupport", Offset(size.width * MinSupport / top + 4.dp.toPx(), 0f), TextStyle(color = SimColors.Active, fontSize = 11.sp))
            }
            Box(Modifier.width(32.dp))
        }
    }
}

@Composable
private fun SsTreeView(tree: SsTree) {
    val measurer = rememberTextMeasurer()
    val onSurface = MaterialTheme.colorScheme.onSurface
    // Depth of each node, and x slots: leaves take consecutive slots in depth-first order and each
    // parent sits over the middle of its children.
    val children = tree.nodes.indices.groupBy { tree.nodes[it].parent }
    val depth = IntArray(tree.nodes.size)
    val x = DoubleArray(tree.nodes.size)
    var slot = 0
    fun place(i: Int, d: Int) {
        depth[i] = d
        val kids = children[i].orEmpty()
        if (kids.isEmpty()) {
            x[i] = slot++.toDouble()
        } else {
            kids.forEach { place(it, d + 1) }
            x[i] = (x[kids.first()] + x[kids.last()]) / 2
        }
    }
    place(0, 0)
    val levels = (depth.maxOrNull() ?: 0) + 1
    val slots = max(slot, 1)
    Canvas(modifier = Modifier.fillMaxWidth().height((28 + levels * 48).dp).ssStage()) {
        val w = 50.dp.toPx()
        val h = 26.dp.toPx()
        fun at(i: Int) = Offset(
            if (slots == 1) size.width / 2 else (w / 2 + 14.dp.toPx() + (x[i] / (slots - 1)).toFloat() * (size.width - w - 28.dp.toPx())),
            22.dp.toPx() + depth[i] * 48.dp.toPx(),
        )
        tree.nodes.forEachIndexed { i, n -> if (n.parent >= 0) drawLine(SimColors.Grey.copy(alpha = 0.6f), at(n.parent), at(i), 1.5.dp.toPx()) }
        tree.links.zipWithNext().forEach { (a, b) ->
            drawLine(SimColors.Active, at(a), at(b), 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())))
        }
        tree.nodes.forEachIndexed { i, n ->
            val c = at(i)
            drawRoundRect(if (n.lit) SimColors.Active else Color(0xFF2E3340), Offset(c.x - w / 2, c.y - h / 2), Size(w, h), CornerRadius(6.dp.toPx()))
            val style = TextStyle(color = if (n.lit) Color(0xFF1B1F27) else onSurface, fontSize = 12.sp, fontFamily = IBMPlexMono, fontWeight = FontWeight.Bold)
            val m = measurer.measure(n.label, style)
            drawText(m, topLeft = Offset(c.x - m.size.width / 2, c.y - m.size.height / 2))
        }
    }
}

@Composable
private fun SsGridView(grid: SsGrid) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val onSurface = MaterialTheme.colorScheme.onSurface
    val n = transactions.size
    Column(modifier = Modifier.fillMaxWidth().ssStage().padding(horizontal = 10.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row {
            Box(Modifier.width(44.dp))
            (1..n).forEach { Text("$it", fontFamily = IBMPlexMono, fontSize = 11.sp, color = muted, textAlign = TextAlign.Center, modifier = Modifier.weight(1f)) }
        }
        grid.rows.forEachIndexed { r, row ->
            if (grid.resultRow && r == grid.rows.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(row.label, fontFamily = IBMPlexMono, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = row.labelColor ?: onSurface, maxLines = 1, modifier = Modifier.width(44.dp))
                (1..n).forEach { id ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 2.dp)
                            .height(24.dp)
                            .background(if (id in row.ids) row.color else SimColors.Tint, RoundedCornerShape(5.dp)),
                    )
                }
            }
        }
    }
}
