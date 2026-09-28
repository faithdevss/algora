import SwiftUI

// Port of GraphSimulationSection.kt, laid out as screen 05 of docs/ios-design/Simulations iOS.html:
// an editable graph with BFS/DFS playback. One step per node taken off the frontier, so the caption
// can say what the step did ("Dequeue C. Its neighbours E and F are new…"). The frontier itself —
// the queue or the stack — is drawn as data under the stage, since it is the thing BFS/DFS teach.
//
// Explore mode: tap a node to make it the start. Edit mode (nav bar): tap empty space to add a node,
// drag from one node to another to link them, long-press a node to delete it.

private let demoNodes = ["A", "B", "C", "D", "E", "F"]
private let demoEdges: [(String, String)] = [("A", "B"), ("A", "C"), ("A", "D"), ("C", "E"), ("C", "F"), ("D", "F")]
/// Positions in a unit square, matching the design's layout.
private let demoPositions: [String: CGPoint] = [
    "A": CGPoint(x: 0.5, y: 0.12), "B": CGPoint(x: 0.2, y: 0.5), "C": CGPoint(x: 0.5, y: 0.5),
    "D": CGPoint(x: 0.8, y: 0.5), "E": CGPoint(x: 0.32, y: 0.88), "F": CGPoint(x: 0.68, y: 0.88),
]
private let maxNodes = 10
private let nodeRadius: CGFloat = 22

private enum NodeState { case idle, queued, current, visited }
private enum Traversal: String, CaseIterable { case BFS, DFS }

private struct EdgeKey: Hashable {
    let a: String
    let b: String
    init(_ x: String, _ y: String) { if x <= y { a = x; b = y } else { a = y; b = x } }
}

private struct GraphStep {
    let state: [String: NodeState]
    /// Edges that discovered a node, drawn solid green.
    let tree: Set<EdgeKey>
    /// Edges this step discovered along, drawn dashed blue.
    let fresh: Set<EdgeKey>
    let frontier: [String]
    let visited: [String]
    let status: String
}

private func listed(_ xs: [String]) -> String {
    switch xs.count {
    case 0: ""
    case 1: xs[0]
    case 2: "\(xs[0]) and \(xs[1])"
    default: xs.dropLast().joined(separator: ", ") + " and " + xs.last!
    }
}

private func adjacency(_ nodes: [String], _ edges: [(String, String)]) -> [String: [String]] {
    var adj = Dictionary(uniqueKeysWithValues: nodes.map { ($0, [String]()) })
    for (a, b) in edges { adj[a]?.append(b); adj[b]?.append(a) }
    return adj.mapValues { $0.sorted() }
}

private func traverse(_ t: Traversal, _ nodes: [String], _ edges: [(String, String)], _ start: String) -> [GraphStep] {
    guard nodes.contains(start) else { return [] }
    let adj = adjacency(nodes, edges)
    var state = Dictionary(uniqueKeysWithValues: nodes.map { ($0, NodeState.idle) })
    var tree = Set<EdgeKey>()
    var frontier = [start]
    var visited: [String] = []
    var seen: Set<String> = [start]
    var steps: [GraphStep] = []
    let bfs = t == .BFS
    let noun = bfs ? "queue" : "stack"
    state[start] = .queued
    func snap(_ status: String, fresh: Set<EdgeKey> = []) {
        steps.append(GraphStep(state: state, tree: tree, fresh: fresh, frontier: frontier, visited: visited, status: status))
    }
    snap("Start at \(start). It goes into the \(noun). " + (bfs ? "BFS explores in rings: every node one edge away, then two." : "DFS follows one path as deep as it goes, then backs up."))

    while !frontier.isEmpty {
        let node = bfs ? frontier.removeFirst() : frontier.removeLast()
        state[node] = .current
        let new = (adj[node] ?? []).filter { !seen.contains($0) }
        let pushed = bfs ? new : Array(new.reversed())
        var fresh = Set<EdgeKey>()
        for nb in pushed {
            seen.insert(nb)
            state[nb] = .queued
            frontier.append(nb)
            fresh.insert(EdgeKey(node, nb))
        }
        let verb = bfs ? "Dequeue" : "Pop"
        let status: String
        if new.isEmpty {
            status = "\(verb) \(node). Its neighbours are all seen already, so nothing joins the \(noun). "
                + (bfs ? "The next node in line is still from the same level or the one after." : "DFS backs up to the last branch it left open.")
        } else if bfs {
            status = "Dequeue \(node). \(new.count == 1 ? "Its neighbour \(new[0]) is" : "Its neighbours \(listed(new)) are") new, so \(new.count == 1 ? "it joins" : "they join") the back of the queue. "
                + "BFS finishes each level before starting the next."
        } else {
            status = "Pop \(node). Push \(listed(new)) onto the stack; \(pushed.last!) is on top, so it is explored next. "
                + "DFS goes as deep as it can before backing up."
        }
        snap(status, fresh: fresh)
        for e in fresh { tree.insert(e) }
        state[node] = .visited
        visited.append(node)
    }
    let unreached = nodes.filter { !seen.contains($0) }
    snap("The \(noun) is empty. \(t.rawValue) reached \(visited.count) of \(nodes.count) nodes. "
        + (unreached.isEmpty ? "Every node was connected to \(start)." : "\(listed(unreached)) \(unreached.count == 1 ? "has" : "have") no path from \(start)."))
    return steps
}

