import SwiftUI

// Port of GeometryLabs.kt. Segment Intersection, Rotating Calipers, Convex Hull and Area & Perimeter
// as tabbed storyboards on one plane: a dot grid, lettered points, segments and a filled polygon,
// with the value being computed riding on the figure as a bubble. Under it, a row of term tiles or a
// formula, then chips and a headline. Every number is computed from the coordinates.

enum GeoTone { case idle, blue, green, active, answer, red }

struct GeoPt { let x: Double; let y: Double; init(_ x: Double, _ y: Double) { self.x = x; self.y = y } }

struct GeoPoint {
    let at: GeoPt
    var label: String? = nil
    var tone: GeoTone = .idle
    /// Drawn as a red ✕ instead of a dot (a point Graham scan popped).
    var cross = false
    var dim = false
}

struct GeoSeg {
    let a: GeoPt
    let b: GeoPt
    var tone: GeoTone = .blue
    var dashed = false
    var width: CGFloat = 2.5
    /// Run the line across the whole plane (a caliper).
    var extend = false
}

struct GeoBubble { let at: GeoPt; let text: String; var tone: GeoTone = .active; var dx: CGFloat = 0; var dy: CGFloat = 0 }

enum GeoTileTone { case pending, current, done }
struct GeoTile { let top: String; let value: String; let tone: GeoTileTone }

struct GeoFrame {
    var polygon: [GeoPt] = []
    var polygonTone: GeoTone = .blue
    var segs: [GeoSeg] = []
    var points: [GeoPoint] = []
    var bubbles: [GeoBubble] = []
    var tiles: [GeoTile] = []
    var formula: [String] = []
    var chips: [StoryChip] = []
    let headline: String
    let body: String
}

struct GeoTab { let label: String; let frames: [GeoFrame]; let legend: [(Color, SwatchStyle, String)] }

struct GeoBounds { let minX: Double; let maxX: Double; let minY: Double; let maxY: Double }

// MARK: - Shared math

private func orient(_ a: GeoPt, _ b: GeoPt, _ c: GeoPt) -> Double { (b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x) }
private func dist(_ a: GeoPt, _ b: GeoPt) -> Double { ((a.x - b.x) * (a.x - b.x) + (a.y - b.y) * (a.y - b.y)).squareRoot() }

/// 25 → "25", 2.5 → "2.5", 5.656 → "5.66".
private func num(_ v: Double) -> String {
    if abs(v - v.rounded()) < 1e-9 { return "\(Int(v.rounded()))" }
    let s = String(format: "%.2f", v)
    return s.hasSuffix("0") ? String(s.dropLast()) : s
}
private func signed(_ v: Double) -> String { v > 0 ? "+\(num(v))" : v < 0 ? "−\(num(-v))" : "0" }
/// A factor in a printed product, bracketed when negative: "(−2)".
private func factor(_ v: Double) -> String { v < 0 ? "(−\(num(-v)))" : num(v) }
private func coord(_ p: GeoPt) -> String { "(\(num(p.x)), \(num(p.y)))" }

// MARK: - Segment intersection

