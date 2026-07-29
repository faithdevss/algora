package com.algora.app.feature.topics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * D5's guard. Four of these exist because the probe disagreed with the copy that was about to be
 * written: the prompt pool had to be reordered before the version space narrowed interestingly, the
 * Tree of Thoughts evaluator turned out to be so weak that width alone rescues it (and the *better*
 * evaluator costs more, not less), a plain k-NN graph came apart into three components, and
 * self-consistency confidence failed to separate supported questions from hallucinated ones.
 */
class D5MathTest {

    // ── Prompt engineering ───────────────────────────────────────────────────

    @Test
    fun `the version space narrows one demonstration at a time`() {
        val sizes = (0..PromptLab.pool.size).map { PromptLab.consistent(PromptLab.pool.take(it)).size }
        assertEquals(listOf(5, 4, 3, 2, 1, 1, 1), sizes)
    }

    @Test
    fun `two reasonable demonstrations answer confidently and wrongly`() {
        val two = PromptLab.pool.take(2)
        assertEquals(3, PromptLab.consistent(two).size)
        val posterior = PromptLab.prediction(two)
        // 'a' wins the vote 2-1 where the true answer is 'y'.
        assertEquals('a', posterior.maxByOrNull { it.value }!!.key)
        assertEquals('y', PromptLab.label(PromptLab.query))
        assertFalse(PromptLab.predictedCorrectly(two))
    }

    @Test
    fun `three demonstrations leave a genuine tie`() {
        val three = PromptLab.pool.take(3)
        assertEquals(2, PromptLab.consistent(three).size)
        assertEquals(setOf(0.5), PromptLab.prediction(three).values.toSet())
        assertFalse(PromptLab.predictedCorrectly(three))
        assertTrue(PromptLab.predictedCorrectly(PromptLab.pool.take(4)))
    }

    @Test
    fun `selection matters more than count`() {
        val identifying = PromptLab.identifyingPairs()
        assertEquals(15, PromptLab.allPairs().size)
        assertEquals(12, identifying.size)
        // The three that fail are exactly the three words the reading path opens with.
        val failing = PromptLab.allPairs().filterNot { it in identifying }
        assertEquals(setOf("banana", "adage", "otter"), failing.flatMap { listOf(it.first, it.second) }.toSet())
        assertEquals(1, PromptLab.consistent(listOf("level", "sonar")).size)
    }

    @Test
    fun `an instruction is worth three demonstrations`() {
        val eliminated = PromptLab.instructionEliminates()
        assertEquals(3, eliminated.size)
        assertEquals(3, PromptLab.demosToEliminate(eliminated))
    }

    // ── Chain of thought ─────────────────────────────────────────────────────

    @Test
    fun `decomposition wins only while the chain is short`() {
        assertEquals(7, CotLab.breakEvenSteps())
        assertTrue(CotLab.chainAccuracy(7) > CotLab.directAccuracy)
        assertTrue(CotLab.chainAccuracy(8) < CotLab.directAccuracy)
    }

    @Test
    fun `plurality voting is a probability distribution`() {
        // Sanity on the exact enumeration: a single sample is just p.
        listOf(0.3, 0.55, 0.9).forEach { p ->
            assertEquals(p, CotLab.pluralityAccuracy(p, 1, 3), 1e-9)
        }
    }

    @Test
    fun `voting helps when errors scatter and hurts when they do not`() {
        val scattered = CotLab.votingCurve(0.40, 4)
        val systematic = CotLab.votingCurve(0.40, 1)
        assertTrue("scattered errors: voting should climb", scattered.last().second > 0.40)
        assertTrue(
            "If this ever stops falling, the topic's central correction is false: $systematic",
            systematic.last().second < 0.40,
        )
        // The direction reverses on identical p and identical vote.
        assertTrue(scattered.last().second > systematic.last().second + 0.3)
    }

