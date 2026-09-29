package com.algora.app.feature.topics

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.algora.app.core.ui.theme.IBMPlexMono
import com.algora.app.core.ui.theme.SimColors
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.random.Random

// ── Reinforcement-learning storyboards ───────────────────────────────────────
// Q-Learning, SARSA, Markov Decision Process, Thompson Sampling, Multi-Armed Bandit and UCB: a grid of
// values (or a row per arm), the update of the current step, chips and a headline, then a parameter
// stepper over a step-back button and the lab's labelled action. Every number comes from a seeded run.

internal val rlStoryTopicIds = setOf("q_learning", "sarsa", "mdp", "thompson_sampling", "multi_armed_bandit", "ucb")

private val GoalGreen = Color(0xFF4CAF7A)
private val PitRed = Color(0xFFD9534F)
private val Violet = Color(0xFFB4A2FF)

// ── Formatting ──

private fun rx(v: Double, d: Int = 2): String {
    var p = 1L
    repeat(d) { p *= 10 }
    val r = (abs(v) * p + 0.5).toLong()
    val body = if (d == 0) "${r / p}" else "${r / p}." + (r % p).toString().padStart(d, '0')
    return if (v < 0 && r != 0L) "−$body" else body
}

private fun ladder(from: Double, to: Double, step: Double) = (0..((to - from) / step).roundToInt()).map { from + it * step }

private fun nearest(values: List<Double>, v: Double) = values.indices.minBy { abs(values[it] - v) }

private val arrows = listOf("↑", "→", "↓", "←")
private val dr = intArrayOf(-1, 0, 1, 0)
private val dc = intArrayOf(0, 1, 0, -1)

// ── Scenes ──

private sealed interface RsScene

private enum class RsCellKind { Plain, Goal, Pit, Wall, Cliff }

private class RsGCell(
    val text: String,
    val sub: String = "",
    val kind: RsCellKind = RsCellKind.Plain,
    val value: Double = 0.0,
    val ring: Color? = null,
    val highlight: Boolean = false,
)

private class RsGridScene(
    val rows: Int,
    val cols: Int,
    val cells: List<RsGCell>,
    /** Neutral cells (the cliff grid): values are printed but not tinted. */
    val neutral: Boolean = false,
    val solid: List<Pair<Int, Int>> = emptyList(),
    val dashed: List<Pair<Int, Int>> = emptyList(),
) : RsScene

private class RsArmRow(
    val label: String,
    val best: Boolean,
    val estimate: Double,
    val bonus: Double = 0.0,
    val scale: Double = 1.0,
    val text: String,
    val trueMean: Double,
    val right: String,
    val active: Boolean,
    val curve: List<Double>? = null,
    val draw: Double? = null,
)

private class RsArmsScene(val rows: List<RsArmRow>, val header: Pair<String, String>? = null) : RsScene

private class RsFrame(
    val headline: String,
    val body: String,
    val scene: RsScene,
    val action: String,
    val formula: List<String> = emptyList(),
    val legend: List<Triple<Color, SwatchStyle, String>> = emptyList(),
    val chips: List<LabChip> = emptyList(),
)

private class RsParam(val name: String, val symbol: String, val values: List<Double>, val initial: Int, val format: (Double) -> String)

/** Steps: a stepper over back + the frame's action. Button: a stepper over one button that bumps the flag. */
private enum class RsControl { Steps, Button }

private class RsLab(
    val frames: (param: Int, tab: Int, flag: Int) -> List<RsFrame>,
    val param: RsParam,
    val control: RsControl = RsControl.Steps,
    val tabs: List<String> = emptyList(),
    val startTab: Int = 0,
    val startIndex: Int = 0,
    val startFlag: Int = 0,
    val button: ((Int) -> String)? = null,
    val nextFlag: (Int) -> Int = { it },
)

private data class RsState(val param: Int, val tab: Int, val index: Int, val flag: Int)

// ── Grid world (4 × 4): goal, pit, wall, start ──

private const val GW = 4
private val goalCell = 0 to 3
private val pitCell = 1 to 3
private val wallCell = 1 to 1
private val startCell = 3 to 0
private const val STEP_REWARD = -0.04

private fun terminal(s: Pair<Int, Int>) = s == goalCell || s == pitCell

private fun move(s: Pair<Int, Int>, a: Int): Pair<Int, Int> {
    val n = (s.first + dr[a]) to (s.second + dc[a])
    return if (n.first !in 0 until GW || n.second !in 0 until GW || n == wallCell) s else n
}

private fun reward(n: Pair<Int, Int>) = when (n) {
    goalCell -> 1.0
    pitCell -> -1.0
    else -> STEP_REWARD
}

private fun tint(v: Double) = v

private fun gridCells(value: (Pair<Int, Int>) -> Pair<String, String>, v: (Pair<Int, Int>) -> Double, ring: Map<Pair<Int, Int>, Color> = emptyMap(), highlight: Pair<Int, Int>? = null) =
    (0 until GW).flatMap { r ->
        (0 until GW).map { c ->
            val s = r to c
            when (s) {
                goalCell -> RsGCell("1.00", kind = RsCellKind.Goal)
                pitCell -> RsGCell("−1.00", kind = RsCellKind.Pit)
                wallCell -> RsGCell("", kind = RsCellKind.Wall)
                else -> {
                    val (t, sub) = value(s)
                    RsGCell(t, sub, value = tint(v(s)), ring = ring[s], highlight = s == highlight)
                }
            }
        }
    }

