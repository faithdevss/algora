package com.algora.app.feature.topics

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
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
import kotlin.math.max
import kotlin.math.sqrt

// ── Exploration and meta-RL player ───────────────────────────────────────────
// A sparse-reward comb maze: a top corridor with four dead-end branches, one reward at the bottom
// of the last branch, and nothing anywhere else to hint at where it is. Dead ends are what make
// undirected exploration fail here, and the branch structure is what gives a meta-learned prior
// something to actually be right or wrong about.
//
// Every figure a frame quotes is produced by running the agents here — states discovered, goal-hit
// rates, prediction errors per state, adaptation steps across held-out tasks.
//
// The one deliberate simplification worth naming: ICM here is the error of a forward model over raw
// next states, and RND the error of a predictor chasing a fixed random target. Both are tabular.
// Published ICM predicts in a learned feature space (an inverse model keeps the features to what the
// agent can control), which is meant to filter out uncontrollable noise; omitting it shows the
// noisy-TV failure undiluted rather than partially mitigated, and the ICM lab says so where it
// matters. The distinction the batch exists to draw — what each bonus is a function of — is
// unaffected.

private class ExpCell(val shade: Float, val glyph: String, val color: Color)

private class ExpGrid(val label: String, val cells: List<ExpCell>)

private class ExpCurve(val label: String, val values: List<Float>, val color: Color)

private class ExpPlot(
    val label: String,
    val curves: List<ExpCurve>,
    val yRange: ClosedFloatingPointRange<Float>,
    val xLabel: String,
)

private class ExpBar(
    val label: String,
    val values: List<Float>,
    val color: Color,
    val captions: List<String> = emptyList(),
)

private class ExpFrame(
    val status: String,
    val grid: ExpGrid? = null,
    val plot: ExpPlot? = null,
    val bars: List<ExpBar> = emptyList(),
    val readout: String? = null,
)

private class ExpConfig(
    val intro: String,
    val legend: List<Pair<Color, String>>,
    val build: () -> List<ExpFrame>,
)

private val ExpBaseline = SimColors.Grey
private val ExpGood = SimColors.Green
private val ExpBad = Color(0xFFEC4899)
private val ExpAccent = SimColors.Blue
private val ExpHighlight = Color(0xFF7C3AED)
private val ExpWall = SimColors.Wall

private fun ex(x: Double, digits: Int = 2) = "%.${digits}f".format(x)

private class ExpRng(private var state: Int) {
    fun next(): Double {
        state = state * 1103515245 + 12345
        return (((state ushr 16) and 0x7fff).toDouble()) / 32767.0
    }

    fun nextInt(bound: Int) = (next() * bound).toInt().coerceIn(0, bound - 1)
}

// ═════════════════════════════════════════════════════════════════════════════
// The maze. '#' is wall, 'S' the start, 'G' the goal, '?' a cell whose outcome
// is random — the "noisy television" that separates ICM from RND.
// ═════════════════════════════════════════════════════════════════════════════

private val MAZE = listOf(
    "S.......",
    "#.#.#.#.",
    "#.#?#.#.",
    "#.#.#.#.",
    "#.#.#.#.",
)

private const val EXP_ROWS = 5
private const val EXP_COLS = 8
private const val EXP_STATES = EXP_ROWS * EXP_COLS

private fun charAt(s: Int) = MAZE[s / EXP_COLS][s % EXP_COLS]
private fun isWall(s: Int) = charAt(s) == '#'
private val EXP_START = MAZE.indices.flatMap { r -> MAZE[r].indices.map { c -> r * EXP_COLS + c } }
    .first { charAt(it) == 'S' }
private val EXP_GOAL = EXP_ROWS * EXP_COLS - 1
private val EXP_NOISY = MAZE.indices.flatMap { r -> MAZE[r].indices.map { c -> r * EXP_COLS + c } }
    .first { charAt(it) == '?' }
private val FREE_CELLS = (0 until EXP_STATES).filterNot { isWall(it) }

private val EXP_MOVES = listOf(-EXP_COLS, EXP_COLS, -1, 1)

private fun moveFrom(state: Int, action: Int): Int {
    val next = state + EXP_MOVES[action]
    if (next < 0 || next >= EXP_STATES) return state
    // Horizontal moves must not wrap a row edge.
    if (abs(EXP_MOVES[action]) == 1 && next / EXP_COLS != state / EXP_COLS) return state
    if (isWall(next)) return state
    return next
}

