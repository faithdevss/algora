package com.algora.app.feature.topics

import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

// ── Transformer storyboard frames ────────────────────────────────────────────
// Attention, multi-head attention, self- and cross-attention, transformers, BERT, GPT, DistilBERT, T5,
// RoBERTa and Hugging Face tokenizers, drawn by DeepStoryLabs.kt. One sentence, "the cat sat on the mat",
// with one set of query, key and value vectors (d = 4) runs through every attention lab, so the
// weights GPT masks are the ones Attention computed. The tokenizer lab trains real BPE merges on a
// fifteen-sentence corpus. The iOS port is TransformerStoryFrames.swift.

internal val transformerStoryTopicIds = setOf(
    "attention", "multi_head_attention", "self_cross_attention", "transformers", "bert", "gpt", "distilbert", "t5", "roberta", "hf_tokenizers",
)

internal fun transformerLab(topicId: String): DkLab? = when (topicId) {
    "attention" -> attentionLab()
    "multi_head_attention" -> multiHeadLab()
    "self_cross_attention" -> selfCrossLab()
    "transformers" -> blockLab()
    "bert" -> bertLab()
    "gpt" -> gptLab()
    "distilbert" -> distilLab()
    "t5" -> t5Lab()
    "roberta" -> robertaLab()
    "hf_tokenizers" -> bpeLab()
    else -> null
}

private fun n(v: Double, d: Int = 2) = dkNum(v, d)

private fun pct(share: Double, d: Int = 0) = n(share * 100, d) + "%"

private fun legend(ink: DkInk, label: String, style: SwatchStyle = SwatchStyle.Fill) = DkLegend(ink, style, label)

private fun stepActions(frames: List<DkFrame>, actions: (Int) -> String) =
    frames.mapIndexed { i, f -> DkFrame(f.header, f.stage, f.legend, f.formula, f.headline, f.body, f.chips, if (i == frames.lastIndex) "Start Over" else actions(i)) }

private fun vec(v: List<Double>) = v.joinToString(", ", "[", "]") { n(it) }

private fun softmax(v: List<Double>): List<Double> {
    val m = v.filter { it.isFinite() }.max()
    val e = v.map { if (it.isFinite()) exp(it - m) else 0.0 }
    val s = e.sum()
    return e.map { it / s }
}

private fun dot(a: List<Double>, b: List<Double>) = a.indices.sumOf { a[it] * b[it] }

// ── The shared sentence ──

private val words = listOf("the", "cat", "sat", "on", "the", "mat")
private const val Dim = 4

private val xs = listOf(
    listOf(0.90, -0.30, 0.20, 0.10), listOf(1.40, 0.80, -0.50, 0.30), listOf(2.07, -1.19, 0.38, 0.29),
    listOf(0.40, 1.10, 0.90, -0.60), listOf(0.80, -0.20, 0.40, 0.50), listOf(1.20, 0.30, -0.80, -0.40),
)
private val qs = listOf(
    listOf(1.0, 1.5, 0.5, 0.5), listOf(0.8, 2.0, 0.2, 0.6), listOf(2.0, 1.6, 2.0, 0.4),
    listOf(0.5, 0.2, 0.3, -1.0), listOf(1.0, 1.0, 0.8, 0.2), listOf(1.5, -0.5, 1.0, -0.8),
)
private val ks = listOf(
    listOf(0.2, 0.1, 0.0, 0.0), listOf(1.5, 0.2, 1.0, 0.3), listOf(0.3, 0.5, 0.2, 0.1),
    listOf(1.0, 1.8, 0.5, 0.6), listOf(-0.6, 0.0, -0.4, 0.2), listOf(0.9, -0.8, 0.3, -0.6),
)
private val vs = listOf(
    listOf(0.1, 0.2, 0.0, 0.1), listOf(-1.2, 1.9, 1.1, -0.2), listOf(0.3, -0.2, 0.5, 0.1),
    listOf(-0.9, 1.6, 1.8, -0.5), listOf(0.2, 0.1, -0.1, 0.3), listOf(0.8, -0.5, 0.6, 0.9),
)

/** q·k/√d for every query and key, on the dimensions [dims] (all four by default). */
private fun scores(q: List<List<Double>>, dims: List<Int> = (0 until Dim).toList()): List<List<Double>> =
    q.map { qi -> ks.map { kj -> dims.sumOf { qi[it] * kj[it] } / sqrt(dims.size.toDouble()) } }

private val selfScores by lazy { scores(qs) }
private val selfWeights by lazy { selfScores.map { softmax(it) } }

private fun heatGrid(w: List<List<Double>>, rows: List<String>, hot: Int?, masked: Boolean = false, maxCell: Float = 30f) = DkGrid(
    "", w.size, words.size,
    w.flatMapIndexed { i, row ->
        row.mapIndexed { j, v ->
            if (masked && j > i) DkCell("−∞", DkCellTone.Zero) else DkCell(n(v), DkCellTone.Heat, v.toFloat())
        }
    },
    boxes = listOfNotNull(hot?.let { DkBox(it, 0, it, words.size - 1) }),
    maxCell = maxCell, rowLabels = rows, colLabels = words, hotRow = hot,
)

