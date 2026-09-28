import SwiftUI

// Port of RlGridWorldSection.kt: the real algorithm (value iteration, policy evaluation, tabular
// Q-learning, SARSA, a Bayes filter) run over a 4×4 grid with fixed seeds, snapshotting the value
// table and greedy policy per sweep or episode. Cell shading is the state value; the glyph is the
// greedy action.

private struct RlWorld {
    let rows: Int
    let cols: Int
    let start: Int
    let goal: Int
    let pits: Set<Int>
    let walls: Set<Int>
    let stepCost: Float
    let goalReward: Float
    let pitReward: Float

    var states: Int { rows * cols }
    /// up, down, left, right — index order is also the arrow-glyph order.
    var actions: [Int] { [-cols, cols, -1, 1] }

    func isTerminal(_ s: Int) -> Bool { s == goal || pits.contains(s) }

    func step(_ state: Int, _ action: Int) -> Int {
        let row = state / cols
        let col = state % cols
        let next = state + action
        if next < 0 || next >= states { return state }
        if abs(action) == 1 && next / cols != row { return state }
        if abs(action) == 1 && abs(next % cols - col) != 1 { return state }
        if walls.contains(next) { return state }
        return next
    }

    func rewardFor(_ state: Int) -> Float {
        if state == goal { return goalReward }
        if pits.contains(state) { return pitReward }
        return stepCost
    }

    /// Terminals are absorbing: reward on the way in, nothing flows back out.
    func backup(_ state: Int, _ action: Int, _ values: [Float], _ gamma: Float) -> Float {
        let next = step(state, action)
        return rewardFor(next) + (isTerminal(next) ? 0 : gamma * values[next])
    }

    func greedyPolicy(_ values: [Float], _ gamma: Float) -> [Int] {
        (0..<states).map { s in
            isTerminal(s) || walls.contains(s) ? -1 : argmaxFirst(actions.map { backup(s, $0, values, gamma) })
        }
    }
}

private let defaultWorld = RlWorld(rows: 4, cols: 4, start: 12, goal: 3, pits: [7], walls: [5], stepCost: -0.04, goalReward: 1, pitReward: -1)

// Sutton & Barto's cliff walk, compressed to 4×4.
private let cliffWorld = RlWorld(rows: 4, cols: 4, start: 12, goal: 15, pits: [13, 14], walls: [], stepCost: -1, goalReward: 0, pitReward: -100)

private let rlCols = 4
private let rlStates = defaultWorld.states
private let rlGoal = defaultWorld.goal
private let rlPit = 7
private let rlWall = 5
private let rlStart = defaultWorld.start
private let actions = defaultWorld.actions
private let arrows = ["↑", "↓", "←", "→"]

private struct GridFrame {
    let values: [Float]
    let policy: [Int]
    let agent: Int?
    let visited: Set<Int>
    let status: String
    var showPolicy = true
    /// Q-function view: four action values per cell instead of one number.
    var qTable: [[Float]]?
    /// POMDP view: states the agent cannot tell apart, rendered as "?".
    var fog: Set<Int> = []
    var world = defaultWorld
}

private struct GridConfig { let intro: String; let valueLabel: String; let build: () -> [GridFrame] }

private let goalColor = SimColors.green
private let pitColor = SimColors.red
private let wallColor = SimColors.wall
private let agentColor = SimColors.active
private let positiveValue = SimColors.blue

private func isTerminal(_ s: Int) -> Bool { defaultWorld.isTerminal(s) }
private func step(_ state: Int, _ action: Int) -> Int { defaultWorld.step(state, action) }
private func rewardFor(_ state: Int) -> Float { defaultWorld.rewardFor(state) }
private func backup(_ state: Int, _ action: Int, _ values: [Float], _ gamma: Float) -> Float { defaultWorld.backup(state, action, values, gamma) }
private func greedyPolicy(_ values: [Float], _ gamma: Float) -> [Int] { defaultWorld.greedyPolicy(values, gamma) }

private func f2(_ x: Float) -> String { fx(x, 2) }
private let noPolicy = [Int](repeating: -1, count: rlStates)

private func terminalValues() -> [Float] {
    var v = [Float](repeating: 0, count: rlStates)
    v[rlGoal] = 1
    v[rlPit] = -1
    return v
}

private func bestBackup(_ s: Int, _ values: [Float], _ gamma: Float) -> Float { actions.map { backup(s, $0, values, gamma) }.max()! }

/// Java's HashMap iteration order for small-Int-pair keys (`Pair.hashCode = 31·first + second`):
/// bucket index under the current capacity, insertion order within a bucket. Dyna-Q's
/// `model.keys.random(random)` indexes into that order, so reproducing it keeps the frames identical.
private func javaHashOrder(_ keys: [(Int, Int)]) -> [(Int, Int)] {
    var capacity = 16
    while Double(keys.count) > Double(capacity) * 0.75 { capacity *= 2 }
    return keys.enumerated().sorted { a, b in
        let ha = (31 * a.element.0 + a.element.1) & (capacity - 1)
        let hb = (31 * b.element.0 + b.element.1) & (capacity - 1)
        return ha == hb ? a.offset < b.offset : ha < hb
    }.map(\.element)
}

// MARK: - MDP: value iteration, one frame per sweep

private func valueIterationFrames() -> [GridFrame] {
    let gamma: Float = 0.9
    var values = terminalValues()
    var frames = [GridFrame(values: values, policy: noPolicy, agent: nil, visited: [],
                            status: "The MDP: states, four actions, reward +1 at the goal and −1 at the pit, −0.04 per step. Every value starts at 0 except the terminals.",
                            showPolicy: false)]
    for sweep in 0..<8 {
        var next = values
        var delta: Float = 0
        for s in 0..<rlStates where !isTerminal(s) && s != rlWall {
            let best = bestBackup(s, values, gamma)
            delta = max(delta, abs(best - values[s]))
            next[s] = best
        }
        values = next
        frames.append(GridFrame(values: values, policy: greedyPolicy(values, gamma), agent: nil, visited: [],
                                status: "Sweep \(sweep + 1): every state takes the best of (reward + γ · value of where that action lands). Largest change this sweep: \(fx(delta, 3))."))
    }
    frames.append(GridFrame(values: values, policy: greedyPolicy(values, gamma), agent: nil, visited: [],
                            status: "Converged. Value spreads outward from the goal, and the greedy policy — take the action into the highest-valued neighbour — is now optimal everywhere."))
    return frames
}

