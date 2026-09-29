import SwiftUI

// Port of BayesStoryLabs.kt: Naive Bayes (Gaussian, on a plane), Multinomial, Bernoulli, Complement and
// Categorical naive Bayes, Bayesian networks and MCMC as step-by-step storyboards: one figure in the card,
// the step's arithmetic under it, then chips and a headline, and a transport whose button names the next
// step ("Score “great”"). Every number is computed from the small fixed data below.

let bayesStoryTopicIds: Set<String> = [
    "naive_bayes", "multinomial_nb", "bernoulli_nb", "complement_nb", "categorical_nb", "bayesian_networks", "mcmc",
]

private let blueClass = SimColors.blue
private let pinkClass = CategoryAccents.pink

// MARK: - Scenes

private enum BnState { case idle, observed, question }

private enum BayesScene {
    /// Two classes on a plane with a query; the per-class bell curves along x and y once fitted.
    case gauss(showX: Bool, showY: Bool, predicted: Int?)
    /// A document's tokens over each class's word counts; `current` is the vocabulary column being scored.
    case counts(tokens: [StoryTone], current: Int?)
    /// Bernoulli: the document's present words, then one row per vocabulary word as it is scored.
    case presence(scored: Int, current: Int?)
    /// Complement: the two models' score cards, filled as each is run.
    case complement(multinomial: Bool, complement: Bool)
    /// Categorical: the row's four values, and the count table of the feature being looked up.
    case table(tones: [StoryTone], feature: Int?, highlight: Int?)
    /// The sprinkler network: node states and the value pill beside each node.
    case net(states: [BnState], pills: [String], pillTones: [StoryTone])
    /// A Metropolis chain over the crescent: its path so far, and the proposal in hand.
    case chain(upTo: Int, proposal: Int?, dotsOnly: Bool)
}

private struct BayesFrame {
    let headline: String
    let body: String
    let scene: BayesScene
    let action: String
    var formula: [String] = []
    var chips: [LabChip] = []
}

private struct BayesLab {
    let frames: [BayesFrame]
    var legend: [(color: Color, style: SwatchStyle, label: String)] = []
}

// MARK: - Formatting

/// Fixed decimals, rounded half away from zero the same way on both platforms; a minus is "−".
private func bx(_ v: Double, _ d: Int = 2) -> String {
    var p: Int64 = 1
    for _ in 0..<d { p *= 10 }
    let r = Int64((abs(v) * Double(p) + 0.5).rounded(.down))
    var body = "\(r / p)"
    if d > 0 {
        let frac = String(r % p)
        body += "." + String(repeating: "0", count: d - frac.count) + frac
    }
    return v < 0 && r != 0 ? "−" + body : body
}

private func quoted(_ word: String) -> String { "“\(word)”" }

// MARK: - Naive Bayes (Gaussian, 2D)

private struct GaussFit {
    let mean: Double
    let sd: Double
    func pdf(_ v: Double) -> Double { exp(-0.5 * pow((v - mean) / sd, 2)) / (sd * (2 * Double.pi).squareRoot()) }
}

private struct GaussData {
    let points: [(x: Double, y: Double, c: Int)]
    let query: (Double, Double)
    let fx: [GaussFit]
    let fy: [GaussFit]
}

private let gaussData: GaussData = {
    var random = KotlinRandom(11)
    func normal() -> Double {
        let u = max(random.nextDouble(), 1e-12)
        return (-2 * log(u)).squareRoot() * cos(2 * Double.pi * random.nextDouble())
    }
    var points: [(x: Double, y: Double, c: Int)] = []
    for _ in 0..<10 { let x = 3.0 + normal() * 1.1; points.append((x, 7.2 + normal() * 0.6, 0)) }
    for _ in 0..<10 { let x = 7.0 + normal() * 1.0; points.append((x, 4.4 + normal() * 1.2, 1)) }
    func fit(_ values: [Double]) -> GaussFit {
        let m = values.reduce(0, +) / Double(values.count)
        return GaussFit(mean: m, sd: (values.reduce(0) { $0 + ($1 - m) * ($1 - m) } / Double(values.count)).squareRoot())
    }
    return GaussData(points: points, query: (4.9, 6.3),
                     fx: (0...1).map { c in fit(points.filter { $0.c == c }.map(\.x)) },
                     fy: (0...1).map { c in fit(points.filter { $0.c == c }.map(\.y)) })
}()

private func naiveBayesLab() -> BayesLab {
    let d = gaussData
    let (qx, qy) = d.query
    let px = d.fx.map { $0.pdf(qx) }, py = d.fy.map { $0.pdf(qy) }
    let score = (0...1).map { 0.5 * px[$0] * py[$0] }
    let win = score[0] >= score[1] ? 0 : 1, lose = 1 - win
    let name = ["class 0", "class 1"]
    let xWin = px[0] >= px[1] ? 0 : 1, yWin = py[0] >= py[1] ? 0 : 1
    let formula = (0...1).map { c in "\(name[c]) ∝ 0.5 × \(bx(px[c])) × \(bx(py[c])) = \(bx(score[c], 3))" }
    let posterior = score[win] / (score[0] + score[1])
    return BayesLab(frames: [
        BayesFrame(headline: "Where does the {yellow query} belong?",
                   body: "Naive Bayes fits one bell curve per class and per feature, then multiplies what they say about the query.",
                   scene: .gauss(showX: false, showY: false, predicted: nil), action: "Fit x Curves"),
        BayesFrame(headline: "Along x, {\(name[xWin])'s curve} is higher at the query: \(bx(px[xWin])) against \(bx(px[1 - xWin])).",
                   body: "Each curve is a Gaussian fitted to one class's x values alone: a mean and a spread.",
                   scene: .gauss(showX: true, showY: false, predicted: nil), action: "Fit y Curves"),
        BayesFrame(headline: "Along y, {\(name[yWin])} is higher: \(bx(py[yWin])) against \(bx(py[1 - yWin])).",
                   body: "The dashed ovals are the two curves together. They stay axis-aligned, because the model never looks at x and y jointly.",
                   scene: .gauss(showX: true, showY: true, predicted: nil), action: "Multiply"),
        BayesFrame(headline: "Class \(win) wins, \(bx(score[win], 3)) to \(bx(score[lose], 3)).",
                   body: "Each factor is one bell curve read at the query's x or y. Multiplying them is the naive part: it treats x and y as independent.",
                   scene: .gauss(showX: true, showY: true, predicted: nil), action: "Predict", formula: formula),
        BayesFrame(headline: "Predicted {class \(win)}, with P = \(bx(posterior)) once the two scores are normalised.",
                   body: "Divide each score by their sum: \(bx(score[win], 3)) / (\(bx(score[0], 3)) + \(bx(score[1], 3))). The priors were equal, so the curves decided it.",
                   scene: .gauss(showX: true, showY: true, predicted: win), action: "Start Over", formula: formula),
    ], legend: [(blueClass, .dot, "Class 0"), (pinkClass, .dot, "Class 1"), (SimColors.active, .dot, "Query")])
}

// MARK: - Multinomial

private let nbVocab = ["goal", "match", "team", "vote", "policy", "great"]
private let sportsCounts = [5, 4, 2, 0, 0, 4]
private let politicsCounts = [0, 0, 0, 5, 3, 3]
private let nbDoc = ["goal", "goal", "great"]