// ── Attention ──

private fun attentionLab(): DkLab = DkLab(DkControl.Track) { _, _ ->
    val qi = 2
    val sc = selfScores[qi]
    val w = selfWeights[qi]
    val top = w.indices.maxBy { w[it] }
    val out = (0 until Dim).map { d -> w.indices.sumOf { w[it] * vs[it][d] } }
    fun rows(showScores: Boolean, showWeights: Boolean) = DkRows(
        words.mapIndexed { j, t ->
            DkRow(
                t, if (showScores) n(sc[j]) else "",
                listOf(DkBar(if (showWeights) w[j] else 0.0, DkInk.Violet, if (showWeights) n(w[j]) else "–")),
                hot = j == qi, inline = true,
            )
        },
    )
    val header = "query \"sat\" against every key · d = $Dim"
    val legend = listOf(legend(DkInk.Yellow, "Query"), legend(DkInk.Violet, "Weight"))
    val frames = listOf(
        DkFrame(
            header, rows(false, false), legend.take(1),
            listOf("q_sat = ${vec(qs[qi])}", "one key per token, one value per token"),
            "Each word asks a {question}: its query vector.",
            "Every token also offers a key, what it matches, and a value, what it hands over. \"sat\" will compare its query with all six keys.",
        ),
        DkFrame(
            header, rows(true, false), legend.take(1),
            listOf("score = q·k / √$Dim", "q_sat·k_on / 2 = {${n(sc[3])}}, q_sat·k_mat / 2 = ${n(sc[5])}"),
            "\"on\" scores highest at {${n(sc[3])}}.",
            "A dot product is large when the vectors point the same way. Dividing by √d keeps scores in a range softmax can handle.",
        ),
        DkFrame(
            header, rows(true, true), legend,
            listOf("w = softmax(q·k / √$Dim) · Σw = ${n(w.sum())}", "top: \"${words[top]}\" {${n(w[top])}} of the mix"),
            "\"sat\" draws {${pct(w[top])}} of its update from \"${words[top]}\".",
            "Softmax turns unbounded scores into weights that sum to 1. The output is that weighted mix of the six value vectors.",
        ),
        DkFrame(
            header, rows(true, true), legend,
            listOf("out = Σ w_j · v_j", "= {${vec(out)}}"),
            "The output is {${vec(out)}}, mostly \"on\" and \"cat\".",
            "Nothing was looked up by position: \"sat\" found its context by content, wherever those words sat in the sentence.",
        ),
        DkFrame(
            header, rows(true, true), legend,
            listOf("all queries at once: softmax(QKᵀ / √d) · V", "6 × 6 scores, ${6 * 6 * Dim} multiplies for QKᵀ"),
            "Every word does this {in parallel}.",
            "Stack the queries into Q and it's two matrix products. That parallelism, unlike an RNN's step-by-step loop, is why transformers train fast.",
        ),
    )
    stepActions(frames) { listOf("Score Keys", "Softmax", "Mix Values", "All Queries")[it] }
}

// ── Multi-head attention ──

private fun multiHeadLab(): DkLab = DkLab(DkControl.Tabs, tabs = listOf("h = 1", "h = 2", "h = 4"), initialTab = 1) { tab, _ ->
    val h = listOf(1, 2, 4)[tab]
    val per = Dim / h
    val qi = 2
    val heads = (0 until h).map { k ->
        val dims = (k * per until (k + 1) * per).toList()
        softmax(scores(listOf(qs[qi]), dims)[0])
    }
    val tops = heads.map { w -> w.indices.maxBy { w[it] } }
    fun stage() = DkTiles(
        heads.mapIndexed { k, w ->
            DkTileRow(
                words.mapIndexed { j, t -> DkTile(t, DkTileTone.Heat, sub = n(w[j]), fill = w[j], ring = j == tops[k]) },
                title = "head ${k + 1}", height = 46,
            )
        },
    )
    val header = "query \"sat\" · d = $Dim split into $h head${if (h > 1) "s" else ""} of $per"
    val legend = listOf(legend(DkInk.Violet, "Weight"), legend(DkInk.Yellow, "Head's top key", SwatchStyle.Ring))
    val distinct = tops.map { words[it] }.distinct()
    val frames = listOf(
        DkFrame(
            header, stage(), legend,
            listOf("each head: its own ${per}-dim slice of q and k", "head weights: softmax(q_h·k_h / √$per)"),
            when {
                h == 1 -> "One head puts {${pct(heads[0][tops[0]])}} on \"${words[tops[0]]}\"."
                distinct.size > 1 -> "The $h heads attend to {different words}: ${distinct.joinToString(" vs ") { "\"$it\"" }}."
                else -> "All $h heads agree on {\"${distinct[0]}\"} here."
            },
            if (h == 1) "A single head must blend every relationship into one weighting. Split d and each slice can specialise."
            else "Splitting d costs no parameters; it lets each slice learn its own pattern, then concatenates them.",
        ),
        DkFrame(
            header, stage(), legend,
            listOf("params = 4·d² at every h · d = 512 → 1.05M", "d per head: h=1 → 512, h=8 → {64}, h=16 → 32"),
            "Splitting d costs {no} extra parameters.",
            "Q, K, V and the output projection are d×d whatever h is; heads only change how those columns are grouped.",
        ),
        DkFrame(
            header, stage(), legend,
            listOf("concat: $h × $per = $Dim dims → W_O ($Dim×$Dim)", "output mixes all heads' findings"),
            "The heads' outputs are {concatenated} and projected.",
            "In trained models some heads track syntax, some coreference, some the previous token; the output layer learns how to combine them.",
        ),
    )
    stepActions(frames) { "Next" }
}

