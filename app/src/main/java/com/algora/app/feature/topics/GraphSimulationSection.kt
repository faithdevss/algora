package com.algora.app.feature.topics

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.algora.app.core.ui.theme.SimColors
import kotlin.math.hypot

private val demoNodes = listOf("A", "B", "C", "D", "E", "F")

// Tree-shaped on purpose: BFS/DFS order is only readable if the picture already looks like the
// levels the algorithm walks. The one extra edge (D-F) closes a cycle so the "Seen" step still
// happens, and it is the only line in the default graph that is not straight parent-to-child.
private val demoEdges = listOf(
    "A" to "B", "A" to "C", "A" to "D", "C" to "E", "C" to "F", "D" to "F",
)
private const val TRAVERSAL_STEP_MS = 600L
private const val MAX_NODES = 10

private enum class NodeState { IDLE, CURRENT, VISITED, QUEUE }

private val NodeStateColors = mapOf(
    // Neutral on purpose: a colour on an untouched node reads as a state the algorithm assigned.
    NodeState.IDLE to Color(0xFFCBD0DA),
    NodeState.CURRENT to Color(0xFFFACC15),
    NodeState.VISITED to Color(0xFFF97316),
    NodeState.QUEUE to Color(0xFF3B82F6),
)

private val EdgeIdleColor = Color(0xFFCBD0DA)
private val PathColor = Color(0xFFF97316)
private val ActiveEdgeColor = Color(0xFFFACC15)

/** Normalized undirected edge id, so A-B and B-A share one walk counter. */
private typealias EdgeKey = Pair<String, String>

private fun edgeKey(a: String, b: String): EdgeKey = if (a <= b) a to b else b to a

/** Same hue, denser each time the walk crosses the edge again. */
private fun pathAlpha(walks: Int): Float = when (walks) {
    1 -> 0.5f
    2 -> 0.72f
    3 -> 0.88f
    else -> 1f
}

// Widths are in dp, not raw canvas pixels: a fixed 3.5px stroke renders as a ~1dp hairline on a
// 3x-density screen, which is what made the edges nearly invisible on device.
private fun pathWidth(walks: Int): Dp = when (walks) {
    1 -> 2.5.dp
    2 -> 3.dp
    3 -> 3.5.dp
    else -> 4.dp
}

private val IdleEdgeWidth = 2.dp
private val NodeRingWidth = 2.dp
private val CurrentNodeRingWidth = 3.dp

private data class GraphSnapshot(
    val nodeState: Map<String, NodeState>,
    val edgeWalks: Map<EdgeKey, Int>,
    val activeEdge: EdgeKey?,
    val status: String,
)

private fun buildAdjacency(nodes: List<String>, edges: List<Pair<String, String>>): Map<String, List<String>> {
    val adjacency = nodes.associateWith { mutableListOf<String>() }
    for ((a, b) in edges) {
        adjacency[a]?.add(b)
        adjacency[b]?.add(a)
    }
    return adjacency.mapValues { it.value.sorted() }
}

/**
 * Rows of nodes by BFS depth from [root]. Drawn top-down this puts every discovery edge on a
 * downward diagonal instead of a chord across a circle, which is what made the old ring layout
 * unreadable. Children land in their parent's discovery order, so a tree draws with no crossings.
 * Anything unreachable from [root] gets one extra row at the bottom rather than being dropped.
 */
private fun layerRows(nodes: List<String>, adjacency: Map<String, List<String>>, root: String): List<List<String>> {
    if (nodes.isEmpty()) return emptyList()
    val rows = mutableListOf<List<String>>()
    val seen = mutableSetOf<String>()
    var frontier = listOf(if (root in nodes) root else nodes.first())
    seen += frontier
    while (frontier.isNotEmpty()) {
        rows += frontier
        val next = mutableListOf<String>()
        for (node in frontier) {
            for (neighbor in adjacency[node].orEmpty()) {
                if (seen.add(neighbor)) next += neighbor
            }
        }
        frontier = next
    }
    val orphans = nodes.filter { it !in seen }
    if (orphans.isNotEmpty()) rows += orphans
    return rows
}

