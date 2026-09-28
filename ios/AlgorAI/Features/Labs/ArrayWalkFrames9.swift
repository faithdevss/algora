import Foundation

// ArrayWalkSection.kt builders: interview-prep pattern walks, part one. Each runs the variant an
// interviewer actually asks for rather than repeating the textbook version.

func prefixSumPatternFrames() -> [WalkFrame] {
    let a = [3, 4, 7, 2, -3, 1, 4, 2]
    let k = 7
    var order: [Int] = [0]
    var seen: [Int: Int] = [0: 1]
    var total = 0, count = 0
    var frames: [WalkFrame] = []

    func mapRow(hit: Int? = nil) -> [CellView] { order.map { CellView("\($0)×\(seen[$0]!)", $0 == hit ? .active : .window) } }

    frames.append(WalkFrame(
        status: "Counting subarrays that sum to \(k). The map is seeded with prefix 0 seen once, so a subarray " +
            "starting at index 0 needs no special case. Note the negative value — a sliding window would break here.",
        cells: a.map { CellView("\($0)", .dim) }, aux: mapRow(), auxLabel: "prefix totals seen", readout: "count = 0"))

    for (i, x) in a.enumerated() {
        total += x
        let need = total - k
        let hits = seen[need] ?? 0
        count += hits
        frames.append(WalkFrame(
            status: "total = \(total) after a[\(i)] = \(x). A subarray ending here sums to \(k) exactly when some earlier " +
                "prefix equals \(need) — " +
                (hits > 0 ? "that prefix was seen \(hits) time(s), so \(hits) subarray(s) end here." : "no prefix of \(need) has been seen, so none end here."),
            cells: a.enumerated().map { j, v in CellView("\(v)", j == i ? .active : j < i ? .window : .idle) },
            pointers: [i: "i"], aux: mapRow(hit: hits > 0 ? need : nil), auxLabel: "prefix totals seen", readout: "count = \(count)"))
        if seen[total] == nil { order.append(total) }
        seen[total, default: 0] += 1
    }

    frames.append(WalkFrame(
        status: "\(count) subarrays sum to \(k), found in one pass. The map holds totals, not indices — that is what " +
            "makes counting O(n) instead of checking every (l, r) pair.",
        cells: a.map { CellView("\($0)", .result) }, aux: mapRow(), auxLabel: "prefix totals seen", readout: "count = \(count)"))
    return frames
}

func binarySearchAnswerFrames() -> [WalkFrame] {
    let weights = [3, 2, 2, 4, 1, 4]
    let days = 3
    var frames: [WalkFrame] = []

    func pack(_ cap: Int) -> [Int] {
        var assigned: [Int] = []
        var day = 1, load = 0
        for w in weights {
            if load + w > cap {
                day += 1
                load = 0
            }
            load += w
            assigned.append(day)
        }
        return assigned
    }
    func dayRow(_ assigned: [Int]) -> [CellView] { assigned.map { CellView("d\($0)", .window) } }

    var lo = weights.max()!
    var hi = weights.reduce(0, +)

    frames.append(WalkFrame(
        status: "Ship these packages in order within \(days) days, minimising the ship's capacity. The array is not " +
            "sorted and never will be — what is monotone is the question \"does capacity x work?\".",
        cells: weights.map { CellView("\($0)", .idle) }, readout: "lo = \(lo) (largest package) · hi = \(hi) (all in one day)"))

    while lo < hi {
        let mid = (lo + hi) / 2
        let assigned = pack(mid)
        let used = assigned.last!
        let ok = used <= days
        frames.append(WalkFrame(
            status: "Probe capacity \(mid): packing left to right needs \(used) day(s). " +
                (ok ? "That fits in \(days) — keep \(mid) as a candidate and search below it (hi = mid)."
                    : "That exceeds \(days) — \(mid) is too small, so every capacity ≤ \(mid) is too (lo = mid + 1)."),
            cells: weights.enumerated().map { i, w in CellView("\(w)", assigned[i] % 2 == 1 ? .window : .active) },
            aux: dayRow(assigned), auxLabel: "day each package sails on",
            readout: "lo = \(lo) · mid = \(mid) · hi = \(hi) · feasible = \(ok)"))
        if ok { hi = mid } else { lo = mid + 1 }
    }

    let assigned = pack(lo)
    frames.append(WalkFrame(
        status: "lo and hi meet at \(lo) — the smallest capacity that still fits in \(days) days. \(weights.count) packages " +
            "were rescanned once per probe: O(n log R) where R is the width of the answer range, not the array.",
        cells: weights.enumerated().map { i, w in CellView("\(w)", assigned[i] % 2 == 1 ? .result : .done) },
        aux: dayRow(assigned), auxLabel: "day each package sails on", readout: "answer = \(lo)"))
    return frames
}

