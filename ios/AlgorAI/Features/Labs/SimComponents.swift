import SwiftUI

// Port of feature/topics/SimComponents.kt: the chrome every lab shares, restyled to
// docs/ios-design/Simulations iOS.html — stage, readout chips, narration, then transport controls.

/// Height shared by lab buttons and inputs, so a row of them lines up (the 44pt touch target).
let simControlHeight: CGFloat = 44

/// One action in a SimOpRow. An empty label makes the button icon-only.
struct SimOp: Identifiable {
    let id = UUID()
    var label: String
    var icon: String
    var color: Color
    var weight: CGFloat = 1
    var enabled = true
    var action: () -> Void
}

/// Icon-and-label action row. Tinted rather than filled, so no one button shouts and a disabled one
/// still reads as disabled.
struct SimOpRow: View {
    let ops: [SimOp]

    var body: some View {
        GeometryReader { geo in
            let spacing: CGFloat = 8
            let totalWeight = ops.reduce(0) { $0 + $1.weight }
            let width = geo.size.width - spacing * CGFloat(max(ops.count - 1, 0))
            HStack(spacing: spacing) {
                ForEach(ops) { op in
                    Button(action: op.action) {
                        HStack(spacing: 6) {
                            if !op.icon.isEmpty {
                                Image(systemName: op.icon).font(.system(size: 15, weight: .semibold))
                            }
                            if !op.label.isEmpty {
                                Text(op.label).font(AppFont.sans(15, .semibold)).lineLimit(1).minimumScaleFactor(0.7)
                            }
                        }
                        .foregroundStyle(op.color)
                        .frame(width: width * op.weight / max(totalWeight, 1), height: simControlHeight)
                        .background(op.color.opacity(0.16), in: RoundedRectangle(cornerRadius: 12))
                        .opacity(op.enabled ? 1 : 0.4)
                    }
                    .buttonStyle(.plain)
                    .disabled(!op.enabled)
                    .accessibilityLabel(op.label.isEmpty ? op.icon : op.label)
                }
            }
        }
        .frame(height: simControlHeight)
    }
}

/// Text-only equally weighted action row (the older SimButtonRow).
struct SimButtonRow: View {
    let buttons: [(String, Color, () -> Void)]

    var body: some View {
        SimOpRow(ops: buttons.map { label, color, action in SimOp(label: label, icon: "", color: color, action: action) })
    }
}

/// Compact numeric input drawn as an editable token: tinted fill with an accent underline.
struct SimNumberField: View {
    @Binding var value: String
    let label: String
    @Environment(\.palette) private var palette

    var body: some View {
        HStack(spacing: 8) {
            Text(label).font(AppFont.sans(13)).foregroundStyle(palette.muted)
            TextField("", text: $value)
                .font(AppFont.mono(17, .semibold))
                .keyboardType(.numbersAndPunctuation)
                .autocorrectionDisabled()
                .textInputAutocapitalization(.never)
        }
        .padding(.horizontal, 12)
        .frame(height: simControlHeight)
        .background(SimColors.tint, in: RoundedRectangle(cornerRadius: 10))
        .overlay(alignment: .bottom) {
            Rectangle().fill(palette.primary).frame(height: 2).padding(.horizontal, 6)
        }
        .clipShape(RoundedRectangle(cornerRadius: 10))
    }
}

/// Fill for a value chip.
struct SimChip {
    let top: Color
    let bottom: Color

    static let violet = SimChip(top: SimColors.violet, bottom: Color(hex: 0x6D28D9))
    static let amber = SimChip(top: SimColors.active, bottom: SimColors.amber)
    static let blue = SimChip(top: Color(hex: 0x60A5FA), bottom: Color(hex: 0x2563EB))
    static let green = SimChip(top: Color(hex: 0x22C55E), bottom: Color(hex: 0x15803D))
}

struct SimValueChip: View {
    let value: String
    let chip: SimChip
    var width: CGFloat = 52
    var height: CGFloat = 52

    var body: some View {
        Text(value)
            .font(AppFont.mono(17, .bold))
            .foregroundStyle(.white)
            .lineLimit(1)
            .minimumScaleFactor(0.5)
            .frame(width: width, height: height)
            .background(chip.bottom, in: RoundedRectangle(cornerRadius: 10))
    }
}

// MARK: - Dock

