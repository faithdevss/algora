import SwiftUI

// Port of feature/analysis/tools/common/: the card chrome, curves, instrumented algorithms and
// mini charts every Analysis tool shares.

/// Card every Analysis tool sits in: 18pt radius, outline border, 16pt pad, muted intro line.
struct AnalysisToolCard<Content: View>: View {
    let intro: String
    @ViewBuilder let content: () -> Content
    @Environment(\.palette) private var palette

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text(intro).font(.bodyMedium).foregroundStyle(palette.muted)
            content()
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .card(radius: 18)
    }
}

/// A labeled, colored function of n.
struct Curve: Identifiable {
    let label: String
    let color: Color
    let fn: (Double) -> Double
    var id: String { label }
}

// Base-2, matching OperationCounterTool's real measured counts.
private func log2d(_ n: Double) -> Double { log(n) / log(2) }

let complexityCurves: [Curve] = [
    Curve(label: "O(1)", color: SimColors.blue) { _ in 1 },
    Curve(label: "O(log n)", color: Color(hex: 0x10B981)) { log2d($0) },
    Curve(label: "O(n)", color: SimColors.amber) { $0 },
    Curve(label: "O(n log n)", color: SimColors.violet) { $0 * log2d($0) },
    Curve(label: "O(n²)", color: SimColors.red) { $0 * $0 },
]

/// Real instrumented implementations: each returns the count of the operation it increments.
enum InstrumentedAlgos {
    static func linearSearch(_ arr: [Int], _ target: Int) -> Int {
        var comparisons = 0
        for value in arr {
            comparisons += 1
            if value == target { break }
        }
        return comparisons
    }

    static func binarySearch(_ arr: [Int], _ target: Int) -> Int {
        var comparisons = 0
        var lo = 0
        var hi = arr.count - 1
        while lo <= hi {
            let mid = lo + (hi - lo) / 2
            comparisons += 1
            if arr[mid] == target { break } else if arr[mid] < target { lo = mid + 1 } else { hi = mid - 1 }
        }
        return comparisons
    }

    /// Counts every evaluation of the inner `arr[j] > key` guard, including the terminal one.
    static func insertionSort(_ input: [Int]) -> Int {
        var arr = input
        var comparisons = 0
        guard arr.count > 1 else { return 0 }
        for i in 1..<arr.count {
            let key = arr[i]
            var j = i - 1
            while j >= 0 {
                comparisons += 1
                if arr[j] <= key { break }
                arr[j + 1] = arr[j]
                j -= 1
            }
            arr[j + 1] = key
        }
        return comparisons
    }

    static func bubbleSort(_ input: [Int]) -> Int {
        var arr = input
        var comparisons = 0
        guard arr.count > 1 else { return 0 }
        for i in 0..<(arr.count - 1) {
            for j in 0..<(arr.count - 1 - i) {
                comparisons += 1
                if arr[j] > arr[j + 1] { arr.swapAt(j, j + 1) }
            }
        }
        return comparisons
    }

    static func quicksort(_ input: [Int]) -> Int {
        var arr = input
        var comparisons = 0
        func sort(_ lo: Int, _ hi: Int) {
            if lo >= hi { return }
            let pivot = arr[hi]
            var i = lo
            for j in lo..<hi {
                comparisons += 1
                if arr[j] < pivot { arr.swapAt(i, j); i += 1 }
            }
            arr.swapAt(i, hi)
            sort(lo, i - 1)
            sort(i + 1, hi)
        }
        sort(0, arr.count - 1)
        return comparisons
    }

    /// O(n²) two-sum — the number of pair checks performed.
    static func twoSumBrute(_ arr: [Int], _ target: Int) -> Int {
        var ops = 0
        for i in arr.indices {
            for j in (i + 1)..<max(arr.count, i + 1) {
                ops += 1
                if arr[i] + arr[j] == target { return ops }
            }
        }
        return ops
    }

    /// O(n) two-sum — ops (lookups + inserts) and the peak size of the seen-set.
    static func twoSumHash(_ arr: [Int], _ target: Int) -> (ops: Int, memory: Int) {
        var ops = 0
        var seen = Set<Int>()
        for value in arr {
            ops += 1
            if seen.contains(target - value) { return (ops, seen.count) }
            seen.insert(value)
        }
        return (ops, seen.count)
    }

