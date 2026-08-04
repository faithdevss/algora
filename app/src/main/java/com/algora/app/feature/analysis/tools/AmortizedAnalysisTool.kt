package com.algora.app.feature.analysis.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.algora.app.core.ui.theme.SimColors
import com.algora.app.feature.analysis.tools.common.AnalysisToolCard
import com.algora.app.feature.analysis.tools.common.Bar
import com.algora.app.feature.analysis.tools.common.InstrumentedAlgos
import com.algora.app.feature.analysis.tools.common.MiniBarChart
import java.util.Locale

@Composable
fun AmortizedAnalysisTool() {
    var pushes by remember { mutableStateOf(16f) }
    val pushesInt = pushes.toInt().coerceAtLeast(1)

    val costs = InstrumentedAlgos.dynamicArrayPushCosts(pushesInt)
    val total = costs.sum()
    val amortized = total.toDouble() / pushesInt

    AnalysisToolCard(
        intro = "A dynamic array doubles its backing store when full, copying existing elements. Most " +
            "pushes cost 1; a resize spikes. Averaged over the whole sequence, the cost per push stays " +
            "constant — that's amortized O(1).",
    ) {
        Column(modifier = Modifier.padding(top = 14.dp, bottom = 6.dp)) {
            Text(
                "Pushes = $pushesInt",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Slider(value = pushes, onValueChange = { pushes = it }, valueRange = 1f..32f, steps = 30)
        }

        // Per-push cost. Tall bars are the resize copies (at capacities 1, 2, 4, 8, …).
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
                .padding(6.dp),
        ) {
            MiniBarChart(
                bars = costs.mapIndexed { i, c ->
                    Bar((i + 1).toString(), c.toDouble(), if (c > 1) SimColors.Red else SimColors.Green)
                },
            )
        }

        Row(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
            StatBlock(label = "Total cost", value = total.toString(), modifier = Modifier.weight(1f))
            StatBlock(
                label = "Amortized / push",
                value = String.format(Locale.US, "%.2f", amortized),
                modifier = Modifier.weight(1f),
            )
        }
        Text(
            "Worst single push here: ${costs.max()} · yet the average stays near a small constant as pushes grow.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 10.dp),
        )
    }
}

@Composable
private fun StatBlock(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
