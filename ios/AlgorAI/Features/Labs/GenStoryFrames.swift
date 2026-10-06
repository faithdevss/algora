import Foundation

// Port of GenStoryFrames.kt: GANs, diffusion, VAEs, CycleGAN, DCGAN, Stable Diffusion, DeepFakes, StyleGAN
// and neural style transfer, drawn by DeepStoryLabs.swift with the stages in GenStoryStages.swift. Every
// value is computed on a stated toy problem with the same seeds and the same plain LCG as on Android.

let genStoryTopicIds: Set<String> = [
    "gans", "diffusion_models", "vae", "cyclegan", "dcgan",
    "stable_diffusion", "deepfakes", "stylegan", "neural_style_transfer",
]

func genLab(_ topicId: String) -> DkLab? {
    switch topicId {
    case "gans": ganLab()
    case "diffusion_models": diffusionLab()
    case "vae": vaeLab()
    case "cyclegan": cycleLab()
    case "dcgan": dcganLab()
    case "stable_diffusion": stableLab()
    case "deepfakes": deepFakeLab()
    case "stylegan": styleGanLab()
    case "neural_style_transfer": styleTransferLab()
    default: nil
    }
}

private func n(_ v: Double, _ d: Int = 2) -> String { dkNum(v, d) }
private func legend(_ ink: DkInk, _ label: String, _ style: SwatchStyle = .fill) -> DkLegend { DkLegend(ink: ink, style: style, label: label) }

private func stepActions(_ frames: [DkFrame]) -> [DkFrame] {
    frames.enumerated().map { i, f in var f = f; f.action = i == frames.count - 1 ? "Start Over" : "Next"; return f }
}

private func frame(_ header: String?, _ stage: DkStage, _ legend: [DkLegend], _ formula: [String], _ headline: String, _ body: String, _ chips: [DkChip] = []) -> DkFrame {
    DkFrame(header: header, stage: stage, legend: legend, formula: formula, headline: headline, body: body, chips: chips)
}

private func chip(_ key: String, _ value: String, tint: Bool = false) -> DkChip { DkChip(key: key, value: value, tint: tint) }

/// 1,234,567 with grouping commas.
private func grouped(_ v: Int64) -> String {
    let digits = String(abs(v))
    var out = ""
    for (i, ch) in digits.enumerated() {
        if i > 0 && (digits.count - i) % 3 == 0 { out += "," }
        out.append(ch)
    }
    return v < 0 ? "−" + out : out
}

/// 786.4k, 16.8M, 68.72B; under ten thousand the plain grouped number.
private func big(_ v: Double) -> String {
    if v >= 1e12 { return n(v / 1e12, 1) + "T" }
    if v >= 1e9 { return n(v / 1e9, 2) + "B" }
    if v >= 1e6 { return n(v / 1e6, 1) + "M" }
    if v >= 1e4 { return n(v / 1e3, 1) + "k" }
    return grouped(Int64(v.rounded()))
}

private struct GenRng {
    var s: Int64
    init(_ seed: Int64) { s = seed }
    mutating func u() -> Double { s = (s &* 1103515245 &+ 12345) & 0x7fffffff; return Double(s) / 2147483648.0 }
    mutating func g() -> Double { let a = max(u(), 1e-12); let b = u(); return (-2 * log(a)).squareRoot() * cos(2 * Double.pi * b) }
}

// MARK: - GANs

private let ganMu = 1.5
private let ganSd = 0.6

/// Abramowitz–Stegun 7.1.26, written out so both platforms round the same way.
private func genErf(_ x: Double) -> Double {
    let t = 1 / (1 + 0.3275911 * abs(x))
    let y = 1 - (((((1.061405429 * t - 1.453152027) * t) + 1.421413741) * t - 0.284496736) * t + 0.254829592) * t * exp(-x * x)
    return x >= 0 ? y : -y
}

private func cdf(_ x: Double, _ mu: Double, _ sd: Double) -> Double { 0.5 * (1 + genErf((x - mu) / (sd * 2.0.squareRoot()))) }
private func pdf(_ x: Double, _ mu: Double, _ sd: Double) -> Double { exp(-(x - mu) * (x - mu) / (2 * sd * sd)) / sd }
private func ganBins(_ mu: Double, _ sd: Double) -> [Double] { (0..<16).map { i in cdf(-4 + 0.5 * Double(i + 1), mu, sd) - cdf(-4 + 0.5 * Double(i), mu, sd) } }

private func ganD(_ x: Double, _ mu: Double, _ sd: Double) -> Double {
    let r = pdf(x, ganMu, ganSd), g = pdf(x, mu, sd)
    return r + g < 1e-300 ? 0.5 : r / (r + g)
}

private struct GanState {
    let mu: Double, sd: Double
    let real: [Double], fake: [Double]
    let dReal: Double, dFake: Double, jsd: Double
    /// D at the generator's mean, and how much stronger −log D's gradient is there than log(1 − D)'s.
    let dAtMean: Double, boost: Double
    let stage: DkGan

    init(_ mu: Double, _ sd: Double) {
        self.mu = mu; self.sd = sd
        real = ganBins(ganMu, ganSd)
        fake = ganBins(mu, sd)
        let real = real, fake = fake
        let binD = real.indices.map { real[$0] + fake[$0] < 1e-15 ? 0.5 : real[$0] / (real[$0] + fake[$0]) }
        dReal = real.indices.reduce(0.0) { $0 + real[$1] * binD[$1] }
        dFake = fake.indices.reduce(0.0) { $0 + fake[$1] * binD[$1] }
        jsd = real.indices.reduce(0.0) { acc, i in
            let m = (real[i] + fake[i]) / 2
            return acc + (real[i] > 0 ? 0.5 * real[i] * log(real[i] / m) : 0) + (fake[i] > 0 ? 0.5 * fake[i] * log(fake[i] / m) : 0)
        } / log(2.0)
        dAtMean = ganD(mu, mu, sd)
        boost = (1 - dAtMean) / dAtMean
        stage = DkGan(real: real, fake: fake, d: (0...160).map { let x = -4 + Double($0) * 0.05; return DkP(x, ganD(x, mu, sd)) })
    }

    func chips() -> [DkChip] { [chip("JSD", n(jsd) + " bits", tint: true), chip("D real", n(dReal)), chip("D fake", n(dFake))] }
}

