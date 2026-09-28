import SwiftUI

// Port of SearchVisualizerSection.kt, built to docs/ios-design/Simulations iOS.html (Binary Search).
// One step per probe: the array with the probe in yellow, the live window in blue and ruled-out cells
// greyed, lo/mid/hi markers under the indices, a running list of every probe so far and its outcome,
// then chips, a headline naming the decision and a sentence on what it cost.

/// One row of the probe list: "a[7] = 61 > 50" and what it decided, "hi = 6".
private struct ProbeLine { let number: Int; let expr: String; let outcome: String }

/// Headline in three parts so the middle word can take the probe yellow (or the found green).
private struct SearchHeadline {
    let lead: String
    let emphasis: String
    let tail: String
    var plain: String { lead + emphasis + tail }
}

private struct SearchFrame {
    let probe: Int?
    let window: Set<Int>
    let eliminated: Set<Int>
    let found: Int?
    let status: String
    /// Index -> labels drawn under it (lo, mid, hi …); several can share one index.
    var pointers: [Int: [String]] = [:]
    var log: [ProbeLine] = []
    /// The index the following step probes, shown as a dimmed placeholder row under the list.
    var next: Int?
    var chips: [(String, String)] = []
    var headline: SearchHeadline?
    var body: String?
}

private struct SearchConfig {
    let values: [Int]
    let target: Int
    /// Card heading; the size is appended, e.g. "SORTED ARRAY · N = 10".
    let title: String
    let windowLabel: String
    let build: () -> [SearchFrame]
}

/// Dark text on the yellow probe cell: white on #F5C542 is unreadable.
private let onProbeCell = Color(hex: 0x1F1A0A)

/// The target badge: "the answer" violet, SimColors.Answer on Android.
private let answerViolet = Color(hex: 0x7C5CFF)

/// Pointer labels that name the probe itself take its yellow; the bounds take the window's blue.
private let probeLabels: Set<String> = ["mid", "pos", "i", "jump", "bound"]

private let sortedInput = [3, 8, 15, 27, 34, 42, 50, 61, 73, 88]
private let sortedTarget = 50
/// Unsorted on purpose — linear search is the one algorithm with no ordering precondition.
private let unsortedInput = [42, 8, 27, 61, 15, 34, 3, 50]
private let unsortedTarget = 34
private let rotatedInput = [27, 34, 42, 50, 61, 73, 88, 3, 8, 15]
private let rotatedTarget = 8

private func range(_ a: Int, _ b: Int) -> Set<Int> { a <= b ? Set(a...b) : [] }

private let probeWords = ["One probe has", "Two probes have", "Three probes have", "Four probes have", "Five probes have"]
private func probesHave(_ k: Int) -> String { k >= 1 && k <= probeWords.count ? probeWords[k - 1] : "\(k) probes have" }
private func cut(_ k: Int, _ n: Int, _ after: Int) -> String {
    after == 0 ? "\(probesHave(k)) emptied the window." : "\(probesHave(k)) cut \(n) candidates down to \(after)."
}

private func marks(_ pairs: (Int, String)...) -> [Int: [String]] {
    var out: [Int: [String]] = [:]
    for (index, label) in pairs { out[index, default: []].append(label) }
    return out
}

private func worstCaseProbes(_ n: Int) -> Int { Int(ceil(log2(Double(n + 1)))) }

private final class SearchBuilder {
    let values: [Int]
    let target: Int
    private var frames: [SearchFrame] = []
    private var log: [ProbeLine] = []
    var eliminated = Set<Int>()

    init(_ values: [Int], _ target: Int) { self.values = values; self.target = target }

    /// The number the probe being added will carry.
    var number: Int { log.count + 1 }

    func probe(_ index: Int, _ window: Set<Int>, _ pointers: [Int: [String]], _ expr: String, _ outcome: String,
               _ chips: [(String, String)], _ headline: SearchHeadline, _ body: String, found: Bool = false) {
        log.append(ProbeLine(number: log.count + 1, expr: expr, outcome: outcome))
        frames.append(SearchFrame(probe: index, window: window, eliminated: eliminated, found: found ? index : nil,
                                  status: headline.plain, pointers: pointers, log: log, chips: chips,
                                  headline: headline, body: body))
    }

