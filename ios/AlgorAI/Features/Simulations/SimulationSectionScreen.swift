import SwiftUI

// One Simulations-tab section on its own page (Coding Patterns, Data Structures, …): a large title with
// its lab and category counts, a search scoped to the section, and one card of category rows. A row
// opens in place to its numbered labs; a lab's play button opens it. Port: SimulationSectionScreen.kt.

/// A 5 × 5 dot glyph per lab family, lit dots in the accent over dim ones.
func simDotPattern(_ name: String) -> [String] {
    switch name {
    case "Array Walk": return [".....", "..#..", "#####", ".....", "....."]
    case "Recursion": return ["#####", "#...#", "#...#", "#...#", "#####"]
    case "Tree": return ["..#..", ".#.#.", "#...#", ".....", "....."]
    case "Graph": return ["#...#", ".#.#.", "..#..", ".#...", "#...#"]
    case "Grid": return ["#.#.#", ".....", "#.#.#", ".....", "#.#.#"]
    case "DP Table": return ["#####", "####.", "###..", "##...", "#...."]
    case "Sorting": return ["....#", "...##", "..###", ".####", "#####"]
    case "Search": return [".###.", "#...#", "#...#", ".###.", "....#"]
    case "Hashing": return ["##...", "..##.", "#...#", ".##..", "...##"]
    case "Bit Board": return ["#.##.", ".#..#", "##.#.", "..##.", "#..##"]
    case "Linked Structure": return ["#....", ".#...", "..#..", "...#.", "....#"]
    case "Game Search": return ["..#..", ".###.", "#####", "..#..", "..#.."]
    default:
        // Any other category: a mirrored pattern seeded by its name, so each reads as its own mark.
        var h: UInt32 = 2166136261
        for b in name.utf8 { h = (h ^ UInt32(b)) &* 16777619 }
        return (0..<5).map { r in
            let bits = (0..<3).map { c in (h >> UInt32((r * 3 + c) % 31)) & 1 == 1 }
            return [bits[0], bits[1], bits[2], bits[1], bits[0]].map { $0 ? "#" : "." }.joined()
        }
    }
}

/// The short lab-type line under a coding-pattern family.
private func patternSubtitle(_ name: String) -> String? {
    [
        "Array Walk": "Array · pointer player", "Recursion": "Call tree", "Tree": "Tree stepper", "Graph": "Graph stepper",
        "Grid": "Grid stepper", "DP Table": "Table fill", "Sorting": "Sort stepper", "Search": "Search stepper",
        "Hashing": "Bucket stepper", "Bit Board": "Per-bit stepper", "Linked Structure": "Node stepper", "Game Search": "Game tree",
    ][name]
}

/// A category's subtitle: the pattern family's line, or the lab type most of its labs run.
private func subtitle(_ sub: SimSubgroup) -> String {
    if let s = patternSubtitle(sub.name), sub.key.hasPrefix(Section.INTERVIEW_PREP.rawValue) { return s }
    var counts: [String: Int] = [:]
    var order: [String] = []
    for e in sub.entries {
        if counts[e.label] == nil { order.append(e.label) }
        counts[e.label, default: 0] += 1
    }
    var best = ""
    for l in order where best.isEmpty || counts[l]! > counts[best]! { best = l }
    return best
}

/// "02 · Trees" → "Trees".
private func plainName(_ name: String) -> String {
    if let r = name.range(of: " · "), name[..<r.lowerBound].allSatisfy(\.isNumber) { return String(name[r.upperBound...]) }
    return name
}

struct SimDotTile: View {
    let name: String
    let accent: Color

    var body: some View {
        let rows = simDotPattern(name)
        VStack(spacing: 3) {
            ForEach(0..<5, id: \.self) { r in
                HStack(spacing: 3) {
                    ForEach(0..<5, id: \.self) { c in
                        let lit = Array(rows[r])[c] == "#"
                        Circle().fill(accent.opacity(lit ? 1 : 0.22)).frame(width: 4.5, height: 4.5)
                    }
                }
            }
        }
        .frame(width: 44, height: 44)
        .background(accent.opacity(0.16), in: RoundedRectangle(cornerRadius: 11))
    }
}

struct SimulationSectionScreen: View {
    let sectionId: String
    @Environment(Router.self) private var router
    @Environment(\.palette) private var palette
    @Environment(\.dismiss) private var dismiss
    @State private var query = ""
    @State private var open: Set<String> = []

    var body: some View {
        if let group = simGroups.first(where: { $0.section.rawValue == sectionId }) {
            content(group)
        } else {
            VStack { DetailHeader(title: "Simulations"); Spacer() }
        }
    }

