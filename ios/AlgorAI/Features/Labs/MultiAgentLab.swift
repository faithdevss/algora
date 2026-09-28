import SwiftUI

// Port of MultiAgentSection.kt. Four topics, two environments, one question: when several agents
// learn at once, what does each one's learning target depend on?
//   A. The climb game — 2 agents × 3 actions, cooperative, big miscoordination penalty (iql, vdn, qmix).
//   B. A continuous 2-agent task over a grid of joint actions (maddpg).
// Every number a frame states is produced here.

private struct MaCell { let text: String; let intensity: Float; var highlight = false }

private struct MaMatrix {
    let label: String
    let rowLabels: [String]
    let colLabels: [String]
    let cells: [[MaCell]]
    var positive: Color = SimColors.blue
}

private struct MaFrame {
    let status: String
    var matrices: [MaMatrix] = []
    var bars: [FrameBars] = []
    var plot: FramePlot?
    var readout: String?
}

private struct MaConfig {
    let intro: String
    let legend: [(Color, String)]
    let build: () -> [MaFrame]
}

private let maBaseline = SimColors.grey
private let maGood = SimColors.green
private let maBad = Color(hex: 0xEC4899)
private let maAccent = SimColors.blue
private let maHighlight = Color(hex: 0x7C3AED)

private struct Joint: Hashable { let a: Int; let b: Int }

// MARK: - Environment A — the climb game

private let maActions = 3

private let climbPayoff: [[Double]] = [
    [8, -12, -12],
    [-12, 0, 0],
    [-12, 0, 0],
]

/// A game whose value really is a sum of per-agent terms — the case VDN is built for.
private let additiveA: [Double] = [3, 1, -1]
private let additiveB: [Double] = [2, 0, -2]
private let additivePayoff = (0..<maActions).map { i in (0..<maActions).map { j in additiveA[i] + additiveB[j] } }

/// Monotonic in each agent's contribution but not a sum: a product of positive terms.
private let monotoneA: [Double] = [3, 2, 1]
private let monotoneB: [Double] = [3, 2, 1]
private let monotonePayoff = (0..<maActions).map { i in (0..<maActions).map { j in monotoneA[i] * monotoneB[j] } }

private func bestJoint(_ payoff: [[Double]]) -> Joint {
    var best = Joint(a: 0, b: 0)
    for i in 0..<maActions {
        for j in 0..<maActions where payoff[i][j] > payoff[best.a][best.b] { best = Joint(a: i, b: j) }
    }
    return best
}

private func payoffAt(_ payoff: [[Double]], _ j: Joint) -> Double { payoff[j.a][j.b] }

private func matrixOf(_ label: String, _ payoff: [[Double]], highlight: Set<Joint> = [], digits: Int = 1) -> MaMatrix {
    let peak = max(payoff.flatMap { $0 }.map { abs($0) }.max() ?? 0, 0.001)
    return MaMatrix(
        label: label,
        rowLabels: (0..<maActions).map { "A\($0)" },
        colLabels: (0..<maActions).map { "B\($0)" },
        cells: (0..<maActions).map { i in
            (0..<maActions).map { j in
                MaCell(text: fx(payoff[i][j], digits), intensity: Float(payoff[i][j] / peak), highlight: highlight.contains(Joint(a: i, b: j)))
            }
        }
    )
}

// MARK: - Independent Q-learning

private struct IqlRun { let q1: [Double]; let q2: [Double]; let joint: Joint; let perceivedA0: [Double] }

/// Two independent learners, each treating the partner as part of the environment.
private func runIql(seed: Int, episodes: Int = 600, eps: Double = 0.2, alpha: Double = 0.05) -> IqlRun {
    var rng = Lcg(seed)
    var q1 = [Double](repeating: 0, count: maActions)
    var q2 = [Double](repeating: 0, count: maActions)
    var perceived: [Double] = []

    func pick(_ q: [Double]) -> Int { rng.next() < eps ? rng.nextInt(maActions) : argmaxFirst(q) }

    for t in 0..<episodes {
        let a1 = pick(q1)
        let a2 = pick(q2)
        let r = climbPayoff[a1][a2]
        q1[a1] += alpha * (r - q1[a1])
        q2[a2] += alpha * (r - q2[a2])
        if t % 20 == 0 { perceived.append(q1[0]) }
    }
    return IqlRun(q1: q1, q2: q2, joint: Joint(a: argmaxFirst(q1), b: argmaxFirst(q2)), perceivedA0: perceived)
}

