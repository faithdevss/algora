import SwiftUI

// Ports of feature/interviewprep/behavioral/BehavioralScreen.kt and
// systemdesign/SystemDesignScreen.kt. The Systa cross-promo is Android-only.

struct BehavioralScreen: View {
    let bank: BehavioralBank
    let onComplete: () -> Void
    @Environment(\.palette) private var palette

    var body: some View {
        VStack(spacing: 0) {
            ScreenHeader(title: bank.title)
            ScrollView {
                LazyVStack(alignment: .leading, spacing: 0) {
                    Text(bank.description).font(.bodyMedium).foregroundStyle(palette.muted).padding(.vertical, 12)
                    PrimerCard(title: "Answer with STAR") {
                        ForEach([("S", "Situation", "the context, kept short"),
                                 ("T", "Task", "your specific responsibility"),
                                 ("A", "Action", "what you did — the bulk of the answer, in first person"),
                                 ("R", "Result", "the outcome, quantified where possible")], id: \.0) { letter, term, desc in
                            HStack(spacing: 10) {
                                Text(letter)
                                    .font(AppFont.sans(14, .bold)).foregroundStyle(palette.primary)
                                    .padding(.horizontal, 8).padding(.vertical, 3)
                                    .background(palette.primary.opacity(0.18), in: RoundedRectangle(cornerRadius: 6))
                                Text("\(term) — \(desc)").font(.bodyMedium).foregroundStyle(palette.muted)
                            }
                            .padding(.vertical, 3)
                        }
                    }
                    Text("Questions").font(.titleLarge).padding(.top, 18).padding(.bottom, 8)
                    ForEach(bank.questions, id: \.self) { ExpandableQuestion(question: $0).padding(.vertical, 5) }
                    PrimaryButton(title: "Mark as reviewed", color: SimColors.green, action: onComplete).padding(.vertical, 16)
                }
                .padding(.horizontal, 16)
            }
        }
    }
}

private struct ExpandableQuestion: View {
    let question: BehavioralQuestion
    @State private var expanded = false
    @Environment(\.palette) private var palette

    var body: some View {
        Button { withAnimation(.easeInOut(duration: 0.2)) { expanded.toggle() } } label: {
            VStack(alignment: .leading, spacing: 0) {
                HStack {
                    Pill(text: question.category)
                    Spacer()
                    Image(systemName: "chevron.down").foregroundStyle(palette.muted).rotationEffect(.degrees(expanded ? 180 : 0))
                }
                Text(question.prompt).font(.titleMedium).multilineTextAlignment(.leading).padding(.top, 10)
                if expanded {
                    GuidanceBlock(label: "What they're assessing", text: question.assesses, accent: SimColors.blue).padding(.top, 12)
                    GuidanceBlock(label: "How to frame it", text: question.starTip, accent: SimColors.green).padding(.top, 10)
                }
            }
            .padding(15)
            .frame(maxWidth: .infinity, alignment: .leading)
            .card()
        }
        .buttonStyle(.plain)
    }
}

struct SystemDesignScreen: View {
    let primer: SystemDesignPrimer
    let onComplete: () -> Void
    @Environment(\.palette) private var palette

    var body: some View {
        VStack(spacing: 0) {
            ScreenHeader(title: primer.title)
            ScrollView {
                LazyVStack(alignment: .leading, spacing: 0) {
                    Text(primer.description).font(.bodyMedium).foregroundStyle(palette.muted).padding(.vertical, 12)
                    PrimerCard(title: "Drive the round") {
                        ForEach(Array(primer.framework.enumerated()), id: \.offset) { i, step in
                            HStack(alignment: .top, spacing: 12) {
                                Text("\(i + 1)")
                                    .font(AppFont.sans(14, .bold)).foregroundStyle(palette.primary)
                                    .padding(.horizontal, 10).padding(.vertical, 3)
                                    .background(palette.primary.opacity(0.18), in: Capsule())
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(step.title).font(AppFont.sans(16, .semibold))
                                    Text(step.detail).font(.bodyMedium).foregroundStyle(palette.muted)
                                }
                            }
                            .padding(.vertical, 5)
                        }
                    }
                    Text("Building Blocks").font(.titleLarge).padding(.top, 18).padding(.bottom, 8)
                    ForEach(primer.concepts, id: \.self) { ExpandableConcept(concept: $0).padding(.vertical, 5) }
                    PrimaryButton(title: "Mark as reviewed", color: SimColors.green, action: onComplete).padding(.vertical, 16)
                }
                .padding(.horizontal, 16)
            }
        }
    }
}

private struct ExpandableConcept: View {
    let concept: SystemDesignConcept
    @State private var expanded = false
    @Environment(\.palette) private var palette

    var body: some View {
        Button { withAnimation(.easeInOut(duration: 0.2)) { expanded.toggle() } } label: {
            VStack(alignment: .leading, spacing: 0) {
                HStack(spacing: 8) {
                    Text(concept.name).font(.titleMedium).multilineTextAlignment(.leading)
                    Spacer()
                    Pill(text: concept.category)
                    Image(systemName: "chevron.down").foregroundStyle(palette.muted).rotationEffect(.degrees(expanded ? 180 : 0))
                }
                Text(concept.summary).font(.bodyMedium).foregroundStyle(palette.muted).multilineTextAlignment(.leading).padding(.top, 8)
                if expanded {
                    GuidanceBlock(label: "When to use it", text: concept.whenToUse, accent: SimColors.green).padding(.top, 10)
                }
            }
            .padding(15)
            .frame(maxWidth: .infinity, alignment: .leading)
            .card()
        }
        .buttonStyle(.plain)
    }
}

private struct PrimerCard<Content: View>: View {
    let title: String
    @ViewBuilder let content: () -> Content
    @Environment(\.palette) private var palette

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text(title).font(AppFont.grotesk(18, .bold)).padding(.bottom, 8)
            content()
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(palette.primary.opacity(0.08), in: RoundedRectangle(cornerRadius: 16))
        .overlay(RoundedRectangle(cornerRadius: 16).stroke(palette.primary.opacity(0.3), lineWidth: 1))
    }
}

private struct Pill: View {
    let text: String
    @Environment(\.palette) private var palette

    var body: some View {
        Text(text)
            .font(AppFont.sans(11, .semibold)).foregroundStyle(palette.primary)
            .padding(.horizontal, 8).padding(.vertical, 4)
            .background(palette.primary.opacity(0.12), in: RoundedRectangle(cornerRadius: 8))
    }
}

private struct GuidanceBlock: View {
    let label: String
    let text: String
    let accent: Color
    @Environment(\.palette) private var palette

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(label).font(AppFont.sans(12, .semibold)).foregroundStyle(accent)
            Text(text).font(.bodyMedium).foregroundStyle(palette.onSurface).multilineTextAlignment(.leading)
        }
        .padding(12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(accent.opacity(0.07), in: RoundedRectangle(cornerRadius: 10))
    }
}
