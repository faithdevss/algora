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
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

// ── Imitation, offline and meta-RL storyboard frames ─────────────────────────
// One slippery corridor for behaviour cloning, DAgger, MaxEnt IRL and tabular GAIL, all with exact
// occupancies; a logged chain with a zero-value lottery for fitted Q-iteration and CQL; a
// return-conditioned policy on mixed data; and a meta-learned start for a family of goals. Drawn by
// RlBoardLabs.kt; the iOS port (RlFrontierFrames.swift) matches it.

internal val rlFrontierTopicIds = setOf("imitation_learning", "irl", "gail", "offline_rl", "cql", "decision_transformer", "meta_rl")

internal fun rlFrontierLab(topicId: String): RbLab? = when (topicId) {
    "imitation_learning" -> imitationLab()
    "irl" -> irlLab()
    "gail" -> gailLab()
    "offline_rl" -> offlineLab()
    "cql" -> cqlLab()
    "decision_transformer" -> dtLab()
    "meta_rl" -> metaLab()
    else -> null
}

// ── The corridor: 3 × 8, start cell 8 (r1 c0), goal 15 (r1 c7); a move slips up or down with p ──

private fun cmv(i: Int, a: Int): Int {
    val r = i / 8 + RbGw.AC[a][0]
    val c = i % 8 + RbGw.AC[a][1]
    return if (r < 0 || r > 2 || c < 0 || c > 7) i else r * 8 + c
}

private val ctrCache = HashMap<Double, Array<Array<List<Pair<Int, Double>>>>>()

/** Next-cell distribution for (i, a) at slip p, targets ascending as the design's Object.entries gives them. */
private fun ctr(p: Double): Array<Array<List<Pair<Int, Double>>>> = synchronized(ctrCache) {
    ctrCache.getOrPut(p) {
        Array(24) { i ->
            Array(4) { a ->
                if (i == 15) listOf(15 to 1.0) else {
                    val o = LinkedHashMap<Int, Double>()
                    fun add(j: Int, w: Double) { o[j] = (o[j] ?: 0.0) + w }
                    add(cmv(i, a), 1 - p)
                    add(cmv(i, 0), p / 2)
                    add(cmv(i, 1), p / 2)
                    o.entries.sortedBy { it.key }.map { it.key to it.value }
                }
            }
        }
    }
}

/** The expert: down from the top row, up from the bottom row, right along the middle. */
private fun cExp(i: Int): Int = when (i / 8) { 0 -> 1; 2 -> 0; else -> 3 }

private fun one(a: Int) = DoubleArray(4) { if (it == a) 1.0 else 0.0 }

private class Occ(val sv: DoubleArray, val rho: Array<DoubleArray>, val succ: Double, val off: List<Double>)

private fun cocc(pi: (Int) -> DoubleArray, p: Double, start: Int = 8): Occ {
    val t = ctr(p)
    var d = DoubleArray(24)
    d[start] = 1.0
    val sv = DoubleArray(24)
    val rho = Array(24) { DoubleArray(4) }
    val off = ArrayList<Double>()
    repeat(15) {
        val n = DoubleArray(24)
        for (i in 0 until 24) {
            if (d[i] == 0.0) continue
            sv[i] += d[i]
            if (i == 15) { n[15] += d[i]; continue }
            val pr = pi(i)
            for (a in 0 until 4) {
                if (pr[a] == 0.0) continue
                rho[i][a] += d[i] * pr[a]
                t[i][a].forEach { (j, w) -> n[j] += d[i] * pr[a] * w }
            }
        }
        d = n
        var s = 0.0
        for (i in 0 until 24) s += if (i / 8 != 1) d[i] else 0.0
        off += s
    }
    return Occ(sv, rho, d[15], off)
}

private class CCell(val t: String? = null, val bg: String? = null, val ring: String? = null)

private fun cgrid(fn: (Int) -> CCell) = RbBlock.Grid(34, (0..7).map { "c$it" }, (0..2).map { r ->
    RbGRow("r$r", "#9aa0ae", (0..7).map { c ->
        val i = r * 8 + c
        val o = fn(i)
        RbCell(o.t ?: (if (i == 8) "S" else if (i == 15) "G" else ""), o.bg ?: "#1f232d", "#fff", o.ring ?: (if (i == 15) "inset 0 0 0 2px #22a06b" else "none"))
    })
})

private fun heat(v: Double, mx: Double, rgb: String) = if (v > 1e-3) "rgba($rgb,${Rb.a2(.12 + .8 * min(1.0, v / mx))})" else "#1f232d"

private fun maxOff15(sv: DoubleArray) = sv.indices.filter { it != 15 }.maxOf { sv[it] }

private fun occGrid(sv: DoubleArray, rgb: String): RbBlock.Grid {
    val mx = maxOff15(sv)
    return cgrid { i -> CCell(if (i == 15) "G" else (if (i == 8) "S " else "") + (if (sv[i] > .005) f(sv[i], 1) else ""), if (i == 15) "#1f232d" else heat(sv[i], mx, rgb)) }
}

private class DagIt(val cov: Int, val succ: Double)

private val dagCache = HashMap<Double, List<DagIt>>()

private fun dagger(p: Double): List<DagIt> = synchronized(dagCache) {
    dagCache.getOrPut(p) {
        val set = linkedSetOf(8, 9, 10, 11, 12, 13, 14)
        val its = ArrayList<DagIt>()
        for (k in 0..4) {
            val snap = set.toSet()
            val o = cocc({ i -> one(if (i in snap) cExp(i) else 3) }, p)
            its += DagIt(set.size, o.succ)
            o.sv.forEachIndexed { i, v -> if (v > .01 && i != 15) set += i }
        }
        its
    }
}

private val expertPi: (Int) -> DoubleArray = { one(cExp(it)) }
private val clonePi: (Int) -> DoubleArray = { one(3) }

// ── 62a Imitation Learning: BC vs DAgger ──

