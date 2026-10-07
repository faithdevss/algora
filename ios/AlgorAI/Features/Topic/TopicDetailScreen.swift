import SwiftUI

// Port of feature/topics/TopicDetailScreen.kt. Every route into topic content passes through here:
// a quiz, the behavioral bank, a system-design primer, an analysis tool, or the standard page.
// The paywall gate is gone on iOS — every topic is open.
struct TopicDetailScreen: View {
    let topicId: String
    @Environment(AppStore.self) private var store

    private let content = ContentStore.shared

    var body: some View {
        Group {
            if let topic = content.topic(topicId) {
                if let quiz = content.quiz(topicId) {
                    QuizScreen(quizId: topicId, quiz: quiz, onFinish: markCompleted)
                } else if let bank = content.behavioralBank(topicId) {
                    BehavioralScreen(bank: bank, onComplete: markCompleted)
                } else if let primer = content.systemDesignPrimer(topicId) {
                    SystemDesignScreen(primer: primer, onComplete: markCompleted)
                } else if analysisBoardIds.contains(topicId) {
                    AnalysisBoardPage(topicId: topicId)
                } else if AnalysisToolRegistry.has(topicId) {
                    AnalysisToolPage(topic: topic)
                } else if let page = content.content(topicId) {
                    TopicPage(topic: topic, page: page)
                } else {
                    ComingSoonPage(topic: topic)
                }
            } else {
                VStack {
                    ScreenHeader(title: "")
                    Spacer()
                    Text("Content not available yet.").font(.bodyLarge)
                    Spacer()
                }
            }
        }
        .onAppear { store.setLastOpened(topicId) }
    }

    private func markCompleted() { store.markCompleted(topicId) }
}

/// Header with the bookmark toggle, shared with the sim-only screen.
struct DetailHeader: View {
    let title: String
    var topicId: String?
    @Environment(AppStore.self) private var store
    @Environment(\.palette) private var palette

    var body: some View {
        ScreenHeader(title: title) {
            if let topicId {
                let marked = store.bookmarks.contains(topicId)
                Button { store.toggleBookmark(topicId) } label: {
                    Image(systemName: marked ? "bookmark.fill" : "bookmark")
                        .font(.system(size: 17))
                        .foregroundStyle(marked ? palette.primary : palette.muted)
                        .frame(width: 44, height: 44)
                }
                .accessibilityLabel(marked ? "Remove bookmark" : "Bookmark")
            }
        }
    }
}

/// "Mark as complete" checkbox row.
struct CompleteToggle: View {
    let topicId: String
    @Environment(AppStore.self) private var store
    @Environment(\.palette) private var palette

    var body: some View {
        let done = store.completedTopicIds.contains(topicId)
        Button {
            if done { store.markIncomplete(topicId) } else { store.markCompleted(topicId) }
        } label: {
            HStack(spacing: 12) {
                Image(systemName: done ? "checkmark.square.fill" : "square")
                    .font(.system(size: 22))
                    .foregroundStyle(done ? palette.primary : palette.muted)
                Text("Mark as complete").font(.bodyLarge)
                Spacer()
            }
            .padding(16)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }
}

private struct TopicScrollOffsetKey: PreferenceKey {
    static let defaultValue: CGFloat = 0
    static func reduce(value: inout CGFloat, nextValue: () -> CGFloat) { value = nextValue() }
}

private enum TopicTab: String, CaseIterable { case overview = "Overview", math = "Math", code = "Code", simulate = "Simulate" }

/// A lesson: back link with share and bookmark, a coloured category eyebrow, a large title and tagline,
/// then pill tabs that split the page into the overview, the maths, the code and the live simulation.
private struct TopicPage: View {
    let topic: Topic
    let page: TopicContent
    @Environment(\.palette) private var palette
    @State private var tab: TopicTab = .overview
    @State private var titleScrolledOff = false

