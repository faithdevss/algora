import SwiftUI

// Port of feature/review/ReviewScreen.kt: the single SM-2 scheduled flashcard surface.
struct ReviewScreen: View {
    @Environment(AppStore.self) private var store
    @Environment(\.palette) private var palette

    @State private var queue: [ReviewCard]?
    @State private var counts = ReviewDeck.Counts()
    @State private var studyingAhead = false
    @State private var index = 0
    @State private var flipped = false
    @State private var reviewed = 0

    var body: some View {
        VStack(spacing: 0) {
            ScreenHeader(title: "Flashcards")
            if let cards = queue {
                if cards.isEmpty {
                    caughtUp("All caught up — nothing due right now. 🎉", scheduled: counts.scheduled, heldBack: counts.newHeldBack, canStudyAhead: true)
                } else if index >= cards.count {
                    caughtUp("Session complete — \(reviewed) card\(reviewed == 1 ? "" : "s") reviewed. 🎉",
                             scheduled: studyingAhead ? 0 : counts.scheduled, heldBack: studyingAhead ? 0 : counts.newHeldBack,
                             canStudyAhead: !studyingAhead)
                } else {
                    session(cards)
                }
            } else {
                Spacer()
            }
        }
        .onAppear(perform: load)
    }

    /// Freeze the queue once so grading does not reshuffle mid-session.
    private func load() {
        guard queue == nil else { return }
        let today = todayEpochDay()
        let deck = ReviewDeck.deck(store.srs)
        let allowance = max(dailyNewCardLimit - store.newCardsIntroduced(today: today), 0)
        counts = ReviewDeck.counts(deck, srs: store.srs, today: today, newAllowance: allowance)
        queue = ReviewDeck.queue(deck, srs: store.srs, today: today, newAllowance: allowance)
    }

    private func studyAhead() {
        queue = ReviewDeck.studyAhead(ReviewDeck.deck(store.srs), srs: store.srs)
        index = 0
        flipped = false
        reviewed = 0
        studyingAhead = true
    }

    private func session(_ cards: [ReviewCard]) -> some View {
        let card = cards[index]
        let rich = card.story != nil || card.figure != nil
        return VStack(spacing: 0) {
            Text(studyingAhead ? "Studying ahead · \(index + 1) of \(cards.count)" : "\(index + 1) of \(cards.count) · \(counts.due) due, \(counts.new) new")
                .font(.bodyMedium).foregroundStyle(palette.muted)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.horizontal, 16).padding(.vertical, 12)
            Button { withAnimation(.easeInOut(duration: 0.2)) { flipped.toggle() } } label: {
                ScrollView {
                    VStack(spacing: 0) {
                        Text(card.topicName)
                            .font(AppFont.grotesk(18, .bold)).foregroundStyle(palette.primary)
                            .padding(.bottom, 18)
                        if let story = card.story {
                            Text(story.text).font(.bodyMedium).foregroundStyle(palette.muted).padding(.bottom, 12)
                        }
                        if let figure = card.figure {
                            FigureCard(figure: figure).padding(.bottom, 14)
                        }
                        if flipped {
                            if let prompt = card.prompt {
                                Text(prompt).font(.bodyLarge).foregroundStyle(palette.muted).padding(.bottom, 14)
                            }
                            Text(card.takeaway).font(.titleLarge)
                            if let detail = card.detail {
                                Text(detail).font(.bodyMedium).foregroundStyle(palette.muted).padding(.top, 12)
                            }
                        } else {
                            Text(card.prompt ?? "Recall a key takeaway")
                                .font(card.prompt != nil ? .titleLarge : .bodyLarge)
                                .foregroundStyle(card.prompt != nil ? palette.onSurface : palette.muted)
                            Text("Tap to reveal").font(.bodyMedium).foregroundStyle(palette.muted).padding(.top, 10)
                        }
                    }
                    .multilineTextAlignment(.center)
                    .padding(rich ? 20 : 24)
                    .frame(maxWidth: .infinity)
                    .frame(minHeight: rich ? nil : 360)
                }
                .background(flipped ? palette.primary.opacity(0.08) : palette.surface, in: RoundedRectangle(cornerRadius: 20))
                .overlay(RoundedRectangle(cornerRadius: 20).stroke(palette.outline, lineWidth: 1))
            }
            .buttonStyle(.plain)
            .padding(.horizontal, 16)
            if flipped {
                HStack(spacing: 10) {
                    PrimaryButton(title: "Again", color: SimColors.red) { grade(card, 2) }
                    PrimaryButton(title: "Good", color: SimColors.blue) { grade(card, 4) }
                    PrimaryButton(title: "Easy", color: SimColors.green) { grade(card, 5) }
                }
                .padding(.horizontal, 16).padding(.vertical, 8)
                hint("Again brings it back tomorrow · Good and Easy push it further out")
            } else {
                hint("Tap the card to reveal, then grade your recall.").padding(.vertical, 8)
            }
        }
    }

    /// The grade sets the next due date — this is what stops a known card coming back tomorrow.
    private func grade(_ card: ReviewCard, _ quality: Int) {
        store.reviewCard(card.key, quality: quality)
        reviewed += 1
        index += 1
        flipped = false
    }

    private func hint(_ text: String) -> some View {
        Text(text).font(AppFont.sans(12)).foregroundStyle(palette.muted).multilineTextAlignment(.center)
            .frame(maxWidth: .infinity).padding(.horizontal, 16).padding(.bottom, 8)
    }

    private func caughtUp(_ message: String, scheduled: Int, heldBack: Int, canStudyAhead: Bool) -> some View {
        VStack(spacing: 10) {
            Spacer()
            Text(message).font(.bodyLarge).foregroundStyle(palette.muted)
            if heldBack > 0 {
                Text("Daily limit of \(dailyNewCardLimit) new cards reached — \(heldBack) more unlock tomorrow.")
                    .font(.bodyMedium).foregroundStyle(palette.muted)
            }
            if canStudyAhead && scheduled > 0 {
                Text("\(scheduled) card\(scheduled == 1 ? "" : "s") scheduled for later.").font(.bodyMedium).foregroundStyle(palette.muted)
                SecondaryButton(title: "Study ahead", action: studyAhead).frame(width: 180).padding(.top, 6)
            }
            Spacer()
        }
        .multilineTextAlignment(.center)
        .padding(32)
    }
}
