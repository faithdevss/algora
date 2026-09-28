import SwiftUI

// Port of BitBoardSection.kt: integers as fixed-width rows of per-bit cells, with the decimal
// value beside each row and a caption naming the operation that produced it.

private enum BitMark { case zero, one, active, cleared, result, masked }

private struct BitCell { let value: Int; let mark: BitMark }
private struct BitRow { let label: String; let cells: [BitCell]; var readout: String? }
private struct BitFrame { let status: String; let rows: [BitRow]; var readout: String? }
private struct BitConfig { let intro: String; let legend: [(Color, String)]; let build: () -> [BitFrame] }

private let zeroFill = SimColors.wall
private let oneFill = SimColors.blue
private let clearedFill = SimColors.red
private let resultFill = Color(hex: 0x7C3AED)
private let maskedFill = Color(hex: 0x2A2F38)
private let width8 = 8

private func fill(_ m: BitMark) -> Color {
    switch m {
    case .zero: zeroFill
    case .one: oneFill
    case .active: SimColors.active
    case .cleared: clearedFill
    case .result: resultFill
    case .masked: maskedFill
    }
}

/// Most-significant bit first, so the row reads the way the number is written.
private func row(_ label: String, _ value: Int, width: Int = width8, active: Set<Int> = [], cleared: Set<Int> = [],
                 result: Set<Int> = [], masked: Set<Int> = []) -> BitRow {
    let cells = stride(from: width - 1, through: 0, by: -1).map { p -> BitCell in
        let bit = (value >> p) & 1
        let mark: BitMark = masked.contains(p) ? .masked : cleared.contains(p) ? .cleared : active.contains(p) ? .active
            : result.contains(p) ? .result : bit == 1 ? .one : .zero
        return BitCell(value: bit, mark: mark)
    }
    return BitRow(label: label, cells: cells, readout: "= \(value)")
}

private func setBits(_ v: Int, _ width: Int = width8) -> Set<Int> { Set((0..<width).filter { (v >> $0) & 1 == 1 }) }
private func binary(_ v: Int, _ width: Int) -> String {
    let s = String(v, radix: 2)
    return String(repeating: "0", count: max(0, width - s.count)) + s
}

private func bitBasics() -> [BitFrame] {
    var f: [BitFrame] = []
    let n = 44
    f.append(BitFrame(status: "\(n) in binary. Bit at position p is worth 2ᵖ, so the row is a place-value system with only two digits — and every trick below is a statement about places, not about arithmetic.",
                      rows: [row("n", n)], readout: "\(n) = 32 + 8 + 4"))
    for c in [44, 37] {
        let even = c & 1 == 0
        f.append(BitFrame(status: "n & 1 isolates the 1s place, and that place alone decides parity: \(c) & 1 = \(c & 1), so \(c) is \(even ? "even" : "odd"). It is the same answer as n % 2 and it is correct for negatives too, which n % 2 is not in Kotlin — (−3) % 2 is −1, but (−3) and 1 is 1.",
                          rows: [row("n", c, active: [0]), row("1", 1, active: [0]), row("n & 1", c & 1, result: [0])],
                          readout: "\(c) is \(even ? "even" : "odd")"))
    }
    for c in [16, 20] {
        let anded = c & (c - 1)
        let isPower = anded == 0
        f.append(BitFrame(status: "Subtracting 1 flips the lowest set bit to 0 and turns everything below it into 1s. So n & (n−1) always clears exactly that lowest set bit: \(c) & \(c - 1) = \(anded). " + (isPower ? "Zero, so \(c) had only one set bit — it is a power of two." : "Non-zero, so \(c) had more than one set bit and is not a power of two."),
                          rows: [row("n", c), row("n − 1", c - 1), row("n & (n−1)", anded, result: isPower ? [] : [anded.trailingZeroBitCount])],
                          readout: isPower ? "\(c) is a power of two" : "\(c) is not a power of two"))
    }
    let v = 44
    f.append(BitFrame(status: "The other two everyday isolations. n & −n keeps only the lowest set bit — because −n is the bitwise complement plus one, which leaves that bit and nothing else agreeing. n | (n−1) fills every bit below the lowest set one.",
                      rows: [row("n", v), row("n & −n", v & -v, result: [v.trailingZeroBitCount]), row("n | (n−1)", v | (v - 1))],
                      readout: "lowest set bit of \(v) is \(v & -v)"))
    f.append(BitFrame(status: "All of it is one machine instruction each: no division, no branch, no loop. That is the entire reason these appear in hot paths — and the entire reason they should not appear anywhere else, since \"n & 1\" says nothing about parity to a reader while \"n % 2 == 0\" does.",
                      rows: [row("n", v), row("n & 1", v & 1), row("n & (n−1)", v & (v - 1)), row("n & −n", v & -v)], readout: "each one instruction"))
    return f
}

