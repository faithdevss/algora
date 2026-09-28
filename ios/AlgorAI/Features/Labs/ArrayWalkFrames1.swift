import Foundation

// ArrayWalkSection.kt builders: prefix sums through greedy coin change.

func prefixSumFrames() -> [WalkFrame] {
    let a = [3, 1, 4, 1, 5, 9, 2, 6]
    var prefix = [Int](repeating: 0, count: a.count)
    var frames: [WalkFrame] = []

    func auxRow(_ filledUpTo: Int, active: Int? = nil) -> [CellView] {
        prefix.enumerated().map { i, v in
            i == active ? CellView("\(v)", .active) : i <= filledUpTo ? CellView("\(v)", .window) : CellView("·", .dim)
        }
    }

    frames.append(WalkFrame(status: "P[i] will hold the sum of everything up to and including a[i]. Building it costs one pass.",
                            cells: a.map { CellView("\($0)") }, aux: auxRow(-1), auxLabel: "P (inclusive prefix)"))

    for i in a.indices {
        prefix[i] = i == 0 ? a[0] : prefix[i - 1] + a[i]
        frames.append(WalkFrame(
            status: i == 0 ? "P[0] = a[0] = \(a[0])." : "P[\(i)] = P[\(i - 1)] + a[\(i)] = \(prefix[i - 1]) + \(a[i]) = \(prefix[i]).",
            cells: a.enumerated().map { j, v in CellView("\(v)", j == i ? .active : j < i ? .window : .idle) },
            pointers: [i: "i"], aux: auxRow(i - 1, active: i), auxLabel: "P (inclusive prefix)"))
    }

    let l = 2, r = 5
    frames.append(WalkFrame(
        status: "Range sum a[\(l)..\(r)] = P[\(r)] − P[\(l - 1)] = \(prefix[r]) − \(prefix[l - 1]) = \(prefix[r] - prefix[l - 1]). " +
            "Two lookups and a subtraction — the width of the range never enters the cost.",
        cells: a.enumerated().map { j, v in CellView("\(v)", inRange(j, l, r) ? .result : .dim) },
        pointers: [l: "l", r: "r"],
        aux: prefix.enumerated().map { i, v in CellView("\(v)", i == r || i == l - 1 ? .active : .dim) },
        auxLabel: "P (inclusive prefix)", readout: "sum = \(prefix[r] - prefix[l - 1])"))
    return frames
}

func differenceArrayFrames() -> [WalkFrame] {
    let n = 8
    let updates = [(1, 4, 3), (3, 6, 2), (0, 2, -1)]
    var diff = [Int](repeating: 0, count: n)
    var frames: [WalkFrame] = []

    func diffRow(_ active: Set<Int> = []) -> [CellView] {
        diff.enumerated().map { i, v in CellView("\(v)", active.contains(i) ? .active : .idle) }
    }

    frames.append(WalkFrame(
        status: "Three range updates are coming. Applying each one element by element would cost O(n) apiece; " +
            "the difference array touches two cells per update instead.",
        cells: (0..<n).map { _ in CellView("0", .dim) }, aux: diffRow(), auxLabel: "D (difference)"))

    for (l, r, value) in updates {
        diff[l] += value
        var touched: Set<Int> = [l]
        if r + 1 < n {
            diff[r + 1] -= value
            touched.insert(r + 1)
        }
        frames.append(WalkFrame(
            status: "Add \(value) to a[\(l)..\(r)]: D[\(l)] += \(value)" +
                (r + 1 < n ? ", D[\(r + 1)] −= \(value)" : ", nothing to cancel past the end") + ".",
            cells: (0..<n).map { i in CellView("0", inRange(i, l, r) ? .window : .dim) },
            pointers: [l: "l", r: "r"], aux: diffRow(touched), auxLabel: "D (difference)"))
    }

    var result = [Int](repeating: 0, count: n)
    for i in 0..<n {
        result[i] = i == 0 ? diff[0] : result[i - 1] + diff[i]
        frames.append(WalkFrame(
            status: "Running sum of D rebuilds the array: a[\(i)] = \(result[i]).",
            cells: (0..<n).map { j in j == i ? CellView("\(result[j])", .active) : j < i ? CellView("\(result[j])", .window) : CellView("·", .dim) },
            pointers: [i: "i"], aux: diffRow([i]), auxLabel: "D (difference)"))
    }

    frames.append(WalkFrame(
        status: "Three range updates cost 6 writes plus one final pass, instead of rewriting every covered element three times.",
        cells: result.map { CellView("\($0)", .result) }, aux: diffRow(), auxLabel: "D (difference)"))
    return frames
}

