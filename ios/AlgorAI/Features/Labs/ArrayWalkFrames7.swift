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