private func countSetBits() -> [BitFrame] {
    let n = 156
    var f: [BitFrame] = []
    f.append(BitFrame(status: "Count the 1s in \(n). The naive loop tests every position regardless of what is there; Brian Kernighan's loop runs once per set bit. On this value that is \(width8) iterations against \(n.nonzeroBitCount).",
                      rows: [row("n", n)], readout: "\(n) = \(binary(n, width8))"))
    var naive = 0
    for p in stride(from: width8 - 1, through: 0, by: -1) {
        let bit = (n >> p) & 1
        if bit == 1 { naive += 1 }
        f.append(BitFrame(status: "Naive pass, position \(p): bit is \(bit), running count \(naive). This iteration happens whether or not there is anything there — the loop is driven by the width, not by the value.",
                          rows: [row("n", n, active: [p], masked: p > 0 ? Set(0..<p) : [])], readout: "count \(naive) after \(width8 - p) of \(width8) iterations"))
    }
    var value = n, k = 0
    while value != 0 {
        let lowest = value.trailingZeroBitCount
        let next = value & (value - 1)
        k += 1
        f.append(BitFrame(status: "Kernighan iteration \(k): n & (n−1) clears the lowest set bit, at position \(lowest). \(value) becomes \(next). Nothing was examined — the arithmetic located the bit.",
                          rows: [row("n", value, active: [lowest]), row("n − 1", value - 1), row("n & (n−1)", next, cleared: [lowest])], readout: "count \(k)"))
        value = next
    }
    f.append(BitFrame(status: "Both say \(n.nonzeroBitCount). The naive loop took \(width8) iterations and would take 32 on an Int; Kernighan took \(k), one per set bit. On sparse values that is the whole difference, and on dense ones it is none. In production neither is what you write: Integer.bitCount compiles to a single POPCNT instruction.",
                      rows: [row("n", n, result: setBits(n))], readout: "popcount = \(n.nonzeroBitCount) · naive \(width8) steps · Kernighan \(k)"))
    return f
}

