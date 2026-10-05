package com.algora.app.feature.topics

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

// ── Optimiser and loss storyboard frames ─────────────────────────────────────
// Momentum, AdaGrad, RMSprop, Adam, AdamW, learning-rate schedulers, KL divergence and cross-entropy,
// drawn by DeepStoryLabs.kt. The optimisers run on the same ill-conditioned bowl ½(w₁² + 20·w₂²) or on
// fixed gradient streams; the KL fits are found by grid search. The iOS port is OptimStoryFrames.swift.

internal val optimStoryTopicIds = setOf(
    "momentum", "adagrad", "rmsprop", "adam", "adamw", "lr_schedulers", "kl_divergence", "cross_entropy_loss",
)

internal fun optimLab(topicId: String): DkLab? = when (topicId) {
    "momentum" -> momentumLab()
    "adagrad" -> adagradLab()
    "rmsprop" -> rmspropLab()
    "adam" -> adamLab()
    "adamw" -> adamwLab()
    "lr_schedulers" -> schedulerLab()
    "kl_divergence" -> klLab()
    "cross_entropy_loss" -> crossEntropyLab()
    else -> null
}

private fun n(v: Double, d: Int = 2) = dkNum(v, d)

private fun legend(ink: DkInk, label: String, style: SwatchStyle = SwatchStyle.Line) = DkLegend(ink, style, label)

private fun stepActions(frames: List<DkFrame>, action: String = "Next") =
    frames.mapIndexed { i, f -> DkFrame(f.header, f.stage, f.legend, f.formula, f.headline, f.body, f.chips, if (i == frames.lastIndex) "Start Over" else action) }

private fun logTicks(lo: Double, hi: Double): List<Pair<Double, String>> =
    (floor(hi).toInt() downTo kotlin.math.ceil(lo).toInt()).map { k -> k.toDouble() to if (k == 0) "1" else "1e" + (if (k < 0) "−" else "") + abs(k) }

/** A ratio as a headline writes it: 43×, 1.58×. */
private fun times(v: Double) = if (v >= 10) n(v, 0) else n(v, 2).trimEnd('0').trimEnd('.')

// ── The bowl ──

private const val Steep = 20.0
private fun bowl(w1: Double, w2: Double) = 0.5 * (w1 * w1 + Steep * w2 * w2)

/** Heavy-ball momentum on the bowl: v ← β·v + ∇f, w ← w − lr·v. */
private fun heavyBall(beta: Double, steps: Int = 60, lr: Double = 0.02): List<DkP> {
    var w1 = -8.0
    var w2 = 1.0
    var v1 = 0.0
    var v2 = 0.0
    val path = arrayListOf(DkP(w1, w2))
    repeat(steps) {
        v1 = beta * v1 + w1
        v2 = beta * v2 + Steep * w2
        w1 -= lr * v1
        w2 -= lr * v2
        path += DkP(w1, w2)
    }
    return path
}

// ── Momentum ──

private val betas = listOf(0.5, 0.9, 0.99)

