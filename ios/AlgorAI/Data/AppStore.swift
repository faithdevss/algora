import Foundation
import Observation

/// UTC-day bucketing, the same one Android uses, so streaks and schedules agree about "today".
func todayEpochDay(_ now: Date = Date()) -> Int64 { Int64(now.timeIntervalSince1970) / 86_400 }

enum ThemeMode: String, CaseIterable { case system, light, dark }

enum AppMode: String { case dsa, ai }

// MARK: - SM-2

struct SrsCard: Codable, Hashable {
    var reps: Int
    var ef: Double
    var intervalDays: Int
    var dueDay: Int64
}

/// SM-2 update. quality: 0-5. A grade below 3 lapses the card back to the start.
func sm2(_ prev: SrsCard?, quality: Int, today: Int64) -> SrsCard {
    let base = prev ?? SrsCard(reps: 0, ef: 2.5, intervalDays: 0, dueDay: today)
    let q = Double(5 - quality)
    let newEf = max(base.ef + (0.1 - q * (0.08 + q * 0.02)), 1.3)
    let reps: Int
    let interval: Int
    if quality < 3 {
        reps = 0
        interval = 1
    } else {
        reps = base.reps + 1
        switch reps {
        case 1: interval = 1
        case 2: interval = 6
        default: interval = max(Int((Double(base.intervalDays) * newEf).rounded()), 1)
        }
    }
    return SrsCard(reps: reps, ef: newEf, intervalDays: interval, dueDay: today + Int64(interval))
}

// MARK: - Quiz history

/// One finished run of a quiz. `wrongIndices` point into the quiz's own question list.
struct QuizAttempt: Codable, Hashable {
    let atEpochSec: Int64
    let correct: Int
    let total: Int
    let seconds: Int
    let wrongIndices: [Int]

    var percent: Int { total == 0 ? 0 : correct * 100 / total }
    var day: Int64 { atEpochSec / 86_400 }
}

let quizAttemptsPerQuiz = 5

extension Array where Element == QuizAttempt {
    /// Runs recorded before a set grew were scored out of a different total; only count current-length runs.
    func ofLength(_ count: Int) -> [QuizAttempt] { filter { $0.total == count } }
    var bestPercent: Int { map(\.percent).max() ?? 0 }
    var bestCorrect: Int { self.max { $0.percent < $1.percent }?.correct ?? 0 }
}

func questionKey(_ quizId: String, _ index: Int) -> String { "\(quizId)#\(index)" }

func parseQuestionKey(_ key: String) -> (quizId: String, index: Int)? {
    guard let cut = key.lastIndex(of: "#"), cut > key.startIndex,
          let index = Int(key[key.index(after: cut)...]) else { return nil }
    return (String(key[..<cut]), index)
}

/// A missed quiz question becomes a flashcard under this prefix.
let quizCardPrefix = "quiz:"
func quizCardKey(_ questionKey: String) -> String { quizCardPrefix + questionKey }

// MARK: - Store

/// Everything the app remembers on-device: the iOS counterpart of Android's settings and progress
/// DataStores. Backed by UserDefaults; each property writes through on change.
@MainActor
@Observable
final class AppStore {
    @ObservationIgnored private let defaults: UserDefaults

    // Appearance
    var themeMode: ThemeMode { didSet { defaults.set(themeMode.rawValue, forKey: Keys.themeMode) } }
    /// nil = Auto: the accent follows the active mode.
    var accent: AccentColor? { didSet { defaults.set(accent?.rawValue ?? "auto", forKey: Keys.accent) } }

    // Reading
    private(set) var bookmarks: Set<String> { didSet { save(bookmarks, Keys.bookmarks) } }
    private(set) var lastOpened: String? { didSet { defaults.set(lastOpened, forKey: Keys.lastOpened) } }

    // Progress
    private(set) var completedTopicIds: Set<String> { didSet { save(completedTopicIds, Keys.completed) } }
    /// First-completion day per topic.
    private(set) var completedAt: [String: Int64] { didSet { save(completedAt, Keys.completedAt) } }
    private(set) var solvedProblemIds: Set<String> { didSet { save(solvedProblemIds, Keys.solved) } }

    // Streak
    private(set) var streak: Int { didSet { defaults.set(streak, forKey: Keys.streak) } }
    private(set) var streakLastDay: Int64? { didSet { save(streakLastDay, Keys.streakLastDay) } }
    private(set) var activeDays: Set<Int64> { didSet { save(activeDays, Keys.activeDays) } }

