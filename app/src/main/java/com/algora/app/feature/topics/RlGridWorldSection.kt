package com.algora.app.feature.topics

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.algora.app.core.ui.theme.SimColors
import kotlin.math.abs
import kotlin.random.Random

// ── Reinforcement-learning grid world ────────────────────────────────────────
// The RL topics need an environment loop, not another structure animation: an agent moving through
// states, collecting reward, and a value/policy table that improves as a result. This runs the real
// algorithm (value iteration or tabular Q-learning) over a 4×4 grid with a fixed seed, snapshotting
// the value table + greedy policy per sweep/episode, and plays those back with PlaybackTransport.
//
// Cell shading is the state value; the glyph is the greedy action. Watching the arrows flip from
// noise into a coherent path is the whole point.

// The grid is a parameter, not a constant, because one topic genuinely needs a different one: SARSA's
// whole point is invisible unless the layout punishes walking beside a hazard, which the default
// world does not. Everything else uses DefaultWorld through the shims below, unchanged.
private class RlWorld(
    val rows: Int,
    val cols: Int,
    val start: Int,
    val goal: Int,
    val pits: Set<Int>,
    val walls: Set<Int>,
    val stepCost: Float,
    val goalReward: Float,
    val pitReward: Float,
) {
    val states = rows * cols

    // up, down, left, right — index order is also the arrow-glyph order.
    val actions = listOf(-cols, cols, -1, 1)

    fun isTerminal(s: Int) = s == goal || s in pits

    fun step(state: Int, action: Int): Int {
        val row = state / cols
        val col = state % cols
        val next = state + action
        // Reject moves that leave the grid, wrap a row edge, or walk into a wall.
        if (next < 0 || next >= states) return state
        if (abs(action) == 1 && next / cols != row) return state
        if (abs(action) == 1 && abs((next % cols) - col) != 1) return state
        if (next in walls) return state
        return next
    }

    fun rewardFor(state: Int) = when {
        state == goal -> goalReward
        state in pits -> pitReward
        else -> stepCost
    }

    // Terminals are absorbing: the reward is collected on the transition into them and nothing flows
    // back out, otherwise the goal acts as a perpetual reward source and values inflate past +1.
    fun backup(state: Int, action: Int, values: FloatArray, gamma: Float): Float {
        val next = step(state, action)
        return rewardFor(next) + if (isTerminal(next)) 0f else gamma * values[next]
    }

    fun greedyPolicy(values: FloatArray, gamma: Float): IntArray = IntArray(states) { s ->
        if (isTerminal(s) || s in walls) -1
        else actions.indices.maxByOrNull { a -> backup(s, actions[a], values, gamma) } ?: 0
    }
}

private val DefaultWorld = RlWorld(
    rows = 4, cols = 4,
    start = 12,                 // (3,0) bottom-left
    goal = 3,                   // (0,3) top-right, reward +1
    pits = setOf(7),            // (1,3) directly below it, reward −1
    walls = setOf(5),           // (1,1) impassable
    stepCost = -0.04f,          // small step cost, so dawdling is punished
    goalReward = 1f,
    pitReward = -1f,
)

// Sutton & Barto's cliff walk, compressed to 4×4: start bottom-left, goal bottom-right, and a cliff
// between them. The 5-step route runs along the cliff edge; the safe route detours through the top
// row. The 100:1 penalty ratio is the textbook's, and it is what makes the two algorithms disagree.
private val CliffWorld = RlWorld(
    rows = 4, cols = 4,
    start = 12,
    goal = 15,
    pits = setOf(13, 14),
    walls = emptySet(),
    stepCost = -1f,
    goalReward = 0f,
    pitReward = -100f,
)

private const val RL_ROWS = 4
private const val RL_COLS = 4
private val RL_STATES = DefaultWorld.states

private val RL_GOAL = DefaultWorld.goal
private val RL_PIT = DefaultWorld.pits.first()
private val RL_WALL = DefaultWorld.walls.first()
private val RL_START = DefaultWorld.start

private val ACTIONS = DefaultWorld.actions
private val ARROWS = listOf("↑", "↓", "←", "→")

private class RlFrame(
    val values: FloatArray,
    val policy: IntArray,
    val agent: Int?,
    val visited: Set<Int>,
    val status: String,
    val showPolicy: Boolean = true,
    // Q-function view: when set, each cell shows its four action values around the edges instead of
    // a single number, so "V(s) = max over these four" is something you can read rather than assert.
    val qTable: Array<FloatArray>? = null,
    // POMDP view: states the agent cannot distinguish from where it stands. Rendered as "?" — the
    // point being that the policy has to give all of them the same action.
    val fog: Set<Int> = emptySet(),
    val world: RlWorld = DefaultWorld,
)

private class RlConfig(
    val intro: String,
    val valueLabel: String,
    val build: () -> List<RlFrame>,
)

private val GoalColor = SimColors.Green
private val PitColor = SimColors.Red
private val WallColor = SimColors.Wall
private val AgentColor = SimColors.Active
private val PositiveValue = SimColors.Blue

// Shims onto DefaultWorld, so every builder written against the original 4×4 keeps reading the way
// it did before the world became a parameter.
private fun isTerminal(s: Int) = DefaultWorld.isTerminal(s)

private fun step(state: Int, action: Int) = DefaultWorld.step(state, action)

private fun rewardFor(state: Int) = DefaultWorld.rewardFor(state)

private fun backup(state: Int, action: Int, values: FloatArray, gamma: Float) =
    DefaultWorld.backup(state, action, values, gamma)

private fun greedyPolicy(values: FloatArray, gamma: Float) = DefaultWorld.greedyPolicy(values, gamma)

// ── MDP: value iteration, one frame per sweep ────────────────────────────────
private fun valueIterationFrames(): List<RlFrame> {
    val gamma = 0.9f
    var values = FloatArray(RL_STATES)
    values[RL_GOAL] = 1f
    values[RL_PIT] = -1f
    val frames = mutableListOf<RlFrame>()

    frames.add(
        RlFrame(
            values.copyOf(), IntArray(RL_STATES) { -1 }, null, emptySet(),
            "The MDP: states, four actions, reward +1 at the goal and −1 at the pit, −0.04 per step. Every value starts at 0 except the terminals.",
            showPolicy = false,
        ),
    )

    repeat(8) { sweep ->
        val next = values.copyOf()
        var delta = 0f
        for (s in 0 until RL_STATES) {
            if (isTerminal(s) || s == RL_WALL) continue
            val best = ACTIONS.maxOf { a -> backup(s, a, values, gamma) }
            delta = maxOf(delta, abs(best - values[s]))
            next[s] = best
        }
        values = next
        frames.add(
            RlFrame(
                values.copyOf(), greedyPolicy(values, gamma), null, emptySet(),
                "Sweep ${sweep + 1}: every state takes the best of (reward + γ · value of where that action lands). Largest change this sweep: ${"%.3f".format(delta)}.",
            ),
        )
    }
    frames.add(
        RlFrame(
            values.copyOf(), greedyPolicy(values, gamma), null, emptySet(),
            "Converged. Value spreads outward from the goal, and the greedy policy — take the action into the highest-valued neighbour — is now optimal everywhere.",
        ),
    )
    return frames
}

