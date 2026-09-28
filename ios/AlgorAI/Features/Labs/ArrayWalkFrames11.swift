import Foundation

// ArrayWalkSection.kt builders: interview-prep pattern walks, part three.

func fisherYatesFrames() -> [WalkFrame] {
    var a = ["A", "B", "C", "D", "E", "F"]
    let n = a.count
    // Fixed draws stand in for uniform(i, n-1) so the frames are identical every run.
    let draws = [3, 1, 5, 4, 5]
    var frames: [WalkFrame] = []

    func rangeRow(_ i: Int) -> [CellView] { (0..<n).map { j in j >= i ? CellView("\(j)", .window) : CellView("·", .dim) } }

    frames.append(WalkFrame(
        status: "Fisher-Yates shuffles in place. At step i the legal draw is any index in [i, \(n - 1)] — the " +
            "*unprocessed* suffix. Drawing from [0, \(n - 1)] instead gives nⁿ equally likely paths onto n! " +
            "permutations, which cannot divide evenly, so some orders come out more often than others.",
        cells: a.map { CellView($0, .idle) }, aux: rangeRow(0), auxLabel: "legal draws for i — never [0, n)"))

    for (i, j) in draws.enumerated() {
        let moved = a[j], displaced = a[i]
        a[i] = moved
        a[j] = displaced
        frames.append(WalkFrame(
            status: "i = \(i) draws j = \(j) from [\(i), \(n - 1)] and swaps: \(moved) is now fixed at position \(i)" +
                (i == j ? " — drawing itself is a legal outcome, and forbidding it biases the result." : ", \(displaced) falls back into the suffix."),
            cells: a.enumerated().map { p, v in CellView(v, p == i ? .done : p == j ? .active : p < i ? .done : .idle) },
            pointers: [i: "i", j: "j"], aux: rangeRow(i), auxLabel: "legal draws for i — never [0, n)"))
    }

    frames.append(WalkFrame(
        status: "\(a.joined()) — one pass, one swap per position, every permutation equally likely. " +
            "Reservoir sampling is the streaming twin of the same idea: keep k items and replace one with " +
            "probability k/i as the i-th arrives, and each of the n items ends up kept with probability k/n.",
        cells: a.map { CellView($0, .result) }, aux: rangeRow(n), auxLabel: "legal draws for i — never [0, n)",
        readout: "shuffled = \(a.joined(separator: " "))"))
    return frames
}