// ── Self- vs cross-attention ──

private val targetWords = listOf("die", "Katze", "saß")
private val targetQs = listOf(listOf(1.6, 0.0, 1.2, 0.4), listOf(1.0, 1.4, 0.6, 0.5), listOf(0.3, 0.4, 0.2, 0.0))

private fun selfCrossLab(): DkLab = DkLab(DkControl.Tabs, tabs = listOf("Self", "Cross", "Both"), initialTab = 2) { tab, _ ->
    val cross = scores(targetQs).map { softmax(it) }
    val selfGrid = { hot: Int? -> heatGrid(selfWeights, words, hot, maxCell = 34f) }
    val crossGrid = { hot: Int? -> heatGrid(cross, targetWords, hot, maxCell = 34f) }
    fun stage(hotSelf: Int?, hotCross: Int?): DkStage = when (tab) {
        0 -> DkGrids(listOf(listOf(selfGrid(hotSelf))), listOf(1f))
        1 -> DkGrids(listOf(listOf(crossGrid(hotCross))), listOf(1f))
        else -> DkGrids(listOf(listOf(selfGrid(hotSelf), crossGrid(hotCross))), listOf(1f))
    }
    val header = when (tab) {
        0 -> "self · Q, K, V from \"the cat sat on the mat\""
        1 -> "cross · Q from German target, K, V from English source"
        else -> "self above · cross below"
    }
    val legend = listOf(legend(DkInk.Violet, "Attention weight"), legend(DkInk.Yellow, "Highlighted query", SwatchStyle.Ring))
    val sat = selfWeights[2]
    val katze = cross[1]
    val kTop = katze.indices.maxBy { katze[it] }
    val sTop = sat.indices.maxBy { sat[it] }
    val frames = listOf(
        DkFrame(
            header, stage(null, null), legend.take(1),
            listOf("self: Q, K, V all from one sequence → 6 × 6", "cross: Q from the decoder, K, V from the encoder → 3 × 6"),
            when (tab) {
                0 -> "Self-attention: every word {reads its own sentence}."
                1 -> "Cross-attention: each German word {reads the English}."
                else -> "Two maps, {one formula}."
            },
            "Each row is one query's weights over the six source words, and sums to 1.",
        ),
        DkFrame(
            header, stage(2, 1), legend,
            when (tab) {
                0 -> listOf("\"sat\" row: top \"${words[sTop]}\" {${n(sat[sTop])}}", "rows: 6 queries × 6 keys")
                1 -> listOf("\"Katze\" row: top \"${words[kTop]}\" {${n(katze[kTop])}}", "rows: 3 target words × 6 source words")
                else -> listOf("self \"sat\" → \"${words[sTop]}\" ${n(sat[sTop])}", "cross \"Katze\" → \"${words[kTop]}\" {${n(katze[kTop])}}")
            },
            when (tab) {
                0 -> "\"sat\" puts {${n(sat[sTop])}} on \"${words[sTop]}\"."
                1 -> "\"Katze\" puts {${n(katze[kTop])}} on \"${words[kTop]}\"."
                else -> "Same softmax(QKᵀ/√d)·V; only {where Q comes from} changes."
            },
            when (tab) {
                2 -> "Self-attention is 6×6. Cross-attention is 3×6: each target word reads the source, e.g. \"Katze\" puts ${n(katze[kTop])} on \"${words[kTop]}\"."
                else -> "The weights here come from hand-set vectors, not a trained model, so the pairs they favour are only illustrative."
            },
        ),
        DkFrame(
            header, stage(null, null), legend.take(1),
            listOf("cost: self n², cross n_target × n_source", "6² = 36 · 3 × 6 = {18}"),
            "Cross-attention is how a decoder {consults} the source.",
            "An encoder-decoder transformer uses both in every decoder block: self-attention over what it has written, then cross-attention into the encoder.",
        ),
    )
    stepActions(frames) { "Next" }
}

// ── Transformers: one block ──

