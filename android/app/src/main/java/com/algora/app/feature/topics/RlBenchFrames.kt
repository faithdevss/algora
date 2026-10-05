package com.algora.app.feature.topics

import com.algora.app.feature.topics.Rb.B
import com.algora.app.feature.topics.Rb.C
import com.algora.app.feature.topics.Rb.F
import com.algora.app.feature.topics.Rb.L
import com.algora.app.feature.topics.Rb.S
import com.algora.app.feature.topics.Rb.brow
import com.algora.app.feature.topics.Rb.f
import com.algora.app.feature.topics.Rb.frame
import com.algora.app.feature.topics.Rb.pct
import com.algora.app.feature.topics.Rb.pt
import com.algora.app.feature.topics.Rb.row
import com.algora.app.feature.topics.Rb.st
import com.algora.app.feature.topics.Rb.tok
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign
import kotlin.math.sin

// ── Famous-benchmark storyboard frames ───────────────────────────────────────
// Value iteration on the classic grid world, CartPole, Mountain Car and the pendulum on their real
// equations, a toy Pong that shows why Atari agents stack frames, league training on a
// rush–expand–defend game, and factorised action heads trained against a flat softmax. Drawn by
// RlBoardLabs.kt; the iOS port (RlBenchFrames.swift) matches it.

internal val rlBenchTopicIds = setOf("grid_world", "cartpole", "mountain_car", "atari", "mujoco", "starcraft", "dota2")

internal fun rlBenchLab(topicId: String): RbLab? = when (topicId) {
    "grid_world" -> gridWorldLab()
    "cartpole" -> cartPoleLab()
    "mountain_car" -> mountainCarLab()
    "atari" -> atariLab()
    "mujoco" -> mujocoLab()
    "starcraft" -> starcraftLab()
    "dota2" -> dotaLab()
    else -> null
}

// ── 63a The classic grid world: 80/10/10 slips, γ = 1, goal cell 3, pit 7, wall 5, start 12 ──

private val GWA = arrayOf(intArrayOf(-1, 0), intArrayOf(0, 1), intArrayOf(1, 0), intArrayOf(0, -1))
private const val GW_AR = "↑→↓←"

private fun gwMv(i: Int, a: Int): Int {
    val r = i / 4 + GWA[a][0]
    val c = i % 4 + GWA[a][1]
    if (r < 0 || r > 3 || c < 0 || c > 3) return i
    val j = r * 4 + c
    return if (j == 5) i else j
}

private fun gwQ(v: DoubleArray, i: Int, a: Int, c: Double): Double =
    listOf(a to .8, (a + 1) % 4 to .1, (a + 3) % 4 to .1).fold(0.0) { s, (b, p) -> s + p * (c + v[gwMv(i, b)]) }

private class Gw(val sw: List<DoubleArray>, val pol: IntArray, val v: DoubleArray)

private val gwCache = HashMap<Double, Gw>()

private fun gw(c: Double): Gw = synchronized(gwCache) {
    gwCache.getOrPut(c) {
        var v = DoubleArray(16)
        v[3] = 1.0
        v[7] = -1.0
        val sw = arrayListOf(v.copyOf())
        for (n in 0 until 300) {
            val o = v
            val nv = DoubleArray(16) { i -> if (i == 3 || i == 7 || i == 5) o[i] else (0..3).maxOf { a -> gwQ(o, i, a, c) } }
            val d = (0 until 16).maxOf { abs(nv[it] - o[it]) }
            v = nv
            sw += v.copyOf()
            if (d < 1e-4) break
        }
        val fin = v
        val pol = IntArray(16) { i -> (0..3).fold(0) { b, a -> if (gwQ(fin, i, a, c) > gwQ(fin, i, b, c) + 1e-9) a else b } }
        Gw(sw, pol, fin)
    }
}

private fun gwGrid(v: DoubleArray, pol: IntArray? = null, hl: List<Int>? = null): RbBlock.Grid {
    val mx = (0 until 16).filter { it != 3 && it != 7 }.maxOf { abs(v[it]) }.let { if (it == 0.0) 1.0 else it }
    return RbBlock.Grid(52, (0..3).map { "c$it" }, (0..3).map { r ->
        RbGRow("r$r", "#9aa0ae", (0..3).map { c ->
            val i = r * 4 + c
            when (i) {
                5 -> RbCell("", "#3a3f4c", "#fff")
                3 -> RbCell("+1", "#22a06b", "#fff")
                7 -> RbCell("−1", "#e5337a", "#fff")
                else -> {
                    val x = v[i]
                    val a = Rb.a2(.1 + .6 * min(1.0, abs(x) / mx))
                    RbCell(
                        (if (pol != null) "${GW_AR[pol[i]]} " else "") + f(x) + (if (i == 12) " S" else ""),
                        if (abs(x) < 1e-9) "#1f232d" else if (x > 0) "rgba(59,130,246,$a)" else "rgba(229,51,122,$a)", "#fff",
                        if (hl != null && i in hl) "inset 0 0 0 2px #f5c542" else "none",
                    )
                }
            }
        })
    })
}

