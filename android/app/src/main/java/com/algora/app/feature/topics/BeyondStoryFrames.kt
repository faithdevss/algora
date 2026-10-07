package com.algora.app.feature.topics

import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

// ── Beyond-transformer and NLP-metric storyboard frames ──────────────────────
// SSMs, Mamba and RWKV run token by token, long-context costs from LLaMA-2-7B's published config, and
// perplexity, WER, BLEU, ROUGE, METEOR and MMLU each computed from its definition on the strings shown.
// Drawn by LlmStoryLabs.kt; the iOS port (BeyondStoryFrames.swift) matches it number for number.

internal val beyondStoryTopicIds = setOf(
    "ssm", "mamba", "rwkv", "long_context", "perplexity", "wer", "bleu", "rouge", "meteor", "mmlu",
)

internal fun beyondLab(topicId: String): LsLab? = when (topicId) {
    "ssm" -> ssmStory()
    "mamba" -> mambaStory()
    "rwkv" -> rwkvStory()
    "long_context" -> longContextStory()
    "perplexity" -> perplexityStory()
    "wer" -> werStory()
    "bleu" -> bleuStory()
    "rouge" -> rougeStory()
    "meteor" -> meteorStory()
    "mmlu" -> mmluStory()
    else -> null
}

private fun f(x: Double, d: Int = 2) = lsF(x, d)
private fun pct(p: Double) = lsPct(p)
private fun L(text: String) = LsBlock.Label(text)
private fun C(toks: List<LsTok>) = LsBlock.Chips(toks)
private fun B(rows: List<LsRow>) = LsBlock.Bars(rows)
private fun S(vararg rows: LsStat) = LsBlock.Stats(rows.toList())
private fun st(k: String, v: String, ink: LsInk? = null) = LsStat(k, v, ink)
private fun F(a: String, b: String = "", ink: LsInk? = null) = LsFx(a, b, ink)
private fun fr(blocks: List<LsBlock>, fx: List<LsFx>, cap: Pair<String, String>) = LsFrame(blocks, fx, cap.first, cap.second)
private fun pick(on: Boolean) = if (on) LsText.Strong else LsText.Muted

/** A number as JavaScript prints it: 0.95, 0.6, 2. */
private fun js(x: Double) = lsBetaText(x)

// ── State space model ──

private fun ssmStory(): LsLab {
    val xs = listOf(1.7, 1.0, 0.0, -0.5, 1.7, -0.7, -0.6, -0.4)
    val cc = listOf(.4, .3, .2, .1)
    return LsLab(
        tabs = listOf("slow Ā", "fast Ā"), initialTab = 0,
        legend = listOf(LsInk.Yellow to "Current token", LsInk.Blue to "State", LsInk.Violet to "Output / kernel"),
    ) { tab ->
        val a = listOf(listOf(.95, .9, .8, .6), listOf(.6, .4, .2, .1))[tab]
        class H(val h: List<Double>, val y: Double)
        val hs = ArrayList<H>()
        var h = listOf(0.0, 0.0, 0.0, 0.0)
        xs.forEach { x ->
            h = h.mapIndexed { i, v -> a[i] * v + x }
            hs += H(h, h.indices.sumOf { cc[it] * h[it] })
        }
        val kernel = List(8) { k -> a.indices.sumOf { cc[it] * a[it].pow(k) } }
        val ys = xs.indices.map { t -> (0..t).sumOf { j -> kernel[t - j] * xs[j] } }
        List(xs.size + 2) { s ->
            val cur = if (s in 1..8) s - 1 else null
            val chips = C(xs.mapIndexed { i, x -> LsTok(f(x, 1), if (cur == i) LsTone.Cur else if (s == 9 || (cur != null && i < cur)) LsTone.Done else LsTone.Fut, "t$i") })
            val done = if (s == 9) 8 else if (cur != null) cur + 1 else 0
            val yc = C(List(max(done, 1)) { i -> if (i < done) LsTok(f(hs[i].y, 1), LsTone.Ans) else LsTok("·", LsTone.Fut) })
            val head = listOf(L("input x"), chips)
            when {
                s == 0 -> fr(head + S(st("Ā per channel", a.joinToString(", ") { js(it) }), st("B̄", "1"), st("C", cc.joinToString(", ") { js(it) })),
                    listOf(F("h_t = Ā·h_{t−1} + B̄·x_t"), F("y_t = C·h_t")),
                    "A state space model is a linear recurrence." to "Four channels, each forgetting at its own rate Ā. Fixed numbers: the same for every token.")
                cur != null -> {
                    val p = hs[cur]
                    fr(head + listOf(L("state h, 4 channels"), B(p.h.mapIndexed { i, v -> lsBrow("Ā " + js(a[i]), v, 4.0) }), L("output y"), yc),
                        listOf(F("h₀ = ${js(a[0])}·${f(if (cur > 0) hs[cur - 1].h[0] else 0.0)} + ${f(xs[cur], 1)} =", f(p.h[0]), LsInk.Blue), F("y = C·h =", f(p.y, 3), LsInk.Lilac)),
                        "Token $cur: every channel decays, then adds x." to "One multiply-add per channel per token: constant cost and memory at any length.")
                }
                else -> fr(head + listOf(L("same outputs as one convolution, kernel K_k = Σ C·Āᵏ"), B(kernel.take(4).mapIndexed { i, k -> lsRow("K$i", f(k, 3), k, LsInk.Violet) })),
                    listOf(F("y₇ by recurrence =", f(hs[7].y, 4)), F("y₇ by convolution =", f(ys[7], 4), LsInk.Lilac)),
                    "Because Ā is fixed, the recurrence is a convolution." to "Train in parallel with one kernel, decode with the cheap recurrence — same numbers.")
            }
        }
    }
}

