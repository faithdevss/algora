package com.algora.app.feature.topics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * C5's guard. The four RNN labs train real networks, so most of what the copy says is a measurement
 * rather than arithmetic — and a measurement can be reproduced in the wrong direction by a re-tune
 * without anything throwing. The tests below are written in two layers: the digits the copy quotes,
 * and the *orderings* it argues from. Several of the orderings are marked "if this flips, the
 * topic's central claim is false", because each of those frames would read perfectly well backwards.
 */
class RnnMathTest {

    // ── BPTT ─────────────────────────────────────────────────────────────────

    @Test
    fun `the cue is the only informative token in the sequence`() {
        BpttLab.trainSet.forEach { item ->
            assertEquals("the label is the first token", item.label, item.tokens[0])
            assertTrue("every other token is noise", item.tokens.drop(1).all { it >= 2 })
        }
        assertEquals(BpttLab.LENGTH, BpttLab.trainSet.first().tokens.size)
    }

    @Test
    fun `the gradient decays geometrically on the way back`() {
        val g = BpttLab.stateGradient
        assertEquals(0.4800, g[BpttLab.LENGTH - 1], 0.01)
        assertEquals(0.0085, g[0], 0.001)
        assertTrue(
            "‖dL/dh‖ must fall monotonically toward the input",
            (1 until BpttLab.LENGTH).all { g[it] > g[it - 1] },
        )
        assertEquals("56x across nine steps", 56.0, g[BpttLab.LENGTH - 1] / g[0], 4.0)
    }

    @Test
    fun `the first step contributes nothing to the recurrent matrix`() {
        // h0 is zero by definition, so dL/dWh at step 1 is an outer product with a zero vector. The
        // cue reaches Wx and nothing else — worth pinning, because it is the mechanism behind the
        // surprise two tests below.
        assertEquals(0.0, BpttLab.matrixShare[0], 1e-12)
        assertTrue(BpttLab.matrixShare[BpttLab.LENGTH - 1] > 0.4)
    }

    @Test
    fun `a truncated gradient is nearly identical to the full one`() {
        val agreement = BpttLab.gradientAgreement.toMap()
        assertEquals(0.943, agreement.getValue(1), 0.01)
        assertEquals(0.990, agreement.getValue(3), 0.01)
        assertEquals(0.9997, agreement.getValue(9), 0.001)
        assertTrue(
            "cosine agreement has to rise with the window, or the sweep means nothing",
            BpttLab.windows.zipWithNext().all { (a, b) -> agreement.getValue(a) <= agreement.getValue(b) + 1e-6 },
        )
    }

    @Test
    fun `a truncated gradient can be larger than the full one`() {
        // Terms that partially cancel are dropped along with everything else, so the magnitude is
        // not a subset bound. The copy says this out loud; without it "recovers 99% of the gradient"
        // reads as if the truncated vector were a piece of the full one.
        val share = BpttLab.magnitudeShare.toMap()
        assertTrue("k=3 recovers more magnitude than the full gradient has", share.getValue(3) > 1.0)
    }

    @Test
    fun `gradient similarity does not predict what gets learned`() {
        // The claim the batch turns on: at k=3 the truncated gradient is 99% aligned with the full
        // one and the model still fails, while k=9 — which never touches the step the cue enters —
        // succeeds every time. If this flips, the topic's central claim is false.
        val reliability = BpttLab.reliability().associateBy { it.window }
        assertEquals("k=9 solves it on every seed", 3, reliability.getValue(9).solved)
        assertEquals("k=10 solves it on every seed", 3, reliability.getValue(10).solved)
        assertTrue("k=1 never solves it", reliability.getValue(1).solved == 0)
        assertTrue(
            "the short windows must stay unreliable — at most one seed in three",
            listOf(3, 5, 7).all { reliability.getValue(it).solved <= 1 },
        )
        assertTrue(
            "and the sweep must not be monotone in the window, which is the whole finding",
            reliability.getValue(5).mean < reliability.getValue(3).mean,
        )
    }

    @Test
    fun `truncation is what buys the memory saving`() {
        assertEquals(120, BpttLab.storedActivations(BpttLab.LENGTH))
        assertEquals(36, BpttLab.storedActivations(3))
    }

