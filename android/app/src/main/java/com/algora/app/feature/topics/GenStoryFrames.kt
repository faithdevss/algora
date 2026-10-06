package com.algora.app.feature.topics

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

// ── Generative-model storyboard frames ───────────────────────────────────────
// GANs, diffusion, VAEs, CycleGAN, DCGAN, Stable Diffusion, DeepFakes, StyleGAN and neural style
// transfer, drawn by DeepStoryLabs.kt with the stages in GenStoryStages.kt. Every value is computed on a
// stated toy problem: the GAN's histograms and optimal discriminator from two Gaussians, the forward
// process from a real linear β schedule, a linear VAE trained here, and the coverage, mapping, swap and
// Gram numbers from GenerativeMath.kt. The iOS port (GenStoryFrames.swift) uses the same LCG.

internal val genStoryTopicIds = setOf(
    "gans", "diffusion_models", "vae", "cyclegan", "dcgan",
    "stable_diffusion", "deepfakes", "stylegan", "neural_style_transfer",
)

internal fun genLab(topicId: String): DkLab? = when (topicId) {
    "gans" -> ganLab()
    "diffusion_models" -> diffusionLab()
    "vae" -> vaeLab()
    "cyclegan" -> cycleLab()
    "dcgan" -> dcganLab()
    "stable_diffusion" -> stableLab()
    "deepfakes" -> deepFakeLab()
    "stylegan" -> styleGanLab()
    "neural_style_transfer" -> styleTransferLab()
    else -> null
}

private fun n(v: Double, d: Int = 2) = dkNum(v, d)

private fun legend(ink: DkInk, label: String, style: SwatchStyle = SwatchStyle.Fill) = DkLegend(ink, style, label)

private fun stepActions(frames: List<DkFrame>) =
    frames.mapIndexed { i, f -> DkFrame(f.header, f.stage, f.legend, f.formula, f.headline, f.body, f.chips, if (i == frames.lastIndex) "Start Over" else "Next") }

/** 1,234,567 with thin grouping commas. */
private fun grouped(v: Long): String = v.toString().reversed().chunked(3).joinToString(",").reversed()

/** 786.4k, 16.8M, 68.72B; under ten thousand the plain grouped number. */
private fun big(v: Double): String = when {
    v >= 1e12 -> n(v / 1e12, 1) + "T"
    v >= 1e9 -> n(v / 1e9, 2) + "B"
    v >= 1e6 -> n(v / 1e6, 1) + "M"
    v >= 1e4 -> n(v / 1e3, 1) + "k"
    else -> grouped(Math.round(v))
}

private class GenRng(seed: Long) {
    private var s = seed
    fun u(): Double {
        s = (s * 1103515245L + 12345L) and 0x7fffffffL
        return s / 2147483648.0
    }

    fun g(): Double {
        val a = max(u(), 1e-12)
        val b = u()
        return sqrt(-2 * ln(a)) * cos(2 * PI * b)
    }
}

// ── GANs ──
// Real data N(1.5, 0.6²); the generator a Gaussian whose mean walks toward it. The discriminator drawn
// is the optimal one for that generator, D = p_real / (p_real + p_gen), and the scores and JSD come from
// the 16 bins on screen.

private const val GAN_MU = 1.5
private const val GAN_SD = 0.6

/** Abramowitz–Stegun 7.1.26, written out so both platforms round the same way. */
private fun erf(x: Double): Double {
    val t = 1 / (1 + 0.3275911 * abs(x))
    val y = 1 - (((((1.061405429 * t - 1.453152027) * t) + 1.421413741) * t - 0.284496736) * t + 0.254829592) * t * exp(-x * x)
    return if (x >= 0) y else -y
}

private fun cdf(x: Double, mu: Double, sd: Double) = 0.5 * (1 + erf((x - mu) / (sd * sqrt(2.0))))

private fun pdf(x: Double, mu: Double, sd: Double) = exp(-(x - mu) * (x - mu) / (2 * sd * sd)) / sd

private fun ganBins(mu: Double, sd: Double) = List(16) { i -> cdf(-4 + 0.5 * (i + 1), mu, sd) - cdf(-4 + 0.5 * i, mu, sd) }

private fun ganD(x: Double, mu: Double, sd: Double): Double {
    val r = pdf(x, GAN_MU, GAN_SD)
    val g = pdf(x, mu, sd)
    return if (r + g < 1e-300) 0.5 else r / (r + g)
}

private class GanState(val mu: Double, val sd: Double) {
    val real = ganBins(GAN_MU, GAN_SD)
    val fake = ganBins(mu, sd)
    private val binD = real.indices.map { if (real[it] + fake[it] < 1e-15) 0.5 else real[it] / (real[it] + fake[it]) }
    val dReal = real.indices.sumOf { real[it] * binD[it] }
    val dFake = fake.indices.sumOf { fake[it] * binD[it] }
    val jsd = real.indices.sumOf { i ->
        val m = (real[i] + fake[i]) / 2
        (if (real[i] > 0) 0.5 * real[i] * ln(real[i] / m) else 0.0) + (if (fake[i] > 0) 0.5 * fake[i] * ln(fake[i] / m) else 0.0)
    } / ln(2.0)
    // D at the generator's mean, and how much stronger −log D's gradient is there than log(1 − D)'s.
    val dAtMean = ganD(mu, mu, sd)
    val boost = (1 - dAtMean) / dAtMean
    val stage = DkGan(real, fake, (0..160).map { val x = -4 + it * 0.05; DkP(x, ganD(x, mu, sd)) })
    fun chips() = listOf(DkChip("JSD", n(jsd) + " bits", tint = true), DkChip("D real", n(dReal)), DkChip("D fake", n(dFake)))
}

private fun ganLab(): DkLab = DkLab(DkControl.Track) { _, _ ->
    val header = "real vs generated · 16 bins · optimal D for this generator"
    val legend = listOf(
        legend(DkInk.Green, "Real data"),
        legend(DkInk.Orange, "Generated"),
        legend(DkInk.Blue, "Discriminator D(x)", SwatchStyle.Line),
    )
    val s0 = GanState(-1.5, GAN_SD)
    val rounds = listOf(-0.6, 0.1, 0.65, 1.15).map { GanState(it, GAN_SD) }
    val eq = GanState(GAN_MU, GAN_SD)
    val collapse = GanState(GAN_MU, 0.15)
    fun f(s: GanState, headline: String, body: String, chips: List<DkChip> = s.chips()) =
        DkFrame(header, s.stage, legend, emptyList(), headline, body, chips)
    val roundText = listOf(
        "Round 1: the generator follows {D's slope} toward the real data." to
            "Moving its samples where D(x) rises, the orange hump shifts right. D still separates them — real ${n(rounds[0].dReal)}, fake ${n(rounds[0].dFake)} — and JSD falls from ${n(s0.jsd)} to ${n(rounds[0].jsd)} bits.",
        "Round 2: the humps overlap and {D's step softens}." to
            "Where both distributions put mass, the best D can only say how much more likely real is. Its curve tilts instead of jumping, and fakes now score ${n(rounds[1].dFake)}.",
        "Round 3: D is unsure across {the whole overlap}." to
            "JSD is down to ${n(rounds[2].jsd)} bits. D's slope at the fakes is gentler now, so each round moves the generator less than the one before.",
        "Round 4: {nearly matched}." to
            "The generated mean is ${n(rounds[3].mu)} against 1.50. D real ${n(rounds[3].dReal)}, D fake ${n(rounds[3].dFake)}: barely better than a guess.",
    )
    stepActions(
        listOf(
            f(
                s0, "Two networks, opposite objectives.",
                "The generator turns noise into samples; the discriminator tries to tell them from real data. At step 1 the two barely overlap, so the best discriminator scores real data ${n(s0.dReal)} and fakes ${n(s0.dFake)} — the generator has almost nothing to learn from yet.",
            ),
            f(
                s0, "D is flat where the fakes are, so {its gradient vanishes}.",
                "The generator learns only through D's slope at its own samples, and D is pinned near 0 across the orange hump. The original loss log(1 − D) gives almost no gradient there; training uses −log D instead, whose gradient is ${big(s0.boost)}× larger at the fakes' centre.",
                listOf(DkChip("D at fake mean", lsSci(s0.dAtMean), tint = true), DkChip("−log D boost", "×" + big(s0.boost))),
            ),
        ) + rounds.mapIndexed { i, s -> f(s, roundText[i].first, roundText[i].second) } + listOf(
            f(
                eq, "{D = 0.5} everywhere: a coin flip.",
                "The generated histogram equals the real one, so the best discriminator can do no better than chance. JSD is 0 bits — the equilibrium the minimax game aims for.",
            ),
            f(
                collapse, "Same mean, one narrow spike: {mode collapse}.",
                "A generator that finds one convincing output can pile every sample onto it. D climbs back toward 1 where real data has no fakes (JSD ${n(collapse.jsd)} bits), and the generator tends to hop to another spike rather than spread out.",
            ),
        ),
    )
}