@MainActor
@Observable
private final class GraphLabModel {
    let traversals: [Traversal]
    var nodes = demoNodes
    var edges = demoEdges
    var positions = demoPositions
    var start = "A" { didSet { rebuild() } }
    var traversal: Traversal { didSet { rebuild() } }
    var editing = false { didSet { rebuild() } }
    var steps: [GraphStep] = []
    let playback = PlaybackState(stepCount: 1, speedMs: 900)
    /// Edit mode: the drag in progress from a node, for the rubber-band line.
    var linkFrom: String?
    var linkTo: CGPoint?
    var notice: String?

    init(topicId: String) {
        traversals = topicId == "bfs" ? [.BFS] : topicId == "dfs" ? [.DFS] : Traversal.allCases
        traversal = traversals[0]
        rebuild()
    }

    var current: GraphStep? { editing || steps.isEmpty ? nil : steps[min(playback.index, steps.count - 1)] }

    func rebuild() {
        steps = traverse(traversal, nodes, edges, start)
        playback.load(stepCount: max(steps.count, 1))
    }

    func reset() {
        nodes = demoNodes
        edges = demoEdges
        positions = demoPositions
        notice = nil
        start = "A"
    }

    // MARK: Editing

    func node(at p: CGPoint, in size: CGSize) -> String? {
        nodes.first { id in
            guard let u = positions[id] else { return false }
            return hypot(u.x * size.width - p.x, u.y * size.height - p.y) <= nodeRadius + 8
        }
    }

    func addNode(at p: CGPoint, in size: CGSize) {
        guard nodes.count < maxNodes else {
            notice = "No room for another node. This lab holds \(maxNodes). Long-press one to delete it first."
            return
        }
        guard let id = (65..<(65 + 26)).map({ String(UnicodeScalar(UInt8($0))) }).first(where: { !nodes.contains($0) }) else { return }
        let margin = nodeRadius + 4
        let x = min(max(p.x, margin), size.width - margin) / size.width
        let y = min(max(p.y, margin), size.height - margin) / size.height
        nodes.append(id)
        positions[id] = CGPoint(x: x, y: y)
        notice = nil
        rebuild()
    }

    func link(_ a: String, _ b: String) {
        guard a != b else { return }
        if edges.contains(where: { EdgeKey($0.0, $0.1) == EdgeKey(a, b) }) {
            notice = "\(a) and \(b) are already linked."
            return
        }
        edges.append((a, b))
        notice = nil
        rebuild()
    }

    func delete(_ id: String) {
        guard nodes.count > 1 else {
            notice = "A graph needs at least one node, so the last one stays."
            return
        }
        nodes.removeAll { $0 == id }
        edges.removeAll { $0.0 == id || $0.1 == id }
        positions[id] = nil
        notice = nil
        if start == id { start = nodes[0] } else { rebuild() }
    }
}

struct GraphBuilderLab: View {
    @State private var model: GraphLabModel
    @Environment(\.labDock) private var dock

    init(topicId: String) { _model = State(initialValue: GraphLabModel(topicId: topicId)) }

    var body: some View {
        Group {
            if let dock {
                VStack(alignment: .leading, spacing: 0) {
                    LabCard(padding: 12) { GraphStage(model: model) }
                    GraphNarration(model: model).padding(.horizontal, 4).padding(.top, 14)
                }
                .onAppear {
                    let model = model
                    dock.controls = { AnyView(GraphControls(model: model)) }
                    installNav(dock)
                }
                .onChange(of: model.editing) { _, _ in installNav(dock) }
                .onDisappear { dock.controls = nil; dock.navAction = nil }
            } else {
                LabCard(padding: 12) {
                    HStack {
                        Spacer()
                        Button(model.editing ? "Done" : "Edit") { model.editing.toggle() }
                            .font(AppFont.sans(15, .semibold))
                    }
                    GraphStage(model: model).padding(.top, 4)
                    GraphNarration(model: model).padding(.top, 14).padding(.horizontal, 4)
                    Divider().padding(.top, 16)
                    GraphControls(model: model).padding(.top, 14)
                }
            }
        }
    }