private func segmentTab(_ label: String, _ p1: GeoPt, _ p2: GeoPt, _ q1: GeoPt, _ q2: GeoPt, kind: Int) -> GeoTab {
    let o = [orient(p1, p2, q1), orient(p1, p2, q2), orient(q1, q2, p1), orient(q1, q2, p2)]
    let names = ["o₁", "o₂", "o₃", "o₄"]
    let tops = ["o₁ p→q₁", "o₂ p→q₂", "o₃ q→p₁", "o₄ q→p₂"]
    let args = ["p₁, p₂, q₁", "p₁, p₂, q₂", "q₁, q₂, p₁", "q₁, q₂, p₂"]
    let tested = [q1, q2, p1, p2]
    let testedName = ["q₁", "q₂", "p₁", "p₂"]
    let lineOf = ["p", "p", "q", "q"]
    func bubble(_ k: Int, _ tone: GeoTone) -> GeoBubble {
        let at = tested[k]
        let side: (CGFloat, CGFloat) = at.x >= 7.5 ? (0, 22) : (at.y >= 4.5 || at.y <= 0.5) ? (36, 0) : (0, 22)
        return GeoBubble(at: at, text: "\(names[k]) \(o[k] > 0 ? "+" : o[k] < 0 ? "−" : "0")", tone: tone, dx: side.0, dy: side.1)
    }
    func base(current: Int?, done: Int, hit: GeoPt? = nil) -> GeoFrame {
        var points = [GeoPoint(at: p1, label: "p₁", tone: .blue), GeoPoint(at: p2, label: "p₂", tone: .blue),
                      GeoPoint(at: q1, label: "q₁", tone: .green), GeoPoint(at: q2, label: "q₂", tone: .green)]
        if let current { points[[2, 3, 0, 1][current]].tone = .active }
        if let hit { points.append(GeoPoint(at: hit, label: coord(hit), tone: .answer)) }
        var bubbles = (0..<done).map { bubble($0, .green) }
        if let current { bubbles.append(bubble(current, .active)) }
        let tiles = (0..<4).map { k in
            GeoTile(top: tops[k], value: k < done || k == current ? signed(o[k]) : "?", tone: k == current ? .current : k < done ? .done : .pending)
        }
        return GeoFrame(segs: [GeoSeg(a: p1, b: p2, tone: .blue), GeoSeg(a: q1, b: q2, tone: .green)], points: points, bubbles: bubbles,
                        tiles: tiles, headline: "", body: "")
    }
    func with(_ f: GeoFrame, formula: [String], chips: [StoryChip], _ headline: String, _ body: String) -> GeoFrame {
        var g = GeoFrame(headline: headline, body: body)
        g.segs = f.segs; g.points = f.points; g.bubbles = f.bubbles; g.tiles = f.tiles; g.formula = formula; g.chips = chips
        return g
    }
    var frames = [with(base(current: nil, done: 0), formula: ["orient(a, b, c) = (b − a) × (c − a)"], chips: [StoryChip("tests", "0 of 4")],
                       "Two segments: p from p₁ to p₂, and q from q₁ to q₂.",
                       "Four orientation tests decide whether they meet, using only multiplication and subtraction.")]
    for k in 0..<4 {
        let side = o[k] > 0 ? "left" : o[k] < 0 ? "right" : "exactly on the line"
        let headline = o[k] == 0 ? "\(testedName[k]) lies {exactly on} the line through \(lineOf[k]) (\(names[k]) = 0)."
            : "\(testedName[k]) is {\(side)} of \(lineOf[k]) (\(names[k]) = \(signed(o[k])))."
        let body: String
        switch k {
        case 1: body = o[0] * o[1] < 0 ? "q's ends are on opposite sides of p, so q crosses p's line." : o[1] == 0 ? "A zero means collinear: q₂ is on p's line, maybe on p itself." : "Both of q's ends are on one side of p."
        case 3: body = o[2] * o[3] < 0 ? "p's ends are on opposite sides of q, so p crosses q's line." : o[3] == 0 ? "p₂ is on q's line too." : "Both of p's ends are on one side of q."
        default: body = "The sign says which side of the line through \(lineOf[k]) the point is on: + left, − right, 0 on it."
        }
        frames.append(with(base(current: k, done: k), formula: ["\(names[k]) = orient(\(args[k])) = {\(signed(o[k]))}"],
                           chips: [StoryChip("tests", "\(k + 1) of 4")], headline, body))
    }
    // Verdict and answer, per case.
    let t = o[2] / (o[2] - o[3])
    let hit = GeoPt(p1.x + t * (p2.x - p1.x), p1.y + t * (p2.y - p1.y))
    switch kind {
    case 0:
        frames.append(with(base(current: nil, done: 4, hit: hit), formula: ["o₁ ≠ o₂ and o₃ ≠ o₄ → {v:intersect}"],
                           chips: [StoryChip("tests", "4 of 4"), StoryChip("verdict", "cross", .answer)],
                           "Both pairs of signs are opposite, so the segments {v:cross}.",
                           "q's ends lie on opposite sides of p, and p's ends on opposite sides of q."))
        frames.append(with(base(current: nil, done: 4, hit: hit),
                           formula: ["t = o₃ / (o₃ − o₄) = \(num(t))", "p₁ + \(num(t))·(p₂ − p₁) = {v:\(coord(hit))}"],
                           chips: [StoryChip("point", coord(hit), .answer), StoryChip("cost", "O(1)")],
                           "The crossing point is {v:\(coord(hit))}.",
                           "Four cross products and one division: O(1), with no slopes and no division by zero for vertical lines."))
    case 1:
        frames.append(with(base(current: nil, done: 4, hit: q2), formula: ["o₂ = 0 and q₂ is inside p's box → {v:touch}"],
                           chips: [StoryChip("tests", "4 of 4"), StoryChip("verdict", "touch", .answer)],
                           "o₂ is 0 and q₂ lies within p's range, so the segments {v:touch} at q₂.",
                           "A zero only says collinear. The bounding-box check decides whether the point is actually on the segment."))
        frames.append(with(base(current: nil, done: 4, hit: q2), formula: ["min(p.x) ≤ q₂.x ≤ max(p.x) → {v:\(coord(q2))}"],
                           chips: [StoryChip("point", coord(q2), .answer), StoryChip("cost", "O(1)")],
                           "They meet at q₂, {v:\(coord(q2))}, an endpoint.",
                           "Skipping the zero case is the classic bug: touching segments would be reported as missing each other."))
    default:
        let pr = [min(p1.x, p2.x), max(p1.x, p2.x)], qr = [min(q1.x, q2.x), max(q1.x, q2.x)]
        frames.append(with(base(current: nil, done: 4), formula: ["all four = 0 → compare ranges", "[\(num(pr[0])), \(num(pr[1]))] vs [\(num(qr[0])), \(num(qr[1]))] → {w:apart}"],
                           chips: [StoryChip("tests", "4 of 4"), StoryChip("verdict", "apart", .warn)],
                           "All four signs are 0: the segments share a line but {w:do not overlap}.",
                           "Collinear segments meet only if their ranges overlap, and here \(num(pr[1])) < \(num(qr[0]))."))
        frames.append(with(base(current: nil, done: 4), formula: ["max(p.x) = \(num(pr[1])) < \(num(qr[0])) = min(q.x) → {w:no intersection}"],
                           chips: [StoryChip("verdict", "none", .warn), StoryChip("cost", "O(1)")],
                           "Same line, different stretches of it: {w:no intersection}.",
                           "Had the ranges overlapped, the overlap itself would be the answer, a segment rather than a point."))
    }
    return GeoTab(label: label, frames: frames,
                  legend: [(SimColors.active, .fill, "Current test"), (SimColors.green, .fill, "Tested"), (SimColors.answer, .fill, "Intersection")])
}

private func segmentTabs() -> [GeoTab] {
    [segmentTab("Crossing", GeoPt(1, 1), GeoPt(8, 4), GeoPt(2, 5), GeoPt(7, 0), kind: 0),
     segmentTab("Touching", GeoPt(1, 1), GeoPt(7, 4), GeoPt(3, 5), GeoPt(5, 3), kind: 1),
     segmentTab("Collinear", GeoPt(1, 1), GeoPt(3, 2), GeoPt(5, 3), GeoPt(7, 4), kind: 2)]
}

