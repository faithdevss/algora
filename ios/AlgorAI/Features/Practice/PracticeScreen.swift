import SwiftUI

// Port of feature/practice/PracticeScreen.kt: the Practice tab — everything the learner *does*.
struct PracticeScreen: View {
    @Environment(AppStore.self) private var store
    @Environment(Router.self) private var router
    @Environment(\.palette) private var palette

    var body: some View {
        let content = ContentStore.shared
        let solved = store.solvedProblemIds.filter { content.problem($0) != nil }.count
        let counts = store.reviewCounts()
        let cardSubtitle = counts.waiting > 0 ? "\(counts.waiting) to review · \(counts.new) new"
            : counts.newHeldBack > 0 ? "Done for today" : "All caught up"
        let entries: [(Route, String, String, String, [Color])] = [
            (.problems, "Problem Solving", "\(content.problems.count) problems · \(solved) solved", "code", Gradients.blue),
            (.quizzes, "Quizzes", "\(content.quizzes.count) timed sets", "quiz", Gradients.amber),
            (.review, "Flashcards", cardSubtitle, "cards", Gradients.violet),
            (.browser(.interviewPrep), "Interview Prep", "Company sets, mock rounds, AI rounds", "mic", Gradients.pink),
        ]

        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                VStack(alignment: .leading, spacing: 3) {
                    Text("Practice").font(AppFont.grotesk(22, .bold))
                    Text("Recall, drill and solve").font(.bodyMedium).foregroundStyle(palette.muted)
                }
                .padding(.top, 16)
                .padding(.bottom, 14)

                DailyDrillBanner { router.push(.dailyDrill) }
                    .padding(.bottom, 13)

                LazyVGrid(columns: [GridItem(.flexible(), spacing: 13), GridItem(.flexible(), spacing: 13)], spacing: 13) {
                    ForEach(entries, id: \.1) { route, title, subtitle, icon, gradient in
                        GradientTile(title: title, subtitle: subtitle, icon: icon, gradient: gradient) { router.push(route) }
                    }
                }
            }
            .padding(.horizontal, screenGutter)
            .padding(.bottom, 24)
        }
    }
}

/// Home's Quick Access card shape: gradient tile, icon chip, oversized faded echo of the icon.
struct GradientTile: View {
    let title: String
    let subtitle: String
    let icon: String
    let gradient: [Color]
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            ZStack(alignment: .bottomTrailing) {
                AppIcon(name: icon, size: 108)
                    .foregroundStyle(.white.opacity(0.14))
                    .rotationEffect(.degrees(-18))
                    .offset(x: 26, y: 26)
                VStack(alignment: .leading, spacing: 0) {
                    AppIcon(name: icon, size: 24)
                        .foregroundStyle(.white)
                        .frame(width: 44, height: 44)
                        .background(.white.opacity(0.22), in: RoundedRectangle(cornerRadius: 14))
                    Spacer(minLength: 12)
                    Text(title).font(AppFont.grotesk(16.5, .bold)).foregroundStyle(.white).multilineTextAlignment(.leading)
                    Text(subtitle).font(AppFont.sans(12.5)).foregroundStyle(.white.opacity(0.82)).multilineTextAlignment(.leading)
                }
                .padding(16)
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .leading)
            }
            .frame(minHeight: 150)
            .background(LinearGradient(colors: gradient, startPoint: .topLeading, endPoint: .bottomTrailing))
            .clipShape(RoundedRectangle(cornerRadius: 20))
        }
        .buttonStyle(.plain)
    }
}

private struct DailyDrillBanner: View {
    let action: () -> Void
    @Environment(AppStore.self) private var store
    @Environment(\.palette) private var palette

    var body: some View {
        let status = store.drillStatus()
        let accent = status.allDone ? SimColors.green : Color(hex: 0x6366F1)
        Button(action: action) {
            HStack(spacing: 13) {
                AppIcon(name: "flame", size: 24)
                    .foregroundStyle(accent)
                    .frame(width: 44, height: 44)
                    .background(accent.opacity(0.18), in: RoundedRectangle(cornerRadius: 14))
                VStack(alignment: .leading, spacing: 2) {
                    Text("Daily Drill").font(AppFont.grotesk(16, .bold))
                    Text(status.allDone ? "Done for today ✓" : "\(status.doneCount) of \(status.stepCount) done · recall, solve, drill")
                        .font(.bodyMedium)
                        .foregroundStyle(status.allDone ? accent : palette.muted)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                Image(systemName: "chevron.right").foregroundStyle(accent)
            }
            .padding(16)
            .background(accent.opacity(0.08), in: RoundedRectangle(cornerRadius: 20))
            .overlay(RoundedRectangle(cornerRadius: 20).stroke(accent.opacity(0.45), lineWidth: 1))
        }
        .buttonStyle(.plain)
    }
}
