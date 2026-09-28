import SwiftUI

// Port of OfflineRlSection.kt: learning from a fixed dataset or from demonstrations. Fitted
// Q-iteration over a logged dataset, behaviour cloning and DAgger on a slippery corridor, occupancy
// matching against a discriminator, max-entropy IRL, and a Bradley-Terry reward model fitted to
// preferences labelled on what a rater can see. Three shared environments:
//   A. a six-state chain with a mean-zero lottery action (offline_rl, cql)
//   B. a 3×8 corridor where every move slips a row (imitation_learning, gail, irl)
//   C. the chain with a per-step cost (decision_transformer, rlhf)

private struct OffCell { let shade: Float; let glyph: String; let color: Color }
private struct OffGrid { let label: String; let cells: [OffCell] }

private struct OffTable {
    let label: String
    let columnLabels: [String]
    let rowLabels: [String]
    let rows: [[String]]
    var highlight: Set<Int> = [] // row * columns + column
}

private struct OffFrame {
    let status: String
    var plot: FramePlot?
    var bars: [FrameBars] = []
    var grid: OffGrid?
    var table: OffTable?
    var readout: String?
}

private struct OffConfig { let intro: String; let legend: [(Color, String)]; let build: () -> [OffFrame] }

private let baselineColor = SimColors.grey
private let improvedColor = SimColors.green
private let warnColor = Color(hex: 0xEC4899)
private let accentColor = SimColors.blue
private let highlightColor = Color(hex: 0x7C3AED)

private func fmt(_ x: Double, _ digits: Int = 3) -> String { fx(x, digits) }
private func floats(_ xs: [Double]) -> [Float] { xs.map { Float($0) } }

// MARK: - Environment A: the chain with a lottery action

private let chN = 6
private let chGoal = 5
private let chNA = 3
private let chGamma = 0.95

private func chStep(_ s: Int, _ a: Int, _ rng: inout Lcg) -> (Double, Int, Bool) {
    switch a {
    case 0: return (0, max(s - 1, 0), false)
    case 1:
        let ns = min(s + 1, chGoal)
        return (ns == chGoal ? 1 : 0, ns, ns == chGoal)
    default: return (rng.next() < 0.1 ? 0.9 : -0.1, s, false)
    }
}

/// Exact action values, by value iteration on the known dynamics.
private let chTrueQ: [[Double]] = {
    var q = [[Double]](repeating: [0, 0, 0], count: chN)
    for _ in 0..<600 {
        var nq = [[Double]](repeating: [0, 0, 0], count: chN)
        for s in 0..<chGoal {
            nq[s][0] = chGamma * q[max(s - 1, 0)].max()!
            let ns = min(s + 1, chGoal)
            nq[s][1] = ns == chGoal ? 1 : chGamma * q[ns].max()!
            nq[s][2] = chGamma * q[s].max()!
        }
        q = nq
    }
    return q
}()

private struct Trans { let s: Int; let a: Int; let r: Double; let ns: Int; let done: Bool }

/// Behaviour policy: mostly right, `eps` dithering, `pGamble` lottery pulls, capped reach.
private func collect(seed: Int, episodes: Int, eps: Double, pGamble: Double = 0.08, cap: Int = -1) -> [Trans] {
    var rng = Lcg(seed)
    var out: [Trans] = []
    for _ in 0..<episodes {
        var s = 0
        for _ in 0..<25 {
            var a = rng.next() < pGamble ? 2 : (rng.next() < eps ? rng.nextInt(2) : 1)
            if cap >= 0 && s >= cap && a == 1 { a = 0 }
            let (r, ns, d) = chStep(s, a, &rng)
            out.append(Trans(s: s, a: a, r: r, ns: ns, done: d))
            s = ns
            if d { break }
        }
    }
    return out
}

/// A left-biased logger: right is present, but it is the minority action.
private func collectLeftBiased(_ seed: Int, _ episodes: Int, _ pRight: Double) -> [Trans] {
    var rng = Lcg(seed)
    var out: [Trans] = []
    for _ in 0..<episodes {
        var s = 0
        for _ in 0..<25 {
            let a = rng.next() < 0.08 ? 2 : (rng.next() < pRight ? 1 : 0)
            let (r, ns, d) = chStep(s, a, &rng)
            out.append(Trans(s: s, a: a, r: r, ns: ns, done: d))
            s = ns
            if d { break }
        }
    }
    return out
}

/// Fitted Q-iteration over a fixed dataset, with CQL's conservative penalty when `cqlBeta` > 0.
private func fqi(_ data: [Trans], cqlBeta: Double = 0, sweeps: Int = 300, alpha: Double = 0.05) -> [[Double]] {
    var q = [[Double]](repeating: [0, 0, 0], count: chN)
    for _ in 0..<sweeps {
        for t in data {
            let target = t.r + (t.done ? 0 : chGamma * q[t.ns].max()!)
            q[t.s][t.a] += alpha * (target - q[t.s][t.a])
            if cqlBeta > 0 {
                let m = q[t.s].max()!
                let e = q[t.s].map { exp($0 - m) }
                let z = e.reduce(0, +)
                for b in 0..<chNA { q[t.s][b] -= alpha * cqlBeta * (e[b] / z) }
                q[t.s][t.a] += alpha * cqlBeta
            }
        }
    }
    return q
}

private func greedyAction(_ q: [[Double]], _ s: Int) -> Int {
    var best = 0
    for a in 1..<chNA where q[s][a] > q[s][best] { best = a }
    return best
}

/// The same algorithm, allowed to keep interacting.
private func onlineQ(_ seed: Int, _ transitions: Int) -> [[Double]] {
    var rng = Lcg(seed)
    var q = [[Double]](repeating: [0, 0, 0], count: chN)
    var s = 0
    for _ in 0..<transitions {
        let a = rng.next() < 0.3 ? rng.nextInt(chNA) : greedyAction(q, s)
        let (r, ns, d) = chStep(s, a, &rng)
        q[s][a] += 0.1 * (r + (d ? 0 : chGamma * q[ns].max()!) - q[s][a])
        s = d ? 0 : ns
    }
    return q
}

/// Deployed discounted return of the greedy policy, averaged over runs.
private func chReturn(_ q: [[Double]], runs: Int = 400) -> Double {
    var total = 0.0
    for i in 0..<runs {
        var rng = Lcg(9000 + i * 7)
        var s = 0
        var g = 1.0
        for _ in 0..<60 {
            let (r, ns, d) = chStep(s, greedyAction(q, s), &rng)
            total += g * r
            g *= chGamma
            s = ns
            if d { break }
        }
    }
    return total / Double(runs)
}

private func behaviourReturn(_ eps: Double, _ pGamble: Double = 0.08, runs: Int = 400) -> Double {
    var total = 0.0
    for i in 0..<runs {
        var rng = Lcg(4100 + i * 13)
        var s = 0
        var g = 1.0
        for _ in 0..<60 {
            let a = rng.next() < pGamble ? 2 : (rng.next() < eps ? rng.nextInt(2) : 1)
            let (r, ns, d) = chStep(s, a, &rng)
            total += g * r
            g *= chGamma
            s = ns
            if d { break }
        }
    }
    return total / Double(runs)
}

private func counts(_ data: [Trans]) -> [[Int]] {
    var c = [[Int]](repeating: [0, 0, 0], count: chN)
    for t in data { c[t.s][t.a] += 1 }
    return c
}

private let chActionNames = ["left", "right", "gamble"]

// MARK: - Environment B: a 3×8 corridor where every move slips a row with probability 0.25

private let rows = 3
private let cols = 8
private let mid = 1
private let hor = 11
private let slip = 0.25
private let lnNA = 3 // 0 = right, 1 = up, 2 = down

private func cell(_ r: Int, _ c: Int) -> Int { r * cols + c }
private func rowOf(_ s: Int) -> Int { s / cols }
private func colOf(_ s: Int) -> Int { s % cols }
private let lnGoal = cell(mid, cols - 1)
private let lnStart = cell(mid, 0)

private func detMove(_ s: Int, _ a: Int) -> Int {
    var r = rowOf(s)
    var c = colOf(s)
    switch a {
    case 0: c = min(c + 1, cols - 1)
    case 1: r = max(r - 1, 0)
    default: r = min(r + 1, rows - 1)
    }
    return cell(r, c)
}

