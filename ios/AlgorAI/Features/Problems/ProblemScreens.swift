import SwiftUI

// Ports of feature/practice/problems/ProblemListScreen.kt, ProblemFilter.kt and
// ProblemDetailScreen.kt. The pattern-level paywall is gone on iOS — every group is open.

struct ProblemFilters {
    var query = ""
    var difficulties: Set<Difficulty> = []
    var unsolvedOnly = false
    var isActive: Bool { !query.trimmingCharacters(in: .whitespaces).isEmpty || !difficulties.isEmpty || unsolvedOnly }

    /// Text matches the title, the pattern's name and blurb, and prerequisite labels — so "hash"
    /// finds problems that *need* hashing.
    func apply(_ problems: [PracticeProblem], pattern: ProblemPattern?, solved: Set<String>) -> [PracticeProblem] {
        let q = query.trimmingCharacters(in: .whitespaces)
        return problems.filter { p in
            let matchesQuery = q.isEmpty || p.title.localizedCaseInsensitiveContains(q)
                || pattern?.name.localizedCaseInsensitiveContains(q) == true
                || pattern?.blurb.localizedCaseInsensitiveContains(q) == true
                || p.prerequisites.contains { $0.label.localizedCaseInsensitiveContains(q) }
            return matchesQuery && (difficulties.isEmpty || difficulties.contains(p.difficulty))
                && (!unsolvedOnly || !solved.contains(p.id))
        }
    }
}

/// Layout: a progress summary card (solved count, overall bar, per-difficulty tallies), a pill search
/// field, one row of pill chips — All/Easy/Medium/Hard pick a single difficulty, Unsolved toggles on
/// top — and a card per pattern with a two-letter tile and its own progress bar, opening in place.
struct ProblemListScreen: View {
    @Environment(AppStore.self) private var store
    @Environment(Router.self) private var router
    @Environment(\.palette) private var palette
    @State private var filters = ProblemFilters()
    @State private var expanded: Set<String> = []

    var body: some View {
        let content = ContentStore.shared
        let solved = store.solvedProblemIds
        let all = content.problems
        let solvedCount = solved.filter { content.problem($0) != nil }.count
        let groups = content.patterns.compactMap { pattern -> (ProblemPattern, [PracticeProblem])? in
            let hits = filters.apply(content.problems(forPattern: pattern.id), pattern: pattern, solved: solved)
            return hits.isEmpty ? nil : (pattern, hits)
        }

        VStack(spacing: 0) {
            ScreenHeader(title: "Problem Solving")
            ScrollView {
                LazyVStack(alignment: .leading, spacing: 0) {
                    SummaryCard(
                        solved: solvedCount,
                        total: all.count,
                        tallies: [Difficulty.BEGINNER, .INTERMEDIATE, .ADVANCED].map { level in
                            let inLevel = all.filter { $0.difficulty == level }
                            return (level, inLevel.filter { solved.contains($0.id) }.count, inLevel.count)
                        }
                    )
                    SearchField(query: $filters.query, placeholder: "Search \(all.count) problems", pill: true)
                        .padding(.top, 14)
                    filterChips.padding(.top, 12).padding(.bottom, 6)
                    if groups.isEmpty {
                        Text("No problems match these filters.").font(.bodyMedium).foregroundStyle(palette.muted).padding(.top, 24)
                    }
                    ForEach(groups, id: \.0.id) { pattern, problems in
                        // A narrowed bank shows its hits directly.
                        let open = filters.isActive || expanded.contains(pattern.id)
                        PatternCard(pattern: pattern, problems: problems, solved: solved, isOpen: open) {
                            if expanded.contains(pattern.id) { expanded.remove(pattern.id) } else { expanded.insert(pattern.id) }
                        } onProblem: { router.push(.problem($0)) }
                        .padding(.top, 12)
                    }
                }
                .padding(.horizontal, screenGutter)
                .padding(.top, 12)
                .padding(.bottom, screenBottomInset)
            }
            .scrollDismissesKeyboard(.immediately)
        }
    }

    private var filterChips: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                PillChip(label: "All", selected: filters.difficulties.isEmpty) { filters.difficulties = [] }
                ForEach([Difficulty.BEGINNER, .INTERMEDIATE, .ADVANCED], id: \.self) { level in
                    let on = filters.difficulties == [level]
                    PillChip(label: difficultyStyle(level).0, selected: on) { filters.difficulties = on ? [] : [level] }
                }
                PillChip(label: "Unsolved", selected: filters.unsolvedOnly) { filters.unsolvedOnly.toggle() }
            }
        }
    }
}

