import Foundation

// ArrayWalkSection.kt builders: selection and randomised walks. Each counts something as it goes, so
// the closing claim is a number the walk itself produced. `Lcg` is the Kotlin WalkRng.

func quickselectFrames() -> [WalkFrame] {
    var a = [7, 2, 9, 4, 1, 8, 3, 6]
    let target = 3
    let sorted = a.sorted()
    var frames: [WalkFrame] = []
    var comparisons = 0

    func row(_ lo: Int, _ hi: Int, _ pivotIndex: Int?, _ scan: Int?, _ boundary: Int?, done: Int? = nil) -> [CellView] {
        a.enumerated().map { i, v in
            let mark: CellMark = i == done ? .result : i < lo || i > hi ? .dim : i == pivotIndex ? .result : i == scan ? .active
                : boundary != nil && i < boundary! ? .window : .idle
            return CellView("\(v)", mark)
        }
    }

    frames.append(WalkFrame(
        status: "Find the \(target + 1)th smallest value without sorting. Sorting would order all \(a.count) " +
            "elements; quickselect only ever descends into the side that can still contain the answer.",
        cells: row(0, a.count - 1, nil, nil, nil), readout: "target: rank \(target + 1) of \(a.count)"))

    var lo = 0, hi = a.count - 1
    var answer = -1
    while lo <= hi {
        let pivot = a[hi]
        frames.append(WalkFrame(status: "Partition a[\(lo)..\(hi)] around the pivot \(pivot). Everything smaller is swapped to the front.",
                                cells: row(lo, hi, hi, nil, nil), pointers: [hi: "pivot"]))
        var boundary = lo
        for j in until(lo, hi) {
            comparisons += 1
            let smaller = a[j] < pivot
            if smaller {
                a.swapAt(boundary, j)
                boundary += 1
            }
            frames.append(WalkFrame(
                status: smaller
                    ? "\(a[boundary - 1]) < \(pivot) — swap it into the smaller-than-pivot block, which now holds \(boundary - lo)."
                    : "\(a[j]) ≥ \(pivot) — leave it where it is.",
                cells: row(lo, hi, hi, j, boundary), pointers: [j: "j", hi: "pivot"]))
        }
        a.swapAt(boundary, hi)

        if boundary == target {
            answer = a[boundary]
            frames.append(WalkFrame(
                status: "The pivot lands at index \(boundary), which is exactly the rank we wanted. Its final " +
                    "position is its answer — no further work, and the two sides were never sorted.",
                cells: row(lo, hi, nil, nil, nil, done: boundary),
                readout: "\(target + 1)th smallest = \(answer), found in \(comparisons) comparisons"))
            lo = boundary + 1
            hi = boundary
        } else if boundary > target {
            frames.append(WalkFrame(
                status: "The pivot settles at index \(boundary), above the rank we want, so the answer lies to " +
                    "its left. Indices \(boundary)..\(hi) are discarded and never looked at again.",
                cells: row(lo, boundary - 1, nil, nil, nil), readout: "\(boundary - lo) of \(a.count) still in play"))
            hi = boundary - 1
        } else {
            frames.append(WalkFrame(
                status: "The pivot settles at index \(boundary), below the rank we want, so the answer lies to " +
                    "its right. Everything from \(lo) up to and including \(boundary) is discarded.",
                cells: row(boundary + 1, hi, nil, nil, nil), readout: "\(hi - boundary) of \(a.count) still in play"))
            lo = boundary + 1
        }
    }

    var sortComparisons = 0
    var b = [7, 2, 9, 4, 1, 8, 3, 6]
    for i in 1..<b.count {
        var j = i
        while j > 0 {
            sortComparisons += 1
            if b[j - 1] <= b[j] { break }
            b.swapAt(j - 1, j)
            j -= 1
        }
    }

    frames.append(WalkFrame(
        status: "Quickselect answered in \(comparisons) comparisons. Sorting the same array to read off index " +
            "\(target) takes \(sortComparisons) — and sorting computes the other \(a.count - 1) ranks nobody asked " +
            "for. Expected cost is linear because each round discards a constant fraction: n + n/2 + n/4 … ≈ 2n.",
        cells: sorted.enumerated().map { i, v in CellView("\(v)", i == target ? .result : .dim) },
        readout: "\(comparisons) comparisons vs \(sortComparisons) to sort"))
    return frames
}