// ── Mamba: selective state ──

private fun mambaStory(): LsLab {
    val xs = listOf("SIG", "·", "·", "·", "·", "·", "·", "·")
    val a = 0.6
    return LsLab(
        tabs = listOf("LTI Ā = 0.6", "Selective"), initialTab = 1,
        legend = listOf(LsInk.Violet to "Signal", LsInk.Blue to "State", LsInk.Yellow to "Current"),
    ) { tab ->
        val sel = tab == 1
        class H(val h: Double, val sig: Double, val aBar: Double, val bBar: Double)
        val hs = ArrayList<H>()
        var h = 0.0
        var sig = 0.0
        xs.forEach { x ->
            val isSig = x == "SIG"
            val v = if (isSig) 1.0 else 0.1
            if (sel) {
                if (isSig) { h = 1.0; sig = 1.0 }
            } else {
                h = a * h + v
                sig = if (isSig) 1.0 else sig * a
            }
            hs += H(h, sig, if (sel) (if (isSig) 0.0 else 1.0) else a, if (sel) (if (isSig) 1.0 else 0.0) else 1.0)
        }
        List(xs.size + 2) { s ->
            val cur = if (s in 1..8) s - 1 else null
            val upto = if (s == 9) 8 else if (cur != null) cur + 1 else 0
            val chips = C(xs.mapIndexed { i, x ->
                LsTok(x, if (cur == i) LsTone.Cur else if (x == "SIG") LsTone.Ans else if (i < upto) LsTone.Done else LsTone.Fut, if (i < upto) "h " + f(hs[i].h) else "")
            })
            val head = listOf(L("one signal, then 7 fillers (value 0.1 each)"), chips)
            when {
                s == 0 -> fr(head, listOf(F(if (sel) "selective: Ā, B̄ computed from each token" else "LTI: Ā = 0.6 for every token")),
                    "Can the state keep one token across seven fillers?" to if (sel) "Mamba makes Ā and B̄ functions of the input: a filler can say “ignore me”." else "A plain SSM applies the same decay to everything.")
                cur != null -> {
                    val p = hs[cur]
                    fr(head + B(listOf(lsRow("state h", f(p.h, 3), p.h / 1.1, LsInk.Blue, LsText.Strong), lsRow("from SIG", f(p.sig, 3), p.sig / 1.1, LsInk.Violet, LsText.Strong))),
                        listOf(F("Ā = ${f(p.aBar)}, B̄ = ${f(p.bBar)}"), F("h =", f(p.h, 3), LsInk.Blue)),
                        when {
                            cur == 0 -> "The signal is written into the state." to if (sel) "The input gate opens fully: Ā = 0 clears the old state, B̄ = 1 writes SIG." else "h = 1.0."
                            sel -> "Filler $cur: the gate closes." to "Ā = 1, B̄ = 0 — the state passes through untouched."
                            else -> "Filler $cur: SIG fades to ${f(p.sig, 3)}." to "Fixed decay forgets on a timer, signal and noise alike."
                        })
                }
                else -> {
                    val a7 = a.pow(7)
                    fr(head + B(listOf(lsRow("LTI, SIG share", pct(a7 / (a7 + 0.1 * (1 - a7) / (1 - a))), a7, LsInk.Slate), lsRow("selective", pct(1.0), 1.0, LsInk.Violet, LsText.Strong))),
                        listOf(F("LTI: 0.6⁷ =", f(a7, 4)), F("selective: SIG intact =", "1.000", LsInk.Lilac)),
                        "Selection is what lets Mamba remember." to "Input-dependent Ā and B̄ break the convolution trick, so Mamba uses a parallel scan instead — linear time, content-aware memory.")
                }
            }
        }
    }
}

// ── RWKV ──

