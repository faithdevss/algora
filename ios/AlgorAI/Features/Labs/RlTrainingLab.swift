import SwiftUI

// Port of RlTrainingSection.kt: the DQN family. Each frame's numbers come from an experiment run
// when the frames are built — tabular Q-learning on a six-state chain, Double Q-learning on the
// classic overestimation MDP, uniform vs prioritised replay. Float arithmetic throughout, as on Android.

private struct RlFrame {
    let status: String
    var plot: FramePlot?
    var bars: [FrameBars] = []
    var chain: [Float]?
    var chainLabel: String?
    var readout: String?
}

private struct RlConfig {
    let intro: String
    let legend: [(Color, String)]
    let build: () -> [RlFrame]
}

private let rlBaseline = SimColors.grey
private let rlImproved = SimColors.green
private let rlWarn = Color(hex: 0xEC4899)
private let rlAccent = SimColors.blue
private let rlHighlight = Color(hex: 0x7C3AED)

// MARK: - The chain: states 0..5, actions 0 = left, 1 = right, reward 1 on reaching state 5.

private let chainStates = 6
private let goal = chainStates - 1
private let gamma: Float = 0.95

private struct Transition { let state: Int; let action: Int; let reward: Float; let next: Int; let done: Bool }

private func stepChain(_ state: Int, _ action: Int) -> Transition {
    let next = action == 1 ? min(state + 1, goal) : max(state - 1, 0)
    let done = next == goal
    return Transition(state: state, action: action, reward: done ? 1 : 0, next: next, done: done)
}

/// Optimal action values, closed form because the chain is deterministic.
private let optimalQ: [Float] = (0..<chainStates).map { powf(gamma, Float(goal - $0 - 1)) }

private func qError(_ q: [[Float]]) -> Float {
    Float((0..<goal).map { abs(q[$0][1] - optimalQ[$0]) }.average)
}

private struct ChainResult { let error: [Float]; let q: [[Float]]; let visits: [Int] }

private func greedyRight(_ q: [[Float]], _ s: Int) -> Int { q[s][1] >= q[s][0] ? 1 : 0 }

/// One Q-learning run over the chain, optionally replaying (uniformly or by TD error).
private func runChain(seed: Int, episodes: Int = 60, alpha: Float = 0.5, epsilon: Float = 0.3,
                      replay: Bool = false, prioritised: Bool = false, replaysPerStep: Int = 4) -> ChainResult {
    var rng = Lcg(seed)
    var q = [[Float]](repeating: [0, 0], count: chainStates)
    var visits = [Int](repeating: 0, count: chainStates)
    var buffer: [Transition] = []
    var error: [Float] = []

    func target(_ t: Transition) -> Float { t.reward + (t.done ? 0 : gamma * max(q[t.next][0], q[t.next][1])) }
    func update(_ t: Transition) { q[t.state][t.action] += alpha * (target(t) - q[t.state][t.action]) }
    func tdError(_ t: Transition) -> Float { abs(target(t) - q[t.state][t.action]) }

    for _ in 0..<episodes {
        var state = 0
        var steps = 0
        while steps < 40 {
            steps += 1
            visits[state] += 1
            let action = rng.nextF() < epsilon ? rng.nextIntF(2) : greedyRight(q, state)
            let transition = stepChain(state, action)
            update(transition)
            buffer.append(transition)
            if buffer.count > 200 { buffer.removeFirst() }
            if replay {
                for _ in 0..<replaysPerStep {
                    let sample: Transition
                    if prioritised {
                        // Draw a few candidates and keep the one with the largest TD error.
                        let candidates = (0..<4).map { _ in buffer[rng.nextIntF(buffer.count)] }
                        sample = candidates[argmaxFirst(candidates.map(tdError))]
                    } else {
                        sample = buffer[rng.nextIntF(buffer.count)]
                    }
                    update(sample)
                }
            }
            state = transition.next
            if transition.done { break }
        }
        error.append(qError(q))
    }
    return ChainResult(error: error, q: q, visits: visits)
}

private func episodesToConverge(_ error: [Float], threshold: Float = 0.05) -> Int {
    (error.firstIndex { $0 < threshold }).map { $0 + 1 } ?? error.count
}

private func f3(_ x: Float) -> String { fx(x, 3) }

