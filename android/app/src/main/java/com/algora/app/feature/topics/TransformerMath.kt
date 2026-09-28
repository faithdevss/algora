package com.algora.app.feature.topics

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

// ── D4 · Transformer internals and pre-trained LM math ───────────────────────
// Positional encodings, the feed-forward block's parameter share, BART's corruptions, XLNet's
// permutation objective, mixture-of-experts routing, the scaling laws behind the model families,
// and the attention/KV-cache cost that decides what a long context actually costs. Everything the
// eight labs quote is computed here and pinned by `D4MathTest`.

// ── Positional encodings ─────────────────────────────────────────────────────

internal object PositionalLab {

    val dim = 16
    val maxPosition = 32
    val base = 10_000.0

    /** The sinusoidal table: even dimensions sine, odd cosine, wavelengths in geometric progression. */
    fun encoding(position: Int, dimension: Int = dim): List<Double> =
        (0 until dimension).map { i ->
            val angle = position / base.pow(2.0 * (i / 2) / dimension)
            if (i % 2 == 0) sin(angle) else cos(angle)
        }

    fun wavelength(pairIndex: Int, dimension: Int = dim): Double =
        2 * Math.PI * base.pow(2.0 * pairIndex / dimension)

    fun dot(a: List<Double>, b: List<Double>): Double = a.indices.sumOf { a[it] * b[it] }

    fun cosine(a: List<Double>, b: List<Double>): Double =
        dot(a, b) / (sqrt(dot(a, a)) * sqrt(dot(b, b)))

    /**
     * The property the design is chosen for: the dot product of two encodings depends only on the
     * *offset* between them, not on where the pair sits in the sequence. Measured by comparing the
     * same offset at several absolute positions.
     */
    fun dotAtOffset(offset: Int, from: Int = 0): Double = dot(encoding(from), encoding(from + offset))

    fun offsetInvariance(offset: Int): Double {
        val samples = listOf(0, 4, 8, 12).map { dotAtOffset(offset, it) }
        return samples.max() - samples.min()
    }

    /**
     * The offset profile. Note what it is *not*: a clean monotone decay. The dot product falls from
     * offset 0, then oscillates (the probe measured 7.49, 6.37, 5.54, **5.56**, 6.14, 6.45 for
     * offsets 1–6), because it is a sum of cosines at different frequencies. "Similarity decreases
     * with distance" is the standard claim and it is only true on average — which is one reason
     * ALiBi, whose penalty *is* monotone by construction, replaced it in long-context models.
     */
    fun offsetProfile(maxOffset: Int = 12): List<Pair<Int, Double>> =
        (0..maxOffset).map { it to dotAtOffset(it) }

    /** Positions where the profile goes back up — evidence the decay is not monotone. */
    fun nonMonotonicOffsets(maxOffset: Int = 12): List<Int> {
        val profile = offsetProfile(maxOffset)
        return (1 until profile.size).filter { profile[it].second > profile[it - 1].second }.map { it }
    }

    /**
     * RoPE rotates each 2-D pair of the query/key by an angle proportional to position, so the
     * *dot product* of a rotated query and key depends only on their relative distance. Implemented
     * literally: rotate one vector to two absolute positions with the same gap and compare.
     */
    fun rotate(vector: List<Double>, position: Int): List<Double> {
        val out = vector.toMutableList()
        for (pair in 0 until vector.size / 2) {
            val theta = position / base.pow(2.0 * pair / vector.size)
            val x = vector[2 * pair]
            val y = vector[2 * pair + 1]
            out[2 * pair] = x * cos(theta) - y * sin(theta)
            out[2 * pair + 1] = x * sin(theta) + y * cos(theta)
        }
        return out
    }

    private val queryVector = List(dim) { i -> sin(0.7 * i + 0.3) }
    private val keyVector = List(dim) { i -> cos(0.4 * i - 0.2) }