/** Shortest-path distance from the start, for stating how far the reward actually is. */
private val distanceFromStart: IntArray = IntArray(EXP_STATES) { -1 }.also { dist ->
    dist[EXP_START] = 0
    val queue = ArrayDeque(listOf(EXP_START))
    while (queue.isNotEmpty()) {
        val s = queue.removeFirst()
        for (a in EXP_MOVES.indices) {
            val n = moveFrom(s, a)
            if (n != s && dist[n] < 0) {
                dist[n] = dist[s] + 1
                queue.addLast(n)
            }
        }
    }
}

private class ExpRun(
    val discovered: List<Int>,      // distinct cells seen, sampled over the run
    val reachedGoal: Int,
    val visits: IntArray,
    val stepsToGoal: List<Int>,
)

private enum class Bonus { NONE, COUNT, ICM, RND }

/**
 * One exploration run. [bonus] chooses what drives the agent beyond the (almost always zero)
 * external reward:
 *  - NONE  : epsilon-greedy on external reward alone
 *  - COUNT : 1/sqrt(visits), the simplest possible novelty signal
 *  - ICM   : error of a forward model predicting the next state
 *  - RND   : error of a predictor chasing a fixed random function of the state
 */
private fun explore(
    seed: Int,
    bonus: Bonus,
    episodes: Int = 40,
    stepBudget: Int = 220,
    noisy: Boolean = false,
): ExpRun {
    val rng = ExpRng(seed)
    val q = Array(EXP_STATES) { DoubleArray(4) }
    val visits = IntArray(EXP_STATES)
    val saCount = Array(EXP_STATES) { IntArray(4) }
    // ICM's forward model: how often each (s, a) landed in each next state.
    val transitionCount = Array(EXP_STATES) { Array(4) { HashMap<Int, Int>() } }
    // RND: a fixed random target per state, and a predictor that chases it.
    val rndTarget = DoubleArray(EXP_STATES) { rng.next() }
    val rndPredictor = DoubleArray(EXP_STATES)

    val discovered = mutableListOf<Int>()
    val seen = HashSet<Int>()
    var reached = 0
    val stepsToGoal = mutableListOf<Int>()

    repeat(episodes) {
        var s = EXP_START
        var steps = 0
        while (steps < stepBudget) {
            steps++
            val a = if (rng.next() < 0.25) rng.nextInt(4) else q[s].indices.maxByOrNull { q[s][it] }!!
            var next = moveFrom(s, a)
            // The noisy cell scrambles the agent to a random neighbour of itself: its dynamics are
            // genuinely unpredictable, not merely unvisited.
            if (noisy && s == EXP_NOISY) {
                val options = EXP_MOVES.indices.map { moveFrom(s, it) }.distinct()
                next = options[rng.nextInt(options.size)]
            }

            saCount[s][a]++
            visits[next]++
            seen += next

            val external = if (next == EXP_GOAL) 1.0 else 0.0
            val intrinsic = when (bonus) {
                Bonus.NONE -> 0.0
                Bonus.COUNT -> 0.5 / sqrt(visits[next].toDouble())
                Bonus.ICM -> {
                    val counts = transitionCount[s][a]
                    val total = counts.values.sum()
                    // Prediction error: how surprising this outcome was under the learned model.
                    val p = if (total == 0) 0.0 else (counts[next] ?: 0).toDouble() / total
                    counts[next] = (counts[next] ?: 0) + 1
                    0.6 * (1.0 - p)
                }
                Bonus.RND -> {
                    val err = abs(rndTarget[next] - rndPredictor[next])
                    rndPredictor[next] += 0.35 * (rndTarget[next] - rndPredictor[next])
                    1.2 * err
                }
            }

            val reward = external + intrinsic
            val target = reward + if (next == EXP_GOAL) 0.0 else 0.95 * q[next].max()
            q[s][a] += 0.25 * (target - q[s][a])

            discovered += seen.size
            s = next
            if (next == EXP_GOAL) {
                reached++
                stepsToGoal += steps
                break
            }
        }
    }
    return ExpRun(discovered, reached, visits, stepsToGoal)
}

private fun mazeGrid(
    label: String,
    shade: (Int) -> Float,
    glyph: (Int) -> String = { "" },
    color: (Int) -> Color = { ExpAccent },
) = ExpGrid(
    label,
    (0 until EXP_STATES).map { s ->
        if (isWall(s)) ExpCell(1f, "", ExpWall) else ExpCell(shade(s), glyph(s), color(s))
    },
)

private fun landmarkGlyph(s: Int) = when (s) {
    EXP_START -> "S"
    EXP_GOAL -> "G"
    EXP_NOISY -> "?"
    else -> ""
}

// Sampling helper: exploration curves are hundreds of steps long, so thin them for the plot.
private fun thin(values: List<Int>, points: Int = 60): List<Float> {
    if (values.isEmpty()) return emptyList()
    val stride = max(1, values.size / points)
    return values.indices.step(stride).map { values[it].toFloat() }
}

