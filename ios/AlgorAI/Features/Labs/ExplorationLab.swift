import SwiftUI

// Port of ExplorationSection.kt. A sparse-reward comb maze: a top corridor with four dead-end
// branches, one reward at the bottom of the last branch. Every figure a frame quotes comes from
// running the agents here. ICM is a tabular forward model over raw next states and RND a tabular
// predictor chasing a fixed random target — the simplification the Android file documents.

private struct ExpCell { let shade: Float; let glyph: String; let color: Color }
private struct ExpGrid { let label: String; let cells: [ExpCell] }

private struct ExpFrame {
    let status: String
    var grid: ExpGrid?
    var plot: FramePlot?
    var bars: [FrameBars] = []
    var readout: String?
}

private struct ExpConfig {
    let intro: String
    let legend: [(Color, String)]
    let build: () -> [ExpFrame]
}

private let expBaseline = SimColors.grey
private let expGood = SimColors.green
private let expBad = Color(hex: 0xEC4899)
private let expAccent = SimColors.blue
private let expHighlight = Color(hex: 0x7C3AED)
private let expWall = SimColors.wall

// MARK: - The maze. '#' wall, 'S' start, 'G' goal, '?' the "noisy television" cell.

private let maze: [[Character]] = [
    "S.......",
    "#.#.#.#.",
    "#.#?#.#.",
    "#.#.#.#.",
    "#.#.#.#.",
].map { Array($0) }

private let expRows = 5
private let expCols = 8
private let expStates = expRows * expCols

private func charAt(_ s: Int) -> Character { maze[s / expCols][s % expCols] }
private func isWall(_ s: Int) -> Bool { charAt(s) == "#" }
private let expStart = (0..<expStates).first { charAt($0) == "S" }!
private let expGoal = expRows * expCols - 1
private let expNoisy = (0..<expStates).first { charAt($0) == "?" }!
private let freeCells = (0..<expStates).filter { !isWall($0) }
private let expMoves = [-expCols, expCols, -1, 1]

private func moveFrom(_ state: Int, _ action: Int) -> Int {
    let next = state + expMoves[action]
    if next < 0 || next >= expStates { return state }
    if abs(expMoves[action]) == 1 && next / expCols != state / expCols { return state }
    if isWall(next) { return state }
    return next
}

private func distinctMoves(_ s: Int) -> [Int] {
    var out: [Int] = []
    for a in expMoves.indices {
        let n = moveFrom(s, a)
        if !out.contains(n) { out.append(n) }
    }
    return out
}

/// Shortest-path distance from the start.
private let distanceFromStart: [Int] = {
    var dist = [Int](repeating: -1, count: expStates)
    dist[expStart] = 0
    var queue = [expStart]
    var head = 0
    while head < queue.count {
        let s = queue[head]; head += 1
        for a in expMoves.indices {
            let n = moveFrom(s, a)
            if n != s && dist[n] < 0 {
                dist[n] = dist[s] + 1
                queue.append(n)
            }
        }
    }
    return dist
}()

/// Shortest-path length between two open cells.
private func pathLength(_ from: Int, _ to: Int) -> Int {
    if from == to { return 0 }
    var dist = [Int](repeating: -1, count: expStates)
    dist[from] = 0
    var queue = [from]
    var head = 0
    while head < queue.count {
        let s = queue[head]; head += 1
        for a in expMoves.indices {
            let n = moveFrom(s, a)
            if n != s && dist[n] < 0 {
                dist[n] = dist[s] + 1
                if n == to { return dist[n] }
                queue.append(n)
            }
        }
    }
    return 0
}

private struct ExpRun {
    let discovered: [Int]
    let reachedGoal: Int
    let visits: [Int]
    let stepsToGoal: [Int]
}

private enum Bonus { case none, count, icm, rnd }

