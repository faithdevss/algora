package com.algora.app.feature.topics

import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

// ── Embeddings, syntax and pretrained-model storyboards ──────────────────────
// GloVe, FastText, ELMo, tagging, NER, chunking, both parsers, coreference, lexicon sentiment, and the
// LLM, BART, XLNet and GPT-3/4 labs, drawn by NlpStoryLabs.kt. GloVe is trained here, the parser runs the
// arc-standard oracle, the chunker matches its rule over the tags; vectors and logits that stand in for a
// trained network say so in their caption. NlpSyntaxFrames.swift is the iOS port.

internal val nlpSyntaxTopicIds = setOf(
    "glove", "fasttext", "elmo", "pos_tagging", "ner", "chunking", "dependency_parsing", "constituency_parsing",
    "coreference", "sentiment_lexicon", "llms", "bart", "xlnet", "gpt3_gpt4",
)

internal fun nlpSyntaxLab(topicId: String): List<NbFrame>? = when (topicId) {
    "glove" -> gloveFrames()
    "fasttext" -> fastTextFrames()
    "elmo" -> elmoFrames()
    "pos_tagging" -> posFrames()
    "ner" -> nerFrames()
    "chunking" -> chunkFrames()
    "dependency_parsing" -> dependencyFrames()
    "constituency_parsing" -> constituencyFrames()
    "coreference" -> corefFrames()
    "sentiment_lexicon" -> sentimentFrames()
    "llms" -> llmFrames()
    "bart" -> bartFrames()
    "xlnet" -> xlnetFrames()
    "gpt3_gpt4" -> gptFrames()
    else -> null
}

private fun f2(v: Double) = nbF(v, 2)

private class SynRng(seed: Long) {
    private var s = seed
    fun u(): Double { s = (s * 1103515245L + 12345L) and 0x7fffffffL; return s / 2147483648.0 }
}

// ── GloVe ──

private val gloveCorpus = listOf("the king rules the land", "the queen rules the land", "a man rules", "a woman rules")
private val gloveVocab = listOf("the", "king", "queen", "rules", "land", "a", "man", "woman")

private class GloveRun(val x: Array<DoubleArray>, val losses: List<Double>, val w: Array<DoubleArray>, val nonZero: Int, val tokens: Int)

private fun trainGlove(): GloveRun {
    val v = gloveVocab.size
    val x = Array(v) { DoubleArray(v) }
    var tokens = 0
    gloveCorpus.forEach { s ->
        val ws = s.split(" ").map { gloveVocab.indexOf(it) }
        tokens += ws.size
        for (i in ws.indices) for (j in ws.indices) {
            val d = kotlin.math.abs(i - j)
            if (d in 1..2) x[ws[i]][ws[j]] += 1.0 / d
        }
    }
    val pairs = (0 until v).flatMap { i -> (0 until v).filter { x[i][it] > 0 }.map { i to it } }
    val dim = 4
    val r = SynRng(11)
    val w = Array(v) { DoubleArray(dim) { (r.u() - 0.5) * 0.5 } }
    val c = Array(v) { DoubleArray(dim) { (r.u() - 0.5) * 0.5 } }
    val bw = DoubleArray(v)
    val bc = DoubleArray(v)
    val xMax = 3.0
    fun weight(xx: Double) = min(1.0, Math.pow(xx / xMax, 0.75))
    val losses = mutableListOf<Double>()
    val rate = 0.05
    repeat(401) { epoch ->
        var loss = 0.0
        pairs.forEach { (i, j) ->
            val diff = (0 until dim).sumOf { w[i][it] * c[j][it] } + bw[i] + bc[j] - ln(x[i][j])
            val f = weight(x[i][j])
            loss += f * diff * diff
            if (epoch < 400) {
                val g = f * diff
                for (k in 0 until dim) {
                    val wi = w[i][k]
                    w[i][k] -= rate * g * c[j][k]
                    c[j][k] -= rate * g * wi
                }
                bw[i] -= rate * g
                bc[j] -= rate * g
            }
        }
        losses += loss / pairs.size
    }
    val emb = Array(v) { i -> DoubleArray(dim) { w[i][it] + c[i][it] } }
    return GloveRun(x, losses, emb, pairs.size, tokens)
}

private fun cosA(a: DoubleArray, b: DoubleArray) = a.indices.sumOf { a[it] * b[it] } / (sqrt(a.sumOf { it * it }) * sqrt(b.sumOf { it * it }))

private fun gloveFrames(): List<NbFrame> {
    val run = trainGlove()
    val v = gloveVocab.size
    val top = run.x.maxOf { it.max() }
    fun matrix(hot: Pair<Int, Int>? = null) = NbBlock.Grid(
        gloveVocab.map { it.take(5) }, gloveVocab,
        run.x.mapIndexed { i, row -> row.mapIndexed { j, xx -> NbGCell(if (xx == 0.0) "" else nbDot2(xx).let { if (xx >= 1) f2(xx) else it }, if (xx == 0.0) 0.0 else 0.25 + 0.7 * xx / top, NbInk.Sky, ring = hot == (i to j)) } },
        cellHeight = 18,
    )
    fun lossPlot(upTo: Int) = NbBlock.Plot(
        0.0 to 400.0, -5.0 to 0.0, 150,
        lines = listOf(NbLine(run.losses.take(upTo + 1).mapIndexed { e, l -> NbP(e.toDouble(), log10(max(l, 1e-5)).coerceIn(-5.0, 0.0)) }, NbInk.Sky, width = 2f)),
        xTicks = listOf(0.0 to "0", 100.0 to "100", 200.0 to "200", 300.0 to "300", 400.0 to "400"),
        yTicks = listOf(0.0 to "1e0", -1.0 to "1e−1", -2.0 to "1e−2", -3.0 to "1e−3", -4.0 to "1e−4", -5.0 to "1e−5"),
        xLabel = "epoch",
    )
    val k = gloveVocab.indexOf("king")
    val q = gloveVocab.indexOf("queen")
    val sims = gloveVocab.indices.filter { it != k }.map { gloveVocab[it] to cosA(run.w[k], run.w[it]) }.sortedByDescending { it.second }
    val head = NbBlock.Caption("co-occurrence X · ${v} words, window 2, 1/distance")
    val legend = listOf(nbLegend(NbInk.Sky, "Co-occurrence weight"))
    val kq = run.x[k][gloveVocab.indexOf("rules")]
    return listOf(
        NbFrame(listOf(NbBlock.Caption("corpus · ${run.tokens} tokens"), NbBlock.Toks(gloveCorpus.map { nbTok(it) })), emptyList(),
            "GloVe starts from {global counts}, not a sliding model.",
            "Word2vec learns one window at a time. GloVe first counts how often every pair of words appears near each other across the whole corpus, then fits vectors to those counts."),
        NbFrame(listOf(head, matrix(k to gloveVocab.indexOf("rules"))), legend,
            "Count co-occurrences, {weighted by distance}.",
            "A neighbour one word away adds 1, two words away adds ½. X(king, rules) = ${f2(kq)}: \"king\" is followed by \"rules\" once.",
            fx = listOf("X(king, rules) = 1/1 = {${f2(kq)}}")),
        NbFrame(listOf(head, matrix()), legend,
            "Fit vectors so {w_i · w̃_j ≈ log X_ij}.",
            "Every non-zero cell becomes one regression target. The weight f(X) caps frequent pairs so \"the\" doesn't dominate, and zero cells are skipped entirely.",
            fx = listOf("J = Σ f(X_ij)·(w_i·w̃_j + b_i + b̃_j − log X_ij)²", "f(x) = min(1, (x / ${3})^0.75)")),
        NbFrame(listOf(head, matrix(), NbBlock.Caption("weighted least-squares loss, log scale"), lossPlot(400)), legend + nbLegend(NbInk.Sky, "Mean loss", SwatchStyle.Line),
            "Fit by SGD over the ${run.nonZero} non-zero entries.",
            "400 epochs, no sliding window, no sampling: the matrix is the whole training set. Loss falls from ${lsSci(run.losses.first())} to ${lsSci(run.losses.last())}."),
        NbFrame(listOf(NbBlock.Caption("cosine similarity to \"king\" · 4-d vectors w + w̃"), NbBlock.Bars(sims.map { NbBar(it.first, f2(it.second), max(it.second, 0.0), if (it.first == "queen") NbInk.Green else NbInk.Sky) }, labelWidth = 64)),
            listOf(nbLegend(NbInk.Green, "Same contexts as king")),
            "\"king\" and \"queen\" {share every context}.",
            "Both appear between \"the\" and \"rules\", so their rows of X are nearly identical and their vectors end up close: cosine ${f2(cosA(run.w[k], run.w[q]))}."),
        NbFrame(listOf(NbBlock.Caption("why log counts"), NbBlock.Table(listOf("ratio", "meaning"), listOf(1.2f, 1f), listOf(
            NbRow(listOf(nbCell("P(ice|solid) / P(steam|solid)"), nbCell("8.9 — large", NbInk.Green))),
            NbRow(listOf(nbCell("P(ice|gas) / P(steam|gas)"), nbCell("0.085 — small", NbInk.Red))),
            NbRow(listOf(nbCell("P(ice|water) / P(steam|water)"), nbCell("1.36 — ≈ 1"))),
        ), listOf(0, 2))), emptyList(),
            "Meaning lives in {ratios} of co-occurrence.",
            "From the GloVe paper: \"solid\" relates to ice not steam, \"gas\" the reverse, \"water\" to both. Logs turn those ratios into differences, which dot products can express."),
        NbFrame(listOf(NbBlock.Table(listOf("", "word2vec", "GloVe"), listOf(0.8f, 1f, 1f), listOf(
            NbRow(listOf(nbName("sees"), nbCell("one window at a time"), nbCell("all counts at once"))),
            NbRow(listOf(nbName("objective"), nbCell("predict neighbours"), nbCell("fit log X"))),
            NbRow(listOf(nbName("training set"), nbCell("every token"), nbCell("non-zero cells", NbInk.Green))),
            NbRow(listOf(nbName("vectors"), nbCell("similar quality"), nbCell("similar quality"))),
        ))), emptyList(),
            "Two routes to {the same kind of vector}.",
            "Levy and Goldberg showed skip-gram with negative sampling implicitly factorises a shifted PMI matrix — GloVe just does it explicitly."),
    )
}

