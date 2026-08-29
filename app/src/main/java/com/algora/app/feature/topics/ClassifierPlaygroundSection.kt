package com.algora.app.feature.topics

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.algora.app.core.ui.theme.AlgoraCodeStyle
import com.algora.app.core.ui.theme.SimColors
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.random.Random

// ── Reusable 2D linear-classifier playground ────────────────────────────────
// Extracted from the original Perceptron gate solver (Phase 3 scope) so more than one topic can
// reuse it. Every variant is a linear classifier: the decision boundary is the zero-contour of
// w₁·x + w₂·y + b, params = [w₁, w₂, b]. Logistic regression (sigmoid) and SVM share that exact
// boundary; they differ only in readout (accuracy / margin), driven by config — not new geometry.

class DataPoint(val x: Float, val y: Float, val target: Int)

class ClassifierVariant(val label: String, val points: List<DataPoint>)

class SliderSpec(val label: String, val range: ClosedFloatingPointRange<Float>, val initial: Float)

class ClassifierConfig(
    val variants: List<ClassifierVariant>,      // more than one → shows a selector row
    val sliders: List<SliderSpec>,              // must be ordered [w₁, w₂, b]
    val axisRange: ClosedFloatingPointRange<Float>,
    val pointRadius: Float,
    val showTruthTable: Boolean,
    val showMargins: Boolean,
    val summary: (correct: Int, total: Int) -> String,
    val note: (variantLabel: String, correct: Int, total: Int) -> String? = { _, _, _ -> null },
    val readout: (w1: Float, w2: Float, bias: Float) -> String? = { _, _, _ -> null },
)

private val PositiveColor = SimColors.Green
private val NegativeColor = Color(0xFF6366F1)
private val BoundaryColor = SimColors.Amber

private fun predict(w1: Float, w2: Float, bias: Float, x: Float, y: Float): Int =
    if (w1 * x + w2 * y + bias >= 0f) 1 else 0

@Composable
fun ClassifierPlaygroundSection(config: ClassifierConfig) {
    var variantIndex by remember(config) { mutableStateOf(0) }
    val params = remember(config) { config.sliders.map { mutableStateOf(it.initial) } }

    val variant = config.variants[variantIndex]
    val w1 = params[0].value
    val w2 = params[1].value
    val bias = params[2].value
    val correct = variant.points.count { predict(w1, w2, bias, it.x, it.y) == it.target }
    val total = variant.points.size

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (config.variants.size > 1) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    config.variants.forEachIndexed { i, v ->
                        val selected = i == variantIndex
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .background(
                                    if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                    RoundedCornerShape(10.dp),
                                )
                                .clickable { variantIndex = i }
                                .padding(vertical = 9.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                v.label,
                                color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = if (config.variants.size > 1) 14.dp else 0.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                    .padding(10.dp),
            ) {
                ClassifierCanvas(
                    points = variant.points,
                    w1 = w1, w2 = w2, bias = bias,
                    axisRange = config.axisRange,
                    pointRadius = config.pointRadius,
                    showMargins = config.showMargins,
                )
            }

            if (config.showTruthTable) {
                Column(modifier = Modifier.padding(top = 14.dp)) {
                    Row(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
                        TableCell("x₁", weight = 1f, header = true)
                        TableCell("x₂", weight = 1f, header = true)
                        TableCell("target", weight = 1.4f, header = true)
                        TableCell("output", weight = 1.4f, header = true)
                    }
                    variant.points.forEach { p ->
                        val out = predict(w1, w2, bias, p.x, p.y)
                        val ok = out == p.target
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                            TableCell(fmt(p.x), weight = 1f)
                            TableCell(fmt(p.y), weight = 1f)
                            TableCell(p.target.toString(), weight = 1.4f)
                            TableCell(
                                if (ok) "$out ✓" else "$out ✗",
                                weight = 1.4f,
                                color = if (ok) PositiveColor else SimColors.Red,
                            )
                        }
                    }
                }
            }

            Text(
                text = config.summary(correct, total),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (correct == total) PositiveColor else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 10.dp),
            )
            config.readout(w1, w2, bias)?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            config.note(variant.label, correct, total)?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            config.sliders.forEachIndexed { i, spec ->
                WeightSlider(spec.label, params[i].value, spec.range) { params[i].value = it }
            }
        }
    }
}

private fun fmt(v: Float): String =
    if (v == v.toInt().toFloat()) v.toInt().toString() else String.format("%.2f", v)

@Composable
private fun RowScope.TableCell(
    text: String,
    weight: Float,
    header: Boolean = false,
    color: Color = MaterialTheme.colorScheme.onSurface,
) {
    Text(
        text = text,
        modifier = Modifier.weight(weight),
        style = if (header) MaterialTheme.typography.bodySmall else AlgoraCodeStyle,
        color = if (header) MaterialTheme.colorScheme.onSurfaceVariant else color,
        fontWeight = if (header) FontWeight.SemiBold else FontWeight.Medium,
    )
}

@Composable
private fun WeightSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
) {
    Column(modifier = Modifier.padding(top = 10.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(((value * 100).roundToInt() / 100f).toString(), style = AlgoraCodeStyle, color = MaterialTheme.colorScheme.primary)
        }
        Slider(value = value, onValueChange = onValueChange, valueRange = range)
    }
}

