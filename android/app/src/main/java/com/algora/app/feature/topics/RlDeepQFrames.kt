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
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

// ── Deep Q-network storyboard frames ─────────────────────────────────────────
// A 6-state chain with reward only past the far end. Approximators, replay, target networks, double
// estimation, dueling heads, noisy exploration and return distributions, each trained or computed
// live from a seeded run. Drawn by RlBoardLabs.kt; the iOS port (RlDeepQFrames.swift) matches it.

internal val rlDeepQTopicIds = setOf("dqn", "experience_replay", "target_networks", "double_dqn", "dueling_dqn", "noisy_nets", "c51", "rainbow_dqn")

internal fun rlDeepQLab(topicId: String): RbLab? = when (topicId) {
    "dqn" -> dqnLab()
    "experience_replay" -> replayLab()
    "target_networks" -> targetLab()
    "double_dqn" -> doubleLab()
    "dueling_dqn" -> duelingLab()
    "noisy_nets" -> noisyLab()
    "c51" -> c51Lab()
    "rainbow_dqn" -> rainbowLab()
    else -> null
}

// ── The chain: right from s5 ends with reward 1; left moves back one state ──

internal class RbCst(val ns: Int, val r: Double, val t: Boolean)

internal fun rbCst(s: Int, a: Int): RbCst =
    if (a == 1) (if (s == 5) RbCst(-1, 1.0, true) else RbCst(s + 1, 0.0, false)) else RbCst(max(0, s - 1), 0.0, false)

internal class RbCq(val qr: List<Double>, val ql: List<Double>)

internal fun rbCq(g: Double = .9): RbCq {
    val qr = (0..5).map { g.pow(5 - it) }
    return RbCq(qr, (0..5).map { g * qr[max(0, it - 1)] })
}

private fun chainChips(vals: List<Double>, hl: Int? = null) = C(vals.mapIndexed { i, v ->
    val t = tok("s$i", if (i == hl) "cur" else "plain", f(v))
    RbTok(t.t, t.sub, if (i == hl) "#f5c542" else RbGw.vcol(v), if (i == hl) "#1b1d24" else "#fff")
})

private fun chainErr(v: List<Double>): Double {
    val qr = rbCq().qr
    return v.indices.maxOf { abs(v[it] - qr[it]) }
}

// ── 59a DQN: semi-gradient Q-learning on three feature sets ──

private val dqnCache = HashMap<Int, Map<Int, List<Double>>>()

private fun dqnRun(kind: Int): Map<Int, List<Double>> = synchronized(dqnCache) {
    dqnCache.getOrPut(kind) {
        val r = Rb.rng(21)
        fun phi(s: Int): DoubleArray {
            val x = s / 5.0
            return when (kind) {
                0 -> DoubleArray(6) { if (it == s) 1.0 else 0.0 }
                1 -> doubleArrayOf(1.0, x)
                else -> doubleArrayOf(1.0, x, x * x)
            }
        }
        val d = phi(0).size
        val w = arrayOf(DoubleArray(d), DoubleArray(d))
        fun q(s: Int, a: Int): Double { val p = phi(s); var acc = 0.0; for (i in p.indices) acc += p[i] * w[a][i]; return acc }
        val snaps = HashMap<Int, List<Double>>()
        for (t in 1..5000) {
            val s = floor(r() * 6).toInt()
            val a = if (r() < .5) 0 else 1
            val o = rbCst(s, a)
            val tg = o.r + if (o.t) 0.0 else .9 * max(q(o.ns, 0), q(o.ns, 1))
            val e = tg - q(s, a)
            phi(s).forEachIndexed { i, v -> w[a][i] += .1 * e * v }
            if (t == 100 || t == 1000 || t == 5000) snaps[t] = (0..5).map { q(it, 1) }
        }
        snaps
    }
}

