import SwiftUI

// Port of SortingVisualizerSection.kt: one bar-chart player for every sorting algorithm. The
// algorithm runs once up front and records a snapshot per comparison / swap / write.

/// A piece of a narrated headline; a tone colours it to match the bar it names.
private enum SortTone { case compare, pivot, placed, range }

/// How a cell in one of the rows under the bars (runs, output, counts, buckets) is drawn. `dim` is a
/// value already used up; `empty` a slot still to fill, drawn dashed.
private enum CellTone { case idle, dim, compare, range, placed, pivot, empty }

/// `digit` brightens one character of `text` and dims the rest: the digit a radix pass is reading.
private struct SortCell { let text: String; var tone: CellTone = .idle; var digit: Int? = nil }

/// A labelled row of cells under the bars. `breaks` are indices that start a new group, with a wider
/// gap before them (left run · right run).
private struct SortRow { let label: String?; let cells: [SortCell]; var note: String? = nil; var headers: [String]? = nil; var breaks: Set<Int> = [] }

/// Radix sort's ten buckets, each a column of the values it holds so far.
private struct DigitBuckets { let label: String; let active: Int?; let columns: [[SortCell]] }

/// One of bucket sort's range boxes: its range, its values as small bars, and whether it is the one
/// being worked on (outlined).
private struct BucketBox { let range: String; let values: [Int]; let tone: CellTone; let active: Bool }

private struct SortSpan { let text: String; var tone: SortTone? }

private struct SortFrame {
    let values: [Int]
    var compared: Set<Int> = []
    var moved: Set<Int> = []
    var sorted: Set<Int> = []
    var range: Set<Int> = []
    let status: String
    // The rest is the narrated design (docs/ios-design/Simulations iOS.html, Quick Sort). A sort that
    // leaves it empty still gets the bars; the headline falls back to `status`.
    var pivot: Int?
    /// Scanned and ≤ pivot.
    var lowSide: Set<Int> = []
    /// Scanned and > pivot.
    var highSide: Set<Int> = []
    /// When set, bars outside it (and not yet sorted) are faded: the partition being worked on.
    var focus: Set<Int>?
    var pointers: [Int: [String]] = [:]
    var chips: [(String, String)] = []
    var title: String?
    var headline: [SortSpan]?
    var body: String?
    /// Top-right of the card, per step ("adjacent swaps", "level 2 of 3").
    var note: String? = nil
    /// An index drawn as an empty dashed bar holding `ghostValue`: insertion sort's gap.
    var ghost: Int? = nil
    var ghostValue: Int? = nil
    /// Pointer label colours for this step, over the defaults; a nil tone is plain text.
    var pointerTones: [String: SortTone?] = [:]
    var rows: [SortRow] = []
    var digitBuckets: DigitBuckets? = nil
    var buckets: [BucketBox] = []
    var barMax: Int? = nil
    var legend: [(CellTone, String)]? = nil
    var showBars = true
    /// Bars drawn as empty slots rather than values (counting sort's output before it is written).
    var emptyBars: Set<Int> = []
}

private struct SortConfig {
    let intro: String
    let rangeLabel: String?
    let build: () -> [SortFrame]
    /// Shown top-right of a narrated sort's card, e.g. the partition scheme.
    var variant: String?
}

private let movedBar = Color(hex: 0xF97316)
/// "The answer" violet (SimColors.Answer on Android), used for the pivot.
private let pivotBar = Color(hex: 0x7C5CFF)
private let sampleInput = [42, 8, 27, 61, 15, 34, 3, 50]

private func ids(_ r: Range<Int>) -> Set<Int> { r.isEmpty ? [] : Set(r) }
private func ids(_ r: ClosedRange<Int>) -> Set<Int> { Set(r) }
private func ids(_ a: Int, through b: Int) -> Set<Int> { a <= b ? Set(a...b) : [] }

private final class SortBuilder {
    var values: [Int]
    var frames: [SortFrame] = []
    init(_ initial: [Int]) { values = initial }

    func frame(_ status: String, compared: Set<Int> = [], moved: Set<Int> = [], sorted: Set<Int> = [], range: Set<Int> = []) {
        frames.append(SortFrame(values: values, compared: compared, moved: moved, sorted: sorted, range: range, status: status))
    }

    func done(_ status: String = "Sorted — every element is in its final position.") {
        frames.append(SortFrame(values: values, sorted: Set(values.indices), status: status))
    }
}

private func bubble() -> [SortFrame] {
    var a = sampleInput
    let n = a.count
    var frames: [SortFrame] = []
    let legend: [(CellTone, String)] = [(.compare, "Comparing"), (.placed, "Sorted tail")]
    var pass = 0
    var swaps = 0
    repeat {
        pass += 1
        let end = n - pass
        let tail = ids(end + 1..<n)
        swaps = 0
        let passMax = a[0...end].max()!
        for j in 0..<end {
            let x = a[j], y = a[j + 1]
            let swap = x > y
            if swap { swaps += 1 }
            let cmp = swap ? "\(x) > \(y)" : "\(x) ≤ \(y)"
            var f = SortFrame(values: a, compared: [j, j + 1], sorted: tail, status: swap ? "\(cmp), so they swap." : "\(cmp), so they stay.",
                              pointers: [j: ["j"], j + 1: ["j+1"]], chips: [("pass", "\(pass)"), ("swaps", "\(swaps)"), ("compare", cmp)],
                              title: "PASS \(pass) · a[0..\(end)]",
                              headline: [SortSpan(text: cmp, tone: .compare), SortSpan(text: swap ? ", so they swap." : ", so they stay.")],
                              body: swap ? "The largest unsorted value moves right. At the end of this pass, \(passMax) joins the sorted tail."
                                  : "Only neighbours are compared. The larger one, \(y), carries on to the next comparison.")
            f.note = "adjacent swaps"
            f.pointerTones = ["j+1": .compare]
            f.legend = legend
            frames.append(f)
            if swap { a.swapAt(j, j + 1) }
        }
    } while swaps > 0 && pass < n - 1
    var f = SortFrame(values: a, sorted: Set(a.indices), status: "Sorted after \(pass) passes.",
                      chips: [("passes", "\(pass)"), ("comparisons", "\(frames.count)")], title: "SORTED a[0..\(n - 1)]",
                      headline: [SortSpan(text: "Sorted", tone: .placed), SortSpan(text: " after \(pass) passes.")],
                      body: "Bubble sort makes O(n²) comparisons. It stops early only when a whole pass makes no swap.")
    f.note = "adjacent swaps"
    f.legend = legend
    frames.append(f)
    return frames
}

