package com.algora.app.feature.topics

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.algora.app.core.ui.theme.IBMPlexMono
import com.algora.app.core.ui.theme.LocalDarkTheme
import com.algora.app.core.ui.theme.SimColors
import kotlin.math.hypot

// An editable graph with BFS/DFS playback, laid out as screen 05 of docs/ios-design/Simulations
// iOS.html. One step per node taken off the frontier, so the caption can say what the step did
// ("Dequeue C. Its neighbours E and F are new…"). The frontier itself — the queue or the stack — is
// drawn as data under the stage, since it is the thing BFS/DFS teach.
//
// Explore mode: tap a node to make it the start. Edit mode (nav bar): tap empty space to add a node,
// drag from one node to another to link them, long-press a node to delete it.

private val demoNodes = listOf("A", "B", "C", "D", "E", "F")
private val demoEdges = listOf("A" to "B", "A" to "C", "A" to "D", "C" to "E", "C" to "F", "D" to "F")

/** Positions in a unit square, matching the design's layout. */
private val demoPositions = mapOf(
    "A" to Offset(0.5f, 0.12f), "B" to Offset(0.2f, 0.5f), "C" to Offset(0.5f, 0.5f),
    "D" to Offset(0.8f, 0.5f), "E" to Offset(0.32f, 0.88f), "F" to Offset(0.68f, 0.88f),
)
private const val MAX_NODES = 10
private val NodeRadius = 22.dp

private enum class NodeState { Idle, Queued, Current, Visited }
private enum class Traversal(val label: String) { BFS("BFS"), DFS("DFS") }

private fun traversalsFor(topicId: String): List<Traversal> = when (topicId) {
    "bfs" -> listOf(Traversal.BFS)
    "dfs" -> listOf(Traversal.DFS)
    else -> Traversal.entries
}

private typealias EdgeKey = Pair<String, String>

private fun edgeKey(a: String, b: String): EdgeKey = if (a <= b) a to b else b to a

private class GraphStep(
    val state: Map<String, NodeState>,
    /** Edges that discovered a node, drawn solid green. */
    val tree: Set<EdgeKey>,
    /** Edges this step discovered along, drawn dashed blue. */
    val fresh: Set<EdgeKey>,
    val frontier: List<String>,
    val visited: List<String>,
    val status: String,
)

private fun listed(xs: List<String>): String = when (xs.size) {
    0 -> ""
    1 -> xs[0]
    2 -> "${xs[0]} and ${xs[1]}"
    else -> xs.dropLast(1).joinToString(", ") + " and " + xs.last()
}

private fun adjacency(nodes: List<String>, edges: List<Pair<String, String>>): Map<String, List<String>> {
    val adj = nodes.associateWith { mutableListOf<String>() }
    edges.forEach { (a, b) -> adj[a]?.add(b); adj[b]?.add(a) }
    return adj.mapValues { it.value.sorted() }
}

private fun traverse(t: Traversal, nodes: List<String>, edges: List<Pair<String, String>>, start: String): List<GraphStep> {
    if (start !in nodes) return emptyList()
    val adj = adjacency(nodes, edges)
    val state = nodes.associateWith { NodeState.Idle }.toMutableMap()
    val tree = mutableSetOf<EdgeKey>()
    val frontier = mutableListOf(start)
    val visited = mutableListOf<String>()
    val seen = mutableSetOf(start)
    val steps = mutableListOf<GraphStep>()
    val bfs = t == Traversal.BFS
    val noun = if (bfs) "queue" else "stack"
    state[start] = NodeState.Queued
    fun snap(status: String, fresh: Set<EdgeKey> = emptySet()) {
        steps += GraphStep(state.toMap(), tree.toSet(), fresh, frontier.toList(), visited.toList(), status)
    }
    snap(
        "Start at $start. It goes into the $noun. " +
            if (bfs) "BFS explores in rings: every node one edge away, then two." else "DFS follows one path as deep as it goes, then backs up.",
    )

    while (frontier.isNotEmpty()) {
        val node = if (bfs) frontier.removeAt(0) else frontier.removeAt(frontier.lastIndex)
        state[node] = NodeState.Current
        val new = adj[node].orEmpty().filter { it !in seen }
        val pushed = if (bfs) new else new.reversed()
        val fresh = mutableSetOf<EdgeKey>()
        pushed.forEach { nb ->
            seen += nb
            state[nb] = NodeState.Queued
            frontier += nb
            fresh += edgeKey(node, nb)
        }
        val status = when {
            new.isEmpty() -> "${if (bfs) "Dequeue" else "Pop"} $node. Its neighbours are all seen already, so nothing joins the $noun. " +
                if (bfs) "The next node in line is still from the same level or the one after." else "DFS backs up to the last branch it left open."
            bfs -> "Dequeue $node. " +
                (if (new.size == 1) "Its neighbour ${new[0]} is new, so it joins" else "Its neighbours ${listed(new)} are new, so they join") +
                " the back of the queue. BFS finishes each level before starting the next."
            else -> "Pop $node. Push ${listed(new)} onto the stack; ${pushed.last()} is on top, so it is explored next. " +
                "DFS goes as deep as it can before backing up."
        }
        snap(status, fresh)
        tree += fresh
        state[node] = NodeState.Visited
        visited += node
    }
    val unreached = nodes.filter { it !in seen }
    snap(
        "The $noun is empty. ${t.label} reached ${visited.size} of ${nodes.size} nodes. " +
            if (unreached.isEmpty()) "Every node was connected to $start."
            else "${listed(unreached)} ${if (unreached.size == 1) "has" else "have"} no path from $start.",
    )
    return steps
}