private func multinomialLab() -> BayesLab {
    let sTotal = sportsCounts.reduce(0, +), pTotal = politicsCounts.reduce(0, +)
    let v = nbVocab.count
    func logP(_ counts: [Int], _ total: Int, _ word: String) -> Double { log(Double(counts[nbVocab.firstIndex(of: word)!] + 1) / Double(total + v)) }
    let priors = [log(3.0 / 5), log(2.0 / 5)]
    func terms(_ k: Int) -> [[Double]] { nbDoc.prefix(k).map { [logP(sportsCounts, sTotal, $0), logP(politicsCounts, pTotal, $0)] } }
    func line(_ label: String, _ c: Int, _ k: Int) -> String {
        let parts = [priors[c]] + terms(k).map { $0[c] }
        let sum = parts.reduce(0, +)
        return parts.count == 1 ? "\(label) \(bx(sum))" : "\(label) \(parts.map { bx($0) }.joined(separator: " ")) = \(bx(sum))"
    }
    func formula(_ k: Int) -> [String] { [line("sports", 0, k), line("politics", 1, k)] }
    func total(_ k: Int, _ c: Int) -> Double { priors[c] + terms(k).reduce(0) { $0 + $1[c] } }
    func tokens(_ current: Int?) -> [StoryTone] {
        nbDoc.indices.map { i in
            guard let current else { return .idle }
            return i < current ? .done : i == current ? .active : .idle
        }
    }
    let gap2 = total(2, 0) - total(2, 1), gap3 = total(3, 0) - total(3, 1)
    return BayesLab(frames: [
        BayesFrame(headline: "A document is just its word counts: {goal ×2}, great ×1.",
                   body: "Multinomial NB scores each class by how often that class used each word, one factor per occurrence.",
                   scene: .counts(tokens: tokens(nil), current: nil), action: "Count Words"),
        BayesFrame(headline: "Sports has {\(sTotal) tokens} of training text, politics \(pTotal).",
                   body: "These counts are the whole model: P(word | class) is a word's share of its class's tokens.",
                   scene: .counts(tokens: tokens(nil), current: nil), action: "Add Priors"),
        BayesFrame(headline: "Start from the priors: {3 of 5} training documents were sports.",
                   body: "Scores are summed in logs, so a long document never underflows to zero.",
                   scene: .counts(tokens: tokens(nil), current: nil), action: "Score \(quoted("goal"))", formula: formula(0)),
        BayesFrame(headline: "Sports used {\(quoted("goal"))} \(sportsCounts[0]) times in \(sTotal) tokens; politics never did.",
                   body: "Smoothing adds 1 to every count: (\(sportsCounts[0]) + 1) / (\(sTotal) + \(v)) for sports, 1/\(pTotal + v) for politics, so no word can zero a class out.",
                   scene: .counts(tokens: tokens(0), current: 0), action: "Score \(quoted("goal"))", formula: formula(1)),
        BayesFrame(headline: "The second {\(quoted("goal"))} is scored again. Counts add up; they don't just flag presence.",
                   body: "Politics never saw \(quoted("goal")), so smoothing gives it 1/\(pTotal + v) and it falls \(bx(gap2, 1)) behind.",
                   scene: .counts(tokens: tokens(1), current: 0), action: "Score \(quoted("great"))", formula: formula(2)),
        BayesFrame(headline: "{\(quoted("great"))} is nearly even: \(sportsCounts[5] + 1)/\(sTotal + v) for sports, \(politicsCounts[5] + 1)/\(pTotal + v) for politics.",
                   body: "Politics stays \(bx(gap3, 1)) behind. One even word can't undo two lopsided ones.",
                   scene: .counts(tokens: tokens(2), current: 5), action: "Predict", formula: formula(3)),
        BayesFrame(headline: "{m:Sports} wins, \(bx(total(3, 0))) to \(bx(total(3, 1))).",
                   body: "Both uses of \(quoted("goal")) counted. Bernoulli NB would count that word once.",
                   scene: .counts(tokens: tokens(3), current: nil), action: "Start Over", formula: formula(3)),
    ])
}

// MARK: - Bernoulli

private let bernSports = [0.8, 0.6, 0.6, 0.2, 0.2, 0.6]
private let bernPolitics = [0.25, 0.25, 0.25, 0.75, 0.75, 0.5]
private let bernPresent = [true, false, false, false, false, true]

private func bernFactor(_ k: Int, _ c: Int) -> Double {
    let p = c == 0 ? bernSports[k] : bernPolitics[k]
    return bernPresent[k] ? p : 1 - p
}

private func bernLog(_ scored: Int, _ c: Int) -> Double { log(c == 0 ? 0.6 : 0.4) + (0..<scored).reduce(0) { $0 + log(bernFactor($1, c)) } }

private func bernoulliLab() -> BayesLab {
    func logLine(_ k: Int) -> String { "log sports {v:\(bx(bernLog(k, 0)))} log politics \(bx(bernLog(k, 1)))" }
    var frames = [
        BayesFrame(headline: "Bernoulli sees a {set of words}: goal is present, so is great.",
                   body: "The second \(quoted("goal")) changes nothing here. Every other vocabulary word counts as absent.",
                   scene: .presence(scored: 0, current: nil), action: "Score \(quoted(nbVocab[0]))",
                   formula: ["log P(sports) = −0.51, log P(politics) = −0.92", logLine(0)]),
    ]
    for k in nbVocab.indices {
        let w = nbVocab[k], s = bernSports[k], p = bernPolitics[k], present = bernPresent[k]
        frames.append(BayesFrame(
            headline: present ? "{\(quoted(w))} is present: sports keeps \(bx(s)), politics \(bx(p))."
                : "{\(quoted(w))} is absent, and Bernoulli counts that as evidence.",
            body: present ? "A present word works as in multinomial, but it counts once however often it appears."
                : "Sports keeps 1 − \(bx(s)) = \(bx(1 - s)); politics keeps 1 − \(bx(p)) = \(bx(1 - p)). Multinomial would skip this term.",
            scene: .presence(scored: k + 1, current: k),
            action: k + 1 < nbVocab.count ? "Score \(quoted(nbVocab[k + 1]))" : "Add Up",
            formula: [present ? "present → × P(\(w) | class)" : "absent → × (1 − P(\(w) | class))", logLine(k + 1)]))
    }
    let n = nbVocab.count
    let absentShift = nbVocab.indices.filter { !bernPresent[$0] }.reduce(0) { $0 + log(bernFactor($1, 0)) - log(bernFactor($1, 1)) }
    let gap = bernLog(n, 0) - bernLog(n, 1)
    frames.append(BayesFrame(
        headline: "{m:Sports} wins, \(bx(bernLog(n, 0))) to \(bx(bernLog(n, 1))).",
        body: "Every one of the \(n) vocabulary words voted, present or not.",
        scene: .presence(scored: n, current: nil), action: "Compare",
        formula: ["sum over all \(n) words, present and absent", logLine(n)]))
    frames.append(BayesFrame(
        headline: "The four absent words moved the gap by {\(bx(absentShift))} toward \(absentShift >= 0 ? "sports" : "politics").",
        body: "Without them sports would lead by \(bx(gap - absentShift)), not \(bx(gap)). Bernoulli suits short texts, where what is missing says as much as what is there.",
        scene: .presence(scored: n, current: nil), action: "Start Over",
        formula: ["absent words: Σ log(1 − P) = \(bx(absentShift)) in sports' favour", logLine(n)]))
    return BayesLab(frames: frames)
}

