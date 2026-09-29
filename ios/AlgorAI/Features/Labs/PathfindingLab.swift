import SwiftUI

// Port of PathfindingGridSection.kt: shortest-path search over a small walled grid, one frame per
// expansion. Dijkstra / A* / D* / UCS / IDA* and two grid-as-graph interview patterns.
//
// Drawn as the story card (docs mock for Dijkstra): each reached cell carries its distance, the
// frontier is dashed, the goal is ringed, and a step's headline names the cell it expands.

private let rows = 6
private let cols = 8

private struct PathFrame {
    let visited: Set<Int>
    let frontier: Set<Int>
    let current: Int?
    let path: Set<Int>
    let walls: Set<Int>
    let status: String
    /// The number drawn in a cell: its distance from S so far.
    var dist: [Int: Int] = [:]
    /// A hand-written step: `{…}` in the headline takes the current cell's colour. Nil narrates `status`.
    var headline: String? = nil
    var body = ""
    var chips: [StoryChip] = []

    var caption: String { headline.map { storyPlain($0) + (body.isEmpty ? "" : " " + body) } ?? status }
}

private struct PathVariant { let label: String; let build: () -> [PathFrame] }

private struct PathConfig {
    let intro: String
    let build: () -> [PathFrame]
    var title = ""
    var note = ""
    /// Tabs over the grid (Dijkstra / A*), and which one the topic opens on.
    var variants: [PathVariant] = []
    var initialTab = 0
    /// Whether G is a goal to ring; the flood-fill patterns use the markers for other things.
    var goal = true
    var pathLabel = "Path"
    var wallLabel: String? = nil
}

private let visitedCell = SimColors.blue
private let currentCell = SimColors.active
private let pathCell = SimColors.green
private let goalRing = SimColors.answer

private func key(_ r: Int, _ c: Int) -> Int { r * cols + c }
private func rowOf(_ k: Int) -> Int { k / cols }
private func colOf(_ k: Int) -> Int { k % cols }
private func rc(_ k: Int) -> String { "(\(rowOf(k)), \(colOf(k)))" }

private let start = key(2, 0)
private let goal = key(2, 7)
private let baseWalls: Set<Int> = [key(0, 4), key(1, 4), key(2, 4), key(4, 4), key(5, 4)]
private let wallGap = key(3, 4)

private func neighbours(_ k: Int, _ walls: Set<Int>) -> [Int] {
    let r = rowOf(k), c = colOf(k)
    return [(r - 1, c), (r + 1, c), (r, c - 1), (r, c + 1)]
        .filter { $0.0 >= 0 && $0.0 < rows && $0.1 >= 0 && $0.1 < cols }
        .map { key($0.0, $0.1) }
        .filter { !walls.contains($0) }
}

private func manhattan(_ a: Int, _ b: Int) -> Int { abs(rowOf(a) - rowOf(b)) + abs(colOf(a) - colOf(b)) }

/// Insertion-ordered set, so ties in the priority pop the way Kotlin's LinkedHashSet does.
private struct OrderedSet {
    private(set) var items: [Int] = []
    var isEmpty: Bool { items.isEmpty }
    var set: Set<Int> { Set(items) }
    func contains(_ x: Int) -> Bool { items.contains(x) }
    mutating func insert(_ x: Int) { if !items.contains(x) { items.append(x) } }
    mutating func remove(_ x: Int) { items.removeAll { $0 == x } }
}

private func walkBack(_ cameFrom: [Int: Int]) -> Set<Int> {
    var path: Set<Int> = [goal]
    var node = goal
    while node != start { node = cameFrom[node]!; path.insert(node) }
    return path
}

