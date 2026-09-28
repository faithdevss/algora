import SwiftUI

// Port of EnvironmentSection.kt: the six "what does the agent actually run in" topics. Cart-pole,
// mountain car and a MuJoCo-style torque-limited pendulum are real dynamics integrated here. Atari,
// Dota and StarCraft cannot run on a phone, so each isolates the property that makes the domain hard
// into something small enough to measure, and says plainly that it is a stand-in.

private enum Scene {
    case cart(x: Double, theta: Double, force: Double?)
    case hill(position: Double, velocity: Double, goal: Double)
    case pendulum(theta: Double, torque: Double, torqueLimit: Double)
    case pixels(rows: [[Float]], caption: String)
}

private struct Series { let label: String; let color: Color; let points: [Float] }
private struct CurveSpec { let series: [Series]; let yMax: Float; let yLabel: String; let xLabel: String }
private struct BarSpec { let label: String; let value: Float; let display: String; let color: Color }

private struct MatrixSpec {
    let rowLabels: [String]
    let colLabels: [String]
    let values: [[Double]]
    var focus: (Int, Int)?
}

private struct EnvFrame {
    let status: String
    var readout: String?
    var scene: Scene?
    var curve: CurveSpec?
    var bars: [BarSpec] = []
    var matrix: MatrixSpec?
}

private struct EnvConfig { let intro: String; let build: () -> [EnvFrame] }

private let envBlue = SimColors.blue
private let envGreen = SimColors.green
private let envAmber = SimColors.active
private let envViolet = Color(hex: 0x7C3AED)
private let envRed = SimColors.red

private func degrees(_ radians: Double) -> Double { radians * 180 / .pi }

/// Kotlin's `"%,d"`.
private func grouped(_ n: Int) -> String {
    let formatter = NumberFormatter()
    formatter.numberStyle = .decimal
    formatter.groupingSeparator = ","
    formatter.usesGroupingSeparator = true
    return formatter.string(from: NSNumber(value: n)) ?? "\(n)"
}

// MARK: - Cart-pole: Gym's CartPole-v1 dynamics, semi-implicit Euler at 50 Hz, ±10 N.

private let cpGravity = 9.8
private let cpMassCart = 1.0
private let cpMassPole = 0.1
private let cpTotalMass = cpMassCart + cpMassPole
private let cpLength = 0.5
private let cpPoleMassLength = cpMassPole * cpLength
private let cpForce = 10.0
private let cpTau = 0.02
private let cpThetaLimit = 12.0 * Double.pi / 180.0
private let cpXLimit = 2.4

private struct CartState { let x: Double; let xDot: Double; let theta: Double; let thetaDot: Double }

private func cartStep(_ s: CartState, _ action: Int) -> CartState {
    let force = action == 1 ? cpForce : -cpForce
    let cosTheta = cos(s.theta)
    let sinTheta = sin(s.theta)
    let temp = (force + cpPoleMassLength * s.thetaDot * s.thetaDot * sinTheta) / cpTotalMass
    let thetaAcc = (cpGravity * sinTheta - cosTheta * temp) / (cpLength * (4.0 / 3.0 - cpMassPole * cosTheta * cosTheta / cpTotalMass))
    let xAcc = temp - cpPoleMassLength * thetaAcc * cosTheta / cpTotalMass
    return CartState(x: s.x + cpTau * s.xDot, xDot: s.xDot + cpTau * xAcc, theta: s.theta + cpTau * s.thetaDot, thetaDot: s.thetaDot + cpTau * thetaAcc)
}

private func cartAlive(_ s: CartState) -> Bool { abs(s.x) <= cpXLimit && abs(s.theta) <= cpThetaLimit }

private func cartReset(_ rng: inout KotlinRandom) -> CartState {
    let x = rng.nextDouble(-0.05, 0.05)
    let xDot = rng.nextDouble(-0.05, 0.05)
    let theta = rng.nextDouble(-0.05, 0.05)
    let thetaDot = rng.nextDouble(-0.05, 0.05)
    return CartState(x: x, xDot: xDot, theta: theta, thetaDot: thetaDot)
}

private func cartEpisode(_ seed: Int, limit: Int = 500, policy: (CartState) -> Int) -> (Int, [CartState]) {
    var rng = KotlinRandom(seed: seed)
    var s = cartReset(&rng)
    var trace = [s]
    var steps = 0
    while steps < limit && cartAlive(s) {
        s = cartStep(s, policy(s))
        trace.append(s)
        steps += 1
    }
    return (steps, trace)
}