// MARK: - Complement

private let compVocab = ["goal", "match", "great", "vote", "policy", "law"]
private let compSports = [3, 3, 0, 0, 0, 0]
private let compPolitics = [1, 0, 8, 11, 8, 4]
private let compDoc = ["goal", "great"]

private func compLog(_ counts: [Int], _ word: String) -> Double {
    log(Double(counts[compVocab.firstIndex(of: word)!] + 1) / Double(counts.reduce(0, +) + compVocab.count))
}

private let compMultinomial = [
    log(0.2) + compDoc.reduce(0) { $0 + compLog(compSports, $1) },
    log(0.8) + compDoc.reduce(0) { $0 + compLog(compPolitics, $1) },
]

/// Each class scored with the other class's counts; the worst fit to the rest wins.
private let compComplement = [
    compDoc.reduce(0) { $0 + compLog(compPolitics, $1) },
    compDoc.reduce(0) { $0 + compLog(compSports, $1) },
]

private func complementLab() -> BayesLab {
    let chips = [LabChip(key: "sports", value: "2 docs · 6 tok"), LabChip(key: "politics", value: "8 docs · 32 tok")]
    let g = compLog(compPolitics, "goal"), gr = compLog(compPolitics, "great")
    return BayesLab(frames: [
        BayesFrame(headline: "Sports has {2 documents}, politics 8, and the test document is sports.",
                   body: "With so little sports text, every sports estimate rests on 6 tokens.",
                   scene: .complement(multinomial: false, complement: false), action: "Score Multinomial", chips: chips),
        BayesFrame(headline: "Multinomial calls it {w:politics}: sports has only 6 tokens and never saw \(quoted("great")).",
                   body: "Smoothing gives \(quoted("great")) 1/12 in sports, and the prior, 0.2 against 0.8, piles on.",
                   scene: .complement(multinomial: true, complement: false), action: "Score Complement",
                   formula: ["politics: log 0.8 + log P(goal | pol) + log P(great | pol)",
                             "= \(bx(log(0.8))) \(bx(g)) \(bx(gr)) = {v:\(bx(compMultinomial[1]))}"],
                   chips: chips),
        BayesFrame(headline: "Complement NB flips it to {m:sports}: it asks which class the rest fits worst.",
                   body: "Complement NB scores each class with the other classes' counts. Politics' 32 tokens now estimate sports' score, and sports wins.",
                   scene: .complement(multinomial: true, complement: true), action: "Predict",
                   formula: ["sports: log P(goal | pol) + log P(great | pol)", "= \(bx(g)) \(bx(gr)) = {v:\(bx(compComplement[0]))}"],
                   chips: chips),
        BayesFrame(headline: "{m:Sports} is right. Complement NB is steadier when the classes are unbalanced.",
                   body: "Each estimate now rests on the many tokens outside a class, not the few inside it.",
                   scene: .complement(multinomial: true, complement: true), action: "Start Over", chips: chips),
    ])
}

// MARK: - Categorical

private struct CatFeature {
    let name: String, value: String, adjective: String
    let levels: [String]
    let yes: [Int], no: [Int]
}

private let catFeatures = [
    CatFeature(name: "Outlook", value: "sunny", adjective: "sunny", levels: ["sunny", "overcast", "rain"], yes: [2, 4, 3], no: [3, 0, 2]),
    CatFeature(name: "Temp", value: "cool", adjective: "cool", levels: ["hot", "mild", "cool"], yes: [2, 4, 3], no: [2, 2, 1]),
    CatFeature(name: "Humidity", value: "high", adjective: "humid", levels: ["high", "normal"], yes: [3, 6], no: [4, 1]),
    CatFeature(name: "Windy", value: "true", adjective: "windy", levels: ["false", "true"], yes: [6, 3], no: [2, 3]),
]

private func catProducts(_ k: Int) -> (Double, Double) {
    var yes = 9.0 / 14, no = 5.0 / 14
    for f in catFeatures.prefix(k) {
        let i = f.levels.firstIndex(of: f.value)!
        yes *= Double(f.yes[i]) / 9
        no *= Double(f.no[i]) / 5
    }
    return (yes, no)
}

private func categoricalLab() -> BayesLab {
    func tones(_ current: Int?, _ done: Int) -> [StoryTone] {
        catFeatures.indices.map { $0 == current ? .active : $0 < done ? .done : .idle }
    }
    func chips(_ k: Int) -> [LabChip] {
        let (y, n) = catProducts(k)
        return [LabChip(key: "yes", value: bx(y, 4), tint: .path), LabChip(key: "no", value: bx(n, 4), tint: .answer)]
    }
    var frames = [
        BayesFrame(headline: "Classify one day: {sunny, cool, high, true}.",
                   body: "Each feature is a category, not a number. Categorical NB keeps one count table per feature.",
                   scene: .table(tones: tones(nil, 0), feature: nil, highlight: nil), action: "Add Priors"),
        BayesFrame(headline: "Start from the priors: {9 of 14} days were \(quoted("yes")).",
                   body: "Each feature's table then multiplies in P(value | class).",
                   scene: .table(tones: tones(nil, 0), feature: nil, highlight: nil), action: "Look Up \(catFeatures[0].name)", chips: chips(0)),
    ]
    for (k, f) in catFeatures.enumerated() {
        let i = f.levels.firstIndex(of: f.value)!
        let yesShare = Double(f.yes[i]) / 9, noShare = Double(f.no[i]) / 5
        let first = noShare > yesShare ? "\(f.no[i]) of 5 \(quoted("no")) days" : "\(f.yes[i]) of 9 \(quoted("yes")) days"
        let second = noShare > yesShare ? "\(f.yes[i]) of 9 \(quoted("yes")) days" : "\(f.no[i]) of 5 \(quoted("no")) days"
        let body: String = switch k {
        case 0, 2: "Each feature has its own count table, so \(quoted(f.value)) is a label to look up, never a number to compare."
        case 1: "This one favours \(yesShare > noShare ? "yes" : "no"). The chips are the running products, prior included."
        default: "The last factor. Both products are now complete."
        }
        frames.append(BayesFrame(
            headline: "\(f.name) = {\(f.value)}: \(first) were \(f.adjective), against \(second).",
            body: body,
            scene: .table(tones: tones(k, k), feature: k, highlight: i),
            action: k + 1 < catFeatures.count ? "Look Up \(catFeatures[k + 1].name)" : "Compare",
            formula: ["P(\(f.value) | yes) = \(f.yes[i])/9 = \(bx(yesShare))", "P(\(f.value) | no) = \(f.no[i])/5 = \(bx(noShare))"],
            chips: chips(k + 1)))
    }
    let (y, n) = catProducts(catFeatures.count)
    let winner = n > y ? "No" : "Yes"
    frames.append(BayesFrame(
        headline: "{m:\(winner)} wins, \(bx(max(y, n), 4)) to \(bx(min(y, n), 4)).",
        body: "Normalised, that is P(\(winner.lowercased())) = \(bx(max(y, n) / (y + n))). Every factor came straight from a count table.",
        scene: .table(tones: tones(nil, catFeatures.count), feature: nil, highlight: nil), action: "Check Zero Counts",
        chips: chips(catFeatures.count)))
    frames.append(BayesFrame(
        headline: "A value never seen with a class gives a {w:0 count}, and one zero wipes out the whole product.",
        body: "Overcast never occurred on a \(quoted("no")) day: 0/5. Categorical NB adds α to every count so an unseen value only lowers the score.",
        scene: .table(tones: tones(nil, catFeatures.count), feature: 0, highlight: 1), action: "Start Over",
        formula: ["P(overcast | no) = 0/5 = 0.00", "smoothed: (0 + 1) / (5 + 3) = 0.13"],
        chips: chips(catFeatures.count)))
    return BayesLab(frames: frames)
}

