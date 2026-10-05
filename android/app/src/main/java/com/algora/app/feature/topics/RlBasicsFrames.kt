package com.algora.app.feature.topics

import com.algora.app.feature.topics.Rb.B
import com.algora.app.feature.topics.Rb.C
import com.algora.app.feature.topics.Rb.F
import com.algora.app.feature.topics.Rb.L
import com.algora.app.feature.topics.Rb.S
import com.algora.app.feature.topics.Rb.brow
import com.algora.app.feature.topics.Rb.f
import com.algora.app.feature.topics.Rb.frame
import com.algora.app.feature.topics.Rb.js
import com.algora.app.feature.topics.Rb.pct
import com.algora.app.feature.topics.Rb.row
import com.algora.app.feature.topics.Rb.st
import com.algora.app.feature.topics.Rb.tok
import com.algora.app.feature.topics.RbGw.AR
import com.algora.app.feature.topics.RbGw.START
import com.algora.app.feature.topics.RbGw.key
import com.algora.app.feature.topics.RbGw.mv
import com.algora.app.feature.topics.RbGw.states
import com.algora.app.feature.topics.RbGw.term
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

// ── RL foundations and tabular-method storyboard frames ──────────────────────
// One 4×4 grid world shared across every screen: rollouts, returns, policy evaluation, Q-learning,
// value iteration, a bandit and a Bayes filter; then Bellman checks, DP sweeps, policy and value
// iteration on the known model, and Monte Carlo and TD learning on a slippery version scored against
// the exact answer. Drawn by RlBoardLabs.kt; the iOS port (RlBasicsFrames.swift) matches it.

internal val rlBasicsTopicIds = setOf(
    "agent_environment", "state_action_reward", "policy", "value_function", "q_function", "discount_factor",
    "exploration_exploitation", "pomdp", "bellman_equation", "dynamic_programming", "policy_iteration",
    "value_iteration", "monte_carlo_rl", "td_learning",
)

internal fun rlBasicsLab(topicId: String): RbLab? = when (topicId) {
    "agent_environment" -> agentEnvLab()
    "state_action_reward" -> sarLab()
    "policy" -> policyLab()
    "value_function" -> valueFnLab()
    "q_function" -> qFnLab()
    "discount_factor" -> discountLab()
    "exploration_exploitation" -> exploreExploitLab()
    "pomdp" -> pomdpLab()
    "bellman_equation" -> bellmanLab()
    "dynamic_programming" -> dpLab()
    "policy_iteration" -> policyIterLab()
    "value_iteration" -> valueIterLab()
    "monte_carlo_rl" -> monteCarloLab()
    "td_learning" -> tdLab()
    else -> null
}

private const val RING_START = "inset 0 0 0 1.5px #f5c542"

// ── 57a Agent & Environment ──

private fun agentEnvLab(): RbLab {
    val lg = listOf("#f5c542" to "Agent", "#22a06b" to "Goal +1 / visited", "#e5484d" to "Pit −1", "#3a3f4c" to "Wall")
    return RbLab(listOf("up first", "right first"), 0) { opt ->
        val paths = listOf(listOf(0, 0, 0, 3, 3, 3), listOf(3, 3, 3, 0, 0))
        val p = paths[opt]
        class T(val s: Pair<Int, Int>, val a: Int, val r: Double, val ns: Pair<Int, Int>)
        val traj = ArrayList<T>()
        var cur = START
        p.forEach { a ->
            val m = mv(cur, a, -0.04)
            traj += T(cur, a, m.r, m.ns)
            cur = m.ns
        }
        val n = p.size + 2
        val g = traj.sumOf { it.r }
        List(n) { st ->
            val t = if (st >= 1 && st <= p.size) st - 1 else null
            val at = if (st == 0) START else if (t != null) traj[t].ns else cur
            val seen = traj.take(if (t == null) (if (st == 0) 0 else p.size) else t + 1).map { key(it.s) }.toSet()
            val grid = RbGw.grid { _, k ->
                when {
                    k == key(at) -> RbCell("agent", "#f5c542", "#1b1d24")
                    k in seen -> RbCell("·", "rgba(34,160,107,.25)", "#5fd49b")
                    else -> RbGw.plainCell()
                }
            }
            val blocks = mutableListOf<RbBlock>(L("4×4 grid world · start bottom-left"), grid)
            val fx: List<RbFx>
            val cap: Pair<String, String>
            if (st == 0) {
                fx = listOf(F("agent → action  ·  environment → (s′, r)"))
                cap = "Agent and environment talk through two channels." to "The agent sends one action. The environment answers with the next state and a reward. Nothing else passes between them."
            } else if (t != null) {
                val x = traj[t]
                blocks += S(st("agent sends", AR[x.a], "#f5c542"), st("env returns", "s′ = (${RbGw.show(x.ns)}), r = ${if (x.r > 0) "+" else ""}${js(x.r)}"))
                fx = listOf(F("step ${t + 1}: (${RbGw.show(x.s)}) ${AR[x.a]} → (${RbGw.show(x.ns)})", "r = ${f(x.r)}", if (x.r > 0) "#22a06b" else if (x.r < -.5) "#e5484d" else "#c3c7d1"))
                cap = if (term(x.ns)) {
                    (if (x.r > 0) "The goal: episode over, +1." else "The pit: episode over, −1.") to "A terminal state ends the episode; nothing more can be earned."
                } else {
                    "Step ${t + 1}: the agent moves ${AR[x.a]}." to "Every non-terminal step costs 0.04 — the environment’s way of saying “hurry up”."
                }
            } else {
                blocks += S(st("steps", "${p.size}"), st("return G = Σ r", f(g), if (g > 0) "#22a06b" else "#e5484d"))
                fx = listOf(F("G = ${traj.joinToString(" + ") { f(it.r) }} =", f(g), if (g > 0) "#22a06b" else "#e5484d"))
                cap = "Return ${f(g)}." to if (opt == 0) "The long way round reaches the goal. Learning means finding actions that make this number large." else "Shorter, but it ends in the pit. Fewer steps is not the objective — total reward is."
            }
            frame(blocks, fx, cap, lg)
        }
    }
}

