import SwiftUI

// Port of feature/practice/QuizCatalogScreen.kt + weakspots/WeakSpotUi.kt.

func scoreColor(_ percent: Int) -> Color {
    percent >= 80 ? SimColors.green : percent >= 60 ? SimColors.amber : SimColors.red
}

struct QuizCatalogScreen: View {
    @Environment(AppStore.self) private var store
    @Environment(Router.self) private var router
    @Environment(\.palette) private var palette
    @AppStorage(quizLearnModeKey) private var learn = false

    var body: some View {
        let content = ContentStore.shared
        let results = WeakSpots.effectiveResults(store.questionResults, attempts: store.quizAttempts)
        let weakest = WeakSpots.weakest(WeakSpots.tagStats(results))
        let items = content.quizzes.compactMap { topicId, _ in content.topic(topicId).map { QuizSetItem.make($0, store: store) } }
        VStack(spacing: 0) {
        CompactNavBar(title: "Quizzes")
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 0) {
                WeakSpotCard(weakest: weakest, answeredTotal: results.count) { router.push(.weakSpotDrill) }
                    .padding(.bottom, 14)
                QuizModeToggle(learn: $learn).padding(.bottom, 20)
                Text("\(items.count) SETS").font(AppFont.sans(14, .bold)).tracking(1.2).foregroundStyle(palette.muted)
                    .padding(.leading, 4).padding(.bottom, 10)
                QuizSetCard(items: items, learn: learn)
            }
            .padding(.horizontal, screenGutter)
            .padding(.top, 8)
            .padding(.bottom, screenBottomInset)
        }
        }
    }
}

/// The weakest patterns in one compact card: a header row with the drill button, then a pill per pattern.
private struct WeakSpotCard: View {
    let weakest: [TagStat]
    let answeredTotal: Int
    let onDrill: () -> Void
    @Environment(\.palette) private var palette

    var body: some View {
        let accent = SimColors.red
        VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 11) {
                Image(systemName: "scope")
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundStyle(accent)
                    .frame(width: 36, height: 36)
                    .background(accent.opacity(0.16), in: RoundedRectangle(cornerRadius: 10))
                VStack(alignment: .leading, spacing: 1) {
                    Text("Your weak spots").font(AppFont.sans(16, .semibold))
                    Text(!weakest.isEmpty ? "Missed most · \(answeredTotal) answers"
                         : answeredTotal == 0 ? "Answer a few sets to see them here"
                         : "None yet — every pattern is at 80%+")
                        .font(AppFont.sans(13)).foregroundStyle(palette.muted).lineLimit(1)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                if !weakest.isEmpty {
                    Button(action: onDrill) {
                        HStack(spacing: 6) {
                            Image(systemName: "play.fill").font(.system(size: 11))
                            Text("Drill \(weakSpotDrillSize)").font(AppFont.sans(15, .semibold))
                        }
                        .foregroundStyle(.white)
                        .padding(.horizontal, 14)
                        .frame(height: 36)
                        .background(Color(hex: 0x3E8E5E), in: Capsule())
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel("Drill weak spots, \(weakSpotDrillSize) questions")
                }
            }
            if !weakest.isEmpty {
                FlowLayout(spacing: 6, lineSpacing: 6) {
                    ForEach(weakest, id: \.tag) { stat in
                        let color = scoreColor(stat.percent)
                        HStack(spacing: 6) {
                            Text(stat.tag).font(AppFont.sans(13, .medium)).foregroundStyle(palette.onSurface)
                            Text("\(stat.correct)/\(stat.answered)").font(AppFont.mono(12, .semibold)).foregroundStyle(color)
                        }
                        .padding(.horizontal, 10).padding(.vertical, 5)
                        .background(color.opacity(0.14), in: Capsule())
                    }
                }
            }
        }
        .padding(12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .card(radius: 16)
    }
}

/// Built once from settled values, then run through QuizScreen with every answer reporting back
/// to the set it came from.
struct WeakSpotDrillScreen: View {
    @Environment(AppStore.self) private var store
    @Environment(\.palette) private var palette
    @State private var built: (quiz: Quiz, questions: [DrillQuestion])?
    @State private var empty = false

    var body: some View {
        Group {
            if let built {
                QuizScreen(quizId: weakSpotQuizId, quiz: built.quiz,
                           questionKeys: built.questions.map { questionKey($0.quizId, $0.index) }, trackBest: false)
            } else {
                VStack(alignment: .leading, spacing: 0) {
                    ScreenHeader(title: "Weak-spot drill")
                    Text(empty ? "No questions to drill yet — finish a few sets first." : "Building your drill…")
                        .font(.bodyLarge).foregroundStyle(palette.muted).padding(16)
                    Spacer()
                }
            }
        }
        .onAppear {
            guard built == nil, !empty else { return }
            let results = WeakSpots.effectiveResults(store.questionResults, attempts: store.quizAttempts)
            let tags = WeakSpots.weakest(WeakSpots.tagStats(results)).map(\.tag)
            let picked = WeakSpots.pickQuestions(tags: Set(tags), results: results)
            if picked.isEmpty { empty = true } else { built = (WeakSpots.quiz(picked, tags: tags), picked) }
        }
    }
}
