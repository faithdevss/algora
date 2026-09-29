import Foundation

// ArrayWalkSection.kt builders: selection and randomised walks. Each counts something as it goes, so
// the closing claim is a number the walk itself produced. `Lcg` is the Kotlin WalkRng.

func ordinalSuffix(_ n: Int) -> String {
    if (11...13).contains(n % 100) { return "\(n)th" }
    switch n % 10 { case 1: return "\(n)st"; case 2: return "\(n)nd"; case 3: return "\(n)rd"; default: return "\(n)th" }
}

/// Lomuto partitions around the last value in range; each round keeps only the side that holds index k.
/// Drawn as the story card: blue is still in play, red is discarded for good, yellow is the pivot.
func quickselectFrames() -> [WalkFrame] {
    let original = [7, 2, 9, 4, 1, 8, 3, 6]
    var a = original
    let k = 2
    var frames: [WalkFrame] = []
    var comparisons = 0
    let legend: [(StoryTone, String)] = [(.active, "Pivot"), (.path, "Still in play"), (.done, "Below pivot"), (.warn, "Discarded"), (.answer, "Answer")]

    func frame(_ lo: Int, _ hi: Int, _ pivot: Int?, _ scan: Int?, _ boundary: Int?, _ answer: Int?,
               _ chips: [StoryChip], _ headline: String, _ body: String) {
        let cells = a.indices.map { i -> StoryCell in
            let tone: StoryTone
            if i == answer { tone = .answer }
            else if i == pivot { tone = .active }
            else if i < lo || i > hi { tone = .warn }
            else if let b = boundary, i >= lo && i < b { tone = .done }
            else { tone = .path }
            if i == scan { return StoryCell("\(a[i])", tone, caption: "j", captionTone: .active) }
            if i == k { return StoryCell("\(a[i])", tone, caption: "k", captionTone: .path) }
            return StoryCell("\(a[i])", tone, caption: "\(i)")
        }
        let story = GreedyStory(title: "FIND k = \(k) · \(ordinalSuffix(k + 1).uppercased()) SMALLEST",
                                note: answer != nil ? "found" : "range \(lo)–\(hi)",
                                sections: [StorySection(cells: cells)], legend: legend, chips: chips, headline: headline, body: body)
        frames.append(WalkFrame(status: story.status, cells: [], story: story))
    }
    func inPlay(_ lo: Int, _ hi: Int) -> StoryChip { StoryChip("in play", "\(hi - lo + 1) of \(a.count)") }

    frame(0, a.count - 1, nil, nil, nil, nil, [StoryChip("k", "\(k)"), inPlay(0, a.count - 1)],
          "Find what sorts into index {p:\(k)}, without sorting.",
          "That is the \(ordinalSuffix(k + 1)) smallest value. Quickselect partitions around a pivot and keeps only the side that holds index \(k).")

    var lo = 0, hi = a.count - 1
    var answer: Int? = nil
    while answer == nil {
        let pivot = a[hi]
        frame(lo, hi, hi, nil, lo, nil, [StoryChip("pivot", "\(pivot)", .active), inPlay(lo, hi)],
              "Partition \(lo)–\(hi) around the last value, {\(pivot)}.",
              "Anything smaller than \(pivot) is swapped to the front of the range; the rest stay behind it.")
        var boundary = lo
        for j in lo..<hi {
            comparisons += 1
            let value = a[j]
            let smaller = value < pivot
            if smaller { a.swapAt(boundary, j); boundary += 1 }
            let below = boundary - lo
            frame(lo, hi, hi, smaller ? boundary - 1 : j, boundary, nil,
                  [StoryChip("pivot", "\(pivot)", .active), StoryChip("below", "\(below)", .done)],
                  smaller ? "{\(value)} < \(pivot), so it joins the front block." : "{\(value)} ≥ \(pivot), so it stays put.",
                  smaller ? "\(below) value\(below == 1 ? " is" : "s are") now below the pivot." : "Only values below the pivot move.")
        }
        a.swapAt(boundary, hi)
        if boundary == k {
            answer = boundary
            frame(lo, hi, nil, nil, nil, boundary, [StoryChip("answer", "\(a[boundary])", .answer), StoryChip("comparisons", "\(comparisons)")],
                  "Pivot {v:\(a[boundary])} settles at index \(boundary), exactly the index we want.",
                  "So \(a[boundary]) is the \(ordinalSuffix(k + 1)) smallest. Neither side was ever sorted.")
        } else if boundary > k {
            frame(lo, boundary - 1, boundary, nil, nil, nil, [StoryChip("pivot", "\(pivot) → index \(boundary)"), inPlay(lo, boundary - 1)],
                  "Pivot {\(pivot)} settles at index \(boundary). We want index \(k), so go left.",
                  "Indices \(boundary)–\(hi) are discarded and never looked at again. Only one side is ever searched.")
            hi = boundary - 1
        } else {
            frame(boundary + 1, hi, boundary, nil, nil, nil, [StoryChip("pivot", "\(pivot) → index \(boundary)"), inPlay(boundary + 1, hi)],
                  "Pivot {\(pivot)} settles at index \(boundary). We want index \(k), so go right.",
                  "Indices \(lo)–\(boundary) are discarded and never looked at again. Only one side is ever searched.")
            lo = boundary + 1
        }
    }

    // What the same array costs to sort, counted the same way.
    var sortComparisons = 0
    var b = original
    for i in 1..<b.count {
        var j = i
        while j > 0 {
            sortComparisons += 1
            if b[j - 1] <= b[j] { break }
            b.swapAt(j - 1, j)
            j -= 1
        }
    }
    frame(lo, hi, nil, nil, nil, answer, [StoryChip("quickselect", "\(comparisons)", .answer), StoryChip("sort", "\(sortComparisons)")],
          "{v:\(a[answer!])} found in \(comparisons) comparisons; sorting takes \(sortComparisons).",
          "Sorting orders every value. Quickselect follows one side each round, n + n/2 + n/4 … ≈ 2n on average.")
    return frames
}

