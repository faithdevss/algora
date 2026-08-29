package com.algora.app.feature.topics

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.algora.app.core.ui.theme.SimColors
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

// ── Policy gradient player ───────────────────────────────────────────────────
// The policy-gradient and continuous-control families. Like the DQN player, every number comes from
// an experiment run when the frames are built: REINFORCE and its variance-reduced variants are
// estimated over hundreds of rollouts on a small chain MDP, and the continuous-control labs run on
// a one-dimensional action with a known reward curve so the "true" answer is available to compare
// against.
//
// The recurring measurement is the standard deviation of a single gradient component across
// independent rollouts — the quantity every method in this family exists to shrink.

private class PgCurve(val label: String, val values: List<Float>, val color: Color)

private class PgPlot(
    val label: String,
    val curves: List<PgCurve>,
    val yRange: ClosedFloatingPointRange<Float>,
    val xLabel: String,
)

private class PgBar(val label: String, val values: List<Float>, val color: Color, val captions: List<String> = emptyList())

private class PgFrame(
    val status: String,
    val plot: PgPlot? = null,
    val bars: List<PgBar> = emptyList(),
    val readout: String? = null,
)

private class PgConfig(
    val intro: String,
    val legend: List<Pair<Color, String>>,
    val build: () -> List<PgFrame>,
)

private val PlainColor = SimColors.Grey
private val BetterColor = SimColors.Green
private val RiskColor = Color(0xFFEC4899)
private val MainColor = SimColors.Blue
private val DeepColor = Color(0xFF7C3AED)

private class PgRng(private var state: Int) {
    fun next(): Float {
        state = state * 1103515245 + 12345
        return ((state ushr 16) and 0x7fff) / 32767f
    }

    /** Box–Muller-free normal enough for these experiments: the sum of 6 uniforms, recentred. */
    fun normal(): Float = ((0 until 6).sumOf { next().toDouble() }.toFloat() - 3f) / 0.707f
}

// ── The discrete environment ─────────────────────────────────────────────────
// The same six-state chain the DQN player uses, but now with a softmax policy over two actions per
// state. Reaching the far end pays 1; every step costs a little, so dithering is punished and the
// return actually distinguishes good policies from bad ones.

private const val PG_STATES = 6
private const val PG_GOAL = PG_STATES - 1
private const val PG_GAMMA = 0.95f
private const val STEP_COST = -0.02f

private fun policy(theta: Array<FloatArray>, state: Int): FloatArray {
    val logits = theta[state]
    val max = max(logits[0], logits[1])
    val e0 = exp(logits[0] - max)
    val e1 = exp(logits[1] - max)
    return floatArrayOf(e0 / (e0 + e1), e1 / (e0 + e1))
}

private class Step(val state: Int, val action: Int, val reward: Float)

private fun rollout(theta: Array<FloatArray>, rng: PgRng, maxSteps: Int = 20): List<Step> {
    val steps = mutableListOf<Step>()
    var state = 0
    repeat(maxSteps) {
        val probabilities = policy(theta, state)
        val action = if (rng.next() < probabilities[1]) 1 else 0
        val next = if (action == 1) min(state + 1, PG_GOAL) else max(state - 1, 0)
        val reward = STEP_COST + if (next == PG_GOAL) 1f else 0f
        steps += Step(state, action, reward)
        state = next
        if (state == PG_GOAL) return steps
    }
    return steps
}

private fun returns(episode: List<Step>): List<Float> {
    val out = MutableList(episode.size) { 0f }
    var running = 0f
    for (t in episode.indices.reversed()) {
        running = episode[t].reward + PG_GAMMA * running
        out[t] = running
    }
    return out
}

private fun totalReturn(episode: List<Step>) = returns(episode).firstOrNull() ?: 0f

/** A policy that is decent but not optimal — the regime where gradient noise actually matters. */
private fun startingTheta(): Array<FloatArray> = Array(PG_STATES) { floatArrayOf(0f, 0.4f) }

/** Monte-Carlo state values under [theta], used as the baseline / critic in the comparisons below. */
private fun estimateValues(theta: Array<FloatArray>, seed: Int, episodes: Int = 400): FloatArray {
    val rng = PgRng(seed)
    val sums = FloatArray(PG_STATES)
    val counts = IntArray(PG_STATES)
    repeat(episodes) {
        val episode = rollout(theta, rng)
        val g = returns(episode)
        episode.forEachIndexed { t, step ->
            sums[step.state] += g[t]
            counts[step.state]++
        }
    }
    return FloatArray(PG_STATES) { if (counts[it] == 0) 0f else sums[it] / counts[it] }
}

private enum class Estimator { RETURN, BASELINE, TD, GAE }

/**
 * One rollout's estimate of ∂J/∂θ[state][1], under the chosen advantage estimator. Everything else
 * in this family — A2C, A3C, PPO, TRPO — is a different way of forming or constraining this same
 * quantity, which is why one function covers all of them.
 */
private fun gradientSample(
    theta: Array<FloatArray>,
    values: FloatArray,
    episode: List<Step>,
    estimator: Estimator,
    lambda: Float = 0.95f,
    trackedState: Int = 2,
): Float {
    val g = returns(episode)
    var total = 0f

    val advantages = when (estimator) {
        Estimator.RETURN -> g
        Estimator.BASELINE -> List(episode.size) { g[it] - values[episode[it].state] }
        Estimator.TD -> List(episode.size) { t ->
            val next = if (t + 1 < episode.size) values[episode[t + 1].state] else 0f
            episode[t].reward + PG_GAMMA * next - values[episode[t].state]
        }
        Estimator.GAE -> {
            val deltas = List(episode.size) { t ->
                val next = if (t + 1 < episode.size) values[episode[t + 1].state] else 0f
                episode[t].reward + PG_GAMMA * next - values[episode[t].state]
            }
            val out = MutableList(episode.size) { 0f }
            var running = 0f
            for (t in episode.indices.reversed()) {
                running = deltas[t] + PG_GAMMA * lambda * running
                out[t] = running
            }
            out
        }
    }

    episode.forEachIndexed { t, step ->
        if (step.state != trackedState) return@forEachIndexed
        val probabilities = policy(theta, step.state)
        // ∂ log π(a|s) / ∂θ[s][1] = 1{a = 1} − π(1|s)
        val score = (if (step.action == 1) 1f else 0f) - probabilities[1]
        total += advantages[t] * score
    }
    return total
}

private class Spread(val mean: Float, val sd: Float, val samples: List<Float>)

