import SwiftUI

/// The category browsers reachable from Home's Quick Access grid and the Progress screen.
enum BrowserKind: String, Hashable {
    case dataStructures, algorithms, analysis, interviewPrep, patterns
    case machineLearning, deepLearning, nlp, reinforcementLearning

    var title: String {
        switch self {
        case .dataStructures: "Data Structures"
        case .algorithms: "Algorithms"
        case .analysis: "Analysis"
        case .interviewPrep: "Interview Prep"
        case .patterns: "Patterns"
        case .machineLearning: "Machine Learning"
        case .deepLearning: "Deep Learning"
        case .nlp: "NLP"
        case .reinforcementLearning: "Reinforcement Learning"
        }
    }

    var section: Section {
        switch self {
        case .dataStructures: .DATA_STRUCTURES
        case .algorithms: .ALGORITHMS
        case .analysis: .ANALYSIS
        case .interviewPrep, .patterns: .INTERVIEW_PREP
        case .machineLearning: .ML
        case .deepLearning: .DL
        case .nlp: .NLP
        case .reinforcementLearning: .RL
        }
    }

    static func of(_ section: Section) -> BrowserKind {
        switch section {
        case .DATA_STRUCTURES: .dataStructures
        case .ALGORITHMS: .algorithms
        case .ANALYSIS: .analysis
        case .INTERVIEW_PREP: .interviewPrep
        case .ML: .machineLearning
        case .DL: .deepLearning
        case .NLP: .nlp
        case .RL: .reinforcementLearning
        }
    }
}

let patternsCategoryId = "interview_patterns"

/// Every pushed destination. Counterpart of core/nav/Screen.kt's routes.
enum Route: Hashable {
    case browser(BrowserKind)
    case topic(String)
    case simulation(String)
    /// A Simulations-tab section on its own page, by Section raw value.
    case simulationSection(String)
    case quizzes
    /// A quiz started straight from a list, in the mode the list was set to.
    case quizRun(String, learn: Bool)
    case weakSpotDrill
    case dailyDrill
    case problems
    case problem(String)
    case review
    case settings
}

enum AppTab: Hashable { case learning, simulations, practice, progress }

/// One navigation stack per tab.
@MainActor
@Observable
final class Router {
    var selectedTab: AppTab = .learning
    var paths: [AppTab: [Route]] = [:]

    func path(_ tab: AppTab) -> Binding<[Route]> {
        Binding(get: { self.paths[tab] ?? [] }, set: { self.paths[tab] = $0 })
    }

    func push(_ route: Route) { paths[selectedTab, default: []].append(route) }

    func pop() { _ = paths[selectedTab]?.popLast() }

    /// The title of the screen a back button returns to: the route under the top one, or the tab's root.
    var backTitle: String {
        let path = paths[selectedTab] ?? []
        if path.count >= 2 {
            switch path[path.count - 2] {
            case .browser(let kind): return kind.title
            case .quizzes: return "Quizzes"
            case .problems: return "Problems"
            default: return "Back"
            }
        }
        switch selectedTab {
        case .learning: return "Learning"
        case .simulations: return "Simulations"
        case .practice: return "Practice"
        case .progress: return "Progress"
        }
    }

    /// Jump to a tab at its root — a reminder tap or a tab re-tap.
    func open(_ tab: AppTab, then route: Route? = nil) {
        paths[tab] = route.map { [$0] } ?? []
        selectedTab = tab
    }
}
