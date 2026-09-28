import SwiftUI

// Port of BanditSection.kt. Four arms with fixed hidden win rates, a fixed seed, 200 pulls; the
// arm-selection rule is the per-topic config.

private let armNames = ["A", "B", "C", "D"]
private let trueRates: [Float] = [0.30, 0.55, 0.45, 0.72] // D is best; C is a plausible decoy
private let bestArm = 3
private let pulls = 200

private struct BanditFrame {
    let estimates: [Float]
    let counts: [Int]
    let chosen: Int
    let pull: Int
    let optimalPulls: Int
    /// Which of the config's modes chose this pull; nil on the closing summary.
    let mode: Int?
    let reward: Int?
    let headline: String
    let detail: String

    var caption: String { "\(headline) \(detail)" }
}

/// One pull's decision: the arm, which of the config's modes chose it, and the sentence saying so.
private struct Choice { let arm: Int; let mode: Int; let headline: String }

private struct BanditConfig {
    let intro: String
    /// The decision modes in the header strip. With more than one, mode 0 is the exploring one and
    /// its pulls are ticked on the scrub track as `markLabel`.
    let modes: [String]
    let markLabel: String
    /// The strategy's parameter as a readout chip ("ε = 0.10").
    let param: String
    /// What, for this strategy, can still rescue a best arm that currently looks worse.
    let correction: String
    let choose: ([Float], [Int], Int, inout KotlinRandom) -> Choice
}

private func argmax(_ xs: [Float]) -> Int { xs.indices.max { xs[$0] < xs[$1] } ?? 0 }
private func f2(_ v: Float) -> String { String(format: "%.2f", v) }
private func name(_ arm: Int) -> String { armNames[arm] }

private func runBandit(_ config: BanditConfig) -> [BanditFrame] {
    var rng = KotlinRandom(11)
    var estimates = [Float](repeating: 0, count: trueRates.count)
    var counts = [Int](repeating: 0, count: trueRates.count)
    var frames: [BanditFrame] = []
    var optimal = 0
    let best = armNames[bestArm]
    for pull in 1...pulls {
        let choice = config.choose(estimates, counts, pull, &rng)
        let arm = choice.arm
        let reward: Float = rng.nextFloat() < trueRates[arm] ? 1 : 0
        counts[arm] += 1
        // Incremental sample mean — no reward history needed.
        estimates[arm] += (reward - estimates[arm]) / Float(counts[arm])
        if arm == bestArm { optimal += 1 }
        // Dense frames early, where the strategies differ most.
        if pull <= 12 || pull % 10 == 0 {
            let leader = argmax(estimates)
            let detail: String
            if arm == bestArm {
                detail = "\(best) is the truly best arm, with a true win rate of \(trueRates[bestArm])."
            } else if counts[bestArm] == 0 {
                detail = "\(best) is truly best but hasn't been tried yet."
            } else if estimates[bestArm] < estimates[leader] {
                detail = "\(best) is truly best but looked worse after \(counts[bestArm]) pull\(counts[bestArm] == 1 ? "" : "s"). \(config.correction)"
            } else {
                detail = "\(best) is truly best, and its estimate already leads."
            }
            frames.append(BanditFrame(estimates: estimates, counts: counts, chosen: arm, pull: pull, optimalPulls: optimal,
                                      mode: choice.mode, reward: Int(reward), headline: choice.headline, detail: detail))
        }
    }
    frames.append(BanditFrame(estimates: estimates, counts: counts, chosen: bestArm, pull: pulls, optimalPulls: optimal, mode: nil, reward: nil,
                              headline: "After \(pulls) pulls, \(optimal * 100 / pulls)% went to \(best), the truly best arm.",
                              detail: "Its true win rate is \(trueRates[bestArm]). The gap from 100% is the cost of finding out."))
    return frames
}

