package com.algora.app.core.data.model

data class TopicContent(
    val topicId: String,
    val whatIsIt: List<String>,
    val steps: List<StepCard>,
    val formulas: List<FormulaEntry>,
    val notationKey: List<NotationEntry>,
    val codeBlocks: List<CodeBlock>,
    val simulation: SimulationType,
    val applications: List<ApplicationCard>,
    val takeaways: List<String>,
    val crossLinks: List<CrossLink> = emptyList(),
)

// A pointer from one topic's detail page to a related topic (possibly in the other mode),
// rendered as a tappable chip under Key Takeaways. See Phase 5 (DSA ↔ AI cross-links).
data class CrossLink(
    val topicId: String,
    val label: String,
)

data class StepCard(
    val number: Int,
    val title: String,
    val body: String,
    val accentColor: Long,
)

data class FormulaEntry(
    val label: String,
    val formula: String,
    val note: String,
)

data class NotationEntry(
    val symbol: String,
    val meaning: String,
)

data class CodeBlock(
    val title: String,
    val accentColor: Long,
    val code: String,
    // When non-empty, the deep-dive shows a language toggle over these instead of the single `code`
    // (Phase 7 multi-language snippets). `code` stays the Kotlin/default fallback.
    val variants: List<CodeVariant> = emptyList(),
)

data class CodeVariant(
    val language: String,
    val code: String,
)

data class ApplicationCard(
    val iconName: String,
    val accentColor: Long,
    val title: String,
    val body: String,
)

sealed interface SimulationType {
    data object ArrayVisualizer : SimulationType
    data object LinkedListVisualizer : SimulationType
    data object StackVisualizer : SimulationType
    data object QueueVisualizer : SimulationType
    data object GraphVisualizer : SimulationType
    data object GraphAlgorithmPlayer : SimulationType
    data object ArrayWalkPlayer : SimulationType
    data object PointCloudPlayer : SimulationType
    data object TokenStripPlayer : SimulationType
    data object NeuralNetPlayer : SimulationType
    data object RlTrainingPlayer : SimulationType
    data object PolicyGradientPlayer : SimulationType
    data object GameSearchPlayer : SimulationType
    data object OfflineRlPlayer : SimulationType
    data object MultiAgentPlayer : SimulationType
    data object ExplorationPlayer : SimulationType
    data object LinkedStructurePlayer : SimulationType
    data object EnvironmentPlayer : SimulationType
    data object RegressionExplorer : SimulationType

    // The mock's hand-tuned regression lab is RegressionExplorer and stays bound to Linear
    // Regression alone. RegressionLab is the config-driven version behind the other Regression
    // topics — same chrome, a real estimator per topic.
    data object RegressionLab : SimulationType

    // ClassifierPlayground's model is a straight line, so it cannot show a kernel boundary, a
    // conic, class-conditional ellipses or support vectors. DecisionSurface renders an arbitrary
    // decision function by sampling it on a grid.
    data object DecisionSurface : SimulationType
    data object PerceptronVisualizer : SimulationType
    data object ClassifierPlayground : SimulationType
    data object RecursionTreeVisualizer : SimulationType
    data object DpGridVisualizer : SimulationType
    data object SortingVisualizer : SimulationType
    data object SearchVisualizer : SimulationType
    data object TreeVisualizer : SimulationType
    data object PathfindingGrid : SimulationType
    data object HashingVisualizer : SimulationType
    data object RlGridWorld : SimulationType
    data object BanditExplorer : SimulationType
    data object NotYetAvailable : SimulationType
}
