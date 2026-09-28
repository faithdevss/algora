package com.algora.app.feature.topics

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.math.tanh
import kotlin.random.Random

// ── D6 · Fine-tuning, efficiency and the post-transformer architectures ──────
// Full fine-tuning, DPO, PEFT, LoRA/QLoRA, quantization, Flash Attention, SSMs, Mamba, RWKV and long
// context. Everything the ten labs quote is computed here and pinned by `D6MathTest`.
//
// Three kinds of claim live in this file and they fail differently, so they are kept apart the way
// `GenerativeMath` keeps its three apart.
//
//   Accounting — parameter counts, optimizer-state bytes, KV-cache bytes, FLOP crossovers, HBM
//   traffic. Exact arithmetic over a stated model config, asserted to the digit. These are the
//   numbers that decide what runs on what hardware, and every one of them is somebody's published
//   headline with the denominator left off.
//
//   Identities — the online-softmax recurrence against the naive softmax, an SSM's recurrent scan
//   against its convolution kernel, RWKV's stabilised WKV against its overflowing textbook form.
//   Exact equalities, asserted to floating-point tolerance, because each one reads like an
//   approximation and is not.
//
//   Experiments — the three fine-tuning regimes, DPO's overoptimization curve, LoRA's rank sweep,
//   quantization error, the selective-copy task. These train or sample, so the test pins the
//   *ordering* as well as the value: a drifted number is inaccurate, an inverted ordering makes the
//   topic pointless.

// ── Shared numeric helpers ───────────────────────────────────────────────────

private fun ftGaussian(random: Random): Double {
    val u1 = random.nextDouble().coerceAtLeast(1e-12)
    val u2 = random.nextDouble()
    return sqrt(-2.0 * ln(u1)) * cos(2.0 * PI * u2)
}

private fun ftSigmoid(x: Double) = 1.0 / (1.0 + exp(-x))

private fun ftSoftmax(logits: DoubleArray): DoubleArray {
    val m = logits.max()
    val e = DoubleArray(logits.size) { exp(logits[it] - m) }
    val s = e.sum()
    return DoubleArray(logits.size) { e[it] / s }
}

/** Eigenvalues of a symmetric matrix by cyclic Jacobi rotations, largest first. */
private fun ftSymmetricEigenvalues(input: Array<DoubleArray>): DoubleArray {
    val n = input.size
    val a = Array(n) { input[it].copyOf() }
    repeat(80) {
        var off = 0.0
        for (p in 0 until n) for (q in p + 1 until n) off += a[p][q] * a[p][q]
        if (off < 1e-22) return@repeat
        for (p in 0 until n) for (q in p + 1 until n) {
            if (abs(a[p][q]) < 1e-14) continue
            val theta = (a[q][q] - a[p][p]) / (2.0 * a[p][q])
            val t = (if (theta >= 0.0) 1.0 else -1.0) / (abs(theta) + sqrt(theta * theta + 1.0))
            val c = 1.0 / sqrt(t * t + 1.0)
            val s = t * c
            for (k in 0 until n) {
                val kp = a[k][p]
                val kq = a[k][q]
                a[k][p] = c * kp - s * kq
                a[k][q] = s * kp + c * kq
            }
            for (k in 0 until n) {
                val pk = a[p][k]
                val qk = a[q][k]
                a[p][k] = c * pk - s * qk
                a[q][k] = s * pk + c * qk
            }
        }
    }
    return DoubleArray(n) { a[it][it] }.sortedArrayDescending()
}

/** Singular values of an arbitrary matrix, via the eigenvalues of `mᵀm`. */
private fun ftSingularValues(m: Array<DoubleArray>): DoubleArray {
    val cols = m[0].size
    val gram = Array(cols) { i -> DoubleArray(cols) { j -> m.sumOf { it[i] * it[j] } } }
    return ftSymmetricEigenvalues(gram).map { sqrt(max(0.0, it)) }.toDoubleArray()
}

// ── Fine-Tuning (Full) ───────────────────────────────────────────────────────

/**
 * The three regimes a practitioner actually chooses between, trained for real on the same data:
 * train from scratch, freeze the body and fit a new head (feature extraction), or fine-tune
 * everything from the pretrained weights.
 *
 * The model is a one-hidden-layer tanh network. Pretraining is **multi-task** — six upstream heads
 * read the same body — and that detail is not decoration: the first version of this lab pretrained
 * on a single scalar output, which only needs one direction of the representation, so the "reusable
 * feature extractor" it was supposed to produce collapsed and feature extraction could never fit
 * anything. Six heads force the body to keep a full-rank representation, which is what makes the
 * frozen features worth anything downstream.
 *
 * Downstream is a seventh head on the same features, and the downstream teacher perturbs the body by
 * 10% so that full fine-tuning has a real ceiling advantage rather than a rigged one.
 */
internal object FineTuneLab {

    const val inDim = 8
    const val hidden = 6
    const val upstreamTasks = 6

    /** Head-only trains `hidden` weights; full fine-tuning trains those plus the body matrix. */
    val headParams = hidden
    val bodyParams = hidden * inDim
    val fullParams = headParams + bodyParams

    class Body(val weights: Array<DoubleArray>) {
        fun features(x: DoubleArray) = DoubleArray(hidden) { h ->
            tanh((0 until inDim).sumOf { weights[h][it] * x[it] })
        }

        fun copy() = Body(Array(hidden) { weights[it].copyOf() })
    }

    private fun readout(f: DoubleArray, head: DoubleArray) = (0 until hidden).sumOf { f[it] * head[it] }

    private val teacherRandom = Random(7)

    private val teacherBody = Body(Array(hidden) { DoubleArray(inDim) { ftGaussian(teacherRandom) * 0.9 } })
    private val teacherHeads = Array(upstreamTasks) { DoubleArray(hidden) { ftGaussian(teacherRandom) * 0.9 } }
    private val downstreamBody = Body(
        Array(hidden) { h -> DoubleArray(inDim) { i -> teacherBody.weights[h][i] + 0.10 * ftGaussian(teacherRandom) } },
    )
    private val downstreamHead = DoubleArray(hidden) { ftGaussian(teacherRandom) * 0.9 }

    private fun upstreamSample(count: Int, seed: Int, noise: Double): List<Pair<DoubleArray, DoubleArray>> {
        val random = Random(seed)
        return (0 until count).map {
            val x = DoubleArray(inDim) { ftGaussian(random) }
            val f = teacherBody.features(x)
            x to DoubleArray(upstreamTasks) { t -> readout(f, teacherHeads[t]) + noise * ftGaussian(random) }
        }
    }

    private fun downstreamSample(count: Int, seed: Int, noise: Double): List<Pair<DoubleArray, Double>> {
        val random = Random(seed)
        return (0 until count).map {
            val x = DoubleArray(inDim) { ftGaussian(random) }
            x to readout(downstreamBody.features(x), downstreamHead) + noise * ftGaussian(random)
        }
    }

    private val upstreamTrain = upstreamSample(200, seed = 21, noise = 0.05)
    private val upstreamTest = upstreamSample(200, seed = 22, noise = 0.0)
    private val downstreamTest = downstreamSample(200, seed = 23, noise = 0.0)
    private val downstreamPool = downstreamSample(256, seed = 24, noise = 0.05)

    fun upstreamLoss(body: Body, heads: Array<DoubleArray>): Double =
        upstreamTest.sumOf { (x, y) ->
            val f = body.features(x)
            (0 until upstreamTasks).sumOf { t -> (readout(f, heads[t]) - y[t]).let { it * it } }
        } / (upstreamTest.size * upstreamTasks)

    fun downstreamLoss(body: Body, head: DoubleArray): Double =
        downstreamTest.sumOf { (x, y) -> (readout(body.features(x), head) - y).let { it * it } } / downstreamTest.size

