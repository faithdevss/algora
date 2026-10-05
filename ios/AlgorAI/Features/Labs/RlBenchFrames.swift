import Foundation

// Port of RlBenchFrames.kt: value iteration on the classic grid world, CartPole, Mountain Car and the
// pendulum on their real equations, a toy Pong that shows why Atari agents stack frames, league training
// on a rush–expand–defend game, and factorised action heads trained against a flat softmax. Drawn by
// RlBoardLabs.swift.

let rlBenchTopicIds: Set<String> = ["grid_world", "cartpole", "mountain_car", "atari", "mujoco", "starcraft", "dota2"]

func rlBenchLab(_ topicId: String) -> RbLab? {
    switch topicId {
    case "grid_world": gridWorldLab()
    case "cartpole": cartPoleLab()
    case "mountain_car": mountainCarLab()
    case "atari": atariLab()
    case "mujoco": mujocoLab()
    case "starcraft": starcraftLab()
    case "dota2": dotaLab()
    default: nil
    }
}

private func f(_ x: Double, _ d: Int = 2) -> String { Rb.f(x, d) }
private func pct(_ p: Double) -> String { Rb.pct(p) }
private func L(_ t: String) -> RbBlock { Rb.L(t) }
private func C(_ items: [RbTok]) -> RbBlock { Rb.C(items) }
private func B(_ rows: [RbRow]) -> RbBlock { Rb.B(rows) }
private func S(_ rows: (String, String, String?)...) -> RbBlock { Rb.S(rows) }
private func st(_ k: String, _ v: String, _ c: String? = nil) -> (String, String, String?) { (k, v, c) }
private func F(_ a: String, _ b: String = "", _ c: String? = nil) -> RbFx { Rb.F(a, b, c) }
private func fr(_ blocks: [RbBlock], _ fx: [RbFx], _ cap: (String, String), _ lg: [(String, String)]) -> RbFrame { Rb.frame(blocks, fx, cap, lg) }
private func pt(_ x: Double, _ y: Double, _ sz: Double, _ bg: String, _ ring: String = "none") -> RbPt { Rb.pt(x, y, sz, bg, ring) }

// MARK: - 63a The classic grid world: 80/10/10 slips, γ = 1, goal cell 3, pit 7, wall 5, start 12

private let GWA = [(-1, 0), (0, 1), (1, 0), (0, -1)]
private let GW_AR = ["↑", "→", "↓", "←"]

private func gwMv(_ i: Int, _ a: Int) -> Int {
    let r = i / 4 + GWA[a].0
    let c = i % 4 + GWA[a].1
    if r < 0 || r > 3 || c < 0 || c > 3 { return i }
    let j = r * 4 + c
    return j == 5 ? i : j
}

private func gwQ(_ v: [Double], _ i: Int, _ a: Int, _ c: Double) -> Double {
    [(a, 0.8), ((a + 1) % 4, 0.1), ((a + 3) % 4, 0.1)].reduce(0.0) { s, bp in s + bp.1 * (c + v[gwMv(i, bp.0)]) }
}

private struct Gw { let sw: [[Double]]; let pol: [Int]; let v: [Double] }

private let gwCache = RbCache<Double, Gw>()

private func gw(_ c: Double) -> Gw {
    gwCache.get(c) {
        var v = [Double](repeating: 0, count: 16)
        v[3] = 1
        v[7] = -1
        var sw = [v]
        for _ in 0..<300 {
            let o = v
            let nv = (0..<16).map { i in i == 3 || i == 7 || i == 5 ? o[i] : (0...3).map { gwQ(o, i, $0, c) }.max()! }
            let d = (0..<16).map { abs(nv[$0] - o[$0]) }.max()!
            v = nv
            sw.append(v)
            if d < 1e-4 { break }
        }
        let fin = v
        let pol = (0..<16).map { i in (0...3).reduce(0) { b, a in gwQ(fin, i, a, c) > gwQ(fin, i, b, c) + 1e-9 ? a : b } }
        return Gw(sw: sw, pol: pol, v: fin)
    }
}

private func gwGrid(_ v: [Double], _ pol: [Int]? = nil, _ hl: [Int]? = nil) -> RbBlock {
    let m = (0..<16).filter { $0 != 3 && $0 != 7 }.map { abs(v[$0]) }.max()!
    let mx = m == 0 ? 1 : m
    return .grid(RbGrid(ch: 52, cols: (0...3).map { "c\($0)" }, rows: (0...3).map { r in
        RbGRow(label: "r\(r)", lc: "#9aa0ae", cells: (0...3).map { c in
            let i = r * 4 + c
            switch i {
            case 5: return RbCell(t: "", bg: "#3a3f4c", color: "#fff")
            case 3: return RbCell(t: "+1", bg: "#22a06b", color: "#fff")
            case 7: return RbCell(t: "−1", bg: "#e5337a", color: "#fff")
            default:
                let x = v[i]
                let a = Rb.a2(0.1 + 0.6 * min(1, abs(x) / mx))
                return RbCell(t: (pol != nil ? GW_AR[pol![i]] + " " : "") + f(x) + (i == 12 ? " S" : ""),
                              bg: abs(x) < 1e-9 ? "#1f232d" : x > 0 ? "rgba(59,130,246,\(a))" : "rgba(229,51,122,\(a))", color: "#fff",
                              ring: hl?.contains(i) == true ? "inset 0 0 0 2px #f5c542" : "none")
            }
        })
    }))
}

