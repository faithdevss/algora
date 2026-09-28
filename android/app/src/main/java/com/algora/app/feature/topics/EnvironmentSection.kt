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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.algora.app.core.ui.theme.SimColors
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

// ── Environment player ───────────────────────────────────────────────────────
// The six "what does the agent actually run in" topics. Three of them (cart-pole, mountain car,
// MuJoCo-style torque control) are real dynamics integrated here, so the rollouts on screen are the
// physics rather than an animation. The other three cannot be run on a phone — an Atari emulator, a
// Dota match, a StarCraft ladder — so each of those labs isolates the one property that makes the
// domain hard (partial observability, horizon length, non-transitivity) into something small enough
// to measure honestly, and says plainly that it is a stand-in.

private sealed interface Scene {
    class Cart(val x: Double, val theta: Double, val force: Double?) : Scene
    class Hill(val position: Double, val velocity: Double, val goal: Double) : Scene
    class Pendulum(val theta: Double, val torque: Double, val torqueLimit: Double) : Scene
    class Pixels(val rows: List<List<Float>>, val caption: String) : Scene
}

private class Series(val label: String, val color: Color, val points: List<Float>)

private class CurveSpec(
    val series: List<Series>,
    val yMax: Float,
    val yLabel: String,
    val xLabel: String,
)

private class BarSpec(val label: String, val value: Float, val display: String, val color: Color)

private class MatrixSpec(
    val rowLabels: List<String>,
    val colLabels: List<String>,
    val values: List<List<Double>>,
    val focus: Pair<Int, Int>? = null,
)

private class EnvFrame(
    val status: String,
    val readout: String? = null,
    val scene: Scene? = null,
    val curve: CurveSpec? = null,
    val bars: List<BarSpec> = emptyList(),
    val matrix: MatrixSpec? = null,
)

private class EnvConfig(val intro: String, val build: () -> List<EnvFrame>)

private val EnvBlue = SimColors.Blue
private val EnvGreen = SimColors.Green
private val EnvAmber = SimColors.Active
private val EnvViolet = Color(0xFF7C3AED)
private val EnvRed = SimColors.Red

// ── Cart-pole ────────────────────────────────────────────────────────────────
// Gym's CartPole-v1 dynamics, unchanged: semi-implicit Euler at 50 Hz, ±10 N, failure at 12° or 2.4 m.

private const val CP_GRAVITY = 9.8
private const val CP_MASS_CART = 1.0
private const val CP_MASS_POLE = 0.1
private const val CP_TOTAL_MASS = CP_MASS_CART + CP_MASS_POLE
private const val CP_LENGTH = 0.5
private const val CP_POLEMASS_LENGTH = CP_MASS_POLE * CP_LENGTH
private const val CP_FORCE = 10.0
private const val CP_TAU = 0.02
private const val CP_THETA_LIMIT = 12.0 * Math.PI / 180.0
private const val CP_X_LIMIT = 2.4

private class CartState(val x: Double, val xDot: Double, val theta: Double, val thetaDot: Double)

private fun cartStep(s: CartState, action: Int): CartState {
    val force = if (action == 1) CP_FORCE else -CP_FORCE
    val cosTheta = cos(s.theta)
    val sinTheta = sin(s.theta)
    val temp = (force + CP_POLEMASS_LENGTH * s.thetaDot * s.thetaDot * sinTheta) / CP_TOTAL_MASS
    val thetaAcc = (CP_GRAVITY * sinTheta - cosTheta * temp) /
        (CP_LENGTH * (4.0 / 3.0 - CP_MASS_POLE * cosTheta * cosTheta / CP_TOTAL_MASS))
    val xAcc = temp - CP_POLEMASS_LENGTH * thetaAcc * cosTheta / CP_TOTAL_MASS
    val xDot = s.xDot + CP_TAU * xAcc
    val thetaDot = s.thetaDot + CP_TAU * thetaAcc
    return CartState(s.x + CP_TAU * s.xDot, xDot, s.theta + CP_TAU * s.thetaDot, thetaDot)
}

private fun cartAlive(s: CartState): Boolean =
    abs(s.x) <= CP_X_LIMIT && abs(s.theta) <= CP_THETA_LIMIT

private fun cartReset(rng: Random) = CartState(
    rng.nextDouble(-0.05, 0.05),
    rng.nextDouble(-0.05, 0.05),
    rng.nextDouble(-0.05, 0.05),
    rng.nextDouble(-0.05, 0.05),
)

private fun cartEpisode(rng: Random, limit: Int = 500, policy: (CartState) -> Int): Pair<Int, List<CartState>> {
    var s = cartReset(rng)
    val trace = mutableListOf(s)
    var steps = 0
    while (steps < limit && cartAlive(s)) {
        s = cartStep(s, policy(s))
        trace += s
        steps++
    }
    return steps to trace
}

