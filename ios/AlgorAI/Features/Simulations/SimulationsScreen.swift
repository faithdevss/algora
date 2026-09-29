import SwiftUI

// Ports of feature/simulations/SimulationsScreen.kt and SimulationDetailScreen.kt.

func simLabel(_ type: SimulationType) -> String {
    switch type {
    case .ArrayVisualizer: "Array visualizer"
    case .LinkedListVisualizer: "Linked-list visualizer"
    case .StackVisualizer: "Stack visualizer"
    case .QueueVisualizer: "Queue visualizer"
    case .GraphVisualizer: "Graph builder · BFS/DFS"
    case .GraphAlgorithmPlayer: "Graph algorithm player"
    case .ArrayWalkPlayer: "Array walk · pointer player"
    case .PointCloudPlayer: "2D feature space player"
    case .TokenStripPlayer: "Token strip · attention"
    case .NeuralNetPlayer: "Network · training player"
    case .RlTrainingPlayer: "RL training · measured runs"
    case .PolicyGradientPlayer: "Policy gradient · variance"
    case .GameSearchPlayer: "Search · planning player"
    case .OfflineRlPlayer: "Offline · imitation player"
    case .MultiAgentPlayer: "Multi-agent player"
    case .ExplorationPlayer: "Exploration · meta-RL"
    case .LinkedStructurePlayer: "Linked structure player"
    case .EnvironmentPlayer: "Environment player"
    case .RegressionExplorer: "Regression explorer"
    case .RegressionLab: "Regression lab · estimators"
    case .DecisionSurface: "Decision surface · boundaries"
    case .FeatureMapPlayer: "Feature map · conv player"
    case .BitBoardPlayer: "Bit board · per-bit player"
    case .PerceptronVisualizer: "Perceptron playground"
    case .ClassifierPlayground: "Classifier playground"
    case .RecursionTreeVisualizer: "Recursion tree · call stack"
    case .DpGridVisualizer: "DP table · animated fill"
    case .SortingVisualizer: "Sorting visualizer"
    case .SearchVisualizer: "Search visualizer"
    case .TreeVisualizer: "Tree visualizer"
    case .PathfindingGrid: "Pathfinding grid"
    case .HashingVisualizer: "Hashing · bucket visualizer"
    case .RlGridWorld: "Grid world · RL agent"
    case .BanditExplorer: "Multi-armed bandit"
    case .NotYetAvailable: "Interactive lab"
    }
}

private struct SimEntry: Hashable { let topic: Topic; let label: String }
private struct SimSubgroup: Hashable { let key: String; let name: String; var entries: [SimEntry] }

private struct SimGroup: Hashable {
    let section: Section
    let title: String
    let icon: String
    let accent: Int64
    var subgroups: [SimSubgroup]
    var count: Int { subgroups.reduce(0) { $0 + $1.entries.count } }

    /// Matches topic name, lab label and category name.
    func filtered(_ query: String) -> SimGroup? {
        var copy = self
        copy.subgroups = subgroups.compactMap { sub in
            var s = sub
            s.entries = sub.entries.filter {
                $0.topic.name.localizedCaseInsensitiveContains(query) || $0.label.localizedCaseInsensitiveContains(query)
                    || sub.name.localizedCaseInsensitiveContains(query)
            }
            return s.entries.isEmpty ? nil : s
        }
        return copy.subgroups.isEmpty ? nil : copy
    }
}

private let sectionMeta: [(Section, String, String, Int64)] = [
    (.DATA_STRUCTURES, "Data Structures", "stack", 0xFF10_B981),
    (.ALGORITHMS, "Algorithms", "chip", 0xFF3B_82F6),
    (.ANALYSIS, "Analysis", "trend", 0xFF8B_5CF6),
    (.INTERVIEW_PREP, "Coding Patterns", "target", 0xFFF5_9E0B),
    (.ML, "Machine Learning", "robot", 0xFF63_66F1),
    (.DL, "Deep Learning", "network", 0xFFEC_4899),
    (.NLP, "NLP", "globe", 0xFF14_B8A6),
    (.RL, "Reinforcement Learning", "game", 0xFFF5_9E0B),
]

