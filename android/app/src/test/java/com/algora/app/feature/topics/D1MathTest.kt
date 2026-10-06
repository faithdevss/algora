package com.algora.app.feature.topics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the property the PCFG lab's copy leans on, in the pattern B7MathTest and DeepNetMathTest set:
 * the PP attaching to the verb phrase must be the parse CYK actually prefers, or the topic loses its
 * payoff while still rendering happily.
 */
class D1MathTest {

    // ── PCFG ─────────────────────────────────────────────────────────────────

    @Test
    fun `CYK prefers the VP attachment, and by the factor the copy states`() {
        val vp = PcfgLab.vpAttachment()
        val np = PcfgLab.npAttachment()
        assertEquals(0.00336, vp.probability, 1e-9)
        assertEquals(0.00224, np.probability, 1e-9)
        assertTrue("The lab narrates VP attachment as the winner", vp.probability > np.probability)
        assertEquals(1.5, PcfgLab.attachmentRatio, 1e-6)
    }
    @Test
    fun `the chart's best parse is the tree the lab draws`() {
        val best = PcfgLab.best()
        requireNotNull(best)
        assertEquals(PcfgLab.vpAttachment().probability, best.probability, 1e-12)
        assertEquals(PcfgLab.bracket(PcfgLab.vpAttachment()), PcfgLab.bracket(best))
        assertEquals(15, PcfgLab.filledCells())
        assertEquals(56, PcfgLab.splitsConsidered())
    }
    @Test
    fun `rules sharing a left-hand side sum to one`() {
        val byLabel = (PcfgLab.binary.map { it.first to it.third } + PcfgLab.lexical.map { it.first to it.third })
            .groupBy({ it.first }, { it.second })
        byLabel.forEach { (label, probabilities) ->
            assertEquals("rules for $label", 1.0, probabilities.sum(), 1e-9)
        }
    }

}
