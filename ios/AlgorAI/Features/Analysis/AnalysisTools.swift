import SwiftUI

// Port of feature/analysis/tools/. Analysis "tools" are live dashboards, not prose topics: they
// bypass the 7-section template and render their own layout.

enum AnalysisToolRegistry {
    private static let ids: Set<String> = [
        "operation_counter", "growth_curve_chart", "growth_class_comparison", "complexity_class_comparison",
        "best_case", "worst_case", "average_case", "cost_profiler", "parameterized_input_generator",
        "amortized_analysis", "space_time_tradeoffs", "benchmark_dashboard", "real_vs_predicted_runtime",
        "input_size_scaling", "master_theorem", "complexity_map", "sandbox_mode",
    ]

    static func has(_ id: String) -> Bool { ids.contains(id) }

    @ViewBuilder static func view(for id: String) -> some View {
        switch id {
        case "operation_counter": OperationCounterTool()
        case "growth_curve_chart": GrowthCurveChartTool()
        case "growth_class_comparison": GrowthClassComparisonTool()
        case "complexity_class_comparison": ComplexityClassComparisonTool()
        case "best_case": CaseAnalysisTool(mode: .best)
        case "worst_case": CaseAnalysisTool(mode: .worst)
        case "average_case": CaseAnalysisTool(mode: .average)
        case "cost_profiler": CostProfilerTool()
        case "parameterized_input_generator": InputGeneratorTool()
        case "amortized_analysis": AmortizedAnalysisTool()
        case "space_time_tradeoffs": SpaceTimeTradeoffTool()
        case "benchmark_dashboard": BenchmarkTool(variant: .benchmark)
        case "real_vs_predicted_runtime": BenchmarkTool(variant: .realVsPredicted)
        case "input_size_scaling": BenchmarkTool(variant: .scaling)
        case "master_theorem": MasterTheoremTool()
        case "complexity_map": ComplexityMapTool()
        case "sandbox_mode": SandboxTool()
        default: EmptyView()
        }
    }
}

// MARK: - Operation counter

private enum SearchAlgo: CaseIterable {
    case linear, binary
    var label: String { self == .linear ? "Linear Search" : "Binary Search" }
    var complexityLabel: String { self == .linear ? "≈ O(n) comparisons, worst-case" : "≈ O(log n) comparisons, worst-case" }
}

private struct OperationCounterTool: View {
    @State private var algo = SearchAlgo.linear
    @State private var n = 16.0
    @State private var last: Int?
    @State private var history: [(n: Int, comparisons: Int)] = []
    @Environment(\.palette) private var palette

    var body: some View {
        AnalysisToolCard(intro: "Runs real search code on a sorted array and counts every comparison it makes — not an estimate.") {
            HStack(spacing: 8) {
                ForEach(SearchAlgo.allCases, id: \.self) { option in
                    ToolChoiceButton(label: option.label, selected: algo == option, color: SimColors.blue) { algo = option }
                }
            }
            .padding(.top, 14)
            ToolSlider(label: "Array size · n = \(Int(n))", value: $n, range: 4...64).padding(.top, 16)
            ToolRunButton(label: "▸ Run") {
                let size = Int(n)
                let sorted = (0..<size).map { $0 * 2 }
                let comparisons = algo == .linear ? InstrumentedAlgos.linearSearch(sorted, -1) : InstrumentedAlgos.binarySearch(sorted, -1)
                last = comparisons
                history.insert((size, comparisons), at: 0)
                if history.count > 6 { history.removeLast(history.count - 6) }
            }
            .padding(.top, 4)
            if let last {
                ToolBigStat(value: "\(last)", color: palette.primary, caption: "comparisons", detail: algo.complexityLabel).padding(.top, 18)
            }
            if !history.isEmpty {
                toolSubtitle("Recent runs")
                VStack(alignment: .leading, spacing: 6) {
                    ForEach(history.indices, id: \.self) { i in
                        Text("n = \(history[i].n) → \(history[i].comparisons) comparisons").font(.bodyMedium).foregroundStyle(palette.muted)
                    }
                }
            }
        }
    }
}

// MARK: - Growth curves

private struct GrowthCurveChartTool: View {
    @State private var maxN = 50.0

