import Foundation

// Port of RlModelFrames.kt: Dyna on the grid world, real UCT and PUCT search on a tic-tac-toe position
// checked against minimax, a world model learned from limited data, and the compounding-error maths
// behind MBPO. Drawn by RlBoardLabs.swift.

let rlModelTopicIds: Set<String> = ["dyna_q", "mcts", "alphago", "alphazero", "muzero", "world_models", "dreamer", "mbpo"]

func rlModelLab(_ topicId: String) -> RbLab? {
    switch topicId {
    case "dyna_q": dynaLab()
    case "mcts": mctsLab()
    case "alphago": alphaGoLab()
    case "alphazero": alphaZeroLab()
    case "muzero": muZeroLab()
    case "world_models": worldModelLab()
    case "dreamer": dreamerLab()
    case "mbpo": mbpoLab()
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

private let START = RbGw.START
private func key(_ s: RbPos) -> String { RbGw.key(s) }
private func term(_ s: RbPos) -> Bool { RbGw.term(s) }
private func mv(_ s: RbPos, _ a: Int, _ c: Double) -> RbGw.Mv { RbGw.mv(s, a, c) }

// MARK: - Tic-tac-toe: exact minimax and a UCT / PUCT search

enum RbTtt {
    private static let TL = [[0, 1, 2], [3, 4, 5], [6, 7, 8], [0, 3, 6], [1, 4, 7], [2, 5, 8], [0, 4, 8], [2, 4, 6]]

    /// X to move, two moves each: X in a and b, O in c and the bottom-right corner.
    static let TB = ["X", "", "O", "", "X", "", "", "", "O"]

    static func win(_ b: [String]) -> String? {
        for l in TL where !b[l[0]].isEmpty && b[l[0]] == b[l[1]] && b[l[0]] == b[l[2]] { return b[l[0]] }
        return b.allSatisfy { !$0.isEmpty } ? "D" : nil
    }

    static func toMove(_ b: [String]) -> String { b.filter { $0 == "X" }.count == b.filter { $0 == "O" }.count ? "X" : "O" }

    static func moves(_ b: [String]) -> [Int] { b.indices.filter { b[$0].isEmpty } }

    static func play(_ b: [String], _ m: Int) -> [String] { var c = b; c[m] = toMove(b); return c }

    static func score(_ w: String) -> Double { w == "X" ? 1 : w == "O" ? -1 : 0 }

    private static let mmCache = RbCache<String, Double>()

    static func minimax(_ b: [String]) -> Double {
        mmCache.get(b.map { $0.isEmpty ? "." : $0 }.joined()) {
            if let w = win(b) { return score(w) }
            let vs = moves(b).map { minimax(play(b, $0)) }
            return toMove(b) == "X" ? vs.max()! : vs.min()!
        }
    }

    static func best(_ b: [String]) -> (vals: [(Int, Double)], best: [Int]) {
        let v = moves(b).map { ($0, minimax(play(b, $0))) }
        let bv = v.map(\.1).max()!
        return (v, v.filter { $0.1 == bv }.map(\.0))
    }

    final class Node {
        let b: [String]
        let p: Double
        var n = 0
        var w = 0.0
        var kids: [(Int, Node)]? = nil
        init(_ b: [String], _ p: Double) { self.b = b; self.p = p }
    }

    struct Opts {
        let rand: () -> Double
        var at: [Int] = []
        var puct = false
        var prior: [Int: Double]? = nil
        var evalFn: (([String]) -> Double)? = nil
    }

    /// Root visit counts per move at each `at` budget, and at the end under -1.
    static func search(_ b: [String], _ sims: Int, _ o: Opts) -> [Int: [Int: Int]] {
        let r = o.rand
        let root = Node(b, 1)
        var snaps: [Int: [Int: Int]] = [:]
        func expand(_ n: Node) {
            let ms = moves(n.b)
            let pr = n === root ? o.prior : nil
            n.kids = ms.map { m in (m, Node(play(n.b, m), pr?[m] ?? 1.0 / Double(ms.count))) }
        }
        func rollout(_ bb: [String]) -> Double {
            var c = bb
            var w = win(c)
            while w == nil {
                let ms = moves(c)
                c[ms[Int(floor(r() * Double(ms.count)))]] = toMove(c)
                w = win(c)
            }
            return score(w!)
        }
        func vis() -> [Int: Int] { var m: [Int: Int] = [:]; for (mv, k) in root.kids! { m[mv] = k.n }; return m }
        for s in 1...sims {
            var n = root
            var path = [root]
            while n.kids != nil && win(n.b) == nil {
                let nn = Double(n.n)
                var best: Node? = nil
                var bs = -1e9
                for (_, c) in n.kids! {
                    let q = c.n > 0 ? c.w / Double(c.n) : o.puct ? 0.5 : 1e9
                    let sc = o.puct ? q + 1.5 * c.p * sqrt(nn + 1) / Double(1 + c.n) : q + (c.n > 0 ? 1.4 * sqrt(log(nn + 1) / Double(c.n)) : 0)
                    if sc > bs { bs = sc; best = c }
                }
                n = best!
                path.append(n)
            }
            let v: Double
            if let w = win(n.b) { v = score(w) } else {
                if n.kids == nil { expand(n) }
                v = o.evalFn?(n.b) ?? rollout(n.b)
            }
            for x in path {
                x.n += 1
                let mover = toMove(x.b) == "X" ? "O" : "X"
                x.w += ((mover == "X" ? v : -v) + 1) / 2
            }
            if o.at.contains(s) { snaps[s] = vis() }
        }
        snaps[-1] = vis()
        return snaps
    }

    static func grid(_ b: [String], _ vis: [Int: Int]?, _ hl: [Int]?) -> RbBlock {
        let tot = vis?.values.reduce(0, +) ?? 0
        return .grid(RbGrid(ch: 44, cols: ["a", "b", "c"], rows: (0...2).map { r in
            RbGRow(label: "", lc: "#9aa0ae", cells: (0...2).map { c in
                let i = r * 3 + c
                if !b[i].isEmpty { return RbCell(t: b[i], bg: "#3a3f4c", color: "#fff") }
                let n = vis?[i]
                return RbCell(t: n.map { "\($0)" } ?? "sq \(i + 1)",
                              bg: n != nil && tot > 0 ? "rgba(59,130,246,\(Rb.js(0.15 + 0.8 * Double(n!) / Double(tot))))" : "#1f232d", color: "#fff",
                              ring: hl?.contains(i) == true ? "inset 0 0 0 2px #22a06b" : "none")
            })
        }))
    }

    static func visBars(_ vis: [Int: Int], _ best: [Int], _ col: String? = nil) -> RbBlock {
        let tot = Double(vis.values.reduce(0, +))
        return .bars(vis.keys.sorted().map { m in
            Rb.row("sq \(m + 1)", "\(vis[m]!) · \(Rb.pct(Double(vis[m]!) / tot))", Double(vis[m]!) / tot, best.contains(m) ? (col ?? "#22a06b") : "#3b82f6", best.contains(m) ? "#f2f3f7" : "#c3c7d1")
        })
    }

    static func share(_ v: [Int: Int], _ best: [Int]) -> Double { Double(best.reduce(0) { $0 + (v[$1] ?? 0) }) / Double(v.values.reduce(0, +)) }

    /// The most visited move, the lowest square on a tie.
    static func pick(_ v: [Int: Int]) -> Int { v.keys.sorted().reduce(v.keys.min()!) { a, c in v[c]! > v[a]! ? c : a } }
}

// MARK: - 60a Dyna-Q

private let dynaCache = RbCache<Int, [(q: [String: [Double]], err: Double)]>()

/// Snapshots after each of 60 episodes (index 0 is before any); the first 30 are the 30-episode run.
func rbDyna(_ n: Int) -> [(q: [String: [Double]], err: Double)] {
    dynaCache.get(n) {
        let r = Rb.rng(41 + Int64(n))
        var q: [String: [Double]] = [:]
        var m: [String: RbGw.Mv] = [:]
        var keys: [String] = []
        let v = RbGw.vi(0.9, -0.04).V
        func qa(_ k: String) -> [Double] { if q[k] == nil { q[k] = [0, 0, 0, 0] }; return q[k]! }
        func err() -> Double { RbGw.states.reduce(0.0) { $0 + abs(qa(key($1)).max()! - v[key($1)]!) } / Double(RbGw.states.count) }
        func up(_ k: String, _ a: Int, _ ns: RbPos, _ rw: Double) {
            let t = rw + (term(ns) ? 0 : 0.9 * qa(key(ns)).max()!)
            _ = qa(k)
            q[k]![a] += 0.5 * (t - q[k]![a])
        }
        var snaps = [(q: [String: [Double]](), err: err())]
        for _ in 1...60 {
            var cur = START
            var t = 0
            while t < 100 && !term(cur) {
                let k = key(cur)
                let qs = qa(k)
                let a = r() < 0.1 ? Int(floor(r() * 4)) : Rb.argmax(qs)
                let o = mv(cur, a, -0.04)
                up(k, a, o.ns, o.r)
                let mk = "\(k)|\(a)"
                if m[mk] == nil { keys.append(mk) }
                m[mk] = o
                for _ in 0..<n {
                    let kk = keys[Int(floor(r() * Double(keys.count)))]
                    let parts = kk.split(separator: "|")
                    up(String(parts[0]), Int(parts[1])!, m[kk]!.ns, m[kk]!.r)
                }
                cur = o.ns
                t += 1
            }
            snaps.append((q, err()))
        }
        return snaps
    }
}

func rbQGrid(_ q: [String: [Double]]) -> RbBlock {
    RbGw.grid { _, k in
        let v = q[k] ?? [0, 0, 0, 0]
        let m = v.max()!
        return RbCell(t: abs(m) < 1e-9 ? "0" : f(m) + " " + RbGw.AR[v.firstIndex(of: m)!], bg: abs(m) < 1e-9 ? "#1f232d" : RbGw.vcol(m), color: "#fff",
                      ring: k == key(START) ? "inset 0 0 0 1.5px #f5c542" : "none")
    }
}

private func dynaLab() -> RbLab {
    let lg = [("#3b82f6", "Value"), ("#e5337a", "Error vs V*"), ("#f5c542", "Start")]
    return RbLab(tabs: ["plan 0", "plan 5", "plan 50"], initialTab: 1) { opt in
        let n = [0, 5, 50][opt]
        let sn = rbDyna(n)
        return (0..<7).map { stp in
            if stp < 6 {
                let e = [0, 1, 2, 5, 10, 30][stp]
                let cap: (String, String) = e == 0 ? ("Dyna-Q learns a model while it acts.", "Every real step updates Q and records what happened; then it replays imagined steps from that record.")
                    : n == 0 ? ("Episode \(e): plain Q-learning.", "Value creeps back one state per visit.")
                    : ("Episode \(e): \(n) imagined updates per real step.", "The same experience is reused many times, so value spreads from the goal in a few episodes.")
                return fr([L("max Q(s,a) after \(e) episode\(e == 1 ? "" : "s") · \(n) planning updates per step"), rbQGrid(sn[e].q)],
                          e > 0 ? [F("real step → Q update + model update"), F("mean |max Q − V*| =", f(sn[e].err, 3), "#e5337a")] : [F("model: (s, a) → (s′, r), learned from experience")],
                          cap, lg)
            }
            return fr([L("mean error after 5 episodes"), B([0, 5, 50].map { x in
                let e = rbDyna(x)[5].err
                return Rb.row(x > 0 ? "plan ×\(x)" : "no planning", f(e, 3), e / 0.6, x == n ? "#6d5dfc" : "#3a3f4c", x == n ? "#f2f3f7" : "#9aa0ae")
            })], [F("same real steps for all three")],
                      ("Planning substitutes compute for experience.", "Useful when real steps are expensive — and only as good as the learned model."), lg)
        }
    }
}

// MARK: - 60b MCTS

private func mctsLab() -> RbLab {
    let lg = [("#3b82f6", "Visits"), ("#22a06b", "Minimax-best"), ("#3a3f4c", "Occupied")]
    return RbLab(tabs: ["30 sims", "200 sims", "1,000 sims"], initialTab: 1) { opt in
        let nn = [30, 200, 1000][opt]
        let b = RbTtt.TB
        let tb = RbTtt.best(b)
        let third = Int(Rb.round(Double(nn) / 3))
        let sn = RbTtt.search(b, nn, RbTtt.Opts(rand: Rb.rng(7), at: [10, third, nn]))
        return (0..<5).map { stp in
            let at = [0, 10, third, nn][min(stp, 3)]
            if stp == 0 {
                return fr([L("X to move · green ring = minimax-best"), RbTtt.grid(b, nil, tb.best)],
                          [F("legal moves:", "\(RbTtt.moves(b).count)"), F("minimax best:", tb.best.map { "sq \($0 + 1)" }.joined(separator: ", "), "#22a06b")],
                          ("Monte Carlo tree search: plan by simulation.", "Select by UCB, expand one node, play a random game to the end, back the result up."), lg)
            }
            if stp < 4 {
                let vis = sn[at]!
                let share = RbTtt.share(vis, tb.best)
                return fr([L("visits after \(at) simulations"), RbTtt.grid(b, vis, tb.best), RbTtt.visBars(vis, tb.best)],
                          [F("share on best move =", pct(share), "#22a06b")],
                          ("\(at) simulations: \(pct(share)) of visits on the best move.", "UCB keeps trying promising moves more often, so visits concentrate where results are good."), lg)
            }
            let pick = RbTtt.pick(sn[-1]!)
            let ok = tb.best.contains(pick)
            return fr([L("minimax value of each move (X’s view)"), B(tb.vals.map { m, v in Rb.brow("sq \(m + 1)", v, 1, v > 0 ? "win" : v < 0 ? "loss" : "draw") })],
                      [F("MCTS picks:", "sq \(pick + 1)", ok ? "#22a06b" : "#e5484d")],
                      (ok ? "Search found the winning move." : "Not enough simulations yet.", "Random playouts are crude evaluators, but averaged over many games they rank moves correctly here."), lg)
        }
    }
}

// MARK: - 60c AlphaGo: a value network in place of playouts

private func evalN(_ sig: Double, _ r: @escaping () -> Double) -> ([String]) -> Double {
    { b in max(-1, min(1, RbTtt.minimax(b) + sig * Rb.gauss(r))) }
}

private let agCache = RbCache<String, [Int: Int]>()

private func agSearch(_ sig: Double?) -> [Int: Int] {
    agCache.get(sig.map { "\($0)" } ?? "plain") {
        let o = sig == nil ? RbTtt.Opts(rand: Rb.rng(3)) : RbTtt.Opts(rand: Rb.rng(3), evalFn: evalN(sig!, Rb.rng(99)))
        return RbTtt.search(RbTtt.TB, 100, o)[-1]!
    }
}

private func alphaGoLab() -> RbLab {
    let lg = [("#9aa0ae", "Playouts"), ("#22a06b", "Value-guided / best"), ("#3b82f6", "Other moves")]
    return RbLab(tabs: ["σ 0", "σ 0.3", "σ 0.8"], initialTab: 1) { opt in
        let sig = [0, 0.3, 0.8][opt]
        let b = RbTtt.TB
        let best = RbTtt.best(b).best
        let plain = agSearch(nil)
        let mine = agSearch(sig)
        func share(_ v: [Int: Int]) -> Double { RbTtt.share(v, best) }
        return (0..<4).map { stp in
            switch stp {
            case 0:
                return fr([L("same position, 100 simulations each"), RbTtt.grid(b, nil, best)], [F("AlphaGo = MCTS + policy net + value net")],
                          ("AlphaGo kept MCTS and replaced its guesswork.", "A value network judges a position directly instead of finishing the game at random."), lg)
            case 1:
                return fr([L("plain MCTS: random playouts"), RbTtt.visBars(plain, best, "#9aa0ae")], [F("share on best =", pct(share(plain)))],
                          ("Baseline: random playouts.", "On a 3×3 board random games are a fair estimate; on Go they are nearly noise."), lg)
            case 2:
                return fr([L("value network (noise σ = \(js(sig)))"), RbTtt.visBars(mine, best)],
                          [F("v(s) = true value + noise σ =", js(sig)), F("share on best =", pct(share(mine)), "#22a06b")],
                          (sig == 0 ? "A noise-free evaluator puts \(pct(share(mine))) on the best move." : "With noise σ \(js(sig)): \(pct(share(mine))) on the best move.", "The “network” here is the exact game value plus noise — a stand-in that shows how evaluator quality drives search."), lg)
            default:
                let all = [("playouts", plain)] + [0, 0.3, 0.8].map { ("value σ \(js($0))", agSearch($0)) }
                return fr([L("share of visits on the best move"), B(all.enumerated().map { j, x in
                    Rb.row(x.0, pct(share(x.1)), share(x.1), j == 0 ? "#9aa0ae" : "#22a06b")
                })], [F("less evaluator noise → more visits on the best move")],
                          ("Here random playouts (\(pct(share(plain)))) beat every value net.", "On 3×3, a few random games already rank moves well. AlphaGo’s gain came on 19×19, where playouts are nearly noise and a learned evaluator is the only usable one."), lg)
            }
        }
    }
}

// MARK: - 60d AlphaZero: PUCT with a prior

private func alphaZeroLab() -> RbLab {
    let lg = [("#9aa0ae", "Prior"), ("#22a06b", "Search / best"), ("#3b82f6", "Other moves")]
    return RbLab(tabs: ["uniform", "mediocre", "misleading"], initialTab: 1) { opt in
        let b = RbTtt.TB
        let best = RbTtt.best(b).best
        let ms = RbTtt.moves(b)
        var pr: [Int: Double] = [:]
        for m in ms {
            switch opt {
            case 0: pr[m] = 1.0 / Double(ms.count)
            case 1: pr[m] = best.contains(m) ? 0.22 : 0.78 / Double(ms.count - 1)
            default: pr[m] = best.contains(m) ? 0.04 : 0.96 / Double(ms.count - 1)
            }
        }
        let sn = RbTtt.search(b, 400, RbTtt.Opts(rand: Rb.rng(5), at: [50, 400], puct: true, prior: pr, evalFn: { RbTtt.minimax($0) }))
        let prBest = best.reduce(0.0) { $0 + pr[$1]! }
        return (0..<4).map { stp in
            if stp == 0 {
                return fr([L("prior π from the policy network"), B(ms.map { m in Rb.row("sq \(m + 1)", pct(pr[m]!), pr[m]!, best.contains(m) ? "#22a06b" : "#9aa0ae") })],
                          [F("prior on best move =", pct(prBest))],
                          ("AlphaZero: one network gives a prior and a value.", "No rollouts, no human games — PUCT search guided by the prior."), lg)
            }
            if stp < 3 {
                let at = stp == 1 ? 50 : 400
                let v = sn[at]!
                return fr([L("visits after \(at) PUCT simulations"), RbTtt.grid(b, v, best), RbTtt.visBars(v, best)],
                          [F("score = Q + 1.5·P·√N / (1 + n)"), F("share on best =", pct(RbTtt.share(v, best)), "#22a06b")],
                          ("\(at) simulations: \(pct(RbTtt.share(v, best))) on the best move.", opt == 2 ? "A misleading prior slows the search, but value backups still pull visits to the winner." : "The prior decides where to look first; values decide where to stay."), lg)
            }
            let fin = RbTtt.share(sn[-1]!, best)
            return fr([B([Rb.row("prior", pct(prBest), prBest, "#9aa0ae"), Rb.row("search visits", pct(fin), fin, "#22a06b", "#f2f3f7")])],
                      [F("training target for π = search visit distribution")],
                      ("Search turns a mediocre policy into a better one.", "AlphaZero trains the network to match the search’s visits — and repeats. Self-play is policy improvement."), lg)
        }
    }
}

// MARK: - 60e MuZero: search in an imperfect model

private let mzCache = RbCache<Double, Double>()

private func muZeroAcc(_ err: Double) -> Double {
    mzCache.get(err) {
        let b = RbTtt.TB
        let best = RbTtt.best(b).best
        var ok = 0
        for t in 0..<40 {
            let r = Rb.rng(200 + Int64(t))
            let fn: ([String]) -> Double = { bb in r() < err ? [-1.0, 0, 1][Int(floor(r() * 3))] : RbTtt.minimax(bb) }
            let v = RbTtt.search(b, 120, RbTtt.Opts(rand: r, puct: true, evalFn: fn))[-1]!
            if best.contains(RbTtt.pick(v)) { ok += 1 }
        }
        return Double(ok) / 40
    }
}

private func muZeroLab() -> RbLab {
    let lg = [("#22a06b", "Correct pick / best"), ("#6d5dfc", "Chosen error")]
    return RbLab(tabs: ["error 0%", "10%", "30%"], initialTab: 1) { opt in
        let e = [0, 0.1, 0.3][opt]
        let b = RbTtt.TB
        let best = RbTtt.best(b).best
        return (0..<4).map { stp in
            switch stp {
            case 0:
                return fr([RbTtt.grid(b, nil, best), S(st("AlphaZero", "needs the rules to simulate moves"), st("MuZero", "learns a model of what matters"))],
                          [F("h = repr(obs) · h′,r = dyn(h,a) · p,v = pred(h)")],
                          ("MuZero searches without the rules.", "It learns a hidden-state model that only has to predict reward, value and policy — not the board."), lg)
            case 1:
                return fr([L("unrolled in latent space"), C([Rb.tok("h₀", "plain", "repr(board)"), Rb.tok("a: sq \(best[0] + 1)", "cur"), Rb.tok("h₁", "ans", "r, v, p"), Rb.tok("a′", "cur"), Rb.tok("h₂", "ans", "r, v, p")])],
                          [F("no board is ever reconstructed")],
                          ("Planning happens on learned states.", "Each hidden state only needs to be good enough to predict what search will ask for."), lg)
            case 2:
                let a = muZeroAcc(e)
                return fr([B([Rb.row("picks best move", pct(a), a, "#22a06b", "#f2f3f7")])],
                          [F("model error rate \(pct(e)) → accuracy", pct(a), "#22a06b")],
                          ("With \(pct(e)) model error, search picks the best move \(pct(a)) of the time.", "40 searches of 120 simulations each, every value from the imperfect model."), lg)
            default:
                return fr([L("accuracy by model error"), B([0, 0.1, 0.3].map { x in
                    let a = muZeroAcc(x)
                    return Rb.row("error \(pct(x))", pct(a), a, x == e ? "#6d5dfc" : "#3a3f4c", x == e ? "#f2f3f7" : "#9aa0ae")
                })], [F("search averages out some model error")],
                          ("Search is robust to a mostly-right model.", "MuZero matched AlphaZero on Go, chess and shogi and extended to Atari, where no rules are available to search with."), lg)
            }
        }
    }
}

// MARK: - 60f World Models

private struct Wm { let m: [String: RbGw.Mv]; let v: [String: Double]; let p: [String: Int]; let known: Int; let out: String; let n: Int }

private let wmCache = RbCache<Int, Wm>()

private func wm(_ steps: Int) -> Wm {
    wmCache.get(steps) {
        let r = Rb.rng(17)
        var m: [String: RbGw.Mv] = [:]
        var cur = START
        for _ in 0..<steps {
            let a = Int(floor(r() * 4))
            let o = mv(cur, a, -0.04)
            m[key(cur) + "|" + "\(a)"] = o
            cur = term(o.ns) ? START : o.ns
        }
        func q(_ v: [String: Double], _ kk: String, _ a: Int) -> Double {
            guard let o = m[kk + "|" + "\(a)"] else { return -0.04 + 0.9 * (v[kk] ?? 0) }
            return o.r + (term(o.ns) ? 0 : 0.9 * (v[key(o.ns)] ?? 0))
        }
        var v: [String: Double] = [:]
        for _ in 0..<200 {
            var n: [String: Double] = [:]
            for p in RbGw.states { n[key(p)] = (0...3).map { q(v, key(p), $0) }.max()! }
            v = n
        }
        var p: [String: Int] = [:]
        for s in RbGw.states {
            var bi = 0
            var bv = -1e9
            for a in 0...3 {
                let x = q(v, key(s), a)
                if x > bv + 1e-9 { bv = x; bi = a }
            }
            p[key(s)] = bi
        }
        var c = START
        var out = "timeout"
        var n = 0
        while n < 20 && !term(c) { c = mv(c, p[key(c)]!, -0.04).ns; n += 1 }
        if term(c) { out = key(c) == RbGw.GOAL ? "goal" : "pit" }
        return Wm(m: m, v: v, p: p, known: m.count, out: out, n: n)
    }
}

private func worldModelLab() -> RbLab {
    let lg = [("#3b82f6", "Observed / value"), ("#22a06b", "Goal reached"), ("#e5484d", "Failure")]
    return RbLab(tabs: ["8 steps", "40 steps", "120 steps"], initialTab: 1) { opt in
        let tt = [8, 40, 120][opt]
        let w = wm(tt)
        return (0..<4).map { stp in
            switch stp {
            case 0:
                return fr([L("share of (state, action) pairs ever observed"), B([8, 20, 40, 120].map { x in
                    let k = Double(wm(x).known) / 52
                    return Rb.row("\(x) steps", pct(k), k, x == tt ? "#3b82f6" : "#3a3f4c", x == tt ? "#f2f3f7" : "#9aa0ae")
                })], [F("13 states × 4 actions =", "52 pairs")],
                          ("A world model predicts what happens next.", "Here it is a table filled in from random real steps. It only knows what it has seen."), lg)
            case 1:
                return fr([L("actions known per cell after \(tt) real steps"), RbGw.grid { _, k in
                    let n = (0...3).filter { w.m[k + "|" + "\($0)"] != nil }.count
                    return RbCell(t: "\(n)/4", bg: "rgba(59,130,246,\(js(0.1 + 0.2 * Double(n))))", color: "#fff")
                }], [F("known pairs =", "\(w.known) / 52", "#3b82f6")],
                          ("\(pct(Double(w.known) / 52)) of the model observed.", "Unknown actions are assumed to go nowhere — a guess the planner can’t check."), lg)
            case 2:
                return fr([L("plan inside the model (value iteration)"), RbGw.vgrid(w.v, w.p, key(START))], [F("planning cost: zero real steps")],
                          ("Planning is free inside the model.", "The plan is optimal for the model — which is not the same as optimal for the world."), lg)
            default:
                let good = w.out == "goal"
                return fr([S(st("executed in the real grid", good ? "reached the goal" : w.out == "pit" ? "fell in the pit" : "got stuck", good ? "#22a06b" : "#e5484d"), st("steps", "\(w.n)"))],
                          [F("outcome:", w.out, good ? "#22a06b" : "#e5484d")],
                          good ? ("The imagined plan works for real.", "With enough coverage, the model is right where it matters.")
                              : ("The plan fails in reality.", "Gaps in the model become blind spots in the plan. More real data, or uncertainty-aware planning, fixes it."), lg)
            }
        }
    }
}

// MARK: - 60g Dreamer

private func dreamerConv(_ x: Int) -> Int {
    let sn = rbDyna(x)
    for e in 1...60 where sn[e].err < 0.05 { return e }
    return 60
}

private func dreamerLab() -> RbLab {
    let lg = [("#9aa0ae", "Model-free"), ("#22a06b", "With imagination"), ("#6d5dfc", "Chosen")]
    return RbLab(tabs: ["model-free", "imagine ×5", "imagine ×20"], initialTab: 2) { opt in
        let n = [0, 5, 20][opt]
        return (0..<4).map { stp in
            switch stp {
            case 0:
                return fr([S(st("world model", "learned from real steps"), st("actor + critic", "trained only on imagined rollouts"), st("real data", "used to improve the model"))],
                          [F("Dreamer: learn behaviour in imagination")],
                          ("Dreamer trains its policy inside its own model.", "Real experience improves the world model; the actor-critic learns from imagined trajectories."), lg)
            case 1:
                let sn = rbDyna(n)
                return fr([L("mean |Q − Q*| by episode, \(n) imagined updates per step"), B([1, 2, 5, 10, 20].map { e in Rb.row("ep \(e)", f(sn[e].err, 3), sn[e].err / 0.6, n > 0 ? "#22a06b" : "#9aa0ae") })],
                          [F("episodes to error < 0.05 =", "\(dreamerConv(n))", "#22a06b")],
                          ("\(dreamerConv(n)) episodes to converge.", n > 0 ? "Imagined updates do most of the learning." : "Model-free: every bit of learning costs a real step."), lg)
            case 2:
                return fr([L("episodes to converge"), B([0, 5, 20].map { x in
                    Rb.row(x > 0 ? "imagine ×\(x)" : "model-free", "\(dreamerConv(x))", Double(dreamerConv(x)) / 60, x == n ? "#6d5dfc" : "#3a3f4c", x == n ? "#f2f3f7" : "#9aa0ae")
                })], [F("same real interaction per episode")],
                          ("More imagination, fewer real episodes.", "The real steps are unchanged; only the thinking per step went up."), lg)
            default:
                return fr([S(st("V1", "continuous latent, learns from pixels"), st("V2", "discrete latents, Atari"), st("V3", "fixed hyperparameters across domains"))],
                          [F("tabular stand-in for a learned latent model")],
                          ("Dreamer V1–V3 scale this idea to pixels.", "The model is a recurrent latent network; this grid uses a table so every number is checkable."), lg)
            }
        }
    }
}

// MARK: - 60h MBPO

private func mbpoLab() -> RbLab {
    let lg = [("#e5337a", "Compounding error"), ("#22a06b", "Best rollout length"), ("#3b82f6", "Useful data")]
    return RbLab(tabs: ["ε 5%", "ε 10%", "ε 20%"], initialTab: 1) { opt in
        let e = [0.05, 0.1, 0.2][opt]
        let ks = [1, 2, 3, 5, 8, 12, 20]
        func ok(_ k: Int) -> Double { pow(1 - e, Double(k)) }
        func use(_ k: Int) -> Double { Double(k) * ok(k) }
        let kstar = -1 / log(1 - e)
        let kr = Int(Rb.round(kstar))
        return (0..<4).map { stp in
            switch stp {
            case 0:
                return fr([S(st("learned model", "wrong ε of the time per step"), st("question", "how many steps to trust it?"))],
                          [F("per-step model error ε =", pct(e), "#e5337a")],
                          ("MBPO asks how far to trust the model.", "Every imagined step can be wrong, and errors compound."), lg)
            case 1:
                return fr([L("P(an imagined rollout is still correct)"), B(ks.map { k in Rb.row("\(k) steps", pct(ok(k)), ok(k), "#e5337a") })],
                          [F("(1 − \(js(e)))^k")],
                          ("After 20 steps only \(pct(ok(20))) of rollouts are still right.", "Long imagined rollouts are mostly fiction."), lg)
            case 2:
                let mx = ks.map(use).max()!
                return fr([L("useful imagined steps per rollout = k·(1−ε)^k"), B(ks.map { k in Rb.row("\(k) steps", f(use(k), 2), use(k) / mx, abs(Double(k) - kstar) < max(1, kstar * 0.4) ? "#22a06b" : "#3b82f6") })],
                          [F("best k ≈ −1 / ln(1−ε) =", f(kstar, 1), "#22a06b")],
                          ("Sweet spot: about \(kr) steps.", "Short enough to stay accurate, long enough to add data."), lg)
            default:
                return fr([S(st("full rollouts from start", "long, compounding error"), st("MBPO branches", "short k-step rollouts from many real states"), st("k used here", "\(kr)", "#22a06b"))],
                          [F("many short branches > few long ones")],
                          ("Branch short rollouts from real states.", "MBPO keeps model data near real data, then trains SAC on the mix."), lg)
            }
        }
    }
}
