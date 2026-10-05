package com.algora.app.feature.topics

import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt
import kotlin.math.tanh
import kotlin.random.Random

// ── C6 · Attention and pre-trained model math ────────────────────────────────
// Self- and cross-attention, multi-head, and the four pre-training recipes. Pinned by
// `AttentionMathTest`.
//
// The centrepiece is a real one: `AttentionSeq2Seq` is C5's encoder-decoder with cross-attention
// bolted on, trained on the same task, the same data and the same budget as the two models the
// encoder-decoder topic already ships. C5 ended on the claim that attention removes the fixed-vector
// bottleneck. This file is where that claim is either reproduced or not, on the same numbers.

private fun softmaxD(logits: DoubleArray): DoubleArray {
    val max = logits.max()
    val exps = DoubleArray(logits.size) { exp(logits[it] - max) }
    val sum = exps.sum()
    return DoubleArray(exps.size) { exps[it] / sum }
}

// ── Cross-attention over the C5 copy task ────────────────────────────────────

/**
 * Encoder-decoder with cross-attention. Everything except the attention block is the C5 model:
 * same cell, same task (copy a symbol sequence), same 120 training pairs, same 100 epochs, same
 * learning rate. The decoder no longer starts from the encoder's last state alone — at every step it
 * scores its own state against *all* encoder states and reads a weighted sum of them.
 *
 * Heads are slices of the same projections, so `heads = 1` and `heads = 4` differ only in how the
 * d-dimensional score space is partitioned — not in parameter count, which is what the multi-head
 * topic is about.
 */
internal class AttentionSeq2Seq(val hidden: Int, val heads: Int, seed: Int) {

    private val random = Random(seed)
    private val encoder = Recurrence(Seq2SeqLab.SYMBOLS, hidden, random)
    private val decoder = Recurrence(Seq2SeqLab.SYMBOLS + 2, hidden, random)
    private val wq = Param(hidden, hidden, random)
    private val wk = Param(hidden, hidden, random)
    private val wv = Param(hidden, hidden, random)
    private val wy = Param(Seq2SeqLab.SYMBOLS + 1, 2 * hidden, random)
    private val by = Param(Seq2SeqLab.SYMBOLS + 1, 1)

    private val headDim = hidden / heads
    private val scale = sqrt(headDim.toDouble())

    val params: List<Param>
        get() = encoder.params + decoder.params + listOf(wq, wk, wv, wy, by)

    val parameterCount get() = params.sumOf { it.w.size }

    /** One decoder step's attention: the weights per head, and the context vector they produce. */
    private class Attended(val weights: Array<DoubleArray>, val context: DoubleArray)

    private fun attend(query: DoubleArray, keys: List<DoubleArray>, values: List<DoubleArray>): Attended {
        val weights = Array(heads) { DoubleArray(keys.size) }
        val context = DoubleArray(hidden)
        for (h in 0 until heads) {
            val from = h * headDim
            val scores = DoubleArray(keys.size) { i ->
                var dot = 0.0
                for (c in from until from + headDim) dot += query[c] * keys[i][c]
                dot / scale
            }
            val alpha = softmaxD(scores)
            alpha.copyInto(weights[h])
            for (i in keys.indices) {
                for (c in from until from + headDim) context[c] += alpha[i] * values[i][c]
            }
        }
        return Attended(weights, context)
    }

    private fun logitsFrom(state: DoubleArray, context: DoubleArray): DoubleArray {
        val joined = DoubleArray(2 * hidden)
        state.copyInto(joined, 0)
        context.copyInto(joined, hidden)
        val logits = wy.matVec(joined)
        for (i in logits.indices) logits[i] += by[i, 0]
        return logits
    }

    private fun decoderStep(state: DoubleArray, token: Int): DoubleArray {
        val pre = decoder.wh.matVec(state)
        decoder.wx.addColumn(token, pre)
        return DoubleArray(hidden) { tanh(pre[it] + decoder.b[it, 0]) }
    }

