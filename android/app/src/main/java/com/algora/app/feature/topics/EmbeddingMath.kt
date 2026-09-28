package com.algora.app.feature.topics

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

// ── D3 · Word embedding math ─────────────────────────────────────────────────
// The word2vec, GloVe, FastText and ELMo labs. Everything here trains for real on a toy corpus —
// there are no hand-set vectors in this file, which is the difference between it and the older
// `word_embeddings` umbrella lab. Deterministic LCG seeds, so every claim in the copy is the number
// this code produces on every run, and `D3MathTest` pins the ones the topics rest on.

private class Rng(private var state: Int) {
    fun next(): Float {
        state = state * 1103515245 + 12345
        return ((state ushr 16) and 0x7fff) / 32767f
    }

    fun uniform(scale: Float): Float = (next() - 0.5f) * scale
    fun int(bound: Int): Int = ((next() * bound).toInt()).coerceIn(0, bound - 1)
}

private fun sigmoid(x: Float): Float = 1f / (1f + exp(-x))

internal fun cosineOf(a: FloatArray, b: FloatArray): Float {
    var dot = 0f
    var na = 0f
    var nb = 0f
    for (i in a.indices) {
        dot += a[i] * b[i]
        na += a[i] * a[i]
        nb += b[i] * b[i]
    }
    return if (na == 0f || nb == 0f) 0f else dot / (sqrt(na) * sqrt(nb))
}

// ── word2vec: CBOW and skip-gram, both with negative sampling ────────────────

internal object Word2VecLab {

    /**
     * A corpus with deliberate parallel structure: king/queen and man/woman appear in the same
     * frames, so distributional similarity has something to find. "monarch" appears twice against
     * king's fourteen — it is the rare word the two objectives are compared on.
     */
    val corpus = listOf(
        "the king rules the kingdom", "the queen rules the kingdom",
        "the king wears a crown", "the queen wears a crown",
        "the king sits on the throne", "the queen sits on the throne",
        "the king is a man", "the queen is a woman",
        "a strong king leads the army", "a strong queen leads the army",
        "the man walks the dog", "the woman walks the dog",
        "the man drinks the water", "the woman drinks the water",
        "the man reads a book", "the woman reads a book",
        "a young man works", "a young woman works",
        "the monarch rules the kingdom", "the monarch wears a crown",
    )

    val window = 2
    val dim = 16
    val negatives = 5
    val epochs = 300
    val learningRate = 0.05f

    val sentences: List<List<String>> get() = corpus.map { it.split(" ") }
    val vocab: List<String> by lazy { sentences.flatten().distinct().sorted() }
    private val index: Map<String, Int> by lazy { vocab.withIndex().associate { (i, w) -> w to i } }

    val counts: Map<String, Int> by lazy { sentences.flatten().groupingBy { it }.eachCount() }

    /** Every (centre, context) pair the window produces — the training set for both objectives. */
    val pairs: List<Pair<Int, Int>> by lazy {
        sentences.flatMap { tokens ->
            tokens.indices.flatMap { i ->
                (maxOf(0, i - window)..min(tokens.lastIndex, i + window))
                    .filter { it != i }
                    .map { index.getValue(tokens[i]) to index.getValue(tokens[it]) }
            }
        }
    }

    /**
     * The noise distribution: unigram counts raised to 3/4, which is the paper's one empirical
     * tweak — it lifts rare words and flattens "the" relative to plain unigram sampling.
     */
    val noiseTable: List<Int> by lazy {
        val weights = vocab.map { (counts.getValue(it).toDouble()).pow(0.75) }
        val total = weights.sum()
        val table = mutableListOf<Int>()
        weights.forEachIndexed { i, w -> repeat((w / total * 1000).toInt().coerceAtLeast(1)) { table += i } }
        table
    }

    fun noiseShare(word: String): Double {
        val i = index.getValue(word)
        return noiseTable.count { it == i }.toDouble() / noiseTable.size
    }

    fun unigramShare(word: String): Double =
        counts.getValue(word).toDouble() / counts.values.sum()