private fun gridWorldLab(): RbLab {
    val lg = listOf("#22a06b" to "Goal +1", "#e5337a" to "Pit −1", "#3b82f6" to "Value", "#f5c542" to "Highlighted")
    return RbLab(listOf("cost −0.04", "cost −0.4", "cost −2"), 0) { opt ->
        val c = listOf(-.04, -.4, -2.0)[opt]
        val g = gw(c)
        val n = g.sw.size - 1
        List(5) { stp ->
            when (stp) {
                0 -> frame(listOf(L("state value V(s) · sweep 0"), gwGrid(g.sw[0])), listOf(F("step cost =", f(c)), F("moves =", "80% as intended, 10% each side")),
                    "The classic grid world, before any planning." to "Only the terminals have values. Moves slip sideways one time in five, so the pit is a risk even when you aim past it.", lg)
                1 -> {
                    val p = g.sw[2]
                    val q = g.sw[3]
                    val a = (0..3).fold(0) { b, x -> if (gwQ(p, 2, x, c) > gwQ(p, 2, b, c)) x else b }
                    val nx = gwMv(2, a)
                    val l = gwMv(2, (a + 3) % 4)
                    val r = gwMv(2, (a + 1) % 4)
                    frame(listOf(L("V(s) after sweep 3"), gwGrid(q, null, listOf(2))),
                        listOf(F("V(r0,c2) = 0.8·(${f(c)} + ${f(p[nx])}) + 0.1·(${f(c)} + ${f(p[l])}) + 0.1·(${f(c)} + ${f(p[r])}) =", f(q[2]), "#f5c542")),
                        "Value spreads out from the goal one cell per sweep." to "Next to the goal, aiming ${GW_AR[a]} is worth ${f(q[2])}; cells three moves away still hold only the step cost.", lg)
                }
                2 -> frame(listOf(L("V(s) converged · $n sweeps"), gwGrid(g.v, null, listOf(12))), listOf(F("V(start) =", f(g.v[12]), "#3b82f6"), F("stop when max change <", "0.0001")),
                    "After $n sweeps nothing changes." to "Every value now prices the whole future from that cell, slips and step costs included.", lg)
                3 -> frame(listOf(L("greedy policy on the converged values"), gwGrid(g.v, g.pol, listOf(6, 10, 11))), listOf(F("π(s) = argmax_a Σ p(s′|s,a)·(r + V(s′))")),
                    when (opt) {
                        0 -> "Near the pit, the policy takes the long way." to "From r2,c2 it heads ${GW_AR[g.pol[10]]} and from r1,c2 ${GW_AR[g.pol[6]]}: with steps this cheap, avoiding a 10% slip into −1 is worth the detour."
                        1 -> "Steps cost more, so the detours shrink." to "From r2,c2 the policy now heads ${GW_AR[g.pol[10]]}."
                        else -> "At −2 a step, the pit is the better deal." to "From r1,c2 the policy heads ${GW_AR[g.pol[6]]} — straight into −1 — because two more steps would cost more."
                    }, lg)
                else -> {
                    val cs = listOf(-.04, -.4, -2.0)
                    val sc = abs(gw(-2.0).v[12])
                    frame(listOf(
                        L("policy at r1,c2 — next to the pit"),
                        C(cs.map { x -> val gg = gw(x); tok(f(x) + " · " + GW_AR[gg.pol[6]], if (gwMv(6, gg.pol[6]) == 7) "err" else "done") }),
                        L("V(start) by step cost"),
                        B(cs.map { x -> brow("cost ${f(x)}", gw(x).v[12], sc, null, if (x == c) "#f2f3f7" else "#9aa0ae") }),
                    ), listOf(F("same grid, same slips, only r changes")),
                        "The reward defines the behaviour." to "Change one number — the step cost — and the optimal plan goes from cautious to suicidal.", lg)
                }
            }
        }
    }
}

// ── 63b CartPole on the Gym equations ──

private class CpState(val x: Double, val xd: Double, val th: Double, val td: Double)

private class Cp(val tr: List<CpState>, val t: Int)

private val cpCache = HashMap<String, Cp>()

private fun cp(k: Int, seed: Int): Cp = synchronized(cpCache) {
    cpCache.getOrPut("${k}_$seed") {
        val r = Rb.rng(seed.toLong())
        var x = (r() - .5) * .1
        var xd = (r() - .5) * .1
        var th = (r() - .5) * .1
        var td = (r() - .5) * .1
        val tr = arrayListOf(CpState(x, xd, th, td))
        var tt = 500
        for (t in 0 until 500) {
            val a = when (k) {
                0 -> if (r() < .5) 1 else 0
                1 -> if (th > 0) 1 else 0
                else -> if (th + .5 * td > 0) 1 else 0
            }
            val force = if (a == 1) 10.0 else -10.0
            val ct = cos(th)
            val sn = sin(th)
            val tmp = (force + .05 * td * td * sn) / 1.1
            val ta = (9.8 * sn - ct * tmp) / (.5 * (4.0 / 3 - .1 * ct * ct / 1.1))
            val xa = tmp - .05 * ta * ct / 1.1
            x += .02 * xd
            xd += .02 * xa
            th += .02 * td
            td += .02 * ta
            tr += CpState(x, xd, th, td)
            if (abs(x) > 2.4 || abs(th) > 12 * PI / 180) { tt = t + 1; break }
        }
        Cp(tr, tt)
    }
}

private fun cpScene(o: CpState): RbBlock.Plot {
    val w = 320.0
    val h = 150.0
    val cx = 160 + max(-2.4, min(2.4, o.x)) / 2.4 * 140
    val cy = 112.0
    val l = 72.0
    val tx = cx + l * sin(o.th)
    val ty = cy - l * cos(o.th)
    fun px(x: Double) = x / w * 100
    fun py(y: Double) = y / h * 100
    val pts = ArrayList<RbPt>()
    for (i in 0..40) pts += pt(px(20 + 280.0 * i / 40), py(124.0), 3.0, "#3a3f4c")
    listOf(-2.4, 2.4).forEach { b -> pts += pt(px(160 + b / 2.4 * 140), py(124.0), 8.0, "#e5337a") }
    pts += Rb.dline(px(cx), py(cy), px(tx), py(ty), 10, 5.0, "#22a06b")
    listOf(-14.0, 0.0, 14.0).forEach { d -> pts += pt(px(cx + d), py(cy + 2), 16.0, "#3b82f6") }
    pts += pt(px(tx), py(ty), 11.0, "#f5c542")
    return Rb.scene(150, pts)
}