    private fun accumulate(item: Seq2SeqLab.Example): Double {
        val encoderStates = encoder.run(item.source)
        val h = (1..item.source.size).map { encoderStates[it] }
        val keys = h.map { wk.matVec(it) }
        val values = h.map { wv.matVec(it) }

        val target = item.target + intArrayOf(Seq2SeqLab.EOS)
        val inputs = intArrayOf(Seq2SeqLab.SOS) + item.target
        val states = Array(target.size + 1) { DoubleArray(hidden) }
        encoderStates.last().copyInto(states[0])
        val attentions = arrayOfNulls<Attended>(target.size)
        val probabilities = Array(target.size) { DoubleArray(Seq2SeqLab.SYMBOLS + 1) }
        val queries = Array(target.size) { DoubleArray(hidden) }
        var loss = 0.0

        for (t in target.indices) {
            val next = decoderStep(states[t], inputs[t])
            next.copyInto(states[t + 1])
            val query = wq.matVec(next)
            query.copyInto(queries[t])
            val attended = attend(query, keys, values)
            attentions[t] = attended
            val p = softmaxD(logitsFrom(next, attended.context))
            p.copyInto(probabilities[t])
            loss += -ln(p[target[t]].coerceAtLeast(1e-12))
        }

        // Backward. Gradients reach the encoder states by two routes now — through the attention
        // block at every decoder step, and through the initial state as before — which is the whole
        // structural difference from C5's model.
        val dEncoder = Array(item.source.size + 1) { DoubleArray(hidden) }
        val dKeys = Array(h.size) { DoubleArray(hidden) }
        val dValues = Array(h.size) { DoubleArray(hidden) }
        val dState = DoubleArray(hidden)

        for (t in target.indices.reversed()) {
            val attended = attentions[t]!!
            val state = states[t + 1]
            val p = probabilities[t]
            val joined = DoubleArray(2 * hidden)
            state.copyInto(joined, 0)
            attended.context.copyInto(joined, hidden)

            val dJoined = DoubleArray(2 * hidden)
            for (r in 0 until Seq2SeqLab.SYMBOLS + 1) {
                val dLogit = p[r] - if (r == target[t]) 1.0 else 0.0
                by.accumulate(r, 0, dLogit)
                for (c in 0 until 2 * hidden) {
                    wy.accumulate(r, c, dLogit * joined[c])
                    dJoined[c] += wy[r, c] * dLogit
                }
            }
            for (c in 0 until hidden) dState[c] += dJoined[c]
            val dContext = DoubleArray(hidden) { dJoined[hidden + it] }

            // Through the attention weights, per head.
            val dQuery = DoubleArray(hidden)
            for (head in 0 until heads) {
                val from = head * headDim
                val alpha = attended.weights[head]
                val dAlpha = DoubleArray(h.size)
                for (i in h.indices) {
                    var dot = 0.0
                    for (c in from until from + headDim) {
                        dot += dContext[c] * values[i][c]
                        dValues[i][c] += alpha[i] * dContext[c]
                    }
                    dAlpha[i] = dot
                }
                val weighted = h.indices.sumOf { alpha[it] * dAlpha[it] }
                for (i in h.indices) {
                    val dScore = alpha[i] * (dAlpha[i] - weighted) / scale
                    for (c in from until from + headDim) {
                        dQuery[c] += dScore * keys[i][c]
                        dKeys[i][c] += dScore * queries[t][c]
                    }
                }
            }

            for (r in 0 until hidden) {
                for (c in 0 until hidden) {
                    wq.accumulate(r, c, dQuery[r] * state[c])
                    dState[c] += wq[r, c] * dQuery[r]
                }
            }

            // Through the decoder cell, one step.
            val dz = DoubleArray(hidden) { dState[it] * (1 - state[it] * state[it]) }
            for (r in 0 until hidden) {
                decoder.wx.accumulate(r, inputs[t], dz[r])
                decoder.b.accumulate(r, 0, dz[r])
                for (c in 0 until hidden) decoder.wh.accumulate(r, c, dz[r] * states[t][c])
            }
            for (c in 0 until hidden) {
                var sum = 0.0
                for (r in 0 until hidden) sum += decoder.wh[r, c] * dz[r]
                dState[c] = sum
            }
        }

        // The key/value projections, and what they send back into the encoder.
        for (i in h.indices) {
            for (r in 0 until hidden) {
                for (c in 0 until hidden) {
                    wk.accumulate(r, c, dKeys[i][r] * h[i][c])
                    wv.accumulate(r, c, dValues[i][r] * h[i][c])
                    dEncoder[i + 1][c] += wk[r, c] * dKeys[i][r] + wv[r, c] * dValues[i][r]
                }
            }
        }
        for (c in 0 until hidden) dEncoder[item.source.size][c] += dState[c]
        encoder.backward(item.source, encoderStates, dEncoder)
        return loss / target.size
    }

    /**
     * The attention block's gradients are derived by hand above, so they are checked against finite
     * differences rather than trusted. Returns the largest relative disagreement over a sample of
     * coordinates in every parameter — anything above ~1e-4 means the derivation is wrong.
     */
    fun gradientCheck(samplesPerParam: Int = 4, epsilon: Double = 1e-6): Double {
        val item = Seq2SeqLab.trainSet.first { it.source.size >= 3 }
        params.forEach { it.zeroGrad() }
        accumulate(item)
        val analytic = params.map { it.g.copyOf() }
        val random = Random(1)
        var worst = 0.0
        params.forEachIndexed { index, param ->
            repeat(samplesPerParam) {
                val at = random.nextInt(param.w.size)
                val original = param.w[at]
                param.w[at] = original + epsilon
                val up = lossOf(item)
                param.w[at] = original - epsilon
                val down = lossOf(item)
                param.w[at] = original
                val numeric = (up - down) / (2 * epsilon)
                val mine = analytic[index][at]
                val scale = kotlin.math.max(1e-8, kotlin.math.max(kotlin.math.abs(numeric), kotlin.math.abs(mine)))
                worst = kotlin.math.max(worst, kotlin.math.abs(numeric - mine) / scale)
            }
        }
        return worst
    }

