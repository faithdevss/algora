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

// MARK: - Bit story labs
// Port of BitStoryLab.kt. Bit Basics, Count Set Bits, XOR Tricks and Subsets as tabbed storyboards:
// rows of bit cells under a place-value header, the value beside each row, the operation with its
// numbers underneath, then chips and a headline. Result rows and their values take the violet.

private enum SBitTone { case zero, one, lowest, result, pending, member }
private struct SBitCell { let text: String; let tone: SBitTone }
private struct SBitRow { let label: String; let cells: [SBitCell]; let readout: String; var result = false }
private enum MaskTone { case listed, current, pending }
private struct MaskTile { let top: String; let bottom: String; let tone: MaskTone }

private struct SBitFrame {
    var headers = (0..<8).map { "\(7 - $0)" }
    /// Header indices lit in yellow (the position being tested or isolated).
    var lit: Set<Int> = []
    let rows: [SBitRow]
    var formula: String? = nil
    var formulaRows: [StoryFormulaRow] = []
    var masks: [MaskTile] = []
    var chips: [StoryChip] = []
    let headline: String
    let body: String
}

private struct SBitTab { let label: String; let frames: [SBitFrame]; let legend: [(Color, SwatchStyle, String)] }

private let sbDim = Color.gray.opacity(0.35)

private func bin8(_ n: Int) -> String { String((0..<8).map { (n >> (7 - $0)) & 1 == 1 ? "1" : "0" }) }

/// An 8-bit row, most significant bit first. Result rows paint their 1s violet; `lowest` is yellow.
private func sbRow(_ label: String, _ n: Int, readout: String? = nil, result: Bool = false, lowest: Int? = nil,
                   pending: ((Int) -> Bool)? = nil) -> SBitRow {
    let v = n & 0xFF
    let cells = (0..<8).map { i -> SBitCell in
        let pos = 7 - i
        let bit = (v >> pos) & 1
        if pending?(pos) == true { return SBitCell(text: "\(bit)", tone: .pending) }
        if pos == lowest { return SBitCell(text: "\(bit)", tone: .lowest) }
        return SBitCell(text: "\(bit)", tone: bit == 1 ? (result ? .result : .one) : .zero)
    }
    return SBitRow(label: label, cells: cells, readout: readout ?? "\(n)", result: result)
}

private func lowestBit(_ n: Int) -> Int { (0..<8).first { (n >> $0) & 1 == 1 } ?? 0 }
private func litCol(_ pos: Int) -> Set<Int> { [7 - pos] }

private let basicsLegend: [(Color, SwatchStyle, String)] = [(SimColors.blue, .fill, "Set bit"), (SimColors.active, .fill, "Lowest set bit"), (SimColors.answer, .fill, "Result")]