    private var tabs: [TopicTab] {
        TopicTab.allCases.filter {
            switch $0 {
            case .overview: true
            case .math: !page.formulas.isEmpty
            case .code: !page.codeBlocks.isEmpty
            case .simulate: page.simulation != .NotYetAvailable
            }
        }
    }

    var body: some View {
        let accent = Color(argb: topic.accentColor)
        VStack(spacing: 0) {
            TopicNavBar(topic: topic, showTitle: titleScrolledOff)
            ScrollView {
                LazyVStack(alignment: .leading, spacing: 0) {
                    GeometryReader { Color.clear.preference(key: TopicScrollOffsetKey.self, value: $0.frame(in: .named("topicScroll")).minY) }
                        .frame(height: 0)
                    Text(eyebrow).font(AppFont.sans(14, .bold)).tracking(1).foregroundStyle(accent).padding(.top, 4)
                    Text(topic.name).font(AppFont.sans(32, .bold)).padding(.top, 4)
                    Text(topic.tagline).font(AppFont.sans(17)).foregroundStyle(palette.muted).padding(.top, 4)
                    TopicTabs(tabs: tabs, selected: $tab).padding(.top, 16).padding(.bottom, 18)
                    switch tab {
                    case .overview: overview
                    case .math: MathSection(formulas: page.formulas, notation: page.notationKey)
                    case .code:
                        ForEach(Array(page.codeBlocks.enumerated()), id: \.offset) { index, block in
                            CodeBlockCard(block: block, expanded: index == 0).padding(.bottom, 12)
                        }
                    case .simulate: SimulationHost(topicId: topic.id, type: page.simulation)
                    }
                }
                .padding(.horizontal, screenGutter)
                .padding(.bottom, screenBottomInset)
            }
            .coordinateSpace(name: "topicScroll")
            .onPreferenceChange(TopicScrollOffsetKey.self) { y in
                let off = y < -70
                if off != titleScrolledOff { withAnimation(.easeInOut(duration: 0.18)) { titleScrolledOff = off } }
            }
        }
    }

    /// "GRAPHS · INTERMEDIATE": the category without its number, then the level.
    private var eyebrow: String {
        let category = ContentStore.shared.category(topic.categoryId)?.name ?? ""
        var name = category
        if let r = category.range(of: " · "), category[..<r.lowerBound].allSatisfy(\.isNumber) { name = String(category[r.upperBound...]) }
        return ([name] + (topic.difficulty.map { [$0.rawValue] } ?? [])).filter { !$0.isEmpty }.joined(separator: " · ").uppercased()
    }

    @ViewBuilder private var overview: some View {
        ForEach(page.whatIsIt, id: \.self) { paragraph in
            Text(paragraph).font(AppFont.sans(17)).lineSpacing(5).padding(.bottom, 12)
        }
        sectionTitle("How It Works")
        if let figure = page.figure { FigureCard(figure: figure).padding(.bottom, 11) }
        VStack(spacing: 11) { ForEach(page.steps, id: \.self) { StepRow(step: $0) } }
        if !page.applications.isEmpty {
            sectionTitle("Real-World Applications")
            VStack(spacing: 11) { ForEach(page.applications, id: \.self) { ApplicationRow(app: $0) } }
        }
        TakeawaysSection(takeaways: page.takeaways).padding(.vertical, 22)
        PrerequisitesSection(topicId: topic.id)
        if !page.crossLinks.isEmpty { RelatedTopicsSection(links: page.crossLinks) }
    }