private func gridWorldLab() -> RbLab {
    let lg = [("#22a06b", "Goal +1"), ("#e5337a", "Pit −1"), ("#3b82f6", "Value"), ("#f5c542", "Highlighted")]
    return RbLab(tabs: ["cost −0.04", "cost −0.4", "cost −2"], initialTab: 0) { opt in
        let c = [-0.04, -0.4, -2.0][opt]
        let g = gw(c)
        let n = g.sw.count - 1
        return (0..<5).map { stp in
            switch stp {
            case 0:
                return fr([L("state value V(s) · sweep 0"), gwGrid(g.sw[0])], [F("step cost =", f(c)), F("moves =", "80% as intended, 10% each side")],
                          ("The classic grid world, before any planning.", "Only the terminals have values. Moves slip sideways one time in five, so the pit is a risk even when you aim past it."), lg)
            case 1:
                let p = g.sw[2]
                let q = g.sw[3]
                let a = (0...3).reduce(0) { b, x in gwQ(p, 2, x, c) > gwQ(p, 2, b, c) ? x : b }
                let nx = gwMv(2, a)
                let l = gwMv(2, (a + 3) % 4)
                let r = gwMv(2, (a + 1) % 4)
                return fr([L("V(s) after sweep 3"), gwGrid(q, nil, [2])],
                          [F("V(r0,c2) = 0.8·(\(f(c)) + \(f(p[nx]))) + 0.1·(\(f(c)) + \(f(p[l]))) + 0.1·(\(f(c)) + \(f(p[r]))) =", f(q[2]), "#f5c542")],
                          ("Value spreads out from the goal one cell per sweep.", "Next to the goal, aiming \(GW_AR[a]) is worth \(f(q[2])); cells three moves away still hold only the step cost."), lg)
            case 2:
                return fr([L("V(s) converged · \(n) sweeps"), gwGrid(g.v, nil, [12])], [F("V(start) =", f(g.v[12]), "#3b82f6"), F("stop when max change <", "0.0001")],
                          ("After \(n) sweeps nothing changes.", "Every value now prices the whole future from that cell, slips and step costs included."), lg)
            case 3:
                let cap: (String, String) = opt == 0 ? ("Near the pit, the policy takes the long way.", "From r2,c2 it heads \(GW_AR[g.pol[10]]) and from r1,c2 \(GW_AR[g.pol[6]]): with steps this cheap, avoiding a 10% slip into −1 is worth the detour.")
                    : opt == 1 ? ("Steps cost more, so the detours shrink.", "From r2,c2 the policy now heads \(GW_AR[g.pol[10]]).")
                    : ("At −2 a step, the pit is the better deal.", "From r1,c2 the policy heads \(GW_AR[g.pol[6]]) — straight into −1 — because two more steps would cost more.")
                return fr([L("greedy policy on the converged values"), gwGrid(g.v, g.pol, [6, 10, 11])], [F("π(s) = argmax_a Σ p(s′|s,a)·(r + V(s′))")], cap, lg)
            default:
                let cs = [-0.04, -0.4, -2.0]
                let sc = abs(gw(-2).v[12])
                return fr([
                    L("policy at r1,c2 — next to the pit"),
                    C(cs.map { x in let gg = gw(x); return Rb.tok(f(x) + " · " + GW_AR[gg.pol[6]], gwMv(6, gg.pol[6]) == 7 ? "err" : "done") }),
                    L("V(start) by step cost"),
                    B(cs.map { x in Rb.brow("cost \(f(x))", gw(x).v[12], sc, nil, x == c ? "#f2f3f7" : "#9aa0ae") }),
                ], [F("same grid, same slips, only r changes")],
                          ("The reward defines the behaviour.", "Change one number — the step cost — and the optimal plan goes from cautious to suicidal."), lg)
            }
        }
    }
}

// MARK: - 63b CartPole on the Gym equations

private struct CpState { let x: Double; let xd: Double; let th: Double; let td: Double }

private let cpCache = RbCache<String, (tr: [CpState], t: Int)>()

private func cp(_ k: Int, _ seed: Int) -> (tr: [CpState], t: Int) {
    cpCache.get("\(k)_\(seed)") {
        let r = Rb.rng(Int64(seed))
        var x = (r() - 0.5) * 0.1
        var xd = (r() - 0.5) * 0.1
        var th = (r() - 0.5) * 0.1
        var td = (r() - 0.5) * 0.1
        var tr = [CpState(x: x, xd: xd, th: th, td: td)]
        var tt = 500
        for t in 0..<500 {
            let a: Int
            switch k {
            case 0: a = r() < 0.5 ? 1 : 0
            case 1: a = th > 0 ? 1 : 0
            default: a = th + 0.5 * td > 0 ? 1 : 0
            }
            let force = a == 1 ? 10.0 : -10.0
            let ct = cos(th)
            let sn = sin(th)
            let tmp = (force + 0.05 * td * td * sn) / 1.1
            let ta = (9.8 * sn - ct * tmp) / (0.5 * (4.0 / 3 - 0.1 * ct * ct / 1.1))
            let xa = tmp - 0.05 * ta * ct / 1.1
            x += 0.02 * xd
            xd += 0.02 * xa
            th += 0.02 * td
            td += 0.02 * ta
            tr.append(CpState(x: x, xd: xd, th: th, td: td))
            if abs(x) > 2.4 || abs(th) > 12 * Double.pi / 180 { tt = t + 1; break }
        }
        return (tr, tt)
    }
}

private func cpScene(_ o: CpState) -> RbBlock {
    let w = 320.0, h = 150.0
    let cx = 160 + max(-2.4, min(2.4, o.x)) / 2.4 * 140
    let cy = 112.0
    let l = 72.0
    let tx = cx + l * sin(o.th)
    let ty = cy - l * cos(o.th)
    func px(_ x: Double) -> Double { x / w * 100 }
    func py(_ y: Double) -> Double { y / h * 100 }
    var pts: [RbPt] = []
    for i in 0...40 { pts.append(pt(px(20 + 280 * Double(i) / 40), py(124), 3, "#3a3f4c")) }
    for b in [-2.4, 2.4] { pts.append(pt(px(160 + b / 2.4 * 140), py(124), 8, "#e5337a")) }
    pts += Rb.dline(px(cx), py(cy), px(tx), py(ty), 10, 5, "#22a06b")
    for d in [-14.0, 0, 14] { pts.append(pt(px(cx + d), py(cy + 2), 16, "#3b82f6")) }
    pts.append(pt(px(tx), py(ty), 11, "#f5c542"))
    return .plot(h: 150, pts: pts)
}

