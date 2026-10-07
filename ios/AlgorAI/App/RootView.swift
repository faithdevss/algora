import SwiftUI

/// The fixed four-destination bar (Learning · Simulations · Practice · Progress) from the mock's
/// navDefs, identical in both modes. Each tab owns a NavigationStack; the in-app ScreenHeader
/// replaces the system navigation bar so the chrome matches the Android app.
struct RootView: View {
    @Environment(AppStore.self) private var store
    @Environment(AppSession.self) private var session
    @Environment(\.colorScheme) private var systemScheme
    @State private var router = Router()

    private var palette: Palette {
        let dark = switch store.themeMode {
        case .system: systemScheme == .dark
        case .light: false
        case .dark: true
        }
        return Palette(dark: dark, accent: store.accent ?? session.mode.accent)
    }

    var body: some View {
        TabView(selection: tabSelection) {
            stack(.learning) { HomeScreen() }
                .tabItem { Label("Learning", systemImage: iconSymbol("book")) }
                .tag(AppTab.learning)
            stack(.simulations) { SimulationsScreen() }
                .tabItem { Label("Simulations", systemImage: iconSymbol("flask")) }
                .tag(AppTab.simulations)
            stack(.practice) { PracticeScreen() }
                .tabItem { Label("Practice", systemImage: iconSymbol("target")) }
                .tag(AppTab.practice)
            stack(.progress) { ProgressScreen() }
                .tabItem { Label("Progress", systemImage: iconSymbol("chart")) }
                .tag(AppTab.progress)
        }
        .tint(palette.dark ? Color.white : palette.primary)
        .animation(.easeInOut(duration: 0.32), value: palette)
        .environment(\.palette, palette)
        .environment(router)
        .preferredColorScheme(store.themeMode == .system ? nil : (palette.dark ? .dark : .light))
        .onAppear {
            // A reminder tap lands on the work itself, over the Practice tab's root.
            AppDelegate.onRoute = { route in openReminder(route) }
            if let route = AppDelegate.pendingRoute {
                AppDelegate.pendingRoute = nil
                openReminder(route)
            }
        }
        #if DEBUG
        // `simctl launch … -openTopic <id>` / `-openSim <id>` / `-openTab practice` jump straight
        // to a screen, so a screenshot pass does not need taps.
        .onAppear {
            let args = UserDefaults.standard
            if let tab = args.string(forKey: "openTab") {
                router.open(["simulations": .simulations, "practice": .practice, "progress": .progress][tab] ?? .learning)
            }
            if let id = args.string(forKey: "openTopic") { router.push(.topic(id)) }
            if let id = args.string(forKey: "openQuiz") { router.push(.quizRun(id, learn: true)) }
            if let id = args.string(forKey: "openSim") { router.push(.simulation(id)) }
            if let id = args.string(forKey: "openSimSection") { router.push(.simulationSection(id)) }
            if let page = args.string(forKey: "openPractice") { router.push(page == "quizzes" ? .quizzes : page == "problems" ? .problems : .browser(.interviewPrep)) }
            if args.string(forKey: "openMode") == "ai" { session.mode = .ai }
            if let theme = args.string(forKey: "openTheme") { store.themeMode = ThemeMode(rawValue: theme) ?? .system }
        }
        #endif
    }

    /// A tap always opens the tab at its root, the way Android's bottom bar pops to start. The
    /// Practice tab forces DSA mode, as it does on Android.
    private var tabSelection: Binding<AppTab> {
        Binding(
            get: { router.selectedTab },
            set: { tab in
                if tab == .practice { session.mode = .dsa }
                router.open(tab)
            }
        )
    }

    private func openReminder(_ route: String) {
        session.mode = .dsa
        router.open(.practice, then: route == "review" ? .review : .dailyDrill)
    }

    private func stack<Content: View>(_ tab: AppTab, @ViewBuilder root: () -> Content) -> some View {
        NavigationStack(path: router.path(tab)) {
            root()
                .screenChrome()
                .navigationDestination(for: Route.self) { route in
                    RouteView(route: route).screenChrome()
                }
        }
    }
}

struct RouteView: View {
    let route: Route

    var body: some View {
        switch route {
        case .browser(let kind):
            if kind == .interviewPrep { InterviewPrepScreen() } else { CategoryBrowserScreen(kind: kind) }
        case .topic(let id): TopicDetailScreen(topicId: id)
        case .simulation(let id): SimulationDetailScreen(topicId: id)
        case .simulationSection(let id): SimulationSectionScreen(sectionId: id)
        case .quizzes: QuizCatalogScreen()
        case .quizRun(let id, let learn): QuizRunScreen(topicId: id, learn: learn)
        case .weakSpotDrill: WeakSpotDrillScreen()
        case .dailyDrill: DailyDrillScreen()
        case .problems: ProblemListScreen()
        case .problem(let id): ProblemDetailScreen(problemId: id)
        case .review: ReviewScreen()
        case .settings: SettingsScreen()
        }
    }
}

private struct ScreenChrome: ViewModifier {
    @Environment(\.palette) private var palette

    func body(content: Content) -> some View {
        content
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
            .background(palette.background.ignoresSafeArea())
            .foregroundStyle(palette.onSurface)
            .toolbar(.hidden, for: .navigationBar)
    }
}

extension View {
    func screenChrome() -> some View { modifier(ScreenChrome()) }
}

// Hiding the system navigation bar also disables the edge-swipe back gesture; re-enable it so a
// pushed screen still pops the iOS way.
extension UINavigationController: @retroactive UIGestureRecognizerDelegate {
    override open func viewDidLoad() {
        super.viewDidLoad()
        interactivePopGestureRecognizer?.delegate = self
    }

    public func gestureRecognizerShouldBegin(_ gestureRecognizer: UIGestureRecognizer) -> Bool {
        viewControllers.count > 1
    }
}