private func ganLab() -> DkLab {
    DkLab(control: .track) { _, _ in
        let header = "real vs generated · 16 bins · optimal D for this generator"
        let lg = [legend(.green, "Real data"), legend(.orange, "Generated"), legend(.blue, "Discriminator D(x)", .line)]
        let s0 = GanState(-1.5, ganSd)
        let rounds = [-0.6, 0.1, 0.65, 1.15].map { GanState($0, ganSd) }
        let eq = GanState(ganMu, ganSd)
        let collapse = GanState(ganMu, 0.15)
        func f(_ s: GanState, _ headline: String, _ body: String, _ chips: [DkChip]? = nil) -> DkFrame {
            frame(header, .gan(s.stage), lg, [], headline, body, chips ?? s.chips())
        }
        let roundText = [
            ("Round 1: the generator follows {D's slope} toward the real data.",
             "Moving its samples where D(x) rises, the orange hump shifts right. D still separates them — real \(n(rounds[0].dReal)), fake \(n(rounds[0].dFake)) — and JSD falls from \(n(s0.jsd)) to \(n(rounds[0].jsd)) bits."),
            ("Round 2: the humps overlap and {D's step softens}.",
             "Where both distributions put mass, the best D can only say how much more likely real is. Its curve tilts instead of jumping, and fakes now score \(n(rounds[1].dFake))."),
            ("Round 3: D is unsure across {the whole overlap}.",
             "JSD is down to \(n(rounds[2].jsd)) bits. D's slope at the fakes is gentler now, so each round moves the generator less than the one before."),
            ("Round 4: {nearly matched}.",
             "The generated mean is \(n(rounds[3].mu)) against 1.50. D real \(n(rounds[3].dReal)), D fake \(n(rounds[3].dFake)): barely better than a guess."),
        ]
        return stepActions([
            f(s0, "Two networks, opposite objectives.",
              "The generator turns noise into samples; the discriminator tries to tell them from real data. At step 1 the two barely overlap, so the best discriminator scores real data \(n(s0.dReal)) and fakes \(n(s0.dFake)) — the generator has almost nothing to learn from yet."),
            f(s0, "D is flat where the fakes are, so {its gradient vanishes}.",
              "The generator learns only through D's slope at its own samples, and D is pinned near 0 across the orange hump. The original loss log(1 − D) gives almost no gradient there; training uses −log D instead, whose gradient is \(big(s0.boost))× larger at the fakes' centre.",
              [chip("D at fake mean", lsSci(s0.dAtMean), tint: true), chip("−log D boost", "×" + big(s0.boost))]),
        ] + rounds.indices.map { f(rounds[$0], roundText[$0].0, roundText[$0].1) } + [
            f(eq, "{D = 0.5} everywhere: a coin flip.",
              "The generated histogram equals the real one, so the best discriminator can do no better than chance. JSD is 0 bits — the equilibrium the minimax game aims for."),
            f(collapse, "Same mean, one narrow spike: {mode collapse}.",
              "A generator that finds one convincing output can pile every sample onto it. D climbs back toward 1 where real data has no fakes (JSD \(n(collapse.jsd)) bits), and the generator tends to hop to another spike rather than spread out."),
        ])
    }
}

// MARK: - Diffusion

private let diffusionAbar: [Double] = {
    var out = [Double](repeating: 1, count: 1001)
    for t in 1...1000 { out[t] = out[t - 1] * (1 - (1e-4 + (0.02 - 1e-4) * Double(t - 1) / 999.0)) }
    return out
}()

private let diffusionTimes = [0, 100, 250, 500, 1000]

private func abarText(_ t: Int) -> String { t == 1000 ? "0.00" : n(diffusionAbar[t]) }

private func diffusionLab() -> DkLab {
    DkLab(control: .track) { _, _ in
        let count = 24
        let ring = (0..<count).map { DkP(cos(2 * Double.pi * Double($0) / Double(count)), sin(2 * Double.pi * Double($0) / Double(count))) }
        var rng = GenRng(89)
        var eps: [DkP] = []
        for _ in 0..<count { let x = rng.g(); let y = rng.g(); eps.append(DkP(x, y)) }
        var fresh: [DkP] = []
        for _ in 0..<count { let x = rng.g(); let y = rng.g(); fresh.append(DkP(x, y)) }
        // Where the reverse process ends: the ring, each point between two training points.
        var landed: [DkP] = []
        for i in 0..<count {
            let a = 2 * Double.pi * (Double(i) + 0.5 + 0.3 * (rng.u() - 0.5)) / Double(count)
            landed.append(DkP(cos(a), sin(a)))
        }
        func noised(_ x0: [DkP], _ e: [DkP], _ t: Int) -> [DkP] {
            let a = diffusionAbar[t].squareRoot(), b = (1 - diffusionAbar[t]).squareRoot()
            return x0.indices.map { DkP(a * x0[$0].x + b * e[$0].x, a * x0[$0].y + b * e[$0].y) }
        }
        let forwardThumbs = diffusionTimes.map { t in DkThumb(t: "t \(t)", abar: "ᾱ \(abarText(t))", pts: t == 0 ? ring : noised(ring, eps, t), ring: t == 0) }
        let reverseThumbs = diffusionTimes.map { t in DkThumb(t: "t \(t)", abar: "ᾱ \(abarText(t))", pts: noised(landed, fresh, t), ring: false) }
        let fwdHeader = "x₀ (24 points on a ring) → x_t · linear β, T = 1000"
        let revHeader = "x_T ~ N(0, I) → x₀ · reverse process, T = 1000"
        let fwdLegend = [legend(.green, "Data manifold x₀"), legend(.cyan, "Noised sample x_t")]
        let revLegend = [legend(.green, "Data manifold"), legend(.cyan, "Denoised sample x_t")]
        func sig(_ t: Int) -> String { "signal √ᾱ = {\(n(diffusionAbar[t].squareRoot()))} noise √(1−ᾱ) = {\(n((1 - diffusionAbar[t]).squareRoot()))}" }
        func forward(_ t: Int, _ headline: String, _ body: String, _ formula: [String]? = nil) -> DkFrame {
            frame(fwdHeader,
                  .diffusion(DkDiffusion(ring: ring, pts: t == 0 ? [] : noised(ring, eps, t), links: t > 0, faint: false, thumbs: forwardThumbs, current: diffusionTimes.firstIndex(of: t)!)),
                  fwdLegend, formula ?? ["x_t = √ᾱ·x₀ + √(1−ᾱ)·ε", sig(t)], headline, body)
        }
        func reverse(_ t: Int, _ headline: String, _ body: String, faint: Bool = true, chips: [DkChip]? = nil) -> DkFrame {
            frame(revHeader,
                  .diffusion(DkDiffusion(ring: ring, pts: noised(landed, fresh, t), links: false, faint: faint, thumbs: reverseThumbs, current: diffusionTimes.firstIndex(of: t)!)),
                  revLegend, chips == nil ? ["x_t = √ᾱ·x̂₀ + √(1−ᾱ)·ε̂", sig(t)] : [], headline, body, chips ?? [])
        }
        let onRing = landed.filter { abs(hypot($0.x, $0.y) - 1) < 0.05 }.count
        let copies = landed.filter { p in ring.contains { hypot($0.x - p.x, $0.y - p.y) < 0.05 } }.count
        return stepActions([
            forward(0, "x₀ — {24 points on a ring}.",
                    "This is the data. All a diffusion model learns is the shape of this manifold; the forward process is about to bury it in noise."),
            forward(100, "x₁₀₀ — {the signal still dominates}.",
                    "Each point has moved off the ring by a small random ε. With β rising linearly from 0.0001 to 0.02, ᾱ = Π(1 − β) is still \(abarText(100)) after 100 steps."),
            forward(250, "x₂₅₀ — signal and noise are now about equal.",
                    "A quarter of the way through, each point has drifted off the ring by a random ε. By t = 1000, ᾱ = \(n(diffusionAbar[1000], 5)): nothing of the ring is left."),
            forward(500, "x₅₀₀ — {the ring is gone}.",
                    "√ᾱ = \(n(diffusionAbar[500].squareRoot())): what remains of x₀ is a faint pull toward the centre. The points are mostly ε now."),
            forward(1000, "x₁₀₀₀ — {pure noise}.",
                    "x_T is indistinguishable from N(0, I). No learning happened in any of these steps: the forward process is a fixed formula, and any t is one jump from x₀."),
            forward(250, "Training: guess {the ε} that was added.",
                    "Pick a random t, noise x₀ in one jump, and ask a network for ε̂(x_t, t). The loss is |ε − ε̂|² — a plain regression with no adversary, which is why training is stable.",
                    ["loss = |ε − ε̂(x_t, t)|²", "t ~ U(1, 1000) · one jump per example"]),
            reverse(1000, "Sampling starts from {fresh noise}.",
                    "Draw x_T ~ N(0, I), none of it from the training points. With a perfect noise predictor the deterministic (DDIM) path back is exactly x_t = √ᾱ·x̂₀ + √(1−ᾱ)·ε̂, drawn here."),
            reverse(500, "t = 500: {structure re-emerges}.",
                    "Half the steps are undone. The points have pulled in toward the ring's scale, though no single one is on it yet."),
            reverse(250, "t = 250: {the ring is visible again}.",
                    "Signal and noise are back in balance. Each step removes a little of the predicted noise, which is why sampling is iterative and slow next to a GAN's single pass."),
            reverse(100, "t = 100: points {sit near the ring}.",
                    "Only √(1−ᾱ) = \(n((1 - diffusionAbar[100]).squareRoot())) of noise remains. The last steps make small corrections."),
            reverse(0, "t = 0: {every point lands on the ring}.",
                    "The reverse process ends on the data manifold — \(onRing) of \(count) points within 0.05 of it."),
            reverse(0, "New points on the manifold, {not copies}.",
                    "Compare with x₀ in green: the samples sit on the same ring at different angles. The model learned the shape, not the 24 examples.",
                    faint: false, chips: [chip("on the ring", "\(onRing) / \(count)", tint: true), chip("copies of x₀", "\(copies)")]),
            reverse(0, "{1,000 network calls} for one sample.",
                    "Training was cheap — one jump per example. Sampling runs the denoiser at every step, which is why DDIM (50 steps) and distillation (1–4 steps) exist.",
                    faint: false, chips: [chip("DDPM steps", "1,000", tint: true), chip("DDIM", "50")]),
        ])
    }
}

