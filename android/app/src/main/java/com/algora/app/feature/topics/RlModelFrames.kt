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
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

// ── Model-based RL storyboard frames ─────────────────────────────────────────
// Dyna on the grid world, real UCT and PUCT search on a tic-tac-toe position checked against minimax,
// a world model learned from limited data, and the compounding-error maths behind MBPO. Drawn by
// RlBoardLabs.kt; the iOS port (RlModelFrames.swift) matches it.

internal val rlModelTopicIds = setOf("dyna_q", "mcts", "alphago", "alphazero", "muzero", "world_models", "dreamer", "mbpo")

internal fun rlModelLab(topicId: String): RbLab? = when (topicId) {
    "dyna_q" -> dynaLab()
    "mcts" -> mctsLab()
    "alphago" -> alphaGoLab()
    "alphazero" -> alphaZeroLab()
    "muzero" -> muZeroLab()
    "world_models" -> worldModelLab()
    "dreamer" -> dreamerLab()
    "mbpo" -> mbpoLab()
    else -> null
}

// ── Tic-tac-toe: exact minimax and a UCT / PUCT search ──

internal object RbTtt {
    private val TL = arrayOf(intArrayOf(0, 1, 2), intArrayOf(3, 4, 5), intArrayOf(6, 7, 8), intArrayOf(0, 3, 6), intArrayOf(1, 4, 7), intArrayOf(2, 5, 8), intArrayOf(0, 4, 8), intArrayOf(2, 4, 6))

    /** X to move, two moves each: X in a and b, O in c and the bottom-right corner. */
    val TB = listOf("X", "", "O", "", "X", "", "", "", "O")

    fun win(b: List<String>): String? {
        for (l in TL) if (b[l[0]].isNotEmpty() && b[l[0]] == b[l[1]] && b[l[0]] == b[l[2]]) return b[l[0]]
        return if (b.all { it.isNotEmpty() }) "D" else null
    }

    fun toMove(b: List<String>): String = if (b.count { it == "X" } == b.count { it == "O" }) "X" else "O"

    fun moves(b: List<String>): List<Int> = b.indices.filter { b[it].isEmpty() }

    fun play(b: List<String>, m: Int): List<String> = b.toMutableList().also { it[m] = toMove(b) }

    private val mmCache = HashMap<String, Double>()

    fun minimax(b: List<String>): Double {
        val k = b.joinToString("") { it.ifEmpty { "." } }
        synchronized(mmCache) { mmCache[k]?.let { return it } }
        val w = win(b)
        val v = if (w != null) score(w) else {
            val vs = moves(b).map { minimax(play(b, it)) }
            if (toMove(b) == "X") vs.max() else vs.min()
        }
        synchronized(mmCache) { mmCache[k] = v }
        return v
    }

    fun score(w: String) = if (w == "X") 1.0 else if (w == "O") -1.0 else 0.0

    class Best(val vals: List<Pair<Int, Double>>, val best: List<Int>)

    fun best(b: List<String>): Best {
        val v = moves(b).map { it to minimax(play(b, it)) }
        val bv = v.maxOf { it.second }
        return Best(v, v.filter { it.second == bv }.map { it.first })
    }

    class Node(val b: List<String>, val p: Double) {
        var n = 0
        var w = 0.0
        var kids: List<Pair<Int, Node>>? = null
    }

    class Opts(
        val rand: () -> Double,
        val at: List<Int> = emptyList(),
        val puct: Boolean = false,
        val prior: Map<Int, Double>? = null,
        val evalFn: ((List<String>) -> Double)? = null,
    )

