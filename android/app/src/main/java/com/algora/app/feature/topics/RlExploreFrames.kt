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
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign
import kotlin.math.sqrt

// ── Exploration and multi-agent storyboard frames ────────────────────────────
// Four-armed bandits pulled live, a sparse-reward maze with a noisy TV for count bonuses, ICM and RND,
// and coordination games where independent learners, VDN, QMIX, MADDPG and fictitious self-play are each
// trained or solved on the spot. Drawn by RlBoardLabs.kt; the iOS port (RlExploreFrames.swift) matches it.

internal val rlExploreTopicIds = setOf(
    "epsilon_greedy", "boltzmann_exploration", "intrinsic_motivation", "icm", "rnd", "minimax", "iql", "vdn", "qmix", "maddpg", "self_play",
)

internal fun rlExploreLab(topicId: String): RbLab? = when (topicId) {
    "epsilon_greedy" -> bandLab("eps", listOf(.01, .1, .3), "ε")
    "boltzmann_exploration" -> bandLab("boltz", listOf(.05, .15, .5), "τ")
    "intrinsic_motivation" -> intrinsicLab()
    "icm" -> icmLab()
    "rnd" -> rndLab()
    "minimax" -> minimaxLab()
    "iql" -> iqlLab()
    "vdn" -> vdnLab()
    "qmix" -> qmixLab()
    "maddpg" -> maddpgLab()
    "self_play" -> selfPlayLab()
    else -> null
}

// ── 61a/61b ε-greedy and Boltzmann bandits ──

private val MU = listOf(.30, .50, .45, .72)
private val ARM = listOf("A", "B", "C", "D")

private class BanditH(val a: Int, val rw: Int, val ex: Boolean, val pr: List<Double>?, val old: Double, val q: List<Double>, val n: List<Int>, val tot: Int)

private val bhCache = HashMap<String, List<BanditH>>()

private fun bandit(kind: String, p: Double, seed: Int): List<BanditH> = synchronized(bhCache) {
    bhCache.getOrPut("$kind$p _$seed") {
        val r = Rb.rng(seed * 13L + 5)
        val q = DoubleArray(4)
        val n = IntArray(4)
        val h = ArrayList<BanditH>()
        var tot = 0
        repeat(200) {
            var a: Int
            var ex = false
            var pr: List<Double>? = null
            if (kind == "eps") {
                ex = r() < p
                if (ex) a = floor(r() * 4).toInt()
                else {
                    val m = q.max()
                    val c = (0..3).filter { q[it] >= m - 1e-12 }
                    a = c[floor(r() * c.size).toInt()]
                }
            } else {
                pr = Rb.softmax(q.map { it / p })
                val u = r()
                var acc = 0.0
                a = 3
                for (i in 0 until 4) {
                    acc += pr[i]
                    if (u < acc) { a = i; break }
                }
            }
            val rw = if (r() < MU[a]) 1 else 0
            val old = q[a]
            n[a]++
            q[a] += (rw - old) / n[a]
            tot += rw
            h += BanditH(a, rw, ex, pr, old, q.toList(), n.toList(), tot)
        }
        h
    }
}

private class BStats(val best: Double, val rew: Double)

private fun bStats(kind: String, p: Double): BStats {
    var best = 0.0
    var rew = 0.0
    for (s in 1..50) {
        val h = bandit(kind, p, s)
        best += h[199].n[3].toDouble() / 200 / 50
        rew += h[199].tot.toDouble() / 200 / 50
    }
    return BStats(best, rew)
}

private fun bandLab(kind: String, ps: List<Double>, pl: String): RbLab {
    val lg = listOf("#f5c542" to "Pulled now", "#3b82f6" to "Estimate") + (if (kind == "boltz") listOf("#6d5dfc" to "Draw prob.") else emptyList()) + listOf("#22a06b" to "Best setting")
    return RbLab(ps.map { "$pl ${js(it)}" }, 1) { opt ->
        val p = ps[opt]
        List(5) { stp ->
            if (stp < 4) {
                val t = listOf(1, 10, 50, 200)[stp]
                val hh = bandit(kind, p, 1)
                val h = hh[t - 1]
                val arm = ARM[h.a]
                val prevQ = if (t > 1) hh[t - 2].q else listOf(0.0, 0.0, 0.0, 0.0)
                val tie = prevQ.all { it == prevQ[0] }
                val blocks = mutableListOf<RbBlock>(
                    C(listOf(tok("pull $t", "plain"), tok(if (kind == "eps") (if (h.ex) "explore" else "exploit") else "softmax draw", "cur"), tok("arm $arm", "plain"), tok("reward ${h.rw}", if (h.rw > 0) "done" else "err"))),
                    L("estimate Q(a) · μ = true win rate"),
                    B(ARM.mapIndexed { i, a ->
                        row("$a · μ ${f(MU[i])}", f(h.q[i]) + " · " + h.n[i] + "×", h.q[i], if (i == h.a) "#f5c542" else "#3b82f6", if (i == h.a) "#f5c542" else if (i == 3) "#5fd49b" else null)
                    }),
                )
                if (kind == "boltz") {
                    blocks += L("π(a) = softmax(Q/τ) used for this draw")
                    blocks += B(ARM.mapIndexed { i, a -> row(a, pct(h.pr!![i]), h.pr[i], if (i == h.a) "#f5c542" else "#6d5dfc") })
                }
                val fx = listOf(
                    if (kind == "eps") F("ε =", js(p) + " · explore with probability " + pct(p))
                    else F("π($arm) = e^(${f(h.old)}/${js(p)}) / Σ =", pct(h.pr!![h.a]), "#b3abff"),
                    F("Q($arm) ← ${f(h.old)} + (${h.rw} − ${f(h.old)})/${h.n[h.a]} =", f(h.q[h.a]), "#f5c542"),
                )
                val top = h.q.indexOf(h.q.max())
                val capB = "D, the truly best arm, has ${h.n[3]} of $t pulls" + if (top == 3) " and now leads the estimates." else "."
                val cap = if (kind == "eps") {
                    if (h.ex) "Explore: a random draw landed on $arm." to capB
                    else (if (tie) "Exploit: every estimate is tied, so $arm wins the tie-break." else "Exploit: $arm had the top estimate, ${f(h.old)}.") to capB
                } else "$arm was drawn with probability ${pct(h.pr!![h.a])}." to capB
                frame(blocks, fx, cap, lg)
            } else {
                val ss = ps.map { bStats(kind, it) }
                var bi = 0
                ss.forEachIndexed { i, x -> if (x.rew > ss[bi].rew) bi = i }
                frame(listOf(
                    L("share of pulls on D · mean of 50 runs"),
                    B(ps.mapIndexed { i, x -> row("$pl ${js(x)}", pct(ss[i].best), ss[i].best, if (i == bi) "#22a06b" else "#3a3f4c", if (i == opt) "#f2f3f7" else "#9aa0ae") }),
                    L("reward per pull"),
                    B(ps.mapIndexed { i, x -> row("$pl ${js(x)}", f(ss[i].rew, 3), ss[i].rew / .72, if (i == bi) "#22a06b" else "#3a3f4c", if (i == opt) "#f2f3f7" else "#9aa0ae") }),
                ), listOf(F("best possible = always D =", "0.720 per pull", "#22a06b")),
                    "$pl ${js(ps[bi])} earns the most: ${f(ss[bi].rew, 3)} per pull." to (if (kind == "eps") "Exploration costs reward every time it fires, but without it an early lucky arm can hold the lead for good." else "Low τ behaves almost greedily, high τ almost uniformly. Unlike ε, bad arms get fewer draws the worse they look."), lg)
            }
        }
    }
}

