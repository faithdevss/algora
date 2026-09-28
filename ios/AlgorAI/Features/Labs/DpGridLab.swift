import SwiftUI

// Port of DpGridSection.kt: fills a DP table cell by cell, then walks the traceback path.

/// One candidate in a cell's recurrence — edit distance's replace/delete/insert, LCS's match/up/left —
/// shown as a card under the grid, the chosen one outlined.
private struct DpOption { let arrow: String; let name: String; let formula: String; let chosen: Bool }

private enum DpTone { case neutral, alert, good }

private struct DpChip { let label: String; let value: String; var tone: DpTone = .neutral }

/// Headline in three parts so the middle (the value just written) can take the active yellow.
private struct DpHeadline { let lead: String; let emphasis: String; let tail: String }

private struct DpFrame {
    let values: [Int: String]
    let active: Int?
    let traced: Set<Int>
    let status: String
    // The rest is the narrated design (docs/ios-design/Simulations iOS.html). A table that leaves it
    // empty still gets the grid; the headline falls back to `status`.
    var reads: Set<Int> = []
    var options: [DpOption] = []
    var chips: [DpChip] = []
    var headline: DpHeadline?
    var body: String?
}

private struct DpConfig {
    let rows: Int
    let cols: Int
    let rowHeader: (Int) -> String
    let colHeader: (Int) -> String
    let corner: String
    let intro: String
    let build: () -> [DpFrame]
    /// Card heading, e.g. "CAT → CUT". A table with one shows it in place of the intro paragraph.
    var title: String?
}

private final class DpBuilder {
    let rows: Int, cols: Int
    var frames: [DpFrame] = []
    var values: [Int: String] = [:]
    init(_ rows: Int, _ cols: Int) { self.rows = rows; self.cols = cols }
    func key(_ r: Int, _ c: Int) -> Int { r * cols + c }
    func fill(_ r: Int, _ c: Int, _ value: String, _ status: String, reads: Set<Int> = [], options: [DpOption] = [],
              chips: [DpChip] = [], headline: DpHeadline? = nil, body: String? = nil) {
        values[key(r, c)] = value
        frames.append(DpFrame(values: values, active: key(r, c), traced: [], status: status, reads: reads,
                              options: options, chips: chips, headline: headline, body: body))
    }
    func trace(_ cells: [Int], _ statusFor: (Int) -> String) {
        var traced = Set<Int>()
        for cell in cells {
            traced.insert(cell)
            frames.append(DpFrame(values: values, active: cell, traced: traced, status: statusFor(cell)))
        }
    }
}

private func chars(_ s: String) -> [String] { s.map { String($0) } }

private func fibonacciDp() -> [DpFrame] {
    let n = 9
    let b = DpBuilder(1, n + 1)
    var dp = [Int](repeating: 0, count: n + 1)
    for i in 0...n {
        dp[i] = i < 2 ? i : dp[i - 1] + dp[i - 2]
        b.fill(0, i, "\(dp[i])", i < 2 ? "dp[\(i)] = \(i)  (base case)" : "dp[\(i)] = dp[\(i - 1)] + dp[\(i - 2)] = \(dp[i])")
    }
    return b.frames
}

private func editDistance() -> [DpFrame] {
    let a = chars("cat"), w = chars("cut")
    let rows = a.count + 1, cols = w.count + 1
    let b = DpBuilder(rows, cols)
    var dp = [[Int]](repeating: [Int](repeating: 0, count: cols), count: rows)
    func edits(_ n: Int) -> String { n == 0 ? "no edits" : n == 1 ? "one edit" : "\(n) edits" }
    for i in 0..<rows {
        for j in 0..<cols {
            let status: String
            let body: String
            var reads: Set<Int> = []
            var options: [DpOption] = []
            var chips = [DpChip(label: "i", value: "\(i)"), DpChip(label: "j", value: "\(j)")]
            if i == 0 && j == 0 {
                dp[i][j] = 0
                status = "dp[0][0] = 0  (two empty strings)"
                body = "Two empty strings already match, so the table starts from zero."
            } else if i == 0 {
                dp[i][j] = j
                status = "dp[0][\(j)] = \(j)  (turn ε into first \(j) chars)"
                reads = [b.key(0, j - 1)]
                body = "Starting from the empty string, every letter has to be inserted — one more than the cell to the left."
            } else if j == 0 {
                dp[i][j] = i
                status = "dp[\(i)][0] = \(i)  (delete \(i) chars)"
                reads = [b.key(i - 1, 0)]
                body = "Reaching the empty string means deleting every letter — one more than the cell above."
            } else {
                let match = a[i - 1] == w[j - 1]
                let cost = match ? 0 : 1
                let diag = dp[i - 1][j - 1] + cost, up = dp[i - 1][j] + 1, left = dp[i][j - 1] + 1
                let v = min(diag, up, left)
                // First minimum in replace → delete → insert order is the one the traceback takes.
                let candidates = [diag, up, left]
                let pick = candidates.firstIndex(of: v)!
                dp[i][j] = v
                options = [
                    DpOption(arrow: "↖", name: match ? "keep" : "replace", formula: "\(dp[i - 1][j - 1]) + \(cost) = \(diag)", chosen: pick == 0),
                    DpOption(arrow: "↑", name: "delete", formula: "\(dp[i - 1][j]) + 1 = \(up)", chosen: pick == 1),
                    DpOption(arrow: "←", name: "insert", formula: "\(dp[i][j - 1]) + 1 = \(left)", chosen: pick == 2),
                ]
                reads = [b.key(i - 1, j - 1), b.key(i - 1, j), b.key(i, j - 1)]
                chips.append(DpChip(label: "\(a[i - 1]) vs \(w[j - 1])", value: match ? "match" : "differ", tone: match ? .good : .alert))
                let ties = candidates.filter { $0 == v }.count
                let move = [match ? "Keeping the letter" : "Replacing", "Deleting", "Inserting"][pick]
                let source = ["the diagonal", "the cell above", "the cell to the left"][pick]
                if match {
                    body = "The letters match, so the diagonal carries over for free. \(move) from \(source) is cheapest."
                    status = "'\(a[i - 1])' == '\(w[j - 1])' → dp[\(i - 1)][\(j - 1)] = \(dp[i - 1][j - 1])"
                } else {
                    body = "The letters differ, so every move costs 1. \(move) from \(source) is \(ties > 1 ? "tied for cheapest." : "cheapest.")"
                    status = "'\(a[i - 1])' ≠ '\(w[j - 1])' → 1 + min(diag, up, left) = \(v)"
                }
            }
            let from = i == 0 ? "\"\"" : "\"\(a[0..<i].joined())\""
            let to = j == 0 ? "\"\"" : "\"\(w[0..<j].joined())\""
            b.fill(i, j, "\(dp[i][j])", status, reads: reads, options: options, chips: chips,
                   headline: DpHeadline(lead: "dp[\(i)][\(j)] = ", emphasis: "\(dp[i][j])", tail: ": turning \(from) into \(to) takes \(edits(dp[i][j]))."),
                   body: body)
        }
    }
    var i = rows - 1, j = cols - 1
    var path = [b.key(i, j)]
    while i > 0 || j > 0 {
        let match = i > 0 && j > 0 && a[i - 1] == w[j - 1]
        if i > 0 && j > 0 && (match || dp[i][j] == dp[i - 1][j - 1] + 1) { i -= 1; j -= 1 }
        else if i > 0 && dp[i][j] == dp[i - 1][j] + 1 { i -= 1 }
        else { j -= 1 }
        path.append(b.key(i, j))
    }
    b.trace(path.reversed()) { _ in "Traceback — the edit path. Distance = \(dp[rows - 1][cols - 1])." }
    return b.frames
}