private fun bfsSnapshots(nodes: List<String>, adjacency: Map<String, List<String>>, start: String): List<GraphSnapshot> {
    val state = nodes.associateWith { NodeState.IDLE }.toMutableMap()
    val walks = mutableMapOf<EdgeKey, Int>()
    val snapshots = mutableListOf<GraphSnapshot>()
    fun snap(status: String, active: EdgeKey? = null) =
        snapshots.add(GraphSnapshot(state.toMap(), walks.toMap(), active, status))

    val visited = mutableSetOf(start)
    state[start] = NodeState.QUEUE
    snap("Enqueue $start")

    val queue = ArrayDeque(listOf(start))
    while (queue.isNotEmpty()) {
        val node = queue.removeFirst()
        state[node] = NodeState.CURRENT
        snap("Visit $node")
        for (neighbor in adjacency[node].orEmpty()) {
            // Every incident edge is walked, whether or not it discovers a new node — the
            // re-walks are what build up the density shading.
            val key = edgeKey(node, neighbor)
            walks[key] = (walks[key] ?: 0) + 1
            if (visited.add(neighbor)) {
                state[neighbor] = NodeState.QUEUE
                snap("Enqueue $neighbor", key)
                queue.addLast(neighbor)
            } else {
                snap("Seen $neighbor", key)
            }
        }
        state[node] = NodeState.VISITED
    }
    snap("Complete")
    return snapshots
}

private fun dfsSnapshots(nodes: List<String>, adjacency: Map<String, List<String>>, start: String): List<GraphSnapshot> {
    val state = nodes.associateWith { NodeState.IDLE }.toMutableMap()
    val walks = mutableMapOf<EdgeKey, Int>()
    val snapshots = mutableListOf<GraphSnapshot>()
    fun snap(status: String, active: EdgeKey? = null) =
        snapshots.add(GraphSnapshot(state.toMap(), walks.toMap(), active, status))

    val visited = mutableSetOf<String>()
    val stack = ArrayDeque(listOf(start))
    state[start] = NodeState.QUEUE
    snap("Push $start")

    while (stack.isNotEmpty()) {
        val node = stack.removeLast()
        if (!visited.add(node)) continue
        state[node] = NodeState.CURRENT
        snap("Visit $node")
        state[node] = NodeState.VISITED
        for (neighbor in adjacency[node].orEmpty().reversed()) {
            val key = edgeKey(node, neighbor)
            walks[key] = (walks[key] ?: 0) + 1
            if (neighbor in visited) {
                snap("Seen $neighbor", key)
            } else {
                stack.addLast(neighbor)
                state[neighbor] = NodeState.QUEUE
                snap("Push $neighbor", key)
            }
        }
    }
    snap("Complete")
    return snapshots
}

/** BFS and DFS have their own topic pages; only the general graph page needs both buttons. */
private enum class Traversal(val label: String) { BFS("BFS"), DFS("DFS") }

private fun traversalsFor(topicId: String): List<Traversal> = when (topicId) {
    "bfs" -> listOf(Traversal.BFS)
    "dfs" -> listOf(Traversal.DFS)
    else -> Traversal.entries
}

/** Snapshot of the graph + choice of algorithm at the moment Play was pressed. [runId] forces a
 *  fresh [PlaybackState] even if the same algorithm is re-run against an unchanged graph. */
private data class GraphRunRequest(
    val kind: Traversal,
    val nodes: List<String>,
    val edges: List<Pair<String, String>>,
    val startNode: String,
    val runId: Int,
)