// ── MDP: value iteration ──

private const val SWEEPS = 12

private fun mdpLab(): RsLab {
    val gammas = ladder(0.5, 0.99, 0.05).map { (it * 100).roundToInt() / 100.0 }.let { it + 0.99 }.distinct()
    return RsLab(
        param = RsParam("Discount", "γ", gammas, nearest(gammas, 0.9)) { rx(it) },
        startIndex = 5,
        frames = { p, _, _ ->
            val gamma = gammas[p]
            fun outcomes(s: Pair<Int, Int>, a: Int) = listOf(move(s, a) to 0.8, move(s, (a + 1) % 4) to 0.1, move(s, (a + 3) % 4) to 0.1)
            val vs = mutableListOf(HashMap<Pair<Int, Int>, Double>().also { m -> m[goalCell] = 1.0; m[pitCell] = -1.0 })
            repeat(SWEEPS) {
                val old = vs.last()
                val next = HashMap(old)
                for (r in 0 until GW) for (c in 0 until GW) {
                    val s = r to c
                    if (terminal(s) || s == wallCell) continue
                    next[s] = (0..3).maxOf { a -> STEP_REWARD + gamma * outcomes(s, a).sumOf { (n, q) -> q * (old[n] ?: 0.0) } }
                }
                vs += next
            }
            fun best(v: Map<Pair<Int, Int>, Double>, s: Pair<Int, Int>) = (0..3).maxBy { a -> outcomes(s, a).sumOf { (n, q) -> q * (v[n] ?: 0.0) } }
            val legend = listOf(
                Triple(GoalGreen, SwatchStyle.Fill, "Goal +1"),
                Triple(PitRed, SwatchStyle.Fill, "Pit −1"),
                Triple(SimColors.Active, SwatchStyle.Ring, "Backing up"),
                Triple(SimColors.Green.copy(alpha = 0.45f), SwatchStyle.Fill, "Higher value"),
            )
            (0..SWEEPS).map { k ->
                val v = vs[k]
                if (k == 0) {
                    RsFrame(
                        "Every value starts at {0}; only the goal and the pit are known.",
                        "Each sweep replaces every value with the best one-step lookahead: reward now, plus γ times what the move leads to.",
                        RsGridScene(GW, GW, gridCells({ s -> rx(v[s] ?: 0.0) to (if (s == startCell) "start" else "") }, { v[it] ?: 0.0 })),
                        "Next Sweep",
                        legend = legend,
                    )
                } else {
                    val prev = vs[k - 1]
                    val cells = (0 until GW).flatMap { r -> (0 until GW).map { r to it } }.filter { !terminal(it) && it != wallCell }
                    val focus = cells.maxBy { abs((v[it] ?: 0.0) - (prev[it] ?: 0.0)) }
                    val delta = cells.maxOf { abs((v[it] ?: 0.0) - (prev[it] ?: 0.0)) }
                    val a = best(prev, focus)
                    val parts = outcomes(focus, a)
                    val expected = parts.sumOf { (n, q) -> q * (prev[n] ?: 0.0) }
                    RsFrame(
                        if (delta < 0.005) "Sweep $k changes no value by more than {0.005}: the values have converged."
                        else "Sweep $k backs up the {yellow cell}: its best move now sees further toward the goal.",
                        "Values spread outward from the goal one sweep at a time. The arrows are the greedy policy so far.",
                        RsGridScene(
                            GW,
                            GW,
                            gridCells(
                                { s ->
                                    val text = if (s == focus) "${rx(prev[s] ?: 0.0)}→${rx(v[s] ?: 0.0)}" else rx(v[s] ?: 0.0)
                                    text to (arrows[best(v, s)] + if (s == startCell) " start" else "")
                                },
                                { v[it] ?: 0.0 },
                                ring = mapOf(focus to SimColors.Active),
                                highlight = focus,
                            ),
                        ),
                        if (k < SWEEPS) "Next Sweep" else "Start Over",
                        formula = listOf(
                            "${arrows[a]} " + parts.joinToString(" + ") { (n, q) -> "${rx(q, 1)}×${rx(prev[n] ?: 0.0)}" },
                            "V = −0.04 + ${rx(gamma)} × ${rx(expected)} = {v:${rx(STEP_REWARD + gamma * expected)}}",
                        ),
                        legend = legend,
                    )
                }
            }
        },
    )
}

// ── Q-learning on the grid world ──

private const val Q_PRETRAIN = 60
private const val Q_STEPS = 30
private const val Q_EPS = 0.2
private const val Q_GAMMA = 0.9