func medianOfMediansFrames() -> [WalkFrame] {
    let a = [12, 3, 17, 8, 1, 20, 6, 14, 9, 2, 18, 11, 5, 15, 7]
    let groups = stride(from: 0, to: a.count, by: 5).map { Array(a[$0..<min($0 + 5, a.count)]) }
    let medians = groups.map { $0.sorted()[$0.count / 2] }
    let pivot = medians.sorted()[medians.count / 2]
    let below = a.filter { $0 < pivot }.count
    let above = a.filter { $0 > pivot }.count
    var frames: [WalkFrame] = []

    func row(_ mark: (Int) -> CellMark) -> [CellView] { a.enumerated().map { i, v in CellView("\(v)", mark(i)) } }

    frames.append(WalkFrame(
        status: "Quickselect is linear *on average*, but a badly chosen pivot splits off one element at a time " +
            "and costs O(n²). Median of medians picks a pivot with a guaranteed split, making the worst case linear too.",
        cells: row { _ in .idle }, readout: "\(a.count) elements, in groups of 5"))
    frames.append(WalkFrame(
        status: "Split into \(groups.count) groups of 5. Each group is small and fixed-size, so sorting one is " +
            "constant work — \(groups.count) groups is O(n) in total.",
        cells: row { ($0 / 5) % 2 == 0 ? .window : .idle },
        aux: groups.flatMap { g in g.sorted().map { CellView("\($0)", .dim) } }, auxLabel: "each group, sorted"))
    frames.append(WalkFrame(
        status: "Take each group's median: \(medians.map(String.init).joined(separator: ", ")). These \(medians.count) values are the " +
            "only ones that matter for choosing the pivot.",
        cells: row { _ in .dim },
        aux: groups.flatMap { g in g.sorted().enumerated().map { i, v in CellView("\(v)", i == g.count / 2 ? .active : .dim) } },
        auxLabel: "group medians highlighted"))
    frames.append(WalkFrame(
        status: "The pivot is the median of those medians: \(pivot). Finding it is a recursive quickselect on a " +
            "list one fifth the size, which is what keeps the recursion affordable.",
        cells: row { a[$0] == pivot ? .result : .dim },
        aux: medians.map { CellView("\($0)", $0 == pivot ? .result : .window) }, auxLabel: "the \(medians.count) medians",
        readout: "pivot = \(pivot)"))
    frames.append(WalkFrame(
        status: "Partitioning on \(pivot) puts \(below) elements below it and \(above) above — the smaller side is " +
            "\(fx(100.0 * Double(min(below, above)) / Double(a.count), 0))% of the array, so that is what gets thrown " +
            "away this round. Half the groups have a median on each side of the pivot, and in each of those at " +
            "least three of five elements fall the same way, so at least 3n/10 is discarded no matter what the " +
            "input is. That floor is what turns the worst case linear.",
        cells: row { a[$0] == pivot ? .result : a[$0] < pivot ? .window : .idle },
        readout: "\(below) below · \(above) above — guaranteed floor is \(3 * a.count / 10)"))
    frames.append(WalkFrame(
        status: "The catch is the constant. Grouping, sorting each group and recursing to find the pivot all cost " +
            "real time, so in practice a random pivot is faster and median of medians is reserved for when a " +
            "worst-case bound actually has to hold.",
        cells: row { a[$0] == pivot ? .result : .dim },
        readout: "guaranteed O(n) — at a constant factor you pay on every input"))
    return frames
}

func mosAlgorithmFrames() -> [WalkFrame] {
    let a = [4, 1, 7, 2, 9, 3, 6, 5, 8, 2, 4, 1]
    let queries = [IPair(0, 4), IPair(6, 11), IPair(1, 3), IPair(5, 9), IPair(2, 7)]
    let block = 3

    func movesFor(_ order: [IPair]) -> Int {
        var l = 0, r = -1, moves = 0
        for q in order {
            moves += abs(q.first - l) + abs(q.second - r)
            l = q.first
            r = q.second
        }
        return moves
    }

    let sorted = queries.sortedBy { ($0.first / block) * 10_000 + $0.second }
    let naiveMoves = movesFor(queries)
    var frames: [WalkFrame] = []
    func label(_ qs: [IPair]) -> String { qs.map { "[\($0.first),\($0.second)]" }.joined(separator: "  ") }

    func row(_ l: Int, _ r: Int, active: Int? = nil) -> [CellView] {
        a.enumerated().map { i, v in CellView("\(v)", i == active ? .active : inRange(i, l, r) ? .window : .dim) }
    }

    frames.append(WalkFrame(
        status: "Five range-sum queries over \(a.count) elements, and all of them are known up front. That is the " +
            "one assumption Mo's algorithm needs: the queries are offline, so we may answer them in whatever " +
            "order is cheapest.",
        cells: a.map { CellView("\($0)") }, readout: label(queries)))
    frames.append(WalkFrame(
        status: "Answering them in the order asked drags the two pointers back and forth across the array: " +
            "\(naiveMoves) pointer moves in total. Nothing is wrong with the answers — the cost is purely the " +
            "travel between consecutive ranges.",
        cells: row(queries[0].first, queries[0].second), readout: "\(naiveMoves) pointer moves in query order"))
    frames.append(WalkFrame(
        status: "Sort the queries instead by which block of \(block) their left end falls in, breaking ties by " +
            "right end. Within a block the right pointer only ever advances, and the left pointer stays inside a " +
            "window of \(block).",
        cells: a.enumerated().map { i, v in CellView("\(v)", (i / block) % 2 == 0 ? .window : .idle) }, readout: label(sorted)))

    var l = 0, r = -1, sum = 0, moves = 0
    for (qi, q) in sorted.enumerated() {
        let ql = q.first, qr = q.second
        while r < qr { r += 1; sum += a[r]; moves += 1 }
        while l > ql { l -= 1; sum += a[l]; moves += 1 }
        while r > qr { sum -= a[r]; r -= 1; moves += 1 }
        while l < ql { sum -= a[l]; l += 1; moves += 1 }
        frames.append(WalkFrame(
            status: "Query \(qi + 1) of \(sorted.count): [\(ql), \(qr)] in block \(ql / block). The window is adjusted " +
                "one element at a time rather than rebuilt, so the running sum is reused.",
            cells: row(l, r), pointers: [l: "L", r: "R"], readout: "sum = \(sum)   ·   \(moves) moves so far"))
    }

    frames.append(WalkFrame(
        status: "All five answered in \(moves) pointer moves against \(naiveMoves) in the order they arrived. The " +
            "ordering is the whole algorithm: with a block size of about √n the total travel is O((n + q)√n), " +
            "which beats recomputing each range from scratch whenever the ranges are long.",
        cells: a.map { CellView("\($0)", .done) }, readout: "\(naiveMoves) → \(moves) pointer moves"))
    return frames
}

