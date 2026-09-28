import SwiftUI

// Port of core/ui/components/CategoryBrowserScreen.kt and the eight thin screens over it: title,
// one overall progress bar, one search field, then icon-badged sections of rows.
struct CategoryBrowserScreen: View {
    let kind: BrowserKind
    @Environment(AppStore.self) private var store
    @Environment(Router.self) private var router
    @Environment(\.palette) private var palette
    @State private var query = ""

    private var sections: [(category: Category, topics: [Topic])] {
        let content = ContentStore.shared
        let topics = content.topics(in: kind.section)
        return content.categories(in: kind.section)
            .filter { category in
                switch kind {
                // Patterns has its own Home card, so Interview Prep does not list it twice.
                case .interviewPrep: category.id != patternsCategoryId
                case .patterns: category.id == patternsCategoryId
                default: true
                }
            }
            .map { category in (category, topics.filter { $0.categoryId == category.id }) }
    }

    var body: some View {
        let sections = sections
        let all = sections.flatMap(\.topics)
        let done = all.filter { store.completedTopicIds.contains($0.id) }.count
        let progress = all.isEmpty ? 0 : Double(done) / Double(all.count)

        VStack(spacing: 0) {
            ScreenHeader(title: kind.title)
            ScrollView {
                LazyVStack(alignment: .leading, spacing: 0) {
                    VStack(spacing: 0) {
                        HStack {
                            Text("\(done) of \(all.count) completed").foregroundStyle(palette.muted)
                            Spacer()
                            Text("\(Int(progress * 100))%").foregroundStyle(palette.primary)
                        }
                        .font(.bodyMedium)
                        .padding(.bottom, 4)
                        CategoryProgressBar(progress: progress).padding(.bottom, 14)
                        SearchField(query: $query, placeholder: "Search \(kind.title.lowercased())…")
                    }
                    .padding(.vertical, 8)

                    ForEach(sections, id: \.category.id) { section in
                        let rows = query.isEmpty ? section.topics : section.topics.filter { $0.name.localizedCaseInsensitiveContains(query) }
                        if !rows.isEmpty {
                            SectionHeader(title: section.category.name, iconName: section.category.iconName, accentColor: Color(argb: section.category.accentColor))
                                .padding(.top, 18)
                                .padding(.bottom, 12)
                            ForEach(rows) { topic in
                                TopicRow(title: topic.name, isCompleted: store.completedTopicIds.contains(topic.id), difficulty: topic.difficulty) {
                                    router.push(.topic(topic.id))
                                }
                                .padding(.vertical, 4)
                            }
                        }
                    }
                }
                .padding(.horizontal, screenGutter)
                .padding(.bottom, screenBottomInset)
            }
            .scrollDismissesKeyboard(.immediately)
        }
    }
}
