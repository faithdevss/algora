import SwiftUI

// Ports of LinkedListSimulationSection.kt, StackSimulationSection.kt, QueueSimulationSection.kt and
// DequeSimulationSection.kt, laid out as the sandboxes in docs/ios-design: pick an operation, read it
// as a sentence ("Push [40]", "Insert [40] at [index 1 ⌄]"), see what it touches on the stage before
// running it. The four share one model — they differ only in which ends are open and how the items
// are drawn. On the sim-only screen the controls are pinned to the bottom and reset sits in the nav bar.

private let initialItems = [10, 20, 30]

enum LinearKind { case stack, queue, deque, list }

private enum LinearOp: String {
    case push = "Push", pop = "Pop", peek = "Peek"
    case enqueue = "Enqueue", dequeue = "Dequeue"
    case insert = "Insert", delete = "Delete", search = "Search"

    var adds: Bool { self == .push || self == .enqueue || self == .insert }
    var removes: Bool { self == .pop || self == .dequeue || self == .delete }
}

private enum End: String { case front, back }
private enum ListPos: Hashable { case head, index, tail }
private enum LinearMark { case cursor, write, gone }

@MainActor
@Observable
private final class LinearSandbox {
    let kind: LinearKind
    var items = initialItems
    var op: LinearOp { didSet { clearResult() } }
    var end = End.back { didSet { clearResult() } }
    var pos = ListPos.index { didSet { clearResult() } }
    var selected = 1 { didSet { clearResult() } }
    var valueInput = "40" { didSet { clearResult() } }
    var busy = false
    var showingResult = false
    var status = ""
    var cost = ""
    var markIndex: Int?
    var markKind = LinearMark.cursor
    private var job: Task<Void, Never>?
    private let stepMs = 320

    init(kind: LinearKind) {
        self.kind = kind
        op = Self.ops(kind)[0]
        if kind == .deque { valueInput = "50" }
    }

    var ops: [LinearOp] { Self.ops(kind) }

    private static func ops(_ kind: LinearKind) -> [LinearOp] {
        switch kind {
        case .stack, .deque: [.push, .pop, .peek]
        case .queue: [.enqueue, .dequeue, .peek]
        case .list: [.insert, .delete, .search]
        }
    }

    var maxItems: Int { kind == .stack ? 7 : 8 }
    /// Why Run can't do this op right now, in words; nil when it can.
    var blockReason: String? {
        let remover = kind == .stack ? "Pop" : kind == .queue ? "Dequeue" : kind == .deque ? "Pop" : "Delete"
        let adder = kind == .stack ? "Push" : kind == .queue ? "Enqueue" : kind == .deque ? "Push" : "Insert"
        if op.adds && full {
            return "No free slot. This \(noun) holds \(maxItems) items in the lab, and all \(maxItems) are used. \(remover) something first."
        }
        if !op.adds && n == 0 { return "The \(noun) is empty, so there is nothing to \(op.rawValue.lowercased()). \(adder) something first." }
        if (op.adds || op == .search) && Int(valueInput) == nil { return "Type a number to \(op.rawValue.lowercased()) first." }
        return nil
    }

    var canRun: Bool { !busy && blockReason == nil }
    var previewing: Bool { !busy && !showingResult }
    var value: String { valueInput.isEmpty ? "?" : valueInput }
    var n: Int { items.count }
    var full: Bool { n >= maxItems }

    /// The index a list op targets (insert: the slot the new node takes).
    var target: Int {
        switch op {
        case .insert: pos == .head ? 0 : pos == .tail ? n : min(selected, n)
        default: min(selected, max(n - 1, 0))
        }
    }

    /// For stack/queue/deque: the item index the op acts on (the open end in use).
    var endIndex: Int? {
        guard n > 0 else { return nil }
        switch kind {
        case .stack: return n - 1
        case .queue: return 0
        case .deque: return end == .front ? 0 : n - 1
        case .list: return nil
        }
    }

    func canSelect(_ i: Int) -> Bool { kind == .list && !busy && (op == .insert ? i <= n : i < n) }

    func select(_ i: Int) {
        guard canSelect(i) else { return }
        selected = i
        if op == .insert { pos = .index }
    }

    // MARK: Preview

    private func show(_ i: Int?) -> String { i.map { "\(items[$0])" } ?? "–" }