private fun rwkvStory(): LsLab {
    val keys = listOf(0.3, -1.1, -1.7, -1.7, 1.0, 3.5, -1.4, -0.9)
    val vals = listOf(1.8, -.2, -1.2, -1.0, 1.5, -2.1, .5, -.3)
    val u = 0.5
    val decays = listOf(0.1, 0.5, 2.0)
    return LsLab(
        tabs = listOf("w 0.1", "w 0.5", "w 2"), initialTab = 1,
        legend = listOf(LsInk.Yellow to "Current token", LsInk.Blue to "Weight", LsInk.Violet to "Read-out"),
    ) { tab ->
        val w = decays[tab]
        fun wkv(t: Int): Pair<List<Double>, Double> {
            val ws = (0..t).map { i -> if (i == t) exp(u + keys[i]) else exp(-(t - 1 - i) * w + keys[i]) }
            val sum = ws.sum()
            return ws.map { it / sum } to ws.indices.sumOf { ws[it] * vals[it] } / sum
        }
        List(10) { s ->
            val cur = if (s in 1..8) s - 1 else null
            val chips = C(keys.mapIndexed { i, k -> LsTok("k " + f(k, 1), if (cur == i) LsTone.Cur else if (cur != null && i < cur) LsTone.Done else LsTone.Fut, "v " + f(vals[i], 1)) })
            val head = listOf(L("8 tokens: key k (bid to be remembered), value v"), chips)
            when {
                s == 0 -> fr(head, listOf(F("weight(i) = e^(k_i − w·distance)"), F("w = ${js(w)}, bonus u = ${js(u)} for the current token")),
                    "RWKV replaces attention with a weighted average." to "There is no query: weights depend only on each token’s key and how far back it is.")
                cur != null -> {
                    val (ws, out) = wkv(cur)
                    val top = ws.indices.maxBy { ws[it] }
                    fr(head + listOf(L("weights at t = $cur"), B(ws.mapIndexed { i, x -> lsRow("t$i", pct(x), x, if (i == cur) LsInk.Yellow else LsInk.Blue, if (i == cur) LsText.Strong else LsText.Normal) })),
                        listOf(F("out_$cur = Σ weight·v =", f(out, 3), LsInk.Lilac)),
                        "t = $cur: token $top dominates." to when {
                            w > 1 -> "Strong decay: only the last token or two count."
                            keys[top] > 2 -> "Its key ${f(keys[top], 1)} outbids distance."
                            else -> "Old tokens fade by e^(−w) per step."
                        })
                }
                else -> fr(head + S(st("state per channel", "2 numbers (a, b)"), st("memory at t = 1M", "still 2 numbers")),
                    listOf(F("a ← e^(−w)·a + e^k·v"), F("b ← e^(−w)·b + e^k  ·  out = a / b")),
                    "The same average runs as an RNN." to "Numerator and denominator update in O(1) per token, so generation never grows a KV cache.")
            }
        }
    }
}

// ── Long context on LLaMA-2-7B ──

private fun longContextStory(): LsLab {
    val params = 6.74e9
    val layers = 32
    val d = 4096.0
    val kvTok = 2.0 * layers * 32 * 128 * 2
    val wts = params * 2
    val windows = listOf(4096.0, 32768.0, 131072.0)
    val names = listOf("4K", "32K", "128K")
    return LsLab(
        tabs = names, initialTab = 2,
        legend = listOf(LsInk.Violet to "Cache", LsInk.Pink to "Attention cost", LsInk.Yellow to "Position scale"),
    ) { tab ->
        val n = windows[tab]
        val nl = names[tab]
        val kv = n * kvTok
        val fW = 2 * params * n
        val fA = 4 * layers * n * n * d
        val scale = n / 4096
        listOf(
            fr(listOf(S(st("parameters", "6.74B"), st("layers · heads", "32 · 32"), st("head dim", "128"), st("trained context", "4,096"))),
                listOf(F("weights: 6.74B × 2 B =", f(wts / 1e9, 1) + " GB")),
                "A context window has three prices." to "LLaMA-2-7B’s published config, so every figure is checkable."),
            fr(listOf(L("KV cache, one sequence"), B(listOf(lsRow("weights", f(wts / 1e9, 1) + " GB", wts / 8e10, LsInk.Slate), lsRow("KV @ $nl", f(kv / 1e9, 1) + " GB", kv / 8e10, LsInk.Violet, LsText.Strong)))),
                listOf(F("2 × 32 × 32 × 128 × 2 B =", "512 KiB / token"), F("× ${lsNum(n)} =", f(kv / 1e9, 1) + " GB", LsInk.Lilac)),
                "Price one: memory." to if (kv > wts) "At $nl the cache outweighs the model itself." else "The cache grows linearly with every token kept."),
            fr(listOf(L("prefill FLOPs"), B(listOf(lsRow("weights 2·N·n", lsSci(fW), fW / (fW + fA), LsInk.Blue), lsRow("attention 4·L·n²·d", lsSci(fA), fA / (fW + fA), LsInk.Pink, LsText.Strong)))),
                listOf(F("attention share =", pct(fA / (fW + fA)), LsInk.Pink)),
                "Price two: quadratic compute." to if (fA > fW) "At $nl, attention costs more than all the weights combined." else "Short contexts are dominated by the weights; the n² term is waiting."),
            fr(listOf(S(st("trained on", "4,096 positions"), st("asked for", lsNum(n)), st("RoPE interpolation factor", "${js(scale)}×", LsInk.Yellow))),
                listOf(F("positions scaled by 4096 / ${lsNum(n)} =", f(1 / scale, 4))),
                "Price three: positions it never saw." to if (scale > 1) "Position interpolation squeezes new positions into the trained range, then fine-tunes briefly." else "Within the trained window: no extension needed."),
            fr(listOf(L("memory per sequence, three windows"), B(windows.mapIndexed { j, m -> lsRow(names[j], f((wts + m * kvTok) / 1e9, 1) + " GB", (wts + m * kvTok) / 9e10, if (m == n) LsInk.Violet else LsInk.Slate, pick(m == n)) })),
                listOf(F("weights + KV =", f((wts + kv) / 1e9, 1) + " GB", LsInk.Lilac)),
                "The advertised number names none of these costs." to "Grouped-query attention, cache quantization and sliding windows each attack one of them."),
        )
    }
}

