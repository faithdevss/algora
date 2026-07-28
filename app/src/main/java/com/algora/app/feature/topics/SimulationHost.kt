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

// The single place that maps a SimulationType to its lab composable. Both the topic detail page's
// "Interactive Simulation" section and the sim-only screen (feature/simulations) render through here,
// so a new lab is wired once.
@Composable
internal fun SimulationHost(topicId: String, type: SimulationType) {
    when (type) {
        SimulationType.ArrayVisualizer -> ArraySimulationSection()
        SimulationType.LinkedListVisualizer -> LinkedListSimulationSection()
        SimulationType.StackVisualizer -> StackSimulationSection()
        SimulationType.QueueVisualizer -> QueueSimulationSection()
        SimulationType.GraphVisualizer -> GraphSimulationSection()
        SimulationType.GraphAlgorithmPlayer -> GraphAlgorithmSection(topicId)
        SimulationType.ArrayWalkPlayer -> ArrayWalkSection(topicId)
        SimulationType.PointCloudPlayer -> PointCloudSection(topicId)
        SimulationType.TokenStripPlayer -> TokenStripSection(topicId)
        SimulationType.NeuralNetPlayer -> NeuralNetSection(topicId)
        SimulationType.RlTrainingPlayer -> RlTrainingSection(topicId)
        SimulationType.PolicyGradientPlayer -> PolicyGradientSection(topicId)
        SimulationType.GameSearchPlayer -> GameSearchSection(topicId)
        SimulationType.OfflineRlPlayer -> OfflineRlSection(topicId)
        SimulationType.MultiAgentPlayer -> MultiAgentSection(topicId)
        SimulationType.ExplorationPlayer -> ExplorationSection(topicId)
        SimulationType.LinkedStructurePlayer -> LinkedStructureSection(topicId)
        SimulationType.EnvironmentPlayer -> EnvironmentSection(topicId)
        SimulationType.RegressionExplorer -> RegressionSimulationSection()
        SimulationType.RegressionLab -> RegressionLabSection(topicId)
        SimulationType.DecisionSurface -> DecisionSurfaceSection(topicId)
        SimulationType.BitBoardPlayer -> BitBoardSection(topicId)
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