private fun spreadOf(
    estimator: Estimator,
    seed: Int,
    rollouts: Int = 300,
    lambda: Float = 0.95f,
    workers: Int = 1,
): Spread {
    val theta = startingTheta()
    val values = estimateValues(theta, seed + 991)
    val rng = PgRng(seed)
    val samples = (0 until rollouts).map {
        // Averaging `workers` independent rollouts is exactly what a parallel actor batch does.
        (0 until workers).map { _ -> gradientSample(theta, values, rollout(theta, rng), estimator, lambda) }.average().toFloat()
    }
    val mean = samples.average().toFloat()
    val sd = sqrt(samples.sumOf { ((it - mean) * (it - mean)).toDouble() }.toFloat() / samples.size)
    return Spread(mean, sd, samples)
}

/** Histogram of gradient samples, for showing the spread rather than just quoting it. */
private fun histogram(samples: List<Float>, bins: Int = 9, range: Float = 1.2f): List<Float> {
    val counts = MutableList(bins) { 0f }
    samples.forEach { value ->
        val clamped = value.coerceIn(-range, range)
        val bin = (((clamped + range) / (2 * range)) * (bins - 1)).toInt().coerceIn(0, bins - 1)
        counts[bin] += 1f
    }
    return counts.map { it / samples.size }
}

private fun histogramCaptions(bins: Int = 9, range: Float = 1.2f): List<String> =
    (0 until bins).map { "%.1f".format(-range + 2 * range * it / (bins - 1f)) }

// ── REINFORCE ────────────────────────────────────────────────────────────────

/** Train with the given estimator and report the return per episode, averaged over several seeds. */
private fun trainCurve(estimator: Estimator, seeds: List<Int>, iterations: Int = 60, batch: Int = 8, lr: Float = 0.4f): List<Float> {
    val perSeed = seeds.map { seed ->
        val theta = startingTheta()
        val rng = PgRng(seed)
        val values = estimateValues(startingTheta(), seed + 77)
        val curve = mutableListOf<Float>()
        repeat(iterations) {
            val gradient = Array(PG_STATES) { FloatArray(2) }
            var meanReturn = 0f
            repeat(batch) {
                val episode = rollout(theta, rng)
                meanReturn += totalReturn(episode) / batch
                val g = returns(episode)
                episode.forEachIndexed { t, step ->
                    val advantage = when (estimator) {
                        Estimator.RETURN -> g[t]
                        else -> g[t] - values[step.state]
                    }
                    val probabilities = policy(theta, step.state)
                    gradient[step.state][1] += advantage * ((if (step.action == 1) 1f else 0f) - probabilities[1]) / batch
                }
            }
            for (s in 0 until PG_STATES) theta[s][1] += lr * gradient[s][1]
            curve += meanReturn
        }
        curve
    }
    return (0 until iterations).map { i -> perSeed.map { it[i] }.average().toFloat() }
}

private fun reinforceFrames(): List<PgFrame> {
    val plain = spreadOf(Estimator.RETURN, seed = 13)
    val curve = trainCurve(Estimator.RETURN, seeds = listOf(3, 19, 41))
    val frames = mutableListOf<PgFrame>()

    frames += PgFrame(
        status = "Value methods learn what states are worth and derive a policy. REINFORCE skips that: it adjusts " +
            "the policy's parameters directly, pushing up the log-probability of actions that preceded a good return.",
        bars = listOf(
            PgBar("π(right | s) at the start", (0 until PG_GOAL).map { policy(startingTheta(), it)[1] }, MainColor, (0 until PG_GOAL).map { "s$it" }),
        ),
    )
    frames += PgFrame(
        status = "It works: over 60 updates the average return climbs from ${"%.2f".format(curve.first())} to " +
            "${"%.2f".format(curve.last())}, with no value function anywhere in the algorithm.",
        plot = PgPlot("mean return per update", listOf(PgCurve("REINFORCE", curve, MainColor)), -0.4f..1f, "update"),
        readout = "return ${"%.2f".format(curve.first())} → ${"%.2f".format(curve.last())}",
    )
    frames += PgFrame(
        status = "The catch is in the estimator, not the idea. Here are ${plain.samples.size} independent estimates of " +
            "the *same* gradient component, from ${plain.samples.size} rollouts of an unchanged policy. They should " +
            "all be estimating one number.",
        bars = listOf(PgBar("distribution of ∂J/∂θ estimates", histogram(plain.samples), RiskColor, histogramCaptions())),
        readout = "mean ${"%.3f".format(plain.mean)} · sd ${"%.3f".format(plain.sd)}",
    )
    frames += PgFrame(
        status = "The spread is ${"%.1f".format(plain.sd / abs(plain.mean))}× the mean itself — the signal is buried " +
            "in noise, because every action in the episode is credited with the entire return, including the parts " +
            "it had nothing to do with. Every method in this family is an attack on that number.",
        bars = listOf(PgBar("distribution of ∂J/∂θ estimates", histogram(plain.samples), RiskColor, histogramCaptions())),
        readout = "sd/|mean| = ${"%.1f".format(plain.sd / abs(plain.mean))}",
    )
    return frames
}

// ── Actor-critic ─────────────────────────────────────────────────────────────

private fun actorCriticFrames(): List<PgFrame> {
    val plain = spreadOf(Estimator.RETURN, seed = 13)
    val baseline = spreadOf(Estimator.BASELINE, seed = 13)
    val td = spreadOf(Estimator.TD, seed = 13)
    val frames = mutableListOf<PgFrame>()

    frames += PgFrame(
        status = "Start from REINFORCE's problem: gradient estimates with a standard deviation of " +
            "${"%.3f".format(plain.sd)} around a mean of ${"%.3f".format(plain.mean)}.",
        bars = listOf(PgBar("REINFORCE", histogram(plain.samples), PlainColor, histogramCaptions())),
        readout = "sd ${"%.3f".format(plain.sd)}",
    )
    frames += PgFrame(
        status = "Subtract a baseline — what this state was worth on average — so the update reacts to whether the " +
            "return beat expectations rather than to its absolute size. Any state-dependent baseline leaves the " +
            "expected gradient untouched, and the spread drops to ${"%.3f".format(baseline.sd)}. (The two means " +
            "differ by ${"%.3f".format(abs(plain.mean - baseline.mean))} here — that is sampling noise over 300 " +
            "rollouts, not bias.)",
        bars = listOf(
            PgBar("REINFORCE", histogram(plain.samples), PlainColor, histogramCaptions()),
            PgBar("with baseline", histogram(baseline.samples), BetterColor, histogramCaptions()),
        ),
        readout = "sd ${"%.3f".format(plain.sd)} → ${"%.3f".format(baseline.sd)}, mean ${"%.3f".format(plain.mean)} → ${"%.3f".format(baseline.mean)}",
    )
    frames += PgFrame(
        status = "A critic goes further: replace the sampled return entirely with r + γV(s′) − V(s). Now a single " +
            "transition carries the signal, so the estimate stops depending on everything that happened afterwards — " +
            "spread ${"%.3f".format(td.sd)}.",
        bars = listOf(
            PgBar("REINFORCE", histogram(plain.samples), PlainColor, histogramCaptions()),
            PgBar("TD critic", histogram(td.samples), MainColor, histogramCaptions()),
        ),
        readout = "sd ${"%.3f".format(plain.sd)} → ${"%.3f".format(td.sd)}",
    )
    frames += PgFrame(
        status = "That trade is the whole design: the return is unbiased and noisy, the critic's estimate is smooth " +
            "and wrong-until-trained. Actor-critic accepts bias to buy variance — and the actor's updates now depend " +
            "on how good the critic is.",
        bars = listOf(
            PgBar(
                "standard deviation of the same gradient component",
                listOf(plain.sd, baseline.sd, td.sd),
                MainColor,
                listOf("return", "baseline", "critic"),
            ),
        ),
    )
    return frames
}