func fenwickRangeQueryFrames() -> [WalkFrame] {
    var a = [3, 1, 4, 1, 5, 9, 2, 6]
    let n = a.count
    var tree = [Int](repeating: 0, count: n + 1)
    var frames: [WalkFrame] = []

    func add(_ index: Int, _ delta: Int) -> [Int] {
        var path: [Int] = []
        var i = index + 1
        while i <= n { tree[i] += delta; path.append(i); i += i & -i }
        return path
    }
    func prefixPath(_ index: Int) -> [Int] {
        var path: [Int] = []
        var i = index + 1
        while i > 0 { path.append(i); i -= i & -i }
        return path
    }
    func prefixSum(_ index: Int) -> Int { prefixPath(index).reduce(0) { $0 + tree[$1] } }
    func treeRow(_ marked: Set<Int> = []) -> [CellView] { (1...n).map { CellView("\(tree[$0])", marked.contains($0) ? .active : .window) } }

    for i in a.indices { _ = add(i, a[i]) }
    var plainPrefix = [Int](repeating: 0, count: n)
    for i in a.indices { plainPrefix[i] = a[i] + (i == 0 ? 0 : plainPrefix[i - 1]) }

    frames.append(WalkFrame(
        status: "Static prefix sums answer any range in O(1): sum[l..r] = P[r] − P[l−1]. The cost is hidden in the " +
            "other operation — a single write to a[2] invalidates every prefix from index 2 rightward.",
        cells: a.map { CellView("\($0)", .idle) }, aux: plainPrefix.map { CellView("\($0)", .window) }, auxLabel: "P (inclusive prefix sums)"))
    frames.append(WalkFrame(
        status: "a[2] += 3, and six of the eight prefixes have to be recomputed. One write costs O(n); a thousand " +
            "interleaved writes and queries cost O(n²). That is the moment prefix sums stop being the answer.",
        cells: a.enumerated().map { i, v in CellView("\(v)", i == 2 ? .active : .idle) }, pointers: [2: "write"],
        aux: plainPrefix.enumerated().map { i, v in CellView("\(v)", i >= 2 ? .active : .window) },
        auxLabel: "P (inclusive prefix sums) — everything from index 2 is now stale"))
    frames.append(WalkFrame(
        status: "The Fenwick tree stores block aggregates instead. tree[i] covers the (i & −i) elements ending at i: " +
            "tree[4] holds a[0..3], tree[6] holds a[4..5], tree[8] holds the whole array. No cell depends on more " +
            "than log n others, which is what makes writes cheap.",
        cells: a.map { CellView("\($0)", .dim) }, aux: treeRow(), auxLabel: "tree[i] = aggregate of the block ending at i (1-indexed)"))

    let updatePath = add(2, 3)
    a[2] += 3
    let pathText = updatePath.map(String.init).joined(separator: " → ")
    frames.append(WalkFrame(
        status: "The same a[2] += 3 as a Fenwick update: start at index 3 (1-indexed) and jump with i += i & −i, " +
            "hitting \(pathText). Three cells touched instead of six, and the count is log n " +
            "no matter how long the array gets.",
        cells: a.enumerated().map { i, v in CellView("\(v)", i == 2 ? .active : .idle) }, pointers: [2: "write"],
        aux: treeRow(Set(updatePath)), auxLabel: "tree[i] = aggregate of the block ending at i (1-indexed)",
        readout: "update path = \(pathText)"))

    let queryPath = prefixPath(5)
    frames.append(WalkFrame(
        status: "prefix(5) walks the other way — i −= i & −i — visiting \(queryPath.map(String.init).joined(separator: " → ")) and adding " +
            "those blocks: \(queryPath.map { "\(tree[$0])" }.joined(separator: " + ")) = \(prefixSum(5)). Each step " +
            "strips one set bit, so the walk is as long as the index has bits.",
        cells: a.enumerated().map { i, v in CellView("\(v)", i <= 5 ? .window : .idle) }, pointers: [5: "r"],
        aux: treeRow(Set(queryPath)), auxLabel: "tree[i] = aggregate of the block ending at i (1-indexed)",
        readout: "prefix(5) = \(prefixSum(5))"))

    let rangeSum = prefixSum(5) - prefixSum(1)
    frames.append(WalkFrame(
        status: "sum[2..5] = prefix(5) − prefix(1) = \(prefixSum(5)) − \(prefixSum(1)) = \(rangeSum). Both halves are " +
            "O(log n), and so was the update — the trade that prefix sums could not make. Sums with point updates " +
            "want a Fenwick; min, max or gcd, or range updates, want a segment tree.",
        cells: a.enumerated().map { i, v in CellView("\(v)", inRange(i, 2, 5) ? .result : .dim) }, pointers: [2: "l", 5: "r"],
        aux: treeRow(Set(queryPath + prefixPath(1))), auxLabel: "tree[i] = aggregate of the block ending at i (1-indexed)",
        readout: "sum[2..5] = \(rangeSum)"))
    return frames
}