// ── The sparse-reward maze: 5 × 8, start (0,0), goal cell 39, a noisy TV at cell 19 ──

private class Explore(val vis: IntArray, val reached: Int, val first: Int, val tv: Double, val seen: Int, val lb: Map<Int, Double>)

private val exCache = HashMap<String, Explore>()

private fun explore(m: String, seed: Int): Explore = synchronized(exCache) {
    exCache.getOrPut("${m}_$seed") {
        val r = Rb.rng(seed * 97L + 13)
        val rr = 5
        val cc = 8
        val beta = if (m == "eps") 0.0 else .3
        val q = HashMap<Int, DoubleArray>()
        val nn = HashMap<String, Int>()
        val ns = HashMap<Int, Int>()
        val no = HashMap<String, Int>()
        val lb = HashMap<Int, Double>()
        fun qa(k: Int) = q.getOrPut(k) { DoubleArray(4) }
        val vis = IntArray(40)
        var reached = 0
        var first = -1
        var tvS = 0
        var steps = 0
        for (e in 0 until 60) {
            var s = 0 to 0
            vis[0]++
            for (t in 0 until 40) {
                val k = s.first * cc + s.second
                val qs = qa(k)
                val a: Int
                if (r() < .1) a = floor(r() * 4).toInt()
                else {
                    var b = 0
                    for (i in 1 until 4) if (qs[i] > qs[b] + 1e-12) b = i
                    a = b
                }
                var n = (s.first + RbGw.AC[a][0]) to (s.second + RbGw.AC[a][1])
                if (n.first < 0 || n.first >= rr || n.second < 0 || n.second >= cc) n = s
                val ci = n.first * cc + n.second
                val isTV = ci == 19
                val ch = if (isTV) floor(r() * 10).toInt() else 0
                val sa = "$k|$a"
                val ok = "$ci#$ch"
                nn[sa] = (nn[sa] ?: 0) + 1
                ns[ci] = (ns[ci] ?: 0) + 1
                no[ok] = (no[ok] ?: 0) + 1
                val b = when (m) {
                    "count" -> 1 / sqrt(ns.getValue(ci).toDouble())
                    "icmpix" -> if (isTV) max(.9, 1 / sqrt(nn.getValue(sa).toDouble())) else 1 / sqrt(nn.getValue(sa).toDouble())
                    "icmfeat" -> 1 / sqrt(nn.getValue(sa).toDouble())
                    "rnd" -> 1 / sqrt(no.getValue(ok).toDouble())
                    else -> 0.0
                }
                lb[ci] = beta * b
                val goal = ci == 39
                qs[a] = (if (goal) 1.0 else 0.0) + beta * b + if (goal) 0.0 else .5 * qa(ci).max()
                vis[ci]++
                steps++
                if (isTV) tvS++
                s = n
                if (goal) {
                    reached++
                    if (first < 0) first = e
                    break
                }
            }
        }
        Explore(vis, reached, first, tvS.toDouble() / steps, vis.count { it > 0 }, lb)
    }
}

private class ExStats(val runs: Int, val seen: Double, val tv: Double)

private val esCache = HashMap<String, ExStats>()

private fun exStats(m: String): ExStats = synchronized(esCache) { esCache[m] } ?: run {
    var runs = 0
    var seen = 0.0
    var tv = 0.0
    for (s in 1..30) {
        val o = explore(m, s)
        if (o.reached > 0) runs++
        seen += o.seen / 30.0
        tv += o.tv / 30
    }
    ExStats(runs, seen, tv).also { synchronized(esCache) { esCache[m] = it } }
}

private class MCell(val t: String? = null, val bg: String? = null, val tvRing: Boolean = false)

private fun mgrid(fn: (Int) -> MCell) = RbBlock.Grid(30, (0..7).map { "c$it" }, (0..4).map { r ->
    RbGRow("r$r", "#9aa0ae", (0..7).map { c ->
        val i = r * 8 + c
        val o = fn(i)
        val tag = if (i == 0) "S" else if (i == 39) "G" else if (i == 19) "?" else null
        RbCell(
            tag ?: o.t?.ifEmpty { null } ?: "", o.bg ?: "#1f232d", "#fff",
            if (i == 19 && o.tvRing) "inset 0 0 0 2px #e5337a" else if (i == 39) "inset 0 0 0 2px #22a06b" else "none",
        )
    })
})

private fun heatGrid(o: Explore, tv: Boolean = false): RbBlock.Grid {
    val mx = o.vis.max()
    return mgrid { i ->
        val v = o.vis[i]
        MCell(
            if (v > 0) (if (v >= 1000) js(Rb.round(v / 100.0) / 10) + "k" else "$v") else "",
            if (v > 0) "rgba(59,130,246,${Rb.a2(.12 + .8 * ln(1.0 + v) / ln(1.0 + mx))})" else "#1f232d",
            tv,
        )
    }
}

private fun bonusGrid(o: Explore, tv: Boolean = false) = mgrid { i ->
    val b = o.lb[i] ?: .3
    MCell(f(b, 2), "rgba(109,93,252,${Rb.a2(.08 + .85 * b / .3)})", tv)
}

// ── 61c Intrinsic Motivation ──

private fun intrinsicLab(): RbLab {
    val lg = listOf("#3b82f6" to "Visits", "#6d5dfc" to "Bonus", "#22a06b" to "Goal / with bonus")
    return RbLab(listOf("ε-greedy", "count bonus"), 1) { opt ->
        val m = listOf("eps", "count")[opt]
        val nm = listOf("ε-greedy", "count bonus")[opt]
        val o = explore(m, 1)
        List(5) { stp ->
            when (stp) {
                0 -> frame(listOf(L("5 × 8 open maze · reward +1 only at G"), mgrid { MCell() }),
                    listOf(F("shortest path S → G =", "11 steps"), F("budget =", "60 episodes × 40 steps")),
                    "Sparse reward: nothing to learn from until G is found." to "Greedy Q-learning with ε = 0.1, ties always broken the same way — how an untrained network behaves.", lg)
                1 -> frame(listOf(L("visits per cell · $nm · run 1"), heatGrid(o)),
                    listOf(F("cells ever visited =", "${o.seen} / 40", if (m == "eps") "#e5337a" else "#22a06b"), F("episodes reaching G =", "${o.reached} / 60", if (o.reached > 0) "#22a06b" else "#e5337a")),
                    if (m == "eps") "ε-greedy dithers around the start." to "A random step 10% of the time rarely adds up to a trip outward: ${o.seen} of 40 cells seen, G reached in ${o.reached} of 60 episodes."
                    else "A count bonus pushes outward." to "Every visit makes a cell pay less, so the greedy choice drifts toward unseen cells: ${o.seen} of 40 seen, G reached in ${o.reached} of 60 episodes.", lg)
                2 -> {
                    val c = explore("count", 1)
                    frame(listOf(L("bonus β/√N(s′) at the end of run 1"), bonusGrid(c)),
                        listOf(F("r⁺ = r + β/√N(s′),  β =", "0.3", "#b3abff"), F("bonus at S =", f(c.lb[0] ?: .3, 3))),
                        "Well-trodden cells pay almost nothing." to "Cells never entered still pay the full 0.30, so they look better than anywhere the agent has been.", lg)
                }
                3 -> {
                    val e = exStats("eps")
                    val cn = exStats("count")
                    frame(listOf(
                        L("runs that ever reached G · 30 runs each"),
                        B(listOf(row("ε-greedy", "${e.runs} / 30", e.runs / 30.0, "#9aa0ae"), row("count bonus", "${cn.runs} / 30", cn.runs / 30.0, "#22a06b", "#f2f3f7"))),
                        L("mean cells visited"),
                        B(listOf(row("ε-greedy", f(e.seen, 1), e.seen / 40, "#9aa0ae"), row("count bonus", f(cn.seen, 1), cn.seen / 40, "#22a06b", "#f2f3f7"))),
                    ), listOf(F("same agent, same budget; only the bonus differs")),
                        "${cn.runs} of 30 runs find the goal with a bonus, ${e.runs} without." to "Directed novelty covers ${f(cn.seen, 1)} cells on average against ${f(e.seen, 1)} for undirected noise.", lg)
                }
                else -> frame(listOf(S(st("needs", "a count per state"), st("breaks when", "states never repeat (pixels)"), st("learned stand-ins", "ICM, RND, pseudo-counts", "#b3abff"))),
                    listOf(F("bonus must generalise to unseen states")),
                    "Counting needs states that repeat." to "In image-based tasks no frame is seen twice, so the novelty signal has to come from a learned model instead.", lg)
            }
        }
    }
}

