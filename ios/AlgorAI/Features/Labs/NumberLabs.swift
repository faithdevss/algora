import SwiftUI

// Port of NumberLabs.kt. Euclid's GCD, Modular Arithmetic, Modular Exponentiation, Fast Power and the
// Sieve as tabbed storyboards. Each frame carries whichever figure its lab draws — a remainder table,
// the residues of a modulus on a clock, a column of recursive calls, or the sieve's number grid — then
// the arithmetic, chips and a headline. Every value is computed.

enum NumTone { case idle, current, done, next, answer, stack, waiting, prime, struckNow, struckEarlier, target, blank, operand, raw }

struct NumCell { let text: String; let tone: NumTone }

struct NumTable { let headers: [String]; let rows: [[NumCell]]; let current: Int? }

struct NumClock {
    let modulus: Int
    var tones: [Int: NumTone] = [:]
    /// A value written beside a residue ("23" next to 2), in that residue's colour.
    var labels: [Int: String] = [:]
    /// Residues joined in order by a dotted yellow line: the steps being counted.
    var path: [Int] = []
}

struct NumRow { let label: String; let expr: String; let value: String; let tone: NumTone }

struct NumFrame {
    var table: NumTable? = nil
    var clock: NumClock? = nil
    var rows: [NumRow] = []
    var sieve: [NumCell] = []
    var formula: [String] = []
    var formulaRows: [StoryFormulaRow] = []
    var chips: [StoryChip] = []
    let headline: String
    let body: String
}

struct NumTab { let label: String; let frames: [NumFrame]; let legend: [(Color, SwatchStyle, String)] }

private let dimSwatch = Color.gray.opacity(0.4)

private func sup(_ n: Int) -> String {
    let map: [Character: String] = ["0": "⁰", "1": "¹", "2": "²", "3": "³", "4": "⁴", "5": "⁵", "6": "⁶", "7": "⁷", "8": "⁸", "9": "⁹"]
    return String(n).map { map[$0] ?? String($0) }.joined()
}

private func grouped(_ n: Int) -> String {
    let f = NumberFormatter(); f.numberStyle = .decimal; f.locale = Locale(identifier: "en_US")
    return f.string(from: NSNumber(value: n)) ?? "\(n)"
}

// MARK: - Euclid

