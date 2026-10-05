import Foundation

// Port of RlDeepQFrames.kt: a 6-state chain with reward only past the far end. Approximators, replay,
// target networks, double estimation, dueling heads, noisy exploration and return distributions, each
// trained or computed live from a seeded run. Drawn by RlBoardLabs.swift.

let rlDeepQTopicIds: Set<String> = ["dqn", "experience_replay", "target_networks", "double_dqn", "dueling_dqn", "noisy_nets", "c51", "rainbow_dqn"]

func rlDeepQLab(_ topicId: String) -> RbLab? {
    switch topicId {
    case "dqn": dqnLab()
    case "experience_replay": replayLab()
    case "target_networks": targetLab()
    case "double_dqn": doubleLab()
    case "dueling_dqn": duelingLab()
    case "noisy_nets": noisyLab()
    case "c51": c51Lab()
    case "rainbow_dqn": rainbowLab()
    default: nil
    }
}

private func f(_ x: Double, _ d: Int = 2) -> String { Rb.f(x, d) }
private func pct(_ p: Double) -> String { Rb.pct(p) }
private func js(_ x: Double) -> String { Rb.js(x) }
private func L(_ t: String) -> RbBlock { Rb.L(t) }
private func C(_ items: [RbTok]) -> RbBlock { Rb.C(items) }
private func B(_ rows: [RbRow]) -> RbBlock { Rb.B(rows) }
private func S(_ rows: (String, String, String?)...) -> RbBlock { Rb.S(rows) }
private func st(_ k: String, _ v: String, _ c: String? = nil) -> (String, String, String?) { (k, v, c) }
private func F(_ a: String, _ b: String = "", _ c: String? = nil) -> RbFx { Rb.F(a, b, c) }
private func fr(_ blocks: [RbBlock], _ fx: [RbFx], _ cap: (String, String), _ lg: [(String, String)]) -> RbFrame { Rb.frame(blocks, fx, cap, lg) }

// MARK: - The chain: right from s5 ends with reward 1; left moves back one state

struct RbCst { let ns: Int; let r: Double; let t: Bool }

func rbCst(_ s: Int, _ a: Int) -> RbCst {
    a == 1 ? (s == 5 ? RbCst(ns: -1, r: 1, t: true) : RbCst(ns: s + 1, r: 0, t: false)) : RbCst(ns: max(0, s - 1), r: 0, t: false)
}

func rbCq(_ g: Double = 0.9) -> (qr: [Double], ql: [Double]) {
    let qr = (0...5).map { pow(g, Double(5 - $0)) }
    return (qr, (0...5).map { g * qr[max(0, $0 - 1)] })
}

private func chainChips(_ vals: [Double], _ hl: Int? = nil) -> RbBlock {
    C(vals.enumerated().map { i, v in
        let t = Rb.tok("s\(i)", i == hl ? "cur" : "plain", f(v))
        return RbTok(t: t.t, sub: t.sub, bg: i == hl ? "#f5c542" : RbGw.vcol(v), color: i == hl ? "#1b1d24" : "#fff")
    })
}

private func chainErr(_ v: [Double]) -> Double {
    let qr = rbCq().qr
    return v.indices.map { abs(v[$0] - qr[$0]) }.max()!
}

// MARK: - 59a DQN: semi-gradient Q-learning on three feature sets

private let dqnCache = RbCache<Int, [Int: [Double]]>()