// ── 57b State, Action, Reward ──

private fun sarLab(): RbLab {
    val lg = listOf("#22a06b" to "Goal +1", "#e5484d" to "Pit −1", "#f5c542" to "Step cost")
    return RbLab(listOf("cost 0", "cost −0.04", "cost −2.5"), 2) { opt ->
        val c = listOf(0.0, -0.04, -2.5)[opt]
        class T(val s: Pair<Int, Int>, val a: Int, val r: Double, val ns: Pair<Int, Int>)
        fun run(p: List<Int>): List<T> {
            var cur = START
            return p.map { a -> val m = mv(cur, a, c); T(cur, a, m.r, m.ns).also { cur = m.ns } }
        }
        val safe = run(listOf(0, 0, 0, 3, 3, 3))
        val pit = run(listOf(0, 3, 3, 0, 3))
        fun g(tr: List<T>) = tr.sumOf { it.r }
        val rg = RbGw.grid { _, _ -> RbGw.plainCell(f(c)) }
        fun tup(tr: List<T>) = C(tr.map { x -> tok("(${RbGw.show(x.s)}) ${AR[x.a]}", if (x.r > 0) "done" else if (x.r < -.9 && term(x.ns)) "err" else "plain", "r " + f(x.r)) })
        List(4) { st ->
            when (st) {
                0 -> frame(listOf(L("reward for entering each cell"), rg), listOf(F("r = +1 goal · −1 pit · step cost", f(c), "#f5c542")),
                    "Each step yields one tuple (s, a, r, s′)." to (if (c == 0.0) "With no step cost, the agent is indifferent to how long it takes." else if (c < -1) "A brutal step cost: every move hurts more than the pit." else "A small step cost turns “reach the goal” into “reach it soon”."), lg)
                1 -> frame(listOf(L("route to the goal, 6 tuples"), tup(safe)), listOf(F("G =", f(g(safe)), "#22a06b")),
                    "Goal route: return ${f(g(safe))}." to "Five step costs, then +1.", lg)
                2 -> frame(listOf(L("route into the pit, 5 tuples"), tup(pit)), listOf(F("G =", f(g(pit)), "#e5484d")),
                    "Pit route: return ${f(g(pit))}." to "Four step costs, then −1.", lg)
                else -> {
                    val best = g(safe) >= g(pit)
                    val sc = max(3.0, abs(g(pit)))
                    frame(listOf(B(listOf(brow("goal route", g(safe), sc), brow("pit route", g(pit), sc)))),
                        listOf(F("better route:", if (best) "goal" else "pit", if (best) "#22a06b" else "#e5484d")),
                        if (best) "The goal wins." to "The reward design, not the map, decides what the agent should want."
                        else "The pit wins — reward design gone wrong." to "With step cost −2.5, ending fast beats ending well. Agents optimise exactly what you reward.", lg)
                }
            }
        }
    }
}

// ── 57c The Policy ──

private fun policyLab(): RbLab {
    val lg = listOf("#3b82f6" to "Positive value", "#e5484d" to "Negative value", "#f5c542" to "Path / start")
    val names = listOf("right, then up", "up, then right", "random")
    return RbLab(listOf("right→up", "up→right", "random"), 0) { opt ->
        val nm = names[opt]
        val pi = RbGw.pol(nm)
        val v = RbGw.evalPi(pi, .9, -0.04, 200)
        fun arrow(p: Pair<Int, Int>) = if (nm == "random") "✣" else AR[pi.of(p)[0].first]
        val path = ArrayList<String>()
        if (nm != "random") {
            var cur = START
            var i = 0
            while (i < 10 && !term(cur)) {
                path += key(cur)
                cur = mv(cur, pi.of(cur)[0].first, -0.04).ns
                i++
            }
        }
        val sk = key(START)
        List(4) { st ->
            when (st) {
                0 -> frame(listOf(L("policy “$nm”"), RbGw.grid { p, k -> RbGw.plainCell(arrow(p), if (k == sk) RING_START else "none") }),
                    listOf(F("π(s) → action, for every state")),
                    "A policy is a rule: state in, action out." to (if (nm == "random") "A stochastic policy: each of the four actions with probability 0.25." else "Deterministic: “$nm”."), lg)
                1 -> frame(listOf(L("Vπ(s), γ = 0.9"), RbGw.grid { _, k -> RbCell(f(v.getValue(k)), RbGw.vcol(v.getValue(k)), "#fff") }),
                    listOf(F("Vπ(start) =", f(v.getValue(sk)), "#3b82f6")),
                    "Every policy has a value at every state." to "Vπ(s): expected discounted return from s if you follow π.", lg)
                2 -> frame(listOf(L("following π from the start"), RbGw.grid { p, k -> if (k in path) RbCell(arrow(p), "rgba(245,197,66,.3)", "#f5c542") else RbGw.plainCell(arrow(p)) }),
                    listOf(F("path length", if (nm == "random") "varies" else "${path.size}")),
                    when (nm) {
                        "right, then up" -> "Confidently wrong: straight into the pit." to "Along the bottom row, up the right column, −1."
                        "up, then right" -> "Up the left side, across the top, +1." to "The same rule format, the opposite outcome."
                        else -> "No fixed path: a random walk." to "It wanders until it hits a terminal; the step cost piles up on the way."
                    }, lg)
                else -> frame(listOf(L("Vπ(start) for three policies"), B(names.mapIndexed { j, n ->
                    val x = RbGw.evalPi(RbGw.pol(n), .9, -0.04, 200).getValue(sk)
                    brow(n, x, 1.0, f(x), if (j == opt) "#f2f3f7" else "#9aa0ae").also { if (j != opt) it.bar = "#3a3f4c" }
                })), listOf(F("best of the three:", "up, then right", "#22a06b")),
                    "Value lets you rank policies." to "Improving a policy means finding one with higher value — the whole game of RL.", lg)
            }
        }
    }
}