// ── 61d ICM ──

private fun icmLab(): RbLab {
    val lg = listOf("#9aa0ae" to "Normal cell", "#e5337a" to "Noisy TV", "#22a06b" to "ICM features")
    return RbLab(listOf("pixel model", "ICM features"), 0) { opt ->
        val m = listOf("icmpix", "icmfeat")[opt]
        val nm = listOf("pixel model", "ICM features")[opt]
        val o = explore(m, 1)
        val pix = m == "icmpix"
        List(5) { stp ->
            when (stp) {
                0 -> frame(listOf(L("same maze, plus a noisy TV at ?"), mgrid { MCell(tvRing = true) }, S(st("forward model", "predicts s′ from (s, a)"), st("TV cell", "random channel 0–9 every visit", "#e5337a"))),
                    listOf(F("bonus = β · prediction error,  β =", "0.3", "#b3abff")),
                    "ICM pays the agent for being surprised." to "Where its forward model is wrong, the agent is curious — and goes there.", lg)
                1 -> frame(listOf(L("prediction error by visits to (s, a)"), RbBlock.Cols(listOf(1, 4, 16, 64).map { n ->
                    val e = 1 / sqrt(n.toDouble())
                    val t = if (pix) max(.9, e) else e
                    RbVCol(f(t), "$n visits", "#9aa0ae", listOf(RbVBar(e * 62, "#9aa0ae"), RbVBar(t * 62, "#e5337a")))
                })), listOf(if (pix) F("TV error floor =", "0.90 · right 1 time in 10", "#e5337a") else F("in φ-space the TV error =", f(1.0 / 8) + " at 64 visits", "#22a06b")),
                    if (pix) "The TV is never predictable." to "Normal cells get boring as the model learns them; the TV stays at 0.90 because the next channel is pure chance."
                    else "In feature space the TV is just a cell." to "The channel is not caused by the action, so the features drop it and the error falls like everywhere else.", lg)
                2 -> frame(listOf(L("visits per cell · $nm · run 1"), heatGrid(o, true)),
                    listOf(F("steps spent on the TV =", pct(o.tv), "#e5337a"), F("cells ever visited =", "${o.seen} / 40")),
                    if (pix) "The pixel model gets hooked on the TV." to "Its bonus never fades, so run 1 spent ${pct(o.tv)} of all steps on that one cell."
                    else "Features fix the noisy TV." to "ICM predicts in features trained by an inverse model, which keeps only what actions control. TV time: ${pct(o.tv)}.", lg)
                3 -> {
                    val p = exStats("icmpix")
                    val f2 = exStats("icmfeat")
                    frame(listOf(
                        L("share of steps on the TV · 30 runs"),
                        B(listOf(row("pixel model", pct(p.tv), p.tv / .25, "#e5337a"), row("ICM features", pct(f2.tv), f2.tv / .25, "#22a06b", "#f2f3f7"))),
                        L("runs that ever reached G"),
                        B(listOf(row("pixel model", "${p.runs} / 30", p.runs / 30.0, "#e5337a"), row("ICM features", "${f2.runs} / 30", f2.runs / 30.0, "#22a06b", "#f2f3f7"))),
                    ), listOf(F("TV time ·", pct(p.tv) + " → " + pct(f2.tv), "#22a06b")),
                        "Features cut TV time from ${pct(p.tv)} to ${pct(f2.tv)}." to "The trap is gone, but per-action novelty spreads slower than a per-cell count: ${f2.runs} of 30 runs reach G.", lg)
                }
                else -> frame(listOf(S(st("inverse model", "predicts a from φ(s), φ(s′)"), st("forward model", "predicts φ(s′) from φ(s), a"), st("bonus", "forward error in φ-space", "#b3abff"))),
                    listOf(F("L = (1 − β)·L_inverse + β·L_forward")),
                    "Two models, one bonus." to "The inverse model shapes the features; the forward model’s error is the curiosity signal.", lg)
            }
        }
    }
}

// ── 61e RND ──

private fun rndLab(): RbLab {
    val lg = listOf("#22a06b" to "RND", "#e5337a" to "ICM pixels / TV", "#3b82f6" to "Visits / other")
    return RbLab(listOf("RND", "ICM pixels", "ε-greedy"), 0) { opt ->
        val m = listOf("rnd", "icmpix", "eps")[opt]
        val nm = listOf("RND", "ICM pixels", "ε-greedy")[opt]
        val o = explore(m, 1)
        List(4) { stp ->
            when (stp) {
                0 -> frame(listOf(S(st("target f", "fixed random net: obs → vector"), st("predictor f̂", "trained to match f where visited"), st("bonus", "‖f̂(s′) − f(s′)‖²", "#b3abff"))),
                    listOf(F("error after n sightings of an obs ≈", "1/√n")),
                    "RND keeps “bonus = prediction error” but changes the target." to "f is a fixed function of the observation, so every observation eventually becomes predictable — even the TV’s.", lg)
                1 -> frame(listOf(L("visits per cell · $nm · run 1"), heatGrid(o, true)),
                    listOf(F("cells visited =", "${o.seen} / 40"), F("steps on TV =", pct(o.tv), "#e5337a")),
                    when (m) {
                        "rnd" -> "RND explores the whole maze." to "Run 1 saw ${o.seen} of 40 cells and reached G in ${o.reached} episodes."
                        "icmpix" -> "ICM on pixels circles the TV." to "${pct(o.tv)} of steps on one cell; ${o.seen} cells seen."
                        else -> "ε-greedy barely leaves the start." to "${o.seen} of 40 cells seen, G reached ${o.reached} times."
                    }, lg)
                2 -> {
                    val r2 = explore("rnd", 1)
                    val p = explore("icmpix", 1)
                    frame(listOf(L("RND bonus at the end of run 1"), bonusGrid(r2, true)),
                        listOf(F("TV bonus · RND", f(r2.lb[19] ?: .3, 3), "#22a06b"), F("TV bonus · ICM pixels", f(p.lb[19] ?: .3, 3), "#e5337a")),
                        "Even the TV gets boring." to "Ten channels are ten observations to learn, not an endless supply: the TV’s bonus fell to ${f(r2.lb[19] ?: .3, 2)}.", lg)
                }
                else -> {
                    val ms = listOf("eps" to "ε-greedy", "count" to "count", "icmpix" to "ICM pixels", "icmfeat" to "ICM feat.", "rnd" to "RND")
                    val ss = ms.map { exStats(it.first) }
                    var bi = 0
                    ss.forEachIndexed { i, x -> if (x.runs > ss[bi].runs) bi = i }
                    frame(listOf(
                        L("runs that ever reached G · 30 each"),
                        B(ms.mapIndexed { i, (k, l) -> row(l, "${ss[i].runs} / 30", ss[i].runs / 30.0, if (k == "rnd") "#22a06b" else if (k == "icmpix") "#e5337a" else "#3b82f6", if (k == m) "#f2f3f7" else "#9aa0ae") }),
                        L("steps on the TV"),
                        B(ms.mapIndexed { i, (k, l) -> row(l, pct(ss[i].tv), ss[i].tv / .25, if (k == "icmpix") "#e5337a" else "#3a3f4c", if (k == m) "#f2f3f7" else "#9aa0ae") }),
                    ), listOf(F("most runs reaching G =", ms[bi].second, "#22a06b")),
                        "RND: ${ss[4].runs} of 30 runs reach G, with ${pct(ss[4].tv)} TV time." to "It nearly matches exact counting without needing states to repeat, and avoids the pixel model’s TV trap.", lg)
                }
            }
        }
    }
}