// ── A2C ──────────────────────────────────────────────────────────────────────

private fun a2cFrames(): List<PgFrame> {
    val single = spreadOf(Estimator.BASELINE, seed = 5, workers = 1)
    val eight = spreadOf(Estimator.BASELINE, seed = 5, workers = 8)
    val sixteen = spreadOf(Estimator.BASELINE, seed = 5, workers = 16)
    val frames = mutableListOf<PgFrame>()

    frames += PgFrame(
        status = "A2C is advantage actor-critic run synchronously across parallel environments: every actor collects " +
            "a rollout, and one update is computed from all of them together.",
        bars = listOf(PgBar("1 actor", histogram(single.samples), PlainColor, histogramCaptions())),
        readout = "sd ${"%.3f".format(single.sd)}",
    )
    frames += PgFrame(
        status = "Averaging 8 independent rollouts cuts the spread from ${"%.3f".format(single.sd)} to " +
            "${"%.3f".format(eight.sd)} — close to the √n the theory promises, since the rollouts are independent.",
        bars = listOf(
            PgBar("1 actor", histogram(single.samples), PlainColor, histogramCaptions()),
            PgBar("8 actors", histogram(eight.samples), BetterColor, histogramCaptions()),
        ),
        readout = "√8 predicts ${"%.3f".format(single.sd / sqrt(8f))}, measured ${"%.3f".format(eight.sd)}",
    )
    frames += PgFrame(
        status = "Doubling again to 16 buys progressively less: variance falls with √n, so the returns diminish " +
            "exactly as fast as the compute grows.",
        bars = listOf(
            PgBar(
                "gradient sd by actor count",
                listOf(single.sd, eight.sd, sixteen.sd),
                MainColor,
                listOf("1", "8", "16"),
            ),
        ),
        readout = "sd ${"%.3f".format(single.sd)} → ${"%.3f".format(eight.sd)} → ${"%.3f".format(sixteen.sd)}",
    )
    frames += PgFrame(
        status = "The other benefit does not show up in a single number: parallel actors are in different parts of " +
            "the environment at any moment, so a batch is decorrelated by construction — the same problem a replay " +
            "buffer solves for off-policy methods, solved here without storing anything.",
        bars = listOf(
            PgBar("16 actors", histogram(sixteen.samples), BetterColor, histogramCaptions()),
        ),
    )
    return frames
}

// ── A3C ──────────────────────────────────────────────────────────────────────

private fun a3cFrames(): List<PgFrame> {
    val single = spreadOf(Estimator.BASELINE, seed = 5, workers = 1)
    val eight = spreadOf(Estimator.BASELINE, seed = 5, workers = 8)
    val stale = spreadOf(Estimator.BASELINE, seed = 71, workers = 8)
    val frames = mutableListOf<PgFrame>()

    frames += PgFrame(
        status = "A3C came first and is the asynchronous version: each worker pulls the shared parameters, runs its " +
            "own rollout, and pushes its gradient back whenever it is ready — no waiting for the slowest actor.",
        bars = listOf(
            PgBar("1 worker", histogram(single.samples), PlainColor, histogramCaptions()),
            PgBar("8 workers", histogram(eight.samples), BetterColor, histogramCaptions()),
        ),
        readout = "sd ${"%.3f".format(single.sd)} → ${"%.3f".format(eight.sd)}",
    )
    frames += PgFrame(
        status = "The variance reduction is the same as A2C's — it comes from averaging independent rollouts, not " +
            "from the asynchrony. What asynchrony adds is throughput on machines where actors finish at different times.",
        bars = listOf(
            PgBar(
                "gradient sd",
                listOf(single.sd, eight.sd),
                MainColor,
                listOf("1 worker", "8 workers"),
            ),
        ),
    )
    frames += PgFrame(
        status = "It also adds a cost: a worker computes its gradient from parameters that may already be out of " +
            "date by the time the update lands. Gradients arrive slightly wrong, in a way that grows with the number " +
            "of workers.",
        bars = listOf(
            PgBar("synchronous batch", histogram(eight.samples), BetterColor, histogramCaptions()),
            PgBar("stale gradients", histogram(stale.samples), RiskColor, histogramCaptions()),
        ),
    )
    frames += PgFrame(
        status = "That is why A2C — the synchronous version — became the default: on GPUs, batching all actors into " +
            "one forward pass is faster than running them asynchronously, and the gradients are never stale. Same " +
            "algorithm, better hardware fit.",
        bars = listOf(
            PgBar(
                "gradient sd",
                listOf(single.sd, eight.sd, stale.sd),
                MainColor,
                listOf("1", "8 sync", "8 stale"),
            ),
        ),
    )
    return frames
}

// ── GAE ──────────────────────────────────────────────────────────────────────