    private func installNav(_ dock: LabDock) {
        let model = model
        dock.navAction = LabNavAction(icon: "", label: model.editing ? "Done" : "Edit", text: true) { model.editing.toggle() }
    }
}

// MARK: - Stage

private struct GraphStage: View {
    let model: GraphLabModel
    @State private var pressStart: Date?
    @State private var pressNode: String?
    @State private var moved = false
    @Environment(\.palette) private var palette

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            GeometryReader { geo in
                let size = geo.size
                Canvas { ctx, _ in draw(ctx, size) }
                    .contentShape(Rectangle())
                    .gesture(gesture(size))
            }
            .frame(height: 250)
            .background(model.editing ? palette.primary.opacity(0.06) : .clear, in: RoundedRectangle(cornerRadius: 14))
            .overlay {
                if model.editing {
                    RoundedRectangle(cornerRadius: 14).stroke(palette.primary.opacity(0.4), style: StrokeStyle(lineWidth: 1.5, dash: [6, 5]))
                }
            }
            HStack(spacing: 14) {
                dot(SimColors.active, model.traversal == .BFS ? "Dequeued" : "Popped")
                dot(SimColors.blue, model.traversal == .BFS ? "In queue" : "On stack")
                dot(SimColors.green, "Visited")
                Spacer(minLength: 0)
            }
            .padding(.horizontal, 4)
            .padding(.bottom, 4)
        }
    }

    private func dot(_ color: Color, _ label: String) -> some View {
        HStack(spacing: 6) {
            Circle().fill(color).frame(width: 10, height: 10)
            Text(label).font(AppFont.sans(13)).foregroundStyle(palette.onSurface.opacity(0.75))
        }
    }

    private func gesture(_ size: CGSize) -> some Gesture {
        DragGesture(minimumDistance: 0)
            .onChanged { g in
                if pressStart == nil {
                    pressStart = Date()
                    pressNode = model.node(at: g.startLocation, in: size)
                    moved = false
                }
                if hypot(g.translation.width, g.translation.height) > 8 { moved = true }
                if model.editing, let from = pressNode, moved {
                    model.linkFrom = from
                    model.linkTo = g.location
                }
            }
            .onEnded { g in
                defer { pressStart = nil; pressNode = nil; model.linkFrom = nil; model.linkTo = nil }
                let held = Date().timeIntervalSince(pressStart ?? Date())
                if model.editing {
                    if let from = pressNode, moved {
                        if let to = model.node(at: g.location, in: size) { model.link(from, to) }
                    } else if let id = pressNode, held >= 0.45 {
                        model.delete(id)
                    } else if pressNode == nil, !moved {
                        model.addNode(at: g.location, in: size)
                    }
                } else if let id = pressNode, !moved {
                    model.start = id
                }
            }
    }

    private func point(_ id: String, _ size: CGSize) -> CGPoint? {
        model.positions[id].map { CGPoint(x: $0.x * size.width, y: $0.y * size.height) }
    }

    private func draw(_ ctx: GraphicsContext, _ size: CGSize) {
        let step = model.current
        let idleEdge = palette.dark ? Color(hex: 0x4A4F5C) : Color(hex: 0xC9CED8)
        for (a, b) in model.edges {
            guard let pa = point(a, size), let pb = point(b, size) else { continue }
            let key = EdgeKey(a, b)
            var path = Path()
            path.move(to: pa)
            path.addLine(to: pb)
            if step?.fresh.contains(key) == true {
                ctx.stroke(path, with: .color(SimColors.blue), style: StrokeStyle(lineWidth: 3, lineCap: .round, dash: [6, 5]))
            } else if step?.tree.contains(key) == true {
                ctx.stroke(path, with: .color(SimColors.green), lineWidth: 3)
            } else {
                ctx.stroke(path, with: .color(idleEdge), lineWidth: 2)
            }
        }
        if let from = model.linkFrom, let pa = point(from, size), let to = model.linkTo {
            var path = Path()
            path.move(to: pa)
            path.addLine(to: to)
            ctx.stroke(path, with: .color(palette.primary), style: StrokeStyle(lineWidth: 2.5, lineCap: .round, dash: [5, 4]))
        }
        for id in model.nodes {
            guard let c = point(id, size) else { continue }
            let state = step?.state[id] ?? .idle
            let (fill, text): (Color, Color) = switch state {
            case .idle: (palette.dark ? Color(hex: 0x3A3F4C) : Color(hex: 0xD5D9E1), palette.onSurface)
            case .queued: (SimColors.blue, .white)
            case .current: (SimColors.active, Color(hex: 0x1A1A1A))
            case .visited: (SimColors.green, .white)
            }
            let r = state == .current ? nodeRadius + 2 : nodeRadius
            if state == .current {
                let halo = Path(ellipseIn: CGRect(x: c.x - r - 6, y: c.y - r - 6, width: (r + 6) * 2, height: (r + 6) * 2))
                ctx.stroke(halo, with: .color(SimColors.active.opacity(0.35)), lineWidth: 3)
            }
            if model.linkFrom == id {
                let ring = Path(ellipseIn: CGRect(x: c.x - r - 5, y: c.y - r - 5, width: (r + 5) * 2, height: (r + 5) * 2))
                ctx.stroke(ring, with: .color(palette.primary), lineWidth: 2)
            }
            ctx.fill(Path(ellipseIn: CGRect(x: c.x - r, y: c.y - r, width: r * 2, height: r * 2)), with: .color(fill))
            ctx.draw(Text(id).font(AppFont.sans(16, .bold)).foregroundColor(text), at: c)
            if id == model.start && !model.editing {
                ctx.draw(Text("start").font(AppFont.mono(12, .semibold)).foregroundColor(palette.muted),
                         at: CGPoint(x: c.x + r + 8, y: c.y - r + 4), anchor: .leading)
            }
        }
    }
}