    func rule(_ a: Int, _ b: Int) { eliminated.formUnion(range(a, b)) }

    func notFound(_ detail: String) {
        let headline = SearchHeadline(lead: "The window is ", emphasis: "empty", tail: ": \(target) is not in the array.")
        frames.append(SearchFrame(probe: nil, window: [], eliminated: eliminated, found: nil, status: headline.plain,
                                  log: log, headline: headline, body: detail))
    }

    func build() -> [SearchFrame] {
        var out = frames
        for i in out.indices where i + 1 < out.count { out[i].next = out[i + 1].probe }
        return out
    }

    /// The discard-half loop, shared by binary search and the second phase of exponential search.
    func binaryPhase(_ startLo: Int, _ startHi: Int, foundBody: (Int) -> String) {
        var lo = startLo, hi = startHi
        while lo <= hi {
            let mid = (lo + hi) / 2
            let v = values[mid]
            let before = hi - lo + 1
            let window = range(lo, hi)
            let ptrs = marks((lo, "lo"), (mid, "mid"), (hi, "hi"))
            if v == target {
                probe(mid, window, ptrs, "a[\(mid)] = \(v)", "found", [("lo", "\(lo)"), ("hi", "\(hi)"), ("mid", "\(mid)")],
                      SearchHeadline(lead: "a[\(mid)] is \(target): ", emphasis: "found", tail: " on probe \(number)."),
                      foundBody(number), found: true)
                return
            }
            if v < target {
                let after = hi - mid
                probe(mid, window, ptrs, "a[\(mid)] = \(v) < \(target)", "lo = \(mid + 1)",
                      [("lo", "\(lo)"), ("hi", "\(hi)"), ("mid", "\(mid)"), ("window", "\(before) → \(after)")],
                      SearchHeadline(lead: "\(v) is smaller than \(target), so the target is right of ", emphasis: "mid", tail: "."),
                      "lo moves to \(mid + 1). \(cut(number, values.count, after))")
                rule(lo, mid)
                lo = mid + 1
            } else {
                let after = mid - lo
                probe(mid, window, ptrs, "a[\(mid)] = \(v) > \(target)", "hi = \(mid - 1)",
                      [("lo", "\(lo)"), ("hi", "\(hi)"), ("mid", "\(mid)"), ("window", "\(before) → \(after)")],
                      SearchHeadline(lead: "\(v) is bigger than \(target), so the target is left of ", emphasis: "mid", tail: "."),
                      "hi moves to \(mid - 1). \(cut(number, values.count, after))")
                rule(mid, hi)
                hi = mid - 1
            }
        }
        notFound("lo passed hi, so every candidate has been ruled out.")
    }
}

private func binaryFrames() -> [SearchFrame] {
    let b = SearchBuilder(sortedInput, sortedTarget)
    let n = sortedInput.count
    b.binaryPhase(0, n - 1) { probes in
        "⌈log₂(\(n + 1))⌉ = \(worstCaseProbes(n)) probes is the worst case for \(n) elements; this took \(probes). A linear scan could have needed all \(n)."
    }
    return b.build()
}

private func linearFrames() -> [SearchFrame] {
    let values = unsortedInput, t = unsortedTarget, n = values.count
    let b = SearchBuilder(values, t)
    for (i, v) in values.enumerated() {
        let chips = [("i", "\(i)"), ("checked", "\(i + 1) / \(n)")]
        if v == t {
            b.probe(i, range(i, n - 1), marks((i, "i")), "a[\(i)] = \(v)", "found", chips,
                    SearchHeadline(lead: "a[\(i)] is \(t): ", emphasis: "found", tail: " after \(i + 1) checks."),
                    "Linear search needs no ordering, but it pays for that: on average it checks half the array, and all of it when the target is missing.",
                    found: true)
            return b.build()
        }
        b.probe(i, range(i, n - 1), marks((i, "i")), "a[\(i)] = \(v) ≠ \(t)", "next", chips,
                SearchHeadline(lead: "\(v) is not \(t), so ", emphasis: "move on", tail: "."),
                "Unsorted data gives no hint where \(t) might be. \(i + 1) of \(n) checked, \(n - i - 1) left.")
        b.rule(i, i)
    }
    b.notFound("All \(n) elements checked — linear search only knows the target is missing after every one.")
    return b.build()
}

