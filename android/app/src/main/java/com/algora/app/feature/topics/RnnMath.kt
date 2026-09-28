package com.algora.app.feature.topics

import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt
import kotlin.math.tanh
import kotlin.random.Random

// ── C5 · Recurrent network math ──────────────────────────────────────────────
// BPTT, bidirectional tagging, the encoder-decoder bottleneck and seq2seq decoding. Every number
// the four labs quote is produced here and pinned by `RnnMathTest`.
//
// These labs train real networks. There is one hand-written RNN cell — tanh state, softmax output,
// gradients derived by hand — and everything else is that cell arranged three ways: a classifier
// whose gradient can be cut off at a chosen window, a tagger that reads left-to-right or both ways,
// and an encoder-decoder that can be decoded greedily or by beam. Nothing here replays stored
// numbers, so the test file asserts the *orderings* the copy argues from and not only the digits.

// ── Parameters, gradients and Adam ───────────────────────────────────────────

internal class Param(val rows: Int, val cols: Int, random: Random? = null) {
    private val scale = 1.0 / sqrt(cols.toDouble())
    val w = DoubleArray(rows * cols) { if (random == null) 0.0 else (random.nextDouble() * 2 - 1) * scale }
    val g = DoubleArray(rows * cols)
    private val m = DoubleArray(rows * cols)
    private val v = DoubleArray(rows * cols)
    private var t = 0

    operator fun get(r: Int, c: Int) = w[r * cols + c]

    fun accumulate(r: Int, c: Int, d: Double) {
        g[r * cols + c] += d
    }

    fun zeroGrad() = g.fill(0.0)

    fun adam(lr: Double, beta1: Double = 0.9, beta2: Double = 0.999, eps: Double = 1e-8) {
        t += 1
        val c1 = 1 - Math.pow(beta1, t.toDouble())
        val c2 = 1 - Math.pow(beta2, t.toDouble())
        for (i in w.indices) {
            m[i] = beta1 * m[i] + (1 - beta1) * g[i]
            v[i] = beta2 * v[i] + (1 - beta2) * g[i] * g[i]
            w[i] -= lr * (m[i] / c1) / (sqrt(v[i] / c2) + eps)
        }
    }
}

internal fun Param.matVec(x: DoubleArray): DoubleArray {
    val out = DoubleArray(rows)
    for (r in 0 until rows) {
        var sum = 0.0
        for (c in 0 until cols) sum += this[r, c] * x[c]
        out[r] = sum
    }
    return out
}

/** The column the one-hot index `i` selects, added into `out`. */
internal fun Param.addColumn(i: Int, out: DoubleArray) {
    for (r in 0 until rows) out[r] += this[r, i]
}

private fun softmax(logits: DoubleArray): DoubleArray {
    val max = logits.max()
    val exps = DoubleArray(logits.size) { exp(logits[it] - max) }
    val sum = exps.sum()
    return DoubleArray(exps.size) { exps[it] / sum }
}

private fun norm(v: DoubleArray) = sqrt(v.sumOf { it * it })

private fun cosine(a: DoubleArray, b: DoubleArray): Double {
    var dot = 0.0
    for (i in a.indices) dot += a[i] * b[i]
    val denominator = norm(a) * norm(b)
    return if (denominator == 0.0) 0.0 else dot / denominator
}

private fun flatGradient(params: List<Param>): DoubleArray {
    val out = DoubleArray(params.sumOf { it.g.size })
    var at = 0
    params.forEach { p -> p.g.copyInto(out, at); at += p.g.size }
    return out
}

// ── The shared cell ──────────────────────────────────────────────────────────
// h_t = tanh(Wx·x_t + Wh·h_{t-1} + b), x_t a one-hot token. Every model below is this cell.

internal class Recurrence(vocab: Int, val hidden: Int, random: Random) {
    val wx = Param(hidden, vocab, random)
    val wh = Param(hidden, hidden, random)
    val b = Param(hidden, 1)

    val params get() = listOf(wx, wh, b)

    /** States h_0..h_T with h_0 = 0. `reversed` feeds the sequence right to left. */
    fun run(seq: IntArray, reversed: Boolean = false): Array<DoubleArray> {
        val states = Array(seq.size + 1) { DoubleArray(hidden) }
        for (step in seq.indices) {
            val token = if (reversed) seq[seq.size - 1 - step] else seq[step]
            val pre = wh.matVec(states[step])
            wx.addColumn(token, pre)
            for (i in 0 until hidden) states[step + 1][i] = tanh(pre[i] + b[i, 0])
        }
        return states
    }