    private func sectionTitle(_ text: String) -> some View {
        Text(text).font(AppFont.grotesk(24, .bold)).padding(.top, 14).padding(.bottom, 12)
    }
}

/// "‹ Learning" on the left; mark-complete and bookmark on the right.
private struct TopicNavBar: View {
    let topic: Topic
    let showTitle: Bool
    @Environment(AppStore.self) private var store
    @Environment(Router.self) private var router
    @Environment(\.palette) private var palette
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        let back = router.backTitle
        let marked = store.bookmarks.contains(topic.id)
        HStack(spacing: 0) {
            Button { dismiss() } label: {
                // The collapsed title is part of the back control, like the arrow beside it.
                HStack(spacing: 4) {
                    Image(systemName: "chevron.backward").font(.system(size: 19, weight: .semibold))
                    if showTitle {
                        Text(topic.name).font(AppFont.sans(17, .semibold)).lineLimit(1).padding(.leading, 4)
                    } else {
                        Text(back).font(AppFont.sans(17)).lineLimit(1)
                    }
                }
                .frame(minHeight: 44, alignment: .leading)
                .contentShape(Rectangle())
                .animation(.easeInOut(duration: 0.2), value: showTitle)
            }
            .accessibilityLabel("Back to \(back)")
            Spacer(minLength: 0)
            DoneToggle(topicId: topic.id)
            Button { store.toggleBookmark(topic.id) } label: {
                Image(systemName: marked ? "bookmark.fill" : "bookmark").font(.system(size: 19)).frame(width: 44, height: 44)
            }
            .accessibilityLabel(marked ? "Remove bookmark" : "Bookmark")
        }
        .buttonStyle(.plain)
        .foregroundStyle(palette.dark ? Color.white : palette.primary)
        .padding(.leading, 10)
        .padding(.trailing, 8)
        .padding(.top, 4)
        .background(palette.background)
    }
}

/// The nav-bar mark-complete: an outlined tick that fills green when the topic is done.
struct DoneToggle: View {
    let topicId: String
    @Environment(AppStore.self) private var store
    @Environment(\.palette) private var palette

    var body: some View {
        let done = store.completedTopicIds.contains(topicId)
        Button {
            if done { store.markIncomplete(topicId) } else { store.markCompleted(topicId) }
        } label: {
            Image(systemName: done ? "checkmark.circle.fill" : "checkmark.circle")
                .font(.system(size: 19))
                .foregroundStyle(done ? SimColors.green : (palette.dark ? Color.white : palette.primary))
            .frame(width: 44, height: 44)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(done ? "Mark not complete" : "Mark complete")
    }
}

/// Pill tabs: the selected one filled in the accent, the rest on the neutral fill.
private struct TopicTabs: View {
    let tabs: [TopicTab]
    @Binding var selected: TopicTab
    @Environment(\.palette) private var palette

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                ForEach(tabs, id: \.self) { t in
                    let on = t == selected
                    Button { selected = t } label: {
                        Text(t.rawValue).font(AppFont.sans(15, .semibold))
                            .foregroundStyle(on ? .white : palette.onSurface)
                            .padding(.horizontal, 16)
                            .frame(height: 36)
                            .background(on ? palette.primary : SimColors.tint, in: Capsule())
                    }
                    .buttonStyle(.plain)
                    .accessibilityAddTraits(on ? .isSelected : [])
                }
            }
        }
    }
}

/// Icon tile + name + difficulty + tagline, shared by the hero and the coming-soon page.
struct TopicHeroHeader: View {
    let topic: Topic
    @Environment(\.palette) private var palette

    var body: some View {
        let accent = Color(argb: topic.accentColor)
        HStack(spacing: 14) {
            AppIcon(name: topic.iconName, size: 24)
                .foregroundStyle(accent)
                .frame(width: 56, height: 56)
                .background(palette.surface, in: RoundedRectangle(cornerRadius: 16))
                .shadow(color: accent.opacity(0.35), radius: 8, y: 3)
            VStack(alignment: .leading, spacing: 2) {
                HStack(spacing: 8) {
                    Text(topic.name).font(.titleLarge)
                    if let difficulty = topic.difficulty { DifficultyBadge(difficulty: difficulty) }
                }
                Text(topic.tagline).font(.bodyMedium).foregroundStyle(palette.muted)
            }
        }
    }
}

private struct StepRow: View {
    let step: StepCard
    @Environment(\.palette) private var palette