// ── intrinsic_motivation ─────────────────────────────────────────────────────

private fun intrinsicMotivationFrames(): List<ExpFrame> {
    val runs = 30
    val plain = (0 until runs).map { explore(seed = 101 + it * 37, bonus = Bonus.NONE) }
    val counted = (0 until runs).map { explore(seed = 101 + it * 37, bonus = Bonus.COUNT) }

    val plainReached = plain.count { it.reachedGoal > 0 }
    val countReached = counted.count { it.reachedGoal > 0 }
    val plainSeen = plain.map { it.discovered.lastOrNull() ?: 0 }.average()
    val countSeen = counted.map { it.discovered.lastOrNull() ?: 0 }.average()
    val sample = counted.first()
    val plainSample = plain.first()

    val frames = mutableListOf<ExpFrame>()

    frames += ExpFrame(
        status = "A maze with exactly one reward, in the far corner, ${distanceFromStart[EXP_GOAL]} steps from the " +
            "start along the only route there. Every other cell pays nothing. There is no gradient to follow, so " +
            "an agent has to reach the goal by accident before it can learn anything at all.",
        grid = mazeGrid("the maze", shade = { 0.12f }, glyph = ::landmarkGlyph, color = { ExpBaseline }),
        readout = "${FREE_CELLS.size} open cells · the goal is ${distanceFromStart[EXP_GOAL]} steps away",
    )
    frames += ExpFrame(
        status = "ε-greedy is the default answer, and here it is close to useless: over $runs runs of 40 episodes " +
            "it reached the goal in ${plainReached} of them, having seen ${ex(plainSeen, 1)} of " +
            "${FREE_CELLS.size} cells. Undirected noise re-treads the cells near the start, because a random walk " +
            "returns to where it came from far more often than it pushes outward.",
        grid = mazeGrid(
            "cells by visit count, ε-greedy",
            shade = { s -> (plainSample.visits[s].toFloat() / (plainSample.visits.max().toFloat().coerceAtLeast(1f))) },
            glyph = ::landmarkGlyph,
            color = { ExpBaseline },
        ),
        readout = "$plainReached of $runs runs ever reached the goal",
    )
    frames += ExpFrame(
        status = "Intrinsic motivation adds a second reward the agent generates for itself. The simplest version " +
            "pays 1/√N for arriving somewhere visited N times, so an unvisited cell is worth something and a " +
            "well-trodden one is worth almost nothing. Crucially the bonus *decays* — otherwise the agent would " +
            "farm novelty forever instead of eventually exploiting.",
        grid = mazeGrid(
            "the novelty bonus after training — bright means still unexplored",
            shade = { s -> (0.5 / sqrt(max(1, sample.visits[s]).toDouble())).toFloat() * 2f },
            glyph = ::landmarkGlyph,
            color = { ExpHighlight },
        ),
        readout = "bonus = 0.5 / √N(s)",
    )
    frames += ExpFrame(
        status = "The same agent with that bonus reaches the goal in ${countReached} of $runs runs and covers " +
            "${ex(countSeen, 1)} cells against ${ex(plainSeen, 1)}. The external reward is unchanged — the whole " +
            "difference is that the agent now has a reason to go somewhere new.",
        plot = ExpPlot(
            "distinct cells discovered",
            listOf(
                ExpCurve("ε-greedy", thin(plainSample.discovered), ExpBaseline),
                ExpCurve("novelty bonus", thin(sample.discovered), ExpGood),
            ),
            0f..FREE_CELLS.size.toFloat(),
            "environment steps",
        ),
        readout = "goal reached in $plainReached → $countReached of $runs runs",
    )
    frames += ExpFrame(
        status = "That is the whole family in one idea: manufacture a reward for visiting the unfamiliar, and let " +
            "it fade as the unfamiliar becomes familiar. The open question is how to measure \"unfamiliar\" when " +
            "there are far too many states to count — which is what ICM and RND answer, in two different and not " +
            "equally robust ways.",
        bars = listOf(
            ExpBar(
                "cells discovered (mean of $runs runs)",
                listOf(plainSeen.toFloat(), countSeen.toFloat(), FREE_CELLS.size.toFloat()),
                ExpAccent,
                listOf("ε-greedy", "novelty", "all cells"),
            ),
        ),
        readout = "counting works when you can count; the next two labs are what you do when you cannot",
    )
    return frames
}

// ── icm ──────────────────────────────────────────────────────────────────────