// ── 57d Value Function ──

private fun valueFnLab(): RbLab {
    val lg = listOf("#3b82f6" to "Positive", "#e5484d" to "Negative", "#f5c542" to "Start (backup shown)")
    return RbLab(listOf("up→right", "right→up"), 0) { opt ->
        val nm = listOf("up, then right", "right, then up")[opt]
        val pi = RbGw.pol(nm)
        List(7) { step ->
            val sw = listOf(0, 1, 2, 3, 5, 10, 50)[step]
            val v = RbGw.evalPi(pi, .9, -0.04, sw)
            val vp = RbGw.evalPi(pi, .9, -0.04, max(sw - 1, 0))
            val sk = key(START)
            val nx = mv(START, pi.of(START)[0].first, -0.04)
            val grid = RbGw.grid { p, k ->
                val x = v[k] ?: 0.0
                RbCell(f(x) + " " + AR[pi.of(p)[0].first], if (sw > 0) RbGw.vcol(x) else "#1f232d", "#fff", if (k == sk) RING_START else "none")
            }
            val delta = states.maxOf { abs((v[key(it)] ?: 0.0) - (vp[key(it)] ?: 0.0)) }
            val fx = if (sw > 0) listOf(F("V(start) = −0.04 + 0.9 × V(${RbGw.show(nx.ns)}) =", f(v[sk] ?: 0.0, 3), "#3b82f6"), F("largest change this sweep =", f(delta, 3)))
            else listOf(F("V₀ = 0 everywhere"))
            val cap = when {
                sw == 0 -> "Iterative policy evaluation starts from zero." to "Policy fixed: “$nm”. Each sweep backs up every state one step."
                sw < 4 -> "Sweep $sw: value has travelled $sw cell${if (sw > 1) "s" else ""} from the terminals." to "No max anywhere — values flow backwards along this policy’s own path."
                sw < 50 -> "Sweep $sw: changes shrinking (${f(delta, 3)})." to "Each sweep is a contraction by γ = 0.9."
                else -> "Converged." to "V(start) = ${f(v.getValue(sk), 3)}. This is Vπ — the value of this policy, good or bad."
            }
            frame(listOf(L("Vπ after $sw sweep${if (sw == 1) "" else "s"}"), grid), fx, cap, lg)
        }
    }
}

// ── 57e Q-Function, learned by Q-learning ──

private fun qFnLab(): RbLab {
    val lg = listOf("#3b82f6" to "Positive Q", "#e5484d" to "Negative Q", "#f5c542" to "Start cell")
    return RbLab(listOf("α 0.1", "α 0.5", "α 0.9"), 1) { opt ->
        val al = listOf(.1, .5, .9)[opt]
        val rnd = Rb.rng(3)
        val q = HashMap<String, DoubleArray>()
        fun qa(k: String) = q.getOrPut(k) { DoubleArray(4) }
        val snaps = HashMap<Int, Map<String, List<Double>>>()
        snaps[0] = emptyMap()
        for (e in 1..300) {
            var cur = START
            var t = 0
            while (t < 60 && !term(cur)) {
                val qs = qa(key(cur))
                val a = if (rnd() < .2) floor(rnd() * 4).toInt() else Rb.argmax(qs)
                val m = mv(cur, a, -0.04)
                val tgt = m.r + if (term(m.ns)) 0.0 else .9 * qa(key(m.ns)).max()
                qs[a] += al * (tgt - qs[a])
                cur = m.ns
                t++
            }
            if (e in listOf(1, 10, 50, 300)) snaps[e] = q.mapValues { it.value.toList() }
        }
        val sk = key(START)
        List(5) { step ->
            val ep = listOf(0, 1, 10, 50, 300)[step]
            val qq = snaps.getValue(ep)
            val qs = qq[sk] ?: listOf(0.0, 0.0, 0.0, 0.0)
            val grid = RbGw.grid { _, k ->
                val v = qq[k] ?: listOf(0.0, 0.0, 0.0, 0.0)
                val m = v.max()
                val a = v.indexOf(m)
                RbCell(if (ep > 0) f(m) + " " + AR[a] else "0", if (ep > 0) RbGw.vcol(m) else "#1f232d", "#fff", if (k == sk) RING_START else "none")
            }
            val blocks = listOf(L("max Q(s,a) and greedy action · episode $ep"), grid, B(qs.mapIndexed { i, v -> brow("Q(start," + AR[i] + ")", v, 1.0) }))
            val fx = if (ep > 0) listOf(F("Q ← Q + α(r + 0.9·max Q′ − Q), α =", js(al), "#8f84ff")) else listOf(F("Q = 0 for all 13 × 4 pairs"))
            val best = qs.indexOf(qs.max())
            val cap = when {
                ep == 0 -> "Q(s,a): the value of taking a, then acting well." to "Learned from experience by Q-learning with ε = 0.2 exploration — no model of the grid."
                ep < 50 -> "Episode $ep: values creep back from the terminals." to "Each update uses one transition; information travels one step per visit."
                else -> "Episode $ep: greedy action at start is ${AR[best]}." to "The policy is read straight off the table — argmax over four numbers, no lookahead."
            }
            frame(blocks, fx, cap, lg)
        }
    }
}