private func cartPoleFrames() -> [EnvFrame] {
    var frames = [EnvFrame(
        status: "Four numbers are the entire state: cart position and velocity, pole angle and angular velocity. Two actions: push left or push right, always at full ±\(Int(cpForce)) N — there is no \"push gently\". The episode ends when the pole passes 12° or the cart leaves ±2.4 m, and every surviving step is worth +1.",
        readout: "state: [x, ẋ, θ, θ̇] · actions: 2 · 50 Hz",
        scene: .cart(x: 0, theta: 0.03, force: nil)
    )]

    // Android draws these coin flips from unseeded generators, so they differ run to run there too.
    let randomEpisodes = (1...200).map { seed in cartEpisode(seed) { _ in Bool.random() ? 1 : 0 }.0 }
    let (randomSteps, randomTrace) = cartEpisode(3) { _ in Bool.random() ? 1 : 0 }
    for i in [0, randomTrace.count / 3, randomTrace.count * 2 / 3, randomTrace.count - 1] {
        let s = randomTrace[i]
        frames.append(EnvFrame(
            status: "Random actions: the pole is at \(fx(degrees(s.theta), 1))° after \(i) steps. Nothing is steering it — a coin flip pushes the cart the wrong way half the time, and the pole falls the moment the errors stop cancelling.",
            readout: "step \(i) · θ = \(fx(degrees(s.theta), 1))°",
            scene: .cart(x: s.x, theta: s.theta, force: i < randomTrace.count - 1 ? cpForce : nil)
        ))
    }
    let randomMean = randomEpisodes.average
    frames.append(EnvFrame(
        status: "Over 200 random episodes the mean survival is \(fx(randomMean, 1)) steps (worst \(randomEpisodes.min()!), best \(randomEpisodes.max()!)). That number is the baseline every cart-pole result is quoted against, and it is why the task is a sanity check rather than a challenge.",
        readout: "random policy: \(fx(randomMean, 1)) of 500 steps",
        bars: [
            BarSpec(label: "random", value: Float(randomMean), display: fx(randomMean, 1), color: envRed),
            BarSpec(label: "solved (v1)", value: 500, display: "500", color: envGreen),
        ]
    ))

    // Random search over linear policies — cart-pole needs no deep network.
    var best = [Double](repeating: 0, count: 4)
    var bestScore = -1.0
    var searchRng = KotlinRandom(seed: 11)
    var evaluations = 0
    for _ in 0..<400 {
        let w = (0..<4).map { _ in searchRng.nextDouble(-1, 1) }
        let score = (1...5).map { seed in
            cartEpisode(seed) { s in w[0] * s.x + w[1] * s.xDot + w[2] * s.theta + w[3] * s.thetaDot > 0 ? 1 : 0 }.0
        }.average
        evaluations += 5
        if score > bestScore { bestScore = score; best = w }
    }
    let weights = best
    let linear: (CartState) -> Int = { s in weights[0] * s.x + weights[1] * s.xDot + weights[2] * s.theta + weights[3] * s.thetaDot > 0 ? 1 : 0 }
    let (linearSteps, linearTrace) = cartEpisode(99, policy: linear)
    for i in [0, 60, 200, min(400, linearTrace.count - 1)] {
        let s = linearTrace[min(i, linearTrace.count - 1)]
        frames.append(EnvFrame(
            status: "The same environment under a linear policy — one dot product of the four state numbers, found by random search over \(evaluations / 5) candidates. At step \(i) the pole is at \(fx(degrees(s.theta), 1))° and the cart at \(fx(s.x)) m: the controller keeps pushing under the pole rather than away from it.",
            readout: "step \(i) · θ = \(fx(degrees(s.theta), 1))° · x = \(fx(s.x)) m",
            scene: .cart(x: s.x, theta: s.theta, force: linear(s) == 1 ? cpForce : -cpForce)
        ))
    }
    let linearMean = (200...299).map { cartEpisode($0, policy: linear).0 }.average
    frames.append(EnvFrame(
        status: "Evaluated on 100 fresh starts the linear policy averages \(fx(linearMean, 1)) steps against random's \(fx(randomMean, 1)). No neural network, no gradients, no replay buffer — four weights. Cart-pole is the environment you use to check that your training loop is wired up, not to demonstrate that it is powerful.",
        readout: "linear \(fx(linearMean, 1)) vs random \(fx(randomMean, 1)) steps",
        bars: [
            BarSpec(label: "random", value: Float(randomMean), display: fx(randomMean, 1), color: envRed),
            BarSpec(label: "linear policy", value: Float(linearMean), display: fx(linearMean, 1), color: envGreen),
            BarSpec(label: "cap", value: 500, display: "500", color: envBlue),
        ]
    ))
    frames.append(EnvFrame(
        status: "What cart-pole does teach is the shape of the problem: reward is dense (+1 every step), the state is fully observed, the dynamics are smooth, and failure is immediate. Change any one of those and the same algorithm stops working — which is what the next five environments are for.",
        readout: "linear policy \(linearSteps) steps · one sampled random episode \(randomSteps)",
        scene: .cart(x: linearTrace.last!.x, theta: linearTrace.last!.theta, force: nil)
    ))
    return frames
}

// MARK: - Mountain car: Gym's MountainCar-v0 dynamics. The greedy action is wrong.

private let mcMinPos = -1.2
private let mcMaxPos = 0.6
private let mcMaxSpeed = 0.07
private let mcPower = 0.001
private let mcGravity = 0.0025
private let mcGoal = 0.5

private struct CarState { let position: Double; let velocity: Double }

private func carStep(_ s: CarState, _ action: Int) -> CarState {
    var v = s.velocity + Double(action - 1) * mcPower + cos(3 * s.position) * -mcGravity
    v = min(max(v, -mcMaxSpeed), mcMaxSpeed)
    let p = min(max(s.position + v, mcMinPos), mcMaxPos)
    if p <= mcMinPos && v < 0 { v = 0 }
    return CarState(position: p, velocity: v)
}

private func carRun(_ start: Double, _ limit: Int, policy: (CarState) -> Int) -> ([CarState], Bool) {
    var s = CarState(position: start, velocity: 0)
    var trace = [s]
    for _ in 0..<limit {
        if s.position >= mcGoal { return (trace, true) }
        s = carStep(s, policy(s))
        trace.append(s)
    }
    return (trace, s.position >= mcGoal)
}

private func mountainCarFrames() -> [EnvFrame] {
    let start = -0.5
    let limit = 200
    var frames = [EnvFrame(
        status: "Two numbers of state — position and velocity — and three actions: push left, coast, push right. The engine is deliberately too weak to climb the hill directly, and the reward is −1 per step until the flag at \(mcGoal). Everything interesting follows from those two facts.",
        readout: "engine power \(mcPower) · gravity term \(mcGravity)",
        scene: .hill(position: start, velocity: 0, goal: mcGoal)
    )]

    let (greedyTrace, greedySolved) = carRun(start, limit) { _ in 2 }
    for i in [0, 30, 80, greedyTrace.count - 1] {
        let s = greedyTrace[i]
        frames.append(EnvFrame(
            status: "Always push right — the action that points at the goal. After \(i) steps the car is at \(fx(s.position)) with velocity \(fx(s.velocity, 3)): it climbs a little, stalls, slides back, and settles into a rut. Gravity beats the engine at every point on this slope.",
            readout: "step \(i) · position \(fx(s.position)) · best so far \(fx(greedyTrace.prefix(i + 1).map(\.position).max()!))",
            scene: .hill(position: s.position, velocity: s.velocity, goal: mcGoal)
        ))
    }
    let greedyMax = greedyTrace.map(\.position).max()!
    frames.append(EnvFrame(
        status: "After \(limit) steps of the greedy action the car has never got past \(fx(greedyMax)), \(greedySolved ? "yet still reached" : "well short of") the flag at \(mcGoal). The action that maximises immediate progress is exactly the action that guarantees failure — this environment is a counterexample to greedy control, not a hard control problem.",
        readout: "max position \(fx(greedyMax)) of \(mcGoal) — failed",
        scene: .hill(position: greedyTrace.last!.position, velocity: greedyTrace.last!.velocity, goal: mcGoal)
    ))

    let (pumpTrace, pumpSolved) = carRun(start, limit) { $0.velocity >= 0 ? 2 : 0 }
    for i in [0, 25, 60, 90, pumpTrace.count - 1] {
        let s = pumpTrace[min(i, pumpTrace.count - 1)]
        frames.append(EnvFrame(
            status: "Now push in whatever direction the car is already moving — pump energy in rather than aim at the goal. At step \(i) it is at \(fx(s.position)) with velocity \(fx(s.velocity, 3)); the car deliberately climbs the wrong hill first to buy the height it needs.",
            readout: "step \(i) · position \(fx(s.position)) · velocity \(fx(s.velocity, 3))",
            scene: .hill(position: s.position, velocity: s.velocity, goal: mcGoal)
        ))
    }
    let pumpMax = pumpTrace.map(\.position).max()!
    frames.append(EnvFrame(
        status: "The energy-pumping policy reaches the flag in \(pumpTrace.count - 1) steps — \(pumpSolved ? "solved" : "not solved") — using an action rule that is wrong at almost every individual step if you judge it by immediate progress. This is what \"delayed reward\" means concretely.",
        readout: "solved in \(pumpTrace.count - 1) steps",
        scene: .hill(position: pumpTrace.last!.position, velocity: pumpTrace.last!.velocity, goal: mcGoal),
        bars: [
            BarSpec(label: "greedy", value: Float(greedyMax), display: fx(greedyMax), color: envRed),
            BarSpec(label: "energy pump", value: Float(pumpMax), display: fx(pumpMax), color: envGreen),
            BarSpec(label: "flag", value: Float(mcGoal), display: "\(mcGoal)", color: envBlue),
        ]
    ))

    var successes = 0
    var bestRandom = mcMinPos
    var rng = KotlinRandom(seed: 5)
    for _ in 0..<300 {
        let from = rng.nextDouble(-0.6, -0.4)
        let (trace, solved) = carRun(from, limit) { _ in rng.nextInt(3) }
        if solved { successes += 1 }
        bestRandom = max(bestRandom, trace.map(\.position).max()!)
    }
    frames.append(EnvFrame(
        status: "And the reason this environment is a standard exploration benchmark: over 300 random episodes of \(limit) steps, \(successes) reached the flag — the best any of them got was \(fx(bestRandom)). With −1 per step and no reward gradient to follow, an agent that explores by acting randomly never sees a single success to learn from, which is why ε-greedy Q-learning stalls here and count-based or curiosity bonuses do not.",
        readout: "random: \(successes) / 300 episodes reached the goal",
        scene: .hill(position: bestRandom, velocity: 0, goal: mcGoal)
    ))
    return frames
}

