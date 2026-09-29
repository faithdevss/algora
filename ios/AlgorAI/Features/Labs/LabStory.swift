import SwiftUI

// Port of LabStory.kt: what the redesigned step-by-step labs share (docs mocks for Dijkstra, Coin Change,
// Fractional Knapsack, Job Sequencing): one set of state colours, a legend of only what is on screen,
// value chips whose number can take a state's colour, and a headline whose key term does.

/// A state a story paints its data in. `empty` is a slot still to fill, drawn dashed.
enum StoryTone: Hashable { case idle, active, done, warn, path, answer, empty }

extension StoryTone {
    var color: Color {
        switch self {
        case .active: return SimColors.active
        case .done: return SimColors.green
        case .warn: return SimColors.red
        case .path: return SimColors.blue
        case .answer: return SimColors.answer
        case .idle, .empty: return SimColors.grey
        }
    }

    /// The tone as text: lighter on the dark card, deeper on white, where the fills are too pale to read.
    func ink(_ palette: Palette) -> Color {
        let dark = palette.dark
        switch self {
        case .active: return dark ? SimColors.active : Color(hex: 0xB7791F)
        case .done: return dark ? Color(hex: 0x7DD3A0) : Color(hex: 0x15803D)
        case .warn: return dark ? Color(hex: 0xF87171) : Color(hex: 0xDC2626)
        case .path: return dark ? Color(hex: 0x93B4F8) : Color(hex: 0x2563EB)
        case .answer: return dark ? Color(hex: 0xB4A2FF) : Color(hex: 0x6D4AFF)
        case .idle: return palette.onSurface
        case .empty: return palette.muted
        }
    }
}

/// `{…}` in a headline is the term drawn in its state's colour: yellow by default (the thing being looked
/// at), `{w:…}` red, `{m:…}` green, `{p:…}` blue and `{v:…}` violet. `\{` is a literal brace ("\{0,1}").
private func storySpans(_ text: String) -> [(text: String, tone: StoryTone?)] {
    var out: [(String, StoryTone?)] = []
    var rest = Substring(text)
    var plain = ""
    while let open = rest.firstIndex(of: "{"), let close = rest[open...].firstIndex(of: "}") {
        if open > rest.startIndex, rest[rest.index(before: open)] == "\\" {
            plain += rest[..<rest.index(before: open)] + "{"
            rest = rest[rest.index(after: open)...]
            continue
        }
        out.append((plain + String(rest[..<open]), nil))
        plain = ""
        var inner = rest[rest.index(after: open)..<close]
        var tone = StoryTone.active
        if inner.count > 2, let first = inner.first, "wmpv".contains(first), inner.dropFirst().first == ":" {
            tone = first == "w" ? .warn : first == "m" ? .done : first == "p" ? .path : .answer
            inner = inner.dropFirst(2)
        }
        out.append((String(inner), tone))
        rest = rest[rest.index(after: close)...]
    }
    out.append((plain + String(rest), nil))
    return out
}

/// `text` with each marked term in its tone's text colour.
func storyText(_ text: String, _ palette: Palette) -> Text {
    storySpans(text).reduce(Text("")) { out, span in
        guard let tone = span.tone else { return out + Text(span.text) }
        return out + Text(span.text).foregroundColor(tone.ink(palette))
    }
}

/// The recurrence being evaluated, centred in a tinted strip: "min( skip {p:2} , use 1 + {p:2} ) = {2}".
struct StoryFormula: View {
    let text: String
    @Environment(\.palette) private var palette

    var body: some View {
        storyText(text, palette)
            .font(AppFont.mono(15))
            .foregroundStyle(palette.onSurface.opacity(0.85))
            .lineLimit(1)
            .minimumScaleFactor(0.7)
            .padding(.horizontal, 12)
            .frame(maxWidth: .infinity)
            .frame(height: 42)
            .background(SimColors.tint, in: RoundedRectangle(cornerRadius: 10))
    }
}