/// What a lab hands up to the screen hosting it: the transport (so the screen can pin it in thumb
/// reach) and the intro (so the screen can move it behind the (i) button). Only the sim-only screen
/// installs one; on the topic page the lab renders both inline.
@MainActor
@Observable
final class LabDock {
    var playback: PlaybackState?
    var captions: [String]?
    var intro: String?
    /// A sandbox lab's own controls (pick an op, run it), pinned where the transport would be. The
    /// lab keeps its state in an @Observable model, so the pinned copy stays live.
    var controls: (() -> AnyView)?
    /// A lab-level action for the nav bar (a sandbox's reset); it takes the bookmark's place.
    var navAction: LabNavAction?
    /// The transport's step label ("Pull 40 of 200") and track marks, when the lab sets them.
    var stepLabel: ((Int) -> String)?
    var marks: TrackMarks?
    /// The storyboard transport's labelled action per step ("Predict"); see `LabTransportBar`.
    var stepAction: ((Int) -> String)?
}

/// Steps flagged on the scrub track with a tick, and what a tick means ("explore"). `color` is the
/// tick and key colour: the active yellow by default, red for N-Queens' backtracks.
struct TrackMarks: Equatable {
    let steps: Set<Int>
    let label: String
    var color: Color = SimColors.active
}

struct LabNavAction {
    let icon: String
    let label: String
    /// Draw `label` as a text button ("Edit") instead of the icon.
    var text = false
    let action: () -> Void
}

private struct LabDockKey: EnvironmentKey {
    static let defaultValue: LabDock? = nil
}

extension EnvironmentValues {
    var labDock: LabDock? {
        get { self[LabDockKey.self] }
        set { self[LabDockKey.self] = newValue }
    }
}

/// A lab's one-paragraph intro: behind (i) when docked, a muted lead-in otherwise.
struct LabIntro: View {
    let text: String
    /// Space under the inline intro; nothing when docked.
    var bottom: CGFloat = 0
    @Environment(\.labDock) private var dock
    @Environment(\.palette) private var palette

    var body: some View {
        if let dock {
            Color.clear.frame(height: 0)
                .onAppear { dock.intro = text }
                .onChange(of: text) { _, new in dock.intro = new }
        } else {
            Text(text).font(.bodyMedium).foregroundStyle(palette.muted).padding(.bottom, bottom)
        }
    }
}

// MARK: - Playback

/// Every lab's per-step delay is stretched by this at 1×, so a step's narration can be read before the
/// next one lands. The rate buttons still scale from here.
private let playbackPace = 2.0

/// Step index + play/pause + speed for any precomputed-snapshot lab.
@MainActor
@Observable
final class PlaybackState {
    var stepCount: Int
    var index = 0
    var playing = false
    /// Base delay per step; `rate` scales it.
    var speedMs: Double
    var rate: Double = 1

    static let rates: [Double] = [1, 1.5, 2, 0.5]

    init(stepCount: Int, speedMs: Double = 700) {
        self.stepCount = stepCount
        self.speedMs = speedMs
    }

    var lastIndex: Int { max(stepCount - 1, 0) }
    var atEnd: Bool { index >= lastIndex }

    func reset() { playing = false; index = 0 }
    func stepBack() { playing = false; if index > 0 { index -= 1 } }
    func stepForward() { playing = false; if index < lastIndex { index += 1 } }
    func togglePlay() {
        if atEnd { index = 0 }
        playing.toggle()
    }
    func jump(to i: Int) { playing = false; index = min(max(i, 0), lastIndex) }
    func cycleRate() {
        let i = Self.rates.firstIndex(of: rate) ?? 0
        rate = Self.rates[(i + 1) % Self.rates.count]
    }

    /// Re-targets the transport at a new frame list (a config or input changed).
    func load(stepCount: Int) {
        self.stepCount = stepCount
        playing = false
        index = 0
    }
}

/// A lab's transport. Hands itself to the dock when the screen has one, otherwise draws inline.
/// `captions` (one per step) enables the scrub preview and the All steps sheet.
struct PlaybackTransport: View {
    let state: PlaybackState
    var captions: [String]? = nil
    var stepLabel: ((Int) -> String)? = nil
    var marks: TrackMarks? = nil
    var action: ((Int) -> String)? = nil
    @Environment(\.labDock) private var dock