// MARK: - VAE

private struct GenVae {
    let enc: [[Double]]
    let logVar: [Double]
    let dec: [[Double]]
    let kl: [Double]
    let rmse: Double
    func mu(_ x: [Double]) -> [Double] { enc.map { row in x.indices.reduce(0.0) { $0 + row[$1] * x[$1] } } }
    func sigma(_ j: Int) -> Double { exp(0.5 * logVar[j]) }
    func decode(_ z: [Double]) -> [Double] { dec.map { row in z.indices.reduce(0.0) { $0 + row[$1] * z[$1] } } }
}

private func trainGenVae(_ beta: Double, _ data: [[Double]], steps: Int = 30_000, rate: Double = 0.01) -> GenVae {
    let d = VaeLab.dataDim, k = VaeLab.latentDim
    var rng = GenRng(41)
    var enc = [[Double]](repeating: [], count: k)
    for j in 0..<k { var row: [Double] = []; for _ in 0..<d { row.append(0.1 * rng.g()) }; enc[j] = row }
    var dec = [[Double]](repeating: [], count: d)
    for i in 0..<d { var row: [Double] = []; for _ in 0..<k { row.append(0.1 * rng.g()) }; dec[i] = row }
    var logVar = [Double](repeating: 0, count: k)
    for _ in 0..<steps {
        let x = data[min(Int(rng.u() * Double(data.count)), data.count - 1)]
        let mu = (0..<k).map { j in (0..<d).reduce(0.0) { $0 + enc[j][$1] * x[$1] } }
        let sigma = (0..<k).map { exp(0.5 * logVar[$0]) }
        var eps: [Double] = []
        for _ in 0..<k { eps.append(rng.g()) }
        let z = (0..<k).map { mu[$0] + sigma[$0] * eps[$0] }
        let res = (0..<d).map { i in (0..<k).reduce(0.0) { $0 + dec[i][$1] * z[$1] } - x[i] }
        let dz = (0..<k).map { j in 2.0 * (0..<d).reduce(0.0) { $0 + res[$1] * dec[$1][j] } }
        for i in 0..<d { for j in 0..<k { dec[i][j] -= rate * 2.0 * res[i] * z[j] } }
        for j in 0..<k {
            let dMu = dz[j] + beta * mu[j]
            for i in 0..<d { enc[j][i] -= rate * dMu * x[i] }
            logVar[j] = min(max(logVar[j] - rate * (dz[j] * eps[j] * sigma[j] * 0.5 + beta * 0.5 * (exp(logVar[j]) - 1.0)), -12), 4)
        }
    }
    var muSq = [Double](repeating: 0, count: k)
    var sq = 0.0
    for x in data {
        let mu = (0..<k).map { j in (0..<d).reduce(0.0) { $0 + enc[j][$1] * x[$1] } }
        for j in 0..<k { muSq[j] += mu[j] * mu[j] }
        for i in 0..<d {
            let r = (0..<k).reduce(0.0) { $0 + dec[i][$1] * mu[$1] } - x[i]
            sq += r * r
        }
    }
    let kl = (0..<k).map { j in 0.5 * (muSq[j] / Double(data.count) + exp(logVar[j]) - 1.0 - logVar[j]) }
    return GenVae(enc: enc, logVar: logVar, dec: dec, kl: kl, rmse: (sq / Double(data.count * d)).squareRoot())
}

private func unitKl(_ mu: Double, _ sigma: Double) -> Double { 0.5 * (mu * mu + sigma * sigma - 1 - 2 * log(sigma)) }

