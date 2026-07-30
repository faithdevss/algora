package com.algora.app.feature.topics

import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow
import kotlin.random.Random

// ── C6 · Pre-training objectives, model sizes and tokenizers ─────────────────
// BERT, GPT, T5, RoBERTa, DistilBERT and the Hugging Face tokenizer trio. Pinned by
// `PretrainMathTest`.
//
// Two kinds of thing are computed here and they are kept apart on purpose. The parameter tables are
// *arithmetic over published configs* — every count below is summed from a layer definition, not
// quoted, which is how the GPT-2 discrepancy surfaced. The objective comparisons are counting
// experiments over a real corpus: how many predictions each recipe extracts from a pass, how much
// context each prediction gets, and what a masking schedule covers.

// ── The shared corpus ────────────────────────────────────────────────────────

internal object PretrainCorpus {
    val sentences = listOf(
        "the cat sat on the mat",
        "the dog sat on the rug",
        "a small cat watched the bird",
        "the bird flew over the garden wall",
        "she opened the garden gate slowly",
        "he closed the gate behind her",
        "the children played in the garden",
        "a dog barked at the passing car",
        "the car stopped near the old wall",
        "she watched the children from the window",
    )

    val tokens: List<List<String>> = sentences.map { it.split(" ") }
    val tokenCount = tokens.sumOf { it.size }
    val vocabulary = tokens.flatten().distinct().sorted()
}

// ── Masked language modelling versus causal language modelling ───────────────

/**
 * The two objectives differ in how much of a pass they turn into training signal and in how much
 * context each prediction gets, and both are countable on a fixed corpus rather than arguable.
 *
 * The masking numbers follow BERT's published recipe exactly: 15% of positions selected, and of
 * those, 80% replaced with [MASK], 10% with a random token, 10% left alone.
 */
internal object PretrainLab {

    const val MASK_RATE = 0.15
    const val MASK_SHARE = 0.80
    const val RANDOM_SHARE = 0.10
    const val KEEP_SHARE = 0.10

    /** Every position is a target under a causal objective. */
    val causalTargets = PretrainCorpus.tokenCount

    /** 15% of them are, under masked LM. */
    val maskedTargets = Math.round(PretrainCorpus.tokenCount * MASK_RATE).toInt()

    val signalRatio = causalTargets.toDouble() / maskedTargets

    /**
     * Context per prediction. A causal model at position i sees i tokens; a masked model sees every
     * other position in the sequence. Averaged over the corpus, per prediction.
     */
    val causalContext: Double by lazy {
        val total = PretrainCorpus.tokens.sumOf { sentence -> sentence.indices.sumOf { it.toLong() } }
        total.toDouble() / PretrainCorpus.tokenCount
    }

    val maskedContext: Double by lazy {
        val total = PretrainCorpus.tokens.sumOf { sentence -> (sentence.size * (sentence.size - 1)).toLong() }
        total.toDouble() / PretrainCorpus.tokenCount
    }

    class Corruption(val masked: Int, val randomised: Int, val kept: Int) {
        val total get() = masked + randomised + kept
    }

    /** The 80/10/10 split, applied to this corpus and counted. */
    val corruption: Corruption by lazy {
        val masked = Math.round(maskedTargets * MASK_SHARE).toInt()
        val randomised = Math.round(maskedTargets * RANDOM_SHARE).toInt()
        Corruption(masked, randomised, maskedTargets - masked - randomised)
    }

    /**
     * The pre-train/fine-tune mismatch, as a fraction of input positions. [MASK] appears on 12% of
     * tokens during pre-training and on none at all afterwards, which is the whole reason for the
     * 10% random and 10% unchanged branches.
     */
    val maskTokenShare = MASK_RATE * MASK_SHARE

    /** One sampled masking of a sentence, for a lab that has to show the input the model sees. */
    fun maskSentence(sentence: List<String>, seed: Int = 5): List<Pair<String, String>> {
        val random = Random(seed)
        val count = kotlin.math.max(1, Math.round(sentence.size * MASK_RATE).toInt())
        val chosen = sentence.indices.shuffled(random).take(count).toSet()
        return sentence.mapIndexed { index, token ->
            if (index !in chosen) token to "kept" else {
                when (random.nextDouble()) {
                    in 0.0..MASK_SHARE -> "[MASK]" to "masked"
                    in MASK_SHARE..(MASK_SHARE + RANDOM_SHARE) ->
                        PretrainCorpus.vocabulary.random(random) to "random"
                    else -> token to "unchanged"
                }
            }
        }
    }