func monotonicStackFrames() -> [WalkFrame] {
    let a = [2, 1, 5, 6, 2, 3]
    var nge = [Int](repeating: -1, count: a.count)
    var st: [Int] = []
    var frames: [WalkFrame] = []

    func stackRow() -> [CellView] { st.isEmpty ? [CellView("empty", .dim)] : st.map { CellView("a[\($0)]=\(a[$0])", .window) } }
    func row(_ current: Int) -> [CellView] {
        a.enumerated().map { i, v in CellView("\(v)", i == current ? .active : nge[i] != -1 ? .done : st.contains(i) ? .window : .idle) }
    }

    frames.append(WalkFrame(
        status: "Next greater element for every index. The stack will hold indices whose answer is still unknown, " +
            "kept in decreasing value order — anything smaller than the incoming element cannot stay.",
        cells: a.map { CellView("\($0)", .idle) }, aux: stackRow(), auxLabel: "stack (indices, values decreasing)"))

    for (i, x) in a.enumerated() {
        var resolved: [Int] = []
        while let top = st.last, a[top] < x {
            st.removeLast()
            nge[top] = x
            resolved.append(top)
        }
        st.append(i)
        frames.append(WalkFrame(
            status: "a[\(i)] = \(x). " +
                (resolved.isEmpty ? "Nothing on the stack is smaller, so nothing is resolved — push \(i) and wait."
                    : "It beats \(resolved.map { "a[\($0)]=\(a[$0])" }.joined(separator: ", ")), so \(x) is their next greater element. Pop them, then push \(i)."),
            cells: row(i), pointers: [i: "i"], aux: stackRow(), auxLabel: "stack (indices, values decreasing)"))
    }

    frames.append(WalkFrame(
        status: "The \(st.count) index(es) still on the stack have nothing greater to their right, so they keep −1. " +
            "Every index was pushed once and popped at most once — O(n), not the O(n²) of scanning right each time.",
        cells: nge.map { CellView($0 == -1 ? "−1" : "\($0)", $0 == -1 ? .dim : .result) },
        aux: a.map { CellView("\($0)", .idle) }, auxLabel: "input", readout: "row above = next greater per index"))
    return frames
}

func cyclicSortFrames() -> [WalkFrame] {
    var a = [3, 1, 5, 4, 3]
    let n = a.count
    var frames: [WalkFrame] = []

    func row(_ current: Int, _ settledUpTo: Int) -> [CellView] {
        a.enumerated().map { i, v in CellView("\(v)", i == current ? .active : v == i + 1 && i < settledUpTo ? .done : .idle) }
    }

    frames.append(WalkFrame(
        status: "\(n) values that should be 1..\(n), one of them repeated. Because every value knows the index it " +
            "belongs at, sorting needs no comparisons — only swaps.",
        cells: a.map { CellView("\($0)", .idle) }, aux: (0..<n).map { CellView("\($0 + 1)", .dim) }, auxLabel: "index i wants value i+1"))

    var i = 0
    while i < n {
        let home = a[i] - 1
        if a[i] != a[home] {
            let moved = a[i], displaced = a[home]
            a[i] = displaced
            a[home] = moved
            frames.append(WalkFrame(
                status: "a[\(i)] = \(moved) belongs at index \(home). Swap it there; index \(i) now holds \(displaced) and " +
                    "still has to be placed, so i does not advance.",
                cells: row(i, i), pointers: [i: "i", home: "home"],
                aux: (0..<n).map { CellView("\($0 + 1)", $0 == home ? .done : .dim) }, auxLabel: "index i wants value i+1"))
        } else {
            let ii = i
            frames.append(WalkFrame(
                status: a[i] == i + 1 ? "a[\(i)] = \(a[i]) is already home. Advance."
                    : "a[\(i)] = \(a[i]), but index \(home) already holds \(a[home]) — a duplicate, so nothing can be " +
                        "placed here. Advance and let the final scan report it.",
                cells: row(i, i + 1), pointers: [i: "i"],
                aux: (0..<n).map { CellView("\($0 + 1)", $0 <= ii ? .done : .dim) }, auxLabel: "index i wants value i+1"))
            i += 1
        }
    }

    let bad = (0..<n).first { a[$0] != $0 + 1 }
    frames.append(WalkFrame(
        status: bad == nil ? "Every value sits at its own index — nothing missing, nothing duplicated."
            : "Index \(bad!) holds \(a[bad!]) instead of \(bad! + 1): \(a[bad!]) is the duplicate and \(bad! + 1) is missing. " +
                "At most \(n) swaps, no hash set, O(1) extra space.",
        cells: a.enumerated().map { j, v in CellView("\(v)", j == bad ? .result : .done) },
        aux: (0..<n).map { CellView("\($0 + 1)", $0 == bad ? .result : .dim) }, auxLabel: "index i wants value i+1",
        readout: bad.map { "duplicate = \(a[$0]) · missing = \($0 + 1)" }))
    return frames
}