/// Slip-aware transitions as (next, probability), in Java HashMap order (bucket = key & 15, then
/// insertion) — the samplers walk this list cumulatively, so the order has to match Android's.
private func lnTrans(_ s: Int, _ a: Int, wall: Int = -1) -> [(Int, Double)] {
    let base = detMove(s, a)
    var keys: [Int] = []
    var probs: [Int: Double] = [:]
    for (dr, p) in [(0, 1 - slip), (-1, slip / 2), (1, slip / 2)] {
        let r = min(max(rowOf(base) + dr, 0), rows - 1)
        var ns = cell(r, colOf(base))
        if ns == wall { ns = s }
        if probs[ns] == nil { keys.append(ns) }
        probs[ns, default: 0] += p
    }
    let ordered = keys.enumerated().sorted { x, y in
        (x.element & 15) == (y.element & 15) ? x.offset < y.offset : (x.element & 15) < (y.element & 15)
    }.map(\.element)
    return ordered.map { ($0, probs[$0]!) }
}

private func expertAction(_ s: Int) -> Int { rowOf(s) < mid ? 2 : rowOf(s) > mid ? 1 : 0 }

private let temp = 0.15
private let lnGamma = 0.98
private let stepCost = 0.25

private typealias LanePolicy = [[Double]]

private let uniformLane = LanePolicy(repeating: [1.0 / 3, 1.0 / 3, 1.0 / 3], count: rows * cols)

/// Max-entropy soft value iteration; the goal is terminal and every step costs.
private func softVi(_ reward: (Int, Int) -> Double, wall: Int = -1, sweeps: Int = 80) -> LanePolicy {
    var v = [Double](repeating: 0, count: rows * cols)
    var pol = uniformLane
    for _ in 0..<sweeps {
        var nv = [Double](repeating: 0, count: rows * cols)
        var np = uniformLane
        for s in 0..<(rows * cols) where s != lnGoal {
            let q = (0..<lnNA).map { a in reward(s, a) + lnGamma * lnTrans(s, a, wall: wall).reduce(0.0) { $0 + $1.1 * v[$1.0] } }
            let m = q.max()!
            let e = q.map { exp(($0 - m) / temp) }
            let z = e.reduce(0, +)
            nv[s] = m + temp * log(z)
            np[s] = e.map { $0 / z }
        }
        v = nv
        pol = np
    }
    return pol
}

private func stateReward(_ rs: @escaping (Int) -> Double, wall: Int = -1) -> (Int, Int) -> Double {
    { s, a in lnTrans(s, a, wall: wall).reduce(0.0) { $0 + $1.1 * rs($1.0) } - stepCost }
}

/// Expected state-action visitation over the horizon, normalised to sum to 1.
private func occupancy(_ pol: LanePolicy, wall: Int = -1) -> [Int: Double] {
    var d = [Double](repeating: 0, count: rows * cols)
    d[lnStart] = 1
    var occ: [Int: Double] = [:]
    for _ in 0..<hor {
        var nd = [Double](repeating: 0, count: rows * cols)
        for s in 0..<(rows * cols) {
            let ps = d[s]
            if ps <= 0 || s == lnGoal { continue }
            for a in 0..<lnNA {
                occ[s * lnNA + a, default: 0] += ps * pol[s][a]
                for (ns, p) in lnTrans(s, a, wall: wall) { nd[ns] += ps * pol[s][a] * p }
            }
        }
        d = nd
    }
    let total = occ.values.reduce(0, +)
    return occ.mapValues { $0 / total }
}

private func tvDistance(_ p: [Int: Double], _ q: [Int: Double]) -> Double {
    0.5 * Set(p.keys).union(q.keys).reduce(0.0) { $0 + abs((p[$1] ?? 0) - (q[$1] ?? 0)) }
}

/// Per-state visitation, marginalising the action out — what IRL matches.
private func stateVisits(_ occ: [Int: Double], wall: Int = -1) -> [Double] {
    var feat = [Double](repeating: 0, count: rows * cols)
    for (k, v) in occ { for (ns, p) in lnTrans(k / lnNA, k % lnNA, wall: wall) { feat[ns] += v * p } }
    return feat
}

/// One sampled step: an action from the policy, then a slip-aware transition.
private func laneStep(_ pol: LanePolicy, _ s: Int, _ rng: inout Lcg, wall: Int = -1) -> Int {
    var u = rng.next()
    var a = lnNA - 1
    var acc = 0.0
    for j in 0..<lnNA {
        acc += pol[s][j]
        if u <= acc { a = j; break }
    }
    u = rng.next()
    acc = 0
    for (ns, p) in lnTrans(s, a, wall: wall) {
        acc += p
        if u <= acc { return ns }
    }
    return s
}

private func laneSuccess(_ pol: LanePolicy, runs: Int = 800, wall: Int = -1, hor h: Int = hor) -> Double {
    var ok = 0
    for i in 0..<runs {
        var rng = Lcg(3000 + i * 11)
        var s = lnStart
        for _ in 0..<h {
            s = laneStep(pol, s, &rng, wall: wall)
            if s == lnGoal { ok += 1; break }
        }
    }
    return Double(ok) / Double(runs)
}

private struct DriftStats { let success: Double; let drifted: Double; let successGivenDrift: Double }

private func driftStats(_ pol: LanePolicy, runs: Int = 800) -> DriftStats {
    var ok = 0, drifted = 0, driftOk = 0
    for i in 0..<runs {
        var rng = Lcg(3000 + i * 11)
        var s = lnStart
        var leftRow = false
        var reached = false
        for _ in 0..<hor {
            s = laneStep(pol, s, &rng)
            if rowOf(s) != mid { leftRow = true }
            if s == lnGoal { ok += 1; reached = true; break }
        }
        if leftRow {
            drifted += 1
            if reached { driftOk += 1 }
        }
    }
    return DriftStats(success: Double(ok) / Double(runs), drifted: Double(drifted) / Double(runs),
                      successGivenDrift: drifted == 0 ? 0 : Double(driftOk) / Double(drifted))
}

private func deterministicPolicy(_ action: (Int) -> Int) -> LanePolicy {
    (0..<(rows * cols)).map { s in (0..<lnNA).map { $0 == action(s) ? 1.0 : 0.0 } }
}

private let expertPolicy = deterministicPolicy(expertAction)

/// Clean, slip-free expert demonstrations — the only states a cloner ever sees.
private func laneDemos(_ n: Int) -> [(Int, Int)] {
    var out: [(Int, Int)] = []
    for _ in 0..<n {
        var s = lnStart
        for _ in 0..<hor {
            out.append((s, expertAction(s)))
            s = detMove(s, expertAction(s))
            if s == lnGoal { break }
        }
    }
    return out
}

private struct BcPolicy { let policy: LanePolicy; let covered: Set<Int>; let fallback: Int }

/// Behaviour cloning; unseen states get the action that dominates the dataset overall.
private func behaviourClone(_ pairs: [(Int, Int)]) -> BcPolicy {
    var cnt: [Int: [Int]] = [:]
    var total = [0, 0, 0]
    for (s, a) in pairs {
        cnt[s, default: [0, 0, 0]][a] += 1
        total[a] += 1
    }
    let fallback = argmaxFirst(total)
    let pol: LanePolicy = (0..<(rows * cols)).map { s in
        let a = cnt[s].map(argmaxFirst) ?? fallback
        return (0..<lnNA).map { $0 == a ? 1.0 : 0.0 }
    }
    return BcPolicy(policy: pol, covered: Set(cnt.keys), fallback: fallback)
}

// MARK: - Environment C: the chain with a per-step cost

private let dtCost = 0.05

private func dtNext(_ s: Int, _ a: Int) -> Int { a == 1 ? min(s + 1, chGoal) : max(s - 1, 0) }

private struct DtTraj { let steps: [(Int, Int, Double)]; let ret: Double }

private func dtTrajectory(_ pRight: Double, _ rng: inout Lcg) -> DtTraj {
    var s = 0
    var steps: [(Int, Int, Double)] = []
    var ret = 0.0
    for _ in 0..<25 {
        let a = rng.next() < pRight ? 1 : 0
        let ns = dtNext(s, a)
        let r = (ns == chGoal ? 1.0 : 0.0) - dtCost
        steps.append((s, a, r))
        ret += r
        s = ns
        if ns == chGoal { break }
    }
    return DtTraj(steps: steps, ret: ret)
}