private fun qLearningLab(): RsLab {
    val alphas = ladder(0.1, 1.0, 0.1)
    return RsLab(
        param = RsParam("Learning rate", "α", alphas, nearest(alphas, 0.5)) { rx(it) },
        frames = { p, _, _ ->
            val alpha = alphas[p]
            val random = Random(31)
            val q = HashMap<Pair<Int, Int>, DoubleArray>()
            fun qs(s: Pair<Int, Int>) = q.getOrPut(s) { DoubleArray(4) }
            fun maxQ(s: Pair<Int, Int>) = if (terminal(s)) 0.0 else qs(s).max()
            fun greedy(s: Pair<Int, Int>) = qs(s).indices.maxBy { qs(s)[it] }
            class Step(val s: Pair<Int, Int>, val a: Int, val explore: Boolean, val n: Pair<Int, Int>, val r: Double, val old: Double, val target: Double, val new: Double, val nextMax: Double)
            fun act(s: Pair<Int, Int>): Step {
                val explore = random.nextDouble() < Q_EPS
                val a = if (explore) random.nextInt(4) else greedy(s)
                val n = move(s, a)
                val r = reward(n)
                val nm = maxQ(n)
                val target = r + Q_GAMMA * nm
                val old = qs(s)[a]
                qs(s)[a] = old + alpha * (target - old)
                return Step(s, a, explore, n, r, old, target, qs(s)[a], nm)
            }
            repeat(Q_PRETRAIN) {
                var s = startCell
                var guard = 0
                while (!terminal(s) && guard++ < 60) s = act(s).n
            }
            var s = startCell
            val legend = listOf(
                Triple(SimColors.Active, SwatchStyle.Ring, "State s"),
                Triple(SimColors.Blue, SwatchStyle.Ring, "Next s′"),
                Triple(SimColors.Green.copy(alpha = 0.45f), SwatchStyle.Fill, "max Q"),
            )
            (1..Q_STEPS).map { k ->
                val st = act(s)
                s = if (terminal(st.n)) startCell else st.n
                val snapshot = (0 until GW).flatMap { r -> (0 until GW).map { r to it } }.associateWith { maxQ(it) }
                val arrowsNow = (0 until GW).flatMap { r -> (0 until GW).map { r to it } }.associateWith { if (terminal(it)) "" else arrows[greedy(it)] }
                val took = arrows[st.a]
                val (headline, body) = when {
                    terminal(st.n) -> "The step reaches {${if (st.n == goalCell) "the goal" else "the pit"}}, so the target is just the reward, ${rx(st.r)}." to
                        "A terminal state has no future to add. The episode ends and the agent starts over from the corner."
                    st.explore -> "The agent explored {$took}, but the update still assumes greedy play from s′." to
                        "That is what off-policy means: it learns the greedy policy while behaving ε-greedily."
                    else -> "The agent took its greedy move {$took}, and the target uses the same greedy value." to
                        "When behaviour and target agree, Q-learning and SARSA make the same update."
                }
                RsFrame(
                    headline,
                    body,
                    RsGridScene(
                        GW,
                        GW,
                        gridCells(
                            { c -> rx(snapshot[c] ?: 0.0) to (arrowsNow[c] ?: "") },
                            { snapshot[it] ?: 0.0 },
                            ring = if (terminal(st.n) || st.n == st.s) mapOf(st.s to SimColors.Active) else mapOf(st.s to SimColors.Active, st.n to SimColors.Blue),
                            highlight = st.s,
                        ),
                    ),
                    if (k < Q_STEPS) "Take Step" else "Start Over",
                    formula = listOf(
                        "took {$took} (${if (st.explore) "explore" else "greedy"}) · target uses max Q(s′) = ${rx(st.nextMax)}",
                        "target = ${rx(st.r)} + ${rx(Q_GAMMA, 1)} × ${rx(st.nextMax)} = ${rx(st.target)}",
                        "Q ← ${rx(st.old)} + ${rx(alpha, 1)} × (${rx(st.target)} − ${rx(st.old)}) = {v:${rx(st.new)}}",
                    ),
                    legend = legend,
                )
            }
        },
    )
}

// ── SARSA vs Q-learning on the cliff ──

private const val CR = 4
private const val CC = 6

/** Moves that stay on the cliff grid; a walk off the edge is never offered. */
private fun cliffMoves(s: Pair<Int, Int>) = (0..3).filter { a -> (s.first + dr[a]) in 0 until CR && (s.second + dc[a]) in 0 until CC }

private class RsCliffRun(val q: HashMap<Pair<Int, Int>, DoubleArray>, val returns: List<Double>)

private fun cliffTrain(sarsa: Boolean, episodes: Int, eps: Double): RsCliffRun {
    val random = Random(if (sarsa) 51 else 52)
    val q = HashMap<Pair<Int, Int>, DoubleArray>()
    fun qs(s: Pair<Int, Int>) = q.getOrPut(s) { DoubleArray(4) }
    fun pick(s: Pair<Int, Int>): Int {
        val moves = cliffMoves(s)
        return if (random.nextDouble() < eps) moves[random.nextInt(moves.size)] else moves.maxBy { qs(s)[it] }
    }
    val returns = mutableListOf<Double>()
    repeat(episodes) {
        var s = (CR - 1) to 0
        var a = pick(s)
        var total = 0.0
        var steps = 0
        while (steps++ < 200) {
            val n = (s.first + dr[a]) to (s.second + dc[a])
            val cliff = n.first == CR - 1 && n.second in 1 until CC - 1
            val goal = n == (CR - 1) to (CC - 1)
            val r = if (cliff) -100.0 else -1.0
            total += r
            if (cliff || goal) {
                qs(s)[a] += 0.5 * (r - qs(s)[a])
                break
            }
            val a2 = pick(n)
            val next = if (sarsa) qs(n)[a2] else cliffMoves(n).maxOf { qs(n)[it] }
            qs(s)[a] += 0.5 * (r + next - qs(s)[a])
            s = n
            a = a2
        }
        returns += total
    }
    return RsCliffRun(q, returns)
}

