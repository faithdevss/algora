package com.algora.app.feature.topics

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.math.tanh

// ── Recurrent-network storyboard frames ──────────────────────────────────────
// RNNs, BPTT, LSTMs and GRUs, bidirectional RNNs, encoder-decoders and seq2seq beam search, drawn by
// DeepStoryLabs.kt. The recurrences are run step by step on small fixed inputs; BPTT's noise comes from
// the same plain LCG as the other labs so the iOS port (RnnStoryFrames.swift) matches.

internal val rnnStoryTopicIds = setOf("rnn", "bptt", "lstm_gru", "bidirectional_rnn", "encoder_decoder", "seq2seq")

internal fun rnnLab(topicId: String): DkLab? = when (topicId) {
    "rnn" -> rnnIntroLab()
    "bptt" -> bpttLab()
    "lstm_gru" -> lstmLab()
    "bidirectional_rnn" -> biRnnLab()
    "encoder_decoder" -> encDecLab()
    "seq2seq" -> seq2seqLab()
    else -> null
}

private fun n(v: Double, d: Int = 2) = dkNum(v, d)

private fun t(v: Double, d: Int = 2): String {
    val s = dkNum(v, d)
    return if ('.' in s) s.trimEnd('0').trimEnd('.') else s
}

private fun pct(share: Double, d: Int = 1) = n(share * 100, d) + "%"

private fun legend(ink: DkInk, label: String, style: SwatchStyle = SwatchStyle.Fill) = DkLegend(ink, style, label)

private fun stepActions(frames: List<DkFrame>, action: String) =
    frames.mapIndexed { i, f -> DkFrame(f.header, f.stage, f.legend, f.formula, f.headline, f.body, f.chips, if (i == frames.lastIndex) "Start Over" else action) }

private const val SupDigits = "⁰¹²³⁴⁵⁶⁷⁸⁹"
private fun sup(k: Int) = k.toString().map { SupDigits[it - '0'] }.joinToString("")

private fun sci(v: Double): String {
    var e = floor(log10(v)).toInt()
    var m = floor(v / 10.0.pow(e) * 10 + 0.5) / 10
    if (m >= 10) { m /= 10; e += 1 }
    return n(m, 1) + "e" + (if (e < 0) "−" else "") + abs(e)
}

// ── RNNs: one cell unrolled ──

private val rnnX = listOf(0.80, -0.40, 0.60, 0.20)
private const val RnnW = 0.7
private const val RnnU = 0.6