private fun imitationLab(): RbLab {
    val lg = listOf("#3b82f6" to "Demonstrated", "#22a06b" to "Expert / DAgger", "#e5337a" to "Off-data")
    fun demo(i: Int) = i in 8..14
    return RbLab(listOf("slip 10%", "slip 20%", "slip 30%"), 1) { opt ->
        val p = listOf(.1, .2, .3)[opt]
        val clone = cocc(clonePi, p)
        val ex = cocc(expertPi, p)
        List(5) { stp ->
            when (stp) {
                0 -> frame(listOf(L("states the demonstrations cover"), cgrid { i -> if (demo(i)) CCell(if (i == 8) "S →" else "→", "#3b82f6") else CCell(if (i == 15) "G" else "·") }),
                    listOf(F("demonstrated =", "7 of 24 states", "#3b82f6"), F("fallback off-data = majority label =", "→")),
                    "Clean demonstrations: the expert never slips." to "So the clone only ever sees the 7 states along the middle row, all labelled →.", lg)
                1 -> {
                    val mx = maxOff15(clone.sv)
                    frame(listOf(L("clone’s expected visits per cell · slip ${pct(p)}"), cgrid { i ->
                        val v = clone.sv[i]
                        CCell(if (i == 15) "G" else if (i == 8) "S" else if (v > .005) f(v, 1) else "", if (i == 15) "#1f232d" else heat(v, mx, if (demo(i)) "59,130,246" else "229,51,122"))
                    }), listOf(F("P(reach G in 15 steps) · clone", pct(clone.succ), "#e5337a"), F("expert, same slips", pct(ex.succ), "#22a06b")),
                        "One slip and the clone is lost." to "Off the middle row it has no label, so it plays → along the wrong row and pins itself against the far wall.", lg)
                }
                2 -> frame(listOf(L("probability mass off the demonstrated row, by step"), B(listOf(2, 5, 8, 11, 14).map { t -> row("step ${t + 1}", pct(clone.off[t]), clone.off[t], "#e5337a") })),
                    listOf(F("expert off-row at step 15 =", pct(ex.off[14]), "#22a06b"), F("clone off-row at step 15 =", pct(clone.off[14]), "#e5337a")),
                    "Errors compound instead of averaging out." to "Every slip moves mass into states the clone never trained on, and nothing brings it back: ${pct(clone.off[14])} is off-row by step 15.", lg)
                3 -> {
                    val d = dagger(p)
                    frame(listOf(L("DAgger: roll out, ask the expert, retrain"), B(d.mapIndexed { k, x -> row("iter $k", pct(x.succ) + " · " + x.cov, x.succ, if (k > 0) "#22a06b" else "#9aa0ae", if (k == d.lastIndex) "#f2f3f7" else null) })),
                        listOf(F("states labelled ·", "7 → ${d.last().cov}", "#22a06b"), F("success ·", pct(d[0].succ) + " → " + pct(d.last().succ), "#22a06b")),
                        "DAgger labels the states the learner actually reaches." to "After ${d.lastIndex} rounds it knows ${d.last().cov} states and matches the expert’s ${pct(ex.succ)}.", lg)
                }
                else -> {
                    val rows = ArrayList<RbRow>()
                    listOf(.1, .2, .3).forEach { x ->
                        val c = cocc(clonePi, x).succ
                        val d = dagger(x)[4].succ
                        rows += row("slip ${pct(x)} · BC", pct(c), c, "#e5337a", if (x == p) "#f2f3f7" else "#9aa0ae")
                        rows += row("slip ${pct(x)} · DAgger", pct(d), d, "#22a06b", if (x == p) "#f2f3f7" else "#9aa0ae")
                    }
                    frame(listOf(L("P(reach G in 15 steps)"), B(rows)), listOf(F("BC error grows ~ εT², DAgger ~ εT")),
                        "Coverage, not accuracy, is the problem." to "The clone is 100% accurate on every state it saw; it fails on the ones it didn’t.", lg)
                }
            }
        }
    }
}

// ── 62b IRL: MaxEnt reward recovery ──

private fun softpi(r: DoubleArray, p: Double): Array<DoubleArray> {
    val t = ctr(p)
    var v = DoubleArray(24)
    var q = Array(24) { DoubleArray(4) }
    repeat(80) {
        val vv = v
        q = Array(24) { i -> DoubleArray(4) { a -> r[i] + .9 * t[i][a].fold(0.0) { s, (j, w) -> s + w * vv[j] } } }
        v = DoubleArray(24) { i -> val m = q[i].max(); m + ln(q[i].fold(0.0) { s, x -> s + exp(x - m) }) }
    }
    return Array(24) { i -> val m = q[i].max(); val e = q[i].map { exp(it - m) }; val s = e.sum(); DoubleArray(4) { e[it] / s } }
}

private class IrlSnap(val r: DoubleArray, val mis: Double, val succ: Double)

private class Irl(val e: Occ, val sn: Map<Int, IrlSnap>, val greedy: IntArray)

private val irlCache = HashMap<Double, Irl>()

private fun irl(p: Double): Irl = synchronized(irlCache) {
    irlCache.getOrPut(p) {
        val e = cocc(expertPi, p)
        var r = DoubleArray(24)
        val sn = HashMap<Int, IrlSnap>()
        for (iter in 0..150) {
            val pi = softpi(r, p)
            val l = cocc({ pi[it] }, p)
            var mis = 0.0
            for (i in 0 until 24) mis += abs(l.sv[i] - e.sv[i])
            mis /= 15
            if (iter in listOf(0, 5, 20, 50, 150)) sn[iter] = IrlSnap(r.copyOf(), mis, l.succ)
            val rr = r
            r = DoubleArray(24) { i -> rr[i] + .2 * (e.sv[i] - l.sv[i]) }
        }
        val pi = softpi(sn.getValue(150).r, p)
        Irl(e, sn, IntArray(24) { Rb.argmax(pi[it]) })
    }
}