// ── Perplexity of a bigram model ──

private fun perplexityStory(): LsLab {
    val corpus = listOf("the cat sat on the mat", "the dog sat on the rug", "the cat ate the fish")
    val uni = HashMap<String, Int>()
    val bi = HashMap<String, Int>()
    val voc = linkedSetOf("</s>")
    corpus.forEach { c ->
        val t = listOf("<s>") + c.split(" ") + "</s>"
        t.forEach { if (it != "<s>") voc += it }
        for (i in 0 until t.size - 1) {
            uni[t[i]] = (uni[t[i]] ?: 0) + 1
            val b = t[i] + " " + t[i + 1]
            bi[b] = (bi[b] ?: 0) + 1
        }
    }
    val v = voc.size
    fun prob(a: String, b: String) = ((bi["$a $b"] ?: 0) + 1.0) / ((uni[a] ?: 0) + v)
    class Tok(val w: String, val p: Double, val seen: Boolean)
    class Score(val ps: List<Tok>, val h: Double, val ppl: Double)
    fun score(c: String): Score {
        val t = listOf("<s>") + c.split(" ") + "</s>"
        val ps = (1 until t.size).map { Tok(t[it], prob(t[it - 1], t[it]), bi.containsKey(t[it - 1] + " " + t[it])) }
        val h = ps.sumOf { -ln(it.p) / ln(2.0) } / ps.size
        return Score(ps, h, 2.0.pow(h))
    }
    val tests = listOf("the cat sat on the rug", "the rug ate the cat", "cat the on sat rug the")
    val names = listOf("sentence", "reordered", "scrambled")
    return LsLab(
        tabs = names, initialTab = 0,
        legend = listOf(LsInk.Blue to "Seen bigram", LsInk.Yellow to "Unseen bigram", LsInk.Violet to "Perplexity"),
    ) { k ->
        val r = score(tests[k])
        fun bits(p: Double) = -ln(p) / ln(2.0)
        listOf(
            fr(listOf(L("training corpus"), LsBlock.Stats(corpus.mapIndexed { i, c -> st("sentence ${i + 1}", c) }), S(st("vocabulary V", "$v"))),
                listOf(F("P(w | prev) = (c(prev,w) + 1) / (c(prev) + V)")),
                "A bigram model with add-1 smoothing." to "Perplexity needs no reference answer — only the model and some text."),
            fr(listOf(L("P(token | previous) for “${tests[k]}”"), C(r.ps.map { LsTok(it.w, if (it.seen) LsTone.Plain else LsTone.Cur, f(it.p, 3)) })),
                listOf(F("P(${r.ps[0].w} | <s>) =", f(r.ps[0].p, 3))),
                "Score every token given the one before." to "Yellow tokens follow a bigram never seen in training; smoothing gives them a small but non-zero probability."),
            fr(listOf(L("surprisal −log₂ P, bits"), B(r.ps.map { lsRow(it.w, f(bits(it.p), 2), bits(it.p) / 5, if (it.seen) LsInk.Blue else LsInk.Yellow) })),
                listOf(F("total =", f(r.h * r.ps.size, 2) + " bits")),
                "Surprisal: how many bits each token cost." to "Unseen bigrams cost the most."),
            fr(listOf(S(st("mean surprisal H", f(r.h, 3) + " bits"), st("perplexity 2^H", f(r.ppl, 3), LsInk.Lilac))),
                listOf(F("PPL = 2^${f(r.h, 3)} =", f(r.ppl, 3), LsInk.Lilac)),
                "Perplexity ${f(r.ppl, 2)}." to "As uncertain as choosing uniformly among ${f(r.ppl, 1)} words at every step."),
            fr(listOf(L("perplexity of three test strings"), B(tests.mapIndexed { j, t -> val p = score(t).ppl; lsRow(names[j], f(p, 2), p / 14, if (j == k) LsInk.Violet else LsInk.Slate, pick(j == k)) })),
                listOf(F("lower = less surprised")),
                "Word order matters to the score." to "Same words, worse order, higher perplexity. Only compare models that share a tokenizer and test set."),
        )
    }
}

// ── WER ──

private class WerOp(val t: Char, val w: String = "", val r: String = "")