private func dqnRun(_ kind: Int) -> [Int: [Double]] {
    dqnCache.get(kind) {
        let r = Rb.rng(21)
        func phi(_ s: Int) -> [Double] {
            let x = Double(s) / 5
            switch kind {
            case 0: return (0..<6).map { $0 == s ? 1 : 0 }
            case 1: return [1, x]
            default: return [1, x, x * x]
            }
        }
        let d = phi(0).count
        var w = [[Double](repeating: 0, count: d), [Double](repeating: 0, count: d)]
        func q(_ s: Int, _ a: Int) -> Double { let p = phi(s); var acc = 0.0; for i in p.indices { acc += p[i] * w[a][i] }; return acc }
        var snaps: [Int: [Double]] = [:]
        for t in 1...5000 {
            let s = Int(floor(r() * 6))
            let a = r() < 0.5 ? 0 : 1
            let o = rbCst(s, a)
            let tg = o.r + (o.t ? 0 : 0.9 * max(q(o.ns, 0), q(o.ns, 1)))
            let e = tg - q(s, a)
            for (i, v) in phi(s).enumerated() { w[a][i] += 0.1 * e * v }
            if t == 100 || t == 1000 || t == 5000 { snaps[t] = (0...5).map { q($0, 1) } }
        }
        return snaps
    }
}

private func dqnLab() -> RbLab {
    let lg = [("#3b82f6", "Learned / true"), ("#e5337a", "Error"), ("#6d5dfc", "Chosen features")]
    let names = ["table", "linear", "quadratic"]
    return RbLab(tabs: names, initialTab: 2) { k in
        let qr = rbCq().qr
        let sn = dqnRun(k)
        return (0..<5).map { stp in
            let tt = [0, 100, 1000, 5000][min(stp, 3)]
            if stp == 0 {
                return fr([L("6-state chain · reward 1 for stepping right off s5 · γ 0.9"), chainChips(qr), S(st("target", "Q*(s, right) = 0.9^(5−s)"))],
                          [F("Q(s,a) ≈ w_a · φ(s)"), F("features:", ["one-hot (6 per action)", "[1, s]", "[1, s, s²]"][k], "#8f84ff")],
                          ("DQN replaces the Q-table with a function.", "Here the “network” is a linear map on features, small enough to watch every weight move."), lg)
            }
            if stp < 4 {
                let l = sn[tt]!
                return fr([L("learned Q(s, right) after \(tt) updates"), B(l.enumerated().map { i, v in Rb.row("s\(i)", "\(f(v)) / \(f(qr[i]))", v, "#3b82f6") })],
                          [F("w ← w + 0.1·(r + 0.9·max Q(s′) − Q)·φ(s)"), F("largest error =", f(chainErr(l), 3), "#e5337a")],
                          ("\(tt) updates: error \(f(chainErr(l), 3)).", k == 0 ? "One-hot features are just a table: it converges to Q* exactly."
                              : k == 1 ? "A straight line can’t bend to fit 0.9^(5−s); some error is permanent." : "A curve fits the shape closely with only 3 weights per action."), lg)
            }
            return fr([L("final largest error, 5,000 updates"), B((0...2).map { j in
                let e = chainErr(dqnRun(j)[5000]!)
                return Rb.row(names[j], f(e, 3), e / 0.2, j == k ? "#6d5dfc" : "#3a3f4c", j == k ? "#f2f3f7" : "#9aa0ae")
            })], [F("weights:", "12 · 4 · 6")],
                      ("Generalisation is the point, and the risk.", "Features let one update move many states. The next screens are the fixes that keep that stable."), lg)
        }
    }
}

// MARK: - 59b Experience Replay

private let repCache = RbCache<Int, [Int: [Double]]>()

func rbRepRun(_ k: Int) -> [Int: [Double]] {
    repCache.get(k) {
        let r = Rb.rng(5)
        var q = [[Double](repeating: 0, count: 6), [Double](repeating: 0, count: 6)]
        var buf: [(s: Int, a: Int, ns: Int, r: Double, t: Bool)] = []
        var snaps: [Int: [Double]] = [:]
        var s = 0
        var steps = 0
        func up(_ x: (s: Int, a: Int, ns: Int, r: Double, t: Bool)) {
            let tg = x.r + (x.t ? 0 : 0.9 * max(q[0][x.ns], q[1][x.ns]))
            q[x.a][x.s] += 0.5 * (tg - q[x.a][x.s])
        }
        while steps < 1000 {
            let a = r() < 0.5 ? 0 : 1
            let o = rbCst(s, a)
            let x = (s: s, a: a, ns: o.ns, r: o.r, t: o.t)
            up(x)
            buf.append(x)
            for _ in 0..<k { up(buf[Int(floor(r() * Double(buf.count)))]) }
            steps += 1
            s = o.t ? 0 : o.ns
            if steps == 50 || steps == 200 || steps == 1000 { snaps[steps] = q[1] }
        }
        return snaps
    }
}

