import Foundation

// Pure practice logic ported from feature/review/ReviewScreen.kt + FlashcardDecks.kt,
// feature/practice/daily/ and feature/practice/weakspots/. No UI and no store writes.

// MARK: - Review cards

/// `prompt` is the card's front. Takeaway cards have none; curated and mistake cards carry their
/// question. Mistake cards also carry the question's story, figure and explanation.
struct ReviewCard: Hashable {
    let key: String
    let topicName: String
    let takeaway: String
    var prompt: String?
    var detail: String?
    var figure: Figure?
    var story: QuizStory?
}

/// Never-seen cards introduced per day.
let dailyNewCardLimit = 20
private let studyAheadBatch = 20

enum ReviewDeck {
    /// Every takeaway ("topicId#index") plus the curated decks ("deck_x#index").
    static let base: [ReviewCard] = {
        let content = ContentStore.shared
        let takeaways = content.contents.keys.sorted().flatMap { topicId -> [ReviewCard] in
            let name = content.topic(topicId)?.name ?? topicId
            return content.contents[topicId]!.takeaways.enumerated().map { i, t in
                ReviewCard(key: "\(topicId)#\(i)", topicName: name, takeaway: t)
            }
        }
        let curated = content.flashcardDecks.flatMap { deck in
            deck.cards.enumerated().map { i, card in
                ReviewCard(key: "\(deck.id)#\(i)", topicName: deck.name, takeaway: card.back, prompt: card.front)
            }
        }
        return takeaways + curated
    }()

    /// A card for every missed quiz question, resolved from its SRS key.
    static func mistakeCards(_ srsKeys: some Collection<String>) -> [ReviewCard] {
        srsKeys.filter { $0.hasPrefix(quizCardPrefix) }.sorted().compactMap { cardKey in
            guard let (quizId, index) = parseQuestionKey(String(cardKey.dropFirst(quizCardPrefix.count))),
                  let quiz = ContentStore.shared.quiz(quizId), quiz.questions.indices.contains(index) else { return nil }
            let q = quiz.questions[index]
            return ReviewCard(key: cardKey, topicName: "\(quiz.title) · missed question", takeaway: q.options[q.correctIndex],
                              prompt: q.prompt, detail: q.explanation, figure: q.figure, story: q.story)
        }
    }

    static func deck(_ srs: [String: SrsCard]) -> [ReviewCard] { base + mistakeCards(srs.keys) }

    struct Counts {
        var due = 0
        var new = 0
        var scheduled = 0
        var newHeldBack = 0
        var waiting: Int { due + new }
    }

    static func counts(_ cards: [ReviewCard], srs: [String: SrsCard], today: Int64, newAllowance: Int = .max) -> Counts {
        var due = 0, new = 0
        for card in cards {
            if let state = srs[card.key] {
                if state.dueDay <= today { due += 1 }
            } else {
                new += 1
            }
        }
        let allowed = min(new, max(newAllowance, 0))
        return Counts(due: due, new: allowed, scheduled: cards.count - due - new, newHeldBack: new - allowed)
    }

    /// Due cards first, then as many never-seen cards as today's allowance still permits.
    static func queue(_ cards: [ReviewCard], srs: [String: SrsCard], today: Int64, newAllowance: Int) -> [ReviewCard] {
        let due = cards.filter { (srs[$0.key]?.dueDay ?? .max) <= today }.shuffled()
        let fresh = cards.filter { srs[$0.key] == nil }.shuffled().prefix(max(newAllowance, 0))
        return due + fresh
    }

    /// Pulls the soonest-scheduled cards forward when nothing is due.
    static func studyAhead(_ cards: [ReviewCard], srs: [String: SrsCard]) -> [ReviewCard] {
        cards.compactMap { card in srs[card.key].map { (card, $0.dueDay) } }
            .sorted { $0.1 < $1.1 }
            .prefix(studyAheadBatch)
            .map(\.0)
    }
}