    /** Multi-task pretraining: one body, six heads, full-batch gradient descent. */
    private fun pretrain(steps: Int, lr: Double): Pair<Body, Array<DoubleArray>> {
        val init = Random(31)
        val body = Body(Array(hidden) { DoubleArray(inDim) { ftGaussian(init) * 0.4 } })
        val heads = Array(upstreamTasks) { DoubleArray(hidden) { ftGaussian(init) * 0.4 } }
        repeat(steps) {
            val headGrad = Array(upstreamTasks) { DoubleArray(hidden) }
            val bodyGrad = Array(hidden) { DoubleArray(inDim) }
            upstreamTrain.forEach { (x, y) ->
                val f = body.features(x)
                for (t in 0 until upstreamTasks) {
                    val err = readout(f, heads[t]) - y[t]
                    val scale = 2.0 * err / (upstreamTrain.size * upstreamTasks)
                    for (h in 0 until hidden) {
                        headGrad[t][h] += scale * f[h]
                        val through = scale * heads[t][h] * (1.0 - f[h] * f[h])
                        for (i in 0 until inDim) bodyGrad[h][i] += through * x[i]
                    }
                }
            }
            for (t in 0 until upstreamTasks) for (h in 0 until hidden) heads[t][h] -= lr * headGrad[t][h]
            for (h in 0 until hidden) for (i in 0 until inDim) body.weights[h][i] -= lr * bodyGrad[h][i]
        }
        return body to heads
    }

    /** Downstream training. `trainBody = false` is feature extraction: the body is frozen. */
    private fun tune(
        startBody: Body,
        startHead: DoubleArray,
        data: List<Pair<DoubleArray, Double>>,
        steps: Int,
        lr: Double,
        trainBody: Boolean,
    ): Pair<Body, DoubleArray> {
        val body = startBody.copy()
        val head = startHead.copyOf()
        repeat(steps) {
            val headGrad = DoubleArray(hidden)
            val bodyGrad = Array(hidden) { DoubleArray(inDim) }
            data.forEach { (x, y) ->
                val f = body.features(x)
                val scale = 2.0 * (readout(f, head) - y) / data.size
                for (h in 0 until hidden) {
                    headGrad[h] += scale * f[h]
                    if (trainBody) {
                        val through = scale * head[h] * (1.0 - f[h] * f[h])
                        for (i in 0 until inDim) bodyGrad[h][i] += through * x[i]
                    }
                }
            }
            for (h in 0 until hidden) {
                head[h] -= lr * headGrad[h]
                if (trainBody) for (i in 0 until inDim) body.weights[h][i] -= lr * bodyGrad[h][i]
            }
        }
        return body to head
    }

    /** The pretrained checkpoint. */
    val pretrained: Pair<Body, Array<DoubleArray>> by lazy { pretrain(steps = 4_000, lr = 0.30) }

    val pretrainedUpstreamLoss: Double by lazy { upstreamLoss(pretrained.first, pretrained.second) }

    class Regime(val name: String, val trainable: Int, val downstreamLoss: Double, val upstreamLoss: Double?)

    val exampleCounts = listOf(4, 8, 16, 32, 64, 256)

    private fun freshHead() = DoubleArray(hidden) { ftGaussian(Random(41 + it)) * 0.2 }

    private val regimeCache = mutableMapOf<Int, List<Regime>>()

    fun regimes(n: Int): List<Regime> = regimeCache.getOrPut(n) {
        val data = downstreamPool.take(n)
        val scratchInit = Random(51)
        val scratchBody = Body(Array(hidden) { DoubleArray(inDim) { ftGaussian(scratchInit) * 0.4 } })
        val scratch = tune(scratchBody, freshHead(), data, 2_000, 0.15, trainBody = true)
        val frozen = tune(pretrained.first, freshHead(), data, 2_000, 0.15, trainBody = false)
        val full = tune(pretrained.first, freshHead(), data, 2_000, 0.15, trainBody = true)
        listOf(
            Regime("from scratch", fullParams, downstreamLoss(scratch.first, scratch.second), null),
            Regime("head only", headParams, downstreamLoss(frozen.first, frozen.second), pretrainedUpstreamLoss),
            Regime(
                "full fine-tune",
                fullParams,
                downstreamLoss(full.first, full.second),
                upstreamLoss(full.first, pretrained.second),
            ),
        )
    }

    /** Smallest example count at which full fine-tuning beats feature extraction downstream. */
    fun crossoverExamples(): Int? = exampleCounts.firstOrNull { n ->
        val r = regimes(n)
        r[2].downstreamLoss < r[1].downstreamLoss
    }

    /** How much worse the upstream tasks get after the body is fine-tuned away from them. */
    fun forgettingRatio(n: Int): Double = regimes(n)[2].upstreamLoss!! / pretrainedUpstreamLoss

    // Mixed-precision Adam, the standard recipe: fp16 weights and grads, an fp32 master copy, and
    // two fp32 moment buffers. A frozen parameter needs only its fp16 weight.
    const val trainableBytesPerParam = 16
    const val frozenBytesPerParam = 2

    fun trainingBytes(totalParams: Long, trainableParams: Long): Long =
        trainableParams * trainableBytesPerParam + (totalParams - trainableParams) * frozenBytesPerParam
}

// ── DPO (Direct Preference Optimization) ─────────────────────────────────────

/**
 * Both alignment pipelines, run end to end on one prompt with six candidate responses.
 *
 * RLHF is two stages: fit a Bradley-Terry reward model to the preference pairs, then optimise
 * `E[r̂] − β·KL(π‖π_ref)`, which on a tabular policy has the closed form `π ∝ π_ref·exp(r̂/β)`.
 * DPO skips the reward model and descends the pairwise logistic loss on the policy directly.
 *
 * The point of doing both is that the theorem says they land in the same place, and that is checkable
 * rather than quotable. The other point is that neither of them optimises *quality* — they optimise
 * agreement with a finite, noisy preference sample, and the true-quality curve says what that costs.
 */
internal object DpoLab {

    val responses = listOf(
        "cites the passage",
        "correct, no citation",
        "hedged and correct",
        "fluent and unsupported",
        "confidently wrong",
        "refuses to answer",
    )

    /** Ground truth the annotators are noisy about, and which no part of the pipeline ever sees. */
    val quality = doubleArrayOf(2.4, 1.6, 1.1, -0.4, -1.8, 0.2)

    /**
     * The reference policy. Its mode is "fluent and unsupported" — the base model's failure mode and
     * the reason there is anything to align.
     */
    val referenceLogits = doubleArrayOf(0.1, 0.4, -0.2, 1.3, 0.6, -0.9)

    val reference: DoubleArray = ftSoftmax(referenceLogits)

    val pairs: List<Pair<Int, Int>> =
        responses.indices.flatMap { i -> (i + 1 until responses.size).map { j -> i to j } }

    /** Three independent annotators per pair, each flipping a coin weighted by Bradley-Terry. */
    const val annotatorsPerPair = 3

    /** Majority-vote labels: `first` is the winner, `second` the loser, `third` the vote count. */
    val preferences: List<Triple<Int, Int, Int>> by lazy {
        val random = Random(97)
        pairs.map { (i, j) ->
            val p = ftSigmoid(quality[i] - quality[j])
            val votes = (0 until annotatorsPerPair).count { random.nextDouble() < p }
            if (votes * 2 > annotatorsPerPair) Triple(i, j, votes) else Triple(j, i, annotatorsPerPair - votes)
        }
    }

    /** Pairs where the annotators contradicted the ground-truth ordering. */
    fun mislabelledPairs(): List<Triple<Int, Int, Int>> =
        preferences.filter { quality[it.first] < quality[it.second] }

    /**
     * Preference cycles: a > b > c > a. Bradley-Terry assigns one scalar per response, so no setting
     * of its parameters can reproduce a cycle — every cycle is an error the reward model must make.
     */
    fun preferenceCycles(): List<Triple<Int, Int, Int>> {
        val beats = preferences.map { it.first to it.second }.toSet()
        val n = responses.size
        val cycles = mutableListOf<Triple<Int, Int, Int>>()
        for (a in 0 until n) for (b in a + 1 until n) for (c in b + 1 until n) {
            if (a to b in beats && b to c in beats && c to a in beats) cycles += Triple(a, b, c)
            if (a to c in beats && c to b in beats && b to a in beats) cycles += Triple(a, c, b)
        }
        return cycles
    }