    @Test
    fun `agreement is highest exactly where accuracy is lowest`() {
        val confident = CotLab.modalShare(0.12, 5, 1)
        val accurate = CotLab.pluralityAccuracy(0.12, 5, 1)
        assertTrue("reported agreement $confident", confident > 0.85)
        assertTrue("actual accuracy $accurate", accurate < 0.05)
    }

    // ── Tree of Thoughts ─────────────────────────────────────────────────────

    @Test
    fun `the puzzle is solvable and small enough to check exhaustively`() {
        val (expanded, solutions) = TotLab.exhaustive()
        assertEquals(4565, expanded)
        assertEquals(9, solutions)
        assertTrue(TotLab.reachable(TotLab.puzzle))
    }

    @Test
    fun `the cheap evaluator is measurably bad`() {
        val (firstSolvable, solvableInTopFive, frontier) = TotLab.evaluatorQuality(5, depth = 1)
        assertEquals(36, frontier)
        assertEquals(8, firstSolvable)
        assertEquals(0, solvableInTopFive)
    }

    @Test
    fun `width substitutes for evaluator quality`() {
        assertFalse("greedy must fail, or the topic has no argument", TotLab.beam(1, depth = 1).solved)
        assertFalse(TotLab.beam(5, depth = 1).solved)
        assertEquals(8, TotLab.widthNeeded(1))
        assertTrue(TotLab.beam(8, depth = 1).solved)
        assertEquals(1, TotLab.widthNeeded(2))
    }

    @Test
    fun `the better evaluator is the more expensive lever here`() {
        val cheapAtWidth = TotLab.beam(TotLab.widthNeeded(1), depth = 1)
        val deepAtWidthOne = TotLab.beam(1, depth = 2)
        assertEquals(180, cheapAtWidth.evaluatorCalls)
        assertEquals(702, deepAtWidthOne.evaluatorCalls)
        assertTrue(
            "If this inverts, the frame claiming a better evaluator costs more is false",
            deepAtWidthOne.evaluatorCalls > cheapAtWidth.evaluatorCalls,
        )
    }

    // ── Vector databases ─────────────────────────────────────────────────────

    @Test
    fun `IVF trades recall for comparisons at a cell boundary`() {
        val (one, oneCost) = VectorDbLab.ivfSearch(1)
        val (two, twoCost) = VectorDbLab.ivfSearch(2)
        assertEquals(0.6, VectorDbLab.recall(one), 1e-9)
        assertEquals(1.0, VectorDbLab.recall(two), 1e-9)
        assertTrue("nprobe 1 must be cheaper than nprobe 2", oneCost < twoCost)
        assertTrue("both must beat brute force", twoCost < VectorDbLab.corpusSize)
    }

    @Test
    fun `a plain k-NN graph is disconnected and the long links fix it`() {
        assertTrue(VectorDbLab.componentCount(VectorDbLab.knnGraph) > 1)
        assertEquals(1, VectorDbLab.componentCount(VectorDbLab.smallWorldGraph))
        val stranded = VectorDbLab.strandedEntry()
        listOf(1, 2, 4, 8).forEach { ef ->
            assertEquals(
                "ef $ef must not rescue a search that has no path to the answer",
                0.0,
                VectorDbLab.recall(VectorDbLab.graphSearch(ef, VectorDbLab.knnGraph, entry = stranded).found),
                1e-9,
            )
        }
        assertEquals(
            1.0,
            VectorDbLab.recall(VectorDbLab.graphSearch(4, VectorDbLab.smallWorldGraph, entry = stranded).found),
            1e-9,
        )
    }

    @Test
    fun `quantization fails when its step is coarser than the neighbour spread`() {
        val spread = VectorDbLab.neighbourSpread()
        assertTrue("16 levels must be coarser than the ranking it has to preserve", VectorDbLab.quantizationStep(16) > 5 * spread)
        assertTrue(VectorDbLab.quantizedRecall(16) < 0.5)
        assertEquals(1.0, VectorDbLab.quantizedRecall(64), 1e-9)
        assertEquals(768, VectorDbLab.bytesPerVector(768, 8))
        assertEquals(3072, VectorDbLab.bytesPerVector(768, 32))
    }