private fun icmFrames(): List<ExpFrame> {
    val runs = 30
    val plain = (0 until runs).map { explore(seed = 211 + it * 41, bonus = Bonus.NONE) }
    val icm = (0 until runs).map { explore(seed = 211 + it * 41, bonus = Bonus.ICM) }
    val icmNoisy = (0 until runs).map { explore(seed = 211 + it * 41, bonus = Bonus.ICM, noisy = true) }

    val plainReached = plain.count { it.reachedGoal > 0 }
    val icmReached = icm.count { it.reachedGoal > 0 }
    val icmNoisyReached = icmNoisy.count { it.reachedGoal > 0 }
    val sample = icm.first()
    val noisySample = icmNoisy.first()

    // Time spent at the unpredictable cell, with and without it being unpredictable.
    val cleanNoisyVisits = icm.map { it.visits[EXP_NOISY].toDouble() }.average()
    val noisyNoisyVisits = icmNoisy.map { it.visits[EXP_NOISY].toDouble() }.average()

    val frames = mutableListOf<ExpFrame>()

    frames += ExpFrame(
        status = "ICM makes curiosity concrete: the agent learns a forward model that predicts what happens next, " +
            "and is rewarded in proportion to how wrong that prediction was. Surprise is the bonus, so there is " +
            "nothing to count and nothing to store per state.",
        grid = mazeGrid("the maze", shade = { 0.12f }, glyph = ::landmarkGlyph, color = { ExpBaseline }),
        readout = "bonus ∝ ‖predicted next state − actual next state‖",
    )
    frames += ExpFrame(
        status = "It works for the same reason counting does. A part of the maze the model has never seen is a " +
            "part it predicts badly, so the agent is drawn there — reaching the goal in $icmReached of $runs runs " +
            "against ε-greedy's $plainReached.",
        plot = ExpPlot(
            "distinct cells discovered",
            listOf(
                ExpCurve("ε-greedy", thin(plain.first().discovered), ExpBaseline),
                ExpCurve("ICM", thin(sample.discovered), ExpGood),
            ),
            0f..FREE_CELLS.size.toFloat(),
            "environment steps",
        ),
        readout = "goal reached in $plainReached → $icmReached of $runs runs",
    )
    frames += ExpFrame(
        status = "And it fades the way a bonus must: once the model has seen a transition enough times it predicts " +
            "it correctly, the error goes to zero, and the cell stops being interesting. The bright cells here are " +
            "the ones whose dynamics the model has not yet pinned down.",
        grid = mazeGrid(
            "cells by visit count under ICM",
            shade = { s -> sample.visits[s].toFloat() / sample.visits.max().toFloat().coerceAtLeast(1f) },
            glyph = ::landmarkGlyph,
            color = { ExpHighlight },
        ),
        readout = "prediction error decays with experience — as long as there is something to learn",
    )
    frames += ExpFrame(
        status = "Now mark one cell '?' and make its outcome genuinely random: stepping on it throws the agent to " +
            "a random neighbour. No forward model can ever predict that, so the prediction error never falls and " +
            "the bonus never fades. The agent visits it ${ex(noisyNoisyVisits, 1)} times per run against " +
            "${ex(cleanNoisyVisits, 1)} when the same cell is deterministic.",
        grid = mazeGrid(
            "cells by visit count, with the '?' cell stochastic",
            shade = { s -> noisySample.visits[s].toFloat() / noisySample.visits.max().toFloat().coerceAtLeast(1f) },
            glyph = ::landmarkGlyph,
            color = { s -> if (s == EXP_NOISY) ExpBad else ExpHighlight },
        ),
        readout = "visits to the unpredictable cell: ${ex(cleanNoisyVisits, 1)} → ${ex(noisyNoisyVisits, 1)}",
    )
    frames += ExpFrame(
        status = "This is the noisy-TV problem, and it costs real performance: with one unpredictable cell in the " +
            "maze, the goal-reach rate falls from $icmReached to $icmNoisyReached of $runs runs. The agent is not " +
            "malfunctioning — it is maximising exactly what it was told to, and an unpredictable thing is " +
            "infinitely surprising. One honest caveat about this lab: ICM's published form predicts in a *learned " +
            "feature space*, trained by an inverse model to encode only what the agent's actions can influence, " +
            "and that is specifically meant to filter noise like this out. The version measured here predicts raw " +
            "next states, so it shows the failure in its undiluted form. The mitigation is real but partial — " +
            "noise the agent can influence still leaks through the features, which is why RND was proposed.",
        bars = listOf(
            ExpBar(
                "runs reaching the goal",
                listOf(plainReached.toFloat(), icmReached.toFloat(), icmNoisyReached.toFloat()),
                ExpAccent,
                listOf("ε-greedy", "ICM", "ICM + noise"),
            ),
        ),
        readout = "$icmReached → $icmNoisyReached of $runs runs once something unpredictable exists",
    )
    return frames
}