private fun rgrid(r: DoubleArray): RbBlock.Grid {
    val mx = r.maxOf { abs(it) }.let { if (it == 0.0) 1.0 else it }
    return cgrid { i ->
        CCell(
            (if (i == 8) "S " else if (i == 15) "G " else "") + f(r[i], 1),
            if (abs(r[i]) < .05) "#1f232d" else if (r[i] > 0) "rgba(109,93,252,${Rb.a2(.15 + .8 * r[i] / mx)})" else "rgba(229,51,122,${Rb.a2(.15 + .8 * -r[i] / mx)})",
        )
    }
}

private fun irlLab(): RbLab {
    val lg = listOf("#6d5dfc" to "Recovered reward", "#3b82f6" to "Visitation", "#e5337a" to "Mismatch / BC")
    return RbLab(listOf("slip 10%", "slip 20%", "slip 30%"), 1) { opt ->
        val p = listOf(.1, .2, .3)[opt]
        val ir = irl(p)
        List(5) { stp ->
            when {
                stp == 0 -> frame(listOf(L("expert’s expected visits per cell · slip ${pct(p)}"), occGrid(ir.e.sv, "59,130,246")), listOf(F("IRL matches this, not the actions")),
                    "IRL asks what objective makes these actions optimal." to "The only statistic it matches is expected state visitation — how long the expert spends where.", lg)
                stp < 3 -> {
                    val it = if (stp == 1) 5 else 150
                    val s = ir.sn.getValue(it)
                    frame(listOf(L("recovered reward r(s) after $it iterations"), rgrid(s.r)),
                        listOf(F("r ← r + 0.2 · (μ_expert − μ_learner)"), F("visitation mismatch =", f(s.mis, 3), if (s.mis < .1) "#22a06b" else "#e5337a")),
                        if (stp == 1) "Early on, reward is piling up where the expert lingers." to "States the learner over-visits are pushed down; the middle row and G are pushed up."
                        else "After 150 iterations the reward explains the demos." to "Its soft-optimal policy reaches G ${pct(s.succ)} of the time — the expert’s rate under the same slips.", lg)
                }
                stp == 3 -> {
                    val m0 = ir.sn.getValue(0).mis
                    frame(listOf(L("visitation mismatch ‖μ_E − μ_π‖₁ / T"), B(listOf(0, 5, 20, 50, 150).map { k -> row("iter $k", f(ir.sn.getValue(k).mis, 3), ir.sn.getValue(k).mis / m0, if (k == 150) "#22a06b" else "#6d5dfc") })),
                        listOf(F("MaxEnt gradient = expert features − learner features")),
                        "The mismatch falls from ${f(m0, 2)} to ${f(ir.sn.getValue(150).mis, 3)}." to "Matching visitation is enough to reproduce behaviour without ever copying an action.", lg)
                }
                else -> {
                    val rows = ArrayList<RbRow>()
                    listOf(0, 16).forEach { x ->
                        val lab = if (x == 0) "start r0" else "start r2"
                        val irs = cocc({ one(ir.greedy[it]) }, p, x).succ
                        val b = cocc(clonePi, p, x).succ
                        rows += row("$lab · IRL", pct(irs), irs, "#6d5dfc", "#f2f3f7")
                        rows += row("$lab · BC", pct(b), b, "#e5337a")
                    }
                    frame(listOf(L("new start cells, never demonstrated"), B(rows)), listOf(F("expert from r0 =", pct(cocc(expertPi, p, 0).succ), "#22a06b")),
                        "A reward transfers; a copied policy doesn’t." to "Started off the demonstrated row, the policy planned on the recovered reward still heads for G; the clone drifts along the wrong row.", lg)
                }
            }
        }
    }
}

// ── 62c GAIL: occupancy matching ──

private class GailSnap(val tv: Double, val succ: Double, val sv: DoubleArray, val rw: DoubleArray)

private class Gail(val e: Occ, val sn: Map<Int, GailSnap>)

private val gailCache = HashMap<Double, Gail>()

private fun gail(p: Double): Gail = synchronized(gailCache) {
    gailCache.getOrPut(p) {
        val t = ctr(p)
        val e = cocc(expertPi, p)
        var pi = Array(24) { one(3) }
        val sn = HashMap<Int, GailSnap>()
        for (iter in 0..30) {
            val cur = pi
            val pp = cocc({ cur[it] }, p)
            var tv = 0.0
            for (i in 0 until 24) for (a in 0 until 4) tv += abs(e.rho[i][a] - pp.rho[i][a]) / 15 / 2
            val rw = Array(24) { i -> DoubleArray(4) { a -> ln((e.rho[i][a] + 1e-3) / (pp.rho[i][a] + 1e-3)) } }
            if (iter in listOf(0, 1, 2, 5, 10, 30)) sn[iter] = GailSnap(tv, pp.succ, pp.sv, DoubleArray(24) { rw[it].max() })
            var v = DoubleArray(24)
            var q = Array(24) { DoubleArray(4) }
            repeat(40) {
                val vv = v
                q = Array(24) { i -> DoubleArray(4) { a -> if (i == 15) 0.0 else rw[i][a] + .95 * t[i][a].fold(0.0) { s, (j, w) -> s + w * vv[j] } } }
                v = DoubleArray(24) { i -> if (i == 15) 0.0 else ln(q[i].fold(0.0) { s, x -> s + exp(x) }) }
            }
            val np = Array(24) { i -> val m = q[i].max(); val ex = q[i].map { exp(it - m) }; val s = ex.sum(); DoubleArray(4) { ex[it] / s } }
            pi = Array(24) { i -> DoubleArray(4) { a -> .5 * cur[i][a] + .5 * np[i][a] } }
        }
        Gail(e, sn)
    }
}