// ── 61f Minimax ──

private class TCount(val v: Double, val n: Int)

private fun tCount(b: List<String>, ab: Boolean, al0: Double, be0: Double): TCount {
    val w = RbTtt.win(b)
    if (w != null) return TCount(RbTtt.score(w), 1)
    var al = al0
    var be = be0
    val x = RbTtt.toMove(b) == "X"
    var v = if (x) -2.0 else 2.0
    var n = 1
    for (m in RbTtt.moves(b)) {
        val o = tCount(RbTtt.play(b, m), ab, al, be)
        n += o.n
        if (x) { v = max(v, o.v); al = max(al, v) } else { v = min(v, o.v); be = min(be, v) }
        if (ab && al >= be) break
    }
    return TCount(v, n)
}

private fun minimaxLab(): RbLab {
    val lg = listOf("#3b82f6" to "X better", "#e5337a" to "O wins", "#22a06b" to "Best move")
    fun vl(v: Double) = if (v > 0) "X wins" else if (v < 0) "O wins" else "draw"
    return RbLab(listOf("minimax", "alpha-beta"), 0) { opt ->
        val b = RbTtt.TB
        val tb = RbTtt.best(b)
        val vals = tb.vals
        val best = tb.best
        val ab = opt == 1
        List(5) { stp ->
            when (stp) {
                0 -> frame(listOf(RbTtt.grid(b, null, null)), listOf(F("X to move ·", "${vals.size} legal moves")),
                    "Minimax assumes both sides play perfectly." to "X picks the move with the highest value; O, replying, picks the lowest. +1 is a win for X, 0 a draw, −1 a loss.", lg)
                1 -> {
                    val m0 = vals.first { it.second < 0 }.first
                    val c = b.toMutableList().also { it[m0] = "X" }
                    val reps = RbTtt.moves(c).map { m -> m to RbTtt.minimax(c.toMutableList().also { it[m] = "O" }) }
                    val r = reps.reduce { a, x -> if (x.second < a.second) x else a }
                    frame(listOf(
                        C(listOf(tok("X: sq ${m0 + 1}", "plain"), tok("O: sq ${r.first + 1}", "err"), tok(vl(r.second), "err"))),
                        L("O’s replies after X plays sq ${m0 + 1}"),
                        B(reps.map { (m, v) -> brow("sq ${m + 1}", v, 1.0, vl(v), if (m == r.first) "#ff8a8d" else null) }),
                    ), listOf(F("value(sq ${m0 + 1}) = min over O’s replies =", f(r.second, 0), "#e5337a")),
                        "Playing sq ${m0 + 1} loses." to "O answers with sq ${r.first + 1} and wins, so the move is worth −1 no matter what else follows.", lg)
                }
                2 -> {
                    val n = tCount(b, ab, -2.0, 2.0).n
                    frame(listOf(RbTtt.grid(b, null, best), L("minimax value per move"), B(vals.map { (m, v) -> brow("sq ${m + 1}", v, 1.0, vl(v), if (m in best) "#5fd49b" else null) })),
                        listOf(F("best =", "sq " + best.joinToString(", ") { "${it + 1}" } + " · value " + f(vals.maxOf { it.second }, 0), "#22a06b"), F((if (ab) "alpha-beta" else "minimax") + " searched", "$n nodes")),
                        "Every legal move, scored by perfect play." to (if (best.size == 1) "Only sq ${best[0] + 1} holds the draw; every other move lets O win." else "Squares ${best.joinToString(", ") { "${it + 1}" }} tie for best."), lg)
                }
                3 -> {
                    val p = tCount(b, false, -2.0, 2.0)
                    val a = tCount(b, true, -2.0, 2.0)
                    frame(listOf(L("positions examined to value the root"), B(listOf(
                        row("minimax", "${p.n}", 1.0, if (ab) "#3a3f4c" else "#3b82f6", if (ab) "#9aa0ae" else "#f2f3f7"),
                        row("alpha-beta", "${a.n}", a.n.toDouble() / p.n, if (ab) "#22a06b" else "#3a3f4c", if (ab) "#f2f3f7" else "#9aa0ae"),
                    ))), listOf(F("same root value ·", f(p.v, 0) + " = " + f(a.v, 0), "#22a06b"), F("saved =", pct(1 - a.n.toDouble() / p.n))),
                        "Alpha-beta reaches the same answer after ${a.n} positions instead of ${p.n}." to "It skips branches that cannot change the decision — on chess-sized trees, the difference between feasible and not.", lg)
                }
                else -> {
                    val c = b.toMutableList().also { it[best[0]] = "X" }
                    frame(listOf(RbTtt.grid(c, null, listOf(best[0])), S(st("X plays", "sq ${best[0] + 1}", "#22a06b"), st("value", "0 · draw with perfect play"))),
                        listOf(F("V(s) = max_a min_b V(s′′)")),
                        "Blocking keeps the game level." to "From here neither side can force a win; the tree’s value is the result of perfect play by both.", lg)
                }
            }
        }
    }
}

// ── 61g Independent Q-learning on the penalty game ──

private val PEN = listOf(listOf(8.0, -12.0, -12.0), listOf(-12.0, 0.0, 0.0), listOf(-12.0, 0.0, 0.0))
private val AL = listOf("A0", "A1", "A2")
private val BL = listOf("B0", "B1", "B2")

/** A payoff grid: blue positive, pink negative, ringed optimum cells. */
private fun pgrid(m: List<List<Double>>, rl: List<String>, cl: List<String>, opt: List<Pair<Int, Int>>?, ch: Int = 34, dp: Int = 1): RbBlock.Grid {
    val sc = m.flatten().maxOf { abs(it) }.let { if (it == 0.0) 1.0 else it }
    return RbBlock.Grid(ch, cl, m.mapIndexed { i, row ->
        RbGRow(rl[i], "#9aa0ae", row.mapIndexed { j, v ->
            val a = Rb.a2(.15 + .7 * abs(v) / sc)
            RbCell(f(v, dp), if (abs(v) < 1e-9) "#1f232d" else if (v > 0) "rgba(59,130,246,$a)" else "rgba(229,51,122,$a)", "#fff",
                if (opt != null && opt.any { it.first == i && it.second == j }) "inset 0 0 0 2px #22a06b" else "none")
        })
    })
}