private let errorLabel = "mean |Q − Q*|"
private let stateCaps = (0..<chainStates).map { "s\($0)" }
private let goalCaps = (0..<goal).map { "s\($0)" }

// MARK: - Experience replay

private func replayFrames() -> [RlFrame] {
    let online = runChain(seed: 5)
    let withReplay = runChain(seed: 5, replay: true)
    return [
        RlFrame(status: "A six-state chain: reward 1 only at the far end, nothing anywhere else. Value has to travel back six states from a single lucky arrival, so how transitions are reused is the whole story.",
                chain: optimalQ, chainLabel: "optimal Q(s, right) — what the agent has to learn"),
        RlFrame(status: "Online learning uses each transition once, in the order it happened. Consecutive updates are strongly correlated — the agent sees the same short stretch of the chain over and over within an episode.",
                plot: FramePlot(label: errorLabel, curves: [FrameCurve(label: "online", values: online.error, color: rlBaseline)], yRange: 0...0.6, xLabel: "episode"),
                readout: "error after \(online.error.count) episodes: \(f3(online.error.last!))"),
        RlFrame(status: "A replay buffer stores transitions and re-samples them at random. Each experience is used several times, and consecutive updates no longer come from the same part of the chain.",
                plot: FramePlot(label: errorLabel, curves: [
                    FrameCurve(label: "online", values: online.error, color: rlBaseline),
                    FrameCurve(label: "replay ×4", values: withReplay.error, color: rlImproved),
                ], yRange: 0...0.6, xLabel: "episode"),
                readout: "final error \(f3(online.error.last!)) → \(f3(withReplay.error.last!))"),
        RlFrame(status: "Below 0.05 mean error takes \(episodesToConverge(online.error)) episodes online and \(episodesToConverge(withReplay.error)) with replay. The environment interactions are identical — only the number of gradient steps per interaction changed, which is why replay is described as sample efficiency.",
                bars: [FrameBars(label: "episodes to mean error < 0.05", values: [Float(episodesToConverge(online.error)), Float(episodesToConverge(withReplay.error))], color: rlAccent, captions: ["online", "replay"])],
                readout: "same experience, 4× the updates"),
    ]
}

// MARK: - Target networks

/// Q-learning where the bootstrap value comes from a copy refreshed every `lag` updates.
private func runChainWithTarget(seed: Int, lag: Int, episodes: Int = 60, alpha: Float = 0.5, epsilon: Float = 0.3) -> [Float] {
    var rng = Lcg(seed)
    var q = [[Float]](repeating: [0, 0], count: chainStates)
    var frozen = q
    var error: [Float] = []
    var updates = 0
    for _ in 0..<episodes {
        var state = 0
        var steps = 0
        while steps < 40 {
            steps += 1
            let action = rng.nextF() < epsilon ? rng.nextIntF(2) : greedyRight(q, state)
            let t = stepChain(state, action)
            let target = t.reward + (t.done ? 0 : gamma * max(frozen[t.next][0], frozen[t.next][1]))
            q[t.state][t.action] += alpha * (target - q[t.state][t.action])
            updates += 1
            if updates % max(lag, 1) == 0 { frozen = q }
            state = t.next
            if t.done { break }
        }
        error.append(qError(q))
    }
    return error
}

