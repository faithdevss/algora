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

// Parameter lab (screen 4e): AND / OR / XOR tabs, a stage with the four inputs on the plane and the
// half-plane w₁·x₁ + w₂·x₂ + b ≥ 0 shaded, a truth-table row, a verdict, then the three sliders.
// Docked (sim-only screen) the sliders pin to the bottom in thumb reach; on the topic page they sit
// under a divider in the card.

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

/** Sliders snap to one decimal, matching their readout. */
private fun snap(v: Float): Float = (v * 10f).roundToInt() / 10f

@Composable
fun PerceptronSimulationSection() {
    var gateIndex by remember { mutableIntStateOf(0) }
    var w1 by remember { mutableFloatStateOf(1f) }
    var w2 by remember { mutableFloatStateOf(1f) }
    var bias by remember { mutableFloatStateOf(-1.5f) }

    val gate = gates[gateIndex]
    val outputs = inputs.map { (x, y) -> predict(w1, w2, bias, x, y) }
    val correct = outputs.indices.count { outputs[it] == gate.targets[it] }

    val dock = LocalLabDock.current
    LabIntro(
        "A perceptron draws one straight line, w₁·x₁ + w₂·x₂ + b = 0, and outputs 1 on the shaded side. " +
            "Move the weights and bias until every input lands on the side its gate asks for.",
    )

    val controls: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            LabParamSlider("Weight", "w₁", w1, -2f..2f) { w1 = snap(it) }
            LabParamSlider("Weight", "w₂", w2, -2f..2f) { w2 = snap(it) }
            LabParamSlider("Bias", "b", bias, -3f..3f) { bias = snap(it) }
        }
    }

    val stage: @Composable () -> Unit = {
        Column {
            GatePlane(gate, w1, w2, bias)
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

    Column(modifier = Modifier.fillMaxWidth()) {
        LabSegments(gates.map { it.label }, gateIndex) { gateIndex = it }
        Surface(
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                stage()
                if (dock == null) {
                    Verdict(gate, correct, Modifier.padding(top = 16.dp))
                    HorizontalDivider(modifier = Modifier.padding(top = 16.dp, bottom = 14.dp), color = MaterialTheme.colorScheme.outline)
                    controls()
                }
            }
        }
        if (dock != null) Verdict(gate, correct, Modifier.padding(start = 4.dp, end = 4.dp, top = 18.dp))
    }

    if (dock != null) {
        // Re-handed every composition: the controls close over this composition's slider values.
        SideEffect { dock.controls = controls }
        DisposableEffect(dock) { onDispose { dock.controls = null } }
    }
}

@Composable
private fun GatePlane(gate: Gate, w1: Float, w2: Float, bias: Float) {
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
            .background(SimColors.Tint.copy(alpha = 0.18f), RoundedCornerShape(10.dp))
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(inputLabel, fontFamily = IBMPlexMono, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            if (ok) "$output ✓" else "$output ✗",
            fontFamily = IBMPlexMono,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (ok) SimColors.Green else SimColors.Red,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun Verdict(gate: Gate, correct: Int, modifier: Modifier) {
    val solved = correct == 4
    val headline = buildAnnotatedString {
        withStyle(SpanStyle(color = if (solved) SimColors.Green else MaterialTheme.colorScheme.onSurface)) {
            append(if (solved) "All 4 correct." else "$correct of 4 correct.")
        }
        append(
            when {
                solved -> " One straight line separates ${gate.label}."
                gate.label == "XOR" -> " No straight line separates XOR."
                else -> " Move the line until every cell shows ✓."
            },
        )
    }
    val detail = when {
        gate.label == "XOR" ->
            "One perceptron tops out at 3 of 4 here. That gap is why networks add a hidden layer."
        solved -> "Try XOR next: no setting of these sliders will work."
        else -> "The shaded side outputs 1. Bias slides the line; the weights tilt it."
    }
    Column(modifier = modifier) {
        Text(headline, fontSize = 19.sp, lineHeight = 25.sp, fontWeight = FontWeight.SemiBold)
        Text(
            detail,
            fontSize = 15.sp,
            lineHeight = 21.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}