private let dtBuckets = [-0.4, -0.1, 0.15, 0.35, 0.55, 0.68]

private func dtBucket(_ r: Double) -> Int { dtBuckets.firstIndex { r <= $0 } ?? dtBuckets.count }

private struct DtDataset { let trajs: [DtTraj]; let conditioned: [Int: [Int]]; let cloned: [Int: [Int]] }

/// A deliberately mixed dataset: mostly poor trajectories, a few good ones.
private func dtDataset(_ seed: Int) -> DtDataset {
    var rng = Lcg(seed)
    var trajs: [DtTraj] = []
    for _ in 0..<150 { trajs.append(dtTrajectory(0.30, &rng)) }
    for _ in 0..<60 { trajs.append(dtTrajectory(0.60, &rng)) }
    for _ in 0..<40 { trajs.append(dtTrajectory(0.95, &rng)) }
    var cond: [Int: [Int]] = [:]
    var clone: [Int: [Int]] = [:]
    for t in trajs {
        var rtg = t.ret
        for (s, a, r) in t.steps {
            cond[s * 16 + dtBucket(rtg), default: [0, 0]][a] += 1
            clone[s, default: [0, 0]][a] += 1
            rtg -= r
        }
    }
    return DtDataset(trajs: trajs, conditioned: cond, cloned: clone)
}

private func dtRollout(_ ds: DtDataset, _ target: Double) -> (Double, Int) {
    var s = 0, rtg = target, got = 0.0, steps = 0
    for _ in 0..<25 {
        let c = ds.conditioned[s * 16 + dtBucket(rtg)]
        let a = c == nil || c![0] + c![1] == 0 ? 1 : (c![0] > c![1] ? 0 : 1)
        let ns = dtNext(s, a)
        let r = (ns == chGoal ? 1.0 : 0.0) - dtCost
        got += r
        rtg -= r
        s = ns
        steps += 1
        if ns == chGoal { break }
    }
    return (got, steps)
}

private func dtCloneRollout(_ ds: DtDataset) -> (Double, Int) {
    var s = 0, got = 0.0, steps = 0
    for _ in 0..<25 {
        let c = ds.cloned[s]
        let a = c == nil ? 1 : (c![0] > c![1] ? 0 : 1)
        let ns = dtNext(s, a)
        got += (ns == chGoal ? 1.0 : 0.0) - dtCost
        s = ns
        steps += 1
        if ns == chGoal { break }
    }
    return (got, steps)
}

// MARK: - RLHF: a "dash" that advances two states and hides a cost no rater sees

private let dashP = 0.35
private let dashCost = 0.8
private let refPolicy = [0.40, 0.45, 0.15] // left / right / dash

private func rlhfNext(_ s: Int, _ a: Int) -> Int {
    switch a {
    case 0: max(s - 1, 0)
    case 1: min(s + 1, chGoal)
    default: min(s + 2, chGoal)
    }
}

private struct PrefSegment { let steps: [(Int, Int, Int)]; let observed: Double; let truth: Double }

private func sampleAction(_ probs: [Double], _ u: Double) -> Int {
    var acc = 0.0
    for j in 0..<3 {
        acc += probs[j]
        if u <= acc { return j }
    }
    return 2
}

private func rlhfRollout(_ pol: [[Double]], _ rng: inout Lcg) -> PrefSegment {
    var s = 0
    var steps: [(Int, Int, Int)] = []
    var obs = 0.0, truth = 0.0
    for _ in 0..<20 {
        let a = sampleAction(pol[s], rng.next())
        let ns = rlhfNext(s, a)
        let o = (ns == chGoal ? 1.0 : 0.0) - dtCost
        obs += o
        truth += o - (a == 2 && rng.next() < dashP ? dashCost : 0)
        steps.append((s, a, ns))
        s = ns
        if ns == chGoal { break }
    }
    return PrefSegment(steps: steps, observed: obs, truth: truth)
}

private func segFeatures(_ seg: PrefSegment) -> [Double] {
    [Double(seg.steps.filter { $0.1 == 2 }.count), Double(seg.steps.filter { $0.1 == 1 }.count),
     seg.steps.last.map { $0.2 == chGoal ? 1.0 : 0.0 } ?? 0]
}

private struct RewardModel { let w: [Double]; let agreement: Double; let segments: [PrefSegment] }

/// Bradley-Terry fit to pairwise comparisons, labelled on the observable return only.
private func fitRewardModel(_ seed: Int) -> RewardModel {
    var rng = Lcg(seed)
    let mixes: [(Double, Double)] = [(0.25, 0.05), (0.5, 0.10), (0.7, 0.15), (0.85, 0.10), (0.6, 0.30)]
    var segs: [PrefSegment] = []
    for _ in 0..<500 {
        let (pr, pd) = mixes[rng.nextInt(mixes.count)]
        let pol = [[Double]](repeating: [max(0, 1 - pr - pd), pr, pd], count: chN)
        segs.append(rlhfRollout(pol, &rng))
    }
    var pairs: [([Double], [Double])] = []
    for _ in 0..<1500 {
        let i = rng.nextInt(segs.count)
        let j = rng.nextInt(segs.count)
        if i == j || segs[i].observed == segs[j].observed { continue }
        var better = segs[i].observed > segs[j].observed ? i : j
        var worse = better == i ? j : i
        if rng.next() < 0.10 { swap(&better, &worse) } // raters are not perfect
        pairs.append((segFeatures(segs[better]), segFeatures(segs[worse])))
    }
    var w = [0.0, 0.0, 0.0]
    for _ in 0..<8000 {
        var g = [0.0, 0.0, 0.0]
        for (fb, fw) in pairs {
            var d = 0.0
            for k in 0..<3 { d += w[k] * (fb[k] - fw[k]) }
            let p = 1 / (1 + exp(-d))
            for k in 0..<3 { g[k] += (1 - p) * (fb[k] - fw[k]) }
        }
        for k in 0..<3 { w[k] += 0.02 * g[k] / Double(pairs.count) }
    }
    var hit = 0, total = 0
    for _ in 0..<3000 {
        let i = rng.nextInt(segs.count)
        let j = rng.nextInt(segs.count)
        if i == j || segs[i].observed == segs[j].observed { continue }
        total += 1
        let fi = segFeatures(segs[i]), fj = segFeatures(segs[j])
        var mi = 0.0, mj = 0.0
        for k in 0..<3 { mi += w[k] * fi[k]; mj += w[k] * fj[k] }
        if (mi > mj) == (segs[i].observed > segs[j].observed) { hit += 1 }
    }
    return RewardModel(w: w, agreement: Double(hit) / Double(total), segments: segs)
}

private func proxyReward(_ w: [Double], _ a: Int, _ ns: Int) -> Double {
    (a == 2 ? w[0] : 0) + (a == 1 ? w[1] : 0) + (ns == chGoal ? w[2] : 0)
}

/// KL-anchored optimisation: π_ref(a|s)·exp(Q/β), renormalised.
private func klOptimise(_ w: [Double], _ beta: Double, sweeps: Int = 800) -> [[Double]] {
    let third = [1.0 / 3, 1.0 / 3, 1.0 / 3]
    var v = [Double](repeating: 0, count: chN)
    var pol = [[Double]](repeating: third, count: chN)
    for _ in 0..<sweeps {
        var nv = [Double](repeating: 0, count: chN)
        var np = [[Double]](repeating: third, count: chN)
        for s in 0..<chN where s != chGoal {
            let q = (0..<3).map { proxyReward(w, $0, rlhfNext(s, $0)) + 0.99 * v[rlhfNext(s, $0)] }
            let m = q.max()!
            let z = (0..<3).map { refPolicy[$0] * exp((q[$0] - m) / beta) }
            let zs = z.reduce(0, +)
            nv[s] = m + beta * log(zs)
            np[s] = z.map { $0 / zs }
        }
        v = nv
        pol = np
    }
    return pol
}

private struct RlhfEval { let proxy: Double; let truth: Double; let dashes: Double }

