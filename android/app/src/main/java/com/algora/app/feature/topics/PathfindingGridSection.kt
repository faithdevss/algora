package com.algora.app.feature.topics

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.algora.app.core.ui.theme.IBMPlexMono
import com.algora.app.core.ui.theme.SimColors
import kotlin.math.abs

// ── Pathfinding grid ─────────────────────────────────────────────────────────
// Shortest-path search over a small walled grid, one frame per expansion. Same precomputed-snapshot
// model as the other players; what differs per algorithm is only the priority used to pop the next
// cell, so Dijkstra / A* / D* share one search loop and one renderer.
//
// The point the frames are built to show: Dijkstra expands blindly in all directions, A* aims at the
// goal with the same guarantee, and D* reuses an existing plan when the map changes.
//
// Drawn as the story card (docs mock for Dijkstra): each reached cell carries its distance, the
// frontier is dashed, the goal is ringed, and a step's headline names the cell it expands.

private const val GRID_ROWS = 6
private const val GRID_COLS = 8

private class PathFrame(
    val visited: Set<Int>,
    val frontier: Set<Int>,
    val current: Int?,
    val path: Set<Int>,
    val walls: Set<Int>,
    val status: String,
    // The number drawn in a cell: its distance from S so far.
    val dist: Map<Int, Int> = emptyMap(),
    // A hand-written step: `{…}` in the headline takes the current cell's colour. Null narrates [status].
    val headline: String? = null,
    val body: String = "",
    val chips: List<StoryChip> = emptyList(),
) {
    val caption: String get() = headline?.let { storyPlain(it) + if (body.isEmpty()) "" else " $body" } ?: status
}

private class PathVariant(val label: String, val build: () -> List<PathFrame>)

private class PathConfig(
    val intro: String,
    val build: () -> List<PathFrame>,
    val title: String = "",
    val note: String = "",
    // Tabs over the grid (Dijkstra / A*), and which one the topic opens on.
    val variants: List<PathVariant> = emptyList(),
    val initialTab: Int = 0,
    // Whether G is a goal to ring; the flood-fill patterns use the markers for other things.
    val goal: Boolean = true,
    val pathLabel: String = "Path",
    val wallLabel: String? = null,
)

private val VisitedCell = SimColors.Blue
private val CurrentCell = SimColors.Active
private val PathCell = SimColors.Green
private val GoalRing = SimColors.Answer

private fun key(r: Int, c: Int) = r * GRID_COLS + c
private fun rowOf(k: Int) = k / GRID_COLS
private fun colOf(k: Int) = k % GRID_COLS
private fun rc(k: Int) = "(${rowOf(k)}, ${colOf(k)})"

private val START = key(2, 0)
private val GOAL = key(2, 7)

// A wall column with one gap, so a blind search wastes effort exploring the wrong side of it.
private val baseWalls = setOf(key(0, 4), key(1, 4), key(2, 4), key(4, 4), key(5, 4))
private val wallGap = key(3, 4)

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
 * Manhattan distance = A*. Records a frame per expansion, after its neighbours are relaxed, then
 * walks the path back.
 */