/// One exploration run: epsilon-greedy Q-learning plus the chosen intrinsic bonus.
private func explore(seed: Int, bonus: Bonus, episodes: Int = 40, stepBudget: Int = 220, noisy: Bool = false) -> ExpRun {
    var rng = Lcg(seed)
    var q = [[Double]](repeating: [0, 0, 0, 0], count: expStates)
    var visits = [Int](repeating: 0, count: expStates)
    var transitionCount = [[[Int: Int]]](repeating: [[:], [:], [:], [:]], count: expStates)
    var rndTarget: [Double] = []
    for _ in 0..<expStates { rndTarget.append(rng.next()) }
    var rndPredictor = [Double](repeating: 0, count: expStates)

    var discovered: [Int] = []
    var seen = Set<Int>()
    var reached = 0
    var stepsToGoal: [Int] = []

    for _ in 0..<episodes {
        var s = expStart
        var steps = 0
        while steps < stepBudget {
            steps += 1
            let a = rng.next() < 0.25 ? rng.nextInt(4) : argmaxFirst(q[s])
            var next = moveFrom(s, a)
            // The noisy cell scrambles the agent to a random neighbour: genuinely unpredictable.
            if noisy && s == expNoisy {
                let options = distinctMoves(s)
                next = options[rng.nextInt(options.count)]
            }
            visits[next] += 1
            seen.insert(next)

            let external: Double = next == expGoal ? 1 : 0
            let intrinsic: Double
            switch bonus {
            case .none: intrinsic = 0
            case .count: intrinsic = 0.5 / Double(visits[next]).squareRoot()
            case .icm:
                let counts = transitionCount[s][a]
                let total = counts.values.reduce(0, +)
                let p = total == 0 ? 0 : Double(counts[next] ?? 0) / Double(total)
                transitionCount[s][a][next, default: 0] += 1
                intrinsic = 0.6 * (1 - p)
            case .rnd:
                let err = abs(rndTarget[next] - rndPredictor[next])
                rndPredictor[next] += 0.35 * (rndTarget[next] - rndPredictor[next])
                intrinsic = 1.2 * err
            }

            let reward = external + intrinsic
            let target = reward + (next == expGoal ? 0 : 0.95 * q[next].max()!)
            q[s][a] += 0.25 * (target - q[s][a])

            discovered.append(seen.count)
            s = next
            if next == expGoal {
                reached += 1
                stepsToGoal.append(steps)
                break
            }
        }
    }
    return ExpRun(discovered: discovered, reachedGoal: reached, visits: visits, stepsToGoal: stepsToGoal)
}

private func mazeGrid(_ label: String, shade: (Int) -> Float, glyph: (Int) -> String = { _ in "" }, color: (Int) -> Color = { _ in expAccent }) -> ExpGrid {
    ExpGrid(label: label, cells: (0..<expStates).map { s in
        isWall(s) ? ExpCell(shade: 1, glyph: "", color: expWall) : ExpCell(shade: shade(s), glyph: glyph(s), color: color(s))
    })
}

private func landmarkGlyph(_ s: Int) -> String {
    switch s {
    case expStart: "S"
    case expGoal: "G"
    case expNoisy: "?"
    default: ""
    }
}

/// Exploration curves are hundreds of steps long, so thin them for the plot.
private func thin(_ values: [Int], points: Int = 60) -> [Float] {
    if values.isEmpty { return [] }
    let stride = max(1, values.count / points)
    return Swift.stride(from: 0, to: values.count, by: stride).map { Float(values[$0]) }
}

private func visitShade(_ run: ExpRun) -> (Int) -> Float {
    let peak = max(Float(run.visits.max() ?? 0), 1)
    return { Float(run.visits[$0]) / peak }
}

private let freeCount = Float(freeCells.count)

// MARK: - intrinsic_motivation