private fun greedyPath(q: HashMap<Pair<Int, Int>, DoubleArray>): List<Pair<Int, Int>> {
    var s = (CR - 1) to 0
    val path = mutableListOf(s)
    while (path.size < 30) {
        val qs = q[s] ?: break
        // The best move that doesn't revisit a cell, so an unsettled value can't send the path in circles.
        val a = cliffMoves(s).filter { ((s.first + dr[it]) to (s.second + dc[it])) !in path }.maxByOrNull { qs[it] } ?: break
        val n = (s.first + dr[a]) to (s.second + dc[a])
        path += n
        s = n
        if (n.first == CR - 1 && n.second >= 1) break
    }
    return path
}

private fun sarsaLab(): RsLab {
    val epsilons = ladder(0.0, 0.3, 0.05)
    return RsLab(
        param = RsParam("Exploration", "ε", epsilons, nearest(epsilons, 0.1)) { rx(it) },
        control = RsControl.Button,
        tabs = listOf("SARSA", "Q-learning"),
        startFlag = 4,
        button = { "Train 100 Episodes" },
        nextFlag = { if (it >= 10) 1 else it + 1 },
        frames = { p, tab, flag ->
            val eps = epsilons[p]
            val episodes = flag * 100
            val sarsa = cliffTrain(true, episodes, eps)
            val ql = cliffTrain(false, episodes, eps)
            val sp = greedyPath(sarsa.q)
            val qp = greedyPath(ql.q)
            val shown = if (tab == 0) sarsa else ql
            val cells = (0 until CR).flatMap { r ->
                (0 until CC).map { c ->
                    when {
                        r == CR - 1 && c == CC - 1 -> RsGCell("goal", kind = RsCellKind.Goal)
                        r == CR - 1 && c in 1 until CC - 1 -> RsGCell("-100", kind = RsCellKind.Cliff)
                        else -> RsGCell("${(shown.q[r to c]?.let { q -> cliffMoves(r to c).maxOf { q[it] } } ?: 0.0).roundToInt()}", ring = if (r == CR - 1 && c == 0) SimColors.Active else null)
                    }
                }
            }
            fun avg(run: RsCliffRun) = run.returns.takeLast(100).average()
            val sSteps = sp.size - 1
            val qSteps = qp.size - 1
            val safer = sp.minOf { it.first } < qp.minOf { it.first }
            val (headline, body) = when {
                tab == 1 -> "Q-learning's greedy path {hugs the edge}: $qSteps steps, the shortest there is." to
                    "Its target assumes greedy play, so it ignores the random steps that sometimes walk it off the cliff while training."
                safer -> "After $episodes episodes SARSA {keeps clear of the cliff}; Q-learning hugs the edge." to
                    "Its target counts its own random steps, so the edge looks risky."
                else -> "After $episodes episodes SARSA's path {matches Q-learning's}." to
                    "With ε = ${rx(eps)} few random steps happen near the edge, so the safe detour isn't worth it."
            }
            listOf(
                RsFrame(
                    headline,
                    body,
                    RsGridScene(CR, CC, cells, neutral = true, solid = if (tab == 0) sp else qp, dashed = if (tab == 0) qp else sp),
                    "",
                    formula = listOf("SARSA: r + γ·Q(s′, {a′ actually taken})", "Q-learning: r + γ·max Q(s′)"),
                    legend = listOf(
                        Triple(Color.Unspecified, SwatchStyle.Line, if (tab == 0) "SARSA path" else "Q-learning path"),
                        Triple(Color.White, SwatchStyle.DashedLine, if (tab == 0) "Q-learning path" else "SARSA path"),
                        Triple(PitRed.copy(alpha = 0.6f), SwatchStyle.Fill, "Cliff"),
                    ),
                    chips = listOf(
                        LabChip("steps", "$sSteps vs $qSteps", tint = StoryTone.Answer),
                        LabChip("avg return", "${avg(sarsa).roundToInt()} vs ${avg(ql).roundToInt()}", good = true),
                    ),
                ),
            )
        },
    )
}

// ── Bandits ──

private val armNames = listOf("A", "B", "C", "D")
private val trueMeans = listOf(0.45, 0.6, 0.4, 0.72)
private const val BEST_ARM = 3
private const val PULLS = 200

private class RsBanditStep(val arm: Int, val explore: Boolean, val roll: Double, val counts: IntArray, val sums: DoubleArray)

private fun estimate(counts: IntArray, sums: DoubleArray, i: Int) = if (counts[i] == 0) 0.0 else sums[i] / counts[i]