private fun blockLab(): DkLab = DkLab(DkControl.Track) { _, _ ->
    val qi = 2
    val w = selfWeights[qi]
    val x = xs[qi]
    val attn = (0 until Dim).map { d -> w.indices.sumOf { w[it] * vs[it][d] } }
    val res = x.indices.map { x[it] + attn[it] }
    val mean = res.average()
    val sd = sqrt(res.sumOf { (it - mean) * (it - mean) } / Dim + 1e-5)
    val norm = res.map { (it - mean) / sd }
    var s = 77L
    fun u(): Double { s = (s * 1103515245L + 12345L) and 0x7fffffffL; return s / 2147483648.0 - 0.5 }
    val w1 = List(16) { List(Dim) { u() } }
    val w2 = List(Dim) { List(16) { u() * 0.6 } }
    val hidden = w1.map { row -> max(0.0, dot(row, norm)) }
    val ffn = w2.map { row -> dot(row, hidden) }
    val out = norm.indices.map { norm[it] + ffn[it] }
    val titles = listOf(
        "x · embedding + position" to "6 × 4", "multi-head attention" to "mixes tokens", "x + Attn(x) · residual" to "add",
        "layer norm" to "per token", "feed-forward 4 → 16 → 4" to "per token", "x + FFN(x) · residual" to "add",
    )
    fun stage(current: Int) = DkPipeline(titles.mapIndexed { i, (t, m) -> DkPipeItem(t, m, if (i < current) DkStepTone.Done else if (i == current) DkStepTone.Current else DkStepTone.Next) })
    val header = "one block, applied to \"sat\" · d = $Dim"
    val legend = listOf(legend(DkInk.Green, "Done"), legend(DkInk.Yellow, "Current", SwatchStyle.Ring), legend(DkInk.Slate, "Next"))
    val frames = listOf(
        DkFrame(
            header, stage(0), legend, listOf("x_sat = embedding(\"sat\") + position(3)", "= {${vec(x)}}"),
            "\"sat\" enters as {4 numbers}: meaning plus position.",
            "Attention ignores order, so a position vector is added in; the same word at another position gets a different x.",
        ),
        DkFrame(
            header, stage(1), legend, listOf("attn = Σ w_j · v_j over all 6 tokens", "= {${vec(attn)}}"),
            "Attention gathers {context} from the other words.",
            "This is the only step where tokens exchange information. Everything after it works on each token alone.",
        ),
        DkFrame(
            header, stage(2), legend, listOf("x_sat = ${vec(x)}", "+ attn = ${vec(attn)}", "= {${vec(res)}}"),
            "Attention's output is {added} to the token, not swapped in.",
            "The residual keeps \"sat\" itself in the stream while mixing in context. Stack this block N times and that is the whole transformer.",
        ),
        DkFrame(
            header, stage(3), legend, listOf("mean ${n(mean)}, std ${n(sd)}", "→ {${vec(norm)}}"),
            "Layer norm rescales the token to {mean 0, std 1}.",
            "Without it, values drift as blocks stack. A learned scale and shift (left out here) let the model undo it where useful.",
        ),
        DkFrame(
            header, stage(5), legend.take(1) + legend[2], listOf("FFN: ReLU(W1·x) then W2 · 4 → 16 → 4", "x + FFN(x) = {${vec(out)}}"),
            "A per-token MLP and a second residual {finish} the block.",
            "Attention mixes tokens; the feed-forward layer, two thirds of a block's weights, transforms each one. GPT-3 stacks 96 of these blocks.",
        ),
    )
    stepActions(frames) { "Next Stage" }
}

// ── BERT ──

