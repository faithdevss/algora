import SwiftUI

// Port of HashingVisualizerSection.kt: an array of slots plus a rule for which slot a key lands in,
// covering separate chaining, a bloom filter's bit array, and an LRU cache's recency order. The
// frames are built on Android and exported as hashing_labs.json (IosContentExportTest), so both
// platforms play the same script; this file only draws them.

private enum HashOpState: String, Decodable { case DONE, CURRENT, TODO }
private enum SlotLayout: String, Decodable { case Rows, Strip }
private enum HashLegendKind: String, Decodable { case CURRENT, FILLED, HIT, MISS }

private struct HashOp: Decodable { let text: String; let state: HashOpState; let result: String?; let good: Bool }
private struct HashLegend: Decodable { let kind: HashLegendKind; let label: String }

private struct HashFrame: Decodable {
    let slots: [[String]]
    let probed: Set<Int>
    let hit: Set<Int>
    let miss: Set<Int>
    /// Narration: the headline, followed by `detail` when set; otherwise split after its first sentence.
    let status: String
    let ops: [HashOp]
    /// A tag at the right of a slot row ("loaded", "hit"), keyed by slot index.
    let tags: [String: String]
    /// An entry that just left, drawn as a dashed ghost row under the slots.
    let evicted: String?
    let readout: String?
    /// The key this frame is about, tinted yellow wherever the headline names it.
    let key: String?
    let detail: String?
    /// More words tinted yellow in the headline (a bloom filter's bit numbers).
    let marks: [String]
    // Bloom filter only: the key being hashed, its hash values in order, and every key added so far.
    let probeKey: String?
    let probeHashes: [Int]
    let added: [String]

    var caption: String { detail.map { "\(status) \($0)" } ?? status }
}

private struct HashLabConfig: Decodable {
    let intro: String
    let layout: SlotLayout
    let slotLabels: [String]
    let opsLabel: String
    let legend: [HashLegend]
    let frames: [HashFrame]
}

private enum HashingLabData {
    static let configs: [String: HashLabConfig] = {
        guard let url = Bundle.main.url(forResource: "hashing_labs", withExtension: "json"),
              let data = try? Data(contentsOf: url),
              let decoded = try? JSONDecoder().decode([String: HashLabConfig].self, from: data)
        else { return [:] }
        return decoded
    }()
}

private let probedSlot = SimColors.active
private let hitSlot = SimColors.green
private let missSlot = SimColors.red
private let filledSlot = SimColors.blue

// Player layout: a stage card (operation strip, slots, legend), readout chips, narration, then the
// transport — pinned in thumb reach when docked.
struct HashingLab: View {
    private let config: HashLabConfig?
    @State private var playback: PlaybackState
    @Environment(\.labDock) private var dock

    init(topicId: String) {
        let c = HashingLabData.configs[topicId] ?? HashingLabData.configs["hash_table"]
        config = c
        _playback = State(initialValue: PlaybackState(stepCount: c?.frames.count ?? 1, speedMs: 900))
    }

    var body: some View {
        if let config {
            let frame = config.frames[min(playback.index, config.frames.count - 1)]
            VStack(alignment: .leading, spacing: 0) {
                LabIntro(text: config.intro, bottom: 12)
                LabCard {
                    let showOps = !frame.ops.isEmpty && !config.opsLabel.isEmpty
                    if showOps { OpStrip(label: config.opsLabel, ops: frame.ops) }
                    Group {
                        switch config.layout {
                        case .Rows:
                            VStack(spacing: 10) {
                                ForEach(frame.slots.indices, id: \.self) { i in
                                    SlotRow(label: config.slotLabels[safe: i] ?? "\(i)", entries: frame.slots[i],
                                            look: SlotLook(frame: frame, index: i), tag: frame.tags["\(i)"])
                                }
                                if let evicted = frame.evicted { GhostRow(text: evicted) }
                            }
                        case .Strip:
                            BloomStage(frame: frame, labels: config.slotLabels)
                        }
                    }
                    .padding(.top, showOps ? 18 : 0)
                    .animation(.easeOut(duration: 0.2), value: playback.index)
                    HashLegendRow(items: config.legend).padding(.top, 16)
                    if dock == nil {
                        HashReadout(frame: frame).padding(.top, 16)
                        HashNarration(frame: frame).padding(.top, 14)
                    }
                    PlaybackTransport(state: playback, captions: config.frames.map(\.caption))
                }
                if dock != nil {
                    HashReadout(frame: frame).padding(.top, 14)
                    HashNarration(frame: frame).padding(.horizontal, 4).padding(.top, 16)
                }
            }
        } else {
            SimulationComingSoonCard()
        }
    }
}

private extension Array {
    subscript(safe i: Int) -> Element? { indices.contains(i) ? self[i] : nil }
}

private struct SlotLook {
    let fill: Color
    let border: Color?
    let text: Color?