private fun searchFrames(
    walls: Set<Int>,
    heuristic: (Int) -> Int,
    label: String,
    frames: MutableList<PathFrame> = mutableListOf(),
    startNote: String? = null,
): List<PathFrame> {
    val informed = heuristic(START) > 0
    val dist = HashMap<Int, Int>()
    val cameFrom = HashMap<Int, Int>()
    val visited = linkedSetOf<Int>()
    val open = linkedSetOf(START)
    dist[START] = 0
    var gapSeen = false

    fun labels() = dist.filterKeys { it in visited || it in open }
    fun chips(current: Int?): List<StoryChip> {
        val done = visited.size - if (current != null) 1 else 0
        return if (informed && current != null) {
            val g = dist.getValue(current)
            listOf(StoryChip("g", "$g"), StoryChip("f", "${g + heuristic(current)}"), StoryChip("visited", "$done"))
        } else {
            listOf(StoryChip("dist", "${current?.let { dist.getValue(it) } ?: 0}"), StoryChip("visited", "$done"), StoryChip("frontier", "${open.size}"))
        }
    }

    startNote?.let {
        frames.add(
            PathFrame(
                emptySet(), setOf(START), null, emptySet(), walls, it,
                dist = labels(),
                headline = "Start at {S} with distance 0.",
                body = it,
                chips = chips(null),
            ),
        )
    }

    while (open.isNotEmpty()) {
        val current = open.minByOrNull { dist.getValue(it) + heuristic(it) }!!
        open.remove(current)
        visited.add(current)
        val d = dist.getValue(current)

        if (current == GOAL) {
            val path = linkedSetOf(GOAL)
            var node = GOAL
            while (node != START) {
                node = cameFrom.getValue(node)
                path.add(node)
            }
            frames.add(
                PathFrame(
                    visited.toSet(), open.toSet(), null, path,
                    walls,
                    "Goal reached. $label expanded ${visited.size} cells; the path is ${path.size - 1} steps.",
                    dist = labels(),
                    headline = "Reach {m:G} at distance $d.",
                    body = "$label expanded ${visited.size} cells to get here. The green path is a shortest " +
                        "route back to S, ${path.size - 1} steps long.",
                    chips = listOf(StoryChip("dist", "$d", StoryTone.Done), StoryChip("visited", "${visited.size}")),
                ),
            )
            return frames
        }

        val added = mutableListOf<Int>()
        neighbours(current, walls).forEach { next ->
            val candidate = d + 1
            if (candidate < (dist[next] ?: Int.MAX_VALUE)) {
                dist[next] = candidate
                cameFrom[next] = current
                if (next !in visited && next !in open) added += next
                if (next !in visited) open.add(next)
            }
        }

        val h = heuristic(current)
        val throughGap = wallGap in added && wallGap !in walls && !gapSeen
        if (throughGap) gapSeen = true
        val headline = (if (informed) "Expand {${rc(current)}}: g = $d, f = ${d + h}." else "Expand {${rc(current)}} at distance $d.") +
            if (throughGap) " It borders the only gap in the wall." else ""
        val body = when {
            throughGap && informed -> "The gap cell joins the frontier at ${d + 1}. Its estimate says G is just past " +
                "the wall, so A* heads straight through."
            throughGap -> "The gap cell joins the frontier at ${d + 1}. $label doesn't know where G is, so it keeps " +
                "spreading in every direction."
            added.isEmpty() -> "Nothing new joins the frontier: every neighbour is a wall or already reached."
            informed -> "${added.size} new cell${if (added.size == 1) " joins" else "s join"} the frontier. The lowest f goes " +
                "next, so cells toward G come first."
            else -> "${added.size} new cell${if (added.size == 1) " joins" else "s join"} the frontier at ${d + 1}. Every cell " +
                "at distance $d is finished before any at ${d + 1}."
        }
        frames.add(
            PathFrame(
                visited.toSet() - current, open.toSet(), current, emptySet(), walls,
                "Expand ${rc(current)} — cost so far $d" + if (h > 0) ", estimate to goal $h" else "",
                dist = labels(),
                headline = headline,
                body = body,
                chips = chips(current),
            ),
        )
    }
    frames.add(PathFrame(visited.toSet(), emptySet(), null, emptySet(), walls, "No path exists."))
    return frames
}

private fun dijkstraFrames(): List<PathFrame> = searchFrames(
    walls = baseWalls,
    heuristic = { 0 },
    label = "Dijkstra",
    startNote = "Dijkstra always expands the closest cell it hasn't finished. It has no idea where G is.",
)

