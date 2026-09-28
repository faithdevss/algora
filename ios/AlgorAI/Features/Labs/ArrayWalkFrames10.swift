import Foundation

// ArrayWalkSection.kt builders: interview-prep pattern walks, part two.

// Decoding "3[a2[bc]]": each '[' pushes the context to restore, each ']' pops one and folds the piece in.
func expressionStackFrames() -> [WalkFrame] {
    let sourceS = "3[a2[bc]]"
    let source = Array(sourceS)
    var frames: [WalkFrame] = []
    var countStack: [Int] = []
    var textStack: [String] = []
    var current = ""
    var number = 0

    func stackRow() -> [CellView] {
        countStack.isEmpty ? [CellView("empty", .dim)] : countStack.indices.map { CellView("\(countStack[$0])×\"\(textStack[$0])\"", .window) }
    }
    func cells(_ at: Int) -> [CellView] { source.enumerated().map { i, c in CellView(String(c), i == at ? .active : i < at ? .done : .idle) } }

    frames.append(WalkFrame(
        status: "Decode \"\(sourceS)\". Nesting is the signal: a stack holds exactly what a recursive parser would " +
            "keep in its call frames — the repeat count and the text built so far — without the depth limit.",
        cells: source.map { CellView(String($0)) }, aux: stackRow(), auxLabel: "stack: pending count × text"))

    for (i, c) in source.enumerated() {
        let status: String
        if let d = c.wholeNumberValue, c.isASCII {
            number = number * 10 + d
            status = "'\(c)' is a digit — accumulate it into the repeat count (\(number)). Multi-digit counts are " +
                "why this is accumulated rather than read once."
        } else if c == "[" {
            countStack.append(number)
            textStack.append(current)
            number = 0
            current = ""
            status = "'[' opens a context: push the count \(countStack.last!) and the text built so far " +
                "(\"\(textStack.last!)\"), then start fresh. Pushing *before* resetting is the whole trick."
        } else if c == "]" {
            let repeatCount = countStack.removeLast()
            let parent = textStack.removeLast()
            current = parent + String(repeating: current, count: repeatCount)
            status = "']' closes it: pop the count \(repeatCount) and the parent text, repeat the finished piece, and " +
                "fold it back into the parent. current = \"\(current)\"."
        } else {
            current.append(c)
            status = "'\(c)' is literal — append it to the piece being built at this depth: \"\(current)\"."
        }
        frames.append(WalkFrame(
            status: status, cells: cells(i), pointers: [i: "i"], aux: stackRow(), auxLabel: "stack: pending count × text",
            readout: "current = \"\(current)\"" + (number > 0 ? " · count \(number)" : "")))
    }

    frames.append(WalkFrame(
        status: "\"\(current)\" — \(current.count) characters, one pass, and the stack never held more than the " +
            "nesting depth. Balanced brackets, directory paths and arithmetic with parentheses are the same loop " +
            "with a different thing pushed.",
        cells: current.map { CellView(String($0), .result) }, aux: stackRow(), auxLabel: "stack: pending count × text",
        readout: "decoded = \"\(current)\""))
    return frames
}

