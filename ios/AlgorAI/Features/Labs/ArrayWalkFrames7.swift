import Foundation

// ArrayWalkSection.kt builders: Manacher, sparse table, and the Apriori / Eclat association-rule walks
// (both run on the shared database in AssociationMath.swift).

func manacherFrames() -> [WalkFrame] {
    let s = "abacaba"
    var tStr = "#"
    for ch in s { tStr.append(ch); tStr.append("#") }
    let t = Array(tStr)
    let sc = Array(s)
    var p = [Int](repeating: 0, count: t.count)
    var frames: [WalkFrame] = []

    func radiusRow(_ upTo: Int, active: Int? = nil) -> [CellView] {
        p.enumerated().map { i, v in i == active ? CellView("\(v)", .active) : i <= upTo ? CellView("\(v)", .window) : CellView("·", .dim) }
    }

    frames.append(WalkFrame(
        status: "\"\(s)\" becomes \"\(tStr)\". With separators every palindrome has odd length, so one loop handles " +
            "both the even and odd cases.",
        cells: t.map { CellView(String($0)) }, aux: radiusRow(-1), auxLabel: "p (radius)"))

    var c = 0, r = 0
    for i in t.indices {
        let mirror = 2 * c - i
        let seeded = i < r
        if seeded { p[i] = min(r - i, p[mirror]) }
        let seed = p[i]
        while i - p[i] - 1 >= 0 && i + p[i] + 1 < t.count && t[i - p[i] - 1] == t[i + p[i] + 1] { p[i] += 1 }
        let grown = p[i] - seed
        let status: String
        if seeded && grown == 0 {
            status = "i = \(i) sits inside the palindrome centred at \(c), so its mirror at \(mirror) hands over " +
                "radius \(seed) for free. Direct comparison adds nothing."
        } else if seeded {
            status = "Mirror at \(mirror) seeds radius \(seed); expanding past the right edge adds \(grown) more, so p[\(i)] = \(p[i])."
        } else {
            status = "i = \(i) is outside every known palindrome, so it expands from scratch to radius \(p[i])."
        }
        var pointers: [Int: String] = [i: "i"]
        if seeded { pointers[mirror] = "mirror" }
        let pi = p[i]
        frames.append(WalkFrame(
            status: status,
            cells: t.enumerated().map { pos, ch in CellView(String(ch), pos == i ? .active : inRange(pos, i - pi, i + pi) ? .window : .idle) },
            pointers: pointers, aux: radiusRow(i - 1, active: i), auxLabel: "p (radius)", readout: "right edge r = \(r)"))
        if i + p[i] > r {
            c = i
            r = i + p[i]
        }
    }

    let best = argmaxFirst(p)
    let start = (best - p[best]) / 2
    let longest = String(sc[start..<(start + p[best])])
    frames.append(WalkFrame(
        status: "Largest radius is \(p[best]) at centre \(best), which maps back to \"\(longest)\" " +
            "in the original string. The right edge only ever moved right, so the whole scan was linear.",
        cells: t.enumerated().map { pos, ch in CellView(String(ch), inRange(pos, best - p[best], best + p[best]) ? .result : .dim) },
        pointers: [best: "centre"], aux: radiusRow(t.count - 1, active: best), auxLabel: "p (radius)",
        readout: "longest palindrome: \(longest)"))
    return frames
}