private fun momentumLab(): DkLab = DkLab(DkControl.Tabs, tabs = betas.map { "β = ${n(it, 2).trimEnd('0').trimEnd('.')}" }, initialTab = 1) { tab, _ ->
    val b = betas[tab]
    val bT = n(b, 2).trimEnd('0').trimEnd('.')
    val base = heavyBall(0.5)
    val mine = heavyBall(b)
    val lBase = bowl(base.last().x, base.last().y)
    val lMine = bowl(mine.last().x, mine.last().y)
    val ellipses = listOf(2.0, 4.0, 6.5, 9.5).map { it to it / sqrt(Steep) }
    fun plot(showMine: Boolean) = DkPlot(
        -9.0 to 3.0, -1.7 to 1.7, emptyList(), "", "",
        listOf(DkLine(base, DkInk.Grey)) + if (showMine && tab != 0) listOf(DkLine(mine, DkInk.Blue)) else emptyList(),
        dots = listOf(DkDot(DkP(-8.0, 1.0), DkInk.Grey, 4f), DkDot(DkP(0.0, 0.0), DkInk.Green, 4.5f)) +
            DkDot((if (showMine) mine else base).last(), DkInk.Yellow, 5f),
        axis = false, ellipses = ellipses,
    )
    val header = "f = ½(w₁² + 20·w₂²) · lr 0.02 · start (−8, 1)"
    val legendAll = listOf(legend(DkInk.Grey, "β = 0.5")) + (if (tab != 0) listOf(legend(DkInk.Blue, "β = $bT")) else emptyList()) + legend(DkInk.Green, "Minimum", SwatchStyle.Fill)
    val ratio = lBase / lMine
    val frames = listOf(
        DkFrame(
            header, plot(false), listOf(legend(DkInk.Grey, "β = 0.5"), legend(DkInk.Green, "Minimum", SwatchStyle.Fill)),
            listOf("v ← β·v + ∇f · w ← w − lr·v", "β = 0.5: loss at 60 = {${n(lBase, 3)}}"),
            "With little momentum, progress along w₁ {crawls}: loss ${n(lBase, 3)} after 60 steps.",
            "The bowl is 20× steeper across than along. A learning rate safe for the steep axis is tiny for the flat one.",
        ),
        DkFrame(
            header, plot(true), legendAll,
            listOf("v ← β·v + ∇f · w ← w − lr·v", if (tab == 0) "loss at 60: β 0.5 → {${n(lBase, 3)}}" else "loss at 60: β 0.5 → ${n(lBase, 3)} · β $bT → {${if (lMine < 0.01) n(lMine, 4) else n(lMine, 3)}}"),
            when {
                tab == 0 -> "This is the baseline: β = 0.5 ends at {${n(lBase, 3)}}."
                ratio >= 1 -> "β = $bT ends {${times(ratio)}×} lower after the same 60 steps."
                else -> "β = $bT {overshoots}: it ends ${times(1 / ratio)}× higher than β = 0.5."
            },
            when {
                tab == 0 -> "Pick β = 0.9 to see velocity build up along the flat axis."
                ratio >= 1 -> "On the flat w₁ axis every gradient points the same way, so velocity builds up. On the steep axis the signs alternate and largely cancel."
                else -> "So much velocity is kept that the ball swings past the minimum again and again; 60 steps aren't enough to settle."
            },
        ),
        DkFrame(
            header, plot(true), legendAll,
            listOf("v_t = Σ βᵏ·∇f_(t−k) · up to 1/(1 − β) = ${n(1 / (1 - b), 0)}× the gradient", "β = 0.9 is the usual default"),
            "β sets {how far back} the velocity remembers.",
            "Steady gradients add up to ${n(1 / (1 - b), 0)}× their size; oscillating ones cancel. Too close to 1 and the optimiser can't brake.",
        ),
    )
    stepActions(frames)
}

// ── AdaGrad ──