private func euclidTabs() -> [NumTab] {
    let a0 = 252, b0 = 105
    var steps: [(Int, Int, Int, Int)] = []
    var a = a0, b = b0
    while b != 0 { steps.append((a, b, a / b, a % b)); (a, b) = (b, a % b) }
    let g = a
    let headers = ["a", "b", "q", "a mod b"]
    func table(_ current: Int?, answer: Bool = false) -> NumTable {
        var rows: [[NumCell]] = []
        for (i, s) in steps.enumerated() {
            let tone: NumTone = current.map { i < $0 ? .done : i == $0 ? .current : .next } ?? .done
            if tone == .next && i > (current ?? 0) + 1 { break }
            rows.append(tone == .next
                ? [NumCell(text: "\(s.0)", tone: .next), NumCell(text: "\(s.1)", tone: .next), NumCell(text: "·", tone: .next), NumCell(text: "·", tone: .next)]
                : [NumCell(text: "\(s.0)", tone: tone), NumCell(text: "\(s.1)", tone: tone), NumCell(text: "\(s.2)", tone: tone), NumCell(text: "\(s.3)", tone: tone)])
        }
        if current == nil || current! >= steps.count - 1 {
            rows.append([NumCell(text: "\(g)", tone: answer ? .answer : .next), NumCell(text: "0", tone: .next), NumCell(text: "·", tone: .next), NumCell(text: "·", tone: .next)])
        }
        return NumTable(headers: headers, rows: rows, current: current)
    }
    var gcd = [NumFrame(table: NumTable(headers: headers, rows: [[NumCell(text: "\(a0)", tone: .next), NumCell(text: "\(b0)", tone: .next), NumCell(text: "·", tone: .next), NumCell(text: "·", tone: .next)]], current: nil),
                        formula: ["gcd(a, b) = gcd(b, a mod b)"], chips: [StoryChip("a", "\(a0)"), StoryChip("b", "\(b0)")],
                        headline: "Euclid replaces (a, b) with (b, a mod b) until b is 0.",
                        body: "Division with remainder is all it needs: no factoring, no trial divisors.")]
    for (i, s) in steps.enumerated() {
        let last = s.3 == 0
        gcd.append(NumFrame(table: table(i), formula: ["\(s.0) = {\(s.2)} · \(s.1) + {\(s.3)}"], chips: [StoryChip("a", "\(s.0)"), StoryChip("b", "\(s.1)")],
                            headline: last ? "\(s.0) mod \(s.1) = {0}: \(s.1) divides \(s.0) exactly." : "\(s.0) mod \(s.1) = {\(s.3)}, so the next pair is (\(s.1), \(s.3)).",
                            body: last ? "A zero remainder ends the loop; the divisor is the answer."
                                : "gcd(a, b) = gcd(b, a mod b), because both pairs have exactly the same common divisors."))
    }
    gcd.append(NumFrame(table: table(steps.count, answer: true), formula: ["gcd(\(a0), \(b0)) = {v:\(g)}"],
                        chips: [StoryChip("divisions", "\(steps.count)"), StoryChip("gcd", "\(g)", .answer)],
                        headline: "b reached 0, so gcd(\(a0), \(b0)) = {v:\(g)} after \(steps.count) divisions.",
                        body: "Remainders at least halve every two steps, so Euclid takes O(log min(a, b)) divisions."))
    let product = a0 * b0, lcm = a0 / g * b0
    let lcmTab = [
        NumFrame(table: table(steps.count, answer: true), formula: ["lcm(a, b) = a · b / gcd(a, b)"], chips: [StoryChip("gcd", "\(g)", .answer)],
                 headline: "With the gcd in hand, the lcm is one multiply and one divide.",
                 body: "a · b counts the shared factor \(g) twice; dividing by the gcd removes the extra copy."),
        NumFrame(table: table(steps.count, answer: true), formula: ["\(a0) · \(b0) = {\(grouped(product))}"], chips: [StoryChip("a · b", grouped(product))],
                 headline: "Multiply first: \(a0) · \(b0) = {\(grouped(product))}.",
                 body: "Correct, but the product can be far bigger than the answer, which is where overflow bites."),
        NumFrame(table: table(steps.count, answer: true), formula: ["\(grouped(product)) / \(g) = {v:\(grouped(lcm))}"],
                 chips: [StoryChip("lcm", grouped(lcm), .answer)],
                 headline: "\(grouped(product)) / \(g) = {v:\(grouped(lcm))}, the smallest number both divide.",
                 body: "\(grouped(lcm)) = \(a0) · \(lcm / a0) = \(b0) · \(lcm / b0)."),
        NumFrame(table: table(steps.count, answer: true), formula: ["\(a0) / \(g) · \(b0) = \(a0 / g) · \(b0) = {v:\(grouped(lcm))}"],
                 chips: [StoryChip("lcm", grouped(lcm), .answer), StoryChip("largest", grouped(lcm))],
                 headline: "Divide first and the biggest value is the answer itself, {v:\(grouped(lcm))}.",
                 body: "Same result with no intermediate larger than the lcm, so it cannot overflow unless the answer does."),
    ]
    let legend: [(Color, SwatchStyle, String)] = [(SimColors.active, .fill, "Current step"), (SimColors.green, .fill, "Done"), (dimSwatch, .fill, "Next pair")]
    return [NumTab(label: "GCD", frames: gcd, legend: legend), NumTab(label: "LCM", frames: lcmTab, legend: legend + [(SimColors.answer, .fill, "Answer")])]
}

// MARK: - Modular arithmetic