// ── FastText ──

private fun ngrams(w: String, n: Int = 3) = "<$w>".windowed(n)

private fun fastTextFrames(): List<NbFrame> {
    val base = ngrams("king").toSet()
    fun row(w: String) = listOf(
        NbBlock.Caption(w, "${ngrams(w).size} n-grams" + if (w != "king") " · ${ngrams(w).count { it in base }} shared with king" else ""),
        NbBlock.Toks(ngrams(w).map { nbTok(it, if (it in base) NbTone.Pick else NbTone.Plain) }),
    )
    val legend = listOf(nbLegend(NbInk.Violet, "Shared with \"king\""), nbLegend(NbInk.Slate, "Unique to the word"))
    val known = (ngrams("king") + ngrams("kingdom") + ngrams("kings")).toSet()
    val unseen = ngrams("kingly")
    val typo = ngrams("kingg")
    val head = NbBlock.Caption("character 3-grams with boundary markers")
    return listOf(
        NbFrame(listOf(head) + row("king") + listOf(NbBlock.Callout(listOf("\"king\" → <ki · kin · ing · ng>  + the whole word <king>"))), legend,
            "FastText splits each word into {character n-grams}.",
            "< and > mark the word's edges, so \"ing\" inside a word differs from the suffix \"ng>\". The whole word is kept as one more unit."),
        NbFrame(listOf(head) + row("king") + row("kingdom") + row("kings"), legend,
            "A word's vector is the sum of its subwords' vectors.",
            "\"king\", \"kingdom\" and \"kings\" share their first three n-grams, so evidence for one helps all, and unseen words still get a vector.",
            fx = listOf("v(w) = Σ v(g) over g ∈ n-grams(w)", "unseen \"kingly\" → {${unseen.count { it in known }} of ${unseen.size}} n-grams already trained")),
        NbFrame(listOf(head) + row("kingly"), legend,
            "An unseen word {still gets a vector}.",
            "Word2vec has nothing for a word it never saw. FastText sums the ${unseen.count { it in known }} trained n-grams of \"kingly\" and the rest start at their (shared, hashed) initial values.",
            chips = listOf(nbChip("known n-grams", "${unseen.count { it in known }} / ${unseen.size}", true))),
        NbFrame(listOf(head) + row("kingg"), legend,
            "Typos land {near the right word}.",
            "\"kingg\" shares ${typo.count { it in base }} of its ${typo.size} n-grams with \"king\", so its vector sits close by — useful for social-media text and morphologically rich languages.",
            chips = listOf(nbChip("shared with king", "${typo.count { it in base }} / ${typo.size}", true))),
        NbFrame(listOf(NbBlock.Caption("n-grams per word length, n = 3…6"), NbBlock.Table(listOf("word", "letters", "n-grams"), listOf(1f, 0.6f, 0.6f),
            listOf("king", "kingdom", "internationalization").map { w -> NbRow(listOf(nbName(w), nbCell("${w.length}"), nbCell("${(3..6).sumOf { ngrams(w, it).size }}", NbInk.Indigo))) })),
            listOf(nbLegend(NbInk.Indigo, "Units summed per word")),
            "Real FastText uses n = 3 to 6 — {many units per word}.",
            "A long word sums dozens of vectors. To bound memory, n-grams are hashed into a fixed number of buckets (2 million by default); collisions are tolerated."),
        NbFrame(listOf(NbBlock.Kv(listOf("vocabulary" to "words + hashed n-grams", "OOV words" to "handled", "morphology" to "shared prefixes and suffixes", "cost" to "more vectors to store and sum"))), emptyList(),
            "Subwords buy {robustness} for the price of memory.",
            "FastText's vectors are trained like skip-gram; only the input representation changes. Modern subword tokenizers (BPE, WordPiece) carry the same idea into transformers."),
    )
}

// ── ELMo ──

private val elmoSentences = listOf("the river bank flooded", "we sat on the bank of the stream", "the bank raised its rates", "I paid it into the bank")

/** Illustrative contextual vectors for "bank" (2-D projection) — river sense up and left, money sense down and right. */
private val elmoVecs = listOf(doubleArrayOf(0.62, 0.78), doubleArrayOf(0.70, 0.70), doubleArrayOf(0.55, -0.62), doubleArrayOf(0.66, -0.58))
private val elmoStatic = doubleArrayOf(0.6, 0.05)

private fun elmoFrames(): List<NbFrame> {
    fun c(a: DoubleArray, b: DoubleArray) = cosA(a, b)
    fun plot(upTo: Int, static: Boolean = true) = NbBlock.Plot(
        -1.0 to 1.0, -1.0 to 1.0, 170,
        dots = (if (static) listOf(NbDot(NbP(elmoStatic[0] - 0.75, elmoStatic[1]), NbInk.Yellow, 6f, label = "word2vec \"bank\" (one vector)")) else emptyList()) +
            elmoVecs.take(upTo).mapIndexed { i, v -> NbDot(NbP(v[0], v[1]), if (i < 2) NbInk.Sky else NbInk.Orange, 7f, text = "${i + 1}") },
        segs = listOf(NbSeg(NbP(-0.95, 0.0), NbP(0.95, 0.0), NbInk.Slate, width = 1f)),
        grid = false,
    )
    fun sentences(upTo: Int) = NbBlock.Table(emptyList(), listOf(0.15f, 1.6f), aligns = listOf(0, 0),
        rows = elmoSentences.take(upTo).mapIndexed { i, s -> NbRow(listOf(nbCell("${i + 1}", if (i < 2) NbInk.Sky else NbInk.Orange, true), NbCell(s, mono = false))) })
    val legend = listOf(nbLegend(NbInk.Sky, "River sense"), nbLegend(NbInk.Orange, "Money sense"), nbLegend(NbInk.Yellow, "Static vector"))
    val head = NbBlock.Caption("4 sentences containing \"bank\" · 2-D projection (illustrative)")
    val c12 = c(elmoVecs[0], elmoVecs[1])
    val c13 = c(elmoVecs[0], elmoVecs[2])
    val c34 = c(elmoVecs[2], elmoVecs[3])
    return listOf(
        NbFrame(listOf(head, plot(0), sentences(4)), listOf(nbLegend(NbInk.Yellow, "Static vector")),
            "Word2vec gives \"bank\" {one vector}.",
            "A static embedding is a lookup: every occurrence of \"bank\" gets the same point, halfway between its senses."),
        NbFrame(listOf(head, plot(4), sentences(4)), legend,
            "ELMo makes the vector a function of the sentence.",
            "A biLSTM reads the whole sentence, so four sentences give four vectors — and the senses separate.",
            chips = listOf(nbChip("cos(1,2)", f2(c12), true), nbChip("cos(1,3)", f2(c13)))),
        NbFrame(listOf(NbBlock.Caption("two directions over \"we sat on the bank of the stream\""), NbBlock.Toks("we sat on the bank of the stream".split(" ").map { nbTok(it, if (it == "bank") NbTone.Pick else if (it in setOf("sat", "on", "the")) NbTone.Blue else NbTone.Orange) }),
            NbBlock.Kv(listOf("forward LSTM" to "reads we → sat → on → the → bank", "backward LSTM" to "reads stream → the → of → bank", "\"bank\" vector" to "both directions, concatenated"))),
            listOf(nbLegend(NbInk.Blue, "Left context"), nbLegend(NbInk.Orange, "Right context")),
            "Two LSTMs, {one per direction}.",
            "\"stream\" comes after \"bank\" — only the backward LSTM sees it. Concatenating both states gives a vector informed by the whole sentence."),
        NbFrame(listOf(NbBlock.Caption("ELMo = weighted sum of layers"), NbBlock.Bars(listOf(
            NbBar("char CNN", "layer 0", 0.25, NbInk.Grey), NbBar("biLSTM 1", "syntax", 0.55, NbInk.Sky), NbBar("biLSTM 2", "semantics", 0.85, NbInk.Indigo),
        ), labelWidth = 80), NbBlock.Callout(listOf("ELMo_k = γ · Σ_j s_j · h_k,j   (s = softmax, learned per task)"))),
            emptyList(),
            "Each task learns {its own mix of layers}.",
            "Lower layers capture syntax (good for tagging), higher ones word sense (good for disambiguation). The task picks the weights s_j."),
        NbFrame(listOf(head, plot(4), NbBlock.Tiles(listOf(NbTile(f2(c12), "river vs river", NbInk.Sky), NbTile(f2(c34), "money vs money", NbInk.Orange), NbTile(f2(c13), "river vs money", NbInk.Red)))),
            legend,
            "Same sense {close}, different sense {w:far}.",
            "Within a sense the vectors agree; across senses cosine drops to ${f2(c13)}. A static vector can't do both — its similarity to each use is fixed."),
        NbFrame(listOf(NbBlock.Kv(listOf("2018 · ELMo" to "contextual vectors from a biLSTM LM", "use" to "features added to a task model", "next · BERT" to "transformer, fine-tuned end to end", "kept idea" to "a word's vector depends on its sentence"))), emptyList(),
            "ELMo started {contextual embeddings}.",
            "It was used as frozen features; BERT and GPT replaced the LSTMs with transformers and fine-tuned the whole network — but the vector-per-occurrence idea is ELMo's."),
    )
}

// ── POS tagging ──

