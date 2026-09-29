import SwiftUI

// Port of StoryControls.kt: pieces of the redesigned ML and deep-learning labs. Value chips that can
// carry a series dot or read as good news, and the parameter bar that replaces a stack of sliders with
// a picker and one stepper.

/// A value chip: the key muted, the value in its tone. `dot` leads with a series colour; `good` tints it
/// green; `tint` washes the chip in a state's colour and inks the key in it (the "MSE" or "τ" to watch).
struct LabChip {
    let key: String
    let value: String
    var tone: StoryTone = .idle
    var dot: Color? = nil
    var good = false
    var tint: StoryTone? = nil
    /// A tint colour outside the story tones (a class's pink); wins over `tint`.
    var tintColor: Color? = nil
}

struct LabChips: View {
    let chips: [LabChip]
    @Environment(\.palette) private var palette

    var body: some View {
        FlowLayout(spacing: 8, lineSpacing: 8) {
            ForEach(chips.indices, id: \.self) { i in
                let chip = chips[i]
                HStack(spacing: 8) {
                    if let dot = chip.dot { Circle().fill(dot).frame(width: 8, height: 8) }
                    Text(chip.key).foregroundStyle(chip.good ? StoryTone.done.ink(palette) : chip.tintColor ?? chip.tint?.ink(palette) ?? palette.muted)
                    Text(chip.value).fontWeight(.bold).foregroundStyle(chip.tone.ink(palette))
                }
                .font(AppFont.mono(15))
                .lineLimit(1)
                .padding(.horizontal, 12)
                .frame(height: 32)
                .background(chip.good ? SimColors.green.opacity(0.18) : chip.tintColor.map { $0.opacity(0.2) } ?? chip.tint.map { $0.color.opacity(0.2) } ?? SimColors.tint, in: RoundedRectangle(cornerRadius: 9))
            }
        }
    }
}

/// One tunable value: its picker label, its name and symbol on the stepper row, and the value as shown.
struct LabParam {
    let tab: String
    let name: String
    let symbol: String
    let text: String
    let canDecrease: Bool
    let canIncrease: Bool
}

/// A picker over `params` (only when there are several) above one stepper for the selected one.
struct LabParamControls: View {
    let params: [LabParam]
    @Binding var selected: Int
    let onStep: (_ index: Int, _ delta: Int) -> Void

    var body: some View {
        let index = min(max(selected, 0), params.count - 1)
        VStack(spacing: 14) {
            if params.count > 1 { LabSegments(labels: params.map(\.tab), selected: $selected) }
            LabParamStepper(param: params[index]) { onStep(index, $0) }
        }
    }
}

/// "Reach  log₁₀ γ ……… −0.8  [− | +]".
struct LabParamStepper: View {
    let param: LabParam
    let onStep: (Int) -> Void
    @Environment(\.palette) private var palette

    var body: some View {
        HStack(spacing: 8) {
            Text(param.name).font(AppFont.sans(17, .semibold)).foregroundStyle(palette.onSurface).lineLimit(1)
            if !param.symbol.isEmpty { Text(param.symbol).font(AppFont.mono(14)).foregroundStyle(palette.muted).lineLimit(1) }
            Spacer(minLength: 8)
            Text(param.text).font(AppFont.mono(18, .bold)).foregroundStyle(palette.primary).lineLimit(1).padding(.trailing, 6)
            StepperPill(param: param, onStep: onStep)
        }
    }

}

/// The − | + pill of a stepper.
private struct StepperPill: View {
    let param: LabParam
    let onStep: (Int) -> Void
    @Environment(\.palette) private var palette

    var body: some View {
        HStack(spacing: 0) {
            button("−", -1, param.canDecrease)
            Rectangle().fill(palette.muted.opacity(0.35)).frame(width: 1, height: 18)
            button("+", 1, param.canIncrease)
        }
        .background(SimColors.tint, in: RoundedRectangle(cornerRadius: 10))
    }

    private func button(_ glyph: String, _ delta: Int, _ enabled: Bool) -> some View {
        Button { onStep(delta) } label: {
            Text(glyph).font(AppFont.sans(20, .medium)).foregroundStyle(palette.onSurface.opacity(enabled ? 1 : 0.3))
                .frame(width: 44, height: 36).contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .disabled(!enabled)
    }
}

/// The picker over the parameters, then one row: the selected value, its − | + pill and the lab's action
/// ("0.35  [− | +]  [Show Best Fit]"). The picker names the parameter, so the row doesn't repeat it.
struct LabParamActionControls: View {
    let params: [LabParam]
    @Binding var selected: Int
    let onStep: (_ index: Int, _ delta: Int) -> Void
    let action: String
    let onAction: () -> Void
    @Environment(\.palette) private var palette

    var body: some View {
        let index = min(max(selected, 0), params.count - 1)
        VStack(spacing: 14) {
            if params.count > 1 { LabSegments(labels: params.map(\.tab), selected: $selected) }
            HStack(spacing: 0) {
                Text(params[index].text).font(AppFont.mono(18, .bold)).foregroundStyle(palette.primary).lineLimit(1)
                    .frame(minWidth: 52, alignment: .leading).padding(.trailing, 12)
                StepperPill(param: params[index]) { onStep(index, $0) }
                LabButton(label: action, primary: true, action: onAction).padding(.leading, 16)
            }
        }
    }
}

/// A square step-back button beside the lab's labelled primary action ("‹  [Next Example]").
struct LabBackActionRow: View {
    let action: String
    let backEnabled: Bool
    let onBack: () -> Void
    let onAction: () -> Void
    @Environment(\.palette) private var palette

    var body: some View {
        HStack(spacing: 12) {
            Button(action: onBack) {
                Image(systemName: "chevron.left")
                    .font(.system(size: 20, weight: .semibold))
                    .foregroundStyle(palette.onSurface.opacity(backEnabled ? 1 : 0.3))
                    .frame(width: 56, height: 52)
                    .background(SimColors.tint, in: RoundedRectangle(cornerRadius: 14))
            }
            .buttonStyle(.plain)
            .disabled(!backEnabled)
            .accessibilityLabel("Step back")
            LabButton(label: action, primary: true, action: onAction)
        }
    }
}