private let banditConfigs: [String: BanditConfig] = [
    "epsilon_greedy": BanditConfig(
        intro: "Four arms with hidden win rates. ε-greedy plays the current best arm 90% of the time and picks at random the other 10% — simple, and it never stops exploring.",
        modes: ["Explore 10%", "Exploit 90%"], markLabel: "explore", param: "ε = 0.10",
        correction: "Only the 10% of random pulls can correct that.",
        choose: { est, counts, _, rng in
            if let u = counts.firstIndex(of: 0) { return Choice(arm: u, mode: 0, headline: "Explore: \(name(u)) is untried, so try it once.") }
            if rng.nextFloat() < 0.10 { let r = rng.nextInt(est.count); return Choice(arm: r, mode: 0, headline: "Explore: a random pull lands on \(name(r)).") }
            let b = argmax(est)
            return Choice(arm: b, mode: 1, headline: "Exploit: \(name(b)) has the highest estimate, so pull it again.")
        }
    ),
    "ucb": BanditConfig(
        intro: "UCB adds a confidence bonus that shrinks as an arm is pulled more, so a rarely-tried arm stays attractive until its estimate is trustworthy. No randomness at all.",
        modes: ["Untried", "Highest bound"], markLabel: "untried", param: "bonus = √(2·ln t / n)",
        correction: "Its confidence bonus keeps growing until it gets another look.",
        choose: { est, counts, pull, _ in
            if let u = counts.firstIndex(of: 0) { return Choice(arm: u, mode: 0, headline: "\(name(u)) is untried, so its upper bound is infinite.") }
            let scores = est.indices.map { est[$0] + (2 * logf(Float(pull)) / Float(counts[$0])).squareRoot() }
            let b = argmax(scores)
            return Choice(arm: b, mode: 1, headline: "\(name(b)) has the highest upper bound, \(f2(scores[b])).")
        }
    ),
    "thompson_sampling": BanditConfig(
        intro: "Thompson sampling keeps a belief distribution per arm and plays whichever arm wins a random draw from those beliefs. Uncertain arms sample widely; settled arms barely move.",
        modes: ["Posterior draw"], markLabel: "", param: "belief = Beta(w+1, l+1)",
        correction: "Its belief is still wide, so it keeps winning a draw now and then.",
        choose: { est, counts, _, rng in
            // A normal of matching spread stands in for the Beta posterior.
            let samples = est.indices.map { i -> Float in
                let spread: Float = counts[i] == 0 ? 1 : 1 / Float(counts[i]).squareRoot()
                return est[i] + (rng.nextFloat() - 0.5) * 2 * spread
            }
            let b = argmax(samples)
            return Choice(arm: b, mode: 0, headline: "\(name(b)) won the posterior draw at \(f2(samples[b])).")
        }
    ),
    "boltzmann_exploration": BanditConfig(
        intro: "Boltzmann (softmax) exploration turns estimates into pull probabilities, so a clearly worse arm is picked rarely rather than as often as any other — unlike ε-greedy's uniform random.",
        modes: ["Untried", "Softmax draw"], markLabel: "untried", param: "τ = 0.15",
        correction: "It still gets pulled in proportion to exp(estimate / τ).",
        choose: { est, counts, _, rng in
            if let u = counts.firstIndex(of: 0) { return Choice(arm: u, mode: 0, headline: "\(name(u)) is untried, so try it once.") }
            let weights = est.map { expf($0 / 0.15) }
            let total = weights.reduce(0, +)
            var roll = rng.nextFloat() * total
            var pick = 0
            for i in weights.indices {
                roll -= weights[i]
                if roll <= 0 { pick = i; break }
            }
            return Choice(arm: pick, mode: 1, headline: "\(name(pick)) was drawn with probability \(Int((weights[pick] / total * 100).rounded()))%.")
        }
    ),
    "multi_armed_bandit": BanditConfig(
        intro: "The problem itself: four arms, hidden win rates, and a pull budget. This baseline picks uniformly at random every single time -- it never uses an estimate at all, which is what every strategy in the Exploration Strategies category improves on.",
        modes: ["Random pick"], markLabel: "", param: "policy = random",
        correction: "A random policy never uses its estimates, so it never gets better.",
        choose: { _, _, _, rng in let r = rng.nextInt(trueRates.count); return Choice(arm: r, mode: 0, headline: "Random pick: the pull lands on \(name(r)).") }
    ),
    "exploration_exploitation": BanditConfig(
        intro: "Pure exploitation, so you can watch it fail. Every arm is tried exactly once, then the agent always plays its current best estimate — and stops collecting the evidence that would tell it otherwise.",
        modes: ["Try once", "Exploit"], markLabel: "untried", param: "ε = 0",
        correction: "Greedy will never pull it again to find out.",
        choose: { est, counts, _, _ in
            if let u = counts.firstIndex(of: 0) { return Choice(arm: u, mode: 0, headline: "\(name(u)) is untried, the only exploration greedy ever does.") }
            let b = argmax(est)
            return Choice(arm: b, mode: 1, headline: "Exploit: \(name(b)) has the highest estimate, \(f2(est[b])).")
        }
    ),
]

