package com.algora.app.feature.topics

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.graphics.luminance
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.unit.sp
import com.algora.app.core.ui.theme.SimColors
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

// ── Graph algorithm player ───────────────────────────────────────────────────
// Shortest-path / MST / SCC algorithms over a small fixed graph, one precomputed frame per
// interesting event (same snapshot model as the pathfinding and sorting players). What differs per
// topic is the graph, the frame builder, and which annotations each frame carries — the renderer is
// shared: node fill + badge under the node, edge colour, arrowheads for directed graphs, and an
// optional distance matrix for Floyd–Warshall.
//
// Frames deliberately skip the no-op steps (an edge that relaxes nothing, a k that improves
// nothing); a viewer learns from the updates, not from watching 40 comparisons decline to fire.

private const val GRAPH_INF = Int.MAX_VALUE / 4

private class GNode(val id: String, val x: Float, val y: Float)

private class GEdge(val from: String, val to: String, val weight: Int? = null, val directed: Boolean = false)

private class GraphDef(val nodes: List<GNode>, val edges: List<GEdge>) {
    val ids: List<String> = nodes.map { it.id }
}

private enum class NodeMark { IDLE, FRONTIER, ACTIVE, UPDATED, DONE }

private enum class EdgeMark { IDLE, ACTIVE, ACCEPTED, REJECTED }

private val NodeMarkColors = mapOf(
    NodeMark.IDLE to Color(0xFF7C3AED),
    NodeMark.FRONTIER to SimColors.Blue,
    NodeMark.ACTIVE to SimColors.Active,
    NodeMark.UPDATED to SimColors.Green,
    NodeMark.DONE to Color(0xFFF97316),
)

private val EdgeMarkColors = mapOf(
    EdgeMark.IDLE to SimColors.Idle,
    EdgeMark.ACTIVE to SimColors.Active,
    EdgeMark.ACCEPTED to SimColors.Green,
    EdgeMark.REJECTED to SimColors.Red,
)

// Component / SCC colours — used when a frame groups nodes rather than marking them individually.
// Deliberately starts far from the idle violet: an assigned first component has to read as a change.
private val GroupColors = listOf(
    Color(0xFF0EA5E9), Color(0xFFF97316), Color(0xFF10B981),
    Color(0xFFEC4899), Color(0xFF6366F1), Color(0xFFA855F7),
)

private class GraphAlgoFrame(
    val status: String,
    val nodeMarks: Map<String, NodeMark> = emptyMap(),
    val badges: Map<String, String> = emptyMap(),
    val groups: Map<String, Int> = emptyMap(),
    val edgeMarks: Map<Int, EdgeMark> = emptyMap(),
    // Kosaraju's second pass runs on the transpose; the renderer flips every arrow instead of the
    // frame carrying a whole second edge list.
    val reversed: Boolean = false,
    // The graph-variants lab shows one node set under four readings, so a frame can suppress the
    // arrowheads, the weights, or an edge entirely rather than carrying four separate graphs.
    val undirected: Boolean = false,
    val hideWeights: Boolean = false,
    val hiddenEdges: Set<Int> = emptySet(),
    val matrix: List<List<Int>>? = null,
    val matrixFocus: Pair<Int, Int>? = null,
    val matrixVia: Int? = null,
    // The edge this frame is deciding on. Drawn in the active yellow whatever its mark, so the
    // edge being judged stands out from the ones already judged.
    val focusEdge: Int? = null,
    // A hand-written headline (Kruskal, Prim). Frames without one split `status` with
    // LabCaptionText.split: its first sentence becomes the headline, the rest the explanation.
    val headline: List<GraphSpan>? = null,
    val body: String? = null,
    // The redesigned labs (docs mocks) carry everything they draw here instead.
    val story: GraphStory? = null,
)

// A piece of a headline; a tone colours it to match what it names in the graph.
private enum class GraphTone { ACTIVE, REJECTED, DONE }

private class GraphSpan(val text: String, val tone: GraphTone? = null)


private class GraphAlgoConfig(
    val intro: String,
    val def: GraphDef,
    val legend: List<Pair<Color, String>>,
    val build: () -> List<GraphAlgoFrame>,
    // Lists every edge sorted by weight under the graph. Kruskal's whole algorithm is a walk down
    // that list, and Prim reads its candidates off it.
    val edgeTable: Boolean = false,
    // Tabs over a story card, each its own graph and storyboard (Path / Cycle).
    val variants: List<GraphVariant> = emptyList(),
)

private class GraphVariant(val label: String, val def: GraphDef, val build: () -> List<GraphAlgoFrame>)

// ── Story frames ─────────────────────────────────────────────────────────────
// The redesigned graph labs: solid nodes coloured by what the algorithm is doing to them, a badge above
// or below each (a degree, disc/low, a distance), coloured edges with optional boxed labels, rows of
// cells for the structure the algorithm keeps beside the graph (path, queue, stack, dist), then chips
// and a headline whose key term takes its node's colour.

// ACTIVE is the current node or edge, PATH what is on the path / stack / queue, DONE finished or used,
// CUT a cut vertex or bridge (and a min cut), WARN something missing, EMPTY a slot still to fill.
private enum class GS { IDLE, ACTIVE, PATH, DONE, CUT, WARN, EMPTY }

private class GCell(val text: String, val tone: GS = GS.IDLE)

// A labelled run of cells; several groups can share one row (CIRCUIT · SUB-TOUR, QUEUE · OUTPUT).
private class GCellGroup(val label: String?, val cells: List<GCell>, val note: String? = null)

// One line of a comparison list: the test on the left, what it decides on the right.
private class GLine(val test: String, val verdict: String, val tone: GS)

private class GChip(val key: String, val value: String, val tone: GS? = null)

private class GraphStory(
    val title: String,
    val note: String,
    val headline: String,
    val body: String,
    val nodes: Map<String, GS> = emptyMap(),
    // Text and tone; drawn above a node in the top third of the graph, below it otherwise.
    val badges: Map<String, Pair<String, GS>> = emptyMap(),
    val edges: Map<Int, GS> = emptyMap(),
    // Replaces an edge's weight (a flow "2/3"); [boxed] draws the label in a pill on the edge.
    val edgeLabels: Map<Int, String> = emptyMap(),
    val boxed: Set<Int> = emptySet(),
    // Pairs with no edge between them, drawn dashed red: the edge a cycle would need.
    val missing: List<Pair<String, String>> = emptyList(),
    val reversed: Boolean = false,
    val rows: List<List<GCellGroup>> = emptyList(),
    val lines: List<GLine> = emptyList(),
    val chips: List<GChip> = emptyList(),
    val emphasis: GS = GS.ACTIVE,
    val legend: List<Pair<GS, String>> = emptyList(),
) {
    val status: String get() = GraphMark.replace(headline) { it.groupValues[2] } + " " + body
}

// `{…}` takes the story's emphasis colour; `{w:…}` red, `{a:…}` yellow, `{p:…}` blue, `{m:…}` green and
// `{c:…}` violet name it outright.
private val GraphMark = Regex("""\{(?:([wapmc]):)?(.+?)\}""")

private fun storyFrame(story: GraphStory) = GraphAlgoFrame(story.status, story = story)

// The index of the edge joining two nodes, in either direction.
private fun GraphDef.edgeIndex(a: String, b: String): Int =
    edges.indexOfFirst { (it.from == a && it.to == b) || (it.from == b && it.to == a) }.also {
        require(it >= 0) { "no edge $a–$b" }
    }

// ── Graphs ───────────────────────────────────────────────────────────────────

// Directed, weighted, with negative edges — the case Dijkstra cannot handle.
private val negativeWeightGraph = GraphDef(
    nodes = listOf(
        GNode("A", 0.06f, 0.50f),
        GNode("B", 0.40f, 0.12f),
        GNode("C", 0.80f, 0.12f),
        GNode("D", 0.40f, 0.88f),
        GNode("E", 0.80f, 0.88f),
    ),
    edges = listOf(
        GEdge("A", "B", 6, directed = true),
        GEdge("A", "D", 7, directed = true),
        GEdge("B", "C", 5, directed = true),
        GEdge("B", "D", 8, directed = true),
        GEdge("B", "E", -4, directed = true),
        GEdge("C", "B", -2, directed = true),
        GEdge("D", "C", -3, directed = true),
        GEdge("D", "E", 9, directed = true),
        GEdge("E", "C", 7, directed = true),
        GEdge("E", "A", 2, directed = true),
    ),
)

// Small directed graph, kept to 4 nodes so the whole distance matrix fits on a phone.
private val allPairsGraph = GraphDef(
    nodes = listOf(
        GNode("A", 0.15f, 0.15f),
        GNode("B", 0.85f, 0.15f),
        GNode("C", 0.85f, 0.85f),
        GNode("D", 0.15f, 0.85f),
    ),
    edges = listOf(
        GEdge("A", "B", 3, directed = true),
        GEdge("A", "D", 7, directed = true),
        GEdge("B", "A", 8, directed = true),
        GEdge("B", "C", 2, directed = true),
        GEdge("C", "A", 5, directed = true),
        GEdge("C", "D", 1, directed = true),
        GEdge("D", "A", 2, directed = true),
    ),
)

// Undirected, weighted, connected — shared by both MST builders so their outputs can be compared.
private val mstGraph = GraphDef(
    nodes = listOf(
        GNode("A", 0.10f, 0.22f),
        GNode("B", 0.50f, 0.05f),
        GNode("C", 0.90f, 0.24f),
        GNode("D", 0.14f, 0.80f),
        GNode("E", 0.54f, 0.95f),
        GNode("F", 0.92f, 0.74f),
    ),
    edges = listOf(
        GEdge("A", "B", 4),
        GEdge("A", "D", 3),
        GEdge("B", "C", 5),
        GEdge("B", "D", 6),
        GEdge("B", "E", 2),
        GEdge("C", "E", 7),
        GEdge("C", "F", 4),
        GEdge("D", "E", 3),
        GEdge("E", "F", 5),
    ),
)

// One node set read four ways. Directedness, weights and the single back edge are toggled per frame,
// and each reading is followed by the measurement that reading changes.
private val variantsGraph = GraphDef(
    nodes = listOf(
        GNode("A", 0.08f, 0.22f),
        GNode("B", 0.42f, 0.06f),
        GNode("C", 0.78f, 0.24f),
        GNode("D", 0.30f, 0.60f),
        GNode("E", 0.70f, 0.66f),
        GNode("F", 0.44f, 0.96f),
    ),
    edges = listOf(
        GEdge("A", "B", 7, directed = true),
        GEdge("A", "D", 1, directed = true),
        GEdge("B", "C", 1, directed = true),
        GEdge("D", "B", 1, directed = true),
        GEdge("D", "E", 9, directed = true),
        GEdge("C", "E", 1, directed = true),
        GEdge("E", "F", 2, directed = true),
        GEdge("F", "D", 1, directed = true),
    ),
)

private fun graphVariantsFrames(): List<GraphAlgoFrame> {
    val def = variantsGraph
    val frames = mutableListOf<GraphAlgoFrame>()
    val backEdgeIndex = def.edges.indexOfFirst { it.from == "F" && it.to == "D" }

    fun reachable(from: String, directed: Boolean, skip: Set<Int> = emptySet()): Set<String> {
        val seen = mutableSetOf(from)
        val queue = ArrayDeque(listOf(from))
        while (queue.isNotEmpty()) {
            val cur = queue.removeFirst()
            def.edges.forEachIndexed { i, e ->
                if (i in skip) return@forEachIndexed
                val next = when {
                    e.from == cur -> e.to
                    !directed && e.to == cur -> e.from
                    else -> null
                }
                if (next != null && seen.add(next)) queue += next
            }
        }
        return seen
    }

    frames += GraphAlgoFrame(
        status = "Six vertices, eight edges. Everything below is the same set of pairs — what changes is only " +
            "what a pair is taken to mean, and every change costs an algorithm something.",
        undirected = true,
        hideWeights = true,
    )

    val undirectedFromC = reachable("C", directed = false)
    frames += GraphAlgoFrame(
        status = "Undirected: an edge is a mutual relation, so a walk from C reaches all " +
            "${undirectedFromC.size} vertices. Degrees sum to ${def.edges.size * 2} = 2 × ${def.edges.size} " +
            "edges — the handshake lemma, and the reason an undirected adjacency list stores every edge twice.",
        groups = undirectedFromC.associateWith { 2 },
        undirected = true,
        hideWeights = true,
    )

    val directedFromC = reachable("C", directed = true)
    val unreachable = def.ids - directedFromC
    frames += GraphAlgoFrame(
        status = "Directed: the same edges, now one-way. From C only ${directedFromC.sorted().joinToString(", ")} " +
            "are reachable — ${unreachable.sorted().joinToString(", ")} " +
            "${if (unreachable.size == 1) "is" else "are"} cut off, because nothing points back into " +
            "${unreachable.sorted().joinToString(" or ")}. Reachability stops being symmetric, which is why " +
            "directed graphs need SCCs rather than connected components.",
        groups = directedFromC.associateWith { 1 },
        nodeMarks = unreachable.associateWith { NodeMark.IDLE },
        hideWeights = true,
    )

    fun bfsHops(from: String, to: String): List<String> {
        val prev = mutableMapOf<String, String>()
        val seen = mutableSetOf(from)
        val queue = ArrayDeque(listOf(from))
        while (queue.isNotEmpty()) {
            val cur = queue.removeFirst()
            def.edges.filter { it.from == cur }.forEach { e ->
                if (seen.add(e.to)) { prev[e.to] = cur; queue += e.to }
            }
        }
        if (to !in seen) return emptyList()
        val path = mutableListOf(to)
        while (path.last() != from) path += prev.getValue(path.last())
        return path.reversed()
    }

    fun cheapestPath(from: String, to: String): Pair<List<String>, Int> {
        val dist = def.ids.associateWith { GRAPH_INF }.toMutableMap()
        val prev = mutableMapOf<String, String>()
        dist[from] = 0
        repeat(def.ids.size) {
            def.edges.forEach { e ->
                val d = dist.getValue(e.from)
                val w = e.weight ?: 1
                if (d + w < dist.getValue(e.to)) { dist[e.to] = d + w; prev[e.to] = e.from }
            }
        }
        val path = mutableListOf(to)
        while (path.last() != from) path += prev.getValue(path.last())
        return path.reversed() to dist.getValue(to)
    }

    fun edgeIndicesOf(path: List<String>): Set<Int> =
        path.zipWithNext().mapNotNull { (u, v) -> def.edges.indexOfFirst { it.from == u && it.to == v }.takeIf { it >= 0 } }.toSet()

    val hopPath = bfsHops("A", "E")
    val hopCost = hopPath.zipWithNext().sumOf { (u, v) -> def.edges.first { it.from == u && it.to == v }.weight ?: 0 }
    frames += GraphAlgoFrame(
        status = "Unweighted, A to E: BFS returns ${hopPath.joinToString("→")} — ${hopPath.size - 1} hops, and " +
            "no shorter walk exists. Fewest edges is the only question an unweighted graph can answer.",
        edgeMarks = edgeIndicesOf(hopPath).associateWith { EdgeMark.ACTIVE },
        nodeMarks = hopPath.associateWith { NodeMark.DONE },
        hideWeights = true,
    )

    val (costPath, cost) = cheapestPath("A", "E")
    frames += GraphAlgoFrame(
        status = "Put the weights back and that answer is wrong. ${hopPath.joinToString("→")} costs $hopCost; " +
            "${costPath.joinToString("→")} costs $cost over ${costPath.size - 1} hops. More edges, less " +
            "distance — a weighted graph makes hop count and cost different questions, which is exactly the gap " +
            "Dijkstra fills and BFS cannot.",
        edgeMarks = edgeIndicesOf(costPath).associateWith { EdgeMark.ACCEPTED } +
            edgeIndicesOf(hopPath).filterNot { it in edgeIndicesOf(costPath) }.associateWith { EdgeMark.REJECTED },
        nodeMarks = costPath.associateWith { NodeMark.UPDATED },
    )

    // Returns what Kahn's algorithm managed to place; a short list is itself the cycle evidence.
    fun topoOrder(skip: Set<Int>): List<String> {
        val indeg = def.ids.associateWith { 0 }.toMutableMap()
        def.edges.forEachIndexed { i, e -> if (i !in skip) indeg[e.to] = indeg.getValue(e.to) + 1 }
        val ready = ArrayDeque(def.ids.filter { indeg.getValue(it) == 0 }.sorted())
        val order = mutableListOf<String>()
        while (ready.isNotEmpty()) {
            val cur = ready.removeFirst()
            order += cur
            def.edges.forEachIndexed { i, e ->
                if (i !in skip && e.from == cur) {
                    indeg[e.to] = indeg.getValue(e.to) - 1
                    if (indeg.getValue(e.to) == 0) ready += e.to
                }
            }
        }
        return order
    }

    val partial = topoOrder(emptySet())
    val stuck = def.ids - partial.toSet()
    val cycle = listOf("D", "E", "F", "D")
    frames += GraphAlgoFrame(
        status = "Cyclic: D→E→F→D closes a loop. Kahn's algorithm places " +
            "${if (partial.isEmpty()) "nothing" else partial.joinToString(", ")} and then stalls — " +
            "${stuck.sorted().joinToString(", ")} never reach in-degree zero because each waits on another " +
            "member of the cycle. There is no valid order, so any \"process dependencies first\" algorithm is " +
            "undefined here.",
        edgeMarks = edgeIndicesOf(cycle).associateWith { EdgeMark.REJECTED },
        nodeMarks = cycle.toSet().associateWith { NodeMark.ACTIVE },
        hideWeights = true,
    )

    val acyclic = topoOrder(setOf(backEdgeIndex))
    frames += GraphAlgoFrame(
        status = "Drop the single edge F→D and it is a DAG. Now every vertex places — " +
            "${acyclic.joinToString(" → ")} — and with a topological order come longest-path in linear time, " +
            "dynamic programming over vertices, and build/scheduling order. One edge is the whole difference.",
        hiddenEdges = setOf(backEdgeIndex),
        nodeMarks = acyclic.associateWith { NodeMark.DONE },
        badges = acyclic.withIndex().associate { (i, id) -> id to "#${i + 1}" },
        hideWeights = true,
    )

    val v = def.ids.size
    val e = def.edges.size
    val maxEdges = v * (v - 1)
    frames += GraphAlgoFrame(
        status = "Last variant, and it is about storage rather than meaning: $e edges out of a possible " +
            "$maxEdges is sparse. An adjacency matrix costs ${v * v} cells whatever you do; adjacency lists " +
            "cost $e entries. Matrices win on \"is A→B an edge\" in O(1); lists win on iterating neighbours " +
            "and on memory, which is why almost every real graph library defaults to lists.",
        undirected = false,
        hideWeights = true,
        groups = def.ids.associateWith { 0 },
    )
    return frames
}