// MARK: - MuJoCo-style continuous control: a torque-limited pendulum (Gym Pendulum-v1 integration).

private let pendG = 10.0
private let pendM = 1.0
private let pendL = 1.0
private let pendDt = 0.05
private let pendMaxTorque = 2.0
private let pendMaxSpeed = 8.0

private struct PendState { let theta: Double; let thetaDot: Double }

private func pendStep(_ s: PendState, _ torque: Double) -> PendState {
    let u = min(max(torque, -pendMaxTorque), pendMaxTorque)
    let acc = 3 * pendG / (2 * pendL) * sin(s.theta) + 3.0 / (pendM * pendL * pendL) * u
    let newDot = min(max(s.thetaDot + acc * pendDt, -pendMaxSpeed), pendMaxSpeed)
    return PendState(theta: s.theta + newDot * pendDt, thetaDot: newDot)
}

private func angleNorm(_ a: Double) -> Double {
    var x = a
    while x > .pi { x -= 2 * .pi }
    while x < -.pi { x += 2 * .pi }
    return x
}

private func mujocoFrames() -> [EnvFrame] {
    // Gym's convention: theta = 0 is upright, ±π hangs down. The arm starts hanging.
    let start = PendState(theta: .pi, thetaDot: 0)
    func fromUpright(_ st: PendState) -> Double { degrees(abs(angleNorm(st.theta))) }
    let gravityPeak = fx(pendM * pendG * pendL / 2, 1)

    var frames = [EnvFrame(
        status: "A single torque-controlled joint, integrated at \(Int(1 / pendDt)) Hz — the smallest honest stand-in for a MuJoCo robot. The action is a real number in [−\(pendMaxTorque), \(pendMaxTorque)] N·m, not a choice from a menu, and gravity needs \(gravityPeak) N·m to hold the arm horizontal. The naive solution is not available: max torque is less than the torque required to lift.",
        readout: "torque limit ±\(pendMaxTorque) N·m · gravity peak \(gravityPeak) N·m",
        scene: .pendulum(theta: start.theta, torque: 0, torqueLimit: pendMaxTorque)
    )]

    var s = start
    var closest = 180.0
    for _ in 0..<200 {
        s = pendStep(s, pendMaxTorque)
        closest = min(closest, fromUpright(s))
    }
    frames.append(EnvFrame(
        status: "Hold the torque at its maximum for 200 steps and the arm never gets closer than \(fx(closest, 0))° from upright — it stalls where gravity's moment overtakes the motor and falls back. Pushing harder is not an option, so the policy has to be cleverer rather than stronger. This is the defining feature of torque-limited control.",
        readout: "closest approach \(fx(closest, 0))° from upright — never arrives",
        scene: .pendulum(theta: s.theta, torque: pendMaxTorque, torqueLimit: pendMaxTorque)
    ))

    // Energy shaping to pump, then a PD catch inside the small region the motor can hold.
    let inertia = pendM * pendL * pendL / 3
    let targetEnergy = pendM * pendG * (pendL / 2)
    func energy(_ st: PendState) -> Double { 0.5 * inertia * st.thetaDot * st.thetaDot + pendM * pendG * (pendL / 2) * cos(st.theta) }
    func shapedTorque(_ st: PendState) -> Double {
        let raw = abs(angleNorm(st.theta)) < 0.35
            ? -(6.0 * angleNorm(st.theta) + 1.2 * st.thetaDot)
            : -1.0 * (energy(st) - targetEnergy) * st.thetaDot
        return min(max(raw, -pendMaxTorque), pendMaxTorque)
    }

    var swing = start
    var trace = [swing]
    var torques = [0.0]
    var reversals = 0
    var lastSign = 0
    var upAt = -1
    for step in 0..<600 {
        let u = shapedTorque(swing)
        swing = pendStep(swing, u)
        trace.append(swing)
        torques.append(u)
        let sign = swing.thetaDot >= 0 ? 1 : -1
        if lastSign != 0 && sign != lastSign && upAt < 0 { reversals += 1 }
        lastSign = sign
        if upAt < 0 && fromUpright(swing) < 15 { upAt = step }
    }
    let settled = fromUpright(trace.last!)
    let marks = upAt > 0 ? [0, upAt / 4, upAt / 2, upAt * 3 / 4, upAt] : [0, 100, 250, 400, 599]
    for i in marks {
        let idx = min(i, trace.count - 1)
        let st = trace[idx]
        frames.append(EnvFrame(
            status: "Energy shaping instead: push in the direction the arm is already swinging, scaled by how far its energy is from what upright requires. At step \(i) the arm is \(fx(fromUpright(st), 0))° from upright with speed \(fx(st.thetaDot, 1)) rad/s and \(fx(torques[idx])) N·m applied — it spends most of that torque swinging away from the goal, because momentum is the only way up.",
            readout: "step \(i) · \(fx(fromUpright(st), 0))° from upright · ω \(fx(st.thetaDot, 1)) rad/s",
            scene: .pendulum(theta: st.theta, torque: torques[idx], torqueLimit: pendMaxTorque)
        ))
    }
    frames.append(EnvFrame(
        status: upAt >= 0
            ? "The arm reaches within 15° of upright at step \(upAt) after \(reversals) direction reversals, and holds at \(fx(settled, 0))° once the PD catch takes over inside 20°. Two controllers were needed — one to add energy, one to hold — and the switch point is set by physics, not taste: past 23° gravity's moment exceeds anything ±\(pendMaxTorque) N·m can answer. A learner has to discover both halves from a cost that only says \"be upright\"."
            : "The arm never came within 15° of upright in 400 steps despite \(reversals) reversals — this gain is not enough, which is itself the point: hand-tuned controllers are brittle, and that brittleness is why these tasks get handed to learners.",
        readout: "\(upAt >= 0 ? "reaches upright at step \(upAt)" : "never upright") · \(reversals) reversals",
        scene: .pendulum(theta: trace[min(max(upAt, 0), trace.count - 1)].theta, torque: 0, torqueLimit: pendMaxTorque)
    ))

    let dims = [1, 6, 17, 21]
    frames.append(EnvFrame(
        status: "Scaling this up is where MuJoCo's real difficulty lives, and it is combinatorial. Discretising each joint into 10 torque levels costs 10^d actions: " + dims.map { d in "\(d) joint\(d == 1 ? "" : "s") → \(String(format: "%.0e", pow(10.0, Double(d))))" }.joined(separator: ", ") + ". A 21-joint humanoid has more discrete actions than there are atoms in a person, which is why continuous-action methods (DDPG, SAC, PPO with a Gaussian head) exist at all — DQN cannot be pointed at this.",
        readout: "discretisation is not an option above a few joints",
        bars: dims.map { BarSpec(label: "\($0) dof", value: Float($0), display: "10^\($0) actions", color: $0 == 21 ? envRed : envBlue) }
    ))
    return frames
}