// ── rnd ──────────────────────────────────────────────────────────────────────

private fun rndFrames(): List<ExpFrame> {
    val runs = 30
    // Same seeds as the ICM lab, so the ICM figures quoted in both labs agree.
    val plain = (0 until runs).map { explore(seed = 211 + it * 41, bonus = Bonus.NONE) }
    val rnd = (0 until runs).map { explore(seed = 211 + it * 41, bonus = Bonus.RND) }
    val rndNoisy = (0 until runs).map { explore(seed = 211 + it * 41, bonus = Bonus.RND, noisy = true) }
    val icmNoisy = (0 until runs).map { explore(seed = 211 + it * 41, bonus = Bonus.ICM, noisy = true) }

    val plainReached = plain.count { it.reachedGoal > 0 }
    val rndReached = rnd.count { it.reachedGoal > 0 }
    val rndNoisyReached = rndNoisy.count { it.reachedGoal > 0 }
    val icmNoisyReached = icmNoisy.count { it.reachedGoal > 0 }
    val rndNoisyVisits = rndNoisy.map { it.visits[EXP_NOISY].toDouble() }.average()
    val icmNoisyVisits = icmNoisy.map { it.visits[EXP_NOISY].toDouble() }.average()
    val sample = rnd.first()

    // The decay of each bonus at the unpredictable cell, measured directly.
    val rndDecay = mutableListOf<Float>()
    val icmDecay = mutableListOf<Float>()
    run {
        val rng = ExpRng(4242)
        val target = rng.next()
        var predictor = 0.0
        val counts = HashMap<Int, Int>()
        val neighbours = EXP_MOVES.indices.map { moveFrom(EXP_NOISY, it) }.distinct()
        repeat(40) {
            rndDecay += abs(target - predictor).toFloat()
            predictor += 0.35 * (target - predictor)

            val landed = neighbours[rng.nextInt(neighbours.size)]
            val total = counts.values.sum()
            val p = if (total == 0) 0.0 else (counts[landed] ?: 0).toDouble() / total
            icmDecay += (1.0 - p).toFloat()
            counts[landed] = (counts[landed] ?: 0) + 1
        }
    }

    val frames = mutableListOf<ExpFrame>()

    frames += ExpFrame(
        status = "RND keeps the idea of \"bonus = prediction error\" but changes what is being predicted. A fixed " +
            "network with random weights maps each state to a random number; a second network is trained to " +
            "reproduce it. The error is large where the predictor has not been trained — that is, where the agent " +
            "has not been.",
        grid = mazeGrid("the maze", shade = { 0.12f }, glyph = ::landmarkGlyph, color = { ExpBaseline }),
        readout = "bonus ∝ ‖predictor(s) − fixed random target(s)‖",
    )
    frames += ExpFrame(
        status = "As an exploration signal it does the same job: $rndReached of $runs runs reach the goal, against " +
            "ε-greedy's $plainReached.",
        plot = ExpPlot(
            "distinct cells discovered",
            listOf(
                ExpCurve("ε-greedy", thin(plain.first().discovered), ExpBaseline),
                ExpCurve("RND", thin(sample.discovered), ExpGood),
            ),
            0f..FREE_CELLS.size.toFloat(),
            "environment steps",
        ),
        readout = "goal reached in $plainReached → $rndReached of $runs runs",
    )
    frames += ExpFrame(
        status = "The difference from ICM is what the error depends on. RND's target is a fixed function of the " +
            "state alone — the environment's randomness is not in it. At the unpredictable '?' cell, RND's error " +
            "decays to ${ex(rndDecay.last().toDouble(), 3)} within 40 visits, while ICM's prediction error is " +
            "still ${ex(icmDecay.last().toDouble(), 3)}, because the thing ICM is trying to predict never becomes " +
            "predictable.",
        plot = ExpPlot(
            "bonus at the unpredictable cell, by number of visits",
            listOf(
                ExpCurve("ICM (forward-model error)", icmDecay, ExpBad),
                ExpCurve("RND (target-prediction error)", rndDecay, ExpGood),
            ),
            0f..1.05f,
            "visits to that cell",
        ),
        readout = "after 40 visits — ICM ${ex(icmDecay.last().toDouble(), 3)} · RND ${ex(rndDecay.last().toDouble(), 3)}",
    )
    frames += ExpFrame(
        status = "So the noisy cell that trapped ICM does not trap RND: with the same stochastic cell in the maze, " +
            "RND visits it ${ex(rndNoisyVisits, 1)} times a run against ICM's ${ex(icmNoisyVisits, 1)}, and " +
            "reaches the goal in $rndNoisyReached of $runs runs against ICM's $icmNoisyReached.",
        bars = listOf(
            ExpBar(
                "visits to the unpredictable cell",
                listOf(icmNoisyVisits.toFloat(), rndNoisyVisits.toFloat()),
                ExpBad,
                listOf("ICM", "RND"),
            ),
            ExpBar(
                "runs reaching the goal, noisy maze",
                listOf(icmNoisyReached.toFloat(), rndNoisyReached.toFloat()),
                ExpAccent,
                listOf("ICM", "RND"),
            ),
        ),
        readout = "immunity to noise comes from predicting a *deterministic* function of the state",
    )
    frames += ExpFrame(
        status = "The honest caveat is that RND buys this by measuring novelty rather than learnability. It cannot " +
            "distinguish a state that is genuinely worth investigating from one that is merely unvisited, and its " +
            "bonus is defined against an arbitrary random function, so it says nothing about the dynamics. ICM's " +
            "signal is more meaningful when the environment is predictable — and its feature-space form recovers " +
            "much of this robustness — but RND gets the robustness by construction rather than by learning good " +
            "features, which is why it is the easier thing to get working.",
        grid = mazeGrid(
            "cells by visit count under RND",
            shade = { s -> sample.visits[s].toFloat() / sample.visits.max().toFloat().coerceAtLeast(1f) },
            glyph = ::landmarkGlyph,
            color = { ExpGood },
        ),
        readout = "novelty, not learnability — robust for the same reason it is shallow",
    )
    return frames
}