    /**
     * How much of the corpus a masking schedule ever reaches. A token is masked with probability
     * 0.15 per epoch, so after k independent maskings the chance it was never selected is 0.85^k —
     * which is RoBERTa's argument for re-masking every epoch instead of baking masks into the data.
     */
    fun neverMasked(epochs: Int) = (1 - MASK_RATE).pow(epochs)

    /** BERT duplicated its corpus 10× and trained 40 epochs, so each mask was reused four times. */
    const val STATIC_DUPLICATES = 10
    const val EPOCHS = 40

    val staticDistinctMasks = STATIC_DUPLICATES
    val dynamicDistinctMasks = EPOCHS
    val staticReuse = EPOCHS / STATIC_DUPLICATES
}

// ── T5: span corruption ──────────────────────────────────────────────────────

/**
 * T5 corrupts spans rather than single tokens and asks for only the corrupted spans back, so the
 * target is far shorter than the input. Both lengths are computed here from the published settings
 * — 15% of tokens corrupted, mean span 3 — on a real sentence.
 */
internal object SpanCorruptionLab {

    const val CORRUPTION_RATE = 0.15
    const val MEAN_SPAN = 3.0

    val sentence = "thank you for inviting me to your party last week".split(" ")

    class Corrupted(
        val input: List<String>,
        val target: List<String>,
        val spans: Int,
        val corruptedTokens: Int,
    )

    /** Deterministic span placement: the spans this sentence's length and rate imply. */
    fun corrupt(tokens: List<String> = sentence, seed: Int = 7): Corrupted {
        val random = Random(seed)
        val budget = kotlin.math.max(1, Math.round(tokens.size * CORRUPTION_RATE).toInt())
        val spanCount = kotlin.math.max(1, Math.round(budget / MEAN_SPAN).toInt())
        val lengths = MutableList(spanCount) { budget / spanCount }
        repeat(budget - lengths.sum()) { lengths[it % spanCount] += 1 }

        val starts = mutableListOf<Int>()
        var cursor = 1
        lengths.forEach { length ->
            val room = tokens.size - length - cursor
            if (room > 0) {
                val start = cursor + random.nextInt(kotlin.math.max(1, room / spanCount))
                starts += start
                cursor = start + length + 1
            }
        }

        val input = mutableListOf<String>()
        val target = mutableListOf<String>()
        var index = 0
        var sentinel = 0
        while (index < tokens.size) {
            val spanAt = starts.indexOf(index)
            if (spanAt >= 0) {
                val length = lengths[spanAt]
                input += "<X$sentinel>"
                target += "<X$sentinel>"
                target += tokens.subList(index, kotlin.math.min(index + length, tokens.size))
                sentinel += 1
                index += length
            } else {
                input += tokens[index]
                index += 1
            }
        }
        target += "<X$sentinel>"
        return Corrupted(input, target, sentinel, tokens.size - input.count { !it.startsWith("<X") } + 0)
    }

    /** BERT's target for the same budget: the model emits a prediction at every masked position. */
    fun maskedEquivalent(tokens: List<String> = sentence): Int =
        kotlin.math.max(1, Math.round(tokens.size * CORRUPTION_RATE).toInt())

    class AtScale(val tokens: Int, val corrupted: Int, val spans: Int) {
        val inputLength get() = tokens - corrupted + spans
        val targetLength get() = corrupted + spans + 1
        /** BERT emits a prediction at every position of a full-length sequence. */
        val maskedOutputLength get() = tokens
    }

    /**
     * The comparison at the length these models actually run: 512 tokens, 15% corrupted, mean span
     * 3. The short target is the point — a decoder pays per step it emits.
     */
    fun atScale(tokens: Int = 512): AtScale {
        val corrupted = Math.round(tokens * CORRUPTION_RATE).toInt()
        return AtScale(tokens, corrupted, Math.round(corrupted / MEAN_SPAN).toInt())
    }

    /** Decoder steps per pass, which is what the shorter target actually buys. */
    fun targetRatio(tokens: List<String> = sentence): Double {
        val corrupted = corrupt(tokens)
        return corrupted.target.size.toDouble() / tokens.size
    }
}

// ── Parameter tables, summed from published configs ──────────────────────────

/**
 * Every number here is a sum over a layer definition, in the same spirit as C3's CNN tables. Two
 * conventions are applied throughout and stated because they move the totals: layer-norm scale and
 * bias count as parameters, and a tied output embedding is counted once.
 */