private func lcs() -> [DpFrame] {
    let a = chars("abcbd"), w = chars("acbd")
    let rows = a.count + 1, cols = w.count + 1
    let b = DpBuilder(rows, cols)
    var dp = [[Int]](repeating: [Int](repeating: 0, count: cols), count: rows)
    func letters(_ n: Int) -> String { n == 1 ? "1 letter" : "\(n) letters" }
    for i in 0..<rows {
        for j in 0..<cols {
            let status: String
            let body: String
            var reads: Set<Int> = []
            var options: [DpOption] = []
            var chips = [DpChip(label: "i", value: "\(i)"), DpChip(label: "j", value: "\(j)")]
            if i == 0 || j == 0 {
                status = "dp[\(i)][\(j)] = 0  (empty prefix)"
                body = "One side is empty, so nothing can be shared."
            } else if a[i - 1] == w[j - 1] {
                dp[i][j] = dp[i - 1][j - 1] + 1
                status = "'\(a[i - 1])' matches → dp[\(i - 1)][\(j - 1)] + 1 = \(dp[i][j])"
                reads = [b.key(i - 1, j - 1)]
                options = [DpOption(arrow: "↖", name: "match", formula: "\(dp[i - 1][j - 1]) + 1 = \(dp[i][j])", chosen: true)]
                chips.append(DpChip(label: "\(a[i - 1]) vs \(w[j - 1])", value: "match", tone: .good))
                body = "'\(a[i - 1])' ends both prefixes, so it joins the best subsequence of what came before them."
            } else {
                let up = dp[i - 1][j], left = dp[i][j - 1]
                dp[i][j] = max(up, left)
                status = "no match → max(up, left) = \(dp[i][j])"
                reads = [b.key(i - 1, j), b.key(i, j - 1)]
                options = [
                    DpOption(arrow: "↑", name: "drop '\(a[i - 1])'", formula: "\(up)", chosen: up >= left),
                    DpOption(arrow: "←", name: "drop '\(w[j - 1])'", formula: "\(left)", chosen: left > up),
                ]
                chips.append(DpChip(label: "\(a[i - 1]) vs \(w[j - 1])", value: "differ", tone: .alert))
                body = "The last letters differ, so one of them is not in the subsequence. Keep whichever drop leaves more."
            }
            b.fill(i, j, "\(dp[i][j])", status, reads: reads, options: options, chips: chips,
                   headline: DpHeadline(lead: "dp[\(i)][\(j)] = ", emphasis: "\(dp[i][j])",
                                        tail: ": \"\(a[0..<i].joined())\" and \"\(w[0..<j].joined())\" share \(letters(dp[i][j])) in order."),
                   body: body)
        }
    }
    var i = rows - 1, j = cols - 1
    var path = [b.key(i, j)]
    while i > 0 && j > 0 {
        if a[i - 1] == w[j - 1] { i -= 1; j -= 1 } else if dp[i - 1][j] >= dp[i][j - 1] { i -= 1 } else { j -= 1 }
        path.append(b.key(i, j))
    }
    b.trace(path.reversed()) { _ in "Traceback — matched characters. LCS length = \(dp[rows - 1][cols - 1])." }
    return b.frames
}

private let coins = [1, 3, 4]
private let coinAmount = 6
private let inf = Int.max / 2
private func coinFmt(_ x: Int) -> String { x >= inf ? "∞" : "\(x)" }

private func coinChange() -> [DpFrame] {
    let rows = coins.count + 1, cols = coinAmount + 1
    let b = DpBuilder(rows, cols)
    var dp = [[Int]](repeating: [Int](repeating: inf, count: cols), count: rows)
    for i in 0..<rows {
        for a in 0..<cols {
            let status: String
            if a == 0 { dp[i][a] = 0; status = "dp[\(i)][0] = 0  (amount 0 needs no coins)" }
            else if i == 0 { dp[i][a] = inf; status = "dp[0][\(a)] = ∞  (no coins available)" }
            else {
                let skip = dp[i - 1][a], coin = coins[i - 1]
                let use = coin <= a && dp[i][a - coin] < inf ? dp[i][a - coin] + 1 : inf
                dp[i][a] = min(skip, use)
                status = "coin \(coin): min(skip \(coinFmt(skip)), use \(coinFmt(use))) = \(coinFmt(dp[i][a]))"
            }
            b.fill(i, a, coinFmt(dp[i][a]), status)
        }
    }
    if dp[rows - 1][cols - 1] < inf {
        var i = rows - 1, a = cols - 1
        var path = [b.key(i, a)]
        while a > 0 && i > 0 {
            if dp[i][a] == dp[i - 1][a] { i -= 1 } else { a -= coins[i - 1] }
            path.append(b.key(i, a))
        }
        b.trace(path.reversed()) { _ in "Traceback — coins used. Minimum = \(dp[rows - 1][cols - 1])." }
    }
    return b.frames
}

private let knapW = [1, 2, 3], knapV = [6, 10, 12], knapCap = 5

private func knapsack() -> [DpFrame] {
    let rows = knapW.count + 1, cols = knapCap + 1
    let b = DpBuilder(rows, cols)
    var dp = [[Int]](repeating: [Int](repeating: 0, count: cols), count: rows)
    for i in 0..<rows {
        for w in 0..<cols {
            let status: String
            if i == 0 || w == 0 { status = "dp[\(i)][\(w)] = 0  (no items or no capacity)" }
            else if knapW[i - 1] <= w {
                let skip = dp[i - 1][w], take = dp[i - 1][w - knapW[i - 1]] + knapV[i - 1]
                dp[i][w] = max(skip, take)
                status = "item \(i) (wt \(knapW[i - 1]), val \(knapV[i - 1])): max(skip \(skip), take \(take)) = \(dp[i][w])"
            } else { dp[i][w] = dp[i - 1][w]; status = "item \(i) too heavy → carry dp[\(i - 1)][\(w)] = \(dp[i - 1][w])" }
            b.fill(i, w, "\(dp[i][w])", status)
        }
    }
    var i = rows - 1, w = cols - 1
    var path = [b.key(i, w)]
    while i > 0 && w >= 0 {
        if dp[i][w] == dp[i - 1][w] { i -= 1 } else { w -= knapW[i - 1]; i -= 1 }
        if w < 0 { break }
        path.append(b.key(i, w))
    }
    b.trace(path.reversed()) { _ in "Traceback — items chosen. Max value = \(dp[rows - 1][cols - 1])." }
    return b.frames
}