private func vaeLab() -> DkLab {
    DkLab(control: .track) { _, _ in
        let data = VaeLab.dataset()
        let one = trainGenVae(1.0, data)
        let four = trainGenVae(4.0, data)
        let x = data[0]
        var rng = GenRng(7)
        var eps1: [Double] = [], eps2: [Double] = [], prior: [Double] = []
        for _ in 0..<VaeLab.latentDim { eps1.append(rng.g()) }
        for _ in 0..<VaeLab.latentDim { eps2.append(rng.g()) }
        for _ in 0..<VaeLab.latentDim { prior.append(rng.g()) }
        func z(_ v: GenVae, _ eps: [Double]) -> [Double] { let m = v.mu(x); return m.indices.map { m[$0] + v.sigma($0) * eps[$0] } }
        func perX(_ v: GenVae) -> [Double] { v.mu(x).enumerated().map { unitKl($1, v.sigma($0)) } }
        func stage(_ v: GenVae, _ eps: [Double]?, _ kl: [Double], _ caption: String) -> DkStage {
            let m = v.mu(x)
            let zs = eps.map { z(v, $0) }
            return .vae(DkVae(x: x, latents: m.indices.map { DkLatent(mu: m[$0], sigma: v.sigma($0), z: zs?[$0]) }, xHat: v.decode(zs ?? m), kl: kl, klCaption: caption))
        }
        let header = "6 inputs → 4 latent distributions → 6 outputs"
        let klX = perX(one)
        let capX = "KL to the prior, per dimension · total \(n(klX.reduce(0, +)))"
        let active1 = one.kl.filter { $0 > VaeLab.activeThreshold }.count
        let z1 = z(one, eps1)
        let m1 = one.mu(x)
        // The arithmetic is shown on the unit carrying the most information about this x.
        let u = klX.indices.max { klX[$0] < klX[$1] }!
        let sub = ["₁", "₂", "₃", "₄"][u]
        let second1 = one.kl.sorted(by: >)[1]
        let second4 = four.kl.sorted(by: >)[1]
        let shift = zip(one.decode(z(one, eps1)), one.decode(z(one, eps2))).map { abs($0 - $1) }.max()!
        return stepActions([
            frame(header, stage(one, eps1, klX, capX), [], [],
                  "The encoder emits {a distribution}, not a point.",
                  "Each latent dimension gets a mean and a variance. The decoder sees one sample z from it; KL measures how far each one strays from the prior."),
            frame(header, stage(one, eps1, klX, capX), [],
                  ["z = μ + σ·ε, ε ~ N(0, 1)", "z\(sub) = \(n(m1[u])) + \(n(one.sigma(u)))·(\(n(eps1[u]))) = {\(n(z1[u]))}"],
                  "Sampling moves into an input: {z = μ + σ·ε}.",
                  "Sampling z directly can't be differentiated. Drawing ε from a fixed N(0, 1) and computing z from μ and σ can — gradients reach μ and σ, and the randomness is just another input."),
            frame(header, stage(one, eps2, klX, capX), [], [],
                  "Same x, {a new z} every pass.",
                  "A fresh ε moves every yellow dot, and the reconstruction shifts with it — by up to \(n(shift)) on one output. Training averages over these draws, so nearby codes must decode to similar outputs.",
                  [chip("max |Δx̂|", n(shift), tint: true)]),
            frame(header, stage(one, eps2, klX, capX), [],
                  ["KL = ½(μ² + σ² − 1 − ln σ²)", "z\(sub): ½(\(n(m1[u]))² + \(n(one.sigma(u)))² − 1 − ln \(n(one.sigma(u)))²) = {\(n(klX[u]))}"],
                  "KL pulls each posterior {toward N(0, 1)}.",
                  "It is zero only at μ = 0, σ = 1. The loss is reconstruction error plus this total, \(n(klX.reduce(0, +))) here, so every unit pays for the information it carries."),
            frame(header, stage(one, nil, one.kl, "KL per dimension, averaged over the data · β = 1"), [], [],
                  "β = 1 keeps {\(active1) of 4} units.",
                  "The data has 2 underlying factors. A unit that doesn't help reconstruction is cheapest at exactly the prior, μ = 0 and σ = 1 — KL zero — so the decoder learns to ignore it.",
                  [chip("active units", "\(active1) / 4", tint: true), chip("RMSE", n(one.rmse, 3))]),
            frame(header, stage(four, nil, four.kl, "KL per dimension, averaged over the data · β = 4"), [], [],
                  "β = 4 {squeezes out} a real factor.",
                  "Weight the KL four times as heavily and the weaker factor gets too expensive to carry: its unit falls from \(n(second1)) to \(n(second4)) nats. Reconstruction error rises from \(n(one.rmse, 3)) to \(n(four.rmse, 3)).",
                  [chip("weaker unit", n(second4) + " nats", tint: true), chip("RMSE", n(four.rmse, 3))]),
            frame(header,
                  .vae(DkVae(x: nil, latents: prior.map { DkLatent(mu: 0, sigma: 1, z: $0) }, xHat: one.decode(prior), kl: one.kl, klCaption: "KL per dimension, averaged over the data · β = 1")),
                  [], ["z ~ N(0, 1) · x̂ = decoder(z)"],
                  "Generate: {sample the prior}, then decode.",
                  "Skip the encoder. Because KL kept every posterior close to N(0, 1), a z drawn from the prior lands where the decoder has seen codes before, and decodes to a plausible new point."),
        ])
    }
}

// MARK: - CycleGAN

private func cycleLab() -> DkLab {
    DkLab(control: .track) { _, _ in
        let nItems = 6
        let identity = Array(0..<nItems)
        let shuffled = [3, 5, 0, 4, 1, 2]
        let neighbours = [1, 0, 3, 2, 5, 4]
        let partial = [2, 1, 0, 5, 4, 3]
        let reversed = Array(identity.reversed())
        let total = CycleGanLab.adversariallyOptimal(nItems)
        let survive = CycleGanLab.cycleConsistent(nItems)
        let local1 = CycleGanLab.withLocality(nItems, 1)
        let local2 = CycleGanLab.withLocality(nItems, 2)
        func right(_ p: [Int], _ target: [Int]) -> Int { p.indices.filter { p[$0] == target[$0] }.count }
        func shift(_ p: [Int]) -> Int { p.indices.map { abs(p[$0] - $0) }.max()! }
        func rightStat(_ p: [Int], _ target: [Int]? = nil) -> DkCycleStat {
            let r = right(p, target ?? identity)
            return DkCycleStat(key: "pairs right", value: "\(r) / \(nItems)", ink: r == nItems ? .green : r == 0 ? .pink : nil)
        }
        let adv = DkCycleStat(key: "adversarial", value: "0")
        let cyc = DkCycleStat(key: "cycle loss", value: "0")
        func intended(_ stats: [DkCycleStat], inverse: Bool = false) -> DkCyclePanel {
            DkCyclePanel(title: "Intended G", titleInk: .green, perm: identity, ink: .green, dashed: false, inverse: inverse, stats: stats)
        }
        func wrong(_ title: String, _ p: [Int], _ stats: [DkCycleStat], inverse: Bool = false) -> DkCyclePanel {
            DkCyclePanel(title: title, titleInk: .pink, perm: p, ink: .pink, dashed: true, inverse: inverse, stats: stats)
        }
        let header = "G : A → B · two mappings, same output set"
        let base = [legend(.cyan, "Domain A"), legend(.orange, "Domain B")]
        let both = base + [legend(.green, "Correct mapping", .line), legend(.pink, "Also zero loss", .dashedLine)]
        let withF = both + [legend(.violet, "Inverse F", .dashedLine)]
        return stepActions([
            frame("A and B · six items each, unpaired",
                  .cycle(DkCycle(panels: [DkCyclePanel(title: "", titleInk: .cyan, perm: nil, ink: .cyan, dashed: false)])), base, [],
                  "Two domains, {no pairs}.",
                  "Six items in A, six in B, and nothing saying which goes with which. CycleGAN has to learn G : A → B from the two sets alone.",
                  [chip("items per domain", "\(nItems)"), chip("paired examples", "0", tint: true)]),
            frame(header, .cycle(DkCycle(panels: [intended([adv, rightStat(identity)]), wrong("Shuffled G", shuffled, [adv, rightStat(shuffled)])])), both, [],
                  "This is the mapping we want — and so is this one, to the discriminator.",
                  "Both send A onto exactly the set B, so the output distribution matches and adversarial loss is zero either way. Only a cycle-consistency term can tell them apart."),
            frame(header, .cycle(DkCycle(panels: [wrong("Another G", neighbours, [adv, rightStat(neighbours)]), wrong("And another", partial, [adv, rightStat(partial)])])), both, [],
                  "Every one of the {\(total)} bijections scores zero.",
                  "Any one-to-one map sends A onto exactly the set B, so the output distribution always matches. 6! = \(total) mappings tie at zero adversarial loss, and one of them is right.",
                  [chip("zero-loss mappings", "\(total)", tint: true), chip("correct", "1")]),
            frame(header, .cycle(DkCycle(panels: [intended([cyc], inverse: true), wrong("Shuffled G", shuffled, [cyc], inverse: true)])), withF,
                  ["cycle loss = |F(G(a)) − a| + |G(F(b)) − b|"],
                  "Add {F : B → A} and require F(G(a)) = a.",
                  "Cycle consistency forces F to undo G — drawn dashed in violet. It is the term usually credited with resolving the ambiguity."),
            frame(header, .cycle(DkCycle(panels: [intended([adv, cyc, rightStat(identity)], inverse: true), wrong("Shuffled G", shuffled, [adv, cyc, rightStat(shuffled)], inverse: true)])), withF, [],
                  "Every bijection has an inverse: {\(survive) of \(total)} survive.",
                  "The shuffled G is undone by its own F just as perfectly. The cycle term rules out many-to-one collapse, which the distribution match had already excluded — it removes no bijection here.",
                  [chip("survive both losses", "\(survive) / \(total)", tint: true)]),
            frame(header, .cycle(DkCycle(panels: [
                intended([DkCycleStat(key: "max shift", value: "\(shift(identity))", ink: .green), rightStat(identity)]),
                wrong("Shuffled G", shuffled, [DkCycleStat(key: "max shift", value: "\(shift(shuffled))", ink: .pink), rightStat(shuffled)]),
            ])), both, [],
                  "A conv net can only {move things a little}.",
                  "Its receptive field is far smaller than the image, so it can't express an arbitrary rearrangement. Allowing each item to move at most one place cuts \(total) candidates to \(local1).",
                  [chip("shift ≤ 1", "\(local1)", tint: true), chip("shift ≤ 2", "\(local2)")]),
            frame(header, .cycle(DkCycle(panels: [
                intended([DkCycleStat(key: "max shift", value: "0", ink: .green), rightStat(identity)]),
                wrong("Swap neighbours", neighbours, [DkCycleStat(key: "max shift", value: "\(shift(neighbours))"), rightStat(neighbours)]),
            ])), both, [],
                  "Locality narrows the field; it doesn't {pin the answer}.",
                  "Swapping neighbours is also local and also zero-loss. What favours the identity in practice is that \"keep the layout, change the texture\" is the easiest map for a small conv net to learn.",
                  [chip("shift ≤ 1", "\(local1)", tint: true)]),
            frame("cat → dog · the mapping needs things to move", .cycle(DkCycle(panels: [
                DkCyclePanel(title: "Needed G", titleInk: .green, perm: reversed, ink: .green, dashed: false,
                             stats: [DkCycleStat(key: "max shift", value: "\(shift(reversed))", ink: .pink), DkCycleStat(key: "expressible", value: "no", ink: .pink)]),
                wrong("Learned G", identity, [DkCycleStat(key: "max shift", value: "0"), rightStat(identity, reversed)]),
            ])), both, [],
                  "When the right map {moves things far}, CycleGAN fails.",
                  "Horse → zebra keeps the layout, so the local map is the right one. Cat → dog needs shapes to move; that mapping is outside what the generator can express, and it falls back to retexturing."),
        ])
    }
}