    var body: some View {
        let n = max(Int(maxN), 1)
        AnalysisToolCard(intro: "How operation count grows with input size n, across the complexity classes you'll see throughout this app. Y-axis is log-scaled so all five curves stay visible at once.") {
            ToolSlider(label: "Max n = \(n)", value: $maxN, range: 10...100, step: 10).padding(.top, 14).padding(.bottom, 6)
            ChartWell { MiniLineChart(curves: complexityCurves, maxN: n) }
            VStack(spacing: 8) {
                ForEach(complexityCurves) { curve in
                    CurveLegendRow(color: curve.color, label: curve.label, value: "≈ \(Int(curve.fn(Double(n)).rounded())) ops")
                }
            }
            .padding(.top, 18)
        }
    }
}

private struct GrowthClassComparisonTool: View {
    @State private var maxN = 50.0
    @State private var logScale = true

    var body: some View {
        let n = max(Int(maxN), 1)
        AnalysisToolCard(intro: "The same five growth classes on one chart. Toggle the scale: log spreads all curves apart so their shapes are readable; linear shows their true relative magnitude, where O(n²) dwarfs the rest.") {
            HStack(spacing: 8) {
                ToolChoiceButton(label: "Log scale", selected: logScale, color: SimColors.blue) { logScale = true }
                ToolChoiceButton(label: "Linear scale", selected: !logScale, color: SimColors.blue) { logScale = false }
            }
            .padding(.top, 14)
            ToolSlider(label: "Max n = \(n)", value: $maxN, range: 10...100, step: 10).padding(.top, 16).padding(.bottom, 6)
            ChartWell { MiniLineChart(curves: complexityCurves, maxN: n, logScale: logScale) }
            VStack(spacing: 8) {
                ForEach(complexityCurves) { curve in
                    CurveLegendRow(color: curve.color, label: curve.label, value: "≈ \(Int(curve.fn(Double(n)).rounded())) ops")
                }
            }
            .padding(.top, 18)
        }
    }
}

private struct ComplexityClassComparisonTool: View {
    @State private var n = 20.0
    @State private var hidden: Set<String> = []
    @Environment(\.palette) private var palette

