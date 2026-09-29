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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.algora.app.core.ui.theme.CategoryAccents
import com.algora.app.core.ui.theme.IBMPlexMono
import com.algora.app.core.ui.theme.SimColors
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.random.Random

// ── Classification-metric storyboards ────────────────────────────────────────
// Confusion Matrix, Model Evaluation, Precision & Recall, Accuracy, ROC Curve, F1 Score, AUC, Log Loss
// and Cohen's Kappa, each a figure over one model's scores (a score histogram, a dot strip, a metric
// curve, F-beta bars or the ROC curve), the step's arithmetic, chips and a headline, then a threshold
// (or prediction) stepper and one action. Every number is computed from the fixed scores below.

internal val metricStoryTopicIds = setOf(
    "confusion_matrix", "model_evaluation", "precision_recall", "accuracy", "roc_curve",
    "f1_score", "auc", "log_loss", "cohens_kappa",
)

private val NegC = SimColors.Blue
private val PosC = CategoryAccents.Pink
private val PinkInk = Color(0xFFF472B6)

// ── Data: 1,000 scored cases, 88 of them positive ──

private class MtScored(val score: Double, val positive: Boolean)

private val cases: List<MtScored> by lazy {
    val random = Random(17)
    val negatives = (0 until 912).map { MtScored(0.52 * random.nextDouble().pow(6), false) }
    val positives = (0 until 88).map { MtScored(random.nextDouble().pow(0.8), true) }
    negatives + positives
}

private const val N_POS = 88
private const val N_NEG = 912

private class MtCounts(val tp: Int, val fn: Int, val fp: Int, val tn: Int) {
    val n get() = tp + fn + fp + tn
    val precision get() = if (tp + fp == 0) 1.0 else tp.toDouble() / (tp + fp)
    val recall get() = if (tp + fn == 0) 0.0 else tp.toDouble() / (tp + fn)
    val accuracy get() = (tp + tn).toDouble() / n
    fun fBeta(beta: Double): Double {
        val p = precision
        val r = recall
        val b2 = beta * beta
        return if (p + r == 0.0) 0.0 else (1 + b2) * p * r / (b2 * p + r)
    }
    val fpr get() = fp.toDouble() / (fp + tn)
    val chance get() = ((tp + fp).toDouble() / n) * ((tp + fn).toDouble() / n) + ((fn + tn).toDouble() / n) * ((fp + tn).toDouble() / n)
    val kappa get() = (accuracy - chance) / (1 - chance)
}

private fun countsOf(data: List<MtScored>, t: Double): MtCounts {
    var tp = 0
    var fn = 0
    var fp = 0
    var tn = 0
    data.forEach { c ->
        val flag = c.score >= t
        when {
            c.positive && flag -> tp++
            c.positive -> fn++
            flag -> fp++
            else -> tn++
        }
    }
    return MtCounts(tp, fn, fp, tn)
}

private val aucPairs: Pair<Long, Long> by lazy {
    val pos = cases.filter { it.positive }.map { it.score }
    val neg = cases.filter { !it.positive }.map { it.score }
    var right = 0L
    pos.forEach { p -> neg.forEach { q -> if (p > q) right++ } }
    right to pos.size.toLong() * neg.size
}

private val rocPoints: List<Pair<Double, Double>> by lazy {
    (100 downTo 0).map { i -> countsOf(cases, i / 100.0).let { it.fpr to it.recall } }
}

/** The ROC curve at every distinct score, so its trapezoid area equals the pair-counting AUC exactly. */
private val exactRoc: List<Pair<Double, Double>> by lazy {
    val sorted = cases.sortedByDescending { it.score }
    val points = mutableListOf(0.0 to 0.0)
    var tp = 0
    var fp = 0
    sorted.forEachIndexed { i, c ->
        if (c.positive) tp++ else fp++
        if (i == sorted.lastIndex || sorted[i + 1].score != c.score) points += fp.toDouble() / N_NEG to tp.toDouble() / N_POS
    }
    points
}

private val modelLogLoss: Double by lazy {
    cases.sumOf { c ->
        val p = c.score.coerceIn(1e-4, 1 - 1e-4)
        -(if (c.positive) ln(p) else ln(1 - p))
    } / cases.size
}

/** The small strip for Model Evaluation: nine negatives and nine positives on one score axis. */
private val stripCases = listOf(0.10, 0.15, 0.23, 0.27, 0.30, 0.36, 0.41, 0.49, 0.62).map { MtScored(it, false) } +
    listOf(0.38, 0.45, 0.52, 0.56, 0.60, 0.64, 0.75, 0.82, 0.90).map { MtScored(it, true) }

// ── Formatting ──

private fun mx(v: Double, d: Int = 2): String {
    var p = 1L
    repeat(d) { p *= 10 }
    val r = (abs(v) * p + 0.5).toLong()
    val body = if (d == 0) "${r / p}" else "${r / p}." + (r % p).toString().padStart(d, '0')
    return if (v < 0 && r != 0L) "−$body" else body
}

private fun pct(v: Double) = "${(v * 100).roundToInt()}%"

private fun thousands(n: Long) = "%,d".format(n)

// ── Scenes ──

private sealed interface MtScene

private class MtHistScene(val t: Double, val byOutcome: Boolean) : MtScene