func rollingHashPatternFrames() -> [WalkFrame] {
    let textS = "abracadabra", patternS = "abra"
    let text = Array(textS)
    let base = 31, mod = 1009
    let m = patternS.count
    var frames: [WalkFrame] = []

    func code(_ c: Character) -> Int { Int(c.asciiValue! - Character("a").asciiValue! + 1) }
    func hashOf<S: Sequence>(_ s: S) -> Int where S.Element == Character { s.reduce(0) { ($0 * base + code($1)) % mod } }

    let high = (1..<m).reduce(1) { acc, _ in acc * base % mod }
    let target = hashOf(patternS)
    var h = hashOf(text[0..<m])
    var comparisons = 0
    var hits: [Int] = []

    func patternRow() -> [CellView] { patternS.map { CellView(String($0), .done) } }
    func textRow(_ start: Int, _ mark: CellMark) -> [CellView] { text.enumerated().map { i, c in CellView(String(c), inUntil(i, start, start + m) ? mark : .idle) } }
    let auxLabel = "pattern \"\(patternS)\" · hash = \(target)"

    frames.append(WalkFrame(
        status: "Base \(base), modulus \(mod), a = 1 … z = 26. The pattern hashes to \(target) once; the point is that " +
            "every window of the text can then be hashed in O(1) instead of O(m).",
        cells: text.map { CellView(String($0), .idle) }, aux: patternRow(), auxLabel: auxLabel))

    for start in 0...(text.count - m) {
        if start > 0 {
            let outgoing = text[start - 1], incoming = text[start + m - 1]
            let before = h
            h = ((h - code(outgoing) * high % mod + mod * mod) % mod * base + code(incoming)) % mod
            frames.append(WalkFrame(
                status: "Slide to \(start): drop '\(outgoing)' (weight b^\(m - 1) = \(high)), shift left by one base, add " +
                    "'\(incoming)'. \(before) → \(h), three operations regardless of how wide the window is.",
                cells: textRow(start, .window), pointers: [start: "l", start + m - 1: "r"], aux: patternRow(), auxLabel: auxLabel,
                readout: "h = \(h) · target = \(target)"))
        }
        if h == target {
            comparisons += 1
            let window = String(text[start..<(start + m)])
            let real = window == patternS
            if real { hits.append(start) }
            frames.append(WalkFrame(
                status: "Hashes match at \(start). That is a *candidate*, not a match — compare the \(m) characters " +
                    "directly: \"\(window)\" " +
                    (real ? "== \"\(patternS)\", a real occurrence." : "≠ \"\(patternS)\", a collision. Reporting it unverified is the bug."),
                cells: textRow(start, real ? .result : .active), pointers: [start: "l", start + m - 1: "r"], aux: patternRow(), auxLabel: auxLabel,
                readout: "verified hits: \(hits.isEmpty ? "none yet" : hits.map(String.init).joined(separator: ", "))"))
        }
    }

    frames.append(WalkFrame(
        status: "Occurrences at \(hits.map(String.init).joined(separator: ", ")) after \(comparisons) character comparison(s) instead of " +
            "\(text.count - m + 1). O(n + m) expected; against an adversary who can see your base, randomise it or " +
            "hash under two moduli.",
        cells: text.enumerated().map { i, c in CellView(String(c), hits.contains { inUntil(i, $0, $0 + m) } ? .result : .dim) },
        aux: patternRow(), auxLabel: auxLabel, readout: "hits = \(hits.map(String.init).joined(separator: ", "))"))
    return frames
}