// ── Diffusion ──
// 24 points on a unit ring, noised by x_t = √ᾱ·x₀ + √(1−ᾱ)·ε with ᾱ from the linear β schedule
// 0.0001 → 0.02 over T = 1000. The reverse frames draw the deterministic (DDIM) path a perfect noise
// predictor takes from fresh noise to new points on the ring.

private val diffusionAbar: DoubleArray = run {
    val out = DoubleArray(1001)
    out[0] = 1.0
    for (t in 1..1000) out[t] = out[t - 1] * (1 - (1e-4 + (0.02 - 1e-4) * (t - 1) / 999.0))
    out
}

private val diffusionTimes = listOf(0, 100, 250, 500, 1000)

private fun abarText(t: Int): String = if (t == 1000) "0.00" else n(diffusionAbar[t])

private fun diffusionLab(): DkLab = DkLab(DkControl.Track) { _, _ ->
    val count = 24
    val ring = List(count) { DkP(cos(2 * PI * it / count), sin(2 * PI * it / count)) }
    val rng = GenRng(89)
    val eps = List(count) { DkP(rng.g(), rng.g()) }
    val fresh = List(count) { DkP(rng.g(), rng.g()) }
    // Where the reverse process ends: the ring, each point between two training points.
    val landed = List(count) { val a = 2 * PI * (it + 0.5 + 0.3 * (rng.u() - 0.5)) / count; DkP(cos(a), sin(a)) }

    fun noised(x0: List<DkP>, e: List<DkP>, t: Int): List<DkP> {
        val a = sqrt(diffusionAbar[t])
        val b = sqrt(1 - diffusionAbar[t])
        return x0.indices.map { DkP(a * x0[it].x + b * e[it].x, a * x0[it].y + b * e[it].y) }
    }

    val forwardThumbs = diffusionTimes.map { t -> DkThumb("t $t", "ᾱ ${abarText(t)}", if (t == 0) ring else noised(ring, eps, t), t == 0) }
    val reverseThumbs = diffusionTimes.map { t -> DkThumb("t $t", "ᾱ ${abarText(t)}", noised(landed, fresh, t), false) }
    val fwdHeader = "x₀ (24 points on a ring) → x_t · linear β, T = 1000"
    val revHeader = "x_T ~ N(0, I) → x₀ · reverse process, T = 1000"
    val fwdLegend = listOf(legend(DkInk.Green, "Data manifold x₀"), legend(DkInk.Cyan, "Noised sample x_t"))
    val revLegend = listOf(legend(DkInk.Green, "Data manifold"), legend(DkInk.Cyan, "Denoised sample x_t"))

    fun sig(t: Int) = "signal √ᾱ = {${n(sqrt(diffusionAbar[t]))}} noise √(1−ᾱ) = {${n(sqrt(1 - diffusionAbar[t]))}}"
    fun forward(t: Int, headline: String, body: String, formula: List<String> = listOf("x_t = √ᾱ·x₀ + √(1−ᾱ)·ε", sig(t))) = DkFrame(
        fwdHeader,
        DkDiffusion(ring, if (t == 0) emptyList() else noised(ring, eps, t), t > 0, false, forwardThumbs, diffusionTimes.indexOf(t)),
        fwdLegend, formula, headline, body,
    )
    fun reverse(t: Int, headline: String, body: String, pts: List<DkP> = noised(landed, fresh, t), faint: Boolean = true) = DkFrame(
        revHeader,
        DkDiffusion(ring, pts, false, faint, reverseThumbs, diffusionTimes.indexOf(t)),
        revLegend, listOf("x_t = √ᾱ·x̂₀ + √(1−ᾱ)·ε̂", sig(t)), headline, body,
    )

    val onRing = landed.count { abs(hypot(it.x, it.y) - 1) < 0.05 }
    val copies = landed.count { p -> ring.any { hypot(it.x - p.x, it.y - p.y) < 0.05 } }
    stepActions(
        listOf(
            forward(
                0, "x₀ — {24 points on a ring}.",
                "This is the data. All a diffusion model learns is the shape of this manifold; the forward process is about to bury it in noise.",
            ),
            forward(
                100, "x₁₀₀ — {the signal still dominates}.",
                "Each point has moved off the ring by a small random ε. With β rising linearly from 0.0001 to 0.02, ᾱ = Π(1 − β) is still ${abarText(100)} after 100 steps.",
            ),
            forward(
                250, "x₂₅₀ — signal and noise are now about equal.",
                "A quarter of the way through, each point has drifted off the ring by a random ε. By t = 1000, ᾱ = ${n(diffusionAbar[1000], 5)}: nothing of the ring is left.",
            ),
            forward(
                500, "x₅₀₀ — {the ring is gone}.",
                "√ᾱ = ${n(sqrt(diffusionAbar[500]))}: what remains of x₀ is a faint pull toward the centre. The points are mostly ε now.",
            ),
            forward(
                1000, "x₁₀₀₀ — {pure noise}.",
                "x_T is indistinguishable from N(0, I). No learning happened in any of these steps: the forward process is a fixed formula, and any t is one jump from x₀.",
            ),
            forward(
                250, "Training: guess {the ε} that was added.",
                "Pick a random t, noise x₀ in one jump, and ask a network for ε̂(x_t, t). The loss is |ε − ε̂|² — a plain regression with no adversary, which is why training is stable.",
                listOf("loss = |ε − ε̂(x_t, t)|²", "t ~ U(1, 1000) · one jump per example"),
            ),
            reverse(
                1000, "Sampling starts from {fresh noise}.",
                "Draw x_T ~ N(0, I), none of it from the training points. With a perfect noise predictor the deterministic (DDIM) path back is exactly x_t = √ᾱ·x̂₀ + √(1−ᾱ)·ε̂, drawn here.",
            ),
            reverse(
                500, "t = 500: {structure re-emerges}.",
                "Half the steps are undone. The points have pulled in toward the ring's scale, though no single one is on it yet.",
            ),
            reverse(
                250, "t = 250: {the ring is visible again}.",
                "Signal and noise are back in balance. Each step removes a little of the predicted noise, which is why sampling is iterative and slow next to a GAN's single pass.",
            ),
            reverse(
                100, "t = 100: points {sit near the ring}.",
                "Only √(1−ᾱ) = ${n(sqrt(1 - diffusionAbar[100]))} of noise remains. The last steps make small corrections.",
            ),
            reverse(
                0, "t = 0: {every point lands on the ring}.",
                "The reverse process ends on the data manifold — $onRing of $count points within 0.05 of it.",
            ),
            reverse(
                0, "New points on the manifold, {not copies}.",
                "Compare with x₀ in green: the samples sit on the same ring at different angles. The model learned the shape, not the 24 examples.",
                faint = false,
            ).let { DkFrame(it.header, it.stage, it.legend, emptyList(), it.headline, it.body, listOf(DkChip("on the ring", "$onRing / $count", tint = true), DkChip("copies of x₀", "$copies"))) },
            reverse(
                0, "{1,000 network calls} for one sample.",
                "Training was cheap — one jump per example. Sampling runs the denoiser at every step, which is why DDIM (50 steps) and distillation (1–4 steps) exist.",
                faint = false,
            ).let { DkFrame(it.header, it.stage, it.legend, emptyList(), it.headline, it.body, listOf(DkChip("DDPM steps", "1,000", tint = true), DkChip("DDIM", "50"))) },
        ),
    )
}

