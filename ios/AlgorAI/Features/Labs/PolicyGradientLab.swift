import SwiftUI

// Port of PolicyGradientSection.kt: the policy-gradient and continuous-control families. REINFORCE
// and its variance-reduced variants are estimated over hundreds of rollouts on a small chain MDP;
// the continuous-control labs use a one-dimensional action with a known reward curve. The recurring
// measurement is the standard deviation of one gradient component across independent rollouts.

private struct PgFrame {
    let status: String
    var plot: FramePlot?
    var bars: [FrameBars] = []
    var readout: String?
}

private struct PgConfig {
    let intro: String
    let legend: [(Color, String)]
    let build: () -> [PgFrame]
}

private let plainColor = SimColors.grey
private let betterColor = SimColors.green
private let riskColor = Color(hex: 0xEC4899)
private let mainColor = SimColors.blue
private let deepColor = Color(hex: 0x7C3AED)

private extension Lcg {
    /// The sum of 6 uniforms, recentred: normal enough for these experiments.
    mutating func normal() -> Float {
        var sum = 0.0
        for _ in 0..<6 { sum += Double(nextF()) }
        return (Float(sum) - 3) / 0.707
    }
}

// MARK: - The discrete environment: the six-state chain with a softmax policy and a step cost.

private let pgStates = 6
private let pgGoal = pgStates - 1
private let pgGamma: Float = 0.95
private let stepCost: Float = -0.02

private func policy(_ theta: [[Float]], _ state: Int) -> [Float] {
    let logits = theta[state]
    let m = max(logits[0], logits[1])
    let e0 = expf(logits[0] - m)
    let e1 = expf(logits[1] - m)
    return [e0 / (e0 + e1), e1 / (e0 + e1)]
}

private struct Step { let state: Int; let action: Int; let reward: Float }

private func rollout(_ theta: [[Float]], _ rng: inout Lcg, maxSteps: Int = 20) -> [Step] {
    var steps: [Step] = []
    var state = 0
    for _ in 0..<maxSteps {
        let p = policy(theta, state)
        let action = rng.nextF() < p[1] ? 1 : 0
        let next = action == 1 ? min(state + 1, pgGoal) : max(state - 1, 0)
        steps.append(Step(state: state, action: action, reward: stepCost + (next == pgGoal ? 1 : 0)))
        state = next
        if state == pgGoal { return steps }
    }
    return steps
}

private func returns(_ episode: [Step]) -> [Float] {
    var out = [Float](repeating: 0, count: episode.count)
    var running: Float = 0
    for t in episode.indices.reversed() {
        running = episode[t].reward + pgGamma * running
        out[t] = running
    }
    return out
}

private func totalReturn(_ episode: [Step]) -> Float { returns(episode).first ?? 0 }

/// A policy that is decent but not optimal — where gradient noise matters.
private func startingTheta() -> [[Float]] { [[Float]](repeating: [0, 0.4], count: pgStates) }

/// Monte-Carlo state values under `theta`, the baseline / critic below.
private func estimateValues(_ theta: [[Float]], seed: Int, episodes: Int = 400) -> [Float] {
    var rng = Lcg(seed)
    var sums = [Float](repeating: 0, count: pgStates)
    var counts = [Int](repeating: 0, count: pgStates)
    for _ in 0..<episodes {
        let episode = rollout(theta, &rng)
        let g = returns(episode)
        for (t, step) in episode.enumerated() {
            sums[step.state] += g[t]
            counts[step.state] += 1
        }
    }
    return (0..<pgStates).map { counts[$0] == 0 ? 0 : sums[$0] / Float(counts[$0]) }
}

private enum Estimator { case ret, baseline, td, gae }

/// One rollout's estimate of ∂J/∂θ[state][1] under the chosen advantage estimator.
private func gradientSample(_ theta: [[Float]], _ values: [Float], _ episode: [Step], _ estimator: Estimator,
                            lambda: Float = 0.95, trackedState: Int = 2) -> Float {
    let g = returns(episode)
    func delta(_ t: Int) -> Float {
        let next: Float = t + 1 < episode.count ? values[episode[t + 1].state] : 0
        return episode[t].reward + pgGamma * next - values[episode[t].state]
    }
    let advantages: [Float]
    switch estimator {
    case .ret: advantages = g
    case .baseline: advantages = episode.indices.map { g[$0] - values[episode[$0].state] }
    case .td: advantages = episode.indices.map(delta)
    case .gae:
        let deltas = episode.indices.map(delta)
        var out = [Float](repeating: 0, count: episode.count)
        var running: Float = 0
        for t in episode.indices.reversed() {
            running = deltas[t] + pgGamma * lambda * running
            out[t] = running
        }
        advantages = out
    }
    var total: Float = 0
    for (t, step) in episode.enumerated() where step.state == trackedState {
        let p = policy(theta, step.state)
        // ∂ log π(a|s) / ∂θ[s][1] = 1{a = 1} − π(1|s)
        total += advantages[t] * ((step.action == 1 ? 1 : 0) - p[1])
    }
    return total
}

private struct Spread { let mean: Float; let sd: Float; let samples: [Float] }