func slidingWindowFrames() -> [WalkFrame] {
    let a = [2, 1, 5, 1, 3, 2, 7, 1]
    let k = 3
    var frames: [WalkFrame] = []
    var sum = a.prefix(k).reduce(0, +)
    var best = sum
    var bestStart = 0

    func row(_ start: Int, active: Int? = nil) -> [CellView] {
        a.enumerated().map { i, v in CellView("\(v)", i == active ? .active : inUntil(i, start, start + k) ? .window : .idle) }
    }

    frames.append(WalkFrame(
        status: "First window a[0..\(k - 1)] sums to \(sum). Every later window shares \(k)−1 elements with the one " +
            "before it, so recomputing from scratch would redo work.",
        cells: row(0), pointers: [0: "start", k - 1: "end"], readout: "sum = \(sum) · best = \(best)"))

    for start in thru(1, a.count - k) {
        let leaving = a[start - 1]
        let entering = a[start + k - 1]
        sum = sum - leaving + entering
        let improved = sum > best
        if improved {
            best = sum
            bestStart = start
        }
        frames.append(WalkFrame(
            status: "Slide: drop a[\(start - 1)] = \(leaving), take a[\(start + k - 1)] = \(entering) → sum \(sum)" + (improved ? " — new best." : "."),
            cells: row(start, active: start + k - 1), pointers: [start: "start", start + k - 1: "end"],
            readout: "sum = \(sum) · best = \(best)"))
    }

    frames.append(WalkFrame(
        status: "Best window is a[\(bestStart)..\(bestStart + k - 1)] with sum \(best), found in one pass: each element " +
            "is added once and removed once.",
        cells: a.enumerated().map { i, v in CellView("\(v)", inUntil(i, bestStart, bestStart + k) ? .result : .dim) },
        readout: "best = \(best)"))
    return frames
}

func twoPointerFrames() -> [WalkFrame] {
    let a = [1, 3, 4, 6, 8, 10, 13]
    let target = 14
    var frames: [WalkFrame] = []
    var found = Set<Int>()
    var lo = 0
    var hi = a.count - 1

    func row(_ active: Set<Int>) -> [CellView] {
        a.enumerated().map { i, v in
            CellView("\(v)", found.contains(i) ? .done : active.contains(i) ? .active : inRange(i, lo, hi) ? .idle : .dim)
        }
    }

    frames.append(WalkFrame(
        status: "Find every pair summing to \(target). The array is sorted, which is what lets one pass replace the nested loop.",
        cells: row([lo, hi]), pointers: [lo: "lo", hi: "hi"]))

    while lo < hi {
        let sum = a[lo] + a[hi]
        if sum == target {
            found.insert(lo)
            found.insert(hi)
            frames.append(WalkFrame(
                status: "\(a[lo]) + \(a[hi]) = \(target) — a pair. Move both inward; nothing else pairs with either of them.",
                cells: row([lo, hi]), pointers: [lo: "lo", hi: "hi"]))
            lo += 1
            hi -= 1
        } else if sum < target {
            frames.append(WalkFrame(
                status: "\(a[lo]) + \(a[hi]) = \(sum), under \(target). Only a larger left value can help, so lo moves right.",
                cells: row([lo, hi]), pointers: [lo: "lo", hi: "hi"]))
            lo += 1
        } else {
            frames.append(WalkFrame(
                status: "\(a[lo]) + \(a[hi]) = \(sum), over \(target). Only a smaller right value can help, so hi moves left.",
                cells: row([lo, hi]), pointers: [lo: "lo", hi: "hi"]))
            hi -= 1
        }
    }

    frames.append(WalkFrame(
        status: "Pointers met after \(a.count) steps total. Each comparison eliminates a whole row or column of the " +
            "pair table, which is why O(n) is enough.",
        cells: a.enumerated().map { i, v in CellView("\(v)", found.contains(i) ? .result : .dim) }))
    return frames
}