    var body: some View {
        let accent = Color(argb: step.accentColor)
        HStack(alignment: .top, spacing: 13) {
            Text("\(step.number)")
                .font(AppFont.grotesk(15, .bold))
                .foregroundStyle(accent)
                .frame(width: 32, height: 32)
                .background(accent.opacity(0.16), in: RoundedRectangle(cornerRadius: 10))
            VStack(alignment: .leading, spacing: 2) {
                Text(step.title).font(.titleMedium)
                Text(step.body).font(.bodyMedium).foregroundStyle(palette.muted).lineSpacing(3)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .padding(15)
        .card(radius: 16)
    }
}

private struct MathSection: View {
    let formulas: [FormulaEntry]
    let notation: [NotationEntry]
    @Environment(\.palette) private var palette

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            ForEach(Array(formulas.enumerated()), id: \.offset) { index, formula in
                VStack(alignment: .leading, spacing: 4) {
                    // Label and formula share a line while they fit; a long formula drops below.
                    ViewThatFits(in: .horizontal) {
                        HStack(alignment: .firstTextBaseline) {
                            Text(formula.label).font(.bodyLarge).fixedSize()
                            Spacer(minLength: 12)
                            Text(formula.formula).font(.code).foregroundStyle(palette.primary).fixedSize()
                        }
                        VStack(alignment: .leading, spacing: 3) {
                            Text(formula.label).font(.bodyLarge)
                            Text(formula.formula).font(.code).foregroundStyle(palette.primary)
                        }
                    }
                    Text(formula.note).font(.bodyMedium).foregroundStyle(palette.muted)
                }
                .padding(.vertical, 12)
                if index < formulas.count - 1 { Rectangle().fill(palette.outline).frame(height: 1) }
            }
            if !notation.isEmpty {
                Text("Notation Key").font(.titleMedium).padding(.top, 8)
                ForEach(notation, id: \.self) { entry in
                    HStack(alignment: .firstTextBaseline, spacing: 8) {
                        Text(entry.symbol).font(.code).foregroundStyle(palette.primary)
                        Text(entry.meaning).font(.bodyMedium).frame(maxWidth: .infinity, alignment: .leading)
                    }
                    .padding(.vertical, 4)
                }
            }
        }
        .padding(.horizontal, screenGutter)
        .padding(.vertical, 6)
        .padding(.bottom, 8)
        .frame(maxWidth: .infinity, alignment: .leading)
        .card(radius: 16)
    }
}

private struct ApplicationRow: View {
    let app: ApplicationCard
    @Environment(\.palette) private var palette

    var body: some View {
        let accent = Color(argb: app.accentColor)
        HStack(spacing: 13) {
            AppIcon(name: app.iconName, size: 22)
                .foregroundStyle(accent)
                .frame(width: 44, height: 44)
                .background(accent.opacity(0.14), in: RoundedRectangle(cornerRadius: 13))
            VStack(alignment: .leading, spacing: 2) {
                Text(app.title).font(.titleMedium)
                Text(app.body).font(.bodyMedium).foregroundStyle(palette.muted)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .padding(15)
        .card(radius: 16)
    }
}

struct TakeawaysSection: View {
    let takeaways: [String]
    @Environment(\.palette) private var palette

    var body: some View {
        let green = SimColors.green
        VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: 10) {
                Image(systemName: "lightbulb.fill").foregroundStyle(green)
                Text("Key Takeaways").font(AppFont.grotesk(22, .bold))
            }
            .padding(.bottom, 14)
            ForEach(takeaways, id: \.self) { takeaway in
                HStack(alignment: .top, spacing: 10) {
                    Image(systemName: "checkmark.circle.fill").font(.system(size: 18)).foregroundStyle(green)
                    Text(takeaway).font(.bodyLarge)
                }
                .padding(.vertical, 5)
            }
        }
        .padding(18)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(palette.dark ? green.opacity(0.12) : Color(hex: 0xE9F9EE), in: RoundedRectangle(cornerRadius: 20))
        .overlay(RoundedRectangle(cornerRadius: 20).stroke(green.opacity(0.3), lineWidth: 1))
    }
}