private func jumpFrames() -> [SearchFrame] {
    let values = sortedInput, t = sortedTarget, n = values.count
    let step = 3 // ≈ √10, rounded for a legible block size
    let b = SearchBuilder(values, t)
    var block = 0
    while block < n {
        let end = min(block + step - 1, n - 1)
        let v = values[end]
        let window = range(block, end)
        let ptrs = marks((block, "from"), (end, "jump"))
        let chips = [("block", "\(block)–\(end)"), ("step", "\(step)")]
        if v == t {
            b.probe(end, window, ptrs, "a[\(end)] = \(v)", "found", chips,
                    SearchHeadline(lead: "The block ends on \(t): ", emphasis: "found", tail: " on probe \(b.number)."),
                    "Jumping \(step) at a time reached it without a scan.", found: true)
            return b.build()
        }
        if v < t {
            b.probe(end, window, ptrs, "a[\(end)] = \(v) < \(t)", "skip → \(end + 1)", chips,
                    SearchHeadline(lead: "\(v) is below \(t), so ", emphasis: "skip", tail: " the whole block."),
                    "Everything in [\(block)..\(end)] is at most \(v). Jump \(step) ahead to index \(end + 1).")
            b.rule(block, end)
            block += step
            continue
        }
        b.probe(end, window, ptrs, "a[\(end)] = \(v) > \(t)", "scan \(block)–\(end)", chips,
                SearchHeadline(lead: "\(v) passes \(t), so the target is ", emphasis: "inside", tail: " this block."),
                "Step back to index \(block) and walk the block one element at a time — at most \(end - block) more checks.")
        for i in block..<end {
            let vi = values[i]
            let scanChips = [("i", "\(i)"), ("block", "\(block)–\(end)")]
            if vi == t {
                b.probe(i, range(i, end), marks((i, "i"), (end, "jump")), "a[\(i)] = \(vi)", "found", scanChips,
                        SearchHeadline(lead: "a[\(i)] is \(t): ", emphasis: "found", tail: " on probe \(b.number)."),
                        "\(b.number) probes: \(block / step + 1) jumps, then a short scan. About 2√n in the worst case, against n for a plain scan.",
                        found: true)
                return b.build()
            }
            b.probe(i, range(i, end), marks((i, "i"), (end, "jump")), "a[\(i)] = \(vi) < \(t)", "next", scanChips,
                    SearchHeadline(lead: "\(vi) is below \(t), keep ", emphasis: "scanning", tail: "."),
                    "Inside the block the search is linear.")
            b.rule(i, i)
        }
        break
    }
    b.notFound("No block can hold \(t).")
    return b.build()
}