// ── Q-learning: real episodes, ε-greedy, one frame per episode ───────────────
private fun qLearningFrames(dynaPlanningSteps: Int = 0): List<RlFrame> {
    val gamma = 0.9f
    val alpha = 0.5f
    val random = Random(7) // fixed seed: the frames must be identical on every open
    val q = Array(RL_STATES) { FloatArray(ACTIONS.size) }
    val model = HashMap<Pair<Int, Int>, Pair<Int, Float>>() // (state, action) -> (next, reward), Dyna-Q only
    val frames = mutableListOf<RlFrame>()

    fun values() = FloatArray(RL_STATES) { s -> if (isTerminal(s)) rewardFor(s) else q[s].max() }
    fun policy() = IntArray(RL_STATES) { s ->
        if (isTerminal(s) || s == RL_WALL) -1
        else if (q[s].all { it == 0f }) -1
        else q[s].indices.maxByOrNull { q[s][it] }!!
    }

    val label = if (dynaPlanningSteps > 0) "Dyna-Q" else "Q-learning"
    frames.add(
        RlFrame(
            values(), policy(), RL_START, emptySet(),
            if (dynaPlanningSteps > 0)
                "Dyna-Q: every real step also updates a learned model of the world, then replays $dynaPlanningSteps imagined transitions from it. Same experience, far more learning per step."
            else
                "Tabular Q-learning knows nothing at the start — no model of the world, no values. It only has the reward it stumbles into.",
            showPolicy = false,
        ),
    )

    val episodes = 40
    repeat(episodes) { episode ->
        var state = RL_START
        val visited = linkedSetOf(state)
        var steps = 0
        // Exploration decays, so early episodes wander and later ones exploit.
        val epsilon = (1f - episode / episodes.toFloat()).coerceAtLeast(0.1f)

        while (!isTerminal(state) && steps < 60) {
            val action = if (random.nextFloat() < epsilon) random.nextInt(ACTIONS.size)
            else q[state].indices.maxByOrNull { q[state][it] }!!
            val next = step(state, ACTIONS[action])
            val reward = rewardFor(next)
            val target = reward + if (isTerminal(next)) 0f else gamma * q[next].max()
            q[state][action] += alpha * (target - q[state][action])

            if (dynaPlanningSteps > 0) {
                model[state to action] = next to reward
                repeat(dynaPlanningSteps) {
                    val (ms, ma) = model.keys.random(random)
                    val (mNext, mReward) = model.getValue(ms to ma)
                    val mTarget = mReward + if (isTerminal(mNext)) 0f else gamma * q[mNext].max()
                    q[ms][ma] += alpha * (mTarget - q[ms][ma])
                }
            }

            state = next
            visited.add(state)
            steps++
        }

        // Only every fourth episode becomes a frame — 40 frames of near-identical tables is noise.
        if (episode % 4 == 3 || episode == 0) {
            frames.add(
                RlFrame(
                    values(), policy(), state, visited,
                    "Episode ${episode + 1}: ${if (state == RL_GOAL) "reached the goal" else if (state == RL_PIT) "fell in the pit" else "ran out of steps"} in $steps steps, ε = ${"%.2f".format(epsilon)}.",
                ),
            )
        }
    }
    frames.add(
        RlFrame(
            values(), policy(), null, emptySet(),
            "$label learned the same policy value iteration computed — but from experience alone, never being told the transition or reward function.",
        ),
    )
    return frames
}

// ── Shared helpers for the Core Concepts topics ──────────────────────────────

private fun deterministic(actionIdx: Int) = FloatArray(ACTIONS.size) { if (it == actionIdx) 1f else 0f }

private val uniformActions = FloatArray(ACTIONS.size) { 1f / ACTIONS.size }

// The naive "head right, then head up" policy. It is a deliberately imperfect choice: from the
// bottom-left start it walks along the bottom row, up the right column, and straight into the pit —
// which is exactly what makes evaluating it instructive.
private fun rightThenUp(state: Int): Int = if (step(state, 1) != state) 3 else 0

// Expected-value backup weighted by the policy's action probabilities (a sum, not a max — that one
// character is the entire difference between policy evaluation and value iteration).
private fun policyEvaluation(
    gamma: Float,
    sweeps: Int,
    actionProbs: (Int) -> FloatArray,
): List<FloatArray> {
    var values = FloatArray(RL_STATES)
    values[RL_GOAL] = 1f
    values[RL_PIT] = -1f
    val snapshots = mutableListOf(values.copyOf())
    repeat(sweeps) {
        val next = values.copyOf()
        for (s in 0 until RL_STATES) {
            if (isTerminal(s) || s == RL_WALL) continue
            val probs = actionProbs(s)
            var v = 0f
            for (a in ACTIONS.indices) {
                if (probs[a] == 0f) continue
                v += probs[a] * backup(s, ACTIONS[a], values, gamma)
            }
            next[s] = v
        }
        values = next
        snapshots.add(values.copyOf())
    }
    return snapshots
}

private fun policyArrows(actionFor: (Int) -> Int): IntArray = IntArray(RL_STATES) { s ->
    if (isTerminal(s) || s == RL_WALL) -1 else actionFor(s)
}

private fun convergedValues(gamma: Float, sweeps: Int = 80): FloatArray {
    var values = FloatArray(RL_STATES)
    values[RL_GOAL] = 1f
    values[RL_PIT] = -1f
    repeat(sweeps) {
        val next = values.copyOf()
        for (s in 0 until RL_STATES) {
            if (isTerminal(s) || s == RL_WALL) continue
            next[s] = ACTIONS.maxOf { a -> backup(s, a, values, gamma) }
        }
        values = next
    }
    return values
}

// One optimal trajectory from the start, as (state, actionIndex) pairs.
private fun optimalRollout(maxSteps: Int = 20): List<Pair<Int, Int>> {
    val gamma = 0.9f
    val values = convergedValues(gamma)
    val policy = greedyPolicy(values, gamma)
    val path = mutableListOf<Pair<Int, Int>>()
    var state = RL_START
    var steps = 0
    while (!isTerminal(state) && steps < maxSteps) {
        val action = policy[state].takeIf { it >= 0 } ?: break
        path.add(state to action)
        state = step(state, ACTIONS[action])
        steps++
    }
    return path
}

private fun cellName(s: Int) = "(${s / RL_COLS},${s % RL_COLS})"