private fun dqnLab(): RbLab {
    val lg = listOf("#3b82f6" to "Learned / true", "#e5337a" to "Error", "#6d5dfc" to "Chosen features")
    val names = listOf("table", "linear", "quadratic")
    return RbLab(names, 2) { k ->
        val qr = rbCq().qr
        val sn = dqnRun(k)
        List(5) { stp ->
            val tt = listOf(0, 100, 1000, 5000)[min(stp, 3)]
            when {
                stp == 0 -> frame(listOf(L("6-state chain · reward 1 for stepping right off s5 · γ 0.9"), chainChips(qr), S(st("target", "Q*(s, right) = 0.9^(5−s)"))),
                    listOf(F("Q(s,a) ≈ w_a · φ(s)"), F("features:", listOf("one-hot (6 per action)", "[1, s]", "[1, s, s²]")[k], "#8f84ff")),
                    "DQN replaces the Q-table with a function." to "Here the “network” is a linear map on features, small enough to watch every weight move.", lg)
                stp < 4 -> {
                    val l = sn.getValue(tt)
                    frame(listOf(L("learned Q(s, right) after $tt updates"), B(l.mapIndexed { i, v -> row("s$i", "${f(v)} / ${f(qr[i])}", v, "#3b82f6") })),
                        listOf(F("w ← w + 0.1·(r + 0.9·max Q(s′) − Q)·φ(s)"), F("largest error =", f(chainErr(l), 3), "#e5337a")),
                        "$tt updates: error ${f(chainErr(l), 3)}." to when (k) {
                            0 -> "One-hot features are just a table: it converges to Q* exactly."
                            1 -> "A straight line can’t bend to fit 0.9^(5−s); some error is permanent."
                            else -> "A curve fits the shape closely with only 3 weights per action."
                        }, lg)
                }
                else -> frame(listOf(L("final largest error, 5,000 updates"), B((0..2).map { j ->
                    val e = chainErr(dqnRun(j).getValue(5000))
                    row(names[j], f(e, 3), e / .2, if (j == k) "#6d5dfc" else "#3a3f4c", if (j == k) "#f2f3f7" else "#9aa0ae")
                })), listOf(F("weights:", "12 · 4 · 6")),
                    "Generalisation is the point, and the risk." to "Features let one update move many states. The next screens are the fixes that keep that stable.", lg)
            }
        }
    }
}

// ── 59b Experience Replay ──

private val repCache = HashMap<Int, Map<Int, List<Double>>>()

private class RbTr(val s: Int, val a: Int, val ns: Int, val r: Double, val t: Boolean)

internal fun rbRepRun(k: Int): Map<Int, List<Double>> = synchronized(repCache) {
    repCache.getOrPut(k) {
        val r = Rb.rng(5)
        val q = arrayOf(DoubleArray(6), DoubleArray(6))
        val buf = ArrayList<RbTr>()
        val snaps = HashMap<Int, List<Double>>()
        var s = 0
        var steps = 0
        fun up(x: RbTr) {
            val tg = x.r + if (x.t) 0.0 else .9 * max(q[0][x.ns], q[1][x.ns])
            q[x.a][x.s] += .5 * (tg - q[x.a][x.s])
        }
        while (steps < 1000) {
            val a = if (r() < .5) 0 else 1
            val o = rbCst(s, a)
            val x = RbTr(s, a, o.ns, o.r, o.t)
            up(x)
            buf += x
            repeat(k) { up(buf[floor(r() * buf.size).toInt()]) }
            steps++
            s = if (o.t) 0 else o.ns
            if (steps == 50 || steps == 200 || steps == 1000) snaps[steps] = q[1].toList()
        }
        snaps
    }
}

private fun replayLab(): RbLab {
    val lg = listOf("#3b82f6" to "Q estimate", "#e5337a" to "Error", "#6d5dfc" to "Chosen ratio")
    return RbLab(listOf("online", "replay ×4", "replay ×16"), 1) { opt ->
        val k = listOf(0, 4, 16)[opt]
        val qr = rbCq().qr
        val sn = rbRepRun(k)
        List(5) { stp ->
            val tt = listOf(0, 50, 200, 1000)[min(stp, 3)]
            val l = if (tt > 0) sn.getValue(tt) else List(6) { 0.0 }
            when {
                stp == 0 -> frame(listOf(L("target Q*(s, right)"), chainChips(qr)), listOf(F("random behaviour from s0, reward only past s5")),
                    "Reward 1 at the far end, nothing elsewhere." to "Value must travel back six states from rare arrivals, so reusing transitions matters.", lg)
                stp < 4 -> frame(listOf(L("Q(s, right) after $tt environment steps"), chainChips(l)),
                    listOf(F("updates per step: 1 + $k replayed"), F("largest error =", f(chainErr(l), 3), "#e5337a")),
                    "$tt steps: error ${f(chainErr(l), 3)}." to (if (k == 0) "Online: each transition is used once and thrown away; the reward creeps back one state per arrival." else "Replay revisits $k stored transitions per step, pushing the reward back through the chain without new experience."), lg)
                else -> frame(listOf(L("largest error after 200 steps"), B(listOf(0, 4, 16).map { j ->
                    val e = chainErr(rbRepRun(j).getValue(200))
                    row(if (j > 0) "replay ×$j" else "online", f(e, 3), e, if (j == k) "#6d5dfc" else "#3a3f4c", if (j == k) "#f2f3f7" else "#9aa0ae")
                })), listOf(F("same experience, more learning")),
                    "Replay buys data efficiency." to "It also breaks the correlation between consecutive samples — what a neural network needs to train stably.", lg)
            }
        }
    }
}