    /** Root visit counts per move (ascending squares) at each `at` budget, and at the end under -1. */
    fun search(b: List<String>, sims: Int, o: Opts): Map<Int, Map<Int, Int>> {
        val r = o.rand
        val root = Node(b, 1.0)
        val snaps = HashMap<Int, Map<Int, Int>>()
        fun expand(n: Node) {
            val ms = moves(n.b)
            val pr = if (n === root) o.prior else null
            n.kids = ms.map { m -> m to Node(play(n.b, m), pr?.getValue(m) ?: (1.0 / ms.size)) }
        }
        fun rollout(bb: List<String>): Double {
            val c = bb.toMutableList()
            var w = win(c)
            while (w == null) {
                val ms = moves(c)
                c[ms[floor(r() * ms.size).toInt()]] = toMove(c)
                w = win(c)
            }
            return score(w)
        }
        fun vis(): Map<Int, Int> = LinkedHashMap<Int, Int>().also { m -> root.kids!!.forEach { (mv, k) -> m[mv] = k.n } }
        for (s in 1..sims) {
            var n = root
            val path = arrayListOf(root)
            while (n.kids != null && win(n.b) == null) {
                val nn = n.n
                var best: Node? = null
                var bs = -1e9
                n.kids!!.forEach { (_, c) ->
                    val q = if (c.n > 0) c.w / c.n else if (o.puct) .5 else 1e9
                    val sc = if (o.puct) q + 1.5 * c.p * sqrt(nn + 1.0) / (1 + c.n) else q + if (c.n > 0) 1.4 * sqrt(ln(nn + 1.0) / c.n) else 0.0
                    if (sc > bs) { bs = sc; best = c }
                }
                n = best!!
                path += n
            }
            val w = win(n.b)
            val v = if (w != null) score(w) else {
                if (n.kids == null) expand(n)
                o.evalFn?.invoke(n.b) ?: rollout(n.b)
            }
            path.forEach { x ->
                x.n++
                val mover = if (toMove(x.b) == "X") "O" else "X"
                x.w += ((if (mover == "X") v else -v) + 1) / 2
            }
            if (s in o.at) snaps[s] = vis()
        }
        snaps[-1] = vis()
        return snaps
    }

    fun grid(b: List<String>, vis: Map<Int, Int>?, hl: List<Int>?): RbBlock.Grid {
        val tot = vis?.values?.sum() ?: 0
        return RbBlock.Grid(44, listOf("a", "b", "c"), (0..2).map { r ->
            RbGRow("", "#9aa0ae", (0..2).map { c ->
                val i = r * 3 + c
                if (b[i].isNotEmpty()) RbCell(b[i], "#3a3f4c", "#fff")
                else {
                    val n = vis?.get(i)
                    RbCell(
                        n?.toString() ?: "sq ${i + 1}",
                        if (n != null && tot > 0) "rgba(59,130,246,${js(.15 + .8 * n / tot)})" else "#1f232d", "#fff",
                        if (hl != null && i in hl) "inset 0 0 0 2px #22a06b" else "none",
                    )
                }
            })
        })
    }

    fun visBars(vis: Map<Int, Int>, best: List<Int>, col: String? = null): RbBlock.Bars {
        val tot = vis.values.sum().toDouble()
        return B(vis.keys.sorted().map { m ->
            row("sq ${m + 1}", "${vis.getValue(m)} · ${pct(vis.getValue(m) / tot)}", vis.getValue(m) / tot, if (m in best) (col ?: "#22a06b") else "#3b82f6", if (m in best) "#f2f3f7" else "#c3c7d1")
        })
    }

    fun share(v: Map<Int, Int>, best: List<Int>) = best.sumOf { v[it] ?: 0 }.toDouble() / v.values.sum()

    /** The most visited move, the lowest square on a tie. */
    fun pick(v: Map<Int, Int>): Int = v.keys.sorted().reduce { a, c -> if (v.getValue(c) > v.getValue(a)) c else a }
}

// ── 60a Dyna-Q ──

internal class DynaSnap(val q: Map<String, List<Double>>, val err: Double)

private val dynaCache = HashMap<Int, List<DynaSnap>>()