func kadaneFrames() -> [WalkFrame] {
    let a = [-2, 1, -3, 4, -1, 2, 1, -5, 4]
    var frames: [WalkFrame] = []
    var current = a[0]
    var best = a[0]
    var start = 0, bestStart = 0, bestEnd = 0

    frames.append(WalkFrame(
        status: "Kadane keeps one number: the best sum of a subarray ending exactly here. Start with a[0] = \(a[0]).",
        cells: a.enumerated().map { i, v in CellView("\(v)", i == 0 ? .active : .idle) },
        pointers: [0: "i"], readout: "current = \(current) · best = \(best)"))

    for i in 1..<a.count {
        let extended = current + a[i]
        let restart = extended < a[i]
        if restart {
            current = a[i]
            start = i
        } else {
            current = extended
        }
        if current > best {
            best = current
            bestStart = start
            bestEnd = i
        }
        frames.append(WalkFrame(
            status: restart
                ? "Extending would give \(extended), worse than starting fresh at \(a[i]) — so the subarray restarts at index \(i)."
                : "Extending is better: current = \(current - a[i]) + \(a[i]) = \(current).",
            cells: a.enumerated().map { j, v in CellView("\(v)", j == i ? .active : inUntil(j, start, i) ? .window : .idle) },
            pointers: [i: "i"], readout: "current = \(current) · best = \(best)"))
    }

    frames.append(WalkFrame(
        status: "Maximum subarray is a[\(bestStart)..\(bestEnd)] summing to \(best). One pass, one running value — no " +
            "need to remember any subarray but the current one.",
        cells: a.enumerated().map { i, v in CellView("\(v)", inRange(i, bestStart, bestEnd) ? .result : .dim) },
        readout: "best = \(best)"))
    return frames
}

func topKStreamFrames() -> [WalkFrame] {
    let stream = [5, 1, 9, 3, 7, 2, 8]
    let k = 3
    var heap: [Int] = [] // kept sorted ascending, like a TreeSet
    var frames: [WalkFrame] = []

    func heapRow(active: Int? = nil) -> [CellView] {
        heap.isEmpty ? [CellView("empty", .dim)] : heap.map { CellView("\($0)", $0 == active ? .active : .done) }
    }
    func insert(_ v: Int) { if !heap.contains(v) { heap.append(v); heap.sort() } }

    frames.append(WalkFrame(
        status: "Values arrive one at a time and the top \(k) has to be current at every moment — sorting is not an " +
            "option, so keep a min-heap of size \(k).",
        cells: stream.map { CellView("\($0)", .dim) }, aux: heapRow(), auxLabel: "min-heap (size ≤ \(k))"))

    for (index, value) in stream.enumerated() {
        let smallest = heap.first
        let status: String
        if heap.count < k {
            insert(value)
            status = "Heap is not full yet — push \(value)."
        } else if value > smallest! {
            heap.removeAll { $0 == smallest }
            insert(value)
            status = "\(value) beats the heap minimum \(smallest!) — evict \(smallest!), push \(value)."
        } else {
            status = "\(value) is not larger than the heap minimum \(smallest!), so it can never be in the top \(k). Discard it."
        }
        frames.append(WalkFrame(
            status: status,
            cells: stream.enumerated().map { i, v in CellView("\(v)", i == index ? .active : i < index ? .idle : .dim) },
            pointers: [index: "in"], aux: heapRow(active: heap.contains(value) ? value : nil), auxLabel: "min-heap (size ≤ \(k))"))
    }

    frames.append(WalkFrame(
        status: "Top \(k) = \(heap.sorted(by: >).map(String.init).joined(separator: ", ")). Each arrival costs O(log \(k)), and only \(k) " +
            "values are ever stored — sorting the stream would need all of it in memory.",
        cells: stream.map { CellView("\($0)", heap.contains($0) ? .result : .dim) },
        aux: heap.map { CellView("\($0)", .result) }, auxLabel: "min-heap (size ≤ \(k))"))
    return frames
}

