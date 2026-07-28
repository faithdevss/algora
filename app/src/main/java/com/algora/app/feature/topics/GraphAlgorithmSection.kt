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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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
    NodeMark.FRONTIER to Color(0xFF3B82F6),
    NodeMark.ACTIVE to Color(0xFFFACC15),
    NodeMark.UPDATED to SimColors.Green,
    NodeMark.DONE to Color(0xFFF97316),
)

private val EdgeMarkColors = mapOf(
    EdgeMark.IDLE to Color(0xFFCBD0DA),
    EdgeMark.ACTIVE to Color(0xFFFACC15),
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
)

private class GraphAlgoConfig(
    val intro: String,
    val def: GraphDef,
    val legend: List<Pair<Color, String>>,
    val build: () -> List<GraphAlgoFrame>,
)

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

// Directed, unweighted, with three strongly connected components: {A,B,C}, {D,E,F}, {G}.
private val sccGraph = GraphDef(
    nodes = listOf(
        GNode("A", 0.08f, 0.20f),
        GNode("B", 0.40f, 0.05f),
        GNode("C", 0.36f, 0.48f),
        GNode("D", 0.72f, 0.16f),
        GNode("E", 0.94f, 0.56f),
        GNode("F", 0.62f, 0.84f),
        GNode("G", 0.14f, 0.86f),
    ),
    edges = listOf(
        GEdge("A", "B", directed = true),
        GEdge("B", "C", directed = true),
        GEdge("C", "A", directed = true),
        GEdge("C", "D", directed = true),
        GEdge("D", "E", directed = true),
        GEdge("E", "F", directed = true),
        GEdge("F", "D", directed = true),
        GEdge("F", "G", directed = true),
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

private fun bellmanFordFrames(): List<GraphAlgoFrame> {
    val def = negativeWeightGraph
    val source = "A"
    val distances = def.ids.associateWith { if (it == source) 0 else GRAPH_INF }.toMutableMap()
    val frames = mutableListOf<GraphAlgoFrame>()
    fun badges() = distances.mapValues { (_, d) -> dist(d) }

    frames += GraphAlgoFrame(
        status = "Source $source starts at 0, everything else at ∞. Bellman–Ford makes no assumption " +
            "about edge signs, so it cannot commit to a node early the way Dijkstra does.",
        badges = badges(),
        nodeMarks = mapOf(source to NodeMark.ACTIVE),
    )

    var pass = 0
    var relaxedThisPass = true
    while (pass < def.ids.size - 1 && relaxedThisPass) {
        pass++
        relaxedThisPass = false
        frames += GraphAlgoFrame(
            status = "Pass $pass of ${def.ids.size - 1}: sweep all ${def.edges.size} edges once, in a fixed order.",
            badges = badges(),
        )
        def.edges.forEachIndexed { index, edge ->
            val from = distances.getValue(edge.from)
            val weight = edge.weight ?: 0
            val candidate = from + weight
            if (from < GRAPH_INF && candidate < distances.getValue(edge.to)) {
                val previous = distances.getValue(edge.to)
                distances[edge.to] = candidate
                relaxedThisPass = true
                frames += GraphAlgoFrame(
                    status = "Relax ${edge.from}→${edge.to} (w = $weight): ${edge.to} improves from " +
                        "${dist(previous)} to $candidate.",
                    badges = badges(),
                    nodeMarks = mapOf(edge.from to NodeMark.ACTIVE, edge.to to NodeMark.UPDATED),
                    edgeMarks = mapOf(index to EdgeMark.ACCEPTED),
                )
            }
        }
        if (!relaxedThisPass) {
            frames += GraphAlgoFrame(
                status = "Pass $pass changed nothing, so nothing can change again — the remaining passes are skipped.",
                badges = badges(),
                nodeMarks = def.ids.associateWith { NodeMark.DONE },
            )
        }
    }

    val violating = def.edges.withIndex().firstOrNull { (_, edge) ->
        val from = distances.getValue(edge.from)
        from < GRAPH_INF && from + (edge.weight ?: 0) < distances.getValue(edge.to)
    }
    frames += if (violating == null) {
        GraphAlgoFrame(
            status = "One extra sweep relaxes no edge, which is the negative-cycle test: none is reachable from " +
                "$source, so these distances are final.",
            badges = badges(),
            nodeMarks = def.ids.associateWith { NodeMark.DONE },
        )
    } else {
        GraphAlgoFrame(
            status = "An extra sweep still relaxes ${violating.value.from}→${violating.value.to}. After " +
                "${def.ids.size - 1} passes that is only possible inside a negative cycle — report it, do not " +
                "return distances.",
            badges = badges(),
            edgeMarks = mapOf(violating.index to EdgeMark.REJECTED),
        )
    }
    return frames
}

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

    val sorted = def.edges.withIndex().sortedBy { it.value.weight ?: 0 }
    frames += GraphAlgoFrame(
        status = "Sort every edge by weight: ${sorted.joinToString(", ") { "${it.value.from}${it.value.to}(${it.value.weight})" }}. " +
            "Kruskal then walks that list once.",
    )

    for ((index, edge) in sorted) {
        val rootFrom = find(edge.from)
        val rootTo = find(edge.to)
        if (rootFrom == rootTo) {
            marks[index] = EdgeMark.REJECTED
            frames += GraphAlgoFrame(
                status = "${edge.from}–${edge.to} (w = ${edge.weight}) is skipped: both ends already sit in the " +
                    "same component, so this edge would close a cycle.",
                edgeMarks = marks.toMap(),
                groups = groupsFrom(def) { find(it) },
                nodeMarks = mapOf(edge.from to NodeMark.ACTIVE, edge.to to NodeMark.ACTIVE),
            )
        } else {
            parent[rootFrom] = rootTo
            marks[index] = EdgeMark.ACCEPTED
            total += edge.weight ?: 0
            accepted++
            frames += GraphAlgoFrame(
                status = "Take ${edge.from}–${edge.to} (w = ${edge.weight}): the ends were in different components, " +
                    "so union them. Tree weight $total, $accepted of ${def.ids.size - 1} edges.",
                edgeMarks = marks.toMap(),
                groups = groupsFrom(def) { find(it) },
                nodeMarks = mapOf(edge.from to NodeMark.UPDATED, edge.to to NodeMark.UPDATED),
            )
        }
        if (accepted == def.ids.size - 1) break
    }

    frames += GraphAlgoFrame(
        status = "${def.ids.size - 1} edges accepted and every node is in one component: minimum spanning tree, " +
            "total weight $total.",
        edgeMarks = marks.toMap(),
        nodeMarks = def.ids.associateWith { NodeMark.DONE },
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
        )
    }

    frames += GraphAlgoFrame(
        status = "All ${def.ids.size} nodes absorbed, total weight $total — the same tree Kruskal builds, reached by " +
            "growing a cut instead of sorting edges.",
        edgeMarks = marks.toMap(),
        nodeMarks = def.ids.associateWith { NodeMark.DONE },
    )
    return frames
}