    /// Doubling dynamic-array push costs: 1, or 1 + old capacity when a resize copies.
    static func dynamicArrayPushCosts(_ pushes: Int) -> [Int] {
        var costs: [Int] = []
        var capacity = 1
        var size = 0
        for _ in 0..<pushes {
            var cost = 1
            if size == capacity {
                cost += capacity
                capacity *= 2
            }
            size += 1
            costs.append(cost)
        }
        return costs
    }
}

/// `(0 until n).shuffled(Random(n * 31 + 7))`, the seeded "random" input every tool shares.
func seededShuffledInput(_ n: Int) -> [Int] {
    var rng = KotlinRandom(seed: n * 31 + 7)
    return Array(0..<n).kotlinShuffled(&rng)
}

/// Tinted well the charts sit in.
struct ChartWell<Content: View>: View {
    @ViewBuilder let content: () -> Content
    @Environment(\.palette) private var palette

    var body: some View {
        content()
            .padding(6)
            .frame(maxWidth: .infinity)
            .background(palette.outlineVariant.opacity(0.3), in: RoundedRectangle(cornerRadius: 14))
    }
}

/// Line chart over n = 1...maxN. Log scale keeps steep curves from flattening the gentle ones.
struct MiniLineChart: View {
    let curves: [Curve]
    let maxN: Int
    var logScale = true
    var height: CGFloat = 220
    @Environment(\.palette) private var palette

    var body: some View {
        Canvas { ctx, size in
            guard !curves.isEmpty, maxN >= 1 else { return }
            let maxValue = max(curves.map { $0.fn(Double(maxN)) }.max() ?? 1, 1)
            let logMax = log(maxValue + 1)
            let pad: CGFloat = 8
            func x(_ n: Int) -> CGFloat { pad + CGFloat(n) / CGFloat(maxN) * (size.width - 2 * pad) }
            func y(_ v: Double) -> CGFloat {
                let norm = logScale ? log(v + 1) / logMax : v / maxValue
                return size.height - pad - CGFloat(min(max(norm, 0), 1)) * (size.height - 2 * pad)
            }
            var axes = Path()
            axes.move(to: CGPoint(x: pad, y: pad))
            axes.addLine(to: CGPoint(x: pad, y: size.height - pad))
            axes.addLine(to: CGPoint(x: size.width - pad, y: size.height - pad))
            ctx.stroke(axes, with: .color(palette.outline), lineWidth: 2)
            let step = max(maxN / 60, 1)
            for curve in curves {
                var path = Path()
                var n = 1
                while n <= maxN {
                    let point = CGPoint(x: x(n), y: y(curve.fn(Double(n))))
                    if n == 1 { path.move(to: point) } else { path.addLine(to: point) }
                    n += step
                }
                ctx.stroke(path, with: .color(curve.color), lineWidth: 3)
            }
        }
        .frame(height: height)
    }
}

struct ChartBar {
    let label: String
    let value: Double
    let color: Color
}

/// Vertical bar chart; labels thin out to every k-th slot when they would overlap.
struct MiniBarChart: View {
    let bars: [ChartBar]
    var logScale = false
    var height: CGFloat = 180
    @Environment(\.palette) private var palette

    var body: some View {
        Canvas { ctx, size in
            guard !bars.isEmpty else { return }
            let maxValue = max(bars.map(\.value).max() ?? 1, 1)
            let logMax = log(maxValue + 1)
            let pad: CGFloat = 8
            let slot = (size.width - 2 * pad) / CGFloat(bars.count)
            let barWidth = slot * 0.6
            let labels = bars.map { ctx.resolve(Text($0.label).font(AppFont.mono(10)).foregroundStyle(palette.muted)) }
            let labelSizes = labels.map { $0.measure(in: size) }
            let labelHeight = labelSizes.map(\.height).max() ?? 0
            let axisY = size.height - pad - labelHeight - 4
            let plotHeight = axisY - pad
            var axis = Path()
            axis.move(to: CGPoint(x: pad, y: axisY))
            axis.addLine(to: CGPoint(x: size.width - pad, y: axisY))
            ctx.stroke(axis, with: .color(palette.outline), lineWidth: 2)
            let widest = labelSizes.map(\.width).max() ?? 0
            let stride = max(Int(ceil((widest + 6) / slot)), 1)
            for (i, bar) in bars.enumerated() {
                let norm = logScale ? log(bar.value + 1) / logMax : bar.value / maxValue
                let h = CGFloat(min(max(norm, 0), 1)) * plotHeight
                let left = pad + CGFloat(i) * slot + (slot - barWidth) / 2
                ctx.fill(Path(CGRect(x: left, y: axisY - h, width: barWidth, height: h)), with: .color(bar.color))
                if i % stride == 0 {
                    let center = CGPoint(x: pad + CGFloat(i) * slot + slot / 2, y: axisY + 4 + labelSizes[i].height / 2)
                    ctx.draw(labels[i], at: center)
                }
            }
        }
        .frame(height: height)
    }
}

