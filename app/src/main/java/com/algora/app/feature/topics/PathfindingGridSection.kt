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

// ── Pathfinding grid ─────────────────────────────────────────────────────────
// Shortest-path search over a small walled grid, one frame per expansion. Same precomputed-snapshot
// model as the other players; what differs per algorithm is only the priority used to pop the next
// cell, so Dijkstra / A* / D* share one search loop and one renderer.
//
// The point the frames are built to show: Dijkstra expands blindly in all directions, A* aims at the
// goal with the same guarantee, and D* reuses an existing plan when the map changes.

private const val GRID_ROWS = 6
private const val GRID_COLS = 8

private class PathFrame(
    val visited: Set<Int>,
    val frontier: Set<Int>,
    val current: Int?,
    val path: Set<Int>,
    val walls: Set<Int>,
    val status: String,
)

private class PathConfig(
    val intro: String,
    val build: () -> List<PathFrame>,
)

private val VisitedCell = Color(0xFF3B82F6)
private val FrontierCell = Color(0xFF8B5CF6)
private val CurrentCell = Color(0xFFFACC15)
private val PathCell = SimColors.Green
private val WallCell = Color(0xFF39414F)
private val StartCell = Color(0xFF0EA5E9)
private val GoalCell = Color(0xFFF97316)

private fun key(r: Int, c: Int) = r * GRID_COLS + c
private fun rowOf(k: Int) = k / GRID_COLS
private fun colOf(k: Int) = k % GRID_COLS

private val START = key(2, 0)
private val GOAL = key(2, 7)

// A wall column with one gap, so a blind search wastes effort exploring the wrong side of it.
private val baseWalls = setOf(key(0, 4), key(1, 4), key(2, 4), key(4, 4), key(5, 4))

private fun neighbours(k: Int, walls: Set<Int>): List<Int> {
    val r = rowOf(k)
    val c = colOf(k)
    return listOf(r - 1 to c, r + 1 to c, r to c - 1, r to c + 1)
        .filter { (nr, nc) -> nr in 0 until GRID_ROWS && nc in 0 until GRID_COLS }
        .map { (nr, nc) -> key(nr, nc) }
        .filter { it !in walls }
}

private fun manhattan(a: Int, b: Int) = abs(rowOf(a) - rowOf(b)) + abs(colOf(a) - colOf(b))

/**
 * One best-first search, parameterised by [heuristic]. Zero heuristic = Dijkstra (uniform cost);
 * Manhattan distance = A*. Records a frame per expansion, then walks the path back.
 */
private fun searchFrames(
    walls: Set<Int>,
    heuristic: (Int) -> Int,
    label: String,
    frames: MutableList<PathFrame> = mutableListOf(),
    startNote: String? = null,
): List<PathFrame> {
    val dist = HashMap<Int, Int>()
    val cameFrom = HashMap<Int, Int>()
    val visited = linkedSetOf<Int>()
    val open = linkedSetOf(START)
    dist[START] = 0

    startNote?.let { frames.add(PathFrame(emptySet(), setOf(START), null, emptySet(), walls, it)) }

    while (open.isNotEmpty()) {
        val current = open.minByOrNull { dist.getValue(it) + heuristic(it) }!!
        open.remove(current)
        visited.add(current)

        if (current == GOAL) {
            val path = linkedSetOf(GOAL)
            var node = GOAL
            while (node != START) {
                node = cameFrom.getValue(node)
                path.add(node)
            }
            frames.add(
                PathFrame(
                    visited.toSet(), open.toSet(), current, path,
                    walls,
                    "Goal reached. $label expanded ${visited.size} cells; the path is ${path.size - 1} steps.",
                ),
            )
            return frames
        }

        frames.add(
            PathFrame(
                visited.toSet(), open.toSet(), current, emptySet(), walls,
                "Expand (${rowOf(current)}, ${colOf(current)}) — cost so far ${dist.getValue(current)}" +
                    if (heuristic(current) > 0) ", estimate to goal ${heuristic(current)}" else "",
            ),
        )

        neighbours(current, walls).forEach { next ->
            val candidate = dist.getValue(current) + 1
            if (candidate < (dist[next] ?: Int.MAX_VALUE)) {
                dist[next] = candidate
                cameFrom[next] = current
                if (next !in visited) open.add(next)
            }
        }
    }
    frames.add(PathFrame(visited.toSet(), emptySet(), null, emptySet(), walls, "No path exists."))
    return frames
}

private fun dijkstraFrames(): List<PathFrame> = searchFrames(
    walls = baseWalls,
    heuristic = { 0 },
    label = "Dijkstra",
    startNote = "Dijkstra expands whichever reachable cell is cheapest so far — no idea where the goal is, so it spreads outward in every direction.",
)