    var body: some View {
        let nInt = max(Int(n), 1)
        AnalysisToolCard(intro: "Toggle complexity classes on and off to compare them directly. Each row shows the operation count at the current n; check a class to overlay its curve on the chart.") {
            ToolSlider(label: "n = \(nInt)", value: $n, range: 5...100, step: 5).padding(.top, 14).padding(.bottom, 6)
            ChartWell { MiniLineChart(curves: complexityCurves.filter { !hidden.contains($0.label) }, maxN: nInt) }
            VStack(spacing: 0) {
                ForEach(complexityCurves) { curve in
                    let checked = !hidden.contains(curve.label)
                    Button {
                        if checked { hidden.insert(curve.label) } else { hidden.remove(curve.label) }
                    } label: {
                        HStack(spacing: 0) {
                            Image(systemName: checked ? "checkmark.square.fill" : "square")
                                .font(.system(size: 20))
                                .foregroundStyle(checked ? palette.primary : palette.muted)
                                .frame(width: 44, height: 44)
                            CurveLegendRow(color: curve.color, label: curve.label, value: "\(Int(curve.fn(Double(nInt)).rounded())) ops", bold: true)
                        }
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(.top, 10)
        }
    }
}

// MARK: - Case analysis

enum CaseMode: CaseIterable {
    case best, worst, average

    var label: String {
        switch self { case .best: "Best"; case .worst: "Worst"; case .average: "Average" }
    }

    var accent: Color {
        switch self { case .best: SimColors.green; case .worst: SimColors.red; case .average: SimColors.amber }
    }

    var blurb: String {
        switch self {
        case .best: "Already-sorted input — the inner loop breaks on its first check."
        case .worst: "Reverse-sorted input — every element shifts all the way left."
        case .average: "Randomly shuffled input — expected halfway shifting."
        }
    }

    var formula: String {
        switch self {
        case .best: "≈ n − 1 comparisons"
        case .worst: "≈ n(n − 1) / 2 comparisons"
        case .average: "≈ n² / 4 comparisons"
        }
    }

    func input(_ n: Int) -> [Int] {
        switch self {
        case .best: Array(0..<n)
        case .worst: (0..<n).map { n - 1 - $0 }
        case .average: seededShuffledInput(n)
        }
    }
}

private struct CaseAnalysisTool: View {
    @State private var mode: CaseMode
    @State private var n = 8.0

    init(mode: CaseMode) { _mode = State(initialValue: mode) }

    var body: some View {
        let nInt = Int(n)
        let counts = Dictionary(uniqueKeysWithValues: CaseMode.allCases.map { ($0, InstrumentedAlgos.insertionSort($0.input(nInt))) })
        AnalysisToolCard(intro: "Insertion sort runs on a crafted input for each case. The comparison count is what the real sort executed — sorted input is fastest, reverse-sorted is the true worst case.") {
            HStack(spacing: 8) {
                ForEach(CaseMode.allCases, id: \.self) { option in
                    ToolChoiceButton(label: option.label, selected: mode == option, color: option.accent) { mode = option }
                }
            }
            .padding(.top, 14)
            ToolSlider(label: "Array size · n = \(nInt)", value: $n, range: 4...32).padding(.top, 16)
            ToolBigStat(value: "\(counts[mode] ?? 0)", color: mode.accent, caption: "comparisons · \(mode.label) case", detail: mode.formula, blurb: mode.blurb)
                .padding(.top, 12)
            toolSubtitle("Same n = \(nInt), all three cases")
            VStack(spacing: 8) {
                ForEach(CaseMode.allCases, id: \.self) { option in
                    HStack {
                        Text("\(option.label) case").font(AppFont.sans(14, .semibold)).frame(maxWidth: .infinity, alignment: .leading)
                        Text("\(counts[option] ?? 0) comparisons").font(AppFont.sans(14, .semibold)).foregroundStyle(option.accent)
                    }
                    .padding(.horizontal, 14)
                    .padding(.vertical, 12)
                    .background(option.accent.opacity(option == mode ? 0.16 : 0.07), in: RoundedRectangle(cornerRadius: 12))
                }
            }
        }
    }
}

// MARK: - Cost profiler

private enum ProfAlgo: CaseIterable {
    case linearSearch, insertionSort, bubbleSort

    var label: String {
        switch self { case .linearSearch: "Linear Search"; case .insertionSort: "Insertion Sort"; case .bubbleSort: "Bubble Sort" }
    }

    var accent: Color {
        switch self { case .linearSearch: SimColors.blue; case .insertionSort: SimColors.amber; case .bubbleSort: SimColors.red }
    }

    var complexity: String { self == .linearSearch ? "O(n)" : "O(n²)" }

    /// Worst-case run at size n.
    func profile(_ n: Int) -> Int {
        switch self {
        case .linearSearch: InstrumentedAlgos.linearSearch(Array(0..<n), -1)
        case .insertionSort: InstrumentedAlgos.insertionSort((0..<n).map { n - 1 - $0 })
        case .bubbleSort: InstrumentedAlgos.bubbleSort((0..<n).map { n - 1 - $0 })
        }
    }
}

private struct CostProfilerTool: View {
    @State private var algo = ProfAlgo.insertionSort
    @State private var maxN = 32.0

    var body: some View {
        let maxNInt = max(Int(maxN), 8)
        let step = max(maxNInt / 8, 1)
        let points = Array(stride(from: step, through: maxNInt, by: step)).map { ($0, algo.profile($0)) }
        AnalysisToolCard(intro: "Sweeps input size and runs the real algorithm at each step, plotting the operations it actually executed. The bar shape is the algorithm's growth curve, measured — not drawn.") {
            HStack(spacing: 8) {
                ForEach(ProfAlgo.allCases, id: \.self) { option in
                    ToolChoiceButton(label: option.label, selected: algo == option, color: option.accent) { algo = option }
                }
            }
            .padding(.top, 14)
            ToolSlider(label: "Max n = \(maxNInt) · \(algo.complexity)", value: $maxN, range: 8...64, step: 8).padding(.top, 16).padding(.bottom, 6)
            ChartWell { MiniBarChart(bars: points.map { ChartBar(label: "\($0.0)", value: Double($0.1), color: algo.accent) }) }
            toolSubtitle("Measured operations", top: 16)
            VStack(spacing: 6) {
                ForEach(points, id: \.0) { n, ops in ToolTableRow(left: "n = \(n)", right: "\(ops) ops") }
            }
        }
    }
}

// MARK: - Input generator

private enum Distribution: CaseIterable {
    case sorted, reversed, random, nearlySorted, duplicates

    var label: String {
        switch self {
        case .sorted: "Sorted"
        case .reversed: "Reversed"
        case .random: "Random"
        case .nearlySorted: "Nearly sorted"
        case .duplicates: "Duplicates"
        }
    }

    var blurb: String {
        switch self {
        case .sorted: "Best case — one comparison per element."
        case .reversed: "Worst case — every element shifts fully left."
        case .random: "Average case — expected halfway shifting."
        case .nearlySorted: "A few swaps from sorted — near best case."
        case .duplicates: "Few distinct values, repeated."
        }
    }

    func generate(_ n: Int) -> [Int] {
        switch self {
        case .sorted: return Array(0..<n)
        case .reversed: return (0..<n).map { n - 1 - $0 }
        case .random: return seededShuffledInput(n)
        case .nearlySorted:
            var arr = Array(0..<n)
            var rng = KotlinRandom(seed: n * 17 + 3)
            for _ in 0..<max(n / 8, 1) {
                let i = rng.nextInt(n - 1)
                arr.swapAt(i, i + 1)
            }
            return arr
        case .duplicates: return (0..<n).map { $0 % 3 }
        }
    }
}

private struct InputGeneratorTool: View {
    @State private var dist = Distribution.random
    @State private var n = 12.0

    var body: some View {
        let nInt = Int(n)
        let array = dist.generate(nInt)
        let comparisons = InstrumentedAlgos.insertionSort(array)
        AnalysisToolCard(intro: "Generate inputs of a chosen size and shape, then feed them to a real insertion sort. Same n, same algorithm — only the input shape changes, and the comparison count moves with it.") {
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(Distribution.allCases, id: \.self) { option in
                        ToolChoiceButton(label: option.label, selected: dist == option, color: SimColors.violet, fill: false) { dist = option }
                    }
                }
                .padding(1)
            }
            .padding(.top, 14)
            ToolSlider(label: "Array size · n = \(nInt)", value: $n, range: 6...24).padding(.top, 16)
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 6) {
                    ForEach(array.indices, id: \.self) { i in
                        Text("\(array[i])")
                            .font(AppFont.sans(12, .semibold))
                            .frame(width: 30, height: 30)
                            .background(SimColors.violet.opacity(0.12), in: RoundedRectangle(cornerRadius: 8))
                    }
                }
            }
            .padding(.vertical, 4)
            ToolBigStat(value: "\(comparisons)", color: SimColors.violet, caption: "comparisons to sort this \(dist.label.lowercased()) input", blurb: dist.blurb)
                .padding(.top, 14)
        }
    }
}