@Composable
fun GraphSimulationSection(topicId: String) {
    val traversals = remember(topicId) { traversalsFor(topicId) }
    var nodes by remember { mutableStateOf(demoNodes) }
    var edges by remember { mutableStateOf(demoEdges) }
    var idleStatus by remember { mutableStateOf("Idle") }
    var fromNode by remember { mutableStateOf(nodes.first()) }
    var toNode by remember { mutableStateOf(nodes[1]) }
    var startNode by remember { mutableStateOf(nodes.first()) }
    var runRequest by remember { mutableStateOf<GraphRunRequest?>(null) }
    var runCounter by remember { mutableStateOf(0) }

    fun resetGraph(newStatus: String) {
        runRequest = null
        idleStatus = newStatus
    }

    fun runTraversal(kind: Traversal) {
        runCounter++
        runRequest = GraphRunRequest(kind, nodes, edges, startNode, runCounter)
    }

    // Snapshots are a pure function of the request, recomputed only when a new run starts —
    // editing the graph mid-run doesn't retroactively change a walk already in flight.
    val snapshots = remember(runRequest) {
        runRequest?.let { req ->
            val adjacency = buildAdjacency(req.nodes, req.edges)
            when (req.kind) {
                Traversal.BFS -> bfsSnapshots(req.nodes, adjacency, req.startNode)
                Traversal.DFS -> dfsSnapshots(req.nodes, adjacency, req.startNode)
            }
        } ?: emptyList()
    }
    val playback = runRequest?.let { req ->
        rememberPlaybackState(key = req, stepCount = snapshots.size, initialSpeedMs = TRAVERSAL_STEP_MS.toFloat())
    }
    // PlaybackState always starts paused; a freshly launched run should autoplay immediately.
    LaunchedEffect(runRequest) { playback?.playing = true }

    val current = playback?.let { snapshots.getOrNull(it.index) }
    val nodeState = current?.nodeState ?: nodes.associateWith { NodeState.IDLE }
    val edgeWalks = current?.edgeWalks ?: emptyMap()
    val activeEdge = current?.activeEdge
    val status = current?.status ?: idleStatus

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
                GraphStat(label = "Nodes", value = nodes.size.toString(), modifier = Modifier.weight(1f))
                GraphStat(label = "Edges", value = edges.size.toString(), modifier = Modifier.weight(1f))
                GraphStat(label = "Status", value = status, modifier = Modifier.weight(1f), valueColor = MaterialTheme.colorScheme.primary)
            }

            GraphCanvas(
                nodes = nodes,
                edges = edges,
                startNode = startNode,
                nodeState = nodeState,
                edgeWalks = edgeWalks,
                activeEdge = activeEdge,
            )

            // FlowRow, not Row: six entries overrun a phone width on one line.
            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                val legend = listOf(
                    NodeState.IDLE to "Idle",
                    NodeState.CURRENT to "Current",
                    NodeState.VISITED to "Visited",
                    NodeState.QUEUE to "Queue",
                )
                legend.forEach { (state, label) ->
                    LegendEntry(label = label) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(NodeStateColors.getValue(state), CircleShape),
                        )
                    }
                }
                LegendEntry(label = "Walked") {
                    Box(
                        modifier = Modifier
                            .width(14.dp)
                            .height(3.dp)
                            .background(PathColor.copy(alpha = pathAlpha(1)), RoundedCornerShape(2.dp)),
                    )
                }
                LegendEntry(label = "Re-walked") {
                    Box(
                        modifier = Modifier
                            .width(14.dp)
                            .height(5.dp)
                            .background(PathColor, RoundedCornerShape(2.dp)),
                    )
                }
            }

            Button(
                onClick = {
                    if (nodes.size < MAX_NODES) {
                        val nextLetter = ('A' + nodes.size).toString()
                        nodes = nodes + nextLetter
                        resetGraph("Idle")
                    } else {
                        resetGraph("Graph is full ($MAX_NODES max)")
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = SimColors.Blue, contentColor = Color.White),
                modifier = Modifier.fillMaxWidth().padding(bottom = 9.dp),
            ) { Text("+ Add Node") }

            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 9.dp)) {
                NodeDropdown(label = "From", selected = fromNode, options = nodes, onSelected = { fromNode = it }, modifier = Modifier.weight(1f))
                Box(modifier = Modifier.width(9.dp))
                NodeDropdown(label = "To", selected = toNode, options = nodes, onSelected = { toNode = it }, modifier = Modifier.weight(1f))
                Box(modifier = Modifier.width(9.dp))
                Button(
                    onClick = {
                        val linkStatus = when {
                            fromNode == toNode -> "Pick two different nodes"
                            edges.any { (a, b) -> (a == fromNode && b == toNode) || (a == toNode && b == fromNode) } -> "Edge exists"
                            else -> {
                                edges = edges + (fromNode to toNode)
                                "Linked"
                            }
                        }
                        resetGraph(linkStatus)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SimColors.Violet, contentColor = Color.White),
                ) { Text("Link") }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                NodeDropdown(label = "Start", selected = startNode, options = nodes, onSelected = { startNode = it }, modifier = Modifier.weight(1.2f))
                if (traversals.size == 1) {
                    // One algorithm on this page, so the button needs no name — it just plays.
                    val only = traversals.first()
                    TransportButton(
                        icon = Icons.Filled.PlayArrow,
                        contentDescription = "Play ${only.label}",
                        color = SimColors.Green,
                        modifier = Modifier.weight(1f),
                        iconSize = 30.dp,
                        onClick = { runTraversal(only) },
                    )
                } else {
                    traversals.forEach { traversal ->
                        RunTraversalButton(
                            label = traversal.label,
                            color = if (traversal == Traversal.BFS) SimColors.Green else SimColors.Amber,
                            modifier = Modifier.weight(1f),
                            onClick = { runTraversal(traversal) },
                        )
                    }
                }
                TransportButton(
                    icon = Icons.Filled.Refresh,
                    contentDescription = "Reset",
                    color = SimColors.Grey,
                    modifier = Modifier.weight(0.7f),
                    onClick = { resetGraph("Idle") },
                )
            }

            if (playback != null) {
                PlaybackTransport(state = playback)
            }
        }
    }
}

/** Same height and tight padding as [TransportButton], but named — the graph page runs both walks. */
@Composable
private fun RunTraversalButton(label: String, color: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = Color.White),
        shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(horizontal = 6.dp),
        modifier = modifier.height(40.dp),
    ) {
        Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 2.dp))
    }
}

@Composable
private fun LegendEntry(label: String, swatch: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        swatch()
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 5.dp),
        )
    }
}