private let rodPrices = [1, 5, 8, 9, 10], rodLength = 5

private func rodCutting() -> [DpFrame] {
    let rows = rodPrices.count + 1, cols = rodLength + 1
    let b = DpBuilder(rows, cols)
    var dp = [[Int]](repeating: [Int](repeating: 0, count: cols), count: rows)
    for i in 0..<rows {
        for l in 0..<cols {
            let status: String
            if i == 0 || l == 0 { status = "dp[\(i)][\(l)] = 0  (no piece length, or no rod left)" }
            else if i <= l {
                let skip = dp[i - 1][l], cut = dp[i][l - i] + rodPrices[i - 1]
                dp[i][l] = max(skip, cut)
                status = "length \(i) (price \(rodPrices[i - 1])): max(skip \(skip), cut \(cut)) = \(dp[i][l])"
            } else { dp[i][l] = dp[i - 1][l]; status = "piece \(i) longer than rod \(l) → carry dp[\(i - 1)][\(l)] = \(dp[i - 1][l])" }
            b.fill(i, l, "\(dp[i][l])", status)
        }
    }
    var i = rows - 1, l = cols - 1
    var path = [b.key(i, l)]
    while i > 0 && l > 0 {
        if dp[i][l] == dp[i - 1][l] { i -= 1 } else { l -= i }
        path.append(b.key(i, l))
    }
    b.trace(path.reversed()) { _ in "Traceback — the cuts chosen. Best revenue = \(dp[rows - 1][cols - 1])." }
    return b.frames
}

private let lisInput = [3, 1, 4, 2, 6, 5]

private func lis() -> [DpFrame] {
    let n = lisInput.count
    let b = DpBuilder(1, n)
    var dp = [Int](repeating: 1, count: n)
    for i in 0..<n {
        var best = 1, from = -1
        for j in 0..<i where lisInput[j] < lisInput[i] && dp[j] + 1 > best { best = dp[j] + 1; from = j }
        dp[i] = best
        b.fill(0, i, "\(best)", from < 0 ? "dp[\(i)] = 1  (\(lisInput[i]) starts its own subsequence)" : "dp[\(i)] = dp[\(from)] + 1 = \(best)  (extend the run ending at \(lisInput[from]))")
    }
    return b.frames
}

private let chainDims = [10, 30, 5, 60]

private func matrixChain() -> [DpFrame] {
    let n = chainDims.count - 1
    let b = DpBuilder(n, n)
    var dp = [[Int]](repeating: [Int](repeating: 0, count: n), count: n)
    for i in 0..<n { b.fill(i, i, "0", "dp[\(i)][\(i)] = 0  (a single matrix needs no multiplication)") }
    for len in 2...n {
        for i in 0...(n - len) {
            let j = i + len - 1
            var best = Int.max, split = i
            for k in i..<j {
                let cost = dp[i][k] + dp[k + 1][j] + chainDims[i] * chainDims[k + 1] * chainDims[j + 1]
                if cost < best { best = cost; split = k }
            }
            dp[i][j] = best
            b.fill(i, j, "\(best)", "dp[\(i)][\(j)] = \(best)  (best split after matrix \(split))")
        }
    }
    return b.frames
}

/// Subset-sum table, shared by subset_sum and partition_problem (which differ only in wording).
private func subsetTable(_ items: [Int], _ target: Int, partition: Bool) -> [DpFrame] {
    let b = DpBuilder(items.count + 1, target + 1)
    var dp = [[Bool]](repeating: [Bool](repeating: false, count: target + 1), count: items.count + 1)
    dp[0][0] = true
    b.fill(0, 0, "T", partition
           ? "Total is \(items.reduce(0, +)) — even, so a split is not ruled out. Each half must sum to \(target). dp[0][0] = true: the empty subset makes 0."
           : "dp[0][0] = true: the empty subset sums to 0. Every other total is unreachable with no items.")
    for t in 1...target {
        b.fill(0, t, "·", partition ? "dp[0][\(t)] = false — nothing chosen yet, so \(t) is out of reach." : "dp[0][\(t)] = false — no items, so no way to reach \(t).")
    }
    for i in 1...items.count {
        let item = items[i - 1]
        for t in 0...target {
            let skip = dp[i - 1][t]
            let take = t >= item && dp[i - 1][t - item]
            dp[i][t] = skip || take
            let status: String
            if take && skip { status = partition ? "dp[\(i)][\(t)]: reachable both ways — without the \(item), or by taking it on top of \(t - item)." : "dp[\(i)][\(t)]: reachable either way — skip \(item), or take it and reach \(t - item) first." }
            else if take { status = partition ? "dp[\(i)][\(t)] = true by taking the \(item): \(t - item) was already reachable." : "dp[\(i)][\(t)] = true by taking \(item): dp[\(i - 1)][\(t - item)] was already reachable." }
            else if skip { status = partition ? "dp[\(i)][\(t)] = true without the \(item) — the earlier items already reach \(t)." : "dp[\(i)][\(t)] = true by skipping \(item) — it was reachable without this item." }
            else { status = partition ? "dp[\(i)][\(t)] = false: \(t) is unreachable from the first \(i) item(s)." : "dp[\(i)][\(t)] = false: unreachable with the first \(i) item(s)." }
            b.fill(i, t, dp[i][t] ? "T" : "·", status)
        }
    }
    var chosen: [Int] = [], path: [Int] = []
    var t = target
    for i in stride(from: items.count, through: 1, by: -1) {
        path.append(b.key(i, t))
        if !dp[i - 1][t] { chosen.append(items[i - 1]); t -= items[i - 1] }
    }
    path.append(b.key(0, t))
    let picked = Array(chosen.reversed())
    var rest = items
    for p in picked { if let idx = rest.firstIndex(of: p) { rest.remove(at: idx) } }
    b.trace(path) { cell in
        let r = cell / (target + 1), c = cell % (target + 1)
        if r == 0 {
            return partition
                ? "Split found: {\(picked.map(String.init).joined(separator: ", "))} = \(target) and {\(rest.map(String.init).joined(separator: ", "))} = \(target). The table is O(n·T/2) cells — pseudo-polynomial, which is why this stays NP-complete."
                : "Back at dp[0][0] — the subset is complete: \(picked.map(String.init).joined(separator: " + ")) = \(target)."
        }
        if partition {
            return "At dp[\(r)][\(c)]: " + (!dp[r - 1][c] ? "unreachable without the \(items[r - 1]), so it goes in the first half." : "still reachable without the \(items[r - 1]), so it goes in the second half.")
        }
        return "At dp[\(r)][\(c)]: " + (!dp[r - 1][c] ? "this total was only reachable by taking \(items[r - 1]), so it is in the subset." : "reachable without item \(items[r - 1]), so skip it and move up.")
    }
    return b.frames
}