private fun neighboursOf(def: GraphDef, id: String, reversed: Boolean = false): List<String> =
    def.edges.mapNotNull { e ->
        val from = if (reversed) e.to else e.from
        val to = if (reversed) e.from else e.to
        when {
            from == id -> to
            !e.directed && to == id -> from
            else -> null
        }
    }.sorted()

private fun dist(value: Int): String = if (value >= GRAPH_INF) "∞" else value.toString()

// ── Bellman–Ford ─────────────────────────────────────────────────────────────

// ── Floyd–Warshall ───────────────────────────────────────────────────────────

private fun floydWarshallFrames(): List<GraphAlgoFrame> {
    val def = allPairsGraph
    val n = def.ids.size
    val d = MutableList(n) { i -> MutableList(n) { j -> if (i == j) 0 else GRAPH_INF } }
    def.edges.forEach { edge ->
        val i = def.ids.indexOf(edge.from)
        val j = def.ids.indexOf(edge.to)
        d[i][j] = minOf(d[i][j], edge.weight ?: 0)
    }
    fun snapshot() = d.map { it.toList() }

    val frames = mutableListOf<GraphAlgoFrame>()
    frames += GraphAlgoFrame(
        status = "The matrix starts as the edge list itself: dist[i][j] is the direct edge, 0 on the diagonal, " +
            "∞ where no edge exists.",
        matrix = snapshot(),
    )

    for (k in 0 until n) {
        frames += GraphAlgoFrame(
            status = "Round ${k + 1}: allow ${def.ids[k]} as an intermediate node. Every pair now asks " +
                "\"is going through ${def.ids[k]} cheaper?\"",
            matrix = snapshot(),
            matrixVia = k,
            nodeMarks = mapOf(def.ids[k] to NodeMark.ACTIVE),
        )
        for (i in 0 until n) {
            for (j in 0 until n) {
                if (i == j || i == k || j == k) continue
                val through = d[i][k] + d[k][j]
                if (d[i][k] < GRAPH_INF && d[k][j] < GRAPH_INF && through < d[i][j]) {
                    val previous = d[i][j]
                    d[i][j] = through
                    frames += GraphAlgoFrame(
                        status = "${def.ids[i]}→${def.ids[j]} via ${def.ids[k]} costs ${d[i][k]} + ${d[k][j]} = " +
                            "$through, better than ${dist(previous)}.",
                        matrix = snapshot(),
                        matrixFocus = i to j,
                        matrixVia = k,
                        nodeMarks = mapOf(
                            def.ids[k] to NodeMark.ACTIVE,
                            def.ids[i] to NodeMark.FRONTIER,
                            def.ids[j] to NodeMark.UPDATED,
                        ),
                    )
                }
            }
        }
    }

    frames += GraphAlgoFrame(
        status = "After all $n rounds every entry is a true shortest path — ${n * n} answers from three nested " +
            "loops, no per-source reruns.",
        matrix = snapshot(),
        nodeMarks = def.ids.associateWith { NodeMark.DONE },
    )
    return frames
}

// ── Kruskal ──────────────────────────────────────────────────────────────────

/** Component colours only make sense once a component has more than one node. */
private fun groupsFrom(def: GraphDef, root: (String) -> String): Map<String, Int> {
    val sizes = def.ids.groupingBy { root(it) }.eachCount()
    val order = def.ids.map(root).distinct()
    return def.ids.filter { sizes.getValue(root(it)) > 1 }
        .associateWith { order.indexOf(root(it)) }
}

// The tree edges already chosen, as an adjacency walk: names the route that makes a skipped edge a
// cycle ("A and B are already joined through D and E").
private fun treePath(def: GraphDef, marks: Map<Int, EdgeMark>, from: String, to: String): List<String> {
    val prev = mutableMapOf(from to from)
    val queue = ArrayDeque(listOf(from))
    while (queue.isNotEmpty()) {
        val cur = queue.removeFirst()
        def.edges.forEachIndexed { i, e ->
            if (marks[i] != EdgeMark.ACCEPTED) return@forEachIndexed
            val next = when (cur) { e.from -> e.to; e.to -> e.from; else -> null } ?: return@forEachIndexed
            if (next !in prev) { prev[next] = cur; queue += next }
        }
    }
    if (to !in prev) return emptyList()
    val path = mutableListOf(to)
    while (path.last() != from) path += prev.getValue(path.last())
    return path.reversed()
}

private fun joinNames(names: List<String>) = when (names.size) {
    0 -> ""
    1 -> names[0]
    else -> names.dropLast(1).joinToString(", ") + " and " + names.last()
}

private fun kruskalFrames(): List<GraphAlgoFrame> {
    val def = mstGraph
    val parent = def.ids.associateWith { it }.toMutableMap()
    fun find(id: String): String {
        var node = id
        while (parent.getValue(node) != node) node = parent.getValue(node)
        return node
    }

    val frames = mutableListOf<GraphAlgoFrame>()
    val marks = mutableMapOf<Int, EdgeMark>()
    var total = 0
    var accepted = 0
    val needed = def.ids.size - 1
    // The last skip, so the next take can say why an equal-weight edge before it was passed over.
    var lastSkip: GEdge? = null

    val sorted = def.edges.withIndex().sortedBy { it.value.weight ?: 0 }
    frames += GraphAlgoFrame(
        status = "Sort every edge by weight: ${sorted.joinToString(", ") { "${it.value.from}${it.value.to}(${it.value.weight})" }}. " +
            "Kruskal then walks that list once.",
        headline = listOf(GraphSpan("Sort the "), GraphSpan("${def.edges.size} edges", GraphTone.ACTIVE), GraphSpan(" by weight.")),
        body = "Kruskal walks this list once, lightest first, and keeps an edge only if it joins two different " +
            "components. The node colours are those components — the union-find sets.",
    )

    for ((index, edge) in sorted) {
        val rootFrom = find(edge.from)
        val rootTo = find(edge.to)
        val name = "${edge.from}–${edge.to}"
        if (rootFrom == rootTo) {
            val via = treePath(def, marks, edge.from, edge.to).drop(1).dropLast(1)
            marks[index] = EdgeMark.REJECTED
            frames += GraphAlgoFrame(
                status = "$name (w = ${edge.weight}) is skipped: both ends already sit in the " +
                    "same component, so this edge would close a cycle.",
                edgeMarks = marks.toMap(),
                groups = groupsFrom(def) { find(it) },
                nodeMarks = mapOf(edge.from to NodeMark.ACTIVE, edge.to to NodeMark.ACTIVE),
                focusEdge = index,
                headline = listOf(
                    GraphSpan("Skip "), GraphSpan(name, GraphTone.REJECTED),
                    GraphSpan(" (${edge.weight}). ${edge.from} and ${edge.to} are in the same set, so it would close a cycle."),
                ),
                body = "${edge.from} and ${edge.to} are already joined" +
                    (if (via.isEmpty()) "" else " through ${joinNames(via)}") +
                    ". Both finds return the same root, and that one comparison is the whole cycle test.",
            )
            lastSkip = edge
        } else {
            parent[rootFrom] = rootTo
            marks[index] = EdgeMark.ACCEPTED
            total += edge.weight ?: 0
            accepted++
            val skipped = lastSkip
            val tally = "Tree weight is now $total, with $accepted of $needed edges."
            frames += GraphAlgoFrame(
                status = "Take $name (w = ${edge.weight}): the ends were in different components, " +
                    "so union them. Tree weight $total, $accepted of $needed edges.",
                edgeMarks = marks.toMap(),
                groups = groupsFrom(def) { find(it) },
                nodeMarks = mapOf(edge.from to NodeMark.ACTIVE, edge.to to NodeMark.ACTIVE),
                focusEdge = index,
                headline = listOf(
                    GraphSpan("Take "), GraphSpan(name, GraphTone.ACTIVE),
                    GraphSpan(" (${edge.weight}). ${edge.from} and ${edge.to} are in different sets, so union them."),
                ),
                body = if (skipped != null && skipped.weight == edge.weight) {
                    "${skipped.from}–${skipped.to} had the same weight but was already connected, so it was skipped. $tally"
                } else {
                    tally
                },
            )
            lastSkip = null
        }
        if (accepted == needed) break
    }

    frames += GraphAlgoFrame(
        status = "$needed edges accepted and every node is in one component: minimum spanning tree, " +
            "total weight $total.",
        edgeMarks = marks.toMap(),
        nodeMarks = def.ids.associateWith { NodeMark.DONE },
        headline = listOf(GraphSpan("Minimum spanning tree", GraphTone.DONE), GraphSpan(": $needed edges, total weight $total.")),
        body = "Every node is in one set, so any further edge would close a cycle and the walk stops early. " +
            "Sorting dominates: O(E log E).",
    )
    return frames
}

// ── Prim ─────────────────────────────────────────────────────────────────────

private fun primFrames(): List<GraphAlgoFrame> {
    val def = mstGraph
    val start = "A"
    val inTree = mutableSetOf(start)
    val marks = mutableMapOf<Int, EdgeMark>()
    val frames = mutableListOf<GraphAlgoFrame>()
    var total = 0

    frames += GraphAlgoFrame(
        status = "Prim grows one tree instead of collecting edges globally. Seed it with $start.",
        nodeMarks = mapOf(start to NodeMark.DONE),
        headline = listOf(GraphSpan("Seed the tree with "), GraphSpan(start, GraphTone.ACTIVE), GraphSpan(".")),
        body = "Prim never sorts the edge list. It grows one tree, and every step takes the cheapest edge with " +
            "exactly one end inside it.",
    )

    while (inTree.size < def.ids.size) {
        val crossing = def.edges.withIndex().filter { (_, e) ->
            (e.from in inTree) != (e.to in inTree)
        }
        val candidateMarks = marks.toMutableMap()
        crossing.forEach { (index, _) -> candidateMarks[index] = EdgeMark.ACTIVE }
        frames += GraphAlgoFrame(
            status = "Edges crossing the cut: ${crossing.joinToString(", ") { "${it.value.from}–${it.value.to}(${it.value.weight})" }}.",
            edgeMarks = candidateMarks,
            nodeMarks = inTree.associateWith { NodeMark.DONE } +
                crossing.flatMap { listOf(it.value.from, it.value.to) }.filter { it !in inTree }
                    .associateWith { NodeMark.FRONTIER },
            headline = listOf(
                GraphSpan("The cut has "),
                GraphSpan("${crossing.size} crossing ${if (crossing.size == 1) "edge" else "edges"}", GraphTone.ACTIVE),
                GraphSpan("."),
            ),
            body = "Crossing: ${crossing.joinToString(", ") { "${it.value.from}–${it.value.to} (${it.value.weight})" }}. " +
                "Each has one end in the tree {${inTree.sorted().joinToString(", ")}} and one outside it.",
        )

        val (index, edge) = crossing.minByOrNull { it.value.weight ?: 0 } ?: break
        val added = if (edge.from in inTree) edge.to else edge.from
        inTree += added
        marks[index] = EdgeMark.ACCEPTED
        total += edge.weight ?: 0
        frames += GraphAlgoFrame(
            status = "Cheapest crossing edge is ${edge.from}–${edge.to} (w = ${edge.weight}) — pull $added into the " +
                "tree. Weight so far $total.",
            edgeMarks = marks.toMap(),
            nodeMarks = inTree.associateWith { NodeMark.DONE } + mapOf(added to NodeMark.UPDATED),
            focusEdge = index,
            headline = listOf(
                GraphSpan("Take "), GraphSpan("${edge.from}–${edge.to}", GraphTone.ACTIVE),
                GraphSpan(" (${edge.weight}), the cheapest edge leaving the tree."),
            ),
            body = "$added joins the tree. Weight so far $total, with ${inTree.size} of ${def.ids.size} nodes inside.",
        )
    }

    frames += GraphAlgoFrame(
        status = "All ${def.ids.size} nodes absorbed, total weight $total — the same tree Kruskal builds, reached by " +
            "growing a cut instead of sorting edges.",
        edgeMarks = marks.toMap(),
        nodeMarks = def.ids.associateWith { NodeMark.DONE },
        headline = listOf(GraphSpan("Minimum spanning tree", GraphTone.DONE), GraphSpan(": all ${def.ids.size} nodes, total weight $total.")),
        body = "The same tree Kruskal builds, reached by growing a cut instead of sorting edges. With a heap of " +
            "crossing edges it runs in O(E log V).",
    )
    return frames
}

// ── Tarjan ───────────────────────────────────────────────────────────────────

// ── Kosaraju ─────────────────────────────────────────────────────────────────

// ── Config ───────────────────────────────────────────────────────────────────

private val shortestPathLegend = listOf(
    NodeMarkColors.getValue(NodeMark.ACTIVE) to "Relaxing from",
    NodeMarkColors.getValue(NodeMark.UPDATED) to "Improved",
    NodeMarkColors.getValue(NodeMark.DONE) to "Final",
)

private val allPairsLegend = listOf(
    NodeMarkColors.getValue(NodeMark.ACTIVE) to "Via node",
    NodeMarkColors.getValue(NodeMark.FRONTIER) to "Row",
    NodeMarkColors.getValue(NodeMark.UPDATED) to "Improved",
)

private val kruskalLegend = listOf(
    EdgeMarkColors.getValue(EdgeMark.ACTIVE) to "Current",
    EdgeMarkColors.getValue(EdgeMark.ACCEPTED) to "In tree",
    EdgeMarkColors.getValue(EdgeMark.REJECTED) to "Skipped: cycle",
)

private val mstLegend = kruskalLegend

// Prim never rejects an edge — a crossing edge that loses simply stays a candidate.
private val primLegend = listOf(
    EdgeMarkColors.getValue(EdgeMark.ACTIVE) to "Crossing the cut",
    EdgeMarkColors.getValue(EdgeMark.ACCEPTED) to "In tree",
)

private val sccLegend = listOf(
    NodeMarkColors.getValue(NodeMark.ACTIVE) to "Current",
    NodeMarkColors.getValue(NodeMark.FRONTIER) to "On stack",
    GroupColors[0] to "Component",
)

// ── Topological sort / max flow / cut vertices ───────────────────────────────

// A DAG with two independent roots, so the ready queue holds more than one node at a time and the
// order is visibly non-unique.
private val dagGraph = GraphDef(
    nodes = listOf(
        GNode("A", 0.08f, 0.20f),
        GNode("B", 0.08f, 0.80f),
        GNode("C", 0.38f, 0.50f),
        GNode("D", 0.68f, 0.18f),
        GNode("E", 0.68f, 0.82f),
        GNode("F", 0.94f, 0.50f),
    ),
    edges = listOf(
        GEdge("A", "C", directed = true),
        GEdge("B", "C", directed = true),
        GEdge("C", "D", directed = true),
        GEdge("C", "E", directed = true),
        GEdge("D", "F", directed = true),
        GEdge("E", "F", directed = true),
    ),
)

// ── Interview-prep pattern guides ────────────────────────────────────────────

// Weighted DAG: the same shape as dagGraph, but the weights are what the DP is about.
private val dagDpGraph = GraphDef(
    nodes = listOf(
        GNode("A", 0.08f, 0.20f),
        GNode("B", 0.08f, 0.80f),
        GNode("C", 0.38f, 0.50f),
        GNode("D", 0.68f, 0.18f),
        GNode("E", 0.68f, 0.82f),
        GNode("F", 0.94f, 0.50f),
    ),
    edges = listOf(
        GEdge("A", "C", 3, directed = true),
        GEdge("B", "C", 6, directed = true),
        GEdge("C", "D", 4, directed = true),
        GEdge("C", "E", 2, directed = true),
        GEdge("D", "F", 5, directed = true),
        GEdge("E", "F", 9, directed = true),
    ),
)