internal object ModelTableLab {

    class Config(
        val name: String,
        val vocab: Int,
        val dim: Int,
        val layers: Int,
        val heads: Int,
        val ffn: Int,
        val positions: Int,
        val typeVocab: Int = 0,
        val pooler: Boolean = false,
        val biases: Boolean = true,
        val embeddingNorm: Boolean = false,
        val decoderLayers: Int = 0,
        val learnedPositions: Boolean = true,
    )

    val bertBase = Config("BERT-base", 30522, 768, 12, 12, 3072, 512, typeVocab = 2, pooler = true, embeddingNorm = true)
    // GPT-2 has no embedding layer norm and no pooler — the 1,536 parameters that separate a sum
    // that matches the released checkpoint exactly from one that is merely close.
    val gpt2Small = Config("GPT-2 small", 50257, 768, 12, 12, 3072, 1024)
    val robertaBase = Config("RoBERTa-base", 50265, 768, 12, 12, 3072, 514, typeVocab = 1, pooler = true, embeddingNorm = true)
    val distilBert = Config("DistilBERT", 30522, 768, 6, 12, 3072, 512, embeddingNorm = true)
    val t5Base = Config(
        "T5-base", 32128, 768, 12, 12, 3072, 0,
        biases = false, decoderLayers = 12, learnedPositions = false,
    )

    val all = listOf(bertBase, gpt2Small, robertaBase, distilBert, t5Base)

    class Breakdown(val embeddings: Long, val encoder: Long, val decoder: Long, val head: Long) {
        val total get() = embeddings + encoder + decoder + head
    }

    private fun linear(inDim: Int, outDim: Int, bias: Boolean) =
        inDim.toLong() * outDim + if (bias) outDim.toLong() else 0L

    private fun layerNorm(dim: Int) = 2L * dim

    /** Q, K, V and the output projection. Independent of the head count, which is the point. */
    fun attentionBlock(config: Config) = 4 * linear(config.dim, config.dim, config.biases)

    fun ffnBlock(config: Config) =
        linear(config.dim, config.ffn, config.biases) + linear(config.ffn, config.dim, config.biases)

    fun encoderLayer(config: Config) =
        attentionBlock(config) + ffnBlock(config) + 2 * layerNormFor(config)

    fun decoderLayer(config: Config) =
        2 * attentionBlock(config) + ffnBlock(config) + 3 * layerNormFor(config)

    // T5 uses RMSNorm: a scale, no bias.
    private fun layerNormFor(config: Config) = if (config.biases) layerNorm(config.dim) else config.dim.toLong()

    fun breakdown(config: Config): Breakdown {
        var embeddings = config.vocab.toLong() * config.dim
        if (config.learnedPositions) embeddings += config.positions.toLong() * config.dim
        if (config.typeVocab > 0) embeddings += config.typeVocab.toLong() * config.dim
        if (config.embeddingNorm) embeddings += layerNormFor(config)

        val encoder = config.layers * encoderLayer(config) +
            if (config.decoderLayers > 0) layerNormFor(config) else 0L
        val decoder = if (config.decoderLayers > 0) {
            config.decoderLayers * decoderLayer(config) + layerNormFor(config)
        } else {
            0L
        }
        var head = 0L
        if (config.pooler) head += linear(config.dim, config.dim, config.biases)
        if (!config.learnedPositions) head += 2L * 32 * config.heads   // T5's relative position bias
        if (!config.biases && config.decoderLayers == 0) head += layerNormFor(config)
        if (config.name == "GPT-2 small") head += layerNormFor(config)
        return Breakdown(embeddings, encoder, decoder, head)
    }

    fun total(config: Config) = breakdown(config).total

    /** What the published papers report, for comparison with the sums above. */
    val published = mapOf(
        "BERT-base" to 110_000_000L,
        "GPT-2 small" to 117_000_000L,
        "RoBERTa-base" to 125_000_000L,
        "DistilBERT" to 66_000_000L,
        "T5-base" to 220_000_000L,
    )

    /** The share of a model that is its embedding table — the number that decides vocab choices. */
    fun embeddingShare(config: Config) = breakdown(config).embeddings.toDouble() / total(config)
}

// ── Distillation ─────────────────────────────────────────────────────────────

/**
 * Distillation's claim is that a teacher's full distribution carries more than its argmax. That is
 * measurable on a real distribution: how much probability mass sits off the top choice, and what
 * temperature does to it. The distribution used is a real one — a decoding step from the trained
 * seq2seq model C5 ships — rather than a hand-written vector.
 */