/// Learning path: what to learn first and what this topic unlocks.
private struct PrerequisitesSection: View {
    let topicId: String
    @Environment(Router.self) private var router
    @Environment(\.palette) private var palette

    var body: some View {
        let content = ContentStore.shared
        let prereqs = content.prereqs(of: topicId).compactMap { content.topic($0) }
        let unlocks = content.unlockedBy(topicId).compactMap { content.topic($0) }
        if !prereqs.isEmpty || !unlocks.isEmpty {
            VStack(alignment: .leading, spacing: 10) {
                Text("Learning Path").font(.titleLarge).padding(.bottom, 2)
                if !prereqs.isEmpty { pathRow("Learn first", prereqs, palette.primary) }
                if !unlocks.isEmpty { pathRow("Unlocks", unlocks, SimColors.green) }
            }
            .padding(.vertical, 4)
            .padding(.bottom, 18)
        }
    }

    private func pathRow(_ label: String, _ topics: [Topic], _ accent: Color) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(label).font(.labelLarge).foregroundStyle(palette.muted)
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(topics) { topic in
                        Button { router.push(.topic(topic.id)) } label: {
                            Text(topic.name)
                                .font(AppFont.sans(14, .medium))
                                .foregroundStyle(accent)
                                .padding(.horizontal, 12)
                                .padding(.vertical, 8)
                                .background(accent.opacity(0.10), in: RoundedRectangle(cornerRadius: 10))
                                .overlay(RoundedRectangle(cornerRadius: 10).stroke(accent.opacity(0.35), lineWidth: 1))
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
        }
    }
}

private struct RelatedTopicsSection: View {
    let links: [CrossLink]
    @Environment(Router.self) private var router
    @Environment(\.palette) private var palette

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("Related Topics").font(.titleLarge).padding(.bottom, 2)
            ForEach(links, id: \.self) { link in
                Button { router.push(.topic(link.topicId)) } label: {
                    HStack {
                        Text(link.label).font(.titleMedium).multilineTextAlignment(.leading)
                        Spacer()
                        Image(systemName: "chevron.right").foregroundStyle(palette.muted)
                    }
                    .padding(.horizontal, 15)
                    .padding(.vertical, 13)
                    .card()
                }
                .buttonStyle(.plain)
            }
        }
    }
}

private struct ComingSoonPage: View {
    let topic: Topic
    @Environment(\.palette) private var palette

    var body: some View {
        let accent = Color(argb: topic.accentColor)
        VStack(spacing: 0) {
            DetailHeader(title: topic.name, topicId: topic.id)
            VStack(spacing: 22) {
                TopicHeroHeader(topic: topic)
                    .padding(18)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(accent.opacity(palette.dark ? 0.14 : 0.10), in: RoundedRectangle(cornerRadius: 20))
                VStack(spacing: 6) {
                    Text("Full content coming soon").font(.titleMedium)
                    Text("This topic is in the taxonomy but its deep-dive content hasn't been authored yet.")
                        .font(.bodyMedium).foregroundStyle(palette.muted).multilineTextAlignment(.center)
                }
                .padding(20)
                .frame(maxWidth: .infinity)
                .card(radius: 16)
                Spacer()
            }
            .padding(16)
        }
    }
}

/// An analysis tool rendered in place of the standard template.
private struct AnalysisToolPage: View {
    let topic: Topic

    var body: some View {
        VStack(spacing: 0) {
            DetailHeader(title: topic.name, topicId: topic.id)
            ScrollView {
                VStack(spacing: 0) {
                    AnalysisToolRegistry.view(for: topic.id)
                    CompleteToggle(topicId: topic.id).padding(.horizontal, -16).padding(.top, 8)
                }
                .padding(16)
            }
        }
    }
}
