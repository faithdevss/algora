import StoreKit
import SwiftUI

/// App Store rating prompt, asked once an install has invested in the app. Mirrors
/// core/playreview/AppReviewPrompt.kt: 15% of the active track, 5 active days, or a timed quiz at
/// 80%+ — whichever comes first, once per install.
enum ReviewPrompt {
    static let progressThresholdPercent = 15
    static let activeDaysThreshold = 5
    static let quizScoreThresholdPercent = 80

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