    /**
     * One walk back along the chain, accumulating parameter gradients.
     *
     * `injected[t]` is ∂L/∂h_t arriving from outside the chain — the output layer at that position.
     * A classifier injects at the last state only; a tagger injects at every state.
     *
     * `window` is truncated BPTT: gradient travels at most that many steps back from the end of the
     * sequence, which is the whole subject of the BPTT lab. `matrixShare` and `stateNorm` are
     * diagnostics — respectively each step's own contribution to ∂L/∂Wh, and ‖∂L/∂h_t‖ as the
     * signal travels.
     */
    fun backward(
        seq: IntArray,
        states: Array<DoubleArray>,
        injected: Array<DoubleArray>,
        reversed: Boolean = false,
        window: Int = Int.MAX_VALUE,
        matrixShare: DoubleArray? = null,
        stateNorm: DoubleArray? = null,
    ) {
        val dh = DoubleArray(hidden)
        var stepsBack = 0
        for (step in seq.size downTo 1) {
            for (i in 0 until hidden) dh[i] += injected[step][i]
            if (stepsBack >= window) break
            stepsBack += 1
            stateNorm?.set(step - 1, norm(dh))
            val h = states[step]
            val dz = DoubleArray(hidden) { dh[it] * (1 - h[it] * h[it]) }
            val token = if (reversed) seq[seq.size - step] else seq[step - 1]
            var share = 0.0
            for (r in 0 until hidden) {
                wx.accumulate(r, token, dz[r])
                b.accumulate(r, 0, dz[r])
                for (c in 0 until hidden) {
                    val d = dz[r] * states[step - 1][c]
                    wh.accumulate(r, c, d)
                    share += d * d
                }
            }
            matrixShare?.set(step - 1, sqrt(share))
            for (c in 0 until hidden) {
                var sum = 0.0
                for (r in 0 until hidden) sum += wh[r, c] * dz[r]
                dh[c] = sum
            }
        }
    }
}

/** ∂L/∂h injected at the last state only — the shape a sequence classifier needs. */
internal fun lastOnly(length: Int, hidden: Int, dh: DoubleArray): Array<DoubleArray> =
    Array(length + 1) { if (it == length) dh else DoubleArray(hidden) }

// ── BPTT ─────────────────────────────────────────────────────────────────────

/**
 * The classic long-dependency task: the first token is A or B, everything after it is noise, and the
 * label read off the last step is that first token. One informative input, nine steps of nothing,
 * and a decision at the end.
 *
 * The lab trains one architecture at six truncation windows and measures what each one learns. The
 * planned claim was that a window short of the cue cannot learn the task, and it is wrong — see
 * `reliability`, which is why that sweep is run over several seeds rather than one.
 */
internal object BpttLab {

    const val LENGTH = 10
    const val VOCAB = 5           // 0 = A, 1 = B, 2..4 = noise
    const val HIDDEN = 12
    private const val EPOCHS = 150
    private const val LR = 0.03
    private const val SEED = 21

    val windows = listOf(1, 3, 5, 7, 9, 10)

    class Example(val tokens: IntArray, val label: Int)

    private fun sample(random: Random, count: Int) = List(count) {
        val label = random.nextInt(2)
        Example(IntArray(LENGTH) { position -> if (position == 0) label else 2 + random.nextInt(3) }, label)
    }

    private val random = Random(7)
    val trainSet: List<Example> = sample(random, 48)
    val testSet: List<Example> = sample(random, 48)

    class Model(seed: Int) {
        private val random = Random(seed)
        val cell = Recurrence(VOCAB, HIDDEN, random)
        private val wy = Param(2, HIDDEN, random)
        private val by = Param(2, 1)

        val params: List<Param> get() = cell.params + listOf(wy, by)

        fun probabilities(seq: IntArray): DoubleArray {
            val states = cell.run(seq)
            val logits = wy.matVec(states[seq.size])
            for (i in logits.indices) logits[i] += by[i, 0]
            return softmax(logits)
        }

        /** Cross-entropy for one example, leaving the gradients in the params. */
        fun accumulate(
            item: Example,
            window: Int = Int.MAX_VALUE,
            matrixShare: DoubleArray? = null,
            stateNorm: DoubleArray? = null,
        ): Double {
            val states = cell.run(item.tokens)
            val top = states[LENGTH]
            val logits = wy.matVec(top)
            for (i in logits.indices) logits[i] += by[i, 0]
            val p = softmax(logits)
            val dLogits = DoubleArray(2) { p[it] - if (it == item.label) 1.0 else 0.0 }
            val dh = DoubleArray(HIDDEN)
            for (r in 0 until 2) {
                by.accumulate(r, 0, dLogits[r])
                for (c in 0 until HIDDEN) {
                    wy.accumulate(r, c, dLogits[r] * top[c])
                    dh[c] += wy[r, c] * dLogits[r]
                }
            }
            cell.backward(
                item.tokens, states, lastOnly(LENGTH, HIDDEN, dh),
                window = window, matrixShare = matrixShare, stateNorm = stateNorm,
            )
            return -ln(p[item.label].coerceAtLeast(1e-12))
        }

        fun accuracy(data: List<Example>) =
            data.count { item -> probabilities(item.tokens).let { (if (it[1] > it[0]) 1 else 0) == item.label } } /
                data.size.toDouble()