internal object DistillLab {

    const val TEMPERATURE = 4.0

    /** A real next-symbol distribution from a trained model, taken at a step it is unsure about. */
    val teacher: DoubleArray by lazy {
        val model = Seq2SeqLab.forwardFed
        Seq2SeqLab.testSet
            .flatMap { model.decodeTrace(it.source) }
            .map { it.distribution }
            .maxBy { entropy(it) }
    }

    fun entropy(p: DoubleArray) = -p.filter { it > 0 }.sumOf { it * ln(it) }

    fun soften(p: DoubleArray, temperature: Double): DoubleArray {
        val logits = p.map { ln(it.coerceAtLeast(1e-12)) / temperature }
        val max = logits.max()
        val exps = logits.map { exp(it - max) }
        val sum = exps.sum()
        return DoubleArray(p.size) { exps[it] / sum }
    }

    /** Everything that is not the argmax — the part a hard label throws away. */
    fun offTopMass(p: DoubleArray) = 1.0 - p.max()

    /** Second-choice probability relative to the top one: how graded the teacher's opinion is. */
    fun runnerUpRatio(p: DoubleArray): Double {
        val sorted = p.sortedDescending()
        return sorted[1] / sorted[0]
    }

    /** Gradients from the soft term are scaled by 1/T², which is why the loss multiplies by T². */
    fun gradientScale(temperature: Double = TEMPERATURE) = temperature * temperature

    class SizeComparison(val name: String, val parameters: Long, val layers: Int)

    val sizes: List<SizeComparison> by lazy {
        listOf(
            SizeComparison("BERT-base", ModelTableLab.total(ModelTableLab.bertBase), ModelTableLab.bertBase.layers),
            SizeComparison("DistilBERT", ModelTableLab.total(ModelTableLab.distilBert), ModelTableLab.distilBert.layers),
        )
    }

    val sizeRatio: Double by lazy { sizes[1].parameters.toDouble() / sizes[0].parameters }
}

// ── Tokenizers: BPE, WordPiece and Unigram on one corpus ─────────────────────

/**
 * The three algorithms the Hugging Face tokenizers library ships, trained to the *same* vocabulary
 * size on the *same* corpus, then compared on held-out text. Trained rather than described: the
 * merges, the scores and the pruning all run here.
 *
 * The comparison that matters for a model builder is fertility — pieces per word — because it
 * decides sequence length, and sequence length decides attention cost.
 */
internal object TokenizerLab {

    const val VOCAB_TARGET = 90

    val corpus = PretrainCorpus.sentences + listOf(
        "the gardener watched the small birds",
        "the smallest bird landed on the gate",
        "she watched the gardener open the window",
        "the children watched the small dog",
        "a bird sang near the garden window",
    )

    val heldOut = "the gardener watched the smallest bird".split(" ")

    private val wordCounts: Map<String, Int> =
        corpus.flatMap { it.split(" ") }.groupingBy { it }.eachCount()

    // ── BPE ──────────────────────────────────────────────────────────────────

    class Bpe(val merges: List<Pair<String, String>>, val vocabulary: Set<String>)

    val bpe: Bpe by lazy {
        var pieces = wordCounts.mapKeys { (word, _) -> word.map { it.toString() } + "</w>" }
        val merges = mutableListOf<Pair<String, String>>()
        val vocabulary = pieces.keys.flatten().toMutableSet()
        while (vocabulary.size < VOCAB_TARGET) {
            val pairs = mutableMapOf<Pair<String, String>, Int>()
            pieces.forEach { (symbols, count) ->
                for (i in 0 until symbols.size - 1) {
                    val pair = symbols[i] to symbols[i + 1]
                    pairs[pair] = (pairs[pair] ?: 0) + count
                }
            }
            val best = pairs.maxByOrNull { it.value }?.key ?: break
            merges += best
            vocabulary += best.first + best.second
            pieces = pieces.mapKeys { (symbols, _) -> applyMerge(symbols, best) }
        }
        Bpe(merges, vocabulary)
    }

    private fun applyMerge(symbols: List<String>, pair: Pair<String, String>): List<String> {
        val out = mutableListOf<String>()
        var i = 0
        while (i < symbols.size) {
            if (i < symbols.size - 1 && symbols[i] == pair.first && symbols[i + 1] == pair.second) {
                out += pair.first + pair.second
                i += 2
            } else {
                out += symbols[i]
                i += 1
            }
        }
        return out
    }