private func floats(_ xs: [Double]) -> [Float] { xs.map { Float($0) } }

private func iqlFrames() -> [MaFrame] {
    let optimal = bestJoint(climbPayoff)
    let runs = (0..<200).map { runIql(seed: 17 + $0 * 31) }
    let reachedOptimum = runs.filter { $0.joint == optimal }.count
    let sample = runs[0]
    let rowMean = (0..<maActions).map { i in climbPayoff[i].reduce(0, +) / Double(maActions) }
    let expectedA0Uniform = rowMean[0]
    let expectedA1Uniform = rowMean[1]
    let optimumValue = payoffAt(climbPayoff, optimal)

    // Bucket the runs by what the joint action they settled on actually pays.
    var byPayoff: [Double: Int] = [:]
    for run in runs { byPayoff[payoffAt(climbPayoff, run.joint), default: 0] += 1 }
    let payoffLevels = byPayoff.keys.sorted(by: >)
    let meanPayoff = runs.reduce(0.0) { $0 + payoffAt(climbPayoff, $1.joint) } / Double(runs.count)
    let others = payoffLevels.filter { $0 != optimumValue }.map { "\(byPayoff[$0]!) runs worth \(fx($0, 0))" }.joined(separator: " and ")

    return [
        MaFrame(
            status: "Two agents, three actions each, one shared payoff. The pair (A\(optimal.a), B\(optimal.b)) pays \(fx(optimumValue, 0)) — the best outcome available — but either agent playing its half while the partner does something else loses 12.",
            matrices: [matrixOf("team payoff", climbPayoff, highlight: [optimal])],
            readout: "optimum \(fx(optimumValue, 0)) at (A\(optimal.a), B\(optimal.b))"
        ),
        MaFrame(
            status: "Independent Q-learning is the obvious first thing to try: each agent runs ordinary Q-learning on its own actions and treats the other as part of the environment. Nothing is shared — which is exactly why it scales to any number of agents.",
            bars: [
                FrameBars(label: "agent 1 Q after training (one run)", values: floats(sample.q1), color: maAccent, captions: ["A0", "A1", "A2"]),
                FrameBars(label: "agent 2 Q after training (one run)", values: floats(sample.q2), color: maAccent, captions: ["B0", "B1", "B2"]),
            ],
            readout: "this run settled on (A\(sample.joint.a), B\(sample.joint.b))"
        ),
        MaFrame(
            status: "The trouble is what A0 is worth. Against a partner still exploring roughly uniformly, playing A0 averages \(fx(expectedA0Uniform)) while the timid A1 averages \(fx(expectedA1Uniform)). The action that is half of the best outcome in the game looks like the worst action on the board, and it looks that way because of the partner, not the environment.",
            bars: [FrameBars(label: "average payoff against an exploring partner", values: floats(rowMean), color: maBad, captions: ["A0", "A1", "A2"])],
            readout: "A0 averages \(fx(expectedA0Uniform)) — punished for the partner's mistakes"
        ),
        MaFrame(
            status: "That is non-stationarity in one number: agent 1's estimate of A0 keeps moving because the distribution generating its rewards keeps moving. Ordinary Q-learning assumes a fixed environment, and here the environment is another learner.",
            plot: FramePlot(label: "agent 1's Q(A0) during training", curves: [FrameCurve(label: "Q(A0)", values: floats(sample.perceivedA0), color: maBad)],
                            yRange: -13...9, xLabel: "every 20th episode"),
            readout: "Q(A0) ends at \(fx(sample.q1[0])) against a true best-case of 8"
        ),
        MaFrame(
            status: "Over \(runs.count) independent runs, IQL reaches the optimal pair \(reachedOptimum) times — \(fx(100.0 * Double(reachedOptimum) / Double(runs.count), 0))% — and the rest settle on \(others), averaging \(fx(meanPayoff)) across all of them. A coin flip on whether the team coordinates at all.",
            bars: [FrameBars(label: "runs by the payoff they settled on", values: payoffLevels.map { Float(byPayoff[$0]!) / Float(runs.count) },
                             color: maAccent, captions: payoffLevels.map { fx($0, 0) })],
            readout: "\(reachedOptimum) of \(runs.count) runs found the optimum · mean payoff \(fx(meanPayoff))"
        ),
        MaFrame(
            status: "IQL is not broken here — it is doing exactly what a single-agent algorithm should do when its best action keeps being punished by someone else's exploration. It stays the baseline every other method is measured against, because it needs no communication, no shared value and no extra machinery, and it scales linearly with the number of agents. What follows are the two standard ways of keeping that decentralised execution while training the agents together.",
            matrices: [matrixOf("team payoff", climbPayoff, highlight: [optimal])],
            readout: "no communication, linear scaling, and a coin flip on coordination"
        ),
    ]
}