private func intrinsicMotivationFrames() -> [ExpFrame] {
    let runs = 30
    let plain = (0..<runs).map { explore(seed: 101 + $0 * 37, bonus: .none) }
    let counted = (0..<runs).map { explore(seed: 101 + $0 * 37, bonus: .count) }
    let plainReached = plain.filter { $0.reachedGoal > 0 }.count
    let countReached = counted.filter { $0.reachedGoal > 0 }.count
    let plainSeen = plain.map { $0.discovered.last ?? 0 }.average
    let countSeen = counted.map { $0.discovered.last ?? 0 }.average
    let sample = counted[0]
    let plainSample = plain[0]
    let goalDist = distanceFromStart[expGoal]

    return [
        ExpFrame(
            status: "A maze with exactly one reward, in the far corner, \(goalDist) steps from the start along the only route there. Every other cell pays nothing. There is no gradient to follow, so an agent has to reach the goal by accident before it can learn anything at all.",
            grid: mazeGrid("the maze", shade: { _ in 0.12 }, glyph: landmarkGlyph, color: { _ in expBaseline }),
            readout: "\(freeCells.count) open cells · the goal is \(goalDist) steps away"
        ),
        ExpFrame(
            status: "ε-greedy is the default answer, and here it is close to useless: over \(runs) runs of 40 episodes it reached the goal in \(plainReached) of them, having seen \(fx(plainSeen, 1)) of \(freeCells.count) cells. Undirected noise re-treads the cells near the start, because a random walk returns to where it came from far more often than it pushes outward.",
            grid: mazeGrid("cells by visit count, ε-greedy", shade: visitShade(plainSample), glyph: landmarkGlyph, color: { _ in expBaseline }),
            readout: "\(plainReached) of \(runs) runs ever reached the goal"
        ),
        ExpFrame(
            status: "Intrinsic motivation adds a second reward the agent generates for itself. The simplest version pays 1/√N for arriving somewhere visited N times, so an unvisited cell is worth something and a well-trodden one is worth almost nothing. Crucially the bonus *decays* — otherwise the agent would farm novelty forever instead of eventually exploiting.",
            grid: mazeGrid("the novelty bonus after training — bright means still unexplored",
                           shade: { Float(0.5 / Double(max(1, sample.visits[$0])).squareRoot()) * 2 },
                           glyph: landmarkGlyph, color: { _ in expHighlight }),
            readout: "bonus = 0.5 / √N(s)"
        ),
        ExpFrame(
            status: "The same agent with that bonus reaches the goal in \(countReached) of \(runs) runs and covers \(fx(countSeen, 1)) cells against \(fx(plainSeen, 1)). The external reward is unchanged — the whole difference is that the agent now has a reason to go somewhere new.",
            plot: FramePlot(label: "distinct cells discovered", curves: [
                FrameCurve(label: "ε-greedy", values: thin(plainSample.discovered), color: expBaseline),
                FrameCurve(label: "novelty bonus", values: thin(sample.discovered), color: expGood),
            ], yRange: 0...freeCount, xLabel: "environment steps"),
            readout: "goal reached in \(plainReached) → \(countReached) of \(runs) runs"
        ),
        ExpFrame(
            status: "That is the whole family in one idea: manufacture a reward for visiting the unfamiliar, and let it fade as the unfamiliar becomes familiar. The open question is how to measure \"unfamiliar\" when there are far too many states to count — which is what ICM and RND answer, in two different and not equally robust ways.",
            bars: [FrameBars(label: "cells discovered (mean of \(runs) runs)", values: [Float(plainSeen), Float(countSeen), freeCount], color: expAccent,
                             captions: ["ε-greedy", "novelty", "all cells"])],
            readout: "counting works when you can count; the next two labs are what you do when you cannot"
        ),
    ]
}

// MARK: - icm

