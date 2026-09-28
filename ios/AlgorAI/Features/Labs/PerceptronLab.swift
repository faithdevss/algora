import SwiftUI

// Port of PerceptronSimulationSection.kt. Parameter lab (screen 4e): AND / OR / XOR tabs, a stage
// with the four inputs on the plane and the half-plane w₁·x₁ + w₂·x₂ + b ≥ 0 shaded, a truth-table
// row, a verdict, then the three sliders. Docked, the sliders pin to the bottom in thumb reach; on
// the topic page they sit under a divider in the card.

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

/// Shared by the page and the pinned controls, so both stay live.
@MainActor
@Observable
private final class PerceptronModel {
    var gateIndex = 0
    var w1 = 1.0
    var w2 = 1.0
    var bias = -1.5

    var gate: Gate { gates[gateIndex] }
    var outputs: [Int] { inputs.map { w1 * Double($0.0) + w2 * Double($0.1) + bias >= 0 ? 1 : 0 } }
    var correct: Int { outputs.indices.filter { outputs[$0] == gate.targets[$0] }.count }
}

struct PerceptronLab: View {
    @State private var model = PerceptronModel()
    @Environment(\.labDock) private var dock

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            LabIntro(text: "A perceptron draws one straight line, w₁·x₁ + w₂·x₂ + b = 0, and outputs 1 on the shaded side. Move the weights and bias until every input lands on the side its gate asks for.", bottom: 12)
            ChipPicker(options: gates.indices.map { ($0, gates[$0].label) }, selection: $model.gateIndex)
            LabCard {
                PerceptronStage(model: model)
                if dock == nil {
                    PerceptronVerdict(model: model).padding(.top, 16)
                    Divider().padding(.top, 16)
                    PerceptronControls(model: model).padding(.top, 14)
                }
            }
            .padding(.top, 14)
            if dock != nil {
                PerceptronVerdict(model: model).padding(.horizontal, 4).padding(.top, 18)
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
                        Text(ok ? "\(out) ✓" : "\(out) ✗").font(AppFont.mono(15, .semibold)).foregroundStyle(ok ? SimColors.green : SimColors.red)
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 10)
                    .background(SimColors.tint.opacity(0.18), in: RoundedRectangle(cornerRadius: 10))
                }
            }
        }
    }

    private func draw(_ ctx: GraphicsContext, _ size: CGSize) {
        let span = axisHi - axisLo
        func px(_ x: Double) -> CGFloat { CGFloat((x - axisLo) / span) * size.width }
        func py(_ y: Double) -> CGFloat { size.height - CGFloat((y - axisLo) / span) * size.height }
        let (w1, w2, b) = (model.w1, model.w2, model.bias)

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
            let gap = r + 5
            ctx.label("(\(x),\(y))", at: CGPoint(x: c.x, y: y == 1 ? c.y - gap : c.y + gap),
                      font: AppFont.mono(11), color: palette.muted, anchor: y == 1 ? .bottom : .top)
        }
    }
}

private struct PerceptronVerdict: View {
    let model: PerceptronModel
    @Environment(\.palette) private var palette

    var body: some View {
        let label = model.gate.label
        let solved = model.correct == 4
        let lead = Text(solved ? "All 4 correct." : "\(model.correct) of 4 correct.")
            .foregroundColor(solved ? SimColors.green : palette.onSurface)
        let rest: String = if solved {
            " One straight line separates \(label)."
        } else if label == "XOR" {
            " No straight line separates XOR."
        } else {
            " Move the line until every cell shows ✓."
        }
        let detail: String = if label == "XOR" {
            "One perceptron tops out at 3 of 4 here. That gap is why networks add a hidden layer."
        } else if solved {
            "Try XOR next: no setting of these sliders will work."
        } else {
            "The shaded side outputs 1. Bias slides the line; the weights tilt it."
        }
        VStack(alignment: .leading, spacing: 8) {
            (lead + Text(rest).foregroundColor(palette.onSurface))
                .font(AppFont.sans(19, .semibold))
                .fixedSize(horizontal: false, vertical: true)
            Text(detail)
                .font(AppFont.sans(15))
                .foregroundStyle(palette.muted)
                .fixedSize(horizontal: false, vertical: true)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

private struct PerceptronControls: View {
    @Bindable var model: PerceptronModel

    var body: some View {
        VStack(spacing: 14) {
            LabParamSlider(name: "Weight", symbol: "w₁", value: $model.w1, range: -2...2, step: 0.1)
            LabParamSlider(name: "Weight", symbol: "w₂", value: $model.w2, range: -2...2, step: 0.1)
            LabParamSlider(name: "Bias", symbol: "b", value: $model.bias, range: -3...3, step: 0.1)
        }
    }
}
