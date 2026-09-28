import Foundation

// Swift mirrors of the Kotlin content model. The JSON these decode is generated from the Android
// sources by IosContentExportTest — see docs/plan/ios-port.md. Field names match the Kotlin ones
// exactly; a nullable Kotlin field is omitted from the JSON when null, so it is optional here.

enum Section: String, Codable, CaseIterable {
    case DATA_STRUCTURES, ALGORITHMS, ANALYSIS, INTERVIEW_PREP, ML, DL, NLP, RL
}

enum Difficulty: String, Codable, Comparable {
    case BEGINNER, INTERMEDIATE, ADVANCED

    private var rank: Int {
        switch self {
        case .BEGINNER: 0
        case .INTERMEDIATE: 1
        case .ADVANCED: 2
        }
    }

    static func < (a: Difficulty, b: Difficulty) -> Bool { a.rank < b.rank }
}

struct Category: Codable, Identifiable, Hashable {
    let id: String
    let name: String
    let section: Section
    let accentColor: Int64
    let iconName: String
}

struct Topic: Codable, Identifiable, Hashable {
    let id: String
    let name: String
    let categoryId: String
    let tagline: String
    let description: String
    let iconName: String
    let accentColor: Int64
    let isPremium: Bool
    let difficulty: Difficulty?
}

struct TopicContent: Codable {
    let topicId: String
    let whatIsIt: [String]
    let steps: [StepCard]
    let formulas: [FormulaEntry]
    let notationKey: [NotationEntry]
    let codeBlocks: [CodeBlock]
    let simulation: SimulationType
    let applications: [ApplicationCard]
    let takeaways: [String]
    let crossLinks: [CrossLink]
    let figure: Figure?
}

struct CrossLink: Codable, Hashable { let topicId: String; let label: String }
struct StepCard: Codable, Hashable { let number: Int; let title: String; let body: String; let accentColor: Int64 }
struct FormulaEntry: Codable, Hashable { let label: String; let formula: String; let note: String }
struct NotationEntry: Codable, Hashable { let symbol: String; let meaning: String }
struct CodeVariant: Codable, Hashable { let language: String; let code: String }

struct CodeBlock: Codable, Hashable {
    let title: String
    let accentColor: Int64
    let code: String
    let variants: [CodeVariant]
}

struct ApplicationCard: Codable, Hashable {
    let iconName: String
    let accentColor: Int64
    let title: String
    let body: String
}

enum SimulationType: String, Codable, CaseIterable {
    case ArrayVisualizer, LinkedListVisualizer, StackVisualizer, QueueVisualizer, GraphVisualizer
    case GraphAlgorithmPlayer, ArrayWalkPlayer, PointCloudPlayer, TokenStripPlayer, NeuralNetPlayer
    case RlTrainingPlayer, PolicyGradientPlayer, GameSearchPlayer, OfflineRlPlayer, MultiAgentPlayer
    case ExplorationPlayer, LinkedStructurePlayer, EnvironmentPlayer, RegressionExplorer, RegressionLab
    case DecisionSurface, BitBoardPlayer, FeatureMapPlayer, PerceptronVisualizer, ClassifierPlayground
    case RecursionTreeVisualizer, DpGridVisualizer, SortingVisualizer, SearchVisualizer, TreeVisualizer
    case PathfindingGrid, HashingVisualizer, RlGridWorld, BanditExplorer, NotYetAvailable
}

// MARK: - Figures

struct Figure: Codable, Hashable {
    let caption: String
    let shape: FigureShape
}

enum FigureTone: String, Codable, Hashable { case Primary, Accent, Muted, Warn }

enum FigureShape: Codable, Hashable {
    case strip(Strip)
    case timeline(Timeline)
    case grid(Grid)
    case stacks(Stacks)
    case tree(Tree)
    case graph(Graph)
    case plot(Plot)
    case layerStack(LayerStack)
    case heatmap(Heatmap)

    struct Strip: Codable, Hashable {
        let cells: [String]
        let bands: [FigureBand]
        let pointers: [FigurePointer]
        let aux: [String]
        let auxLabel: String?
    }

    struct Timeline: Codable, Hashable {
        let spans: [FigureSpan]
        let axisMax: Int
        let marker: Int?
        let markerLabel: String?
    }

    struct Grid: Codable, Hashable {
        let rows: [[String]]
        let rowHeaders: [String]
        let colHeaders: [String]
        let marks: [FigureCell]
        let arrows: [FigureArrow]
    }

    struct Stacks: Codable, Hashable { let columns: [FigureStack] }
    struct Tree: Codable, Hashable { let nodes: [FigureNode] }
    struct Graph: Codable, Hashable { let nodes: [FigureGraphNode]; let edges: [FigureEdge] }

    struct Plot: Codable, Hashable {
        let series: [FigureSeries]
        let bars: [FigureBar]
        let xLabel: String?
        let yLabel: String?
        let markers: [FigurePoint]
    }

    struct LayerStack: Codable, Hashable {
        let layers: [FigureLayer]
        let horizontal: Bool
        let backwardLabel: String?
    }

    struct Heatmap: Codable, Hashable {
        let values: [[Double]]
        let rowLabels: [String]
        let colLabels: [String]
        let tone: FigureTone
        let marks: [FigureCell]
        let legend: String?
    }

    private enum TypeKey: String, CodingKey { case type }