private func cartPoleLab() -> RbLab {
    let lg = [("#3b82f6", "Cart"), ("#22a06b", "Pole / balanced"), ("#f5c542", "Tip / angle"), ("#e5337a", "Limit")]
    func deg(_ x: Double) -> String { f(x * 180 / Double.pi, 1) + "°" }
    return RbLab(tabs: ["random", "lean", "lean + spin"], initialTab: 1) { k in
        let pn = ["random", "lean", "lean + spin"][k]
        let o = cp(k, 1)
        return (0..<5).map { stp in
            switch stp {
            case 0:
                return fr([cpScene(o.tr[0]), C([Rb.tok("state [x, ẋ, θ, θ̇]"), Rb.tok("2 actions"), Rb.tok("50 Hz")])],
                          [F("fail if |θ| > 12° or |x| > 2.4 m"), F("reward =", "+1 per surviving step · max 500")],
                          ("Four numbers are the whole state.", "Cart position and velocity, pole angle and spin. The only actions are full push left or right, ±10 N."), lg)
            case 1:
                let t = min(o.t, k == 2 ? 120 : max(1, o.t - 3))
                let p = o.tr[t]
                let cap: (String, String) = k == 0 ? ("Random pushes drop the pole in \(o.t) steps.", "It isn’t pushed over; it is simply never caught.")
                    : k == 1 ? ("Pushing toward the lean fails at step \(o.t).", "It reacts to where the pole is, not where it is going, so every correction overshoots a little more.")
                    : ("Adding spin to the rule balances indefinitely.", "At step \(t) the pole is at \(deg(p.th)) and the cart \(f(p.x)) m from centre; it lasts the full 500.")
                return fr([cpScene(p), C([Rb.tok("step \(t)", "cur"), Rb.tok("θ \(deg(p.th))", abs(p.th) > 0.15 ? "err" : "plain"), Rb.tok("θ̇ \(f(p.td))"), Rb.tok("x \(f(p.x)) m")])],
                          [F("\(pn) rule =", ["coin flip", "push toward the lean", "push toward θ + 0.5·θ̇"][k]), F("episode length =", "\(o.t) steps", o.t == 500 ? "#22a06b" : "#e5337a")], cap, lg)
            case 2:
                let n = min(o.tr.count, 150)
                let v = o.tr.prefix(n).map { $0.th * 180 / Double.pi }
                let cap: (String, String) = k == 1 ? ("The wobble grows every swing.", "Each push arrives a little late, adding energy instead of removing it, until the angle crosses 12°.")
                    : k == 2 ? ("The wobble dies down.", "Leading the angle by its spin damps the oscillation — a PD controller in one line.")
                    : ("No pattern, just drift.", "Random pushes cancel on average, so the pole falls as if unattended.")
                return fr([L("pole angle θ, first \(n - 1) steps · red = ±12°"), Rb.chart([Rb.Series(vals: Array(v), c: k == 2 ? "#22a06b" : "#f5c542", sz: 3)], 130, -14, 14, [(12, "#e5337a"), (-12, "#e5337a"), (0, "#3a3f4c")])],
                          [F("max |θ| =", deg(o.tr.prefix(n).map { abs($0.th) }.max()!))], cap, lg)
            case 3:
                let s = (0...2).map { j -> Double in var t = 0.0; for i in 1...50 { t += Double(cp(j, i).t) / 50 }; return t }
                return fr([L("mean episode length · 50 random starts"), B(["random", "lean", "lean + spin"].enumerated().map { j, n in
                    Rb.row(n, f(s[j], 1), s[j] / 500, j == 2 ? "#22a06b" : "#3b82f6", j == k ? "#f2f3f7" : "#9aa0ae")
                })], [F("solved =", "average ≥ 475 over 100 episodes", "#22a06b")],
                          ("One extra term separates \(f(s[1], 0)) steps from 500.", "That is why CartPole is a first benchmark: a learner has to discover the spin term from reward alone."), lg)
            default:
                return fr([S(st("observations", "4 continuous numbers"), st("actions", "2 discrete"), st("dynamics", "Euler step, τ = 0.02 s"), st("typical solve", "DQN or PPO in minutes on a CPU", "#22a06b"))],
                          [F("θ̈ = (g·sinθ − cosθ·(F + m_p·l·θ̇²·sinθ)/m) / (l·(4/3 − m_p·cos²θ/m))")],
                          ("Small, fast, unforgiving.", "Every curve on this screen came from the same equation the Gym environment uses."), lg)
            }
        }
    }
}

// MARK: - 63c Mountain Car

private struct Mc { let tr: [(p: Double, v: Double)]; let t: Int; let ok: Bool; let best: Double }

private let mcCache = RbCache<String, Mc>()

private func mc(_ k: Int, _ p0: Double, _ seed: Int) -> Mc {
    mcCache.get("\(k)_\(p0)_\(seed)") {
        let r = Rb.rng(Int64(seed) * 7 + 1)
        var p = p0
        var v = 0.0
        var best = p
        var tr = [(p: p, v: v)]
        var tt = 200
        var ok = false
        for t in 0..<200 {
            let a = k == 0 ? 2 : k == 1 ? Int(floor(r() * 3)) : (v >= 0 ? 2 : 0)
            v += Double(a - 1) * 0.001 - 0.0025 * cos(3 * p)
            v = max(-0.07, min(0.07, v))
            p += v
            if p < -1.2 { p = -1.2; v = 0 }
            best = max(best, p)
            tr.append((p, v))
            if p >= 0.5 { tt = t + 1; ok = true; break }
        }
        return Mc(tr: tr, t: tt, ok: ok, best: best)
    }
}

private func mcScene(_ tr: [(p: Double, v: Double)], _ t: Int) -> RbBlock {
    func x(_ p: Double) -> Double { 5 + 90 * (p + 1.2) / 1.8 }
    func y(_ p: Double) -> Double { 52 - 36 * sin(3 * p) }
    var pts: [RbPt] = []
    for i in 0...60 { let p = -1.2 + 1.8 * Double(i) / 60; pts.append(pt(x(p), y(p) + 6, 3, "#3a3f4c")) }
    pts.append(pt(x(0.5), y(0.5) - 4, 12, "#22a06b"))
    var j = max(0, t - 60)
    while j < t { pts.append(pt(x(tr[j].p), y(tr[j].p) + 2, 4, "rgba(245,197,66,.45)")); j += 3 }
    pts.append(pt(x(tr[t].p), y(tr[t].p) + 1, 15, "#3b82f6"))
    return .plot(h: 150, pts: pts)
}

