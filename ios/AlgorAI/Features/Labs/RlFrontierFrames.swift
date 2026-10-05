import Foundation

// Port of RlFrontierFrames.kt: one slippery corridor for behaviour cloning, DAgger, MaxEnt IRL and
// tabular GAIL, all with exact occupancies; a logged chain with a zero-value lottery for fitted
// Q-iteration and CQL; a return-conditioned policy on mixed data; and a meta-learned start for a family
// of goals. Drawn by RlBoardLabs.swift.

let rlFrontierTopicIds: Set<String> = ["imitation_learning", "irl", "gail", "offline_rl", "cql", "decision_transformer", "meta_rl"]

func rlFrontierLab(_ topicId: String) -> RbLab? {
    switch topicId {
    case "imitation_learning": imitationLab()
    case "irl": irlLab()
    case "gail": gailLab()
    case "offline_rl": offlineLab()
    case "cql": cqlLab()
    case "decision_transformer": dtLab()
    case "meta_rl": metaLab()
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

// MARK: - The corridor: 3 × 8, start cell 8 (r1 c0), goal 15 (r1 c7); a move slips up or down with p

private func cmv(_ i: Int, _ a: Int) -> Int {
    let r = i / 8 + RbGw.AC[a].0
    let c = i % 8 + RbGw.AC[a].1
    return r < 0 || r > 2 || c < 0 || c > 7 ? i : r * 8 + c
}

private let ctrCache = RbCache<Double, [[[(Int, Double)]]]>()

/// Next-cell distribution for (i, a) at slip p, targets ascending as the design's Object.entries gives them.
private func ctr(_ p: Double) -> [[[(Int, Double)]]] {
    ctrCache.get(p) {
        (0..<24).map { i in
            (0..<4).map { a in
                if i == 15 { return [(15, 1.0)] }
                var keys: [Int] = []
                var o: [Int: Double] = [:]
                func add(_ j: Int, _ w: Double) { if o[j] == nil { keys.append(j) }; o[j, default: 0] += w }
                add(cmv(i, a), 1 - p)
                add(cmv(i, 0), p / 2)
                add(cmv(i, 1), p / 2)
                return keys.sorted().map { ($0, o[$0]!) }
            }
        }
    }
}

/// The expert: down from the top row, up from the bottom row, right along the middle.
private func cExp(_ i: Int) -> Int { switch i / 8 { case 0: 1; case 2: 0; default: 3 } }

private func one(_ a: Int) -> [Double] { (0..<4).map { $0 == a ? 1 : 0 } }

private struct Occ { let sv: [Double]; let rho: [[Double]]; let succ: Double; let off: [Double] }

private func occ(_ pol: (Int) -> [Double], _ p: Double, _ start: Int = 8) -> Occ {
    let t = ctr(p)
    var d = [Double](repeating: 0, count: 24)
    d[start] = 1
    var sv = [Double](repeating: 0, count: 24)
    var rho = [[Double]](repeating: [0, 0, 0, 0], count: 24)
    var off: [Double] = []
    for _ in 0..<15 {
        var n = [Double](repeating: 0, count: 24)
        for i in 0..<24 where d[i] != 0 {
            sv[i] += d[i]
            if i == 15 { n[15] += d[i]; continue }
            let pr = pol(i)
            for a in 0..<4 where pr[a] != 0 {
                rho[i][a] += d[i] * pr[a]
                for (j, w) in t[i][a] { n[j] += d[i] * pr[a] * w }
            }
        }
        d = n
        var s = 0.0
        for i in 0..<24 { s += i / 8 != 1 ? d[i] : 0 }
        off.append(s)
    }
    return Occ(sv: sv, rho: rho, succ: d[15], off: off)
}

private struct CCell { var t: String? = nil; var bg: String? = nil; var ring: String? = nil }

private func cgrid(_ fn: (Int) -> CCell) -> RbBlock {
    .grid(RbGrid(ch: 34, cols: (0...7).map { "c\($0)" }, rows: (0...2).map { r in
        RbGRow(label: "r\(r)", lc: "#9aa0ae", cells: (0...7).map { c in
            let i = r * 8 + c
            let o = fn(i)
            return RbCell(t: o.t ?? (i == 8 ? "S" : i == 15 ? "G" : ""), bg: o.bg ?? "#1f232d", color: "#fff", ring: o.ring ?? (i == 15 ? "inset 0 0 0 2px #22a06b" : "none"))
        })
    }))
}

private func heat(_ v: Double, _ mx: Double, _ rgb: String) -> String { v > 1e-3 ? "rgba(\(rgb),\(Rb.a2(0.12 + 0.8 * min(1, v / mx))))" : "#1f232d" }

private func maxOff15(_ sv: [Double]) -> Double { sv.indices.filter { $0 != 15 }.map { sv[$0] }.max()! }

private func occGrid(_ sv: [Double], _ rgb: String) -> RbBlock {
    let mx = maxOff15(sv)
    return cgrid { i in CCell(t: i == 15 ? "G" : (i == 8 ? "S " : "") + (sv[i] > 0.005 ? f(sv[i], 1) : ""), bg: i == 15 ? "#1f232d" : heat(sv[i], mx, rgb)) }
}

private let dagCache = RbCache<Double, [(cov: Int, succ: Double)]>()

private func dagger(_ p: Double) -> [(cov: Int, succ: Double)] {
    dagCache.get(p) {
        var set: Set<Int> = [8, 9, 10, 11, 12, 13, 14]
        var its: [(cov: Int, succ: Double)] = []
        for _ in 0...4 {
            let snap = set
            let o = occ({ i in one(snap.contains(i) ? cExp(i) : 3) }, p)
            its.append((set.count, o.succ))
            for (i, v) in o.sv.enumerated() where v > 0.01 && i != 15 { set.insert(i) }
        }
        return its
    }
}

private let expertPi: (Int) -> [Double] = { one(cExp($0)) }
private let clonePi: (Int) -> [Double] = { _ in one(3) }

// MARK: - 62a Imitation Learning: BC vs DAgger

private func imitationLab() -> RbLab {
    let lg = [("#3b82f6", "Demonstrated"), ("#22a06b", "Expert / DAgger"), ("#e5337a", "Off-data")]
    func demo(_ i: Int) -> Bool { (8...14).contains(i) }
    return RbLab(tabs: ["slip 10%", "slip 20%", "slip 30%"], initialTab: 1) { opt in
        let p = [0.1, 0.2, 0.3][opt]
        let clone = occ(clonePi, p)
        let ex = occ(expertPi, p)
        return (0..<5).map { stp in
            switch stp {
            case 0:
                return fr([L("states the demonstrations cover"), cgrid { i in demo(i) ? CCell(t: i == 8 ? "S →" : "→", bg: "#3b82f6") : CCell(t: i == 15 ? "G" : "·") }],
                          [F("demonstrated =", "7 of 24 states", "#3b82f6"), F("fallback off-data = majority label =", "→")],
                          ("Clean demonstrations: the expert never slips.", "So the clone only ever sees the 7 states along the middle row, all labelled →."), lg)
            case 1:
                let mx = maxOff15(clone.sv)
                return fr([L("clone’s expected visits per cell · slip \(pct(p))"), cgrid { i in
                    let v = clone.sv[i]
                    return CCell(t: i == 15 ? "G" : i == 8 ? "S" : v > 0.005 ? f(v, 1) : "", bg: i == 15 ? "#1f232d" : heat(v, mx, demo(i) ? "59,130,246" : "229,51,122"))
                }], [F("P(reach G in 15 steps) · clone", pct(clone.succ), "#e5337a"), F("expert, same slips", pct(ex.succ), "#22a06b")],
                          ("One slip and the clone is lost.", "Off the middle row it has no label, so it plays → along the wrong row and pins itself against the far wall."), lg)
            case 2:
                return fr([L("probability mass off the demonstrated row, by step"), B([2, 5, 8, 11, 14].map { t in Rb.row("step \(t + 1)", pct(clone.off[t]), clone.off[t], "#e5337a") })],
                          [F("expert off-row at step 15 =", pct(ex.off[14]), "#22a06b"), F("clone off-row at step 15 =", pct(clone.off[14]), "#e5337a")],
                          ("Errors compound instead of averaging out.", "Every slip moves mass into states the clone never trained on, and nothing brings it back: \(pct(clone.off[14])) is off-row by step 15."), lg)
            case 3:
                let d = dagger(p)
                return fr([L("DAgger: roll out, ask the expert, retrain"), B(d.enumerated().map { k, x in Rb.row("iter \(k)", pct(x.succ) + " · " + "\(x.cov)", x.succ, k > 0 ? "#22a06b" : "#9aa0ae", k == d.count - 1 ? "#f2f3f7" : nil) })],
                          [F("states labelled ·", "7 → \(d.last!.cov)", "#22a06b"), F("success ·", pct(d[0].succ) + " → " + pct(d.last!.succ), "#22a06b")],
                          ("DAgger labels the states the learner actually reaches.", "After \(d.count - 1) rounds it knows \(d.last!.cov) states and matches the expert’s \(pct(ex.succ))."), lg)
            default:
                var rows: [RbRow] = []
                for x in [0.1, 0.2, 0.3] {
                    let c = occ(clonePi, x).succ
                    let d = dagger(x)[4].succ
                    rows.append(Rb.row("slip \(pct(x)) · BC", pct(c), c, "#e5337a", x == p ? "#f2f3f7" : "#9aa0ae"))
                    rows.append(Rb.row("slip \(pct(x)) · DAgger", pct(d), d, "#22a06b", x == p ? "#f2f3f7" : "#9aa0ae"))
                }
                return fr([L("P(reach G in 15 steps)"), B(rows)], [F("BC error grows ~ εT², DAgger ~ εT")],
                          ("Coverage, not accuracy, is the problem.", "The clone is 100% accurate on every state it saw; it fails on the ones it didn’t."), lg)
            }
        }
    }
}

// MARK: - 62b IRL: MaxEnt reward recovery

private func softpi(_ r: [Double], _ p: Double) -> [[Double]] {
    let t = ctr(p)
    var v = [Double](repeating: 0, count: 24)
    var q = [[Double]](repeating: [0, 0, 0, 0], count: 24)
    for _ in 0..<80 {
        let vv = v
        q = (0..<24).map { i in (0..<4).map { a in r[i] + 0.9 * t[i][a].reduce(0.0) { $0 + $1.1 * vv[$1.0] } } }
        v = (0..<24).map { i in let m = q[i].max()!; return m + log(q[i].reduce(0.0) { $0 + exp($1 - m) }) }
    }
    return (0..<24).map { i in let m = q[i].max()!; let e = q[i].map { exp($0 - m) }; let s = e.reduce(0, +); return e.map { $0 / s } }
}

private struct Irl { let e: Occ; let sn: [Int: (r: [Double], mis: Double, succ: Double)]; let greedy: [Int] }

private let irlCache = RbCache<Double, Irl>()

private func irl(_ p: Double) -> Irl {
    irlCache.get(p) {
        let e = occ(expertPi, p)
        var r = [Double](repeating: 0, count: 24)
        var sn: [Int: (r: [Double], mis: Double, succ: Double)] = [:]
        for iter in 0...150 {
            let pi = softpi(r, p)
            let l = occ({ pi[$0] }, p)
            var mis = 0.0
            for i in 0..<24 { mis += abs(l.sv[i] - e.sv[i]) }
            mis /= 15
            if [0, 5, 20, 50, 150].contains(iter) { sn[iter] = (r, mis, l.succ) }
            r = (0..<24).map { r[$0] + 0.2 * (e.sv[$0] - l.sv[$0]) }
        }
        let pi = softpi(sn[150]!.r, p)
        return Irl(e: e, sn: sn, greedy: (0..<24).map { Rb.argmax(pi[$0]) })
    }
}

private func rgrid(_ r: [Double]) -> RbBlock {
    let m = r.map { abs($0) }.max()!
    let mx = m == 0 ? 1 : m
    return cgrid { i in
        CCell(t: (i == 8 ? "S " : i == 15 ? "G " : "") + f(r[i], 1),
              bg: abs(r[i]) < 0.05 ? "#1f232d" : r[i] > 0 ? "rgba(109,93,252,\(Rb.a2(0.15 + 0.8 * r[i] / mx)))" : "rgba(229,51,122,\(Rb.a2(0.15 + 0.8 * -r[i] / mx)))")
    }
}

private func irlLab() -> RbLab {
    let lg = [("#6d5dfc", "Recovered reward"), ("#3b82f6", "Visitation"), ("#e5337a", "Mismatch / BC")]
    return RbLab(tabs: ["slip 10%", "slip 20%", "slip 30%"], initialTab: 1) { opt in
        let p = [0.1, 0.2, 0.3][opt]
        let ir = irl(p)
        return (0..<5).map { stp in
            if stp == 0 {
                return fr([L("expert’s expected visits per cell · slip \(pct(p))"), occGrid(ir.e.sv, "59,130,246")], [F("IRL matches this, not the actions")],
                          ("IRL asks what objective makes these actions optimal.", "The only statistic it matches is expected state visitation — how long the expert spends where."), lg)
            }
            if stp < 3 {
                let it = stp == 1 ? 5 : 150
                let s = ir.sn[it]!
                return fr([L("recovered reward r(s) after \(it) iterations"), rgrid(s.r)],
                          [F("r ← r + 0.2 · (μ_expert − μ_learner)"), F("visitation mismatch =", f(s.mis, 3), s.mis < 0.1 ? "#22a06b" : "#e5337a")],
                          stp == 1 ? ("Early on, reward is piling up where the expert lingers.", "States the learner over-visits are pushed down; the middle row and G are pushed up.")
                              : ("After 150 iterations the reward explains the demos.", "Its soft-optimal policy reaches G \(pct(s.succ)) of the time — the expert’s rate under the same slips."), lg)
            }
            if stp == 3 {
                let m0 = ir.sn[0]!.mis
                return fr([L("visitation mismatch ‖μ_E − μ_π‖₁ / T"), B([0, 5, 20, 50, 150].map { k in Rb.row("iter \(k)", f(ir.sn[k]!.mis, 3), ir.sn[k]!.mis / m0, k == 150 ? "#22a06b" : "#6d5dfc") })],
                          [F("MaxEnt gradient = expert features − learner features")],
                          ("The mismatch falls from \(f(m0, 2)) to \(f(ir.sn[150]!.mis, 3)).", "Matching visitation is enough to reproduce behaviour without ever copying an action."), lg)
            }
            var rows: [RbRow] = []
            for x in [0, 16] {
                let lab = x == 0 ? "start r0" : "start r2"
                let irs = occ({ one(ir.greedy[$0]) }, p, x).succ
                let b = occ(clonePi, p, x).succ
                rows.append(Rb.row("\(lab) · IRL", pct(irs), irs, "#6d5dfc", "#f2f3f7"))
                rows.append(Rb.row("\(lab) · BC", pct(b), b, "#e5337a"))
            }
            return fr([L("new start cells, never demonstrated"), B(rows)], [F("expert from r0 =", pct(occ(expertPi, p, 0).succ), "#22a06b")],
                      ("A reward transfers; a copied policy doesn’t.", "Started off the demonstrated row, the policy planned on the recovered reward still heads for G; the clone drifts along the wrong row."), lg)
        }
    }
}

// MARK: - 62c GAIL: occupancy matching

private struct Gail { let e: Occ; let sn: [Int: (tv: Double, succ: Double, sv: [Double], rw: [Double])] }

private let gailCache = RbCache<Double, Gail>()

private func gail(_ p: Double) -> Gail {
    gailCache.get(p) {
        let t = ctr(p)
        let e = occ(expertPi, p)
        var pi = [[Double]](repeating: one(3), count: 24)
        var sn: [Int: (tv: Double, succ: Double, sv: [Double], rw: [Double])] = [:]
        for iter in 0...30 {
            let cur = pi
            let pp = occ({ cur[$0] }, p)
            var tv = 0.0
            for i in 0..<24 { for a in 0..<4 { tv += abs(e.rho[i][a] - pp.rho[i][a]) / 15 / 2 } }
            let rw = (0..<24).map { i in (0..<4).map { a in log((e.rho[i][a] + 1e-3) / (pp.rho[i][a] + 1e-3)) } }
            if [0, 1, 2, 5, 10, 30].contains(iter) { sn[iter] = (tv, pp.succ, pp.sv, rw.map { $0.max()! }) }
            var v = [Double](repeating: 0, count: 24)
            var q = [[Double]](repeating: [0, 0, 0, 0], count: 24)
            for _ in 0..<40 {
                let vv = v
                func backup(_ i: Int, _ a: Int) -> Double {
                    if i == 15 { return 0 }
                    var s = 0.0
                    for (j, w) in t[i][a] { s += w * vv[j] }
                    return rw[i][a] + 0.95 * s
                }
                q = (0..<24).map { i in (0..<4).map { a in backup(i, a) } }
                v = (0..<24).map { i -> Double in i == 15 ? 0 : log(q[i].reduce(0.0) { $0 + exp($1) }) }
            }
            let np = (0..<24).map { i -> [Double] in let m = q[i].max()!; let ex = q[i].map { exp($0 - m) }; let s = ex.reduce(0, +); return ex.map { $0 / s } }
            pi = (0..<24).map { i in (0..<4).map { a in 0.5 * cur[i][a] + 0.5 * np[i][a] } }
        }
        return Gail(e: e, sn: sn)
    }
}

private func gailLab() -> RbLab {
    let lg = [("#22a06b", "Expert / GAIL"), ("#e5337a", "Clone / penalty"), ("#6d5dfc", "Reward")]
    return RbLab(tabs: ["slip 10%", "slip 20%", "slip 30%"], initialTab: 1) { opt in
        let p = [0.1, 0.2, 0.3][opt]
        let g = gail(p)
        let s0 = g.sn[0]!
        let s30 = g.sn[30]!
        return (0..<5).map { stp in
            switch stp {
            case 0:
                return fr([L("expert occupancy · expected visits"), occGrid(g.e.sv, "34,160,107")],
                          [F("TV(expert, clone) =", f(s0.tv, 3), "#e5337a"), F("expert reaches G", pct(g.e.succ))],
                          ("GAIL matches distributions, not actions.", "The expert recovers from slips, so its occupancy spills into the outer rows and comes back."), lg)
            case 1:
                return fr([L("clone occupancy · iteration 0"), occGrid(s0.sv, "229,51,122")],
                          [F("TV(expert, clone) =", f(s0.tv, 3), "#e5337a"), F("clone reaches G", pct(s0.succ), "#e5337a")],
                          ("The clone’s occupancy is in the wrong places.", "It is \(f(s0.tv, 3)) away in total variation — that distance, not per-state accuracy, is what its \(pct(s0.succ)) success reflects."), lg)
            case 2:
                return fr([L("discriminator reward, best action per cell"), rgrid(s0.rw)], [F("D = ρ_E / (ρ_E + ρ_π) · r = log D − log(1 − D)")],
                          ("The discriminator becomes the reward.", "Outer-row moves back to the middle score high (the expert does them); pressing on along a wrong row scores low."), lg)
            case 3:
                return fr([L("TV(expert, policy) by GAIL iteration"), B([0, 1, 2, 5, 10, 30].map { k in
                    let x = g.sn[k]!
                    return Rb.row("iter \(k)", f(x.tv, 3) + " · " + pct(x.succ), x.tv / 0.6, k == 30 ? "#22a06b" : "#e5337a", k == 30 ? "#f2f3f7" : nil)
                })], [F("label = TV · success")],
                          ("TV falls from \(f(s0.tv, 3)) to \(f(s30.tv, 3)).", g.sn[1]!.tv > s0.tv ? "The first update overshoots — the policy flees the penalised states — then settles." : "Each round the policy moves toward the states the discriminator rewards."), lg)
            default:
                return fr([L("GAIL policy occupancy · iteration 30"), occGrid(s30.sv, "34,160,107")],
                          [F("success ·", pct(s0.succ) + " → " + pct(s30.succ), "#22a06b"), F("expert =", pct(g.e.succ))],
                          ("The policy learns to recover.", "Success rises from \(pct(s0.succ)) to \(pct(s30.succ)) (expert \(pct(g.e.succ))) without one labelled off-row state; the policy stays soft, which costs some steps."), lg)
            }
        }
    }
}

// MARK: - The logged chain: right walks on (reward 1 off s4), left walks back, gamble pays ±5 and ends

private struct Logged { let s: Int; let a: Int; let rw: Double; let ns: Int; let done: Bool }

private let cdCache = RbCache<Int, [Logged]>()

private func chainData(_ n: Int) -> [Logged] {
    cdCache.get(n) {
        let r = Rb.rng(3)
        var d: [Logged] = []
        for _ in 0..<n {
            var s = 0
            for _ in 0..<20 {
                let u = r()
                let a = u < 0.6 ? 1 : u < 0.9 ? 0 : 2
                var rw = 0.0
                var ns = -1
                var done = false
                if a == 2 { rw = r() < 0.5 ? 5 : -5; done = true } else if a == 1 { if s == 4 { rw = 1; done = true } else { ns = s + 1 } } else { ns = max(0, s - 1) }
                d.append(Logged(s: s, a: a, rw: rw, ns: ns, done: done))
                if done { break }
                s = ns
            }
        }
        return d
    }
}

private func chainEval(_ pol: [Int]) -> Double {
    var v = [Double](repeating: 0, count: 5)
    for _ in 0..<300 {
        let o = v
        v = (0..<5).map { s in pol[s] == 2 ? 0 : pol[s] == 1 ? (s == 4 ? 1 : 0.9 * o[s + 1]) : 0.9 * o[max(0, s - 1)] }
    }
    return v[0]
}

private let chainBeh: Double = {
    var v = [Double](repeating: 0, count: 5)
    for _ in 0..<300 {
        let o = v
        v = (0..<5).map { s in 0.6 * (s == 4 ? 1 : 0.9 * o[s + 1]) + 0.3 * 0.9 * o[max(0, s - 1)] }
    }
    return v[0]
}()

private let chainOpt = chainEval([1, 1, 1, 1, 1])

private struct Fqi { let q: [[Double]]; let n: [[Int]]; let r: [[Double]]; let pol: [Int]; let ret: Double }

private let cfCache = RbCache<String, Fqi>()

private func cfqi(_ d: [Logged], _ alpha: Double) -> Fqi {
    cfCache.get("\(d.count)_\(alpha)") {
        var n = [[Int]](repeating: [0, 0, 0], count: 5)
        var r = [[Double]](repeating: [0, 0, 0], count: 5)
        // Next-state counts per (s, a); the terminal (the design's 'T') is not stored, as it is skipped.
        var t = [[[Int: Int]]](repeating: [[:], [:], [:]], count: 5)
        for x in d {
            n[x.s][x.a] += 1
            r[x.s][x.a] += x.rw
            if !x.done { t[x.s][x.a][x.ns, default: 0] += 1 }
        }
        let tk = t.map { $0.map { $0.keys.sorted() } }
        var q = [[Double]](repeating: [0, 0, 0], count: 5)
        for _ in 0..<3000 {
            let o = q
            q = (0..<5).map { s in
                (0..<3).map { a in
                    if n[s][a] == 0 { return 0 }
                    var v = r[s][a] / Double(n[s][a])
                    for k in tk[s][a] { v += 0.9 * Double(t[s][a][k]!) / Double(n[s][a]) * o[k].max()! }
                    if alpha != 0 {
                        let mx = o[s].max()!
                        let e = o[s].map { exp($0 - mx) }
                        let sum = e.reduce(0, +)
                        v -= alpha * (e[a] / sum / (Double(n[s][a]) / Double(n[s].reduce(0, +))) - 1)
                    }
                    return o[s][a] + 0.05 * (v - o[s][a])
                }
            }
        }
        let pol = q.map { Rb.argmax($0) }
        return Fqi(q: q, n: n, r: r, pol: pol, ret: chainEval(pol))
    }
}

private let AN = ["left", "right", "gamble"]

private func polChips(_ fq: Fqi) -> RbBlock {
    C(fq.q.enumerated().map { i, q in Rb.tok("s\(i)", fq.pol[i] == 2 ? "err" : fq.pol[i] == 1 ? "done" : "plain", AN[fq.pol[i]] + " " + f(q.max()!)) })
}

private func polText(_ pol: [Int]) -> String { pol.map { ["←", "→", "G"][$0] }.joined(separator: " ") }

// MARK: - 62d Offline RL

private func offlineLab() -> RbLab {
    let lg = [("#9aa0ae", "Behaviour"), ("#22a06b", "Right / optimal"), ("#e5337a", "Lottery overrated")]
    return RbLab(tabs: ["small log", "4× log", "16× log"], initialTab: 0) { opt in
        let nn = [60, 240, 960][opt]
        let d = chainData(nn)
        let fq = cfqi(d, 0)
        let beh = chainBeh
        let best = chainOpt
        return (0..<5).map { stp in
            switch stp {
            case 0:
                return fr([L("transitions logged per state and action"), .grid(RbGrid(ch: 26, cols: AN, rows: fq.n.enumerated().map { i, r in
                    RbGRow(label: "s\(i)", lc: "#9aa0ae", cells: r.enumerated().map { a, v in RbCell(t: "\(v)", bg: a == 2 ? "rgba(229,51,122,.22)" : "#1f232d", color: a == 2 ? "#ff8a8d" : "#f2f3f7") })
                }))], [F("behaviour policy scores", f(beh, 3)), F("best possible (always right) =", f(best, 3), "#22a06b")],
                          ("Offline RL starts from someone else’s log.", "\(d.count) transitions from a policy that mostly walks right, dithers, and occasionally pulls a ±5 lottery worth exactly 0. No more interaction."), lg)
            case 1:
                let mx = (0..<5).map { i in fq.n[i][2] > 0 ? fq.r[i][2] / Double(fq.n[i][2]) : -9 }.max()!
                return fr([L("empirical mean reward of gamble · true = 0"), B(fq.n.enumerated().map { i, r in Rb.brow("s\(i) · n \(r[2])", r[2] > 0 ? fq.r[i][2] / Double(r[2]) : 0, 5) })],
                          [F("largest estimate =", f(mx), "#e5337a"), F("standard error with n = 5 ≈ 5/√5 =", "2.24")],
                          ("A handful of lottery pulls per state is pure noise.", "With so few samples, at least one state’s estimate is bound to look like a jackpot."), lg)
            case 2:
                let gi = fq.pol.firstIndex(of: 2) ?? -1
                let toG = (0...4).filter { x in
                    var c = x
                    for _ in 0..<10 {
                        if fq.pol[c] == 2 { return true }
                        if fq.pol[c] == 1 && c == 4 { return false }
                        c = fq.pol[c] == 1 ? c + 1 : max(0, c - 1)
                    }
                    return false
                }.count
                let has = fq.pol.contains(2)
                return fr([L("fitted Q-iteration on the log · greedy action per state"), polChips(fq), B(fq.q.enumerated().map { i, q in Rb.brow("Q(s\(i), gamble)", q[2], 3) })],
                          [F("Q(s,a) ← r̂ + 0.9 · max Q(s′, ·)"), F("greedy policy =", polText(fq.pol), has ? "#e5337a" : "#22a06b")],
                          has ? ("The max picks up the noise.", "FQI trusts a \(f(fq.q[gi][2])) lottery estimate at s\(gi) built from \(fq.n[gi][2]) pulls; from \(toG) of 5 states its greedy policy heads there.")
                              : ("With this much data the noise averages out.", "Every state’s greedy action is right."), lg)
            case 3:
                let worse = fq.ret < beh
                return fr([L("return from s0 when deployed (γ = 0.9)"), B([
                    Rb.row("behaviour", f(beh, 3), beh / best, "#9aa0ae"),
                    Rb.row("FQI greedy", f(fq.ret, 3), fq.ret / best, worse ? "#e5337a" : "#22a06b", "#f2f3f7"),
                    Rb.row("optimal", f(best, 3), 1, "#22a06b"),
                ])], [F("deployed return =", f(fq.ret, 3), worse ? "#e5337a" : "#22a06b")],
                          worse ? ("The “improved” policy is worse than the log.", "It scores \(f(fq.ret, 3)) against the behaviour policy’s \(f(beh, 3)) — distribution shift in one step.")
                              : ("Here FQI recovers the optimum.", "Enough data per action makes plain offline Q-learning safe."), lg)
            default:
                return fr([L("FQI deployed return by log size"), B([60, 240, 960].map { x in
                    let o = cfqi(chainData(x), 0)
                    return Rb.row("\(chainData(x).count) transitions", f(o.ret, 3), o.ret / best, o.ret > 0.5 ? "#22a06b" : "#e5337a", x == nn ? "#f2f3f7" : "#9aa0ae")
                })], [F("optimum =", f(best, 3), "#22a06b")],
                          ("Only a lot more data fixes plain FQI.", "Rare actions stay rare in any log; offline methods instead refuse to trust them — next, CQL."), lg)
            }
        }
    }
}

// MARK: - 62e CQL

private func cqlLab() -> RbLab {
    let lg = [("#3b82f6", "Q value"), ("#22a06b", "Right / optimum"), ("#e5337a", "Lottery chosen")]
    return RbLab(tabs: ["α 0", "α 0.3", "α 1"], initialTab: 2) { opt in
        let al = [0, 0.3, 1.0][opt]
        let d = chainData(60)
        let f0 = cfqi(d, 0)
        let cq = cfqi(d, al)
        let best = chainOpt
        let beh = chainBeh
        return (0..<5).map { stp in
            switch stp {
            case 0:
                let gap = f0.q.map { $0[2] }.max()!
                return fr([L("learned Q(s, gamble) without CQL · true = 0"), B(f0.q.enumerated().map { i, q in Rb.brow("s\(i)", q[2], 3) })],
                          [F("deployed return", f(f0.ret, 3) + " vs optimum " + f(best, 3), "#e5337a")],
                          ("Plain FQI overrates the rarely pulled lottery by up to \(f(gap)).", "CQL’s fix is not to estimate it better — it refuses to trust it."), lg)
            case 1:
                let n0 = f0.n[0]
                let pb = Double(n0[2]) / Double(n0.reduce(0, +))
                let q = cq.q[0]
                let mx = q.max()!
                let e = q.map { exp($0 - mx) }
                let mu = e[2] / e.reduce(0, +)
                return fr([S(st("π̂β(gamble | s0)", pct(pb) + " of logged actions"), st("μ(gamble | s0)", pct(mu) + " under softmax Q"), st("penalty at α = \(js(al))", f(al * (mu / pb - 1), 3), al != 0 ? "#e5337a" : "#9aa0ae"))],
                          [F("Q ← B̂Q − α·(μ(a|s) / π̂β(a|s) − 1)")],
                          ("Rare actions pay a penalty; logged actions get a boost.", "The ratio μ/π̂β is large exactly where the data is thin, so that is where Q is pushed down."), lg)
            case 2:
                let allRight = cq.pol.allSatisfy { $0 == 1 }
                let cap: (String, String) = allRight ? ("Walking right wins everywhere.", "The lottery estimates are pulled toward 0 and below; nothing out-of-data looks attractive any more.")
                    : al != 0 ? ("α = \(js(al)) is not enough here.", "The noisiest estimate still beats walking right in some states.")
                    : ("α = 0 is plain FQI.", "The lottery wins wherever its noisy estimate is highest.")
                return fr([L("CQL α = \(js(al)) · greedy action per state"), polChips(cq), B(cq.q.enumerated().map { i, q in Rb.brow("Q(s\(i), gamble)", q[2], 3) })],
                          [F("greedy policy =", polText(cq.pol), allRight ? "#22a06b" : "#e5337a")], cap, lg)
            case 3:
                return fr([L("deployed return from s0 · same 397-step log"), B([0, 0.3, 1, 3].map { x in
                    let o = cfqi(d, x)
                    return Rb.row("α \(js(x))", f(o.ret, 3), o.ret / best, o.ret > 0.5 ? "#22a06b" : "#e5337a", x == al ? "#f2f3f7" : "#9aa0ae")
                })], [F("behaviour =", f(beh, 3)), F("optimum =", f(best, 3), "#22a06b")],
                          ("A little pessimism recovers the optimum.", "Same log, same estimator; only the penalty on unsupported actions changed."), lg)
            default:
                return fr([S(st("α too small", "extrapolation error wins"), st("α right", "best supported action", "#22a06b"), st("α huge", "collapses to behaviour cloning"))],
                          [F("CQL learns a lower bound on the policy’s value")],
                          ("Conservatism is a dial.", "Too little trusts the noise; too much copies the behaviour policy and can’t improve on it."), lg)
            }
        }
    }
}

// MARK: - 62f Decision Transformer: a return-conditioned policy on a 7-cell corridor

private struct DtStep { let s: Int; let a: Int; let r: Double; var rtg = 0.0 }

private func dtEnv(_ s: Int, _ a: Int) -> (n: Int, r: Double, done: Bool) {
    let n = max(0, min(6, s + (a == 1 ? 1 : -1)))
    return (n, n == 6 ? 1 : -0.05, n == 6)
}

private let dtData: (t: [(st: [DtStep], ret: Double)], samples: [DtStep], rets: [Double]) = {
    let r = Rb.rng(11)
    var t: [(st: [DtStep], ret: Double)] = []
    for _ in 0..<250 {
        let pr = 0.15 + 0.6 * r()
        var s = 0
        var st: [DtStep] = []
        for _ in 0..<25 {
            let a = r() < pr ? 1 : 0
            let o = dtEnv(s, a)
            st.append(DtStep(s: s, a: a, r: o.r))
            s = o.n
            if o.done { break }
        }
        var g = 0.0
        for i in stride(from: st.count - 1, through: 0, by: -1) { g += st[i].r; st[i].rtg = g }
        t.append((st, st[0].rtg))
    }
    return (t, t.flatMap(\.st), t.map(\.ret))
}()

/// A policy sees the state and the reward it just got (nil on the first step).
private func dtRun(_ pol: (Int, Double?) -> Int) -> (r: Double, t: Int, ok: Bool, path: [Int]) {
    var s = 0
    var r = 0.0
    var last: Double? = nil
    var path = [0]
    for t in 0..<25 {
        let a = pol(s, last)
        let o = dtEnv(s, a)
        r += o.r
        last = o.r
        s = o.n
        path.append(s)
        if o.done { return (r, t + 1, true, path) }
    }
    return (r, 25, false, path)
}

private func dtPol(_ target: Double) -> (Int, Double?) -> Int {
    let samples = dtData.samples
    var rr = target
    return { s, last in
        if let last { rr -= last }
        let c = samples.filter { $0.s == s }.map { (abs($0.rtg - rr), $0.a) }.stableSorted { $0.0 < $1.0 }.prefix(15)
        var v = [0, 0]
        for x in c { v[x.1] += 1 }
        return v[1] >= v[0] ? 1 : 0
    }
}

private func bcPol(_ samples: [DtStep]) -> (Int, Double?) -> Int {
    let m = (0..<6).map { s -> Int in
        var c = [0, 0]
        for x in samples where x.s == s { c[x.a] += 1 }
        return c[1] > c[0] ? 1 : 0
    }
    return { s, _ in m[s] }
}

private func dtLab() -> RbLab {
    let lg = [("#22a06b", "Target hit"), ("#3b82f6", "Dataset"), ("#e5337a", "Cloned average")]
    return RbLab(tabs: ["R̂ 0", "R̂ 0.5", "R̂ 0.75"], initialTab: 2) { opt in
        let tg = [0, 0.5, 0.75][opt]
        let dd = dtData
        let rets = dd.rets
        let mn = rets.min()!
        let mxr = rets.max()!
        let mean = rets.reduce(0, +) / Double(rets.count)
        let bc = bcPol(dd.samples)
        let bco = dtRun(bc)
        func frac(_ x: Double) -> Double { (x + 1.3) / 2.1 }
        return (0..<5).map { stp in
            switch stp {
            case 0:
                let bins = [-1.25, -1, -0.75, -0.5, -0.25, 0, 0.25, 0.5, 0.75]
                let cnt = bins.map { b in rets.filter { $0 >= b - 0.125 && $0 < b + 0.125 }.count }
                let cm = Double(cnt.max()!)
                return fr([L("returns of the 250 logged trajectories"), .cols(bins.enumerated().map { i, b in
                    var label = f(b, 2)
                    if let r = label.range(of: "0.") { label.replaceSubrange(r, with: ".") }
                    return RbVCol(v: "\(cnt[i])", label: label, lc: "#9aa0ae", bars: [RbVBar(h: Double(cnt[i]) / cm * 62, bg: b >= 0.5 ? "#22a06b" : "#3b82f6")])
                })], [F("worst · mean · best =", f(mn) + " · " + f(mean) + " · " + f(mxr))],
                          ("A deliberately mixed log: most trajectories are poor.", "Each was generated by a random walker with its own bias to the right; the goal is 6 cells away."), lg)
            case 1:
                return fr([L("behaviour cloning: majority action per cell"), C((0...5).map { x in let a = bc(x, nil); return Rb.tok("s\(x)", a == 1 ? "done" : "err", a == 1 ? "→" : "←") })],
                          [F("cloned return =", f(bco.r), "#e5337a"), F("reaches the goal in 25 steps:", bco.ok ? "yes" : "no", bco.ok ? "#22a06b" : "#e5337a")],
                          ("Cloning mixed data copies the average.", "The majority action near the start is ←, so the clone oscillates and scores \(f(bco.r))."), lg)
            case 2:
                let o = dtRun(dtPol(tg))
                return fr([L("conditioned on return-to-go R̂ = \(js(tg))"), C(o.path.prefix(10).map { Rb.tok("s\($0)", $0 == 6 ? "ans" : "plain") }),
                            S(st("target", f(tg)), st("achieved", f(o.r), abs(o.r - tg) < 0.13 ? "#22a06b" : "#e5337a"), st("steps", "\(o.t)"))],
                          [F("a = π(s, R̂) · R̂ ← R̂ − r after each step")],
                          ("Ask for \(f(tg)), get \(f(o.r)).", "The policy picks the action that logged trajectories with that much return still to come took from here."), lg)
            case 3:
                return fr([L("achieved return by requested target"), B([-0.5, 0, 0.25, 0.5, 0.75, 1, 2].map { t in
                    let o = dtRun(dtPol(t))
                    return Rb.row("target \(f(t))", f(o.r), frac(o.r), abs(o.r - t) < 0.13 ? "#22a06b" : "#9aa0ae", t == tg ? "#f2f3f7" : "#9aa0ae")
                })], [F("best in the log =", f(mxr), "#22a06b")],
                          ("Targets inside the data are hit; beyond it, they saturate.", "Asking for 2 returns \(f(dtRun(dtPol(2)).r)) — the policy can stitch good behaviour together, not invent better."), lg)
            default:
                let top = dd.t.stableSorted { $0.ret > $1.ret }.prefix(25).flatMap(\.st)
                let pb = dtRun(bcPol(Array(top)))
                let d = dtRun(dtPol(0.75))
                return fr([B([
                    Rb.row("BC, all data", f(bco.r), frac(bco.r), "#e5337a"),
                    Rb.row("BC, top 10%", f(pb.r), frac(pb.r), "#9aa0ae"),
                    Rb.row("DT, R̂ = 0.75", f(d.r), frac(d.r), "#22a06b", "#f2f3f7"),
                    Rb.row("best logged", f(mxr), frac(mxr), "#3b82f6"),
                ])], [F("sequence modelling, no Bellman backup")],
                          ("Return conditioning gets filtering for free.", "Here it matches cloning the top 10% — without choosing a cutoff, and with one model for every target."), lg)
            }
        }
    }
}

// MARK: - 62g Meta-RL: a 5 × 8 room, four training goals in the bottom-right

private func mmv(_ i: Int, _ a: Int) -> Int {
    let r = i / 8 + RbGw.AC[a].0
    let c = i % 8 + RbGw.AC[a].1
    return r < 0 || r > 4 || c < 0 || c > 7 ? i : r * 8 + c
}

private func mvi(_ g: Int) -> [[Double]] {
    var v = [Double](repeating: 0, count: 40)
    func q(_ vv: [Double], _ i: Int, _ a: Int) -> Double {
        let n = mmv(i, a)
        return i == g ? 0 : (n == g ? 1 : -0.04) + (n == g ? 0 : 0.95 * vv[n])
    }
    for _ in 0..<200 { let o = v; v = (0..<40).map { i in i == g ? 0 : (0...3).map { q(o, i, $0) }.max()! } }
    return (0..<40).map { i in (0..<4).map { q(v, i, $0) } }
}

private let MTR = [23, 31, 39, 37]

private let mamlInit: [[Double]] = {
    let qs = MTR.map { mvi($0) }
    return (0..<40).map { i in (0..<4).map { a in qs.reduce(0.0) { $0 + $1[i][a] } / 4 } }
}()

private let maCache = RbCache<String, [Double]>()

private func madapt(_ meta: Bool, _ g: Int) -> [Double] {
    maCache.get("\(meta)_\(g)") {
        var sc = [Double](repeating: 0, count: 10)
        for k in 1...30 {
            let r = Rb.rng(Int64(k) * 17 + 1)
            var q = meta ? mamlInit : [[Double]](repeating: [0, 0, 0, 0], count: 40)
            for e in 0..<10 {
                var s = 0
                var t = 0
                while t < 40 {
                    let qs = q[s]
                    let a: Int
                    if r() < 0.1 { a = Int(floor(r() * 4)) } else {
                        var b = 0
                        for i in 1..<4 where qs[i] > qs[b] + 1e-12 { b = i }
                        a = b
                    }
                    let n = mmv(s, a)
                    let rw = n == g ? 1.0 : -0.04
                    q[s][a] += 0.5 * (rw + (n == g ? 0 : 0.95 * q[n].max()!) - q[s][a])
                    s = n
                    if n == g { break }
                    t += 1
                }
                sc[e] += Double(s == g ? t + 1 : 40) / 30
            }
        }
        return sc
    }
}

private struct MetaCell { var t: String? = nil; var bg: String? = nil; var ring: String? = nil }

private func g58(_ fn: (Int) -> MetaCell) -> RbBlock {
    .grid(RbGrid(ch: 30, cols: (0...7).map { "c\($0)" }, rows: (0...4).map { r in
        RbGRow(label: "r\(r)", lc: "#9aa0ae", cells: (0...7).map { c in
            let o = fn(r * 8 + c)
            return RbCell(t: o.t ?? "", bg: o.bg ?? "#1f232d", color: "#fff", ring: o.ring ?? "none")
        })
    }))
}

private func metaLab() -> RbLab {
    let lg = [("#6d5dfc", "Training goals"), ("#22a06b", "Meta-learned"), ("#e5337a", "Out of distribution")]
    func base(_ i: Int) -> MetaCell {
        if i == 0 { return MetaCell(t: "S") }
        if MTR.contains(i) { return MetaCell(t: "★", bg: "#6d5dfc") }
        if i == 38 { return MetaCell(t: "?", ring: "inset 0 0 0 2px #f5c542") }
        if i == 7 { return MetaCell(t: "?", ring: "inset 0 0 0 2px #e5337a") }
        return MetaCell()
    }
    return RbLab(tabs: ["held-out goal", "OOD goal"], initialTab: 0) { opt in
        let g = [38, 7][opt]
        let gn = ["held-out goal", "out-of-distribution goal"][opt]
        let q0 = mamlInit
        let sc = madapt(false, g)
        let mt = madapt(true, g)
        return (0..<5).map { stp in
            switch stp {
            case 0:
                return fr([L("training goals ★ · test goals ?"), g58(base)],
                          [F("4 training tasks · the agent is told none of them"), F("test ·", "yellow = held-out, pink = out of distribution")],
                          ("Meta-RL learns across a family of tasks.", "The goal is always somewhere in the bottom-right; what carries over is not a policy but a fast way to find one."), lg)
            case 1:
                let v = q0.map { $0.max()! }
                let mx = v.max()!
                let mn = v.min()!
                return fr([L("meta-learned starting values max_a Q₀(s, a)"), g58 { i in
                    let b = base(i)
                    return MetaCell(t: b.t ?? f(v[i], 1), bg: b.bg ?? "rgba(59,130,246,\(Rb.a2(0.1 + 0.8 * (v[i] - mn) / (mx - mn))))", ring: b.ring)
                }], [F("Q₀ = mean of the 4 task solutions")],
                          ("The start already points at the goal region.", "It can’t know which cell, but every task rewards heading down and right."), lg)
            case 2:
                return fr([L("steps to reach the \(gn) · 30 runs"), .cols([0, 1, 2, 4, 9].map { e in
                    RbVCol(v: f(mt[e], 1), label: "ep \(e + 1)", lc: "#9aa0ae", bars: [RbVBar(h: sc[e] / 40 * 62, bg: "#9aa0ae"), RbVBar(h: mt[e] / 40 * 62, bg: "#22a06b")])
                })], [F("episode 1 · scratch \(f(sc[0], 1)) vs meta", f(mt[0], 1), "#22a06b"), F("optimal path =", "\(g / 8 + g % 8) steps")],
                          g == 38 ? ("From the meta start, the new goal is found almost at once.", "Episode 1 takes \(f(mt[0], 1)) steps against \(f(sc[0], 1)) from scratch; by episode 10 it is at \(f(mt[9], 1)).")
                              : ("Out of distribution, the head start disappears.", "The prior pulls toward the bottom-right, away from this goal: episode 1 takes \(f(mt[0], 1)) steps."), lg)
            case 3:
                var rows: [RbRow] = []
                for (gg, l) in [(38, "held-out"), (7, "OOD")] {
                    let a = madapt(false, gg)
                    let b = madapt(true, gg)
                    rows.append(Rb.row("\(l) · scratch", f(a[2], 1), a[2] / 40, "#9aa0ae", gg == g ? "#f2f3f7" : "#9aa0ae"))
                    rows.append(Rb.row("\(l) · meta", f(b[2], 1), b[2] / 40, gg == 7 ? "#e5337a" : "#22a06b", gg == g ? "#f2f3f7" : "#9aa0ae"))
                }
                return fr([L("steps to goal in episode 3 · lower is better"), B(rows)], [F("adaptation: Q-learning, ε = 0.1, α = 0.5")],
                          ("Meta-learning buys speed inside the family only.", "A prior is a bet about which tasks will come; outside that family it is mostly dead weight."), lg)
            default:
                return fr([S(st("inner loop", "θ′ = θ − α ∇L_task(θ)"), st("outer loop", "θ ← θ − β ∇ Σ L_task(θ′)", "#22a06b"), st("here", "averaged task solutions as θ₀"))],
                          [F("MAML optimises the start for one-step adaptation")],
                          ("MAML differentiates through the adaptation step.", "This tabular stand-in averages task solutions (Reptile-style); the adaptation is real Q-learning."), lg)
            }
        }
    }
}