// ── Tarjan ───────────────────────────────────────────────────────────────────

private fun tarjanFrames(): List<GraphAlgoFrame> {
    val def = sccGraph
    val discovery = mutableMapOf<String, Int>()
    val low = mutableMapOf<String, Int>()
    val onStack = mutableSetOf<String>()
    val stack = ArrayDeque<String>()
    val groups = mutableMapOf<String, Int>()
    val frames = mutableListOf<GraphAlgoFrame>()
    var counter = 0
    var componentCount = 0

    fun badges() = def.ids.filter { it in discovery }
        .associateWith { "${discovery.getValue(it)}/${low.getValue(it)}" }

    fun stackText() = if (stack.isEmpty()) "empty" else stack.joinToString("", limit = 8)

    frames += GraphAlgoFrame(
        status = "Tarjan finds strongly connected components in one DFS. Each node gets a discovery index and a " +
            "low-link: the smallest index reachable from its subtree.",
    )

    fun strongConnect(node: String) {
        discovery[node] = counter
        low[node] = counter
        counter++
        stack.addLast(node)
        onStack += node
        frames += GraphAlgoFrame(
            status = "Visit $node — index ${discovery.getValue(node)}, low-link starts equal to it. Stack: ${stackText()}.",
            badges = badges(),
            nodeMarks = mapOf(node to NodeMark.ACTIVE) + onStack.filter { it != node }.associateWith { NodeMark.FRONTIER },
            groups = groups.toMap(),
        )

        for (next in neighboursOf(def, node)) {
            if (next !in discovery) {
                strongConnect(next)
                if (low.getValue(next) < low.getValue(node)) {
                    low[node] = low.getValue(next)
                    frames += GraphAlgoFrame(
                        status = "$node inherits low-link ${low.getValue(node)} from $next: whatever $next can reach, " +
                            "$node can reach.",
                        badges = badges(),
                        nodeMarks = mapOf(node to NodeMark.UPDATED, next to NodeMark.FRONTIER),
                        groups = groups.toMap(),
                    )
                }
            } else if (next in onStack && discovery.getValue(next) < low.getValue(node)) {
                low[node] = discovery.getValue(next)
                frames += GraphAlgoFrame(
                    status = "$node→$next is a back edge to a node still on the stack, so $node's low-link drops to " +
                        "${discovery.getValue(next)} — they are on a cycle together.",
                    badges = badges(),
                    nodeMarks = mapOf(node to NodeMark.UPDATED, next to NodeMark.ACTIVE),
                    groups = groups.toMap(),
                )
            }
        }

        if (low.getValue(node) == discovery.getValue(node)) {
            val members = mutableListOf<String>()
            do {
                val popped = stack.removeLast()
                onStack -= popped
                groups[popped] = componentCount
                members += popped
            } while (popped != node)
            componentCount++
            frames += GraphAlgoFrame(
                status = "$node's low-link equals its own index, so it is the root of a component: pop " +
                    "{${members.sorted().joinToString(", ")}} off the stack.",
                badges = badges(),
                groups = groups.toMap(),
            )
        }
    }

    def.ids.forEach { if (it !in discovery) strongConnect(it) }

    frames += GraphAlgoFrame(
        status = "$componentCount strongly connected components in a single DFS — no second pass and no transpose, " +
            "which is what separates Tarjan from Kosaraju.",
        groups = groups.toMap(),
        badges = badges(),
    )
    return frames
}

