import SwiftUI

// Port of PerceptronSimulationSection.kt: AND / OR / XOR tabs, a stage with the four inputs on the plane
// and the half-plane w₁·x₁ + w₂·x₂ + b ≥ 0 shaded, a truth-table row, chips and a story headline, then a
// w₁ / w₂ / b picker with the value's stepper and Train Step, pinned in thumb reach when docked.

private struct Gate { let label: String; let targets: [Int] }

private let gates = [
    Gate(label: "AND", targets: [0, 0, 0, 1]),
    Gate(label: "OR", targets: [0, 1, 1, 1]),
    Gate(label: "XOR", targets: [0, 1, 1, 0]),
]

private let inputs = [(0, 0), (0, 1), (1, 0), (1, 1)]

private let oneColor = CategoryAccents.pink
private let zeroColor = SimColors.blue

// Wide enough that the (x,y) labels clear the edges.
private let axisLo = -0.36
private let axisHi = 1.36

private let startWeights = [1.0, 1.0, -1.5]
private let symbols = ["w₁", "w₂", "b"]

private func snap(_ v: Double) -> Double { (v * 10).rounded() / 10 }

private func pointName(_ i: Int) -> String { "(\(inputs[i].0),\(inputs[i].1))" }

private func joined(_ names: [String]) -> String {
    names.count <= 1 ? names.first ?? "" : names.dropLast().joined(separator: ", ") + " and " + names.last!
}

/// Shared by the page and the pinned controls, so both stay live.
@MainActor
@Observable
private final class PerceptronModel {
    var gateIndex = 0
    var w = startWeights
    /// The picker: which of w₁, w₂, b the stepper is on.
    var selected = 2

    var gate: Gate { gates[gateIndex] }
    var outputs: [Int] { inputs.map { w[0] * Double($0.0) + w[1] * Double($0.1) + w[2] >= 0 ? 1 : 0 } }
    var correct: Int { outputs.indices.filter { outputs[$0] == gate.targets[$0] }.count }
    var wrong: Set<Int> { Set(outputs.indices.filter { outputs[$0] != gate.targets[$0] }) }

    /// One perceptron-rule update on the first input it gets wrong: w += (target − output)·x, b += (target − output).
    func trainStep() {
        guard let i = outputs.indices.first(where: { outputs[$0] != gate.targets[$0] }) else { return }
        let e = Double(gate.targets[i] - outputs[i])
        let (x, y) = (Double(inputs[i].0), Double(inputs[i].1))
        w = [snap(min(max(w[0] + 0.5 * e * x, -3), 3)), snap(min(max(w[1] + 0.5 * e * y, -3), 3)), snap(min(max(w[2] + 0.5 * e, -3), 3))]
    }

    var story: (String, String) {
        let wrong = outputs.indices.filter { outputs[$0] != gate.targets[$0] }
        let xorBody = "No values of w₁, w₂ and b fix that, because one line cannot split XOR. A hidden layer can."
        if wrong.isEmpty {
            return ("{m:All 4 correct.} One straight line separates \(gate.label).",
                    "The shaded side outputs 1. Try XOR next: Train Step will never settle there.")
        }
        if wrong.count == 1 {
            let i = wrong[0]
            let with = inputs.indices.filter { $0 != i && outputs[$0] == outputs[i] }.map(pointName)
            let wants = inputs.indices.filter { $0 != i && gate.targets[$0] == gate.targets[i] }.map(pointName)
            let headline = !with.isEmpty && !wants.isEmpty
                ? "\(correct) of 4. {w:\(pointName(i))} lands with \(joined(with)), but \(gate.label) wants it with \(joined(wants))."
                : "\(correct) of 4. {w:\(pointName(i))} lands on the \(outputs[i]) side, but \(gate.label) wants \(gate.targets[i])."
            return (headline, gate.label == "XOR" ? xorBody : "Train Step moves the line toward it: w += (target − output)·x.")
        }
        return ("\(correct) of 4. {w:\(wrong.count) inputs} are on the wrong side.",
                gate.label == "XOR" ? xorBody : "Train Step fixes them one at a time: w += (target − output)·x.")
    }
}

struct PerceptronLab: View {
    @State private var model = PerceptronModel()
    @Environment(\.labDock) private var dock