func heapSchedulingFrames() -> [WalkFrame] {
    let meetings = [IPair(0, 30), IPair(5, 10), IPair(6, 12), IPair(15, 20), IPair(25, 35)]
    let sorted = meetings.sortedBy { $0.first }
    var heap: [Int] = []
    var frames: [WalkFrame] = []
    var peak = 0
    func label(_ m: IPair) -> String { "\(m.first)–\(m.second)" }
    func heapRow(active: Int? = nil) -> [CellView] {
        heap.isEmpty ? [CellView("empty", .dim)] : heap.sorted().map { CellView("\($0)", $0 == active ? .active : .done) }
    }

    frames.append(WalkFrame(
        status: "Five meetings, one calendar: how many rooms run at once? The arrival order is fixed by sorting on " +
            "start time; the heap decides the one thing left to choose — which room is free.",
        cells: sorted.map { CellView(label($0), .idle) }, aux: heapRow(), auxLabel: "min-heap of end times (rooms in use)",
        intervals: sorted.map { IntervalView($0.first, $0.second, .idle) }))

    for (i, m) in sorted.enumerated() {
        let start = m.first, end = m.second
        let earliest = heap.min()
        var reused = false
        if let e = earliest, e <= start {
            heap.remove(at: heap.firstIndex(of: e)!)
            reused = true
        }
        heap.append(end)
        peak = max(peak, heap.count)
        let detail: String
        if earliest == nil {
            detail = "Nothing is running yet — open the first room, busy until \(end)."
        } else if reused {
            detail = "The heap's root says a room frees at \(earliest!) ≤ \(start), so pop it and reuse that room. " +
                "It is now busy until \(end), and the room count did not grow."
        } else {
            detail = "The earliest room is busy until \(earliest!), after \(start) — nothing has retired, so this meeting needs a room of its own."
        }
        frames.append(WalkFrame(
            status: "\(label(m)) starts at \(start). " + detail,
            cells: sorted.enumerated().map { j, v in CellView(label(v), j == i ? .active : j < i ? .done : .idle) },
            pointers: [i: "now"], aux: heapRow(active: end), auxLabel: "min-heap of end times (rooms in use)",
            readout: "in use = \(heap.count) · peak = \(peak)",
            intervals: sorted.prefix(i + 1).map { IntervalView($0.first, $0.second, $0 == m ? .active : .window) }))
    }

    frames.append(WalkFrame(
        status: "The heap never held more than \(peak) entries, so \(peak) rooms cover the day. Heap size *is* the " +
            "concurrency — the same number a sweep line reports as its maximum overlap, reached from the other side.",
        cells: sorted.map { CellView(label($0), .result) }, aux: heapRow(), auxLabel: "min-heap of end times (rooms in use)",
        readout: "rooms needed = \(peak)", intervals: sorted.map { IntervalView($0.first, $0.second, .result) }))
    return frames
}

func lisTailsFrames() -> [WalkFrame] {
    let a = [10, 9, 2, 5, 3, 7, 101, 18]
    var tails: [Int] = []
    var frames: [WalkFrame] = []

    func tailsRow(active: Int? = nil) -> [CellView] {
        tails.isEmpty ? [CellView("empty", .dim)] : tails.enumerated().map { i, v in CellView("\(v)", i == active ? .active : .window) }
    }

    frames.append(WalkFrame(
        status: "tails[k] will hold the smallest value any increasing subsequence of length k+1 can end on. It is " +
            "not the subsequence — it is the best possible ending for each length seen so far.",
        cells: a.map { CellView("\($0)", .idle) }, aux: tailsRow(), auxLabel: "tails[k] = smallest tail of a chain of length k+1"))

    for (i, x) in a.enumerated() {
        let pos = tails.firstIndex { $0 >= x } ?? tails.count
        let appended = pos == tails.count
        let replaced: Int? = appended ? nil : tails[pos]
        if appended { tails.append(x) } else { tails[pos] = x }
        frames.append(WalkFrame(
            status: appended
                ? "\(x) is larger than every tail, so no existing chain can absorb it — append. The longest chain is now \(tails.count)."
                : "Binary search lands on tails[\(pos)] = \(replaced!), the first tail ≥ \(x). Overwrite it: a length-\(pos + 1) " +
                    "chain ending on \(x) leaves more room for what follows than one ending on \(replaced!). The length is unchanged.",
            cells: a.enumerated().map { j, v in CellView("\(v)", j == i ? .active : j < i ? .dim : .idle) },
            pointers: [i: "x"], aux: tailsRow(active: pos), auxLabel: "tails[k] = smallest tail of a chain of length k+1",
            readout: "LIS length so far = \(tails.count)"))
    }

    frames.append(WalkFrame(
        status: "tails = \(tails.map(String.init).joined(separator: ", ")) — length \(tails.count), and that length is the answer. The row " +
            "itself is not a subsequence of the input: reconstructing one needs a parent index recorded per element.",
        cells: a.map { CellView("\($0)", .dim) }, aux: tails.map { CellView("\($0)", .result) },
        auxLabel: "tails[k] = smallest tail of a chain of length k+1",
        readout: "LIS = \(tails.count) · one binary search per element, so O(n log n)"))
    return frames
}