// MARK: - Bayesian network

private let netNames = ["Cloudy", "Sprinkler", "Rain", "WetGrass"]

/// P(query = true | evidence) on the sprinkler network, by enumerating all 16 worlds.
private func netPosterior(_ query: Int, _ evidence: [Int: Bool]) -> Double {
    var num = 0.0, den = 0.0
    for mask in 0..<16 {
        let v = (0..<4).map { (mask >> $0) & 1 == 1 }
        if evidence.contains(where: { v[$0.key] != $0.value }) { continue }
        let ps = v[0] ? 0.1 : 0.5, pr = v[0] ? 0.8 : 0.2
        let pw = v[1] && v[2] ? 0.99 : v[1] || v[2] ? 0.9 : 0
        let p = 0.5 * (v[1] ? ps : 1 - ps) * (v[2] ? pr : 1 - pr) * (v[3] ? pw : 1 - pw)
        den += p
        if v[query] { num += p }
    }
    return num / den
}

private func bayesNetLab() -> BayesLab {
    let prior = (0..<4).map { netPosterior($0, [:]) }
    let wet = (0..<4).map { netPosterior($0, [3: true]) }
    let both = (0..<4).map { netPosterior($0, [3: true, 1: true]) }
    func scene(_ values: [Double], _ observed: Set<Int>, _ question: Int?) -> BayesScene {
        .net(states: (0..<4).map { observed.contains($0) ? .observed : $0 == question ? .question : .idle },
             pills: (0..<4).map { observed.contains($0) ? "= true" : bx(values[$0]) },
             pillTones: (0..<4).map { $0 == question ? .answer : .idle })
    }
    return BayesLab(frames: [
        BayesFrame(headline: "Before any evidence, P(Rain) is {\(bx(prior[2]))}.",
                   body: "Each arrow is a conditional table: rain depends on cloud, wet grass on the sprinkler and the rain.",
                   scene: scene(prior, [], nil), action: "Observe WetGrass", chips: [LabChip(key: "P(Rain)", value: bx(prior[2]))]),
        BayesFrame(headline: "The grass is wet: {p:WetGrass} is now observed.",
                   body: "An observed node is fixed. Every other probability has to be recomputed given it.",
                   scene: scene(prior, [3], nil), action: "Update Sprinkler", chips: [LabChip(key: "P(Rain)", value: bx(prior[2]))]),
        BayesFrame(headline: "Wet grass raises P(Sprinkler) from \(bx(prior[1])) to {v:\(bx(wet[1]))}.",
                   body: "Evidence flows against the arrows: a sprinkler is one way the grass gets wet.",
                   scene: .net(states: [.idle, .question, .idle, .observed], pills: [bx(prior[0]), bx(wet[1]), bx(prior[2]), "= true"],
                               pillTones: [.idle, .answer, .idle, .idle]),
                   action: "Update Rain",
                   chips: [LabChip(key: "P(Spr)", value: bx(prior[1])), LabChip(key: "P(Spr | wet)", value: bx(wet[1]), tint: .answer)]),
        BayesFrame(headline: "Wet grass raises P(Rain) from \(bx(prior[2])) to {v:\(bx(wet[2]))}.",
                   body: "Evidence flows against the arrows. Sprinkler rises too, from \(bx(prior[1])) to \(bx(wet[1])), since either could have wet the grass.",
                   scene: scene(wet, [3], 2), action: "Observe Sprinkler",
                   chips: [LabChip(key: "P(Rain)", value: bx(prior[2])), LabChip(key: "P(Rain | wet)", value: bx(wet[2]), tint: .answer)]),
        BayesFrame(headline: "The sprinkler was on, and P(Rain) falls back to {v:\(bx(both[2]))}.",
                   body: "The sprinkler already explains the wet grass, so rain is no longer needed. This is explaining away.",
                   scene: scene(both, [1, 3], 2), action: "Summary",
                   chips: [LabChip(key: "P(Rain | wet)", value: bx(wet[2])), LabChip(key: "P(Rain | wet, spr)", value: bx(both[2]), tint: .answer)]),
        BayesFrame(headline: "{Rain and Sprinkler} were independent causes until wet grass tied them together.",
                   body: "Observing a common effect makes its causes compete. Cloudy moved too: \(bx(prior[0])), then \(bx(wet[0])), then \(bx(both[0])).",
                   scene: scene(both, [1, 3], nil), action: "Start Over",
                   chips: [LabChip(key: "P(Rain)", value: "\(bx(prior[2])) → \(bx(wet[2])) → \(bx(both[2]))")]),
    ], legend: [(SimColors.blue, .dot, "Observed"), (SimColors.active, .dot, "In question"), (SimColors.answer, .dot, "Posterior")])
}

// MARK: - MCMC

private let mcmcSteps = 500

/// An unnormalised crescent: a parabola's neighbourhood, fading along x.
private func crescent(_ x: Double, _ y: Double) -> Double {
    let r = (y - (0.85 * x * x - 1.1)) / 0.32
    return exp(-0.5 * r * r - 0.5 * (x / 1.25) * (x / 1.25))
}

private struct McmcStep { let fromX, fromY, toX, toY, alpha, u: Double; let accepted: Bool }

private let mcmcRun: [McmcStep] = {
    var random = KotlinRandom(21)
    func normal() -> Double {
        let u = max(random.nextDouble(), 1e-12)
        return (-2 * log(u)).squareRoot() * cos(2 * Double.pi * random.nextDouble())
    }
    var x = -1.55, y = 0.95
    return (0..<mcmcSteps).map { _ in
        let nx = x + normal() * 0.35
        let ny = y + normal() * 0.35
        let alpha = min(1, crescent(nx, ny) / crescent(x, y))
        let u = random.nextDouble()
        let ok = u < alpha
        let step = McmcStep(fromX: x, fromY: y, toX: nx, toY: ny, alpha: alpha, u: u, accepted: ok)
        if ok { x = nx; y = ny }
        return step
    }
}()