// MARK: - Atari: the frame-stacking lab (no emulator — the property that makes frames insufficient)

private let catchWidth = 8

private struct Catch { let ball: Int; let dir: Int; let paddle: Int; let height: Int }

private func catchStep(_ g: Catch, _ action: Int) -> Catch {
    var dir = g.dir
    var ball = g.ball + dir
    if ball < 0 { ball = 1; dir = 1 }
    if ball >= catchWidth { ball = catchWidth - 2; dir = -1 }
    return Catch(ball: ball, dir: dir, paddle: min(max(g.paddle + action - 1, 0), catchWidth - 1), height: g.height - 1)
}

private func render(_ g: Catch) -> [[Float]] {
    (0..<4).map { row in
        (0..<catchWidth).map { col in
            if row == 3 && col == g.paddle { return 1 }
            if row == 3 - g.height / 2 && col == g.ball { return 0.75 }
            return 0
        }
    }
}

/// Tabular Q-learning with the ball position only, or with the previous position too.
private func trainCatch(stacked: Bool, episodes: Int, seed: Int) -> Double {
    var rng = KotlinRandom(seed: seed)
    var q: [Int: [Double]] = [:]
    func key(_ g: Catch, _ prevBall: Int) -> Int {
        stacked ? ((g.ball * catchWidth + prevBall) * catchWidth + g.paddle) * 8 + g.height : (g.ball * catchWidth + g.paddle) * 8 + g.height
    }
    var caught = 0
    let evalFrom = episodes * 3 / 4
    for ep in 0..<episodes {
        let ball = rng.nextInt(catchWidth)
        let dir = rng.nextBoolean() ? 1 : -1
        var g = Catch(ball: ball, dir: dir, paddle: rng.nextInt(catchWidth), height: 6)
        var prev = g.ball
        let epsilon = ep >= evalFrom ? 0.0 : 0.2
        while g.height > 0 {
            let k = key(g, prev)
            let row = q[k] ?? [0, 0, 0]
            let a = rng.nextDouble() < epsilon ? rng.nextInt(3) : argmaxFirst(row)
            let prevBall = g.ball
            let next = catchStep(g, a)
            let reward: Double = next.height == 0 && next.paddle == next.ball ? 1 : 0
            let nextKey = key(next, prevBall)
            let nextRow = q[nextKey] ?? [0, 0, 0]
            if q[nextKey] == nil { q[nextKey] = nextRow }
            let target = reward + (next.height == 0 ? 0 : 0.9 * nextRow.max()!)
            var updated = q[k] ?? row
            updated[a] += 0.2 * (target - updated[a])
            q[k] = updated
            prev = prevBall
            g = next
        }
        if ep >= evalFrom && g.paddle == g.ball { caught += 1 }
    }
    return Double(caught) / Double(episodes - evalFrom)
}