private func modularTabs() -> [NumTab] {
    let m = 7, a = 17, b = 23
    let ra = a % m, rb = b % m
    func clock(_ tones: [Int: NumTone], _ labels: [Int: String], path: [Int] = []) -> NumClock { NumClock(modulus: m, tones: tones, labels: labels, path: path) }
    let add = (ra + rb) % m, sub = ((ra - rb) % m + m) % m, mul = (ra * rb) % m
    let raw = (a - b) % m
    let addTab = [
        NumFrame(clock: clock([ra: .operand, rb: .operand], [ra: "\(a)", rb: "\(b)"]), formula: ["\(a) % \(m) = \(ra)    \(b) % \(m) = \(rb)"],
                 chips: [StoryChip("a", "\(a) → \(ra)"), StoryChip("b", "\(b) → \(rb)")],
                 headline: "Mod \(m), every number sits on one of \(m) positions: \(a) is at {p:\(ra)}, \(b) at {p:\(rb)}.",
                 body: "Only the remainder matters, so the big numbers can be reduced before anything else."),
        NumFrame(clock: clock([ra: .operand, add: .answer], [ra: "\(a)", add: "\(a + b)"], path: Array(stride(from: ra, through: ra + rb, by: 1)).map { $0 % m }),
                 formula: ["(\(ra) + \(rb)) % \(m) = {v:\(add)} = \(a + b) % \(m)"], chips: [StoryChip("sum", "\(a + b)"), StoryChip("residue", "\(add)", .answer)],
                 headline: "Adding is walking the clock: \(rb) steps on from \(ra) lands on {v:\(add)}.",
                 body: "(a + b) mod m = ((a mod m) + (b mod m)) mod m, so the full sum never has to be formed."),
    ]
    let subTab = [
        NumFrame(clock: clock([ra: .operand, rb: .operand, sub: .answer], [ra: "\(a)", rb: "\(b)", sub: "\(a - b)"], path: (0...rb).map { ((ra - $0) % m + m) % m }),
                 formula: ["(\(ra) − \(rb)) % \(m) = {v:\(sub)}"], chips: [StoryChip("a − b", "\(a - b)"), StoryChip("residue", "\(sub)", .answer)],
                 headline: "Subtracting walks backwards: \(rb) steps back from \(ra) lands on {v:\(sub)}.",
                 body: "On the clock there are no negatives. Every difference is one of 0 to \(m - 1)."),
        NumFrame(clock: clock([ra: .operand, rb: .operand, sub: .answer], [ra: "\(a)", rb: "\(b)", sub: "\(a - b)"], path: (0...rb).map { ((ra - $0) % m + m) % m }),
                 formula: ["((\(a - b < 0 ? "{w:\(a - b)}" : "\(a - b)") % \(m)) + \(m)) % \(m) = {v:\(sub)}"],
                 chips: [StoryChip("\(a - b) % \(m)", "\(raw)", .warn), StoryChip("normalised", "\(sub)", .answer)],
                 headline: "\(a) − \(b) = \(a - b), but % gives {w:\(raw)}, not {v:\(sub)}.",
                 body: "% keeps the dividend's sign. Adding m and taking % again lands on the residue."),
    ]
    let mulTab = [
        NumFrame(clock: clock([ra: .operand, rb: .operand], [ra: "\(a)", rb: "\(b)"]), formula: ["\(a) · \(b) = \(a * b)"],
                 chips: [StoryChip("a · b", "\(a * b)")],
                 headline: "\(a) · \(b) = \(a * b), but only its place on the clock matters.",
                 body: "Multiply the residues instead: they are small, and the answer is the same."),
        NumFrame(clock: clock([ra: .operand, mul: .answer], [ra: "\(a)", mul: "\(a * b)"], path: (0...rb).map { ($0 * ra) % m }),
                 formula: ["(\(ra) · \(rb)) % \(m) = {v:\(mul)} = \(a * b) % \(m)"], chips: [StoryChip("residue", "\(mul)", .answer)],
                 headline: "\(rb) hops of \(ra) from 0 land on {v:\(mul)}.",
                 body: "(a · b) mod m = ((a mod m) · (b mod m)) mod m, which keeps every product below m²."),
    ]
    let inv = (1..<m).first { ($0 * ra) % m == 1 }!
    let invHops = (0...inv).map { ($0 * ra) % m }
    let invTab = [
        NumFrame(clock: clock([ra: .operand], [ra: "\(ra)"], path: invHops), formula: ["\(ra) · x ≡ 1 (mod \(m))"],
                 chips: [StoryChip("a", "\(ra)"), StoryChip("hops", "\(inv)")],
                 headline: "Division needs an inverse: the x that makes \(ra) · x land on {1}.",
                 body: "Hop by \(ra) from 0 until the clock reads 1. It only works when gcd(a, m) = 1."),
        NumFrame(clock: clock([ra: .operand, 1: .answer], [ra: "\(ra)", 1: "×\(inv)"], path: invHops),
                 formula: ["\(ra) · \(inv) = \(ra * inv) = \(ra * inv / m) · \(m) + {v:1}"], chips: [StoryChip("inverse", "\(inv)", .answer)],
                 headline: "\(inv) hops of \(ra) land on 1, so \(ra)⁻¹ = {v:\(inv)} (mod \(m)).",
                 body: "Dividing by \(ra) mod \(m) means multiplying by \(inv). For large m, extended Euclid finds it in O(log m)."),
    ]
    let legend: [(Color, SwatchStyle, String)] = [(SimColors.blue, .fill, "Operand residue"), (SimColors.active, .fill, "Steps"), (SimColors.answer, .fill, "Answer")]
    return [NumTab(label: "Add", frames: addTab, legend: legend),
            NumTab(label: "Subtract", frames: subTab, legend: [(SimColors.blue, .fill, "Operand residue"), (SimColors.active, .fill, "Step back \(rb)"), (SimColors.answer, .fill, "Answer"), (SimColors.red, .fill, "Raw % result")]),
            NumTab(label: "Multiply", frames: mulTab, legend: legend),
            NumTab(label: "Inverse", frames: invTab, legend: legend)]
}

// MARK: - Powers

