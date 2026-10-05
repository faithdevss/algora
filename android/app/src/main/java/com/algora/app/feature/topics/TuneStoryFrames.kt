package com.algora.app.feature.topics

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tanh

// ── Fine-tuning and efficiency storyboard frames ─────────────────────────────
// Full fine-tuning, DPO, PEFT, LoRA & QLoRA, quantization, Flash Attention and RLHF, drawn by
// LlmStoryLabs.kt. Three tiny networks are trained here, DPO is optimised on seven pairs, 4,096 seeded
// weights are quantized and the online softmax is run block by block; the PEFT, LoRA and long-run memory
// figures are arithmetic over the stated configs. The iOS port (TuneStoryFrames.swift) matches it.

internal val tuneStoryTopicIds = setOf(
    "fine_tuning_full", "dpo", "peft", "lora_qlora", "quantization", "flash_attention", "rlhf",
)

internal fun tuneLab(topicId: String): LsLab? = when (topicId) {
    "fine_tuning_full" -> fineTuneStory()
    "dpo" -> dpoStory()
    "peft" -> peftStory()
    "lora_qlora" -> loraStory()
    "quantization" -> quantStory()
    "flash_attention" -> flashStory()
    "rlhf" -> rlhfStory()
    else -> null
}

private fun f(x: Double, d: Int = 2) = lsF(x, d)
private fun pct(p: Double) = lsPct(p)
private fun L(text: String) = LsBlock.Label(text)
private fun B(rows: List<LsRow>) = LsBlock.Bars(rows)
private fun S(vararg rows: LsStat) = LsBlock.Stats(rows.toList())
private fun st(k: String, v: String, ink: LsInk? = null) = LsStat(k, v, ink)
private fun F(a: String, b: String = "", ink: LsInk? = null) = LsFx(a, b, ink)
private fun fr(blocks: List<LsBlock>, fx: List<LsFx>, cap: Pair<String, String>) = LsFrame(blocks, fx, cap.first, cap.second)
private fun pick(on: Boolean) = if (on) LsText.Strong else LsText.Muted

// ── Fine-tuning: scratch vs head only vs full ──

private class FtNet(val w: Array<DoubleArray>, val b: DoubleArray, val v: DoubleArray, var c: Double) {
    fun copy() = FtNet(Array(6) { w[it].copyOf() }, b.copyOf(), v.copyOf(), c)
}

private class FtPt(val x: Double, val y: Double, val l: Int)

private class FtSnap(val net: FtNet, val tr: Double, val te: Double)

private fun ftHidden(m: FtNet, p: FtPt) = DoubleArray(6) { tanh(m.w[it][0] * p.x + m.w[it][1] * p.y + m.b[it]) }

private fun ftProb(m: FtNet, p: FtPt): Double {
    val h = ftHidden(m, p)
    val z = h.indices.fold(m.c) { a, j -> a + h[j] * m.v[j] }
    return 1 / (1 + exp(-z))
}

private fun ftAcc(m: FtNet, d: List<FtPt>) = d.count { (if (ftProb(m, it) > 0.5) 1 else 0) == it.l }.toDouble() / d.size

/** Batch gradient descent for 300 epochs; snapshots at 10, 50 and 300. [body] = false freezes the hidden layer. */
private fun ftRun(m: FtNet, body: Boolean, lr: Double, tr: List<FtPt>, te: List<FtPt>): Map<Int, FtSnap> {
    val snaps = HashMap<Int, FtSnap>()
    for (e in 1..300) {
        val gw = Array(6) { DoubleArray(2) }
        val gb = DoubleArray(6)
        val gv = DoubleArray(6)
        var gc = 0.0
        tr.forEach { p ->
            val h = ftHidden(m, p)
            val z = h.indices.fold(m.c) { a, j -> a + h[j] * m.v[j] }
            val d = 1 / (1 + exp(-z)) - p.l
            gc += d
            for (j in 0 until 6) {
                gv[j] += d * h[j]
                if (body) {
                    val dp = d * m.v[j] * (1 - h[j] * h[j])
                    gw[j][0] += dp * p.x
                    gw[j][1] += dp * p.y
                    gb[j] += dp
                }
            }
        }
        val n = tr.size
        m.c -= lr * gc / n
        for (j in 0 until 6) m.v[j] -= lr * gv[j] / n
        if (body) for (j in 0 until 6) {
            m.w[j][0] -= lr * gw[j][0] / n
            m.w[j][1] -= lr * gw[j][1] / n
            m.b[j] -= lr * gb[j] / n
        }
        if (e == 10 || e == 50 || e == 300) snaps[e] = FtSnap(m.copy(), ftAcc(m, tr), ftAcc(m, te))
    }
    return snaps
}