private func mcmcLab() -> BayesLab {
    let run = mcmcRun
    let uphill = run.firstIndex { $0.alpha >= 1 } ?? 0
    let downhill = (60..<run.count).first { run[$0].alpha >= 0.15 && run[$0].alpha <= 0.6 } ?? 60
    func accepted(_ n: Int) -> Int { run.prefix(n).filter(\.accepted).count }
    func chips(_ n: Int) -> [LabChip] {
        [LabChip(key: "iteration", value: "\(n)"),
         LabChip(key: "accepted", value: "\(accepted(n))/\(n)", tint: .path),
         LabChip(key: "rate", value: n == 0 ? "—" : "\(Int((100 * Double(accepted(n)) / Double(n)).rounded()))%")]
    }
    func alphaLine(_ i: Int) -> String {
        let s = run[i]
        return "α = min(1, p(x′) / p(x)) = min(1, \(bx(crescent(s.toX, s.toY))) / \(bx(crescent(s.fromX, s.fromY)))) = {v:\(bx(s.alpha))}"
    }
    let d = run[downhill]
    let rate = Int((100 * Double(accepted(run.count)) / Double(run.count)).rounded())
    return BayesLab(frames: [
        BayesFrame(headline: "The chain starts at one point on the {crescent}.",
                   body: "Metropolis never needs the normalising constant. It only compares p at two points.",
                   scene: .chain(upTo: 0, proposal: nil, dotsOnly: false), action: "Propose", chips: chips(0)),
        BayesFrame(headline: "This proposal is {m:uphill}, so α = 1 and it is always accepted.",
                   body: "A proposal is a small random jump from where the chain is now.",
                   scene: .chain(upTo: uphill, proposal: uphill, dotsOnly: false), action: "Test Proposal",
                   formula: [alphaLine(uphill)], chips: chips(uphill)),
        BayesFrame(headline: "Accepted: the chain {moves} to the proposal.",
                   body: "An uphill move is never refused. Downhill ones are where it gets interesting.",
                   scene: .chain(upTo: uphill + 1, proposal: nil, dotsOnly: false), action: "Run to \(downhill)", chips: chips(uphill + 1)),
        BayesFrame(headline: "After \(downhill) proposals, \(accepted(downhill)) were accepted.",
                   body: "Hollow circles are refused proposals. When one is refused the chain stays put, and that repeat counts as a sample.",
                   scene: .chain(upTo: downhill, proposal: nil, dotsOnly: false), action: "Propose", chips: chips(downhill)),
        BayesFrame(headline: "This proposal is {downhill}, so it is accepted with probability \(bx(d.alpha)).",
                   body: "Taking some downhill moves lets the chain cover the whole crescent. Uphill-only moves would leave it stuck at the peak.",
                   scene: .chain(upTo: downhill, proposal: downhill, dotsOnly: false), action: "Test Proposal",
                   formula: [alphaLine(downhill)], chips: chips(downhill)),
        BayesFrame(headline: d.accepted ? "u = \(bx(d.u)) is below α, so the downhill move is {m:accepted}."
                       : "u = \(bx(d.u)) is above α, so the proposal is {w:refused} and the chain stays.",
                   body: "Over many steps each region is visited in proportion to its probability.",
                   scene: .chain(upTo: downhill + 1, proposal: nil, dotsOnly: false), action: "Run \(mcmcSteps) Steps",
                   formula: ["u = \(bx(d.u)) \(d.accepted ? "<" : "≥") α = \(bx(d.alpha))"], chips: chips(downhill + 1)),
        BayesFrame(headline: "After \(mcmcSteps) steps the samples {trace the crescent}.",
                   body: "Acceptance rate \(rate)%: a proposal step of 0.35 keeps it in the 20–50% range samplers usually aim for.",
                   scene: .chain(upTo: run.count, proposal: nil, dotsOnly: true), action: "Start Over", chips: chips(run.count)),
    ], legend: [(SimColors.blue, .dot, "Chain"), (SimColors.active, .dot, "Proposal"), (SimColors.grey, .ring, "Rejected")])
}

// MARK: - Lab

private func bayesLab(_ topicId: String) -> BayesLab {
    switch topicId {
    case "multinomial_nb": multinomialLab()
    case "bernoulli_nb": bernoulliLab()
    case "complement_nb": complementLab()
    case "categorical_nb": categoricalLab()
    case "bayesian_networks": bayesNetLab()
    case "mcmc": mcmcLab()
    default: naiveBayesLab()
    }
}

struct BayesStoryLab: View {
    private let lab: BayesLab
    @State private var playback: PlaybackState

    init(topicId: String) {
        let lab = bayesLab(topicId)
        self.lab = lab
        let state = PlaybackState(stepCount: lab.frames.count, speedMs: 1000)
        #if DEBUG
        // `simctl launch … -openStep 4` opens on that step (1-based), for a screenshot pass.
        let step = UserDefaults.standard.integer(forKey: "openStep")
        if step > 0 { state.index = min(step, lab.frames.count) - 1 }
        #endif
        _playback = State(initialValue: state)
    }

    var body: some View {
        let frames = lab.frames
        let frame = frames[min(playback.index, frames.count - 1)]
        VStack(alignment: .leading, spacing: 0) {
            LabCard {
                switch frame.scene {
                case let .gauss(showX, showY, predicted): GaussView(showX: showX, showY: showY, predicted: predicted)
                case let .counts(tokens, current): CountView(tokens: tokens, current: current)
                case let .presence(scored, current): PresenceView(scored: scored, current: current)
                case let .complement(m, c): ComplementView(multinomial: m, complement: c)
                case let .table(tones, feature, highlight): TableView(tones: tones, feature: feature, highlight: highlight)
                case let .net(states, pills, pillTones): NetView(states: states, pills: pills, pillTones: pillTones)
                case let .chain(upTo, proposal, dotsOnly): ChainView(upTo: upTo, proposal: proposal, dotsOnly: dotsOnly)
                }
                if !frame.formula.isEmpty {
                    if case .gauss = frame.scene { GaussFormula(lines: frame.formula).padding(.top, 12) }
                    else { BayesFormula(lines: frame.formula).padding(.top, 12) }
                }
                if !lab.legend.isEmpty { StoryLegendRow(items: lab.legend).padding(.top, 14) }
            }
            if !frame.chips.isEmpty { LabChips(chips: frame.chips).padding(.top, 16) }
            LabStoryNarration(headline: frame.headline, body: frame.body).padding(.top, 16)
            PlaybackTransport(state: playback, captions: frames.map { storyPlain($0.headline) },
                              action: { frames[min(max($0, 0), frames.count - 1)].action })
        }
    }
}

// MARK: - Rendering

private struct Stage: ViewModifier {
    func body(content: Content) -> some View {
        content.background(Color.black.opacity(0.16)).clipShape(RoundedRectangle(cornerRadius: 12))
    }
}

private struct BayesFormula: View {
    let lines: [String]
    @Environment(\.palette) private var palette

    var body: some View {
        VStack(spacing: 2) {
            ForEach(lines.indices, id: \.self) { i in
                storyText(lines[i], palette).font(AppFont.mono(13)).multilineTextAlignment(.center)
            }
        }
        .foregroundStyle(palette.onSurface.opacity(0.85))
        .lineSpacing(3)
        .frame(maxWidth: .infinity)
        .padding(.vertical, 10).padding(.horizontal, 12)
        .background(SimColors.tint, in: RoundedRectangle(cornerRadius: 10))
    }
}

/// The two class scores, each class name in its colour and the larger score in violet.
private struct GaussFormula: View {
    let lines: [String]
    @Environment(\.palette) private var palette