// ── 59c Target Networks ──

private class TgRun(val trace: List<Double>, val move: Double, val err: Double)

private val tgCache = HashMap<Int, TgRun>()

private fun tgtRun(cc: Int): TgRun = synchronized(tgCache) {
    tgCache.getOrPut(cc) {
        val r = Rb.rng(13)
        fun phi(s: Int): DoubleArray { val x = s / 5.0; return doubleArrayOf(1.0, x, x * x) }
        val w = arrayOf(DoubleArray(3), DoubleArray(3))
        var wt = arrayOf(w[0].copyOf(), w[1].copyOf())
        fun q(m: Array<DoubleArray>, s: Int, a: Int): Double { val p = phi(s); var acc = 0.0; for (i in p.indices) acc += p[i] * m[a][i]; return acc }
        val trace = ArrayList<Double>()
        var move = 0.0
        var prev: Double? = null
        for (t in 1..3000) {
            if (t % cc == 0) wt = arrayOf(w[0].copyOf(), w[1].copyOf())
            val lab = .9 * max(q(wt, 5, 0), q(wt, 5, 1))
            if (t <= 16) trace += lab
            if (prev != null) move += abs(lab - prev)
            prev = lab
            val s = floor(r() * 6).toInt()
            val a = if (r() < .5) 0 else 1
            val o = rbCst(s, a)
            val tg = o.r + if (o.t) 0.0 else .9 * max(q(wt, o.ns, 0), q(wt, o.ns, 1))
            val e = tg - q(w, s, a)
            phi(s).forEachIndexed { i, v -> w[a][i] += .1 * e * v }
        }
        val qr = rbCq().qr
        TgRun(trace, move, (0..5).maxOf { abs(q(w, it, 1) - qr[it]) })
    }
}

private fun targetLab(): RbLab {
    val lg = listOf("#f5c542" to "Label", "#e5337a" to "Error", "#6d5dfc" to "Chosen period")
    return RbLab(listOf("sync 1", "sync 10", "sync 100"), 0) { opt ->
        val cc = listOf(1, 10, 100)[opt]
        val rr = tgtRun(cc)
        List(4) { stp ->
            when (stp) {
                0 -> frame(listOf(S(st("update", "Q(s,a) ← r + γ·max Q(s′,·)"), st("problem", "the label uses the weights being trained"))),
                    listOf(F("label for Q(s4, right) = 0.9 · max Q(s5, ·)")),
                    "Bootstrapping chases its own tail." to "Every update moves the weights, which moves the labels — with shared features, for every state at once.", lg)
                1 -> {
                    val mx = max(rr.trace.maxOf { abs(it) }, .01)
                    frame(listOf(L("label for Q(s4, right), first 16 updates"), RbBlock.Cols(rr.trace.mapIndexed { i, v ->
                        RbVCol("", if (i % 5 == 0) "${i + 1}" else "", "#9aa0ae", listOf(RbVBar(abs(v) / mx * 58, "#f5c542")))
                    })), listOf(F("target network synced every $cc update${if (cc > 1) "s" else ""}")),
                        (if (cc == 1) "Without a target network the label moves every update." else "A frozen copy holds the label still between syncs.") to
                            (if (cc == 1) "Each bar is a different target." else "Flat runs of $cc updates, then a step when the copy is refreshed."), lg)
                }
                2 -> frame(listOf(S(st("total label movement", f(rr.move, 3)), st("final error", f(rr.err, 3), "#e5337a"))),
                    listOf(F("Σ |Δ label| over 3,000 updates =", f(rr.move, 3), "#f5c542")),
                    "Stable labels, a real regression problem." to "Between syncs the network fits a fixed target, like supervised learning.", lg)
                else -> frame(listOf(L("label movement vs final error"), B(listOf(1, 10, 100).map { c ->
                    val x = tgtRun(c)
                    row("sync $c", "move ${f(x.move, 2)} · err ${f(x.err, 3)}", x.move / max(tgtRun(1).move, .01), if (c == cc) "#6d5dfc" else "#3a3f4c", if (c == cc) "#f2f3f7" else "#9aa0ae")
                })), listOf(F("DQN (2015) synced every 10,000 steps")),
                    "Freeze too briefly: chasing. Too long: stale." to "The sync period trades stability against how fresh the bootstrapped labels are.", lg)
            }
        }
    }
}