// ── 57f Horizon & Discount ──

private fun discountLab(): RbLab {
    val lg = listOf("#6d5dfc" to "Discount weight", "#3b82f6" to "Value", "#f5c542" to "Start")
    return RbLab(listOf("γ 0.5", "γ 0.9", "γ 0.99"), 0) { opt ->
        val g = listOf(.5, .9, .99)[opt]
        val vi = RbGw.vi(g, -0.04)
        val sk = key(START)
        List(4) { st ->
            when (st) {
                0 -> frame(listOf(L("weight on a reward k steps ahead: γᵏ"), B(listOf(1, 2, 4, 8, 16).map { k -> row("k = $k", f(g.pow(k), 3), g.pow(k), "#6d5dfc") })),
                    listOf(F("effective horizon ≈ 1 / (1 − γ) =", f(1 / (1 - g), 0) + " steps", "#8f84ff")),
                    "γ = ${js(g)}: a reward 8 steps away counts ${f(g.pow(8), 2)}." to "Discounting sets how far ahead the agent effectively plans.", lg)
                1 -> frame(listOf(L("V*(s), γ = ${js(g)}"), RbGw.grid { _, k -> RbCell(f(vi.V.getValue(k)), RbGw.vcol(vi.V.getValue(k)), "#fff", if (k == sk) RING_START else "none") }),
                    listOf(F("V*(start) =", f(vi.V.getValue(sk), 3), "#3b82f6")),
                    (if (g < .6) "Value barely leaks out from the goal." else "Value spreads across the whole grid.") to (if (g < .6) "Two steps out, the +1 is worth less than the step cost." else "Distant states still feel the goal."), lg)
                2 -> frame(listOf(L("optimal policy"), RbGw.grid { _, k -> RbGw.plainCell(AR[vi.P.getValue(k)], if (k == sk) RING_START else "none") }),
                    listOf(F("π*(start) =", AR[vi.P.getValue(sk)], "#f5c542")),
                    "The discount changes the plan, not just the numbers." to (if (g < .6) "Far from the goal, the myopic agent sees nothing worth walking for." else "Every state points along a route to +1."), lg)
                else -> frame(listOf(L("V*(start) by γ"), B(listOf(.5, .9, .99).map { x ->
                    val v = RbGw.vi(x, -0.04).V.getValue(sk)
                    brow("γ " + js(x), v, 1.0, f(v, 3), if (x == g) "#f2f3f7" else "#9aa0ae").also { if (x != g) it.bar = "#3a3f4c" }
                })), listOf(F("value iteration, 300 sweeps each")),
                    "Higher γ, longer horizon, higher value at the start." to "γ near 1 is patient but learns slowly; small γ is quick but short-sighted.", lg)
            }
        }
    }
}

// ── 57g Exploration vs Exploitation ──

private class BanditSnap(val n: IntArray, val q: DoubleArray, val tot: Int)

private val banditM = doubleArrayOf(.2, .5, .6, .8)

private fun banditRun(e: Double, seed0: Long, tt: Int): Map<Int, BanditSnap> {
    val rnd = Rb.rng(seed0)
    val n = IntArray(4)
    val q = DoubleArray(4)
    var tot = 0
    val snaps = HashMap<Int, BanditSnap>()
    for (t in 1..tt) {
        val a = if (rnd() < e) floor(rnd() * 4).toInt() else Rb.argmax(q)
        val r = if (rnd() < banditM[a]) 1 else 0
        n[a]++
        q[a] += (r - q[a]) / n[a]
        tot += r
        if (t == 10 || t == 50 || t == 200) snaps[t] = BanditSnap(n.copyOf(), q.copyOf(), tot)
    }
    return snaps
}

private val banditAvg: List<Double> by lazy {
    listOf(0.0, .1, .3).map { e ->
        var a = 0.0
        for (k in 0 until 200) a += banditRun(e, 101L + k * 7, 200).getValue(200).tot
        a / 200 / 200
    }
}

private fun exploreExploitLab(): RbLab {
    val lg = listOf("#22a06b" to "Truly best", "#3b82f6" to "Estimate", "#6d5dfc" to "Chosen ε")
    val arms = listOf("A", "B", "C", "D")
    return RbLab(listOf("ε 0", "ε 0.1", "ε 0.3"), 0) { opt ->
        val eps = listOf(0.0, .1, .3)[opt]
        val r = banditRun(eps, 17, 200)
        val avg = banditAvg
        List(5) { step ->
            val tt = listOf(0, 10, 50, 200, 200)[step]
            val sn = if (tt > 0) r.getValue(tt) else BanditSnap(IntArray(4), DoubleArray(4), 0)
            if (step < 4) {
                val blocks = listOf(
                    L(if (tt > 0) "after $tt pulls: estimate (pulls)" else "4 slot machines, true win rates hidden"),
                    B(arms.mapIndexed { i, a ->
                        row("$a · true ${js(banditM[i])}", if (tt > 0) "${f(sn.q[i])} (${sn.n[i]})" else "?", sn.q[i], if (i == 3) "#22a06b" else "#3b82f6", if (i == 3) "#f2f3f7" else "#c3c7d1")
                    }),
                )
                val fx = if (tt > 0) listOf(F("reward so far =", "${sn.tot} / $tt"), F("pulls on best arm D =", pct(sn.n[3].toDouble() / tt), "#22a06b"))
                else listOf(F("ε-greedy: explore with probability ${js(eps)}"))
                val cap = when {
                    tt == 0 -> "Exploit what you know, or explore what you don’t?" to "Estimates start at 0 and update with each pull’s win or loss."
                    eps == 0.0 -> "Pure greed: ${pct(sn.n[0].toDouble() / tt)} of pulls on A." to "With ε = 0 it never tries another arm, so it never learns D is better."
                    else -> "${pct(sn.n[3].toDouble() / tt)} of pulls on the best arm." to "Random exploration ${pct(eps)} of the time keeps every estimate improving."
                }
                frame(blocks, fx, cap, lg)
            } else {
                frame(listOf(L("average win rate over 200 runs × 200 pulls"), B(listOf(0.0, .1, .3).mapIndexed { j, e ->
                    row("ε " + js(e), f(avg[j], 3), avg[j] / .8, if (e == eps) "#6d5dfc" else "#3a3f4c", if (e == eps) "#f2f3f7" else "#9aa0ae")
                } + row("always D", f(.8, 2), 1.0, "#22a06b"))), listOf(F("best possible = 0.80 (arm D)")),
                    "A little exploration pays; a lot costs." to "ε = 0 gets stuck, ε = 0.3 wastes pulls on known-bad arms. The trade-off never goes away.", lg)
            }
        }
    }
}