private func subsetsBitmask() -> [BitFrame] {
    let items = ["a", "b", "c"]
    let w = items.count
    var f: [BitFrame] = []
    f.append(BitFrame(status: "Every subset of a \(w)-element set is a \(w)-bit number: bit i means \"item \(items.joined(separator: "/")) at index i is in\". There are 2^\(w) = \(1 << w) of them, and counting from 0 to \((1 << w) - 1) enumerates them all exactly once.",
                      rows: [BitRow(label: "bit i ⇒ item", cells: items.map { _ in BitCell(value: 0, mark: .zero) }, readout: items.reversed().joined(separator: " "))], readout: "\(1 << w) subsets"))
    for mask in 0..<(1 << w) {
        let chosen = items.indices.filter { (mask >> $0) & 1 == 1 }.map { items[$0] }
        let text = chosen.isEmpty ? "{ }" : "{ \(chosen.joined(separator: ", ")) }"
        f.append(BitFrame(status: "mask = \(mask): \(chosen.isEmpty ? "the empty subset" : text). The loop body is `if (mask shr i and 1 == 1)` — no recursion, no visited set, and the mask is itself a usable array index, which is what makes it a DP state.",
                          rows: [row("mask", mask, width: w, result: setBits(mask, w))], readout: text))
    }
    let full = (1 << w) - 1, sub = 5, comp = full ^ sub
    f.append(BitFrame(status: "Set operations become one instruction each: union is |, intersection is &, complement is xor with the full mask, and membership is a shift and an &. Here mask \(sub) is { \(items.indices.filter { (sub >> $0) & 1 == 1 }.map { items[$0] }.joined(separator: ", ")) }, and its complement against \(full) is \(comp).",
                      rows: [row("subset", sub, width: w), row("full", full, width: w), row("complement", comp, width: w, result: setBits(comp, w))], readout: "union | · intersect & · complement xor"))
    f.append(BitFrame(status: "The ceiling is the exponent, not the encoding: 2ⁿ masks is fine to about n = 20 and hopeless at n = 40. That is exactly the boundary Held-Karp lives on — a bitmask DP over subsets is O(2ⁿ·n²), which beats n! and is still exponential.",
                      rows: [row("all masks", full, width: w, result: Set(items.indices))], readout: "2^20 ≈ 10⁶ · 2^40 ≈ 10¹²"))
    return f
}

private func xorTricks() -> [BitFrame] {
    var f: [BitFrame] = []
    let a = 44, b = 25
    f.append(BitFrame(status: "XOR is 1 where the operands differ. That single property gives it the two features everything below uses: x xor x = 0, and x xor 0 = x. It is its own inverse.",
                      rows: [row("a", a), row("b", b), row("a xor b", a ^ b, result: setBits(a ^ b))], readout: "\(a) xor \(b) = \(a ^ b)"))
    f.append(BitFrame(status: "Applying the same value twice undoes it: (\(a) xor \(b)) xor \(b) = \((a ^ b) ^ b), back to a. That is the whole basis of one-time-pad encryption and of the swap below.",
                      rows: [row("a xor b", a ^ b), row("b", b), row("xor b again", (a ^ b) ^ b, result: setBits(a))], readout: "recovered a = \((a ^ b) ^ b)"))
    let stream = [4, 1, 2, 1, 2]
    var acc = 0
    f.append(BitFrame(status: "The classic use: in \(stream.map(String.init).joined(separator: ", ")) every value appears twice except one. XOR the whole list — pairs cancel to 0 and the odd one out survives, in O(n) time and O(1) space with no hash set and no sorting.",
                      rows: [row("accumulator", 0)], readout: "start at 0"))
    for v in stream {
        let before = acc
        acc ^= v
        let cancelled = stream.filter { $0 == v }.count == 2 && before != 0 && acc < before
        f.append(BitFrame(status: "xor \(v): \(before) → \(acc)." + (cancelled ? " A pair just cancelled." : ""),
                          rows: [row("accumulator", before), row("value", v), row("result", acc, result: setBits(acc))], readout: "accumulator = \(acc)"))
    }
    f.append(BitFrame(status: "The survivor is \(acc) — order never mattered, because XOR is commutative and associative. Note what this does not do: it finds the element appearing an odd number of times, so it is wrong the moment the premise \"exactly twice\" is.",
                      rows: [row("answer", acc, result: setBits(acc))], readout: "single number = \(acc)"))
    let array = [3, 8, 2, 6, 4]
    var prefix = [Int](repeating: 0, count: array.count + 1)
    for i in array.indices { prefix[i + 1] = prefix[i] ^ array[i] }
    let l = 1, r = 3
    let via = prefix[r + 1] ^ prefix[l]
    f.append(BitFrame(status: "Because XOR is its own inverse it also supports prefix arrays, exactly like a prefix sum but with subtraction replaced by XOR itself. Over \(array.map(String.init).joined(separator: ", ")), the XOR of positions \(l)..\(r) is prefix[\(r + 1)] xor prefix[\(l)] = \(prefix[r + 1]) xor \(prefix[l]) = \(via) — two lookups, and the range width never enters the cost.",
                      rows: [row("prefix[\(r + 1)]", prefix[r + 1]), row("prefix[\(l)]", prefix[l]), row("range xor", via, result: setBits(via))], readout: "a[\(l)..\(r)] xor = \(via)"))
    return f
}