/// Best-first search; zero heuristic = Dijkstra, Manhattan = A*. A frame per expansion, recorded after
/// its neighbours are relaxed.
private func searchFrames(walls: Set<Int>, heuristic: (Int) -> Int, label: String, into frames: inout [PathFrame], startNote: String?) {
    let informed = heuristic(start) > 0
    var dist: [Int: Int] = [start: 0]
    var cameFrom: [Int: Int] = [:]
    var visited = OrderedSet()
    var open = OrderedSet()
    open.insert(start)
    var gapSeen = false

    func labels() -> [Int: Int] { dist.filter { visited.contains($0.key) || open.contains($0.key) } }
    func chips(_ current: Int?) -> [StoryChip] {
        let done = visited.items.count - (current == nil ? 0 : 1)
        if informed, let current {
            let g = dist[current]!
            return [StoryChip("g", "\(g)"), StoryChip("f", "\(g + heuristic(current))"), StoryChip("visited", "\(done)")]
        }
        return [StoryChip("dist", "\(current.map { dist[$0]! } ?? 0)"), StoryChip("visited", "\(done)"), StoryChip("frontier", "\(open.items.count)")]
    }

    if let note = startNote {
        frames.append(PathFrame(visited: [], frontier: [start], current: nil, path: [], walls: walls, status: note,
                                dist: labels(), headline: "Start at {S} with distance 0.", body: note, chips: chips(nil)))
    }
    while !open.isEmpty {
        let current = open.items.min { dist[$0]! + heuristic($0) < dist[$1]! + heuristic($1) }!
        open.remove(current)
        visited.insert(current)
        let d = dist[current]!
        if current == goal {
            let path = walkBack(cameFrom)
            frames.append(PathFrame(visited: visited.set, frontier: open.set, current: nil, path: path, walls: walls,
                                    status: "Goal reached. \(label) expanded \(visited.items.count) cells; the path is \(path.count - 1) steps.",
                                    dist: labels(), headline: "Reach {m:G} at distance \(d).",
                                    body: "\(label) expanded \(visited.items.count) cells to get here. The green path is a shortest route back to S, \(path.count - 1) steps long.",
                                    chips: [StoryChip("dist", "\(d)", .done), StoryChip("visited", "\(visited.items.count)")]))
            return
        }
        var added: [Int] = []
        for next in neighbours(current, walls) {
            let candidate = d + 1
            if candidate < (dist[next] ?? .max) {
                dist[next] = candidate
                cameFrom[next] = current
                if !visited.contains(next) && !open.contains(next) { added.append(next) }
                if !visited.contains(next) { open.insert(next) }
            }
        }
        let h = heuristic(current)
        let throughGap = added.contains(wallGap) && !walls.contains(wallGap) && !gapSeen
        if throughGap { gapSeen = true }
        let headline = (informed ? "Expand {\(rc(current))}: g = \(d), f = \(d + h)." : "Expand {\(rc(current))} at distance \(d).")
            + (throughGap ? " It borders the only gap in the wall." : "")
        let joins = added.count == 1 ? " joins" : "s join"
        let body: String
        if throughGap && informed {
            body = "The gap cell joins the frontier at \(d + 1). Its estimate says G is just past the wall, so A* heads straight through."
        } else if throughGap {
            body = "The gap cell joins the frontier at \(d + 1). \(label) doesn't know where G is, so it keeps spreading in every direction."
        } else if added.isEmpty {
            body = "Nothing new joins the frontier: every neighbour is a wall or already reached."
        } else if informed {
            body = "\(added.count) new cell\(joins) the frontier. The lowest f goes next, so cells toward G come first."
        } else {
            body = "\(added.count) new cell\(joins) the frontier at \(d + 1). Every cell at distance \(d) is finished before any at \(d + 1)."
        }
        frames.append(PathFrame(visited: visited.set.subtracting([current]), frontier: open.set, current: current, path: [], walls: walls,
                                status: "Expand \(rc(current)) — cost so far \(d)" + (h > 0 ? ", estimate to goal \(h)" : ""),
                                dist: labels(), headline: headline, body: body, chips: chips(current)))
    }
    frames.append(PathFrame(visited: visited.set, frontier: [], current: nil, path: [], walls: walls, status: "No path exists."))
}