private fun bertLab(): DkLab = DkLab(DkControl.Track) { _, _ ->
    val masked = 3
    val qMask = listOf(1.2, -0.6, 0.8, -0.9)
    val keys = ks.mapIndexed { j, k -> if (j == masked) List(Dim) { 0.0 } else k }
    val w = softmax(keys.map { k -> dot(qMask, k) / sqrt(Dim.toDouble()) })
    val right = w.indices.filter { it > masked }.sumOf { w[it] }
    fun stage(mask: Boolean, weights: Boolean) = DkTiles(
        listOf(
            DkTileRow(
                words.mapIndexed { j, t ->
                    val under = listOf(if (weights && j != masked) (if (j < masked) DkInk.Blue else DkInk.Green) else null)
                    when {
                        j == masked && mask -> DkTile("[MASK]", DkTileTone.Current, sub = if (weights) "?" else null, under = under)
                        weights -> DkTile(t, DkTileTone.Heat, sub = n(w[j]), fill = w[j], under = under)
                        else -> DkTile(t, DkTileTone.Plain, under = under)
                    }
                },
                height = 50,
            ),
        ),
    )
    val sel = (0.15 * 512).roundToInt()
    val m80 = (sel * 0.8).roundToInt()
    val m10 = (sel * 0.1).roundToInt()
    val preds = listOf("on" to 0.62, "in" to 0.18, "at" to 0.09, "under" to 0.06, "by" to 0.03)
    val header = "\"on\" replaced by [MASK] · what it attends to"
    val legend = listOf(legend(DkInk.Blue, "Left context"), legend(DkInk.Green, "Right context"), legend(DkInk.Yellow, "Masked", SwatchStyle.Ring))
    val frames = listOf(
        DkFrame(
            "the sentence", stage(false, false), emptyList(), listOf("6 tokens, no labels needed", "the text is its own supervision"),
            "BERT learns from {plain text}, no labels.",
            "Hide a word and ask the model to fill it in: every sentence on the web becomes a training example.",
        ),
        DkFrame(
            header, stage(true, false), legend.drop(2), listOf("hide \"on\" → [MASK]", "target: predict \"on\" at position 4"),
            "One word is {hidden} behind [MASK].",
            "The model sees the rest of the sentence, both sides of the gap.",
        ),
        DkFrame(
            header, stage(true, true), legend,
            listOf("select 15% of 512 = $sel positions", "of those: 80% [MASK] · 10% random · 10% kept → {$m80 / $m10 / $m10}"),
            "[MASK] reads both sides: {${pct(right)}} of its weight is to the right.",
            "No causal mask, so it can use \"the mat\" to guess \"on\". The price: BERT can fill blanks but not continue text.",
        ),
        DkFrame(
            header, DkRows(preds.mapIndexed { i, (word, p) -> DkRow(word, "", listOf(DkBar(p / preds[0].second, DkInk.Violet, n(p))), inline = true, dim = i > 0) }),
            listOf(legend(DkInk.Violet, "P(word | context)")), listOf("softmax over a 30,522-word vocabulary", "loss = −log P(\"on\") = {${n(-ln(0.62))}}"),
            "The head predicts {\"on\"} with 0.62 (illustrative).",
            "Only the masked positions contribute to the loss, about 15% of tokens, which is why BERT needs a lot of text.",
        ),
        DkFrame(
            header, stage(true, true), legend, listOf("80% → [MASK], 10% → random word, 10% → unchanged", "[MASK] never appears when fine-tuning"),
            "Not every chosen word becomes {[MASK]}.",
            "Swapping some for random or unchanged words stops the model relying on seeing [MASK], which it never will after pre-training.",
        ),
        DkFrame(
            header, stage(true, true), legend, listOf("BERT-base: 12 layers, 768 dims, 110M params", "fine-tune: add one small head per task"),
            "One pre-trained encoder, {many tasks}.",
            "Classification, NER and question answering each add a tiny output layer on top and fine-tune the whole stack for an epoch or two.",
        ),
        DkFrame(
            header, stage(true, true), legend, listOf("bidirectional: sees the future → can't generate", "GPT: causal mask → can generate"),
            "BERT {understands}; it doesn't write.",
            "Reading both directions is perfect for filling and classifying, and useless for producing text one token at a time. That's GPT's job.",
        ),
    )
    stepActions(frames) { listOf("Mask a Word", "Attend", "Predict", "Masking Recipe", "Fine-tune", "Compare GPT")[it] }
}

// ── GPT: the causal mask ──

private fun gptLab(): DkLab = DkLab(DkControl.Track) { _, _ ->
    val causal = selfScores.mapIndexed { i, row -> softmax(row.mapIndexed { j, v -> if (j > i) Double.NEGATIVE_INFINITY else v }) }
    val maskedCount = (0 until 6).sumOf { i -> 5 - i }
    val header = "attention weights with the causal mask"
    val legend = listOf(legend(DkInk.Violet, "Weight"), legend(DkInk.Slate, "Masked to −∞"), legend(DkInk.Yellow, "Query row", SwatchStyle.Ring))
    val frames = (0 until 6).map { i ->
        val row = causal[i]
        val top = (0..i).maxBy { row[it] }
        val visible = words.take(i + 1)
        DkFrame(
            header, DkGrids(listOf(listOf(heatGrid(causal, words, i, masked = true, maxCell = 36f))), listOf(1f)), legend,
            listOf("score(i, j) = −∞ for j > i → e^−∞ = 0", "\"${words[i]}\" row: ${(0..i).joinToString(" + ") { n(row[it]) }} = {${n(row.sum())}}"),
            when (i) {
                0 -> "The first token can only attend to {itself}."
                else -> "\"${words[i]}\" can only use {${visible.joinToString(", ")}}; ${pct(row[top])} goes to \"${words[top]}\"."
            },
            when (i) {
                0 -> "Every later position is masked, so its whole weight, 1.00, stays home."
                5 -> "The last row sees everything. Training predicts all six next tokens in one pass: $maskedCount of 36 entries are masked."
                else -> "Same block as BERT plus the triangle mask, so each position predicts the next token without seeing it. $maskedCount of 36 entries are masked."
            },
        )
    }
    stepActions(frames) { "Next Row" }
}