private func targetNetworkFrames() -> [RlFrame] {
    let noTarget = runChainWithTarget(seed: 7, lag: 1)
    let lag20 = runChainWithTarget(seed: 7, lag: 20)
    let lag50 = runChainWithTarget(seed: 7, lag: 50)
    let (e0, e20, e50) = (episodesToConverge(noTarget), episodesToConverge(lag20), episodesToConverge(lag50))

    // One shared weight covers the whole chain, so one update moves every state's value.
    let features = (0..<chainStates).map { Float($0 + 1) / 5 }
    var w: Float = 0
    let before = features.map { w * $0 }
    w += 0.5 * (1 - w * features[4]) * features[4]
    let after = features.map { w * $0 }
    let shifts = features.indices.map { after[$0] - before[$0] }

    return [
        RlFrame(status: "Bootstrapping means an update's target is computed from the same estimates being updated: Q(s,a) ← r + γ·max Q(s′,·). Nothing else in supervised learning works this way — the labels move.",
                chain: optimalQ, chainLabel: "Q*(s, right) on the chain"),
        RlFrame(status: "First, the uncomfortable measurement: on this tabular chain a target network is pure cost. Refreshing every update converges in \(e0) episodes, freezing for 20 takes \(e20), and freezing for 50 takes \(e50). Stale targets are slower targets.",
                plot: FramePlot(label: errorLabel, curves: [
                    FrameCurve(label: "no target net", values: noTarget, color: rlImproved),
                    FrameCurve(label: "refresh every 20", values: lag20, color: rlAccent),
                    FrameCurve(label: "refresh every 50", values: lag50, color: rlWarn),
                ], yRange: 0...0.6, xLabel: "episode"),
                readout: "\(e0) → \(e20) → \(e50) episodes"),
        RlFrame(status: "So why does DQN need one? Because a table updates one cell while a network updates a shared function. Give this chain a single shared weight and one update at s4 moves the predicted value of every state at once — including the state whose value that update was aiming at.",
                bars: [FrameBars(label: "change in Q(s) after one update at s4", values: shifts, color: rlWarn, captions: stateCaps)],
                readout: "one update, \(shifts.filter { abs($0) > 1e-4 }.count) state values moved"),
        RlFrame(status: "That is the feedback loop a target network cuts: freeze a copy of the weights, compute every target from the copy, and each batch becomes ordinary regression against fixed labels. It buys nothing on a table, and it is what keeps a deep Q-network from chasing its own tail on Atari.",
                bars: [FrameBars(label: "episodes to converge, tabular chain", values: [Float(e0), Float(e20), Float(e50)], color: rlAccent, captions: ["no target", "lag 20", "lag 50"])],
                readout: "an empirical stabiliser, not a convergence guarantee"),
    ]
}

// MARK: - Double DQN

private struct BiasResult { let single: [Float]; let double: [Float]; let leftShare: (Float, Float) }

/// The overestimation MDP: start offers a certain 0 (right) or a move to B (left), whose eight
/// actions pay mean-zero random rewards. The true value of left is 0; max over noise is not.
private func overestimationRun() -> BiasResult {
    let actionsAtB = 8
    let runs = 40
    let episodes = 60

    func run(_ double: Bool, _ seed: Int) -> ([Float], Float) {
        var rng = Lcg(seed)
        var qA: [Float] = [0, 0]
        var qB1 = [Float](repeating: 0, count: actionsAtB)
        var qB2 = [Float](repeating: 0, count: actionsAtB)
        let alpha: Float = 0.1
        var history: [Float] = []
        var leftCount = 0
        for _ in 0..<episodes {
            let goLeft = rng.nextF() < 0.1 ? rng.nextF() < 0.5 : qA[0] > qA[1]
            if goLeft {
                leftCount += 1
                let action = rng.nextIntF(actionsAtB)
                let reward = (rng.nextF() - 0.5) * 2
                if double {
                    if rng.nextF() < 0.5 { qB1[action] += alpha * (reward - qB1[action]) } else { qB2[action] += alpha * (reward - qB2[action]) }
                } else {
                    qB1[action] += alpha * (reward - qB1[action])
                }
                let bootstrap = double ? qB2[argmaxFirst(qB1)] : qB1.max()!
                qA[0] += alpha * (bootstrap - qA[0])
            } else {
                qA[1] += alpha * (0 - qA[1])
            }
            history.append(qA[0])
        }
        return (history, Float(leftCount) / Float(episodes))
    }

    let singleRuns = (0..<runs).map { run(false, 17 + $0 * 7) }
    let doubleRuns = (0..<runs).map { run(true, 17 + $0 * 7) }
    func mean(_ list: [([Float], Float)]) -> [Float] { (0..<episodes).map { e in Float(list.map { $0.0[e] }.average) } }
    return BiasResult(single: mean(singleRuns), double: mean(doubleRuns),
                      leftShare: (Float(singleRuns.map(\.1).average), Float(doubleRuns.map(\.1).average)))
}

