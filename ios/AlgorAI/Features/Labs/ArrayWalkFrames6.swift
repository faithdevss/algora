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
    let jobs = [Job(id: "J1", deadline: 2, profit: 100), Job(id: "J2", deadline: 1, profit: 19), Job(id: "J3", deadline: 2, profit: 27),
                Job(id: "J4", deadline: 1, profit: 25), Job(id: "J5", deadline: 3, profit: 15)]
    let slotCount = jobs.map(\.deadline).max()!
    let byProfit = jobs.sortedByDescending { $0.profit }
    var frames: [WalkFrame] = []

    func jobRow(_ current: Int, _ taken: Set<String>, _ rejected: Set<String>) -> [CellView] {
        byProfit.enumerated().map { i, job in
            CellView("\(job.profit)", i == current ? .active : taken.contains(job.id) ? .done : rejected.contains(job.id) ? .dim : .idle)
        }
    }
    func slotRow(_ slots: [String?], active: Int? = nil) -> [CellView] {
        (1...slotCount).map { s in CellView(slots[s] ?? "·", s == active ? .active : slots[s] != nil ? .result : .dim) }
    }

    var allPointers: [Int: String] = [:]
    for i in byProfit.indices { allPointers[i] = "d\(byProfit[i].deadline)" }
    frames.append(WalkFrame(
        status: "Five jobs, each with a deadline and a profit; one unit of time per job and \(slotCount) slots. " +
            "Sorted by profit, highest first — the greedy claim is that considering them in this order is enough.",
        cells: jobRow(-1, [], []), pointers: allPointers,
        aux: slotRow([String?](repeating: nil, count: slotCount + 1)), auxLabel: "slots 1..\(slotCount)"))

    var slots = [String?](repeating: nil, count: slotCount + 1)
    var taken = Set<String>(), rejected = Set<String>()
    var profit = 0
    for (i, job) in byProfit.enumerated() {
        var placed: Int? = nil
        for s in stride(from: min(job.deadline, slotCount), through: 1, by: -1) where slots[s] == nil {
            slots[s] = job.id
            profit += job.profit
            placed = s
            break
        }
        if placed != nil { taken.insert(job.id) } else { rejected.insert(job.id) }
        frames.append(WalkFrame(
            status: placed != nil
                ? "\(job.id) (profit \(job.profit), deadline \(job.deadline)) goes in slot \(placed!) — the latest free " +
                    "slot at or before its deadline. Taking the latest one keeps the early slots open for jobs " +
                    "that have no other option."
                : "\(job.id) (profit \(job.profit), deadline \(job.deadline)) is dropped: every slot up to " +
                    "\(job.deadline) is already held by a job worth more. Nothing later can rescue it, so the decision is final.",
            cells: jobRow(i, taken, rejected), pointers: [i: "d\(job.deadline)"],
            aux: slotRow(slots, active: placed), auxLabel: "slots 1..\(slotCount)", readout: "profit = \(profit)"))
    }

    let deadlineFirst = runSchedule(jobs.sortedBy { $0.deadline }, slotCount)
    frames.append(WalkFrame(
        status: "Greedy by profit finishes at \(profit), running \(taken.sorted().joined(separator: ", ")). The ordering " +
            "is doing real work here — the same algorithm fed jobs sorted by deadline instead scores " +
            "\(deadlineFirst.profit), because it spends slot 1 on \(deadlineFirst.slots[1] ?? "null") before it has seen " +
            "what else wants that slot.",
        cells: jobRow(-1, taken, rejected), aux: slotRow(deadlineFirst.slots),
        auxLabel: "deadline-first schedule — profit \(deadlineFirst.profit)",
        readout: "profit-first \(profit) vs deadline-first \(deadlineFirst.profit)"))

    var best = 0
    func permute(_ chosen: [Job], _ remaining: [Int]) {
        let result = runSchedule(chosen, slotCount)
        if result.log.allSatisfy({ $0.1 != nil }) { best = max(best, result.profit) }
        for idx in remaining { permute(chosen + [jobs[idx]], remaining.filter { $0 != idx }) }
    }
    permute([], Array(jobs.indices))
    frames.append(WalkFrame(
        status: "Checked against brute force — every subset in every order, keeping only the ones where each job " +
            "lands in a slot: the optimum is \(best). Greedy matched it. That is the exchange argument in action, " +
            "and it holds for every instance, not just this one.",
        cells: jobRow(-1, taken, rejected), aux: slotRow(slots), auxLabel: "greedy schedule — profit \(profit)",
        readout: "greedy \(profit) · brute-force optimum \(best)"))
    return frames
}