    fun bpeEncode(word: String): List<String> {
        var symbols = word.map { it.toString() } + "</w>"
        bpe.merges.forEach { symbols = applyMerge(symbols, it) }
        return symbols
    }

    // ── WordPiece ────────────────────────────────────────────────────────────

    /**
     * Same loop as BPE with one line changed: the pair is scored by freq(ab)/(freq(a)·freq(b))
     * rather than by freq(ab). That prefers pairs whose halves are rare *apart*, which is the
     * likelihood criterion, and it produces a visibly different vocabulary from the same corpus.
     */
    class WordPiece(val vocabulary: Set<String>, val minFrequency: Int)

    /**
     * The floor exists because of what happens without it, which this lab measures rather than
     * asserts. freq(ab)/(freq(a)·freq(b)) is maximal — exactly 1 — for a pair whose two halves each
     * occur once, so on a corpus this size the criterion spends its whole budget on one-off letter
     * pairs and the common words are never merged at all. Real trainers run on billions of tokens
     * *and* set a minimum pair frequency; `wordPieceUnfloored` is the same trainer with the floor
     * removed, and the fertility gap between them is the argument.
     */
    const val MIN_PAIR_FREQUENCY = 4

    val wordPiece: WordPiece by lazy { trainWordPiece(MIN_PAIR_FREQUENCY) }

    val wordPieceUnfloored: WordPiece by lazy { trainWordPiece(1) }

    private fun trainWordPiece(minFrequency: Int): WordPiece {
        var pieces = wordCounts.mapKeys { (word, _) ->
            word.mapIndexed { index, c -> if (index == 0) c.toString() else "##$c" }
        }
        val vocabulary = pieces.keys.flatten().toMutableSet()
        while (vocabulary.size < VOCAB_TARGET) {
            val pairCounts = mutableMapOf<Pair<String, String>, Int>()
            val unitCounts = mutableMapOf<String, Int>()
            pieces.forEach { (symbols, count) ->
                symbols.forEach { unitCounts[it] = (unitCounts[it] ?: 0) + count }
                for (i in 0 until symbols.size - 1) {
                    val pair = symbols[i] to symbols[i + 1]
                    pairCounts[pair] = (pairCounts[pair] ?: 0) + count
                }
            }
            // The highest-scoring pair whose merged unit is new. Taking the top pair and stopping
            // when it is already known ends the loop after a handful of merges — a word's units are
            // reachable by several merge orders, so collisions are the common case, not the end.
            val best = pairCounts.entries
                .filter { it.value >= minFrequency }
                .sortedByDescending { (pair, count) ->
                    count.toDouble() / (unitCounts.getValue(pair.first) * unitCounts.getValue(pair.second))
                }
                .map { it.key }
                .firstOrNull { it.first + it.second.removePrefix("##") !in vocabulary }
                ?: break
            val merged = best.first + best.second.removePrefix("##")
            vocabulary += merged
            pieces = pieces.mapKeys { (symbols, _) -> applyWordPieceMerge(symbols, best) }
        }
        return WordPiece(vocabulary, minFrequency)
    }

    /**
     * Merging two WordPiece units drops the continuation marker on the right-hand one: "s" + "##m"
     * is the unit "sm", not "s##m". The first draft concatenated the raw strings, which put a unit
     * in the vocabulary under one name and in the working set under another — the merges after it
     * were then scored on units that did not exist, and fertility came out at 4.67 pieces per word,
     * worse than characters. Fertility being *worse than the baseline* is what caught it.
     */
    private fun applyWordPieceMerge(symbols: List<String>, pair: Pair<String, String>): List<String> {
        val joined = pair.first + pair.second.removePrefix("##")
        val out = mutableListOf<String>()
        var i = 0
        while (i < symbols.size) {
            if (i < symbols.size - 1 && symbols[i] == pair.first && symbols[i + 1] == pair.second) {
                out += joined
                i += 2
            } else {
                out += symbols[i]
                i += 1
            }
        }
        return out
    }

    /** Greedy longest-match-first, which is what WordPiece uses at inference. */
    fun wordPieceEncode(word: String, model: WordPiece = wordPiece): List<String> {
        val out = mutableListOf<String>()
        var start = 0
        while (start < word.length) {
            var end = word.length
            var found: String? = null
            while (end > start) {
                val candidate = if (start == 0) word.substring(start, end) else "##" + word.substring(start, end)
                if (candidate in model.vocabulary) {
                    found = candidate
                    break
                }
                end -= 1
            }
            if (found == null) return listOf("[UNK]")
            out += found
            start = end
        }
        return out
    }