// MARK: - Q-learning / Dyna-Q: real episodes, ε-greedy

private func qValues(_ q: [[Float]]) -> [Float] { (0..<rlStates).map { isTerminal($0) ? rewardFor($0) : q[$0].max()! } }

private func qPolicy(_ q: [[Float]]) -> [Int] {
    (0..<rlStates).map { s in
        if isTerminal(s) || s == rlWall || q[s].allSatisfy({ $0 == 0 }) { return -1 }
        return argmaxFirst(q[s])
    }
}

private func qLearningFrames(dynaPlanningSteps: Int = 0) -> [GridFrame] {
    let gamma: Float = 0.9
    let alpha: Float = 0.5
    var random = KotlinRandom(seed: 7)
    var q = [[Float]](repeating: [0, 0, 0, 0], count: rlStates)
    var model: [Int: (Int, Float)] = [:]
    var modelKeys: [(Int, Int)] = []
    let label = dynaPlanningSteps > 0 ? "Dyna-Q" : "Q-learning"

    var frames = [GridFrame(values: qValues(q), policy: qPolicy(q), agent: rlStart, visited: [],
                            status: dynaPlanningSteps > 0
                                ? "Dyna-Q: every real step also updates a learned model of the world, then replays \(dynaPlanningSteps) imagined transitions from it. Same experience, far more learning per step."
                                : "Tabular Q-learning knows nothing at the start — no model of the world, no values. It only has the reward it stumbles into.",
                            showPolicy: false)]
    let episodes = 40
    for episode in 0..<episodes {
        var state = rlStart
        var visited: Set<Int> = [state]
        var steps = 0
        let epsilon = max(1 - Float(episode) / Float(episodes), 0.1)
        while !isTerminal(state) && steps < 60 {
            let action = random.nextFloat() < epsilon ? random.nextInt(actions.count) : argmaxFirst(q[state])
            let next = step(state, actions[action])
            let reward = rewardFor(next)
            let target = reward + (isTerminal(next) ? 0 : gamma * q[next].max()!)
            q[state][action] += alpha * (target - q[state][action])

            if dynaPlanningSteps > 0 {
                let key = state * 4 + action
                if model[key] == nil { modelKeys.append((state, action)) }
                model[key] = (next, reward)
                let ordered = javaHashOrder(modelKeys)
                for _ in 0..<dynaPlanningSteps {
                    let (ms, ma) = ordered[random.nextInt(ordered.count)]
                    let (mNext, mReward) = model[ms * 4 + ma]!
                    let mTarget = mReward + (isTerminal(mNext) ? 0 : gamma * q[mNext].max()!)
                    q[ms][ma] += alpha * (mTarget - q[ms][ma])
                }
            }
            state = next
            visited.insert(state)
            steps += 1
        }
        // Only every fourth episode becomes a frame.
        if episode % 4 == 3 || episode == 0 {
            let outcome = state == rlGoal ? "reached the goal" : state == rlPit ? "fell in the pit" : "ran out of steps"
            frames.append(GridFrame(values: qValues(q), policy: qPolicy(q), agent: state, visited: visited,
                                    status: "Episode \(episode + 1): \(outcome) in \(steps) steps, ε = \(f2(epsilon))."))
        }
    }
    frames.append(GridFrame(values: qValues(q), policy: qPolicy(q), agent: nil, visited: [],
                            status: "\(label) learned the same policy value iteration computed — but from experience alone, never being told the transition or reward function."))
    return frames
}

// MARK: - Shared helpers for the Core Concepts topics

private func deterministic(_ actionIdx: Int) -> [Float] { (0..<4).map { $0 == actionIdx ? 1 : 0 } }
private let uniformActions: [Float] = [0.25, 0.25, 0.25, 0.25]

/// "Go right if you can, otherwise up": from the start it walks the bottom row and up into the pit.
private func rightThenUp(_ state: Int) -> Int { step(state, 1) != state ? 3 : 0 }

/// Expected-value backup weighted by π — a sum where value iteration takes a max.
private func policyEvaluation(_ gamma: Float, _ sweeps: Int, _ actionProbs: (Int) -> [Float]) -> [[Float]] {
    var values = terminalValues()
    var snapshots = [values]
    for _ in 0..<sweeps {
        var next = values
        for s in 0..<rlStates where !isTerminal(s) && s != rlWall {
            let probs = actionProbs(s)
            var v: Float = 0
            for a in actions.indices where probs[a] != 0 { v += probs[a] * backup(s, actions[a], values, gamma) }
            next[s] = v
        }
        values = next
        snapshots.append(values)
    }
    return snapshots
}

private func policyArrows(_ actionFor: (Int) -> Int) -> [Int] { (0..<rlStates).map { isTerminal($0) || $0 == rlWall ? -1 : actionFor($0) } }

private func convergedValues(_ gamma: Float, sweeps: Int = 80) -> [Float] {
    var values = terminalValues()
    for _ in 0..<sweeps {
        var next = values
        for s in 0..<rlStates where !isTerminal(s) && s != rlWall { next[s] = bestBackup(s, values, gamma) }
        values = next
    }
    return values
}

/// One optimal trajectory from the start, as (state, actionIndex) pairs.
private func optimalRollout(maxSteps: Int = 20) -> [(Int, Int)] {
    let gamma: Float = 0.9
    let policy = greedyPolicy(convergedValues(gamma), gamma)
    var path: [(Int, Int)] = []
    var state = rlStart
    var steps = 0
    while !isTerminal(state) && steps < maxSteps {
        let action = policy[state]
        if action < 0 { break }
        path.append((state, action))
        state = step(state, actions[action])
        steps += 1
    }
    return path
}

private func cellName(_ s: Int) -> String { "(\(s / rlCols),\(s % rlCols))" }

// MARK: - Agent & Environment