    // Spaced repetition
    private(set) var srs: [String: SrsCard] { didSet { save(srs, Keys.srs) } }
    private(set) var newCardsDay: Int64 { didSet { defaults.set(newCardsDay, forKey: Keys.newCardsDay) } }
    private(set) var newCardsCount: Int { didSet { defaults.set(newCardsCount, forKey: Keys.newCardsCount) } }

    // Quizzes
    private(set) var quizAttempts: [String: [QuizAttempt]] { didSet { save(quizAttempts, Keys.quizAttempts) } }
    private(set) var questionResults: [String: Bool] { didSet { save(questionResults, Keys.questionResults) } }
    private(set) var quizzesFinished: Int { didSet { defaults.set(quizzesFinished, forKey: Keys.quizzesFinished) } }

    // Daily drill
    private(set) var drillDay: Int64? { didSet { save(drillDay, Keys.drillDay) } }
    private(set) var drillProblemId: String? { didSet { defaults.set(drillProblemId, forKey: Keys.drillProblem) } }

    // Reminders & review prompt
    var studyRemindersEnabled: Bool { didSet { defaults.set(studyRemindersEnabled, forKey: Keys.studyReminders) } }
    var dailyReminderEnabled: Bool { didSet { defaults.set(dailyReminderEnabled, forKey: Keys.dailyReminder) } }
    var dailyReminderMinute: Int { didSet { defaults.set(dailyReminderMinute, forKey: Keys.dailyReminderMinute) } }
    private(set) var notificationPermissionAsked: Bool { didSet { defaults.set(notificationPermissionAsked, forKey: Keys.notifAsked) } }
    private(set) var reviewPromptedDay: Int64 { didSet { defaults.set(reviewPromptedDay, forKey: Keys.reviewPrompted) } }

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
        themeMode = ThemeMode(rawValue: defaults.string(forKey: Keys.themeMode) ?? "") ?? .system
        accent = AccentColor(rawValue: defaults.string(forKey: Keys.accent) ?? "auto")
        bookmarks = Self.load(defaults, Keys.bookmarks) ?? []
        lastOpened = defaults.string(forKey: Keys.lastOpened)
        completedTopicIds = Self.load(defaults, Keys.completed) ?? []
        completedAt = Self.load(defaults, Keys.completedAt) ?? [:]
        solvedProblemIds = Self.load(defaults, Keys.solved) ?? []
        streak = defaults.integer(forKey: Keys.streak)
        streakLastDay = Self.load(defaults, Keys.streakLastDay)
        activeDays = Self.load(defaults, Keys.activeDays) ?? []
        srs = Self.load(defaults, Keys.srs) ?? [:]
        newCardsDay = Int64(defaults.integer(forKey: Keys.newCardsDay))
        newCardsCount = defaults.integer(forKey: Keys.newCardsCount)
        quizAttempts = Self.load(defaults, Keys.quizAttempts) ?? [:]
        questionResults = Self.load(defaults, Keys.questionResults) ?? [:]
        quizzesFinished = defaults.integer(forKey: Keys.quizzesFinished)
        drillDay = Self.load(defaults, Keys.drillDay)
        drillProblemId = defaults.string(forKey: Keys.drillProblem)
        studyRemindersEnabled = defaults.object(forKey: Keys.studyReminders) as? Bool ?? true
        dailyReminderEnabled = defaults.object(forKey: Keys.dailyReminder) as? Bool ?? true
        dailyReminderMinute = defaults.object(forKey: Keys.dailyReminderMinute) as? Int ?? defaultDailyReminderMinute
        notificationPermissionAsked = defaults.bool(forKey: Keys.notifAsked)
        reviewPromptedDay = Int64(defaults.integer(forKey: Keys.reviewPrompted))
    }

    // MARK: Reading

    func toggleBookmark(_ topicId: String) {
        if bookmarks.contains(topicId) { bookmarks.remove(topicId) } else { bookmarks.insert(topicId) }
    }

    func setLastOpened(_ topicId: String) { lastOpened = topicId }

    // MARK: Progress

    func markCompleted(_ topicId: String, day: Int64 = todayEpochDay()) {
        completedTopicIds.insert(topicId)
        // Re-completing keeps the original date; only the first finish is a data point.
        if completedAt[topicId] == nil { completedAt[topicId] = day }
    }

    func markIncomplete(_ topicId: String) {
        completedTopicIds.remove(topicId)
        completedAt[topicId] = nil
    }

    /// Topics finished per epoch day.
    var completionsByDay: [Int64: Int] {
        completedAt.values.reduce(into: [:]) { $0[$1, default: 0] += 1 }
    }

    func setProblemSolved(_ problemId: String, _ solved: Bool) {
        if solved { solvedProblemIds.insert(problemId) } else { solvedProblemIds.remove(problemId) }
    }

    // MARK: Streak

    /// Same day → no change, yesterday → +1, any larger gap → reset to 1. (Android's rewarded-ad
    /// streak freezes are not part of iOS, which has no ads.)
    func recordActivityToday(today: Int64 = todayEpochDay()) {
        if let last = streakLastDay {
            switch today - last {
            case 0: streak = max(streak, 1)
            case 1: streak += 1
            default: streak = 1
            }
        } else {
            streak = 1
        }
        streakLastDay = today
        activeDays = Set((activeDays.union([today])).filter { $0 > today - activityHistoryDays })
    }

    // MARK: Spaced repetition

    func reviewCard(_ key: String, quality: Int, today: Int64 = todayEpochDay()) {
        let prev = srs[key]
        srs[key] = sm2(prev, quality: quality, today: today)
        // Grading a card with no prior state is what "introducing a new card" means.
        if prev == nil {
            newCardsCount = (newCardsDay == today ? newCardsCount : 0) + 1
            newCardsDay = today
        }
    }

    func newCardsIntroduced(today: Int64 = todayEpochDay()) -> Int {
        newCardsDay == today ? newCardsCount : 0
    }

    // MARK: Quizzes

    func recordQuizAttempt(_ quizId: String, _ attempt: QuizAttempt) {
        let kept = ((quizAttempts[quizId] ?? []) + [attempt])
            .sorted { $0.atEpochSec > $1.atEpochSec }
            .prefix(quizAttemptsPerQuiz)
        quizAttempts[quizId] = Array(kept)
    }

    /// Records answers from one run. Every miss also goes into the flashcard deck: a new card is
    /// created already scheduled for tomorrow, and an existing one lapses like an "Again" grade.
    func recordQuestionResults(_ results: [String: Bool], today: Int64 = todayEpochDay()) {
        guard !results.isEmpty else { return }
        questionResults.merge(results) { _, new in new }
        var cards = srs
        for (key, correct) in results where !correct {
            let cardKey = quizCardKey(key)
            cards[cardKey] = sm2(cards[cardKey], quality: 2, today: today)
        }
        srs = cards
    }

    func recordQuizFinished() { quizzesFinished += 1 }

    // MARK: Daily drill

    func setDrillProblem(day: Int64, problemId: String) {
        drillDay = day
        drillProblemId = problemId
    }

    // MARK: Reminders & review

    func markNotificationPermissionAsked() { notificationPermissionAsked = true }
    func markReviewPrompted(day: Int64 = todayEpochDay()) { reviewPromptedDay = day }

    // MARK: Persistence helpers

    private func save<T: Encodable>(_ value: T?, _ key: String) {
        if let value, let data = try? JSONEncoder().encode(value) {
            defaults.set(data, forKey: key)
        } else {
            defaults.removeObject(forKey: key)
        }
    }

    private static func load<T: Decodable>(_ defaults: UserDefaults, _ key: String) -> T? {
        guard let data = defaults.data(forKey: key) else { return nil }
        return try? JSONDecoder().decode(T.self, from: data)
    }

    private enum Keys {
        static let themeMode = "theme_mode"
        static let accent = "accent_color"
        static let bookmarks = "bookmarks"
        static let lastOpened = "last_opened_topic"
        static let completed = "completed_topic_ids"
        static let completedAt = "completed_at_days"
        static let solved = "solved_problem_ids"
        static let streak = "streak_count"
        static let streakLastDay = "streak_last_epoch_day"
        static let activeDays = "active_epoch_days"
        static let srs = "srs_state"
        static let newCardsDay = "new_cards_epoch_day"
        static let newCardsCount = "new_cards_count"
        static let quizAttempts = "quiz_attempts"
        static let questionResults = "question_results"
        static let quizzesFinished = "quizzes_finished"
        static let drillDay = "drill_epoch_day"
        static let drillProblem = "drill_problem_id"
        static let studyReminders = "study_reminders_enabled"
        static let dailyReminder = "daily_reminder_enabled"
        static let dailyReminderMinute = "daily_reminder_minute_of_day"
        static let notifAsked = "notification_permission_asked"
        static let reviewPrompted = "review_prompt_epoch_day"
    }
}

/// Only recent days are ever rendered; keeping more would grow the set without bound.
private let activityHistoryDays: Int64 = 60

/// 20:00 local.
let defaultDailyReminderMinute = 20 * 60