/// Recursive and iterative squaring for 3¹³, reduced mod `mod` when given.
private func powerTabs(mod: Int?) -> [NumTab] {
    let base = 3, e0 = 13
    func red(_ v: Int) -> Int { mod.map { v % $0 } ?? v }
    let modText = mod.map { " mod \($0)" } ?? ""
    var exps: [Int] = []
    var e = e0
    while true { exps.append(e); if e == 0 { break }; e /= 2 }
    var value: [Int: Int] = [0: 1]
    for e in exps.reversed() where e > 0 { let h = value[e / 2]!; value[e] = red(red(h * h) * (e % 2 == 1 ? base : 1)) }
    func expr(_ e: Int, known: Bool) -> String {
        if e == 0 { return "base" }
        // Mod shows the reduced number being squared; plain powers keep the exponent form.
        let inner = known && mod != nil ? "(\(value[e / 2]!))" : "(\(base)\(sup(e / 2)))"
        let body = inner + "²" + (e % 2 == 1 ? "·\(base)" : "")
        return mod == nil ? "\(e % 2 == 1 ? "odd" : "even") · " + body : body + modText
    }
    func rows(_ deepest: Int, returning: Int?, answer: Bool = false) -> [NumRow] {
        exps.prefix(deepest + 1).enumerated().map { i, e in
            let label = "\(base)\(sup(e))"
            if let returning, i > returning { return NumRow(label: label, expr: expr(e, known: true), value: "\(value[e]!)", tone: .done) }
            if let returning, i == returning { return NumRow(label: label, expr: expr(e, known: true), value: "\(value[e]!)", tone: answer ? .answer : .current) }
            return NumRow(label: label, expr: expr(e, known: false), value: mod == nil ? "waiting" : "…", tone: .waiting)
        }
    }
    let naive = e0 - 1
    func stepFormula(_ e: Int) -> String {
        if e == 0 { return "\(base)⁰ = {1}" }
        let h = value[e / 2]!
        if let mod {
            let sq = h * h
            return e % 2 == 1 ? "\(h)² = \(sq) ≡ \(sq % mod) → \(sq % mod) · \(base) = {\(value[e]!)} (mod \(mod))" : "\(h)² = \(sq) ≡ {\(value[e]!)} (mod \(mod))"
        }
        return "\(base)\(sup(e)) = (\(base)\(sup(e / 2)))²\(e % 2 == 1 ? "·\(base)" : "") = \(grouped(h))²\(e % 2 == 1 ? "·\(base)" : "") = {\(grouped(value[e]!))}"
    }
    var largest = 1
    var rec = [NumFrame(rows: rows(0, returning: nil), formula: ["\(base)\(sup(e0))\(modText)"],
                        chips: [StoryChip("calls", "1"), StoryChip("naive", "\(naive) mults")],
                        headline: "Compute \(base)\(sup(e0))\(modText) by halving the exponent.",
                        body: "Even n: xⁿ = (x^(n/2))². Odd n: one extra multiply by x.")]
    for (i, e) in exps.enumerated() where e > 0 {
        rec.append(NumFrame(rows: rows(i + 1, returning: nil), formula: ["\(base)\(sup(e)) needs \(base)\(sup(e / 2)) first"],
                            chips: [StoryChip("calls", "\(i + 2)"), StoryChip("naive", "\(naive) mults")],
                            headline: "\(e) is \(e % 2 == 1 ? "odd" : "even"), so {\(base)\(sup(e))} calls \(base)\(sup(e / 2)) and waits.",
                            body: "Each call halves the exponent, so \(e0) needs \(exps.count) calls instead of \(naive) multiplications."))
    }
    for i in stride(from: exps.count - 1, through: 0, by: -1) {
        let e = exps[i]
        if e > 0 { let h = value[e / 2]!; largest = max(largest, h * h * (mod == nil && e % 2 == 1 ? base : 1)) }
        let final = i == 0
        let headline: String
        if e == 0 { headline = "\(base)⁰ is the base case: {1}." }
        else if final { headline = "\(base)\(sup(e))\(modText) = {v:\(grouped(value[e]!))}." }
        else if e % 2 == 0 { headline = "\(e) is even, so \(base)\(sup(e)) just squares \(grouped(value[e / 2]!)): {\(grouped(value[e]!))}." }
        else { headline = "\(e) is odd, so \(base)\(sup(e)) squares \(grouped(value[e / 2]!)) and multiplies by \(base): {\(grouped(value[e]!))}." }
        let body: String
        if final, let mod { body = "Reducing after every multiply keeps each value below \(mod)², so it never overflows. Without mod, \(base)\(sup(e0)) = \(grouped(Int(pow(Double(base), Double(e0)))))." }
        else if final { body = "\(exps.count) calls and at most two multiplies each: O(log n) instead of \(naive) multiplications." }
        else { body = "Each call halves the exponent, so \(e0) needs \(exps.count) calls instead of \(naive) multiplications." }
        var chips = [StoryChip("calls", "\(exps.count)"), StoryChip("naive", "\(naive) mults")]
        if final, let mod { chips = [StoryChip("\(base)\(sup(e0)) mod \(mod)", "\(value[e]!)", .answer), StoryChip("largest", "\(largest)")] }
        rec.append(NumFrame(rows: rows(exps.count - 1, returning: i), formula: [stepFormula(e).replacingOccurrences(of: final && mod != nil ? "{\(value[e]!)}" : "@@", with: "{v:\(value[e]!)}")],
                            chips: chips, headline: headline, body: body))
    }
    // Iterative: read the exponent's bits from the bottom, squaring the base each time.
    var r = 1, b = base, k = e0, bit = 0
    var iterRows: [NumRow] = []
    var iter = [NumFrame(rows: [], formula: ["\(e0) = \(String(e0, radix: 2)) in binary"], chips: [StoryChip("result", "1"), StoryChip("base", "\(base)")],
                         headline: "The loop reads \(e0)'s bits from the lowest: \(String(e0, radix: 2)).",
                         body: "Each 1 bit multiplies the current power of \(base) into the result; every bit squares the base.")]
    while k > 0 {
        let on = k & 1 == 1
        let newR = on ? red(r * b) : r
        let newB = red(b * b)
        iterRows = iterRows.map { NumRow(label: $0.label, expr: $0.expr, value: $0.value, tone: .done) }
        let exprText = on ? "r = \(grouped(r))·\(grouped(b))\(modText)" : "skip, r = \(grouped(r))"
        iterRows.insert(NumRow(label: "bit \(bit) = \(on ? 1 : 0)", expr: exprText, value: grouped(newR), tone: .current), at: 0)
        iter.append(NumFrame(rows: iterRows,
                             formula: [on ? "r = \(grouped(r)) · \(grouped(b)) = {\(grouped(newR))}" + (mod == nil ? "" : " (mod \(mod!))") : "bit is 0 → r stays {\(grouped(r))}",
                                       "base = \(grouped(b))² = \(grouped(newB))" + (mod == nil ? "" : " (mod \(mod!))")],
                             chips: [StoryChip("result", grouped(newR), k / 2 == 0 ? .answer : .idle), StoryChip("base", grouped(newB))],
                             headline: on ? "Bit \(bit) is 1, so the result picks up \(base)\(sup(1 << bit)): {\(grouped(newR))}." : "Bit \(bit) is 0, so the result stays {\(grouped(r))}.",
                             body: k / 2 == 0 ? "No bits left: \(base)\(sup(e0))\(modText) = \(grouped(newR)), in \(bit + 1) iterations." : "The base squares every step: \(base), \(base)², \(base)⁴, \(base)⁸."))
        r = newR; b = newB; k >>= 1; bit += 1
    }
    iter.append(NumFrame(rows: iterRows.enumerated().map { NumRow(label: $1.label, expr: $1.expr, value: $1.value, tone: $0 == 0 ? .answer : .done) },
                         formula: ["\(base)\(sup(e0))\(modText) = {v:\(grouped(r))}"], chips: [StoryChip("result", grouped(r), .answer), StoryChip("iterations", "\(bit)")],
                         headline: "\(base)\(sup(e0))\(modText) = {v:\(grouped(r))} after \(bit) iterations.",
                         body: "Same answer as the recursion with no stack at all: one loop per bit, O(log n)."))
    let recLegend: [(Color, SwatchStyle, String)] = mod == nil
        ? [(SimColors.active, .fill, "Active"), (SimColors.active, .ring, "On the stack"), (SimColors.green, .fill, "Returned")]
        : [(SimColors.active, .fill, "Active"), (SimColors.green, .fill, "Returned"), (SimColors.answer, .fill, "Answer")]
    return [NumTab(label: "Recursive", frames: rec, legend: recLegend),
            NumTab(label: "Iterative", frames: iter, legend: [(SimColors.active, .fill, "Current bit"), (SimColors.green, .fill, "Done"), (SimColors.answer, .fill, "Answer")])]
}