@Composable
private fun GraphStat(label: String, value: String, modifier: Modifier = Modifier, valueColor: Color = MaterialTheme.colorScheme.onSurface) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium, color = valueColor)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun NodeDropdown(label: String, selected: String, options: List<String>, onSelected: (String) -> Unit, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        Surface(
            modifier = Modifier.fillMaxWidth().clickable { expanded = true },
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(selected, style = MaterialTheme.typography.bodyLarge)
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(text = { Text(option) }, onClick = { onSelected(option); expanded = false })
            }
        }
    }
}

@Composable
private fun GraphCanvas(
    nodes: List<String>,
    edges: List<Pair<String, String>>,
    startNode: String,
    nodeState: Map<String, NodeState>,
    edgeWalks: Map<EdgeKey, Int>,
    activeEdge: EdgeKey?,
) {
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = TextStyle(
        color = MaterialTheme.colorScheme.onSurface,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
    )
    val rows = remember(nodes, edges, startNode) {
        layerRows(nodes, buildAdjacency(nodes, edges), startNode)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
            .padding(6.dp),
    ) {
        Canvas(modifier = Modifier.fillMaxWidth().height(260.dp)) {
            // Same trick the static figures use: size the node off the canvas rather than fixing a
            // pixel radius, so the circle stays big enough to read its label as rows and columns grow.
            val maxRowSize = (rows.maxOfOrNull { it.size } ?: 1).coerceAtLeast(1)
            // Cap and floor in dp, not raw pixels: a 62px cap was a 62dp node on a 1x screen and a
            // 21dp one at 3x. 16.dp matches the graph-algorithm canvas.
            val radius = minOf(
                size.height / (rows.size.coerceAtLeast(2) * 2.8f),
                size.width / (maxRowSize * 3.1f),
                16.dp.toPx(),
            ).coerceAtLeast(11.dp.toPx())
            val marginX = radius + 10f
            val marginY = radius + 8f
            val usableWidth = size.width - marginX * 2
            // Held in locals: inside buildMap's lambda `size` would resolve to the map's size.
            val canvasHeight = size.height
            val rowGap = if (rows.size > 1) (canvasHeight - marginY * 2) / (rows.size - 1) else 0f

            val positions = buildMap {
                rows.forEachIndexed { rowIndex, row ->
                    val y = if (rows.size > 1) marginY + rowIndex * rowGap else canvasHeight / 2f
                    val slot = usableWidth / row.size
                    row.forEachIndexed { column, id ->
                        put(id, Offset(marginX + slot * (column + 0.5f), y))
                    }
                }
            }

            edges.forEach { (a, b) ->
                val pa = positions[a]
                val pb = positions[b]
                if (pa == null || pb == null) return@forEach

                // Stop the line at each rim instead of under the circle: a thick stroke running
                // through the node washes out its fill and hides the label.
                val dx = pb.x - pa.x
                val dy = pb.y - pa.y
                val length = hypot(dx, dy).coerceAtLeast(1f)
                val ux = dx / length
                val uy = dy / length
                val start = Offset(pa.x + ux * radius, pa.y + uy * radius)
                val end = Offset(pb.x - ux * radius, pb.y - uy * radius)

                val key = edgeKey(a, b)
                val walks = edgeWalks[key] ?: 0
                drawLine(
                    color = EdgeIdleColor,
                    start = start,
                    end = end,
                    strokeWidth = IdleEdgeWidth.toPx(),
                    cap = StrokeCap.Round,
                )
                if (walks > 0) {
                    drawLine(
                        color = PathColor.copy(alpha = pathAlpha(walks)),
                        start = start,
                        end = end,
                        strokeWidth = pathWidth(walks).toPx(),
                        cap = StrokeCap.Round,
                    )
                }
                if (key == activeEdge) {
                    drawLine(
                        color = ActiveEdgeColor,
                        start = start,
                        end = end,
                        strokeWidth = (pathWidth(walks) + 1.dp).toPx(),
                        cap = StrokeCap.Round,
                    )
                }
            }

            nodes.forEach { id ->
                val center = positions[id] ?: return@forEach
                val state = nodeState[id] ?: NodeState.IDLE
                val color = NodeStateColors.getValue(state)
                // Translucent disc under a solid ring, matching the static figure cards: the state
                // colour still reads at a glance but the label sits on the card background, not on
                // a saturated fill.
                drawCircle(color = color.copy(alpha = 0.28f), radius = radius, center = center)
                drawCircle(
                    color = color,
                    radius = radius,
                    center = center,
                    style = Stroke(
                        width = if (state == NodeState.CURRENT) {
                            CurrentNodeRingWidth.toPx()
                        } else {
                            NodeRingWidth.toPx()
                        },
                    ),
                )

                val layout = textMeasurer.measure(id, labelStyle)
                drawText(
                    layout,
                    topLeft = Offset(center.x - layout.size.width / 2f, center.y - layout.size.height / 2f),
                )
            }
        }
    }
}