private fun cartPoleFrames(): List<EnvFrame> {
    val frames = mutableListOf<EnvFrame>()
    val rng = Random(4)

    frames += EnvFrame(
        status = "Four numbers are the entire state: cart position and velocity, pole angle and angular " +
            "velocity. Two actions: push left or push right, always at full ±${CP_FORCE.toInt()} N — there is " +
            "no \"push gently\". The episode ends when the pole passes 12° or the cart leaves ±2.4 m, and every " +
            "surviving step is worth +1.",
        scene = Scene.Cart(0.0, 0.03, null),
        readout = "state: [x, ẋ, θ, θ̇] · actions: 2 · 50 Hz",
    )

    val randomEpisodes = (1..200).map { cartEpisode(Random(it)) { if (Random.nextBoolean()) 1 else 0 }.first }
    val (randomSteps, randomTrace) = cartEpisode(Random(3)) { if (Random(it.hashCode()).nextBoolean()) 1 else 0 }
    listOf(0, randomTrace.size / 3, randomTrace.size * 2 / 3, randomTrace.lastIndex).forEach { i ->
        val s = randomTrace[i]
        frames += EnvFrame(
            status = "Random actions: the pole is at ${"%.1f".format(Math.toDegrees(s.theta))}° after $i steps. " +
                "Nothing is steering it — a coin flip pushes the cart the wrong way half the time, and the pole " +
                "falls the moment the errors stop cancelling.",
            scene = Scene.Cart(s.x, s.theta, if (i < randomTrace.lastIndex) CP_FORCE else null),
            readout = "step $i · θ = ${"%.1f".format(Math.toDegrees(s.theta))}°",
        )
    }
    frames += EnvFrame(
        status = "Over 200 random episodes the mean survival is " +
            "${"%.1f".format(randomEpisodes.average())} steps (worst ${randomEpisodes.min()}, best " +
            "${randomEpisodes.max()}). That number is the baseline every cart-pole result is quoted against, " +
            "and it is why the task is a sanity check rather than a challenge.",
        bars = listOf(
            BarSpec("random", randomEpisodes.average().toFloat(), "%.1f".format(randomEpisodes.average()), EnvRed),
            BarSpec("solved (v1)", 500f, "500", EnvGreen),
        ),
        readout = "random policy: ${"%.1f".format(randomEpisodes.average())} of 500 steps",
    )

    // Random search over linear policies — the classic result that cart-pole needs no deep network.
    var best = DoubleArray(4)
    var bestScore = -1.0
    val searchRng = Random(11)
    var evaluations = 0
    repeat(400) {
        val w = DoubleArray(4) { searchRng.nextDouble(-1.0, 1.0) }
        val score = (1..5).map { seed ->
            cartEpisode(Random(seed)) { s ->
                val v = w[0] * s.x + w[1] * s.xDot + w[2] * s.theta + w[3] * s.thetaDot
                if (v > 0) 1 else 0
            }.first
        }.average()
        evaluations += 5
        if (score > bestScore) { bestScore = score; best = w }
    }
    val linear: (CartState) -> Int = { s ->
        val v = best[0] * s.x + best[1] * s.xDot + best[2] * s.theta + best[3] * s.thetaDot
        if (v > 0) 1 else 0
    }
    val (linearSteps, linearTrace) = cartEpisode(Random(99), policy = linear)
    listOf(0, 60, 200, minOf(400, linearTrace.lastIndex)).forEach { i ->
        val s = linearTrace[minOf(i, linearTrace.lastIndex)]
        frames += EnvFrame(
            status = "The same environment under a linear policy — one dot product of the four state numbers, " +
                "found by random search over ${evaluations / 5} candidates. At step $i the pole is at " +
                "${"%.1f".format(Math.toDegrees(s.theta))}° and the cart at ${"%.2f".format(s.x)} m: the " +
                "controller keeps pushing under the pole rather than away from it.",
            scene = Scene.Cart(s.x, s.theta, if (linear(s) == 1) CP_FORCE else -CP_FORCE),
            readout = "step $i · θ = ${"%.1f".format(Math.toDegrees(s.theta))}° · x = ${"%.2f".format(s.x)} m",
        )
    }
    val linearScores = (200..299).map { cartEpisode(Random(it), policy = linear).first }
    frames += EnvFrame(
        status = "Evaluated on 100 fresh starts the linear policy averages " +
            "${"%.1f".format(linearScores.average())} steps against random's " +
            "${"%.1f".format(randomEpisodes.average())}. No neural network, no gradients, no replay buffer — " +
            "four weights. Cart-pole is the environment you use to check that your training loop is wired up, " +
            "not to demonstrate that it is powerful.",
        bars = listOf(
            BarSpec("random", randomEpisodes.average().toFloat(), "%.1f".format(randomEpisodes.average()), EnvRed),
            BarSpec("linear policy", linearScores.average().toFloat(), "%.1f".format(linearScores.average()), EnvGreen),
            BarSpec("cap", 500f, "500", EnvBlue),
        ),
        readout = "linear ${"%.1f".format(linearScores.average())} vs random ${"%.1f".format(randomEpisodes.average())} steps",
    )
    frames += EnvFrame(
        status = "What cart-pole does teach is the shape of the problem: reward is dense (+1 every step), the " +
            "state is fully observed, the dynamics are smooth, and failure is immediate. Change any one of " +
            "those and the same algorithm stops working — which is what the next five environments are for.",
        scene = Scene.Cart(linearTrace.last().x, linearTrace.last().theta, null),
        readout = "linear policy $linearSteps steps · one sampled random episode $randomSteps",
    )
    return frames
}

// ── Mountain car ─────────────────────────────────────────────────────────────
// Gym's MountainCar-v0 dynamics. The point of this environment is that the greedy action is wrong.

private const val MC_MIN_POS = -1.2
private const val MC_MAX_POS = 0.6
private const val MC_MAX_SPEED = 0.07
private const val MC_POWER = 0.001
private const val MC_GRAVITY = 0.0025
private const val MC_GOAL = 0.5

private class CarState(val position: Double, val velocity: Double)

private fun carStep(s: CarState, action: Int): CarState {
    var v = s.velocity + (action - 1) * MC_POWER + cos(3 * s.position) * (-MC_GRAVITY)
    v = v.coerceIn(-MC_MAX_SPEED, MC_MAX_SPEED)
    var p = (s.position + v).coerceIn(MC_MIN_POS, MC_MAX_POS)
    if (p <= MC_MIN_POS && v < 0) v = 0.0
    return CarState(p, v)
}

private fun carRun(start: Double, limit: Int, policy: (CarState) -> Int): Pair<List<CarState>, Boolean> {
    var s = CarState(start, 0.0)
    val trace = mutableListOf(s)
    repeat(limit) {
        if (s.position >= MC_GOAL) return trace to true
        s = carStep(s, policy(s))
        trace += s
    }
    return trace to (s.position >= MC_GOAL)
}

