import SwiftUI

// Port of feature/topics/ArraySimulationSection.kt. A fixed-size contiguous block: every slot is
// drawn, filled or free, and every element move is animated one slot per tick — which is why an
// insert or delete is O(n) while an index read is O(1).
//
// Laid out as the sandbox in docs/ios-design/Simulations iOS.html (screen 04): pick an operation,
// read it as a sentence ("Insert [42] at [index 2 ⌄]"), see its cost previewed on the stage, run.
// On the sim-only screen the controls are pinned to the bottom and reset sits in the nav bar.

private let initialCapacity = 8
private let maxCapacity = 16
private let initialValues = [4, 8, 15, 16, 23]
private let defaultSelected = 2

private enum Mark { case cursor, move, write }

/// The four things you can do to an array, picked with the segmented control.
private enum ArrayOp: String, CaseIterable { case insert = "Insert", delete = "Delete", access = "Access", search = "Search" }

/// Where an insert lands. Head and Tail stay pinned as the array changes size.
private enum InsertPos: Hashable { case head, index, tail }

/// The lab's state and operations. A class rather than view @State so the controls pinned in the
/// screen's bottom bar (outside this view's tree) read and write the same live state.
@MainActor
@Observable
private final class ArraySandbox {
    var slots: [Int?] = ArraySandbox.initialSlots()
    var size = initialValues.count
    var markIndex: Int?
    var markKind = Mark.cursor
    var status = ""
    var cost = ""
    var op = ArrayOp.insert { didSet { clearResult() } }
    var pos = InsertPos.index { didSet { clearResult() } }
    var selected = defaultSelected { didSet { clearResult() } }
    var valueInput = "42" { didSet { clearResult() } }
    var busy = false
    /// Set when an op finishes, so its outcome stays on screen until the next edit.
    var showingResult = false
    private var job: Task<Void, Never>?

    private let stepMs = 300

    private static func initialSlots() -> [Int?] {
        var s = [Int?](repeating: nil, count: initialCapacity)
        for (i, v) in initialValues.enumerated() { s[i] = v }
        return s
    }

    /// The index the current op targets.
    var target: Int {
        switch op {
        case .insert: pos == .head ? 0 : pos == .tail ? size : min(selected, size)
        case .delete, .access: min(selected, max(size - 1, 0))
        case .search: 0
        }
    }

    var previewing: Bool { !busy && !showingResult }

    /// Why Run can't do this op right now, in words; nil when it can.
    var blockReason: String? {
        if op == .insert && full && slots.count * 2 > maxCapacity {
            return "No free slot. All \(slots.count) slots are used and this array can't grow past \(maxCapacity). Delete something first."
        }
        if (op == .delete || op == .access) && size == 0 { return "The array is empty, so there is nothing to \(op == .delete ? "delete" : "read"). Insert something first." }
        if (op == .insert || op == .search) && Int(valueInput) == nil { return "Type a number to \(op == .insert ? "insert" : "find") first." }
        return nil
    }

    /// A heads-up that the op costs more than it looks: a full block has to be reallocated first.
    var growNotice: String? {
        guard op == .insert, full, blockReason == nil else { return nil }
        return "All \(slots.count) slots are used. Inserting copies every element into a new \(slots.count * 2)-slot block first."
    }
    var full: Bool { size == slots.count }
    var value: String { valueInput.isEmpty ? "?" : valueInput }

    func willShift(_ i: Int) -> Bool {
        guard previewing else { return false }
        switch op {
        case .insert: return i >= target && i < size
        case .delete: return i > target && i < size
        default: return false
        }
    }

    func canSelect(_ i: Int) -> Bool { op == .insert ? i <= size : slots[i] != nil }

    func select(_ i: Int) {
        guard !busy, canSelect(i) else { return }
        selected = i
        if op == .insert { pos = .index }
    }

    // MARK: Preview

    var chips: String {
        var parts = ["size = \(size) / \(slots.count)"]
        guard previewing else { return cost.isEmpty ? parts[0] : parts[0] + " · " + cost }
        switch op {
        case .insert:
            let shifts = size - target
            parts += ["shifts = \(shifts)", "cost = " + (shifts == 0 && !full ? "O(1)" : "O(n−i)")]
        case .delete:
            parts += ["shifts = \(max(size - 1 - target, 0))", "cost = O(n−i)"]
        case .access: parts += ["steps = 1", "cost = O(1)"]
        case .search: parts += ["worst = \(size) compares", "cost = O(n)"]
        }
        return parts.joined(separator: " · ")
    }

    var caption: String { previewing ? preview : status }