    @Test
    fun `contrast collapses with dimension`() {
        val contrasts = listOf(2, 8, 32, 128).map { VectorDbLab.contrastRatio(it) }
        assertEquals(contrasts.sortedDescending(), contrasts)
        assertTrue("d=128 contrast ${contrasts.last()}", contrasts.last() < 1.0)
    }

    // ── Retrieval and ReAct ──────────────────────────────────────────────────

    @Test
    fun `one retrieval cannot reach the second hop`() {
        val rank = RetrievalLab.rankOf(ReActLab.question, ReActLab.hop2PassageId)
        assertTrue("answer passage must be out of reach at top-3, was rank $rank", rank > 3)
        val (_, contains) = ReActLab.singleShot()
        assertFalse(contains)
    }

    @Test
    fun `the rewritten query puts the answer first`() {
        assertEquals(1, RetrievalLab.rankOf("Linux kernel written language", ReActLab.hop1PassageId))
        assertEquals(1, RetrievalLab.rankOf("C language first released", ReActLab.hop2PassageId))
        assertEquals(3, ReActLab.trajectory.size)
        assertTrue(ReActLab.trajectory.last().observation.contains(ReActLab.groundedAnswer))
    }

    // ── Agents ───────────────────────────────────────────────────────────────

    @Test
    fun `the transcript grows linearly and the bill grows faster`() {
        assertEquals(700, AgentLab.finalContext())
        assertEquals(1970, AgentLab.billedTokens())
        assertTrue("billing multiple ${AgentLab.billingMultiple()}", AgentLab.billingMultiple() > 2.5)
    }

    @Test
    fun `a retry costs far more than its error message`() {
        val errorTokens = AgentLab.trajectory.filter { it.failed }.sumOf { it.tokens }
        assertEquals(647, AgentLab.retryOverhead())
        assertTrue(
            "the point is that the overhead exceeds the entries themselves ($errorTokens)",
            AgentLab.retryOverhead() > 5 * errorTokens,
        )
    }

    @Test
    fun `batching independent calls beats issuing them in sequence`() {
        assertEquals(267, AgentLab.sequentialLatency())
        assertEquals(240, AgentLab.parallelLatency())
        assertTrue(AgentLab.parallelLatency() < AgentLab.sequentialLatency())
    }

    // ── Hallucination mitigation ─────────────────────────────────────────────

    @Test
    fun `confidence does not separate supported questions from hallucinated ones`() {
        assertFalse(
            "If this ever separates, the topic's headline finding is false",
            HallucinationLab.confidenceSeparates(),
        )
        assertEquals(4, HallucinationLab.confidentlyWrong().size)
        assertTrue("ECE ${HallucinationLab.expectedCalibrationError()}", HallucinationLab.expectedCalibrationError() > 0.2)
    }

    @Test
    fun `grounding beats a confidence threshold at equal coverage`() {
        val (_, coverage, selective) = HallucinationLab.bestThreshold()
        assertEquals(0.6, coverage, 1e-9)
        assertEquals(0.6, HallucinationLab.groundedCoverage(), 1e-9)
        assertTrue("threshold selective accuracy $selective", selective < 0.7)
        assertTrue(HallucinationLab.groundedSelectiveAccuracy() > 0.95)
    }

    @Test
    fun `the control run names the mechanism`() {
        val scattered = HallucinationLab.scatteredQuestions
        assertTrue(HallucinationLab.confidenceSeparates(scattered))
        assertEquals(0, HallucinationLab.confidentlyWrong(scattered).size)
        val (_, coverage, selective) = HallucinationLab.bestThreshold(scattered)
        assertEquals(0.6, coverage, 1e-9)
        assertEquals(
            "with scattered errors, thresholding should match grounding",
            HallucinationLab.groundedSelectiveAccuracy(scattered),
            selective,
            1e-9,
        )
    }
}