// ── Agent & Environment: narrate one turn of the loop at a time ──────────────
private fun agentEnvironmentFrames(): List<RlFrame> {
    val terminalValues = FloatArray(RL_STATES).also {
        it[RL_GOAL] = 1f
        it[RL_PIT] = -1f
    }
    val noPolicy = IntArray(RL_STATES) { -1 }
    val frames = mutableListOf<RlFrame>()
    val path = optimalRollout()

    frames.add(
        RlFrame(
            terminalValues.copyOf(), noPolicy, RL_START, emptySet(),
            "The split: the agent is the thing choosing a move. The grid, the wall, the pit and the rule that pays +1 at the goal are all environment — the agent cannot change any of them.",
            showPolicy = false,
        ),
    )

    val visited = linkedSetOf(RL_START)
    path.forEachIndexed { i, (state, action) ->
        val next = step(state, ACTIONS[action])
        visited.add(next)
        frames.add(
            RlFrame(
                terminalValues.copyOf(), noPolicy, state, visited.toSet(),
                "Step ${i + 1} — agent side: it observes ${cellName(state)} and sends one action, ${ARROWS[action]}. That is the entire outgoing channel.",
                showPolicy = false,
            ),
        )
        frames.add(
            RlFrame(
                terminalValues.copyOf(), noPolicy, next, visited.toSet(),
                "Step ${i + 1} — environment side: it applies its own dynamics, lands the agent in ${cellName(next)}, and answers with reward ${"%.2f".format(rewardFor(next))}. The agent is never told why.",
                showPolicy = false,
            ),
        )
    }

    frames.add(
        RlFrame(
            terminalValues.copyOf(), noPolicy, null, visited.toSet(),
            "Terminal reached, episode over, environment resets. Everything the agent will ever learn from has to be squeezed out of streams like this one.",
            showPolicy = false,
        ),
    )
    return frames
}

// ── State, Action, Reward: the tuple and the return it accumulates ───────────
private fun stateActionRewardFrames(): List<RlFrame> {
    val gamma = 0.9f
    val terminalValues = FloatArray(RL_STATES).also {
        it[RL_GOAL] = 1f
        it[RL_PIT] = -1f
    }
    val noPolicy = IntArray(RL_STATES) { -1 }
    val frames = mutableListOf<RlFrame>()
    val path = optimalRollout()

    frames.add(
        RlFrame(
            terminalValues.copyOf(), noPolicy, RL_START, emptySet(),
            "Three signals, one tuple: (s, a, r, s′). Reward here is +1 at the goal, −1 at the pit, and −0.04 on every other step — the step cost is what turns \"reach the goal\" into \"reach it soon\".",
            showPolicy = false,
        ),
    )

    val visited = linkedSetOf(RL_START)
    var undiscounted = 0f
    var discounted = 0f
    var weight = 1f // γᵗ, carried forward rather than recomputed
    path.forEachIndexed { t, (state, action) ->
        val next = step(state, ACTIONS[action])
        val reward = rewardFor(next)
        undiscounted += reward
        discounted += weight * reward
        weight *= gamma
        visited.add(next)
        frames.add(
            RlFrame(
                terminalValues.copyOf(), noPolicy, next, visited.toSet(),
                "t=$t: (s=${cellName(state)}, a=${ARROWS[action]}, r=${"%.2f".format(reward)}, s′=${cellName(next)}). " +
                    "Running total ${"%.2f".format(undiscounted)}, discounted return ${"%.3f".format(discounted)}. " +
                    "The reward is indexed t+1 because it arrives with the next state, not with the action.",
                showPolicy = false,
            ),
        )
    }

    frames.add(
        RlFrame(
            terminalValues.copyOf(), noPolicy, null, visited.toSet(),
            "Return G₀ = ${"%.3f".format(discounted)} against a raw sum of ${"%.2f".format(undiscounted)}. This number — not any single reward — is what every algorithm in this section maximizes.",
            showPolicy = false,
        ),
    )
    return frames
}

// ── The Policy: three different π over the same MDP ──────────────────────────
private fun policyFrames(): List<RlFrame> {
    val gamma = 0.9f
    val frames = mutableListOf<RlFrame>()

    val randomValues = policyEvaluation(gamma, 60) { uniformActions }.last()
    frames.add(
        RlFrame(
            randomValues, IntArray(RL_STATES) { -1 }, null, emptySet(),
            "A uniform random policy: π(a|s) = 0.25 for all four actions. It is a valid policy, and these are its true values — mostly negative, because a random walk pays the step cost far longer than it needs to.",
            showPolicy = false,
        ),
    )

    val naiveValues = policyEvaluation(gamma, 60) { s -> deterministic(rightThenUp(s)) }.last()
    frames.add(
        RlFrame(
            naiveValues, policyArrows(::rightThenUp), null, emptySet(),
            "A deterministic policy: \"go right if you can, otherwise up\". Note V(start) = ${"%.2f".format(naiveValues[RL_START])} — it is confidently wrong, marching along the bottom row and up into the pit.",
        ),
    )

    val optimalValues = convergedValues(gamma)
    val optimalPolicy = greedyPolicy(optimalValues, gamma)
    frames.add(
        RlFrame(
            optimalValues, optimalPolicy, null, emptySet(),
            "The optimal policy π*, worth ${"%.2f".format(optimalValues[RL_START])} from the same start. Same MDP, same states, same actions — the only thing that changed is the mapping from one to the other.",
        ),
    )
    frames.add(
        RlFrame(
            optimalValues, optimalPolicy, RL_START, emptySet(),
            "π* here is deterministic, which a finite fully-observable MDP always permits. Take observability away and that guarantee dies: aliased states force a stochastic policy, which is the POMDP topic.",
        ),
    )
    return frames
}

// ── Value function: evaluating a FIXED policy, then improving it ─────────────
private fun valueFunctionFrames(): List<RlFrame> {
    val gamma = 0.9f
    val arrows = policyArrows(::rightThenUp)
    val frames = mutableListOf<RlFrame>()
    val sweeps = policyEvaluation(gamma, 7) { s -> deterministic(rightThenUp(s)) }

    frames.add(
        RlFrame(
            sweeps.first(), arrows, null, emptySet(),
            "Policy evaluation, not planning. The policy is fixed — \"right if you can, else up\" — and the only question is what it is worth. Everything starts at zero except the terminals.",
        ),
    )

    sweeps.drop(1).forEachIndexed { i, values ->
        frames.add(
            RlFrame(
                values, arrows, null, emptySet(),
                "Sweep ${i + 1}: each state takes reward + γ·V of wherever THIS policy sends it. No max anywhere — value flows backwards along the policy's own path, so the pit's −1 spreads down the right column.",
            ),
        )
    }

    val evaluated = sweeps.last()
    frames.add(
        RlFrame(
            evaluated, arrows, null, emptySet(),
            "Converged: this is Vπ for that policy. V(start) = ${"%.2f".format(evaluated[RL_START])}. The superscript matters — these numbers are a property of the policy, not of the grid.",
        ),
    )

    val improved = greedyPolicy(evaluated, gamma)
    frames.add(
        RlFrame(
            evaluated, improved, null, emptySet(),
            "Now act greedily with respect to those same values and the arrows change — the states beside the pit turn away from it. Evaluate the new policy, improve again, repeat: that alternation is policy iteration.",
        ),
    )

    val optimal = convergedValues(gamma)
    frames.add(
        RlFrame(
            optimal, greedyPolicy(optimal, gamma), null, emptySet(),
            "Where the alternation ends: V* = ${"%.2f".format(optimal[RL_START])} at the start, against ${"%.2f".format(evaluated[RL_START])} for the policy we began with.",
        ),
    )
    return frames
}