private fun posFrames(): List<NbFrame> {
    val s1 = listOf("the" to "DT", "old" to "JJ", "man" to "NN", "chased" to "VBD")
    val s2 = listOf("they" to "PRP", "man" to "VBP", "a" to "DT", "boat" to "NN")
    fun toks(s: List<Pair<String, String>>, hot: String? = "man") = NbBlock.Toks(s.map { (w, t) -> nbTok(w, if (w == hot) NbTone.Hot else NbTone.Plain, t) })
    val counts = listOf("NN" to 2, "VBP" to 1)
    val bars = NbBlock.Bars(counts.map { NbBar(it.first, "${it.second}", it.second / 3.0, if (it.first == "NN") NbInk.Blue else NbInk.Violet) }, labelWidth = 44)
    val head = NbBlock.Caption("the same word, two categories")
    val legend = listOf(nbLegend(NbInk.Yellow, "Ambiguous word"))
    val baseline = listOf(true, true, true, true, true, false, true, true)
    val acc = baseline.count { it }.toDouble() / baseline.size
    return listOf(
        NbFrame(listOf(head, NbBlock.Caption("sentence 1"), toks(s1), NbBlock.Caption("sentence 2"), toks(s2), NbBlock.Caption("training counts for \"man\""), bars,
            NbBlock.Callout(listOf("Most-frequent-tag baseline: always NN → right in sentence 1, wrong in sentence 2."), warn = true)), legend,
            "Tagging assigns each token a syntactic category.",
            "It looks like a lookup until a word has more than one. Nothing about \"man\" itself decides — only its neighbours do."),
        NbFrame(listOf(NbBlock.Caption("most-frequent-tag baseline on both sentences"), NbBlock.Toks((s1 + s2).mapIndexed { i, (w, t) -> nbTok(w, if (baseline[i]) NbTone.Good else NbTone.Bad, if (baseline[i]) t else "NN ≠ $t") })),
            listOf(nbLegend(NbInk.Green, "Correct"), nbLegend(NbInk.Red, "Wrong")),
            "The baseline gets {${baseline.count { it }} of ${baseline.size}}.",
            "Tag every word with its most common tag in training. On real text this already reaches about 92% — most words have one tag.",
            chips = listOf(nbChip("accuracy", nbF(acc * 100, 1) + "%", true))),
        NbFrame(listOf(NbBlock.Caption("context decides · P(tag | previous tag)"), toks(s2), NbBlock.Table(listOf("previous", "next", "P"), listOf(0.8f, 0.6f, 0.5f), listOf(
            NbRow(listOf(nbName("PRP"), nbCell("VBP"), nbCell("0.42", NbInk.Green))), NbRow(listOf(nbName("PRP"), nbCell("NN"), nbCell("0.01", NbInk.Red))),
            NbRow(listOf(nbName("JJ"), nbCell("NN"), nbCell("0.45", NbInk.Green))), NbRow(listOf(nbName("JJ"), nbCell("VBP"), nbCell("0.003", NbInk.Red))),
        ))), legend,
            "After a pronoun, {a verb} is far likelier than a noun.",
            "An HMM or CRF multiplies these transition probabilities by how often each tag emits \"man\". The context term overrules the word's own preference for NN. (Transition values illustrative.)"),
        NbFrame(listOf(NbBlock.Caption("Penn Treebank tags used here"), NbBlock.Table(listOf("tag", "meaning", "example"), listOf(0.5f, 1.2f, 0.8f), listOf(
            "DT" to ("determiner" to "the, a"), "JJ" to ("adjective" to "old"), "NN" to ("noun, singular" to "man, boat"), "VBD" to ("verb, past tense" to "chased"),
            "VBP" to ("verb, present" to "man (staff)"), "PRP" to ("personal pronoun" to "they"),
        ).map { (t, m) -> NbRow(listOf(nbName(t, NbInk.Indigo), NbCell(m.first, mono = false), nbCell(m.second))) }, listOf(0, 0, 2))), emptyList(),
            "The tag set has {45 tags}, not 8 parts of speech.",
            "Penn Treebank splits verbs by tense and person and nouns by number, because those distinctions matter to parsers downstream."),
        NbFrame(listOf(NbBlock.Caption("accuracy on WSJ (Penn Treebank)"), NbBlock.Bars(listOf(
            NbBar("most frequent", "≈92%", 0.92, NbInk.Grey), NbBar("HMM", "≈96%", 0.96, NbInk.Sky), NbBar("BiLSTM-CRF", "≈97.5%", 0.975, NbInk.Indigo),
        ), labelWidth = 108)), emptyList(),
            "The last few points are {all about context}.",
            "Most tokens are easy; the gains come from ambiguous words like \"man\", \"that\" and \"back\", where the neighbours decide."),
        NbFrame(listOf(NbBlock.Kv(listOf("ambiguous types" to "~15% of word types", "ambiguous tokens" to "~55% of running text", "why" to "common words are the ambiguous ones", "next" to "chunking and parsing use the tags"))), emptyList(),
            "Few word types are ambiguous — but {they are the common ones}.",
            "\"that\", \"back\", \"down\", \"put\": a minority of the vocabulary, a majority of the tokens. Tagging is where every syntactic pipeline starts."),
    )
}

// ── NER ──

private fun nerFrames(): List<NbFrame> {
    val toks = listOf("Ada" to "B-PER", "Lovelace" to "I-PER", "joined" to "O", "Analytical" to "B-ORG", "Engine" to "I-ORG", "Ltd" to "I-ORG", "in" to "O", "London" to "B-LOC")
    fun tone(tag: String) = when (tag.substringAfter('-')) { "PER" -> NbTone.Blue; "ORG" -> NbTone.Pink; "LOC" -> NbTone.Orange; else -> NbTone.Dim }
    fun bio(show: Boolean = true) = NbBlock.Toks(toks.map { (w, t) -> nbTok(w, if (show) tone(t) else NbTone.Plain, if (show) t else "") })
    // Spans read back from the tags: each B- with the I- tags that follow it.
    val spans = mutableListOf<Pair<String, String>>()
    toks.forEach { (w, t) ->
        when {
            t.startsWith("B-") -> spans += w to t.drop(2)
            t.startsWith("I-") && spans.isNotEmpty() -> spans[spans.lastIndex] = (spans.last().first + " " + w) to spans.last().second
        }
    }
    fun ink(type: String) = when (type) { "PER" -> NbInk.Blue; "ORG" -> NbInk.Pink; else -> NbInk.Orange }
    val entities = NbBlock.Table(emptyList(), listOf(1.5f, 0.5f), spans.map { (s, t) -> NbRow(listOf(nbName(s, ink(t)), nbCell(t, ink(t)))) })
    val labels = NbBlock.Boxes(listOf("PER", "ORG", "LOC").map { NbBox(it, listOf("B-$it · I-$it"), ink(it)) })
    val legend = listOf(nbLegend(NbInk.Blue, "Person"), nbLegend(NbInk.Pink, "Organisation"), nbLegend(NbInk.Orange, "Location"), nbLegend(NbInk.Slate, "Outside (O)"))
    // A model's prediction with one boundary error and one type error, scored at entity level.
    val predicted = listOf("Ada Lovelace" to "PER", "Analytical Engine" to "ORG", "London" to "ORG")
    val correct = predicted.count { it in spans }
    val p = correct.toDouble() / predicted.size
    val r = correct.toDouble() / spans.size
    val f1 = 2 * p * r / (p + r)
    return listOf(
        NbFrame(listOf(NbBlock.Caption("find the names · one label per token"), bio(false)), emptyList(),
            "NER turns span-finding into {token labelling}.",
            "Entities can be several tokens long. Labelling each token keeps the model a simple per-position classifier."),
        NbFrame(listOf(NbBlock.Caption("gold BIO tags · one tag per token"), bio(), NbBlock.Caption("entities recovered from the tags"), entities, NbBlock.Caption("label set · 2 × 3 + O = 7"), labels), legend,
            "B- starts an entity, I- continues it, O is outside.",
            "Spans are read back by joining each B- with the I- tags that follow it."),
        NbFrame(listOf(NbBlock.Caption("why B- is needed"), NbBlock.Toks(listOf(nbTok("Paris", NbTone.Orange, "B-LOC"), nbTok("London", NbTone.Orange, "B-LOC"), nbTok("Rome", NbTone.Orange, "B-LOC"))),
            NbBlock.Callout(listOf("with only I-LOC: Paris London Rome → {one} entity", "with B-LOC each: → {three} entities"))), legend,
            "Adjacent entities of the same type {need the B- tag}.",
            "\"…flights Paris London Rome…\" are three places. Without a begin tag the decoder would merge them into one span."),
        NbFrame(listOf(NbBlock.Caption("a sequence the decoder must not output"), NbBlock.Toks(listOf(nbTok("joined", NbTone.Dim, "O"), nbTok("Engine", NbTone.Bad, "I-ORG"), nbTok("Ltd", NbTone.Pink, "I-ORG"))),
            NbBlock.Callout(listOf("O → I-ORG is invalid: an entity can't continue without starting", "CRF layer: transition score O → I-* = {−∞}"))), legend,
            "A CRF layer {forbids} invalid tag sequences.",
            "Independent per-token predictions can output O followed by I-ORG. A CRF scores whole sequences and gives impossible transitions zero probability."),
        NbFrame(listOf(NbBlock.Caption("same word, different type"), NbBlock.Table(emptyList(), listOf(1.6f, 0.5f), listOf(
            NbRow(listOf(NbCell("Washington signed the bill", mono = false), nbCell("PER", NbInk.Blue))),
            NbRow(listOf(NbCell("we flew to Washington", mono = false), nbCell("LOC", NbInk.Orange))),
            NbRow(listOf(NbCell("Washington beat Dallas 3–1", mono = false), nbCell("ORG", NbInk.Pink))),
        ))), legend,
            "The type comes from context, {not the name}.",
            "A gazetteer of names can't decide this. The model has to read the verb and the preposition around the name."),
        NbFrame(listOf(NbBlock.Caption("scored at entity level · exact span and type"), NbBlock.Table(listOf("predicted", "type", "gold?"), listOf(1.4f, 0.5f, 0.5f), predicted.map { (s, t) ->
            val ok = (s to t) in spans
            NbRow(listOf(nbName(s, ink(t)), nbCell(t), nbCell(if (ok) "✓" else "✗", if (ok) NbInk.Green else NbInk.Red, true)))
        }), NbBlock.Tiles(listOf(NbTile(f2(p), "precision", NbInk.Indigo), NbTile(f2(r), "recall", NbInk.Indigo), NbTile(f2(f1), "F1", NbInk.Green)))),
            legend,
            "Off by one token {counts as wrong}.",
            "\"Analytical Engine\" misses \"Ltd\" and \"London\" has the wrong type: both are errors. $correct of ${predicted.size} predictions are exact, giving F1 = ${f2(f1)}."),
        NbFrame(listOf(NbBlock.Caption("what flat BIO cannot express"), NbBlock.Toks(listOf(nbTok("Bank", NbTone.Pink), nbTok("of", NbTone.Pink), nbTok("England", NbTone.Orange))),
            NbBlock.Callout(listOf("ORG: Bank of England", "LOC inside it: England  → needs a {second layer}"))), legend,
            "Nested entities {don't fit} one tag per token.",
            "\"England\" is a location inside an organisation's name. Flat BIO picks one; nested NER uses span classification or layered tags."),
    )
}