private func replayLab() -> RbLab {
    let lg = [("#3b82f6", "Q estimate"), ("#e5337a", "Error"), ("#6d5dfc", "Chosen ratio")]
    return RbLab(tabs: ["online", "replay ×4", "replay ×16"], initialTab: 1) { opt in
        let k = [0, 4, 16][opt]
        let qr = rbCq().qr
        let sn = rbRepRun(k)
        return (0..<5).map { stp in
            let tt = [0, 50, 200, 1000][min(stp, 3)]
            let l = tt > 0 ? sn[tt]! : [Double](repeating: 0, count: 6)
            if stp == 0 {
                return fr([L("target Q*(s, right)"), chainChips(qr)], [F("random behaviour from s0, reward only past s5")],
                          ("Reward 1 at the far end, nothing elsewhere.", "Value must travel back six states from rare arrivals, so reusing transitions matters."), lg)
            }
            if stp < 4 {
                return fr([L("Q(s, right) after \(tt) environment steps"), chainChips(l)],
                          [F("updates per step: 1 + \(k) replayed"), F("largest error =", f(chainErr(l), 3), "#e5337a")],
                          ("\(tt) steps: error \(f(chainErr(l), 3)).", k == 0 ? "Online: each transition is used once and thrown away; the reward creeps back one state per arrival." : "Replay revisits \(k) stored transitions per step, pushing the reward back through the chain without new experience."), lg)
            }
            return fr([L("largest error after 200 steps"), B([0, 4, 16].map { j in
                let e = chainErr(rbRepRun(j)[200]!)
                return Rb.row(j > 0 ? "replay ×\(j)" : "online", f(e, 3), e, j == k ? "#6d5dfc" : "#3a3f4c", j == k ? "#f2f3f7" : "#9aa0ae")
            })], [F("same experience, more learning")],
                      ("Replay buys data efficiency.", "It also breaks the correlation between consecutive samples — what a neural network needs to train stably."), lg)
        }
    }
}

// MARK: - 59c Target Networks

private let tgCache = RbCache<Int, (trace: [Double], move: Double, err: Double)>()

private func tgtRun(_ cc: Int) -> (trace: [Double], move: Double, err: Double) {
    tgCache.get(cc) {
        let r = Rb.rng(13)
        func phi(_ s: Int) -> [Double] { let x = Double(s) / 5; return [1, x, x * x] }
        var w = [[0.0, 0, 0], [0.0, 0, 0]]
        var wt = w
        func q(_ m: [[Double]], _ s: Int, _ a: Int) -> Double { let p = phi(s); var acc = 0.0; for i in p.indices { acc += p[i] * m[a][i] }; return acc }
        var trace: [Double] = []
        var move = 0.0
        var prev: Double? = nil
        for t in 1...3000 {
            if t % cc == 0 { wt = w }
            let lab = 0.9 * max(q(wt, 5, 0), q(wt, 5, 1))
            if t <= 16 { trace.append(lab) }
            if let p = prev { move += abs(lab - p) }
            prev = lab
            let s = Int(floor(r() * 6))
            let a = r() < 0.5 ? 0 : 1
            let o = rbCst(s, a)
            let tg = o.r + (o.t ? 0 : 0.9 * max(q(wt, o.ns, 0), q(wt, o.ns, 1)))
            let e = tg - q(w, s, a)
            for (i, v) in phi(s).enumerated() { w[a][i] += 0.1 * e * v }
        }
        let qr = rbCq().qr
        return (trace, move, (0...5).map { abs(q(w, $0, 1) - qr[$0]) }.max()!)
    }
}

