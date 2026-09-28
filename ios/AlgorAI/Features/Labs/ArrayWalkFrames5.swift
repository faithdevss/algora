import Foundation

// ArrayWalkSection.kt builders: Fenwick tree, strings as arrays, and the list ADT.

private func lowbit(_ i: Int) -> Int { i & -i }

// Contiguous storage buys O(1) indexing, and immutability turns "s = s + x" in a loop into a quadratic copy.
func stringFrames() -> [WalkFrame] {
    let s = "algora", other = "algebra"
    let sc = Array(s), oc = Array(other)
    var frames: [WalkFrame] = []

    func charRow(_ text: String, active: Int? = nil, done: Range<Int>? = nil, dimFrom: Int? = nil) -> [CellView] {
        let chars = Array(text)
        let dimAt = dimFrom ?? chars.count
        return chars.enumerated().map { i, c in
            CellView(String(c), i == active ? .active : done?.contains(i) == true ? .done : i >= dimAt ? .dim : .idle)
        }
    }

    frames.append(WalkFrame(
        status: "A string is a contiguous block of code units. s[3] is one multiply-and-add on the base address — " +
            "the same cost as a[3] on any array, and the reason indexing never appears in a complexity analysis.",
        cells: charRow(s, active: 3), pointers: [3: "s[3]"], readout: "s[3] = '\(sc[3])'"))

    let mismatch = sc.indices.first { sc[$0] != oc[$0] }!
    frames.append(WalkFrame(
        status: "Comparing \"\(s)\" with \"\(other)\" stops at index \(mismatch) ('\(sc[mismatch])' vs " +
            "'\(oc[mismatch])'), after \(mismatch + 1) character comparisons rather than \(sc.count). " +
            "Equality is worst-case O(n) but usually leaves early; sorting strings is why that worst case matters.",
        cells: charRow(s, active: mismatch, done: 0..<mismatch) + (0..<(oc.count - sc.count)).map { _ in CellView("·", .dim) },
        aux: charRow(other, active: mismatch, done: 0..<mismatch), auxLabel: "the string being compared against",
        readout: "\(mismatch + 1) comparisons, verdict: \"\(s)\" > \"\(other)\""))

    var copies = 0
    var built = ""
    for c in sc {
        copies += built.count + 1
        built.append(c)
        let existing = built.count - 1
        frames.append(WalkFrame(
            status: "s = s + '\(c)'. The result is a new string, so the " +
                (existing == 1 ? "1 character" : "\(existing) characters") +
                " already there must be copied into fresh storage before '\(c)' is written. Total copied so far: \(copies).",
            cells: charRow(built + String(repeating: "·", count: sc.count - built.count), active: built.count - 1, dimFrom: built.count),
            readout: "characters copied: \(copies)"))
    }

    func builderWrites(_ count: Int, _ startCapacity: Int) -> Int {
        var capacity = startCapacity, size = 0, growthCopies = 0
        for _ in 0..<count {
            if size == capacity { growthCopies += size; capacity *= 2 }
            size += 1
        }
        return size + growthCopies
    }

    let builderSmall = builderWrites(sc.count, 4)
    frames.append(WalkFrame(
        status: "Six characters cost \(copies) character writes that way. A mutable builder writes each character " +
            "once into a buffer it owns and only copies when the buffer fills — \(builderSmall) writes for the same " +
            "result. At this size the difference is a rounding error, which is exactly why the bug survives review.",
        cells: charRow(built, done: 0..<built.count), readout: "concat \(copies) writes · builder \(builderSmall) writes"))

    let big = 1000
    let naiveBig = big * (big + 1) / 2
    let builderBig = builderWrites(big, 16)
    frames.append(WalkFrame(
        status: "Run the same loop to length \(big) and the gap is the whole point: concatenation writes " +
            "\(naiveBig) characters because step i copies i of them — 1 + 2 + … + \(big). The builder writes " +
            "\(builderBig), since doubling makes the copies a geometric series that sums to under 2n. " +
            "Quadratic against linear, from one operator.",
        cells: charRow(built, done: 0..<built.count), readout: "n = \(big) → \(naiveBig) vs \(builderBig) writes (\(naiveBig / builderBig)×)"))
    frames.append(WalkFrame(
        status: "Immutability is not the mistake — it is what makes strings safe to share, hash once, and use as " +
            "map keys. Building them in a loop with + is the mistake. Use a builder while assembling, freeze to a " +
            "string when done.",
        cells: charRow(s, done: 0..<sc.count)))
    return frames
}