// MARK: - Hull shared by calipers and convex hull

private let hullPts = [GeoPt(3, 0), GeoPt(7, 1), GeoPt(9, 3), GeoPt(6, 5), GeoPt(3, 5), GeoPt(1, 4), GeoPt(0, 2)]
private let hullNames = ["A", "B", "C", "D", "E", "F", "G"]
private let innerPts = [GeoPt(2, 3), GeoPt(4, 2), GeoPt(6, 3), GeoPt(5, 4)]
private let innerNames = ["H", "I", "J", "K"]

// MARK: - Rotating calipers

private func calipersTabs() -> [GeoTab] {
    let h = hullPts, names = hullNames, n = h.count
    func area(_ i: Int, _ j: Int) -> Double { abs(orient(h[i], h[(i + 1) % n], h[j % n])) }
    func label(_ i: Int) -> String { names[i % n] }
    func points(_ lit: Set<Int>) -> [GeoPoint] {
        h.indices.map { GeoPoint(at: h[$0], label: names[$0], tone: lit.contains($0) ? .active : .blue) }
            + innerPts.map { GeoPoint(at: $0, tone: .idle, dim: true) }
    }
    // Antipodal vertex per edge, advanced monotonically: the caliper never goes back.
    var anti: [Int] = []
    var j = 1
    for i in 0..<n {
        while area(i, j + 1) > area(i, j) { j += 1 }
        anti.append(j % n)
    }
    func calipers(_ i: Int, _ a: Int) -> [GeoSeg] {
        let p = h[i], q = h[(i + 1) % n]
        let d = GeoPt(q.x - p.x, q.y - p.y)
        return [GeoSeg(a: p, b: q, tone: .active, width: 4),
                GeoSeg(a: p, b: q, tone: .active, dashed: true, width: 1.5, extend: true),
                GeoSeg(a: h[a], b: GeoPt(h[a].x + d.x, h[a].y + d.y), tone: .active, dashed: true, width: 1.5, extend: true)]
    }
    let hullSegs = { (h.indices.map { GeoSeg(a: h[$0], b: h[($0 + 1) % n], tone: .blue) }) }()
    func caliperFormula(_ i: Int, _ a: Int) -> [String] {
        let e = "\(label(i))\(label(i + 1))"
        return ["|\(e) × \(label(i))\(label(a))| = {\(num(area(i, a)))}",
                "> |\(e) × \(label(i))\(label(a + 1))| = \(num(area(i, a + 1))) → stay on \(label(a))"]
    }

    // Diameter
    var dia = [GeoFrame(polygon: h, segs: hullSegs, points: points([]), chips: [StoryChip("hull", "\(n) vertices")],
                        headline: "The diameter is the farthest pair of points, and both ends are always hull vertices.",
                        body: "Rotating calipers checks O(n) antipodal pairs instead of all n² pairs.")]
    var best: (Int, Int, Double) = (0, 0, 0)
    for i in 0..<n {
        let a = anti[i]
        let c1 = (i, a, dist(h[i], h[a])), c2 = ((i + 1) % n, a, dist(h[(i + 1) % n], h[a]))
        let win = c1.2 >= c2.2 ? c1 : c2, lose = c1.2 >= c2.2 ? c2 : c1
        let improved = win.2 > best.2
        if improved { best = win }
        var segs = hullSegs + calipers(i, a)
        segs.append(GeoSeg(a: h[lose.0], b: h[lose.1], tone: .idle, dashed: true, width: 1.5))
        segs.append(GeoSeg(a: h[best.0], b: h[best.1], tone: .answer, width: 3))
        let mid = { (x: Int, y: Int) in GeoPt((h[x].x + h[y].x) / 2, (h[x].y + h[y].y) / 2) }
        let pair = "\(label(best.0))–\(label(best.1))"
        dia.append(GeoFrame(polygon: h, segs: segs, points: points([i, (i + 1) % n, a]),
                            bubbles: [GeoBubble(at: mid(lose.0, lose.1), text: num(lose.2), tone: .idle), GeoBubble(at: mid(best.0, best.1), text: num(best.2), tone: .answer)],
                            formula: caliperFormula(i, a),
                            chips: [StoryChip("best", "\(pair) \(num(best.2))", .answer), StoryChip("edge", "\(i + 1) of \(n)")],
                            headline: "\(label(a)) is farthest from edge \(label(i))\(label(i + 1)), so try \(label(i))–\(label(a)) and \(label(i + 1))–\(label(a)). "
                                + "{v:\(label(win.0))–\(label(win.1))} is longer.",
                            body: improved ? "Only the \(n) hull vertices can be farthest apart. Each edge moves the opposite caliper forward, never back."
                                : "Still shorter than \(pair), so the best pair stands."))
    }
    let pair = "\(label(best.0))–\(label(best.1))"
    dia.append(GeoFrame(polygon: h, segs: hullSegs + [GeoSeg(a: h[best.0], b: h[best.1], tone: .answer, width: 3)], points: points([best.0, best.1]),
                        bubbles: [GeoBubble(at: GeoPt((h[best.0].x + h[best.1].x) / 2, (h[best.0].y + h[best.1].y) / 2), text: num(best.2), tone: .answer)],
                        formula: ["diameter = |\(pair)| = {v:\(num(best.2))}"],
                        chips: [StoryChip("best", "\(pair) \(num(best.2))", .answer), StoryChip("edges", "\(n) of \(n)")],
                        headline: "The diameter is {v:\(pair) = \(num(best.2))}.",
                        body: "One lap of the calipers: O(n) once the hull is built, against n² for every pair of points."))

    // Width
    var wid = [GeoFrame(polygon: h, segs: hullSegs, points: points([]), chips: [StoryChip("hull", "\(n) vertices")],
                        headline: "The width is the narrowest gap between two parallel lines that hold the hull.",
                        body: "One of the two lines always lies along a hull edge, so trying each edge is enough.")]
    var bestW = (0, 0, Double.infinity)
    for i in 0..<n {
        let a = anti[i]
        let len = dist(h[i], h[(i + 1) % n])
        let w = area(i, a) / len
        let improved = w < bestW.2
        if improved { bestW = (i, a, w) }
        func foot(_ e: Int, _ v: Int) -> GeoPt {
            let p = h[e], q = h[(e + 1) % n], d = GeoPt(q.x - p.x, q.y - p.y)
            let t = ((h[v].x - p.x) * d.x + (h[v].y - p.y) * d.y) / (d.x * d.x + d.y * d.y)
            return GeoPt(p.x + t * d.x, p.y + t * d.y)
        }
        let f = foot(i, a), bf = foot(bestW.0, bestW.1)
        var segs = hullSegs + calipers(i, a)
        segs.append(GeoSeg(a: h[a], b: f, tone: .idle, dashed: true, width: 1.5))
        segs.append(GeoSeg(a: h[bestW.1], b: bf, tone: .answer, width: 3))
        wid.append(GeoFrame(polygon: h, segs: segs, points: points([i, (i + 1) % n, a]),
                            bubbles: [GeoBubble(at: GeoPt((h[bestW.1].x + bf.x) / 2, (h[bestW.1].y + bf.y) / 2), text: num(bestW.2), tone: .answer)],
                            formula: ["|\(label(i))\(label(i + 1)) × \(label(i))\(label(a))| / |\(label(i))\(label(i + 1))|", "= \(num(area(i, a))) / \(num(len)) = {\(num(w))}"],
                            chips: [StoryChip("narrowest", num(bestW.2), .answer), StoryChip("edge", "\(i + 1) of \(n)")],
                            headline: "Edge \(label(i))\(label(i + 1)) with \(label(a)) opposite: the calipers are {\(num(w))} apart.",
                            body: improved ? "That is the narrowest so far. The same antipodal walk as the diameter, measuring a height instead of a distance."
                                : "Wider than \(num(bestW.2)), so the narrowest stays."))
    }
    let bf: GeoPt = {
        let p = h[bestW.0], q = h[(bestW.0 + 1) % n], d = GeoPt(q.x - p.x, q.y - p.y), v = h[bestW.1]
        let t = ((v.x - p.x) * d.x + (v.y - p.y) * d.y) / (d.x * d.x + d.y * d.y)
        return GeoPt(p.x + t * d.x, p.y + t * d.y)
    }()
    wid.append(GeoFrame(polygon: h, segs: hullSegs + calipers(bestW.0, bestW.1) + [GeoSeg(a: h[bestW.1], b: bf, tone: .answer, width: 3)],
                        points: points([bestW.0, (bestW.0 + 1) % n, bestW.1]),
                        bubbles: [GeoBubble(at: GeoPt((h[bestW.1].x + bf.x) / 2, (h[bestW.1].y + bf.y) / 2), text: num(bestW.2), tone: .answer)],
                        formula: ["width = {v:\(num(bestW.2))}, edge \(label(bestW.0))\(label(bestW.0 + 1)) to \(label(bestW.1))"],
                        chips: [StoryChip("width", num(bestW.2), .answer), StoryChip("edges", "\(n) of \(n)")],
                        headline: "The width is {v:\(num(bestW.2))}, set by edge \(label(bestW.0))\(label(bestW.0 + 1)) and \(label(bestW.1)).",
                        body: "It is the narrowest slot the shape could slide through, found in one O(n) lap."))
    let legend: [(Color, SwatchStyle, String)] = [(SimColors.blue, .fill, "Hull"), (SimColors.active, .fill, "Caliper pair"), (SimColors.answer, .fill, "Best so far")]
    return [GeoTab(label: "Diameter", frames: dia, legend: legend), GeoTab(label: "Width", frames: wid, legend: legend)]
}