// Player layout: a stage card (mode strip, one bar per arm, legend), readout chips, narration, then
// the transport — pinned in thumb reach when docked, with exploring pulls ticked on its track.
struct BanditLab: View {
    let topicId: String
    @State private var playback: PlaybackState
    private let config: BanditConfig
    private let frames: [BanditFrame]
    private let marks: TrackMarks?
    @Environment(\.labDock) private var dock

    init(topicId: String) {
        self.topicId = topicId
        let c = banditConfigs[topicId] ?? banditConfigs["epsilon_greedy"]!
        config = c
        let f = runBandit(c)
        frames = f
        marks = c.modes.count > 1 ? TrackMarks(steps: Set(f.indices.filter { f[$0].mode == 0 }), label: c.markLabel) : nil
        _playback = State(initialValue: PlaybackState(stepCount: f.count, speedMs: 700))
    }

    var body: some View {
        let frame = frames[min(playback.index, frames.count - 1)]
        let frames = frames
        VStack(alignment: .leading, spacing: 0) {
            LabIntro(text: config.intro, bottom: 12)
            LabCard {
                ModeStrip(modes: config.modes, active: frame.mode)
                VStack(spacing: 12) {
                    ForEach(trueRates.indices, id: \.self) { ArmRow(arm: $0, frame: frame) }
                }
                .padding(.top, 16)
                .animation(.easeOut(duration: 0.2), value: playback.index)
                BanditLegend().padding(.top, 16)
                if dock == nil {
                    BanditReadout(config: config, frame: frame).padding(.top, 16)
                    BanditNarration(frame: frame).padding(.top, 14)
                }
                PlaybackTransport(state: playback, captions: frames.map(\.caption),
                                  stepLabel: { "Pull \(frames[min($0, frames.count - 1)].pull) of \(pulls)" }, marks: marks)
            }
            if dock != nil {
                BanditReadout(config: config, frame: frame).padding(.top, 14)
                BanditNarration(frame: frame).padding(.horizontal, 4).padding(.top, 16)
            }
        }
    }
}

/// The strategy's decision modes, read-only, with the one that chose this pull lit.
private struct ModeStrip: View {
    let modes: [String]
    let active: Int?
    @Environment(\.palette) private var palette

    var body: some View {
        HStack(spacing: 8) {
            HStack(spacing: 0) {
                ForEach(modes.indices, id: \.self) { i in
                    let on = i == active
                    Text(modes[i])
                        .font(AppFont.sans(14, on ? .semibold : .medium))
                        .foregroundStyle(on ? palette.onSurface : palette.muted)
                        .lineLimit(1)
                        .padding(.horizontal, 12)
                        .frame(height: 30)
                        .background {
                            if on { RoundedRectangle(cornerRadius: 7).fill(palette.dark ? Color(hex: 0x636366) : .white) }
                        }
                }
            }
            .padding(2)
            .background(SimColors.tint, in: RoundedRectangle(cornerRadius: 9))
            Spacer(minLength: 0)
            Text("estimated value").font(AppFont.sans(14)).foregroundStyle(palette.muted).lineLimit(1)
        }
    }
}

private struct ArmRow: View {
    let arm: Int
    let frame: BanditFrame
    @Environment(\.palette) private var palette