    var chips: String {
        var parts: [String]
        switch kind {
        case .stack: parts = ["size = \(n)", "top = \(show(n > 0 ? n - 1 : nil))"]
        case .queue: parts = ["size = \(n)", "front = \(show(n > 0 ? 0 : nil))"]
        case .deque: parts = ["size = \(n)", "front = \(show(n > 0 ? 0 : nil))", "back = \(show(n > 0 ? n - 1 : nil))"]
        case .list:
            parts = ["length = \(n)"]
            if previewing { parts.append("hops = \(hops)") }
        }
        if previewing {
            parts.append("cost = \(previewCost)")
        } else if !cost.isEmpty {
            parts.append(cost)
        }
        return parts.joined(separator: " · ")
    }

    private var hops: Int {
        switch op {
        case .insert: target
        case .delete: n == 0 ? 0 : target
        default: n
        }
    }

    private var previewCost: String {
        guard kind == .list else { return "O(1)" }
        switch op {
        case .search: return "O(n)"
        default: return hops == 0 ? "O(1)" : "O(i)"
        }
    }

    var caption: String { previewing ? preview : status }

    private var preview: String {
        let v = value
        if let blockReason { return blockReason }
        switch (kind, op) {
        case (.stack, .push):
            return n == 0 ? "Push \(v) onto the empty stack. It becomes the top. Only the top pointer moves."
                : "Push \(v) lands on top of \(items[n - 1]). It becomes the new top. Only the top pointer moves. Nothing below is touched."
        case (.stack, .pop):
            return "Pop removes \(items[n - 1]) from the top. " + (n == 1 ? "The stack becomes empty." : "\(items[n - 2]) becomes the new top.")
                + " Last in, first out: only the top is reachable."
        case (.stack, .peek):
            return "Peek reads \(items[n - 1]) without removing it. The stack is unchanged, and it costs O(1)."
        case (.queue, .enqueue):
            return n == 0 ? "Enqueue \(v) into the empty queue. It is both front and back."
                : "Enqueue \(v) joins behind \(items[n - 1]). \(items[0]) is still first out. First in, first out: work happens only at the two ends."
        case (.queue, .dequeue):
            return "Dequeue removes \(items[0]) from the front. " + (n == 1 ? "The queue becomes empty." : "\(items[1]) is next in line.")
                + " Nothing else moves."
        case (.queue, .peek):
            return "Peek reads \(items[0]) at the front without removing it. Nothing moves, O(1)."
        case (.deque, .push):
            guard n > 0 else { return "Push \(v) into the empty deque. It is both front and back." }
            let old = end == .front ? items[0] : items[n - 1]
            return "Push \(v) at the \(end.rawValue). The \(end.rawValue) pointer moves from \(old) to \(v). Four operations where a queue has two. Nothing in the middle ever shifts."
        case (.deque, .pop):
            let gone = end == .front ? items[0] : items[n - 1]
            let next = n == 1 ? nil : (end == .front ? items[1] : items[n - 2])
            return "Pop removes \(gone) from the \(end.rawValue). " + (next.map { "\($0) becomes the new \(end.rawValue)." } ?? "The deque becomes empty.")
                + " Either end is open, so both are O(1)."
        case (.deque, .peek):
            return "Peek reads \(items[endIndex!]) at the \(end.rawValue) without removing it. Nothing moves, O(1)."
        case (.list, .insert):
            let t = target
            if t == 0 { return "Insert \(v) at the head. Link \(v) → \(show(n > 0 ? 0 : nil)) and move head to it. No walk needed, so it is O(1)." }
            let after = t < n ? "\(items[t])" : "null"
            return "Walk \(t) hop\(t == 1 ? "" : "s") to \(items[t - 1]), then link \(items[t - 1]) → \(v) → \(after). Two pointer writes. The walk to the spot is what costs O(i)."
        case (.list, .delete):
            let t = target
            if t == 0 { return "Delete the head \(items[0]). Head moves to \(show(n > 1 ? 1 : nil)). No walk needed, so it is O(1)." }
            let after = t + 1 < n ? "\(items[t + 1])" : "null"
            return "Walk \(t) hop\(t == 1 ? "" : "s") to \(items[t - 1]), then link \(items[t - 1]) → \(after). The removed node is simply unlinked. The walk is what costs O(i)."
        case (.list, .search):
            return "Search walks from head, comparing each node to \(v). A list has no index math, so a miss costs O(n)."
        default:
            return ""
        }
    }