private func difficultyStyle(_ difficulty: Difficulty) -> (String, Color) {
    switch difficulty {
    case .BEGINNER: ("Easy", SimColors.green)
    case .INTERMEDIATE: ("Medium", SimColors.amber)
    case .ADVANCED: ("Hard", SimColors.red)
    }
}

/// Two letters for a pattern's tile: the initials of its first two words, or the first two letters of
/// a one-word name — "Sliding Window" → SW, "Greedy" → GR.
func patternCode(_ name: String) -> String {
    let words = name.split(whereSeparator: { $0 == " " || $0 == "-" || $0 == "&" }).filter { $0.first?.isLetter == true }
    let code = words.count >= 2 ? "\(words[0].first!)\(words[1].first!)" : String(words.first?.prefix(2) ?? "")
    return code.uppercased()
}

private struct ProgressTrack: View {
    let fraction: Double
    let color: Color
    let height: CGFloat
    @Environment(\.palette) private var palette

    var body: some View {
        GeometryReader { geo in
            ZStack(alignment: .leading) {
                Capsule().fill(palette.outline)
                if fraction > 0 {
                    Capsule().fill(color).frame(width: geo.size.width * min(max(fraction, 0), 1))
                }
            }
        }
        .frame(height: height)
    }
}

private struct SummaryCard: View {
    let solved: Int
    let total: Int
    let tallies: [(Difficulty, Int, Int)]
    @Environment(\.palette) private var palette

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(alignment: .lastTextBaseline, spacing: 8) {
                Text("\(solved)").font(AppFont.grotesk(34, .bold)).foregroundStyle(palette.onSurface)
                Text("of \(total) solved").font(.bodyLarge).foregroundStyle(palette.muted)
                Spacer()
                Text("\(total == 0 ? 0 : solved * 100 / total)%").font(AppFont.mono(13)).foregroundStyle(palette.primary)
            }
            ProgressTrack(fraction: total == 0 ? 0 : Double(solved) / Double(total), color: palette.primary, height: 6)
                .padding(.vertical, 14)
            HStack(spacing: 18) {
                ForEach(tallies, id: \.0) { level, done, count in
                    HStack(spacing: 6) {
                        Circle().fill(difficultyStyle(level).1).frame(width: 7, height: 7)
                        Text(difficultyStyle(level).0).font(AppFont.sans(13)).foregroundStyle(palette.muted)
                        Text("\(done)/\(count)").font(AppFont.mono(13)).foregroundStyle(palette.onSurface)
                    }
                }
            }
        }
        .padding(18)
        .background(palette.surface, in: RoundedRectangle(cornerRadius: 20))
        .overlay(RoundedRectangle(cornerRadius: 20).stroke(palette.outline, lineWidth: 1))
    }
}

private struct PillChip: View {
    let label: String
    let selected: Bool
    let action: () -> Void
    @Environment(\.palette) private var palette

    var body: some View {
        Button(action: action) {
            Text(label)
                .font(AppFont.sans(14, .semibold))
                .foregroundStyle(selected ? palette.background : palette.muted)
                .padding(.horizontal, 16)
                .padding(.vertical, 8)
                .background(selected ? palette.primary : palette.surface, in: Capsule())
                .overlay(Capsule().stroke(selected ? Color.clear : palette.outline, lineWidth: 1))
        }
        .buttonStyle(.plain)
    }
}

private struct PatternCard: View {
    let pattern: ProblemPattern
    let problems: [PracticeProblem]
    let solved: Set<String>
    let isOpen: Bool
    let onToggle: () -> Void
    let onProblem: (String) -> Void
    @Environment(\.palette) private var palette