private func spreadOf(_ estimator: Estimator, seed: Int, rollouts: Int = 300, lambda: Float = 0.95, workers: Int = 1) -> Spread {
    let theta = startingTheta()
    let values = estimateValues(theta, seed: seed + 991)
    var rng = Lcg(seed)
    var samples: [Float] = []
    for _ in 0..<rollouts {
        // Averaging `workers` independent rollouts is what a parallel actor batch does.
        var batch: [Float] = []
        for _ in 0..<workers { batch.append(gradientSample(theta, values, rollout(theta, &rng), estimator, lambda: lambda)) }
        samples.append(Float(batch.average))
    }
    let mean = Float(samples.average)
    let sd = (Float(samples.reduce(0.0) { $0 + Double(($1 - mean) * ($1 - mean)) }) / Float(samples.count)).squareRoot()
    return Spread(mean: mean, sd: sd, samples: samples)
}

private func histogram(_ samples: [Float], bins: Int = 9, range: Float = 1.2) -> [Float] {
    var counts = [Float](repeating: 0, count: bins)
    for value in samples {
        let clamped = min(max(value, -range), range)
        let bin = min(max(Int((clamped + range) / (2 * range) * Float(bins - 1)), 0), bins - 1)
        counts[bin] += 1
    }
    return counts.map { $0 / Float(samples.count) }
}

private let histCaps: [String] = (0..<9).map { fx(-1.2 + 2 * 1.2 * Float($0) / 8, 1) }

private func hist(_ label: String, _ s: Spread, _ color: Color) -> FrameBars {
    FrameBars(label: label, values: histogram(s.samples), color: color, captions: histCaps)
}

private func f3(_ x: Float) -> String { fx(x, 3) }
private func f2(_ x: Float) -> String { fx(x, 2) }

// MARK: - REINFORCE

/// Train with the given estimator; the return per update, averaged over several seeds.
private func trainCurve(_ estimator: Estimator, seeds: [Int], iterations: Int = 60, batch: Int = 8, lr: Float = 0.4) -> [Float] {
    let perSeed: [[Float]] = seeds.map { seed in
        var theta = startingTheta()
        var rng = Lcg(seed)
        let values = estimateValues(startingTheta(), seed: seed + 77)
        var curve: [Float] = []
        for _ in 0..<iterations {
            var gradient = [[Float]](repeating: [0, 0], count: pgStates)
            var meanReturn: Float = 0
            for _ in 0..<batch {
                let episode = rollout(theta, &rng)
                meanReturn += totalReturn(episode) / Float(batch)
                let g = returns(episode)
                for (t, step) in episode.enumerated() {
                    let advantage = estimator == .ret ? g[t] : g[t] - values[step.state]
                    let p = policy(theta, step.state)
                    gradient[step.state][1] += advantage * ((step.action == 1 ? 1 : 0) - p[1]) / Float(batch)
                }
            }
            for s in 0..<pgStates { theta[s][1] += lr * gradient[s][1] }
            curve.append(meanReturn)
        }
        return curve
    }
    return (0..<iterations).map { i in Float(perSeed.map { $0[i] }.average) }
}

private func reinforceFrames() -> [PgFrame] {
    let plain = spreadOf(.ret, seed: 13)
    let curve = trainCurve(.ret, seeds: [3, 19, 41])
    let ratio = fx(plain.sd / abs(plain.mean), 1)
    return [
        PgFrame(status: "Value methods learn what states are worth and derive a policy. REINFORCE skips that: it adjusts the policy's parameters directly, pushing up the log-probability of actions that preceded a good return.",
                bars: [FrameBars(label: "π(right | s) at the start", values: (0..<pgGoal).map { policy(startingTheta(), $0)[1] }, color: mainColor, captions: (0..<pgGoal).map { "s\($0)" })]),
        PgFrame(status: "It works: over 60 updates the average return climbs from \(f2(curve.first!)) to \(f2(curve.last!)), with no value function anywhere in the algorithm.",
                plot: FramePlot(label: "mean return per update", curves: [FrameCurve(label: "REINFORCE", values: curve, color: mainColor)], yRange: -0.4...1, xLabel: "update"),
                readout: "return \(f2(curve.first!)) → \(f2(curve.last!))"),
        PgFrame(status: "The catch is in the estimator, not the idea. Here are \(plain.samples.count) independent estimates of the *same* gradient component, from \(plain.samples.count) rollouts of an unchanged policy. They should all be estimating one number.",
                bars: [hist("distribution of ∂J/∂θ estimates", plain, riskColor)],
                readout: "mean \(f3(plain.mean)) · sd \(f3(plain.sd))"),
        PgFrame(status: "The spread is \(ratio)× the mean itself — the signal is buried in noise, because every action in the episode is credited with the entire return, including the parts it had nothing to do with. Every method in this family is an attack on that number.",
                bars: [hist("distribution of ∂J/∂θ estimates", plain, riskColor)],
                readout: "sd/|mean| = \(ratio)"),
    ]
}

// MARK: - Actor-critic