private func interpolationFrames() -> [SearchFrame] {
    let values = sortedInput, t = sortedTarget
    let b = SearchBuilder(values, t)
    var lo = 0, hi = values.count - 1
    while lo <= hi && t >= values[lo] && t <= values[hi] {
        let span = values[hi] - values[lo]
        let pos = span == 0 ? lo : lo + (t - values[lo]) * (hi - lo) / span
        let v = values[pos]
        let before = hi - lo + 1
        let window = range(lo, hi)
        let ptrs = marks((lo, "lo"), (pos, "pos"), (hi, "hi"))
        let formula = "pos = \(lo) + (\(t) − \(values[lo]))·(\(hi) − \(lo)) / (\(values[hi]) − \(values[lo])) = \(pos)."
        if v == t {
            b.probe(pos, window, ptrs, "a[\(pos)] = \(v)", "found", [("lo", "\(lo)"), ("hi", "\(hi)"), ("pos", "\(pos)")],
                    SearchHeadline(lead: "The estimate lands on \(t): ", emphasis: "found", tail: " on probe \(b.number)."),
                    "\(formula) On evenly spread values the estimate lands close, which is why interpolation can beat binary search's \(worstCaseProbes(values.count)).",
                    found: true)
            return b.build()
        }
        if v < t {
            let after = hi - pos
            b.probe(pos, window, ptrs, "a[\(pos)] = \(v) < \(t)", "lo = \(pos + 1)",
                    [("lo", "\(lo)"), ("hi", "\(hi)"), ("pos", "\(pos)"), ("window", "\(before) → \(after)")],
                    SearchHeadline(lead: "The estimate reads \(v), below \(t), so search ", emphasis: "right", tail: "."),
                    "\(formula) lo moves to \(pos + 1).")
            b.rule(lo, pos)
            lo = pos + 1
        } else {
            let after = pos - lo
            b.probe(pos, window, ptrs, "a[\(pos)] = \(v) > \(t)", "hi = \(pos - 1)",
                    [("lo", "\(lo)"), ("hi", "\(hi)"), ("pos", "\(pos)"), ("window", "\(before) → \(after)")],
                    SearchHeadline(lead: "The estimate reads \(v), above \(t), so search ", emphasis: "left", tail: "."),
                    "\(formula) hi moves to \(pos - 1).")
            b.rule(pos, hi)
            hi = pos - 1
        }
    }
    b.notFound("\(t) is outside the remaining range.")
    return b.build()
}

private func exponentialFrames() -> [SearchFrame] {
    let values = sortedInput, t = sortedTarget, n = values.count
    let b = SearchBuilder(values, t)
    if values[0] == t {
        b.probe(0, [0], marks((0, "bound")), "a[0] = \(values[0])", "found", [("bound", "0")],
                SearchHeadline(lead: "a[0] is \(t): ", emphasis: "found", tail: " on the first probe."), "No doubling needed.", found: true)
        return b.build()
    }
    var bound = 1
    while bound < n && values[bound] < t {
        b.probe(bound, range(0, bound), marks((bound, "bound")), "a[\(bound)] = \(values[bound]) < \(t)", "bound = \(bound * 2)",
                [("bound", "\(bound)")],
                SearchHeadline(lead: "\(values[bound]) is below \(t), so ", emphasis: "double", tail: " the bound."),
                "Everything up to index \(bound) is too small. The next check is at \(bound * 2).")
        b.rule(0, bound)
        bound *= 2
    }
    let lo = bound / 2, hi = min(bound, n - 1)
    // The last doubling's own index is back inside the bracket.
    b.eliminated.subtract(range(lo, hi))
    let edge = values[hi]
    if edge == t {
        b.probe(hi, range(lo, hi), marks((hi, "bound")), "a[\(hi)] = \(edge)", "found", [("bound", "\(hi)")],
                SearchHeadline(lead: "a[\(hi)] is \(t): ", emphasis: "found", tail: " on probe \(b.number)."), "The doubling landed on it.", found: true)
        return b.build()
    }
    let pastEnd = bound >= n
    b.probe(hi, range(lo, hi), marks((lo, "lo"), (hi, "bound")),
            pastEnd ? "bound \(bound) > end" : "a[\(hi)] = \(edge) > \(t)", "range \(lo)–\(hi)",
            [("lo", "\(lo)"), ("bound", "\(hi)")],
            SearchHeadline(lead: pastEnd ? "The bound ran past the end, so the target is " : "\(edge) passes \(t), so the target is ",
                           emphasis: "bracketed", tail: "."),
            "It must lie between index \(lo) and \(hi) — binary search just that range.")
    let bracketProbes = b.number - 1
    // a[hi] was only compared when the bound stayed inside the array; past the end it is still a candidate.
    b.binaryPhase(lo, pastEnd ? hi : hi - 1) { probes in
        "\(bracketProbes) probes to bracket it, \(probes - bracketProbes) inside. Doubling costs log of the target's position, not of the array — which is why this works on unbounded input."
    }
    return b.build()
}