    var body: some View {
        let accent = Color(argb: pattern.accentColor)
        let done = problems.filter { solved.contains($0.id) }.count
        VStack(alignment: .leading, spacing: 0) {
            Button(action: onToggle) {
                HStack(spacing: 14) {
                    Text(patternCode(pattern.name))
                        .font(AppFont.mono(14, .medium))
                        .foregroundStyle(accent)
                        .frame(width: 40, height: 40)
                        .background(accent.opacity(0.18), in: RoundedRectangle(cornerRadius: 11))
                    VStack(alignment: .leading, spacing: 8) {
                        HStack(spacing: 8) {
                            Text(pattern.name).font(AppFont.grotesk(17, .bold)).foregroundStyle(palette.onSurface).lineLimit(1)
                                .frame(maxWidth: .infinity, alignment: .leading)
                            Text("\(done)/\(problems.count)").font(AppFont.mono(13)).foregroundStyle(palette.muted)
                        }
                        ProgressTrack(fraction: problems.isEmpty ? 0 : Double(done) / Double(problems.count), color: accent, height: 4)
                    }
                    Image(systemName: isOpen ? "chevron.up" : "chevron.down")
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundStyle(palette.muted)
                }
                .padding(16)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel("\(pattern.name), \(done) of \(problems.count) solved")
            if isOpen {
                Rectangle().fill(palette.outline).frame(height: 1)
                Text(pattern.blurb).font(.bodyMedium).foregroundStyle(palette.muted)
                    .padding(.horizontal, 16).padding(.top, 12).padding(.bottom, 4)
                ForEach(problems) { problem in
                    ProblemRow(problem: problem, accent: accent, solved: solved.contains(problem.id)) {
                        onProblem(problem.id)
                    }
                }
                Spacer().frame(height: 8)
            }
        }
        .background(palette.surface, in: RoundedRectangle(cornerRadius: 18))
        .overlay(RoundedRectangle(cornerRadius: 18).stroke(palette.outline, lineWidth: 1))
    }
}

private struct ProblemRow: View {
    let problem: PracticeProblem
    let accent: Color
    let solved: Bool
    let action: () -> Void
    @Environment(\.palette) private var palette