    private var noun: String {
        switch kind {
        case .stack: "stack"
        case .queue: "queue"
        case .deque: "deque"
        case .list: "list"
        }
    }

    // MARK: Operations

    private func clearResult() {
        guard !busy else { return }
        showingResult = false
        cost = ""
    }

    private func tick() async { try? await Task.sleep(for: .milliseconds(stepMs)) }
    private func mark(_ i: Int?, _ kind: LinearMark = .cursor) { markIndex = i; markKind = kind }
    private func fail(_ message: String) { status = message; cost = ""; showingResult = true }

    private func run(_ block: @escaping @MainActor () async -> Void) {
        guard !busy else { return }
        busy = true
        job = Task { @MainActor in
            await block()
            mark(nil)
            showingResult = true
            busy = false
        }
    }

    func runOp() {
        if let blockReason { return fail(blockReason) }
        let v = Int(valueInput)
        switch kind {
        case .list: run { await self.runList(v ?? 0) }
        default: run { await self.runEnds(v ?? 0) }
        }
    }

    /// Stack, queue and deque: every op touches one end only.
    private func runEnds(_ v: Int) async {
        let front = kind == .queue ? op != .enqueue : kind == .deque ? end == .front : false
        switch op {
        case .push, .enqueue:
            if front { items.insert(v, at: 0); mark(0, .write) } else { items.append(v); mark(n - 1, .write) }
            status = kind == .stack ? "Pushed \(v). It is the new top, and nothing below moved."
                : kind == .queue ? "Enqueued \(v) at the back. It leaves after everything already waiting."
                : "Pushed \(v) at the \(end.rawValue). Nothing in the middle moved."
            cost = "cost = O(1)"
            await tick()
            await tick()
        case .pop, .dequeue:
            let i = front ? 0 : n - 1
            let gone = items[i]
            mark(i, .gone)
            await tick()
            await tick()
            if Task.isCancelled { return }
            items.remove(at: i)
            mark(nil)
            status = kind == .stack ? "Popped \(gone). " + (n > 0 ? "\(items[n - 1]) is the top again." : "The stack is empty.")
                : kind == .queue ? "Dequeued \(gone) from the front. " + (n > 0 ? "\(items[0]) is next." : "The queue is empty.")
                : "Popped \(gone) from the \(end.rawValue). Only that end changed."
            cost = "cost = O(1)"
        default:
            let i = endIndex ?? 0
            mark(i)
            status = "\(kind == .stack ? "Top" : kind == .queue ? "Front" : end.rawValue.capitalized) is \(items[i]). Peek left the \(noun) unchanged."
            cost = "cost = O(1)"
            await tick()
            await tick()
            await tick()
        }
    }

    /// Singly linked list: reaching position i means walking i nodes from head.
    private func runList(_ v: Int) async {
        switch op {
        case .insert:
            let t = target
            for h in 0..<t {
                mark(h)
                status = "Walking: hop \(h + 1) of \(t), at \(items[h])."
                await tick()
                if Task.isCancelled { return }
            }
            items.insert(v, at: t)
            mark(t, .write)
            status = t == 0 ? "Inserted \(v) at the head. One link and the head pointer, no walk."
                : "Inserted \(v) after \(items[t - 1]) in \(t) hop\(t == 1 ? "" : "s"). Two pointer writes did the rest."
            cost = "hops = \(t) · cost = " + (t == 0 ? "O(1)" : "O(i)")
            await tick()
            await tick()
        case .delete:
            let t = target
            for h in 0..<t {
                mark(h)
                status = "Walking: hop \(h + 1) of \(t), at \(items[h])."
                await tick()
                if Task.isCancelled { return }
            }
            let gone = items[t]
            mark(t, .gone)
            await tick()
            await tick()
            items.remove(at: t)
            mark(nil)
            status = t == 0 ? "Deleted the head \(gone). The head pointer moved on, no walk."
                : "Deleted \(gone). \(items[t - 1]) now links past it in one pointer write."
            cost = "hops = \(t) · cost = " + (t == 0 ? "O(1)" : "O(i)")
        default:
            for i in items.indices {
                mark(i)
                status = "Comparing node \(i), \(items[i]), with \(v)."
                await tick()
                if Task.isCancelled { return }
                if items[i] == v {
                    mark(i, .write)
                    status = "Found \(v) at node \(i) after \(i + 1) comparison\(i == 0 ? "" : "s"). A list can only be searched by walking."
                    cost = "hops = \(i) · cost = O(n)"
                    await tick()
                    await tick()
                    return
                }
            }
            status = "\(v) is not in the list. A miss walks every node."
            cost = "hops = \(n) · cost = O(n)"
        }
    }