private fun banditRun(strategy: Int, eps: Double): List<RsBanditStep> {
    val random = Random(71)
    val counts = IntArray(4)
    val sums = DoubleArray(4)
    return (1..PULLS).map {
        val roll = random.nextDouble()
        val greedy = (0..3).maxBy { estimate(counts, sums, it) }
        val explore = strategy == 0 || (strategy == 2 && roll < eps)
        val arm = if (explore) random.nextInt(4) else greedy
        val r = if (random.nextDouble() < trueMeans[arm]) 1.0 else 0.0
        counts[arm]++
        sums[arm] += r
        RsBanditStep(arm, explore, roll, counts.copyOf(), sums.copyOf())
    }
}

private val armLegend = listOf(
    Triple(SimColors.Active, SwatchStyle.Fill, "Pulled now"),
    Triple(SimColors.Blue, SwatchStyle.Fill, "Estimate"),
    Triple(Color.White, SwatchStyle.Line, "True mean"),
)

private fun banditLab(): RsLab {
    val epsilons = ladder(0.0, 0.5, 0.05)
    return RsLab(
        param = RsParam("Exploration", "ε", epsilons, nearest(epsilons, 0.1)) { rx(it) },
        tabs = listOf("Random", "Greedy", "ε-greedy"),
        startTab = 2,
        startIndex = 56,
        frames = { p, tab, _ ->
            val eps = epsilons[p]
            val run = banditRun(tab, eps)
            run.mapIndexed { i, st ->
                val t = i + 1
                val greedyBefore = if (i == 0) 0 else (0..3).maxBy { estimate(run[i - 1].counts, run[i - 1].sums, it) }
                val arm = armNames[st.arm]
                val rows = (0..3).map { a ->
                    val e = estimate(st.counts, st.sums, a)
                    RsArmRow(armNames[a], a == BEST_ARM, e, text = rx(e), trueMean = trueMeans[a], right = "${st.counts[a]} pulls", active = a == st.arm)
                }
                val formula = when (tab) {
                    0 -> "random arm {$arm}"
                    1 -> "greedy arm {$arm} (highest estimate)"
                    else -> if (st.explore) "roll ${rx(st.roll)} < ε ${rx(eps)} → {explore}: random arm $arm"
                    else "roll ${rx(st.roll)} ≥ ε ${rx(eps)} → exploit: greedy arm {$arm}"
                }
                val (headline, body) = when {
                    tab == 0 -> "Random pulls {$arm}: it never uses what it has learned." to
                        "Every arm gets a quarter of the pulls, so the best one is pulled no more than the worst."
                    tab == 1 -> "Greedy pulls {$arm} again: the highest estimate so far." to
                        "An arm that looked bad early is never tried again, so greedy can lock onto the wrong one."
                    st.explore -> "The greedy pick is ${armNames[greedyBefore]}, but this roll sends the pull to {$arm}." to
                        "One pull in ${if (eps > 0) (1 / eps).roundToInt() else 0} is random, so a weak early estimate can still recover. Random never exploits; greedy never explores."
                    else -> "This roll exploits: the pull goes to the greedy pick {$arm}." to
                        "Most pulls go to the best estimate, and the rest keep checking the others."
                }
                RsFrame(
                    headline,
                    body,
                    RsArmsScene(rows),
                    if (t < PULLS) "Pull Arm" else "Start Over",
                    formula = listOf(formula),
                    legend = armLegend,
                    chips = listOf(
                        LabChip("greedy pick", armNames[greedyBefore], tint = StoryTone.Path),
                        LabChip("${armNames[BEST_ARM]} pulls", "${st.counts[BEST_ARM]} / $t", tint = StoryTone.Answer),
                    ),
                )
            }
        },
    )
}

private fun ucbLab(): RsLab {
    val cs = ladder(0.5, 4.0, 0.5)
    return RsLab(
        param = RsParam("Bonus scale", "c", cs, nearest(cs, 2.0)) { rx(it, 1) },
        startIndex = 12,
        frames = { p, _, _ ->
            val c = cs[p]
            val random = Random(81)
            val counts = IntArray(4)
            val sums = DoubleArray(4)
            (1..PULLS).map { t ->
                val est = (0..3).map { estimate(counts, sums, it) }
                val bonus = (0..3).map { if (counts[it] == 0) 0.0 else sqrt(c * ln(t.toDouble()) / counts[it]) }
                val total = (0..3).map { est[it] + bonus[it] }
                val chosen = if (t <= 4) t - 1 else (0..3).maxBy { total[it] }
                val bestEst = (0..3).maxBy { est[it] }
                val scale = max(1.0, total.max() * 1.08)
                val rows = (0..3).map { a ->
                    RsArmRow(armNames[a], a == BEST_ARM, est[a], bonus[a], scale, text = rx(total[a]), trueMean = trueMeans[a], right = "${counts[a]} pulls", active = a == chosen)
                }
                val name = armNames[chosen]
                val (headline, body) = when {
                    t <= 4 -> "Every arm is tried {once} before any bonus can be computed." to
                        "The bonus divides by the pull count, so an untried arm would have an infinite one."
                    chosen != bestEst -> "{$name} has only ${counts[chosen]} pulls, so its bonus lifts it above ${armNames[bestEst]}." to
                        "Each bar shows estimate plus bonus. The bonus shrinks with every pull, so rarely tried arms get another look."
                    else -> "{$name} leads on its estimate and its bonus doesn't change that: UCB exploits." to
                        "As pulls pile up every bonus shrinks, and the arm with the best estimate wins more often."
                }
                val formula = if (t <= 4) "$name: untried, so it is pulled first"
                else "$name: ${rx(est[chosen])} + √(${rx(c, 1).removeSuffix(".0")} ln $t / ${counts[chosen]}) = ${rx(est[chosen])} + ${rx(bonus[chosen])} = {v:${rx(total[chosen])}}"
                val frame = RsFrame(
                    headline,
                    body,
                    RsArmsScene(rows),
                    if (t < PULLS) "Pull Arm" else "Start Over",
                    formula = listOf(formula),
                    legend = listOf(
                        Triple(SimColors.Blue, SwatchStyle.Fill, "Estimate"),
                        Triple(Color(0xFF4C3F91), SwatchStyle.Fill, "Bonus"),
                        Triple(Color.White, SwatchStyle.Line, "True mean"),
                    ),
                    chips = listOf(LabChip("pull", "$t / $PULLS"), LabChip("best estimate", armNames[bestEst], tint = StoryTone.Path)),
                )
                counts[chosen]++
                sums[chosen] += if (random.nextDouble() < trueMeans[chosen]) 1.0 else 0.0
                frame
            }
        },
    )
}