// ── VAE ──
// A linear VAE (encoder mean W·x, one log-variance per unit, linear decoder) trained here by SGD with the
// reparameterization trick on VaeLab.dataset(): 6 observed dimensions driven by 2 true factors.

private class GenVae(val enc: Array<DoubleArray>, val logVar: DoubleArray, val dec: Array<DoubleArray>, val kl: DoubleArray, val rmse: Double) {
    fun mu(x: DoubleArray) = DoubleArray(enc.size) { j -> x.indices.sumOf { enc[j][it] * x[it] } }
    fun sigma(j: Int) = exp(0.5 * logVar[j])
    fun decode(z: DoubleArray) = DoubleArray(dec.size) { i -> z.indices.sumOf { dec[i][it] * z[it] } }
}

private fun trainGenVae(beta: Double, data: List<DoubleArray>, steps: Int = 30_000, rate: Double = 0.01): GenVae {
    val d = VaeLab.DATA_DIM
    val k = VaeLab.LATENT_DIM
    val rng = GenRng(41)
    val enc = Array(k) { DoubleArray(d) { 0.1 * rng.g() } }
    val dec = Array(d) { DoubleArray(k) { 0.1 * rng.g() } }
    val logVar = DoubleArray(k)
    repeat(steps) {
        val x = data[(rng.u() * data.size).toInt().coerceAtMost(data.size - 1)]
        val mu = DoubleArray(k) { j -> (0 until d).sumOf { enc[j][it] * x[it] } }
        val sigma = DoubleArray(k) { exp(0.5 * logVar[it]) }
        val eps = DoubleArray(k) { rng.g() }
        val z = DoubleArray(k) { mu[it] + sigma[it] * eps[it] }
        val res = DoubleArray(d) { i -> (0 until k).sumOf { dec[i][it] * z[it] } - x[i] }
        val dz = DoubleArray(k) { j -> 2.0 * (0 until d).sumOf { res[it] * dec[it][j] } }
        for (i in 0 until d) for (j in 0 until k) dec[i][j] -= rate * 2.0 * res[i] * z[j]
        for (j in 0 until k) {
            val dMu = dz[j] + beta * mu[j]
            for (i in 0 until d) enc[j][i] -= rate * dMu * x[i]
            logVar[j] = (logVar[j] - rate * (dz[j] * eps[j] * sigma[j] * 0.5 + beta * 0.5 * (exp(logVar[j]) - 1.0))).coerceIn(-12.0, 4.0)
        }
    }
    val muSq = DoubleArray(k)
    var sq = 0.0
    data.forEach { x ->
        val mu = DoubleArray(k) { j -> (0 until d).sumOf { enc[j][it] * x[it] } }
        for (j in 0 until k) muSq[j] += mu[j] * mu[j]
        for (i in 0 until d) {
            val r = (0 until k).sumOf { dec[i][it] * mu[it] } - x[i]
            sq += r * r
        }
    }
    val kl = DoubleArray(k) { j -> 0.5 * (muSq[j] / data.size + exp(logVar[j]) - 1.0 - logVar[j]) }
    return GenVae(enc, logVar, dec, kl, sqrt(sq / (data.size * d)))
}

private fun unitKl(mu: Double, sigma: Double) = 0.5 * (mu * mu + sigma * sigma - 1 - 2 * ln(sigma))

private fun vaeLab(): DkLab = DkLab(DkControl.Track) { _, _ ->
    val data = VaeLab.dataset()
    val one = trainGenVae(1.0, data)
    val four = trainGenVae(4.0, data)
    val x = data[0]
    val rng = GenRng(7)
    val eps1 = DoubleArray(VaeLab.LATENT_DIM) { rng.g() }
    val eps2 = DoubleArray(VaeLab.LATENT_DIM) { rng.g() }
    val prior = DoubleArray(VaeLab.LATENT_DIM) { rng.g() }

    fun z(v: GenVae, eps: DoubleArray): DoubleArray { val m = v.mu(x); return DoubleArray(m.size) { m[it] + v.sigma(it) * eps[it] } }
    fun perX(v: GenVae) = v.mu(x).mapIndexed { j, m -> unitKl(m, v.sigma(j)) }
    fun stage(v: GenVae, eps: DoubleArray?, kl: List<Double>, caption: String): DkVae {
        val m = v.mu(x)
        val zs = eps?.let { z(v, it) }
        return DkVae(
            x.toList(),
            m.indices.map { DkLatent(m[it], v.sigma(it), zs?.get(it)) },
            v.decode(zs ?: m).toList(),
            kl, caption,
        )
    }
    val header = "6 inputs → 4 latent distributions → 6 outputs"
    val klX = perX(one)
    val capX = "KL to the prior, per dimension · total ${n(klX.sum())}"
    val active1 = one.kl.count { it > VaeLab.ACTIVE_THRESHOLD }
    val z1 = z(one, eps1)
    val m1 = one.mu(x)
    // The arithmetic is shown on the unit carrying the most information about this x.
    val u = klX.indices.maxBy { klX[it] }
    val sub = listOf("₁", "₂", "₃", "₄")[u]
    val second1 = one.kl.sortedDescending()[1]
    val second4 = four.kl.sortedDescending()[1]
    val shift = one.decode(z(one, eps1)).zip(one.decode(z(one, eps2))).maxOf { (a, b) -> abs(a - b) }
    fun f(stage: DkVae, formula: List<String>, headline: String, body: String, chips: List<DkChip> = emptyList()) =
        DkFrame(header, stage, emptyList(), formula, headline, body, chips)
    stepActions(
        listOf(
            f(
                stage(one, eps1, klX, capX), emptyList(),
                "The encoder emits {a distribution}, not a point.",
                "Each latent dimension gets a mean and a variance. The decoder sees one sample z from it; KL measures how far each one strays from the prior.",
            ),
            f(
                stage(one, eps1, klX, capX),
                listOf("z = μ + σ·ε, ε ~ N(0, 1)", "z$sub = ${n(m1[u])} + ${n(one.sigma(u))}·(${n(eps1[u])}) = {${n(z1[u])}}"),
                "Sampling moves into an input: {z = μ + σ·ε}.",
                "Sampling z directly can't be differentiated. Drawing ε from a fixed N(0, 1) and computing z from μ and σ can — gradients reach μ and σ, and the randomness is just another input.",
            ),
            f(
                stage(one, eps2, klX, capX), emptyList(),
                "Same x, {a new z} every pass.",
                "A fresh ε moves every yellow dot, and the reconstruction shifts with it — by up to ${n(shift)} on one output. Training averages over these draws, so nearby codes must decode to similar outputs.",
                listOf(DkChip("max |Δx̂|", n(shift), tint = true)),
            ),
            f(
                stage(one, eps2, klX, capX),
                listOf("KL = ½(μ² + σ² − 1 − ln σ²)", "z$sub: ½(${n(m1[u])}² + ${n(one.sigma(u))}² − 1 − ln ${n(one.sigma(u))}²) = {${n(klX[u])}}"),
                "KL pulls each posterior {toward N(0, 1)}.",
                "It is zero only at μ = 0, σ = 1. The loss is reconstruction error plus this total, ${n(klX.sum())} here, so every unit pays for the information it carries.",
            ),
            f(
                stage(one, null, one.kl.toList(), "KL per dimension, averaged over the data · β = 1"), emptyList(),
                "β = 1 keeps {$active1 of 4} units.",
                "The data has 2 underlying factors. A unit that doesn't help reconstruction is cheapest at exactly the prior, μ = 0 and σ = 1 — KL zero — so the decoder learns to ignore it.",
                listOf(DkChip("active units", "$active1 / 4", tint = true), DkChip("RMSE", n(one.rmse, 3))),
            ),
            f(
                stage(four, null, four.kl.toList(), "KL per dimension, averaged over the data · β = 4"), emptyList(),
                "β = 4 {squeezes out} a real factor.",
                "Weight the KL four times as heavily and the weaker factor gets too expensive to carry: its unit falls from ${n(second1)} to ${n(second4)} nats. Reconstruction error rises from ${n(one.rmse, 3)} to ${n(four.rmse, 3)}.",
                listOf(DkChip("weaker unit", n(second4) + " nats", tint = true), DkChip("RMSE", n(four.rmse, 3))),
            ),
            f(
                DkVae(
                    null,
                    prior.map { DkLatent(0.0, 1.0, it) },
                    one.decode(prior).toList(),
                    one.kl.toList(), "KL per dimension, averaged over the data · β = 1",
                ),
                listOf("z ~ N(0, 1) · x̂ = decoder(z)"),
                "Generate: {sample the prior}, then decode.",
                "Skip the encoder. Because KL kept every posterior close to N(0, 1), a z drawn from the prior lands where the decoder has seen codes before, and decodes to a plausible new point.",
            ),
        ),
    )
}