private func bitBasicsTabs() -> [SBitTab] {
    let n = 44, low = lowestBit(44), neg = (256 - n) & 0xFF, m1 = n - 1
    let andNeg = SBitTab(label: "n & −n", frames: [
        SBitFrame(rows: [sbRow("n", n)], formula: "44 = 32 + 8 + 4 = \(bin8(n))",
                  headline: "n = 44 has three set bits: 5, 3 and 2.", body: "Each cell is a place value. The tricks below are all about which places are occupied."),
        SBitFrame(lit: litCol(low), rows: [sbRow("n", n, lowest: low), sbRow("−n", -n, lowest: low)],
                  formulaRows: [StoryFormulaRow(label: "−n", formula: "~n + 1 = \(bin8(~n)) + 1")],
                  headline: "−n flips every bit and adds 1, which carries up to bit {\(low)}.",
                  body: "Two's complement: the +1 stops at the first 0 of ~n, and that is exactly n's lowest set bit."),
        SBitFrame(lit: litCol(low), rows: [sbRow("n", n, lowest: low), sbRow("−n", -n, lowest: low), sbRow("n & −n", n & neg, result: true)],
                  formula: "\(bin8(n)) & \(bin8(neg)) = {v:\(bin8(n & neg))}",
                  formulaRows: [StoryFormulaRow(label: "−n", formula: "~n + 1 = \(bin8(~n)) + 1")],
                  headline: "Only bit \(low) is 1 in both n and −n, so n & −n = {v:\(n & neg)}.",
                  body: "−n flips every bit, then +1 carries up to the lowest set bit. That bit is the only one they share."),
        SBitFrame(lit: litCol(low), rows: [sbRow("n", n, lowest: low), sbRow("n & −n", n & neg, result: true)],
                  formula: "i += i & −i → \(n) + {v:\(n & neg)} = \(n + (n & neg))",
                  headline: "n & −n isolates the lowest set bit, {v:\(n & neg)}, in one step.",
                  body: "A Fenwick tree walks between ranges by exactly this amount."),
    ], legend: basicsLegend)
    let orM1 = SBitTab(label: "n | (n−1)", frames: [
        SBitFrame(lit: litCol(low), rows: [sbRow("n", n, lowest: low)], formula: "n = \(bin8(n))",
                  headline: "n = 44. Its lowest set bit is bit {\(low)}, with two zeros below it.",
                  body: "Subtracting 1 has to borrow from that bit."),
        SBitFrame(lit: litCol(low), rows: [sbRow("n", n, lowest: low), sbRow("n − 1", m1)], formula: "44 − 1 = \(bin8(m1))",
                  headline: "n − 1 = 43 clears bit \(low) and sets every bit below it.",
                  body: "Borrowing turns the lowest 1 into 0 and the trailing zeros into ones. Everything above is untouched."),
        SBitFrame(lit: litCol(low), rows: [sbRow("n", n, lowest: low), sbRow("n − 1", m1), sbRow("n | (n−1)", n | m1, result: true)],
                  formula: "\(bin8(n)) | \(bin8(m1)) = {v:\(bin8(n | m1))}",
                  headline: "n | (n − 1) = {v:\(n | m1)}: the trailing zeros are filled in.",
                  body: "Bits above \(low) keep their value and every bit from \(low) down becomes 1."),
    ], legend: basicsLegend)
    let andM1 = SBitTab(label: "n & (n−1)", frames: [
        SBitFrame(lit: litCol(low), rows: [sbRow("n", n, lowest: low), sbRow("n − 1", m1)], formula: "44 − 1 = \(bin8(m1))",
                  headline: "n − 1 flips bit {\(low)} and everything below it.",
                  body: "So n and n − 1 agree on every bit above \(low) and disagree on the rest."),
        SBitFrame(lit: litCol(low), rows: [sbRow("n", n, lowest: low), sbRow("n − 1", m1), sbRow("n & (n−1)", n & m1, result: true)],
                  formula: "\(bin8(n)) & \(bin8(m1)) = {v:\(bin8(n & m1))}",
                  headline: "n & (n − 1) = {v:\(n & m1)}: the lowest set bit is gone.",
                  body: "Repeat it until n is 0 and you have counted the set bits, one per loop."),
        SBitFrame(lit: litCol(3), rows: [sbRow("n", 8, lowest: 3), sbRow("n − 1", 7), sbRow("n & (n−1)", 0, result: true)],
                  formula: "\(bin8(8)) & \(bin8(7)) = {v:\(bin8(0))}",
                  headline: "For n = 8 the answer is {v:0}, because 8 has a single set bit.",
                  body: "That is the power-of-two test: n > 0 && (n & (n − 1)) == 0."),
    ], legend: basicsLegend)
    return [andNeg, orM1, andM1]
}

