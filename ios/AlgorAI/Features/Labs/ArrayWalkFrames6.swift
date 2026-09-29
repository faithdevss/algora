import Foundation

// ArrayWalkSection.kt builders: job sequencing, fractional knapsack, KMP and Rabin–Karp.

private struct Job: Equatable { let id: String; let deadline: Int; let profit: Int }

private struct Schedule { let profit: Int; let slots: [String?]; let log: [(Job, Int?)] }

private func runSchedule(_ jobs: [Job], _ slotCount: Int) -> Schedule {
    var slots = [String?](repeating: nil, count: slotCount + 1)
    var profit = 0
    var log: [(Job, Int?)] = []
    for job in jobs {
        var placed: Int? = nil
        for s in stride(from: min(job.deadline, slotCount), through: 1, by: -1) where slots[s] == nil {
            slots[s] = job.id
            profit += job.profit
            placed = s
            break
        }
        log.append((job, placed))
    }
    return Schedule(profit: profit, slots: slots, log: log)
}

func jobSequencingFrames() -> [WalkFrame] {
    let jobs = [Job(id: "J1", deadline: 1, profit: 27), Job(id: "J2", deadline: 1, profit: 25), Job(id: "J3", deadline: 2, profit: 100),
                Job(id: "J4", deadline: 1, profit: 19), Job(id: "J5", deadline: 3, profit: 15)]
    let slotCount = jobs.map(\.deadline).max()!
    let byProfit = jobs.sortedByDescending { $0.profit }
    var frames: [WalkFrame] = []
    let legend: [(StoryTone, String)] = [(.active, "Considering"), (.done, "Scheduled"), (.warn, "Skipped")]

    /// result[id] is a scheduled job's slot, or 0 once it has been skipped.
    func frame(_ result: [String: Int], _ considering: String?, _ slots: [String?], _ chips: [StoryChip], _ headline: String, _ body: String) {
        let rows = byProfit.map { job -> StoryTableRow in
            let slot = result[job.id]
            let text: String, tone: StoryTone
            if job.id == considering { text = job.deadline == 1 ? "slot 1 full" : "slots 1–\(job.deadline) full"; tone = .active }
            else if slot == nil { text = "—"; tone = .idle }
            else if slot == 0 { text = "skipped"; tone = .warn }
            else { text = "slot \(slot!)"; tone = .done }
            return StoryTableRow(values: [job.id, "\(job.profit)", "\(job.deadline)", text], tone: tone)
        }
        let story = GreedyStory(
            title: "BY PROFIT, HIGH TO LOW", note: "latest free slot ≤ deadline",
            sections: [
                StorySection(table: StoryTable(headers: ["JOB", "PROFIT", "DUE", "RESULT"], weights: [1, 1.2, 1, 1.7], rows: rows)),
                StorySection(label: "SLOTS", note: (1...slotCount).map(String.init).joined(separator: " · "),
                             cells: (1...slotCount).map { s in slots[s].map { StoryCell($0, .done) } ?? StoryCell("", .empty) }),
            ],
            legend: legend, chips: chips, headline: headline, body: body)
        frames.append(WalkFrame(status: story.status, cells: [], story: story))
    }

    var slots = [String?](repeating: nil, count: slotCount + 1)
    var result: [String: Int] = [:]
    var profit = 0
    frame(result, nil, slots, [StoryChip("profit", "0")], "Sort the jobs by {profit}, highest first.",
          "Each job takes one slot and has to finish by its due slot. There are \(slotCount) slots and \(jobs.count) jobs, so some won't make it.")

    var pendingSkip: String? = nil
    for job in byProfit {
        if let id = pendingSkip { result[id] = 0 }
        pendingSkip = nil
        let placed = stride(from: min(job.deadline, slotCount), through: 1, by: -1).first { slots[$0] == nil }
        if let placed {
            slots[placed] = job.id
            result[job.id] = placed
            profit += job.profit
            frame(result, nil, slots, [StoryChip("profit", "\(profit)")],
                  placed == job.deadline ? "{m:\(job.id)} takes slot \(placed), right at its deadline."
                      : "{m:\(job.id)} is due by slot \(job.deadline), and slot \(placed) is the latest one free.",
                  placed > 1 ? "Taking the latest free slot keeps the earlier ones open for jobs due sooner."
                      : "It goes there, for \(job.profit). Profit so far: \(profit).")
        } else {
            let holders = (1...min(job.deadline, slotCount)).compactMap { slots[$0] }
            frame(result, job.id, slots, [StoryChip("profit", "\(profit)")],
                  job.deadline == 1 ? "{\(job.id)} is due by slot 1, and \(holders[0]) already holds it."
                      : "{\(job.id)} is due by slot \(job.deadline), and \(holders.joined(separator: " and ")) hold every slot up to it.",
                  "So \(job.id) is skipped. Each job takes the latest free slot before its deadline.")
            pendingSkip = job.id
        }
    }
    if let id = pendingSkip { result[id] = 0 }

    let kept = byProfit.filter { (result[$0.id] ?? 0) > 0 }.map(\.id).sorted()
    let lost = byProfit.filter { result[$0.id] == 0 }.map(\.id).sorted()
    frame(result, nil, slots, [StoryChip("profit", "\(profit)", .done)],
          "Done: \(kept.dropLast().joined(separator: ", ")) and \(kept.last!) earn {m:\(profit)}.",
          "\(lost.joined(separator: " and ")) lost their slot to jobs worth more. Every slot is used.")

    var best = 0
    func permute(_ chosen: [Job], _ remaining: [Job]) {
        let run = runSchedule(chosen, slotCount)
        if run.log.allSatisfy({ $0.1 != nil }) { best = max(best, run.profit) }
        for job in remaining { permute(chosen + [job], remaining.filter { $0 != job }) }
    }
    permute([], jobs)
    frame(result, nil, slots, [StoryChip("greedy", "\(profit)", .done), StoryChip("best", "\(best)", .answer)],
          "No schedule beats {v:\(best)}.",
          "Trying every set of jobs in every order, the best feasible profit is \(best). Going by profit is safe: a job only ever loses its slot to one worth more.")
    return frames
}