// MARK: - Amortized analysis

private struct AmortizedAnalysisTool: View {
    @State private var pushes = 16.0
    @Environment(\.palette) private var palette

    var body: some View {
        let count = max(Int(pushes), 1)
        let costs = InstrumentedAlgos.dynamicArrayPushCosts(count)
        let total = costs.reduce(0, +)
        AnalysisToolCard(intro: "A dynamic array doubles its backing store when full, copying existing elements. Most pushes cost 1; a resize spikes. Averaged over the whole sequence, the cost per push stays constant — that's amortized O(1).") {
            ToolSlider(label: "Pushes = \(count)", value: $pushes, range: 1...32).padding(.top, 14).padding(.bottom, 6)
            ChartWell {
                MiniBarChart(bars: costs.enumerated().map { i, c in ChartBar(label: "\(i + 1)", value: Double(c), color: c > 1 ? SimColors.red : SimColors.green) })
            }
            HStack {
                statBlock("Total cost", "\(total)")
                statBlock("Amortized / push", String(format: "%.2f", Double(total) / Double(count)))
            }
            .padding(.top, 16)
            Text("Worst single push here: \(costs.max() ?? 0) · yet the average stays near a small constant as pushes grow.")
                .font(AppFont.sans(12)).foregroundStyle(palette.muted).padding(.top, 10)
        }
    }

    private func statBlock(_ label: String, _ value: String) -> some View {
        VStack(spacing: 0) {
            Text(value).font(AppFont.grotesk(24, .bold)).foregroundStyle(palette.primary)
            Text(label).font(AppFont.sans(12)).foregroundStyle(palette.muted)
        }
        .frame(maxWidth: .infinity)
    }
}