private fun fineTuneStory(): LsLab {
    val rnd = LsRng(11)
    val pts = List(76) {
        val x = rnd.next() * 2 - 1
        val y = rnd.next() * 2 - 1
        FtPt(x, y, if (x * x + y * y < 0.45) 1 else 0)
    }
    val tr = pts.take(16)
    val te = pts.drop(16)
    fun pre() = FtNet(Array(6) { doubleArrayOf(3 * cos(it * PI / 3), 3 * sin(it * PI / 3)) }, DoubleArray(6) { -1.5 }, DoubleArray(6), 0.0)
    val randW = Array(6) { doubleArrayOf(rnd.next() * 2 - 1, rnd.next() * 2 - 1) }
    val randB = DoubleArray(6) { rnd.next() - 0.5 }
    val randV = DoubleArray(6) { rnd.next() - 0.5 }
    val runs = listOf(
        ftRun(FtNet(randW, randB, randV, 0.0), true, 0.5, tr, te),
        ftRun(pre(), false, 0.5, tr, te),
        ftRun(pre(), true, 0.5, tr, te),
    )
    val names = listOf("From scratch", "Head only", "Full fine-tune")
    val counts = listOf(25, 7, 25)
    return LsLab(
        tabs = listOf("Scratch", "Head only", "Full"), initialTab = 1,
        legend = listOf(LsInk.Green to "Right", LsInk.Red to "Wrong", LsInk.Grey to "Training point"),
    ) { k ->
        List(5) { s ->
            val ep = listOf(0, 10, 50, 300, 300)[s]
            val sn = if (s > 0) runs[k].getValue(ep) else null
            val plotPts = te.map { it to false } + tr.map { it to true }
            val plot = LsBlock.Plot(170, plotPts.map { (p, isT) ->
                var ink = if (p.l == 1) LsInk.Blue else LsInk.Grey
                if (sn != null && s < 4) ink = if ((if (ftProb(sn.net, p) > 0.5) 1 else 0) == p.l) LsInk.Green else LsInk.Red
                LsPt((p.x + 1) / 2, (p.y + 1) / 2, ink, big = isT)
            })
            when {
                s == 0 -> fr(
                    listOf(L("task: inside the circle? 16 training points (ringed), 60 test"), plot, S(st("trainable, head only", "7 weights"), st("trainable, full", "25 weights"))),
                    listOf(F("body: 2 → 6 tanh units (18 weights)"), F("head: 6 → 1 sigmoid (7 weights)", "", LsInk.Lilac)),
                    "Three ways to use a small dataset." to "The body was pretrained on a related task. Train from scratch, train only a new head, or fine-tune everything.",
                )
                s < 4 -> fr(
                    listOf(
                        L("${names[k]}, epoch $ep: test predictions"), plot,
                        B(listOf(lsRow("train acc.", pct(sn!!.tr), sn.tr, LsInk.Slate), lsRow("test acc.", pct(sn.te), sn.te, LsInk.Violet, LsText.Strong))),
                    ),
                    listOf(F("${names[k]}: ${counts[k]} trainable · lr 0.5"), F("epoch $ep test accuracy =", pct(sn.te), LsInk.Lilac)),
                    "Epoch $ep: ${pct(sn.te)} on unseen points." to when (k) {
                        0 -> "Random features must be learned from 16 points; the boundary is slow to form and easy to overfit."
                        1 -> "The frozen features already describe distance from the centre; only 7 weights need fitting."
                        else -> "Starts from the pretrained features and adjusts them too — the most flexible, and the most to store."
                    },
                )
                else -> fr(
                    listOf(
                        L("test accuracy after 300 epochs"),
                        B(runs.mapIndexed { j, r -> val a = r.getValue(300).te; lsRow(names[j], pct(a), a, if (j == k) LsInk.Violet else LsInk.Slate, pick(j == k)) }),
                        S(st("gap train − test", pct(runs[k].getValue(300).tr - runs[k].getValue(300).te))),
                    ),
                    listOf(F("trained for real: batch gradient descent, 300 epochs")),
                    "Pretrained features carry the small-data case." to "With 16 examples, reusing a body beats learning one. Full fine-tuning adds capacity on top, and a copy of every weight per task.",
                )
            }
        }
    }
}

