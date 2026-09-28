import Foundation

// ArrayWalkSection.kt builders: math & number theory.

private func csv(_ xs: [Int], _ sep: String = ", ") -> String { xs.map(String.init).joined(separator: sep) }

// The remainder chain as a growing row, then the back-substitution that turns it into Bézout coefficients.
func euclidGcdFrames() -> [WalkFrame] {
    let a0 = 252, b0 = 105
    var frames: [WalkFrame] = []
    var chain = [a0, b0]
    var steps: [(Int, Int, Int)] = [] // (dividend, quotient, remainder)

    frames.append(WalkFrame(
        status: "gcd(\(a0), \(b0)). The identity that drives everything: gcd(a, b) = gcd(b, a mod b) — both pairs have " +
            "exactly the same common divisors, not merely the same greatest one.",
        cells: chain.map { CellView("\($0)", .active) }))

    var a = a0, b = b0
    while b != 0 {
        let q = a / b, r = a % b
        steps.append((a, q, r))
        chain.append(r)
        let last = chain.count - 1
        frames.append(WalkFrame(
            status: "\(a) = \(q) × \(b) + \(r)." + (r == 0 ? " Remainder zero — the previous value, \(b), is the answer." : " Replace the pair with (\(b), \(r)) and repeat."),
            cells: chain.enumerated().map { i, v in
                CellView("\(v)", i == last ? (r == 0 ? .dim : .active) : i == last - 1 ? (r == 0 ? .result : .window) : .dim)
            },
            readout: "step \(steps.count)"))
        a = b
        b = r
    }
    let g = a

    frames.append(WalkFrame(
        status: "gcd = \(g) in \(steps.count) divisions. Lamé's bound is five times the digit count of the smaller input, and the " +
            "worst case is a pair of consecutive Fibonacci numbers — every remainder then lands exactly on the previous one.",
        cells: chain.map { CellView("\($0)", $0 == g ? .result : .dim) }, readout: "gcd(\(a0), \(b0)) = \(g)"))
    frames.append(WalkFrame(
        status: "lcm = \(a0) / \(g) × \(b0) = \(a0 / g * b0). Divide before multiplying: \(a0) × \(b0) can overflow for inputs whose " +
            "lcm comfortably would not.",
        cells: chain.map { CellView("\($0)", .dim) }, readout: "lcm(\(a0), \(b0)) = \(a0 / g * b0)"))

    var x = 1, y = 0
    for (dividend, q, _) in steps.reversed() {
        let newX = y, newY = x - q * y
        x = newX
        y = newY
        frames.append(WalkFrame(
            status: "Back-substitute through \(dividend) = \(q) × … : \(g) = \(a0) × \(x) + \(b0) × \(y)." +
                (x == 1 && y == 0 ? "" : " Check: \(a0 * x) + \(b0 * y) = \(a0 * x + b0 * y)."),
            cells: chain.enumerated().map { i, v in CellView("\(v)", i == chain.count - 2 ? .result : .dim) },
            readout: "\(a0)·\(x) + \(b0)·\(y) = \(a0 * x + b0 * y)"))
    }

    frames.append(WalkFrame(
        status: "Bézout: \(a0) × \(x) + \(b0) × \(y) = \(g). Those coefficients are the whole reason the extended version exists — " +
            "when gcd is 1 the coefficient of a is a's modular inverse, and that is where CRT and RSA key generation start.",
        cells: chain.map { CellView("\($0)", $0 == g ? .result : .dim) }, readout: "x = \(x), y = \(y)"))
    return frames
}