private fun gaeFrames(): List<PgFrame> {
    val lambdas = listOf(0f, 0.5f, 0.9f, 0.95f, 1f)
    val spreads = lambdas.map { spreadOf(Estimator.GAE, seed = 13, lambda = it) }
    val monteCarlo = spreadOf(Estimator.RETURN, seed = 13)
    val frames = mutableListOf<PgFrame>()

    frames += PgFrame(
        status = "The two ends of the advantage spectrum: the sampled return (unbiased, sd " +
            "${"%.3f".format(monteCarlo.sd)}) and the one-step TD error (biased by the critic's errors, but far " +
            "quieter at sd ${"%.3f".format(spreads.first().sd)}).",
        bars = listOf(
            PgBar("λ = 0 (one-step TD)", histogram(spreads.first().samples), MainColor, histogramCaptions()),
            PgBar("Monte-Carlo return", histogram(monteCarlo.samples), PlainColor, histogramCaptions()),
        ),
        readout = "sd ${"%.3f".format(spreads.first().sd)} vs ${"%.3f".format(monteCarlo.sd)}",
    )
    frames += PgFrame(
        status = "GAE interpolates with one knob: an exponentially weighted average of every n-step advantage, with " +
            "λ setting the decay. λ = 0 is the one-step TD error, λ = 1 is the full Monte-Carlo advantage. Measured " +
            "here: ${spreads.joinToString(" → ") { "%.2f".format(it.sd) }}.",
        bars = listOf(
            PgBar("gradient sd by λ", spreads.map { it.sd }, MainColor, lambdas.map { "λ=$it" }),
        ),
        readout = "lowest spread at λ = ${lambdas[spreads.indices.minByOrNull { spreads[it].sd }!!]}",
    )
    frames += PgFrame(
        status = "Note that the minimum is not at λ = 0. The textbook picture — variance rising monotonically with " +
            "λ — assumes an accurate critic; this one is a Monte-Carlo estimate with errors of its own, and at λ = 0 " +
            "every bit of that error goes straight into the gradient. Between λ = ${lambdas[1]} and 1 the trend is " +
            "monotone as expected.",
        bars = listOf(
            PgBar("gradient sd by λ", spreads.map { it.sd }, MainColor, lambdas.map { "λ=$it" }),
            PgBar("gradient mean by λ", spreads.map { it.mean }, DeepColor, lambdas.map { "λ=$it" }),
        ),
        readout = "mean drifts ${"%.3f".format(spreads.first().mean)} → ${"%.3f".format(spreads.last().mean)} as λ → 1",
    )
    frames += PgFrame(
        status = "The mean drift is the bias half of the trade: at low λ the estimate leans on the critic, so a " +
            "critic that is wrong pulls the gradient with it no matter how many rollouts are averaged. λ ≈ 0.95 is " +
            "the common default because it keeps most of the variance reduction while depending on the critic only " +
            "weakly — a hedge against exactly the critic error visible in the previous frame.",
        plot = PgPlot(
            "gradient sd across λ",
            listOf(PgCurve("sd", spreads.map { it.sd }, MainColor)),
            0f..spreads.maxOf { it.sd } * 1.2f,
            "λ = 0 · 0.5 · 0.9 · 0.95 · 1",
        ),
        readout = "λ = 0.95 → sd ${"%.3f".format(spreads[3].sd)}, λ = 1 → ${"%.3f".format(spreads[4].sd)}",
    )
    return frames
}

// ── Trust region: TRPO and PPO ───────────────────────────────────────────────

private class StepResult(val returns: List<Float>, val maxKl: Float, val maxRatio: Float)

/**
 * Policy improvement at a given step size, optionally clipping the probability ratio the way PPO
 * does. Returns the learning curve plus the largest KL divergence and ratio seen — the quantities
 * TRPO constrains and PPO clips.
 */
private fun trustRegionRun(lr: Float, clip: Float?, seed: Int = 29, iterations: Int = 40, batch: Int = 4): StepResult {
    val theta = startingTheta()
    val rng = PgRng(seed)
    val values = estimateValues(startingTheta(), seed + 5)
    val curve = mutableListOf<Float>()
    var maxKl = 0f
    var maxRatio = 1f

    repeat(iterations) {
        val old = Array(PG_STATES) { theta[it].copyOf() }
        val gradient = Array(PG_STATES) { FloatArray(2) }
        var meanReturn = 0f
        repeat(batch) {
            val episode = rollout(theta, rng)
            meanReturn += totalReturn(episode) / batch
            val g = returns(episode)
            episode.forEachIndexed { t, step ->
                // Advantage estimates are noisy — that is what the earlier labs measured — and a
                // large step multiplies that noise straight into the policy.
                val advantage = g[t] - values[step.state] + 3f * (rng.next() - 0.5f)
                val probabilities = policy(theta, step.state)
                gradient[step.state][1] += advantage * ((if (step.action == 1) 1f else 0f) - probabilities[1]) / batch
            }
        }
        for (s in 0 until PG_STATES) {
            var delta = lr * gradient[s][1]
            if (clip != null) {
                // PPO clips the objective once the probability ratio leaves [1−ε, 1+ε], which
                // bounds how far one update can move the policy. On a softmax the equivalent bound
                // is on the logit step, which is what is applied here.
                val bound = ln(1f + clip)
                delta = delta.coerceIn(-bound, bound)
            }
            theta[s][1] += delta
        }
        curve += meanReturn

        for (s in 0 until PG_GOAL) {
            val before = policy(old, s)
            val after = policy(theta, s)
            val kl = (0..1).sumOf { (before[it] * ln((before[it] + 1e-8f) / (after[it] + 1e-8f))).toDouble() }.toFloat()
            maxKl = max(maxKl, kl)
            maxRatio = max(maxRatio, max(after[1] / (before[1] + 1e-8f), after[0] / (before[0] + 1e-8f)))
        }
    }
    return StepResult(curve, maxKl, maxRatio)
}

private fun trpoFrames(): List<PgFrame> {
    val small = trustRegionRun(lr = 0.4f, clip = null)
    val large = trustRegionRun(lr = 20f, clip = null)
    val constrained = trustRegionRun(lr = 20f, clip = 0.2f)
    val frames = mutableListOf<PgFrame>()

    frames += PgFrame(
        status = "A policy gradient tells you a direction, not a distance. At a modest step size the policy improves " +
            "steadily: return ${"%.2f".format(small.returns.first())} → ${"%.2f".format(small.returns.last())}, " +
            "largest KL between consecutive policies ${"%.3f".format(small.maxKl)}.",
        plot = PgPlot("mean return", listOf(PgCurve("small steps", small.returns, MainColor)), -0.4f..1f, "update"),
        readout = "max KL ${"%.3f".format(small.maxKl)}",
    )
    frames += PgFrame(
        status = "Push the step size to 20 and the same gradient direction destroys the policy: return ends at " +
            "${"%.2f".format(large.returns.last())} — the agent never reaches the goal again. The KL between " +
            "successive policies hits ${"%.2f".format(large.maxKl)}, so a single noisy advantage estimate was enough " +
            "to move the policy somewhere the batch that justified the step says nothing about.",
        plot = PgPlot(
            "mean return",
            listOf(
                PgCurve("small steps", small.returns, MainColor),
                PgCurve("large steps", large.returns, RiskColor),
            ),
            -0.4f..1f, "update",
        ),
        readout = "max KL ${"%.3f".format(small.maxKl)} → ${"%.2f".format(large.maxKl)}",
    )
    frames += PgFrame(
        status = "TRPO's answer is to make that explicit: maximise the surrogate objective subject to a hard KL " +
            "constraint, so no update can move the policy further than a fixed distance in distribution space — " +
            "regardless of what the raw gradient magnitude suggests.",
        bars = listOf(
            PgBar(
                "largest KL between consecutive policies",
                listOf(small.maxKl, large.maxKl, constrained.maxKl),
                DeepColor,
                listOf("small lr", "large lr", "constrained"),
            ),
        ),
        readout = "same large learning rate, KL held to ${"%.3f".format(constrained.maxKl)}",
    )
    frames += PgFrame(
        status = "Constrained, the aggressive learning rate is safe again: return " +
            "${"%.2f".format(constrained.returns.last())} without the collapse. The price is the machinery — a " +
            "conjugate-gradient solve and a line search per update, which is exactly what PPO set out to avoid.",
        plot = PgPlot(
            "mean return",
            listOf(
                PgCurve("large steps", large.returns, RiskColor),
                PgCurve("KL-constrained", constrained.returns, BetterColor),
            ),
            -0.4f..1f, "update",
        ),
    )
    return frames
}