private fun aStarFrames(): List<PathFrame> = searchFrames(
    walls = baseWalls,
    heuristic = { manhattan(it, GOAL) },
    label = "A*",
    startNote = "A* expands the cell with the lowest f = g + h, where h counts the grid steps left to G. " +
        "h never overshoots, so the path is still the shortest.",
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

// UCS's separation from Dijkstra is not the relaxation — it is the goal test and the lazy successor
// function. To make the goal test visible the two direct approaches to the goal carry a toll, so the
// goal gets generated at a high cost long before the cheap route around reaches it.
private val tolledEdges = setOf(key(2, 6) to GOAL, key(3, 7) to GOAL)
private const val TOLL = 9

private fun stepCost(from: Int, to: Int) = if ((from to to) in tolledEdges) TOLL else 1

private fun ucsFrames(): List<PathFrame> {
    val walls = baseWalls
    val frames = mutableListOf<PathFrame>()
    val dist = HashMap<Int, Int>()
    val cameFrom = HashMap<Int, Int>()
    val visited = linkedSetOf<Int>()
    val open = linkedSetOf(START)
    dist[START] = 0
    var goalFirstSeenAt: Int? = null

    frames += PathFrame(
        emptySet(), setOf(START), null, emptySet(), walls,
        "Uniform cost search: f = g, nothing else. The two cells that touch the goal from the left and below charge a " +
            "toll of $TOLL; every other move costs 1.",
    )

    while (open.isNotEmpty()) {
        val current = open.minByOrNull { dist.getValue(it) }!!
        open.remove(current)

        // Goal test on POP, not on generation — this is the line the whole topic turns on.
        if (current == GOAL) {
            val path = linkedSetOf(GOAL)
            var node = GOAL
            while (node != START) {
                node = cameFrom.getValue(node)
                path.add(node)
            }
            frames += PathFrame(
                visited.toSet(), open.toSet(), current, path, walls,
                "Goal popped at cost ${dist.getValue(GOAL)} after ${visited.size} expansions. It was first generated at cost " +
                    "${goalFirstSeenAt ?: dist.getValue(GOAL)} — testing at generation time would have returned that path and called it done.",
                dist = dist.filterKeys { it in visited || it in open || it == GOAL },
            )
            return frames
        }
        visited.add(current)

        val generated = mutableListOf<String>()
        neighbours(current, walls).forEach { next ->
            val candidate = dist.getValue(current) + stepCost(current, next)
            if (candidate < (dist[next] ?: Int.MAX_VALUE)) {
                val improving = dist.containsKey(next)
                dist[next] = candidate
                cameFrom[next] = current
                if (next !in visited) open.add(next)
                if (next == GOAL && goalFirstSeenAt == null) goalFirstSeenAt = candidate
                generated += if (improving) "lowered (${rowOf(next)}, ${colOf(next)}) to $candidate" else "(${rowOf(next)}, ${colOf(next)}) at $candidate"
            }
        }

        frames += PathFrame(
            visited.toSet(), open.toSet(), current, emptySet(), walls,
            "Pop (${rowOf(current)}, ${colOf(current)}) at cost ${dist.getValue(current)} — that cost is now final. " +
                if (generated.isEmpty()) "Its successors are all settled or already cheaper." else "Successors: ${generated.joinToString("; ")}.",
            dist = dist.filterKeys { it in visited || it in open },
        )
    }
    frames += PathFrame(visited.toSet(), emptySet(), null, emptySet(), walls, "No path exists.")
    return frames
}

// IDA*: f-bounded DFS, restarted at the minimum overshoot. Frames are emitted per expansion only —
// pruned branches are folded into the parent's status, which is also how a reader should think of them.
private fun idaStarFrames(): List<PathFrame> {
    val walls = baseWalls
    val frames = mutableListOf<PathFrame>()
    val h = { k: Int -> manhattan(k, GOAL) }
    var threshold = h(START)
    var iteration = 0
    var totalExpansions = 0

    frames += PathFrame(
        emptySet(), emptySet(), START, emptySet(), walls,
        "IDA* keeps no frontier at all — only the current path on the recursion stack. The first threshold is " +
            "h(start) = $threshold, the cheapest the answer could possibly be.",
    )

    while (iteration < 12) {
        iteration++
        val touched = linkedSetOf<Int>()
        val path = mutableListOf(START)
        var nextThreshold = Int.MAX_VALUE
        var expansions = 0

        frames += PathFrame(
            emptySet(), emptySet(), START, setOf(START), walls,
            "Iteration $iteration: depth-first search, abandoning any cell with f = g + h greater than $threshold.",
        )

        fun search(g: Int): Boolean {
            val node = path.last()
            touched.add(node)
            expansions++
            totalExpansions++

            if (node == GOAL) {
                frames += PathFrame(
                    touched.toSet(), emptySet(), node, path.toSet(), walls,
                    "Goal reached at g = $g under threshold $threshold. Manhattan distance never overestimates, so this " +
                        "first goal is already optimal — $totalExpansions expansions in total across $iteration iteration(s).",
                )
                return true
            }

            val pruned = mutableListOf<Int>()
            val open = mutableListOf<Int>()
            for (next in neighbours(node, walls)) {
                if (next in path) continue          // the only cycle check a stack affords
                val f = g + 1 + h(next)
                if (f > threshold) {
                    pruned += f
                    nextThreshold = minOf(nextThreshold, f)
                } else {
                    open += next
                }
            }

            frames += PathFrame(
                touched.toSet(), open.toSet(), node, path.toSet(), walls,
                "At (${rowOf(node)}, ${colOf(node)}): g = $g, h = ${h(node)}, f = ${g + h(node)}. " +
                    if (pruned.isEmpty()) "All ${open.size} successor(s) fit under the threshold."
                    else "${pruned.size} successor(s) pruned at f = ${pruned.distinct().sorted().joinToString("/")}; ${open.size} left to try.",
            )

            for (next in open) {
                path += next
                if (search(g + 1)) return true
                path.removeAt(path.lastIndex)
            }

            if (open.isEmpty()) {
                frames += PathFrame(
                    touched.toSet(), emptySet(), node, path.toSet(), walls,
                    "Dead end at (${rowOf(node)}, ${colOf(node)}) — every successor is either a wall, already on the path, " +
                        "or over budget. Pop the stack.",
                )
            }
            return false
        }

        if (search(0)) return frames

        if (nextThreshold == Int.MAX_VALUE) {
            frames += PathFrame(touched.toSet(), emptySet(), null, emptySet(), walls, "Nothing left to raise the threshold to — no path exists.")
            return frames
        }
        frames += PathFrame(
            touched.toSet(), emptySet(), null, emptySet(), walls,
            "Iteration $iteration failed after $expansions expansions. The smallest f it had to prune was $nextThreshold, so that " +
                "becomes the next threshold — never a guess, and never a fixed increment. Everything so far is discarded and re-expanded.",
        )
        threshold = nextThreshold
    }
    return frames
}

// ── Interview-prep pattern: one wave from many sources ───────────────────────
// The seeds are START and GOAL, so the renderer's S and G markers land on the two sources instead of
// implying a search between them. Walls are the empty cells no wave can cross.
private val rotSources = setOf(START, GOAL)
private val rotWalls = setOf(key(0, 3), key(1, 3), key(3, 5), key(4, 5), key(5, 5))

private fun multiSourceBfsFrames(): List<PathFrame> {
    val frames = mutableListOf<PathFrame>()
    val dist = HashMap<Int, Int>()
    var frontier = rotSources.toSet()
    frontier.forEach { dist[it] = 0 }
    val visited = frontier.toMutableSet()

    frames += PathFrame(
        emptySet(), frontier, null, emptySet(), rotWalls,
        dist = dist.toMap(),
        status = "Both sources are seeded into the queue at distance 0 *before* the loop starts. That initialisation is the " +
            "entire pattern — the loop below is ordinary BFS and never learns there was more than one source.",
    )

    var round = 0
    while (frontier.isNotEmpty()) {
        round++
        val next = mutableSetOf<Int>()
        frontier.forEach { cell ->
            neighbours(cell, rotWalls).forEach { n ->
                if (visited.add(n)) {
                    dist[n] = round
                    next += n
                }
            }
        }
        if (next.isEmpty()) break
        frames += PathFrame(
            visited - next, next, null, emptySet(), rotWalls,
            "Minute $round: the whole frontier advances one step together, claiming ${next.size} cell(s). A cell is " +
                "marked the moment it is queued, so the wave that got there first keeps it — no comparison between " +
                "sources is ever needed.",
            dist = dist.toMap(),
        )
        frontier = next
    }

    val farthest = dist.maxByOrNull { it.value }!!
    val unreached = (0 until GRID_ROWS * GRID_COLS).toSet() - rotWalls - visited
    frames += PathFrame(
        visited, emptySet(), farthest.key, setOf(farthest.key), rotWalls,
        "Everything reachable is claimed after ${farthest.value} minute(s) — the answer is the largest distance " +
            "assigned, and ${if (unreached.isEmpty()) "no cell was left out" else "${unreached.size} walled-off cell(s) were never reached, which is the case that returns −1"}. " +
            "Running a separate BFS per source and taking the minimum gives the same numbers for k times the work.",
        dist = dist.toMap(),
    )
    return frames
}

// ── Interview-prep pattern: grid as a graph ──────────────────────────────────
// Land cells; everything else is water and reuses the wall colour. START and GOAL are deliberately
// water so the renderer's start/goal markers never appear — this lab has neither.
private val islandLand = setOf(
    key(0, 0), key(0, 1), key(1, 0), key(1, 1),
    key(0, 5), key(0, 6), key(1, 6),
    key(2, 3), key(3, 3), key(3, 4),
    key(4, 0), key(5, 0), key(5, 1),
    key(4, 6), key(4, 7), key(5, 7),
)

// The outer scan starts one flood per unvisited land cell, so the number of floods started is the
// number of connected components — no counting of anything else is needed.
private fun islandCountFrames(): List<PathFrame> {
    val water = (0 until GRID_ROWS * GRID_COLS).toSet() - islandLand
    val frames = mutableListOf<PathFrame>()
    val finished = mutableSetOf<Int>()
    val visited = mutableSetOf<Int>()
    var islands = 0

    frames += PathFrame(
        emptySet(), emptySet(), null, emptySet(), water,
        "A grid is a graph: cells are nodes, the four neighbours are edges, and \"count the islands\" is \"count the " +
            "connected components\". ${islandLand.size} land cells, no start and no goal.",
    )

    for (cell in 0 until GRID_ROWS * GRID_COLS) {
        if (cell !in islandLand || cell in visited) continue
        islands++
        val region = mutableListOf(cell)
        val queue = ArrayDeque(listOf(cell))
        visited += cell                              // marked on enqueue, never on pop

        frames += PathFrame(
            visited.toSet() - region, queue.toSet(), cell, finished.toSet(), water,
            "The scan hits unvisited land at (${rowOf(cell)}, ${colOf(cell)}). That is island #$islands — flood it, " +
                "and the whole region is consumed before the scan resumes.",
        )

        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            val added = mutableListOf<Int>()
            for (next in neighbours(current, water)) {
                if (next in visited) continue
                visited += next                      // marking here, not on pop, keeps duplicates out of the queue
                queue.addLast(next)
                region += next
                added += next
            }
            frames += PathFrame(
                visited.toSet() - queue.toSet(), queue.toSet(), current, finished.toSet(), water,
                "Expand (${rowOf(current)}, ${colOf(current)}): " +
                    if (added.isEmpty()) "every neighbour is water or already marked."
                    else "${added.size} new land neighbour(s) marked and queued. Marking on enqueue is what stops a " +
                        "cell entering the queue twice.",
            )
        }

        finished += region
        frames += PathFrame(
            visited.toSet() - finished, emptySet(), null, finished.toSet(), water,
            "Island #$islands is complete at ${region.size} cell(s). Resume the outer scan from where it left off.",
        )
    }

    frames += PathFrame(
        emptySet(), emptySet(), null, finished.toSet(), water,
        "$islands islands. Every cell was examined a constant number of times, so the whole thing is O(rows × cols) — " +
            "the count comes from how many floods were started, not from anything the floods measured.",
    )
    return frames
}

