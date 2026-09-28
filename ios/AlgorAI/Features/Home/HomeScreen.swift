import SwiftUI

// Port of feature/home/HomeScreen.kt: gradient topbar with the DSA/AI switch, a rotating Featured
// Lab, Jump Back In, the 2×2 Quick Access grid and bookmarks. The premium upsell is left out —
// iOS has no payments, so everything is already open.
struct HomeScreen: View {
    @Environment(AppStore.self) private var store
    @Environment(AppSession.self) private var session
    @Environment(Router.self) private var router
    @Environment(\.palette) private var palette

    private let content = ContentStore.shared

    var body: some View {
        @Bindable var session = session
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                topbar
                if let featured = pickFeatured() {
                    FeaturedLabCard(mode: session.mode, topic: featured) { router.push(.topic(featured.id)) }
                        .padding(.top, 14)
                }
                if let id = store.lastOpened, let topic = content.topic(id) {
                    SectionTitle("Jump Back In")
                    Button { router.push(.topic(topic.id)) } label: { continueRow(topic) }
                        .buttonStyle(.plain)
                }
                Text("Quick Access")
                    .font(AppFont.grotesk(20, .bold))
                    .padding(.top, 22)
                    .padding(.bottom, 14)
                LazyVGrid(columns: [GridItem(.flexible(), spacing: 13), GridItem(.flexible(), spacing: 13)], spacing: 13) {
                    ForEach(session.mode == .dsa ? QuickCard.dsa : QuickCard.ai, id: \.kind) { card in
                        QuickAccessCard(card: card) { router.push(.browser(card.kind)) }
                    }
                }
                let bookmarked = store.bookmarks.sorted().compactMap { content.topic($0) }
                if !bookmarked.isEmpty {
                    SectionTitle("Bookmarks")
                    VStack(spacing: 10) {
                        ForEach(bookmarked) { topic in
                            Button { router.push(.topic(topic.id)) } label: { bookmarkRow(topic) }
                                .buttonStyle(.plain)
                        }
                    }
                }
            }
            .padding(.horizontal, screenGutter)
            .padding(.bottom, 24)
        }
    }

    private var topbar: some View {
        VStack(spacing: 16) {
            HStack(spacing: 9) {
                VStack(alignment: .leading, spacing: 0) {
                    Text("Welcome back").font(AppFont.sans(12)).foregroundStyle(.white.opacity(0.7))
                    Text("AlgorAI").font(AppFont.grotesk(22, .bold)).foregroundStyle(.white)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                Button { router.push(.settings) } label: {
                    Image(systemName: "gearshape.fill")
                        .font(.system(size: 16))
                        .foregroundStyle(.white)
                        .frame(width: 38, height: 38)
                        .background(.white.opacity(0.16), in: RoundedRectangle(cornerRadius: 12))
                }
                .accessibilityLabel("Settings")
                HStack(spacing: 5) {
                    Image(systemName: "flame.fill").font(.system(size: 13)).foregroundStyle(Color(hex: 0xFB923C))
                    Text("\(max(store.streak, 1)) Day\(store.streak > 1 ? "s" : "")")
                        .font(AppFont.sans(13, .semibold))
                        .foregroundStyle(.white)
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 8)
                .background(.black.opacity(0.18), in: RoundedRectangle(cornerRadius: 12))
            }
            HStack(spacing: 4) {
                modeTab("DSA", .dsa)
                modeTab("AI", .ai)
            }
            .padding(4)
            .background(.black.opacity(0.2), in: RoundedRectangle(cornerRadius: 14))
        }
        .padding(18)
        .background(
            LinearGradient(colors: [palette.accent.topbarStart, palette.accent.topbarEnd], startPoint: .topLeading, endPoint: .bottomTrailing),
            in: RoundedRectangle(cornerRadius: 22)
        )
        .padding(.top, 12)
    }

    private func modeTab(_ label: String, _ mode: AppMode) -> some View {
        let selected = session.mode == mode
        return Button {
            withAnimation(.easeInOut(duration: 0.32)) { session.mode = mode }
        } label: {
            Text(label)
                .font(AppFont.sans(13.5, .bold))
                .foregroundStyle(selected ? Color(hex: 0x161A22) : .white.opacity(0.85))
                .frame(maxWidth: .infinity)
                .padding(.vertical, 9)
                .background(selected ? Color.white : .clear, in: RoundedRectangle(cornerRadius: 10))
        }
        .buttonStyle(.plain)
    }

    private func continueRow(_ topic: Topic) -> some View {
        HStack(spacing: 13) {
            AppIcon(name: topic.iconName, size: 22)
                .foregroundStyle(Color(argb: topic.accentColor))
                .frame(width: 44, height: 44)
                .background(Color(argb: topic.accentColor).opacity(0.14), in: RoundedRectangle(cornerRadius: 13))
            VStack(alignment: .leading, spacing: 2) {
                Text(topic.name).font(.titleMedium)
                Text(topic.tagline).font(.bodyMedium).foregroundStyle(palette.muted).lineLimit(2)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            Image(systemName: "chevron.right").foregroundStyle(palette.muted)
        }
        .padding(15)
        .card(radius: 16)
    }

    private func bookmarkRow(_ topic: Topic) -> some View {
        HStack(spacing: 12) {
            Image(systemName: "bookmark.fill").foregroundStyle(Color(argb: topic.accentColor))
            Text(topic.name).font(.bodyLarge).frame(maxWidth: .infinity, alignment: .leading)
            Image(systemName: "chevron.right").font(.system(size: 13, weight: .semibold)).foregroundStyle(palette.muted)
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 13)
        .card()
    }

    /// Rotates once a day through the mode's runnable labs the user has not finished; nil once
    /// every one is done, and the card disappears.
    private func pickFeatured() -> Topic? {
        let sections: [Section] = session.mode == .dsa
            ? [.DATA_STRUCTURES, .ALGORITHMS, .ANALYSIS, .INTERVIEW_PREP]
            : [.ML, .DL, .NLP, .RL]
        let modeIds = Set(sections.flatMap { content.topics(in: $0).map(\.id) })
        let pool = content.runnableSimulations
            .compactMap { content.topic($0.topicId) }
            .filter { modeIds.contains($0.id) && !store.completedTopicIds.contains($0.id) }
        guard !pool.isEmpty else { return nil }
        return pool[Int(todayEpochDay() % Int64(pool.count))]
    }
}

private struct QuickCard {
    let kind: BrowserKind
    let title: String
    let sub: String
    let icon: String
    let gradient: [Color]

    static let dsa = [
        QuickCard(kind: .dataStructures, title: "Data Structures", sub: "Build your foundation", icon: "tree", gradient: Gradients.green),
        QuickCard(kind: .algorithms, title: "Algorithms", sub: "Master problem solving", icon: "functions", gradient: Gradients.blue),
        QuickCard(kind: .patterns, title: "Patterns", sub: "Recognise the shape", icon: "bulb", gradient: Gradients.amber),
        QuickCard(kind: .analysis, title: "Analysis", sub: "Time & Space efficiency", icon: "trend", gradient: Gradients.violet),
    ]

    static let ai = [
        QuickCard(kind: .machineLearning, title: "Machine Learning", sub: "Learn from data", icon: "robot", gradient: Gradients.indigo),
        QuickCard(kind: .deepLearning, title: "Deep Learning", sub: "Neural networks", icon: "network", gradient: Gradients.pink),
        QuickCard(kind: .nlp, title: "NLP", sub: "Language & text", icon: "translate", gradient: Gradients.teal),
        QuickCard(kind: .reinforcementLearning, title: "Reinforcement Learning", sub: "Trial & error", icon: "game", gradient: Gradients.orange),
    ]
}

private struct QuickAccessCard: View {
    let card: QuickCard
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            ZStack(alignment: .bottomTrailing) {
                AppIcon(name: card.icon, size: 108)
                    .foregroundStyle(.white.opacity(0.14))
                    .rotationEffect(.degrees(-18))
                    .offset(x: 26, y: 26)
                VStack(alignment: .leading, spacing: 0) {
                    AppIcon(name: card.icon, size: 24)
                        .foregroundStyle(.white)
                        .frame(width: 44, height: 44)
                        .background(.white.opacity(0.22), in: RoundedRectangle(cornerRadius: 14))
                    Spacer(minLength: 12)
                    Text(card.title).font(AppFont.grotesk(16.5, .bold)).foregroundStyle(.white)
                        .multilineTextAlignment(.leading)
                    Text(card.sub).font(AppFont.sans(12.5)).foregroundStyle(.white.opacity(0.82))
                }
                .padding(16)
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .leading)
            }
            .frame(minHeight: 150)
            .background(LinearGradient(colors: card.gradient, startPoint: .topLeading, endPoint: .bottomTrailing))
            .clipShape(RoundedRectangle(cornerRadius: 20))
        }
        .buttonStyle(.plain)
    }
}