        fun zeroGrad() = params.forEach { it.zeroGrad() }
    }

    class Window(val window: Int, val trainAccuracy: Double, val testAccuracy: Double, val loss: Double)

    // Memoised: the lab draws the single-seed sweep and the seed comparison in the same session, and
    // they overlap on two runs of ~50 ms each.
    private val runs = mutableMapOf<Pair<Int, Int>, Window>()

    fun trainAt(window: Int, seed: Int = SEED): Window = runs.getOrPut(window to seed) { train(window, seed) }

    private fun train(window: Int, seed: Int): Window {
        // Same seed at every window, so the only difference between these runs is how far the
        // gradient is allowed to travel.
        val model = Model(seed)
        var loss = 0.0
        repeat(EPOCHS) {
            loss = 0.0
            model.zeroGrad()
            trainSet.forEach { loss += model.accumulate(it, window = window) }
            model.params.forEach { p -> for (i in p.g.indices) p.g[i] /= trainSet.size }
            model.params.forEach { it.adam(LR) }
            loss /= trainSet.size
        }
        return Window(window, model.accuracy(trainSet), model.accuracy(testSet), loss)
    }

    val trained: List<Window> by lazy { windows.map { trainAt(it) } }

    val seeds = listOf(21, 33, 47)

    class Reliability(val window: Int, val accuracies: List<Double>) {
        val mean get() = accuracies.average()
        val worst get() = accuracies.min()
        val solved get() = accuracies.count { it > 0.95 }
    }

    /**
     * The same sweep repeated across seeds. One run of this sweep reads as a clean threshold and a
     * different seed moves that threshold, so the honest summary is not "k must be at least n" but
     * "below n, whether it works is a property of the initialization". Quoted by the copy and pinned
     * by `RnnMathTest`; the lab draws the single-seed curve, which is cheap enough to build on the
     * main thread.
     */
    fun reliability(sweep: List<Int> = windows): List<Reliability> =
        sweep.map { window -> Reliability(window, seeds.map { trainAt(window, it).testAccuracy }) }

    private fun diagnostic(pick: (DoubleArray, Model, Example) -> Unit): DoubleArray {
        val model = Model(SEED)
        val totals = DoubleArray(LENGTH)
        trainSet.forEach { item ->
            val row = DoubleArray(LENGTH)
            model.zeroGrad()
            pick(row, model, item)
            for (i in 0 until LENGTH) totals[i] += row[i] / trainSet.size
        }
        return totals
    }

    /** Each step's share of ∂L/∂Wh at initialization, averaged over the training set. */
    val matrixShare: DoubleArray by lazy {
        diagnostic { row, model, item -> model.accumulate(item, matrixShare = row) }
    }

    /** ‖∂L/∂h_t‖ at initialization — the signal that survives the walk back. */
    val stateGradient: DoubleArray by lazy {
        diagnostic { row, model, item -> model.accumulate(item, stateNorm = row) }
    }

    private fun gradientAt(window: Int): DoubleArray {
        val model = Model(SEED)
        model.zeroGrad()
        trainSet.forEach { model.accumulate(it, window = window) }
        return flatGradient(model.params)
    }

    /** cos(truncated gradient, full gradient) at initialization, per window. */
    val gradientAgreement: List<Pair<Int, Double>> by lazy {
        val full = gradientAt(Int.MAX_VALUE)
        windows.map { it to cosine(gradientAt(it), full) }
    }

    /** The share of the full gradient's magnitude each window recovers. */
    val magnitudeShare: List<Pair<Int, Double>> by lazy {
        val full = norm(gradientAt(Int.MAX_VALUE))
        windows.map { it to norm(gradientAt(it)) / full }
    }

    /** Hidden values a training step has to keep alive: T·H for full BPTT, k·H for a window. */
    fun storedActivations(window: Int) = minOf(window, LENGTH) * HIDDEN
}

// ── Bidirectional RNNs ───────────────────────────────────────────────────────

/**
 * A tagging corpus built from garden-path minimal pairs: sentences identical up to and including
 * the disputed word, told apart only by something further right. That makes the left-to-right
 * model's ceiling a property of the corpus rather than of the training run — it is enumerated below
 * before anything is trained, and the trained taggers are then measured against it.
 */
internal object BiRnnLab {

    val tags = listOf("DET", "ADJ", "NOUN", "VERB", "PREP", "PRON", "PART")

    class Sentence(val words: List<String>, val labels: List<String>)

    private fun sentence(vararg pairs: String) = Sentence(
        pairs.map { it.substringBefore('/') },
        pairs.map { it.substringAfter('/') },
    )