private fun mountainCarFrames(): List<EnvFrame> {
    val frames = mutableListOf<EnvFrame>()
    val start = -0.5
    val limit = 200

    frames += EnvFrame(
        status = "Two numbers of state — position and velocity — and three actions: push left, coast, push " +
            "right. The engine is deliberately too weak to climb the hill directly, and the reward is −1 per " +
            "step until the flag at ${MC_GOAL}. Everything interesting follows from those two facts.",
        scene = Scene.Hill(start, 0.0, MC_GOAL),
        readout = "engine power $MC_POWER · gravity term $MC_GRAVITY",
    )

    val (greedyTrace, greedySolved) = carRun(start, limit) { 2 }
    listOf(0, 30, 80, greedyTrace.lastIndex).forEach { i ->
        val s = greedyTrace[i]
        frames += EnvFrame(
            status = "Always push right — the action that points at the goal. After $i steps the car is at " +
                "${"%.2f".format(s.position)} with velocity ${"%.3f".format(s.velocity)}: it climbs a little, " +
                "stalls, slides back, and settles into a rut. Gravity beats the engine at every point on this " +
                "slope.",
            scene = Scene.Hill(s.position, s.velocity, MC_GOAL),
            readout = "step $i · position ${"%.2f".format(s.position)} · best so far ${"%.2f".format(greedyTrace.take(i + 1).maxOf { it.position })}",
        )
    }
    frames += EnvFrame(
        status = "After $limit steps of the greedy action the car has never got past " +
            "${"%.2f".format(greedyTrace.maxOf { it.position })}, ${if (greedySolved) "yet still reached" else "well short of"} " +
            "the flag at $MC_GOAL. The action that maximises immediate progress is exactly the action that " +
            "guarantees failure — this environment is a counterexample to greedy control, not a hard control " +
            "problem.",
        scene = Scene.Hill(greedyTrace.last().position, greedyTrace.last().velocity, MC_GOAL),
        readout = "max position ${"%.2f".format(greedyTrace.maxOf { it.position })} of $MC_GOAL — failed",
    )

    val (pumpTrace, pumpSolved) = carRun(start, limit) { s -> if (s.velocity >= 0) 2 else 0 }
    listOf(0, 25, 60, 90, pumpTrace.lastIndex).forEach { i ->
        val s = pumpTrace[minOf(i, pumpTrace.lastIndex)]
        frames += EnvFrame(
            status = "Now push in whatever direction the car is already moving — pump energy in rather than aim " +
                "at the goal. At step $i it is at ${"%.2f".format(s.position)} with velocity " +
                "${"%.3f".format(s.velocity)}; the car deliberately climbs the wrong hill first to buy the " +
                "height it needs.",
            scene = Scene.Hill(s.position, s.velocity, MC_GOAL),
            readout = "step $i · position ${"%.2f".format(s.position)} · velocity ${"%.3f".format(s.velocity)}",
        )
    }
    frames += EnvFrame(
        status = "The energy-pumping policy reaches the flag in ${pumpTrace.size - 1} steps — " +
            "${if (pumpSolved) "solved" else "not solved"} — using an action rule that is wrong at almost " +
            "every individual step if you judge it by immediate progress. This is what \"delayed reward\" " +
            "means concretely.",
        scene = Scene.Hill(pumpTrace.last().position, pumpTrace.last().velocity, MC_GOAL),
        bars = listOf(
            BarSpec("greedy", greedyTrace.maxOf { it.position }.toFloat(), "%.2f".format(greedyTrace.maxOf { it.position }), EnvRed),
            BarSpec("energy pump", pumpTrace.maxOf { it.position }.toFloat(), "%.2f".format(pumpTrace.maxOf { it.position }), EnvGreen),
            BarSpec("flag", MC_GOAL.toFloat(), "$MC_GOAL", EnvBlue),
        ),
        readout = "solved in ${pumpTrace.size - 1} steps",
    )

    var successes = 0
    var bestRandom = MC_MIN_POS
    val rng = Random(5)
    repeat(300) {
        val (trace, solved) = carRun(rng.nextDouble(-0.6, -0.4), limit) { rng.nextInt(3) }
        if (solved) successes++
        bestRandom = maxOf(bestRandom, trace.maxOf { it.position })
    }
    frames += EnvFrame(
        status = "And the reason this environment is a standard exploration benchmark: over 300 random " +
            "episodes of $limit steps, $successes reached the flag — the best any of them got was " +
            "${"%.2f".format(bestRandom)}. With −1 per step and no reward gradient to follow, an agent that " +
            "explores by acting randomly never sees a single success to learn from, which is why " +
            "ε-greedy Q-learning stalls here and count-based or curiosity bonuses do not.",
        scene = Scene.Hill(bestRandom, 0.0, MC_GOAL),
        readout = "random: $successes / 300 episodes reached the goal",
    )
    return frames
}

// ── MuJoCo-style continuous control ──────────────────────────────────────────
// A torque-limited pendulum, integrated the way Gym's Pendulum-v1 does. Same lesson as a MuJoCo
// humanoid at 1/1000 the size: continuous torques, a limit that forbids the naive solution, and a
// cost that is shaped rather than sparse.

private const val PEND_G = 10.0
private const val PEND_M = 1.0
private const val PEND_L = 1.0
private const val PEND_DT = 0.05
private const val PEND_MAX_TORQUE = 2.0
private const val PEND_MAX_SPEED = 8.0

private class PendState(val theta: Double, val thetaDot: Double)

private fun pendStep(s: PendState, torque: Double): PendState {
    val u = torque.coerceIn(-PEND_MAX_TORQUE, PEND_MAX_TORQUE)
    val acc = 3 * PEND_G / (2 * PEND_L) * sin(s.theta) + 3.0 / (PEND_M * PEND_L * PEND_L) * u
    var newDot = (s.thetaDot + acc * PEND_DT).coerceIn(-PEND_MAX_SPEED, PEND_MAX_SPEED)
    return PendState(s.theta + newDot * PEND_DT, newDot)
}

private fun angleNorm(a: Double): Double {
    var x = a
    while (x > Math.PI) x -= 2 * Math.PI
    while (x < -Math.PI) x += 2 * Math.PI
    return x
}

