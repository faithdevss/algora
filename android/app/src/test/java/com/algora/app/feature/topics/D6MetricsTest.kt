package com.algora.app.feature.topics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * D6's guard for the six NLP metric labs.
 *
 * Three of these exist because the probe falsified the copy that was about to be written: the test
 * sentence had no unseen bigrams, so the "unsmoothed perplexity is infinite" frame had nothing to
 * stand on; the two WER hypotheses that were to score identically differ by 3×; and the
 * whole-document ROUGE recall is 0.800 rather than the perfect score the file had asserted.
 *
 * The tests marked **inverts** are the ones whose failure means a topic's central claim has flipped
 * rather than drifted — each of those frames reads perfectly well in the wrong direction, which is
 * exactly why it needs pinning.
 */
class D6MetricsTest {

    // ── Perplexity ───────────────────────────────────────────────────────────

    @Test
    fun `a uniform model's perplexity is exactly the vocabulary size`() {
        assertEquals(19, PerplexityLab.vocabulary.size)
        assertEquals(19.0, PerplexityLab.uniformPerplexity(), 1e-9)
        assertTrue(
            "The trained model must beat the uniform bound, or the branching-factor reading says nothing",
            PerplexityLab.perplexity(PerplexityLab.testSentence) < PerplexityLab.uniformPerplexity(),
        )
    }

    @Test
    fun `smoothing is a cost on attested text and insurance on unattested text`() {
        // Every bigram of the test sentence is attested, so add-k can only take mass away.
        assertTrue(PerplexityLab.unseenBigrams(PerplexityLab.testSentence).isEmpty())
        val sweep = PerplexityLab.smoothingSweep.map { PerplexityLab.perplexity(PerplexityLab.testSentence, it) }
        assertEquals(
            "The sweep must be monotone increasing on a fully attested sentence, or the frame's premium is fiction",
            sweep.sorted(), sweep,
        )
        assertEquals(0.001, PerplexityLab.bestSmoothing(), 1e-12)
        assertTrue("add-1 must cost at least 3x here", PerplexityLab.smoothingPremium() > 3.0)

        // And the sentence that needs it: one unattested bigram, so k = 0 is infinite.
        assertEquals(1, PerplexityLab.unseenBigrams(PerplexityLab.unseenSentence).size)
        assertTrue(
            "An unattested bigram must send the unsmoothed score to infinity",
            PerplexityLab.unsmoothedPerplexity(PerplexityLab.unseenSentence).isInfinite(),
        )
        val best = PerplexityLab.bestSmoothing(PerplexityLab.unseenSentence)
        assertTrue(
            "The optimum must move inside the sweep once a zero exists — it was at the floor without one",
            best > PerplexityLab.smoothingSweep.first() && best < PerplexityLab.smoothingSweep.last(),
        )
    }

    @Test
    fun `perplexity and bits per character rank the two tokenizers in opposite orders`() {
        val wordPpl = PerplexityLab.perplexity(PerplexityLab.testSentence)
        val charPpl = PerplexityLab.characterPerplexity(PerplexityLab.testSentence)
        val wordBpc = PerplexityLab.bitsPerCharacter(PerplexityLab.testSentence)
        val charBpc = PerplexityLab.characterBitsPerCharacter(PerplexityLab.testSentence)
        // **inverts** — the topic's headline is that these two orderings disagree.
        assertTrue("The character model must look better on raw perplexity", charPpl < wordPpl)
        assertTrue("...and worse on bits per character, or there is no trap to warn about", wordBpc < charBpc)
    }

    // ── WER ──────────────────────────────────────────────────────────────────

    @Test
    fun `WER exceeds one when a decoder runs away`() {
        val runaway = WerLab.alignmentFor(WerLab.cases.last())
        assertTrue("Insertions must outnumber the reference, or the >100% claim fails", runaway.wer > 1.0)
        assertEquals(17, runaway.insertions)
        assertEquals(9, runaway.referenceLength)
    }

    @Test
    fun `WER ranks a meaning-preserving error worse than a meaning-reversing one`() {
        val (harmless, reversed) = WerLab.misrankedCases()
        val harmlessWer = WerLab.alignmentFor(harmless).wer
        val reversedWer = WerLab.alignmentFor(reversed).wer
        // **inverts** — if the reversed sentence ever scores worse, the topic has no argument.
        assertTrue(
            "The sentence that reverses the meaning must score lower than the one that preserves it",
            reversedWer < harmlessWer,
        )
        assertEquals(3.0, harmlessWer / reversedWer, 1e-9)
    }