// Residue rows: whether 1 ever appears in the multiples row is exactly whether a is invertible.
func modularArithmeticFrames() -> [WalkFrame] {
    var frames: [WalkFrame] = []
    func residueRow(_ m: Int) -> [CellView] { (0..<m).map { CellView("\($0)", .idle) } }
    func multiplesRow(_ a: Int, _ m: Int, highlightOne: Bool) -> [CellView] {
        (0..<m).map { k in
            let v = a * k % m
            return CellView("\(v)", highlightOne && v == 1 ? .result : v == 0 ? .dim : .window)
        }
    }
    func marked(_ m: Int, _ at: Int, _ mark: CellMark) -> [CellView] {
        residueRow(m).enumerated().map { i, c in i == at ? CellView(c.text, mark) : c }
    }
    let neg = ((-6 % 7) + 7) % 7

    frames.append(WalkFrame(
        status: "Working mod 7, every integer collapses onto one of seven residues. 17 ≡ 3 and 23 ≡ 2, because each differs " +
            "from its residue by a multiple of 7.",
        cells: residueRow(7), pointers: [3: "17", 2: "23"]))
    frames.append(WalkFrame(
        status: "Addition survives the collapse: 17 + 23 = 40 ≡ \(40 % 7), and (3 + 2) mod 7 = \((3 + 2) % 7). They agree, which is " +
            "what lets you reduce at every step instead of at the end.",
        cells: marked(7, 40 % 7, .result), readout: "17 + 23 ≡ \(40 % 7) (mod 7)"))
    frames.append(WalkFrame(
        status: "Multiplication too: 17 × 23 = 391 ≡ \(391 % 7), and (3 × 2) mod 7 = \(3 * 2 % 7).",
        cells: marked(7, 391 % 7, .result), readout: "17 × 23 ≡ \(391 % 7) (mod 7)"))
    frames.append(WalkFrame(
        status: "Subtraction survives mathematically but not in Kotlin: 17 − 23 = −6, and −6 % 7 is −6, because % takes the " +
            "dividend's sign. The residue wanted is \(neg), so normalise with ((a % m) + m) % m.",
        cells: marked(7, neg, .result), readout: "−6 % 7 = −6 in code · \(neg) in maths"))
    frames.append(WalkFrame(
        status: "Division is the operation that does not survive. Dividing by 3 means multiplying by some 3⁻¹ with 3 × 3⁻¹ ≡ 1. " +
            "The row below is 3k mod 7 for k = 0 … 6 — the question is whether 1 appears in it.",
        cells: residueRow(7), aux: multiplesRow(3, 7, highlightOne: false), auxLabel: "3k mod 7"))
    frames.append(WalkFrame(
        status: "It does, at k = 5: 3 × 5 = 15 = 2×7 + 1. So 3⁻¹ ≡ 5 (mod 7). The row hits every residue exactly once, which is " +
            "what gcd(3, 7) = 1 guarantees.",
        cells: marked(7, 5, .active), pointers: [5: "k"], aux: multiplesRow(3, 7, highlightOne: true), auxLabel: "3k mod 7",
        readout: "3⁻¹ ≡ 5 (mod 7)"))
    frames.append(WalkFrame(
        status: "Now mod 4, with a = 2. gcd(2, 4) = 2, and the row is 2k mod 4 — every entry is even, so 1 never appears and 2 has " +
            "no inverse at all. An inverse exists exactly when gcd(a, m) = 1; this is that condition made visible.",
        cells: residueRow(4), aux: multiplesRow(2, 4, highlightOne: true), auxLabel: "2k mod 4", readout: "no inverse: gcd(2, 4) = 2"))
    return frames
}