// ── 59d Double DQN: Sutton & Barto's maximisation-bias example ──

internal class DdqPt(val e: Int, val left: Double, val q: Double)

internal class DdqRes(val pts: List<DdqPt>, val peak: Double)

internal val rbDdq: List<DdqRes> by lazy {
    val eps = listOf(10, 50, 100, 300)
    listOf(false, true).map { dbl ->
        val left = DoubleArray(301)
        val qa = DoubleArray(301)
        for (run in 0 until 100) {
            val r = Rb.rng(1000L + run * 13)
            fun g() = -0.1 + Rb.gauss(r)
            val a1 = DoubleArray(2)
            val a2 = DoubleArray(2)
            val b1 = DoubleArray(8)
            val b2 = DoubleArray(8)
            for (e in 1..300) {
                val q0 = a1[0] + if (dbl) a2[0] else 0.0
                val q1 = a1[1] + if (dbl) a2[1] else 0.0
                val a = if (r() < .1) (if (r() < .5) 0 else 1) else if (q0 == q1) (if (r() < .5) 0 else 1) else if (q0 > q1) 0 else 1
                if (a == 1) {
                    if (dbl && r() < .5) a2[1] += .1 * (0 - a2[1]) else a1[1] += .1 * (0 - a1[1])
                } else {
                    left[e]++
                    val qb = DoubleArray(8) { b1[it] + if (dbl) b2[it] else 0.0 }
                    val b = if (r() < .1) floor(r() * 8).toInt() else Rb.argmax(qb)
                    val rew = g()
                    if (!dbl) {
                        a1[0] += .1 * (b1.max() - a1[0])
                        b1[b] += .1 * (rew - b1[b])
                    } else if (r() < .5) {
                        a1[0] += .1 * (b2[Rb.argmax(b1)] - a1[0])
                        b1[b] += .1 * (rew - b1[b])
                    } else {
                        a2[0] += .1 * (b1[Rb.argmax(b2)] - a2[0])
                        b2[b] += .1 * (rew - b2[b])
                    }
                }
                qa[e] += if (dbl) (a1[0] + a2[0]) / 2 else a1[0]
            }
        }
        DdqRes(eps.map { DdqPt(it, left[it] / 100, qa[it] / 100) }, (1..300).maxOf { qa[it] / 100 })
    }
}