private func dijkstraFrames() -> [PathFrame] {
    var f: [PathFrame] = []
    searchFrames(walls: baseWalls, heuristic: { _ in 0 }, label: "Dijkstra", into: &f,
                 startNote: "Dijkstra always expands the closest cell it hasn't finished. It has no idea where G is.")
    return f
}

private func aStarFrames() -> [PathFrame] {
    var f: [PathFrame] = []
    searchFrames(walls: baseWalls, heuristic: { manhattan($0, goal) }, label: "A*", into: &f,
                 startNote: "A* expands the cell with the lowest f = g + h, where h counts the grid steps left to G. h never overshoots, so the path is still the shortest.")
    return f
}

private func dStarFrames() -> [PathFrame] {
    var f: [PathFrame] = []
    searchFrames(walls: baseWalls, heuristic: { manhattan($0, goal) }, label: "The initial plan", into: &f, startNote: "D* starts from a full plan, exactly like A*.")
    let original = f.last!.path
    let blocked = original.filter { colOf($0) == 5 }.min { rowOf($0) < rowOf($1) } ?? key(2, 5)
    let newWalls = baseWalls.union([blocked])
    f.append(PathFrame(visited: [], frontier: [], current: blocked, path: original, walls: newWalls,
                       status: "The robot starts moving, then discovers a new obstacle at \(rc(blocked)) — right on the committed path."))
    f.append(PathFrame(visited: [], frontier: [], current: nil, path: [], walls: newWalls,
                       status: "A full replan would throw away everything. D* keeps the costs that are still valid and repairs only the region the obstacle invalidated."))
    searchFrames(walls: newWalls, heuristic: { manhattan($0, goal) }, label: "The repair", into: &f, startNote: "Repairing from the affected cell outward…")
    return f
}

private let toll = 9
private func stepCost(_ from: Int, _ to: Int) -> Int {
    (from == key(2, 6) && to == goal) || (from == key(3, 7) && to == goal) ? toll : 1
}

private func ucsFrames() -> [PathFrame] {
    let walls = baseWalls
    var frames: [PathFrame] = []
    var dist: [Int: Int] = [start: 0]
    var cameFrom: [Int: Int] = [:]
    var visited = OrderedSet()
    var open = OrderedSet()
    open.insert(start)
    var goalFirstSeenAt: Int?
    frames.append(PathFrame(visited: [], frontier: [start], current: nil, path: [], walls: walls,
                            status: "Uniform cost search: f = g, nothing else. The two cells that touch the goal from the left and below charge a toll of \(toll); every other move costs 1."))
    while !open.isEmpty {
        let current = open.items.min { dist[$0]! < dist[$1]! }!
        open.remove(current)
        // Goal test on POP, not on generation — the line the whole topic turns on.
        if current == goal {
            frames.append(PathFrame(visited: visited.set, frontier: open.set, current: current, path: walkBack(cameFrom), walls: walls,
                                    status: "Goal popped at cost \(dist[goal]!) after \(visited.items.count) expansions. It was first generated at cost \(goalFirstSeenAt ?? dist[goal]!) — testing at generation time would have returned that path and called it done.",
                                    dist: dist.filter { visited.contains($0.key) || open.contains($0.key) || $0.key == goal }))
            return frames
        }
        visited.insert(current)
        var generated: [String] = []
        for next in neighbours(current, walls) {
            let candidate = dist[current]! + stepCost(current, next)
            if candidate < (dist[next] ?? .max) {
                let improving = dist[next] != nil
                dist[next] = candidate
                cameFrom[next] = current
                if !visited.contains(next) { open.insert(next) }
                if next == goal && goalFirstSeenAt == nil { goalFirstSeenAt = candidate }
                generated.append(improving ? "lowered \(rc(next)) to \(candidate)" : "\(rc(next)) at \(candidate)")
            }
        }
        frames.append(PathFrame(visited: visited.set, frontier: open.set, current: current, path: [], walls: walls,
                                status: "Pop \(rc(current)) at cost \(dist[current]!) — that cost is now final. " + (generated.isEmpty ? "Its successors are all settled or already cheaper." : "Successors: \(generated.joined(separator: "; "))."),
                                dist: dist.filter { visited.contains($0.key) || open.contains($0.key) }))
    }
    frames.append(PathFrame(visited: visited.set, frontier: [], current: nil, path: [], walls: walls, status: "No path exists."))
    return frames
}