private func targetLab() -> RbLab {
    let lg = [("#f5c542", "Label"), ("#e5337a", "Error"), ("#6d5dfc", "Chosen period")]
    return RbLab(tabs: ["sync 1", "sync 10", "sync 100"], initialTab: 0) { opt in
        let cc = [1, 10, 100][opt]
        let rr = tgtRun(cc)
        return (0..<4).map { stp in
            switch stp {
            case 0:
                return fr([S(st("update", "Q(s,a) ← r + γ·max Q(s′,·)"), st("problem", "the label uses the weights being trained"))],
                          [F("label for Q(s4, right) = 0.9 · max Q(s5, ·)")],
                          ("Bootstrapping chases its own tail.", "Every update moves the weights, which moves the labels — with shared features, for every state at once."), lg)
            case 1:
                let mx = max(rr.trace.map { abs($0) }.max()!, 0.01)
                return fr([L("label for Q(s4, right), first 16 updates"), .cols(rr.trace.enumerated().map { i, v in
                    RbVCol(v: "", label: i % 5 == 0 ? "\(i + 1)" : "", lc: "#9aa0ae", bars: [RbVBar(h: abs(v) / mx * 58, bg: "#f5c542")])
                })], [F("target network synced every \(cc) update\(cc > 1 ? "s" : "")")],
                          (cc == 1 ? "Without a target network the label moves every update." : "A frozen copy holds the label still between syncs.",
                           cc == 1 ? "Each bar is a different target." : "Flat runs of \(cc) updates, then a step when the copy is refreshed."), lg)
            case 2:
                return fr([S(st("total label movement", f(rr.move, 3)), st("final error", f(rr.err, 3), "#e5337a"))],
                          [F("Σ |Δ label| over 3,000 updates =", f(rr.move, 3), "#f5c542")],
                          ("Stable labels, a real regression problem.", "Between syncs the network fits a fixed target, like supervised learning."), lg)
            default:
                return fr([L("label movement vs final error"), B([1, 10, 100].map { c in
                    let x = tgtRun(c)
                    return Rb.row("sync \(c)", "move \(f(x.move, 2)) · err \(f(x.err, 3))", x.move / max(tgtRun(1).move, 0.01), c == cc ? "#6d5dfc" : "#3a3f4c", c == cc ? "#f2f3f7" : "#9aa0ae")
                })], [F("DQN (2015) synced every 10,000 steps")],
                          ("Freeze too briefly: chasing. Too long: stale.", "The sync period trades stability against how fresh the bootstrapped labels are."), lg)
            }
        }
    }
}

// MARK: - 59d Double DQN: Sutton & Barto's maximisation-bias example

struct DdqRes { let pts: [(e: Int, left: Double, q: Double)]; let peak: Double }