private func atariFrames() -> [EnvFrame] {
    let demo = Catch(ball: 2, dir: 1, paddle: 4, height: 6)
    let after = catchStep(demo, 1)
    let single = (1...5).map { trainCatch(stacked: false, episodes: 20000, seed: $0) }.average
    let stacked = (1...5).map { trainCatch(stacked: true, episodes: 20000, seed: $0) }.average
    let rawBytes = 210 * 160 * 3
    let processed = 84 * 84
    return [
        EnvFrame(status: "This is not an Atari emulator — a phone cannot run one, and pretending otherwise would be dishonest. It is the property that makes Atari frames hard: an 8-wide catch game where the ball moves one cell per step and the paddle must be under it when it lands.",
                 readout: "8×4 grid · 3 actions · reward on catch",
                 scene: .pixels(rows: render(demo), caption: "one frame — where is the ball going?")),
        EnvFrame(status: "Here is the same scene one step later. Only by comparing the two frames can you tell the ball is moving " + (after.ball > demo.ball ? "right" : "left") + ". A single frame is a position with no velocity in it — exactly the situation DQN faced with raw Atari screens, and the reason the published agent stacks 4 frames into one observation.",
                 readout: "ball \(demo.ball) → \(after.ball)",
                 scene: .pixels(rows: render(after), caption: "the next frame — now the direction is visible")),
        EnvFrame(status: "Both observations trained with identical tabular Q-learning, 20,000 episodes, five seeds, then evaluated greedily. Position-only catches \(fx(single * 100, 0))% of balls; position plus the previous position catches \(fx(stacked * 100, 0))%. The gap is not a tuning problem — the first agent is being asked to act on a state that genuinely does not determine the answer.",
                 readout: "catch rate: \(fx(single * 100, 0))% vs \(fx(stacked * 100, 0))%",
                 bars: [
                    BarSpec(label: "1 frame", value: Float(single * 100), display: "\(fx(single * 100, 0))%", color: envRed),
                    BarSpec(label: "2 frames", value: Float(stacked * 100), display: "\(fx(stacked * 100, 0))%", color: envGreen),
                 ]),
        EnvFrame(status: "The rest of the Atari pipeline is bookkeeping with real consequences: a raw frame is 210×160×3 = \(rawBytes) bytes; the standard preprocessing takes greyscale, downsamples to 84×84 (\(processed) bytes, \(fx(Double(rawBytes) / Double(processed), 1))× smaller), stacks 4 of them, and repeats each action for 4 emulator frames. A 1M-transition replay buffer is \(fx(1_000_000.0 * Double(processed) * 4 / 1e9, 1)) GB stored naively, which is why implementations share frames between neighbouring stacks.",
                 readout: "\(rawBytes) B → \(processed) B per frame · ×4 stack",
                 scene: .pixels(rows: render(after), caption: "84×84×4 is what the network actually sees")),
        EnvFrame(status: "Two more properties make the suite what it is: the 57 games share one action interface and one observation format, so a single agent can be evaluated across all of them without per-game engineering — and scores are reported against a human baseline because raw game points are not comparable between games. Atari is a benchmark design, not just a set of games.",
                 readout: "shared interface · human-normalised scoring",
                 scene: .pixels(rows: render(demo), caption: "same interface, 57 games")),
    ]
}

// MARK: - Dota 2: horizon and action space

private func dotaFrames() -> [EnvFrame] {
    let components: [(String, Int)] = [("action type", 8), ("target unit", 190), ("offset x", 9), ("offset y", 9), ("delay", 6)]
    let product = components.reduce(1) { $0 * $1.1 }
    let ticks = 45 * 60 * 30 / 4

    // A chain with the win at the far end and a proxy available immediately.
    let chain = 40
    let proxy = 0.05
    func trainChain(_ gamma: Double, _ episodes: Int, _ seed: Int) -> (Double, Double, Double) {
        var rng = KotlinRandom(seed: seed)
        var q = [[Double]](repeating: [0, 0], count: chain + 1)
        var wins = 0
        let evalFrom = episodes * 3 / 4
        for ep in 0..<episodes {
            var s = 0
            var won = false
            let epsilon = ep >= evalFrom ? 0.0 : 0.5
            while true {
                let a = rng.nextDouble() < epsilon ? rng.nextInt(2) : (q[s][1] >= q[s][0] ? 1 : 0)
                // The choice exists only at the start: cash the proxy out now, or commit to the walk.
                let cashOut = a == 0 && s == 0
                let next = cashOut ? s : s + 1
                let terminal = cashOut || next == chain
                let reward = cashOut ? proxy : next == chain ? 1.0 : 0.0
                let target = reward + (terminal ? 0 : gamma * q[next].max()!)
                q[s][a] += 0.2 * (target - q[s][a])
                if terminal { won = !cashOut; break }
                s = next
            }
            if ep >= evalFrom && won { wins += 1 }
        }
        return (q[0][1], q[0][0], Double(wins) / Double(episodes - evalFrom))
    }
    let gammas = [0.9, 0.99, 0.999]
    let results = gammas.map { g in (g, trainChain(g, 20000, 3), 1.0 / (1 - g)) }
    let h998 = 1 / (1 - 0.998), h9997 = 1 / (1 - 0.9997)

    return [
        EnvFrame(status: "OpenAI Five's action is not one choice but several made together — " + components.map { "\($0.0) (\($0.1))" }.joined(separator: ", ") + " — so the joint action space is their product: \(grouped(product)) per decision. A flat softmax over that is unusable; the policy factorises the head and picks each component conditionally, which is the only reason the output layer fits in memory.",
                 readout: "joint action space ≈ \(grouped(product))",
                 bars: components.map { BarSpec(label: $0.0, value: Float($0.1), display: "\($0.1)", color: envBlue) }),
        EnvFrame(status: "The horizon is the other wall. A 45-minute match at 30 ticks per second with one decision every 4 ticks is about \(grouped(ticks)) decisions in a single episode, and the thing you want to reward — winning — happens once, at the end. Cart-pole told you within 20 steps whether you were wrong.",
                 readout: "one terminal reward across \(grouped(ticks)) decisions",
                 bars: [
                    BarSpec(label: "cart-pole", value: 500, display: "500", color: envGreen),
                    BarSpec(label: "Atari episode", value: 27000, display: "~27k", color: envAmber),
                    BarSpec(label: "Dota match", value: Float(ticks), display: "~\(grouped(ticks))", color: envRed),
                 ]),
        EnvFrame(status: "Measured on a \(chain)-step chain: the win is worth 1.0 and sits \(chain) steps away, while a proxy worth \(proxy) — a last hit, a small objective — can be taken immediately. Trained identically at three discount factors: " + results.map { g, res, horizon in "γ=\(g) (horizon \(fx(horizon, 0))) → push \(fx(res.0, 3)) vs cash out \(fx(res.1, 3)), wins \(fx(res.2 * 100, 0))%" }.joined(separator: "; ") + ". A discount is a horizon: 1/(1−γ). Shorter than the distance to the win, and the far reward is worth less than the near one — the agent that takes the small reward is not being greedy, it is being correct about the objective you actually gave it.",
                 readout: results.map { g, res, _ in "γ=\(g) wins \(fx(res.2 * 100, 0))%" }.joined(separator: " · "),
                 curve: CurveSpec(series: [
                    Series(label: "value of pushing for the win", color: envGreen, points: results.map { Float($0.1.0) }),
                    Series(label: "value of the immediate proxy", color: envRed, points: results.map { Float($0.1.1) }),
                 ], yMax: max(0.05, results.map { Float(max($0.1.0, $0.1.1)) }.max()!), yLabel: "learned value at the start state", xLabel: "γ = 0.9 · 0.99 · 0.999")),
        EnvFrame(status: "OpenAI Five's γ was annealed from 0.998 to 0.9997 during training — an effective horizon growing from about \(fx(h998, 0)) to \(fx(h9997, 0)) decisions, roughly 6 minutes of game time. Even that does not span a match, so the reward function was shaped with dense proxies (last hits, kills, tower damage). Those carry the opposite risk to the frame before: a proxy that keeps paying out beats a one-off win at any long horizon, which is why the shaped terms were decayed towards zero as training went on.",
                 readout: "even the longest horizon is \(fx(Double(ticks) / h9997, 0))× shorter than a match",
                 bars: [
                    BarSpec(label: "γ=0.998", value: Float(h998), display: "\(fx(h998, 0)) steps", color: envAmber),
                    BarSpec(label: "γ=0.9997", value: Float(h9997), display: "\(fx(h9997, 0)) steps", color: envGreen),
                    BarSpec(label: "match", value: Float(ticks), display: "\(grouped(ticks)) steps", color: envRed),
                 ]),
        EnvFrame(status: "The last property is partial observability: each hero sees a fog-limited slice of the map, so the five policies share weights but act on different observations, and an LSTM carries what has been seen. Add self-play, 180 years of simulated game time per day, and the honest summary is that the algorithm (PPO) was ordinary — the engineering around the environment was not.",
                 readout: "PPO + LSTM + self-play at 180 game-years/day"),
    ]
}

