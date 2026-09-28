import Foundation

// ArrayWalkSection.kt builders: string walks and in-place two-pointer passes.

func naiveSearchFrames() -> [WalkFrame] {
    let textS = "ABCABCABD", patternS = "ABCABD"
    let text = Array(textS), pattern = Array(patternS)
    var frames: [WalkFrame] = []

    func patternRow(_ shift: Int, _ upTo: Int, _ failed: Int?) -> [CellView] {
        (0..<text.count).map { i in
            let j = i - shift
            if j < 0 || j >= pattern.count { return CellView("·", .dim) }
            return CellView(String(pattern[j]), j == failed ? .result : j < upTo ? .done : j == upTo ? .active : .idle)
        }
    }

    frames.append(WalkFrame(
        status: "Text \"\(textS)\", pattern \"\(patternS)\". No preprocessing and no extra memory — align, compare, shift by one.",
        cells: text.map { CellView(String($0)) }, aux: patternRow(0, 0, nil), auxLabel: "pattern"))

    var comparisons = 0
    var hits: [Int] = []
    for shift in thru(0, text.count - pattern.count) {
        var j = 0
        while j < pattern.count && text[shift + j] == pattern[j] {
            comparisons += 1
            j += 1
            frames.append(WalkFrame(
                status: "Shift \(shift): '\(pattern[j - 1])' matches text[\(shift + j - 1)]. \(j) of \(pattern.count) characters agree.",
                cells: text.enumerated().map { i, c in CellView(String(c), inUntil(i, shift, shift + j) ? .window : i < shift ? .dim : .idle) },
                pointers: [shift + j - 1: "i"], aux: patternRow(shift, j, nil), auxLabel: "pattern", readout: "comparisons: \(comparisons)"))
        }
        if j == pattern.count {
            hits.append(shift)
            frames.append(WalkFrame(
                status: "Shift \(shift): the pattern ran out — every character agreed, so this is a match.",
                cells: text.enumerated().map { i, c in CellView(String(c), inUntil(i, shift, shift + pattern.count) ? .result : .dim) },
                aux: patternRow(shift, pattern.count, nil), auxLabel: "pattern", readout: "match at \(shift) · comparisons: \(comparisons)"))
        } else {
            comparisons += 1
            frames.append(WalkFrame(
                status: "Shift \(shift): '\(pattern[j])' ≠ text[\(shift + j)] = '\(text[shift + j])'. Mismatch — slide the pattern one position right. " +
                    (j > 0 ? "The \(j) character\(j == 1 ? "" : "s") that just matched are discarded, and \(j) of them will be compared again." : "Nothing was learned to discard here."),
                cells: text.enumerated().map { i, c in CellView(String(c), i == shift + j ? .result : inUntil(i, shift, shift + j) ? .window : .dim) },
                pointers: [shift + j: "i"], aux: patternRow(shift, j, j), auxLabel: "pattern", readout: "comparisons: \(comparisons)"))
        }
    }

    frames.append(WalkFrame(
        status: "Found at \(hits.map(String.init).joined(separator: ", ")) in \(comparisons) comparisons over \(text.count - pattern.count + 1) alignments. " +
            "On ordinary text most mismatches land on the first character, so this is close to linear in practice.",
        cells: text.enumerated().map { i, c in CellView(String(c), hits.contains { inUntil(i, $0, $0 + pattern.count) } ? .result : .dim) },
        readout: "\(comparisons) comparisons"))

    let badTextS = "AAAAAAAAAB", badPatternS = "AAAB"
    let badText = Array(badTextS), badPattern = Array(badPatternS)
    var badComparisons = 0
    for shift in thru(0, badText.count - badPattern.count) {
        var j = 0
        while j < badPattern.count && badText[shift + j] == badPattern[j] { badComparisons += 1; j += 1 }
        if j < badPattern.count { badComparisons += 1 }
    }
    frames.append(WalkFrame(
        status: "Now the pathological input: text \"\(badTextS)\", pattern \"\(badPatternS)\". Every alignment matches three characters " +
            "and fails on the fourth — \(badComparisons) comparisons for a \(badText.count)-character text, which is (n − m + 1)·m almost exactly. " +
            "KMP's prefix table exists to record what those matched characters already established.",
        cells: badText.map { CellView(String($0), .dim) },
        aux: (0..<badText.count).map { i in i < badPattern.count ? CellView(String(badPattern[i]), .result) : CellView("·", .dim) },
        auxLabel: "pattern", readout: "\(badComparisons) comparisons · n·m = \(badText.count * badPattern.count)"))
    return frames
}