/// Rotated sorted array: one half of every window is always sorted, which is enough to discard half.
private func rotatedFrames() -> [SearchFrame] {
    let values = rotatedInput, t = rotatedTarget
    let b = SearchBuilder(values, t)
    var lo = 0, hi = values.count - 1
    while lo <= hi {
        let mid = (lo + hi) / 2
        let v = values[mid]
        let before = hi - lo + 1
        let window = range(lo, hi)
        let ptrs = marks((lo, "lo"), (mid, "mid"), (hi, "hi"))
        if v == t {
            b.probe(mid, window, ptrs, "a[\(mid)] = \(v)", "found", [("lo", "\(lo)"), ("hi", "\(hi)"), ("mid", "\(mid)")],
                    SearchHeadline(lead: "a[\(mid)] is \(t): ", emphasis: "found", tail: " on probe \(b.number)."),
                    "Same O(log n) as an unrotated search — only the branch condition changed.", found: true)
            return b.build()
        }
        let leftSorted = values[lo] <= v
        let inLeft = leftSorted && t >= values[lo] && t < v
        let inRight = !leftSorted && t > v && t <= values[hi]
        let goLeft = inLeft || (!leftSorted && !inRight)
        let side = leftSorted ? "left" : "right"
        let span = leftSorted ? "[\(values[lo]), \(v)]" : "[\(v), \(values[hi])]"
        let holds = inLeft || inRight
        let newLo = goLeft ? lo : mid + 1
        let newHi = goLeft ? mid - 1 : hi
        b.probe(mid, window, ptrs, "a[\(mid)] = \(v), \(side) sorted", goLeft ? "hi = \(mid - 1)" : "lo = \(mid + 1)",
                [("lo", "\(lo)"), ("hi", "\(hi)"), ("mid", "\(mid)"), ("window", "\(before) → \(newHi - newLo + 1)")],
                SearchHeadline(lead: "The \(side) half is sorted and \(t) \(holds ? "falls inside it" : "is not in it"), so go ",
                               emphasis: goLeft ? "left" : "right", tail: "."),
                "The \(side) side runs \(span) in order, so a range check is enough to tell whether \(t) is there. " + (goLeft ? "hi moves to \(mid - 1)." : "lo moves to \(mid + 1)."))
        if goLeft { b.rule(mid, hi) } else { b.rule(lo, mid) }
        lo = newLo
        hi = newHi
    }
    b.notFound("The window closed.")
    return b.build()
}

private let searchConfigs: [String: SearchConfig] = [
    "modified_binary_search_pattern": SearchConfig(values: rotatedInput, target: rotatedTarget, title: "ROTATED SORTED ARRAY",
                                                   windowLabel: "Live window", build: rotatedFrames),
    "linear_search": SearchConfig(values: unsortedInput, target: unsortedTarget, title: "UNSORTED ARRAY",
                                  windowLabel: "Still to check", build: linearFrames),
    "binary_search": SearchConfig(values: sortedInput, target: sortedTarget, title: "SORTED ARRAY",
                                  windowLabel: "Live window", build: binaryFrames),
    "jump_search": SearchConfig(values: sortedInput, target: sortedTarget, title: "SORTED ARRAY",
                                windowLabel: "Current block", build: jumpFrames),
    "interpolation_search": SearchConfig(values: sortedInput, target: sortedTarget, title: "SORTED ARRAY",
                                         windowLabel: "Live window", build: interpolationFrames),
    "exponential_search": SearchConfig(values: sortedInput, target: sortedTarget, title: "SORTED ARRAY",
                                       windowLabel: "Live window", build: exponentialFrames),
]

struct SearchLab: View {
    private let config: SearchConfig
    private let frames: [SearchFrame]
    @State private var playback: PlaybackState
    @Environment(\.palette) private var palette
    @Environment(\.colorScheme) private var scheme

