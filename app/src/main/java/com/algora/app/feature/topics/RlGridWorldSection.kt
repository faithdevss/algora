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

private const val RL_ROWS = 4
private const val RL_COLS = 4
private const val RL_STATES = RL_ROWS * RL_COLS

private val RL_GOAL = 3            // (0,3) top-right, reward +1
private val RL_PIT = 7             // (1,3) directly below it, reward −1
private val RL_WALL = 5            // (1,1) impassable
private val RL_START = 12          // (3,0) bottom-left

// up, down, left, right — index order is also the arrow-glyph order.
private val ACTIONS = listOf(-RL_COLS, RL_COLS, -1, 1)
private val ARROWS = listOf("↑", "↓", "←", "→")

private class RlFrame(
    val values: FloatArray,
    val policy: IntArray,
    val agent: Int?,
    val visited: Set<Int>,
    val status: String,
    val showPolicy: Boolean = true,
)

private class RlConfig(
    val intro: String,
    val valueLabel: String,
    val build: () -> List<RlFrame>,
)

private val GoalColor = SimColors.Green
private val PitColor = Color(0xFFEF4444)
private val WallColor = Color(0xFF39414F)
private val AgentColor = Color(0xFFFACC15)
private val PositiveValue = Color(0xFF3B82F6)

private fun isTerminal(s: Int) = s == RL_GOAL || s == RL_PIT

private fun step(state: Int, action: Int): Int {
    val row = state / RL_COLS
    val col = state % RL_COLS
    val next = state + action
    // Reject moves that leave the grid, wrap a row edge, or walk into the wall.
    if (next < 0 || next >= RL_STATES) return state
    if (abs(action) == 1 && next / RL_COLS != row) return state
    if (abs(action) == 1 && abs((next % RL_COLS) - col) != 1) return state
    if (next == RL_WALL) return state
    return next
}

private fun rewardFor(state: Int) = when (state) {
    RL_GOAL -> 1f
    RL_PIT -> -1f
    else -> -0.04f // small step cost, so dawdling is punished
}

// Terminals are absorbing: the reward is collected on the transition into them and nothing flows
// back out, otherwise the goal acts as a perpetual reward source and values inflate past +1.
private fun backup(state: Int, action: Int, values: FloatArray, gamma: Float): Float {
    val next = step(state, action)
    return rewardFor(next) + if (isTerminal(next)) 0f else gamma * values[next]
}

private fun greedyPolicy(values: FloatArray, gamma: Float): IntArray = IntArray(RL_STATES) { s ->
    if (isTerminal(s) || s == RL_WALL) -1
    else ACTIONS.indices.maxByOrNull { a -> backup(s, ACTIONS[a], values, gamma) } ?: 0
}

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

private val rlConfigs = mapOf(
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
                RlLegend(GoalColor, "Goal +1")
                RlLegend(PitColor, "Pit −1")
                RlLegend(AgentColor, "Agent")
                RlLegend(PositiveValue, "Value")
            }

            PlaybackTransport(playback)
        }
    }
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

@Composable
private fun RlGrid(frame: RlFrame, modifier: Modifier = Modifier) {
    // Shade by |value| relative to the largest magnitude in this frame, so early all-zero tables
    // don't render as a uniform block of colour.
    val peak = frame.values.maxOfOrNull { abs(it) }?.takeIf { it > 0.001f } ?: 1f

    Column(
        modifier = modifier.fillMaxWidth().aspectRatio(1f),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        for (r in 0 until RL_ROWS) {
            Row(
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                for (c in 0 until RL_COLS) {
                    val s = r * RL_COLS + c
                    val value = frame.values[s]
                    val color = when {
                        s == RL_WALL -> WallColor
                        s == RL_GOAL -> GoalColor
                        s == RL_PIT -> PitColor
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
                        if (s != RL_WALL) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
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
                                if (s == RL_START && frame.agent == null) {
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