private fun cartPoleLab(): RbLab {
    val lg = listOf("#3b82f6" to "Cart", "#22a06b" to "Pole / balanced", "#f5c542" to "Tip / angle", "#e5337a" to "Limit")
    fun deg(x: Double) = f(x * 180 / PI, 1) + "°"
    return RbLab(listOf("random", "lean", "lean + spin"), 1) { k ->
        val pn = listOf("random", "lean", "lean + spin")[k]
        val o = cp(k, 1)
        List(5) { stp ->
            when (stp) {
                0 -> frame(listOf(cpScene(o.tr[0]), C(listOf(tok("state [x, ẋ, θ, θ̇]"), tok("2 actions"), tok("50 Hz")))),
                    listOf(F("fail if |θ| > 12° or |x| > 2.4 m"), F("reward =", "+1 per surviving step · max 500")),
                    "Four numbers are the whole state." to "Cart position and velocity, pole angle and spin. The only actions are full push left or right, ±10 N.", lg)
                1 -> {
                    val t = min(o.t, if (k == 2) 120 else max(1, o.t - 3))
                    val p = o.tr[t]
                    frame(listOf(cpScene(p), C(listOf(tok("step $t", "cur"), tok("θ ${deg(p.th)}", if (abs(p.th) > .15) "err" else "plain"), tok("θ̇ ${f(p.td)}"), tok("x ${f(p.x)} m")))),
                        listOf(F("$pn rule =", listOf("coin flip", "push toward the lean", "push toward θ + 0.5·θ̇")[k]), F("episode length =", "${o.t} steps", if (o.t == 500) "#22a06b" else "#e5337a")),
                        when (k) {
                            0 -> "Random pushes drop the pole in ${o.t} steps." to "It isn’t pushed over; it is simply never caught."
                            1 -> "Pushing toward the lean fails at step ${o.t}." to "It reacts to where the pole is, not where it is going, so every correction overshoots a little more."
                            else -> "Adding spin to the rule balances indefinitely." to "At step $t the pole is at ${deg(p.th)} and the cart ${f(p.x)} m from centre; it lasts the full 500."
                        }, lg)
                }
                2 -> {
                    val n = min(o.tr.size, 150)
                    val v = o.tr.take(n).map { it.th * 180 / PI }
                    frame(listOf(L("pole angle θ, first ${n - 1} steps · red = ±12°"),
                        Rb.chart(listOf(Rb.Series(v, if (k == 2) "#22a06b" else "#f5c542", 3.0)), 130, -14.0, 14.0, listOf(12.0 to "#e5337a", -12.0 to "#e5337a", 0.0 to "#3a3f4c"))),
                        listOf(F("max |θ| =", deg(o.tr.take(n).maxOf { abs(it.th) }))),
                        when (k) {
                            1 -> "The wobble grows every swing." to "Each push arrives a little late, adding energy instead of removing it, until the angle crosses 12°."
                            2 -> "The wobble dies down." to "Leading the angle by its spin damps the oscillation — a PD controller in one line."
                            else -> "No pattern, just drift." to "Random pushes cancel on average, so the pole falls as if unattended."
                        }, lg)
                }
                3 -> {
                    val s = (0..2).map { j -> var t = 0.0; for (i in 1..50) t += cp(j, i).t / 50.0; t }
                    frame(listOf(L("mean episode length · 50 random starts"), B(listOf("random", "lean", "lean + spin").mapIndexed { j, n ->
                        row(n, f(s[j], 1), s[j] / 500, if (j == 2) "#22a06b" else "#3b82f6", if (j == k) "#f2f3f7" else "#9aa0ae")
                    })), listOf(F("solved =", "average ≥ 475 over 100 episodes", "#22a06b")),
                        "One extra term separates ${f(s[1], 0)} steps from 500." to "That is why CartPole is a first benchmark: a learner has to discover the spin term from reward alone.", lg)
                }
                else -> frame(listOf(S(st("observations", "4 continuous numbers"), st("actions", "2 discrete"), st("dynamics", "Euler step, τ = 0.02 s"), st("typical solve", "DQN or PPO in minutes on a CPU", "#22a06b"))),
                    listOf(F("θ̈ = (g·sinθ − cosθ·(F + m_p·l·θ̇²·sinθ)/m) / (l·(4/3 − m_p·cos²θ/m))")),
                    "Small, fast, unforgiving." to "Every curve on this screen came from the same equation the Gym environment uses.", lg)
            }
        }
    }
}

// ── 63c Mountain Car ──

private class McPt(val p: Double, val v: Double)

private class Mc(val tr: List<McPt>, val t: Int, val ok: Boolean, val best: Double)

private val mcCache = HashMap<String, Mc>()

private fun mc(k: Int, p0: Double, seed: Int): Mc = synchronized(mcCache) {
    mcCache.getOrPut("${k}_${p0}_$seed") {
        val r = Rb.rng(seed * 7L + 1)
        var p = p0
        var v = 0.0
        var best = p
        val tr = arrayListOf(McPt(p, v))
        var tt = 200
        var ok = false
        for (t in 0 until 200) {
            val a = when (k) { 0 -> 2; 1 -> floor(r() * 3).toInt(); else -> if (v >= 0) 2 else 0 }
            v += (a - 1) * .001 - .0025 * cos(3 * p)
            v = max(-.07, min(.07, v))
            p += v
            if (p < -1.2) { p = -1.2; v = 0.0 }
            best = max(best, p)
            tr += McPt(p, v)
            if (p >= .5) { tt = t + 1; ok = true; break }
        }
        Mc(tr, tt, ok, best)
    }
}

private fun mcScene(tr: List<McPt>, t: Int): RbBlock.Plot {
    fun x(p: Double) = 5 + 90 * (p + 1.2) / 1.8
    fun y(p: Double) = 52 - 36 * sin(3 * p)
    val pts = ArrayList<RbPt>()
    for (i in 0..60) { val p = -1.2 + 1.8 * i / 60; pts += pt(x(p), y(p) + 6, 3.0, "#3a3f4c") }
    pts += pt(x(.5), y(.5) - 4, 12.0, "#22a06b")
    var j = max(0, t - 60)
    while (j < t) { pts += pt(x(tr[j].p), y(tr[j].p) + 2, 4.0, "rgba(245,197,66,.45)"); j += 3 }
    pts += pt(x(tr[t].p), y(tr[t].p) + 1, 15.0, "#3b82f6")
    return Rb.scene(150, pts)
}