// ── Q-function: the same learning run, but showing all four action values ────
private fun qFunctionFrames(): List<RlFrame> {
    val gamma = 0.9f
    val alpha = 0.5f
    val random = Random(7)
    val q = Array(RL_STATES) { FloatArray(ACTIONS.size) }
    val frames = mutableListOf<RlFrame>()

    fun snapshot() = Array(RL_STATES) { q[it].copyOf() }
    fun values() = FloatArray(RL_STATES) { s -> if (isTerminal(s)) rewardFor(s) else q[s].max() }
    fun policy() = IntArray(RL_STATES) { s ->
        if (isTerminal(s) || s == RL_WALL) -1
        else if (q[s].all { it == 0f }) -1
        else q[s].indices.maxByOrNull { q[s][it] }!!
    }

    frames.add(
        RlFrame(
            values(), policy(), RL_START, emptySet(),
            "Q is one number per action per state — four per cell, shown around the edges in ↑↓←→ order. All zero to begin with, and no transition model is available anywhere.",
            showPolicy = false, qTable = snapshot(),
        ),
    )

    val episodes = 60
    repeat(episodes) { episode ->
        var state = RL_START
        var steps = 0
        val epsilon = (1f - episode / episodes.toFloat()).coerceAtLeast(0.1f)
        while (!isTerminal(state) && steps < 60) {
            val action = if (random.nextFloat() < epsilon) random.nextInt(ACTIONS.size)
            else q[state].indices.maxByOrNull { q[state][it] }!!
            val next = step(state, ACTIONS[action])
            val reward = rewardFor(next)
            val target = reward + if (isTerminal(next)) 0f else gamma * q[next].max()
            q[state][action] += alpha * (target - q[state][action])
            state = next
            steps++
        }
        if (episode == 0 || episode % 10 == 9) {
            val start = q[RL_START]
            val best = start.indices.maxByOrNull { start[it] }!!
            frames.add(
                RlFrame(
                    values(), policy(), null, emptySet(),
                    "Episode ${episode + 1}: at the start cell the four action values are now " +
                        ARROWS.indices.joinToString(", ") { "${ARROWS[it]} ${"%.2f".format(start[it])}" } +
                        ". The greedy action is ${ARROWS[best]} — read straight off the table, with no lookahead.",
                    qTable = snapshot(),
                ),
            )
        }
    }

    frames.add(
        RlFrame(
            values(), policy(), null, emptySet(),
            "Every cell's shade is max Q(s,a) — that is V(s). The arrow is argmax Q(s,a) — that is the policy. Both fall out of the same table, which is why model-free control is built on Q and not on V.",
            qTable = snapshot(),
        ),
    )
    return frames
}

// ── Discount factor: the same MDP solved at four horizons ────────────────────
private fun discountFactorFrames(): List<RlFrame> {
    val frames = mutableListOf<RlFrame>()
    val notes = listOf(
        0f to "γ = 0: a pure bandit. Value is just the immediate reward, so only the two cells touching a terminal have any opinion at all, and the agent is blind everywhere else.",
        0.5f to "γ = 0.5, horizon ≈ 2 steps. Value now leaks one or two cells out from the goal, and the far corner is still effectively unreachable information.",
        0.9f to "γ = 0.9, horizon ≈ 10 steps. The grid's longest path is about 6 moves, so this is the first γ where the start cell can actually see the goal.",
        0.99f to "γ = 0.99, horizon ≈ 100 steps. Values are nearly undiscounted and the whole grid is bright — but in a real problem this is also where bootstrap error compounds and training slows down.",
    )
    notes.forEach { (gamma, note) ->
        val values = convergedValues(gamma)
        frames.add(
            RlFrame(
                values, greedyPolicy(values, gamma), null, emptySet(),
                "$note  V(start) = ${"%.3f".format(values[RL_START])}.",
            ),
        )
    }
    frames.add(
        RlFrame(
            convergedValues(0.9f), greedyPolicy(convergedValues(0.9f), 0.9f), RL_START, emptySet(),
            "One number, two jobs: γ < 1 keeps the infinite sum finite and makes the Bellman operator a contraction, and 1/(1−γ) sets how many steps ahead the agent can reason. Pick it from the task's horizon, not by maximizing it.",
        ),
    )
    return frames
}

// ── POMDP: the same grid, seen through a wall sensor ─────────────────────────

// The agent's entire sensor: a bump counter. It feels how many of its four moves are blocked by a
// wall or an edge, and nothing else — not which ones, and nothing about position. Deliberately
// coarser than reporting the blocked directions: that finer sensor happens to identify almost every
// cell of this grid on the first reading, which would make the aliasing story a lie.
private fun observationOf(s: Int): Int =
    ACTIONS.count { step(s, it) == s }

private fun observationLabel(obs: Int): String = when (obs) {
    0 -> "no sides blocked"
    1 -> "1 side blocked"
    else -> "$obs sides blocked"
}

private fun candidateStates(obs: Int): Set<Int> =
    (0 until RL_STATES).filter { it != RL_WALL && !isTerminal(it) && observationOf(it) == obs }.toSet()

// Exact Bayes filter: push the belief through the (deterministic) dynamics, keep only the states
// consistent with the new reading, renormalize.
private fun beliefUpdate(belief: FloatArray, actionIdx: Int, obs: Int): FloatArray {
    val predicted = FloatArray(RL_STATES)
    for (s in 0 until RL_STATES) {
        if (belief[s] <= 0f) continue
        predicted[step(s, ACTIONS[actionIdx])] += belief[s]
    }
    val posterior = FloatArray(RL_STATES)
    var total = 0f
    for (s in 0 until RL_STATES) {
        if (predicted[s] > 0f && s != RL_WALL && !isTerminal(s) && observationOf(s) == obs) {
            posterior[s] = predicted[s]
            total += predicted[s]
        }
    }
    if (total <= 0f) return predicted
    for (s in 0 until RL_STATES) posterior[s] /= total
    return posterior
}