// Dijkstra and A* share one grid and one pair of tabs, so either topic can flip to the other and compare.
private val searchTabs = listOf(PathVariant("Dijkstra", ::dijkstraFrames), PathVariant("A*", ::aStarFrames))

private val pathConfigs = mapOf(
    "multi_source_bfs_pattern" to PathConfig(
        intro = "Two rotten oranges spreading at once — the S and G markers are the seeds, not a start and a goal. " +
            "Every cell is claimed by whichever wave reaches it first, so one sweep answers all of them.",
        build = ::multiSourceBfsFrames,
        title = "TWO SOURCES",
        note = "minutes to reach",
        goal = false,
        pathLabel = "Last reached",
    ),
    "matrix_islands_pattern" to PathConfig(
        intro = "Counting islands by flood fill. Dark cells are water, and each fill consumes one whole region before " +
            "the outer scan moves on — the number of fills started is the answer.",
        build = ::islandCountFrames,
        title = "ISLANDS",
        note = "flood fill",
        goal = false,
        pathLabel = "Island done",
        wallLabel = "Water",
    ),
    "dijkstras_algorithm" to PathConfig(
        intro = "Dijkstra on a walled grid, every step costing 1. With no sense of direction it expands in rings until the goal happens to fall inside one.",
        build = ::dijkstraFrames,
        variants = searchTabs,
        initialTab = 0,
    ),
    "a_star_search" to PathConfig(
        intro = "A* on the same grid and the same walls. Adding a Manhattan-distance estimate to the priority pulls the search straight at the goal — compare the number of expanded cells with Dijkstra's.",
        build = ::aStarFrames,
        variants = searchTabs,
        initialTab = 1,
    ),
    "d_star_algorithm" to PathConfig(
        intro = "D* is A* for a map that changes underneath you: plan, start driving, discover an obstacle, then repair the affected part of the plan instead of starting over.",
        build = ::dStarFrames,
        title = "PLAN, THEN REPAIR",
        note = "f = g + h",
    ),
    "uniform_cost_search" to PathConfig(
        intro = "UCS is Dijkstra's relaxation with a goal test bolted on and the graph generated as it goes. The two " +
            "approaches to the goal charge a toll, so the goal is generated cheaply-looking-expensive long before the " +
            "real answer arrives — which is exactly why the test happens on pop.",
        build = ::ucsFrames,
        title = "UNIFORM COST",
        note = "toll $TOLL into G",
    ),
    "ida_star" to PathConfig(
        intro = "The same map and the same Manhattan heuristic as A*, but no frontier: a depth-first search bounded by " +
            "f = g + h, restarted at the smallest f it had to prune. Watch the threshold rise and the search start over.",
        build = ::idaStarFrames,
        title = "ITERATIVE DEEPENING",
        note = "f ≤ threshold",
    ),
)