/** Snapshots after each of 60 episodes (index 0 is before any); the first 30 are the 30-episode run. */
internal fun rbDyna(n: Int): List<DynaSnap> = synchronized(dynaCache) {
    dynaCache.getOrPut(n) {
        val r = Rb.rng(41L + n)
        val q = HashMap<String, DoubleArray>()
        val m = HashMap<String, RbGw.Mv>()
        val keys = ArrayList<String>()
        fun qa(k: String) = q.getOrPut(k) { DoubleArray(4) }
        val v = RbGw.vi(.9, -0.04).V
        fun err() = states.sumOf { p -> abs(qa(key(p)).max() - v.getValue(key(p))) } / states.size
        fun up(k: String, a: Int, ns: Pair<Int, Int>, rw: Double) {
            val t = rw + if (term(ns)) 0.0 else .9 * qa(key(ns)).max()
            qa(k)[a] += .5 * (t - qa(k)[a])
        }
        val snaps = arrayListOf(DynaSnap(emptyMap(), err()))
        for (e in 1..60) {
            var cur = START
            var t = 0
            while (t < 100 && !term(cur)) {
                val k = key(cur)
                val qs = qa(k)
                val a = if (r() < .1) floor(r() * 4).toInt() else Rb.argmax(qs)
                val o = mv(cur, a, -0.04)
                up(k, a, o.ns, o.r)
                val mk = "$k|$a"
                if (mk !in m) keys += mk
                m[mk] = o
                repeat(n) {
                    val kk = keys[floor(r() * keys.size).toInt()]
                    val (sk, aa) = kk.split("|")
                    up(sk, aa.toInt(), m.getValue(kk).ns, m.getValue(kk).r)
                }
                cur = o.ns
                t++
            }
            snaps += DynaSnap(q.mapValues { it.value.toList() }, err())
        }
        snaps
    }
}

internal fun rbQGrid(q: Map<String, List<Double>>) = RbGw.grid { _, k ->
    val v = q[k] ?: listOf(0.0, 0.0, 0.0, 0.0)
    val m = v.max()
    RbCell(
        if (abs(m) < 1e-9) "0" else f(m) + " " + AR[v.indexOf(m)],
        if (abs(m) < 1e-9) "#1f232d" else RbGw.vcol(m), "#fff",
        if (k == key(START)) "inset 0 0 0 1.5px #f5c542" else "none",
    )
}

private fun dynaLab(): RbLab {
    val lg = listOf("#3b82f6" to "Value", "#e5337a" to "Error vs V*", "#f5c542" to "Start")
    return RbLab(listOf("plan 0", "plan 5", "plan 50"), 1) { opt ->
        val n = listOf(0, 5, 50)[opt]
        val sn = rbDyna(n)
        List(7) { stp ->
            if (stp < 6) {
                val e = listOf(0, 1, 2, 5, 10, 30)[stp]
                frame(listOf(L("max Q(s,a) after $e episode${if (e == 1) "" else "s"} · $n planning updates per step"), rbQGrid(sn[e].q)),
                    if (e > 0) listOf(F("real step → Q update + model update"), F("mean |max Q − V*| =", f(sn[e].err, 3), "#e5337a")) else listOf(F("model: (s, a) → (s′, r), learned from experience")),
                    when {
                        e == 0 -> "Dyna-Q learns a model while it acts." to "Every real step updates Q and records what happened; then it replays imagined steps from that record."
                        n == 0 -> "Episode $e: plain Q-learning." to "Value creeps back one state per visit."
                        else -> "Episode $e: $n imagined updates per real step." to "The same experience is reused many times, so value spreads from the goal in a few episodes."
                    }, lg)
            } else {
                frame(listOf(L("mean error after 5 episodes"), B(listOf(0, 5, 50).map { x ->
                    val e = rbDyna(x)[5].err
                    row(if (x > 0) "plan ×$x" else "no planning", f(e, 3), e / .6, if (x == n) "#6d5dfc" else "#3a3f4c", if (x == n) "#f2f3f7" else "#9aa0ae")
                })), listOf(F("same real steps for all three")),
                    "Planning substitutes compute for experience." to "Useful when real steps are expensive — and only as good as the learned model.", lg)
            }
        }
    }
}

// ── 60b MCTS ──

