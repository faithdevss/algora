package com.algora.app.feature.topics

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.algora.app.core.ui.theme.CategoryAccents
import com.algora.app.core.ui.theme.IBMPlexMono
import com.algora.app.core.ui.theme.SimColors
import kotlin.math.roundToInt

// AND / OR / XOR tabs, a stage with the four inputs on the plane and the half-plane w₁·x₁ + w₂·x₂ + b ≥ 0
// shaded, a truth-table row, chips and a story headline, then a w₁ / w₂ / b picker with the value's
// stepper and Train Step. Docked (sim-only screen) the controls pin to the bottom in thumb reach.

private class Gate(val label: String, val targets: List<Int>)

private val gates = listOf(
    Gate("AND", listOf(0, 0, 0, 1)),
    Gate("OR", listOf(0, 1, 1, 1)),
    Gate("XOR", listOf(0, 1, 1, 0)),
)

private val inputs = listOf(0 to 0, 0 to 1, 1 to 0, 1 to 1)

private val OneColor = CategoryAccents.Pink
private val ZeroColor = SimColors.Blue

// Wide enough that the (x,y) labels clear the edges.
private const val AXIS_LO = -0.36f
private const val AXIS_HI = 1.36f

private fun predict(w1: Float, w2: Float, b: Float, x: Int, y: Int): Int =
    if (w1 * x + w2 * y + b >= 0f) 1 else 0

/** Values snap to one decimal, matching their readout. */
private fun snap(v: Float): Float = (v * 10f).roundToInt() / 10f

private val startWeights = floatArrayOf(1f, 1f, -1.5f)
private val symbols = listOf("w₁", "w₂", "b")

/** One perceptron-rule update on the first input it gets wrong: w += (target − output)·x, b += (target − output). */
private fun trainStep(w: FloatArray, gate: Gate): FloatArray {
    val i = inputs.indices.firstOrNull { predict(w[0], w[1], w[2], inputs[it].first, inputs[it].second) != gate.targets[it] } ?: return w
    val (x, y) = inputs[i]
    val e = gate.targets[i] - predict(w[0], w[1], w[2], x, y)
    return floatArrayOf(
        snap((w[0] + 0.5f * e * x).coerceIn(-3f, 3f)),
        snap((w[1] + 0.5f * e * y).coerceIn(-3f, 3f)),
        snap((w[2] + 0.5f * e).coerceIn(-3f, 3f)),
    )
}

private fun pointName(i: Int) = "(${inputs[i].first},${inputs[i].second})"

private fun joined(names: List<String>) = when (names.size) {
    0 -> ""
    1 -> names[0]
    else -> names.dropLast(1).joinToString(", ") + " and " + names.last()
}

private fun perceptronStory(gate: Gate, outputs: List<Int>): Pair<String, String> {
    val wrong = inputs.indices.filter { outputs[it] != gate.targets[it] }
    val correct = 4 - wrong.size
    val xorBody = "No values of w₁, w₂ and b fix that, because one line cannot split XOR. A hidden layer can."
    if (wrong.isEmpty()) {
        return "{m:All 4 correct.} One straight line separates ${gate.label}." to
            "The shaded side outputs 1. Try XOR next: Train Step will never settle there."
    }
    if (wrong.size == 1) {
        val i = wrong[0]
        val with = inputs.indices.filter { it != i && outputs[it] == outputs[i] }.map(::pointName)
        val wants = inputs.indices.filter { it != i && gate.targets[it] == gate.targets[i] }.map(::pointName)
        val headline = if (with.isNotEmpty() && wants.isNotEmpty()) {
            "$correct of 4. {w:${pointName(i)}} lands with ${joined(with)}, but ${gate.label} wants it with ${joined(wants)}."
        } else {
            "$correct of 4. {w:${pointName(i)}} lands on the ${outputs[i]} side, but ${gate.label} wants ${gate.targets[i]}."
        }
        return headline to if (gate.label == "XOR") xorBody else "Train Step moves the line toward it: w += (target − output)·x."
    }
    return "$correct of 4. {w:${wrong.size} inputs} are on the wrong side." to
        if (gate.label == "XOR") xorBody else "Train Step fixes them one at a time: w += (target − output)·x."
}

