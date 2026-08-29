package com.algora.app.feature.analysis.tools

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.algora.app.core.ui.theme.SimColors
import com.algora.app.feature.analysis.tools.common.AnalysisToolCard

private data class Band(val label: String, val color: Color, val algos: List<String>)

// Green (efficient) → red (intractable), so the map reads as a difficulty gradient top to bottom.
private val bands = listOf(
    Band("O(1)", Color(0xFF10B981), listOf("Array access", "Hash lookup", "Stack push")),
    Band("O(log n)", Color(0xFF22C55E), listOf("Binary search", "BST insert", "Heap push")),
    Band("O(n)", SimColors.Amber, listOf("Linear search", "Array scan", "BFS / DFS")),
    Band("O(n log n)", Color(0xFFF97316), listOf("Merge sort", "Heap sort", "Quicksort avg")),
    Band("O(n²)", SimColors.Red, listOf("Bubble sort", "Insertion sort", "Selection sort")),
    Band("O(2ⁿ)", Color(0xFFB91C1C), listOf("Naive Fibonacci", "Subset gen", "TSP brute force")),
)

@Composable
fun ComplexityMapTool() {
    AnalysisToolCard(
        intro = "Where common algorithms land on the complexity spectrum — efficient classes in " +
            "green at the top, intractable ones in red at the bottom.",
    ) {
        Column(modifier = Modifier.padding(top = 14.dp)) {
            bands.forEach { band ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(modifier = Modifier.size(10.dp).background(band.color, CircleShape))
                    Text(
                        band.label,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = band.color,
                        modifier = Modifier.padding(start = 8.dp).width(96.dp),
                    )
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        band.algos.forEach { algo ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = band.color.copy(alpha = 0.12f),
                            ) {
                                Text(
                                    algo,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
