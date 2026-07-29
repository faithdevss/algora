package com.algora.app.feature.topics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the properties D1's copy leans on, in the pattern B7MathTest and DeepNetMathTest set: a
 * re-tune that makes a topic's argument false fails here rather than shipping.
 *
 * The two that matter most are the ones a plausible-looking edit would quietly break — greedy
 * tagging and Viterbi *disagreeing* on the HMM lab's sentence, and the PP attaching to the verb
 * phrase being the parse CYK actually prefers. Both are the reason those topics have a payoff at
 * all, and both would still render happily if the numbers stopped supporting them.
 */
class D1MathTest {

    // ── Stop words ───────────────────────────────────────────────────────────

    @Test
    fun `the stop list removes the share of the corpus the copy quotes`() {
        assertEquals(51, StopWordLab.totalTokens)
        assertEquals(21, StopWordLab.keptTokens)
        assertEquals(30, StopWordLab.removedTokens)
        assertEquals(0.588, StopWordLab.removalRate, 0.001)
        assertEquals(37, StopWordLab.typesBefore)
        assertEquals(19, StopWordLab.typesAfter)
    }

    @Test
    fun `removing the negation really does collapse two opposite reviews`() {
        assertTrue("not", "not" in StopWordLab.stopList)
        assertNotEquals(StopWordLab.tokens(StopWordLab.corpus[0]), StopWordLab.tokens(StopWordLab.corpus[1]))
        assertTrue(
            "The whole topic rests on these two bags being identical after removal",
            StopWordLab.negationCollapse,
        )
        assertTrue(StopWordLab.hamletSurvivors.isEmpty())
    }

    @Test
    fun `idf down-weights the frequent term instead of deleting it`() {
        val the = StopWordLab.idf("the")
        val movie = StopWordLab.idf("movie")
        assertTrue("idf(the) should be well below idf(movie): $the vs $movie", movie > the * 5)
        assertEquals(0.18, the, 0.01)
        assertEquals(1.10, movie, 0.01)
    }

    // ── Cleaning ─────────────────────────────────────────────────────────────

    @Test
    fun `the cleaning pipeline shrinks the vocabulary and never grows it`() {
        assertEquals(35, CleaningLab.typesRaw)
        assertEquals(29, CleaningLab.typesClean)
        val counts = (0..CleaningLab.stages.size).map { CleaningLab.typesAfter(it) }
        assertEquals(counts.sortedDescending(), counts)
    }

    @Test
    fun `lowercasing both merges a word it should and collides one it should not`() {
        assertEquals(2, CleaningLab.appleTypesBefore)
        assertEquals(1, CleaningLab.appleTypesAfter)
        assertTrue("US/us must actually collide for the cost frame to be true", CleaningLab.casedPairCollapsed)
    }

    // ── Regex ────────────────────────────────────────────────────────────────

    @Test
    fun `the tuned pattern keeps whole what the naive one shatters`() {
        assertEquals(21, RegexLab.naiveTokens.size)
        assertEquals(12, RegexLab.tunedTokens.size)
        assertTrue("a.smith@x.co" in RegexLab.tunedTokens)
        assertTrue("2024-01-05" in RegexLab.tunedTokens)
        assertTrue("U.S." in RegexLab.tunedTokens)
        assertTrue("3.5%" in RegexLab.tunedTokens)
        assertEquals(6, RegexLab.rescued.size)
    }

    @Test
    fun `branch order is what makes the pattern work`() {
        // Not back to \w+'s 21: the branches that begin with a digit still fire. What breaks is
        // every branch a letter can start, and the e-mail comes back as a token that looks right.
        assertEquals(16, RegexLab.misorderedTokens.size)
        assertTrue(RegexLab.misorderedTokens.size > RegexLab.tunedTokens.size)
        assertTrue("a.smith@x.co" !in RegexLab.misorderedTokens)
        assertTrue(".smith@x.co" in RegexLab.misorderedTokens)
        assertTrue("2024-01-05" in RegexLab.misorderedTokens)
    }

    @Test
    fun `nested quantifier backtracking is exponential and the rewrite is linear`() {
        listOf(4, 8, 12, 16, 20, 24).forEach { n ->
            assertEquals("n=$n", 1L shl (n - 1), RegexLab.backtrackAttempts(n))
            assertEquals(n.toLong(), RegexLab.linearAttempts(n))
        }
        assertEquals(8_388_608L, RegexLab.backtrackAttempts(24))
    }

    // ── N-grams ──────────────────────────────────────────────────────────────

    @Test
    fun `the bigram model is the counts the copy quotes`() {
        assertEquals(11, NGramLab.vocabSize)
        assertEquals(0.6, NGramLab.mle("i", "like"), 1e-9)
        assertEquals(0.4, NGramLab.mle("i", "love"), 1e-9)
        assertEquals(0.0, NGramLab.mle("love", "deep"), 1e-9)
        assertEquals(1, NGramLab.unseenBigrams.size)
    }