    /**
     * Stage one of RLHF: a Bradley-Terry reward fit by gradient ascent, centred at zero.
     *
     * The L2 term is not decoration. Without it the fit is unbounded — the responses that beat
     * everything and lose to nothing have their rewards pushed up forever, so the "fitted reward" is
     * whatever number the loop was stopped at. Every production reward model carries the same term
     * for the same reason.
     */
    const val rewardWeightDecay = 0.02

    val rewardModel: DoubleArray by lazy {
        val r = DoubleArray(responses.size)
        repeat(20_000) {
            val grad = DoubleArray(responses.size)
            preferences.forEach { (w, l, _) ->
                val g = 1.0 - ftSigmoid(r[w] - r[l])
                grad[w] += g
                grad[l] -= g
            }
            for (i in r.indices) r[i] += 0.2 * (grad[i] / preferences.size - rewardWeightDecay * r[i])
        }
        val mean = r.average()
        DoubleArray(r.size) { r[it] - mean }
    }

    fun rewardAccuracy(): Double =
        preferences.count { (w, l, _) -> rewardModel[w] > rewardModel[l] }.toDouble() / preferences.size

    /** The quality of the single best response — what a perfect aligner would put all its mass on. */
    val bestPossibleQuality: Double = quality.max()

    /**
     * The ceiling the *preference data* imposes, measured as the quality of the best policy any
     * amount of optimization against the fitted reward can reach. Whatever is left between this and
     * [bestPossibleQuality] cannot be recovered by tuning β, by more RL steps, or by switching from
     * RLHF to DPO — it is an error in the reward, not in the optimizer.
     */
    fun alignmentCeiling(): Double = expectedQuality(rlhfPolicy(0.01))

    /** Stage two of RLHF, in closed form: the KL-regularised optimum against the *fitted* reward. */
    fun rlhfPolicy(beta: Double): DoubleArray =
        ftSoftmax(DoubleArray(responses.size) { ln(reference[it]) + rewardModel[it] / beta })

    fun kl(policy: DoubleArray, ref: DoubleArray = reference): Double =
        policy.indices.sumOf { if (policy[it] < 1e-12) 0.0 else policy[it] * ln(policy[it] / ref[it]) }

    fun expectedQuality(policy: DoubleArray): Double = policy.indices.sumOf { policy[it] * quality[it] }

    class DpoStep(val step: Int, val policy: DoubleArray, val kl: Double, val quality: Double, val loss: Double)

    /**
     * DPO by gradient descent on the policy logits. The trajectory is the interesting object, not the
     * endpoint: with hard preference labels and a tabular policy the loss keeps falling as the
     * implicit rewards diverge, so there is no endpoint — only a point past which you should stop.
     */
    fun dpoTrajectory(beta: Double, steps: Int = 4_000, record: Int = 100): List<DpoStep> {
        val theta = referenceLogits.copyOf()
        val out = mutableListOf<DpoStep>()
        fun snapshot(step: Int) {
            val pi = ftSoftmax(theta)
            val loss = preferences.sumOf { (w, l, _) ->
                val margin = beta * (ln(pi[w] / reference[w]) - ln(pi[l] / reference[l]))
                -ln(ftSigmoid(margin).coerceAtLeast(1e-15))
            } / preferences.size
            out += DpoStep(step, pi, kl(pi), expectedQuality(pi), loss)
        }
        snapshot(0)
        repeat(steps) { s ->
            val pi = ftSoftmax(theta)
            val dLogPi = DoubleArray(responses.size)
            preferences.forEach { (w, l, _) ->
                val margin = beta * (ln(pi[w] / reference[w]) - ln(pi[l] / reference[l]))
                val g = beta * (1.0 - ftSigmoid(margin)) / preferences.size
                dLogPi[w] += g
                dLogPi[l] -= g
            }
            // d log π_k / dθ_j = [k == j] − π_j, so the logit step is the centred version of dLogPi.
            val total = dLogPi.sum()
            for (j in theta.indices) theta[j] += 0.5 * (dLogPi[j] - pi[j] * total)
            if ((s + 1) % record == 0) snapshot(s + 1)
        }
        return out
    }

    /** The step at which true quality peaks, and everything after it is overoptimization. */
    fun peakQualityStep(beta: Double): DpoStep = dpoTrajectory(beta).maxBy { it.quality }

    /**
     * The theorem, checked: at matched KL the DPO policy and the closed-form RLHF policy agree. The
     * search finds the β whose closed form sits at the DPO trajectory's KL.
     */
    fun matchedPolicyGap(beta: Double, step: Int): Pair<Double, Double> {
        val target = dpoTrajectory(beta).first { it.step == step }
        var lo = 0.01
        var hi = 20.0
        repeat(80) {
            val mid = sqrt(lo * hi)
            if (kl(rlhfPolicy(mid)) > target.kl) lo = mid else hi = mid
        }
        val matched = rlhfPolicy(sqrt(lo * hi))
        val gap = matched.indices.maxOf { abs(matched[it] - target.policy[it]) }
        return sqrt(lo * hi) to gap
    }
}

// ── PEFT (Parameter-Efficient Fine-Tuning) ───────────────────────────────────

/**
 * Exact parameter accounting for one concrete model — BERT-base's config — under six tuning methods,
 * then the memory those counts actually buy.
 *
 * The headline every PEFT paper leads with is the trainable percentage. The number that decides
 * whether the job fits on the card is total training memory, and it does not move nearly as far,
 * because the frozen weights and the activations are still there. Both are computed.
 */
internal object PeftLab {

    const val layers = 12
    const val dModel = 768
    const val dFfn = 3072
    const val vocab = 30_522
    const val maxPositions = 512
    const val typeVocab = 2

    val embeddingParams: Long =
        (vocab.toLong() + maxPositions + typeVocab) * dModel + 2L * dModel

    /** Per encoder layer: four attention projections, two FFN matrices, two LayerNorms. */
    val attentionParams: Long = 4L * (dModel.toLong() * dModel + dModel)
    val ffnParams: Long = dModel.toLong() * dFfn + dFfn + dFfn.toLong() * dModel + dModel
    val layerNormParams: Long = 2L * 2L * dModel
    val perLayerParams: Long = attentionParams + ffnParams + layerNormParams

    val totalParams: Long = embeddingParams + layers * perLayerParams

    /** Every bias and every LayerNorm scale/shift — what BitFit trains. */
    val biasAndNormParams: Long =
        2L * dModel + layers * (4L * dModel + dFfn + dModel + layerNormParams)

    const val loraRank = 8
    const val adapterBottleneck = 64
    const val prefixLength = 32

    class Method(val name: String, val trainable: Long, val note: String) {
        fun sharePercent(total: Long) = 100.0 * trainable / total
    }

    val methods: List<Method> by lazy {
        listOf(
            Method("full fine-tuning", totalParams, "every weight, every bias, every embedding"),
            Method(
                "BitFit (biases only)",
                biasAndNormParams,
                "no matrix is touched — only the shifts",
            ),
            Method(
                "LoRA r=$loraRank (Wq, Wv)",
                layers * 2L * 2L * dModel * loraRank,
                "two rank-$loraRank factors per attention projection",
            ),
            Method(
                "adapters (bottleneck $adapterBottleneck)",
                layers * 2L * (2L * dModel * adapterBottleneck + adapterBottleneck + dModel),
                "a down/up pair inserted twice per layer",
            ),
            Method(
                "prefix tuning (len $prefixLength)",
                layers * 2L * prefixLength * dModel,
                "$prefixLength learned key/value vectors per layer",
            ),
            Method(
                "IA³ (learned rescaling)",
                layers * (2L * dModel + dFfn),
                "one multiplier per key, value and FFN channel",
            ),
        )
    }

    // Activation memory for one training step, fp16. The standard per-layer estimate for a
    // transformer block: roughly 34·b·s·d bytes plus the 5·b·s²·h attention-matrix term.
    const val batch = 32
    const val seqLen = 128
    const val heads = 12

    val activationBytes: Long =
        layers * (34L * batch * seqLen * dModel + 5L * batch * seqLen * seqLen * heads)

    fun stateBytes(method: Method): Long = FineTuneLab.trainingBytes(totalParams, method.trainable)