let rbDdq: [DdqRes] = [false, true].map { dbl in
    let eps = [10, 50, 100, 300]
    var left = [Double](repeating: 0, count: 301)
    var qa = [Double](repeating: 0, count: 301)
    for run in 0..<100 {
        let r = Rb.rng(1000 + Int64(run) * 13)
        func g() -> Double { -0.1 + Rb.gauss(r) }
        var a1 = [0.0, 0.0]
        var a2 = [0.0, 0.0]
        var b1 = [Double](repeating: 0, count: 8)
        var b2 = [Double](repeating: 0, count: 8)
        for e in 1...300 {
            let q0 = a1[0] + (dbl ? a2[0] : 0)
            let q1 = a1[1] + (dbl ? a2[1] : 0)
            let a = r() < 0.1 ? (r() < 0.5 ? 0 : 1) : q0 == q1 ? (r() < 0.5 ? 0 : 1) : q0 > q1 ? 0 : 1
            if a == 1 {
                if dbl && r() < 0.5 { a2[1] += 0.1 * (0 - a2[1]) } else { a1[1] += 0.1 * (0 - a1[1]) }
            } else {
                left[e] += 1
                let qb = (0..<8).map { b1[$0] + (dbl ? b2[$0] : 0) }
                let b = r() < 0.1 ? Int(floor(r() * 8)) : Rb.argmax(qb)
                let rew = g()
                if !dbl {
                    a1[0] += 0.1 * (b1.max()! - a1[0])
                    b1[b] += 0.1 * (rew - b1[b])
                } else if r() < 0.5 {
                    a1[0] += 0.1 * (b2[Rb.argmax(b1)] - a1[0])
                    b1[b] += 0.1 * (rew - b1[b])
                } else {
                    a2[0] += 0.1 * (b1[Rb.argmax(b2)] - a2[0])
                    b2[b] += 0.1 * (rew - b2[b])
                }
            }
            qa[e] += dbl ? (a1[0] + a2[0]) / 2 : a1[0]
        }
    }
    return DdqRes(pts: eps.map { ($0, left[$0] / 100, qa[$0] / 100) }, peak: (1...300).map { qa[$0] / 100 }.max()!)
}

private func doubleLab() -> RbLab {
    let lg = [("#e5337a", "Q-learning"), ("#22a06b", "Double"), ("#e5484d", "True value")]
    return RbLab(tabs: ["Q-learning", "Double"], initialTab: 0) { dbl in
        let d = rbDdq
        let rr = d[dbl]
        return (0..<5).map { stp in
            if stp == 0 {
                return fr([S(st("right from A", "ends, reward 0"), st("left from A", "to B, then 8 actions"), st("each B action", "reward ~ N(−0.1, 1)"), st("true Q(A, left)", "−0.10", "#e5484d"))],
                          [F("optimal: always go right")],
                          ("A trap built from noise.", "Every action in B is slightly bad on average, but some will look good by chance."), lg)
            }
            if stp < 4 {
                let x = rr.pts[stp == 1 ? 0 : stp == 2 ? 2 : 3]
                return fr([B([Rb.brow("Q(A, left)", x.q, 0.3, f(x.q, 3)), Rb.row("P(choose left)", pct(x.left), x.left, "#e5337a")])],
                          [F(dbl == 1 ? "target: Q₂(B, argmax Q₁(B,·))" : "target: max_b Q(B, b)"), F("episode \(x.e), mean of 100 runs")],
                          dbl == 1 ? ("Episode \(x.e): Q(A, left) = \(f(x.q, 3)).", "One table picks the action, the other scores it — the lucky noise doesn’t get to grade itself.")
                              : ("Episode \(x.e): Q(A, left) = \(f(x.q, 3)), true −0.10.", "The max over 8 noisy estimates is biased upward, so left looks worth taking."), lg)
            }
            return fr([L("P(choose left) by episode"), B((0...1).flatMap { m in
                (0...3).map { i in Rb.row("\(m == 1 ? "Double" : "Q") · ep \(d[m].pts[i].e)", pct(d[m].pts[i].left), d[m].pts[i].left, m == 1 ? "#22a06b" : "#e5337a", m == dbl ? "#f2f3f7" : "#9aa0ae") }
            })], [F("peak Q(A, left): Q", f(d[0].peak, 3), "#e5337a"), F("peak Q(A, left): Double", f(d[1].peak, 3), "#22a06b")],
                      ("Double estimation removes the optimism.", "Double DQN applies the same trick with the online and target networks."), lg)
        }
    }
}

// MARK: - 59e Dueling DQN