// dp over a DAG: relax in topological order and every node is final on its first pop. The same
// recurrence on a cyclic graph has no order that works, which is the pattern's precondition.
private fun dagDpFrames(): List<GraphAlgoFrame> {
    val def = dagDpGraph
    val frames = mutableListOf<GraphAlgoFrame>()
    val inDegree = def.ids.associateWith { id -> def.edges.count { it.to == id } }.toMutableMap()
    val best = def.ids.associateWith { 0 }.toMutableMap()
    val done = mutableSetOf<String>()

    fun badges() = best.mapValues { (_, v) -> v.toString() }

    frames += GraphAlgoFrame(
        status = "Longest path from any source. On a general graph this is NP-hard; on a DAG it is a scan, because " +
            "a topological order guarantees every predecessor of a node is finished before the node is read.",
        badges = badges(),
    )

    val ready = ArrayDeque(def.ids.filter { inDegree.getValue(it) == 0 })
    frames += GraphAlgoFrame(
        status = "A and B have no incoming edges, so their best-path values are already final at 0. Everything else " +
            "starts at 0 and can only grow.",
        nodeMarks = ready.associateWith { NodeMark.FRONTIER },
        badges = badges(),
    )

    while (ready.isNotEmpty()) {
        val u = ready.removeFirst()
        done += u
        val outgoing = def.edges.withIndex().filter { it.value.from == u }
        val improved = mutableListOf<String>()

        for ((_, edge) in outgoing) {
            val candidate = best.getValue(u) + (edge.weight ?: 0)
            if (candidate > best.getValue(edge.to)) {
                best[edge.to] = candidate
                improved += edge.to
            }
            inDegree[edge.to] = inDegree.getValue(edge.to) - 1
            if (inDegree.getValue(edge.to) == 0) ready += edge.to
        }

        frames += GraphAlgoFrame(
            status = "$u is final at ${best.getValue(u)} — no unprocessed node can still reach it. Relaxing its " +
                "edges: " + if (improved.isEmpty()) "nothing improved." else improved.joinToString(", ") { to ->
                "$to → ${best.getValue(to)}"
            } + ". Each edge is relaxed exactly once, so the whole thing is O(V + E).",
            nodeMarks = buildMap {
                putAll(done.associateWith { NodeMark.DONE })
                putAll(ready.associateWith { NodeMark.FRONTIER })
                putAll(improved.associateWith { NodeMark.UPDATED })
                put(u, NodeMark.ACTIVE)
            },
            badges = badges(),
            edgeMarks = outgoing.associate { it.index to EdgeMark.ACTIVE },
        )
    }

    val winner = best.maxByOrNull { it.value }!!
    frames += GraphAlgoFrame(
        status = "Longest path ends at ${winner.key} with weight ${winner.value}. Flip the comparison to get the " +
            "shortest path, or count instead of maximise to get path counts — the traversal never changes, only the " +
            "combine step does.",
        nodeMarks = def.ids.associateWith { NodeMark.DONE } + (winner.key to NodeMark.UPDATED),
        badges = badges(),
        edgeMarks = def.edges.indices.associateWith { EdgeMark.ACCEPTED },
    )
    return frames
}

// One node set carrying unit costs, then real weights, then a negative edge.
private val shortestPathChoiceGraph = GraphDef(
    nodes = listOf(
        GNode("S", 0.06f, 0.50f),
        GNode("A", 0.38f, 0.14f),
        GNode("B", 0.38f, 0.86f),
        GNode("C", 0.72f, 0.50f),
        GNode("T", 0.95f, 0.14f),
    ),
    edges = listOf(
        GEdge("S", "A", 1, directed = true),
        GEdge("S", "B", 4, directed = true),
        GEdge("A", "C", 6, directed = true),
        GEdge("B", "C", 1, directed = true),
        GEdge("A", "T", 9, directed = true),
        GEdge("C", "T", 1, directed = true),
    ),
)

private fun shortestPathChoiceFrames(): List<GraphAlgoFrame> {
    val def = shortestPathChoiceGraph
    val frames = mutableListOf<GraphAlgoFrame>()

    // ── Unit weights: BFS is enough ──
    val bfsDist = mutableMapOf("S" to 0)
    val queue = ArrayDeque(listOf("S"))
    frames += GraphAlgoFrame(
        status = "First question to ask: what do the edges cost? If every edge is 1, a queue is already a priority " +
            "queue — BFS settles nodes in distance order for free.",
        badges = mapOf("S" to "0"),
        hideWeights = true,
    )
    while (queue.isNotEmpty()) {
        val u = queue.removeFirst()
        val outgoing = def.edges.withIndex().filter { it.value.from == u }
        val discovered = mutableListOf<String>()
        for ((_, e) in outgoing) {
            if (e.to !in bfsDist) {
                bfsDist[e.to] = bfsDist.getValue(u) + 1
                queue += e.to
                discovered += e.to
            }
        }
        frames += GraphAlgoFrame(
            status = "BFS from $u (hop ${bfsDist.getValue(u)}): " +
                if (discovered.isEmpty()) "everything reachable is already labelled."
                else "${discovered.joinToString(", ")} reached in ${bfsDist.getValue(u) + 1} hop(s), and that is final " +
                    "— a later path can only be longer.",
            nodeMarks = bfsDist.keys.associateWith { NodeMark.FRONTIER } + (u to NodeMark.ACTIVE),
            badges = bfsDist.mapValues { it.value.toString() },
            edgeMarks = outgoing.associate { it.index to EdgeMark.ACTIVE },
            hideWeights = true,
        )
    }

    // ── Real weights: Dijkstra ──
    val dist = def.ids.associateWith { if (it == "S") 0 else GRAPH_INF }.toMutableMap()
    val settled = mutableSetOf<String>()

    fun badges() = dist.mapValues { (_, v) -> if (v >= GRAPH_INF) "∞" else v.toString() }

    frames += GraphAlgoFrame(
        status = "Now the real weights. BFS's answer (S→A→T, 2 hops) costs ${1 + 9} — while S→B→C→T takes three hops " +
            "but costs ${4 + 1 + 1}. Hop count and cost disagree, so the queue has to become a priority queue.",
        badges = badges(),
    )

    while (settled.size < def.ids.size) {
        val u = dist.filterKeys { it !in settled }.minByOrNull { it.value }?.key ?: break
        if (dist.getValue(u) >= GRAPH_INF) break
        settled += u
        val outgoing = def.edges.withIndex().filter { it.value.from == u }
        val improved = mutableListOf<String>()
        for ((_, e) in outgoing) {
            val candidate = dist.getValue(u) + (e.weight ?: 0)
            if (candidate < dist.getValue(e.to)) {
                dist[e.to] = candidate
                improved += e.to
            }
        }
        frames += GraphAlgoFrame(
            status = "Pop the cheapest unsettled node: $u at ${dist.getValue(u)}. Dijkstra declares it final here — " +
                "no unsettled node is closer, and every edge is non-negative, so no detour can undercut it. " +
                if (improved.isEmpty()) "No neighbour improved."
                else "Improved: ${improved.joinToString(", ") { "$it → ${dist.getValue(it)}" }}.",
            nodeMarks = buildMap {
                putAll(settled.associateWith { NodeMark.DONE })
                putAll(improved.associateWith { NodeMark.UPDATED })
                put(u, NodeMark.ACTIVE)
            },
            badges = badges(),
            edgeMarks = outgoing.associate { it.index to EdgeMark.ACTIVE },
        )
    }

    // ── One negative edge: the assumption breaks ──
    frames += GraphAlgoFrame(
        status = "Dijkstra settles T at ${dist.getValue("T")}. Now change A→T from 9 to −3: S→A→T would cost " +
            "${1 - 3}, but Dijkstra already finalised T and never looks again. That finality is exactly what a " +
            "negative edge invalidates — Bellman-Ford drops it, relaxing every edge V−1 times instead, at O(V·E).",
        nodeMarks = def.ids.associateWith { NodeMark.DONE } + ("T" to NodeMark.UPDATED),
        badges = badges(),
        edgeMarks = mapOf(4 to EdgeMark.REJECTED),
    )
    return frames
}

// A four-cycle (two-colourable) with a triangle welded on (not).
private val colouringGraph = GraphDef(
    nodes = listOf(
        GNode("A", 0.10f, 0.18f),
        GNode("B", 0.10f, 0.82f),
        GNode("C", 0.42f, 0.50f),
        GNode("D", 0.72f, 0.16f),
        GNode("E", 0.72f, 0.84f),
        GNode("F", 0.96f, 0.50f),
    ),
    edges = listOf(
        GEdge("A", "B"),
        GEdge("A", "C"),
        GEdge("B", "C"),
        GEdge("C", "D"),
        GEdge("C", "E"),
        GEdge("D", "F"),
        GEdge("E", "F"),
    ),
)

private fun graphColouringFrames(): List<GraphAlgoFrame> {
    val def = colouringGraph
    val frames = mutableListOf<GraphAlgoFrame>()
    val adjacency = def.ids.associateWith { id ->
        def.edges.filter { it.from == id || it.to == id }.map { if (it.from == id) it.to else it.from }
    }
    val colour = mutableMapOf<String, Int>()
    val names = listOf("c1", "c2", "c3", "c4")

    frames += GraphAlgoFrame(
        status = "Colour every node so no edge joins two of the same colour, using as few colours as possible. " +
            "Optimal colouring is NP-hard; greedy in a fixed order is the cheap answer, and its cost is bounded by " +
            "max degree + 1.",
    )

    def.ids.forEach { id ->
        val used = adjacency.getValue(id).mapNotNull { colour[it] }.toSet()
        val pick = (0..used.size).first { it !in used }
        colour[id] = pick
        frames += GraphAlgoFrame(
            status = "$id: neighbours already hold " +
                (if (used.isEmpty()) "nothing" else used.sorted().joinToString(", ") { names[it] }) +
                ", so take the smallest colour not among them — ${names[pick]}. Greedy never backtracks, which is " +
                "why the node order changes the result.",
            nodeMarks = mapOf(id to NodeMark.ACTIVE),
            groups = colour.toMap(),
            badges = colour.mapValues { names[it.value] },
        )
    }

    val used = colour.values.distinct().size
    frames += GraphAlgoFrame(
        status = "$used colours in this order. The triangle A–B–C alone forces three: three mutually adjacent nodes " +
            "cannot share any colour, so no ordering does better here.",
        groups = colour.toMap(),
        badges = colour.mapValues { names[it.value] },
        nodeMarks = def.ids.associateWith { NodeMark.DONE },
    )

    // ── Bipartite check: the same machinery asking a yes/no question ──
    val side = mutableMapOf("D" to 0)
    val queue = ArrayDeque(listOf("D"))
    val bipartitePart = setOf("C", "D", "E", "F")
    while (queue.isNotEmpty()) {
        val u = queue.removeFirst()
        adjacency.getValue(u).filter { it in bipartitePart }.forEach { v ->
            if (side[v] == null) {
                side[v] = 1 - side.getValue(u)
                queue += v
            }
        }
    }
    frames += GraphAlgoFrame(
        status = "\"Is it bipartite?\" is the two-colour case, and BFS answers it: alternate colours across every " +
            "edge and see whether anything clashes. On the C–D–F–E square it succeeds — ${side.entries.sortedBy { it.key }.joinToString(", ") { "${it.key}=${names[it.value]}" }} — so that part is bipartite.",
        groups = side.toMap(),
        badges = side.mapValues { names[it.value] },
        nodeMarks = side.keys.associateWith { NodeMark.DONE },
    )
    frames += GraphAlgoFrame(
        status = "Run the same alternation into the triangle and it fails: A and B are adjacent yet both sit one step " +
            "from C, so they demand the same colour and share an edge. An odd cycle is precisely what makes a graph " +
            "non-bipartite — the conflict edge, not a colour count, is the proof to state in an interview.",
        nodeMarks = mapOf("A" to NodeMark.ACTIVE, "B" to NodeMark.ACTIVE, "C" to NodeMark.DONE),
        edgeMarks = mapOf(0 to EdgeMark.REJECTED, 1 to EdgeMark.ACCEPTED, 2 to EdgeMark.ACCEPTED),
        badges = mapOf("A" to "c1", "B" to "c1", "C" to "c2"),
    )
    return frames
}

// ── Bayesian network ─────────────────────────────────────────────────────────
// The classic sprinkler network. Small enough that the joint can be written out in full, which is
// the point: the factorization's parameter saving is countable rather than asserted.
private val bayesNetGraph = GraphDef(
    nodes = listOf(
        GNode("Cloudy", 0.50f, 0.10f),
        GNode("Sprinkler", 0.18f, 0.45f),
        GNode("Rain", 0.80f, 0.45f),
        GNode("WetGrass", 0.50f, 0.85f),
    ),
    edges = listOf(
        GEdge("Cloudy", "Sprinkler", directed = true),
        GEdge("Cloudy", "Rain", directed = true),
        GEdge("Sprinkler", "WetGrass", directed = true),
        GEdge("Rain", "WetGrass", directed = true),
    ),
)

private fun bayesNetworkFrames(): List<GraphAlgoFrame> {
    val frames = mutableListOf<GraphAlgoFrame>()

    frames.add(
        GraphAlgoFrame(
            status = "Four binary variables. Naive Bayes would assume all of them independent given the class; a Bayesian network instead states exactly which dependencies exist, as a directed acyclic graph.",
            hideWeights = true,
        ),
    )

    frames.add(
        GraphAlgoFrame(
            status = "Each node carries P(node | its parents). Cloudy has no parents so it needs 1 number; Sprinkler and Rain need 2 each; WetGrass has two parents so it needs 4. That is 9 parameters against the 2⁴ − 1 = 15 a full joint table would need.",
            badges = mapOf("Cloudy" to "1", "Sprinkler" to "2", "Rain" to "2", "WetGrass" to "4"),
            hideWeights = true,
        ),
    )

    frames.add(
        GraphAlgoFrame(
            status = "The saving comes entirely from the missing edges. There is no arrow from Sprinkler to Rain, which asserts they are conditionally independent given Cloudy — a claim about the world that the graph makes explicit and testable.",
            nodeMarks = mapOf("Sprinkler" to NodeMark.ACTIVE, "Rain" to NodeMark.ACTIVE, "Cloudy" to NodeMark.DONE),
            edgeMarks = mapOf(0 to EdgeMark.ACCEPTED, 1 to EdgeMark.ACCEPTED),
            hideWeights = true,
        ),
    )

    frames.add(
        GraphAlgoFrame(
            status = "Observe Cloudy and the path between Sprinkler and Rain is blocked — learning it is sunny tells you about the sprinkler, but once you already know the weather, the sprinkler tells you nothing more about rain. This is d-separation, and it is what makes inference tractable.",
            nodeMarks = mapOf("Cloudy" to NodeMark.DONE),
            groups = mapOf("Sprinkler" to 0, "Rain" to 1),
            hideWeights = true,
        ),
    )

    frames.add(
        GraphAlgoFrame(
            status = "Now the collider. Sprinkler and Rain are marginally independent — neither causes the other. But observe WetGrass and they become dependent: the grass is wet, so if the sprinkler was off, rain becomes far more likely.",
            nodeMarks = mapOf("WetGrass" to NodeMark.DONE),
            edgeMarks = mapOf(2 to EdgeMark.ACTIVE, 3 to EdgeMark.ACTIVE),
            groups = mapOf("Sprinkler" to 0, "Rain" to 0),
            hideWeights = true,
        ),
    )

    frames.add(
        GraphAlgoFrame(
            status = "That is explaining away, and it is the one pattern where conditioning creates dependence rather than removing it. It is also why you cannot read independence off the arrows alone — the direction of the arrows into a node matters.",
            nodeMarks = mapOf("WetGrass" to NodeMark.DONE, "Sprinkler" to NodeMark.ACTIVE),
            groups = mapOf("Rain" to 1),
            hideWeights = true,
        ),
    )

    frames.add(
        GraphAlgoFrame(
            status = "The joint factorizes along the arrows: P(C,S,R,W) = P(C)·P(S|C)·P(R|C)·P(W|S,R). Exact inference by variable elimination is efficient on a graph this sparse; on densely connected graphs it becomes intractable, which is where MCMC comes in.",
            nodeMarks = GraphDefAllDone(bayesNetGraph),
            hideWeights = true,
        ),
    )
    return frames
}

private fun GraphDefAllDone(def: GraphDef): Map<String, NodeMark> =
    def.ids.associateWith { NodeMark.DONE }

private fun undirectedAdjacency(def: GraphDef): Map<String, List<String>> =
    def.ids.associateWith { id ->
        def.edges.mapNotNull { e ->
            when (id) {
                e.from -> e.to
                e.to -> e.from
                else -> null
            }
        }.sorted()
    }

// ── Interview-prep pattern graphs ────────────────────────────────────────────

// Course numbers rather than letters, because that is the wording the question arrives in. The last
// edge closes a cycle and stays hidden until the second act.
private val courseGraph = GraphDef(
    nodes = listOf(
        GNode("101", 0.08f, 0.50f),
        GNode("201", 0.34f, 0.18f),
        GNode("210", 0.34f, 0.82f),
        GNode("301", 0.62f, 0.50f),
        GNode("330", 0.90f, 0.18f),
        GNode("401", 0.90f, 0.82f),
    ),
    edges = listOf(
        GEdge("101", "201", directed = true),
        GEdge("101", "210", directed = true),
        GEdge("201", "301", directed = true),
        GEdge("210", "301", directed = true),
        GEdge("301", "330", directed = true),
        GEdge("301", "401", directed = true),
        GEdge("401", "201", directed = true),
    ),
)