    var body: some View {
        let values = lines.map { Double($0.components(separatedBy: "= ").last?.replacingOccurrences(of: "−", with: "-") ?? "") ?? 0 }
        let best = values.indices.max { values[$0] < values[$1] } ?? 0
        VStack(spacing: 2) {
            ForEach(lines.indices, id: \.self) { i in
                let line = lines[i]
                let name = line.components(separatedBy: " ∝").first ?? ""
                let tail = String(line.dropFirst(name.count))
                let eq = tail.range(of: "= ", options: .backwards)!
                (Text(name).foregroundColor(i == 0 ? Color(hex: 0x93B4F8) : Color(hex: 0xF472B6))
                    + Text(tail[..<eq.upperBound])
                    + Text(tail[eq.upperBound...]).foregroundColor(i == best ? StoryTone.answer.ink(palette) : palette.onSurface.opacity(0.85)))
                    .font(AppFont.mono(13))
            }
        }
        .foregroundStyle(palette.onSurface.opacity(0.85))
        .frame(maxWidth: .infinity)
        .padding(.vertical, 10).padding(.horizontal, 12)
        .background(SimColors.tint, in: RoundedRectangle(cornerRadius: 10))
    }
}

private struct GaussView: View {
    let showX: Bool
    let showY: Bool
    let predicted: Int?
    @Environment(\.palette) private var palette

    var body: some View {
        Canvas { ctx, size in
            let d = gaussData
            let lo = 0.0, hi = 10.0
            func px(_ x: Double) -> CGFloat { CGFloat((x - lo) / (hi - lo)) * size.width }
            func py(_ y: Double) -> CGFloat { size.height - CGFloat((y - lo) / (hi - lo)) * size.height }
            let (qx, qy) = d.query
            let colors = [blueClass, pinkClass]
            let dash = StrokeStyle(lineWidth: 1.5, dash: [4, 4])
            // Bells along the bottom (x) and the left edge (y), each scaled to its own peak.
            if showX {
                let peak = d.fx.map { $0.pdf($0.mean) }.max() ?? 1
                for c in 0...1 {
                    var path = Path()
                    for i in 0...120 {
                        let x = lo + (hi - lo) * Double(i) / 120
                        let pt = CGPoint(x: px(x), y: size.height - CGFloat(d.fx[c].pdf(x) / peak) * size.height * 0.22)
                        if i == 0 { path.move(to: pt) } else { path.addLine(to: pt) }
                    }
                    ctx.stroke(path, with: .color(colors[c]), lineWidth: 2)
                }
                var v = Path(); v.move(to: CGPoint(x: px(qx), y: py(qy))); v.addLine(to: CGPoint(x: px(qx), y: size.height))
                ctx.stroke(v, with: .color(SimColors.active), style: dash)
            }
            if showY {
                let peak = d.fy.map { $0.pdf($0.mean) }.max() ?? 1
                for c in 0...1 {
                    var path = Path()
                    for i in 0...120 {
                        let y = lo + (hi - lo) * Double(i) / 120
                        let pt = CGPoint(x: CGFloat(d.fy[c].pdf(y) / peak) * size.width * 0.12, y: py(y))
                        if i == 0 { path.move(to: pt) } else { path.addLine(to: pt) }
                    }
                    ctx.stroke(path, with: .color(colors[c]), lineWidth: 2)
                    // The class's two curves together: a 1.5σ oval, axis-aligned.
                    let rx = CGFloat(d.fx[c].sd * 1.5 / (hi - lo)) * size.width
                    let ry = CGFloat(d.fy[c].sd * 1.5 / (hi - lo)) * size.height
                    ctx.stroke(Path(ellipseIn: CGRect(x: px(d.fx[c].mean) - rx, y: py(d.fy[c].mean) - ry, width: rx * 2, height: ry * 2)),
                               with: .color(colors[c]), style: dash)
                }
                var h = Path(); h.move(to: CGPoint(x: px(qx), y: py(qy))); h.addLine(to: CGPoint(x: 0, y: py(qy)))
                ctx.stroke(h, with: .color(SimColors.active), style: dash)
            }
            for p in d.points {
                let c = CGPoint(x: px(p.x), y: py(p.y))
                ctx.fill(Path(ellipseIn: CGRect(x: c.x - 5.5, y: c.y - 5.5, width: 11, height: 11)), with: .color(colors[p.c]))
            }
            let q = CGPoint(x: px(qx), y: py(qy))
            let dot = Path(ellipseIn: CGRect(x: q.x - 7, y: q.y - 7, width: 14, height: 14))
            ctx.fill(dot, with: .color(SimColors.active))
            ctx.stroke(dot, with: .color(palette.surface), lineWidth: 1.5)
            if let predicted {
                ctx.stroke(Path(ellipseIn: CGRect(x: q.x - 12, y: q.y - 12, width: 24, height: 24)), with: .color(colors[predicted]), lineWidth: 2.5)
            }
        }
        .aspectRatio(1.45, contentMode: .fit)
        .modifier(Stage())
    }
}

private struct CardLabel: View {
    let text: String
    @Environment(\.palette) private var palette
    var body: some View { Text(text).font(AppFont.sans(14)).foregroundStyle(palette.muted) }
}

/// A word or value tile, tinted in its state: green once used, yellow while scored, grey still to come.
private struct WordTile: View {
    let text: String
    let tone: StoryTone
    var caption: String? = nil
    @Environment(\.palette) private var palette

    var body: some View {
        let fill = tone == .idle ? SimColors.tint : tone.color.opacity(0.2)
        let ink = tone == .idle ? palette.onSurface.opacity(0.85) : tone.ink(palette)
        VStack(spacing: 1) {
            Text(text).font(AppFont.sans(16, .semibold)).foregroundStyle(ink).lineLimit(1)
            if let caption { Text(caption).font(AppFont.sans(11)).foregroundStyle(ink.opacity(0.75)).lineLimit(1) }
        }
        .frame(maxWidth: .infinity)
        .frame(height: caption == nil ? 52 : 50)
        .background(fill, in: RoundedRectangle(cornerRadius: 10))
    }
}

private struct CountView: View {
    let tokens: [StoryTone]
    let current: Int?

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            CardLabel(text: "Document to classify")
            HStack(spacing: 8) {
                ForEach(nbDoc.indices, id: \.self) { i in WordTile(text: nbDoc[i], tone: tokens[i]) }
            }
            .padding(.top, 8)
            CountBars(title: "Counts · sports · \(sportsCounts.reduce(0, +)) tokens", counts: sportsCounts, color: blueClass, current: current).padding(.top, 14)
            CountBars(title: "Counts · politics · \(politicsCounts.reduce(0, +)) tokens", counts: politicsCounts, color: pinkClass, current: current).padding(.top, 12)
        }
    }
}

private struct CountBars: View {
    let title: String
    let counts: [Int]
    let color: Color
    let current: Int?
    @Environment(\.palette) private var palette