private class GraphLabModel(topicId: String) {
    val traversals = traversalsFor(topicId)
    val nodes = mutableStateListOf<String>().apply { addAll(demoNodes) }
    val edges = mutableStateListOf<Pair<String, String>>().apply { addAll(demoEdges) }
    val positions = mutableStateMapOf<String, Offset>().apply { putAll(demoPositions) }
    var start by mutableStateOf("A")
        private set
    var traversal by mutableStateOf(traversals[0])
        private set
    var editing by mutableStateOf(false)
        private set
    var steps by mutableStateOf<List<GraphStep>>(emptyList())
        private set
    var playback by mutableStateOf(PlaybackState(1, 900f))
        private set

    /** Edit mode: the drag in progress from a node, for the rubber-band line. */
    var linkFrom by mutableStateOf<String?>(null)
    var linkTo by mutableStateOf<Offset?>(null)
    var notice by mutableStateOf<String?>(null)

    init { rebuild() }

    val current: GraphStep? get() = if (editing || steps.isEmpty()) null else steps[playback.index.coerceIn(0, steps.lastIndex)]

    private fun rebuild() {
        steps = traverse(traversal, nodes, edges, start)
        playback = PlaybackState(steps.size.coerceAtLeast(1), 900f)
    }

    fun chooseStart(id: String) { start = id; rebuild() }
    fun chooseTraversal(t: Traversal) { traversal = t; rebuild() }
    fun toggleEditing() { editing = !editing; linkFrom = null; linkTo = null; rebuild() }

    fun reset() {
        nodes.clear(); nodes.addAll(demoNodes)
        edges.clear(); edges.addAll(demoEdges)
        positions.clear(); positions.putAll(demoPositions)
        notice = null
        start = "A"
        rebuild()
    }

    fun nodeAt(p: Offset, size: Size, radiusPx: Float): String? = nodes.firstOrNull { id ->
        val u = positions[id] ?: return@firstOrNull false
        hypot(u.x * size.width - p.x, u.y * size.height - p.y) <= radiusPx + 8f
    }

    fun addNode(p: Offset, size: Size, radiusPx: Float) {
        if (nodes.size >= MAX_NODES) {
            notice = "No room for another node. This lab holds $MAX_NODES. Long-press one to delete it first."
            return
        }
        val id = ('A'..'Z').map { it.toString() }.firstOrNull { it !in nodes } ?: return
        val margin = radiusPx + 4f
        nodes += id
        positions[id] = Offset(
            p.x.coerceIn(margin, size.width - margin) / size.width,
            p.y.coerceIn(margin, size.height - margin) / size.height,
        )
        notice = null
        rebuild()
    }

    fun link(a: String, b: String) {
        if (a == b) return
        if (edges.any { edgeKey(it.first, it.second) == edgeKey(a, b) }) {
            notice = "$a and $b are already linked."
            return
        }
        edges += a to b
        notice = null
        rebuild()
    }

    fun delete(id: String) {
        if (nodes.size <= 1) {
            notice = "A graph needs at least one node, so the last one stays."
            return
        }
        nodes.remove(id)
        edges.removeAll { it.first == id || it.second == id }
        positions.remove(id)
        notice = null
        if (start == id) start = nodes[0]
        rebuild()
    }
}