// ── Chunking ──

private val chunkSentence = listOf("the" to "DT", "small" to "JJ", "dog" to "NN", "chased" to "VBD", "a" to "DT", "cat" to "NN", "in" to "IN", "the" to "DT", "garden" to "NN")

/** NP = DT? JJ* NN+, matched greedily left to right; returns [start, end) spans. */
private fun npChunks(): List<Pair<Int, Int>> {
    val out = mutableListOf<Pair<Int, Int>>()
    var i = 0
    val tags = chunkSentence.map { it.second }
    while (i < tags.size) {
        var j = i
        if (j < tags.size && tags[j] == "DT") j++
        while (j < tags.size && tags[j] == "JJ") j++
        val nounStart = j
        while (j < tags.size && tags[j].startsWith("NN")) j++
        if (j > nounStart) { out += i to j; i = j } else i++
    }
    return out
}

private fun chunkFrames(): List<NbFrame> {
    val chunks = npChunks()
    fun toks(upTo: Int, scanned: Int) = NbBlock.Toks(chunkSentence.mapIndexed { i, (w, t) ->
        val k = chunks.indexOfFirst { i >= it.first && i < it.second }
        val tone = when {
            k == upTo -> NbTone.Hot
            k in 0 until upTo -> NbTone.Pick
            i < scanned -> NbTone.Plain
            else -> NbTone.Dim
        }
        nbTok(w, tone, t)
    })
    fun bracket(n: Int): NbBlock.Text {
        val spans = mutableListOf<NbSpan>()
        var i = 0
        while (i < chunkSentence.size) {
            val k = chunks.indexOfFirst { it.first == i }
            if (k in 0 until n) {
                val (a, b) = chunks[k]
                spans += NbSpan("[NP " + chunkSentence.subList(a, b).joinToString(" ") { it.first } + "]", if (k == n - 1) NbTone.Hot else NbTone.Blue)
                spans += NbSpan(" ")
                i = b
            } else {
                spans += NbSpan(chunkSentence[i].first + " ", NbTone.Dim)
                i++
            }
        }
        return NbBlock.Text(spans, mono = true)
    }
    fun ruleTable(k: Int): NbBlock.Table {
        val (a, b) = chunks[k]
        val words = chunkSentence.subList(a, b)
        val det = words.filter { it.second == "DT" }.joinToString(" ") { it.first }.ifEmpty { "—" }
        val adj = words.filter { it.second == "JJ" }.joinToString(" ") { it.first }.ifEmpty { "—" }
        val noun = words.filter { it.second.startsWith("NN") }.joinToString(" ") { it.first }
        return NbBlock.Table(listOf("pattern", "means", "token"), listOf(0.5f, 1.2f, 0.7f), listOf(
            NbRow(listOf(nbName("DT?", NbInk.Yellow), nbCell("optional determiner"), nbCell(det, bold = true))),
            NbRow(listOf(nbName("JJ*", NbInk.Yellow), nbCell("any adjectives"), nbCell(adj, bold = true))),
            NbRow(listOf(nbName("NN+", NbInk.Yellow), nbCell("one or more nouns"), nbCell(noun, bold = true))),
        ))
    }
    val legend = listOf(nbLegend(NbInk.Yellow, "Current chunk"), nbLegend(NbInk.Violet, "Later chunks"), nbLegend(NbInk.Slate, "Not yet scanned"))
    val doneLegend = listOf(nbLegend(NbInk.Violet, "Chunk found"), nbLegend(NbInk.Yellow, "Current chunk"))
    val head = NbBlock.Caption("NP rule · DT? JJ* NN+")
    val iob = chunkSentence.mapIndexed { i, (w, _) ->
        val k = chunks.indexOfFirst { i >= it.first && i < it.second }
        nbTok(w, if (k < 0) NbTone.Dim else NbTone.Blue, if (k < 0) "O" else if (chunks[k].first == i) "B-NP" else "I-NP")
    }
    fun chunkText(k: Int) = chunkSentence.subList(chunks[k].first, chunks[k].second).joinToString(" ") { it.first }
    return listOf(
        NbFrame(listOf(NbBlock.Caption("input · words with their POS tags"), NbBlock.Toks(chunkSentence.map { (w, t) -> nbTok(w, NbTone.Plain, t) })), emptyList(),
            "Chunking groups tags into {flat phrases}.",
            "It runs on POS tags, not words. A noun phrase is a determiner, any adjectives, then nouns — one regular expression over the tag sequence."),
        NbFrame(listOf(head, toks(0, chunks[0].second), NbBlock.Caption("rule matched token by token"), ruleTable(0), NbBlock.Caption("after all chunks (preview)"), bracket(chunks.size)), legend,
            "Chunk 1: \"${chunkText(0)}\" is an NP — tokens [${chunks[0].first}, ${chunks[0].second}).",
            "Chunks never nest and never overlap — that is the whole simplification over a full parse."),
        NbFrame(listOf(head, toks(1, chunks[0].second + 1)), legend,
            "\"chased\" is VBD — {no NP starts here}.",
            "The rule needs a noun to finish. A verb can't start a determiner or an adjective run, so the scanner moves on one token."),
        NbFrame(listOf(head, toks(1, chunks[1].second), ruleTable(1)), legend,
            "Chunk 2: \"${chunkText(1)}\" — tokens [${chunks[1].first}, ${chunks[1].second}).",
            "No adjectives this time: JJ* matches zero tokens, which a star allows."),
        NbFrame(listOf(head, toks(2, chunks[1].second + 1)), legend,
            "\"in\" is a preposition — {skipped}.",
            "Prepositions belong to PP chunks, which a second rule would find. The NP rule simply passes over it."),
        NbFrame(listOf(head, toks(2, chunks[2].second), ruleTable(2)), legend,
            "Chunk 3: \"${chunkText(2)}\" — tokens [${chunks[2].first}, ${chunks[2].second}).",
            "The scan reaches the end with ${chunks.size} noun phrases."),
        NbFrame(listOf(head, bracket(chunks.size)), doneLegend,
            "Three NPs, {each a contiguous span}.",
            "The output is a bracketing of the sentence. Every word is either inside exactly one chunk or outside all of them."),
        NbFrame(listOf(NbBlock.Caption("the same chunks as IOB tags"), NbBlock.Toks(iob)), listOf(nbLegend(NbInk.Blue, "Inside a noun phrase"), nbLegend(NbInk.Slate, "Outside")),
            "As labels, chunking is {the same task as NER}.",
            "B-NP / I-NP / O per token: the same encoding, so the same sequence models (CRF, BiLSTM) learn it from data instead of hand-written rules."),
        NbFrame(listOf(NbBlock.Caption("more rules · VP and PP"), NbBlock.Text(listOf(NbSpan("[NP the small dog] ", NbTone.Blue), NbSpan("[VP chased] ", NbTone.Pink), NbSpan("[NP a cat] ", NbTone.Blue), NbSpan("[PP in] ", NbTone.Orange), NbSpan("[NP the garden]", NbTone.Blue)), mono = true)),
            listOf(nbLegend(NbInk.Blue, "NP"), nbLegend(NbInk.Pink, "VP"), nbLegend(NbInk.Orange, "PP")),
            "Add rules for verbs and prepositions: {a shallow parse}.",
            "Every word now sits in a phrase, but the phrases stay flat — nothing says the PP attaches to \"chased\" rather than \"a cat\"."),
        NbFrame(listOf(NbBlock.Kv(listOf("output" to "flat, non-overlapping spans", "cost" to "linear time, one pass", "loses" to "nesting and attachment", "good for" to "keyphrases, information extraction"))), emptyList(),
            "Chunking trades structure for {speed and robustness}.",
            "When you only need the noun phrases, a full parser is overkill. When you need who-did-what-to-whom, see dependency parsing."),
    )
}

// ── Dependency parsing (arc-standard oracle) ──

private val depWords = listOf("ROOT", "the", "small", "dog", "chased", "a", "cat")
private val depHead = mapOf(1 to 3, 2 to 3, 3 to 4, 4 to 0, 5 to 6, 6 to 4)
private val depLabel = mapOf(1 to "det", 2 to "amod", 3 to "nsubj", 4 to "root", 5 to "det", 6 to "obj")

private class DepState(val stack: List<Int>, val buffer: List<Int>, val arcs: List<Int>, val action: String)

private fun depOracle(): List<DepState> {
    val stack = mutableListOf(0)
    val buffer = (1..6).toMutableList()
    val arcs = mutableListOf<Int>()
    val out = mutableListOf<DepState>()
    fun attached(h: Int) = depHead.filterValues { it == h }.keys.all { it in arcs }
    while (buffer.isNotEmpty() || stack.size > 1) {
        val action: String
        if (stack.size >= 2 && stack[stack.size - 2] != 0 && depHead[stack[stack.size - 2]] == stack.last()) {
            val d = stack.removeAt(stack.size - 2); arcs += d; action = "LA"
        } else if (stack.size >= 2 && depHead[stack.last()] == stack[stack.size - 2] && attached(stack.last())) {
            val d = stack.removeAt(stack.size - 1); arcs += d; action = "RA"
        } else {
            stack += buffer.removeAt(0); action = "SH"
        }
        out += DepState(stack.toList(), buffer.toList(), arcs.toList(), action)
    }
    return out
}