func runningBestFrames() -> [WalkFrame] {
    let a = [-2, 1, -3, 4, -1, 2, 1, -5, 4]
    var curRow = [Int](repeating: 0, count: a.count)
    var frames: [WalkFrame] = []

    func curCells(_ upTo: Int, _ active: Int) -> [CellView] {
        curRow.enumerated().map { i, v in i == active ? CellView("\(v)", .active) : i <= upTo ? CellView("\(v)", .window) : CellView("·", .dim) }
    }

    var cur = a[0], best = a[0]
    var bestStart = 0, bestEnd = 0, start = 0
    curRow[0] = cur

    frames.append(WalkFrame(
        status: "Two scalars carry the whole scan: cur, the best run that *must* end at the current index, and " +
            "best, the best run seen anywhere. cur starts at a[0] = \(a[0]).",
        cells: a.enumerated().map { i, v in CellView("\(v)", i == 0 ? .active : .idle) }, pointers: [0: "i"],
        aux: curCells(-1, 0), auxLabel: "cur = best run ending here", readout: "cur = \(cur) · best = \(best)"))

    for i in 1..<a.count {
        let extend = cur + a[i]
        let restarted = a[i] > extend
        cur = max(a[i], extend)
        if restarted { start = i }
        curRow[i] = cur
        let improved = cur > best
        if improved {
            best = cur
            bestStart = start
            bestEnd = i
        }
        let st = start
        var pointers: [Int: String] = [i: "i"]
        pointers[start] = "run"
        frames.append(WalkFrame(
            status: "a[\(i)] = \(a[i]): extending gives \(extend), restarting gives \(a[i]). " +
                (restarted ? "The carried prefix has gone negative, so it can only hurt what follows — drop it and start fresh at \(i). "
                    : "Extending wins, so the run grows. ") +
                (improved ? "cur = \(cur) beats the old best, so best moves up." : "best stays at \(best) — the optimum may have ended earlier."),
            cells: a.enumerated().map { j, v in CellView("\(v)", j == i ? .active : inRange(j, st, i) ? .window : .idle) },
            pointers: pointers, aux: curCells(i - 1, i), auxLabel: "cur = best run ending here", readout: "cur = \(cur) · best = \(best)"))
    }

    frames.append(WalkFrame(
        status: "best = \(best), from a[\(bestStart)..\(bestEnd)]. One pass, two scalars, no table — and the run that won " +
            "ended before the array did, which is exactly why best is tracked separately from cur.",
        cells: a.enumerated().map { j, v in CellView("\(v)", inRange(j, bestStart, bestEnd) ? .result : .dim) },
        aux: curRow.map { CellView("\($0)", .window) }, auxLabel: "cur = best run ending here", readout: "max subarray sum = \(best)"))

    let p = [-2, 3, -4]
    var curMax = p[0], curMin = p[0], bestProduct = p[0]
    var maxRow = [Int](repeating: 0, count: p.count)
    var minRow = [Int](repeating: 0, count: p.count)
    maxRow[0] = curMax
    minRow[0] = curMin
    var minBeforeLast = curMin
    for i in 1..<p.count {
        if i == p.count - 1 { minBeforeLast = curMin }
        let candidates = [p[i], curMax * p[i], curMin * p[i]]
        curMax = candidates.max()!
        curMin = candidates.min()!
        maxRow[i] = curMax
        minRow[i] = curMin
        bestProduct = max(bestProduct, curMax)
    }
    frames.append(WalkFrame(
        status: "The product variant needs one more scalar. On \(p.map(String.init).joined(separator: ", ")) the running *minimum* reaches " +
            "\(minBeforeLast), and \(minBeforeLast) × \(p.last!) = \(bestProduct) is the answer — it comes out of the " +
            "minimum, not the maximum, because a negative flips the two. Track both or the negatives beat you.",
        cells: p.map { CellView("\($0)", .result) }, aux: maxRow.map { CellView("\($0)", .window) },
        auxLabel: "cur_max ending here (cur_min: \(minRow.map(String.init).joined(separator: ", ")))", readout: "max product = \(bestProduct)"))
    return frames
}

func sweepLineFrames() -> [WalkFrame] {
    let intervals = [IPair(1, 5), IPair(2, 7), IPair(4, 6), IPair(8, 10), IPair(9, 12)]
    // Ties: an interval ending at x frees the point before one starting at x claims it, so −1 sorts first.
    let events = intervals.flatMap { [IPair($0.first, 1), IPair($0.second, -1)] }.sortedBy { $0.first * 10 + $0.second }
    var frames: [WalkFrame] = []
    func eventLabel(_ e: IPair) -> String { "\(e.first)\(e.second > 0 ? "+" : "−")" }

    frames.append(WalkFrame(
        status: "\(intervals.count) intervals become \(events.count) endpoints and nothing else: +1 where one opens, " +
            "−1 where one closes. The intervals themselves are never looked at again.",
        cells: events.map { CellView(eventLabel($0), .idle) }, intervals: intervals.map { IntervalView($0.first, $0.second, .idle) }))

    var active = 0, peak = 0
    var peakAt = events[0].first
    var activeRow = [Int](repeating: 0, count: events.count)

    for (i, e) in events.enumerated() {
        active += e.second
        activeRow[i] = active
        if active > peak {
            peak = active
            peakAt = e.first
        }
        let pk = peak
        let row = activeRow
        frames.append(WalkFrame(
            status: "x = \(e.first): " + (e.second > 0 ? "an interval opens" : "an interval closes") +
                ", so active \(e.second > 0 ? "+" : "−") 1 = \(active)." + (active == peak && e.second > 0 ? " That is a new maximum overlap." : ""),
            cells: events.enumerated().map { j, v in CellView(eventLabel(v), j == i ? .active : j < i ? .done : .idle) },
            pointers: [i: "sweep"],
            aux: row.enumerated().map { j, v in j <= i ? CellView("\(v)", v == pk ? .result : .window) : CellView("·", .dim) },
            auxLabel: "active count after each event", readout: "active = \(active) · peak = \(peak) at x = \(peakAt)",
            intervals: intervals.map { IntervalView($0.first, $0.second, inUntil(e.first, $0.first, $0.second) ? .window : .idle) }))
    }

    let pa = peakAt
    frames.append(WalkFrame(
        status: "Maximum overlap \(peak), first reached at x = \(peakAt) — the sort dominates at O(n log n), the sweep " +
            "itself is one pass. A difference array is the same trick on a fixed index range: d[l] += v, d[r+1] −= v " +
            "per update, then one prefix sum materialises every value.",
        cells: events.map { CellView(eventLabel($0), .dim) },
        aux: activeRow.map { CellView("\($0)", $0 == peak ? .result : .window) }, auxLabel: "active count after each event",
        readout: "max overlap = \(peak) at x = \(peakAt)",
        intervals: intervals.map { IntervalView($0.first, $0.second, inUntil(pa, $0.first, $0.second) ? .result : .dim) }))
    return frames
}

