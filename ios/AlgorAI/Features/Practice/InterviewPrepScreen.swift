import SwiftUI

// Interview Prep as a list of sets: overall progress, the Interview / Learn switch, then each category
// under its icon header as one card of sets that start straight away in the chosen mode. Guides that are
// not quizzes (behavioral bank, design primer) sit in the same cards and open as pages.
struct InterviewPrepScreen: View {
    @Environment(AppStore.self) private var store
    @Environment(\.palette) private var palette
    @AppStorage(quizLearnModeKey) private var learn = false

    var body: some View {
        let content = ContentStore.shared
        let topics = content.topics(in: BrowserKind.interviewPrep.section)
        // Patterns has its own Home card, so Interview Prep does not list it twice.
        let sections = content.categories(in: BrowserKind.interviewPrep.section)
            .filter { $0.id != patternsCategoryId }
            .map { category in (category, topics.filter { $0.categoryId == category.id }) }
            .filter { !$0.1.isEmpty }
        let all = sections.flatMap(\.1)
        let done = all.filter { store.completedTopicIds.contains($0.id) }.count
        let progress = all.isEmpty ? 0 : Double(done) / Double(all.count)
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 0) {
                LargeTitleHeader(title: BrowserKind.interviewPrep.title)
                HStack {
                    Text("\(done) of \(all.count) completed").foregroundStyle(palette.muted)
                    Spacer()
                    Text("\(Int(progress * 100))%").foregroundStyle(palette.primary)
                }
                .font(AppFont.sans(17))
                .padding(.top, 8)
                GeometryReader { geo in
                    ZStack(alignment: .leading) {
                        Capsule().fill(SimColors.tint)
                        Capsule().fill(palette.primary).frame(width: max(geo.size.width * progress, progress > 0 ? 8 : 0))
                    }
                }
                .frame(height: 5)
                .padding(.top, 8)
                .padding(.bottom, 20)
                QuizModeToggle(learn: $learn).padding(.bottom, 8)
                ForEach(sections, id: \.0.id) { category, rows in
                    let accent = Color(argb: category.accentColor)
                    HStack(spacing: 12) {
                        AppIcon(name: category.iconName, size: 18)
                            .foregroundStyle(.white)
                            .frame(width: 36, height: 36)
                            .background(LinearGradient(colors: [accent, accent.opacity(0.7)], startPoint: .topLeading, endPoint: .bottomTrailing),
                                        in: RoundedRectangle(cornerRadius: 10))
                        Text(category.name).font(AppFont.grotesk(22, .bold))
                    }
                    .padding(.top, 20)
                        .padding(.bottom, 12)
                    QuizSetCard(items: rows.map { QuizSetItem.make($0, store: store) }, learn: learn)
                }
            }
            .padding(.horizontal, screenGutter)
            .padding(.top, 4)
            .padding(.bottom, screenBottomInset)
        }
    }
}