private fun rnnIntroLab(): DkLab = DkLab(DkControl.Track) { _, _ ->
    val h = ArrayList<Double>()
    var prev = 0.0
    rnnX.forEach { x -> prev = tanh(RnnW * x + RnnU * prev); h += prev }
    // ∂h4/∂x1: through x1's own step, then three recurrent hops.
    val reach = RnnW * (1 - h[0] * h[0]) * (1..3).fold(1.0) { acc, k -> acc * RnnU * (1 - h[k] * h[k]) }
    fun stage(done: Int, current: Int?) = DkTiles(
        listOf(
            DkTileRow(rnnX.mapIndexed { i, x -> DkTile("x${i + 1}", if (i <= (current ?: done - 1)) DkTileTone.Source else DkTileTone.Plain, sub = n(x)) }),
            DkTileRow(h.mapIndexed { i, v ->
                when {
                    i == current -> DkTile("h${i + 1}", DkTileTone.Current, sub = n(v, 3))
                    i < done -> DkTile("h${i + 1}", DkTileTone.Emitted, sub = n(v, 3))
                    else -> DkTile("h${i + 1}", DkTileTone.Plain, sub = "–")
                }
            }),
        ),
        arrows = true,
    )
    fun stepLine(k: Int): List<String> {
        val hp = if (k == 0) 0.0 else h[k - 1]
        val z = RnnW * rnnX[k] + RnnU * hp
        return listOf(
            "h${k + 1} = tanh(w·x${k + 1} + u·h$k)",
            "= tanh(${t(RnnW)}·${n(rnnX[k])} + ${t(RnnU)}·${n(hp, 3)}) = tanh(${n(z, 3)}) = {${n(h[k], 3)}}",
        )
    }
    val header = "one cell, unrolled over 4 timesteps · w = ${t(RnnW)}, u = ${t(RnnU)}"
    val legend = listOf(legend(DkInk.Blue, "Input"), legend(DkInk.Green, "Computed state"), legend(DkInk.Yellow, "Current", SwatchStyle.Ring))
    val frames = listOf(
        DkFrame(
            header, stage(0, null), legend.take(1), listOf("h_t = tanh(w·x_t + u·h_{t−1}),  h0 = 0", "the same w and u at every step"),
            "An RNN reads a sequence {one step at a time}.",
            "Its state h carries forward whatever it has seen so far, so each step depends on the past.",
        ),
        DkFrame(
            header, stage(1, 0), legend, stepLine(0),
            "h1 sees only {x1}: ${n(h[0], 3)}.", "With no past, h0 = 0 and the step is a single tanh neuron.",
        ),
        DkFrame(
            header, stage(2, 1), legend, stepLine(1),
            "h2 = {${n(h[1], 3)}}: the negative x2 nearly cancels the past.",
            "u·h1 = ${n(RnnU * h[0], 3)} pulls up while w·x2 = ${n(RnnW * rnnX[1], 3)} pulls down.",
        ),
        DkFrame(
            header, stage(3, 2), legend, stepLine(2),
            "h3 mixes the new input with {everything before it}.",
            "The same w and u are reused at every step, so a sequence of any length needs just 2 weights here.",
        ),
        DkFrame(
            header, stage(4, 3), legend, stepLine(3),
            "h4 = {${n(h[3], 3)}} summarises all four inputs.",
            "A classifier reading only h4 sees the whole sequence, compressed into this one state.",
        ),
        DkFrame(
            header, stage(4, null), legend.take(2), listOf("unrolled: 4 layers, all sharing w and u", "gradients for w add up over the 4 steps"),
            "Unrolled, it's a 4-layer net with {tied weights}.",
            "Training backpropagates through these copies, which is BPTT, and sums each step's gradient for the shared w and u.",
        ),
        DkFrame(
            header, stage(4, null), legend.take(2), listOf("∂h4/∂x1 = w·(1 − h1²) · Π u·(1 − h_k²)", "= {${n(reach, 3)}}"),
            "x1 still reaches h4, scaled by {${n(reach, 3)}}.",
            "Every step multiplies by u·tanh′ < 1, so early inputs fade. Longer sequences make it worse: that is the vanishing gradient BPTT shows.",
        ),
    )
    stepActions(frames, "Next Timestep")
}

// ── BPTT: the gradient back to the cue ──

private val bpttUs = listOf(0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9, 0.95, 1.0, 1.2)
private const val BpttSteps = 10

private val bpttInputs: List<Double> by lazy {
    var s = 909L
    fun u(): Double { s = (s * 1103515245L + 12345L) and 0x7fffffffL; return s / 2147483648.0 }
    fun g(): Double { val a = max(u(), 1e-12); val b = u(); return sqrt(-2 * ln(a)) * cos(2 * PI * b) }
    listOf(1.0) + List(BpttSteps - 1) { 0.3 * g() }
}

/** |∂h10/∂h_t| for t = 1..10 with recurrent weight [u]. */
private fun bpttGrads(u: Double): List<Double> {
    val h = ArrayList<Double>()
    var prev = 0.0
    bpttInputs.forEach { x -> prev = tanh(x + u * prev); h += prev }
    val out = DoubleArray(BpttSteps)
    out[BpttSteps - 1] = 1.0
    for (k in BpttSteps - 2 downTo 0) out[k] = out[k + 1] * abs(u * (1 - h[k + 1] * h[k + 1]))
    return out.toList()
}

