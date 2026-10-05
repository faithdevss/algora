import StoreKit
import SwiftUI

// Port of feature/interviewprep/quiz/QuizScreen.kt: a mode chooser (Interview / Learn), a timed or
// untimed run, and a results review with "retry missed". The quiz-exit interstitial and the
// premium nudge are Android-only (no ads or payments on iOS).

private let correctGreen = SimColors.green
private let wrongRed = SimColors.red

struct QuizScreen: View {
    let quizId: String
    let quiz: Quiz
    var onFinish: () -> Void = {}
    /// Where each question came from; drills pass the sets their questions were sampled from.
    var questionKeys: [String]?
    /// Ask Learn or Interview first. Off for the daily drill.
    var offerLearnMode = true
    /// Record timed runs and show the best score. Off for sets rebuilt on every open.
    var trackBest = true
    /// Start in this mode and skip the chooser — the list it was opened from already asked.
    var initialMode: QuizMode? = nil

    @Environment(AppStore.self) private var store
    @Environment(\.requestReview) private var requestReview

    @State private var mode: QuizMode?
    @State private var running: Quiz?
    @State private var runningKeys: [String] = []
    @State private var isFullQuiz = true
    @State private var attempt = 0
    @State private var recordedAt: Int64?

    private var baseKeys: [String] { questionKeys ?? quiz.questions.indices.map { questionKey(quizId, $0) } }
    private var history: [QuizAttempt] { (store.quizAttempts[quizId] ?? []).ofLength(quiz.questions.count) }

    var body: some View {
        Group {
            if let mode = mode ?? initialMode ?? (offerLearnMode ? nil : .interview) {
                let scored = isFullQuiz && mode == .interview && trackBest
                QuizRunner(
                    source: running ?? quiz,
                    mode: mode,
                    priorAttempts: scored ? history.filter { $0.atEpochSec != recordedAt } : [],
                    onComplete: { complete($0, scored: scored) },
                    onRetry: {
                        running = quiz
                        runningKeys = baseKeys
                        isFullQuiz = true
                        recordedAt = nil
                        attempt += 1
                    },
                    onRetryWrong: { wrong in
                        let source = running ?? quiz
                        let keys = runningKeys.isEmpty ? baseKeys : runningKeys
                        runningKeys = wrong.map { keys[$0] }
                        running = Quiz(id: "\(source.id)_missed", title: "\(source.title) · missed", description: source.description,
                                       questions: wrong.map { source.questions[$0] })
                        isFullQuiz = false
                        attempt += 1
                    }
                )
                .id(attempt)
            } else {
                QuizModeChooser(quiz: quiz, trackBest: trackBest, history: trackBest ? history : []) { mode = $0 }
            }
        }
    }

    private func complete(_ result: QuizAttempt, scored: Bool) {
        // Every run reports each answer to the question it came from — weak spots and the mistake
        // flashcards read that.
        let keys = runningKeys.isEmpty ? baseKeys : runningKeys
        let wrong = Set(result.wrongIndices)
        store.recordQuestionResults(Dictionary(keys.enumerated().map { ($1, !wrong.contains($0)) }, uniquingKeysWith: { _, b in b }))
        guard isFullQuiz else { return }
        store.recordQuizFinished()
        if scored {
            recordedAt = result.atEpochSec
            store.recordQuizAttempt(quizId, result)
            Task {
                // Let the score land on screen before the rating sheet slides over it.
                try? await Task.sleep(for: .seconds(1.2))
                ReviewPrompt.maybeAskAfterQuiz(store: store, scorePercent: result.percent, requestReview: requestReview)
            }
        }
        onFinish()
    }
}

private struct QuizRunner: View {
    let source: Quiz
    let mode: QuizMode
    let priorAttempts: [QuizAttempt]
    let onComplete: (QuizAttempt) -> Void
    let onRetry: () -> Void
    let onRetryWrong: ([Int]) -> Void

    @Environment(\.palette) private var palette
    @State private var quiz: Quiz?
    @State private var answers: [Int?] = []
    @State private var checked: [Bool] = []
    @State private var index = 0
    @State private var remaining = 0
    @State private var elapsed = 0
    @State private var finished = false
    @State private var result: QuizAttempt?