private fun gailLab(): RbLab {
    val lg = listOf("#22a06b" to "Expert / GAIL", "#e5337a" to "Clone / penalty", "#6d5dfc" to "Reward")
    return RbLab(listOf("slip 10%", "slip 20%", "slip 30%"), 1) { opt ->
        val p = listOf(.1, .2, .3)[opt]
        val g = gail(p)
        val s0 = g.sn.getValue(0)
        val s30 = g.sn.getValue(30)
        List(5) { stp ->
            when (stp) {
                0 -> frame(listOf(L("expert occupancy · expected visits"), occGrid(g.e.sv, "34,160,107")),
                    listOf(F("TV(expert, clone) =", f(s0.tv, 3), "#e5337a"), F("expert reaches G", pct(g.e.succ))),
                    "GAIL matches distributions, not actions." to "The expert recovers from slips, so its occupancy spills into the outer rows and comes back.", lg)
                1 -> frame(listOf(L("clone occupancy · iteration 0"), occGrid(s0.sv, "229,51,122")),
                    listOf(F("TV(expert, clone) =", f(s0.tv, 3), "#e5337a"), F("clone reaches G", pct(s0.succ), "#e5337a")),
                    "The clone’s occupancy is in the wrong places." to "It is ${f(s0.tv, 3)} away in total variation — that distance, not per-state accuracy, is what its ${pct(s0.succ)} success reflects.", lg)
                2 -> frame(listOf(L("discriminator reward, best action per cell"), rgrid(s0.rw)), listOf(F("D = ρ_E / (ρ_E + ρ_π) · r = log D − log(1 − D)")),
                    "The discriminator becomes the reward." to "Outer-row moves back to the middle score high (the expert does them); pressing on along a wrong row scores low.", lg)
                3 -> frame(listOf(L("TV(expert, policy) by GAIL iteration"), B(listOf(0, 1, 2, 5, 10, 30).map { k ->
                    val x = g.sn.getValue(k)
                    row("iter $k", f(x.tv, 3) + " · " + pct(x.succ), x.tv / .6, if (k == 30) "#22a06b" else "#e5337a", if (k == 30) "#f2f3f7" else null)
                })), listOf(F("label = TV · success")),
                    "TV falls from ${f(s0.tv, 3)} to ${f(s30.tv, 3)}." to (if (g.sn.getValue(1).tv > s0.tv) "The first update overshoots — the policy flees the penalised states — then settles." else "Each round the policy moves toward the states the discriminator rewards."), lg)
                else -> frame(listOf(L("GAIL policy occupancy · iteration 30"), occGrid(s30.sv, "34,160,107")),
                    listOf(F("success ·", pct(s0.succ) + " → " + pct(s30.succ), "#22a06b"), F("expert =", pct(g.e.succ))),
                    "The policy learns to recover." to "Success rises from ${pct(s0.succ)} to ${pct(s30.succ)} (expert ${pct(g.e.succ)}) without one labelled off-row state; the policy stays soft, which costs some steps.", lg)
            }
        }
    }
}

// ── The logged chain: right walks on (reward 1 off s4), left walks back, gamble pays ±5 and ends ──

private class Logged(val s: Int, val a: Int, val rw: Double, val ns: Int, val done: Boolean)

private val cdCache = HashMap<Int, List<Logged>>()

private fun chainData(n: Int): List<Logged> = synchronized(cdCache) {
    cdCache.getOrPut(n) {
        val r = Rb.rng(3)
        val d = ArrayList<Logged>()
        repeat(n) {
            var s = 0
            for (t in 0 until 20) {
                val u = r()
                val a = if (u < .6) 1 else if (u < .9) 0 else 2
                var rw = 0.0
                var ns = -1
                var done = false
                if (a == 2) { rw = if (r() < .5) 5.0 else -5.0; done = true }
                else if (a == 1) { if (s == 4) { rw = 1.0; done = true } else ns = s + 1 }
                else ns = max(0, s - 1)
                d += Logged(s, a, rw, ns, done)
                if (done) break
                s = ns
            }
        }
        d
    }
}

private fun chainEval(pol: IntArray): Double {
    var v = DoubleArray(5)
    repeat(300) { val o = v; v = DoubleArray(5) { s -> if (pol[s] == 2) 0.0 else if (pol[s] == 1) (if (s == 4) 1.0 else .9 * o[s + 1]) else .9 * o[max(0, s - 1)] } }
    return v[0]
}

private val chainBeh: Double by lazy {
    var v = DoubleArray(5)
    repeat(300) { val o = v; v = DoubleArray(5) { s -> .6 * (if (s == 4) 1.0 else .9 * o[s + 1]) + .3 * .9 * o[max(0, s - 1)] } }
    v[0]
}

private val chainOpt: Double by lazy { chainEval(IntArray(5) { 1 }) }

private class Fqi(val q: Array<DoubleArray>, val n: Array<IntArray>, val r: Array<DoubleArray>, val pol: IntArray, val ret: Double)

private val cfCache = HashMap<String, Fqi>()

private fun cfqi(d: List<Logged>, alpha: Double): Fqi = synchronized(cfCache) {
    cfCache.getOrPut("${d.size}_$alpha") {
        val n = Array(5) { IntArray(3) }
        val r = Array(5) { DoubleArray(3) }
        // Next-state counts per (s, a), the terminal under -1 (the design's 'T', which it skips).
        val t = Array(5) { Array(3) { java.util.TreeMap<Int, Int>() } }
        d.forEach { x ->
            n[x.s][x.a]++
            r[x.s][x.a] += x.rw
            val k = if (x.done) -1 else x.ns
            t[x.s][x.a][k] = (t[x.s][x.a][k] ?: 0) + 1
        }
        var q = Array(5) { DoubleArray(3) }
        repeat(3000) {
            val o = q
            q = Array(5) { s ->
                DoubleArray(3) { a ->
                    if (n[s][a] == 0) 0.0 else {
                        var v = r[s][a] / n[s][a]
                        for ((k, c) in t[s][a]) if (k >= 0) v += .9 * c / n[s][a] * o[k].max()
                        if (alpha != 0.0) {
                            val mx = o[s].max()
                            val e = o[s].map { exp(it - mx) }
                            val sum = e.sum()
                            v -= alpha * (e[a] / sum / (n[s][a].toDouble() / n[s].sum()) - 1)
                        }
                        o[s][a] + .05 * (v - o[s][a])
                    }
                }
            }
        }
        val pol = IntArray(5) { Rb.argmax(q[it]) }
        Fqi(q, n, r, pol, chainEval(pol))
    }
}