private fun adagradLab(): DkLab = DkLab(DkControl.Track) { _, _ ->
    val steps = 100
    var dense = 0.0
    var sparse = 0.0
    val dRate = ArrayList<Double>()
    val sRate = ArrayList<Double>()
    for (t in 0 until steps) {
        dense += 1.0
        if (t % 10 == 9) sparse += 4.0
        dRate += 1 / max(1.0, sqrt(dense))
        sRate += 1 / max(1.0, sqrt(sparse))
    }
    fun line(v: List<Double>, ink: DkInk, stairs: Boolean) = DkLine(
        if (stairs) v.indices.flatMap { t -> if (t > 0 && v[t] != v[t - 1]) listOf(DkP(t.toDouble(), log10(v[t - 1])), DkP(t.toDouble(), log10(v[t]))) else listOf(DkP(t.toDouble(), log10(v[t]))) }
        else v.mapIndexed { t, r -> DkP(t.toDouble(), log10(r)) },
        ink,
    )
    fun plot(showSparse: Boolean) = DkPlot(
        0.0 to 99.0, -2.1 to 0.1, logTicks(-2.1, 0.1), "step 0", "99",
        listOf(line(dRate, DkInk.Blue, false)) + if (showSparse) listOf(line(sRate, DkInk.Orange, true)) else emptyList(),
        dots = listOf(DkDot(DkP(99.0, log10(dRate.last())), DkInk.Blue, 4.5f)) + if (showSparse) listOf(DkDot(DkP(99.0, log10(sRate.last())), DkInk.Orange, 4.5f)) else emptyList(),
        axis = false,
    )
    val header = "η / √(Σ g²) · dense g = 1 every step, sparse g = 2 every 10th"
    val legend = listOf(legend(DkInk.Blue, "Dense"), legend(DkInk.Orange, "Sparse"))
    val ratio = sRate.last() / dRate.last()
    val frames = listOf(
        DkFrame(
            header, plot(false), legend.take(1), listOf("G ← G + g² · step = η·g / √G", "dense at 100: 1/√100 = {${n(dRate.last(), 3)}}"),
            "A feature seen every step has its rate fall as {1/√t}.",
            "AdaGrad divides each parameter's step by the root of all its past squared gradients.",
        ),
        DkFrame(
            header, plot(true), legend, listOf("dense at 100: 1/√100 = ${n(dRate.last(), 3)}", "sparse at 100: 1/√(10·4) = {${n(sRate.last(), 3)}}"),
            "The rare feature keeps a {${times(ratio)}×} larger step.",
            "AdaGrad sums every squared gradient, so frequent features cool down fast and rare ones stay responsive. The sum never shrinks, so every rate only falls.",
        ),
        DkFrame(
            header, plot(true), legend, listOf("G only grows → rate only falls", "after 10,000 steps the dense rate would be 0.01"),
            "The rate {never recovers}, even if gradients change.",
            "On long training runs AdaGrad's steps shrink toward zero and learning stalls. RMSprop replaces the sum with a moving average.",
        ),
        DkFrame(
            header, plot(true), legend, listOf("good fit: sparse features (word embeddings, ads)", "per-parameter rates with no tuning"),
            "AdaGrad shines on {sparse} data.",
            "Rare words or features get large updates when they finally appear, which is why it was popular for embeddings and click models.",
        ),
    )
    stepActions(frames)
}

// ── RMSprop ──

