package com.algora.app.feature.topics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * C6's pre-training guard. Two different kinds of claim are checked here and they fail differently.
 *
 * The parameter tables are arithmetic, so they are asserted to the digit — and they are asserted
 * against the *released checkpoints* rather than against the papers, because for GPT-2 those two
 * disagree by 6.4% and the copy says so. The objective comparisons are counts over a corpus, so the
 * orderings are pinned alongside the numbers.
 */
class PretrainMathTest {

    // ── Objectives ───────────────────────────────────────────────────────────

    @Test
    fun `masking extracts far less signal per pass than a causal objective`() {
        assertEquals(64, PretrainLab.causalTargets)
        assertEquals(10, PretrainLab.maskedTargets)
        assertEquals(6.4, PretrainLab.signalRatio, 0.01)
    }

    @Test
    fun `and buys exactly twice the context per prediction`() {
        assertEquals(2.72, PretrainLab.causalContext, 0.01)
        assertEquals(5.44, PretrainLab.maskedContext, 0.01)
        assertEquals(
            "a masked prediction sees every other position; a causal one sees half of them on average",
            2.0, PretrainLab.maskedContext / PretrainLab.causalContext, 0.02,
        )
    }

    @Test
    fun `the corruption split is 80-10-10 of the selected positions`() {
        val corruption = PretrainLab.corruption
        assertEquals(PretrainLab.maskedTargets, corruption.total)
        assertEquals(8, corruption.masked)
        assertEquals(1, corruption.randomised)
        assertEquals(1, corruption.kept)
    }

    @Test
    fun `the pre-train fine-tune mismatch is twelve percent of positions`() {
        assertEquals(0.12, PretrainLab.maskTokenShare, 1e-9)
    }

    @Test
    fun `static masking leaves one token in five never predicted`() {
        // RoBERTa's cheapest change, as a probability rather than an opinion.
        assertEquals(0.1969, PretrainLab.neverMasked(PretrainLab.STATIC_DUPLICATES), 1e-4)
        assertEquals(0.0015, PretrainLab.neverMasked(PretrainLab.EPOCHS), 1e-4)
        assertEquals(4, PretrainLab.staticReuse)
        assertTrue(
            "dynamic masking must reach more of the corpus, or RoBERTa's argument is empty",
            PretrainLab.neverMasked(PretrainLab.dynamicDistinctMasks) <
                PretrainLab.neverMasked(PretrainLab.staticDistinctMasks) / 100,
        )
    }

    // ── T5 ───────────────────────────────────────────────────────────────────

    @Test
    fun `span corruption keeps the input long and the target short`() {
        val corrupted = SpanCorruptionLab.corrupt()
        assertTrue("one sentinel per span in the input", corrupted.input.count { it.startsWith("<X") } == corrupted.spans)
        assertTrue("the target is shorter than the source", corrupted.target.size < SpanCorruptionLab.sentence.size)
        assertEquals(
            "target = corrupted tokens + one sentinel each + the final sentinel",
            corrupted.corruptedTokens + corrupted.spans + 1, corrupted.target.size,
        )
    }

    @Test
    fun `at 512 tokens the decoder emits a fifth of what a masked model would`() {
        val scaled = SpanCorruptionLab.atScale()
        assertEquals(77, scaled.corrupted)
        assertEquals(26, scaled.spans)
        assertEquals(461, scaled.inputLength)
        assertEquals(104, scaled.targetLength)
        assertEquals(4.9, scaled.maskedOutputLength.toDouble() / scaled.targetLength, 0.05)
    }

    // ── Parameter tables ─────────────────────────────────────────────────────

    @Test
    fun `BERT-base sums to the released checkpoint`() {
        val config = ModelTableLab.bertBase
        assertEquals(109_482_240L, ModelTableLab.total(config))
        val breakdown = ModelTableLab.breakdown(config)
        assertEquals(23_837_184L, breakdown.embeddings)
        assertEquals(85_054_464L, breakdown.encoder)
        assertEquals(590_592L, breakdown.head)
        assertEquals(7_087_872L, ModelTableLab.encoderLayer(config))
    }