private func countSetBitsTabs() -> [SBitTab] {
    let n = 156
    let total = (0..<8).filter { (n >> $0) & 1 == 1 }.count
    let compare = [StoryFormulaRow(label: "naive", formula: "8 iterations, always"),
                   StoryFormulaRow(label: "Kernighan", formula: "\(total) iterations, one per set bit")]
    var naive = [SBitFrame(rows: [sbRow("n", n, pending: { _ in true })], formula: "n = \(bin8(n))", formulaRows: compare,
                           chips: [StoryChip("count", "0"), StoryChip("iteration", "0 of 8")],
                           headline: "Count the 1s in n = 156 by testing one position at a time.",
                           body: "Shift the bit into place and mask it with 1, from bit 7 down to bit 0.")]
    var count = 0
    for (k, pos) in (0..<8).reversed().enumerated() {
        let bit = (n >> pos) & 1
        count += bit
        let cells = (0..<8).map { i -> SBitCell in
            let p = 7 - i, b = (n >> p) & 1
            if p == pos { return SBitCell(text: "\(b)", tone: .lowest) }
            if p > pos { return SBitCell(text: "\(b)", tone: b == 1 ? .one : .zero) }
            return SBitCell(text: "\(b)", tone: .pending)
        }
        naive.append(SBitFrame(lit: litCol(pos), rows: [SBitRow(label: "n", cells: cells, readout: "\(n)")],
                               formula: "(\(n) >> \(pos)) & 1 = \(bit == 1 ? "{1}" : "0") → count \(bit == 1 ? "{\(count)}" : "\(count)")",
                               formulaRows: compare,
                               chips: [StoryChip("count", "\(count)"), StoryChip("iteration", "\(k + 1) of 8")],
                               headline: bit == 1 ? "Bit \(pos) is {1}, so the count goes up to \(count)." : "Bit \(pos) is 0, so the count stays at \(count).",
                               body: bit == 1 ? "The loop visits all 8 positions, including zeros. Kernighan skips zeros by clearing the lowest set bit each time."
                                   : "A zero still costs an iteration. The naive loop's work depends on the width, not on n."))
    }
    naive.append(SBitFrame(rows: [sbRow("n", n)], formula: "popcount(\(n)) = {v:\(total)}", formulaRows: compare,
                           chips: [StoryChip("count", "\(total)", .answer), StoryChip("iterations", "8")],
                           headline: "Eight iterations to find {v:\(total)} set bits.", body: "Half of those iterations looked at zeros."))
    var kern = [SBitFrame(rows: [sbRow("n", n)], formula: "while n ≠ 0: n &= n − 1, count++", formulaRows: compare,
                          chips: [StoryChip("count", "0"), StoryChip("iteration", "0")],
                          headline: "Kernighan's loop runs once per set bit, not once per position.",
                          body: "Each n & (n − 1) removes exactly the lowest set bit.")]
    var v = n, c = 0
    while v != 0 {
        let low = lowestBit(v), next = v & (v - 1)
        c += 1
        kern.append(SBitFrame(lit: litCol(low), rows: [sbRow("n", v, lowest: low), sbRow("n − 1", v - 1), sbRow("n & (n−1)", next, result: true)],
                              formula: "\(v) & \(v - 1) = {v:\(next)} → count {\(c)}", formulaRows: compare,
                              chips: [StoryChip("count", "\(c)"), StoryChip("iteration", "\(c) of \(total)")],
                              headline: "Clearing bit {\(low)} leaves \(next), and the count is \(c).",
                              body: c == 1 ? "The zeros below bit \(low) were never visited." : "Only set bits cost an iteration."))
        v = next
    }
    kern.append(SBitFrame(rows: [sbRow("n", 0)], formula: "n = 0 → count = {v:\(total)}", formulaRows: compare,
                          chips: [StoryChip("count", "\(total)", .answer), StoryChip("iterations", "\(total)")],
                          headline: "n reached 0 after {v:\(total)} iterations, one per set bit.",
                          body: "Same answer as the naive loop in half the iterations here, and far fewer on sparse numbers."))
    return [
        SBitTab(label: "Naive", frames: naive, legend: [(SimColors.blue, .fill, "Counted"), (SimColors.active, .fill, "Testing"), (sbDim, .fill, "Not checked yet")]),
        SBitTab(label: "Kernighan", frames: kern, legend: basicsLegend),
    ]
}