// MARK: - Value factorisation

private struct FitResult {
    let mse: Double
    let q1: [Double]
    let q2: [Double]
    let predicted: [[Double]]
    /// The joint action decentralised execution would produce: each agent's own argmax.
    var greedyJoint: Joint { Joint(a: argmaxFirst(q1), b: argmaxFirst(q2)) }
}

private func meanSquaredError(_ predicted: [[Double]], _ payoff: [[Double]]) -> Double {
    var mse = 0.0
    for i in 0..<maActions {
        for j in 0..<maActions {
            let d = predicted[i][j] - payoff[i][j]
            mse += d * d
        }
    }
    return mse / Double(maActions * maActions)
}

/// Least-squares fit of Q_tot = Q1(a1) + Q2(a2) to the payoff matrix.
private func fitVdn(_ payoff: [[Double]], steps: Int = 20000, lr: Double = 0.01) -> FitResult {
    var q1 = [Double](repeating: 0, count: maActions)
    var q2 = [Double](repeating: 0, count: maActions)
    let n = Double(maActions * maActions)
    for _ in 0..<steps {
        var g1 = [Double](repeating: 0, count: maActions)
        var g2 = [Double](repeating: 0, count: maActions)
        for i in 0..<maActions {
            for j in 0..<maActions {
                let err = (q1[i] + q2[j]) - payoff[i][j]
                g1[i] += 2 * err
                g2[j] += 2 * err
            }
        }
        for i in 0..<maActions {
            q1[i] -= lr * g1[i] / n
            q2[i] -= lr * g2[i] / n
        }
    }
    let predicted = (0..<maActions).map { i in (0..<maActions).map { j in q1[i] + q2[j] } }
    return FitResult(mse: meanSquaredError(predicted, payoff), q1: q1, q2: q2, predicted: predicted)
}

/// QMIX's mixer at its smallest: a hidden layer with non-negative (squared) weights and an ELU,
/// then a non-negative output layer. Squaring keeps each agent's argmax consistent with the team's.
private func fitQmix(_ payoff: [[Double]], steps: Int = 40000, lr: Double = 0.004, seed: Int = 5) -> FitResult {
    var rng = Lcg(seed)
    var q1 = (0..<maActions).map { _ in rng.next() - 0.5 }
    var q2 = (0..<maActions).map { _ in rng.next() - 0.5 }
    let hidden = 4
    var w1 = (0..<hidden).map { _ in rng.next() }
    var w2 = (0..<hidden).map { _ in rng.next() }
    var b1 = [Double](repeating: 0, count: hidden)
    var wo = (0..<hidden).map { _ in rng.next() }
    var bo = 0.0

    func elu(_ x: Double) -> Double { x >= 0 ? x : exp(x) - 1 }
    func dElu(_ x: Double) -> Double { x >= 0 ? 1 : exp(x) }

    func forward(_ a: Int, _ b: Int) -> (Double, [Double]) {
        var pre = [Double](repeating: 0, count: hidden)
        for h in 0..<hidden { pre[h] = w1[h] * w1[h] * q1[a] + w2[h] * w2[h] * q2[b] + b1[h] }
        var out = bo
        for h in 0..<hidden { out += wo[h] * wo[h] * elu(pre[h]) }
        return (out, pre)
    }

    let n = Double(maActions * maActions)
    for _ in 0..<steps {
        var gq1 = [Double](repeating: 0, count: maActions)
        var gq2 = [Double](repeating: 0, count: maActions)
        var gw1 = [Double](repeating: 0, count: hidden)
        var gw2 = [Double](repeating: 0, count: hidden)
        var gb1 = [Double](repeating: 0, count: hidden)
        var gwo = [Double](repeating: 0, count: hidden)
        var gbo = 0.0
        for i in 0..<maActions {
            for j in 0..<maActions {
                let (out, pre) = forward(i, j)
                let err = 2 * (out - payoff[i][j])
                gbo += err
                for h in 0..<hidden {
                    gwo[h] += err * 2 * wo[h] * elu(pre[h])
                    let chain = err * wo[h] * wo[h] * dElu(pre[h])
                    gb1[h] += chain
                    gw1[h] += chain * 2 * w1[h] * q1[i]
                    gw2[h] += chain * 2 * w2[h] * q2[j]
                    gq1[i] += chain * w1[h] * w1[h]
                    gq2[j] += chain * w2[h] * w2[h]
                }
            }
        }
        for h in 0..<hidden {
            wo[h] -= lr * gwo[h] / n
            w1[h] -= lr * gw1[h] / n
            w2[h] -= lr * gw2[h] / n
            b1[h] -= lr * gb1[h] / n
        }
        bo -= lr * gbo / n
        for i in 0..<maActions {
            q1[i] -= lr * gq1[i] / n
            q2[i] -= lr * gq2[i] / n
        }
    }
    let predicted = (0..<maActions).map { i in (0..<maActions).map { j in forward(i, j).0 } }
    return FitResult(mse: meanSquaredError(predicted, payoff), q1: q1, q2: q2, predicted: predicted)
}