func aprioriFrames() -> [WalkFrame] {
    var frames: [WalkFrame] = []
    let passes = aprioriPasses()

    frames.append(WalkFrame(
        status: "\(transactions.count) baskets over \(basketItems.count) items, minimum support \(minSupport) of " +
            "\(transactions.count). The cells are the \(basketItems.count) single items; the row beneath is how many " +
            "baskets each appears in.",
        cells: basketItems.map { CellView($0) }, aux: basketItems.map { CellView("\(support([$0]))", .dim) }, auxLabel: "support"))

    for pass in passes {
        let labels = pass.candidates.map(\.label)
        let counting = pass.candidates.map { CellView($0.label, $0.prunedBySubset != nil ? .dim : .active) }

        if pass.k > 1 {
            let pruned = pass.candidates.filter { $0.prunedBySubset != nil }
            frames.append(WalkFrame(
                status: pruned.isEmpty
                    ? "Level \(pass.k): join the frequent \(pass.k - 1)-itemsets pairwise to get \(labels.count) " +
                        "candidates. Every \(pass.k - 1)-subset of every one of them is frequent, so none can be " +
                        "ruled out in advance — all \(labels.count) have to be counted."
                    : "Level \(pass.k): joining gives \(labels.count) candidates, but " +
                        "\(pruned.map(\.label).joined(separator: ", ")) contains the subset " +
                        "\(pruned.map { itemsetLabel($0.prunedBySubset!) }.joined(separator: ", ")), which level \(pass.k - 1) " +
                        "already found infrequent. **A superset of an infrequent set cannot be frequent** — so it is " +
                        "discarded without reading a single basket. That is the Apriori property, and it is the whole algorithm.",
                cells: pass.candidates.map { CellView($0.label, $0.prunedBySubset != nil ? .dim : .idle) },
                aux: pass.candidates.map { CellView($0.prunedBySubset != nil ? "✗" : "?", .dim) },
                auxLabel: "before counting", readout: "\(pass.counted) of \(pass.candidates.count) need a database pass"))
        }

        let below = pass.candidates.filter { $0.prunedBySubset == nil && !$0.frequent }
        let belowText = below.isEmpty
            ? "All of them clear support \(minSupport)."
            : "\(below.map { "\($0.label) (\($0.support))" }.joined(separator: ", ")) " +
                "\(below.count == 1 ? "falls" : "fall") below \(minSupport) and \(below.count == 1 ? "is" : "are") dropped."
        frames.append(WalkFrame(
            status: "Counting pass \(pass.k): read all \(transactions.count) baskets once and tally the " +
                "\(pass.counted) surviving candidate\(pass.counted == 1 ? "" : "s"). " + belowText,
            cells: counting,
            aux: pass.candidates.map { c in
                CellView(c.prunedBySubset != nil ? "—" : "\(c.support)", c.prunedBySubset != nil ? .dim : c.frequent ? .done : .idle)
            },
            auxLabel: "support", readout: "L\(pass.k) = { \(pass.frequent.map(\.label).joined(separator: ", ")) }"))
    }

    let last = passes.last!
    let lastCells = last.frequent.map { CellView($0.label, .result) }
    let lastAux = last.frequent.map { CellView("\($0.support)", .result) }
    frames.append(WalkFrame(
        status: "No pair of \(last.k)-itemsets shares a prefix, so nothing can be joined and the search stops. " +
            "The frequent sets are everything marked green along the way — found in \(passes.count) database passes, " +
            "one per level, which is Apriori's cost and its weakness on long itemsets.",
        cells: lastCells.isEmpty ? [CellView("—")] : lastCells, aux: lastAux.isEmpty ? [CellView("—")] : lastAux,
        auxLabel: "support", readout: "\(passes.count) database passes"))

    let rules = allRules()
    let strongest = rules[0]
    let misleading = rules.filter { $0.lift < 1.0 }.max { $0.confidence < $1.confidence }
    func short(_ r: AssociationRule) -> String { r.label.replacingOccurrences(of: " → ", with: "→") }
    frames.append(WalkFrame(
        status: "Frequent itemsets are not rules yet. Split each one every way and score the split: confidence is " +
            "P(consequent | antecedent), lift is that divided by P(consequent) on its own. " +
            "\(strongest.label) has confidence \(fx(strongest.confidence)) and lift \(fx(strongest.lift)).",
        cells: rules.prefix(6).map { CellView(short($0), .idle) },
        aux: rules.prefix(6).map { CellView(fx($0.lift), $0.lift > 1.0 ? .done : .idle) }, auxLabel: "lift"))

    if let misleading {
        let top = Array(rules.sortedByDescending(\.confidence).prefix(8))
        let isIt: (AssociationRule) -> Bool = { $0.label == misleading.label }
        frames.append(WalkFrame(
            status: "And the trap. \(misleading.label) has confidence \(fx(misleading.confidence)) — a " +
                "rule that fires \(fx(misleading.confidence * 100, 0))% of the time, which sounds like a " +
                "finding. But \(itemsetLabel(misleading.consequent)) appears in " +
                "\(support(misleading.consequent)) of \(transactions.count) baskets anyway, so lift is " +
                "\(fx(misleading.lift)): buying \(itemsetLabel(misleading.antecedent)) makes " +
                "\(itemsetLabel(misleading.consequent)) *less* likely, not more. Confidence cannot see that, because " +
                "it never looks at how common the consequent is.",
            cells: top.map { CellView(short($0), isIt($0) ? .active : .dim) },
            aux: top.map { CellView(fx($0.confidence), isIt($0) ? .active : .dim) }, auxLabel: "confidence",
            readout: "conf \(fx(misleading.confidence)) · lift \(fx(misleading.lift))"))
    }
    return frames
}