    // Four minimal pairs (indices 0/1, 2/3, 4/5, 6/7) and four sentences that are decidable from
    // the left, so the corpus is not rigged to make the forward model look worse than it is.
    val corpus = listOf(
        sentence("the/DET", "old/ADJ", "man/NOUN", "sighed/VERB"),
        sentence("the/DET", "old/ADJ", "man/VERB", "the/DET", "boats/NOUN"),
        sentence("the/DET", "complex/ADJ", "houses/NOUN", "burned/VERB"),
        sentence("the/DET", "complex/NOUN", "houses/VERB", "married/ADJ", "soldiers/NOUN"),
        sentence("the/DET", "horse/NOUN", "raced/VERB", "past/PREP", "the/DET", "barn/NOUN"),
        sentence("the/DET", "horse/NOUN", "raced/PART", "past/PREP", "the/DET", "barn/NOUN", "fell/VERB"),
        sentence("she/PRON", "saw/VERB", "her/DET", "duck/NOUN"),
        sentence("she/PRON", "saw/VERB", "her/PRON", "duck/VERB", "under/PREP", "the/DET", "table/NOUN"),
        sentence("time/NOUN", "flies/VERB", "like/PREP", "an/DET", "arrow/NOUN"),
        sentence("fruit/NOUN", "flies/NOUN", "like/VERB", "a/DET", "banana/NOUN"),
        sentence("the/DET", "book/NOUN", "fell/VERB"),
        sentence("i/PRON", "book/VERB", "the/DET", "flight/NOUN"),
    )

    val vocabulary: List<String> = corpus.flatMap { it.words }.distinct().sorted()

    val tokenCount = corpus.sumOf { it.words.size }

    private fun encode(words: List<String>) = IntArray(words.size) { vocabulary.indexOf(words[it]) }

    class Position(val sentence: Int, val index: Int) {
        val word get() = corpus[sentence].words[index]
        val tag get() = corpus[sentence].labels[index]
        override fun toString() = "\"$word\" in \"${corpus[sentence].words.joinToString(" ")}\""
    }

    /**
     * Positions whose tag is not a function of the words at and to the left of them, enumerated over
     * the corpus with no model involved. This is what caps a left-to-right tagger.
     */
    val leftAmbiguous: List<Position> by lazy {
        corpus.indices.flatMap { s ->
            corpus[s].words.indices.mapNotNull { i ->
                val prefix = corpus[s].words.subList(0, i + 1)
                val clash = corpus.indices.any { other ->
                    other != s && corpus[other].words.size > i &&
                        corpus[other].words.subList(0, i + 1) == prefix &&
                        corpus[other].labels[i] != corpus[s].labels[i]
                }
                if (clash) Position(s, i) else null
            }
        }
    }

    /** The same enumeration with the whole sentence visible. */
    val fullyAmbiguous: List<Position> by lazy {
        corpus.indices.flatMap { s ->
            corpus[s].words.indices.mapNotNull { i ->
                val clash = corpus.indices.any { other ->
                    other != s && corpus[other].words == corpus[s].words &&
                        corpus[other].labels[i] != corpus[s].labels[i]
                }
                if (clash) Position(s, i) else null
            }
        }
    }

    /**
     * The best accuracy any left-to-right tagger can reach here: every position whose prefix
     * determines its tag, plus the largest group inside each set of positions that share a prefix.
     */
    val forwardCeiling: Double by lazy {
        val groups = corpus.indices.flatMap { s ->
            corpus[s].words.indices.map { i -> corpus[s].words.subList(0, i + 1) to corpus[s].labels[i] }
        }.groupBy({ it.first }, { it.second })
        groups.values.sumOf { labels -> labels.groupingBy { it }.eachCount().values.max() } / tokenCount.toDouble()
    }

    private const val HIDDEN = 16
    private const val EPOCHS = 900
    private const val LR = 0.03

    class Tagger(val bidirectional: Boolean, seed: Int) {
        private val random = Random(seed)
        private val forward = Recurrence(vocabulary.size, HIDDEN, random)
        private val backward = if (bidirectional) Recurrence(vocabulary.size, HIDDEN, random) else null
        private val width = if (bidirectional) 2 * HIDDEN else HIDDEN
        private val wy = Param(tags.size, width, random)
        private val by = Param(tags.size, 1)

        val params: List<Param>
            get() = forward.params + (backward?.params ?: emptyList()) + listOf(wy, by)

        val parameterCount get() = params.sumOf { it.w.size }

        private fun featureAt(index: Int, length: Int, f: Array<DoubleArray>, b: Array<DoubleArray>?): DoubleArray {
            val out = DoubleArray(width)
            f[index + 1].copyInto(out, 0)
            // The backward pass ran right to left, so the state after (length - index) steps is the
            // one that has read positions index..length-1 — this position and everything after it.
            b?.get(length - index)?.copyInto(out, HIDDEN)
            return out
        }

        private fun distribution(index: Int, length: Int, f: Array<DoubleArray>, b: Array<DoubleArray>?): DoubleArray {
            val logits = wy.matVec(featureAt(index, length, f, b))
            for (i in logits.indices) logits[i] += by[i, 0]
            return softmax(logits)
        }

        fun tagDistribution(words: List<String>, index: Int): DoubleArray {
            val seq = encode(words)
            return distribution(index, words.size, forward.run(seq), backward?.run(seq, reversed = true))
        }