// Kahn's algorithm run twice on the same node set: once on the DAG, once after a back edge turns the
// curriculum into an impossible one. The emitted count is the only cycle check either run needs.
private fun courseScheduleFrames(): List<GraphAlgoFrame> {
    val def = courseGraph
    val cycleEdge = def.edges.lastIndex
    val frames = mutableListOf<GraphAlgoFrame>()

    fun run(active: List<Int>, hidden: Set<Int>, opening: String): List<String> {
        val edges = active.map { it to def.edges[it] }
        val inDeg = def.ids.associateWith { id -> edges.count { (_, e) -> e.to == id } }.toMutableMap()
        val done = mutableSetOf<String>()
        val emitted = mutableListOf<String>()

        fun badges() = inDeg.mapValues { (id, d) -> if (id in done) "✓" else d.toString() }

        frames += GraphAlgoFrame(status = opening, badges = badges(), hiddenEdges = hidden)

        val ready = ArrayDeque(def.ids.filter { inDeg.getValue(it) == 0 })
        frames += GraphAlgoFrame(
            status = "Ready now: ${ready.joinToString(", ").ifEmpty { "nothing — every course is waiting on another" }}. " +
                "In-degree is the count of prerequisites still unmet.",
            nodeMarks = ready.associateWith { NodeMark.FRONTIER },
            badges = badges(),
            hiddenEdges = hidden,
        )

        while (ready.isNotEmpty()) {
            val u = ready.removeFirst()
            emitted += u
            done += u
            val outgoing = edges.filter { (_, e) -> e.from == u }

            frames += GraphAlgoFrame(
                status = "Take $u (${emitted.joinToString(" → ")}). It unlocks ${outgoing.size} course(s).",
                nodeMarks = buildMap {
                    putAll(done.associateWith { NodeMark.DONE })
                    putAll(ready.associateWith { NodeMark.FRONTIER })
                    put(u, NodeMark.ACTIVE)
                },
                badges = badges(),
                edgeMarks = outgoing.associate { it.first to EdgeMark.ACTIVE },
                hiddenEdges = hidden,
            )

            val freed = mutableListOf<String>()
            for ((_, edge) in outgoing) {
                val left = inDeg.getValue(edge.to) - 1
                inDeg[edge.to] = left
                if (left == 0) {
                    ready += edge.to
                    freed += edge.to
                }
            }
            if (freed.isNotEmpty()) {
                frames += GraphAlgoFrame(
                    status = "${freed.joinToString(" and ")} drop${if (freed.size == 1) "s" else ""} to in-degree 0 — " +
                        "every prerequisite met, so ${if (freed.size == 1) "it joins" else "they join"} the queue.",
                    nodeMarks = buildMap {
                        putAll(done.associateWith { NodeMark.DONE })
                        putAll(ready.associateWith { NodeMark.FRONTIER })
                        putAll(freed.associateWith { NodeMark.UPDATED })
                    },
                    badges = badges(),
                    hiddenEdges = hidden,
                )
            }
        }
        return emitted
    }

    val order = run(
        active = def.edges.indices.filter { it != cycleEdge },
        hidden = setOf(cycleEdge),
        opening = "Six courses, each arrow meaning \"must come first\". The question is whether a legal order exists " +
            "at all, and Kahn's algorithm answers both halves at once.",
    )
    frames += GraphAlgoFrame(
        status = "${order.size} of ${def.ids.size} courses emitted, so the order ${order.joinToString(" → ")} is valid. " +
            "It is not the only one — 201 and 210 were ready simultaneously.",
        nodeMarks = def.ids.associateWith { NodeMark.DONE },
        badges = def.ids.associateWith { "✓" },
        hiddenEdges = setOf(cycleEdge),
    )

    val cyclic = run(
        active = def.edges.indices.toList(),
        hidden = emptySet(),
        opening = "Now add one prerequisite: 401 → 201, so 201 needs a course that needs 201. Nothing about the " +
            "algorithm changes — only the in-degrees do.",
    )
    frames += GraphAlgoFrame(
        status = "The queue drained after ${cyclic.size} of ${def.ids.size} courses. The ${def.ids.size - cyclic.size} " +
            "left all still have an unmet prerequisite, which can only happen inside a cycle — that count is the whole " +
            "cycle check.",
        nodeMarks = def.ids.associateWith { if (it in cyclic) NodeMark.DONE else NodeMark.ACTIVE },
        badges = def.ids.associateWith { if (it in cyclic) "✓" else "stuck" },
        edgeMarks = mapOf(cycleEdge to EdgeMark.REJECTED),
    )
    return frames
}

private val unionFindGraph = GraphDef(
    nodes = listOf(
        GNode("A", 0.10f, 0.22f),
        GNode("B", 0.10f, 0.78f),
        GNode("C", 0.38f, 0.50f),
        GNode("D", 0.66f, 0.18f),
        GNode("E", 0.66f, 0.82f),
        GNode("F", 0.92f, 0.50f),
    ),
    edges = listOf(
        GEdge("A", "B"),
        GEdge("C", "D"),
        GEdge("B", "C"),
        GEdge("A", "D"),
        GEdge("E", "F"),
        GEdge("D", "E"),
    ),
)

// Edges arriving one at a time — the case where re-running a traversal per edge would be O(E²) and
// where the second find of a union doubles as the cycle test.
private fun unionFindFrames(): List<GraphAlgoFrame> {
    val def = unionFindGraph
    val frames = mutableListOf<GraphAlgoFrame>()
    val parent = def.ids.associateWith { it }.toMutableMap()
    var components = def.ids.size

    fun find(x: String): String {
        var node = x
        while (parent.getValue(node) != node) {
            parent[node] = parent.getValue(parent.getValue(node))   // path compression
            node = parent.getValue(node)
        }
        return node
    }

    // Group index per node, ordered by first appearance so a colour never jumps between frames.
    fun groups(): Map<String, Int> {
        val order = LinkedHashMap<String, Int>()
        return def.ids.associateWith { id ->
            val root = find(id)
            order.getOrPut(root) { order.size % GroupColors.size }
        }
    }

    fun badges() = def.ids.associateWith { find(it) }

    frames += GraphAlgoFrame(
        status = "Six nodes, no edges yet: ${def.ids.size} components, each its own root. The edges below arrive one " +
            "at a time, which is what rules out re-running BFS after every one.",
        badges = badges(),
        groups = groups(),
        hiddenEdges = def.edges.indices.toSet(),
        undirected = true,
    )

    def.edges.forEachIndexed { index, edge ->
        val ra = find(edge.from)
        val rb = find(edge.to)
        val merged = ra != rb
        if (merged) {
            parent[rb] = ra
            components--
        }
        frames += GraphAlgoFrame(
            status = if (merged) "Edge ${edge.from}–${edge.to}: roots $ra and $rb differ, so attach $rb under $ra. " +
                "Components ${components + 1} → $components."
            else "Edge ${edge.from}–${edge.to}: both already find their way to $ra. Merging would change nothing — " +
                "in an undirected graph that is exactly a cycle.",
            nodeMarks = mapOf(edge.from to NodeMark.ACTIVE, edge.to to NodeMark.ACTIVE),
            badges = badges(),
            groups = groups(),
            edgeMarks = mapOf(index to if (merged) EdgeMark.ACCEPTED else EdgeMark.REJECTED),
            hiddenEdges = (index + 1 until def.edges.size).toSet(),
            undirected = true,
        )
    }

    frames += GraphAlgoFrame(
        status = "$components component(s) left after ${def.edges.size} edges, and the one rejected edge is the " +
            "graph's cycle. Every find ran in near-constant time because path compression flattened the trees as it " +
            "went — α(n), under 5 for any n you will meet.",
        badges = badges(),
        groups = groups(),
        undirected = true,
    )
    return frames
}

// ── GCN / GAT: two triangles bridged by one edge ────────────────────────────
// Node ids match `SmallGraph`'s indices (0-5) so `GcnLab`/`GatLab`'s computed values key straight
// onto badges without a translation table. Bridge nodes 2 and 3 (degree 4) sit in the middle;
// 0/1/4/5 (degree 3) are each triangle's other two corners.
private val smallBridgeGraph = GraphDef(
    nodes = listOf(
        GNode("0", 0.15f, 0.22f),
        GNode("1", 0.15f, 0.78f),
        GNode("2", 0.38f, 0.50f),
        GNode("3", 0.62f, 0.50f),
        GNode("4", 0.85f, 0.22f),
        GNode("5", 0.85f, 0.78f),
    ),
    edges = listOf(
        GEdge("0", "1"),
        GEdge("1", "2"),
        GEdge("0", "2"),
        GEdge("3", "4"),
        GEdge("4", "5"),
        GEdge("3", "5"),
        GEdge("2", "3"),
    ),
)

private fun gcnFrames(): List<GraphAlgoFrame> {
    val trace = GcnLab.layers(GcnLab.initialFeatures(), depth = 100)
    val checkpoints = listOf(0, 1, 2, 5, 20, 100)
    return checkpoints.map { depth ->
        val features = trace[depth]
        val badges = (0 until SmallGraph.NODES).associate { i -> i.toString() to "%.2f".format(features[i][0]) }
        val groups = (0 until SmallGraph.NODES).associate { i -> i.toString() to if (i < 3) 0 else 1 }
        val separation = GcnLab.separation(features)
        val status = when (depth) {
            0 -> "Two triangles, one bridge edge (2–3). Badges are each node's first feature value; colour is the " +
                "true triangle. Cross-triangle distance is %.2fx the within-triangle distance.".format(separation)
            1 -> "One layer of neighbor-averaging already blurs the split: the ratio drops to %.2f.".format(separation)
            100 -> "By depth 100 the ratio has converged to %.3f -- exactly two-thirds, not one. Nodes 2 and 3 (the " +
                "bridge, degree 4 on both sides) are now nearly identical despite sitting in different triangles."
                .format(separation)
            else -> "Depth $depth: ratio now %.2f. The bridge's weak connectivity slows this convergence down.".format(separation)
        }
        GraphAlgoFrame(status = status, badges = badges, groups = groups, undirected = true, hideWeights = true)
    }
}

private fun gatFrames(): List<GraphAlgoFrame> {
    // Node 2's neighbors are 0, 1 and 3 -- edge indices 2, 1 and 6 in `smallBridgeGraph.edges`.
    val scoredEdges = setOf(1, 2, 6)
    fun badgesFor(weights: Map<Int, Double>) = weights.mapKeys { it.key.toString() }.mapValues { "%.3f".format(it.value) }

    return listOf(
        GraphAlgoFrame(
            status = "Node 2's neighborhood: 0, 1 and 3. GCN's weight is 1/√(deg·deg) -- fixed by the graph alone, " +
                "computed once, and blind to whatever the features say.",
            nodeMarks = mapOf("2" to NodeMark.ACTIVE),
            badges = badgesFor(GatLab.baseGcn),
            edgeMarks = scoredEdges.associateWith { EdgeMark.ACTIVE },
            undirected = true,
            hideWeights = true,
        ),
        GraphAlgoFrame(
            status = "GAT's attention on the identical neighborhood, computed from the real feature values: nodes " +
                "0 and 1 (feature-similar to node 2) take 92% of the mass between them; node 3 (very different " +
                "features) gets 0.083.",
            nodeMarks = mapOf("2" to NodeMark.ACTIVE),
            badges = badgesFor(GatLab.baseAttention),
            edgeMarks = scoredEdges.associateWith { EdgeMark.ACTIVE },
            undirected = true,
            hideWeights = true,
        ),
        GraphAlgoFrame(
            status = "Move node 3's features to match node 2's exactly, and recompute both weightings. GAT's " +
                "weight on that edge roughly quadruples (0.083 -> 0.331); GCN's weight on the same edge does not " +
                "move at all -- it never read the features to begin with.",
            nodeMarks = mapOf("2" to NodeMark.ACTIVE, "3" to NodeMark.UPDATED),
            badges = badgesFor(GatLab.perturbedAttention),
            edgeMarks = scoredEdges.associateWith { EdgeMark.ACTIVE },
            undirected = true,
            hideWeights = true,
        ),
        GraphAlgoFrame(
            status = "GCN's weight on that same edge, recomputed after the identical perturbation: 0.250, unchanged " +
                "to the last decimal. Degree-normalization has no feature input to react to.",
            nodeMarks = mapOf("2" to NodeMark.ACTIVE, "3" to NodeMark.UPDATED),
            badges = badgesFor(GatLab.gcnWeights()),
            edgeMarks = scoredEdges.associateWith { EdgeMark.ACTIVE },
            undirected = true,
            hideWeights = true,
        ),
    )
}

// ── Story storyboards ────────────────────────────────────────────────────────

private val hamiltonStoryGraph = GraphDef(
    nodes = listOf(
        GNode("A", 0.04f, 0.5f), GNode("B", 0.35f, 0.06f), GNode("C", 0.35f, 0.94f),
        GNode("D", 0.65f, 0.06f), GNode("E", 0.65f, 0.94f), GNode("F", 0.96f, 0.5f),
    ),
    edges = listOf(
        GEdge("A", "B"), GEdge("A", "C"), GEdge("B", "C"), GEdge("B", "D"),
        GEdge("C", "E"), GEdge("D", "E"), GEdge("D", "F"), GEdge("E", "F"),
    ),
)

// Backtracking from A, neighbours in alphabetical order. A full path that cannot close and the dead
// ends after it are shown as one step each; consecutive backtracks collapse into one.
private fun hamiltonStoryFrames(cycle: Boolean): List<GraphAlgoFrame> {
    val def = hamiltonStoryGraph
    val n = def.nodes.size
    val adj = def.ids.associateWith { id ->
        def.edges.mapNotNull { if (it.from == id) it.to else if (it.to == id) it.from else null }.sorted()
    }
    val frames = mutableListOf<GraphAlgoFrame>()
    val goal = if (cycle) "Hamiltonian cycle" else "Hamiltonian path"
    val legend = listOf(GS.ACTIVE to "Extending", GS.PATH to "On path", GS.WARN to "No closing edge", GS.DONE to goal)

    fun story(
        path: List<String>,
        headline: String,
        body: String,
        chips: List<GChip>,
        done: Boolean = false,
        missing: List<Pair<String, String>> = emptyList(),
        extending: Boolean = true,
    ): GraphStory {
        val last = path.last()
        val tone = { id: String -> if (done) GS.DONE else if (id == last && extending) GS.ACTIVE else GS.PATH }
        val edges = path.zipWithNext().mapIndexed { i, (a, b) ->
            def.edgeIndex(a, b) to if (done) GS.DONE else if (i == path.size - 2 && extending) GS.ACTIVE else GS.PATH
        }.toMap().toMutableMap()
        if (done && cycle) edges[def.edgeIndex(last, path.first())] = GS.DONE
        return GraphStory(
            title = if (cycle) "HAMILTONIAN CYCLE" else "HAMILTONIAN PATH",
            note = "",
            headline = headline,
            body = body,
            nodes = path.associateWith(tone),
            badges = path.mapIndexed { i, id -> id to ("${i + 1}" to if (id == last && extending && !done) GS.ACTIVE else GS.IDLE) }.toMap(),
            edges = edges,
            missing = missing,
            rows = listOf(listOf(GCellGroup("PATH", path.map { GCell(it, tone(it)) } + List(n - path.size) { GCell("", GS.EMPTY) }, "${path.size} of $n vertices"))),
            chips = chips,
            emphasis = if (done) GS.DONE else GS.ACTIVE,
            legend = legend,
        )
    }

    val path = mutableListOf("A")
    frames += storyFrame(
        story(
            path,
            "Start at {A}.",
            if (cycle) "A Hamiltonian cycle visits every vertex once and comes back to A. The search grows a path one vertex at a time and backs up at dead ends."
            else "A Hamiltonian path visits every vertex exactly once. The search grows a path one vertex at a time and backs up at dead ends.",
            listOf(GChip("path", "1 of $n")),
        ),
    )
    var pendingBack = 0
    var backFrom = ""
    var afterNoClose = false

    fun flushBack() {
        if (pendingBack == 0) return
        val at = path.last()
        frames += storyFrame(
            story(
                path.toList(),
                "Dead end, so back up to {$at}.",
                (if (afterNoClose) "The full path could not close" else "$backFrom has no neighbour left that is off the path") +
                    ". The search undoes $pendingBack step${if (pendingBack == 1) "" else "s"} and tries $at's next neighbour.",
                listOf(GChip("backtracked", "$pendingBack")),
            ),
        )
        pendingBack = 0
        afterNoClose = false
    }

    fun dfs(): Boolean {
        if (path.size == n) {
            val last = path.last()
            if (!cycle) {
                frames += storyFrame(
                    story(
                        path.toList(),
                        "Extend to {m:$last}. All $n vertices are on the path.",
                        "That is a Hamiltonian path: ${path.joinToString("→")}. No vertex repeats.",
                        listOf(GChip("path", "$n of $n")),
                        done = true,
                    ),
                )
                return true
            }
            if ("A" in adj.getValue(last)) {
                frames += storyFrame(
                    story(
                        path.toList(),
                        "$last connects back to {m:A}, so the cycle closes.",
                        "${path.joinToString("→")}→A visits every vertex once and returns home.",
                        listOf(GChip("closing edge", "$last–A", GS.DONE)),
                        done = true,
                    ),
                )
                return true
            }
            frames.removeAt(frames.lastIndex)
            frames += storyFrame(
                story(
                    path.toList(),
                    "Extend to {$last}. All $n vertices are on the path.",
                    "There is no edge from $last back to A, so this is a path but not a cycle. Next, the search backtracks.",
                    listOf(GChip("closing edge", "$last–A missing", GS.WARN)),
                    missing = listOf(last to "A"),
                ),
            )
            afterNoClose = true
            return false
        }
        for (v in adj.getValue(path.last())) {
            if (v in path) continue
            flushBack()
            val from = path.last()
            path += v
            frames += storyFrame(
                story(
                    path.toList(),
                    "Extend to {$v}.",
                    "$v is the first neighbour of $from that is not on the path yet.",
                    listOf(GChip("path", "${path.size} of $n")),
                ),
            )
            if (dfs()) return true
            if (pendingBack == 0) backFrom = path.last()
            path.removeAt(path.lastIndex)
            pendingBack++
        }
        return false
    }
    dfs()
    return frames
}

private val eulerCircuitGraph = GraphDef(
    nodes = listOf(
        GNode("A", 0.12f, 0.08f), GNode("B", 0.12f, 0.92f), GNode("C", 0.5f, 0.5f),
        GNode("D", 0.88f, 0.08f), GNode("E", 0.88f, 0.92f),
    ),
    edges = listOf(GEdge("A", "B"), GEdge("A", "C"), GEdge("B", "C"), GEdge("C", "D"), GEdge("C", "E"), GEdge("D", "E")),
)