    var body: some View {
        if let dock {
            Color.clear.frame(height: 0)
                .onAppear { hand(to: dock) }
                .onChange(of: ObjectIdentifier(state)) { _, _ in hand(to: dock) }
                .onChange(of: captions ?? []) { _, new in dock.captions = new.isEmpty ? nil : new }
                .onChange(of: marks) { _, new in dock.marks = new }
                .onDisappear {
                    if dock.playback === state {
                        dock.playback = nil; dock.captions = nil; dock.stepLabel = nil; dock.marks = nil; dock.stepAction = nil
                    }
                }
        } else {
            VStack(spacing: 0) {
                Divider().padding(.top, 16)
                LabTransportBar(state: state, captions: captions, stepLabel: stepLabel, marks: marks, action: action).padding(.top, 14)
            }
        }
    }

    private func hand(to dock: LabDock) {
        dock.playback = state
        dock.captions = captions
        dock.stepLabel = stepLabel
        dock.marks = marks
        dock.stepAction = action
    }
}

/// Controller 2c: a thick segmented step track with a 28pt thumb (drag previews each step), then
/// speed, back, a 56pt play, forward and reset.
struct LabTransportBar: View {
    @Bindable var state: PlaybackState
    var captions: [String]?
    var stepLabel: ((Int) -> String)? = nil
    /// A text action at the right of the step label, in place of All steps ("New Data").
    var trailing: (label: String, action: () -> Void)? = nil
    var marks: TrackMarks? = nil
    /// The storyboard transport: the step's labelled action ("Predict", "Score “great”") beside a square
    /// step-back button, in place of speed, play and reset. It advances one step; on the last it starts over.
    var action: ((Int) -> String)? = nil
    /// Drawn under the step label in place of the play or action row (a stepper that re-runs the story).
    var footer: (() -> AnyView)? = nil
    @State private var showSteps = false
    @Environment(\.palette) private var palette

    var body: some View {
        VStack(spacing: 14) {
            VStack(spacing: 8) {
                StepTrack(state: state, captions: captions, marks: marks?.steps ?? [], markColor: marks?.color ?? SimColors.active)
                HStack(spacing: 0) {
                    Text(stepLabel?(state.index) ?? "Step \(state.index + 1) of \(state.stepCount)").font(AppFont.sans(13)).foregroundStyle(palette.muted)
                    if let marks {
                        RoundedRectangle(cornerRadius: 1).fill(marks.color).frame(width: 3, height: 11).padding(.leading, 10)
                        Text(marks.label).font(AppFont.sans(13)).foregroundStyle(palette.muted).padding(.leading, 5)
                    }
                    Spacer()
                    if let trailing {
                        Button(trailing.label, action: trailing.action)
                            .font(AppFont.sans(13, .semibold))
                            .foregroundStyle(palette.primary)
                    } else if captions != nil {
                        Button("All steps") { showSteps = true }
                            .font(AppFont.sans(13, .semibold))
                            .foregroundStyle(palette.primary)
                    }
                }
            }
            if let footer {
                footer()
            } else if let action {
                LabBackActionRow(action: action(state.index), backEnabled: state.index > 0,
                                 onBack: { state.stepBack() },
                                 onAction: { if state.atEnd { state.reset() } else { state.stepForward() } })
            } else {
            HStack {
                Button { state.cycleRate() } label: {
                    Text(rateLabel(state.rate))
                        .font(AppFont.sans(14, .semibold))
                        .monospacedDigit()
                        .frame(minWidth: 44, minHeight: 32)
                        .background(SimColors.tint, in: Capsule())
                }
                .accessibilityLabel("Speed \(rateLabel(state.rate))")
                Spacer()
                glyph("backward.end.fill", "Step back", enabled: state.index > 0) { state.stepBack() }
                Spacer()
                Button { state.togglePlay() } label: {
                    Image(systemName: state.playing ? "pause.fill" : state.atEnd ? "arrow.counterclockwise" : "play.fill")
                        .font(.system(size: 22, weight: .bold))
                        .foregroundStyle(.white)
                        .frame(width: 56, height: 56)
                        .background(palette.primary, in: Circle())
                }
                .accessibilityLabel(state.playing ? "Pause" : state.atEnd ? "Replay" : "Play")
                Spacer()
                glyph("forward.end.fill", "Step forward", enabled: !state.atEnd) { state.stepForward() }
                Spacer()
                Button { state.reset() } label: {
                    Image(systemName: "arrow.counterclockwise")
                        .font(.system(size: 19, weight: .semibold))
                        .foregroundStyle(palette.muted)
                        .frame(width: 44, height: 44)
                }
                .accessibilityLabel("Reset")
            }
            .buttonStyle(.plain)
            .foregroundStyle(palette.onSurface)
            }
        }
        .sensoryFeedback(.selection, trigger: state.index)
        // Speed is read fresh each tick so a rate change takes effect live.
        .task(id: state.playing) {
            while state.playing {
                try? await Task.sleep(for: .milliseconds(Int(state.speedMs * playbackPace / state.rate)))
                guard !Task.isCancelled, state.playing else { return }
                if state.index < state.stepCount - 1 { state.index += 1 } else { state.playing = false }
            }
        }
        .sheet(isPresented: $showSteps) {
            if let captions { StepsSheet(state: state, captions: captions) }
        }
    }

