import SwiftUI

// Ports of android/app/src/main/java/com/algora/app/core/ui/components/.

/// Back button, centred 17pt Space Grotesk title, hairline. Pinned above the scrolling body.
struct ScreenHeader<Trailing: View>: View {
    let title: String
    var onBack: (() -> Void)?
    @ViewBuilder var trailing: () -> Trailing
    @Environment(\.palette) private var palette
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        VStack(spacing: 0) {
            HStack(spacing: 0) {
                Button {
                    if let onBack { onBack() } else { dismiss() }
                } label: {
                    Image(systemName: "chevron.backward")
                        .font(.system(size: 18, weight: .semibold))
                        .foregroundStyle(palette.onSurface)
                        .frame(width: 44, height: 44)
                        .contentShape(Circle())
                }
                .accessibilityLabel("Back")
                Text(title)
                    .font(AppFont.grotesk(17, .bold))
                    .lineLimit(1)
                    .padding(.leading, 4)
                    .frame(maxWidth: .infinity, alignment: .leading)
                trailing()
                    .frame(minWidth: 44, minHeight: 44)
            }
            .padding(.leading, 8)
            .padding(.trailing, 14)
            .padding(.top, 8)
            .padding(.bottom, 12)
            .background(palette.background)
            Rectangle().fill(palette.outline).frame(height: 1)
        }
    }
}

extension ScreenHeader where Trailing == EmptyView {
    init(title: String, onBack: (() -> Void)? = nil) {
        self.init(title: title, onBack: onBack, trailing: { EmptyView() })
    }
}

/// Bordered surface card — the Surface(shape, border = outline) every screen repeats.
struct CardSurface: ViewModifier {
    var radius: CGFloat = 14
    var fill: Color?
    @Environment(\.palette) private var palette

    func body(content: Content) -> some View {
        content
            .background(fill ?? palette.surface, in: RoundedRectangle(cornerRadius: radius))
            .overlay(RoundedRectangle(cornerRadius: radius).stroke(palette.outline, lineWidth: 1))
    }
}

extension View {
    func card(radius: CGFloat = 14, fill: Color? = nil) -> some View {
        modifier(CardSurface(radius: radius, fill: fill))
    }
}

struct DifficultyBadge: View {
    let difficulty: Difficulty

    var body: some View {
        let (label, color): (String, Color) = switch difficulty {
        case .BEGINNER: ("Easy", SimColors.green)
        case .INTERMEDIATE: ("Medium", SimColors.amber)
        case .ADVANCED: ("Hard", SimColors.red)
        }
        Text(label)
            .font(AppFont.sans(10, .bold))
            .foregroundStyle(color)
            .lineLimit(1)
            .fixedSize()
            .padding(.horizontal, 7)
            .padding(.vertical, 3)
            .background(color.opacity(0.12), in: RoundedRectangle(cornerRadius: 6))
    }
}

/// Completion ring or check, title, optional difficulty, chevron.
struct TopicRow: View {
    let title: String
    let isCompleted: Bool
    var difficulty: Difficulty?
    let action: () -> Void
    @Environment(\.palette) private var palette

    var body: some View {
        Button(action: action) {
            HStack(spacing: 0) {
                if isCompleted {
                    Image(systemName: "checkmark.circle.fill")
                        .font(.system(size: 20))
                        .foregroundStyle(SimColors.green)
                        .frame(width: 22, height: 22)
                } else {
                    Circle().stroke(palette.outline, lineWidth: 2).frame(width: 22, height: 22)
                }
                Text(title)
                    .font(.bodyLarge)
                    .foregroundStyle(palette.onSurface)
                    .multilineTextAlignment(.leading)
                    .padding(.leading, 12)
                    .frame(maxWidth: .infinity, alignment: .leading)
                if let difficulty {
                    DifficultyBadge(difficulty: difficulty).padding(.trailing, 8)
                }
                Image(systemName: "chevron.right")
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(palette.muted)
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 13)
            .card()
        }
        .buttonStyle(.plain)
    }
}

/// 34pt gradient icon tile + category title.
struct SectionHeader: View {
    let title: String
    let iconName: String
    let accentColor: Color

    var body: some View {
        HStack(spacing: 11) {
            AppIcon(name: iconName, size: 18)
                .foregroundStyle(.white)
                .frame(width: 34, height: 34)
                .background(
                    LinearGradient(colors: [accentColor, accentColor.opacity(0.6)], startPoint: .topLeading, endPoint: .bottomTrailing),
                    in: RoundedRectangle(cornerRadius: 11)
                )
            Text(title).font(.titleMedium)
        }
    }
}

/// 7pt track, gradient fill from the accent to violet.
struct CategoryProgressBar: View {
    let progress: Double
    @Environment(\.palette) private var palette

    var body: some View {
        GeometryReader { geo in
            ZStack(alignment: .leading) {
                Capsule().fill(palette.outlineVariant)
                Capsule()
                    .fill(LinearGradient(colors: [palette.primary, Brand.violet], startPoint: .leading, endPoint: .trailing))
                    .frame(width: geo.size.width * min(max(progress, 0), 1))
            }
        }
        .frame(height: 7)
    }
}

struct SearchField: View {
    @Binding var query: String
    let placeholder: String
    @Environment(\.palette) private var palette

