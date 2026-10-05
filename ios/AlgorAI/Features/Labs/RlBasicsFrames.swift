import Foundation

// Port of RlBasicsFrames.kt: one 4×4 grid world shared across every screen — rollouts, returns, policy
// evaluation, Q-learning, value iteration, a bandit and a Bayes filter; then Bellman checks, DP sweeps,
// policy and value iteration on the known model, and Monte Carlo and TD learning on a slippery version
// scored against the exact answer. Drawn by RlBoardLabs.swift.

let rlBasicsTopicIds: Set<String> = [
    "agent_environment", "state_action_reward", "policy", "value_function", "q_function", "discount_factor",
    "exploration_exploitation", "pomdp", "bellman_equation", "dynamic_programming", "policy_iteration",
    "value_iteration", "monte_carlo_rl", "td_learning",
]

func rlBasicsLab(_ topicId: String) -> RbLab? {
    switch topicId {
    case "agent_environment": agentEnvLab()
    case "state_action_reward": sarLab()
    case "policy": policyLab()
    case "value_function": valueFnLab()
    case "q_function": qFnLab()
    case "discount_factor": discountLab()
    case "exploration_exploitation": exploreExploitLab()
    case "pomdp": pomdpLab()
    case "bellman_equation": bellmanLab()
    case "dynamic_programming": dpLab()
    case "policy_iteration": policyIterLab()
    case "value_iteration": valueIterLab()
    case "monte_carlo_rl": monteCarloLab()
    case "td_learning": tdLab()
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

private let AR = RbGw.AR
private let START = RbGw.START
private func key(_ s: RbPos) -> String { RbGw.key(s) }
private func term(_ s: RbPos) -> Bool { RbGw.term(s) }
private func mv(_ s: RbPos, _ a: Int, _ c: Double) -> RbGw.Mv { RbGw.mv(s, a, c) }
private func show(_ s: RbPos) -> String { RbGw.show(s) }
private let RING_START = "inset 0 0 0 1.5px #f5c542"

// MARK: - 57a Agent & Environment

private func agentEnvLab() -> RbLab {
    let lg = [("#f5c542", "Agent"), ("#22a06b", "Goal +1 / visited"), ("#e5484d", "Pit −1"), ("#3a3f4c", "Wall")]
    return RbLab(tabs: ["up first", "right first"], initialTab: 0) { opt in
        let p = [[0, 0, 0, 3, 3, 3], [3, 3, 3, 0, 0]][opt]
        var traj: [(s: RbPos, a: Int, r: Double, ns: RbPos)] = []
        var cur = START
        for a in p {
            let m = mv(cur, a, -0.04)
            traj.append((cur, a, m.r, m.ns))
            cur = m.ns
        }
        let n = p.count + 2
        let g = traj.reduce(0.0) { $0 + $1.r }
        return (0..<n).map { st0 in
            let t: Int? = st0 >= 1 && st0 <= p.count ? st0 - 1 : nil
            let at = st0 == 0 ? START : t != nil ? traj[t!].ns : cur
            let seen = Set(traj.prefix(t == nil ? (st0 == 0 ? 0 : p.count) : t! + 1).map { key($0.s) })
            let grid = RbGw.grid { _, k in
                if k == key(at) { return RbCell(t: "agent", bg: "#f5c542", color: "#1b1d24") }
                if seen.contains(k) { return RbCell(t: "·", bg: "rgba(34,160,107,.25)", color: "#5fd49b") }
                return RbGw.plainCell()
            }
            var blocks = [L("4×4 grid world · start bottom-left"), grid]
            let fx: [RbFx]
            let cap: (String, String)
            if st0 == 0 {
                fx = [F("agent → action  ·  environment → (s′, r)")]
                cap = ("Agent and environment talk through two channels.", "The agent sends one action. The environment answers with the next state and a reward. Nothing else passes between them.")
            } else if let t {
                let x = traj[t]
                blocks.append(S(st("agent sends", AR[x.a], "#f5c542"), st("env returns", "s′ = (\(show(x.ns))), r = \(x.r > 0 ? "+" : "")\(js(x.r))")))
                fx = [F("step \(t + 1): (\(show(x.s))) \(AR[x.a]) → (\(show(x.ns)))", "r = \(f(x.r))", x.r > 0 ? "#22a06b" : x.r < -0.5 ? "#e5484d" : "#c3c7d1")]
                cap = term(x.ns)
                    ? (x.r > 0 ? "The goal: episode over, +1." : "The pit: episode over, −1.", "A terminal state ends the episode; nothing more can be earned.")
                    : ("Step \(t + 1): the agent moves \(AR[x.a]).", "Every non-terminal step costs 0.04 — the environment’s way of saying “hurry up”.")
            } else {
                blocks.append(S(st("steps", "\(p.count)"), st("return G = Σ r", f(g), g > 0 ? "#22a06b" : "#e5484d")))
                fx = [F("G = \(traj.map { f($0.r) }.joined(separator: " + ")) =", f(g), g > 0 ? "#22a06b" : "#e5484d")]
                cap = ("Return \(f(g)).", opt == 0 ? "The long way round reaches the goal. Learning means finding actions that make this number large." : "Shorter, but it ends in the pit. Fewer steps is not the objective — total reward is.")
            }
            return fr(blocks, fx, cap, lg)
        }
    }
}

// MARK: - 57b State, Action, Reward

private func sarLab() -> RbLab {
    let lg = [("#22a06b", "Goal +1"), ("#e5484d", "Pit −1"), ("#f5c542", "Step cost")]
    return RbLab(tabs: ["cost 0", "cost −0.04", "cost −2.5"], initialTab: 2) { opt in
        let c = [0, -0.04, -2.5][opt]
        typealias T = (s: RbPos, a: Int, r: Double, ns: RbPos)
        func run(_ p: [Int]) -> [T] {
            var cur = START
            return p.map { a in let m = mv(cur, a, c); defer { cur = m.ns }; return (cur, a, m.r, m.ns) }
        }
        let safe = run([0, 0, 0, 3, 3, 3])
        let pit = run([0, 3, 3, 0, 3])
        func g(_ tr: [T]) -> Double { tr.reduce(0.0) { $0 + $1.r } }
        let rg = RbGw.grid { _, _ in RbGw.plainCell(f(c)) }
        func tup(_ tr: [T]) -> RbBlock {
            C(tr.map { x in Rb.tok("(\(show(x.s))) \(AR[x.a])", x.r > 0 ? "done" : x.r < -0.9 && term(x.ns) ? "err" : "plain", "r " + f(x.r)) })
        }
        return (0..<4).map { st0 in
            switch st0 {
            case 0:
                return fr([L("reward for entering each cell"), rg], [F("r = +1 goal · −1 pit · step cost", f(c), "#f5c542")],
                          ("Each step yields one tuple (s, a, r, s′).", c == 0 ? "With no step cost, the agent is indifferent to how long it takes." : c < -1 ? "A brutal step cost: every move hurts more than the pit." : "A small step cost turns “reach the goal” into “reach it soon”."), lg)
            case 1:
                return fr([L("route to the goal, 6 tuples"), tup(safe)], [F("G =", f(g(safe)), "#22a06b")], ("Goal route: return \(f(g(safe))).", "Five step costs, then +1."), lg)
            case 2:
                return fr([L("route into the pit, 5 tuples"), tup(pit)], [F("G =", f(g(pit)), "#e5484d")], ("Pit route: return \(f(g(pit))).", "Four step costs, then −1."), lg)
            default:
                let best = g(safe) >= g(pit)
                let sc = max(3, abs(g(pit)))
                return fr([B([Rb.brow("goal route", g(safe), sc), Rb.brow("pit route", g(pit), sc)])],
                          [F("better route:", best ? "goal" : "pit", best ? "#22a06b" : "#e5484d")],
                          best ? ("The goal wins.", "The reward design, not the map, decides what the agent should want.")
                               : ("The pit wins — reward design gone wrong.", "With step cost −2.5, ending fast beats ending well. Agents optimise exactly what you reward."), lg)
            }
        }
    }
}

// MARK: - 57c The Policy

private func policyLab() -> RbLab {
    let lg = [("#3b82f6", "Positive value"), ("#e5484d", "Negative value"), ("#f5c542", "Path / start")]
    let names = ["right, then up", "up, then right", "random"]
    return RbLab(tabs: ["right→up", "up→right", "random"], initialTab: 0) { opt in
        let nm = names[opt]
        let pi = RbGw.pol(nm)
        let v = RbGw.evalPi(pi, 0.9, -0.04, 200)
        func arrow(_ p: RbPos) -> String { nm == "random" ? "✣" : AR[pi(p)[0].0] }
        var path: [String] = []
        if nm != "random" {
            var cur = START
            var i = 0
            while i < 10 && !term(cur) { path.append(key(cur)); cur = mv(cur, pi(cur)[0].0, -0.04).ns; i += 1 }
        }
        let sk = key(START)
        return (0..<4).map { st0 in
            switch st0 {
            case 0:
                return fr([L("policy “\(nm)”"), RbGw.grid { p, k in RbGw.plainCell(arrow(p), k == sk ? RING_START : "none") }],
                          [F("π(s) → action, for every state")],
                          ("A policy is a rule: state in, action out.", nm == "random" ? "A stochastic policy: each of the four actions with probability 0.25." : "Deterministic: “\(nm)”."), lg)
            case 1:
                return fr([L("Vπ(s), γ = 0.9"), RbGw.grid { _, k in RbCell(t: f(v[k]!), bg: RbGw.vcol(v[k]!), color: "#fff") }],
                          [F("Vπ(start) =", f(v[sk]!), "#3b82f6")],
                          ("Every policy has a value at every state.", "Vπ(s): expected discounted return from s if you follow π."), lg)
            case 2:
                let cap: (String, String) = nm == "right, then up" ? ("Confidently wrong: straight into the pit.", "Along the bottom row, up the right column, −1.")
                    : nm == "up, then right" ? ("Up the left side, across the top, +1.", "The same rule format, the opposite outcome.")
                    : ("No fixed path: a random walk.", "It wanders until it hits a terminal; the step cost piles up on the way.")
                return fr([L("following π from the start"), RbGw.grid { p, k in path.contains(k) ? RbCell(t: arrow(p), bg: "rgba(245,197,66,.3)", color: "#f5c542") : RbGw.plainCell(arrow(p)) }],
                          [F("path length", nm == "random" ? "varies" : "\(path.count)")], cap, lg)
            default:
                return fr([L("Vπ(start) for three policies"), B(names.enumerated().map { j, n in
                    let x = RbGw.evalPi(RbGw.pol(n), 0.9, -0.04, 200)[sk]!
                    var r = Rb.brow(n, x, 1, f(x), j == opt ? "#f2f3f7" : "#9aa0ae")
                    if j != opt { r.bar = "#3a3f4c" }
                    return r
                })], [F("best of the three:", "up, then right", "#22a06b")],
                          ("Value lets you rank policies.", "Improving a policy means finding one with higher value — the whole game of RL."), lg)
            }
        }
    }
}

// MARK: - 57d Value Function

private func valueFnLab() -> RbLab {
    let lg = [("#3b82f6", "Positive"), ("#e5484d", "Negative"), ("#f5c542", "Start (backup shown)")]
    return RbLab(tabs: ["up→right", "right→up"], initialTab: 0) { opt in
        let nm = ["up, then right", "right, then up"][opt]
        let pi = RbGw.pol(nm)
        return (0..<7).map { step in
            let sw = [0, 1, 2, 3, 5, 10, 50][step]
            let v = RbGw.evalPi(pi, 0.9, -0.04, sw)
            let vp = RbGw.evalPi(pi, 0.9, -0.04, max(sw - 1, 0))
            let sk = key(START)
            let nx = mv(START, pi(START)[0].0, -0.04)
            let grid = RbGw.grid { p, k in
                let x = v[k] ?? 0
                return RbCell(t: f(x) + " " + AR[pi(p)[0].0], bg: sw > 0 ? RbGw.vcol(x) : "#1f232d", color: "#fff", ring: k == sk ? RING_START : "none")
            }
            let delta = RbGw.states.map { abs((v[key($0)] ?? 0) - (vp[key($0)] ?? 0)) }.max()!
            let fx = sw > 0 ? [F("V(start) = −0.04 + 0.9 × V(\(show(nx.ns))) =", f(v[sk] ?? 0, 3), "#3b82f6"), F("largest change this sweep =", f(delta, 3))]
                : [F("V₀ = 0 everywhere")]
            let cap: (String, String) = sw == 0 ? ("Iterative policy evaluation starts from zero.", "Policy fixed: “\(nm)”. Each sweep backs up every state one step.")
                : sw < 4 ? ("Sweep \(sw): value has travelled \(sw) cell\(sw > 1 ? "s" : "") from the terminals.", "No max anywhere — values flow backwards along this policy’s own path.")
                : sw < 50 ? ("Sweep \(sw): changes shrinking (\(f(delta, 3))).", "Each sweep is a contraction by γ = 0.9.")
                : ("Converged.", "V(start) = \(f(v[sk]!, 3)). This is Vπ — the value of this policy, good or bad.")
            return fr([L("Vπ after \(sw) sweep\(sw == 1 ? "" : "s")"), grid], fx, cap, lg)
        }
    }
}

// MARK: - 57e Q-Function, learned by Q-learning

private func qFnLab() -> RbLab {
    let lg = [("#3b82f6", "Positive Q"), ("#e5484d", "Negative Q"), ("#f5c542", "Start cell")]
    return RbLab(tabs: ["α 0.1", "α 0.5", "α 0.9"], initialTab: 1) { opt in
        let al = [0.1, 0.5, 0.9][opt]
        let rnd = Rb.rng(3)
        var q: [String: [Double]] = [:]
        var snaps: [Int: [String: [Double]]] = [0: [:]]
        for e in 1...300 {
            var cur = START
            var t = 0
            while t < 60 && !term(cur) {
                let k = key(cur)
                if q[k] == nil { q[k] = [0, 0, 0, 0] }
                let qs = q[k]!
                let a = rnd() < 0.2 ? Int(floor(rnd() * 4)) : Rb.argmax(qs)
                let m = mv(cur, a, -0.04)
                let nk = key(m.ns)
                if q[nk] == nil { q[nk] = [0, 0, 0, 0] }
                let tgt = m.r + (term(m.ns) ? 0 : 0.9 * q[nk]!.max()!)
                q[k]![a] += al * (tgt - q[k]![a])
                cur = m.ns
                t += 1
            }
            if [1, 10, 50, 300].contains(e) { snaps[e] = q }
        }
        let sk = key(START)
        return (0..<5).map { step in
            let ep = [0, 1, 10, 50, 300][step]
            let qq = snaps[ep]!
            let qs = qq[sk] ?? [0, 0, 0, 0]
            let grid = RbGw.grid { _, k in
                let v = qq[k] ?? [0, 0, 0, 0]
                let m = v.max()!
                let a = v.firstIndex(of: m)!
                return RbCell(t: ep > 0 ? f(m) + " " + AR[a] : "0", bg: ep > 0 ? RbGw.vcol(m) : "#1f232d", color: "#fff", ring: k == sk ? RING_START : "none")
            }
            let blocks = [L("max Q(s,a) and greedy action · episode \(ep)"), grid, B(qs.enumerated().map { i, v in Rb.brow("Q(start," + AR[i] + ")", v, 1) })]
            let fx = ep > 0 ? [F("Q ← Q + α(r + 0.9·max Q′ − Q), α =", js(al), "#8f84ff")] : [F("Q = 0 for all 13 × 4 pairs")]
            let best = qs.firstIndex(of: qs.max()!)!
            let cap: (String, String) = ep == 0 ? ("Q(s,a): the value of taking a, then acting well.", "Learned from experience by Q-learning with ε = 0.2 exploration — no model of the grid.")
                : ep < 50 ? ("Episode \(ep): values creep back from the terminals.", "Each update uses one transition; information travels one step per visit.")
                : ("Episode \(ep): greedy action at start is \(AR[best]).", "The policy is read straight off the table — argmax over four numbers, no lookahead.")
            return fr(blocks, fx, cap, lg)
        }
    }
}

// MARK: - 57f Horizon & Discount

private func discountLab() -> RbLab {
    let lg = [("#6d5dfc", "Discount weight"), ("#3b82f6", "Value"), ("#f5c542", "Start")]
    return RbLab(tabs: ["γ 0.5", "γ 0.9", "γ 0.99"], initialTab: 0) { opt in
        let g = [0.5, 0.9, 0.99][opt]
        let vi = RbGw.vi(g, -0.04)
        let sk = key(START)
        return (0..<4).map { st0 in
            switch st0 {
            case 0:
                return fr([L("weight on a reward k steps ahead: γᵏ"), B([1, 2, 4, 8, 16].map { k in Rb.row("k = \(k)", f(pow(g, Double(k)), 3), pow(g, Double(k)), "#6d5dfc") })],
                          [F("effective horizon ≈ 1 / (1 − γ) =", f(1 / (1 - g), 0) + " steps", "#8f84ff")],
                          ("γ = \(js(g)): a reward 8 steps away counts \(f(pow(g, 8), 2)).", "Discounting sets how far ahead the agent effectively plans."), lg)
            case 1:
                return fr([L("V*(s), γ = \(js(g))"), RbGw.grid { _, k in RbCell(t: f(vi.V[k]!), bg: RbGw.vcol(vi.V[k]!), color: "#fff", ring: k == sk ? RING_START : "none") }],
                          [F("V*(start) =", f(vi.V[sk]!, 3), "#3b82f6")],
                          (g < 0.6 ? "Value barely leaks out from the goal." : "Value spreads across the whole grid.", g < 0.6 ? "Two steps out, the +1 is worth less than the step cost." : "Distant states still feel the goal."), lg)
            case 2:
                return fr([L("optimal policy"), RbGw.grid { _, k in RbGw.plainCell(AR[vi.P[k]!], k == sk ? RING_START : "none") }],
                          [F("π*(start) =", AR[vi.P[sk]!], "#f5c542")],
                          ("The discount changes the plan, not just the numbers.", g < 0.6 ? "Far from the goal, the myopic agent sees nothing worth walking for." : "Every state points along a route to +1."), lg)
            default:
                return fr([L("V*(start) by γ"), B([0.5, 0.9, 0.99].map { x in
                    let v = RbGw.vi(x, -0.04).V[sk]!
                    var r = Rb.brow("γ " + js(x), v, 1, f(v, 3), x == g ? "#f2f3f7" : "#9aa0ae")
                    if x != g { r.bar = "#3a3f4c" }
                    return r
                })], [F("value iteration, 300 sweeps each")],
                          ("Higher γ, longer horizon, higher value at the start.", "γ near 1 is patient but learns slowly; small γ is quick but short-sighted."), lg)
            }
        }
    }
}

// MARK: - 57g Exploration vs Exploitation

private struct BanditSnap { let n: [Int]; let q: [Double]; let tot: Int }

private let banditM = [0.2, 0.5, 0.6, 0.8]

private func banditRun(_ e: Double, _ seed0: Int64, _ tt: Int) -> [Int: BanditSnap] {
    let rnd = Rb.rng(seed0)
    var n = [0, 0, 0, 0]
    var q = [0.0, 0.0, 0.0, 0.0]
    var tot = 0
    var snaps: [Int: BanditSnap] = [:]
    for t in 1...tt {
        let a = rnd() < e ? Int(floor(rnd() * 4)) : Rb.argmax(q)
        let r = rnd() < banditM[a] ? 1 : 0
        n[a] += 1
        q[a] += (Double(r) - q[a]) / Double(n[a])
        tot += r
        if t == 10 || t == 50 || t == 200 { snaps[t] = BanditSnap(n: n, q: q, tot: tot) }
    }
    return snaps
}

private let banditAvg: [Double] = [0.0, 0.1, 0.3].map { e in
    var a = 0.0
    for k in 0..<200 { a += Double(banditRun(e, 101 + Int64(k) * 7, 200)[200]!.tot) }
    return a / 200 / 200
}

private func exploreExploitLab() -> RbLab {
    let lg = [("#22a06b", "Truly best"), ("#3b82f6", "Estimate"), ("#6d5dfc", "Chosen ε")]
    let arms = ["A", "B", "C", "D"]
    return RbLab(tabs: ["ε 0", "ε 0.1", "ε 0.3"], initialTab: 0) { opt in
        let eps = [0.0, 0.1, 0.3][opt]
        let r = banditRun(eps, 17, 200)
        let avg = banditAvg
        return (0..<5).map { step in
            let tt = [0, 10, 50, 200, 200][step]
            let sn = tt > 0 ? r[tt]! : BanditSnap(n: [0, 0, 0, 0], q: [0, 0, 0, 0], tot: 0)
            if step < 4 {
                let blocks = [
                    L(tt > 0 ? "after \(tt) pulls: estimate (pulls)" : "4 slot machines, true win rates hidden"),
                    B(arms.enumerated().map { i, a in
                        Rb.row("\(a) · true \(js(banditM[i]))", tt > 0 ? "\(f(sn.q[i])) (\(sn.n[i]))" : "?", sn.q[i], i == 3 ? "#22a06b" : "#3b82f6", i == 3 ? "#f2f3f7" : "#c3c7d1")
                    }),
                ]
                let fx = tt > 0 ? [F("reward so far =", "\(sn.tot) / \(tt)"), F("pulls on best arm D =", pct(Double(sn.n[3]) / Double(tt)), "#22a06b")]
                    : [F("ε-greedy: explore with probability \(js(eps))")]
                let cap: (String, String) = tt == 0 ? ("Exploit what you know, or explore what you don’t?", "Estimates start at 0 and update with each pull’s win or loss.")
                    : eps == 0 ? ("Pure greed: \(pct(Double(sn.n[0]) / Double(tt))) of pulls on A.", "With ε = 0 it never tries another arm, so it never learns D is better.")
                    : ("\(pct(Double(sn.n[3]) / Double(tt))) of pulls on the best arm.", "Random exploration \(pct(eps)) of the time keeps every estimate improving.")
                return fr(blocks, fx, cap, lg)
            }
            return fr([L("average win rate over 200 runs × 200 pulls"), B([0.0, 0.1, 0.3].enumerated().map { j, e in
                Rb.row("ε " + js(e), f(avg[j], 3), avg[j] / 0.8, e == eps ? "#6d5dfc" : "#3a3f4c", e == eps ? "#f2f3f7" : "#9aa0ae")
            } + [Rb.row("always D", f(0.8, 2), 1, "#22a06b")])], [F("best possible = 0.80 (arm D)")],
                      ("A little exploration pays; a lot costs.", "ε = 0 gets stuck, ε = 0.3 wastes pulls on known-bad arms. The trade-off never goes away."), lg)
        }
    }
}

// MARK: - 57h POMDP: a Bayes filter over the grid

private func pomdpLab() -> RbLab {
    let lg = [("#3b82f6", "Belief"), ("#f5c542", "True position"), ("#3a3f4c", "Wall")]
    return RbLab(tabs: ["exact sensor", "10% noise"], initialTab: 0) { opt in
        let noise = [0, 0.1][opt]
        let cand = RbGw.states
        func walls(_ p: RbPos) -> Int { (0...3).filter { a in !RbGw.free(RbPos(r: p.r + RbGw.AC[a].0, c: p.c + RbGw.AC[a].1)) }.count }
        func parse(_ k: String) -> RbPos { let x = k.split(separator: ",").map { Int($0)! }; return RbPos(r: x[0], c: x[1]) }
        let truth = [RbPos(r: 3, c: 0), RbPos(r: 2, c: 0), RbPos(r: 1, c: 0)]
        // Each step is an observation (blocked-side count) or a move up (-1).
        let seq = [walls(truth[0]), -1, walls(truth[1]), -1, walls(truth[2])]
        var b = RbOrdered()
        for p in cand { b[key(p)] = 1.0 / Double(cand.count) }
        var hist: [(b: RbOrdered, o: Int?)] = [(b, nil)]
        for x in seq {
            var nb = RbOrdered()
            if x >= 0 {
                var z = 0.0
                for (k, v) in b.entries {
                    let l = walls(parse(k)) == x ? 1 - noise : noise / 3
                    nb[k] = v * l
                    z += nb[k]!
                }
                for k in nb.keys { nb[k] = nb[k]! / (z == 0 ? 1 : z) }
            } else {
                for (k, v) in b.entries {
                    let n = mv(parse(k), 0, 0).ns
                    if term(n) { continue }
                    nb[key(n)] = (nb[key(n)] ?? 0) + v
                }
                let z = nb.entries.reduce(0.0) { $0 + $1.1 }
                for k in nb.keys { nb[k] = nb[k]! / z }
            }
            b = nb
            hist.append((b, x))
        }
        return (0..<6).map { st0 in
            let h = hist[min(st0, 5)]
            let tk = key(truth[min(st0 / 2, 2)])
            let top = h.b.entries.stableSorted { $0.1 > $1.1 }[0]
            let grid = RbGw.grid { _, k in
                let v = h.b[k] ?? 0
                return RbCell(t: v > 0.005 ? f(v) : "", bg: v > 0.005 ? "rgba(59,130,246,\(js(0.15 + 0.8 * v)))" : "#1f232d", color: "#fff", ring: k == tk ? RING_START : "none")
            }
            let fx: [RbFx]
            var cap: (String, String)
            if let x = h.o {
                if x >= 0 {
                    fx = [F("sensor: blocked sides =", "\(x)", "#f5c542"), F("b′(s) ∝ P(o | s) · b(s)")]
                    cap = ("It senses \(x) blocked side\(x == 1 ? "" : "s").", noise > 0 ? "A 10%-noisy sensor: mismatching cells are down-weighted, not ruled out." : "Cells with a different count drop to zero.")
                } else {
                    fx = [F("action ↑: shift every candidate up")]
                    cap = ("It moves up — so does the whole belief.", "Each candidate moves as the agent would have; walls stop some of them.")
                }
            } else {
                fx = [F("b(s) = 1 /", "\(cand.count) each")]
                cap = ("The agent knows the map, not where it is.", "A POMDP replaces the state with a belief: a probability over every cell it might be in.")
            }
            if st0 >= 5 {
                cap = (top.1 > 0.95 ? "Localised: (\(top.0)) with \(pct(top.1))." : "Most likely (\(top.0)), \(pct(top.1)).", "Three observations and two moves — acting and sensing together narrow the belief.")
            }
            return fr([L("belief b(s) · yellow ring = true position"), grid], fx, cap, lg)
        }
    }
}

// MARK: - 58a Bellman Equation

private func bellmanLab() -> RbLab {
    let lg = [("#f5c542", "Checked state"), ("#3b82f6", "Value"), ("#6d5dfc", "Best action")]
    return RbLab(tabs: ["(0,2)", "(2,2)", "start"], initialTab: 0) { opt in
        let c = [RbPos(r: 0, c: 2), RbPos(r: 2, c: 2), RbPos(r: 3, c: 0)][opt]
        let ck = key(c)
        let v = RbGw.vi(0.9, -0.04).V
        let p = RbGw.greedy(v)
        let bk = RbGw.backups(v, c)
        let best = bk.dropFirst().reduce(bk[0]) { a, b in b.q > a.q ? b : a }
        let cs = show(c)
        return (0..<4).map { st0 in
            var blocks = [L("V*(s), γ 0.9 · checking (\(cs))"), RbGw.vgrid(v, st0 >= 2 ? p : nil, ck)]
            let fx: [RbFx]
            let cap: (String, String)
            switch st0 {
            case 0:
                fx = [F("V*(\(cs)) =", f(v[ck]!, 3), "#f5c542")]
                cap = ("The Bellman equation is a consistency check.", "At the optimum, every state’s value equals the best one-step reward plus the discounted value of where it lands.")
            case 1:
                blocks = [L("r + 0.9·V*(s′) for each action at (\(cs))"), B(bk.map { x in Rb.brow(AR[x.a] + " → (" + show(x.ns) + ")", x.q, 1, f(x.q, 3)) })]
                fx = [F("\(AR[best.a]): \(f(best.r)) + 0.9 × \(f(v[key(best.ns)] ?? 0, 3)) =", f(best.q, 3), "#8f84ff")]
                cap = ("Back up all four actions.", "Bumping a wall leaves you in place, so it scores the step cost plus your own value.")
            case 2:
                fx = [F("max_a Q = \(f(best.q, 3))  ·  V*(\(cs)) =", f(v[ck]!, 3), "#22a06b")]
                cap = ("The max equals the stored value.", "Consistent: V*(\(cs)) = \(f(v[ck]!, 3)). The arrow is simply the action that achieved the max.")
            default:
                let second = bk.stableSorted { $0.q > $1.q }[1]
                fx = [F("gap to next-best (\(AR[second.a])) =", f(best.q - second.q, 3), "#f5c542")]
                cap = ("Every other action falls short.", "If any state failed this check, V would not be optimal — that is what DP algorithms iterate on.")
            }
            return fr(blocks, fx, cap, lg)
        }
    }
}

// MARK: - 58b Dynamic Programming: synchronous vs in-place sweeps

private func dpRun(_ ip: Bool, _ kk: Int) -> (v: [String: Double], d: Double) {
    var v: [String: Double] = [:]
    var d = 0.0
    for _ in 0..<kk {
        var n = v
        d = 0
        for p in RbGw.states {
            let k = key(p)
            let nv = RbGw.backups(ip ? n : v, p).map(\.q).max()!
            d = max(d, abs(nv - (v[k] ?? 0)))
            n[k] = nv
        }
        v = n
    }
    return (v, d)
}

private func dpConv(_ ip: Bool) -> Int {
    for k in 1..<200 where dpRun(ip, k).d < 1e-4 { return k }
    return 200
}

private func dpLab() -> RbLab {
    let lg = [("#3b82f6", "Positive value"), ("#e5484d", "Negative"), ("#6d5dfc", "In-place")]
    return RbLab(tabs: ["synchronous", "in-place"], initialTab: 0) { opt in
        let inplace = opt == 1
        return (0..<8).map { st0 in
            if st0 < 7 {
                let kk = [0, 1, 2, 3, 4, 6, 10][st0]
                let r = dpRun(inplace, kk)
                return fr([L("V after \(kk) sweep\(kk == 1 ? "" : "s") (\(inplace ? "in-place" : "synchronous"))"), RbGw.vgrid(r.v, nil)],
                          kk > 0 ? [F("largest change this sweep =", f(r.d, 4), "#3b82f6")] : [F("V₀ = 0 · model P and R known")],
                          kk == 0 ? ("Dynamic programming needs the model.", "With P and R known, each sweep backs up every state from its neighbours — no experience needed.")
                              : ("Sweep \(kk): value has reached \(kk) step\(kk > 1 ? "s" : "") out.", inplace ? "In-place sweeps reuse values updated earlier in the same sweep, so information can travel further per pass." : "Synchronous sweeps read only last sweep’s values."), lg)
            }
            let a = dpConv(false)
            let b = dpConv(true)
            let m = Double(max(a, b))
            return fr([L("sweeps to converge (Δ < 0.0001)"), B([Rb.row("synchronous", "\(a)", Double(a) / m, "#3a3f4c"), Rb.row("in-place", "\(b)", Double(b) / m, "#6d5dfc", "#f2f3f7")])],
                      [F("saved:", "\(a - b) sweeps", "#8f84ff")],
                      ("Same answer, fewer passes in place.", "Sweep order matters for speed, never for the fixed point."), lg)
        }
    }
}

// MARK: - 58c Policy Iteration

private func policyIterLab() -> RbLab {
    let lg = [("#3b82f6", "Value"), ("#e5484d", "Negative"), ("#f5c542", "Start")]
    return RbLab(tabs: ["right, else up", "always left"], initialTab: 0) { opt in
        var initP: [String: Int] = [:]
        for p in RbGw.states { initP[key(p)] = opt == 0 ? (RbGw.free(RbPos(r: p.r, c: p.c + 1)) ? 3 : 0) : 2 }
        struct Ph { let t: String; let r: Int; let p: [String: Int]; let v: [String: Double]; var ch = 0 }
        var phases = [Ph(t: "init", r: 0, p: initP, v: [:])]
        var pp = initP
        for r in 1..<8 {
            let cur = pp
            let v = RbGw.evalPi({ s in [(cur[key(s)]!, 1)] }, 0.9, -0.04, 300)
            phases.append(Ph(t: "eval", r: r, p: cur, v: v))
            let np = RbGw.greedy(v)
            let ch = np.keys.filter { np[$0] != cur[$0] }.count
            phases.append(Ph(t: "imp", r: r, p: np, v: v, ch: ch))
            pp = np
            if ch == 0 { break }
        }
        let sk = key(START)
        return phases.map { ph in
            let fx: [RbFx]
            let cap: (String, String)
            let label: String
            switch ph.t {
            case "init":
                fx = [F("π₀ =", opt == 0 ? "right, else up" : "always left")]
                cap = ("Start from any policy at all.", opt == 0 ? "“Right if you can, else up” walks into the pit." : "“Always left” never reaches anything.")
                label = "initial policy"
            case "eval":
                fx = [F("Vπ\(ph.r - 1)(start) =", f(ph.v[sk] ?? 0, 3), "#3b82f6")]
                cap = ("Round \(ph.r): evaluate the current policy.", "Solve for its values exactly — here, 300 sweeps of the policy’s own backup.")
                label = "Vπ, round \(ph.r)"
            default:
                fx = [F("states whose action changed =", "\(ph.ch)", ph.ch > 0 ? "#f5c542" : "#22a06b")]
                cap = ph.ch > 0 ? ("Round \(ph.r): improve — act greedily on those values.", "\(ph.ch) states switch action. Each improvement is guaranteed not to lower any value.")
                    : ("Stable after \(ph.r) rounds: optimal.", "Greedy on its own values changes nothing, so this policy satisfies the Bellman optimality equation.")
                label = "improved policy, round \(ph.r)"
            }
            return fr([L(label), RbGw.vgrid(ph.v, ph.p, sk)], fx, cap, lg)
        }
    }
}

// MARK: - 58d Value Iteration

private func valueIterLab() -> RbLab {
    let lg = [("#3b82f6", "Value"), ("#e5484d", "Negative"), ("#f5c542", "Start")]
    return RbLab(tabs: ["γ 0.5", "γ 0.9", "γ 0.99"], initialTab: 1) { opt in
        let g = [0.5, 0.9, 0.99][opt]
        return (0..<8).map { st0 in
            let kk = [0, 1, 2, 3, 4, 6, 10, 30][st0]
            var v: [String: Double] = [:]
            var d = 0.0
            for _ in 0..<kk {
                var n: [String: Double] = [:]
                d = 0
                for p in RbGw.states {
                    let k = key(p)
                    let nv = (0...3).map { a -> Double in let m = mv(p, a, -0.04); return m.r + (term(m.ns) ? 0 : g * (v[key(m.ns)] ?? 0)) }.max()!
                    d = max(d, abs(nv - (v[k] ?? 0)))
                    n[k] = nv
                }
                v = n
            }
            var p: [String: Int]? = nil
            if kk > 0 {
                var o: [String: Int] = [:]
                for s in RbGw.states {
                    var bi = 0
                    var bv = -1e9
                    for a in 0...3 {
                        let m = mv(s, a, -0.04)
                        let q = m.r + (term(m.ns) ? 0 : g * (v[key(m.ns)] ?? 0))
                        if q > bv + 1e-9 { bv = q; bi = a }
                    }
                    o[key(s)] = bi
                }
                p = o
            }
            let sk = key(START)
            let fx = kk > 0 ? [F("V(s) ← max_a [r + γ·V(s′)]"), F("largest change =", f(d, 4), "#3b82f6")] : [F("V₀ = 0, no policy stored")]
            let cap: (String, String) = kk == 0 ? ("Value iteration: one backup per state, with a max.", "Policy iteration with the evaluation cut to a single sweep.")
                : kk < 6 ? ("Sweep \(kk): V(start) = \(f(v[sk] ?? 0, 3)).", "The greedy arrows already point the right way near the goal, long before the values settle.")
                : ("Sweep \(kk): change \(f(d, 4)).", "Error shrinks by γ = \(js(g)) per sweep — \(g > 0.95 ? "slowly" : "quickly"). Read the policy off V at the end.")
            return fr([L("V after \(kk) sweep\(kk == 1 ? "" : "s"), greedy arrows"), RbGw.vgrid(v, p, sk)], fx, cap, lg)
        }
    }
}

// MARK: - 58e Monte Carlo

private func rollSlip(_ pi: RbGw.Pi, _ slip: Double, _ rnd: () -> Double) -> [(s: String, r: Double)] {
    var cur = START
    var ep: [(s: String, r: Double)] = []
    var t = 0
    while t < 100 && !term(cur) {
        let a0 = pi(cur)[0].0
        let u = rnd()
        let a = u < 1 - slip ? a0 : u < 1 - slip / 2 ? RbGw.perp(a0)[0] : RbGw.perp(a0)[1]
        let m = mv(cur, a, -0.04)
        ep.append((key(cur), m.r))
        cur = m.ns
        t += 1
    }
    return ep
}

private func maxErr(_ v: [String: Double], _ exact: [String: Double]) -> Double {
    RbGw.states.map { abs((v[key($0)] ?? 0) - exact[key($0)]!) }.max()!
}

private func monteCarloLab() -> RbLab {
    let lg = [("#3b82f6", "Estimate"), ("#e5337a", "Error"), ("#f5c542", "Start")]
    return RbLab(tabs: ["slip 0.1", "slip 0.2", "slip 0.3"], initialTab: 1) { opt in
        let slip = [0.1, 0.2, 0.3][opt]
        let pi = RbGw.pol("up, then right")
        let exact = RbGw.evalSlip(pi, slip)
        let rnd = Rb.rng(9)
        var sum: [String: Double] = [:]
        var n: [String: Int] = [:]
        var snaps: [Int: [String: Double]] = [0: [:]]
        for e in 1...500 {
            let ep = rollSlip(pi, slip, rnd)
            var g = 0.0
            var gs = [Double](repeating: 0, count: ep.count)
            for t in stride(from: ep.count - 1, through: 0, by: -1) { g = ep[t].r + 0.9 * g; gs[t] = g }
            var seen = Set<String>()
            for (t, x) in ep.enumerated() where !seen.contains(x.s) {
                seen.insert(x.s)
                sum[x.s, default: 0] += gs[t]
                n[x.s, default: 0] += 1
            }
            if [1, 5, 20, 100, 500].contains(e) { snaps[e] = sum.reduce(into: [:]) { $0[$1.key] = $1.value / Double(n[$1.key]!) } }
        }
        return (0..<7).map { st0 in
            let ee = [0, 1, 5, 20, 100, 500][min(st0, 5)]
            let sv = snaps[ee]!
            let err = ee > 0 ? maxErr(sv, exact) : 0
            if st0 < 6 {
                let cap: (String, String) = ee == 0 ? ("Monte Carlo: learn from whole episodes.", "Moves slip sideways \(pct(slip)) of the time, so returns vary. The exact answer is shown first, to score against.")
                    : ee == 1 ? ("One episode: one return per state visited.", "States never visited stay at 0. No bootstrapping — only actual outcomes.")
                    : ("\(ee) episodes: error \(f(err, 3)).", "Averaging more returns shrinks the noise, roughly as 1/√n — but nothing is learned until an episode ends.")
                return fr([L(ee > 0 ? "V̂ from \(ee) episode\(ee > 1 ? "s" : "") of averaged returns" : "exact Vπ (for scoring only)"), RbGw.vgrid(ee > 0 ? sv : exact, nil, key(START))],
                          ee > 0 ? [F("V̂(s) = mean of returns observed from s"), F("largest error vs exact =", f(err, 3), "#e5337a")] : [F("policy “up, then right”, slip \(js(slip))")],
                          cap, lg)
            }
            return fr([L("largest error by episodes"), B([1, 5, 20, 100, 500].map { e in
                let er = maxErr(snaps[e]!, exact)
                return Rb.row("\(e) ep", f(er, 3), er / 1.2, "#e5337a")
            })], [F("unbiased, high variance")],
                      ("Monte Carlo is unbiased but noisy.", "It needs complete episodes and many of them. TD, next, trades some bias for much less variance."), lg)
        }
    }
}

// MARK: - 58f TD(0)

private func tdLab() -> RbLab {
    let lg = [("#3b82f6", "Estimate"), ("#6d5dfc", "Error"), ("#f5c542", "Start")]
    return RbLab(tabs: ["α 0.05", "α 0.1", "α 0.5"], initialTab: 1) { opt in
        let al = [0.05, 0.1, 0.5][opt]
        let pi = RbGw.pol("up, then right")
        let slip = 0.2
        let exact = RbGw.evalSlip(pi, slip)
        let rnd = Rb.rng(9)
        var v: [String: Double] = [:]
        var snaps: [Int: (v: [String: Double], u: Int)] = [0: ([:], 0)]
        var u = 0
        for e in 1...500 {
            var cur = START
            var t = 0
            while t < 100 && !term(cur) {
                let a0 = pi(cur)[0].0
                let r0 = rnd()
                let a = r0 < 1 - slip ? a0 : r0 < 1 - slip / 2 ? RbGw.perp(a0)[0] : RbGw.perp(a0)[1]
                let m = mv(cur, a, -0.04)
                let k = key(cur)
                let tg = m.r + (term(m.ns) ? 0 : 0.9 * (v[key(m.ns)] ?? 0))
                v[k] = (v[k] ?? 0) + al * (tg - (v[k] ?? 0))
                u += 1
                cur = m.ns
                t += 1
            }
            if [1, 5, 20, 100, 500].contains(e) { snaps[e] = (v, u) }
        }
        return (0..<7).map { st0 in
            let ee = [0, 1, 5, 20, 100, 500][min(st0, 5)]
            let sn = snaps[ee]!
            if st0 < 6 {
                let cap: (String, String) = ee == 0 ? ("Temporal difference: update after every step.", "Use your own next estimate as the target — bootstrapping — instead of waiting for the return.")
                    : ee == 1 ? ("After one episode, only the last step learned much.", "Value leaks back one state per visit, starting from the terminal it hit.")
                    : ("\(ee) episodes: error \(f(maxErr(sn.v, exact), 3)).", al > 0.3 ? "A large α learns fast but keeps jittering around the answer." : "Small steps: slower, smoother convergence.")
                return fr([L(ee > 0 ? "V̂ after \(ee) episode\(ee > 1 ? "s" : "") (\(sn.u) updates)" : "V̂ = 0"), RbGw.vgrid(sn.v, nil, key(START))],
                          ee > 0 ? [F("V(s) ← V(s) + \(js(al))·[r + 0.9·V(s′) − V(s)]"), F("largest error vs exact =", f(maxErr(sn.v, exact), 3), "#e5337a")] : [F("same policy and slip 0.2 as Monte Carlo")],
                          cap, lg)
            }
            return fr([L("largest error by episodes, α = " + js(al)), B([1, 5, 20, 100, 500].map { e in
                let x = maxErr(snaps[e]!.v, exact)
                return Rb.row("\(e) ep", f(x, 3), x / 1.2, "#6d5dfc")
            })], [F("biased early, low variance")],
                      ("TD learns online, from incomplete episodes.", "It is biased while V is wrong, but far less noisy than Monte Carlo — the basis of Q-learning and SARSA."), lg)
        }
    }
}