/** Minimum word edit distance with a backtrace: 'o' match, 's' substitution, 'd' deletion, 'i' insertion. */
private fun werAlign(ref: List<String>, hyp: List<String>): List<WerOp> {
    val n = ref.size
    val m = hyp.size
    val d = Array(n + 1) { i -> IntArray(m + 1) { j -> if (i == 0) j else if (j == 0) i else 0 } }
    for (i in 1..n) for (j in 1..m) {
        d[i][j] = minOf(d[i - 1][j] + 1, d[i][j - 1] + 1, d[i - 1][j - 1] + if (ref[i - 1] == hyp[j - 1]) 0 else 1)
    }
    val ops = ArrayList<WerOp>()
    var i = n
    var j = m
    while (i > 0 || j > 0) {
        if (i > 0 && j > 0 && d[i][j] == d[i - 1][j - 1] + if (ref[i - 1] == hyp[j - 1]) 0 else 1) {
            ops.add(0, if (ref[i - 1] == hyp[j - 1]) WerOp('o', hyp[j - 1]) else WerOp('s', hyp[j - 1], ref[i - 1]))
            i--; j--
        } else if (i > 0 && d[i][j] == d[i - 1][j] + 1) {
            ops.add(0, WerOp('d', r = ref[i - 1]))
            i--
        } else {
            ops.add(0, WerOp('i', hyp[j - 1]))
            j--
        }
    }
    return ops
}

private fun werNorm(s: String) = s.lowercase().replace(".", "").replace(",", "")

private fun werStory(): LsLab {
    val ref = "the model did not converge on the second run".split(" ")
    val hyps = listOf("model did not converge second run", "the model did converge on the second run", "The model did not converge on the 2nd run.")
    val n = ref.size
    fun rate(j: Int): Double {
        val hh = (if (j == 2) werNorm(hyps[j]) else hyps[j]).split(" ")
        return werAlign(ref, hh).count { it.t != 'o' }.toDouble() / n
    }
    return LsLab(
        tabs = listOf("dropped", "“not” gone", "format"), initialTab = 1,
        legend = listOf(LsInk.Green to "Match", LsInk.Red to "Substitution", LsInk.Slate to "Deletion", LsInk.Yellow to "Meaning reversed"),
    ) { k ->
        List(5) { s ->
            val norm = k == 2 && s >= 3
            val hyp = (if (norm) werNorm(hyps[k]) else hyps[k]).split(" ")
            val ops = werAlign(ref, hyp)
            val subs = ops.count { it.t == 's' }
            val dels = ops.count { it.t == 'd' }
            val ins = ops.count { it.t == 'i' }
            val wer = (subs + dels + ins).toDouble() / n
            val chips = ops.map {
                when (it.t) {
                    'o' -> LsTok(it.w, LsTone.Done)
                    's' -> LsTok(it.w, LsTone.Err, "≠ " + it.r)
                    'd' -> LsTok(it.r, LsTone.Fut, "deleted")
                    else -> LsTok(it.w, LsTone.Cur, "inserted")
                }
            }
            val head = listOf(L("reference"), C(ref.map { LsTok(it) }), L("hypothesis" + if (norm) " (normalised)" else ""), C(if (s == 0) hyp.map { LsTok(it) } else chips))
            when (s) {
                0 -> fr(head, listOf(F("reference:", "$n words")),
                    "WER compares a transcript with a reference, word by word." to "It counts the fewest substitutions, deletions and insertions that turn one into the other.")
                1 -> fr(head, listOf(F("edit distance (dynamic programming) =", "${subs + dels + ins}", LsInk.Pink)),
                    "Align with minimum edit distance." to "Green matches, red substitutions, grey deletions.")
                2 -> fr(head + B(listOf(lsRow("substitutions", "$subs", subs / 4.0, LsInk.Pink), lsRow("deletions", "$dels", dels / 4.0, LsInk.Pink), lsRow("insertions", "$ins", ins / 4.0, LsInk.Pink))),
                    listOf(F("WER = ($subs + $dels + $ins) / $n =", f(wer, 3), LsInk.Pink)),
                    "WER ${f(wer, 3)}." to when (k) {
                        1 -> "One deleted word, a low score — and the meaning is reversed."
                        0 -> "Three function words gone, meaning intact, and a third of the words counted wrong."
                        else -> "Case and punctuation count as errors before normalising."
                    })
                3 -> fr(head, listOf(F("WER =", f(wer, 3), LsInk.Pink)),
                    if (k == 2) "Normalise first: lowercase, strip punctuation." to "Only “2nd” vs “second” remains: WER ${f(wer, 3)}. Normalisation rules change the reported number."
                    else "WER weighs every word equally." to if (k == 1) "“not” costs the same as “the”. A metric for transcription accuracy, not for meaning." else "Dropping “the” costs as much as dropping “not”.")
                else -> fr(head + B(hyps.indices.map { j -> val d = rate(j); lsRow(listOf("dropped words", "“not” deleted", "format")[j], f(d, 3), d / 0.4, if (j == 1) LsInk.Yellow else LsInk.Pink, pick(j == k)) }),
                    listOf(F("lowest WER, reversed meaning:", "“not” deleted", LsInk.Yellow)),
                    "The best-scoring hypothesis is the most wrong." to "Report WER alongside a check on meaning-bearing words.")
            }
        }
    }
}