// MARK: - Space-time tradeoff

private struct SpaceTimeTradeoffTool: View {
    @State private var n = 24.0
    @Environment(\.palette) private var palette

    var body: some View {
        let nInt = Int(n)
        // Absent target → both approaches run their full worst case.
        let array = Array(0..<nInt)
        let brute = InstrumentedAlgos.twoSumBrute(array, -1)
        let hash = InstrumentedAlgos.twoSumHash(array, -1)
        AnalysisToolCard(intro: "Two ways to solve two-sum. Brute force checks every pair — O(n²) time, O(1) extra space. The hash approach trades memory for speed — O(n) time, O(n) extra space. Same problem, opposite ends of the space-time tradeoff.") {
            ToolSlider(label: "Array size · n = \(nInt)", value: $n, range: 8...48).padding(.top, 14).padding(.bottom, 6)
            approach("Brute force", SimColors.red, "\(brute) pair checks", "O(n²) time", "0 extra cells", "O(1) space")
            approach("Hash set", SimColors.green, "\(hash.ops) lookups", "O(n) time", "\(hash.memory) extra cells", "O(n) space").padding(.top, 10)
            Text("At n = \(nInt) the hash approach does \(brute - hash.ops) fewer operations, paying \(hash.memory) cells of memory for it.")
                .font(AppFont.sans(12)).foregroundStyle(palette.muted).padding(.top, 12)
        }
    }

    private func approach(_ name: String, _ accent: Color, _ time: String, _ timeC: String, _ space: String, _ spaceC: String) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            Text(name).font(AppFont.grotesk(18, .semibold)).foregroundStyle(accent)
            HStack {
                metric("Time", time, timeC)
                metric("Space", space, spaceC)
            }
            .padding(.top, 8)
        }
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(accent.opacity(0.08), in: RoundedRectangle(cornerRadius: 12))
    }

    private func metric(_ label: String, _ value: String, _ complexity: String) -> some View {
        VStack(spacing: 0) {
            Text(label).font(AppFont.sans(12, .medium)).foregroundStyle(palette.muted)
            Text(value).font(AppFont.sans(14, .semibold))
            Text(complexity).font(AppFont.sans(12)).foregroundStyle(palette.muted)
        }
        .frame(maxWidth: .infinity)
    }
}

// MARK: - Benchmarks

enum BenchVariant {
    case benchmark, realVsPredicted, scaling

    var intro: String {
        switch self {
        case .benchmark: "Measures real wall-clock time of an insertion sort across growing inputs. Each point is the average of several timed runs after a warmup — on-device timing is noisy, so read the curve's shape, not the absolute microseconds."
        case .realVsPredicted: "Compares measured runtime against the O(n²) prediction. The predicted column scales the first measured point by (n / n₀)² — if the algorithm really is quadratic, measured and predicted track each other as n grows."
        case .scaling: "Shows how measured runtime responds to input size. For an O(n²) algorithm, doubling n should roughly quadruple the time — watch the ratio column settle near 4× as inputs grow."
        }
    }
}

private struct BenchPoint { let n: Int; let micros: Double }

// Sizes double, so each step should land near 4× the previous time.
private let benchSizes = [250, 500, 1000, 2000, 4000]

/// Warmup, then the average of four timed runs on reverse-sorted input. Runs off the main actor.
private func runBenchmark() async -> [BenchPoint] {
    await Task.detached(priority: .userInitiated) {
        benchSizes.map { n in
            let base = (0..<n).map { n - 1 - $0 }
            _ = InstrumentedAlgos.insertionSort(base)
            let trials = 4
            var totalNs: UInt64 = 0
            for _ in 0..<trials {
                let start = DispatchTime.now().uptimeNanoseconds
                _ = InstrumentedAlgos.insertionSort(base)
                totalNs += DispatchTime.now().uptimeNanoseconds - start
            }
            return BenchPoint(n: n, micros: Double(totalNs / UInt64(trials)) / 1000)
        }
    }.value
}

private struct BenchmarkTool: View {
    let variant: BenchVariant
    @State private var results: [BenchPoint]?
    @State private var running = false
    @Environment(\.palette) private var palette