    fun ropeScore(queryPosition: Int, keyPosition: Int): Double =
        dot(rotate(queryVector, queryPosition), rotate(keyVector, keyPosition))

    /** The relative-only guarantee, measured: same gap at different absolute positions. */
    fun ropeRelativeError(gap: Int): Double {
        val samples = listOf(0, 5, 10, 20).map { ropeScore(it + gap, it) }
        return samples.max() - samples.min()
    }

    /** ALiBi's per-head slopes: a geometric series, the bias added to attention scores. */
    fun alibiSlopes(heads: Int = 8): List<Double> =
        (1..heads).map { 2.0.pow(-8.0 * it / heads) }

    fun alibiBias(head: Int, distance: Int, heads: Int = 8): Double =
        -alibiSlopes(heads)[head] * distance

    /** Learned embeddings simply have no row past the trained length — the hard wall. */
    fun learnedTableParameters(maxLength: Int, dimension: Int = 768): Int = maxLength * dimension
}

// ── Feed-forward networks ────────────────────────────────────────────────────

internal object FfnLab {

    val dModel = 768
    val expansion = 4
    val dFf: Int get() = dModel * expansion

    /** Attention's four projections: Q, K, V and the output, each d × d. */
    fun attentionParameters(d: Int = dModel): Long = 4L * d * d

    /** The FFN's two matrices: d × 4d and 4d × d. */
    fun ffnParameters(d: Int = dModel, factor: Int = expansion): Long = 2L * factor * d * d

    fun blockParameters(d: Int = dModel): Long = attentionParameters(d) + ffnParameters(d)

    /** The number worth remembering: two thirds of a transformer block is the FFN, not attention. */
    fun ffnShare(d: Int = dModel): Double = ffnParameters(d).toDouble() / blockParameters(d)

    /**
     * SwiGLU uses three matrices instead of two, so the hidden width is cut to 8/3·d to keep the
     * parameter count the same — which is why LLaMA's FFN width is 11008 rather than 16384.
     */
    fun swigluHidden(d: Int, factor: Double = 8.0 / 3.0): Int = (d * factor).toInt()
    fun swigluParameters(d: Int, hidden: Int): Long = 3L * d * hidden

    /** LLaMA-7B's actual numbers, rounded to its multiple-of-256 constraint. */
    val llamaDModel = 4096
    val llamaHidden = 11008

    fun gelu(x: Double): Double = 0.5 * x * (1 + kotlin.math.tanh(sqrt(2 / Math.PI) * (x + 0.044715 * x * x * x)))
    fun relu(x: Double): Double = if (x > 0) x else 0.0

    /** GELU is smooth and non-monotonic near zero; ReLU is neither. The gap, measured. */
    fun activationGap(x: Double): Double = gelu(x) - relu(x)

    /**
     * The "key-value memory" reading: each FFN row is a pattern detector and each column a value
     * written when it fires. Sparsity is what makes that plausible — most units are near zero for
     * any one token.
     */
    fun activationSparsity(dimension: Int = 64, threshold: Double = 0.05): Double {
        val rng = java.util.Random(19)
        val active = (0 until dimension).count { relu(rng.nextGaussian() - 1.2) > threshold }
        return 1.0 - active.toDouble() / dimension
    }

    /** Per-token FLOPs, forward only: 2 multiply-adds per parameter. */
    fun ffnFlops(d: Int = dModel): Long = 2 * ffnParameters(d)
    fun attentionProjectionFlops(d: Int = dModel): Long = 2 * attentionParameters(d)
}

// ── BART: the denoising objective ────────────────────────────────────────────

internal object BartLab {

    val original = listOf("the", "cat", "sat", "on", "the", "mat", ".", "it", "purred", "loudly", ".")

    class Corruption(val name: String, val result: List<String>, val note: String)