private func duelingLab() -> RbLab {
    let lg = [("#3b82f6", "Q"), ("#6d5dfc", "V(s)"), ("#22a06b", "A(s, right)")]
    return RbLab(tabs: ["γ 0.9", "γ 0.99"], initialTab: 1) { opt in
        let g = [0.9, 0.99][opt]
        let cq = rbCq(g)
        let v = cq.qr.indices.map { (cq.qr[$0] + cq.ql[$0]) / 2 }
        let ar = cq.qr.indices.map { cq.qr[$0] - v[$0] }
        return (0..<4).map { stp in
            switch stp {
            case 0:
                return fr([L("Q(s, right) / Q(s, left)"), B((0...5).map { i in Rb.row("s\(i)", "\(f(cq.qr[i])) / \(f(cq.ql[i]))", cq.qr[i], "#3b82f6") })],
                          [F("γ = \(js(g))")],
                          ("Two numbers per state, nearly equal.", "Most of each Q is “how good is this state”; only a sliver is “which action”."), lg)
            case 1:
                return fr([L("V(s) = mean of the two Qs"), B(v.enumerated().map { i, x in Rb.row("s\(i)", f(x), x, "#6d5dfc") })],
                          [F("Q(s,a) = V(s) + A(s,a) − mean A")],
                          ("Dueling splits the head in two.", "One stream estimates V(s), the other the advantage of each action."), lg)
            case 2:
                return fr([L("A(s, right) = Q(s, right) − V(s)"), B(ar.enumerated().map { i, a in Rb.brow("s\(i)", a, 0.1, f(a, 3)) })],
                          [F("|A| / V at s0 =", pct(abs(ar[0]) / v[0]), "#22a06b")],
                          ("The choice is worth \(pct(abs(ar[0]) / v[0])) of the value.", g > 0.95 ? "At γ 0.99 the advantage is tiny next to V — easy to drown in noise if learned inside one number." : "Small, but it is the only part that decides the action."), lg)
            default:
                return fr([S(st("plain DQN", "an update to Q(s, right) leaves Q(s, left) unchanged"), st("dueling", "the same update also moves V(s), so both Qs improve"))],
                          [F("same parameters, better use of each sample")],
                          ("V is learned from every action.", "States where the action barely matters still teach the value stream."), lg)
            }
        }
    }
}

// MARK: - 59f Noisy Nets

struct NoisyRun { let ok: Double; let mean: Double; let vis: [Double] }

let rbNoisy: (eg: NoisyRun, nz: NoisyRun) = {
    let r = Rb.rng(31)
    func run(_ noisy: Bool) -> NoisyRun {
        var ok = 0
        var steps = 0
        var vis = [Int](repeating: 0, count: 6)
        for _ in 0..<2000 {
            let b = Rb.gauss(r)
            let n = (0..<6).map { _ in 0.3 * Rb.gauss(r) }
            var s = 0
            for t in 0..<100 {
                vis[s] += 1
                let a = noisy ? (b + n[s] > 0 ? 1 : 0) : (r() < 0.5 ? 1 : 0)
                let o = rbCst(s, a)
                if o.t { ok += 1; steps += t + 1; break }
                s = o.ns
            }
        }
        return NoisyRun(ok: Double(ok) / 2000, mean: ok > 0 ? Double(steps) / Double(ok) : 0, vis: vis.map { Double($0) / 2000 })
    }
    let eg = run(false)
    return (eg, run(true))
}()