    @Test
    fun `one unseen bigram makes MLE perplexity infinite and add-k has an optimum`() {
        assertEquals(Double.POSITIVE_INFINITY, NGramLab.perplexity(NGramLab.heldOut, 0.0), 0.0)
        val k1 = NGramLab.perplexity(NGramLab.heldOut, 1.0)
        val kTenth = NGramLab.perplexity(NGramLab.heldOut, 0.1)
        val kHundredth = NGramLab.perplexity(NGramLab.heldOut, 0.01)
        assertEquals(5.20, k1, 0.01)
        assertEquals(3.42, kTenth, 0.01)
        assertEquals(4.34, kHundredth, 0.01)
        assertTrue("k=0.1 must beat both neighbours, which is the frame's point", kTenth < k1 && kTenth < kHundredth)
    }

    @Test
    fun `smoothing costs perplexity on text the model has seen`() {
        val mle = NGramLab.perplexity("i like nlp", 0.0)
        val add1 = NGramLab.perplexity("i like nlp", 1.0)
        assertEquals(1.86, mle, 0.01)
        assertEquals(4.51, add1, 0.01)
        assertTrue(add1 > mle)
    }

    @Test
    fun `sparsity worsens with the order`() {
        val ratios = (1..4).map { NGramLab.typeCount(it).toDouble() / NGramLab.tokenCount(it) }
        assertEquals(ratios.sorted(), ratios)
        assertEquals(20, NGramLab.typeCount(4))
        assertEquals(21, NGramLab.tokenCount(4))
    }

    // ── HMM ──────────────────────────────────────────────────────────────────

    @Test
    fun `greedy tagging and Viterbi disagree on the lab sentence`() {
        val greedy = HmmLab.greedy()
        val viterbi = HmmLab.viterbi()
        assertEquals(listOf("NN", "IN", "NN"), greedy.tags)
        assertEquals(listOf("VB", "DT", "NN"), viterbi.tags)
        assertTrue(
            "Viterbi must beat greedy or the topic has no payoff",
            viterbi.probability > greedy.probability,
        )
        assertEquals(1.43, viterbi.probability / greedy.probability, 0.01)
    }

    @Test
    fun `greedy really is locally optimal at its first step`() {
        // The failure has to come from committing, not from a bad local score: NN must genuinely
        // win the first word.
        val nn = (HmmLab.start.getValue("NN")) * HmmLab.b("NN", "book")
        val vb = (HmmLab.start.getValue("VB")) * HmmLab.b("VB", "book")
        assertTrue("NN must win locally: $nn vs $vb", nn > vb)
    }

    @Test
    fun `Viterbi is the max over the paths the forward algorithm sums`() {
        val paths = HmmLab.allPaths()
        assertEquals(HmmLab.viterbi().probability, paths.first().probability, 1e-12)
        assertEquals(HmmLab.sentenceProbability(), paths.sumOf { it.probability }, 1e-12)
        assertEquals(48, HmmLab.viterbiOperations())
        assertEquals(64, HmmLab.bruteForcePaths())
    }

    @Test
    fun `every transition row is a distribution`() {
        HmmLab.transition.forEach { (from, row) ->
            assertEquals("row $from", 1.0, row.values.sum(), 1e-9)
        }
        assertEquals(1.0, HmmLab.start.values.sum(), 1e-9)
    }

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

    // ── Similarity ───────────────────────────────────────────────────────────

    @Test
    fun `cosine ignores length where Euclidean distance does not`() {
        assertTrue(SimilarityLab.lengthInvariant)
        assertEquals(1.0, SimilarityLab.cosine("short", "long"), 1e-9)
        assertEquals(3.162, SimilarityLab.euclidean("short", "long"), 0.001)
        assertEquals(0.6, SimilarityLab.cosine("short", "theory"), 1e-9)
    }

    @Test
    fun `the two metrics rank the query differently`() {
        val byCosine = SimilarityLab.rankByCosine()
        val byEuclidean = SimilarityLab.rankByEuclidean()
        assertNotEquals("The ranking flip is the frame's whole point", byCosine, byEuclidean)
        assertEquals("long", byEuclidean.last())
        assertTrue("long must rank above theory by angle", byCosine.indexOf("long") < byCosine.indexOf("theory"))
    }

    @Test
    fun `Jaccard and cosine disagree on the repeated term`() {
        val j = SimilarityLab.jaccard(SimilarityLab.docA, SimilarityLab.docB)
        val c = SimilarityLab.cosineOfDocs(SimilarityLab.docA, SimilarityLab.docB)
        assertEquals(0.714, j, 0.001)
        assertEquals(0.849, c, 0.001)
        assertTrue("Cosine sees the repetition Jaccard cannot", c > j)
    }

    @Test
    fun `MinHash converges on the shingled Jaccard it estimates`() {
        val truth = SimilarityLab.jaccardShingles(SimilarityLab.docA, SimilarityLab.docB)
        assertEquals(0.629, truth, 0.001)
        assertEquals(35, SimilarityLab.shingles(SimilarityLab.docA).size)
        assertEquals(22, SimilarityLab.shingles(SimilarityLab.docB).size)
        val coarse = kotlin.math.abs(SimilarityLab.minHashEstimate(SimilarityLab.docA, SimilarityLab.docB, 16) - truth)
        val fine = kotlin.math.abs(SimilarityLab.minHashEstimate(SimilarityLab.docA, SimilarityLab.docB, 256) - truth)
        assertTrue("k=16 error $coarse, k=256 error $fine", fine < coarse)
        assertTrue("a 256-permutation signature should be close", fine < 0.05)
    }
}