func inPlaceReversalFrames() -> [WalkFrame] {
    let nodes = ["A", "B", "C", "D", "E"]
    var frames: [WalkFrame] = []
    var prev = -1, cur = 0

    func chainRow() -> [CellView] {
        var chain: [CellView] = []
        var walk = prev
        while walk >= 0 { chain.append(CellView(nodes[walk], .done)); walk -= 1 }
        return chain.isEmpty ? [CellView("empty", .dim)] : chain
    }
    func row() -> [CellView] { nodes.indices.map { i in CellView(nodes[i], i == cur ? .active : i < cur ? .done : .idle) } }
    func pointerRow() -> [Int: String] {
        var labels: [Int: String] = [:]
        if prev >= 0 { labels[prev] = "prev" }
        if nodes.indices.contains(cur) { labels[cur] = "cur" }
        return labels
    }

    frames.append(WalkFrame(
        status: "A → B → C → D → E, to be reversed without allocating a second list. prev starts null, cur starts " +
            "at the head; the reversed part grows behind cur.",
        cells: row(), pointers: pointerRow(), aux: chainRow(), auxLabel: "reversed so far (head first)"))

    while nodes.indices.contains(cur) {
        let next = cur + 1
        let nextLabel = nodes.indices.contains(next) ? nodes[next] : "null"
        let prevLabel = prev >= 0 ? nodes[prev] : "null"
        let moved = nodes[cur]
        prev = cur
        cur = next
        frames.append(WalkFrame(
            status: "Save next = \(nextLabel) first — the instant \(moved).next is reassigned, the rest of the list is " +
                "unreachable. Then \(moved).next = \(prevLabel), and both pointers slide right.",
            cells: row(), pointers: pointerRow(), aux: chainRow(), auxLabel: "reversed so far (head first)",
            readout: "prev = \(nodes[prev]) · cur = \(nodes.indices.contains(cur) ? nodes[cur] : "null")"))
    }

    frames.append(WalkFrame(
        status: "cur ran off the end, so prev — \(nodes[prev]) — is the new head. One pass, three references, no " +
            "extra list: O(n) time and O(1) space.",
        cells: nodes.indices.reversed().map { CellView(nodes[$0], .result) }, aux: chainRow(), auxLabel: "reversed so far (head first)",
        readout: "new head = \(nodes[prev])"))
    return frames
}