private struct FeaturedLabCard: View {
    let mode: AppMode
    let topic: Topic
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack {
                VStack(alignment: .leading, spacing: 0) {
                    Text("FEATURED LAB")
                        .font(AppFont.sans(11, .bold)).tracking(1.5)
                        .foregroundStyle(.white.opacity(0.75))
                    Text(topic.name)
                        .font(AppFont.grotesk(20, .bold)).foregroundStyle(.white)
                        .padding(.top, 3)
                        .multilineTextAlignment(.leading)
                    Text(topic.tagline).font(AppFont.sans(13)).foregroundStyle(.white.opacity(0.82))
                        .multilineTextAlignment(.leading)
                    HStack(spacing: 2) {
                        Text("Open interactive").font(AppFont.sans(13, .bold))
                        Image(systemName: "chevron.right").font(.system(size: 12, weight: .bold))
                    }
                    .foregroundStyle(Color(hex: 0x161A22))
                    .padding(.horizontal, 15)
                    .padding(.vertical, 8)
                    .background(.white, in: RoundedRectangle(cornerRadius: 11))
                    .padding(.top, 13)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                AppIcon(name: topic.iconName, size: 36)
                    .foregroundStyle(.white)
                    .frame(width: 74, height: 74)
                    .background(.white.opacity(0.16), in: RoundedRectangle(cornerRadius: 20))
            }
            .padding(18)
            .background(
                LinearGradient(colors: mode == .dsa ? Gradients.featuredDsa : Gradients.featuredAi, startPoint: .topLeading, endPoint: .bottomTrailing),
                in: RoundedRectangle(cornerRadius: 22)
            )
        }
        .buttonStyle(.plain)
    }
}