    private var preview: String {
        if let blockReason { return blockReason }
        let n = { (k: Int) in k == 1 ? "One element moves" : "\(Self.spell(k)) elements move" }
        switch op {
        case .insert:
            let shifts = size - target
            let grow = full ? " The block is full, so it first doubles to \(slots.count * 2) and copies all \(size)." : ""
            if shifts == 0 { return "Appending at index \(target). Nothing moves, \(value) lands in free capacity." + grow }
            return "Index \(target) is selected. \(n(shifts)) right before \(value) lands." + grow
        case .delete:
            guard size > 0 else { return "The array is empty. Insert something first." }
            let shifts = size - 1 - target
            return shifts == 0
                ? "Removing the last element at index \(target). Nothing needs to move."
                : "Index \(target) is selected. \(n(shifts)) left to close the gap."
        case .access:
            guard size > 0 else { return "The array is empty. Insert something first." }
            return "Reading index \(target) is one address computation. Base + \(target) × element size, no scanning."
        case .search:
            return "Looking for \(value) means checking cells left to right. An unsorted array has no shortcut."
        }
    }

    private static func spell(_ k: Int) -> String {
        let words = ["Zero", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine", "Ten"]
        return k < words.count ? words[k] : "\(k)"
    }

    // MARK: Operations

    private func clearResult() {
        guard !busy else { return }
        showingResult = false
        cost = ""
    }

    private func tick() async { try? await Task.sleep(for: .milliseconds(stepMs)) }

    private func mark(_ i: Int?, _ kind: Mark = .cursor) { markIndex = i; markKind = kind }

    private func fail(_ message: String) { status = message; cost = ""; showingResult = true }

    /// One animated op at a time — a queued op would index into a list another op already shifted.
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
        switch op {
        case .insert:
            guard let v = Int(valueInput) else { return fail("Enter a value first.") }
            let at = target
            run { await self.insert(at: at, v) }
        case .delete: run { await self.delete() }
        case .access: run { await self.access() }
        case .search:
            guard let v = Int(valueInput) else { return fail("Enter a value first.") }
            run { await self.search(v) }
        }
    }

    /// A dynamic array allocates a bigger block and copies every element: O(n), amortized O(1).
    private func grow() async -> Bool {
        let capacity = slots.count
        let newCapacity = capacity * 2
        guard newCapacity <= maxCapacity else {
            status = "Capacity \(capacity) is full. This lab stops growing at \(maxCapacity)."
            cost = ""
            return false
        }
        status = "Full: allocating a block of \(newCapacity) and copying \(size) elements."
        slots += [Int?](repeating: nil, count: newCapacity - capacity)
        for i in 0..<size {
            mark(i, .move)
            await tick()
            if Task.isCancelled { return false }
        }
        cost = "copies = \(size)"
        return true
    }

    /// Everything from `at` shifts right, highest index first, so nothing live is overwritten.
    private func insert(at: Int, _ value: Int) async {
        if size == slots.count, !(await grow()) { return }
        var shifts = 0
        var j = size - 1
        while j >= at {
            slots[j + 1] = slots[j]
            slots[j] = nil
            mark(j + 1, .move)
            shifts += 1
            status = "Shifting index \(j) → \(j + 1)."
            await tick()
            if Task.isCancelled { return }
            j -= 1
        }
        slots[at] = value
        size += 1
        mark(at, .write)
        status = "Inserted \(value) at index \(at). " + (shifts == 0 ? "A tail insert moves nothing." : "Every later element moved one slot right.")
        cost = "shifts = \(shifts) · cost = " + (shifts == 0 ? "O(1)" : "O(n−i)")
        await tick()
    }

    private func access() async {
        let i = target
        guard (0..<size).contains(i) else { status = "The array is empty."; cost = ""; return }
        mark(i)
        status = "a[\(i)] = \(slots[i].map(String.init) ?? ""). One address computation, no scanning."
        cost = "steps = 1 · cost = O(1)"
        await tick()
        await tick()
    }

    private func search(_ v: Int) async {
        var comparisons = 0
        for i in 0..<size {
            mark(i)
            comparisons += 1
            status = "Comparing a[\(i)] = \(slots[i] ?? 0) with \(v)."
            await tick()
            if Task.isCancelled { return }
            if slots[i] == v {
                mark(i, .write)
                status = "Found \(v) at index \(i) after \(comparisons) comparison\(comparisons == 1 ? "" : "s"). An unsorted array has no shortcut."
                cost = "compares = \(comparisons) · cost = O(n)"
                await tick()
                return
            }
        }
        status = "\(v) is not in the array. A miss always scans every element."
        cost = "compares = \(comparisons) · cost = O(n)"
    }