private fun dependencyFrames(): List<NbFrame> {
    val states = depOracle()
    fun plan(cur: Int) = NbBlock.Toks(states.mapIndexed { i, s -> nbTok(s.action, if (i == cur) NbTone.Hot else if (s.action == "SH") NbTone.Plain else NbTone.Pick, "${i + 1}") }, columns = 6, mono = true)
    fun arcText(d: Int) = "${depWords[depHead.getValue(d)]} → ${depWords[d]} ${depLabel[d]}"
    fun arcGrid(built: List<Int>) = NbBlock.Toks(depHead.keys.sortedBy { listOf(1, 2, 3, 6, 5, 4).indexOf(it) }.map { d -> nbTok(arcText(d), if (d in built) NbTone.Good else NbTone.Dim) }, columns = 2, mono = true, start = true)
    fun sb(s: DepState?) = listOf(
        NbBlock.Toks((s?.stack ?: listOf(0)).mapIndexed { i, w -> nbTok(depWords[w], if (s != null && i == s.stack.lastIndex && w != 0) NbTone.Hot else NbTone.Plain) }, label = "stack"),
        NbBlock.Toks((s?.buffer ?: (1..6).toList()).map { nbTok(depWords[it], NbTone.Dim) }.ifEmpty { listOf(nbTok("empty", NbTone.Empty)) }, label = "buffer"),
    )
    val legend = listOf(nbLegend(NbInk.Yellow, "Current step"), nbLegend(NbInk.Violet, "Arc action (LA / RA)"), nbLegend(NbInk.Grey, "Shift"))
    val head = NbBlock.Caption("arc-standard transitions · gold tree oracle")
    val frames = mutableListOf<NbFrame>()
    frames += NbFrame(listOf(NbBlock.Caption("the goal · every word gets one head"), NbBlock.Toks((1..6).map { nbTok(depWords[it], NbTone.Plain, "← ${depWords[depHead.getValue(it)]}") }), NbBlock.Caption("arcs it will build"), arcGrid(emptyList())),
        listOf(nbLegend(NbInk.Green, "Built"), nbLegend(NbInk.Slate, "To build")),
        "A dependency parse gives every word {one head}.",
        "\"dog\" is the subject of \"chased\", \"cat\" its object, and the determiners hang off their nouns. Six words, six arcs, one of them from ROOT.")
    frames += NbFrame(listOf(head) + sb(null) + listOf(NbBlock.Caption("all ${states.size} transitions for this sentence"), plan(-1), NbBlock.Caption("arcs it will build"), arcGrid(emptyList())), legend,
        "Parse with {a stack and three moves}.",
        "SHIFT moves the next word onto the stack. LEFT-ARC makes the top word the head of the one under it; RIGHT-ARC the reverse. An oracle reads the moves off the gold tree: ${states.size} = 2 × 6.")
    states.forEachIndexed { i, s ->
        val name = when (s.action) { "SH" -> "SHIFT"; "LA" -> "LEFT-ARC"; else -> "RIGHT-ARC" }
        val newArc = if (s.action == "SH") null else s.arcs.last()
        val body = when (s.action) {
            "SH" -> "Push \"${depWords[s.stack.last()]}\" onto the stack; no arc yet. ${s.arcs.size} of 6 arcs built."
            else -> "${arcText(newArc!!)}: \"${depWords[newArc]}\" leaves the stack with its head found. ${s.arcs.size} of 6 arcs built."
        }
        frames += NbFrame(listOf(head) + sb(s) + listOf(NbBlock.Caption("all ${states.size} transitions for this sentence"), plan(i), NbBlock.Caption("arcs it will build"), arcGrid(s.arcs)), legend,
            "Transition ${i + 1} of ${states.size}: $name.", body)
    }
    frames += NbFrame(listOf(NbBlock.Caption("scored per word · a parser's output"), NbBlock.Table(listOf("word", "gold head", "predicted", ""), listOf(0.8f, 0.9f, 0.9f, 0.3f), (1..6).map { d ->
        val pred = if (d == 6) "dog obj" else "${depWords[depHead.getValue(d)]} ${depLabel[d]}"
        val ok = d != 6
        NbRow(listOf(nbName(depWords[d]), nbCell("${depWords[depHead.getValue(d)]} ${depLabel[d]}"), nbCell(pred, if (ok) null else NbInk.Red), nbCell(if (ok) "✓" else "✗", if (ok) NbInk.Green else NbInk.Red, true)))
    }), NbBlock.Tiles(listOf(NbTile("5 / 6", "UAS · right head", NbInk.Indigo), NbTile("5 / 6", "LAS · head and label", NbInk.Indigo)))), emptyList(),
        "Score: {right head per word}.",
        "Unlabelled attachment (UAS) checks the head; labelled (LAS) also the relation. Attaching \"cat\" to \"dog\" costs one word on both.")
    frames += NbFrame(listOf(NbBlock.Kv(listOf("time" to "linear — 2n transitions", "learned part" to "a classifier picks the move", "limit" to "projective trees only", "fix" to "swap transition or graph parsers"))), emptyList(),
        "Transition parsing is {linear time}.",
        "A trained classifier replaces the oracle and picks each move from the stack and buffer. Crossing arcs (common in Dutch, Czech) need an extra SWAP move.")
    return frames
}

// ── Constituency parsing ──

private fun constituencyFrames(): List<NbFrame> {
    val words = listOf("the" to "DT", "small" to "JJ", "dog" to "NN", "chased" to "VBD", "a" to "DT", "cat" to "NN")
    fun leaf(i: Int) = 0.08f + 0.84f * i / 5
    val leafY = 0.82f
    val gold = listOf(Triple("NP", 0, 3), Triple("VP", 3, 6), Triple("NP", 4, 6), Triple("S", 0, 6))
    fun tree(show: Set<Int>, hot: Int? = null): NbBlock.Tree {
        val nodes = mutableListOf<NbNode>()
        words.forEachIndexed { i, (w, t) -> nodes += NbNode(leaf(i), leafY, w, t) }
        val pos = mapOf(0 to (0.25f to 0.4f), 1 to (0.66f to 0.4f), 2 to (0.83f to 0.6f), 3 to (0.45f to 0.14f))
        val idx = mutableMapOf<Int, Int>()
        gold.forEachIndexed { k, (l, a, b) ->
            if (k in show) {
                idx[k] = nodes.size
                nodes += NbNode(pos.getValue(k).first, pos.getValue(k).second, l, "[$a,$b)", if (k == hot) NbTone.Hot else NbTone.Good)
            }
        }
        val edges = mutableListOf<NbEdge>()
        idx[0]?.let { n -> (0..2).forEach { edges += NbEdge(n, it) } }
        idx[2]?.let { n -> (4..5).forEach { edges += NbEdge(n, it) } }
        idx[1]?.let { n -> edges += NbEdge(n, 3); idx[2]?.let { edges += NbEdge(n, it, hot = true) } }
        idx[3]?.let { n -> idx[0]?.let { edges += NbEdge(n, it, hot = true) }; idx[1]?.let { edges += NbEdge(n, it, hot = true) } }
        return NbBlock.Tree(nodes, edges, 210)
    }
    val spanToks = NbBlock.Toks(gold.map { (l, a, b) -> nbTok("$l [$a,$b)", NbTone.Good) }, mono = true)
    val legend = listOf(nbLegend(NbInk.Green, "Constituent (scored)"), nbLegend(NbInk.Blue, "Phrase-to-phrase edge", SwatchStyle.Line), nbLegend(NbInk.Slate, "POS node (not scored)"))
    val head = NbBlock.Caption("constituency tree · spans are [start, end)")
    val pred = listOf(Triple("NP", 0, 3), Triple("VP", 3, 6), Triple("NP", 4, 6), Triple("S", 0, 6), Triple("NP", 3, 6))
    val correct = pred.count { it in gold }
    val p = correct.toDouble() / pred.size
    val r = correct.toDouble() / gold.size
    val f1 = 2 * p * r / (p + r)
    return listOf(
        NbFrame(listOf(head, tree(emptySet())), legend.drop(2),
            "Start from the words and {their tags}.",
            "A constituency parse groups words into nested phrases. The POS tags are the bottom layer every phrase is built on."),
        NbFrame(listOf(head, tree(setOf(0, 1, 2, 3)), NbBlock.Caption("what the parser is scored on"), spanToks), legend,
            "The units it is scored on are labelled spans.",
            "POS nodes are excluded by convention, since tagger accuracy would otherwise inflate the parser's score."),
        NbFrame(listOf(head, tree(setOf(0, 2), hot = 2)), legend,
            "Bottom-up: two noun phrases, {[0,3) and [4,6)}.",
            "Each NP covers a contiguous span. Unlike chunks, they will be nested inside larger phrases."),
        NbFrame(listOf(head, tree(setOf(0, 1, 2), hot = 1)), legend,
            "VP [3,6) {contains} NP [4,6).",
            "The verb and its object form a verb phrase — the nesting a chunker can't express."),
        NbFrame(listOf(NbBlock.Caption("a parser's output vs gold · labelled brackets"), NbBlock.Table(listOf("span", "in gold?"), listOf(1f, 0.5f), pred.map { (l, a, b) ->
            val ok = Triple(l, a, b) in gold
            NbRow(listOf(nbName("$l [$a,$b)"), nbCell(if (ok) "✓" else "✗", if (ok) NbInk.Green else NbInk.Red, true)))
        }), NbBlock.Tiles(listOf(NbTile(f2(p), "precision", NbInk.Indigo), NbTile(f2(r), "recall", NbInk.Indigo), NbTile(f2(f1), "F1", NbInk.Green)))), emptyList(),
            "PARSEVAL: {precision and recall over spans}.",
            "An extra wrong bracket NP [3,6) costs precision but not recall: $correct of ${pred.size} predicted spans are in gold, all ${gold.size} gold spans are found."),
        NbFrame(listOf(NbBlock.Kv(listOf("CKY chart" to "every span, every split: O(n³)", "n = 6" to "${6 * 6 * 6} span-split checks", "n = 40" to "${40 * 40 * 40} checks", "neural parsers" to "score spans, same chart"))), emptyList(),
            "Exact parsing is {cubic} in sentence length.",
            "The CKY chart considers every span and every split point. Modern neural parsers keep the chart and learn the span scores."),
    )
}