func sieveFrames() -> [WalkFrame] {
    let n = 30
    var frames: [WalkFrame] = []
    var isPrime = (0...n).map { $0 >= 2 }
    let split = 16

    func rowFor(_ range: ClosedRange<Int>, _ active: Set<Int>, _ struck: Set<Int>) -> [CellView] {
        range.map { v in CellView("\(v)", active.contains(v) ? .active : struck.contains(v) ? .result : !isPrime[v] ? .dim : .window) }
    }
    func frame(_ status: String, active: Set<Int> = [], struck: Set<Int> = [], readout: String? = nil) {
        frames.append(WalkFrame(status: status, cells: rowFor(2...split, active, struck), aux: rowFor((split + 1)...n, active, struck),
                                auxLabel: "\(split + 1) … \(n)", readout: readout))
    }

    frame("Everything from 2 to \(n) starts as a candidate. Nothing here will ever be divided — composites are removed by marking.")

    var p = 2
    while p * p <= n {
        if isPrime[p] {
            var struck = Set<Int>()
            var multiple = p * p
            while multiple <= n {
                if isPrime[multiple] { struck.insert(multiple) }
                isPrime[multiple] = false
                multiple += p
            }
            frame("\(p) survives, so it is prime. Strike its multiples starting at \(p * p) — not at \(2 * p), because every k·\(p) with " +
                  "k < \(p) was already removed when k's own prime factors were processed. " +
                  (struck.isEmpty ? "Nothing new was left to strike." : "Struck \(csv(struck.sorted()))."),
                  active: [p], struck: struck, readout: "p = \(p), marking from \(p * p)")
        }
        p += 1
    }

    let primes = (2...n).filter { isPrime[$0] }
    frame("The outer loop stopped at p·p > \(n), so only \(csv((2...n).filter { isPrime[$0] && $0 * $0 <= n })) ever marked " +
          "anything — a composite below \(n) must have a factor at most √\(n). Everything still standing is prime: \(csv(primes)).",
          readout: "\(primes.count) primes below \(n) · O(n log log n)")
    return frames
}

func fermatFrames() -> [WalkFrame] {
    let p = 7
    var frames: [WalkFrame] = []
    func powMod(_ a: Int, _ e: Int) -> Int { var v = 1; for _ in 0..<e { v = v * a % p }; return v }

    func powerRow(_ a: Int, _ upTo: Int) -> [CellView] {
        (1...(p - 1)).map { e in
            let v = powMod(a, e)
            return CellView(e <= upTo ? "\(v)" : "·", e > upTo ? .dim : v == 1 && e == p - 1 ? .result : e == upTo ? .active : .window)
        }
    }

    frames.append(WalkFrame(
        status: "Fermat's claim: for a prime p and any a it does not divide, a^(p−1) ≡ 1 (mod \(p)). The row is the exponent, " +
            "1 through \(p - 1).",
        cells: (1...(p - 1)).map { CellView("\($0)", .idle) }, aux: powerRow(3, 0), auxLabel: "3^e mod \(p)"))
    for e in 1...(p - 1) {
        let v = powMod(3, e)
        frames.append(WalkFrame(
            status: "3^\(e) ≡ \(v) (mod \(p))." + (e == p - 1 ? " There it is — and note the row hit every non-zero residue exactly once on the way, which makes 3 a primitive root mod \(p)." : ""),
            cells: (1...(p - 1)).map { CellView("\($0)", $0 == e ? .active : $0 < e ? .window : .idle) },
            pointers: [e - 1: "e"], aux: powerRow(3, e), auxLabel: "3^e mod \(p)", readout: "3^\(e) ≡ \(v)"))
    }
    frames.append(WalkFrame(
        status: "Base 2 lands on 1 every three steps rather than every six, but still lands on 1 at exponent \(p - 1) — an element's " +
            "order always divides p − 1, which is why the theorem holds for every base at once.",
        cells: (1...(p - 1)).map { CellView("\($0)", .dim) }, aux: powerRow(2, p - 1), auxLabel: "2^e mod \(p)",
        readout: "order of 2 is 3, and 3 divides \(p - 1)"))

    let inverse = powMod(3, p - 2)
    frames.append(WalkFrame(
        status: "Drop the exponent by one and you have division: 3^\(p - 2) ≡ \(inverse), and 3 × \(inverse) = \(3 * inverse) ≡ \(3 * inverse % p) (mod \(p)). " +
            "That is how a fraction is computed \"mod 10⁹ + 7\" — the modulus is prime, so every non-zero residue is invertible.",
        cells: (1...(p - 1)).map { CellView("\($0)", $0 == p - 2 ? .result : .dim) }, aux: powerRow(3, p - 1), auxLabel: "3^e mod \(p)",
        readout: "3⁻¹ ≡ \(inverse) (mod \(p))"))

    let carmichael = 561
    let factors = [3, 11, 17]
    frames.append(WalkFrame(
        status: "Reversed, this is a primality test: a^(n−1) ≢ 1 proves n composite. But it never proves the converse. " +
            "\(carmichael) = \(csv(factors, " × ")) passes for every base coprime to it, because it is squarefree and " +
            "\(factors.map { "\($0 - 1) | \(carmichael - 1)" }.joined(separator: ", ")). Carmichael numbers are infinite in supply, so " +
            "Miller-Rabin — which also checks square roots of 1 — is what actually gets used.",
        cells: factors.map { CellView("\($0)", .result) } + [CellView("= \(carmichael)", .dim)],
        readout: "561 is composite and passes every Fermat round"))
    return frames
}