// ── BLEU ──

internal class LsNgram(val m: Int, val raw: Int, val t: Int, val p: Double)

internal class LsBleu(val ps: List<LsNgram>, val bp: Double, val b: Double)

/** Unsmoothed sentence BLEU: clipped 1- to 4-gram precision, geometric mean, brevity penalty. */
internal fun lsBleu(c: List<String>, r: List<String>): LsBleu {
    fun grams(t: List<String>, n: Int): Map<String, Int> {
        val o = LinkedHashMap<String, Int>()
        for (i in 0..t.size - n) { val g = t.subList(i, i + n).joinToString(" "); o[g] = (o[g] ?: 0) + 1 }
        return o
    }
    val ps = (1..4).map { n ->
        val cg = grams(c, n)
        val rg = grams(r, n)
        var m = 0
        var raw = 0
        var t = 0
        cg.forEach { (g, cnt) -> t += cnt; raw += if (rg.containsKey(g)) cnt else 0; m += minOf(cnt, rg[g] ?: 0) }
        LsNgram(m, raw, t, if (t > 0) m.toDouble() / t else 0.0)
    }
    val bp = if (c.size > r.size) 1.0 else exp(1 - r.size.toDouble() / c.size)
    val b = if (ps.any { it.p == 0.0 }) 0.0 else bp * exp(ps.sumOf { ln(it.p) } / 4)
    return LsBleu(ps, bp, b)
}

private fun bleuStory(): LsLab {
    val ref = "the cat is on the mat".split(" ")
    val cands = listOf("the the the the the the the the", "the cat sat on the mat", "on the mat the cat is").map { it.split(" ") }
    val names = listOf("repetition", "one word off", "reordered")
    val rc = ref.groupingBy { it }.eachCount()
    return LsLab(
        tabs = names, initialTab = 0,
        legend = listOf(LsInk.Violet to "Credited", LsInk.Blue to "Clipped precision", LsInk.Pink to "Raw"),
    ) { k ->
        val c = cands[k]
        val r = lsBleu(c, ref)
        List(5) { s ->
            val used = HashMap<String, Int>()
            val chips = C(c.map { w ->
                val ok = (used[w] ?: 0) < (rc[w] ?: 0)
                if (ok) used[w] = (used[w] ?: 0) + 1
                LsTok(w, if (s == 0) LsTone.Plain else if (ok) LsTone.Ans else LsTone.Fut, if (s >= 1) (if (ok) "credited" else if (rc.containsKey(w)) "over cap" else "no match") else "")
            })
            val head = listOf(L("reference: the cat is on the mat"), chips)
            when (s) {
                0 -> fr(head, listOf(F("candidate length", "${c.size} vs reference ${ref.size}")),
                    "BLEU scores n-gram overlap with a reference." to "Precision of 1- to 4-grams, combined, with a penalty for being too short.")
                1 -> {
                    val p = r.ps[0]
                    fr(head + B(listOf(lsRow("unclipped", "${p.raw}/${p.t}", p.raw.toDouble() / p.t, LsInk.Pink), lsRow("clipped", "${p.m}/${p.t}", p.m.toDouble() / p.t, LsInk.Blue, LsText.Strong))),
                        listOf(F("clip each n-gram at its reference count")),
                        "Clipping is the whole defence against repetition." to if (k == 0) "“the” appears twice in the reference, so only 2 of 8 count." else "Each word is credited at most as often as the reference uses it.")
                }
                2 -> fr(head + B(r.ps.mapIndexed { i, p -> lsRow("p${i + 1}", "${p.m}/${p.t}", p.p, if (p.p > 0) LsInk.Blue else LsInk.Red) }),
                    listOf(F("p₁ … p₄ =", r.ps.joinToString(", ") { f(it.p, 2) })),
                    "Precision at each n-gram length." to if (r.ps[3].p == 0.0) "No 4-gram matches: unsmoothed BLEU will be zero." else "Longer n-grams reward correct word order.")
                3 -> fr(head, listOf(F("BP = ${if (c.size > ref.size) "1 (not shorter)" else "e^(1 − r/c)"} =", f(r.bp, 3))),
                    "Brevity penalty." to "Precision alone would reward a one-word output; BP punishes candidates shorter than the reference.")
                else -> fr(head + B(cands.mapIndexed { j, cc -> val b = lsBleu(cc, ref).b; lsRow(names[j], f(b, 3), b, if (j == k) LsInk.Violet else LsInk.Slate, pick(j == k)) }),
                    listOf(F("BLEU = BP · (p₁p₂p₃p₄)^¼ =", f(r.b, 3), LsInk.Lilac)),
                    "BLEU ${f(r.b, 3)}." to if (k == 2) "All six words right, order shuffled: zero. BLEU is a corpus metric and harsh on single sentences." else "One substitution breaks every n-gram that crosses it.")
            }
        }
    }
}