    private var learning: Bool { mode == .learn }

    var body: some View {
        Group {
            if let quiz, !answers.isEmpty {
                if finished, let result {
                    QuizResults(quiz: quiz, answers: answers, result: result, priorAttempts: priorAttempts, mode: mode,
                                onRetry: onRetry, onRetryWrong: onRetryWrong)
                } else {
                    questionView(quiz)
                }
            } else {
                Color.clear
            }
        }
        .onAppear {
            // Shuffled once per run so paging back finds the options where the learner left them.
            guard quiz == nil else { return }
            let shuffled = source.withShuffledOptions()
            quiz = shuffled
            answers = Array(repeating: nil, count: shuffled.questions.count)
            checked = Array(repeating: false, count: shuffled.questions.count)
            remaining = shuffled.timeLimitSeconds
        }
        .task(id: finished) {
            // Interview counts down and auto-submits at zero; Learn only counts up.
            while !finished && (learning || remaining > 0) {
                try? await Task.sleep(for: .seconds(1))
                if Task.isCancelled || finished { return }
                elapsed += 1
                if !learning { remaining -= 1 }
            }
            if !learning && remaining <= 0 && !finished { finish() }
        }
    }

    private func finish() {
        guard let quiz, !finished else { return }
        let wrong = quiz.questions.indices.filter { answers[$0] != quiz.questions[$0].correctIndex }
        let attempt = QuizAttempt(
            atEpochSec: Int64(Date().timeIntervalSince1970),
            correct: quiz.questions.count - wrong.count,
            total: quiz.questions.count,
            seconds: learning ? elapsed : quiz.timeLimitSeconds - remaining,
            wrongIndices: wrong
        )
        result = attempt
        finished = true
        onComplete(attempt)
    }

    private func questionView(_ quiz: Quiz) -> some View {
        let question = quiz.questions[index]
        let isChecked = learning && checked[index]
        return VStack(spacing: 0) {
            ScreenHeader(title: quiz.title) {
                if learning { LearnPill() } else { TimerPill(remaining: remaining) }
            }
            ScrollViewReader { proxy in
                ScrollView {
                    VStack(alignment: .leading, spacing: 0) {
                        Text("Question \(index + 1) of \(quiz.questions.count)")
                            .font(.bodyMedium).foregroundStyle(palette.muted)
                            .padding(.top, 12).padding(.bottom, 6)
                            .id("top")
                        ProgressView(value: Double(index + 1), total: Double(quiz.questions.count))
                            .tint(palette.primary)
                        HStack(spacing: 8) {
                            TagChip(text: question.patternTag, color: palette.primary)
                            DifficultyBadge(difficulty: question.difficulty)
                        }
                        .padding(.top, 14)
                        if let story = question.story {
                            StoryCard(story: story).padding(.top, 14)
                            if let figure = question.figure { FigureCard(figure: figure).padding(.top, 8) }
                        }
                        Text(question.prompt).font(.titleMedium).padding(.top, 14).padding(.bottom, 4)
                        if question.story == nil, let figure = question.figure {
                            FigureCard(figure: figure).padding(.top, 8)
                        }
                        VStack(spacing: 10) {
                            ForEach(Array(question.options.enumerated()), id: \.offset) { i, option in
                                OptionCard(
                                    text: option,
                                    selected: answers[index] == i,
                                    result: !isChecked ? nil : i == question.correctIndex ? true : answers[index] == i ? false : nil
                                ) {
                                    if !isChecked { answers[index] = i }
                                }
                            }
                        }
                        .padding(.top, 8)
                        if isChecked {
                            LearnFeedback(question: question, correct: answers[index] == question.correctIndex)
                                .padding(.top, 14).padding(.bottom, 8)
                                .id("feedback")
                        }
                    }
                    .padding(.horizontal, 16)
                }
                .onChange(of: index) { proxy.scrollTo("top", anchor: .top) }
                .onChange(of: isChecked) { _, now in
                    // The explanation lands below the fold on figure questions; bring it into view.
                    if now { withAnimation { proxy.scrollTo("feedback", anchor: .bottom) } }
                }
            }
            HStack(spacing: 10) {
                if index > 0 {
                    SecondaryButton(title: "Previous") { index -= 1 }
                }
                if learning && !checked[index] {
                    PrimaryButton(title: "Check", enabled: answers[index] != nil) { checked[index] = true }
                } else {
                    PrimaryButton(title: index < quiz.questions.count - 1 ? "Next" : "Finish") {
                        if index < quiz.questions.count - 1 { index += 1 } else { finish() }
                    }
                }
            }
            .padding(16)
        }
    }
}