// ── 57h POMDP: a Bayes filter over the grid ──

private fun pomdpLab(): RbLab {
    val lg = listOf("#3b82f6" to "Belief", "#f5c542" to "True position", "#3a3f4c" to "Wall")
    return RbLab(listOf("exact sensor", "10% noise"), 0) { opt ->
        val noise = listOf(0.0, .1)[opt]
        val cand = states
        fun walls(p: Pair<Int, Int>) = (0..3).count { a -> !RbGw.free(p.first + RbGw.AC[a][0] to p.second + RbGw.AC[a][1]) }
        fun parse(k: String) = k.split(",").let { it[0].toInt() to it[1].toInt() }
        val truth = listOf(3 to 0, 2 to 0, 1 to 0)
        // Each step is an observation (blocked-side count) or a move up (-1).
        val seq = listOf(walls(truth[0]), -1, walls(truth[1]), -1, walls(truth[2]))
        var b = LinkedHashMap<String, Double>()
        cand.forEach { b[key(it)] = 1.0 / cand.size }
        class H(val b: Map<String, Double>, val o: Int?)
        val hist = arrayListOf(H(LinkedHashMap(b), null))
        seq.forEach { x ->
            if (x >= 0) {
                val nb = LinkedHashMap<String, Double>()
                var z = 0.0
                for ((k, v) in b) {
                    val l = if (walls(parse(k)) == x) 1 - noise else noise / 3
                    nb[k] = v * l
                    z += nb.getValue(k)
                }
                for (k in nb.keys) nb[k] = nb.getValue(k) / (if (z == 0.0) 1.0 else z)
                b = nb
            } else {
                val nb = LinkedHashMap<String, Double>()
                for ((k, v) in b) {
                    val n = mv(parse(k), 0, 0.0).ns
                    if (term(n)) continue
                    nb[key(n)] = (nb[key(n)] ?: 0.0) + v
                }
                val z = nb.values.sum()
                for (k in nb.keys) nb[k] = nb.getValue(k) / z
                b = nb
            }
            hist += H(LinkedHashMap(b), x)
        }
        List(6) { st ->
            val h = hist[min(st, 5)]
            val tk = key(truth[min(st / 2, 2)])
            val top = h.b.entries.sortedByDescending { it.value }[0]
            val grid = RbGw.grid { _, k ->
                val v = h.b[k] ?: 0.0
                RbCell(if (v > 0.005) f(v) else "", if (v > 0.005) "rgba(59,130,246,${js(.15 + .8 * v)})" else "#1f232d", "#fff", if (k == tk) RING_START else "none")
            }
            val x = h.o
            val fx: List<RbFx>
            var cap: Pair<String, String>
            when {
                x == null -> {
                    fx = listOf(F("b(s) = 1 /", "${cand.size} each"))
                    cap = "The agent knows the map, not where it is." to "A POMDP replaces the state with a belief: a probability over every cell it might be in."
                }
                x >= 0 -> {
                    fx = listOf(F("sensor: blocked sides =", "$x", "#f5c542"), F("b′(s) ∝ P(o | s) · b(s)"))
                    cap = "It senses $x blocked side${if (x == 1) "" else "s"}." to (if (noise > 0) "A 10%-noisy sensor: mismatching cells are down-weighted, not ruled out." else "Cells with a different count drop to zero.")
                }
                else -> {
                    fx = listOf(F("action ↑: shift every candidate up"))
                    cap = "It moves up — so does the whole belief." to "Each candidate moves as the agent would have; walls stop some of them."
                }
            }
            if (st >= 5) cap = (if (top.value > .95) "Localised: (${top.key}) with ${pct(top.value)}." else "Most likely (${top.key}), ${pct(top.value)}.") to
                "Three observations and two moves — acting and sensing together narrow the belief."
            frame(listOf(L("belief b(s) · yellow ring = true position"), grid), fx, cap, lg)
        }
    }
}

// ── 58a Bellman Equation ──