    init(topicId: String) {
        config = searchConfigs[topicId] ?? searchConfigs["binary_search"]!
        frames = config.build()
        _playback = State(initialValue: PlaybackState(stepCount: frames.count, speedMs: 650))
    }

    /// The mock's yellow is for a dark card; on white it needs to be darker to stay legible as text.
    private var highlight: Color { scheme == .dark ? SimColors.active : Color(hex: 0xB7791F) }

    var body: some View {
        let frame = frames[min(playback.index, frames.count - 1)]
        VStack(alignment: .leading, spacing: 0) {
            LabCard {
                HStack {
                    Text("\(config.title) · N = \(config.values.count)")
                        .font(AppFont.sans(13, .semibold)).tracking(0.8).foregroundStyle(palette.muted)
                    Spacer()
                    Text("target \(config.target)")
                        .font(.system(size: 15, weight: .bold, design: .monospaced))
                        .foregroundStyle(scheme == .dark ? Color(hex: 0xC4B5FD) : answerViolet)
                        .padding(.horizontal, 10).padding(.vertical, 4)
                        .background(answerViolet.opacity(0.22), in: RoundedRectangle(cornerRadius: 8))
                }
                CellStrip(values: config.values, frame: frame, highlight: highlight).padding(.top, 12)
                if !frame.log.isEmpty {
                    Text("PROBES").font(AppFont.sans(12, .semibold)).tracking(1).foregroundStyle(palette.muted)
                        .padding(.top, 18).padding(.bottom, 8)
                    VStack(spacing: 8) {
                        ForEach(frame.log.indices, id: \.self) { i in
                            ProbeRow(line: frame.log[i], current: i == frame.log.count - 1, highlight: highlight)
                        }
                        if let next = frame.next {
                            ProbeRow(line: ProbeLine(number: frame.log.count + 1, expr: "a[\(next)] …", outcome: ""),
                                     current: false, highlight: highlight, pending: true)
                        }
                    }
                }
                FlowLayout(spacing: 16, lineSpacing: 6) {
                    SearchSwatch(color: SimColors.active, label: "Probe")
                    SearchSwatch(color: SimColors.blue, label: config.windowLabel)
                    SearchSwatch(color: palette.muted.opacity(0.16), label: "Ruled out")
                    if frame.found != nil { SearchSwatch(color: SimColors.green, label: "Found") }
                }
                .padding(.top, 14)
            }
            if !frame.chips.isEmpty {
                FlowLayout(spacing: 8, lineSpacing: 8) {
                    ForEach(frame.chips.indices, id: \.self) { SearchChip(label: frame.chips[$0].0, value: frame.chips[$0].1) }
                }
                .padding(.top, 16)
            }
            headline(frame)
                .font(AppFont.sans(20, .bold))
                .foregroundStyle(palette.onSurface)
                .padding(.top, 16)
            if let note = frame.body {
                Text(note).font(AppFont.sans(15)).foregroundStyle(palette.muted).lineSpacing(3).padding(.top, 8)
            }
            PlaybackTransport(state: playback, captions: frames.map(\.status))
        }
    }

    private func headline(_ frame: SearchFrame) -> Text {
        guard let h = frame.headline else { return Text(frame.status) }
        let accent = frame.found != nil ? SimColors.green : highlight
        return Text(h.lead) + Text(h.emphasis).foregroundColor(accent) + Text(h.tail)
    }
}

private struct SearchSwatch: View {
    let color: Color
    let label: String
    @Environment(\.palette) private var palette

    var body: some View {
        HStack(spacing: 7) {
            RoundedRectangle(cornerRadius: 3).fill(color).frame(width: 11, height: 11)
            Text(label).font(AppFont.sans(14)).foregroundStyle(palette.muted)
        }
    }
}

private struct SearchChip: View {
    let label: String
    let value: String
    @Environment(\.palette) private var palette