private func icmFrames() -> [ExpFrame] {
    let runs = 30
    let plain = (0..<runs).map { explore(seed: 211 + $0 * 41, bonus: .none) }
    let icm = (0..<runs).map { explore(seed: 211 + $0 * 41, bonus: .icm) }
    let icmNoisy = (0..<runs).map { explore(seed: 211 + $0 * 41, bonus: .icm, noisy: true) }
    let plainReached = plain.filter { $0.reachedGoal > 0 }.count
    let icmReached = icm.filter { $0.reachedGoal > 0 }.count
    let icmNoisyReached = icmNoisy.filter { $0.reachedGoal > 0 }.count
    let sample = icm[0]
    let noisySample = icmNoisy[0]
    let cleanNoisyVisits = icm.map { Double($0.visits[expNoisy]) }.average
    let noisyNoisyVisits = icmNoisy.map { Double($0.visits[expNoisy]) }.average
    let noisyShade = visitShade(noisySample)

    return [
        ExpFrame(
            status: "ICM makes curiosity concrete: the agent learns a forward model that predicts what happens next, and is rewarded in proportion to how wrong that prediction was. Surprise is the bonus, so there is nothing to count and nothing to store per state.",
            grid: mazeGrid("the maze", shade: { _ in 0.12 }, glyph: landmarkGlyph, color: { _ in expBaseline }),
            readout: "bonus ∝ ‖predicted next state − actual next state‖"
        ),
        ExpFrame(
            status: "It works for the same reason counting does. A part of the maze the model has never seen is a part it predicts badly, so the agent is drawn there — reaching the goal in \(icmReached) of \(runs) runs against ε-greedy's \(plainReached).",
            plot: FramePlot(label: "distinct cells discovered", curves: [
                FrameCurve(label: "ε-greedy", values: thin(plain[0].discovered), color: expBaseline),
                FrameCurve(label: "ICM", values: thin(sample.discovered), color: expGood),
            ], yRange: 0...freeCount, xLabel: "environment steps"),
            readout: "goal reached in \(plainReached) → \(icmReached) of \(runs) runs"
        ),
        ExpFrame(
            status: "And it fades the way a bonus must: once the model has seen a transition enough times it predicts it correctly, the error goes to zero, and the cell stops being interesting. The bright cells here are the ones whose dynamics the model has not yet pinned down.",
            grid: mazeGrid("cells by visit count under ICM", shade: visitShade(sample), glyph: landmarkGlyph, color: { _ in expHighlight }),
            readout: "prediction error decays with experience — as long as there is something to learn"
        ),
        ExpFrame(
            status: "Now mark one cell '?' and make its outcome genuinely random: stepping on it throws the agent to a random neighbour. No forward model can ever predict that, so the prediction error never falls and the bonus never fades. The agent visits it \(fx(noisyNoisyVisits, 1)) times per run against \(fx(cleanNoisyVisits, 1)) when the same cell is deterministic.",
            grid: mazeGrid("cells by visit count, with the '?' cell stochastic", shade: noisyShade, glyph: landmarkGlyph,
                           color: { $0 == expNoisy ? expBad : expHighlight }),
            readout: "visits to the unpredictable cell: \(fx(cleanNoisyVisits, 1)) → \(fx(noisyNoisyVisits, 1))"
        ),
        ExpFrame(
            status: "This is the noisy-TV problem, and it costs real performance: with one unpredictable cell in the maze, the goal-reach rate falls from \(icmReached) to \(icmNoisyReached) of \(runs) runs. The agent is not malfunctioning — it is maximising exactly what it was told to, and an unpredictable thing is infinitely surprising. One honest caveat about this lab: ICM's published form predicts in a *learned feature space*, trained by an inverse model to encode only what the agent's actions can influence, and that is specifically meant to filter noise like this out. The version measured here predicts raw next states, so it shows the failure in its undiluted form. The mitigation is real but partial — noise the agent can influence still leaks through the features, which is why RND was proposed.",
            bars: [FrameBars(label: "runs reaching the goal", values: [Float(plainReached), Float(icmReached), Float(icmNoisyReached)], color: expAccent,
                             captions: ["ε-greedy", "ICM", "ICM + noise"])],
            readout: "\(icmReached) → \(icmNoisyReached) of \(runs) runs once something unpredictable exists"
        ),
    ]
}