// MARK: - Pieces

func formatTime(_ seconds: Int) -> String {
    let s = max(seconds, 0)
    return String(format: "%d:%02d", s / 60, s % 60)
}

private struct TimerPill: View {
    let remaining: Int
    @Environment(\.palette) private var palette

    var body: some View {
        let color = remaining <= 30 ? wrongRed : palette.primary
        HStack(spacing: 5) {
            Image(systemName: "timer").font(.system(size: 14, weight: .semibold))
            Text(formatTime(remaining)).font(AppFont.grotesk(14, .bold)).monospacedDigit()
        }
        .foregroundStyle(color)
        .padding(.horizontal, 10)
        .padding(.vertical, 6)
        .background(color.opacity(0.12), in: RoundedRectangle(cornerRadius: 10))
    }
}

private struct LearnPill: View {
    var body: some View {
        HStack(spacing: 5) {
            Image(systemName: "graduationcap.fill").font(.system(size: 13))
            Text("Learn").font(AppFont.grotesk(14, .bold))
        }
        .foregroundStyle(correctGreen)
        .padding(.horizontal, 10)
        .padding(.vertical, 6)
        .background(correctGreen.opacity(0.12), in: RoundedRectangle(cornerRadius: 10))
    }
}

struct TagChip: View {
    let text: String
    let color: Color

    var body: some View {
        Text(text)
            .font(AppFont.sans(11, .bold))
            .foregroundStyle(color)
            .padding(.horizontal, 8)
            .padding(.vertical, 4)
            .background(color.opacity(0.12), in: RoundedRectangle(cornerRadius: 6))
    }
}

/// result: nil while unanswered or in interview mode; true/false once learn mode has checked it.
private struct OptionCard: View {
    let text: String
    let selected: Bool
    let result: Bool?
    let action: () -> Void
    @Environment(\.palette) private var palette

    var body: some View {
        let accent = result == true ? correctGreen : result == false ? wrongRed : palette.primary
        let emphasised = selected || result != nil
        Button(action: action) {
            Text(text)
                .font(.bodyLarge)
                .foregroundStyle(palette.onSurface)
                .multilineTextAlignment(.leading)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.horizontal, 15)
                .padding(.vertical, 14)
                .background(emphasised ? accent.opacity(0.08) : palette.surface, in: RoundedRectangle(cornerRadius: 14))
                .overlay(RoundedRectangle(cornerRadius: 14).stroke(emphasised ? accent : palette.outline, lineWidth: emphasised ? 2 : 1))
        }
        .buttonStyle(.plain)
    }
}

struct StoryCard: View {
    let story: QuizStory
    @Environment(\.palette) private var palette

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(story.title).font(AppFont.sans(14, .semibold)).foregroundStyle(palette.primary)
            Text(story.text).font(.bodyMedium)
        }
        .padding(15)
        .frame(maxWidth: .infinity, alignment: .leading)
        .card(radius: 16)
    }
}