private fun doubleLab(): RbLab {
    val lg = listOf("#e5337a" to "Q-learning", "#22a06b" to "Double", "#e5484d" to "True value")
    return RbLab(listOf("Q-learning", "Double"), 0) { dbl ->
        val d = rbDdq
        val rr = d[dbl]
        List(5) { stp ->
            when {
                stp == 0 -> frame(listOf(S(st("right from A", "ends, reward 0"), st("left from A", "to B, then 8 actions"), st("each B action", "reward ~ N(−0.1, 1)"), st("true Q(A, left)", "−0.10", "#e5484d"))),
                    listOf(F("optimal: always go right")),
                    "A trap built from noise." to "Every action in B is slightly bad on average, but some will look good by chance.", lg)
                stp < 4 -> {
                    val x = rr.pts[if (stp == 1) 0 else if (stp == 2) 2 else 3]
                    frame(listOf(B(listOf(brow("Q(A, left)", x.q, .3, f(x.q, 3)), row("P(choose left)", pct(x.left), x.left, "#e5337a")))),
                        listOf(F(if (dbl == 1) "target: Q₂(B, argmax Q₁(B,·))" else "target: max_b Q(B, b)"), F("episode ${x.e}, mean of 100 runs")),
                        if (dbl == 1) "Episode ${x.e}: Q(A, left) = ${f(x.q, 3)}." to "One table picks the action, the other scores it — the lucky noise doesn’t get to grade itself."
                        else "Episode ${x.e}: Q(A, left) = ${f(x.q, 3)}, true −0.10." to "The max over 8 noisy estimates is biased upward, so left looks worth taking.", lg)
                }
                else -> frame(listOf(L("P(choose left) by episode"), B((0..1).flatMap { m ->
                    (0..3).map { i -> row("${if (m == 1) "Double" else "Q"} · ep ${d[m].pts[i].e}", pct(d[m].pts[i].left), d[m].pts[i].left, if (m == 1) "#22a06b" else "#e5337a", if (m == dbl) "#f2f3f7" else "#9aa0ae") }
                })), listOf(F("peak Q(A, left): Q", f(d[0].peak, 3), "#e5337a"), F("peak Q(A, left): Double", f(d[1].peak, 3), "#22a06b")),
                    "Double estimation removes the optimism." to "Double DQN applies the same trick with the online and target networks.", lg)
            }
        }
    }
}

// ── 59e Dueling DQN ──

private fun duelingLab(): RbLab {
    val lg = listOf("#3b82f6" to "Q", "#6d5dfc" to "V(s)", "#22a06b" to "A(s, right)")
    return RbLab(listOf("γ 0.9", "γ 0.99"), 1) { opt ->
        val g = listOf(.9, .99)[opt]
        val cq = rbCq(g)
        val v = cq.qr.indices.map { (cq.qr[it] + cq.ql[it]) / 2 }
        val ar = cq.qr.indices.map { cq.qr[it] - v[it] }
        List(4) { stp ->
            when (stp) {
                0 -> frame(listOf(L("Q(s, right) / Q(s, left)"), B((0..5).map { i -> row("s$i", "${f(cq.qr[i])} / ${f(cq.ql[i])}", cq.qr[i], "#3b82f6") })),
                    listOf(F("γ = ${js(g)}")),
                    "Two numbers per state, nearly equal." to "Most of each Q is “how good is this state”; only a sliver is “which action”.", lg)
                1 -> frame(listOf(L("V(s) = mean of the two Qs"), B(v.mapIndexed { i, x -> row("s$i", f(x), x, "#6d5dfc") })),
                    listOf(F("Q(s,a) = V(s) + A(s,a) − mean A")),
                    "Dueling splits the head in two." to "One stream estimates V(s), the other the advantage of each action.", lg)
                2 -> frame(listOf(L("A(s, right) = Q(s, right) − V(s)"), B(ar.mapIndexed { i, a -> brow("s$i", a, .1, f(a, 3)) })),
                    listOf(F("|A| / V at s0 =", pct(abs(ar[0]) / v[0]), "#22a06b")),
                    "The choice is worth ${pct(abs(ar[0]) / v[0])} of the value." to (if (g > .95) "At γ 0.99 the advantage is tiny next to V — easy to drown in noise if learned inside one number." else "Small, but it is the only part that decides the action."), lg)
                else -> frame(listOf(S(st("plain DQN", "an update to Q(s, right) leaves Q(s, left) unchanged"), st("dueling", "the same update also moves V(s), so both Qs improve"))),
                    listOf(F("same parameters, better use of each sample")),
                    "V is learned from every action." to "States where the action barely matters still teach the value stream.", lg)
            }
        }
    }
}

// ── 59f Noisy Nets ──

internal class NoisyRun(val ok: Double, val mean: Double, val vis: List<Double>)

internal class NoisyRes(val eg: NoisyRun, val nz: NoisyRun)