private func vdnFrames() -> [MaFrame] {
    let additiveOptimum = bestJoint(additivePayoff)
    let climbOptimum = bestJoint(climbPayoff)
    let fitAdditive = fitVdn(additivePayoff)
    let fitClimb = fitVdn(climbPayoff)
    let climbGreedyPays = payoffAt(climbPayoff, fitClimb.greedyJoint)

    return [
        MaFrame(
            status: "VDN's answer to the coordination problem is to train centrally and execute locally. One team reward trains everything, but the joint value is constrained to be a sum of per-agent values — so at run time each agent can just take its own argmax and no communication is needed.",
            matrices: [matrixOf("team payoff (additive game)", additivePayoff, highlight: [additiveOptimum])],
            readout: "Q_tot = Q₁(a₁) + Q₂(a₂)"
        ),
        MaFrame(
            status: "When the game really is additive, the constraint costs nothing. Fitting the sum to this payoff table leaves a mean squared error of \(fx(fitAdditive.mse, 4)) — an exact fit — and each agent's argmax picks out (A\(fitAdditive.greedyJoint.a), B\(fitAdditive.greedyJoint.b)), which is the true optimum.",
            matrices: [matrixOf("what the sum represents", fitAdditive.predicted, highlight: [fitAdditive.greedyJoint])],
            bars: [
                FrameBars(label: "learned Q₁", values: floats(fitAdditive.q1), color: maAccent, captions: ["A0", "A1", "A2"]),
                FrameBars(label: "learned Q₂", values: floats(fitAdditive.q2), color: maAccent, captions: ["B0", "B1", "B2"]),
            ],
            readout: "MSE \(fx(fitAdditive.mse, 4)) · greedy joint = the optimum"
        ),
        MaFrame(
            status: "Now the climb game, where the payoff is emphatically not a sum: A0 is worth +8 or −12 depending entirely on what the partner does, and a per-agent term cannot express \"depends entirely on the partner\".",
            matrices: [matrixOf("team payoff (climb game)", climbPayoff, highlight: [climbOptimum])]
        ),
        MaFrame(
            status: "The best sum available leaves a mean squared error of \(fx(fitClimb.mse, 1)), and the table it does represent is a completely different game. Decentralised argmax now picks (A\(fitClimb.greedyJoint.a), B\(fitClimb.greedyJoint.b)), worth \(fx(climbGreedyPays, 0)) rather than \(fx(payoffAt(climbPayoff, climbOptimum), 0)).",
            matrices: [
                matrixOf("climb game", climbPayoff, highlight: [climbOptimum]),
                matrixOf("what the sum can represent", fitClimb.predicted, highlight: [fitClimb.greedyJoint]),
            ],
            readout: "MSE \(fx(fitClimb.mse, 1)) · greedy joint pays \(fx(climbGreedyPays, 0))"
        ),
        MaFrame(
            status: "So the additive form is a real restriction, not a formality — it buys decentralised execution and pays for it in what the team value can say. QMIX keeps the decentralisation and widens the class of functions, which is the next lab.",
            bars: [FrameBars(label: "fit error by game", values: [Float(fitAdditive.mse), Float(fitClimb.mse)], color: maBad, captions: ["additive", "climb"])],
            readout: "MSE \(fx(fitAdditive.mse, 4)) → \(fx(fitClimb.mse, 1))"
        ),
    ]
}