    // ── Bidirectional ────────────────────────────────────────────────────────

    @Test
    fun `the corpus is ambiguous from the left and unambiguous from both sides`() {
        assertEquals(59, BiRnnLab.tokenCount)
        assertEquals(12, BiRnnLab.leftAmbiguous.size)
        assertEquals(
            "no position is ambiguous once the whole sentence is visible — otherwise the bi model's " +
                "ceiling would be below 1.0 and the comparison would be measuring something else",
            0, BiRnnLab.fullyAmbiguous.size,
        )
    }

    @Test
    fun `the left-to-right ceiling is enumerated, not trained`() {
        assertEquals(53.0 / 59.0, BiRnnLab.forwardCeiling, 1e-9)
    }

    @Test
    fun `the forward tagger lands exactly on that ceiling`() {
        assertEquals(BiRnnLab.forwardCeiling, BiRnnLab.forwardTagger.accuracy, 1e-9)
        assertEquals(0.5, BiRnnLab.forwardTagger.accuracyAt(BiRnnLab.leftAmbiguous), 1e-9)
    }

    @Test
    fun `the bidirectional tagger closes the gap completely`() {
        assertEquals(1.0, BiRnnLab.biTagger.accuracy, 1e-9)
        assertEquals(1.0, BiRnnLab.biTagger.accuracyAt(BiRnnLab.leftAmbiguous), 1e-9)
    }

    @Test
    fun `the forward tagger is exactly undecided where the prefix is shared`() {
        // Both readings of "the horse raced past the barn(, fell)" share their first three words, so
        // the forward model produces one distribution for both and it sits on the coin flip.
        val short = BiRnnLab.corpus[4].words
        val long = BiRnnLab.corpus[5].words
        val a = BiRnnLab.forwardTagger.tagDistribution(short, 2)
        val b = BiRnnLab.forwardTagger.tagDistribution(long, 2)
        val verb = BiRnnLab.tags.indexOf("VERB")
        val part = BiRnnLab.tags.indexOf("PART")
        assertEquals("identical prefix, identical state, identical prediction", a[verb], b[verb], 1e-9)
        assertEquals(0.5, a[verb], 0.02)
        assertEquals(0.5, a[part], 0.02)
        assertTrue("the bidirectional model separates them", BiRnnLab.biTagger.tagDistribution(short, 2)[verb] > 0.9)
        assertTrue(BiRnnLab.biTagger.tagDistribution(long, 2)[part] > 0.9)
    }

    @Test
    fun `reading both ways costs about double the parameters`() {
        assertEquals(903, BiRnnLab.forwardTagger.parameterCount)
        assertEquals(1799, BiRnnLab.biTagger.parameterCount)
    }

    // ── Encoder-decoder ──────────────────────────────────────────────────────

    @Test
    fun `the copy task is the weakest possible demand on the model`() {
        Seq2SeqLab.trainSet.forEach { assertTrue(it.target.contentEquals(it.source)) }
        assertEquals(571, Seq2SeqLab.forwardFed.parameterCount)
        assertEquals(
            "both models are the same size — the only difference is the order the encoder reads",
            Seq2SeqLab.forwardFed.parameterCount, Seq2SeqLab.reverseFed.parameterCount,
        )
    }

    @Test
    fun `accuracy falls with source length at a fixed context size`() {
        val byLength = Seq2SeqLab.forwardFed.exactMatchByLength(Seq2SeqLab.testSet).toMap()
        assertEquals(1.0, byLength.getValue(1), 1e-9)
        assertTrue("length 6 is out of reach", byLength.getValue(6) < 0.1)
        assertTrue("and so is length 4", byLength.getValue(4) < 0.1)
    }

    @Test
    fun `the context vector holds what the encoder read last`() {
        // The mechanism behind the reversal trick, measured by a linear probe on a frozen encoder:
        // the forward-fed vector recovers the *last* source position best, the reverse-fed one the
        // first. If this flips, the encoder-decoder topic's central claim is false.
        val forward = Seq2SeqLab.probeProfile(Seq2SeqLab.forwardFed).toMap()
        val reverse = Seq2SeqLab.probeProfile(Seq2SeqLab.reverseFed).toMap()
        assertTrue("forward-fed recovers position 6 best", forward.getValue(6) > forward.getValue(1))
        assertTrue("reverse-fed recovers position 1 best", reverse.getValue(1) > reverse.getValue(6))
        assertEquals(0.85, forward.getValue(6), 0.08)
        assertEquals(0.98, reverse.getValue(1), 0.05)
    }