private func noisyLab() -> RbLab {
    let lg = [("#9aa0ae", "ε-greedy"), ("#22a06b", "Noisy net")]
    return RbLab(tabs: ["ε-greedy", "noisy net"], initialTab: 1) { opt in
        let n = rbNoisy
        let rr = opt == 1 ? n.nz : n.eg
        return (0..<4).map { stp in
            switch stp {
            case 0:
                return fr([S(st("task", "reach past s5 from s0, 100-step limit"), st("ε-greedy (untrained)", "a fresh coin flip every step"), st("noisy net", "random weights drawn once per episode"))],
                          [F("2,000 episodes each")],
                          ("Exploration that dithers vs exploration that commits.", "A random walk needs ~42 steps to cross a 6-state chain; a consistent push needs 6."), lg)
            case 1:
                return fr([L("visits per episode, \(opt == 1 ? "noisy net" : "ε-greedy")"), B(rr.vis.enumerated().map { i, v in Rb.row("s\(i)", f(v, 1), v / 20, opt == 1 ? "#22a06b" : "#9aa0ae") })],
                          [F("reached goal =", pct(rr.ok), opt == 1 ? "#22a06b" : "#c3c7d1")],
                          opt == 1 ? ("Noisy: many episodes march straight through.", "When the drawn weights favour right, every state goes right.")
                              : ("ε-greedy: most time is spent near the start.", "Independent coin flips cancel out; deep states are rarely seen."), lg)
            case 2:
                return fr([S(st("reached goal", pct(rr.ok)), st("mean steps when reached", f(rr.mean, 1)))],
                          [F("mean steps =", f(rr.mean, 1), "#22a06b")],
                          ("Successful episodes take \(f(rr.mean, 1)) steps.", "Per-episode noise explores in a direction; per-step noise explores in place."), lg)
            default:
                return fr([B([
                    Rb.row("ε-greedy · reached", pct(n.eg.ok), n.eg.ok, "#9aa0ae"),
                    Rb.row("noisy · reached", pct(n.nz.ok), n.nz.ok, "#22a06b", "#f2f3f7"),
                    Rb.row("ε-greedy · steps", f(n.eg.mean, 1), n.eg.mean / 100, "#9aa0ae"),
                    Rb.row("noisy · steps", f(n.nz.mean, 1), n.nz.mean / 100, "#22a06b", "#f2f3f7"),
                ])], [F("noise σ is learned, so it shrinks where it stops helping")],
                          ("Noisy nets learn how much to explore.", "The noise scale is a trainable parameter — no ε schedule to tune."), lg)
            }
        }
    }
}

// MARK: - 59g Distributional RL (C51)

/// `z.toFixed(2).replace('0.','.').replace('-.','−.')`: −.75, .25, −1.00.
private func atomLabel(_ z: Double) -> String {
    var s = (z < 0 ? "-" : "") + Rb.fixed(abs(z), 2)
    if let r = s.range(of: "0.") { s.replaceSubrange(r, with: ".") }
    if let r = s.range(of: "-.") { s.replaceSubrange(r, with: "−.") }
    return s
}