func reservoirSamplingFrames() -> [WalkFrame] {
    let stream = [41, 17, 63, 8, 92, 25, 54, 39, 71, 6, 88, 30]
    let k = 3
    var rng = Lcg(20240727)
    var reservoir = Array(stream.prefix(k))
    var frames: [WalkFrame] = []

    func streamRow(_ upTo: Int, _ active: Int?, _ kept: Set<Int>) -> [CellView] {
        stream.enumerated().map { i, v in CellView("\(v)", i == active ? .active : kept.contains(i) ? .result : i <= upTo ? .dim : .idle) }
    }

    var keptIndices = Set(0..<k)

    frames.append(WalkFrame(
        status: "Pick \(k) items uniformly at random from a stream whose length is unknown until it ends, storing " +
            "only the \(k). The first \(k) items go straight in — at that point they are the whole stream.",
        cells: streamRow(k - 1, nil, keptIndices), aux: reservoir.map { CellView("\($0)", .result) }, auxLabel: "reservoir",
        readout: "reservoir filled with the first \(k) items"))

    for i in k..<stream.count {
        let n = i + 1
        let roll = rng.next()
        let threshold = Double(k) / Double(n)
        let accepted = roll < threshold
        var replacedSlot = -1
        if accepted {
            replacedSlot = rng.nextInt(k)
            let evicted = reservoir[replacedSlot]
            keptIndices = keptIndices.filter { stream[$0] != evicted }
            keptIndices.insert(i)
            reservoir[replacedSlot] = stream[i]
        }
        let slot = replacedSlot
        frames.append(WalkFrame(
            status: accepted
                ? "Item \(i + 1) is \(stream[i]). It is accepted with probability \(k)/\(n) = " +
                    "\(fx(threshold)); the draw was \(fx(roll)), so it enters and evicts slot \(replacedSlot)."
                : "Item \(i + 1) is \(stream[i]). Accepted with probability \(k)/\(n) = \(fx(threshold)), " +
                    "but the draw was \(fx(roll)) — it is discarded and never stored.",
            cells: streamRow(i, i, keptIndices),
            aux: reservoir.enumerated().map { s, v in CellView("\(v)", s == slot ? .active : .result) }, auxLabel: "reservoir",
            readout: "accept probability \(k)/\(n) = \(fx(threshold))"))
    }

    let trials = 20000
    var hits = [Int](repeating: 0, count: stream.count)
    var trialRng = Lcg(987654321)
    for _ in 0..<trials {
        var res = Array(0..<k)
        for i in k..<stream.count where trialRng.next() < Double(k) / Double(i + 1) {
            res[trialRng.nextInt(k)] = i
        }
        for idx in res { hits[idx] += 1 }
    }
    let rates = hits.map { Double($0) / Double(trials) }
    let expected = Double(k) / Double(stream.count)

    frames.append(WalkFrame(
        status: "The stream is done and the reservoir holds \(reservoir.map(String.init).joined(separator: ", ")) — using memory for \(k) " +
            "items, never \(stream.count). The claim that matters is that every item had an equal chance of " +
            "ending up there, so run it \(trials) times and count.",
        cells: streamRow(stream.count - 1, nil, keptIndices), aux: reservoir.map { CellView("\($0)", .result) }, auxLabel: "final reservoir",
        readout: "selection rate ranged \(fx(rates.min()!, 3))–\(fx(rates.max()!, 3)) against the expected \(fx(expected, 3))"))
    frames.append(WalkFrame(
        status: "Every position lands within \(fx(rates.map { abs($0 - expected) }.max()!, 3)) of " +
            "\(k)/\(stream.count) = \(fx(expected, 3)), including the very first item — which survives only " +
            "by never being evicted — and the last, which walks in with probability \(k)/\(stream.count). The " +
            "shrinking accept probability is exactly what keeps those two equal.",
        cells: rates.map { CellView(fx($0), abs($0 - expected) < 0.01 ? .done : .active) },
        auxLabel: "measured selection rate per position", readout: "expected \(fx(expected, 3)) everywhere"))
    return frames
}