/// A labelled line of arithmetic: "k = 2" muted on the left, "{p:1500} + 0 + 3000 = {4500}" on the right.
struct StoryFormulaRow { let label: String; let formula: String }

struct StoryFormulaRows: View {
    let rows: [StoryFormulaRow]
    @Environment(\.palette) private var palette

    var body: some View {
        VStack(spacing: 6) {
            ForEach(rows.indices, id: \.self) { i in
                HStack(spacing: 10) {
                    Text(rows[i].label).foregroundStyle(palette.muted).lineLimit(1)
                    Spacer(minLength: 0)
                    storyText(rows[i].formula, palette).foregroundStyle(palette.onSurface.opacity(0.85)).lineLimit(1)
                        .minimumScaleFactor(0.7)
                }
                .font(AppFont.mono(15))
                .padding(.horizontal, 14)
                .frame(height: 40)
                .background(SimColors.tint, in: RoundedRectangle(cornerRadius: 10))
            }
        }
    }
}

/// `text` with its colour marks dropped, for captions and the All steps sheet.
func storyPlain(_ text: String) -> String { storySpans(text).map(\.text).joined() }

/// A step's narration: the headline large and bold with its marked term coloured, the why muted under it.
struct LabStoryNarration: View {
    let headline: String
    let body_: String
    @Environment(\.palette) private var palette