private func agentEnvironmentFrames() -> [GridFrame] {
    let tv = terminalValues()
    var frames = [GridFrame(values: tv, policy: noPolicy, agent: rlStart, visited: [],
                            status: "The split: the agent is the thing choosing a move. The grid, the wall, the pit and the rule that pays +1 at the goal are all environment — the agent cannot change any of them.",
                            showPolicy: false)]
    var visited: Set<Int> = [rlStart]
    for (i, (state, action)) in optimalRollout().enumerated() {
        let next = step(state, actions[action])
        visited.insert(next)
        frames.append(GridFrame(values: tv, policy: noPolicy, agent: state, visited: visited,
                                status: "Step \(i + 1) — agent side: it observes \(cellName(state)) and sends one action, \(arrows[action]). That is the entire outgoing channel.",
                                showPolicy: false))
        frames.append(GridFrame(values: tv, policy: noPolicy, agent: next, visited: visited,
                                status: "Step \(i + 1) — environment side: it applies its own dynamics, lands the agent in \(cellName(next)), and answers with reward \(f2(rewardFor(next))). The agent is never told why.",
                                showPolicy: false))
    }
    frames.append(GridFrame(values: tv, policy: noPolicy, agent: nil, visited: visited,
                            status: "Terminal reached, episode over, environment resets. Everything the agent will ever learn from has to be squeezed out of streams like this one.",
                            showPolicy: false))
    return frames
}

// MARK: - State, Action, Reward

private func stateActionRewardFrames() -> [GridFrame] {
    let gamma: Float = 0.9
    let tv = terminalValues()
    var frames = [GridFrame(values: tv, policy: noPolicy, agent: rlStart, visited: [],
                            status: "Three signals, one tuple: (s, a, r, s′). Reward here is +1 at the goal, −1 at the pit, and −0.04 on every other step — the step cost is what turns \"reach the goal\" into \"reach it soon\".",
                            showPolicy: false)]
    var visited: Set<Int> = [rlStart]
    var undiscounted: Float = 0
    var discounted: Float = 0
    var weight: Float = 1
    for (t, (state, action)) in optimalRollout().enumerated() {
        let next = step(state, actions[action])
        let reward = rewardFor(next)
        undiscounted += reward
        discounted += weight * reward
        weight *= gamma
        visited.insert(next)
        frames.append(GridFrame(values: tv, policy: noPolicy, agent: next, visited: visited,
                                status: "t=\(t): (s=\(cellName(state)), a=\(arrows[action]), r=\(f2(reward)), s′=\(cellName(next))). Running total \(f2(undiscounted)), discounted return \(fx(discounted, 3)). The reward is indexed t+1 because it arrives with the next state, not with the action.",
                                showPolicy: false))
    }
    frames.append(GridFrame(values: tv, policy: noPolicy, agent: nil, visited: visited,
                            status: "Return G₀ = \(fx(discounted, 3)) against a raw sum of \(f2(undiscounted)). This number — not any single reward — is what every algorithm in this section maximizes.",
                            showPolicy: false))
    return frames
}

// MARK: - The Policy

private func policyFrames() -> [GridFrame] {
    let gamma: Float = 0.9
    let randomValues = policyEvaluation(gamma, 60) { _ in uniformActions }.last!
    let naiveValues = policyEvaluation(gamma, 60) { deterministic(rightThenUp($0)) }.last!
    let optimalValues = convergedValues(gamma)
    let optimalPolicy = greedyPolicy(optimalValues, gamma)
    return [
        GridFrame(values: randomValues, policy: noPolicy, agent: nil, visited: [],
                  status: "A uniform random policy: π(a|s) = 0.25 for all four actions. It is a valid policy, and these are its true values — mostly negative, because a random walk pays the step cost far longer than it needs to.",
                  showPolicy: false),
        GridFrame(values: naiveValues, policy: policyArrows(rightThenUp), agent: nil, visited: [],
                  status: "A deterministic policy: \"go right if you can, otherwise up\". Note V(start) = \(f2(naiveValues[rlStart])) — it is confidently wrong, marching along the bottom row and up into the pit."),
        GridFrame(values: optimalValues, policy: optimalPolicy, agent: nil, visited: [],
                  status: "The optimal policy π*, worth \(f2(optimalValues[rlStart])) from the same start. Same MDP, same states, same actions — the only thing that changed is the mapping from one to the other."),
        GridFrame(values: optimalValues, policy: optimalPolicy, agent: rlStart, visited: [],
                  status: "π* here is deterministic, which a finite fully-observable MDP always permits. Take observability away and that guarantee dies: aliased states force a stochastic policy, which is the POMDP topic."),
    ]
}

// MARK: - Value function

private func valueFunctionFrames() -> [GridFrame] {
    let gamma: Float = 0.9
    let arrowsFixed = policyArrows(rightThenUp)
    let sweeps = policyEvaluation(gamma, 7) { deterministic(rightThenUp($0)) }
    var frames = [GridFrame(values: sweeps[0], policy: arrowsFixed, agent: nil, visited: [],
                            status: "Policy evaluation, not planning. The policy is fixed — \"right if you can, else up\" — and the only question is what it is worth. Everything starts at zero except the terminals.")]
    for (i, values) in sweeps.dropFirst().enumerated() {
        frames.append(GridFrame(values: values, policy: arrowsFixed, agent: nil, visited: [],
                                status: "Sweep \(i + 1): each state takes reward + γ·V of wherever THIS policy sends it. No max anywhere — value flows backwards along the policy's own path, so the pit's −1 spreads down the right column."))
    }
    let evaluated = sweeps.last!
    let optimal = convergedValues(gamma)
    frames.append(GridFrame(values: evaluated, policy: arrowsFixed, agent: nil, visited: [],
                            status: "Converged: this is Vπ for that policy. V(start) = \(f2(evaluated[rlStart])). The superscript matters — these numbers are a property of the policy, not of the grid."))
    frames.append(GridFrame(values: evaluated, policy: greedyPolicy(evaluated, gamma), agent: nil, visited: [],
                            status: "Now act greedily with respect to those same values and the arrows change — the states beside the pit turn away from it. Evaluate the new policy, improve again, repeat: that alternation is policy iteration."))
    frames.append(GridFrame(values: optimal, policy: greedyPolicy(optimal, gamma), agent: nil, visited: [],
                            status: "Where the alternation ends: V* = \(f2(optimal[rlStart])) at the start, against \(f2(evaluated[rlStart])) for the policy we began with."))
    return frames
}

// MARK: - Q-function: all four action values per cell