// ── Thompson sampling ──

private fun logGamma(x: Double): Double {
    val g = doubleArrayOf(76.18009172947146, -86.50532032941677, 24.01409824083091, -1.231739572450155, 0.1208650973866179e-2, -0.5395239384953e-5)
    var y = x
    val tmp = x + 5.5 - (x + 0.5) * ln(x + 5.5)
    var ser = 1.000000000190015
    for (c in g) { y += 1; ser += c / y }
    return -tmp + ln(2.5066282746310005 * ser / x)
}

private fun betaPdf(x: Double, a: Double, b: Double) =
    exp((a - 1) * ln(x) + (b - 1) * ln(1 - x) + logGamma(a + b) - logGamma(a) - logGamma(b))

private class RsGaussian(seed: Int) {
    val random = Random(seed)
    fun uniform() = random.nextDouble()
    fun normal(): Double {
        val u = max(random.nextDouble(), 1e-12)
        return sqrt(-2 * ln(u)) * cos(2 * PI * random.nextDouble())
    }

    /** Marsaglia–Tsang for shape ≥ 1. */
    fun gamma(k: Double): Double {
        val d = k - 1.0 / 3
        val c = 1 / sqrt(9 * d)
        while (true) {
            val x = normal()
            val v = (1 + c * x).pow(3)
            if (v <= 0) continue
            val u = max(uniform(), 1e-12)
            if (ln(u) < 0.5 * x * x + d - d * v + d * ln(v)) return d * v
        }
    }

    fun beta(a: Double, b: Double): Double {
        val x = gamma(a)
        return x / (x + gamma(b))
    }
}

private fun thompsonLab(): RsLab = RsLab(
    param = RsParam("Prior strength", "α₀", listOf(1.0), 0) { rx(it, 0) },
    startIndex = 41,
    frames = { _, _, _ ->
        val g = RsGaussian(91)
        val wins = IntArray(4)
        val losses = IntArray(4)
        (1..PULLS).map { t ->
            val draws = (0..3).map { g.beta(1.0 + wins[it], 1.0 + losses[it]) }
            val winner = (0..3).maxBy { draws[it] }
            val bestMean = (0..3).maxBy { (1.0 + wins[it]) / (2.0 + wins[it] + losses[it]) }
            val rows = (0..3).map { a ->
                val alpha = 1.0 + wins[a]
                val beta = 1.0 + losses[a]
                val pts = (1..59).map { betaPdf(it / 60.0, alpha, beta) }
                val top = pts.max()
                RsArmRow(armNames[a], a == BEST_ARM, 0.0, text = "", trueMean = trueMeans[a], right = rx(draws[a]), active = a == winner, curve = pts.map { it / top }, draw = draws[a])
            }
            val w = armNames[winner]
            val runnerUp = (0..3).filter { it != winner }.maxBy { draws[it] }
            val (headline, body) = if (winner != bestMean) {
                "{$w}'s wide posterior drew ${rx(draws[winner])}, beating ${armNames[runnerUp]}'s ${rx(draws[runnerUp])}." to
                    "Arms with few pulls have wide curves, so they sometimes draw high. That is the exploration."
            } else {
                "{$w} drew highest again: its posterior is narrow and high." to
                    "As pulls pile up, the best arm's curve narrows and wins most draws. No ε, no bonus: the posterior's own width decides."
            }
            val frame = RsFrame(
                headline,
                body,
                RsArmsScene(rows, header = "Posterior per arm" to "draw"),
                if (t < PULLS) "Draw and Pull" else "Start Over",
                formula = listOf("$w ~ Beta(1+${wins[winner]}, 1+${losses[winner]}) → draw {v:${rx(draws[winner])}}"),
                legend = listOf(
                    Triple(Violet, SwatchStyle.DashedLine, "This draw"),
                    Triple(Color.White, SwatchStyle.Line, "True mean"),
                    Triple(Color(0xFF8A7440), SwatchStyle.Fill, "Winner"),
                ),
                chips = listOf(LabChip("pull", "$t / $PULLS"), LabChip("${armNames[BEST_ARM]} pulls", "${wins[BEST_ARM] + losses[BEST_ARM]}", tint = StoryTone.Answer)),
            )
            if (g.uniform() < trueMeans[winner]) wins[winner]++ else losses[winner]++
            frame
        }
    },
)

