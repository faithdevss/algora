import StoreKit
import SwiftUI

// Port of feature/progress/ProgressScreen.kt: overall ring, this week, milestones, per-category
// cards. The streak-freeze row is Android-only (earned by watching a rewarded ad).

private struct ProgressGroup {
    let title: String
    let icon: String
    let color: Color
    let topics: [Topic]
    let kind: BrowserKind
}

private func groups(for mode: AppMode) -> [ProgressGroup] {
    let content = ContentStore.shared
    if mode == .dsa {
        let prep = content.topics(in: .INTERVIEW_PREP)
        return [
            ProgressGroup(title: "Data Structures", icon: "stack", color: Color(hex: 0x10B981), topics: content.topics(in: .DATA_STRUCTURES), kind: .dataStructures),
            ProgressGroup(title: "Algorithms", icon: "chip", color: SimColors.blue, topics: content.topics(in: .ALGORITHMS), kind: .algorithms),
            ProgressGroup(title: "Patterns", icon: "help", color: SimColors.amber, topics: prep.filter { $0.categoryId == patternsCategoryId }, kind: .patterns),
            ProgressGroup(title: "Interview Prep", icon: "mic", color: Color(hex: 0xEC4899), topics: prep.filter { $0.categoryId != patternsCategoryId }, kind: .interviewPrep),
            ProgressGroup(title: "Analysis", icon: "trend", color: SimColors.violet, topics: content.topics(in: .ANALYSIS), kind: .analysis),
        ]
    }
    return [
        ProgressGroup(title: "Machine Learning", icon: "robot", color: Color(hex: 0x6366F1), topics: content.topics(in: .ML), kind: .machineLearning),
        ProgressGroup(title: "Deep Learning", icon: "network", color: Color(hex: 0xEC4899), topics: content.topics(in: .DL), kind: .deepLearning),
        ProgressGroup(title: "NLP", icon: "globe", color: Color(hex: 0x14B8A6), topics: content.topics(in: .NLP), kind: .nlp),
        ProgressGroup(title: "Reinforcement Learning", icon: "game", color: Color(hex: 0xF97316), topics: content.topics(in: .RL), kind: .reinforcementLearning),
    ]
}

private struct Milestone: Hashable {
    let title: String
    let icon: String
    let color: Color
    let target: Int
}

private func milestones(total: Int) -> [Milestone] {
    let all = [
        Milestone(title: "First Steps", icon: "check", color: Color(hex: 0x10B981), target: 1),
        Milestone(title: "Explorer", icon: "map", color: SimColors.blue, target: 10),
        Milestone(title: "Momentum", icon: "trend", color: SimColors.amber, target: 25),
        Milestone(title: "Halfway", icon: "target", color: SimColors.violet, target: (total + 1) / 2),
        Milestone(title: "Track Complete", icon: "crown", color: Color(hex: 0xEC4899), target: total),
    ]
    var seen = Set<Int>()
    return all.filter { (1...max(total, 1)).contains($0.target) && seen.insert($0.target).inserted }.sorted { $0.target < $1.target }
}

struct ProgressScreen: View {
    @Environment(AppStore.self) private var store
    @Environment(AppSession.self) private var session
    @Environment(Router.self) private var router
    @Environment(\.palette) private var palette
    @Environment(\.requestReview) private var requestReview

    var body: some View {
        let mode = session.mode
        let groups = groups(for: mode)
        let completed = store.completedTopicIds
        let total = groups.reduce(0) { $0 + $1.topics.count }
        let done = groups.reduce(0) { $0 + $1.topics.filter { completed.contains($0.id) }.count }
        let pct = total == 0 ? 0 : done * 100 / total
        let solved = store.solvedProblemIds.filter { ContentStore.shared.problem($0) != nil }.count
        let streak = max(store.streak, 1)

        ScrollView {
            LazyVStack(alignment: .leading, spacing: 0) {
                Text("Your Progress").font(AppFont.grotesk(22, .bold)).padding(.bottom, 14)
                HeroCard(modeLabel: mode == .dsa ? "DSA TRACK" : "AI SIMULATION TRACK", pct: pct, done: done, total: total,
                         middle: mode == .dsa ? ("\(solved)", "Problems") : ("\(total - done)", "Remaining"), streak: streak)
                WeekCard(streak: streak).padding(.top, 12)
                sectionLabel("Milestones")
                MilestoneCard(milestones: milestones(total: total), done: done)
                sectionLabel("By category")
                ForEach(groups, id: \.title) { group in
                    let groupDone = group.topics.filter { completed.contains($0.id) }.count
                    CategoryProgressCard(group: group, done: groupDone) { router.push(.browser(group.kind)) }
                        .padding(.bottom, 11)
                }
            }
            .padding(.horizontal, screenGutter)
            .padding(.top, 14)
            .padding(.bottom, screenBottomInset)
        }
        .onAppear {
            // The learner opened this to look at their own progress — a good moment to ask.
            ReviewPrompt.maybeAsk(store: store, progressPercent: pct, requestReview: requestReview)
        }
    }