private func doubleDqnFrames() -> [RlFrame] {
    let result = overestimationRun()
    let peakSingle = result.single.max()!, peakDouble = result.double.max()!
    let label = "Q(start, left) — true value is 0"
    return [
        RlFrame(status: "The classic setup: from the start state, going right ends the episode with 0. Going left reaches a state whose eight actions all pay mean-zero random rewards — so the true value of going left is exactly 0.",
                chain: [0, 0], chainLabel: "true Q(start, ·) — both actions are worth 0"),
        RlFrame(status: "Plain Q-learning bootstraps with max over its own noisy estimates. The maximum of several noisy zeros is positive, so the start state's left action is valued above its true 0 — averaged over 40 runs it peaks at \(f3(peakSingle)).",
                plot: FramePlot(label: label, curves: [FrameCurve(label: "Q-learning", values: result.single, color: rlWarn)], yRange: -0.1...0.35, xLabel: "episode"),
                readout: "peak overestimate \(f3(peakSingle))"),
        RlFrame(status: "Double Q-learning keeps two estimates: one picks the action, the other scores it. The noise that made an action look best is no longer the noise used to value it, so the bias largely cancels.",
                plot: FramePlot(label: label, curves: [
                    FrameCurve(label: "Q-learning", values: result.single, color: rlWarn),
                    FrameCurve(label: "Double Q-learning", values: result.double, color: rlImproved),
                ], yRange: -0.1...0.35, xLabel: "episode"),
                readout: "peak \(f3(peakSingle)) → \(f3(peakDouble))"),
        RlFrame(status: "The consequence is behavioural, not cosmetic: the inflated value makes the agent take the pointless left action \(fx(result.leftShare.0 * 100, 0))% of the time, against \(fx(result.leftShare.1 * 100, 0))% for Double Q-learning. In DQN the same trick reuses the target network as the second estimator, so it costs nothing.",
                bars: [FrameBars(label: "share of episodes taking the worthless action", values: [result.leftShare.0, result.leftShare.1], color: rlAccent, captions: ["Q-learning", "Double"])]),
    ]
}

// MARK: - Dueling DQN

private func duelingFrames() -> [RlFrame] {
    let q = runChain(seed: 11, replay: true).q
    let stateValue = (0..<goal).map { max(q[$0][0], q[$0][1]) }
    let advantageRight = (0..<goal).map { q[$0][1] - (q[$0][0] + q[$0][1]) / 2 }
    let advantageLeft = (0..<goal).map { q[$0][0] - (q[$0][0] + q[$0][1]) / 2 }
    return [
        RlFrame(status: "Take the Q-table a trained agent ends up with on the chain. Q(s, a) mixes two different questions: how good is this state at all, and how much does the choice of action matter here.",
                bars: [
                    FrameBars(label: "Q(s, left)", values: (0..<goal).map { q[$0][0] }, color: rlBaseline, captions: goalCaps),
                    FrameBars(label: "Q(s, right)", values: (0..<goal).map { q[$0][1] }, color: rlAccent, captions: goalCaps),
                ]),
        RlFrame(status: "Split it: V(s) is the state's own worth, running \(fx(stateValue.min()!)) to \(fx(stateValue.max()!)) across the chain. That is the bulk of every Q value — mostly an answer to \"how close am I to the goal\", repeated inside both actions.",
                bars: [FrameBars(label: "V(s)", values: stateValue, color: rlHighlight, captions: goalCaps)]),
        RlFrame(status: "What is left is the advantage A(s, a): at most \(f3(advantageRight.map { abs($0) }.max()!)) in magnitude here, an order of magnitude below V, and yet it is the only part that decides the action. A dueling head estimates V once per state instead of relearning it inside every action's value.",
                bars: [
                    FrameBars(label: "A(s, left)", values: advantageLeft, color: rlBaseline, captions: goalCaps),
                    FrameBars(label: "A(s, right)", values: advantageRight, color: rlImproved, captions: goalCaps),
                ]),
        RlFrame(status: "One catch: V + A is not identifiable — add 5 to V, subtract 5 from every A, and Q is unchanged. Dueling networks pin it down by forcing the advantages to have zero mean, which is exactly how the bars above were computed.",
                bars: [FrameBars(label: "A(s, left) + A(s, right)", values: (0..<goal).map { advantageLeft[$0] + advantageRight[$0] }, color: rlHighlight, captions: goalCaps)],
                readout: "advantages sum to zero at every state, by construction"),
    ]
}

// MARK: - Prioritized replay