    @Test
    fun `reversing the source doubles exact match at no cost`() {
        val forward = Seq2SeqLab.forwardFed.exactMatch(Seq2SeqLab.testSet)
        val reverse = Seq2SeqLab.reverseFed.exactMatch(Seq2SeqLab.testSet)
        assertEquals(0.292, forward, 0.03)
        assertEquals(0.542, reverse, 0.03)
        assertTrue("Sutskever's trick has to reproduce, or the frame is decoration", reverse > forward * 1.5)
    }

    @Test
    fun `the bottleneck can be stated as a capacity`() {
        assertEquals(15.5, Seq2SeqLab.sourceBits(6), 0.05)
        assertEquals(2.585, Seq2SeqLab.sourceBits(1), 0.005)
    }

    // ── Seq2seq decoding ─────────────────────────────────────────────────────

    @Test
    fun `beam search finds more probable sequences`() {
        val sweep = Seq2SeqLab.beamSweep(Seq2SeqLab.forwardFed, listOf(1, 3, 10)).associateBy { it.width }
        assertTrue(
            "mean log-probability must rise with the beam, which is all beam search promises",
            sweep.getValue(1).meanLogProbability < sweep.getValue(3).meanLogProbability &&
                sweep.getValue(3).meanLogProbability <= sweep.getValue(10).meanLogProbability,
        )
        assertEquals(-2.284, sweep.getValue(1).meanLogProbability, 0.1)
        assertEquals(-1.994, sweep.getValue(10).meanLogProbability, 0.1)
    }

    @Test
    fun `and it buys almost no accuracy, because the errors are not search errors`() {
        // The seq2seq topic's central claim. 84 of the 120 outputs are wrong because the model gives
        // its own wrong answer the higher probability — no width fixes those.
        val narrow = Seq2SeqLab.errorSplit(Seq2SeqLab.forwardFed, 1)
        val wide = Seq2SeqLab.errorSplit(Seq2SeqLab.forwardFed, 10)
        assertEquals(120, narrow.total)
        assertTrue("search errors are the rare kind here", narrow.searchError <= 2)
        assertEquals("and a wide beam removes them entirely", 0, wide.searchError)
        assertTrue("model errors dominate", wide.modelError > 5 * wide.correct / 3)
        assertTrue(
            "widening the beam moves accuracy by at most a couple of sequences",
            wide.correct - narrow.correct <= 3,
        )
    }

    @Test
    fun `the better model is the one that removes model errors`() {
        val forward = Seq2SeqLab.errorSplit(Seq2SeqLab.forwardFed, 5)
        val reverse = Seq2SeqLab.errorSplit(Seq2SeqLab.reverseFed, 5)
        assertTrue(
            "feeding the source backwards fixes far more than any beam width does",
            reverse.correct - forward.correct > 20,
        )
    }

    @Test
    fun `length normalization changes outputs without buying accuracy here`() {
        // Reported rather than skipped: on a task whose output length is fixed by the source, the
        // famous fix is a no-op. The copy names where it does matter instead of implying it always
        // does.
        val effect = Seq2SeqLab.normalizationEffect(Seq2SeqLab.forwardFed, 5)
        assertTrue("it does change some outputs", effect.changed > 5)
        assertTrue("longer, as advertised", effect.normalizedLength > effect.plainLength)
        assertTrue("but not better", effect.exactMatchDelta <= 0.01)
    }

    @Test
    fun `teacher forcing scores higher than the model's own reading`() {
        val teacher = Seq2SeqLab.forwardFed.teacherForcedAccuracy(Seq2SeqLab.testSet)
        val free = Seq2SeqLab.forwardFed.freeRunningAccuracy(Seq2SeqLab.testSet)
        assertTrue("exposure bias, measured on one trained model", teacher > free + 0.1)
        assertEquals(0.495, teacher, 0.04)
        assertEquals(0.324, free, 0.04)
    }
}