// ── CycleGAN ──
// Six items per domain; a mapping is a permutation. The counts come from CycleGanLab's enumeration.

private fun cycleLab(): DkLab = DkLab(DkControl.Track) { _, _ ->
    val nItems = 6
    val identity = (0 until nItems).toList()
    val shuffled = listOf(3, 5, 0, 4, 1, 2)
    val neighbours = listOf(1, 0, 3, 2, 5, 4)
    val partial = listOf(2, 1, 0, 5, 4, 3)
    val reversed = identity.reversed()
    val total = CycleGanLab.adversariallyOptimal(nItems)
    val survive = CycleGanLab.cycleConsistent(nItems)
    val local1 = CycleGanLab.withLocality(nItems, 1)
    val local2 = CycleGanLab.withLocality(nItems, 2)
    fun right(p: List<Int>, target: List<Int> = identity) = p.indices.count { p[it] == target[it] }
    fun shift(p: List<Int>) = p.indices.maxOf { abs(p[it] - it) }
    fun rightStat(p: List<Int>, target: List<Int> = identity): DkCycleStat {
        val r = right(p, target)
        return DkCycleStat("pairs right", "$r / $nItems", if (r == nItems) DkInk.Green else if (r == 0) DkInk.Pink else null)
    }
    val adv = DkCycleStat("adversarial", "0")
    val cyc = DkCycleStat("cycle loss", "0")
    fun intended(vararg stats: DkCycleStat, inverse: Boolean = false) =
        DkCyclePanel("Intended G", DkInk.Green, identity, DkInk.Green, false, inverse, stats.toList())
    fun wrong(title: String, p: List<Int>, vararg stats: DkCycleStat, inverse: Boolean = false) =
        DkCyclePanel(title, DkInk.Pink, p, DkInk.Pink, true, inverse, stats.toList())
    val header = "G : A → B · two mappings, same output set"
    val base = listOf(legend(DkInk.Cyan, "Domain A"), legend(DkInk.Orange, "Domain B"))
    val both = base + listOf(legend(DkInk.Green, "Correct mapping", SwatchStyle.Line), legend(DkInk.Pink, "Also zero loss", SwatchStyle.DashedLine))
    val withF = both + legend(DkInk.Violet, "Inverse F", SwatchStyle.DashedLine)
    stepActions(
        listOf(
            DkFrame(
                "A and B · six items each, unpaired",
                DkCycle(listOf(DkCyclePanel("", DkInk.Cyan, null, DkInk.Cyan, false))), base, emptyList(),
                "Two domains, {no pairs}.",
                "Six items in A, six in B, and nothing saying which goes with which. CycleGAN has to learn G : A → B from the two sets alone.",
                listOf(DkChip("items per domain", "$nItems"), DkChip("paired examples", "0", tint = true)),
            ),
            DkFrame(
                header,
                DkCycle(listOf(intended(adv, rightStat(identity)), wrong("Shuffled G", shuffled, adv, rightStat(shuffled)))), both, emptyList(),
                "This is the mapping we want — and so is this one, to the discriminator.",
                "Both send A onto exactly the set B, so the output distribution matches and adversarial loss is zero either way. Only a cycle-consistency term can tell them apart.",
            ),
            DkFrame(
                header,
                DkCycle(listOf(wrong("Another G", neighbours, adv, rightStat(neighbours)), wrong("And another", partial, adv, rightStat(partial)))), both, emptyList(),
                "Every one of the {$total} bijections scores zero.",
                "Any one-to-one map sends A onto exactly the set B, so the output distribution always matches. 6! = $total mappings tie at zero adversarial loss, and one of them is right.",
                listOf(DkChip("zero-loss mappings", "$total", tint = true), DkChip("correct", "1")),
            ),
            DkFrame(
                header,
                DkCycle(listOf(intended(cyc, inverse = true), wrong("Shuffled G", shuffled, cyc, inverse = true))), withF,
                listOf("cycle loss = |F(G(a)) − a| + |G(F(b)) − b|"),
                "Add {F : B → A} and require F(G(a)) = a.",
                "Cycle consistency forces F to undo G — drawn dashed in violet. It is the term usually credited with resolving the ambiguity.",
            ),
            DkFrame(
                header,
                DkCycle(listOf(intended(adv, cyc, rightStat(identity), inverse = true), wrong("Shuffled G", shuffled, adv, cyc, rightStat(shuffled), inverse = true))), withF,
                emptyList(),
                "Every bijection has an inverse: {$survive of $total} survive.",
                "The shuffled G is undone by its own F just as perfectly. The cycle term rules out many-to-one collapse, which the distribution match had already excluded — it removes no bijection here.",
                listOf(DkChip("survive both losses", "$survive / $total", tint = true)),
            ),
            DkFrame(
                header,
                DkCycle(
                    listOf(
                        intended(DkCycleStat("max shift", "${shift(identity)}", DkInk.Green), rightStat(identity)),
                        wrong("Shuffled G", shuffled, DkCycleStat("max shift", "${shift(shuffled)}", DkInk.Pink), rightStat(shuffled)),
                    ),
                ),
                both, emptyList(),
                "A conv net can only {move things a little}.",
                "Its receptive field is far smaller than the image, so it can't express an arbitrary rearrangement. Allowing each item to move at most one place cuts $total candidates to $local1.",
                listOf(DkChip("shift ≤ 1", "$local1", tint = true), DkChip("shift ≤ 2", "$local2")),
            ),
            DkFrame(
                header,
                DkCycle(
                    listOf(
                        intended(DkCycleStat("max shift", "0", DkInk.Green), rightStat(identity)),
                        wrong("Swap neighbours", neighbours, DkCycleStat("max shift", "${shift(neighbours)}"), rightStat(neighbours)),
                    ),
                ),
                both, emptyList(),
                "Locality narrows the field; it doesn't {pin the answer}.",
                "Swapping neighbours is also local and also zero-loss. What favours the identity in practice is that \"keep the layout, change the texture\" is the easiest map for a small conv net to learn.",
                listOf(DkChip("shift ≤ 1", "$local1", tint = true)),
            ),
            DkFrame(
                "cat → dog · the mapping needs things to move",
                DkCycle(
                    listOf(
                        DkCyclePanel(
                            "Needed G", DkInk.Green, reversed, DkInk.Green, false,
                            stats = listOf(DkCycleStat("max shift", "${shift(reversed)}", DkInk.Pink), DkCycleStat("expressible", "no", DkInk.Pink)),
                        ),
                        wrong("Learned G", identity, DkCycleStat("max shift", "0"), rightStat(identity, reversed)),
                    ),
                ),
                both, emptyList(),
                "When the right map {moves things far}, CycleGAN fails.",
                "Horse → zebra keeps the layout, so the local map is the right one. Cat → dog needs shapes to move; that mapping is outside what the generator can express, and it falls back to retexturing.",
            ),
        ),
    )
}

