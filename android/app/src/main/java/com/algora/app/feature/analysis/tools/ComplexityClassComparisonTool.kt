package com.algora.app.feature.analysis.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.algora.app.feature.analysis.tools.common.AnalysisToolCard
import com.algora.app.feature.analysis.tools.common.MiniLineChart
import com.algora.app.feature.analysis.tools.common.complexityCurves
import kotlin.math.roundToInt

@Composable
fun ComplexityClassComparisonTool() {
    var n by remember { mutableStateOf(20f) }
    val nInt = n.toInt().coerceAtLeast(1)
    // Which classes are currently plotted — toggle any on or off.
    val shown = remember {
        mutableStateMapOf<String, Boolean>().apply {
            complexityCurves.forEach { put(it.label, true) }
        }
    }
    val visible = complexityCurves.filter { shown[it.label] == true }

    AnalysisToolCard(
        intro = "Toggle complexity classes on and off to compare them directly. Each row shows the " +
            "operation count at the current n; check a class to overlay its curve on the chart.",
    ) {
        Column(modifier = Modifier.padding(top = 14.dp, bottom = 6.dp)) {
            Text(
                "n = $nInt",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Slider(value = n, onValueChange = { n = it }, valueRange = 5f..100f, steps = 18)
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
                .padding(6.dp),
        ) {
            MiniLineChart(curves = visible, maxN = nInt, logScale = true)
        }

        Column(modifier = Modifier.padding(top = 10.dp)) {
            complexityCurves.forEach { curve ->
                val checked = shown[curve.label] == true
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = checked, onCheckedChange = { shown[curve.label] = it })
                    Box(modifier = Modifier.size(10.dp).background(curve.color, CircleShape))
                    Text(
                        curve.label,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = 8.dp).weight(1f),
                    )
                    Text(
                        "${curve.fn(nInt.toDouble()).roundToInt()} ops",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}
