import SwiftUI

// Port of TokenizerLabSection.kt: the tokenization topics on one layout — the input with its split
// points, a divider naming the method, the tokens it produced with their ids, then readout chips,
// narration and the transport. The frames are built on Android from the real trainers and exported
// as tokenizer_labs.json (IosContentExportTest), so both platforms play the same numbers.

private enum TokKind: String, Decodable { case VOCAB, UNKNOWN, NEW, PAIR, DIM }
private enum TokLegendKind: String, Decodable { case SPLIT, VOCAB, UNKNOWN, NEW }

private struct TokChip: Decodable { let text: String; let sub: String?; let kind: TokKind }
private struct TokRow: Decodable { let label: String; let value: String }
private struct TokLegend: Decodable { let kind: TokLegendKind; let label: String }

private struct TokFrame: Decodable {
    let input: String
    /// Character offsets where a split point is drawn. A split at a space replaces the space.
    let splits: [Int]
    let method: String
    let tokensLabel: String
    let tokens: [TokChip]
    let rows: [TokRow]
    let readout: String
    let headline: String
    let highlight: String?
    let detail: String
}

private struct TokenizerLabConfig: Decodable {
    let intro: String
    let legend: [TokLegend]
    let frames: [TokFrame]
}

enum TokenizerLabData {
    fileprivate static let configs: [String: TokenizerLabConfig] = {
        guard let url = Bundle.main.url(forResource: "tokenizer_labs", withExtension: "json"),
              let data = try? Data(contentsOf: url),
              let decoded = try? JSONDecoder().decode([String: TokenizerLabConfig].self, from: data)
        else { return [:] }
        return decoded
    }()

    static var topicIds: Set<String> { Set(configs.keys) }
}

private let unknownRed = SimColors.red

struct TokenizerLab: View {
    private let config: TokenizerLabConfig?
    @State private var playback: PlaybackState
    @Environment(\.labDock) private var dock

    init(topicId: String) {
        let c = TokenizerLabData.configs[topicId]
        config = c
        _playback = State(initialValue: PlaybackState(stepCount: c?.frames.count ?? 1, speedMs: 1400))
    }

    var body: some View {
        if let config {
            let frame = config.frames[min(playback.index, config.frames.count - 1)]
            VStack(alignment: .leading, spacing: 0) {
                LabIntro(text: config.intro, bottom: 12)
                LabCard {
                    TokenizerStage(frame: frame)
                    TokenizerLegend(items: config.legend).padding(.top, 16)
                    if dock == nil {
                        TokenizerReadout(frame: frame).padding(.top, 16)
                        TokenizerNarration(frame: frame).padding(.top, 14)
                    }
                    PlaybackTransport(state: playback, captions: config.frames.map { "\($0.headline) \($0.detail)" })
                }
                if dock != nil {
                    TokenizerReadout(frame: frame).padding(.top, 14)
                    TokenizerNarration(frame: frame).padding(.horizontal, 4).padding(.top, 16)
                }
            }
        } else {
            SimulationComingSoonCard()
        }
    }
}

private struct SectionLabel: View {
    let text: String
    @Environment(\.palette) private var palette

    var body: some View {
        Text(text).font(AppFont.sans(13, .semibold)).tracking(0.8).foregroundStyle(palette.muted)
    }
}

/// A split point: a small yellow tab between two pieces of the input.
private struct SplitMark: View {
    var body: some View {
        Text("·")
            .font(AppFont.mono(16, .bold))
            .foregroundStyle(Color(hex: 0x1A1A1A))
            .frame(width: 12, height: 24)
            .background(SimColors.active, in: RoundedRectangle(cornerRadius: 4))
            .padding(.horizontal, 2)
    }
}

private struct TokenizerStage: View {
    let frame: TokFrame
    @Environment(\.palette) private var palette

    /// The input cut into runs at the split points; a split on a space swallows the space.
    private var runs: [String] {
        let chars = Array(frame.input)
        var out: [String] = []
        var start = 0
        for at in frame.splits.sorted() where at < chars.count {
            out.append(String(chars[start..<at]))
            start = chars[at] == " " ? at + 1 : at
        }
        out.append(String(chars[min(start, chars.count)...]))
        return out
    }