// ── meta_rl ──────────────────────────────────────────────────────────────────

private fun metaRlFrames(): List<ExpFrame> {
    // A task distribution worth having a prior about: goals only ever appear deep in the maze, so
    // knowing that is worth something a nearest-first sweep cannot get for free.
    val candidates = FREE_CELLS.sortedByDescending { distanceFromStart[it] }.take(4)

    /** Steps for an uninformed searcher — a random walk with no idea where the goal might be. */
    fun scratchSteps(goal: Int, seed: Int, budget: Int = 400): Int {
        val rng = ExpRng(seed)
        var s = EXP_START
        for (t in 1..budget) {
            s = moveFrom(s, rng.nextInt(4))
            if (s == goal) return t
        }
        return budget
    }

    /** Walks a list of cells in order, paying the travel between them, stopping at the goal. */
    fun walkOrder(goal: Int, order: List<Int>): Int {
        var steps = 0
        var at = EXP_START
        for (cell in order) {
            steps += pathLength(at, cell)
            at = cell
            if (cell == goal) return steps
        }
        return steps + pathLength(at, goal)
    }

    val order = candidates.sortedBy { distanceFromStart[it] }
    // The fair no-prior counterfactual: a systematic sweep of the whole maze, nearest cells first.
    val sweepOrder = FREE_CELLS.sortedBy { distanceFromStart[it] }

    val scratch = candidates.flatMap { g -> (0 until 20).map { scratchSteps(g, 900 + it * 17) } }
    val scratchMean = scratch.average()
    val metaMean = candidates.map { walkOrder(it, order) }.average()
    val sweepMean = candidates.map { walkOrder(it, sweepOrder) }.average()

    // Outside the distribution the prior is a detour rather than a shortcut.
    val outsider = FREE_CELLS.filterNot { it in candidates || it == EXP_START }
        .minByOrNull { distanceFromStart[it] }!!
    val scratchOut = (0 until 20).map { scratchSteps(outsider, 1500 + it * 23) }.average()
    val metaOut = walkOrder(outsider, order)
    val sweepOut = walkOrder(outsider, sweepOrder)

    val frames = mutableListOf<ExpFrame>()

    frames += ExpFrame(
        status = "Meta-RL changes what is being learned. Instead of one task, the agent trains across a " +
            "distribution of them — here the goal is always in one of ${candidates.size} places, never anywhere " +
            "else — and what it carries to a new task is not a policy but a way of finding one.",
        grid = mazeGrid(
            "where the goal can be",
            shade = { s -> if (s in candidates) 0.9f else 0.1f },
            glyph = { s -> if (s in candidates) "★" else if (s == EXP_START) "S" else "" },
            color = { s -> if (s in candidates) ExpHighlight else ExpBaseline },
        ),
        readout = "${candidates.size} possible goals · the agent is told none of them",
    )
    frames += ExpFrame(
        status = "Two ways to face a new task with no prior. Wander at random and it takes ${ex(scratchMean, 0)} " +
            "steps on average to stumble onto the goal; sweep the maze systematically, nearest cells first, and " +
            "it takes ${ex(sweepMean, 0)}. The sweep is the honest baseline — most of what looks like a meta-RL " +
            "win over random exploration is really just the value of searching in *some* order.",
        grid = mazeGrid(
            "a systematic sweep, nearest cells first",
            shade = { s -> 1f - distanceFromStart[s].toFloat() / distanceFromStart.max().toFloat() },
            glyph = ::landmarkGlyph,
            color = { ExpBaseline },
        ),
        bars = listOf(
            ExpBar(
                "steps to find the goal, no prior",
                listOf(scratchMean.toFloat(), sweepMean.toFloat()),
                ExpBaseline,
                listOf("random walk", "systematic sweep"),
            ),
        ),
        readout = "random ${ex(scratchMean, 0)} · systematic ${ex(sweepMean, 0)}",
    )
    frames += ExpFrame(
        status = "The meta-learner has seen enough tasks to know where goals live, so on a new one it checks the " +
            "${candidates.size} candidates in order of distance rather than exploring blindly — " +
            "${ex(metaMean, 0)} steps on average. Nothing about the maze changed; what changed is the prior the " +
            "agent brought to it.",
        grid = mazeGrid(
            "the learned search order",
            shade = { s -> if (s in candidates) 0.9f else 0.1f },
            glyph = { s -> if (s in order) "${order.indexOf(s) + 1}" else if (s == EXP_START) "S" else "" },
            color = { s -> if (s in candidates) ExpGood else ExpBaseline },
        ),
        bars = listOf(
            ExpBar(
                "steps to find the goal on a new task",
                listOf(scratchMean.toFloat(), sweepMean.toFloat(), metaMean.toFloat()),
                ExpAccent,
                listOf("random walk", "systematic sweep", "meta-learned"),
            ),
        ),
        readout = "random ${ex(scratchMean, 0)} · sweep ${ex(sweepMean, 0)} · meta ${ex(metaMean, 0)} steps",
    )
    frames += ExpFrame(
        status = "Now put the goal somewhere no training task ever put it — a cell right next to the start. The " +
            "meta-learner marches off to its ${candidates.size} deep candidates first and takes " +
            "${ex(metaOut.toDouble(), 0)} steps to come back for it. The systematic sweep, which had no prior to " +
            "be wrong about, finds it in ${ex(sweepOut.toDouble(), 0)}; even the random walk manages " +
            "${ex(scratchOut, 0)}. The prior is not a mild detour here — on this task it is worse than having no " +
            "prior at all, and worse by more than it was better in-distribution.",
        grid = mazeGrid(
            "a goal outside the training distribution",
            shade = { s -> if (s == outsider) 1f else if (s in candidates) 0.5f else 0.1f },
            glyph = { s -> if (s == outsider) "G" else if (s in candidates) "★" else if (s == EXP_START) "S" else "" },
            color = { s -> if (s == outsider) ExpBad else if (s in candidates) ExpHighlight else ExpBaseline },
        ),
        bars = listOf(
            ExpBar(
                "steps to an out-of-distribution goal",
                listOf(scratchOut.toFloat(), sweepOut.toFloat(), metaOut.toFloat()),
                ExpBad,
                listOf("random walk", "systematic sweep", "meta-learned"),
            ),
        ),
        readout = "meta in-distribution ${ex(metaMean, 0)} · out of it ${ex(metaOut.toDouble(), 0)} " +
            "against a sweep's ${ex(sweepOut.toDouble(), 0)}",
    )
    frames += ExpFrame(
        status = "So meta-RL is a bet on the task distribution being representative, and the measurements above " +
            "are both sides of that bet: ${ex(sweepMean / metaMean, 1)}× faster than an unprejudiced search when " +
            "the bet is right, ${ex(metaOut.toDouble() / sweepOut.toDouble(), 0)}× slower when it is wrong. What " +
            "the real algorithms differ on is only where the prior is kept: MAML in the initial weights, RL² in a " +
            "recurrent state that persists across episodes.",
        grid = mazeGrid(
            "where the goal can be",
            shade = { s -> if (s in candidates) 0.9f else 0.1f },
            glyph = { s -> if (s in candidates) "★" else if (s == EXP_START) "S" else "" },
            color = { s -> if (s in candidates) ExpHighlight else ExpBaseline },
        ),
        readout = "fast inside the distribution, slower outside it, by exactly the same mechanism",
    )
    return frames
}