    class Model(val input: Array<FloatArray>, val output: Array<FloatArray>, val losses: List<Float>) {
        fun vector(word: String, lab: Word2VecLab = Word2VecLab): FloatArray = input[lab.indexOf(word)]
    }

    fun indexOf(word: String): Int = index.getValue(word)

    /** Skip-gram with negative sampling: one update per (centre, context) pair. */
    fun trainSkipGram(seed: Int = 7): Model = train(seed, cbow = false)

    /** CBOW: the context vectors are averaged, and one update is shared between them. */
    fun trainCbow(seed: Int = 7): Model = train(seed, cbow = true)

    // Training runs during composition when a lab is opened, and three labs plus two tests want the
    // same two models — so they are trained once per process and shared.
    val skipGram: Model by lazy { trainSkipGram() }
    val cbow: Model by lazy { trainCbow() }

    private fun train(seed: Int, cbow: Boolean): Model {
        val rng = Rng(seed)
        val input = Array(vocab.size) { FloatArray(dim) { rng.uniform(1f / dim) } }
        val output = Array(vocab.size) { FloatArray(dim) }
        val losses = mutableListOf<Float>()

        repeat(epochs) { epoch ->
            var loss = 0f
            var steps = 0
            for (tokens in sentences) {
                for (i in tokens.indices) {
                    val centre = index.getValue(tokens[i])
                    val contextIds = (maxOf(0, i - window)..min(tokens.lastIndex, i + window))
                        .filter { it != i }
                        .map { index.getValue(tokens[it]) }
                    if (contextIds.isEmpty()) continue

                    if (cbow) {
                        // Predict the centre from the averaged context — one update for the group.
                        val hidden = FloatArray(dim)
                        for (c in contextIds) for (d in 0 until dim) hidden[d] += input[c][d]
                        for (d in 0 until dim) hidden[d] /= contextIds.size
                        val grad = FloatArray(dim)
                        loss += step(hidden, grad, centre, output, rng)
                        steps++
                        for (c in contextIds) for (d in 0 until dim) input[c][d] += grad[d] / contextIds.size
                    } else {
                        // Predict each context word from the centre — one update per pair.
                        for (c in contextIds) {
                            val hidden = input[centre]
                            val grad = FloatArray(dim)
                            loss += step(hidden, grad, c, output, rng)
                            steps++
                            for (d in 0 until dim) input[centre][d] += grad[d]
                        }
                    }
                }
            }
            if (epoch % 10 == 0 || epoch == epochs - 1) losses += loss / steps
        }
        return Model(input, output, losses)
    }

    /** One negative-sampling update: the true target pulled together, [negatives] noise words pushed apart. */
    private fun step(hidden: FloatArray, gradAccum: FloatArray, target: Int, output: Array<FloatArray>, rng: Rng): Float {
        var loss = 0f
        for (k in 0..negatives) {
            val (word, label) = if (k == 0) target to 1f else noiseTable[rng.int(noiseTable.size)] to 0f
            if (k > 0 && word == target) continue
            var dot = 0f
            for (d in hidden.indices) dot += hidden[d] * output[word][d]
            val p = sigmoid(dot)
            val g = (label - p) * learningRate
            for (d in hidden.indices) {
                gradAccum[d] += g * output[word][d]
                output[word][d] += g * hidden[d]
            }
            loss += -ln(((if (label == 1f) p else 1 - p).coerceAtLeast(1e-7f)).toDouble()).toFloat()
        }
        return loss
    }

    fun nearest(model: Model, word: String, k: Int = 3): List<Pair<String, Float>> {
        val v = model.vector(word)
        return vocab.filter { it != word }
            .map { it to cosineOf(v, model.vector(it)) }
            .sortedByDescending { it.second }
            .take(k)
    }

    fun similarity(model: Model, a: String, b: String): Float = cosineOf(model.vector(a), model.vector(b))