/// "⅔" for 2/3, plain "a/b" for anything without its own glyph.
private func fractionGlyph(_ num: Int, _ den: Int) -> String {
    var a = num, b = den
    while b != 0 { (a, b) = (b, a % b) }
    let key = "\(num / a)/\(den / a)"
    return ["1/2": "½", "1/3": "⅓", "2/3": "⅔", "1/4": "¼", "3/4": "¾"][key] ?? key
}

func fractionalKnapsackFrames() -> [WalkFrame] {
    let values = [60, 100, 120]
    let weights = [10, 20, 30]
    let capacity = 50
    let order = values.indices.sortedByDescending { Double(values[$0]) / Double(weights[$0]) }
    var frames: [WalkFrame] = []
    func ratio(_ i: Int) -> String { fx(Double(values[i]) / Double(weights[i]), 1) }
    let legend: [(StoryTone, String)] = [(.active, "Taking part"), (.done, "Taken whole"), (.warn, "Won't fit"), (.answer, "0/1 best")]

    func table(_ tones: [Int: StoryTone]) -> StorySection {
        StorySection(table: StoryTable(headers: ["ITEM", "VALUE", "KG", "PER KG"], weights: [1, 1.2, 1, 1.3],
                                       rows: order.map { StoryTableRow(values: ["\($0 + 1)", "\(values[$0])", "\(weights[$0])", ratio($0)], tone: tones[$0] ?? .idle) }))
    }
    func frame(_ sections: [StorySection], _ chips: [StoryChip], _ headline: String, _ body: String) {
        let story = GreedyStory(title: "BY VALUE PER KG", note: "capacity \(capacity) kg", sections: sections, legend: legend,
                                chips: chips, headline: headline, body: body)
        frames.append(WalkFrame(status: story.status, cells: [], story: story))
    }
    /// The sack as blocks by kilogram: whole items, then the part being taken, then the room still free.
    func sack(_ whole: [Int], _ part: (Int, Int)?) -> StorySection {
        let used = whole.reduce(0) { $0 + weights[$1] } + (part?.1 ?? 0)
        let free = capacity - used
        var blocks = whole.map { StoryBlock(text: "\($0 + 1)", weight: CGFloat(weights[$0]), tone: .done) }
        if case let (i, kg)? = part { blocks.append(StoryBlock(text: "\(fractionGlyph(kg, weights[i])) of \(i + 1)", weight: CGFloat(kg), tone: .active)) }
        if free > 0 { blocks.append(StoryBlock(text: "\(free) kg free", weight: CGFloat(free), tone: .empty)) }
        return StorySection(label: "SACK", note: "\(used) kg", blocks: blocks)
    }

    frame([table([:]), sack([], nil)], [StoryChip("value", "0"), StoryChip("room", "\(capacity) kg")],
          "Rank the items by {value per kg}: " + order.map(ratio).joined(separator: ", ") + ".",
          "Items can be cut, so the densest kilogram is always the best one to take next. The sack holds \(capacity) kg.")

    var whole: [Int] = []
    var gains: [Int] = []
    var room = capacity
    for (pos, i) in order.enumerated() {
        let tones = Dictionary(uniqueKeysWithValues: whole.map { ($0, StoryTone.done) })
        if weights[i] <= room {
            whole.append(i)
            gains.append(values[i])
            room -= weights[i]
            let next = pos + 1 < order.count ? order[pos + 1] : nil
            frame([table(tones.merging([i: .done]) { $1 }), sack(whole, nil)],
                  [StoryChip("value", gains.map(String.init).joined(separator: " + ")), StoryChip("room", "\(room) kg")],
                  "Item \(i + 1) fits whole: {m:\(weights[i]) kg} for \(values[i]).",
                  "\(room) kg of room is left." + (next.map { " Next is item \($0 + 1) at \(ratio($0)) per kg." } ?? ""))
        } else {
            frame([table(tones.merging([i: .active]) { $1 }), sack(whole, nil)],
                  [StoryChip("value", gains.map(String.init).joined(separator: " + ")), StoryChip("room", "\(room) kg")],
                  "Item \(i + 1) weighs \(weights[i]) kg, but only {\(room) kg} of room is left.",
                  "A 0/1 knapsack would have to skip it. Here it can be cut.")
            let gained = values[i] * room / weights[i]
            gains.append(gained)
            frame([table(tones.merging([i: .active]) { $1 }), sack(whole, (i, room))],
                  [StoryChip("value", gains.map(String.init).joined(separator: " + ")), StoryChip("room", "0 kg")],
                  "\(room) kg of room is left, so take {\(fractionGlyph(room, weights[i])) of item \(i + 1)} for \(gained).",
                  "Items go in by value per kg, so only the last one is ever split. The total is \(gains.reduce(0, +)).")
            break
        }
    }

    // The same ranking with cutting forbidden, against the best 0/1 packing.
    var taken01: [Int] = []
    var room01 = capacity
    for i in order where weights[i] <= room01 { taken01.append(i); room01 -= weights[i] }
    let greedy01 = taken01.reduce(0) { $0 + values[$1] }
    let best01 = (0..<(1 << values.count))
        .map { mask in values.indices.filter { mask & (1 << $0) != 0 } }
        .filter { set in set.reduce(0) { $0 + weights[$1] } <= capacity }
        .max { a, b in a.reduce(0) { $0 + values[$1] } < b.reduce(0) { $0 + values[$1] } }!
    let bestValue = best01.reduce(0) { $0 + values[$1] }
    let skipped = order.filter { !taken01.contains($0) }
    var greedyBlocks = taken01.map { StoryBlock(text: "\($0 + 1)", weight: CGFloat(weights[$0]), tone: .done) }
    if room01 > 0 { greedyBlocks.append(StoryBlock(text: "\(room01) kg wasted", weight: CGFloat(room01), tone: .empty)) }
    var bestBlocks = best01.sorted { order.firstIndex(of: $0)! < order.firstIndex(of: $1)! }
        .map { StoryBlock(text: "\($0 + 1)", weight: CGFloat(weights[$0]), tone: .answer) }
    let bestFree = capacity - best01.reduce(0) { $0 + weights[$1] }
    if bestFree > 0 { bestBlocks.append(StoryBlock(text: "\(bestFree) kg free", weight: CGFloat(bestFree), tone: .empty)) }
    var tones: [Int: StoryTone] = [:]
    taken01.forEach { tones[$0] = .done }
    skipped.forEach { tones[$0] = .warn }
    frame([table(tones),
           StorySection(label: "0/1 GREEDY", note: "\(greedy01)", blocks: greedyBlocks),
           StorySection(label: "0/1 BEST", note: "\(bestValue)", blocks: bestBlocks)],
          [StoryChip("greedy", "\(greedy01)", .warn), StoryChip("best", "\(bestValue)", .answer)],
          "Forbid the cut and greedy stalls at {w:\(greedy01)}.",
          "Item \(skipped.map { "\($0 + 1)" }.joined(separator: ", ")) no longer fits, so \(room01) kg sits empty. The best 0/1 packing is items \(best01.sorted().map { "\($0 + 1)" }.joined(separator: " and ")) for \(bestValue), so ranking by value per kg only works when items can be split.")
    return frames
}