    private func glyph(_ icon: String, _ label: String, enabled: Bool, _ action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: icon).font(.system(size: 24)).frame(width: 44, height: 44)
        }
        .disabled(!enabled)
        .opacity(enabled ? 1 : 0.35)
        .accessibilityLabel(label)
    }

    private func rateLabel(_ r: Double) -> String {
        (r == r.rounded() ? String(Int(r)) : String(r)) + "×"
    }
}

/// Segments (one per step) when they fit, a continuous bar when they don't. Drag to scrub.
private struct StepTrack: View {
    let state: PlaybackState
    let captions: [String]?
    var marks: Set<Int> = []
    var markColor: Color = SimColors.active
    @State private var dragging = false
    @Environment(\.palette) private var palette

    var body: some View {
        GeometryReader { geo in
            let n = max(state.stepCount, 1)
            let w = geo.size.width
            let segmented = CGFloat(n) * 6 <= w
            let thumbX = n == 1 ? w / 2 : (CGFloat(state.index) + 0.5) / CGFloat(n) * w
            ZStack(alignment: .leading) {
                if segmented {
                    HStack(spacing: n > 24 ? 2 : 4) {
                        ForEach(0..<n, id: \.self) { i in
                            RoundedRectangle(cornerRadius: 4).fill(fill(i))
                        }
                    }
                    .frame(height: 8)
                } else {
                    Capsule().fill(SimColors.tint).frame(height: 8)
                    Capsule().fill(palette.primary).frame(width: thumbX, height: 8)
                }
                // Flagged steps: a tick through the track at each one's centre.
                ForEach(marks.filter { $0 >= 0 && $0 < n }.sorted(), id: \.self) { i in
                    RoundedRectangle(cornerRadius: 1).fill(markColor).frame(width: 3, height: 12)
                        .position(x: (CGFloat(i) + 0.5) / CGFloat(n) * w, y: 14)
                }
                Circle()
                    .fill(.white)
                    .frame(width: 28, height: 28)
                    .shadow(color: .black.opacity(0.35), radius: 5, y: 3)
                    .scaleEffect(dragging ? 1.1 : 1)
                    .position(x: min(max(thumbX, 14), w - 14), y: 14)
                if dragging { bubble(thumbX: thumbX, width: w) }
            }
            .frame(height: 28)
            .contentShape(Rectangle())
            .gesture(
                DragGesture(minimumDistance: 0)
                    .onChanged { g in
                        dragging = true
                        let i = Int((g.location.x / max(w, 1)) * CGFloat(n))
                        if min(max(i, 0), n - 1) != state.index { state.jump(to: i) }
                    }
                    .onEnded { _ in dragging = false }
            )
            .animation(.easeOut(duration: 0.15), value: state.index)
        }
        .frame(height: 28)
        .accessibilityElement()
        .accessibilityLabel("Step")
        .accessibilityValue("\(state.index + 1) of \(state.stepCount)")
        .accessibilityAdjustableAction { direction in
            switch direction {
            case .increment: state.stepForward()
            case .decrement: state.stepBack()
            @unknown default: break
            }
        }
    }

    private func fill(_ i: Int) -> Color {
        i < state.index ? palette.primary : i == state.index ? palette.primary.opacity(0.55) : SimColors.tint
    }