extension AppStore {
    func reviewCounts(today: Int64 = todayEpochDay()) -> ReviewDeck.Counts {
        ReviewDeck.counts(ReviewDeck.deck(srs), srs: srs, today: today,
                          newAllowance: max(dailyNewCardLimit - newCardsIntroduced(today: today), 0))
    }
}

// MARK: - Daily drill

/// One sampled question, kept with the quiz it came from so a miss traces back to a real set.
struct DrillQuestion: Hashable {
    let quizId: String
    let index: Int
    let question: QuizQuestion
}

let drillQuestionCount = 5
let dailyDrillQuizId = "daily_drill"

enum DailyDrill {
    /// One unsolved problem at the difficulty the learner has earned.
    static func pickProblem(day: Int64, problems: [PracticeProblem], solved: Set<String>) -> PracticeProblem? {
        let unsolved = problems.filter { !solved.contains($0.id) }
        guard !unsolved.isEmpty else { return nil }
        let solvedCount = problems.count { solved.contains($0.id) }
        let target: Difficulty = solvedCount < 10 ? .BEGINNER : solvedCount < 40 ? .INTERMEDIATE : .ADVANCED
        var candidates = unsolved.filter { $0.difficulty == target }
        if candidates.isEmpty { candidates = unsolved }
        var rng = KotlinRandom(long: day)
        let sorted = candidates.sorted { $0.id < $1.id }
        return sorted[rng.nextInt(sorted.count)]
    }

    /// Previously missed questions first, then questions no attempt has covered.
    static func pickQuestions(day: Int64, quizzes: [(topicId: String, quiz: Quiz)], attempts: [String: [QuizAttempt]],
                              count: Int = drillQuestionCount) -> [DrillQuestion] {
        guard !quizzes.isEmpty, count > 0 else { return [] }
        var missed: [DrillQuestion] = [], fresh: [DrillQuestion] = []
        for (quizId, quiz) in quizzes {
            let wrong = Set(attempts[quizId]?.first?.wrongIndices ?? [])
            for (i, q) in quiz.questions.enumerated() {
                let entry = DrillQuestion(quizId: quizId, index: i, question: q)
                if wrong.contains(i) { missed.append(entry) } else { fresh.append(entry) }
            }
        }
        var rng = KotlinRandom(long: day)
        var picked = Array(missed.kotlinShuffled(&rng).prefix(count))
        if picked.count < count { picked += fresh.kotlinShuffled(&rng).prefix(count - picked.count) }
        return picked
    }

    static func quiz(_ questions: [DrillQuestion]) -> Quiz {
        Quiz(id: dailyDrillQuizId, title: "Daily drill",
             description: "Mixed questions, weighted towards what you have missed before.",
             questions: questions.map(\.question))
    }
}

/// Today's drill and how far through it the learner is. Each step's completion is derived from
/// state the app already keeps, so nothing drifts out of sync with the real work.
struct DrillStatus {
    let problem: PracticeProblem?
    let questions: [DrillQuestion]
    let cardsWaiting: Int
    let recallDone: Bool
    let solveDone: Bool
    let drillDone: Bool

    var stepCount: Int { 3 }
    var doneCount: Int { [recallDone, solveDone, drillDone].filter { $0 }.count }
    var allDone: Bool { doneCount == stepCount }
}

extension AppStore {
    /// Builds (or restores) today's drill. The problem pick is persisted so solving it does not
    /// reroll the step; questions are deterministic in the day.
    func drillStatus(today: Int64 = todayEpochDay()) -> DrillStatus {
        let content = ContentStore.shared
        let questions = DailyDrill.pickQuestions(day: today, quizzes: content.quizzes, attempts: quizAttempts)
        let problem: PracticeProblem?
        if drillDay == today, let id = drillProblemId {
            problem = content.problem(id)
        } else {
            problem = DailyDrill.pickProblem(day: today, problems: content.problems, solved: solvedProblemIds)
            if let problem {
                // Deferred: this runs during view evaluation, which must not mutate observed state.
                Task { @MainActor in self.setDrillProblem(day: today, problemId: problem.id) }
            }
        }
        let counts = reviewCounts(today: today)
        return DrillStatus(
            problem: problem,
            questions: questions,
            cardsWaiting: counts.waiting,
            recallDone: counts.waiting == 0,
            solveDone: problem.map { solvedProblemIds.contains($0.id) } ?? true,
            drillDone: questions.isEmpty || (quizAttempts[dailyDrillQuizId] ?? []).contains { $0.day == today }
        )
    }
}