private func c51Lab() -> RbLab {
    let lg = [("#e5337a", "Risky"), ("#22a06b", "Safe")]
    return RbLab(tabs: ["P(+1) 0.5", "0.65", "0.8"], initialTab: 0) { opt in
        let p = [0.5, 0.65, 0.8][opt]
        let atoms = [-1, -0.75, -0.5, -0.25, 0, 0.25, 0.5, 0.75, 1.0]
        let mean = 2 * p - 1
        let safe = 0.3
        let risky = atoms.map { $0 == -1 ? 1 - p : $0 == 1 ? p : 0 }
        let w = (0.5 - safe) / 0.25
        let safeD = atoms.indices.map { $0 == 5 ? w : $0 == 6 ? 1 - w : 0 }
        func cvar(_ dist: [Double]) -> Double {
            var m = 0.0
            var acc = 0.0
            var i = 0
            while i < 9 && acc < 0.25 {
                let take = min(dist[i], 0.25 - acc)
                m += take * atoms[i]
                acc += take
                i += 1
            }
            return m / 0.25
        }
        func vb(_ a: [Double], _ b: [Double]) -> RbBlock {
            .cols(atoms.enumerated().map { i, z in RbVCol(v: "", label: atomLabel(z), lc: "#9aa0ae", bars: [RbVBar(h: a[i] * 58, bg: "#e5337a"), RbVBar(h: b[i] * 58, bg: "#22a06b")]) })
        }
        return (0..<4).map { stp in
            switch stp {
            case 0:
                return fr([B([Rb.brow("risky: E[return]", mean, 1), Rb.brow("safe: E[return]", safe, 1)])],
                          [F("risky: ±1 with P(+1) = \(js(p))"), F("safe: always 0.30")],
                          ("DQN sees only these two numbers.", abs(mean - safe) < 0.01 ? "Equal expectations: a plain Q-network can’t tell them apart." : "It picks \(mean > safe ? "risky" : "safe") on the mean alone."), lg)
            case 1:
                return fr([L("P(return) on 9 atoms, −1 to +1"), vb(risky, safeD)], [F("C51 keeps a probability per atom")],
                          ("Same axis, different worlds.", "Risky is two spikes at the ends; safe is one lump in the middle."), lg)
            case 2:
                return fr([L("safe return 0.30 projected onto the grid"), vb([Double](repeating: 0, count: 9), safeD)],
                          [F("0.30 between 0.25 and 0.50 →", "\(f(w, 2)) / \(f(1 - w, 2))", "#22a06b")],
                          ("Returns off the grid are split between neighbours.", "The same projection is applied to r + γ·z after every Bellman backup."), lg)
            default:
                return fr([B([Rb.brow("risky · CVaR 25%", cvar(risky), 1, f(cvar(risky), 2)), Rb.brow("safe · CVaR 25%", cvar(safeD), 1, f(cvar(safeD), 2))])],
                          [F("mean of the worst 25% of outcomes")],
                          ("The distribution enables risk-aware choices.", "A cautious agent picks safe even when risky has the higher mean — impossible with a single Q value."), lg)
            }
        }
    }
}

// MARK: - 59h Rainbow

private func rainbowLab() -> RbLab {
    let lg = [("#f5c542", "This fix"), ("#22a06b", "Covered")]
    return RbLab(tabs: [], initialTab: 0) { _ in
        let d = rbDdq
        let n = rbNoisy
        let rows = [
            ("Double", "overestimation", "peak Q(A,left) \(f(d[0].peak, 3)) → \(f(d[1].peak, 3))"),
            ("Replay", "wasted samples", "error @200 steps \(f(chainErr(rbRepRun(0)[200]!), 2)) → \(f(chainErr(rbRepRun(16)[200]!), 2))"),
            ("Dueling", "relearning V per action", "V learned from every action"),
            ("Noisy", "dithering exploration", "goal reached \(pct(n.eg.ok)) → \(pct(n.nz.ok))"),
            ("C51", "averaging away risk", "full return distribution"),
            ("Multi-step", "slow reward propagation", "n-step targets"),
        ]
        return (0..<8).map { stp in
            let cur: Int? = (1...6).contains(stp) ? stp - 1 : nil
            var blocks = [L("one fix per failure, measured on this page"), C(rows.enumerated().map { i, r in
                Rb.tok(r.0, cur == i ? "cur" : cur != nil && i < cur! ? "done" : stp == 7 ? "done" : "fut", r.1)
            })]
            if let cur { blocks.append(S(st(rows[cur].0, rows[cur].2, "#22a06b"))) }
            let fx = cur != nil ? [F(rows[cur!].1 + ":", rows[cur!].2)] : [F("Rainbow = DQN + all six")]
            let cap: (String, String) = stp == 0 ? ("Rainbow combines six independent fixes.", "Each targets a different failure of plain DQN — the previous screens measured most of them.")
                : stp == 7 ? ("Not all fixes matter equally.", "In the Rainbow paper’s ablations, prioritized replay and multi-step returns were the most important.")
                : ("\(rows[cur!].0) fixes \(rows[cur!].1).", rows[cur!].2 + ".")
            return fr(blocks, fx, cap, lg)
        }
    }
}