    /// While dragging: the step number and its caption, floating above the thumb.
    private func bubble(thumbX: CGFloat, width: CGFloat) -> some View {
        let caption = captions.flatMap { state.index < $0.count ? LabCaption.headline($0[state.index]) : nil }
        let bubbleWidth = min(width, 260)
        return HStack(spacing: 8) {
            Text("\(state.index + 1)").font(AppFont.sans(14, .bold)).foregroundStyle(palette.primary)
            if let caption { Text(caption).font(AppFont.sans(14)).lineLimit(1) }
        }
        .padding(.horizontal, 12)
        .frame(height: 34)
        .background(palette.surface, in: RoundedRectangle(cornerRadius: 10))
        .overlay(RoundedRectangle(cornerRadius: 10).stroke(palette.outline, lineWidth: 1))
        .shadow(color: .black.opacity(0.3), radius: 9, y: 6)
        .frame(maxWidth: bubbleWidth)
        .fixedSize(horizontal: caption == nil, vertical: true)
        .position(x: min(max(thumbX, bubbleWidth / 2), width - bubbleWidth / 2), y: -26)
        .allowsHitTesting(false)
    }
}

/// Every caption as a readable transcript; tap one to jump to it. Medium detent, so the stage
/// stays visible above.
private struct StepsSheet: View {
    let state: PlaybackState
    let captions: [String]
    @Environment(\.dismiss) private var dismiss
    @Environment(\.palette) private var palette

    var body: some View {
        VStack(spacing: 0) {
            ZStack {
                Text("All steps").font(AppFont.sans(17, .semibold))
                HStack {
                    Spacer()
                    Button("Done") { dismiss() }.font(AppFont.sans(17, .semibold)).foregroundStyle(palette.primary)
                }
            }
            .padding(.horizontal, 16)
            .frame(height: 52)
            ScrollViewReader { proxy in
                ScrollView {
                    VStack(spacing: 0) {
                        ForEach(captions.indices, id: \.self) { i in row(i) }
                    }
                    .background(palette.surface, in: RoundedRectangle(cornerRadius: 12))
                    .clipShape(RoundedRectangle(cornerRadius: 12))
                    .padding(.horizontal, 16)
                    .padding(.bottom, 24)
                }
                .onAppear { proxy.scrollTo(state.index, anchor: .center) }
            }
        }
        .background(palette.background)
        .presentationDetents([.medium, .large])
        .presentationBackgroundInteraction(.enabled(upThrough: .medium))
        .presentationDragIndicator(.visible)
    }