    fun totalTrainingBytes(method: Method): Long = stateBytes(method) + activationBytes

    /** How far the trainable-parameter headline overstates the memory saving. */
    fun headlineOverstatement(method: Method): Double {
        val full = methods.first()
        val paramRatio = full.trainable.toDouble() / method.trainable
        val memoryRatio = totalTrainingBytes(full).toDouble() / totalTrainingBytes(method)
        return paramRatio / memoryRatio
    }
}

// ── LoRA & QLoRA ─────────────────────────────────────────────────────────────

/**
 * The low-rank hypothesis, measured rather than assumed.
 *
 * A 32×32 linear map is fine-tuned for real from a pretrained `W0` onto a downstream target. The
 * update `ΔW = W − W0` is then decomposed, and LoRA is trained separately at each rank so the
 * spectrum's promise can be checked against a loss rather than an energy fraction.
 *
 * QLoRA is the same experiment with `W0` quantized to NF4 first, which asks the question the paper
 * actually answers: can adapters trained in full precision absorb the base model's quantization
 * error?
 */
internal object LoraLab {

    const val dim = 24
    const val samples = 128

    private val random = Random(1_009)

    /** The pretrained weights: a random well-conditioned map. */
    val base: Array<DoubleArray> = Array(dim) { DoubleArray(dim) { ftGaussian(random) / sqrt(dim.toDouble()) } }

    /**
     * The downstream target. Built as the base plus a genuinely low-rank shift plus a small dense
     * component, because a purely rank-3 target would make the lab's own conclusion true by
     * construction.
     */
    val target: Array<DoubleArray> by lazy {
        val u = Array(3) { DoubleArray(dim) { ftGaussian(random) } }
        val v = Array(3) { DoubleArray(dim) { ftGaussian(random) } }
        Array(dim) { i ->
            DoubleArray(dim) { j ->
                base[i][j] + 0.35 * (0 until 3).sumOf { u[it][i] * v[it][j] } / sqrt(dim.toDouble()) +
                    0.05 * ftGaussian(random)
            }
        }
    }

    private fun apply(w: Array<DoubleArray>, x: DoubleArray) = DoubleArray(dim) { i -> (0 until dim).sumOf { w[i][it] * x[it] } }

    private val inputs: List<DoubleArray> = (0 until samples).map { DoubleArray(dim) { ftGaussian(random) } }
    private val outputs: List<DoubleArray> by lazy { inputs.map { apply(target, it) } }
    // One stream for the whole held-out set. Seeding each row separately (`Random(2011 + i)`)
    // correlated the rows enough to distort the empirical covariance, and a distorted covariance is
    // exactly what breaks the comparison between a trained rank-r fit and its Eckart-Young bound.
    private val testInputs: List<DoubleArray> = Random(2_011).let { r -> (0 until 800).map { DoubleArray(dim) { ftGaussian(r) } } }
    private val testOutputs: List<DoubleArray> by lazy { testInputs.map { apply(target, it) } }

    fun testLoss(w: Array<DoubleArray>): Double {
        var acc = 0.0
        testInputs.indices.forEach { n ->
            val p = apply(w, testInputs[n])
            acc += p.indices.sumOf { (p[it] - testOutputs[n][it]).let { e -> e * e } }
        }
        return acc / (testInputs.size * dim)
    }

    val baseLoss: Double by lazy { testLoss(base) }

    /** Mean squared difference between two weight matrices' *outputs* on the held-out inputs. */
    fun outputDistance(a: Array<DoubleArray>, b: Array<DoubleArray>): Double {
        var acc = 0.0
        testInputs.forEach { x ->
            val pa = apply(a, x)
            val pb = apply(b, x)
            acc += pa.indices.sumOf { (pa[it] - pb[it]).let { e -> e * e } }
        }
        return acc / (testInputs.size * dim)
    }

    /**
     * Learning rate for every arm. The first run of this lab used 40, which diverged to NaN on every
     * rank — a reminder that the sweep has to be looked at, not assumed.
     */
    const val learningRate = 4.0

    /** Full fine-tuning: gradient descent on every entry of W, starting from the pretrained weights. */
    val fullyTuned: Array<DoubleArray> by lazy {
        val w = Array(dim) { base[it].copyOf() }
        repeat(4_000) {
            val grad = Array(dim) { DoubleArray(dim) }
            inputs.indices.forEach { n ->
                val p = apply(w, inputs[n])
                for (i in 0 until dim) {
                    val e = 2.0 * (p[i] - outputs[n][i]) / (samples * dim)
                    for (j in 0 until dim) grad[i][j] += e * inputs[n][j]
                }
            }
            for (i in 0 until dim) for (j in 0 until dim) w[i][j] -= learningRate * grad[i][j]
        }
        w
    }

    val fullLoss: Double by lazy { testLoss(fullyTuned) }

    val update: Array<DoubleArray> by lazy {
        Array(dim) { i -> DoubleArray(dim) { j -> fullyTuned[i][j] - base[i][j] } }
    }

    val updateSpectrum: DoubleArray by lazy { ftSingularValues(update) }
    val baseSpectrum: DoubleArray by lazy { ftSingularValues(base) }

    /** Fraction of squared Frobenius norm captured by the top `r` singular values. */
    fun energyAtRank(r: Int, spectrum: DoubleArray = updateSpectrum): Double {
        val total = spectrum.sumOf { it * it }
        return spectrum.take(r).sumOf { it * it } / total
    }

    /** Effective rank: `(Σσ²)² / Σσ⁴`, the participation ratio. 1 for rank-1, `dim` for flat. */
    fun effectiveRank(spectrum: DoubleArray): Double {
        val s2 = spectrum.map { it * it }
        return s2.sum() * s2.sum() / s2.sumOf { it * it }
    }

    const val alpha = 16.0

    class LoraFit(val rank: Int, val trainable: Int, val loss: Double)

    /**
     * Train `W0 + (α/r)·BA` with B initialised at zero, which is what makes the adapter a no-op at
     * step 0. `frozenBase` lets the QLoRA arm swap in a quantized `W0`.
     *
     * The learning rate carries a `(r/α)²` factor, and finding out why is what the first run of this
     * sweep was for: at a fixed rate every rank below 16 diverged to NaN, because the update passes
     * through the `α/r` scale on the way in *and* on the way back, so the effective step size on the
     * product grows as `(α/r)²`. At r = 1 that is 256× the rate at r = 16. This is the concrete
     * content of the LoRA paper's remark that tuning α is roughly like tuning the learning rate.
     */
    fun trainLora(rank: Int, frozenBase: Array<DoubleArray> = base, steps: Int = 4_000, rateScale: Double = -1.0): LoraFit {
        val scale = alpha / rank
        val lr = if (rateScale > 0) rateScale else min(learningRate, learningRate * (rank / alpha) * (rank / alpha))
        val init = Random(3_001 + rank)
        val a = Array(rank) { DoubleArray(dim) { ftGaussian(init) / sqrt(dim.toDouble()) } }
        val b = Array(dim) { DoubleArray(rank) }
        fun effective(): Array<DoubleArray> = Array(dim) { i ->
            DoubleArray(dim) { j -> frozenBase[i][j] + scale * (0 until rank).sumOf { b[i][it] * a[it][j] } }
        }
        repeat(steps) {
            val w = effective()
            val gradW = Array(dim) { DoubleArray(dim) }
            inputs.indices.forEach { n ->
                val p = apply(w, inputs[n])
                for (i in 0 until dim) {
                    val e = 2.0 * (p[i] - outputs[n][i]) / (samples * dim)
                    for (j in 0 until dim) gradW[i][j] += e * inputs[n][j]
                }
            }
            val gradB = Array(dim) { i -> DoubleArray(rank) { r -> scale * (0 until dim).sumOf { gradW[i][it] * a[r][it] } } }
            val gradA = Array(rank) { r -> DoubleArray(dim) { j -> scale * (0 until dim).sumOf { gradW[it][j] * b[it][r] } } }
            for (i in 0 until dim) for (r in 0 until rank) b[i][r] -= lr * gradB[i][r]
            for (r in 0 until rank) for (j in 0 until dim) a[r][j] -= lr * gradA[r][j]
        }
        return LoraFit(rank, 2 * dim * rank, testLoss(effective()))
    }