    /** Independent tokens replaced by [MASK] — BERT's objective, one of BART's five. */
    fun tokenMasking(): Corruption {
        val out = original.toMutableList()
        listOf(1, 8).forEach { out[it] = "[MASK]" }
        return Corruption("Token masking", out, "BERT's objective: each masked slot is one token.")
    }

    /** Tokens deleted outright — the model must work out *where* something is missing. */
    fun tokenDeletion(): Corruption {
        val out = original.filterIndexed { i, _ -> i != 2 && i != 9 }
        return Corruption("Token deletion", out, "No placeholder, so the position is part of what must be predicted.")
    }

    /** A span of any length replaced by a single [MASK] — including a zero-length insertion. */
    fun textInfilling(): Corruption {
        val out = original.toMutableList()
        repeat(3) { out.removeAt(2) }
        out.add(2, "[MASK]")
        out.add(6, "[MASK]")
        return Corruption("Text infilling", out, "One mask can stand for 0, 1 or many tokens — the strongest of the five.")
    }

    fun sentencePermutation(): Corruption {
        val stop = original.indexOf(".")
        val first = original.subList(0, stop + 1)
        val second = original.subList(stop + 1, original.size)
        return Corruption("Sentence permutation", second + first, "Document-level order becomes something to restore.")
    }

    /**
     * Rotation pivots mid-sentence on purpose. With a pivot at the sentence boundary it produces
     * exactly the same string as sentence permutation on a two-sentence document, which made the
     * two corruptions indistinguishable in the lab — a real defect the probe caught.
     */
    fun documentRotation(): Corruption {
        val pivot = 4
        return Corruption("Document rotation", original.drop(pivot) + original.take(pivot), "Rotated to start at a token mid-sentence; the model has to find the real beginning.")
    }

    fun all(): List<Corruption> =
        listOf(tokenMasking(), tokenDeletion(), textInfilling(), sentencePermutation(), documentRotation())

    /**
     * Position-wise similarity is a useless measure here — a rotation preserves every token and
     * scores near zero on it. What actually separates the five corruptions is two independent
     * questions: are any tokens *gone*, and is the *order* changed. That 2×2 is the taxonomy.
     */
    fun tokensLost(corruption: Corruption): Int {
        val kept = corruption.result.count { it != "[MASK]" }
        return original.size - kept
    }

    fun lengthKnown(corruption: Corruption): Boolean =
        corruption.result.size == original.size

    fun orderChanged(corruption: Corruption): Boolean {
        val kept = corruption.result.filter { it != "[MASK]" }
        val originalOrder = original.filter { it in kept }
        return kept != originalOrder.filter { it in kept }.take(kept.size)
    }

    /** Every corruption produces a distinct string — the property the lab's frames depend on. */
    fun allDistinct(): Boolean = all().map { it.result }.distinct().size == all().size

    /** Parameter cost of the encoder-decoder shape: roughly 10% more than BERT-large. */
    val bartLargeParameters = 400_000_000L
    val bertLargeParameters = 340_000_000L
    val gpt2LargeParameters = 774_000_000L
}

// ── XLNet: permutation language modelling ────────────────────────────────────

internal object XlnetLab {

    val sentence = listOf("New", "York", "is", "a", "city")
    val maskedPositions = listOf(0, 1)

    /** Every factorization order of a short sequence — the space XLNet samples from. */
    fun factorizationOrders(length: Int = 4): List<List<Int>> {
        val out = mutableListOf<List<Int>>()
        fun permute(prefix: List<Int>, rest: List<Int>) {
            if (rest.isEmpty()) { out += prefix; return }
            rest.forEach { permute(prefix + it, rest - it) }
        }
        permute(emptyList(), (0 until length).toList())
        return out
    }

    fun orderCount(length: Int): Int = (1..length).fold(1) { acc, n -> acc * n }