    private func sectionLabel(_ text: String) -> some View {
        Text(text).font(AppFont.sans(14, .bold)).foregroundStyle(palette.muted).padding(.top, 22).padding(.bottom, 10)
    }
}

private struct HeroCard: View {
    let modeLabel: String
    let pct: Int
    let done: Int
    let total: Int
    let middle: (String, String)
    let streak: Int
    @Environment(\.palette) private var palette

    var body: some View {
        VStack(spacing: 0) {
            HStack(spacing: 18) {
                OverallRing(pct: pct)
                VStack(alignment: .leading, spacing: 0) {
                    Text(modeLabel)
                        .font(AppFont.sans(11, .bold)).tracking(1.1)
                        .foregroundStyle(palette.primary)
                        .padding(.horizontal, 9).padding(.vertical, 4)
                        .background(palette.primary.opacity(0.14), in: RoundedRectangle(cornerRadius: 8))
                    HStack(alignment: .lastTextBaseline, spacing: 0) {
                        Text("\(done)").font(AppFont.grotesk(28, .bold))
                        Text(" / \(total)").font(AppFont.grotesk(18, .bold)).foregroundStyle(palette.muted)
                    }
                    .padding(.top, 8)
                    Text("topics completed").font(AppFont.sans(12.5)).foregroundStyle(palette.muted)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            }
            Rectangle().fill(palette.outline).frame(height: 1).padding(.top, 16)
            HStack {
                stat("\(done)", "Topics")
                divider
                stat(middle.0, middle.1)
                divider
                stat("\(streak)", "Day streak")
            }
            .padding(.top, 14)
        }
        .padding(18)
        .card(radius: 20)
    }

    private var divider: some View { Rectangle().fill(palette.outline).frame(width: 1, height: 26) }

    private func stat(_ value: String, _ label: String) -> some View {
        VStack(spacing: 2) {
            Text(value).font(AppFont.grotesk(18, .bold))
            Text(label).font(AppFont.sans(11.5)).foregroundStyle(palette.muted)
        }
        .frame(maxWidth: .infinity)
    }
}

/// Accent→tertiary sweep, starting at 12 o'clock.
private struct OverallRing: View {
    let pct: Int
    @Environment(\.palette) private var palette

    var body: some View {
        ZStack {
            Circle().stroke(palette.outlineVariant, lineWidth: 10)
            Circle()
                .trim(from: 0, to: CGFloat(pct) / 100)
                .stroke(AngularGradient(colors: [palette.primary, palette.tertiary, palette.primary], center: .center),
                        style: StrokeStyle(lineWidth: 10, lineCap: .round))
                .rotationEffect(.degrees(-90))
                .animation(.easeOut, value: pct)
            VStack(spacing: 0) {
                Text("\(pct)%").font(AppFont.grotesk(24, .bold))
                Text("COMPLETE").font(AppFont.sans(9, .bold)).tracking(1).foregroundStyle(palette.muted)
            }
        }
        .padding(5)
        .frame(width: 104, height: 104)
    }
}

/// Mon–Sun: topics finished that day, a tinted check for an active day, a ring on today.
private struct WeekCard: View {
    let streak: Int
    @Environment(AppStore.self) private var store
    @Environment(\.palette) private var palette

    var body: some View {
        let today = todayEpochDay()
        // Epoch day 0 was a Thursday, so +3 puts the week origin on Monday.
        let weekStart = today - (today + 3) % 7
        let completions = store.completionsByDay
        let days = store.activeDays.union((0..<Int64(streak)).map { today - $0 })
        let weekTotal = (0..<7).reduce(0) { $0 + (completions[weekStart + Int64($1)] ?? 0) }
        VStack(spacing: 0) {
            HStack {
                HStack(alignment: .lastTextBaseline, spacing: 0) {
                    Text("This week").font(AppFont.sans(14.5, .bold))
                    Text("  \(weekTotal) \(weekTotal == 1 ? "topic" : "topics")").font(AppFont.sans(12.5)).foregroundStyle(palette.muted)
                }
                Spacer()
                HStack(spacing: 3) {
                    Image(systemName: "flame.fill").font(.system(size: 12)).foregroundStyle(Color(hex: 0xFB923C))
                    Text("\(streak) \(streak == 1 ? "day" : "days") in a row").font(AppFont.sans(12.5)).foregroundStyle(palette.muted)
                }
            }
            HStack {
                ForEach(0..<7, id: \.self) { i in
                    let day = weekStart + Int64(i)
                    let finished = completions[day] ?? 0
                    let active = days.contains(day)
                    let isToday = day == today
                    VStack(spacing: 7) {
                        ZStack {
                            RoundedRectangle(cornerRadius: 10)
                                .fill(finished > 0 ? palette.primary : active ? palette.primary.opacity(0.18) : palette.outlineVariant)
                            if isToday && finished == 0 {
                                RoundedRectangle(cornerRadius: 10).stroke(palette.primary, lineWidth: 1.5)
                            }
                            if finished > 0 {
                                Text("\(finished)").font(AppFont.grotesk(13, .bold)).foregroundStyle(palette.onPrimary)
                            } else if active {
                                Image(systemName: "checkmark").font(.system(size: 11, weight: .bold)).foregroundStyle(palette.primary)
                            }
                        }
                        .frame(width: 30, height: 34)
                        Text(["M", "T", "W", "T", "F", "S", "S"][i])
                            .font(AppFont.sans(11.5, isToday ? .bold : .regular))
                            .foregroundStyle(isToday ? palette.primary : day > today ? palette.muted.opacity(0.5) : palette.muted)
                    }
                    if i < 6 { Spacer(minLength: 0) }
                }
            }
            .padding(.top, 14)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 15)
        .card(radius: 20)
    }
}

private struct MilestoneCard: View {
    let milestones: [Milestone]
    let done: Int
    @Environment(\.palette) private var palette