private fun ppoFrames(): List<PgFrame> {
    val unclipped = trustRegionRun(lr = 20f, clip = null)
    val clipped = trustRegionRun(lr = 20f, clip = 0.2f)
    val ratios = listOf(0.5f, 0.8f, 1f, 1.2f, 1.5f, 2f)
    val advantage = 1f
    val objective = ratios.map { min(it * advantage, it.coerceIn(0.8f, 1.2f) * advantage) }
    val frames = mutableListOf<PgFrame>()

    frames += PgFrame(
        status = "PPO wants TRPO's guarantee without TRPO's solver. It works with the probability ratio " +
            "r = π_new(a|s) / π_old(a|s) — how much more likely the updated policy makes an action it already took.",
        bars = listOf(PgBar("probability ratio r", ratios, PlainColor, ratios.map { "%.1f".format(it) })),
    )
    frames += PgFrame(
        status = "The clipped objective is min(r·A, clip(r, 0.8, 1.2)·A). For a positive advantage the objective " +
            "stops improving once r passes 1.2, so there is no gradient left to push the action further — the " +
            "incentive to take a huge step simply disappears.",
        bars = listOf(
            PgBar("unclipped r·A", ratios.map { it * advantage }, PlainColor, ratios.map { "%.1f".format(it) }),
            PgBar("clipped objective", objective, BetterColor, ratios.map { "%.1f".format(it) }),
        ),
        readout = "flat beyond r = 1.2 when A > 0",
    )
    frames += PgFrame(
        status = "Same aggressive learning rate as the TRPO lab: unclipped it drives the largest KL to " +
            "${"%.2f".format(unclipped.maxKl)}; clipped it stays at ${"%.3f".format(clipped.maxKl)} and the return " +
            "ends at ${"%.2f".format(clipped.returns.last())} instead of ${"%.2f".format(unclipped.returns.last())}.",
        plot = PgPlot(
            "mean return",
            listOf(
                PgCurve("unclipped", unclipped.returns, RiskColor),
                PgCurve("PPO-clipped", clipped.returns, BetterColor),
            ),
            -0.4f..1f, "update",
        ),
        readout = "max KL ${"%.2f".format(unclipped.maxKl)} → ${"%.3f".format(clipped.maxKl)}",
    )
    frames += PgFrame(
        status = "That is the whole algorithm: a clip, a few epochs over the same batch, and first-order optimisation. " +
            "It is not a bound on anything the way TRPO's constraint is — it just removes the reward for stepping too " +
            "far, which turned out to be enough, and cheap enough to become the default.",
        bars = listOf(
            PgBar(
                "largest KL between consecutive policies",
                listOf(unclipped.maxKl, clipped.maxKl),
                DeepColor,
                listOf("unclipped", "clipped"),
            ),
        ),
    )
    return frames
}

// ── Continuous control ───────────────────────────────────────────────────────
// A single continuous action a ∈ [−1, 1] with a known reward curve, so the optimum is available for
// comparison and the critic's error can be measured rather than assumed.

private const val BEST_ACTION = 0.45f

private fun trueReward(action: Float) = 1f - (action - BEST_ACTION) * (action - BEST_ACTION)

private fun dpgFrames(): List<PgFrame> {
    val actions = (0..20).map { -1f + 2f * it / 20f }
    var action = -0.8f
    val path = mutableListOf(action)
    val lr = 0.25f
    repeat(18) {
        // The deterministic policy gradient: follow ∂Q/∂a straight up the critic's surface.
        val slope = -2f * (action - BEST_ACTION)
        action += lr * slope
        path += action
    }
    val frames = mutableListOf<PgFrame>()

    frames += PgFrame(
        status = "With a continuous action there is no max over actions to take — you cannot enumerate the " +
            "uncountable. A stochastic policy gradient would sample actions and average; a deterministic one asks the " +
            "critic which way is uphill.",
        bars = listOf(PgBar("Q(a) — the critic's surface", actions.map { trueReward(it) }, MainColor)),
        readout = "optimum at a = ${"%.2f".format(BEST_ACTION)}",
    )
    frames += PgFrame(
        status = "DPG's update is the chain rule through the critic: ∇θ J = ∇θ μ(s) · ∂Q/∂a. Starting at " +
            "a = ${"%.2f".format(path.first())}, following ∂Q/∂a reaches ${"%.2f".format(path.last())} in " +
            "${path.size - 1} steps without ever sampling an action.",
        plot = PgPlot("action over updates", listOf(PgCurve("μ(s)", path, DeepColor)), -1f..1f, "update"),
        readout = "a ${"%.2f".format(path.first())} → ${"%.2f".format(path.last())} (optimum ${"%.2f".format(BEST_ACTION)})",
    )
    frames += PgFrame(
        status = "Because no action sampling is involved, the gradient has no sampling variance at all — the entire " +
            "expectation over actions collapses to one evaluation. That is the deterministic policy gradient theorem's " +
            "payoff.",
        bars = listOf(
            PgBar("|a − optimum| over updates", path.map { abs(it - BEST_ACTION) }, BetterColor),
        ),
    )
    frames += PgFrame(
        status = "The bill arrives elsewhere: a deterministic policy explores nothing. Off-policy exploration noise " +
            "has to be added by hand — which is precisely what DDPG bolts on, along with the replay buffer and target " +
            "networks it inherits from DQN.",
        bars = listOf(PgBar("Q(a)", actions.map { trueReward(it) }, MainColor)),
    )
    return frames
}