private func xorTricksTabs() -> [SBitTab] {
    let a = 44, b = 25
    let legend: [(Color, SwatchStyle, String)] = [(SimColors.blue, .fill, "Set bit"), (SimColors.answer, .fill, "Bits differ → 1"), (sbDim, .fill, "Bits match → 0")]
    let basics = SBitTab(label: "Basics", frames: [
        SBitFrame(rows: [sbRow("a", a), sbRow("b", b), sbRow("a ⊕ b", a ^ b, result: true)], formula: "\(a) ⊕ \(b) = {v:\(a ^ b)}",
                  formulaRows: [StoryFormulaRow(label: "undo", formula: "\(a ^ b) ⊕ \(b) = \(a)")],
                  headline: "XOR is {v:1} wherever a and b differ.",
                  body: "So x ⊕ x = 0 and x ⊕ 0 = x, which means XOR undoes itself. Swap and Find unique rely on this."),
        SBitFrame(rows: [sbRow("a", a), sbRow("a", a), sbRow("a ⊕ a", 0, result: true)], formula: "\(a) ⊕ \(a) = {v:0}",
                  headline: "a ⊕ a = {v:0}: every bit matches itself.", body: "Any value XOR-ed with itself cancels completely."),
        SBitFrame(rows: [sbRow("a", a), sbRow("0", 0), sbRow("a ⊕ 0", a, result: true)], formula: "\(a) ⊕ 0 = {v:\(a)}",
                  headline: "a ⊕ 0 = {v:\(a)}: XOR with zero changes nothing.", body: "Zero is the identity, the way 0 is for addition."),
        SBitFrame(rows: [sbRow("a ⊕ b", a ^ b), sbRow("b", b), sbRow("(a ⊕ b) ⊕ b", a, result: true)], formula: "\(a ^ b) ⊕ \(b) = {v:\(a)}",
                  headline: "XOR-ing b in again cancels it and gives back {v:\(a)}.", body: "(a ⊕ b) ⊕ b = a ⊕ (b ⊕ b) = a ⊕ 0 = a."),
    ], legend: legend)
    let s1 = a ^ b, s2 = b ^ s1, s3 = s1 ^ s2
    let swap = SBitTab(label: "Swap", frames: [
        SBitFrame(rows: [sbRow("a", a), sbRow("b", b)], formula: "a = \(a), b = \(b)",
                  headline: "Swap a and b with three XORs and no temporary.", body: "Each line folds one value into the other, then unfolds it."),
        SBitFrame(rows: [sbRow("a", s1, result: true), sbRow("b", b)], formula: "a ^= b → \(a) ⊕ \(b) = {v:\(s1)}",
                  headline: "a ^= b stores both values in a: {v:\(s1)}.", body: "a now holds a ⊕ b, and b is untouched."),
        SBitFrame(rows: [sbRow("a", s1), sbRow("b", s2, result: true)], formula: "b ^= a → \(b) ⊕ \(s1) = {v:\(s2)}",
                  headline: "b ^= a cancels b's own bits and leaves the old a, {v:\(s2)}.", body: "b ⊕ (a ⊕ b) = a."),
        SBitFrame(rows: [sbRow("a", s3, result: true), sbRow("b", s2)], formula: "a ^= b → \(s1) ⊕ \(s2) = {v:\(s3)}",
                  headline: "a ^= b cancels the old a and leaves the old b, {v:\(s3)}.", body: "(a ⊕ b) ⊕ a = b. The two values have traded places."),
    ], legend: legend)
    let items = [4, 7, 2, 7, 4]
    var acc = 0
    var unique = [SBitFrame(rows: [sbRow("acc", 0)], formula: "acc = 0", chips: [StoryChip("array", "4 7 2 7 4")],
                            headline: "Every value appears twice except one. XOR them all together.",
                            body: "Pairs cancel to 0 whatever order they arrive in, so only the loner survives.")]
    for (i, x) in items.enumerated() {
        let next = acc ^ x
        unique.append(SBitFrame(rows: [sbRow("acc", acc), sbRow("x", x), sbRow("acc ⊕ x", next, result: true)], formula: "\(acc) ⊕ \(x) = {v:\(next)}",
                                chips: [StoryChip("array", "4 7 2 7 4"), StoryChip("read", "\(i + 1) of \(items.count)")],
                                headline: items[..<i].contains(x) ? "\(x) again cancels its first copy: acc = {v:\(next)}." : "Fold in \(x): acc = {v:\(next)}.",
                                body: "One pass, one integer of memory, O(n)."))
        acc = next
    }
    unique.append(SBitFrame(rows: [sbRow("acc", acc, result: true)], formula: "unique = {v:\(acc)}",
                            chips: [StoryChip("array", "4 7 2 7 4"), StoryChip("unique", "\(acc)", .answer)],
                            headline: "Both pairs cancelled, so {v:\(acc)} is the one that appears once.",
                            body: "No sorting and no hash set: the pairs erase each other."))
    return [basics, swap, SBitTab(label: "Find unique", frames: unique, legend: legend)]
}