private func prioritizedFrames() -> [RlFrame] {
    let uniform = runChain(seed: 3, replay: true)
    let prioritised = runChain(seed: 3, replay: true, prioritised: true)
    let (eu, ep) = (episodesToConverge(uniform.error), episodesToConverge(prioritised.error))
    return [
        RlFrame(status: "On the chain, almost every stored transition has a TD error of zero — the reward is 0 and both value estimates already agree. Sampling those teaches nothing.",
                chain: optimalQ, chainLabel: "optimal Q(s, right): only the last transition carries reward at first"),
        RlFrame(status: "Uniform replay spends most of its updates on those zero-error transitions. The information has to diffuse backwards one state per lucky sample.",
                plot: FramePlot(label: errorLabel, curves: [FrameCurve(label: "uniform replay", values: uniform.error, color: rlBaseline)], yRange: 0...0.6, xLabel: "episode"),
                readout: "final error \(f3(uniform.error.last!))"),
        RlFrame(status: "Prioritized replay samples in proportion to |TD error|, so the frontier where value is actually changing gets replayed and the settled part of the chain does not.",
                plot: FramePlot(label: errorLabel, curves: [
                    FrameCurve(label: "uniform replay", values: uniform.error, color: rlBaseline),
                    FrameCurve(label: "prioritized", values: prioritised.error, color: rlImproved),
                ], yRange: 0...0.6, xLabel: "episode"),
                readout: "\(eu) → \(ep) episodes to error < 0.05"),
        RlFrame(status: "The catch is that sampling by error is no longer sampling the distribution the agent actually experiences, so real implementations correct it with importance-sampling weights — a bias/variance trade made deliberately.",
                bars: [FrameBars(label: "episodes to mean error < 0.05", values: [Float(eu), Float(ep)], color: rlAccent, captions: ["uniform", "prioritized"])]),
    ]
}

// MARK: - Noisy nets

private struct ExploreResult { let reached: Int; let meanSteps: Float; let visits: [Int]; let error: [Float] }

/// ε-greedy re-rolls at every step; parameter noise draws one perturbation per episode.
private func exploreChain(seed: Int, perEpisodeNoise: Bool, episodes: Int = 60) -> ExploreResult {
    var rng = Lcg(seed)
    var q = [[Float]](repeating: [0, 0], count: chainStates)
    var visits = [Int](repeating: 0, count: chainStates)
    var error: [Float] = []
    var reached = 0
    var totalSteps = 0
    for _ in 0..<episodes {
        let bias: Float = perEpisodeNoise ? (rng.nextF() - 0.5) * 0.6 : 0
        var state = 0
        var steps = 0
        while steps < 40 {
            steps += 1
            visits[state] += 1
            let action: Int
            if perEpisodeNoise {
                action = q[state][1] + bias >= q[state][0] ? 1 : 0
            } else {
                action = rng.nextF() < 0.3 ? rng.nextIntF(2) : greedyRight(q, state)
            }
            let t = stepChain(state, action)
            let target = t.reward + (t.done ? 0 : gamma * max(q[t.next][0], q[t.next][1]))
            q[t.state][t.action] += 0.5 * (target - q[t.state][t.action])
            state = t.next
            if t.done {
                reached += 1
                totalSteps += steps
                break
            }
        }
        error.append(qError(q))
    }
    return ExploreResult(reached: reached, meanSteps: reached == 0 ? 0 : Float(totalSteps) / Float(reached), visits: visits, error: error)
}