private class MtStripScene(val t: Double) : MtScene

private class MtSeries(val points: List<Pair<Double, Double>>, val color: Color?, val dashed: Boolean = false, val faint: Boolean = false, val width: Float = 2.5f)

private class MtMarker(val x: Double, val y: Double, val color: Color)

private class MtCurveScene(
    val series: List<MtSeries>,
    val markers: List<MtMarker> = emptyList(),
    val vline: Double? = null,
    val xTitle: String,
    val yTitle: String? = null,
    val yTop: String = "1",
    val yMax: Double = 1.0,
    val fillUnder: MtSeries? = null,
) : MtScene

private class MtFBars(val values: List<Double>, val selected: Int) : MtScene

private class MtFrame(
    val headline: String,
    val body: String,
    val scene: MtScene,
    val matrix: MtCounts? = null,
    val pair: Pair<Double, Double>? = null,
    val formula: List<String> = emptyList(),
    val legend: List<Triple<Color, SwatchStyle, String>> = emptyList(),
    val chips: List<LabChip> = emptyList(),
)

/** p: the stepper's index; tab: the picker; flag: the action's own state (a toggle or a draw count). */
private data class MtState(val p: Int, val tab: Int = 0, val flag: Int = 0)

private class MtParam(val name: String, val symbol: String, val values: List<Double>, val format: (Double) -> String = { mx(it) })

private class MtLab(
    val initial: MtState,
    val frame: (MtState) -> MtFrame,
    val param: MtParam? = null,
    val tabs: List<String> = emptyList(),
    val tabsInDock: Boolean = false,
    val action: ((MtState) -> String)? = null,
    val onAction: (MtState) -> MtState = { it },
)

private fun ladder(from: Double, to: Double, step: Double) = (0..((to - from) / step).roundToInt()).map { from + it * step }

private val thresholds = ladder(0.05, 0.95, 0.05)

private fun nearest(values: List<Double>, v: Double) = values.indices.minBy { abs(values[it] - v) }

private fun curve(f: (MtCounts) -> Double) = (0..100).map { i -> (i / 100.0) to f(countsOf(cases, i / 100.0)) }

private val thresholdLegend = { t: Double -> Triple(SimColors.Active, SwatchStyle.DashedLine, "t = ${mx(t)}") }

// ── Labs ──

private fun confusionLab() = MtLab(
    initial = MtState(nearest(thresholds, 0.5)),
    param = MtParam("Threshold", "t", thresholds),
    action = { if (it.flag == 0) "Split by Outcome" else "Show Classes" },
    onAction = { it.copy(flag = 1 - it.flag) },
    frame = { s ->
        val t = thresholds[s.p]
        val c = countsOf(cases, t)
        if (s.flag == 0) {
            MtFrame(
                "Every metric in this chapter is built from these {four counts}.",
                "At t = ${mx(t)} the model flags ${c.tp + c.fp} cases. ${c.tp} are real and ${c.fn} positives slip under the line.",
                MtHistScene(t, byOutcome = false),
                matrix = c,
                legend = listOf(
                    Triple(NegC, SwatchStyle.Fill, "$N_NEG negatives"),
                    Triple(PosC, SwatchStyle.Fill, "$N_POS positives"),
                    Triple(Color.Unspecified, SwatchStyle.Line, "Threshold"),
                ),
            )
        } else {
            MtFrame(
                "{w:${c.fn} positives} fall below t and {w:${c.fp} negatives} rise above it.",
                "Those two cells are the only errors. Raising t trades false positives for false negatives; lowering it does the reverse.",
                MtHistScene(t, byOutcome = true),
                matrix = c,
                legend = listOf(
                    Triple(SimColors.Green, SwatchStyle.Fill, "Correct"),
                    Triple(SimColors.Red, SwatchStyle.Fill, "Wrong"),
                    Triple(Color.Unspecified, SwatchStyle.Line, "Threshold"),
                ),
            )
        }
    },
)

private fun rateChip(key: String, v: Double) = when {
    v >= 0.8 -> LabChip(key, mx(v), good = true)
    v < 0.5 -> LabChip(key, mx(v), tint = StoryTone.Warn)
    else -> LabChip(key, mx(v))
}

private val stripThresholds = ladder(0.04, 0.96, 0.04)

