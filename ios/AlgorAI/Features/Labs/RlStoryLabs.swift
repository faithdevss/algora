import SwiftUI

// Port of RlStoryLabs.kt: Q-Learning, SARSA, Markov Decision Process, Thompson Sampling, Multi-Armed Bandit
// and UCB: a grid of values (or a row per arm), the update of the current step, chips and a headline,
// then a parameter stepper over a step-back button and the lab's labelled action. Every number comes
// from a seeded run.

let rlStoryTopicIds: Set<String> = ["q_learning", "sarsa", "mdp", "thompson_sampling", "multi_armed_bandit", "ucb"]

private let goalGreen = Color(hex: 0x4CAF7A)
private let pitRed = Color(hex: 0xD9534F)
private let violet = Color(hex: 0xB4A2FF)
private let bonusViolet = Color(hex: 0x4C3F91)
private let winnerFill = Color(hex: 0x8A7440)

// MARK: - Formatting

private func rx(_ v: Double, _ d: Int = 2) -> String {
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

private func ladder(_ from: Double, _ to: Double, _ step: Double) -> [Double] {
    (0...Int(((to - from) / step).rounded())).map { from + Double($0) * step }
}

private func nearest(_ values: [Double], _ v: Double) -> Int { values.indices.min { abs(values[$0] - v) < abs(values[$1] - v) }! }

private let arrows = ["↑", "→", "↓", "←"]
private let dr = [-1, 0, 1, 0]
private let dc = [0, 1, 0, -1]

private struct RsCell: Hashable { let r: Int; let c: Int }

// MARK: - Scenes

private enum RsKind { case plain, goal, pit, wall, cliff }

private struct RsGCell {
    let text: String
    var sub = ""
    var kind: RsKind = .plain
    var value = 0.0
    var ring: Color? = nil
    var highlight = false
}

private struct RsGrid {
    let rows: Int
    let cols: Int
    let cells: [RsGCell]
    /// Neutral cells (the cliff grid): values are printed but not tinted.
    var neutral = false
    var solid: [RsCell] = []
    var dashed: [RsCell] = []
}

private struct RsArmRow {
    let label: String
    let best: Bool
    let estimate: Double
    var bonus = 0.0
    var scale = 1.0
    let text: String
    let trueMean: Double
    let right: String
    let active: Bool
    var curve: [Double]? = nil
    var draw: Double? = nil
}

private enum RsScene {
    case grid(RsGrid)
    case arms([RsArmRow], header: (String, String)?)
}

private struct RsFrame {
    let headline: String
    let body: String
    let scene: RsScene
    let action: String
    var formula: [String] = []
    var legend: [(color: Color?, style: SwatchStyle, label: String)] = []
    var chips: [LabChip] = []
}

private struct RsParam { let name: String; let symbol: String; let values: [Double]; let initial: Int; let format: (Double) -> String }

/// steps: a stepper over back + the frame's action. button: a stepper over one button that bumps the flag.
private enum RsControl { case steps, button }

private struct RsLab {
    let frames: (_ param: Int, _ tab: Int, _ flag: Int) -> [RsFrame]
    let param: RsParam
    var control: RsControl = .steps
    var tabs: [String] = []
    var startTab = 0
    var startIndex = 0
    var startFlag = 0
    var button: ((Int) -> String)? = nil
    var nextFlag: (Int) -> Int = { $0 }
}

private struct RsState: Equatable { var param: Int; var tab: Int; var index: Int; var flag: Int }

// MARK: - Grid world (4 × 4): goal, pit, wall, start

private let gw = 4
private let goalCell = RsCell(r: 0, c: 3)
private let pitCell = RsCell(r: 1, c: 3)
private let wallCell = RsCell(r: 1, c: 1)
private let startCell = RsCell(r: 3, c: 0)
private let stepReward = -0.04

private func terminal(_ s: RsCell) -> Bool { s == goalCell || s == pitCell }

private func move(_ s: RsCell, _ a: Int) -> RsCell {
    let n = RsCell(r: s.r + dr[a], c: s.c + dc[a])
    return n.r < 0 || n.r >= gw || n.c < 0 || n.c >= gw || n == wallCell ? s : n
}

private func reward(_ n: RsCell) -> Double { n == goalCell ? 1 : n == pitCell ? -1 : stepReward }

private let allCells = (0..<gw).flatMap { r in (0..<gw).map { RsCell(r: r, c: $0) } }

private func gridCells(_ value: (RsCell) -> (String, String), _ v: (RsCell) -> Double, ring: [RsCell: Color] = [:], highlight: RsCell? = nil) -> [RsGCell] {
    allCells.map { s in
        if s == goalCell { return RsGCell(text: "1.00", kind: .goal) }
        if s == pitCell { return RsGCell(text: "−1.00", kind: .pit) }
        if s == wallCell { return RsGCell(text: "", kind: .wall) }
        let (t, sub) = value(s)
        return RsGCell(text: t, sub: sub, value: v(s), ring: ring[s], highlight: s == highlight)
    }
}

// MARK: - MDP: value iteration

private let sweeps = 12

private func mdpLab() -> RsLab {
    let gammas = Array(Set(ladder(0.5, 0.99, 0.05).map { ($0 * 100).rounded() / 100 } + [0.99])).sorted()
    return RsLab(frames: { p, _, _ in
        let gamma = gammas[p]
        func outcomes(_ s: RsCell, _ a: Int) -> [(RsCell, Double)] { [(move(s, a), 0.8), (move(s, (a + 1) % 4), 0.1), (move(s, (a + 3) % 4), 0.1)] }
        var vs: [[RsCell: Double]] = [[goalCell: 1, pitCell: -1]]
        for _ in 0..<sweeps {
            let old = vs.last!
            var next = old
            for s in allCells where !terminal(s) && s != wallCell {
                next[s] = (0...3).map { a in stepReward + gamma * outcomes(s, a).reduce(0) { $0 + $1.1 * (old[$1.0] ?? 0) } }.max()!
            }
            vs.append(next)
        }
        func best(_ v: [RsCell: Double], _ s: RsCell) -> Int {
            (0...3).max { a, b in outcomes(s, a).reduce(0) { $0 + $1.1 * (v[$1.0] ?? 0) } < outcomes(s, b).reduce(0) { $0 + $1.1 * (v[$1.0] ?? 0) } }!
        }
        let legend: [(color: Color?, style: SwatchStyle, label: String)] = [(goalGreen, .fill, "Goal +1"), (pitRed, .fill, "Pit −1"), (SimColors.active, .ring, "Backing up"), (SimColors.green.opacity(0.45), .fill, "Higher value")]
        return (0...sweeps).map { k in
            let v = vs[k]
            if k == 0 {
                return RsFrame(
                    headline: "Every value starts at {0}; only the goal and the pit are known.",
                    body: "Each sweep replaces every value with the best one-step lookahead: reward now, plus γ times what the move leads to.",
                    scene: .grid(RsGrid(rows: gw, cols: gw, cells: gridCells({ s in (rx(v[s] ?? 0), s == startCell ? "start" : "") }, { v[$0] ?? 0 }))),
                    action: "Next Sweep", legend: legend)
            }
            let prev = vs[k - 1]
            let cells = allCells.filter { !terminal($0) && $0 != wallCell }
            func change(_ s: RsCell) -> Double { abs((v[s] ?? 0) - (prev[s] ?? 0)) }
            let focus = cells.max { change($0) < change($1) }!
            let delta = change(focus)
            let a = best(prev, focus)
            let parts = outcomes(focus, a)
            let expected = parts.reduce(0) { $0 + $1.1 * (prev[$1.0] ?? 0) }
            return RsFrame(
                headline: delta < 0.005 ? "Sweep \(k) changes no value by more than {0.005}: the values have converged."
                    : "Sweep \(k) backs up the {yellow cell}: its best move now sees further toward the goal.",
                body: "Values spread outward from the goal one sweep at a time. The arrows are the greedy policy so far.",
                scene: .grid(RsGrid(rows: gw, cols: gw, cells: gridCells({ s in
                    (s == focus ? "\(rx(prev[s] ?? 0))→\(rx(v[s] ?? 0))" : rx(v[s] ?? 0), arrows[best(v, s)] + (s == startCell ? " start" : ""))
                }, { v[$0] ?? 0 }, ring: [focus: SimColors.active], highlight: focus))),
                action: k < sweeps ? "Next Sweep" : "Start Over",
                formula: ["\(arrows[a]) " + parts.map { "\(rx($0.1, 1))×\(rx(prev[$0.0] ?? 0))" }.joined(separator: " + "),
                          "V = −0.04 + \(rx(gamma)) × \(rx(expected)) = {v:\(rx(stepReward + gamma * expected))}"],
                legend: legend)
        }
    }, param: RsParam(name: "Discount", symbol: "γ", values: gammas, initial: nearest(gammas, 0.9), format: { rx($0) }), startIndex: 5)
}

// MARK: - Q-learning on the grid world

private let qPretrain = 60
private let qSteps = 30
private let qEps = 0.2
private let qGamma = 0.9

private func qLearningLab() -> RsLab {
    let alphas = ladder(0.1, 1.0, 0.1)
    return RsLab(frames: { p, _, _ in
        let alpha = alphas[p]
        var random = KotlinRandom(31)
        var q: [RsCell: [Double]] = [:]
        func qs(_ s: RsCell) -> [Double] { q[s] ?? [0, 0, 0, 0] }
        func maxQ(_ s: RsCell) -> Double { terminal(s) ? 0 : qs(s).max()! }
        func greedy(_ s: RsCell) -> Int { let v = qs(s); return v.indices.max { v[$0] < v[$1] }! }
        struct Step { let s: RsCell; let a: Int; let explore: Bool; let n: RsCell; let r: Double; let old: Double; let target: Double; let new: Double; let nextMax: Double }
        func act(_ s: RsCell) -> Step {
            let explore = random.nextDouble() < qEps
            let a = explore ? random.nextInt(4) : greedy(s)
            let n = move(s, a)
            let r = reward(n)
            let nm = maxQ(n)
            let target = r + qGamma * nm
            var row = qs(s)
            let old = row[a]
            row[a] = old + alpha * (target - old)
            q[s] = row
            return Step(s: s, a: a, explore: explore, n: n, r: r, old: old, target: target, new: row[a], nextMax: nm)
        }
        for _ in 0..<qPretrain {
            var s = startCell
            var guardCount = 0
            while !terminal(s) && guardCount < 60 { s = act(s).n; guardCount += 1 }
        }
        var s = startCell
        let legend: [(color: Color?, style: SwatchStyle, label: String)] = [(SimColors.active, .ring, "State s"), (SimColors.blue, .ring, "Next s′"), (SimColors.green.opacity(0.45), .fill, "max Q")]
        return (1...qSteps).map { k in
            let st = act(s)
            s = terminal(st.n) ? startCell : st.n
            let snapshot = Dictionary(uniqueKeysWithValues: allCells.map { ($0, maxQ($0)) })
            let arrowsNow = Dictionary(uniqueKeysWithValues: allCells.map { ($0, terminal($0) ? "" : arrows[greedy($0)]) })
            let took = arrows[st.a]
            let headline: String, body: String
            if terminal(st.n) {
                headline = "The step reaches {\(st.n == goalCell ? "the goal" : "the pit")}, so the target is just the reward, \(rx(st.r))."
                body = "A terminal state has no future to add. The episode ends and the agent starts over from the corner."
            } else if st.explore {
                headline = "The agent explored {\(took)}, but the update still assumes greedy play from s′."
                body = "That is what off-policy means: it learns the greedy policy while behaving ε-greedily."
            } else {
                headline = "The agent took its greedy move {\(took)}, and the target uses the same greedy value."
                body = "When behaviour and target agree, Q-learning and SARSA make the same update."
            }
            return RsFrame(
                headline: headline, body: body,
                scene: .grid(RsGrid(rows: gw, cols: gw, cells: gridCells({ c in (rx(snapshot[c] ?? 0), arrowsNow[c] ?? "") }, { snapshot[$0] ?? 0 },
                                                                          ring: terminal(st.n) || st.n == st.s ? [st.s: SimColors.active] : [st.s: SimColors.active, st.n: SimColors.blue],
                                                                          highlight: st.s))),
                action: k < qSteps ? "Take Step" : "Start Over",
                formula: ["took {\(took)} (\(st.explore ? "explore" : "greedy")) · target uses max Q(s′) = \(rx(st.nextMax))",
                          "target = \(rx(st.r)) + \(rx(qGamma, 1)) × \(rx(st.nextMax)) = \(rx(st.target))",
                          "Q ← \(rx(st.old)) + \(rx(alpha, 1)) × (\(rx(st.target)) − \(rx(st.old))) = {v:\(rx(st.new))}"],
                legend: legend)
        }
    }, param: RsParam(name: "Learning rate", symbol: "α", values: alphas, initial: nearest(alphas, 0.5), format: { rx($0) }))
}

// MARK: - SARSA vs Q-learning on the cliff

private let cr = 4
private let cc = 6

/// Moves that stay on the cliff grid; a walk off the edge is never offered.
private func cliffMoves(_ s: RsCell) -> [Int] { (0...3).filter { s.r + dr[$0] >= 0 && s.r + dr[$0] < cr && s.c + dc[$0] >= 0 && s.c + dc[$0] < cc } }

private func cliffTrain(_ sarsa: Bool, _ episodes: Int, _ eps: Double) -> ([RsCell: [Double]], [Double]) {
    var random = KotlinRandom(sarsa ? 51 : 52)
    var q: [RsCell: [Double]] = [:]
    func qs(_ s: RsCell) -> [Double] { q[s] ?? [0, 0, 0, 0] }
    func pick(_ s: RsCell) -> Int {
        let moves = cliffMoves(s)
        if random.nextDouble() < eps { return moves[random.nextInt(moves.count)] }
        let v = qs(s)
        return moves.max { v[$0] < v[$1] }!
    }
    var returns: [Double] = []
    for _ in 0..<episodes {
        var s = RsCell(r: cr - 1, c: 0)
        var a = pick(s)
        var total = 0.0
        var steps = 0
        while steps < 200 {
            steps += 1
            let n = RsCell(r: s.r + dr[a], c: s.c + dc[a])
            let cliff = n.r == cr - 1 && n.c >= 1 && n.c < cc - 1
            let goal = n == RsCell(r: cr - 1, c: cc - 1)
            let r = cliff ? -100.0 : -1.0
            total += r
            var row = qs(s)
            if cliff || goal {
                row[a] += 0.5 * (r - row[a])
                q[s] = row
                break
            }
            let a2 = pick(n)
            let next = sarsa ? qs(n)[a2] : cliffMoves(n).map { qs(n)[$0] }.max()!
            row[a] += 0.5 * (r + next - row[a])
            q[s] = row
            s = n
            a = a2
        }
        returns.append(total)
    }
    return (q, returns)
}

private func greedyPath(_ q: [RsCell: [Double]]) -> [RsCell] {
    var s = RsCell(r: cr - 1, c: 0)
    var path = [s]
    while path.count < 30 {
        guard let v = q[s] else { break }
        // The best move that doesn't revisit a cell, so an unsettled value can't send the path in circles.
        guard let a = cliffMoves(s).filter({ !path.contains(RsCell(r: s.r + dr[$0], c: s.c + dc[$0])) }).max(by: { v[$0] < v[$1] }) else { break }
        let n = RsCell(r: s.r + dr[a], c: s.c + dc[a])
        path.append(n)
        s = n
        if n.r == cr - 1 && n.c >= 1 { break }
    }
    return path
}

private func sarsaLab() -> RsLab {
    let epsilons = ladder(0, 0.3, 0.05)
    return RsLab(frames: { p, tab, flag in
        let eps = epsilons[p]
        let episodes = flag * 100
        let sarsa = cliffTrain(true, episodes, eps), ql = cliffTrain(false, episodes, eps)
        let sp = greedyPath(sarsa.0), qp = greedyPath(ql.0)
        let shown = tab == 0 ? sarsa.0 : ql.0
        let cells: [RsGCell] = (0..<cr).flatMap { r in
            (0..<cc).map { c in
                if r == cr - 1 && c == cc - 1 { return RsGCell(text: "goal", kind: .goal) }
                if r == cr - 1 && c >= 1 && c < cc - 1 { return RsGCell(text: "-100", kind: .cliff) }
                let cell = RsCell(r: r, c: c)
                let best = shown[cell].map { q in cliffMoves(cell).map { q[$0] }.max()! } ?? 0
                return RsGCell(text: "\(Int(best.rounded()))", ring: r == cr - 1 && c == 0 ? SimColors.active : nil)
            }
        }
        func avg(_ returns: [Double]) -> Double { let last = returns.suffix(100); return last.reduce(0, +) / Double(max(last.count, 1)) }
        let sSteps = sp.count - 1, qStepsCount = qp.count - 1
        let safer = sp.map(\.r).min()! < qp.map(\.r).min()!
        let headline: String, body: String
        if tab == 1 {
            headline = "Q-learning's greedy path {hugs the edge}: \(qStepsCount) steps, the shortest there is."
            body = "Its target assumes greedy play, so it ignores the random steps that sometimes walk it off the cliff while training."
        } else if safer {
            headline = "After \(episodes) episodes SARSA {keeps clear of the cliff}; Q-learning hugs the edge."
            body = "Its target counts its own random steps, so the edge looks risky."
        } else {
            headline = "After \(episodes) episodes SARSA's path {matches Q-learning's}."
            body = "With ε = \(rx(eps)) few random steps happen near the edge, so the safe detour isn't worth it."
        }
        return [RsFrame(
            headline: headline, body: body,
            scene: .grid(RsGrid(rows: cr, cols: cc, cells: cells, neutral: true, solid: tab == 0 ? sp : qp, dashed: tab == 0 ? qp : sp)),
            action: "",
            formula: ["SARSA: r + γ·Q(s′, {a′ actually taken})", "Q-learning: r + γ·max Q(s′)"],
            legend: [(nil, .line, tab == 0 ? "SARSA path" : "Q-learning path"), (.white, .dashedLine, tab == 0 ? "Q-learning path" : "SARSA path"), (pitRed.opacity(0.6), .fill, "Cliff")],
            chips: [LabChip(key: "steps", value: "\(sSteps) vs \(qStepsCount)", tint: .answer),
                    LabChip(key: "avg return", value: "\(Int(avg(sarsa.1).rounded())) vs \(Int(avg(ql.1).rounded()))", good: true)])]
    }, param: RsParam(name: "Exploration", symbol: "ε", values: epsilons, initial: nearest(epsilons, 0.1), format: { rx($0) }),
       control: .button, tabs: ["SARSA", "Q-learning"], startFlag: 4,
       button: { _ in "Train 100 Episodes" }, nextFlag: { $0 >= 10 ? 1 : $0 + 1 })
}

// MARK: - Bandits

private let armNames = ["A", "B", "C", "D"]
private let trueMeans = [0.45, 0.6, 0.4, 0.72]
private let bestArm = 3
private let pulls = 200

private struct BanditStep { let arm: Int; let explore: Bool; let roll: Double; let counts: [Int]; let sums: [Double] }

private func estimate(_ counts: [Int], _ sums: [Double], _ i: Int) -> Double { counts[i] == 0 ? 0 : sums[i] / Double(counts[i]) }

private func argmax(_ v: [Double]) -> Int { v.indices.max { v[$0] < v[$1] || (v[$0] == v[$1] && $0 > $1) }! }

private func banditRun(_ strategy: Int, _ eps: Double) -> [BanditStep] {
    var random = KotlinRandom(71)
    var counts = [0, 0, 0, 0]
    var sums = [0.0, 0, 0, 0]
    return (1...pulls).map { _ in
        let roll = random.nextDouble()
        let greedy = argmax((0...3).map { estimate(counts, sums, $0) })
        let explore = strategy == 0 || (strategy == 2 && roll < eps)
        let arm = explore ? random.nextInt(4) : greedy
        let r = random.nextDouble() < trueMeans[arm] ? 1.0 : 0
        counts[arm] += 1
        sums[arm] += r
        return BanditStep(arm: arm, explore: explore, roll: roll, counts: counts, sums: sums)
    }
}

private let armLegend: [(color: Color?, style: SwatchStyle, label: String)] = [(SimColors.active, .fill, "Pulled now"), (SimColors.blue, .fill, "Estimate"), (.white, .line, "True mean")]

private func banditLab() -> RsLab {
    let epsilons = ladder(0, 0.5, 0.05)
    return RsLab(frames: { p, tab, _ in
        let eps = epsilons[p]
        let run = banditRun(tab, eps)
        return run.indices.map { i in
            let st = run[i]
            let t = i + 1
            let greedyBefore = i == 0 ? 0 : argmax((0...3).map { estimate(run[i - 1].counts, run[i - 1].sums, $0) })
            let arm = armNames[st.arm]
            let rows = (0...3).map { a -> RsArmRow in
                let e = estimate(st.counts, st.sums, a)
                return RsArmRow(label: armNames[a], best: a == bestArm, estimate: e, text: rx(e), trueMean: trueMeans[a], right: "\(st.counts[a]) pulls", active: a == st.arm)
            }
            let formula = tab == 0 ? "random arm {\(arm)}" : tab == 1 ? "greedy arm {\(arm)} (highest estimate)"
                : st.explore ? "roll \(rx(st.roll)) < ε \(rx(eps)) → {explore}: random arm \(arm)" : "roll \(rx(st.roll)) ≥ ε \(rx(eps)) → exploit: greedy arm {\(arm)}"
            let headline: String, body: String
            if tab == 0 {
                headline = "Random pulls {\(arm)}: it never uses what it has learned."
                body = "Every arm gets a quarter of the pulls, so the best one is pulled no more than the worst."
            } else if tab == 1 {
                headline = "Greedy pulls {\(arm)} again: the highest estimate so far."
                body = "An arm that looked bad early is never tried again, so greedy can lock onto the wrong one."
            } else if st.explore {
                headline = "The greedy pick is \(armNames[greedyBefore]), but this roll sends the pull to {\(arm)}."
                body = "One pull in \(eps > 0 ? Int((1 / eps).rounded()) : 0) is random, so a weak early estimate can still recover. Random never exploits; greedy never explores."
            } else {
                headline = "This roll exploits: the pull goes to the greedy pick {\(arm)}."
                body = "Most pulls go to the best estimate, and the rest keep checking the others."
            }
            return RsFrame(
                headline: headline, body: body, scene: .arms(rows, header: nil),
                action: t < pulls ? "Pull Arm" : "Start Over", formula: [formula], legend: armLegend,
                chips: [LabChip(key: "greedy pick", value: armNames[greedyBefore], tint: .path),
                        LabChip(key: "\(armNames[bestArm]) pulls", value: "\(st.counts[bestArm]) / \(t)", tint: .answer)])
        }
    }, param: RsParam(name: "Exploration", symbol: "ε", values: epsilons, initial: nearest(epsilons, 0.1), format: { rx($0) }),
       tabs: ["Random", "Greedy", "ε-greedy"], startTab: 2, startIndex: 56)
}

private func ucbLab() -> RsLab {
    let cs = ladder(0.5, 4, 0.5)
    return RsLab(frames: { p, _, _ in
        let c = cs[p]
        var random = KotlinRandom(81)
        var counts = [0, 0, 0, 0]
        var sums = [0.0, 0, 0, 0]
        return (1...pulls).map { t in
            let est = (0...3).map { estimate(counts, sums, $0) }
            let bonus = (0...3).map { counts[$0] == 0 ? 0 : (c * log(Double(t)) / Double(counts[$0])).squareRoot() }
            let total = (0...3).map { est[$0] + bonus[$0] }
            let chosen = t <= 4 ? t - 1 : argmax(total)
            let bestEst = argmax(est)
            let scale = max(1, total.max()! * 1.08)
            let rows = (0...3).map { a in
                RsArmRow(label: armNames[a], best: a == bestArm, estimate: est[a], bonus: bonus[a], scale: scale, text: rx(total[a]), trueMean: trueMeans[a], right: "\(counts[a]) pulls", active: a == chosen)
            }
            let name = armNames[chosen]
            let headline: String, body: String
            if t <= 4 {
                headline = "Every arm is tried {once} before any bonus can be computed."
                body = "The bonus divides by the pull count, so an untried arm would have an infinite one."
            } else if chosen != bestEst {
                headline = "{\(name)} has only \(counts[chosen]) pulls, so its bonus lifts it above \(armNames[bestEst])."
                body = "Each bar shows estimate plus bonus. The bonus shrinks with every pull, so rarely tried arms get another look."
            } else {
                headline = "{\(name)} leads on its estimate and its bonus doesn't change that: UCB exploits."
                body = "As pulls pile up every bonus shrinks, and the arm with the best estimate wins more often."
            }
            let cText = rx(c, 1).hasSuffix(".0") ? String(rx(c, 1).dropLast(2)) : rx(c, 1)
            let formula = t <= 4 ? "\(name): untried, so it is pulled first"
                : "\(name): \(rx(est[chosen])) + √(\(cText) ln \(t) / \(counts[chosen])) = \(rx(est[chosen])) + \(rx(bonus[chosen])) = {v:\(rx(total[chosen]))}"
            let frame = RsFrame(
                headline: headline, body: body, scene: .arms(rows, header: nil),
                action: t < pulls ? "Pull Arm" : "Start Over", formula: [formula],
                legend: [(SimColors.blue, .fill, "Estimate"), (bonusViolet, .fill, "Bonus"), (.white, .line, "True mean")],
                chips: [LabChip(key: "pull", value: "\(t) / \(pulls)"), LabChip(key: "best estimate", value: armNames[bestEst], tint: .path)])
            counts[chosen] += 1
            sums[chosen] += random.nextDouble() < trueMeans[chosen] ? 1 : 0
            return frame
        }
    }, param: RsParam(name: "Bonus scale", symbol: "c", values: cs, initial: nearest(cs, 2), format: { rx($0, 1) }), startIndex: 12)
}

// MARK: - Thompson sampling

private func logGamma(_ x: Double) -> Double {
    let g = [76.18009172947146, -86.50532032941677, 24.01409824083091, -1.231739572450155, 0.1208650973866179e-2, -0.5395239384953e-5]
    var y = x
    let tmp = x + 5.5 - (x + 0.5) * log(x + 5.5)
    var ser = 1.000000000190015
    for c in g { y += 1; ser += c / y }
    return -tmp + log(2.5066282746310005 * ser / x)
}

private func betaPdf(_ x: Double, _ a: Double, _ b: Double) -> Double {
    exp((a - 1) * log(x) + (b - 1) * log(1 - x) + logGamma(a + b) - logGamma(a) - logGamma(b))
}

private struct RsGaussian {
    var random: KotlinRandom
    init(_ seed: Int32) { random = KotlinRandom(seed) }
    mutating func uniform() -> Double { random.nextDouble() }
    mutating func normal() -> Double {
        let u = max(random.nextDouble(), 1e-12)
        return (-2 * log(u)).squareRoot() * cos(2 * Double.pi * random.nextDouble())
    }
    /// Marsaglia–Tsang for shape ≥ 1.
    mutating func gamma(_ k: Double) -> Double {
        let d = k - 1.0 / 3, c = 1 / (9 * d).squareRoot()
        while true {
            let x = normal()
            let v = pow(1 + c * x, 3)
            if v <= 0 { continue }
            let u = max(uniform(), 1e-12)
            if log(u) < 0.5 * x * x + d - d * v + d * log(v) { return d * v }
        }
    }
    mutating func beta(_ a: Double, _ b: Double) -> Double {
        let x = gamma(a)
        return x / (x + gamma(b))
    }
}

private func thompsonLab() -> RsLab {
    RsLab(frames: { _, _, _ in
        var g = RsGaussian(91)
        var wins = [0, 0, 0, 0], losses = [0, 0, 0, 0]
        return (1...pulls).map { t in
            let draws = (0...3).map { g.beta(1 + Double(wins[$0]), 1 + Double(losses[$0])) }
            let winner = argmax(draws)
            let bestMean = argmax((0...3).map { (1 + Double(wins[$0])) / (2 + Double(wins[$0] + losses[$0])) })
            let rows = (0...3).map { a -> RsArmRow in
                let alpha = 1 + Double(wins[a]), beta = 1 + Double(losses[a])
                let pts = (1...59).map { betaPdf(Double($0) / 60, alpha, beta) }
                let top = pts.max()!
                return RsArmRow(label: armNames[a], best: a == bestArm, estimate: 0, text: "", trueMean: trueMeans[a], right: rx(draws[a]), active: a == winner,
                                curve: pts.map { $0 / top }, draw: draws[a])
            }
            let w = armNames[winner]
            let runnerUp = (0...3).filter { $0 != winner }.max { draws[$0] < draws[$1] }!
            let headline: String, body: String
            if winner != bestMean {
                headline = "{\(w)}'s wide posterior drew \(rx(draws[winner])), beating \(armNames[runnerUp])'s \(rx(draws[runnerUp]))."
                body = "Arms with few pulls have wide curves, so they sometimes draw high. That is the exploration."
            } else {
                headline = "{\(w)} drew highest again: its posterior is narrow and high."
                body = "As pulls pile up, the best arm's curve narrows and wins most draws. No ε, no bonus: the posterior's own width decides."
            }
            let frame = RsFrame(
                headline: headline, body: body, scene: .arms(rows, header: ("Posterior per arm", "draw")),
                action: t < pulls ? "Draw and Pull" : "Start Over",
                formula: ["\(w) ~ Beta(1+\(wins[winner]), 1+\(losses[winner])) → draw {v:\(rx(draws[winner]))}"],
                legend: [(violet, .dashedLine, "This draw"), (.white, .line, "True mean"), (winnerFill, .fill, "Winner")],
                chips: [LabChip(key: "pull", value: "\(t) / \(pulls)"), LabChip(key: "\(armNames[bestArm]) pulls", value: "\(wins[bestArm] + losses[bestArm])", tint: .answer)])
            if g.uniform() < trueMeans[winner] { wins[winner] += 1 } else { losses[winner] += 1 }
            return frame
        }
    }, param: RsParam(name: "Prior strength", symbol: "α₀", values: [1], initial: 0, format: { rx($0, 0) }), startIndex: 41)
}

private func rlLab(_ topicId: String) -> RsLab {
    switch topicId {
    case "sarsa": sarsaLab()
    case "mdp": mdpLab()
    case "thompson_sampling": thompsonLab()
    case "multi_armed_bandit": banditLab()
    case "ucb": ucbLab()
    default: qLearningLab()
    }
}

// MARK: - Lab

/// Shared by the page and the pinned controls, so both stay live.
@MainActor
@Observable
private final class RsModel {
    let lab: RsLab
    let initial: RsState
    var state: RsState {
        didSet {
            if state.param != oldValue.param || state.tab != oldValue.tab || state.flag != oldValue.flag {
                frames = lab.frames(state.param, state.tab, state.flag)
            }
        }
    }
    private(set) var frames: [RsFrame]

    init(lab: RsLab) {
        self.lab = lab
        initial = RsState(param: lab.param.initial, tab: lab.startTab, index: lab.startIndex, flag: lab.startFlag)
        state = initial
        frames = lab.frames(initial.param, initial.tab, initial.flag)
    }

    var index: Int { min(max(state.index, 0), frames.count - 1) }
    var frame: RsFrame { frames[index] }
}

struct RlStoryLab: View {
    @State private var model: RsModel
    @Environment(\.labDock) private var dock
    @Environment(\.palette) private var palette

    init(topicId: String) {
        _model = State(initialValue: RsModel(lab: rlLab(topicId)))
    }

    var body: some View {
        let frame = model.frame
        VStack(alignment: .leading, spacing: 0) {
            if !model.lab.tabs.isEmpty {
                LabSegments(labels: model.lab.tabs, selected: Binding(get: { model.state.tab }, set: { model.state.tab = $0 })).padding(.bottom, 14)
            }
            LabCard {
                switch frame.scene {
                case .grid(let g): RsGridView(grid: g)
                case let .arms(rows, header): RsArmsView(rows: rows, header: header)
                }
                if !frame.formula.isEmpty { RsFormula(lines: frame.formula).padding(.top, 12) }
                StoryLegendRow(items: frame.legend.map { ($0.color ?? palette.primary, $0.style, $0.label) }).padding(.top, 14)
            }
            if !frame.chips.isEmpty { LabChips(chips: frame.chips).padding(.top, 16) }
            LabStoryNarration(headline: frame.headline, body: frame.body).padding(.top, 16)
            if dock == nil {
                Divider().padding(.top, 16)
                RsControls(model: model).padding(.top, 14)
            }
        }
        .onAppear {
            guard let dock else { return }
            let model = model
            dock.controls = { AnyView(RsControls(model: model)) }
            dock.navAction = LabNavAction(icon: "arrow.counterclockwise", label: "Reset") { model.state = model.initial }
        }
        .onDisappear {
            dock?.controls = nil
            dock?.navAction = nil
        }
    }
}

private struct RsControls: View {
    let model: RsModel

    var body: some View {
        let lab = model.lab
        let p = lab.param
        let s = model.state
        let index = model.index
        VStack(spacing: 14) {
            // Thompson sampling has nothing to tune; its stepper is hidden.
            if p.values.count > 1 {
                LabParamStepper(param: LabParam(tab: p.name, name: p.name, symbol: p.symbol, text: p.format(p.values[s.param]),
                                                canDecrease: s.param > 0, canIncrease: s.param < p.values.count - 1)) { d in
                    model.state.param = min(max(model.state.param + d, 0), p.values.count - 1)
                }
            }
            switch lab.control {
            case .steps:
                LabBackActionRow(action: model.frame.action, backEnabled: index > 0,
                                 onBack: { model.state.index = index - 1 },
                                 onAction: { model.state.index = index >= model.frames.count - 1 ? 0 : index + 1 })
            case .button:
                LabButton(label: lab.button?(s.flag) ?? "", primary: true) { model.state.flag = lab.nextFlag(model.state.flag) }
            }
        }
    }
}

// MARK: - Rendering

private struct RsFormula: View {
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

private struct RsGridView: View {
    let grid: RsGrid
    @Environment(\.palette) private var palette

    var body: some View {
        let gap: CGFloat = 6
        GeometryReader { geo in
            let cell = (geo.size.width - gap * CGFloat(grid.cols - 1)) / CGFloat(grid.cols)
            let cellH = cell * (grid.neutral ? 0.78 : 0.72)
            ZStack(alignment: .topLeading) {
                VStack(spacing: gap) {
                    ForEach(0..<grid.rows, id: \.self) { r in
                        HStack(spacing: gap) {
                            ForEach(0..<grid.cols, id: \.self) { c in
                                cellView(grid.cells[r * grid.cols + c]).frame(width: cell, height: cellH)
                            }
                        }
                    }
                }
                if !grid.solid.isEmpty || !grid.dashed.isEmpty {
                    Canvas { ctx, _ in
                        func centre(_ p: RsCell) -> CGPoint { CGPoint(x: CGFloat(p.c) * (cell + gap) + cell / 2, y: CGFloat(p.r) * (cellH + gap) + cellH / 2) }
                        func line(_ ps: [RsCell], _ color: Color, _ dash: Bool, _ width: CGFloat) {
                            guard ps.count > 1 else { return }
                            var path = Path()
                            for (i, p) in ps.enumerated() { if i == 0 { path.move(to: centre(p)) } else { path.addLine(to: centre(p)) } }
                            ctx.stroke(path, with: .color(color), style: StrokeStyle(lineWidth: width, dash: dash ? [4, 4] : []))
                        }
                        line(grid.dashed, .white.opacity(0.9), true, 1.5)
                        line(grid.solid, palette.primary, false, 3.5)
                    }
                    .allowsHitTesting(false)
                }
            }
        }
        .aspectRatio(gridAspect, contentMode: .fit)
    }

    private var gridAspect: CGFloat {
        let ratio: CGFloat = grid.neutral ? 0.78 : 0.72
        // width : height = cols·cell + gaps : rows·cell·ratio + gaps, approximated at a 330pt width.
        let w: CGFloat = 330, gap: CGFloat = 6
        let cell = (w - gap * CGFloat(grid.cols - 1)) / CGFloat(grid.cols)
        return w / (CGFloat(grid.rows) * cell * ratio + gap * CGFloat(grid.rows - 1))
    }

    @ViewBuilder
    private func cellView(_ g: RsGCell) -> some View {
        let fill: Color = switch g.kind {
        case .goal: goalGreen
        case .pit: pitRed
        case .cliff: pitRed.opacity(0.55)
        case .wall: SimColors.tint
        case .plain:
            grid.neutral ? SimColors.tint
                : g.value > 0.005 ? SimColors.green.opacity(0.12 + 0.4 * min(g.value, 1))
                : g.value < -0.005 ? SimColors.red.opacity(0.12 + 0.3 * min(-g.value, 1))
                : SimColors.tint
        }
        VStack(spacing: 2) {
            if !g.text.isEmpty {
                Text(g.text)
                    .font(AppFont.mono(grid.neutral ? 12 : g.text.count > 6 ? 11 : 14, .bold))
                    .foregroundStyle(g.highlight ? StoryTone.active.ink(palette) : g.kind == .plain && grid.neutral ? palette.onSurface.opacity(0.75) : palette.onSurface)
                    .lineLimit(1).minimumScaleFactor(0.7)
            }
            if !g.sub.isEmpty {
                Text(g.sub).font(AppFont.sans(12)).foregroundStyle(g.highlight ? StoryTone.active.ink(palette) : palette.muted).lineLimit(1)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(fill, in: RoundedRectangle(cornerRadius: 9))
        .overlay { if let ring = g.ring { RoundedRectangle(cornerRadius: 9).strokeBorder(ring, lineWidth: 2) } }
    }
}

private struct RsArmsView: View {
    let rows: [RsArmRow]
    let header: (String, String)?
    @Environment(\.palette) private var palette

    var body: some View {
        VStack(spacing: 10) {
            if let header {
                HStack {
                    Text(header.0)
                    Spacer()
                    Text(header.1)
                }
                .font(AppFont.sans(14)).foregroundStyle(palette.muted)
            }
            ForEach(rows.indices, id: \.self) { RsArmRowView(row: rows[$0]) }
        }
    }
}

private struct RsArmRowView: View {
    let row: RsArmRow
    @Environment(\.palette) private var palette

    var body: some View {
        HStack(spacing: 0) {
            VStack(spacing: 0) {
                Text(row.label).font(AppFont.sans(18, .bold)).foregroundStyle(row.active ? StoryTone.active.ink(palette) : palette.onSurface)
                if row.best { Text("BEST").font(AppFont.sans(9, .bold)).foregroundStyle(StoryTone.done.ink(palette)) }
            }
            .frame(width: 34)
            ZStack(alignment: .leading) {
                Canvas { ctx, size in
                    let w = size.width, h = size.height
                    if let curve = row.curve {
                        var path = Path(), fill = Path()
                        fill.move(to: CGPoint(x: 0, y: h))
                        for (i, v) in curve.enumerated() {
                            let pt = CGPoint(x: CGFloat(i + 1) / 60 * w, y: h - 4 - CGFloat(v) * (h - 10))
                            if i == 0 { path.move(to: pt) } else { path.addLine(to: pt) }
                            fill.addLine(to: pt)
                        }
                        fill.addLine(to: CGPoint(x: w, y: h))
                        fill.closeSubpath()
                        let color = row.active ? SimColors.active : SimColors.blue
                        ctx.fill(fill, with: .color(color.opacity(0.25)))
                        ctx.stroke(path, with: .color(color), lineWidth: 1.5)
                        if let d = row.draw {
                            var l = Path(); l.move(to: CGPoint(x: CGFloat(d) * w, y: 6)); l.addLine(to: CGPoint(x: CGFloat(d) * w, y: h - 6))
                            ctx.stroke(l, with: .color(row.active ? SimColors.active : violet), style: StrokeStyle(lineWidth: 2, dash: [3, 2]))
                        }
                    } else {
                        let estW = CGFloat(min(max(row.estimate / row.scale, 0), 1)) * w
                        let bonusW = CGFloat(min(max(row.bonus / row.scale, 0), 1)) * w
                        ctx.fill(Path(CGRect(x: 0, y: 0, width: estW, height: h)), with: .color(row.active ? SimColors.active : SimColors.blue))
                        if bonusW > 0 {
                            ctx.fill(Path(CGRect(x: estW, y: 0, width: min(bonusW, w - estW), height: h)),
                                     with: .color(row.active ? SimColors.active.opacity(0.45) : bonusViolet))
                        }
                    }
                    let tx = CGFloat(row.trueMean / row.scale) * w
                    ctx.line(CGPoint(x: tx, y: 8), CGPoint(x: tx, y: h - 8), color: .white, width: 2.5)
                }
                if !row.text.isEmpty {
                    Text(row.text).font(AppFont.mono(15, .bold)).foregroundStyle(row.active ? Color(hex: 0x1F1A0A) : .white).padding(.leading, 10)
                }
            }
            .frame(height: 44)
            .background(row.active && row.curve != nil ? SimColors.active.opacity(0.3) : SimColors.tint)
            .clipShape(RoundedRectangle(cornerRadius: 10))
            .overlay { if row.active { RoundedRectangle(cornerRadius: 10).strokeBorder(SimColors.active, lineWidth: 2) } }
            Text(row.right).font(AppFont.mono(14, .bold))
                .foregroundStyle(row.active && row.curve != nil ? StoryTone.active.ink(palette) : palette.onSurface)
                .frame(width: 76, alignment: .trailing)
        }
    }
}
