import SwiftUI

// Port of feature/home/HomeScreen.kt: topbar with the DSA/AI switch, a rotating Featured
// Lab, Jump Back In, the 2×2 Quick Access grid and bookmarks.
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
                    FeaturedLabCard(topic: featured) { router.push(.topic(featured.id)) }
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
        VStack(spacing: 18) {
            HStack(spacing: 10) {
                VStack(alignment: .leading, spacing: 0) {
                    Text("Welcome back").font(AppFont.sans(15)).foregroundStyle(.white.opacity(0.9))
                    Text("AlgorAI").font(AppFont.grotesk(28, .bold)).foregroundStyle(.white)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                HStack(spacing: 6) {
                    Image(systemName: "flame.fill").font(.system(size: 15)).foregroundStyle(Color(hex: 0xF6A86B))
                    Text("\(max(store.streak, 1)) Day\(store.streak > 1 ? "s" : "")")
                        .font(AppFont.sans(15, .bold))
                        .foregroundStyle(.white)
                }
                .padding(.horizontal, 14)
                .padding(.vertical, 9)
                .glass(Capsule())
                Button { router.push(.settings) } label: {
                    Image(systemName: "gearshape")
                        .font(.system(size: 18))
                        .foregroundStyle(.white)
                        .frame(width: 40, height: 40)
                        .glass(Circle())
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Settings")
            }
            HStack(spacing: 4) {
                modeTab("DSA", .dsa)
                modeTab("AI", .ai)
            }
            .padding(4)
            .background(Color(hex: 0x2A1A6E).opacity(0.22), in: RoundedRectangle(cornerRadius: 14))
            .overlay(RoundedRectangle(cornerRadius: 14).stroke(.white.opacity(0.18), lineWidth: 1))
        }
        .padding(18)
        .background(LinearGradient(colors: palette.theme.featured, startPoint: .topLeading, endPoint: .bottomTrailing))
        .clipShape(RoundedRectangle(cornerRadius: 24))
        .overlay(RoundedRectangle(cornerRadius: 24).stroke(.white.opacity(0.3), lineWidth: 1))
        .padding(.top, 12)
    }

    private func modeTab(_ label: String, _ mode: AppMode) -> some View {
        let selected = session.mode == mode
        return Button {
            withAnimation(.easeInOut(duration: 0.32)) { session.mode = mode }
        } label: {
            Text(label)
                .font(AppFont.sans(15, .bold))
                .foregroundStyle(selected ? palette.theme.featured[1] : .white)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 11)
                .background(selected ? Color.white : .clear, in: RoundedRectangle(cornerRadius: 11))
                // A clear fill doesn't hit-test, so claim the whole tab for the tap.
                .contentShape(RoundedRectangle(cornerRadius: 11))
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

/// Oversized, low-opacity echo of a card's icon, tilted and bleeding off its bottom-right corner —
/// the motif the Home cards share. The caller clips the card, which crops the overhang.
private struct EchoIcon: View {
    let name: String
    let size: CGFloat

    var body: some View {
        AppIcon(name: name, size: size)
            .foregroundStyle(.white.opacity(0.14))
            .rotationEffect(.degrees(-18))
            .offset(x: size * 0.24, y: size * 0.24)
    }
}

private struct QuickAccessCard: View {
    let card: QuickCard
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            ZStack(alignment: .bottomTrailing) {
                EchoIcon(name: card.icon, size: 108)
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

private extension View {
    /// Frosted chip on the topbar: a faint white fill under a brighter hairline.
    func glass(_ shape: some Shape) -> some View {
        background(.white.opacity(0.2), in: shape)
            .overlay(shape.stroke(.white.opacity(0.35), lineWidth: 1))
    }
}

private struct FeaturedLabCard: View {
    let topic: Topic
    let action: () -> Void

    @Environment(\.palette) private var palette

    var body: some View {
        Button(action: action) {
            HStack(alignment: .top, spacing: 12) {
                VStack(alignment: .leading, spacing: 0) {
                    Text("FEATURED LAB")
                        .font(AppFont.sans(11, .bold)).tracking(2)
                        .foregroundStyle(.white)
                    Text(topic.name)
                        .font(AppFont.grotesk(24, .bold)).foregroundStyle(.white)
                        .padding(.top, 6)
                        .multilineTextAlignment(.leading)
                    Text(topic.tagline).font(AppFont.sans(15)).foregroundStyle(.white.opacity(0.92))
                        .multilineTextAlignment(.leading)
                        .padding(.top, 4)
                    HStack(spacing: 4) {
                        Text("Open interactive").font(AppFont.sans(15, .bold))
                        Image(systemName: "chevron.right").font(.system(size: 13, weight: .bold))
                    }
                    .foregroundStyle(palette.theme.featured[1])
                    .padding(.leading, 20).padding(.trailing, 16)
                    .padding(.vertical, 11)
                    .background(.white, in: RoundedRectangle(cornerRadius: 14))
                    .padding(.top, 16)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                // Frosted tile holding the card's own mark, the same whichever lab it features.
                FeaturedLabMark()
                    .frame(width: 30, height: 30)
                    .frame(width: 64, height: 64)
                    .background(.white.opacity(0.18), in: RoundedRectangle(cornerRadius: 18))
                    .overlay(RoundedRectangle(cornerRadius: 18).stroke(.white.opacity(0.35), lineWidth: 1))
            }
            .padding(20)
            // The theme's icon ramp: brighter than the deep topbar above it, so the two read apart.
            .background(LinearGradient(colors: [palette.theme.headerStart, palette.theme.headerEnd], startPoint: .topLeading, endPoint: .bottomTrailing))
            .clipShape(RoundedRectangle(cornerRadius: 22))
            .overlay(RoundedRectangle(cornerRadius: 22).stroke(.white.opacity(0.22), lineWidth: 1))
        }
        .buttonStyle(.plain)
    }
}

/// The Featured Lab card's own mark: an outlined flask with a liquid line, on a 24-unit grid.
private struct FeaturedLabMark: View {
    var body: some View {
        Canvas { ctx, size in
            let u = size.width / 24
            func p(_ x: CGFloat, _ y: CGFloat) -> CGPoint { CGPoint(x: x * u, y: y * u) }
            let style = StrokeStyle(lineWidth: 1.8 * u, lineCap: .round, lineJoin: .round)
            var flask = Path()
            flask.move(to: p(10, 3))
            flask.addLine(to: p(10, 9))
            flask.addLine(to: p(4.6, 18.2))
            flask.addQuadCurve(to: p(6.3, 21), control: p(3.6, 21))
            flask.addLine(to: p(17.7, 21))
            flask.addQuadCurve(to: p(19.4, 18.2), control: p(20.4, 21))
            flask.addLine(to: p(14, 9))
            flask.addLine(to: p(14, 3))
            flask.move(to: p(8.5, 3))
            flask.addLine(to: p(15.5, 3))
            flask.move(to: p(6.6, 15))
            flask.addLine(to: p(17.4, 15))
            ctx.stroke(flask, with: .color(.white), style: style)
        }
        .accessibilityHidden(true)
    }
}