private func evalRlhf(_ w: [Double], _ pol: [[Double]], runs: Int = 800) -> RlhfEval {
    var proxy = 0.0, truth = 0.0, dashes = 0.0
    for i in 0..<runs {
        var rng = Lcg(7700 + i * 13)
        var s = 0
        for _ in 0..<20 {
            let a = sampleAction(pol[s], rng.next())
            let ns = rlhfNext(s, a)
            proxy += proxyReward(w, a, ns)
            truth += (ns == chGoal ? 1.0 : 0.0) - dtCost - (a == 2 && rng.next() < dashP ? dashCost : 0)
            if a == 2 { dashes += 1 }
            s = ns
            if ns == chGoal { break }
        }
    }
    return RlhfEval(proxy: proxy / Double(runs), truth: truth / Double(runs), dashes: dashes / Double(runs))
}

// MARK: - Shared frame helpers

private func coverageTable(_ data: [Trans]) -> OffTable {
    let c = counts(data)
    return OffTable(label: "transitions logged per state and action", columnLabels: chActionNames,
                    rowLabels: (0..<chGoal).map { "s\($0)" },
                    rows: (0..<chGoal).map { s in (0..<chNA).map { "\(c[s][$0])" } },
                    highlight: Set((0..<chGoal).map { $0 * chNA + 2 }))
}

private let goalCaps = (0..<chGoal).map { "s\($0)" }

private func qBars(_ q: [[Double]], _ action: Int, _ label: String, _ color: Color) -> FrameBars {
    FrameBars(label: label, values: (0..<chGoal).map { Float(q[$0][action]) }, color: color, captions: goalCaps)
}

private func laneGrid(_ label: String, shade: (Int) -> Float, glyph: (Int) -> String, color: (Int) -> Color = { _ in accentColor }) -> OffGrid {
    OffGrid(label: label, cells: (0..<(rows * cols)).map { OffCell(shade: shade($0), glyph: glyph($0), color: color($0)) })
}

private let actionArrows = ["→", "↑", "↓"]

private func clamp01(_ x: Float) -> Float { min(max(x, 0), 1) }

// MARK: - offline_rl

private func offlineRlFrames() -> [OffFrame] {
    let data = collect(seed: 3, episodes: 40, eps: 0.5)
    let behaviour = behaviourReturn(0.5)
    let naive = fqi(data)
    let naiveReturn = chReturn(naive)
    let optimal = chReturn(chTrueQ)
    let gambleErr = (0..<chGoal).map { naive[$0][2] - chTrueQ[$0][2] }.max()!
    let dataCounts = counts(data)
    let gambleCounts = (0..<chGoal).reduce(0) { $0 + dataCounts[$1][2] }
    let onlineSame = onlineQ(3, data.count)
    let onlineEight = onlineQ(3, data.count * 8)
    let onlineErr = (0..<chGoal).map { onlineSame[$0][2] - chTrueQ[$0][2] }.max()!
    let cleanData = collect(seed: 3, episodes: 40, eps: 0.7, pGamble: 0)
    let stitched = chReturn(fqi(cleanData))
    let cleanBehaviour = behaviourReturn(0.7, 0)
    let capped = collect(seed: 3, episodes: 40, eps: 0.5, pGamble: 0, cap: 3)
    let cappedQ = fqi(capped)

    return [
        OffFrame(status: "Offline RL starts from a log somebody else produced. This one holds \(data.count) transitions from a behaviour policy that mostly walks right, dithers sometimes, and occasionally pulls a lottery action worth exactly nothing in expectation. No further interaction is allowed.",
                 table: coverageTable(data), readout: "behaviour policy scores \(fmt(behaviour)); the best possible is \(fmt(optimal))"),
        OffFrame(status: "First, what offline RL buys you. On a log with no lottery pulls, the behaviour policy scores \(fmt(cleanBehaviour)) — but fitted Q-iteration over its transitions reaches \(fmt(stitched)), the optimum. It stitches the good fragments of mediocre trajectories into a policy nobody demonstrated. This is the reason not to just clone the data.",
                 bars: [FrameBars(label: "discounted return", values: floats([cleanBehaviour, stitched, optimal]), color: accentColor, captions: ["behaviour", "offline RL", "optimal"])],
                 readout: "\(fmt(cleanBehaviour)) → \(fmt(stitched)) from the very same transitions"),
        OffFrame(status: "Now put the lottery back. It was pulled only \(gambleCounts) times across the whole log, and a couple of those pulls happened to pay. Fitted Q-iteration takes the maximum over its own estimates, so those lucky samples become the value of the action — overestimated by \(fmt(gambleErr)) at worst.",
                 bars: [qBars(naive, 2, "learned Q(s, gamble)", warnColor), qBars(chTrueQ, 2, "true Q(s, gamble)", baselineColor)],
                 readout: "greedy policy: " + (0..<chGoal).map { "s\($0)→\(chActionNames[greedyAction(naive, $0)])" }.joined(separator: ", ")),
        OffFrame(status: "Deployed, that policy scores \(fmt(naiveReturn)) against the optimum's \(fmt(optimal)). The failure is not that offline learning is slow. Run the same algorithm online on the identical \(data.count)-transition budget and its lottery estimate is not inflated at all — it sits \(fmt(onlineErr)) *below* the truth, because every extra pull drags a lucky estimate back towards the mean. Online, more interaction eventually fixes it (\(data.count * 8) transitions here). Offline, no amount of compute does, because the corrective pulls are not in the log and never will be.",
                 bars: [
                    FrameBars(label: "discounted return", values: floats([naiveReturn, chReturn(onlineEight), optimal]), color: accentColor, captions: ["offline", "online ×8", "optimal"]),
                    FrameBars(label: "worst overestimate of the lottery", values: floats([gambleErr, onlineErr]), color: warnColor, captions: ["offline", "online"]),
                 ],
                 readout: "the error is not slower — it is uncorrectable"),
        OffFrame(status: "The harder limit is coverage. Log a behaviour policy that never walks past s3 and the far end of the chain simply is not in the data: every Q(s, right) stays at \(fmt(cappedQ[0][1], 2)), and the learned policy scores \(fmt(chReturn(cappedQ))). Conservatism, pessimism and clever penalties all help with actions the data covers badly. Nothing recovers an outcome the data never recorded.",
                 bars: [qBars(cappedQ, 1, "learned Q(s, right) from the truncated log", baselineColor)],
                 readout: "states visited: s0–s\(capped.map(\.s).max()!) of s0–s\(chGoal)"),
    ]
}

// MARK: - cql