private func patternGroupName(_ type: SimulationType) -> String {
    switch type {
    case .ArrayWalkPlayer: "Array Walk"
    case .BitBoardPlayer: "Bit Board"
    case .GraphAlgorithmPlayer: "Graph"
    case .PathfindingGrid: "Grid"
    case .TreeVisualizer: "Tree"
    case .RecursionTreeVisualizer: "Recursion"
    case .DpGridVisualizer: "DP Table"
    case .SortingVisualizer: "Sorting"
    case .SearchVisualizer: "Search"
    case .HashingVisualizer: "Hashing"
    case .LinkedStructurePlayer: "Linked Structure"
    case .GameSearchPlayer: "Game Search"
    default: "Other"
    }
}

private let simGroups: [SimGroup] = {
    let content = ContentStore.shared
    var bySection: [Section: [(String, [SimEntry])]] = [:]
    for (topicId, type) in content.runnableSimulations {
        guard let topic = content.topic(topicId) else { continue }
        let category = content.category(topic.categoryId)
        let section = category?.section ?? .ALGORITHMS
        // The coding patterns are one content category of 50+ topics, so here they split by the kind of lab
        // they run instead.
        let name = topic.categoryId == "interview_patterns" ? patternGroupName(type) : category?.name ?? "Other"
        var list = bySection[section] ?? []
        let entry = SimEntry(topic: topic, label: simLabel(type))
        if let i = list.firstIndex(where: { $0.0 == name }) { list[i].1.append(entry) } else { list.append((name, [entry])) }
        bySection[section] = list
    }
    return sectionMeta.compactMap { section, title, icon, accent in
        guard let cats = bySection[section] else { return nil }
        return SimGroup(section: section, title: title, icon: icon, accent: accent,
                        subgroups: cats.map { SimSubgroup(key: "\(section.rawValue)|\($0.0)", name: $0.0, entries: $0.1) })
    }
}()

struct SimulationsScreen: View {
    @Environment(Router.self) private var router
    @Environment(\.palette) private var palette
    @State private var query = ""
    @State private var openSections: Set<Section> = []
    @State private var openCategories: Set<String> = []

    var body: some View {
        let searching = !query.trimmingCharacters(in: .whitespaces).isEmpty
        let groups = searching ? simGroups.compactMap { $0.filtered(query) } : simGroups
        let total = simGroups.reduce(0) { $0 + $1.count }
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 0) {
                Text("Simulations").font(.headlineMedium).padding(.top, 12)
                Text("\(total) interactive labs — pick a section, then a lab")
                    .font(.bodyMedium).foregroundStyle(palette.muted).padding(.top, 4).padding(.bottom, 12)
                SearchField(query: $query, placeholder: "Search labs…").padding(.bottom, 10)
                if groups.isEmpty {
                    Text("No labs match “\(query)”.").font(.bodyMedium).foregroundStyle(palette.muted).padding(.top, 24)
                }
                ForEach(groups, id: \.section) { group in
                    // A search shows its hits directly.
                    let sectionOpen = searching || openSections.contains(group.section)
                    GroupHeader(group: group, expanded: sectionOpen) { toggle(&openSections, group.section) }
                    if sectionOpen {
                        ForEach(group.subgroups, id: \.key) { sub in
                            let open = searching || openCategories.contains(sub.key)
                            AccordionHeader(title: sub.name, count: sub.entries.count, accent: Color(argb: group.accent), isExpanded: open) {
                                toggle(&openCategories, sub.key)
                            }
                            .padding(.leading, 8).padding(.top, 5).padding(.bottom, 1)
                            if open {
                                ForEach(sub.entries, id: \.topic.id) { entry in
                                    SimRow(entry: entry) { router.push(.simulation(entry.topic.id)) }
                                }
                            }
                        }
                    }
                }
            }
            .padding(.horizontal, screenGutter)
            .padding(.bottom, screenBottomInset)
        }
        .scrollDismissesKeyboard(.immediately)
    }

    private func toggle<T: Hashable>(_ set: inout Set<T>, _ value: T) {
        if set.contains(value) { set.remove(value) } else { set.insert(value) }
    }
}