    var body: some View {
        let next = milestones.first { done < $0.target }?.target
        VStack(spacing: 0) {
            ForEach(Array(milestones.enumerated()), id: \.offset) { index, m in
                if index > 0 { Rectangle().fill(palette.outline).frame(height: 1) }
                row(m, unlocked: done >= m.target, isNext: m.target == next)
            }
        }
        .padding(.horizontal, 15)
        .padding(.vertical, 6)
        .card(radius: 20)
    }

    private func row(_ m: Milestone, unlocked: Bool, isNext: Bool) -> some View {
        HStack(spacing: 13) {
            Group {
                if unlocked {
                    AppIcon(name: m.icon, size: 19).foregroundStyle(.white)
                } else {
                    Image(systemName: "lock.fill").font(.system(size: 13)).foregroundStyle(palette.muted)
                }
            }
            .frame(width: 36, height: 36)
            .background(
                unlocked ? AnyShapeStyle(LinearGradient(colors: [m.color, m.color.opacity(0.6)], startPoint: .topLeading, endPoint: .bottomTrailing))
                    : AnyShapeStyle(palette.outlineVariant),
                in: RoundedRectangle(cornerRadius: 12)
            )
            VStack(alignment: .leading, spacing: 1) {
                Text(m.title).font(AppFont.sans(14, .bold)).foregroundStyle(palette.onSurface.opacity(unlocked ? 1 : 0.75))
                Text("Finish \(m.target) \(m.target == 1 ? "topic" : "topics")").font(AppFont.sans(11.5)).foregroundStyle(palette.muted)
                if isNext {
                    Bar(fraction: Double(done) / Double(m.target), color: m.color).padding(.top, 8)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            if unlocked {
                Image(systemName: "checkmark").font(.system(size: 15, weight: .bold)).foregroundStyle(m.color)
            } else {
                Text("\(m.target - done) to go").font(AppFont.sans(11.5, isNext ? .bold : .regular)).foregroundStyle(isNext ? m.color : palette.muted)
            }
        }
        .padding(.vertical, 12)
    }
}

private struct CategoryProgressCard: View {
    let group: ProgressGroup
    let done: Int
    let action: () -> Void
    @Environment(\.palette) private var palette

    var body: some View {
        let total = group.topics.count
        let pct = total == 0 ? 0 : done * 100 / total
        Button(action: action) {
            HStack(spacing: 13) {
                AppIcon(name: group.icon, size: 20)
                    .foregroundStyle(.white)
                    .frame(width: 38, height: 38)
                    .background(LinearGradient(colors: [group.color, group.color.opacity(0.6)], startPoint: .topLeading, endPoint: .bottomTrailing),
                                in: RoundedRectangle(cornerRadius: 12))
                VStack(alignment: .leading, spacing: 0) {
                    HStack {
                        Text(group.title).font(AppFont.sans(14.5, .bold))
                        Spacer()
                        Text("\(pct)%").font(AppFont.sans(13, .bold)).foregroundStyle(group.color)
                    }
                    .padding(.bottom, 7)
                    Bar(fraction: total == 0 ? 0 : Double(done) / Double(total), color: group.color)
                    Text("\(done) / \(total) topics").font(AppFont.sans(11.5)).foregroundStyle(palette.muted).padding(.top, 6)
                }
                Image(systemName: "chevron.right").font(.system(size: 13, weight: .semibold)).foregroundStyle(palette.muted)
            }
            .padding(.horizontal, 15)
            .padding(.vertical, 14)
            .card(radius: 16)
        }
        .buttonStyle(.plain)
    }
}

/// Shared 7pt track + fill.
private struct Bar: View {
    let fraction: Double
    let color: Color
    @Environment(\.palette) private var palette

    var body: some View {
        GeometryReader { geo in
            ZStack(alignment: .leading) {
                Capsule().fill(palette.outlineVariant)
                Capsule().fill(color).frame(width: geo.size.width * min(max(fraction, 0), 1))
            }
        }
        .frame(height: 7)
        .animation(.easeOut, value: fraction)
    }
}