// ── Coreference ──

private class Mention(val text: String, val number: String, val gender: String, val animate: Boolean)

private fun corefFrames(): List<NbFrame> {
    val doc = listOf(
        NbSpan("Ada Lovelace", NbTone.Pick), NbSpan(" wrote to "), NbSpan("Charles Babbage", NbTone.Plain), NbSpan(" from "), NbSpan("London", NbTone.Plain), NbSpan(". "),
        NbSpan("She", NbTone.Hot), NbSpan(" admired "), NbSpan("his", NbTone.Hot), NbSpan(" engine."),
    )
    val cands = listOf(Mention("Ada Lovelace", "sg", "fem", true), Mention("Charles Babbage", "sg", "masc", true), Mention("London", "sg", "—", false))
    fun table(gender: String): NbBlock.Table = NbBlock.Table(listOf("mention", "number", "gender", "animate"), listOf(1.3f, 0.6f, 0.7f, 0.7f), cands.map { m ->
        val gOk = m.gender == gender
        val ok = gOk && m.animate
        NbRow(listOf(nbName(m.text), nbCell("sg ✓", NbInk.Green), nbCell(m.gender + if (gOk) " ✓" else " ✗", if (gOk) NbInk.Green else NbInk.Red), nbCell(if (m.animate) "yes ✓" else "no ✗", if (m.animate) NbInk.Green else NbInk.Red)), ring = ok, dim = !ok)
    })
    val legend = listOf(nbLegend(NbInk.Yellow, "Pronoun"), nbLegend(NbInk.Violet, "Antecedent"), nbLegend(NbInk.Slate, "Filtered out"))
    return listOf(
        NbFrame(listOf(NbBlock.Caption("which mentions refer to the same entity?"), NbBlock.Text(doc.map { if (it.tone == NbTone.Pick) NbSpan(it.t, NbTone.Plain) else it })), listOf(nbLegend(NbInk.Yellow, "Pronoun")),
            "Coreference links mentions {to the same entity}.",
            "\"She\" and \"his\" point back to people named earlier. A reader resolves them instantly; a program has to choose among every earlier mention."),
        NbFrame(listOf(NbBlock.Caption("candidates for \"She\" · agreement check"), NbBlock.Text(doc), table("fem"),
            NbBlock.Tiles(listOf(NbTile("1 of 3", "candidates survive", NbInk.Indigo), NbTile("0", "learned parameters")))), legend,
            "Agreement filters candidates cheaply.",
            "A singular feminine animate pronoun can only refer to Ada Lovelace here — and \"his\" only to Babbage — with no learning at all."),
        NbFrame(listOf(NbBlock.Caption("candidates for \"his\" · agreement check"), table("masc")), legend,
            "\"his\" → {Charles Babbage}.",
            "Masculine and animate: one candidate left again. Rules like these were the core of early systems (Hobbs, 1978)."),
        NbFrame(listOf(NbBlock.Caption("resolved · two entity clusters"), NbBlock.Table(listOf("entity", "mentions"), listOf(0.8f, 1.4f), listOf(
            NbRow(listOf(nbName("Ada Lovelace", NbInk.Indigo), nbCell("Ada Lovelace · She"))),
            NbRow(listOf(nbName("Charles Babbage", NbInk.Pink), nbCell("Charles Babbage · his"))),
            NbRow(listOf(nbName("London", NbInk.Grey), nbCell("London"))),
        ), listOf(0, 0))), emptyList(),
            "The output is {clusters} of mentions.",
            "Each cluster is one entity. Downstream, \"She admired his engine\" can be read as \"Lovelace admired Babbage's engine\"."),
        NbFrame(listOf(NbBlock.Caption("when agreement can't decide"), NbBlock.Text(listOf(NbSpan("The trophy", NbTone.Pick), NbSpan(" didn't fit in "), NbSpan("the suitcase", NbTone.Pick), NbSpan(" because "), NbSpan("it", NbTone.Hot), NbSpan(" was too big."))),
            NbBlock.Callout(listOf("both singular, neuter, inanimate → agreement keeps {2 of 2}", "\"too big\" → trophy · \"too small\" → suitcase"))), legend,
            "Some pronouns need {world knowledge}.",
            "Winograd schemas swap one word and flip the answer. Only knowing how trophies and suitcases work resolves \"it\"."),
        NbFrame(listOf(NbBlock.Kv(listOf("rules" to "agreement, syntax, recency", "mention-ranking" to "score each antecedent", "span-based (2017+)" to "find mentions and links jointly", "LLMs" to "resolve in context, implicitly"))), emptyList(),
            "Filters first, {learned scores} for the rest.",
            "Neural systems still prune with agreement-like features, then rank the surviving antecedents with a model trained on annotated clusters."),
    )
}

// ── Lexicon sentiment ──

private fun sentimentFrames(): List<NbFrame> {
    val words = "the plot was not good but the acting was very good".split(" ")
    val lexicon = mapOf("good" to 2.0)
    val butAt = words.indexOf("but")
    // VADER-style rules: negation in the 3 words before ×−0.75, "very" ×1.5, clause before "but" ×0.5 and after ×1.5.
    fun score(i: Int): Pair<Double, String> {
        var s = lexicon[words[i]] ?: return 0.0 to ""
        val notes = mutableListOf("+${nbF(s, 0)}")
        if ((max(0, i - 3) until i).any { words[it] == "not" }) { s *= -0.75; notes += "not ×−0.75" }
        if (i > 0 && words[i - 1] == "very") { s *= 1.5; notes += "very ×1.5" }
        if (i < butAt) { s *= 0.5; notes += "before but ×0.5" } else if (i > butAt) { s *= 1.5; notes += "after but ×1.5" }
        return s to notes.joinToString(" · ")
    }
    val scored = words.indices.filter { words[it] in lexicon }
    val raw = scored.sumOf { lexicon.getValue(words[it]) }
    val ruled = scored.sumOf { score(it).first }
    fun toks(rules: Boolean) = NbBlock.Toks(words.mapIndexed { i, w ->
        nbTok(w, when {
            w in lexicon -> NbTone.Pick
            rules && w == "not" -> NbTone.Hot
            rules && (w == "very" || w == "but") -> NbTone.Blue
            else -> NbTone.Dim
        })
    })
    fun table(rules: Boolean) = NbBlock.Table(listOf("word", "lex", "rules applied", "score"), listOf(0.7f, 0.3f, 1.4f, 0.5f), scored.mapIndexed { k, i ->
        val (s, n) = score(i)
        NbRow(listOf(nbName("good #${k + 1}"), nbCell("+2"), nbCell(if (rules) n.substringAfter(" · ", "—") else "—"), nbCell(if (rules) (if (s > 0) "+" else "") + f2(s) else "+2.00", if (rules) (if (s < 0) NbInk.Red else NbInk.Green) else null)))
    })
    val tiles = NbBlock.Tiles(listOf(NbTile("+" + nbF(raw, 1), "raw lexicon sum"), NbTile((if (ruled >= 0) "+" else "") + f2(ruled), "with the three rules", NbInk.Green)))
    val head = NbBlock.Caption("\"${words.joinToString(" ")}\"")
    val legend = listOf(nbLegend(NbInk.Yellow, "Negator"), nbLegend(NbInk.Blue, "Modifier / contrast"), nbLegend(NbInk.Violet, "Scored word"))
    val (s1, _) = score(scored[0])
    return listOf(
        NbFrame(listOf(head, toks(false), NbBlock.Caption("lexicon: every word has a fixed score"), NbBlock.Table(listOf("word", "score"), listOf(1f, 0.5f), listOf(
            NbRow(listOf(nbName("good"), nbCell("+2.0", NbInk.Green))), NbRow(listOf(nbName("great"), nbCell("+3.1", NbInk.Green))), NbRow(listOf(nbName("bad"), nbCell("−2.5", NbInk.Red))), NbRow(listOf(nbName("plot, acting, the…"), nbCell("0"))),
        ))), listOf(nbLegend(NbInk.Violet, "In the lexicon")),
            "A lexicon gives each word {a polarity score}.",
            "Hand-built lists (VADER, AFINN) rate thousands of words. Most words, including \"plot\" and \"acting\", score zero. (Scores illustrative.)"),
        NbFrame(listOf(head, toks(false), table(false), tiles), legend,
            "Summing the lexicon says {+${nbF(raw, 1)}} — clearly positive.",
            "Two \"good\"s, nothing negative. But the reviewer said the plot was not good."),
        NbFrame(listOf(head, toks(true), table(true), tiles), legend,
            "Every lexicon system grows the same three rules.",
            "Negation flips and damps nearby scores, intensifiers scale them, and the clause after \"but\" outweighs the one before."),
        NbFrame(listOf(head, toks(true), NbBlock.Callout(listOf("good #1 = +2 × (−0.75) × 0.5 = {${f2(s1)}}"))), legend,
            "\"not good\" becomes {${f2(s1)}}, not −2.",
            "Negation is damped, not mirrored: \"not good\" is milder than \"bad\". VADER looks three words back for a negator."),
        NbFrame(listOf(head, toks(true), table(true), NbBlock.Callout(listOf("before \"but\" ×0.5 · after \"but\" ×1.5"))), legend,
            "After \"but\", {the writer's real view}.",
            "Contrast shifts weight to the second clause. The final +${f2(ruled)} is positive but well below the naive +${nbF(raw, 1)}."),
        NbFrame(listOf(NbBlock.Caption("where rules break"), NbBlock.Table(listOf("text", "lexicon", "truth"), listOf(1.6f, 0.5f, 0.5f), listOf(
            NbRow(listOf(NbCell("great, another delay", mono = false), nbCell("+", NbInk.Green), nbCell("−", NbInk.Red))),
            NbRow(listOf(NbCell("not bad at all", mono = false), nbCell("+ / −"), nbCell("+", NbInk.Green))),
            NbRow(listOf(NbCell("the battery died fast", mono = false), nbCell("0"), nbCell("−", NbInk.Red))),
        ))), emptyList(),
            "Sarcasm and domain words {defeat the lexicon}.",
            "\"fast\" is good for a car and bad for a battery; \"great\" is sarcastic here. Lexicons are transparent and need no training data — and that is all."),
    )
}