private fun rmspropLab(): DkLab = DkLab(DkControl.Track) { _, _ ->
    val eta = 0.01
    var v = 0.0
    var sum = 0.0
    val rms = ArrayList<Double>()
    val ada = ArrayList<Double>()
    for (t in 0 until 200) {
        val g = if (t < 100) 1.0 else 0.1
        v = 0.9 * v + 0.1 * g * g
        sum += g * g
        rms += eta / sqrt(v)
        ada += eta / sqrt(sum)
    }
    fun plot(upTo: Int) = DkPlot(
        0.0 to 199.0, -4.2 to -0.85, logTicks(-4.2, -0.85), "step 0", "199",
        listOf(
            DkLine((0 until upTo).map { DkP(it.toDouble(), log10(rms[it])) }, DkInk.Blue),
            DkLine((0 until upTo).map { DkP(it.toDouble(), log10(ada[it])) }, DkInk.Grey),
        ),
        dots = listOf(DkDot(DkP(upTo - 1.0, log10(rms[upTo - 1])), DkInk.Blue, 4.5f), DkDot(DkP(upTo - 1.0, log10(ada[upTo - 1])), DkInk.Grey, 4.5f)),
        axis = false,
    )
    val header = "effective rate · g = 1, then 0.1 from step 100"
    val legend = listOf(legend(DkInk.Blue, "RMSprop, γ = 0.9"), legend(DkInk.Grey, "AdaGrad"))
    val frames = listOf(
        DkFrame(
            header, plot(100), legend, listOf("v ← 0.9·v + 0.1·g² · rate = η/√v", "step 100: RMSprop ${n(rms[99], 3)} · AdaGrad ${n(ada[99], 4)}"),
            "With a steady gradient both rates {settle}; AdaGrad's keeps sinking.",
            "RMSprop's v is an average of recent g², so it levels off at g² = 1. AdaGrad's sum keeps growing.",
        ),
        DkFrame(
            header, plot(200), legend,
            listOf("v ← 0.9·v + 0.1·g² → √v at 200: ${n(sqrt(0.9.pow(100) + 0.01 * (1 - 0.9.pow(100))), 3)}", "rate at 200: RMSprop {${n(rms[199], 3)}} · AdaGrad ${n(ada[199], 5)}"),
            "After the shift RMSprop's rate recovers {${n(rms[199] / rms[99], 0)}×}; AdaGrad's stays stuck.",
            "An exponential average forgets old gradients, so the step adapts to the current scale. At step 200 it is ${n(rms[199] / ada[199], 0)}× AdaGrad's.",
        ),
        DkFrame(
            header, plot(200), legend, listOf("RMSprop + momentum on the gradient = Adam", "γ = 0.9: remembers about 10 steps"),
            "Add momentum to RMSprop and you get {Adam}.",
            "RMSprop was never formally published: Hinton proposed it in a Coursera lecture, and it became the default for RNNs.",
        ),
    )
    stepActions(frames)
}

// ── Adam: bias correction ──

private fun adamLab(): DkLab = DkLab(DkControl.Track) { _, _ ->
    val g = 2.0
    val ratios = (1..60).map { t -> (1 - 0.9.pow(t)) / sqrt(1 - 0.999.pow(t)) }
    val peak = ratios.indices.maxBy { ratios[it] }
    val top = ratios.max()
    val plot = DkPlot(
        0.0 to 59.0, -0.3 to top * 1.08, listOf(top to n(top, 1), top / 2 to n(top / 2, 1), 0.0 to "0.00"), "step 0", "59",
        listOf(DkLine(ratios.mapIndexed { i, r -> DkP(i.toDouble(), r) }, DkInk.Pink), DkLine(listOf(DkP(0.0, 1.0), DkP(59.0, 1.0)), DkInk.Blue)),
        dots = listOf(DkDot(DkP(peak.toDouble(), top), DkInk.Yellow, 5f)), axis = false,
    )
    val header = "step size ÷ lr · m/√v vs m̂/√v̂"
    val legend = listOf(legend(DkInk.Pink, "Uncorrected"), legend(DkInk.Blue, "Bias-corrected"), legend(DkInk.Yellow, "Peak", SwatchStyle.Fill))
    val m1 = 0.1 * g
    val v1 = 0.001 * g * g
    val frames = listOf(
        DkFrame(
            header, plot, legend,
            listOf("t = 1: m = ${n(m1, 1)}, v = ${n(v1, 3)} → m/√v = ${n(m1 / sqrt(v1))}", "m̂ = m/(1−0.9ᵗ), v̂ = v/(1−0.999ᵗ) → {1.00} every step"),
            "Without correction steps run up to {${n(top)}×} too large at step ${peak + 1}.",
            "Both averages start at 0, and v warms up 100× slower than m. Dividing by (1 − βᵗ) removes that start-up bias exactly.",
        ),
        DkFrame(
            header, plot, legend,
            listOf("Adam = momentum (m) + RMSprop (v) + bias correction", "defaults: β₁ 0.9, β₂ 0.999, ε 1e−8"),
            "Adam is momentum and RMSprop {in one update}.",
            "m smooths the direction, v scales each parameter's step, and the corrections make the first few hundred steps behave. It works well out of the box, which made it the default.",
        ),
    )
    stepActions(frames)
}