// The same bow tie minus A–C: A and C now have odd degree, so only a path exists.
private val eulerPathGraph = GraphDef(
    nodes = eulerCircuitGraph.nodes,
    edges = listOf(GEdge("A", "B"), GEdge("B", "C"), GEdge("C", "D"), GEdge("C", "E"), GEdge("D", "E")),
)

private fun eulerStoryFrames(circuit: Boolean): List<GraphAlgoFrame> {
    val def = if (circuit) eulerCircuitGraph else eulerPathGraph
    val degree = def.ids.associateWith { id -> def.edges.count { it.from == id || it.to == id } }
    val odd = degree.count { it.value % 2 == 1 }
    val m = def.edges.size
    val frames = mutableListOf<GraphAlgoFrame>()
    val legend = listOf(GS.ACTIVE to "Current", GS.PATH to "Splice point", GS.DONE to "Edge used")
    val badges = degree.mapValues { "${it.value}" to GS.IDLE }

    fun frame(
        headline: String,
        body: String,
        main: List<String>,
        sub: List<String>? = null,
        current: String? = null,
        currentEdge: Pair<String, String>? = null,
        splice: String? = null,
        emphasis: GS = GS.ACTIVE,
    ) {
        val used = (main.zipWithNext() + sub.orEmpty().zipWithNext()).map { (a, b) -> def.edgeIndex(a, b) }
        val edges = used.associateWith { GS.DONE }.toMutableMap()
        currentEdge?.let { (a, b) -> edges[def.edgeIndex(a, b)] = GS.ACTIVE }
        val nodes = (main + sub.orEmpty()).associateWith { GS.DONE }.toMutableMap()
        splice?.let { nodes[it] = GS.PATH }
        current?.let { nodes[it] = GS.ACTIVE }
        fun cells(list: List<String>, slots: Int) =
            list.mapIndexed { i, id -> GCell(id, if (id == current && i == list.lastIndex) GS.ACTIVE else if (id == splice) GS.PATH else GS.DONE) } +
                List(maxOf(0, slots - list.size)) { GCell("", GS.EMPTY) }
        val label = if (circuit) "CIRCUIT" else "PATH"
        frames += storyFrame(
            GraphStory(
                title = if (circuit) "EULERIAN CIRCUIT" else "EULERIAN PATH",
                note = "badges = degree",
                headline = headline,
                body = body,
                nodes = nodes,
                badges = badges,
                edges = edges,
                rows = listOf(
                    if (sub == null) listOf(GCellGroup(label, cells(main, m + 1)))
                    else listOf(GCellGroup(label, cells(main, main.size)), GCellGroup("SUB-TOUR", cells(sub, 4))),
                ),
                chips = listOf(GChip("odd degree", "$odd"), GChip("edges", "${used.size + (if (currentEdge != null) 0 else 0)} of $m")),
                emphasis = emphasis,
                legend = legend,
            ),
        )
    }

    if (circuit) {
        frame("Every vertex has an {even} degree, so a circuit exists.",
            "An Eulerian circuit uses every edge once and ends where it started. Hierholzer's algorithm walks until it is stuck, then splices in detours.",
            listOf("A"), current = "A")
        frame("Walk from A to {B}.", "Any unused edge will do. Each edge is crossed once and then it is gone.",
            listOf("A", "B"), current = "B", currentEdge = "A" to "B")
        frame("Walk from B to {C}.", "C has four edges, so the walk will come back through it.",
            listOf("A", "B", "C"), current = "C", currentEdge = "B" to "C")
        frame("C→A closes {A→B→C→A}, but C still has unused edges.",
            "With every degree even, a walk can only get stuck back where it began. C's other two edges become a sub-tour.",
            listOf("A", "B", "C", "A"), current = "A", currentEdge = "C" to "A", splice = "C")
        frame("A→B→C→A closed with edges left at C. A sub-tour starts there and reaches {D}.",
            "C→D→E→C gets spliced into the circuit at C. Every degree is even, so a circuit exists.",
            listOf("A", "B", "C", "A"), sub = listOf("C", "D"), current = "D", currentEdge = "C" to "D", splice = "C")
        frame("The sub-tour goes on to {E}.", "D's only other edge leads to E.",
            listOf("A", "B", "C", "A"), sub = listOf("C", "D", "E"), current = "E", currentEdge = "D" to "E", splice = "C")
        frame("E→C closes the sub-tour {C→D→E→C}.", "It starts and ends at C, so it can be dropped into the circuit at C without breaking it.",
            listOf("A", "B", "C", "A"), sub = listOf("C", "D", "E", "C"), current = "C", currentEdge = "E" to "C", splice = "C")
        frame("Splice it in: {m:A→B→C→D→E→C→A}.", "All $m edges are used exactly once, and the walk ends at A where it started.",
            listOf("A", "B", "C", "D", "E", "C", "A"), emphasis = GS.DONE)
    } else {
        frame("Two vertices, {A} and {C}, have odd degree, so only a path exists.",
            "An Eulerian path must start at one odd vertex and end at the other. Here it starts at A.",
            listOf("A"), current = "A")
        frame("Walk from A to {B}.", "A's only edge leads to B.", listOf("A", "B"), current = "B", currentEdge = "A" to "B")
        frame("Walk from B to {C}.", "C has three edges: one to arrive, then two more to use.", listOf("A", "B", "C"), current = "C", currentEdge = "B" to "C")
        frame("Walk from C to {D}.", "Going to D first still leaves a way back to C through E.", listOf("A", "B", "C", "D"), current = "D", currentEdge = "C" to "D")
        frame("Walk from D to {E}.", "D's other edge leads to E.", listOf("A", "B", "C", "D", "E"), current = "E", currentEdge = "D" to "E")
        frame("The path ends at {m:C}, the other odd vertex.", "A→B→C→D→E→C uses all $m edges once. It cannot return to A, because A has odd degree.",
            listOf("A", "B", "C", "D", "E", "C"), emphasis = GS.DONE)
    }
    return frames
}

private val flowStoryGraph = GraphDef(
    nodes = listOf(GNode("S", 0.05f, 0.5f), GNode("A", 0.5f, 0.06f), GNode("B", 0.5f, 0.94f), GNode("T", 0.95f, 0.5f)),
    edges = listOf(
        GEdge("S", "A", 3, directed = true), GEdge("S", "B", 2, directed = true), GEdge("A", "T", 2, directed = true),
        GEdge("B", "T", 3, directed = true), GEdge("A", "B", 1, directed = true),
    ),
)

private fun maxFlowStoryFrames(): List<GraphAlgoFrame> {
    val def = flowStoryGraph
    val flow = IntArray(def.edges.size)
    val frames = mutableListOf<GraphAlgoFrame>()
    var total = 0
    fun cap(i: Int) = def.edges[i].weight!!
    fun idx(a: String, b: String) = def.edgeIndex(a, b)

    fun frame(title: String, path: List<String>?, headline: String, body: String, chips: List<GChip>, cut: Boolean = false, emphasis: GS = GS.ACTIVE) {
        val onPath = path?.zipWithNext()?.map { (a, b) -> idx(a, b) }.orEmpty()
        val edges = def.edges.indices.associateWith { i ->
            when {
                i in onPath -> GS.ACTIVE
                cut && def.edges[i].from == "S" -> GS.CUT
                flow[i] > 0 -> GS.DONE
                else -> GS.IDLE
            }
        }
        val residual = onPath.map { i -> GCell("${def.edges[i].from}→${def.edges[i].to} ${cap(i) - flow[i]}") }
        frames += storyFrame(
            GraphStory(
                title = title,
                note = "flow / capacity",
                headline = headline,
                body = body,
                nodes = path.orEmpty().associateWith { GS.ACTIVE } + (if (cut) mapOf("S" to GS.CUT) else emptyMap()),
                edges = edges,
                edgeLabels = def.edges.indices.associateWith { "${flow[it]}/${cap(it)}" },
                boxed = def.edges.indices.toSet(),
                rows = if (path == null) emptyList() else listOf(listOf(GCellGroup(null, residual + GCell("min ${onPath.minOf { cap(it) - flow[it] }}", GS.ACTIVE)))),
                chips = chips,
                emphasis = emphasis,
                legend = listOf(GS.ACTIVE to "Augmenting path", GS.DONE to "Carrying flow", GS.CUT to "Min cut"),
            ),
        )
    }
    fun push(path: List<String>): Int {
        val ids = path.zipWithNext().map { (a, b) -> idx(a, b) }
        val amount = ids.minOf { cap(it) - flow[it] }
        ids.forEach { flow[it] += amount }
        total += amount
        return amount
    }

    frame("NETWORK", null, "Every edge starts with {flow 0}.",
        "Each label is flow / capacity. Ford-Fulkerson keeps finding a path from S to T with room left, and pushes as much as its tightest edge allows.",
        listOf(GChip("total flow", "0")))
    frame("AUGMENTING PATH 1", listOf("S", "A", "T"), "Push 2 along {S→A→T}. A→T is the bottleneck.",
        "A→T has room for only 2 of S→A's 3. Every unit that leaves S along this path reaches T.",
        listOf(GChip("bottleneck", "2"), GChip("total flow", "0 → 2")))
    push(listOf("S", "A", "T"))
    frame("AFTER PATH 1", null, "{m:A→T} is now full.", "Its 2/2 means no more flow can use it, so the next path has to go another way.",
        listOf(GChip("total flow", "$total")), emphasis = GS.DONE)
    frame("AUGMENTING PATH 2", listOf("S", "B", "T"), "Push 2 along {S→B→T}. S→B is the bottleneck.",
        "Flow is now 4. Next, S→A→B→T carries the last unit, for a max flow of 5.",
        listOf(GChip("bottleneck", "2"), GChip("total flow", "$total → ${total + 2}")))
    push(listOf("S", "B", "T"))
    frame("AFTER PATH 2", null, "{m:S→B} is full too.", "The only room left out of S is 1 unit on S→A.",
        listOf(GChip("total flow", "$total")), emphasis = GS.DONE)
    frame("AUGMENTING PATH 3", listOf("S", "A", "B", "T"), "Push 1 along {S→A→B→T}.",
        "S→A has 1 unit spare and A→B carries it across to B, which still has room to T.",
        listOf(GChip("bottleneck", "1"), GChip("total flow", "$total → ${total + 1}")))
    push(listOf("S", "A", "B", "T"))
    frame("AFTER PATH 3", null, "Every edge out of S is now {m:full}.", "S→A carries 3 of 3 and S→B 2 of 2, so no augmenting path is left.",
        listOf(GChip("total flow", "$total")), emphasis = GS.DONE)
    frame("MAX FLOW", null, "The max flow is {c:$total}.",
        "Cutting S off from the rest costs 3 + 2 = $total, the same as the flow. That match is what proves no more can fit.",
        listOf(GChip("max flow", "$total", GS.CUT), GChip("min cut", "$total", GS.CUT)), cut = true, emphasis = GS.CUT)
    return frames
}

private val cutStoryGraph = GraphDef(
    nodes = listOf(
        GNode("A", 0.1f, 0.08f), GNode("B", 0.1f, 0.92f), GNode("C", 0.4f, 0.5f),
        GNode("D", 0.6f, 0.5f), GNode("E", 0.9f, 0.08f), GNode("F", 0.9f, 0.92f),
    ),
    edges = listOf(GEdge("A", "B"), GEdge("A", "C"), GEdge("B", "C"), GEdge("C", "D"), GEdge("D", "E"), GEdge("D", "F"), GEdge("E", "F")),
)

private fun articulationStoryFrames(): List<GraphAlgoFrame> {
    val def = cutStoryGraph
    val frames = mutableListOf<GraphAlgoFrame>()
    val disc = mutableMapOf<String, Int>()
    val low = mutableMapOf<String, Int>()
    val cuts = mutableListOf<String>()
    val bridges = mutableListOf<Int>()
    val found = mutableListOf<String>()

    fun frame(current: String?, headline: String, body: String, lines: List<GLine> = emptyList(), emphasis: GS = GS.ACTIVE) {
        frames += storyFrame(
            GraphStory(
                title = "DFS · disc / low",
                note = "cut if low(child) ≥ disc",
                headline = headline,
                body = body,
                nodes = cuts.associateWith { GS.CUT } + (current?.let { mapOf(it to GS.ACTIVE) } ?: emptyMap()),
                badges = disc.keys.associateWith { "${disc[it]}/${low[it]}" to if (it == current) GS.ACTIVE else if (it in cuts) GS.CUT else GS.IDLE },
                edges = bridges.associateWith { GS.CUT },
                lines = lines,
                chips = if (found.isEmpty()) listOf(GChip("found", "none yet")) else listOf(GChip("found", found.joinToString(", "), GS.CUT)),
                emphasis = emphasis,
                legend = listOf(GS.ACTIVE to "Checking", GS.CUT to "Cut vertex or bridge"),
            ),
        )
    }
    fun visit(id: String, d: Int) { disc[id] = d; low[id] = d }

    visit("A", 1)
    frame("A", "Start the DFS at {A}: disc 1, low 1.",
        "disc is the visit order. low is the earliest disc a node's subtree can reach with one back edge.")
    visit("B", 2)
    frame("B", "Visit {B}: disc 2.", "B's only other neighbour so far is A, its parent, so low stays 2.")
    visit("C", 3)
    low["C"] = 1
    frame("C", "Visit {C}: disc 3. It also sees A.", "A is already visited and isn't C's parent, so that is a back edge: low(C) drops to 1.")
    visit("D", 4)
    frame("D", "Visit {D}: disc 4.", "The only way to D is across the edge from C.")
    visit("E", 5)
    frame("E", "Visit {E}: disc 5.", "E's subtree is about to find its own way back.")
    visit("F", 6)
    low["F"] = 4
    frame("F", "Visit {F}: disc 6. It sees D.", "D is already visited, so that is a back edge: low(F) drops to 4.")
    low["E"] = 4
    frame("E", "{E} finishes with low 4.", "Its child F reaches D, so E's subtree can climb to D but no higher.")
    cuts += "D"
    found += "D"
    frame("D", "E's subtree can't climb above {D}, so D is a cut vertex.",
        "Removing D would cut E and F off. D–E is not a bridge, because F gives E a second way to D.",
        listOf(GLine("low(E) 4 ≥ disc(D) 4", "D is a cut vertex", GS.CUT), GLine("low(E) 4 = disc(D) 4", "no bridge D–E", GS.IDLE)))
    cuts += "C"
    bridges += def.edgeIndex("C", "D")
    found += listOf("C", "C–D")
    frame("D", "{D}'s subtree can't climb above D, so C–D is a bridge.",
        "Removing C would cut A and B off from D, E and F, so C is a cut vertex too.",
        listOf(GLine("low(D) 4 > disc(C) 3", "bridge C–D", GS.CUT), GLine("low(D) 4 ≥ disc(C) 3", "C is a cut vertex", GS.CUT)))
    low["B"] = 1
    frame("B", "{B} finishes with low 1.", "C's back edge to A lifts B's whole subtree above B, so B is not a cut vertex.",
        listOf(GLine("low(C) 1 < disc(B) 2", "B is not a cut vertex", GS.IDLE)))
    frame("A", "The root {A} has one DFS child.", "A root is a cut vertex only when it has two or more children, so A is not one.")
    frame(null, "Cut vertices {c:C and D}, and one bridge, {c:C–D}.",
        "Removing either vertex, or that edge, splits the graph. Every other edge sits on a cycle.", emphasis = GS.CUT)
    return frames
}

private val dagStoryGraph = GraphDef(
    nodes = listOf(
        GNode("A", 0.1f, 0.08f), GNode("B", 0.1f, 0.92f), GNode("C", 0.4f, 0.5f),
        GNode("D", 0.7f, 0.08f), GNode("E", 0.7f, 0.92f), GNode("F", 0.95f, 0.5f),
    ),
    edges = listOf(
        GEdge("A", "C", directed = true), GEdge("B", "C", directed = true), GEdge("C", "D", directed = true),
        GEdge("C", "E", directed = true), GEdge("D", "F", directed = true), GEdge("E", "F", directed = true),
    ),
)

private fun topoStoryFrames(): List<GraphAlgoFrame> {
    val def = dagStoryGraph
    val n = def.nodes.size
    val indeg = def.ids.associateWith { id -> def.edges.count { it.to == id } }.toMutableMap()
    val queue = mutableListOf<String>()
    val output = mutableListOf<String>()
    val frames = mutableListOf<GraphAlgoFrame>()

    fun frame(popped: String?, changed: Map<String, Int>, headline: String, body: String, emphasis: GS = GS.ACTIVE) {
        val nodes = output.associateWith { GS.DONE } + queue.associateWith { GS.PATH } + (popped?.let { mapOf(it to GS.ACTIVE) } ?: emptyMap())
        val badges = def.ids.associateWith { id ->
            changed[id]?.let { before -> "$before → ${indeg[id]}" to GS.PATH } ?: ("${indeg[id]}" to GS.IDLE)
        }
        val edges = popped?.let { p -> def.edges.indices.filter { def.edges[it].from == p }.associateWith { GS.ACTIVE } }.orEmpty()
        frames += storyFrame(
            GraphStory(
                title = "KAHN'S ALGORITHM",
                note = "badges = in-degree",
                headline = headline,
                body = body,
                nodes = nodes,
                badges = badges,
                edges = edges,
                rows = listOf(
                    listOf(
                        GCellGroup("QUEUE", if (queue.isEmpty()) listOf(GCell("", GS.EMPTY)) else queue.map { GCell(it, GS.PATH) }),
                        GCellGroup("OUTPUT", output.map { GCell(it, if (it == popped) GS.ACTIVE else GS.DONE) } + List(n - output.size) { GCell("", GS.EMPTY) }),
                    ),
                ),
                chips = listOf(GChip("emitted", "${output.size} of $n")),
                emphasis = emphasis,
                legend = listOf(GS.ACTIVE to "Popped", GS.PATH to "Ready", GS.DONE to "Emitted"),
            ),
        )
    }

    frame(null, emptyMap(), "Count each vertex's {in-degree}.",
        "In-degree is the number of edges coming in. A vertex at 0 has no dependency left and can go next.")
    queue += def.ids.filter { indeg[it] == 0 }
    frame(null, emptyMap(), "{p:A} and {p:B} have in-degree 0, so they start the queue.",
        "Kahn's algorithm only ever outputs a vertex whose dependencies are all out already.")
    val lines = mapOf(
        "A" to ("Pop {A}. C loses one dependency." to "C still waits on B."),
        "B" to ("Pop {B}. C's last dependency is gone." to "C drops to in-degree 0 and joins the queue."),
        "C" to ("Pop {C}. D and E lose their last dependency." to "Both drop to in-degree 0 and join the queue. F still waits on two."),
        "D" to ("Pop {D}. F loses one dependency." to "F still waits on E."),
        "E" to ("Pop {E}. F is free." to "F drops to in-degree 0 and joins the queue."),
        "F" to ("Pop {F}, the last vertex." to "Nothing depends on F, so no in-degree changes."),
    )
    while (queue.isNotEmpty()) {
        val v = queue.removeAt(0)
        output += v
        val changed = mutableMapOf<String, Int>()
        def.edges.filter { it.from == v }.forEach { e ->
            changed[e.to] = indeg.getValue(e.to)
            indeg[e.to] = indeg.getValue(e.to) - 1
            if (indeg[e.to] == 0) queue += e.to
        }
        val (h, b) = lines.getValue(v)
        frame(v, changed, h, b)
    }
    frame(null, emptyMap(), "A valid order: {m:${output.joinToString(", ")}}.",
        "Every edge points forward in this order. If the queue ran dry before all $n were out, the graph would have a cycle.", GS.DONE)
    return frames
}