private func mountainCarLab() -> RbLab {
    let lg = [("#3b82f6", "Car"), ("#f5c542", "Trail"), ("#22a06b", "Flag")]
    return RbLab(tabs: ["push right", "random", "pump"], initialTab: 0) { k in
        let o = mc(k, -0.5, 1)
        return (0..<5).map { stp in
            switch stp {
            case 0:
                return fr([mcScene(o.tr, 0), S(st("actions", "push left · none · push right"), st("engine", "0.001 per step"), st("gravity", "up to 0.0025 per step", "#e5337a"), st("reward", "−1 per step until the flag"))],
                          [F("v ← v + 0.001·(a − 1) − 0.0025·cos(3p)")],
                          ("The engine is weaker than the hill.", "The car starts in the valley at −0.50 and has 200 steps to reach the flag at 0.50."), lg)
            case 1:
                let t = o.tr.count - 1
                let p = o.tr[t]
                let cap: (String, String) = k == 0 ? ("Pushing at the goal never gets there.", "After 200 steps the car sits at \(f(p.p)) with velocity \(f(p.v, 4)). It climbs to \(f(o.best)), stalls, and settles.")
                    : k == 1 ? ("Random pushes go nowhere.", "Best position \(f(o.best)). The pushes average out, so the car only jiggles in the valley.")
                    : ("Rocking back and forth reaches the flag in \(o.t) steps.", "Push in whichever direction the car is already moving: each swing goes higher than the last.")
                return fr([mcScene(o.tr, t), C([Rb.tok("step \(t)", "cur"), Rb.tok("position \(f(p.p))"), Rb.tok("best \(f(o.best))", o.ok ? "done" : "plain")])],
                          [F("reached the flag:", o.ok ? "yes, step \(o.t)" : "no", o.ok ? "#22a06b" : "#e5337a")], cap, lg)
            case 2:
                return fr([L("position over time · green = flag"), Rb.chart([Rb.Series(vals: o.tr.map(\.p), c: o.ok ? "#22a06b" : "#f5c542", sz: 3)], 130, -1.2, 0.6, [(0.5, "#22a06b"), (-0.5, "#3a3f4c")])],
                          [F("steps =", "\(o.t)", o.ok ? "#22a06b" : "#e5337a")],
                          k == 2 ? ("Each swing is bigger than the last.", "The car backs up the left slope to build speed, then carries it over the right.")
                              : ("The trace flattens out.", "No energy is being added, so the car never gets more than part-way up."), lg)
            case 3:
                let starts = [-0.6, -0.58, -0.56, -0.54, -0.52, -0.5, -0.48, -0.46, -0.44, -0.42, -0.4]
                func okc(_ j: Int) -> Int { starts.indices.filter { mc(j, starts[$0], $0 + 1).ok }.count }
                let ms = starts.indices.reduce(0.0) { $0 + Double(mc(2, starts[$1], $1 + 1).t) } / Double(starts.count)
                return fr([L("reached the flag · 11 starts from −0.60 to −0.40"), B(["push right", "random", "pump"].enumerated().map { j, n in
                    let c = okc(j)
                    return Rb.row(n, "\(c) / 11", Double(c) / 11, j == 2 ? "#22a06b" : "#e5337a", j == k ? "#f2f3f7" : "#9aa0ae")
                })], [F("pump · mean steps =", f(ms, 0), "#22a06b")],
                          ("Only the counter-intuitive policy works.", "Pumping reaches the flag from every start, in about \(f(ms, 0)) steps."), lg)
            default:
                return fr([S(st("random reward seen", "never in 200 steps"), st("signal before success", "−1, −1, −1 … identical", "#e5337a"), st("what helps", "optimistic init, exploration bonus, shaping", "#22a06b"))],
                          [F("every failed episode returns exactly", "−200")],
                          ("A sparse-reward trap.", "Until the car hits the flag once, every episode scores −200 — there is nothing to tell good attempts from bad."), lg)
            }
        }
    }
}

// MARK: - 63d Atari: a toy Pong, 8 wide with 4 rows to fall

private let pongCache = RbCache<Int, (curve: [(Int, Double)], q: [String: [Double]])>()

private func pong(_ frames: Int) -> (curve: [(Int, Double)], q: [String: [Double]]) {
    pongCache.get(frames) {
        let r = Rb.rng(1)
        var q: [String: [Double]] = [:]
        func key(_ bx: Int, _ by: Int, _ px: Int, _ pbx: Int) -> String { frames == 1 ? "\(bx),\(by),\(px)" : "\(bx),\(by),\(px),\(pbx)" }
        func ep(_ greedy: Bool) -> Bool {
            var bx = Int(floor(r() * 8))
            var dx = r() < 0.5 ? -1 : 1
            var by = 0
            var px = Int(floor(r() * 8))
            var pbx = bx - dx
            for _ in 0..<4 {
                let k = key(bx, by, px, pbx)
                if q[k] == nil { q[k] = [0, 0, 0] }
                let qs = q[k]!
                let a: Int
                if !greedy && r() < 0.1 { a = Int(floor(r() * 3)) } else {
                    var b = 0
                    for i in 1..<3 where qs[i] > qs[b] + 1e-12 { b = i }
                    a = b
                }
                px = max(0, min(7, px + a - 1))
                pbx = bx
                bx += dx
                if bx < 0 { bx = 1; dx = 1 }
                if bx > 7 { bx = 6; dx = -1 }
                by += 1
                let done = by == 4
                let rw = done ? (bx == px ? 1.0 : -1.0) : 0
                if !greedy {
                    var next = 0.0
                    if !done {
                        let nk = key(bx, by, px, pbx)
                        if q[nk] == nil { q[nk] = [0, 0, 0] }
                        next = q[nk]!.max()!
                    }
                    q[k]![a] += 0.2 * (rw + next - q[k]![a])
                }
                if done { return rw > 0 }
            }
            return false
        }
        var curve: [(Int, Double)] = []
        for e in 1...3000 {
            _ = ep(false)
            if e == 100 || e == 300 || e == 1000 || e == 3000 {
                var w = 0
                for _ in 0..<400 where ep(true) { w += 1 }
                curve.append((e, Double(w) / 400))
            }
        }
        return (curve, q)
    }
}