    init(headline: String, body: String) {
        self.headline = headline
        self.body_ = body
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            storyText(headline, palette)
            .font(AppFont.sans(20, .bold))
            .foregroundStyle(palette.onSurface)
            .fixedSize(horizontal: false, vertical: true)
            if !body_.isEmpty {
                Text(body_).font(AppFont.sans(15)).foregroundStyle(palette.muted).lineSpacing(3)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

enum SwatchStyle { case fill, dashed, ring, dot }

/// A legend swatch: filled, a dashed outline (a frontier, a node not made yet) or a solid ring (a goal).
struct StorySwatch: View {
    let color: Color
    let style: SwatchStyle
    let label: String
    @Environment(\.palette) private var palette

    var body: some View {
        HStack(spacing: 6) {
            let shape = RoundedRectangle(cornerRadius: 3)
            switch style {
            case .fill: shape.fill(color).frame(width: 10, height: 10)
            case .dot: Circle().fill(color).frame(width: 10, height: 10)
            case .ring: shape.stroke(color, lineWidth: 1.5).frame(width: 10, height: 10)
            case .dashed: shape.stroke(color, style: StrokeStyle(lineWidth: 1.5, dash: [2, 1.5])).frame(width: 10, height: 10)
            }
            Text(label).font(AppFont.sans(13)).foregroundStyle(palette.onSurface.opacity(0.75))
        }
    }
}

struct StoryLegendRow: View {
    let items: [(color: Color, style: SwatchStyle, label: String)]

    var body: some View {
        if !items.isEmpty {
            FlowLayout(spacing: 14, lineSpacing: 6) {
                ForEach(items.indices, id: \.self) { i in
                    StorySwatch(color: items[i].color, style: items[i].style, label: items[i].label)
                }
            }
        }
    }
}

/// One value chip: the key muted, the value in its tone's colour ("greedy 3" in red).
struct StoryChip {
    let key: String
    let value: String
    var tone: StoryTone = .idle
    init(_ key: String, _ value: String, _ tone: StoryTone = .idle) { self.key = key; self.value = value; self.tone = tone }
}

struct StoryChips: View {
    let chips: [StoryChip]
    @Environment(\.palette) private var palette

    var body: some View {
        FlowLayout(spacing: 8, lineSpacing: 8) {
            ForEach(chips.indices, id: \.self) { i in
                let chip = chips[i]
                HStack(spacing: 8) {
                    Text(chip.key).foregroundStyle(palette.muted)
                    Text(chip.value).fontWeight(.bold).foregroundStyle(chip.tone.ink(palette))
                }
                .font(AppFont.mono(15))
                .lineLimit(1)
                .padding(.horizontal, 12)
                .frame(height: 32)
                .background(SimColors.tint, in: RoundedRectangle(cornerRadius: 9))
            }
        }
    }
}

/// A story card's caps title with a note on its right.
struct StoryHeader: View {
    let title: String
    let note: String
    @Environment(\.palette) private var palette

    var body: some View {
        HStack {
            Text(title).font(AppFont.sans(13, .semibold)).tracking(0.8).foregroundStyle(palette.muted).lineLimit(1)
            Spacer(minLength: 10)
            if !note.isEmpty { Text(note).font(AppFont.sans(13)).foregroundStyle(palette.muted).lineLimit(1) }
        }
    }
}

// MARK: - Greedy story card
// Coin Change, Fractional Knapsack and Job Sequencing: a header naming the ordering, then sections of
// tinted table rows, proportional blocks (coins, kilograms) and equal cells (a dp row, slots).

struct StoryBlock { let text: String; let weight: CGFloat; let tone: StoryTone }

/// `captionTone` colours the caption under the cell (Quickselect's "k"); idle leaves it muted.
struct StoryCell {
    let text: String
    let tone: StoryTone
    var caption: String? = nil
    var captionTone: StoryTone = .idle
    init(_ text: String, _ tone: StoryTone, caption: String? = nil, captionTone: StoryTone = .idle) {
        self.text = text; self.tone = tone; self.caption = caption; self.captionTone = captionTone
    }
}

struct StoryTableRow { let values: [String]; let tone: StoryTone }

/// Empty `headers` draws the rows alone. `aligns` defaults to every column at the leading edge.
struct StoryTable {
    let headers: [String]
    let weights: [CGFloat]
    let rows: [StoryTableRow]
    var aligns: [Alignment]? = nil
}

/// One labelled row of equal cells ("g1  1 3 8 12 17").
struct StoryGridLine { let label: String; let cells: [StoryCell] }

/// A pill in a call fan, with an optional line under it ("= 672").
struct StoryPill { let text: String; let tone: StoryTone; var caption: String? = nil }

/// One call and the calls it makes, a level deep: Karatsuba's three products under the full multiply.
struct StoryFan { let root: StoryPill; let children: [StoryPill] }

/// A block of the card under an optional caps `label`; exactly one of the content fields is set.
struct StorySection {
    var label: String? = nil
    var note: String? = nil
    var table: StoryTable? = nil
    var blocks: [StoryBlock]? = nil
    var cells: [StoryCell]? = nil
    var grid: [StoryGridLine]? = nil
    var fan: StoryFan? = nil
}

/// A size control in place of the card's title: "matrix size 4" with a − | + pill.
struct StoryStepper {
    let label: String
    let value: Int
    let canDecrease: Bool
    let canIncrease: Bool
    let onChange: (Int) -> Void
}

struct GreedyStory {
    let title: String
    let note: String
    let sections: [StorySection]
    /// In display order; an entry shows only while its tone is on screen.
    let legend: [(StoryTone, String)]
    let chips: [StoryChip]
    let headline: String
    let body: String

    var status: String { storyPlain(headline) + (body.isEmpty ? "" : " " + body) }

    var tones: Set<StoryTone> {
        var out = Set<StoryTone>()
        for s in sections {
            s.table?.rows.forEach { out.insert($0.tone) }
            s.blocks?.forEach { out.insert($0.tone) }
            s.cells?.forEach { out.insert($0.tone) }
            s.grid?.forEach { $0.cells.forEach { out.insert($0.tone) } }
            if let fan = s.fan { out.insert(fan.root.tone); fan.children.forEach { out.insert($0.tone) } }
        }
        return out
    }
}

struct GreedyStoryLab: View {
    let story: GreedyStory
    let playback: PlaybackState
    let captions: [String]
    var stepper: StoryStepper? = nil

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            LabCard {
                if let stepper { StoryStepperRow(stepper: stepper) } else { StoryHeader(title: story.title, note: story.note) }
                ForEach(story.sections.indices, id: \.self) { i in
                    let section = story.sections[i]
                    if let label = section.label {
                        StoryLabelRow(label: label, note: section.note).padding(.top, i == 0 ? 12 : 16)
                    }
                    let top: CGFloat = section.label == nil ? 12 : 0
                    if let table = section.table { StoryTableView(table: table).padding(.top, top) }
                    if let blocks = section.blocks { StoryBlocks(blocks: blocks).padding(.top, top) }
                    if let cells = section.cells { StoryCells(cells: cells).padding(.top, top) }
                    if let grid = section.grid { StoryGridLines(lines: grid).padding(.top, top) }
                    if let fan = section.fan { StoryFanView(fan: fan).padding(.top, top) }
                }
                let present = story.tones
                StoryLegendRow(items: story.legend.filter { present.contains($0.0) }.map { ($0.0.color, .fill, $0.1) })
                    .padding(.top, 14)
            }
            if !story.chips.isEmpty { StoryChips(chips: story.chips).padding(.top, 16) }
            LabStoryNarration(headline: story.headline, body: story.body).padding(.top, 16)
            PlaybackTransport(state: playback, captions: captions)
        }
    }
}

private struct StoryLabelRow: View {
    let label: String
    let note: String?
    @Environment(\.palette) private var palette