    val ranks = listOf(1, 2, 4, 8, 16, 24)

    val rankSweep: List<LoraFit> by lazy { ranks.map { trainLora(it) } }

    /** The same rank at the *unscaled* rate — what the α/r factor does if you leave it alone. */
    fun divergesAtFixedRate(rank: Int): Boolean =
        trainLora(rank, rateScale = learningRate).loss.let { it.isNaN() || it > 1e3 }

    /** Share of the gap between the frozen base and full fine-tuning that rank `r` closes. */
    fun gapClosed(fit: LoraFit): Double = 1.0 - (fit.loss - fullLoss) / (baseLoss - fullLoss)

    /**
     * What the spectrum predicts rank `r` should cost, with no training involved: the best rank-`r`
     * approximation of `ΔW` leaves the tail singular values behind, and for isotropic inputs the
     * squared output error is exactly that tail divided by the width. If the trained sweep matches
     * this, the low-rank story is not a heuristic — it is the Eckart-Young theorem with a learning
     * rate attached.
     */
    fun predictedLossAtRank(r: Int): Double =
        updateSpectrum.drop(r).sumOf { it * it } / dim

    // QLoRA: the frozen base is stored in NF4 and the adapters stay in full precision.
    val quantizedBase: Array<DoubleArray> by lazy {
        Array(dim) { i -> QuantLab.quantizeBlockwise(base[i], QuantLab.nf4Levels, blockSize = 16) }
    }

    val quantizedBaseLoss: Double by lazy { testLoss(quantizedBase) }

    fun qloraFit(rank: Int): LoraFit = trainLora(rank, quantizedBase)
}

// ── Quantization (4-bit / 8-bit) ─────────────────────────────────────────────

/**
 * Weight quantization measured on the distribution weights actually have, plus the one thing that
 * breaks the naive scheme.
 *
 * Everything here is error against the original tensor, and then — because tensor error is not the
 * number anyone cares about — the same quantizers are run on `LoraLab`'s pretrained matrix and
 * scored by the task loss they produce.
 */
internal object QuantLab {

    /** The 16 NF4 levels: quantiles of a standard normal, rescaled so the extremes sit at ±1. */
    val nf4Levels = doubleArrayOf(
        -1.0, -0.6961928009986877, -0.5250730514526367, -0.39491748809814453,
        -0.28444138169288635, -0.18477343022823334, -0.09105003625154495, 0.0,
        0.07958029955625534, 0.16093020141124725, 0.24611230194568634, 0.33791524171829224,
        0.44070982933044434, 0.5626170039176941, 0.7229568362236023, 1.0,
    )

    /** Uniform signed levels — what plain int-N quantization uses. */
    fun uniformLevels(bits: Int): DoubleArray {
        val steps = (1 shl bits) - 1
        return DoubleArray(steps + 1) { -1.0 + 2.0 * it / steps }
    }

    val int8Levels = uniformLevels(8)
    val int4Levels = uniformLevels(4)

    private fun nearest(levels: DoubleArray, x: Double): Double {
        var best = levels[0]
        var bestGap = abs(x - best)
        levels.forEach { l ->
            val gap = abs(x - l)
            if (gap < bestGap) {
                best = l
                bestGap = gap
            }
        }
        return best
    }

    /** Absmax quantization over the whole vector: one scale for everything. */
    fun quantizePerTensor(values: DoubleArray, levels: DoubleArray): DoubleArray {
        val scale = values.maxOf { abs(it) }.coerceAtLeast(1e-12)
        return DoubleArray(values.size) { nearest(levels, values[it] / scale) * scale }
    }

    /** Blockwise absmax: one scale per block, which is what every real 4-bit kernel does. */
    fun quantizeBlockwise(values: DoubleArray, levels: DoubleArray, blockSize: Int): DoubleArray {
        val out = DoubleArray(values.size)
        var start = 0
        while (start < values.size) {
            val end = min(start + blockSize, values.size)
            val scale = (start until end).maxOf { abs(values[it]) }.coerceAtLeast(1e-12)
            for (i in start until end) out[i] = nearest(levels, values[i] / scale) * scale
            start = end
        }
        return out
    }

    fun mse(a: DoubleArray, b: DoubleArray) = a.indices.sumOf { (a[it] - b[it]).let { e -> e * e } } / a.size

    /** Signal-to-quantization-noise ratio in dB — the number hardware people quote. */
    fun snrDb(original: DoubleArray, quantized: DoubleArray): Double {
        val signal = original.sumOf { it * it } / original.size
        return 10.0 * ln(signal / mse(original, quantized)) / ln(10.0)
    }

    const val weightCount = 4_096

    val weights: DoubleArray by lazy {
        val random = Random(613)
        DoubleArray(weightCount) { ftGaussian(random) }
    }

    /** The same weights with one 20σ outlier — an activation outlier is the LLM.int8() story. */
    val weightsWithOutlier: DoubleArray by lazy {
        weights.copyOf().also { it[1_234] = 20.0 }
    }

    class Scheme(val name: String, val bits: Int, val quantize: (DoubleArray) -> DoubleArray)

    val schemes: List<Scheme> by lazy {
        listOf(
            Scheme("int8, per tensor", 8) { quantizePerTensor(it, int8Levels) },
            Scheme("int4, per tensor", 4) { quantizePerTensor(it, int4Levels) },
            Scheme("NF4, per tensor", 4) { quantizePerTensor(it, nf4Levels) },
            Scheme("int4, blocks of 64", 4) { quantizeBlockwise(it, int4Levels, 64) },
            Scheme("NF4, blocks of 64", 4) { quantizeBlockwise(it, nf4Levels, 64) },
        )
    }

    fun errors(source: DoubleArray = weights): List<Pair<String, Double>> =
        schemes.map { it.name to mse(source, it.quantize(source)) }

    /** What the outlier costs each scheme, measured on the 4,095 weights that are not the outlier. */
    fun outlierPenalty(scheme: Scheme): Double {
        fun cleanMse(source: DoubleArray): Double {
            val q = scheme.quantize(source)
            val idx = source.indices.filter { it != 1_234 }
            return idx.sumOf { (source[it] - q[it]).let { e -> e * e } } / idx.size
        }
        return cleanMse(weightsWithOutlier) / cleanMse(weights)
    }

    /**
     * What quantization does to a real matrix's *outputs*, which is the number that matters — weight
     * MSE is a proxy for it and task loss against a distant target is not a measure of it at all.
     * (The first version of this lab reported task loss and found NF4 "better than fp16", because
     * quantization noise was moving an under-fitted model around at random.)
     */
    fun outputDrift(levels: DoubleArray, blockSize: Int?): Double {
        val w = Array(LoraLab.dim) { row ->
            if (blockSize == null) quantizePerTensor(LoraLab.base[row], levels)
            else quantizeBlockwise(LoraLab.base[row], levels, blockSize)
        }
        return LoraLab.outputDistance(LoraLab.base, w)
    }

    fun bytesForParams(params: Long, bits: Int, blockSize: Int = 64): Long =
        params * bits / 8 + (params / blockSize) * 2
}

// ── Flash Attention ──────────────────────────────────────────────────────────

/**
 * The online-softmax recurrence, run against the textbook softmax on the same scores, plus a memory
 * counter over both algorithms.
 *
 * Flash Attention does not change the answer and does not reduce the arithmetic — it changes what
 * touches memory. The lab therefore proves the equality and then counts bytes, because "IO-aware" is
 * not a property you can see in the output.
 */
internal object FlashLab {

    /** A tile's running softmax state: the max seen, the running denominator, the running output. */
    class Running(var m: Double, var l: Double, val o: DoubleArray)

    fun naiveAttention(scores: DoubleArray, values: Array<DoubleArray>): DoubleArray {
        val p = ftSoftmax(scores)
        val dim = values[0].size
        return DoubleArray(dim) { d -> scores.indices.sumOf { p[it] * values[it][d] } }
    }