private func qFunctionFrames() -> [GridFrame] {
    let gamma: Float = 0.9
    let alpha: Float = 0.5
    var random = KotlinRandom(seed: 7)
    var q = [[Float]](repeating: [0, 0, 0, 0], count: rlStates)
    var frames = [GridFrame(values: qValues(q), policy: qPolicy(q), agent: rlStart, visited: [],
                            status: "Q is one number per action per state — four per cell, shown around the edges in ↑↓←→ order. All zero to begin with, and no transition model is available anywhere.",
                            showPolicy: false, qTable: q)]
    let episodes = 60
    for episode in 0..<episodes {
        var state = rlStart
        var steps = 0
        let epsilon = max(1 - Float(episode) / Float(episodes), 0.1)
        while !isTerminal(state) && steps < 60 {
            let action = random.nextFloat() < epsilon ? random.nextInt(actions.count) : argmaxFirst(q[state])
            let next = step(state, actions[action])
            let target = rewardFor(next) + (isTerminal(next) ? 0 : gamma * q[next].max()!)
            q[state][action] += alpha * (target - q[state][action])
            state = next
            steps += 1
        }
        if episode == 0 || episode % 10 == 9 {
            let start = q[rlStart]
            frames.append(GridFrame(values: qValues(q), policy: qPolicy(q), agent: nil, visited: [],
                                    status: "Episode \(episode + 1): at the start cell the four action values are now " + arrows.indices.map { "\(arrows[$0]) \(f2(start[$0]))" }.joined(separator: ", ") + ". The greedy action is \(arrows[argmaxFirst(start)]) — read straight off the table, with no lookahead.",
                                    qTable: q))
        }
    }
    frames.append(GridFrame(values: qValues(q), policy: qPolicy(q), agent: nil, visited: [],
                            status: "Every cell's shade is max Q(s,a) — that is V(s). The arrow is argmax Q(s,a) — that is the policy. Both fall out of the same table, which is why model-free control is built on Q and not on V.",
                            qTable: q))
    return frames
}

// MARK: - Discount factor

private func discountFactorFrames() -> [GridFrame] {
    let notes: [(Float, String)] = [
        (0, "γ = 0: a pure bandit. Value is just the immediate reward, so only the two cells touching a terminal have any opinion at all, and the agent is blind everywhere else."),
        (0.5, "γ = 0.5, horizon ≈ 2 steps. Value now leaks one or two cells out from the goal, and the far corner is still effectively unreachable information."),
        (0.9, "γ = 0.9, horizon ≈ 10 steps. The grid's longest path is about 6 moves, so this is the first γ where the start cell can actually see the goal."),
        (0.99, "γ = 0.99, horizon ≈ 100 steps. Values are nearly undiscounted and the whole grid is bright — but in a real problem this is also where bootstrap error compounds and training slows down."),
    ]
    var frames = notes.map { gamma, note -> GridFrame in
        let values = convergedValues(gamma)
        return GridFrame(values: values, policy: greedyPolicy(values, gamma), agent: nil, visited: [], status: "\(note)  V(start) = \(fx(values[rlStart], 3)).")
    }
    let v09 = convergedValues(0.9)
    frames.append(GridFrame(values: v09, policy: greedyPolicy(v09, 0.9), agent: rlStart, visited: [],
                            status: "One number, two jobs: γ < 1 keeps the infinite sum finite and makes the Bellman operator a contraction, and 1/(1−γ) sets how many steps ahead the agent can reason. Pick it from the task's horizon, not by maximizing it."))
    return frames
}

// MARK: - POMDP: the same grid through a bump counter

/// How many of the four moves are blocked — and nothing about which, or where.
private func observationOf(_ s: Int) -> Int { actions.filter { step(s, $0) == s }.count }

private func observationLabel(_ obs: Int) -> String {
    switch obs {
    case 0: "no sides blocked"
    case 1: "1 side blocked"
    default: "\(obs) sides blocked"
    }
}

private func candidateStates(_ obs: Int) -> Set<Int> {
    Set((0..<rlStates).filter { $0 != rlWall && !isTerminal($0) && observationOf($0) == obs })
}

/// Exact Bayes filter over the deterministic dynamics.
private func beliefUpdate(_ belief: [Float], _ actionIdx: Int, _ obs: Int) -> [Float] {
    var predicted = [Float](repeating: 0, count: rlStates)
    for s in 0..<rlStates where belief[s] > 0 { predicted[step(s, actions[actionIdx])] += belief[s] }
    var posterior = [Float](repeating: 0, count: rlStates)
    var total: Float = 0
    for s in 0..<rlStates where predicted[s] > 0 && s != rlWall && !isTerminal(s) && observationOf(s) == obs {
        posterior[s] = predicted[s]
        total += predicted[s]
    }
    if total <= 0 { return predicted }
    return posterior.map { $0 / total }
}

private func aliasedCountLabel(_ stepIndex: Int, _ support: Int) -> String {
    if support <= 1 { return "the belief has collapsed to a single state. The agent now knows where it is, from memory rather than from what it can see." }
    if stepIndex == 0 { return "\(support) candidates remain." }
    return "down to \(support). Each observation is weak on its own; accumulated, they localize."
}

private func pomdpFrames() -> [GridFrame] {
    let tv = terminalValues()
    var trueState = 13
    var frames = [GridFrame(values: tv, policy: noPolicy, agent: trueState, visited: [],
                            status: "As an MDP this is easy: the agent is told it is in \(cellName(trueState)) and every algorithm in this section applies.",
                            showPolicy: false)]
    let firstObs = observationOf(trueState)
    let aliased = candidateStates(firstObs)
    frames.append(GridFrame(values: tv, policy: noPolicy, agent: nil, visited: [],
                            status: "Now the agent has only a bump sensor: it feels how many sides are blocked, not which. Its reading is \"\(observationLabel(firstObs))\" — and \(aliased.count) different cells produce exactly that reading. The \"?\" cells are indistinguishable to it.",
                            showPolicy: false, fog: aliased))
    var belief = [Float](repeating: 0, count: rlStates)
    for s in aliased { belief[s] = 1 / Float(aliased.count) }
    frames.append(GridFrame(values: belief, policy: noPolicy, agent: nil, visited: [],
                            status: "The fix is to stop tracking a state and start tracking a belief: b(s), a posterior over where it might be. Uniform over \(aliased.count) candidates, \(f2(1 / Float(aliased.count))) each.",
                            showPolicy: false))
    for (i, actionIdx) in [2, 0].enumerated() {
        trueState = step(trueState, actions[actionIdx])
        let obs = observationOf(trueState)
        belief = beliefUpdate(belief, actionIdx, obs)
        let support = belief.filter { $0 > 0.001 }.count
        frames.append(GridFrame(values: belief, policy: noPolicy, agent: nil, visited: [],
                                status: "Move \(arrows[actionIdx]), then read the sensor: \"\(observationLabel(obs))\". Bayes rule pushes the belief through the dynamics and drops everything inconsistent — \(aliasedCountLabel(i, support))",
                                showPolicy: false))
    }
    frames.append(GridFrame(values: belief, policy: noPolicy, agent: nil, visited: [],
                            status: "Nothing in the last observation identified the state — the history did. That is the whole lesson: a policy over observations cannot solve this, and a policy over beliefs can, which is why partially observed agents carry an RNN or a frame stack.",
                            showPolicy: false))
    frames.append(GridFrame(values: tv, policy: noPolicy, agent: nil, visited: [],
                            status: "And while the states stay aliased, a deterministic reactive policy is trapped: it must give every \"?\" cell the same action, so if that action is wrong in one of them it loops forever. A stochastic policy escapes by coin flip.",
                            showPolicy: false, fog: aliased))
    return frames
}