private let pongBound: Double = {
    var c = 0
    var n = 0
    for bx in 0..<8 {
        for dx in [-1, 1] {
            for px in 0..<8 {
                var x = bx
                var d = dx
                for _ in 0..<4 {
                    x += d
                    if x < 0 { x = 1; d = 1 }
                    if x > 7 { x = 6; d = -1 }
                }
                n += 1
                if abs(x - px) <= 4 { c += 1 }
            }
        }
    }
    return Double(c) / Double(n)
}()

private func pgGrid(_ ball: (Int, Int)?, _ prev: (Int, Int)?, _ pad: Int, _ cand: [(Int, Int)]?) -> RbBlock {
    .grid(RbGrid(ch: 30, cols: [String](repeating: "", count: 8), rows: (0...4).map { r in
        RbGRow(label: "", lc: "#9aa0ae", cells: (0...7).map { c in
            let isB = ball.map { $0 == (r, c) } ?? false
            let isP = prev.map { $0 == (r, c) } ?? false
            let isPad = r == 4 && c == pad
            let isC = cand?.contains { $0 == (r, c) } ?? false
            return RbCell(t: "", bg: isB ? "#f5c542" : isP ? "rgba(245,197,66,.35)" : isPad ? "#22a06b" : "#2a2e39", color: "#fff", ring: isC ? "inset 0 0 0 2px #6d5dfc" : "none")
        })
    }))
}

private func atariLab() -> RbLab {
    let lg = [("#f5c542", "Ball"), ("#22a06b", "Paddle / 2 frames"), ("#6d5dfc", "Possible next")]
    let mvn = ["left", "stay", "right"]
    return RbLab(tabs: ["1 frame", "2 frames"], initialTab: 0) { opt in
        let fr2 = [1, 2][opt]
        let p1 = pong(1)
        let p2 = pong(2)
        let ub = pongBound
        return (0..<5).map { stp in
            switch stp {
            case 0:
                return fr([L("one frame: ball at r1,c3 · paddle at c4"), pgGrid((1, 3), nil, 4, [(2, 2), (2, 4)])], [F("possible next ball cells =", "2")],
                          ("A single frame is a position with no velocity.", "From this picture alone the ball could be heading left or right — both outlined cells are equally likely."), lg)
            case 1:
                return fr([L("stack the previous frame: faded = one step ago"), pgGrid((2, 4), (1, 3), 4, nil)], [F("ball c3 → c4 ·", "moving right", "#22a06b")],
                          ("Two frames make the direction visible.", "The difference between frames is the velocity — the reason DQN feeds the network a stack of recent frames."), lg)
            case 2:
                let rows = p1.curve.enumerated().flatMap { i, ew in
                    [Rb.row("\(ew.0) ep · 1 frame", pct(ew.1), ew.1, "#9aa0ae", fr2 == 1 ? "#f2f3f7" : "#9aa0ae"),
                     Rb.row("\(ew.0) ep · 2 frames", pct(p2.curve[i].1), p2.curve[i].1, "#22a06b", fr2 == 2 ? "#f2f3f7" : "#9aa0ae")]
                }.dropFirst(2)
                return fr([L("catch rate during Q-learning · 400 test serves"), B(Array(rows))],
                          [F("reachable serves =", pct(ub) + " · paddle moves 1 cell per step")],
                          ("One frame stalls at \(pct(p1.curve[3].1)); two frames reach \(pct(p2.curve[3].1)).", "Same learner, same 3,000 episodes — the only difference is whether the input contains the direction."), lg)
            case 3:
                let qs = p1.q["3,1,4"] ?? [0, 0, 0]
                let q2 = p2.q["3,1,4,2"] ?? [0, 0, 0]
                let q3 = p2.q["3,1,4,4"] ?? [0, 0, 0]
                return fr([
                    L("1-frame Q at the opening state"), B(mvn.enumerated().map { i, n in Rb.brow(n, qs[i], 1) }),
                    L("2-frame Q · ball moving right / left"), B(mvn.enumerated().flatMap { i, n in [Rb.brow("\(n) · →", q2[i], 1), Rb.brow("\(n) · ←", q3[i], 1)] }),
                ], [F("1-frame best =", mvn[Rb.argmax(qs)]), F("2-frame best · → / ← =", mvn[Rb.argmax(q2)] + " / " + mvn[Rb.argmax(q3)], "#22a06b")],
                          ("Without the direction, values average two different situations.", "With the previous frame the agent picks a different move for each direction — the 1-frame agent has to hedge."), lg)
            default:
                return fr([S(st("input", "84 × 84 grayscale"), st("stack", "last 4 frames"), st("actions", "up to 18 joystick moves"), st("frame skip", "repeat each action for 4 frames"))],
                          [F("observation =", "4 × 84 × 84 tensor")],
                          ("Partial observability, fixed with history.", "Frame stacking turns a POMDP back into something close enough to an MDP for Q-learning."), lg)
            }
        }
    }
}

// MARK: - 63e MuJoCo: pendulum swing-up on the Gym dynamics

private func pn(_ th: Double) -> Double {
    ((th + Double.pi).truncatingRemainder(dividingBy: 2 * Double.pi) + 2 * Double.pi).truncatingRemainder(dividingBy: 2 * Double.pi) - Double.pi
}

private let pdCache = RbCache<Int, (tr: [(a: Double, w: Double, u: Double)], cost: Double, up: Int)>()

private func pend(_ k: Int) -> (tr: [(a: Double, w: Double, u: Double)], cost: Double, up: Int) {
    pdCache.get(k) {
        var th = Double.pi
        var w = 0.0
        var tr = [(a: Double.pi, w: 0.0, u: 0.0)]
        var cost = 0.0
        var up = -1
        for t in 0..<200 {
            let a = pn(th)
            let e = 5 - (w * w / 6 + 5 * cos(a))
            let sgn: Double = e > 0 ? 1 : e < 0 ? -1 : 0
            var u = k == 0 || abs(a) < 0.5 ? -10 * a - 2 * w : (w >= 0 ? 1.0 : -1.0) * 2 * sgn
            u = max(-2, min(2, u))
            cost += a * a + 0.1 * w * w + 0.001 * u * u
            w += (15 * sin(th) + 3 * u) * 0.05
            w = max(-8, min(8, w))
            th += w * 0.05
            let na = pn(th)
            if up < 0 && abs(na) < 0.1 && abs(w) < 1 { up = t + 1 }
            tr.append((na, w, u))
        }
        return (tr, cost, up)
    }
}