private fun modelEvaluationLab() = MtLab(
    initial = MtState(nearest(stripThresholds, 0.72)),
    param = MtParam("Threshold", "t", stripThresholds),
    action = { "Find Best F1" },
    onAction = { s -> s.copy(p = stripThresholds.indices.maxBy { countsOf(stripCases, stripThresholds[it]).fBeta(1.0) }) },
    frame = { s ->
        val t = stripThresholds[s.p]
        val c = countsOf(stripCases, t)
        val (headline, body) = when {
            c.fp == 0 && c.tp > 0 -> "At t = ${mx(t)} every flag is right, but {w:${c.fn} of ${c.tp + c.fn}} positives are missed." to
                "Precision is 1.00 and recall is ${mx(c.recall)}. That suits a spam filter, where a false positive costs real mail."
            c.fn == 0 -> "At t = ${mx(t)} every positive is caught, but {w:${c.fp} of ${c.tp + c.fp}} flags are false alarms." to
                "Recall is 1.00 and precision is ${mx(c.precision)}. That suits screening, where a miss costs more than a second look."
            else -> "At t = ${mx(t)} the model makes {w:${c.fp} false alarm${if (c.fp == 1) "" else "s"}} and {w:${c.fn} miss${if (c.fn == 1) "" else "es"}}." to
                "Precision ${mx(c.precision)}, recall ${mx(c.recall)}, F1 ${mx(c.fBeta(1.0))}. Find Best F1 picks the threshold that balances them."
        }
        MtFrame(
            headline,
            body,
            MtStripScene(t),
            matrix = c,
            legend = listOf(Triple(NegC, SwatchStyle.Dot, "Negative"), Triple(PosC, SwatchStyle.Dot, "Positive"), Triple(SimColors.Red, SwatchStyle.Ring, "Wrong")),
            chips = listOf(rateChip("P", c.precision), rateChip("R", c.recall), LabChip("F1", mx(c.fBeta(1.0))), LabChip("acc", mx(c.accuracy))),
        )
    },
)

private fun precisionRecallLab() = MtLab(
    initial = MtState(nearest(thresholds, 0.1)),
    param = MtParam("Threshold", "t", thresholds),
    action = { "Find Crossover" },
    onAction = { s -> s.copy(p = thresholds.indices.minBy { countsOf(cases, thresholds[it]).let { c -> abs(c.precision - c.recall) } }) },
    frame = { s ->
        val t = thresholds[s.p]
        val c = countsOf(cases, t)
        val flags = c.tp + c.fp
        val falseAlarms = if (flags == 0) 0.0 else c.fp.toDouble() / flags
        val (headline, body) = when {
            abs(c.precision - c.recall) < 0.05 -> "At t = ${mx(t)} precision and recall {meet} near ${mx((c.precision + c.recall) / 2)}." to
                "Below this t recall wins, above it precision does. The crossover is one reasonable default, not a rule."
            falseAlarms > 0.3 -> "At t = ${mx(t)} recall is ${mx(c.recall)}, but {w:${pct(falseAlarms)}} of flags are false alarms." to
                "Same model, same scores. Moving t only trades one error for the other, so it's a business decision."
            else -> "At t = ${mx(t)} precision is ${mx(c.precision)}, but recall falls to {w:${mx(c.recall)}}." to
                "Same model, same scores. Moving t only trades one error for the other, so it's a business decision."
        }
        MtFrame(
            headline,
            body,
            MtCurveScene(
                listOf(MtSeries(curve { it.precision }, NegC), MtSeries(curve { it.recall }, PosC)),
                markers = listOf(MtMarker(t, c.precision, NegC), MtMarker(t, c.recall, PosC)),
                vline = t,
                xTitle = "threshold t",
            ),
            formula = listOf(
                "P = TP / (TP + FP) = ${c.tp} / $flags = {v:${mx(c.precision, 3)}}",
                "R = TP / (TP + FN) = ${c.tp} / $N_POS = {v:${mx(c.recall, 3)}}",
            ),
            legend = listOf(Triple(NegC, SwatchStyle.Line, "Precision"), Triple(PosC, SwatchStyle.Line, "Recall"), thresholdLegend(t)),
            chips = listOf(LabChip("flags", "$flags"), LabChip("false alarms", pct(falseAlarms), tint = if (falseAlarms > 0.3) StoryTone.Warn else null)),
        )
    },
)

private val accuracyThresholds = ladder(0.05, 1.0, 0.05)

private fun accuracyLab() = MtLab(
    initial = MtState(nearest(accuracyThresholds, 0.9)),
    param = MtParam("Threshold", "t", accuracyThresholds),
    action = { "Show Baseline" },
    onAction = { s -> s.copy(p = accuracyThresholds.lastIndex) },
    frame = { s ->
        val t = accuracyThresholds[s.p]
        val c = countsOf(cases, t)
        val baseline = N_NEG.toDouble() / (N_NEG + N_POS)
        val (headline, body) = when {
            c.tp == 0 -> "Flag nobody and accuracy is still {${mx(baseline, 3)}}." to
                "Recall is 0: the model catches no positives at all, yet it beats most thresholds on accuracy. That is why accuracy misleads on imbalanced data."
            c.recall < 0.5 -> "At t = ${mx(t)} accuracy is ${mx(c.accuracy)}, and recall is only {w:${mx(c.recall)}}." to
                "Calling everyone negative already scores ${mx(baseline, 3)}, because ${pct(baseline)} of cases are negative."
            else -> "At t = ${mx(t)} accuracy is ${mx(c.accuracy)} and recall {m:${mx(c.recall)}}." to
                "Accuracy barely moves across thresholds, because ${pct(baseline)} of cases are negative. Recall shows what it hides."
        }
        MtFrame(
            headline,
            body,
            MtCurveScene(
                listOf(
                    MtSeries(curve { it.accuracy }, NegC),
                    MtSeries(curve { it.recall }, PosC),
                    MtSeries(listOf(0.0 to baseline, 1.0 to baseline), SimColors.Grey, dashed = true, width = 1.5f),
                ),
                markers = listOf(MtMarker(t, c.accuracy, NegC), MtMarker(t, c.recall, PosC)),
                vline = t,
                xTitle = "threshold t",
            ),
            formula = listOf("acc = (TP + TN) / n = (${c.tp} + ${c.tn}) / ${c.n} = {v:${mx(c.accuracy, 3)}}"),
            legend = listOf(Triple(NegC, SwatchStyle.Line, "Accuracy"), Triple(PosC, SwatchStyle.Line, "Recall"), Triple(SimColors.Grey, SwatchStyle.DashedLine, "Always “negative”")),
            chips = listOf(
                LabChip("acc", mx(c.accuracy, 3), tint = StoryTone.Path),
                LabChip("recall", mx(c.recall, 3), tint = if (c.recall < 0.5) StoryTone.Warn else null),
                LabChip("baseline", mx(baseline, 3)),
            ),
        )
    },
)