// ── DCGAN ──
// Writes per output position of a transposed convolution, from DcganLab.coverage.

private fun covRow(k: Int, s: Int, inputs: Int, selected: Boolean, label: String = "k = $k, s = $s"): DkCovRow {
    val counts = DcganLab.coverage(k, s, inputs).toList()
    val border = k - 1
    val interior = counts.subList(border, counts.size - border).toSortedSet().toList()
    val uniform = interior.size == 1
    return DkCovRow(
        label,
        if (uniform) "flat" else "checkerboard " + interior.joinToString(" / "),
        if (uniform) DkInk.Green else DkInk.Orange,
        counts, border, uniform, selected,
    )
}

private fun interiorOf(k: Int, s: Int, inputs: Int = 8): List<Int> {
    val counts = DcganLab.coverage(k, s, inputs).toList()
    return counts.subList(k - 1, counts.size - (k - 1)).toSortedSet().toList()
}

private fun dcganLab(): DkLab = DkLab(DkControl.Track) { _, _ ->
    val legend = listOf(
        legend(DkInk.Green, "Uniform coverage"),
        legend(DkInk.Orange, "Uneven — checkerboard"),
        legend(DkInk.Slate, "Border (fewer writes)"),
    )
    val h2 = "writes per output position · 8 inputs, stride 2"
    fun cov(header: String, rows: List<DkCovRow>, formula: List<String>, headline: String, body: String, chips: List<DkChip>) =
        DkFrame(header, DkCoverage(rows, rows.maxOf { r -> r.counts.max() }), legend, formula, headline, body, chips)
    fun chips(k: Int, s: Int, inputs: Int = 8) = listOf(
        DkChip("k mod s", "${k % s}", tint = true),
        DkChip("interior counts", interiorOf(k, s, inputs).joinToString(", ", "[", "]")),
    )
    val layers = DcganLab.generator
    val total = DcganLab.generatorParameters
    val shares = layers.map { it.parameters.toDouble() / total }
    val biggest = shares.indices.maxBy { shares[it] }
    stepActions(
        listOf(
            cov(
                h2, listOf(covRow(4, 2, 8, true)),
                listOf("O = (I − 1)·s + k = (8 − 1)·2 + 4 = {18}"),
                "A transposed conv {stamps each input into a window}.",
                "Each of the 8 inputs is written into a kernel-sized window of the output, the windows stride apart. The bars count how many writes land on each position — before any weights exist.",
                chips(4, 2),
            ),
            cov(
                h2, listOf(covRow(3, 2, 8, false), covRow(4, 2, 8, true), covRow(5, 2, 8, false)), emptyList(),
                "DCGAN's choice: kernel 4, stride 2.",
                "Every interior position is written exactly twice. With k = 3 or 5 the kernel doesn't divide by the stride, so counts alternate and the image picks up a checkerboard.",
                chips(4, 2),
            ),
            cov(
                h2, listOf(covRow(3, 2, 8, true), covRow(4, 2, 8, false), covRow(5, 2, 8, false)), emptyList(),
                "Kernel 3: counts alternate {${interiorOf(3, 2).joinToString(", ")}}.",
                "Every other position gets half the writes of its neighbour. Whatever the weights learn, that pattern repeats across the image — the checkerboard artefact.",
                chips(3, 2),
            ),
            cov(
                h2, listOf(covRow(3, 2, 8, false), covRow(4, 2, 8, false), covRow(5, 2, 8, true)), emptyList(),
                "Kernel 5: {${interiorOf(5, 2).joinToString(", ")}} — gentler, same period.",
                "The ratio is 2 : 3 instead of 1 : 2, so the artefact is a faint weave rather than a hard grid. Its period is still the stride.",
                chips(5, 2),
            ),
            cov(
                "writes per output position · 8 inputs, stride 3",
                listOf(covRow(4, 3, 8, false), covRow(5, 3, 8, false), covRow(6, 3, 8, true)), emptyList(),
                "The rule: uniform exactly when {s divides k}.",
                "At stride 3, kernels 4 and 5 still mix ${interiorOf(4, 3).joinToString(" and ")} writes; kernel 6 is flat at ${interiorOf(6, 3).single()}. Training can rescale each write but never change how many arrive.",
                chips(6, 3),
            ),
            cov(
                "writes per output position · 16 inputs after nearest ×2",
                listOf(covRow(3, 1, 16, true, "resize ×2, then k = 3, s = 1"), covRow(3, 2, 8, false)), emptyList(),
                "The usual fix: {resize, then convolve}.",
                "Upsample by nearest neighbour and apply an ordinary stride-1 convolution. Every interior position reads exactly 3 values, so the overlap is even by construction (Odena et al., 2016).",
                listOf(DkChip("interior counts", interiorOf(3, 1, 16).joinToString(", ", "[", "]"), tint = true)),
            ),
            DkFrame(
                "DCGAN generator · ${grouped(total)} parameters",
                DkRows(
                    layers.mapIndexed { i, l ->
                        DkRow(
                            l.name.replace("->", "→"), grouped(l.parameters),
                            listOf(DkBar(shares[i], DkInk.Violet, n(shares[i] * 100, if (shares[i] < 0.01) 2 else 0) + "%")),
                            hot = i == biggest,
                        )
                    },
                ),
                listOf(legend(DkInk.Violet, "Share of parameters")), emptyList(),
                "{${n(shares[biggest] * 100, 0)}%} of the generator is one layer.",
                "Capacity follows channels, not pixels: the 1024 → 512 transposed conv at 8×8 dominates, and the layer that actually emits the 64×64×3 image holds ${n(shares.last() * 100, 2)}% of the weights.",
            ),
        ),
    )
}

// ── Stable Diffusion ──
// LatentDiffusionLab's counts on a log scale shared by every bar.