private func selection() -> [SortFrame] {
    var a = sampleInput
    let n = a.count
    var frames: [SortFrame] = []
    let legend: [(CellTone, String)] = [(.compare, "Scanning"), (.range, "Min so far"), (.placed, "Sorted")]
    let plain: [String: SortTone?] = ["i": nil, "min": .range]
    for i in 0..<(n - 1) {
        var m = i
        let sorted = ids(0..<i)
        for j in (i + 1)..<n {
            let v = a[j], previous = a[m]
            let smaller = v < previous
            var pointers: [Int: [String]] = [:]
            for (k, label) in [(i, "i"), (m, "min"), (j, "j")] { pointers[k, default: []].append(label) }
            var f = SortFrame(values: a, compared: [j], sorted: sorted, range: [m],
                              status: smaller ? "\(v) is smaller than \(previous), so it becomes the minimum." : "\(v) is not smaller than \(previous), so the minimum stays.",
                              pointers: pointers, chips: [("i", "\(i)"), ("min", "\(previous)"), ("j", "\(j)")], title: "SCAN a[\(i)..\(n - 1)]",
                              headline: [SortSpan(text: "\(v)", tone: .compare), SortSpan(text: smaller ? " is smaller than " : " is not smaller than "),
                                         SortSpan(text: "\(previous)", tone: .range), SortSpan(text: smaller ? ", so it becomes the minimum." : ", so the minimum stays.")],
                              body: j == n - 1 ? "That was the last value. \(smaller ? v : previous) is the minimum of a[\(i)..\(n - 1)]."
                                  : "When the scan ends, the minimum swaps into position \(i).")
            f.note = "find the minimum"
            f.pointerTones = plain
            f.legend = legend
            frames.append(f)
            if smaller { m = j }
        }
        let value = a[m], displaced = a[i]
        a.swapAt(i, m)
        var f = SortFrame(values: a, sorted: ids(0...i), status: "\(value) moves into position \(i).", pointers: [i: ["i"]],
                          chips: [("i", "\(i)"), ("min", "\(value)"), ("swaps", "\(i + 1)")], title: "SCAN a[\(i)..\(n - 1)]",
                          headline: [SortSpan(text: "\(value)", tone: .placed), SortSpan(text: " moves into position \(i).")],
                          body: m == i ? "It was already there, so the swap does nothing. a[0..\(i)] is final." : "\(displaced) goes to index \(m) in exchange. a[0..\(i)] is final.")
        f.note = "find the minimum"
        f.pointerTones = plain
        f.legend = legend
        frames.append(f)
    }
    var f = SortFrame(values: a, sorted: Set(a.indices), status: "Sorted with \(n - 1) swaps.", chips: [("swaps", "\(n - 1)")], title: "SORTED a[0..\(n - 1)]",
                      headline: [SortSpan(text: "Sorted", tone: .placed), SortSpan(text: " with \(n - 1) swaps.")],
                      body: "Selection sort always scans the whole unsorted part, so it is O(n²) even on sorted input. Its strength is the small number of swaps.")
    f.note = "find the minimum"
    f.legend = legend
    frames.append(f)
    return frames
}

private func insertion() -> [SortFrame] {
    var a = sampleInput
    let n = a.count
    var frames: [SortFrame] = []
    let legend: [(CellTone, String)] = [(.compare, "Comparing"), (.range, "Sorted prefix"), (.empty, "Key in the gap")]
    for i in 1..<n {
        let key = a[i]
        var gap = i
        var shifts = 0
        let title = "INSERT a[\(i)] INTO a[0..\(i - 1)]"
        func prefix() -> Set<Int> { ids(0...i).subtracting([gap]) }
        var f = SortFrame(values: a, range: prefix(), status: "Take \(key) out of a[\(i)].", pointers: [gap: ["gap"]],
                          chips: [("key", "\(key)"), ("shifts", "0")], title: title,
                          headline: [SortSpan(text: "Take "), SortSpan(text: "\(key)", tone: .compare), SortSpan(text: " out of a[\(i)].")],
                          body: "a[0..\(i - 1)] is already sorted. Larger values shift right until the gap reaches the key's place.")
        f.note = "shift right"; f.ghost = gap; f.ghostValue = key; f.pointerTones = ["gap": .compare]; f.legend = legend
        frames.append(f)
        while true {
            let j = gap - 1
            if j < 0 || a[j] <= key { break }
            let v = a[j]
            let body: String
            if j == 0 { body = "The gap moves to index 0, the front of the array, so the key lands there." }
            else if a[j - 1] <= key { body = "The gap moves to index \(j). \(a[j - 1]) is smaller than \(key), so the key lands there." }
            else { body = "The gap moves to index \(j), and \(key) is compared with \(a[j - 1]) next." }
            var g = SortFrame(values: a, compared: [j], range: prefix().subtracting([j]), status: "\(v) > \(key), so \(v) shifts right.",
                              pointers: [j: ["j"], gap: ["gap"]], chips: [("key", "\(key)"), ("shifts", "\(shifts)")], title: title,
                              headline: [SortSpan(text: "\(v) > \(key)", tone: .compare), SortSpan(text: ", so \(v) shifts right.")], body: body)
            g.note = "shift right"; g.ghost = gap; g.ghostValue = key; g.pointerTones = ["gap": .compare]; g.legend = legend
            frames.append(g)
            a[gap] = v
            gap = j
            shifts += 1
        }
        a[gap] = key
        var h = SortFrame(values: a, sorted: [gap], range: ids(0...i).subtracting([gap]), status: "\(key) lands at index \(gap).",
                          chips: [("key", "\(key)"), ("shifts", "\(shifts)")], title: title,
                          headline: [SortSpan(text: "\(key)", tone: .placed), SortSpan(text: " lands at index \(gap).")],
                          body: shifts == 0 ? "Nothing before it was bigger, so it stays put. a[0..\(i)] is sorted."
                              : "\(shifts) value\(shifts == 1 ? "" : "s") shifted to make room. a[0..\(i)] is sorted.")
        h.note = "shift right"; h.legend = [(.range, "Sorted prefix"), (.placed, "Just placed")]
        frames.append(h)
    }
    var f = SortFrame(values: a, sorted: Set(a.indices), status: "Sorted.", title: "SORTED a[0..\(n - 1)]",
                      headline: [SortSpan(text: "Sorted", tone: .placed), SortSpan(text: ": the prefix grew to the whole array.")],
                      body: "Each key only shifts past bigger values, so nearly sorted input finishes in close to linear time.")
    f.note = "shift right"; f.legend = [(.placed, "Sorted")]
    frames.append(f)
    return frames
}

private func mergeSort() -> [SortFrame] {
    var a = sampleInput
    let n = a.count
    var frames: [SortFrame] = []
    let levels = 3
    let legend: [(CellTone, String)] = [(.compare, "Comparing"), (.range, "Active run"), (.placed, "Written")]
    var width = 1
    var level = 0
    // Bottom-up: runs of 1 merge into runs of 2, then 4, then 8.
    while width < n {
        level += 1
        for lo in stride(from: 0, to: n, by: 2 * width) {
            let mid = lo + width - 1
            let hi = min(lo + 2 * width - 1, n - 1)
            let left = Array(a[lo...mid]), right = Array(a[(mid + 1)...hi])
            var out: [Int] = []
            var i = 0, j = 0
            while i < left.count && j < right.count {
                let x = left[i], y = right[j]
                let takeLeft = x <= y
                let written = takeLeft ? x : y
                let usedLeft = i + (takeLeft ? 1 : 0), usedRight = j + (takeLeft ? 0 : 1)
                let after: String
                if usedLeft == left.count {
                    let rest = right.dropFirst(usedRight)
                    after = "Then \(rest.map(String.init).joined(separator: ", ")) \(rest.count == 1 ? "is" : "are") copied over, and a[\(lo)..\(hi)] is sorted."
                } else if usedRight == right.count {
                    let rest = left.dropFirst(usedLeft)
                    after = "Then \(rest.map(String.init).joined(separator: ", ")) \(rest.count == 1 ? "is" : "are") copied over, and a[\(lo)..\(hi)] is sorted."
                } else {
                    after = "Each run is sorted, so only the two heads can be the next smallest."
                }
                func run(_ values: [Int], _ used: Int) -> [SortCell] {
                    values.enumerated().map { k, v in SortCell(text: "\(v)", tone: k < used ? .dim : k == used ? .compare : .idle) }
                }
                let cmp = "\(min(x, y)) < \(max(x, y))"
                var f = SortFrame(values: a, compared: [lo + i, mid + 1 + j], range: ids(lo..<(lo + i)).union(ids((mid + 1)..<(mid + 1 + j))),
                                  status: "\(cmp), so \(written) is written next.", focus: ids(lo...hi),
                                  chips: [("run width", "\(hi - lo + 1)"), ("written", "\(out.count) of \(hi - lo + 1)")], title: "MERGE a[\(lo)..\(hi)]",
                                  headline: [SortSpan(text: cmp, tone: .compare), SortSpan(text: ", so \(written) is written next.")], body: after)
                f.note = "level \(level) of \(levels)"
                f.rows = [
                    SortRow(label: "LEFT RUN · RIGHT RUN", cells: run(left, i) + run(right, j), breaks: [left.count]),
                    SortRow(label: "OUTPUT", cells: (0...(hi - lo)).map { k in k < out.count ? SortCell(text: "\(out[k])", tone: .placed) : SortCell(text: "", tone: .empty) }),
                ]
                f.legend = legend
                frames.append(f)
                out.append(written)
                if takeLeft { i += 1 } else { j += 1 }
            }
            out += left.dropFirst(i) + right.dropFirst(j)
            for (k, v) in out.enumerated() { a[lo + k] = v }
        }
        width *= 2
    }
    var f = SortFrame(values: a, sorted: Set(a.indices), status: "Sorted after \(levels) levels of merging.",
                      chips: [("levels", "\(levels)"), ("comparisons", "\(frames.count)")], title: "SORTED a[0..\(n - 1)]",
                      headline: [SortSpan(text: "Sorted", tone: .placed), SortSpan(text: " after \(levels) levels of merging.")],
                      body: "Each level touches every value once, and there are log n levels, so merge sort is O(n log n) on any input.")
    f.note = "level \(levels) of \(levels)"; f.legend = [(.placed, "Sorted")]
    frames.append(f)
    return frames
}