private fun mujocoFrames(): List<EnvFrame> {
    val frames = mutableListOf<EnvFrame>()
    // Gym's convention, which these dynamics are: theta = 0 is upright (unstable), ±π hangs down.
    // "Distance from upright" is therefore |angleNorm(theta)|, and the arm starts hanging.
    val start = PendState(Math.PI, 0.0)
    fun fromUpright(st: PendState) = Math.toDegrees(abs(angleNorm(st.theta)))

    frames += EnvFrame(
        status = "A single torque-controlled joint, integrated at ${(1 / PEND_DT).toInt()} Hz — the smallest " +
            "honest stand-in for a MuJoCo robot. The action is a real number in " +
            "[−$PEND_MAX_TORQUE, $PEND_MAX_TORQUE] N·m, not a choice from a menu, and gravity needs " +
            "${"%.1f".format(PEND_M * PEND_G * PEND_L / 2)} N·m to hold the arm horizontal. The naive " +
            "solution is not available: max torque is less than the torque required to lift.",
        scene = Scene.Pendulum(start.theta, 0.0, PEND_MAX_TORQUE),
        readout = "torque limit ±$PEND_MAX_TORQUE N·m · gravity peak ${"%.1f".format(PEND_M * PEND_G * PEND_L / 2)} N·m",
    )

    var s = start
    var closest = 180.0
    repeat(200) {
        s = pendStep(s, PEND_MAX_TORQUE)
        closest = minOf(closest, fromUpright(s))
    }
    frames += EnvFrame(
        status = "Hold the torque at its maximum for 200 steps and the arm never gets closer than " +
            "${"%.0f".format(closest)}° from upright — it stalls where gravity's moment overtakes the motor " +
            "and falls back. Pushing harder is not an option, so the policy has to be cleverer rather than " +
            "stronger. This is the defining feature of torque-limited control.",
        scene = Scene.Pendulum(s.theta, PEND_MAX_TORQUE, PEND_MAX_TORQUE),
        readout = "closest approach ${"%.0f".format(closest)}° from upright — never arrives",
    )

    // Energy shaping: drive the total energy to what upright-at-rest requires, then let it arrive.
    val inertia = PEND_M * PEND_L * PEND_L / 3
    val targetEnergy = PEND_M * PEND_G * (PEND_L / 2)
    fun energy(st: PendState) = 0.5 * inertia * st.thetaDot * st.thetaDot +
        PEND_M * PEND_G * (PEND_L / 2) * cos(st.theta)
    // Two controllers, which is how swing-up is actually done: pump energy until the arm is near the
    // top, then catch it with a PD law. The catch region has to be small — beyond about 23° gravity's
    // moment exceeds anything the motor can answer with.
    fun nearTop(st: PendState) = abs(angleNorm(st.theta)) < 0.35
    fun shapedTorque(st: PendState) = if (nearTop(st)) {
        (-(6.0 * angleNorm(st.theta) + 1.2 * st.thetaDot)).coerceIn(-PEND_MAX_TORQUE, PEND_MAX_TORQUE)
    } else {
        (-1.0 * (energy(st) - targetEnergy) * st.thetaDot).coerceIn(-PEND_MAX_TORQUE, PEND_MAX_TORQUE)
    }

    var swing = start
    val trace = mutableListOf(swing)
    val torques = mutableListOf(0.0)
    var reversals = 0
    var lastSign = 0
    var upAt = -1
    repeat(600) { step ->
        val u = shapedTorque(swing)
        swing = pendStep(swing, u)
        trace += swing
        torques += u
        val sign = if (swing.thetaDot >= 0) 1 else -1
        if (lastSign != 0 && sign != lastSign && upAt < 0) reversals++
        lastSign = sign
        if (upAt < 0 && fromUpright(swing) < 15.0) upAt = step
    }
    val settled = fromUpright(trace.last())
    val marks = if (upAt > 0) listOf(0, upAt / 4, upAt / 2, upAt * 3 / 4, upAt) else listOf(0, 100, 250, 400, 599)
    marks.forEach { i ->
        val idx = minOf(i, trace.lastIndex)
        val st = trace[idx]
        frames += EnvFrame(
            status = "Energy shaping instead: push in the direction the arm is already swinging, scaled by how " +
                "far its energy is from what upright requires. At step $i the arm is " +
                "${"%.0f".format(fromUpright(st))}° from upright with speed ${"%.1f".format(st.thetaDot)} " +
                "rad/s and ${"%.2f".format(torques[idx])} N·m applied — it spends most of that torque swinging " +
                "away from the goal, because momentum is the only way up.",
            scene = Scene.Pendulum(st.theta, torques[idx], PEND_MAX_TORQUE),
            readout = "step $i · ${"%.0f".format(fromUpright(st))}° from upright · ω ${"%.1f".format(st.thetaDot)} rad/s",
        )
    }
    frames += EnvFrame(
        status = if (upAt >= 0) {
            "The arm reaches within 15° of upright at step $upAt after $reversals direction reversals, and " +
                "holds at ${"%.0f".format(settled)}° once the PD catch takes over inside 20°. Two controllers " +
                "were needed — one to add energy, one to hold — and the switch point is set by physics, not " +
                "taste: past 23° gravity's moment exceeds anything ±$PEND_MAX_TORQUE N·m can answer. A learner " +
                "has to discover both halves from a cost that only says \"be upright\"."
        } else {
            "The arm never came within 15° of upright in 400 steps despite $reversals reversals — this gain is " +
                "not enough, which is itself the point: hand-tuned controllers are brittle, and that " +
                "brittleness is why these tasks get handed to learners."
        },
        scene = Scene.Pendulum(trace[minOf(maxOf(upAt, 0), trace.lastIndex)].theta, 0.0, PEND_MAX_TORQUE),
        readout = "${if (upAt >= 0) "reaches upright at step $upAt" else "never upright"} · $reversals reversals",
    )

    val dims = listOf(1, 6, 17, 21)
    frames += EnvFrame(
        status = "Scaling this up is where MuJoCo's real difficulty lives, and it is combinatorial. Discretising " +
            "each joint into 10 torque levels costs 10^d actions: " +
            dims.joinToString(", ") { d -> "$d joint${if (d == 1) "" else "s"} → ${"%.0e".format(Math.pow(10.0, d.toDouble()))}" } +
            ". A 21-joint humanoid has more discrete actions than there are atoms in a person, which is why " +
            "continuous-action methods (DDPG, SAC, PPO with a Gaussian head) exist at all — DQN cannot be " +
            "pointed at this.",
        bars = dims.map { d ->
            BarSpec("$d dof", d.toFloat(), "10^$d actions", if (d == 21) EnvRed else EnvBlue)
        },
        readout = "discretisation is not an option above a few joints",
    )
    return frames
}

// ── Atari: the frame-stacking lab ────────────────────────────────────────────
// No emulator here, and the lab says so. What it does run is the property that makes raw Atari
// frames insufficient: one frame has no velocity in it, so the optimal policy is unreachable.