private func actorCriticFrames() -> [PgFrame] {
    let plain = spreadOf(.ret, seed: 13)
    let baseline = spreadOf(.baseline, seed: 13)
    let td = spreadOf(.td, seed: 13)
    return [
        PgFrame(status: "Start from REINFORCE's problem: gradient estimates with a standard deviation of \(f3(plain.sd)) around a mean of \(f3(plain.mean)).",
                bars: [hist("REINFORCE", plain, plainColor)], readout: "sd \(f3(plain.sd))"),
        PgFrame(status: "Subtract a baseline — what this state was worth on average — so the update reacts to whether the return beat expectations rather than to its absolute size. Any state-dependent baseline leaves the expected gradient untouched, and the spread drops to \(f3(baseline.sd)). (The two means differ by \(f3(abs(plain.mean - baseline.mean))) here — that is sampling noise over 300 rollouts, not bias.)",
                bars: [hist("REINFORCE", plain, plainColor), hist("with baseline", baseline, betterColor)],
                readout: "sd \(f3(plain.sd)) → \(f3(baseline.sd)), mean \(f3(plain.mean)) → \(f3(baseline.mean))"),
        PgFrame(status: "A critic goes further: replace the sampled return entirely with r + γV(s′) − V(s). Now a single transition carries the signal, so the estimate stops depending on everything that happened afterwards — spread \(f3(td.sd)).",
                bars: [hist("REINFORCE", plain, plainColor), hist("TD critic", td, mainColor)],
                readout: "sd \(f3(plain.sd)) → \(f3(td.sd))"),
        PgFrame(status: "That trade is the whole design: the return is unbiased and noisy, the critic's estimate is smooth and wrong-until-trained. Actor-critic accepts bias to buy variance — and the actor's updates now depend on how good the critic is.",
                bars: [FrameBars(label: "standard deviation of the same gradient component", values: [plain.sd, baseline.sd, td.sd], color: mainColor, captions: ["return", "baseline", "critic"])]),
    ]
}

// MARK: - A2C / A3C

private func a2cFrames() -> [PgFrame] {
    let single = spreadOf(.baseline, seed: 5, workers: 1)
    let eight = spreadOf(.baseline, seed: 5, workers: 8)
    let sixteen = spreadOf(.baseline, seed: 5, workers: 16)
    return [
        PgFrame(status: "A2C is advantage actor-critic run synchronously across parallel environments: every actor collects a rollout, and one update is computed from all of them together.",
                bars: [hist("1 actor", single, plainColor)], readout: "sd \(f3(single.sd))"),
        PgFrame(status: "Averaging 8 independent rollouts cuts the spread from \(f3(single.sd)) to \(f3(eight.sd)) — close to the √n the theory promises, since the rollouts are independent.",
                bars: [hist("1 actor", single, plainColor), hist("8 actors", eight, betterColor)],
                readout: "√8 predicts \(f3(single.sd / Float(8).squareRoot())), measured \(f3(eight.sd))"),
        PgFrame(status: "Doubling again to 16 buys progressively less: variance falls with √n, so the returns diminish exactly as fast as the compute grows.",
                bars: [FrameBars(label: "gradient sd by actor count", values: [single.sd, eight.sd, sixteen.sd], color: mainColor, captions: ["1", "8", "16"])],
                readout: "sd \(f3(single.sd)) → \(f3(eight.sd)) → \(f3(sixteen.sd))"),
        PgFrame(status: "The other benefit does not show up in a single number: parallel actors are in different parts of the environment at any moment, so a batch is decorrelated by construction — the same problem a replay buffer solves for off-policy methods, solved here without storing anything.",
                bars: [hist("16 actors", sixteen, betterColor)]),
    ]
}

private func a3cFrames() -> [PgFrame] {
    let single = spreadOf(.baseline, seed: 5, workers: 1)
    let eight = spreadOf(.baseline, seed: 5, workers: 8)
    let stale = spreadOf(.baseline, seed: 71, workers: 8)
    return [
        PgFrame(status: "A3C came first and is the asynchronous version: each worker pulls the shared parameters, runs its own rollout, and pushes its gradient back whenever it is ready — no waiting for the slowest actor.",
                bars: [hist("1 worker", single, plainColor), hist("8 workers", eight, betterColor)],
                readout: "sd \(f3(single.sd)) → \(f3(eight.sd))"),
        PgFrame(status: "The variance reduction is the same as A2C's — it comes from averaging independent rollouts, not from the asynchrony. What asynchrony adds is throughput on machines where actors finish at different times.",
                bars: [FrameBars(label: "gradient sd", values: [single.sd, eight.sd], color: mainColor, captions: ["1 worker", "8 workers"])]),
        PgFrame(status: "It also adds a cost: a worker computes its gradient from parameters that may already be out of date by the time the update lands. Gradients arrive slightly wrong, in a way that grows with the number of workers.",
                bars: [hist("synchronous batch", eight, betterColor), hist("stale gradients", stale, riskColor)]),
        PgFrame(status: "That is why A2C — the synchronous version — became the default: on GPUs, batching all actors into one forward pass is faster than running them asynchronously, and the gradients are never stale. Same algorithm, better hardware fit.",
                bars: [FrameBars(label: "gradient sd", values: [single.sd, eight.sd, stale.sd], color: mainColor, captions: ["1", "8 sync", "8 stale"])]),
    ]
}

// MARK: - GAE