private fun stableLab(): DkLab = DkLab(DkControl.Track) { _, _ ->
    val L = LatentDiffusionLab
    val px = L.pixelElements.toDouble()
    val lat = L.latentElements.toDouble()
    val pt = L.pixelTokens.toDouble()
    val lt = L.latentTokens.toDouble()
    val pp = L.pixelAttentionPairs.toDouble()
    val lp = L.latentAttentionPairs.toDouble()
    val pc = L.pixelCrossAttentionPairs.toDouble()
    val lc = L.latentCrossAttentionPairs.toDouble()
    val top = log10(L.pixelSamplingPairs)
    fun fr(v: Double) = log10(v) / top
    fun row(name: String, formula: String, a: Double, b: Double, hot: Boolean = false) = DkShrinkRow(name, formula, big(a), fr(a), big(b), fr(b), hot)
    fun ratio(v: Double) = grouped(Math.round(v)) + "×"
    val header = "pixel space vs latent space · log scale"
    val left = "pixel 512 × 512"
    val right = "latent 64 × 64"
    val values = row("Values", "512·512·3 → 64·64·4", px, lat)
    val tokens = row("Tokens", "512² → 64²", pt, lt)
    val parts = L.components
    val unet = parts.last().second
    stepActions(
        listOf(
            DkFrame(
                header, DkShrink(left, right, listOf(values), listOf(DkShrinkTile(ratio(L.elementRatio), "fewer values", hot = true))), emptyList(),
                listOf("512·512·3 = {${grouped(L.pixelElements)}}", "64·64·4 = {${grouped(L.latentElements)}}"),
                "Stable Diffusion denoises a {64 × 64 × 4} latent.",
                "An autoencoder squeezes the 512 × 512 image 8× per side into 4 channels before diffusion starts, so every denoising step works on ${ratio(L.elementRatio)} fewer numbers.",
            ),
            DkFrame(
                header,
                DkShrink(
                    left, right,
                    listOf(values, tokens, row("Attention pairs", "N² → N²", pp, lp, hot = true)),
                    listOf(
                        DkShrinkTile(ratio(L.elementRatio), "fewer values"),
                        DkShrinkTile(ratio(pt / lt), "fewer tokens"),
                        DkShrinkTile(ratio(L.attentionRatio), "fewer attention pairs", hot = true),
                    ),
                ),
                emptyList(),
                listOf("pairs: ${grouped(L.pixelTokens)}² = {${grouped(L.pixelAttentionPairs)}}", "pairs: ${grouped(L.latentTokens)}² = {${grouped(L.latentAttentionPairs)}}"),
                "Attention saves far more than the latent shrink.",
                "${ratio(pt / lt)} fewer tokens means 64² = ${ratio(L.attentionRatio)} fewer self-attention pairs, because every token is compared with every other.",
            ),
            DkFrame(
                header,
                DkShrink(
                    left, right,
                    listOf(row("Self-attention", "N² → N²", pp, lp), row("Cross-attention", "N·77 → N·77", pc, lc, hot = true)),
                    listOf(DkShrinkTile(ratio(L.attentionRatio), "self-attention"), DkShrinkTile(ratio(L.crossAttentionRatio), "cross-attention", hot = true)),
                ),
                emptyList(),
                listOf("cross: ${grouped(L.pixelTokens)} × 77 = {${grouped(L.pixelCrossAttentionPairs)}}", "cross: ${grouped(L.latentTokens)} × 77 = {${grouped(L.latentCrossAttentionPairs)}}"),
                "Cross-attention saves {only ${ratio(L.crossAttentionRatio)}}.",
                "It compares image tokens with 77 text tokens, so it is linear in N and shrinks only as fast as the token count. The ${ratio(L.attentionRatio)} belongs to self-attention alone.",
            ),
            DkFrame(
                header,
                DkShrink(
                    "pixel · DDPM ${grouped(L.DDPM_STEPS.toLong())} steps", "latent · DDIM ${L.DDIM_STEPS} steps",
                    listOf(row("Attention pairs per image", "N²·steps", L.pixelSamplingPairs, L.latentSamplingPairs, hot = true)),
                    listOf(
                        DkShrinkTile(ratio(L.attentionRatio), "per step"),
                        DkShrinkTile(ratio(L.stepSaving), "fewer steps"),
                        DkShrinkTile(ratio(L.samplingRatio), "less work per image", hot = true),
                    ),
                ),
                emptyList(), emptyList(),
                "Add a 50-step sampler: {${ratio(L.samplingRatio)}} less attention work.",
                "DDPM runs the network ${grouped(L.DDPM_STEPS.toLong())} times; DDIM gets comparable images in ${L.DDIM_STEPS}. Together with the latent, that is the gap between a cluster and a consumer GPU.",
            ),
            DkFrame(
                "the three networks in one checkpoint",
                DkShrink(
                    "parameters", "runs per image",
                    parts.mapIndexed { i, (name, p) ->
                        val runs = if (i == parts.lastIndex) L.DDIM_STEPS else 1
                        DkShrinkRow(name.substringBefore(" ("), "", "${p}M", p.toDouble() / unet, "×$runs", runs.toDouble() / L.DDIM_STEPS, hot = i == parts.lastIndex)
                    },
                    listOf(
                        DkShrinkTile("${grouped(L.totalParametersMillions.toLong())}M", "parameters in all"),
                        DkShrinkTile(n(L.unetShare * 100, 0) + "%", "in the UNet"),
                        DkShrinkTile("${L.DDIM_STEPS}×", "UNet runs per image", hot = true),
                    ),
                ),
                emptyList(), emptyList(),
                "Only the UNet {runs every step}.",
                "The VAE and the text encoder run once each, at the two ends. The UNet — ${n(L.unetShare * 100, 0)}% of the ${grouped(L.totalParametersMillions.toLong())}M parameters — runs ${L.DDIM_STEPS} times, twice per step with classifier-free guidance.",
            ),
            DkFrame(
                "one latent cell against the pixels it covers",
                DkShrink(
                    left, right,
                    listOf(row("Numbers per 8 × 8 patch", "8·8·3 → 4", 192.0, 4.0, hot = true)),
                    listOf(
                        DkShrinkTile("${L.DOWNSAMPLE} px", "per cell, each side"),
                        DkShrinkTile("${L.LATENT_CHANNELS}", "numbers to hold it"),
                        DkShrinkTile("48×", "squeeze", hot = true),
                    ),
                ),
                emptyList(), listOf("f = ${L.DOWNSAMPLE} · ${L.LATENT_CHANNELS} channels · 192 → {4}"),
                "Detail lost at encode time {never comes back}.",
                "Each latent cell summarises an 8 × 8 patch in 4 numbers. Small faces, hands and text that don't fit are gone before the first denoising step, and no number of sampling steps recovers them.",
            ),
        ),
    )
}

// ── DeepFakes ──
// DeepFakeLab: a shared or per-identity linear encoder, a least-squares decoder per identity, and the
// swap scored against B's real faces.