private val AN = listOf("left", "right", "gamble")

private fun polChips(fq: Fqi) = C(fq.q.mapIndexed { i, q -> tok("s$i", if (fq.pol[i] == 2) "err" else if (fq.pol[i] == 1) "done" else "plain", AN[fq.pol[i]] + " " + f(q.max())) })

private fun polText(pol: IntArray) = pol.joinToString(" ") { listOf("←", "→", "G")[it] }

// ── 62d Offline RL ──

private fun offlineLab(): RbLab {
    val lg = listOf("#9aa0ae" to "Behaviour", "#22a06b" to "Right / optimal", "#e5337a" to "Lottery overrated")
    return RbLab(listOf("small log", "4× log", "16× log"), 0) { opt ->
        val nn = listOf(60, 240, 960)[opt]
        val d = chainData(nn)
        val fq = cfqi(d, 0.0)
        val beh = chainBeh
        val best = chainOpt
        List(5) { stp ->
            when (stp) {
                0 -> frame(listOf(L("transitions logged per state and action"), RbBlock.Grid(26, AN, fq.n.mapIndexed { i, r ->
                    RbGRow("s$i", "#9aa0ae", r.mapIndexed { a, v -> RbCell("$v", if (a == 2) "rgba(229,51,122,.22)" else "#1f232d", if (a == 2) "#ff8a8d" else "#f2f3f7") })
                })), listOf(F("behaviour policy scores", f(beh, 3)), F("best possible (always right) =", f(best, 3), "#22a06b")),
                    "Offline RL starts from someone else’s log." to "${d.size} transitions from a policy that mostly walks right, dithers, and occasionally pulls a ±5 lottery worth exactly 0. No more interaction.", lg)
                1 -> {
                    val mx = (0 until 5).maxOf { i -> if (fq.n[i][2] > 0) fq.r[i][2] / fq.n[i][2] else -9.0 }
                    frame(listOf(L("empirical mean reward of gamble · true = 0"), B(fq.n.mapIndexed { i, r -> brow("s$i · n ${r[2]}", if (r[2] > 0) fq.r[i][2] / r[2] else 0.0, 5.0) })),
                        listOf(F("largest estimate =", f(mx), "#e5337a"), F("standard error with n = 5 ≈ 5/√5 =", "2.24")),
                        "A handful of lottery pulls per state is pure noise." to "With so few samples, at least one state’s estimate is bound to look like a jackpot.", lg)
                }
                2 -> {
                    val gi = fq.pol.indexOf(2)
                    val toG = (0..4).count { x ->
                        var c = x
                        var hit = false
                        for (k in 0 until 10) {
                            if (fq.pol[c] == 2) { hit = true; break }
                            if (fq.pol[c] == 1 && c == 4) break
                            c = if (fq.pol[c] == 1) c + 1 else max(0, c - 1)
                        }
                        hit
                    }
                    frame(listOf(L("fitted Q-iteration on the log · greedy action per state"), polChips(fq), B(fq.q.mapIndexed { i, q -> brow("Q(s$i, gamble)", q[2], 3.0) })),
                        listOf(F("Q(s,a) ← r̂ + 0.9 · max Q(s′, ·)"), F("greedy policy =", polText(fq.pol), if (2 in fq.pol) "#e5337a" else "#22a06b")),
                        if (2 in fq.pol) "The max picks up the noise." to "FQI trusts a ${f(fq.q[gi][2])} lottery estimate at s$gi built from ${fq.n[gi][2]} pulls; from $toG of 5 states its greedy policy heads there."
                        else "With this much data the noise averages out." to "Every state’s greedy action is right.", lg)
                }
                3 -> {
                    val worse = fq.ret < beh
                    frame(listOf(L("return from s0 when deployed (γ = 0.9)"), B(listOf(
                        row("behaviour", f(beh, 3), beh / best, "#9aa0ae"),
                        row("FQI greedy", f(fq.ret, 3), fq.ret / best, if (worse) "#e5337a" else "#22a06b", "#f2f3f7"),
                        row("optimal", f(best, 3), 1.0, "#22a06b"),
                    ))), listOf(F("deployed return =", f(fq.ret, 3), if (worse) "#e5337a" else "#22a06b")),
                        if (worse) "The “improved” policy is worse than the log." to "It scores ${f(fq.ret, 3)} against the behaviour policy’s ${f(beh, 3)} — distribution shift in one step."
                        else "Here FQI recovers the optimum." to "Enough data per action makes plain offline Q-learning safe.", lg)
                }
                else -> frame(listOf(L("FQI deployed return by log size"), B(listOf(60, 240, 960).map { x ->
                    val o = cfqi(chainData(x), 0.0)
                    row("${chainData(x).size} transitions", f(o.ret, 3), o.ret / best, if (o.ret > .5) "#22a06b" else "#e5337a", if (x == nn) "#f2f3f7" else "#9aa0ae")
                })), listOf(F("optimum =", f(best, 3), "#22a06b")),
                    "Only a lot more data fixes plain FQI." to "Rare actions stay rare in any log; offline methods instead refuse to trust them — next, CQL.", lg)
            }
        }
    }
}

// ── 62e CQL ──