// ── AdamW: decoupled weight decay ──

private fun adamwLab(): DkLab = DkLab(DkControl.Tabs, tabs = listOf("L2 in Adam", "AdamW"), initialTab = 1) { tab, _ ->
    val lr = 0.001
    val lambda = 0.01
    val w = 1.0
    class P(val name: String, val vhat: Double, val g: Double)
    val ps = listOf(P("large history", 25.0, 5.0), P("small history", 0.25, 0.5))
    val l2 = ps.map { lr * lambda * w / sqrt(it.vhat) }
    val aw = lr * lambda * w
    val top = l2.max()
    fun micro(v: Double) = n(v * 1e6, 1) + "μ"
    fun rows(both: Boolean) = DkRows(
        ps.mapIndexed { i, p ->
            DkRow(
                "${p.name} · v̂ = ${n(p.vhat, 2).trimEnd('0').trimEnd('.')}", "|g| was ${n(p.g, 1).removeSuffix(".0")}",
                listOf(DkBar(l2[i] / top, DkInk.Pink, micro(l2[i]))) + if (both) listOf(DkBar(aw / top, DkInk.Blue, micro(aw))) else emptyList(),
                pair = both,
            )
        },
    )
    val header = "decay applied to each parameter · λ = $lambda, lr = $lr"
    val legend = listOf(legend(DkInk.Pink, "L2 inside Adam", SwatchStyle.Fill), legend(DkInk.Blue, "AdamW, decoupled", SwatchStyle.Fill))
    val both = tab == 1
    val l2Line = "L2: lr·λw/√v̂ = $lr·$lambda/√25 = ${micro(l2[0])} vs /√0.25 = ${micro(l2[1])}"
    val frames = listOf(
        DkFrame(
            header, rows(both), if (both) legend else legend.take(1),
            listOf(l2Line, if (both) "AdamW: lr·λ·w = {${micro(aw)}} for both" else "same weight, same λ, {${n(l2[1] / l2[0], 0)}×} different decay"),
            "L2-in-Adam decays the two weights {${n(l2[1] / l2[0], 0)}×} differently.",
            "Adam divides the decay by √v̂ along with the gradient, so weights with big gradients barely shrink. AdamW applies decay outside the adaptive step.",
        ),
        DkFrame(
            header, rows(both), if (both) legend else legend.take(1),
            listOf("AdamW: w ← w − lr·(m̂/√v̂ + λ·w)", "decay no longer depends on gradient history"),
            if (both) "AdamW shrinks every weight by the {same} ${micro(aw)}." else "With L2 inside Adam, regularisation {depends} on the gradients.",
            "That makes λ behave like true weight decay. AdamW generalises better and is what transformers are trained with.",
        ),
    )
    stepActions(frames)
}

// ── Learning-rate schedulers ──

private class LrSchedule(val name: String, val ink: DkInk, val dashed: Boolean, val lr: (Int) -> Double)

private val schedules = listOf(
    LrSchedule("constant", DkInk.Grey, true) { 0.03 },
    LrSchedule("step decay", DkInk.Orange, false) { t -> 0.09 * 0.5.pow(t / 15) },
    LrSchedule("cosine", DkInk.Violet, false) { t -> 0.045 * (1 + cos(PI * t / 60)) },
    LrSchedule("warmup+cos", DkInk.Blue, false) { t -> if (t < 5) 0.09 * (t + 1) / 5 else 0.045 * (1 + cos(PI * (t - 5) / 55)) },
)

private fun runSchedule(s: LrSchedule): List<Double> {
    var w1 = -8.0
    var w2 = 1.0
    val loss = arrayListOf(bowl(w1, w2))
    for (t in 0 until 60) {
        val lr = s.lr(t)
        w1 -= lr * w1
        w2 -= lr * Steep * w2
        loss += bowl(w1, w2)
    }
    return loss
}