private func noisyNetFrames() -> [RlFrame] {
    let dithering = exploreChain(seed: 9, perEpisodeNoise: false)
    let noisy = exploreChain(seed: 9, perEpisodeNoise: true)
    let dVisits = dithering.visits.map { Float($0) }
    return [
        RlFrame(status: "ε-greedy re-rolls the dice at every single step. It reaches the goal in all \(dithering.reached) of 60 episodes, but takes \(fx(dithering.meanSteps, 1)) steps to do it — the minimum is 5, and the rest is the agent undoing its own random moves.",
                bars: [FrameBars(label: "state visits, ε-greedy", values: dVisits, color: rlBaseline, captions: stateCaps)],
                readout: "\(dithering.reached)/60 episodes reached the goal in \(fx(dithering.meanSteps, 1)) steps"),
        RlFrame(status: "Noisy nets put the randomness in the weights and draw it once per episode, so the agent commits to one perturbed policy and follows it. When the perturbation points the right way it walks straight to the goal: \(fx(noisy.meanSteps, 1)) steps, against \(fx(dithering.meanSteps, 1)).",
                bars: [
                    FrameBars(label: "ε-greedy", values: dVisits, color: rlBaseline, captions: stateCaps),
                    FrameBars(label: "parameter noise", values: noisy.visits.map { Float($0) }, color: rlImproved, captions: stateCaps),
                ],
                readout: "mean steps to goal \(fx(dithering.meanSteps, 1)) → \(fx(noisy.meanSteps, 1))"),
        RlFrame(status: "Commitment cuts both ways, and the visit counts show it: when the draw points the wrong way the agent spends the entire episode stuck at the start, so it only finishes \(noisy.reached) of 60 episodes against ε-greedy's \(dithering.reached). Consistent exploration is a bet, not a free win.",
                bars: [FrameBars(label: "episodes that reached the goal", values: [Float(dithering.reached), Float(noisy.reached)], color: rlAccent, captions: ["ε-greedy", "parameter noise"])],
                readout: "\(dithering.visits[0]) vs \(noisy.visits[0]) visits to the start state"),
        RlFrame(status: "The real argument for noisy nets is that the noise scale is a learned parameter: where action values are already clear the network can shrink its own noise, and where they are not it stays exploratory — with no ε schedule to hand-tune per environment.",
                plot: FramePlot(label: errorLabel, curves: [
                    FrameCurve(label: "ε-greedy", values: dithering.error, color: rlBaseline),
                    FrameCurve(label: "parameter noise", values: noisy.error, color: rlImproved),
                ], yRange: 0...0.6, xLabel: "episode"),
                readout: "final error \(f3(dithering.error.last!)) vs \(f3(noisy.error.last!))"),
    ]
}

// MARK: - C51

private func distributionalFrames() -> [RlFrame] {
    let atoms = 9
    let support = (0..<atoms).map { -1 + 2 * Float($0) / Float(atoms - 1) }
    // A risky action paying ±1, and a safe one that always pays a small positive amount.
    let risky: [Float] = [0.5, 0, 0, 0, 0, 0, 0, 0, 0.5]
    let safeAtom = support.indices.min { abs(support[$0] - 0.25) < abs(support[$1] - 0.25) }!
    let safe = (0..<atoms).map { $0 == safeAtom ? Float(1) : 0 }
    func expectation(_ p: [Float]) -> Float { Float(p.indices.reduce(0.0) { $0 + Double(p[$1] * support[$1]) }) }
    let supportCaps = support.map { fx($0, 1) }
    let distBars = [
        FrameBars(label: "P(return) — risky", values: risky, color: rlWarn, captions: supportCaps),
        FrameBars(label: "P(return) — safe", values: safe, color: rlImproved, captions: supportCaps),
    ]
    return [
        RlFrame(status: "Standard Q-learning stores one number per action: the expected return. Here the risky action's expectation is \(fx(expectation(risky))) and the safe one's is \(fx(expectation(safe))) — on that basis the safe action simply wins.",
                bars: [FrameBars(label: "expected return", values: [expectation(risky), expectation(safe)], color: rlAccent, captions: ["risky", "safe"])]),
        RlFrame(status: "But those expectations hide completely different worlds. C51 keeps a probability over a fixed grid of \(atoms) return values instead of collapsing to the mean.",
                bars: distBars, readout: "same axis, \(atoms) atoms from −1 to +1"),
        RlFrame(status: "The risky action is a coin flip between the best and worst outcomes on the board; the safe one is a near-certainty. Any risk-sensitive policy needs that distinction, and the mean cannot express it.",
                bars: distBars, readout: "risky: 50% at −1, 50% at +1"),
        RlFrame(status: "The learning update projects the shifted, discounted target distribution back onto the fixed atom grid — that projection is the whole algorithm. Even agents that only ever act on the mean train faster this way: predicting a distribution is a richer signal than predicting one number.",
                bars: [FrameBars(label: "target support after γ = 0.95 discount", values: support.map { gamma * $0 }, color: rlHighlight, captions: support.map { fx(gamma * $0, 1) })]),
    ]
}

// MARK: - DQN

