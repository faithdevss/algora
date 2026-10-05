import SwiftUI

// The pieces the Quizzes and Interview Prep lists share: a large-title header with an iOS back link, the
// Interview / Learn switch that decides how every set on the page runs, and a grouped card of sets, each
// with its done mark, size, best score and a play button that starts it in that mode.

/// Which mode the lists start sets in; one choice shared by both pages.
let quizLearnModeKey = "quizListLearnMode"

/// "‹ Practice", then a 34pt title.
struct LargeTitleHeader: View {
    let title: String
    @Environment(Router.self) private var router
    @Environment(\.palette) private var palette
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        let back = router.backTitle
        VStack(alignment: .leading, spacing: 0) {
            Button { dismiss() } label: {
                HStack(spacing: 4) {
                    Image(systemName: "chevron.backward").font(.system(size: 19, weight: .semibold))
                    Text(back).font(AppFont.sans(17))
                }
                .foregroundStyle(palette.primary)
                .frame(minHeight: 44)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Back to \(back)")
            Text(title).font(AppFont.sans(34, .bold)).padding(.top, 2)
        }
    }
}

/// One 44pt row: "‹ Practice" on the left, the title centred — for lists that need the room below.
struct CompactNavBar: View {
    let title: String
    @Environment(Router.self) private var router
    @Environment(\.palette) private var palette
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        let back = router.backTitle
        ZStack {
            Text(title).font(AppFont.sans(17, .semibold)).lineLimit(1).padding(.horizontal, 110)
            HStack {
                Button { dismiss() } label: {
                    HStack(spacing: 4) {
                        Image(systemName: "chevron.backward").font(.system(size: 19, weight: .semibold))
                        Text(back).font(AppFont.sans(17)).lineLimit(1)
                    }
                    .foregroundStyle(palette.primary)
                    .frame(maxWidth: 110, minHeight: 44, alignment: .leading)
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Back to \(back)")
                Spacer()
            }
        }
        .padding(.horizontal, 8)
        .frame(height: 44)
        .padding(.top, 4)
        .background(palette.background)
    }
}

/// Interview (timed, scored) or Learn (untimed, explained), with what the choice means under it.
struct QuizModeToggle: View {
    @Binding var learn: Bool
    @Environment(\.palette) private var palette

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 4) {
                segment("Interview", icon: "timer", tint: palette.primary, on: !learn) { learn = false }
                segment("Learn", icon: "graduationcap", tint: SimColors.green, on: learn) { learn = true }
            }
            .padding(4)
            .background(SimColors.tint, in: RoundedRectangle(cornerRadius: 14))
            Text(learn ? "No timer. Each answer is checked with the reason straight away. Not added to your best score."
                 : "Timed like the real thing. Answers at the end, counts toward your best score.")
                .font(AppFont.sans(15)).foregroundStyle(palette.muted)
                .fixedSize(horizontal: false, vertical: true)
                .padding(.horizontal, 4)
        }
    }

    private func segment(_ title: String, icon: String, tint: Color, on: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack(spacing: 8) {
                Image(systemName: icon).font(.system(size: 16, weight: .semibold)).foregroundStyle(on ? tint : palette.muted)
                Text(title).font(AppFont.sans(17, .semibold)).foregroundStyle(on ? palette.onSurface : palette.muted)
            }
            .frame(maxWidth: .infinity, minHeight: 46)
            .background {
                if on {
                    RoundedRectangle(cornerRadius: 11).fill(palette.dark ? Color(hex: 0x3A3F4C) : .white)
                        .shadow(color: .black.opacity(0.12), radius: 2, y: 1)
                }
            }
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(on ? .isSelected : [])
    }
}

/// One row of a set list.
struct QuizSetItem: Identifiable {
    let topicId: String
    let title: String
    /// "15 questions · 11 min", in the accent; nil for a guide that is not a quiz.
    let meta: String?
    /// The guide's one-liner when there is no quiz.
    let note: String?
    let done: Bool
    let best: (text: String, percent: Int)?
    var id: String { topicId }
}

func relativeQuizDay(_ day: Int64, _ today: Int64) -> String {
    switch today - day {
    case 0: "today"
    case 1: "yesterday"
    case 2...30: "\(today - day)d ago"
    default: "a while ago"
    }
}