    var body: some View {
        let runs = runs
        VStack(alignment: .leading, spacing: 0) {
            SectionLabel(text: "INPUT")
            FlowLayout(spacing: 0, lineSpacing: 6) {
                ForEach(runs.indices, id: \.self) { i in
                    HStack(spacing: 0) {
                        Text(runs[i]).font(AppFont.mono(21)).foregroundStyle(palette.onSurface)
                        if i < runs.count - 1 { SplitMark() }
                    }
                }
            }
            .padding(.top, 8)
            HStack(spacing: 12) {
                Rectangle().fill(palette.outline).frame(height: 1)
                Text("\(frame.method)  ↓").font(AppFont.sans(14)).foregroundStyle(palette.muted).fixedSize()
                Rectangle().fill(palette.outline).frame(height: 1)
            }
            .padding(.vertical, 14)
            SectionLabel(text: frame.tokensLabel)
            FlowLayout(spacing: 10, lineSpacing: 10) {
                ForEach(frame.tokens.indices, id: \.self) { TokenChipView(chip: frame.tokens[$0]) }
            }
            .padding(.top, 10)
            if !frame.rows.isEmpty {
                VStack(alignment: .leading, spacing: 6) {
                    ForEach(frame.rows.indices, id: \.self) { i in
                        HStack(alignment: .firstTextBaseline, spacing: 10) {
                            Text(frame.rows[i].label).font(AppFont.sans(14)).foregroundStyle(palette.muted).frame(minWidth: 92, alignment: .leading)
                            Text(frame.rows[i].value).font(AppFont.mono(14)).foregroundStyle(palette.onSurface)
                        }
                    }
                }
                .padding(.top, 14)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

private struct TokenChipView: View {
    let chip: TokChip
    @Environment(\.palette) private var palette

    var body: some View {
        let (fill, border, text): (Color, Color?, Color) = switch chip.kind {
        case .VOCAB: (SimColors.answer.opacity(0.32), nil, palette.onSurface)
        case .UNKNOWN: (unknownRed.opacity(0.14), unknownRed, palette.onSurface)
        case .NEW: (SimColors.green, nil, .white)
        case .PAIR: (SimColors.tint, nil, palette.onSurface)
        case .DIM: (SimColors.tint.opacity(0.3), nil, palette.muted)
        }
        VStack(spacing: 6) {
            Text(chip.text)
                .font(AppFont.mono(17, .bold))
                .foregroundStyle(text)
                .padding(.horizontal, 14)
                .frame(minWidth: 40, minHeight: 44)
                .background(fill, in: RoundedRectangle(cornerRadius: 12))
                .overlay { if let border { RoundedRectangle(cornerRadius: 12).stroke(border, lineWidth: 2) } }
            if let sub = chip.sub {
                Text(sub).font(AppFont.mono(13)).foregroundStyle(chip.kind == .UNKNOWN ? unknownRed.opacity(0.85) : palette.muted)
            }
        }
    }
}

private struct TokenizerLegend: View {
    let items: [TokLegend]
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

    @ViewBuilder private func swatch(_ kind: TokLegendKind) -> some View {
        let shape = RoundedRectangle(cornerRadius: 3)
        switch kind {
        case .SPLIT: shape.fill(SimColors.active).frame(width: 10, height: 10)
        case .VOCAB: shape.fill(SimColors.answer).frame(width: 10, height: 10)
        case .UNKNOWN: shape.stroke(unknownRed, lineWidth: 2).frame(width: 10, height: 10)
        case .NEW: shape.fill(SimColors.green).frame(width: 10, height: 10)
        }
    }
}

private struct TokenizerReadout: View {
    let frame: TokFrame

    var body: some View {
        let parts = ReadoutChips.parts(frame.readout)
        // A nonzero unknown count is the frame's warning; it is drawn in red.
        ReadoutChips(parts: parts, warned: Set(parts.indices.filter { parts[$0].key == "unknown" && parts[$0].value != "0" }))
    }
}

private struct TokenizerNarration: View {
    let frame: TokFrame
    @Environment(\.palette) private var palette

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            headline
                .font(AppFont.sans(19, .semibold))
                .foregroundStyle(palette.onSurface)
                .fixedSize(horizontal: false, vertical: true)
            Text(frame.detail)
                .font(AppFont.sans(15))
                .foregroundStyle(palette.muted)
                .fixedSize(horizontal: false, vertical: true)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private var headline: Text {
        let h = frame.headline
        guard let hl = frame.highlight, let r = h.range(of: hl) else { return Text(h) }
        return Text(h[..<r.lowerBound]) + Text(h[r]).foregroundColor(palette.primary) + Text(h[r.upperBound...])
    }
}
