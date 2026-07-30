package com.algora.app.feature.topics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * C6's attention guard. The cross-attention model here is the experiment C5's encoder-decoder topic
 * ends by pointing at, so the ordering it produces is load-bearing for two topics: if attention
 * stops beating the fixed vector, both sets of copy become false.
 *
 * The first test is the one that matters most. Every gradient in the attention block is derived by
 * hand, and a wrong derivation still trains — just to a worse optimum — so it is checked against
 * finite differences rather than inferred from the fact that the loss goes down.
 */
class AttentionMathTest {

    @Test
    fun `the hand-derived attention gradients match finite differences`() {
        // The check itself caught a scale error first: gradients are summed over the target while
        // the returned loss was averaged, which reported a uniform 1 - 1/T disagreement in every
        // parameter block. A derivation error looks different — one block wrong, the rest exact.
        listOf(1, 4).forEach { heads ->
            val worst = AttentionSeq2Seq(Seq2SeqLab.HIDDEN, heads = heads, seed = 3).gradientCheck()
            assertTrue("worst relative error at $heads head(s) was $worst", worst < 1e-3)
        }
    }

    @Test
    fun `cross-attention beats the fixed context vector on the same task`() {
        val fixed = AttentionLab.comparison.first { it.name == "fixed vector" }
        val attention = AttentionLab.comparison.first { it.name == "cross-attention" }
        assertEquals(0.292, fixed.exactMatch, 0.03)
        assertEquals(0.867, attention.exactMatch, 0.04)
        assertTrue(
            "if this flips, both this topic's and the encoder-decoder topic's copy are false",
            attention.exactMatch > fixed.exactMatch * 2,
        )
    }

    @Test
    fun `and it is not the parameter count`() {
        // The widened fixed-vector model is the control the comparison would be worthless without.
        val matched = AttentionLab.comparison.first { it.name == "fixed vector, widened to match" }
        val attention = AttentionLab.comparison.first { it.name == "cross-attention" }
        assertTrue(
            "the matched model must be within 5% of the attention model's size",
            kotlin.math.abs(matched.parameters - attention.parameters) < attention.parameters / 20,
        )
        assertEquals(0.300, matched.exactMatch, 0.04)
        assertTrue("capacity is not what attention added", attention.exactMatch > matched.exactMatch * 2)
    }

    @Test
    fun `attention removes the collapse with length, not just the average`() {
        val matched = AttentionLab.comparison.first { it.name == "fixed vector, widened to match" }.byLength.toMap()
        val attention = AttentionLab.comparison.first { it.name == "cross-attention" }.byLength.toMap()
        assertTrue("the widened fixed vector is still gone by length 4", matched.getValue(4) < 0.1)
        assertTrue("attention is not", attention.getValue(4) > 0.7)
        assertTrue("nor at length 5", attention.getValue(5) > 0.7)
    }

    @Test
    fun `the alignment is learned and it is the right one`() {
        val model = AttentionLab.single
        assertTrue("mass on the position each step copies", model.diagonalMass() > 0.85)
        assertTrue("and it sits on that position, not near it", model.diagonalOffset() < 0.3)
    }

    @Test
    fun `masking is a counting argument`() {
        assertEquals(36, MaskLab.pairs(6))
        assertEquals(21, MaskLab.causalPairs(6))
        assertEquals(24, MaskLab.crossPairs(6, 4))
        assertEquals(56, MaskLab.scoresWithoutCache(6))
        assertEquals(21, MaskLab.scoresWithCache(6))
        assertEquals(18_874_368L, MaskLab.kvCacheValues(12, 768, 1024))
    }

    @Test
    fun `the head split costs no parameters`() {
        assertEquals(16_384, MultiHeadLab.attentionParameters())
        assertEquals(AttentionLab.single.parameterCount, AttentionLab.multi.parameterCount)
        MultiHeadLab.headCounts.forEach {
            assertEquals(MultiHeadLab.MODEL_DIM, MultiHeadLab.headDim(it) * it)
        }
    }

    @Test
    fun `one head cannot read two positions at once`() {
        // The argument the multi-head topic is built on, in place of the rank story the plan
        // assumed. Two heads are exact by construction; one head has to blend.
        val read = MultiHeadLab.simultaneousRead()
        assertEquals("the best a single head can do is split the mass evenly", 0.5, read.bestAlpha, 0.02)
        assertEquals(0.697, read.singleHeadError, 0.02)
        assertEquals(0.0, read.twoHeadError, 1e-12)
    }

    @Test
    fun `the rank ceiling bites when heads get thin, not when there is one head`() {
        val combined = MultiHeadLab.combinedPatterns()
        assertEquals("four patterns over 12 positions", 12, MultiHeadLab.combinedRank())
        assertEquals("a single full-width head can represent them exactly", 0.0, MultiHeadLab.rankError(combined, 64), 1e-9)
        assertEquals("eight heads cannot", 0.134, MultiHeadLab.rankError(combined, MultiHeadLab.headDim(8)), 0.01)
        assertEquals("sixteen heads much less so", 0.371, MultiHeadLab.rankError(combined, MultiHeadLab.headDim(16)), 0.01)
        assertTrue(
            "error must grow as heads get thinner, or the frame's argument is backwards",
            MultiHeadLab.headCounts.map { MultiHeadLab.rankError(combined, MultiHeadLab.headDim(it)) }
                .zipWithNext().all { (a, b) -> b >= a - 1e-9 },
        )
    }

    @Test
    fun `four heads do not help on a task with one alignment`() {
        // Reported as measured. The plan assumed multi-head would win here; it does not, and the
        // simultaneous-read test above is why that is the expected result rather than a failure.
        val single = AttentionLab.single.exactMatch()
        val multi = AttentionLab.multi.exactMatch()
        assertTrue("both models work", single > 0.7 && multi > 0.7)
        assertTrue("and the extra heads buy nothing on this task", multi <= single + 0.02)
    }

    @Test
    fun `the four heads still learned different things`() {
        val masses = (0..3).map { AttentionLab.multi.diagonalMass(it) }
        assertTrue("one head carries the alignment", masses.max() > 0.85)
        assertTrue("and not all of them do", masses.min() < 0.6)
    }
}