func fractionalKnapsackFrames() -> [WalkFrame] {
    let values = [60, 100, 120]
    let weights = [10, 20, 30]
    let capacity = 50
    let order = values.indices.sortedByDescending { Double(values[$0]) / Double(weights[$0]) }
    var frames: [WalkFrame] = []

    func itemRow(_ current: Int, _ taken: Set<Int>, partial: Int? = nil) -> [CellView] {
        order.enumerated().map { pos, idx in
            CellView("\(values[idx])/\(weights[idx])", pos == current ? .active : idx == partial ? .result : taken.contains(idx) ? .done : .idle)
        }
    }

    var ratioPointers: [Int: String] = [:]
    for i in order.indices { ratioPointers[i] = fx(Double(values[order[i]]) / Double(weights[order[i]]), 0) }

    frames.append(WalkFrame(
        status: "Three items and a sack that holds \(capacity) kg. Sorted by value per kg — " +
            order.map { fx(Double(values[$0]) / Double(weights[$0]), 0) }.joined(separator: ", ") +
            " — because when items divide, density is the only thing worth ranking on.",
        cells: itemRow(-1, []), pointers: ratioPointers, readout: "capacity \(capacity) kg"))

    var left = Double(capacity)
    var total = 0.0
    var taken = Set<Int>()
    for (pos, idx) in order.enumerated() {
        let take = min(Double(weights[idx]), left)
        let gained = Double(values[idx]) * take / Double(weights[idx])
        total += gained
        left -= take
        let whole = take == Double(weights[idx])
        if whole { taken.insert(idx) }
        frames.append(WalkFrame(
            status: whole
                ? "Item \(idx + 1) is worth \(values[idx]) at \(weights[idx]) kg — take all of it. Remaining capacity \(fx(left, 0)) kg."
                : "Only \(fx(left + take, 0)) kg of room is left and item \(idx + 1) weighs " +
                    "\(weights[idx]) kg, so take the fraction \(fx(take, 0))/\(weights[idx]) of it for " +
                    "\(fx(gained, 0)). This step is the one 0/1 knapsack is not allowed to make.",
            cells: itemRow(pos, taken, partial: whole ? nil : idx), pointers: ratioPointers,
            readout: "value \(fx(total, 0)) · \(fx(left, 0)) kg left"))
    }

    frames.append(WalkFrame(
        status: "Total \(fx(total, 0)) with the sack exactly full. No exchange can improve it: swapping " +
            "any kilogram for one from a lower-density item strictly loses value, and the sack is never left " +
            "with unused room. That is the proof, not a spot check.",
        cells: itemRow(-1, taken, partial: order.last), readout: "optimal value \(fx(total, 0))"))

    var greedy01 = 0
    var room = capacity
    var taken01 = Set<Int>()
    for idx in order where weights[idx] <= room {
        greedy01 += values[idx]
        room -= weights[idx]
        taken01.insert(idx)
    }
    var dp = [Int](repeating: 0, count: capacity + 1)
    for i in values.indices {
        for c in stride(from: capacity, through: weights[i], by: -1) { dp[c] = max(dp[c], dp[c - weights[i]] + values[i]) }
    }
    frames.append(WalkFrame(
        status: "Forbid the fraction and the same ordering breaks. Density-greedy takes items " +
            taken01.sorted().map { "\($0 + 1)" }.joined(separator: ", ") +
            " for \(greedy01) and leaves \(room) kg unusable; the DP optimum is \(dp[capacity]), from items 2 and 3. " +
            "Greedy is not \"usually close\" here — it is wrong by \(dp[capacity] - greedy01).",
        cells: order.map { idx in CellView("\(values[idx])/\(weights[idx])", taken01.contains(idx) ? .done : .dim) },
        pointers: ratioPointers,
        aux: [CellView("\(greedy01)", .active), CellView("\(dp[capacity])", .result)], auxLabel: "0/1 greedy vs 0/1 optimum",
        readout: "divisible → greedy optimal · indivisible → greedy off by \(dp[capacity] - greedy01)"))
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