/// The explanation and its "Study:" link, shared by results and learn-mode feedback.
private struct ExplanationBlock: View {
    let question: QuizQuestion
    @Environment(Router.self) private var router
    @Environment(\.palette) private var palette

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(question.explanation).font(.bodyMedium).foregroundStyle(palette.muted)
            if let id = question.linkedTopicId {
                Button { router.push(.topic(id)) } label: {
                    HStack(spacing: 2) {
                        Text("Study: \(question.linkedTopicLabel ?? "Related topic")").font(AppFont.sans(14, .semibold))
                        Image(systemName: "chevron.right").font(.system(size: 12, weight: .bold))
                    }
                    .foregroundStyle(palette.primary)
                    .padding(.horizontal, 12)
                    .padding(.vertical, 8)
                    .background(palette.primary.opacity(0.10), in: RoundedRectangle(cornerRadius: 10))
                }
                .buttonStyle(.plain)
            }
        }
        .padding(.top, 8)
    }
}

private struct LearnFeedback: View {
    let question: QuizQuestion
    let correct: Bool

    var body: some View {
        let tone = correct ? correctGreen : wrongRed
        VStack(alignment: .leading, spacing: 0) {
            Text(correct ? "Correct" : "Not quite — the answer is: \(question.options[question.correctIndex])")
                .font(AppFont.sans(14, .bold))
                .foregroundStyle(tone)
            ExplanationBlock(question: question)
        }
        .padding(15)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(tone.opacity(0.08), in: RoundedRectangle(cornerRadius: 16))
        .overlay(RoundedRectangle(cornerRadius: 16).stroke(tone.opacity(0.3), lineWidth: 1))
    }
}

private struct QuizResults: View {
    let quiz: Quiz
    let answers: [Int?]
    let result: QuizAttempt
    let priorAttempts: [QuizAttempt]
    let mode: QuizMode
    let onRetry: () -> Void
    let onRetryWrong: ([Int]) -> Void
    @Environment(\.dismiss) private var dismiss
    @Environment(\.palette) private var palette

    var body: some View {
        let total = quiz.questions.count
        let pct = result.percent
        let good = pct >= 60
        VStack(spacing: 0) {
            ScreenHeader(title: "Results")
            ScrollView {
                LazyVStack(alignment: .leading, spacing: 0) {
                    VStack(spacing: 0) {
                        Text("\(result.correct) / \(total)")
                            .font(AppFont.grotesk(40, .bold))
                            .foregroundStyle(good ? correctGreen : palette.onSurface)
                        Text("\(pct)% correct · finished in \(formatTime(result.seconds))")
                            .font(.bodyMedium).foregroundStyle(palette.muted)
                        if mode == .learn {
                            note("Learn mode — not added to your best score. Missed questions are now in your flashcards.")
                        } else if !result.wrongIndices.isEmpty {
                            note("Missed questions are now in your flashcards.")
                        }
                        if !priorAttempts.isEmpty {
                            let best = priorAttempts.bestPercent
                            let line = "Best before: \(priorAttempts.bestCorrect)/\(total) · \(priorAttempts.count) past \(priorAttempts.count == 1 ? "attempt" : "attempts")"
                            Text(pct > best ? "\(line) — new best" : line)
                                .font(AppFont.sans(12))
                                .foregroundStyle(pct > best ? correctGreen : palette.muted)
                                .padding(.top, 6)
                        }
                    }
                    .padding(20)
                    .frame(maxWidth: .infinity)
                    .background(good ? correctGreen.opacity(0.12) : palette.muted.opacity(0.08), in: RoundedRectangle(cornerRadius: 20))
                    .overlay(RoundedRectangle(cornerRadius: 20).stroke(good ? correctGreen.opacity(0.3) : palette.outline, lineWidth: 1))
                    .padding(.top, 14)

                    Text("Review").font(.titleLarge).padding(.top, 22).padding(.bottom, 8)
                    ForEach(quiz.questions.indices, id: \.self) { i in
                        ReviewedQuestion(question: quiz.questions[i], chosen: answers[i]).padding(.vertical, 6)
                    }

                    VStack(spacing: 10) {
                        let wrong = result.wrongIndices
                        if !wrong.isEmpty {
                            PrimaryButton(title: "Retry \(wrong.count) missed \(wrong.count == 1 ? "question" : "questions")", color: correctGreen) {
                                onRetryWrong(wrong)
                            }
                        }
                        HStack(spacing: 10) {
                            SecondaryButton(title: "Back to Prep") { dismiss() }
                            SecondaryButton(title: "Retry all", action: onRetry)
                        }
                    }
                    .padding(.vertical, 20)
                }
                .padding(.horizontal, 16)
            }
        }
    }