private class Iql(val snaps: Map<Int, Pair<List<Double>, List<Double>>>, val joint: Pair<Int, Int>)

private val iqCache = HashMap<String, Iql>()

private fun iql(eps: Double, seed: Int): Iql = synchronized(iqCache) {
    iqCache.getOrPut("${eps}_$seed") {
        val r = Rb.rng(seed * 31L + 7)
        val q1 = DoubleArray(3)
        val q2 = DoubleArray(3)
        val snaps = HashMap<Int, Pair<List<Double>, List<Double>>>()
        fun am(q: DoubleArray): Int { var b = 0; for (i in 1 until 3) if (q[i] > q[b] + 1e-12) b = i; return b }
        for (t in 1..500) {
            val a = if (r() < eps) floor(r() * 3).toInt() else am(q1)
            val b = if (r() < eps) floor(r() * 3).toInt() else am(q2)
            val rw = PEN[a][b]
            q1[a] += .1 * (rw - q1[a])
            q2[b] += .1 * (rw - q2[b])
            if (t == 10 || t == 500) snaps[t] = q1.toList() to q2.toList()
        }
        Iql(snaps, am(q1) to am(q2))
    }
}

private fun iqlLab(): RbLab {
    val lg = listOf("#3b82f6" to "Positive", "#e5337a" to "Penalty", "#22a06b" to "Optimum")
    return RbLab(listOf("ε 0.05", "ε 0.2", "ε 0.5"), 1) { opt ->
        val eps = listOf(.05, .2, .5)[opt]
        List(5) { stp ->
            when {
                stp == 0 -> frame(listOf(L("team payoff"), pgrid(PEN, AL, BL, listOf(0 to 0))), listOf(F("optimum =", "8 at (A0, B0)", "#22a06b")),
                    "Two agents, three actions each, one shared payoff." to "(A0, B0) pays 8, but either agent playing its half while the partner does anything else loses 12.", lg)
                stp < 3 -> {
                    val t = if (stp == 1) 10 else 500
                    val o = iql(eps, 1)
                    val (sq1, sq2) = o.snaps.getValue(t)
                    val blocks = listOf(L("Q₁ · agent A after $t rounds"), B(sq1.mapIndexed { i, v -> brow(AL[i], v, 12.0) }), L("Q₂ · agent B"), B(sq2.mapIndexed { i, v -> brow(BL[i], v, 12.0) }))
                    if (stp == 1) {
                        frame(blocks, listOf(F("Q(a) ← Q(a) + 0.1·(r − Q(a))"), F("Q₁(A0) =", f(sq1[0]), if (sq1[0] < 0) "#e5337a" else "#3b82f6")),
                            "Each agent learns as if the other were the weather." to "After 10 rounds A0 is worth ${f(sq1[0])} to agent A: the partner’s exploration keeps punishing it.", lg)
                    } else {
                        val (a, b) = o.joint
                        val v = PEN[a][b]
                        val best = a == 0 && b == 0
                        frame(blocks, listOf(F("greedy joint action =", "(${AL[a]}, ${BL[b]}) → ${f(v, 0)}", if (best) "#22a06b" else "#e5337a")),
                            if (best) "This run found the optimum." to "Both agents happened to commit to their half at the same time."
                            else "They settle on (${AL[a]}, ${BL[b]}), worth ${f(v, 0)} instead of 8." to "Each agent’s best reply to the other is safe and mediocre — neither can move to the optimum alone.", lg)
                    }
                }
                stp == 3 -> {
                    val ex = (0..2).map { PEN[it].sum() / 3 }
                    frame(listOf(L("E[r] per action if the partner is uniform"), B(ex.mapIndexed { i, v -> brow(AL[i], v, 12.0) })),
                        listOf(F("E[r | A0] = (8 − 12 − 12) / 3 =", f(ex[0]), "#e5337a"), F("E[r | A1] = (−12 + 0 + 0) / 3 =", f(ex[1]))),
                        "The safe actions look better on average." to "While the partner explores, A0 averages ${f(ex[0])} and A1 or A2 average ${f(ex[1])}. Both agents retreat: relative over-generalisation.", lg)
                }
                else -> {
                    val cnt = LinkedHashMap<String, Int>()
                    for (k in 1..50) {
                        val j = iql(eps, k).joint.let { "${it.first},${it.second}" }
                        cnt[j] = (cnt[j] ?: 0) + 1
                    }
                    val ks = cnt.keys.sortedByDescending { cnt.getValue(it) }
                    val n00 = cnt["0,0"] ?: 0
                    frame(listOf(L("greedy joint action after 500 rounds · 50 runs"), B(ks.map { k ->
                        val (a, b) = k.split(",").map { it.toInt() }
                        val opt0 = a == 0 && b == 0
                        row("(${AL[a]}, ${BL[b]})", "${cnt.getValue(k)} · r ${f(PEN[a][b], 0)}", cnt.getValue(k) / 50.0, if (opt0) "#22a06b" else "#9aa0ae", if (opt0) "#5fd49b" else null)
                    })), listOf(F("optimum found in", "$n00 / 50 runs", if (n00 > 25) "#22a06b" else "#e5337a")),
                        "Independent learners find the optimum in $n00 of 50 runs." to "The rest lock into a safe zero. Coordination needs a learner that sees the joint action — VDN, QMIX, centralised critics.", lg)
                }
            }
        }
    }
}

// ── 61h VDN and 61i QMIX ──

private val GAMES = mapOf(
    "additive" to listOf(listOf(5.0, 3.0, 1.0), listOf(3.0, 1.0, -1.0), listOf(1.0, -1.0, -3.0)),
    "product" to listOf(listOf(9.0, 6.0, 3.0), listOf(6.0, 4.0, 2.0), listOf(3.0, 2.0, 1.0)),
    "penalty" to PEN,
)

private class Vdn(val q1: List<Double>, val q2: List<Double>, val fit: List<List<Double>>, val mse: Double)

private fun vdnFit(m: List<List<Double>>): Vdn {
    val rm = m.map { it.sum() / 3 }
    val cm = (0..2).map { j -> (m[0][j] + m[1][j] + m[2][j]) / 3 }
    val g = rm.sum() / 3
    val q1 = rm.map { it - g / 2 }
    val q2 = cm.map { it - g / 2 }
    val fit = m.indices.map { i -> (0..2).map { j -> q1[i] + q2[j] } }
    var mse = 0.0
    m.forEachIndexed { i, r -> r.forEachIndexed { j, v -> mse += (v - fit[i][j]).let { it * it } / 9 } }
    return Vdn(q1, q2, fit, mse)
}

private class Qmix(val fit: List<List<Double>>, val mse: Double)

private val qmCache = HashMap<String, Qmix>()

private fun sgn1(x: Double) = if (x == 0.0) 1.0 else sign(x)