extension QuizSetItem {
    /// A set from the store's attempts and completions.
    @MainActor
    static func make(_ topic: Topic, store: AppStore) -> QuizSetItem {
        let today = todayEpochDay()
        guard let quiz = ContentStore.shared.quiz(topic.id) else {
            return QuizSetItem(topicId: topic.id, title: topic.name, meta: nil, note: topic.tagline,
                               done: store.completedTopicIds.contains(topic.id), best: nil)
        }
        let history = (store.quizAttempts[topic.id] ?? []).ofLength(quiz.questions.count)
        return QuizSetItem(
            topicId: topic.id, title: quiz.title,
            meta: "\(quiz.questions.count) questions · \(quiz.timeLimitSeconds / 60) min", note: nil,
            done: store.completedTopicIds.contains(topic.id) || !history.isEmpty,
            best: history.first.map { ("Best \(history.bestCorrect)/\(quiz.questions.count) · \(relativeQuizDay($0.day, today))", history.bestPercent) }
        )
    }
}

/// Rows in one card, divided; a row's play button runs its set in the list's mode.
struct QuizSetCard: View {
    let items: [QuizSetItem]
    let learn: Bool
    @Environment(Router.self) private var router
    @Environment(\.palette) private var palette

    var body: some View {
        VStack(spacing: 0) {
            ForEach(Array(items.enumerated()), id: \.element.id) { i, item in
                if i > 0 { Divider().overlay(palette.outline).padding(.leading, 64) }
                QuizSetRow(item: item) {
                    // A guide (behavioral bank, design primer) opens as a page; a quiz starts right away.
                    router.push(item.meta == nil ? .topic(item.topicId) : .quizRun(item.topicId, learn: learn))
                }
            }
        }
        .background(palette.surface, in: RoundedRectangle(cornerRadius: 18))
        .overlay(RoundedRectangle(cornerRadius: 18).stroke(palette.outline, lineWidth: 1))
    }
}

private struct QuizSetRow: View {
    let item: QuizSetItem
    let action: () -> Void
    @Environment(\.palette) private var palette

    var body: some View {
        Button(action: action) {
            HStack(spacing: 18) {
                ZStack {
                    if item.done {
                        Circle().fill(SimColors.green)
                        Image(systemName: "checkmark").font(.system(size: 13, weight: .bold)).foregroundStyle(.white)
                    } else {
                        Circle().stroke(palette.muted.opacity(0.45), lineWidth: 2)
                    }
                }
                .frame(width: 26, height: 26)
                VStack(alignment: .leading, spacing: 3) {
                    Text(item.title).font(AppFont.sans(18, .semibold)).foregroundStyle(palette.onSurface)
                        .multilineTextAlignment(.leading)
                    if let meta = item.meta {
                        Text(meta).font(AppFont.mono(14)).foregroundStyle(palette.primary)
                    } else if let note = item.note {
                        Text(note).font(AppFont.sans(14)).foregroundStyle(palette.muted).lineLimit(2).multilineTextAlignment(.leading)
                    }
                    if let best = item.best {
                        Text(best.text).font(AppFont.sans(15)).foregroundStyle(scoreColor(best.percent))
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                Image(systemName: item.meta == nil ? "chevron.right" : "play.fill")
                    .font(.system(size: item.meta == nil ? 15 : 14, weight: .semibold))
                    .foregroundStyle(palette.primary)
                    .frame(width: 48, height: 48)
                    .background(palette.primary.opacity(0.18), in: Circle())
            }
            .padding(.leading, 18)
            .padding(.trailing, 16)
            .padding(.vertical, 14)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(item.meta == nil ? "Open \(item.title)" : "Start \(item.title)")
    }
}

/// A quiz opened from a list: the set runs in the list's mode, and finishing marks the topic done.
struct QuizRunScreen: View {
    let topicId: String
    let learn: Bool
    @Environment(AppStore.self) private var store

    var body: some View {
        Group {
            if let quiz = ContentStore.shared.quiz(topicId) {
                QuizScreen(quizId: topicId, quiz: quiz, onFinish: { store.markCompleted(topicId) }, initialMode: learn ? .learn : .interview)
            } else {
                TopicDetailScreen(topicId: topicId)
            }
        }
        .onAppear { store.setLastOpened(topicId) }
    }
}