private func subsetsTabs() -> [SBitTab] {
    let names = ["a", "b", "c"]
    func set(_ m: Int) -> [String] { (0..<3).filter { (m >> $0) & 1 == 1 }.map { names[$0] } }
    func setText(_ m: Int) -> String { m == 0 ? "∅" : "{" + set(m).joined(separator: ",") + "}" }
    func bits(_ m: Int) -> String { (0..<3).map { (m >> (2 - $0)) & 1 == 1 ? "1" : "0" }.joined() }
    func tiles(_ current: Int?) -> [MaskTile] {
        (0..<8).map { m in MaskTile(top: "\(m) · \(bits(m))", bottom: setText(m),
                                    tone: current.map { m < $0 ? .listed : m == $0 ? .current : .pending } ?? .listed) }
    }
    let headers = ["c · 2", "b · 1", "a · 0"]
    func maskRow(_ m: Int) -> SBitRow {
        SBitRow(label: "mask", cells: (0..<3).map { i in
            let bit = (m >> (2 - i)) & 1
            return SBitCell(text: "\(bit)", tone: bit == 1 ? .member : .zero)
        }, readout: "\(m)")
    }
    var frames = [SBitFrame(headers: headers, rows: [maskRow(0)], formula: "for mask in 0 ..< 2³", masks: tiles(-1),
                            chips: [StoryChip("items", "a b c"), StoryChip("masks", "8")],
                            headline: "Three items, so every mask from 0 to 7 is one subset.",
                            body: "Bit i of the mask says whether item i is in the subset.")]
    for m in 0..<8 {
        let parts = (0..<3).filter { (m >> $0) & 1 == 1 }
        let formula: String
        if parts.isEmpty { formula = "0 has no set bits → take {v:nothing}" }
        else if parts.count == 1 { formula = "\(m) & (1 << \(parts[0])) = \(1 << parts[0]) ≠ 0 → take {v:\(names[parts[0]])}" }
        else { formula = parts.map { "\(m) & \(1 << $0) ≠ 0" }.joined(separator: ", ") + " → {v:\(set(m).joined(separator: ", "))}" }
        let subset = m == 0 ? "the empty set, {v:∅}" : "\\{ {v:\(set(m).joined(separator: ", "))} }"
        frames.append(SBitFrame(headers: headers, lit: Set((0..<3).filter { (m >> (2 - $0)) & 1 == 1 }), rows: [maskRow(m)], formula: formula, masks: tiles(m),
                                chips: [StoryChip("mask", "\(m)"), StoryChip("subset", setText(m), .answer), StoryChip("listed", "\(m + 1) of 8")],
                                headline: "mask \(m) = \(bits(m)), so the subset is \(subset).",
                                body: "Counting 0 to 2ⁿ−1 lists every subset once. The mask is also a plain integer, so it can index a DP array."))
    }
    frames.append(SBitFrame(headers: headers, rows: [maskRow(7)], formula: "8 masks → {v:8 subsets}", masks: tiles(nil),
                            chips: [StoryChip("listed", "8 of 8", .answer)],
                            headline: "All {v:2³ = 8} subsets, each exactly once.",
                            body: "No recursion and no visited set: the counter itself guarantees coverage."))
    frames.append(SBitFrame(headers: headers, rows: [maskRow(5)], formula: "dp[5] ← best answer for \\{{v:a,c}}",
                            masks: tiles(nil), chips: [StoryChip("state", "mask 5")],
                            headline: "Because a mask is an integer, dp[mask] can store an answer per subset.",
                            body: "That is bitmask DP: 2ⁿ states, each reachable by flipping one bit of a smaller mask."))
    return [SBitTab(label: "Subsets", frames: frames, legend: [(SimColors.answer, .fill, "In this subset"), (SimColors.active, .fill, "Current mask"), (SimColors.green, .fill, "Listed")])]
}

let bitStoryTopicIds: Set<String> = ["bit_basics", "count_set_bits", "xor_tricks", "subsets_bitmask"]

private func bitStoryTabs(_ topicId: String) -> [SBitTab] {
    switch topicId {
    case "count_set_bits": countSetBitsTabs()
    case "xor_tricks": xorTricksTabs()
    case "subsets_bitmask": subsetsTabs()
    default: bitBasicsTabs()
    }
}

struct BitStoryLab: View {
    private let tabs: [SBitTab]
    @State private var tab = 0
    @State private var playback: PlaybackState
    @Environment(\.palette) private var palette

    init(topicId: String) {
        tabs = bitStoryTabs(topicId)
        _playback = State(initialValue: PlaybackState(stepCount: tabs[0].frames.count, speedMs: 900))
    }