/// Lomuto partition, narrated: the last element is the pivot, i marks the end of the ≤ pivot side, and
/// j scans. One step per comparison (showing the state before its swap), one per real swap, and one
/// for the pivot landing.
private func quickSort() -> [SortFrame] {
    let b = SortBuilder(sampleInput)
    var sorted = Set<Int>()
    var swaps = 0, comparisons = 0

    func pointerMap(_ marks: (Int, String)...) -> [Int: [String]] {
        var out: [Int: [String]] = [:]
        for (index, label) in marks where index >= 0 { out[index, default: []].append(label) }
        return out
    }

    func partition(_ lo: Int, _ hi: Int) -> Int {
        let window = ids(lo...hi)
        let title = "PARTITION a[\(lo)..\(hi)]"
        let pivot = b.values[hi]
        var i = lo - 1
        var start = SortFrame(values: b.values, sorted: sorted, status: "Partition a[\(lo)..\(hi)] around pivot \(pivot)")
        start.pivot = hi
        start.focus = window
        start.pointers = pointerMap((hi, "pivot"))
        start.chips = [("pivot", "\(pivot)"), ("i", "\(i)"), ("swaps", "\(swaps)")]
        start.title = title
        start.headline = [SortSpan(text: "Partition a[\(lo)..\(hi)] around pivot "), SortSpan(text: "\(pivot)", tone: .pivot), SortSpan(text: ".")]
        start.body = "Lomuto takes the last element as the pivot. i marks the end of the ≤ \(pivot) side; it starts just before index \(lo), so that side is empty."
        b.frames.append(start)

        for j in lo..<hi {
            let v = b.values[j]
            comparisons += 1
            let fits = v <= pivot
            let body: String
            if !fits {
                body = "\(v) > \(pivot), so it stays on the right. Only j moves on."
            } else if i + 1 == j {
                body = "\(v) ≤ \(pivot), so i moves to \(i + 1) and \(v) joins the left side. It is already in place, so the swap does nothing."
            } else {
                body = "\(v) ≤ \(pivot), so i moves to \(i + 1) and \(v) swaps with a[\(i + 1)] = \(b.values[i + 1])."
            }
            var compare = SortFrame(values: b.values, compared: [j], sorted: sorted, status: "Compare \(v) with pivot \(pivot)")
            compare.pivot = hi
            compare.lowSide = ids(lo, through: i)
            compare.highSide = ids(i + 1, through: j - 1)
            compare.focus = window
            compare.pointers = pointerMap((i, "i"), (j, "j"), (hi, "pivot"))
            compare.chips = [("pivot", "\(pivot)"), ("i", "\(i)"), ("j", "\(j)"), ("swaps", "\(swaps)")]
            compare.title = title
            compare.headline = [SortSpan(text: "Compare "), SortSpan(text: "\(v)", tone: .compare), SortSpan(text: " with pivot "),
                                SortSpan(text: "\(pivot)", tone: .pivot), SortSpan(text: ".")]
            compare.body = body
            b.frames.append(compare)
            guard fits else { continue }
            i += 1
            swaps += 1
            guard i != j else { continue }
            let displaced = b.values[i]
            b.values.swapAt(i, j)
            var swap = SortFrame(values: b.values, compared: [i, j], sorted: sorted, status: "Swap \(v) into index \(i)")
            swap.pivot = hi
            swap.lowSide = ids(lo, through: i)
            swap.highSide = ids(i + 1, through: j)
            swap.focus = window
            swap.pointers = pointerMap((i, "i"), (j, "j"), (hi, "pivot"))
            swap.chips = [("pivot", "\(pivot)"), ("i", "\(i)"), ("j", "\(j)"), ("swaps", "\(swaps)")]
            swap.title = title
            swap.headline = [SortSpan(text: "Swap "), SortSpan(text: "\(v)", tone: .compare), SortSpan(text: " onto the left side.")]
            swap.body = "\(v) and \(displaced) trade places: \(v) joins the ≤ \(pivot) side at index \(i), and \(displaced), already known to be bigger than \(pivot), moves right to where the scan has been."
            b.frames.append(swap)
        }

        let k = i + 1
        swaps += 1
        b.values.swapAt(k, hi)
        sorted.insert(k)
        let next: String
        if k - 1 > lo && hi > k + 1 {
            next = "Recurse on a[\(lo)..\(k - 1)] and a[\(k + 1)..\(hi)]."
        } else if k - 1 > lo {
            next = "Recurse on a[\(lo)..\(k - 1)]; the right side is \(k == hi ? "empty" : "a single element")."
        } else if hi > k + 1 {
            next = "Recurse on a[\(k + 1)..\(hi)]; the left side is \(k == lo ? "empty" : "a single element")."
        } else {
            next = "Both sides are at most one element, so this branch is done."
        }
        var land = SortFrame(values: b.values, sorted: sorted, status: "Pivot \(pivot) lands at index \(k) — final position.")
        land.pivot = k
        land.lowSide = ids(lo, through: k - 1)
        land.highSide = ids(k + 1, through: hi)
        land.focus = window
        land.pointers = pointerMap((k, "pivot"))
        land.chips = [("pivot", "\(pivot)"), ("index", "\(k)"), ("swaps", "\(swaps)")]
        land.title = title
        land.headline = [SortSpan(text: "Pivot "), SortSpan(text: "\(pivot)", tone: .pivot), SortSpan(text: " lands at index \(k).")]
        land.body = "Swapping it with a[\(k)] puts everything ≤ \(pivot) to its left and everything bigger to its right, so index \(k) is final. " + next
        b.frames.append(land)
        return k
    }

    func sort(_ lo: Int, _ hi: Int) {
        guard lo < hi else { if lo == hi { sorted.insert(lo) }; return }
        let p = partition(lo, hi)
        sort(lo, p - 1)
        sort(p + 1, hi)
    }
    sort(0, b.values.count - 1)

    let n = b.values.count
    var done = SortFrame(values: b.values, sorted: Set(b.values.indices), status: "Sorted — every pivot landed in its final place.")
    done.chips = [("comparisons", "\(comparisons)"), ("swaps", "\(swaps)")]
    done.title = "SORTED a[0..\(n - 1)]"
    done.headline = [SortSpan(text: "Sorted", tone: .placed), SortSpan(text: ": every pivot landed in its final place.")]
    done.body = "\(comparisons) comparisons and \(swaps) swaps for \(n) values. Quicksort averages O(n log n); a pivot that is always the smallest or largest value makes it O(n²)."
    b.frames.append(done)
    return b.frames
}