private fun mctsLab(): RbLab {
    val lg = listOf("#3b82f6" to "Visits", "#22a06b" to "Minimax-best", "#3a3f4c" to "Occupied")
    return RbLab(listOf("30 sims", "200 sims", "1,000 sims"), 1) { opt ->
        val nn = listOf(30, 200, 1000)[opt]
        val b = RbTtt.TB
        val tb = RbTtt.best(b)
        val third = Rb.round(nn / 3.0).toInt()
        val sn = RbTtt.search(b, nn, RbTtt.Opts(Rb.rng(7), at = listOf(10, third, nn)))
        List(5) { stp ->
            val at = listOf(0, 10, third, nn)[min(stp, 3)]
            when {
                stp == 0 -> frame(listOf(L("X to move · green ring = minimax-best"), RbTtt.grid(b, null, tb.best)),
                    listOf(F("legal moves:", "${RbTtt.moves(b).size}"), F("minimax best:", tb.best.joinToString(", ") { "sq ${it + 1}" }, "#22a06b")),
                    "Monte Carlo tree search: plan by simulation." to "Select by UCB, expand one node, play a random game to the end, back the result up.", lg)
                stp < 4 -> {
                    val vis = sn.getValue(at)
                    val share = RbTtt.share(vis, tb.best)
                    frame(listOf(L("visits after $at simulations"), RbTtt.grid(b, vis, tb.best), RbTtt.visBars(vis, tb.best)),
                        listOf(F("share on best move =", pct(share), "#22a06b")),
                        "$at simulations: ${pct(share)} of visits on the best move." to "UCB keeps trying promising moves more often, so visits concentrate where results are good.", lg)
                }
                else -> {
                    val pick = RbTtt.pick(sn.getValue(-1))
                    val ok = pick in tb.best
                    frame(listOf(L("minimax value of each move (X’s view)"), B(tb.vals.map { (m, v) -> brow("sq ${m + 1}", v, 1.0, if (v > 0) "win" else if (v < 0) "loss" else "draw") })),
                        listOf(F("MCTS picks:", "sq ${pick + 1}", if (ok) "#22a06b" else "#e5484d")),
                        (if (ok) "Search found the winning move." else "Not enough simulations yet.") to "Random playouts are crude evaluators, but averaged over many games they rank moves correctly here.", lg)
                }
            }
        }
    }
}

// ── 60c AlphaGo: a value network in place of playouts ──

private fun evalN(sig: Double, r: () -> Double): (List<String>) -> Double = { b ->
    max(-1.0, min(1.0, RbTtt.minimax(b) + sig * Rb.gauss(r)))
}

private val agCache = HashMap<String, Map<Int, Int>>()

private fun agSearch(sig: Double?): Map<Int, Int> = synchronized(agCache) {
    agCache.getOrPut(sig?.toString() ?: "plain") {
        val o = if (sig == null) RbTtt.Opts(Rb.rng(3)) else RbTtt.Opts(Rb.rng(3), evalFn = evalN(sig, Rb.rng(99)))
        RbTtt.search(RbTtt.TB, 100, o).getValue(-1)
    }
}