private fun pathConfigFor(topicId: String): PathConfig =
    pathConfigs[topicId] ?: pathConfigs.getValue("a_star_search")

internal val pathfindingTopicIds: Set<String> get() = pathConfigs.keys

internal fun pathfindingFrameCount(topicId: String): Int {
    val config = pathConfigFor(topicId)
    val builds = if (config.variants.isEmpty()) listOf(config.build) else config.variants.map { it.build }
    return builds.sumOf { build ->
        val frames = build()
        frames.forEach { frame ->
            val cells = frame.visited + frame.frontier + frame.path + frame.walls + listOfNotNull(frame.current) + frame.dist.keys
            require(cells.all { it in 0 until GRID_ROWS * GRID_COLS }) { "$topicId references a cell outside the grid" }
            require(frame.caption.isNotBlank()) { "$topicId has a frame with no caption" }
        }
        frames.size
    }
}

@Composable
fun PathfindingGridSection(topicId: String) {
    val config = remember(topicId) { pathConfigFor(topicId) }
    var tab by remember(config) { mutableIntStateOf(config.initialTab) }
    val frames = remember(config, tab) { (config.variants.getOrNull(tab)?.build ?: config.build)() }
    val playback = rememberPlaybackState(key = config to tab, stepCount = frames.size, initialSpeedMs = 400f)
    val frame = frames[playback.index.coerceIn(0, frames.lastIndex)]

    Column(modifier = Modifier.fillMaxWidth()) {
        LabIntro(config.intro, Modifier.padding(bottom = 12.dp))

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                if (config.variants.isNotEmpty()) {
                    LabSegments(config.variants.map { it.label }, tab) { tab = it }
                } else {
                    StoryHeader(config.title, config.note)
                }
                Grid(frame, config, Modifier.padding(top = 14.dp))
                val muted = MaterialTheme.colorScheme.onSurfaceVariant
                StoryLegendRow(
                    listOfNotNull(
                        Triple(CurrentCell, SwatchStyle.Fill, "Expanding").takeIf { frame.current != null },
                        Triple(VisitedCell, SwatchStyle.Fill, "Visited").takeIf { frame.visited.isNotEmpty() },
                        Triple(VisitedCell, SwatchStyle.Dashed, "Frontier").takeIf { frame.frontier.isNotEmpty() },
                        Triple(PathCell, SwatchStyle.Fill, config.pathLabel).takeIf { frame.path.isNotEmpty() },
                        config.wallLabel?.let { Triple(muted.copy(alpha = 0.45f), SwatchStyle.Fill, it) },
                        Triple(GoalRing, SwatchStyle.Ring, "Goal").takeIf { config.goal && GOAL !in frame.path },
                    ),
                    Modifier.padding(top = 14.dp),
                )
            }
        }

        StoryChips(frame.chips, Modifier.padding(top = 16.dp))
        val narration = Modifier.padding(top = 16.dp)
        if (frame.headline != null) {
            LabStoryNarration(frame.headline, frame.body, narration)
        } else {
            LabNarration(frame.status, narration)
        }

        PlaybackTransport(playback, captions = frames.map { it.caption })
    }
}