private let tspMasks = (0..<16).filter { $0 & 1 == 1 }
private func tspMaskLabel(_ row: Int) -> String {
    let mask = tspMasks[row]
    return "{" + (0..<4).filter { mask & (1 << $0) != 0 }.map(String.init).joined(separator: ",") + "}"
}

private func bitmaskDp() -> [DpFrame] {
    let n = 4
    let cost = [[0, 10, 15, 20], [10, 0, 35, 25], [15, 35, 0, 30], [20, 25, 30, 0]]
    let full = (1 << n) - 1
    let b = DpBuilder(tspMasks.count, n)
    var dp = [[Int]](repeating: [Int](repeating: inf, count: n), count: 1 << n)
    var from = [[Int]](repeating: [Int](repeating: -1, count: n), count: 1 << n)
    func row(_ m: Int) -> Int { tspMasks.firstIndex(of: m)! }
    dp[1][0] = 0
    b.fill(row(1), 0, "0", "Start at city 0 with only city 0 visited: dp[{0}][0] = 0. Every tour begins here.")
    for mask in tspMasks {
        for last in 1..<n where mask & (1 << last) != 0 {
            let without = mask & ~(1 << last)
            var best = inf, bestPrev = -1
            for prev in 0..<n where without & (1 << prev) != 0 && dp[without][prev] < inf {
                let c = dp[without][prev] + cost[prev][last]
                if c < best { best = c; bestPrev = prev }
            }
            if best >= inf { continue }
            dp[mask][last] = best
            from[mask][last] = bestPrev
            b.fill(row(mask), last, "\(best)", "dp[\(tspMaskLabel(row(mask)))][\(last)] = dp[\(tspMaskLabel(row(without)))][\(bestPrev)] + cost[\(bestPrev)][\(last)] = \(dp[without][bestPrev]) + \(cost[bestPrev][last]) = \(best). The subset is one bit larger than the row it read from.")
        }
    }
    var bestLast = 1, bestTotal = inf
    for last in 1..<n {
        let total = dp[full][last] + cost[last][0]
        if total < bestTotal { bestTotal = total; bestLast = last }
    }
    var tour: [Int] = [], traced: [Int] = []
    var mask = full, last = bestLast
    while last != -1 && mask != 0 {
        traced.append(b.key(row(mask), last))
        tour.append(last)
        let prev = from[mask][last]
        mask &= ~(1 << last)
        last = prev
        if last == 0 { traced.append(b.key(row(mask), 0)); tour.append(0); break }
    }
    tour.reverse(); traced.reverse()
    let tourText = (tour + [0]).map(String.init).joined(separator: " → ")
    b.trace(traced) { cell in
        "Traceback: dp[\(tspMaskLabel(cell / 4))][\(cell % 4)]. Closing the tour costs cost[\(bestLast)][0] = \(cost[bestLast][0]), giving \(tourText) for a total of \(bestTotal) — found by filling 20 cells instead of enumerating 6 tours, a gap that becomes 2ⁿ·n² vs n! as n grows."
    }
    return b.frames
}

private let lcsubA = chars("abcdxy"), lcsubB = chars("zabcdw")

private func longestCommonSubstring() -> [DpFrame] {
    let rows = lcsubA.count + 1, cols = lcsubB.count + 1
    let b = DpBuilder(rows, cols)
    var dp = [[Int]](repeating: [Int](repeating: 0, count: cols), count: rows)
    var best = 0, endI = 0, endJ = 0
    for i in 0..<rows {
        for j in 0..<cols {
            let status: String
            if i == 0 || j == 0 { status = "dp[\(i)][\(j)] = 0 — an empty prefix shares no suffix with anything." }
            else if lcsubA[i - 1] == lcsubB[j - 1] {
                dp[i][j] = dp[i - 1][j - 1] + 1
                status = "'\(lcsubA[i - 1])' == '\(lcsubB[j - 1])' → dp[\(i - 1)][\(j - 1)] + 1 = \(dp[i][j]). The run grows by one."
                if dp[i][j] > best { best = dp[i][j]; endI = i; endJ = j }
            } else {
                status = "'\(lcsubA[i - 1])' ≠ '\(lcsubB[j - 1])' → 0. Not max(up, left) — that would be LCS, and it would let the run survive a gap. Zero here is what makes the answer contiguous."
            }
            b.fill(i, j, "\(dp[i][j])", status)
        }
    }
    var path: [Int] = []
    var i = endI, j = endJ
    for _ in 0..<best { path.append(b.key(i, j)); i -= 1; j -= 1 }
    let run = lcsubA[(endI - best)..<endI].joined()
    b.trace(path) { cell in
        let r = cell / cols, c = cell % cols
        return dp[r][c] == 1
            ? "Back to the start of the run: \"\(run)\", length \(best). The maximum was at dp[\(endI)][\(endJ)], not at dp[\(rows - 1)][\(cols - 1)] — the corner only ever holds the common suffix."
            : "dp[\(r)][\(c)] = \(dp[r][c]), so '\(lcsubA[r - 1])' is part of the run; step diagonally back."
    }
    return b.frames
}

private let knapItems = [(2, 3), (3, 4), (4, 5), (5, 6)]
private let knapPatternCap = 5

private func knapsackPattern() -> [DpFrame] {
    let rows = knapItems.count + 1, cols = knapPatternCap + 1
    let b = DpBuilder(rows, cols)
    var dp = [[Int]](repeating: [Int](repeating: 0, count: cols), count: rows)
    for i in 0..<rows {
        for c in 0..<cols {
            let status: String
            if i == 0 { status = "dp[0][\(c)] = 0 — no items considered yet, so no value at any capacity." }
            else {
                let (weight, value) = knapItems[i - 1]
                let skip = dp[i - 1][c]
                if weight > c {
                    dp[i][c] = skip
                    status = "Item \(i) weighs \(weight), more than capacity \(c) — the take branch is illegal, so dp[\(i)][\(c)] = dp[\(i - 1)][\(c)] = \(skip)."
                } else {
                    let take = value + dp[i - 1][c - weight]
                    dp[i][c] = max(skip, take)
                    status = "Item \(i) (w\(weight), v\(value)) at capacity \(c): skip = \(skip), take = \(value) + dp[\(i - 1)][\(c - weight)] = \(take). " + (take > skip ? "Taking wins." : "Skipping wins — the capacity it costs is worth more elsewhere.")
                }
            }
            b.fill(i, c, "\(dp[i][c])", status)
        }
    }
    var i = knapItems.count, c = knapPatternCap
    var path = [b.key(i, c)]
    var chosen: [Int] = []
    while i > 0 {
        if dp[i][c] == dp[i - 1][c] { i -= 1 } else { chosen.append(i); c -= knapItems[i - 1].0; i -= 1 }
        path.append(b.key(i, c))
    }
    b.trace(path) { _ in "Traceback: a cell equal to the one above it means that item was skipped; a drop of its weight means it was taken. Items \(chosen.reversed().map(String.init).joined(separator: " and ")) give value \(dp[knapItems.count][knapPatternCap])." }
    return b.frames
}