private func qmixFrames() -> [MaFrame] {
    let monotoneOptimum = bestJoint(monotonePayoff)
    let climbOptimum = bestJoint(climbPayoff)
    let vdnMonotone = fitVdn(monotonePayoff)
    let qmixMonotone = fitQmix(monotonePayoff)
    let vdnClimb = fitVdn(climbPayoff)
    let qmixClimb = fitQmix(climbPayoff)

    return [
        MaFrame(
            status: "QMIX replaces VDN's sum with a learned mixing network, constrained so that every weight is non-negative. That single constraint is what preserves decentralised execution: if raising any agent's own Q can never lower the team value, then each agent's argmax agrees with the team's.",
            matrices: [matrixOf("team payoff (monotone, non-additive)", monotonePayoff, highlight: [monotoneOptimum])],
            readout: "∂Q_tot / ∂Qᵢ ≥ 0"
        ),
        MaFrame(
            status: "This payoff is a product, not a sum — monotone in each agent's contribution, but with an interaction a sum cannot capture. VDN's best fit leaves \(fx(vdnMonotone.mse, 3)); the monotonic mixer gets it to \(fx(qmixMonotone.mse, 3)). The sum is a special case of monotonic mixing, so QMIX can never do worse, and here it does measurably better.",
            matrices: [
                matrixOf("VDN's best sum", vdnMonotone.predicted),
                matrixOf("QMIX's mixer", qmixMonotone.predicted, highlight: [qmixMonotone.greedyJoint]),
            ],
            bars: [FrameBars(label: "fit error", values: [Float(vdnMonotone.mse), Float(qmixMonotone.mse)], color: maAccent, captions: ["VDN", "QMIX"])],
            readout: "MSE \(fx(vdnMonotone.mse, 3)) → \(fx(qmixMonotone.mse, 3)), and both keep the right argmax"
        ),
        MaFrame(
            status: "Then the climb game, which is not monotone: whether raising Q(A0) should raise the team value depends on the partner's action, and the mixer is forbidden from expressing that. VDN leaves \(fx(vdnClimb.mse, 1)), QMIX \(fx(qmixClimb.mse, 1)) — better, and still nowhere near right.",
            matrices: [
                matrixOf("climb game", climbPayoff, highlight: [climbOptimum]),
                matrixOf("QMIX's best monotonic fit", qmixClimb.predicted, highlight: [qmixClimb.greedyJoint]),
            ],
            readout: "MSE: VDN \(fx(vdnClimb.mse, 1)) · QMIX \(fx(qmixClimb.mse, 1))"
        ),
        MaFrame(
            status: "And the consequence is the one that matters: decentralised argmax under QMIX picks (A\(qmixClimb.greedyJoint.a), B\(qmixClimb.greedyJoint.b)), worth \(fx(payoffAt(climbPayoff, qmixClimb.greedyJoint), 0)) instead of \(fx(payoffAt(climbPayoff, climbOptimum), 0)). Monotonicity is not a technicality that buys a little tractability — it is a hard limit on which games the factorisation can solve at all, and it is why methods like QTRAN and QPLEX exist.",
            bars: [
                FrameBars(label: "fit error on the climb game", values: [Float(vdnClimb.mse), Float(qmixClimb.mse)], color: maBad, captions: ["VDN", "QMIX"]),
                FrameBars(label: "payoff of the joint action each method executes", values: [
                    Float(payoffAt(climbPayoff, vdnClimb.greedyJoint)),
                    Float(payoffAt(climbPayoff, qmixClimb.greedyJoint)),
                    Float(payoffAt(climbPayoff, climbOptimum)),
                ], color: maAccent, captions: ["VDN", "QMIX", "optimum"]),
            ],
            readout: "QMIX ≥ VDN everywhere, and both are bounded by the monotonicity constraint"
        ),
    ]
}