    private func delete() async {
        let i = target
        guard (0..<size).contains(i) else { status = "The array is empty."; cost = ""; return }
        let removed = slots[i]
        mark(i, .write)
        status = "Removing \(removed ?? 0) at index \(i)."
        await tick()
        // Close the hole: each later element slides one slot left.
        var shifts = 0
        for j in i..<(size - 1) {
            slots[j] = slots[j + 1]
            slots[j + 1] = nil
            mark(j, .move)
            shifts += 1
            status = "Shifting index \(j + 1) → \(j)."
            await tick()
            if Task.isCancelled { return }
        }
        slots[size - 1] = nil
        size -= 1
        status = "Deleted \(removed ?? 0). An array has no holes, so every later element closed the gap."
        cost = "shifts = \(shifts) · cost = O(n−i)"
    }

    func reset() {
        job?.cancel()
        busy = false
        slots = Self.initialSlots()
        size = initialValues.count
        mark(nil)
        selected = defaultSelected
        pos = .index
        op = .insert
        showingResult = false
        status = ""
        cost = ""
    }

    func cancel() { job?.cancel() }
}

struct ArrayLab: View {
    @State private var model = ArraySandbox()
    @Environment(\.labDock) private var dock

    var body: some View {
        Group {
            if let dock {
                // Docked: stage card, then readout and narration on the page, controls pinned below.
                VStack(alignment: .leading, spacing: 0) {
                    LabCard { ArrayStage(model: model) }
                    ArrayNarration(model: model).padding(.horizontal, 4).padding(.top, 14)
                }
                .onAppear {
                    let model = model
                    dock.controls = { AnyView(ArrayControls(model: model)) }
                    dock.navAction = LabNavAction(icon: "arrow.counterclockwise", label: "Reset array") { model.reset() }
                }
                .onDisappear { dock.controls = nil; dock.navAction = nil }
            } else {
                LabCard {
                    ArrayStage(model: model, showsReset: true)
                    ArrayNarration(model: model).padding(.top, 14)
                    Divider().padding(.top, 16)
                    ArrayControls(model: model).padding(.top, 14)
                }
            }
        }
        .onDisappear { model.cancel() }
    }
}

// MARK: - Stage

private struct ArrayStage: View {
    let model: ArraySandbox
    var showsReset = false
    @Environment(\.palette) private var palette

    var body: some View {
        VStack(spacing: 0) {
            GeometryReader { geo in
                let (w, gap) = metrics(geo.size.width)
                ZStack(alignment: .topLeading) {
                    if model.op == .insert && model.previewing { incomingToken(x: CGFloat(model.target) * (w + gap) + w / 2, width: geo.size.width) }
                    if showsReset {
                        Button(action: model.reset) {
                            Image(systemName: "arrow.counterclockwise").font(.system(size: 16, weight: .semibold)).foregroundStyle(palette.muted)
                                .frame(width: 32, height: 32)
                        }
                        .buttonStyle(.plain)
                        .accessibilityLabel("Reset array")
                        .frame(maxWidth: .infinity, alignment: .trailing)
                    }
                    HStack(spacing: gap) {
                        ForEach(model.slots.indices, id: \.self) { i in cell(i, width: w) }
                    }
                    .offset(y: 44)
                }
            }
            .frame(height: 44 + 72)
            HStack(spacing: 14) {
                LegendItem(color: SimColors.blue, label: model.op == .delete ? "Will shift left" : "Will shift right")
                HStack(spacing: 6) {
                    RoundedRectangle(cornerRadius: 3).stroke(palette.muted, style: StrokeStyle(lineWidth: 1.5, dash: [3, 2])).frame(width: 10, height: 10)
                    Text("Free capacity").font(AppFont.sans(13)).foregroundStyle(palette.onSurface.opacity(0.75))
                }
                Spacer(minLength: 0)
            }
            .padding(.top, 12)
        }
    }

    private func metrics(_ width: CGFloat) -> (CGFloat, CGFloat) {
        let gap: CGFloat = model.slots.count > 8 ? 3 : 5
        return ((width - gap * CGFloat(model.slots.count - 1)) / CGFloat(model.slots.count), gap)
    }

    /// The value about to be inserted, floating over the slot it will land in.
    private func incomingToken(x: CGFloat, width: CGFloat) -> some View {
        HStack(spacing: 5) {
            Text(model.value).font(AppFont.mono(15, .semibold))
            Image(systemName: "arrow.down").font(.system(size: 10, weight: .bold)).foregroundStyle(palette.primary)
        }
        .padding(.horizontal, 10)
        .frame(height: 30)
        .background(palette.primary.opacity(0.2), in: RoundedRectangle(cornerRadius: 9))
        .overlay(RoundedRectangle(cornerRadius: 9).stroke(palette.primary, lineWidth: 1.5))
        .fixedSize()
        .position(x: min(max(x, 30), width - 30), y: 18)
    }