private val rocThresholds = ladder(0.0, 0.95, 0.05)

private fun rocLab() = MtLab(
    initial = MtState(nearest(rocThresholds, 0.2)),
    param = MtParam("Threshold", "t", rocThresholds),
    action = { "Trace Full Curve" },
    onAction = { s -> s.copy(p = 0) },
    frame = { s ->
        val t = rocThresholds[s.p]
        val c = countsOf(cases, t)
        val traced = (100 downTo (t * 100).roundToInt()).map { i -> countsOf(cases, i / 100.0).let { it.fpr to it.recall } }
        val auc = aucPairs.first.toDouble() / aucPairs.second
        val (headline, body) = if (t < 1e-9) {
            "Traced end to end, the curve {hugs the top-left}: AUC ${mx(auc, 3)}." to
                "At t = 0 everything is flagged, so both rates reach 1. A useless model would follow the dashed diagonal."
        } else {
            "Each threshold is {one point}. Lowering t moves up and to the right." to
                "At t = ${mx(t)} the model catches ${pct(c.recall)} of positives for ${mx(c.fpr * 100, 1)}% false alarms. The faint line is the path still to trace."
        }
        MtFrame(
            headline,
            body,
            MtCurveScene(
                listOf(
                    MtSeries(listOf(0.0 to 0.0, 1.0 to 1.0), SimColors.Grey, dashed = true, width = 1.5f),
                    MtSeries(rocPoints, NegC, faint = true),
                    MtSeries(traced, NegC),
                ),
                markers = listOf(MtMarker(c.fpr, c.recall, SimColors.Active)),
                xTitle = "false positive rate",
                yTitle = "true positive rate",
            ),
            formula = listOf("TPR = ${c.tp} / $N_POS = {v:${mx(c.recall, 3)}}   FPR = ${c.fp} / $N_NEG = {v:${mx(c.fpr, 3)}}"),
            legend = listOf(Triple(NegC, SwatchStyle.Line, "Traced so far"), Triple(SimColors.Active, SwatchStyle.Dot, "t = ${mx(t)}"), Triple(SimColors.Grey, SwatchStyle.DashedLine, "Chance")),
        )
    },
)

private val betas = listOf(0.5, 1.0, 2.0)

private fun f1Lab() = MtLab(
    initial = MtState(nearest(thresholds, 0.7), tab = 1),
    param = MtParam("Threshold", "t", thresholds),
    tabs = listOf("β = 0.5", "β = 1", "β = 2"),
    tabsInDock = true,
    frame = { s ->
        val t = thresholds[s.p]
        val c = countsOf(cases, t)
        val f = betas.map { c.fBeta(it) }
        val b = betas[s.tab]
        val bl = if (b == 0.5) "0.5" else mx(b, 0)
        val name = "F$bl"
        val headline = when (s.tab) {
            0 -> "F0.5 weights precision {4×} as much as recall."
            1 -> "F1 weights precision and recall {1 : 1}."
            else -> "F2 weights recall {4×} as much as precision."
        }
        val p = mx(c.precision)
        val r = mx(c.recall)
        MtFrame(
            headline,
            "At t = ${mx(t)} precision is $p and recall $r. If a miss costs more, use β = 2: F2 is ${mx(f[2], 3)}. β = 0.5 rewards the precision: ${mx(f[0], 3)}.",
            MtFBars(f, s.tab),
            formula = listOf(
                "Fβ = (1 + β²) PR / (β²P + R)",
                if (s.tab == 1) "F1 = 2 × $p × $r / ($p + $r) = {v:${mx(f[1], 3)}}"
                else "$name = ${mx(1 + b * b)} × $p × $r / (${mx(b * b)} × $p + $r) = {v:${mx(f[s.tab], 3)}}",
            ),
            chips = listOf(LabChip("P", p, tint = StoryTone.Path), LabChip("R", r, tintColor = PosC), LabChip("t", mx(t))),
        )
    },
)

