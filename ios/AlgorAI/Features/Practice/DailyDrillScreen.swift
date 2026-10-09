import SwiftUI

// Port of feature/practice/daily/DailyDrillScreen.kt: recall, solve one problem, a short drill.
struct DailyDrillScreen: View {
    @Environment(AppStore.self) private var store
    @Environment(PremiumStore.self) private var premium
    @Environment(Router.self) private var router
    @Environment(\.palette) private var palette
    @State private var runningQuiz = false

    var body: some View {
        let status = store.drillStatus(premium: premium.isPremium)
        if runningQuiz && !status.questions.isEmpty {
            // Straight into the timed run: its recorded attempt marks the step done.
            QuizScreen(quizId: dailyDrillQuizId, quiz: DailyDrill.quiz(status.questions),
                       questionKeys: status.questions.map { questionKey($0.quizId, $0.index) }, offerLearnMode: false)
        } else {
            VStack(spacing: 0) {
                ScreenHeader(title: "Daily Drill")
                ScrollView {
                    VStack(alignment: .leading, spacing: 0) {
                        Text(status.allDone ? "Done for today — come back tomorrow." : "\(status.doneCount) of \(status.stepCount) done")
                            .font(.bodyMedium)
                            .foregroundStyle(status.allDone ? SimColors.green : palette.muted)
                            .padding(.bottom, 8)
                        ProgressView(value: Double(status.doneCount), total: Double(status.stepCount)).tint(palette.primary)
                            .padding(.bottom, 18)
                        DrillStepCard(index: 1, title: "Recall",
                                      subtitle: status.recallDone ? "Nothing waiting" : "\(status.cardsWaiting) cards waiting",
                                      icon: "stack", accent: Color(hex: 0xC084FC), done: status.recallDone, enabled: true) {
                            router.push(.review)
                        }
                        DrillStepCard(index: 2, title: "Solve", subtitle: status.problem?.title ?? "Whole bank solved",
                                      icon: "chip", accent: Color(hex: 0x60A5FA), done: status.solveDone, enabled: status.problem != nil) {
                            if let problem = status.problem { router.push(.problem(problem.id)) }
                        }
                        DrillStepCard(index: 3, title: "Drill",
                                      subtitle: status.questions.isEmpty ? "No questions available"
                                          : "\(status.questions.count) questions · \(status.questions.count * 45 / 60) min",
                                      icon: "help", accent: Color(hex: 0xFBBF24), done: status.drillDone, enabled: !status.questions.isEmpty) {
                            runningQuiz = true
                        }
                        Text("Questions you have missed before come first; the rest are ones no attempt has covered yet.")
                            .font(AppFont.sans(12)).foregroundStyle(palette.muted)
                            .padding(.top, 14).padding(.bottom, 28)
                    }
                    .padding(.horizontal, screenGutter)
                    .padding(.top, 12)
                }
            }
        }
    }
}

private struct DrillStepCard: View {
    let index: Int
    let title: String
    let subtitle: String
    let icon: String
    let accent: Color
    let done: Bool
    let enabled: Bool
    let action: () -> Void
    @Environment(\.palette) private var palette

    var body: some View {
        let green = SimColors.green
        Button(action: action) {
            HStack(spacing: 13) {
                AppIcon(name: icon, size: 22)
                    .foregroundStyle(accent)
                    .frame(width: 44, height: 44)
                    .background(accent.opacity(0.16), in: RoundedRectangle(cornerRadius: 14))
                VStack(alignment: .leading, spacing: 1) {
                    Text("STEP \(index)").font(AppFont.grotesk(10, .bold)).foregroundStyle(palette.muted)
                    Text(title).font(AppFont.grotesk(16, .bold))
                    Text(subtitle).font(.bodyMedium).foregroundStyle(palette.muted).multilineTextAlignment(.leading)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                Image(systemName: done ? "checkmark.circle.fill" : "chevron.right")
                    .font(.system(size: done ? 22 : 14, weight: .semibold))
                    .foregroundStyle(done ? green : palette.muted)
            }
            .padding(15)
            .background(done ? green.opacity(0.07) : palette.surface, in: RoundedRectangle(cornerRadius: 18))
            .overlay(RoundedRectangle(cornerRadius: 18).stroke(done ? green.opacity(0.4) : palette.outline, lineWidth: 1))
        }
        .buttonStyle(.plain)
        .disabled(!enabled)
        .padding(.vertical, 5)
    }
}