// ── DistilBERT: soft targets ──

private val distilLabels = listOf("a", "b", "c", "d", "e", "f", "EOS")
private val teacher = listOf(0.12, 0.23, 0.22, 0.09, 0.16, 0.18, 0.001).let { p -> p.map { it / p.sum() } }

private fun distilLab(): DkLab = DkLab(DkControl.Tabs, tabs = listOf("T = 1", "T = 2", "T = 4"), initialTab = 1) { tab, _ ->
    val temp = listOf(1.0, 2.0, 4.0)[tab]
    val soft = teacher.map { it.pow(1 / temp) }.let { p -> p.map { it / p.sum() } }
    fun bits(p: List<Double>) = -p.filter { it > 0 }.sumOf { it * ln(it) / ln(2.0) }
    val hard = teacher.indices.maxBy { teacher[it] }
    val tT = n(temp, 0)
    val stage = DkHist(
        listOf(DkHistRow("T = 1", teacher, DkInk.Blue, hard), DkHistRow("T = $tT · what the student trains on", soft, DkInk.Green, hard)),
        distilLabels,
    )
    val header = "teacher distribution at its least certain step"
    val legend = listOf(legend(DkInk.Blue, "Teacher, T = 1"), legend(DkInk.Green, "Softened, T = $tT"), legend(DkInk.Yellow, "Hard label"))
    val frames = listOf(
        DkFrame(
            header, stage, legend,
            listOf("p_i = softmax(z_i / T)", "top: \"${distilLabels[hard]}\" {${n(teacher[hard])}} · runner-up \"c\" ${n(teacher[2])}"),
            "The teacher's top guess gets only {${pct(teacher[hard])}}.",
            "The rest of the distribution says which wrong answers are nearly right. That is knowledge a one-hot label never carries.",
        ),
        DkFrame(
            header, stage, legend,
            listOf("hard label keeps ${pct(teacher[hard])} · entropy ${n(bits(teacher))} → {${n(bits(soft))} bits} at T = $tT", "student: 6 layers, 66M vs 12, 110M · ~97% of the score"),
            "A one-hot label would throw away {${pct(1 - teacher[hard])}} of what the teacher knows.",
            if (temp == 1.0) "At T = 1 the student copies the teacher's own distribution; raise T to spread it further."
            else "\"b\" and \"c\" are nearly tied. Softening with T = $tT makes that visible, and the student learns it, at 40% fewer parameters.",
        ),
        DkFrame(
            header, stage, legend,
            listOf("loss = α·T²·KL(teacher_T ‖ student_T) + (1 − α)·CE", "DistilBERT: 40% smaller, 60% faster"),
            "DistilBERT keeps {~97%} of BERT's score.",
            "Half the layers, initialised from the teacher's, trained on its softened outputs. Higher T gives smoother targets but weaker signal on the top class.",
        ),
    )
    stepActions(frames) { "Next" }
}

// ── T5: text to text ──

private val t5Rows = listOf(
    Triple("translate English to German:", "that is good", "Das ist gut."),
    Triple("cola sentence:", "the cat sat on mat", "acceptable"),
    Triple("stsb sentence1: … sentence2: …", "(pair)", "3.8"),
    Triple("summarize:", "(article)", "(summary)"),
)

private fun t5Lab(): DkLab = DkLab(DkControl.Tabs, tabs = listOf("Translate", "CoLA", "STS-B", "Summ.")) { tab, _ ->
    fun stage() = DkTiles(
        t5Rows.mapIndexed { i, (p, x, y) ->
            DkTileRow(listOf(DkTile(p, DkTileTone.Plain, ring = i == tab), DkTile(x, DkTileTone.Plain), DkTile(y, DkTileTone.Hot)), height = 54)
        },
        footers = listOf("task prefix", "input", "target"),
    )
    val (p, x, y) = t5Rows[tab]
    fun wc(s: String) = s.split(" ").count { it.isNotBlank() && it != "…" }
    val header = "every task is input text → output text"
    val legend = listOf(legend(DkInk.Yellow, "Current prefix", SwatchStyle.Ring), legend(DkInk.Violet, "Decoder output"))
    val frames = listOf(
        DkFrame(
            header, stage(), legend,
            listOf("encoder in: ${wc(p) + wc(x)} words · decoder out: ${wc(y)} word${if (wc(y) > 1) "s" else ""}", "loss = cross-entropy on target tokens, {same for all 4}"),
            when (tab) {
                0 -> "Translation is just {text in, text out}."
                1 -> "A grammar judgement comes out as the word {\"acceptable\"}."
                2 -> "Even a similarity score is emitted as the text {\"3.8\"}."
                else -> "A summary is the {same shape}: text in, text out."
            },
            "The prefix tells one encoder-decoder which task it is doing, so there is one model, one loss and one decoding path.",
        ),
        DkFrame(
            header, stage(), legend,
            listOf("pre-training: span corruption on C4 (750 GB)", "\"the <X> sat on <Y>\" → \"<X> cat <Y> the mat\""),
            "T5 pre-trains by filling {masked spans}.",
            "Like BERT's masking, but whole spans, and the answer is generated as text, so pre-training and fine-tuning use the same decoder.",
        ),
        DkFrame(
            header, stage(), legend,
            listOf("T5-base 220M · T5-11B: same recipe, more of it", "new task = new prefix, no new head"),
            "Adding a task needs {no new layer}.",
            "Classification heads, span pointers and regressors all become text. The model just learns another kind of answer.",
        ),
    )
    stepActions(frames) { "Next" }
}

