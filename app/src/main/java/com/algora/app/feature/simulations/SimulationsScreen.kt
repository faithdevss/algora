package com.algora.app.feature.simulations

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.algora.app.core.data.TopicRegistry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.Topic
import com.algora.app.core.ui.components.resolveIcon
import com.algora.app.core.ui.theme.SpaceGrotesk
import com.algora.app.feature.topics.content.TopicContentProvider

private data class SimEntry(val topic: Topic, val label: String)

private fun simLabel(type: SimulationType): String = when (type) {
    SimulationType.ArrayVisualizer -> "Array visualizer"
    SimulationType.LinkedListVisualizer -> "Linked-list visualizer"
    SimulationType.StackVisualizer -> "Stack visualizer"
    SimulationType.QueueVisualizer -> "Queue visualizer"
    SimulationType.GraphVisualizer -> "Graph builder · BFS/DFS"
    SimulationType.GraphAlgorithmPlayer -> "Graph algorithm player"
    SimulationType.ArrayWalkPlayer -> "Array walk · pointer player"
    SimulationType.PointCloudPlayer -> "2D feature space player"
    SimulationType.TokenStripPlayer -> "Token strip · attention"
    SimulationType.NeuralNetPlayer -> "Network · training player"
    SimulationType.RlTrainingPlayer -> "RL training · measured runs"
    SimulationType.PolicyGradientPlayer -> "Policy gradient · variance"
    SimulationType.GameSearchPlayer -> "Search · planning player"
    SimulationType.RegressionExplorer -> "Regression explorer"
    SimulationType.PerceptronVisualizer -> "Perceptron playground"
    SimulationType.ClassifierPlayground -> "Classifier playground"
    SimulationType.RecursionTreeVisualizer -> "Recursion tree · call stack"
    SimulationType.DpGridVisualizer -> "DP table · animated fill"
    SimulationType.SortingVisualizer -> "Sorting visualizer"
    SimulationType.SearchVisualizer -> "Search visualizer"
    SimulationType.TreeVisualizer -> "Tree visualizer"
    SimulationType.PathfindingGrid -> "Pathfinding grid"
    SimulationType.HashingVisualizer -> "Hashing · bucket visualizer"
    SimulationType.RlGridWorld -> "Grid world · RL agent"
    SimulationType.BanditExplorer -> "Multi-armed bandit"
    SimulationType.NotYetAvailable -> "Interactive lab"
}

// The Simulations tab: a catalog of every topic that ships a runnable interactive lab. Tapping a row
// opens that topic's detail page, whose Interactive Simulation section hosts the live lab.
@Composable
fun SimulationsScreen(onTopicClick: (String) -> Unit, modifier: Modifier = Modifier) {
    val entries = remember {
        TopicContentProvider.runnableSimulations.mapNotNull { (topicId, type) ->
            TopicRegistry.find(topicId)?.let { SimEntry(it, simLabel(type)) }
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
    ) {
        item {
            Column(modifier = Modifier.padding(bottom = 8.dp)) {
                Text("Simulations", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "${entries.size} interactive labs — tap to run",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }

        items(entries, key = { it.topic.id }) { entry ->
            SimulationRow(entry = entry, onClick = { onTopicClick(entry.topic.id) })
        }
    }
}

@Composable
private fun SimulationRow(entry: SimEntry, onClick: () -> Unit) {
    val accent = Color(entry.topic.accentColor)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(accent.copy(alpha = 0.14f), RoundedCornerShape(13.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(resolveIcon(entry.topic.iconName), contentDescription = null, tint = accent, modifier = Modifier.size(22.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(entry.topic.name, fontFamily = SpaceGrotesk, fontWeight = FontWeight.Bold, fontSize = 15.5.sp)
                Text(
                    entry.label,
                    fontSize = 12.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .background(accent, RoundedCornerShape(11.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = "Run", tint = Color.White, modifier = Modifier.size(20.dp))
            }
        }
    }
}
