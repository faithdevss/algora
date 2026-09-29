package com.algora.app.feature.topics

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.algora.app.core.data.model.SimulationType

// Interview-prep pattern topics whose technique already has a redesigned algorithm lab (same example as
// the pattern's write-up) open that lab instead of their own older one.
internal val patternLabAliases: Map<String, Pair<String, SimulationType>> = mapOf(
    "knapsack_dp_pattern" to ("knapsack_01" to SimulationType.DpGridVisualizer),
    "bitmask_state_pattern" to ("bitmask_dp" to SimulationType.DpGridVisualizer),
    "tree_dp_pattern" to ("tree_dp" to SimulationType.TreeVisualizer),
    "trie_prefix_pattern" to ("trie" to SimulationType.TreeVisualizer),
    "union_find_pattern" to ("disjoint_set" to SimulationType.TreeVisualizer),
    "range_query_pattern" to ("fenwick_tree" to SimulationType.TreeVisualizer),
    "tree_bfs_pattern" to ("tree" to SimulationType.TreeVisualizer),
)

// The single place that maps a SimulationType to its lab composable. Both the topic detail page's
// "Interactive Simulation" section and the sim-only screen (feature/simulations) render through here,
// so a new lab is wired once.
@Composable
internal fun SimulationHost(topicId: String, type: SimulationType) {
    // The storyboarded string and array labs span several simulation types; they are routed first.
    if (topicId in textStoryTopicIds) return TextStorySection(topicId)
    patternLabAliases[topicId]?.let { (id, labType) -> return SimulationHost(id, labType) }
    when (type) {
        SimulationType.ArrayVisualizer -> ArraySimulationSection()
        SimulationType.LinkedListVisualizer -> LinkedListSimulationSection()
        SimulationType.StackVisualizer -> StackSimulationSection()
        SimulationType.QueueVisualizer -> QueueSimulationSection()
        SimulationType.GraphVisualizer -> GraphSimulationSection(topicId)
        SimulationType.GraphAlgorithmPlayer -> GraphAlgorithmSection(topicId)
        SimulationType.ArrayWalkPlayer -> ArrayWalkSection(topicId)
        SimulationType.PointCloudPlayer ->
            if (topicId in geometryTopicIds) GeometryLabSection(topicId) else PointCloudSection(topicId)
        // The tokenization topics have their own lab (input, split points, token ids).
        SimulationType.TokenStripPlayer ->
            if (topicId in tokenizerLabTopicIds) TokenizerLabSection(topicId) else TokenStripSection(topicId)
        SimulationType.NeuralNetPlayer -> NeuralNetSection(topicId)
        SimulationType.RlTrainingPlayer -> RlTrainingSection(topicId)
        SimulationType.PolicyGradientPlayer -> PolicyGradientSection(topicId)
        SimulationType.GameSearchPlayer -> GameSearchSection(topicId)
        SimulationType.OfflineRlPlayer -> OfflineRlSection(topicId)
        SimulationType.MultiAgentPlayer -> MultiAgentSection(topicId)
        SimulationType.ExplorationPlayer -> ExplorationSection(topicId)
        // Deque is taught as a sandbox (push/pop at either end) rather than a frame player; the doubly
        // linked list has its own tabbed storyboard.
        SimulationType.LinkedStructurePlayer ->
            when (topicId) {
                "deque" -> DequeSimulationSection()
                "doubly_linked_list" -> DoublyLinkedLabSection()
                else -> LinkedStructureSection(topicId)
            }
        SimulationType.EnvironmentPlayer -> EnvironmentSection(topicId)
        SimulationType.RegressionExplorer -> RegressionSimulationSection()
        SimulationType.RegressionLab -> RegressionLabSection(topicId)
        SimulationType.DecisionSurface -> DecisionSurfaceSection(topicId)
        SimulationType.FeatureMapPlayer -> FeatureMapSection(topicId)
        SimulationType.BitBoardPlayer ->
            if (topicId in bitStoryTopicIds) BitStorySection(topicId) else BitBoardSection(topicId)
        SimulationType.PerceptronVisualizer -> PerceptronSimulationSection()
        SimulationType.ClassifierPlayground -> ClassifierPlaygroundSection(classifierConfigFor(topicId))
        SimulationType.RecursionTreeVisualizer -> RecursionTreeSection(topicId)
        SimulationType.DpGridVisualizer -> DpGridSection(topicId)
        SimulationType.SortingVisualizer -> SortingVisualizerSection(topicId)
        SimulationType.SearchVisualizer -> SearchVisualizerSection(topicId)
        SimulationType.TreeVisualizer -> TreeVisualizerSection(topicId)
        SimulationType.PathfindingGrid -> PathfindingGridSection(topicId)
        SimulationType.HashingVisualizer -> HashingVisualizerSection(topicId)
        SimulationType.RlGridWorld -> RlGridWorldSection(topicId)
        SimulationType.BanditExplorer -> BanditSection(topicId)
        SimulationType.NotYetAvailable -> SimulationComingSoonCard()
    }
}

@Composable
internal fun SimulationComingSoonCard() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Simulation coming soon", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.size(6.dp))
            Text(
                "An interactive visualizer for this topic hasn't been built yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