// ── DPO ──

private fun dpoStory(): LsLab {
    val names = mapOf("A" to "cites a source", "B" to "correct, terse", "C" to "hedged", "D" to "confident, wrong")
    val keys = listOf("A", "B", "C", "D")
    val pairs = listOf("A" to "B", "A" to "C", "B" to "C", "C" to "D", "B" to "D", "A" to "D", "D" to "A")
    fun sig(z: Double) = 1 / (1 + exp(-z))
    val betas = listOf(0.1, 0.5, 2.0)
    return LsLab(
        tabs = listOf("β 0.1", "β 0.5", "β 2"), initialTab = 1,
        legend = listOf(LsInk.Blue to "Policy probability", LsInk.Pink to "Bad response", LsInk.Red to "Mislabelled pair"),
    ) { tab ->
        val beta = betas[tab]
        val th = HashMap(keys.associateWith { 0.0 })
        val snaps = HashMap<Int, Map<String, Double>>()
        snaps[0] = th.toMap()
        for (t in 1..1000) {
            val g = HashMap(keys.associateWith { 0.0 })
            pairs.forEach { (w, l) ->
                val d = beta * (1 - sig(beta * (th.getValue(w) - th.getValue(l))))
                g[w] = g.getValue(w) - d
                g[l] = g.getValue(l) + d
            }
            keys.forEach { th[it] = th.getValue(it) - g.getValue(it) / pairs.size }
            if (t == 10 || t == 100 || t == 1000) snaps[t] = th.toMap()
        }
        val chips = LsBlock.Chips(pairs.mapIndexed { i, (w, l) -> LsTok("$w ≻ $l", if (i == 6) LsTone.Err else LsTone.Plain, if (i == 6) "mislabelled" else "") })
        List(5) { s ->
            val big = listOf(0, 0, 10, 100, 1000)[s]
            val t = snaps.getValue(big)
            val e = keys.map { exp(t.getValue(it)) }
            val pi = keys.indices.associate { keys[it] to e[it] / e.sum() }
            val loss = pairs.sumOf { (w, l) -> -ln(sig(beta * (t.getValue(w) - t.getValue(l)))) } / pairs.size
            val kl = keys.sumOf { pi.getValue(it) * ln(pi.getValue(it) / 0.25) }
            val bars = B(keys.map { x -> lsRow("$x · ${names[x]}", pct(pi.getValue(x)), pi.getValue(x), if (x == "D") LsInk.Pink else LsInk.Blue, if (x == "A") LsText.Strong else LsText.Normal) })
            val head = listOf(L("7 preference pairs (one mislabelled)"), chips)
            when (s) {
                0 -> fr(head + listOf(L("reference policy π_ref"), bars), listOf(F("π_ref = uniform over 4 responses", "25% each")),
                    "DPO learns straight from preferences." to "No reward model and no RL loop: one classification-style loss on (chosen, rejected) pairs.")
                1 -> fr(head + listOf(L("implicit reward"), S(st("r(y)", "β · log π(y) / π_ref(y)"), st("β", lsBetaText(beta)))),
                    listOf(F("loss = −log σ(β[(log π(y_w) − log π(y_l)) − ref])")),
                    "The policy is its own reward model." to "How much more likely the policy makes a response than the reference does, scaled by β, plays the role of the reward.")
                else -> fr(head + listOf(L("policy after $big steps"), bars),
                    listOf(F("mean DPO loss =", f(loss, 3), LsInk.Lilac), F("KL(π ‖ π_ref) =", f(kl, 3) + " nats")),
                    if (s == 4) "After 1,000 steps: ${pct(pi.getValue("A"))} on the best response." to "β = ${lsBetaText(beta)}: ${
                        if (beta < 0.5) "small steps per pair, the policy stays near the reference" else if (beta > 1) "large β moves far from the reference fast" else "a middle setting"
                    }. The mislabelled D ≻ A pair keeps pulling the other way."
                    else "Step $big: the ranking A > B > C > D is emerging." to "KL from the reference is ${f(kl, 2)} nats so far.")
            }
        }
    }
}

