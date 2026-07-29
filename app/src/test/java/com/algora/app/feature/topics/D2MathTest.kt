package com.algora.app.feature.topics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * D2's guard. The pattern from D1 and D3: exact pins on what is counted, relational assertions on
 * what is run — the contextual tagger beating the baseline, the boundary error costing a whole span,
 * the head-rule conversion reproducing the dependency gold, the rules improving sentiment accuracy.
 *
 * Two of these exist because the first probe run disagreed with the copy that was about to be
 * written: the HMM tagger originally tied the baseline (fixed by adding the missing VBP → RB
 * evidence to training) and coreference chains were reported on immediate links rather than on the
 * entity they close to.
 */
class D2MathTest {

    // ── POS tagging ──────────────────────────────────────────────────────────

    @Test
    fun `the mini treebank is the size the copy quotes`() {
        assertEquals(24, PosLab.typeCount)
        assertEquals(listOf("man"), PosLab.ambiguousTypes)
        assertEquals(9, PosLab.tagset.size)
        assertEquals(14, PosLab.baselineScore.total)
    }

    @Test
    fun `context beats per-word frequency, which is the whole topic`() {
        assertEquals(12, PosLab.baselineScore.correct)
        assertEquals(14, PosLab.viterbiScore.correct)
        assertTrue(
            "If the HMM ever ties the baseline, the topic's comparison is empty",
            PosLab.viterbiScore.accuracy > PosLab.baselineScore.accuracy,
        )
        assertEquals(0.857, PosLab.baselineScore.accuracy, 0.001)
        assertEquals(1.0, PosLab.viterbiScore.accuracy, 1e-9)
    }

    @Test
    fun `both baseline errors are a verb read as a noun`() {
        val errors = PosLab.baselineErrors()
        assertEquals(2, errors.size)
        assertTrue(errors.all { it.second == "VBP" && it.third == "NN" })
        assertTrue("walk is the unknown-word case", errors.any { it.first == "walk" })
    }

    @Test
    fun `a pronoun is likelier to precede a verb than a noun`() {
        // The frame quotes these two numbers as the reason Viterbi fixes "they man".
        assertTrue(PosLab.a("PRP", "VBP") > PosLab.a("PRP", "NN"))
    }

    // ── Dependency parsing ───────────────────────────────────────────────────

    @Test
    fun `arc-standard finishes in exactly 2n transitions`() {
        assertEquals(12, DependencyLab.transitionCount())
        assertEquals(DependencyLab.expectedTransitions(), DependencyLab.transitionCount())
        assertEquals(DependencyLab.sentence.size, DependencyLab.finalArcs().size)
    }

    @Test
    fun `the oracle recovers the gold tree exactly`() {
        val heads = IntArray(DependencyLab.sentence.size)
        DependencyLab.finalArcs().forEach { (head, dependent, _) -> heads[dependent - 1] = head }
        assertEquals(DependencyLab.goldHeads, heads.toList())
        assertTrue(DependencyLab.isProjective)
    }

    @Test
    fun `LAS is strictly below UAS on the lab's wrong parse`() {
        val uas = DependencyLab.uas(DependencyLab.predictedHeads)
        val las = DependencyLab.las(DependencyLab.predictedHeads, DependencyLab.predictedLabels)
        assertEquals(5.0 / 6, uas, 1e-9)
        assertEquals(4.0 / 6, las, 1e-9)
        assertTrue("A label error on a correct attachment is what separates them", las < uas)
    }

    @Test
    fun `the non-projective sentence really does have a crossing arc`() {
        assertTrue(DependencyLab.crossingArcs().isNotEmpty())
        assertTrue(DependencyLab.crossingArcs(DependencyLab.goldHeads).isEmpty())
    }

    // ── Constituency parsing ─────────────────────────────────────────────────

    @Test
    fun `evalb reports the asymmetry the copy describes`() {
        val score = ConstituencyLab.evalb()
        assertEquals(4, ConstituencyLab.spanList(ConstituencyLab.gold).size)
        assertEquals(3, ConstituencyLab.spanList(ConstituencyLab.predicted).size)
        assertEquals(1.0, score.precision, 1e-9)
        assertEquals(0.75, score.recall, 1e-9)
        assertEquals(0.857, score.f1, 0.001)
    }

    @Test
    fun `head rules convert the phrase tree into the dependency gold`() {
        assertEquals(DependencyLab.goldHeads, ConstituencyLab.toDependencies())
        assertTrue(ConstituencyLab.conversionMatchesDependencyGold)
    }