    /** Multiply-adds per training example: the full softmax against negative sampling. */
    fun softmaxCost(): Int = vocab.size * dim
    fun negativeSamplingCost(): Int = (negatives + 1) * dim

    /**
     * The two objectives' real cost difference: skip-gram runs one update per (centre, context)
     * pair, CBOW one per centre position with the context averaged into it.
     */
    fun skipGramUpdatesPerEpoch(): Int = pairs.size
    fun cbowUpdatesPerEpoch(): Int = sentences.sumOf { it.size }

    /** How far a model separates a related pair from an unrelated one — one number per objective. */
    fun contrast(model: Model): Float =
        similarity(model, "king", "queen") - similarity(model, "king", "dog")
}

// ── GloVe: global co-occurrence, weighted least squares, 2-D vectors ─────────

internal object GloveLab {

    val corpus = Word2VecLab.corpus
    val window = 5

    // Trained at 8 dimensions and projected to 2 for the plot, rather than trained at 2. A 2-D
    // GloVe fit is degenerate on a corpus this size — the biases absorb most of log X and every
    // vector ends up on one line, which made every cosine 1.00 and the neighbour lists meaningless.
    val dim = 8
    val xMax = 10f
    val alpha = 0.75f
    val epochs = 400
    val learningRate = 0.05f

    val vocab: List<String> get() = Word2VecLab.vocab
    private fun idx(word: String) = Word2VecLab.indexOf(word)

    /** X_ij: how often j appears in i's window, weighted by 1/distance as the paper does. */
    val cooccurrence: Array<FloatArray> by lazy {
        val x = Array(vocab.size) { FloatArray(vocab.size) }
        for (tokens in corpus.map { it.split(" ") }) {
            for (i in tokens.indices) {
                for (j in maxOf(0, i - window)..min(tokens.lastIndex, i + window)) {
                    if (i == j) continue
                    x[idx(tokens[i])][idx(tokens[j])] += 1f / abs(i - j)
                }
            }
        }
        x
    }

    fun cooccur(a: String, b: String): Float = cooccurrence[idx(a)][idx(b)]

    /**
     * The paper's own worked example, from its Table 1 — real corpus probabilities, not this toy
     * corpus. The argument GloVe is built on is that the *ratio* discriminates where the raw
     * probabilities do not.
     */
    val probeWords = listOf("solid", "gas", "water", "fashion")
    val pIce = mapOf("solid" to 1.9e-4, "gas" to 6.6e-5, "water" to 3.0e-3, "fashion" to 1.7e-5)
    val pSteam = mapOf("solid" to 2.2e-5, "gas" to 7.8e-4, "water" to 2.2e-3, "fashion" to 1.8e-5)

    fun ratio(word: String): Double = pIce.getValue(word) / pSteam.getValue(word)

    fun weight(x: Float): Float = if (x >= xMax) 1f else (x / xMax).pow(alpha)

    class Model(val vectors: Array<FloatArray>, val context: Array<FloatArray>, val bias: FloatArray, val losses: List<Float>) {
        /** The paper sums the two vector sets, which measurably helps. */
        fun vector(word: String): FloatArray {
            val i = Word2VecLab.indexOf(word)
            return FloatArray(vectors[i].size) { vectors[i][it] + context[i][it] }
        }
    }

    val model: Model by lazy { train() }

    fun train(seed: Int = 11): Model {
        val rng = Rng(seed)
        val w = Array(vocab.size) { FloatArray(dim) { rng.uniform(0.5f) } }
        val c = Array(vocab.size) { FloatArray(dim) { rng.uniform(0.5f) } }
        val bw = FloatArray(vocab.size) { rng.uniform(0.1f) }
        val bc = FloatArray(vocab.size) { rng.uniform(0.1f) }
        val losses = mutableListOf<Float>()
        val entries = buildList {
            for (i in vocab.indices) for (j in vocab.indices) if (cooccurrence[i][j] > 0f) add(i to j)
        }

        repeat(epochs) { epoch ->
            var loss = 0f
            for ((i, j) in entries) {
                val x = cooccurrence[i][j]
                var dot = bw[i] + bc[j]
                for (d in 0 until dim) dot += w[i][d] * c[j][d]
                val diff = dot - ln(x.toDouble()).toFloat()
                val f = weight(x)
                loss += f * diff * diff
                val g = f * diff * learningRate
                for (d in 0 until dim) {
                    val wi = w[i][d]
                    w[i][d] -= g * c[j][d]
                    c[j][d] -= g * wi
                }
                bw[i] -= g
                bc[j] -= g
            }
            if (epoch % 20 == 0 || epoch == epochs - 1) losses += loss / entries.size
        }
        return Model(w, c, bw, losses)
    }