private fun atariFrames(): List<EnvFrame> {
    val frames = mutableListOf<EnvFrame>()
    val width = 8

    // Toy catch game: a ball at a column moves left or right by one each step and bounces off the
    // walls; the paddle moves one cell per step and must be under the ball when it lands.
    class Catch(val ball: Int, val dir: Int, val paddle: Int, val height: Int)

    fun step(g: Catch, action: Int): Catch {
        var dir = g.dir
        var ball = g.ball + dir
        if (ball < 0) { ball = 1; dir = 1 }
        if (ball >= width) { ball = width - 2; dir = -1 }
        val paddle = (g.paddle + action - 1).coerceIn(0, width - 1)
        return Catch(ball, dir, paddle, g.height - 1)
    }

    fun render(g: Catch): List<List<Float>> = (0 until 4).map { row ->
        (0 until width).map { col ->
            when {
                row == 3 && col == g.paddle -> 1f
                row == 3 - g.height / 2 && col == g.ball -> 0.75f
                else -> 0f
            }
        }
    }

    var demo = Catch(2, 1, 4, 6)
    frames += EnvFrame(
        status = "This is not an Atari emulator — a phone cannot run one, and pretending otherwise would be " +
            "dishonest. It is the property that makes Atari frames hard: an 8-wide catch game where the ball " +
            "moves one cell per step and the paddle must be under it when it lands.",
        scene = Scene.Pixels(render(demo), "one frame — where is the ball going?"),
        readout = "8×4 grid · 3 actions · reward on catch",
    )
    val after = step(demo, 1)
    frames += EnvFrame(
        status = "Here is the same scene one step later. Only by comparing the two frames can you tell the ball " +
            "is moving " + (if (after.ball > demo.ball) "right" else "left") + ". A single frame is a position " +
            "with no velocity in it — exactly the situation DQN faced with raw Atari screens, and the reason " +
            "the published agent stacks 4 frames into one observation.",
        scene = Scene.Pixels(render(after), "the next frame — now the direction is visible"),
        readout = "ball ${demo.ball} → ${after.ball}",
    )

    // Tabular Q-learning under two observation functions: position only, and position + previous position.
    fun train(stacked: Boolean, episodes: Int, seed: Int): Double {
        val rng = Random(seed)
        val q = HashMap<Int, DoubleArray>()
        fun key(g: Catch, prevBall: Int): Int =
            if (stacked) ((g.ball * width + prevBall) * width + g.paddle) * 8 + g.height
            else (g.ball * width + g.paddle) * 8 + g.height
        var caught = 0
        val evalFrom = episodes * 3 / 4
        repeat(episodes) { ep ->
            var g = Catch(rng.nextInt(width), if (rng.nextBoolean()) 1 else -1, rng.nextInt(width), 6)
            var prev = g.ball
            val epsilon = if (ep >= evalFrom) 0.0 else 0.2
            while (g.height > 0) {
                val k = key(g, prev)
                val row = q.getOrPut(k) { DoubleArray(3) }
                val a = if (rng.nextDouble() < epsilon) rng.nextInt(3) else row.indices.maxBy { row[it] }
                val prevBall = g.ball
                val next = step(g, a)
                val reward = if (next.height == 0) (if (next.paddle == next.ball) 1.0 else 0.0) else 0.0
                val nextRow = q.getOrPut(key(next, prevBall)) { DoubleArray(3) }
                val target = reward + if (next.height == 0) 0.0 else 0.9 * nextRow.max()
                row[a] += 0.2 * (target - row[a])
                prev = prevBall
                g = next
            }
            if (ep >= evalFrom && g.paddle == g.ball) caught++
        }
        return caught.toDouble() / (episodes - evalFrom)
    }

    val single = (1..5).map { train(stacked = false, episodes = 20000, seed = it) }.average()
    val stacked = (1..5).map { train(stacked = true, episodes = 20000, seed = it) }.average()
    frames += EnvFrame(
        status = "Both observations trained with identical tabular Q-learning, 20,000 episodes, five seeds, " +
            "then evaluated greedily. Position-only catches ${"%.0f".format(single * 100)}% of balls; position " +
            "plus the previous position catches ${"%.0f".format(stacked * 100)}%. The gap is not a tuning " +
            "problem — the first agent is being asked to act on a state that genuinely does not determine the " +
            "answer.",
        bars = listOf(
            BarSpec("1 frame", (single * 100).toFloat(), "${"%.0f".format(single * 100)}%", EnvRed),
            BarSpec("2 frames", (stacked * 100).toFloat(), "${"%.0f".format(stacked * 100)}%", EnvGreen),
        ),
        readout = "catch rate: ${"%.0f".format(single * 100)}% vs ${"%.0f".format(stacked * 100)}%",
    )

    val rawBytes = 210 * 160 * 3
    val processed = 84 * 84
    frames += EnvFrame(
        status = "The rest of the Atari pipeline is bookkeeping with real consequences: a raw frame is " +
            "210×160×3 = $rawBytes bytes; the standard preprocessing takes greyscale, downsamples to 84×84 " +
            "($processed bytes, ${"%.1f".format(rawBytes.toDouble() / processed)}× smaller), stacks 4 of them, " +
            "and repeats each action for 4 emulator frames. A 1M-transition replay buffer is " +
            "${"%.1f".format(1_000_000.0 * processed * 4 / 1e9)} GB stored naively, which is why " +
            "implementations share frames between neighbouring stacks.",
        scene = Scene.Pixels(render(after), "84×84×4 is what the network actually sees"),
        readout = "$rawBytes B → $processed B per frame · ×4 stack",
    )
    frames += EnvFrame(
        status = "Two more properties make the suite what it is: the 57 games share one action interface and " +
            "one observation format, so a single agent can be evaluated across all of them without per-game " +
            "engineering — and scores are reported against a human baseline because raw game points are not " +
            "comparable between games. Atari is a benchmark design, not just a set of games.",
        scene = Scene.Pixels(render(demo), "same interface, 57 games"),
        readout = "shared interface · human-normalised scoring",
    )
    return frames
}

// ── Dota 2: horizon and action space ─────────────────────────────────────────

private fun dotaFrames(): List<EnvFrame> {
    val frames = mutableListOf<EnvFrame>()

    val components = listOf("action type" to 8, "target unit" to 190, "offset x" to 9, "offset y" to 9, "delay" to 6)
    val product = components.fold(1L) { acc, (_, n) -> acc * n }
    frames += EnvFrame(
        status = "OpenAI Five's action is not one choice but several made together — " +
            components.joinToString(", ") { (name, n) -> "$name ($n)" } +
            " — so the joint action space is their product: ${"%,d".format(product)} per decision. A flat " +
            "softmax over that is unusable; the policy factorises the head and picks each component " +
            "conditionally, which is the only reason the output layer fits in memory.",
        bars = components.map { (name, n) -> BarSpec(name, n.toFloat(), n.toString(), EnvBlue) },
        readout = "joint action space ≈ ${"%,d".format(product)}",
    )

    val ticks = 45 * 60 * 30 / 4
    frames += EnvFrame(
        status = "The horizon is the other wall. A 45-minute match at 30 ticks per second with one decision " +
            "every 4 ticks is about ${"%,d".format(ticks)} decisions in a single episode, and the thing you " +
            "want to reward — winning — happens once, at the end. Cart-pole told you within 20 steps whether " +
            "you were wrong.",
        bars = listOf(
            BarSpec("cart-pole", 500f, "500", EnvGreen),
            BarSpec("Atari episode", 27000f, "~27k", EnvAmber),
            BarSpec("Dota match", ticks.toFloat(), "~${"%,d".format(ticks)}", EnvRed),
        ),
        readout = "one terminal reward across ${"%,d".format(ticks)} decisions",
    )

    // What a discount factor actually buys, measured on a chain with the win at the far end and a
    // dense proxy reward available immediately — the farming-versus-winning trade in miniature.
    val chain = 40
    val proxy = 0.05
    // Both actions terminate the episode eventually, so nothing here depends on how a time limit is
    // bootstrapped — the only thing separating the two options is how far away the big reward is.
    fun trainChain(gamma: Double, episodes: Int, seed: Int): Triple<Double, Double, Double> {
        val rng = Random(seed)
        val q = Array(chain + 1) { DoubleArray(2) }
        var wins = 0
        val evalFrom = episodes * 3 / 4
        repeat(episodes) { ep ->
            var s = 0
            var won = false
            val epsilon = if (ep >= evalFrom) 0.0 else 0.5
            while (true) {
                val a = if (rng.nextDouble() < epsilon) rng.nextInt(2) else if (q[s][1] >= q[s][0]) 1 else 0
                // The choice exists only at the start: cash the proxy out now, or commit to the walk.
                // Past that the march to the win is forced, so the far reward is always reachable and
                // what the three runs differ on is purely how much the discount says it is worth.
                val cashOut = a == 0 && s == 0
                val next = if (cashOut) s else s + 1
                val terminal = cashOut || next == chain
                val reward = if (cashOut) proxy else if (next == chain) 1.0 else 0.0
                val target = reward + if (terminal) 0.0 else gamma * q[next].max()
                q[s][a] += 0.2 * (target - q[s][a])
                if (terminal) { won = !cashOut; break }
                s = next
            }
            if (ep >= evalFrom && won) wins++
        }
        return Triple(q[0][1], q[0][0], wins.toDouble() / (episodes - evalFrom))
    }
    val gammas = listOf(0.9, 0.99, 0.999)
    val results = gammas.map { g -> Triple(g, trainChain(g, 20000, 3), 1.0 / (1 - g)) }
    frames += EnvFrame(
        status = "Measured on a ${chain}-step chain: the win is worth 1.0 and sits $chain steps away, while a " +
            "proxy worth $proxy — a last hit, a small objective — can be taken immediately. Trained " +
            "identically at three discount factors: " +
            results.joinToString("; ") { (g, res, horizon) ->
                "γ=$g (horizon ${"%.0f".format(horizon)}) → push ${"%.3f".format(res.first)} vs cash out " +
                    "${"%.3f".format(res.second)}, wins ${"%.0f".format(res.third * 100)}%"
            } +
            ". A discount is a horizon: 1/(1−γ). Shorter than the distance to the win, and the far reward is " +
            "worth less than the near one — the agent that takes the small reward is not being greedy, it is " +
            "being correct about the objective you actually gave it.",
        curve = CurveSpec(
            series = listOf(
                Series("value of pushing for the win", EnvGreen, results.map { it.second.first.toFloat() }),
                Series("value of the immediate proxy", EnvRed, results.map { it.second.second.toFloat() }),
            ),
            yMax = maxOf(0.05f, results.maxOf { maxOf(it.second.first, it.second.second).toFloat() }),
            yLabel = "learned value at the start state",
            xLabel = "γ = 0.9 · 0.99 · 0.999",
        ),
        readout = results.joinToString(" · ") { (g, res, _) -> "γ=$g wins ${"%.0f".format(res.third * 100)}%" },
    )
    frames += EnvFrame(
        status = "OpenAI Five's γ was annealed from 0.998 to 0.9997 during training — an effective horizon " +
            "growing from about ${"%.0f".format(1 / (1 - 0.998))} to ${"%.0f".format(1 / (1 - 0.9997))} " +
            "decisions, roughly 6 minutes of game time. Even that does not span a match, so the reward " +
            "function was shaped with dense proxies (last hits, kills, tower damage). Those carry the " +
            "opposite risk to the frame before: a proxy that keeps paying out beats a one-off win at any " +
            "long horizon, which is why the shaped terms were decayed towards zero as training went on.",
        bars = listOf(
            BarSpec("γ=0.998", (1 / (1 - 0.998)).toFloat(), "${"%.0f".format(1 / (1 - 0.998))} steps", EnvAmber),
            BarSpec("γ=0.9997", (1 / (1 - 0.9997)).toFloat(), "${"%.0f".format(1 / (1 - 0.9997))} steps", EnvGreen),
            BarSpec("match", ticks.toFloat(), "${"%,d".format(ticks)} steps", EnvRed),
        ),
        readout = "even the longest horizon is ${"%.0f".format(ticks / (1 / (1 - 0.9997)))}× shorter than a match",
    )
    frames += EnvFrame(
        status = "The last property is partial observability: each hero sees a fog-limited slice of the map, so " +
            "the five policies share weights but act on different observations, and an LSTM carries what has " +
            "been seen. Add self-play, 180 years of simulated game time per day, and the honest summary is " +
            "that the algorithm (PPO) was ordinary — the engineering around the environment was not.",
        readout = "PPO + LSTM + self-play at 180 game-years/day",
    )
    return frames
}