private fun schedulerLab(): DkLab = DkLab(DkControl.Tabs, tabs = listOf("Constant", "Step", "Cosine", "Warmup"), initialTab = 3) { tab, _ ->
    val runs = schedules.map { runSchedule(it) }
    val finals = runs.map { it.last() }
    val logs = runs.flatten().map { log10(it) }
    val lo = floor(logs.min()) - 0.3
    val hi = logs.max() + 0.3
    fun lossPlot() = DkPlot(
        0.0 to 60.0, lo to hi, logTicks(lo, hi), "step 0", "60",
        schedules.mapIndexed { i, s -> DkLine(runs[i].mapIndexed { t, v -> DkP(t.toDouble(), log10(v)) }, s.ink, dashed = s.dashed) },
        axis = false,
    )
    val lrPlot = DkPlot(
        0.0 to 59.0, -0.004 to 0.096, listOf(0.09 to "0.09", 0.03 to "0.03", 0.0 to "0"), "step 0", "59",
        schedules.map { s -> DkLine((0 until 60).map { DkP(it.toDouble(), s.lr(it)) }, s.ink, dashed = s.dashed) },
        rules = listOf(DkRule(0.1, DkInk.Pink, thin = true)), axis = false,
    )
    val legend = schedules.map { legend(it.ink, it.name, if (it.dashed) SwatchStyle.DashedLine else SwatchStyle.Line) }
    val s = schedules[tab]
    val f = finals[tab]
    val step1 = runs.map { it[1] }
    val finalLine = "step 60: " + finals.mapIndexed { i, v -> if (i == tab) "{${n(v, 3)}}" else n(v, 3) }.joinToString(" · ")
    val frames = listOf(
        DkFrame(
            "learning rate per step · steep-axis limit 0.1", lrPlot, legend,
            listOf("stable while lr < 2/20 = 0.1 on the steep axis", "constant 0.03 · others start near 0.09"),
            "Four ways to set the {learning rate} over 60 steps.",
            "Step decay halves it every 15 steps, cosine glides to 0, warmup ramps up over 5 steps first. All but the constant start near the stability limit.",
        ),
        DkFrame(
            "loss on f = ½(w₁² + 20·w₂²) · four schedules", lossPlot(), legend,
            listOf("step 1: constant ${n(step1[0], 3)} · warmup ${n(step1[3], 3)} · hot starts ${n(step1[1], 3)}", finalLine),
            if (tab == 0) "A safe constant 0.03 ends at {${n(finals[0], 3)}}."
            else "A safe constant 0.03 ends at ${n(finals[0], 3)}; {${s.name}} reaches ${n(f, 3)}.",
            if (tab == 0) "It is slow on the flat axis all the way. Pick another schedule to compare."
            else "Starting at 0.09 is near the steep axis's 0.1 limit but moves fast along the flat one. Decaying from there gets both: ${s.name} ends ${n(finals[0] / f, 0)}× below the constant rate.",
        ),
        DkFrame(
            "loss on f = ½(w₁² + 20·w₂²) · four schedules", lossPlot(), legend,
            listOf("warmup: protects early steps when gradients are wild", "cosine with warmup: the transformer default"),
            "Warmup matters when the {first steps} are unstable.",
            "Here the bowl is gentle, so warmup costs a little. In a fresh transformer, Adam's early estimates are noisy and a full-size first step can blow training up.",
        ),
    )
    stepActions(frames)
}

// ── KL divergence ──

private val klP: List<Double> = listOf(0.0, 0.03, 0.18, 0.18, 0.03, 0.0, 0.0, 0.03, 0.18, 0.18, 0.03, 0.0).let { p -> p.map { it / p.sum() } }

private fun gaussQ(mu: Double, sigma: Double): List<Double> {
    val raw = (1..12).map { exp(-0.5 * ((it - mu) / sigma).pow(2)) }
    return raw.map { it / raw.sum() }
}