private fun bpttLab(): DkLab = DkLab(DkControl.StepperOnly, stepper = DkStepper("recurrent u", bpttUs, bpttUs.indexOf(0.6)) { t(it) }) { _, p ->
    val u = bpttUs[p]
    val g = bpttGrads(u)
    val ref = bpttGrads(0.95)
    val logs = (g + ref).map { log10(it) }
    val lo = floor(logs.min()) - 0.1
    val hi = max(0.0, logs.max()) + 0.15
    val ticks = (floor(hi).toInt() downTo kotlin.math.ceil(lo).toInt()).map { k -> k.toDouble() to if (k == 0) "1" else "1e" + (if (k < 0) "−" else "") + abs(k) }
    fun plot(hot: Set<Int>, dim: Set<Int> = emptySet()) = DkPlot(
        0.4 to BpttSteps + 0.6, lo to hi, ticks, "", "",
        listOf(DkLine(ref.mapIndexed { i, v -> DkP(i + 1.0, log10(v)) }, DkInk.Green, dashed = true)),
        axis = false,
        bars = g.mapIndexed { i, v -> DkPBar(i + 1.0, log10(v), if (i in hot) DkInk.Yellow else if (i in dim) DkInk.Slate else DkInk.Pink) },
        xTicks = (1..BpttSteps).map { DkTick(it.toDouble(), "t$it", hot = it - 1 in hot) },
    )
    val uT = t(u)
    val header = "|∂h10 / ∂h_t| · cue at t1, noise t2–t10"
    val legend = listOf(legend(DkInk.Pink, "u = $uT"), legend(DkInk.Yellow, "Reaches the cue"), legend(DkInk.Green, "u = 0.95", SwatchStyle.DashedLine))
    val first = g[0]
    listOf(
        DkFrame(
            header, plot(emptySet()), listOf(legend[0], legend[2]),
            listOf("h_t = tanh(x_t + u·h_{t−1})", "∂h10/∂h_t = Π u·(1 − h_k²) from k = t+1 to 10"),
            "The answer at t10 depends on the {cue at t1}.",
            "Steps 2–10 are noise. To learn the task, the error at t10 has to travel back through nine steps to reach t1.",
        ),
        DkFrame(
            header, plot(setOf(0)), legend,
            listOf("∂h10/∂h1 = Π u·(1 − h_t²) over 9 steps", "u = $uT: {${if (first < 0.01) n(first, 4) else n(first, 3)}} · u = 0.95: ${n(ref[0], 3)}"),
            when {
                first > 1.05 -> "The signal reaching the cue has grown {${n(first, 1)}×}."
                first > 0.3 -> "{${pct(first, 0)}} of the error signal gets back to the cue."
                else -> "Only {${pct(first)}} of the error signal gets back to the cue."
            },
            when {
                first > 1.05 -> "Above 1 the factors compound the other way and the gradient explodes; clipping is the usual guard."
                first > 0.3 -> "With u near 1 the factors barely shrink it, so the cue can still be learned, though tanh′ keeps nibbling at it."
                else -> "Backprop through time multiplies one factor per step. Below 1, the signal vanishes before it reaches the one input that matters."
            },
        ),
        DkFrame(
            header, plot(emptySet(), dim = (0 until 5).toSet()), listOf(legend[0], legend(DkInk.Slate, "Cut off")),
            listOf("truncated BPTT, k = 5: backprop stops at t6", "cost: 5 steps of memory instead of 10"),
            "Truncate at 5 steps and the cue gets {no} gradient.",
            "Truncated BPTT bounds memory and compute on long sequences, but it can't learn dependencies longer than its window. LSTMs attack the factor itself.",
        ),
    ).map { DkFrame(it.header, it.stage, it.legend, it.formula, it.headline, it.body, it.chips, "Next") }
}

// ── LSTMs and GRUs ──

private val lstmF = listOf(0.10, 0.92, 0.92, 0.92, 0.05)
private val lstmI = listOf(0.95, 0.12, 0.12, 0.12, 0.10)
private val lstmG = listOf(0.9, 0.1, 0.1, 0.1, -0.2)
private val gruZ = listOf(0.95, 0.08, 0.08, 0.08, 0.95)
private val gruR = listOf(0.50, 0.90, 0.90, 0.90, 0.10)
private val gruH = listOf(0.9, 0.1, 0.1, 0.1, 0.0)