private fun qmixFit(name: String): Qmix = synchronized(qmCache) {
    qmCache.getOrPut(name) {
        val m = GAMES.getValue(name)
        val sc = m.flatten().maxOf { abs(it) }
        val k = 6
        var best: Qmix? = null
        var seed = 1
        while (seed <= 3 && !(best != null && best.mse < .01)) {
            val r = Rb.rng(seed.toLong())
            val q1 = DoubleArray(3) { r() - .5 }
            val q2 = DoubleArray(3) { r() - .5 }
            val u1 = DoubleArray(k) { r() }
            val u2 = DoubleArray(k) { r() }
            val c = DoubleArray(k) { r() - .5 }
            val w = DoubleArray(k) { r() }
            var d = 0.0
            val hh = DoubleArray(k)
            fun fwd(i: Int, j: Int): Double {
                var o = d
                for (x in 0 until k) {
                    val z = abs(u1[x]) * q1[i] + abs(u2[x]) * q2[j] + c[x]
                    hh[x] = z
                    o += abs(w[x]) * max(0.0, z)
                }
                return o
            }
            repeat(6000) {
                val gq1 = DoubleArray(3)
                val gq2 = DoubleArray(3)
                val gu1 = DoubleArray(k)
                val gu2 = DoubleArray(k)
                val gc = DoubleArray(k)
                val gw = DoubleArray(k)
                var gd = 0.0
                for (i in 0 until 3) for (j in 0 until 3) {
                    val o = fwd(i, j)
                    val e = 2 * (o - m[i][j] / sc) / 9
                    gd += e
                    for (x in 0 until k) {
                        if (hh[x] <= 0) continue
                        val aw = abs(w[x])
                        gw[x] += e * sgn1(w[x]) * hh[x]
                        val eh = e * aw
                        gc[x] += eh
                        gu1[x] += eh * sgn1(u1[x]) * q1[i]
                        gu2[x] += eh * sgn1(u2[x]) * q2[j]
                        gq1[i] += eh * abs(u1[x])
                        gq2[j] += eh * abs(u2[x])
                    }
                }
                for (x in 0 until 3) { q1[x] -= .1 * gq1[x]; q2[x] -= .1 * gq2[x] }
                for (x in 0 until k) { u1[x] -= .1 * gu1[x]; u2[x] -= .1 * gu2[x]; c[x] -= .1 * gc[x]; w[x] -= .1 * gw[x] }
                d -= .1 * gd
            }
            val fit = (0..2).map { i -> (0..2).map { j -> fwd(i, j) * sc } }
            var mse = 0.0
            m.forEachIndexed { i, rr -> rr.forEachIndexed { j, v -> mse += (v - fit[i][j]).let { it * it } / 9 } }
            if (best == null || mse < best.mse) best = Qmix(fit, mse)
            seed++
        }
        best!!
    }
}

private fun argmax2(fm: List<List<Double>>): Pair<Int, Int> {
    var bi = 0
    var bj = 0
    fm.forEachIndexed { i, r -> r.forEachIndexed { j, v -> if (v > fm[bi][bj] + 1e-9) { bi = i; bj = j } } }
    return bi to bj
}

private fun vdnLab(): RbLab {
    val lg = listOf("#3b82f6" to "Value", "#e5337a" to "Negative / miss", "#22a06b" to "Optimum")
    return RbLab(listOf("additive", "product", "penalty"), 2) { opt ->
        val gn = listOf("additive", "product", "penalty")[opt]
        val m = GAMES.getValue(gn)
        val v = vdnFit(m)
        val o = argmax2(m)
        List(4) { stp ->
            when (stp) {
                0 -> frame(listOf(L("team payoff · $gn game"), pgrid(m, AL, BL, listOf(o))),
                    listOf(F("optimum =", f(m[o.first][o.second], 0) + " at (${AL[o.first]}, ${BL[o.second]})", "#22a06b")),
                    "VDN trains centrally and executes locally." to "One team reward trains everything; at run time each agent just takes its own argmax.", lg)
                1 -> frame(listOf(L("per-agent utilities fitted to the team reward"), B(v.q1.mapIndexed { i, x -> brow("Q₁(${AL[i]})", x, 8.0) } + v.q2.mapIndexed { i, x -> brow("Q₂(${BL[i]})", x, 8.0) })),
                    listOf(F("Q_tot(a₁, a₂) = Q₁(a₁) + Q₂(a₂)")),
                    "Each agent keeps its own small table." to "The tables are fitted so their sum matches the team reward as closely as a sum can.", lg)
                2 -> frame(listOf(L("VDN’s Q_tot = Q₁ + Q₂"), pgrid(v.fit, AL, BL, listOf(argmax2(v.fit)))),
                    listOf(F("fit error (MSE) =", f(v.mse, 3), if (v.mse < 1e-6) "#22a06b" else "#e5337a")),
                    if (v.mse < 1e-6) "An additive payoff is matched exactly." to "Every cell is a row value plus a column value, which is precisely what VDN can represent."
                    else "A sum cannot represent this payoff." to "The best additive fit still misses by MSE ${f(v.mse, 3)}.", lg)
                else -> {
                    val i = v.q1.indexOf(v.q1.max())
                    val j = v.q2.indexOf(v.q2.max())
                    val got = m[i][j]
                    val bestv = m[o.first][o.second]
                    val hit = got == bestv
                    frame(listOf(S(
                        st("VDN plays", "(${AL[i]}, ${BL[j]}) → ${f(got, 0)}", if (hit) "#22a06b" else "#e5337a"),
                        st("true optimum", "(${AL[o.first]}, ${BL[o.second]}) → ${f(bestv, 0)}"),
                        st("regret", f(bestv - got, 0), if (hit) "#22a06b" else "#e5337a"),
                    )), listOf(F("each agent: argmax Qᵢ, no communication")),
                        if (hit) "Local argmaxes give the team optimum." to "Even with a fit error, the ranking survives here."
                        else "Local argmaxes miss the optimum by ${f(bestv - got, 0)}." to "The −12 penalties drag A0 and B0 down in the averaged fit — the coordination problem is back.", lg)
                }
            }
        }
    }
}