private func gaeFrames() -> [PgFrame] {
    let lambdas: [Float] = [0, 0.5, 0.9, 0.95, 1]
    let spreads = lambdas.map { spreadOf(.gae, seed: 13, lambda: $0) }
    let monteCarlo = spreadOf(.ret, seed: 13)
    let lambdaCaps = lambdas.map { "λ=\($0)" }
    let bestLambda = lambdas[spreads.indices.min { spreads[$0].sd < spreads[$1].sd }!]
    let sdBars = FrameBars(label: "gradient sd by λ", values: spreads.map(\.sd), color: mainColor, captions: lambdaCaps)
    return [
        PgFrame(status: "The two ends of the advantage spectrum: the sampled return (unbiased, sd \(f3(monteCarlo.sd))) and the one-step TD error (biased by the critic's errors, but far quieter at sd \(f3(spreads[0].sd))).",
                bars: [hist("λ = 0 (one-step TD)", spreads[0], mainColor), hist("Monte-Carlo return", monteCarlo, plainColor)],
                readout: "sd \(f3(spreads[0].sd)) vs \(f3(monteCarlo.sd))"),
        PgFrame(status: "GAE interpolates with one knob: an exponentially weighted average of every n-step advantage, with λ setting the decay. λ = 0 is the one-step TD error, λ = 1 is the full Monte-Carlo advantage. Measured here: \(spreads.map { f2($0.sd) }.joined(separator: " → ")).",
                bars: [sdBars], readout: "lowest spread at λ = \(bestLambda)"),
        PgFrame(status: "Note that the minimum is not at λ = 0. The textbook picture — variance rising monotonically with λ — assumes an accurate critic; this one is a Monte-Carlo estimate with errors of its own, and at λ = 0 every bit of that error goes straight into the gradient. Between λ = \(lambdas[1]) and 1 the trend is monotone as expected.",
                bars: [sdBars, FrameBars(label: "gradient mean by λ", values: spreads.map(\.mean), color: deepColor, captions: lambdaCaps)],
                readout: "mean drifts \(f3(spreads.first!.mean)) → \(f3(spreads.last!.mean)) as λ → 1"),
        PgFrame(status: "The mean drift is the bias half of the trade: at low λ the estimate leans on the critic, so a critic that is wrong pulls the gradient with it no matter how many rollouts are averaged. λ ≈ 0.95 is the common default because it keeps most of the variance reduction while depending on the critic only weakly — a hedge against exactly the critic error visible in the previous frame.",
                plot: FramePlot(label: "gradient sd across λ", curves: [FrameCurve(label: "sd", values: spreads.map(\.sd), color: mainColor)],
                                yRange: 0...(spreads.map(\.sd).max()! * 1.2), xLabel: "λ = 0 · 0.5 · 0.9 · 0.95 · 1"),
                readout: "λ = 0.95 → sd \(f3(spreads[3].sd)), λ = 1 → \(f3(spreads[4].sd))"),
    ]
}

// MARK: - Trust region: TRPO and PPO

private struct StepResult { let returns: [Float]; let maxKl: Float; let maxRatio: Float }

/// Policy improvement at a step size, optionally clipping the step the way PPO clips the ratio.
private func trustRegionRun(lr: Float, clip: Float?, seed: Int = 29, iterations: Int = 40, batch: Int = 4) -> StepResult {
    var theta = startingTheta()
    var rng = Lcg(seed)
    let values = estimateValues(startingTheta(), seed: seed + 5)
    var curve: [Float] = []
    var maxKl: Float = 0
    var maxRatio: Float = 1
    for _ in 0..<iterations {
        let old = theta
        var gradient = [[Float]](repeating: [0, 0], count: pgStates)
        var meanReturn: Float = 0
        for _ in 0..<batch {
            let episode = rollout(theta, &rng)
            meanReturn += totalReturn(episode) / Float(batch)
            let g = returns(episode)
            for (t, step) in episode.enumerated() {
                // Noisy advantages: a large step multiplies that noise straight into the policy.
                let advantage = g[t] - values[step.state] + 3 * (rng.nextF() - 0.5)
                let p = policy(theta, step.state)
                gradient[step.state][1] += advantage * ((step.action == 1 ? 1 : 0) - p[1]) / Float(batch)
            }
        }
        for s in 0..<pgStates {
            var delta = lr * gradient[s][1]
            if let clip {
                // On a softmax the ratio bound is a bound on the logit step.
                let bound = logf(1 + clip)
                delta = min(max(delta, -bound), bound)
            }
            theta[s][1] += delta
        }
        curve.append(meanReturn)
        for s in 0..<pgGoal {
            let before = policy(old, s)
            let after = policy(theta, s)
            let kl = Float((0...1).reduce(0.0) { $0 + Double(before[$1] * logf((before[$1] + 1e-8) / (after[$1] + 1e-8))) })
            maxKl = max(maxKl, kl)
            maxRatio = max(maxRatio, max(after[1] / (before[1] + 1e-8), after[0] / (before[0] + 1e-8)))
        }
    }
    return StepResult(returns: curve, maxKl: maxKl, maxRatio: maxRatio)
}

private func returnPlot(_ curves: [FrameCurve]) -> FramePlot {
    FramePlot(label: "mean return", curves: curves, yRange: -0.4...1, xLabel: "update")
}