// MARK: - Bellman equation: one state's backup, term by term

private func bellmanEquationFrames() -> [GridFrame] {
    let gamma: Float = 0.9
    let values = convergedValues(gamma)
    let policy = greedyPolicy(values, gamma)
    let focus = 2
    var frames = [GridFrame(values: values, policy: policy, agent: focus, visited: [],
                            status: "The Bellman equation is a consistency condition, not an algorithm: a state's value must equal the reward for leaving it plus the discounted value of where it lands. Take the highlighted cell \(cellName(focus)) and check all four actions.")]
    for a in actions.indices {
        let next = step(focus, actions[a])
        let reward = rewardFor(next)
        let bootstrap: Float = isTerminal(next) ? 0 : gamma * values[next]
        let detail = isTerminal(next) ? "lands on a terminal, so there is no future to discount" : "γ·V(\(cellName(next))) = 0.9 × \(f2(values[next])) = \(f2(bootstrap))"
        frames.append(GridFrame(values: values, policy: policy, agent: next, visited: [],
                                status: "Action \(arrows[a]) → \(cellName(next)): r = \(f2(reward)), \(detail). Backup value = \(f2(reward + bootstrap))."))
    }
    let best = argmaxFirst(actions.map { backup(focus, $0, values, gamma) })
    frames.append(GridFrame(values: values, policy: policy, agent: focus, visited: [],
                            status: "V*(\(cellName(focus))) = max over those four = \(f2(values[focus])), achieved by \(arrows[best]). That is the Bellman optimality equation. Swap the max for an average weighted by π and you get the Bellman expectation equation instead."))
    frames.append(GridFrame(values: values, policy: policy, agent: nil, visited: [],
                            status: "Every cell satisfies the same condition simultaneously — |S| equations in |S| unknowns. Value iteration, policy iteration, TD and Q-learning are all just different ways of solving this system."))
    return frames
}

// MARK: - Dynamic programming

private func dynamicProgrammingFrames() -> [GridFrame] {
    let gamma: Float = 0.9
    var values = terminalValues()
    var backups = 0
    let planningStates = (0..<rlStates).filter { !isTerminal($0) && $0 != rlWall }.count
    var frames = [GridFrame(values: values, policy: noPolicy, agent: nil, visited: [],
                            status: "Dynamic programming solves an MDP the way it solves any DP problem: overlapping subproblems (a state's value reuses its neighbours') and optimal substructure (the optimal path's tail is itself optimal). The catch is the precondition — P and R must be known.",
                            showPolicy: false)]
    for sweep in 0..<6 {
        var next = values
        for s in 0..<rlStates where !isTerminal(s) && s != rlWall {
            next[s] = bestBackup(s, values, gamma)
            backups += actions.count
        }
        values = next
        frames.append(GridFrame(values: values, policy: greedyPolicy(values, gamma), agent: nil, visited: [],
                                status: "Sweep \(sweep + 1): all \(planningStates) non-terminal states updated in one pass, \(backups) backups so far. Nothing was sampled and no episode was run — the model was queried directly, which is the difference between planning and learning."))
    }
    frames.append(GridFrame(values: values, policy: greedyPolicy(values, gamma), agent: nil, visited: [],
                            status: "Solved in \(backups) backups, exactly and without touching the environment once. That is DP's bargain: it is the fastest method here, and it is unusable the moment you do not have the model — which is almost always."))
    return frames
}

// MARK: - Policy iteration

private func policyIterationFrames() -> [GridFrame] {
    let gamma: Float = 0.9
    var current = (0..<rlStates).map { isTerminal($0) || $0 == rlWall ? -1 : rightThenUp($0) }
    var frames = [GridFrame(values: terminalValues(), policy: current, agent: nil, visited: [],
                            status: "Start from any policy at all — here the naive \"right if you can, else up\", which marches into the pit. Policy iteration will fix it in a handful of rounds.")]
    var round = 0
    while round < 6 {
        round += 1
        let fixed = current
        let evaluated = policyEvaluation(gamma, 60) { fixed[$0] < 0 ? [0, 0, 0, 0] : deterministic(fixed[$0]) }.last!
        frames.append(GridFrame(values: evaluated, policy: fixed, agent: nil, visited: [],
                                status: "Round \(round) — evaluate: run policy evaluation to convergence for this exact policy. V(start) = \(f2(evaluated[rlStart]))."))
        let improved = greedyPolicy(evaluated, gamma)
        let changed = (0..<rlStates).filter { !isTerminal($0) && $0 != rlWall && improved[$0] != fixed[$0] }.count
        frames.append(GridFrame(values: evaluated, policy: improved, agent: nil, visited: [],
                                status: changed == 0
                                    ? "Round \(round) — improve: acting greedily on those values changes nothing. The policy is stable, which for a finite MDP means it is optimal. Done in \(round) round\(round == 1 ? "" : "s")."
                                    : "Round \(round) — improve: acting greedily on those values changes the action in \(changed) state\(changed == 1 ? "" : "s"). Any change means the new policy is strictly better, so go evaluate it."))
        if changed == 0 { break }
        current = improved
    }
    let optimal = convergedValues(gamma)
    frames.append(GridFrame(values: optimal, policy: greedyPolicy(optimal, gamma), agent: rlStart, visited: [],
                            status: "Policy iteration terminates in finitely many rounds because there are finitely many policies and every round is a strict improvement. Each round is expensive — a full evaluation — but there are very few of them."))
    return frames
}

// MARK: - Value iteration, in detail