        fun predict(words: List<String>): List<String> {
            val seq = encode(words)
            val f = forward.run(seq)
            val b = backward?.run(seq, reversed = true)
            return words.indices.map { i ->
                val p = distribution(i, words.size, f, b)
                tags[p.indices.maxBy { p[it] }]
            }
        }

        private fun accumulate(item: Sentence): Double {
            val seq = encode(item.words)
            val f = forward.run(seq)
            val b = backward?.run(seq, reversed = true)
            val forwardInjected = Array(seq.size + 1) { DoubleArray(HIDDEN) }
            val backwardInjected = Array(seq.size + 1) { DoubleArray(HIDDEN) }
            var loss = 0.0
            item.words.indices.forEach { i ->
                val x = featureAt(i, seq.size, f, b)
                val p = distribution(i, seq.size, f, b)
                val gold = tags.indexOf(item.labels[i])
                loss += -ln(p[gold].coerceAtLeast(1e-12))
                val dLogits = DoubleArray(tags.size) { p[it] - if (it == gold) 1.0 else 0.0 }
                val dx = DoubleArray(width)
                for (r in tags.indices) {
                    by.accumulate(r, 0, dLogits[r])
                    for (c in 0 until width) {
                        wy.accumulate(r, c, dLogits[r] * x[c])
                        dx[c] += wy[r, c] * dLogits[r]
                    }
                }
                for (h in 0 until HIDDEN) forwardInjected[i + 1][h] += dx[h]
                if (backward != null) for (h in 0 until HIDDEN) backwardInjected[seq.size - i][h] += dx[HIDDEN + h]
            }
            forward.backward(seq, f, forwardInjected)
            backward?.backward(seq, b!!, backwardInjected, reversed = true)
            return loss
        }

        fun train(): Tagger {
            repeat(EPOCHS) {
                params.forEach { it.zeroGrad() }
                corpus.forEach { accumulate(it) }
                params.forEach { p -> for (i in p.g.indices) p.g[i] /= corpus.size }
                params.forEach { it.adam(LR) }
            }
            return this
        }

        val accuracy: Double
            get() = corpus.sumOf { item ->
                predict(item.words).zip(item.labels).count { (predicted, gold) -> predicted == gold }
            } / tokenCount.toDouble()

        fun accuracyAt(positions: List<Position>): Double {
            if (positions.isEmpty()) return 1.0
            return positions.count { position ->
                val item = corpus[position.sentence]
                predict(item.words)[position.index] == item.labels[position.index]
            } / positions.size.toDouble()
        }
    }

    val forwardTagger: Tagger by lazy { Tagger(bidirectional = false, seed = 5).train() }
    val biTagger: Tagger by lazy { Tagger(bidirectional = true, seed = 5).train() }
}

// ── Encoder-decoder and seq2seq decoding ─────────────────────────────────────

/**
 * Copy a sequence of symbols through a fixed-size context vector. Copying is the weakest possible
 * demand — no transformation at all — which is what makes it a fair test of the bottleneck: every
 * failure below is the vector losing the source, not the model failing to compute something.
 *
 * The encoder can be fed the source forwards or backwards. That is Sutskever et al.'s reversal
 * trick, and on a copy task it is exactly the right experiment: reversing puts the source token the
 * decoder needs *first* nearest the context vector, without changing the data, the parameter count
 * or the training budget.
 */
internal object Seq2SeqLab {

    const val SYMBOLS = 6
    const val EOS = SYMBOLS
    const val SOS = SYMBOLS + 1
    const val MAX_LENGTH = 6
    const val HIDDEN = 12
    private const val EPOCHS = 100
    private const val LR = 0.005
    private const val SEED = 3

    val symbolNames = listOf("a", "b", "c", "d", "e", "f")

    class Example(val source: IntArray) {
        /** Copy: the decoder has to reproduce the source in order. */
        val target: IntArray get() = source
        val text get() = source.joinToString("") { symbolNames[it] }
    }

    private fun sample(count: Int, random: Random, fixedLength: Int? = null) = List(count) {
        val length = fixedLength ?: (1 + random.nextInt(MAX_LENGTH))
        Example(IntArray(length) { random.nextInt(SYMBOLS) })
    }

    private val random = Random(11)
    val trainSet: List<Example> = sample(120, random)
    val testSet: List<Example> = sample(120, random)

    // A separate fixed-length pool, so the context-vector probe reads the same positions in every
    // example it is fitted on.
    val probeTrain: List<Example> = sample(120, random, fixedLength = MAX_LENGTH)
    val probeTest: List<Example> = sample(120, random, fixedLength = MAX_LENGTH)

    class Hypothesis(val tokens: IntArray, val logProbability: Double) {
        val probability get() = exp(logProbability)
        val text get() = tokens.joinToString("") { symbolNames[it] }
    }