// One script of operations against two implementations of the list contract, counting what each pays.
func listAdtFrames() -> [WalkFrame] {
    let start = [10, 20, 30, 40, 50]
    var array = start, linked = start
    var shifts = 0, indexReads = 0, hops = 0, pointerWrites = 0
    var frames: [WalkFrame] = []

    func row(_ list: [Int], _ marked: Set<Int>, _ mark: CellMark) -> [CellView] {
        list.enumerated().map { i, v in CellView("\(v)", marked.contains(i) ? mark : .idle) }
    }

    frames.append(WalkFrame(
        status: "The contract is the same for both rows: ordered, indexed, duplicates allowed, get / insert / " +
            "remove at any position. Nothing below changes what the list means — only what each call costs.",
        cells: row(array, [], .idle), aux: row(linked, [], .idle), auxLabel: "linked implementation"))

    let getIndex = 3
    indexReads += 1
    hops += getIndex + 1
    frames.append(WalkFrame(
        status: "get(\(getIndex)): the array computes base + \(getIndex) × width and reads once. The linked list has " +
            "no address arithmetic to do — it follows \(getIndex + 1) next pointers to reach the same value.",
        cells: row(array, [getIndex], .active), pointers: [getIndex: "read"],
        aux: row(linked, Set(0...getIndex), .window), auxLabel: "linked implementation — \(getIndex + 1) hops",
        readout: "array 1 read · linked \(getIndex + 1) hops"))

    let shiftCount = array.count
    shifts += shiftCount
    array.insert(5, at: 0)
    linked.insert(5, at: 0)
    pointerWrites += 2
    frames.append(WalkFrame(
        status: "insert(0, 5): the array must move every one of \(shiftCount) elements up a slot before the new " +
            "head has anywhere to live. The linked list allocates a node and writes 2 pointers — position 0 costs it nothing.",
        cells: row(array, [0], .result), aux: row(linked, [0], .result), auxLabel: "linked implementation — 2 pointer writes",
        readout: "array \(shiftCount) shifts · linked 2 pointer writes"))

    array.append(60)
    linked.append(60)
    pointerWrites += 2
    frames.append(WalkFrame(
        status: "append(60): both are cheap. The array writes into spare capacity (and occasionally pays a " +
            "doubling copy, amortised to O(1)); the linked list splices at a tail pointer it already keeps.",
        cells: row(array, [array.count - 1], .result), aux: row(linked, [linked.count - 1], .result), auxLabel: "linked implementation",
        readout: "array ~1 write · linked 2 pointer writes"))

    let removeIndex = 2
    array.remove(at: removeIndex)
    let removeShifts = array.count - removeIndex
    shifts += removeShifts
    hops += removeIndex
    pointerWrites += 1
    linked.remove(at: removeIndex)
    frames.append(WalkFrame(
        status: "remove(\(removeIndex)): the array closes the hole by shifting \(removeShifts) elements down. The " +
            "linked list hops \(removeIndex) nodes to find the predecessor, then unlinks with a single pointer " +
            "write — the traversal, not the removal, is what it pays for.",
        cells: row(array, [removeIndex], .done), aux: row(linked, Set(0..<removeIndex), .window),
        auxLabel: "linked implementation — \(removeIndex) hops + 1 write", readout: "array \(removeShifts) shifts · linked \(removeIndex) hops"))

    let lastGet = 4
    indexReads += 1
    hops += lastGet + 1
    frames.append(WalkFrame(
        status: "One more get(\(lastGet)) to close the script. Both lists hold \(array.map(String.init).joined(separator: ", ")) — " +
            "identical contents, identical answers to every query the interface exposes.",
        cells: row(array, [lastGet], .active), aux: row(linked, Set(0...lastGet), .window),
        auxLabel: "linked implementation — \(lastGet + 1) hops", readout: "array 1 read · linked \(lastGet + 1) hops"))

    frames.append(WalkFrame(
        status: "Whole script: the array moved \(shifts) elements and did \(indexReads) O(1) index reads; the linked " +
            "list walked \(hops) nodes and wrote \(pointerWrites) pointers. Pick by the mix you actually run — " +
            "index-heavy work wants the array, splice-heavy work with a node already in hand wants the links. " +
            "\"Linked lists are faster at inserts\" is only true once you are standing at the insertion point.",
        cells: row(array, Set(array.indices), .done), aux: row(linked, Set(linked.indices), .done),
        auxLabel: "linked implementation — same contents", readout: "array: \(shifts) shifts · linked: \(hops) hops, \(pointerWrites) pointer writes"))
    return frames
}