private func trpoFrames() -> [PgFrame] {
    let small = trustRegionRun(lr: 0.4, clip: nil)
    let large = trustRegionRun(lr: 20, clip: nil)
    let constrained = trustRegionRun(lr: 20, clip: 0.2)
    return [
        PgFrame(status: "A policy gradient tells you a direction, not a distance. At a modest step size the policy improves steadily: return \(f2(small.returns.first!)) → \(f2(small.returns.last!)), largest KL between consecutive policies \(f3(small.maxKl)).",
                plot: returnPlot([FrameCurve(label: "small steps", values: small.returns, color: mainColor)]),
                readout: "max KL \(f3(small.maxKl))"),
        PgFrame(status: "Push the step size to 20 and the same gradient direction destroys the policy: return ends at \(f2(large.returns.last!)) — the agent never reaches the goal again. The KL between successive policies hits \(f2(large.maxKl)), so a single noisy advantage estimate was enough to move the policy somewhere the batch that justified the step says nothing about.",
                plot: returnPlot([FrameCurve(label: "small steps", values: small.returns, color: mainColor), FrameCurve(label: "large steps", values: large.returns, color: riskColor)]),
                readout: "max KL \(f3(small.maxKl)) → \(f2(large.maxKl))"),
        PgFrame(status: "TRPO's answer is to make that explicit: maximise the surrogate objective subject to a hard KL constraint, so no update can move the policy further than a fixed distance in distribution space — regardless of what the raw gradient magnitude suggests.",
                bars: [FrameBars(label: "largest KL between consecutive policies", values: [small.maxKl, large.maxKl, constrained.maxKl], color: deepColor, captions: ["small lr", "large lr", "constrained"])],
                readout: "same large learning rate, KL held to \(f3(constrained.maxKl))"),
        PgFrame(status: "Constrained, the aggressive learning rate is safe again: return \(f2(constrained.returns.last!)) without the collapse. The price is the machinery — a conjugate-gradient solve and a line search per update, which is exactly what PPO set out to avoid.",
                plot: returnPlot([FrameCurve(label: "large steps", values: large.returns, color: riskColor), FrameCurve(label: "KL-constrained", values: constrained.returns, color: betterColor)])),
    ]
}

private func ppoFrames() -> [PgFrame] {
    let unclipped = trustRegionRun(lr: 20, clip: nil)
    let clipped = trustRegionRun(lr: 20, clip: 0.2)
    let ratios: [Float] = [0.5, 0.8, 1, 1.2, 1.5, 2]
    let advantage: Float = 1
    let objective = ratios.map { min($0 * advantage, min(max($0, 0.8), 1.2) * advantage) }
    let ratioCaps = ratios.map { fx($0, 1) }
    return [
        PgFrame(status: "PPO wants TRPO's guarantee without TRPO's solver. It works with the probability ratio r = π_new(a|s) / π_old(a|s) — how much more likely the updated policy makes an action it already took.",
                bars: [FrameBars(label: "probability ratio r", values: ratios, color: plainColor, captions: ratioCaps)]),
        PgFrame(status: "The clipped objective is min(r·A, clip(r, 0.8, 1.2)·A). For a positive advantage the objective stops improving once r passes 1.2, so there is no gradient left to push the action further — the incentive to take a huge step simply disappears.",
                bars: [
                    FrameBars(label: "unclipped r·A", values: ratios.map { $0 * advantage }, color: plainColor, captions: ratioCaps),
                    FrameBars(label: "clipped objective", values: objective, color: betterColor, captions: ratioCaps),
                ],
                readout: "flat beyond r = 1.2 when A > 0"),
        PgFrame(status: "Same aggressive learning rate as the TRPO lab: unclipped it drives the largest KL to \(f2(unclipped.maxKl)); clipped it stays at \(f3(clipped.maxKl)) and the return ends at \(f2(clipped.returns.last!)) instead of \(f2(unclipped.returns.last!)).",
                plot: returnPlot([FrameCurve(label: "unclipped", values: unclipped.returns, color: riskColor), FrameCurve(label: "PPO-clipped", values: clipped.returns, color: betterColor)]),
                readout: "max KL \(f2(unclipped.maxKl)) → \(f3(clipped.maxKl))"),
        PgFrame(status: "That is the whole algorithm: a clip, a few epochs over the same batch, and first-order optimisation. It is not a bound on anything the way TRPO's constraint is — it just removes the reward for stepping too far, which turned out to be enough, and cheap enough to become the default.",
                bars: [FrameBars(label: "largest KL between consecutive policies", values: [unclipped.maxKl, clipped.maxKl], color: deepColor, captions: ["unclipped", "clipped"])]),
    ]
}

// MARK: - Continuous control: one action a ∈ [−1, 1] with a known reward curve.

private let bestAction: Float = 0.45

private func trueReward(_ action: Float) -> Float { 1 - (action - bestAction) * (action - bestAction) }

private let actionGrid: [Float] = (0...20).map { -1 + 2 * Float($0) / 20 }