private let gridCosts = [[1, 3, 1, 2], [1, 5, 1, 3], [4, 2, 1, 1]]

private func gridPattern() -> [DpFrame] {
    let rows = gridCosts.count, cols = gridCosts[0].count
    let b = DpBuilder(rows, cols)
    var dp = [[Int]](repeating: [Int](repeating: 0, count: cols), count: rows)
    for r in 0..<rows {
        for c in 0..<cols {
            let cost = gridCosts[r][c]
            let status: String
            if r == 0 && c == 0 { dp[r][c] = cost; status = "dp[0][0] = \(cost) — the start cell is its own cost, nothing to choose." }
            else if r == 0 { dp[r][c] = dp[0][c - 1] + cost; status = "Top row: the only way in is from the left, so dp[0][\(c)] = dp[0][\(c - 1)] + \(cost) = \(dp[r][c]). No max or min appears until a cell has two ways in." }
            else if c == 0 { dp[r][c] = dp[r - 1][0] + cost; status = "Left column: only reachable from above — dp[\(r)][0] = dp[\(r - 1)][0] + \(cost) = \(dp[r][c])." }
            else {
                let up = dp[r - 1][c], left = dp[r][c - 1]
                dp[r][c] = cost + min(up, left)
                status = "dp[\(r)][\(c)] = \(cost) + min(up \(up), left \(left)) = \(dp[r][c]). Both dependencies are already written, which is the whole reason for sweeping rows left to right."
            }
            b.fill(r, c, "\(dp[r][c])", status)
        }
    }
    var r = rows - 1, c = cols - 1
    var path = [b.key(r, c)]
    while r > 0 || c > 0 {
        if r == 0 { c -= 1 } else if c == 0 { r -= 1 } else if dp[r - 1][c] <= dp[r][c - 1] { r -= 1 } else { c -= 1 }
        path.append(b.key(r, c))
    }
    b.trace(path.reversed()) { _ in "Traceback from the corner: at each step, the neighbour whose value the cell was built from. Cheapest path costs \(dp[rows - 1][cols - 1])." }
    return b.frames
}

private let balloonNums = [3, 1, 5, 8]
private let balloons = [1] + balloonNums + [1]

private func intervalPattern() -> [DpFrame] {
    let n = balloons.count
    let b = DpBuilder(n, n)
    var dp = [[Int]](repeating: [Int](repeating: 0, count: n), count: n)
    b.fill(0, 0, "0", "Burst balloons: dp[i][j] is the best score from the balloons strictly *between* i and j, with i and j still standing. Ranges of width 1 hold nothing, so they score 0.")
    for len in 2..<n {
        for i in 0...(n - 1 - len) {
            let j = i + len
            var best = 0, bestK = i + 1
            for k in (i + 1)..<j {
                let cand = dp[i][k] + dp[k][j] + balloons[i] * balloons[k] * balloons[j]
                if cand > best { best = cand; bestK = k }
            }
            dp[i][j] = best
            b.fill(i, j, "\(best)", "Range (\(i), \(j)), width \(len): the balloon that bursts *last* is \(bestK). Its neighbours are then i and j themselves — \(balloons[i]) × \(balloons[bestK]) × \(balloons[j]) = \(balloons[i] * balloons[bestK] * balloons[j]) — plus the two sub-ranges dp[\(i)][\(bestK)] = \(dp[i][bestK]) and dp[\(bestK)][\(j)] = \(dp[bestK][j]), both already filled because they are shorter. Total \(best).")
        }
    }
    b.fill(0, n - 1, "\(dp[0][n - 1])", "dp[0][\(n - 1)] = \(dp[0][n - 1]) over the whole padded array. Choosing what bursts *first* would leave two halves whose neighbours are not yet known — choosing what bursts *last* is what makes the split independent. Filling by length, shortest first, is the only order that has the sub-ranges ready.")
    return b.frames
}

private let stockPrices = [1, 2, 3, 0, 2]
private let stockModes = ["hold", "sold", "rest"]

private func stateMachinePattern() -> [DpFrame] {
    let days = stockPrices.count
    let b = DpBuilder(stockModes.count, days)
    var hold = [Int](repeating: 0, count: days), sold = hold, rest = hold
    hold[0] = -stockPrices[0]
    b.fill(0, 0, "\(hold[0])", "Three modes after each day: holding a share, having just sold (which forces tomorrow's cooldown), or resting free to buy. Day 0 holding means having bought at \(stockPrices[0]), so the balance is \(hold[0]).")
    b.fill(1, 0, "0", "Selling on day 0 is meaningless with nothing held — 0.")
    b.fill(2, 0, "0", "Resting on day 0 costs nothing — 0. Every later cell is one of these three plus a move.")
    for d in 1..<days {
        let price = stockPrices[d]
        hold[d] = max(hold[d - 1], rest[d - 1] - price)
        b.fill(0, d, "\(hold[d])", "Day \(d), price \(price). hold = max(keep holding \(hold[d - 1]), buy today from rest \(rest[d - 1]) − \(price) = \(rest[d - 1] - price)) = \(hold[d]). Buying is only legal from *rest* — that edge is the cooldown rule, and it is the entire difference from the unrestricted version.")
        sold[d] = hold[d - 1] + price
        b.fill(1, d, "\(sold[d])", "sold = hold(yesterday) + \(price) = \(hold[d - 1]) + \(price) = \(sold[d]). There is no choice here: the only way to be in *sold* is to have been holding and sold today.")
        rest[d] = max(rest[d - 1], sold[d - 1])
        b.fill(2, d, "\(rest[d])", "rest = max(stay resting \(rest[d - 1]), yesterday's sold \(sold[d - 1])) = \(rest[d]). Coming from sold is what serves the cooldown day.")
    }
    let answer = max(sold[days - 1], rest[days - 1])
    b.trace([b.key(1, days - 1), b.key(2, days - 1)]) { _ in "Answer = max(sold, rest) on the last day = \(answer) — never *hold*, since ending with an unsold share is money left on the table. Three modes × \(days) days, each cell O(1): O(n) time, and rolling each row to a scalar makes it O(1) space." }
    return b.frames
}

private let prefixMatrix = [[3, 0, 1, 4], [5, 6, 3, 2], [1, 2, 0, 1]]