// MARK: - Environment B — a continuous 2-agent task, for MADDPG

private let maGrid = 21

private func actionAt(_ index: Int) -> Double { -1 + 2 * Double(index) / Double(maGrid - 1) }

/// Reward over the joint action. The cross term is what an independent critic cannot see.
private func jointReward(_ a1: Double, _ a2: Double) -> Double {
    -(a1 - 0.5) * (a1 - 0.5) - (a2 - 0.5) * (a2 - 0.5) + 1.6 * a1 * a2
}

/// A partner policy as a distribution over the action grid, peaked at `mean`.
private func partnerPolicy(_ mean: Double, spread: Double = 0.25) -> [Double] {
    let w = (0..<maGrid).map { exp(-(actionAt($0) - mean) * (actionAt($0) - mean) / (2 * spread * spread)) }
    let total = w.reduce(0, +)
    return w.map { $0 / total }
}

/// What an independent critic converges to: the reward averaged over the partner's policy.
private func marginalCritic(_ policy: [Double]) -> [Double] {
    (0..<maGrid).map { i in (0..<maGrid).reduce(0.0) { $0 + policy[$1] * jointReward(actionAt(i), actionAt($1)) } }
}

private func maddpgFrames() -> [MaFrame] {
    // Three snapshots of a partner that is itself still learning.
    let partnerMeans = [-0.6, 0.0, 0.6]
    let marginals = partnerMeans.map { marginalCritic(partnerPolicy($0)) }
    let bestResponses = marginals.map { actionAt(argmaxFirst($0)) }

    var bestPair = Joint(a: 0, b: 0)
    for i in 0..<maGrid {
        for j in 0..<maGrid where jointReward(actionAt(i), actionAt(j)) > jointReward(actionAt(bestPair.a), actionAt(bestPair.b)) {
            bestPair = Joint(a: i, b: j)
        }
    }
    let bestA1 = actionAt(bestPair.a), bestA2 = actionAt(bestPair.b)
    let bestValue = jointReward(bestA1, bestA2)

    // A centralised critic represents the reward exactly; an independent one cannot.
    let centralMse = 0.0
    let independentMse: Double = {
        let m = marginals[1]
        var total = 0.0
        for i in 0..<maGrid {
            for j in 0..<maGrid {
                let d = m[i] - jointReward(actionAt(i), actionAt(j))
                total += d * d
            }
        }
        return total / Double(maGrid * maGrid)
    }()

    // Replay: rewards stored under an old partner no longer describe the current one.
    let oldPolicy = partnerPolicy(partnerMeans.first!)
    let newPolicy = partnerPolicy(partnerMeans.last!)
    let staleGap = (0..<maGrid).reduce(0.0) { acc, i in
        let old = (0..<maGrid).reduce(0.0) { $0 + oldPolicy[$1] * jointReward(actionAt(i), actionAt($1)) }
        let new = (0..<maGrid).reduce(0.0) { $0 + newPolicy[$1] * jointReward(actionAt(i), actionAt($1)) }
        return acc + abs(old - new)
    } / Double(maGrid)

    // Actor gradients at one point, from each kind of critic, against the truth.
    let probe = 0.2
    let h = 0.05
    let trueGrad = (jointReward(probe + h, 0.6) - jointReward(probe - h, 0.6)) / (2 * h)
    let marginalGradEarly: Double = {
        let m = marginals[0]
        let i = min(max(Int((probe + 1) / 2 * Double(maGrid - 1)), 1), maGrid - 2)
        return (m[i + 1] - m[i - 1]) / (actionAt(i + 1) - actionAt(i - 1))
    }()

    // A coarse view of the reward surface for the matrix renderer.
    let coarse = 5
    let surface = (0..<coarse).map { r in
        (0..<coarse).map { c in jointReward(-1 + 2 * Double(r) / Double(coarse - 1), -1 + 2 * Double(c) / Double(coarse - 1)) }
    }
    let surfacePeak = surface.flatMap { $0 }.map { abs($0) }.max()!
    let axisLabels = ["-1.0", "-0.5", "0.0", "0.5", "1.0"]
    let surfaceMatrix = MaMatrix(
        label: "reward over the joint action (a₁ down, a₂ across)",
        rowLabels: axisLabels,
        colLabels: axisLabels,
        cells: surface.map { row in row.map { MaCell(text: fx($0), intensity: Float($0 / surfacePeak)) } }
    )
    let curveColors = [maBaseline, maAccent, maBad]

    return [
        MaFrame(
            status: "A continuous task now: both agents pick a real number, and the reward couples them through a cross term. The best joint action is (\(fx(bestA1)), \(fx(bestA2))), worth \(fx(bestValue)).",
            matrices: [surfaceMatrix],
            readout: "optimum \(fx(bestValue)) at (\(fx(bestA1)), \(fx(bestA2)))"
        ),
        MaFrame(
            status: "An independent critic is a function of its own action alone, so what it converges to is the reward averaged over whatever the partner is currently doing. Here is that average for three different partners — and the action it recommends moves from \(fx(bestResponses[0])) to \(fx(bestResponses[2])) as the partner shifts.",
            plot: FramePlot(label: "agent 1's independent critic, by partner policy",
                            curves: marginals.indices.map { FrameCurve(label: "partner ≈ \(fx(partnerMeans[$0], 1))", values: floats(marginals[$0]), color: curveColors[$0]) },
                            yRange: -3.5...2.5, xLabel: "agent 1's action, −1 → +1"),
            readout: "best response moves \(fx(bestResponses[0])) → \(fx(bestResponses[1])) → \(fx(bestResponses[2]))"
        ),
        MaFrame(
            status: "That is the instability in one measurement: the same critic, the same environment, three different targets. Worse for anything using a replay buffer — a reward recorded against the early partner differs from what the late partner would produce by \(fx(staleGap)) on average, so stored experience is not merely old, it is wrong.",
            bars: [FrameBars(label: "recommended action by partner policy", values: floats(bestResponses), color: maBad, captions: partnerMeans.map { "partner \(fx($0, 1))" })],
            readout: "mean reward discrepancy between old and current partner: \(fx(staleGap))"
        ),
        MaFrame(
            status: "MADDPG's fix is to let each critic see every agent's action during training. Conditioned on both, the critic is fitting a fixed function of the joint action — mean squared error \(fx(centralMse, 3)) against the true reward, and it does not move when the partner's policy does. The independent critic's error against the same target is \(fx(independentMse)), and no amount of training reduces it, because the information is not in its inputs.",
            bars: [FrameBars(label: "critic error against the true joint reward", values: [Float(independentMse), Float(centralMse)], color: maAccent, captions: ["independent", "centralised"])],
            readout: "MSE \(fx(independentMse)) → \(fx(centralMse, 3))"
        ),
        MaFrame(
            status: "It also fixes the actor's gradient. At a₁ = \(fx(probe)) with the partner at 0.6, the true ∂R/∂a₁ is \(fx(trueGrad)); the centralised critic reproduces it because it is the same function, while an independent critic fitted against an early partner reports \(fx(marginalGradEarly)) — a different direction, so the actor climbs the wrong hill.",
            bars: [FrameBars(label: "∂Q/∂a₁ at a₁ = \(fx(probe))", values: [Float(trueGrad), Float(trueGrad), Float(marginalGradEarly)], color: maHighlight, captions: ["true", "centralised", "independent"])],
            readout: "true \(fx(trueGrad)) · centralised \(fx(trueGrad)) · independent \(fx(marginalGradEarly))"
        ),
        MaFrame(
            status: "None of this costs anything at run time: the critics exist only during training and are thrown away afterwards, so execution is each agent reading its own observation and acting. That is the same centralised-training, decentralised-execution bargain VDN and QMIX make — MADDPG just pays for it with extra critic inputs rather than with a constraint on what the value function may represent, which is why it handles competitive and mixed settings the factorisation methods cannot.",
            matrices: [surfaceMatrix],
            readout: "critics are training-only; actors are local"
        ),
    ]
}