func kmpFrames() -> [WalkFrame] {
    let text = Array("ABABDABABC"), pattern = Array("ABABC")
    var frames: [WalkFrame] = []
    let m = pattern.count
    var lps = [Int](repeating: 0, count: m)

    func lpsRow(_ filledUpTo: Int, active: Int? = nil) -> [CellView] {
        lps.enumerated().map { i, v in i == active ? CellView("\(v)", .active) : i <= filledUpTo ? CellView("\(v)", .window) : CellView("·", .dim) }
    }
    func patternRow(matched: Int, active: Int? = nil) -> [CellView] {
        pattern.enumerated().map { i, c in CellView(String(c), i == active ? .active : i < matched ? .window : .idle) }
    }

    frames.append(WalkFrame(
        status: "Phase 1 builds the LPS table over the pattern alone: for each position, how long is the longest " +
            "prefix that is also a suffix ending there.",
        cells: pattern.map { CellView(String($0)) }, aux: lpsRow(-1), auxLabel: "LPS"))

    var len = 0, k = 1
    while k < m {
        if pattern[k] == pattern[len] {
            len += 1
            lps[k] = len
            frames.append(WalkFrame(
                status: "P[\(k)] = '\(pattern[k])' matches P[\(len - 1)] = '\(pattern[len - 1])', so the shared prefix grows to \(len). lps[\(k)] = \(len).",
                cells: patternRow(matched: len, active: k), pointers: [k: "k", len - 1: "len"], aux: lpsRow(k - 1, active: k), auxLabel: "LPS"))
            k += 1
        } else if len > 0 {
            frames.append(WalkFrame(
                status: "P[\(k)] = '\(pattern[k])' breaks the run. Fall back to lps[\(len - 1)] = \(lps[len - 1]) " +
                    "instead of restarting — the shorter prefix may still extend.",
                cells: patternRow(matched: len, active: k), pointers: [k: "k"], aux: lpsRow(k - 1), auxLabel: "LPS"))
            len = lps[len - 1]
        } else {
            lps[k] = 0
            frames.append(WalkFrame(
                status: "No prefix of the pattern ends at P[\(k)], so lps[\(k)] = 0.",
                cells: patternRow(matched: 0, active: k), pointers: [k: "k"], aux: lpsRow(k, active: k), auxLabel: "LPS"))
            k += 1
        }
    }

    frames.append(WalkFrame(
        status: "Table complete: [\(lps.map(String.init).joined(separator: ", "))]. Phase 2 now scans the text — and i will never move backward.",
        cells: text.map { CellView(String($0)) }, aux: lpsRow(m - 1), auxLabel: "LPS"))

    var j = 0
    for i in text.indices {
        while j > 0 && text[i] != pattern[j] {
            let fallback = lps[j - 1]
            let jj = j
            frames.append(WalkFrame(
                status: "Mismatch: T[\(i)] = '\(text[i])' ≠ P[\(j)] = '\(pattern[j])'. The first \(fallback) " +
                    "characters are already matched, so j drops to \(fallback) and i stays put.",
                cells: text.enumerated().map { p, c in CellView(String(c), p == i ? .active : inUntil(p, i - jj, i) ? .window : .idle) },
                pointers: [i: "i"], aux: patternRow(matched: fallback, active: fallback),
                auxLabel: "pattern (j = \(fallback) after fallback)", readout: "text pointer stays at \(i)"))
            j = fallback
        }
        if text[i] == pattern[j] { j += 1 }
        let jj = j
        frames.append(WalkFrame(
            status: j == 0
                ? "T[\(i)] = '\(text[i])' does not start the pattern. Move on."
                : "T[\(i)] = '\(text[i])' matches P[\(j - 1)] — \(j) character\(j == 1 ? "" : "s") of the pattern now aligned.",
            cells: text.enumerated().map { p, c in CellView(String(c), p == i ? .active : p > i - jj && p < i ? .window : .idle) },
            pointers: [i: "i"], aux: patternRow(matched: j, active: j < m ? j : nil), auxLabel: "pattern (j = \(j))"))
        if j == m {
            let start = i - m + 1
            frames.append(WalkFrame(
                status: "Full match at index \(start). j falls back to lps[\(m - 1)] = \(lps[m - 1]) so overlapping occurrences are still found.",
                cells: text.enumerated().map { p, c in CellView(String(c), inRange(p, start, i) ? .result : .dim) },
                pointers: [start: "hit"], aux: patternRow(matched: m), auxLabel: "pattern matched",
                readout: "match at \(start) · text scanned once"))
            j = lps[j - 1]
        }
    }
    return frames
}