private fun aStarFrames(): List<PathFrame> = searchFrames(
    walls = baseWalls,
    heuristic = { manhattan(it, GOAL) },
    label = "A*",
    startNote = "A* ranks cells by cost-so-far + estimated distance to the goal. The estimate never overshoots, so the path is still optimal — it just gets there having opened far fewer cells.",
)

// D*: plan once, then a wall appears on the committed path and only the affected region is replanned.
private fun dStarFrames(): List<PathFrame> {
    val frames = mutableListOf<PathFrame>()
    searchFrames(
        walls = baseWalls,
        heuristic = { manhattan(it, GOAL) },
        label = "The initial plan",
        frames = frames,
        startNote = "D* starts from a full plan, exactly like A*.",
    )

    val original = frames.last().path
    // Block a cell the robot was about to cross.
    val blocked = original.filter { colOf(it) == 5 }.minByOrNull { rowOf(it) } ?: key(2, 5)
    val newWalls = baseWalls + blocked

    frames.add(
        PathFrame(
            emptySet(), emptySet(), blocked, original, newWalls,
            "The robot starts moving, then discovers a new obstacle at (${rowOf(blocked)}, ${colOf(blocked)}) — right on the committed path.",
        ),
    )
    frames.add(
        PathFrame(
            emptySet(), emptySet(), null, emptySet(), newWalls,
            "A full replan would throw away everything. D* keeps the costs that are still valid and repairs only the region the obstacle invalidated.",
        ),
    )

    searchFrames(
        walls = newWalls,
        heuristic = { manhattan(it, GOAL) },
        label = "The repair",
        frames = frames,
        startNote = "Repairing from the affected cell outward…",
    )
    return frames
}

private val pathConfigs = mapOf(
    "dijkstras_algorithm" to PathConfig(
        intro = "Dijkstra on a walled grid, every step costing 1. With no sense of direction it expands in rings until the goal happens to fall inside one.",
        build = ::dijkstraFrames,
    ),
    "a_star_search" to PathConfig(
        intro = "A* on the same grid and the same walls. Adding a Manhattan-distance estimate to the priority pulls the search straight at the goal — compare the number of expanded cells with Dijkstra's.",
        build = ::aStarFrames,
    ),
    "d_star_algorithm" to PathConfig(
        intro = "D* is A* for a map that changes underneath you: plan, start driving, discover an obstacle, then repair the affected part of the plan instead of starting over.",
        build = ::dStarFrames,
    ),
)

private fun pathConfigFor(topicId: String): PathConfig =
    pathConfigs[topicId] ?: pathConfigs.getValue("a_star_search")

@Composable
fun PathfindingGridSection(topicId: String) {
    val config = remember(topicId) { pathConfigFor(topicId) }
    val frames = remember(config) { config.build() }
    val playback = rememberPlaybackState(key = config, stepCount = frames.size, initialSpeedMs = 400f)
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

            Grid(frame = frame, modifier = Modifier.padding(top = 14.dp))

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
                PathLegend(CurrentCell, "Expanding")
                PathLegend(FrontierCell, "Frontier")
                PathLegend(VisitedCell, "Visited")
                PathLegend(PathCell, "Path")
            }

            PlaybackTransport(playback)
        }
    }
}

@Composable
private fun PathLegend(color: Color, label: String) {
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
private fun Grid(frame: PathFrame, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(GRID_COLS.toFloat() / GRID_ROWS),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        for (r in 0 until GRID_ROWS) {
            Row(
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                for (c in 0 until GRID_COLS) {
                    val k = key(r, c)
                    // Start/goal markers stay visible through every search state except the final
                    // path, which is the one thing worth reading over them.
                    val color = when {
                        k in frame.walls -> WallCell
                        k in frame.path -> PathCell
                        k == frame.current -> CurrentCell
                        k == START -> StartCell
                        k == GOAL -> GoalCell
                        k in frame.frontier -> FrontierCell
                        k in frame.visited -> VisitedCell.copy(alpha = 0.55f)
                        else -> VisitedCell.copy(alpha = 0.10f)
                    }
                    Box(
                        // weight gives the width; the row's height comes from the grid's aspect
                        // ratio, so the cell must fill it or it collapses to its text height.
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(color, RoundedCornerShape(5.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        val marker = when (k) {
                            START -> "S"
                            GOAL -> "G"
                            else -> null
                        }
                        if (marker != null) {
                            Text(
                                marker,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                            )
                        }
                    }
                }
            }
        }
    }
}