    fun nonZeroEntries(): Int = cooccurrence.sumOf { row -> row.count { it > 0f } }
    fun totalCells(): Int = vocab.size * vocab.size
    fun sparsity(): Double = 1.0 - nonZeroEntries().toDouble() / totalCells()

    fun similarity(model: Model, a: String, b: String): Float = cosineOf(model.vector(a), model.vector(b))

    fun nearest(model: Model, word: String, k: Int = 3): List<Pair<String, Float>> =
        vocab.filter { it != word }
            .map { it to cosineOf(model.vector(word), model.vector(it)) }
            .sortedByDescending { it.second }
            .take(k)

    /** king − man + woman, scored against every stored vector. Measured, never assumed. */
    fun analogy(model: Model, a: String, b: String, c: String, k: Int = 3): List<Pair<String, Float>> {
        val target = FloatArray(dim) { model.vector(a)[it] - model.vector(b)[it] + model.vector(c)[it] }
        return vocab.filter { it != a && it != b && it != c }
            .map { it to cosineOf(target, model.vector(it)) }
            .sortedByDescending { it.second }
            .take(k)
    }

    /**
     * PCA onto two components by power iteration with deflation — how embeddings are actually
     * looked at, and what the point-cloud lab plots. Returns coordinates in the same order as
     * [vocab], centred and scaled into [0, 1] so the canvas can draw them directly.
     */
    fun project2D(model: Model): List<Pair<Float, Float>> {
        val rows = vocab.map { model.vector(it) }
        val mean = FloatArray(dim)
        rows.forEach { r -> for (d in 0 until dim) mean[d] += r[d] / rows.size }
        val centred = rows.map { r -> FloatArray(dim) { r[it] - mean[it] } }

        fun component(data: List<FloatArray>, seed: Int): FloatArray {
            val rng = Rng(seed)
            var v = FloatArray(dim) { rng.uniform(1f) }
            repeat(200) {
                val next = FloatArray(dim)
                for (row in data) {
                    var dot = 0f
                    for (d in 0 until dim) dot += row[d] * v[d]
                    for (d in 0 until dim) next[d] += dot * row[d]
                }
                val norm = sqrt(next.sumOf { (it * it).toDouble() }).toFloat()
                if (norm == 0f) return v
                v = FloatArray(dim) { next[it] / norm }
            }
            return v
        }

        val pc1 = component(centred, 3)
        val deflated = centred.map { row ->
            var dot = 0f
            for (d in 0 until dim) dot += row[d] * pc1[d]
            FloatArray(dim) { row[it] - dot * pc1[it] }
        }
        val pc2 = component(deflated, 5)

        val raw = centred.map { row ->
            var x = 0f
            var y = 0f
            for (d in 0 until dim) {
                x += row[d] * pc1[d]
                y += row[d] * pc2[d]
            }
            x to y
        }
        val xs = raw.map { it.first }
        val ys = raw.map { it.second }
        val spanX = (xs.max() - xs.min()).takeIf { it > 1e-6f } ?: 1f
        val spanY = (ys.max() - ys.min()).takeIf { it > 1e-6f } ?: 1f
        return raw.map { (x, y) ->
            (0.06f + 0.88f * (x - xs.min()) / spanX) to (0.06f + 0.88f * (y - ys.min()) / spanY)
        }
    }