private struct GroupHeader: View {
    let group: SimGroup
    let expanded: Bool
    let action: () -> Void
    @Environment(\.palette) private var palette

    var body: some View {
        let accent = Color(argb: group.accent)
        Button(action: action) {
            HStack(spacing: 13) {
                AppIcon(name: group.icon, size: 18)
                    .foregroundStyle(.white)
                    .frame(width: 34, height: 34)
                    .background(LinearGradient(colors: [accent, accent.opacity(0.6)], startPoint: .topLeading, endPoint: .bottomTrailing),
                                in: RoundedRectangle(cornerRadius: 11))
                VStack(alignment: .leading, spacing: 1) {
                    Text(group.title).font(AppFont.grotesk(15.5, .bold))
                    Text("\(group.count) labs · \(group.subgroups.count) categories").font(AppFont.sans(12.5)).foregroundStyle(palette.muted)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                Image(systemName: "chevron.down").font(.system(size: 15, weight: .semibold)).foregroundStyle(accent)
                    .rotationEffect(.degrees(expanded ? 180 : 0))
            }
            .padding(14)
            .background(expanded ? accent.opacity(0.07) : palette.surface, in: RoundedRectangle(cornerRadius: 16))
            .overlay(RoundedRectangle(cornerRadius: 16).stroke(expanded ? accent.opacity(0.45) : palette.outline, lineWidth: 1))
        }
        .buttonStyle(.plain)
        .padding(.top, 10)
        .padding(.bottom, 3)
        .animation(.easeInOut(duration: 0.2), value: expanded)
    }
}

private struct SimRow: View {
    let entry: SimEntry
    let action: () -> Void
    @Environment(\.palette) private var palette

    var body: some View {
        let accent = Color(argb: entry.topic.accentColor)
        Button(action: action) {
            HStack(spacing: 13) {
                AppIcon(name: entry.topic.iconName, size: 22)
                    .foregroundStyle(accent)
                    .frame(width: 44, height: 44)
                    .background(accent.opacity(0.14), in: RoundedRectangle(cornerRadius: 13))
                VStack(alignment: .leading, spacing: 1) {
                    Text(entry.topic.name).font(AppFont.grotesk(15.5, .bold)).multilineTextAlignment(.leading)
                    Text(entry.label).font(AppFont.sans(12.5)).foregroundStyle(palette.muted)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                Image(systemName: "play.fill")
                    .font(.system(size: 14))
                    .foregroundStyle(.white)
                    .frame(width: 34, height: 34)
                    .background(accent, in: RoundedRectangle(cornerRadius: 11))
            }
            .padding(14)
            .card(radius: 16)
        }
        .buttonStyle(.plain)
        .padding(.leading, 16)
        .padding(.vertical, 4)
    }
}

/// The lab on its own: stage and narration scroll, the transport is pinned in thumb reach, and the
/// lab's intro plus the full write-up sit behind (i). The tab bar hides inside a lab.
struct SimulationDetailScreen: View {
    let topicId: String
    @State private var dock = LabDock()
    @State private var showInfo = false
    @Environment(Router.self) private var router
    @Environment(\.palette) private var palette