func eclatFrames() -> [WalkFrame] {
    var frames: [WalkFrame] = []
    let order = basketItems.filter { support([$0]) >= minSupport }.sortedByDescending { support([$0]) }

    func tidRow(_ tids: [Int], _ mark: CellMark = .window) -> [CellView] {
        transactions.indices.map { t in CellView("\(t + 1)", tids.contains(t) ? mark : .dim) }
    }

    frames.append(WalkFrame(
        status: "Apriori stores the database by row: basket 1 holds these items, basket 2 holds those. Eclat turns it " +
            "on its side and stores it by column — for each item, the set of basket ids containing it. Same data, and " +
            "the whole algorithm follows from the layout.",
        cells: transactions.indices.map { CellView("\($0 + 1)") },
        aux: transactions.map { CellView($0.sorted().joined(), .idle) }, auxLabel: "items"))

    for item in order {
        let tids = tidList(item)
        frames.append(WalkFrame(
            status: "The tid-list for \(item) (\(basketNames[item]!)): baskets " +
                "\(tids.map { "\($0 + 1)" }.joined(separator: ", ")). Its support is not counted — it is the length of this list, " +
                "\(tids.count). That is the trade Eclat makes: hold the lists in memory and never scan the database again.",
            cells: tidRow(tids), aux: transactions.indices.map { CellView($0 < tids.count ? "•" : "", .dim) },
            auxLabel: "|t(\(item))| = \(tids.count)", readout: "support(\(item)) = \(tids.count)"))
    }

    let a = order[0], b = order[1]
    let ta = tidList(a), tb = tidList(b)
    let intersection = ta.filter { tb.contains($0) }
    frames.append(WalkFrame(
        status: "Extending \(a) with \(b) is a set intersection: t(\(a)) ∩ t(\(b)). The cells show t(\(a)) in blue; the row " +
            "beneath marks which of those also appear in t(\(b)). \(intersection.count) survive, so support(\(a)\(b)) = " +
            "\(intersection.count) — again with no counting pass, because the intersection *is* the count.",
        cells: tidRow(ta),
        aux: transactions.indices.map { t in
            CellView(intersection.contains(t) ? "✓" : ta.contains(t) ? "✗" : "", intersection.contains(t) ? .done : .dim)
        },
        auxLabel: "also in t(\(b))", readout: "support(\(a)\(b)) = \(intersection.count)"))

    let all = eclatSteps()
    let steps = all.filter { $0.depth > 0 }
    let deepest = steps.max { $0.itemset.count < $1.itemset.count }
    for step in steps.prefix(6) {
        frames.append(WalkFrame(
            status: "Depth \(step.depth): \(step.label), \(step.tids.count) tids. " +
                (step.frequent
                    ? "Still frequent, so the search goes deeper along this branch before backtracking — depth-first, " +
                        "unlike Apriori's level-by-level sweep."
                    : "Below support \(minSupport), so this branch is abandoned immediately and nothing under it is ever generated."),
            cells: tidRow(step.tids, step.frequent ? .done : .idle), aux: transactions.indices.map { _ in CellView("", .dim) },
            auxLabel: step.label, readout: "support(\(step.label)) = \(step.tids.count)"))
    }

    let frequent = all.filter { $0.frequent && $0.itemset.count >= 2 }
    frames.append(WalkFrame(
        status: "The frequent itemsets Eclat ends with are exactly the ones Apriori ends with — " +
            "\(frequent.map(\.label).joined(separator: ", ")) — which is the point: these are different searches of the same " +
            "space, not different definitions of the answer. Eclat wins when tid-lists are short (sparse data, high " +
            "support) and loses when they are long, because a tid-list for a common item is nearly the whole database " +
            "and intersecting two of them is expensive. \(deepest.map { "The deepest branch reached \($0.label)." } ?? "")",
        cells: frequent.map { CellView($0.label, .result) }, aux: frequent.map { CellView("\($0.tids.count)", .result) },
        auxLabel: "support", readout: "\(frequent.count) frequent itemsets of size ≥ 2"))
    return frames
}