@Composable
fun GraphSimulationSection(topicId: String) {
    val model = remember(topicId) { GraphLabModel(topicId) }
    val dock = LocalLabDock.current

    // Read here, not only inside SideEffect, so toggling it recomposes this scope and re-labels the
    // nav button.
    val editing = model.editing
    if (dock != null) {
        SideEffect {
            dock.controls = { GraphControls(model) }
            dock.navAction = LabNavAction(null, if (editing) "Done" else "Edit", model::toggleEditing)
        }
        DisposableEffect(dock) {
            onDispose {
                dock.controls = null
                dock.navAction = null
            }
        }
        Column(modifier = Modifier.fillMaxWidth()) {
            GraphCard { GraphStage(model) }
            GraphNarration(model, Modifier.padding(start = 4.dp, end = 4.dp, top = 14.dp))
        }
    } else {
        GraphCard {
            Row(modifier = Modifier.fillMaxWidth()) {
                Spacer(Modifier.weight(1f))
                Text(
                    if (model.editing) "Done" else "Edit",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = model::toggleEditing).padding(6.dp),
                )
            }
            GraphStage(model)
            GraphNarration(model, Modifier.padding(start = 4.dp, end = 4.dp, top = 14.dp))
            HorizontalDivider(modifier = Modifier.padding(top = 16.dp, bottom = 14.dp), color = MaterialTheme.colorScheme.outline)
            GraphControls(model)
        }
    }
}

@Composable
private fun GraphCard(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(modifier = Modifier.padding(12.dp)) { content() }
    }
}

// ── Stage ────────────────────────────────────────────────────────────────────

@Composable
private fun GraphStage(model: GraphLabModel) {
    val accent = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val onSurface = MaterialTheme.colorScheme.onSurface
    val dark = LocalDarkTheme.current
    val idleEdge = if (dark) Color(0xFF4A4F5C) else Color(0xFFC9CED8)
    val idleNode = if (dark) Color(0xFF3A3F4C) else Color(0xFFD5D9E1)
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontWeight = FontWeight.Bold, fontSize = 16.sp)
    val startStyle = TextStyle(fontFamily = IBMPlexMono, fontWeight = FontWeight.Medium, fontSize = 12.sp, color = muted)
    val step = model.current

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(250.dp)
                .then(
                    if (model.editing) {
                        Modifier
                            .background(accent.copy(alpha = 0.06f), RoundedCornerShape(14.dp))
                            .border(1.5.dp, accent.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                    } else {
                        Modifier
                    },
                )
                .pointerInput(model, model.editing) {
                    val r = NodeRadius.toPx()
                    val area = Size(size.width.toFloat(), size.height.toFloat())
                    if (model.editing) {
                        detectDragGestures(
                            onDragStart = { p -> model.linkFrom = model.nodeAt(p, area, r); model.linkTo = p },
                            onDragEnd = {
                                val from = model.linkFrom
                                val to = model.linkTo?.let { model.nodeAt(it, area, r) }
                                if (from != null && to != null) model.link(from, to)
                                model.linkFrom = null
                                model.linkTo = null
                            },
                            onDragCancel = { model.linkFrom = null; model.linkTo = null },
                        ) { change, _ -> if (model.linkFrom != null) model.linkTo = change.position }
                    }
                }
                .pointerInput(model, model.editing) {
                    val r = NodeRadius.toPx()
                    val area = Size(size.width.toFloat(), size.height.toFloat())
                    detectTapGestures(
                        onTap = { p ->
                            val hit = model.nodeAt(p, area, r)
                            if (model.editing) {
                                if (hit == null) model.addNode(p, area, r)
                            } else if (hit != null) {
                                model.chooseStart(hit)
                            }
                        },
                        onLongPress = { p ->
                            if (model.editing) model.nodeAt(p, area, r)?.let(model::delete)
                        },
                    )
                },
        ) {
            val r = NodeRadius.toPx()
            fun at(id: String) = model.positions[id]?.let { Offset(it.x * size.width, it.y * size.height) }
            model.edges.forEach { (a, b) ->
                val pa = at(a) ?: return@forEach
                val pb = at(b) ?: return@forEach
                val key = edgeKey(a, b)
                when {
                    step?.fresh?.contains(key) == true -> drawLine(
                        SimColors.Blue, pa, pb, 3.dp.toPx(), cap = StrokeCap.Round,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(16f, 13f)),
                    )
                    step?.tree?.contains(key) == true -> drawLine(SimColors.Green, pa, pb, 3.dp.toPx())
                    else -> drawLine(idleEdge, pa, pb, 2.dp.toPx())
                }
            }
            val from = model.linkFrom?.let(::at)
            val to = model.linkTo
            if (from != null && to != null) {
                drawLine(accent, from, to, 2.5.dp.toPx(), cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f)))
            }
            model.nodes.forEach { id ->
                val c = at(id) ?: return@forEach
                val state = step?.state?.get(id) ?: NodeState.Idle
                val (fill, text) = when (state) {
                    NodeState.Idle -> idleNode to onSurface
                    NodeState.Queued -> SimColors.Blue to Color.White
                    NodeState.Current -> SimColors.Active to Color(0xFF1A1A1A)
                    NodeState.Visited -> SimColors.Green to Color.White
                }
                val radius = if (state == NodeState.Current) r + 2.dp.toPx() else r
                if (state == NodeState.Current) {
                    drawCircle(SimColors.Active.copy(alpha = 0.35f), radius + 6.dp.toPx(), c, style = Stroke(3.dp.toPx()))
                }
                if (model.linkFrom == id) drawCircle(accent, radius + 5.dp.toPx(), c, style = Stroke(2.dp.toPx()))
                drawCircle(fill, radius, c)
                val layout = measurer.measure(id, labelStyle.copy(color = text))
                drawText(layout, topLeft = Offset(c.x - layout.size.width / 2f, c.y - layout.size.height / 2f))
                if (id == model.start && !model.editing) {
                    val s = measurer.measure("start", startStyle)
                    drawText(s, topLeft = Offset(c.x + radius + 8.dp.toPx(), c.y - radius - s.size.height / 2f + 4.dp.toPx()))
                }
            }
        }
        Row(
            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val bfs = model.traversal == Traversal.BFS
            LegendDot(SimColors.Active, if (bfs) "Dequeued" else "Popped")
            LegendDot(SimColors.Blue, if (bfs) "In queue" else "On stack")
            LegendDot(SimColors.Green, "Visited")
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).background(color, CircleShape))
        Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f), modifier = Modifier.padding(start = 6.dp))
    }
}