    init(frame: HashFrame, index: Int) {
        let filled = !frame.slots[index].isEmpty
        if frame.miss.contains(index) { (fill, border, text) = (missSlot.opacity(0.16), missSlot, nil) }
        else if frame.hit.contains(index) { (fill, border, text) = (hitSlot.opacity(0.2), hitSlot, nil) }
        else if frame.probed.contains(index) { (fill, border, text) = (probedSlot, nil, Color(hex: 0x1A1A1A)) }
        else if filled { (fill, border, text) = (filledSlot.opacity(0.28), filledSlot, nil) }
        else { (fill, border, text) = (SimColors.tint.opacity(0.35), nil, nil) }
    }
}

private struct OpStrip: View {
    let label: String
    let ops: [HashOp]
    @Environment(\.palette) private var palette

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(label).font(AppFont.sans(13, .semibold)).tracking(0.8).foregroundStyle(palette.muted)
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(alignment: .top, spacing: 6) {
                    ForEach(ops.indices, id: \.self) { i in
                        let op = ops[i]
                        VStack(spacing: 4) {
                            Text(op.text)
                                .font(AppFont.sans(17, .bold))
                                .foregroundStyle(op.state == .CURRENT ? Color(hex: 0x1A1A1A) : op.state == .DONE ? palette.onSurface : palette.muted.opacity(0.55))
                                .padding(.horizontal, 8)
                                .frame(minWidth: 36, minHeight: 40)
                                .background(op.state == .CURRENT ? SimColors.active : op.state == .DONE ? SimColors.tint : .clear,
                                            in: RoundedRectangle(cornerRadius: 9))
                            Text(op.result ?? " ")
                                .font(AppFont.mono(12, .semibold))
                                .foregroundStyle(op.good ? SimColors.green : SimColors.red)
                        }
                    }
                }
            }
        }
    }
}

private struct SlotLabel: View {
    let text: String
    @Environment(\.palette) private var palette

    var body: some View {
        Text(text).font(AppFont.mono(13, .bold)).foregroundStyle(palette.muted).frame(width: 52, alignment: .leading)
    }
}

private struct SlotRow: View {
    let label: String
    let entries: [String]
    let look: SlotLook
    let tag: String?
    @Environment(\.palette) private var palette

    var body: some View {
        let text = look.text ?? (entries.isEmpty ? palette.muted : palette.onSurface)
        HStack(spacing: 0) {
            SlotLabel(text: label)
            HStack {
                // Chained entries render as an "a → b" run, which is exactly the collision story.
                Text(entries.joined(separator: "  →  ")).font(AppFont.sans(18, .bold)).foregroundStyle(text).lineLimit(1)
                Spacer(minLength: 8)
                if let tag { Text(tag).font(AppFont.sans(15)).foregroundStyle(text.opacity(0.85)) }
            }
            .padding(.horizontal, 16)
            .frame(height: 48)
            .background(look.fill, in: RoundedRectangle(cornerRadius: 10))
            .overlay { if let border = look.border { RoundedRectangle(cornerRadius: 10).stroke(border, lineWidth: 1.5) } }
        }
    }
}

/// An entry that just left: a dashed red outline under the slots.
private struct GhostRow: View {
    let text: String

    var body: some View {
        HStack(spacing: 0) {
            SlotLabel(text: "")
            HStack {
                Text(text).font(AppFont.sans(18, .bold))
                Spacer()
                Text("evicted").font(AppFont.sans(15))
            }
            .foregroundStyle(missSlot.opacity(0.85))
            .padding(.horizontal, 16)
            .frame(height: 48)
            .background(missSlot.opacity(0.1), in: RoundedRectangle(cornerRadius: 10))
            .overlay(RoundedRectangle(cornerRadius: 10).stroke(missSlot.opacity(0.8), style: StrokeStyle(lineWidth: 1.5, dash: [5, 4])))
        }
    }
}