private fun lstmLab(): DkLab = DkLab(DkControl.Tabs, tabs = listOf("LSTM", "GRU")) { tab, _ ->
    val gru = tab == 1
    val c = ArrayList<Double>()
    var prev = 0.0
    for (k in 0 until 5) {
        prev = if (gru) (1 - gruZ[k]) * prev + gruZ[k] * gruH[k] else lstmF[k] * prev + lstmI[k] * lstmG[k]
        c += prev
    }
    val gate1 = if (gru) gruZ else lstmF
    val gate2 = if (gru) gruR else lstmI
    val names = if (gru) listOf("update z", "reset r", "state h") else listOf("forget", "input", "cell c")
    fun stage(hot: Int) = DkTiles(
        listOf(
            DkTileRow(gate1.map { DkTile("", DkTileTone.Fill, fill = it, fillInk = DkInk.Blue, caption = n(it)) }, label = names[0]),
            DkTileRow(gate2.map { DkTile("", DkTileTone.Fill, fill = it, fillInk = DkInk.Orange, caption = n(it)) }, label = names[1]),
            DkTileRow(c.map { DkTile("", DkTileTone.Fill, fill = abs(it), fillInk = DkInk.Violet, caption = n(it)) }, label = names[2]),
        ),
        headers = (1..5).map { "t$it" }, hot = hot,
    )
    val keep = gate1[1] // 0.92 for LSTM's forget gate; 1 − z = 0.92 for the GRU
    val keepFactor = if (gru) 1 - gruZ[1] else keep
    val chain = "∂${if (gru) "h" else "c"}4/∂${if (gru) "h" else "c"}1 = Π ${if (gru) "(1 − z)" else "f"} = ${n(keepFactor)}³ = ${n(keepFactor.pow(3), 3)} vs RNN 0.6³ = ${n(0.216, 3)}"
    fun update(k: Int): String {
        val p = if (k == 0) 0.0 else c[k - 1]
        val v = if (gru) "h" else "c"
        return if (gru) "h${k + 1} = (1 − z)·h$k + z·h̃ = ${n(1 - gruZ[k])}·${n(p, 3)} + ${n(gruZ[k])}·${t(gruH[k])} = {${n(c[k], 3)}}"
        else "$v${k + 1} = f·c$k + i·g = ${n(lstmF[k])}·${n(p, 3)} + ${n(lstmI[k])}·${t(lstmG[k])} = {${n(c[k], 3)}}"
    }
    val header = if (gru) "gates and state · t1 store, t2–t4 keep, t5 overwrite" else "gates and cell state · t1 store, t2–t4 keep, t5 forget"
    val legend = if (gru) listOf(legend(DkInk.Blue, "Update z"), legend(DkInk.Orange, "Reset r"), legend(DkInk.Violet, "State h"))
    else listOf(legend(DkInk.Blue, "Forget f"), legend(DkInk.Orange, "Input i"), legend(DkInk.Violet, "Cell c"))
    val mem = if (gru) "state" else "cell"
    val heads = listOf(
        "t1: the ${if (gru) "update" else "input"} gate opens and {stores ${n(c[0])}}." to
            if (gru) "z near 1 replaces the old state with the new candidate h̃ = 0.9." else "i = 0.95 lets the candidate g = 0.9 in; f = 0.1 throws away the empty past.",
        "The ${if (gru) "update gate stays shut" else "forget gate keeps"} {${pct(keepFactor, 0)}}; little new comes in." to
            if (gru) "1 − z = 0.92 carries the state forward; z = 0.08 lets only a trace of the new input in." else "The cell is a conveyor belt: f decides how much rides on, i how much is added.",
        "The $mem holds {${n(c[2])}}, ${pct(c[2] / c[0], 0)} of what was stored at t1." to
            "A ${if (gru) "update gate near 0" else "forget gate near 1"} lets memory and gradient pass almost unchanged; at t5 it ${if (gru) "opens" else "closes"} and ${if (gru) "h" else "c"} drops to ${n(c[4], 3)}.",
        "Three steps on, it still holds {${n(c[3])}}." to
            "An RNN with u = 0.6 would have kept ${pct(0.6.pow(3), 0)} of it by now; the gate is learned, so the network chooses what to remember.",
        "At t5 the ${if (gru) "update gate opens" else "forget gate closes"}: ${if (gru) "h" else "c"} drops to {${n(c[4], 3)}}." to
            if (gru) "The GRU merges the LSTM's cell and output into one state with two gates: fewer parameters, similar results."
            else "Forgetting is as deliberate as remembering: the network clears the cell when the stored fact stops mattering.",
    )
    val frames = heads.mapIndexed { k, (h, b) ->
        DkFrame(header, stage(k), legend, listOf(update(k), chain), h, b)
    }
    stepActions(frames, "Next Timestep")
}

// ── Bidirectional RNNs ──

private val biWords = listOf("the", "horse", "raced", "past", "the", "barn", "fell")