// MARK: - Controls

/// Filled-when-selected / outlined-otherwise button (the tools' Button vs OutlinedButton pair).
struct ToolChoiceButton: View {
    let label: String
    let selected: Bool
    let color: Color
    var fill = true
    let action: () -> Void
    @Environment(\.palette) private var palette

    var body: some View {
        Button(action: action) {
            Text(label)
                .font(AppFont.sans(14, .medium))
                .lineLimit(1)
                .minimumScaleFactor(0.7)
                .foregroundStyle(selected ? .white : palette.primary)
                .padding(.horizontal, 16)
                .frame(maxWidth: fill ? .infinity : nil)
                .frame(height: 40)
                .background(selected ? color : .clear, in: Capsule())
                .overlay(Capsule().stroke(selected ? .clear : palette.outline, lineWidth: 1))
                .contentShape(Capsule())
        }
        .buttonStyle(.plain)
    }
}

/// Bold caption over an integer slider.
struct ToolSlider: View {
    let label: String
    @Binding var value: Double
    let range: ClosedRange<Double>
    var step: Double = 1
    @Environment(\.palette) private var palette

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(label).font(AppFont.sans(14, .semibold))
            Slider(value: $value, in: range, step: step).tint(palette.primary)
        }
    }
}

/// Filled green run button.
struct ToolRunButton: View {
    let label: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(label)
                .font(AppFont.sans(14, .medium))
                .foregroundStyle(.white)
                .frame(maxWidth: .infinity)
                .frame(height: 40)
                .background(SimColors.green, in: Capsule())
        }
        .buttonStyle(.plain)
    }
}

/// Big 40pt number, caption and optional detail lines under it.
struct ToolBigStat: View {
    let value: String
    let color: Color
    let caption: String
    var detail: String?
    var blurb: String?
    @Environment(\.palette) private var palette

    var body: some View {
        VStack(spacing: 0) {
            Text(value).font(AppFont.grotesk(40, .bold)).foregroundStyle(color)
            Text(caption).font(.bodyMedium).foregroundStyle(palette.muted).multilineTextAlignment(.center)
            if let detail {
                Text(detail).font(.labelLarge).foregroundStyle(palette.muted).padding(.top, 4)
            }
            if let blurb {
                Text(blurb).font(AppFont.sans(12)).foregroundStyle(palette.muted).multilineTextAlignment(.center).padding(.top, 6)
            }
        }
        .frame(maxWidth: .infinity)
    }
}

/// Dot, label, trailing value — the curve legend rows.
struct CurveLegendRow: View {
    let color: Color
    let label: String
    let value: String
    var bold = false
    @Environment(\.palette) private var palette

    var body: some View {
        HStack(spacing: 0) {
            Circle().fill(color).frame(width: 10, height: 10)
            Text(label).font(.bodyMedium).padding(.leading, 8).frame(maxWidth: .infinity, alignment: .leading)
            Text(value).font(AppFont.sans(14, bold ? .semibold : .regular)).foregroundStyle(palette.muted)
        }
    }
}

/// Left label, right muted-bold value.
struct ToolTableRow: View {
    let left: String
    let right: String
    @Environment(\.palette) private var palette

    var body: some View {
        HStack {
            Text(left).font(.bodyMedium).frame(maxWidth: .infinity, alignment: .leading)
            Text(right).font(AppFont.sans(14, .semibold)).foregroundStyle(palette.muted)
        }
    }
}

func toolSubtitle(_ text: String, top: CGFloat = 20) -> some View {
    Text(text).font(.titleMedium).padding(.top, top).padding(.bottom, 8)
}