// MARK: - Convex hull

private func hullTabs() -> [GeoTab] {
    let all = hullPts + innerPts
    let names = hullNames + innerNames
    let pivot = 0
    let order = [pivot] + all.indices.filter { $0 != pivot }.sorted {
        let a = atan2(all[$0].y - all[pivot].y, all[$0].x - all[pivot].x), b = atan2(all[$1].y - all[pivot].y, all[$1].x - all[pivot].x)
        return a != b ? a < b : dist(all[pivot], all[$0]) < dist(all[pivot], all[$1])
    }
    func crossText(_ a: Int, _ b: Int, _ c: Int) -> String {
        let p = all[a], q = all[b], r = all[c]
        let v = orient(p, q, r)
        return "cross(\(names[a]), \(names[b]), \(names[c])) = \(factor(q.x - p.x))\(factor(r.y - p.y)) − \(factor(q.y - p.y))\(factor(r.x - p.x)) = {\(v > 0 ? "+" : "")\(num(v))} \(v > 0 ? ">" : "≤") 0"
    }
    func pts(stack: [Int], popped: Set<Int>, testing: Int?, reached: Set<Int>) -> [GeoPoint] {
        all.indices.map { i in
            if popped.contains(i) { return GeoPoint(at: all[i], label: names[i], tone: .red, cross: true) }
            if i == testing { return GeoPoint(at: all[i], label: names[i], tone: .active) }
            if stack.contains(i) { return GeoPoint(at: all[i], label: names[i], tone: .blue) }
            return GeoPoint(at: all[i], label: names[i], tone: .idle, dim: !reached.contains(i))
        }
    }
    func chain(_ stack: [Int]) -> [GeoSeg] { zip(stack, stack.dropFirst()).map { GeoSeg(a: all[$0], b: all[$1], tone: .blue) } }

    // Graham scan
    var graham = [GeoFrame(points: pts(stack: [pivot], popped: [], testing: nil, reached: [pivot]),
                           formula: ["sorted: " + order.dropFirst().map { names[$0] }.joined(separator: " ")],
                           chips: [StoryChip("stack", names[pivot]), StoryChip("popped", "0")],
                           headline: "Start from the lowest point, {\(names[pivot])}, and sort the rest by angle around it.",
                           body: "Walking the points in that order, the hull only ever turns left.")]
    var stack = [order[0], order[1]]
    var popped = Set<Int>()
    var reached: Set<Int> = [order[0], order[1]]
    for k in 2..<order.count {
        let p = order[k]
        reached.insert(p)
        while stack.count >= 2 {
            let a = stack[stack.count - 2], b = stack[stack.count - 1]
            let v = orient(all[a], all[b], all[p])
            var segs = chain(stack)
            segs.append(GeoSeg(a: all[b], b: all[p], tone: .active, dashed: true, width: 2))
            let text = v > 0 ? "+\(num(v)) left" : "\(signed(v)) right"
            let chips = [StoryChip("stack", stack.map { names[$0] }.joined(separator: " ")), StoryChip("popped", "\(popped.count)")]
            if v > 0 {
                stack.append(p)
                graham.append(GeoFrame(segs: segs, points: pts(stack: stack, popped: popped, testing: p, reached: reached),
                                       bubbles: [GeoBubble(at: GeoPt((all[b].x + all[p].x) / 2, (all[b].y + all[p].y) / 2), text: text, tone: .active, dy: 16)],
                                       formula: [crossText(a, b, p)], chips: [StoryChip("stack", stack.map { names[$0] }.joined(separator: " ")), StoryChip("popped", "\(popped.count)")],
                                       headline: "{+\(num(v))} is a left turn, so \(names[p]) is pushed onto the stack.",
                                       body: popped.isEmpty ? "A left turn keeps the chain convex, so \(names[p]) might be on the hull."
                                           : "\(popped.count) inner point\(popped.count == 1 ? " was" : "s were") already popped on right turns. The chain now has \(stack.count) vertices."))
                break
            }
            popped.insert(b)
            stack.removeLast()
            graham.append(GeoFrame(segs: segs, points: pts(stack: stack + [b], popped: popped, testing: p, reached: reached),
                                   bubbles: [GeoBubble(at: GeoPt((all[b].x + all[p].x) / 2, (all[b].y + all[p].y) / 2), text: text, tone: .red, dy: 16)],
                                   formula: [crossText(a, b, p)], chips: chips,
                                   headline: "{w:\(signed(v))} is a right turn, so \(names[b]) is popped.",
                                   body: "\(names[b]) sits inside the triangle \(names[a]), \(names[p]) and the pivot, so it cannot be on the hull."))
        }
    }
    let hullOrder = stack.map { names[$0] }.joined(separator: " ")
    graham.append(GeoFrame(polygon: stack.map { all[$0] }, segs: chain(stack + [stack[0]]), points: pts(stack: stack, popped: popped, testing: nil, reached: Set(all.indices)),
                           formula: ["hull = {v:\(hullOrder)}"],
                           chips: [StoryChip("hull", hullOrder, .answer), StoryChip("popped", "\(popped.count)")],
                           headline: "Close the stack back to \(names[pivot]): the hull is {v:\(hullOrder)}.",
                           body: "Every point is pushed once and popped at most once, so after the O(n log n) sort the scan is O(n)."))

    // Jarvis march
    let n = all.count
    var jarvis = [GeoFrame(points: pts(stack: [pivot], popped: [], testing: pivot, reached: Set(all.indices)), formula: ["start = lowest point = \(names[pivot])"],
                           chips: [StoryChip("hull", names[pivot]), StoryChip("checks", "0")],
                           headline: "Gift wrapping starts at the lowest point, {\(names[pivot])}, which must be on the hull.",
                           body: "From each hull point it finds the next by checking every other point.")]
    var hull = [pivot]
    var cur = pivot
    var checks = 0
    repeat {
        var next = (cur + 1) % n
        for c in 0..<n where c != cur {
            if orient(all[cur], all[next], all[c]) < 0 { next = c }
        }
        checks += n - 1
        let rays = all.indices.filter { $0 != cur }.map { GeoSeg(a: all[cur], b: all[$0], tone: .idle, dashed: true, width: 1) }
        let segs = rays + chain(hull) + [GeoSeg(a: all[cur], b: all[next], tone: .active, width: 3)]
        let closing = next == pivot
        jarvis.append(GeoFrame(segs: segs, points: pts(stack: hull, popped: [], testing: cur, reached: Set(all.indices)),
                               formula: ["checked \(n - 1) points → next = {\(names[next])}"],
                               chips: [StoryChip("hull", (hull.map { names[$0] }).joined(separator: " ")), StoryChip("checks", "\(checks)")],
                               headline: closing ? "From \(names[cur]), the wrap comes back to {\(names[next])}: the hull is closed."
                                   : "From \(names[cur]), every point is left of \(names[cur])→\(names[next]), so {\(names[next])} is next.",
                               body: "Each hull vertex costs one pass over all n points, so the march is O(nh)."))
        if closing { break }
        hull.append(next)
        cur = next
    } while hull.count <= n
    let jOrder = hull.map { names[$0] }.joined(separator: " ")
    jarvis.append(GeoFrame(polygon: hull.map { all[$0] }, segs: chain(hull + [pivot]), points: pts(stack: hull, popped: [], testing: nil, reached: Set(all.indices)),
                           formula: ["\(hull.count) passes × \(n - 1) checks = {v:\(checks)}"],
                           chips: [StoryChip("hull", jOrder, .answer), StoryChip("checks", "\(checks)")],
                           headline: "Same hull, {v:\(jOrder)}, in \(hull.count) passes.",
                           body: "O(nh) beats Graham's O(n log n) when the hull has few vertices, and loses when most points are on it."))
    return [GeoTab(label: "Graham scan", frames: graham,
                   legend: [(SimColors.blue, .fill, "Stack (hull so far)"), (SimColors.active, .fill, "Testing"), (SimColors.red, .fill, "Popped"), (Color.gray.opacity(0.4), .fill, "Not reached")]),
            GeoTab(label: "Jarvis march", frames: jarvis,
                   legend: [(SimColors.blue, .fill, "Hull so far"), (SimColors.active, .fill, "Current"), (Color.gray.opacity(0.4), .fill, "Checked")])]
}