// ── Kosaraju ─────────────────────────────────────────────────────────────────

private fun kosarajuFrames(): List<GraphAlgoFrame> {
    val def = sccGraph
    val visited = mutableSetOf<String>()
    val order = mutableListOf<String>()
    val finishBadges = mutableMapOf<String, String>()
    val frames = mutableListOf<GraphAlgoFrame>()

    frames += GraphAlgoFrame(
        status = "Kosaraju uses two passes. First: a DFS over the graph as given, recording the order in which " +
            "nodes finish.",
    )

    fun firstPass(node: String) {
        visited += node
        neighboursOf(def, node).forEach { if (it !in visited) firstPass(it) }
        order += node
        finishBadges[node] = "#${order.size}"
        frames += GraphAlgoFrame(
            status = "$node finishes (position ${order.size}). A node finishes only after everything it can reach.",
            badges = finishBadges.toMap(),
            nodeMarks = mapOf(node to NodeMark.UPDATED) + visited.filter { it != node }.associateWith { NodeMark.DONE },
        )
    }
    def.ids.forEach { if (it !in visited) firstPass(it) }

    frames += GraphAlgoFrame(
        status = "Finish order: ${order.joinToString(" ")}. Now reverse every edge — components survive reversal, " +
            "but the paths between them do not.",
        badges = finishBadges.toMap(),
        reversed = true,
        nodeMarks = def.ids.associateWith { NodeMark.DONE },
    )

    val groups = mutableMapOf<String, Int>()
    var componentCount = 0
    fun secondPass(node: String, component: Int, members: MutableList<String>) {
        groups[node] = component
        members += node
        neighboursOf(def, node, reversed = true).forEach { if (it !in groups) secondPass(it, component, members) }
    }

    for (node in order.reversed()) {
        if (node in groups) continue
        val members = mutableListOf<String>()
        secondPass(node, componentCount, members)
        frames += GraphAlgoFrame(
            status = "Start the reverse DFS at $node (latest unassigned finish) — it reaches exactly " +
                "{${members.sorted().joinToString(", ")}}, which is one component.",
            reversed = true,
            groups = groups.toMap(),
            nodeMarks = mapOf(node to NodeMark.ACTIVE),
            badges = finishBadges.toMap(),
        )
        componentCount++
    }

    frames += GraphAlgoFrame(
        status = "$componentCount components, two linear passes: O(V + E) overall, at the cost of storing the " +
            "transpose that Tarjan never builds.",
        groups = groups.toMap(),
    )
    return frames
}

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