func topKFrequentFrames() -> [WalkFrame] {
    let a = [1, 3, 1, 5, 3, 1, 7, 3, 5]
    let k = 2
    var frames: [WalkFrame] = []
    var order: [Int] = [] // LinkedHashMap insertion order
    var counts: [Int: Int] = [:]

    func countRow(active: Int? = nil) -> [CellView] {
        order.isEmpty ? [CellView("empty", .dim)] : order.map { CellView("\($0)×\(counts[$0]!)", $0 == active ? .active : .window) }
    }

    frames.append(WalkFrame(
        status: "\"Top \(k) most frequent\" is two problems: count, then select. Only the second half is the heap pattern.",
        cells: a.map { CellView("\($0)", .dim) }, aux: countRow(), auxLabel: "counts"))

    for (index, value) in a.enumerated() {
        if counts[value] == nil { order.append(value) }
        counts[value, default: 0] += 1
        frames.append(WalkFrame(
            status: "Count pass: \(value) → \(counts[value]!).",
            cells: a.enumerated().map { i, v in CellView("\(v)", i == index ? .active : i < index ? .idle : .dim) },
            pointers: [index: "i"], aux: countRow(active: value), auxLabel: "counts"))
    }

    var heap: [IPair] = []
    for value in order {
        let count = counts[value]!
        let status: String
        if heap.count < k {
            heap.append(IPair(value, count))
            status = "Heap not full — push \(value) (count \(count))."
        } else {
            let weakest = heap.min { $0.second < $1.second }!
            if count > weakest.second {
                heap.remove(at: heap.firstIndex(of: weakest)!)
                heap.append(IPair(value, count))
                status = "\(value) appears \(count) times, more than \(weakest.first)'s \(weakest.second) — swap it in."
            } else {
                status = "\(value) appears only \(count) times, no better than the heap's weakest (\(weakest.second)). Skip."
            }
        }
        frames.append(WalkFrame(
            status: status,
            cells: a.map { CellView("\($0)", $0 == value ? .active : .dim) },
            aux: heap.sortedByDescending { $0.second }.map { CellView("\($0.first)×\($0.second)", .done) },
            auxLabel: "min-heap by count (size ≤ \(k))"))
    }

    let winners = Set(heap.map(\.first))
    frames.append(WalkFrame(
        status: "Top \(k) = \(heap.sortedByDescending { $0.second }.map { "\($0.first) (\($0.second)×)" }.joined(separator: ", ")). " +
            "Sorting all counts would be O(m log m); the size-\(k) heap is O(m log \(k)).",
        cells: a.map { CellView("\($0)", winners.contains($0) ? .result : .dim) },
        aux: heap.sortedByDescending { $0.second }.map { CellView("\($0.first)×\($0.second)", .result) },
        auxLabel: "min-heap by count (size ≤ \(k))"))
    return frames
}