    var body: some View {
        // The chip reads the score at the input in question: the first one wrong, else (1,1).
        let focus = model.outputs.indices.first { model.outputs[$0] != model.gate.targets[$0] } ?? 3
        let focusScore = model.w[0] * Double(inputs[focus].0) + model.w[1] * Double(inputs[focus].1) + model.w[2]
        let (headline, detail) = model.story
        VStack(alignment: .leading, spacing: 0) {
            LabIntro(text: "A perceptron draws one straight line, w₁·x₁ + w₂·x₂ + b = 0, and outputs 1 on the shaded side. Move the weights and bias until every input lands on the side its gate asks for.", bottom: 12)
            LabSegments(labels: gates.map(\.label), selected: $model.gateIndex)
            LabCard {
                PerceptronStage(model: model)
            }
            .padding(.top, 14)
            LabChips(chips: [
                model.correct == 4 ? LabChip(key: "correct", value: "4 / 4", good: true) : LabChip(key: "correct", value: "\(model.correct) / 4", tint: .warn),
                LabChip(key: "w·x+b at \(pointName(focus))", value: String(format: "%.1f", focusScore)),
            ]).padding(.top, 16)
            LabStoryNarration(headline: headline, body: detail).padding(.top, 16)
            if dock == nil {
                Divider().padding(.top, 16)
                PerceptronControls(model: model).padding(.top, 14)
            }
        }
        .onAppear {
            guard let dock else { return }
            let model = model
            dock.controls = { AnyView(PerceptronControls(model: model)) }
        }
        .onDisappear { dock?.controls = nil }
    }
}

private struct PerceptronStage: View {
    let model: PerceptronModel
    @Environment(\.palette) private var palette

    var body: some View {
        VStack(spacing: 12) {
            Canvas { ctx, size in draw(ctx, size) }
                .aspectRatio(1.65, contentMode: .fit)
                .clipShape(RoundedRectangle(cornerRadius: 14))
            HStack(spacing: 8) {
                ForEach(inputs.indices, id: \.self) { i in
                    let out = model.outputs[i]
                    let ok = out == model.gate.targets[i]
                    VStack(spacing: 4) {
                        Text("\(inputs[i].0) \(inputs[i].1) →").font(AppFont.mono(12)).foregroundStyle(palette.muted)
                        Text(ok ? "\(out) ✓" : "\(out) ✗").font(AppFont.mono(15, .semibold)).foregroundStyle(ok ? StoryTone.done.ink(palette) : StoryTone.warn.ink(palette))
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 10)
                    .background(ok ? SimColors.tint : SimColors.red.opacity(0.18), in: RoundedRectangle(cornerRadius: 10))
                }
            }
        }
    }

    private func draw(_ ctx: GraphicsContext, _ size: CGSize) {
        let span = axisHi - axisLo
        func px(_ x: Double) -> CGFloat { CGFloat((x - axisLo) / span) * size.width }
        func py(_ y: Double) -> CGFloat { size.height - CGFloat((y - axisLo) / span) * size.height }
        let (w1, w2, b) = (model.w[0], model.w[1], model.w[2])

        // The whole plane is the 0 side; the half-plane where the sum is ≥ 0 is repainted as the 1 side.
        ctx.fill(Path(CGRect(origin: .zero, size: size)), with: .color(zeroColor.opacity(0.16)))
        let plane = linearHalfPlane(w1, w2, b, size: size, x: axisLo...axisHi, y: axisLo...axisHi)
        if let region = plane.region { ctx.fill(region, with: .color(oneColor.opacity(0.16))) }

        ctx.line(CGPoint(x: px(0), y: 0), CGPoint(x: px(0), y: size.height), color: palette.outline, width: 1)
        ctx.line(CGPoint(x: 0, y: py(0)), CGPoint(x: size.width, y: py(0)), color: palette.outline, width: 1)

        if case let (a, c)? = plane.line { ctx.line(a, c, color: palette.primary, width: 3) }

        let r: CGFloat = 11
        for (i, (x, y)) in inputs.enumerated() {
            let c = CGPoint(x: px(Double(x)), y: py(Double(y)))
            let dot = Path(ellipseIn: CGRect(x: c.x - r, y: c.y - r, width: r * 2, height: r * 2))
            ctx.fill(dot, with: .color(model.gate.targets[i] == 1 ? oneColor : zeroColor))
            ctx.stroke(dot, with: .color(palette.surface), lineWidth: 2)
            if model.wrong.contains(i) {
                let halo = Path(ellipseIn: CGRect(x: c.x - r - 5, y: c.y - r - 5, width: (r + 5) * 2, height: (r + 5) * 2))
                ctx.stroke(halo, with: .color(SimColors.red), lineWidth: 2.5)
            }
            let gap = r + 5
            ctx.label("(\(x),\(y))", at: CGPoint(x: c.x, y: y == 1 ? c.y - gap : c.y + gap),
                      font: AppFont.mono(11), color: palette.muted, anchor: y == 1 ? .bottom : .top)
        }
    }
}

private struct PerceptronControls: View {
    @Bindable var model: PerceptronModel

    var body: some View {
        let names = ["Weight", "Weight", "Bias"]
        LabParamActionControls(
            params: names.indices.map { i in
                let v = model.w[i]
                return LabParam(tab: "\(names[i]) \(symbols[i])", name: names[i], symbol: symbols[i], text: String(format: "%.1f", v),
                                canDecrease: v > -3 + 1e-4, canIncrease: v < 3 - 1e-4)
            },
            selected: $model.selected,
            onStep: { i, delta in model.w[i] = snap(min(max(model.w[i] + Double(delta) * 0.1, -3), 3)) },
            action: "Train Step"
        ) { model.trainStep() }
    }
}