    /**
     * BERT's independence assumption, made concrete. Masking both tokens of "New York" and
     * predicting them independently multiplies two marginals; the true joint is far higher, because
     * seeing one of the pair nearly determines the other.
     */
    val pNewGivenContext = 0.30
    val pYorkGivenContext = 0.35
    val pYorkGivenNew = 0.90

    fun independentJoint(): Double = pNewGivenContext * pYorkGivenContext
    fun trueJoint(): Double = pNewGivenContext * pYorkGivenNew
    fun independenceGap(): Double = trueJoint() / independentJoint()

    /**
     * The pretrain/finetune mismatch BERT carries and XLNet does not: [MASK] appears in 15% of
     * training positions and in none at fine-tuning time.
     */
    val bertMaskRate = 0.15
    val bertPredictedPerSequence = 0.15
    val xlnetPredictedPerSequence = 1.0 / 6

    /**
     * Two-stream attention: the content stream sees the token, the query stream sees only its
     * position — needed because a permutation-order prediction must know *which* position it is
     * predicting without seeing what is there.
     */
    fun contentStreamSees(order: List<Int>, step: Int): List<Int> = order.take(step + 1)
    fun queryStreamSees(order: List<Int>, step: Int): List<Int> = order.take(step)
}

// ── Mixture of experts ───────────────────────────────────────────────────────

internal object MoeLab {

    val experts = 8
    val topK = 2

    // Mixtral 8x7B, from the paper: the FFN blocks are replicated per expert, everything else shared.
    val totalParameters = 46.7
    val activeParameters = 12.9
    val denseEquivalent = 7.0

    fun activeFraction(): Double = activeParameters / totalParameters

    /** A small router over a token strip: logits per expert, softmax, then the top-k gate. */
    val tokens = listOf("the", "protein", "folds", "into", "a", "helix", "when", "cooled")

    fun routerLogits(token: String): List<Double> {
        var seed = token.hashCode()
        return (0 until experts).map {
            seed = seed * 1103515245 + 12345
            ((seed ushr 16) and 0x7fff) / 32767.0 * 4 - 2
        }
    }

    fun softmax(values: List<Double>): List<Double> {
        val max = values.max()
        val exps = values.map { exp(it - max) }
        val sum = exps.sum()
        return exps.map { it / sum }
    }

    class Route(val token: String, val experts: List<Int>, val weights: List<Double>)

    fun route(token: String): Route {
        val logits = routerLogits(token)
        val chosen = logits.indices.sortedByDescending { logits[it] }.take(topK)
        val renormalised = softmax(chosen.map { logits[it] })
        return Route(token, chosen, renormalised)
    }

    fun routes(): List<Route> = tokens.map { route(it) }

    /** Load per expert across the strip — the imbalance the auxiliary loss exists to punish. */
    fun expertLoad(): List<Int> {
        val counts = MutableList(experts) { 0 }
        routes().forEach { r -> r.experts.forEach { counts[it]++ } }
        return counts
    }

    fun loadImbalance(): Double {
        val load = expertLoad()
        val mean = load.average()
        return load.max() / mean
    }

    /** The auxiliary loss: experts × Σ (fraction of tokens routed) × (mean gate probability). */
    fun auxiliaryLoss(): Double {
        val fractions = expertLoad().map { it.toDouble() / (tokens.size * topK) }
        val gateMass = MutableList(experts) { 0.0 }
        tokens.forEach { token ->
            val probabilities = softmax(routerLogits(token))
            probabilities.forEachIndexed { i, p -> gateMass[i] += p / tokens.size }
        }
        return experts * fractions.indices.sumOf { fractions[it] * gateMass[it] }
    }

    /** What MoE buys: quality of a large model at the compute of a small one — memory unchanged. */
    fun computeSaving(): Double = 1 - activeFraction()
    fun memoryPenalty(): Double = totalParameters / denseEquivalent
}

// ── Scaling laws and the model families ──────────────────────────────────────

internal object ScalingLab {