private fun mountainCarLab(): RbLab {
    val lg = listOf("#3b82f6" to "Car", "#f5c542" to "Trail", "#22a06b" to "Flag")
    return RbLab(listOf("push right", "random", "pump"), 0) { k ->
        val o = mc(k, -.5, 1)
        List(5) { stp ->
            when (stp) {
                0 -> frame(listOf(mcScene(o.tr, 0), S(st("actions", "push left · none · push right"), st("engine", "0.001 per step"), st("gravity", "up to 0.0025 per step", "#e5337a"), st("reward", "−1 per step until the flag"))),
                    listOf(F("v ← v + 0.001·(a − 1) − 0.0025·cos(3p)")),
                    "The engine is weaker than the hill." to "The car starts in the valley at −0.50 and has 200 steps to reach the flag at 0.50.", lg)
                1 -> {
                    val t = o.tr.lastIndex
                    val p = o.tr[t]
                    frame(listOf(mcScene(o.tr, t), C(listOf(tok("step $t", "cur"), tok("position ${f(p.p)}"), tok("best ${f(o.best)}", if (o.ok) "done" else "plain")))),
                        listOf(F("reached the flag:", if (o.ok) "yes, step ${o.t}" else "no", if (o.ok) "#22a06b" else "#e5337a")),
                        when (k) {
                            0 -> "Pushing at the goal never gets there." to "After 200 steps the car sits at ${f(p.p)} with velocity ${f(p.v, 4)}. It climbs to ${f(o.best)}, stalls, and settles."
                            1 -> "Random pushes go nowhere." to "Best position ${f(o.best)}. The pushes average out, so the car only jiggles in the valley."
                            else -> "Rocking back and forth reaches the flag in ${o.t} steps." to "Push in whichever direction the car is already moving: each swing goes higher than the last."
                        }, lg)
                }
                2 -> frame(listOf(L("position over time · green = flag"), Rb.chart(listOf(Rb.Series(o.tr.map { it.p }, if (o.ok) "#22a06b" else "#f5c542", 3.0)), 130, -1.2, .6, listOf(.5 to "#22a06b", -.5 to "#3a3f4c"))),
                    listOf(F("steps =", "${o.t}", if (o.ok) "#22a06b" else "#e5337a")),
                    if (k == 2) "Each swing is bigger than the last." to "The car backs up the left slope to build speed, then carries it over the right."
                    else "The trace flattens out." to "No energy is being added, so the car never gets more than part-way up.", lg)
                3 -> {
                    val starts = listOf(-.6, -.58, -.56, -.54, -.52, -.5, -.48, -.46, -.44, -.42, -.4)
                    fun okc(j: Int) = starts.indices.count { mc(j, starts[it], it + 1).ok }
                    val ms = starts.indices.sumOf { mc(2, starts[it], it + 1).t.toDouble() } / starts.size
                    frame(listOf(L("reached the flag · 11 starts from −0.60 to −0.40"), B(listOf("push right", "random", "pump").mapIndexed { j, n ->
                        val c = okc(j)
                        row(n, "$c / 11", c / 11.0, if (j == 2) "#22a06b" else "#e5337a", if (j == k) "#f2f3f7" else "#9aa0ae")
                    })), listOf(F("pump · mean steps =", f(ms, 0), "#22a06b")),
                        "Only the counter-intuitive policy works." to "Pumping reaches the flag from every start, in about ${f(ms, 0)} steps.", lg)
                }
                else -> frame(listOf(S(st("random reward seen", "never in 200 steps"), st("signal before success", "−1, −1, −1 … identical", "#e5337a"), st("what helps", "optimistic init, exploration bonus, shaping", "#22a06b"))),
                    listOf(F("every failed episode returns exactly", "−200")),
                    "A sparse-reward trap." to "Until the car hits the flag once, every episode scores −200 — there is nothing to tell good attempts from bad.", lg)
            }
        }
    }
}

// ── 63d Atari: a toy Pong, 8 wide with 4 rows to fall ──

private class Pong(val curve: List<Pair<Int, Double>>, val q: Map<String, DoubleArray>)

private val pongCache = HashMap<Int, Pong>()

private fun pong(frames: Int): Pong = synchronized(pongCache) {
    pongCache.getOrPut(frames) {
        val r = Rb.rng(1)
        val q = HashMap<String, DoubleArray>()
        fun qa(k: String) = q.getOrPut(k) { DoubleArray(3) }
        fun key(bx: Int, by: Int, px: Int, pbx: Int) = if (frames == 1) "$bx,$by,$px" else "$bx,$by,$px,$pbx"
        fun ep(greedy: Boolean): Boolean {
            var bx = floor(r() * 8).toInt()
            var dx = if (r() < .5) -1 else 1
            var by = 0
            var px = floor(r() * 8).toInt()
            var pbx = bx - dx
            for (t in 0 until 4) {
                val qs = qa(key(bx, by, px, pbx))
                val a: Int
                if (!greedy && r() < .1) a = floor(r() * 3).toInt()
                else {
                    var b = 0
                    for (i in 1 until 3) if (qs[i] > qs[b] + 1e-12) b = i
                    a = b
                }
                px = max(0, min(7, px + a - 1))
                pbx = bx
                bx += dx
                if (bx < 0) { bx = 1; dx = 1 }
                if (bx > 7) { bx = 6; dx = -1 }
                by++
                val done = by == 4
                val rw = if (done) (if (bx == px) 1.0 else -1.0) else 0.0
                if (!greedy) qs[a] += .2 * (rw + (if (done) 0.0 else qa(key(bx, by, px, pbx)).max()) - qs[a])
                if (done) return rw > 0
            }
            return false
        }
        val curve = ArrayList<Pair<Int, Double>>()
        for (e in 1..3000) {
            ep(false)
            if (e == 100 || e == 300 || e == 1000 || e == 3000) {
                var w = 0
                repeat(400) { if (ep(true)) w++ }
                curve += e to w / 400.0
            }
        }
        Pong(curve, q)
    }
}