private fun kl(a: List<Double>, b: List<Double>) = a.indices.sumOf { i -> if (a[i] <= 0) 0.0 else a[i] * ln(a[i] / max(b[i], 1e-9)) }

private class KlBest(val mu: Double, val sigma: Double, val value: Double)

private val klFits: Pair<KlBest, KlBest> by lazy {
    var fwd = KlBest(0.0, 0.0, Double.MAX_VALUE)
    var rev = KlBest(0.0, 0.0, Double.MAX_VALUE)
    var mu = 1.0
    while (mu <= 12.0001) {
        var s = 0.4
        while (s <= 6.0001) {
            val q = gaussQ(mu, s)
            val f = kl(klP, q)
            val r = kl(q, klP.map { max(it, 1e-6) })
            if (f < fwd.value) fwd = KlBest(mu, s, f)
            if (r < rev.value) rev = KlBest(mu, s, r)
            s += 0.05
        }
        mu += 0.5
    }
    fwd to rev
}

private fun klLab(): DkLab = DkLab(DkControl.Tabs, tabs = listOf("Forward", "Reverse", "Both"), initialTab = 2) { tab, _ ->
    val (fwd, rev) = klFits
    val top = klP.max() * 1.3
    fun qCurve(f: KlBest, ink: DkInk) = DkLine((0..110).map { k -> val x = 1 + 11.0 * k / 110; DkP(x, exp(-0.5 * ((x - f.mu) / f.sigma).pow(2)) / gaussQ(f.mu, f.sigma).let { q -> (1..12).sumOf { exp(-0.5 * ((it - f.mu) / f.sigma).pow(2)) } }) }, ink)
    fun plot(showF: Boolean, showR: Boolean) = DkPlot(
        0.4 to 12.6, 0.0 to top, emptyList(), "bin 1", "12",
        (if (showF) listOf(qCurve(fwd, DkInk.Orange)) else emptyList()) + if (showR) listOf(qCurve(rev, DkInk.Pink)) else emptyList(),
        axis = false, bars = klP.mapIndexed { i, p -> DkPBar(i + 1.0, p, DkInk.Slate, 0.8) },
    )
    val header = "P = two bumps · best single-Gaussian Q under each direction"
    val legend = listOf(legend(DkInk.Slate, "P (target)", SwatchStyle.Fill)) +
        (if (tab != 1) listOf(legend(DkInk.Orange, "min KL(P‖Q)")) else emptyList()) + if (tab != 0) listOf(legend(DkInk.Pink, "min KL(Q‖P)")) else emptyList()
    val fLine = "KL(P‖Q): μ ${n(fwd.mu, 1)}, σ ${n(fwd.sigma)} → ${n(fwd.value, 3)} · covers both"
    val rLine = "KL(Q‖P): μ ${n(rev.mu, 1)}, σ ${n(rev.sigma)} → {${n(rev.value, 3)}} · picks one"
    val frames = listOf(
        DkFrame(
            header, plot(false, false), legend.take(1), listOf("KL(P‖Q) = Σ P·log(P/Q)", "Q: one Gaussian, μ and σ searched on a grid"),
            "Fit {one Gaussian} to a two-peaked P.",
            "No single bump can match both peaks, so the direction of KL decides which compromise wins.",
        ),
        DkFrame(
            header, plot(tab != 1, tab != 0), legend,
            when (tab) {
                0 -> listOf("KL(P‖Q): μ ${n(fwd.mu, 1)}, σ ${n(fwd.sigma)} → {${n(fwd.value, 3)}}", "Q must cover wherever P > 0")
                1 -> listOf("KL(Q‖P): μ ${n(rev.mu, 1)}, σ ${n(rev.sigma)} → {${n(rev.value, 3)}}", "Q must avoid wherever P ≈ 0")
                else -> listOf(fLine, rLine)
            },
            when (tab) {
                0 -> "Forward KL {spreads} Q across both peaks."
                1 -> "Reverse KL {locks onto} one peak."
                else -> "Swapping the order changes the {answer}, not just the number."
            },
            "Forward KL punishes Q for missing any of P's mass, so it spreads across both peaks. Reverse KL punishes Q for putting mass where P has none, so it locks onto one.",
        ),
        DkFrame(
            header, plot(tab != 1, tab != 0), legend,
            listOf("forward: maximum likelihood, mass-covering", "reverse: variational inference, mode-seeking"),
            "KL is {not a distance}: KL(P‖Q) ≠ KL(Q‖P).",
            "Training a model by likelihood minimises forward KL, which is why it hedges. VAEs and variational methods minimise reverse KL, which is why they can miss modes.",
        ),
    )
    stepActions(frames)
}

