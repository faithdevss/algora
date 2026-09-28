import Foundation

/// The bundled content, decoded once. Mirrors the Android registries (TopicRegistry,
/// CategoryRegistry, TopicContentProvider, QuizRegistry, ProblemRegistry, PrerequisiteGraph...).
final class ContentStore: Sendable {
    static let shared = ContentStore()

    let categories: [Category]
    let sections: [Section: [Topic]]
    let contents: [String: TopicContent]
    let quizzes: [(topicId: String, quiz: Quiz)]
    let patterns: [ProblemPattern]
    let problems: [PracticeProblem]
    let behavioralBanks: [BehavioralBank]
    let systemDesignPrimers: [SystemDesignPrimer]
    let flashcardDecks: [FlashcardDeck]
    private let prerequisites: [String: [String]]
    private let contentOrder: [String]

    private let topicsById: [String: Topic]
    private let categoriesById: [String: Category]
    private let quizzesById: [String: Quiz]
    private let problemsById: [String: PracticeProblem]
    private let problemsByPattern: [String: [PracticeProblem]]

    private struct Catalog: Decodable {
        let categories: [Category]
        let sections: [String: [Topic]]
        let prerequisites: [String: [String]]
        let contentOrder: [String]
    }

    private struct QuizEntry: Decodable { let topicId: String; let quiz: Quiz }
    private struct Problems: Decodable { let patterns: [ProblemPattern]; let problems: [PracticeProblem] }
    private struct Interview: Decodable { let behavioral: [BehavioralBank]; let systemDesign: [SystemDesignPrimer] }

    private init() {
        let catalog: Catalog = Self.load("catalog")
        categories = catalog.categories
        var sections: [Section: [Topic]] = [:]
        for (key, topics) in catalog.sections {
            if let section = Section(rawValue: key) { sections[section] = topics }
        }
        self.sections = sections
        prerequisites = catalog.prerequisites
        contentOrder = catalog.contentOrder
        contents = Self.load("topic_content")
        let quizEntries: [QuizEntry] = Self.load("quizzes")
        quizzes = quizEntries.map { ($0.topicId, $0.quiz) }
        let problemFile: Problems = Self.load("problems")
        patterns = problemFile.patterns
        problems = problemFile.problems
        let interview: Interview = Self.load("interview")
        behavioralBanks = interview.behavioral
        systemDesignPrimers = interview.systemDesign
        flashcardDecks = Self.load("flashcards")

        // Cross-listed ids resolve to their first occurrence, in section order — matching
        // TopicRegistry's first-wins lookup.
        var byId: [String: Topic] = [:]
        for section in Section.allCases {
            for topic in sections[section] ?? [] where byId[topic.id] == nil { byId[topic.id] = topic }
        }
        topicsById = byId
        categoriesById = Dictionary(categories.map { ($0.id, $0) }, uniquingKeysWith: { a, _ in a })
        quizzesById = Dictionary(quizEntries.map { ($0.topicId, $0.quiz) }, uniquingKeysWith: { a, _ in a })
        problemsById = Dictionary(problems.map { ($0.id, $0) }, uniquingKeysWith: { a, _ in a })
        problemsByPattern = Dictionary(grouping: problems, by: \.patternId)
            .mapValues { group in group.enumerated().sorted { ($0.element.difficulty, $0.offset) < ($1.element.difficulty, $1.offset) }.map(\.element) }
    }

    private static func load<T: Decodable>(_ name: String) -> T {
        guard let url = Bundle.main.url(forResource: name, withExtension: "json") else {
            fatalError("Missing bundled content \(name).json — run the Android export (docs/plan/ios-port.md)")
        }
        do {
            return try JSONDecoder().decode(T.self, from: Data(contentsOf: url))
        } catch {
            fatalError("Could not decode \(name).json: \(error)")
        }
    }

    // MARK: Lookups

    func topic(_ id: String) -> Topic? { topicsById[id] }
    func category(_ id: String) -> Category? { categoriesById[id] }
    func content(_ topicId: String) -> TopicContent? { contents[topicId] }
    func quiz(_ topicId: String) -> Quiz? { quizzesById[topicId] }
    func problem(_ id: String) -> PracticeProblem? { problemsById[id] }
    func problems(forPattern id: String) -> [PracticeProblem] { problemsByPattern[id] ?? [] }
    func pattern(_ id: String) -> ProblemPattern? { patterns.first { $0.id == id } }
    func behavioralBank(_ topicId: String) -> BehavioralBank? { behavioralBanks.first { $0.id == topicId } }
    func systemDesignPrimer(_ topicId: String) -> SystemDesignPrimer? { systemDesignPrimers.first { $0.id == topicId } }

    func topics(in section: Section) -> [Topic] { sections[section] ?? [] }
    func categories(in section: Section) -> [Category] { categories.filter { $0.section == section } }

    func prereqs(of topicId: String) -> [String] { prerequisites[topicId] ?? [] }
    func unlockedBy(_ topicId: String) -> [String] {
        prerequisites.filter { $0.value.contains(topicId) }.map(\.key).sorted()
    }

    /// Every topic that ships a runnable lab, in registry order.
    var runnableSimulations: [(topicId: String, simulation: SimulationType)] {
        contentOrder.compactMap { id in
            guard let page = contents[id], page.simulation != .NotYetAvailable else { return nil }
            return (id, page.simulation)
        }
    }
}