// ── StarCraft II: non-transitivity and the league ────────────────────────────

private fun starcraftFrames(): List<EnvFrame> {
    val frames = mutableListOf<EnvFrame>()
    val names = listOf("rush", "expand", "defend")
    // A cyclic strategy space: rush beats expand, expand beats defend, defend beats rush.
    val payoff = listOf(
        listOf(0.0, 1.0, -1.0),
        listOf(-1.0, 0.0, 1.0),
        listOf(1.0, -1.0, 0.0),
    )

    frames += EnvFrame(
        status = "StarCraft's strategy space is not a ladder, it is a cycle: a rush beats a greedy expansion, " +
            "expanding beats a defensive build, and defending beats the rush. There is no single best " +
            "strategy to converge on — which breaks the usual assumption that training against your current " +
            "best opponent makes you better.",
        matrix = MatrixSpec(names, names, payoff),
        readout = "non-transitive: no strategy beats all others",
    )

    fun bestResponse(mix: DoubleArray): Int =
        names.indices.maxBy { i -> names.indices.sumOf { j -> payoff[i][j] * mix[j] } }

    fun exploitability(mix: DoubleArray): Double =
        names.indices.maxOf { i -> names.indices.sumOf { j -> payoff[i][j] * mix[j] } }

    // Naive self-play: always best-respond to the opponent's latest policy.
    var current = 0
    val cycle = mutableListOf(current)
    val selfPlayExploit = mutableListOf<Float>()
    repeat(12) {
        val mix = DoubleArray(3).also { it[current] = 1.0 }
        selfPlayExploit += exploitability(mix).toFloat()
        current = bestResponse(mix)
        cycle += current
    }
    frames += EnvFrame(
        status = "Naive self-play, best-responding to the latest opponent: " +
            cycle.take(7).joinToString(" → ") { names[it] } + " … and it keeps going round. Every policy in " +
            "the sequence is beaten outright by the next one — exploitability stays at " +
            "${"%.1f".format(selfPlayExploit.max())} forever. The agent is not improving, it is orbiting.",
        matrix = MatrixSpec(names, names, payoff, focus = cycle[0] to cycle[1]),
        curve = CurveSpec(
            listOf(Series("self-play", EnvRed, selfPlayExploit)),
            yMax = 1.1f,
            yLabel = "exploitability (1.0 = beaten by a counter every time)",
            xLabel = "iteration",
        ),
        readout = "self-play exploitability stays ${"%.1f".format(selfPlayExploit.last())}",
    )

    // Fictitious play: best-respond to the average of everything seen so far — the league idea.
    val counts = DoubleArray(3)
    counts[0] = 1.0
    val leagueExploit = mutableListOf<Float>()
    var total = 1.0
    repeat(200) {
        val mix = DoubleArray(3) { counts[it] / total }
        leagueExploit += exploitability(mix).toFloat()
        val br = bestResponse(mix)
        counts[br] += 1.0
        total += 1.0
    }
    val finalMix = DoubleArray(3) { counts[it] / total }
    frames += EnvFrame(
        status = "Now best-respond to the whole history rather than the latest opponent — the idea behind " +
            "AlphaStar's league. Exploitability falls from ${"%.2f".format(leagueExploit.first())} to " +
            "${"%.2f".format(leagueExploit.last())} over 200 iterations, and the average policy converges " +
            "towards the mixture " + finalMix.indices.joinToString(", ") { "${names[it]} ${"%.2f".format(finalMix[it])}" } +
            " — the Nash equilibrium of this game is a third each, which is what the numbers are approaching.",
        matrix = MatrixSpec(names, names, payoff),
        curve = CurveSpec(
            listOf(
                Series("league / fictitious play", EnvGreen, leagueExploit),
                Series("self-play", EnvRed, List(leagueExploit.size) { selfPlayExploit.max() }),
            ),
            yMax = 1.1f,
            yLabel = "exploitability of the current policy",
            xLabel = "iteration",
        ),
        readout = "league ${"%.2f".format(leagueExploit.last())} vs self-play ${"%.2f".format(selfPlayExploit.max())}",
    )
    frames += EnvFrame(
        status = "AlphaStar's league added one more thing this toy cannot show: exploiter agents trained " +
            "specifically to beat the main agent, whose job is to find its blind spots rather than to be good " +
            "at the game. The main agent then has to fix what they expose — a curriculum generated by " +
            "adversaries instead of by hand.",
        matrix = MatrixSpec(names, names, payoff),
        readout = "main agents + league exploiters + main exploiters",
    )

    val apmLimit = 22
    val obsPixels = 128 * 128
    frames += EnvFrame(
        status = "The rest of what makes StarCraft hard is scale and fairness constraints, both real: the " +
            "observation is a stack of 128×128 feature layers (${"%,d".format(obsPixels)} cells each) plus " +
            "lists of units, the action is " +
            "again factorised (what, where, which unit, queued or not), the horizon runs to tens of thousands " +
            "of decisions, and AlphaStar was rate-limited to about $apmLimit actions per 5 seconds so it could " +
            "not win by mechanical speed alone. Removing that limit makes the benchmark meaningless.",
        readout = "${obsPixels}-px feature layers · ~$apmLimit actions / 5 s cap",
    )
    return frames
}