    var body: some View {
        Button(action: action) {
            HStack(spacing: 12) {
                if solved {
                    Image(systemName: "checkmark.circle.fill").font(.system(size: 17)).foregroundStyle(accent)
                } else {
                    Circle().stroke(palette.outline, lineWidth: 1.5).frame(width: 15, height: 15).frame(width: 18, height: 18)
                }
                VStack(alignment: .leading, spacing: 4) {
                    Text(problem.title).font(.bodyLarge).foregroundStyle(palette.onSurface).multilineTextAlignment(.leading)
                    DifficultyBadge(difficulty: problem.difficulty)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                Image(systemName: "chevron.right").font(.system(size: 13, weight: .semibold)).foregroundStyle(palette.muted)
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 10)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }
}

/// Everything past the prompt is revealed on demand: hints one at a time, then the approach, then
/// the solution.
struct ProblemDetailScreen: View {
    let problemId: String
    @Environment(AppStore.self) private var store
    @Environment(Router.self) private var router
    @Environment(\.palette) private var palette
    @State private var hintsShown = 0
    @State private var approachShown = false

    var body: some View {
        if let problem = ContentStore.shared.problem(problemId) {
            detail(problem)
        } else {
            VStack(alignment: .leading) {
                ScreenHeader(title: "Problem")
                Text("Problem not found").font(.titleMedium).padding(16)
                Spacer()
            }
        }
    }

    private func detail(_ problem: PracticeProblem) -> some View {
        let pattern = ContentStore.shared.pattern(problem.patternId)
        let accentArgb = pattern?.accentColor ?? 0xFF3B_82F6
        let accent = Color(argb: accentArgb)
        let solved = store.solvedProblemIds.contains(problem.id)
        return VStack(spacing: 0) {
            ScreenHeader(title: problem.title)
            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    HStack(spacing: 8) {
                        Text(pattern?.name ?? problem.patternId)
                            .font(AppFont.sans(11, .medium)).foregroundStyle(accent)
                            .padding(.horizontal, 8).padding(.vertical, 3)
                            .background(accent.opacity(0.14), in: RoundedRectangle(cornerRadius: 6))
                        DifficultyBadge(difficulty: problem.difficulty)
                    }
                    SectionCard(title: "Problem") { Text(problem.prompt).font(.bodyLarge) }
                    if !problem.examples.isEmpty {
                        SectionCard(title: "Examples") {
                            VStack(alignment: .leading, spacing: 12) {
                                ForEach(problem.examples, id: \.self) { example in
                                    VStack(alignment: .leading, spacing: 2) {
                                        Text("Input:  \(example.input)").font(AppFont.mono(14))
                                        Text("Output: \(example.output)").font(AppFont.mono(14)).foregroundStyle(accent)
                                        if !example.note.isEmpty {
                                            Text(example.note).font(.bodyMedium).foregroundStyle(palette.muted)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    // Before the hints: if you cannot start at all, the gap is knowledge, not a nudge.
                    if !problem.prerequisites.isEmpty {
                        SectionCard(title: "Knowledge you need") {
                            Text("Tap any of these to study it before solving.").font(.bodyMedium).foregroundStyle(palette.muted).padding(.bottom, 10)
                            ForEach(problem.prerequisites, id: \.self) { prereq in
                                Button { router.push(.topic(prereq.topicId)) } label: {
                                    HStack {
                                        VStack(alignment: .leading, spacing: 1) {
                                            Text(prereq.label).font(.bodyLarge)
                                            Text(prereq.why).font(.bodyMedium).foregroundStyle(palette.muted)
                                        }
                                        .multilineTextAlignment(.leading)
                                        .frame(maxWidth: .infinity, alignment: .leading)
                                        Image(systemName: "chevron.right").font(.system(size: 13, weight: .semibold)).foregroundStyle(palette.primary)
                                    }
                                    .padding(.horizontal, 12).padding(.vertical, 10)
                                    .card(radius: 12, fill: palette.background)
                                }
                                .buttonStyle(.plain)
                                .padding(.bottom, 8)
                            }
                        }
                    }
                    if !problem.constraints.isEmpty {
                        SectionCard(title: "Constraints") {
                            ForEach(problem.constraints, id: \.self) { line in
                                HStack(alignment: .top, spacing: 8) { Text("·"); Text(line) }.font(.bodyMedium).padding(.vertical, 2)
                            }
                        }
                    }
                    SectionCard(title: "Hints") {
                        if hintsShown == 0 {
                            Text("Stuck? Take the smallest nudge first — \(problem.hints.count) hints available.").font(.bodyMedium).foregroundStyle(palette.muted)
                        }
                        ForEach(Array(problem.hints.prefix(hintsShown).enumerated()), id: \.offset) { i, hint in
                            HStack(alignment: .top, spacing: 10) {
                                Text("\(i + 1)").font(AppFont.sans(14, .bold)).foregroundStyle(accent)
                                Text(hint).font(.bodyMedium)
                            }
                            .padding(.top, i == 0 ? 0 : 10)
                        }
                        if hintsShown < problem.hints.count {
                            SecondaryButton(title: hintsShown == 0 ? "Show first hint" : "Next hint (\(hintsShown + 1)/\(problem.hints.count))") { hintsShown += 1 }
                                .fixedSize().padding(.top, 10)
                        }
                    }
                    SectionCard(title: "Approach") {
                        if approachShown {
                            ForEach(Array(problem.approach.enumerated()), id: \.offset) { i, step in
                                HStack(alignment: .top, spacing: 8) {
                                    Text("\(i + 1).").font(AppFont.sans(14, .bold)).foregroundStyle(accent)
                                    Text(step).font(.bodyMedium)
                                }
                                .padding(.top, i == 0 ? 0 : 9)
                            }
                            complexity("Time", problem.timeComplexity, accent).padding(.top, 12)
                            complexity("Space", problem.spaceComplexity, accent)
                        } else {
                            SecondaryButton(title: "Show approach & complexity") { approachShown = true }.fixedSize()
                        }
                    }
                    // Collapsed by default — the card's own expander is the "show solution" gate.
                    CodeBlockCard(block: CodeBlock(title: "Solution — tap to reveal", accentColor: accentArgb, code: problem.solutionCode, variants: []), expanded: false)
                    PrimaryButton(title: solved ? "Solved — tap to undo" : "Mark as solved", color: solved ? palette.muted : accent) {
                        store.setProblemSolved(problem.id, !solved)
                    }
                    if let linked = problem.linkedTopicId {
                        Button { router.push(.topic(linked)) } label: {
                            VStack(alignment: .leading, spacing: 2) {
                                Text("Study the pattern").font(AppFont.sans(12, .medium)).foregroundStyle(accent)
                                Text(problem.linkedTopicLabel ?? linked).font(.titleMedium)
                            }
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .padding(14)
                            .background(accent.opacity(0.10), in: RoundedRectangle(cornerRadius: 14))
                            .overlay(RoundedRectangle(cornerRadius: 14).stroke(accent.opacity(0.35), lineWidth: 1))
                        }
                        .buttonStyle(.plain)
                    }
                }
                .padding(.horizontal, screenGutter)
                .padding(.top, 8)
                .padding(.bottom, screenBottomInset)
            }
        }
    }

    private func complexity(_ label: String, _ value: String, _ accent: Color) -> some View {
        HStack(alignment: .top, spacing: 8) {
            Text(label).font(AppFont.sans(14, .bold)).foregroundStyle(accent)
            Text(value).font(.bodyMedium)
        }
        .padding(.top, 3)
    }
}

/// Card with a small uppercase accent title.
struct SectionCard<Content: View>: View {
    let title: String
    @ViewBuilder let content: () -> Content
    @Environment(\.palette) private var palette

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text(title.uppercased()).font(AppFont.sans(11, .bold)).foregroundStyle(palette.primary).padding(.bottom, 9)
            content()
        }
        .padding(15)
        .frame(maxWidth: .infinity, alignment: .leading)
        .card(radius: 16)
    }
}