// MARK: - Sieve

private func sieveTabs() -> [NumTab] {
    let n = 30
    var struckBy = [Int: Int]()
    var frames: [NumFrame] = []
    var primesDone: [Int] = []
    let limit = Int(Double(n).squareRoot())
    let passes = (2...limit).filter { p in (2..<p).allSatisfy { p % $0 != 0 } }
    func cells(current: Int?, target: Int? = nil) -> [NumCell] {
        (1...n).map { v in
            if v == 1 { return NumCell(text: "", tone: .blank) }
            if v == target { return NumCell(text: "\(v)", tone: .target) }
            if v == current { return NumCell(text: "\(v)", tone: .current) }
            if primesDone.contains(v) { return NumCell(text: "\(v)", tone: .prime) }
            if let by = struckBy[v] { return NumCell(text: "\(v)", tone: by == current ? .struckNow : .struckEarlier) }
            return NumCell(text: "\(v)", tone: .stack)
        }
    }
    func standing() -> Int { (2...n).filter { struckBy[$0] == nil }.count }
    let passesRow = [StoryFormulaRow(label: "passes needed", formula: "p ≤ √\(n) → " + passes.map(String.init).joined(separator: ", "))]
    frames.append(NumFrame(sieve: cells(current: nil), formula: ["cross out multiples of each prime p ≤ √\(n)"], formulaRows: passesRow,
                           chips: [StoryChip("n", "\(n)"), StoryChip("standing", "\(standing())")],
                           headline: "Every number from 2 to \(n) starts out standing.",
                           body: "The sieve never tests a number for primality; it only crosses out multiples."))
    for p in passes {
        // Odd primes step by 2p: every other multiple is even and already gone.
        let multiples = Array(stride(from: p * p, through: n, by: p == 2 ? 2 : 2 * p))
        func list(_ upto: Int?) -> String {
            multiples.map { $0 == upto ? "{w:\($0)}" : "\($0)" }.joined(separator: ", ")
        }
        frames.append(NumFrame(sieve: cells(current: p), formula: ["p = {\(p)} : start at \(p)² = \(p * p) → " + list(nil)], formulaRows: passesRow,
                               chips: [StoryChip("p", "\(p)"), StoryChip("standing", "\(standing())")],
                               headline: "{\(p)} is still standing, so it is prime. Strike its multiples from \(p)² = \(p * p).",
                               body: p == 2 ? "Anything smaller than p² with a factor p has a smaller factor too, so it is already gone." : "\(p) · 2 up to \(p) · \(p - 1) were struck by smaller primes already."))
        if p == 2 {
            multiples.forEach { struckBy[$0] = p }
            frames.append(NumFrame(sieve: cells(current: p), formula: ["p = {2} : " + multiples.map(String.init).joined(separator: ", ")], formulaRows: passesRow,
                                   chips: [StoryChip("p", "2"), StoryChip("struck", "\(multiples.count)"), StoryChip("standing", "\(standing())")],
                                   headline: "p = 2 strikes every even number from 4 up: {w:\(multiples.count)} of them.",
                                   body: "Half the grid gone in one pass, with nothing but additions of 2."))
        } else {
            for j in multiples {
                struckBy[j] = p
                frames.append(NumFrame(sieve: cells(current: p, target: j), formula: ["p = {\(p)} : start at \(p)² = \(p * p) → " + list(j)], formulaRows: passesRow,
                                       chips: [StoryChip("p", "\(p)"), StoryChip("j", "\(j)"), StoryChip("standing", "\(standing())")],
                                       headline: j == p * p ? "p = \(p) strikes {w:\(j)} first, its own square." : "p = \(p) strikes {w:\(j)}. It started at \(p * p) because \(p * 2) was already struck by 2.",
                                       body: "No division happens, and the step is 2p because the even multiples are already gone. After p = \(passes.last!), whatever is still blue is prime."))
            }
        }
        primesDone.append(p)
    }
    let primes = (2...n).filter { struckBy[$0] == nil }
    primesDone = primes
    frames.append(NumFrame(sieve: cells(current: nil), formula: ["primes ≤ \(n): {v:" + primes.map(String.init).joined(separator: " ") + "}"], formulaRows: passesRow,
                           chips: [StoryChip("primes", "\(primes.count)", .answer), StoryChip("passes", "\(passes.count)")],
                           headline: "\(passes.count) passes leave {v:\(primes.count)} primes up to \(n).",
                           body: "Total work is about n log log n: each number is struck once per prime factor below √n."))
    return [NumTab(label: "Sieve", frames: frames,
                   legend: [(SimColors.active, .fill, "Current prime"), (SimColors.green, .fill, "Prime"), (SimColors.blue, .fill, "Still standing"),
                            (SimColors.red, .fill, "Struck this pass"), (dimSwatch, .fill, "Struck earlier")])]
}