// MARK: - DCGAN

private func covRow(_ k: Int, _ s: Int, _ inputs: Int, _ selected: Bool, _ label: String? = nil) -> DkCovRow {
    let counts = DcganLab.coverage(k, s, inputs)
    let border = k - 1
    let interior = Array(Set(counts[border..<(counts.count - border)])).sorted()
    let uniform = interior.count == 1
    return DkCovRow(label: label ?? "k = \(k), s = \(s)",
                    note: uniform ? "flat" : "checkerboard " + interior.map(String.init).joined(separator: " / "),
                    noteInk: uniform ? .green : .orange,
                    counts: counts, border: border, uniform: uniform, selected: selected)
}

private func interiorOf(_ k: Int, _ s: Int, _ inputs: Int = 8) -> [Int] {
    let counts = DcganLab.coverage(k, s, inputs)
    return Array(Set(counts[(k - 1)..<(counts.count - (k - 1))])).sorted()
}

private func dcganLab() -> DkLab {
    DkLab(control: .track) { _, _ in
        let lg = [legend(.green, "Uniform coverage"), legend(.orange, "Uneven — checkerboard"), legend(.slate, "Border (fewer writes)")]
        let h2 = "writes per output position · 8 inputs, stride 2"
        func cov(_ header: String, _ rows: [DkCovRow], _ formula: [String], _ headline: String, _ body: String, _ chips: [DkChip]) -> DkFrame {
            frame(header, .coverage(DkCoverage(rows: rows, maxCount: rows.map { $0.counts.max()! }.max()!)), lg, formula, headline, body, chips)
        }
        func chips(_ k: Int, _ s: Int, _ inputs: Int = 8) -> [DkChip] {
            [chip("k mod s", "\(k % s)", tint: true), chip("interior counts", "[" + interiorOf(k, s, inputs).map(String.init).joined(separator: ", ") + "]")]
        }
        func list(_ v: [Int], _ sep: String = ", ") -> String { v.map(String.init).joined(separator: sep) }
        let layers = DcganLab.generator
        let total = DcganLab.generatorParameters
        let shares = layers.map { Double($0.parameters) / Double(total) }
        let biggest = shares.indices.max { shares[$0] < shares[$1] }!
        return stepActions([
            cov(h2, [covRow(4, 2, 8, true)], ["O = (I − 1)·s + k = (8 − 1)·2 + 4 = {18}"],
                "A transposed conv {stamps each input into a window}.",
                "Each of the 8 inputs is written into a kernel-sized window of the output, the windows stride apart. The bars count how many writes land on each position — before any weights exist.",
                chips(4, 2)),
            cov(h2, [covRow(3, 2, 8, false), covRow(4, 2, 8, true), covRow(5, 2, 8, false)], [],
                "DCGAN's choice: kernel 4, stride 2.",
                "Every interior position is written exactly twice. With k = 3 or 5 the kernel doesn't divide by the stride, so counts alternate and the image picks up a checkerboard.",
                chips(4, 2)),
            cov(h2, [covRow(3, 2, 8, true), covRow(4, 2, 8, false), covRow(5, 2, 8, false)], [],
                "Kernel 3: counts alternate {\(list(interiorOf(3, 2)))}.",
                "Every other position gets half the writes of its neighbour. Whatever the weights learn, that pattern repeats across the image — the checkerboard artefact.",
                chips(3, 2)),
            cov(h2, [covRow(3, 2, 8, false), covRow(4, 2, 8, false), covRow(5, 2, 8, true)], [],
                "Kernel 5: {\(list(interiorOf(5, 2)))} — gentler, same period.",
                "The ratio is 2 : 3 instead of 1 : 2, so the artefact is a faint weave rather than a hard grid. Its period is still the stride.",
                chips(5, 2)),
            cov("writes per output position · 8 inputs, stride 3", [covRow(4, 3, 8, false), covRow(5, 3, 8, false), covRow(6, 3, 8, true)], [],
                "The rule: uniform exactly when {s divides k}.",
                "At stride 3, kernels 4 and 5 still mix \(list(interiorOf(4, 3), " and ")) writes; kernel 6 is flat at \(interiorOf(6, 3)[0]). Training can rescale each write but never change how many arrive.",
                chips(6, 3)),
            cov("writes per output position · 16 inputs after nearest ×2", [covRow(3, 1, 16, true, "resize ×2, then k = 3, s = 1"), covRow(3, 2, 8, false)], [],
                "The usual fix: {resize, then convolve}.",
                "Upsample by nearest neighbour and apply an ordinary stride-1 convolution. Every interior position reads exactly 3 values, so the overlap is even by construction (Odena et al., 2016).",
                [chip("interior counts", "[" + list(interiorOf(3, 1, 16)) + "]", tint: true)]),
            frame("DCGAN generator · \(grouped(total)) parameters",
                  .rows(DkRows(rows: layers.indices.map { i in
                      DkRow(title: layers[i].name.replacingOccurrences(of: "->", with: "→"), meta: grouped(layers[i].parameters),
                            bars: [DkBar(frac: shares[i], ink: .violet, label: n(shares[i] * 100, shares[i] < 0.01 ? 2 : 0) + "%")], hot: i == biggest)
                  })),
                  [legend(.violet, "Share of parameters")], [],
                  "{\(n(shares[biggest] * 100, 0))%} of the generator is one layer.",
                  "Capacity follows channels, not pixels: the 1024 → 512 transposed conv at 8×8 dominates, and the layer that actually emits the 64×64×3 image holds \(n(shares.last! * 100, 2))% of the weights."),
        ])
    }
}