private fun rlLab(topicId: String): RsLab = when (topicId) {
    "sarsa" -> sarsaLab()
    "mdp" -> mdpLab()
    "thompson_sampling" -> thompsonLab()
    "multi_armed_bandit" -> banditLab()
    "ucb" -> ucbLab()
    else -> qLearningLab()
}

// ── Lab ──

@Composable
internal fun RlStorySection(topicId: String) {
    val lab = remember(topicId) { rlLab(topicId) }
    val initial = remember(topicId) { RsState(lab.param.initial, lab.startTab, lab.startIndex, lab.startFlag) }
    var state by remember(topicId) { mutableStateOf(initial) }
    val frames = remember(state.param, state.tab, state.flag) { lab.frames(state.param, state.tab, state.flag) }
    val index = state.index.coerceIn(0, frames.lastIndex)
    val frame = frames[index]
    val dock = LocalLabDock.current
    // Thompson sampling has nothing to tune; its stepper is hidden.
    val showStepper = lab.param.values.size > 1

    val controls: @Composable () -> Unit = {
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (showStepper) {
                val p = lab.param
                LabParamStepper(LabParam(p.name, p.name, p.symbol, p.format(p.values[state.param]), state.param > 0, state.param < p.values.lastIndex)) { d ->
                    state = state.copy(param = (state.param + d).coerceIn(0, p.values.lastIndex))
                }
            }
            when (lab.control) {
                RsControl.Steps -> LabBackActionRow(
                    frame.action,
                    backEnabled = index > 0,
                    onBack = { state = state.copy(index = index - 1) },
                    onAction = { state = state.copy(index = if (index >= frames.lastIndex) 0 else index + 1) },
                )
                RsControl.Button -> LabButton(lab.button!!(state.flag), primary = true, modifier = Modifier.fillMaxWidth()) {
                    state = state.copy(flag = lab.nextFlag(state.flag))
                }
            }
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        if (lab.tabs.isNotEmpty()) LabSegments(lab.tabs, state.tab, Modifier.padding(bottom = 14.dp)) { state = state.copy(tab = it) }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                when (val scene = frame.scene) {
                    is RsGridScene -> GridView(scene)
                    is RsArmsScene -> ArmsView(scene)
                }
                if (frame.formula.isNotEmpty()) RlFormula(frame.formula, Modifier.padding(top = 12.dp))
                val accent = MaterialTheme.colorScheme.primary
                StoryLegendRow(frame.legend.map { Triple(if (it.first == Color.Unspecified) accent else it.first, it.second, it.third) }, Modifier.padding(top = 14.dp))
            }
        }
        LabChips(frame.chips, Modifier.padding(top = 16.dp))
        LabStoryNarration(frame.headline, frame.body, Modifier.padding(top = 16.dp))
        if (dock == null) {
            HorizontalDivider(modifier = Modifier.padding(top = 16.dp, bottom = 14.dp), color = MaterialTheme.colorScheme.outline)
            controls()
        }
    }

    if (dock != null) {
        SideEffect {
            dock.controls = controls
            dock.navAction = LabNavAction(Icons.Filled.Refresh, "Reset") { state = initial }
        }
        DisposableEffect(dock) {
            onDispose {
                dock.controls = null
                dock.navAction = null
            }
        }
    }
}

// ── Rendering ──

@Composable
private fun RlFormula(lines: List<String>, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(SimColors.Tint, RoundedCornerShape(10.dp))
            .padding(vertical = 10.dp, horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        lines.forEach { line ->
            Text(
                storyAnnotated(line),
                fontFamily = IBMPlexMono,
                fontSize = 13.sp,
                lineHeight = 20.sp,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
            )
        }
    }
}