    func reset() {
        job?.cancel()
        busy = false
        items = initialItems
        mark(nil)
        op = ops[0]
        end = .back
        pos = .index
        selected = 1
        showingResult = false
        status = ""
        cost = ""
    }

    func cancel() { job?.cancel() }
}

// MARK: - Entry points

struct StackQueueLab: View {
    let isStack: Bool
    var body: some View { LinearSandboxLab(kind: isStack ? .stack : .queue) }
}

struct LinkedListLab: View {
    var body: some View { LinearSandboxLab(kind: .list) }
}

struct DequeLab: View {
    var body: some View { LinearSandboxLab(kind: .deque) }
}

struct LinearSandboxLab: View {
    @State private var model: LinearSandbox
    @Environment(\.labDock) private var dock

    init(kind: LinearKind) { _model = State(initialValue: LinearSandbox(kind: kind)) }

    var body: some View {
        Group {
            if let dock {
                VStack(alignment: .leading, spacing: 0) {
                    LabCard { LinearStage(model: model) }
                    LinearNarration(model: model).padding(.horizontal, 4).padding(.top, 14)
                }
                .onAppear {
                    let model = model
                    dock.controls = { AnyView(LinearControls(model: model)) }
                    dock.navAction = LabNavAction(icon: "arrow.counterclockwise", label: "Reset") { model.reset() }
                }
                .onDisappear { dock.controls = nil; dock.navAction = nil }
            } else {
                LabCard {
                    LinearStage(model: model, showsReset: true)
                    LinearNarration(model: model).padding(.top, 14)
                    Divider().padding(.top, 16)
                    LinearControls(model: model).padding(.top, 14)
                }
            }
        }
        .onDisappear { model.cancel() }
    }
}

// MARK: - Stage

private struct LinearStage: View {
    let model: LinearSandbox
    var showsReset = false
    @Environment(\.palette) private var palette

    private var inFill: Color { SimColors.blue.opacity(0.28) }
    private var incoming: Bool { model.previewing && model.op.adds && !model.full }
    private var leaving: Int? {
        guard model.previewing, model.op.removes, model.n > 0 else { return nil }
        return model.kind == .list ? model.target : model.endIndex
    }