private fun qmixLab(): RbLab {
    val lg = listOf("#3b82f6" to "QMIX", "#9aa0ae" to "VDN", "#22a06b" to "Optimum")
    return RbLab(listOf("product", "penalty"), 0) { opt ->
        val gn = listOf("product", "penalty")[opt]
        val m = GAMES.getValue(gn)
        val v = vdnFit(m)
        val qm = qmixFit(gn)
        val o = argmax2(m)
        List(4) { stp ->
            when (stp) {
                0 -> frame(listOf(L("team payoff · $gn game"), pgrid(m, AL, BL, listOf(o)), S(st("mixer", "6 ReLU units, weights forced ≥ 0"), st("guarantee", "argmax Q_tot = per-agent argmaxes"))),
                    listOf(F("Q_tot = mix(Q₁, Q₂),  ∂Q_tot/∂Qᵢ ≥ 0")),
                    if (gn == "product") "This payoff is a product, not a sum." to "Monotone in each agent’s contribution, but with an interaction a sum cannot capture."
                    else "The penalty game is not monotone." to "Whether A0 is good depends on what B plays — the hard case for any decomposition.", lg)
                1 -> frame(listOf(L("VDN’s best sum"), pgrid(v.fit, AL, BL, listOf(argmax2(v.fit)))), listOf(F("VDN fit error (MSE) =", f(v.mse, 3), "#9aa0ae")),
                    "VDN’s best additive fit." to "Least squares, solved exactly: MSE ${f(v.mse, 3)}.", lg)
                2 -> {
                    val oq = argmax2(qm.fit)
                    frame(listOf(L("QMIX’s monotone mixer · trained 6,000 steps"), pgrid(qm.fit, AL, BL, listOf(oq))),
                        listOf(F("MSE ·", f(v.mse, 3) + " → " + f(qm.mse, 3), if (qm.mse < v.mse / 4) "#22a06b" else "#e5337a")),
                        if (gn == "product") "The mixer bends the sum into a product." to "MSE drops from ${f(v.mse, 3)} to ${f(qm.mse, 3)}. A sum is one monotone mixer, so QMIX can only match or beat VDN."
                        else "QMIX can’t fit the penalty game either." to "MSE ${f(v.mse, 1)} → ${f(qm.mse, 1)}; its greedy pick is (${AL[oq.first]}, ${BL[oq.second]}), worth ${f(m[oq.first][oq.second], 0)}.", lg)
                }
                else -> {
                    val rows = ArrayList<RbRow>()
                    listOf("product", "penalty").forEach { g ->
                        val vg = vdnFit(GAMES.getValue(g))
                        val qg = qmixFit(g)
                        val mg = GAMES.getValue(g)
                        fun ok(fm: List<List<Double>>): Boolean {
                            val (i, j) = argmax2(fm)
                            val (a, b) = argmax2(mg)
                            return mg[i][j] == mg[a][b]
                        }
                        rows += row("$g · VDN", f(vg.mse, 3) + if (ok(vg.fit)) " ✓" else " ✗", min(1.0, sqrt(vg.mse) / 8), "#9aa0ae", if (g == gn) "#f2f3f7" else "#9aa0ae")
                        rows += row("$g · QMIX", f(qg.mse, 3) + if (ok(qg.fit)) " ✓" else " ✗", min(1.0, sqrt(qg.mse) / 8), "#3b82f6", if (g == gn) "#f2f3f7" else "#9aa0ae")
                    }
                    frame(listOf(L("fit error · ✓ = greedy pick is the true optimum"), B(rows)), listOf(F("monotone mixing ⊃ sums ⊂ all payoffs")),
                        "Monotone helps; it isn’t everything." to "QMIX nails the product game. In the penalty game the optimum needs non-monotone mixing — the gap QTRAN and QPLEX target.", lg)
                }
            }
        }
    }
}

// ── 61j MADDPG ──

private fun mr(a: Double, b: Double) = 1.6 * a * b + a + b - a * a - b * b - .5

/** Least squares via the normal equations and Gauss–Jordan with partial pivoting, as the design's lsq(). */
internal fun rbLsq(a: List<DoubleArray>, y: List<Double>): DoubleArray {
    val n = a[0].size
    val m = Array(n) { i -> DoubleArray(n + 1) { j -> var s = 0.0; a.forEachIndexed { k, row -> s += row[i] * (if (j < n) row[j] else y[k]) }; s } }
    for (i in 0 until n) {
        var p = i
        for (k in i + 1 until n) if (abs(m[k][i]) > abs(m[p][i])) p = k
        val tmp = m[i]; m[i] = m[p]; m[p] = tmp
        for (k in 0 until n) {
            if (k == i) continue
            val q = m[k][i] / m[i][i]
            for (j in i..n) m[k][j] -= q * m[i][j]
        }
    }
    return DoubleArray(n) { m[it][n] / m[it][it] }
}

private class Ddpg(val tr: List<Pair<Double, Double>>, val g: List<Pair<Double, Double>>)

private val dpCache = HashMap<String, Ddpg>()

private fun ddpg(cent: Boolean, sig: Double, seed: Int): Ddpg = synchronized(dpCache) {
    dpCache.getOrPut("${cent}_${sig}_$seed") {
        val r = Rb.rng(seed * 7L + 3)
        var m0 = -.8
        var m1 = .6
        val tr = arrayListOf(m0 to m1)
        val gs = ArrayList<Pair<Double, Double>>()
        val xs = ArrayList<DoubleArray>()
        val ys = ArrayList<Double>()
        repeat(15) {
            repeat(8) {
                val a0 = m0 + sig * Rb.gauss(r)
                val a1 = m1 + sig * Rb.gauss(r)
                xs += doubleArrayOf(a0, a1)
                ys += mr(a0, a1)
            }
            val from = max(0, xs.size - 160)
            val xw = xs.subList(from, xs.size)
            val yw = ys.subList(from, ys.size)
            val g = if (cent) {
                val c = rbLsq(xw.map { doubleArrayOf(1.0, it[0], it[1], it[0] * it[0], it[1] * it[1], it[0] * it[1]) }, yw)
                doubleArrayOf(c[1] + 2 * c[3] * m0 + c[5] * m1, c[2] + 2 * c[4] * m1 + c[5] * m0)
            } else {
                val mm = doubleArrayOf(m0, m1)
                DoubleArray(2) { i -> val c = rbLsq(xw.map { doubleArrayOf(1.0, it[i], it[i] * it[i]) }, yw); c[1] + 2 * c[2] * mm[i] }
            }
            gs += g[0] to (1.6 * m1 + 1 - 2 * m0)
            m0 = max(-1.0, min(1.0, m0 + .25 * g[0]))
            m1 = max(-1.0, min(1.0, m1 + .25 * g[1]))
            tr += m0 to m1
        }
        Ddpg(tr, gs)
    }
}