    var body: some View {
        AnalysisToolCard(intro: variant.intro) {
            ToolRunButton(label: running ? "Running…" : "▸ Run benchmark") {
                guard !running else { return }
                running = true
                Task {
                    results = await runBenchmark()
                    running = false
                }
            }
            .padding(.top, 14)
            if let data = results {
                ChartWell { MiniBarChart(bars: data.map { ChartBar(label: "\($0.n)", value: $0.micros, color: SimColors.green) }) }
                switch variant {
                case .benchmark:
                    header("n", "measured")
                    rows(data.map { ("\($0.n)", "\(fmt($0.micros)) µs") })
                case .realVsPredicted:
                    header("n", "measured  ·  predicted")
                    rows(data.map { p in
                        let ratio = Double(p.n) / Double(data[0].n)
                        return ("\(p.n)", "\(fmt(p.micros))  ·  \(fmt(data[0].micros * ratio * ratio)) µs")
                    })
                case .scaling:
                    header("n", "measured  ·  ×prev")
                    rows(data.indices.map { i in
                        let ratio = i == 0 ? "—" : "\(fmt(data[i].micros / data[i - 1].micros))×"
                        return ("\(data[i].n)", "\(fmt(data[i].micros))  ·  \(ratio)")
                    })
                }
                Text("Timing is measured on this device and varies run to run — the trend is the signal.")
                    .font(AppFont.sans(12)).foregroundStyle(palette.muted).padding(.top, 12)
            } else {
                Text("Tap run to time real insertion sorts at n = \(benchSizes.first!)…\(benchSizes.last!).")
                    .font(.bodyMedium).foregroundStyle(palette.muted).padding(.top, 14)
            }
        }
    }

    private func fmt(_ v: Double) -> String { String(format: "%.1f", v) }

    private func header(_ left: String, _ right: String) -> some View {
        HStack {
            Text(left).font(.titleMedium).frame(maxWidth: .infinity, alignment: .leading)
            Text(right).font(.titleMedium)
        }
        .padding(.top, 16).padding(.bottom, 8)
    }

    private func rows(_ items: [(String, String)]) -> some View {
        VStack(spacing: 6) {
            ForEach(items.indices, id: \.self) { ToolTableRow(left: items[$0].0, right: items[$0].1) }
        }
    }
}

// MARK: - Master theorem

private struct MasterPreset { let label: String; let a: Int; let b: Int; let d: Int }

private let masterPresets = [
    MasterPreset(label: "Merge sort", a: 2, b: 2, d: 1),
    MasterPreset(label: "Binary search", a: 1, b: 2, d: 0),
    MasterPreset(label: "Karatsuba", a: 3, b: 2, d: 1),
    MasterPreset(label: "Naive matrix mult", a: 8, b: 2, d: 2),
]

/// Polynomial term for an exponent: n^0 → "1", n^1 → "n", else "n^k".
private func poly(_ exp: Double) -> String {
    let rounded = exp.rounded()
    let label = abs(exp - rounded) < 1e-9 ? "\(Int(rounded))" : String(format: "%.2f", exp)
    switch label {
    case "0": return "1"
    case "1": return "n"
    default: return "n^\(label)"
    }
}

private struct MasterTheoremTool: View {
    @State private var a = 2
    @State private var b = 2
    @State private var d = 1
    @Environment(\.palette) private var palette