private val sccStoryGraph = GraphDef(
    nodes = listOf(
        GNode("A", 0.08f, 0.35f), GNode("B", 0.38f, 0.06f), GNode("C", 0.38f, 0.68f), GNode("D", 0.68f, 0.14f),
        GNode("E", 0.92f, 0.56f), GNode("F", 0.64f, 0.94f), GNode("G", 0.12f, 0.94f),
    ),
    edges = listOf(
        GEdge("A", "B", directed = true), GEdge("B", "C", directed = true), GEdge("C", "A", directed = true),
        GEdge("C", "D", directed = true), GEdge("D", "E", directed = true), GEdge("E", "F", directed = true),
        GEdge("F", "G", directed = true), GEdge("F", "D", directed = true),
    ),
)

private fun kosarajuStoryFrames(pass2: Boolean): List<GraphAlgoFrame> {
    val def = sccStoryGraph
    val frames = mutableListOf<GraphAlgoFrame>()
    fun e(a: String, b: String) = def.edges.indexOfFirst { it.from == a && it.to == b }

    if (!pass2) {
        val finish = mutableListOf<String>()
        fun frame(current: String?, stack: List<String>, treeEdges: List<Int>, active: Int?, headline: String, body: String, emphasis: GS = GS.ACTIVE) {
            frames += storyFrame(
                GraphStory(
                    title = "PASS 1 · DFS",
                    note = "record finish order",
                    headline = headline,
                    body = body,
                    nodes = finish.associateWith { GS.DONE } + stack.associateWith { GS.PATH } + (current?.let { mapOf(it to GS.ACTIVE) } ?: emptyMap()),
                    edges = treeEdges.associateWith { GS.PATH } + (active?.let { mapOf(it to GS.ACTIVE) } ?: emptyMap()),
                    rows = listOf(listOf(GCellGroup("FINISH ORDER", finish.map { GCell(it, GS.DONE) } + List(7 - finish.size) { GCell("", GS.EMPTY) }, "first to finish first"))),
                    chips = listOf(GChip("finished", "${finish.size} of 7")),
                    emphasis = emphasis,
                    legend = listOf(GS.ACTIVE to "Current", GS.PATH to "On stack", GS.DONE to "Finished"),
                ),
            )
        }
        frame("A", emptyList(), emptyList(), null, "Pass 1 runs a plain DFS from {A}.", "It records the order in which vertices finish, nothing else.")
        frame("B", listOf("A"), emptyList(), e("A", "B"), "Visit {B}.", "Follow A→B.")
        frame("C", listOf("A", "B"), listOf(e("A", "B")), e("B", "C"), "Visit {C}.", "Follow B→C.")
        frame("D", listOf("A", "B", "C"), listOf(e("A", "B"), e("B", "C")), e("C", "D"),
            "C→A leads back to A, already visited, so C moves on to {D}.", "An edge to a visited vertex is skipped in both passes.")
        frame("E", listOf("A", "B", "C", "D"), listOf(e("A", "B"), e("B", "C"), e("C", "D")), e("D", "E"), "Visit {E}.", "Follow D→E.")
        frame("F", listOf("A", "B", "C", "D", "E"), listOf(e("A", "B"), e("B", "C"), e("C", "D"), e("D", "E")), e("E", "F"), "Visit {F}.", "Follow E→F.")
        val tree = listOf(e("A", "B"), e("B", "C"), e("C", "D"), e("D", "E"), e("E", "F"))
        frame("G", listOf("A", "B", "C", "D", "E", "F"), tree, e("F", "G"), "Visit {G}.", "F→G first. F→D goes back to a visited vertex, so it is skipped.")
        finish += "G"
        frame(null, listOf("A", "B", "C", "D", "E", "F"), tree, null, "{m:G} has no way out, so it finishes first.", "A vertex finishes once every edge out of it is explored.", GS.DONE)
        finish += listOf("F", "E", "D")
        frame(null, listOf("A", "B", "C"), tree, null, "{m:F}, {m:E} and {m:D} finish in turn.", "Each has nothing left to explore once G is done.", GS.DONE)
        finish += listOf("C", "B", "A")
        frame(null, emptyList(), tree, null, "{m:C}, {m:B} and {m:A} finish last.", "A started the search, so it finishes after everything it reached.", GS.DONE)
        frames += storyFrame(
            GraphStory(
                title = "PASS 1 · DFS",
                note = "record finish order",
                headline = "Read latest first: {A, B, C, D, E, F, G}.",
                body = "Pass 2 starts from whichever vertex finished last. That guarantees each search stays inside one component.",
                nodes = def.ids.associateWith { GS.DONE },
                rows = listOf(listOf(GCellGroup("FINISH ORDER", finish.reversed().map { GCell(it, GS.DONE) }, "latest first"))),
                chips = listOf(GChip("finished", "7 of 7")),
                legend = listOf(GS.DONE to "Finished"),
            ),
        )
        return frames
    }

    val order = listOf("A", "B", "C", "D", "E", "F", "G")
    val found = mutableListOf<String>()
    var components = 0
    fun frame(current: String?, comp: List<String>, compEdges: List<Int>, active: Int?, headline: String, body: String, emphasis: GS = GS.ACTIVE) {
        frames += storyFrame(
            GraphStory(
                title = "PASS 2 · REVERSED",
                note = "latest first",
                headline = headline,
                body = body,
                nodes = found.associateWith { GS.DONE } + comp.associateWith { GS.PATH } + (current?.let { mapOf(it to GS.ACTIVE) } ?: emptyMap()),
                edges = compEdges.associateWith { GS.PATH } + (active?.let { mapOf(it to GS.ACTIVE) } ?: emptyMap()),
                reversed = true,
                rows = listOf(listOf(GCellGroup("PASS 1 FINISH ORDER", order.map { id ->
                    GCell(id, when (id) { current -> GS.ACTIVE; in comp -> GS.PATH; in found -> GS.DONE; else -> GS.IDLE })
                }, "latest first"))),
                chips = listOf(GChip("components", "$components found")),
                emphasis = emphasis,
                legend = listOf(GS.ACTIVE to "Current", GS.PATH to "This component", GS.DONE to "Component found"),
            ),
        )
    }
    frame(null, emptyList(), emptyList(), null, "Reverse every {edge}.",
        "A component stays a component when its edges flip, but the links between components now point backwards.")
    frame("A", emptyList(), emptyList(), null, "Start from {A}, the last to finish.", "Whatever A reaches in the reversed graph is its component.")
    frame("C", listOf("A"), emptyList(), e("C", "A"), "A reaches {C}.", "C→A flipped is A→C.")
    frame("B", listOf("A", "C"), listOf(e("C", "A")), e("B", "C"), "C reaches {B}, and B leads back to A.", "Nothing else is reachable from A, B and C in the reversed graph.")
    found += listOf("A", "B", "C")
    components = 1
    frame(null, emptyList(), emptyList(), null, "{m:A, B and C} form the first component.", "The next start is the first unvisited vertex in the order: D.", GS.DONE)
    frame("D", emptyList(), emptyList(), null, "Start again from {D}.", "C→D flipped points into C, which is already taken, so the search can't leak back.")
    frame("F", listOf("D"), emptyList(), e("F", "D"), "D reaches {F}.", "F→D flipped is D→F.")
    frame("E", listOf("D", "F"), listOf(e("F", "D")), e("E", "F"), "In the reversed graph, D reaches F and then {E}.",
        "They can't get back to A, B or C, so D, E and F form the second component. G is last, on its own.")
    found += listOf("D", "E", "F")
    components = 2
    frame(null, emptyList(), emptyList(), null, "{m:D, E and F} form the second component.", "Only G is left.", GS.DONE)
    found += "G"
    components = 3
    frame(null, emptyList(), emptyList(), null, "{m:G} is a component of its own.",
        "Three components: {A, B, C}, {D, E, F} and {G}, found by two DFS passes in O(V + E).", GS.DONE)
    return frames
}

private fun bellmanStoryFrames(): List<GraphAlgoFrame> {
    val def = negativeWeightGraph
    val dist = def.ids.associateWith { if (it == "A") 0 else GRAPH_INF }.toMutableMap()
    val frames = mutableListOf<GraphAlgoFrame>()
    val rounds = def.nodes.size - 1
    fun d(v: Int) = if (v >= GRAPH_INF) "∞" else "$v"
    var first = true
    for (round in 1..rounds) {
        def.edges.forEachIndexed { i, edge ->
            val du = dist.getValue(edge.from)
            val dv = dist.getValue(edge.to)
            val w = edge.weight!!
            val improved = du < GRAPH_INF && du + w < dv
            if (improved) dist[edge.to] = du + w
            val (headline, body) = when {
                du >= GRAPH_INF -> "Skip {${edge.from}→${edge.to}}: ${edge.from} is still ∞." to
                    "An edge can only help once its start has a distance."
                improved -> "Relax {${edge.from}→${edge.to}}. ${d(du)} + $w beats ${d(dv)}, so ${edge.to} becomes ${du + w}." to
                    if (first) "Each round relaxes every edge. Four rounds cover any shortest path in five vertices, and a fifth checks for negative cycles."
                    else "${edge.to}'s best known distance drops from ${d(dv)} to ${du + w}."
                else -> "{${edge.from}→${edge.to}} gives ${du + w}, no better than ${d(dv)}." to "${edge.to} keeps ${d(dv)}."
            }
            if (improved) first = false
            frames += storyFrame(
                GraphStory(
                    title = "ROUND $round OF $rounds",
                    note = "relax every edge",
                    headline = headline,
                    body = body,
                    nodes = mapOf(edge.from to GS.ACTIVE) + if (improved) mapOf(edge.to to GS.DONE) else emptyMap(),
                    badges = def.ids.associateWith { id -> d(dist.getValue(id)) to if (id == edge.from) GS.ACTIVE else if (improved && id == edge.to) GS.DONE else GS.IDLE },
                    edges = mapOf(i to if (improved) GS.DONE else GS.ACTIVE),
                    boxed = setOf(i),
                    rows = listOf(listOf(GCellGroup("DIST", def.ids.map { id -> GCell("$id ${d(dist.getValue(id))}", if (improved && id == edge.to) GS.DONE else GS.IDLE) }))),
                    chips = listOf(GChip("edge", "${edge.from}→${edge.to}"), GChip("w", "$w")),
                    legend = listOf(GS.ACTIVE to "Relaxing from", GS.DONE to "Improved"),
                ),
            )
        }
    }
    val negative = def.edges.any { dist.getValue(it.from) < GRAPH_INF && dist.getValue(it.from) + it.weight!! < dist.getValue(it.to) }
    frames += storyFrame(
        GraphStory(
            title = "ROUND 5 · CHECK",
            note = "relax every edge",
            headline = if (negative) "Round 5 still improves an edge, so there is a {w:negative cycle}." else "Round 5 changes nothing, so there is {m:no negative cycle}.",
            body = "Final distances from A: " + def.ids.joinToString(", ") { "$it ${d(dist.getValue(it))}" } + ".",
            nodes = def.ids.associateWith { GS.DONE },
            badges = def.ids.associateWith { d(dist.getValue(it)) to GS.DONE },
            rows = listOf(listOf(GCellGroup("DIST", def.ids.map { GCell("$it ${d(dist.getValue(it))}", GS.DONE) }))),
            chips = listOf(GChip("rounds", "$rounds + 1")),
            emphasis = GS.DONE,
            legend = listOf(GS.DONE to "Final"),
        ),
    )
    return frames
}

private fun tarjanStoryFrames(): List<GraphAlgoFrame> {
    val def = sccStoryGraph
    val frames = mutableListOf<GraphAlgoFrame>()
    val index = mutableMapOf<String, Int>()
    val low = mutableMapOf<String, Int>()
    val stack = mutableListOf<String>()
    val done = mutableListOf<String>()
    fun e(a: String, b: String) = def.edges.indexOfFirst { it.from == a && it.to == b }

    fun frame(current: String?, active: Int?, headline: String, body: String, chips: List<GChip> = emptyList(), emphasis: GS = GS.ACTIVE) {
        frames += storyFrame(
            GraphStory(
                title = "ONE DFS",
                note = "badges = index / low",
                headline = headline,
                body = body,
                nodes = done.associateWith { GS.DONE } + stack.associateWith { GS.PATH } + (current?.let { mapOf(it to GS.ACTIVE) } ?: emptyMap()),
                badges = index.keys.associateWith { "${index[it]}/${low[it]}" to if (it == current) GS.ACTIVE else GS.IDLE },
                edges = active?.let { mapOf(it to GS.ACTIVE) }.orEmpty(),
                rows = listOf(listOf(GCellGroup("STACK", if (stack.isEmpty()) listOf(GCell("", GS.EMPTY)) else stack.map { GCell(it, if (it == current) GS.ACTIVE else GS.PATH) }, "bottom → top"))),
                chips = chips.ifEmpty { listOf(GChip("components", "${if (done.isEmpty()) 0 else listOf("G", "D", "A").count { it in done }} found")) },
                emphasis = emphasis,
                legend = listOf(GS.ACTIVE to "Current", GS.PATH to "On stack", GS.DONE to "Component found"),
            ),
        )
    }
    fun visit(v: String) { index[v] = index.size; low[v] = index.getValue(v); stack += v }

    visit("A")
    frame("A", null, "Visit {A}: index 0, pushed on the stack.",
        "Tarjan finds every strongly connected component in one DFS. low is the smallest index a vertex can reach while staying on the stack.")
    visit("B"); frame("B", e("A", "B"), "Visit {B}: index 1.", "Every visit pushes the vertex on the stack.")
    visit("C"); frame("C", e("B", "C"), "Visit {C}: index 2.", "Its first edge leads back to A.")
    low["C"] = 0
    frame("C", e("C", "A"), "{C} reaches A, which is still on the stack.", "So low(C) drops to 0: C, B and A belong together.",
        listOf(GChip("low(C)", "min(2, 0) = 0")))
    visit("D"); frame("D", e("C", "D"), "Visit {D}: index 3.", "C's next edge leaves for D.")
    visit("E"); frame("E", e("D", "E"), "Visit {E}: index 4.", "Follow D→E.")
    visit("F"); frame("F", e("E", "F"), "Visit {F}: index 5.", "F has two edges out: to G, then back to D.")
    visit("G"); frame("G", e("F", "G"), "Visit {G}: index 6.", "G has no edges out.")
    stack.remove("G"); done += "G"
    frame(null, null, "{m:G} is a component on its own.", "Its low equals its index, so it pops off the stack alone.", emphasis = GS.DONE)
    low["F"] = 3
    frame("F", e("F", "D"), "{F} reaches D, which is still on the stack.",
        "So low(F) drops to 3. When D finishes with low equal to its index, D, E and F pop off as one component.",
        listOf(GChip("low(F)", "min(5, 3) = 3")))
    frame("F", null, "{F} finishes with low 3.", "3 is less than F's index, so F stays on the stack for D to collect.")
    low["E"] = 3
    frame("E", null, "{E} finishes with low 3.", "It takes F's low, since F is its child.", listOf(GChip("low(E)", "min(4, 3) = 3")))
    listOf("D", "E", "F").forEach { stack.remove(it) }
    done += listOf("D", "E", "F")
    frame(null, null, "{m:D, E and F} pop off as one component.", "D finished with low equal to its index, so it and everything above it leave the stack.", emphasis = GS.DONE)
    frame("C", null, "{C} finishes with low 0.", "0 is A's index, so C stays on the stack.")
    low["B"] = 0
    frame("B", null, "{B} finishes with low 0.", "It takes C's low.", listOf(GChip("low(B)", "min(1, 0) = 0")))
    listOf("A", "B", "C").forEach { stack.remove(it) }
    done += listOf("A", "B", "C")
    frame(null, null, "{m:A, B and C} pop off as one component.", "A's low equals its index, so the rest of the stack comes with it.", emphasis = GS.DONE)
    frame(null, null, "Three components from {m:one DFS}.",
        "{A, B, C}, {D, E, F} and {G}. Every vertex is pushed and popped exactly once, so the whole run is O(V + E).", emphasis = GS.DONE)
    return frames
}