private func cqlFrames() -> [OffFrame] {
    let data = collect(seed: 3, episodes: 40, eps: 0.5)
    let betas = [0.0, 0.1, 0.3, 1.0, 3.0, 10.0]
    let runs = betas.map { fqi(data, cqlBeta: $0) }
    let returns = runs.map { chReturn($0) }
    let gambleErr = runs.map { r in (0..<chGoal).map { r[$0][2] - chTrueQ[$0][2] }.max()! }
    let rightErr = runs.map { r in (0..<chGoal).reduce(0.0) { $0 + r[$1][1] - chTrueQ[$1][1] } / Double(chGoal) }
    let leftData = collectLeftBiased(3, 60, 0.35)
    let leftBetas = [0.0, 0.1, 0.3, 1.0, 3.0]
    let leftReturns = leftBetas.map { chReturn(fqi(leftData, cqlBeta: $0)) }
    let leftCounts = counts(leftData)
    let betaCaps = betas.map { fmt($0, 1) }

    return [
        OffFrame(status: "Plain fitted Q-iteration on this log overrates the rarely-pulled lottery by \(fmt(gambleErr[0])) and ends up choosing it. CQL's fix is not to estimate it better — it is to refuse to trust it.",
                 bars: [qBars(runs[0], 2, "learned Q(s, gamble)", warnColor), qBars(chTrueQ, 2, "true Q(s, gamble)", baselineColor)],
                 readout: "deployed return \(fmt(returns[0])) against an optimum of \(fmt(chReturn(chTrueQ)))"),
        OffFrame(status: "Each update now does two extra things: it pushes the whole action distribution at that state down by its softmax weight, and pushes the action the data actually took back up. Actions the log rarely contains take the push down far more often than they take the push up.",
                 table: coverageTable(data), readout: "α weights that push-down; the gamble column is what it targets"),
        OffFrame(status: "At α = \(betas[1]) the overestimate collapses from \(fmt(gambleErr[0])) to \(fmt(gambleErr[1])) and the deployed policy jumps from \(fmt(returns[0])) to \(fmt(returns[1])) — the optimum. Every α from \(betas[1]) to \(betas.last!) recovers the same optimal policy here, so on this problem the penalty costs nothing at all.",
                 plot: FramePlot(label: "deployed return by conservatism weight", curves: [FrameCurve(label: "return", values: floats(returns), color: improvedColor)],
                                 yRange: -0.1...1.0, xLabel: "α = " + betas.map { "\($0)" }.joined(separator: " · ")),
                 bars: [FrameBars(label: "worst overestimate of the lottery", values: floats(gambleErr), color: warnColor, captions: betaCaps)],
                 readout: "return \(fmt(returns[0])) → \(fmt(returns[1])), overestimate \(fmt(gambleErr[0])) → \(fmt(gambleErr[1]))"),
        OffFrame(status: "One claim worth checking rather than repeating: that CQL learns values which lower-bound the truth. Measured here, they do not. The penalty is *relative* — it pushes rare actions down and data actions up — so the absolute level drifts upward with α, and by α = \(betas.last!) the data action is overvalued by \(fmt(rightErr.last!)). The guarantee in the paper is on the expected value under the learned policy, with a large enough α, not on every entry of the table.",
                 bars: [FrameBars(label: "mean error in Q(s, right), the data's own action", values: floats(rightErr), color: highlightColor, captions: betaCaps)],
                 readout: "the ordering is conservative; the numbers are not lower bounds"),
        OffFrame(status: "And the cost of α shows up as soon as the log itself is poor. On a left-biased log — right is present but outnumbered \(leftCounts[0][0]) to \(leftCounts[0][1]) at the start state — plain fitted Q-iteration stitches its way to \(fmt(leftReturns[0])), while every α from \(leftBetas[1]) up drags the policy back onto the behaviour it was told to stay near and scores \(fmt(leftReturns[1])). α is not a safety dial with a good default: which way it helps depends on whether your danger is trusting unsupported actions or refusing supported ones.",
                 bars: [FrameBars(label: "return on the left-biased log, by α", values: floats(leftReturns), color: accentColor, captions: leftBetas.map { fmt($0, 1) })],
                 readout: "same algorithm, opposite conclusion, because the data changed"),
    ]
}

// MARK: - decision_transformer

private func decisionTransformerFrames() -> [OffFrame] {
    let ds = dtDataset(11)
    let rets = ds.trajs.map(\.ret)
    let best = rets.max()!, worst = rets.min()!, meanRet = rets.average
    let (cloneRet, cloneSteps) = dtCloneRollout(ds)
    let targets = [-0.2, 0.0, 0.2, 0.4, 0.55, 0.68, 0.9]
    let achieved = targets.map { dtRollout(ds, $0).0 }
    func row(_ key: Int) -> [String] { (ds.conditioned[key] ?? [0, 0]).map { "\($0)" } }

    return [
        OffFrame(status: "A deliberately mixed log: \(ds.trajs.count) trajectories, most of them poor. Returns run from \(fmt(worst, 2)) to \(fmt(best, 2)) with a mean of \(fmt(meanRet, 2)). Cloning it copies the average — the majority action at most states is the wrong one, so the cloned policy scores \(fmt(cloneRet, 2)) and never finishes inside \(cloneSteps) steps.",
                 bars: [FrameBars(label: "return", values: floats([worst, meanRet, cloneRet, best]), color: accentColor, captions: ["worst", "mean", "cloned", "best"])],
                 readout: "behaviour cloning on mixed data inherits the mixture"),
        OffFrame(status: "The Decision Transformer changes the question. Rather than \"what action follows this state\", it learns \"what action follows this state *when the rest of the trajectory earned R*\". Each step is tokenised as (return-to-go, state, action), and the return-to-go is decremented by the reward as the rollout proceeds.",
                 table: OffTable(label: "actions taken at s2, by the return the trajectory went on to earn", columnLabels: ["left", "right"], rowLabels: ["low", "mid", "high"],
                                 rows: [row(2 * 16 + 1), row(2 * 16 + 3), row(2 * 16 + 6)], highlight: [2 * 2 + 1]),
                 readout: "the same state, opposite action, depending on the return it is conditioned on"),
        OffFrame(status: "So the target return becomes the control knob. Prompt it with a low return and it reproduces dithering; prompt it high and it walks straight to the goal. Nothing was optimised — the model only ever predicted actions.",
                 plot: FramePlot(label: "achieved return against the target it was prompted with", curves: [
                    FrameCurve(label: "achieved", values: floats(achieved), color: improvedColor),
                    FrameCurve(label: "target", values: floats(targets), color: baselineColor),
                 ], yRange: -0.4...1.0, xLabel: "target " + targets.map { fmt($0, 2) }.joined(separator: " · ")),
                 readout: targets.indices.map { "\(fmt(targets[$0], 2))→\(fmt(achieved[$0], 2))" }.joined(separator: "  ")),
        OffFrame(status: "The ceiling is the honest part. Asking for \(fmt(targets.last!, 2)) — more than any trajectory in the log achieved — returns \(fmt(achieved.last!, 2)), exactly the dataset's best. Conditioning retrieves the behaviour that earned a return; it cannot invent behaviour that earns more. That is the trade for dropping value functions and Bellman backups entirely.",
                 bars: [FrameBars(label: "achieved return", values: floats([achieved[1], achieved[4], achieved.last!, best]), color: improvedColor, captions: ["ask 0.0", "ask 0.55", "ask 0.90", "data best"])],
                 readout: "asks above \(fmt(best, 2)) all return \(fmt(best, 2)) — the log is the ceiling"),
    ]
}

// MARK: - imitation_learning

private func imitationFrames() -> [OffFrame] {
    let expertRate = laneSuccess(expertPolicy)
    let demoCounts = [5, 20, 80, 320]
    let clones = demoCounts.map { behaviourClone(laneDemos($0)) }
    let rates = clones.map { laneSuccess($0.policy) }
    let bc = clones[1]
    let drift = driftStats(bc.policy)

    // DAgger: relabel the states the learner actually reaches.
    var aggregated = laneDemos(5)
    var current = behaviourClone(aggregated)
    var daggerRates = [laneSuccess(current.policy)]
    var daggerCoverage = [current.covered.count]
    for iteration in 0..<4 {
        var rng = Lcg(600 + iteration * 17)
        for _ in 0..<20 {
            var s = lnStart
            for _ in 0..<hor {
                aggregated.append((s, expertAction(s)))
                s = laneStep(current.policy, s, &rng)
                if s == lnGoal { break }
            }
        }
        current = behaviourClone(aggregated)
        daggerRates.append(laneSuccess(current.policy))
        daggerCoverage.append(current.covered.count)
    }
    let daggerDrift = driftStats(current.policy)
    let finalCovered = current.covered

    return [
        OffFrame(status: "A \(rows)×\(cols) corridor. The expert walks the middle row to the far end, and every move slips one row up or down with probability \(fmt(slip, 2)). Following the expert's own rule succeeds \(fmt(expertRate, 3)) of the time — the slips alone cost the rest.",
                 grid: laneGrid("the expert's route", shade: { rowOf($0) == mid ? 0.85 : 0.06 },
                                glyph: { $0 == lnStart ? "S" : $0 == lnGoal ? "G" : rowOf($0) == mid ? actionArrows[expertAction($0)] : "" },
                                color: { rowOf($0) == mid ? improvedColor : baselineColor }),
                 readout: "expert success \(fmt(expertRate, 3))"),
        OffFrame(status: "Demonstrations are clean: the expert shows the task without slipping. So the cloner only ever sees the \(bc.covered.count) states along the middle row, out of \(rows * cols). Two thirds of the corridor is a blank it has no label for, and off there it falls back on the action that dominates its dataset — \"\(actionArrows[bc.fallback])\", which walks along the wrong row rather than back to the right one.",
                 grid: laneGrid("states the demonstrations cover", shade: { bc.covered.contains($0) ? 0.85 : 0.06 },
                                glyph: { bc.covered.contains($0) ? actionArrows[expertAction($0)] : "·" },
                                color: { bc.covered.contains($0) ? accentColor : baselineColor }),
                 readout: "\(bc.covered.count) of \(rows * cols) states demonstrated"),
        OffFrame(status: "Deployed into the slippery corridor the clone scores \(fmt(drift.success, 3)) against the expert's \(fmt(expertRate, 3)). The mechanism is visible in the split: \(fmt(drift.drifted, 2)) of episodes leave the demonstrated row at least once, and once off it the success rate is only \(fmt(drift.successGivenDrift, 2)). One slip puts the policy somewhere it was never taught, and its own mistake carries it further out.",
                 bars: [FrameBars(label: "success rate", values: floats([drift.success, drift.successGivenDrift, expertRate]), color: accentColor, captions: ["clone", "clone after drift", "expert"])],
                 readout: "\(fmt(drift.drifted * 100, 0))% of episodes drift off the demonstrated states"),
        OffFrame(status: "The instinctive fix — collect more demonstrations — does nothing. Going from \(demoCounts.first!) to \(demoCounts.last!) demonstrations leaves coverage at exactly \(clones.last!.covered.count) states and the success rate at \(fmt(rates.last!, 3)). More data from the expert's distribution cannot describe states the expert's distribution never reaches.",
                 bars: [FrameBars(label: "success rate by number of demonstrations", values: floats(rates), color: baselineColor, captions: demoCounts.map { "\($0)" })],
                 readout: "\(demoCounts.last!) demonstrations, still \(clones.last!.covered.count)/\(rows * cols) states covered"),
        OffFrame(status: "DAgger changes which states get labelled: run the learner, and ask the expert what it should have done at the states the *learner* reached. Coverage goes \(daggerCoverage.first!) → \(daggerCoverage.last!) states and the success rate \(fmt(daggerRates.first!, 3)) → \(fmt(daggerRates.last!, 3)), matching the expert. Recovery from a mistake is precisely the skill no flawless demonstration contains.",
                 plot: FramePlot(label: "success rate per DAgger round", curves: [FrameCurve(label: "DAgger", values: floats(daggerRates), color: improvedColor)], yRange: 0...1, xLabel: "round"),
                 grid: laneGrid("states labelled after DAgger", shade: { finalCovered.contains($0) ? 0.85 : 0.06 },
                                glyph: { finalCovered.contains($0) ? actionArrows[expertAction($0)] : "·" },
                                color: { finalCovered.contains($0) ? improvedColor : baselineColor }),
                 readout: "after drift: \(fmt(drift.successGivenDrift, 2)) → \(fmt(daggerDrift.successGivenDrift, 2))"),
    ]
}