// ── LLMs: the next-token distribution ──

private val llmContext = listOf("The", "capital", "of", "France", "is")
private val llmCands = listOf("Paris", "the", "a", "located", "home", "Lyon", "known", "not")
/** Illustrative logits for the top candidates; the rest of the 50,257-entry vocabulary shares the remainder. */
private val llmLogits = listOf(9.0, 5.1, 4.4, 4.0, 3.4, 3.3, 2.7, 2.6)
private const val LLM_VOCAB = 50257
private const val LLM_TAIL_LOGIT = -2.1

private fun llmSoftmax(t: Double): Pair<List<Double>, Double> {
    val m = llmLogits.max()
    val top = llmLogits.map { exp((it - m) / t) }
    val tail = (LLM_VOCAB - llmLogits.size) * exp((LLM_TAIL_LOGIT - m) / t)
    val z = top.sum() + tail
    return top.map { it / z } to tail / z
}

private fun llmFrames(): List<NbFrame> {
    fun bars(p: List<Double>, pick: Int = 0) = NbBlock.Bars(llmCands.indices.map { NbBar(llmCands[it], nbF(p[it] * 100, 1) + "%", p[it] / p.max(), if (it == pick) NbInk.Violet else NbInk.Blue) }, labelWidth = 72)
    val (p1, tail1) = llmSoftmax(1.0)
    val (p05, _) = llmSoftmax(0.5)
    val (p2, tail2) = llmSoftmax(2.0)
    val ctx = NbBlock.Toks(llmContext.map { nbTok(it) } + nbTok("?", NbTone.Hot))
    val legend = listOf(nbLegend(NbInk.Blue, "Next-token probability"), nbLegend(NbInk.Violet, "Most likely"))
    // Nucleus at p = 0.5 is the top token alone; at p = 0.9 it has to reach into the tail.
    var cum = 0.0
    val nucleus = llmCands.indices.takeWhile { val keep = cum < 0.5; cum += p1[it]; keep }
    val tailEach = tail1 / (LLM_VOCAB - llmCands.size)
    val need90 = llmCands.size + kotlin.math.ceil((0.9 - p1.sum()) / tailEach).toLong()
    return listOf(
        NbFrame(listOf(NbBlock.Caption("context → one score per vocabulary entry"), ctx, NbBlock.Caption("top 8 of ${nbGrouped(LLM_VOCAB.toLong())} · softmax of illustrative logits"), bars(p1),
            NbBlock.Tiles(listOf(NbTile(nbF(p1[0] * 100, 0) + "%", "on \"Paris\"", NbInk.Indigo), NbTile(nbF((1 - p1[0]) * 100, 0) + "%", "spread over the other ${nbGrouped((LLM_VOCAB - 1).toLong())}")))), legend,
            "An LLM does exactly one thing.",
            "Given the tokens so far, score every token in the vocabulary as a candidate for the next one. Everything else is how you pick from this list."),
        NbFrame(listOf(NbBlock.Caption("temperature T divides the logits before softmax"), NbBlock.Table(listOf("T", "P(Paris)", "tail mass"), listOf(0.5f, 0.7f, 0.7f), listOf(
            NbRow(listOf(nbName("0.5"), nbCell(nbF(p05[0] * 100, 1) + "%", NbInk.Indigo), nbCell("≈0%"))),
            NbRow(listOf(nbName("1.0"), nbCell(nbF(p1[0] * 100, 1) + "%", NbInk.Indigo), nbCell(nbF(tail1 * 100, 1) + "%"))),
            NbRow(listOf(nbName("2.0"), nbCell(nbF(p2[0] * 100, 1) + "%", NbInk.Indigo), nbCell(nbF(tail2 * 100, 1) + "%", NbInk.Red))),
        )), bars(p2)), legend,
            "Temperature {sharpens or flattens} the list.",
            "At T = 0.5 \"Paris\" takes ${nbF(p05[0] * 100, 0)}%; at T = 2 the long tail of unlikely tokens grows to ${nbF(tail2 * 100, 0)}% and the output gets creative — or wrong."),
        NbFrame(listOf(NbBlock.Caption("top-p (nucleus) sampling · p = 0.5"), NbBlock.Bars(llmCands.indices.map { NbBar(llmCands[it], nbF(p1[it] * 100, 1) + "%", p1[it] / p1.max(), if (it in nucleus) NbInk.Violet else NbInk.Slate) }, labelWidth = 72)),
            listOf(nbLegend(NbInk.Violet, "Kept"), nbLegend(NbInk.Slate, "Cut")),
            "Top-p keeps the {smallest set} covering p.",
            "At p = 0.5 that is ${nucleus.size} token${if (nucleus.size == 1) "" else "s"}. At p = 0.9 it must reach ${nbGrouped(need90)} tokens deep, because the remaining mass is spread thinly over the tail — the set adapts to the model's confidence, unlike a fixed top-k.",
            chips = listOf(nbChip("p = 0.5", "${nucleus.size}", true), nbChip("p = 0.9", nbGrouped(need90)))),
        NbFrame(listOf(NbBlock.Caption("generation is the same step in a loop"), NbBlock.Table(listOf("step", "context ends …", "picked"), listOf(0.4f, 1.4f, 0.6f), listOf(
            NbRow(listOf(nbCell("1"), nbCell("… France is"), nbCell("Paris", NbInk.Indigo, true))),
            NbRow(listOf(nbCell("2"), nbCell("… is Paris"), nbCell(".", NbInk.Indigo, true))),
            NbRow(listOf(nbCell("3"), nbCell("… Paris ."), nbCell("It", NbInk.Indigo, true))),
        ), listOf(0, 0, 2))), emptyList(),
            "Append the pick, {score again}.",
            "Each new token joins the context and the whole distribution is recomputed. A paragraph is a few hundred of these steps."),
        NbFrame(listOf(NbBlock.Kv(listOf("training" to "make the true next token likely", "data" to "trillions of tokens of text", "loss" to "−log P(true next token)", "everything else" to "instruction tuning, RLHF, sampling"))), emptyList(),
            "Training only ever {raises the right token's score}.",
            "The model sees text and is penalised by −log P of each actual next token. Knowledge, grammar and reasoning are whatever helps that prediction."),
    )
}

// ── BART ──

private val bartDoc = listOf("the", "cat", "sat", "on", "the", "mat", ".", "it", "purred", "loudly", ".")

private fun bartFrames(): List<NbFrame> {
    val models = listOf(
        NbModel("BERT-large", "fill blanks", true, false, 340.0 / 774, "340M"),
        NbModel("GPT-2 large", "continue", false, true, 774.0 / 774, "774M"),
        NbModel("BART-large", "both", true, true, 400.0 / 774, "400M", ring = true),
    )
    val overhead = 400.0 / 340 - 1
    val doc = NbBlock.Toks(bartDoc.map { nbTok(it) })
    val legend = listOf(nbLegend(NbInk.Blue, "Encoder"), nbLegend(NbInk.Violet, "Decoder"))
    val corruptLegend = listOf(nbLegend(NbInk.Yellow, "Corrupted"), nbLegend(NbInk.Red, "Deleted"))
    fun corrupted(caption: String, toks: List<NbTok>, headline: String, body: String) = NbFrame(
        listOf(NbBlock.Caption("the original document · ${bartDoc.size} tokens"), doc, NbBlock.Caption(caption), NbBlock.Toks(toks)),
        corruptLegend, headline, body,
    )
    val sentences = listOf(bartDoc.subList(0, 7), bartDoc.subList(7, 11))
    return listOf(
        NbFrame(listOf(NbBlock.Caption("the original document · ${bartDoc.size} tokens"), doc, NbBlock.Caption("three shapes of pretrained transformer"), NbBlock.Models(models)), legend,
            "BART: corrupt the text, reconstruct the original.",
            "An encoder and a decoder, so one model fine-tunes for both classification and generation.",
            chips = listOf(nbChip("over BERT-large", "+" + nbF(overhead * 100, 0) + "%", true))),
        corrupted("1 · token masking", bartDoc.mapIndexed { i, w -> if (i == 1 || i == 8) nbTok("[MASK]", NbTone.Hot) else nbTok(w) },
            "Token masking — {BERT's} corruption.",
            "Random tokens become [MASK]. The decoder must regenerate the whole document, not just the masked slots."),
        corrupted("2 · token deletion", bartDoc.filterIndexed { i, _ -> i != 2 && i != 9 }.map { nbTok(it) },
            "Token deletion: {where} is the gap?",
            "No [MASK] marks the spot. The model must work out which positions are missing as well as what goes there."),
        corrupted("3 · text infilling · span length ~ Poisson(3)", listOf(nbTok("the"), nbTok("[MASK]", NbTone.Hot)) + bartDoc.drop(4).map { nbTok(it) },
            "Text infilling: {three tokens}, one mask.",
            "\"cat sat on\" is replaced by a single [MASK], so the model also predicts how many tokens are missing. This was BART's best single objective."),
        corrupted("4 · sentence permutation", (sentences[1] + sentences[0]).map { nbTok(it, NbTone.Plain) },
            "Sentence permutation: {restore the order}.",
            "The two sentences are shuffled. On its own this objective helped little; combined with infilling it was BART's final recipe."),
        corrupted("5 · document rotation", (bartDoc.drop(3) + bartDoc.take(3)).mapIndexed { i, w -> nbTok(w, if (i == 0) NbTone.Hot else NbTone.Plain) },
            "Document rotation: {find the start}.",
            "The document is rotated to begin at a random token (\"on\"). The model must identify where the real document begins."),
        NbFrame(listOf(NbBlock.Caption("encoder reads the corruption · decoder writes the original"), NbBlock.Toks(listOf(nbTok("the"), nbTok("[MASK]", NbTone.Hot)) + bartDoc.drop(4).map { nbTok(it, NbTone.Blue) }, label = "enc"),
            NbBlock.Toks(bartDoc.map { nbTok(it, NbTone.Pick) }, label = "dec")), legend,
            "The loss is over {every output token}.",
            "The encoder sees the corrupted text bidirectionally; the decoder reproduces the full original left to right, attending to the encoder. Cross-entropy on all ${bartDoc.size} tokens."),
        NbFrame(listOf(NbBlock.Kv(listOf("summarisation" to "CNN/DM ROUGE-L 40.9 (state of the art, 2019)", "classification" to "matches RoBERTa on GLUE", "translation" to "encoder adapts a new source language", "shape" to "encoder-decoder, like T5"))), emptyList(),
            "Denoising pretraining fits {generation tasks} best.",
            "Because the decoder is trained to write whole documents, BART was strongest on summarisation while staying competitive on understanding tasks."),
    )
}