private fun pomdpFrames(): List<RlFrame> {
    val noPolicy = IntArray(RL_STATES) { -1 }
    val frames = mutableListOf<RlFrame>()
    val terminalValues = FloatArray(RL_STATES).also {
        it[RL_GOAL] = 1f
        it[RL_PIT] = -1f
    }

    // Bottom-middle: its reading is the grid's most ambiguous, so the filter has real work to do.
    var trueState = 13
    frames.add(
        RlFrame(
            terminalValues.copyOf(), noPolicy, trueState, emptySet(),
            "As an MDP this is easy: the agent is told it is in ${cellName(trueState)} and every algorithm in this section applies.",
            showPolicy = false,
        ),
    )

    val firstObs = observationOf(trueState)
    val aliased = candidateStates(firstObs)
    frames.add(
        RlFrame(
            terminalValues.copyOf(), noPolicy, null, emptySet(),
            "Now the agent has only a bump sensor: it feels how many sides are blocked, not which. Its reading is \"${observationLabel(firstObs)}\" — and ${aliased.size} different cells produce exactly that reading. The \"?\" cells are indistinguishable to it.",
            showPolicy = false, fog = aliased,
        ),
    )

    var belief = FloatArray(RL_STATES)
    aliased.forEach { belief[it] = 1f / aliased.size }
    frames.add(
        RlFrame(
            belief.copyOf(), noPolicy, null, emptySet(),
            "The fix is to stop tracking a state and start tracking a belief: b(s), a posterior over where it might be. Uniform over ${aliased.size} candidates, ${"%.2f".format(1f / aliased.size)} each.",
            showPolicy = false,
        ),
    )

    // Two steps of evidence. Each frame reports the real support size, so the narration cannot drift
    // from what the filter actually computed.
    listOf(2, 0).forEachIndexed { i, actionIdx ->
        trueState = step(trueState, ACTIONS[actionIdx])
        val obs = observationOf(trueState)
        belief = beliefUpdate(belief, actionIdx, obs)
        val support = (0 until RL_STATES).count { belief[it] > 0.001f }
        frames.add(
            RlFrame(
                belief.copyOf(), noPolicy, null, emptySet(),
                "Move ${ARROWS[actionIdx]}, then read the sensor: \"${observationLabel(obs)}\". Bayes rule pushes the belief through the dynamics and drops everything inconsistent — ${aliasedCountLabel(i, support)}",
                showPolicy = false,
            ),
        )
    }

    frames.add(
        RlFrame(
            belief.copyOf(), noPolicy, null, emptySet(),
            "Nothing in the last observation identified the state — the history did. That is the whole lesson: a policy over observations cannot solve this, and a policy over beliefs can, which is why partially observed agents carry an RNN or a frame stack.",
            showPolicy = false,
        ),
    )
    frames.add(
        RlFrame(
            terminalValues.copyOf(), noPolicy, null, emptySet(),
            "And while the states stay aliased, a deterministic reactive policy is trapped: it must give every \"?\" cell the same action, so if that action is wrong in one of them it loops forever. A stochastic policy escapes by coin flip.",
            showPolicy = false, fog = aliased,
        ),
    )
    return frames
}

private fun aliasedCountLabel(stepIndex: Int, support: Int): String = when {
    support <= 1 -> "the belief has collapsed to a single state. The agent now knows where it is, from memory rather than from what it can see."
    stepIndex == 0 -> "$support candidates remain."
    else -> "down to $support. Each observation is weak on its own; accumulated, they localize."
}

// ── Tabular methods ──────────────────────────────────────────────────────────

// ── Bellman equation: one state's backup, term by term ──────────────────────
private fun bellmanEquationFrames(): List<RlFrame> {
    val gamma = 0.9f
    val values = convergedValues(gamma)
    val policy = greedyPolicy(values, gamma)
    val focus = 2 // (0,2) — one step from the goal, so the arithmetic is small enough to read
    val frames = mutableListOf<RlFrame>()

    frames.add(
        RlFrame(
            values, policy, focus, emptySet(),
            "The Bellman equation is a consistency condition, not an algorithm: a state's value must equal the reward for leaving it plus the discounted value of where it lands. Take the highlighted cell ${cellName(focus)} and check all four actions.",
        ),
    )

    ACTIONS.indices.forEach { a ->
        val next = step(focus, ACTIONS[a])
        val reward = rewardFor(next)
        val bootstrap = if (isTerminal(next)) 0f else gamma * values[next]
        val total = reward + bootstrap
        val detail = if (isTerminal(next)) {
            "lands on a terminal, so there is no future to discount"
        } else {
            "γ·V(${cellName(next)}) = 0.9 × ${"%.2f".format(values[next])} = ${"%.2f".format(bootstrap)}"
        }
        frames.add(
            RlFrame(
                values, policy, next, emptySet(),
                "Action ${ARROWS[a]} → ${cellName(next)}: r = ${"%.2f".format(reward)}, $detail. Backup value = ${"%.2f".format(total)}.",
            ),
        )
    }

    val best = ACTIONS.indices.maxByOrNull { backup(focus, ACTIONS[it], values, gamma) }!!
    frames.add(
        RlFrame(
            values, policy, focus, emptySet(),
            "V*(${cellName(focus)}) = max over those four = ${"%.2f".format(values[focus])}, achieved by ${ARROWS[best]}. That is the Bellman optimality equation. Swap the max for an average weighted by π and you get the Bellman expectation equation instead.",
        ),
    )
    frames.add(
        RlFrame(
            values, policy, null, emptySet(),
            "Every cell satisfies the same condition simultaneously — |S| equations in |S| unknowns. Value iteration, policy iteration, TD and Q-learning are all just different ways of solving this system.",
        ),
    )
    return frames
}

// ── Dynamic programming: sweeps over a KNOWN model ──────────────────────────
private fun dynamicProgrammingFrames(): List<RlFrame> {
    val gamma = 0.9f
    var values = FloatArray(RL_STATES)
    values[RL_GOAL] = 1f
    values[RL_PIT] = -1f
    val frames = mutableListOf<RlFrame>()
    var backups = 0
    val planningStates = (0 until RL_STATES).count { !isTerminal(it) && it != RL_WALL }

    frames.add(
        RlFrame(
            values.copyOf(), IntArray(RL_STATES) { -1 }, null, emptySet(),
            "Dynamic programming solves an MDP the way it solves any DP problem: overlapping subproblems (a state's value reuses its neighbours') and optimal substructure (the optimal path's tail is itself optimal). The catch is the precondition — P and R must be known.",
            showPolicy = false,
        ),
    )

    repeat(6) { sweep ->
        val next = values.copyOf()
        for (s in 0 until RL_STATES) {
            if (isTerminal(s) || s == RL_WALL) continue
            next[s] = ACTIONS.maxOf { a -> backup(s, a, values, gamma) }
            backups += ACTIONS.size
        }
        values = next
        frames.add(
            RlFrame(
                values.copyOf(), greedyPolicy(values, gamma), null, emptySet(),
                "Sweep ${sweep + 1}: all $planningStates non-terminal states updated in one pass, $backups backups so far. Nothing was sampled and no episode was run — the model was queried directly, which is the difference between planning and learning.",
            ),
        )
    }

    frames.add(
        RlFrame(
            values.copyOf(), greedyPolicy(values, gamma), null, emptySet(),
            "Solved in $backups backups, exactly and without touching the environment once. That is DP's bargain: it is the fastest method here, and it is unusable the moment you do not have the model — which is almost always.",
        ),
    )
    return frames
}