    /**
     * The streaming version: one pass, block by block, rescaling the accumulator whenever a block
     * raises the running maximum. Never materialises the score row.
     */
    fun tiledAttention(scores: DoubleArray, values: Array<DoubleArray>, blockSize: Int): DoubleArray {
        val dim = values[0].size
        val state = Running(Double.NEGATIVE_INFINITY, 0.0, DoubleArray(dim))
        var start = 0
        while (start < scores.size) {
            val end = min(start + blockSize, scores.size)
            val blockMax = (start until end).maxOf { scores[it] }
            val newMax = max(state.m, blockMax)
            val correction = if (state.m == Double.NEGATIVE_INFINITY) 0.0 else exp(state.m - newMax)
            var blockSum = 0.0
            val blockAcc = DoubleArray(dim)
            for (i in start until end) {
                val w = exp(scores[i] - newMax)
                blockSum += w
                for (d in 0 until dim) blockAcc[d] += w * values[i][d]
            }
            for (d in 0 until dim) state.o[d] = state.o[d] * correction + blockAcc[d]
            state.l = state.l * correction + blockSum
            state.m = newMax
            start = end
        }
        return DoubleArray(dim) { state.o[it] / state.l }
    }

    /** One entry per key block: what the scan knew before it, and what the block changed. */
    class Tile(
        val index: Int,
        val blockMax: Double,
        val runningMax: Double,
        val runningSum: Double,
        val rescale: Double,
    )

    /** The scan's own trace, so a frame can draw the rescale rather than describe it. */
    fun tileTrace(scores: DoubleArray, blockSize: Int): List<Tile> {
        val out = mutableListOf<Tile>()
        var m = Double.NEGATIVE_INFINITY
        var l = 0.0
        var start = 0
        var index = 0
        while (start < scores.size) {
            val end = min(start + blockSize, scores.size)
            val blockMax = (start until end).maxOf { scores[it] }
            val newMax = max(m, blockMax)
            val correction = if (m == Double.NEGATIVE_INFINITY) 1.0 else exp(m - newMax)
            l = l * correction + (start until end).sumOf { exp(scores[it] - newMax) }
            m = newMax
            out += Tile(index, blockMax, m, l, correction)
            start = end
            index++
        }
        return out
    }

    /** The same recurrence without the running max — what overflows, and at what score. */
    fun unstableAttention(scores: DoubleArray, values: Array<DoubleArray>): DoubleArray {
        val dim = values[0].size
        var l = 0.0
        val o = DoubleArray(dim)
        scores.indices.forEach { i ->
            val w = exp(scores[i])
            l += w
            for (d in 0 until dim) o[d] += w * values[i][d]
        }
        return DoubleArray(dim) { o[it] / l }
    }

    val demoScores: DoubleArray by lazy {
        val random = Random(457)
        DoubleArray(512) { ftGaussian(random) * 3.0 }
    }

    val demoValues: Array<DoubleArray> by lazy {
        val random = Random(458)
        Array(512) { DoubleArray(8) { ftGaussian(random) } }
    }

    fun maxDifference(blockSize: Int): Double {
        val a = naiveAttention(demoScores, demoValues)
        val b = tiledAttention(demoScores, demoValues, blockSize)
        return a.indices.maxOf { abs(a[it] - b[it]) }
    }

    /** The score at which `exp` overflows a float64 accumulator. `ln(Double.MAX_VALUE) ≈ 709.78`. */
    val overflowScore: Double = ln(Double.MAX_VALUE)

    fun overflows(peakScore: Double): Boolean {
        val scores = DoubleArray(8) { if (it == 3) peakScore else 0.0 }
        val values = Array(8) { i -> DoubleArray(2) { i.toDouble() } }
        return unstableAttention(scores, values).any { it.isNaN() || it.isInfinite() }
    }

    // ── HBM traffic ──────────────────────────────────────────────────────────
    // Bytes moved between high-bandwidth memory and on-chip SRAM, fp16, for one attention head.
    // Standard (non-fused) attention writes the N×N score matrix out and reads it back twice; the
    // tiled kernel never writes it at all and instead re-reads K and V once per query block.

    const val bytesPerElement = 2

    fun standardTraffic(seq: Long, headDim: Long): Long {
        val qkv = 3L * seq * headDim
        val out = seq * headDim
        // write S, read S for softmax, write P, read P for the PV product.
        val scoreTraffic = 4L * seq * seq
        return (qkv + out + scoreTraffic) * bytesPerElement
    }

    fun flashTraffic(seq: Long, headDim: Long, queryBlock: Long): Long {
        val blocks = (seq + queryBlock - 1) / queryBlock
        val q = seq * headDim
        val out = seq * headDim
        // every query block streams the whole of K and V once.
        val kv = 2L * blocks * seq * headDim
        return (q + out + kv) * bytesPerElement
    }

    fun trafficRatio(seq: Long, headDim: Long = 64, queryBlock: Long = 128): Double =
        standardTraffic(seq, headDim).toDouble() / flashTraffic(seq, headDim, queryBlock)

    /**
     * The ratio's limit, and the reason the "10× less IO" folklore has no N in it: the score terms
     * dominate both counts at large N, standard moves `4N²` elements and the tiled kernel moves
     * `2N²d/Br`, so the saving is exactly `2·Br/d` — a property of the tile and the head, not the
     * sequence. Tiles are sized by SRAM, which is why this number is a hardware fact.
     */
    fun asymptoticTrafficRatio(headDim: Long = 64, queryBlock: Long = 128): Double =
        2.0 * queryBlock / headDim

    /** Forward-pass arithmetic, identical for both: `QKᵀ` then `PV`. */
    fun forwardFlops(seq: Long, headDim: Long): Long = 4L * seq * seq * headDim

    /**
     * Backward with recomputation. The standard backward reads its stored `P`; the flash backward
     * recomputes `QKᵀ`, which is arithmetic it did not have to do.
     */
    fun backwardFlops(seq: Long, headDim: Long, recompute: Boolean): Long =
        (if (recompute) 10L else 8L) * seq * seq * headDim

    fun flopOverhead(seq: Long, headDim: Long = 64): Double {
        val plain = forwardFlops(seq, headDim) + backwardFlops(seq, headDim, recompute = false)
        val flash = forwardFlops(seq, headDim) + backwardFlops(seq, headDim, recompute = true)
        return flash.toDouble() / plain
    }

    /** Peak activation memory for the score matrix alone — the term flash removes entirely. */
    fun scoreMatrixBytes(seq: Long, heads: Long = 32): Long = heads * seq * seq * bytesPerElement
}

// ── State Space Models (SSMs) ────────────────────────────────────────────────

/**
 * A diagonal linear time-invariant SSM run two ways on the same input: the `O(L)` recurrence a
 * decoder would use, and the convolution with the kernel `K = (CB, CAB, CA²B, …)` a trainer would
 * use. They are the same function, which is the entire structural argument for the family, and it is
 * an equality rather than an approximation.
 */
internal object SsmLab {

    const val stateDim = 4

    /** Continuous-time poles, spread over decades so the state carries several timescales at once. */
    val poles = doubleArrayOf(0.01, 0.05, 0.20, 0.80)

    const val delta = 1.0

    /** Zero-order hold discretization: `Ā = exp(Δ·(−λ))`, `B̄ = (1 − Ā)/λ`. */
    val aBar: DoubleArray = DoubleArray(stateDim) { exp(-delta * poles[it]) }
    val bBar: DoubleArray = DoubleArray(stateDim) { (1.0 - aBar[it]) / poles[it] }
    val c: DoubleArray = doubleArrayOf(1.0, -0.6, 0.4, -0.25)

    fun recurrent(input: DoubleArray): DoubleArray {
        val h = DoubleArray(stateDim)
        return DoubleArray(input.size) { t ->
            for (n in 0 until stateDim) h[n] = aBar[n] * h[n] + bBar[n] * input[t]
            (0 until stateDim).sumOf { c[it] * h[it] }
        }
    }

    /** The state itself at each step, for frames that want to draw what the scan is carrying. */
    fun stateTrace(input: DoubleArray): List<DoubleArray> {
        val h = DoubleArray(stateDim)
        return input.map { x ->
            for (n in 0 until stateDim) h[n] = aBar[n] * h[n] + bBar[n] * x
            h.copyOf()
        }
    }