private fun bellmanLab(): RbLab {
    val lg = listOf("#f5c542" to "Checked state", "#3b82f6" to "Value", "#6d5dfc" to "Best action")
    return RbLab(listOf("(0,2)", "(2,2)", "start"), 0) { opt ->
        val c = listOf(0 to 2, 2 to 2, 3 to 0)[opt]
        val ck = key(c)
        val v = RbGw.vi(.9, -0.04).V
        val p = RbGw.greedy(v)
        val bk = RbGw.backups(v, c)
        val best = bk.reduce { a, b -> if (b.q > a.q) b else a }
        val cs = RbGw.show(c)
        List(4) { st ->
            var blocks = listOf<RbBlock>(L("V*(s), γ 0.9 · checking ($cs)"), RbGw.vgrid(v, if (st >= 2) p else null, ck))
            val fx: List<RbFx>
            val cap: Pair<String, String>
            when (st) {
                0 -> {
                    fx = listOf(F("V*($cs) =", f(v.getValue(ck), 3), "#f5c542"))
                    cap = "The Bellman equation is a consistency check." to "At the optimum, every state’s value equals the best one-step reward plus the discounted value of where it lands."
                }
                1 -> {
                    blocks = listOf(L("r + 0.9·V*(s′) for each action at ($cs)"), B(bk.map { x -> brow(AR[x.a] + " → (" + RbGw.show(x.ns) + ")", x.q, 1.0, f(x.q, 3)) }))
                    fx = listOf(F("${AR[best.a]}: ${f(best.r)} + 0.9 × ${f(v[key(best.ns)] ?: 0.0, 3)} =", f(best.q, 3), "#8f84ff"))
                    cap = "Back up all four actions." to "Bumping a wall leaves you in place, so it scores the step cost plus your own value."
                }
                2 -> {
                    fx = listOf(F("max_a Q = ${f(best.q, 3)}  ·  V*($cs) =", f(v.getValue(ck), 3), "#22a06b"))
                    cap = "The max equals the stored value." to "Consistent: V*($cs) = ${f(v.getValue(ck), 3)}. The arrow is simply the action that achieved the max."
                }
                else -> {
                    val second = bk.sortedByDescending { it.q }[1]
                    fx = listOf(F("gap to next-best (${AR[second.a]}) =", f(best.q - second.q, 3), "#f5c542"))
                    cap = "Every other action falls short." to "If any state failed this check, V would not be optimal — that is what DP algorithms iterate on."
                }
            }
            frame(blocks, fx, cap, lg)
        }
    }
}

// ── 58b Dynamic Programming: synchronous vs in-place sweeps ──

private class DpRun(val v: Map<String, Double>, val d: Double)

private fun dpRun(ip: Boolean, kk: Int): DpRun {
    var v = HashMap<String, Double>()
    var d = 0.0
    repeat(kk) {
        val n = if (ip) v else HashMap(v)
        d = 0.0
        states.forEach { p ->
            val k = key(p)
            val nv = RbGw.backups(if (ip) n else v, p).maxOf { it.q }
            d = max(d, abs(nv - (v[k] ?: 0.0)))
            n[k] = nv
        }
        v = n
    }
    return DpRun(v, d)
}

private fun dpConv(ip: Boolean): Int {
    for (k in 1 until 200) if (dpRun(ip, k).d < 1e-4) return k
    return 200
}

private fun dpLab(): RbLab {
    val lg = listOf("#3b82f6" to "Positive value", "#e5484d" to "Negative", "#6d5dfc" to "In-place")
    return RbLab(listOf("synchronous", "in-place"), 0) { opt ->
        val inplace = opt == 1
        List(8) { st ->
            if (st < 7) {
                val kk = listOf(0, 1, 2, 3, 4, 6, 10)[st]
                val r = dpRun(inplace, kk)
                frame(listOf(L("V after $kk sweep${if (kk == 1) "" else "s"} (${if (inplace) "in-place" else "synchronous"})"), RbGw.vgrid(r.v, null)),
                    if (kk > 0) listOf(F("largest change this sweep =", f(r.d, 4), "#3b82f6")) else listOf(F("V₀ = 0 · model P and R known")),
                    if (kk == 0) "Dynamic programming needs the model." to "With P and R known, each sweep backs up every state from its neighbours — no experience needed."
                    else "Sweep $kk: value has reached $kk step${if (kk > 1) "s" else ""} out." to (if (inplace) "In-place sweeps reuse values updated earlier in the same sweep, so information can travel further per pass." else "Synchronous sweeps read only last sweep’s values."), lg)
            } else {
                val a = dpConv(false)
                val b = dpConv(true)
                val m = max(a, b).toDouble()
                frame(listOf(L("sweeps to converge (Δ < 0.0001)"), B(listOf(row("synchronous", "$a", a / m, "#3a3f4c"), row("in-place", "$b", b / m, "#6d5dfc", "#f2f3f7")))),
                    listOf(F("saved:", "${a - b} sweeps", "#8f84ff")),
                    "Same answer, fewer passes in place." to "Sweep order matters for speed, never for the fixed point.", lg)
            }
        }
    }
}

// ── 58c Policy Iteration ──