// ── Policy iteration: evaluate to convergence, improve, repeat ───────────────
private fun policyIterationFrames(): List<RlFrame> {
    val gamma = 0.9f
    val frames = mutableListOf<RlFrame>()
    var actions = IntArray(RL_STATES) { s -> if (isTerminal(s) || s == RL_WALL) -1 else rightThenUp(s) }

    frames.add(
        RlFrame(
            FloatArray(RL_STATES).also { it[RL_GOAL] = 1f; it[RL_PIT] = -1f },
            actions, null, emptySet(),
            "Start from any policy at all — here the naive \"right if you can, else up\", which marches into the pit. Policy iteration will fix it in a handful of rounds.",
        ),
    )

    var round = 0
    while (round < 6) {
        round++
        val fixed = actions.copyOf()
        val evaluated = policyEvaluation(gamma, 60) { s ->
            val a = fixed[s]
            if (a < 0) FloatArray(ACTIONS.size) else deterministic(a)
        }.last()
        frames.add(
            RlFrame(
                evaluated, fixed, null, emptySet(),
                "Round $round — evaluate: run policy evaluation to convergence for this exact policy. V(start) = ${"%.2f".format(evaluated[RL_START])}.",
            ),
        )

        val improved = greedyPolicy(evaluated, gamma)
        val changed = (0 until RL_STATES).count { !isTerminal(it) && it != RL_WALL && improved[it] != fixed[it] }
        frames.add(
            RlFrame(
                evaluated, improved, null, emptySet(),
                if (changed == 0) {
                    "Round $round — improve: acting greedily on those values changes nothing. The policy is stable, which for a finite MDP means it is optimal. Done in $round round${if (round == 1) "" else "s"}."
                } else {
                    "Round $round — improve: acting greedily on those values changes the action in $changed state${if (changed == 1) "" else "s"}. Any change means the new policy is strictly better, so go evaluate it."
                },
            ),
        )
        if (changed == 0) break
        actions = improved
    }

    val optimal = convergedValues(gamma)
    frames.add(
        RlFrame(
            optimal, greedyPolicy(optimal, gamma), RL_START, emptySet(),
            "Policy iteration terminates in finitely many rounds because there are finitely many policies and every round is a strict improvement. Each round is expensive — a full evaluation — but there are very few of them.",
        ),
    )
    return frames
}

// ── Value iteration: the max fused into the sweep ───────────────────────────
private fun valueIterationDetailFrames(): List<RlFrame> {
    val gamma = 0.9f
    var values = FloatArray(RL_STATES)
    values[RL_GOAL] = 1f
    values[RL_PIT] = -1f
    val optimalPolicy = greedyPolicy(convergedValues(gamma), gamma)
    val frames = mutableListOf<RlFrame>()
    var policyOptimalAt = -1
    var sweep = 0

    frames.add(
        RlFrame(
            values.copyOf(), IntArray(RL_STATES) { -1 }, null, emptySet(),
            "Value iteration is policy iteration with the evaluation cut short: instead of running evaluation to convergence, take exactly one backup and fold the improvement in as a max. No policy is ever stored.",
            showPolicy = false,
        ),
    )

    while (sweep < 10) {
        sweep++
        val next = values.copyOf()
        var delta = 0f
        for (s in 0 until RL_STATES) {
            if (isTerminal(s) || s == RL_WALL) continue
            val best = ACTIONS.maxOf { a -> backup(s, a, values, gamma) }
            delta = maxOf(delta, abs(best - values[s]))
            next[s] = best
        }
        values = next
        val policy = greedyPolicy(values, gamma)
        val matches = (0 until RL_STATES).all { isTerminal(it) || it == RL_WALL || policy[it] == optimalPolicy[it] }
        if (matches && policyOptimalAt < 0) policyOptimalAt = sweep
        frames.add(
            RlFrame(
                values.copyOf(), policy, null, emptySet(),
                "Sweep $sweep: largest value change ${"%.4f".format(delta)}. The greedy policy implied by these values is ${if (matches) "already optimal" else "not optimal yet"}.",
            ),
        )
    }

    frames.add(
        RlFrame(
            values.copyOf(), greedyPolicy(values, gamma), RL_START, emptySet(),
            "Worth noticing: the policy became optimal at sweep $policyOptimalAt, but the values kept moving for several sweeps after that. If you only want to act well you can stop early — the numbers converge long after the decisions do.",
        ),
    )
    return frames
}

// ── Monte Carlo and TD prediction, measured against the exact answer ─────────

private const val PREDICTION_GAMMA = 0.9f

private fun exactPolicyValues(): FloatArray =
    policyEvaluation(PREDICTION_GAMMA, 200) { s -> deterministic(rightThenUp(s)) }.last()

private fun maxError(estimate: FloatArray, exact: FloatArray): Float =
    (0 until RL_STATES)
        .filter { !isTerminal(it) && it != RL_WALL }
        .maxOf { abs(estimate[it] - exact[it]) }

// Exploring starts: begin episodes anywhere, or only the handful of states on the policy's own path
// are ever visited and most of the table stays at its initial value forever.
private fun randomStart(random: Random): Int {
    while (true) {
        val s = random.nextInt(RL_STATES)
        if (!isTerminal(s) && s != RL_WALL) return s
    }
}

private fun predictionFrames(useTd: Boolean): List<RlFrame> {
    val exact = exactPolicyValues()
    val arrows = policyArrows(::rightThenUp)
    val random = Random(5)
    val values = FloatArray(RL_STATES)
    values[RL_GOAL] = 1f
    values[RL_PIT] = -1f
    val returnsSum = FloatArray(RL_STATES)
    val returnsCount = IntArray(RL_STATES)
    val alpha = 0.1f
    val frames = mutableListOf<RlFrame>()

    frames.add(
        RlFrame(
            values.copyOf(), arrows, null, emptySet(),
            if (useTd) {
                "TD(0) prediction of the same fixed policy. Every single step produces an update: V(s) moves toward r + γV(s′), an estimate built on another estimate — that is bootstrapping."
            } else {
                "Monte Carlo prediction of a fixed policy. No model, no bootstrapping: play a whole episode, compute the actual return from each state, and average. The exact answer is known here, so the error can be measured rather than eyeballed."
            },
        ),
    )

    val checkpoints = setOf(1, 5, 20, 60, 200, 600, 2000)
    var updates = 0
    repeat(2000) { episode ->
        var state = randomStart(random)
        val trajectory = mutableListOf<Pair<Int, Float>>()
        var steps = 0
        while (!isTerminal(state) && steps < 100) {
            val action = rightThenUp(state)
            val next = step(state, ACTIONS[action])
            val reward = rewardFor(next)
            if (useTd) {
                // Updated immediately, mid-episode — no need to wait for a terminal state.
                val target = reward + if (isTerminal(next)) 0f else PREDICTION_GAMMA * values[next]
                values[state] += alpha * (target - values[state])
                updates++
            } else {
                trajectory.add(state to reward)
            }
            state = next
            steps++
        }

        if (!useTd) {
            // Every-visit returns, computed backwards once the episode has actually ended.
            var g = 0f
            val seen = mutableSetOf<Int>()
            for (t in trajectory.indices.reversed()) {
                val (s, r) = trajectory[t]
                g = r + PREDICTION_GAMMA * g
                if (seen.add(s)) {
                    returnsSum[s] += g
                    returnsCount[s]++
                    values[s] = returnsSum[s] / returnsCount[s]
                    updates++
                }
            }
        }

        val n = episode + 1
        if (n in checkpoints) {
            frames.add(
                RlFrame(
                    values.copyOf(), arrows, null, emptySet(),
                    "After $n episode${if (n == 1) "" else "s"} ($updates updates): largest error against the exact Vπ is ${"%.3f".format(maxError(values, exact))}." +
                        if (useTd) " TD has been updating since its very first step." else " Every one of those updates had to wait for an episode to finish.",
                ),
            )
        }
    }

    frames.add(
        RlFrame(
            exact, arrows, null, emptySet(),
            if (useTd) {
                "For reference, the exact Vπ computed by dynamic programming. TD reached it without ever seeing P or R — and unlike Monte Carlo, it never had to wait for an episode to end, which is why it works on continuing tasks that have no end."
            } else {
                "For reference, the exact Vπ computed by dynamic programming from the model. Monte Carlo matched it from sampled returns alone — but it is unbiased at the cost of high variance, and it cannot start learning until an episode terminates."
            },
        ),
    )
    return frames
}