// MARK: - StarCraft II: non-transitivity and the league

private func starcraftFrames() -> [EnvFrame] {
    let names = ["rush", "expand", "defend"]
    // A cyclic strategy space: rush beats expand, expand beats defend, defend beats rush.
    let payoff: [[Double]] = [[0, 1, -1], [-1, 0, 1], [1, -1, 0]]
    func value(_ i: Int, _ mix: [Double]) -> Double { names.indices.reduce(0.0) { $0 + payoff[i][$1] * mix[$1] } }
    func bestResponse(_ mix: [Double]) -> Int { argmaxFirst(names.indices.map { value($0, mix) }) }
    func exploitability(_ mix: [Double]) -> Double { names.indices.map { value($0, mix) }.max()! }

    // Naive self-play: always best-respond to the opponent's latest policy.
    var current = 0
    var cycle = [current]
    var selfPlayExploit: [Float] = []
    for _ in 0..<12 {
        var mix = [0.0, 0.0, 0.0]
        mix[current] = 1
        selfPlayExploit.append(Float(exploitability(mix)))
        current = bestResponse(mix)
        cycle.append(current)
    }
    // Fictitious play: best-respond to the average of everything seen — the league idea.
    var counts = [1.0, 0.0, 0.0]
    var total = 1.0
    var leagueExploit: [Float] = []
    for _ in 0..<200 {
        let mix = counts.map { $0 / total }
        leagueExploit.append(Float(exploitability(mix)))
        counts[bestResponse(mix)] += 1
        total += 1
    }
    let finalMix = counts.map { $0 / total }
    let selfMax = selfPlayExploit.max()!
    let obsPixels = 128 * 128
    let apmLimit = 22

    return [
        EnvFrame(status: "StarCraft's strategy space is not a ladder, it is a cycle: a rush beats a greedy expansion, expanding beats a defensive build, and defending beats the rush. There is no single best strategy to converge on — which breaks the usual assumption that training against your current best opponent makes you better.",
                 readout: "non-transitive: no strategy beats all others",
                 matrix: MatrixSpec(rowLabels: names, colLabels: names, values: payoff)),
        EnvFrame(status: "Naive self-play, best-responding to the latest opponent: " + cycle.prefix(7).map { names[$0] }.joined(separator: " → ") + " … and it keeps going round. Every policy in the sequence is beaten outright by the next one — exploitability stays at \(fx(selfMax, 1)) forever. The agent is not improving, it is orbiting.",
                 readout: "self-play exploitability stays \(fx(selfPlayExploit.last!, 1))",
                 curve: CurveSpec(series: [Series(label: "self-play", color: envRed, points: selfPlayExploit)], yMax: 1.1, yLabel: "exploitability (1.0 = beaten by a counter every time)", xLabel: "iteration"),
                 matrix: MatrixSpec(rowLabels: names, colLabels: names, values: payoff, focus: (cycle[0], cycle[1]))),
        EnvFrame(status: "Now best-respond to the whole history rather than the latest opponent — the idea behind AlphaStar's league. Exploitability falls from \(fx(leagueExploit.first!)) to \(fx(leagueExploit.last!)) over 200 iterations, and the average policy converges towards the mixture " + finalMix.indices.map { "\(names[$0]) \(fx(finalMix[$0]))" }.joined(separator: ", ") + " — the Nash equilibrium of this game is a third each, which is what the numbers are approaching.",
                 readout: "league \(fx(leagueExploit.last!)) vs self-play \(fx(selfMax))",
                 curve: CurveSpec(series: [
                    Series(label: "league / fictitious play", color: envGreen, points: leagueExploit),
                    Series(label: "self-play", color: envRed, points: [Float](repeating: selfMax, count: leagueExploit.count)),
                 ], yMax: 1.1, yLabel: "exploitability of the current policy", xLabel: "iteration"),
                 matrix: MatrixSpec(rowLabels: names, colLabels: names, values: payoff)),
        EnvFrame(status: "AlphaStar's league added one more thing this toy cannot show: exploiter agents trained specifically to beat the main agent, whose job is to find its blind spots rather than to be good at the game. The main agent then has to fix what they expose — a curriculum generated by adversaries instead of by hand.",
                 readout: "main agents + league exploiters + main exploiters",
                 matrix: MatrixSpec(rowLabels: names, colLabels: names, values: payoff)),
        EnvFrame(status: "The rest of what makes StarCraft hard is scale and fairness constraints, both real: the observation is a stack of 128×128 feature layers (\(grouped(obsPixels)) cells each) plus lists of units, the action is again factorised (what, where, which unit, queued or not), the horizon runs to tens of thousands of decisions, and AlphaStar was rate-limited to about \(apmLimit) actions per 5 seconds so it could not win by mechanical speed alone. Removing that limit makes the benchmark meaningless.",
                 readout: "\(obsPixels)-px feature layers · ~\(apmLimit) actions / 5 s cap"),
    ]
}

// MARK: - Config

