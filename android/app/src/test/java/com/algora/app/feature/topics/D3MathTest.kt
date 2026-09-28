package com.algora.app.feature.topics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * D3's guard. Unlike the earlier batches' math tests, most of what this pins is the output of real
 * SGD — so the assertions are about *relations that must hold* (king nearer queen than dog, the
 * analogy landing on queen, same-sense pairs beating cross-sense pairs) with tolerances wide enough
 * to survive a re-tune, plus exact pins on everything counted rather than trained.
 *
 * The relational form is deliberate: a lab that trains and then narrates the result is only honest
 * if the narration fails when the training stops supporting it.
 */
class D3MathTest {

    // ── Corpus and counting ──────────────────────────────────────────────────

    @Test
    fun `the corpus is the size the copy quotes`() {
        assertEquals(26, Word2VecLab.vocab.size)
        assertEquals(102, Word2VecLab.sentences.sumOf { it.size })
        assertEquals(288, Word2VecLab.pairs.size)
        assertEquals(288, Word2VecLab.skipGramUpdatesPerEpoch())
        assertEquals(102, Word2VecLab.cbowUpdatesPerEpoch())
        assertEquals(2, Word2VecLab.counts.getValue("monarch"))
        assertEquals(5, Word2VecLab.counts.getValue("king"))
    }

    @Test
    fun `the three-quarter power flattens the frequent word and lifts the rare one`() {
        assertTrue(Word2VecLab.noiseShare("the") < Word2VecLab.unigramShare("the"))
        assertTrue(Word2VecLab.noiseShare("monarch") > Word2VecLab.unigramShare("monarch"))
        assertEquals(0.265, Word2VecLab.unigramShare("the"), 0.005)
        assertEquals(0.180, Word2VecLab.noiseShare("the"), 0.005)
    }

    @Test
    fun `negative sampling is the cost saving the copy claims`() {
        assertEquals(416, Word2VecLab.softmaxCost())
        assertEquals(96, Word2VecLab.negativeSamplingCost())
        assertEquals(4.33, Word2VecLab.softmaxCost().toDouble() / Word2VecLab.negativeSamplingCost(), 0.01)
    }

    // ── What training actually produces ──────────────────────────────────────

    @Test
    fun `skip-gram separates related words from unrelated ones`() {
        val sg = Word2VecLab.skipGram
        val related = Word2VecLab.similarity(sg, "king", "queen")
        val unrelated = Word2VecLab.similarity(sg, "king", "dog")
        assertTrue("king·queen = $related must clear 0.8", related > 0.8f)
        assertTrue("king·dog = $unrelated must stay below 0.5", unrelated < 0.5f)
        assertEquals("queen", Word2VecLab.nearest(sg, "king", 1).first().first)
    }

    @Test
    fun `the analogy lands on queen for both trained models`() {
        val sg = Word2VecLab.skipGram
        val target = FloatArray(Word2VecLab.dim) {
            sg.vector("king")[it] - sg.vector("man")[it] + sg.vector("woman")[it]
        }
        val best = Word2VecLab.vocab
            .filter { it !in listOf("king", "man", "woman") }
            .maxByOrNull { cosineOf(target, sg.vector(it)) }
        assertEquals("skip-gram analogy", "queen", best)

        val glove = GloveLab.model
        assertEquals("GloVe analogy", "queen", GloveLab.analogy(glove, "king", "man", "woman").first().first)
    }

    @Test
    fun `both objectives converge and CBOW reaches the lower loss on this corpus`() {
        val sg = Word2VecLab.skipGram
        val cbow = Word2VecLab.cbow
        assertTrue(sg.losses.last() < sg.losses.first())
        assertTrue(cbow.losses.last() < cbow.losses.first())
        assertTrue(
            "CBOW predicts one word from an averaged window — the easier problem",
            cbow.losses.last() < sg.losses.last(),
        )
    }

    @Test
    fun `CBOW is the smoother model and skip-gram the sharper one`() {
        val sg = Word2VecLab.skipGram
        val cbow = Word2VecLab.cbow
        // Both find the pair; CBOW also pulls unrelated-but-co-occurring words closer, so its
        // related-minus-unrelated contrast is the smaller of the two. This is the frame's claim.
        assertTrue(Word2VecLab.similarity(cbow, "king", "man") > Word2VecLab.similarity(sg, "king", "man"))
        assertTrue(Word2VecLab.contrast(sg) > Word2VecLab.contrast(cbow))
    }

    @Test
    fun `the rare word result is the one the copy reports, not the one folklore expects`() {
        // Deliberately pinned: the topic says CBOW places "monarch" nearer "king" here and explains
        // why the published claim points the other way. If a re-tune flips this, the copy is wrong.
        val sgScore = Word2VecLab.similarity(Word2VecLab.skipGram, "monarch", "king")
        val cbowScore = Word2VecLab.similarity(Word2VecLab.cbow, "monarch", "king")
        assertTrue("sg $sgScore, cbow $cbowScore", cbowScore > sgScore)
    }

    // ── GloVe ────────────────────────────────────────────────────────────────