private func valueIterationDetailFrames() -> [GridFrame] {
    let gamma: Float = 0.9
    var values = terminalValues()
    let optimalPolicy = greedyPolicy(convergedValues(gamma), gamma)
    var policyOptimalAt = -1
    var frames = [GridFrame(values: values, policy: noPolicy, agent: nil, visited: [],
                            status: "Value iteration is policy iteration with the evaluation cut short: instead of running evaluation to convergence, take exactly one backup and fold the improvement in as a max. No policy is ever stored.",
                            showPolicy: false)]
    for sweep in 1...10 {
        var next = values
        var delta: Float = 0
        for s in 0..<rlStates where !isTerminal(s) && s != rlWall {
            let best = bestBackup(s, values, gamma)
            delta = max(delta, abs(best - values[s]))
            next[s] = best
        }
        values = next
        let policy = greedyPolicy(values, gamma)
        let matches = (0..<rlStates).allSatisfy { isTerminal($0) || $0 == rlWall || policy[$0] == optimalPolicy[$0] }
        if matches && policyOptimalAt < 0 { policyOptimalAt = sweep }
        frames.append(GridFrame(values: values, policy: policy, agent: nil, visited: [],
                                status: "Sweep \(sweep): largest value change \(fx(delta, 4)). The greedy policy implied by these values is \(matches ? "already optimal" : "not optimal yet")."))
    }
    frames.append(GridFrame(values: values, policy: greedyPolicy(values, gamma), agent: rlStart, visited: [],
                            status: "Worth noticing: the policy became optimal at sweep \(policyOptimalAt), but the values kept moving for several sweeps after that. If you only want to act well you can stop early — the numbers converge long after the decisions do."))
    return frames
}

// MARK: - Monte Carlo and TD prediction, against the exact answer

private let predictionGamma: Float = 0.9

private func maxError(_ estimate: [Float], _ exact: [Float]) -> Float {
    (0..<rlStates).filter { !isTerminal($0) && $0 != rlWall }.map { abs(estimate[$0] - exact[$0]) }.max()!
}

/// Exploring starts: begin anywhere, or most of the table is never visited.
private func randomStart(_ random: inout KotlinRandom) -> Int {
    while true {
        let s = random.nextInt(rlStates)
        if !isTerminal(s) && s != rlWall { return s }
    }
}

private func predictionFrames(useTd: Bool) -> [GridFrame] {
    let exact = policyEvaluation(predictionGamma, 200) { deterministic(rightThenUp($0)) }.last!
    let arrowsFixed = policyArrows(rightThenUp)
    var random = KotlinRandom(seed: 5)
    var values = terminalValues()
    var returnsSum = [Float](repeating: 0, count: rlStates)
    var returnsCount = [Int](repeating: 0, count: rlStates)
    let alpha: Float = 0.1
    var frames = [GridFrame(values: values, policy: arrowsFixed, agent: nil, visited: [],
                            status: useTd
                                ? "TD(0) prediction of the same fixed policy. Every single step produces an update: V(s) moves toward r + γV(s′), an estimate built on another estimate — that is bootstrapping."
                                : "Monte Carlo prediction of a fixed policy. No model, no bootstrapping: play a whole episode, compute the actual return from each state, and average. The exact answer is known here, so the error can be measured rather than eyeballed.")]
    let checkpoints: Set<Int> = [1, 5, 20, 60, 200, 600, 2000]
    var updates = 0
    for episode in 0..<2000 {
        var state = randomStart(&random)
        var trajectory: [(Int, Float)] = []
        var steps = 0
        while !isTerminal(state) && steps < 100 {
            let next = step(state, actions[rightThenUp(state)])
            let reward = rewardFor(next)
            if useTd {
                let target = reward + (isTerminal(next) ? 0 : predictionGamma * values[next])
                values[state] += alpha * (target - values[state])
                updates += 1
            } else {
                trajectory.append((state, reward))
            }
            state = next
            steps += 1
        }
        if !useTd {
            var g: Float = 0
            var seen = Set<Int>()
            for (s, r) in trajectory.reversed() {
                g = r + predictionGamma * g
                if seen.insert(s).inserted {
                    returnsSum[s] += g
                    returnsCount[s] += 1
                    values[s] = returnsSum[s] / Float(returnsCount[s])
                    updates += 1
                }
            }
        }
        let n = episode + 1
        if checkpoints.contains(n) {
            frames.append(GridFrame(values: values, policy: arrowsFixed, agent: nil, visited: [],
                                    status: "After \(n) episode\(n == 1 ? "" : "s") (\(updates) updates): largest error against the exact Vπ is \(fx(maxError(values, exact), 3))." + (useTd ? " TD has been updating since its very first step." : " Every one of those updates had to wait for an episode to finish.")))
        }
    }
    frames.append(GridFrame(values: exact, policy: arrowsFixed, agent: nil, visited: [],
                            status: useTd
                                ? "For reference, the exact Vπ computed by dynamic programming. TD reached it without ever seeing P or R — and unlike Monte Carlo, it never had to wait for an episode to end, which is why it works on continuing tasks that have no end."
                                : "For reference, the exact Vπ computed by dynamic programming from the model. Monte Carlo matched it from sampled returns alone — but it is unbiased at the cost of high variance, and it cannot start learning until an episode terminates."))
    return frames
}

// MARK: - SARSA: on-policy control, and the cliff that reveals it

private struct CliffRun { let q: [[Float]]; let falls: Int; let path: [Int] }

private func runCliff(onPolicy: Bool, episodes: Int = 4000, seed: Int = 1) -> CliffRun {
    let world = cliffWorld
    var random = KotlinRandom(seed: seed)
    let alpha: Float = 0.5
    let gamma: Float = 1
    let epsilon: Float = 0.1
    var q = [[Float]](repeating: [0, 0, 0, 0], count: world.states)
    func pick(_ s: Int) -> Int { random.nextFloat() < epsilon ? random.nextInt(world.actions.count) : argmaxFirst(q[s]) }

    for _ in 0..<episodes {
        var state = world.start
        var action = pick(state)
        var steps = 0
        while !world.isTerminal(state) && steps < 200 {
            let next = world.step(state, world.actions[action])
            let reward = world.rewardFor(next)
            let nextAction = pick(next)
            // SARSA bootstraps off the action it will take; Q-learning off the best one.
            let bootstrap: Float = world.isTerminal(next) ? 0 : onPolicy ? q[next][nextAction] : q[next].max()!
            q[state][action] += alpha * (reward + gamma * bootstrap - q[state][action])
            state = next
            action = nextAction
            steps += 1
        }
    }
    // Score the learned table the way it would actually be used: still exploring.
    var scorer = KotlinRandom(seed: seed + 100)
    var falls = 0
    for _ in 0..<500 {
        var s = world.start
        var steps = 0
        while steps < 200 {
            let a = scorer.nextFloat() < epsilon ? scorer.nextInt(world.actions.count) : argmaxFirst(q[s])
            s = world.step(s, world.actions[a])
            if world.pits.contains(s) { falls += 1; break }
            if s == world.goal { break }
            steps += 1
        }
    }
    var path = [world.start]
    var s = world.start
    for _ in 0..<24 where !world.isTerminal(s) {
        s = world.step(s, world.actions[argmaxFirst(q[s])])
        path.append(s)
    }
    return CliffRun(q: q, falls: falls, path: path)
}