    var body: some View {
        HStack(spacing: 9) {
            Image(systemName: "magnifyingglass").foregroundStyle(palette.muted)
            TextField("", text: $query, prompt: Text(placeholder).foregroundStyle(palette.muted))
                .font(.bodyMedium)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
            if !query.isEmpty {
                Button { query = "" } label: {
                    Image(systemName: "xmark.circle.fill").foregroundStyle(palette.muted)
                }
            }
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 11)
        .card()
    }
}

/// A collapsible group header for long catalogs: tinted strip with an accent rail and a count pill.
struct AccordionHeader: View {
    let title: String
    let count: Int
    let accent: Color
    let isExpanded: Bool
    var subtitle: String?
    let action: () -> Void
    @Environment(\.palette) private var palette

    var body: some View {
        Button(action: action) {
            HStack(spacing: 10) {
                RoundedRectangle(cornerRadius: 2).fill(accent).frame(width: 3, height: 14)
                Text(title)
                    .font(AppFont.grotesk(13, .semibold))
                    .foregroundStyle(palette.onSurface)
                    .lineLimit(1)
                    .frame(maxWidth: .infinity, alignment: .leading)
                if let subtitle {
                    Text(subtitle).font(AppFont.sans(11)).foregroundStyle(palette.muted)
                }
                Text("\(count)")
                    .font(AppFont.grotesk(11, .bold))
                    .foregroundStyle(accent)
                    .padding(.horizontal, 7)
                    .padding(.vertical, 2)
                    .background(accent.opacity(0.16), in: RoundedRectangle(cornerRadius: 8))
                Image(systemName: "chevron.down")
                    .font(.system(size: 12, weight: .bold))
                    .foregroundStyle(accent.opacity(0.85))
                    .rotationEffect(.degrees(isExpanded ? 180 : 0))
            }
            .padding(.horizontal, 10)
            .padding(.vertical, 9)
            .background(accent.opacity(isExpanded ? 0.13 : 0.06), in: RoundedRectangle(cornerRadius: 12))
        }
        .buttonStyle(.plain)
        .animation(.easeInOut(duration: 0.2), value: isExpanded)
    }
}

/// Bold 20pt Space Grotesk section title used across Home, Practice and Progress.
struct SectionTitle: View {
    let text: String
    init(_ text: String) { self.text = text }

    var body: some View {
        Text(text)
            .font(AppFont.grotesk(20, .bold))
            .padding(.top, 22)
            .padding(.bottom, 12)
            .frame(maxWidth: .infinity, alignment: .leading)
    }
}

/// Small uppercase label above a card body.
struct Eyebrow: View {
    let text: String
    var color: Color?
    @Environment(\.palette) private var palette

    var body: some View {
        Text(text.uppercased())
            .font(AppFont.sans(11, .bold))
            .tracking(1.2)
            .foregroundStyle(color ?? palette.muted)
    }
}

/// Flow layout for chip rows (Compose FlowRow).
struct FlowLayout: Layout {
    var spacing: CGFloat = 8
    var lineSpacing: CGFloat = 8

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let rows = arrange(proposal.width ?? .infinity, subviews)
        let height = rows.map(\.height).reduce(0, +) + lineSpacing * CGFloat(max(rows.count - 1, 0))
        let width = rows.map(\.width).max() ?? 0
        return CGSize(width: proposal.width ?? width, height: height)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        var y = bounds.minY
        for row in arrange(bounds.width, subviews) {
            var x = bounds.minX
            for index in row.indices {
                let size = subviews[index].sizeThatFits(.unspecified)
                subviews[index].place(at: CGPoint(x: x, y: y), proposal: ProposedViewSize(size))
                x += size.width + spacing
            }
            y += row.height + lineSpacing
        }
    }

    private struct Row { var indices: [Int] = []; var width: CGFloat = 0; var height: CGFloat = 0 }

    private func arrange(_ maxWidth: CGFloat, _ subviews: Subviews) -> [Row] {
        var rows: [Row] = [Row()]
        for (index, subview) in subviews.enumerated() {
            let size = subview.sizeThatFits(.unspecified)
            if !rows[rows.count - 1].indices.isEmpty, rows[rows.count - 1].width + spacing + size.width > maxWidth {
                rows.append(Row())
            }
            var row = rows[rows.count - 1]
            row.width += (row.indices.isEmpty ? 0 : spacing) + size.width
            row.height = max(row.height, size.height)
            row.indices.append(index)
            rows[rows.count - 1] = row
        }
        return rows
    }
}

/// Filled accent button (Material Button).
struct PrimaryButton: View {
    let title: String
    var color: Color?
    var enabled = true
    let action: () -> Void
    @Environment(\.palette) private var palette

    var body: some View {
        Button(action: action) {
            Text(title)
                .font(AppFont.sans(15, .semibold))
                .foregroundStyle(.white)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 13)
                .background((color ?? palette.primary).opacity(enabled ? 1 : 0.35), in: Capsule())
        }
        .buttonStyle(.plain)
        .disabled(!enabled)
    }
}

/// Outlined button (Material OutlinedButton).
struct SecondaryButton: View {
    let title: String
    var color: Color?
    let action: () -> Void
    @Environment(\.palette) private var palette

    var body: some View {
        Button(action: action) {
            Text(title)
                .font(AppFont.sans(15, .semibold))
                .foregroundStyle(color ?? palette.primary)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 12)
                .overlay(Capsule().stroke(palette.outline, lineWidth: 1))
                .contentShape(Capsule())
        }
        .buttonStyle(.plain)
    }
}