// MARK: - rnd

private func rndFrames() -> [ExpFrame] {
    let runs = 30
    // Same seeds as the ICM lab, so the ICM figures quoted in both labs agree.
    let plain = (0..<runs).map { explore(seed: 211 + $0 * 41, bonus: .none) }
    let rnd = (0..<runs).map { explore(seed: 211 + $0 * 41, bonus: .rnd) }
    let rndNoisy = (0..<runs).map { explore(seed: 211 + $0 * 41, bonus: .rnd, noisy: true) }
    let icmNoisy = (0..<runs).map { explore(seed: 211 + $0 * 41, bonus: .icm, noisy: true) }
    let plainReached = plain.filter { $0.reachedGoal > 0 }.count
    let rndReached = rnd.filter { $0.reachedGoal > 0 }.count
    let rndNoisyReached = rndNoisy.filter { $0.reachedGoal > 0 }.count
    let icmNoisyReached = icmNoisy.filter { $0.reachedGoal > 0 }.count
    let rndNoisyVisits = rndNoisy.map { Double($0.visits[expNoisy]) }.average
    let icmNoisyVisits = icmNoisy.map { Double($0.visits[expNoisy]) }.average
    let sample = rnd[0]

    // The decay of each bonus at the unpredictable cell, measured directly.
    var rndDecay: [Float] = []
    var icmDecay: [Float] = []
    do {
        var rng = Lcg(4242)
        let target = rng.next()
        var predictor = 0.0
        var counts: [Int: Int] = [:]
        let neighbours = distinctMoves(expNoisy)
        for _ in 0..<40 {
            rndDecay.append(Float(abs(target - predictor)))
            predictor += 0.35 * (target - predictor)
            let landed = neighbours[rng.nextInt(neighbours.count)]
            let total = counts.values.reduce(0, +)
            let p = total == 0 ? 0 : Double(counts[landed] ?? 0) / Double(total)
            icmDecay.append(Float(1 - p))
            counts[landed, default: 0] += 1
        }
    }
    let icmLast = Double(icmDecay.last!)
    let rndLast = Double(rndDecay.last!)

    return [
        ExpFrame(
            status: "RND keeps the idea of \"bonus = prediction error\" but changes what is being predicted. A fixed network with random weights maps each state to a random number; a second network is trained to reproduce it. The error is large where the predictor has not been trained — that is, where the agent has not been.",
            grid: mazeGrid("the maze", shade: { _ in 0.12 }, glyph: landmarkGlyph, color: { _ in expBaseline }),
            readout: "bonus ∝ ‖predictor(s) − fixed random target(s)‖"
        ),
        ExpFrame(
            status: "As an exploration signal it does the same job: \(rndReached) of \(runs) runs reach the goal, against ε-greedy's \(plainReached).",
            plot: FramePlot(label: "distinct cells discovered", curves: [
                FrameCurve(label: "ε-greedy", values: thin(plain[0].discovered), color: expBaseline),
                FrameCurve(label: "RND", values: thin(sample.discovered), color: expGood),
            ], yRange: 0...freeCount, xLabel: "environment steps"),
            readout: "goal reached in \(plainReached) → \(rndReached) of \(runs) runs"
        ),
        ExpFrame(
            status: "The difference from ICM is what the error depends on. RND's target is a fixed function of the state alone — the environment's randomness is not in it. At the unpredictable '?' cell, RND's error decays to \(fx(rndLast, 3)) within 40 visits, while ICM's prediction error is still \(fx(icmLast, 3)), because the thing ICM is trying to predict never becomes predictable.",
            plot: FramePlot(label: "bonus at the unpredictable cell, by number of visits", curves: [
                FrameCurve(label: "ICM (forward-model error)", values: icmDecay, color: expBad),
                FrameCurve(label: "RND (target-prediction error)", values: rndDecay, color: expGood),
            ], yRange: 0...1.05, xLabel: "visits to that cell"),
            readout: "after 40 visits — ICM \(fx(icmLast, 3)) · RND \(fx(rndLast, 3))"
        ),
        ExpFrame(
            status: "So the noisy cell that trapped ICM does not trap RND: with the same stochastic cell in the maze, RND visits it \(fx(rndNoisyVisits, 1)) times a run against ICM's \(fx(icmNoisyVisits, 1)), and reaches the goal in \(rndNoisyReached) of \(runs) runs against ICM's \(icmNoisyReached).",
            bars: [
                FrameBars(label: "visits to the unpredictable cell", values: [Float(icmNoisyVisits), Float(rndNoisyVisits)], color: expBad, captions: ["ICM", "RND"]),
                FrameBars(label: "runs reaching the goal, noisy maze", values: [Float(icmNoisyReached), Float(rndNoisyReached)], color: expAccent, captions: ["ICM", "RND"]),
            ],
            readout: "immunity to noise comes from predicting a *deterministic* function of the state"
        ),
        ExpFrame(
            status: "The honest caveat is that RND buys this by measuring novelty rather than learnability. It cannot distinguish a state that is genuinely worth investigating from one that is merely unvisited, and its bonus is defined against an arbitrary random function, so it says nothing about the dynamics. ICM's signal is more meaningful when the environment is predictable — and its feature-space form recovers much of this robustness — but RND gets the robustness by construction rather than by learning good features, which is why it is the easier thing to get working.",
            grid: mazeGrid("cells by visit count under RND", shade: visitShade(sample), glyph: landmarkGlyph, color: { _ in expGood }),
            readout: "novelty, not learnability — robust for the same reason it is shallow"
        ),
    ]
}