private func prefix2dPattern() -> [DpFrame] {
    let rows = prefixMatrix.count + 1, cols = prefixMatrix[0].count + 1
    let b = DpBuilder(rows, cols)
    var p = [[Int]](repeating: [Int](repeating: 0, count: cols), count: rows)
    for i in 0..<rows {
        for j in 0..<cols {
            if i == 0 || j == 0 {
                b.fill(i, j, "0", "The border stays 0. It is not part of the matrix — it exists so the recurrence below never has to test whether a neighbour is off the edge.")
                continue
            }
            let v = prefixMatrix[i - 1][j - 1]
            p[i][j] = v + p[i - 1][j] + p[i][j - 1] - p[i - 1][j - 1]
            b.fill(i, j, "\(p[i][j])", "P[\(i)][\(j)] = the whole rectangle from the origin to here: cell \(v) + above \(p[i - 1][j]) + left \(p[i][j - 1]) − corner \(p[i - 1][j - 1]) = \(p[i][j]). The corner is subtracted because the strip above and the strip to the left both already contain it.")
        }
    }
    let r1 = 2, c1 = 2, r2 = 3, c2 = 4
    let total = p[r2][c2] - p[r1 - 1][c2] - p[r2][c1 - 1] + p[r1 - 1][c1 - 1]
    b.trace([b.key(r2, c2), b.key(r1 - 1, c2), b.key(r2, c1 - 1), b.key(r1 - 1, c1 - 1)]) { cell in
        switch cell {
        case b.key(r2, c2): "Query the submatrix rows 1–2, cols 1–3. Start with the big rectangle P[\(r2)][\(c2)] = \(p[r2][c2])."
        case b.key(r1 - 1, c2): "Subtract the strip above it, P[\(r1 - 1)][\(c2)] = \(p[r1 - 1][c2])."
        case b.key(r2, c1 - 1): "Subtract the strip to its left, P[\(r2)][\(c1 - 1)] = \(p[r2][c1 - 1])."
        default: "Add back the corner P[\(r1 - 1)][\(c1 - 1)] = \(p[r1 - 1][c1 - 1]), subtracted twice. Sum = \(total) — four lookups, and the size of the rectangle never entered the cost."
        }
    }
    return b.frames
}

private let rotateMatrix = [[1, 2, 3, 4], [5, 6, 7, 8], [9, 10, 11, 12], [13, 14, 15, 16]]

private func matrixTransformPattern() -> [DpFrame] {
    let n = rotateMatrix.count
    let b = DpBuilder(n, n)
    var m = rotateMatrix
    for r in 0..<n { for c in 0..<n { b.values[b.key(r, c)] = "\(m[r][c])" } }
    b.fill(0, 0, "\(m[0][0])", "Rotate this \(n)×\(n) matrix 90° clockwise in place. Element (r, c) must end up at (c, \(n - 1)−r) — one mapping, applied as two simpler ones rather than juggled at once.")
    for r in 0..<n {
        for c in (r + 1)..<n {
            let a = m[r][c], bb = m[c][r]
            m[r][c] = bb; m[c][r] = a
            b.values[b.key(r, c)] = "\(bb)"
            b.fill(c, r, "\(a)", "Transpose step: swap (\(r), \(c)) with (\(c), \(r)) — \(a) and \(bb) trade places. Only the upper triangle is iterated; running over the whole matrix swaps every pair twice and leaves it unchanged.")
        }
    }
    for r in 0..<n {
        var lo = 0, hi = n - 1
        while lo < hi {
            let a = m[r][lo], bb = m[r][hi]
            m[r][lo] = bb; m[r][hi] = a
            b.values[b.key(r, lo)] = "\(bb)"
            b.fill(r, hi, "\(a)", "Reverse row \(r): swap columns \(lo) and \(hi). After the transpose, the columns are in the right order but backwards — reversing each row finishes the rotation.")
            lo += 1; hi -= 1
        }
    }
    b.trace((0..<n).map { b.key(0, $0) }) { _ in "Top row is now \(m[0].map(String.init).joined(separator: ", ")) — the old first *column*, bottom to top. Transpose then reverse, O(n²) reads and writes, no second matrix. Anti-clockwise is the same two steps with the reversal applied to columns instead." }
    return b.frames
}

private let partitionItems = [1, 5, 11, 5]
private let subsetItems = [3, 4, 5, 2]