    var body: some View {
        let frames = tabs[tab].frames
        let frame = frames[min(playback.index, frames.count - 1)]
        VStack(alignment: .leading, spacing: 0) {
            LabCard {
                if tabs.count > 1 {
                    LabSegments(labels: tabs.map(\.label), selected: Binding(get: { tab }, set: { select($0) })).padding(.bottom, 14)
                }
                SBitGrid(frame: frame)
                if let formula = frame.formula { StoryFormula(text: formula).padding(.top, 12) }
                if !frame.formulaRows.isEmpty { StoryFormulaRows(rows: frame.formulaRows).padding(.top, frame.formula == nil ? 12 : 8) }
                if !frame.masks.isEmpty {
                    Text("ALL 2³ MASKS").font(AppFont.sans(12, .semibold)).tracking(1).foregroundStyle(palette.muted).padding(.top, 14)
                    LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 6), count: 4), spacing: 6) {
                        ForEach(frame.masks.indices, id: \.self) { MaskTileView(tile: frame.masks[$0]) }
                    }
                    .padding(.top, 8)
                }
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
        playback = PlaybackState(stepCount: tabs[i].frames.count, speedMs: 900)
    }
}

private struct SBitGrid: View {
    let frame: SBitFrame
    @Environment(\.palette) private var palette

    private let gutter: CGFloat = 70
    private let readout: CGFloat = 44

    var body: some View {
        let wide = frame.headers.count <= 4
        VStack(spacing: 8) {
            HStack(spacing: 4) {
                Color.clear.frame(width: gutter, height: 1)
                ForEach(frame.headers.indices, id: \.self) { i in
                    let on = frame.lit.contains(i)
                    Text(frame.headers[i]).font(AppFont.mono(wide ? 12 : 11, on ? .bold : .regular))
                        .foregroundStyle(on ? StoryTone.active.ink(palette) : palette.muted).lineLimit(1)
                        .frame(maxWidth: .infinity)
                }
                Color.clear.frame(width: readout, height: 1)
            }
            ForEach(frame.rows.indices, id: \.self) { r in
                let row = frame.rows[r]
                let ink = row.result ? StoryTone.answer.ink(palette) : palette.onSurface
                HStack(spacing: 4) {
                    Text(row.label).font(AppFont.mono(13)).foregroundStyle(row.result ? ink : palette.onSurface.opacity(0.8))
                        .lineLimit(1).minimumScaleFactor(0.7).frame(width: gutter, alignment: .leading)
                    ForEach(row.cells.indices, id: \.self) { SBitCellView(cell: row.cells[$0], height: wide ? 44 : 34) }
                    Text(row.readout).font(AppFont.mono(15, .bold)).foregroundStyle(ink).lineLimit(1).minimumScaleFactor(0.7)
                        .frame(width: readout, alignment: .trailing)
                }
            }
        }
    }
}

private struct SBitCellView: View {
    let cell: SBitCell
    let height: CGFloat
    @Environment(\.palette) private var palette

    var body: some View {
        let (fill, ink): (Color, Color) = switch cell.tone {
        case .zero: (palette.muted.opacity(0.18), palette.muted)
        case .one: (SimColors.blue, .white)
        case .lowest: (SimColors.active, Color(hex: 0x1F1A0A))
        case .result, .member: (SimColors.answer, .white)
        case .pending: (palette.muted.opacity(0.08), palette.muted.opacity(0.55))
        }
        Text(cell.text).font(AppFont.mono(15, .bold)).foregroundStyle(ink)
            .frame(maxWidth: .infinity).frame(height: height)
            .background(fill, in: RoundedRectangle(cornerRadius: 7))
    }
}

private struct MaskTileView: View {
    let tile: MaskTile
    @Environment(\.palette) private var palette

    var body: some View {
        let (fill, ink): (Color, Color) = switch tile.tone {
        case .listed: (SimColors.green.opacity(palette.dark ? 0.22 : 0.16), StoryTone.done.ink(palette))
        case .current: (SimColors.active, Color(hex: 0x1F1A0A))
        case .pending: (palette.muted.opacity(0.08), palette.muted.opacity(0.6))
        }
        VStack(spacing: 2) {
            Text(tile.top).font(AppFont.mono(11)).opacity(0.85)
            Text(tile.bottom).font(AppFont.mono(13, .bold))
        }
        .foregroundStyle(ink)
        .lineLimit(1).minimumScaleFactor(0.7)
        .frame(maxWidth: .infinity).frame(height: 48)
        .background(fill, in: RoundedRectangle(cornerRadius: 8))
    }
}