// ── XLNet ──

private fun xlnetFrames(): List<NbFrame> {
    val sent = listOf("New", "York", "is", "a", "city")
    val masked = NbBlock.Toks(listOf(nbTok("[MASK]", NbTone.Hot), nbTok("[MASK]", NbTone.Hot)) + sent.drop(2).map { nbTok(it) })
    val fine = NbBlock.Toks(sent.map { nbTok(it) })
    val legend = listOf(nbLegend(NbInk.Yellow, "[MASK] token"))
    val order = listOf(3, 2, 5, 1, 4)
    fun orderToks(upTo: Int) = NbBlock.Toks(order.mapIndexed { k, p -> nbTok(sent[p - 1], if (k < upTo) NbTone.Pick else if (k == upTo) NbTone.Hot else NbTone.Dim, "#${k + 1}") })
    val york = order.indexOf(2)
    val newIdx = order.indexOf(1)
    return listOf(
        NbFrame(listOf(NbBlock.Caption("the same sentence, two moments"), NbBlock.Caption("pretraining input · BERT"), masked, NbBlock.Caption("fine-tuning input"), fine,
            NbBlock.Tiles(listOf(NbTile("15%", "positions masked in pretraining", NbInk.Yellow), NbTile("0%", "at fine-tuning time"))),
            NbBlock.Caption("and the masked pair is predicted independently"),
            NbBlock.Table(emptyList(), listOf(1.2f, 0.7f), listOf(NbRow(listOf(nbName("P(New | is a city)"), nbCell("guessed alone"))), NbRow(listOf(nbName("P(York | is a city)"), nbCell("guessed alone")))))), legend,
            "XLNet starts from two complaints about BERT.",
            "[MASK] appears in pretraining and never downstream, and two masked words can't inform each other — \"York\" never conditions on \"New\"."),
        NbFrame(listOf(NbBlock.Caption("one sampled factorisation order"), NbBlock.Toks(order.mapIndexed { k, p -> nbTok(sent[p - 1], NbTone.Plain, "#${k + 1}") })),
            listOf(nbLegend(NbInk.Violet, "Already predicted"), nbLegend(NbInk.Yellow, "Being predicted")),
            "Predict the words in {a random order}.",
            "Positions stay where they are; only the order of prediction is shuffled. This order is is → York → city → New → a."),
        NbFrame(listOf(NbBlock.Caption("predicting \"York\" (#${york + 1} in this order)"), orderToks(york)), listOf(nbLegend(NbInk.Violet, "Visible context"), nbLegend(NbInk.Yellow, "Being predicted")),
            "\"York\" sees only {what came earlier in the order}.",
            "Here that is just \"is\". In another order \"New\" comes first and \"York\" conditions on it — something BERT's independent guesses never do."),
        NbFrame(listOf(NbBlock.Caption("predicting \"New\" (#${newIdx + 1} in this order)"), orderToks(newIdx)), listOf(nbLegend(NbInk.Violet, "Visible context"), nbLegend(NbInk.Yellow, "Being predicted")),
            "\"New\" sees {both sides} — York and city.",
            "Over many sampled orders every word sees every subset of the others, so the model learns bidirectional context with no [MASK] token at all.",
            chips = listOf(nbChip("orders of 5 words", "${(1..5).fold(1) { a, b -> a * b }}", true))),
        NbFrame(listOf(NbBlock.Caption("two-stream attention"), NbBlock.Kv(listOf("content stream" to "knows the word and its position", "query stream" to "knows the position only", "why" to "predicting a word must not see it", "result" to "same weights, two views"))), emptyList(),
            "The target position must {know where, not what}.",
            "A standard transformer state includes the token itself. XLNet adds a query stream that carries position but hides content, so the word can't predict itself."),
        NbFrame(listOf(NbBlock.Table(listOf("", "BERT", "XLNet"), listOf(0.9f, 1f, 1f), listOf(
            NbRow(listOf(nbName("[MASK] mismatch"), nbCell("yes", NbInk.Red), nbCell("no", NbInk.Green))),
            NbRow(listOf(nbName("masked words"), nbCell("independent", NbInk.Red), nbCell("conditioned", NbInk.Green))),
            NbRow(listOf(nbName("training cost"), nbCell("lower", NbInk.Green), nbCell("higher", NbInk.Red))),
        ))), emptyList(),
            "Better on paper, {costlier in practice}.",
            "XLNet beat BERT on 20 tasks in 2019, but RoBERTa matched it by simply training BERT longer on more data — and that simpler recipe won out."),
    )
}

// ── GPT-3 and GPT-4 ──

private class LmScale(val name: String, val params: Double, val tokens: Double)

private fun gptFrames(): List<NbFrame> {
    val models = listOf(
        LmScale("GPT-3", 175.0, 300.0), LmScale("Chinchilla", 70.0, 1400.0), LmScale("LLaMA-1", 65.0, 1400.0),
        LmScale("LLaMA-2", 70.0, 2000.0), LmScale("LLaMA-3", 70.0, 15000.0),
    )
    val logMax = log10(15000.0)
    fun table(ring: String?) = NbBlock.Table(listOf("", "params (B)", "tokens (B)", "tok/p"), listOf(0.9f, 0.9f, 0.9f, 0.4f), models.map { m ->
        NbRow(listOf(nbName(m.name), NbCell(nbGrouped(m.params.toLong()), sub = "", bar = null), NbCell(nbGrouped(m.tokens.toLong())), nbCell(nbF(m.tokens / m.params, if (m.tokens / m.params < 10) 1 else 0), if (m.name == ring) NbInk.Yellow else null, true)), ring = m.name == ring)
    })
    fun bars(ring: String?) = NbBlock.Bars(models.flatMap { m ->
        listOf(
            NbBar(m.name, nbGrouped(m.params.toLong()) + "B p", log10(m.params) / logMax, NbInk.Blue, if (m.name == ring) NbInk.Yellow else null),
            NbBar("", nbGrouped(m.tokens.toLong()) + "B t", log10(m.tokens) / logMax, NbInk.Violet),
        )
    }, labelWidth = 88)
    val legend = listOf(nbLegend(NbInk.Blue, "Parameters"), nbLegend(NbInk.Violet, "Training tokens"))
    val g = models[0]
    val flops = models.map { 6 * it.params * 1e9 * it.tokens * 1e9 }
    return listOf(
        NbFrame(listOf(NbBlock.Caption("parameters vs training tokens · log scale"), bars("GPT-3")), legend,
            "GPT-3 changed scale, not shape.",
            "The same decoder-only design as GPT-2 at 175B parameters — and it did tasks from a few in-prompt examples, no gradient update. Later models trained far longer per parameter.",
            fx = listOf("GPT-3: 175B params · 300B tokens · {${nbF(g.tokens / g.params, 1)}} tokens/param")),
        NbFrame(listOf(NbBlock.Caption("tokens per parameter"), table("Chinchilla"), NbBlock.Callout(listOf("compute-optimal ≈ {20} tokens per parameter"))), legend,
            "Chinchilla: GPT-3 was {undertrained}.",
            "For a fixed compute budget, a 70B model on 1.4T tokens beat much larger ones. The ratio jumped from 1.7 to 20."),
        NbFrame(listOf(NbBlock.Caption("training compute ≈ 6 · N · D"), NbBlock.Table(listOf("", "FLOPs"), listOf(1f, 1f), models.indices.map { NbRow(listOf(nbName(models[it].name), nbCell(lsSci(flops[it]), if (it == 0) NbInk.Yellow else null))) })), emptyList(),
            "Compute is {6 × parameters × tokens}.",
            "Each token costs about 6 FLOPs per parameter (forward and backward). LLaMA-3 70B used ${nbF(flops[4] / flops[0], 0)}× GPT-3's compute — spent on data, not size."),
        NbFrame(listOf(NbBlock.Caption("in-context learning · GPT-3 175B, TriviaQA"), NbBlock.Bars(listOf(
            NbBar("zero-shot", "64.3", 0.643, NbInk.Indigo), NbBar("one-shot", "68.0", 0.680, NbInk.Indigo), NbBar("few-shot", "71.2", 0.712, NbInk.Violet),
        ), labelWidth = 84)), emptyList(),
            "Examples in the prompt, {no fine-tuning}.",
            "GPT-3's headline result: accuracy rises with the number of demonstrations in the prompt, with the weights frozen (Brown et al., 2020)."),
        NbFrame(listOf(NbBlock.Kv(listOf("GPT-4 (2023)" to "size and data undisclosed", "inputs" to "text and images", "alignment" to "RLHF on top of pretraining", "exams" to "e.g. bar exam ~90th percentile"))), emptyList(),
            "GPT-4: {details withheld}, capability measured.",
            "OpenAI published benchmark results but not parameter or token counts. The scaling story is now told through evaluations rather than architecture."),
    )
}