private fun maddpgLab(): RbLab {
    val lg = listOf("#3b82f6" to "Centralised", "#9aa0ae" to "Independent", "#e5337a" to "Drift")
    val ax = listOf(-1.0, -.5, 0.0, .5, 1.0)
    fun dist(t: Pair<Double, Double>) = hypot(1 - t.first, 1 - t.second)
    return RbLab(listOf("σ 0.1", "σ 0.3", "σ 0.6"), 1) { opt ->
        val sig = listOf(.1, .3, .6)[opt]
        fun mean(cent: Boolean, u: Int): Double { var d = 0.0; for (k in 1..30) d += dist(ddpg(cent, sig, k).tr[u]) / 30; return d }
        List(5) { stp ->
            when (stp) {
                0 -> frame(listOf(L("reward over the joint action (a₁ down, a₂ across)"), pgrid(ax.map { a -> ax.map { b -> mr(a, b) } }, ax.map { f(it, 1) }, ax.map { f(it, 1) }, listOf(4 to 4), 28, 2)),
                    listOf(F("r = 1.6·a₁a₂ + a₁ + a₂ − a₁² − a₂² − 0.5"), F("optimum =", "1.10 at (1, 1)", "#22a06b")),
                    "A continuous task: both agents pick a real number." to "The reward couples them through the cross term 1.6·a₁a₂, so each agent’s best action depends on the other’s.", lg)
                1 -> {
                    val i = ddpg(false, sig, 1).g[9]
                    val cn = ddpg(true, sig, 1).g[9]
                    frame(listOf(S(st("independent critic", "Q₁(a₁) · partner hidden in the noise", "#e5337a"), st("centralised critic", "Q(a₁, a₂) · sees both actions", "#3b82f6"), st("replay buffer", "last 160 samples, old partner policies"), st("start", "(−0.8, 0.6)"))),
                        listOf(F("update 10 · independent ∂Q/∂a₁ = ${f(i.first)} · true", f(i.second), "#e5337a"), F("update 10 · centralised ∂Q/∂a₁ = ${f(cn.first)} · true", f(cn.second), "#3b82f6")),
                        "The partner keeps changing, so the replay buffer goes stale." to "An independent critic blends old partner behaviour into its gradient; a critic that sees both actions is unaffected.", lg)
                }
                2 -> frame(listOf(L("mean distance to the optimum · 30 runs"), RbBlock.Cols(listOf(1, 3, 5, 10, 15).map { u ->
                    val i = mean(false, u)
                    val c = mean(true, u)
                    RbVCol(f(i), "upd $u", "#9aa0ae", listOf(RbVBar(min(100.0, i / 2.1 * 100) * .62, "#9aa0ae"), RbVBar(min(100.0, c / 2.1 * 100) * .62, "#3b82f6")))
                })), listOf(F("update 15 · independent ${f(mean(false, 15))} vs centralised", f(mean(true, 15)), "#3b82f6")),
                    "The centralised critic walks straight to (1, 1)." to "With exploration noise σ = ${js(sig)}, independent critics are still ${f(mean(false, 15))} away after 15 updates.", lg)
                3 -> {
                    val rows = ArrayList<RbRow>()
                    listOf(.1, .3, .6).forEach { x ->
                        var ri = 0.0
                        var rc = 0.0
                        for (k in 1..30) {
                            ddpg(false, x, k).tr[15].let { ri += mr(it.first, it.second) / 30 }
                            ddpg(true, x, k).tr[15].let { rc += mr(it.first, it.second) / 30 }
                        }
                        rows += row("σ ${js(x)} · indep.", f(ri), max(0.0, (ri + 4) / 5.1), "#9aa0ae", if (x == sig) "#f2f3f7" else "#9aa0ae")
                        rows += row("σ ${js(x)} · central", f(rc), max(0.0, (rc + 4) / 5.1), "#3b82f6", if (x == sig) "#f2f3f7" else "#9aa0ae")
                    }
                    frame(listOf(L("reward after 15 updates · max 1.10"), B(rows)), listOf(F("independent learners improve with more noise — but never catch up")),
                        "Centralised critics reach 1.10 at every noise level." to "More exploration helps independent critics refresh their view of the partner, but stale data still costs reward.", lg)
                }
                else -> frame(listOf(S(st("training", "critic Qᵢ(s, a₁, a₂) per agent"), st("execution", "actor μᵢ(oᵢ) — own observation only", "#22a06b"), st("communication at run time", "none"))),
                    listOf(F("∇θᵢ J = E[∇θᵢ μᵢ(oᵢ) · ∇aᵢ Qᵢ(s, a₁, a₂)]")),
                    "Centralised training, decentralised execution." to "The critic is thrown away after training; each actor acts on its own observation.", lg)
            }
        }
    }
}

// ── 61k Self-Play: fictitious play on rock-paper-scissors ──

private val RPS = mapOf(
    "standard" to listOf(listOf(0.0, -1.0, 1.0), listOf(1.0, 0.0, -1.0), listOf(-1.0, 1.0, 0.0)),
    "rock ×2" to listOf(listOf(0.0, -1.0, 2.0), listOf(1.0, 0.0, -1.0), listOf(-2.0, 1.0, 0.0)),
)

private class FpSnap(val fr: List<Double>, val expl: Double, val last: List<Int>)

private val fpCache = HashMap<String, Map<Int, FpSnap>>()

private fun fp(g: String): Map<Int, FpSnap> = synchronized(fpCache) {
    fpCache.getOrPut(g) {
        val a = RPS.getValue(g)
        val c1 = doubleArrayOf(1.0, 0.0, 0.0)
        val c2 = doubleArrayOf(0.0, 1.0, 0.0)
        val snaps = HashMap<Int, FpSnap>()
        val hist = ArrayList<Int>()
        fun v(i: Int, c: List<Double>) = c.indices.sumOf { c[it] * a[i][it] }
        fun br(c: List<Double>): Int { var b = 0; for (i in 1 until 3) if (v(i, c) > v(b, c) + 1e-12) b = i; return b }
        for (t in 1..2000) {
            val x = br(c2.toList())
            val y = br(c1.toList())
            c1[x]++
            c2[y]++
            hist += x
            if (t in listOf(10, 40, 300, 2000)) {
                val tot = c1.sum()
                val fr = c1.map { it / tot }
                snaps[t] = FpSnap(fr, (0..2).maxOf { v(it, fr) }, hist.takeLast(6))
            }
        }
        snaps
    }
}

private fun selfPlayLab(): RbLab {
    val lg = listOf("#3b82f6" to "Frequency", "#22a06b" to "Near Nash", "#e5337a" to "Exploitable")
    val nm = listOf("rock", "paper", "scissors")
    return RbLab(listOf("standard", "rock ×2"), 1) { opt ->
        val g = listOf("standard", "rock ×2")[opt]
        val sn = fp(g)
        val ne = if (g == "standard") listOf(1.0 / 3, 1.0 / 3, 1.0 / 3) else listOf(.25, .5, .25)
        List(5) { stp ->
            when {
                stp < 3 -> {
                    val t = listOf(10, 40, 300)[stp]
                    val o = sn.getValue(t)
                    frame(listOf(
                        L("last 5 plays"), C(o.last.takeLast(5).map { tok(nm[it], "plain") }),
                        L("play frequency after $t rounds"), B(o.fr.mapIndexed { i, x -> row(nm[i], pct(x), x / .6, if (abs(x - ne[i]) < .03) "#22a06b" else "#3b82f6") }),
                    ), listOf(F("Nash =", ne.joinToString(" / ") { pct(it) }), F("exploitability =", f(o.expl, 3), if (o.expl < .05) "#22a06b" else "#e5337a")),
                        when (stp) {
                            0 -> "Self-play: each side best-responds to the other’s history." to "Early frequencies swing as each side chases the other’s last habit; ${if (g == "standard") "" else "here rock beats scissors for 2, "}exploitability is ${f(o.expl, 2)}."
                            1 -> "The averages start to settle." to "Exploitability is down to ${f(o.expl, 3)} after 40 rounds."
                            else -> "After $t rounds the average is almost unexploitable." to (if (g == "standard") "It sits near ⅓ each — the Nash equilibrium nobody programmed in." else "Paper rises toward 50%: doubling rock’s win makes paper, rock’s counter, the equilibrium favourite.")
                        }, lg)
                }
                stp == 3 -> {
                    val ts = listOf(10, 40, 300, 2000)
                    val e10 = sn.getValue(10).expl
                    frame(listOf(L("exploitability of the average strategy"), B(ts.map { t ->
                        val e = sn.getValue(t).expl
                        row("$t rounds", f(e, 3), e / max(e10, .01), if (e < .05) "#22a06b" else "#e5337a")
                    })), listOf(F("exploitability = max_a (A·π̄)_a,  0 at Nash")),
                        "Fictitious play converges in two-player zero-sum games." to "From ${f(e10, 3)} to ${f(sn.getValue(2000).expl, 3)} with no external opponent — the curriculum is entirely internal.", lg)
                }
                else -> frame(listOf(S(st("current policy", "always a pure best response", "#e5337a"), st("average policy", "converges to Nash", "#22a06b"), st("at scale", "AlphaZero, OpenAI Five, AlphaStar league"))),
                    listOf(F("Nash · " + ne.joinToString(" / ") { pct(it) })),
                    "The average is what converges." to "The latest best response is always exploitable; keeping old versions around (a league) is how large systems use the same idea.", lg)
            }
        }
    }
}