    var body: some View {
        let logBA = log(Double(a)) / log(Double(b))
        let (caseNum, caseDesc, result): (Int, String, String) = {
            if Double(d) < logBA - 1e-9 { return (1, "d < log_b(a) — the leaves dominate.", "Θ(\(poly(logBA)))") }
            if Double(d) > logBA + 1e-9 { return (3, "d > log_b(a) — the root work dominates.", "Θ(\(poly(Double(d))))") }
            let p = poly(Double(d))
            return (2, "d = log_b(a) — work is even across levels.", p == "1" ? "Θ(log n)" : "Θ(\(p) log n)")
        }()
        AnalysisToolCard(intro: "Solves divide-and-conquer recurrences of the form T(n) = a·T(n/b) + Θ(n^d). Set a, b, d and it picks the case by comparing d to log_b(a).") {
            Text("T(n) = a·T(n/b) + Θ(n^d)").font(AppFont.grotesk(18, .bold)).padding(.top, 14).padding(.bottom, 6)
            stepper("a — subproblems", $a, 1, 16)
            stepper("b — shrink factor", $b, 2, 16)
            stepper("d — exponent of f(n)", $d, 0, 5)
            VStack(spacing: 0) {
                Text("log_b(a) = \(String(format: "%.3f", logBA))   vs   d = \(d)").font(.bodyMedium).foregroundStyle(palette.muted)
                Text(result).font(AppFont.grotesk(24, .bold)).foregroundStyle(SimColors.blue).padding(.top, 6)
                Text("Case \(caseNum) · \(caseDesc)").font(AppFont.sans(12)).foregroundStyle(palette.muted).multilineTextAlignment(.center).padding(.top, 6)
            }
            .padding(16)
            .frame(maxWidth: .infinity)
            .background(SimColors.blue.opacity(0.10), in: RoundedRectangle(cornerRadius: 14))
            .padding(.top, 16)
            toolSubtitle("Examples", top: 18)
            LazyVGrid(columns: [GridItem(.flexible(), spacing: 8), GridItem(.flexible(), spacing: 8)], spacing: 8) {
                ForEach(masterPresets, id: \.label) { preset in
                    Button { a = preset.a; b = preset.b; d = preset.d } label: {
                        Text(preset.label)
                            .font(AppFont.sans(12, .medium))
                            .foregroundStyle(palette.primary)
                            .lineLimit(1)
                            .minimumScaleFactor(0.8)
                            .frame(maxWidth: .infinity)
                            .frame(height: 40)
                            .overlay(Capsule().stroke(palette.outline, lineWidth: 1))
                            .contentShape(Capsule())
                    }
                    .buttonStyle(.plain)
                }
            }
        }
    }

    private func stepper(_ label: String, _ value: Binding<Int>, _ lo: Int, _ hi: Int) -> some View {
        HStack(spacing: 0) {
            Text(label).font(.bodyMedium).frame(maxWidth: .infinity, alignment: .leading)
            stepButton("−") { if value.wrappedValue > lo { value.wrappedValue -= 1 } }
            Text("\(value.wrappedValue)").font(AppFont.grotesk(18, .bold)).frame(width: 40, height: 40)
            stepButton("+") { if value.wrappedValue < hi { value.wrappedValue += 1 } }
        }
        .padding(.vertical, 4)
    }

    private func stepButton(_ symbol: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(symbol)
                .font(AppFont.sans(16, .medium))
                .foregroundStyle(palette.primary)
                .frame(width: 40, height: 40)
                .overlay(Circle().stroke(palette.outline, lineWidth: 1))
                .contentShape(Circle())
        }
        .buttonStyle(.plain)
    }
}

// MARK: - Complexity map

private struct ComplexityBand { let label: String; let color: Color; let algos: [String] }

// Green (efficient) → red (intractable), top to bottom.
private let complexityBands = [
    ComplexityBand(label: "O(1)", color: Color(hex: 0x10B981), algos: ["Array access", "Hash lookup", "Stack push"]),
    ComplexityBand(label: "O(log n)", color: Color(hex: 0x22C55E), algos: ["Binary search", "BST insert", "Heap push"]),
    ComplexityBand(label: "O(n)", color: SimColors.amber, algos: ["Linear search", "Array scan", "BFS / DFS"]),
    ComplexityBand(label: "O(n log n)", color: Color(hex: 0xF97316), algos: ["Merge sort", "Heap sort", "Quicksort avg"]),
    ComplexityBand(label: "O(n²)", color: SimColors.red, algos: ["Bubble sort", "Insertion sort", "Selection sort"]),
    ComplexityBand(label: "O(2ⁿ)", color: Color(hex: 0xB91C1C), algos: ["Naive Fibonacci", "Subset gen", "TSP brute force"]),
]