private func pdScene(_ p: (a: Double, w: Double, u: Double)) -> RbBlock {
    let w = 320.0, h = 160.0, cx = 160.0, cy = 80.0, rr = 56.0
    func px(_ x: Double) -> Double { x / w * 100 }
    func py(_ y: Double) -> Double { y / h * 100 }
    var pts: [RbPt] = []
    for i in 0..<48 { let g = Double(i) / 48 * 2 * Double.pi; pts.append(pt(px(cx + rr * sin(g)), py(cy - rr * cos(g)), 3, "#3a3f4c")) }
    pts.append(pt(px(cx), py(cy - rr), 14, "transparent", "inset 0 0 0 2px #22a06b"))
    let bx = cx + rr * sin(p.a)
    let by = cy - rr * cos(p.a)
    pts += Rb.dline(px(cx), py(cy), px(bx), py(by), 10, 6, "#3b82f6")
    pts.append(pt(px(cx), py(cy), 9, "#f2f3f7"))
    pts.append(pt(px(bx), py(by), 16, "#f5c542"))
    return .plot(h: 160, pts: pts)
}

private func mujocoLab() -> RbLab {
    let lg = [("#f5c542", "Bob"), ("#3b82f6", "Arm"), ("#22a06b", "Upright target")]
    func deg(_ a: Double) -> String { f(abs(a) * 180 / Double.pi, 0) + "°" }
    return RbLab(tabs: ["push to top", "energy pump"], initialTab: 1) { k in
        let o = pend(k)
        return (0..<5).map { stp in
            switch stp {
            case 0:
                return fr([pdScene(o.tr[0]), S(st("action", "torque u ∈ [−2, 2] · continuous"), st("gravity at 90°", "15 rad/s²"), st("motor at full torque", "6 rad/s²", "#e5337a"))],
                          [F("ω̇ = 15·sinθ + 3·u")],
                          ("The motor cannot lift the arm directly.", "At full torque it supplies 6 rad/s² against gravity’s 15 at horizontal — the same trap as Mountain Car, with a continuous action."), lg)
            case 1:
                let p = o.tr[26]
                return fr([pdScene(p), C([Rb.tok("step 26", "cur"), Rb.tok(deg(p.a) + " from upright"), Rb.tok("ω \(f(p.w, 1)) rad/s"), Rb.tok("u \(f(p.u))")])],
                          [k == 1 ? F("u = 2 · sign(ω) · sign(E* − E) =", f(p.u), "#f5c542") : F("u = clip(−10·θ − 2·ω) =", f(p.u), "#f5c542")],
                          k == 0 ? ("Pushing straight at the top just saturates.", "At step 26 the arm is \(deg(p.a)) from upright with the motor pinned at \(f(p.u)) — it rises a little and falls back.")
                              : ("Energy pumping: push the way the arm is already swinging.", "At step 26 the arm is \(deg(p.a)) from upright, swinging at \(f(p.w, 1)) rad/s, with \(f(p.u)) applied while energy is below the upright level."), lg)
            case 2:
                return fr([L("angle from upright, 200 steps (10 s)"), Rb.chart([Rb.Series(vals: o.tr.map { abs($0.a) * 180 / Double.pi }, c: k == 1 ? "#22a06b" : "#f5c542", sz: 3)], 130, 0, 180, [(0, "#22a06b")])],
                          [F("upright and still at step", o.up > 0 ? "\(o.up)" : "never", o.up > 0 ? "#22a06b" : "#e5337a")],
                          k == 1 ? ("Swings grow until a PD catch at the top.", "Within 0.5 rad the controller switches to balancing; the arm is upright at step \(o.up).")
                              : ("It never gets past \(deg(o.tr.map { abs($0.a) }.min()!)).", "Without building momentum the arm oscillates near the bottom forever."), lg)
            case 3:
                let a = pend(0)
                let b = pend(1)
                return fr([L("Gym cost Σ θ² + 0.1·ω² + 0.001·u² · lower is better"), B([
                    Rb.row("push to top", f(a.cost, 0), 1, "#e5337a", k == 0 ? "#f2f3f7" : "#9aa0ae"),
                    Rb.row("energy pump", f(b.cost, 0), b.cost / a.cost, "#22a06b", k == 1 ? "#f2f3f7" : "#9aa0ae"),
                ])], [F("cost ratio =", f(b.cost / a.cost, 2), "#22a06b")],
                          ("Swinging away first costs less overall.", "Short-term cost goes up while the arm swings; it pays back once it is held upright."), lg)
            default:
                return fr([S(st("Pendulum", "1 torque"), st("HalfCheetah", "6 joint torques"), st("Humanoid", "17 joint torques"), st("algorithms", "DDPG, TD3, SAC, PPO", "#22a06b"))],
                          [F("continuous actions: no argmax over a table")],
                          ("MuJoCo tasks are this, with more joints.", "Every action is a vector of real-valued torques, which is why continuous-control methods were built around them."), lg)
            }
        }
    }
}

// MARK: - 63f StarCraft II: league training on rush–expand–defend

private let SCG: [String: [[Double]]] = [
    "symmetric": [[0, 1, -1], [-1, 0, 1], [1, -1, 0]],
    "rush ×2": [[0, 2, -1], [-2, 0, 1], [1, -1, 0]],
]

private let lgCache = RbCache<String, (ex: [Double], seq: [Int], counts: [Int])>()