@Composable
private fun ClassifierCanvas(
    points: List<DataPoint>,
    w1: Float,
    w2: Float,
    bias: Float,
    axisRange: ClosedFloatingPointRange<Float>,
    pointRadius: Float,
    showMargins: Boolean,
) {
    val gridColor = MaterialTheme.colorScheme.outline
    Canvas(modifier = Modifier.fillMaxWidth().aspectRatio(1.4f)) {
        val lo = axisRange.start
        val hi = axisRange.endInclusive
        fun px(x: Float): Float = (x - lo) / (hi - lo) * size.width
        fun py(y: Float): Float = size.height - (y - lo) / (hi - lo) * size.height

        drawLine(gridColor, Offset(px(0f), 0f), Offset(px(0f), size.height), strokeWidth = 1.dp.toPx())
        drawLine(gridColor, Offset(0f, py(0f)), Offset(size.width, py(0f)), strokeWidth = 1.dp.toPx())

        // A line where w₁·x + w₂·y + b = level.
        fun drawBoundary(level: Float, color: Color, width: Float) {
            if (kotlin.math.abs(w2) > 1e-3f) {
                val yAtLo = (level - w1 * lo - bias) / w2
                val yAtHi = (level - w1 * hi - bias) / w2
                drawLine(color, Offset(px(lo), py(yAtLo)), Offset(px(hi), py(yAtHi)), strokeWidth = width)
            } else if (kotlin.math.abs(w1) > 1e-3f) {
                val xAt = (level - bias) / w1
                drawLine(color, Offset(px(xAt), 0f), Offset(px(xAt), size.height), strokeWidth = width)
            }
        }

        if (showMargins) {
            drawBoundary(1f, BoundaryColor.copy(alpha = 0.35f), 2f)
            drawBoundary(-1f, BoundaryColor.copy(alpha = 0.35f), 2f)
        }
        drawBoundary(0f, BoundaryColor, 5f)

        points.forEach { p ->
            val color = if (p.target == 1) PositiveColor else NegativeColor
            drawCircle(color, radius = pointRadius, center = Offset(px(p.x), py(p.y)))
        }
    }
}

// ── Configs ─────────────────────────────────────────────────────────────────

private fun gatePoints(targets: List<Int>): List<DataPoint> {
    val inputs = listOf(0 to 0, 0 to 1, 1 to 0, 1 to 1)
    return inputs.mapIndexed { i, (x1, x2) -> DataPoint(x1.toFloat(), x2.toFloat(), targets[i]) }
}

// The original Perceptron gate solver, now expressed as config. Perceptron's screen is unchanged.
val gateClassifierConfig = ClassifierConfig(
    variants = listOf(
        ClassifierVariant("AND", gatePoints(listOf(0, 0, 0, 1))),
        ClassifierVariant("OR", gatePoints(listOf(0, 1, 1, 1))),
        ClassifierVariant("XOR", gatePoints(listOf(0, 1, 1, 0))),
    ),
    sliders = listOf(
        SliderSpec("Weight w₁", -2f..2f, 1f),
        SliderSpec("Weight w₂", -2f..2f, 1f),
        SliderSpec("Bias b", -3f..3f, -1.5f),
    ),
    axisRange = -0.3f..1.3f,
    pointRadius = 12f,
    showTruthTable = true,
    showMargins = false,
    summary = { correct, _ ->
        if (correct == 4) "All 4 correct — this gate is linearly separable." else "$correct / 4 correct."
    },
    note = { label, _, _ ->
        if (label == "XOR") {
            "No single straight line can separate XOR — one perceptron will never reach 4/4. That gap is exactly why multi-layer networks were invented."
        } else {
            null
        }
    },
)

// Two gaussian blobs — a deterministic, seeded scatter for the continuous classifiers.
private fun blobs(seed: Int): List<DataPoint> {
    val rng = Random(seed)
    fun cluster(cx: Float, cy: Float, target: Int) = List(8) {
        DataPoint(cx + (rng.nextFloat() - 0.5f) * 0.9f, cy + (rng.nextFloat() - 0.5f) * 0.9f, target)
    }
    return cluster(-0.7f, -0.7f, 0) + cluster(0.7f, 0.7f, 1)
}

private val blobSliders = listOf(
    SliderSpec("Weight w₁", -3f..3f, 1f),
    SliderSpec("Weight w₂", -3f..3f, 1f),
    SliderSpec("Bias b", -3f..3f, 0f),
)

private fun accuracySummary(correct: Int, total: Int): String {
    val pct = (100f * correct / total).roundToInt()
    return "$correct / $total correctly classified ($pct%)."
}

val logisticClassifierConfig = ClassifierConfig(
    variants = listOf(ClassifierVariant("Two classes", blobs(seed = 7))),
    sliders = blobSliders,
    axisRange = -1.5f..1.5f,
    pointRadius = 9f,
    showTruthTable = false,
    showMargins = false,
    summary = ::accuracySummary,
)

val svmClassifierConfig = ClassifierConfig(
    variants = listOf(ClassifierVariant("Two classes", blobs(seed = 7))),
    sliders = blobSliders,
    axisRange = -1.5f..1.5f,
    pointRadius = 9f,
    showTruthTable = false,
    showMargins = true,
    summary = ::accuracySummary,
    readout = { w1, w2, _ ->
        val norm = sqrt(w1 * w1 + w2 * w2)
        if (norm > 1e-3f) "Margin width ≈ ${String.format("%.2f", 2f / norm)} (the amber band between the dashed lines)." else null
    },
)

// SimulationType is a param-free data object, so config is resolved by topic id (mirrors
// AnalysisToolRegistry's id→content pattern). Keeps the param-free convention the exhaustive
// `when` relies on.
fun classifierConfigFor(topicId: String): ClassifierConfig = when (topicId) {
    "svm" -> svmClassifierConfig
    "logistic_regression" -> logisticClassifierConfig
    else -> logisticClassifierConfig
}