private struct ComplexityMapTool: View {
    var body: some View {
        AnalysisToolCard(intro: "Where common algorithms land on the complexity spectrum — efficient classes in green at the top, intractable ones in red at the bottom.") {
            VStack(spacing: 0) {
                ForEach(complexityBands, id: \.label) { band in
                    HStack(spacing: 0) {
                        Circle().fill(band.color).frame(width: 10, height: 10)
                        Text(band.label).font(AppFont.grotesk(18, .bold)).foregroundStyle(band.color)
                            .frame(width: 96, alignment: .leading).padding(.leading, 8)
                        ScrollView(.horizontal, showsIndicators: false) {
                            HStack(spacing: 6) {
                                ForEach(band.algos, id: \.self) { algo in
                                    Text(algo)
                                        .font(AppFont.sans(12, .medium))
                                        .padding(.horizontal, 10)
                                        .padding(.vertical, 6)
                                        .background(band.color.opacity(0.12), in: RoundedRectangle(cornerRadius: 8))
                                }
                            }
                        }
                    }
                    .padding(.vertical, 8)
                }
            }
            .padding(.top, 14)
        }
    }
}

// MARK: - Sandbox

private enum SandAlgo: CaseIterable {
    case linearSearch, insertionSort, bubbleSort, quicksort

    var label: String {
        switch self {
        case .linearSearch: "Linear Search"
        case .insertionSort: "Insertion Sort"
        case .bubbleSort: "Bubble Sort"
        case .quicksort: "Quicksort"
        }
    }

    var short: String {
        switch self { case .linearSearch: "lin"; case .insertionSort: "ins"; case .bubbleSort: "bub"; case .quicksort: "qck" }
    }

    func run(_ arr: [Int]) -> Int {
        switch self {
        case .linearSearch: InstrumentedAlgos.linearSearch(arr, -1)
        case .insertionSort: InstrumentedAlgos.insertionSort(arr)
        case .bubbleSort: InstrumentedAlgos.bubbleSort(arr)
        case .quicksort: InstrumentedAlgos.quicksort(arr)
        }
    }
}

private enum SandShape: CaseIterable {
    case sorted, reversed, random

    var label: String {
        switch self { case .sorted: "Sorted"; case .reversed: "Reversed"; case .random: "Random" }
    }

    func build(_ n: Int) -> [Int] {
        switch self {
        case .sorted: Array(0..<n)
        case .reversed: (0..<n).map { n - 1 - $0 }
        case .random: seededShuffledInput(n)
        }
    }
}

private struct SandRun: Identifiable {
    let id = UUID()
    let algo: SandAlgo
    let shape: SandShape
    let n: Int
    let ops: Int
}

private struct SandboxTool: View {
    @State private var algo = SandAlgo.insertionSort
    @State private var shape = SandShape.reversed
    @State private var n = 16.0
    @State private var runs: [SandRun] = []
    @Environment(\.palette) private var palette

    var body: some View {
        let nInt = Int(n)
        AnalysisToolCard(intro: "Free-form playground. Pick an algorithm, an input shape, and a size, then run it — each run's real operation count stacks onto the chart so you can compare anything against anything.") {
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(SandAlgo.allCases, id: \.self) { option in
                        ToolChoiceButton(label: option.label, selected: algo == option, color: SimColors.blue, fill: false) { algo = option }
                    }
                }
                .padding(1)
            }
            .padding(.top, 14)
            HStack(spacing: 8) {
                ForEach(SandShape.allCases, id: \.self) { option in
                    ToolChoiceButton(label: option.label, selected: shape == option, color: SimColors.violet) { shape = option }
                }
            }
            .padding(.top, 10)
            ToolSlider(label: "Input size · n = \(nInt)", value: $n, range: 4...48).padding(.top, 12)
            HStack(spacing: 8) {
                ToolRunButton(label: "▸ Run") {
                    runs.insert(SandRun(algo: algo, shape: shape, n: nInt, ops: algo.run(shape.build(nInt))), at: 0)
                    if runs.count > 8 { runs.removeLast(runs.count - 8) }
                }
                if !runs.isEmpty {
                    Button("Clear") { runs.removeAll() }
                        .font(AppFont.sans(14, .medium))
                        .foregroundStyle(palette.primary)
                        .padding(.horizontal, 12)
                }
            }
            if !runs.isEmpty {
                ChartWell { MiniBarChart(bars: runs.reversed().map { ChartBar(label: "\($0.n)", value: Double($0.ops), color: SimColors.green) }) }
                    .padding(.top, 6)
                VStack(spacing: 6) {
                    ForEach(runs) { r in
                        ToolTableRow(left: "\(r.algo.short) · \(r.shape.label.lowercased()) · n=\(r.n)", right: "\(r.ops) ops")
                    }
                }
                .padding(.top, 12)
            }
        }
    }
}