    @Test
    fun `GPT-2 small sums to 124M, not the paper's 117M`() {
        // Kept as its own test because the copy makes a point of the discrepancy. If a config edit
        // ever makes this match the paper, the copy is the thing that has become wrong.
        assertEquals(124_439_808L, ModelTableLab.total(ModelTableLab.gpt2Small))
        val published = ModelTableLab.published.getValue("GPT-2 small")
        assertEquals(
            6.4,
            (ModelTableLab.total(ModelTableLab.gpt2Small) - published) * 100.0 / published,
            0.1,
        )
    }

    @Test
    fun `RoBERTa DistilBERT and T5 sum to their checkpoints`() {
        assertEquals(124_645_632L, ModelTableLab.total(ModelTableLab.robertaBase))
        assertEquals(66_362_880L, ModelTableLab.total(ModelTableLab.distilBert))
        assertEquals(222_903_552L, ModelTableLab.total(ModelTableLab.t5Base))
    }

    @Test
    fun `every table is within two percent of its published figure except GPT-2`() {
        ModelTableLab.all.filter { it.name != "GPT-2 small" }.forEach { config ->
            val published = ModelTableLab.published.getValue(config.name)
            val error = kotlin.math.abs(ModelTableLab.total(config) - published) * 100.0 / published
            assertTrue("${config.name} is $error% from its published figure", error < 2.0)
        }
    }

    @Test
    fun `two thirds of a transformer layer is the feed-forward block`() {
        val config = ModelTableLab.bertBase
        val share = ModelTableLab.ffnBlock(config).toDouble() / ModelTableLab.encoderLayer(config)
        assertEquals(0.666, share, 0.01)
    }

    @Test
    fun `RoBERTa's vocabulary is where its extra parameters are`() {
        val bert = ModelTableLab.breakdown(ModelTableLab.bertBase)
        val roberta = ModelTableLab.breakdown(ModelTableLab.robertaBase)
        assertEquals("the layers are identical", bert.encoder, roberta.encoder)
        assertEquals(0.636, roberta.embeddings.toDouble() / bert.embeddings - 1, 0.01)
    }

    @Test
    fun `T5's decoder is the larger half`() {
        val breakdown = ModelTableLab.breakdown(ModelTableLab.t5Base)
        assertTrue("cross-attention is what makes it bigger", breakdown.decoder > breakdown.encoder)
        assertEquals(
            "a decoder layer is two attention blocks and a feed-forward",
            2 * ModelTableLab.attentionBlock(ModelTableLab.t5Base) + ModelTableLab.ffnBlock(ModelTableLab.t5Base) +
                3 * ModelTableLab.t5Base.dim,
            ModelTableLab.decoderLayer(ModelTableLab.t5Base),
        )
    }

    @Test
    fun `halving the layers does not halve the model`() {
        assertEquals(0.606, DistillLab.sizeRatio, 0.005)
        val bert = ModelTableLab.breakdown(ModelTableLab.bertBase)
        val distil = ModelTableLab.breakdown(ModelTableLab.distilBert)
        assertEquals("the layers halve", 2.0, bert.encoder.toDouble() / distil.encoder, 0.01)
        assertTrue("the embeddings do not move", kotlin.math.abs(bert.embeddings - distil.embeddings) < 2000)
        assertTrue("so embeddings are a larger share of the student", ModelTableLab.embeddingShare(ModelTableLab.distilBert) > ModelTableLab.embeddingShare(ModelTableLab.bertBase))
    }

    // ── Distillation ─────────────────────────────────────────────────────────