    /** `feedReversed` hands the encoder the source backwards — the only difference between models. */
    class Model(val hidden: Int, seed: Int, val feedReversed: Boolean = false) {
        private val random = Random(seed)
        private val encoder = Recurrence(SYMBOLS, hidden, random)
        private val decoder = Recurrence(SYMBOLS + 2, hidden, random)
        private val wy = Param(SYMBOLS + 1, hidden, random)
        private val by = Param(SYMBOLS + 1, 1)

        val params: List<Param> get() = encoder.params + decoder.params + listOf(wy, by)
        val parameterCount get() = params.sumOf { it.w.size }

        /** The context vector — everything the decoder will ever know about the source. */
        fun context(source: IntArray): DoubleArray = encoder.run(source, reversed = feedReversed).last()

        private fun step(state: DoubleArray, token: Int): Pair<DoubleArray, DoubleArray> {
            val pre = decoder.wh.matVec(state)
            decoder.wx.addColumn(token, pre)
            val next = DoubleArray(hidden) { tanh(pre[it] + decoder.b[it, 0]) }
            val logits = wy.matVec(next)
            for (i in logits.indices) logits[i] += by[i, 0]
            return next to softmax(logits)
        }

        private fun accumulate(item: Example): Double {
            val encoderStates = encoder.run(item.source, reversed = feedReversed)
            val target = item.target + intArrayOf(EOS)
            val inputs = intArrayOf(SOS) + item.target
            val decoderStates = Array(target.size + 1) { DoubleArray(hidden) }
            encoderStates.last().copyInto(decoderStates[0])
            val probabilities = Array(target.size) { DoubleArray(SYMBOLS + 1) }
            var loss = 0.0
            for (t in target.indices) {
                val (next, p) = step(decoderStates[t], inputs[t])
                next.copyInto(decoderStates[t + 1])
                p.copyInto(probabilities[t])
                loss += -ln(p[target[t]].coerceAtLeast(1e-12))
            }
            // Back through the decoder by hand, then hand what is left to the encoder's last state:
            // the context vector is the only channel between them, so it is the only gradient path.
            val dh = DoubleArray(hidden)
            for (t in target.indices.reversed()) {
                val p = probabilities[t]
                val h = decoderStates[t + 1]
                for (r in 0 until SYMBOLS + 1) {
                    val dLogit = p[r] - if (r == target[t]) 1.0 else 0.0
                    by.accumulate(r, 0, dLogit)
                    for (c in 0 until hidden) {
                        wy.accumulate(r, c, dLogit * h[c])
                        dh[c] += wy[r, c] * dLogit
                    }
                }
                val dz = DoubleArray(hidden) { dh[it] * (1 - h[it] * h[it]) }
                for (r in 0 until hidden) {
                    decoder.wx.accumulate(r, inputs[t], dz[r])
                    decoder.b.accumulate(r, 0, dz[r])
                    for (c in 0 until hidden) decoder.wh.accumulate(r, c, dz[r] * decoderStates[t][c])
                }
                for (c in 0 until hidden) {
                    var sum = 0.0
                    for (r in 0 until hidden) sum += decoder.wh[r, c] * dz[r]
                    dh[c] = sum
                }
            }
            encoder.backward(
                item.source, encoderStates, lastOnly(item.source.size, hidden, dh),
                reversed = feedReversed,
            )
            return loss / target.size
        }

        fun train(epochs: Int = EPOCHS, lr: Double = LR): Model {
            repeat(epochs) {
                trainSet.forEach { item ->
                    params.forEach { it.zeroGrad() }
                    accumulate(item)
                    params.forEach { it.adam(lr) }
                }
            }
            return this
        }

        /** Greedy decoding: argmax, feed it back, stop at EOS. */
        fun greedy(source: IntArray): Hypothesis {
            var state = context(source)
            var token = SOS
            val out = mutableListOf<Int>()
            var logProbability = 0.0
            repeat(MAX_LENGTH + 1) {
                val (next, p) = step(state, token)
                val best = p.indices.maxBy { p[it] }
                logProbability += ln(p[best].coerceAtLeast(1e-12))
                if (best == EOS) return Hypothesis(out.toIntArray(), logProbability)
                out += best
                state = next
                token = best
            }
            return Hypothesis(out.toIntArray(), logProbability)
        }

        class DecodeStep(val emitted: Int, val distribution: DoubleArray)

        /** The greedy run step by step, for a lab that has to show the loop rather than its result. */
        fun decodeTrace(source: IntArray): List<DecodeStep> {
            var state = context(source)
            var token = SOS
            val trace = mutableListOf<DecodeStep>()
            repeat(MAX_LENGTH + 1) {
                val (next, p) = step(state, token)
                val best = p.indices.maxBy { p[it] }
                trace += DecodeStep(best, p)
                if (best == EOS) return trace
                state = next
                token = best
            }
            return trace
        }

        class BeamCandidate(val tokens: IntArray, val score: Double, val finished: Boolean) {
            val text get() = tokens.joinToString("") { symbolNames[it] }
        }