private func dpgFrames() -> [PgFrame] {
    var action: Float = -0.8
    var path = [action]
    let lr: Float = 0.25
    for _ in 0..<18 {
        // Follow ∂Q/∂a straight up the critic's surface.
        action += lr * (-2 * (action - bestAction))
        path.append(action)
    }
    let qBars = FrameBars(label: "Q(a) — the critic's surface", values: actionGrid.map(trueReward), color: mainColor)
    return [
        PgFrame(status: "With a continuous action there is no max over actions to take — you cannot enumerate the uncountable. A stochastic policy gradient would sample actions and average; a deterministic one asks the critic which way is uphill.",
                bars: [qBars], readout: "optimum at a = \(f2(bestAction))"),
        PgFrame(status: "DPG's update is the chain rule through the critic: ∇θ J = ∇θ μ(s) · ∂Q/∂a. Starting at a = \(f2(path.first!)), following ∂Q/∂a reaches \(f2(path.last!)) in \(path.count - 1) steps without ever sampling an action.",
                plot: FramePlot(label: "action over updates", curves: [FrameCurve(label: "μ(s)", values: path, color: deepColor)], yRange: -1...1, xLabel: "update"),
                readout: "a \(f2(path.first!)) → \(f2(path.last!)) (optimum \(f2(bestAction)))"),
        PgFrame(status: "Because no action sampling is involved, the gradient has no sampling variance at all — the entire expectation over actions collapses to one evaluation. That is the deterministic policy gradient theorem's payoff.",
                bars: [FrameBars(label: "|a − optimum| over updates", values: path.map { abs($0 - bestAction) }, color: betterColor)]),
        PgFrame(status: "The bill arrives elsewhere: a deterministic policy explores nothing. Off-policy exploration noise has to be added by hand — which is precisely what DDPG bolts on, along with the replay buffer and target networks it inherits from DQN.",
                bars: [FrameBars(label: "Q(a)", values: actionGrid.map(trueReward), color: mainColor)]),
    ]
}

private struct CriticRun { let singleBias: [Float]; let twinBias: [Float] }

/// Critic overestimation: one noisy critic chases its own noise; the min of two mostly does not.
private func criticBiasRun(seed: Int = 61, rounds: Int = 40, samples: Int = 12) -> CriticRun {
    var rng = Lcg(seed)
    var single: [Float] = []
    var twin: [Float] = []
    var singleRunning: Float = 0
    var twinRunning: Float = 0
    for round in 0..<rounds {
        let candidates = (0..<samples).map { -1 + 2 * Float($0) / (Float(samples) - 1) }
        let noiseA = candidates.map { _ in rng.normal() * 0.15 }
        let noiseB = candidates.map { _ in rng.normal() * 0.15 }
        let singlePick = argmaxFirst(candidates.indices.map { trueReward(candidates[$0]) + noiseA[$0] })
        let twinPick = argmaxFirst(candidates.indices.map { min(trueReward(candidates[$0]) + noiseA[$0], trueReward(candidates[$0]) + noiseB[$0]) })
        let singleEstimate = trueReward(candidates[singlePick]) + noiseA[singlePick]
        let twinEstimate = min(trueReward(candidates[twinPick]) + noiseA[twinPick], trueReward(candidates[twinPick]) + noiseB[twinPick])
        singleRunning += (singleEstimate - trueReward(candidates[singlePick]) - singleRunning) / Float(round + 1)
        twinRunning += (twinEstimate - trueReward(candidates[twinPick]) - twinRunning) / Float(round + 1)
        single.append(singleRunning)
        twin.append(twinRunning)
    }
    return CriticRun(singleBias: single, twinBias: twin)
}

private func biasPlot(_ curves: [FrameCurve]) -> FramePlot {
    FramePlot(label: "critic bias at the chosen action", curves: curves, yRange: -0.1...0.35, xLabel: "round")
}

private func ddpgFrames() -> [PgFrame] {
    let bias = criticBiasRun()
    return [
        PgFrame(status: "DDPG is DPG plus the DQN machinery: a replay buffer, target networks for both actor and critic, and exploration noise added to the deterministic action.",
                bars: [FrameBars(label: "Q(a)", values: actionGrid.map(trueReward), color: mainColor)]),
        PgFrame(status: "Off-policy is the point — the replay buffer holds transitions from every past policy, so a continuous-control agent can reuse experience instead of throwing each batch away after one update the way an on-policy method does.",
                bars: [FrameBars(label: "exploration: μ(s) + noise", values: actionGrid.map(trueReward), color: mainColor)]),
        PgFrame(status: "The known failure: the actor maximises the critic, so any state-action pair the critic happens to overrate becomes the policy's target. Averaged over \(bias.singleBias.count) rounds the critic's value at the chosen action sits \(f3(bias.singleBias.last!)) above the truth — pure noise, promoted by the max.",
                plot: biasPlot([FrameCurve(label: "single critic", values: bias.singleBias, color: riskColor)]),
                readout: "mean overestimate \(f3(bias.singleBias.last!))"),
        PgFrame(status: "That is the loop TD3 breaks. DDPG's reputation for brittleness is mostly this: the actor is an optimiser pointed straight at the critic's errors, and nothing in the algorithm damps it.",
                bars: [FrameBars(label: "critic bias", values: [bias.singleBias.last!, bias.twinBias.last!], color: deepColor, captions: ["DDPG", "twin critics"])]),
    ]
}