// MARK: - meta_rl

private func metaRlFrames() -> [ExpFrame] {
    // Goals only ever appear deep in the maze, so knowing that is worth something.
    let candidates = Array(freeCells.sortedByDescending { distanceFromStart[$0] }.prefix(4))

    /// Steps for an uninformed searcher — a random walk.
    func scratchSteps(_ goal: Int, _ seed: Int, budget: Int = 400) -> Int {
        var rng = Lcg(seed)
        var s = expStart
        for t in 1...budget {
            s = moveFrom(s, rng.nextInt(4))
            if s == goal { return t }
        }
        return budget
    }

    /// Walks a list of cells in order, paying the travel between them, stopping at the goal.
    func walkOrder(_ goal: Int, _ order: [Int]) -> Int {
        var steps = 0
        var at = expStart
        for cell in order {
            steps += pathLength(at, cell)
            at = cell
            if cell == goal { return steps }
        }
        return steps + pathLength(at, goal)
    }

    let order = candidates.sortedBy { distanceFromStart[$0] }
    let sweepOrder = freeCells.sortedBy { distanceFromStart[$0] }
    let scratch = candidates.flatMap { g in (0..<20).map { scratchSteps(g, 900 + $0 * 17) } }
    let scratchMean = scratch.average
    let metaMean = candidates.map { walkOrder($0, order) }.average
    let sweepMean = candidates.map { walkOrder($0, sweepOrder) }.average

    // Outside the distribution the prior is a detour rather than a shortcut.
    let outsider = freeCells.filter { !candidates.contains($0) && $0 != expStart }.min { distanceFromStart[$0] < distanceFromStart[$1] }!
    let scratchOut = (0..<20).map { scratchSteps(outsider, 1500 + $0 * 23) }.average
    let metaOut = Double(walkOrder(outsider, order))
    let sweepOut = Double(walkOrder(outsider, sweepOrder))
    let maxDist = Float(distanceFromStart.max()!)

    let whereGoalsLive = mazeGrid("where the goal can be",
                                  shade: { candidates.contains($0) ? 0.9 : 0.1 },
                                  glyph: { candidates.contains($0) ? "★" : $0 == expStart ? "S" : "" },
                                  color: { candidates.contains($0) ? expHighlight : expBaseline })

    return [
        ExpFrame(
            status: "Meta-RL changes what is being learned. Instead of one task, the agent trains across a distribution of them — here the goal is always in one of \(candidates.count) places, never anywhere else — and what it carries to a new task is not a policy but a way of finding one.",
            grid: whereGoalsLive,
            readout: "\(candidates.count) possible goals · the agent is told none of them"
        ),
        ExpFrame(
            status: "Two ways to face a new task with no prior. Wander at random and it takes \(fx(scratchMean, 0)) steps on average to stumble onto the goal; sweep the maze systematically, nearest cells first, and it takes \(fx(sweepMean, 0)). The sweep is the honest baseline — most of what looks like a meta-RL win over random exploration is really just the value of searching in *some* order.",
            grid: mazeGrid("a systematic sweep, nearest cells first", shade: { 1 - Float(distanceFromStart[$0]) / maxDist },
                           glyph: landmarkGlyph, color: { _ in expBaseline }),
            bars: [FrameBars(label: "steps to find the goal, no prior", values: [Float(scratchMean), Float(sweepMean)], color: expBaseline,
                             captions: ["random walk", "systematic sweep"])],
            readout: "random \(fx(scratchMean, 0)) · systematic \(fx(sweepMean, 0))"
        ),
        ExpFrame(
            status: "The meta-learner has seen enough tasks to know where goals live, so on a new one it checks the \(candidates.count) candidates in order of distance rather than exploring blindly — \(fx(metaMean, 0)) steps on average. Nothing about the maze changed; what changed is the prior the agent brought to it.",
            grid: mazeGrid("the learned search order", shade: { candidates.contains($0) ? 0.9 : 0.1 },
                           glyph: { s in order.firstIndex(of: s).map { "\($0 + 1)" } ?? (s == expStart ? "S" : "") },
                           color: { candidates.contains($0) ? expGood : expBaseline }),
            bars: [FrameBars(label: "steps to find the goal on a new task", values: [Float(scratchMean), Float(sweepMean), Float(metaMean)], color: expAccent,
                             captions: ["random walk", "systematic sweep", "meta-learned"])],
            readout: "random \(fx(scratchMean, 0)) · sweep \(fx(sweepMean, 0)) · meta \(fx(metaMean, 0)) steps"
        ),
        ExpFrame(
            status: "Now put the goal somewhere no training task ever put it — a cell right next to the start. The meta-learner marches off to its \(candidates.count) deep candidates first and takes \(fx(metaOut, 0)) steps to come back for it. The systematic sweep, which had no prior to be wrong about, finds it in \(fx(sweepOut, 0)); even the random walk manages \(fx(scratchOut, 0)). The prior is not a mild detour here — on this task it is worse than having no prior at all, and worse by more than it was better in-distribution.",
            grid: mazeGrid("a goal outside the training distribution",
                           shade: { $0 == outsider ? 1 : candidates.contains($0) ? 0.5 : 0.1 },
                           glyph: { $0 == outsider ? "G" : candidates.contains($0) ? "★" : $0 == expStart ? "S" : "" },
                           color: { $0 == outsider ? expBad : candidates.contains($0) ? expHighlight : expBaseline }),
            bars: [FrameBars(label: "steps to an out-of-distribution goal", values: [Float(scratchOut), Float(sweepOut), Float(metaOut)], color: expBad,
                             captions: ["random walk", "systematic sweep", "meta-learned"])],
            readout: "meta in-distribution \(fx(metaMean, 0)) · out of it \(fx(metaOut, 0)) against a sweep's \(fx(sweepOut, 0))"
        ),
        ExpFrame(
            status: "So meta-RL is a bet on the task distribution being representative, and the measurements above are both sides of that bet: \(fx(sweepMean / metaMean, 1))× faster than an unprejudiced search when the bet is right, \(fx(metaOut / sweepOut, 0))× slower when it is wrong. What the real algorithms differ on is only where the prior is kept: MAML in the initial weights, RL² in a recurrent state that persists across episodes.",
            grid: whereGoalsLive,
            readout: "fast inside the distribution, slower outside it, by exactly the same mechanism"
        ),
    ]
}