// ── RoBERTa: dynamic masking ──

private fun robertaLab(): DkLab = DkLab(DkControl.Track) { _, _ ->
    val len = 12
    val epochs = 4
    val static = setOf(3, 5)
    var s = 2019L
    fun u(): Double { s = (s * 1103515245L + 12345L) and 0x7fffffffL; return s / 2147483648.0 }
    val dynamic = List(epochs) {
        val picks = LinkedHashSet<Int>()
        while (picks.size < 2) picks += (u() * len).toInt()
        picks
    }
    fun grid(masks: List<Set<Int>>, shown: Int) = DkGrids(
        listOf(listOf(DkGrid("", epochs, len, (0 until epochs).flatMap { e -> (0 until len).map { c -> if (e < shown && c in masks[e]) DkCell("", DkCellTone.Hot) else DkCell("", DkCellTone.Empty) } }, maxCell = 24f, rowLabels = (1..epochs).map { "e$it" }))),
        listOf(1f),
    )
    fun covered(k: Int) = dynamic.take(k).flatten().toSet().size
    val header = "same sentence, $epochs epochs · 15% masked (2 of $len)"
    val legend = listOf(legend(DkInk.Yellow, "Masked this epoch"), legend(DkInk.Slate, "Visible"))
    val staticLine = "static (BERT): ${static.size} positions ever masked"
    val frames = listOf(
        DkFrame(
            header, grid(List(epochs) { static }, epochs), legend,
            listOf(staticLine, "masks chosen once, when the data was prepared"),
            "BERT masks the same {${static.size} positions} every epoch.",
            "The masking was done once in preprocessing, so the model sees the identical puzzle on every pass.",
        ),
        DkFrame(
            header, grid(dynamic, 2), legend,
            listOf(staticLine, "dynamic: {${covered(2)} of $len} positions after 2 epochs"),
            "RoBERTa draws {fresh masks} every epoch.",
            "Masking happens as each batch is built, so the same sentence becomes a new exercise each time.",
        ),
        DkFrame(
            header, grid(dynamic, 3), legend,
            listOf(staticLine, "dynamic: {${covered(3)} of $len} positions after 3 epochs"),
            "Three epochs in, {${covered(3)} positions} have been predicted.",
            "Each is a different fill-in-the-blank on the same words.",
        ),
        DkFrame(
            header, grid(dynamic, 4), legend,
            listOf(staticLine, "dynamic: {${covered(4)} of $len} positions after $epochs epochs", "also: no NSP · batch 256 → 8K · 16 → 160 GB"),
            "Over $epochs epochs the model learns to predict {${covered(4)} positions}, not ${static.size}.",
            "RoBERTa kept BERT's architecture and changed only the training recipe. Fresh masks are the change you can see.",
        ),
        DkFrame(
            header, grid(dynamic, 4), legend,
            listOf("dropped next-sentence prediction", "batch 256 → 8K · data 16 → 160 GB · trained longer"),
            "The rest of the recipe: {no NSP}, bigger batches, 10× the data.",
            "Removing next-sentence prediction didn't hurt; more data and longer training did most of the work.",
        ),
        DkFrame(
            header, grid(dynamic, 4), legend,
            listOf("same 125M-parameter architecture as BERT-base", "GLUE: 88.5 (RoBERTa) vs 82.1 (BERT-large)"),
            "Same model, better training: RoBERTa {beat} every BERT result.",
            "BERT was undertrained. The lesson carried into every later model: the recipe matters as much as the architecture.",
        ),
    )
    stepActions(frames) { "Next Epoch" }
}

// ── Hugging Face tokenizers: BPE ──

private val bpeCorpus = listOf(
    "the gardener watched the birds", "the gardener planted the small tree", "a small bird sat on the fence",
    "the birds watched the gardener", "the tallest tree in the garden", "the gardener watered the garden",
    "a bird watched the small garden", "the small bird sang", "the gardener smiled at the bird",
    "the best garden in the town", "the birds sang in the tree", "the gardener watched the sky",
    "the smaller bird flew to the tree", "the gardener and the birds", "the gardener rested in the garden",
)
private const val EndMark = "·"
private val bpeMergeCounts = listOf(0, 5, 10, 20, 40, 80)

private class BpeRun(val merges: List<Pair<String, String>>, val counts: List<Int>)