private fun cqlLab(): RbLab {
    val lg = listOf("#3b82f6" to "Q value", "#22a06b" to "Right / optimum", "#e5337a" to "Lottery chosen")
    return RbLab(listOf("α 0", "α 0.3", "α 1"), 2) { opt ->
        val al = listOf(0.0, .3, 1.0)[opt]
        val d = chainData(60)
        val f0 = cfqi(d, 0.0)
        val cq = cfqi(d, al)
        val best = chainOpt
        val beh = chainBeh
        List(5) { stp ->
            when (stp) {
                0 -> {
                    val gap = f0.q.maxOf { it[2] }
                    frame(listOf(L("learned Q(s, gamble) without CQL · true = 0"), B(f0.q.mapIndexed { i, q -> brow("s$i", q[2], 3.0) })),
                        listOf(F("deployed return", f(f0.ret, 3) + " vs optimum " + f(best, 3), "#e5337a")),
                        "Plain FQI overrates the rarely pulled lottery by up to ${f(gap)}." to "CQL’s fix is not to estimate it better — it refuses to trust it.", lg)
                }
                1 -> {
                    val n0 = f0.n[0]
                    val pb = n0[2].toDouble() / n0.sum()
                    val q = cq.q[0]
                    val mx = q.max()
                    val e = q.map { exp(it - mx) }
                    val mu = e[2] / e.sum()
                    frame(listOf(S(st("π̂β(gamble | s0)", pct(pb) + " of logged actions"), st("μ(gamble | s0)", pct(mu) + " under softmax Q"), st("penalty at α = ${js(al)}", f(al * (mu / pb - 1), 3), if (al != 0.0) "#e5337a" else "#9aa0ae"))),
                        listOf(F("Q ← B̂Q − α·(μ(a|s) / π̂β(a|s) − 1)")),
                        "Rare actions pay a penalty; logged actions get a boost." to "The ratio μ/π̂β is large exactly where the data is thin, so that is where Q is pushed down.", lg)
                }
                2 -> {
                    val allRight = cq.pol.all { it == 1 }
                    frame(listOf(L("CQL α = ${js(al)} · greedy action per state"), polChips(cq), B(cq.q.mapIndexed { i, q -> brow("Q(s$i, gamble)", q[2], 3.0) })),
                        listOf(F("greedy policy =", polText(cq.pol), if (allRight) "#22a06b" else "#e5337a")),
                        when {
                            allRight -> "Walking right wins everywhere." to "The lottery estimates are pulled toward 0 and below; nothing out-of-data looks attractive any more."
                            al != 0.0 -> "α = ${js(al)} is not enough here." to "The noisiest estimate still beats walking right in some states."
                            else -> "α = 0 is plain FQI." to "The lottery wins wherever its noisy estimate is highest."
                        }, lg)
                }
                3 -> frame(listOf(L("deployed return from s0 · same 397-step log"), B(listOf(0.0, .3, 1.0, 3.0).map { x ->
                    val o = cfqi(d, x)
                    row("α ${js(x)}", f(o.ret, 3), o.ret / best, if (o.ret > .5) "#22a06b" else "#e5337a", if (x == al) "#f2f3f7" else "#9aa0ae")
                })), listOf(F("behaviour =", f(beh, 3)), F("optimum =", f(best, 3), "#22a06b")),
                    "A little pessimism recovers the optimum." to "Same log, same estimator; only the penalty on unsupported actions changed.", lg)
                else -> frame(listOf(S(st("α too small", "extrapolation error wins"), st("α right", "best supported action", "#22a06b"), st("α huge", "collapses to behaviour cloning"))),
                    listOf(F("CQL learns a lower bound on the policy’s value")),
                    "Conservatism is a dial." to "Too little trusts the noise; too much copies the behaviour policy and can’t improve on it.", lg)
            }
        }
    }
}

// ── 62f Decision Transformer: a return-conditioned policy on a 7-cell corridor ──

private class DtStep(val s: Int, val a: Int, val r: Double) { var rtg = 0.0 }

private class DtLogTraj(val st: List<DtStep>, val ret: Double)

private class DtData(val t: List<DtLogTraj>, val samples: List<DtStep>, val rets: List<Double>)

private class DtEnv(val n: Int, val r: Double, val done: Boolean)

private fun dtEnv(s: Int, a: Int): DtEnv {
    val n = max(0, min(6, s + if (a == 1) 1 else -1))
    return DtEnv(n, if (n == 6) 1.0 else -.05, n == 6)
}

private val dtData: DtData by lazy {
    val r = Rb.rng(11)
    val t = ArrayList<DtLogTraj>()
    repeat(250) {
        val pr = .15 + .6 * r()
        var s = 0
        val st = ArrayList<DtStep>()
        for (k in 0 until 25) {
            val a = if (r() < pr) 1 else 0
            val o = dtEnv(s, a)
            st += DtStep(s, a, o.r)
            s = o.n
            if (o.done) break
        }
        var g = 0.0
        for (i in st.indices.reversed()) { g += st[i].r; st[i].rtg = g }
        t += DtLogTraj(st, st[0].rtg)
    }
    DtData(t, t.flatMap { it.st }, t.map { it.ret })
}

private class DtRun(val r: Double, val t: Int, val ok: Boolean, val path: List<Int>)

/** A policy sees the state and the reward it just got (null on the first step). */
private fun dtRun(pol: (Int, Double?) -> Int): DtRun {
    var s = 0
    var r = 0.0
    var last: Double? = null
    val path = arrayListOf(0)
    for (t in 0 until 25) {
        val a = pol(s, last)
        val o = dtEnv(s, a)
        r += o.r
        last = o.r
        s = o.n
        path += s
        if (o.done) return DtRun(r, t + 1, true, path)
    }
    return DtRun(r, 25, false, path)
}

private fun dtPol(target: Double): (Int, Double?) -> Int {
    val samples = dtData.samples
    var rr = target
    return { s, last ->
        if (last != null) rr -= last
        val c = samples.filter { it.s == s }.map { abs(it.rtg - rr) to it.a }.sortedBy { it.first }.take(15)
        val v = IntArray(2)
        c.forEach { v[it.second]++ }
        if (v[1] >= v[0]) 1 else 0
    }
}