private func league(_ g: String, _ full: Bool) -> (ex: [Double], seq: [Int], counts: [Int]) {
    lgCache.get(g + "\(full)") {
        let a = SCG[g]!
        var counts = [1, 0, 0]
        var ex: [Double] = []
        var seq = [0]
        var last = 0
        for _ in 1...300 {
            let tot = Double(counts.reduce(0, +))
            let opp = full ? counts.map { Double($0) / tot } : (0...2).map { $0 == last ? 1.0 : 0 }
            let v = (0...2).map { i in opp.indices.reduce(0.0) { $0 + opp[$1] * a[i][$1] } }
            var b = 0
            for i in 1..<3 where v[i] > v[b] + 1e-12 { b = i }
            counts[b] += 1
            last = b
            seq.append(b)
            let t2 = Double(counts.reduce(0, +))
            let pol = full ? counts.map { Double($0) / t2 } : (0...2).map { $0 == b ? 1.0 : 0 }
            ex.append((0...2).map { i in -pol.indices.reduce(0.0) { $0 + pol[$1] * a[$1][i] } }.max()!)
        }
        return (ex, seq, counts)
    }
}

private func payGrid(_ m: [[Double]], _ names: [String]) -> RbBlock {
    let mx = m.flatMap { $0 }.map { abs($0) }.max()!
    let sc = mx == 0 ? 1 : mx
    return .grid(RbGrid(ch: 34, cols: names, rows: m.enumerated().map { i, row in
        RbGRow(label: names[i], lc: "#9aa0ae", cells: row.map { v in
            let a = Rb.a2(0.15 + 0.7 * abs(v) / sc)
            return RbCell(t: f(v, 0), bg: abs(v) < 1e-9 ? "#1f232d" : v > 0 ? "rgba(59,130,246,\(a))" : "rgba(229,51,122,\(a))", color: "#fff")
        })
    }))
}

private func starcraftLab() -> RbLab {
    let lg = [("#e5337a", "Naive self-play"), ("#22a06b", "League"), ("#3b82f6", "Mixture")]
    let sn = ["rush", "expand", "defend"]
    return RbLab(tabs: ["symmetric", "rush ×2"], initialTab: 0) { opt in
        let g = ["symmetric", "rush ×2"][opt]
        let n = league(g, false)
        let lgd = league(g, true)
        let ne = g == "symmetric" ? [1.0 / 3, 1.0 / 3, 1.0 / 3] : [0.25, 0.25, 0.5]
        return (0..<5).map { stp in
            switch stp {
            case 0:
                return fr([L("payoff to the row strategy"), payGrid(SCG[g]!, sn)], [F("rush beats expand · expand beats defend · defend beats rush")],
                          ("A rock-paper-scissors core, as in real-time strategy.", "No strategy is best against everything; \(g == "symmetric" ? "every win is worth 1." : "here a successful rush wins double.")"), lg)
            case 1:
                return fr([
                    L("naive self-play: best response to the latest version"),
                    C(n.seq.prefix(8).enumerated().map { i, a in Rb.tok(sn[a], i == 7 ? "cur" : "plain") }),
                    Rb.chart([Rb.Series(vals: Array(n.ex.prefix(60)), c: "#e5337a", sz: 3)], 110, 0, 2.2, [(0, "#22a06b")]),
                ], [F("exploitability stays at", f(n.ex.dropFirst(5).min()!, 1) + " – " + f(n.ex.max()!, 1), "#e5337a")],
                          ("Naive self-play goes in circles.", "rush → defend → expand → rush … Each version beats the last and loses to the next; it never gets harder to exploit."), lg)
            case 2:
                return fr([L("exploitability by iteration · red naive, green league"),
                            Rb.chart([Rb.Series(vals: Array(n.ex.prefix(100)), c: "#e5337a", sz: 3), Rb.Series(vals: Array(lgd.ex.prefix(100)), c: "#22a06b", sz: 3)], 120, 0, 2.2, [(0, "#3a3f4c")])],
                          [F("league after 300 iterations =", f(lgd.ex[299], 3), "#22a06b")],
                          ("A league best-responds to every past version.", "Exploitability falls from \(f(lgd.ex[0], 2)) to \(f(lgd.ex[299], 3)) while naive self-play keeps cycling."), lg)
            case 3:
                let tot = Double(lgd.counts.reduce(0, +))
                return fr([L("league mixture after 300 iterations · Nash in label"), B(sn.enumerated().map { i, nm in
                    let x = Double(lgd.counts[i]) / tot
                    return Rb.row("\(nm) · \(pct(ne[i]))", pct(x), x / 0.6, abs(x - ne[i]) < 0.04 ? "#22a06b" : "#3b82f6")
                })], [F("Nash =", ne.map { pct($0) }.joined(separator: " / "))],
                          g == "symmetric" ? ("The league settles on an even mix.", "Close to ⅓ each — no single opponent can exploit it.")
                              : ("Rewarding rush more makes defend more common.", "Defend, rush’s counter, rises toward the 50% Nash share."), lg)
            default:
                return fr([S(st("main agents", "play the whole league", "#22a06b"), st("main exploiters", "target the current main agents"), st("league exploiters", "find holes in the whole league"), st("frozen copies", "kept so old strategies stay covered"))],
                          [F("AlphaStar: league training on top of imitation")],
                          ("AlphaStar used the same idea at scale.", "Keeping old and adversarial players around stops the main agent from forgetting how to beat strategies it once mastered."), lg)
            }
        }
    }
}

// MARK: - 63g Dota 2: factorised action heads vs a flat softmax

private let HEADS = [("action type", 8), ("target", 10), ("offset", 6), ("delay", 9)]

private let dotaCache = RbCache<String, [Double]>()