private fun biRnnLab(): DkLab = DkLab(DkControl.Tabs, tabs = listOf("Forward only", "Bidirectional"), initialTab = 1) { tab, _ ->
    val bi = tab == 1
    val last = biWords.lastIndex
    fun stage(i: Int) = DkTiles(
        listOf(
            DkTileRow(
                biWords.mapIndexed { k, w ->
                    DkTile(
                        w,
                        when {
                            k == i -> DkTileTone.Current
                            k == last -> DkTileTone.Hot
                            else -> DkTileTone.Plain
                        },
                        under = listOf(if (k <= i) DkInk.Blue else null) + if (bi) listOf(if (k >= i) DkInk.Green else null) else emptyList(),
                    )
                },
                height = 46,
            ),
        ),
    )
    val header = "\"${biWords.joinToString(" ")}\""
    val legend = listOf(legend(DkInk.Blue, "→ forward state")) + (if (bi) listOf(legend(DkInk.Green, "← backward state")) else emptyList()) + legend(DkInk.Violet, "Disambiguates")
    val frames = biWords.indices.map { i ->
        val w = biWords[i]
        val seenF = i + 1
        val seenB = biWords.size - i
        DkFrame(
            header, stage(i), legend,
            if (bi) listOf("h_$w = [ →h ; ←h ] · 2 × 128 = 256 dims", "→h has seen $seenF word${if (seenF > 1) "s" else ""}, ←h has seen {$seenB${if (i < last) ", incl. \"fell\"" else ""}}")
            else listOf("h_$w = →h · 128 dims", "→h has seen $seenF word${if (seenF > 1) "s" else ""}, {${if (i < last) "not \"fell\"" else "including \"fell\""}}"),
            when {
                i == 2 && bi -> "A forward RNN's state at \"raced\" is {identical} in both readings."
                i == 2 -> "At \"raced\", a forward RNN {can't tell} which reading it is."
                i == last && bi -> "At \"fell\" both directions agree: it's the {main verb}."
                i == last -> "Only at \"fell\" does the forward pass {see} the main verb, too late for \"raced\"."
                bi -> "At \"$w\", the state joins {$seenF} word${if (seenF > 1) "s" else ""} from the left with $seenB from the right."
                else -> "At \"$w\", the state has seen only the {$seenF} word${if (seenF > 1) "s" else ""} so far."
            },
            when {
                i == 2 && bi -> "\"fell\" is 4 words to the right, so only the backward pass can tell main verb from reduced relative. Concatenating both gives the tagger that context."
                i == 2 -> "\"The horse raced past the barn\" reads as complete. Only \"fell\" reveals that \"raced\" meant \"that was raced\", and this RNN hasn't seen it."
                i == last -> "Garden-path sentences are why taggers and BERT-style encoders read both ways; a generator can't, because the future isn't written yet."
                bi -> "The backward RNN runs right to left over the same words; the two states are concatenated at every position."
                else -> "A forward-only RNN is what a text generator must use: it can only condition on the past."
            },
        )
    }
    stepActions(frames, "Next Word")
}

// ── Encoder-decoder ──

private val encSource = listOf("a", "c", "f", "e", "b", "b")
private const val EncUnits = 12
private const val EncU = 0.6