// ── ROUGE-1 ──

private fun rougeStory(): LsLab {
    val ref = "the council approved a new budget for city parks on monday".split(" ")
    val cands = listOf(
        "council approved new budget for city parks monday",
        "on monday the city council met for three hours and after a long debate approved a new budget that raises spending on city parks and libraries by ten percent next year",
        "budget",
    ).map { it.split(" ") }
    val names = listOf("summary", "whole doc", "“budget”")
    class R(val m: Int, val r: Double, val p: Double, val f1: Double)
    fun score(c: List<String>): R {
        val rc = ref.groupingBy { it }.eachCount()
        val cc = c.groupingBy { it }.eachCount()
        val m = cc.entries.sumOf { (w, n) -> minOf(n, rc[w] ?: 0) }
        val rec = m.toDouble() / ref.size
        val prec = m.toDouble() / c.size
        return R(m, rec, prec, if (rec + prec > 0) 2 * rec * prec / (rec + prec) else 0.0)
    }
    return LsLab(
        tabs = names, initialTab = 1,
        legend = listOf(LsInk.Violet to "In the reference", LsInk.Pink to "Recall", LsInk.Blue to "Precision"),
    ) { k ->
        val c = cands[k]
        val r = score(c)
        List(5) { s ->
            val head = listOf(
                L("reference summary (11 words)"),
                C(ref.map { LsTok(it, if (s >= 1 && it in c) LsTone.Ans else LsTone.Plain) }),
                S(st("candidate", "${names[k]}, ${c.size} words")),
            )
            when (s) {
                0 -> fr(head, listOf(F("ROUGE-1: unigram overlap with the reference")),
                    "ROUGE was built for summaries." to "Originally recall-first: how much of the reference does the candidate cover?")
                1 -> fr(head, listOf(F("overlapping words (clipped) =", "${r.m}", LsInk.Lilac)),
                    "Purple: reference words the candidate contains." to if (k == 1) "The whole document contains all of them — without summarising anything." else "Counts are clipped, as in BLEU.")
                2 -> fr(head, listOf(F("recall = ${r.m} / 11 =", f(r.r, 3), LsInk.Pink)),
                    "Recall ${f(r.r, 3)}." to if (k == 1) "Submit the entire document and recall is perfect. Recall alone can’t be the score." else "Coverage of the reference.")
                3 -> fr(head, listOf(F("precision = ${r.m} / ${c.size} =", f(r.p, 3), LsInk.Blue)),
                    "Precision ${f(r.p, 3)}." to when (k) {
                        2 -> "One correct word: perfect precision, almost no recall."
                        1 -> "Precision exposes the padding."
                        else -> "Almost every word earns its place."
                    })
                else -> fr(head + B(cands.mapIndexed { j, cc -> val x = score(cc); lsRow(names[j], "F1 ${f(x.f1, 3)}", x.f1, if (j == k) LsInk.Violet else LsInk.Slate, pick(j == k)) }),
                    listOf(F("F1 = 2PR / (P + R) =", f(r.f1, 3), LsInk.Lilac)),
                    "Report F1, not recall." to "Both exploits — the whole document and one word — collapse under F1; the real summary wins.")
            }
        }
    }
}

// ── METEOR ──