@Composable
fun PerceptronSimulationSection() {
    var gateIndex by remember { mutableIntStateOf(0) }
    val weights = remember { startWeights.map { mutableFloatStateOf(it) } }
    var selected by remember { mutableIntStateOf(2) }
    val w = floatArrayOf(weights[0].floatValue, weights[1].floatValue, weights[2].floatValue)

    val gate = gates[gateIndex]
    val outputs = inputs.map { (x, y) -> predict(w[0], w[1], w[2], x, y) }
    val correct = outputs.indices.count { outputs[it] == gate.targets[it] }
    // The chip reads the score at the input in question: the first one wrong, else (1,1).
    val focus = outputs.indices.firstOrNull { outputs[it] != gate.targets[it] } ?: 3
    val focusScore = w[0] * inputs[focus].first + w[1] * inputs[focus].second + w[2]
    val (headline, body) = perceptronStory(gate, outputs)
    val chips = listOf(
        if (correct == 4) LabChip("correct", "4 / 4", good = true) else LabChip("correct", "$correct / 4", tint = StoryTone.Warn),
        LabChip("w·x+b at ${pointName(focus)}", "%.1f".format(focusScore)),
    )

    val dock = LocalLabDock.current
    LabIntro(
        "A perceptron draws one straight line, w₁·x₁ + w₂·x₂ + b = 0, and outputs 1 on the shaded side. " +
            "Move the weights and bias until every input lands on the side its gate asks for.",
    )

    val controls: @Composable () -> Unit = {
        LabParamActionControls(
            params = listOf("Weight", "Weight", "Bias").mapIndexed { i, name ->
                val v = weights[i].floatValue
                LabParam("$name ${symbols[i]}", name, symbols[i], "%.1f".format(v), v > -3f + 1e-4f, v < 3f - 1e-4f)
            },
            selected = selected,
            onSelect = { selected = it },
            onStep = { i, delta -> weights[i].floatValue = snap((weights[i].floatValue + delta * 0.1f).coerceIn(-3f, 3f)) },
            action = "Train Step",
            onAction = {
                val next = trainStep(w, gate)
                weights.forEachIndexed { i, s -> s.floatValue = next[i] }
            },
        )
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        LabSegments(gates.map { it.label }, gateIndex) { gateIndex = it }
        Surface(
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                GatePlane(gate, w[0], w[1], w[2], wrong = outputs.indices.filter { outputs[it] != gate.targets[it] }.toSet())
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    inputs.forEachIndexed { i, (x, y) ->
                        TruthCell("$x $y →", outputs[i], outputs[i] == gate.targets[i], Modifier.weight(1f))
                    }
                }
            }
        }
        LabChips(chips, Modifier.padding(top = 16.dp))
        LabStoryNarration(headline, body, Modifier.padding(top = 16.dp))
        if (dock == null) {
            HorizontalDivider(modifier = Modifier.padding(top = 16.dp, bottom = 14.dp), color = MaterialTheme.colorScheme.outline)
            controls()
        }
    }

    if (dock != null) {
        // Re-handed every composition: the controls close over this composition's values.
        SideEffect { dock.controls = controls }
        DisposableEffect(dock) { onDispose { dock.controls = null } }
    }
}

@Composable
private fun GatePlane(gate: Gate, w1: Float, w2: Float, bias: Float, wrong: Set<Int>) {
    val accent = MaterialTheme.colorScheme.primary
    val axisColor = MaterialTheme.colorScheme.outline
    val ring = MaterialTheme.colorScheme.surface
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontFamily = IBMPlexMono, fontSize = 11.sp, color = labelColor)

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.65f)
            .clip(RoundedCornerShape(14.dp)),
    ) {
        val span = AXIS_HI - AXIS_LO
        fun px(x: Float) = (x - AXIS_LO) / span * size.width
        fun py(y: Float) = size.height - (y - AXIS_LO) / span * size.height

        // The whole plane is the 0 side; the half-plane where the sum is ≥ 0 is repainted as the 1 side.
        drawRect(ZeroColor.copy(alpha = 0.16f))
        val plane = linearHalfPlane(w1, w2, bias, AXIS_LO, AXIS_HI, AXIS_LO, AXIS_HI)
        plane.region?.let { drawPath(it, OneColor.copy(alpha = 0.16f)) }

        drawLine(axisColor, Offset(px(0f), 0f), Offset(px(0f), size.height), strokeWidth = 1.dp.toPx())
        drawLine(axisColor, Offset(0f, py(0f)), Offset(size.width, py(0f)), strokeWidth = 1.dp.toPx())

        plane.line?.let { (a, b) -> drawLine(accent, a, b, strokeWidth = 3.dp.toPx()) }

        val r = 11.dp.toPx()
        inputs.forEachIndexed { i, (x, y) ->
            val c = Offset(px(x.toFloat()), py(y.toFloat()))
            drawCircle(if (gate.targets[i] == 1) OneColor else ZeroColor, radius = r, center = c)
            drawCircle(ring, radius = r, center = c, style = Stroke(width = 2.dp.toPx()))
            if (i in wrong) drawCircle(SimColors.Red, radius = r + 5.dp.toPx(), center = c, style = Stroke(width = 2.5.dp.toPx()))
            val text = measurer.measure("($x,$y)", labelStyle)
            val gap = r + 5.dp.toPx()
            val top = if (y == 1) c.y - gap - text.size.height else c.y + gap
            drawText(text, topLeft = Offset(c.x - text.size.width / 2f, top))
        }
    }
}

@Composable
private fun TruthCell(inputLabel: String, output: Int, ok: Boolean, modifier: Modifier) {
    Column(
        modifier = modifier
            .background(if (ok) SimColors.Tint else SimColors.Red.copy(alpha = 0.18f), RoundedCornerShape(10.dp))
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(inputLabel, fontFamily = IBMPlexMono, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            if (ok) "$output ✓" else "$output ✗",
            fontFamily = IBMPlexMono,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (ok) StoryTone.Done.ink() else StoryTone.Warn.ink(),
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