    init(from decoder: Decoder) throws {
        let type = try decoder.container(keyedBy: TypeKey.self).decode(String.self, forKey: .type)
        switch type {
        case "Strip": self = .strip(try Strip(from: decoder))
        case "Timeline": self = .timeline(try Timeline(from: decoder))
        case "Grid": self = .grid(try Grid(from: decoder))
        case "Stacks": self = .stacks(try Stacks(from: decoder))
        case "Tree": self = .tree(try Tree(from: decoder))
        case "Graph": self = .graph(try Graph(from: decoder))
        case "Plot": self = .plot(try Plot(from: decoder))
        case "LayerStack": self = .layerStack(try LayerStack(from: decoder))
        case "Heatmap": self = .heatmap(try Heatmap(from: decoder))
        default:
            throw DecodingError.dataCorrupted(.init(codingPath: decoder.codingPath, debugDescription: "Unknown figure shape \(type)"))
        }
    }

    // Content is read-only on iOS; encoding exists only so the enum can be Codable.
    func encode(to encoder: Encoder) throws {}
}

struct FigureLayer: Codable, Hashable { let label: String; let detail: String?; let tone: FigureTone }
struct FigureSeries: Codable, Hashable { let label: String; let points: [FigurePoint]; let tone: FigureTone; let dashed: Bool }
struct FigurePoint: Codable, Hashable { let x: Double; let y: Double; let label: String?; let tone: FigureTone }
struct FigureBar: Codable, Hashable { let label: String; let value: Double; let tone: FigureTone }
struct FigureGraphNode: Codable, Hashable { let label: String; let x: Double; let y: Double; let tone: FigureTone }
struct FigureEdge: Codable, Hashable { let from: Int; let to: Int; let label: String?; let directed: Bool; let tone: FigureTone }
struct FigureNode: Codable, Hashable { let label: String; let parent: Int?; let tone: FigureTone }
struct FigureCell: Codable, Hashable { let row: Int; let col: Int; let tone: FigureTone }

struct FigureArrow: Codable, Hashable {
    let fromRow: Int
    let fromCol: Int
    let toRow: Int
    let toCol: Int
    let tone: FigureTone
    let label: String?
}

struct FigureStack: Codable, Hashable { let label: String; let entries: [String]; let tone: FigureTone; let note: String? }
struct FigureBand: Codable, Hashable { let from: Int; let to: Int; let label: String; let tone: FigureTone }
struct FigurePointer: Codable, Hashable { let index: Int; let label: String; let tone: FigureTone }
struct FigureSpan: Codable, Hashable { let start: Int; let end: Int; let label: String; let tone: FigureTone }

// MARK: - Quizzes

struct QuizStory: Codable, Hashable { let title: String; let text: String }

struct QuizQuestion: Codable, Hashable {
    let prompt: String
    let options: [String]
    let correctIndex: Int
    let patternTag: String
    let difficulty: Difficulty
    let explanation: String
    let linkedTopicId: String?
    let linkedTopicLabel: String?
    let figure: Figure?
    let story: QuizStory?
}

/// One budget for every set; the floor keeps a one-question retry from being a scramble.
let secondsPerQuestion = 45

struct Quiz: Codable, Hashable {
    let id: String
    let title: String
    let description: String
    let questions: [QuizQuestion]

    var timeLimitSeconds: Int { max(questions.count * secondsPerQuestion, 60) }
}

extension QuizQuestion {
    /// Every question is authored with the correct option first; shuffle once per attempt so the
    /// position carries no signal. The answer follows by identity.
    func withShuffledOptions() -> QuizQuestion {
        guard options.indices.contains(correctIndex) else { return self }
        let order = Array(options.indices).shuffled()
        return QuizQuestion(
            prompt: prompt, options: order.map { options[$0] },
            correctIndex: order.firstIndex(of: correctIndex)!, patternTag: patternTag,
            difficulty: difficulty, explanation: explanation, linkedTopicId: linkedTopicId,
            linkedTopicLabel: linkedTopicLabel, figure: figure, story: story
        )
    }
}

extension Quiz {
    func withShuffledOptions() -> Quiz {
        Quiz(id: id, title: title, description: description, questions: questions.map { $0.withShuffledOptions() })
    }
}

enum QuizMode { case interview, learn }

// MARK: - Problems

struct ProblemPrereq: Codable, Hashable { let topicId: String; let label: String; let why: String }
struct ProblemExample: Codable, Hashable { let input: String; let output: String; let note: String }

struct PracticeProblem: Codable, Hashable, Identifiable {
    let id: String
    let title: String
    let patternId: String
    let difficulty: Difficulty
    let prompt: String
    let examples: [ProblemExample]
    let constraints: [String]
    let hints: [String]
    let approach: [String]
    let timeComplexity: String
    let spaceComplexity: String
    let solutionCode: String
    let prerequisites: [ProblemPrereq]
    let linkedTopicId: String?
    let linkedTopicLabel: String?
}

struct ProblemPattern: Codable, Hashable, Identifiable {
    let id: String
    let name: String
    let blurb: String
    let accentColor: Int64
    let topicId: String
    let isPremium: Bool
}

// MARK: - Interview extras

struct BehavioralQuestion: Codable, Hashable { let prompt: String; let category: String; let assesses: String; let starTip: String }
struct BehavioralBank: Codable, Hashable { let id: String; let title: String; let description: String; let questions: [BehavioralQuestion] }
struct SystemDesignStep: Codable, Hashable { let title: String; let detail: String }
struct SystemDesignConcept: Codable, Hashable { let name: String; let category: String; let summary: String; let whenToUse: String }

struct SystemDesignPrimer: Codable, Hashable {
    let id: String
    let title: String
    let description: String
    let framework: [SystemDesignStep]
    let concepts: [SystemDesignConcept]
}

// MARK: - Flashcards

struct Flashcard: Codable, Hashable { let front: String; let back: String }
struct FlashcardDeck: Codable, Hashable { let id: String; let name: String; let cards: [Flashcard] }