private val mstLegend = listOf(
    EdgeMarkColors.getValue(EdgeMark.ACTIVE) to "Candidate",
    EdgeMarkColors.getValue(EdgeMark.ACCEPTED) to "In tree",
    EdgeMarkColors.getValue(EdgeMark.REJECTED) to "Cycle",
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

// Four nodes, five capacities. Small enough that every residual value fits in the status line.
private val flowGraph = GraphDef(
    nodes = listOf(
        GNode("S", 0.08f, 0.50f),
        GNode("A", 0.45f, 0.15f),
        GNode("B", 0.45f, 0.85f),
        GNode("T", 0.92f, 0.50f),
    ),
    edges = listOf(
        GEdge("S", "A", 3, directed = true),
        GEdge("S", "B", 2, directed = true),
        GEdge("A", "B", 1, directed = true),
        GEdge("A", "T", 2, directed = true),
        GEdge("B", "T", 3, directed = true),
    ),
)

// A triangle hanging off a second triangle by a single edge: one bridge, two cut vertices.
private val cutGraph = GraphDef(
    nodes = listOf(
        GNode("A", 0.10f, 0.18f),
        GNode("B", 0.10f, 0.82f),
        GNode("C", 0.40f, 0.50f),
        GNode("D", 0.70f, 0.50f),
        GNode("E", 0.94f, 0.18f),
        GNode("F", 0.94f, 0.82f),
    ),
    edges = listOf(
        GEdge("A", "B"),
        GEdge("A", "C"),
        GEdge("B", "C"),
        GEdge("C", "D"),
        GEdge("D", "E"),
        GEdge("D", "F"),
        GEdge("E", "F"),
    ),
)

private fun topologicalSortFrames(): List<GraphAlgoFrame> {
    val def = dagGraph
    val frames = mutableListOf<GraphAlgoFrame>()
    val inDegree = def.ids.associateWith { id -> def.edges.count { it.to == id } }.toMutableMap()
    val emitted = mutableListOf<String>()
    val done = mutableSetOf<String>()

    fun badges() = inDegree.mapValues { (id, d) -> if (id in done) "✓" else d.toString() }

    frames += GraphAlgoFrame(
        status = "In-degree is the number of unmet dependencies. A and B have none, so they are ready " +
            "immediately; C waits on both of them.",
        badges = badges(),
    )

    val ready = ArrayDeque(def.ids.filter { inDegree.getValue(it) == 0 })
    frames += GraphAlgoFrame(
        status = "Queue seeded with ${ready.joinToString(", ")}. Two nodes are ready at once, which is why this " +
            "DAG has several valid orders rather than one.",
        nodeMarks = ready.associateWith { NodeMark.FRONTIER },
        badges = badges(),
    )

    while (ready.isNotEmpty()) {
        val u = ready.removeFirst()
        emitted += u
        done += u

        val outgoing = def.edges.withIndex().filter { it.value.from == u }
        frames += GraphAlgoFrame(
            status = "Pop $u and append it to the order (${emitted.joinToString(" → ")}). Now relax its " +
                "${outgoing.size} outgoing edge${if (outgoing.size == 1) "" else "s"}.",
            nodeMarks = buildMap {
                putAll(done.associateWith { NodeMark.DONE })
                putAll(ready.associateWith { NodeMark.FRONTIER })
                put(u, NodeMark.ACTIVE)
            },
            badges = badges(),
            edgeMarks = outgoing.associate { it.index to EdgeMark.ACTIVE },
        )

        val freed = mutableListOf<String>()
        for ((_, edge) in outgoing) {
            val left = inDegree.getValue(edge.to) - 1
            inDegree[edge.to] = left
            if (left == 0) {
                ready += edge.to
                freed += edge.to
            }
        }

        if (freed.isNotEmpty()) {
            frames += GraphAlgoFrame(
                status = "${freed.joinToString(" and ")} now ${if (freed.size == 1) "has" else "have"} in-degree 0 " +
                    "— every dependency satisfied, so ${if (freed.size == 1) "it joins" else "they join"} the queue.",
                nodeMarks = buildMap {
                    putAll(done.associateWith { NodeMark.DONE })
                    putAll(ready.associateWith { NodeMark.FRONTIER })
                    putAll(freed.associateWith { NodeMark.UPDATED })
                },
                badges = badges(),
                edgeMarks = outgoing.associate { it.index to EdgeMark.ACCEPTED },
            )
        }
    }

    frames += GraphAlgoFrame(
        status = "Order: ${emitted.joinToString(" → ")}. All ${def.ids.size} nodes were emitted, so the graph is " +
            "acyclic — had the queue emptied early, the leftovers would have been sitting on a cycle.",
        nodeMarks = def.ids.associateWith { NodeMark.DONE },
        badges = emitted.withIndex().associate { (i, id) -> id to "#${i + 1}" },
        edgeMarks = def.edges.indices.associateWith { EdgeMark.ACCEPTED },
    )

    return frames
}

private fun maxFlowFrames(): List<GraphAlgoFrame> {
    val def = flowGraph
    val frames = mutableListOf<GraphAlgoFrame>()
    val capacity = def.edges.map { it.weight ?: 0 }.toMutableList()
    val flow = MutableList(def.edges.size) { 0 }

    fun edgeIndex(from: String, to: String) =
        def.edges.indexOfFirst { it.from == from && it.to == to }

    fun residualLine() = def.edges.indices.joinToString("  ") { i ->
        "${def.edges[i].from}→${def.edges[i].to} ${capacity[i] - flow[i]}/${capacity[i]}"
    }

    frames += GraphAlgoFrame(
        status = "Every edge starts empty. Labels are capacities; the goal is to push as much as possible from " +
            "S to T. Residual now: ${residualLine()}.",
        nodeMarks = mapOf("S" to NodeMark.FRONTIER, "T" to NodeMark.FRONTIER),
    )

    // Three augmenting paths, the last one only reachable because of the A→B edge.
    val paths = listOf(
        listOf("S", "A", "T"),
        listOf("S", "B", "T"),
        listOf("S", "A", "B", "T"),
    )

    var total = 0
    for ((round, path) in paths.withIndex()) {
        val indices = path.zipWithNext().map { (a, b) -> edgeIndex(a, b) }
        val bottleneck = indices.minOf { capacity[it] - flow[it] }

        frames += GraphAlgoFrame(
            status = "Augmenting path ${round + 1}: ${path.joinToString(" → ")}. The narrowest edge on it has " +
                "$bottleneck unit${if (bottleneck == 1) "" else "s"} of spare capacity, so that is all this path can carry.",
            nodeMarks = path.associateWith { NodeMark.ACTIVE },
            edgeMarks = indices.associateWith { EdgeMark.ACTIVE },
            badges = mapOf("S" to "source", "T" to "sink"),
        )

        indices.forEach { flow[it] += bottleneck }
        total += bottleneck

        frames += GraphAlgoFrame(
            status = "Push $bottleneck along it — total flow is now $total. Each unit pushed also opens $bottleneck " +
                "unit${if (bottleneck == 1) "" else "s"} of backward residual, which is what lets a later path " +
                "reroute an earlier decision. Residual: ${residualLine()}.",
            nodeMarks = path.associateWith { NodeMark.UPDATED },
            edgeMarks = indices.associateWith { EdgeMark.ACCEPTED },
            badges = mapOf("T" to "flow $total"),
        )
    }

    val saturated = def.edges.indices.filter { capacity[it] - flow[it] == 0 }
    frames += GraphAlgoFrame(
        status = "No path from S to T has spare capacity left, so the flow of $total is maximum. The saturated " +
            "edges S→B, A→T and A→B form the minimum cut — same number, read from the other side.",
        nodeMarks = def.ids.associateWith { NodeMark.DONE },
        edgeMarks = saturated.associateWith { EdgeMark.REJECTED },
        badges = mapOf("T" to "max flow $total"),
    )

    return frames
}

private fun articulationPointsFrames(): List<GraphAlgoFrame> {
    val def = cutGraph
    val frames = mutableListOf<GraphAlgoFrame>()
    val adj = def.ids.associateWith { id ->
        def.edges.mapNotNull {
            when (id) {
                it.from -> it.to
                it.to -> it.from
                else -> null
            }
        }
    }

    val disc = mutableMapOf<String, Int>()
    val low = mutableMapOf<String, Int>()
    val cuts = mutableSetOf<String>()
    val bridges = mutableListOf<Pair<String, String>>()
    var timer = 0

    fun edgeIndex(a: String, b: String) = def.edges.indexOfFirst {
        (it.from == a && it.to == b) || (it.from == b && it.to == a)
    }

    fun badges() = def.ids.associateWith { id ->
        if (id in disc) "${disc[id]}/${low[id]}" else "·"
    }

    frames += GraphAlgoFrame(
        status = "Each node will be stamped disc/low: the time DFS first reached it, and the earliest node its " +
            "subtree can climb back to using one back edge.",
        badges = badges(),
    )

    fun dfs(u: String, parent: String?) {
        disc[u] = timer
        low[u] = timer
        timer++
        frames += GraphAlgoFrame(
            status = "Visit $u at time ${disc[u]}. low[$u] starts equal to its own discovery time.",
            nodeMarks = disc.keys.associateWith { if (it == u) NodeMark.ACTIVE else NodeMark.FRONTIER },
            badges = badges(),
        )

        var children = 0
        for (v in adj.getValue(u)) {
            if (v == parent) continue
            val index = edgeIndex(u, v)
            if (v in disc) {
                if (disc.getValue(v) < low.getValue(u)) {
                    low[u] = disc.getValue(v)
                    frames += GraphAlgoFrame(
                        status = "$u → $v is a back edge to an ancestor discovered at ${disc[v]}, so low[$u] drops " +
                            "to ${low[u]} — $u's subtree can bypass its parent.",
                        nodeMarks = mapOf(u to NodeMark.ACTIVE, v to NodeMark.UPDATED),
                        badges = badges(),
                        edgeMarks = mapOf(index to EdgeMark.ACTIVE),
                    )
                }
                continue
            }

            children++
            dfs(v, u)

            if (low.getValue(v) < low.getValue(u)) low[u] = low.getValue(v)

            val isBridge = low.getValue(v) > disc.getValue(u)
            val isCut = parent != null && low.getValue(v) >= disc.getValue(u)
            if (isBridge) bridges += u to v
            if (isCut) cuts += u

            frames += GraphAlgoFrame(
                status = buildString {
                    append("Back at $u after $v: low[$u] = ${low[u]}, low[$v] = ${low[v]}, disc[$u] = ${disc[u]}. ")
                    when {
                        isBridge && isCut -> append("low[$v] > disc[$u], so $u–$v is a bridge and $u is a cut vertex.")
                        isBridge -> append("low[$v] > disc[$u], so $u–$v is a bridge.")
                        isCut -> append("low[$v] ≥ disc[$u], so $v's subtree cannot escape past $u — $u is a cut vertex.")
                        else -> append("low[$v] < disc[$u]: $v's subtree reaches above $u, so nothing is cut here.")
                    }
                },
                nodeMarks = buildMap {
                    putAll(disc.keys.associateWith { NodeMark.FRONTIER })
                    put(u, NodeMark.ACTIVE)
                    putAll(cuts.associateWith { NodeMark.DONE })
                },
                badges = badges(),
                edgeMarks = mapOf(index to if (isBridge) EdgeMark.REJECTED else EdgeMark.ACCEPTED),
            )
        }

        if (parent == null && children > 1) {
            cuts += u
            frames += GraphAlgoFrame(
                status = "$u is the DFS root with $children separate children, which is the root's own rule for " +
                    "being a cut vertex.",
                nodeMarks = cuts.associateWith { NodeMark.DONE },
                badges = badges(),
            )
        }
    }

    dfs("A", null)

    frames += GraphAlgoFrame(
        status = "Cut vertices: ${cuts.sorted().joinToString(", ")}. Bridge: " +
            bridges.joinToString(", ") { "${it.first}–${it.second}" } +
            ". Removing either triangle's link node splits the graph; the edges inside a triangle are never " +
            "bridges because the third side routes around them.",
        nodeMarks = def.ids.associateWith { if (it in cuts) NodeMark.DONE else NodeMark.IDLE },
        badges = badges(),
        edgeMarks = bridges.associate { edgeIndex(it.first, it.second) to EdgeMark.REJECTED },
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

private val graphAlgoConfigs = mapOf(
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
        def = dagGraph,
        legend = listOf(
            NodeMarkColors.getValue(NodeMark.ACTIVE) to "Popped",
            NodeMarkColors.getValue(NodeMark.FRONTIER) to "Ready",
            NodeMarkColors.getValue(NodeMark.DONE) to "Emitted",
        ),
        build = ::topologicalSortFrames,
    ),
    "max_flow" to GraphAlgoConfig(
        intro = "Ford-Fulkerson with BFS-chosen paths. Edge labels are capacities; the third augmenting path can " +
            "only exist because the first two left residual capacity behind.",
        def = flowGraph,
        legend = listOf(
            NodeMarkColors.getValue(NodeMark.ACTIVE) to "On path",
            EdgeMarkColors.getValue(EdgeMark.ACCEPTED) to "Flow pushed",
            EdgeMarkColors.getValue(EdgeMark.REJECTED) to "Saturated (min cut)",
        ),
        build = ::maxFlowFrames,
    ),
    "articulation_points" to GraphAlgoConfig(
        intro = "One DFS, badges showing disc/low per node. The ≥ test marks cut vertices, the strict > test " +
            "marks the single bridge holding the two triangles together.",
        def = cutGraph,
        legend = listOf(
            NodeMarkColors.getValue(NodeMark.ACTIVE) to "Current",
            NodeMarkColors.getValue(NodeMark.DONE) to "Cut vertex",
            EdgeMarkColors.getValue(EdgeMark.REJECTED) to "Bridge",
        ),
        build = ::articulationPointsFrames,
    ),
    "bellman_ford" to GraphAlgoConfig(
        intro = "Bellman–Ford on a directed graph with negative edges. Badges show the current distance from A; " +
            "watch a node that already looks settled get corrected later — the reason Dijkstra breaks here.",
        def = negativeWeightGraph,
        legend = shortestPathLegend,
        build = ::bellmanFordFrames,
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
        legend = mstLegend,
        build = ::kruskalFrames,
    ),
    "prims_mst" to GraphAlgoConfig(
        intro = "Prim on the same graph as Kruskal. It never sorts: it grows one tree, repeatedly taking the " +
            "cheapest edge that crosses out of it. Same total weight, different order of discovery.",
        def = mstGraph,
        legend = mstLegend,
        build = ::primFrames,
    ),
    "tarjans_algorithm" to GraphAlgoConfig(
        intro = "Tarjan's SCC algorithm. Badges read index/low-link; a component pops off the stack the moment a " +
            "node's low-link equals its own index.",
        def = sccGraph,
        legend = sccLegend,
        build = ::tarjanFrames,
    ),
    "kosarajus_algorithm" to GraphAlgoConfig(
        intro = "Kosaraju's two-pass SCC algorithm: finish times on the original graph, then DFS the transpose in " +
            "reverse finish order. Arrows flip when the second pass starts.",
        def = sccGraph,
        legend = sccLegend,
        build = ::kosarajuFrames,
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
)

private fun graphAlgoConfigFor(topicId: String): GraphAlgoConfig =
    graphAlgoConfigs[topicId] ?: graphAlgoConfigs.getValue("bellman_ford")

// ── UI ───────────────────────────────────────────────────────────────────────

@Composable
fun GraphAlgorithmSection(topicId: String) {
    val config = remember(topicId) { graphAlgoConfigFor(topicId) }
    val frames = remember(config) { config.build() }
    val playback = rememberPlaybackState(key = config, stepCount = frames.size, initialSpeedMs = 700f)
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

            GraphAlgoCanvas(def = config.def, frame = frame)

            if (frame.matrix != null) {
                DistanceMatrix(ids = config.def.ids, frame = frame)
            }

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
                config.legend.forEach { (color, label) -> GraphAlgoLegend(color, label) }
            }

            PlaybackTransport(playback)
        }
    }
}