    var body: some View {
        HStack {
            Text(label).font(AppFont.sans(12, .semibold)).tracking(1).foregroundStyle(palette.muted)
            Spacer(minLength: 8)
            if let note { Text(note).font(AppFont.sans(13)).foregroundStyle(palette.muted).lineLimit(1) }
        }
        .padding(.bottom, 8)
    }
}

/// A tone as a solid tile: its fill and the text on it. Warn is tinted and outlined instead of filled.
func tileColors(_ tone: StoryTone, _ palette: Palette) -> (fill: Color, ink: Color) {
    switch tone {
    case .active: return (SimColors.active, Color(hex: 0x1F1A0A))
    case .done, .path, .answer: return (tone.color, .white)
    case .warn: return (SimColors.red.opacity(0.18), StoryTone.warn.ink(palette))
    case .idle: return (palette.muted.opacity(0.2), palette.onSurface)
    case .empty: return (.clear, palette.muted)
    }
}

struct StoryTile: ViewModifier {
    let tone: StoryTone
    let radius: CGFloat
    @Environment(\.palette) private var palette

    func body(content: Content) -> some View {
        let shape = RoundedRectangle(cornerRadius: radius)
        content
            .background(tileColors(tone, palette).fill, in: shape)
            .overlay {
                if tone == .warn { shape.strokeBorder(SimColors.red.opacity(0.8), lineWidth: 1.5) }
                else if tone == .empty { shape.strokeBorder(palette.muted.opacity(0.4), style: StrokeStyle(lineWidth: 1.5, dash: [4, 3])) }
            }
    }
}

/// Proportional blocks in one row: coins by value, a sack by kilograms.
private struct StoryBlocks: View {
    let blocks: [StoryBlock]
    @Environment(\.palette) private var palette