private func heapSort() -> [SortFrame] {
    let b = SortBuilder(sampleInput)
    let n = b.values.count
    var sorted = Set<Int>()
    b.frame("Start — build a max-heap, then repeatedly move the root to the end.")
    func siftDown(_ root: Int, _ end: Int) {
        var parent = root
        while true {
            let left = 2 * parent + 1, right = left + 1
            if left >= end { break }
            var largest = parent
            b.frame("Compare parent \(b.values[parent]) with its children", compared: right < end ? [left, right] : [left], sorted: sorted, range: ids(0..<end))
            if b.values[left] > b.values[largest] { largest = left }
            if right < end && b.values[right] > b.values[largest] { largest = right }
            if largest == parent { break }
            b.values.swapAt(parent, largest)
            b.frame("Sift \(b.values[parent]) up — heap property restored here.", moved: [parent, largest], sorted: sorted, range: ids(0..<end))
            parent = largest
        }
    }
    for i in stride(from: n / 2 - 1, through: 0, by: -1) { siftDown(i, n) }
    b.frame("Max-heap built — the largest element is at the root.", range: ids(0..<n))
    for end in stride(from: n - 1, through: 1, by: -1) {
        b.values.swapAt(0, end)
        sorted.insert(end)
        b.frame("Move root \(b.values[end]) to index \(end).", moved: [0, end], sorted: sorted)
        siftDown(0, end)
    }
    b.done()
    return b.frames
}

private func countingSort() -> [SortFrame] {
    // Small values so the count array stays meaningful.
    let input = [4, 2, 7, 1, 4, 6, 2, 5]
    let k = 8
    let n = input.count
    var counts = [Int](repeating: 0, count: k)
    var frames: [SortFrame] = []
    func countRow(_ label: String, _ values: [Int], _ tone: (Int) -> CellTone, _ note: String) -> SortRow {
        SortRow(label: label, cells: (0..<k).map { SortCell(text: "\(values[$0])", tone: tone($0)) }, note: note, headers: (0..<k).map(String.init))
    }
    for (i, v) in input.enumerated() {
        let before = counts[v]
        counts[v] += 1
        let snapshot = counts
        let body: String
        if i == n - 1 { body = "Every value is counted. Next, prefix sums over the counts give each value its output position." }
        else if before == 0 { body = "Each value only bumps its own slot. Nothing is compared with anything else." }
        else { body = "Duplicates just raise the count. The two \(v)s never meet." }
        var f = SortFrame(values: input, compared: [i], range: ids(0..<i), status: "count[\(v)] becomes \(counts[v]).", pointers: [i: ["i"]],
                          chips: [("value", "\(v)"), ("count[\(v)]", "\(before) → \(counts[v])")], title: "COUNT a[0..\(n - 1)]",
                          headline: [SortSpan(text: before == 0 ? "Value \(v), so " : "Value \(v) again, so "), SortSpan(text: "count[\(v)]", tone: .compare),
                                     SortSpan(text: " becomes \(counts[v]).")], body: body)
        f.note = "values 0–\(k - 1)"
        f.pointerTones = ["i": .compare]
        f.rows = [countRow("COUNT[v]", snapshot, { $0 == v ? .compare : .idle }, "no comparisons")]
        f.legend = [(.compare, "Reading"), (.range, "Counted")]
        frames.append(f)
    }
    var start = [Int](repeating: 0, count: k)
    for v in 1..<k { start[v] = start[v - 1] + counts[v - 1] }
    let example = (0..<k).first { counts[$0] > 1 }!
    var p = SortFrame(values: input, range: Set(input.indices), status: "Prefix sums turn counts into start positions.",
                      chips: [("start[\(example)]", "\(start[example])")], title: "PREFIX SUMS",
                      headline: [SortSpan(text: "Prefix sums turn counts into "), SortSpan(text: "start positions", tone: .compare), SortSpan(text: ".")],
                      body: "start[v] is how many values are smaller than v. \(example) starts at \(start[example]) because \(start[example]) smaller values come first.")
    p.note = "values 0–\(k - 1)"
    p.rows = [countRow("COUNT[v]", counts, { _ in .idle }, "no comparisons"),
              countRow("START[v]", start, { $0 == example ? .compare : .range }, "running total")]
    p.legend = [(.compare, "Example"), (.range, "Start index")]
    frames.append(p)
    var output = [Int](repeating: 0, count: n)
    var written = 0
    for v in 0..<k where counts[v] > 0 {
        let from = written
        for _ in 0..<counts[v] { output[written] = v; written += 1 }
        let slots = Array(from..<written)
        var f = SortFrame(values: output, compared: Set(slots), sorted: ids(0..<from), status: "Write \(v) at \(slots.map(String.init).joined(separator: " and ")).",
                          chips: [("value", "\(v)"), ("count", "\(counts[v])"), ("index", slots.map(String.init).joined(separator: ", "))], title: "WRITE OUTPUT",
                          headline: slots.count == 1
                              ? [SortSpan(text: "Write "), SortSpan(text: "\(v)", tone: .compare), SortSpan(text: " at index \(slots[0]).")]
                              : [SortSpan(text: "Write the \(slots.count) "), SortSpan(text: "\(v)s", tone: .compare), SortSpan(text: " at indices \(slots.map(String.init).joined(separator: " and ")).")],
                          body: "Reading the counts in value order writes a sorted array without a single comparison.")
        f.note = "values 0–\(k - 1)"
        f.emptyBars = ids(written..<n)
        f.barMax = k - 1
        f.rows = [countRow("COUNT[v]", counts, { $0 == v ? .compare : ($0 < v && counts[$0] > 0 ? .placed : .idle) }, "no comparisons")]
        f.legend = [(.compare, "Writing"), (.placed, "Written")]
        frames.append(f)
    }
    var f = SortFrame(values: output, sorted: Set(output.indices), status: "Sorted in O(n + k).",
                      chips: [("n", "\(n)"), ("k", "\(k)"), ("comparisons", "0")], title: "SORTED",
                      headline: [SortSpan(text: "Sorted in "), SortSpan(text: "O(n + k)", tone: .placed), SortSpan(text: ".")],
                      body: "\(n) values, \(k) possible keys, zero comparisons. It only works because the values are small integers.")
    f.note = "values 0–\(k - 1)"; f.barMax = k - 1; f.legend = [(.placed, "Sorted")]
    frames.append(f)
    return frames
}