/** 0.1, 0.5, 2 — as JavaScript prints them. */
internal fun lsBetaText(b: Double) = if (b == b.roundToLong().toDouble()) b.roundToLong().toString() else b.toString()

// ── PEFT on BERT-base ──

private fun peftStory(): LsLab {
    val total = 108891648.0
    val methods = listOf("Full" to total, "Adapters r64" to 2379264.0, "Prefix (20)" to 368640.0, "LoRA r8 (Q,V)" to 294912.0, "BitFit" to 102144.0, "Head only" to 1538.0)
    val formulas = listOf("2 · 768 · 8 per matrix × 2 matrices × 12 layers", "(768·64 + 64 + 64·768 + 768) × 2 × 12", "every bias: 8,448 per layer × 12 + 768")
    val how = listOf(
        "LoRA adds a rank-8 bypass to two attention matrices." to "Each frozen 768×768 weight gets B (768×8) · A (8×768) beside it.",
        "Adapters insert a small bottleneck MLP twice per layer." to "768 → 64 → 768, after attention and after the FFN.",
        "BitFit trains only the bias vectors." to "No new modules at all — the cheapest method that still touches every layer.",
    )
    return LsLab(
        tabs = listOf("LoRA", "Adapters", "BitFit"), initialTab = 0,
        legend = listOf(LsInk.Blue to "Trainable", LsInk.Pink to "Full fine-tune", LsInk.Green to "Per-task file"),
    ) { k ->
        val pickIdx = listOf(3, 1, 4)[k]
        val (nm, n) = methods[pickIdx]
        val share = f(n / total * 100, 2) + "%"
        fun mem(x: Double) = (total * 2 + x * 16) / 1e9
        listOf(
            fr(listOf(S(st("layers", "12"), st("width", "768"), st("FFN width", "3,072"), st("vocab", "30,522"), st("total", lsNum(total)))),
                listOf(F("embeddings 23.8M + 12 × 7.09M =", "108.9M", LsInk.Lilac)),
                "One concrete model: BERT-base." to "Every count on this screen is arithmetic over this config, not a figure copied from a paper."),
            fr(listOf(L("trainable parameters, log scale"), B(methods.mapIndexed { j, (m, c) -> lsRow(m, lsBig(c), log10(c) / 8.1, if (j == pickIdx) LsInk.Blue else LsInk.Slate, pick(j == pickIdx)) })),
                listOf(F("$nm:", lsNum(n), LsInk.Blue), F("share of model =", share)),
                "Six ways to tune it span five orders of magnitude." to "$nm trains $share of the weights; the rest stay frozen."),
            fr(listOf(S(st(nm, lsNum(n), LsInk.Blue))), listOf(F(formulas[k]), F("=", lsNum(n), LsInk.Blue)), how[k]),
            fr(listOf(L("training memory: fp16 weights + 16 B per trainable (fp32 copy, grad, Adam ×2)"),
                B(listOf(lsRow("Full", f(mem(total), 2) + " GB", mem(total) / 2.2, LsInk.Pink), lsRow(nm, f(mem(n), 2) + " GB", mem(n) / 2.2, LsInk.Blue, LsText.Strong)))),
                listOf(F("full: 2·108.9M + 16·108.9M =", f(mem(total), 2) + " GB", LsInk.Pink), F("$nm: 2·108.9M + 16·${lsBig(n)} =", f(mem(n), 2) + " GB", LsInk.Blue)),
                "${f(mem(total) / mem(n), 1)}× less training memory." to "The frozen weights still have to sit in memory; what disappears is the optimizer state for them."),
            fr(listOf(L("saved per task (fp16)"),
                B(listOf(lsRow("Full", lsBig(total * 2) + "B", 1.0, LsInk.Pink), lsRow(nm, lsBig(n * 2) + "B", max(n / total, 0.01), LsInk.Green, LsText.Strong)))),
                listOf(F("100 tasks, full:", lsBig(total * 2 * 100) + "B", LsInk.Pink), F("100 tasks, $nm:", lsBig(total * 2 + n * 2 * 100) + "B incl. one base", LsInk.Green)),
                "The real win is what you store per task." to "One shared base model plus a tiny file per task, instead of a full copy each time."),
        )
    }
}

