import SwiftUI

// Port of DpGridSection.kt: fills a DP table cell by cell, then walks the traceback path.

/// One candidate in a cell's recurrence — edit distance's replace/delete/insert — shown as a card under
/// the grid, the chosen one outlined.
private struct DpOption { let arrow: String; let name: String; let formula: String; let chosen: Bool }

private struct DpFrame {
    let values: [Int: String]
    let active: Int?
    let traced: Set<Int>
    let status: String
    // The rest is the narrated design (docs mocks). A table that leaves it empty still gets the grid;
    // the headline falls back to `status`.
    var reads: Set<Int> = []
    var options: [DpOption] = []
    var chips: [StoryChip] = []
    /// `{…}` marks the term drawn in its tone's colour (see LabStory).
    var headline: String?
    var body: String?
    /// The recurrence with this cell's numbers, in a strip under the grid.
    var formula: String?
    /// Labelled lines of arithmetic under the grid: matrix chain's splits, Fibonacci's call count.
    var formulaRows: [StoryFormulaRow] = []
    /// Cells holding the answer, in violet.
    var answer: Set<Int> = []
    /// Tiles under the grid spelling out the result (LCS's letters).
    var strip: [StoryCell] = []
    /// Replaces the config's header note for this step ("d(1,3) = 25").
    var note: String?
    /// Several cells written in one step (matrix chain's diagonal of zeros).
    var actives: Set<Int> = []
}

private let dpLegend: [(StoryTone, String)] = [(.active, "Current"), (.path, "Reads from"), (.done, "Traceback"), (.answer, "Answer")]

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
    /// Right of the heading; defaults to dp[i] / dp[i][j].
    var note: String?
    /// Equal tiles over the grid naming the inputs (matrix chain's "A1 10×30").
    var pills: [String] = []
    /// In display order; an entry shows only while its tone is on screen.
    var legend: [(StoryTone, String)] = dpLegend
}