    @Test
    fun `two hypotheses with opposite meanings score exactly the same WER`() {
        val (a, b) = WerLab.collidingCases()
        val werA = WerLab.alignmentFor(a).wer
        val werB = WerLab.alignmentFor(b).wer
        assertEquals("The tie is the claim; without it the frame is about a near-miss", werA, werB, 1e-12)
        assertEquals(3, WerLab.alignmentFor(a).errors)
        assertEquals(3, WerLab.alignmentFor(b).errors)
        // Reached by different routes, which is what makes the tie interesting.
        assertEquals(3, WerLab.alignmentFor(a).deletions)
        assertEquals(2, WerLab.alignmentFor(b).substitutions)
    }

    @Test
    fun `normalization alone moves WER further than most model changes`() {
        assertTrue(WerLab.unnormalizedWer() > 0.5)
        assertEquals(0.0, WerLab.normalizedWer(), 1e-12)
    }

    // ── BLEU ─────────────────────────────────────────────────────────────────

    @Test
    fun `clipping is what stops a stuck decoder scoring perfect unigram precision`() {
        val degenerate = BleuLab.candidates[3].text
        val (unclipped, unclippedTotal) = BleuLab.unclippedPrecision(degenerate, 1)
        val (clipped, clippedTotal) = BleuLab.modifiedPrecision(degenerate, 1)
        assertEquals("Unclipped, the repetition must score perfectly", unclipped, unclippedTotal)
        assertEquals(2, clipped)
        assertEquals(8, clippedTotal)
    }

    @Test
    fun `a correct paraphrase scores exactly zero and the degenerate output scores the same`() {
        val paraphrase = BleuLab.candidates[2].text
        val degenerate = BleuLab.candidates[3].text
        // **inverts** — the whole topic turns on a good translation being indistinguishable from noise.
        assertEquals(0.0, BleuLab.bleu(paraphrase), 1e-12)
        assertEquals(0.0, BleuLab.bleu(degenerate), 1e-12)
        assertEquals(listOf(3, 4), BleuLab.emptyOrders(paraphrase))
        assertTrue(
            "Smoothing must lift the paraphrase above zero without separating it from the degenerate output",
            BleuLab.bleu(paraphrase, smoothing = true) > 0.0 &&
                kotlin.math.abs(
                    BleuLab.bleu(paraphrase, smoothing = true) - BleuLab.bleu(degenerate, smoothing = true),
                ) < 0.05,
        )
    }

    @Test
    fun `the brevity penalty is what punishes a correct fragment`() {
        val short = BleuLab.candidates[4].text
        assertEquals(3 to 3, BleuLab.modifiedPrecision(short, 1))
        assertTrue("A 3-word candidate against 8 must be penalised hard", BleuLab.brevityPenalty(short) < 0.25)
    }

    // ── ROUGE ────────────────────────────────────────────────────────────────

    @Test
    fun `submitting the whole document scores high recall and is saved only by precision`() {
        val whole = RougeLab.rougeN(RougeLab.candidates[1].text, 1)
        assertTrue("Recall for summarising nothing must stay high, or the exploit is not one", whole.recall >= 0.75)
        assertTrue("Precision is what closes it", whole.precision < 0.3)
        assertTrue(
            "F1 must rank the real summary above the whole document",
            RougeLab.rougeN(RougeLab.candidates[0].text, 1).f1 > whole.f1,
        )
    }

    @Test
    fun `ROUGE-1 cannot see a reordering that ROUGE-2 and ROUGE-L both can`() {
        val reordered = RougeLab.candidates[2].text
        val focused = RougeLab.candidates[0].text
        // **inverts** — if ROUGE-1 ever separates these, the reason to quote three variants disappears.
        assertEquals(
            "ROUGE-1 must score the reordering identically to the correct summary",
            RougeLab.rougeN(focused, 1).f1, RougeLab.rougeN(reordered, 1).f1, 1e-12,
        )
        assertTrue(RougeLab.rougeN(reordered, 2).f1 < RougeLab.rougeN(focused, 2).f1)
        assertTrue(RougeLab.rougeL(reordered).f1 < RougeLab.rougeL(focused).f1)
        assertEquals(0.5, RougeLab.rougeL(reordered).f1, 1e-9)
    }