        /** The beam's live set after each step — what the search is actually holding. */
        fun beamTrace(source: IntArray, width: Int): List<List<BeamCandidate>> {
            class Live(val tokens: List<Int>, val state: DoubleArray, val score: Double, val last: Int)

            var live = listOf(Live(emptyList(), context(source), 0.0, SOS))
            val finished = mutableListOf<BeamCandidate>()
            val trace = mutableListOf<List<BeamCandidate>>()
            repeat(MAX_LENGTH + 1) {
                val expanded = mutableListOf<Live>()
                live.forEach { candidate ->
                    val (next, p) = step(candidate.state, candidate.last)
                    p.indices.forEach { token ->
                        val score = candidate.score + ln(p[token].coerceAtLeast(1e-12))
                        if (token == EOS) {
                            finished += BeamCandidate(candidate.tokens.toIntArray(), score, finished = true)
                        } else {
                            expanded += Live(candidate.tokens + token, next, score, token)
                        }
                    }
                }
                live = expanded.sortedByDescending { it.score }.take(width)
                trace += (live.map { BeamCandidate(it.tokens.toIntArray(), it.score, finished = false) } + finished)
                    .sortedByDescending { it.score }
                    .take(width)
            }
            return trace
        }

        /** Beam search. `normalize` scores by log-probability per token instead of per sequence. */
        fun beam(source: IntArray, width: Int, normalize: Boolean = false): Hypothesis {
            class Live(val tokens: List<Int>, val state: DoubleArray, val score: Double, val last: Int)

            var live = listOf(Live(emptyList(), context(source), 0.0, SOS))
            val finished = mutableListOf<Hypothesis>()
            repeat(MAX_LENGTH + 1) {
                val expanded = mutableListOf<Live>()
                live.forEach { candidate ->
                    val (next, p) = step(candidate.state, candidate.last)
                    p.indices.forEach { token ->
                        val score = candidate.score + ln(p[token].coerceAtLeast(1e-12))
                        if (token == EOS) {
                            finished += Hypothesis(candidate.tokens.toIntArray(), score)
                        } else {
                            expanded += Live(candidate.tokens + token, next, score, token)
                        }
                    }
                }
                live = expanded.sortedByDescending { it.score }.take(width)
            }
            live.forEach { finished += Hypothesis(it.tokens.toIntArray(), it.score) }
            return finished.maxBy {
                if (normalize) it.logProbability / maxOf(it.tokens.size, 1) else it.logProbability
            }
        }

        /** Next-token accuracy with the gold prefix supplied — what training actually optimises. */
        fun teacherForcedAccuracy(data: List<Example>): Double {
            var correct = 0
            var total = 0
            data.forEach { item ->
                var state = context(item.source)
                val target = item.target + intArrayOf(EOS)
                val inputs = intArrayOf(SOS) + item.target
                target.indices.forEach { t ->
                    val (next, p) = step(state, inputs[t])
                    if (p.indices.maxBy { p[it] } == target[t]) correct += 1
                    total += 1
                    state = next
                }
            }
            return correct / total.toDouble()
        }

        /** The same count with the model reading its own output — the exposure-bias comparison. */
        fun freeRunningAccuracy(data: List<Example>): Double {
            var correct = 0
            var total = 0
            data.forEach { item ->
                val predicted = greedy(item.source).tokens
                item.target.indices.forEach { i ->
                    if (i < predicted.size && predicted[i] == item.target[i]) correct += 1
                    total += 1
                }
            }
            return correct / total.toDouble()
        }

        fun exactMatch(data: List<Example>, width: Int = 1, normalize: Boolean = false) =
            data.count { item ->
                val prediction =
                    if (width <= 1 && !normalize) greedy(item.source) else beam(item.source, width, normalize)
                prediction.tokens.contentEquals(item.target)
            } / data.size.toDouble()

        fun exactMatchByLength(data: List<Example>): List<Pair<Int, Double>> =
            (1..MAX_LENGTH).mapNotNull { length ->
                val subset = data.filter { it.source.size == length }
                if (subset.isEmpty()) null
                else length to subset.count { greedy(it.source).tokens.contentEquals(it.target) } / subset.size.toDouble()
            }

        /** log P(tokens | source) under the model, EOS included — scored, not searched. */
        fun sequenceLogProbability(source: IntArray, tokens: IntArray): Double {
            var state = context(source)
            var previous = SOS
            var total = 0.0
            (tokens + intArrayOf(EOS)).forEach { token ->
                val (next, p) = step(state, previous)
                total += ln(p[token].coerceAtLeast(1e-12))
                state = next
                previous = token
            }
            return total
        }
    }

    val forwardFed: Model by lazy { Model(HIDDEN, SEED).train() }
    val reverseFed: Model by lazy { Model(HIDDEN, SEED, feedReversed = true).train() }