private func radixSort() -> [SortFrame] {
    var order = [170, 45, 75, 90, 802, 24, 2, 66]
    var frames: [SortFrame] = []
    let places = ["ONES", "TENS", "HUNDREDS"]
    let legend: [(CellTone, String)] = [(.compare, "Placing"), (.range, "Placed")]
    func pad(_ v: Int) -> String { String(repeating: "0", count: max(0, 3 - String(v).count)) + String(v) }
    for pass in 0..<3 {
        let exp = [1, 10, 100][pass]
        let charIndex = 2 - pass
        var buckets = [[Int]](repeating: [], count: 10)
        let rowLabel = pass == 0 ? "INPUT" : "AFTER PASS \(pass)"
        let place = places[pass].lowercased()
        for (i, v) in order.enumerated() {
            let digit = (v / exp) % 10
            buckets[digit].append(v)
            var f = SortFrame(values: order, status: "\(pad(v)) has \(place) digit \(digit), so it goes into bucket \(digit).",
                              chips: [("pass", "\(pass + 1) of 3"), ("digit", "\(digit)")], title: "PASS \(pass + 1) · \(places[pass]) DIGIT",
                              headline: [SortSpan(text: pad(v), tone: .compare), SortSpan(text: " has \(place) digit \(digit), so it goes into bucket \(digit).")],
                              body: i == 0 ? "Only one digit is read per pass, least significant first."
                                  : "Buckets are read back from 0 to 9. Order within a bucket is kept, and that is what makes the next pass work.")
            f.note = "stable"
            f.showBars = false
            f.rows = [SortRow(label: rowLabel, cells: order.enumerated().map { k, x in
                SortCell(text: pad(x), tone: k < i ? .range : k == i ? .compare : .dim, digit: charIndex) })]
            f.digitBuckets = DigitBuckets(label: "BUCKETS BY \(places[pass]) DIGIT", active: digit,
                                          columns: buckets.map { $0.map { x in SortCell(text: pad(x), tone: x == v ? .compare : .range) } })
            f.legend = legend
            frames.append(f)
        }
        order = buckets.flatMap { $0 }
        var f = SortFrame(values: order, status: "Reading the buckets from 0 to 9 gives the order for the next pass.",
                          chips: [("pass", "\(pass + 1) of 3")], title: "PASS \(pass + 1) · \(places[pass]) DIGIT",
                          headline: pass < 2 ? [SortSpan(text: "Reading buckets 0 to 9 gives the "), SortSpan(text: "next order", tone: .range), SortSpan(text: ".")]
                              : [SortSpan(text: "Sorted", tone: .placed), SortSpan(text: " after 3 passes, one per digit.")],
                          body: pass < 2 ? "The values are now ordered by their last \(pass + 1) digit\(pass == 0 ? "" : "s"). Ties keep their previous order."
                              : "Each pass is O(n + 10), so the whole sort is O(d·n) for d digits, with no comparisons at all.")
        f.note = "stable"
        f.showBars = false
        f.rows = [SortRow(label: "AFTER PASS \(pass + 1)", cells: order.map { SortCell(text: pad($0), tone: pass < 2 ? .range : .placed, digit: charIndex) })]
        f.digitBuckets = DigitBuckets(label: "BUCKETS BY \(places[pass]) DIGIT", active: nil, columns: buckets.map { $0.map { SortCell(text: pad($0), tone: .range) } })
        f.legend = pass < 2 ? [(.range, "Placed")] : [(.placed, "Sorted")]
        frames.append(f)
    }
    return frames
}

private func bucketSort() -> [SortFrame] {
    let input = [33, 12, 77, 41, 5, 68, 29, 54]
    let width = 20, count = 4
    let n = input.count
    var buckets = [[Int]](repeating: [], count: count)
    var output: [Int] = []
    var done = Set<Int>()
    var frames: [SortFrame] = []
    func range(_ b: Int) -> String { "\(b * width)–\((b + 1) * width - 1)" }
    func frame(_ inputTone: (Int) -> CellTone, _ active: Int?, _ chips: [(String, String)], _ headline: [SortSpan], _ body: String, _ legend: [(CellTone, String)]) {
        var f = SortFrame(values: input, status: headline.map(\.text).joined(), chips: chips, title: "\(count) BUCKETS · WIDTH \(width)",
                          headline: headline, body: body)
        f.note = "values 0–\(count * width - 1)"
        f.showBars = false
        f.rows = [
            SortRow(label: "INPUT", cells: input.enumerated().map { SortCell(text: "\($1)", tone: inputTone($0)) }),
            SortRow(label: "OUTPUT", cells: (0..<n).map { $0 < output.count ? SortCell(text: "\(output[$0])", tone: .placed) : SortCell(text: "", tone: .empty) }),
        ]
        f.buckets = buckets.enumerated().map { b, values in
            BucketBox(range: range(b), values: values, tone: done.contains(b) ? .placed : b == active ? .compare : .idle, active: b == active)
        }
        f.barMax = count * width - 1
        f.legend = legend
        frames.append(f)
    }
    for (i, v) in input.enumerated() {
        let b = v / width
        buckets[b].append(v)
        frame({ $0 < i ? .dim : $0 == i ? .compare : .idle }, b, [("value", "\(v)"), ("bucket", "\(v) ÷ \(width) = \(b)")],
              [SortSpan(text: "\(v)", tone: .compare), SortSpan(text: " goes into bucket \(range(b)).")],
              i == n - 1 ? "Every value is placed in one pass, without comparing any two of them." : "The bucket is value ÷ \(width), so placing needs no comparisons.",
              [(.compare, "Placing")])
    }
    let sortLegend: [(CellTone, String)] = [(.compare, "Comparing"), (.placed, "Sorted and written")]
    for b in 0..<count {
        if buckets[b].count > 1 {
            let x = buckets[b][0], y = buckets[b][1]
            let swap = x > y
            frame({ _ in .idle }, b, [("bucket", "\(b + 1) of \(count)")],
                  [SortSpan(text: "In bucket \(range(b)), "), SortSpan(text: swap ? "\(x) > \(y)" : "\(x) < \(y)", tone: .compare),
                   SortSpan(text: swap ? ", so they swap." : ", so they stay.")],
                  "Each bucket is sorted on its own, then joined in order.", sortLegend)
            buckets[b].sort()
        }
        output += buckets[b]
        done.insert(b)
        frame({ _ in .idle }, nil, [("bucket", "\(b + 1) of \(count)"), ("written", "\(output.count) of \(n)")],
              [SortSpan(text: "Bucket \(range(b)) is written out: "), SortSpan(text: buckets[b].map(String.init).joined(separator: ", "), tone: .placed), SortSpan(text: ".")],
              b == count - 1 ? "Buckets cover increasing ranges, so joining them in order is already sorted." : "Every value in a later bucket is bigger, so nothing written moves again.",
              sortLegend)
    }
    return frames
}

private func divideConquer() -> [SortFrame] {
    let input = [5, 3, 8, 1, 9, 2]
    let b = SortBuilder(input)
    var inversions = 0
    b.frame("Count the pairs that are out of order in \(input.map(String.init).joined(separator: ", ")). Comparing every pair is O(n²); the answers that span a split fall out of a merge sort that has to happen anyway.")
    func sort(_ lo: Int, _ hi: Int) {
        guard hi - lo >= 2 else { return }
        let mid = (lo + hi) / 2
        b.frame("Split [\(lo), \(hi - 1)] at \(mid). Inversions inside each half are counted recursively; only the pairs that straddle the split are left for the combine step.",
                compared: [mid], range: ids(lo..<hi))
        sort(lo, mid)
        sort(mid, hi)
        var merged: [Int] = []
        var i = lo, j = mid
        while i < mid || j < hi {
            let takeLeft = j >= hi || (i < mid && b.values[i] <= b.values[j])
            if takeLeft {
                merged.append(b.values[i]); i += 1
            } else {
                let crossing = mid - i
                inversions += crossing
                b.frame("\(b.values[j]) moves ahead of \(crossing == 0 ? "nothing" : "\(crossing) still-unmerged left-half value(s)")"
                        + (crossing == 0 ? " — no inversion here." : ": that is \(crossing) inversion(s) at once, found without comparing them individually. Running total \(inversions)."),
                        compared: [j], moved: ids(i..<mid), range: ids(lo..<hi))
                merged.append(b.values[j]); j += 1
            }
        }
        for (o, v) in merged.enumerated() { b.values[lo + o] = v }
        b.frame("[\(lo), \(hi - 1)] merged: \(merged.map(String.init).joined(separator: ", ")). Linear combine work per level, log n levels — T(n) = 2T(n/2) + O(n) = O(n log n).",
                sorted: ids(lo..<hi), range: ids(lo..<hi))
    }
    sort(0, b.values.count)
    b.done("Sorted, and \(inversions) inversions counted along the way. The sort was a side effect — the combine step was the algorithm.")
    return b.frames
}