    var body: some View {
        let top = max(counts.max() ?? 1, 1)
        VStack(alignment: .leading, spacing: 6) {
            CardLabel(text: title)
            HStack(spacing: 4) {
                ForEach(counts.indices, id: \.self) { i in
                    let on = i == current
                    let n = counts[i]
                    VStack(spacing: 0) {
                        Text("\(n)").font(AppFont.mono(13)).foregroundStyle(palette.muted)
                        ZStack(alignment: .bottom) {
                            Color.clear
                            if n > 0 {
                                RoundedRectangle(cornerRadius: 4).fill(color).frame(width: 30, height: 40 * CGFloat(n) / CGFloat(top))
                            } else {
                                RoundedRectangle(cornerRadius: 1).fill(color.opacity(0.45)).frame(width: 30, height: 2)
                            }
                        }
                        .frame(height: 44)
                        Text(nbVocab[i]).font(AppFont.sans(12)).foregroundStyle(on ? StoryTone.active.ink(palette) : palette.onSurface.opacity(0.85))
                            .lineLimit(1).padding(.top, 4)
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 6)
                    .background(on ? SimColors.active.opacity(0.16) : .clear, in: RoundedRectangle(cornerRadius: 8))
                }
            }
        }
    }
}

private struct PresenceView: View {
    let scored: Int
    let current: Int?
    @Environment(\.palette) private var palette

    var body: some View {
        let widths: [CGFloat] = [1.1, 1.3, 1, 1]
        VStack(alignment: .leading, spacing: 0) {
            CardLabel(text: "Document · \(nbDoc.joined(separator: " "))")
            HStack(spacing: 8) {
                ForEach(nbVocab.indices.filter { bernPresent[$0] }, id: \.self) { WordTile(text: nbVocab[$0], tone: .done, caption: "present") }
            }
            .padding(.top, 8)
            GeometryReader { geo in
                let unit = (geo.size.width - 20) / widths.reduce(0, +)
                HStack(spacing: 0) {
                    ForEach(Array(["term", "in doc", "× sports", "× politics"].enumerated()), id: \.offset) { i, h in
                        Text(h).font(AppFont.sans(13)).foregroundStyle(palette.muted)
                            .frame(width: unit * widths[i], alignment: i >= 2 ? .trailing : .leading)
                    }
                }
                .padding(.horizontal, 10)
            }
            .frame(height: 18)
            .padding(.top, 12)
            ForEach(nbVocab.indices, id: \.self) { k in
                let on = k == current
                let done = k < scored
                let ink = on ? StoryTone.active.ink(palette) : palette.onSurface
                GeometryReader { geo in
                    let unit = (geo.size.width - 20) / widths.reduce(0, +)
                    HStack(spacing: 0) {
                        Text(nbVocab[k]).font(AppFont.sans(15, .semibold)).foregroundStyle(done || on ? ink : palette.muted)
                            .frame(width: unit * widths[0], alignment: .leading)
                        Text(bernPresent[k] ? "present" : "absent").font(AppFont.mono(14))
                            .foregroundStyle(bernPresent[k] ? StoryTone.done.ink(palette) : palette.muted)
                            .frame(width: unit * widths[1], alignment: .leading)
                        ForEach(0..<2, id: \.self) { c in
                            Text(done ? bx(bernFactor(k, c)) : "–").font(AppFont.mono(14)).foregroundStyle(done ? ink : palette.muted)
                                .frame(width: unit * widths[2 + c], alignment: .trailing)
                        }
                    }
                    .padding(.horizontal, 10)
                    .frame(maxHeight: .infinity)
                }
                .frame(height: 36)
                .background(on ? SimColors.active.opacity(0.16) : .clear, in: RoundedRectangle(cornerRadius: 8))
                .padding(.top, 2)
            }
        }
    }
}

private struct ComplementView: View {
    let multinomial: Bool
    let complement: Bool

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            CardLabel(text: "Document · true label sports")
            HStack(spacing: 8) { ForEach(compDoc, id: \.self) { WordTile(text: $0, tone: .done) } }.padding(.top, 8)
            HStack(alignment: .top, spacing: 10) {
                ScoreCard(title: "Multinomial", subtitle: "highest fit wins", scores: multinomial ? compMultinomial : nil, pickMax: true)
                ScoreCard(title: "Complement", subtitle: "worst fit to the rest wins", scores: complement ? compComplement : nil, pickMax: false)
            }
            .padding(.top, 12)
        }
    }
}

private struct ScoreCard: View {
    let title: String
    let subtitle: String
    let scores: [Double]?
    let pickMax: Bool
    @Environment(\.palette) private var palette

    var body: some View {
        let names = ["sports", "politics"]
        let winner: Int? = scores.map { ($0[0] > $0[1]) == pickMax ? 0 : 1 }
        let right = winner == 0
        let tone: StoryTone = right ? .done : .warn
        VStack(alignment: .leading, spacing: 0) {
            Text(title).font(AppFont.sans(15, .bold))
            Text(subtitle).font(AppFont.sans(12)).foregroundStyle(palette.muted).lineLimit(1).minimumScaleFactor(0.8)
            ForEach(0..<2, id: \.self) { i in
                let on = i == winner
                HStack {
                    Text(names[i])
                    Spacer()
                    Text(scores.map { bx($0[i]) } ?? "–")
                }
                .font(AppFont.mono(14, on ? .bold : .regular))
                .foregroundStyle(on ? tone.ink(palette) : palette.onSurface)
                .padding(.top, 8)
            }
            Text(winner.map { "→ \(names[$0]) \(right ? "✓" : "✗")" } ?? " ")
                .font(AppFont.sans(14, .semibold)).foregroundStyle(tone.ink(palette)).padding(.top, 8)
        }
        .padding(12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(SimColors.tint, in: RoundedRectangle(cornerRadius: 12))
    }
}

private struct TableView: View {
    let tones: [StoryTone]
    let feature: Int?
    let highlight: Int?
    @Environment(\.palette) private var palette

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            CardLabel(text: "Row to classify")
            HStack(spacing: 6) {
                ForEach(catFeatures.indices, id: \.self) { i in WordTile(text: catFeatures[i].value, tone: tones[i], caption: catFeatures[i].name) }
            }
            .padding(.top, 8)
            if let feature {
                let f = catFeatures[feature]
                CardLabel(text: "\(f.name) count table").padding(.top, 14)
                HStack(spacing: 0) {
                    Spacer()
                    Text("play = yes (9)").frame(width: 110, alignment: .trailing)
                    Text("play = no (5)").frame(width: 100, alignment: .trailing)
                }
                .font(AppFont.sans(13)).foregroundStyle(palette.muted)
                .padding(.horizontal, 10).padding(.top, 6)
                ForEach(f.levels.indices, id: \.self) { i in
                    let on = i == highlight
                    let ink = on ? StoryTone.active.ink(palette) : palette.onSurface
                    HStack(spacing: 0) {
                        Text(f.levels[i]).font(AppFont.sans(15, .semibold)).foregroundStyle(on ? ink : ink.opacity(0.75))
                        Spacer()
                        Text("\(f.yes[i])").frame(width: 110, alignment: .trailing)
                        Text("\(f.no[i])").frame(width: 100, alignment: .trailing)
                    }
                    .font(AppFont.sans(15, on ? .bold : .regular))
                    .foregroundStyle(ink)
                    .padding(.horizontal, 10).padding(.vertical, 9)
                    .background(on ? SimColors.active.opacity(0.16) : .clear, in: RoundedRectangle(cornerRadius: 8))
                    .padding(.top, 2)
                }
            }
        }
    }
}