// MARK: - Lab

let numberStoryTopicIds: Set<String> = ["euclid_gcd", "modular_arithmetic", "modular_exponentiation", "fast_power", "sieve_of_eratosthenes"]

private func numberTabs(_ topicId: String) -> [NumTab] {
    switch topicId {
    case "euclid_gcd": euclidTabs()
    case "modular_arithmetic": modularTabs()
    case "modular_exponentiation": powerTabs(mod: 17)
    case "fast_power": powerTabs(mod: nil)
    default: sieveTabs()
    }
}

struct NumberStoryLab: View {
    private let tabs: [NumTab]
    @State private var tab = 0
    @State private var playback: PlaybackState
    @Environment(\.palette) private var palette

    init(topicId: String) {
        tabs = numberTabs(topicId)
        _playback = State(initialValue: PlaybackState(stepCount: tabs[0].frames.count, speedMs: 1000))
    }

    var body: some View {
        let frames = tabs[tab].frames
        let frame = frames[min(playback.index, frames.count - 1)]
        VStack(alignment: .leading, spacing: 0) {
            LabCard {
                if tabs.count > 1 {
                    LabSegments(labels: tabs.map(\.label), selected: Binding(get: { tab }, set: { select($0) })).padding(.bottom, 14)
                }
                if let table = frame.table { NumTableView(table: table) }
                if let clock = frame.clock {
                    NumClockView(clock: clock).frame(height: 200)
                        .background(Color.black.opacity(0.16), in: RoundedRectangle(cornerRadius: 14))
                }
                if !frame.rows.isEmpty { NumRowsView(rows: frame.rows) }
                if !frame.sieve.isEmpty { NumSieveView(cells: frame.sieve) }
                if !frame.formula.isEmpty {
                    VStack(spacing: 4) {
                        ForEach(frame.formula.indices, id: \.self) { i in
                            storyText(frame.formula[i], palette).font(AppFont.mono(14)).foregroundStyle(palette.onSurface.opacity(0.8))
                                .multilineTextAlignment(.center).minimumScaleFactor(0.7)
                        }
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 12).padding(.horizontal, 10)
                    .background(SimColors.tint, in: RoundedRectangle(cornerRadius: 10))
                    .padding(.top, 12)
                }
                if !frame.formulaRows.isEmpty { StoryFormulaRows(rows: frame.formulaRows).padding(.top, 8) }
                StoryLegendRow(items: tabs[tab].legend).padding(.top, 14)
            }
            if !frame.chips.isEmpty { StoryChips(chips: frame.chips).padding(.top, 16) }
            LabStoryNarration(headline: frame.headline, body: frame.body).padding(.top, 16)
            PlaybackTransport(state: playback, captions: frames.map { storyPlain($0.headline) })
        }
    }