@Composable
private fun GridView(scene: RsGridScene) {
    val accent = MaterialTheme.colorScheme.primary
    val onSurface = MaterialTheme.colorScheme.onSurface
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val gap = 6.dp
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val cell = (maxWidth - gap * (scene.cols - 1)) / scene.cols
        val cellH = if (scene.neutral) cell * 0.78f else cell * 0.72f
        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            (0 until scene.rows).forEach { r ->
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    (0 until scene.cols).forEach { c ->
                        val g = scene.cells[r * scene.cols + c]
                        val fill = when (g.kind) {
                            RsCellKind.Goal -> GoalGreen
                            RsCellKind.Pit -> PitRed
                            RsCellKind.Cliff -> PitRed.copy(alpha = 0.55f)
                            RsCellKind.Wall -> SimColors.Tint.copy(alpha = 1f)
                            RsCellKind.Plain -> when {
                                scene.neutral -> SimColors.Tint
                                g.value > 0.005 -> SimColors.Green.copy(alpha = (0.12 + 0.4 * min(g.value, 1.0)).toFloat())
                                g.value < -0.005 -> SimColors.Red.copy(alpha = (0.12 + 0.3 * min(-g.value, 1.0)).toFloat())
                                else -> SimColors.Tint
                            }
                        }
                        Column(
                            modifier = Modifier
                                .width(cell)
                                .height(cellH)
                                .clip(RoundedCornerShape(9.dp))
                                .background(fill)
                                .then(if (g.ring != null) Modifier.border(2.dp, g.ring, RoundedCornerShape(9.dp)) else Modifier),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            if (g.text.isNotEmpty()) {
                                Text(
                                    g.text,
                                    fontFamily = IBMPlexMono,
                                    fontSize = if (scene.neutral) 12.sp else if (g.text.length > 6) 11.sp else 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    color = when {
                                        g.highlight -> StoryTone.Active.ink()
                                        g.kind == RsCellKind.Plain && scene.neutral -> onSurface.copy(alpha = 0.75f)
                                        else -> onSurface
                                    },
                                )
                            }
                            if (g.sub.isNotEmpty()) Text(g.sub, fontSize = 12.sp, maxLines = 1, color = if (g.highlight) StoryTone.Active.ink() else muted)
                        }
                    }
                }
            }
        }
        // Paths through cell centres, drawn over the grid.
        if (scene.solid.isNotEmpty() || scene.dashed.isNotEmpty()) {
            Canvas(modifier = Modifier.matchParentSize()) {
                val cw = cell.toPx()
                val ch = cellH.toPx()
                val g = gap.toPx()
                fun centre(p: Pair<Int, Int>) = Offset(p.second * (cw + g) + cw / 2, p.first * (ch + g) + ch / 2)
                fun line(ps: List<Pair<Int, Int>>, color: Color, dash: Boolean, width: Float) {
                    if (ps.size < 2) return
                    val path = Path()
                    ps.forEachIndexed { i, p -> val o = centre(p); if (i == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y) }
                    drawPath(path, color, style = Stroke(width = width.dp.toPx(), pathEffect = if (dash) PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())) else null))
                }
                line(scene.dashed, Color.White.copy(alpha = 0.9f), true, 1.5f)
                line(scene.solid, accent, false, 3.5f)
            }
        }
    }
}

@Composable
private fun ArmsView(scene: RsArmsScene) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        scene.header?.let { (l, r) ->
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(l, fontSize = 14.sp, color = muted, modifier = Modifier.weight(1f))
                Text(r, fontSize = 14.sp, color = muted)
            }
        }
        scene.rows.forEach { row -> ArmRowView(row) }
    }
}

@Composable
private fun ArmRowView(row: RsArmRow) {
    val activeInk = StoryTone.Active.ink()
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.width(34.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(row.label, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = if (row.active) activeInk else MaterialTheme.colorScheme.onSurface)
            if (row.best) Text("BEST", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = StoryTone.Done.ink())
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .height(44.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(if (row.active && row.curve != null) SimColors.Active.copy(alpha = 0.3f) else SimColors.Tint)
                .then(if (row.active) Modifier.border(2.dp, SimColors.Active, RoundedCornerShape(10.dp)) else Modifier),
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                if (row.curve != null) {
                    val path = Path()
                    val fill = Path()
                    fill.moveTo(0f, h)
                    row.curve.forEachIndexed { i, v ->
                        val x = (i + 1) / 60f * w
                        val y = h - 4.dp.toPx() - v.toFloat() * (h - 10.dp.toPx())
                        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                        fill.lineTo(x, y)
                    }
                    fill.lineTo(w, h)
                    fill.close()
                    val color = if (row.active) SimColors.Active else SimColors.Blue
                    drawPath(fill, color.copy(alpha = 0.25f))
                    drawPath(path, color, style = Stroke(width = 1.5.dp.toPx()))
                    row.draw?.let { d ->
                        drawLine(if (row.active) SimColors.Active else Violet, Offset(d.toFloat() * w, 6.dp.toPx()), Offset(d.toFloat() * w, h - 6.dp.toPx()), strokeWidth = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 2.dp.toPx())))
                    }
                } else {
                    val estW = (row.estimate / row.scale).toFloat().coerceIn(0f, 1f) * w
                    val bonusW = (row.bonus / row.scale).toFloat().coerceIn(0f, 1f) * w
                    drawRect(if (row.active) SimColors.Active else SimColors.Blue, size = androidx.compose.ui.geometry.Size(estW, h))
                    if (bonusW > 0) drawRect((if (row.active) SimColors.Active else Color(0xFF4C3F91)).copy(alpha = if (row.active) 0.45f else 1f), topLeft = Offset(estW, 0f), size = androidx.compose.ui.geometry.Size(min(bonusW, w - estW), h))
                }
                val tx = (row.trueMean / row.scale).toFloat() * w
                drawLine(Color.White, Offset(tx, 8.dp.toPx()), Offset(tx, h - 8.dp.toPx()), strokeWidth = 2.5.dp.toPx())
            }
            if (row.text.isNotEmpty()) {
                Text(
                    row.text,
                    fontFamily = IBMPlexMono,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (row.active) Color(0xFF1F1A0A) else Color.White,
                    modifier = Modifier.align(Alignment.CenterStart).padding(start = 10.dp),
                )
            }
        }
        Text(
            row.right,
            fontFamily = IBMPlexMono,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.End,
            color = if (row.active && row.curve != null) activeInk else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.width(76.dp),
        )
    }
}