// MARK: - Config

private let explorationConfigs: [String: ExpConfig] = [
    "intrinsic_motivation": ExpConfig(
        intro: "One reward, 26 steps away, and nothing in between. What a self-generated novelty bonus buys, measured over 30 runs against plain ε-greedy.",
        legend: [(expBaseline, "ε-greedy"), (expGood, "With bonus"), (expHighlight, "Novelty")],
        build: intrinsicMotivationFrames
    ),
    "icm": ExpConfig(
        intro: "Curiosity as forward-model error — and the one cell in the maze that breaks it, with the cost measured in goals reached.",
        legend: [(expBaseline, "ε-greedy"), (expGood, "ICM"), (expBad, "Unpredictable")],
        build: icmFrames
    ),
    "rnd": ExpConfig(
        intro: "Predicting a fixed random function of the state instead of the dynamics. The two bonuses are measured side by side at the cell that defeats one of them.",
        legend: [(expGood, "RND"), (expBad, "ICM"), (expBaseline, "ε-greedy")],
        build: rndFrames
    ),
    "meta_rl": ExpConfig(
        intro: "Learning across a distribution of tasks rather than one — with the adaptation speed measured both inside that distribution and outside it.",
        legend: [(expHighlight, "Possible goals"), (expGood, "Meta-learned"), (expBad, "Out of distribution")],
        build: metaRlFrames
    ),
]