func fastSlowFrames() -> [WalkFrame] {
    let nodes = ["A", "B", "C", "D", "E", "F", "G", "H"]
    let loopEntry = 3
    let last = nodes.count - 1
    func next(_ i: Int) -> Int { i == last ? loopEntry : i + 1 }
    var frames: [WalkFrame] = []

    func row(_ slow: Int, _ fast: Int) -> [CellView] {
        nodes.enumerated().map { i, id in
            CellView(id, i == slow && i == fast ? .result : i == fast ? .active : i == slow ? .window : .idle)
        }
    }
    // A cell can only carry one label, and "slow+fast" wraps to two lines in a cell-width slot.
    func pointers(_ slow: Int, _ fast: Int) -> [Int: String] { slow == fast ? [slow: "both"] : [slow: "slow", fast: "fast"] }

    var slow = 0, fast = 0
    frames.append(WalkFrame(
        status: "This list ends by pointing back at \(nodes[loopEntry]), so walking it never terminates. Two " +
            "pointers at different speeds settle it in O(1) extra space.",
        cells: row(slow, fast), pointers: pointers(slow, fast), loopBack: (last, loopEntry)))

    repeat {
        slow = next(slow)
        fast = next(next(fast))
        frames.append(WalkFrame(
            status: "slow → \(nodes[slow]), fast → \(nodes[fast])." + (slow == fast ? " They are on the same node, which can only happen inside a loop." : ""),
            cells: row(slow, fast), pointers: pointers(slow, fast), loopBack: (last, loopEntry)))
    } while slow != fast

    frames.append(WalkFrame(
        status: "Meeting proves a cycle exists. To find where it starts, reset slow to the head and step both one at a time.",
        cells: row(0, fast), pointers: [0: "slow", fast: "fast"], loopBack: (last, loopEntry)))

    slow = 0
    while slow != fast {
        slow = next(slow)
        fast = next(fast)
        frames.append(WalkFrame(status: "slow → \(nodes[slow]), fast → \(nodes[fast]).",
                                cells: row(slow, fast), pointers: pointers(slow, fast), loopBack: (last, loopEntry)))
    }

    frames.append(WalkFrame(
        status: "They meet again at \(nodes[slow]) — the entry point of the cycle. No visited set, no marking of nodes, constant memory.",
        cells: nodes.enumerated().map { i, id in CellView(id, i == slow ? .result : .dim) },
        readout: "cycle starts at \(nodes[slow])", loopBack: (last, loopEntry)))
    return frames
}

func mergeIntervalsFrames() -> [WalkFrame] {
    let raw = [IPair(1, 3), IPair(8, 10), IPair(2, 6), IPair(15, 18), IPair(9, 12)]
    let sorted = raw.sortedBy { $0.first }
    var frames: [WalkFrame] = []
    func label(_ p: IPair) -> String { "\(p.first)–\(p.second)" }

    frames.append(WalkFrame(
        status: "Unsorted intervals: \(raw.map(label).joined(separator: ", ")). Overlap is only a local question once they are sorted by start.",
        cells: raw.map { CellView(label($0), .idle) }, intervals: raw.map { IntervalView($0.first, $0.second, .idle) }))
    frames.append(WalkFrame(
        status: "Sort by start: \(sorted.map(label).joined(separator: ", ")). Now any interval can only overlap the one being built.",
        cells: sorted.map { CellView(label($0), .window) }, intervals: sorted.map { IntervalView($0.first, $0.second, .window) }))

    var merged: [IPair] = []
    for (index, interval) in sorted.enumerated() {
        let open = merged.last
        let status: String
        if open == nil || interval.first > open!.second {
            merged.append(interval)
            status = "\(label(interval)) starts after \(open == nil ? "nothing is open" : "the open interval ends at \(open!.second)") — open a new interval."
        } else {
            merged[merged.count - 1] = IPair(open!.first, max(open!.second, interval.second))
            status = "\(label(interval)) starts at \(interval.first), inside the open \(label(open!)) — extend it to " +
                "\(label(merged.last!)) instead of adding a row."
        }
        frames.append(WalkFrame(
            status: status,
            cells: sorted.enumerated().map { i, v in CellView(label(v), i == index ? .active : i < index ? .dim : .idle) },
            pointers: [index: "i"],
            intervals: merged.map { IntervalView($0.first, $0.second, .done) } + [IntervalView(interval.first, interval.second, .active)]))
    }

    frames.append(WalkFrame(
        status: "\(raw.count) intervals collapse to \(merged.count): \(merged.map(label).joined(separator: ", ")). The " +
            "sort dominates at O(n log n); the merge itself is one pass.",
        cells: merged.map { CellView(label($0), .result) }, intervals: merged.map { IntervalView($0.first, $0.second, .result) }))
    return frames
}