/// Bit-array view: the key being hashed and its hash values, the bits six to a row with their index
/// underneath, then every key added so far.
private struct BloomStage: View {
    let frame: HashFrame
    let labels: [String]
    @Environment(\.palette) private var palette

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            if let key = frame.probeKey {
                HStack(spacing: 0) {
                    Text("\"\(key)\"")
                        .font(AppFont.mono(19, .bold))
                        .foregroundStyle(palette.onSurface)
                        .padding(.horizontal, 16).padding(.vertical, 9)
                        .background(probedSlot.opacity(0.12), in: RoundedRectangle(cornerRadius: 10))
                        .overlay(RoundedRectangle(cornerRadius: 10).stroke(probedSlot, lineWidth: 2))
                    Text("→").font(AppFont.sans(22)).foregroundStyle(palette.muted).padding(.horizontal, 12)
                    ForEach(frame.probeHashes.indices, id: \.self) { i in
                        HStack(spacing: 8) {
                            Text("h" + String(Array("₁₂₃₄")[min(i, 3)])).font(AppFont.mono(16)).foregroundStyle(palette.muted)
                            Text("\(frame.probeHashes[i])").font(AppFont.mono(17, .bold)).foregroundStyle(probedSlot)
                        }
                        .padding(.horizontal, 12)
                        .frame(height: 40)
                        .background(SimColors.tint, in: RoundedRectangle(cornerRadius: 9))
                        .padding(.trailing, 8)
                    }
                }
                .padding(.bottom, 18)
            }
            VStack(spacing: 12) {
                ForEach(Array(stride(from: 0, to: frame.slots.count, by: 6)), id: \.self) { start in
                    HStack(spacing: 8) {
                        ForEach(start..<min(start + 6, frame.slots.count), id: \.self) { i in
                            let look = SlotLook(frame: frame, index: i)
                            let empty = frame.slots[i].isEmpty
                            VStack(spacing: 6) {
                                Text(empty ? "0" : "1")
                                    .font(AppFont.mono(22, .bold))
                                    .foregroundStyle(look.text ?? (empty ? palette.muted : palette.onSurface))
                                    .frame(maxWidth: .infinity, minHeight: 58)
                                    .background(look.fill, in: RoundedRectangle(cornerRadius: 12))
                                    .overlay { if let border = look.border { RoundedRectangle(cornerRadius: 12).stroke(border, lineWidth: 1.5) } }
                                Text(labels[safe: i] ?? "\(i)").font(AppFont.mono(13))
                                    .foregroundStyle(frame.probed.contains(i) ? probedSlot : palette.muted)
                            }
                        }
                    }
                }
            }
            if !frame.added.isEmpty {
                HStack(spacing: 8) {
                    Text("Added").font(AppFont.sans(15)).foregroundStyle(palette.muted).padding(.trailing, 2)
                    ForEach(frame.added, id: \.self) { key in
                        let current = key == frame.probeKey
                        Text(key).font(AppFont.mono(15))
                            .foregroundStyle(current ? probedSlot : palette.onSurface)
                            .padding(.horizontal, 12).padding(.vertical, 7)
                            .background(current ? probedSlot.opacity(0.18) : SimColors.tint, in: RoundedRectangle(cornerRadius: 8))
                    }
                }
                .padding(.top, 16)
            }
        }
    }
}

private struct HashLegendRow: View {
    let items: [HashLegend]
    @Environment(\.palette) private var palette

    var body: some View {
        FlowLayout(spacing: 16, lineSpacing: 6) {
            ForEach(items.indices, id: \.self) { i in
                HStack(spacing: 6) {
                    swatch(items[i].kind)
                    Text(items[i].label).font(AppFont.sans(14)).foregroundStyle(palette.onSurface.opacity(0.8))
                }
            }
        }
    }

    @ViewBuilder private func swatch(_ kind: HashLegendKind) -> some View {
        let shape = RoundedRectangle(cornerRadius: 3)
        switch kind {
        case .CURRENT: shape.fill(probedSlot).frame(width: 10, height: 10)
        case .FILLED: shape.fill(filledSlot).frame(width: 10, height: 10)
        case .HIT: shape.fill(hitSlot).frame(width: 10, height: 10)
        case .MISS: shape.stroke(missSlot, lineWidth: 2).frame(width: 10, height: 10)
        }
    }
}

private let goodKeys: Set<String> = ["hits", "found"]
private let badKeys: Set<String> = ["misses", "collisions"]

private struct HashReadout: View {
    let frame: HashFrame

    var body: some View {
        if let readout = frame.readout {
            let parts = ReadoutChips.parts(readout)
            let nonzero = { (i: Int) in parts[i].value.trimmingCharacters(in: .whitespaces) != "0" }
            ReadoutChips(parts: parts,
                         warned: Set(parts.indices.filter { badKeys.contains(parts[$0].key ?? "") && nonzero($0) }),
                         positive: Set(parts.indices.filter { goodKeys.contains(parts[$0].key ?? "") && nonzero($0) }))
        }
    }
}

private struct HashNarration: View {
    let frame: HashFrame
    @Environment(\.palette) private var palette

    var body: some View {
        let (head, tail): (String, String?) = frame.detail.map { (frame.status, $0) } ?? LabCaption.split(frame.status)
        VStack(alignment: .leading, spacing: 8) {
            headline(head)
                .font(AppFont.sans(19, .semibold))
                .foregroundStyle(palette.onSurface)
                .fixedSize(horizontal: false, vertical: true)
            if let tail {
                Text(tail).font(AppFont.sans(15)).foregroundStyle(palette.muted).fixedSize(horizontal: false, vertical: true)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    /// The key this frame is about, and any marked words, are tinted yellow in the headline.
    private func headline(_ h: String) -> Text {
        let words = Set([frame.key].compactMap { $0 } + frame.marks)
        guard !words.isEmpty else { return Text(h) }
        let tint = palette.dark ? SimColors.active : Color(hex: 0xB45309)
        var out = Text("")
        var word = ""
        func flush() {
            if !word.isEmpty { out = out + (words.contains(word) ? Text(word).foregroundColor(tint) : Text(word)); word = "" }
        }
        for ch in h {
            if ch.isLetter || ch.isNumber { word.append(ch) } else { flush(); out = out + Text(String(ch)) }
        }
        flush()
        return out
    }
}