    // ── Unigram ──────────────────────────────────────────────────────────────

    /**
     * The other direction entirely: start from a large candidate set, score every piece by how much
     * the corpus likelihood would fall without it, and prune. Encoding is then a Viterbi search for
     * the most probable segmentation rather than a replay of merges — which is why Unigram can
     * produce a segmentation no greedy merge order would reach.
     */
    class Unigram(val logProbabilities: Map<String, Double>)

    val unigram: Unigram by lazy {
        val candidates = mutableMapOf<String, Double>()
        wordCounts.forEach { (word, count) ->
            for (start in word.indices) {
                for (end in start + 1..kotlin.math.min(word.length, start + 6)) {
                    val piece = word.substring(start, end)
                    candidates[piece] = (candidates[piece] ?: 0.0) + count
                }
            }
        }
        var scores = candidates.toMutableMap()
        while (scores.size > VOCAB_TARGET) {
            val total = scores.values.sum()
            val logProbabilities = scores.mapValues { ln(it.value / total) }
            // One EM pass: re-estimate each piece's count from the best segmentation it appears in.
            val expected = mutableMapOf<String, Double>()
            wordCounts.forEach { (word, count) ->
                viterbi(word, logProbabilities).forEach { piece ->
                    expected[piece] = (expected[piece] ?: 0.0) + count
                }
            }
            val singleCharacters = scores.keys.filter { it.length == 1 }.toSet()
            val prunable = scores.keys.filter { it !in singleCharacters }
                .sortedBy { expected[it] ?: 0.0 }
            val drop = kotlin.math.max(1, (scores.size - VOCAB_TARGET).coerceAtMost(prunable.size / 5 + 1))
            prunable.take(drop).forEach { scores.remove(it) }
            if (prunable.isEmpty()) break
            scores = scores.mapValues { (piece, _) -> (expected[piece] ?: 0.0) + 0.1 }.toMutableMap()
        }
        val total = scores.values.sum()
        Unigram(scores.mapValues { ln(it.value / total) })
    }

    private fun viterbi(word: String, logProbabilities: Map<String, Double>): List<String> {
        val best = DoubleArray(word.length + 1) { Double.NEGATIVE_INFINITY }
        val back = IntArray(word.length + 1) { -1 }
        best[0] = 0.0
        for (end in 1..word.length) {
            for (start in 0 until end) {
                val piece = word.substring(start, end)
                val score = logProbabilities[piece] ?: continue
                if (best[start] + score > best[end]) {
                    best[end] = best[start] + score
                    back[end] = start
                }
            }
        }
        if (best[word.length] == Double.NEGATIVE_INFINITY) return listOf("[UNK]")
        val pieces = mutableListOf<String>()
        var at = word.length
        while (at > 0) {
            val start = back[at]
            pieces += word.substring(start, at)
            at = start
        }
        return pieces.reversed()
    }

    fun unigramEncode(word: String) = viterbi(word, unigram.logProbabilities)

    // ── Comparison ───────────────────────────────────────────────────────────

    class Fertility(val name: String, val vocabulary: Int, val pieces: Int, val words: Int) {
        val perWord get() = pieces.toDouble() / words
    }

    val fertilities: List<Fertility> by lazy {
        listOf(
            Fertility("BPE", bpe.vocabulary.size, heldOut.sumOf { bpeEncode(it).size }, heldOut.size),
            Fertility("WordPiece", wordPiece.vocabulary.size, heldOut.sumOf { wordPieceEncode(it).size }, heldOut.size),
            Fertility(
                "WordPiece, no frequency floor",
                wordPieceUnfloored.vocabulary.size,
                heldOut.sumOf { wordPieceEncode(it, wordPieceUnfloored).size },
                heldOut.size,
            ),
            Fertility("Unigram", unigram.logProbabilities.size, heldOut.sumOf { unigramEncode(it).size }, heldOut.size),
        )
    }

    /** The same word through all three, which is where they visibly disagree. */
    fun segmentations(word: String) = mapOf(
        "BPE" to bpeEncode(word),
        "WordPiece" to wordPieceEncode(word),
        "Unigram" to unigramEncode(word),
    )

    /** Sequence length is attention cost: n² scores for n pieces. */
    fun attentionCost(pieces: Int) = pieces.toLong() * pieces
}