    var body: some View {
        GeometryReader { geo in
            let gap: CGFloat = 6
            let total = blocks.reduce(0) { $0 + $1.weight }
            let room = geo.size.width - gap * CGFloat(blocks.count - 1)
            HStack(spacing: gap) {
                ForEach(blocks.indices, id: \.self) { i in
                    let block = blocks[i]
                    Text(block.text)
                        .font(AppFont.mono(15, .bold))
                        .foregroundStyle(tileColors(block.tone, palette).ink)
                        .lineLimit(1)
                        .minimumScaleFactor(0.6)
                        .padding(.horizontal, 4)
                        .frame(width: max(room * block.weight / max(total, 1), 0), height: 46)
                        .modifier(StoryTile(tone: block.tone, radius: 10))
                }
            }
        }
        .frame(height: 46)
    }
}

/// Equal cells, each with an optional caption under it (a dp row's amounts).
private struct StoryCells: View {
    let cells: [StoryCell]
    @Environment(\.palette) private var palette

    var body: some View {
        HStack(alignment: .top, spacing: 6) {
            ForEach(cells.indices, id: \.self) { i in
                let cell = cells[i]
                VStack(spacing: 6) {
                    Text(cell.text)
                        .font(AppFont.mono(16, .bold))
                        .foregroundStyle(tileColors(cell.tone, palette).ink)
                        .lineLimit(1)
                        .frame(maxWidth: .infinity)
                        .frame(height: 44)
                        .modifier(StoryTile(tone: cell.tone, radius: 10))
                    if let caption = cell.caption {
                        Text(caption).font(AppFont.mono(12, cell.captionTone == .idle ? .regular : .bold))
                            .foregroundStyle(cell.captionTone == .idle ? palette.muted : cell.captionTone.ink(palette))
                    }
                }
            }
        }
    }
}

/// Column headers, then one tinted row per entry in its tone's colour.
private struct StoryTableView: View {
    let table: StoryTable
    @Environment(\.palette) private var palette

    var body: some View {
        let total = table.weights.reduce(0, +)
        let aligns = table.aligns ?? table.weights.map { _ in .leading }
        VStack(spacing: 6) {
            if !table.headers.isEmpty { GeometryReader { geo in
                HStack(spacing: 0) {
                    ForEach(table.headers.indices, id: \.self) { i in
                        Text(table.headers[i]).font(AppFont.sans(12, .semibold)).tracking(1).foregroundStyle(palette.muted).lineLimit(1)
                            .frame(width: (geo.size.width - 28) * table.weights[i] / total, alignment: .leading)
                    }
                }
                .padding(.horizontal, 14)
            }
            .frame(height: 16) }
            ForEach(table.rows.indices, id: \.self) { r in
                let row = table.rows[r]
                let tint = row.tone == .idle || row.tone == .empty ? palette.muted.opacity(0.12) : row.tone.color.opacity(palette.dark ? 0.18 : 0.14)
                GeometryReader { geo in
                    HStack(spacing: 0) {
                        ForEach(row.values.indices, id: \.self) { i in
                            Text(row.values[i]).font(AppFont.mono(15)).foregroundStyle(row.tone.ink(palette)).lineLimit(1)
                                .frame(width: (geo.size.width - 28) * table.weights[i] / total, alignment: aligns[i])
                        }
                    }
                    .padding(.horizontal, 14)
                    .frame(height: 40)
                }
                .frame(height: 40)
                .background(tint, in: RoundedRectangle(cornerRadius: 10))
            }
        }
    }
}

/// A lab's variant switch (Dijkstra / A*) as a full-width iOS-style segmented control.
struct LabSegments: View {
    let labels: [String]
    @Binding var selected: Int
    @Environment(\.palette) private var palette

    var body: some View {
        HStack(spacing: 0) {
            ForEach(labels.indices, id: \.self) { i in
                let on = i == selected
                Text(labels[i])
                    .font(AppFont.sans(14, on ? .semibold : .medium))
                    .foregroundStyle(palette.onSurface)
                    .lineLimit(1)
                    .frame(maxWidth: .infinity)
                    .frame(height: 30)
                    .background(on ? (palette.dark ? Color(hex: 0x636366) : .white) : .clear, in: RoundedRectangle(cornerRadius: 7))
                    .contentShape(Rectangle())
                    .onTapGesture { selected = i }
            }
        }
        .padding(2)
        .background(SimColors.tint, in: RoundedRectangle(cornerRadius: 9))
    }
}

struct StoryStepperRow: View {
    let stepper: StoryStepper
    @Environment(\.palette) private var palette

