import SwiftUI

/// The single place that maps a SimulationType to its lab (SimulationHost.kt). Both the topic
/// page and the sim-only screen render through here, so a new lab is wired once.
struct SimulationHost: View {
    let topicId: String
    let type: SimulationType

    var body: some View {
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
        case .BitBoardPlayer: BitBoardLab(topicId: topicId).id(topicId)
        // Deque is taught as a sandbox (push/pop at either end) rather than a frame player.
        case .LinkedStructurePlayer:
            if topicId == "deque" { DequeLab() } else { LinkedStructureLab(topicId: topicId).id(topicId) }
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
        case .FeatureMapPlayer: FeatureMapLab(topicId: topicId).id(topicId)
        case .ArrayWalkPlayer: ArrayWalkLab(topicId: topicId).id(topicId)
        // Only the k-d tree has a point-cloud lab on iOS so far; the rest of PointCloud is not ported yet.
        case .PointCloudPlayer:
            if topicId == "kd_tree" { KdTreeLab() } else { SimulationComingSoonCard() }
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