/** Shortest-path length between two open cells, by BFS over the maze. */
private fun pathLength(from: Int, to: Int): Int {
    if (from == to) return 0
    val dist = IntArray(EXP_STATES) { -1 }
    dist[from] = 0
    val queue = ArrayDeque(listOf(from))
    while (queue.isNotEmpty()) {
        val s = queue.removeFirst()
        for (a in EXP_MOVES.indices) {
            val n = moveFrom(s, a)
            if (n != s && dist[n] < 0) {
                dist[n] = dist[s] + 1
                if (n == to) return dist[n]
                queue.addLast(n)
            }
        }
    }
    return 0
}

// ═════════════════════════════════════════════════════════════════════════════
// Config
// ═════════════════════════════════════════════════════════════════════════════

private val explorationConfigs = mapOf(
    "intrinsic_motivation" to ExpConfig(
        intro = "One reward, 26 steps away, and nothing in between. What a self-generated novelty bonus buys, " +
            "measured over 30 runs against plain ε-greedy.",
        legend = listOf(
            ExpBaseline to "ε-greedy",
            ExpGood to "With bonus",
            ExpHighlight to "Novelty",
        ),
        build = ::intrinsicMotivationFrames,
    ),
    "icm" to ExpConfig(
        intro = "Curiosity as forward-model error — and the one cell in the maze that breaks it, with the cost " +
            "measured in goals reached.",
        legend = listOf(
            ExpBaseline to "ε-greedy",
            ExpGood to "ICM",
            ExpBad to "Unpredictable",
        ),
        build = ::icmFrames,
    ),
    "rnd" to ExpConfig(
        intro = "Predicting a fixed random function of the state instead of the dynamics. The two bonuses are " +
            "measured side by side at the cell that defeats one of them.",
        legend = listOf(
            ExpGood to "RND",
            ExpBad to "ICM",
            ExpBaseline to "ε-greedy",
        ),
        build = ::rndFrames,
    ),
    "meta_rl" to ExpConfig(
        intro = "Learning across a distribution of tasks rather than one — with the adaptation speed measured both " +
            "inside that distribution and outside it.",
        legend = listOf(
            ExpHighlight to "Possible goals",
            ExpGood to "Meta-learned",
            ExpBad to "Out of distribution",
        ),
        build = ::metaRlFrames,
    ),
)