private val pongBound: Double by lazy {
    var c = 0
    var n = 0
    for (bx in 0 until 8) for (dx in listOf(-1, 1)) for (px in 0 until 8) {
        var x = bx
        var d = dx
        repeat(4) {
            x += d
            if (x < 0) { x = 1; d = 1 }
            if (x > 7) { x = 6; d = -1 }
        }
        n++
        if (abs(x - px) <= 4) c++
    }
    c.toDouble() / n
}

private fun pgGrid(ball: Pair<Int, Int>?, prev: Pair<Int, Int>?, pad: Int, cand: List<Pair<Int, Int>>?) = RbBlock.Grid(30, List(8) { "" }, (0..4).map { r ->
    RbGRow("", "#9aa0ae", (0..7).map { c ->
        val isB = ball == r to c
        val isP = prev == r to c
        val isPad = r == 4 && c == pad
        val isC = cand?.contains(r to c) == true
        RbCell("", if (isB) "#f5c542" else if (isP) "rgba(245,197,66,.35)" else if (isPad) "#22a06b" else "#2a2e39", "#fff", if (isC) "inset 0 0 0 2px #6d5dfc" else "none")
    })
})

private fun atariLab(): RbLab {
    val lg = listOf("#f5c542" to "Ball", "#22a06b" to "Paddle / 2 frames", "#6d5dfc" to "Possible next")
    val mvn = listOf("left", "stay", "right")
    return RbLab(listOf("1 frame", "2 frames"), 0) { opt ->
        val fr = listOf(1, 2)[opt]
        val p1 = pong(1)
        val p2 = pong(2)
        val ub = pongBound
        List(5) { stp ->
            when (stp) {
                0 -> frame(listOf(L("one frame: ball at r1,c3 · paddle at c4"), pgGrid(1 to 3, null, 4, listOf(2 to 2, 2 to 4))), listOf(F("possible next ball cells =", "2")),
                    "A single frame is a position with no velocity." to "From this picture alone the ball could be heading left or right — both outlined cells are equally likely.", lg)
                1 -> frame(listOf(L("stack the previous frame: faded = one step ago"), pgGrid(2 to 4, 1 to 3, 4, null)), listOf(F("ball c3 → c4 ·", "moving right", "#22a06b")),
                    "Two frames make the direction visible." to "The difference between frames is the velocity — the reason DQN feeds the network a stack of recent frames.", lg)
                2 -> frame(listOf(L("catch rate during Q-learning · 400 test serves"), B(p1.curve.flatMapIndexed { i, (e, w) ->
                    listOf(
                        row("$e ep · 1 frame", pct(w), w, "#9aa0ae", if (fr == 1) "#f2f3f7" else "#9aa0ae"),
                        row("$e ep · 2 frames", pct(p2.curve[i].second), p2.curve[i].second, "#22a06b", if (fr == 2) "#f2f3f7" else "#9aa0ae"),
                    )
                }.drop(2))), listOf(F("reachable serves =", pct(ub) + " · paddle moves 1 cell per step")),
                    "One frame stalls at ${pct(p1.curve[3].second)}; two frames reach ${pct(p2.curve[3].second)}." to "Same learner, same 3,000 episodes — the only difference is whether the input contains the direction.", lg)
                3 -> {
                    val qs = p1.q["3,1,4"] ?: DoubleArray(3)
                    val q2 = p2.q["3,1,4,2"] ?: DoubleArray(3)
                    val q3 = p2.q["3,1,4,4"] ?: DoubleArray(3)
                    frame(listOf(
                        L("1-frame Q at the opening state"), B(mvn.mapIndexed { i, n -> brow(n, qs[i], 1.0) }),
                        L("2-frame Q · ball moving right / left"), B(mvn.flatMapIndexed { i, n -> listOf(brow("$n · →", q2[i], 1.0), brow("$n · ←", q3[i], 1.0)) }),
                    ), listOf(F("1-frame best =", mvn[Rb.argmax(qs)]), F("2-frame best · → / ← =", mvn[Rb.argmax(q2)] + " / " + mvn[Rb.argmax(q3)], "#22a06b")),
                        "Without the direction, values average two different situations." to "With the previous frame the agent picks a different move for each direction — the 1-frame agent has to hedge.", lg)
                }
                else -> frame(listOf(S(st("input", "84 × 84 grayscale"), st("stack", "last 4 frames"), st("actions", "up to 18 joystick moves"), st("frame skip", "repeat each action for 4 frames"))),
                    listOf(F("observation =", "4 × 84 × 84 tensor")),
                    "Partial observability, fixed with history." to "Frame stacking turns a POMDP back into something close enough to an MDP for Q-learning.", lg)
            }
        }
    }
}

// ── 63e MuJoCo: pendulum swing-up on the Gym dynamics ──

private fun pn(th: Double): Double = ((th + PI) % (2 * PI) + 2 * PI) % (2 * PI) - PI

private class PdPt(val a: Double, val w: Double, val u: Double)

private class Pend(val tr: List<PdPt>, val cost: Double, val up: Int)

private val pdCache = HashMap<Int, Pend>()

private fun pend(k: Int): Pend = synchronized(pdCache) {
    pdCache.getOrPut(k) {
        var th = PI
        var w = 0.0
        val tr = arrayListOf(PdPt(PI, 0.0, 0.0))
        var cost = 0.0
        var up = -1
        for (t in 0 until 200) {
            val a = pn(th)
            var u = if (k == 0 || abs(a) < .5) -10 * a - 2 * w else (if (w >= 0) 1.0 else -1.0) * 2 * sign(5 - (w * w / 6 + 5 * cos(a)))
            u = max(-2.0, min(2.0, u))
            cost += a * a + .1 * w * w + .001 * u * u
            w += (15 * sin(th) + 3 * u) * .05
            w = max(-8.0, min(8.0, w))
            th += w * .05
            val na = pn(th)
            if (up < 0 && abs(na) < .1 && abs(w) < 1) up = t + 1
            tr += PdPt(na, w, u)
        }
        Pend(tr, cost, up)
    }
}