// MARK: - Area & perimeter

private func areaTabs() -> [GeoTab] {
    let pts = [GeoPt(1, 4), GeoPt(4, 2), GeoPt(7, 3), GeoPt(7, 0), GeoPt(1, 0)]
    let names = ["A", "B", "C", "D", "E"]
    // Walked E → D → C → B → A → E, the order the terms are listed in.
    let walk = [4, 3, 2, 1, 0]
    let edges = walk.indices.map { (walk[$0], walk[($0 + 1) % walk.count]) }
    func pointsFor(_ lit: Set<Int>) -> [GeoPoint] {
        pts.indices.map { GeoPoint(at: pts[$0], label: "\(names[$0]) \(coord(pts[$0]).replacingOccurrences(of: " ", with: ""))", tone: lit.contains($0) ? .active : .idle) }
    }
    func segs(done: Int, current: Int?) -> [GeoSeg] {
        edges.indices.map { k in
            let (a, b) = edges[k]
            if k == current { return GeoSeg(a: pts[a], b: pts[b], tone: .active, width: 3.5) }
            return k < done ? GeoSeg(a: pts[a], b: pts[b], tone: .green) : GeoSeg(a: pts[a], b: pts[b], tone: .idle, dashed: true, width: 1.5)
        }
    }
    func edgeName(_ k: Int) -> String { "\(names[edges[k].0])→\(names[edges[k].1])" }

    let terms = edges.map { pts[$0.0].x * pts[$0.1].y - pts[$0.1].x * pts[$0.0].y }
    func areaTiles(_ done: Int, _ current: Int?) -> [GeoTile] {
        edges.indices.map { GeoTile(top: edgeName($0), value: signed(terms[$0]).replacingOccurrences(of: "+0", with: "0"), tone: $0 == current ? .current : $0 < done ? .done : .pending) }
    }
    var area = [GeoFrame(polygon: pts, polygonTone: .green, segs: segs(done: 0, current: nil), points: pointsFor([]), tiles: areaTiles(0, nil),
                         formula: ["Σ (xᵢ·yᵢ₊₁ − xᵢ₊₁·yᵢ) over every edge"], chips: [StoryChip("sum", "0")],
                         headline: "The shoelace formula adds one cross term per edge, walking around the polygon.",
                         body: "Each term is twice the signed area of the triangle an edge makes with the origin.")]
    var sum = 0.0
    for k in edges.indices {
        let (a, b) = edges[k]
        sum += terms[k]
        let pa = pts[a], pb = pts[b]
        let dent = a == 1 || b == 1
        area.append(GeoFrame(polygon: pts, polygonTone: .green, segs: segs(done: k, current: k), points: pointsFor([a, b]),
                             bubbles: [GeoBubble(at: GeoPt((pa.x + pb.x) / 2, (pa.y + pb.y) / 2), text: signed(terms[k]), tone: .active, dy: -14)],
                             tiles: areaTiles(k, k),
                             formula: ["x\(names[a])·y\(names[b]) − x\(names[b])·y\(names[a]) = \(num(pa.x))·\(num(pb.y)) − \(num(pb.x))·\(num(pa.y)) = {\(signed(terms[k]))}"],
                             chips: [StoryChip("term", signed(terms[k])), StoryChip("sum", num(sum))],
                             headline: "Edge \(edgeName(k)) adds {\(signed(terms[k]))}, so the running sum is \(num(sum)).",
                             body: k == edges.count - 1 ? "That closes the loop, and the negative term takes back area that was counted twice."
                                 : dent ? "B is a dent, yet the signed terms still work out. Area = |Σ| ÷ 2 once A→E is added."
                                 : "Terms can be negative. They cancel area counted twice. Area = |Σ| ÷ 2 at the end."))
    }
    area.append(GeoFrame(polygon: pts, polygonTone: .green, segs: segs(done: edges.count, current: nil), points: pointsFor([]), tiles: areaTiles(edges.count, nil),
                         formula: ["area = |\(num(sum))| ÷ 2 = {v:\(num(abs(sum) / 2))}"],
                         chips: [StoryChip("sum", num(sum)), StoryChip("area", num(abs(sum) / 2), .answer)],
                         headline: "Area = |\(num(sum))| ÷ 2 = {v:\(num(abs(sum) / 2))} square units.",
                         body: "n terms, O(n), and it works for any simple polygon, dents included."))

    let lens = edges.map { dist(pts[$0.0], pts[$0.1]) }
    func lenTiles(_ done: Int, _ current: Int?) -> [GeoTile] {
        edges.indices.map { GeoTile(top: edgeName($0), value: num(lens[$0]), tone: $0 == current ? .current : $0 < done ? .done : .pending) }
    }
    var perim = [GeoFrame(polygon: pts, polygonTone: .green, segs: segs(done: 0, current: nil), points: pointsFor([]), tiles: lenTiles(0, nil),
                          formula: ["Σ √(Δx² + Δy²) over every edge"], chips: [StoryChip("total", "0")],
                          headline: "The perimeter adds up the length of each edge.",
                          body: "Each length is Pythagoras on the edge's run and rise.")]
    var total = 0.0
    for k in edges.indices {
        let (a, b) = edges[k]
        total += lens[k]
        let dx = abs(pts[b].x - pts[a].x), dy = abs(pts[b].y - pts[a].y)
        perim.append(GeoFrame(polygon: pts, polygonTone: .green, segs: segs(done: k, current: k), points: pointsFor([a, b]),
                              bubbles: [GeoBubble(at: GeoPt((pts[a].x + pts[b].x) / 2, (pts[a].y + pts[b].y) / 2), text: num(lens[k]), tone: .active, dy: -14)],
                              tiles: lenTiles(k, k),
                              formula: ["|\(edgeName(k))| = √(\(num(dx))² + \(num(dy))²) = {\(num(lens[k]))}"],
                              chips: [StoryChip("edge", num(lens[k])), StoryChip("total", num(total))],
                              headline: "Edge \(edgeName(k)) is {\(num(lens[k]))} long, so the total is \(num(total)).",
                              body: dx == 0 || dy == 0 ? "An axis-aligned edge needs no square root: its length is just the run or the rise."
                                  : "A slanted edge is the hypotenuse of its run and rise."))
    }
    perim.append(GeoFrame(polygon: pts, polygonTone: .green, segs: segs(done: edges.count, current: nil), points: pointsFor([]), tiles: lenTiles(edges.count, nil),
                          formula: ["perimeter = {v:\(num(total))}"], chips: [StoryChip("perimeter", num(total), .answer)],
                          headline: "Perimeter = {v:\(num(total))} units.",
                          body: "Unlike area, perimeter never cancels: every edge counts in full."))
    let legend: [(Color, SwatchStyle, String)] = [(SimColors.active, .fill, "Current edge"), (SimColors.green, .fill, "Term added"), (Color.gray.opacity(0.4), .fill, "Still to add")]
    return [GeoTab(label: "Area", frames: area, legend: legend),
            GeoTab(label: "Perimeter", frames: perim, legend: [(SimColors.active, .fill, "Current edge"), (SimColors.green, .fill, "Length added"), (Color.gray.opacity(0.4), .fill, "Still to add")])]
}