private fun aucLab() = MtLab(
    initial = MtState(0, tab = 1),
    tabs = listOf("Trapezoid", "Pair counting"),
    action = { if (it.tab == 0) "Count Pairs" else "Draw Another Pair" },
    onAction = { s -> if (s.tab == 0) s.copy(tab = 1) else s.copy(flag = s.flag + 1) },
    frame = { s ->
        val (right, all) = aucPairs
        val auc = right.toDouble() / all
        val trapezoid = exactRoc.zipWithNext().sumOf { (a, b) -> (b.first - a.first) * (a.second + b.second) / 2 }
        val curveScene = MtCurveScene(
            listOf(MtSeries(listOf(0.0 to 0.0, 1.0 to 1.0), SimColors.Grey, dashed = true, width = 1.5f), MtSeries(rocPoints, NegC)),
            xTitle = "false positive rate",
            yTitle = "true positive rate",
            fillUnder = MtSeries(rocPoints, NegC),
        )
        if (s.tab == 0) {
            MtFrame(
                "AUC is the {area} under the ROC curve.",
                "Trapezoids under each step of the curve add up to ${mx(trapezoid, 4)}. A model that ranks at random scores 0.5, the area under the diagonal.",
                curveScene,
                formula = listOf("AUC = Σ ½ (y₁ + y₂)(x₂ − x₁) = {v:${mx(trapezoid, 4)}}"),
            )
        } else {
            val random = Random(40 + s.flag)
            val pos = cases.filter { it.positive }.let { it[random.nextInt(it.size)] }.score
            val neg = cases.filter { !it.positive }.let { it[random.nextInt(it.size)] }.score
            val (headline, body) = if (pos > neg) {
                "AUC is the chance a random positive {outranks} a random negative." to
                    "Counting all ${thousands(all)} pairs gives the same ${mx(auc, 4)} as the trapezoid area. No threshold needed."
            } else {
                "This pair is {w:ranked wrong}: the negative outscores the positive." to
                    "Only ${thousands(all - right)} of the ${thousands(all)} pairs go this way, which is why AUC is ${mx(auc, 4)}."
            }
            MtFrame(
                headline,
                body,
                curveScene,
                pair = pos to neg,
                formula = listOf("AUC = pairs ranked right / all pairs", "= ${thousands(right)} / ${thousands(all)} = {v:${mx(auc, 4)}}"),
            )
        }
    },
)

private val predictions = listOf(0.01) + ladder(0.06, 0.96, 0.05) + listOf(0.99)

private fun logLossLab() = MtLab(
    initial = MtState(nearest(predictions, 0.51)),
    param = MtParam("Prediction", "p", predictions),
    action = { if (it.flag == 0) "Score Whole Model" else "Back to One Case" },
    onAction = { it.copy(flag = 1 - it.flag) },
    frame = { s ->
        val p = predictions[s.p]
        val cost = -ln(p)
        val pos = (1..99).map { it / 100.0 }.map { it to -ln(it) }
        val neg = (1..99).map { it / 100.0 }.map { it to -ln(1 - it) }
        val (headline, body) = when {
            s.flag == 1 -> "Over all 1,000 cases the model averages {${mx(modelLogLoss, 4)}}." to
                "Log loss is the mean of these costs, so a few confident mistakes dominate it. Accuracy would count them the same as near misses."
            p < 0.3 -> "A positive at p = {${mx(p)}} costs {w:${mx(cost)}}: confident mistakes cost the most." to
                "At t = 0.5 this is just one wrong answer, but log loss charges more the surer the model was."
            else -> "A positive at p = {${mx(p)}} costs ${mx(cost)}; at 0.99 it costs almost nothing." to
                "Both count as correct at t = 0.5, but log loss scores the probability itself. Confident mistakes cost the most."
        }
        MtFrame(
            headline,
            body,
            MtCurveScene(
                listOf(MtSeries(neg, NegC.copy(alpha = 0.7f)), MtSeries(pos, PosC)),
                markers = listOf(MtMarker(0.05, -ln(0.05), SimColors.Red), MtMarker(0.99, -ln(0.99), SimColors.Green), MtMarker(p, cost, SimColors.Active)),
                xTitle = "predicted p",
                yTitle = "cost",
                yTop = "4",
                yMax = 4.0,
            ),
            formula = listOf("positive: −log(${mx(p)}) = {v:${mx(cost)}}", "0.99 → ${mx(-ln(0.99))}   0.05 → {w:${mx(-ln(0.05))}}"),
            legend = listOf(Triple(PosC, SwatchStyle.Line, "−log p (positive)"), Triple(NegC, SwatchStyle.Line, "−log(1 − p) (negative)")),
            chips = if (s.flag == 1) listOf(LabChip("model log loss", mx(modelLogLoss, 4), tint = StoryTone.Path)) else emptyList(),
        )
    },
)

private fun kappaLab() = MtLab(
    initial = MtState(nearest(thresholds, 0.5)),
    param = MtParam("Threshold", "t", thresholds),
    action = { "Find Best κ" },
    onAction = { s -> s.copy(p = thresholds.indices.maxBy { countsOf(cases, thresholds[it]).kappa }) },
    frame = { s ->
        val t = thresholds[s.p]
        val c = countsOf(cases, t)
        MtFrame(
            "Chance alone would get {${mx(c.chance, 3)}} right, so kappa only counts the part above that.",
            "Accuracy is ${mx(c.accuracy, 3)}, but κ is ${mx(c.kappa, 3)}. Across thresholds κ moves where accuracy stays flat.",
            MtCurveScene(
                listOf(MtSeries(curve { it.accuracy }, SimColors.Grey), MtSeries(curve { it.kappa.coerceAtLeast(0.0) }, null)),
                markers = listOf(MtMarker(t, c.accuracy, SimColors.Grey), MtMarker(t, c.kappa, SimColors.Active)),
                vline = t,
                xTitle = "threshold t",
            ),
            formula = listOf("κ = (p₀ − pₑ) / (1 − pₑ)", "= (${mx(c.accuracy, 3)} − ${mx(c.chance, 3)}) / (1 − ${mx(c.chance, 3)}) = {v:${mx(c.kappa, 3)}}"),
            legend = listOf(Triple(SimColors.Grey, SwatchStyle.Line, "Accuracy"), Triple(Color.Unspecified, SwatchStyle.Line, "Kappa"), thresholdLegend(t)),
        )
    },
)