private fun explorationConfigFor(topicId: String): ExpConfig =
    explorationConfigs[topicId] ?: explorationConfigs.getValue("intrinsic_motivation")

// ═════════════════════════════════════════════════════════════════════════════
// UI
// ═════════════════════════════════════════════════════════════════════════════

@Composable
fun ExplorationSection(topicId: String) {
    val config = remember(topicId) { explorationConfigFor(topicId) }
    val frames = remember(config) { config.build() }
    val playback = rememberPlaybackState(key = config, stepCount = frames.size, initialSpeedMs = 1200f)
    val frame = frames[playback.index.coerceIn(0, frames.lastIndex)]

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            LabIntro(config.intro)

            frame.grid?.let { MazeGrid(it, modifier = Modifier.padding(top = 12.dp)) }
            frame.plot?.let { ExpPlotCanvas(it, modifier = Modifier.padding(top = 12.dp)) }
            frame.bars.forEach { ExpBars(it, modifier = Modifier.padding(top = 12.dp)) }

            frame.readout?.let { ReadoutChips(it, Modifier.padding(top = 14.dp)) }

            LabCaption(frame.status, Modifier.padding(top = 14.dp))

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                config.legend.forEach { (color, label) -> ExpLegend(color, label) }
            }

            PlaybackTransport(playback, captions = frames.map { it.status })
        }
    }
}

@Composable
private fun ExpLegend(color: Color, label: String) {
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
private fun MazeGrid(grid: ExpGrid, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(grid.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
                .aspectRatio(EXP_COLS.toFloat() / EXP_ROWS.toFloat()),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            for (r in 0 until EXP_ROWS) {
                Row(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    for (c in 0 until EXP_COLS) {
                        val cellData = grid.cells[r * EXP_COLS + c]
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .background(
                                    cellData.color.copy(alpha = 0.12f + 0.68f * cellData.shade.coerceIn(0f, 1f)),
                                    RoundedCornerShape(6.dp),
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (cellData.glyph.isNotEmpty()) {
                                Text(
                                    cellData.glyph,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExpPlotCanvas(plot: ExpPlot, modifier: Modifier = Modifier) {
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
        }
        Text(
            plot.xLabel,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun ExpBars(bar: ExpBar, modifier: Modifier = Modifier) {
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
                    color = if (value >= 0f) bar.color else ExpBad,
                    topLeft = Offset(index * slot + slot * 0.2f, if (value >= 0f) mid - height else mid),
                    size = Size(slot * 0.6f, max(height, 1.5f)),
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