private let dpConfigs: [String: DpConfig] = [
    "prefix_2d_pattern": DpConfig(rows: 4, cols: 5, rowHeader: { $0 == 0 ? "0" : "r\($0 - 1)" }, colHeader: { $0 == 0 ? "0" : "c\($0 - 1)" }, corner: "P",
                                  intro: "A 2D prefix table over a 3×4 matrix, then one submatrix query answered by the four highlighted lookups — big rectangle, two strips, and the corner added back.", build: prefix2dPattern),
    "matrix_transform_pattern": DpConfig(rows: 4, cols: 4, rowHeader: { "r\($0)" }, colHeader: { "c\($0)" }, corner: "",
                                         intro: "Rotating a 4×4 matrix 90° clockwise in place, as transpose-then-reverse. The cells change under the same coordinates, which is what \"in place\" costs you in readability.", build: matrixTransformPattern),
    "knapsack_dp_pattern": DpConfig(rows: knapItems.count + 1, cols: knapPatternCap + 1, rowHeader: { $0 == 0 ? "ε" : "w\(knapItems[$0 - 1].0)·v\(knapItems[$0 - 1].1)" }, colHeader: { "\($0)" }, corner: "cap",
                                    intro: "0/1 knapsack, capacity \(knapPatternCap). Every cell is one skip-or-take decision, and the traceback reads the chosen items back out of the table.", build: knapsackPattern),
    "grid_dp_pattern": DpConfig(rows: 3, cols: 4, rowHeader: { "r\($0)" }, colHeader: { "c\($0)" }, corner: "min",
                                intro: "Minimum path sum through a 3×4 grid, moving only right or down. The first row and column have one way in; every other cell picks the cheaper of two.", build: gridPattern),
    "interval_dp_pattern": DpConfig(rows: balloons.count, cols: balloons.count, rowHeader: { "\(balloons[$0])" }, colHeader: { "\(balloons[$0])" }, corner: "i\\j",
                                    intro: "Burst balloons \(balloonNums.map(String.init).joined(separator: ", ")), padded with 1s. Only the upper triangle fills, and it fills by range length — every range needs the shorter ones inside it first.", build: intervalPattern),
    "state_machine_dp_pattern": DpConfig(rows: 3, cols: stockPrices.count, rowHeader: { stockModes[$0] }, colHeader: { "\(stockPrices[$0])" }, corner: "mode",
                                         intro: "Stock trading with a cooldown, prices \(stockPrices.map(String.init).joined(separator: ", ")). The rows are modes rather than positions — the table *is* the state machine, one column per day.", build: stateMachinePattern),
    "fibonacci_dp": DpConfig(rows: 1, cols: 10, rowHeader: { _ in "dp" }, colHeader: { "\($0)" }, corner: "i",
                             intro: "Bottom-up Fibonacci: each cell is the sum of the two before it — computed once, left to right. No recursion, no repeated work.", build: fibonacciDp),
    "edit_distance": DpConfig(rows: 4, cols: 4, rowHeader: { $0 == 0 ? "ε" : chars("cat")[$0 - 1] }, colHeader: { $0 == 0 ? "ε" : chars("cut")[$0 - 1] }, corner: "",
                              intro: "Edit distance between \"cat\" and \"cut\". Each cell is the cheapest way to turn one prefix into the other; the traceback shows the actual edits.", build: editDistance, title: "CAT → CUT"),
    "longest_common_subsequence": DpConfig(rows: 6, cols: 5, rowHeader: { $0 == 0 ? "ε" : chars("abcbd")[$0 - 1] }, colHeader: { $0 == 0 ? "ε" : chars("acbd")[$0 - 1] }, corner: "",
                                           intro: "Longest common subsequence of \"abcbd\" and \"acbd\". On a match the diagonal grows; otherwise carry the best neighbour. Traceback recovers the subsequence.", build: lcs, title: "ABCBD · ACBD"),
    "coin_change": DpConfig(rows: coins.count + 1, cols: coinAmount + 1, rowHeader: { $0 == 0 ? "ε" : "\(coins[$0 - 1])" }, colHeader: { "\($0)" }, corner: "¢",
                            intro: "Fewest coins to make each amount, using denominations {1, 3, 4}. Each row adds a coin type; ∞ means unreachable. Traceback shows which coins make the target.", build: coinChange),
    "rod_cutting": DpConfig(rows: rodPrices.count + 1, cols: rodLength + 1, rowHeader: { $0 == 0 ? "ε" : "\($0)" }, colHeader: { "\($0)" }, corner: "len",
                            intro: "Rod cutting — prices (1,5,8,9,10) for lengths 1..5. Each row allows one more piece length; the traceback shows which cuts produce the best revenue.", build: rodCutting),
    "longest_increasing_subsequence": DpConfig(rows: 1, cols: lisInput.count, rowHeader: { _ in "dp" }, colHeader: { "\(lisInput[$0])" }, corner: "a[i]",
                                               intro: "Longest increasing subsequence of (3,1,4,2,6,5). Each cell is the longest run ending at that element — the answer is the largest cell, not the last one.", build: lis),
    "matrix_chain_multiplication": DpConfig(rows: 3, cols: 3, rowHeader: { "A\($0 + 1)" }, colHeader: { "A\($0 + 1)" }, corner: "i\\j",
                                            intro: "Matrix chain with dimensions 10×30, 30×5, 5×60. The table fills along diagonals — by chain length — so every sub-chain is solved before the chains that contain it. Cells below the diagonal stay empty.", build: matrixChain),
    "knapsack_01": DpConfig(rows: knapW.count + 1, cols: knapCap + 1, rowHeader: { $0 == 0 ? "ε" : "\(knapW[$0 - 1])" }, colHeader: { "\($0)" }, corner: "wt",
                            intro: "0/1 knapsack — items (wt, val) = (1,6), (2,10), (3,12), capacity 5. Each cell is the best value achievable; the traceback marks the items chosen.", build: knapsack),
    "bitmask_dp": DpConfig(rows: 8, cols: 4, rowHeader: tspMaskLabel, colHeader: { "at \($0)" }, corner: "visited",
                           intro: "Held-Karp TSP over four cities. A row is a subset of visited cities encoded as a bitmask, a column is the city you are standing on — 2ⁿ·n states instead of n! tours.", build: bitmaskDp),
    "subset_sum": DpConfig(rows: 5, cols: 10, rowHeader: { $0 == 0 ? "ε" : "\(subsetItems[$0 - 1])" }, colHeader: { "\($0)" }, corner: "item",
                           intro: "Can any subset of {3, 4, 5, 2} total exactly 9? Each cell is a yes/no rather than a number, and the traceback recovers which items were actually chosen.", build: { subsetTable(subsetItems, 9, partition: false) }),
    "partition_problem": DpConfig(rows: partitionItems.count + 1, cols: 12, rowHeader: { $0 == 0 ? "ε" : "\(partitionItems[$0 - 1])" }, colHeader: { "\($0)" }, corner: "item",
                                  intro: "Can {1, 5, 11, 5} be split into two equal halves? The total is 22, so the question becomes whether any subset reaches exactly 11 — subset-sum with the target derived from the input rather than given.", build: { subsetTable(partitionItems, 11, partition: true) }),
    "longest_common_substring": DpConfig(rows: lcsubA.count + 1, cols: lcsubB.count + 1, rowHeader: { $0 == 0 ? "ε" : lcsubA[$0 - 1] }, colHeader: { $0 == 0 ? "ε" : lcsubB[$0 - 1] }, corner: "",
                                         intro: "Longest common substring of \"abcdxy\" and \"zabcdw\". A cell is the longest common *suffix* of the two prefixes, so a mismatch resets it to zero — and the answer is the largest cell anywhere, not the corner.", build: longestCommonSubstring),
]

// Built to docs/ios-design/Simulations iOS.html (Edit Distance): the table in a card with the cell
// being written in yellow and the cells it reads in solid blue, the recurrence's candidates as cards
// under it, then i/j chips, a headline with the new value, and a sentence on why that candidate won.

/// Dark text on the yellow current cell: white on #F5C542 is unreadable.
private let onActiveCell = Color(hex: 0x1F1A0A)

struct DpGridLab: View {
    private let config: DpConfig
    private let frames: [DpFrame]
    private let hasTraceback: Bool
    private let hasReads: Bool
    @State private var playback: PlaybackState
    @Environment(\.palette) private var palette
    @Environment(\.colorScheme) private var scheme

    init(topicId: String) {
        config = dpConfigs[topicId] ?? dpConfigs["fibonacci_dp"]!
        frames = config.build()
        // Not every table has a traceback pass, nor names the cells it reads, so the legend follows
        // what the frames actually contain.
        hasTraceback = frames.contains { !$0.traced.isEmpty }
        hasReads = frames.contains { !$0.reads.isEmpty }
        _playback = State(initialValue: PlaybackState(stepCount: frames.count))
    }

    /// The mock's yellow is for a dark card; on white it needs to be darker to stay legible as text.
    private var highlight: Color { scheme == .dark ? SimColors.active : Color(hex: 0xB7791F) }