    private func content(_ group: SimGroup) -> some View {
        let searching = !query.trimmingCharacters(in: .whitespaces).isEmpty
        let shown: SimGroup? = searching ? group.filtered(query) : group
        let accent = Color(argb: group.accent)
        return ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                Button { dismiss() } label: {
                    HStack(spacing: 4) {
                        Image(systemName: "chevron.backward").font(.system(size: 19, weight: .semibold))
                        Text("Simulations").font(AppFont.sans(17))
                    }
                    .foregroundStyle(palette.primary)
                    .frame(minHeight: 44)
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Back to Simulations")
                Text(group.title).font(AppFont.sans(34, .bold)).padding(.top, 2)
                Text("\(group.count) labs · \(group.subgroups.count) categories")
                    .font(AppFont.sans(15)).foregroundStyle(palette.muted).padding(.top, 2).padding(.bottom, 14)
                SearchField(query: $query, placeholder: "Search \(group.title)").padding(.bottom, 16)
                if let shown {
                    VStack(spacing: 0) {
                        ForEach(Array(shown.subgroups.enumerated()), id: \.element.key) { i, sub in
                            // A search opens every category it matched.
                            let isOpen = searching || open.contains(sub.key)
                            if i > 0 { Divider().overlay(palette.outline) }
                            CategoryRow(sub: sub, accent: accent, expanded: isOpen) {
                                withAnimation(.easeInOut(duration: 0.2)) {
                                    if open.contains(sub.key) { open.remove(sub.key) } else { open.insert(sub.key) }
                                }
                            }
                            if isOpen {
                                ForEach(Array(sub.entries.enumerated()), id: \.element.topic.id) { j, entry in
                                    Divider().overlay(palette.outline).padding(.leading, 16)
                                    LabRow(number: j + 1, entry: entry, accent: accent) { router.push(.simulation(entry.topic.id)) }
                                }
                            }
                        }
                    }
                    .background(palette.surface, in: RoundedRectangle(cornerRadius: 16))
                    .clipShape(RoundedRectangle(cornerRadius: 16))
                    .overlay(RoundedRectangle(cornerRadius: 16).stroke(palette.outline, lineWidth: 1))
                } else {
                    Text("No labs match “\(query)”.").font(.bodyMedium).foregroundStyle(palette.muted).padding(.top, 12)
                }
            }
            .padding(.horizontal, screenGutter)
            .padding(.bottom, screenBottomInset)
        }
        .scrollDismissesKeyboard(.immediately)
    }
}

private struct CategoryRow: View {
    let sub: SimSubgroup
    let accent: Color
    let expanded: Bool
    let action: () -> Void
    @Environment(\.palette) private var palette

    var body: some View {
        Button(action: action) {
            HStack(spacing: 14) {
                SimDotTile(name: sub.name, accent: accent)
                VStack(alignment: .leading, spacing: 2) {
                    Text(plainName(sub.name)).font(AppFont.sans(17, .semibold)).foregroundStyle(palette.onSurface).lineLimit(1)
                    Text(subtitle(sub)).font(AppFont.sans(14)).foregroundStyle(palette.muted).lineLimit(1)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                Text("\(sub.entries.count)").font(AppFont.sans(16)).foregroundStyle(palette.muted)
                Image(systemName: expanded ? "chevron.up" : "chevron.down")
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundStyle(expanded ? accent : palette.muted)
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 12)
            .background(expanded ? palette.onSurface.opacity(0.04) : .clear)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel("\(plainName(sub.name)), \(sub.entries.count) labs")
        .accessibilityHint(expanded ? "Collapse" : "Expand")
    }
}

private struct LabRow: View {
    let number: Int
    let entry: SimEntry
    let accent: Color
    let action: () -> Void
    @Environment(\.palette) private var palette

    var body: some View {
        Button(action: action) {
            HStack(spacing: 14) {
                Text(String(format: "%02d", number)).font(AppFont.mono(14, .semibold)).foregroundStyle(accent).frame(width: 30, alignment: .leading)
                Text(entry.topic.name).font(AppFont.sans(17)).foregroundStyle(palette.onSurface).lineLimit(2)
                    .frame(maxWidth: .infinity, alignment: .leading)
                Image(systemName: "play.fill")
                    .font(.system(size: 12))
                    .foregroundStyle(accent)
                    .frame(width: 40, height: 40)
                    .background(accent.opacity(0.2), in: Circle())
            }
            .padding(.leading, 26)
            .padding(.trailing, 16)
            .padding(.vertical, 10)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel("Run \(entry.topic.name)")
    }
}
