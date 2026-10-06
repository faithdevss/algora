import SwiftUI

/// The single place that maps a SimulationType to its lab (SimulationHost.kt). Both the topic
/// page and the sim-only screen render through here, so a new lab is wired once.
/// Interview-prep pattern topics whose technique already has a redesigned algorithm lab (same example
/// as the pattern's write-up) open that lab instead of their own older one.
let patternLabAliases: [String: (topicId: String, type: SimulationType)] = [
    "knapsack_dp_pattern": ("knapsack_01", .DpGridVisualizer),
    "bitmask_state_pattern": ("bitmask_dp", .DpGridVisualizer),
    "tree_dp_pattern": ("tree_dp", .TreeVisualizer),
    "trie_prefix_pattern": ("trie", .TreeVisualizer),
    "union_find_pattern": ("disjoint_set", .TreeVisualizer),
    "range_query_pattern": ("fenwick_tree", .TreeVisualizer),
    "tree_bfs_pattern": ("tree", .TreeVisualizer),
]

struct SimulationHost: View {
    let topicId: String
    let type: SimulationType

    var body: some View {
        // The storyboarded string and array labs span several simulation types; they are routed first.
        if textStoryTopicIds.contains(topicId) { TextStoryLab(topicId: topicId).id(topicId) }
        else if neuralStoryTopicIds.contains(topicId) { NeuralStoryLab(topicId: topicId).id(topicId) }
        else if deepStoryTopicIds.contains(topicId) { DeepStoryLab(topicId: topicId).id(topicId) }
        else if mlStoryTopicIds.contains(topicId) { MlStoryLab(topicId: topicId).id(topicId) }
        else if regressionStoryTopicIds.contains(topicId) { RegressionStoryLab(topicId: topicId).id(topicId) }
        else if bayesStoryTopicIds.contains(topicId) { BayesStoryLab(topicId: topicId).id(topicId) }
        else if ensembleStoryTopicIds.contains(topicId) { EnsembleStoryLab(topicId: topicId).id(topicId) }
        else if metricStoryTopicIds.contains(topicId) { MetricStoryLab(topicId: topicId).id(topicId) }
        else if evalStoryTopicIds.contains(topicId) { EvalStoryLab(topicId: topicId).id(topicId) }
        else if rlStoryTopicIds.contains(topicId) { RlStoryLab(topicId: topicId).id(topicId) }
        else if rlBoardTopicIds.contains(topicId) { RlBoardLab(topicId: topicId).id(topicId) }
        else if preprocessStoryTopicIds.contains(topicId) { PreprocessStoryLab(topicId: topicId).id(topicId) }
        else if clusterStoryTopicIds.contains(topicId) { ClusterStoryLab(topicId: topicId).id(topicId) }
        else if dimStoryTopicIds.contains(topicId) { DimStoryLab(topicId: topicId).id(topicId) }
        else if seriesStoryTopicIds.contains(topicId) { SeriesStoryLab(topicId: topicId).id(topicId) }
        else if llmStoryTopicIds.contains(topicId) { LlmStoryLab(topicId: topicId).id(topicId) }
        else if nlpStoryTopicIds.contains(topicId) { NlpStoryLab(topicId: topicId).id(topicId) }
        else if let alias = patternLabAliases[topicId] { SimulationHost(topicId: alias.topicId, type: alias.type) }
        else { lab }
    }