// ── LoRA & QLoRA ──

private fun loraStory(): LsLab {
    val ds = listOf(2.34, 2.12, 1.19, .46, .41, .37, .33, .30, .27, .25, .22, .20, .18, .16, .14, .12, .11, .10, .09, .08, .07, .06, .05, .04)
    val ws = ds.indices.map { 3.1 * 0.93.pow(it) }
    fun energy(a: List<Double>) = a.sumOf { it * it }
    val ranks = listOf(1, 2, 4, 8)
    return LsLab(
        tabs = listOf("r 1", "r 2", "r 4", "r 8"), initialTab = 1,
        legend = listOf(LsInk.Pink to "Update spectrum / full", LsInk.Blue to "Kept by LoRA", LsInk.Green to "QLoRA"),
    ) { tab ->
        val r = ranks[tab]
        val keep = energy(ds.take(r)) / energy(ds)
        val d = 4096.0
        val full = d * d
        val lora = 2 * d * r
        val fullMem = 7e9 * (2 + 2 + 12) / 1e9
        val q = 7e9 * 0.5 / 1e9
        listOf(
            fr(listOf(L("singular values, first 10 of 24"), B(ds.take(10).mapIndexed { i, v -> lsRow("σ${i + 1}", "ΔW ${f(v)} · W₀ ${f(ws[i])}", v / 3.2, LsInk.Pink) })),
                listOf(F("ΔW = W_finetuned − W₀ (24×24)")),
                "LoRA’s claim: the update is low-rank, not the model." to "ΔW’s spectrum collapses after three values; the pretrained W₀ decays slowly."),
            fr(listOf(L("energy kept by the top $r"), B(ds.take(10).mapIndexed { i, v -> lsRow("σ${i + 1}", f(v), v / 2.4, if (i < r) LsInk.Blue else LsInk.Slate, if (i < r) LsText.Strong else LsText.Muted) }),
                S(st("energy kept", pct(keep), LsInk.Blue))),
                listOf(F("Σ σ²(top $r) / Σ σ² =", pct(keep), LsInk.Blue)),
                "Rank $r keeps ${pct(keep)} of the update." to if (r < 3) "Too low: part of the real update can’t be represented." else "Past the cliff, extra rank buys almost nothing."),
            fr(listOf(S(st("full ΔW, d = 4096", lsNum(full)), st("LoRA B·A, r = $r", lsNum(lora), LsInk.Blue), st("ratio", "${lsNum(full / lora)}×"))),
                listOf(F("2 · 4096 · $r =", lsNum(lora), LsInk.Blue)),
                "${lsNum(full / lora)}× fewer parameters per matrix." to "Train B (d×r) and A (r×d) instead of the d×d update; W₀ stays frozen."),
            fr(listOf(L("fine-tuning a 7B model"), B(listOf(lsRow("full fp16", f(fullMem, 0) + " GB", 1.0, LsInk.Pink), lsRow("QLoRA 4-bit", f(q, 1) + " GB + adapters", q / fullMem + .02, LsInk.Green, LsText.Strong)))),
                listOf(F("full: 7B × (2 W + 2 grad + 12 Adam/fp32) =", f(fullMem, 0) + " GB", LsInk.Pink), F("QLoRA base: 7B × 0.5 B =", f(q, 1) + " GB", LsInk.Green)),
                "QLoRA stores the frozen base in 4 bits." to "Gradients flow through the quantized weights into fp16 adapters. A 7B fine-tune fits on one consumer GPU."),
            fr(listOf(S(st("inference", "W = W₀ + B·A, merged once"), st("extra latency", "0"))),
                listOf(F("merge cost: one d×r×d multiply per matrix")),
                "At inference, merge and forget." to "B·A is added into W₀, so the deployed model is exactly the original shape — unlike adapters, which add layers."),
        )
    }
}

// ── Quantization ──

private val quantWeights: List<Double> by lazy {
    val rnd = LsRng(5)
    List(4096) {
        val u = rnd.next().let { if (it == 0.0) 1e-9 else it }
        val v = rnd.next()
        0.02 * sqrt(-2 * ln(u)) * cos(2 * PI * v)
    }
}