// MARK: - Stable Diffusion

private func stableLab() -> DkLab {
    DkLab(control: .track) { _, _ in
        typealias L = LatentDiffusionLab
        let px = Double(L.pixelElements), lat = Double(L.latentElements)
        let pt = Double(L.pixelTokens), lt = Double(L.latentTokens)
        let pp = Double(L.pixelAttentionPairs), lp = Double(L.latentAttentionPairs)
        let pc = Double(L.pixelCrossAttentionPairs), lc = Double(L.latentCrossAttentionPairs)
        let top = log10(L.pixelSamplingPairs)
        func fr(_ v: Double) -> Double { log10(v) / top }
        func row(_ name: String, _ formula: String, _ a: Double, _ b: Double, hot: Bool = false) -> DkShrinkRow {
            DkShrinkRow(name: name, formula: formula, a: big(a), aFrac: fr(a), b: big(b), bFrac: fr(b), hot: hot)
        }
        func ratio(_ v: Double) -> String { grouped(Int64(v.rounded())) + "×" }
        let header = "pixel space vs latent space · log scale"
        let left = "pixel 512 × 512", right = "latent 64 × 64"
        let values = row("Values", "512·512·3 → 64·64·4", px, lat)
        let tokens = row("Tokens", "512² → 64²", pt, lt)
        let parts = L.components
        let unet = parts.last!.1
        return stepActions([
            frame(header, .shrink(DkShrink(left: left, right: right, rows: [values], tiles: [DkShrinkTile(value: ratio(L.elementRatio), caption: "fewer values", hot: true)])), [],
                  ["512·512·3 = {\(grouped(L.pixelElements))}", "64·64·4 = {\(grouped(L.latentElements))}"],
                  "Stable Diffusion denoises a {64 × 64 × 4} latent.",
                  "An autoencoder squeezes the 512 × 512 image 8× per side into 4 channels before diffusion starts, so every denoising step works on \(ratio(L.elementRatio)) fewer numbers."),
            frame(header, .shrink(DkShrink(left: left, right: right, rows: [values, tokens, row("Attention pairs", "N² → N²", pp, lp, hot: true)], tiles: [
                DkShrinkTile(value: ratio(L.elementRatio), caption: "fewer values"),
                DkShrinkTile(value: ratio(pt / lt), caption: "fewer tokens"),
                DkShrinkTile(value: ratio(L.attentionRatio), caption: "fewer attention pairs", hot: true),
            ])), [],
                  ["pairs: \(grouped(L.pixelTokens))² = {\(grouped(L.pixelAttentionPairs))}", "pairs: \(grouped(L.latentTokens))² = {\(grouped(L.latentAttentionPairs))}"],
                  "Attention saves far more than the latent shrink.",
                  "\(ratio(pt / lt)) fewer tokens means 64² = \(ratio(L.attentionRatio)) fewer self-attention pairs, because every token is compared with every other."),
            frame(header, .shrink(DkShrink(left: left, right: right, rows: [row("Self-attention", "N² → N²", pp, lp), row("Cross-attention", "N·77 → N·77", pc, lc, hot: true)], tiles: [
                DkShrinkTile(value: ratio(L.attentionRatio), caption: "self-attention"),
                DkShrinkTile(value: ratio(L.crossAttentionRatio), caption: "cross-attention", hot: true),
            ])), [],
                  ["cross: \(grouped(L.pixelTokens)) × 77 = {\(grouped(L.pixelCrossAttentionPairs))}", "cross: \(grouped(L.latentTokens)) × 77 = {\(grouped(L.latentCrossAttentionPairs))}"],
                  "Cross-attention saves {only \(ratio(L.crossAttentionRatio))}.",
                  "It compares image tokens with 77 text tokens, so it is linear in N and shrinks only as fast as the token count. The \(ratio(L.attentionRatio)) belongs to self-attention alone."),
            frame(header, .shrink(DkShrink(left: "pixel · DDPM \(grouped(Int64(L.ddpmSteps))) steps", right: "latent · DDIM \(L.ddimSteps) steps",
                                         rows: [row("Attention pairs per image", "N²·steps", L.pixelSamplingPairs, L.latentSamplingPairs, hot: true)], tiles: [
                DkShrinkTile(value: ratio(L.attentionRatio), caption: "per step"),
                DkShrinkTile(value: ratio(L.stepSaving), caption: "fewer steps"),
                DkShrinkTile(value: ratio(L.samplingRatio), caption: "less work per image", hot: true),
            ])), [], [],
                  "Add a 50-step sampler: {\(ratio(L.samplingRatio))} less attention work.",
                  "DDPM runs the network \(grouped(Int64(L.ddpmSteps))) times; DDIM gets comparable images in \(L.ddimSteps). Together with the latent, that is the gap between a cluster and a consumer GPU."),
            frame("the three networks in one checkpoint", .shrink(DkShrink(left: "parameters", right: "runs per image", rows: parts.indices.map { i in
                let runs = i == parts.count - 1 ? L.ddimSteps : 1
                let name = parts[i].0.components(separatedBy: " (").first!
                return DkShrinkRow(name: name, formula: "", a: "\(parts[i].1)M", aFrac: Double(parts[i].1) / Double(unet), b: "×\(runs)", bFrac: Double(runs) / Double(L.ddimSteps), hot: i == parts.count - 1)
            }, tiles: [
                DkShrinkTile(value: "\(grouped(Int64(L.totalParametersMillions)))M", caption: "parameters in all"),
                DkShrinkTile(value: n(L.unetShare * 100, 0) + "%", caption: "in the UNet"),
                DkShrinkTile(value: "\(L.ddimSteps)×", caption: "UNet runs per image", hot: true),
            ])), [], [],
                  "Only the UNet {runs every step}.",
                  "The VAE and the text encoder run once each, at the two ends. The UNet — \(n(L.unetShare * 100, 0))% of the \(grouped(Int64(L.totalParametersMillions)))M parameters — runs \(L.ddimSteps) times, twice per step with classifier-free guidance."),
            frame("one latent cell against the pixels it covers", .shrink(DkShrink(left: left, right: right, rows: [row("Numbers per 8 × 8 patch", "8·8·3 → 4", 192, 4, hot: true)], tiles: [
                DkShrinkTile(value: "\(L.downsample) px", caption: "per cell, each side"),
                DkShrinkTile(value: "\(L.latentChannels)", caption: "numbers to hold it"),
                DkShrinkTile(value: "48×", caption: "squeeze", hot: true),
            ])), [], ["f = \(L.downsample) · \(L.latentChannels) channels · 192 → {4}"],
                  "Detail lost at encode time {never comes back}.",
                  "Each latent cell summarises an 8 × 8 patch in 4 numbers. Small faces, hands and text that don't fit are gone before the first denoising step, and no number of sampling steps recovers them."),
        ])
    }
}

// MARK: - DeepFakes