private func td3Frames() -> [PgFrame] {
    let bias = criticBiasRun()
    let s = bias.singleBias.last!, t = bias.twinBias.last!
    return [
        PgFrame(status: "TD3 is three fixes to DDPG, and the first is the important one. A single critic's value at the action the actor picked is biased upward by \(f3(s)) — the actor is selecting for the critic's own noise.",
                plot: biasPlot([FrameCurve(label: "single critic", values: bias.singleBias, color: riskColor)]),
                readout: "single-critic bias \(f3(s))"),
        PgFrame(status: "Train two critics and use the smaller of the two values as the target. Noise that inflates one critic rarely inflates the other at the same action, so the minimum lands near the truth: bias \(f3(t)).",
                plot: biasPlot([FrameCurve(label: "single critic", values: bias.singleBias, color: riskColor), FrameCurve(label: "min of twin critics", values: bias.twinBias, color: betterColor)]),
                readout: "\(f3(s)) → \(f3(t))"),
        PgFrame(status: "Fix two — delayed policy updates: update the actor once per two critic updates, so the policy chases a critic that has had time to settle. Fix three — target policy smoothing: add noise to the target action, which stops the critic from developing a sharp spike the actor can exploit.",
                bars: [FrameBars(label: "critic bias", values: [s, t], color: deepColor, captions: ["DDPG", "TD3"])]),
        PgFrame(status: "Note the deliberate underestimation: the minimum of two critics is biased low, and TD3 accepts that. An underestimated action simply does not get chosen; an overestimated one becomes the policy.",
                bars: [FrameBars(label: "sign of the bias", values: [s, t], color: mainColor, captions: ["over", "slightly under"])]),
    ]
}

private struct EntropyPoint { let temperature: Float; let sigma: Float; let reward: Float; let entropy: Float }

private func gaussianEntropy(_ sigma: Float) -> Float { 0.5 * logf(2 * Float.pi * expf(1) * sigma * sigma) }

/// For N(μ, σ) on the quadratic reward the objective is closed form, so sweep σ per temperature.
private func entropySweep() -> [EntropyPoint] {
    let sigmas = (1...40).map { Float($0) * 0.02 }
    return ([0, 0.02, 0.05, 0.1, 0.2] as [Float]).map { alpha in
        let best = sigmas[argmaxFirst(sigmas.map { 1 - $0 * $0 + alpha * gaussianEntropy($0) })]
        return EntropyPoint(temperature: alpha, sigma: best, reward: 1 - best * best, entropy: gaussianEntropy(best))
    }
}

private func sacFrames() -> [PgFrame] {
    let sweep = entropySweep()
    let caps = sweep.map { "α=\($0.temperature)" }
    return [
        PgFrame(status: "SAC optimises reward plus α times the policy's entropy. With a Gaussian policy on this reward the trade is exactly solvable: expected reward falls as σ², entropy grows as ln σ.",
                bars: [FrameBars(label: "chosen σ by temperature α", values: sweep.map(\.sigma), color: mainColor, captions: caps)]),
        PgFrame(status: "At α = 0 the optimum is a deterministic policy (σ → 0, reward \(f2(sweep.first!.reward))). Raise α and the optimal policy widens: at α = \(sweep.last!.temperature) it settles at σ = \(f2(sweep.last!.sigma)), paying \(f2(sweep.first!.reward - sweep.last!.reward)) of reward for the extra randomness.",
                bars: [
                    FrameBars(label: "expected reward", values: sweep.map(\.reward), color: betterColor, captions: caps),
                    FrameBars(label: "policy entropy", values: sweep.map(\.entropy), color: deepColor, captions: caps),
                ],
                readout: "α = 0 → σ \(f2(sweep.first!.sigma)) · α = \(sweep.last!.temperature) → σ \(f2(sweep.last!.sigma))"),
        PgFrame(status: "That width is not wasted: it is exploration the objective itself asks for, rather than noise bolted on afterwards the way DDPG does it. The policy stays stochastic exactly as long as being stochastic is worth its cost.",
                plot: FramePlot(label: "σ chosen as α rises", curves: [FrameCurve(label: "σ*", values: sweep.map(\.sigma), color: mainColor)], yRange: 0...1, xLabel: "α = 0 · 0.02 · 0.05 · 0.1 · 0.2")),
        PgFrame(status: "SAC combines that objective with TD3's twin critics and off-policy replay, and tunes α automatically against a target entropy — which is most of why it needs so little per-environment tuning compared to DDPG.",
                bars: [FrameBars(label: "σ", values: sweep.map(\.sigma), color: mainColor, captions: caps)]),
    ]
}

private func maxEntropyFrames() -> [PgFrame] {
    let sweep = entropySweep()
    let qValues: [Float] = [1.0, 0.9, 0.4, 0.1]
    func boltzmann(_ alpha: Float) -> [Float] {
        let scaled = qValues.map { $0 / alpha }
        let m = scaled.max()!
        let exps = scaled.map { expf($0 - m) }
        let sum = exps.reduce(0, +)
        return exps.map { $0 / sum }
    }
    let aCaps = ["a1", "a2", "a3", "a4"]
    let p05 = boltzmann(0.05)
    return [
        PgFrame(status: "Standard RL maximises expected return, and its optimal policy is deterministic — one best action per state, everything else discarded. Maximum-entropy RL maximises return plus α·H(π) instead.",
                bars: [FrameBars(label: "Q(a) for four actions", values: qValues, color: mainColor, captions: aCaps)]),
        PgFrame(status: "The optimal policy for that objective is not greedy but Boltzmann: π(a) ∝ exp(Q(a)/α). At α = 0.05 the two near-tied actions still split \(fx(p05[0] * 100, 0))/\(fx(p05[1] * 100, 0)) instead of winner-take-all.",
                bars: [
                    FrameBars(label: "π(a) at α = 0.05", values: p05, color: betterColor, captions: aCaps),
                    FrameBars(label: "π(a) at α = 0.5", values: boltzmann(0.5), color: deepColor, captions: aCaps),
                ],
                readout: "α → 0 recovers the greedy policy"),
        PgFrame(status: "Keeping the near-tie alive is the point. A policy that commits to a 1.0-vs-0.9 difference is betting that its own value estimates are right to a tenth; the entropy term keeps that bet hedged until the evidence separates them.",
                bars: [
                    FrameBars(label: "π(a) at α = 0.02", values: boltzmann(0.02), color: plainColor, captions: aCaps),
                    FrameBars(label: "π(a) at α = 0.2", values: boltzmann(0.2), color: mainColor, captions: aCaps),
                ]),
        PgFrame(status: "α is the exchange rate between reward and randomness, and it is a real cost: on the continuous task in the SAC lab, α = \(sweep.last!.temperature) gives up \(f2(sweep.first!.reward - sweep.last!.reward)) of expected reward. The return is robustness — policies that keep alternatives alive degrade more gracefully when the environment shifts.",
                bars: [FrameBars(label: "reward given up", values: sweep.map { sweep.first!.reward - $0.reward }, color: riskColor, captions: sweep.map { "α=\($0.temperature)" })]),
    ]
}