// ── SARSA: on-policy control, and the cliff that reveals it ─────────────────

private class CliffRun(val q: Array<FloatArray>, val falls: Int, val path: List<Int>)

private fun runCliff(onPolicy: Boolean, episodes: Int = 4000, seed: Int = 1): CliffRun {
    val world = CliffWorld
    val random = Random(seed)
    val alpha = 0.5f
    val gamma = 1f
    val epsilon = 0.1f
    val q = Array(world.states) { FloatArray(world.actions.size) }

    fun pick(s: Int) = if (random.nextFloat() < epsilon) random.nextInt(world.actions.size)
    else q[s].indices.maxByOrNull { q[s][it] }!!

    repeat(episodes) {
        var state = world.start
        var action = pick(state)
        var steps = 0
        while (!world.isTerminal(state) && steps < 200) {
            val next = world.step(state, world.actions[action])
            val reward = world.rewardFor(next)
            val nextAction = pick(next)
            // The one-line difference: SARSA bootstraps off the action it will actually take,
            // including the exploratory ones. Q-learning bootstraps off the best action, which it
            // may never take.
            val bootstrap = when {
                world.isTerminal(next) -> 0f
                onPolicy -> q[next][nextAction]
                else -> q[next].max()
            }
            q[state][action] += alpha * (reward + gamma * bootstrap - q[state][action])
            state = next
            action = nextAction
            steps++
        }
    }

    // Score the learned table the way it would actually be used: still exploring.
    val scorer = Random(seed + 100)
    var falls = 0
    repeat(500) {
        var s = world.start
        var steps = 0
        while (steps < 200) {
            val a = if (scorer.nextFloat() < epsilon) scorer.nextInt(world.actions.size)
            else q[s].indices.maxByOrNull { q[s][it] }!!
            s = world.step(s, world.actions[a])
            if (s in world.pits) { falls++; break }
            if (s == world.goal) break
            steps++
        }
    }

    val path = mutableListOf(world.start)
    var s = world.start
    repeat(24) {
        if (world.isTerminal(s)) return@repeat
        s = world.step(s, world.actions[q[s].indices.maxByOrNull { q[s][it] }!!])
        path.add(s)
    }
    return CliffRun(q, falls, path)
}

private fun cliffValues(q: Array<FloatArray>): FloatArray = FloatArray(CliffWorld.states) { s ->
    if (CliffWorld.isTerminal(s)) CliffWorld.rewardFor(s) else q[s].max()
}

private fun cliffPolicy(q: Array<FloatArray>): IntArray = IntArray(CliffWorld.states) { s ->
    if (CliffWorld.isTerminal(s)) -1
    else if (q[s].all { it == 0f }) -1
    else q[s].indices.maxByOrNull { q[s][it] }!!
}

private fun sarsaFrames(): List<RlFrame> {
    val world = CliffWorld
    val frames = mutableListOf<RlFrame>()
    val blank = FloatArray(world.states)

    frames.add(
        RlFrame(
            blank, IntArray(world.states) { -1 }, world.start, emptySet(),
            "A different grid, because SARSA's behaviour is invisible without one. Start bottom-left, goal bottom-right, and a cliff in between: −100 to fall in, −1 per step. The short route runs right along the cliff edge.",
            showPolicy = false, world = world,
        ),
    )

    val sarsa = runCliff(onPolicy = true)
    val qlearn = runCliff(onPolicy = false)

    frames.add(
        RlFrame(
            cliffValues(qlearn.q), cliffPolicy(qlearn.q), null, emptySet(),
            "Q-learning after 4,000 episodes. Its target uses maxₐ′ Q(s′,a′) — the value of the best action, whether or not it takes it — so it learns the values of a perfectly greedy agent and walks the ${qlearn.path.size - 1}-step cliff edge.",
            world = world,
        ),
    )

    frames.add(
        RlFrame(
            cliffValues(sarsa.q), cliffPolicy(sarsa.q), null, emptySet(),
            "SARSA on the identical problem. Its target uses Q(s′,a′) for the action it will actually take — including the 10% that are random — so the cliff's −100 leaks back into the cells beside it. It takes the longer ${sarsa.path.size - 1}-step detour.",
            world = world,
        ),
    )

    frames.add(
        RlFrame(
            cliffValues(sarsa.q), cliffPolicy(sarsa.q), null, emptySet(),
            "Scored over 500 episodes with exploration still on: SARSA fell in ${sarsa.falls} times, Q-learning ${qlearn.falls}. Q-learning's policy is optimal for an agent that never slips; SARSA's is better for the agent that actually exists.",
            world = world,
        ),
    )

    frames.add(
        RlFrame(
            cliffValues(qlearn.q), cliffPolicy(qlearn.q), null, emptySet(),
            "Neither is wrong — they answer different questions. Off-policy learns about the greedy policy from any data; on-policy learns about the policy generating the data, exploration included. Drive ε to zero and the two answers converge.",
            world = world,
        ),
    )
    return frames
}