func twoHeapsFrames() -> [WalkFrame] {
    let stream = [5, 15, 1, 3, 8, 7, 9, 10]
    var lo: [Int] = [] // smaller half, max-heap
    var hi: [Int] = [] // larger half, min-heap
    var frames: [WalkFrame] = []

    func median() -> Double { lo.count > hi.count ? Double(lo.max()!) : Double(lo.max()! + hi.min()!) / 2.0 }
    func show(_ v: Double) -> String { v == v.rounded(.towardZero) ? "\(Int(v))" : "\(v)" }
    func heapRow() -> [CellView] { lo.sorted(by: >).map { CellView("\($0)", .window) } + hi.sorted().map { CellView("\($0)", .done) } }

    frames.append(WalkFrame(
        status: "A running median over a stream. lo is a max-heap of the smaller half, hi a min-heap of the larger " +
            "half; every value in lo is ≤ every value in hi, so the middle of the data is always a root away.",
        cells: stream.map { CellView("\($0)", .idle) }, aux: [CellView("empty", .dim)], auxLabel: "lo (larger-first) | hi (smaller-first)"))

    for (i, x) in stream.enumerated() {
        lo.append(x)
        let promoted = lo.max()!
        lo.remove(at: lo.firstIndex(of: promoted)!)
        hi.append(promoted)
        var demoted: Int? = nil
        if hi.count > lo.count {
            let d = hi.min()!
            hi.remove(at: hi.firstIndex(of: d)!)
            lo.append(d)
            demoted = d
        }
        frames.append(WalkFrame(
            status: "Insert \(x): push it into lo, then move lo's largest (\(promoted)) into hi — that single hand-off " +
                "is what keeps every lo value below every hi value. " +
                (demoted != nil ? "hi is now the bigger half, so its smallest (\(demoted!)) comes back to lo." : "The sizes are already legal, so nothing comes back.") +
                " Median = \(show(median()))" + (lo.count > hi.count ? ", read straight off lo's root." : ", the mean of the two roots."),
            cells: stream.enumerated().map { j, v in CellView("\(v)", j == i ? .active : j < i ? .done : .idle) },
            pointers: [i: "x"], aux: heapRow(),
            auxLabel: "lo (larger-first) | hi (smaller-first) · |lo| = \(lo.count), |hi| = \(hi.count)",
            readout: "median = \(show(median()))"))
    }

    frames.append(WalkFrame(
        status: "\(stream.count) inserts, each O(log n), and every median was O(1) — both candidates were always " +
            "roots. Re-sorting after each insert would have been O(n² log n) for the same answers.",
        cells: stream.map { CellView("\($0)", .dim) }, aux: heapRow(), auxLabel: "lo (larger-first) | hi (smaller-first)",
        readout: "final median = \(show(median()))"))
    return frames
}