internal val rbNoisy: NoisyRes by lazy {
    val r = Rb.rng(31)
    fun g() = Rb.gauss(r)
    fun run(noisy: Boolean): NoisyRun {
        var ok = 0
        var steps = 0
        val vis = IntArray(6)
        for (e in 0 until 2000) {
            val b = g()
            val n = DoubleArray(6) { .3 * g() }
            var s = 0
            for (t in 0 until 100) {
                vis[s]++
                val a = if (noisy) (if (b + n[s] > 0) 1 else 0) else (if (r() < .5) 1 else 0)
                val o = rbCst(s, a)
                if (o.t) { ok++; steps += t + 1; break }
                s = o.ns
            }
        }
        return NoisyRun(ok / 2000.0, if (ok > 0) steps.toDouble() / ok else 0.0, vis.map { it / 2000.0 })
    }
    val eg = run(false)
    NoisyRes(eg, run(true))
}

private fun noisyLab(): RbLab {
    val lg = listOf("#9aa0ae" to "ε-greedy", "#22a06b" to "Noisy net")
    return RbLab(listOf("ε-greedy", "noisy net"), 1) { opt ->
        val n = rbNoisy
        val rr = if (opt == 1) n.nz else n.eg
        List(4) { stp ->
            when (stp) {
                0 -> frame(listOf(S(st("task", "reach past s5 from s0, 100-step limit"), st("ε-greedy (untrained)", "a fresh coin flip every step"), st("noisy net", "random weights drawn once per episode"))),
                    listOf(F("2,000 episodes each")),
                    "Exploration that dithers vs exploration that commits." to "A random walk needs ~42 steps to cross a 6-state chain; a consistent push needs 6.", lg)
                1 -> frame(listOf(L("visits per episode, ${if (opt == 1) "noisy net" else "ε-greedy"}"), B(rr.vis.mapIndexed { i, v -> row("s$i", f(v, 1), v / 20, if (opt == 1) "#22a06b" else "#9aa0ae") })),
                    listOf(F("reached goal =", pct(rr.ok), if (opt == 1) "#22a06b" else "#c3c7d1")),
                    if (opt == 1) "Noisy: many episodes march straight through." to "When the drawn weights favour right, every state goes right."
                    else "ε-greedy: most time is spent near the start." to "Independent coin flips cancel out; deep states are rarely seen.", lg)
                2 -> frame(listOf(S(st("reached goal", pct(rr.ok)), st("mean steps when reached", f(rr.mean, 1)))),
                    listOf(F("mean steps =", f(rr.mean, 1), "#22a06b")),
                    "Successful episodes take ${f(rr.mean, 1)} steps." to "Per-episode noise explores in a direction; per-step noise explores in place.", lg)
                else -> frame(listOf(B(listOf(
                    row("ε-greedy · reached", pct(n.eg.ok), n.eg.ok, "#9aa0ae"),
                    row("noisy · reached", pct(n.nz.ok), n.nz.ok, "#22a06b", "#f2f3f7"),
                    row("ε-greedy · steps", f(n.eg.mean, 1), n.eg.mean / 100, "#9aa0ae"),
                    row("noisy · steps", f(n.nz.mean, 1), n.nz.mean / 100, "#22a06b", "#f2f3f7"),
                ))), listOf(F("noise σ is learned, so it shrinks where it stops helping")),
                    "Noisy nets learn how much to explore." to "The noise scale is a trainable parameter — no ε schedule to tune.", lg)
            }
        }
    }
}

// ── 59g Distributional RL (C51) ──

/** `z.toFixed(2).replace('0.','.').replace('-.','−.')`: −.75, .25, −1.00. */
private fun atomLabel(z: Double): String {
    val s = (if (z < 0) "-" else "") + Rb.fixed(abs(z), 2)
    return s.replaceFirst("0.", ".").replaceFirst("-.", "−.")
}