private fun deepFakeLab(): DkLab = DkLab(DkControl.Track) { _, _ ->
    val sweep = DeepFakeLab.sweepResults
    val aligned = sweep.first()
    val own = DeepFakeLab.ownDomainRmse()
    val header = "one encoder, two decoders"
    val legend = listOf(
        legend(DkInk.Blue, "Training on A", SwatchStyle.Line),
        legend(DkInk.Violet, "Training on B", SwatchStyle.Line),
        legend(DkInk.Yellow, "Swap at inference", SwatchStyle.DashedLine),
    )
    val formula = listOf("train: D_A(E(x_A)) ≈ x_A, D_B(E(x_B)) ≈ x_B", "swap: {D_B(E(x_A))} → B's face, A's pose")
    val finite = sweep.filter { it.sharedEncoderRmse.isFinite() && it.sharedEncoderRmse < aligned.meanFaceBaselineRmse * 4 }
    val crossing = finite.firstOrNull { !it.sharedBeatsBaseline }
    val hi = max(finite.maxOf { it.sharedEncoderRmse }, finite.maxOf { it.meanFaceBaselineRmse }) * 1.1
    fun plot(guide: Pair<Double, String>?) = DkPlot(
        0.0 to 90.0, 0.0 to hi,
        listOf(0.0 to "0", hi / 2 to n(hi / 2), hi to n(hi)), "0°", "90°",
        listOf(
            DkLine(finite.map { DkP(it.degrees, it.sharedEncoderRmse) }, DkInk.Blue, dots = true),
            DkLine(sweep.map { DkP(it.degrees, it.meanFaceBaselineRmse) }, DkInk.Grey, dashed = true),
        ),
        axis = false, guide = guide,
        xTicks = sweep.map { DkTick(it.degrees, "${it.degrees.toInt()}°") },
    )
    val plotLegend = listOf(legend(DkInk.Blue, "Shared encoder", SwatchStyle.Line), legend(DkInk.Grey, "Mean-face baseline", SwatchStyle.DashedLine))
    val plotHeader = "swap error against the angle between the two faces' expression spaces"
    val indLoses = !aligned.independentBeatsBaseline
    stepActions(
        listOf(
            DkFrame(
                header, DkFakeArch(shared = true, trainA = true, trainB = true, swap = true), legend, formula,
                "One shared encoder and two identity-specific decoders.",
                "Both identities go through the same trunk, so E learns pose and expression. The swap is breaking the pairing at inference: A's code into B's decoder.",
            ),
            DkFrame(
                header, DkFakeArch(shared = true, trainA = false, trainB = false, swap = true), legend.drop(2), emptyList(),
                "The bar to clear: {B's average face}.",
                "Nothing in the loss says z should hold expression and not identity. So score the swap against a model that ignores its input and always outputs B's mean face: error ${n(aligned.meanFaceBaselineRmse, 3)}. The shared encoder gets ${n(aligned.sharedEncoderRmse, 3)}.",
                listOf(DkChip("shared swap", n(aligned.sharedEncoderRmse, 3), tint = true), DkChip("mean face", n(aligned.meanFaceBaselineRmse, 3))),
            ),
            DkFrame(
                "one encoder per identity", DkFakeArch(shared = false, trainA = true, trainB = true, swap = true), legend, emptyList(),
                if (indLoses) "Give each face its own encoder and the swap {loses to doing nothing}."
                else "Give each face its own encoder and the swap {gets worse}.",
                "Each encoder orders and scales its directions by its own identity's variance, so the same expression lands on different numbers and B's decoder reads a different expression: error ${n(aligned.independentEncoderRmse, 3)} against ${n(aligned.meanFaceBaselineRmse, 3)} for the mean face.",
                listOf(DkChip("independent swap", n(aligned.independentEncoderRmse, 3), tint = true), DkChip("mean face", n(aligned.meanFaceBaselineRmse, 3))),
            ),
            DkFrame(
                header, DkFakeArch(shared = true, trainA = true, trainB = true, swap = false), legend.take(2), emptyList(),
                "Both rebuild their own faces {almost perfectly}.",
                "Own-domain error is ${n(own, 3)} either way. A model can reconstruct perfectly and still swap worse than a constant, so reconstruction quality says nothing about the swap.",
                listOf(DkChip("own-domain error", n(own, 3), tint = true)),
            ),
            DkFrame(
                plotHeader, plot(null), plotLegend, emptyList(),
                "Sharing works only if {the two faces move alike}.",
                "Rotate B's expression space away from A's and the shared code means less to B's decoder: " +
                    finite.joinToString(" · ") { "${it.degrees.toInt()}° ${n(it.sharedEncoderRmse, 3)}" } + ".",
            ),
            DkFrame(
                plotHeader, plot(crossing?.let { it.degrees to "${it.degrees.toInt()}°" }), plotLegend, emptyList(),
                if (crossing != null) "By {${crossing.degrees.toInt()}°} the swap is worse than the mean face."
                else "The swap {degrades steadily} with the angle.",
                "No amount of training fixes this: the information B's decoder needs is not in the code. It is the measurable form of what practitioners report — swaps work between people who already look and move alike.",
            ),
        ),
    )
}

// ── StyleGAN ──
// A smooth warp G of the unit square stands in for "what the data needs"; path lengths are measured on
// the drawn paths, and the averaged figures come from StyleGanLab.pathLengths().

private fun warp(u: Double, v: Double) = DkP(u + 0.07 * sin(2 * PI * v), v - 0.18 * sin(2 * PI * u))

private fun pathLength(pts: List<DkP>) = pts.zipWithNext().sumOf { (a, b) -> hypot(b.x - a.x, b.y - a.y) }

private fun styleGanLab(): DkLab = DkLab(DkControl.Track) { _, _ ->
    val side = 9
    val grid = (0 until side).flatMap { i -> (0 until side).map { j -> DkP(i / (side - 1.0), j / (side - 1.0)) } }
    val warped = grid.map { warp(it.x, it.y) }
    val a = DkP(0.1, 0.15)
    val b = DkP(0.9, 0.75)
    val steps = 64
    val zLine = (0..steps).map { s -> val t = s.toDouble() / steps; DkP(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t) }
    val gPath = zLine.map { warp(it.x, it.y) }
    val wa = warp(a.x, a.y)
    val wb = warp(b.x, b.y)
    val wLine = (0..steps).map { s -> val t = s.toDouble() / steps; DkP(wa.x + (wb.x - wa.x) * t, wa.y + (wb.y - wa.y) * t) }
    val stretch = pathLength(gPath) / pathLength(zLine)
    val viaW = pathLength(gPath) / pathLength(wLine)
    val lengths = StyleGanLab.pathLengths()
    val cx = warped.sumOf { it.x } / warped.size
    val cy = warped.sumOf { it.y } / warped.size
    val psi = 0.7
    val truncated = warped.map { DkP(cx + psi * (it.x - cx), cy + psi * (it.y - cy)) }
    val header = "z ~ uniform on the square · 81 samples"
    val zLegend = listOf(legend(DkInk.Cyan, "Latent Z"), legend(DkInk.Green, "Straight path in Z", SwatchStyle.Line), legend(DkInk.Orange, "Same path after G", SwatchStyle.Line))
    val wLegend = listOf(legend(DkInk.Cyan, "Samples"), legend(DkInk.Orange, "Path via Z", SwatchStyle.Line), legend(DkInk.Green, "Straight in W", SwatchStyle.Line))
    val gridChip = DkChip("grid", "$side × $side")
    stepActions(
        listOf(
            DkFrame(
                header, DkWarp(DkWarpPanel("Z (prior)", grid), DkWarpPanel("G(Z) (what data needs)", warped)),
                listOf(legend(DkInk.Cyan, "Latent Z")), emptyList(),
                "The prior is {a fixed square}.",
                "z is drawn uniformly from the square — 81 samples on a 9 × 9 grid here. The data's attributes don't fill a square, so the generator has to bend it into the shape on the right.",
                listOf(gridChip),
            ),
            DkFrame(
                header, DkWarp(DkWarpPanel("Z (prior)", grid, zLine, DkInk.Green), DkWarpPanel("G(Z) (what data needs)", warped, gPath, DkInk.Orange)),
                zLegend, emptyList(),
                "A fixed prior forces the generator to warp.",
                "Z is a square and can't change shape, so G must bend it to fit the data. A straight line in Z comes out curved and ${n(stretch)}× longer — attributes change unevenly along it. StyleGAN's mapping to W exists to undo this.",
                listOf(DkChip("path length", "×${n(stretch)}", tint = true), gridChip),
            ),
            DkFrame(
                "the same two endpoints, interpolated two ways",
                DkWarp(DkWarpPanel("via Z: curved", warped, gPath, DkInk.Orange), DkWarpPanel("via W: straight", warped, wLine, DkInk.Green)),
                wLegend, emptyList(),
                "A mapping network lets the path {go straight}.",
                "StyleGAN first maps z to w with an 8-layer MLP. W isn't tied to a fixed shape, so it can take the data's shape itself, and a straight line in W crosses it evenly. Between the same endpoints the path through Z is ${n(viaW)}× longer.",
                listOf(DkChip("via Z", "×${n(viaW)}", tint = true), DkChip("via W", "×1.00")),
            ),
            DkFrame(
                "the same two endpoints, interpolated two ways",
                DkWarp(DkWarpPanel("via Z: curved", warped, gPath, DkInk.Orange), DkWarpPanel("via W: straight", warped, wLine, DkInk.Green)),
                wLegend,
                listOf("path length via Z = {${n(lengths.latentZ, 3)}}", "path length via W = {${n(lengths.latentW, 3)}}"),
                "Averaged over 40,000 paths: {${n(lengths.ratio, 1)}×} shorter in W.",
                "Measured on a toy generator whose data is missing one attribute combination, the squared path length through Z is ${n(lengths.ratio, 1)}× that through W. Shorter paths mean attributes change smoothly — the disentanglement StyleGAN reports.",
            ),
            DkFrame(
                "w pulled toward the average w̄",
                DkWarp(DkWarpPanel("W", warped), DkWarpPanel("truncated, ψ = ${n(psi, 1)}", truncated)),
                listOf(legend(DkInk.Cyan, "Samples in W")),
                listOf("w′ = w̄ + ψ·(w − w̄)"),
                "Truncation trades variety for quality: {ψ = ${n(psi, 1)}}.",
                "Samples far from the average w are the ones the generator saw least. Pulling every w ${n((1 - psi) * 100, 0)}% toward w̄ shrinks the spread to ${n(psi * 100, 0)}% — fewer odd outputs, less diversity.",
                listOf(DkChip("spread", "×${n(psi)}", tint = true)),
            ),
            DkFrame(
                "w reaches every layer as a style",
                DkWarp(DkWarpPanel("Z (prior)", grid), DkWarpPanel("W", warped, wLine, DkInk.Green)),
                listOf(legend(DkInk.Cyan, "Samples"), legend(DkInk.Green, "Straight in W", SwatchStyle.Line)),
                listOf("AdaIN(x, w) = σ_w·(x − μ(x))/σ(x) + μ_w"),
                "w is fed to every layer as {a style}.",
                "AdaIN rescales each layer's features with w: the 4–8 px layers set pose and shape, 16–32 px set features, 64–1024 px set colour and texture. Mixing two w's across layers mixes those attributes.",
                listOf(
                    DkChip("coarse", "${StyleGanLab.styleInputsIn("coarse")}", tint = true),
                    DkChip("middle", "${StyleGanLab.styleInputsIn("middle")}"),
                    DkChip("fine", "${StyleGanLab.styleInputsIn("fine")}"),
                ),
            ),
        ),
    )
}