private func idaStarFrames() -> [PathFrame] {
    let walls = baseWalls
    var frames: [PathFrame] = []
    let h = { (k: Int) in manhattan(k, goal) }
    var threshold = h(start)
    var iteration = 0, totalExpansions = 0
    frames.append(PathFrame(visited: [], frontier: [], current: start, path: [], walls: walls,
                            status: "IDA* keeps no frontier at all — only the current path on the recursion stack. The first threshold is h(start) = \(threshold), the cheapest the answer could possibly be."))
    while iteration < 12 {
        iteration += 1
        var touched = OrderedSet()
        var path = [start]
        var nextThreshold = Int.max
        var expansions = 0
        frames.append(PathFrame(visited: [], frontier: [], current: start, path: [start], walls: walls,
                                status: "Iteration \(iteration): depth-first search, abandoning any cell with f = g + h greater than \(threshold)."))
        func search(_ g: Int) -> Bool {
            let node = path.last!
            touched.insert(node)
            expansions += 1
            totalExpansions += 1
            if node == goal {
                frames.append(PathFrame(visited: touched.set, frontier: [], current: node, path: Set(path), walls: walls,
                                        status: "Goal reached at g = \(g) under threshold \(threshold). Manhattan distance never overestimates, so this first goal is already optimal — \(totalExpansions) expansions in total across \(iteration) iteration(s)."))
                return true
            }
            var pruned: [Int] = [], open: [Int] = []
            for next in neighbours(node, walls) where !path.contains(next) {
                let f = g + 1 + h(next)
                if f > threshold { pruned.append(f); nextThreshold = min(nextThreshold, f) } else { open.append(next) }
            }
            frames.append(PathFrame(visited: touched.set, frontier: Set(open), current: node, path: Set(path), walls: walls,
                                    status: "At \(rc(node)): g = \(g), h = \(h(node)), f = \(g + h(node)). "
                                    + (pruned.isEmpty ? "All \(open.count) successor(s) fit under the threshold."
                                       : "\(pruned.count) successor(s) pruned at f = \(Array(Set(pruned)).sorted().map(String.init).joined(separator: "/")); \(open.count) left to try.")))
            for next in open {
                path.append(next)
                if search(g + 1) { return true }
                path.removeLast()
            }
            if open.isEmpty {
                frames.append(PathFrame(visited: touched.set, frontier: [], current: node, path: Set(path), walls: walls,
                                        status: "Dead end at \(rc(node)) — every successor is either a wall, already on the path, or over budget. Pop the stack."))
            }
            return false
        }
        if search(0) { return frames }
        if nextThreshold == .max {
            frames.append(PathFrame(visited: touched.set, frontier: [], current: nil, path: [], walls: walls, status: "Nothing left to raise the threshold to — no path exists."))
            return frames
        }
        frames.append(PathFrame(visited: touched.set, frontier: [], current: nil, path: [], walls: walls,
                                status: "Iteration \(iteration) failed after \(expansions) expansions. The smallest f it had to prune was \(nextThreshold), so that becomes the next threshold — never a guess, and never a fixed increment. Everything so far is discarded and re-expanded."))
        threshold = nextThreshold
    }
    return frames
}