private func dutchFlag() -> [SortFrame] {
    let b = SortBuilder([3, 1, 3, 2, 2, 1, 3, 1, 2])
    let pivot = 2
    var low = 0, mid = 0, high = b.values.count - 1
    func classified() -> Set<Int> { ids(0..<low).union(ids(high + 1, through: b.values.count - 1)) }
    b.frame("Three categories — below \(pivot), equal to \(pivot), above \(pivot) — sorted in one pass with no extra array. low and high mark the settled regions; mid scans the unclassified middle.",
            range: ids(mid, through: high))
    while mid <= high {
        let v = b.values[mid]
        if v < pivot {
            b.values.swapAt(mid, low)
            b.frame("\(v) < \(pivot): swap it down to index \(low). The value it displaced was already scanned and known to equal \(pivot), so mid can advance too.",
                    moved: [low, mid], sorted: classified(), range: ids(mid + 1, through: high))
            low += 1; mid += 1
        } else if v == pivot {
            b.frame("\(v) == \(pivot): it already belongs in the middle region, so only mid advances.", compared: [mid], sorted: classified(), range: ids(mid, through: high))
            mid += 1
        } else {
            b.values.swapAt(mid, high)
            b.frame("\(v) > \(pivot): swap it up to index \(high). mid does *not* advance — the value swapped in has never been looked at, and advancing here is the classic bug.",
                    moved: [mid, high], sorted: classified(), range: ids(mid, through: high - 1))
            high -= 1
        }
    }
    b.done("One pass, \(b.values.count) elements, O(1) extra space. Two pointers that partition rather than converge — the same engine quicksort uses, and the reason it handles duplicate keys without quadratic blowup.")
    return b.frames
}

private func greedyExchange() -> [SortFrame] {
    let b = SortBuilder([4, 1, 7, 2])
    func cost() -> Int { b.values.enumerated().reduce(0) { $0 + (b.values.count - $1.offset) * $1.element } }
    func breakdown() -> String {
        var acc = 0
        return b.values.map { acc += $0; return "\(acc)" }.joined(separator: " + ")
    }
    b.frame("Four jobs on one machine; minimise the total time customers wait. Order \(b.values.map(String.init).joined(separator: ", ")) gives completion times \(breakdown()) = \(cost()). A job's duration is paid by everyone still queued behind it, which is the hint.",
            range: Set(b.values.indices))
    var swapped = true, exchanges = 0
    while swapped {
        swapped = false
        for i in 0..<(b.values.count - 1) where b.values[i] > b.values[i + 1] {
            let before = cost()
            let longer = b.values[i], shorter = b.values[i + 1]
            b.values.swapAt(i, i + 1)
            exchanges += 1
            swapped = true
            b.frame("Adjacent pair \(longer) before \(shorter) is out of greedy order. Exchange them: everything outside the pair is unaffected, and the total drops from \(before) to \(cost()) — a difference of \(before - cost()), exactly \(longer) − \(shorter). Never worse, so no optimal solution is lost.",
                    moved: [i, i + 1], range: Set(b.values.indices))
        }
    }
    b.frame("No out-of-order adjacent pair remains, so the order is sorted by duration and the cost is \(cost()) after \(exchanges) exchange(s). That is the whole argument: any optimal schedule can be rewritten into this one one swap at a time without getting worse, so this one is optimal too.",
            sorted: Set(b.values.indices))
    b.done("Shortest-job-first, justified rather than guessed. When the same exchange *can* make things worse — items with weights and a capacity, say — the argument fails and the answer is DP instead.")
    return b.frames
}

private let sortConfigs: [String: SortConfig] = [
    "divide_conquer_pattern": SortConfig(intro: "Merge sort counting inversions. The interesting frames are the merges: taking one right-half value settles several pairs at once, which is the cross-boundary work the pattern is really about.", rangeLabel: "Current range", build: divideConquer),
    "dutch_flag_pattern": SortConfig(intro: "Three-way partition around 2, values 1/2/3 standing in for the flag's colours. Watch mid stall after a swap with high — the incoming value has not been classified yet.", rangeLabel: "Unclassified", build: dutchFlag),
    "greedy_exchange_pattern": SortConfig(intro: "The exchange argument run as an experiment: four jobs, and every adjacent swap that puts the shorter one first lowers the total wait. The proof is the algorithm.", rangeLabel: "Schedule", build: greedyExchange),
    "bubble_sort": SortConfig(intro: "Bubble sort on 8 values. Each pass walks the array swapping out-of-order neighbours, so the largest remaining value bubbles to the end — that tail is locked in green.", rangeLabel: nil, build: bubble),
    "selection_sort": SortConfig(intro: "Selection sort scans the unsorted region for its minimum, then swaps that value into place. Exactly n−1 swaps regardless of input order.", rangeLabel: nil, build: selection),
    "insertion_sort": SortConfig(intro: "Insertion sort keeps a sorted prefix (blue) and inserts the next element into it by shifting larger values right. Near-sorted input finishes in almost linear time.", rangeLabel: "Sorted prefix", build: insertion),
    "merge_sort": SortConfig(intro: "Merge sort splits down to single elements, then merges sorted runs pairwise. The blue window is the run being merged right now.", rangeLabel: "Active run", build: mergeSort),
    "quick_sort": SortConfig(intro: "Quicksort partitions the blue window around a pivot (the last element), locking the pivot into its final index, then recurses on each side.", rangeLabel: "Partition", build: quickSort, variant: "Lomuto"),
    "heap_sort": SortConfig(intro: "Heap sort first sifts the array into a max-heap, then repeatedly swaps the root to the end and re-heapifies the shrinking blue region.", rangeLabel: "Heap region", build: heapSort),
    "counting_sort": SortConfig(intro: "Counting sort never compares two elements. It tallies how often each value occurs, then writes the values back in ascending order — O(n + k).", rangeLabel: nil, build: countingSort),
    "radix_sort": SortConfig(intro: "Radix sort runs one stable bucket pass per digit, least significant first. After the last pass the array is fully ordered.", rangeLabel: "Pass result", build: radixSort),
    "bucket_sort": SortConfig(intro: "Bucket sort scatters 8 values into 8 range-buckets, sorts each with insertion sort, then concatenates. The blue window is the bucket just written back.", rangeLabel: "Bucket written", build: bucketSort),
]

// Built to docs/ios-design/Simulations iOS.html (Quick Sort): bars in a card under a heading naming the
// range being worked on, index and pointer labels under them, then chips, a headline whose numbers take
// their bar's colour, and a sentence on what the step decided. Sorts without narration keep the same
// card and bars, with their one-line status as the headline.
struct SortingLab: View {
    private let config: SortConfig
    private let frames: [SortFrame]
    private let peak: Int
    private let narrated: Bool
    @State private var playback: PlaybackState
    @Environment(\.palette) private var palette
    @Environment(\.colorScheme) private var scheme