    private func select(_ i: Int) {
        guard i != tab else { return }
        tab = i
        playback = PlaybackState(stepCount: tabs[i].frames.count, speedMs: 1000)
    }
}

/// Fill and text for a tone, shared by the table, rows and sieve.
private func numColors(_ tone: NumTone, _ palette: Palette) -> (Color, Color) {
    let soft = palette.dark ? 0.22 : 0.16
    switch tone {
    case .current: return (SimColors.active, Color(hex: 0x1F1A0A))
    case .done: return (SimColors.green.opacity(soft), StoryTone.done.ink(palette))
    case .next: return (palette.muted.opacity(0.08), palette.muted.opacity(0.6))
    case .answer: return (SimColors.answer, .white)
    case .stack, .operand: return (SimColors.blue, .white)
    case .waiting: return (palette.muted.opacity(0.1), palette.onSurface)
    case .prime: return (SimColors.green.opacity(soft), StoryTone.done.ink(palette))
    case .struckNow: return (SimColors.red.opacity(0.2), StoryTone.warn.ink(palette))
    case .struckEarlier: return (palette.muted.opacity(0.06), palette.muted.opacity(0.45))
    case .target: return (SimColors.red.opacity(0.14), StoryTone.warn.ink(palette))
    case .blank: return (palette.muted.opacity(0.05), .clear)
    case .raw: return (SimColors.red, .white)
    case .idle: return (palette.muted.opacity(0.22), palette.onSurface.opacity(0.75))
    }
}

private struct NumTableView: View {
    let table: NumTable
    @Environment(\.palette) private var palette

    var body: some View {
        VStack(spacing: 6) {
            HStack(spacing: 6) {
                Color.clear.frame(width: 22, height: 1)
                ForEach(table.headers.indices, id: \.self) { Text(table.headers[$0]).font(AppFont.mono(12)).foregroundStyle(palette.muted).frame(maxWidth: .infinity) }
            }
            ForEach(table.rows.indices, id: \.self) { r in
                HStack(spacing: 6) {
                    Text("\(r + 1)").font(AppFont.mono(13, r == table.current ? .bold : .regular))
                        .foregroundStyle(r == table.current ? StoryTone.active.ink(palette) : palette.muted).frame(width: 22, alignment: .leading)
                    ForEach(table.rows[r].indices, id: \.self) { c in
                        let cell = table.rows[r][c]
                        let (fill, ink) = numColors(cell.tone, palette)
                        Text(cell.text).font(AppFont.mono(16, .bold)).foregroundStyle(ink).lineLimit(1).minimumScaleFactor(0.6)
                            .frame(maxWidth: .infinity).frame(height: 42).background(fill, in: RoundedRectangle(cornerRadius: 9))
                    }
                }
            }
        }
    }
}

private struct NumRowsView: View {
    let rows: [NumRow]
    @Environment(\.palette) private var palette