private fun alphaGoLab(): RbLab {
    val lg = listOf("#9aa0ae" to "Playouts", "#22a06b" to "Value-guided / best", "#3b82f6" to "Other moves")
    return RbLab(listOf("σ 0", "σ 0.3", "σ 0.8"), 1) { opt ->
        val sig = listOf(0.0, .3, .8)[opt]
        val b = RbTtt.TB
        val best = RbTtt.best(b).best
        val plain = agSearch(null)
        val mine = agSearch(sig)
        fun share(v: Map<Int, Int>) = RbTtt.share(v, best)
        List(4) { stp ->
            when (stp) {
                0 -> frame(listOf(L("same position, 100 simulations each"), RbTtt.grid(b, null, best)), listOf(F("AlphaGo = MCTS + policy net + value net")),
                    "AlphaGo kept MCTS and replaced its guesswork." to "A value network judges a position directly instead of finishing the game at random.", lg)
                1 -> frame(listOf(L("plain MCTS: random playouts"), RbTtt.visBars(plain, best, "#9aa0ae")), listOf(F("share on best =", pct(share(plain)))),
                    "Baseline: random playouts." to "On a 3×3 board random games are a fair estimate; on Go they are nearly noise.", lg)
                2 -> frame(listOf(L("value network (noise σ = ${js(sig)})"), RbTtt.visBars(mine, best)),
                    listOf(F("v(s) = true value + noise σ =", js(sig)), F("share on best =", pct(share(mine)), "#22a06b")),
                    (if (sig == 0.0) "A noise-free evaluator puts ${pct(share(mine))} on the best move." else "With noise σ ${js(sig)}: ${pct(share(mine))} on the best move.") to "The “network” here is the exact game value plus noise — a stand-in that shows how evaluator quality drives search.", lg)
                else -> frame(listOf(L("share of visits on the best move"), B((listOf("playouts" to plain) + listOf(0.0, .3, .8).map { "value σ ${js(it)}" to agSearch(it) }).mapIndexed { j, (n, v) ->
                    row(n, pct(share(v)), share(v), if (j == 0) "#9aa0ae" else "#22a06b")
                })), listOf(F("less evaluator noise → more visits on the best move")),
                    "Here random playouts (${pct(share(plain))}) beat every value net." to "On 3×3, a few random games already rank moves well. AlphaGo’s gain came on 19×19, where playouts are nearly noise and a learned evaluator is the only usable one.", lg)
            }
        }
    }
}

// ── 60d AlphaZero: PUCT with a prior ──

private fun alphaZeroLab(): RbLab {
    val lg = listOf("#9aa0ae" to "Prior", "#22a06b" to "Search / best", "#3b82f6" to "Other moves")
    return RbLab(listOf("uniform", "mediocre", "misleading"), 1) { opt ->
        val b = RbTtt.TB
        val best = RbTtt.best(b).best
        val ms = RbTtt.moves(b)
        val pr: Map<Int, Double> = ms.associateWith { m ->
            when (opt) {
                0 -> 1.0 / ms.size
                1 -> if (m in best) .22 else .78 / (ms.size - 1)
                else -> if (m in best) .04 else .96 / (ms.size - 1)
            }
        }
        val sn = RbTtt.search(b, 400, RbTtt.Opts(Rb.rng(5), at = listOf(50, 400), puct = true, prior = pr, evalFn = { RbTtt.minimax(it) }))
        val prBest = best.sumOf { pr.getValue(it) }
        List(4) { stp ->
            when {
                stp == 0 -> frame(listOf(L("prior π from the policy network"), B(ms.map { m -> row("sq ${m + 1}", pct(pr.getValue(m)), pr.getValue(m), if (m in best) "#22a06b" else "#9aa0ae") })),
                    listOf(F("prior on best move =", pct(prBest))),
                    "AlphaZero: one network gives a prior and a value." to "No rollouts, no human games — PUCT search guided by the prior.", lg)
                stp < 3 -> {
                    val at = if (stp == 1) 50 else 400
                    val v = sn.getValue(at)
                    frame(listOf(L("visits after $at PUCT simulations"), RbTtt.grid(b, v, best), RbTtt.visBars(v, best)),
                        listOf(F("score = Q + 1.5·P·√N / (1 + n)"), F("share on best =", pct(RbTtt.share(v, best)), "#22a06b")),
                        "$at simulations: ${pct(RbTtt.share(v, best))} on the best move." to (if (opt == 2) "A misleading prior slows the search, but value backups still pull visits to the winner." else "The prior decides where to look first; values decide where to stay."), lg)
                }
                else -> {
                    val fin = RbTtt.share(sn.getValue(-1), best)
                    frame(listOf(B(listOf(row("prior", pct(prBest), prBest, "#9aa0ae"), row("search visits", pct(fin), fin, "#22a06b", "#f2f3f7")))),
                        listOf(F("training target for π = search visit distribution")),
                        "Search turns a mediocre policy into a better one." to "AlphaZero trains the network to match the search’s visits — and repeats. Self-play is policy improvement.", lg)
                }
            }
        }
    }
}

// ── 60e MuZero: search in an imperfect model ──

private val mzCache = HashMap<Double, Double>()