    private func note(_ text: String) -> some View {
        Text(text).font(AppFont.sans(12)).foregroundStyle(palette.muted).multilineTextAlignment(.center).padding(.top, 6)
    }
}

private struct ReviewedQuestion: View {
    let question: QuizQuestion
    let chosen: Int?
    @Environment(\.palette) private var palette

    var body: some View {
        let correct = chosen == question.correctIndex
        VStack(alignment: .leading, spacing: 0) {
            if let story = question.story {
                Text(story.title).font(.labelLarge).foregroundStyle(palette.primary).padding(.bottom, 4)
            }
            Text(question.prompt).font(.titleMedium)
            if let figure = question.figure { FigureCard(figure: figure).padding(.top, 8) }
            Text("Your answer: \(chosen.map { question.options[$0] } ?? "— (skipped)")")
                .font(AppFont.sans(14, .semibold))
                .foregroundStyle(correct ? correctGreen : wrongRed)
                .padding(.top, 8)
            if !correct {
                Text("Correct: \(question.options[question.correctIndex])")
                    .font(AppFont.sans(14, .semibold))
                    .foregroundStyle(correctGreen)
                    .padding(.top, 2)
            }
            ExplanationBlock(question: question)
        }
        .padding(15)
        .frame(maxWidth: .infinity, alignment: .leading)
        .card(radius: 16)
    }
}

private struct QuizModeChooser: View {
    let quiz: Quiz
    let trackBest: Bool
    let history: [QuizAttempt]
    let onPick: (QuizMode) -> Void
    @Environment(\.palette) private var palette

    var body: some View {
        VStack(spacing: 0) {
            ScreenHeader(title: quiz.title)
            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    Text(quiz.description).font(.bodyLarge).padding(.top, 16)
                    Text("\(quiz.questions.count) questions · \(quiz.timeLimitSeconds / 60) min timed")
                        .font(.bodyMedium).foregroundStyle(palette.muted).padding(.top, 6)
                    if !history.isEmpty {
                        Text("Best so far: \(history.bestCorrect)/\(quiz.questions.count)")
                            .font(AppFont.sans(14, .semibold)).foregroundStyle(correctGreen).padding(.top, 2)
                    }
                    VStack(spacing: 10) {
                        ModeCard(icon: "timer", tint: palette.primary, title: "Interview mode",
                                 text: "Timed like the real thing. Answers and explanations at the end." + (trackBest ? " Counts toward your best score." : "")) {
                            onPick(.interview)
                        }
                        ModeCard(icon: "graduationcap.fill", tint: correctGreen, title: "Learn mode",
                                 text: "No timer. Check each answer and read why straight away." + (trackBest ? " Not added to your best score." : "")) {
                            onPick(.learn)
                        }
                    }
                    .padding(.top, 18)
                }
                .padding(.horizontal, 16)
            }
        }
    }
}

/// List-card row with a tinted 44pt icon tile, title, body and chevron.
struct ModeCard: View {
    let icon: String
    let tint: Color
    let title: String
    let text: String
    let action: () -> Void
    @Environment(\.palette) private var palette

    var body: some View {
        Button(action: action) {
            HStack(spacing: 13) {
                Image(systemName: icon)
                    .font(.system(size: 20))
                    .foregroundStyle(tint)
                    .frame(width: 44, height: 44)
                    .background(tint.opacity(0.14), in: RoundedRectangle(cornerRadius: 13))
                VStack(alignment: .leading, spacing: 2) {
                    Text(title).font(.titleMedium)
                    Text(text).font(.bodyMedium).foregroundStyle(palette.muted).multilineTextAlignment(.leading)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                Image(systemName: "chevron.right").font(.system(size: 13, weight: .semibold)).foregroundStyle(palette.muted)
            }
            .padding(15)
            .card(radius: 16)
        }
        .buttonStyle(.plain)
    }
}