private fun bcPol(samples: List<DtStep>): (Int, Double?) -> Int {
    val m = IntArray(6) { s ->
        val c = IntArray(2)
        samples.forEach { if (it.s == s) c[it.a]++ }
        if (c[1] > c[0]) 1 else 0
    }
    return { s, _ -> m[s] }
}

private fun dtLab(): RbLab {
    val lg = listOf("#22a06b" to "Target hit", "#3b82f6" to "Dataset", "#e5337a" to "Cloned average")
    return RbLab(listOf("R̂ 0", "R̂ 0.5", "R̂ 0.75"), 2) { opt ->
        val tg = listOf(0.0, .5, .75)[opt]
        val dd = dtData
        val rets = dd.rets
        val mn = rets.min()
        val mxr = rets.max()
        val mean = rets.sum() / rets.size
        val bc = bcPol(dd.samples)
        val bco = dtRun(bc)
        fun frac(x: Double) = (x + 1.3) / 2.1
        List(5) { stp ->
            when (stp) {
                0 -> {
                    val bins = listOf(-1.25, -1.0, -.75, -.5, -.25, 0.0, .25, .5, .75)
                    val cnt = bins.map { b -> rets.count { it >= b - .125 && it < b + .125 } }
                    val cm = cnt.max().toDouble()
                    frame(listOf(L("returns of the 250 logged trajectories"), RbBlock.Cols(bins.mapIndexed { i, b ->
                        RbVCol("${cnt[i]}", f(b, 2).replaceFirst("0.", "."), "#9aa0ae", listOf(RbVBar(cnt[i] / cm * 62, if (b >= .5) "#22a06b" else "#3b82f6")))
                    })), listOf(F("worst · mean · best =", f(mn) + " · " + f(mean) + " · " + f(mxr))),
                        "A deliberately mixed log: most trajectories are poor." to "Each was generated by a random walker with its own bias to the right; the goal is 6 cells away.", lg)
                }
                1 -> frame(listOf(L("behaviour cloning: majority action per cell"), C((0..5).map { x -> val a = bc(x, null); tok("s$x", if (a == 1) "done" else "err", if (a == 1) "→" else "←") })),
                    listOf(F("cloned return =", f(bco.r), "#e5337a"), F("reaches the goal in 25 steps:", if (bco.ok) "yes" else "no", if (bco.ok) "#22a06b" else "#e5337a")),
                    "Cloning mixed data copies the average." to "The majority action near the start is ←, so the clone oscillates and scores ${f(bco.r)}.", lg)
                2 -> {
                    val o = dtRun(dtPol(tg))
                    frame(listOf(L("conditioned on return-to-go R̂ = ${js(tg)}"), C(o.path.take(10).map { tok("s$it", if (it == 6) "ans" else "plain") }),
                        S(st("target", f(tg)), st("achieved", f(o.r), if (abs(o.r - tg) < .13) "#22a06b" else "#e5337a"), st("steps", "${o.t}"))),
                        listOf(F("a = π(s, R̂) · R̂ ← R̂ − r after each step")),
                        "Ask for ${f(tg)}, get ${f(o.r)}." to "The policy picks the action that logged trajectories with that much return still to come took from here.", lg)
                }
                3 -> frame(listOf(L("achieved return by requested target"), B(listOf(-.5, 0.0, .25, .5, .75, 1.0, 2.0).map { t ->
                    val o = dtRun(dtPol(t))
                    row("target ${f(t)}", f(o.r), frac(o.r), if (abs(o.r - t) < .13) "#22a06b" else "#9aa0ae", if (t == tg) "#f2f3f7" else "#9aa0ae")
                })), listOf(F("best in the log =", f(mxr), "#22a06b")),
                    "Targets inside the data are hit; beyond it, they saturate." to "Asking for 2 returns ${f(dtRun(dtPol(2.0)).r)} — the policy can stitch good behaviour together, not invent better.", lg)
                else -> {
                    val top = dd.t.sortedByDescending { it.ret }.take(25).flatMap { it.st }
                    val pb = dtRun(bcPol(top))
                    val d = dtRun(dtPol(.75))
                    frame(listOf(B(listOf(
                        row("BC, all data", f(bco.r), frac(bco.r), "#e5337a"),
                        row("BC, top 10%", f(pb.r), frac(pb.r), "#9aa0ae"),
                        row("DT, R̂ = 0.75", f(d.r), frac(d.r), "#22a06b", "#f2f3f7"),
                        row("best logged", f(mxr), frac(mxr), "#3b82f6"),
                    ))), listOf(F("sequence modelling, no Bellman backup")),
                        "Return conditioning gets filtering for free." to "Here it matches cloning the top 10% — without choosing a cutoff, and with one model for every target.", lg)
                }
            }
        }
    }
}

// ── 62g Meta-RL: a 5 × 8 room, four training goals in the bottom-right ──

private fun mmv(i: Int, a: Int): Int {
    val r = i / 8 + RbGw.AC[a][0]
    val c = i % 8 + RbGw.AC[a][1]
    return if (r < 0 || r > 4 || c < 0 || c > 7) i else r * 8 + c
}

private fun mvi(g: Int): Array<DoubleArray> {
    var v = DoubleArray(40)
    fun q(vv: DoubleArray, i: Int, a: Int): Double {
        val n = mmv(i, a)
        return if (i == g) 0.0 else (if (n == g) 1.0 else -0.04) + if (n == g) 0.0 else .95 * vv[n]
    }
    repeat(200) { val o = v; v = DoubleArray(40) { i -> if (i == g) 0.0 else (0..3).maxOf { a -> q(o, i, a) } } }
    return Array(40) { i -> DoubleArray(4) { a -> q(v, i, a) } }
}

private val MTR = listOf(23, 31, 39, 37)

private val mamlInit: Array<DoubleArray> by lazy {
    val qs = MTR.map { mvi(it) }
    Array(40) { i -> DoubleArray(4) { a -> qs.fold(0.0) { s, q -> s + q[i][a] } / 4 } }
}

private val maCache = HashMap<String, DoubleArray>()