private let rotWalls: Set<Int> = [key(0, 3), key(1, 3), key(3, 5), key(4, 5), key(5, 5)]

private func multiSourceFrames() -> [PathFrame] {
    var frames: [PathFrame] = []
    var dist: [Int: Int] = [start: 0, goal: 0]
    var frontier: [Int] = [start, goal]
    var visited = Set(frontier)
    frames.append(PathFrame(visited: [], frontier: Set(frontier), current: nil, path: [], walls: rotWalls,
                            status: "Both sources are seeded into the queue at distance 0 *before* the loop starts. That initialisation is the entire pattern — the loop below is ordinary BFS and never learns there was more than one source.", dist: dist))
    var round = 0
    while !frontier.isEmpty {
        round += 1
        var next: [Int] = []
        for cell in frontier {
            for n in neighbours(cell, rotWalls) where visited.insert(n).inserted {
                dist[n] = round
                next.append(n)
            }
        }
        if next.isEmpty { break }
        frames.append(PathFrame(visited: visited.subtracting(next), frontier: Set(next), current: nil, path: [], walls: rotWalls,
                                status: "Minute \(round): the whole frontier advances one step together, claiming \(next.count) cell(s). A cell is marked the moment it is queued, so the wave that got there first keeps it — no comparison between sources is ever needed.", dist: dist))
        frontier = next
    }
    let farthest = dist.max { $0.value < $1.value }!
    let unreached = Set(0..<(rows * cols)).subtracting(rotWalls).subtracting(visited)
    frames.append(PathFrame(visited: visited, frontier: [], current: farthest.key, path: [farthest.key], walls: rotWalls,
                            status: "Everything reachable is claimed after \(farthest.value) minute(s) — the answer is the largest distance assigned, and \(unreached.isEmpty ? "no cell was left out" : "\(unreached.count) walled-off cell(s) were never reached, which is the case that returns −1"). Running a separate BFS per source and taking the minimum gives the same numbers for k times the work.", dist: dist))
    return frames
}

private let islandLand: Set<Int> = [
    key(0, 0), key(0, 1), key(1, 0), key(1, 1), key(0, 5), key(0, 6), key(1, 6), key(2, 3), key(3, 3), key(3, 4),
    key(4, 0), key(5, 0), key(5, 1), key(4, 6), key(4, 7), key(5, 7),
]

private func islandFrames() -> [PathFrame] {
    let water = Set(0..<(rows * cols)).subtracting(islandLand)
    var frames: [PathFrame] = []
    var finished = Set<Int>(), visited = Set<Int>()
    var islands = 0
    frames.append(PathFrame(visited: [], frontier: [], current: nil, path: [], walls: water,
                            status: "A grid is a graph: cells are nodes, the four neighbours are edges, and \"count the islands\" is \"count the connected components\". \(islandLand.count) land cells, no start and no goal."))
    for cell in 0..<(rows * cols) where islandLand.contains(cell) && !visited.contains(cell) {
        islands += 1
        var region = [cell]
        var queue = [cell]
        visited.insert(cell)
        frames.append(PathFrame(visited: visited.subtracting(region), frontier: Set(queue), current: cell, path: finished, walls: water,
                                status: "The scan hits unvisited land at \(rc(cell)). That is island #\(islands) — flood it, and the whole region is consumed before the scan resumes."))
        while !queue.isEmpty {
            let current = queue.removeFirst()
            var added: [Int] = []
            for next in neighbours(current, water) where !visited.contains(next) {
                visited.insert(next)
                queue.append(next)
                region.append(next)
                added.append(next)
            }
            frames.append(PathFrame(visited: visited.subtracting(queue), frontier: Set(queue), current: current, path: finished, walls: water,
                                    status: "Expand \(rc(current)): " + (added.isEmpty ? "every neighbour is water or already marked." : "\(added.count) new land neighbour(s) marked and queued. Marking on enqueue is what stops a cell entering the queue twice.")))
        }
        finished.formUnion(region)
        frames.append(PathFrame(visited: visited.subtracting(finished), frontier: [], current: nil, path: finished, walls: water,
                                status: "Island #\(islands) is complete at \(region.count) cell(s). Resume the outer scan from where it left off."))
    }
    frames.append(PathFrame(visited: [], frontier: [], current: nil, path: finished, walls: water,
                            status: "\(islands) islands. Every cell was examined a constant number of times, so the whole thing is O(rows × cols) — the count comes from how many floods were started, not from anything the floods measured."))
    return frames
}