func kWayMergeFrames() -> [WalkFrame] {
    let lists = [[2, 6, 8], [3, 6, 7], [1, 3, 4]]
    let k = lists.count
    let total = lists.reduce(0) { $0 + $1.count }
    var cursor = [Int](repeating: 0, count: k)
    var out: [Int] = []
    var frames: [WalkFrame] = []

    func heapEntries() -> [IPair] {
        (0..<k).filter { cursor[$0] < lists[$0].count }.map { IPair($0, lists[$0][cursor[$0]]) }.sortedBy { $0.second }
    }
    func heapRow(popped: Int? = nil) -> [CellView] {
        let e = heapEntries()
        return e.isEmpty ? [CellView("empty", .dim)] : e.map { CellView("\($0.second)·L\($0.first + 1)", $0.first == popped ? .active : .done) }
    }
    func outRow() -> [CellView] { (0..<total).map { i in i < out.count ? CellView("\(out[i])", .window) : CellView("·", .dim) } }

    frames.append(WalkFrame(
        status: "Three sorted lists: \(lists.map { $0.map(String.init).joined(separator: ",") }.joined(separator: "  ")). Concatenating and sorting " +
            "throws the existing order away; a heap of one head per list keeps it.",
        cells: outRow(), aux: heapRow(), auxLabel: "min-heap of list heads (size ≤ \(k))"))

    while out.count < total {
        let top = heapEntries()[0]
        let list = top.first, value = top.second
        out.append(value)
        cursor[list] += 1
        let refill: Int? = cursor[list] < lists[list].count ? lists[list][cursor[list]] : nil
        frames.append(WalkFrame(
            status: "Smallest head is \(value) from L\(list + 1) — pop it into the output, then " +
                (refill.map { "push L\(list + 1)'s next element, \($0)." } ?? "L\(list + 1) is exhausted, so the heap shrinks."),
            cells: outRow(), aux: heapRow(popped: list), auxLabel: "min-heap of list heads (size ≤ \(k))",
            readout: "merged \(out.count) of \(total)"))
    }

    frames.append(WalkFrame(
        status: "\(total) elements merged with a heap that never held more than \(k) entries: O(n log \(k)). Sorting the " +
            "concatenation would have been O(n log n) and would have ignored the sortedness you were handed.",
        cells: out.map { CellView("\($0)", .result) }, aux: heapRow(), auxLabel: "min-heap of list heads (size ≤ \(k))",
        readout: "merged = \(out.map(String.init).joined(separator: ", "))"))
    return frames
}

func greedyIntervalsFrames() -> [WalkFrame] {
    let raw = [IPair(1, 4), IPair(2, 3), IPair(3, 5), IPair(0, 7), IPair(6, 8), IPair(5, 9)]
    let sorted = raw.sortedBy { $0.second }
    var frames: [WalkFrame] = []
    func label(_ iv: IPair) -> String { "\(iv.first)–\(iv.second)" }

    frames.append(WalkFrame(
        status: "Six meetings, one room: keep as many as possible. Sorting by start or by duration both have " +
            "counterexamples — sort by end time, because finishing early is what frees the room.",
        cells: raw.map { CellView(label($0), .idle) }, intervals: raw.map { IntervalView($0.first, $0.second, .idle) }))
    frames.append(WalkFrame(
        status: "Sorted by end: \(sorted.map(label).joined(separator: ", ")). Now one scan decides everything.",
        cells: sorted.map { CellView(label($0), .window) }, intervals: sorted.map { IntervalView($0.first, $0.second, .window) }))

    var kept: [IPair] = []
    var last = Int.min
    for (index, iv) in sorted.enumerated() {
        let take = iv.first >= last
        if take {
            kept.append(iv)
            last = iv.second
        }
        frames.append(WalkFrame(
            status: take
                ? "\(label(iv)) starts at \(iv.first), at or after the room frees at " +
                    (kept.count == 1 ? "the start of the day" : "\(kept[kept.count - 2].second)") +
                    " — keep it. The room is now busy until \(iv.second)."
                : "\(label(iv)) starts at \(iv.first), before the room frees at \(last) — it clashes, so drop it. " +
                    "Nothing kept so far needs revisiting.",
            cells: sorted.enumerated().map { i, v in CellView(label(v), i == index ? .active : kept.contains(v) ? .done : i < index ? .dim : .idle) },
            pointers: [index: "i"],
            intervals: kept.map { IntervalView($0.first, $0.second, .done) } + [IntervalView(iv.first, iv.second, take ? .done : .active)]))
    }

    frames.append(WalkFrame(
        status: "\(kept.count) of \(raw.count) meetings fit: \(kept.map(label).joined(separator: ", ")). The same scan " +
            "answers \"minimum removals\" — \(raw.count - kept.count) — because the two questions are complements.",
        cells: kept.map { CellView(label($0), .result) }, readout: "kept \(kept.count) · removed \(raw.count - kept.count)",
        intervals: kept.map { IntervalView($0.first, $0.second, .result) }))
    return frames
}