// ── Neural style transfer ──
// StyleTransferLab's 6 × 10 feature map, the same map with its positions shuffled, and both Gram matrices.

private fun styleTransferLab(): DkLab = DkLab(DkControl.Track) { _, _ ->
    val f = StyleTransferLab.featureMap(channels = 6, positions = 10)
    val p = StyleTransferLab.shuffleColumns(f)
    val g = StyleTransferLab.gram(f)
    val gp = StyleTransferLab.gram(p)
    fun rows(m: Array<DoubleArray>) = m.map { it.toList() }
    val moved = (0 until 10).first { j -> (0 until 6).all { p[it][j] == f[it][0] } }
    var content = 0.0
    for (i in 0 until 6) for (j in 0 until 10) content += (f[i][j] - p[i][j]) * (f[i][j] - p[i][j])
    var gramDelta = 0.0
    for (i in 0 until 6) for (j in 0 until 6) gramDelta = max(gramDelta, abs(g[i][j] - gp[i][j]))
    val hi = 2
    val hj = 3
    val entry = (0 until 10).sumOf { f[hi][it] * f[hj][it] } / 10
    val header = "6 channels × 10 positions · then G = F Fᵀ / 10"
    val legend = listOf(legend(DkInk.Blue, "Feature value"), legend(DkInk.Violet, "Gram entry"))
    val tracked = legend + legend(DkInk.Yellow, "Column 1, tracked", SwatchStyle.Ring)
    fun four(track: Boolean) = DkGram(
        listOf(
            listOf(DkHeat("F", rows(f), DkInk.Blue, false, if (track) 0 else null), DkHeat("Gram of F", rows(g), DkInk.Violet, true)),
            listOf(DkHeat("F, columns permuted", rows(p), DkInk.Blue, false, if (track) moved else null), DkHeat("Gram of permuted F", rows(gp), DkInk.Violet, true)),
        ),
    )
    val deltas = listOf(DkChip("content Δ", n(content)), DkChip("Gram Δ", n(gramDelta, 3), tint = true))
    stepActions(
        listOf(
            DkFrame(
                header,
                DkGram(listOf(listOf(DkHeat("F", rows(f), DkInk.Blue, false), DkHeat("Gram of F", rows(g), DkInk.Violet, true)))),
                legend, emptyList(),
                "Style transfer starts from {a feature map}.",
                "VGG is frozen; the image's pixels are what get optimized. This is one layer's output — 6 channels at 10 positions — and its Gram matrix: how strongly each pair of channels fires together.",
                listOf(DkChip("channels", "6"), DkChip("positions", "10")),
            ),
            DkFrame(
                header, four(true), tracked, emptyList(),
                "Shuffle every position — the style stays put.",
                "Every value has moved, so content loss sees a different picture. The Gram matrix only sums over positions, so it is unchanged: that is why it captures texture, not layout.",
                deltas,
            ),
            DkFrame(
                header,
                DkGram(
                    listOf(
                        listOf(
                            DkHeat("F", rows(f), DkInk.Blue, false, hotRows = hi to hj),
                            DkHeat("Gram of F", rows(g), DkInk.Violet, true, hotCell = hi to hj),
                        ),
                    ),
                ),
                legend, listOf("G₃₄ = Σₚ F₃ₚ·F₄ₚ / 10 = {${n(entry)}}"),
                "Each Gram entry {sums over positions}.",
                "Entry (3, 4) multiplies channel 3 by channel 4 at every position and adds them up. Position only indexes the sum, so reordering positions can't change it.",
            ),
            DkFrame(
                header, four(false), legend,
                listOf("content = Σ (F − F′)² = {${n(content)}}", "style = max |G − G′| = {${n(gramDelta, 3)}}"),
                "Content loss sees {layout}; style loss can't.",
                "That is the division of labour: the content term compares feature maps position by position, the style term compares only which features co-occur.",
                deltas,
            ),
            DkFrame(
                header, four(false), legend,
                listOf("L = α·content(x, photo) + β·style(x, painting)", "∂L/∂x → update the pixels of x"),
                "The image is optimized to {match both}.",
                "Start from the photo and take gradient steps on its pixels: keep the photo's feature map at ${StyleTransferLab.CONTENT_LAYER}, match the painting's Gram matrices at ${StyleTransferLab.styleLayers.first()} … ${StyleTransferLab.styleLayers.last()}.",
            ),
            DkFrame(
                header, four(false), legend,
                listOf(
                    "conv4_1: ${StyleTransferLab.VGG_CHANNELS} × ${StyleTransferLab.VGG_SIDE} × ${StyleTransferLab.VGG_SIDE} = {${grouped(StyleTransferLab.featureValues)}} values",
                    "Gram: ${StyleTransferLab.VGG_CHANNELS}·${StyleTransferLab.VGG_CHANNELS + 1} / 2 = {${grouped(StyleTransferLab.gramUniqueEntries)}} entries",
                ),
                "Style is {statistics with the geometry deleted}.",
                "Brushwork and palette transfer because they don't depend on position; composition doesn't, because the Gram matrix has nowhere to keep it. It is only ${n(StyleTransferLab.compression)}× smaller — the point was invariance, not compression.",
            ),
        ),
    )
}