    var body: some View {
        let content = ContentStore.shared
        Group {
            if let topic = content.topic(topicId), let page = content.content(topicId) {
                VStack(spacing: 0) {
                    LabNavBar(title: topic.name, back: backLabel(content.category(topic.categoryId)?.name), topicId: topicId, action: dock.navAction) {
                        showInfo = true
                    }
                    ScrollView {
                        SimulationHost(topicId: topicId, type: page.simulation)
                            .environment(\.labDock, dock)
                            .padding(.horizontal, 16)
                            .padding(.top, 12)
                            .padding(.bottom, 20)
                    }
                    .safeAreaInset(edge: .bottom, spacing: 0) { LabDockBar(dock: dock) }
                }
                .sheet(isPresented: $showInfo) {
                    LabInfoSheet(title: simLabel(page.simulation), intro: dock.intro) {
                        showInfo = false
                        router.push(.topic(topicId))
                    }
                }
            } else {
                VStack {
                    DetailHeader(title: content.topic(topicId)?.name ?? "Simulation")
                    Spacer()
                    Text("Simulation not available yet.").font(.bodyLarge)
                    Spacer()
                }
            }
        }
        .toolbar(.hidden, for: .tabBar)
    }
}

/// "13 · Misc Algorithms" → "Misc Algorithms".
private func backLabel(_ category: String?) -> String {
    guard let category else { return "Back" }
    if let r = category.range(of: " · "), category[..<r.lowerBound].allSatisfy(\.isNumber) { return String(category[r.upperBound...]) }
    return category
}

/// iOS navigation bar: "‹ Category" back, centred title, (i) and bookmark in the accent.
private struct LabNavBar: View {
    let title: String
    let back: String
    let topicId: String
    var action: LabNavAction?
    let onInfo: () -> Void
    @Environment(AppStore.self) private var store
    @Environment(\.palette) private var palette
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        let marked = store.bookmarks.contains(topicId)
        ZStack {
            Text(title).font(AppFont.sans(17, .semibold)).lineLimit(1).padding(.horizontal, 110)
            HStack(spacing: 0) {
                Button { dismiss() } label: {
                    HStack(spacing: 4) {
                        Image(systemName: "chevron.backward").font(.system(size: 19, weight: .semibold))
                        Text(back).font(AppFont.sans(17)).lineLimit(1)
                    }
                    .frame(maxWidth: 110, minHeight: 44, alignment: .leading)
                    .contentShape(Rectangle())
                }
                .accessibilityLabel("Back to \(back)")
                Spacer()
                if let action {
                    Button(action: action.action) {
                        if action.text {
                            Text(action.label).font(AppFont.sans(17, .medium)).frame(minWidth: 40, minHeight: 44).padding(.horizontal, 4)
                        } else {
                            Image(systemName: action.icon).font(.system(size: 18, weight: .semibold)).frame(width: 40, height: 44)
                        }
                    }
                    .accessibilityLabel(action.label)
                }
                Button(action: onInfo) {
                    Image(systemName: "info.circle").font(.system(size: 20)).frame(width: 40, height: 44)
                }
                .accessibilityLabel("About this lab")
                if action == nil {
                    Button { store.toggleBookmark(topicId) } label: {
                        Image(systemName: marked ? "bookmark.fill" : "bookmark").font(.system(size: 18)).frame(width: 40, height: 44)
                    }
                    .accessibilityLabel(marked ? "Remove bookmark" : "Bookmark")
                }
            }
            .buttonStyle(.plain)
            .foregroundStyle(palette.primary)
        }
        .padding(.leading, 8)
        .padding(.trailing, 8)
        .frame(height: 44)
        .padding(.top, 4)
        .background(palette.background)
    }
}

/// Behind (i): what the lab shows, then the way into the full topic.
private struct LabInfoSheet: View {
    let title: String
    let intro: String?
    let onReadTopic: () -> Void
    @Environment(\.dismiss) private var dismiss
    @Environment(\.palette) private var palette

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            ZStack {
                Text("About this lab").font(AppFont.sans(17, .semibold))
                HStack {
                    Spacer()
                    Button("Done") { dismiss() }.font(AppFont.sans(17, .semibold)).foregroundStyle(palette.primary)
                }
            }
            .frame(height: 52)
            Text(title).font(.titleLarge).padding(.top, 8)
            if let intro {
                Text(intro).font(AppFont.sans(16)).foregroundStyle(palette.onSurface.opacity(0.85))
                    .fixedSize(horizontal: false, vertical: true)
                    .padding(.top, 10)
            }
            Spacer(minLength: 20)
            Button(action: onReadTopic) {
                HStack(spacing: 8) {
                    Text("Read full topic").font(AppFont.sans(17, .semibold))
                    Image(systemName: "arrow.right").font(.system(size: 15, weight: .semibold))
                }
                .foregroundStyle(.white)
                .frame(maxWidth: .infinity, minHeight: 50)
                .background(palette.primary, in: RoundedRectangle(cornerRadius: 14))
            }
            .buttonStyle(.plain)
            .padding(.bottom, 8)
        }
        .padding(.horizontal, 20)
        .background(palette.background)
        .presentationDetents([.medium])
        .presentationDragIndicator(.visible)
    }
}