private val graphAlgoConfigs = mapOf(
    "dag_dp_pattern" to GraphAlgoConfig(
        intro = "Longest path in a weighted DAG. Badges are the best distance found so far — relaxing nodes in " +
            "topological order means each one is finished the first time it is popped.",
        def = dagDpGraph,
        legend = listOf(
            NodeMarkColors.getValue(NodeMark.ACTIVE) to "Relaxing from",
            NodeMarkColors.getValue(NodeMark.UPDATED) to "Improved",
            NodeMarkColors.getValue(NodeMark.DONE) to "Final",
        ),
        build = ::dagDpFrames,
    ),
    "shortest_path_pattern" to GraphAlgoConfig(
        intro = "One graph, three answers: BFS when every edge costs 1, Dijkstra when they differ, and the negative " +
            "edge that breaks Dijkstra's finality assumption and hands the problem to Bellman-Ford.",
        def = shortestPathChoiceGraph,
        legend = listOf(
            NodeMarkColors.getValue(NodeMark.ACTIVE) to "Settling",
            NodeMarkColors.getValue(NodeMark.FRONTIER) to "Reached",
            NodeMarkColors.getValue(NodeMark.DONE) to "Final",
        ),
        build = ::shortestPathChoiceFrames,
    ),
    "graph_coloring_pattern" to GraphAlgoConfig(
        intro = "Greedy colouring in a fixed node order, then the same graph two-coloured as a bipartite check — " +
            "until the odd cycle refuses and forces a third colour.",
        def = colouringGraph,
        legend = listOf(
            NodeMarkColors.getValue(NodeMark.ACTIVE) to "Colouring now",
            EdgeMarkColors.getValue(EdgeMark.REJECTED) to "Conflict edge",
            NodeMarkColors.getValue(NodeMark.DONE) to "Coloured",
        ),
        build = ::graphColouringFrames,
    ),
    "topological_sort_pattern" to GraphAlgoConfig(
        intro = "Course schedule, the interview phrasing of a topological sort: first a curriculum that works, then " +
            "the same one with a back edge added — where the emitted count, not a separate check, catches the cycle.",
        def = courseGraph,
        legend = listOf(
            NodeMarkColors.getValue(NodeMark.ACTIVE) to "Taken now",
            NodeMarkColors.getValue(NodeMark.FRONTIER) to "Ready",
            NodeMarkColors.getValue(NodeMark.DONE) to "Completed",
        ),
        build = ::courseScheduleFrames,
    ),
    "union_find_pattern" to GraphAlgoConfig(
        intro = "Connectivity as edges stream in. Node badges are the current root, colours are the components, and " +
            "the one edge whose two finds agree is the cycle.",
        def = unionFindGraph,
        legend = listOf(
            NodeMarkColors.getValue(NodeMark.ACTIVE) to "Edge endpoints",
            EdgeMarkColors.getValue(EdgeMark.ACCEPTED) to "Merged",
            EdgeMarkColors.getValue(EdgeMark.REJECTED) to "Cycle edge",
        ),
        build = ::unionFindFrames,
    ),
    "bayesian_networks" to GraphAlgoConfig(
        intro = "The sprinkler network: how the missing edges buy the parameter saving, and the two ways conditioning changes what is independent of what.",
        def = bayesNetGraph,
        legend = listOf(
            NodeMarkColors.getValue(NodeMark.DONE) to "Observed",
            NodeMarkColors.getValue(NodeMark.ACTIVE) to "In question",
            GroupColors[0] to "Dependent",
        ),
        build = ::bayesNetworkFrames,
    ),
    "topological_sort" to GraphAlgoConfig(
        intro = "Kahn's algorithm on a DAG. Badges are remaining in-degrees — a node joins the queue the moment " +
            "its count hits zero, and the emitted count at the end is the cycle check.",
        def = dagStoryGraph,
        legend = listOf(
            NodeMarkColors.getValue(NodeMark.ACTIVE) to "Popped",
            NodeMarkColors.getValue(NodeMark.FRONTIER) to "Ready",
            NodeMarkColors.getValue(NodeMark.DONE) to "Emitted",
        ),
        build = ::topoStoryFrames,
    ),
    "max_flow" to GraphAlgoConfig(
        intro = "Ford-Fulkerson with BFS-chosen paths. Edge labels are capacities; the third augmenting path can " +
            "only exist because the first two left residual capacity behind.",
        def = flowStoryGraph,
        legend = listOf(
            NodeMarkColors.getValue(NodeMark.ACTIVE) to "On path",
            EdgeMarkColors.getValue(EdgeMark.ACCEPTED) to "Flow pushed",
            EdgeMarkColors.getValue(EdgeMark.REJECTED) to "Saturated (min cut)",
        ),
        build = ::maxFlowStoryFrames,
    ),
    "articulation_points" to GraphAlgoConfig(
        intro = "One DFS, badges showing disc/low per node. The ≥ test marks cut vertices, the strict > test " +
            "marks the single bridge holding the two triangles together.",
        def = cutStoryGraph,
        legend = listOf(
            NodeMarkColors.getValue(NodeMark.ACTIVE) to "Current",
            NodeMarkColors.getValue(NodeMark.DONE) to "Cut vertex",
            EdgeMarkColors.getValue(EdgeMark.REJECTED) to "Bridge",
        ),
        build = ::articulationStoryFrames,
    ),
    "bellman_ford" to GraphAlgoConfig(
        intro = "Bellman–Ford on a directed graph with negative edges. Badges show the current distance from A; " +
            "watch a node that already looks settled get corrected later — the reason Dijkstra breaks here.",
        def = negativeWeightGraph,
        legend = shortestPathLegend,
        build = ::bellmanStoryFrames,
    ),
    "floyd_warshall" to GraphAlgoConfig(
        intro = "Floyd–Warshall fills in every pair at once. The matrix is dist[row][col]; each round opens one more " +
            "node as a legal intermediate stop.",
        def = allPairsGraph,
        legend = allPairsLegend,
        build = ::floydWarshallFrames,
    ),
    "kruskals_mst" to GraphAlgoConfig(
        intro = "Kruskal sorts every edge by weight and takes each one unless it closes a cycle — the cycle test is " +
            "a union-find lookup, shown here as node colouring by component.",
        def = mstGraph,
        legend = kruskalLegend,
        build = ::kruskalFrames,
        edgeTable = true,
    ),
    "prims_mst" to GraphAlgoConfig(
        intro = "Prim on the same graph as Kruskal. It never sorts: it grows one tree, repeatedly taking the " +
            "cheapest edge that crosses out of it. Same total weight, different order of discovery.",
        def = mstGraph,
        legend = primLegend,
        build = ::primFrames,
        edgeTable = true,
    ),
    "tarjans_algorithm" to GraphAlgoConfig(
        intro = "Tarjan's SCC algorithm. Badges read index/low-link; a component pops off the stack the moment a " +
            "node's low-link equals its own index.",
        def = sccStoryGraph,
        legend = sccLegend,
        build = ::tarjanStoryFrames,
    ),
    "kosarajus_algorithm" to GraphAlgoConfig(
        intro = "Kosaraju's two-pass SCC algorithm: finish times on the original graph, then DFS the transpose in " +
            "reverse finish order. Arrows flip when the second pass starts.",
        def = sccStoryGraph,
        legend = sccLegend,
        build = { kosarajuStoryFrames(pass2 = false) },
        variants = listOf(
            GraphVariant("Pass 1 · Finish", sccStoryGraph) { kosarajuStoryFrames(pass2 = false) },
            GraphVariant("Pass 2 · Reverse", sccStoryGraph) { kosarajuStoryFrames(pass2 = true) },
        ),
    ),
    "graph_variants" to GraphAlgoConfig(
        intro = "The same six vertices and eight edges, read as undirected, directed, unweighted, weighted, " +
            "cyclic and acyclic in turn. Each frame states what that reading costs or buys, measured on this graph.",
        def = variantsGraph,
        legend = listOf(
            Color(0xFF0EA5E9) to "Reachable",
            SimColors.Green to "Cheapest route",
            SimColors.Red to "Blocked / cycle",
        ),
        build = ::graphVariantsFrames,
    ),
    "eulerian_path" to GraphAlgoConfig(
        intro = "Two triangles hinged at C. Badges start as degrees, because the degrees decide existence outright — " +
            "then Hierholzer walks until stuck and splices the leftover circuit in at the hinge.",
        def = eulerPathGraph,
        legend = listOf(
            NodeMarkColors.getValue(NodeMark.ACTIVE) to "Current vertex",
            NodeMarkColors.getValue(NodeMark.FRONTIER) to "Splice point",
            EdgeMarkColors.getValue(EdgeMark.ACCEPTED) to "Edge used",
        ),
        build = { eulerStoryFrames(circuit = false) },
        variants = listOf(
            GraphVariant("Path", eulerPathGraph) { eulerStoryFrames(circuit = false) },
            GraphVariant("Circuit", eulerCircuitGraph) { eulerStoryFrames(circuit = true) },
        ),
    ),
    "gcn" to GraphAlgoConfig(
        intro = "Two triangles bridged by one edge, aggregated through the normalized adjacency matrix, depth " +
            "after depth -- oversmoothing measured as an exact limit rather than asserted.",
        def = smallBridgeGraph,
        legend = listOf(
            GroupColors[0] to "Triangle A",
            GroupColors[1] to "Triangle B",
        ),
        build = ::gcnFrames,
    ),
    "gat" to GraphAlgoConfig(
        intro = "The same neighborhood GCN reads by degree alone, reweighted by feature content instead -- and a " +
            "direct perturbation showing which weighting reacts to it.",
        def = smallBridgeGraph,
        legend = listOf(
            NodeMarkColors.getValue(NodeMark.ACTIVE) to "Center node",
            NodeMarkColors.getValue(NodeMark.UPDATED) to "Perturbed neighbor",
            EdgeMarkColors.getValue(EdgeMark.ACTIVE) to "Scored edge",
        ),
        build = ::gatFrames,
    ),
    "hamiltonian_path" to GraphAlgoConfig(
        intro = "The same question about vertices instead of edges, and no counting argument to settle it. Badges are " +
            "position in the path; the frames include every dead end the search has to undo before the circuit appears.",
        def = hamiltonStoryGraph,
        legend = listOf(
            NodeMarkColors.getValue(NodeMark.ACTIVE) to "Just extended",
            NodeMarkColors.getValue(NodeMark.FRONTIER) to "On the path",
            NodeMarkColors.getValue(NodeMark.UPDATED) to "Path but no closing edge",
        ),
        build = { hamiltonStoryFrames(cycle = false) },
        variants = listOf(
            GraphVariant("Path", hamiltonStoryGraph) { hamiltonStoryFrames(cycle = false) },
            GraphVariant("Cycle", hamiltonStoryGraph) { hamiltonStoryFrames(cycle = true) },
        ),
    ),
)

private fun graphAlgoConfigFor(topicId: String): GraphAlgoConfig =
    graphAlgoConfigs[topicId] ?: graphAlgoConfigs.getValue("bellman_ford")

internal val graphAlgoTopicIds: Set<String> get() = graphAlgoConfigs.keys

// Frames name nodes and edges by id/index, so a typo resolves to nothing and just renders blank.
internal fun graphAlgoFrameCount(topicId: String): Int {
    val config = graphAlgoConfigFor(topicId)
    val runs = listOf(config.def to config.build) + config.variants.map { it.def to it.build }
    return runs.sumOf { (def, build) ->
        val frames = build()
        val ids = def.ids.toSet()
        frames.forEach { frame ->
            val story = frame.story
            val unknown = (frame.nodeMarks.keys + frame.badges.keys + frame.groups.keys +
                story?.nodes?.keys.orEmpty() + story?.badges?.keys.orEmpty() +
                story?.missing.orEmpty().flatMap { listOf(it.first, it.second) }) - ids
            require(unknown.isEmpty()) { "$topicId marks nodes not in its graph: $unknown" }
            val badEdge = (frame.edgeMarks.keys + frame.hiddenEdges + story?.edges?.keys.orEmpty() + story?.boxed.orEmpty())
                .filter { it !in def.edges.indices }
            require(badEdge.isEmpty()) { "$topicId marks edge indices outside its edge list: $badEdge" }
        }
        frames.size
    }
}

// ── UI ───────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GraphAlgorithmSection(topicId: String) {
    val config = remember(topicId) { graphAlgoConfigFor(topicId) }
    val storyFrames = remember(config) { config.build() }
    if (storyFrames.any { it.story != null }) {
        GraphStoryLab(config)
        return
    }
    val frames = storyFrames
    val playback = rememberPlaybackState(key = config, stepCount = frames.size, initialSpeedMs = 700f)
    val frame = frames[playback.index.coerceIn(0, frames.lastIndex)]
    // Kruskal and Prim narrate every step by hand, so their intro paragraph would only repeat it.
    val narrated = remember(frames) { frames.any { it.headline != null } }
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    // The mock's yellow and red are for a dark card; on white they need to be darker as text.
    val highlight = if (dark) SimColors.Active else Color(0xFFB7791F)
    val rejected = if (dark) Color(0xFFF28B82) else SimColors.Red

    Column(modifier = Modifier.fillMaxWidth()) {
    if (!narrated) {
        LabIntro(config.intro)
        Spacer(modifier = Modifier.height(12.dp))
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            GraphAlgoCanvas(def = config.def, frame = frame)

            if (config.edgeTable) {
                EdgeWeightTable(def = config.def, frame = frame)
            }

            if (frame.matrix != null) {
                DistanceMatrix(ids = config.def.ids, frame = frame)
            }

            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                config.legend.forEach { (color, label) -> GraphAlgoLegend(color, label) }
            }
        }
    }

    val (autoHeadline, autoBody) = remember(frame) { LabCaptionText.split(frame.status) }
    val headline = frame.headline
    Text(
        if (headline == null) {
            AnnotatedString(autoHeadline)
        } else {
            buildAnnotatedString {
                headline.forEach { span ->
                    val color = when (span.tone) {
                        GraphTone.ACTIVE -> highlight
                        GraphTone.REJECTED -> rejected
                        GraphTone.DONE -> SimColors.Green
                        null -> null
                    }
                    if (color == null) append(span.text) else withStyle(SpanStyle(color = color)) { append(span.text) }
                }
            }
        },
        fontSize = 20.sp,
        lineHeight = 26.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(top = 16.dp),
    )
    (if (headline == null) autoBody else frame.body)?.let { body ->
        Text(
            body,
            fontSize = 15.sp,
            lineHeight = 22.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }

    PlaybackTransport(playback, captions = frames.map { it.status })
    }
}

@Composable
private fun GraphAlgoLegend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(11.dp).background(color, RoundedCornerShape(3.dp)))
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 7.dp),
        )
    }
}

@Composable
private fun GraphAlgoCanvas(def: GraphDef, frame: GraphAlgoFrame) {
    val textMeasurer = rememberTextMeasurer()
    val nodeLabelStyle = TextStyle(
        color = MaterialTheme.colorScheme.onSurface,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
    )
    val weightStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    val badgeStyle = TextStyle(color = MaterialTheme.colorScheme.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    val surfaceTint = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    val surface = MaterialTheme.colorScheme.surface
    val idleFill = MaterialTheme.colorScheme.surfaceVariant
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    // Anti-parallel pairs (A→B alongside B→A) would draw on top of each other; the second one is
    // nudged off the centre line so both arrowheads and both weights stay readable.
    val hasReverse = remember(def) {
        def.edges.map { e -> def.edges.any { it !== e && it.from == e.to && it.to == e.from } }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            
            .background(surfaceTint, RoundedCornerShape(14.dp))
            .padding(6.dp),
    ) {
        Canvas(modifier = Modifier.fillMaxWidth().height(250.dp)) {
            val radius = 16.dp.toPx()
            val padX = 26.dp.toPx()
            val padY = 26.dp.toPx()
            val positions = def.nodes.associate { node ->
                node.id to Offset(
                    padX + node.x * (size.width - 2 * padX),
                    padY + node.y * (size.height - 2 * padY),
                )
            }

            def.edges.forEachIndexed { index, edge ->
                if (index in frame.hiddenEdges) return@forEachIndexed
                val flip = frame.reversed && edge.directed
                val from = positions.getValue(if (flip) edge.to else edge.from)
                val to = positions.getValue(if (flip) edge.from else edge.to)
                val length = hypot(to.x - from.x, to.y - from.y).coerceAtLeast(1f)
                val unit = Offset((to.x - from.x) / length, (to.y - from.y) / length)
                val shift = if (hasReverse[index]) 7.dp.toPx() else 0f
                val offset = Offset(-unit.y * shift, unit.x * shift)
                val start = Offset(from.x + unit.x * radius, from.y + unit.y * radius) + offset
                val end = Offset(to.x - unit.x * radius, to.y - unit.y * radius) + offset
                val mark = if (index == frame.focusEdge) EdgeMark.ACTIVE else frame.edgeMarks[index] ?: EdgeMark.IDLE
                // Idle edges recede so the ones an algorithm has touched carry the picture.
                val color = if (mark == EdgeMark.IDLE) muted.copy(alpha = 0.45f) else EdgeMarkColors.getValue(mark)
                val width = when (mark) {
                    EdgeMark.IDLE -> 1.5.dp.toPx()
                    EdgeMark.REJECTED -> 2.dp.toPx()
                    else -> 3.dp.toPx()
                }
                // Rejected edges are dashed as well as red, so "skipped" survives colour blindness.
                val dash = if (mark == EdgeMark.REJECTED) {
                    PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx()))
                } else {
                    null
                }

                drawLine(color = color, start = start, end = end, strokeWidth = width, pathEffect = dash)

                if (edge.directed && !frame.undirected) {
                    val head = 9.dp.toPx()
                    val angle = kotlin.math.atan2(unit.y, unit.x)
                    val arrow = Path().apply {
                        moveTo(end.x, end.y)
                        listOf(2.7, -2.7).forEach { spread ->
                            lineTo(
                                end.x + head * cos(angle + spread).toFloat(),
                                end.y + head * sin(angle + spread).toFloat(),
                            )
                        }
                        close()
                    }
                    drawPath(arrow, color)
                }

                edge.weight?.takeUnless { frame.hideWeights }?.let { weight ->
                    val layout = textMeasurer.measure(weight.toString(), weightStyle)
                    // Beside the line rather than on it, pushed out along the normal on the same
                    // side an anti-parallel pair is nudged to, so the two weights never collide.
                    val side = 10.dp.toPx()
                    val mid = Offset((start.x + end.x) / 2f - unit.y * side, (start.y + end.y) / 2f + unit.x * side)
                    drawText(
                        layout,
                        color = if (mark == EdgeMark.IDLE) muted else color,
                        topLeft = Offset(mid.x - layout.size.width / 2f, mid.y - layout.size.height / 2f),
                    )
                }
            }

            // Nodes carry two things at once: the fill says which group a node belongs to, the ring
            // says what the algorithm is doing to it right now. A plain node is neutral, not violet.
            def.nodes.forEach { node ->
                val center = positions.getValue(node.id)
                val mark = frame.nodeMarks[node.id]
                val groupColor = frame.groups[node.id]
                    ?.takeUnless { mark == NodeMark.IDLE }
                    ?.let { GroupColors[it % GroupColors.size] }
                // An explicit IDLE mark means "take this node out of its group" — draw it neutral.
                val markColor = mark?.takeIf { it != NodeMark.IDLE }?.let { NodeMarkColors.getValue(it) }

                drawCircle(color = surface, radius = radius, center = center)
                drawCircle(
                    color = when {
                        groupColor != null -> groupColor.copy(alpha = 0.32f)
                        markColor != null -> markColor.copy(alpha = 0.22f)
                        else -> idleFill
                    },
                    radius = radius,
                    center = center,
                )
                drawCircle(
                    color = markColor ?: groupColor ?: muted.copy(alpha = 0.5f),
                    radius = radius,
                    center = center,
                    style = Stroke(width = if (markColor != null) 2.5.dp.toPx() else 1.5.dp.toPx()),
                )

                val label = textMeasurer.measure(node.id, nodeLabelStyle)
                drawText(
                    label,
                    topLeft = Offset(center.x - label.size.width / 2f, center.y - label.size.height / 2f),
                )

                frame.badges[node.id]?.let { badge ->
                    val layout = textMeasurer.measure(badge, badgeStyle)
                    drawText(
                        layout,
                        topLeft = Offset(center.x - layout.size.width / 2f, center.y + radius + 2.dp.toPx()),
                    )
                }
            }
        }
    }
}