/// Dijkstra and A* share one grid and one pair of tabs, so either topic can flip to the other and compare.
private let searchTabs = [PathVariant(label: "Dijkstra", build: dijkstraFrames), PathVariant(label: "A*", build: aStarFrames)]

private let pathConfigs: [String: PathConfig] = [
    "multi_source_bfs_pattern": PathConfig(intro: "Two rotten oranges spreading at once — the S and G markers are the seeds, not a start and a goal. Every cell is claimed by whichever wave reaches it first, so one sweep answers all of them.",
                                           build: multiSourceFrames, title: "TWO SOURCES", note: "minutes to reach", goal: false, pathLabel: "Last reached"),
    "matrix_islands_pattern": PathConfig(intro: "Counting islands by flood fill. Dark cells are water, and each fill consumes one whole region before the outer scan moves on — the number of fills started is the answer.",
                                         build: islandFrames, title: "ISLANDS", note: "flood fill", goal: false, pathLabel: "Island done", wallLabel: "Water"),
    "dijkstras_algorithm": PathConfig(intro: "Dijkstra on a walled grid, every step costing 1. With no sense of direction it expands in rings until the goal happens to fall inside one.",
                                      build: dijkstraFrames, variants: searchTabs, initialTab: 0),
    "a_star_search": PathConfig(intro: "A* on the same grid and the same walls. Adding a Manhattan-distance estimate to the priority pulls the search straight at the goal — compare the number of expanded cells with Dijkstra's.",
                                build: aStarFrames, variants: searchTabs, initialTab: 1),
    "d_star_algorithm": PathConfig(intro: "D* is A* for a map that changes underneath you: plan, start driving, discover an obstacle, then repair the affected part of the plan instead of starting over.",
                                   build: dStarFrames, title: "PLAN, THEN REPAIR", note: "f = g + h"),
    "uniform_cost_search": PathConfig(intro: "UCS is Dijkstra's relaxation with a goal test bolted on and the graph generated as it goes. The two approaches to the goal charge a toll, so the goal is generated cheaply-looking-expensive long before the real answer arrives — which is exactly why the test happens on pop.",
                                      build: ucsFrames, title: "UNIFORM COST", note: "toll \(toll) into G"),
    "ida_star": PathConfig(intro: "The same map and the same Manhattan heuristic as A*, but no frontier: a depth-first search bounded by f = g + h, restarted at the smallest f it had to prune. Watch the threshold rise and the search start over.",
                           build: idaStarFrames, title: "ITERATIVE DEEPENING", note: "f ≤ threshold"),
]

struct PathfindingLab: View {
    private let config: PathConfig
    @State private var tab: Int
    @State private var frames: [PathFrame]
    @State private var playback: PlaybackState

    init(topicId: String) {
        let config = pathConfigs[topicId] ?? pathConfigs["a_star_search"]!
        self.config = config
        let frames = (config.initialTab < config.variants.count ? config.variants[config.initialTab].build : config.build)()
        _tab = State(initialValue: config.initialTab)
        _frames = State(initialValue: frames)
        _playback = State(initialValue: PlaybackState(stepCount: frames.count, speedMs: 400))
    }