/// Median of medians: groups of five, each sorted, their middles, and the middle of those as the pivot.
func medianOfMediansFrames() -> [WalkFrame] {
    let a = [12, 3, 17, 8, 1, 20, 6, 14, 9, 2, 18, 11, 5, 15, 7]
    let groups = stride(from: 0, to: a.count, by: 5).map { Array(a[$0..<min($0 + 5, a.count)]) }
    let sortedGroups = groups.map { $0.sorted() }
    let medians = sortedGroups.map { $0[$0.count / 2] }
    let pivot = medians.sorted()[medians.count / 2]
    let below = a.filter { $0 < pivot }.count
    let above = a.filter { $0 > pivot }.count
    let floor = 30
    var frames: [WalkFrame] = []
    let legend: [(StoryTone, String)] = [(.active, "Group median"), (.answer, "Pivot"), (.path, "Below pivot")]

    func frame(_ title: String, _ note: String, _ rows: [[Int]], _ tone: (Int, Int) -> StoryTone, _ mediansRow: [StoryBlock]?,
               _ chips: [StoryChip], _ headline: String, _ body: String) {
        var sections = [StorySection(grid: rows.enumerated().map { g, row in
            StoryGridLine(label: "g\(g + 1)", cells: row.enumerated().map { i, v in StoryCell("\(v)", tone(v, i)) })
        })]
        if let mediansRow { sections.append(StorySection(label: "MEDIANS", note: "median of these = pivot", blocks: mediansRow)) }
        let story = GreedyStory(title: title, note: note, sections: sections, legend: legend, chips: chips, headline: headline, body: body)
        frames.append(WalkFrame(status: story.status, cells: [], story: story))
    }
    let title = "\(groups.count) GROUPS OF 5 · EACH SORTED"
    func blocks(_ tone: (Int) -> StoryTone) -> [StoryBlock] { medians.map { StoryBlock(text: "\($0)", weight: 1, tone: tone($0)) } }

    frame("\(groups.count) GROUPS OF 5", "\(a.count) values", groups, { _, _ in .idle }, nil, [StoryChip("values", "\(a.count)")],
          "A bad pivot makes quickselect {w:O(n²)}.",
          "Median of medians picks a pivot that is guaranteed to split well, so even the worst case is linear.")
    frame(title, "median = middle", sortedGroups, { _, _ in .idle }, medians.map { _ in StoryBlock(text: "", weight: 1, tone: .empty) },
          [StoryChip("groups", "\(groups.count)")],
          "Sort each group of {5}.", "Five values is constant work, so sorting every group is O(n) in total.")
    frame(title, "median = middle", sortedGroups, { _, i in i == 2 ? .active : .idle }, blocks { _ in .active },
          [StoryChip("medians", medians.map(String.init).joined(separator: ", "))],
          "Each group's middle value is its {median}: \(medians.map(String.init).joined(separator: ", ")).",
          "Only these \(medians.count) values matter for picking the pivot.")
    frame(title, "median = middle", sortedGroups, { v, i in i != 2 ? .idle : v == pivot ? .answer : .active }, blocks { $0 == pivot ? .answer : .active },
          [StoryChip("pivot", "\(pivot)", .answer), StoryChip("below", "≥ \(floor)%")],
          "The medians are \(medians.dropLast().map(String.init).joined(separator: ", ")) and \(medians.last!), so the pivot is {v:\(pivot)}.",
          "At least \(floor)% of the elements are below \(pivot) and \(floor)% above, so every split is guaranteed to be fair.")
    frame(title, "median = middle", sortedGroups, { v, _ in v == pivot ? .answer : v < pivot ? .path : .idle },
          blocks { $0 == pivot ? .answer : $0 < pivot ? .path : .idle },
          [StoryChip("below", "\(below)", .path), StoryChip("above", "\(above)")],
          "Partition on {v:\(pivot)}: \(below) below, \(above) above.",
          "Half the groups have a median ≤ \(pivot), and each of those has 3 values ≤ its median, so at least 3n/10 land on each side.")
    frame(title, "median = middle", sortedGroups, { v, _ in v == pivot ? .answer : .idle }, blocks { $0 == pivot ? .answer : .idle },
          [StoryChip("worst case", "O(n)", .done)],
          "Guaranteed {m:O(n)}, at a price.",
          "Grouping, sorting and recursing for the pivot all cost time, so a random pivot is faster in practice. This one is for when the worst case has to hold.")
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