private fun encDecLab(): DkLab = DkLab(DkControl.Track) { _, _ ->
    val nSrc = encSource.size
    fun stage(emitted: Int, current: Int?, encodeAll: Boolean, hotSource: Int?) = DkTiles(
        listOf(
            DkTileRow(
                encSource.mapIndexed { k, s ->
                    DkTile(s, if (k == hotSource) DkTileTone.Current else if (encodeAll) DkTileTone.Source else DkTileTone.Plain)
                },
                title = "encoder",
            ),
            DkTileRow(
                encSource.mapIndexed { k, s ->
                    when {
                        k == current -> DkTile("?", DkTileTone.Current)
                        k < emitted -> DkTile(s, DkTileTone.Emitted)
                        else -> DkTile("", DkTileTone.Empty)
                    }
                },
                title = "decoder",
            ),
        ),
        pill = 0 to "context · $EncUnits numbers",
    )
    fun path(j: Int): Int = (nSrc - j) + 1 + (j - 1)
    val header = "copy task · $EncUnits-unit encoder and decoder"
    val legend = listOf(legend(DkInk.Blue, "Source"), legend(DkInk.Green, "Emitted"), legend(DkInk.Yellow, "Current", SwatchStyle.Ring), legend(DkInk.Violet, "Bottleneck"))
    val frames = ArrayList<DkFrame>()
    frames += DkFrame(
        header, stage(0, null, true, null), listOf(legend[0], legend[3]),
        listOf("encoder: 6 steps, one per symbol", "final state → context: {$EncUnits} numbers"),
        "The encoder reads \"${encSource.joinToString("")}\" into {$EncUnits numbers}.",
        "Only its last state is passed on. Everything the decoder will ever know about the source is in that vector.",
    )
    for (j in 1..nSrc) {
        val steps = path(j)
        val g = EncU.pow(steps)
        frames += DkFrame(
            header, stage(j - 1, j - 1, true, j - 1), legend,
            listOf(
                "path ${encSource[j - 1]} → output $j: ${nSrc - j} encoder + 1 hand-off + ${j - 1} decoder = {$steps steps}",
                "at u = ${t(EncU)}: ${t(EncU)}${sup(steps)} = ${n(g, 3)} of the gradient",
            ),
            if (j == 4) "Every source symbol must squeeze through {$EncUnits numbers}."
            else "Output $j copies \"${encSource[j - 1]}\" over a path of {$steps steps}.",
            if (j == 4) "Output 4 can only reach \"e\" through $steps recurrent steps and the context vector. Longer sources overflow it, which is the problem attention removes."
            else "The decoder emits one symbol per step, feeding each back in, with only the context and its own state to go on.",
        )
    }
    val long = 20
    frames += DkFrame(
        header, stage(nSrc, null, true, null), listOf(legend[0], legend[1], legend[3]),
        listOf("source of $long: first symbol's path = $long steps", "${t(EncU)}${sup(long)} = {${sci(EncU.pow(long))}}"),
        "A $long-symbol source leaves its first symbol {${sci(EncU.pow(long))}} of the gradient.",
        "The fixed-size context can't grow with the input. Attention lets each output look back at every encoder state directly.",
    )
    stepActions(frames, "Emit Next")
}

// ── Seq2seq: greedy vs beam search ──

private const val Eos = "⟨eos⟩"
private val vocab = listOf("a", "b", "c", "d", "e", Eos)

private val nextProbs: Map<String, Map<String, Double>> = mapOf(
    "" to mapOf("e" to 0.55, "b" to 0.25, "c" to 0.10, "a" to 0.06, "d" to 0.04, Eos to 0.0),
    "e" to mapOf("b" to 0.36, "c" to 0.34, "a" to 0.10, Eos to 0.10, "d" to 0.06, "e" to 0.04),
    "b" to mapOf("e" to 0.60, "c" to 0.24, "a" to 0.08, "d" to 0.04, "b" to 0.02, Eos to 0.02),
    "c" to mapOf("a" to 0.40, "b" to 0.25, "e" to 0.15, "d" to 0.10, "c" to 0.05, Eos to 0.05),
    "a" to mapOf("e" to 0.30, "b" to 0.30, "c" to 0.20, "d" to 0.10, "a" to 0.05, Eos to 0.05),
    "d" to mapOf("e" to 0.30, "b" to 0.25, "c" to 0.20, "a" to 0.15, "d" to 0.05, Eos to 0.05),
    "eb" to mapOf(Eos to 0.25, "a" to 0.24, "c" to 0.20, "d" to 0.16, "e" to 0.10, "b" to 0.05),
    "ec" to mapOf("b" to 0.90, Eos to 0.04, "a" to 0.03, "d" to 0.01, "e" to 0.01, "c" to 0.01),
)

/** P(next | prefix): the prefix's own table if it has one, else its last symbol's. */
private fun probs(prefix: List<String>): Map<String, Double> =
    nextProbs[prefix.joinToString("")] ?: nextProbs.getValue(prefix.lastOrNull() ?: "")

private class Hyp(val seq: List<String>, val score: Double) {
    val done get() = seq.lastOrNull() == Eos
    val text get() = seq.joinToString(" ")
}

private class BeamStep(val expansions: List<Hyp>, val kept: List<Hyp>, val total: Int)

private fun beamSearch(k: Int, steps: Int = 3): List<BeamStep> {
    var beam = listOf(Hyp(emptyList(), 1.0))
    val out = ArrayList<BeamStep>()
    repeat(steps) {
        val live = beam.filter { !it.done }
        val exp = live.flatMap { h -> vocab.map { v -> Hyp(h.seq + v, h.score * (probs(h.seq)[v] ?: 0.0)) } }.filter { it.score > 0 }
        val pool = (exp + beam.filter { it.done }).sortedByDescending { it.score }
        val kept = pool.take(k)
        out += BeamStep(exp.sortedByDescending { it.score }, kept, live.size * vocab.size)
        beam = kept
    }
    return out
}