    var body: some View {
        let frame = frames[min(playback.index, frames.count - 1)]
        VStack(alignment: .leading, spacing: 0) {
            LabIntro(text: config.intro, bottom: 12)
            LabCard {
                if config.variants.isEmpty {
                    StoryHeader(title: config.title, note: config.note)
                } else {
                    LabSegments(labels: config.variants.map(\.label), selected: Binding(get: { tab }, set: select))
                }
                PathGrid(frame: frame, config: config)
                    .padding(.top, 14)
                    .animation(.easeInOut(duration: 0.15), value: playback.index)
                StoryLegendRow(items: legend(frame)).padding(.top, 14)
            }
            if !frame.chips.isEmpty { StoryChips(chips: frame.chips).padding(.top, 16) }
            Group {
                if let headline = frame.headline {
                    LabStoryNarration(headline: headline, body: frame.body)
                } else {
                    LabNarration(text: frame.status)
                }
            }
            .padding(.top, 16)
            PlaybackTransport(state: playback, captions: frames.map(\.caption))
        }
    }

    private func select(_ i: Int) {
        guard i != tab else { return }
        tab = i
        frames = config.variants[i].build()
        playback.load(stepCount: frames.count)
    }

    private func legend(_ frame: PathFrame) -> [(color: Color, style: SwatchStyle, label: String)] {
        var out: [(color: Color, style: SwatchStyle, label: String)] = []
        if frame.current != nil { out.append((currentCell, .fill, "Expanding")) }
        if !frame.visited.isEmpty { out.append((visitedCell, .fill, "Visited")) }
        if !frame.frontier.isEmpty { out.append((visitedCell, .dashed, "Frontier")) }
        if !frame.path.isEmpty { out.append((pathCell, .fill, config.pathLabel)) }
        if let wall = config.wallLabel { out.append((SimColors.grey.opacity(0.55), .fill, wall)) }
        if config.goal && !frame.path.contains(goal) { out.append((goalRing, .ring, "Goal")) }
        return out
    }
}

private struct PathGrid: View {
    let frame: PathFrame
    let config: PathConfig
    @Environment(\.palette) private var palette

    var body: some View {
        Grid(horizontalSpacing: 6, verticalSpacing: 6) {
            ForEach(0..<rows, id: \.self) { r in
                GridRow {
                    ForEach(0..<cols, id: \.self) { c in
                        cell(key(r, c))
                    }
                }
            }
        }
    }

    @ViewBuilder
    private func cell(_ k: Int) -> some View {
        let shape = RoundedRectangle(cornerRadius: 8)
        let wall = frame.walls.contains(k)
        let onPath = frame.path.contains(k)
        let frontier = frame.frontier.contains(k) && k != frame.current
        let fill: Color = wall ? palette.muted.opacity(0.45) : onPath ? pathCell : k == frame.current ? currentCell
            : frame.visited.contains(k) ? visitedCell : frontier ? visitedCell.opacity(0.08) : SimColors.tint
        let ringed = k == goal && config.goal && !wall && !onPath && k != frame.current && !frame.visited.contains(k) && !frontier
        let ink: Color = k == frame.current && !onPath ? Color(hex: 0x1F1A0A) : onPath || frame.visited.contains(k) ? .white
            : frontier ? StoryTone.path.ink(palette) : ringed ? goalRing : palette.onSurface
        let text: String? = wall ? nil : k == start ? "S" : k == goal ? "G" : frame.dist[k].map(String.init)
        shape.fill(ringed ? .clear : fill)
            .aspectRatio(1, contentMode: .fit)
            .overlay {
                if ringed { shape.strokeBorder(goalRing, lineWidth: 2) }
                else if frontier && !wall { shape.strokeBorder(visitedCell, style: StrokeStyle(lineWidth: 1.5, dash: [3, 2.5])) }
            }
            .overlay {
                if let text { Text(text).font(AppFont.mono(14, .bold)).foregroundStyle(ink).lineLimit(1).minimumScaleFactor(0.6) }
            }
    }
}