    var body: some View {
        HStack(spacing: 8) {
            Text(stepper.label).font(AppFont.sans(15)).foregroundStyle(palette.muted)
            Text("\(stepper.value)").font(AppFont.sans(17, .bold)).foregroundStyle(palette.onSurface)
            Spacer(minLength: 8)
            HStack(spacing: 0) {
                button("−", -1, stepper.canDecrease)
                Rectangle().fill(palette.muted.opacity(0.35)).frame(width: 1, height: 18)
                button("+", 1, stepper.canIncrease)
            }
            .background(SimColors.tint, in: RoundedRectangle(cornerRadius: 10))
        }
    }

    private func button(_ glyph: String, _ delta: Int, _ enabled: Bool) -> some View {
        Button { stepper.onChange(delta) } label: {
            Text(glyph).font(AppFont.sans(20, .medium)).foregroundStyle(palette.onSurface.opacity(enabled ? 1 : 0.3))
                .frame(width: 44, height: 36).contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .disabled(!enabled)
    }
}

/// Labelled rows of equal cells: a gutter label in mono, then the row.
private struct StoryGridLines: View {
    let lines: [StoryGridLine]
    @Environment(\.palette) private var palette

    var body: some View {
        VStack(spacing: 8) {
            ForEach(lines.indices, id: \.self) { l in
                HStack(spacing: 0) {
                    Text(lines[l].label).font(AppFont.mono(13)).foregroundStyle(palette.muted).frame(width: 34, alignment: .leading)
                    HStack(spacing: 6) {
                        ForEach(lines[l].cells.indices, id: \.self) { i in
                            let cell = lines[l].cells[i]
                            Text(cell.text).font(AppFont.mono(15, .bold)).foregroundStyle(tileColors(cell.tone, palette).ink).lineLimit(1)
                                .frame(maxWidth: .infinity).frame(height: 40)
                                .modifier(StoryTile(tone: cell.tone, radius: 9))
                        }
                    }
                }
            }
        }
    }
}

/// The fan: the call on top, its children spread evenly under it, an edge to each in the child's colour.
private struct StoryFanView: View {
    let fan: StoryFan
    @Environment(\.palette) private var palette

    var body: some View {
        VStack(spacing: 0) {
            StoryPillView(pill: fan.root)
            Canvas { ctx, size in
                let count = CGFloat(fan.children.count)
                for (i, child) in fan.children.enumerated() {
                    var path = Path()
                    path.move(to: CGPoint(x: size.width / 2, y: 0))
                    path.addLine(to: CGPoint(x: size.width * (CGFloat(i) + 0.5) / count, y: size.height))
                    let color: Color = child.tone == .done ? SimColors.green : child.tone == .active ? SimColors.blue : palette.muted.opacity(0.5)
                    ctx.stroke(path, with: .color(color), lineWidth: 1.5)
                }
            }
            .frame(height: 30)
            HStack(alignment: .top, spacing: 0) {
                ForEach(fan.children.indices, id: \.self) { i in
                    let child = fan.children[i]
                    VStack(spacing: 6) {
                        StoryPillView(pill: child)
                        if let caption = child.caption {
                            Text(caption).font(AppFont.mono(13, .bold)).foregroundStyle(child.tone == .idle ? palette.muted : child.tone.ink(palette))
                        }
                    }
                    .frame(maxWidth: .infinity)
                }
            }
        }
        .frame(maxWidth: .infinity)
    }
}

private struct StoryPillView: View {
    let pill: StoryPill
    @Environment(\.palette) private var palette