// Z[i] is how far the suffix at i agrees with the string's own prefix; [l, r) is the pointer row.
func zAlgorithmFrames() -> [WalkFrame] {
    let s = Array("aabcaabxaaz")
    let n = s.count
    var z = [Int](repeating: 0, count: n)
    var frames: [WalkFrame] = []

    func zRow(_ upTo: Int, active: Int? = nil) -> [CellView] {
        (0..<n).map { i in
            i == 0 ? CellView("–", .dim) : i == active ? CellView("\(z[i])", .active) : i <= upTo ? CellView("\(z[i])", .window) : CellView("·", .dim)
        }
    }

    frames.append(WalkFrame(
        status: "Z[i] is the length of the longest run starting at i that also matches the start of the string. Z[0] is left undefined.",
        cells: s.map { CellView(String($0)) }, aux: zRow(0), auxLabel: "Z"))

    var l = 0, r = 0, copied = 0, compared = 0
    for i in 1..<n {
        let insideWindow = i < r
        let mirror = i - l
        if insideWindow {
            z[i] = min(r - i, z[mirror])
            copied += 1
        }
        let startedAt = z[i]
        while i + z[i] < n && s[z[i]] == s[i + z[i]] {
            z[i] += 1
            compared += 1
        }
        let extended = z[i] - startedAt
        let grew = i + z[i] > r
        if grew {
            l = i
            r = i + z[i]
        }
        var status = "i = \(i): "
        status += insideWindow
            ? "inside the known window, so the mirror at \(mirror) supplies a starting value of \(startedAt) without a single comparison. "
            : "outside any known window, so compare from scratch. "
        status += extended == 0 ? "No extension." : "Extended \(extended) character\(extended == 1 ? "" : "s")."
        status += " Z[\(i)] = \(z[i])."
        if grew { status += " That pushed the right edge to \(r) — it never moves left, which is why the total comparison work is O(n)." }
        var pointers: [Int: String] = [i: "i"]
        if l != r {
            pointers[l] = "l"
            pointers[max(r - 1, l)] = "r"
        }
        let zi = z[i]
        frames.append(WalkFrame(
            status: status,
            cells: s.enumerated().map { j, c in CellView(String(c), inUntil(j, i, i + zi) ? .result : j < zi ? .done : j == i ? .active : .idle) },
            pointers: pointers, aux: zRow(i - 1, active: i), auxLabel: "Z",
            readout: "copied from mirror: \(copied) · characters compared: \(compared)"))
    }

    frames.append(WalkFrame(
        status: "Z = [\(z.indices.map { $0 == 0 ? "–" : "\(z[$0])" }.joined(separator: ", "))]. \(compared) character comparisons for " +
            "an \(n)-character string. Concatenate pattern + '$' + text and every Z equal to the pattern's length is an occurrence — " +
            "that is O(n + m) matching out of a general-purpose array.",
        cells: s.map { CellView(String($0), .dim) }, aux: zRow(n - 1), auxLabel: "Z", readout: "\(compared) comparisons for n = \(n)"))
    return frames
}