    @ViewBuilder private var lab: some View {
        switch type {
        case .ArrayVisualizer: ArrayLab()
        case .LinkedListVisualizer: LinkedListLab()
        case .StackVisualizer: StackQueueLab(isStack: true)
        case .QueueVisualizer: StackQueueLab(isStack: false)
        case .RegressionExplorer: RegressionExplorerLab()
        case .PerceptronVisualizer: PerceptronLab()
        case .ClassifierPlayground: ClassifierPlaygroundLab(config: .forTopic(topicId)).id(topicId)
        case .BanditExplorer: BanditLab(topicId: topicId).id(topicId)
        case .SearchVisualizer: SearchLab(topicId: topicId).id(topicId)
        case .SortingVisualizer: SortingLab(topicId: topicId).id(topicId)
        case .HashingVisualizer: HashingLab(topicId: topicId).id(topicId)
        case .GraphVisualizer: GraphBuilderLab(topicId: topicId).id(topicId)
        case .PathfindingGrid: PathfindingLab(topicId: topicId).id(topicId)
        case .RecursionTreeVisualizer: RecursionTreeLab(topicId: topicId).id(topicId)
        case .DpGridVisualizer: DpGridLab(topicId: topicId).id(topicId)
        case .BitBoardPlayer:
            if bitStoryTopicIds.contains(topicId) { BitStoryLab(topicId: topicId).id(topicId) } else { BitBoardLab(topicId: topicId).id(topicId) }
        // Deque is taught as a sandbox (push/pop at either end) rather than a frame player; the doubly
        // linked list has its own tabbed storyboard.
        case .LinkedStructurePlayer:
            if topicId == "deque" { DequeLab() } else if topicId == "doubly_linked_list" { DoublyLinkedLab() } else { LinkedStructureLab(topicId: topicId).id(topicId) }
        case .DecisionSurface: DecisionSurfaceLab(topicId: topicId).id(topicId)
        case .RegressionLab: RegressionLab(topicId: topicId).id(topicId)
        case .ExplorationPlayer: ExplorationLab(topicId: topicId).id(topicId)
        case .MultiAgentPlayer: MultiAgentLab(topicId: topicId).id(topicId)
        case .RlTrainingPlayer: RlTrainingLab(topicId: topicId).id(topicId)
        case .PolicyGradientPlayer: PolicyGradientLab(topicId: topicId).id(topicId)
        case .GameSearchPlayer: GameSearchLab(topicId: topicId).id(topicId)
        case .EnvironmentPlayer: EnvironmentLab(topicId: topicId).id(topicId)
        case .RlGridWorld: RlGridWorldLab(topicId: topicId).id(topicId)
        case .OfflineRlPlayer: OfflineRlLab(topicId: topicId).id(topicId)
        case .GraphAlgorithmPlayer: GraphAlgorithmLab(topicId: topicId).id(topicId)
        case .TreeVisualizer: TreeVisualizerLab(topicId: topicId).id(topicId)
        // Every feature-map topic is a deep-learning storyboard now.
        case .FeatureMapPlayer: DeepStoryLab(topicId: topicId).id(topicId)
        case .ArrayWalkPlayer: ArrayWalkLab(topicId: topicId).id(topicId)
        // Only the geometry storyboards, the k-d tree and closest pair have point-cloud labs on iOS so far; the rest of
        // PointCloud is not ported yet.
        case .PointCloudPlayer:
            if geometryTopicIds.contains(topicId) { GeometryLab(topicId: topicId).id(topicId) } else if topicId == "kd_tree" { KdTreeLab() } else if topicId == "closest_pair_of_points" { ClosestPairLab() } else { SimulationComingSoonCard() }
        // The tokenization topics have their own lab; the rest of TokenStrip is not ported yet.
        case .TokenStripPlayer:
            if TokenizerLabData.topicIds.contains(topicId) { TokenizerLab(topicId: topicId).id(topicId) } else { SimulationComingSoonCard() }
        default: SimulationComingSoonCard()
        }
    }
}

struct SimulationComingSoonCard: View {
    @Environment(\.palette) private var palette

    var body: some View {
        VStack(spacing: 6) {
            Text("Simulation coming soon").font(.titleMedium)
            Text("An interactive visualizer for this topic hasn't been built yet.")
                .font(.bodyMedium).foregroundStyle(palette.muted).multilineTextAlignment(.center)
        }
        .padding(20)
        .frame(maxWidth: .infinity)
        .card(radius: 18)
    }
}