private fun quantStory(): LsLab {
    val w = quantWeights
    val n = w.size
    val bins = IntArray(16)
    w.forEach { x -> val j = kotlin.math.floor((x / 0.08 + .5) * 16).toInt(); if (j in 0 until 16) bins[j]++ }
    val hist = LsBlock.Hist(bins.map { it.toDouble() / bins.max() })
    val variance = w.sumOf { it * it } / n
    fun mse(a: List<Double>) = a.indices.sumOf { (a[it] - w[it]).pow(2) } / n
    fun snr(a: List<Double>) = 10 * log10(variance / mse(a))
    val widths = listOf(8, 4, 3)
    return LsLab(
        tabs = listOf("8-bit", "4-bit", "3-bit"), initialTab = 1,
        legend = listOf(LsInk.Grey to "Weights", LsInk.Green to "Quantized", LsInk.Pink to "Error"),
    ) { tab ->
        val b = widths[tab]
        val qmax = 2.0.pow(b - 1) - 1
        val amax = w.maxOf { abs(it) }
        val sc = amax / qmax
        val deq = w.map { Math.round(it / sc) * sc }
        val blk = w.chunked(64).flatMap { blkW -> val s2 = blkW.maxOf { abs(it) } / qmax; blkW.map { Math.round(it / s2) * s2 } }
        val used = w.map { Math.round(it / sc) }.toSet().size
        val levels = lsNum(2 * qmax + 1)
        val snrT = snr(deq)
        val snrB = snr(blk)
        val bits = b + 16.0 / 64
        val g = 7e9 * bits / 8 / 1e9
        listOf(
            fr(listOf(L("4,096 weights, σ = 0.02"), hist), listOf(F("fp32: 4,096 × 4 B =", "16,384 B")),
                "Trained weights look like a bell curve." to "Quantizing replaces each with the nearest of a few levels and stores the level’s index."),
            fr(listOf(L("absmax scaling"), hist, S(st("max |w|", f(amax, 4)), st("levels ±${lsNum(qmax)}", levels))),
                listOf(F("scale = ${f(amax, 4)} / ${lsNum(qmax)} =", lsExp(sc), LsInk.Green)),
                "$b-bit: $levels levels spread to the largest weight." to "One outlier sets the scale for everyone."),
            fr(listOf(L("levels actually used"), hist, S(st("levels available", levels), st("levels used", "$used", LsInk.Green))),
                listOf(F("q = round(w / scale)")),
                "Only $used of $levels levels get used." to if (b <= 4) "The tails are rare, so most weights crowd into a handful of levels near zero." else "At 8 bits there are levels to spare."),
            fr(listOf(L("reconstruction error, whole tensor"), S(st("MSE", lsExp(mse(deq))), st("SNR", f(snrT, 1) + " dB", LsInk.Pink))),
                listOf(F("SNR = 10·log₁₀(var(w) / MSE) =", f(snrT, 1) + " dB", LsInk.Pink)),
                "${f(snrT, 1)} dB signal-to-noise." to "Each bit removed costs roughly 6 dB."),
            fr(listOf(L("per-tensor vs per-block (64) scales"), B(listOf(lsRow("per-tensor", f(snrT, 1) + " dB", snrT / 50, LsInk.Slate), lsRow("block 64", f(snrB, 1) + " dB", snrB / 50, LsInk.Green, LsText.Strong)))),
                listOf(F("gain from blockwise scales =", "+${f(snrB - snrT, 1)} dB", LsInk.Green)),
                "Blockwise scales tame outliers." to "Each 64-weight block gets its own scale, so one large weight only coarsens its own block."),
            fr(listOf(L("7B model weights"), B(listOf(lsRow("fp16", f(14.0, 1) + " GB", 1.0, LsInk.Slate), lsRow("$b-bit + scales", f(g, 1) + " GB", g / 14, LsInk.Green, LsText.Strong)))),
                listOf(F("$b + 16/64 bits per weight =", f(bits, 2) + " bits"), F("7B ×", f(g, 1) + " GB", LsInk.Green)),
                "${f(14 / g, 1)}× smaller than fp16." to "Scales cost a quarter-bit per weight at block 64 — worth it for the error they remove."),
        )
    }
}

// ── Flash Attention: the online softmax ──