private func dqnFrames() -> [RlFrame] {
    let plain = runChain(seed: 21)
    let withReplay = runChain(seed: 21, replay: true)
    return [
        RlFrame(status: "DQN is Q-learning with a function approximator in place of the table — plus the two fixes that make that combination stable at all: a replay buffer and a target network.",
                chain: optimalQ, chainLabel: "Q*(s, right) on the chain"),
        RlFrame(status: "Without them, the combination of bootstrapping, off-policy updates and function approximation is the \"deadly triad\": correlated consecutive samples and a target that moves with every update.",
                plot: FramePlot(label: errorLabel, curves: [FrameCurve(label: "online Q-learning", values: plain.error, color: rlBaseline)], yRange: 0...0.6, xLabel: "episode"),
                readout: "final error \(f3(plain.error.last!))"),
        RlFrame(status: "Replay decorrelates the updates and reuses each transition; the target network makes each batch an ordinary regression against a fixed label. Same environment, same interactions, \(episodesToConverge(plain.error)) → \(episodesToConverge(withReplay.error)) episodes to converge.",
                plot: FramePlot(label: errorLabel, curves: [
                    FrameCurve(label: "online", values: plain.error, color: rlBaseline),
                    FrameCurve(label: "replay + target", values: withReplay.error, color: rlImproved),
                ], yRange: 0...0.6, xLabel: "episode"),
                readout: "final error \(f3(plain.error.last!)) → \(f3(withReplay.error.last!))"),
        RlFrame(status: "The learned greedy policy: right at every state, values rising towards the goal. On Atari the same loop runs on raw pixels — the algorithm is unchanged, the approximator is a conv net.",
                bars: [
                    FrameBars(label: "learned Q(s, right)", values: (0..<goal).map { withReplay.q[$0][1] }, color: rlImproved, captions: goalCaps),
                    FrameBars(label: "optimal Q(s, right)", values: (0..<goal).map { optimalQ[$0] }, color: rlBaseline, captions: goalCaps),
                ]),
    ]
}

// MARK: - Rainbow

private func rainbowFrames() -> [RlFrame] {
    let base = runChain(seed: 31)
    let replay = runChain(seed: 31, replay: true)
    let prioritised = runChain(seed: 31, replay: true, prioritised: true)
    let bias = overestimationRun()
    let (eb, er, ep) = (episodesToConverge(base.error), episodesToConverge(replay.error), episodesToConverge(prioritised.error))
    let peakSingle = bias.single.max()!, peakDouble = bias.double.max()!
    return [
        RlFrame(status: "Rainbow is not a new algorithm — it is DQN with six independent improvements switched on at once, each fixing a different failure.",
                bars: [FrameBars(label: "episodes to converge on the chain", values: [Float(eb), Float(er), Float(ep)], color: rlAccent, captions: ["plain", "+replay", "+priority"])]),
        RlFrame(status: "Each component targets a distinct problem: Double DQN the overestimation bias (\(f3(peakSingle)) → \(f3(peakDouble)) peak here), prioritized replay the wasted updates, dueling the redundant relearning of state value, noisy nets the exploration schedule, and distributional heads the information thrown away by averaging.",
                bars: [FrameBars(label: "peak overestimate", values: [peakSingle, peakDouble], color: rlWarn, captions: ["DQN", "Double"])]),
        RlFrame(status: "They compose because they touch different parts of the agent — the target computation, the sampling distribution, the network head, the exploration rule. Rainbow's ablation study is the evidence: removing prioritized replay or multi-step returns hurts most, removing dueling barely registers.",
                plot: FramePlot(label: "mean |Q − Q*| on the chain", curves: [
                    FrameCurve(label: "plain", values: base.error, color: rlBaseline),
                    FrameCurve(label: "+ replay", values: replay.error, color: rlAccent),
                    FrameCurve(label: "+ prioritized", values: prioritised.error, color: rlImproved),
                ], yRange: 0...0.6, xLabel: "episode"),
                readout: "\(eb) → \(er) → \(ep) episodes"),
        RlFrame(status: "The lesson generalises past DQN: most large jumps in RL benchmark scores have come from stacking small, well-understood fixes rather than from one new idea.",
                bars: [FrameBars(label: "components", values: [1, 1, 1, 1, 1, 1], color: rlHighlight, captions: ["double", "prio", "duel", "multi-step", "distrib", "noisy"])]),
    ]
}

// MARK: - Config

private let comparisonLegend: [(Color, String)] = [(rlBaseline, "Baseline"), (rlImproved, "Improved"), (rlWarn, "Failure mode")]