private fun pdScene(p: PdPt): RbBlock.Plot {
    val w = 320.0
    val h = 160.0
    val cx = 160.0
    val cy = 80.0
    val rr = 56.0
    fun px(x: Double) = x / w * 100
    fun py(y: Double) = y / h * 100
    val pts = ArrayList<RbPt>()
    for (i in 0 until 48) { val g = i / 48.0 * 2 * PI; pts += pt(px(cx + rr * sin(g)), py(cy - rr * cos(g)), 3.0, "#3a3f4c") }
    pts += pt(px(cx), py(cy - rr), 14.0, "transparent", "inset 0 0 0 2px #22a06b")
    val bx = cx + rr * sin(p.a)
    val by = cy - rr * cos(p.a)
    pts += Rb.dline(px(cx), py(cy), px(bx), py(by), 10, 6.0, "#3b82f6")
    pts += pt(px(cx), py(cy), 9.0, "#f2f3f7")
    pts += pt(px(bx), py(by), 16.0, "#f5c542")
    return Rb.scene(160, pts)
}

private fun mujocoLab(): RbLab {
    val lg = listOf("#f5c542" to "Bob", "#3b82f6" to "Arm", "#22a06b" to "Upright target")
    fun deg(a: Double) = f(abs(a) * 180 / PI, 0) + "°"
    return RbLab(listOf("push to top", "energy pump"), 1) { k ->
        val o = pend(k)
        List(5) { stp ->
            when (stp) {
                0 -> frame(listOf(pdScene(o.tr[0]), S(st("action", "torque u ∈ [−2, 2] · continuous"), st("gravity at 90°", "15 rad/s²"), st("motor at full torque", "6 rad/s²", "#e5337a"))),
                    listOf(F("ω̇ = 15·sinθ + 3·u")),
                    "The motor cannot lift the arm directly." to "At full torque it supplies 6 rad/s² against gravity’s 15 at horizontal — the same trap as Mountain Car, with a continuous action.", lg)
                1 -> {
                    val p = o.tr[26]
                    frame(listOf(pdScene(p), C(listOf(tok("step 26", "cur"), tok(deg(p.a) + " from upright"), tok("ω ${f(p.w, 1)} rad/s"), tok("u ${f(p.u)}")))),
                        listOf(if (k == 1) F("u = 2 · sign(ω) · sign(E* − E) =", f(p.u), "#f5c542") else F("u = clip(−10·θ − 2·ω) =", f(p.u), "#f5c542")),
                        if (k == 0) "Pushing straight at the top just saturates." to "At step 26 the arm is ${deg(p.a)} from upright with the motor pinned at ${f(p.u)} — it rises a little and falls back."
                        else "Energy pumping: push the way the arm is already swinging." to "At step 26 the arm is ${deg(p.a)} from upright, swinging at ${f(p.w, 1)} rad/s, with ${f(p.u)} applied while energy is below the upright level.", lg)
                }
                2 -> frame(listOf(L("angle from upright, 200 steps (10 s)"), Rb.chart(listOf(Rb.Series(o.tr.map { abs(it.a) * 180 / PI }, if (k == 1) "#22a06b" else "#f5c542", 3.0)), 130, 0.0, 180.0, listOf(0.0 to "#22a06b"))),
                    listOf(F("upright and still at step", if (o.up > 0) "${o.up}" else "never", if (o.up > 0) "#22a06b" else "#e5337a")),
                    if (k == 1) "Swings grow until a PD catch at the top." to "Within 0.5 rad the controller switches to balancing; the arm is upright at step ${o.up}."
                    else "It never gets past ${deg(o.tr.minOf { abs(it.a) })}." to "Without building momentum the arm oscillates near the bottom forever.", lg)
                3 -> {
                    val a = pend(0)
                    val b = pend(1)
                    frame(listOf(L("Gym cost Σ θ² + 0.1·ω² + 0.001·u² · lower is better"), B(listOf(
                        row("push to top", f(a.cost, 0), 1.0, "#e5337a", if (k == 0) "#f2f3f7" else "#9aa0ae"),
                        row("energy pump", f(b.cost, 0), b.cost / a.cost, "#22a06b", if (k == 1) "#f2f3f7" else "#9aa0ae"),
                    ))), listOf(F("cost ratio =", f(b.cost / a.cost, 2), "#22a06b")),
                        "Swinging away first costs less overall." to "Short-term cost goes up while the arm swings; it pays back once it is held upright.", lg)
                }
                else -> frame(listOf(S(st("Pendulum", "1 torque"), st("HalfCheetah", "6 joint torques"), st("Humanoid", "17 joint torques"), st("algorithms", "DDPG, TD3, SAC, PPO", "#22a06b"))),
                    listOf(F("continuous actions: no argmax over a table")),
                    "MuJoCo tasks are this, with more joints." to "Every action is a vector of real-valued torques, which is why continuous-control methods were built around them.", lg)
            }
        }
    }
}

// ── 63f StarCraft II: league training on rush–expand–defend ──

private val SCG = mapOf(
    "symmetric" to listOf(listOf(0.0, 1.0, -1.0), listOf(-1.0, 0.0, 1.0), listOf(1.0, -1.0, 0.0)),
    "rush ×2" to listOf(listOf(0.0, 2.0, -1.0), listOf(-2.0, 0.0, 1.0), listOf(1.0, -1.0, 0.0)),
)

private class League(val ex: List<Double>, val seq: List<Int>, val counts: IntArray)

private val lgCache = HashMap<String, League>()