    private func row(_ i: Int) -> some View {
        let current = i == state.index
        let seen = i <= state.index
        return Button { state.jump(to: i) } label: {
            HStack(alignment: .firstTextBaseline, spacing: 12) {
                Text("\(i + 1)")
                    .font(AppFont.sans(13, .bold))
                    .foregroundStyle(seen ? .white : palette.muted)
                    .frame(width: 24, height: 24)
                    .background(current ? palette.primary : seen ? palette.primary.opacity(0.35) : SimColors.tint, in: Circle())
                Text(captions[i])
                    .font(AppFont.sans(15))
                    .foregroundStyle(seen ? palette.onSurface : palette.muted)
                    .multilineTextAlignment(.leading)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
            .padding(.vertical, 12)
            .padding(.horizontal, 14)
            .background(current ? palette.primary.opacity(0.18) : .clear)
            .overlay(alignment: .bottom) {
                if i < captions.count - 1 { Rectangle().fill(palette.outline).frame(height: 0.5).padding(.leading, 50) }
            }
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .id(i)
    }
}

/// The docked transport: pinned under the scroll view on a bar material, in thumb reach.
struct LabDockBar: View {
    let dock: LabDock
    @Environment(\.palette) private var palette

    var body: some View {
        if let controls = dock.controls {
            controls()
                .padding(.horizontal, 16)
                .padding(.top, 14)
                .padding(.bottom, 8)
                .background(.bar)
                .overlay(alignment: .top) { Rectangle().fill(palette.outline).frame(height: 0.5) }
        } else if let playback = dock.playback {
            LabTransportBar(state: playback, captions: dock.captions, stepLabel: dock.stepLabel, marks: dock.marks, action: dock.stepAction)
                .padding(.horizontal, 20)
                .padding(.top, 14)
                .padding(.bottom, 8)
                .background(.bar)
                .overlay(alignment: .top) { Rectangle().fill(palette.outline).frame(height: 0.5) }
        }
    }
}

// MARK: - Narration

/// Narration: what happened (bold, white) over why it matters (muted). The first sentence is the
/// headline, the rest the explanation.
struct LabCaption: View {
    let text: String
    @Environment(\.palette) private var palette

    var body: some View {
        let (head, tail) = Self.split(text)
        VStack(alignment: .leading, spacing: 6) {
            Text(head).font(AppFont.sans(18, .semibold)).foregroundStyle(palette.onSurface)
            if let tail { Text(tail).font(AppFont.sans(15)).foregroundStyle(palette.muted) }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .fixedSize(horizontal: false, vertical: true)
    }

    static func headline(_ text: String) -> String { split(text).0 }

    /// Abbreviations whose full stop does not end a sentence.
    private static let abbreviations: Set<String> = ["e.g", "i.e", "vs", "cf", "approx", "etc", "Fig", "fig", "No", "no"]

    /// Offset just past the first real sentence end, or nil. A break needs the next sentence to start
    /// with a capital, a digit, a quote, a bracket or markdown — or, 20+ characters in, with a lowercase
    /// code name ("lo is a max-heap"). "0.867" never qualifies (no space), nor does "e.g. x".
    private static func sentenceEnd(_ text: String) -> Int? {
        let chars = Array(text)
        var i = 0
        while i < chars.count {
            defer { i += 1 }
            guard ".!?".contains(chars[i]), i + 1 < chars.count, chars[i + 1].isWhitespace else { continue }
            var j = i + 1
            while j < chars.count, chars[j].isWhitespace { j += 1 }
            guard j < chars.count else { return nil }
            var k = i
            while k > 0, !chars[k - 1].isWhitespace { k -= 1 }
            if abbreviations.contains(String(chars[k..<i])) { continue }
            let next = chars[j]
            if next.isUppercase || next.isNumber || "\"'([*`∞".contains(next) || (next.isLowercase && i >= 20) { return i + 1 }
        }
        return nil
    }

    /// Headline and explanation from one status line: the first sentence, then the rest (Android's
    /// LabCaptionText.split). With no sentence break it falls back to the first " — ". A headline over
    /// 140 characters breaks again at its first clause — a colon or a dash — and the tail joins the rest.
    static func split(_ text: String) -> (String, String?) {
        var head = text
        var rest: String?
        if let end = sentenceEnd(text) {
            head = String(text.prefix(end))
            let tail = text.dropFirst(end).trimmingCharacters(in: .whitespaces)
            rest = tail.isEmpty ? nil : tail
        } else if let r = text.range(of: " — "), text.distance(from: text.startIndex, to: r.lowerBound) >= 8 {
            head = String(text[..<r.lowerBound]) + "."
            let tail = text[r.upperBound...].trimmingCharacters(in: .whitespaces)
            rest = tail.isEmpty ? nil : tail.prefix(1).uppercased() + tail.dropFirst()
        }
        if head.count > 140 {
            let h = head as NSString
            let cuts = [": ", " — "].compactMap { mark -> (Int, String)? in
                let r = h.range(of: mark, range: NSRange(location: 40, length: h.length - 40))
                return r.location != NSNotFound && r.location <= 140 ? (r.location, mark) : nil
            }
            if let (at, mark) = cuts.min(by: { $0.0 < $1.0 }) {
                var tail = h.substring(from: at + (mark as NSString).length)
                tail = tail.prefix(1).uppercased() + tail.dropFirst()
                head = h.substring(to: at) + (mark == ": " ? ":" : ".")
                rest = [tail, rest].compactMap { $0 }.joined(separator: " ")
            }
        }
        return (head, rest)
    }
}

/// Status lines use **bold** for the claim a step proves and *italics* for a stressed word; both render
/// instead of showing their asterisks. Anything the parser rejects is shown as written.
func inlineMarkdown(_ text: String) -> AttributedString {
    (try? AttributedString(markdown: text, options: .init(interpretedSyntax: .inlineOnlyPreservingWhitespace))) ?? AttributedString(text)
}

/// Narration under a lab's card (docs/ios-design/Simulations iOS.html): the step's headline large and
/// bold, the explanation muted beneath it. `text` is split by `LabCaption.split`.
struct LabNarration: View {
    let text: String
    @Environment(\.palette) private var palette

    var body: some View {
        let (head, tail) = LabCaption.split(text)
        VStack(alignment: .leading, spacing: 8) {
            Text(inlineMarkdown(head)).font(AppFont.sans(20, .bold)).foregroundStyle(palette.onSurface)
            if let tail { Text(inlineMarkdown(tail)).font(AppFont.sans(15)).foregroundStyle(palette.muted).lineSpacing(3) }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .fixedSize(horizontal: false, vertical: true)
    }
}

/// A readout ("sum = 9 · best = 9") as mono value chips. Keys that name the answer get the accent.
struct ReadoutChips: View {
    /// (key, value) chips; a nil key shows the value alone ("y = 0.35x + 1.5").
    let parts: [(key: String?, value: String)]
    /// Chips to accent too: the lab's headline number.
    var accented: Set<Int> = []

    init(text: String, accented: Set<Int> = []) {
        self.parts = Self.parts(text)
        self.accented = accented
    }

    /// Chips drawn as a warning, in red (a nonzero unknown-token count).
    var warned: Set<Int> = []
    /// Chips drawn as good news, in green (cache hits).
    var positive: Set<Int> = []
    /// A lab-specific chip after the rest (a share bar), in the same flow.
    var trailing: AnyView? = nil

    init(parts: [(key: String?, value: String)], accented: Set<Int> = [], warned: Set<Int> = [], positive: Set<Int> = [], trailing: AnyView? = nil) {
        self.parts = parts
        self.accented = accented
        self.warned = warned
        self.positive = positive
        self.trailing = trailing
    }
    @Environment(\.palette) private var palette

    var body: some View {
        FlowLayout(spacing: 8, lineSpacing: 8) {
            ForEach(Array(parts.enumerated()), id: \.offset) { i, part in
                let warn = warned.contains(i)
                let good = positive.contains(i)
                let strong = accented.contains(i) || part.key.map { k in ["best", "answer", "result", "final"].contains { k.lowercased().hasPrefix($0) } } ?? false
                HStack(spacing: 8) {
                    if let key = part.key { Text(key).foregroundStyle(warn ? SimColors.red : good ? SimColors.green : strong ? palette.primary : palette.muted) }
                    Text(part.value).fontWeight(.semibold).foregroundStyle(warn ? SimColors.red : palette.onSurface)
                }
                .font(AppFont.mono(15))
                .lineLimit(1)
                .padding(.horizontal, 12)
                .frame(height: 32)
                .background(warn ? SimColors.red.opacity(0.18) : good ? SimColors.green.opacity(0.18) : strong ? palette.primary.opacity(0.22) : SimColors.tint, in: RoundedRectangle(cornerRadius: 9))
            }
            if let trailing { trailing }
        }
    }

    static func parts(_ text: String) -> [(key: String?, value: String)] {
        text.components(separatedBy: " · ").map { chunk in
            for sep in [" = ", ": "] {
                if let r = chunk.range(of: sep) {
                    let key = String(chunk[..<r.lowerBound])
                    if key.count <= 22 { return (key, String(chunk[r.upperBound...])) }
                }
            }
            return (nil, chunk)
        }
    }
}

/// The card every lab sits in: the stage.
struct LabCard<Content: View>: View {
    var padding: CGFloat = 16
    @ViewBuilder let content: () -> Content

    var body: some View {
        VStack(alignment: .leading, spacing: 0) { content() }
            .padding(padding)
            .frame(maxWidth: .infinity, alignment: .leading)
            .card(radius: 20)
    }
}

/// A small legend swatch + label.
struct LegendItem: View {
    let color: Color
    let label: String
    @Environment(\.palette) private var palette

    var body: some View {
        HStack(spacing: 6) {
            RoundedRectangle(cornerRadius: 3).fill(color).frame(width: 10, height: 10)
            Text(label).font(AppFont.sans(13)).foregroundStyle(palette.onSurface.opacity(0.75))
        }
    }
}

/// One size for every linked-list node in the app (the singly linked list sandbox, the doubly linked
/// list / skip list / LRU players), so moving between labs never rescales the same structure. A row
/// too long for the screen scrolls sideways instead of shrinking its nodes.
enum LinkedNodeSpec {
    static let width: CGFloat = 44
    static let height: CGFloat = 52
    static let radius: CGFloat = 10
    static let fontSize: CGFloat = 18
    /// The gap between nodes, where the pointer arrows are drawn.
    static let link: CGFloat = 22
}

/// "Weight w₁ ……… 1.0" over the slider. `symbol` is set in mono; the value snaps to `step`.
struct LabParamSlider: View {
    let name: String
    let symbol: String
    @Binding var value: Double
    let range: ClosedRange<Double>
    var step: Double? = nil
    var format: (Double) -> String = { String(format: "%.1f", $0) }
    @Environment(\.palette) private var palette

    var body: some View {
        VStack(spacing: 4) {
            HStack {
                (Text(name).font(AppFont.sans(16, .semibold))
                    + Text(symbol.isEmpty ? "" : " " + symbol).font(AppFont.mono(16)).foregroundColor(palette.muted))
                Spacer()
                Text(format(value)).font(AppFont.mono(16, .semibold)).foregroundStyle(palette.primary)
            }
            if let step {
                Slider(value: $value, in: range, step: step).tint(palette.primary)
            } else {
                Slider(value: $value, in: range).tint(palette.primary)
            }
        }
    }
}

/// A full-width 52pt button: accent-filled when `primary`, tinted otherwise.
struct LabButton: View {
    let label: String
    var primary = false
    let action: () -> Void
    @Environment(\.palette) private var palette

    var body: some View {
        Button(action: action) {
            Text(label)
                .font(AppFont.sans(17, .semibold))
                .foregroundStyle(primary ? .white : palette.onSurface)
                .frame(maxWidth: .infinity, minHeight: 52)
                .background(primary ? palette.primary : SimColors.tint, in: RoundedRectangle(cornerRadius: 14))
        }
        .buttonStyle(.plain)
    }
}

/// Selectable options inside a lab: an iOS segmented control when they fit on one line, a wrapping
/// row of capsules otherwise.
struct ChipPicker<T: Hashable>: View {
    let options: [(T, String)]
    @Binding var selection: T
    @Environment(\.palette) private var palette

    var body: some View {
        if options.count <= 4 && options.reduce(0, { $0 + $1.1.count }) <= 30 {
            HStack(spacing: 0) {
                ForEach(options, id: \.0) { value, label in
                    let selected = value == selection
                    Button { selection = value } label: {
                        Text(label)
                            .font(AppFont.sans(14, selected ? .semibold : .medium))
                            .lineLimit(1)
                            .minimumScaleFactor(0.8)
                            .frame(maxWidth: .infinity, minHeight: 30)
                            .background {
                                if selected {
                                    RoundedRectangle(cornerRadius: 7).fill(palette.dark ? Color(hex: 0x636366) : .white)
                                        .shadow(color: .black.opacity(0.12), radius: 2, y: 1)
                                }
                            }
                            .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(2)
            .background(SimColors.tint, in: RoundedRectangle(cornerRadius: 9))
            .animation(.easeOut(duration: 0.15), value: selection)
        } else {
            FlowLayout(spacing: 6, lineSpacing: 6) {
                ForEach(options, id: \.0) { value, label in
                    let selected = value == selection
                    Button { selection = value } label: {
                        Text(label)
                            .font(AppFont.sans(13, selected ? .semibold : .medium))
                            .foregroundStyle(selected ? .white : palette.onSurface)
                            .padding(.horizontal, 12)
                            .frame(minHeight: 32)
                            .background(selected ? palette.primary : SimColors.tint, in: Capsule())
                    }
                    .buttonStyle(.plain)
                }
            }
        }
    }
}

// MARK: - Notice

/// Why a sandbox op can't run (no free slot, nothing to remove, no value), or a heads-up about what
/// it will cost. Sits at the top of the controls, so it stays in view when the caption doesn't.
struct LabNotice: View {
    enum Kind { case blocked, info }
    let text: String
    var kind = Kind.blocked
    @Environment(\.palette) private var palette

    var body: some View {
        let color = kind == .blocked ? SimColors.red : palette.primary
        HStack(alignment: .top, spacing: 8) {
            Image(systemName: kind == .blocked ? "exclamationmark.triangle.fill" : "info.circle.fill")
                .font(.system(size: 14, weight: .semibold))
                .foregroundStyle(color)
                .padding(.top, 1)
            Text(text).font(AppFont.sans(14, .medium)).foregroundStyle(palette.onSurface)
                .fixedSize(horizontal: false, vertical: true)
            Spacer(minLength: 0)
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 10)
        .background(color.opacity(0.14), in: RoundedRectangle(cornerRadius: 12))
        .overlay(RoundedRectangle(cornerRadius: 12).stroke(color.opacity(0.45), lineWidth: 1))
        .accessibilityElement(children: .combine)
    }
}