    init(topicId: String) {
        config = sortConfigs[topicId] ?? sortConfigs["bubble_sort"]!
        frames = config.build()
        peak = frames[0].values.max() ?? 1
        narrated = frames.contains { $0.headline != nil }
        _playback = State(initialValue: PlaybackState(stepCount: frames.count, speedMs: 450))
    }

    /// The mock's yellow and violet are for a dark card; on white they need to be darker as text.
    private func toneColor(_ tone: SortTone) -> Color {
        switch tone {
        case .compare: scheme == .dark ? SimColors.active : Color(hex: 0xB7791F)
        case .pivot: scheme == .dark ? Color(hex: 0xA78BFA) : pivotBar
        case .placed: SimColors.green
        case .range: SimColors.blue
        }
    }

    var body: some View {
        let frame = frames[min(playback.index, frames.count - 1)]
        VStack(alignment: .leading, spacing: 0) {
            if !narrated {
                LabIntro(text: config.intro).padding(.bottom, 12)
            }
            LabCard {
                let note = frame.note ?? config.variant
                if frame.title != nil || note != nil {
                    HStack {
                        Text(frame.title ?? "").font(.system(size: 13, weight: .semibold, design: .monospaced)).tracking(0.8).foregroundStyle(palette.muted)
                            .lineLimit(1)
                        Spacer()
                        if let note { Text(note).font(AppFont.sans(14)).foregroundStyle(palette.muted).lineLimit(1) }
                    }
                }
                if frame.showBars {
                    SortBars(frame: frame, peak: frame.barMax ?? peak, toneColor: toneColor).padding(.top, 12)
                }
                // Bucket sort draws its input, then the buckets, then its output.
                ForEach(frame.rows.indices, id: \.self) { i in
                    if !frame.buckets.isEmpty && i == 1 { BucketBoxes(boxes: frame.buckets, max: frame.barMax ?? peak) }
                    SortCellRow(row: frame.rows[i])
                }
                if let grid = frame.digitBuckets { DigitBucketGrid(grid: grid) }
                FlowLayout(spacing: 16, lineSpacing: 6) {
                    if let legend = frame.legend {
                        ForEach(legend.indices, id: \.self) { i in
                            SortSwatch(color: cellFill(legend[i].0, palette), label: legend[i].1, dashed: legend[i].0 == .empty)
                        }
                    } else if narrated {
                        SortSwatch(color: SimColors.active, label: "Comparing")
                        SortSwatch(color: SimColors.blue, label: "≤ pivot")
                        SortSwatch(color: palette.muted.opacity(0.4), label: "> pivot")
                        SortSwatch(color: pivotBar, label: "Pivot")
                        SortSwatch(color: SimColors.green, label: "Placed")
                        SortSwatch(color: palette.muted.opacity(0.16), label: "Not scanned")
                    } else {
                        SortSwatch(color: SimColors.active, label: "Comparing")
                        SortSwatch(color: movedBar, label: "Moving")
                        SortSwatch(color: SimColors.green, label: "Sorted")
                        if let label = config.rangeLabel { SortSwatch(color: SimColors.blue, label: label) }
                    }
                }
                .padding(.top, 14)
            }
            if !frame.chips.isEmpty {
                FlowLayout(spacing: 8, lineSpacing: 8) {
                    ForEach(frame.chips.indices, id: \.self) { SortChip(label: frame.chips[$0].0, value: frame.chips[$0].1) }
                }
                .padding(.top, 16)
            }
            if let spans = frame.headline {
                spans.reduce(Text("")) { text, span in
                    text + (span.tone.map { Text(span.text).foregroundColor(toneColor($0)) } ?? Text(span.text))
                }
                .font(AppFont.sans(20, .bold))
                .foregroundStyle(palette.onSurface)
                .padding(.top, 16)
            } else {
                // Sorts without narration show their status line as the headline, a size down — several
                // run to two sentences.
                Text(frame.status)
                    .font(AppFont.sans(16, .semibold))
                    .foregroundStyle(palette.onSurface)
                    .lineSpacing(3)
                    .padding(.top, 16)
            }
            if let note = frame.body {
                Text(note).font(AppFont.sans(15)).foregroundStyle(palette.muted).lineSpacing(3).padding(.top, 8)
            }
            PlaybackTransport(state: playback, captions: frames.map(\.status))
        }
    }
}

private struct SortBars: View {
    let frame: SortFrame
    let peak: Int
    let toneColor: (SortTone) -> Color
    @Environment(\.palette) private var palette

    var body: some View {
        VStack(spacing: 8) {
            HStack(alignment: .bottom, spacing: 8) {
                ForEach(frame.values.indices, id: \.self) { i in bar(i) }
            }
            .frame(height: 200, alignment: .bottom)
            .animation(.easeInOut(duration: 0.18), value: frame.values)
            HStack(alignment: .top, spacing: 8) {
                ForEach(frame.values.indices, id: \.self) { i in
                    VStack(spacing: 2) {
                        Text("\(i)").font(.system(size: 12, design: .monospaced)).foregroundStyle(palette.muted)
                        pointerLabel(frame.pointers[i] ?? [])
                            .font(.system(size: 13, weight: .bold))
                            .lineLimit(1)
                            .fixedSize()
                    }
                    .frame(maxWidth: .infinity)
                }
            }
        }
    }

    private func bar(_ i: Int) -> some View {
        let faded = frame.focus.map { !$0.contains(i) && !frame.sorted.contains(i) } ?? false
        let ghost = frame.ghost == i
        let empty = frame.emptyBars.contains(i)
        var lit = true
        let color: Color
        if frame.pivot == i {
            color = pivotBar
        } else if frame.moved.contains(i) {
            color = movedBar
        } else if frame.compared.contains(i) {
            color = SimColors.active
        } else if frame.sorted.contains(i) {
            color = SimColors.green
        } else if frame.lowSide.contains(i) || frame.range.contains(i) {
            color = SimColors.blue
        } else if frame.highSide.contains(i) {
            color = palette.muted.opacity(0.4)
        } else {
            color = palette.muted.opacity(faded ? 0.08 : 0.2)
            lit = false
        }
        let v = ghost ? (frame.ghostValue ?? frame.values[i]) : frame.values[i]
        let shape = RoundedRectangle(cornerRadius: 8)
        return VStack(spacing: 6) {
            if !empty {
                Text("\(v)")
                    .font(AppFont.sans(15, .bold))
                    .foregroundStyle(ghost ? toneColor(.compare) : faded ? palette.muted.opacity(0.45) : lit ? palette.onSurface : palette.muted)
                    .lineLimit(1).minimumScaleFactor(0.6)
            }
            // 10pt floor keeps small values visible as bars rather than slivers.
            Group {
                if ghost {
                    shape.stroke(SimColors.active, style: StrokeStyle(lineWidth: 1, dash: [4, 3]))
                } else if empty {
                    shape.stroke(palette.muted.opacity(0.35), style: StrokeStyle(lineWidth: 1, dash: [4, 3]))
                } else {
                    shape.fill(color)
                }
            }
            .frame(height: empty ? 34 : 10 + 150 * CGFloat(v) / CGFloat(peak))
        }
        .frame(maxWidth: .infinity)
    }

    private func pointerLabel(_ labels: [String]) -> Text {
        var text = Text("")
        for (i, label) in labels.enumerated() {
            if i > 0 { text = text + Text("·").foregroundColor(palette.muted) }
            let color: Color
            if let override = frame.pointerTones[label] {
                text = text + Text(label).foregroundColor(override.map(toneColor) ?? palette.onSurface)
                continue
            }
            switch label {
            case "j": color = toneColor(.compare)
            case "pivot": color = toneColor(.pivot)
            case "i": color = SimColors.blue
            default: color = palette.muted
            }
            text = text + Text(label).foregroundColor(color)
        }
        return text
    }
}