private fun meteorStory(): LsLab {
    val ref = "the committee approved the revised budget on friday".split(" ")
    val hyps = listOf(
        "on friday the panel signed off on the amended budget",
        "budget the friday on approved committee the revised",
        "the committee approved the revised budget on friday",
    ).map { it.split(" ") }
    class M(val al: List<Int>, val m: Int, val ch: Int, val p: Double, val r: Double, val f: Double, val pen: Double, val score: Double)
    fun met(h: List<String>): M {
        val used = BooleanArray(ref.size)
        val al = h.map { w ->
            val j = ref.indices.firstOrNull { !used[it] && ref[it] == w } ?: -1
            if (j >= 0) used[j] = true
            j
        }
        val m = al.count { it >= 0 }
        var ch = 0
        var prev = -2
        al.forEach { a ->
            if (a < 0) { prev = -2; return@forEach }
            if (a != prev + 1) ch++
            prev = a
        }
        val p = m.toDouble() / h.size
        val r = m.toDouble() / ref.size
        val fm = if (m > 0) 10 * p * r / (r + 9 * p) else 0.0
        val pen = if (m > 0) 0.5 * (ch.toDouble() / m).pow(3) else 0.0
        return M(al, m, ch, p, r, fm, pen, fm * (1 - pen))
    }
    return LsLab(
        tabs = listOf("paraphrase", "shuffled", "exact"), initialTab = 0,
        legend = listOf(LsInk.Violet to "Aligned", LsInk.Yellow to "Fragmentation", LsInk.Blue to "METEOR"),
    ) { k ->
        val h = hyps[k]
        val r = met(h)
        val b = lsBleu(h, ref).b
        val head = listOf(
            L("alignment to: the committee approved the revised budget on friday"),
            C(h.mapIndexed { i, w -> if (r.al[i] >= 0) LsTok(w, LsTone.Ans, "→ ${r.al[i]}") else LsTok(w, LsTone.Fut) }),
        )
        listOf(
            fr(head, listOf(F("aligned words:", "${r.m} of ${h.size}", LsInk.Lilac)),
                "METEOR aligns words one-to-one." to "Exact matches first; the full metric adds stems and WordNet synonyms."),
            fr(head + S(st("precision", "${r.m}/${h.size} = ${f(r.p, 3)}"), st("recall", "${r.m}/${ref.size} = ${f(r.r, 3)}")),
                listOf(F("P =", f(r.p, 3)), F("R =", f(r.r, 3))),
                "Precision and recall over aligned words." to "Unlike BLEU, recall counts."),
            fr(head, listOf(F("F = 10PR / (R + 9P) =", f(r.f, 4), LsInk.Lilac)),
                "Recall weighted 9× over precision." to "Missing content is punished more than extra words."),
            fr(head, listOf(F("chunks =", "${r.ch}"), F("penalty = 0.5 · (${r.ch}/${r.m})³ =", f(r.pen, 4), LsInk.Yellow)),
                "${r.ch} chunk${if (r.ch > 1) "s" else ""}: contiguous runs in matching order." to if (k == 1) "Every word present, order broken: ${r.ch} chunks for ${r.m} matches, heavy penalty." else "Fewer chunks means better word order."),
            fr(head + B(listOf(lsRow("BLEU", f(b, 4), b, LsInk.Slate), lsRow("METEOR", f(r.score, 4), r.score, LsInk.Blue, LsText.Strong))),
                listOf(F("METEOR = F · (1 − penalty) =", f(r.score, 4), LsInk.Blue)),
                when (k) {
                    0 -> "BLEU 0, METEOR ${f(r.score, 2)} on a fair paraphrase."
                    1 -> "Shuffled words: METEOR’s penalty bites."
                    else -> "An exact copy scores near 1 on both."
                } to "METEOR was built to correlate better with human judgements at the sentence level."),
        )
    }
}

// ── MMLU: floors and error bars ──

private fun mmluStory(): LsLab {
    val models = listOf("A" to .712, "B" to .698, "C" to .655, "D" to .310)
    fun cc(p: Double) = (p - .25) / .75
    val sizes = listOf(14042.0, 1000.0, 100.0)
    return LsLab(
        tabs = listOf("n 14,042", "n 1,000", "n 100"), initialTab = 2,
        legend = listOf(LsInk.Blue to "Reported", LsInk.Green to "Chance-corrected", LsInk.Red to "Not significant"),
    ) { tab ->
        val n = sizes[tab]
        fun se(p: Double) = sqrt(p * (1 - p) / n)
        val diff = .712 - .698
        val sd = sqrt(se(.712).pow(2) + se(.698).pow(2))
        val z = diff / sd
        val zInk = if (z > 2) LsInk.Green else LsInk.Red
        listOf(
            fr(listOf(L("reported accuracy"), B(models.map { (m, p) -> lsRow("model $m", f(p, 3), p, LsInk.Blue) })),
                listOf(F("14,042 four-way questions, 57 subjects")),
                "MMLU is quoted as one accuracy." to "Before comparing models, that number needs a floor and an error bar."),
            fr(listOf(L("chance-corrected (acc − 0.25) / 0.75"), B(models.map { (m, p) -> lsRow("model $m", f(cc(p), 3), max(cc(p), 0.0), LsInk.Green) })),
                listOf(F("model D: (0.310 − 0.25) / 0.75 =", f(cc(.31), 3), LsInk.Green)),
                "Guessing scores 0.25, not 0." to "Model D’s 31% is only 8% of the way from guessing to perfect."),
            fr(listOf(L("±2 standard errors, n = ${lsNum(n)}"), B(models.map { (m, p) -> lsRow("model $m", "${f(p, 3)} ± ${f(2 * se(p), 3)}", p, LsInk.Blue) })),
                listOf(F("SE = √(p(1−p)/n) at p 0.7 =", f(se(.7), 4))),
                "At n = ${lsNum(n)}, the error bar is ±${f(2 * se(.7) * 100, 1)} points." to if (n < 1000) "One subject’s ~100 questions can’t rank close models." else "Full MMLU is large enough for a tight interval."),
            fr(listOf(S(st("A − B", "0.014"), st("SE of difference", f(sd, 4)), st("z", f(z, 2), zInk))),
                listOf(F("z = 0.014 / SE =", f(z, 2), zInk)),
                if (z > 2) "A beats B — significant at this n." to "Only with all 14,042 questions does a 1.4-point gap clear two standard errors."
                else "A vs B is a coin flip at this n." to "Leaderboard gaps this small need the full test set, or they are noise."),
        )
    }
}