private fun seq2seqLab(): DkLab = DkLab(DkControl.Tabs, tabs = listOf("Greedy", "k = 2", "k = 5"), initialTab = 1) { tab, _ ->
    val k = listOf(1, 2, 5)[tab]
    val run = beamSearch(k)
    val greedy = beamSearch(1).last().kept.first()
    val best = run.last().kept.first()
    fun factors(h: Hyp): String {
        val parts = h.seq.indices.map { i -> probs(h.seq.take(i))[h.seq[i]] ?: 0.0 }
        return parts.joinToString("·") { n(it) }
    }
    fun pathText(h: Hyp) = h.seq.joinToString(" → ")
    fun stage(s: Int): DkRows {
        val st = run[s]
        val shown = st.expansions.take(6)
        val keptTexts = st.kept.map { it.text }.toSet()
        val top = shown.first().score
        return DkRows(
            shown.map { h ->
                val kept = h.text in keptTexts
                DkRow(h.text, "", listOf(DkBar(h.score / top, DkInk.Blue, n(h.score, 3) + if (kept) " kept" else "")), inline = true, dim = !kept)
            },
            caption = "step ${s + 1} · ${shown.size} of ${st.total} expansions shown, top $k kept",
        )
    }
    val header = "source \"e c b\" · 6 symbols, 55,986 sequences of length ≤ 6"
    val legend = listOf(legend(DkInk.Blue, "In the beam"), legend(DkInk.Slate, "Pruned"))
    val greedyLine = "greedy: ${pathText(greedy)} = ${factors(greedy)} = ${n(greedy.score, 3)}"
    val beam2 = beamSearch(2).last().kept.first()
    val beamLine = if (k == 1) "beam, k = 2: ${pathText(beam2)} = ${factors(beam2)} = {${n(beam2.score, 3)}}"
    else "beam: ${pathText(best)} = ${factors(best)} = {${n(best.score, 3)}}"
    val first = run[0].expansions.first()
    val s2 = run[1].expansions
    val frames = listOf(
        DkFrame(
            header, stage(0), legend,
            listOf("score = Π P(symbol | prefix)", "kept: top {$k} of ${run[0].total}"),
            "Step 1 scores all 6 first symbols; {\"${first.text}\"} leads at ${n(first.score, 2)}.",
            if (k == 1) "Greedy keeps only the best one and builds on it." else "A beam of $k keeps the $k best and expands each of them at the next step.",
        ),
        DkFrame(
            header, stage(1), legend,
            listOf(greedyLine, if (k == 1) "only one prefix survives each step" else beamLine),
            if (k == 1) "Greedy keeps only {\"${s2[0].text.replace(" ", "")}\"}: ${n(probs(listOf("e"))["b"]!!)} beat ${n(probs(listOf("e"))["c"]!!)}."
            else "\"${s2[0].text.replace(" ", "")}\" ${n(s2[0].score, 3)} edges out \"${s2[1].text.replace(" ", "")}\" ${n(s2[1].score, 3)}; the beam keeps {${if (k == 2) "both" else "the top $k"}}.",
            if (k == 1) "It commits to the locally best symbol and can never revisit that choice."
            else "Greedy took \"b\" at step 2 because 0.36 beat 0.34. Keeping the runner-up gives the far better third step a chance.",
        ),
        DkFrame(
            header, stage(2), legend, listOf(greedyLine, beamLine),
            when (tab) {
                0 -> "Greedy commits to \"${greedy.text.replace(" ", "").replace(Eos, "")}\" and ends at {${n(greedy.score, 3)}}."
                1 -> "Beam search finds \"${best.text.replace(" ", "")}\" at {${n(best.score, 3)}}; greedy is stuck at ${n(greedy.score, 3)}."
                else -> "k = 5 finds the same \"${best.text.replace(" ", "")}\" at {${n(best.score, 3)}}."
            },
            when (tab) {
                0 -> "\"e c b\" scores ${n(beam2.score, 3)}, more than three times as likely, but greedy dropped \"e c\" at step 2."
                1 -> "Greedy took \"b\" at step 2 because 0.36 beat 0.34. Keeping the runner-up let the far better third step win."
                else -> "A wider beam costs 5 × 6 = 30 expansions a step instead of 12 and buys nothing more here. Translation systems typically use k = 4 to 10."
            },
        ),
    )
    stepActions(frames, "Expand Beam")
}