    var body: some View {
        VStack(spacing: 0) {
            ZStack(alignment: .topTrailing) {
                Group {
                    switch model.kind {
                    case .stack: stack
                    case .queue, .deque: row
                    case .list: list
                    }
                }
                .frame(maxWidth: .infinity)
                if showsReset {
                    Button(action: model.reset) {
                        Image(systemName: "arrow.counterclockwise").font(.system(size: 16, weight: .semibold)).foregroundStyle(palette.muted)
                            .frame(width: 32, height: 32)
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel("Reset")
                }
            }
            legend.padding(.top, 16)
        }
        .animation(.easeInOut(duration: 0.18), value: model.items)
        .animation(.easeInOut(duration: 0.12), value: model.markIndex)
    }

    // Colours: the open end in use is yellow, the rest blue, incoming dashed accent; while an op
    // runs the marked cell is yellow (visiting), green (written) or red (leaving).
    private func style(_ i: Int, isEnd: Bool) -> (Color, Color, Bool) {
        if i == model.markIndex {
            switch model.markKind {
            case .cursor: return (SimColors.active, Color(hex: 0x1A1A1A), false)
            case .write: return (SimColors.green, .white, false)
            case .gone: return (SimColors.red, .white, false)
            }
        }
        if i == leaving { return (SimColors.red.opacity(0.85), .white, false) }
        if isEnd { return (SimColors.active, Color(hex: 0x1A1A1A), false) }
        return (inFill, palette.onSurface, true)
    }

    private func box(_ text: String, fill: Color, fg: Color, border: Bool, width: CGFloat?, height: CGFloat) -> some View {
        Text(text)
            .font(AppFont.mono(18, .bold))
            .foregroundStyle(fg)
            .frame(width: width, height: height)
            .frame(maxWidth: width == nil ? .infinity : nil)
            .background(fill, in: RoundedRectangle(cornerRadius: 10))
            .overlay { if border { RoundedRectangle(cornerRadius: 10).stroke(SimColors.blue, lineWidth: 1.5) } }
    }

    private func incomingBox(width: CGFloat?, height: CGFloat) -> some View {
        Text(model.value)
            .font(AppFont.mono(18, .bold))
            .foregroundStyle(palette.primary.opacity(0.9))
            .frame(width: width, height: height)
            .frame(maxWidth: width == nil ? .infinity : nil)
            .background(palette.primary.opacity(0.14), in: RoundedRectangle(cornerRadius: 10))
            .overlay(RoundedRectangle(cornerRadius: 10).stroke(palette.primary, style: StrokeStyle(lineWidth: 1.5, dash: [4, 3])))
    }

    private func tag(_ text: String, _ color: Color) -> some View {
        Text(text).font(AppFont.mono(13, .semibold)).foregroundStyle(color)
    }

    // Stack: bottom to top, a base line underneath, labels to the right.
    private var stack: some View {
        VStack(spacing: 8) {
            if incoming {
                HStack(spacing: 12) {
                    incomingBox(width: 180, height: 52)
                    tag("← push", palette.primary).frame(width: 64, alignment: .leading)
                }
            }
            ForEach(model.items.indices.reversed(), id: \.self) { i in
                let (fill, fg, border) = style(i, isEnd: i == model.n - 1)
                HStack(spacing: 12) {
                    box("\(model.items[i])", fill: fill, fg: fg, border: border, width: 180, height: 52)
                    Group {
                        if i == model.n - 1 { tag(model.op == .pop && model.previewing ? "← pop" : "← top", i == leaving ? SimColors.red : SimColors.active) }
                        else { Text("") }
                    }
                    .frame(width: 64, alignment: .leading)
                }
            }
            if model.n == 0 && !incoming {
                Text("empty").font(AppFont.mono(14)).foregroundStyle(palette.muted).frame(height: 52)
            }
            Rectangle().fill(palette.muted.opacity(0.5)).frame(width: 220, height: 2).padding(.trailing, 76)
        }
        .frame(minHeight: 320, alignment: .bottom)
        .padding(.top, 8)
    }

    // Queue and deque: a row, front on the left; the end labels and the "open end" notes beneath.
    private var row: some View {
        let isDeque = model.kind == .deque
        let pushFront = isDeque && model.end == .front
        let cells = model.n + (incoming ? 1 : 0)
        return GeometryReader { geo in
            let gap: CGFloat = 8
            let slots = CGFloat(max(cells, 4))
            // Cells shrink to fit up to a floor; past that the row scrolls sideways.
            let w = max((geo.size.width - gap * (slots - 1)) / slots, 52)
            VStack(alignment: .leading, spacing: 8) {
              ScrollView(.horizontal, showsIndicators: false) {
               VStack(alignment: .leading, spacing: 8) {
                HStack(spacing: gap) {
                    if incoming && pushFront { incomingBox(width: w, height: 58) }
                    ForEach(model.items.indices, id: \.self) { i in
                        let isEnd = isDeque ? i == model.endIndex : i == 0
                        let (fill, fg, border) = style(i, isEnd: isEnd)
                        box("\(model.items[i])", fill: fill, fg: fg, border: border, width: w, height: 58)
                    }
                    if incoming && !pushFront { incomingBox(width: w, height: 58) }
                }
                HStack(spacing: gap) {
                    if incoming && pushFront { tag("new", palette.primary).frame(width: w) }
                    ForEach(model.items.indices, id: \.self) { i in
                        let isFront = i == 0, isBack = i == model.n - 1
                        let label = isFront && isBack ? "front·back" : isFront ? "front" : isBack ? "back" : ""
                        let lit = isDeque ? i == model.endIndex : isFront
                        tag(label, lit ? SimColors.active : palette.muted).lineLimit(1).minimumScaleFactor(0.6).frame(width: w)
                    }
                    if incoming && !pushFront { tag("new", palette.primary).frame(width: w) }
                }
               }
              }
                HStack {
                    Text(isDeque ? "⇄ open end" : "← leaves here")
                    Spacer()
                    Text(isDeque ? "open end ⇄" : "joins here ←")
                }
                .font(AppFont.sans(14))
                .foregroundStyle(palette.muted)
                .padding(.top, 10)
            }
        }
        .frame(height: 130)
        .padding(.top, 50)
        .padding(.bottom, 24)
    }

    // Singly linked list: nodes joined by links, null at the end; the incoming node sits dashed in
    // the slot it will take, and a node can be tapped to set the index.
    private var list: some View {
        let t = model.target
        var entries: [(Int?, Bool)] = model.items.indices.map { ($0, false) }
        if incoming { entries.insert((nil, true), at: t) }
        return GeometryReader { geo in
            let link = LinkedNodeSpec.link
            let nullW: CGFloat = 52
            // Every linked list in the app draws its nodes at LinkedNodeSpec's size; a list too long
            // for the screen scrolls sideways rather than shrinking them.
            let w = LinkedNodeSpec.width, h = LinkedNodeSpec.height
            ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 0) {
                ForEach(entries.indices, id: \.self) { k in
                    let (i, isNew) = entries[k]
                    VStack(spacing: 8) {
                        if isNew {
                            incomingBox(width: w, height: h)
                            tag("new", palette.primary)
                        } else if let i {
                            let walkTo = model.previewing && model.op != .search && i == t - 1
                            let (fill, fg, border) = style(i, isEnd: walkTo)
                            Button { model.select(i) } label: {
                                box("\(model.items[i])", fill: fill, fg: fg, border: border, width: w, height: h)
                            }
                            .buttonStyle(.plain)
                            tag(i == 0 ? "head" : i == model.n - 1 ? "tail" : " ", i == 0 && (walkTo || model.markIndex == 0) ? SimColors.active : palette.muted)
                        }
                    }
                    let nextNew = k + 1 < entries.count && entries[k + 1].1
                    linkLine(dashed: isNew || nextNew).frame(width: link).padding(.bottom, 22)
                }
                Text("null").font(AppFont.mono(14)).foregroundStyle(palette.muted).frame(width: nullW, alignment: .leading).padding(.bottom, 22)
            }
            .frame(minWidth: geo.size.width, maxHeight: .infinity)
            }
        }
        .frame(height: 100)
        .padding(.vertical, 50)
    }

    private func linkLine(dashed: Bool) -> some View {
        Canvas { ctx, size in
            var p = Path()
            p.move(to: CGPoint(x: 2, y: size.height / 2))
            p.addLine(to: CGPoint(x: size.width - 2, y: size.height / 2))
            ctx.stroke(p, with: .color(dashed ? palette.primary : palette.muted), style: StrokeStyle(lineWidth: 2, dash: dashed ? [3, 3] : []))
        }
        .frame(height: 4)
    }

    private var legend: some View {
        let endLabel = switch model.kind {
        case .stack: "Top"
        case .queue: "Front"
        case .deque: "End in use"
        case .list: "Walk to"
        }
        let bodyLabel = switch model.kind {
        case .stack: "In stack"
        case .queue: "Waiting"
        case .deque: "In deque"
        case .list: "Node"
        }
        return HStack(spacing: 14) {
            LegendItem(color: SimColors.active, label: endLabel)
            LegendItem(color: SimColors.blue, label: bodyLabel)
            if model.op.removes {
                LegendItem(color: SimColors.red, label: "Leaving")
            } else {
                HStack(spacing: 6) {
                    RoundedRectangle(cornerRadius: 3).stroke(palette.primary, style: StrokeStyle(lineWidth: 1.5, dash: [3, 2])).frame(width: 10, height: 10)
                    Text("Incoming").font(AppFont.sans(13)).foregroundStyle(palette.onSurface.opacity(0.75))
                }
            }
            Spacer(minLength: 0)
        }
    }
}