private fun league(g: String, full: Boolean): League = synchronized(lgCache) {
    lgCache.getOrPut(g + full) {
        val a = SCG.getValue(g)
        val counts = intArrayOf(1, 0, 0)
        val ex = ArrayList<Double>()
        val seq = arrayListOf(0)
        var last = 0
        for (t in 1..300) {
            val tot = counts.sum().toDouble()
            val opp = if (full) counts.map { it / tot } else (0..2).map { if (it == last) 1.0 else 0.0 }
            val v = (0..2).map { i -> opp.indices.fold(0.0) { s, j -> s + opp[j] * a[i][j] } }
            var b = 0
            for (i in 1 until 3) if (v[i] > v[b] + 1e-12) b = i
            counts[b]++
            last = b
            seq += b
            val t2 = counts.sum().toDouble()
            val pol = if (full) counts.map { it / t2 } else (0..2).map { if (it == b) 1.0 else 0.0 }
            ex += (0..2).maxOf { i -> -pol.indices.fold(0.0) { s, j -> s + pol[j] * a[j][i] } }
        }
        League(ex, seq, counts)
    }
}

/** A payoff grid as the exploration file draws it; integers here, so no decimals. */
private fun payGrid(m: List<List<Double>>, names: List<String>): RbBlock.Grid {
    val sc = m.flatten().maxOf { abs(it) }.let { if (it == 0.0) 1.0 else it }
    return RbBlock.Grid(34, names, m.mapIndexed { i, row ->
        RbGRow(names[i], "#9aa0ae", row.map { v ->
            val a = Rb.a2(.15 + .7 * abs(v) / sc)
            RbCell(f(v, 0), if (abs(v) < 1e-9) "#1f232d" else if (v > 0) "rgba(59,130,246,$a)" else "rgba(229,51,122,$a)", "#fff")
        })
    })
}

private fun starcraftLab(): RbLab {
    val lg = listOf("#e5337a" to "Naive self-play", "#22a06b" to "League", "#3b82f6" to "Mixture")
    val sn = listOf("rush", "expand", "defend")
    return RbLab(listOf("symmetric", "rush ×2"), 0) { opt ->
        val g = listOf("symmetric", "rush ×2")[opt]
        val n = league(g, false)
        val lgd = league(g, true)
        val ne = if (g == "symmetric") listOf(1.0 / 3, 1.0 / 3, 1.0 / 3) else listOf(.25, .25, .5)
        List(5) { stp ->
            when (stp) {
                0 -> frame(listOf(L("payoff to the row strategy"), payGrid(SCG.getValue(g), sn)), listOf(F("rush beats expand · expand beats defend · defend beats rush")),
                    "A rock-paper-scissors core, as in real-time strategy." to "No strategy is best against everything; ${if (g == "symmetric") "every win is worth 1." else "here a successful rush wins double."}", lg)
                1 -> frame(listOf(
                    L("naive self-play: best response to the latest version"),
                    C(n.seq.take(8).mapIndexed { i, a -> tok(sn[a], if (i == 7) "cur" else "plain") }),
                    Rb.chart(listOf(Rb.Series(n.ex.take(60), "#e5337a", 3.0)), 110, 0.0, 2.2, listOf(0.0 to "#22a06b")),
                ), listOf(F("exploitability stays at", f(n.ex.drop(5).min(), 1) + " – " + f(n.ex.max(), 1), "#e5337a")),
                    "Naive self-play goes in circles." to "rush → defend → expand → rush … Each version beats the last and loses to the next; it never gets harder to exploit.", lg)
                2 -> frame(listOf(L("exploitability by iteration · red naive, green league"),
                    Rb.chart(listOf(Rb.Series(n.ex.take(100), "#e5337a", 3.0), Rb.Series(lgd.ex.take(100), "#22a06b", 3.0)), 120, 0.0, 2.2, listOf(0.0 to "#3a3f4c"))),
                    listOf(F("league after 300 iterations =", f(lgd.ex[299], 3), "#22a06b")),
                    "A league best-responds to every past version." to "Exploitability falls from ${f(lgd.ex[0], 2)} to ${f(lgd.ex[299], 3)} while naive self-play keeps cycling.", lg)
                3 -> {
                    val tot = lgd.counts.sum().toDouble()
                    frame(listOf(L("league mixture after 300 iterations · Nash in label"), B(sn.mapIndexed { i, nm ->
                        val x = lgd.counts[i] / tot
                        row("$nm · ${pct(ne[i])}", pct(x), x / .6, if (abs(x - ne[i]) < .04) "#22a06b" else "#3b82f6")
                    })), listOf(F("Nash =", ne.joinToString(" / ") { pct(it) })),
                        if (g == "symmetric") "The league settles on an even mix." to "Close to ⅓ each — no single opponent can exploit it."
                        else "Rewarding rush more makes defend more common." to "Defend, rush’s counter, rises toward the 50% Nash share.", lg)
                }
                else -> frame(listOf(S(st("main agents", "play the whole league", "#22a06b"), st("main exploiters", "target the current main agents"), st("league exploiters", "find holes in the whole league"), st("frozen copies", "kept so old strategies stay covered"))),
                    listOf(F("AlphaStar: league training on top of imitation")),
                    "AlphaStar used the same idea at scale." to "Keeping old and adversarial players around stops the main agent from forgetting how to beat strategies it once mastered.", lg)
            }
        }
    }
}

// ── 63g Dota 2: factorised action heads vs a flat softmax ──

private val HEADS = listOf("action type" to 8, "target" to 10, "offset" to 6, "delay" to 9)

private val dotaCache = HashMap<String, List<Double>>()