// MARK: - Config

private let maLegend: [(Color, String)] = [(maBaseline, "Baseline"), (maGood, "Optimum"), (maBad, "Failure mode")]

private let multiAgentConfigs: [String: MaConfig] = [
    "iql": MaConfig(
        intro: "Two ordinary Q-learners in the same game, each treating the other as scenery. 200 runs measure how often that is enough.",
        legend: maLegend, build: iqlFrames
    ),
    "vdn": MaConfig(
        intro: "Constraining the team value to a sum of per-agent values, fitted to two payoff tables — one the constraint suits exactly, one it cannot represent at all.",
        legend: maLegend, build: vdnFrames
    ),
    "qmix": MaConfig(
        intro: "A monotonic mixing network against VDN's plain sum, measured on a game where the extra expressiveness helps and one where monotonicity itself is the obstacle.",
        legend: [(maAccent, "QMIX"), (maBaseline, "VDN"), (maBad, "Failure mode")], build: qmixFrames
    ),
    "maddpg": MaConfig(
        intro: "What a critic can know. An independent critic's target moves as its partner learns; a centralised one is fitting a fixed function — both measured against the true joint reward.",
        legend: [(maAccent, "Centralised"), (maBaseline, "Independent"), (maBad, "Drift")], build: maddpgFrames
    ),
]