    @Test
    fun `the teacher's off-top mass is the thing a hard label discards`() {
        val teacher = DistillLab.teacher
        assertEquals("this is a real distribution from a trained model", 1.0, teacher.sum(), 1e-9)
        assertEquals(0.704, DistillLab.offTopMass(teacher), 0.02)
        assertEquals(0.675, DistillLab.runnerUpRatio(teacher), 0.03)
    }

    @Test
    fun `temperature flattens it monotonically`() {
        val teacher = DistillLab.teacher
        val temperatures = listOf(1.0, 2.0, 4.0, 8.0)
        val entropies = temperatures.map { DistillLab.entropy(DistillLab.soften(teacher, it)) }
        assertTrue("entropy must rise with T", entropies.zipWithNext().all { (a, b) -> b > a })
        assertEquals(1.706, entropies.first(), 0.03)
        assertEquals(1.838, DistillLab.entropy(DistillLab.soften(teacher, 4.0)), 0.03)
        assertEquals(16.0, DistillLab.gradientScale(), 1e-9)
    }

    // ── Tokenizers ───────────────────────────────────────────────────────────

    @Test
    fun `all three tokenizers train on the same corpus to a comparable budget`() {
        val names = TokenizerLab.fertilities.map { it.name }
        assertTrue(names.containsAll(listOf("BPE", "WordPiece", "Unigram")))
        assertEquals(TokenizerLab.VOCAB_TARGET, TokenizerLab.bpe.vocabulary.size)
        assertEquals(TokenizerLab.VOCAB_TARGET, TokenizerLab.unigram.logProbabilities.size)
    }

    @Test
    fun `fertility is what separates them`() {
        val byName = TokenizerLab.fertilities.associateBy { it.name }
        assertEquals(1.667, byName.getValue("BPE").perWord, 0.01)
        assertEquals(1.667, byName.getValue("WordPiece").perWord, 0.01)
        assertEquals(1.500, byName.getValue("Unigram").perWord, 0.01)
        assertTrue(
            "Unigram is the shortest here — the Viterbi decode is what buys it",
            byName.getValue("Unigram").perWord < byName.getValue("BPE").perWord,
        )
        assertTrue(
            "and it does it at no larger a vocabulary",
            byName.getValue("Unigram").vocabulary <= byName.getValue("BPE").vocabulary,
        )
    }

    @Test
    fun `without a frequency floor WordPiece is worse than characters`() {
        // The measured justification for a hyper-parameter the copy says nobody reads.
        val floored = TokenizerLab.fertilities.first { it.name == "WordPiece" }
        val unfloored = TokenizerLab.fertilities.first { it.name.contains("floor") }
        assertEquals(4.333, unfloored.perWord, 0.05)
        assertTrue("the floor is what makes the criterion usable at this scale", floored.perWord < unfloored.perWord / 2)
        assertTrue("and it gets there with a smaller vocabulary than BPE needed", floored.vocabulary < TokenizerLab.VOCAB_TARGET)
    }

    @Test
    fun `they disagree on a word all three have seen`() {
        val segmentations = TokenizerLab.segmentations("gardener")
        assertEquals(3, segmentations.size)
        assertTrue("at least two of the three differ", segmentations.values.distinct().size > 1)
        assertEquals(listOf("garden", "##er"), segmentations.getValue("WordPiece"))
    }

    @Test
    fun `and only WordPiece loses an unseen word entirely`() {
        val segmentations = TokenizerLab.segmentations("unbelievable")
        assertEquals(listOf("[UNK]"), segmentations.getValue("WordPiece"))
        assertTrue("BPE falls back to pieces", segmentations.getValue("BPE").none { it == "[UNK]" })
        assertTrue("so does Unigram", segmentations.getValue("Unigram").none { it == "[UNK]" })
    }

    @Test
    fun `fertility is priced as attention work`() {
        assertEquals(100L, TokenizerLab.attentionCost(10))
        assertEquals(81L, TokenizerLab.attentionCost(9))
    }
}