    private func cell(_ i: Int, width: CGFloat) -> some View {
        let inUse = model.slots[i] != nil
        let marked = i == model.markIndex
        let isTarget = model.previewing && model.op != .search && i == model.target && model.canSelect(i)
        let shift = model.willShift(i)
        let fill: Color = marked
            ? (model.markKind == .cursor ? SimColors.active : model.markKind == .move ? SimColors.blue : SimColors.green)
            : shift ? SimColors.blue.opacity(0.25)
            : inUse ? (palette.dark ? Color(hex: 0x2A2E3A) : Color(hex: 0xE3E6EC)) : .clear
        let fg: Color = marked ? (model.markKind == .cursor ? Color(hex: 0x1A1A1A) : .white) : palette.onSurface
        return Button { model.select(i) } label: {
            VStack(spacing: 6) {
                ZStack {
                    RoundedRectangle(cornerRadius: 10).fill(fill)
                    if shift { RoundedRectangle(cornerRadius: 10).stroke(SimColors.blue, lineWidth: 1.5) }
                    if !inUse && !marked {
                        RoundedRectangle(cornerRadius: 10).stroke(palette.muted.opacity(0.6), style: StrokeStyle(lineWidth: 1.5, dash: [4, 3]))
                    }
                    if let v = model.slots[i] {
                        Text("\(v)").font(AppFont.mono(model.slots.count > 8 ? 13 : 17, .bold)).foregroundStyle(fg).minimumScaleFactor(0.5)
                    }
                }
                .frame(height: 50)
                .padding(isTarget ? 2 : 0)
                .overlay { if isTarget { RoundedRectangle(cornerRadius: 12).stroke(palette.primary, lineWidth: 2) } }
                Text("\(i)")
                    .font(AppFont.mono(12, isTarget ? .semibold : .regular))
                    .foregroundStyle(isTarget ? palette.primary : palette.muted.opacity(inUse ? 1 : 0.5))
            }
            .frame(width: width)
        }
        .buttonStyle(.plain)
        .animation(.easeInOut(duration: 0.12), value: model.slots)
    }
}

/// Readout chips over the narration. Two caption lines are reserved so nothing below jumps while
/// an op narrates.
private struct ArrayNarration: View {
    let model: ArraySandbox

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            ReadoutChips(text: model.chips)
            LabCaption(text: model.caption).frame(minHeight: 72, alignment: .top)
        }
    }
}

// MARK: - Controls

/// The op picker, where an insert lands as an inline segmented row, then the value (or index) and a
/// button named after the op.
private struct ArrayControls: View {
    @Bindable var model: ArraySandbox
    @Environment(\.palette) private var palette

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            if !model.busy {
                if let reason = model.blockReason { LabNotice(text: reason) }
                else if let note = model.growNotice { LabNotice(text: note, kind: .info) }
            }
            LabSegments(labels: ArrayOp.allCases.map(\.rawValue),
                        selected: Binding(get: { ArrayOp.allCases.firstIndex(of: model.op) ?? 0 },
                                          set: { if !model.busy { model.op = ArrayOp.allCases[$0] } }))
            if model.op == .insert {
                LabOptionRow(label: "At", options: ["Head", "Index \(min(model.selected, model.size))", "Tail"],
                             selected: model.pos == .head ? 0 : model.pos == .index ? 1 : 2, enabled: !model.busy) {
                    model.pos = $0 == 0 ? .head : $0 == 1 ? .index : .tail
                }
            }
            HStack(spacing: 12) {
                if model.op == .insert || model.op == .search {
                    LabValueStepper(label: "Value", text: $model.valueInput) { model.valueInput = "\((Int(model.valueInput) ?? 0) + $0)" }
                } else {
                    LabValueStepper(label: "Index", text: .constant("\(model.target)"), canDecrease: model.target > 0,
                                    canIncrease: model.target < model.size - 1, editable: false) { model.select(model.target + $0) }
                }
                LabActionButton(title: verb, enabled: !model.busy && model.blockReason == nil) { model.runOp() }
                    .disabled(model.busy)
            }
            if model.op == .insert && model.pos == .index {
                Text("Tap a cell to move the index.").font(AppFont.sans(13)).foregroundStyle(palette.muted).padding(.horizontal, 4)
            }
        }
    }

    private var verb: String {
        switch model.op {
        case .insert: "Insert"
        case .delete: "Delete"
        case .access: "Read"
        case .search: "Search"
        }
    }
}