    @Test
    fun `a correct abstractive summary is scored below the do-nothing baseline`() {
        val abstractive = RougeLab.rougeN(RougeLab.candidates[3].text, 1)
        val whole = RougeLab.rougeN(RougeLab.candidates[1].text, 1)
        // **inverts** — "the metric selects against abstraction" needs the ordering to hold.
        assertTrue(
            "A correct abstractive summary must score below the whole document, or the frame is wrong",
            abstractive.f1 < whole.f1,
        )
        assertEquals(0.0, RougeLab.rougeN(RougeLab.candidates[3].text, 2).f1, 1e-12)
    }

    // ── METEOR ───────────────────────────────────────────────────────────────

    @Test
    fun `METEOR scores the paraphrase BLEU zeroes`() {
        val paraphrase = BleuLab.candidates[2].text
        // **inverts** — this is the reason the topic exists at all.
        assertEquals(0.0, BleuLab.bleu(paraphrase), 1e-12)
        assertTrue("METEOR must give the paraphrase real credit", MeteorLab.score(paraphrase) > 0.4)
        assertTrue(
            "...and must still rank it above the stuck decoder",
            MeteorLab.score(paraphrase) > MeteorLab.score(BleuLab.candidates[3].text),
        )
    }

    @Test
    fun `the fragmentation penalty saturates at gamma on a full shuffle`() {
        val shuffled = MeteorLab.shuffled
        assertEquals("Every word still matches, so the unordered mean is perfect", 1.0, MeteorLab.fMean(shuffled), 1e-9)
        assertEquals(MeteorLab.alignment(shuffled).size, MeteorLab.chunks(shuffled))
        assertEquals("chunks == matches makes the penalty exactly gamma", MeteorLab.gamma, MeteorLab.penalty(shuffled), 1e-9)
        assertEquals("...so a full shuffle scores exactly half, never zero", 0.5, MeteorLab.score(shuffled), 1e-9)
    }

    @Test
    fun `the F-mean is pulled towards recall rather than sitting halfway`() {
        val paraphrase = BleuLab.candidates[2].text
        val p = MeteorLab.precision(paraphrase)
        val r = MeteorLab.recall(paraphrase)
        val f = MeteorLab.fMean(paraphrase)
        assertTrue("Recall must exceed precision here for the weighting to be visible", r > p)
        assertTrue("The weighted mean must sit above the unweighted midpoint", f > (p + r) / 2)
    }

    // ── MMLU ─────────────────────────────────────────────────────────────────

    @Test
    fun `the top two models are not separated by the benchmark`() {
        val (a, b) = MmluLab.models[0] to MmluLab.models[1]
        val full = MmluLab.separation(a.reported, b.reported, MmluLab.totalQuestions)
        // **inverts** — if this ever exceeds 2, the topic's headline claim is false.
        assertTrue("The top two must sit inside 2 SE, or the leaderboard is measuring something", full < 2.0)
        assertTrue(
            "...while a genuine tier gap must clear it comfortably",
            MmluLab.separation(a.reported, MmluLab.models[2].reported, MmluLab.totalQuestions) > 5.0,
        )
    }

    @Test
    fun `subject-level standard error is an order of magnitude larger`() {
        val full = MmluLab.standardError(0.70, MmluLab.totalQuestions)
        val subject = MmluLab.standardError(0.70, MmluLab.subjectQuestions)
        assertTrue("Subject SE must be at least 10x the benchmark's", subject / full > 10)
        val pairs = MmluLab.indistinguishableSubjectPairs()
        val total = MmluLab.subjects.size * (MmluLab.subjects.size - 1) / 2
        assertTrue(
            "A meaningful share of subject pairs must be indistinguishable, or the frame overstates",
            pairs.size >= total / 8,
        )
    }

    @Test
    fun `the averaging choice is worth most of the gap between the top two models`() {
        val spread = kotlin.math.abs(MmluLab.macroAverage() - MmluLab.microAverage())
        val topGap = MmluLab.models[0].reported - MmluLab.models[1].reported
        assertTrue("Micro and macro must actually differ", spread > 0.0)
        assertTrue(
            "The reporting choice must be comparable to the gap being reported",
            spread / topGap > 0.5,
        )
    }

    @Test
    fun `contamination moves a score further than any honest gap on the board`() {
        val leaked = MmluLab.contaminatedScore(0.55, 0.20) - 0.55
        val topGap = MmluLab.models[0].reported - MmluLab.models[1].reported
        assertTrue("20% leakage must dwarf the top-two gap", leaked > 10 * topGap)
        assertEquals(0.625, MmluLab.trueAbility(0.70, 0.20), 1e-9)
        assertEquals(0.08, MmluLab.chanceCorrected(MmluLab.models[3].reported), 1e-9)
    }
}