private fun flashStory(): LsLab {
    val sc = listOf(2.1, 3.4, 7.6, 5.0, 7.9, 1.2, 6.3, 4.4)
    val vals = listOf(1, 0, 2, 1, 3, 0, 2, 1)
    val nb = sc.size
    class H(val mOld: Double, val m: Double, val r: Double, val e: Double, val l: Double, val acc: Double)
    val hist = ArrayList<H>()
    var m = Double.NEGATIVE_INFINITY
    var l = 0.0
    var acc = 0.0
    for (i in 0 until nb) {
        val mn = max(m, sc[i])
        val r = if (m == Double.NEGATIVE_INFINITY) 0.0 else exp(m - mn)
        val e = exp(sc[i] - mn)
        l = l * r + e
        acc = acc * r + e * vals[i]
        hist += H(m, mn, r, e, l, acc)
        m = mn
    }
    val top = sc.max()
    val ex = sc.map { exp(it - top) }
    val exact = ex.indices.sumOf { ex[it] * vals[it] } / ex.sum()
    return LsLab(
        tabs = listOf("4K", "32K", "128K"), initialTab = 0,
        legend = listOf(LsInk.Yellow to "Current block", LsInk.Green to "Folded in", LsInk.Violet to "Result"),
    ) { tab ->
        val ns = listOf(4096.0, 32768.0, 131072.0)[tab]
        val nl = listOf("4K", "32K", "128K")[tab]
        List(nb + 3) { s ->
            val cur = if (s in 1..nb) s - 1 else null
            val chips = LsBlock.Chips(sc.mapIndexed { i, x ->
                LsTok("b$i", if (cur == i) LsTone.Cur else if (s > nb || (cur != null && i < cur)) LsTone.Done else LsTone.Fut, "s " + f(x, 1))
            })
            val head = listOf(L("one query row, 8 key blocks (s = score, v = value)"), chips)
            when {
                s == 0 -> fr(head, listOf(F("softmax needs max and Σ over all 8 blocks"), F("standard: write all scores to memory first")),
                    "Attention’s softmax seems to need the whole row." to "Flash Attention streams blocks through fast on-chip memory, keeping three running numbers: m, ℓ, acc.")
                cur != null -> {
                    val h = hist[cur]
                    val rescale = h.r < 1 && cur > 0
                    fr(head + S(st("running max m", f(h.m, 2), LsInk.Yellow), st("ℓ (denominator)", f(h.l, 4)), st("acc (numerator)", f(h.acc, 4))),
                        if (rescale) listOf(F("m: ${f(h.mOld, 2)} → ${f(h.m, 2)}, rescale ×", f(h.r, 4), LsInk.Yellow), F("ℓ = ℓ·${f(h.r, 4)} + e^(${f(sc[cur], 1)}−${f(h.m, 1)}) =", f(h.l, 4)))
                        else listOf(F("ℓ = ℓ + e^(${f(sc[cur], 1)}−${f(h.m, 1)}) =", f(h.l, 4)), F("acc += ${f(h.e, 4)} × v=${vals[cur]} =", f(h.acc, 4))),
                        if (rescale) "Block $cur beats the running max." to "Everything so far was scaled to the old max, so ℓ and acc are multiplied by ${f(h.r, 4)} before adding this block."
                        else "Block $cur folds in without rescaling." to if (cur == 0) "The first block sets the max." else "${f(sc[cur], 1)} is below the max ${f(h.m, 1)}, so it just adds its share.")
                }
                s == nb + 1 -> {
                    val h = hist[nb - 1]
                    fr(head + S(st("streamed result", f(h.acc / h.l, 6), LsInk.Lilac), st("exact softmax", f(exact, 6))),
                        listOf(F("out = acc / ℓ = ${f(h.acc, 4)} / ${f(h.l, 4)} =", f(h.acc / h.l, 6), LsInk.Lilac)),
                        "Identical to the exact softmax." to "Not an approximation: the rescaling makes the streamed sum mathematically equal.")
                }
                else -> {
                    val std = ns * ns * 2 / 1e6
                    val fl = ns * 2 * 3 / 1e3
                    fr(head + B(listOf(lsRow("score matrix", f(std, 0) + " MB", 1.0, LsInk.Pink), lsRow("Flash state", f(fl, 1) + " KB", .02, LsInk.Green, LsText.Strong))),
                        listOf(F("N² × 2 B = $nl² × 2 =", f(std, 0) + " MB per head", LsInk.Pink)),
                        "At $nl tokens the full score matrix would be ${f(std, 0)} MB per head." to "Flash never writes it. The work is the same; the slow-memory traffic disappears.")
                }
            }
        }
    }
}