    var body: some View {
        let frame = frames[min(playback.index, frames.count - 1)]
        VStack(alignment: .leading, spacing: 0) {
            if config.title == nil {
                LabIntro(text: config.intro).padding(.bottom, 12)
            }
            LabCard {
                HStack {
                    Text(config.title ?? "").font(AppFont.sans(13, .semibold)).tracking(0.8).foregroundStyle(palette.muted)
                    Spacer()
                    Text(config.rows == 1 ? "dp[i]" : "dp[i][j]").font(.system(size: 13, design: .monospaced)).foregroundStyle(palette.muted)
                }
                DpGridTable(config: config, frame: frame, highlight: highlight).padding(.top, 12)
                if !frame.options.isEmpty {
                    HStack(spacing: 8) {
                        ForEach(frame.options.indices, id: \.self) { OptionCard(option: frame.options[$0], highlight: highlight) }
                    }
                    .padding(.top, 14)
                }
                FlowLayout(spacing: 16, lineSpacing: 6) {
                    DpSwatch(color: SimColors.active, label: "Current")
                    if hasReads { DpSwatch(color: SimColors.blue, label: "Reads from") }
                    DpSwatch(color: SimColors.blue.opacity(0.22), label: "Filled", border: SimColors.blue.opacity(0.55))
                    if hasTraceback { DpSwatch(color: SimColors.green, label: "Traceback") }
                }
                .padding(.top, 14)
            }
            let chips = chipsFor(frame)
            if !chips.isEmpty {
                FlowLayout(spacing: 8, lineSpacing: 8) {
                    ForEach(chips.indices, id: \.self) { DpStepChip(chip: chips[$0]) }
                }
                .padding(.top, 16)
            }
            if let headline = frame.headline {
                (Text(headline.lead) + Text(headline.emphasis).foregroundColor(highlight) + Text(headline.tail))
                    .font(AppFont.sans(20, .bold))
                    .foregroundStyle(palette.onSurface)
                    .padding(.top, 16)
            } else {
                // Tables without narration show their one-line status as the headline, a size down —
                // several run to two sentences.
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

    private func chipsFor(_ frame: DpFrame) -> [DpChip] {
        if !frame.chips.isEmpty { return frame.chips }
        guard let key = frame.active else { return [] }
        if config.rows == 1 { return [DpChip(label: "i", value: "\(key % config.cols)")] }
        return [DpChip(label: "i", value: "\(key / config.cols)"), DpChip(label: "j", value: "\(key % config.cols)")]
    }
}

private struct DpGridTable: View {
    let config: DpConfig
    let frame: DpFrame
    let highlight: Color
    @Environment(\.palette) private var palette
    @Environment(\.colorScheme) private var scheme

    // The mock's roomy 4×4 squares only fit small tables; wider ones step down so ten columns still
    // fit a phone.
    private var roomy: Bool { config.cols <= 5 }
    private var cellHeight: CGFloat { roomy ? 48 : config.cols <= 7 ? 40 : 34 }
    private var gap: CGFloat { roomy ? 8 : 4 }
    private var fontSize: CGFloat { roomy ? 16 : config.cols <= 7 ? 14 : 12 }
    /// Single letters (ε, c, a) get a narrow gutter; longer labels (w2·v3, hold) get room for themselves.
    private var headerWidth: CGFloat {
        let longest = (0..<config.rows).map { config.rowHeader($0).count }.max() ?? 1
        return longest <= 2 ? 28 : min(CGFloat(longest * 7 + 8), 64)
    }

    var body: some View {
        let activeRow = frame.active.map { $0 / config.cols }
        let activeCol = frame.active.map { $0 % config.cols }
        VStack(spacing: gap) {
            HStack(spacing: gap) {
                label(config.corner, active: false).frame(width: headerWidth)
                ForEach(0..<config.cols, id: \.self) { c in
                    label(config.colHeader(c), active: c == activeCol).frame(maxWidth: .infinity)
                }
            }
            ForEach(0..<config.rows, id: \.self) { r in
                HStack(spacing: gap) {
                    label(config.rowHeader(r), active: r == activeRow).frame(width: headerWidth)
                    ForEach(0..<config.cols, id: \.self) { c in cell(r * config.cols + c) }
                }
            }
        }
    }

    /// Row and column labels; the current cell's row and column light up in the active yellow.
    private func label(_ text: String, active: Bool) -> some View {
        Text(text)
            .font(.system(size: 14, weight: active ? .bold : .medium, design: .monospaced))
            .foregroundStyle(active ? highlight : palette.muted)
            .lineLimit(1).minimumScaleFactor(0.6)
            .frame(height: 24)
    }

    private func cell(_ key: Int) -> some View {
        let value = frame.values[key]
        let fill: Color
        let border: Color
        let text: Color
        if key == frame.active {
            fill = SimColors.active; border = .clear; text = onActiveCell
        } else if frame.traced.contains(key) {
            fill = SimColors.green; border = .clear; text = .white
        } else if frame.reads.contains(key) {
            fill = SimColors.blue; border = .clear; text = .white
        } else if value != nil {
            fill = SimColors.blue.opacity(scheme == .dark ? 0.22 : 0.14); border = SimColors.blue.opacity(0.55); text = palette.onSurface
        } else {
            fill = palette.muted.opacity(0.12); border = .clear; text = palette.onSurface
        }
        let shape = RoundedRectangle(cornerRadius: roomy ? 10 : 6)
        return Text(value ?? "")
            .font(.system(size: fontSize, weight: .bold, design: .monospaced))
            .foregroundStyle(text)
            .lineLimit(1).minimumScaleFactor(0.5)
            .frame(maxWidth: .infinity).frame(height: cellHeight)
            .background(fill, in: shape)
            .overlay(shape.strokeBorder(border, lineWidth: 1.5))
    }
}

private struct OptionCard: View {
    let option: DpOption
    let highlight: Color
    @Environment(\.palette) private var palette

    var body: some View {
        let shape = RoundedRectangle(cornerRadius: 10)
        VStack(alignment: .leading, spacing: 4) {
            Text("\(option.arrow) \(option.name)")
                .font(AppFont.sans(13, .semibold))
                .foregroundStyle(option.chosen ? highlight : palette.muted)
                .lineLimit(1)
            Text(option.formula)
                .font(.system(size: 15, weight: .bold, design: .monospaced))
                .foregroundStyle(palette.onSurface)
                .lineLimit(1).minimumScaleFactor(0.7)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, 10)
        .padding(.vertical, 8)
        .background(option.chosen ? SimColors.active.opacity(0.12) : palette.muted.opacity(0.12), in: shape)
        .overlay(shape.strokeBorder(option.chosen ? SimColors.active : .clear, lineWidth: 1.5))
    }
}

private struct DpStepChip: View {
    let chip: DpChip
    @Environment(\.palette) private var palette

    var body: some View {
        let background: Color = switch chip.tone {
        case .alert: SimColors.red.opacity(0.18)
        case .good: SimColors.green.opacity(0.18)
        case .neutral: palette.muted.opacity(0.12)
        }
        HStack(spacing: 8) {
            Text(chip.label).foregroundStyle(palette.muted)
            Text(chip.value).fontWeight(.bold).foregroundStyle(palette.onSurface)
        }
        .font(.system(size: 15, design: .monospaced))
        .padding(.horizontal, 12)
        .padding(.vertical, 8)
        .background(background, in: RoundedRectangle(cornerRadius: 10))
    }
}

private struct DpSwatch: View {
    let color: Color
    let label: String
    var border: Color?
    @Environment(\.palette) private var palette

    var body: some View {
        HStack(spacing: 7) {
            RoundedRectangle(cornerRadius: 3)
                .fill(color)
                .overlay { if let border { RoundedRectangle(cornerRadius: 3).strokeBorder(border, lineWidth: 1) } }
                .frame(width: 11, height: 11)
            Text(label).font(AppFont.sans(14)).foregroundStyle(palette.muted)
        }
    }
}