// MARK: - UI

struct ExplorationLab: View {
    private let config: ExpConfig
    private let key: String
    @Environment(\.palette) private var palette

    init(topicId: String) {
        key = explorationConfigs[topicId] == nil ? "intrinsic_motivation" : topicId
        config = explorationConfigs[key]!
    }

    var body: some View {
        let key = self.key
        AsyncFrameLab(key: "exploration:\(key)", speedMs: 1200, build: { explorationConfigs[key]!.build() }) { frames, playback in
            content(frames, playback)
        }
    }

    @ViewBuilder
    private func content(_ frames: [ExpFrame], _ playback: PlaybackState) -> some View {
        let frame = frames[min(playback.index, frames.count - 1)]
        LabCard {
            LabIntro(text: config.intro)
            if let grid = frame.grid { MazeGridView(grid: grid).padding(.top, 12) }
            if let plot = frame.plot { FramePlotView(plot: plot, xLabelInline: false).padding(.top, 12) }
            ForEach(frame.bars.indices, id: \.self) { FrameBarsView(bar: frame.bars[$0], negativeColor: expBad).padding(.top, 12) }
            FramePlayerFooter(readout: frame.readout, status: frame.status, legend: config.legend, playback: playback, captions: frames.map(\.status))
        }
    }
}

private struct MazeGridView: View {
    let grid: ExpGrid
    @Environment(\.palette) private var palette

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text(grid.label).font(.labelSmall).foregroundStyle(palette.muted)
            Grid(horizontalSpacing: 3, verticalSpacing: 3) {
                ForEach(0..<expRows, id: \.self) { r in
                    GridRow {
                        ForEach(0..<expCols, id: \.self) { c in
                            let cell = grid.cells[r * expCols + c]
                            RoundedRectangle(cornerRadius: 6)
                                .fill(cell.color.opacity(Double(0.12 + 0.68 * min(max(cell.shade, 0), 1))))
                                .aspectRatio(1, contentMode: .fit)
                                .overlay {
                                    if !cell.glyph.isEmpty {
                                        Text(cell.glyph).font(AppFont.sans(11, .bold))
                                    }
                                }
                        }
                    }
                }
            }
            .padding(.top, 4)
        }
    }
}