// MARK: - Weak spots

let weakSpotQuizId = "weak_spot_drill"
let weakSpotDrillSize = 10
let weakSpotMinAnswers = 3
let weakSpotThresholdPercent = 80

struct TagStat: Hashable {
    let tag: String
    let answered: Int
    let correct: Int
    var percent: Int { answered == 0 ? 0 : correct * 100 / answered }
}

enum WeakSpots {
    /// "Scenario · Heap" and "Heap" test the same pattern; the prefix is framing.
    static func normalizeTag(_ tag: String) -> String {
        for prefix in ["Scenario · ", "Picture · ", "Story · "] where tag.hasPrefix(prefix) {
            return String(tag.dropFirst(prefix.count))
        }
        return tag
    }

    /// Stored results, backfilled from the newest timed attempt per quiz.
    static func effectiveResults(_ stored: [String: Bool], attempts: [String: [QuizAttempt]]) -> [String: Bool] {
        var merged = stored
        for (quizId, quiz) in ContentStore.shared.quizzes {
            guard let newest = attempts[quizId]?.first else { continue }
            let wrong = Set(newest.wrongIndices)
            for i in 0..<min(newest.total, quiz.questions.count) {
                let key = questionKey(quizId, i)
                if merged[key] == nil { merged[key] = !wrong.contains(i) }
            }
        }
        return merged
    }

    static func tagStats(_ results: [String: Bool]) -> [TagStat] {
        var answered: [String: Int] = [:], correct: [String: Int] = [:]
        for (quizId, quiz) in ContentStore.shared.quizzes {
            for (i, q) in quiz.questions.enumerated() {
                guard let result = results[questionKey(quizId, i)] else { continue }
                let tag = normalizeTag(q.patternTag)
                answered[tag, default: 0] += 1
                if result { correct[tag, default: 0] += 1 }
            }
        }
        return answered.map { TagStat(tag: $0.key, answered: $0.value, correct: correct[$0.key] ?? 0) }
    }

    /// Weakest first; ties go to the tag with more evidence.
    static func weakest(_ stats: [TagStat], limit: Int = 3) -> [TagStat] {
        stats.filter { $0.answered >= weakSpotMinAnswers && $0.percent < weakSpotThresholdPercent }
            .sorted { ($0.percent, -$0.answered, $0.tag) < ($1.percent, -$1.answered, $1.tag) }
            .prefix(limit)
            .map { $0 }
    }

    /// Missed first, then unseen, then already-right — so a thin tag still fills the drill.
    static func pickQuestions(tags: Set<String>, results: [String: Bool], count: Int = weakSpotDrillSize) -> [DrillQuestion] {
        var missed: [DrillQuestion] = [], unseen: [DrillQuestion] = [], right: [DrillQuestion] = []
        for (quizId, quiz) in ContentStore.shared.quizzes {
            for (i, q) in quiz.questions.enumerated() where tags.contains(normalizeTag(q.patternTag)) {
                let entry = DrillQuestion(quizId: quizId, index: i, question: q)
                switch results[questionKey(quizId, i)] {
                case false?: missed.append(entry)
                case nil: unseen.append(entry)
                case true?: right.append(entry)
                }
            }
        }
        return Array((missed.shuffled() + unseen.shuffled() + right.shuffled()).prefix(count))
    }

    static func quiz(_ questions: [DrillQuestion], tags: [String]) -> Quiz {
        Quiz(id: weakSpotQuizId, title: "Weak-spot drill",
             description: "Questions from the patterns you miss most: \(tags.joined(separator: ", ")).",
             questions: questions.map(\.question))
    }
}