    /**
     * A linear read-out fitted from the context vector to the source symbol at one position. It
     * answers the question the accuracy curve leaves open: when a long source fails, *which* part of
     * it did the vector stop holding? Softmax regression, fitted by gradient descent on contexts the
     * trained encoder produces, and scored on held-out ones — the encoder is frozen throughout.
     */
    fun contextProbe(model: Model, position: Int): Double {
        val w = Param(SYMBOLS, model.hidden, Random(99))
        val b = Param(SYMBOLS, 1)
        val trainContexts = probeTrain.map { model.context(it.source) to it.source[position] }
        repeat(250) {
            w.zeroGrad()
            b.zeroGrad()
            trainContexts.forEach { (c, gold) ->
                val logits = w.matVec(c)
                for (i in logits.indices) logits[i] += b[i, 0]
                val p = softmax(logits)
                for (r in 0 until SYMBOLS) {
                    val d = p[r] - if (r == gold) 1.0 else 0.0
                    b.accumulate(r, 0, d)
                    for (col in 0 until model.hidden) w.accumulate(r, col, d * c[col])
                }
            }
            w.g.indices.forEach { i -> w.g[i] /= trainContexts.size }
            b.g.indices.forEach { i -> b.g[i] /= trainContexts.size }
            w.adam(0.05)
            b.adam(0.05)
        }
        return probeTest.count { item ->
            val logits = w.matVec(model.context(item.source))
            for (i in logits.indices) logits[i] += b[i, 0]
            logits.indices.maxBy { logits[it] } == item.source[position]
        } / probeTest.size.toDouble()
    }

    /** Recoverability of every source position from one context vector. */
    fun probeProfile(model: Model): List<Pair<Int, Double>> =
        (0 until MAX_LENGTH).map { it + 1 to contextProbe(model, it) }

    /** Bits of source against dimensions of context — the bottleneck stated as a capacity. */
    fun sourceBits(length: Int) = length * ln(SYMBOLS.toDouble()) / ln(2.0)

    class BeamResult(
        val width: Int,
        val exactMatch: Double,
        val meanLogProbability: Double,
        val changed: Int,
        val meanLength: Double,
    )

    class ErrorSplit(val correct: Int, val searchError: Int, val modelError: Int) {
        val total get() = correct + searchError + modelError
    }

    /**
     * Every wrong output is one of two different failures, and a wider beam only fixes one of them.
     * Scoring the gold sequence under the same model separates them: if the model gives the gold
     * sequence a *higher* probability than the one the search returned, the search lost it — widen
     * the beam. If the model gives its own wrong answer the higher probability, no search can help,
     * because the thing being searched is wrong.
     */
    fun errorSplit(model: Model, width: Int): ErrorSplit {
        var correct = 0
        var searchError = 0
        var modelError = 0
        testSet.forEach { item ->
            val hypothesis = model.beam(item.source, width)
            when {
                hypothesis.tokens.contentEquals(item.target) -> correct += 1
                model.sequenceLogProbability(item.source, item.target) > hypothesis.logProbability -> searchError += 1
                else -> modelError += 1
            }
        }
        return ErrorSplit(correct, searchError, modelError)
    }

    class NormalizationEffect(val changed: Int, val plainLength: Double, val normalizedLength: Double, val exactMatchDelta: Double)

    /** Length normalization compared against the same beam without it — same model, same width. */
    fun normalizationEffect(model: Model, width: Int): NormalizationEffect {
        var changed = 0
        var plain = 0.0
        var normalized = 0.0
        var plainMatches = 0
        var normalizedMatches = 0
        testSet.forEach { item ->
            val a = model.beam(item.source, width, normalize = false)
            val b = model.beam(item.source, width, normalize = true)
            if (!a.tokens.contentEquals(b.tokens)) changed += 1
            plain += a.tokens.size
            normalized += b.tokens.size
            if (a.tokens.contentEquals(item.target)) plainMatches += 1
            if (b.tokens.contentEquals(item.target)) normalizedMatches += 1
        }
        return NormalizationEffect(
            changed,
            plain / testSet.size,
            normalized / testSet.size,
            (normalizedMatches - plainMatches) / testSet.size.toDouble(),
        )
    }

    fun beamSweep(model: Model, widths: List<Int>, normalize: Boolean = false): List<BeamResult> {
        val greedyOutputs = testSet.map { model.greedy(it.source).tokens }
        return widths.map { width ->
            var logProbability = 0.0
            var changed = 0
            var matched = 0
            var length = 0.0
            testSet.forEachIndexed { index, item ->
                val hypothesis = model.beam(item.source, width, normalize)
                logProbability += hypothesis.logProbability
                length += hypothesis.tokens.size
                if (!hypothesis.tokens.contentEquals(greedyOutputs[index])) changed += 1
                if (hypothesis.tokens.contentEquals(item.target)) matched += 1
            }
            BeamResult(
                width,
                matched / testSet.size.toDouble(),
                logProbability / testSet.size,
                changed,
                length / testSet.size,
            )
        }
    }
}