private struct LinearNarration: View {
    let model: LinearSandbox

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            ReadoutChips(text: model.chips)
            LabCaption(text: model.caption).frame(minHeight: 72, alignment: .top)
        }
    }
}

// MARK: - Controls

private struct LinearControls: View {
    @Bindable var model: LinearSandbox
    @FocusState private var editingValue: Bool
    @Environment(\.palette) private var palette

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            if let reason = model.blockReason, !model.busy { LabNotice(text: reason) }
            ChipPicker(options: model.ops.map { ($0, $0.rawValue) }, selection: $model.op)
            HStack(spacing: 8) {
                Text(model.op.rawValue).font(AppFont.sans(17)).foregroundStyle(palette.muted)
                if model.op.adds || model.op == .search { valueToken }
                if let connector { Text(connector).font(AppFont.sans(17)).foregroundStyle(palette.muted) }
                if model.kind == .deque || (model.kind == .list && model.op != .search) { positionMenu }
                Spacer(minLength: 0)
                Button {
                    editingValue = false
                    model.runOp()
                } label: {
                    Image(systemName: "play.fill").font(.system(size: 16, weight: .bold)).foregroundStyle(.white)
                        .frame(width: 44, height: 44)
                        .background(palette.primary.opacity(model.canRun ? 1 : 0.4), in: Circle())
                }
                .buttonStyle(.plain)
                .disabled(model.busy)
                .accessibilityLabel("Run")
            }
            .padding(.leading, 16)
            .padding(.trailing, 8)
            .frame(height: 60)
            .background(SimColors.tint.opacity(0.6), in: RoundedRectangle(cornerRadius: 16))
            Text(hint).font(AppFont.sans(13)).foregroundStyle(palette.muted).padding(.horizontal, 4)
                .fixedSize(horizontal: false, vertical: true)
        }
    }

    private var connector: String? {
        switch (model.kind, model.op) {
        case (.deque, .push): "to"
        case (.deque, .pop): "from"
        case (.deque, .peek): "at"
        case (.list, .insert): "at"
        default: nil
        }
    }

    private var hint: String {
        switch model.kind {
        case .stack: "Pop and Peek take no value. The field hides and Run acts on the top."
        case .queue: model.n > 0 ? "Dequeue removes \(model.items[0]) from the front. Peek reads it without removing it." : "Dequeue and Peek act on the front."
        case .deque: "Menu: Front · Back. That one choice is what makes it a deque."
        case .list: "Menu: Head · Tail · Index. Or tap a node to set the index."
        }
    }

    private var valueToken: some View {
        TextField("?", text: $model.valueInput)
            .font(AppFont.mono(17, .semibold))
            .keyboardType(.numbersAndPunctuation)
            .focused($editingValue)
            .multilineTextAlignment(.center)
            .fixedSize()
            .frame(minWidth: 28)
            .padding(.horizontal, 10)
            .frame(height: 36)
            .background(SimColors.tint, in: RoundedRectangle(cornerRadius: 9))
            .overlay(alignment: .bottom) { Rectangle().fill(palette.primary).frame(height: 2).padding(.horizontal, 4) }
            .clipShape(RoundedRectangle(cornerRadius: 9))
    }

    private var positionMenu: some View {
        Menu {
            if model.kind == .deque {
                Button { model.end = .front } label: { menuLabel("Front", model.end == .front) }
                Button { model.end = .back } label: { menuLabel("Back", model.end == .back) }
            } else if model.op == .insert {
                Button { model.pos = .head } label: { menuLabel("Head", model.pos == .head) }
                Button { model.pos = .index } label: { menuLabel("Index \(min(model.selected, model.n))", model.pos == .index) }
                Button { model.pos = .tail } label: { menuLabel("Tail", model.pos == .tail) }
            } else {
                ForEach(0..<max(model.n, 1), id: \.self) { i in
                    Button { model.selected = i } label: { menuLabel("Index \(i)", model.target == i) }
                }
            }
        } label: {
            HStack(spacing: 6) {
                Text(positionLabel).font(AppFont.sans(17, .semibold)).lineLimit(1)
                Image(systemName: "chevron.up.chevron.down").font(.system(size: 11, weight: .semibold))
            }
            .foregroundStyle(palette.primary)
            .padding(.leading, 12)
            .padding(.trailing, 8)
            .frame(height: 36)
            .background(palette.primary.opacity(0.18), in: RoundedRectangle(cornerRadius: 9))
        }
        .disabled(model.busy)
    }

    private var positionLabel: String {
        if model.kind == .deque { return model.end.rawValue }
        if model.op == .insert && model.pos == .head { return "head" }
        if model.op == .insert && model.pos == .tail { return "tail" }
        return "index \(model.target)"
    }

    @ViewBuilder private func menuLabel(_ text: String, _ checked: Bool) -> some View {
        if checked { Label(text, systemImage: "checkmark") } else { Text(text) }
    }
}