private let envConfigs: [String: EnvConfig] = [
    "cartpole": EnvConfig(intro: "Gym's CartPole-v1 dynamics, integrated here at 50 Hz. Random actions first, then a four-weight linear policy found by random search — both scored over real episodes.", build: cartPoleFrames),
    "mountain_car": EnvConfig(intro: "MountainCar-v0's real dynamics. The greedy action fails on purpose; the policy that works looks wrong at nearly every step. The last frame measures why random exploration cannot solve it.", build: mountainCarFrames),
    "mujoco": EnvConfig(intro: "A torque-limited joint integrated the way MuJoCo tasks are: continuous actions, a limit that rules out the naive solution, and an action space that explodes once you add joints.", build: mujocoFrames),
    "atari": EnvConfig(intro: "No emulator on a phone — so this lab runs the property that made raw Atari frames insufficient, and measures what stacking frames actually buys.", build: atariFrames),
    "dota2": EnvConfig(intro: "The two walls that define Dota as an RL problem — a factorised action space and an episode tens of thousands of decisions long — with the discount-factor claim measured on a chain MDP.", build: dotaFrames),
    "starcraft": EnvConfig(intro: "Why self-play alone fails on a non-transitive strategy space, measured: best-responding to the latest opponent cycles forever, best-responding to the whole history converges.", build: starcraftFrames),
]

// MARK: - UI

struct EnvironmentLab: View {
    private let config: EnvConfig
    private let key: String
    @Environment(\.palette) private var palette

    init(topicId: String) {
        key = envConfigs[topicId] == nil ? "cartpole" : topicId
        config = envConfigs[key]!
    }

    var body: some View {
        let key = self.key
        AsyncFrameLab(key: "environment:\(key)", speedMs: 900, build: { envConfigs[key]!.build() }) { frames, playback in
            content(frames, playback)
        }
    }

    @ViewBuilder
    private func content(_ frames: [EnvFrame], _ playback: PlaybackState) -> some View {
        let frame = frames[min(playback.index, frames.count - 1)]
        LabCard {
            LabIntro(text: config.intro)
            if let scene = frame.scene { SceneView(scene: scene) }
            if let matrix = frame.matrix { EnvMatrixView(spec: matrix) }
            if let curve = frame.curve { EnvCurveView(spec: curve) }
            if !frame.bars.isEmpty { BarPanel(bars: frame.bars) }
            FramePlayerFooter(readout: frame.readout, status: frame.status, legend: [], playback: playback, statusTop: 8, captions: frames.map(\.status))
        }
    }
}

private struct SceneView: View {
    let scene: Scene
    @Environment(\.palette) private var palette

    private var isPixels: Bool { if case .pixels = scene { true } else { false } }

    var body: some View {
        Canvas { ctx, size in
            let track = palette.muted.opacity(0.4)
            func line(_ a: CGPoint, _ b: CGPoint, _ color: Color, _ width: CGFloat) {
                var p = Path()
                p.move(to: a)
                p.addLine(to: b)
                ctx.stroke(p, with: .color(color), style: StrokeStyle(lineWidth: width, lineCap: .butt))
            }
            func circle(_ c: CGPoint, _ r: CGFloat, _ color: Color) {
                ctx.fill(Path(ellipseIn: CGRect(x: c.x - r, y: c.y - r, width: 2 * r, height: 2 * r)), with: .color(color))
            }
            switch scene {
            case let .cart(x, theta, force):
                let groundY = size.height * 0.72
                line(CGPoint(x: 16, y: groundY), CGPoint(x: size.width - 16, y: groundY), track, 3)
                let cx = size.width / 2 + CGFloat(x / cpXLimit) * (size.width / 2 - 60)
                let cartW: CGFloat = 60, cartH: CGFloat = 26
                ctx.fill(Path(roundedRect: CGRect(x: cx - cartW / 2, y: groundY - cartH, width: cartW, height: cartH), cornerRadius: 6), with: .color(envBlue))
                let poleLen = size.height * 0.45
                let tip = CGPoint(x: cx + poleLen * CGFloat(sin(theta)), y: groundY - cartH - poleLen * CGFloat(cos(theta)))
                line(CGPoint(x: cx, y: groundY - cartH), tip, abs(theta) > cpThetaLimit * 0.75 ? envRed : envGreen, 8)
                circle(tip, 7, envAmber)
                if let force {
                    let dir: CGFloat = force > 0 ? 1 : -1
                    line(CGPoint(x: cx, y: groundY + 14), CGPoint(x: cx + dir * 34, y: groundY + 14), envViolet, 5)
                }
            case let .hill(position, velocity, goal):
                func heightAt(_ p: Double) -> CGFloat { CGFloat(sin(3 * p) * 0.45 + 0.55) }
                func screenOf(_ p: Double) -> CGPoint {
                    let t = CGFloat((p - mcMinPos) / (mcMaxPos - mcMinPos))
                    return CGPoint(x: 16 + t * (size.width - 32), y: size.height * (1 - heightAt(p) * 0.8) - 6)
                }
                var path = Path()
                for i in 0...60 {
                    let point = screenOf(mcMinPos + (mcMaxPos - mcMinPos) * Double(i) / 60)
                    if i == 0 { path.move(to: point) } else { path.addLine(to: point) }
                }
                ctx.stroke(path, with: .color(track), lineWidth: 3)
                let g = screenOf(goal)
                line(g, CGPoint(x: g.x, y: g.y - 34), envGreen, 3)
                ctx.fill(Path(roundedRect: CGRect(x: g.x, y: g.y - 34, width: 20, height: 12), cornerRadius: 2), with: .color(envGreen))
                let car = screenOf(position)
                let body = CGPoint(x: car.x, y: car.y - 11)
                circle(body, 11, position >= goal ? envGreen : envBlue)
                line(body, CGPoint(x: body.x + CGFloat(velocity / mcMaxSpeed) * 40, y: body.y), envAmber, 4)
            case let .pendulum(theta, torque, torqueLimit):
                let pivot = CGPoint(x: size.width / 2, y: size.height * 0.5)
                let len = size.height * 0.34
                let tip = CGPoint(x: pivot.x + len * CGFloat(sin(theta)), y: pivot.y - len * CGFloat(cos(theta)))
                ctx.stroke(Path(ellipseIn: CGRect(x: pivot.x - len, y: pivot.y - len, width: 2 * len, height: 2 * len)), with: .color(track), lineWidth: 1.5)
                line(pivot, tip, abs(angleNorm(theta)) < 0.25 ? envGreen : envBlue, 8)
                circle(tip, 10, envAmber)
                circle(pivot, 5, palette.onSurface)
                let barW = CGFloat(torque / torqueLimit) * size.width * 0.28
                line(CGPoint(x: size.width / 2, y: size.height - 14), CGPoint(x: size.width / 2 + barW, y: size.height - 14), envViolet, 6)
                ctx.draw(Text("torque \(fx(torque)) N·m").font(AppFont.sans(10, .semibold)).foregroundStyle(palette.muted),
                         at: CGPoint(x: 12, y: size.height - 30), anchor: .topLeading)
            case let .pixels(rows, caption):
                let cols = rows[0].count
                let cell = min((size.width - 32) / CGFloat(cols), (size.height - 40) / CGFloat(rows.count))
                let originX = (size.width - cell * CGFloat(cols)) / 2
                let originY: CGFloat = 8
                for (r, row) in rows.enumerated() {
                    for (c, v) in row.enumerated() {
                        let color: Color = v >= 0.9 ? envGreen : v > 0 ? envAmber : track.opacity(0.25)
                        ctx.fill(Path(roundedRect: CGRect(x: originX + CGFloat(c) * cell + 2, y: originY + CGFloat(r) * cell + 2, width: cell - 4, height: cell - 4), cornerRadius: 4), with: .color(color))
                    }
                }
                ctx.draw(Text(caption).font(AppFont.sans(10, .semibold)).foregroundStyle(palette.muted),
                         at: CGPoint(x: originX, y: originY + CGFloat(rows.count) * cell + 6), anchor: .topLeading)
            }
        }
        .frame(height: isPixels ? 150 : 170)
        .padding(8)
        .background(palette.outlineVariant.opacity(0.3), in: RoundedRectangle(cornerRadius: 14))
        .padding(.top, 14)
    }
}

