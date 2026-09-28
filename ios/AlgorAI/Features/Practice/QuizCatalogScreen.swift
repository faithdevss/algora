import SwiftUI

// Port of feature/practice/QuizCatalogScreen.kt + weakspots/WeakSpotUi.kt.

func scoreColor(_ percent: Int) -> Color {
    percent >= 80 ? SimColors.green : percent >= 60 ? SimColors.amber : SimColors.red
}

private func relativeDay(_ day: Int64, _ today: Int64) -> String {
    switch today - day {
    case 0: "today"
    case 1: "yesterday"
    case 2...30: "\(today - day)d ago"
    default: "a while ago"
    }
}

struct QuizCatalogScreen: View {
    @Environment(AppStore.self) private var store
    @Environment(Router.self) private var router
    @Environment(\.palette) private var palette

    var body: some View {
        let entries = ContentStore.shared.quizzes
        let results = WeakSpots.effectiveResults(store.questionResults, attempts: store.quizAttempts)
        let weakest = WeakSpots.weakest(WeakSpots.tagStats(results))
        let today = todayEpochDay()
        VStack(spacing: 0) {
            ScreenHeader(title: "Quizzes")
            ScrollView {
                LazyVStack(alignment: .leading, spacing: 0) {
                    WeakSpotCard(weakest: weakest, answeredTotal: results.count) { router.push(.weakSpotDrill) }
                        .padding(.bottom, 14)
                    Text("\(entries.count) timed sets — the clock auto-submits when it runs out")
                        .font(.bodyMedium).foregroundStyle(palette.muted)
                        .padding(.leading, 4).padding(.bottom, 10)
                    ForEach(entries, id: \.topicId) { topicId, quiz in
                        let history = (store.quizAttempts[topicId] ?? []).ofLength(quiz.questions.count)
                        QuizRow(
                            quiz: quiz,
                            history: history.first.map { "Best \(history.bestCorrect)/\(quiz.questions.count) · last \(relativeDay($0.day, today))" },
                            bestPercent: history.bestPercent
                        ) { router.push(.topic(topicId)) }
                        .padding(.vertical, 5)
                    }
                }
                .padding(.horizontal, screenGutter)
                .padding(.top, 8)
                .padding(.bottom, screenBottomInset)
            }
        }
    }
}

private struct QuizRow: View {
    let quiz: Quiz
    let history: String?
    let bestPercent: Int
    let action: () -> Void
    @Environment(\.palette) private var palette

    var body: some View {
        let accent = SimColors.amber
        Button(action: action) {
            HStack(spacing: 13) {
                AppIcon(name: "help", size: 21)
                    .foregroundStyle(accent)
                    .frame(width: 42, height: 42)
                    .background(accent.opacity(0.14), in: RoundedRectangle(cornerRadius: 13))
                VStack(alignment: .leading, spacing: 1) {
                    Text(quiz.title).font(.titleMedium)
                    Text("\(quiz.questions.count) questions · \(quiz.timeLimitSeconds / 60) min").font(AppFont.sans(12)).foregroundStyle(accent)
                    Text(quiz.description).font(.bodyMedium).foregroundStyle(palette.muted)
                    if let history {
                        Text(history).font(AppFont.sans(12)).foregroundStyle(scoreColor(bestPercent)).padding(.top, 2)
                    }
                }
                .multilineTextAlignment(.leading)
                .frame(maxWidth: .infinity, alignment: .leading)
                if history != nil {
                    Text("\(bestPercent)%")
                        .font(AppFont.sans(12, .bold))
                        .foregroundStyle(scoreColor(bestPercent))
                        .padding(.horizontal, 8).padding(.vertical, 4)
                        .background(scoreColor(bestPercent).opacity(0.14), in: RoundedRectangle(cornerRadius: 9))
                }
                Image(systemName: "chevron.right").font(.system(size: 13, weight: .semibold)).foregroundStyle(palette.muted)
            }
            .padding(14)
            .card(radius: 16)
        }
        .buttonStyle(.plain)
    }
}

/// The weakest patterns with a bar each, and one button that builds a drill from them.
private struct WeakSpotCard: View {
    let weakest: [TagStat]
    let answeredTotal: Int
    let onDrill: () -> Void
    @Environment(\.palette) private var palette

    var body: some View {
        let accent = SimColors.red
        VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: 13) {
                Image(systemName: "scope")
                    .font(.system(size: 20))
                    .foregroundStyle(accent)
                    .frame(width: 44, height: 44)
                    .background(accent.opacity(0.14), in: RoundedRectangle(cornerRadius: 13))
                VStack(alignment: .leading, spacing: 2) {
                    Text("Your weak spots").font(.titleMedium)
                    Text(!weakest.isEmpty ? "The patterns you miss most, from \(answeredTotal) answers"
                         : answeredTotal == 0 ? "Answer a few sets and your weakest patterns show up here"
                         : "No weak pattern yet — every pattern with 3+ answers is at 80% or better")
                        .font(.bodyMedium).foregroundStyle(palette.muted)
                }
            }
            if !weakest.isEmpty {
                VStack(spacing: 9) {
                    ForEach(weakest, id: \.tag) { stat in
                        let color = scoreColor(stat.percent)
                        VStack(alignment: .leading, spacing: 4) {
                            HStack {
                                Text(stat.tag).font(.bodyMedium)
                                Spacer()
                                Text("\(stat.correct)/\(stat.answered) · \(stat.percent)%").font(AppFont.sans(12, .bold)).foregroundStyle(color)
                            }
                            GeometryReader { geo in
                                ZStack(alignment: .leading) {
                                    RoundedRectangle(cornerRadius: 3).fill(color.opacity(0.14))
                                    RoundedRectangle(cornerRadius: 3).fill(color).frame(width: geo.size.width * CGFloat(stat.percent) / 100)
                                }
                            }
                            .frame(height: 6)
                        }
                    }
                }
                .padding(.top, 12)
                PrimaryButton(title: "Drill weak spots · \(weakSpotDrillSize) questions", action: onDrill).padding(.top, 14)
            }
        }
        .padding(15)
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