private func deepFakeLab() -> DkLab {
    DkLab(control: .track) { _, _ in
        let sweep = DeepFakeLab.sweepResults
        let aligned = sweep[0]
        let own = DeepFakeLab.ownDomainRmse()
        let header = "one encoder, two decoders"
        let lg = [legend(.blue, "Training on A", .line), legend(.violet, "Training on B", .line), legend(.yellow, "Swap at inference", .dashedLine)]
        let formula = ["train: D_A(E(x_A)) ≈ x_A, D_B(E(x_B)) ≈ x_B", "swap: {D_B(E(x_A))} → B's face, A's pose"]
        let finite = sweep.filter { $0.sharedEncoderRmse.isFinite && $0.sharedEncoderRmse < aligned.meanFaceBaselineRmse * 4 }
        let crossing = finite.first { !$0.sharedBeatsBaseline }
        let hi = max(finite.map(\.sharedEncoderRmse).max()!, finite.map(\.meanFaceBaselineRmse).max()!) * 1.1
        func plot(_ guide: (Double, String)?) -> DkStage {
            .plot(DkPlot(xr: (0, 90), yr: (0, hi), yTicks: [(0, "0"), (hi / 2, n(hi / 2)), (hi, n(hi))], xLeft: "0°", xRight: "90°",
                         lines: [DkLine(pts: finite.map { DkP($0.degrees, $0.sharedEncoderRmse) }, ink: .blue, dots: true),
                                 DkLine(pts: sweep.map { DkP($0.degrees, $0.meanFaceBaselineRmse) }, ink: .grey, dashed: true)],
                         axis: false, guide: guide, xTicks: sweep.map { DkTick(x: $0.degrees, label: "\(Int($0.degrees))°") }))
        }
        let plotLegend = [legend(.blue, "Shared encoder", .line), legend(.grey, "Mean-face baseline", .dashedLine)]
        let plotHeader = "swap error against the angle between the two faces' expression spaces"
        let indLoses = !aligned.independentBeatsBaseline
        return stepActions([
            frame(header, .fakeArch(DkFakeArch(shared: true, trainA: true, trainB: true, swap: true)), lg, formula,
                  "One shared encoder and two identity-specific decoders.",
                  "Both identities go through the same trunk, so E learns pose and expression. The swap is breaking the pairing at inference: A's code into B's decoder."),
            frame(header, .fakeArch(DkFakeArch(shared: true, trainA: false, trainB: false, swap: true)), Array(lg.dropFirst(2)), [],
                  "The bar to clear: {B's average face}.",
                  "Nothing in the loss says z should hold expression and not identity. So score the swap against a model that ignores its input and always outputs B's mean face: error \(n(aligned.meanFaceBaselineRmse, 3)). The shared encoder gets \(n(aligned.sharedEncoderRmse, 3)).",
                  [chip("shared swap", n(aligned.sharedEncoderRmse, 3), tint: true), chip("mean face", n(aligned.meanFaceBaselineRmse, 3))]),
            frame("one encoder per identity", .fakeArch(DkFakeArch(shared: false, trainA: true, trainB: true, swap: true)), lg, [],
                  indLoses ? "Give each face its own encoder and the swap {loses to doing nothing}." : "Give each face its own encoder and the swap {gets worse}.",
                  "Each encoder orders and scales its directions by its own identity's variance, so the same expression lands on different numbers and B's decoder reads a different expression: error \(n(aligned.independentEncoderRmse, 3)) against \(n(aligned.meanFaceBaselineRmse, 3)) for the mean face.",
                  [chip("independent swap", n(aligned.independentEncoderRmse, 3), tint: true), chip("mean face", n(aligned.meanFaceBaselineRmse, 3))]),
            frame(header, .fakeArch(DkFakeArch(shared: true, trainA: true, trainB: true, swap: false)), Array(lg.prefix(2)), [],
                  "Both rebuild their own faces {almost perfectly}.",
                  "Own-domain error is \(n(own, 3)) either way. A model can reconstruct perfectly and still swap worse than a constant, so reconstruction quality says nothing about the swap.",
                  [chip("own-domain error", n(own, 3), tint: true)]),
            frame(plotHeader, plot(nil), plotLegend, [],
                  "Sharing works only if {the two faces move alike}.",
                  "Rotate B's expression space away from A's and the shared code means less to B's decoder: " +
                    finite.map { "\(Int($0.degrees))° \(n($0.sharedEncoderRmse, 3))" }.joined(separator: " · ") + "."),
            frame(plotHeader, plot(crossing.map { ($0.degrees, "\(Int($0.degrees))°") }), plotLegend, [],
                  crossing.map { "By {\(Int($0.degrees))°} the swap is worse than the mean face." } ?? "The swap {degrades steadily} with the angle.",
                  "No amount of training fixes this: the information B's decoder needs is not in the code. It is the measurable form of what practitioners report — swaps work between people who already look and move alike."),
        ])
    }
}

// MARK: - StyleGAN

private func warp(_ u: Double, _ v: Double) -> DkP { DkP(u + 0.07 * sin(2 * Double.pi * v), v - 0.18 * sin(2 * Double.pi * u)) }

private func pathLength(_ pts: [DkP]) -> Double { zip(pts, pts.dropFirst()).reduce(0.0) { $0 + hypot($1.1.x - $1.0.x, $1.1.y - $1.0.y) } }

private func styleGanLab() -> DkLab {
    DkLab(control: .track) { _, _ in
        let side = 9
        let grid = (0..<side).flatMap { i in (0..<side).map { j in DkP(Double(i) / Double(side - 1), Double(j) / Double(side - 1)) } }
        let warped = grid.map { warp($0.x, $0.y) }
        let a = DkP(0.1, 0.15), b = DkP(0.9, 0.75)
        let steps = 64
        let zLine = (0...steps).map { s -> DkP in let t = Double(s) / Double(steps); return DkP(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t) }
        let gPath = zLine.map { warp($0.x, $0.y) }
        let wa = warp(a.x, a.y), wb = warp(b.x, b.y)
        let wLine = (0...steps).map { s -> DkP in let t = Double(s) / Double(steps); return DkP(wa.x + (wb.x - wa.x) * t, wa.y + (wb.y - wa.y) * t) }
        let stretch = pathLength(gPath) / pathLength(zLine)
        let viaW = pathLength(gPath) / pathLength(wLine)
        let lengths = StyleGanLab.pathLengths()
        let cx = warped.reduce(0.0) { $0 + $1.x } / Double(warped.count)
        let cy = warped.reduce(0.0) { $0 + $1.y } / Double(warped.count)
        let psi = 0.7
        let truncated = warped.map { DkP(cx + psi * ($0.x - cx), cy + psi * ($0.y - cy)) }
        let header = "z ~ uniform on the square · 81 samples"
        let zLegend = [legend(.cyan, "Latent Z"), legend(.green, "Straight path in Z", .line), legend(.orange, "Same path after G", .line)]
        let wLegend = [legend(.cyan, "Samples"), legend(.orange, "Path via Z", .line), legend(.green, "Straight in W", .line)]
        let gridChip = chip("grid", "\(side) × \(side)")
        let twoWays = "the same two endpoints, interpolated two ways"
        let viaPanels = DkStage.warp(DkWarp(left: DkWarpPanel(caption: "via Z: curved", pts: warped, path: gPath, pathInk: .orange),
                                            right: DkWarpPanel(caption: "via W: straight", pts: warped, path: wLine, pathInk: .green)))
        return stepActions([
            frame(header, .warp(DkWarp(left: DkWarpPanel(caption: "Z (prior)", pts: grid), right: DkWarpPanel(caption: "G(Z) (what data needs)", pts: warped))),
                  [legend(.cyan, "Latent Z")], [],
                  "The prior is {a fixed square}.",
                  "z is drawn uniformly from the square — 81 samples on a 9 × 9 grid here. The data's attributes don't fill a square, so the generator has to bend it into the shape on the right.",
                  [gridChip]),
            frame(header, .warp(DkWarp(left: DkWarpPanel(caption: "Z (prior)", pts: grid, path: zLine, pathInk: .green),
                                       right: DkWarpPanel(caption: "G(Z) (what data needs)", pts: warped, path: gPath, pathInk: .orange))),
                  zLegend, [],
                  "A fixed prior forces the generator to warp.",
                  "Z is a square and can't change shape, so G must bend it to fit the data. A straight line in Z comes out curved and \(n(stretch))× longer — attributes change unevenly along it. StyleGAN's mapping to W exists to undo this.",
                  [chip("path length", "×\(n(stretch))", tint: true), gridChip]),
            frame(twoWays, viaPanels, wLegend, [],
                  "A mapping network lets the path {go straight}.",
                  "StyleGAN first maps z to w with an 8-layer MLP. W isn't tied to a fixed shape, so it can take the data's shape itself, and a straight line in W crosses it evenly. Between the same endpoints the path through Z is \(n(viaW))× longer.",
                  [chip("via Z", "×\(n(viaW))", tint: true), chip("via W", "×1.00")]),
            frame(twoWays, viaPanels, wLegend,
                  ["path length via Z = {\(n(lengths.latentZ, 3))}", "path length via W = {\(n(lengths.latentW, 3))}"],
                  "Averaged over 40,000 paths: {\(n(lengths.ratio, 1))×} shorter in W.",
                  "Measured on a toy generator whose data is missing one attribute combination, the squared path length through Z is \(n(lengths.ratio, 1))× that through W. Shorter paths mean attributes change smoothly — the disentanglement StyleGAN reports."),
            frame("w pulled toward the average w̄",
                  .warp(DkWarp(left: DkWarpPanel(caption: "W", pts: warped), right: DkWarpPanel(caption: "truncated, ψ = \(n(psi, 1))", pts: truncated))),
                  [legend(.cyan, "Samples in W")], ["w′ = w̄ + ψ·(w − w̄)"],
                  "Truncation trades variety for quality: {ψ = \(n(psi, 1))}.",
                  "Samples far from the average w are the ones the generator saw least. Pulling every w \(n((1 - psi) * 100, 0))% toward w̄ shrinks the spread to \(n(psi * 100, 0))% — fewer odd outputs, less diversity.",
                  [chip("spread", "×\(n(psi))", tint: true)]),
            frame("w reaches every layer as a style",
                  .warp(DkWarp(left: DkWarpPanel(caption: "Z (prior)", pts: grid), right: DkWarpPanel(caption: "W", pts: warped, path: wLine, pathInk: .green))),
                  [legend(.cyan, "Samples"), legend(.green, "Straight in W", .line)], ["AdaIN(x, w) = σ_w·(x − μ(x))/σ(x) + μ_w"],
                  "w is fed to every layer as {a style}.",
                  "AdaIN rescales each layer's features with w: the 4–8 px layers set pose and shape, 16–32 px set features, 64–1024 px set colour and texture. Mixing two w's across layers mixes those attributes.",
                  [chip("coarse", "\(StyleGanLab.styleInputsIn("coarse"))", tint: true), chip("middle", "\(StyleGanLab.styleInputsIn("middle"))"), chip("fine", "\(StyleGanLab.styleInputsIn("fine"))")]),
        ])
    }
}