// Earliest-finish-first, then the same data under earliest-start-first, then the duration trap.
func activitySelectionFrames() -> [WalkFrame] {
    let raw = [IPair(1, 4), IPair(3, 5), IPair(0, 6), IPair(5, 7), IPair(3, 9), IPair(5, 9), IPair(6, 10), IPair(8, 11)]
    var frames: [WalkFrame] = []
    func label(_ a: IPair) -> String { "\(a.first)–\(a.second)" }

    func sweep(_ order: [IPair], _ keyName: String, emitFrames: Bool) -> [IPair] {
        var taken: [IPair] = []
        var lastFinish = Int.min
        for (index, activity) in order.enumerated() {
            let fits = activity.first >= lastFinish
            if fits {
                taken.append(activity)
                lastFinish = activity.second
            }
            if emitFrames {
                frames.append(WalkFrame(
                    status: fits
                        ? "\(label(activity)) starts at \(activity.first) ≥ last finish " +
                            "\(taken.count == 1 ? "(nothing taken yet)" : "\(taken[taken.count - 2].second)") — take it. Last finish is now \(activity.second)."
                        : "\(label(activity)) starts at \(activity.first), before the last finish \(lastFinish) — it overlaps, so skip it and never look at it again.",
                    cells: order.enumerated().map { i, a in
                        CellView(label(a), i == index ? (fits ? .done : .dim) : taken.contains(a) ? .done : i < index ? .dim : .idle)
                    },
                    pointers: [index: keyName],
                    readout: "taken: \(taken.count)",
                    intervals: order.map { IntervalView($0.first, $0.second, taken.contains($0) ? .done : $0 == activity ? .active : .idle) }))
            }
        }
        return taken
    }

    frames.append(WalkFrame(
        status: "Eight activities on one resource. The bar chart is the overlap; the question is how many of them can run.",
        cells: raw.map { CellView(label($0), .idle) }, intervals: raw.map { IntervalView($0.first, $0.second, .idle) }))

    let byFinish = raw.sortedBy { $0.second }
    frames.append(WalkFrame(
        status: "Sort by finish time: \(byFinish.map(label).joined(separator: ", ")). Every decision from here is a single comparison against one number.",
        cells: byFinish.map { CellView(label($0), .window) }, intervals: byFinish.map { IntervalView($0.first, $0.second, .window) }))

    let chosen = sweep(byFinish, "f", emitFrames: true)
    frames.append(WalkFrame(
        status: "\(chosen.count) activities selected: \(chosen.map(label).joined(separator: ", ")). One pass, one variable, and this is provably a maximum-size set.",
        cells: chosen.map { CellView(label($0), .result) }, readout: "optimum = \(chosen.count)",
        intervals: raw.map { IntervalView($0.first, $0.second, chosen.contains($0) ? .result : .dim) }))

    let byStart = raw.sortedBy { $0.first }
    let startAnswer = sweep(byStart, "s", emitFrames: false)
    frames.append(WalkFrame(
        status: "Earliest start first on the identical set takes \(startAnswer.map(label).joined(separator: ", ")) — " +
            "\(startAnswer.count) activities, not \(chosen.count). 0–6 wins the sort and blocks three shorter activities behind it.",
        cells: byStart.map { CellView(label($0), startAnswer.contains($0) ? .active : .dim) },
        readout: "earliest start = \(startAnswer.count)",
        intervals: byStart.map { IntervalView($0.first, $0.second, startAnswer.contains($0) ? .active : .dim) }))

    let trap = [IPair(0, 5), IPair(4, 6), IPair(5, 10)]
    let byDuration = trap.sortedBy { $0.second - $0.first }
    let durationAnswer = sweep(byDuration, "d", emitFrames: false)
    frames.append(WalkFrame(
        status: "Shortest duration first, on a set built to break it: 4–6 has length 2, wins the sort, and conflicts with " +
            "both 0–5 and 5–10. It selects \(durationAnswer.count); finish-time order selects 2.",
        cells: byDuration.map { CellView(label($0), durationAnswer.contains($0) ? .active : .dim) },
        readout: "shortest duration = \(durationAnswer.count) vs 2",
        intervals: byDuration.map { IntervalView($0.first, $0.second, durationAnswer.contains($0) ? .active : .dim) }))
    return frames
}