// MARK: - gail

private struct GailRun { let tv: [Double]; let accuracy: [Double]; let success: [Double]; let policy: LanePolicy }

private func runGail(iterations: Int = 25) -> GailRun {
    let expOcc = occupancy(expertPolicy)
    var agOcc = occupancy(uniformLane)
    var policy = expertPolicy
    var tv: [Double] = [], acc: [Double] = [], success: [Double] = []
    for _ in 0..<iterations {
        let snapshot = agOcc
        let disc = { (s: Int, a: Int) -> Double in
            let e = expOcc[s * lnNA + a] ?? 0
            let g = snapshot[s * lnNA + a] ?? 0
            return (e + 1e-3) / (e + g + 2e-3)
        }
        // The symmetric logit reward: −log(1−D) would pay the agent to survive instead of finish.
        policy = softVi { s, a in
            let d = disc(s, a)
            return log(max(1e-6, d)) - log(max(1e-6, 1 - d))
        }
        let fresh = occupancy(policy)
        // Average occupancies rather than jumping to the best response (fictitious-play damping).
        var merged: [Int: Double] = [:]
        for k in Set(expOcc.keys).union(agOcc.keys).union(fresh.keys) {
            merged[k] = 0.75 * (agOcc[k] ?? 0) + 0.25 * (fresh[k] ?? 0)
        }
        agOcc = merged
        var a = 0.0
        for (k, v) in expOcc where disc(k / lnNA, k % lnNA) > 0.5 { a += 0.5 * v }
        for (k, v) in agOcc where disc(k / lnNA, k % lnNA) <= 0.5 { a += 0.5 * v }
        tv.append(tvDistance(agOcc, expOcc))
        acc.append(a)
        success.append(laneSuccess(policy))
    }
    return GailRun(tv: tv, accuracy: acc, success: success, policy: policy)
}

private func gailFrames() -> [OffFrame] {
    let expOcc = occupancy(expertPolicy)
    let bc = behaviourClone(laneDemos(20))
    let bcTv = tvDistance(occupancy(bc.policy), expOcc)
    let bcRate = laneSuccess(bc.policy)
    let run = runGail()
    let expertRate = laneSuccess(expertPolicy)
    let wall = cell(mid, 4)
    let gailWalled = laneSuccess(run.policy, wall: wall, hor: 16)
    let bcWalled = laneSuccess(bc.policy, wall: wall, hor: 16)

    return [
        OffFrame(status: "GAIL drops the idea of copying actions and matches distributions instead. The object it compares is occupancy: how often each state-action pair is visited over an episode. The clone's occupancy sits \(fmt(bcTv, 3)) away from the expert's in total variation — that distance, not the per-state action accuracy, is what its \(fmt(bcRate, 3)) success rate reflects.",
                 grid: laneGrid("expert occupancy", shade: { s in clamp01(Float((0..<lnNA).reduce(0.0) { $0 + (expOcc[s * lnNA + $1] ?? 0) } * 9)) },
                                glyph: { $0 == lnGoal ? "G" : $0 == lnStart ? "S" : "" }, color: { _ in improvedColor }),
                 readout: "TV(expert, clone) = \(fmt(bcTv, 3))"),
        OffFrame(status: "A discriminator is fitted to tell expert visits from agent visits, and the agent is then trained to maximise how expert-like it looks. At the start the discriminator separates them almost perfectly — \(fmt(run.accuracy.first!, 2)) accuracy at a total-variation distance of \(fmt(run.tv.first!, 3)).",
                 plot: FramePlot(label: "occupancy distance from the expert", curves: [FrameCurve(label: "TV distance", values: floats(run.tv), color: warnColor)], yRange: 0...0.7, xLabel: "adversarial round"),
                 readout: "round 0: TV \(fmt(run.tv.first!, 3)), discriminator \(fmt(run.accuracy.first!, 2))"),
        OffFrame(status: "As the agent closes the gap the discriminator's job gets harder, and its accuracy falls to \(fmt(run.accuracy.last!, 2)) — chance. The distance ends at \(fmt(run.tv.last!, 3)) and the agent succeeds \(fmt(run.success.last!, 3)) of the time against the clone's \(fmt(bcRate, 3)) and the expert's \(fmt(expertRate, 3)), from the same demonstrations the clone had.",
                 plot: FramePlot(label: "the adversarial game", curves: [
                    FrameCurve(label: "TV distance", values: floats(run.tv), color: warnColor),
                    FrameCurve(label: "discriminator accuracy", values: floats(run.accuracy), color: accentColor),
                    FrameCurve(label: "success rate", values: floats(run.success), color: improvedColor),
                 ], yRange: 0...1, xLabel: "adversarial round"),
                 readout: "TV \(fmt(run.tv.first!, 3)) → \(fmt(run.tv.last!, 3)); discriminator \(fmt(run.accuracy.first!, 2)) → \(fmt(run.accuracy.last!, 2))"),
        OffFrame(status: "Two things this cost. GAIL fixed the drift the clone could not because it *interacted* with the corridor — it visited the off-row states and learned what to do there, using no reward but plenty of environment access the clone never needed. And what it learned is a policy, not a purpose: put a wall in the middle row and it scores \(fmt(gailWalled, 3)), below even the clone's \(fmt(bcWalled, 3)). Matching the expert's distribution matches it in the world it was recorded in.",
                 bars: [
                    FrameBars(label: "success rate, original corridor", values: floats([bcRate, run.success.last!, expertRate]), color: improvedColor, captions: ["clone", "GAIL", "expert"]),
                    FrameBars(label: "success rate, wall added", values: floats([bcWalled, gailWalled]), color: warnColor, captions: ["clone", "GAIL"]),
                 ],
                 readout: "occupancy matching does not survive a changed environment"),
    ]
}

// MARK: - irl

private struct IrlRun { let weights: [Double]; let error: [Double]; let success: [Double]; let policy: LanePolicy }