private val bpeFull: BpeRun by lazy {
    val freq = LinkedHashMap<String, Int>()
    bpeCorpus.forEach { line -> line.split(" ").forEach { w -> freq[w] = (freq[w] ?: 0) + 1 } }
    var words = freq.keys.map { w -> w.map { it.toString() } + EndMark }.toMutableList()
    val counts = freq.values.toList()
    val merges = ArrayList<Pair<String, String>>()
    val mergeCounts = ArrayList<Int>()
    repeat(bpeMergeCounts.last()) {
        val pairs = HashMap<Pair<String, String>, Int>()
        words.forEachIndexed { i, w -> for (k in 0 until w.size - 1) { val p = w[k] to w[k + 1]; pairs[p] = (pairs[p] ?: 0) + counts[i] } }
        if (pairs.isEmpty()) return@repeat
        val best = pairs.entries.sortedWith(compareByDescending<Map.Entry<Pair<String, String>, Int>> { it.value }.thenBy { it.key.first }.thenBy { it.key.second }).first()
        merges += best.key
        mergeCounts += best.value
        words = words.map { merge(it, best.key) }.toMutableList()
    }
    BpeRun(merges, mergeCounts)
}

private fun merge(w: List<String>, p: Pair<String, String>): List<String> {
    val out = ArrayList<String>()
    var k = 0
    while (k < w.size) {
        if (k < w.size - 1 && w[k] == p.first && w[k + 1] == p.second) { out += w[k] + w[k + 1]; k += 2 } else { out += w[k]; k++ }
    }
    return out
}

private fun bpeTokenize(word: String, merges: List<Pair<String, String>>): List<String> =
    merges.fold(word.map { it.toString() } + EndMark) { w, p -> merge(w, p) }

private fun bpeLab(): DkLab = DkLab(DkControl.StepperOnly, stepper = DkStepper("merges", bpeMergeCounts.map { it.toDouble() }, bpeMergeCounts.indexOf(40)) { n(it, 0) }) { _, p ->
    val m = bpeMergeCounts[p]
    val merges = bpeFull.merges.take(m)
    val sentence = "the gardener watched the smallest bird".split(" ")
    val tokens = sentence.map { bpeTokenize(it, merges) }
    val flat = tokens.flatten()
    val seen = bpeCorpus.flatMap { it.split(" ") }.toSet()
    val unseen = sentence.first { it !in seen }
    val unseenPieces = tokens[sentence.indexOf(unseen)]
    fun tone(piece: String): DkTokTone = when {
        piece == EndMark -> DkTokTone.Plain
        piece.removeSuffix(EndMark).length >= 3 -> DkTokTone.Whole
        else -> DkTokTone.Part
    }
    val firstMerges = bpeFull.merges.take(5).mapIndexed { i, (a, b) -> "$a+$b (${bpeFull.counts[i]})" }.joinToString(" ")
    val stage = DkTokens(flat.map { it to tone(it) }, notes = if (m > 0) listOf("first merges:", firstMerges) else listOf("no merges yet: every character is a token"))
    val header = "\"${sentence.joinToString(" ")}\" · merges learned from ${bpeCorpus.size} sentences"
    val legend = listOf(legend(DkInk.Violet, "Whole learned piece"), legend(DkInk.Yellow, "Short fragment"), legend(DkInk.Slate, "· = word end"))
    val perWord = flat.size.toDouble() / sentence.size
    val baseSymbols = bpeCorpus.joinToString("").replace(" ", "").toSet().size + 1
    listOf(
        DkFrame(
            header, stage, legend,
            listOf("tokens: {${flat.size}} for ${sentence.size} words = ${n(perWord)} per word · unknown 0", "vocabulary: ${baseSymbols + m} symbols after $m merges"),
            if (m == 0) "With no merges, the sentence is {${flat.size} characters}." else "$m merges cut the sentence to {${flat.size} tokens}.",
            "BPE starts from single characters, so any text can be written; each merge adds one new symbol to the vocabulary.",
        ),
        DkFrame(
            header, stage, legend,
            listOf("tokens: ${flat.size} for ${sentence.size} words = ${n(perWord)} per word · unknown 0", "\"$unseen\" → ${unseenPieces.joinToString(" | ")}"),
            "An unseen word is still covered: \"$unseen\" splits into {${unseenPieces.size} pieces}.",
            "BPE repeatedly merges the most frequent adjacent pair. Common words become one token; rare ones fall back to fragments, so nothing is unknown.",
        ),
        DkFrame(
            header, stage, legend,
            listOf("more merges → bigger vocabulary, shorter sequences", "GPT-2: 50,257 tokens · BERT WordPiece: 30,522"),
            "Vocabulary size is a {trade-off}.",
            "Fewer tokens per word means shorter sequences and cheaper attention, but a bigger embedding table and rarer pieces to learn.",
        ),
    ).map { DkFrame(it.header, it.stage, it.legend, it.formula, it.headline, it.body, it.chips, "Next") }
}