@Composable
private fun Grid(frame: PathFrame, config: PathConfig, modifier: Modifier = Modifier) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val gap = 6.dp
    Column(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(GRID_COLS.toFloat() / GRID_ROWS),
        verticalArrangement = Arrangement.spacedBy(gap),
    ) {
        for (r in 0 until GRID_ROWS) {
            Row(
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(gap),
            ) {
                for (c in 0 until GRID_COLS) {
                    val k = key(r, c)
                    val shape = RoundedCornerShape(8.dp)
                    val wall = k in frame.walls
                    val frontier = k in frame.frontier && k != frame.current
                    val fill = when {
                        wall -> muted.copy(alpha = 0.45f)
                        k in frame.path -> PathCell
                        k == frame.current -> CurrentCell
                        k in frame.visited -> VisitedCell
                        frontier -> VisitedCell.copy(alpha = 0.08f)
                        else -> SimColors.Tint
                    }
                    val ink = when {
                        k == frame.current && k !in frame.path -> Color(0xFF1F1A0A)
                        k in frame.path || k in frame.visited -> Color.White
                        frontier -> StoryTone.Path.ink()
                        k == GOAL && config.goal -> GoalRing
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                    val ringed = k == GOAL && config.goal && !wall && fill == SimColors.Tint
                    Box(
                        // weight gives the width; the row's height comes from the grid's aspect
                        // ratio, so the cell must fill it or it collapses to its text height.
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(if (ringed) Color.Transparent else fill, shape)
                            .then(
                                when {
                                    ringed -> Modifier.border(2.dp, GoalRing, shape)
                                    frontier && !wall -> Modifier.dashedOutline(VisitedCell, 8.dp, 3.dp, 2.5.dp, 1.5.dp)
                                    else -> Modifier
                                },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        val text = when {
                            wall -> null
                            k == START -> "S"
                            k == GOAL -> "G"
                            else -> frame.dist[k]?.toString()
                        }
                        if (text != null) {
                            Text(text, fontFamily = IBMPlexMono, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = ink, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}