    var body: some View {
        VStack(spacing: 6) {
            ForEach(rows.indices, id: \.self) { i in
                let row = rows[i]
                let (fill, ink): (Color, Color) = switch row.tone {
                case .current: (SimColors.active, Color(hex: 0x1F1A0A))
                case .answer: (SimColors.answer.opacity(palette.dark ? 0.3 : 0.2), StoryTone.answer.ink(palette))
                case .waiting: (palette.muted.opacity(0.1), palette.onSurface)
                default: (SimColors.green.opacity(palette.dark ? 0.22 : 0.16), StoryTone.done.ink(palette))
                }
                HStack(spacing: 8) {
                    Text(row.label).font(AppFont.mono(14, .bold)).lineLimit(1)
                    Spacer(minLength: 6)
                    Text(row.expr).font(AppFont.mono(12)).opacity(0.85).lineLimit(1).minimumScaleFactor(0.7)
                    Text(row.value).font(AppFont.mono(14, .bold)).lineLimit(1).frame(minWidth: 52, alignment: .trailing)
                }
                .foregroundStyle(ink)
                .padding(.horizontal, 14)
                .frame(height: 40)
                .background(fill, in: RoundedRectangle(cornerRadius: 9))
                .overlay { if row.tone == .waiting { RoundedRectangle(cornerRadius: 9).strokeBorder(SimColors.active, lineWidth: 1.5) } }
            }
        }
    }
}

private struct NumSieveView: View {
    let cells: [NumCell]
    @Environment(\.palette) private var palette

    var body: some View {
        VStack(spacing: 5) {
            ForEach(0..<(cells.count / 10), id: \.self) { r in
                HStack(spacing: 5) {
                    ForEach(0..<10, id: \.self) { c in
                        let cell = cells[r * 10 + c]
                        let (fill, ink) = numColors(cell.tone, palette)
                        Text(cell.text).font(AppFont.mono(13, .bold)).foregroundStyle(ink)
                            .strikethrough(cell.tone == .struckEarlier, color: palette.muted.opacity(0.6))
                            .lineLimit(1).minimumScaleFactor(0.7)
                            .frame(maxWidth: .infinity).frame(height: 32)
                            .background(fill, in: RoundedRectangle(cornerRadius: 6))
                            .overlay { if cell.tone == .target { RoundedRectangle(cornerRadius: 6).strokeBorder(SimColors.red, lineWidth: 1.5) } }
                    }
                }
            }
        }
    }
}

private struct NumClockView: View {
    let clock: NumClock
    @Environment(\.palette) private var palette

    var body: some View {
        Canvas { ctx, size in
            let m = clock.modulus
            let center = CGPoint(x: size.width / 2, y: size.height / 2)
            let radius = min(size.width, size.height) / 2 - 30
            func at(_ i: Int) -> CGPoint {
                let a = Double(i) / Double(m) * 2 * .pi - .pi / 2
                return CGPoint(x: center.x + radius * cos(a), y: center.y + radius * sin(a))
            }
            var ring = Path()
            ring.addEllipse(in: CGRect(x: center.x - radius, y: center.y - radius, width: 2 * radius, height: 2 * radius))
            ctx.stroke(ring, with: .color(palette.muted.opacity(0.3)), lineWidth: 1.5)
            if clock.path.count > 1 {
                var p = Path()
                p.move(to: at(clock.path[0]))
                clock.path.dropFirst().forEach { p.addLine(to: at($0)) }
                ctx.stroke(p, with: .color(SimColors.active), style: StrokeStyle(lineWidth: 2.5, lineCap: .round, dash: [2, 5]))
            }
            ctx.draw(ctx.resolve(Text("mod \(m)").font(AppFont.mono(13)).foregroundColor(palette.muted)), at: center)
            for i in 0..<m {
                let c = at(i)
                let tone = clock.tones[i] ?? .idle
                let (fill, ink): (Color, Color) = switch tone {
                case .operand: (SimColors.blue, .white)
                case .answer: (SimColors.answer, .white)
                case .raw: (SimColors.red, .white)
                default: (palette.muted.opacity(0.25), palette.onSurface.opacity(0.75))
                }
                let r: CGFloat = 16
                ctx.fill(Path(ellipseIn: CGRect(x: c.x - r, y: c.y - r, width: 2 * r, height: 2 * r)), with: .color(palette.surface))
                ctx.fill(Path(ellipseIn: CGRect(x: c.x - r, y: c.y - r, width: 2 * r, height: 2 * r)), with: .color(fill))
                ctx.draw(ctx.resolve(Text("\(i)").font(AppFont.mono(15, .bold)).foregroundColor(ink)), at: c)
                if let label = clock.labels[i] {
                    let labelInk = tone == .answer ? StoryTone.answer.ink(palette) : tone == .raw ? StoryTone.warn.ink(palette) : StoryTone.path.ink(palette)
                    let dx = c.x >= center.x - 1 ? 1.0 : -1.0
                    ctx.draw(ctx.resolve(Text(label).font(AppFont.mono(12, .bold)).foregroundColor(labelInk)),
                             at: CGPoint(x: c.x + dx * (r + 8), y: c.y), anchor: dx > 0 ? .leading : .trailing)
                }
            }
        }
    }
}