private class CriticRun(val singleBias: List<Float>, val twinBias: List<Float>)

/**
 * Critic overestimation in continuous control: a critic with independent noise per action, and a
 * policy that maximises it. One critic chases its own noise; the minimum of two independent critics
 * mostly does not.
 */
private fun criticBiasRun(seed: Int = 61, rounds: Int = 40, samples: Int = 12): CriticRun {
    val rng = PgRng(seed)
    val single = mutableListOf<Float>()
    val twin = mutableListOf<Float>()
    var singleRunning = 0f
    var twinRunning = 0f
    repeat(rounds) { round ->
        val candidates = (0 until samples).map { -1f + 2f * it / (samples - 1f) }
        val noiseA = candidates.map { rng.normal() * 0.15f }
        val noiseB = candidates.map { rng.normal() * 0.15f }
        val singlePick = candidates.indices.maxByOrNull { trueReward(candidates[it]) + noiseA[it] }!!
        val twinPick = candidates.indices.maxByOrNull {
            min(trueReward(candidates[it]) + noiseA[it], trueReward(candidates[it]) + noiseB[it])
        }!!
        val singleEstimate = trueReward(candidates[singlePick]) + noiseA[singlePick]
        val twinEstimate = min(
            trueReward(candidates[twinPick]) + noiseA[twinPick],
            trueReward(candidates[twinPick]) + noiseB[twinPick],
        )
        singleRunning += (singleEstimate - trueReward(candidates[singlePick]) - singleRunning) / (round + 1)
        twinRunning += (twinEstimate - trueReward(candidates[twinPick]) - twinRunning) / (round + 1)
        single += singleRunning
        twin += twinRunning
    }
    return CriticRun(single, twin)
}

private fun ddpgFrames(): List<PgFrame> {
    val actions = (0..20).map { -1f + 2f * it / 20f }
    val bias = criticBiasRun()
    val frames = mutableListOf<PgFrame>()

    frames += PgFrame(
        status = "DDPG is DPG plus the DQN machinery: a replay buffer, target networks for both actor and critic, " +
            "and exploration noise added to the deterministic action.",
        bars = listOf(PgBar("Q(a)", actions.map { trueReward(it) }, MainColor)),
    )
    frames += PgFrame(
        status = "Off-policy is the point — the replay buffer holds transitions from every past policy, so a " +
            "continuous-control agent can reuse experience instead of throwing each batch away after one update the " +
            "way an on-policy method does.",
        bars = listOf(
            PgBar("exploration: μ(s) + noise", actions.map { trueReward(it) }, MainColor),
        ),
    )
    frames += PgFrame(
        status = "The known failure: the actor maximises the critic, so any state-action pair the critic happens to " +
            "overrate becomes the policy's target. Averaged over ${bias.singleBias.size} rounds the critic's value at " +
            "the chosen action sits ${"%.3f".format(bias.singleBias.last())} above the truth — pure noise, promoted " +
            "by the max.",
        plot = PgPlot(
            "critic bias at the chosen action",
            listOf(PgCurve("single critic", bias.singleBias, RiskColor)),
            -0.1f..0.35f, "round",
        ),
        readout = "mean overestimate ${"%.3f".format(bias.singleBias.last())}",
    )
    frames += PgFrame(
        status = "That is the loop TD3 breaks. DDPG's reputation for brittleness is mostly this: the actor is an " +
            "optimiser pointed straight at the critic's errors, and nothing in the algorithm damps it.",
        bars = listOf(
            PgBar(
                "critic bias",
                listOf(bias.singleBias.last(), bias.twinBias.last()),
                DeepColor,
                listOf("DDPG", "twin critics"),
            ),
        ),
    )
    return frames
}

private fun td3Frames(): List<PgFrame> {
    val bias = criticBiasRun()
    val frames = mutableListOf<PgFrame>()

    frames += PgFrame(
        status = "TD3 is three fixes to DDPG, and the first is the important one. A single critic's value at the " +
            "action the actor picked is biased upward by ${"%.3f".format(bias.singleBias.last())} — the actor is " +
            "selecting for the critic's own noise.",
        plot = PgPlot(
            "critic bias at the chosen action",
            listOf(PgCurve("single critic", bias.singleBias, RiskColor)),
            -0.1f..0.35f, "round",
        ),
        readout = "single-critic bias ${"%.3f".format(bias.singleBias.last())}",
    )
    frames += PgFrame(
        status = "Train two critics and use the smaller of the two values as the target. Noise that inflates one " +
            "critic rarely inflates the other at the same action, so the minimum lands near the truth: bias " +
            "${"%.3f".format(bias.twinBias.last())}.",
        plot = PgPlot(
            "critic bias at the chosen action",
            listOf(
                PgCurve("single critic", bias.singleBias, RiskColor),
                PgCurve("min of twin critics", bias.twinBias, BetterColor),
            ),
            -0.1f..0.35f, "round",
        ),
        readout = "${"%.3f".format(bias.singleBias.last())} → ${"%.3f".format(bias.twinBias.last())}",
    )
    frames += PgFrame(
        status = "Fix two — delayed policy updates: update the actor once per two critic updates, so the policy " +
            "chases a critic that has had time to settle. Fix three — target policy smoothing: add noise to the " +
            "target action, which stops the critic from developing a sharp spike the actor can exploit.",
        bars = listOf(
            PgBar(
                "critic bias",
                listOf(bias.singleBias.last(), bias.twinBias.last()),
                DeepColor,
                listOf("DDPG", "TD3"),
            ),
        ),
    )
    frames += PgFrame(
        status = "Note the deliberate underestimation: the minimum of two critics is biased low, and TD3 accepts " +
            "that. An underestimated action simply does not get chosen; an overestimated one becomes the policy.",
        bars = listOf(
            PgBar(
                "sign of the bias",
                listOf(bias.singleBias.last(), bias.twinBias.last()),
                MainColor,
                listOf("over", "slightly under"),
            ),
        ),
    )
    return frames
}

private class EntropyPoint(val temperature: Float, val sigma: Float, val reward: Float, val entropy: Float)

/**
 * For a Gaussian policy N(μ, σ) on the quadratic reward, the expected reward is available in closed
 * form (E[−(a−a*)²] = −σ² at the optimal mean) and the entropy is ½ln(2πeσ²). So the temperature
 * that maximises reward + α·entropy can be found by sweeping σ — no training loop needed, and the
 * answer is exact.
 */
