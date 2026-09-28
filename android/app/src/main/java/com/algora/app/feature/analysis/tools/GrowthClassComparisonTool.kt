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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.algora.app.core.ui.theme.SimColors
import com.algora.app.feature.analysis.tools.common.AnalysisToolCard
import com.algora.app.feature.analysis.tools.common.MiniLineChart
import com.algora.app.feature.analysis.tools.common.complexityCurves
import kotlin.math.roundToInt

@Composable
fun GrowthClassComparisonTool() {
    var maxN by remember { mutableStateOf(50f) }
    var logScale by remember { mutableStateOf(true) }
    val maxNInt = maxN.toInt().coerceAtLeast(1)

    AnalysisToolCard(
        intro = "The same five growth classes on one chart. Toggle the scale: log spreads all curves " +
            "apart so their shapes are readable; linear shows their true relative magnitude, where " +
            "O(n²) dwarfs the rest.",
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(top = 14.dp)) {
            val logSelected = logScale
            if (logSelected) {
                Button(
                    onClick = { logScale = true },
                    colors = ButtonDefaults.buttonColors(containerColor = SimColors.Blue, contentColor = Color.White),
                    modifier = Modifier.weight(1f),
                ) { Text("Log scale") }
            } else {
                OutlinedButton(onClick = { logScale = true }, modifier = Modifier.weight(1f)) { Text("Log scale") }
            }
            Box(modifier = Modifier.padding(start = 8.dp))
            if (!logSelected) {
                Button(
                    onClick = { logScale = false },
                    colors = ButtonDefaults.buttonColors(containerColor = SimColors.Blue, contentColor = Color.White),
                    modifier = Modifier.weight(1f),
                ) { Text("Linear scale") }
            } else {
                OutlinedButton(onClick = { logScale = false }, modifier = Modifier.weight(1f)) { Text("Linear scale") }
            }
        }

        Column(modifier = Modifier.padding(top = 16.dp, bottom = 6.dp)) {
            Text(
                "Max n = $maxNInt",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Slider(value = maxN, onValueChange = { maxN = it }, valueRange = 10f..100f, steps = 8)
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
                .padding(6.dp),
        ) {
            MiniLineChart(curves = complexityCurves, maxN = maxNInt, logScale = logScale)
        }

        Column(modifier = Modifier.padding(top = 14.dp)) {
            complexityCurves.forEach { curve ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(modifier = Modifier.size(10.dp).background(curve.color, CircleShape))
                    Text(
                        curve.label,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = 8.dp).weight(1f),
                    )
                    Text(
                        "≈ ${curve.fn(maxNInt.toDouble()).roundToInt()} ops",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