private fun muZeroAcc(err: Double): Double = synchronized(mzCache) {
    mzCache.getOrPut(err) {
        val b = RbTtt.TB
        val best = RbTtt.best(b).best
        var ok = 0
        for (t in 0 until 40) {
            val r = Rb.rng(200L + t)
            val fn: (List<String>) -> Double = { bb -> if (r() < err) listOf(-1.0, 0.0, 1.0)[floor(r() * 3).toInt()] else RbTtt.minimax(bb) }
            val v = RbTtt.search(b, 120, RbTtt.Opts(r, puct = true, evalFn = fn)).getValue(-1)
            if (RbTtt.pick(v) in best) ok++
        }
        ok / 40.0
    }
}

private fun muZeroLab(): RbLab {
    val lg = listOf("#22a06b" to "Correct pick / best", "#6d5dfc" to "Chosen error")
    return RbLab(listOf("error 0%", "10%", "30%"), 1) { opt ->
        val e = listOf(0.0, .1, .3)[opt]
        val b = RbTtt.TB
        val best = RbTtt.best(b).best
        List(4) { stp ->
            when (stp) {
                0 -> frame(listOf(RbTtt.grid(b, null, best), S(st("AlphaZero", "needs the rules to simulate moves"), st("MuZero", "learns a model of what matters"))),
                    listOf(F("h = repr(obs) · h′,r = dyn(h,a) · p,v = pred(h)")),
                    "MuZero searches without the rules." to "It learns a hidden-state model that only has to predict reward, value and policy — not the board.", lg)
                1 -> frame(listOf(L("unrolled in latent space"), C(listOf(tok("h₀", "plain", "repr(board)"), tok("a: sq ${best[0] + 1}", "cur"), tok("h₁", "ans", "r, v, p"), tok("a′", "cur"), tok("h₂", "ans", "r, v, p")))),
                    listOf(F("no board is ever reconstructed")),
                    "Planning happens on learned states." to "Each hidden state only needs to be good enough to predict what search will ask for.", lg)
                2 -> {
                    val a = muZeroAcc(e)
                    frame(listOf(B(listOf(row("picks best move", pct(a), a, "#22a06b", "#f2f3f7")))),
                        listOf(F("model error rate ${pct(e)} → accuracy", pct(a), "#22a06b")),
                        "With ${pct(e)} model error, search picks the best move ${pct(a)} of the time." to "40 searches of 120 simulations each, every value from the imperfect model.", lg)
                }
                else -> frame(listOf(L("accuracy by model error"), B(listOf(0.0, .1, .3).map { x ->
                    val a = muZeroAcc(x)
                    row("error ${pct(x)}", pct(a), a, if (x == e) "#6d5dfc" else "#3a3f4c", if (x == e) "#f2f3f7" else "#9aa0ae")
                })), listOf(F("search averages out some model error")),
                    "Search is robust to a mostly-right model." to "MuZero matched AlphaZero on Go, chess and shogi and extended to Atari, where no rules are available to search with.", lg)
            }
        }
    }
}

// ── 60f World Models ──

private class Wm(val m: Map<String, RbGw.Mv>, val v: Map<String, Double>, val p: Map<String, Int>, val known: Int, val out: String, val n: Int)

private val wmCache = HashMap<Int, Wm>()