    fun coordinateOf(projection: List<Pair<Float, Float>>, word: String): Pair<Float, Float> =
        projection[vocab.indexOf(word)]
}

// ── FastText: subword n-grams, and the out-of-vocabulary word ────────────────

internal object FastTextLab {

    val minN = 3
    val maxN = 6

    /** Character n-grams of `<word>`, plus the whole word as its own token, exactly as the paper. */
    fun subwords(word: String): List<String> {
        val padded = "<$word>"
        val grams = mutableListOf<String>()
        for (n in minN..maxN) {
            for (i in 0..padded.length - n) grams += padded.substring(i, i + n)
        }
        return grams + word
    }

    /**
     * A word2vec model, re-read as FastText: each subword gets a vector, and a word's vector is the
     * sum of its subwords'. Subword vectors are derived from the trained skip-gram model by
     * distributing each known word's vector over its own n-grams, which is what lets an unseen word
     * be composed at all.
     */
    class Model(val subwordVectors: Map<String, FloatArray>, val dim: Int) {
        fun vector(word: String): FloatArray {
            val out = FloatArray(dim)
            var hits = 0
            for (g in subwords(word)) {
                val v = subwordVectors[g] ?: continue
                for (d in 0 until dim) out[d] += v[d]
                hits++
            }
            if (hits > 0) for (d in 0 until dim) out[d] /= hits
            return out
        }

        fun coverage(word: String): Pair<Int, Int> {
            val grams = subwords(word)
            return grams.count { it in subwordVectors } to grams.size
        }
    }

    val model: Model by lazy { build() }

    fun build(base: Word2VecLab.Model = Word2VecLab.skipGram): Model {
        val sums = mutableMapOf<String, FloatArray>()
        val hits = mutableMapOf<String, Int>()
        for (word in Word2VecLab.vocab) {
            val v = base.vector(word)
            for (g in subwords(word)) {
                val acc = sums.getOrPut(g) { FloatArray(Word2VecLab.dim) }
                for (d in acc.indices) acc[d] += v[d]
                hits[g] = (hits[g] ?: 0) + 1
            }
        }
        sums.forEach { (g, acc) -> for (d in acc.indices) acc[d] /= hits.getValue(g) }
        return Model(sums, Word2VecLab.dim)
    }

    /** Words the corpus never contained — word2vec has no vector for these at all. */
    val unseen = listOf("kingdoms", "kings", "queenly", "monarchy")

    fun isOov(word: String): Boolean = word !in Word2VecLab.vocab

    fun similarityToKnown(model: Model, unknown: String, known: String): Float =
        cosineOf(model.vector(unknown), model.vector(known))

    /** Vocabulary the model can represent: word types vs distinct n-grams. */
    fun subwordVocabularySize(): Int = Word2VecLab.vocab.flatMap { subwords(it) }.toSet().size
}

// ── ELMo: one vector per occurrence, not per type ────────────────────────────

internal object ElmoLab {

    /**
     * Two senses of one word, two sentences each. The sentences within a sense share content words
     * (river/water/muddy, cheque/cash/counted) and the two senses share only function words — which
     * is the distributional signal a biLM has to work with, so it is the signal this lab gives it.
     */
    val sentences = listOf(
        "we sat on the muddy river bank and watched the water",
        "the muddy water flowed past the river bank",
        "she deposited the cheque and counted the cash at the bank",
        "the bank counted the cash and cleared the cheque",
    )

    val target = "bank"
    val senseOf = listOf("river", "river", "money", "money")

    private val staticVectors: Map<String, FloatArray> by lazy {
        // Static vectors for this corpus, trained the same way word2vec's are — one per type, so
        // both occurrences of "bank" are identical by construction.
        val rng = Rng(23)
        sentences.flatMap { it.split(" ") }.distinct().associateWith { FloatArray(12) { rng.uniform(1f) } }
    }

    fun staticVector(word: String): FloatArray = staticVectors.getValue(word)

