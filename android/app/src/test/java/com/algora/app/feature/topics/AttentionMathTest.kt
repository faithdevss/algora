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










}