// ── Config ───────────────────────────────────────────────────────────────────

private val envConfigs = mapOf(
    "cartpole" to EnvConfig(
        intro = "Gym's CartPole-v1 dynamics, integrated here at 50 Hz. Random actions first, then a four-weight " +
            "linear policy found by random search — both scored over real episodes.",
        build = ::cartPoleFrames,
    ),
    "mountain_car" to EnvConfig(
        intro = "MountainCar-v0's real dynamics. The greedy action fails on purpose; the policy that works looks " +
            "wrong at nearly every step. The last frame measures why random exploration cannot solve it.",
        build = ::mountainCarFrames,
    ),
    "mujoco" to EnvConfig(
        intro = "A torque-limited joint integrated the way MuJoCo tasks are: continuous actions, a limit that " +
            "rules out the naive solution, and an action space that explodes once you add joints.",
        build = ::mujocoFrames,
    ),
    "atari" to EnvConfig(
        intro = "No emulator on a phone — so this lab runs the property that made raw Atari frames insufficient, " +
            "and measures what stacking frames actually buys.",
        build = ::atariFrames,
    ),
    "dota2" to EnvConfig(
        intro = "The two walls that define Dota as an RL problem — a factorised action space and an episode " +
            "tens of thousands of decisions long — with the discount-factor claim measured on a chain MDP.",
        build = ::dotaFrames,
    ),
    "starcraft" to EnvConfig(
        intro = "Why self-play alone fails on a non-transitive strategy space, measured: best-responding to the " +
            "latest opponent cycles forever, best-responding to the whole history converges.",
        build = ::starcraftFrames,
    ),
)

private fun envConfigFor(topicId: String): EnvConfig = envConfigs[topicId] ?: envConfigs.getValue("cartpole")

// ── UI ───────────────────────────────────────────────────────────────────────

@Composable
fun EnvironmentSection(topicId: String) {
    val config = remember(topicId) { envConfigFor(topicId) }
    val frames = remember(config) { config.build() }
    val playback = rememberPlaybackState(key = config, stepCount = frames.size, initialSpeedMs = 900f)
    val frame = frames[playback.index.coerceIn(0, frames.lastIndex)]

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            LabIntro(config.intro)

            frame.scene?.let { SceneCanvas(it) }
            frame.matrix?.let { PayoffMatrix(it) }
            frame.curve?.let { CurvePlot(it) }
            if (frame.bars.isNotEmpty()) BarPanel(frame.bars)

            frame.readout?.let { ReadoutChips(it, Modifier.padding(top = 14.dp)) }
            LabCaption(frame.status, Modifier.padding(top = 14.dp))

            PlaybackTransport(playback, captions = frames.map { it.status })
        }
    }
}