func windowMaxDequeFrames() -> [WalkFrame] {
    let a = [1, 3, -1, -3, 5, 3, 6, 7]
    let k = 3
    var dq: [Int] = []
    var out: [Int] = []
    var frames: [WalkFrame] = []

    func dequeRow() -> [CellView] { dq.isEmpty ? [CellView("empty", .dim)] : dq.map { CellView("a[\($0)]=\(a[$0])", .window) } }

    frames.append(WalkFrame(
        status: "Maximum of every window of width \(k). The deque will hold indices whose values decrease front to " +
            "back, so its front is always the current window's maximum.",
        cells: a.map { CellView("\($0)", .idle) }, aux: dequeRow(), auxLabel: "deque of indices, values decreasing"))

    for (i, x) in a.enumerated() {
        let expired: Int? = dq.first.flatMap { $0 <= i - k ? $0 : nil }
        if expired != nil { dq.removeFirst() }
        var dominated: [Int] = []
        while let last = dq.last, a[last] <= x { dominated.append(dq.removeLast()) }
        dq.append(i)
        if i >= k - 1 { out.append(a[dq[0]]) }

        let windowStart = max(0, i - k + 1)
        var status = "a[\(i)] = \(x). "
        if let expired { status += "Index \(expired) has slid out of the window, so drop it from the front. " }
        status += dominated.isEmpty
            ? "Nothing at the back is smaller, so \(x) just joins it."
            : "It dominates \(dominated.map { "a[\($0)]=\(a[$0])" }.joined(separator: ", ")) — older *and* smaller can " +
                "never be the max again, so pop from the back before pushing."
        if i >= k - 1 { status += " Window [\(windowStart)..\(i)] max = \(a[dq[0]]), read straight off the front." }
        let front = dq[0]
        frames.append(WalkFrame(
            status: status,
            cells: a.enumerated().map { j, v in
                CellView("\(v)", j == i ? .active : i >= k - 1 && j == front ? .result : inRange(j, windowStart, i) ? .window : .idle)
            },
            pointers: [i: "i"], aux: dequeRow(), auxLabel: "deque of indices, values decreasing",
            readout: out.isEmpty ? nil : "maxima: \(out.map(String.init).joined(separator: ", "))"))
    }

    frames.append(WalkFrame(
        status: "\(out.count) window maxima in \(a.count) steps. Every index was pushed once and popped at most once, " +
            "so the whole scan is O(n) — a heap would be O(n log k) and would still need stale entries filtered out.",
        cells: out.map { CellView("\($0)", .result) }, aux: dequeRow(), auxLabel: "deque of indices, values decreasing",
        readout: "window maxima = \(out.map(String.init).joined(separator: ", "))"))
    return frames
}

func palindromeExpansionFrames() -> [WalkFrame] {
    let str = "abbanana"
    let s = Array(str)
    var frames: [WalkFrame] = []

    func expand(_ lo: Int, _ hi: Int) -> IPair {
        var l = lo, r = hi
        while l >= 0 && r < s.count && s[l] == s[r] { l -= 1; r += 1 }
        return IPair(l + 1, r - 1)
    }
    func width(_ span: IPair) -> Int { span.second - span.first + 1 }
    func text(_ span: IPair) -> String { width(span) <= 0 ? "" : String(s[span.first...span.second]) }

    var best = IPair(0, 0)
    var total = 0

    frames.append(WalkFrame(
        status: "\"\(str)\" has \(2 * s.count - 1) centres: \(s.count) characters and \(s.count - 1) gaps between " +
            "them. Every palindrome is symmetric about one of them, so enumerating centres finds all of them.",
        cells: s.map { CellView(String($0), .idle) }))

    for i in s.indices {
        let odd = expand(i, i)
        let even = expand(i, i + 1)
        total += (width(odd) + 1) / 2 + (width(even) + 1) / 2
        let local = width(even) > width(odd) ? even : odd
        if width(local) > width(best) { best = local }
        let b = best
        frames.append(WalkFrame(
            status: "Centre \(i): expanding around the character gives \"\(text(odd))\" (\(width(odd))). " +
                (width(even) <= 0
                    ? "The gap between \(i) and \(i + 1) fails on its first comparison — skipping it is the classic bug, " +
                        "not skipping it costs nothing."
                    : "The gap between \(i) and \(i + 1) gives \"\(text(even))\" (\(width(even))) — the even case earns its keep here."),
            cells: s.enumerated().map { j, c in
                CellView(String(c), width(local) > 1 && inRange(j, local.first, local.second) ? .window : j == i ? .active : .idle)
            },
            pointers: [i: "centre"], aux: (b.first...b.second).map { CellView(String(s[$0]), .result) },
            auxLabel: "longest palindrome so far",
            readout: "best = \"\(text(best))\" (\(width(best))) · palindromic substrings so far = \(total)"))
    }

    frames.append(WalkFrame(
        status: "Longest is \"\(text(best))\", and the same sweep counted \(total) palindromic substrings — every " +
            "expansion step is one more of them. \(2 * s.count - 1) centres × O(n) growth = O(n²) time, O(1) space.",
        cells: s.enumerated().map { j, c in CellView(String(c), inRange(j, best.first, best.second) ? .result : .dim) },
        readout: "longest = \"\(text(best))\" · count = \(total)"))
    return frames
}