private fun wm(steps: Int): Wm = synchronized(wmCache) {
    wmCache.getOrPut(steps) {
        val r = Rb.rng(17)
        val m = HashMap<String, RbGw.Mv>()
        var cur = START
        repeat(steps) {
            val a = floor(r() * 4).toInt()
            val o = mv(cur, a, -0.04)
            m[key(cur) + "|" + a] = o
            cur = if (term(o.ns)) START else o.ns
        }
        fun q(v: Map<String, Double>, kk: String, a: Int): Double {
            val o = m[kk + "|" + a] ?: return -0.04 + .9 * (v[kk] ?: 0.0)
            return o.r + if (term(o.ns)) 0.0 else .9 * (v[key(o.ns)] ?: 0.0)
        }
        var v = HashMap<String, Double>()
        repeat(200) {
            val n = HashMap<String, Double>()
            states.forEach { p -> n[key(p)] = (0..3).maxOf { a -> q(v, key(p), a) } }
            v = n
        }
        val p = HashMap<String, Int>()
        states.forEach { s ->
            var bi = 0
            var bv = -1e9
            for (a in 0..3) {
                val x = q(v, key(s), a)
                if (x > bv + 1e-9) { bv = x; bi = a }
            }
            p[key(s)] = bi
        }
        var c = START
        var out = "timeout"
        var n = 0
        while (n < 20 && !term(c)) { c = mv(c, p.getValue(key(c)), -0.04).ns; n++ }
        if (term(c)) out = if (key(c) == RbGw.GOAL) "goal" else "pit"
        Wm(m, v, p, m.size, out, n)
    }
}

private fun worldModelLab(): RbLab {
    val lg = listOf("#3b82f6" to "Observed / value", "#22a06b" to "Goal reached", "#e5484d" to "Failure")
    return RbLab(listOf("8 steps", "40 steps", "120 steps"), 1) { opt ->
        val tt = listOf(8, 40, 120)[opt]
        val w = wm(tt)
        List(4) { stp ->
            when (stp) {
                0 -> frame(listOf(L("share of (state, action) pairs ever observed"), B(listOf(8, 20, 40, 120).map { x ->
                    val k = wm(x).known / 52.0
                    row("$x steps", pct(k), k, if (x == tt) "#3b82f6" else "#3a3f4c", if (x == tt) "#f2f3f7" else "#9aa0ae")
                })), listOf(F("13 states × 4 actions =", "52 pairs")),
                    "A world model predicts what happens next." to "Here it is a table filled in from random real steps. It only knows what it has seen.", lg)
                1 -> frame(listOf(L("actions known per cell after $tt real steps"), RbGw.grid { _, k ->
                    val n = (0..3).count { a -> (k + "|" + a) in w.m }
                    RbCell("$n/4", "rgba(59,130,246,${js(.1 + .2 * n)})", "#fff")
                }), listOf(F("known pairs =", "${w.known} / 52", "#3b82f6")),
                    "${pct(w.known / 52.0)} of the model observed." to "Unknown actions are assumed to go nowhere — a guess the planner can’t check.", lg)
                2 -> frame(listOf(L("plan inside the model (value iteration)"), RbGw.vgrid(w.v, w.p, key(START))), listOf(F("planning cost: zero real steps")),
                    "Planning is free inside the model." to "The plan is optimal for the model — which is not the same as optimal for the world.", lg)
                else -> {
                    val good = w.out == "goal"
                    frame(listOf(S(st("executed in the real grid", if (good) "reached the goal" else if (w.out == "pit") "fell in the pit" else "got stuck", if (good) "#22a06b" else "#e5484d"), st("steps", "${w.n}"))),
                        listOf(F("outcome:", w.out, if (good) "#22a06b" else "#e5484d")),
                        if (good) "The imagined plan works for real." to "With enough coverage, the model is right where it matters."
                        else "The plan fails in reality." to "Gaps in the model become blind spots in the plan. More real data, or uncertainty-aware planning, fixes it.", lg)
                }
            }
        }
    }
}

// ── 60g Dreamer ──

private fun dreamerConv(x: Int): Int {
    val sn = rbDyna(x)
    for (e in 1..60) if (sn[e].err < .05) return e
    return 60
}