// MARK: - UI

struct MultiAgentLab: View {
    private let config: MaConfig
    private let key: String
    @Environment(\.palette) private var palette

    init(topicId: String) {
        key = multiAgentConfigs[topicId] == nil ? "iql" : topicId
        config = multiAgentConfigs[key]!
    }

    var body: some View {
        let key = self.key
        AsyncFrameLab(key: "multiagent:\(key)", speedMs: 1200, build: { multiAgentConfigs[key]!.build() }) { frames, playback in
            content(frames, playback)
        }
    }

    @ViewBuilder
    private func content(_ frames: [MaFrame], _ playback: PlaybackState) -> some View {
        let frame = frames[min(playback.index, frames.count - 1)]
        LabCard {
            LabIntro(text: config.intro)
            ForEach(frame.matrices.indices, id: \.self) { PayoffMatrixView(matrix: frame.matrices[$0]).padding(.top, 12) }
            if let plot = frame.plot { FramePlotView(plot: plot, xLabelInline: false).padding(.top, 12) }
            ForEach(frame.bars.indices, id: \.self) { FrameBarsView(bar: frame.bars[$0], negativeColor: maBad).padding(.top, 12) }
            FramePlayerFooter(readout: frame.readout, status: frame.status, legend: config.legend, playback: playback, captions: frames.map(\.status))
        }
    }
}

private struct PayoffMatrixView: View {
    let matrix: MaMatrix
    @Environment(\.palette) private var palette

    var body: some View {
        let weights: [CGFloat] = [0.5] + Array(repeating: 1, count: matrix.colLabels.count)
        VStack(alignment: .leading, spacing: 0) {
            Text(matrix.label).font(.labelSmall).foregroundStyle(palette.muted)
            WeightedRow(weights: weights) {
                Color.clear.frame(height: 1)
                ForEach(matrix.colLabels.indices, id: \.self) {
                    Text(matrix.colLabels[$0]).font(.labelSmall).foregroundStyle(palette.muted).frame(maxWidth: .infinity)
                }
            }
            .padding(.top, 4)
            ForEach(matrix.cells.indices, id: \.self) { r in
                WeightedRow(weights: weights) {
                    Text(matrix.rowLabels[r]).font(AppFont.sans(11, .bold)).foregroundStyle(palette.muted)
                        .lineLimit(1).minimumScaleFactor(0.6)
                        .frame(maxWidth: .infinity, alignment: .leading)
                    ForEach(matrix.cells[r].indices, id: \.self) { c in
                        let cell = matrix.cells[r][c]
                        // Negative payoffs read as the failure colour, positive as the accent.
                        let base = cell.intensity >= 0 ? matrix.positive : maBad
                        Text(cell.text)
                            .font(AppFont.sans(11, cell.highlight ? .bold : .regular))
                            .foregroundStyle(cell.highlight ? maGood : palette.onSurface)
                            .lineLimit(1).minimumScaleFactor(0.6)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 6)
                            .background(base.opacity(Double(0.10 + 0.45 * min(abs(cell.intensity), 1))), in: RoundedRectangle(cornerRadius: 6))
                            .padding(.horizontal, 2)
                    }
                }
                .padding(.top, 3)
            }
        }
    }
}