    @Test
    fun `the co-occurrence ratios discriminate where the probabilities do not`() {
        assertEquals(8.64, GloveLab.ratio("solid"), 0.01)
        assertEquals(0.085, GloveLab.ratio("gas"), 0.005)
        assertEquals(1.36, GloveLab.ratio("water"), 0.01)
        assertEquals(0.94, GloveLab.ratio("fashion"), 0.01)
        // The paper's argument in one assertion: related-to-both and related-to-neither both sit
        // near 1, while the discriminating words are an order of magnitude away in either direction.
        assertTrue(GloveLab.ratio("solid") > 5)
        assertTrue(GloveLab.ratio("gas") < 0.2)
        assertTrue(abs(GloveLab.ratio("water") - 1) < 0.5 && abs(GloveLab.ratio("fashion") - 1) < 0.5)
    }

    @Test
    fun `the matrix is sparse and the weighting damps rare pairs`() {
        assertEquals(193, GloveLab.nonZeroEntries())
        assertEquals(676, GloveLab.totalCells())
        assertEquals(0.714, GloveLab.sparsity(), 0.005)
        assertTrue(GloveLab.weight(GloveLab.cooccur("the", "king")) > GloveLab.weight(GloveLab.cooccur("king", "crown")))
        assertEquals(0f, GloveLab.weight(0f), 1e-9f)
        assertEquals(1f, GloveLab.weight(GloveLab.xMax * 2), 1e-9f)
    }

    @Test
    fun `the fit converges and puts queen nearest king`() {
        val model = GloveLab.model
        assertTrue(model.losses.last() < model.losses.first() / 10)
        assertEquals("queen", GloveLab.nearest(model, "king", 1).first().first)
        assertTrue(GloveLab.similarity(model, "king", "queen") > 0.8f)
    }

    @Test
    fun `the projection the lab plots stays inside the canvas`() {
        val projection = GloveLab.project2D(GloveLab.model)
        assertEquals(GloveLab.vocab.size, projection.size)
        assertTrue(projection.all { it.first in 0f..1f && it.second in 0f..1f })
        // A degenerate projection — everything on one line — is what a 2-D fit produced before the
        // model was widened to 8 dimensions, and it made every neighbour list meaningless.
        val xs = projection.map { it.first }
        val ys = projection.map { it.second }
        assertTrue((xs.max() - xs.min()) > 0.5f && (ys.max() - ys.min()) > 0.5f)
    }

    // ── FastText ─────────────────────────────────────────────────────────────

    @Test
    fun `subword decomposition is the shape the copy describes`() {
        val grams = FastTextLab.subwords("king")
        assertEquals(11, grams.size)
        assertTrue("<ki" in grams && "ng>" in grams && "<king>" in grams && "king" in grams)
        assertEquals(299, FastTextLab.subwordVocabularySize())
    }

    @Test
    fun `unseen words get vectors that land near their morphological relatives`() {
        val model = FastTextLab.model
        FastTextLab.unseen.forEach { assertTrue("$it should be OOV", FastTextLab.isOov(it)) }

        val kings = FastTextLab.similarityToKnown(model, "kings", "king")
        val kingsToDog = FastTextLab.similarityToKnown(model, "kings", "dog")
        assertTrue("kings·king = $kings", kings > 0.9f)
        assertTrue("kings·dog = $kingsToDog must be well below kings·king", kings - kingsToDog > 0.3f)

        val monarchy = FastTextLab.similarityToKnown(model, "monarchy", "king")
        assertTrue("monarchy·king = $monarchy", monarchy > 0.6f)
        assertTrue(monarchy > FastTextLab.similarityToKnown(model, "monarchy", "dog"))

        val (known, total) = model.coverage("kings")
        assertEquals(6, known)
        assertEquals(15, total)
    }

    // ── ELMo ─────────────────────────────────────────────────────────────────

    @Test
    fun `every same-sense pair beats every cross-sense pair`() {
        val within = listOf(ElmoLab.similarity(0, 1), ElmoLab.similarity(2, 3))
        val across = listOf(
            ElmoLab.similarity(0, 2), ElmoLab.similarity(0, 3),
            ElmoLab.similarity(1, 2), ElmoLab.similarity(1, 3),
        )
        assertTrue(
            "within ${within.min()} must beat across ${across.max()} — the topic's whole claim",
            within.min() > across.max(),
        )
        assertTrue(ElmoLab.senseGap() > 0f)
        assertEquals(1f, ElmoLab.staticSimilarity(), 1e-4f)
    }

    @Test
    fun `the occurrence projection stays inside the canvas`() {
        val projection = ElmoLab.project2D()
        assertEquals(ElmoLab.sentences.size + 1, projection.size)
        assertTrue(projection.all { it.first in 0f..1f && it.second in 0f..1f })
    }

    @Test
    fun `the parameter comparison is the way round the copy states`() {
        assertNotNull(ElmoLab.occurrenceVectors())
        assertTrue(
            "The contextual model is the smaller one — that is the point of the frame",
            ElmoLab.elmoParameters() < ElmoLab.lookupParameters(),
        )
        assertEquals(300_000_000L, ElmoLab.lookupParameters())
    }

    private fun abs(x: Double) = kotlin.math.abs(x)
}