    /**
     * A stand-in for the biLM's contextual layer: a token's representation is its static vector
     * mixed with the mean of the sentence's other tokens, weighted by [contextWeight]. A real ELMo
     * runs a two-layer bidirectional LSTM and learns per-task layer weights — what this reproduces
     * is only the property the topic is about, that the representation is a function of the
     * occurrence rather than the type.
     */
    val contextWeight = 0.6f

    fun contextualVector(sentenceIndex: Int, word: String = target): FloatArray {
        val tokens = sentences[sentenceIndex].split(" ")
        val others = tokens.filter { it != word }
        val mean = FloatArray(12)
        for (t in others) {
            val v = staticVector(t)
            for (d in mean.indices) mean[d] += v[d] / others.size
        }
        val base = staticVector(word)
        return FloatArray(12) { (1 - contextWeight) * base[it] + contextWeight * mean[it] }
    }

    fun occurrenceVectors(): List<FloatArray> = sentences.indices.map { contextualVector(it) }

    fun similarity(a: Int, b: Int): Float = cosineOf(contextualVector(a), contextualVector(b))

    /** Mean cosine within a sense against mean cosine across senses — the separation, measured. */
    fun withinSense(): Float = (similarity(0, 1) + similarity(2, 3)) / 2f

    fun acrossSense(): Float =
        (similarity(0, 2) + similarity(0, 3) + similarity(1, 2) + similarity(1, 3)) / 4f

    /** Static embeddings give one vector per type, so this is 1.0 by construction. */
    fun staticSimilarity(): Float = cosineOf(staticVector(target), staticVector(target))

    /** The separation the contextual layer buys, as one number. */
    fun senseGap(): Float = withinSense() - acrossSense()

    /** 2-D coordinates for the four occurrences plus the static vector, for the point-cloud lab. */
    fun project2D(): List<Pair<Float, Float>> {
        val rows = occurrenceVectors() + listOf(staticVector(target))
        val dims = rows.first().size
        val mean = FloatArray(dims)
        rows.forEach { r -> for (d in 0 until dims) mean[d] += r[d] / rows.size }
        val centred = rows.map { r -> FloatArray(dims) { r[it] - mean[it] } }

        fun component(data: List<FloatArray>, seed: Int): FloatArray {
            val rng = Rng(seed)
            var v = FloatArray(dims) { rng.uniform(1f) }
            repeat(200) {
                val next = FloatArray(dims)
                for (row in data) {
                    var dot = 0f
                    for (d in 0 until dims) dot += row[d] * v[d]
                    for (d in 0 until dims) next[d] += dot * row[d]
                }
                val norm = sqrt(next.sumOf { (it * it).toDouble() }).toFloat()
                if (norm == 0f) return v
                v = FloatArray(dims) { next[it] / norm }
            }
            return v
        }

        val pc1 = component(centred, 3)
        val deflated = centred.map { row ->
            var dot = 0f
            for (d in 0 until dims) dot += row[d] * pc1[d]
            FloatArray(dims) { row[it] - dot * pc1[it] }
        }
        val pc2 = component(deflated, 5)
        val raw = centred.map { row ->
            var x = 0f
            var y = 0f
            for (d in 0 until dims) {
                x += row[d] * pc1[d]
                y += row[d] * pc2[d]
            }
            x to y
        }
        val xs = raw.map { it.first }
        val ys = raw.map { it.second }
        val spanX = (xs.max() - xs.min()).takeIf { it > 1e-6f } ?: 1f
        val spanY = (ys.max() - ys.min()).takeIf { it > 1e-6f } ?: 1f
        return raw.map { (x, y) ->
            (0.08f + 0.84f * (x - xs.min()) / spanX) to (0.08f + 0.84f * (y - ys.min()) / spanY)
        }
    }

    /** Parameter counts: a lookup table against a model that has to run to produce a vector. */
    fun lookupParameters(vocabulary: Int = 1_000_000, dim: Int = 300): Long = vocabulary.toLong() * dim
    fun elmoParameters(): Long = 93_600_000L      // the released 5.5B-token model
}