    /** `K[t] = C Āᵗ B̄` — the impulse response, which is all an LTI system is. */
    fun kernel(length: Int): DoubleArray = DoubleArray(length) { t ->
        (0 until stateDim).sumOf { c[it] * aBar[it].pow(t) * bBar[it] }
    }

    fun convolutional(input: DoubleArray): DoubleArray {
        val k = kernel(input.size)
        return DoubleArray(input.size) { t -> (0..t).sumOf { k[t - it] * input[it] } }
    }

    val demoInput: DoubleArray by lazy {
        val random = Random(811)
        DoubleArray(64) { ftGaussian(random) }
    }

    fun formEquivalenceGap(): Double {
        val a = recurrent(demoInput)
        val b = convolutional(demoInput)
        return a.indices.maxOf { abs(a[it] - b[it]) }
    }

    /** Steps for a channel's contribution to fall to half — its memory, in tokens. */
    fun halfLife(channel: Int): Double = ln(0.5) / ln(aBar[channel])

    /** Where the impulse response has decayed below 1% of its peak. */
    fun effectiveHorizon(threshold: Double = 0.01): Int {
        val k = kernel(4_096)
        val peak = k.maxOf { abs(it) }
        return k.indexOfLast { abs(it) > threshold * peak } + 1
    }

    /** The scan is associative — `(a₂,b₂) ∘ (a₁,b₁) = (a₂a₁, a₂b₁+b₂)` — so it parallelises. */
    fun scanCompose(first: Pair<Double, Double>, second: Pair<Double, Double>) =
        second.first * first.first to second.first * first.second + second.second

    fun parallelScanDepth(length: Int): Int {
        var depth = 0
        var span = 1
        while (span < length) {
            span *= 2
            depth++
        }
        return 2 * depth - 1
    }

    class CostRow(val length: Long, val attentionOps: Long, val ssmRecurrentOps: Long, val ssmScanDepth: Int)

    fun costs(lengths: List<Long> = listOf(1_024, 16_384, 1_048_576), width: Long = 1_024): List<CostRow> =
        lengths.map { l ->
            CostRow(l, 4L * l * l * width, 2L * l * stateDim * width, parallelScanDepth(l.toInt().coerceAtLeast(2)))
        }
}

private fun Double.pow(n: Int): Double {
    var acc = 1.0
    repeat(n) { acc *= this }
    return acc
}

// ── Mamba ────────────────────────────────────────────────────────────────────

/**
 * The selective-copying task, which is the experiment the Mamba paper is built around, run on three
 * one-channel systems that differ only in whether their `Δ` depends on the input.
 *
 * A token to remember arrives first, then `k` filler tokens, then a read-out. The three arms are the
 * two things a *time-invariant* system can do — decay, or not decay — and the one thing it cannot.
 */
internal object MambaLab {

    const val signalValue = 1.0
    const val fillerValue = 0.7

    /** Filler tokens are non-zero: a system that cannot ignore them will integrate them. */
    fun sequence(fillers: Int): DoubleArray =
        DoubleArray(fillers + 1) { if (it == 0) signalValue else fillerValue }

    /** Time-invariant: one `a` and one `b` for every position, whatever the token is. */
    fun lti(input: DoubleArray, a: Double, b: Double): Double {
        var h = 0.0
        input.forEach { x -> h = a * h + b * x }
        return h
    }

    /**
     * Selective: `Δ` is a function of the token, so `a = exp(−Δ)` and `b = Δ` both are. The gate here
     * is the simplest one that expresses the paper's mechanism — a large `Δ` for a token worth
     * writing, `Δ = 0` for filler, which holds the state at `a = 1` and writes nothing.
     */
    fun selective(input: DoubleArray): Double {
        var h = 0.0
        input.forEach { x ->
            val d = if (abs(x - signalValue) < 1e-9) 4.0 else 0.0
            val a = exp(-d)
            val b = 1.0 - a
            h = a * h + b * x
        }
        return h
    }

    val fillerCounts = listOf(0, 5, 10, 20, 50, 100)

    class Arm(val name: String, val short: String, val recovered: (Int) -> Double, val trace: (DoubleArray) -> DoubleArray)

    /** The running state at every position, so a frame can show where the signal goes. */
    private fun ltiTrace(input: DoubleArray, a: Double, b: Double): DoubleArray {
        var h = 0.0
        return DoubleArray(input.size) { h = a * h + b * input[it]; h }
    }

    private fun selectiveTrace(input: DoubleArray): DoubleArray {
        var h = 0.0
        return DoubleArray(input.size) {
            val d = if (abs(input[it] - signalValue) < 1e-9) 4.0 else 0.0
            val a = exp(-d)
            h = a * h + (1.0 - a) * input[it]
            h
        }
    }

    val arms: List<Arm> = listOf(
        Arm("LTI, decaying (a = 0.90)", "decaying", { lti(sequence(it), 0.90, 1.0 - 0.90) }, { ltiTrace(it, 0.90, 0.10) }),
        Arm("LTI, lossless (a = 1.00)", "lossless", { lti(sequence(it), 1.0, 1.0) }, { ltiTrace(it, 1.0, 1.0) }),
        Arm("selective (Δ from the token)", "selective", { selective(sequence(it)) }, { selectiveTrace(it) }),
    )

    /** Absolute error against the value that should have survived. */
    fun errorAt(arm: Arm, fillers: Int): Double = abs(arm.recovered(fillers) - signalValue)

    /** The same run with the signal token replaced by filler — the control the read-out is compared against. */
    fun withoutSignal(fillers: Int): DoubleArray =
        DoubleArray(fillers + 1) { fillerValue }

    /**
     * How much of the read-out the signal token is actually responsible for. Raw recovered values
     * flatter the decaying arm badly: at 100 fillers it reports 0.700 against a target of 1.0, which
     * reads like a 30% error and is in fact the filler steady state with no trace of the signal in it
     * at all. This is the difference the signal makes, which is the quantity the task is about.
     */
    fun signalContribution(arm: Arm, fillers: Int): Double {
        val control = when (arm.name) {
            arms[0].name -> lti(withoutSignal(fillers), 0.90, 1.0 - 0.90)
            arms[1].name -> lti(withoutSignal(fillers), 1.0, 1.0)
            else -> selective(withoutSignal(fillers))
        }
        return arm.recovered(fillers) - control
    }

    /**
     * Selectivity costs the convolution. An LTI system is one fixed kernel; a selective one has a
     * different kernel per position, so no single `K` reproduces it. Measured as the residual of the
     * best fixed kernel fitted to the selective system's own outputs across filler counts.
     */
    fun bestFixedKernelResidual(): Double {
        // For each filler count the selective system emits its answer; a fixed kernel must produce
        // all of them from the same coefficients. Fit the one free scalar (the kernel's DC gain over
        // the filler run) by least squares and report what it cannot explain.
        val targets = fillerCounts.map { selective(sequence(it)) }
        val features = fillerCounts.map { signalValue + it * fillerValue }
        val g = features.indices.sumOf { features[it] * targets[it] } / features.sumOf { it * it }
        return sqrt(features.indices.sumOf { (g * features[it] - targets[it]).let { e -> e * e } } / features.size)
    }

    // Cost of the two execution modes, per token, at inference.
    const val hiddenWidth = 2_048
    const val expandedState = 16

    /** Mamba's recurrent state is fixed size; a transformer's KV cache is not. */
    fun mambaStateBytes(): Long = 2L * hiddenWidth * expandedState

    fun transformerCacheBytes(length: Long, layers: Long = 1, heads: Long = 32, headDim: Long = 64): Long =
        2L * length * layers * heads * headDim * 2
}

// ── RWKV ─────────────────────────────────────────────────────────────────────

/**
 * The WKV operator — a decaying weighted average of past values — computed in the numerically stable
 * form RWKV actually ships, and compared against softmax attention on the same keys and values.
 *
 * The comparison is the point. Both produce a convex combination of values; only one of them can
 * choose its weights using the token doing the reading. What that costs is measurable: place a
 * needle at distance `d` and ask how much of the output it accounts for.
 */