private fun policyIterLab(): RbLab {
    val lg = listOf("#3b82f6" to "Value", "#e5484d" to "Negative", "#f5c542" to "Start")
    return RbLab(listOf("right, else up", "always left"), 0) { opt ->
        val init = LinkedHashMap<String, Int>()
        states.forEach { p -> init[key(p)] = if (opt == 0) (if (RbGw.free(p.first to p.second + 1)) 3 else 0) else 2 }
        class Ph(val t: String, val r: Int, val p: Map<String, Int>, val v: Map<String, Double>, val ch: Int = 0)
        val phases = arrayListOf(Ph("init", 0, init, emptyMap()))
        var pp: Map<String, Int> = LinkedHashMap(init)
        for (r in 1 until 8) {
            val cur = pp
            val v = RbGw.evalPi({ s -> listOf(cur.getValue(key(s)) to 1.0) }, .9, -0.04, 300)
            phases += Ph("eval", r, LinkedHashMap(cur), v)
            val np = RbGw.greedy(v)
            val ch = np.keys.count { np[it] != cur[it] }
            phases += Ph("imp", r, np, v, ch)
            pp = np
            if (ch == 0) break
        }
        val sk = key(START)
        phases.map { ph ->
            val fx: List<RbFx>
            val cap: Pair<String, String>
            when (ph.t) {
                "init" -> {
                    fx = listOf(F("π₀ =", if (opt == 0) "right, else up" else "always left"))
                    cap = "Start from any policy at all." to (if (opt == 0) "“Right if you can, else up” walks into the pit." else "“Always left” never reaches anything.")
                }
                "eval" -> {
                    fx = listOf(F("Vπ${ph.r - 1}(start) =", f(ph.v[sk] ?: 0.0, 3), "#3b82f6"))
                    cap = "Round ${ph.r}: evaluate the current policy." to "Solve for its values exactly — here, 300 sweeps of the policy’s own backup."
                }
                else -> {
                    fx = listOf(F("states whose action changed =", "${ph.ch}", if (ph.ch > 0) "#f5c542" else "#22a06b"))
                    cap = if (ph.ch > 0) "Round ${ph.r}: improve — act greedily on those values." to "${ph.ch} states switch action. Each improvement is guaranteed not to lower any value."
                    else "Stable after ${ph.r} rounds: optimal." to "Greedy on its own values changes nothing, so this policy satisfies the Bellman optimality equation."
                }
            }
            val label = when (ph.t) {
                "init" -> "initial policy"
                "eval" -> "Vπ, round ${ph.r}"
                else -> "improved policy, round ${ph.r}"
            }
            frame(listOf(L(label), RbGw.vgrid(ph.v, ph.p, sk)), fx, cap, lg)
        }
    }
}

// ── 58d Value Iteration ──

private fun valueIterLab(): RbLab {
    val lg = listOf("#3b82f6" to "Value", "#e5484d" to "Negative", "#f5c542" to "Start")
    return RbLab(listOf("γ 0.5", "γ 0.9", "γ 0.99"), 1) { opt ->
        val g = listOf(.5, .9, .99)[opt]
        List(8) { st ->
            val kk = listOf(0, 1, 2, 3, 4, 6, 10, 30)[st]
            var v = HashMap<String, Double>()
            var d = 0.0
            repeat(kk) {
                val n = HashMap<String, Double>()
                d = 0.0
                states.forEach { p ->
                    val k = key(p)
                    val nv = (0..3).maxOf { a -> val m = mv(p, a, -0.04); m.r + if (term(m.ns)) 0.0 else g * (v[key(m.ns)] ?: 0.0) }
                    d = max(d, abs(nv - (v[k] ?: 0.0)))
                    n[k] = nv
                }
                v = n
            }
            val p = if (kk > 0) {
                val o = HashMap<String, Int>()
                states.forEach { s ->
                    var bi = 0
                    var bv = -1e9
                    for (a in 0..3) {
                        val m = mv(s, a, -0.04)
                        val q = m.r + if (term(m.ns)) 0.0 else g * (v[key(m.ns)] ?: 0.0)
                        if (q > bv + 1e-9) { bv = q; bi = a }
                    }
                    o[key(s)] = bi
                }
                o
            } else null
            val sk = key(START)
            val fx = if (kk > 0) listOf(F("V(s) ← max_a [r + γ·V(s′)]"), F("largest change =", f(d, 4), "#3b82f6")) else listOf(F("V₀ = 0, no policy stored"))
            val cap = when {
                kk == 0 -> "Value iteration: one backup per state, with a max." to "Policy iteration with the evaluation cut to a single sweep."
                kk < 6 -> "Sweep $kk: V(start) = ${f(v[sk] ?: 0.0, 3)}." to "The greedy arrows already point the right way near the goal, long before the values settle."
                else -> "Sweep $kk: change ${f(d, 4)}." to "Error shrinks by γ = ${js(g)} per sweep — ${if (g > .95) "slowly" else "quickly"}. Read the policy off V at the end."
            }
            frame(listOf(L("V after $kk sweep${if (kk == 1) "" else "s"}, greedy arrows"), RbGw.vgrid(v, p, sk)), fx, cap, lg)
        }
    }
}

// ── 58e Monte Carlo ──

private class RbStep(val s: String, val r: Double)

private fun rollSlip(pi: RbGw.Pi, slip: Double, rnd: () -> Double): List<RbStep> {
    var cur = START
    val ep = ArrayList<RbStep>()
    var t = 0
    while (t < 100 && !term(cur)) {
        val a0 = pi.of(cur)[0].first
        val u = rnd()
        val a = if (u < 1 - slip) a0 else if (u < 1 - slip / 2) RbGw.perp(a0)[0] else RbGw.perp(a0)[1]
        val m = mv(cur, a, -0.04)
        ep += RbStep(key(cur), m.r)
        cur = m.ns
        t++
    }
    return ep
}

private fun maxErr(v: Map<String, Double>, exact: Map<String, Double>) = states.maxOf { abs((v[key(it)] ?: 0.0) - exact.getValue(key(it))) }