    var body: some View {
        let chosen = arm == frame.chosen
        let estimate = CGFloat(min(max(frame.estimates[arm], 0), 1))
        HStack(spacing: 0) {
            Text(armNames[arm]).font(AppFont.sans(18, .bold))
                .foregroundStyle(chosen ? SimColors.active : palette.onSurface)
                .frame(width: 30, alignment: .leading)
            // Bar length is the estimated win rate; the tick is the arm's hidden true rate.
            GeometryReader { geo in
                ZStack(alignment: .leading) {
                    Rectangle().fill(SimColors.tint)
                    Rectangle().fill(chosen ? SimColors.active : SimColors.blue.opacity(0.6)).frame(width: geo.size.width * estimate)
                    Rectangle().fill(chosen ? Color.white.opacity(0.9) : palette.onSurface.opacity(0.85))
                        .frame(width: 2, height: 26)
                        .position(x: geo.size.width * CGFloat(trueRates[arm]), y: geo.size.height / 2)
                    Text(f2(frame.estimates[arm])).font(AppFont.mono(17, .bold))
                        .foregroundStyle(chosen ? Color(hex: 0x1A1A1A) : palette.onSurface)
                        .padding(.leading, 14)
                }
            }
            .frame(height: 44)
            .clipShape(RoundedRectangle(cornerRadius: 10))
            .overlay { if chosen { RoundedRectangle(cornerRadius: 10).stroke(SimColors.active, lineWidth: 2) } }
            VStack(alignment: .trailing, spacing: 2) {
                (Text("\(frame.counts[arm])").fontWeight(.bold).foregroundColor(palette.onSurface)
                    + Text(frame.counts[arm] == 1 ? " pull" : " pulls").foregroundColor(palette.muted))
                    .font(AppFont.mono(16))
                if arm == bestArm {
                    Text("TRULY BEST").font(AppFont.sans(11, .bold)).tracking(0.6).foregroundStyle(SimColors.green)
                }
            }
            .frame(width: 96, alignment: .trailing)
        }
    }
}

private struct BanditLegend: View {
    @Environment(\.palette) private var palette

    var body: some View {
        FlowLayout(spacing: 16, lineSpacing: 6) {
            item(RoundedRectangle(cornerRadius: 3).fill(SimColors.active).frame(width: 10, height: 10), "Pulled now")
            item(RoundedRectangle(cornerRadius: 3).fill(SimColors.blue).frame(width: 10, height: 10), "Estimate")
            item(Rectangle().fill(palette.onSurface.opacity(0.85)).frame(width: 2, height: 13), "True mean")
        }
    }

    private func item(_ swatch: some View, _ label: String) -> some View {
        HStack(spacing: 6) {
            swatch
            Text(label).font(AppFont.sans(14)).foregroundStyle(palette.onSurface.opacity(0.8))
        }
    }
}

private struct BanditReadout: View {
    let config: BanditConfig
    let frame: BanditFrame

    var body: some View {
        var parts = ReadoutChips.parts(config.param)
        if let r = frame.reward { parts.append((key: "reward", value: r > 0 ? "+1" : "0")) }
        parts.append((key: "best arm", value: "\(frame.optimalPulls) / \(frame.pull)"))
        return ReadoutChips(parts: parts)
    }
}

private struct BanditNarration: View {
    let frame: BanditFrame
    @Environment(\.palette) private var palette

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            headline
                .font(AppFont.sans(19, .semibold))
                .foregroundStyle(palette.onSurface)
                .fixedSize(horizontal: false, vertical: true)
            Text(frame.detail).font(AppFont.sans(15)).foregroundStyle(palette.muted).fixedSize(horizontal: false, vertical: true)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    /// The pulled arm is named in yellow wherever the headline mentions it.
    private var headline: Text {
        let arm = armNames[frame.chosen]
        var out = Text("")
        var word = ""
        func flush() {
            if !word.isEmpty { out = out + (word == arm ? Text(word).foregroundColor(SimColors.active) : Text(word)); word = "" }
        }
        for ch in frame.headline {
            if ch.isLetter || ch.isNumber { word.append(ch) } else { flush(); out = out + Text(String(ch)) }
        }
        flush()
        return out
    }
}

/// Round legend marker.
struct LegendDot: View {
    let color: Color
    let label: String
    @Environment(\.palette) private var palette

    var body: some View {
        HStack(spacing: 5) {
            Circle().fill(color).frame(width: 10, height: 10)
            Text(label).font(AppFont.sans(12)).foregroundStyle(palette.muted)
        }
    }
}