private func twoSingles() -> [BitFrame] {
    let a = [4, 1, 2, 1, 3, 2]
    var f: [BitFrame] = []
    var acc = 0
    f.append(BitFrame(status: "Every value appears twice except two of them. No hash set is allowed, so the only tool left is XOR's self-inverse property: x ^ x = 0.",
                      rows: [row("acc", 0)], readout: "input: \(a.map(String.init).joined(separator: ", "))"))
    for x in a {
        let before = acc
        acc ^= x
        f.append(BitFrame(status: "acc ^= \(x). Bits where the two agreed cancel to 0; bits where they differed survive.",
                          rows: [row("acc", before), row("x", x, active: setBits(x)), row("acc ^ x", acc, result: setBits(acc))], readout: nil))
    }
    let lowest = acc & -acc
    let bit = lowest.trailingZeroBitCount
    f.append(BitFrame(status: "The pairs are gone, so acc = \(acc) is p ^ q for the two unpaired values. It cannot be 0 — they differ somewhere — and n & −n isolates the lowest place where they differ, bit \(bit).",
                      rows: [row("acc = p^q", acc), row("−acc", -acc & ((1 << width8) - 1)), row("acc & −acc", lowest, result: [bit])], readout: "split on bit \(bit) (value \(1 << bit))"))
    var with = 0, without = 0
    for x in a { if x & lowest != 0 { with ^= x } else { without ^= x } }
    f.append(BitFrame(status: "Split the input on bit \(bit). Each duplicate pair lands wholly in one group, and p and q land in different ones — so XOR-ing each group separately leaves exactly one value in each.",
                      rows: [row("group with bit", with, result: setBits(with)), row("group without", without, result: setBits(without))], readout: "answers: \(with) and \(without) — two passes, O(1) space"))
    return f
}

private let xorNumbers = [3, 10, 5, 25, 2, 8]
private let xorWidth = 5

private func bitTriePattern() -> [BitFrame] {
    var f: [BitFrame] = []
    f.append(BitFrame(status: "Find the pair with the largest XOR among \(xorNumbers.map(String.init).joined(separator: ", ")). Every pair is O(n²); a binary trie of the values, most significant bit first, answers each query in \(xorWidth) steps.",
                      rows: xorNumbers.map { row(String(format: "%2d", $0), $0, width: xorWidth) }, readout: "\(xorNumbers.count) values, \(xorWidth) bits each"))
    let query = 5
    var partner = 0, best = 0
    let fullMask = (1 << xorWidth) - 1
    for bit in stride(from: xorWidth - 1, through: 0, by: -1) {
        let qb = (query >> bit) & 1
        let wanted = 1 - qb
        let prefixMask = (-1 << bit) & fullMask
        let candidates = xorNumbers.filter { ($0 & prefixMask) == ((partner & (prefixMask << 1)) | (wanted << bit)) }
        let taken = !candidates.isEmpty
        partner |= (taken ? wanted : qb) << bit
        best = query ^ partner
        f.append(BitFrame(status: "Bit \(bit) of the query is \(qb), so the branch worth \(1 << bit) is the one holding \(wanted). " + (taken
                            ? "The trie has \(candidates.count) value(s) down that branch (\(candidates.map(String.init).joined(separator: ", "))), so take it — that bit of the answer is now guaranteed 1."
                            : "Nothing is stored down that branch, so the walk is forced into the \(qb) side and this bit of the answer is 0. Being forced never undoes a higher bit already won."),
                          rows: [row("query \(query)", query, width: xorWidth, active: [bit]), row("partner", partner, width: xorWidth, result: [bit]), row("xor", best, width: xorWidth, result: [bit])],
                          readout: "best so far = \(best)"))
    }
    let brute = xorNumbers.flatMap { a in xorNumbers.map { a ^ $0 } }.max()!
    f.append(BitFrame(status: "Greedy from the top is optimal because one high bit outweighs every lower bit combined: \(1 << (xorWidth - 1)) > \((1 << (xorWidth - 1)) - 1). Query \(query) pairs best with \(partner) for \(best); over all queries the maximum is \(brute). n insertions and n queries, \(xorWidth) steps each — O(n · bits) instead of O(n²).",
                      rows: [row("query \(query)", query, width: xorWidth), row("partner", partner, width: xorWidth), row("xor", best, width: xorWidth, result: setBits(best, xorWidth))], readout: "max XOR = \(brute)"))
    return f
}