private fun entropySweep(): List<EntropyPoint> {
    val sigmas = (1..40).map { it * 0.02f }
    return listOf(0f, 0.02f, 0.05f, 0.1f, 0.2f).map { alpha ->
        val best = sigmas.maxByOrNull { sigma ->
            val reward = 1f - sigma * sigma
            val entropy = 0.5f * ln(2f * Math.PI.toFloat() * exp(1f) * sigma * sigma)
            reward + alpha * entropy
        }!!
        EntropyPoint(
            alpha,
            best,
            1f - best * best,
            0.5f * ln(2f * Math.PI.toFloat() * exp(1f) * best * best),
        )
    }
}

private fun sacFrames(): List<PgFrame> {
    val sweep = entropySweep()
    val frames = mutableListOf<PgFrame>()

    frames += PgFrame(
        status = "SAC optimises reward plus α times the policy's entropy. With a Gaussian policy on this reward the " +
            "trade is exactly solvable: expected reward falls as σ², entropy grows as ln σ.",
        bars = listOf(
            PgBar("chosen σ by temperature α", sweep.map { it.sigma }, MainColor, sweep.map { "α=${it.temperature}" }),
        ),
    )
    frames += PgFrame(
        status = "At α = 0 the optimum is a deterministic policy (σ → 0, reward ${"%.2f".format(sweep.first().reward)}). " +
            "Raise α and the optimal policy widens: at α = ${sweep.last().temperature} it settles at σ = " +
            "${"%.2f".format(sweep.last().sigma)}, paying ${"%.2f".format(sweep.first().reward - sweep.last().reward)} " +
            "of reward for the extra randomness.",
        bars = listOf(
            PgBar("expected reward", sweep.map { it.reward }, BetterColor, sweep.map { "α=${it.temperature}" }),
            PgBar("policy entropy", sweep.map { it.entropy }, DeepColor, sweep.map { "α=${it.temperature}" }),
        ),
        readout = "α = 0 → σ ${"%.2f".format(sweep.first().sigma)} · α = ${sweep.last().temperature} → σ ${"%.2f".format(sweep.last().sigma)}",
    )
    frames += PgFrame(
        status = "That width is not wasted: it is exploration the objective itself asks for, rather than noise bolted " +
            "on afterwards the way DDPG does it. The policy stays stochastic exactly as long as being stochastic is " +
            "worth its cost.",
        plot = PgPlot(
            "σ chosen as α rises",
            listOf(PgCurve("σ*", sweep.map { it.sigma }, MainColor)),
            0f..1f,
            "α = 0 · 0.02 · 0.05 · 0.1 · 0.2",
        ),
    )
    frames += PgFrame(
        status = "SAC combines that objective with TD3's twin critics and off-policy replay, and tunes α " +
            "automatically against a target entropy — which is most of why it needs so little per-environment " +
            "tuning compared to DDPG.",
        bars = listOf(
            PgBar("σ", sweep.map { it.sigma }, MainColor, sweep.map { "α=${it.temperature}" }),
        ),
    )
    return frames
}

private fun maxEntropyFrames(): List<PgFrame> {
    val sweep = entropySweep()
    val qValues = listOf(1.0f, 0.9f, 0.4f, 0.1f)
    fun boltzmann(alpha: Float): List<Float> {
        val scaled = qValues.map { it / alpha }
        val maxScaled = scaled.max()
        val exps = scaled.map { exp(it - maxScaled) }
        val sum = exps.sum()
        return exps.map { it / sum }
    }

    val frames = mutableListOf<PgFrame>()
    frames += PgFrame(
        status = "Standard RL maximises expected return, and its optimal policy is deterministic — one best action " +
            "per state, everything else discarded. Maximum-entropy RL maximises return plus α·H(π) instead.",
        bars = listOf(PgBar("Q(a) for four actions", qValues, MainColor, listOf("a1", "a2", "a3", "a4"))),
    )
    frames += PgFrame(
        status = "The optimal policy for that objective is not greedy but Boltzmann: π(a) ∝ exp(Q(a)/α). At α = 0.05 " +
            "the two near-tied actions still split ${"%.0f".format(boltzmann(0.05f)[0] * 100)}/" +
            "${"%.0f".format(boltzmann(0.05f)[1] * 100)} instead of winner-take-all.",
        bars = listOf(
            PgBar("π(a) at α = 0.05", boltzmann(0.05f), BetterColor, listOf("a1", "a2", "a3", "a4")),
            PgBar("π(a) at α = 0.5", boltzmann(0.5f), DeepColor, listOf("a1", "a2", "a3", "a4")),
        ),
        readout = "α → 0 recovers the greedy policy",
    )
    frames += PgFrame(
        status = "Keeping the near-tie alive is the point. A policy that commits to a 1.0-vs-0.9 difference is " +
            "betting that its own value estimates are right to a tenth; the entropy term keeps that bet hedged until " +
            "the evidence separates them.",
        bars = listOf(
            PgBar("π(a) at α = 0.02", boltzmann(0.02f), PlainColor, listOf("a1", "a2", "a3", "a4")),
            PgBar("π(a) at α = 0.2", boltzmann(0.2f), MainColor, listOf("a1", "a2", "a3", "a4")),
        ),
    )
    frames += PgFrame(
        status = "α is the exchange rate between reward and randomness, and it is a real cost: on the continuous task " +
            "in the SAC lab, α = ${sweep.last().temperature} gives up " +
            "${"%.2f".format(sweep.first().reward - sweep.last().reward)} of expected reward. The return is " +
            "robustness — policies that keep alternatives alive degrade more gracefully when the environment shifts.",
        bars = listOf(
            PgBar("reward given up", sweep.map { sweep.first().reward - it.reward }, RiskColor, sweep.map { "α=${it.temperature}" }),
        ),
    )
    return frames
}

// ── Config ───────────────────────────────────────────────────────────────────

private val varianceLegend = listOf(
    PlainColor to "Baseline",
    BetterColor to "Improved",
    RiskColor to "Failure mode",
)