// ── RLHF: the reward model's blind spot ──

private fun rlhfStory(): LsLab {
    class Beh(val n: String, val goal: Int, val steps: Int, val vase: Int)
    val behs = listOf(Beh("careful path", 1, 10, 0), Beh("shortcut via vase", 1, 6, 1), Beh("wander", 0, 12, 0))
    val ref = listOf(.5, .2, .3)
    fun tru(b: Beh) = 2.0 * b.goal - .1 * b.steps - 1.5 * b.vase
    fun prox(b: Beh) = 2.06 * b.goal - .1 * b.steps
    fun pol(bt: Double): List<Double> {
        val w = behs.mapIndexed { i, b -> ref[i] * exp(prox(b) / bt) }
        return w.map { it / w.sum() }
    }
    fun ex(p: List<Double>, fn: (Beh) -> Double) = behs.indices.sumOf { p[it] * fn(behs[it]) }
    val betas = listOf(0.1, 0.5, 2.0)
    return LsLab(
        tabs = listOf("β 0.1", "β 0.5", "β 2"), initialTab = 0,
        legend = listOf(LsInk.Violet to "Proxy reward", LsInk.Green to "True return", LsInk.Pink to "Hidden cost"),
    ) { tab ->
        val beta = betas[tab]
        val pi = pol(beta)
        val eProx = ex(pi, ::prox)
        val eTrue = ex(pi, ::tru)
        listOf(
            fr(listOf(L("three behaviours, reference policy"), B(behs.mapIndexed { i, b -> lsRow(b.n, pct(ref[i]), ref[i], LsInk.Slate) }), S(st("true reward", "2·goal − 0.1·steps − 1.5·vase"))),
                listOf(F("raters compare trajectories pairwise")),
                "RLHF learns a reward from human comparisons." to "Raters can see whether the goal was reached and how long it took. They can’t see the vase."),
            fr(listOf(L("fitted reward weights vs true"), B(listOf(lsBrow("goal", 2.06, 2.5, "2.06 / 2.0"), lsBrow("steps", -.1, 2.5, "−0.10 / −0.10"), lsBrow("vase", 0.0, 2.5, "0.00 / −1.50")))),
                listOf(F("vase weight:", "0 — never observed", LsInk.Red)),
                "The reward model is right about everything it was shown." to "It agrees with raters on held-out pairs, and is blind to the cost they never saw."),
            fr(listOf(L("policy π ∝ π_ref · exp(r / β), β = ${lsBetaText(beta)}"), B(behs.mapIndexed { i, b -> lsRow(b.n, pct(pi[i]), pi[i], if (i == 1) LsInk.Pink else LsInk.Violet, if (i == 1) LsText.Strong else LsText.Normal) })),
                listOf(F("proxy r: careful 1.06, vase 1.46, wander −1.20"), F("shortcut share =", pct(pi[1]), LsInk.Pink)),
                "The policy optimises the proxy." to "The shortcut scores 0.4 higher on the learned reward, so it gets more probability — limited only by the KL leash β."),
            fr(listOf(B(listOf(lsRow("proxy reward", f(eProx), max(eProx, 0.0) / 1.6, LsInk.Violet, LsText.Strong), lsRow("true return", f(eTrue), max(eTrue, 0.0) / 1.6, LsInk.Green, LsText.Strong)))),
                listOf(F("E[proxy] =", f(eProx, 3), LsInk.Lilac), F("E[true] =", f(eTrue, 3), LsInk.Green)),
                "The proxy goes up; the true return need not." to "Gap ${f(eProx - eTrue, 2)}: the policy is paid for breaking the vase."),
            fr(listOf(L("true return by KL strength β"), B(betas.map { bt -> val v = ex(pol(bt), ::tru); lsBrow("β ${lsBetaText(bt)}", v, 1.0, f(v, 3), pick(bt == beta)) }),
                S(st("reference policy", f(ex(ref, ::tru), 3)))),
                listOf(F("weak KL (β 0.1) → exploits the proxy")),
                "The KL penalty is the guard against reward hacking." to "Weak leash: the policy chases the proxy into the blind spot. Strong leash: little improvement at all."),
        )
    }
}