private func cliffValues(_ q: [[Float]]) -> [Float] { (0..<cliffWorld.states).map { cliffWorld.isTerminal($0) ? cliffWorld.rewardFor($0) : q[$0].max()! } }

private func cliffPolicy(_ q: [[Float]]) -> [Int] {
    (0..<cliffWorld.states).map { s in
        if cliffWorld.isTerminal(s) || q[s].allSatisfy({ $0 == 0 }) { return -1 }
        return argmaxFirst(q[s])
    }
}

private func sarsaFrames() -> [GridFrame] {
    let world = cliffWorld
    let sarsa = runCliff(onPolicy: true)
    let qlearn = runCliff(onPolicy: false)
    return [
        GridFrame(values: [Float](repeating: 0, count: world.states), policy: [Int](repeating: -1, count: world.states), agent: world.start, visited: [],
                  status: "A different grid, because SARSA's behaviour is invisible without one. Start bottom-left, goal bottom-right, and a cliff in between: −100 to fall in, −1 per step. The short route runs right along the cliff edge.",
                  showPolicy: false, world: world),
        GridFrame(values: cliffValues(qlearn.q), policy: cliffPolicy(qlearn.q), agent: nil, visited: [],
                  status: "Q-learning after 4,000 episodes. Its target uses maxₐ′ Q(s′,a′) — the value of the best action, whether or not it takes it — so it learns the values of a perfectly greedy agent and walks the \(qlearn.path.count - 1)-step cliff edge.",
                  world: world),
        GridFrame(values: cliffValues(sarsa.q), policy: cliffPolicy(sarsa.q), agent: nil, visited: [],
                  status: "SARSA on the identical problem. Its target uses Q(s′,a′) for the action it will actually take — including the 10% that are random — so the cliff's −100 leaks back into the cells beside it. It takes the longer \(sarsa.path.count - 1)-step detour.",
                  world: world),
        GridFrame(values: cliffValues(sarsa.q), policy: cliffPolicy(sarsa.q), agent: nil, visited: [],
                  status: "Scored over 500 episodes with exploration still on: SARSA fell in \(sarsa.falls) times, Q-learning \(qlearn.falls). Q-learning's policy is optimal for an agent that never slips; SARSA's is better for the agent that actually exists.",
                  world: world),
        GridFrame(values: cliffValues(qlearn.q), policy: cliffPolicy(qlearn.q), agent: nil, visited: [],
                  status: "Neither is wrong — they answer different questions. Off-policy learns about the greedy policy from any data; on-policy learns about the policy generating the data, exploration included. Drive ε to zero and the two answers converge.",
                  world: world),
    ]
}

// MARK: - Config

private let gridConfigs: [String: GridConfig] = [
    "bellman_equation": GridConfig(intro: "One cell, four actions, and the arithmetic behind each — the equation checked term by term rather than stated.", valueLabel: "V*(s), already converged", build: bellmanEquationFrames),
    "dynamic_programming": GridConfig(intro: "Planning with the model in hand: full sweeps over every state, with the backup count as the running cost.", valueLabel: "V(s) after each sweep", build: dynamicProgrammingFrames),
    "policy_iteration": GridConfig(intro: "Evaluate a policy to convergence, act greedily on the result, repeat. Starting from a policy that walks into the pit, so there is something to fix.", valueLabel: "Vπ(s) for the current policy", build: policyIterationFrames),
    "value_iteration": GridConfig(intro: "One backup per state per sweep, with the max folded in. Watch for the sweep where the policy stops changing — it arrives well before the values settle.", valueLabel: "V(s), converging to V*", build: valueIterationDetailFrames),
    "monte_carlo_rl": GridConfig(intro: "Monte Carlo prediction from complete episodes, with the error measured against the exact Vπ that dynamic programming computes from the model.", valueLabel: "V̂π(s) from averaged returns", build: { predictionFrames(useTd: false) }),
    "td_learning": GridConfig(intro: "TD(0) on the same policy and the same error metric as the Monte Carlo lab, so the two are directly comparable.", valueLabel: "V̂π(s) from bootstrapped updates", build: { predictionFrames(useTd: true) }),
    "sarsa": GridConfig(intro: "SARSA and Q-learning trained on the same cliff, from the same seed, differing in one term of the update — and ending up with visibly different routes.", valueLabel: "max Q(s,a)", build: sarsaFrames),
    "agent_environment": GridConfig(intro: "One episode, split into the two halves of the loop: what the agent sends, and what the environment sends back. Nothing else crosses the boundary.", valueLabel: "Terminal rewards only — no values learned yet", build: agentEnvironmentFrames),
    "state_action_reward": GridConfig(intro: "The same episode, read as a stream of (s, a, r, s′) tuples, with the discounted return accumulating underneath.", valueLabel: "Reward at the terminals", build: stateActionRewardFrames),
    "policy": GridConfig(intro: "Three policies over one MDP — random, a plausible-looking hand-written rule, and the optimal one — each shown with the values it actually achieves.", valueLabel: "Vπ(s) for the policy shown", build: policyFrames),
    "value_function": GridConfig(intro: "Policy evaluation: a fixed policy, swept until its values converge. The sum over π where value iteration takes a max is the only difference, and it changes the answer.", valueLabel: "Vπ(s)", build: valueFunctionFrames),
    "q_function": GridConfig(intro: "Tabular Q-learning with all four action values visible per cell, in ↑↓←→ order. Watch V(s) = max of the four, and the policy = argmax of the four.", valueLabel: "Q(s,a) per action; shading is max Q", build: qFunctionFrames),
    "discount_factor": GridConfig(intro: "The identical MDP solved four times, changing only γ. The horizon 1/(1−γ) is visible as how far value spreads from the goal.", valueLabel: "V*(s) at each γ", build: discountFactorFrames),
    "pomdp": GridConfig(intro: "The same grid behind a wall sensor: the agent learns only which moves are blocked. Watch a real Bayes filter turn a set of indistinguishable cells into a single located one.", valueLabel: "Belief b(s)", build: pomdpFrames),
    "mdp": GridConfig(intro: "A 4×4 grid MDP: goal +1, pit −1, one wall, −0.04 per step, γ = 0.9. Value iteration sweeps the whole state space until the numbers stop moving.", valueLabel: "State value V(s)", build: valueIterationFrames),
    "grid_world": GridConfig(intro: "The canonical RL testbed. Every RL algorithm here is judged on the same question: from the bottom-left corner, can it learn to reach +1 while avoiding −1 next to it?", valueLabel: "State value V(s)", build: valueIterationFrames),
    "q_learning": GridConfig(intro: "Tabular Q-learning over 40 ε-greedy episodes. Nothing about the environment is given — the values below are built purely from reward the agent stumbled into.", valueLabel: "max Q(s, a)", build: { qLearningFrames() }),
    "dyna_q": GridConfig(intro: "Dyna-Q: the same 40 episodes as plain Q-learning, but each real step also feeds a learned model that is then replayed 5 times. Compare how much earlier the arrows settle.", valueLabel: "max Q(s, a)", build: { qLearningFrames(dynaPlanningSteps: 5) }),
]