private let tspCities = ["A", "B", "C", "D"]
private let tspCost = [[0, 5, 9, 4], [5, 0, 3, 8], [9, 3, 0, 6], [4, 8, 6, 0]]

private func bitmaskStatePattern() -> [BitFrame] {
    let n = tspCities.count, full = (1 << n) - 1
    let big = Int.max / 4
    var f: [BitFrame] = []
    var dp = [[Int]](repeating: [Int](repeating: big, count: n), count: 1 << n)
    dp[1][0] = 0
    func members(_ m: Int) -> [Int] { tspCities.indices.filter { (m >> $0) & 1 == 1 } }
    let fact = (1...n).reduce(1, *)
    f.append(BitFrame(status: "Visit all \(n) cities once, starting at \(tspCities[0]). The state that matters is *which* cities are visited plus where you are — not the order you visited them in. That set is a \(n)-bit mask, and the mask doubles as the dp array index.",
                      rows: [row("mask", 1, width: n, result: [0])], readout: "\(1 << n) masks × \(n) positions = \((1 << n) * n) states"))
    for mask in 1...full where mask & 1 == 1 {
        for last in members(mask) where dp[mask][last] < big {
            for next in tspCities.indices where (mask >> next) & 1 == 0 {
                let nm = mask | (1 << next)
                dp[nm][next] = min(dp[nm][next], dp[mask][last] + tspCost[last][next])
            }
        }
        let reachable = members(mask).filter { dp[mask][$0] < big }
        if reachable.isEmpty { continue }
        f.append(BitFrame(status: "mask \(mask) = { \(members(mask).map { tspCities[$0] }.joined(separator: ", ")) }. Best cost per ending city: \(reachable.map { "\(tspCities[$0]) \(dp[mask][$0])" }.joined(separator: ", ")). Every route reaching this same set collapses into these \(n) numbers — that collapse is what turns \(fact) orderings into \((1 << n) * n) states.",
                          rows: [row("mask", mask, width: n, result: Set(members(mask)))], readout: "visited \(members(mask).count) of \(n)"))
    }
    let best = tspCities.indices.filter { $0 != 0 }.map { dp[full][$0] + tspCost[$0][0] }.min()!
    f.append(BitFrame(status: "Full mask \(full) — every city visited. Closing the tour back to \(tspCities[0]) costs \(best). O(2ⁿ · n²) is still exponential, but it is the difference between \(fact) permutations at n = \(n) and 20! ≈ 2.4 × 10¹⁸ at n = 20, where the mask version is merely expensive.",
                      rows: [row("mask", full, width: n, result: Set(tspCities.indices))], readout: "optimal tour = \(best)"))
    return f
}