private val rlConfigs = mapOf(
    "bellman_equation" to RlConfig(
        intro = "One cell, four actions, and the arithmetic behind each — the equation checked term by term rather than stated.",
        valueLabel = "V*(s), already converged",
        build = ::bellmanEquationFrames,
    ),
    "dynamic_programming" to RlConfig(
        intro = "Planning with the model in hand: full sweeps over every state, with the backup count as the running cost.",
        valueLabel = "V(s) after each sweep",
        build = ::dynamicProgrammingFrames,
    ),
    "policy_iteration" to RlConfig(
        intro = "Evaluate a policy to convergence, act greedily on the result, repeat. Starting from a policy that walks into the pit, so there is something to fix.",
        valueLabel = "Vπ(s) for the current policy",
        build = ::policyIterationFrames,
    ),
    "value_iteration" to RlConfig(
        intro = "One backup per state per sweep, with the max folded in. Watch for the sweep where the policy stops changing — it arrives well before the values settle.",
        valueLabel = "V(s), converging to V*",
        build = ::valueIterationDetailFrames,
    ),
    "monte_carlo_rl" to RlConfig(
        intro = "Monte Carlo prediction from complete episodes, with the error measured against the exact Vπ that dynamic programming computes from the model.",
        valueLabel = "V̂π(s) from averaged returns",
        build = { predictionFrames(useTd = false) },
    ),
    "td_learning" to RlConfig(
        intro = "TD(0) on the same policy and the same error metric as the Monte Carlo lab, so the two are directly comparable.",
        valueLabel = "V̂π(s) from bootstrapped updates",
        build = { predictionFrames(useTd = true) },
    ),
    "sarsa" to RlConfig(
        intro = "SARSA and Q-learning trained on the same cliff, from the same seed, differing in one term of the update — and ending up with visibly different routes.",
        valueLabel = "max Q(s,a)",
        build = ::sarsaFrames,
    ),
    "agent_environment" to RlConfig(
        intro = "One episode, split into the two halves of the loop: what the agent sends, and what the environment sends back. Nothing else crosses the boundary.",
        valueLabel = "Terminal rewards only — no values learned yet",
        build = ::agentEnvironmentFrames,
    ),
    "state_action_reward" to RlConfig(
        intro = "The same episode, read as a stream of (s, a, r, s′) tuples, with the discounted return accumulating underneath.",
        valueLabel = "Reward at the terminals",
        build = ::stateActionRewardFrames,
    ),
    "policy" to RlConfig(
        intro = "Three policies over one MDP — random, a plausible-looking hand-written rule, and the optimal one — each shown with the values it actually achieves.",
        valueLabel = "Vπ(s) for the policy shown",
        build = ::policyFrames,
    ),
    "value_function" to RlConfig(
        intro = "Policy evaluation: a fixed policy, swept until its values converge. The sum over π where value iteration takes a max is the only difference, and it changes the answer.",
        valueLabel = "Vπ(s)",
        build = ::valueFunctionFrames,
    ),
    "q_function" to RlConfig(
        intro = "Tabular Q-learning with all four action values visible per cell, in ↑↓←→ order. Watch V(s) = max of the four, and the policy = argmax of the four.",
        valueLabel = "Q(s,a) per action; shading is max Q",
        build = ::qFunctionFrames,
    ),
    "discount_factor" to RlConfig(
        intro = "The identical MDP solved four times, changing only γ. The horizon 1/(1−γ) is visible as how far value spreads from the goal.",
        valueLabel = "V*(s) at each γ",
        build = ::discountFactorFrames,
    ),
    "pomdp" to RlConfig(
        intro = "The same grid behind a wall sensor: the agent learns only which moves are blocked. Watch a real Bayes filter turn a set of indistinguishable cells into a single located one.",
        valueLabel = "Belief b(s)",
        build = ::pomdpFrames,
    ),
    "mdp" to RlConfig(
        intro = "A 4×4 grid MDP: goal +1, pit −1, one wall, −0.04 per step, γ = 0.9. Value iteration sweeps the whole state space until the numbers stop moving.",
        valueLabel = "State value V(s)",
        build = ::valueIterationFrames,
    ),
    "grid_world" to RlConfig(
        intro = "The canonical RL testbed. Every RL algorithm here is judged on the same question: from the bottom-left corner, can it learn to reach +1 while avoiding −1 next to it?",
        valueLabel = "State value V(s)",
        build = ::valueIterationFrames,
    ),
    "q_learning" to RlConfig(
        intro = "Tabular Q-learning over 40 ε-greedy episodes. Nothing about the environment is given — the values below are built purely from reward the agent stumbled into.",
        valueLabel = "max Q(s, a)",
        build = { qLearningFrames() },
    ),
    "dyna_q" to RlConfig(
        intro = "Dyna-Q: the same 40 episodes as plain Q-learning, but each real step also feeds a learned model that is then replayed 5 times. Compare how much earlier the arrows settle.",
        valueLabel = "max Q(s, a)",
        build = { qLearningFrames(dynaPlanningSteps = 5) },
    ),
)

private fun rlConfigFor(topicId: String): RlConfig =
    rlConfigs[topicId] ?: rlConfigs.getValue("grid_world")

@Composable
fun RlGridWorldSection(topicId: String) {
    val config = remember(topicId) { rlConfigFor(topicId) }
    val frames = remember(config) { config.build() }
    val playback = rememberPlaybackState(key = config, stepCount = frames.size, initialSpeedMs = 600f)
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

            Text(
                config.valueLabel,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 12.dp),
            )

            RlGrid(frame = frame, modifier = Modifier.padding(top = 8.dp))

            Text(
                frame.status,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 12.dp),
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Labelled from the frame's own world — the cliff world's numbers are not the
                // default world's, and a legend that lied about them would undercut the whole point.
                RlLegend(GoalColor, "Goal ${signed(frame.world.goalReward)}")
                RlLegend(PitColor, "${if (frame.world.pits.size > 1) "Cliff" else "Pit"} ${signed(frame.world.pitReward)}")
                RlLegend(AgentColor, "Agent")
                RlLegend(PositiveValue, "Value")
            }

            PlaybackTransport(playback)
        }
    }
}

private fun signed(v: Float): String {
    val trimmed = if (v == v.toInt().toFloat()) v.toInt().toString() else "%.2f".format(v)
    return if (v > 0f) "+$trimmed" else trimmed
}

@Composable
private fun RlLegend(color: Color, label: String) {
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

// The four action values placed where their actions point — up on top, right on the right, and so
// on. The largest is highlighted, because "the policy is the biggest of these four" is the single
// thing this view exists to make visible.
@Composable
private fun RlQuad(q: FloatArray) {
    val best = q.indices.maxByOrNull { q[it] }?.takeIf { q.any { v -> v != 0f } } ?: -1

    @Composable
    fun value(index: Int) {
        Text(
            "%.2f".format(q[index]),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (index == best) FontWeight.Bold else FontWeight.Normal,
            color = if (index == best) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        value(0)
        Row(verticalAlignment = Alignment.CenterVertically) {
            value(2)
            Text(
                if (best >= 0) ARROWS[best] else "·",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 3.dp),
            )
            value(3)
        }
        value(1)
    }
}

@Composable
private fun RlGrid(frame: RlFrame, modifier: Modifier = Modifier) {
    // Shade by |value| relative to the largest magnitude in this frame, so early all-zero tables
    // don't render as a uniform block of colour.
    val peak = frame.values.maxOfOrNull { abs(it) }?.takeIf { it > 0.001f } ?: 1f
    val world = frame.world

    Column(
        modifier = modifier.fillMaxWidth().aspectRatio(1f),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        for (r in 0 until world.rows) {
            Row(
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                for (c in 0 until world.cols) {
                    val s = r * world.cols + c
                    val value = frame.values[s]
                    val color = when {
                        s in world.walls -> WallColor
                        s == world.goal -> GoalColor
                        s in world.pits -> PitColor
                        s == frame.agent -> AgentColor
                        value < 0f -> PitColor.copy(alpha = (abs(value) / peak).coerceIn(0.08f, 0.55f))
                        else -> PositiveValue.copy(alpha = (value / peak).coerceIn(0.08f, 0.75f))
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(color, RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (s !in world.walls) {
                            when {
                                // Indistinguishable to the agent: showing a number here would be a
                                // lie about what it can perceive.
                                s in frame.fog -> Text(
                                    "?",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )

                                frame.qTable != null && !world.isTerminal(s) -> RlQuad(frame.qTable[s])

                                else -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        "%.2f".format(value),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    val arrow = frame.policy.getOrElse(s) { -1 }
                                    if (frame.showPolicy && arrow >= 0) {
                                        Text(
                                            ARROWS[arrow],
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                        )
                                    }
                                    if (s == world.start && frame.agent == null) {
                                        Text(
                                            "start",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