internal object RwkvLab {

    const val decay = 0.10
    const val bonus = 1.0

    /** The textbook form: overflows the moment a key gets large. */
    fun wkvNaive(keys: DoubleArray, values: DoubleArray, t: Int): Double {
        var num = 0.0
        var den = 0.0
        for (i in 0 until t) {
            val w = exp(-(t - 1 - i) * decay + keys[i])
            num += w * values[i]
            den += w
        }
        val w = exp(bonus + keys[t])
        return (num + w * values[t]) / (den + w)
    }

    /**
     * The shipped form: carry `(numerator, denominator, running max)` and rescale. The invariant is
     * that after token `i` the state holds the sum over `j ≤ i` weighted by `exp(k_j − (i−j)·w − p)`,
     * so the decay belongs *before* the new token is folded in, not after — putting it after decays
     * the current token as well and the two forms then disagree by 5e-2 rather than 1e-16.
     */
    fun wkvStable(keys: DoubleArray, values: DoubleArray, t: Int): Double {
        var a = 0.0
        var b = 0.0
        var p = Double.NEGATIVE_INFINITY
        for (i in 0 until t) {
            val shifted = p - decay
            val q = max(shifted, keys[i])
            val e1 = if (p == Double.NEGATIVE_INFINITY) 0.0 else exp(shifted - q)
            val e2 = exp(keys[i] - q)
            a = e1 * a + e2 * values[i]
            b = e1 * b + e2
            p = q
        }
        val q = max(p, bonus + keys[t])
        val e1 = if (p == Double.NEGATIVE_INFINITY) 0.0 else exp(p - q)
        val e2 = exp(bonus + keys[t] - q)
        return (e1 * a + e2 * values[t]) / (e1 * b + e2)
    }

    /** The weight RWKV gives a token `d` steps back, relative to the current token. */
    fun relativeWeight(distance: Int, keyGap: Double = 0.0): Double =
        exp(-distance * decay + keyGap - bonus)

    class Needle(val distance: Int, val rwkvShare: Double, val attentionShare: Double)

    /**
     * A needle with a strong key sits `distance` tokens back among neutral tokens. `rwkvShare` is the
     * fraction of the WKV denominator it accounts for; `attentionShare` is the same fraction under a
     * softmax attention whose query is matched to the needle's key.
     */
    fun needle(distance: Int, needleKey: Double = 3.0, queryMatch: Double = 3.0): Needle {
        val length = distance + 1
        val keys = DoubleArray(length) { if (it == 0) needleKey else 0.0 }
        var rwkvTotal = 0.0
        var rwkvNeedle = 0.0
        for (i in 0 until length - 1) {
            val w = exp(-(length - 2 - i) * decay + keys[i])
            rwkvTotal += w
            if (i == 0) rwkvNeedle = w
        }
        rwkvTotal += exp(bonus + keys[length - 1])
        // Softmax attention: score = q·k, and the query can be aimed at the needle's key direction.
        val scores = DoubleArray(length) { keys[it] * queryMatch }
        val p = ftSoftmax(scores)
        return Needle(distance, rwkvNeedle / rwkvTotal, p[0])
    }

    val needleDistances = listOf(5, 20, 50, 100, 500)

    val demoKeys: DoubleArray by lazy {
        val random = Random(929)
        DoubleArray(24) { ftGaussian(random) * 1.5 }
    }

    val demoValues: DoubleArray by lazy {
        val random = Random(930)
        DoubleArray(24) { ftGaussian(random) }
    }

    fun stabilityGap(): Double =
        (1 until demoKeys.size).maxOf { abs(wkvNaive(demoKeys, demoValues, it) - wkvStable(demoKeys, demoValues, it)) }

    /** The key value at which the naive form stops producing a number at all. */
    fun naiveOverflowsAt(key: Double): Boolean {
        val keys = DoubleArray(4) { if (it == 1) key else 0.0 }
        val values = DoubleArray(4) { it.toDouble() }
        val r = wkvNaive(keys, values, 3)
        return r.isNaN() || r.isInfinite()
    }

    const val channels = 2_048

    /** RWKV keeps `(a, b, p)` per channel and nothing else, whatever the sequence length. */
    fun stateBytes(): Long = 3L * channels * 4

    fun cacheBytes(length: Long, layers: Long = 24, heads: Long = 16, headDim: Long = 64): Long =
        2L * length * layers * heads * headDim * 2
}

// ── Long Context Windows ─────────────────────────────────────────────────────

/**
 * What a context window costs, in the three currencies that actually bind: KV-cache bytes at decode
 * time, attention FLOPs at prefill time, and the prefill bill measured against simply retrieving the
 * five passages that contain the answer.
 *
 * Config is LLaMA-2-7B's, so the numbers are checkable against a real model rather than a shape.
 */
internal object LongContextLab {

    const val layers = 32L
    const val heads = 32L
    const val headDim = 128L
    const val dModel = 4_096L
    const val dFfn = 11_008L
    const val params = 6_738_415_616L
    const val bytesPerElement = 2L

    val lengths = listOf(4_096L, 32_768L, 131_072L, 1_048_576L)

    /** Two tensors (K and V), one per layer per head, in fp16. */
    fun kvCacheBytes(length: Long, kvHeads: Long = heads): Long =
        2L * length * layers * kvHeads * headDim * bytesPerElement

    /** Grouped-query attention shares one K/V head across a group of query heads. */
    val attentionVariants = listOf(
        "multi-head (32 kv heads)" to heads,
        "grouped-query (8 kv heads)" to 8L,
        "multi-query (1 kv head)" to 1L,
    )

    /** `QKᵀ` and `PV`, both `2·L²·d` per layer. */
    fun attentionFlops(length: Long): Long = 4L * length * length * dModel * layers

    /** Projections (`4·2·L·d²`) plus the SwiGLU FFN (`3·2·L·d·d_ffn`). */
    fun feedForwardFlops(length: Long): Long =
        (8L * length * dModel * dModel + 6L * length * dModel * dFfn) * layers

    fun attentionShare(length: Long): Double {
        val a = attentionFlops(length).toDouble()
        return a / (a + feedForwardFlops(length))
    }

    /** The length at which attention first costs more arithmetic than everything else combined. */
    fun flopCrossover(): Long {
        var lo = 1L
        var hi = 1L shl 22
        while (lo < hi) {
            val mid = (lo + hi) / 2
            if (attentionShare(mid) >= 0.5) hi = mid else lo = mid + 1
        }
        return lo
    }

    fun prefillFlops(length: Long): Long = attentionFlops(length) + feedForwardFlops(length)

    /** Stuffing the window against retrieving five 400-token passages and prefilling only those. */
    const val retrievedPassages = 5L
    const val passageTokens = 400L

    fun retrievalPrefillFlops(): Long = prefillFlops(retrievedPassages * passageTokens)

    fun stuffingOverhead(length: Long): Double =
        prefillFlops(length).toDouble() / retrievalPrefillFlops()

    // ALiBi's per-head slopes: a geometric sequence starting at 2^(−8/n) for n heads. The bias is
    // −slope·distance, so each head has a distance past which it contributes essentially nothing.
    fun alibiSlopes(headCount: Int = 8): DoubleArray {
        val start = 2.0.pow(-8.0 / headCount)
        return DoubleArray(headCount) { start.pow((it + 1).toDouble()) }
    }

    /** Distance at which a head's ALiBi bias has driven its weight below 1% of the nearest token's. */
    fun effectiveWindow(slope: Double, threshold: Double = 0.01): Int =
        (-ln(threshold) / slope).toInt()

    fun advertisedVersusEffective(length: Long): List<Pair<Double, Int>> =
        alibiSlopes().map { it to min(effectiveWindow(it).toLong(), length).toInt() }
}

private fun Double.pow(x: Double): Double = exp(x * ln(this))

/** Rounded to the nearest tenth of a gigabyte, for the frames that quote memory. */
internal fun bytesToGb(bytes: Long): Double = (bytes / 1_073_741_824.0 * 10.0).roundToInt() / 10.0
