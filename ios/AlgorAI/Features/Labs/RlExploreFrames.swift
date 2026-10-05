import Foundation

// Port of RlExploreFrames.kt: four-armed bandits pulled live, a sparse-reward maze with a noisy TV for
// count bonuses, ICM and RND, and coordination games where independent learners, VDN, QMIX, MADDPG and
// fictitious self-play are each trained or solved on the spot. Drawn by RlBoardLabs.swift.

let rlExploreTopicIds: Set<String> = [
    "epsilon_greedy", "boltzmann_exploration", "intrinsic_motivation", "icm", "rnd", "minimax", "iql", "vdn", "qmix", "maddpg", "self_play",
]

func rlExploreLab(_ topicId: String) -> RbLab? {
    switch topicId {
    case "epsilon_greedy": bandLab("eps", [0.01, 0.1, 0.3], "ε")
    case "boltzmann_exploration": bandLab("boltz", [0.05, 0.15, 0.5], "τ")
    case "intrinsic_motivation": intrinsicLab()
    case "icm": icmLab()
    case "rnd": rndLab()
    case "minimax": minimaxLab()
    case "iql": iqlLab()
    case "vdn": vdnLab()
    case "qmix": qmixLab()
    case "maddpg": maddpgLab()
    case "self_play": selfPlayLab()
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

// MARK: - 61a/61b ε-greedy and Boltzmann bandits

private let MU = [0.30, 0.50, 0.45, 0.72]
private let ARM = ["A", "B", "C", "D"]

private struct BanditH { let a: Int; let rw: Int; let ex: Bool; let pr: [Double]?; let old: Double; let q: [Double]; let n: [Int]; let tot: Int }

private let bhCache = RbCache<String, [BanditH]>()

private func bandit(_ kind: String, _ p: Double, _ seed: Int) -> [BanditH] {
    bhCache.get("\(kind)\(p)_\(seed)") {
        let r = Rb.rng(Int64(seed) * 13 + 5)
        var q = [0.0, 0, 0, 0]
        var n = [0, 0, 0, 0]
        var h: [BanditH] = []
        var tot = 0
        for _ in 0..<200 {
            var a: Int
            var ex = false
            var pr: [Double]? = nil
            if kind == "eps" {
                ex = r() < p
                if ex { a = Int(floor(r() * 4)) } else {
                    let m = q.max()!
                    let c = (0...3).filter { q[$0] >= m - 1e-12 }
                    a = c[Int(floor(r() * Double(c.count)))]
                }
            } else {
                let probs = Rb.softmax(q.map { $0 / p })
                pr = probs
                let u = r()
                var acc = 0.0
                a = 3
                for i in 0..<4 {
                    acc += probs[i]
                    if u < acc { a = i; break }
                }
            }
            let rw = r() < MU[a] ? 1 : 0
            let old = q[a]
            n[a] += 1
            q[a] += (Double(rw) - old) / Double(n[a])
            tot += rw
            h.append(BanditH(a: a, rw: rw, ex: ex, pr: pr, old: old, q: q, n: n, tot: tot))
        }
        return h
    }
}

private func bStats(_ kind: String, _ p: Double) -> (best: Double, rew: Double) {
    var best = 0.0
    var rew = 0.0
    for s in 1...50 {
        let h = bandit(kind, p, s)
        best += Double(h[199].n[3]) / 200 / 50
        rew += Double(h[199].tot) / 200 / 50
    }
    return (best, rew)
}

private func bandLab(_ kind: String, _ ps: [Double], _ pl: String) -> RbLab {
    let lg = [("#f5c542", "Pulled now"), ("#3b82f6", "Estimate")] + (kind == "boltz" ? [("#6d5dfc", "Draw prob.")] : []) + [("#22a06b", "Best setting")]
    return RbLab(tabs: ps.map { "\(pl) \(js($0))" }, initialTab: 1) { opt in
        let p = ps[opt]
        return (0..<5).map { stp in
            if stp < 4 {
                let t = [1, 10, 50, 200][stp]
                let hh = bandit(kind, p, 1)
                let h = hh[t - 1]
                let arm = ARM[h.a]
                let prevQ = t > 1 ? hh[t - 2].q : [0, 0, 0, 0]
                let tie = prevQ.allSatisfy { $0 == prevQ[0] }
                var blocks = [
                    C([Rb.tok("pull \(t)", "plain"), Rb.tok(kind == "eps" ? (h.ex ? "explore" : "exploit") : "softmax draw", "cur"), Rb.tok("arm \(arm)", "plain"), Rb.tok("reward \(h.rw)", h.rw > 0 ? "done" : "err")]),
                    L("estimate Q(a) · μ = true win rate"),
                    B(ARM.enumerated().map { i, a in
                        Rb.row("\(a) · μ \(f(MU[i]))", f(h.q[i]) + " · " + "\(h.n[i])" + "×", h.q[i], i == h.a ? "#f5c542" : "#3b82f6", i == h.a ? "#f5c542" : i == 3 ? "#5fd49b" : nil)
                    }),
                ]
                if kind == "boltz" {
                    blocks.append(L("π(a) = softmax(Q/τ) used for this draw"))
                    blocks.append(B(ARM.enumerated().map { i, a in Rb.row(a, pct(h.pr![i]), h.pr![i], i == h.a ? "#f5c542" : "#6d5dfc") }))
                }
                let fx = [
                    kind == "eps" ? F("ε =", js(p) + " · explore with probability " + pct(p)) : F("π(\(arm)) = e^(\(f(h.old))/\(js(p))) / Σ =", pct(h.pr![h.a]), "#b3abff"),
                    F("Q(\(arm)) ← \(f(h.old)) + (\(h.rw) − \(f(h.old)))/\(h.n[h.a]) =", f(h.q[h.a]), "#f5c542"),
                ]
                let top = h.q.firstIndex(of: h.q.max()!)!
                let capB = "D, the truly best arm, has \(h.n[3]) of \(t) pulls" + (top == 3 ? " and now leads the estimates." : ".")
                let cap: (String, String) = kind == "eps"
                    ? (h.ex ? ("Explore: a random draw landed on \(arm).", capB) : (tie ? "Exploit: every estimate is tied, so \(arm) wins the tie-break." : "Exploit: \(arm) had the top estimate, \(f(h.old)).", capB))
                    : ("\(arm) was drawn with probability \(pct(h.pr![h.a])).", capB)
                return fr(blocks, fx, cap, lg)
            }
            let ss = ps.map { bStats(kind, $0) }
            var bi = 0
            for (i, x) in ss.enumerated() where x.rew > ss[bi].rew { bi = i }
            return fr([
                L("share of pulls on D · mean of 50 runs"),
                B(ps.enumerated().map { i, x in Rb.row("\(pl) \(js(x))", pct(ss[i].best), ss[i].best, i == bi ? "#22a06b" : "#3a3f4c", i == opt ? "#f2f3f7" : "#9aa0ae") }),
                L("reward per pull"),
                B(ps.enumerated().map { i, x in Rb.row("\(pl) \(js(x))", f(ss[i].rew, 3), ss[i].rew / 0.72, i == bi ? "#22a06b" : "#3a3f4c", i == opt ? "#f2f3f7" : "#9aa0ae") }),
            ], [F("best possible = always D =", "0.720 per pull", "#22a06b")],
                      ("\(pl) \(js(ps[bi])) earns the most: \(f(ss[bi].rew, 3)) per pull.", kind == "eps" ? "Exploration costs reward every time it fires, but without it an early lucky arm can hold the lead for good." : "Low τ behaves almost greedily, high τ almost uniformly. Unlike ε, bad arms get fewer draws the worse they look."), lg)
        }
    }
}

// MARK: - The sparse-reward maze: 5 × 8, start (0,0), goal cell 39, a noisy TV at cell 19

private struct Explore { let vis: [Int]; let reached: Int; let first: Int; let tv: Double; let seen: Int; let lb: [Int: Double] }

private let exCache = RbCache<String, Explore>()

private func explore(_ m: String, _ seed: Int) -> Explore {
    exCache.get("\(m)_\(seed)") {
        let r = Rb.rng(Int64(seed) * 97 + 13)
        let rr = 5
        let cc = 8
        let beta = m == "eps" ? 0.0 : 0.3
        var q: [Int: [Double]] = [:]
        var nn: [String: Int] = [:]
        var ns: [Int: Int] = [:]
        var no: [String: Int] = [:]
        var lb: [Int: Double] = [:]
        var vis = [Int](repeating: 0, count: 40)
        var reached = 0
        var first = -1
        var tvS = 0
        var steps = 0
        for e in 0..<60 {
            var s = (0, 0)
            vis[0] += 1
            for _ in 0..<40 {
                let k = s.0 * cc + s.1
                if q[k] == nil { q[k] = [0, 0, 0, 0] }
                let qs = q[k]!
                let a: Int
                if r() < 0.1 { a = Int(floor(r() * 4)) } else {
                    var b = 0
                    for i in 1..<4 where qs[i] > qs[b] + 1e-12 { b = i }
                    a = b
                }
                var n = (s.0 + RbGw.AC[a].0, s.1 + RbGw.AC[a].1)
                if n.0 < 0 || n.0 >= rr || n.1 < 0 || n.1 >= cc { n = s }
                let ci = n.0 * cc + n.1
                let isTV = ci == 19
                let ch = isTV ? Int(floor(r() * 10)) : 0
                let sa = "\(k)|\(a)"
                let ok = "\(ci)#\(ch)"
                nn[sa, default: 0] += 1
                ns[ci, default: 0] += 1
                no[ok, default: 0] += 1
                let b: Double
                switch m {
                case "count": b = 1 / sqrt(Double(ns[ci]!))
                case "icmpix": b = isTV ? max(0.9, 1 / sqrt(Double(nn[sa]!))) : 1 / sqrt(Double(nn[sa]!))
                case "icmfeat": b = 1 / sqrt(Double(nn[sa]!))
                case "rnd": b = 1 / sqrt(Double(no[ok]!))
                default: b = 0
                }
                lb[ci] = beta * b
                let goal = ci == 39
                if q[ci] == nil { q[ci] = [0, 0, 0, 0] }
                q[k]![a] = (goal ? 1 : 0) + beta * b + (goal ? 0 : 0.5 * q[ci]!.max()!)
                vis[ci] += 1
                steps += 1
                if isTV { tvS += 1 }
                s = n
                if goal {
                    reached += 1
                    if first < 0 { first = e }
                    break
                }
            }
        }
        return Explore(vis: vis, reached: reached, first: first, tv: Double(tvS) / Double(steps), seen: vis.filter { $0 > 0 }.count, lb: lb)
    }
}

private let esCache = RbCache<String, (runs: Int, seen: Double, tv: Double)>()

private func exStats(_ m: String) -> (runs: Int, seen: Double, tv: Double) {
    esCache.get(m) {
        var runs = 0
        var seen = 0.0
        var tv = 0.0
        for s in 1...30 {
            let o = explore(m, s)
            if o.reached > 0 { runs += 1 }
            seen += Double(o.seen) / 30
            tv += o.tv / 30
        }
        return (runs, seen, tv)
    }
}

private struct MCell { var t: String? = nil; var bg: String? = nil; var tvRing = false }

private func mgrid(_ fn: (Int) -> MCell) -> RbBlock {
    .grid(RbGrid(ch: 30, cols: (0...7).map { "c\($0)" }, rows: (0...4).map { r in
        RbGRow(label: "r\(r)", lc: "#9aa0ae", cells: (0...7).map { c in
            let i = r * 8 + c
            let o = fn(i)
            let tag: String? = i == 0 ? "S" : i == 39 ? "G" : i == 19 ? "?" : nil
            let t = tag ?? ((o.t?.isEmpty ?? true) ? "" : o.t!)
            return RbCell(t: t, bg: o.bg ?? "#1f232d", color: "#fff",
                          ring: i == 19 && o.tvRing ? "inset 0 0 0 2px #e5337a" : i == 39 ? "inset 0 0 0 2px #22a06b" : "none")
        })
    }))
}

private func heatGrid(_ o: Explore, _ tv: Bool = false) -> RbBlock {
    let mx = Double(o.vis.max()!)
    return mgrid { i in
        let v = o.vis[i]
        return MCell(t: v > 0 ? (v >= 1000 ? js(Rb.round(Double(v) / 100) / 10) + "k" : "\(v)") : "",
                     bg: v > 0 ? "rgba(59,130,246,\(Rb.a2(0.12 + 0.8 * log(1 + Double(v)) / log(1 + mx))))" : "#1f232d", tvRing: tv)
    }
}

private func bonusGrid(_ o: Explore, _ tv: Bool = false) -> RbBlock {
    mgrid { i in
        let b = o.lb[i] ?? 0.3
        return MCell(t: f(b, 2), bg: "rgba(109,93,252,\(Rb.a2(0.08 + 0.85 * b / 0.3)))", tvRing: tv)
    }
}

// MARK: - 61c Intrinsic Motivation

private func intrinsicLab() -> RbLab {
    let lg = [("#3b82f6", "Visits"), ("#6d5dfc", "Bonus"), ("#22a06b", "Goal / with bonus")]
    return RbLab(tabs: ["ε-greedy", "count bonus"], initialTab: 1) { opt in
        let m = ["eps", "count"][opt]
        let nm = ["ε-greedy", "count bonus"][opt]
        let o = explore(m, 1)
        return (0..<5).map { stp in
            switch stp {
            case 0:
                return fr([L("5 × 8 open maze · reward +1 only at G"), mgrid { _ in MCell() }],
                          [F("shortest path S → G =", "11 steps"), F("budget =", "60 episodes × 40 steps")],
                          ("Sparse reward: nothing to learn from until G is found.", "Greedy Q-learning with ε = 0.1, ties always broken the same way — how an untrained network behaves."), lg)
            case 1:
                return fr([L("visits per cell · \(nm) · run 1"), heatGrid(o)],
                          [F("cells ever visited =", "\(o.seen) / 40", m == "eps" ? "#e5337a" : "#22a06b"), F("episodes reaching G =", "\(o.reached) / 60", o.reached > 0 ? "#22a06b" : "#e5337a")],
                          m == "eps" ? ("ε-greedy dithers around the start.", "A random step 10% of the time rarely adds up to a trip outward: \(o.seen) of 40 cells seen, G reached in \(o.reached) of 60 episodes.")
                              : ("A count bonus pushes outward.", "Every visit makes a cell pay less, so the greedy choice drifts toward unseen cells: \(o.seen) of 40 seen, G reached in \(o.reached) of 60 episodes."), lg)
            case 2:
                let c = explore("count", 1)
                return fr([L("bonus β/√N(s′) at the end of run 1"), bonusGrid(c)],
                          [F("r⁺ = r + β/√N(s′),  β =", "0.3", "#b3abff"), F("bonus at S =", f(c.lb[0] ?? 0.3, 3))],
                          ("Well-trodden cells pay almost nothing.", "Cells never entered still pay the full 0.30, so they look better than anywhere the agent has been."), lg)
            case 3:
                let e = exStats("eps")
                let cn = exStats("count")
                return fr([
                    L("runs that ever reached G · 30 runs each"),
                    B([Rb.row("ε-greedy", "\(e.runs) / 30", Double(e.runs) / 30, "#9aa0ae"), Rb.row("count bonus", "\(cn.runs) / 30", Double(cn.runs) / 30, "#22a06b", "#f2f3f7")]),
                    L("mean cells visited"),
                    B([Rb.row("ε-greedy", f(e.seen, 1), e.seen / 40, "#9aa0ae"), Rb.row("count bonus", f(cn.seen, 1), cn.seen / 40, "#22a06b", "#f2f3f7")]),
                ], [F("same agent, same budget; only the bonus differs")],
                          ("\(cn.runs) of 30 runs find the goal with a bonus, \(e.runs) without.", "Directed novelty covers \(f(cn.seen, 1)) cells on average against \(f(e.seen, 1)) for undirected noise."), lg)
            default:
                return fr([S(st("needs", "a count per state"), st("breaks when", "states never repeat (pixels)"), st("learned stand-ins", "ICM, RND, pseudo-counts", "#b3abff"))],
                          [F("bonus must generalise to unseen states")],
                          ("Counting needs states that repeat.", "In image-based tasks no frame is seen twice, so the novelty signal has to come from a learned model instead."), lg)
            }
        }
    }
}

// MARK: - 61d ICM

private func icmLab() -> RbLab {
    let lg = [("#9aa0ae", "Normal cell"), ("#e5337a", "Noisy TV"), ("#22a06b", "ICM features")]
    return RbLab(tabs: ["pixel model", "ICM features"], initialTab: 0) { opt in
        let m = ["icmpix", "icmfeat"][opt]
        let nm = ["pixel model", "ICM features"][opt]
        let o = explore(m, 1)
        let pix = m == "icmpix"
        return (0..<5).map { stp in
            switch stp {
            case 0:
                return fr([L("same maze, plus a noisy TV at ?"), mgrid { _ in MCell(tvRing: true) }, S(st("forward model", "predicts s′ from (s, a)"), st("TV cell", "random channel 0–9 every visit", "#e5337a"))],
                          [F("bonus = β · prediction error,  β =", "0.3", "#b3abff")],
                          ("ICM pays the agent for being surprised.", "Where its forward model is wrong, the agent is curious — and goes there."), lg)
            case 1:
                return fr([L("prediction error by visits to (s, a)"), .cols([1, 4, 16, 64].map { n in
                    let e = 1 / sqrt(Double(n))
                    let t = pix ? max(0.9, e) : e
                    return RbVCol(v: f(t), label: "\(n) visits", lc: "#9aa0ae", bars: [RbVBar(h: e * 62, bg: "#9aa0ae"), RbVBar(h: t * 62, bg: "#e5337a")])
                })], [pix ? F("TV error floor =", "0.90 · right 1 time in 10", "#e5337a") : F("in φ-space the TV error =", f(1.0 / 8) + " at 64 visits", "#22a06b")],
                          pix ? ("The TV is never predictable.", "Normal cells get boring as the model learns them; the TV stays at 0.90 because the next channel is pure chance.")
                              : ("In feature space the TV is just a cell.", "The channel is not caused by the action, so the features drop it and the error falls like everywhere else."), lg)
            case 2:
                return fr([L("visits per cell · \(nm) · run 1"), heatGrid(o, true)],
                          [F("steps spent on the TV =", pct(o.tv), "#e5337a"), F("cells ever visited =", "\(o.seen) / 40")],
                          pix ? ("The pixel model gets hooked on the TV.", "Its bonus never fades, so run 1 spent \(pct(o.tv)) of all steps on that one cell.")
                              : ("Features fix the noisy TV.", "ICM predicts in features trained by an inverse model, which keeps only what actions control. TV time: \(pct(o.tv))."), lg)
            case 3:
                let p = exStats("icmpix")
                let f2 = exStats("icmfeat")
                return fr([
                    L("share of steps on the TV · 30 runs"),
                    B([Rb.row("pixel model", pct(p.tv), p.tv / 0.25, "#e5337a"), Rb.row("ICM features", pct(f2.tv), f2.tv / 0.25, "#22a06b", "#f2f3f7")]),
                    L("runs that ever reached G"),
                    B([Rb.row("pixel model", "\(p.runs) / 30", Double(p.runs) / 30, "#e5337a"), Rb.row("ICM features", "\(f2.runs) / 30", Double(f2.runs) / 30, "#22a06b", "#f2f3f7")]),
                ], [F("TV time ·", pct(p.tv) + " → " + pct(f2.tv), "#22a06b")],
                          ("Features cut TV time from \(pct(p.tv)) to \(pct(f2.tv)).", "The trap is gone, but per-action novelty spreads slower than a per-cell count: \(f2.runs) of 30 runs reach G."), lg)
            default:
                return fr([S(st("inverse model", "predicts a from φ(s), φ(s′)"), st("forward model", "predicts φ(s′) from φ(s), a"), st("bonus", "forward error in φ-space", "#b3abff"))],
                          [F("L = (1 − β)·L_inverse + β·L_forward")],
                          ("Two models, one bonus.", "The inverse model shapes the features; the forward model’s error is the curiosity signal."), lg)
            }
        }
    }
}

// MARK: - 61e RND

private func rndLab() -> RbLab {
    let lg = [("#22a06b", "RND"), ("#e5337a", "ICM pixels / TV"), ("#3b82f6", "Visits / other")]
    return RbLab(tabs: ["RND", "ICM pixels", "ε-greedy"], initialTab: 0) { opt in
        let m = ["rnd", "icmpix", "eps"][opt]
        let nm = ["RND", "ICM pixels", "ε-greedy"][opt]
        let o = explore(m, 1)
        return (0..<4).map { stp in
            switch stp {
            case 0:
                return fr([S(st("target f", "fixed random net: obs → vector"), st("predictor f̂", "trained to match f where visited"), st("bonus", "‖f̂(s′) − f(s′)‖²", "#b3abff"))],
                          [F("error after n sightings of an obs ≈", "1/√n")],
                          ("RND keeps “bonus = prediction error” but changes the target.", "f is a fixed function of the observation, so every observation eventually becomes predictable — even the TV’s."), lg)
            case 1:
                let cap: (String, String) = m == "rnd" ? ("RND explores the whole maze.", "Run 1 saw \(o.seen) of 40 cells and reached G in \(o.reached) episodes.")
                    : m == "icmpix" ? ("ICM on pixels circles the TV.", "\(pct(o.tv)) of steps on one cell; \(o.seen) cells seen.")
                    : ("ε-greedy barely leaves the start.", "\(o.seen) of 40 cells seen, G reached \(o.reached) times.")
                return fr([L("visits per cell · \(nm) · run 1"), heatGrid(o, true)],
                          [F("cells visited =", "\(o.seen) / 40"), F("steps on TV =", pct(o.tv), "#e5337a")], cap, lg)
            case 2:
                let r2 = explore("rnd", 1)
                let p = explore("icmpix", 1)
                return fr([L("RND bonus at the end of run 1"), bonusGrid(r2, true)],
                          [F("TV bonus · RND", f(r2.lb[19] ?? 0.3, 3), "#22a06b"), F("TV bonus · ICM pixels", f(p.lb[19] ?? 0.3, 3), "#e5337a")],
                          ("Even the TV gets boring.", "Ten channels are ten observations to learn, not an endless supply: the TV’s bonus fell to \(f(r2.lb[19] ?? 0.3, 2))."), lg)
            default:
                let ms = [("eps", "ε-greedy"), ("count", "count"), ("icmpix", "ICM pixels"), ("icmfeat", "ICM feat."), ("rnd", "RND")]
                let ss = ms.map { exStats($0.0) }
                var bi = 0
                for (i, x) in ss.enumerated() where x.runs > ss[bi].runs { bi = i }
                return fr([
                    L("runs that ever reached G · 30 each"),
                    B(ms.enumerated().map { i, kl in Rb.row(kl.1, "\(ss[i].runs) / 30", Double(ss[i].runs) / 30, kl.0 == "rnd" ? "#22a06b" : kl.0 == "icmpix" ? "#e5337a" : "#3b82f6", kl.0 == m ? "#f2f3f7" : "#9aa0ae") }),
                    L("steps on the TV"),
                    B(ms.enumerated().map { i, kl in Rb.row(kl.1, pct(ss[i].tv), ss[i].tv / 0.25, kl.0 == "icmpix" ? "#e5337a" : "#3a3f4c", kl.0 == m ? "#f2f3f7" : "#9aa0ae") }),
                ], [F("most runs reaching G =", ms[bi].1, "#22a06b")],
                          ("RND: \(ss[4].runs) of 30 runs reach G, with \(pct(ss[4].tv)) TV time.", "It nearly matches exact counting without needing states to repeat, and avoids the pixel model’s TV trap."), lg)
            }
        }
    }
}

// MARK: - 61f Minimax

private func tCount(_ b: [String], _ ab: Bool, _ al0: Double, _ be0: Double) -> (v: Double, n: Int) {
    if let w = RbTtt.win(b) { return (RbTtt.score(w), 1) }
    var al = al0
    var be = be0
    let x = RbTtt.toMove(b) == "X"
    var v = x ? -2.0 : 2.0
    var n = 1
    for m in RbTtt.moves(b) {
        let o = tCount(RbTtt.play(b, m), ab, al, be)
        n += o.n
        if x { v = max(v, o.v); al = max(al, v) } else { v = min(v, o.v); be = min(be, v) }
        if ab && al >= be { break }
    }
    return (v, n)
}

private func minimaxLab() -> RbLab {
    let lg = [("#3b82f6", "X better"), ("#e5337a", "O wins"), ("#22a06b", "Best move")]
    func vl(_ v: Double) -> String { v > 0 ? "X wins" : v < 0 ? "O wins" : "draw" }
    return RbLab(tabs: ["minimax", "alpha-beta"], initialTab: 0) { opt in
        let b = RbTtt.TB
        let tb = RbTtt.best(b)
        let vals = tb.vals
        let best = tb.best
        let ab = opt == 1
        return (0..<5).map { stp in
            switch stp {
            case 0:
                return fr([RbTtt.grid(b, nil, nil)], [F("X to move ·", "\(vals.count) legal moves")],
                          ("Minimax assumes both sides play perfectly.", "X picks the move with the highest value; O, replying, picks the lowest. +1 is a win for X, 0 a draw, −1 a loss."), lg)
            case 1:
                let m0 = vals.first { $0.1 < 0 }!.0
                var c = b
                c[m0] = "X"
                let reps = RbTtt.moves(c).map { m -> (Int, Double) in var d = c; d[m] = "O"; return (m, RbTtt.minimax(d)) }
                let r = reps.dropFirst().reduce(reps[0]) { a, x in x.1 < a.1 ? x : a }
                return fr([
                    C([Rb.tok("X: sq \(m0 + 1)", "plain"), Rb.tok("O: sq \(r.0 + 1)", "err"), Rb.tok(vl(r.1), "err")]),
                    L("O’s replies after X plays sq \(m0 + 1)"),
                    B(reps.map { m, v in Rb.brow("sq \(m + 1)", v, 1, vl(v), m == r.0 ? "#ff8a8d" : nil) }),
                ], [F("value(sq \(m0 + 1)) = min over O’s replies =", f(r.1, 0), "#e5337a")],
                          ("Playing sq \(m0 + 1) loses.", "O answers with sq \(r.0 + 1) and wins, so the move is worth −1 no matter what else follows."), lg)
            case 2:
                let n = tCount(b, ab, -2, 2).n
                return fr([RbTtt.grid(b, nil, best), L("minimax value per move"), B(vals.map { m, v in Rb.brow("sq \(m + 1)", v, 1, vl(v), best.contains(m) ? "#5fd49b" : nil) })],
                          [F("best =", "sq " + best.map { "\($0 + 1)" }.joined(separator: ", ") + " · value " + f(vals.map(\.1).max()!, 0), "#22a06b"), F((ab ? "alpha-beta" : "minimax") + " searched", "\(n) nodes")],
                          ("Every legal move, scored by perfect play.", best.count == 1 ? "Only sq \(best[0] + 1) holds the draw; every other move lets O win." : "Squares \(best.map { "\($0 + 1)" }.joined(separator: ", ")) tie for best."), lg)
            case 3:
                let p = tCount(b, false, -2, 2)
                let a = tCount(b, true, -2, 2)
                return fr([L("positions examined to value the root"), B([
                    Rb.row("minimax", "\(p.n)", 1, ab ? "#3a3f4c" : "#3b82f6", ab ? "#9aa0ae" : "#f2f3f7"),
                    Rb.row("alpha-beta", "\(a.n)", Double(a.n) / Double(p.n), ab ? "#22a06b" : "#3a3f4c", ab ? "#f2f3f7" : "#9aa0ae"),
                ])], [F("same root value ·", f(p.v, 0) + " = " + f(a.v, 0), "#22a06b"), F("saved =", pct(1 - Double(a.n) / Double(p.n)))],
                          ("Alpha-beta reaches the same answer after \(a.n) positions instead of \(p.n).", "It skips branches that cannot change the decision — on chess-sized trees, the difference between feasible and not."), lg)
            default:
                var c = b
                c[best[0]] = "X"
                return fr([RbTtt.grid(c, nil, [best[0]]), S(st("X plays", "sq \(best[0] + 1)", "#22a06b"), st("value", "0 · draw with perfect play"))],
                          [F("V(s) = max_a min_b V(s′′)")],
                          ("Blocking keeps the game level.", "From here neither side can force a win; the tree’s value is the result of perfect play by both."), lg)
            }
        }
    }
}

// MARK: - 61g Independent Q-learning on the penalty game

private let PEN: [[Double]] = [[8, -12, -12], [-12, 0, 0], [-12, 0, 0]]
private let AL = ["A0", "A1", "A2"]
private let BL = ["B0", "B1", "B2"]

/// A payoff grid: blue positive, pink negative, ringed optimum cells.
private func pgrid(_ m: [[Double]], _ rl: [String], _ cl: [String], _ opt: [(Int, Int)]?, _ ch: Int = 34, _ dp: Int = 1) -> RbBlock {
    let mx = m.flatMap { $0 }.map { abs($0) }.max()!
    let sc = mx == 0 ? 1 : mx
    return .grid(RbGrid(ch: ch, cols: cl, rows: m.enumerated().map { i, row in
        RbGRow(label: rl[i], lc: "#9aa0ae", cells: row.enumerated().map { j, v in
            let a = Rb.a2(0.15 + 0.7 * abs(v) / sc)
            return RbCell(t: f(v, dp), bg: abs(v) < 1e-9 ? "#1f232d" : v > 0 ? "rgba(59,130,246,\(a))" : "rgba(229,51,122,\(a))", color: "#fff",
                          ring: opt?.contains { $0.0 == i && $0.1 == j } == true ? "inset 0 0 0 2px #22a06b" : "none")
        })
    }))
}

private struct Iql { let snaps: [Int: ([Double], [Double])]; let joint: (Int, Int) }

private let iqCache = RbCache<String, Iql>()

private func iql(_ eps: Double, _ seed: Int) -> Iql {
    iqCache.get("\(eps)_\(seed)") {
        let r = Rb.rng(Int64(seed) * 31 + 7)
        var q1 = [0.0, 0, 0]
        var q2 = [0.0, 0, 0]
        var snaps: [Int: ([Double], [Double])] = [:]
        func am(_ q: [Double]) -> Int { var b = 0; for i in 1..<3 where q[i] > q[b] + 1e-12 { b = i }; return b }
        for t in 1...500 {
            let a = r() < eps ? Int(floor(r() * 3)) : am(q1)
            let b = r() < eps ? Int(floor(r() * 3)) : am(q2)
            let rw = PEN[a][b]
            q1[a] += 0.1 * (rw - q1[a])
            q2[b] += 0.1 * (rw - q2[b])
            if t == 10 || t == 500 { snaps[t] = (q1, q2) }
        }
        return Iql(snaps: snaps, joint: (am(q1), am(q2)))
    }
}

private func iqlLab() -> RbLab {
    let lg = [("#3b82f6", "Positive"), ("#e5337a", "Penalty"), ("#22a06b", "Optimum")]
    return RbLab(tabs: ["ε 0.05", "ε 0.2", "ε 0.5"], initialTab: 1) { opt in
        let eps = [0.05, 0.2, 0.5][opt]
        return (0..<5).map { stp in
            if stp == 0 {
                return fr([L("team payoff"), pgrid(PEN, AL, BL, [(0, 0)])], [F("optimum =", "8 at (A0, B0)", "#22a06b")],
                          ("Two agents, three actions each, one shared payoff.", "(A0, B0) pays 8, but either agent playing its half while the partner does anything else loses 12."), lg)
            }
            if stp < 3 {
                let t = stp == 1 ? 10 : 500
                let o = iql(eps, 1)
                let (sq1, sq2) = o.snaps[t]!
                let blocks = [L("Q₁ · agent A after \(t) rounds"), B(sq1.enumerated().map { i, v in Rb.brow(AL[i], v, 12) }), L("Q₂ · agent B"), B(sq2.enumerated().map { i, v in Rb.brow(BL[i], v, 12) })]
                if stp == 1 {
                    return fr(blocks, [F("Q(a) ← Q(a) + 0.1·(r − Q(a))"), F("Q₁(A0) =", f(sq1[0]), sq1[0] < 0 ? "#e5337a" : "#3b82f6")],
                              ("Each agent learns as if the other were the weather.", "After 10 rounds A0 is worth \(f(sq1[0])) to agent A: the partner’s exploration keeps punishing it."), lg)
                }
                let (a, b) = o.joint
                let v = PEN[a][b]
                let best = a == 0 && b == 0
                return fr(blocks, [F("greedy joint action =", "(\(AL[a]), \(BL[b])) → \(f(v, 0))", best ? "#22a06b" : "#e5337a")],
                          best ? ("This run found the optimum.", "Both agents happened to commit to their half at the same time.")
                              : ("They settle on (\(AL[a]), \(BL[b])), worth \(f(v, 0)) instead of 8.", "Each agent’s best reply to the other is safe and mediocre — neither can move to the optimum alone."), lg)
            }
            if stp == 3 {
                let ex = (0...2).map { PEN[$0].reduce(0, +) / 3 }
                return fr([L("E[r] per action if the partner is uniform"), B(ex.enumerated().map { i, v in Rb.brow(AL[i], v, 12) })],
                          [F("E[r | A0] = (8 − 12 − 12) / 3 =", f(ex[0]), "#e5337a"), F("E[r | A1] = (−12 + 0 + 0) / 3 =", f(ex[1]))],
                          ("The safe actions look better on average.", "While the partner explores, A0 averages \(f(ex[0])) and A1 or A2 average \(f(ex[1])). Both agents retreat: relative over-generalisation."), lg)
            }
            var cntKeys: [String] = []
            var cnt: [String: Int] = [:]
            for k in 1...50 {
                let jt = iql(eps, k).joint
                let j = "\(jt.0),\(jt.1)"
                if cnt[j] == nil { cntKeys.append(j) }
                cnt[j, default: 0] += 1
            }
            let ks = cntKeys.stableSorted { cnt[$0]! > cnt[$1]! }
            let n00 = cnt["0,0"] ?? 0
            return fr([L("greedy joint action after 500 rounds · 50 runs"), B(ks.map { k in
                let ab = k.split(separator: ",").map { Int($0)! }
                let opt0 = ab[0] == 0 && ab[1] == 0
                return Rb.row("(\(AL[ab[0]]), \(BL[ab[1]]))", "\(cnt[k]!) · r \(f(PEN[ab[0]][ab[1]], 0))", Double(cnt[k]!) / 50, opt0 ? "#22a06b" : "#9aa0ae", opt0 ? "#5fd49b" : nil)
            })], [F("optimum found in", "\(n00) / 50 runs", n00 > 25 ? "#22a06b" : "#e5337a")],
                      ("Independent learners find the optimum in \(n00) of 50 runs.", "The rest lock into a safe zero. Coordination needs a learner that sees the joint action — VDN, QMIX, centralised critics."), lg)
        }
    }
}

// MARK: - 61h VDN and 61i QMIX

private let GAMES: [String: [[Double]]] = [
    "additive": [[5, 3, 1], [3, 1, -1], [1, -1, -3]],
    "product": [[9, 6, 3], [6, 4, 2], [3, 2, 1]],
    "penalty": PEN,
]

private func vdnFit(_ m: [[Double]]) -> (q1: [Double], q2: [Double], fit: [[Double]], mse: Double) {
    let rm = m.map { $0.reduce(0, +) / 3 }
    let cm = (0...2).map { j in (m[0][j] + m[1][j] + m[2][j]) / 3 }
    let g = rm.reduce(0, +) / 3
    let q1 = rm.map { $0 - g / 2 }
    let q2 = cm.map { $0 - g / 2 }
    let fit = m.indices.map { i in (0...2).map { j in q1[i] + q2[j] } }
    var mse = 0.0
    for (i, r) in m.enumerated() { for (j, v) in r.enumerated() { mse += (v - fit[i][j]) * (v - fit[i][j]) / 9 } }
    return (q1, q2, fit, mse)
}

private let qmCache = RbCache<String, (fit: [[Double]], mse: Double)>()

private func sgn1(_ x: Double) -> Double { x == 0 ? 1 : x > 0 ? 1 : -1 }

private func qmixFit(_ name: String) -> (fit: [[Double]], mse: Double) {
    qmCache.get(name) {
        let m = GAMES[name]!
        let sc = m.flatMap { $0 }.map { abs($0) }.max()!
        let k = 6
        var best: (fit: [[Double]], mse: Double)? = nil
        var seed: Int64 = 1
        while seed <= 3 && !(best != nil && best!.mse < 0.01) {
            let r = Rb.rng(seed)
            var q1 = (0..<3).map { _ in r() - 0.5 }
            var q2 = (0..<3).map { _ in r() - 0.5 }
            var u1 = (0..<k).map { _ in r() }
            var u2 = (0..<k).map { _ in r() }
            var c = (0..<k).map { _ in r() - 0.5 }
            var w = (0..<k).map { _ in r() }
            var d = 0.0
            var hh = [Double](repeating: 0, count: k)
            func fwd(_ i: Int, _ j: Int) -> Double {
                var o = d
                for x in 0..<k {
                    let z = abs(u1[x]) * q1[i] + abs(u2[x]) * q2[j] + c[x]
                    hh[x] = z
                    o += abs(w[x]) * max(0, z)
                }
                return o
            }
            for _ in 0..<6000 {
                var gq1 = [0.0, 0, 0], gq2 = [0.0, 0, 0]
                var gu1 = [Double](repeating: 0, count: k), gu2 = gu1, gc = gu1, gw = gu1
                var gd = 0.0
                for i in 0..<3 {
                    for j in 0..<3 {
                        let o = fwd(i, j)
                        let e = 2 * (o - m[i][j] / sc) / 9
                        gd += e
                        for x in 0..<k where hh[x] > 0 {
                            let aw = abs(w[x])
                            gw[x] += e * sgn1(w[x]) * hh[x]
                            let eh = e * aw
                            gc[x] += eh
                            gu1[x] += eh * sgn1(u1[x]) * q1[i]
                            gu2[x] += eh * sgn1(u2[x]) * q2[j]
                            gq1[i] += eh * abs(u1[x])
                            gq2[j] += eh * abs(u2[x])
                        }
                    }
                }
                for x in 0..<3 { q1[x] -= 0.1 * gq1[x]; q2[x] -= 0.1 * gq2[x] }
                for x in 0..<k { u1[x] -= 0.1 * gu1[x]; u2[x] -= 0.1 * gu2[x]; c[x] -= 0.1 * gc[x]; w[x] -= 0.1 * gw[x] }
                d -= 0.1 * gd
            }
            let fit = (0...2).map { i in (0...2).map { j in fwd(i, j) * sc } }
            var mse = 0.0
            for (i, rr) in m.enumerated() { for (j, v) in rr.enumerated() { mse += (v - fit[i][j]) * (v - fit[i][j]) / 9 } }
            if best == nil || mse < best!.mse { best = (fit, mse) }
            seed += 1
        }
        return best!
    }
}

private func argmax2(_ fm: [[Double]]) -> (Int, Int) {
    var bi = 0
    var bj = 0
    for (i, r) in fm.enumerated() { for (j, v) in r.enumerated() where v > fm[bi][bj] + 1e-9 { bi = i; bj = j } }
    return (bi, bj)
}

private func vdnLab() -> RbLab {
    let lg = [("#3b82f6", "Value"), ("#e5337a", "Negative / miss"), ("#22a06b", "Optimum")]
    return RbLab(tabs: ["additive", "product", "penalty"], initialTab: 2) { opt in
        let gn = ["additive", "product", "penalty"][opt]
        let m = GAMES[gn]!
        let v = vdnFit(m)
        let o = argmax2(m)
        return (0..<4).map { stp in
            switch stp {
            case 0:
                return fr([L("team payoff · \(gn) game"), pgrid(m, AL, BL, [o])],
                          [F("optimum =", f(m[o.0][o.1], 0) + " at (\(AL[o.0]), \(BL[o.1]))", "#22a06b")],
                          ("VDN trains centrally and executes locally.", "One team reward trains everything; at run time each agent just takes its own argmax."), lg)
            case 1:
                return fr([L("per-agent utilities fitted to the team reward"), B(v.q1.enumerated().map { i, x in Rb.brow("Q₁(\(AL[i]))", x, 8) } + v.q2.enumerated().map { i, x in Rb.brow("Q₂(\(BL[i]))", x, 8) })],
                          [F("Q_tot(a₁, a₂) = Q₁(a₁) + Q₂(a₂)")],
                          ("Each agent keeps its own small table.", "The tables are fitted so their sum matches the team reward as closely as a sum can."), lg)
            case 2:
                return fr([L("VDN’s Q_tot = Q₁ + Q₂"), pgrid(v.fit, AL, BL, [argmax2(v.fit)])],
                          [F("fit error (MSE) =", f(v.mse, 3), v.mse < 1e-6 ? "#22a06b" : "#e5337a")],
                          v.mse < 1e-6 ? ("An additive payoff is matched exactly.", "Every cell is a row value plus a column value, which is precisely what VDN can represent.")
                              : ("A sum cannot represent this payoff.", "The best additive fit still misses by MSE \(f(v.mse, 3))."), lg)
            default:
                let i = v.q1.firstIndex(of: v.q1.max()!)!
                let j = v.q2.firstIndex(of: v.q2.max()!)!
                let got = m[i][j]
                let bestv = m[o.0][o.1]
                let hit = got == bestv
                return fr([S(
                    st("VDN plays", "(\(AL[i]), \(BL[j])) → \(f(got, 0))", hit ? "#22a06b" : "#e5337a"),
                    st("true optimum", "(\(AL[o.0]), \(BL[o.1])) → \(f(bestv, 0))"),
                    st("regret", f(bestv - got, 0), hit ? "#22a06b" : "#e5337a")
                )], [F("each agent: argmax Qᵢ, no communication")],
                          hit ? ("Local argmaxes give the team optimum.", "Even with a fit error, the ranking survives here.")
                              : ("Local argmaxes miss the optimum by \(f(bestv - got, 0)).", "The −12 penalties drag A0 and B0 down in the averaged fit — the coordination problem is back."), lg)
            }
        }
    }
}

private func qmixLab() -> RbLab {
    let lg = [("#3b82f6", "QMIX"), ("#9aa0ae", "VDN"), ("#22a06b", "Optimum")]
    return RbLab(tabs: ["product", "penalty"], initialTab: 0) { opt in
        let gn = ["product", "penalty"][opt]
        let m = GAMES[gn]!
        let v = vdnFit(m)
        let qm = qmixFit(gn)
        let o = argmax2(m)
        return (0..<4).map { stp in
            switch stp {
            case 0:
                return fr([L("team payoff · \(gn) game"), pgrid(m, AL, BL, [o]), S(st("mixer", "6 ReLU units, weights forced ≥ 0"), st("guarantee", "argmax Q_tot = per-agent argmaxes"))],
                          [F("Q_tot = mix(Q₁, Q₂),  ∂Q_tot/∂Qᵢ ≥ 0")],
                          gn == "product" ? ("This payoff is a product, not a sum.", "Monotone in each agent’s contribution, but with an interaction a sum cannot capture.")
                              : ("The penalty game is not monotone.", "Whether A0 is good depends on what B plays — the hard case for any decomposition."), lg)
            case 1:
                return fr([L("VDN’s best sum"), pgrid(v.fit, AL, BL, [argmax2(v.fit)])], [F("VDN fit error (MSE) =", f(v.mse, 3), "#9aa0ae")],
                          ("VDN’s best additive fit.", "Least squares, solved exactly: MSE \(f(v.mse, 3))."), lg)
            case 2:
                let oq = argmax2(qm.fit)
                return fr([L("QMIX’s monotone mixer · trained 6,000 steps"), pgrid(qm.fit, AL, BL, [oq])],
                          [F("MSE ·", f(v.mse, 3) + " → " + f(qm.mse, 3), qm.mse < v.mse / 4 ? "#22a06b" : "#e5337a")],
                          gn == "product" ? ("The mixer bends the sum into a product.", "MSE drops from \(f(v.mse, 3)) to \(f(qm.mse, 3)). A sum is one monotone mixer, so QMIX can only match or beat VDN.")
                              : ("QMIX can’t fit the penalty game either.", "MSE \(f(v.mse, 1)) → \(f(qm.mse, 1)); its greedy pick is (\(AL[oq.0]), \(BL[oq.1])), worth \(f(m[oq.0][oq.1], 0))."), lg)
            default:
                var rows: [RbRow] = []
                for g in ["product", "penalty"] {
                    let vg = vdnFit(GAMES[g]!)
                    let qg = qmixFit(g)
                    let mg = GAMES[g]!
                    func ok(_ fm: [[Double]]) -> Bool { let (i, j) = argmax2(fm); let (a, b) = argmax2(mg); return mg[i][j] == mg[a][b] }
                    rows.append(Rb.row("\(g) · VDN", f(vg.mse, 3) + (ok(vg.fit) ? " ✓" : " ✗"), min(1, sqrt(vg.mse) / 8), "#9aa0ae", g == gn ? "#f2f3f7" : "#9aa0ae"))
                    rows.append(Rb.row("\(g) · QMIX", f(qg.mse, 3) + (ok(qg.fit) ? " ✓" : " ✗"), min(1, sqrt(qg.mse) / 8), "#3b82f6", g == gn ? "#f2f3f7" : "#9aa0ae"))
                }
                return fr([L("fit error · ✓ = greedy pick is the true optimum"), B(rows)], [F("monotone mixing ⊃ sums ⊂ all payoffs")],
                          ("Monotone helps; it isn’t everything.", "QMIX nails the product game. In the penalty game the optimum needs non-monotone mixing — the gap QTRAN and QPLEX target."), lg)
            }
        }
    }
}

// MARK: - 61j MADDPG

private func mr(_ a: Double, _ b: Double) -> Double { 1.6 * a * b + a + b - a * a - b * b - 0.5 }

/// Least squares via the normal equations and Gauss–Jordan with partial pivoting, as the design's lsq().
func rbLsq(_ a: [[Double]], _ y: [Double]) -> [Double] {
    let n = a[0].count
    var m = (0..<n).map { i in (0...n).map { j in a.enumerated().reduce(0.0) { s, kr in s + kr.element[i] * (j < n ? kr.element[j] : y[kr.offset]) } } }
    for i in 0..<n {
        var p = i
        for k in (i + 1)..<max(n, i + 1) where abs(m[k][i]) > abs(m[p][i]) { p = k }
        m.swapAt(i, p)
        for k in 0..<n where k != i {
            let q = m[k][i] / m[i][i]
            for j in i...n { m[k][j] -= q * m[i][j] }
        }
    }
    return (0..<n).map { m[$0][n] / m[$0][$0] }
}

private struct Ddpg { let tr: [(Double, Double)]; let g: [(Double, Double)] }

private let dpCache = RbCache<String, Ddpg>()

private func ddpg(_ cent: Bool, _ sig: Double, _ seed: Int) -> Ddpg {
    dpCache.get("\(cent)_\(sig)_\(seed)") {
        let r = Rb.rng(Int64(seed) * 7 + 3)
        var m0 = -0.8
        var m1 = 0.6
        var tr = [(m0, m1)]
        var gs: [(Double, Double)] = []
        var xs: [[Double]] = []
        var ys: [Double] = []
        for _ in 0..<15 {
            for _ in 0..<8 {
                let a0 = m0 + sig * Rb.gauss(r)
                let a1 = m1 + sig * Rb.gauss(r)
                xs.append([a0, a1])
                ys.append(mr(a0, a1))
            }
            let from = max(0, xs.count - 160)
            let xw = Array(xs[from...])
            let yw = Array(ys[from...])
            let g: [Double]
            if cent {
                let c = rbLsq(xw.map { [1, $0[0], $0[1], $0[0] * $0[0], $0[1] * $0[1], $0[0] * $0[1]] }, yw)
                g = [c[1] + 2 * c[3] * m0 + c[5] * m1, c[2] + 2 * c[4] * m1 + c[5] * m0]
            } else {
                let mm = [m0, m1]
                g = (0..<2).map { i in let c = rbLsq(xw.map { [1, $0[i], $0[i] * $0[i]] }, yw); return c[1] + 2 * c[2] * mm[i] }
            }
            gs.append((g[0], 1.6 * m1 + 1 - 2 * m0))
            m0 = max(-1, min(1, m0 + 0.25 * g[0]))
            m1 = max(-1, min(1, m1 + 0.25 * g[1]))
            tr.append((m0, m1))
        }
        return Ddpg(tr: tr, g: gs)
    }
}

private func maddpgLab() -> RbLab {
    let lg = [("#3b82f6", "Centralised"), ("#9aa0ae", "Independent"), ("#e5337a", "Drift")]
    let ax = [-1, -0.5, 0, 0.5, 1.0]
    func dist(_ t: (Double, Double)) -> Double { hypot(1 - t.0, 1 - t.1) }
    return RbLab(tabs: ["σ 0.1", "σ 0.3", "σ 0.6"], initialTab: 1) { opt in
        let sig = [0.1, 0.3, 0.6][opt]
        func mean(_ cent: Bool, _ u: Int) -> Double { var d = 0.0; for k in 1...30 { d += dist(ddpg(cent, sig, k).tr[u]) / 30 }; return d }
        return (0..<5).map { stp in
            switch stp {
            case 0:
                return fr([L("reward over the joint action (a₁ down, a₂ across)"), pgrid(ax.map { a in ax.map { b in mr(a, b) } }, ax.map { f($0, 1) }, ax.map { f($0, 1) }, [(4, 4)], 28, 2)],
                          [F("r = 1.6·a₁a₂ + a₁ + a₂ − a₁² − a₂² − 0.5"), F("optimum =", "1.10 at (1, 1)", "#22a06b")],
                          ("A continuous task: both agents pick a real number.", "The reward couples them through the cross term 1.6·a₁a₂, so each agent’s best action depends on the other’s."), lg)
            case 1:
                let i = ddpg(false, sig, 1).g[9]
                let cn = ddpg(true, sig, 1).g[9]
                return fr([S(st("independent critic", "Q₁(a₁) · partner hidden in the noise", "#e5337a"), st("centralised critic", "Q(a₁, a₂) · sees both actions", "#3b82f6"), st("replay buffer", "last 160 samples, old partner policies"), st("start", "(−0.8, 0.6)"))],
                          [F("update 10 · independent ∂Q/∂a₁ = \(f(i.0)) · true", f(i.1), "#e5337a"), F("update 10 · centralised ∂Q/∂a₁ = \(f(cn.0)) · true", f(cn.1), "#3b82f6")],
                          ("The partner keeps changing, so the replay buffer goes stale.", "An independent critic blends old partner behaviour into its gradient; a critic that sees both actions is unaffected."), lg)
            case 2:
                return fr([L("mean distance to the optimum · 30 runs"), .cols([1, 3, 5, 10, 15].map { u in
                    let i = mean(false, u)
                    let c = mean(true, u)
                    return RbVCol(v: f(i), label: "upd \(u)", lc: "#9aa0ae", bars: [RbVBar(h: min(100, i / 2.1 * 100) * 0.62, bg: "#9aa0ae"), RbVBar(h: min(100, c / 2.1 * 100) * 0.62, bg: "#3b82f6")])
                })], [F("update 15 · independent \(f(mean(false, 15))) vs centralised", f(mean(true, 15)), "#3b82f6")],
                          ("The centralised critic walks straight to (1, 1).", "With exploration noise σ = \(js(sig)), independent critics are still \(f(mean(false, 15))) away after 15 updates."), lg)
            case 3:
                var rows: [RbRow] = []
                for x in [0.1, 0.3, 0.6] {
                    var ri = 0.0
                    var rc = 0.0
                    for k in 1...30 {
                        let ti = ddpg(false, x, k).tr[15]
                        ri += mr(ti.0, ti.1) / 30
                        let tc = ddpg(true, x, k).tr[15]
                        rc += mr(tc.0, tc.1) / 30
                    }
                    rows.append(Rb.row("σ \(js(x)) · indep.", f(ri), max(0, (ri + 4) / 5.1), "#9aa0ae", x == sig ? "#f2f3f7" : "#9aa0ae"))
                    rows.append(Rb.row("σ \(js(x)) · central", f(rc), max(0, (rc + 4) / 5.1), "#3b82f6", x == sig ? "#f2f3f7" : "#9aa0ae"))
                }
                return fr([L("reward after 15 updates · max 1.10"), B(rows)], [F("independent learners improve with more noise — but never catch up")],
                          ("Centralised critics reach 1.10 at every noise level.", "More exploration helps independent critics refresh their view of the partner, but stale data still costs reward."), lg)
            default:
                return fr([S(st("training", "critic Qᵢ(s, a₁, a₂) per agent"), st("execution", "actor μᵢ(oᵢ) — own observation only", "#22a06b"), st("communication at run time", "none"))],
                          [F("∇θᵢ J = E[∇θᵢ μᵢ(oᵢ) · ∇aᵢ Qᵢ(s, a₁, a₂)]")],
                          ("Centralised training, decentralised execution.", "The critic is thrown away after training; each actor acts on its own observation."), lg)
            }
        }
    }
}

// MARK: - 61k Self-Play: fictitious play on rock-paper-scissors

private let RPS: [String: [[Double]]] = [
    "standard": [[0, -1, 1], [1, 0, -1], [-1, 1, 0]],
    "rock ×2": [[0, -1, 2], [1, 0, -1], [-2, 1, 0]],
]

private let fpCache = RbCache<String, [Int: (fr: [Double], expl: Double, last: [Int])]>()

private func fp(_ g: String) -> [Int: (fr: [Double], expl: Double, last: [Int])] {
    fpCache.get(g) {
        let a = RPS[g]!
        var c1 = [1.0, 0, 0]
        var c2 = [0.0, 1, 0]
        var snaps: [Int: (fr: [Double], expl: Double, last: [Int])] = [:]
        var hist: [Int] = []
        func v(_ i: Int, _ c: [Double]) -> Double { c.indices.reduce(0.0) { $0 + c[$1] * a[i][$1] } }
        func br(_ c: [Double]) -> Int { var b = 0; for i in 1..<3 where v(i, c) > v(b, c) + 1e-12 { b = i }; return b }
        for t in 1...2000 {
            let x = br(c2)
            let y = br(c1)
            c1[x] += 1
            c2[y] += 1
            hist.append(x)
            if [10, 40, 300, 2000].contains(t) {
                let tot = c1.reduce(0, +)
                let fr = c1.map { $0 / tot }
                snaps[t] = (fr, (0...2).map { v($0, fr) }.max()!, Array(hist.suffix(6)))
            }
        }
        return snaps
    }
}

private func selfPlayLab() -> RbLab {
    let lg = [("#3b82f6", "Frequency"), ("#22a06b", "Near Nash"), ("#e5337a", "Exploitable")]
    let nm = ["rock", "paper", "scissors"]
    return RbLab(tabs: ["standard", "rock ×2"], initialTab: 1) { opt in
        let g = ["standard", "rock ×2"][opt]
        let sn = fp(g)
        let ne = g == "standard" ? [1.0 / 3, 1.0 / 3, 1.0 / 3] : [0.25, 0.5, 0.25]
        return (0..<5).map { stp in
            if stp < 3 {
                let t = [10, 40, 300][stp]
                let o = sn[t]!
                let cap: (String, String) = stp == 0 ? ("Self-play: each side best-responds to the other’s history.", "Early frequencies swing as each side chases the other’s last habit; \(g == "standard" ? "" : "here rock beats scissors for 2, ")exploitability is \(f(o.expl, 2)).")
                    : stp == 1 ? ("The averages start to settle.", "Exploitability is down to \(f(o.expl, 3)) after 40 rounds.")
                    : ("After \(t) rounds the average is almost unexploitable.", g == "standard" ? "It sits near ⅓ each — the Nash equilibrium nobody programmed in." : "Paper rises toward 50%: doubling rock’s win makes paper, rock’s counter, the equilibrium favourite.")
                return fr([
                    L("last 5 plays"), C(o.last.suffix(5).map { Rb.tok(nm[$0], "plain") }),
                    L("play frequency after \(t) rounds"), B(o.fr.enumerated().map { i, x in Rb.row(nm[i], pct(x), x / 0.6, abs(x - ne[i]) < 0.03 ? "#22a06b" : "#3b82f6") }),
                ], [F("Nash =", ne.map { pct($0) }.joined(separator: " / ")), F("exploitability =", f(o.expl, 3), o.expl < 0.05 ? "#22a06b" : "#e5337a")], cap, lg)
            }
            if stp == 3 {
                let e10 = sn[10]!.expl
                return fr([L("exploitability of the average strategy"), B([10, 40, 300, 2000].map { t in
                    let e = sn[t]!.expl
                    return Rb.row("\(t) rounds", f(e, 3), e / max(e10, 0.01), e < 0.05 ? "#22a06b" : "#e5337a")
                })], [F("exploitability = max_a (A·π̄)_a,  0 at Nash")],
                          ("Fictitious play converges in two-player zero-sum games.", "From \(f(e10, 3)) to \(f(sn[2000]!.expl, 3)) with no external opponent — the curriculum is entirely internal."), lg)
            }
            return fr([S(st("current policy", "always a pure best response", "#e5337a"), st("average policy", "converges to Nash", "#22a06b"), st("at scale", "AlphaZero, OpenAI Five, AlphaStar league"))],
                      [F("Nash · " + ne.map { pct($0) }.joined(separator: " / "))],
                      ("The average is what converges.", "The latest best response is always exploitable; keeping old versions around (a league) is how large systems use the same idea."), lg)
        }
    }
}