@Composable
private fun GraphAlgoLegend(color: Color, label: String) {
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
private fun GraphAlgoCanvas(def: GraphDef, frame: GraphAlgoFrame) {
    val textMeasurer = rememberTextMeasurer()
    val nodeLabelStyle = TextStyle(color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    val weightStyle = TextStyle(color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    val badgeStyle = TextStyle(color = MaterialTheme.colorScheme.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    val surfaceTint = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    val nodeStroke = MaterialTheme.colorScheme.surface

    // Anti-parallel pairs (A→B alongside B→A) would draw on top of each other; the second one is
    // nudged off the centre line so both arrowheads and both weights stay readable.
    val hasReverse = remember(def) {
        def.edges.map { e -> def.edges.any { it !== e && it.from == e.to && it.to == e.from } }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp)
            .background(surfaceTint, RoundedCornerShape(14.dp))
            .padding(6.dp),
    ) {
        Canvas(modifier = Modifier.fillMaxWidth().height(250.dp)) {
            val radius = 17.dp.toPx()
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
                val mark = frame.edgeMarks[index] ?: EdgeMark.IDLE
                val color = EdgeMarkColors.getValue(mark)
                val width = if (mark == EdgeMark.IDLE) 2f else 5f

                drawLine(color = color, start = start, end = end, strokeWidth = width)

                if (edge.directed && !frame.undirected) {
                    val head = 9.dp.toPx()
                    listOf(2.6, -2.6).forEach { spread ->
                        val angle = kotlin.math.atan2(unit.y, unit.x) + spread
                        drawLine(
                            color = color,
                            start = end,
                            end = Offset(end.x + head * cos(angle).toFloat(), end.y + head * sin(angle).toFloat()),
                            strokeWidth = width,
                        )
                    }
                }

                edge.weight?.takeUnless { frame.hideWeights }?.let { weight ->
                    val layout = textMeasurer.measure(weight.toString(), weightStyle)
                    val mid = Offset((start.x + end.x) / 2f, (start.y + end.y) / 2f) + offset
                    drawText(
                        layout,
                        topLeft = Offset(mid.x - layout.size.width / 2f, mid.y - layout.size.height / 2f),
                    )
                }
            }

            def.nodes.forEach { node ->
                val center = positions.getValue(node.id)
                val group = frame.groups[node.id]
                val color = when {
                    frame.nodeMarks[node.id] != null -> NodeMarkColors.getValue(frame.nodeMarks.getValue(node.id))
                    group != null -> GroupColors[group % GroupColors.size]
                    else -> NodeMarkColors.getValue(NodeMark.IDLE)
                }
                drawCircle(color = color, radius = radius, center = center)
                drawCircle(color = nodeStroke, radius = radius, center = center, style = Stroke(width = 2.5f))

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