// Greedy and DP on the same {1,3,4} / target 6 instance, so the two-coin answer greed misses is visible.
func coinChangeGreedyFrames() -> [WalkFrame] {
    let coins = [4, 3, 1]
    let target = 6
    var frames: [WalkFrame] = []

    func amountRow(_ remaining: Int) -> [CellView] {
        (0...target).map { a in CellView("\(a)", a == remaining ? .active : a > remaining ? .dim : .idle) }
    }

    frames.append(WalkFrame(
        status: "On {1, 5, 10, 25}, greedy change is correct: 68¢ becomes 25+25+10+5+1+1+1 and no shorter answer exists. " +
            "That is a fact about the coins, not about the algorithm.",
        cells: (0...target).map { CellView("\($0)", .idle) }, readout: "now try {1, 3, 4} at 6"))

    var remaining = target
    var taken: [Int] = []
    frames.append(WalkFrame(status: "Coins {1, 3, 4}, target 6. Greedy takes the largest coin that fits, repeatedly.",
                            cells: amountRow(remaining), pointers: [remaining: "left"]))
    while remaining > 0 {
        let before = remaining
        let coin = coins.first { $0 <= remaining }!
        taken.append(coin)
        remaining -= coin
        frames.append(WalkFrame(
            status: "Largest coin that fits in \(before) is \(coin) — take it. Remaining \(remaining), coins used \(taken.count).",
            cells: amountRow(remaining), pointers: [remaining: "left"],
            readout: "greedy: \(taken.map(String.init).joined(separator: " + ")) = \(taken.count) coins"))
    }
    let greedyCount = taken.count

    let unreachable = target + 1
    var dp = [Int](repeating: unreachable, count: target + 1)
    dp[0] = 0
    func dpRow(_ upTo: Int, active: Int? = nil) -> [CellView] {
        (0...target).map { a in CellView(dp[a] == unreachable ? "∞" : "\(dp[a])", a == active ? .active : a <= upTo ? .window : .dim) }
    }

    frames.append(WalkFrame(
        status: "Greed answered \(greedyCount) coins. Now tabulate: dp[a] is the fewest coins that make exactly a.",
        cells: (0...target).map { CellView("\($0)", .idle) }, aux: dpRow(0), auxLabel: "dp (fewest coins)"))
    for a in 1...target {
        var bestCoin = 0
        for coin in coins where coin <= a && dp[a - coin] + 1 < dp[a] {
            dp[a] = dp[a - coin] + 1
            bestCoin = coin
        }
        frames.append(WalkFrame(
            status: "dp[\(a)] = 1 + dp[\(a - bestCoin)] = \(dp[a]), taking a \(bestCoin).",
            cells: (0...target).map { CellView("\($0)", $0 == a ? .active : $0 < a ? .window : .idle) },
            pointers: [a: "a"], aux: dpRow(a - 1, active: a), auxLabel: "dp (fewest coins)"))
    }

    frames.append(WalkFrame(
        status: "dp[6] = \(dp[target]) — the split 3 + 3. Greed took \(taken.map(String.init).joined(separator: " + ")), \(greedyCount) coins, because taking the 4 " +
            "left a remainder that only 1s can fill. The rule never had to be wrong; these denominations are simply not canonical.",
        cells: (0...target).map { CellView("\($0)", $0 == target ? .result : .dim) },
        aux: dpRow(target, active: target), auxLabel: "dp (fewest coins)",
        readout: "greedy \(greedyCount) coins · optimal \(dp[target]) coins"))
    return frames
}