@Composable
private fun SceneCanvas(scene: Scene) {
    val textMeasurer = rememberTextMeasurer()
    val captionStyle = TextStyle(color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
    val trackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
    val bodyColor = MaterialTheme.colorScheme.onSurface

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
            .padding(8.dp),
    ) {
        Canvas(modifier = Modifier.fillMaxWidth().height(if (scene is Scene.Pixels) 150.dp else 170.dp)) {
            when (scene) {
                is Scene.Cart -> {
                    val groundY = size.height * 0.72f
                    val inset = 16.dp.toPx()
                    drawLine(trackColor, Offset(inset, groundY), Offset(size.width - inset, groundY), strokeWidth = 3.dp.toPx())
                    val cx = size.width / 2f + (scene.x / CP_X_LIMIT).toFloat() * (size.width / 2f - 60.dp.toPx())
                    val cartW = 60.dp.toPx()
                    val cartH = 26.dp.toPx()
                    drawRoundRect(
                        color = EnvBlue,
                        topLeft = Offset(cx - cartW / 2f, groundY - cartH),
                        size = Size(cartW, cartH),
                        cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx()),
                    )
                    val poleLen = size.height * 0.45f
                    val tip = Offset(
                        cx + poleLen * sin(scene.theta).toFloat(),
                        groundY - cartH - poleLen * cos(scene.theta).toFloat(),
                    )
                    val failing = abs(scene.theta) > CP_THETA_LIMIT * 0.75
                    drawLine(
                        if (failing) EnvRed else EnvGreen,
                        Offset(cx, groundY - cartH),
                        tip,
                        strokeWidth = 8.dp.toPx(),
                    )
                    drawCircle(EnvAmber, radius = 7.dp.toPx(), center = tip)
                    scene.force?.let { f ->
                        val dir = if (f > 0) 1f else -1f
                        drawLine(EnvViolet, Offset(cx, groundY + 14.dp.toPx()), Offset(cx + dir * 34.dp.toPx(), groundY + 14.dp.toPx()), strokeWidth = 5.dp.toPx())
                    }
                }

                is Scene.Hill -> {
                    fun heightAt(p: Double) = sin(3 * p) * 0.45 + 0.55
                    val path = Path()
                    val steps = 60
                    for (i in 0..steps) {
                        val p = MC_MIN_POS + (MC_MAX_POS - MC_MIN_POS) * i / steps
                        val x = 16.dp.toPx() + (i.toFloat() / steps) * (size.width - 32.dp.toPx())
                        val y = size.height * (1f - heightAt(p).toFloat() * 0.8f) - 6.dp.toPx()
                        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    drawPath(path, trackColor, style = Stroke(width = 3.dp.toPx()))
                    fun screenOf(p: Double): Offset {
                        val t = ((p - MC_MIN_POS) / (MC_MAX_POS - MC_MIN_POS)).toFloat()
                        return Offset(16.dp.toPx() + t * (size.width - 32.dp.toPx()), size.height * (1f - heightAt(p).toFloat() * 0.8f) - 6.dp.toPx())
                    }
                    val goal = screenOf(scene.goal)
                    drawLine(EnvGreen, goal, Offset(goal.x, goal.y - 34.dp.toPx()), strokeWidth = 3.dp.toPx())
                    drawRoundRect(
                        EnvGreen,
                        topLeft = Offset(goal.x, goal.y - 34.dp.toPx()),
                        size = Size(20.dp.toPx(), 12.dp.toPx()),
                        cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()),
                    )
                    val car = screenOf(scene.position)
                    drawCircle(if (scene.position >= scene.goal) EnvGreen else EnvBlue, radius = 11.dp.toPx(), center = Offset(car.x, car.y - 11.dp.toPx()))
                    val vLen = (scene.velocity / MC_MAX_SPEED).toFloat() * 40.dp.toPx()
                    drawLine(EnvAmber, Offset(car.x, car.y - 11.dp.toPx()), Offset(car.x + vLen, car.y - 11.dp.toPx()), strokeWidth = 4.dp.toPx())
                }

                is Scene.Pendulum -> {
                    val pivot = Offset(size.width / 2f, size.height * 0.5f)
                    val len = size.height * 0.34f
                    // Gym's convention: theta = 0 is upright, ±pi hangs. Canvas y grows downwards, so
                    // the vertical term is negated to keep theta = 0 pointing at the top of the screen.
                    val tip = Offset(
                        pivot.x + len * sin(scene.theta).toFloat(),
                        pivot.y - len * cos(scene.theta).toFloat(),
                    )
                    drawCircle(trackColor, radius = len, center = pivot, style = Stroke(width = 1.5.dp.toPx()))
                    val upright = abs(angleNorm(scene.theta)) < 0.25
                    drawLine(if (upright) EnvGreen else EnvBlue, pivot, tip, strokeWidth = 8.dp.toPx())
                    drawCircle(EnvAmber, radius = 10.dp.toPx(), center = tip)
                    drawCircle(bodyColor, radius = 5.dp.toPx(), center = pivot)
                    val barW = (scene.torque / scene.torqueLimit).toFloat() * (size.width * 0.28f)
                    drawLine(
                        EnvViolet,
                        Offset(size.width / 2f, size.height - 14.dp.toPx()),
                        Offset(size.width / 2f + barW, size.height - 14.dp.toPx()),
                        strokeWidth = 6.dp.toPx(),
                    )
                    val lay = textMeasurer.measure("torque ${"%.2f".format(scene.torque)} N·m", captionStyle)
                    drawText(lay, topLeft = Offset(12f, size.height - 30f))
                }

                is Scene.Pixels -> {
                    val rows = scene.rows.size
                    val cols = scene.rows.first().size
                    val cell = minOf((size.width - 32f) / cols, (size.height - 40f) / rows)
                    val originX = (size.width - cell * cols) / 2f
                    val originY = 8f
                    scene.rows.forEachIndexed { r, row ->
                        row.forEachIndexed { c, v ->
                            drawRoundRect(
                                color = when {
                                    v >= 0.9f -> EnvGreen
                                    v > 0f -> EnvAmber
                                    else -> trackColor.copy(alpha = 0.25f)
                                },
                                topLeft = Offset(originX + c * cell + 2f, originY + r * cell + 2f),
                                size = Size(cell - 4f, cell - 4f),
                                cornerRadius = CornerRadius(4f, 4f),
                            )
                        }
                    }
                    val lay = textMeasurer.measure(scene.caption, captionStyle)
                    drawText(lay, topLeft = Offset(originX, originY + rows * cell + 6f))
                }
            }
        }
    }
}

@Composable
private fun BarPanel(bars: List<BarSpec>) {
    val maxValue = bars.maxOf { it.value }.coerceAtLeast(1f)
    Column(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
        bars.forEach { bar ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    bar.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(0.32f),
                )
                Box(modifier = Modifier.weight(0.48f)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(5.dp)),
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth((bar.value / maxValue).coerceIn(0.02f, 1f))
                            .height(10.dp)
                            .background(bar.color, RoundedCornerShape(5.dp)),
                    )
                }
                Text(
                    bar.display,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(0.24f).padding(start = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun CurvePlot(spec: CurveSpec) {
    val textMeasurer = rememberTextMeasurer()
    val axisStyle = TextStyle(color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 9.sp)
    val axisColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)

    Column(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Text(
            spec.yLabel,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                .padding(8.dp),
        ) {
            Canvas(modifier = Modifier.fillMaxWidth().height(120.dp)) {
                val padX = 26.dp.toPx()
                val padY = 12.dp.toPx()
                drawLine(axisColor, Offset(padX, size.height - padY), Offset(size.width - 8.dp.toPx(), size.height - padY), strokeWidth = 2.dp.toPx())
                drawLine(axisColor, Offset(padX, padY), Offset(padX, size.height - padY), strokeWidth = 2.dp.toPx())
                spec.series.forEach { series ->
                    if (series.points.size < 2) {
                        series.points.forEachIndexed { i, v ->
                            val x = padX + (i + 0.5f) / series.points.size * (size.width - padX - 12.dp.toPx())
                            val y = size.height - padY - (v / spec.yMax) * (size.height - 2 * padY)
                            drawCircle(series.color, radius = 6.dp.toPx(), center = Offset(x, y))
                        }
                        return@forEach
                    }
                    val path = Path()
                    series.points.forEachIndexed { i, v ->
                        val x = padX + i.toFloat() / (series.points.size - 1) * (size.width - padX - 12.dp.toPx())
                        val y = size.height - padY - (v / spec.yMax).coerceIn(0f, 1f) * (size.height - 2 * padY)
                        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    drawPath(path, series.color, style = Stroke(width = 3.dp.toPx()))
                }
                val lay = textMeasurer.measure(spec.xLabel, axisStyle)
                drawText(lay, topLeft = Offset(padX + 4.dp.toPx(), size.height - padY + 2.dp.toPx()))
            }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            spec.series.forEach { series ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(9.dp).background(series.color, CircleShape))
                    Text(
                        series.label,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun PayoffMatrix(spec: MatrixSpec) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Box(modifier = Modifier.weight(1f))
            spec.colLabels.forEach { label ->
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f).padding(2.dp),
                )
            }
        }
        spec.values.forEachIndexed { r, row ->
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    spec.rowLabels[r],
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f).padding(2.dp),
                )
                row.forEachIndexed { c, v ->
                    val focused = spec.focus == r to c
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(2.dp)
                            .background(
                                when {
                                    focused -> EnvAmber
                                    v > 0 -> EnvGreen.copy(alpha = 0.25f + 0.4f * v.toFloat())
                                    v < 0 -> EnvRed.copy(alpha = 0.25f + 0.4f * (-v).toFloat())
                                    else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                },
                                RoundedCornerShape(6.dp),
                            )
                            .height(28.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "%+.0f".format(v),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}