/// Max-entropy IRL: plan under the current reward, compare visitation, move the reward.
private func runIrl(_ expFeat: [Double], iterations: Int = 120, initial: [Double]? = nil) -> IrlRun {
    var w = initial ?? [Double](repeating: 0, count: rows * cols)
    var error: [Double] = [], success: [Double] = []
    var policy = expertPolicy
    func l1(_ feat: [Double]) -> Double { (0..<(rows * cols)).reduce(0.0) { $0 + abs(expFeat[$1] - feat[$1]) } }
    for i in 0..<iterations {
        let weights = w
        policy = softVi(stateReward { weights[$0] })
        let feat = stateVisits(occupancy(policy))
        if i % 10 == 0 {
            error.append(l1(feat))
            success.append(laneSuccess(policy))
        }
        for s in 0..<(rows * cols) { w[s] += 2 * (expFeat[s] - feat[s]) }
    }
    error.append(l1(stateVisits(occupancy(policy))))
    success.append(laneSuccess(policy))
    return IrlRun(weights: w, error: error, success: success, policy: policy)
}

private func irlFrames() -> [OffFrame] {
    let expFeat = stateVisits(occupancy(expertPolicy))
    let run = runIrl(expFeat)
    let w = run.weights
    var rng = Lcg(4242)
    let initial = (0..<(rows * cols)).map { _ in (rng.next() - 0.5) * 4 }
    let second = runIrl(expFeat, initial: initial)
    let rewardGap = (0..<(rows * cols)).map { abs(w[$0] - second.weights[$0]) }.max()!
    let policyGap = (0..<(rows * cols)).map { s in (0..<lnNA).map { abs(second.policy[s][$0] - run.policy[s][$0]) }.max()! }.max()!
    let wall = cell(mid, 4)
    let bcWalled = laneSuccess(behaviourClone(laneDemos(20)).policy, wall: wall, hor: 16)
    let irlWalled = laneSuccess(softVi(stateReward({ w[$0] }, wall: wall), wall: wall), wall: wall, hor: 16)
    let trueWalled = laneSuccess(softVi(stateReward({ $0 == lnGoal ? 3.0 : 0.0 }, wall: wall), wall: wall), wall: wall, hor: 16)
    let peak = argmaxFirst(w)
    let wMax = w[peak]
    let middleCaps = (0..<cols).map { "c\($0)" }

    return [
        OffFrame(status: "IRL asks a different question of the same demonstrations: not which action the expert took, but what objective would make those actions optimal. The only thing it matches is expected state visitation — how much time the expert spends where.",
                 grid: laneGrid("expert state visitation", shade: { clamp01(Float(expFeat[$0] * 6)) },
                                glyph: { $0 == lnGoal ? "G" : $0 == lnStart ? "S" : "" }, color: { _ in accentColor }),
                 readout: "match these counts and you have matched the behaviour"),
        OffFrame(status: "The loop: guess a reward, plan the max-entropy optimal policy under it, compare that policy's visitation to the expert's, and move the reward up where the expert went more often. Visitation error falls from \(fmt(run.error.first!, 3)) to \(fmt(run.error.last!, 3)) and the resulting policy's success rate climbs \(fmt(run.success.first!, 3)) → \(fmt(run.success.last!, 3)).",
                 plot: FramePlot(label: "visitation mismatch and the policy it implies", curves: [
                    FrameCurve(label: "feature-count error", values: floats(run.error), color: warnColor),
                    FrameCurve(label: "success rate", values: floats(run.success), color: improvedColor),
                 ], yRange: 0...1, xLabel: "every 10th iteration"),
                 readout: "error \(fmt(run.error.first!, 3)) → \(fmt(run.error.last!, 3))"),
        OffFrame(status: "The recovered reward peaks at the goal cell (\(fmt(wMax, 2))) and slopes up along the corridor towards it. Nobody wrote that down — it was inferred from where the expert chose to spend its time.",
                 grid: laneGrid("recovered reward", shade: { clamp01(Float(w[$0] / wMax)) }, glyph: { $0 == lnGoal ? "G" : "" }, color: { _ in highlightColor }),
                 readout: "peak at row \(rowOf(peak)), column \(colOf(peak)) = \(fmt(wMax, 2))"),
        OffFrame(status: "IRL is ill-posed, and the second run shows it concretely: started from a different random reward, it lands on weights that differ by up to \(fmt(rewardGap, 2)) — yet the policies they imply differ by at most \(fmt(policyGap, 3)) in action probability and succeed at \(fmt(second.success.last!, 3)) against \(fmt(run.success.last!, 3)). Many rewards explain the same behaviour equally well; max-entropy IRL picks the least committal of them.",
                 bars: [
                    FrameBars(label: "recovered reward along the middle row, two runs", values: (0..<cols).map { Float(w[cell(mid, $0)]) }, color: highlightColor, captions: middleCaps),
                    FrameBars(label: "second run", values: (0..<cols).map { Float(second.weights[cell(mid, $0)]) }, color: baselineColor, captions: middleCaps),
                 ],
                 readout: "different rewards, the same behaviour"),
        OffFrame(status: "Why bother recovering a reward at all: put a wall in the middle of the corridor and re-plan. The clone walks into it and scores \(fmt(bcWalled, 3)); planning against the *recovered* reward routes around it for \(fmt(irlWalled, 3)), close to the \(fmt(trueWalled, 3)) you get from the true reward. A policy is an answer to one world. A reward is an answer to the question, and it still applies when the world changes.",
                 bars: [FrameBars(label: "success after the wall appears", values: floats([bcWalled, irlWalled, trueWalled]), color: improvedColor, captions: ["clone", "IRL re-plan", "true reward"])],
                 grid: laneGrid("the changed corridor", shade: { $0 == wall ? 1 : rowOf($0) == mid ? 0.35 : 0.08 },
                                glyph: { $0 == wall ? "✕" : $0 == lnGoal ? "G" : $0 == lnStart ? "S" : "" },
                                color: { $0 == wall ? warnColor : accentColor }),
                 readout: "\(fmt(bcWalled, 3)) → \(fmt(irlWalled, 3)) by re-planning against an inferred objective"),
    ]
}

// MARK: - rlhf