// MARK: - Neural style transfer

private func styleTransferLab() -> DkLab {
    DkLab(control: .track) { _, _ in
        let f = StyleTransferLab.featureMap(channels: 6, positions: 10)
        let p = StyleTransferLab.shuffleColumns(f)
        let g = StyleTransferLab.gram(f)
        let gp = StyleTransferLab.gram(p)
        let moved = (0..<10).first { j in (0..<6).allSatisfy { p[$0][j] == f[$0][0] } }!
        var content = 0.0
        for i in 0..<6 { for j in 0..<10 { content += (f[i][j] - p[i][j]) * (f[i][j] - p[i][j]) } }
        var gramDelta = 0.0
        for i in 0..<6 { for j in 0..<6 { gramDelta = max(gramDelta, abs(g[i][j] - gp[i][j])) } }
        let hi = 2, hj = 3
        let entry = (0..<10).reduce(0.0) { $0 + f[hi][$1] * f[hj][$1] } / 10
        let header = "6 channels × 10 positions · then G = F Fᵀ / 10"
        let lg = [legend(.blue, "Feature value"), legend(.violet, "Gram entry")]
        let tracked = lg + [legend(.yellow, "Column 1, tracked", .ring)]
        func four(_ track: Bool) -> DkStage {
            .gram(DkGram(columns: [
                [DkHeat(title: "F", m: f, ink: .blue, values: false, track: track ? 0 : nil), DkHeat(title: "Gram of F", m: g, ink: .violet, values: true)],
                [DkHeat(title: "F, columns permuted", m: p, ink: .blue, values: false, track: track ? moved : nil), DkHeat(title: "Gram of permuted F", m: gp, ink: .violet, values: true)],
            ]))
        }
        let deltas = [chip("content Δ", n(content)), chip("Gram Δ", n(gramDelta, 3), tint: true)]
        return stepActions([
            frame(header, .gram(DkGram(columns: [[DkHeat(title: "F", m: f, ink: .blue, values: false), DkHeat(title: "Gram of F", m: g, ink: .violet, values: true)]])), lg, [],
                  "Style transfer starts from {a feature map}.",
                  "VGG is frozen; the image's pixels are what get optimized. This is one layer's output — 6 channels at 10 positions — and its Gram matrix: how strongly each pair of channels fires together.",
                  [chip("channels", "6"), chip("positions", "10")]),
            frame(header, four(true), tracked, [],
                  "Shuffle every position — the style stays put.",
                  "Every value has moved, so content loss sees a different picture. The Gram matrix only sums over positions, so it is unchanged: that is why it captures texture, not layout.",
                  deltas),
            frame(header, .gram(DkGram(columns: [[
                DkHeat(title: "F", m: f, ink: .blue, values: false, hotRows: (hi, hj)),
                DkHeat(title: "Gram of F", m: g, ink: .violet, values: true, hotCell: (hi, hj)),
            ]])), lg, ["G₃₄ = Σₚ F₃ₚ·F₄ₚ / 10 = {\(n(entry))}"],
                  "Each Gram entry {sums over positions}.",
                  "Entry (3, 4) multiplies channel 3 by channel 4 at every position and adds them up. Position only indexes the sum, so reordering positions can't change it."),
            frame(header, four(false), lg, ["content = Σ (F − F′)² = {\(n(content))}", "style = max |G − G′| = {\(n(gramDelta, 3))}"],
                  "Content loss sees {layout}; style loss can't.",
                  "That is the division of labour: the content term compares feature maps position by position, the style term compares only which features co-occur.",
                  deltas),
            frame(header, four(false), lg, ["L = α·content(x, photo) + β·style(x, painting)", "∂L/∂x → update the pixels of x"],
                  "The image is optimized to {match both}.",
                  "Start from the photo and take gradient steps on its pixels: keep the photo's feature map at \(StyleTransferLab.contentLayer), match the painting's Gram matrices at \(StyleTransferLab.styleLayers.first!) … \(StyleTransferLab.styleLayers.last!)."),
            frame(header, four(false), lg, [
                "conv4_1: \(StyleTransferLab.vggChannels) × \(StyleTransferLab.vggSide) × \(StyleTransferLab.vggSide) = {\(grouped(StyleTransferLab.featureValues))} values",
                "Gram: \(StyleTransferLab.vggChannels)·\(StyleTransferLab.vggChannels + 1) / 2 = {\(grouped(StyleTransferLab.gramUniqueEntries))} entries",
            ],
                  "Style is {statistics with the geometry deleted}.",
                  "Brushwork and palette transfer because they don't depend on position; composition doesn't, because the Gram matrix has nowhere to keep it. It is only \(n(StyleTransferLab.compression))× smaller — the point was invariance, not compression."),
        ])
    }
}