// MARK: - Lab

let geometryTopicIds: Set<String> = ["line_intersection", "rotating_calipers", "convex_hull", "polygon_area"]

private func geometryLab(_ topicId: String) -> (GeoBounds, [GeoTab]) {
    switch topicId {
    case "line_intersection": (GeoBounds(minX: 0, maxX: 8, minY: 0, maxY: 5), segmentTabs())
    case "rotating_calipers": (GeoBounds(minX: 0, maxX: 9, minY: 0, maxY: 5), calipersTabs())
    case "convex_hull": (GeoBounds(minX: 0, maxX: 9, minY: 0, maxY: 5), hullTabs())
    default: (GeoBounds(minX: 0, maxX: 8, minY: 0, maxY: 4.5), areaTabs())
    }
}

struct GeometryLab: View {
    private let bounds: GeoBounds
    private let tabs: [GeoTab]
    @State private var tab = 0
    @State private var playback: PlaybackState
    @Environment(\.palette) private var palette

    init(topicId: String) {
        (bounds, tabs) = geometryLab(topicId)
        _playback = State(initialValue: PlaybackState(stepCount: tabs[0].frames.count, speedMs: 1000))
    }

    var body: some View {
        let frames = tabs[tab].frames
        let frame = frames[min(playback.index, frames.count - 1)]
        VStack(alignment: .leading, spacing: 0) {
            LabCard {
                LabSegments(labels: tabs.map(\.label), selected: Binding(get: { tab }, set: { select($0) }))
                GeoPlane(bounds: bounds, frame: frame)
                    .frame(height: 196)
                    .background(Color.black.opacity(0.16), in: RoundedRectangle(cornerRadius: 14))
                    .padding(.top, 14)
                if !frame.tiles.isEmpty {
                    HStack(spacing: 5) { ForEach(frame.tiles.indices, id: \.self) { GeoTileView(tile: frame.tiles[$0]) } }.padding(.top, 12)
                }
                if !frame.formula.isEmpty {
                    VStack(spacing: 4) {
                        ForEach(frame.formula.indices, id: \.self) { i in
                            storyText(frame.formula[i], palette).font(AppFont.mono(14)).foregroundStyle(palette.onSurface.opacity(0.8))
                                .lineLimit(1).minimumScaleFactor(0.6)
                        }
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 12).padding(.horizontal, 10)
                    .background(SimColors.tint, in: RoundedRectangle(cornerRadius: 10))
                    .padding(.top, 12)
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
        playback = PlaybackState(stepCount: tabs[i].frames.count, speedMs: 1000)
    }
}

private func geoColor(_ tone: GeoTone, _ palette: Palette) -> Color {
    switch tone {
    case .idle: palette.muted.opacity(0.6)
    case .blue: SimColors.blue
    case .green: SimColors.green
    case .active: SimColors.active
    case .answer: SimColors.answer
    case .red: SimColors.red
    }
}

private func geoInk(_ tone: GeoTone, _ palette: Palette) -> Color {
    switch tone {
    case .idle: palette.muted
    case .blue: StoryTone.path.ink(palette)
    case .green: StoryTone.done.ink(palette)
    case .active: StoryTone.active.ink(palette)
    case .answer: StoryTone.answer.ink(palette)
    case .red: StoryTone.warn.ink(palette)
    }
}

private struct GeoPlane: View {
    let bounds: GeoBounds
    let frame: GeoFrame
    @Environment(\.palette) private var palette

    var body: some View {
        Canvas { ctx, size in
            let pad: CGFloat = 26
            func at(_ p: GeoPt) -> CGPoint {
                CGPoint(x: pad + (p.x - bounds.minX) / (bounds.maxX - bounds.minX) * (size.width - 2 * pad),
                        y: size.height - pad - (p.y - bounds.minY) / (bounds.maxY - bounds.minY) * (size.height - 2 * pad))
            }
            // Dot grid on the integer lattice.
            for x in Int(bounds.minX.rounded(.up))...Int(bounds.maxX) {
                for y in Int(bounds.minY.rounded(.up))...Int(bounds.maxY) {
                    let c = at(GeoPt(Double(x), Double(y)))
                    ctx.fill(Path(ellipseIn: CGRect(x: c.x - 1, y: c.y - 1, width: 2, height: 2)), with: .color(palette.muted.opacity(0.35)))
                }
            }
            if !frame.polygon.isEmpty {
                var p = Path()
                p.move(to: at(frame.polygon[0]))
                frame.polygon.dropFirst().forEach { p.addLine(to: at($0)) }
                p.closeSubpath()
                ctx.fill(p, with: .color(geoColor(frame.polygonTone, palette).opacity(0.12)))
            }
            for seg in frame.segs {
                var a = at(seg.a), b = at(seg.b)
                if seg.extend {
                    let dx = b.x - a.x, dy = b.y - a.y
                    let len = max((dx * dx + dy * dy).squareRoot(), 0.001)
                    let reach = size.width * 2
                    a = CGPoint(x: a.x - dx / len * reach, y: a.y - dy / len * reach)
                    b = CGPoint(x: b.x + dx / len * reach, y: b.y + dy / len * reach)
                }
                var p = Path(); p.move(to: a); p.addLine(to: b)
                ctx.stroke(p, with: .color(geoColor(seg.tone, palette)), style: StrokeStyle(lineWidth: seg.width, lineCap: .round, dash: seg.dashed ? [5, 4] : []))
            }
            for point in frame.points {
                let c = at(point.at)
                let color = geoColor(point.tone, palette)
                if point.cross {
                    var p = Path()
                    p.move(to: CGPoint(x: c.x - 6, y: c.y - 6)); p.addLine(to: CGPoint(x: c.x + 6, y: c.y + 6))
                    p.move(to: CGPoint(x: c.x + 6, y: c.y - 6)); p.addLine(to: CGPoint(x: c.x - 6, y: c.y + 6))
                    ctx.stroke(p, with: .color(SimColors.red), lineWidth: 2)
                } else {
                    let r: CGFloat = point.tone == .idle ? 3.5 : 5.5
                    ctx.fill(Path(ellipseIn: CGRect(x: c.x - r, y: c.y - r, width: 2 * r, height: 2 * r)), with: .color(point.dim ? color.opacity(0.5) : color))
                }
                if let label = point.label {
                    let ink = point.dim ? palette.muted.opacity(0.5) : geoInk(point.tone, palette)
                    let text = ctx.resolve(Text(label).font(AppFont.mono(11, .bold)).foregroundColor(ink))
                    // Labels sit outside the figure: left of points on the left half, right on the right half.
                    let left = point.at.x < (bounds.minX + bounds.maxX) / 2
                    let below = point.at.y <= bounds.minY + 0.3
                    ctx.draw(text, at: CGPoint(x: c.x + (left ? -9 : 9), y: c.y + (below ? 12 : -10)), anchor: left ? .trailing : .leading)
                }
            }
            for bubble in frame.bubbles {
                let c = at(bubble.at)
                let (fill, ink): (Color, Color) = switch bubble.tone {
                case .active: (SimColors.active, Color(hex: 0x1F1A0A))
                case .answer: (SimColors.answer, .white)
                case .green: (SimColors.green.opacity(0.3), StoryTone.done.ink(palette))
                case .red: (SimColors.red, .white)
                default: (palette.surface.opacity(0.9), palette.onSurface.opacity(0.8))
                }
                let text = ctx.resolve(Text(bubble.text).font(AppFont.mono(12, .bold)).foregroundColor(ink))
                let s = text.measure(in: CGSize(width: 200, height: 30))
                let mid = CGPoint(x: c.x + bubble.dx, y: c.y + bubble.dy)
                let rect = CGRect(x: mid.x - s.width / 2 - 6, y: mid.y - s.height / 2 - 3, width: s.width + 12, height: s.height + 6)
                ctx.fill(Path(roundedRect: rect, cornerRadius: 6), with: .color(fill))
                ctx.draw(text, at: mid)
            }
        }
        .clipShape(RoundedRectangle(cornerRadius: 14))
    }
}

private struct GeoTileView: View {
    let tile: GeoTile
    @Environment(\.palette) private var palette

    var body: some View {
        let (fill, ink): (Color, Color) = switch tile.tone {
        case .pending: (palette.muted.opacity(0.08), palette.muted.opacity(0.6))
        case .current: (SimColors.active, Color(hex: 0x1F1A0A))
        case .done: (SimColors.green.opacity(palette.dark ? 0.22 : 0.16), StoryTone.done.ink(palette))
        }
        VStack(spacing: 3) {
            Text(tile.top).font(AppFont.mono(10)).opacity(0.85)
            Text(tile.value).font(AppFont.mono(15, .bold))
        }
        .foregroundStyle(ink)
        .lineLimit(1).minimumScaleFactor(0.6)
        .frame(maxWidth: .infinity).frame(height: 50)
        .background(fill, in: RoundedRectangle(cornerRadius: 8))
    }
}