private final class DpBuilder {
    let rows: Int, cols: Int
    var frames: [DpFrame] = []
    var values: [Int: String] = [:]
    init(_ rows: Int, _ cols: Int) { self.rows = rows; self.cols = cols }
    func key(_ r: Int, _ c: Int) -> Int { r * cols + c }
    func fill(_ r: Int, _ c: Int, _ value: String, _ status: String = "", reads: Set<Int> = [], options: [DpOption] = [],
              chips: [StoryChip] = [], headline: String? = nil, body: String? = nil, formula: String? = nil,
              formulaRows: [StoryFormulaRow] = [], note: String? = nil) {
        values[key(r, c)] = value
        frames.append(DpFrame(values: values, active: key(r, c), traced: [], status: status.isEmpty ? storyPlain(headline ?? "") : status,
                              reads: reads, options: options, chips: chips, headline: headline, body: body, formula: formula,
                              formulaRows: formulaRows, note: note))
    }
    /// A step that writes nothing new: an intro, a closing answer, a whole traceback at once.
    func frame(headline: String, body: String, active: Int? = nil, actives: Set<Int> = [], traced: Set<Int> = [],
               reads: Set<Int> = [], answer: Set<Int> = [], chips: [StoryChip] = [], formula: String? = nil,
               formulaRows: [StoryFormulaRow] = [], strip: [StoryCell] = [], note: String? = nil) {
        frames.append(DpFrame(values: values, active: active, traced: traced, status: storyPlain(headline), reads: reads,
                              chips: chips, headline: headline, body: body, formula: formula, formulaRows: formulaRows,
                              answer: answer, strip: strip, note: note, actives: actives))
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

/// dp[i] = dp[i-1] + dp[i-2], filled left to right. The call count is what the naive recursion would spend
/// on the same f(i), the thing the table saves.
private func fibonacciDp() -> [DpFrame] {
    let n = 9
    let b = DpBuilder(1, n + 1)
    var dp = [Int](repeating: 0, count: n + 1)
    var calls = [Int](repeating: 1, count: n + 1)
    for i in 0...n {
        dp[i] = i < 2 ? i : dp[i - 1] + dp[i - 2]
        calls[i] = i < 2 ? 1 : calls[i - 1] + calls[i - 2] + 1
        if i < 2 {
            b.fill(0, i, "\(dp[i])", chips: [StoryChip("i", "\(i)")], headline: "dp[\(i)] = {\(dp[i])} is a base case, written without looking anything up.",
                   body: i == 0 ? "The recurrence needs two earlier cells, so the first two are given." : "With dp[0] and dp[1] in place, every later cell has both of its inputs.",
                   formula: "dp[\(i)] = {\(dp[i])}")
        } else {
            b.fill(0, i, "\(dp[i])", reads: [i - 1, i - 2], chips: [StoryChip("i", "\(i)")],
                   headline: "dp[\(i)] adds the two cells before it: \(dp[i - 1]) + \(dp[i - 2]) = {\(dp[i])}.",
                   body: "Each value is computed once and reused, compared with \(calls[i]) calls for the recursive version.",
                   formula: "dp[\(i)] = {p:\(dp[i - 1])} + {p:\(dp[i - 2])} = {\(dp[i])}",
                   formulaRows: [StoryFormulaRow(label: "recursive f(\(i))", formula: "\(calls[i]) calls"),
                                 StoryFormulaRow(label: "table", formula: "\(i + 1) cells, each once")])
        }
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
            var chips = [StoryChip("i", "\(i)"), StoryChip("j", "\(j)")]
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
                chips.append(StoryChip("\(a[i - 1]) vs \(w[j - 1])", match ? "match" : "differ", match ? .done : .warn))
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
                   headline: "dp[\(i)][\(j)] = {\(dp[i][j])}: turning \(from) into \(to) takes \(edits(dp[i][j])).",
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
    for i in 0..<rows {
        for j in 0..<cols {
            if i == 0 || j == 0 {
                b.fill(i, j, "0", chips: [StoryChip("prefix", i == 0 ? "ε" : "\"\(a[0..<i].joined())\""), StoryChip("vs", j == 0 ? "ε" : "\"\(w[0..<j].joined())\"")],
                       headline: "An empty prefix shares {0} letters with anything.",
                       body: "Row ε and column ε stay zero, so every other cell has a neighbour to read.")
                continue
            }
            let x = a[i - 1], y = w[j - 1]
            let chips = [StoryChip("\(x) vs \(y)", x == y ? "match" : "differ", x == y ? .done : .warn),
                         StoryChip("prefixes", "\(a[0..<i].joined()) · \(w[0..<j].joined())")]
            if x == y {
                dp[i][j] = dp[i - 1][j - 1] + 1
                b.fill(i, j, "\(dp[i][j])", reads: [b.key(i - 1, j - 1)], chips: chips,
                       headline: "'\(x)' ends both prefixes, so it extends the diagonal: {\(dp[i][j])}.",
                       body: "A match joins the best subsequence of everything before both letters.",
                       formula: "diagonal {p:\(dp[i - 1][j - 1])} + 1 = {\(dp[i][j])}")
            } else {
                let up = dp[i - 1][j], left = dp[i][j - 1]
                dp[i][j] = max(up, left)
                b.fill(i, j, "\(dp[i][j])", reads: [b.key(i - 1, j), b.key(i, j - 1)], chips: chips,
                       headline: "'\(x)' and '\(y)' differ, so the better neighbour carries: {\(dp[i][j])}.",
                       body: "One of the two letters is not in the subsequence. Keep whichever drop leaves more.",
                       formula: "max( up {p:\(up)} , left {p:\(left)} ) = {\(dp[i][j])}")
            }
        }
    }
    // Every step back from the corner: diagonals are matched letters, the rest skip one.
    var i = rows - 1, j = cols - 1
    var path = Set<Int>()
    var letters: [String] = []
    while i > 0 && j > 0 {
        path.insert(b.key(i, j))
        if a[i - 1] == w[j - 1] { letters.append(a[i - 1]); i -= 1; j -= 1 }
        else if dp[i - 1][j] >= dp[i][j - 1] { i -= 1 } else { j -= 1 }
    }
    let word = letters.reversed().joined().uppercased()
    let corner = b.key(rows - 1, cols - 1)
    b.frame(headline: "Following the diagonals back spells out \(word).", body: "Each diagonal step is a matched letter.",
            traced: path.subtracting([corner]), answer: [corner],
            chips: [StoryChip("LCS", word), StoryChip("length", "\(word.count)", .answer)],
            strip: word.map { StoryCell(String($0), .done) })
    return b.frames
}

private let coins = [1, 3, 4]
private let coinAmount = 6
private let inf = Int.max / 2
private func coinFmt(_ x: Int) -> String { x >= inf ? "∞" : "\(x)" }
private func countWord(_ n: Int) -> String { n < 6 ? ["zero", "one", "two", "three", "four", "five"][n] : "\(n)" }

/// dp[i][a] = fewest coins for amount a using the first i denominations (unbounded).
private func coinChange() -> [DpFrame] {
    let rows = coins.count + 1, cols = coinAmount + 1
    let b = DpBuilder(rows, cols)
    var dp = [[Int]](repeating: [Int](repeating: inf, count: cols), count: rows)
    // What greedy (largest coin first) spends on the target, for the closing contrast.
    var greedy: [Int] = [], left = coinAmount
    for coin in coins.sorted(by: >) { while left >= coin { greedy.append(coin); left -= coin } }
    // The coins the table settles on, read back from the finished corner.
    func used() -> [Int] {
        var i = rows - 1, a = cols - 1, out: [Int] = []
        while a > 0 && i > 0 { if dp[i][a] == dp[i - 1][a] { i -= 1 } else { out.append(coins[i - 1]); a -= coins[i - 1] } }
        return out
    }
    for i in 0..<rows {
        for a in 0..<cols {
            let chips = [StoryChip("coin", i == 0 ? "none" : "\(coins[i - 1])"), StoryChip("amount", "\(a)")]
            if a == 0 {
                dp[i][0] = 0
                b.fill(i, 0, "0", chips: chips, headline: "Making 0 takes {0} coins.",
                       body: i == 0 ? "Row ε has no coins at all, so every other amount in it is out of reach." : "Every row starts from zero: no coins make nothing.")
                continue
            }
            if i == 0 {
                b.fill(0, a, "∞", chips: chips, headline: "With no coins, \(a) is out of reach: {∞}.", body: "∞ marks an amount this row cannot make.")
                continue
            }
            let coin = coins[i - 1], skip = dp[i - 1][a]
            if coin > a {
                dp[i][a] = skip
                b.fill(i, a, coinFmt(skip), reads: [b.key(i - 1, a)], chips: chips,
                       headline: "A \(coin) is too big for \(a), so the row above carries down: {\(coinFmt(skip))}.",
                       body: "Without room for this coin, the answer is whatever the smaller coins managed.",
                       formula: "\(coin) > \(a), keep {p:\(coinFmt(skip))}")
                continue
            }
            let rest = dp[i][a - coin]
            let use = rest < inf ? rest + 1 : inf
            dp[i][a] = min(skip, use)
            let v = coinFmt(dp[i][a])
            let headline: String
            if use < skip && skip >= inf { headline = "A \(coin) reaches \(a): 1 + \(coinFmt(rest)) = {\(v)} coins." }
            else if use < skip { headline = "Using a \(coin): 1 + \(coinFmt(rest)) = {\(v)} coins, fewer than \(coinFmt(skip))." }
            else if use == skip { headline = "Using a \(coin) ties with skipping it: {\(v)} either way." }
            else { headline = "Using a \(coin) costs 1 + \(coinFmt(rest)) = \(coinFmt(use)) coins. Skipping it keeps {\(v)}." }
            let body: String
            if i == rows - 1 && a == cols - 1 {
                let u = used()
                body = "So \(a) takes \(countWord(u.count)) coins, \(u.map(String.init).joined(separator: " + ")). That is the answer greedy missed."
            } else {
                body = "Each cell is the fewer of skipping this coin or using one more of it. The use reads from the same row, since coins repeat."
            }
            b.fill(i, a, v, reads: [b.key(i - 1, a), b.key(i, a - coin)], chips: chips, headline: headline, body: body,
                   formula: "min( skip {p:\(coinFmt(skip))} , use 1 + {p:\(coinFmt(rest))} ) = {\(v)}")
        }
    }
    let corner = b.key(rows - 1, cols - 1)
    if dp[rows - 1][cols - 1] < inf {
        var i = rows - 1, a = cols - 1
        var path = Set<Int>()
        while a > 0 && i > 0 {
            path.insert(b.key(i, a))
            if dp[i][a] == dp[i - 1][a] { i -= 1 } else { a -= coins[i - 1] }
        }
        path.insert(b.key(i, a))
        let u = used()
        b.frame(headline: "\(u.map(String.init).joined(separator: " + ")) makes \(coinAmount) with {v:\(u.count)} coins.",
                body: "Greedy grabs the \(greedy[0]) first and needs \(greedy.map(String.init).joined(separator: " + ")), \(greedy.count) coins. The table tried every coin at every amount.",
                traced: path.subtracting([corner]), answer: [corner],
                chips: [StoryChip("coins", u.map(String.init).joined(separator: " + ")), StoryChip("count", "\(u.count)", .answer)])
    }
    return b.frames
}

private let knapW = [1, 2, 3], knapV = [6, 10, 12], knapCap = 5

/// dp[i][w] = best value from the first i items within capacity w.
private func knapsack() -> [DpFrame] {
    let rows = knapW.count + 1, cols = knapCap + 1
    let b = DpBuilder(rows, cols)
    var dp = [[Int]](repeating: [Int](repeating: 0, count: cols), count: rows)
    for i in 0..<rows {
        for w in 0..<cols {
            let chips = [StoryChip("item", i == 0 ? "none" : "\(i)"), StoryChip("capacity", "\(w)")]
            if i == 0 || w == 0 {
                b.fill(i, w, "0", chips: chips, headline: i == 0 ? "No items yet: capacity \(w) holds {0}." : "Capacity 0 holds nothing: {0}.",
                       body: i == 0 ? "Row ε is the baseline every item is measured against." : "Column 0 stays zero, so a take that uses up the whole room reads a real cell.")
                continue
            }
            let weight = knapW[i - 1], value = knapV[i - 1], skip = dp[i - 1][w]
            if weight > w {
                dp[i][w] = skip
                b.fill(i, w, "\(skip)", reads: [b.key(i - 1, w)], chips: chips,
                       headline: "Item \(i) weighs \(weight), more than \(w). The row above carries: {\(skip)}.",
                       body: "An item that does not fit leaves the answer to the items before it.",
                       formula: "item \(i) too heavy, keep {p:\(skip)}")
                continue
            }
            let room = dp[i - 1][w - weight], take = value + room
            dp[i][w] = max(skip, take)
            let v = dp[i][w]
            let headline = take > skip ? "Taking item \(i) leaves room \(w - weight), worth \(room). \(value) + \(room) = {\(v)} beats \(skip)."
                : take == skip ? "Taking item \(i) ties with skipping it: {\(v)}." : "Skipping item \(i) keeps {\(v)}. Taking it only makes \(take)."
            b.fill(i, w, "\(v)", reads: [b.key(i - 1, w), b.key(i - 1, w - weight)], chips: chips, headline: headline,
                   body: "Each cell picks the better of skipping the item or taking it once.",
                   formula: "max( skip {p:\(skip)} , take \(value) + {p:\(room)} ) = {\(v)}")
        }
    }
    var i = rows - 1, w = cols - 1
    var path = Set<Int>(), taken: [Int] = []
    while i > 0 {
        path.insert(b.key(i, w))
        if dp[i][w] != dp[i - 1][w] { taken.append(i); w -= knapW[i - 1] }
        i -= 1
    }
    let corner = b.key(rows - 1, cols - 1), best = dp[rows - 1][cols - 1]
    let names = taken.reversed().map(String.init)
    b.frame(headline: "Items \(names.joined(separator: " and ")) fill the sack for {v:\(best)}.",
            body: "Where a cell differs from the one above it, that item was taken; step left by its weight.",
            traced: path.subtracting([corner]), answer: [corner],
            chips: [StoryChip("take", names.joined(separator: " + ")), StoryChip("value", "\(best)", .answer)])
    return b.frames
}

private let rodPrices = [1, 5, 8, 9, 10], rodLength = 5

/// "1", "1 and 2", "1 to 3": the piece lengths a row allows.
private func rodPieces(_ upTo: Int) -> String { upTo == 1 ? "1" : upTo == 2 ? "1 and 2" : "1 to \(upTo)" }

/// dp[i][l] = best revenue for a rod of length l cutting only pieces of length <= i.
private func rodCutting() -> [DpFrame] {
    let rows = rodPrices.count + 1, cols = rodLength + 1
    let b = DpBuilder(rows, cols)
    var dp = [[Int]](repeating: [Int](repeating: 0, count: cols), count: rows)
    for i in 0..<rows {
        for l in 0..<cols {
            let chips = [StoryChip("piece", i == 0 ? "none" : "\(i)"), StoryChip("rod", "\(l)")]
            if i == 0 || l == 0 {
                b.fill(i, l, "0", chips: chips, headline: i == 0 ? "No pieces allowed: a rod of \(l) sells for {0}." : "A rod of 0 sells for {0}.",
                       body: "Each row allows one more piece length than the row above it.")
                continue
            }
            let price = rodPrices[i - 1], skip = dp[i - 1][l]
            if i > l {
                dp[i][l] = skip
                b.fill(i, l, "\(skip)", reads: [b.key(i - 1, l)], chips: chips,
                       headline: "A \(i) is longer than the rod, so the row above carries: {\(skip)}.",
                       body: "Only pieces that fit can change the answer.", formula: "piece \(i) > rod \(l), keep {p:\(skip)}")
                continue
            }
            let rest = dp[i][l - i], cut = price + rest
            dp[i][l] = max(skip, cut)
            let v = dp[i][l]
            let headline: String
            if cut > skip && l == i { headline = "Selling the whole \(l) as one piece earns $\(price): {\(v)}." }
            else if cut > skip { headline = "Cut a \(i) for $\(price) and sell the leftover \(l - i) for $\(rest): {\(v)}." }
            else if cut == skip { headline = "Cutting a \(i) ties with skipping it: {\(v)}." }
            else { headline = "Skipping keeps {\(v)}. Cutting a \(i) only makes \(cut)." }
            b.fill(i, l, "\(v)", reads: [b.key(i - 1, l), b.key(i, l - i)], chips: chips, headline: headline,
                   body: cut > skip && i > 1 ? "That beats \(skip) from pieces of \(rodPieces(i - 1)) only. Pieces can repeat, so the take reads from the same row."
                       : "Pieces can repeat, so the take reads from the same row.",
                   formula: "max( skip {p:\(skip)} , cut \(price) + {p:\(rest)} ) = {\(v)}")
        }
    }
    var i = rows - 1, l = cols - 1
    var path = Set<Int>(), cuts: [Int] = []
    while i > 0 && l > 0 {
        path.insert(b.key(i, l))
        if dp[i][l] == dp[i - 1][l] { i -= 1 } else { cuts.append(i); l -= i }
    }
    let corner = b.key(rows - 1, cols - 1), best = dp[rows - 1][cols - 1]
    let pieces = cuts.sorted().map(String.init)
    b.frame(headline: "Pieces of \(pieces.joined(separator: " and ")) sell for {v:$\(best)}.",
            body: "Where a cell beats the one above it, that piece was cut; step left by its length.",
            traced: path.subtracting([corner]), answer: [corner],
            chips: [StoryChip("cuts", pieces.joined(separator: " + ")), StoryChip("revenue", "$\(best)", .answer)])
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

private func chainName(_ i: Int, _ j: Int) -> String { (i...j).map { "A\($0 + 1)" }.joined(separator: " ") }

/// A split written as parentheses: "(A1 A2) A3". A lone matrix needs none.
private func chainGroup(_ i: Int, _ j: Int) -> String { i == j ? "A\(i + 1)" : "(\(chainName(i, j)))" }

/// dp[i][j] = fewest scalar multiplications to multiply matrices i..j. Filled by chain length, so the table
/// populates diagonally rather than row by row; a chain of three tries its splits one at a time.
private func matrixChain() -> [DpFrame] {
    let p = chainDims, n = p.count - 1
    let b = DpBuilder(n, n)
    var dp = [[Int]](repeating: [Int](repeating: 0, count: n), count: n)
    for i in 0..<n { b.values[b.key(i, i)] = "0" }
    b.frame(headline: "A single matrix costs {0}: there is nothing to multiply.",
            body: "The table fills by chain length, so every shorter chain is ready before a longer one needs it.",
            actives: Set((0..<n).map { b.key($0, $0) }), chips: [StoryChip("length", "1")])
    for len in 2...n {
        for i in 0...(n - len) {
            let j = i + len - 1
            var tried: [(k: Int, join: Int, total: Int)] = []
            var best = Int.max, split = i
            for k in i..<j {
                let join = p[i] * p[k + 1] * p[j + 1]
                let total = dp[i][k] + dp[k + 1][j] + join
                tried.append((k, join, total))
                // Every split after the first gets its own step, so the comparison is visible.
                let better = total < best
                if better { best = total; split = k }
                let rows = tried.map { t -> StoryFormulaRow in
                    let current = t.k == k
                    let left = current ? "{p:\(dp[i][t.k])}" : "\(dp[i][t.k])"
                    let right = current ? "{p:\(dp[t.k + 1][j])}" : "\(dp[t.k + 1][j])"
                    return StoryFormulaRow(label: "k = \(t.k + 1)", formula: "\(left) + \(right) + \(t.join) = \(current ? "{\(t.total)}" : "\(t.total)")")
                }
                var chips = [StoryChip("length", "\(len)")]
                let headline: String, body: String
                if len == 2 {
                    headline = "\(chainName(i, j)) has one split: \(p[i]) × \(p[k + 1]) × \(p[j + 1]) = {\(total)}."
                    body = "m[i][j] is the cheapest way to multiply Ai through Aj."
                } else if k == i {
                    headline = "Splitting after A\(k + 1) costs {\(total)}."
                    body = "\(chainGroup(i, k)) \(chainGroup(k + 1, j)): the two sides' \(dp[i][k]) and \(dp[k + 1][j]), plus \(p[i]) × \(p[k + 1]) × \(p[j + 1]) = \(join) to join them."
                } else {
                    let times = tried[0].total / total
                    headline = better ? "Splitting after A\(k + 1) costs {\(total)}" + (times == 6 ? ", six times cheaper." : ".")
                        : "Splitting after A\(k + 1) costs \(total), more than {\(best)}."
                    body = "Only the upper triangle is used: m[i][j] is the cheapest way to multiply Ai through Aj."
                    chips[0] = StoryChip("best split", "\(chainGroup(i, split)) \(chainGroup(split + 1, j))")
                }
                dp[i][j] = best
                b.fill(i, j, "\(total)", reads: [b.key(i, k), b.key(k + 1, j)], chips: chips, headline: headline, body: body, formulaRows: rows)
            }
            b.values[b.key(i, j)] = "\(best)"
        }
    }
    let corner = b.key(0, n - 1)
    let split = (0..<(n - 1)).first { dp[0][$0] + dp[$0 + 1][n - 1] + p[0] * p[$0 + 1] * p[n] == dp[0][n - 1] } ?? 0
    let order = "\(chainGroup(0, split)) \(chainGroup(split + 1, n - 1))"
    b.frame(headline: "\(order) takes {v:\(dp[0][n - 1])} multiplications.",
            body: "The corner cell covers the whole chain. The order of the multiplications changes the cost, never the product.",
            answer: [corner], chips: [StoryChip("best split", order), StoryChip("cost", "\(dp[0][n - 1])", .answer)])
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

/// Rows in the order the table fills them: by how many cities are visited, then by the set.
private let tspMasks = (0..<16).filter { $0 & 1 == 1 }.sorted { ($0.nonzeroBitCount, $0) < ($1.nonzeroBitCount, $1) }
private func tspSet(_ mask: Int) -> String { "{" + (0..<4).filter { mask & (1 << $0) != 0 }.map(String.init).joined(separator: ",") + "}" }
private func tspMaskLabel(_ row: Int) -> String { tspMasks[row] == 15 ? "all" : tspSet(tspMasks[row]) }

private func bitmaskDp() -> [DpFrame] {
    let n = 4
    let cost = [[0, 10, 15, 20], [10, 0, 35, 25], [15, 35, 0, 30], [20, 25, 30, 0]]
    let full = (1 << n) - 1
    let b = DpBuilder(tspMasks.count, n)
    var dp = [[Int]](repeating: [Int](repeating: inf, count: n), count: 1 << n)
    var from = [[Int]](repeating: [Int](repeating: -1, count: n), count: 1 << n)
    func row(_ m: Int) -> Int { tspMasks.firstIndex(of: m)! }
    /// A set inside a headline, its braces escaped so they are not read as a colour mark.
    func set(_ m: Int) -> String { "\\" + tspSet(m) }

    b.frame(headline: "Four cities, starting from 0. A row is the set visited so far, a column the city the route ends at.",
            body: "That is 2ⁿ·n cells to fill instead of n! whole tours to compare.", note: "4 cities")
    dp[1][0] = 0
    b.fill(row(1), 0, "0", chips: [StoryChip("dp", "0")], headline: "The route starts at city 0, having travelled {0}.",
           body: "Every other cell extends a shorter route by one city.", note: "start")
    for mask in tspMasks {
        for last in 1..<n where mask & (1 << last) != 0 {
            let without = mask & ~(1 << last)
            // Every city the shorter route could have ended at, cheapest first.
            let options = (0..<n).filter { without & (1 << $0) != 0 && dp[without][$0] < inf }
                .map { (prev: $0, total: dp[without][$0] + cost[$0][last]) }
                .sorted { ($0.total, $0.prev) < ($1.total, $1.prev) }
            guard let first = options.first else { continue }
            let prev = first.prev, best = first.total
            dp[mask][last] = best
            from[mask][last] = prev
            let body = options.count > 1
                ? "That beats ending at \(options[1].prev) first: \(dp[without][options[1].prev]) + \(cost[options[1].prev][last]) = \(options[1].total)."
                : "Each cell is the shortest route through that set, ending at that city."
            b.fill(row(mask), last, "\(best)", reads: [b.key(row(without), prev)],
                   chips: [StoryChip("dp", "\(dp[without][prev]) + \(cost[prev][last]) = \(best)")],
                   headline: "Reach \(last) from \(set(without)) ending at \(prev): \(dp[without][prev]) + \(cost[prev][last]) = {\(best)}.",
                   body: body, note: "d(\(prev),\(last)) = \(cost[prev][last])")
        }
    }
    // Closing the tour: every end city of the full set, plus the road home.
    let closes = (1..<n).map { (last: $0, total: dp[full][$0] + cost[$0][0]) }
    let bestClose = closes.min { ($0.total, $0.last) < ($1.total, $1.last) }!
    b.frame(headline: "Back to 0 from each end of the full set. The cheapest tour costs {v:\(bestClose.total)}.",
            body: "The last row holds every city; adding the road home to 0 turns each cell into a whole tour.",
            reads: Set((1..<n).map { b.key(row(full), $0) }),
            formulaRows: closes.map { c in
                c.last == bestClose.last
                    ? StoryFormulaRow(label: "end at \(c.last)", formula: "{p:\(dp[full][c.last])} + \(cost[c.last][0]) = {v:\(c.total)}")
                    : StoryFormulaRow(label: "end at \(c.last)", formula: "\(dp[full][c.last]) + \(cost[c.last][0]) = \(c.total)")
            },
            note: "back to 0")
    // Walk the choices back to recover the tour, tracing the cells that produced it.
    var tour: [Int] = [], traced = Set<Int>()
    var mask = full, last = bestClose.last
    while last != -1 {
        traced.insert(b.key(row(mask), last))
        tour.append(last)
        let prev = from[mask][last]
        mask &= ~(1 << last)
        last = prev
    }
    let tourText = (tour.reversed() + [0]).map(String.init).joined(separator: " → ")
    let corner = b.key(row(full), bestClose.last)
    b.frame(headline: "Tour \(tourText) costs {v:\(bestClose.total)}.",
            body: "Each cell points back at the one it read from. \(b.frames.count - 2) cells stood in for 6 tours, and the gap grows as 2ⁿ·n² against n!.",
            traced: traced.subtracting([corner]), answer: [corner],
            chips: [StoryChip("tour", tourText), StoryChip("cost", "\(bestClose.total)", .answer)], note: "traceback")
    return b.frames
}

private let partitionItems = [1, 5, 11, 5]
private let partitionTotal = partitionItems.reduce(0, +)

/// Partition reduces to subset-sum at half the total, so the table is the subset-sum table with the
/// target derived rather than given.
private func partitionDp() -> [DpFrame] {
    let items = partitionItems, target = partitionTotal / 2
    let b = DpBuilder(items.count + 1, target + 1)
    var dp = [[Bool]](repeating: [Bool](repeating: false, count: target + 1), count: items.count + 1)
    func mark(_ x: Bool) -> String { x ? "T" : "–" }
    /// The subset behind a true cell, read back up the table, and the items it leaves out.
    func split(_ row: Int, _ sum: Int) -> (half: [Int], rest: [Int]) {
        var chosen: [Int] = [], t = sum
        for i in stride(from: row, through: 1, by: -1) where !dp[i - 1][t] { chosen.append(items[i - 1]); t -= items[i - 1] }
        let picked = Array(chosen.reversed())
        var rest = items
        for x in picked { if let at = rest.firstIndex(of: x) { rest.remove(at: at) } }
        return (picked, rest)
    }
    func braces(_ xs: [Int]) -> String { "{" + xs.map(String.init).joined(separator: ", ") + "}" }

    for i in 0...items.count {
        for t in 0...target {
            let chips = [StoryChip("item", i == 0 ? "none" : "\(items[i - 1])"), StoryChip("sum", "\(t)")]
            if i == 0 {
                dp[0][t] = t == 0
                b.fill(0, t, mark(t == 0), chips: chips,
                       headline: t == 0 ? "The empty subset makes 0: {T}." : "With no items, \(t) is out of reach: {–}.",
                       body: t == 0 ? "The total is \(partitionTotal), even, so each half must reach \(target)." : "Each cell asks whether some of the items so far add up to exactly that sum.")
                continue
            }
            let item = items[i - 1], skip = dp[i - 1][t], fits = t >= item
            let take = fits && dp[i - 1][t - item]
            dp[i][t] = skip || take
            let headline: String
            if !fits && skip { headline = "The \(item) is too big for \(t), and the row above already reaches it: {T}." }
            else if !fits { headline = "The \(item) is too big for \(t), and nothing smaller reaches it: {–}." }
            else if skip { headline = "\(t) was already reachable without the \(item): {T}." }
            else if take && t == item { headline = "dp[\(i - 1)][0] is true, so {\(item)} on its own reaches \(t)." }
            else if take { headline = "dp[\(i - 1)][\(t - item)] is true, so adding the {\(item)} reaches \(t)." }
            else { headline = "Neither skipping nor taking the \(item) reaches \(t): {–}." }
            let body: String
            if t == target && dp[i][t] && !skip {
                let s = split(i, t)
                body = "A subset sums to half of \(partitionTotal), so the array splits evenly: \(braces(s.half)) and \(braces(s.rest))."
            } else {
                body = "A cell is true if the row above is, or if taking this item lands on a true cell."
            }
            b.fill(i, t, mark(dp[i][t]), reads: fits ? [b.key(i - 1, t), b.key(i - 1, t - item)] : [b.key(i - 1, t)], chips: chips,
                   headline: headline, body: body,
                   formula: fits ? "skip {p:\(mark(skip))} or take {p:\(mark(dp[i - 1][t - item]))} → {\(mark(dp[i][t]))}" : "\(item) > \(t), keep {p:\(mark(skip))}")
        }
    }
    // Read the chosen items back: a true cell whose row above is false must have taken its item.
    var path = Set<Int>(), t = target
    for i in stride(from: items.count, through: 1, by: -1) {
        path.insert(b.key(i, t))
        if !dp[i - 1][t] { t -= items[i - 1] }
    }
    path.insert(b.key(0, t))
    let s = split(items.count, target)
    let corner = b.key(items.count, target)
    b.frame(headline: "\\\(braces(s.half)) and \\\(braces(s.rest)) both sum to {v:\(target)}.",
            body: "The table has n × \(target + 1) cells. That is pseudo-polynomial: fine for small sums, which is why partition stays NP-complete.",
            traced: path.subtracting([corner]), answer: [corner],
            chips: [StoryChip("halves", "\(s.half.reduce(0, +)) + \(s.rest.reduce(0, +))"), StoryChip("each", "\(target)", .answer)])
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
            let chips = [StoryChip("i", "\(i)"), StoryChip("j", "\(j)"), StoryChip("best", "\(best)", .answer)]
            if i == 0 || j == 0 {
                b.fill(i, j, "0", chips: chips, headline: "An empty prefix shares no suffix with anything: {0}.",
                       body: "Row ε and column ε stay zero, so every run has somewhere to start from.")
                continue
            }
            let x = lcsubA[i - 1], y = lcsubB[j - 1]
            if x == y {
                dp[i][j] = dp[i - 1][j - 1] + 1
                let v = dp[i][j]
                let run = lcsubA[(i - v)..<i].joined()
                let isBest = v > best
                if isBest { best = v; endI = i; endJ = j }
                b.fill(i, j, "\(v)", reads: [b.key(i - 1, j - 1)], chips: [StoryChip("i", "\(i)"), StoryChip("j", "\(j)"), StoryChip("best", "\(best)", .answer)],
                       headline: "dp[\(i)][\(j)] = {\(v)}: the run \"\(run)\" ends here" + (isBest ? ", a new best." : "."),
                       body: "A mismatch resets to 0, unlike subsequence DP, so only the diagonal carries a run.",
                       formula: "'\(x)' = '\(y)' → dp[\(i - 1)][\(j - 1)] + 1 = {p:\(dp[i - 1][j - 1])} + 1 = {\(v)}")
            } else {
                b.fill(i, j, "0", chips: chips, headline: "'\(x)' ≠ '\(y)', so the run breaks: {0}.",
                       body: "Not max(up, left): that would be subsequence DP, which lets a run survive a gap.",
                       formula: "'\(x)' ≠ '\(y)' → {0}")
            }
        }
    }
    var path = Set<Int>()
    for k in 0..<best { path.insert(b.key(endI - k, endJ - k)) }
    let corner = b.key(endI, endJ)
    let run = lcsubA[(endI - best)..<endI].joined()
    b.frame(headline: "The longest common substring is {v:\(run)}, length \(best).",
            body: "Its run peaks at dp[\(endI)][\(endJ)], not in the corner: the answer is the largest cell anywhere.",
            traced: path.subtracting([corner]), answer: [corner], chips: [StoryChip("substring", run, .answer), StoryChip("length", "\(best)")],
            formula: "max cell = dp[\(endI)][\(endJ)] = {v:\(best)}")
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
    "fibonacci_dp": DpConfig(rows: 1, cols: 10, rowHeader: { _ in "dp" }, colHeader: { "\($0)" }, corner: "",
                             intro: "Bottom-up Fibonacci: each cell is the sum of the two before it — computed once, left to right. No recursion, no repeated work.", build: fibonacciDp,
                             title: "BOTTOM-UP", note: "dp[i] = dp[i-1] + dp[i-2]"),
    "edit_distance": DpConfig(rows: 4, cols: 4, rowHeader: { $0 == 0 ? "ε" : chars("cat")[$0 - 1] }, colHeader: { $0 == 0 ? "ε" : chars("cut")[$0 - 1] }, corner: "",
                              intro: "Edit distance between \"cat\" and \"cut\". Each cell is the cheapest way to turn one prefix into the other; the traceback shows the actual edits.", build: editDistance, title: "CAT → CUT"),
    "longest_common_subsequence": DpConfig(rows: 6, cols: 5, rowHeader: { $0 == 0 ? "ε" : chars("abcbd")[$0 - 1] }, colHeader: { $0 == 0 ? "ε" : chars("acbd")[$0 - 1] }, corner: "",
                                           intro: "Longest common subsequence of \"abcbd\" and \"acbd\". On a match the diagonal grows; otherwise carry the best neighbour. Traceback recovers the subsequence.", build: lcs, title: "ABCBD · ACBD",
                                           legend: [(.active, "Current"), (.path, "Reads from"), (.done, "Traceback"), (.answer, "Length")]),
    "coin_change": DpConfig(rows: coins.count + 1, cols: coinAmount + 1, rowHeader: { $0 == 0 ? "ε" : "\(coins[$0 - 1])" }, colHeader: { "\($0)" }, corner: "¢",
                            intro: "Fewest coins to make each amount, using denominations {1, 3, 4}. Each row adds a coin type; ∞ means unreachable. Traceback shows which coins make the target.", build: coinChange,
                            title: "COINS {1, 3, 4}", note: "fewest coins", legend: Array(dpLegend.dropLast()) + [(.answer, "Fewest")]),
    "rod_cutting": DpConfig(rows: rodPrices.count + 1, cols: rodLength + 1, rowHeader: { $0 == 0 ? "ε" : "\($0)" }, colHeader: { "\($0)" }, corner: "len",
                            intro: "Rod cutting — prices (1,5,8,9,10) for lengths 1..5. Each row allows one more piece length; the traceback shows which cuts produce the best revenue.", build: rodCutting,
                            title: "PIECE PRICES", note: rodPrices.enumerated().map { "\($0.offset + 1):$\($0.element)" }.joined(separator: " "),
                            legend: Array(dpLegend.dropLast()) + [(.answer, "Best")]),
    "longest_increasing_subsequence": DpConfig(rows: 1, cols: lisInput.count, rowHeader: { _ in "dp" }, colHeader: { "\(lisInput[$0])" }, corner: "a[i]",
                                               intro: "Longest increasing subsequence of (3,1,4,2,6,5). Each cell is the longest run ending at that element — the answer is the largest cell, not the last one.", build: lis),
    "matrix_chain_multiplication": DpConfig(rows: 3, cols: 3, rowHeader: { "A\($0 + 1)" }, colHeader: { "A\($0 + 1)" }, corner: "",
                                            intro: "Matrix chain with dimensions 10×30, 30×5, 5×60. The table fills along diagonals — by chain length — so every sub-chain is solved before the chains that contain it. Cells below the diagonal stay empty.", build: matrixChain,
                                            title: "DIMENSIONS", note: "p = " + chainDims.map(String.init).joined(separator: ", "),
                                            pills: (0..<(chainDims.count - 1)).map { "A\($0 + 1) \(chainDims[$0])×\(chainDims[$0 + 1])" }),
    "knapsack_01": DpConfig(rows: knapW.count + 1, cols: knapCap + 1, rowHeader: { $0 == 0 ? "ε" : "\(knapW[$0 - 1]), $\(knapV[$0 - 1])" }, colHeader: { "\($0)" }, corner: "cap →",
                            intro: "0/1 knapsack — items (wt, val) = (1,6), (2,10), (3,12), capacity 5. Each cell is the best value achievable; the traceback marks the items chosen.", build: knapsack,
                            title: "ITEMS (w, $)", note: "cap \(knapCap)"),
    "bitmask_dp": DpConfig(rows: 8, cols: 4, rowHeader: tspMaskLabel, colHeader: { "at \($0)" }, corner: "",
                           intro: "Held-Karp TSP over four cities. A row is a subset of visited cities encoded as a bitmask, a column is the city you are standing on — 2ⁿ·n states instead of n! tours.", build: bitmaskDp,
                           title: "VISITED SET × ENDS AT"),
    "subset_sum": DpConfig(rows: 5, cols: 10, rowHeader: { $0 == 0 ? "ε" : "\(subsetItems[$0 - 1])" }, colHeader: { "\($0)" }, corner: "item",
                           intro: "Can any subset of {3, 4, 5, 2} total exactly 9? Each cell is a yes/no rather than a number, and the traceback recovers which items were actually chosen.", build: { subsetTable(subsetItems, 9, partition: false) }),
    "partition_problem": DpConfig(rows: partitionItems.count + 1, cols: partitionTotal / 2 + 1, rowHeader: { $0 == 0 ? "ε" : "\(partitionItems[$0 - 1])" }, colHeader: { "\($0)" }, corner: "",
                                  intro: "Can {1, 5, 11, 5} be split into two equal halves? The total is 22, so the question becomes whether any subset reaches exactly 11 — subset-sum with the target derived from the input rather than given.", build: partitionDp,
                                  title: "{" + partitionItems.map(String.init).joined(separator: ", ") + "} · SUM \(partitionTotal)", note: "target \(partitionTotal / 2)"),
    "longest_common_substring": DpConfig(rows: lcsubA.count + 1, cols: lcsubB.count + 1, rowHeader: { $0 == 0 ? "ε" : lcsubA[$0 - 1] }, colHeader: { $0 == 0 ? "ε" : lcsubB[$0 - 1] }, corner: "",
                                         intro: "Longest common substring of \"abcdxy\" and \"zabcdw\". A cell is the longest common *suffix* of the two prefixes, so a mismatch resets it to zero — and the answer is the largest cell anywhere, not the corner.", build: longestCommonSubstring,
                                         title: "ABCDXY · ZABCDW", legend: [(.active, "Current"), (.path, "Reads from"), (.done, "Run"), (.answer, "Longest")]),
]

// The DP story card (docs mocks for Coin Change, Knapsack, LCS, Matrix Chain…): the table with written
// cells in grey, the cell being written in yellow and the cells it reads in solid blue, then the recurrence
// with this cell's numbers, a legend of only what is on screen, value chips, and a headline whose key
// number takes the cell's colour. Cells a table never writes (matrix chain's lower triangle) are left out;
// cells still to come are dim tiles.

/// Each written or still-to-come cell's tone. Unwritten cells are empty; cells outside `used` are absent.
private func cellTones(_ frame: DpFrame, _ used: Set<Int>) -> [Int: StoryTone] {
    var out: [Int: StoryTone] = [:]
    for key in used {
        if key == frame.active || frame.actives.contains(key) { out[key] = .active }
        else if frame.answer.contains(key) { out[key] = .answer }
        else if frame.traced.contains(key) { out[key] = .done }
        else if frame.reads.contains(key) { out[key] = .path }
        else if frame.values[key] != nil { out[key] = .idle }
        else { out[key] = .empty }
    }
    return out
}

struct DpGridLab: View {
    private let config: DpConfig
    private let frames: [DpFrame]
    /// Every cell some step writes; the rest of the rectangle is never part of the table.
    private let used: Set<Int>
    @State private var playback: PlaybackState
    @Environment(\.palette) private var palette

    init(topicId: String) {
        config = dpConfigs[topicId] ?? dpConfigs["fibonacci_dp"]!
        frames = config.build()
        used = frames.reduce(into: Set<Int>()) { $0.formUnion($1.values.keys) }
        _playback = State(initialValue: PlaybackState(stepCount: frames.count))
    }

    var body: some View {
        let frame = frames[min(playback.index, frames.count - 1)]
        let tones = cellTones(frame, used)
        VStack(alignment: .leading, spacing: 0) {
            if config.title == nil {
                LabIntro(text: config.intro).padding(.bottom, 12)
            }
            LabCard {
                HStack {
                    Text(config.title ?? "").font(AppFont.sans(13, .semibold)).tracking(0.8).foregroundStyle(palette.muted).lineLimit(1)
                    Spacer(minLength: 10)
                    Text(frame.note ?? config.note ?? (config.rows == 1 ? "dp[i]" : "dp[i][j]"))
                        .font(AppFont.mono(13)).foregroundStyle(palette.muted).lineLimit(1)
                }
                if !config.pills.isEmpty {
                    HStack(spacing: 6) {
                        ForEach(config.pills.indices, id: \.self) { i in
                            Text(config.pills[i]).font(AppFont.mono(14, .semibold)).foregroundStyle(palette.onSurface).lineLimit(1)
                                .minimumScaleFactor(0.7)
                                .frame(maxWidth: .infinity).frame(height: 40)
                                .modifier(StoryTile(tone: .idle, radius: 9))
                        }
                    }
                    .padding(.top, 12)
                }
                DpGridTable(config: config, frame: frame, tones: tones).padding(.top, 12)
                if !frame.strip.isEmpty {
                    HStack(spacing: 6) {
                        ForEach(frame.strip.indices, id: \.self) { i in
                            let cell = frame.strip[i]
                            Text(cell.text).font(AppFont.mono(15, .bold)).foregroundStyle(tileColors(cell.tone, palette).ink)
                                .frame(maxWidth: .infinity).frame(height: 42)
                                .modifier(StoryTile(tone: cell.tone, radius: 10))
                        }
                    }
                    .padding(.top, 12)
                }
                if !frame.options.isEmpty {
                    HStack(spacing: 8) {
                        ForEach(frame.options.indices, id: \.self) { OptionCard(option: frame.options[$0]) }
                    }
                    .padding(.top, 12)
                }
                if let formula = frame.formula { StoryFormula(text: formula).padding(.top, 12) }
                if !frame.formulaRows.isEmpty {
                    StoryFormulaRows(rows: frame.formulaRows).padding(.top, frame.formula == nil ? 12 : 6)
                }
                let present = Set(tones.values)
                StoryLegendRow(items: config.legend.filter { present.contains($0.0) }.map { ($0.0.color, .fill, $0.1) })
                    .padding(.top, 14)
            }
            let chips = chipsFor(frame)
            if !chips.isEmpty { StoryChips(chips: chips).padding(.top, 16) }
            if let headline = frame.headline {
                LabStoryNarration(headline: headline, body: frame.body ?? "").padding(.top, 16)
            } else {
                // Tables without narration show their one-line status as the headline, a size down —
                // several run to two sentences.
                Text(frame.status)
                    .font(AppFont.sans(16, .semibold))
                    .foregroundStyle(palette.onSurface)
                    .lineSpacing(3)
                    .padding(.top, 16)
            }
            PlaybackTransport(state: playback, captions: frames.map(\.status))
        }
    }

    private func chipsFor(_ frame: DpFrame) -> [StoryChip] {
        if !frame.chips.isEmpty { return frame.chips }
        guard let key = frame.active else { return [] }
        if config.rows == 1 { return [StoryChip("i", "\(key % config.cols)")] }
        return [StoryChip("i", "\(key / config.cols)"), StoryChip("j", "\(key % config.cols)")]
    }
}

private struct DpGridTable: View {
    let config: DpConfig
    let frame: DpFrame
    let tones: [Int: StoryTone]
    @Environment(\.palette) private var palette

    // Roomy squares only fit small tables; wider ones step down so twelve columns still fit a phone.
    /// Tall tables (bitmask's eight sets) step down too, so the card stays on one screen.
    private var cellHeight: CGFloat { config.cols <= 5 && config.rows <= 6 ? 42 : config.cols <= 7 && config.rows <= 6 ? 38 : config.cols <= 10 ? 34 : 28 }
    private var gap: CGFloat { config.cols <= 7 ? 6 : 4 }
    private var fontSize: CGFloat { config.cols <= 5 ? 16 : config.cols <= 7 ? 15 : config.cols <= 10 ? 13 : 12 }
    private var radius: CGFloat { config.cols <= 7 ? 9 : 6 }
    /// Single letters (ε, c, a) get a narrow gutter; longer labels ({0,1,3}, 3, $12) get room for themselves.
    private var headerWidth: CGFloat {
        let longest = (0..<config.rows).map { config.rowHeader($0).count }.max() ?? 1
        return longest <= 2 ? 28 : min(CGFloat(longest * 8 + 6), 72)
    }

    var body: some View {
        let activeRow = frame.active.map { $0 / config.cols }
        let activeCol = frame.active.map { $0 % config.cols }
        VStack(spacing: gap) {
            HStack(spacing: gap) {
                label(config.corner, active: false).frame(width: headerWidth, alignment: .leading)
                ForEach(0..<config.cols, id: \.self) { c in
                    label(config.colHeader(c), active: c == activeCol).frame(maxWidth: .infinity)
                }
            }
            ForEach(0..<config.rows, id: \.self) { r in
                HStack(spacing: gap) {
                    label(config.rowHeader(r), active: r == activeRow).frame(width: headerWidth, alignment: .leading)
                    ForEach(0..<config.cols, id: \.self) { c in cell(r * config.cols + c) }
                }
            }
        }
    }

    /// Row and column labels; the current cell's row and column light up in the active yellow.
    private func label(_ text: String, active: Bool) -> some View {
        Text(text)
            .font(AppFont.mono(13, active ? .bold : .regular))
            .foregroundStyle(active ? StoryTone.active.ink(palette) : palette.muted)
            .lineLimit(1).minimumScaleFactor(0.6)
            .frame(height: 22)
    }

    @ViewBuilder
    private func cell(_ key: Int) -> some View {
        switch tones[key] {
        case nil:
            Color.clear.frame(maxWidth: .infinity).frame(height: cellHeight)
        case .empty?:
            // A slot still to fill: a dim tile, quieter than a written cell.
            RoundedRectangle(cornerRadius: radius).fill(palette.muted.opacity(0.08))
                .frame(maxWidth: .infinity).frame(height: cellHeight)
        case let tone?:
            Text(frame.values[key] ?? "")
                .font(AppFont.mono(fontSize, .bold))
                .foregroundStyle(tileColors(tone, palette).ink)
                .lineLimit(1).minimumScaleFactor(0.5)
                .frame(maxWidth: .infinity).frame(height: cellHeight)
                .modifier(StoryTile(tone: tone, radius: radius))
        }
    }
}

private struct OptionCard: View {
    let option: DpOption
    @Environment(\.palette) private var palette

    var body: some View {
        let shape = RoundedRectangle(cornerRadius: 10)
        VStack(alignment: .leading, spacing: 4) {
            Text("\(option.arrow) \(option.name)")
                .font(AppFont.sans(13, .semibold))
                .foregroundStyle(option.chosen ? StoryTone.active.ink(palette) : palette.muted)
                .lineLimit(1)
            Text(option.formula)
                .font(AppFont.mono(15, .bold))
                .foregroundStyle(palette.onSurface)
                .lineLimit(1).minimumScaleFactor(0.7)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, 10)
        .padding(.vertical, 8)
        .background(option.chosen ? SimColors.active.opacity(0.12) : SimColors.tint, in: shape)
        .overlay(shape.strokeBorder(option.chosen ? SimColors.active : .clear, lineWidth: 1.5))
    }
}