    class Model(val name: String, val parameters: Double, val tokens: Double, val open: Boolean)

    /** Parameters in billions, training tokens in billions. Published figures. */
    val models = listOf(
        Model("GPT-3", 175.0, 300.0, open = false),
        Model("Chinchilla", 70.0, 1400.0, open = false),
        Model("LLaMA-1 65B", 65.0, 1400.0, open = true),
        Model("LLaMA-2 70B", 70.0, 2000.0, open = true),
        Model("LLaMA-3 70B", 70.0, 15000.0, open = true),
        Model("Mistral 7B", 7.0, 8000.0, open = true),
    )

    /** Chinchilla's result: compute-optimal training uses about 20 tokens per parameter. */
    val chinchillaRatio = 20.0

    fun ratio(model: Model): Double = model.tokens / model.parameters

    fun underTrained(): List<Model> = models.filter { ratio(it) < chinchillaRatio }

    /**
     * Kaplan-style power law, with the paper's coefficients: loss falls as a power of parameters.
     * Used only to draw the curve — the point is the shape, not the constant.
     */
    fun lossFromParameters(billions: Double): Double = (8.8e13 / (billions * 1e9)).pow(0.076)

    fun lossFromTokens(billions: Double): Double = (5.4e13 / (billions * 1e9)).pow(0.095)

    /** Training compute in FLOPs, the standard 6ND approximation. */
    fun trainingFlops(model: Model): Double = 6 * model.parameters * 1e9 * model.tokens * 1e9

    /** Inference cost is set by parameters alone, which is why over-training a small model wins. */
    fun inferenceFlopsPerToken(model: Model): Double = 2 * model.parameters * 1e9
}

// ── Context windows and what they cost ───────────────────────────────────────

internal object ContextLab {

    val contexts = listOf(4_096, 32_768, 128_000, 1_000_000)

    /** Attention is quadratic in sequence length: the score matrix alone is n² entries. */
    fun attentionScores(n: Int): Double = n.toDouble() * n

    fun relativeAttentionCost(n: Int, baseline: Int = 4_096): Double =
        attentionScores(n) / attentionScores(baseline)

    /**
     * The KV cache is what actually bounds a long context at serving time: 2 (K and V) × layers ×
     * heads × head_dim × 2 bytes per token, for a 70B-class model.
     */
    val layers = 80
    val heads = 64
    val headDim = 128
    val bytesPerValue = 2

    fun kvCacheBytesPerToken(): Long = 2L * layers * heads * headDim * bytesPerValue

    fun kvCacheGb(tokens: Int): Double = kvCacheBytesPerToken() * tokens.toDouble() / (1024.0 * 1024 * 1024)

    /** Grouped-query attention shares K/V across query heads — the standard fix. */
    val kvGroups = 8
    fun gqaCacheGb(tokens: Int): Double = kvCacheGb(tokens) * kvGroups / heads

    /**
     * The frontier families as of mid-2026, at the level of detail that does not rot: context
     * window and the shape of the offering. Prices move; these are the design facts.
     */
    class Family(val name: String, val vendor: String, val contextTokens: Int, val note: String)

    val families = listOf(
        Family("Claude Opus / Sonnet 5", "Anthropic", 1_000_000, "One-million-token context across the tier; trained with Constitutional AI."),
        Family("Claude Haiku 4.5", "Anthropic", 200_000, "The small, fast tier — a smaller window is part of the trade."),
        Family("Gemini 2.5 Pro / Flash", "Google", 1_000_000, "Natively multimodal from pretraining; mixture-of-experts; trained on TPUs."),
        Family("GPT-4-class", "OpenAI", 128_000, "The generation that set the 128K expectation."),
        Family("LLaMA 3 / Mistral", "Meta / Mistral", 128_000, "Open weights — the whole point is that you can run and fine-tune them."),
    )

    fun longestContext(): Family = families.maxByOrNull { it.contextTokens }!!
}