// MARK: - Config

private let varianceLegend: [(Color, String)] = [(plainColor, "Baseline"), (betterColor, "Improved"), (riskColor, "Failure mode")]

private let pgConfigs: [String: PgConfig] = [
    "reinforce": PgConfig(intro: "The vanilla policy gradient, run for real — then 300 independent estimates of the same gradient component, to show what the algorithm is actually working with.",
                          legend: varianceLegend, build: reinforceFrames),
    "actor_critic": PgConfig(intro: "The same gradient component under three estimators: raw return, return minus a baseline, and a TD critic. The spread is measured, not asserted.",
                             legend: varianceLegend, build: actorCriticFrames),
    "a2c": PgConfig(intro: "What parallel actors buy: the same estimate averaged over 1, 8 and 16 independent rollouts, against the √n the theory predicts.",
                    legend: varianceLegend, build: a2cFrames),
    "a3c": PgConfig(intro: "A3C's asynchrony against A2C's batching — where the variance reduction actually comes from, and what stale gradients cost.",
                    legend: varianceLegend, build: a3cFrames),
    "gae": PgConfig(intro: "λ swept from 0 to 1, with the gradient's standard deviation and mean measured at each setting — the bias/variance trade in two numbers.",
                    legend: varianceLegend, build: gaeFrames),
    "trpo": PgConfig(intro: "The same gradient direction at a safe and a reckless step size, with the KL divergence between consecutive policies measured at each update.",
                     legend: varianceLegend, build: trpoFrames),
    "ppo": PgConfig(intro: "The clipped objective drawn as a function of the probability ratio, then the same aggressive step size with and without it.",
                    legend: varianceLegend, build: ppoFrames),
    "dpg": PgConfig(intro: "One continuous action, a known reward curve, and a policy that climbs ∂Q/∂a instead of sampling.",
                    legend: [(mainColor, "Critic Q(a)"), (deepColor, "Policy μ(s)"), (betterColor, "Distance to optimum")], build: dpgFrames),
    "ddpg": PgConfig(intro: "DPG plus replay and target networks — and the measured failure mode that follows from an actor pointed straight at a critic's errors.",
                     legend: varianceLegend, build: ddpgFrames),
    "td3": PgConfig(intro: "Critic overestimation measured over 40 rounds, with a single critic and with the minimum of two.",
                    legend: varianceLegend, build: td3Frames),
    "sac": PgConfig(intro: "The entropy-regularised objective solved exactly on a Gaussian policy: the optimal σ for each temperature, and what it costs in reward.",
                    legend: [(mainColor, "σ"), (betterColor, "Reward"), (deepColor, "Entropy")], build: sacFrames),
    "max_entropy_rl": PgConfig(intro: "Why the optimal max-entropy policy is Boltzmann rather than greedy, and what each temperature costs in expected reward.",
                               legend: [(mainColor, "Policy"), (deepColor, "High α"), (riskColor, "Reward given up")], build: maxEntropyFrames),
]

// MARK: - UI

struct PolicyGradientLab: View {
    private let config: PgConfig
    private let key: String
    @Environment(\.palette) private var palette

    init(topicId: String) {
        key = pgConfigs[topicId] == nil ? "reinforce" : topicId
        config = pgConfigs[key]!
    }

    var body: some View {
        let key = self.key
        AsyncFrameLab(key: "policygradient:\(key)", speedMs: 1100, build: { pgConfigs[key]!.build() }) { frames, playback in
            content(frames, playback)
        }
    }

    @ViewBuilder
    private func content(_ frames: [PgFrame], _ playback: PlaybackState) -> some View {
        let frame = frames[min(playback.index, frames.count - 1)]
        LabCard {
            LabIntro(text: config.intro)
            if let plot = frame.plot { FramePlotView(plot: plot).padding(.top, 12) }
            ForEach(frame.bars.indices, id: \.self) { FrameBarsView(bar: frame.bars[$0], negativeColor: riskColor).padding(.top, 12) }
            FramePlayerFooter(readout: frame.readout, status: frame.status, legend: config.legend, playback: playback, captions: frames.map(\.status))
        }
    }
}
