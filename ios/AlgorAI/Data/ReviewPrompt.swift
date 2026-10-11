import StoreKit
import SwiftUI

/// App Store rating prompt, asked once an install has invested in the app. Mirrors
/// core/playreview/AppReviewPrompt.kt: 15% of the active track, 5 active days, or a timed quiz at
/// 80%+, plus the moments that ask as they happen: the topic that reaches a milestone, opening the
/// app on a 3-day streak, and finishing the daily drill — whichever comes first, once per install.
enum ReviewPrompt {
    static let progressThresholdPercent = 15
    static let activeDaysThreshold = 5
    static let quizScoreThresholdPercent = 80
    /// Explorer and Momentum on the Progress ladder. First Steps (1) is too early to ask.
    static let milestoneTopics: Set<Int> = [10, 25]
    static let streakDays = 3

    static func reachedMilestone(before: Int, after: Int) -> Bool {
        after > before && milestoneTopics.contains { (before + 1)...after ~= $0 }
    }

    /// For a happy moment the caller has already recognised (milestone, streak, finished drill).
    @MainActor
    static func maybeAskNow(store: AppStore, requestReview: RequestReviewAction) {
        guard store.reviewPromptedDay == 0 else { return }
        requestReview()
        store.markReviewPrompted()
    }

    @MainActor
    static func maybeAsk(store: AppStore, progressPercent: Int, requestReview: RequestReviewAction) {
        guard store.reviewPromptedDay == 0,
              progressPercent >= progressThresholdPercent || store.activeDays.count >= activeDaysThreshold else { return }
        requestReview()
        store.markReviewPrompted()
    }

    @MainActor
    static func maybeAskAfterQuiz(store: AppStore, scorePercent: Int, requestReview: RequestReviewAction) {
        guard store.reviewPromptedDay == 0, scorePercent >= quizScoreThresholdPercent else { return }
        requestReview()
        store.markReviewPrompted()
    }
}