    var body: some View {
        Text(pill.text).font(AppFont.mono(14, .bold)).foregroundStyle(tileColors(pill.tone, palette).ink).lineLimit(1)
            .padding(.horizontal, 12).frame(height: 34)
            .modifier(StoryTile(tone: pill.tone, radius: 8))
    }
}

// MARK: - Sandbox controls
// The operation bar of the hands-on labs (Array, Linked List, Stack, Queue, Deque): a segmented op
// picker, an inline segmented option row ("End  Front | Back"), a value field with − / +, and a
// labelled action button in place of a bare play icon, so the button says what it will do.

/// "End" on the left, a compact segmented control on the right.
struct LabOptionRow: View {
    let label: String
    let options: [String]
    let selected: Int
    var enabled = true
    let onSelect: (Int) -> Void
    @Environment(\.palette) private var palette

    var body: some View {
        HStack(spacing: 12) {
            Text(label).font(AppFont.sans(16)).foregroundStyle(palette.onSurface)
            Spacer(minLength: 0)
            LabSegments(labels: options, selected: Binding(get: { selected }, set: { if enabled { onSelect($0) } }))
                .frame(maxWidth: CGFloat(options.count) * 76)
        }
    }
}

/// "Value 40" with a − | + pill; the number can also be typed.
struct LabValueStepper: View {
    let label: String
    @Binding var text: String
    var canDecrease = true
    var canIncrease = true
    var editable = true
    let onStep: (Int) -> Void
    @FocusState private var editing: Bool
    @Environment(\.palette) private var palette

    var body: some View {
        HStack(spacing: 10) {
            Text(label).font(AppFont.sans(15)).foregroundStyle(palette.muted).fixedSize()
            TextField("?", text: $text)
                .font(AppFont.mono(20, .bold))
                .foregroundStyle(palette.onSurface)
                .keyboardType(.numbersAndPunctuation)
                .focused($editing)
                .disabled(!editable)
                .frame(minWidth: 30)
                .fixedSize()
            Spacer(minLength: 0)
            HStack(spacing: 0) {
                button("minus", -1, canDecrease)
                Rectangle().fill(palette.muted.opacity(0.35)).frame(width: 1, height: 18)
                button("plus", 1, canIncrease)
            }
            .background(palette.muted.opacity(0.18), in: RoundedRectangle(cornerRadius: 10))
        }
        .padding(.leading, 14)
        .padding(.trailing, 8)
        .frame(height: 60)
        .background(SimColors.tint, in: RoundedRectangle(cornerRadius: 16))
    }

    private func button(_ icon: String, _ delta: Int, _ enabled: Bool) -> some View {
        Button { editing = false; onStep(delta) } label: {
            Image(systemName: icon).font(.system(size: 15, weight: .bold))
                .foregroundStyle(palette.onSurface.opacity(enabled ? 1 : 0.3))
                .frame(width: 38, height: 34).contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .disabled(!enabled)
    }
}

/// The run button, labelled with the operation it performs ("Enqueue →").
struct LabActionButton: View {
    let title: String
    var enabled = true
    /// Stretch across the row, for ops that take no value (Pop, Peek).
    var fill = false
    let action: () -> Void
    @Environment(\.palette) private var palette

    var body: some View {
        Button(action: action) {
            HStack(spacing: 8) {
                Text(title).font(AppFont.sans(18, .semibold)).lineLimit(1).minimumScaleFactor(0.8)
                Image(systemName: "arrow.right").font(.system(size: 15, weight: .bold))
            }
            .foregroundStyle(.white)
            .padding(.horizontal, 22)
            .frame(height: 60)
            .frame(minWidth: 0, maxWidth: fill ? .infinity : nil)
            .background(palette.primary.opacity(enabled ? 1 : 0.45), in: Capsule())
            .shadow(color: palette.primary.opacity(enabled ? 0.45 : 0), radius: 14, y: 4)
        }
        .buttonStyle(.plain)
        .accessibilityLabel(title)
    }
}