// ── Cross-entropy ──

private fun crossEntropyLab(): DkLab = DkLab(DkControl.Tabs, tabs = listOf("True class 0", "True class 2"), initialTab = 1) { tab, _ ->
    val z = listOf(2.0, 1.0, 0.1)
    val e = z.map { exp(it) }
    val p = e.map { it / e.sum() }
    val y = if (tab == 0) 0 else 2
    val grad = p.mapIndexed { i, v -> v - if (i == y) 1.0 else 0.0 }
    fun rows(withGrad: Boolean) = DkRows(
        p.mapIndexed { i, v ->
            DkRow(
                "class $i", "",
                listOf(DkBar(v, DkInk.Blue, n(v, 3))) + if (withGrad) listOf(DkBar(abs(grad[i]), if (grad[i] >= 0) DkInk.Blue else DkInk.Pink, n(grad[i], 3))) else emptyList(),
                hot = i == y, pair = withGrad,
            )
        },
        caption = if (withGrad) "left: p · right: ∂L/∂z = p − y" else "p = softmax(z)",
    )
    val header = "logits (2.0, 1.0, 0.1) → softmax p, gradient p − y"
    val legend = listOf(legend(DkInk.Blue, "Probability / push up", SwatchStyle.Fill), legend(DkInk.Pink, "Push down", SwatchStyle.Fill), legend(DkInk.Yellow, "True class", SwatchStyle.Fill))
    val l0 = -ln(p[0])
    val l2 = -ln(p[2])
    val lossLine = "true 0: −ln ${n(p[0], 3)} = ${if (tab == 0) "{${n(l0, 3)}}" else n(l0, 3)} · true 2: −ln ${n(p[2], 3)} = ${if (tab == 1) "{${n(l2, 3)}}" else n(l2, 3)}"
    val frames = listOf(
        DkFrame(
            header, rows(false), legend.take(1) + legend[2], listOf("L = −ln p_true", lossLine),
            if (tab == 0) "The model already favours class 0: loss {${n(l0, 3)}}." else "The model gives the true class only {${n(p[2], 3)}}.",
            "Cross-entropy only looks at the probability of the true class: the lower it is, the steeper the penalty.",
        ),
        DkFrame(
            header, rows(true), legend, listOf(lossLine),
            if (tab == 0) "Already right: the gradient is {small} everywhere."
            else "Being wrong costs {${n(l2 / l0, 1)}×} more, and the gradient says exactly how to fix it.",
            if (tab == 0) "p − y is ${n(grad[0], 3)} on the true class and small elsewhere: the model is nearly done with this example."
            else "p − y lowers each wrong logit by its own probability and raises the true one by ${n(-grad[2], 2)}. No Jacobian needed.",
        ),
        DkFrame(
            header, rows(true), legend, listOf("softmax + cross-entropy: ∂L/∂z = p − y", "−ln p → ∞ as p → 0"),
            "Softmax and cross-entropy {cancel} into p − y.",
            "That clean gradient is why the two are always paired. Confident mistakes cost the most, so they get fixed first.",
        ),
    )
    stepActions(frames)
}