private struct BarPanel: View {
    let bars: [BarSpec]
    @Environment(\.palette) private var palette

    var body: some View {
        let maxValue = max(bars.map(\.value).max() ?? 1, 1)
        VStack(spacing: 0) {
            ForEach(bars.indices, id: \.self) { i in
                let bar = bars[i]
                WeightedRow(weights: [0.32, 0.48, 0.24]) {
                    Text(bar.label).font(.labelSmall).foregroundStyle(palette.muted).frame(maxWidth: .infinity, alignment: .leading)
                    GeometryReader { geo in
                        ZStack(alignment: .leading) {
                            RoundedRectangle(cornerRadius: 5).fill(palette.outlineVariant.opacity(0.5))
                            RoundedRectangle(cornerRadius: 5).fill(bar.color).frame(width: geo.size.width * CGFloat(min(max(bar.value / maxValue, 0.02), 1)))
                        }
                    }
                    .frame(height: 10)
                    Text(bar.display).font(AppFont.sans(11, .bold)).padding(.leading, 8).frame(maxWidth: .infinity, alignment: .leading)
                }
                .padding(.vertical, 3)
            }
        }
        .padding(.top, 12)
    }
}

private struct EnvCurveView: View {
    let spec: CurveSpec
    @Environment(\.palette) private var palette

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text(spec.yLabel).font(.labelSmall).foregroundStyle(palette.muted)
            Canvas { ctx, size in
                let padX: CGFloat = 26, padY: CGFloat = 12
                let axis = palette.muted.opacity(0.4)
                var axes = Path()
                axes.move(to: CGPoint(x: padX, y: padY))
                axes.addLine(to: CGPoint(x: padX, y: size.height - padY))
                axes.addLine(to: CGPoint(x: size.width - 8, y: size.height - padY))
                ctx.stroke(axes, with: .color(axis), lineWidth: 2)
                let plotW = size.width - padX - 12
                for series in spec.series {
                    if series.points.count < 2 {
                        for (i, v) in series.points.enumerated() {
                            let x = padX + (CGFloat(i) + 0.5) / CGFloat(series.points.count) * plotW
                            let y = size.height - padY - CGFloat(v / spec.yMax) * (size.height - 2 * padY)
                            ctx.fill(Path(ellipseIn: CGRect(x: x - 6, y: y - 6, width: 12, height: 12)), with: .color(series.color))
                        }
                        continue
                    }
                    var path = Path()
                    for (i, v) in series.points.enumerated() {
                        let x = padX + CGFloat(i) / CGFloat(series.points.count - 1) * plotW
                        let y = size.height - padY - CGFloat(min(max(v / spec.yMax, 0), 1)) * (size.height - 2 * padY)
                        if i == 0 { path.move(to: CGPoint(x: x, y: y)) } else { path.addLine(to: CGPoint(x: x, y: y)) }
                    }
                    ctx.stroke(path, with: .color(series.color), lineWidth: 3)
                }
                ctx.draw(Text(spec.xLabel).font(AppFont.sans(9)).foregroundStyle(palette.muted),
                         at: CGPoint(x: padX + 4, y: size.height - padY + 2), anchor: .topLeading)
            }
            .frame(height: 120)
            .padding(8)
            .background(palette.outlineVariant.opacity(0.3), in: RoundedRectangle(cornerRadius: 12))
            .padding(.top, 4)
            FlowLayout(spacing: 12, lineSpacing: 4) {
                ForEach(spec.series.indices, id: \.self) { i in
                    HStack(spacing: 4) {
                        Circle().fill(spec.series[i].color).frame(width: 9, height: 9)
                        Text(spec.series[i].label).font(.bodySmall).foregroundStyle(palette.muted)
                    }
                }
            }
            .padding(.top, 6)
        }
        .padding(.top, 12)
    }
}

private struct EnvMatrixView: View {
    let spec: MatrixSpec
    @Environment(\.palette) private var palette

    var body: some View {
        let weights = [CGFloat](repeating: 1, count: spec.colLabels.count + 1)
        VStack(spacing: 0) {
            WeightedRow(weights: weights) {
                Color.clear.frame(height: 1)
                ForEach(spec.colLabels.indices, id: \.self) {
                    Text(spec.colLabels[$0]).font(.labelSmall).foregroundStyle(palette.muted).padding(2).frame(maxWidth: .infinity, alignment: .leading)
                }
            }
            ForEach(spec.values.indices, id: \.self) { r in
                WeightedRow(weights: weights) {
                    Text(spec.rowLabels[r]).font(.labelSmall).foregroundStyle(palette.muted).padding(2).frame(maxWidth: .infinity, alignment: .leading)
                    ForEach(spec.values[r].indices, id: \.self) { c in
                        let v = spec.values[r][c]
                        let focused = spec.focus.map { $0 == (r, c) } ?? false
                        let fill: Color = focused ? envAmber
                            : v > 0 ? envGreen.opacity(0.25 + 0.4 * v)
                            : v < 0 ? envRed.opacity(0.25 + 0.4 * -v)
                            : palette.outlineVariant.opacity(0.5)
                        Text(String(format: "%+.0f", v))
                            .font(AppFont.sans(11, .bold))
                            .frame(maxWidth: .infinity)
                            .frame(height: 28)
                            .background(fill, in: RoundedRectangle(cornerRadius: 6))
                            .padding(2)
                    }
                }
            }
        }
        .padding(.top, 12)
    }
}