private val pgConfigs = mapOf(
    "reinforce" to PgConfig(
        intro = "The vanilla policy gradient, run for real — then 300 independent estimates of the same gradient " +
            "component, to show what the algorithm is actually working with.",
        legend = varianceLegend,
        build = ::reinforceFrames,
    ),
    "actor_critic" to PgConfig(
        intro = "The same gradient component under three estimators: raw return, return minus a baseline, and a TD " +
            "critic. The spread is measured, not asserted.",
        legend = varianceLegend,
        build = ::actorCriticFrames,
    ),
    "a2c" to PgConfig(
        intro = "What parallel actors buy: the same estimate averaged over 1, 8 and 16 independent rollouts, against " +
            "the √n the theory predicts.",
        legend = varianceLegend,
        build = ::a2cFrames,
    ),
    "a3c" to PgConfig(
        intro = "A3C's asynchrony against A2C's batching — where the variance reduction actually comes from, and what " +
            "stale gradients cost.",
        legend = varianceLegend,
        build = ::a3cFrames,
    ),
    "gae" to PgConfig(
        intro = "λ swept from 0 to 1, with the gradient's standard deviation and mean measured at each setting — the " +
            "bias/variance trade in two numbers.",
        legend = varianceLegend,
        build = ::gaeFrames,
    ),
    "trpo" to PgConfig(
        intro = "The same gradient direction at a safe and a reckless step size, with the KL divergence between " +
            "consecutive policies measured at each update.",
        legend = varianceLegend,
        build = ::trpoFrames,
    ),
    "ppo" to PgConfig(
        intro = "The clipped objective drawn as a function of the probability ratio, then the same aggressive step " +
            "size with and without it.",
        legend = varianceLegend,
        build = ::ppoFrames,
    ),
    "dpg" to PgConfig(
        intro = "One continuous action, a known reward curve, and a policy that climbs ∂Q/∂a instead of sampling.",
        legend = listOf(
            MainColor to "Critic Q(a)",
            DeepColor to "Policy μ(s)",
            BetterColor to "Distance to optimum",
        ),
        build = ::dpgFrames,
    ),
    "ddpg" to PgConfig(
        intro = "DPG plus replay and target networks — and the measured failure mode that follows from an actor " +
            "pointed straight at a critic's errors.",
        legend = varianceLegend,
        build = ::ddpgFrames,
    ),
    "td3" to PgConfig(
        intro = "Critic overestimation measured over 40 rounds, with a single critic and with the minimum of two.",
        legend = varianceLegend,
        build = ::td3Frames,
    ),
    "sac" to PgConfig(
        intro = "The entropy-regularised objective solved exactly on a Gaussian policy: the optimal σ for each " +
            "temperature, and what it costs in reward.",
        legend = listOf(
            MainColor to "σ",
            BetterColor to "Reward",
            DeepColor to "Entropy",
        ),
        build = ::sacFrames,
    ),
    "max_entropy_rl" to PgConfig(
        intro = "Why the optimal max-entropy policy is Boltzmann rather than greedy, and what each temperature costs " +
            "in expected reward.",
        legend = listOf(
            MainColor to "Policy",
            DeepColor to "High α",
            RiskColor to "Reward given up",
        ),
        build = ::maxEntropyFrames,
    ),
)

private fun pgConfigFor(topicId: String): PgConfig = pgConfigs[topicId] ?: pgConfigs.getValue("reinforce")

// ── UI ───────────────────────────────────────────────────────────────────────

@Composable
fun PolicyGradientSection(topicId: String) {
    val config = remember(topicId) { pgConfigFor(topicId) }
    val frames = remember(config) { config.build() }
    val playback = rememberPlaybackState(key = config, stepCount = frames.size, initialSpeedMs = 1100f)
    val frame = frames[playback.index.coerceIn(0, frames.lastIndex)]

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                config.intro,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            frame.plot?.let { PgPlotCanvas(it, modifier = Modifier.padding(top = 12.dp)) }
            frame.bars.forEach { bar -> PgBars(bar, modifier = Modifier.padding(top = 12.dp)) }

            frame.readout?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }

            Text(
                frame.status,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 10.dp),
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                config.legend.forEach { (color, label) -> PgLegend(color, label) }
            }

            PlaybackTransport(playback)
        }
    }
}

@Composable
private fun PgLegend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(10.dp).background(color, CircleShape))
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}

@Composable
private fun PgPlotCanvas(plot: PgPlot, modifier: Modifier = Modifier) {
    val axisColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
    val span = plot.curves.maxOfOrNull { it.values.size } ?: 1

    Column(modifier = modifier.fillMaxWidth()) {
        Text(plot.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                .padding(8.dp),
        ) {
            Canvas(modifier = Modifier.fillMaxWidth().height(150.dp)) {
                fun place(i: Int, value: Float): Offset {
                    val x = if (span <= 1) 0f else i.toFloat() / (span - 1) * size.width
                    val ny = (value - plot.yRange.start) / (plot.yRange.endInclusive - plot.yRange.start)
                    return Offset(x, size.height - ny.coerceIn(-0.05f, 1.05f) * size.height)
                }
                if (0f in plot.yRange) {
                    val zero = place(0, 0f)
                    drawLine(axisColor, Offset(0f, zero.y), Offset(size.width, zero.y), strokeWidth = 1.5.dp.toPx())
                }
                plot.curves.forEach { curve ->
                    curve.values.forEachIndexed { i, value ->
                        if (i == 0) return@forEachIndexed
                        drawLine(
                            color = curve.color,
                            start = place(i - 1, curve.values[i - 1]),
                            end = place(i, value),
                            strokeWidth = 4.dp.toPx(),
                        )
                    }
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            plot.curves.forEach { curve ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(8.dp).background(curve.color, CircleShape))
                    Text(
                        curve.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 3.dp),
                    )
                }
            }
            Text(
                plot.xLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.End,
            )
        }
    }
}

@Composable
private fun PgBars(bar: PgBar, modifier: Modifier = Modifier) {
    val axis = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
    val peak = bar.values.maxOfOrNull { abs(it) }?.coerceAtLeast(0.001f) ?: 1f

    Column(modifier = modifier.fillMaxWidth()) {
        Text(bar.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Canvas(modifier = Modifier.fillMaxWidth().height(46.dp).padding(top = 4.dp)) {
            val slot = size.width / bar.values.size
            val mid = size.height / 2f
            drawLine(axis, Offset(0f, mid), Offset(size.width, mid), strokeWidth = 1.5.dp.toPx())
            bar.values.forEachIndexed { index, value ->
                val height = (abs(value) / peak) * (size.height / 2f - 2f)
                drawRoundRect(
                    color = if (value >= 0f) bar.color else RiskColor,
                    topLeft = Offset(index * slot + slot * 0.2f, if (value >= 0f) mid - height else mid),
                    size = Size(slot * 0.6f, height.coerceAtLeast(1.5f)),
                    cornerRadius = CornerRadius(3f, 3f),
                )
            }
        }
        if (bar.captions.isNotEmpty()) {
            Row(modifier = Modifier.fillMaxWidth()) {
                bar.captions.forEach { caption ->
                    Text(
                        caption,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}