// The three congruences as successive filters over 0 … 31, so the unique survivor is watched appearing.
func crtFrames() -> [WalkFrame] {
    let remainders = [2, 3, 2]
    let moduli = [3, 5, 7]
    let product = moduli.reduce(1, *)
    let shown = 32, split = 15
    var frames: [WalkFrame] = []
    var alive = Set(0..<shown)

    func rowFor(_ range: [Int], _ live: Set<Int>, _ justCut: Set<Int>) -> [CellView] {
        range.map { v in
            CellView("\(v)", justCut.contains(v) ? .dim : live.count == 1 && live.contains(v) ? .result : live.contains(v) ? .window : .dim)
        }
    }
    func frame(_ status: String, _ live: Set<Int>, _ justCut: Set<Int> = [], readout: String? = nil) {
        frames.append(WalkFrame(status: status, cells: rowFor(Array(0...split), live, justCut),
                                aux: rowFor(Array((split + 1)..<shown), live, justCut), auxLabel: "\(split + 1) … \(shown - 1)", readout: readout))
    }

    frame("Sunzi's puzzle: a number leaving remainder 2 under 3, 3 under 5 and 2 under 7. The moduli are pairwise coprime, so a " +
          "solution exists and is unique modulo 3 × 5 × 7 = \(product).", alive)

    for i in moduli.indices {
        let survivors = alive.filter { $0 % moduli[i] == remainders[i] }
        let cut = alive.subtracting(survivors)
        alive = survivors
        frame("x ≡ \(remainders[i]) (mod \(moduli[i])) leaves \(csv(alive.sorted())). " +
              (alive.count == 1 ? "One survivor below \(product), exactly as the theorem promises." : "\(alive.count) candidates remain in this window."),
              alive, cut, readout: "after \(i + 1) congruence\(i == 0 ? "" : "s"): \(alive.count) left")
    }

    let answer = alive.first!
    let partials = moduli.map { product / $0 }
    let inverses = moduli.enumerated().map { i, m in (1..<m).first { partials[i] % m * $0 % m == 1 }! }
    let terms = remainders.indices.map { remainders[$0] * partials[$0] * inverses[$0] }
    let total = terms.reduce(0, +)

    frame("The construction gets there without scanning. Mᵢ = \(product) / mᵢ is \(csv(partials)) — each divisible by every " +
          "modulus but its own — and multiplying by Mᵢ⁻¹ mod mᵢ (\(csv(inverses))) makes each term 1 in its own modulus and 0 " +
          "in the others. \(csv(terms, " + ")) = \(total), and \(total) mod \(product) = \(total % product).",
          alive, readout: "x ≡ \(answer) (mod \(product))")
    frame("Coprimality is doing all the work. With moduli 4 and 6, x ≡ 1 (mod 4) and x ≡ 2 (mod 6) have no solution at all: the first " +
          "forces x odd, the second forces it even. The general form is solvable exactly when the remainders agree modulo each pair's gcd.",
          alive, readout: "unique mod \(product) · \(answer), 128, 233, …")
    return frames
}