private fun metricLab(topicId: String): MtLab = when (topicId) {
    "model_evaluation" -> modelEvaluationLab()
    "precision_recall" -> precisionRecallLab()
    "accuracy" -> accuracyLab()
    "roc_curve" -> rocLab()
    "f1_score" -> f1Lab()
    "auc" -> aucLab()
    "log_loss" -> logLossLab()
    "cohens_kappa" -> kappaLab()
    else -> confusionLab()
}

// ── Lab ──

@Composable
internal fun MetricStorySection(topicId: String) {
    val lab = remember(topicId) { metricLab(topicId) }
    var state by remember(topicId) { mutableStateOf(lab.initial) }
    val frame = remember(state) { lab.frame(state) }
    val dock = LocalLabDock.current

    val controls: @Composable () -> Unit = {
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (lab.tabsInDock) LabSegments(lab.tabs, state.tab) { state = state.copy(tab = it) }
            lab.param?.let { p ->
                LabParamStepper(LabParam(p.name, p.name, p.symbol, p.format(p.values[state.p]), state.p > 0, state.p < p.values.lastIndex)) { d ->
                    state = state.copy(p = (state.p + d).coerceIn(0, p.values.lastIndex))
                }
            }
            lab.action?.let { label ->
                LabButton(label(state), primary = true, modifier = Modifier.fillMaxWidth()) { state = lab.onAction(state) }
            }
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        if (lab.tabs.isNotEmpty() && !lab.tabsInDock) {
            LabSegments(lab.tabs, state.tab, Modifier.padding(bottom = 14.dp)) { state = state.copy(tab = it) }
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                when (val scene = frame.scene) {
                    is MtHistScene -> HistView(scene)
                    is MtStripScene -> StripView(scene)
                    is MtCurveScene -> MetricCurveView(scene)
                    is MtFBars -> MtFBarsView(scene)
                }
                frame.matrix?.let { MatrixView(it, Modifier.padding(top = 14.dp)) }
                frame.pair?.let { PairRow(it, Modifier.padding(top = 12.dp)) }
                if (frame.formula.isNotEmpty()) MetricFormula(frame.formula, Modifier.padding(top = 12.dp))
                val accent = MaterialTheme.colorScheme.primary
                StoryLegendRow(frame.legend.map { Triple(if (it.first == Color.Unspecified) accent else it.first, it.second, it.third) }, Modifier.padding(top = 14.dp))
            }
        }
        LabChips(frame.chips, Modifier.padding(top = 16.dp))
        LabStoryNarration(frame.headline, frame.body, Modifier.padding(top = 16.dp))
        if (dock == null) {
            HorizontalDivider(modifier = Modifier.padding(top = 16.dp, bottom = 14.dp), color = MaterialTheme.colorScheme.outline)
            controls()
        }
    }

    if (dock != null) {
        SideEffect { dock.controls = controls }
        DisposableEffect(dock) { onDispose { dock.controls = null } }
    }
}

// ── Rendering ──

private fun Modifier.stage() = this.clip(RoundedCornerShape(12.dp)).background(Color.Black.copy(alpha = 0.16f))