    // ── Chunking ─────────────────────────────────────────────────────────────

    @Test
    fun `the regex chunker matches the gold chunks`() {
        assertEquals(ChunkLab.gold, ChunkLab.chunk())
        assertEquals(1.0, ChunkLab.evaluate().f1, 1e-9)
        assertEquals(
            listOf("B-NP", "I-NP", "I-NP", "B-VP", "B-NP", "I-NP", "B-PP", "B-NP", "I-NP"),
            ChunkLab.bio(),
        )
    }

    @Test
    fun `one wrong boundary costs a whole span, not part of one`() {
        val broken = ChunkLab.evaluate(ChunkLab.boundaryError)
        assertEquals(0.8, broken.f1, 1e-9)
        // The span overlaps the gold one by two of three tokens and still scores zero — the point
        // of the frame is exact match, not the size of the drop.
        assertTrue(ChunkLab.boundaryError.none { it == ChunkLab.gold.first() })
        assertEquals(0.778, ChunkLab.tokenAccuracy(ChunkLab.boundaryError), 0.001)
        assertEquals(729, ChunkLab.parseOperations())
        assertEquals(9, ChunkLab.chunkOperations())
    }

    // ── Coreference ──────────────────────────────────────────────────────────

    @Test
    fun `chains are scored on entities, not on immediate links`() {
        assertEquals(2.0 / 3, CorefLab.pairAccuracy(), 1e-9)
        assertEquals(1.0, CorefLab.easyAccuracy(), 1e-9)
        assertTrue(
            "The gap between the two is the evaluation mistake the frame is about",
            CorefLab.easyAccuracy() > CorefLab.pairAccuracy(),
        )
        assertEquals("She", CorefLab.resolveEasy(5))
        assertEquals("Ada Lovelace", CorefLab.resolveChain(5))
    }

    @Test
    fun `the chains close to two entities`() {
        val chains = CorefLab.chains()
        assertEquals(setOf("Ada Lovelace", "Charles Babbage"), chains.keys)
        assertEquals(listOf("Ada Lovelace", "She", "her"), chains.getValue("Ada Lovelace"))
    }

    @Test
    fun `every syntactic heuristic scores fifty percent on the Winograd pair`() {
        assertNotEquals(CorefLab.goldA, CorefLab.goldB)
        assertEquals(0.5, CorefLab.baselineAccuracyOnPair(), 1e-9)
        // The two sentences must differ by exactly one word, or the schema proves nothing.
        val a = CorefLab.winogradA.split(" ")
        val b = CorefLab.winogradB.split(" ")
        assertEquals(a.size, b.size)
        assertEquals(1, a.indices.count { a[it] != b[it] })
        assertEquals(15, CorefLab.candidatePairs())
    }

    // ── Lexicon sentiment ────────────────────────────────────────────────────

    @Test
    fun `the plain sum gets negated sentences wrong`() {
        val negated = SentimentLexiconLab.testSet[1]
        assertTrue(SentimentLexiconLab.plainScore(negated.first) > 0)
        assertEquals(-1, negated.second)
        assertTrue(SentimentLexiconLab.ruleScore(negated.first) < 0)
    }

    @Test
    fun `the rules improve accuracy and every fixed error was a negation`() {
        assertEquals(0.7, SentimentLexiconLab.plainAccuracy, 1e-9)
        assertEquals(0.9, SentimentLexiconLab.ruleAccuracy, 1e-9)
        val plainErrors = SentimentLexiconLab.errors(SentimentLexiconLab::plainScore)
        assertEquals(3, plainErrors.size)
        assertTrue(
            "All three must contain a negator, which is what makes negation the top rule",
            plainErrors.all { (text, _) -> text.split(" ").any { it in SentimentLexiconLab.negators } },
        )
    }

    @Test
    fun `the rules keep one error, and it is the scope failure the copy names`() {
        val remaining = SentimentLexiconLab.errors(SentimentLexiconLab::ruleScore)
        assertEquals(1, remaining.size)
        assertEquals("never buy this awful thing", remaining.first().first)
        assertTrue("It is wrong in the positive direction", remaining.first().second > 0)
    }

    @Test
    fun `lexicon coverage is the ceiling the copy quotes`() {
        assertEquals(20, SentimentLexiconLab.lexiconSize)
        assertEquals(0.207, SentimentLexiconLab.coverage(), 0.005)
    }
}