    /**
     * Forward-only loss, for the finite-difference check. Summed rather than averaged over the
     * target, because `accumulate` leaves *summed* gradients in the params and returns the mean —
     * the first run of `gradientCheck` compared the two conventions and reported a uniform 1 − 1/T
     * disagreement, which is what a scale error looks like when it is not a derivation error.
     */
    private fun lossOf(item: Seq2SeqLab.Example): Double {
        val encoderStates = encoder.run(item.source)
        val h = (1..item.source.size).map { encoderStates[it] }
        val keys = h.map { wk.matVec(it) }
        val values = h.map { wv.matVec(it) }
        val target = item.target + intArrayOf(Seq2SeqLab.EOS)
        val inputs = intArrayOf(Seq2SeqLab.SOS) + item.target
        var state = encoderStates.last()
        var loss = 0.0
        for (t in target.indices) {
            state = decoderStep(state, inputs[t])
            val attended = attend(wq.matVec(state), keys, values)
            val p = softmaxD(logitsFrom(state, attended.context))
            loss += -ln(p[target[t]].coerceAtLeast(1e-12))
        }
        return loss
    }

    fun train(epochs: Int = EPOCHS, lr: Double = LR): AttentionSeq2Seq {
        repeat(epochs) {
            Seq2SeqLab.trainSet.forEach { item ->
                params.forEach { it.zeroGrad() }
                accumulate(item)
                params.forEach { it.adam(lr) }
            }
        }
        return this
    }

    class Decoded(val tokens: IntArray, val alignment: List<Array<DoubleArray>>) {
        val text get() = tokens.joinToString("") { Seq2SeqLab.symbolNames[it] }
    }

    /** Greedy decode, keeping the attention weights every step produced. */
    fun decode(source: IntArray): Decoded {
        val encoderStates = encoder.run(source)
        val h = (1..source.size).map { encoderStates[it] }
        val keys = h.map { wk.matVec(it) }
        val values = h.map { wv.matVec(it) }
        var state = encoderStates.last()
        var token = Seq2SeqLab.SOS
        val out = mutableListOf<Int>()
        val alignment = mutableListOf<Array<DoubleArray>>()
        repeat(Seq2SeqLab.MAX_LENGTH + 1) {
            val next = decoderStep(state, token)
            val attended = attend(wq.matVec(next), keys, values)
            alignment += attended.weights
            val p = softmaxD(logitsFrom(next, attended.context))
            val best = p.indices.maxBy { p[it] }
            if (best == Seq2SeqLab.EOS) return Decoded(out.toIntArray(), alignment)
            out += best
            state = next
            token = best
        }
        return Decoded(out.toIntArray(), alignment)
    }

    fun exactMatch(data: List<Seq2SeqLab.Example> = Seq2SeqLab.testSet) =
        data.count { decode(it.source).tokens.contentEquals(it.target) } / data.size.toDouble()

    fun exactMatchByLength(data: List<Seq2SeqLab.Example> = Seq2SeqLab.testSet): List<Pair<Int, Double>> =
        (1..Seq2SeqLab.MAX_LENGTH).mapNotNull { length ->
            val subset = data.filter { it.source.size == length }
            if (subset.isEmpty()) null
            else length to subset.count { decode(it.source).tokens.contentEquals(it.target) } / subset.size.toDouble()
        }

    /** Head `head`'s attention matrix for one source: decoder step × source position. */
    fun alignmentMatrix(source: IntArray, head: Int = 0): List<DoubleArray> =
        decode(source).alignment.map { it[head] }

    /**
     * How far each decoder step's attention mass sits from the diagonal, averaged over the test set.
     * Zero means the head has learned the alignment the task actually has; the copy task's alignment
     * is known in advance, which is what makes this scoreable at all.
     */
    fun diagonalOffset(head: Int = 0): Double {
        var total = 0.0
        var count = 0
        Seq2SeqLab.testSet.forEach { item ->
            val rows = alignmentMatrix(item.source, head)
            rows.forEachIndexed { step, weights ->
                if (step < item.source.size) {
                    val centre = weights.indices.sumOf { it * weights[it] }
                    total += kotlin.math.abs(centre - step)
                    count += 1
                }
            }
        }
        return if (count == 0) 0.0 else total / count
    }

    /** Mass the head puts on the position it should be reading, averaged over the test set. */
    fun diagonalMass(head: Int = 0): Double {
        var total = 0.0
        var count = 0
        Seq2SeqLab.testSet.forEach { item ->
            val rows = alignmentMatrix(item.source, head)
            rows.forEachIndexed { step, weights ->
                if (step < item.source.size) {
                    total += weights[step]
                    count += 1
                }
            }
        }
        return if (count == 0) 0.0 else total / count
    }

    companion object {
        const val EPOCHS = 100
        const val LR = 0.005
    }
}

// ── Masking: what self-attention is allowed to see ───────────────────────────

// ── Multi-head: what the split buys, and what it costs ───────────────────────