private struct NetView: View {
    let states: [BnState]
    let pills: [String]
    let pillTones: [StoryTone]
    @Environment(\.palette) private var palette

    var body: some View {
        Canvas { ctx, size in
            let w: CGFloat = 104, h: CGFloat = 40
            let centres = [
                CGPoint(x: size.width * 0.5, y: size.height * 0.17),
                CGPoint(x: size.width * 0.24, y: size.height * 0.5),
                CGPoint(x: size.width * 0.76, y: size.height * 0.5),
                CGPoint(x: size.width * 0.5, y: size.height * 0.83),
            ]
            // Arrows end on the target box's edge, so the heads stay visible.
            func inset(_ c: CGPoint, _ u: CGPoint, _ sign: CGFloat) -> CGPoint {
                let tx = abs(u.x) < 1e-3 ? CGFloat.greatestFiniteMagnitude : (w / 2) / abs(u.x)
                let ty = abs(u.y) < 1e-3 ? CGFloat.greatestFiniteMagnitude : (h / 2) / abs(u.y)
                let t = min(tx, ty) + 4
                return CGPoint(x: c.x + u.x * t * sign, y: c.y + u.y * t * sign)
            }
            for (a, b) in [(0, 1), (0, 2), (1, 3), (2, 3)] {
                let dx = centres[b].x - centres[a].x, dy = centres[b].y - centres[a].y
                let len = (dx * dx + dy * dy).squareRoot()
                let u = CGPoint(x: dx / len, y: dy / len)
                let s = inset(centres[a], u, 1), e = inset(centres[b], u, -1)
                ctx.line(s, e, color: palette.muted, width: 1.5)
                let n = CGPoint(x: -u.y, y: u.x)
                let head: CGFloat = 8
                var tri = Path()
                tri.move(to: e)
                tri.addLine(to: CGPoint(x: e.x - u.x * head + n.x * head * 0.5, y: e.y - u.y * head + n.y * head * 0.5))
                tri.addLine(to: CGPoint(x: e.x - u.x * head - n.x * head * 0.5, y: e.y - u.y * head - n.y * head * 0.5))
                tri.closeSubpath()
                ctx.fill(tri, with: .color(palette.muted))
            }
            for (i, c) in centres.enumerated() {
                let (fill, stroke): (Color, Color) = switch states[i] {
                case .observed: (SimColors.blue.opacity(0.25), SimColors.blue)
                case .question: (SimColors.active.opacity(0.18), SimColors.active)
                case .idle: (palette.dark ? Color(hex: 0x2A2F3A) : Color(hex: 0xF1F2F6), palette.muted.opacity(0.6))
                }
                let box = Path(roundedRect: CGRect(x: c.x - w / 2, y: c.y - h / 2, width: w, height: h), cornerRadius: h / 2)
                ctx.fill(box, with: .color(fill))
                ctx.stroke(box, with: .color(stroke), lineWidth: 2)
                ctx.draw(Text(netNames[i]).font(AppFont.sans(14, .semibold)).foregroundColor(palette.onSurface), at: c)
                // The value pill: right of Cloudy and WetGrass, under Sprinkler and Rain.
                let answer = pillTones[i] == .answer
                let text = ctx.resolve(Text(pills[i]).font(AppFont.mono(12, .bold))
                    .foregroundColor(answer ? StoryTone.answer.ink(palette) : palette.onSurface.opacity(0.85)))
                let ts = text.measure(in: size)
                let pw = ts.width + 16, ph: CGFloat = 24
                let pc = i == 0 || i == 3 ? CGPoint(x: c.x + w / 2 + 10 + pw / 2, y: c.y)
                    : CGPoint(x: c.x + (i == 1 ? -w * 0.1 : w * 0.1), y: c.y + h / 2 + 6 + ph / 2)
                ctx.fill(Path(roundedRect: CGRect(x: pc.x - pw / 2, y: pc.y - ph / 2, width: pw, height: ph), cornerRadius: 6),
                         with: .color(answer ? SimColors.answer.opacity(0.22) : SimColors.tint))
                ctx.draw(text, at: pc)
            }
        }
        .aspectRatio(1.6, contentMode: .fit)
        .modifier(Stage())
    }
}

private struct ChainView: View {
    let upTo: Int
    let proposal: Int?
    let dotsOnly: Bool
    @Environment(\.palette) private var palette

    var body: some View {
        Canvas { ctx, size in
            let run = mcmcRun
            let xLo = -2.5, xHi = 2.5, yLo = -1.9, yHi = 2.1
            func at(_ x: Double, _ y: Double) -> CGPoint {
                CGPoint(x: CGFloat((x - xLo) / (xHi - xLo)) * size.width, y: size.height - CGFloat((y - yLo) / (yHi - yLo)) * size.height)
            }
            func circle(_ c: CGPoint, _ r: CGFloat) -> Path { Path(ellipseIn: CGRect(x: c.x - r, y: c.y - r, width: r * 2, height: r * 2)) }
            // The target density as a field of dots, each sized by p there.
            let cols = 40, rows = 26
            for i in 0..<cols {
                for j in 0..<rows {
                    let x = xLo + (xHi - xLo) * (Double(i) + 0.5) / Double(cols)
                    let y = yLo + (yHi - yLo) * (Double(j) + 0.5) / Double(rows)
                    let p = crescent(x, y)
                    if p > 0.05 { ctx.fill(circle(at(x, y), 0.6 + 1.6 * CGFloat(p)), with: .color(palette.muted.opacity(0.55))) }
                }
            }
            let steps = Array(run.prefix(upTo))
            let blue = SimColors.blue
            if dotsOnly {
                // Each step's sample: where the chain is after it, the proposal if taken, else where it was.
                for s in steps { ctx.fill(circle(s.accepted ? at(s.toX, s.toY) : at(s.fromX, s.fromY), 2.5), with: .color(blue.opacity(0.55))) }
                return
            }
            for s in steps where !s.accepted { ctx.stroke(circle(at(s.toX, s.toY), 4), with: .color(palette.muted), lineWidth: 1.2) }
            let start = at(run[0].fromX, run[0].fromY)
            var path = Path()
            path.move(to: start)
            for s in steps where s.accepted { path.addLine(to: at(s.toX, s.toY)) }
            ctx.stroke(path, with: .color(blue.opacity(0.8)), lineWidth: 1.5)
            ctx.fill(circle(start, 3), with: .color(blue))
            for s in steps where s.accepted { ctx.fill(circle(at(s.toX, s.toY), 3), with: .color(blue)) }
            let current = steps.last(where: \.accepted).map { at($0.toX, $0.toY) } ?? start
            ctx.fill(circle(current, 6), with: .color(blue))
            if let proposal {
                let p = at(run[proposal].toX, run[proposal].toY)
                var l = Path(); l.move(to: current); l.addLine(to: p)
                ctx.stroke(l, with: .color(SimColors.active), style: StrokeStyle(lineWidth: 1.5, dash: [3, 3]))
                ctx.fill(circle(p, 6), with: .color(SimColors.active))
            }
        }
        .aspectRatio(1.5, contentMode: .fit)
        .modifier(Stage())
    }
}