private fun monteCarloLab(): RbLab {
    val lg = listOf("#3b82f6" to "Estimate", "#e5337a" to "Error", "#f5c542" to "Start")
    return RbLab(listOf("slip 0.1", "slip 0.2", "slip 0.3"), 1) { opt ->
        val slip = listOf(.1, .2, .3)[opt]
        val pi = RbGw.pol("up, then right")
        val exact = RbGw.evalSlip(pi, slip)
        val rnd = Rb.rng(9)
        val sum = HashMap<String, Double>()
        val n = HashMap<String, Int>()
        val snaps = HashMap<Int, Map<String, Double>>()
        snaps[0] = emptyMap()
        for (e in 1..500) {
            val ep = rollSlip(pi, slip, rnd)
            var g = 0.0
            val gs = DoubleArray(ep.size)
            for (t in ep.indices.reversed()) { g = ep[t].r + .9 * g; gs[t] = g }
            val seen = HashSet<String>()
            ep.forEachIndexed { t, x ->
                if (!seen.add(x.s)) return@forEachIndexed
                sum[x.s] = (sum[x.s] ?: 0.0) + gs[t]
                n[x.s] = (n[x.s] ?: 0) + 1
            }
            if (e in listOf(1, 5, 20, 100, 500)) snaps[e] = sum.mapValues { it.value / n.getValue(it.key) }
        }
        List(7) { st ->
            val ee = listOf(0, 1, 5, 20, 100, 500)[min(st, 5)]
            val sv = snaps.getValue(ee)
            val err = if (ee > 0) maxErr(sv, exact) else 0.0
            if (st < 6) {
                frame(listOf(L(if (ee > 0) "V̂ from $ee episode${if (ee > 1) "s" else ""} of averaged returns" else "exact Vπ (for scoring only)"), RbGw.vgrid(if (ee > 0) sv else exact, null, key(START))),
                    if (ee > 0) listOf(F("V̂(s) = mean of returns observed from s"), F("largest error vs exact =", f(err, 3), "#e5337a")) else listOf(F("policy “up, then right”, slip ${js(slip)}")),
                    when (ee) {
                        0 -> "Monte Carlo: learn from whole episodes." to "Moves slip sideways ${pct(slip)} of the time, so returns vary. The exact answer is shown first, to score against."
                        1 -> "One episode: one return per state visited." to "States never visited stay at 0. No bootstrapping — only actual outcomes."
                        else -> "$ee episodes: error ${f(err, 3)}." to "Averaging more returns shrinks the noise, roughly as 1/√n — but nothing is learned until an episode ends."
                    }, lg)
            } else {
                frame(listOf(L("largest error by episodes"), B(listOf(1, 5, 20, 100, 500).map { e ->
                    val er = maxErr(snaps.getValue(e), exact)
                    row("$e ep", f(er, 3), er / 1.2, "#e5337a")
                })), listOf(F("unbiased, high variance")),
                    "Monte Carlo is unbiased but noisy." to "It needs complete episodes and many of them. TD, next, trades some bias for much less variance.", lg)
            }
        }
    }
}

// ── 58f TD(0) ──

private fun tdLab(): RbLab {
    val lg = listOf("#3b82f6" to "Estimate", "#6d5dfc" to "Error", "#f5c542" to "Start")
    return RbLab(listOf("α 0.05", "α 0.1", "α 0.5"), 1) { opt ->
        val al = listOf(.05, .1, .5)[opt]
        val pi = RbGw.pol("up, then right")
        val slip = .2
        val exact = RbGw.evalSlip(pi, slip)
        val rnd = Rb.rng(9)
        val v = HashMap<String, Double>()
        class Sn(val v: Map<String, Double>, val u: Int)
        val snaps = hashMapOf(0 to Sn(emptyMap(), 0))
        var u = 0
        for (e in 1..500) {
            var cur = START
            var t = 0
            while (t < 100 && !term(cur)) {
                val a0 = pi.of(cur)[0].first
                val r0 = rnd()
                val a = if (r0 < 1 - slip) a0 else if (r0 < 1 - slip / 2) RbGw.perp(a0)[0] else RbGw.perp(a0)[1]
                val m = mv(cur, a, -0.04)
                val k = key(cur)
                val tg = m.r + if (term(m.ns)) 0.0 else .9 * (v[key(m.ns)] ?: 0.0)
                v[k] = (v[k] ?: 0.0) + al * (tg - (v[k] ?: 0.0))
                u++
                cur = m.ns
                t++
            }
            if (e in listOf(1, 5, 20, 100, 500)) snaps[e] = Sn(HashMap(v), u)
        }
        List(7) { st ->
            val ee = listOf(0, 1, 5, 20, 100, 500)[min(st, 5)]
            val sn = snaps.getValue(ee)
            if (st < 6) {
                frame(listOf(L(if (ee > 0) "V̂ after $ee episode${if (ee > 1) "s" else ""} (${sn.u} updates)" else "V̂ = 0"), RbGw.vgrid(sn.v, null, key(START))),
                    if (ee > 0) listOf(F("V(s) ← V(s) + ${js(al)}·[r + 0.9·V(s′) − V(s)]"), F("largest error vs exact =", f(maxErr(sn.v, exact), 3), "#e5337a")) else listOf(F("same policy and slip 0.2 as Monte Carlo")),
                    when (ee) {
                        0 -> "Temporal difference: update after every step." to "Use your own next estimate as the target — bootstrapping — instead of waiting for the return."
                        1 -> "After one episode, only the last step learned much." to "Value leaks back one state per visit, starting from the terminal it hit."
                        else -> "$ee episodes: error ${f(maxErr(sn.v, exact), 3)}." to (if (al > .3) "A large α learns fast but keeps jittering around the answer." else "Small steps: slower, smoother convergence.")
                    }, lg)
            } else {
                frame(listOf(L("largest error by episodes, α = " + js(al)), B(listOf(1, 5, 20, 100, 500).map { e ->
                    val x = maxErr(snaps.getValue(e).v, exact)
                    row("$e ep", f(x, 3), x / 1.2, "#6d5dfc")
                })), listOf(F("biased early, low variance")),
                    "TD learns online, from incomplete episodes." to "It is biased while V is wrong, but far less noisy than Monte Carlo — the basis of Q-learning and SARSA.", lg)
            }
        }
    }
}