private fun dota(nh: Int, fact: Boolean, seed: Int): List<Double> = synchronized(dotaCache) {
    dotaCache.getOrPut("${nh}_${fact}_$seed") {
        val sizes = HEADS.take(nh).map { it.second }
        val r = Rb.rng(seed.toLong())
        val cc = 4
        val tgt = List(cc) { sizes.map { n -> floor(r() * n).toInt() } }
        val jj = sizes.fold(1) { a, b -> a * b }
        val thF = if (fact) Array(cc) { Array(sizes.size) { DoubleArray(sizes[it]) } } else null
        val thJ = if (!fact) Array(cc) { DoubleArray(jj) } else null
        fun sm(v: DoubleArray): DoubleArray {
            val m = v.max()
            val e = DoubleArray(v.size) { kotlin.math.exp(v[it] - m) }
            val s = e.sum()
            return DoubleArray(v.size) { e[it] / s }
        }
        fun samp(p: DoubleArray): Int {
            val u = r()
            var a = 0.0
            for (i in p.indices) { a += p[i]; if (u < a) return i }
            return p.size - 1
        }
        fun dec(j0: Int): IntArray {
            var j = j0
            val o = IntArray(sizes.size)
            for (h in sizes.indices.reversed()) { o[h] = j % sizes[h]; j /= sizes[h] }
            return o
        }
        fun score(acts: IntArray, c: Int) = acts.indices.count { acts[it] == tgt[c][it] }.toDouble() / nh
        var base = 0.0
        val curve = ArrayList<Double>()
        for (u in 1..600) {
            val c = floor(r() * cc).toInt()
            val rw: Double
            if (fact) {
                val th = thF!![c]
                val ps = th.map { sm(it) }
                val acts = IntArray(ps.size) { samp(ps[it]) }
                rw = score(acts, c)
                val ad = rw - base
                ps.forEachIndexed { h, p -> p.forEachIndexed { i, pp -> th[h][i] += .5 * ad * ((if (i == acts[h]) 1 else 0) - pp) } }
            } else {
                val th = thJ!![c]
                val p = sm(th)
                val j = samp(p)
                rw = score(dec(j), c)
                val ad = rw - base
                for (i in 0 until jj) th[i] += .5 * ad * ((if (i == j) 1 else 0) - p[i])
            }
            base += .05 * (rw - base)
            if (u % 50 == 0) {
                var er = 0.0
                for (k in 0 until cc) {
                    if (fact) {
                        er += thF!![k].map { sm(it) }.foldIndexed(0.0) { h, s, p -> s + p[tgt[k][h]] } / nh / cc
                    } else {
                        val p = sm(thJ!![k])
                        var e = 0.0
                        for (i in 0 until jj) e += p[i] * score(dec(i), k)
                        er += e / cc
                    }
                }
                curve += er
            }
        }
        curve
    }
}

private fun dotaAvg(nh: Int, fact: Boolean): List<Double> {
    val s = DoubleArray(12)
    for (k in 1..3) dota(nh, fact, k).forEachIndexed { i, e -> s[i] += e / 3 }
    return s.toList()
}

private fun dotaLab(): RbLab {
    val lg = listOf("#22a06b" to "Factorised", "#e5337a" to "Flat softmax", "#3b82f6" to "Head size")
    return RbLab(listOf("2 heads", "3 heads", "4 heads"), 1) { opt ->
        val nh = listOf(2, 3, 4)[opt]
        val h = HEADS.take(nh)
        val jj = h.fold(1) { a, x -> a * x.second }
        val sm = h.sumOf { it.second }
        val fc = dotaAvg(nh, true)
        val fl = dotaAvg(nh, false)
        List(5) { stp ->
            when (stp) {
                0 -> {
                    val mx = h.maxOf { it.second }.toDouble()
                    frame(listOf(
                        L("one decision = one choice per head"),
                        B(h.map { (n, k) -> row(n, "$k", k / mx, "#3b82f6") }),
                        C(listOf(tok("joint actions = " + h.joinToString(" × ") { "${it.second}" } + " = " + lsNum(jj.toDouble()), "ans"))),
                    ), listOf(F("flat softmax outputs =", lsNum(jj.toDouble())), F("factorised heads output =", "$sm", "#22a06b")),
                        "A game action is several choices made together." to "A flat policy needs one output per combination; separate heads need one per option.", lg)
                }
                1 -> {
                    val hd = 512.0
                    frame(listOf(L("output-layer weights from a 512-unit core"), B(listOf(row("flat softmax", lsBig(hd * jj), 1.0, "#e5337a"), row("factorised", lsBig(hd * sm), hd * sm / (hd * jj), "#22a06b")))),
                        listOf(F("512 × ${lsNum(jj.toDouble())} vs 512 × $sm"), F("ratio =", f(jj.toDouble() / sm, 1) + "×", "#22a06b")),
                        "Factorising shrinks the output layer ${f(jj.toDouble() / sm, 0)}×." to "The gap multiplies with every head; with real game-sized heads a flat layer no longer fits in memory.", lg)
                }
                2 -> frame(listOf(L("expected reward over 600 REINFORCE updates · 3 seeds"),
                    Rb.chart(listOf(Rb.Series(listOf(0.0) + fl, "#e5337a", 4.0), Rb.Series(listOf(0.0) + fc, "#22a06b", 4.0)), 120, 0.0, 1.0, listOf(1.0 to "#3a3f4c"))),
                    listOf(F("update 600 · flat ${f(fl[11])} vs factorised", f(fc[11]), "#22a06b")),
                    "Factorised heads learn; the flat policy barely moves." to "Reward = share of heads chosen correctly. Each head gets credit separately; a flat policy has to stumble on whole combinations.", lg)
                3 -> {
                    val rows = ArrayList<RbRow>()
                    listOf(2, 3, 4).forEach { n ->
                        val a = dotaAvg(n, false)[11]
                        val b = dotaAvg(n, true)[11]
                        rows += row("$n heads · flat", f(a), a, "#e5337a", if (n == nh) "#f2f3f7" else "#9aa0ae")
                        rows += row("$n heads · factorised", f(b), b, "#22a06b", if (n == nh) "#f2f3f7" else "#9aa0ae")
                    }
                    frame(listOf(L("expected reward after 600 updates"), B(rows)), listOf(F("chance level with $nh heads ≈", f(h.sumOf { 1.0 / it.second } / nh))),
                        "Every extra head widens the gap." to "The flat policy stays near chance as the joint space grows; factorised heads only slow a little.", lg)
                }
                else -> frame(listOf(S(st("independent heads", "fast, but ignore interactions"), st("autoregressive heads", "each head sees earlier choices", "#22a06b"), st("used by", "OpenAI Five, AlphaStar"))),
                    listOf(F("π(a) = Π_h π_h(a_h | s, a_<h)")),
                    "Real agents condition later heads on earlier ones." to "Choosing the target after the action type keeps the output small while still allowing combinations that only make sense together.", lg)
            }
        }
    }
}