private fun madapt(meta: Boolean, g: Int): DoubleArray = synchronized(maCache) {
    maCache.getOrPut("${meta}_$g") {
        val sc = DoubleArray(10)
        val init = if (meta) mamlInit else null
        for (k in 1..30) {
            val r = Rb.rng(k * 17L + 1)
            val q = Array(40) { i -> init?.get(i)?.copyOf() ?: DoubleArray(4) }
            for (e in 0 until 10) {
                var s = 0
                var t = 0
                while (t < 40) {
                    val qs = q[s]
                    val a: Int
                    if (r() < .1) a = floor(r() * 4).toInt()
                    else {
                        var b = 0
                        for (i in 1 until 4) if (qs[i] > qs[b] + 1e-12) b = i
                        a = b
                    }
                    val n = mmv(s, a)
                    val rw = if (n == g) 1.0 else -0.04
                    q[s][a] += .5 * (rw + (if (n == g) 0.0 else .95 * q[n].max()) - q[s][a])
                    s = n
                    if (n == g) break
                    t++
                }
                sc[e] += (if (s == g) t + 1 else 40) / 30.0
            }
        }
        sc
    }
}

private class MetaCell(val t: String? = null, val bg: String? = null, val ring: String? = null)

private fun g58(fn: (Int) -> MetaCell) = RbBlock.Grid(30, (0..7).map { "c$it" }, (0..4).map { r ->
    RbGRow("r$r", "#9aa0ae", (0..7).map { c ->
        val o = fn(r * 8 + c)
        RbCell(o.t ?: "", o.bg ?: "#1f232d", "#fff", o.ring ?: "none")
    })
})

private fun metaLab(): RbLab {
    val lg = listOf("#6d5dfc" to "Training goals", "#22a06b" to "Meta-learned", "#e5337a" to "Out of distribution")
    fun base(i: Int): MetaCell = when {
        i == 0 -> MetaCell("S")
        i in MTR -> MetaCell("★", "#6d5dfc")
        i == 38 -> MetaCell("?", ring = "inset 0 0 0 2px #f5c542")
        i == 7 -> MetaCell("?", ring = "inset 0 0 0 2px #e5337a")
        else -> MetaCell()
    }
    return RbLab(listOf("held-out goal", "OOD goal"), 0) { opt ->
        val g = listOf(38, 7)[opt]
        val gn = listOf("held-out goal", "out-of-distribution goal")[opt]
        val q0 = mamlInit
        val sc = madapt(false, g)
        val mt = madapt(true, g)
        List(5) { stp ->
            when (stp) {
                0 -> frame(listOf(L("training goals ★ · test goals ?"), g58(::base)),
                    listOf(F("4 training tasks · the agent is told none of them"), F("test ·", "yellow = held-out, pink = out of distribution")),
                    "Meta-RL learns across a family of tasks." to "The goal is always somewhere in the bottom-right; what carries over is not a policy but a fast way to find one.", lg)
                1 -> {
                    val v = q0.map { it.max() }
                    val mx = v.max()
                    val mn = v.min()
                    frame(listOf(L("meta-learned starting values max_a Q₀(s, a)"), g58 { i ->
                        val b = base(i)
                        MetaCell(b.t ?: f(v[i], 1), b.bg ?: "rgba(59,130,246,${Rb.a2(.1 + .8 * (v[i] - mn) / (mx - mn))})", b.ring)
                    }), listOf(F("Q₀ = mean of the 4 task solutions")),
                        "The start already points at the goal region." to "It can’t know which cell, but every task rewards heading down and right.", lg)
                }
                2 -> frame(listOf(L("steps to reach the $gn · 30 runs"), RbBlock.Cols(listOf(0, 1, 2, 4, 9).map { e ->
                    RbVCol(f(mt[e], 1), "ep ${e + 1}", "#9aa0ae", listOf(RbVBar(sc[e] / 40 * 62, "#9aa0ae"), RbVBar(mt[e] / 40 * 62, "#22a06b")))
                })), listOf(F("episode 1 · scratch ${f(sc[0], 1)} vs meta", f(mt[0], 1), "#22a06b"), F("optimal path =", "${g / 8 + g % 8} steps")),
                    if (g == 38) "From the meta start, the new goal is found almost at once." to "Episode 1 takes ${f(mt[0], 1)} steps against ${f(sc[0], 1)} from scratch; by episode 10 it is at ${f(mt[9], 1)}."
                    else "Out of distribution, the head start disappears." to "The prior pulls toward the bottom-right, away from this goal: episode 1 takes ${f(mt[0], 1)} steps.", lg)
                3 -> {
                    val rows = ArrayList<RbRow>()
                    listOf(38 to "held-out", 7 to "OOD").forEach { (gg, l) ->
                        val a = madapt(false, gg)
                        val b = madapt(true, gg)
                        rows += row("$l · scratch", f(a[2], 1), a[2] / 40, "#9aa0ae", if (gg == g) "#f2f3f7" else "#9aa0ae")
                        rows += row("$l · meta", f(b[2], 1), b[2] / 40, if (gg == 7) "#e5337a" else "#22a06b", if (gg == g) "#f2f3f7" else "#9aa0ae")
                    }
                    frame(listOf(L("steps to goal in episode 3 · lower is better"), B(rows)), listOf(F("adaptation: Q-learning, ε = 0.1, α = 0.5")),
                        "Meta-learning buys speed inside the family only." to "A prior is a bet about which tasks will come; outside that family it is mostly dead weight.", lg)
                }
                else -> frame(listOf(S(st("inner loop", "θ′ = θ − α ∇L_task(θ)"), st("outer loop", "θ ← θ − β ∇ Σ L_task(θ′)", "#22a06b"), st("here", "averaged task solutions as θ₀"))),
                    listOf(F("MAML optimises the start for one-step adaptation")),
                    "MAML differentiates through the adaptation step." to "This tabular stand-in averages task solutions (Reptile-style); the adaptation is real Q-learning.", lg)
            }
        }
    }
}
