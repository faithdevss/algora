import SwiftUI
import UserNotifications

@main
struct AlgorAIApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) private var delegate
    @State private var store = AppStore()
    @State private var session = AppSession()
    @Environment(\.scenePhase) private var scenePhase

    var body: some Scene {
        WindowGroup {
            RootView()
                .environment(store)
                .environment(session)
                .onChange(of: scenePhase, initial: true) { _, phase in
                    guard phase == .active else { return }
                    store.recordActivityToday()
                    Task { await Reminders.reschedule(store: store) }
                }
        }
    }
}

/// App-wide UI state that is not persisted: the DSA/AI mode, and a pending reminder deep link.
@MainActor
@Observable
final class AppSession {
    var mode: AppMode = .dsa
    var pendingRoute: String?
}

/// Routes a tapped reminder to the work it was about.
@MainActor
final class AppDelegate: NSObject, UIApplicationDelegate, UNUserNotificationCenterDelegate {
    static var onRoute: (@MainActor (String) -> Void)?
    static var pendingRoute: String?

    func application(_ application: UIApplication, didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil) -> Bool {
        UNUserNotificationCenter.current().delegate = self
        return true
    }

    nonisolated func userNotificationCenter(_ center: UNUserNotificationCenter, didReceive response: UNNotificationResponse,
                                            withCompletionHandler completionHandler: @escaping () -> Void) {
        let route = response.notification.request.content.userInfo[Reminders.routeKey] as? String
        completionHandler()
        guard let route else { return }
        Task { @MainActor in
            if let onRoute = AppDelegate.onRoute { onRoute(route) } else { AppDelegate.pendingRoute = route }
        }
    }
}