private func cellFill(_ tone: CellTone, _ palette: Palette) -> Color {
    switch tone {
    case .idle: return palette.muted.opacity(0.2)
    case .dim: return palette.muted.opacity(0.09)
    case .compare: return SimColors.active
    case .range: return SimColors.blue
    case .placed: return SimColors.green
    case .pivot: return pivotBar
    case .empty: return .clear
    }
}

private func cellInk(_ tone: CellTone, _ palette: Palette) -> Color {
    switch tone {
    case .compare: return Color(hex: 0x1F1A0A)
    case .range, .placed, .pivot: return .white
    case .dim: return palette.muted.opacity(0.6)
    default: return palette.onSurface
    }
}

private struct SortSectionLabel: View {
    let label: String
    var note: String? = nil
    @Environment(\.palette) private var palette

    var body: some View {
        HStack {
            Text(label).font(AppFont.sans(12, .semibold)).tracking(1).foregroundStyle(palette.muted)
            Spacer(minLength: 8)
            if let note { Text(note).font(AppFont.sans(13)).foregroundStyle(palette.muted).lineLimit(1) }
        }
        .padding(.top, 14).padding(.bottom, 8)
    }
}

private struct SortCellRow: View {
    let row: SortRow
    @Environment(\.palette) private var palette

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            if let label = row.label { SortSectionLabel(label: label, note: row.note) }
            if let headers = row.headers {
                HStack(spacing: 6) {
                    ForEach(headers.indices, id: \.self) { Text(headers[$0]).font(.system(size: 12, design: .monospaced)).foregroundStyle(palette.muted).frame(maxWidth: .infinity) }
                }
                .padding(.bottom, 6)
            }
            HStack(spacing: 0) {
                ForEach(row.cells.indices, id: \.self) { i in
                    if i > 0 { Spacer().frame(width: row.breaks.contains(i) ? 14 : 6) }
                    cell(row.cells[i])
                }
            }
            .frame(height: 44)
        }
    }

    private func cell(_ c: SortCell) -> some View {
        let shape = RoundedRectangle(cornerRadius: 10)
        let ink = cellInk(c.tone, palette)
        let text: Text
        if let digit = c.digit {
            text = c.text.enumerated().reduce(Text("")) { out, pair in
                out + Text(String(pair.element)).foregroundColor(pair.offset == digit ? ink : ink.opacity(0.45))
            }
        } else {
            text = Text(c.text).foregroundColor(ink)
        }
        return text
            .font(.system(size: c.text.count > 2 ? 14 : 15, weight: .bold, design: .monospaced))
            .lineLimit(1).minimumScaleFactor(0.7)
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(cellFill(c.tone, palette), in: shape)
            .overlay { if c.tone == .empty { shape.stroke(palette.muted.opacity(0.35), style: StrokeStyle(lineWidth: 1, dash: [4, 3])) } }
    }
}

/// Ten columns, one per digit, each stacking the values dropped into it so far.
private struct DigitBucketGrid: View {
    let grid: DigitBuckets
    @Environment(\.palette) private var palette
    @Environment(\.colorScheme) private var scheme

    var body: some View {
        let yellow = scheme == .dark ? SimColors.active : Color(hex: 0xB7791F)
        let tallest = max(2, grid.columns.map(\.count).max() ?? 0)
        VStack(alignment: .leading, spacing: 0) {
            SortSectionLabel(label: grid.label)
            HStack(spacing: 4) {
                ForEach(grid.columns.indices, id: \.self) { d in
                    Text("\(d)").font(.system(size: 12, weight: d == grid.active ? .bold : .regular, design: .monospaced))
                        .foregroundStyle(d == grid.active ? yellow : palette.muted).frame(maxWidth: .infinity)
                }
            }
            HStack(alignment: .top, spacing: 4) {
                ForEach(grid.columns.indices, id: \.self) { d in
                    VStack(spacing: 4) {
                        ForEach(grid.columns[d].indices, id: \.self) { k in
                            let c = grid.columns[d][k]
                            Text(c.text).font(.system(size: 10, weight: .bold, design: .monospaced)).foregroundStyle(cellInk(c.tone, palette))
                                .lineLimit(1).minimumScaleFactor(0.6)
                                .frame(maxWidth: .infinity).frame(height: 26)
                                .background(cellFill(c.tone, palette), in: RoundedRectangle(cornerRadius: 6))
                        }
                        Spacer(minLength: 0)
                    }
                    .padding(3)
                    .frame(maxWidth: .infinity)
                    .frame(height: CGFloat(tallest * 30 + 8))
                    .background(palette.muted.opacity(0.08), in: RoundedRectangle(cornerRadius: 8))
                }
            }
            .padding(.top, 6)
        }
    }
}

/// Bucket sort's range boxes: a caption, then each value as a bar sized against the whole value range.
private struct BucketBoxes: View {
    let boxes: [BucketBox]
    let max: Int
    @Environment(\.palette) private var palette
    @Environment(\.colorScheme) private var scheme

    var body: some View {
        let yellow = scheme == .dark ? SimColors.active : Color(hex: 0xB7791F)
        VStack(alignment: .leading, spacing: 0) {
            SortSectionLabel(label: "BUCKETS")
            HStack(spacing: 8) {
                ForEach(boxes.indices, id: \.self) { b in
                    let box = boxes[b]
                    let shape = RoundedRectangle(cornerRadius: 12)
                    VStack(spacing: 0) {
                        Text(box.range).font(.system(size: 12, design: .monospaced)).foregroundStyle(box.active ? yellow : palette.muted)
                        Spacer(minLength: 0)
                        HStack(alignment: .bottom, spacing: 4) {
                            ForEach(box.values.indices, id: \.self) { k in
                                let v = box.values[k]
                                VStack(spacing: 4) {
                                    Text("\(v)").font(.system(size: 12, weight: .bold, design: .monospaced))
                                        .foregroundStyle(box.tone == .idle ? palette.muted : palette.onSurface)
                                    RoundedRectangle(cornerRadius: 5).fill(cellFill(box.tone, palette))
                                        .frame(height: 6 + 60 * CGFloat(v) / CGFloat(max))
                                }
                                .frame(maxWidth: .infinity)
                            }
                            // Keep one value from stretching across the whole box.
                            ForEach(0..<Swift.max(0, 2 - box.values.count), id: \.self) { _ in Color.clear.frame(maxWidth: .infinity, maxHeight: 1) }
                        }
                    }
                    .padding(.horizontal, 6).padding(.vertical, 8)
                    .frame(maxWidth: .infinity).frame(height: 150)
                    .background(palette.muted.opacity(0.08), in: shape)
                    .overlay { if box.active { shape.stroke(SimColors.active, lineWidth: 1.5) } }
                }
            }
        }
    }
}

private struct SortSwatch: View {
    let color: Color
    let label: String
    var dashed = false
    @Environment(\.palette) private var palette

    var body: some View {
        HStack(spacing: 7) {
            if dashed {
                RoundedRectangle(cornerRadius: 3).stroke(SimColors.active, style: StrokeStyle(lineWidth: 1, dash: [3, 2])).frame(width: 11, height: 11)
            } else {
                RoundedRectangle(cornerRadius: 3).fill(color).frame(width: 11, height: 11)
            }
            Text(label).font(AppFont.sans(14)).foregroundStyle(palette.muted)
        }
    }
}

private struct SortChip: View {
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