private fun c51Lab(): RbLab {
    val lg = listOf("#e5337a" to "Risky", "#22a06b" to "Safe")
    return RbLab(listOf("P(+1) 0.5", "0.65", "0.8"), 0) { opt ->
        val p = listOf(.5, .65, .8)[opt]
        val atoms = listOf(-1.0, -.75, -.5, -.25, 0.0, .25, .5, .75, 1.0)
        val mean = 2 * p - 1
        val safe = .3
        val risky = atoms.map { if (it == -1.0) 1 - p else if (it == 1.0) p else 0.0 }
        val w = (.5 - safe) / .25
        val safeD = atoms.indices.map { if (it == 5) w else if (it == 6) 1 - w else 0.0 }
        fun cvar(dist: List<Double>): Double {
            var m = 0.0
            var acc = 0.0
            var i = 0
            while (i < 9 && acc < .25) {
                val take = min(dist[i], .25 - acc)
                m += take * atoms[i]
                acc += take
                i++
            }
            return m / .25
        }
        fun vb(a: List<Double>, b: List<Double>) = RbBlock.Cols(atoms.mapIndexed { i, z ->
            RbVCol("", atomLabel(z), "#9aa0ae", listOf(RbVBar(a[i] * 58, "#e5337a"), RbVBar(b[i] * 58, "#22a06b")))
        })
        List(4) { stp ->
            when (stp) {
                0 -> frame(listOf(B(listOf(brow("risky: E[return]", mean, 1.0), brow("safe: E[return]", safe, 1.0)))),
                    listOf(F("risky: ±1 with P(+1) = ${js(p)}"), F("safe: always 0.30")),
                    "DQN sees only these two numbers." to (if (abs(mean - safe) < .01) "Equal expectations: a plain Q-network can’t tell them apart." else "It picks ${if (mean > safe) "risky" else "safe"} on the mean alone."), lg)
                1 -> frame(listOf(L("P(return) on 9 atoms, −1 to +1"), vb(risky, safeD)), listOf(F("C51 keeps a probability per atom")),
                    "Same axis, different worlds." to "Risky is two spikes at the ends; safe is one lump in the middle.", lg)
                2 -> frame(listOf(L("safe return 0.30 projected onto the grid"), vb(List(9) { 0.0 }, safeD)),
                    listOf(F("0.30 between 0.25 and 0.50 →", "${f(w, 2)} / ${f(1 - w, 2)}", "#22a06b")),
                    "Returns off the grid are split between neighbours." to "The same projection is applied to r + γ·z after every Bellman backup.", lg)
                else -> frame(listOf(B(listOf(brow("risky · CVaR 25%", cvar(risky), 1.0, f(cvar(risky), 2)), brow("safe · CVaR 25%", cvar(safeD), 1.0, f(cvar(safeD), 2))))),
                    listOf(F("mean of the worst 25% of outcomes")),
                    "The distribution enables risk-aware choices." to "A cautious agent picks safe even when risky has the higher mean — impossible with a single Q value.", lg)
            }
        }
    }
}

// ── 59h Rainbow ──

private fun rainbowLab(): RbLab {
    val lg = listOf("#f5c542" to "This fix", "#22a06b" to "Covered")
    return RbLab(emptyList(), 0) { _ ->
        val d = rbDdq
        val n = rbNoisy
        val rows = listOf(
            Triple("Double", "overestimation", "peak Q(A,left) ${f(d[0].peak, 3)} → ${f(d[1].peak, 3)}"),
            Triple("Replay", "wasted samples", "error @200 steps ${f(chainErr(rbRepRun(0).getValue(200)), 2)} → ${f(chainErr(rbRepRun(16).getValue(200)), 2)}"),
            Triple("Dueling", "relearning V per action", "V learned from every action"),
            Triple("Noisy", "dithering exploration", "steps to goal ${f(n.eg.mean, 1)} → ${f(n.nz.mean, 1)}"),
            Triple("C51", "averaging away risk", "full return distribution"),
            Triple("Multi-step", "slow reward propagation", "n-step targets"),
        )
        List(8) { stp ->
            val cur = if (stp in 1..6) stp - 1 else null
            val blocks = mutableListOf<RbBlock>(L("one fix per failure, measured on this page"), C(rows.mapIndexed { i, r ->
                tok(r.first, if (cur == i) "cur" else if (cur != null && i < cur) "done" else if (stp == 7) "done" else "fut", r.second)
            }))
            if (cur != null) blocks += S(st(rows[cur].first, rows[cur].third, "#22a06b"))
            val fx = if (cur != null) listOf(F(rows[cur].second + ":", rows[cur].third)) else listOf(F("Rainbow = DQN + all six"))
            val cap = when {
                stp == 0 -> "Rainbow combines six independent fixes." to "Each targets a different failure of plain DQN — the previous screens measured most of them."
                stp == 7 -> "Not all fixes matter equally." to "In the Rainbow paper’s ablations, prioritized replay and multi-step returns were the most important."
                else -> "${rows[cur!!].first} fixes ${rows[cur].second}." to rows[cur].third + "."
            }
            frame(blocks, fx, cap, lg)
        }
    }
}