private fun dreamerLab(): RbLab {
    val lg = listOf("#9aa0ae" to "Model-free", "#22a06b" to "With imagination", "#6d5dfc" to "Chosen")
    return RbLab(listOf("model-free", "imagine ×5", "imagine ×20"), 2) { opt ->
        val n = listOf(0, 5, 20)[opt]
        List(4) { stp ->
            when (stp) {
                0 -> frame(listOf(S(st("world model", "learned from real steps"), st("actor + critic", "trained only on imagined rollouts"), st("real data", "used to improve the model"))),
                    listOf(F("Dreamer: learn behaviour in imagination")),
                    "Dreamer trains its policy inside its own model." to "Real experience improves the world model; the actor-critic learns from imagined trajectories.", lg)
                1 -> {
                    val sn = rbDyna(n)
                    frame(listOf(L("mean |Q − Q*| by episode, $n imagined updates per step"), B(listOf(1, 2, 5, 10, 20).map { e -> row("ep $e", f(sn[e].err, 3), sn[e].err / .6, if (n > 0) "#22a06b" else "#9aa0ae") })),
                        listOf(F("episodes to error < 0.05 =", "${dreamerConv(n)}", "#22a06b")),
                        "${dreamerConv(n)} episodes to converge." to (if (n > 0) "Imagined updates do most of the learning." else "Model-free: every bit of learning costs a real step."), lg)
                }
                2 -> frame(listOf(L("episodes to converge"), B(listOf(0, 5, 20).map { x ->
                    row(if (x > 0) "imagine ×$x" else "model-free", "${dreamerConv(x)}", dreamerConv(x) / 60.0, if (x == n) "#6d5dfc" else "#3a3f4c", if (x == n) "#f2f3f7" else "#9aa0ae")
                })), listOf(F("same real interaction per episode")),
                    "More imagination, fewer real episodes." to "The real steps are unchanged; only the thinking per step went up.", lg)
                else -> frame(listOf(S(st("V1", "continuous latent, learns from pixels"), st("V2", "discrete latents, Atari"), st("V3", "fixed hyperparameters across domains"))),
                    listOf(F("tabular stand-in for a learned latent model")),
                    "Dreamer V1–V3 scale this idea to pixels." to "The model is a recurrent latent network; this grid uses a table so every number is checkable.", lg)
            }
        }
    }
}

// ── 60h MBPO ──

private fun mbpoLab(): RbLab {
    val lg = listOf("#e5337a" to "Compounding error", "#22a06b" to "Best rollout length", "#3b82f6" to "Useful data")
    return RbLab(listOf("ε 5%", "ε 10%", "ε 20%"), 1) { opt ->
        val e = listOf(.05, .1, .2)[opt]
        val ks = listOf(1, 2, 3, 5, 8, 12, 20)
        fun ok(k: Int) = (1 - e).pow(k)
        fun use(k: Int) = k * ok(k)
        val kstar = -1 / ln(1 - e)
        val kr = Rb.round(kstar).toInt()
        List(4) { stp ->
            when (stp) {
                0 -> frame(listOf(S(st("learned model", "wrong ε of the time per step"), st("question", "how many steps to trust it?"))),
                    listOf(F("per-step model error ε =", pct(e), "#e5337a")),
                    "MBPO asks how far to trust the model." to "Every imagined step can be wrong, and errors compound.", lg)
                1 -> frame(listOf(L("P(an imagined rollout is still correct)"), B(ks.map { k -> row("$k steps", pct(ok(k)), ok(k), "#e5337a") })),
                    listOf(F("(1 − ${js(e)})^k")),
                    "After 20 steps only ${pct(ok(20))} of rollouts are still right." to "Long imagined rollouts are mostly fiction.", lg)
                2 -> {
                    val mx = ks.maxOf { use(it) }
                    frame(listOf(L("useful imagined steps per rollout = k·(1−ε)^k"), B(ks.map { k -> row("$k steps", f(use(k), 2), use(k) / mx, if (abs(k - kstar) < max(1.0, kstar * .4)) "#22a06b" else "#3b82f6") })),
                        listOf(F("best k ≈ −1 / ln(1−ε) =", f(kstar, 1), "#22a06b")),
                        "Sweet spot: about $kr steps." to "Short enough to stay accurate, long enough to add data.", lg)
                }
                else -> frame(listOf(S(st("full rollouts from start", "long, compounding error"), st("MBPO branches", "short k-step rollouts from many real states"), st("k used here", "$kr", "#22a06b"))),
                    listOf(F("many short branches > few long ones")),
                    "Branch short rollouts from real states." to "MBPO keeps model data near real data, then trains SAC on the mix.", lg)
            }
        }
    }
}