private let bitConfigs: [String: BitConfig] = [
    "bit_trie_pattern": BitConfig(intro: "Maximum XOR through a binary trie: walk the query's bits from the top and take the opposite branch whenever one exists. One high bit outweighs every lower bit, so greedy is optimal.",
                                  legend: [(oneFill, "Set bit"), (SimColors.active, "Bit being decided"), (resultFill, "Answer bit")], build: bitTriePattern),
    "bitmask_state_pattern": BitConfig(intro: "Held-Karp on four cities. The mask is the visited set *and* the dp index, which is the whole trick — routes that visit the same set collapse into one state.",
                                       legend: [(oneFill, "Visited"), (resultFill, "In this state"), (maskedFill, "Outside the width")], build: bitmaskStatePattern),
    "bit_manipulation_pattern": BitConfig(intro: "Two values appear once, everything else twice. XOR collapses the pairs, then the lowest set bit of the result splits the input into two groups that each hide exactly one answer.",
                                          legend: [(oneFill, "Set bit"), (SimColors.active, "Incoming value"), (resultFill, "Result")], build: twoSingles),
    "bit_basics": BitConfig(intro: "One integer as eight place-value cells. Parity, powers of two and the n & (n−1) trick are all statements about which places are occupied — the arithmetic is incidental.",
                            legend: [(oneFill, "Set bit"), (SimColors.active, "Being tested"), (resultFill, "Answer")], build: bitBasics),
    "count_set_bits": BitConfig(intro: "The width-driven loop and Brian Kernighan's value-driven loop on the same number, so the difference is a step count rather than a claim.",
                                legend: [(oneFill, "Set bit"), (SimColors.active, "Current position"), (clearedFill, "Just cleared")], build: countSetBits),
    "subsets_bitmask": BitConfig(intro: "Counting from 0 to 2ⁿ−1 enumerates every subset exactly once. Each mask is both the subset and a usable array index, which is what makes it a DP state rather than just an encoding.",
                                 legend: [(oneFill, "Item present"), (resultFill, "In this subset"), (maskedFill, "Outside the width")], build: subsetsBitmask),
    "xor_tricks": BitConfig(intro: "XOR is its own inverse, and that one fact produces the swap, the single-number scan and a prefix array that answers range queries in constant time.",
                            legend: [(oneFill, "Set bit"), (resultFill, "Result"), (zeroFill, "Cancelled to 0")], build: xorTricks),
]

struct BitBoardLab: View {
    private let config: BitConfig
    private let frames: [BitFrame]
    @State private var playback: PlaybackState
    @Environment(\.palette) private var palette

    init(topicId: String) {
        config = bitConfigs[topicId] ?? bitConfigs["bit_basics"]!
        frames = config.build()
        _playback = State(initialValue: PlaybackState(stepCount: frames.count, speedMs: 750))
    }

    var body: some View {
        let frame = frames[min(playback.index, frames.count - 1)]
        LabCard {
            LabIntro(text: config.intro)
            ForEach(Array(frame.rows.enumerated()), id: \.offset) { _, bitRow in
                HStack(spacing: 0) {
                    Text(bitRow.label).font(AppFont.sans(11)).foregroundStyle(palette.muted)
                        .lineLimit(1).minimumScaleFactor(0.7).frame(width: 72, alignment: .leading)
                    HStack(spacing: 3) {
                        ForEach(Array(bitRow.cells.enumerated()), id: \.offset) { _, cell in
                            Text("\(cell.value)")
                                .font(.system(size: 11, weight: .bold, design: .monospaced))
                                .foregroundStyle(cell.mark == .active ? Color(hex: 0x1F2530) : .white)
                                .frame(maxWidth: .infinity).frame(height: 30)
                                .background(fill(cell.mark), in: RoundedRectangle(cornerRadius: 5))
                        }
                    }
                    if let r = bitRow.readout {
                        Text(r).font(.system(size: 11, weight: .bold, design: .monospaced))
                            .lineLimit(1).minimumScaleFactor(0.6)
                            .frame(width: 56, alignment: .leading).padding(.leading, 8)
                    }
                }
                .padding(.top, 10)
            }
            if let r = frame.readout { Text(r).font(AppFont.sans(15, .bold)).padding(.top, 12) }
            LabCaption(text: frame.status).padding(.top, 14)
            FlowLayout(spacing: 12, lineSpacing: 6) {
                ForEach(Array(config.legend.enumerated()), id: \.offset) { _, item in LegendDot(color: item.0, label: item.1) }
            }
            .padding(.top, 10)
            PlaybackTransport(state: playback, captions: frames.map(\.status))
        }
    }
}