private func dota(_ nh: Int, _ fact: Bool, _ seed: Int) -> [Double] {
    dotaCache.get("\(nh)_\(fact)_\(seed)") {
        let sizes = HEADS.prefix(nh).map(\.1)
        let r = Rb.rng(Int64(seed))
        let cc = 4
        let tgt = (0..<cc).map { _ in sizes.map { Int(floor(r() * Double($0))) } }
        let jj = sizes.reduce(1, *)
        var thF = fact ? [[[Double]]](repeating: sizes.map { [Double](repeating: 0, count: $0) }, count: cc) : []
        var thJ = fact ? [] : [[Double]](repeating: [Double](repeating: 0, count: jj), count: cc)
        func sm(_ v: [Double]) -> [Double] { Rb.softmax(v) }
        func samp(_ p: [Double]) -> Int {
            let u = r()
            var a = 0.0
            for i in p.indices { a += p[i]; if u < a { return i } }
            return p.count - 1
        }
        func dec(_ j0: Int) -> [Int] {
            var j = j0
            var o = [Int](repeating: 0, count: sizes.count)
            for h in stride(from: sizes.count - 1, through: 0, by: -1) { o[h] = j % sizes[h]; j /= sizes[h] }
            return o
        }
        func score(_ acts: [Int], _ c: Int) -> Double { Double(acts.indices.filter { acts[$0] == tgt[c][$0] }.count) / Double(nh) }
        var base = 0.0
        var curve: [Double] = []
        for u in 1...600 {
            let c = Int(floor(r() * Double(cc)))
            let rw: Double
            if fact {
                let ps = thF[c].map(sm)
                let acts = ps.map(samp)
                rw = score(acts, c)
                let ad = rw - base
                for (h, p) in ps.enumerated() { for (i, pp) in p.enumerated() { thF[c][h][i] += 0.5 * ad * ((i == acts[h] ? 1 : 0) - pp) } }
            } else {
                let p = sm(thJ[c])
                let j = samp(p)
                rw = score(dec(j), c)
                let ad = rw - base
                for i in 0..<jj { thJ[c][i] += 0.5 * ad * ((i == j ? 1 : 0) - p[i]) }
            }
            base += 0.05 * (rw - base)
            if u % 50 == 0 {
                var er = 0.0
                for k in 0..<cc {
                    if fact {
                        er += thF[k].map(sm).enumerated().reduce(0.0) { $0 + $1.element[tgt[k][$1.offset]] } / Double(nh) / Double(cc)
                    } else {
                        let p = sm(thJ[k])
                        var e = 0.0
                        for i in 0..<jj { e += p[i] * score(dec(i), k) }
                        er += e / Double(cc)
                    }
                }
                curve.append(er)
            }
        }
        return curve
    }
}

private func dotaAvg(_ nh: Int, _ fact: Bool) -> [Double] {
    var s = [Double](repeating: 0, count: 12)
    for k in 1...3 { for (i, e) in dota(nh, fact, k).enumerated() { s[i] += e / 3 } }
    return s
}

private func dotaLab() -> RbLab {
    let lg = [("#22a06b", "Factorised"), ("#e5337a", "Flat softmax"), ("#3b82f6", "Head size")]
    return RbLab(tabs: ["2 heads", "3 heads", "4 heads"], initialTab: 1) { opt in
        let nh = [2, 3, 4][opt]
        let h = Array(HEADS.prefix(nh))
        let jj = h.reduce(1) { $0 * $1.1 }
        let sm = h.reduce(0) { $0 + $1.1 }
        let fc = dotaAvg(nh, true)
        let fl = dotaAvg(nh, false)
        return (0..<5).map { stp in
            switch stp {
            case 0:
                let mx = Double(h.map(\.1).max()!)
                return fr([
                    L("one decision = one choice per head"),
                    B(h.map { n, k in Rb.row(n, "\(k)", Double(k) / mx, "#3b82f6") }),
                    C([Rb.tok("joint actions = " + h.map { "\($0.1)" }.joined(separator: " × ") + " = " + Rb.num(Double(jj)), "ans")]),
                ], [F("flat softmax outputs =", Rb.num(Double(jj))), F("factorised heads output =", "\(sm)", "#22a06b")],
                          ("A game action is several choices made together.", "A flat policy needs one output per combination; separate heads need one per option."), lg)
            case 1:
                let hd = 512.0
                return fr([L("output-layer weights from a 512-unit core"), B([Rb.row("flat softmax", Rb.big(hd * Double(jj)), 1, "#e5337a"), Rb.row("factorised", Rb.big(hd * Double(sm)), hd * Double(sm) / (hd * Double(jj)), "#22a06b")])],
                          [F("512 × \(Rb.num(Double(jj))) vs 512 × \(sm)"), F("ratio =", f(Double(jj) / Double(sm), 1) + "×", "#22a06b")],
                          ("Factorising shrinks the output layer \(f(Double(jj) / Double(sm), 0))×.", "The gap multiplies with every head; with real game-sized heads a flat layer no longer fits in memory."), lg)
            case 2:
                return fr([L("expected reward over 600 REINFORCE updates · 3 seeds"),
                            Rb.chart([Rb.Series(vals: [0] + fl, c: "#e5337a", sz: 4), Rb.Series(vals: [0] + fc, c: "#22a06b", sz: 4)], 120, 0, 1, [(1, "#3a3f4c")])],
                          [F("update 600 · flat \(f(fl[11])) vs factorised", f(fc[11]), "#22a06b")],
                          ("Factorised heads learn; the flat policy barely moves.", "Reward = share of heads chosen correctly. Each head gets credit separately; a flat policy has to stumble on whole combinations."), lg)
            case 3:
                var rows: [RbRow] = []
                for n in [2, 3, 4] {
                    let a = dotaAvg(n, false)[11]
                    let b = dotaAvg(n, true)[11]
                    rows.append(Rb.row("\(n) heads · flat", f(a), a, "#e5337a", n == nh ? "#f2f3f7" : "#9aa0ae"))
                    rows.append(Rb.row("\(n) heads · factorised", f(b), b, "#22a06b", n == nh ? "#f2f3f7" : "#9aa0ae"))
                }
                return fr([L("expected reward after 600 updates"), B(rows)], [F("chance level with \(nh) heads ≈", f(h.reduce(0.0) { $0 + 1 / Double($1.1) } / Double(nh)))],
                          ("Every extra head widens the gap.", "The flat policy stays near chance as the joint space grows; factorised heads only slow a little."), lg)
            default:
                return fr([S(st("independent heads", "fast, but ignore interactions"), st("autoregressive heads", "each head sees earlier choices", "#22a06b"), st("used by", "OpenAI Five, AlphaStar"))],
                          [F("π(a) = Π_h π_h(a_h | s, a_<h)")],
                          ("Real agents condition later heads on earlier ones.", "Choosing the target after the action type keeps the output small while still allowing combinations that only make sense together."), lg)
            }
        }
    }
}