func prefixFunctionFrames() -> [WalkFrame] {
    let str = "abacabab"
    let s = Array(str)
    var pi = [Int](repeating: 0, count: s.count)
    var frames: [WalkFrame] = []

    func piRow(active: Int? = nil, filledUpTo: Int) -> [CellView] {
        pi.enumerated().map { i, v in i == active ? CellView("\(v)", .active) : i <= filledUpTo ? CellView("\(v)", .window) : CellView("·", .dim) }
    }

    frames.append(WalkFrame(
        status: "pi[i] is the length of the longest proper prefix of \"\(str)\"[0..i] that is also a suffix of it — its " +
            "longest border. pi[0] is 0 by definition: a string cannot be its own proper prefix.",
        cells: s.map { CellView(String($0), .idle) }, aux: piRow(filledUpTo: 0), auxLabel: "pi (longest border per prefix)"))

    var k = 0
    for i in 1..<s.count {
        var fallbacks: [Int] = []
        while k > 0 && s[i] != s[k] {
            k = pi[k - 1]
            fallbacks.append(k)
        }
        let matched = s[i] == s[k]
        if matched { k += 1 }
        pi[i] = k
        let cand = matched ? k - 1 : k
        var status = "s[\(i)] = '\(s[i])' against the border candidate s[\(cand)] = '\(s[cand])'. "
        if !fallbacks.isEmpty {
            status += "Mismatch, so fall back to pi of the border — \(fallbacks.map(String.init).joined(separator: " → ")) — instead of " +
                "restarting at 0. That fallback is why the whole build stays linear. "
        }
        status += matched ? "Match: the border extends to length \(k), so pi[\(i)] = \(k)." : "No border survives here, so pi[\(i)] = 0."
        var pointers: [Int: String] = [i: "i"]
        if k > 0 { pointers[k - 1] = "border" }
        let kk = k
        frames.append(WalkFrame(
            status: status,
            cells: s.enumerated().map { j, c in CellView(String(c), j == i ? .active : j < kk ? .window : .idle) },
            pointers: pointers, aux: piRow(active: i, filledUpTo: i - 1), auxLabel: "pi (longest border per prefix)",
            readout: "border length k = \(k)"))
    }

    let lastPi = pi[s.count - 1]
    let period = s.count - lastPi
    frames.append(WalkFrame(
        status: "pi = \(pi.map(String.init).joined(separator: ", ")). Candidate period = n − pi[n−1] = \(s.count) − \(lastPi) = " +
            "\(period), but \(period) does not divide \(s.count), so \"\(str)\" is not a repeated block — the divisibility " +
            "check is the half of the rule people forget. Run the same table over pattern + '#' + text and every " +
            "pi[i] = m marks a full occurrence.",
        cells: s.enumerated().map { j, c in CellView(String(c), j < lastPi ? .result : .dim) },
        aux: pi.map { CellView("\($0)", .window) }, auxLabel: "pi (longest border per prefix)",
        readout: "longest border = \(lastPi) · period = \(period) (does not divide \(s.count))"))
    return frames
}