    var body: some View {
        HStack(spacing: 8) {
            Text(label).foregroundStyle(palette.muted)
            Text(value).fontWeight(.bold).foregroundStyle(palette.onSurface)
        }
        .font(.system(size: 15, design: .monospaced))
        .padding(.horizontal, 12)
        .padding(.vertical, 8)
        .background(palette.muted.opacity(0.12), in: RoundedRectangle(cornerRadius: 10))
    }
}

/// One line of the probe list. The current probe is outlined in yellow with its outcome in yellow; the
/// placeholder for the next probe is an empty outline with dimmed text.
private struct ProbeRow: View {
    let line: ProbeLine
    let current: Bool
    let highlight: Color
    var pending = false
    @Environment(\.palette) private var palette

    var body: some View {
        let shape = RoundedRectangle(cornerRadius: 10)
        let fill: Color = current ? SimColors.active.opacity(0.12) : pending ? .clear : palette.muted.opacity(0.10)
        let stroke: Color = current ? SimColors.active : pending ? palette.muted.opacity(0.25) : .clear
        HStack(spacing: 0) {
            Text("\(line.number)")
                .font(.system(size: 15, weight: .bold, design: .monospaced))
                .foregroundStyle(current ? highlight : palette.muted.opacity(pending ? 0.5 : 1))
                .frame(width: 28, alignment: .leading)
            Text(line.expr)
                .font(.system(size: 15, design: .monospaced))
                .foregroundStyle(pending ? palette.muted.opacity(0.5) : palette.onSurface)
                .lineLimit(1)
            Spacer(minLength: 8)
            if !line.outcome.isEmpty {
                Text(line.outcome)
                    .font(.system(size: 15, weight: current ? .bold : .regular, design: .monospaced))
                    .foregroundStyle(current ? highlight : palette.muted)
                    .lineLimit(1)
            }
        }
        .padding(.horizontal, 14)
        .frame(height: 44)
        .background(fill, in: shape)
        .overlay(shape.strokeBorder(stroke, lineWidth: current ? 1.5 : 1))
    }
}

private struct CellStrip: View {
    let values: [Int]
    let frame: SearchFrame
    let highlight: Color
    @Environment(\.palette) private var palette

    var body: some View {
        HStack(spacing: 4) {
            ForEach(values.indices, id: \.self) { i in
                VStack(spacing: 6) {
                    cell(i)
                    Text("\(i)").font(.system(size: 12, design: .monospaced)).foregroundStyle(palette.muted)
                    pointerLabel(frame.pointers[i] ?? [])
                        .font(.system(size: 12, weight: .bold))
                        .lineLimit(1)
                        .fixedSize()
                        .frame(height: 14)
                }
            }
        }
        .animation(.easeInOut(duration: 0.2), value: frame.probe)
    }

    private func cell(_ i: Int) -> some View {
        let fill: Color
        let text: Color
        if frame.found == i {
            fill = SimColors.green; text = .white
        } else if frame.probe == i {
            fill = SimColors.active; text = onProbeCell
        } else if frame.window.contains(i) {
            fill = SimColors.blue; text = .white
        } else if frame.eliminated.contains(i) {
            fill = palette.muted.opacity(0.12); text = palette.muted.opacity(0.5)
        } else {
            // Not ruled out, but outside what this step looks at (jump search's later blocks).
            fill = palette.muted.opacity(0.12); text = palette.onSurface.opacity(0.8)
        }
        return Text("\(values[i])")
            .font(AppFont.sans(16, .bold))
            .foregroundStyle(text)
            .lineLimit(1).minimumScaleFactor(0.6)
            .frame(maxWidth: .infinity).frame(height: 52)
            .background(fill, in: RoundedRectangle(cornerRadius: 10))
    }

    private func pointerLabel(_ labels: [String]) -> Text {
        var text = Text("")
        for (i, label) in labels.enumerated() {
            if i > 0 { text = text + Text("·").foregroundColor(palette.muted) }
            text = text + Text(label).foregroundColor(probeLabels.contains(label) ? highlight : SimColors.blue)
        }
        return text
    }
}