private let rlConfigs: [String: RlConfig] = [
    "dqn": RlConfig(intro: "Q-learning on a six-state chain, run for real — with and without the two stabilisers that turn it into DQN.",
                    legend: comparisonLegend, build: dqnFrames),
    "experience_replay": RlConfig(intro: "The same interactions, replayed or not. The gap in the curves is sample efficiency, measured rather than asserted.",
                                  legend: comparisonLegend, build: replayFrames),
    "target_networks": RlConfig(intro: "The moving-target problem in its smallest form: one bootstrapped value chasing itself, with and without a frozen copy.",
                                legend: comparisonLegend, build: targetNetworkFrames),
    "double_dqn": RlConfig(intro: "The classic overestimation MDP, averaged over 40 runs. Both actions are truly worth 0 — watch what the max operator does to one of them.",
                           legend: comparisonLegend, build: doubleDqnFrames),
    "dueling_dqn": RlConfig(intro: "A real learned Q-table split into V and A, including why the split needs the zero-mean constraint to be well defined.",
                            legend: [(rlHighlight, "V(s)"), (rlImproved, "A(s, right)"), (rlBaseline, "A(s, left)")], build: duelingFrames),
    "prioritized_replay": RlConfig(intro: "Uniform sampling against TD-error sampling on a sparse-reward chain, where most stored transitions carry no information at all.",
                                   legend: comparisonLegend, build: prioritizedFrames),
    "noisy_nets": RlConfig(intro: "Per-step dithering against a perturbation held for a whole episode — the difference shows up in how deep into the chain the agent ever gets.",
                           legend: comparisonLegend, build: noisyNetFrames),
    "c51": RlConfig(intro: "Two actions with nearly the same expected return and completely different return distributions. The mean cannot tell them apart; a distribution can.",
                    legend: [(rlWarn, "Risky"), (rlImproved, "Safe"), (rlHighlight, "Discounted support")], build: distributionalFrames),
    "rainbow_dqn": RlConfig(intro: "The components measured one at a time on the same toy problem, then the argument for why they compose.",
                            legend: comparisonLegend, build: rainbowFrames),
]

// MARK: - UI

struct RlTrainingLab: View {
    private let config: RlConfig
    private let key: String
    @Environment(\.palette) private var palette

    init(topicId: String) {
        key = rlConfigs[topicId] == nil ? "dqn" : topicId
        config = rlConfigs[key]!
    }

    var body: some View {
        let key = self.key
        AsyncFrameLab(key: "rltraining:\(key)", speedMs: 1100, build: { rlConfigs[key]!.build() }) { frames, playback in
            content(frames, playback)
        }
    }

    @ViewBuilder
    private func content(_ frames: [RlFrame], _ playback: PlaybackState) -> some View {
        let frame = frames[min(playback.index, frames.count - 1)]
        LabCard {
            LabIntro(text: config.intro)
            if let chain = frame.chain { ChainStrip(values: chain, label: frame.chainLabel).padding(.top, 12) }
            if let plot = frame.plot { FramePlotView(plot: plot).padding(.top, 12) }
            ForEach(frame.bars.indices, id: \.self) { FrameBarsView(bar: frame.bars[$0], negativeColor: rlWarn).padding(.top, 12) }
            FramePlayerFooter(readout: frame.readout, status: frame.status, legend: config.legend, playback: playback, captions: frames.map(\.status))
        }
    }
}

private struct ChainStrip: View {
    let values: [Float]
    let label: String?
    @Environment(\.palette) private var palette

    var body: some View {
        let peak = max(values.max() ?? 1, 0.001)
        VStack(alignment: .leading, spacing: 0) {
            if let label { Text(label).font(.labelSmall).foregroundStyle(palette.muted) }
            HStack(spacing: 4) {
                ForEach(values.indices, id: \.self) { i in
                    VStack(spacing: 0) {
                        Text("s\(i)").font(AppFont.sans(11, .bold))
                        Text(fx(values[i])).font(.labelSmall)
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 8)
                    .background(rlAccent.opacity(Double(0.15 + 0.7 * min(max(values[i] / peak, 0), 1))), in: RoundedRectangle(cornerRadius: 8))
                }
            }
            .padding(.top, 4)
        }
    }
}