// MARK: - Narration

private struct GraphNarration: View {
    let model: GraphLabModel
    @Environment(\.palette) private var palette

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            if model.editing {
                LabCaption(text: "Editing the graph. Tap empty space to add a node, drag from one node to another to link them, and long-press a node to delete it.")
                    .frame(minHeight: 72, alignment: .top)
            } else if let step = model.current {
                frontierRow(step)
                LabCaption(text: step.status).frame(minHeight: 72, alignment: .top)
            }
        }
    }

    private func frontierRow(_ step: GraphStep) -> some View {
        HStack(spacing: 8) {
            Text(model.traversal == .BFS ? "Queue" : "Stack").font(AppFont.sans(13)).foregroundStyle(palette.muted).frame(width: 48, alignment: .leading)
            if step.frontier.isEmpty {
                Text("empty").font(AppFont.mono(14)).foregroundStyle(palette.muted)
            }
            ForEach(Array(step.frontier.enumerated()), id: \.offset) { _, id in
                Text(id)
                    .font(AppFont.sans(15, .bold))
                    .frame(width: 32, height: 32)
                    .background(SimColors.blue.opacity(0.25), in: RoundedRectangle(cornerRadius: 9))
                    .overlay(RoundedRectangle(cornerRadius: 9).stroke(SimColors.blue, lineWidth: 1.5))
            }
            Spacer(minLength: 8)
            (Text("visited ").foregroundColor(palette.muted) + Text(step.visited.isEmpty ? "–" : step.visited.joined(separator: " ")).foregroundColor(palette.onSurface).fontWeight(.semibold))
                .font(AppFont.mono(14))
                .lineLimit(1)
                .minimumScaleFactor(0.7)
        }
    }
}

// MARK: - Controls

private struct GraphControls: View {
    @Bindable var model: GraphLabModel
    @Environment(\.palette) private var palette

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            if let notice = model.notice { LabNotice(text: notice) }
            if model.editing {
                LabNotice(text: "\(model.nodes.count) nodes, \(model.edges.count) edges. Tap Done in the top bar to run \(model.traversal.rawValue) on it.", kind: .info)
            } else {
                HStack(spacing: 10) {
                    if model.traversals.count > 1 {
                        ChipPicker(options: model.traversals.map { ($0, $0.rawValue) }, selection: $model.traversal)
                            .frame(width: 130)
                    } else {
                        Text(model.traversal.rawValue).font(AppFont.sans(14, .semibold))
                            .padding(.horizontal, 12).frame(height: 32)
                            .background(SimColors.tint, in: RoundedRectangle(cornerRadius: 9))
                    }
                    Menu {
                        ForEach(model.nodes, id: \.self) { id in
                            Button { model.start = id } label: {
                                if id == model.start { Label(id, systemImage: "checkmark") } else { Text(id) }
                            }
                        }
                    } label: {
                        HStack(spacing: 6) {
                            Text("Start").foregroundStyle(palette.muted)
                            Text(model.start).fontWeight(.semibold).foregroundStyle(palette.onSurface)
                        }
                        .font(AppFont.sans(14))
                        .padding(.horizontal, 12)
                        .frame(height: 32)
                        .background(SimColors.tint, in: Capsule())
                    }
                    Spacer(minLength: 0)
                }
                LabTransportBar(state: model.playback, captions: model.steps.map(\.status))
            }
        }
    }
}