// Expand around centre. Both kinds of centre get their own frames.
func longestPalindromeFrames() -> [WalkFrame] {
    let str = "abacabad"
    let s = Array(str)
    var frames: [WalkFrame] = []
    var bestStart = 0, bestLength = 1
    func sub(_ start: Int, _ length: Int) -> String { String(s[start..<(start + length)]) }

    frames.append(WalkFrame(
        status: "A palindrome is defined by its centre, not its endpoints — so enumerate centres. There are " +
            "\(2 * s.count - 1) of them here: \(s.count) characters and \(s.count - 1) gaps.",
        cells: s.map { CellView(String($0)) }, readout: "\(2 * s.count - 1) centres"))

    for centre in s.indices {
        for even in [false, true] {
            var left = centre
            var right = even ? centre + 1 : centre
            if even && (right >= s.count || s[left] != s[right]) { continue }
            while left >= 0 && right < s.count && s[left] == s[right] {
                left -= 1
                right += 1
            }
            let length = right - left - 1
            let start = left + 1
            let improved = length > bestLength
            if improved {
                bestLength = length
                bestStart = start
            }
            let bs = bestStart, bl = bestLength
            frames.append(WalkFrame(
                status: "\(even ? "Even" : "Odd") centre at \(even ? "the gap after index \(centre)" : "index \(centre)"): " +
                    "expanded to \"\(sub(start, length))\", length \(length). " +
                    (improved ? "New best." : "Shorter than the best so far (\(bestLength))."),
                cells: s.enumerated().map { i, c in
                    CellView(String(c), inUntil(i, start, start + length) ? (improved ? .result : .window) : inUntil(i, bs, bs + bl) ? .done : .idle)
                },
                pointers: [start: "l", start + length - 1: "r"],
                readout: "best: \"\(sub(bestStart, bestLength))\" (\(bestLength))"))
        }
    }

    frames.append(WalkFrame(
        status: "Longest palindromic substring: \"\(sub(bestStart, bestLength))\", length \(bestLength). " +
            "\(2 * s.count - 1) centres each expanding at most n/2 gives O(n²) time in O(1) space. Manacher reaches O(n) by " +
            "initialising each centre's radius from its mirror — the same window trick the Z-algorithm uses.",
        cells: s.enumerated().map { i, c in CellView(String(c), inUntil(i, bestStart, bestStart + bestLength) ? .result : .dim) },
        readout: "\"\(sub(bestStart, bestLength))\" · O(n²) time, O(1) space"))
    return frames
}

func longestUniqueWindowFrames() -> [WalkFrame] {
    let s = Array("abcabcbb")
    var frames: [WalkFrame] = []
    var seen: [Character: Int] = [:]
    var start = 0, best = 0, bestStart = 0

    frames.append(WalkFrame(
        status: "Longest substring without a repeated character. A window that only ever grows on the right and " +
            "shrinks on the left visits each index twice at most.",
        cells: s.map { CellView(String($0), .idle) }, readout: "best = 0"))

    for (end, ch) in s.enumerated() {
        let previous = seen[ch]
        let moved = previous != nil && previous! >= start
        if moved { start = previous! + 1 }
        seen[ch] = end
        let length = end - start + 1
        let improved = length > best
        if improved {
            best = length
            bestStart = start
        }
        let st = start
        frames.append(WalkFrame(
            status: moved
                ? "'\(ch)' repeats (last seen at index \(previous!)) — pull start to \(start) so the window stays unique. Length \(length)."
                : "'\(ch)' is new to the window — extend right. Length \(length)" + (improved ? ", a new best." : "."),
            cells: s.enumerated().map { i, c in CellView(String(c), i == end ? .active : inUntil(i, st, end) ? .window : .idle) },
            pointers: [start: "start", end: "end"], readout: "window = \(length) · best = \(best)"))
    }

    frames.append(WalkFrame(
        status: "Longest unique window is \"\(String(s[bestStart..<(bestStart + best)]))\" at length " +
            "\(best). Neither pointer ever moves backwards, which is what keeps it linear.",
        cells: s.enumerated().map { i, c in CellView(String(c), inUntil(i, bestStart, bestStart + best) ? .result : .dim) },
        readout: "best = \(best)"))
    return frames
}

func dedupeInPlaceFrames() -> [WalkFrame] {
    var a = [1, 1, 2, 2, 3, 4, 4, 5]
    var frames: [WalkFrame] = []
    var write = 0

    func row(_ read: Int) -> [CellView] {
        a.enumerated().map { i, v in CellView("\(v)", i == read ? .active : i == write ? .done : i < write ? .window : .idle) }
    }

    frames.append(WalkFrame(
        status: "Remove duplicates from a sorted array in place. Here the two pointers move the same direction at " +
            "different rates: one reads, one writes.",
        cells: row(0), pointers: [0: "both"]))

    for read in 1..<a.count {
        let duplicate = a[read] == a[write]
        if !duplicate {
            write += 1
            a[write] = a[read]
        }
        frames.append(WalkFrame(
            status: duplicate
                ? "a[\(read)] = \(a[read]) equals the last kept value — read moves on, write stays put."
                : "a[\(read)] = \(a[read]) is new — copy it to index \(write).",
            cells: row(read), pointers: read == write ? [read: "both"] : [write: "write", read: "read"]))
    }

    frames.append(WalkFrame(
        status: "First \(write + 1) slots hold the distinct values; everything past them is stale. No extra array was allocated.",
        cells: a.enumerated().map { i, v in CellView("\(v)", i <= write ? .result : .dim) }, readout: "length = \(write + 1)"))
    return frames
}
