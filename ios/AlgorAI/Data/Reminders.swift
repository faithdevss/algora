import Foundation
import UserNotifications

// Port of core/notify/DailyReminder.kt + StudyReminder.kt. Android re-checks the rules in a
// WorkManager job at fire time; iOS has no equivalent, so every app open re-plans the next few
// days as one-off local notifications computed from today's state. Opening the app again replaces
// the plan, so a nudge for a day the learner already showed up never fires.
enum Reminders {
    /** Beyond this many days away the learner is lapsed; the weekly reminder takes over. */
    static let maxGapDays: Int64 = 3
    /** Below this a streak is not worth defending. */
    static let streakAtRiskMin = 3
    /** The lapsed reminder speaks after this much silence. */
    static let inactiveDays: Int64 = 7

    private static let dailyPrefix = "daily-"
    private static let lapsedPrefix = "lapsed-"

    /// Deep-link route carried in the notification, read back by the app delegate.
    static let routeKey = "route"

    @MainActor
    static func reschedule(store: AppStore) async {
        let center = UNUserNotificationCenter.current()
        let pending = await center.pendingNotificationRequests().map(\.identifier)
        center.removePendingNotificationRequests(withIdentifiers: pending.filter { $0.hasPrefix(dailyPrefix) || $0.hasPrefix(lapsedPrefix) })
        guard store.dailyReminderEnabled || store.studyRemindersEnabled else { return }
        #if DEBUG
        // Screenshot launches (`-openSim` / `-openTopic`) must not be covered by the permission dialog.
        let args = UserDefaults.standard
        if args.string(forKey: "openSim") != nil || args.string(forKey: "openTopic") != nil { return }
        #endif

        // Ask once; the system dialog is one-shot anyway.
        if !store.notificationPermissionAsked {
            store.markNotificationPermissionAsked()
            _ = try? await center.requestAuthorization(options: [.alert, .sound, .badge])
        }
        let settings = await center.notificationSettings()
        guard settings.authorizationStatus == .authorized || settings.authorizationStatus == .provisional else { return }

        let today = todayEpochDay()
        if store.dailyReminderEnabled {
            let streak = store.streak
            for offset in 1...maxGapDays {
                let day = today + offset
                let cards = store.reviewCounts(today: day).waiting
                // Only tomorrow can still save the current streak.
                let (title, body, route): (String, String, String)
                if offset == 1 && streak >= streakAtRiskMin {
                    title = "Your \(streak)-day streak ends tonight"
                    body = cards > 0 ? "\(cards) card\(cards == 1 ? "" : "s") to review — a few minutes keeps it alive."
                        : "One drill is enough to keep it going."
                    route = "dailyDrill"
                } else if cards > 0 {
                    title = "\(cards) card\(cards == 1 ? "" : "s") waiting"
                    body = "A short recall session now beats a backlog later."
                    route = "review"
                } else {
                    title = "Today's drill is waiting"
                    body = "One problem and a five-question set — about five minutes."
                    route = "dailyDrill"
                }
                schedule(center, id: "\(dailyPrefix)\(day)", day: day, minute: store.dailyReminderMinute, title: title, body: body, route: route)
            }
        }
        if store.studyRemindersEnabled {
            schedule(center, id: "\(lapsedPrefix)\(today + inactiveDays)", day: today + inactiveDays, minute: store.dailyReminderMinute,
                     title: "Pick up where you left off",
                     body: "A week since your last session — a few flashcards will bring it back fast.", route: "review")
        }
    }

    private static func schedule(_ center: UNUserNotificationCenter, id: String, day: Int64, minute: Int,
                                 title: String, body: String, route: String) {
        // Epoch days are UTC; the reminder time is local wall-clock on that calendar date.
        let date = Date(timeIntervalSince1970: TimeInterval(day * 86_400))
        var utc = Calendar(identifier: .gregorian)
        utc.timeZone = TimeZone(identifier: "UTC")!
        var components = utc.dateComponents([.year, .month, .day], from: date)
        components.hour = minute / 60
        components.minute = minute % 60
        let content = UNMutableNotificationContent()
        content.title = title
        content.body = body
        content.sound = .default
        content.userInfo = [routeKey: route]
        center.add(UNNotificationRequest(identifier: id, content: content,
                                         trigger: UNCalendarNotificationTrigger(dateMatching: components, repeats: false)))
    }
}