@Composable
private fun MetricFormula(lines: List<String>, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
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
private fun HistView(scene: MtHistScene) {
    val measurer = rememberTextMeasurer()
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val accent = MaterialTheme.colorScheme.primary
    val bins = 20
    val counts = remember {
        (0 until bins).map { b ->
            val lo = b.toDouble() / bins
            val hi = (b + 1).toDouble() / bins
            val inBin = cases.filter { it.score >= lo && (it.score < hi || (b == bins - 1 && it.score <= hi)) }
            inBin.count { !it.positive } to inBin.count { it.positive }
        }
    }
    Canvas(modifier = Modifier.fillMaxWidth().aspectRatio(1.6f).stage()) {
        val label = TextStyle(fontFamily = IBMPlexMono, fontSize = 11.sp, color = muted)
        val left = 34.dp.toPx()
        val right = size.width - 12.dp.toPx()
        val top = 12.dp.toPx()
        val bottom = size.height - 24.dp.toPx()
        val peak = sqrt(counts.maxOf { max(it.first, it.second) }.toDouble())
        val slot = (right - left) / bins
        val bw = slot * 0.36f
        counts.forEachIndexed { b, (neg, pos) ->
            val x = left + b * slot
            val above = (b + 0.5) / bins >= scene.t
            fun bar(n: Int, dx: Float, color: Color) {
                if (n == 0) return
                val h = (sqrt(n.toDouble()) / peak * (bottom - top)).toFloat()
                drawRect(color, Offset(x + dx, bottom - h), Size(bw, h))
            }
            val negColor = if (!scene.byOutcome) NegC else if (above) SimColors.Red else SimColors.Green
            val posColor = if (!scene.byOutcome) PosC else if (above) SimColors.Green else SimColors.Red
            bar(neg, slot * 0.1f, negColor)
            bar(pos, slot * 0.1f + bw, posColor)
        }
        val tx = left + scene.t.toFloat() * (right - left)
        drawLine(accent, Offset(tx, top), Offset(tx, bottom), strokeWidth = 3.dp.toPx())
        fun text(s: String, at: Offset) {
            val l = measurer.measure(s, label)
            drawText(l, topLeft = Offset(at.x - l.size.width / 2f, at.y - l.size.height / 2f))
        }
        text("0", Offset(left - 12.dp.toPx(), bottom))
        text("0", Offset(left, bottom + 13.dp.toPx()))
        text("1", Offset(right, bottom + 13.dp.toPx()))
        text("score", Offset((left + right) / 2, bottom + 13.dp.toPx()))
        rotate(-90f, Offset(12.dp.toPx(), (top + bottom) / 2)) { text("√count", Offset(12.dp.toPx(), (top + bottom) / 2)) }
    }
}

@Composable
private fun StripView(scene: MtStripScene) {
    val measurer = rememberTextMeasurer()
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val accent = MaterialTheme.colorScheme.primary
    val surface = MaterialTheme.colorScheme.surface
    val outline = MaterialTheme.colorScheme.outline
    Canvas(modifier = Modifier.fillMaxWidth().aspectRatio(1.75f).stage()) {
        val left = 22.dp.toPx()
        val right = size.width - 22.dp.toPx()
        fun px(s: Double) = left + s.toFloat() * (right - left)
        val rowNeg = size.height * 0.34f
        val rowPos = size.height * 0.64f
        val tx = px(scene.t)
        drawRect(PosC.copy(alpha = 0.14f), Offset(tx, 0f), Size(size.width - tx, size.height))
        val label = TextStyle(fontSize = 12.sp, color = muted)
        val mono = TextStyle(fontFamily = IBMPlexMono, fontSize = 11.sp, color = muted)
        drawText(measurer.measure("Negative", label), topLeft = Offset(left, rowNeg - 30.dp.toPx()))
        drawText(measurer.measure("Positive", label), topLeft = Offset(left, rowPos - 30.dp.toPx()))
        drawLine(outline, Offset(left, rowNeg), Offset(right, rowNeg), strokeWidth = 1.dp.toPx())
        drawLine(outline, Offset(left, rowPos), Offset(right, rowPos), strokeWidth = 1.dp.toPx())
        drawLine(accent, Offset(tx, 0f), Offset(tx, size.height), strokeWidth = 3.dp.toPx())
        val flag = measurer.measure("flag ≥ ${mx(scene.t)}", TextStyle(fontFamily = IBMPlexMono, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = StoryTone.Answer.let { Color(0xFFB4A2FF) }))
        drawText(flag, topLeft = Offset(min(tx + 6.dp.toPx(), size.width - flag.size.width - 4.dp.toPx()), 6.dp.toPx()))
        val r = 6.dp.toPx()
        stripCases.forEach { c ->
            val center = Offset(px(c.score), if (c.positive) rowPos else rowNeg)
            drawCircle(if (c.positive) PosC else NegC, r, center)
            drawCircle(surface, r, center, style = Stroke(width = 1.dp.toPx()))
            if ((c.score >= scene.t) != c.positive) drawCircle(SimColors.Red, r + 4.dp.toPx(), center, style = Stroke(width = 2.dp.toPx()))
        }
        listOf(0.0 to "0", 0.5 to "0.5", 1.0 to "1").forEach { (v, s) ->
            val l = measurer.measure(s, mono)
            drawText(l, topLeft = Offset(px(v) - l.size.width / 2f, size.height - l.size.height - 6.dp.toPx()))
        }
        val title = measurer.measure("model score", mono)
        drawText(title, topLeft = Offset(px(0.78) - title.size.width / 2f, size.height - title.size.height - 6.dp.toPx()))
    }
}

@Composable
private fun MatrixView(c: MtCounts, modifier: Modifier) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Box(Modifier.width(76.dp))
            Text("Predicted +", fontSize = 14.sp, color = muted, modifier = Modifier.weight(1f).padding(start = 4.dp))
            Text("Predicted −", fontSize = 14.sp, color = muted, modifier = Modifier.weight(1f).padding(start = 8.dp))
        }
        listOf("Actual +" to listOf("TP" to c.tp, "FN" to c.fn), "Actual −" to listOf("FP" to c.fp, "TN" to c.tn)).forEach { (label, cells) ->
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(label, fontSize = 14.sp, color = muted, modifier = Modifier.width(76.dp))
                Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    cells.forEach { (key, n) ->
                        val good = key == "TP" || key == "TN"
                        val tone = if (good) StoryTone.Done else StoryTone.Warn
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .background((if (good) SimColors.Green else SimColors.Red).copy(alpha = 0.18f), RoundedCornerShape(10.dp))
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(key, fontFamily = IBMPlexMono, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = tone.ink(), modifier = Modifier.weight(1f))
                            Text("$n", fontFamily = IBMPlexMono, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PairRow(pair: Pair<Double, Double>, modifier: Modifier) {
    val right = pair.first > pair.second
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Row(
            modifier = Modifier.weight(1f).height(38.dp).background(PosC.copy(alpha = 0.2f), RoundedCornerShape(10.dp)).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Text("positive ", fontFamily = IBMPlexMono, fontSize = 15.sp, color = PinkInk)
            Text(mx(pair.first, 3), fontFamily = IBMPlexMono, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
        Text(
            if (right) ">" else "<",
            fontFamily = IBMPlexMono,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = if (right) StoryTone.Done.ink() else StoryTone.Warn.ink(),
            modifier = Modifier.padding(horizontal = 10.dp),
        )
        Row(
            modifier = Modifier.weight(1f).height(38.dp).background(NegC.copy(alpha = 0.2f), RoundedCornerShape(10.dp)).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Text("negative ", fontFamily = IBMPlexMono, fontSize = 15.sp, color = StoryTone.Path.ink())
            Text(mx(pair.second, 3), fontFamily = IBMPlexMono, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun MetricCurveView(scene: MtCurveScene) {
    val measurer = rememberTextMeasurer()
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val accent = MaterialTheme.colorScheme.primary
    val outline = MaterialTheme.colorScheme.outline
    val surface = MaterialTheme.colorScheme.surface
    Canvas(modifier = Modifier.fillMaxWidth().aspectRatio(1.6f).stage()) {
        val left = (if (scene.yTitle != null) 44 else 30).dp.toPx()
        val right = size.width - 14.dp.toPx()
        val top = 14.dp.toPx()
        val bottom = size.height - 26.dp.toPx()
        fun px(x: Double) = left + x.toFloat() * (right - left)
        fun py(y: Double) = bottom - (y.coerceIn(0.0, scene.yMax) / scene.yMax).toFloat() * (bottom - top)
        drawLine(outline, Offset(left, top), Offset(left, bottom), strokeWidth = 1.dp.toPx())
        drawLine(outline, Offset(left, bottom), Offset(right, bottom), strokeWidth = 1.dp.toPx())
        scene.fillUnder?.let { s ->
            val path = Path()
            path.moveTo(px(s.points.first().first), bottom)
            s.points.forEach { (x, y) -> path.lineTo(px(x), py(y)) }
            path.lineTo(px(s.points.last().first), bottom)
            path.close()
            drawPath(path, NegC.copy(alpha = 0.2f))
        }
        scene.vline?.let { drawLine(SimColors.Active, Offset(px(it), top), Offset(px(it), bottom), strokeWidth = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx()))) }
        scene.series.forEach { s ->
            if (s.points.size < 2) return@forEach
            val path = Path()
            s.points.forEachIndexed { i, (x, y) -> if (i == 0) path.moveTo(px(x), py(y)) else path.lineTo(px(x), py(y)) }
            val color = (s.color ?: accent).let { if (s.faint) it.copy(alpha = 0.3f) else it }
            drawPath(
                path,
                color,
                style = Stroke(width = (if (s.faint) 1.5f else s.width).dp.toPx(), pathEffect = if (s.dashed) PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx())) else null),
            )
        }
        scene.markers.forEach { m ->
            val c = Offset(px(m.x), py(m.y))
            drawCircle(m.color, 6.dp.toPx(), c)
            drawCircle(surface, 6.dp.toPx(), c, style = Stroke(width = 1.5.dp.toPx()))
        }
        val label = TextStyle(fontFamily = IBMPlexMono, fontSize = 11.sp, color = muted)
        fun text(s: String, at: Offset) {
            val l = measurer.measure(s, label)
            drawText(l, topLeft = Offset(at.x - l.size.width / 2f, at.y - l.size.height / 2f))
        }
        text(scene.yTop, Offset(left - 10.dp.toPx(), top))
        text("0", Offset(left - 10.dp.toPx(), bottom))
        text("0", Offset(left, bottom + 13.dp.toPx()))
        text("1", Offset(right, bottom + 13.dp.toPx()))
        text(scene.xTitle, Offset((left + right) / 2, bottom + 13.dp.toPx()))
        scene.yTitle?.let { t -> rotate(-90f, Offset(14.dp.toPx(), (top + bottom) / 2)) { text(t, Offset(14.dp.toPx(), (top + bottom) / 2)) } }
    }
}

@Composable
private fun MtFBarsView(scene: MtFBars) {
    val names = listOf("F0.5", "F1", "F2")
    val accent = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(modifier = Modifier.fillMaxWidth().height(150.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Bottom) {
        scene.values.forEachIndexed { i, v ->
            val on = i == scene.selected
            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(mx(v, 3), fontFamily = IBMPlexMono, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = if (on) Color(0xFFB4A2FF) else MaterialTheme.colorScheme.onSurface)
                Box(
                    Modifier
                        .padding(top = 6.dp)
                        .fillMaxWidth()
                        .height((90f * v.toFloat()).coerceAtLeast(4f).dp)
                        .background(if (on) accent else SimColors.Tint.copy(alpha = 1f), RoundedCornerShape(10.dp)),
                )
                Text(names[i], fontFamily = IBMPlexMono, fontSize = 13.sp, color = if (on) Color(0xFFB4A2FF) else muted, modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
}