// MARK: - UI

private func signed(_ v: Float) -> String {
    let trimmed = v == v.rounded() ? "\(Int(v))" : f2(v)
    return v > 0 ? "+\(trimmed)" : trimmed
}

struct RlGridWorldLab: View {
    private let config: GridConfig
    private let key: String
    @Environment(\.palette) private var palette

    init(topicId: String) {
        key = gridConfigs[topicId] == nil ? "grid_world" : topicId
        config = gridConfigs[key]!
    }

    var body: some View {
        let key = self.key
        AsyncFrameLab(key: "gridworld:\(key)", speedMs: 600, build: { gridConfigs[key]!.build() }) { frames, playback in
            content(frames, playback)
        }
    }

    @ViewBuilder
    private func content(_ frames: [GridFrame], _ playback: PlaybackState) -> some View {
        let frame = frames[min(playback.index, frames.count - 1)]
        let world = frame.world
        LabCard {
            LabIntro(text: config.intro)
            Text(config.valueLabel).font(AppFont.sans(12, .bold)).foregroundStyle(palette.primary).padding(.top, 12)
            RlGridView(frame: frame).padding(.top, 8)
            LabCaption(text: frame.status).padding(.top, 14)
            // Labelled from the frame's own world — the cliff world's numbers are not the default's.
            FlowLayout(spacing: 12, lineSpacing: 6) {
                LegendDot(color: goalColor, label: "Goal \(signed(world.goalReward))")
                LegendDot(color: pitColor, label: "\(world.pits.count > 1 ? "Cliff" : "Pit") \(signed(world.pitReward))")
                LegendDot(color: agentColor, label: "Agent")
                LegendDot(color: positiveValue, label: "Value")
            }
            .padding(.top, 10)
            PlaybackTransport(state: playback, captions: frames.map(\.status))
        }
    }
}

private struct RlGridView: View {
    let frame: GridFrame
    @Environment(\.palette) private var palette

    var body: some View {
        let world = frame.world
        let peak = frame.values.map { abs($0) }.max().flatMap { $0 > 0.001 ? $0 : nil } ?? 1
        Grid(horizontalSpacing: 4, verticalSpacing: 4) {
            ForEach(0..<world.rows, id: \.self) { r in
                GridRow {
                    ForEach(0..<world.cols, id: \.self) { c in
                        let s = r * world.cols + c
                        let value = frame.values[s]
                        let color: Color = world.walls.contains(s) ? wallColor
                            : s == world.goal ? goalColor
                            : world.pits.contains(s) ? pitColor
                            : s == frame.agent ? agentColor
                            : value < 0 ? pitColor.opacity(Double(min(max(abs(value) / peak, 0.08), 0.55)))
                            : positiveValue.opacity(Double(min(max(value / peak, 0.08), 0.75)))
                        RoundedRectangle(cornerRadius: 8)
                            .fill(color)
                            .aspectRatio(1, contentMode: .fit)
                            .overlay { cellContent(s, value, world) }
                    }
                }
            }
        }
    }

    @ViewBuilder
    private func cellContent(_ s: Int, _ value: Float, _ world: RlWorld) -> some View {
        if world.walls.contains(s) {
            EmptyView()
        } else if frame.fog.contains(s) {
            Text("?").font(AppFont.grotesk(18, .bold)).foregroundStyle(palette.onSurface)
        } else if let q = frame.qTable, !world.isTerminal(s) {
            QuadView(q: q[s])
        } else {
            VStack(spacing: 0) {
                Text(f2(value)).font(AppFont.sans(11, .bold)).foregroundStyle(palette.onSurface)
                let arrow = s < frame.policy.count ? frame.policy[s] : -1
                if frame.showPolicy && arrow >= 0 {
                    Text(arrows[arrow]).font(AppFont.sans(14, .bold)).foregroundStyle(palette.onSurface)
                }
                if s == world.start && frame.agent == nil {
                    Text("start").font(.labelSmall).foregroundStyle(palette.muted)
                }
            }
        }
    }
}

/// The four action values placed where their actions point, with the largest highlighted.
private struct QuadView: View {
    let q: [Float]
    @Environment(\.palette) private var palette

    var body: some View {
        let best = q.contains { $0 != 0 } ? argmaxFirst(q) : -1
        VStack(spacing: 0) {
            value(0, best)
            HStack(spacing: 0) {
                value(2, best)
                Text(best >= 0 ? arrows[best] : "·").font(AppFont.sans(12, .bold)).foregroundStyle(palette.onSurface).padding(.horizontal, 3)
                value(3, best)
            }
            value(1, best)
        }
        .minimumScaleFactor(0.6)
    }

    private func value(_ index: Int, _ best: Int) -> some View {
        Text(f2(q[index]))
            .font(AppFont.sans(11, index == best ? .bold : .regular))
            .foregroundStyle(index == best ? palette.onSurface : palette.muted)
            .lineLimit(1)
    }
}