func rabinKarpFrames() -> [WalkFrame] {
    let textS = "31415926", patternS = "415"
    let text = Array(textS)
    let base = 10, mod = 13
    let m = patternS.count
    var frames: [WalkFrame] = []
    func digit(_ c: Character) -> Int { Int(String(c))! }

    var high = 1
    for _ in 0..<(m - 1) { high = high * base % mod }
    var patternHash = 0
    for c in patternS { patternHash = (patternHash * base + digit(c)) % mod }

    frames.append(WalkFrame(
        status: "Hash the pattern once: \"\(patternS)\" as a base-\(base) number mod \(mod) is \(patternHash). Every text " +
            "window will be compared against this single number.",
        cells: text.map { CellView(String($0), .idle) }, aux: patternS.map { CellView(String($0), .result) },
        auxLabel: "pattern hash = \(patternHash)"))

    var windowHash = 0
    for i in 0..<m { windowHash = (windowHash * base + digit(text[i])) % mod }

    for i in 0...(text.count - m) {
        let window = String(text[i..<(i + m)])
        let collision = windowHash == patternHash && window != patternS
        let hit = window == patternS
        let status: String
        if hit {
            status = "Window \"\(window)\" hashes to \(windowHash) — equal to the pattern's. Verified character by character: a real match at index \(i)."
        } else if collision {
            status = "Window \"\(window)\" also hashes to \(windowHash). Equal hashes are not equal strings, " +
                "so the check rejects it — this is why verification is mandatory."
        } else {
            status = "Window \"\(window)\" hashes to \(windowHash) ≠ \(patternHash). Skip it without comparing a single character."
        }
        frames.append(WalkFrame(
            status: status,
            cells: text.enumerated().map { p, c in CellView(String(c), hit && inUntil(p, i, i + m) ? .result : inUntil(p, i, i + m) ? .window : .dim) },
            pointers: [i: "i"], readout: "hash \(windowHash) vs \(patternHash)" + (hit ? " · match" : "")))

        if i < text.count - m {
            let outgoing = digit(text[i]), incoming = digit(text[i + m])
            windowHash = (windowHash - outgoing * high % mod + mod) % mod
            windowHash = (windowHash * base + incoming) % mod
            frames.append(WalkFrame(
                status: "Roll: drop '\(outgoing)' from the front, shift, add '\(incoming)' at the back. One " +
                    "subtraction and one multiply — the window's hash never gets recomputed from scratch.",
                cells: text.enumerated().map { p, c in
                    CellView(String(c), p == i ? .active : p == i + m ? .active : inUntil(p, i + 1, i + m) ? .window : .dim)
                },
                pointers: [i: "out", i + m: "in"], readout: "new hash \(windowHash)"))
        }
    }
    return frames
}