// ── Narration ────────────────────────────────────────────────────────────────

@Composable
private fun GraphNarration(model: GraphLabModel, modifier: Modifier) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(modifier) {
        val step = model.current
        if (model.editing) {
            LabCaption(
                "Editing the graph. Tap empty space to add a node, drag from one node to another to link them, and long-press a node to delete it.",
                Modifier.heightIn(min = 78.dp),
            )
        } else if (step != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (model.traversal == Traversal.BFS) "Queue" else "Stack", fontSize = 13.sp, color = muted, modifier = Modifier.width(48.dp))
                if (step.frontier.isEmpty()) Text("empty", fontFamily = IBMPlexMono, fontSize = 14.sp, color = muted)
                step.frontier.forEach { id ->
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(SimColors.Blue.copy(alpha = 0.25f), RoundedCornerShape(9.dp))
                            .border(1.5.dp, SimColors.Blue, RoundedCornerShape(9.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(id, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }
                Spacer(Modifier.weight(1f))
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(color = muted)) { append("visited ") }
                        withStyle(SpanStyle(fontWeight = FontWeight.Medium)) { append(step.visited.joinToString(" ").ifEmpty { "–" }) }
                    },
                    fontFamily = IBMPlexMono,
                    fontSize = 14.sp,
                    maxLines = 1,
                )
            }
            LabCaption(step.status, Modifier.padding(top = 14.dp).heightIn(min = 78.dp))
        }
    }
}

// ── Controls ─────────────────────────────────────────────────────────────────

@Composable
private fun GraphControls(model: GraphLabModel) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val dark = LocalDarkTheme.current
    var startMenu by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        model.notice?.let { LabNotice(it) }
        if (model.editing) {
            LabNotice(
                "${model.nodes.size} nodes, ${model.edges.size} edges. Tap Done in the top bar to run ${model.traversal.label} on it.",
                blocked = false,
            )
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier
                        .height(32.dp)
                        .background(SimColors.Tint, RoundedCornerShape(9.dp))
                        .padding(2.dp),
                ) {
                    model.traversals.forEach { t ->
                        val on = t == model.traversal
                        Box(
                            modifier = Modifier
                                .width(if (model.traversals.size > 1) 62.dp else 56.dp)
                                .height(28.dp)
                                .clip(RoundedCornerShape(7.dp))
                                .background(if (on && model.traversals.size > 1) (if (dark) Color(0xFF636366) else Color.White) else Color.Transparent)
                                .clickable(enabled = model.traversals.size > 1) { model.chooseTraversal(t) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(t.label, fontSize = 14.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium)
                        }
                    }
                }
                Box {
                    Row(
                        modifier = Modifier
                            .height(32.dp)
                            .clip(CircleShape)
                            .background(SimColors.Tint)
                            .clickable { startMenu = true }
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text("Start", fontSize = 14.sp, color = muted)
                        Text(model.start, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                    DropdownMenu(expanded = startMenu, onDismissRequest = { startMenu = false }) {
                        model.nodes.forEach { id ->
                            DropdownMenuItem(text = { Text(id) }, onClick = { model.chooseStart(id); startMenu = false })
                        }
                    }
                }
            }
            LabTransportBar(model.playback, model.steps.map { it.status })
        }
    }
}