// Every edge, lightest first, three to a row. Kruskal's progress is a cursor moving down this list;
// each cell shows the same mark the edge carries in the graph above it.
@Composable
private fun EdgeWeightTable(def: GraphDef, frame: GraphAlgoFrame) {
    val sorted = remember(def) { def.edges.withIndex().sortedBy { it.value.weight ?: 0 } }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Text(
            "EDGES BY WEIGHT",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = muted,
            letterSpacing = 0.8.sp,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        sorted.chunked(3).forEach { row ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { (index, edge) ->
                    val mark = if (index == frame.focusEdge) null else frame.edgeMarks[index]
                    val focused = index == frame.focusEdge
                    val (background, content) = when {
                        focused -> SimColors.Active to Color(0xFF1F1A0A)
                        mark == EdgeMark.ACCEPTED -> SimColors.Green.copy(alpha = 0.16f) to SimColors.Green
                        mark == EdgeMark.REJECTED -> SimColors.Red.copy(alpha = 0.14f) to SimColors.Red
                        mark == EdgeMark.ACTIVE -> SimColors.Active.copy(alpha = 0.18f) to MaterialTheme.colorScheme.onSurface
                        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f) to muted
                    }
                    val struck = if (mark == EdgeMark.REJECTED) TextDecoration.LineThrough else null
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .padding(bottom = 6.dp)
                            .background(background, RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "${edge.from}–${edge.to}",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = content,
                            textDecoration = struck,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            "${edge.weight ?: ""}",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = content,
                            textDecoration = struck,
                        )
                    }
                }
                // Keep a short last row's cells the same width as the rest.
                repeat(3 - row.size) { Box(modifier = Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun DistanceMatrix(ids: List<String>, frame: GraphAlgoFrame) {
    val matrix = frame.matrix ?: return
    Column(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            MatrixCell("", Modifier.weight(1f))
            ids.forEach { id ->
                MatrixCell(
                    id,
                    Modifier.weight(1f),
                    header = true,
                    tinted = frame.matrixVia == ids.indexOf(id),
                )
            }
        }
        matrix.forEachIndexed { i, row ->
            Row(modifier = Modifier.fillMaxWidth()) {
                MatrixCell(ids[i], Modifier.weight(1f), header = true, tinted = frame.matrixVia == i)
                row.forEachIndexed { j, value ->
                    MatrixCell(
                        dist(value),
                        Modifier.weight(1f),
                        focused = frame.matrixFocus == i to j,
                        tinted = frame.matrixVia == i || frame.matrixVia == j,
                    )
                }
            }
        }
    }
}

@Composable
private fun MatrixCell(
    text: String,
    modifier: Modifier = Modifier,
    header: Boolean = false,
    focused: Boolean = false,
    tinted: Boolean = false,
) {
    val background = when {
        focused -> NodeMarkColors.getValue(NodeMark.UPDATED)
        header -> MaterialTheme.colorScheme.surfaceVariant
        tinted -> NodeMarkColors.getValue(NodeMark.ACTIVE).copy(alpha = 0.25f)
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    }
    Box(
        modifier = modifier
            .padding(2.dp)
            .background(background, RoundedCornerShape(6.dp))
            .padding(vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (header || focused) FontWeight.Bold else FontWeight.Normal,
            color = if (focused) Color.White else MaterialTheme.colorScheme.onSurface,
        )
    }
}

// ── Story UI ─────────────────────────────────────────────────────────────────

private val CutColor = SimColors.Answer

private fun gsColor(tone: GS): Color? = when (tone) {
    GS.ACTIVE -> SimColors.Active
    GS.PATH -> SimColors.Blue
    GS.DONE -> SimColors.Green
    GS.CUT -> CutColor
    GS.WARN -> SimColors.Red
    else -> null
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GraphStoryLab(config: GraphAlgoConfig) {
    var tab by remember(config) { mutableIntStateOf(0) }
    val variant = config.variants.getOrNull(tab)
    val def = variant?.def ?: config.def
    val frames = remember(config, tab) { (variant?.build ?: config.build)() }
    val playback = rememberPlaybackState(key = config to tab, stepCount = frames.size, initialSpeedMs = 700f)
    val story = frames[playback.index.coerceIn(0, frames.lastIndex)].story ?: return
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val yellow = if (dark) SimColors.Active else Color(0xFFB7791F)
    fun ink(tone: GS?): Color? = when (tone) {
        GS.ACTIVE -> yellow
        GS.CUT -> if (dark) Color(0xFFA78BFA) else CutColor
        GS.WARN -> if (dark) Color(0xFFF28B82) else SimColors.Red
        null -> null
        else -> gsColor(tone)
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                if (config.variants.isNotEmpty()) {
                    GraphTabs(config.variants.map { it.label }, tab) { tab = it }
                } else {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(story.title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, letterSpacing = 0.8.sp, color = muted, maxLines = 1, modifier = Modifier.weight(1f))
                        if (story.note.isNotEmpty()) Text(story.note, fontSize = 13.sp, color = muted, maxLines = 1, modifier = Modifier.padding(start = 12.dp))
                    }
                }

                GraphStoryCanvas(def, story, Modifier.padding(top = 12.dp))

                story.rows.forEach { row -> GraphCellRow(row) }

                if (story.lines.isNotEmpty()) {
                    Column(modifier = Modifier.fillMaxWidth().padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        story.lines.forEach { line ->
                            Row(
                                modifier = Modifier.fillMaxWidth().height(40.dp).background(muted.copy(alpha = 0.12f), RoundedCornerShape(10.dp)).padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(line.test, fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, modifier = Modifier.weight(1f))
                                Text(line.verdict, fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = ink(line.tone) ?: muted, maxLines = 1)
                            }
                        }
                    }
                }

                val present = buildSet {
                    addAll(story.nodes.values); addAll(story.edges.values)
                    story.rows.flatten().forEach { g -> g.cells.forEach { add(it.tone) } }
                    if (story.missing.isNotEmpty()) add(GS.WARN)
                    story.lines.forEach { add(it.tone) }
                }
                val legend = story.legend.filter { it.first in present }
                if (legend.isNotEmpty()) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        legend.forEach { (tone, label) ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (tone == GS.WARN) {
                                    Box(modifier = Modifier.size(width = 12.dp, height = 2.dp).background(SimColors.Red, RoundedCornerShape(1.dp)))
                                } else {
                                    Box(modifier = Modifier.size(10.dp).background(gsColor(tone) ?: muted, RoundedCornerShape(3.dp)))
                                }
                                Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f), modifier = Modifier.padding(start = 6.dp))
                            }
                        }
                    }
                }
            }
        }

        if (story.chips.isNotEmpty()) {
            FlowRow(
                modifier = Modifier.padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                story.chips.forEach { chip ->
                    Row(
                        modifier = Modifier.height(32.dp).background(SimColors.Tint, RoundedCornerShape(9.dp)).padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(chip.key, fontFamily = FontFamily.Monospace, fontSize = 15.sp, color = muted, maxLines = 1)
                        Text(chip.value, fontFamily = FontFamily.Monospace, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = ink(chip.tone) ?: MaterialTheme.colorScheme.onSurface, maxLines = 1)
                    }
                }
            }
        }

        Text(
            buildAnnotatedString {
                var at = 0
                GraphMark.findAll(story.headline).forEach { m ->
                    append(story.headline.substring(at, m.range.first))
                    val tone = when (m.groupValues[1]) {
                        "w" -> GS.WARN
                        "a" -> GS.ACTIVE
                        "p" -> GS.PATH
                        "m" -> GS.DONE
                        "c" -> GS.CUT
                        else -> story.emphasis
                    }
                    withStyle(SpanStyle(color = ink(tone) ?: yellow)) { append(m.groupValues[2]) }
                    at = m.range.last + 1
                }
                append(story.headline.substring(at))
            },
            fontSize = 20.sp,
            lineHeight = 26.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 16.dp),
        )
        Text(story.body, fontSize = 15.sp, lineHeight = 22.sp, color = muted, modifier = Modifier.padding(top = 8.dp))

        PlaybackTransport(playback, captions = frames.map { it.status })
    }
}

@Composable
private fun GraphTabs(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    Row(modifier = Modifier.fillMaxWidth().background(SimColors.Tint, RoundedCornerShape(9.dp)).padding(2.dp)) {
        labels.forEachIndexed { i, label ->
            val on = i == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(30.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(if (on) (if (dark) Color(0xFF636366) else Color.White) else Color.Transparent)
                    .clickable { onSelect(i) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    fontSize = 13.sp,
                    fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (on) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

// One row of labelled cell groups sharing the width: every cell is the same size, and a wider gap
// separates groups.
@Composable
private fun GraphCellRow(groups: List<GCellGroup>) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val gap = 6.dp
    Column(modifier = Modifier.fillMaxWidth().padding(top = 14.dp)) {
        if (groups.any { it.label != null }) {
            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                groups.forEachIndexed { i, g ->
                    if (i > 0) Spacer(Modifier.width(14.dp))
                    Row(modifier = Modifier.weight(g.cells.size.toFloat()), verticalAlignment = Alignment.CenterVertically) {
                        Text(g.label.orEmpty(), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp, color = muted, maxLines = 1, modifier = Modifier.weight(1f))
                        g.note?.let { Text(it, fontSize = 13.sp, color = muted, maxLines = 1) }
                    }
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth().height(40.dp)) {
            groups.forEachIndexed { gi, g ->
                if (gi > 0) Spacer(Modifier.width(14.dp))
                Row(modifier = Modifier.weight(g.cells.size.toFloat()).fillMaxHeight(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                    g.cells.forEach { cell ->
                        val fill = gsColor(cell.tone)
                        val shape = RoundedCornerShape(10.dp)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .then(
                                    if (cell.tone == GS.EMPTY) {
                                        Modifier.drawBehind {
                                            drawRoundRect(
                                                muted.copy(alpha = 0.35f),
                                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(10.dp.toPx()),
                                                style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))),
                                            )
                                        }
                                    } else {
                                        Modifier.background(fill ?: muted.copy(alpha = 0.2f), shape)
                                    },
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                cell.text,
                                fontFamily = FontFamily.Monospace,
                                fontSize = if (cell.text.length > 4) 13.sp else 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (cell.tone == GS.ACTIVE) Color(0xFF1F1A0A) else if (fill != null) Color.White else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GraphStoryCanvas(def: GraphDef, story: GraphStory, modifier: Modifier = Modifier) {
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold)
    val badgeStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    val weightStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val onSurface = MaterialTheme.colorScheme.onSurface
    val surface = MaterialTheme.colorScheme.surface
    val hasReverse = remember(def) { def.edges.map { e -> def.edges.any { it !== e && it.from == e.to && it.to == e.from } } }

    Canvas(modifier = modifier.fillMaxWidth().height(210.dp)) {
        val radius = 16.dp.toPx()
        val padX = 22.dp.toPx()
        val padY = 30.dp.toPx()
        val pos = def.nodes.associate { it.id to Offset(padX + it.x * (size.width - 2 * padX), padY + it.y * (size.height - 2 * padY)) }

        story.missing.forEach { (a, b) ->
            val p = pos.getValue(a)
            val q = pos.getValue(b)
            val u = (q - p) / (q - p).getDistance()
            drawLine(SimColors.Red, p + u * radius, q - u * radius, 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx())))
        }

        def.edges.forEachIndexed { i, edge ->
            val flip = story.reversed && edge.directed
            val from = pos.getValue(if (flip) edge.to else edge.from)
            val to = pos.getValue(if (flip) edge.from else edge.to)
            val u = (to - from) / (to - from).getDistance()
            val shift = if (hasReverse[i]) 7.dp.toPx() else 0f
            val off = Offset(-u.y * shift, u.x * shift)
            val start = from + u * radius + off
            val end = to - u * (radius + if (edge.directed) 2.dp.toPx() else 0f) + off
            val tone = story.edges[i] ?: GS.IDLE
            val color = gsColor(tone) ?: muted.copy(alpha = 0.45f)
            drawLine(color, start, end, (if (tone == GS.IDLE) 1.5.dp else 3.dp).toPx(), StrokeCap.Round)
            if (edge.directed) {
                val head = 9.dp.toPx()
                val angle = kotlin.math.atan2(u.y, u.x)
                drawPath(
                    Path().apply {
                        moveTo(end.x, end.y)
                        listOf(2.7, -2.7).forEach { spread -> lineTo(end.x + head * cos(angle + spread).toFloat(), end.y + head * sin(angle + spread).toFloat()) }
                        close()
                    },
                    color,
                )
            }
            val text = story.edgeLabels[i] ?: edge.weight?.toString() ?: return@forEachIndexed
            val layout = textMeasurer.measure(text, weightStyle)
            if (i in story.boxed) {
                val mid = (start + end) / 2f
                val w = layout.size.width + 10.dp.toPx()
                val h = layout.size.height + 4.dp.toPx()
                val tl = Offset(mid.x - w / 2f, mid.y - h / 2f)
                val corner = androidx.compose.ui.geometry.CornerRadius(5.dp.toPx())
                drawRoundRect(surface, tl, androidx.compose.ui.geometry.Size(w, h), corner)
                drawRoundRect(if (tone == GS.IDLE) muted.copy(alpha = 0.5f) else color, tl, androidx.compose.ui.geometry.Size(w, h), corner, style = Stroke(1.dp.toPx()))
                drawText(layout, color = if (tone == GS.IDLE) onSurface else color, topLeft = Offset(mid.x - layout.size.width / 2f, mid.y - layout.size.height / 2f))
            } else {
                // 40% along rather than halfway, so two edges crossing at their midpoints keep their weights apart.
                val side = 10.dp.toPx()
                val at = start + (end - start) * 0.4f
                val mid = Offset(at.x - u.y * side, at.y + u.x * side)
                drawText(layout, color = muted, topLeft = Offset(mid.x - layout.size.width / 2f, mid.y - layout.size.height / 2f))
            }
        }

        def.nodes.forEach { node ->
            val c = pos.getValue(node.id)
            val tone = story.nodes[node.id] ?: GS.IDLE
            val fill = gsColor(tone)
            drawCircle(surface, radius, c)
            drawCircle(fill ?: muted.copy(alpha = 0.3f), radius, c)
            val label = textMeasurer.measure(node.id, labelStyle)
            drawText(label, color = if (tone == GS.ACTIVE) Color(0xFF1F1A0A) else if (fill != null) Color.White else onSurface,
                topLeft = Offset(c.x - label.size.width / 2f, c.y - label.size.height / 2f))
            story.badges[node.id]?.let { (text, badgeTone) ->
                val layout = textMeasurer.measure(text, badgeStyle)
                val above = node.y < 0.3f
                val y = if (above) c.y - radius - 4.dp.toPx() - layout.size.height else c.y + radius + 4.dp.toPx()
                drawText(layout, color = gsColor(badgeTone) ?: muted, topLeft = Offset(c.x - layout.size.width / 2f, y))
            }
        }
    }
}