private func rlhfFrames() -> [OffFrame] {
    let rm = fitRewardModel(29)
    let betas = [0.02, 0.05, 0.1, 0.25, 0.5, 1.0, 2.0, 4.0, 8.0]
    let evals = betas.map { evalRlhf(rm.w, klOptimise(rm.w, $0)) }
    let reference = evalRlhf(rm.w, [[Double]](repeating: refPolicy, count: chN))
    let bestTrue = argmaxFirst(evals.map(\.truth))
    let bestProxy = argmaxFirst(evals.map(\.proxy))
    let observedGap = rm.segments.map { $0.observed - $0.truth }.average
    let betaCaps = betas.map { fmt($0, 2) }
    let first = evals[0]

    return [
        OffFrame(status: "The chain gains a \"dash\" that advances two states at once. It also fails \(fmt(dashP, 2)) of the time at a cost of \(fmt(dashCost, 1)) — a cost that appears in no segment a rater is shown. Across the \(rm.segments.count) segments collected here, what the rater sees is on average \(fmt(observedGap, 3)) better than what actually happened.",
                 bars: [FrameBars(label: "mean return over the collected segments", values: floats([rm.segments.map(\.observed).average, rm.segments.map(\.truth).average]), color: accentColor, captions: ["as rated", "as it truly was"])],
                 readout: "preferences are labelled on the left bar; the right bar is what we care about"),
        OffFrame(status: "A Bradley-Terry reward model is fitted to those pairwise comparisons, 10% of which are mislabelled. It agrees with the raters on \(fmt(rm.agreement * 100, 1))% of held-out pairs — by the only metric available at training time, a good reward model. It scores dashing at \(fmt(rm.w[0], 3)) and reaching the goal at \(fmt(rm.w[2], 3)).",
                 bars: [FrameBars(label: "learned reward weights", values: floats(rm.w), color: highlightColor, captions: ["dash", "step right", "reached goal"])],
                 readout: "held-out agreement with rater preferences: \(fmt(rm.agreement * 100, 1))%"),
        OffFrame(status: "Optimise against it as hard as possible — a KL weight of \(fmt(betas[0], 2)) — and the proxy reward climbs to its maximum, \(fmt(reference.proxy, 3)) → \(fmt(first.proxy, 3)). True return over the same policy: \(fmt(first.truth, 3)), which is no better than the untuned reference it started from (\(fmt(reference.truth, 3))). All that optimisation pressure went into dashing \(fmt(first.dashes, 2)) times an episode, because the reward model has no term for what dashing costs.",
                 bars: [
                    FrameBars(label: "reward-model score", values: floats([reference.proxy, first.proxy]), color: highlightColor, captions: ["reference", "optimised"]),
                    FrameBars(label: "true return", values: floats([reference.truth, first.truth]), color: warnColor, captions: ["reference", "optimised"]),
                 ],
                 readout: "the proxy went up; the thing it stands for did not"),
        OffFrame(status: "Sweeping the KL weight puts the whole problem on one chart, and the two curves peak in different places: the proxy is maximised at β = \(fmt(betas[bestProxy], 2)), the true return at β = \(fmt(betas[bestTrue], 2)). Optimising the measure past that point degrades the thing it was measuring. That is Goodhart's law with numbers attached, and it is the entire reason the KL term is in the objective.",
                 plot: FramePlot(label: "proxy against truth, by KL weight", curves: [
                    FrameCurve(label: "reward-model score", values: floats(evals.map(\.proxy)), color: highlightColor),
                    FrameCurve(label: "true return", values: floats(evals.map(\.truth)), color: improvedColor),
                 ], yRange: -0.3...1.3, xLabel: "β = " + betaCaps.joined(separator: " · ")),
                 readout: "best proxy at β=\(fmt(betas[bestProxy], 2)), best truth at β=\(fmt(betas[bestTrue], 2))"),
        OffFrame(status: "At the best setting the policy still improves on the reference — \(fmt(reference.truth, 3)) to \(fmt(evals[bestTrue].truth, 3)) — and dashes \(fmt(evals[bestTrue].dashes, 2)) times an episode instead of \(fmt(first.dashes, 2)). β is not a safety margin bolted on afterwards; it is the admission that the reward model is only trustworthy near the data it was fitted on.",
                 bars: [
                    FrameBars(label: "true return by KL weight", values: floats(evals.map(\.truth)), color: improvedColor, captions: betaCaps),
                    FrameBars(label: "dashes per episode", values: floats(evals.map(\.dashes)), color: warnColor, captions: betaCaps),
                 ],
                 readout: "reference \(fmt(reference.truth, 3)) → best \(fmt(evals[bestTrue].truth, 3)) at β=\(fmt(betas[bestTrue], 2))"),
    ]
}

// MARK: - Config

private let comparisonLegend: [(Color, String)] = [(baselineColor, "Baseline"), (improvedColor, "Improved"), (warnColor, "Failure mode")]

private let offlineConfigs: [String: OffConfig] = [
    "offline_rl": OffConfig(intro: "One logged dataset, no further interaction. What that buys you, and the two ways it bites back — measured on a chain that hides a worthless lottery action.", legend: comparisonLegend, build: offlineRlFrames),
    "cql": OffConfig(intro: "A conservative penalty applied to a real overestimate, swept across its strength — including the dataset where the same penalty makes things worse.", legend: comparisonLegend, build: cqlFrames),
    "decision_transformer": OffConfig(intro: "Return-conditioned sequence modelling on a deliberately mixed log: what a target return controls, and the ceiling it cannot pass.",
                                      legend: [(baselineColor, "Target"), (improvedColor, "Achieved"), (accentColor, "Dataset")], build: decisionTransformerFrames),
    "imitation_learning": OffConfig(intro: "Clean demonstrations, noisy execution. Behaviour cloning's compounding error measured on a corridor, then repaired by relabelling the states the learner actually reaches.", legend: comparisonLegend, build: imitationFrames),
    "gail": OffConfig(intro: "Imitation as a distribution-matching game: a discriminator that separates expert from agent, and the occupancy distance closing until it cannot.", legend: comparisonLegend, build: gailFrames),
    "irl": OffConfig(intro: "Recovering the objective instead of the actions — by matching state visitation — and then the test that only a reward can pass: the environment changes.",
                     legend: [(highlightColor, "Recovered reward"), (improvedColor, "Improved"), (warnColor, "Mismatch")], build: irlFrames),
    "rlhf": OffConfig(intro: "A reward model fitted to preferences that were labelled on what a rater can see, then optimised against at nine different KL weights. The two curves do not peak in the same place.",
                      legend: [(highlightColor, "Proxy reward"), (improvedColor, "True return"), (warnColor, "Hidden cost")], build: rlhfFrames),
]

// MARK: - UI

struct OfflineRlLab: View {
    private let config: OffConfig
    private let key: String
    @Environment(\.palette) private var palette

    init(topicId: String) {
        key = offlineConfigs[topicId] == nil ? "offline_rl" : topicId
        config = offlineConfigs[key]!
    }

    var body: some View {
        let key = self.key
        AsyncFrameLab(key: "offline:\(key)", speedMs: 1200, build: { offlineConfigs[key]!.build() }) { frames, playback in
            content(frames, playback)
        }
    }

    @ViewBuilder
    private func content(_ frames: [OffFrame], _ playback: PlaybackState) -> some View {
        let frame = frames[min(playback.index, frames.count - 1)]
        LabCard {
            LabIntro(text: config.intro)
            if let grid = frame.grid { CorridorGridView(grid: grid).padding(.top, 12) }
            if let table = frame.table { CountTableView(table: table).padding(.top, 12) }
            if let plot = frame.plot { FramePlotView(plot: plot, xLabelInline: false).padding(.top, 12) }
            ForEach(frame.bars.indices, id: \.self) { FrameBarsView(bar: frame.bars[$0], negativeColor: warnColor).padding(.top, 12) }
            FramePlayerFooter(readout: frame.readout, status: frame.status, legend: config.legend, playback: playback, captions: frames.map(\.status))
        }
    }
}

private struct CorridorGridView: View {
    let grid: OffGrid
    @Environment(\.palette) private var palette

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text(grid.label).font(.labelSmall).foregroundStyle(palette.muted)
            Grid(horizontalSpacing: 3, verticalSpacing: 3) {
                ForEach(0..<rows, id: \.self) { r in
                    GridRow {
                        ForEach(0..<cols, id: \.self) { c in
                            let cellData = grid.cells[cell(r, c)]
                            RoundedRectangle(cornerRadius: 6)
                                .fill(cellData.color.opacity(Double(0.12 + 0.68 * clamp01(cellData.shade))))
                                .aspectRatio(1, contentMode: .fit)
                                .overlay {
                                    if !cellData.glyph.isEmpty { Text(cellData.glyph).font(AppFont.sans(11, .bold)) }
                                }
                        }
                    }
                }
            }
            .padding(.top, 4)
        }
    }
}

private struct CountTableView: View {
    let table: OffTable
    @Environment(\.palette) private var palette

    var body: some View {
        let weights: [CGFloat] = [0.6] + Array(repeating: 1, count: table.columnLabels.count)
        VStack(alignment: .leading, spacing: 0) {
            Text(table.label).font(.labelSmall).foregroundStyle(palette.muted)
            WeightedRow(weights: weights) {
                Color.clear.frame(height: 1)
                ForEach(table.columnLabels.indices, id: \.self) {
                    Text(table.columnLabels[$0]).font(.labelSmall).foregroundStyle(palette.muted).frame(maxWidth: .infinity)
                }
            }
            .padding(.top, 4)
            ForEach(table.rows.indices, id: \.self) { r in
                WeightedRow(weights: weights) {
                    Text(table.rowLabels[r]).font(AppFont.sans(11, .bold)).foregroundStyle(palette.muted).frame(maxWidth: .infinity, alignment: .leading)
                    ForEach(table.rows[r].indices, id: \.self) { c in
                        let marked = table.highlight.contains(r * table.columnLabels.count + c)
                        Text(table.rows[r][c])
                            .font(AppFont.sans(11, marked ? .bold : .regular))
                            .foregroundStyle(marked ? warnColor : palette.onSurface)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 2)
                            .background(marked ? warnColor.opacity(0.14) : .clear, in: RoundedRectangle(cornerRadius: 4))
                            .padding(.horizontal, 2)
                    }
                }
                .padding(.top, 2)
            }
        }
    }
}
