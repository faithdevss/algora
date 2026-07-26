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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.algora.app.core.ui.theme.SimColors

// ── Tree visualizer ──────────────────────────────────────────────────────────
// One player for every tree-shaped structure. Unlike the recursion-tree widget (fixed node set, only
// the states animate), here each frame carries its own node list — so insertions, rotations and
// B-tree splits are just a different set of parent links from one frame to the next.
//
// Layout is the same leaf-slot algorithm the recursion tree uses: leaves take sequential x slots and
// internal nodes centre over their children. Siblings are ordered by `order`, so a BST's left child
// stays left even when it is inserted after the right one.

private enum class TreeState { Idle, Path, Active, Marked }

private class TreeNodeSpec(
    val id: Int,
    val label: String,
    val parent: Int?,
    val order: Int,
    val state: TreeState = TreeState.Idle,
)

private class TreeFrame(val nodes: List<TreeNodeSpec>, val status: String)

private class TreeConfig(
    val intro: String,
    val markedLabel: String,
    val build: () -> List<TreeFrame>,
)

private val PathColor = Color(0xFF3B82F6)
private val ActiveColor = Color(0xFFFACC15)
private val MarkedColor = SimColors.Green
private val IdleColor = Color(0xFF7C3AED)

// Mutable tree the builders mutate; each `frame()` call snapshots it.
private class TreeBuilder {
    private class Node(val id: Int, var label: String, var parent: Int?, var order: Int)

    private val nodes = LinkedHashMap<Int, Node>()
    private var nextId = 0
    val frames = mutableListOf<TreeFrame>()

    fun add(label: String, parent: Int?, order: Int): Int {
        val id = nextId++
        nodes[id] = Node(id, label, parent, order)
        return id
    }

    fun relabel(id: Int, label: String) { nodes.getValue(id).label = label }
    fun reparent(id: Int, parent: Int?, order: Int) {
        nodes.getValue(id).parent = parent
        nodes.getValue(id).order = order
    }
    fun remove(id: Int) { nodes.remove(id) }
    fun labelOf(id: Int) = nodes.getValue(id).label

    fun frame(
        status: String,
        active: Set<Int> = emptySet(),
        path: Set<Int> = emptySet(),
        marked: Set<Int> = emptySet(),
    ) {
        frames.add(
            TreeFrame(
                nodes.values.map { n ->
                    TreeNodeSpec(
                        id = n.id,
                        label = n.label,
                        parent = n.parent,
                        order = n.order,
                        state = when {
                            n.id in active -> TreeState.Active
                            n.id in marked -> TreeState.Marked
                            n.id in path -> TreeState.Path
                            else -> TreeState.Idle
                        },
                    )
                },
                status,
            ),
        )
    }
}

// ── Binary search tree: insert then search, both walking the compare path ────
private class BstNode(val id: Int, val value: Int) {
    var left: BstNode? = null
    var right: BstNode? = null
}

private fun bstFrames(): List<TreeFrame> {
    val b = TreeBuilder()
    val values = listOf(50, 30, 70, 20, 40, 60, 80)
    var root: BstNode? = null

    fun insert(value: Int) {
        if (root == null) {
            root = BstNode(b.add(value.toString(), null, 0), value)
            b.frame("$value becomes the root.", marked = setOf(root!!.id))
            return
        }
        var current = root!!
        val path = mutableSetOf<Int>()
        while (true) {
            path.add(current.id)
            b.frame("Insert $value: compare with ${current.value}", active = setOf(current.id), path = path)
            if (value < current.value) {
                val next = current.left
                if (next == null) {
                    val node = BstNode(b.add(value.toString(), current.id, 0), value)
                    current.left = node
                    b.frame("$value < ${current.value} → new left child.", path = path, marked = setOf(node.id))
                    return
                }
                current = next
            } else {
                val next = current.right
                if (next == null) {
                    val node = BstNode(b.add(value.toString(), current.id, 1), value)
                    current.right = node
                    b.frame("$value > ${current.value} → new right child.", path = path, marked = setOf(node.id))
                    return
                }
                current = next
            }
        }
    }

    values.forEach { insert(it) }

    // Search: the same compare walk, now read-only.
    val target = 60
    var current = root
    val path = mutableSetOf<Int>()
    while (current != null) {
        path.add(current.id)
        if (current.value == target) {
            b.frame("Found $target — 3 comparisons for 7 nodes.", path = path, marked = setOf(current.id))
            break
        }
        b.frame(
            "Search $target: ${current.value} ${if (target < current.value) "> target, go left" else "< target, go right"}",
            active = setOf(current.id),
            path = path,
        )
        current = if (target < current.value) current.left else current.right
    }
    return b.frames
}

// ── General tree: depth-first then breadth-first over a fixed hierarchy ──────
private fun treeTraversalFrames(): List<TreeFrame> {
    val b = TreeBuilder()
    val root = b.add("/", null, 0)
    val usr = b.add("usr", root, 0)
    val etc = b.add("etc", root, 1)
    val bin = b.add("bin", usr, 0)
    val lib = b.add("lib", usr, 1)
    val conf = b.add("conf", etc, 0)
    val children = mapOf(root to listOf(usr, etc), usr to listOf(bin, lib), etc to listOf(conf))

    b.frame("A directory tree — the same structure, visited two different ways.")

    val visited = mutableSetOf<Int>()
    fun dfs(id: Int) {
        visited.add(id)
        b.frame("Depth-first (pre-order): visit ${b.labelOf(id)}", active = setOf(id), marked = visited - id)
        children[id].orEmpty().forEach { dfs(it) }
    }
    dfs(root)
    b.frame("Depth-first order: / → usr → bin → lib → etc → conf", marked = visited.toSet())

    visited.clear()
    val queue = ArrayDeque(listOf(root))
    while (queue.isNotEmpty()) {
        val id = queue.removeFirst()
        visited.add(id)
        b.frame("Breadth-first: visit ${b.labelOf(id)} (level by level)", active = setOf(id), marked = visited - id)
        queue.addAll(children[id].orEmpty())
    }
    b.frame("Breadth-first order: / → usr → etc → bin → lib → conf", marked = visited.toSet())
    return b.frames
}

// ── Binary max-heap: array-backed insert with sift-up ────────────────────────
private fun heapFrames(): List<TreeFrame> {
    val b = TreeBuilder()
    val heap = mutableListOf<Int>()
    val ids = mutableListOf<Int>()

    fun idOf(index: Int) = ids[index]

    for (value in listOf(5, 9, 3, 12, 20)) {
        val index = heap.size
        val parentIndex = if (index == 0) null else (index - 1) / 2
        heap.add(value)
        ids.add(b.add(value.toString(), parentIndex?.let { idOf(it) }, if (index == 0) 0 else (index - 1) % 2))
        b.frame("Insert $value at the next free slot (index $index).", active = setOf(idOf(index)))

        var i = index
        while (i > 0) {
            val p = (i - 1) / 2
            if (heap[i] <= heap[p]) {
                b.frame("${heap[i]} ≤ parent ${heap[p]} → heap property holds.", path = setOf(idOf(i), idOf(p)))
                break
            }
            val tmp = heap[i]
            heap[i] = heap[p]
            heap[p] = tmp
            b.relabel(idOf(i), heap[i].toString())
            b.relabel(idOf(p), heap[p].toString())
            b.frame("${heap[p]} > ${heap[i]} → sift up, swap with parent.", active = setOf(idOf(p), idOf(i)))
            i = p
        }
    }
    b.frame("Max-heap complete — every parent is ≥ both of its children.", marked = ids.toSet())
    return b.frames
}

// ── Trie: insert words character by character, then look one up ──────────────
private fun trieFrames(): List<TreeFrame> {
    val b = TreeBuilder()
    val root = b.add("•", null, 0)
    // path key = accumulated prefix, so shared prefixes reuse the same node.
    val byPrefix = mutableMapOf("" to root)

    for (word in listOf("cat", "car", "dog")) {
        var prefix = ""
        var parent = root
        val path = mutableSetOf(root)
        for (ch in word) {
            val next = prefix + ch
            val existing = byPrefix[next]
            parent = if (existing != null) {
                b.frame("Insert \"$word\": '$ch' already exists — reuse the shared prefix \"$next\".", active = setOf(existing), path = path)
                existing
            } else {
                val id = b.add(ch.toString(), parent, next.length)
                byPrefix[next] = id
                b.frame("Insert \"$word\": add node '$ch' for prefix \"$next\".", active = setOf(id), path = path)
                id
            }
            path.add(parent)
            prefix = next
        }
        b.relabel(parent, "${word.last()}∎")
        b.frame("\"$word\" stored — the terminal node is flagged ∎.", marked = setOf(parent), path = path)
    }

    var prefix = ""
    val path = mutableSetOf(root)
    for (ch in "car") {
        prefix += ch
        val id = byPrefix.getValue(prefix)
        path.add(id)
        b.frame("Lookup \"car\": follow '$ch' → \"$prefix\"", active = setOf(id), path = path)
    }
    b.frame("Found \"car\" — cost is the word length, not the dictionary size.", marked = setOf(byPrefix.getValue("car")), path = path)
    return b.frames
}

// ── AVL: three ascending inserts force a left rotation ───────────────────────
private fun avlFrames(): List<TreeFrame> {
    val b = TreeBuilder()
    val n10 = b.add("10", null, 0)
    b.frame("Insert 10 — the tree is balanced.", marked = setOf(n10))
    val n20 = b.add("20", n10, 1)
    b.frame("Insert 20 to the right. Balance factor of 10 is −1 — still fine.", marked = setOf(n20))
    val n30 = b.add("30", n20, 1)
    b.frame("Insert 30. Node 10 now has balance factor −2 — right-right violation.", active = setOf(n10), path = setOf(n20, n30))

    // Left rotation: 20 becomes the root, 10 becomes its left child.
    b.reparent(n20, null, 0)
    b.reparent(n10, n20, 0)
    b.frame("Left rotation around 10: 20 becomes the root.", active = setOf(n20), marked = setOf(n10, n30))
    b.frame("Height is back to log n. A plain BST would have degenerated into a linked list here.", marked = setOf(n10, n20, n30))
    return b.frames
}

// ── Segment tree: bottom-up sum build, then a range query ────────────────────
private fun segmentTreeFrames(): List<TreeFrame> {
    val data = listOf(2, 1, 5, 3)
    val b = TreeBuilder()
    val root = b.add("[0..3]", null, 0)
    val left = b.add("[0..1]", root, 0)
    val right = b.add("[2..3]", root, 1)
    val leaves = listOf(
        b.add(data[0].toString(), left, 0),
        b.add(data[1].toString(), left, 1),
        b.add(data[2].toString(), right, 0),
        b.add(data[3].toString(), right, 1),
    )
    b.frame("Segment tree over [2, 1, 5, 3]. Leaves hold the values; each internal node will hold its range's sum.", marked = leaves.toSet())

    val leftSum = data[0] + data[1]
    b.relabel(left, "$leftSum")
    b.frame("[0..1] = ${data[0]} + ${data[1]} = $leftSum", active = setOf(left), path = setOf(leaves[0], leaves[1]))
    val rightSum = data[2] + data[3]
    b.relabel(right, "$rightSum")
    b.frame("[2..3] = ${data[2]} + ${data[3]} = $rightSum", active = setOf(right), path = setOf(leaves[2], leaves[3]))
    b.relabel(root, "${leftSum + rightSum}")
    b.frame("Root = $leftSum + $rightSum = ${leftSum + rightSum} — the whole-array sum.", active = setOf(root))

    b.frame("Query sum[1..3]: the range straddles both halves, so descend.", active = setOf(root))
    b.frame("Left half contributes only index 1 → take the leaf ${data[1]}.", active = setOf(leaves[1]), path = setOf(left))
    b.frame("Right half [2..3] is fully covered → take its node, $rightSum. No need to visit its leaves.", active = setOf(right), path = setOf(right))
    b.frame("sum[1..3] = ${data[1]} + $rightSum = ${data[1] + rightSum} — two nodes touched instead of three elements.", marked = setOf(leaves[1], right))
    return b.frames
}

// ── B-tree of order 3: inserts until a node splits ───────────────────────────
private fun bTreeFrames(): List<TreeFrame> {
    val b = TreeBuilder()
    var root = b.add("10", null, 0)
    b.frame("Order-3 B-tree: a node holds up to 2 keys. Insert 10.", marked = setOf(root))
    b.relabel(root, "10 · 20")
    b.frame("Insert 20 — it fits in the same node, keys stay sorted.", marked = setOf(root))

    b.relabel(root, "10 · 20 · 30")
    b.frame("Insert 30 — the node is now over capacity (3 keys) and must split.", active = setOf(root))

    // Split: median 20 moves up into a new root, 10 and 30 become leaves.
    b.remove(root)
    root = b.add("20", null, 0)
    val leftLeaf = b.add("10", root, 0)
    val rightLeaf = b.add("30", root, 1)
    b.frame("Median 20 is promoted to a new root; 10 and 30 become its children.", active = setOf(root), marked = setOf(leftLeaf, rightLeaf))

    b.relabel(rightLeaf, "30 · 40")
    b.frame("Insert 40: descend past 20, land in the right leaf — it has room.", path = setOf(root), marked = setOf(rightLeaf))
    b.relabel(leftLeaf, "5 · 10")
    b.frame("Insert 5: descend left of 20 into that leaf. The tree grows in width, not depth.", path = setOf(root), marked = setOf(leftLeaf))
    b.frame("All leaves sit at the same depth — that is what keeps B-tree lookups O(log n) on disk.", marked = setOf(leftLeaf, rightLeaf))
    return b.frames
}

private val treeConfigs = mapOf(
    "binary_search_tree" to TreeConfig(
        intro = "Inserting 50, 30, 70, 20, 40, 60, 80, then searching for 60. Every operation walks one root-to-leaf path, comparing once per level.",
        markedLabel = "Placed / found",
        build = ::bstFrames,
    ),
    "tree" to TreeConfig(
        intro = "One hierarchy, two traversal orders: depth-first goes as deep as it can before backtracking; breadth-first sweeps level by level.",
        markedLabel = "Visited",
        build = ::treeTraversalFrames,
    ),
    "heap" to TreeConfig(
        intro = "A binary max-heap is a complete tree stored in an array. Each insert drops the value into the next free slot, then sifts it up while it beats its parent.",
        markedLabel = "Settled",
        build = ::heapFrames,
    ),
    "trie" to TreeConfig(
        intro = "Inserting \"cat\", \"car\", \"dog\" — shared prefixes share nodes. Lookup cost depends on the word length, never on how many words are stored.",
        markedLabel = "Word end / found",
        build = ::trieFrames,
    ),
    "avl_red_black_tree" to TreeConfig(
        intro = "Three ascending inserts would make a plain BST degenerate into a list. A self-balancing tree detects the violation and rotates.",
        markedLabel = "Balanced",
        build = ::avlFrames,
    ),
    "segment_tree" to TreeConfig(
        intro = "Built bottom-up over [2, 1, 5, 3]: every internal node caches its range's sum, so a range query only touches O(log n) nodes.",
        markedLabel = "Contributes to answer",
        build = ::segmentTreeFrames,
    ),
    "b_tree" to TreeConfig(
        intro = "An order-3 B-tree: nodes hold multiple keys and split at the median when full, so the tree stays shallow and wide — the shape disk and database indexes want.",
        markedLabel = "Settled",
        build = ::bTreeFrames,
    ),
)

private fun treeConfigFor(topicId: String): TreeConfig =
    treeConfigs[topicId] ?: treeConfigs.getValue("binary_search_tree")

@Composable
fun TreeVisualizerSection(topicId: String) {
    val config = remember(topicId) { treeConfigFor(topicId) }
    val frames = remember(config) { config.build() }
    val playback = rememberPlaybackState(key = config, stepCount = frames.size)
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

            TreeCanvas(frame = frame)

            Text(
                frame.status,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 12.dp),
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                TreeLegend(ActiveColor, "Current")
                TreeLegend(PathColor, "Path")
                TreeLegend(MarkedColor, config.markedLabel)
            }

            PlaybackTransport(playback)
        }
    }
}

@Composable
private fun TreeLegend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(10.dp).background(color, CircleShape))
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 5.dp),
        )
    }
}

@Composable
private fun TreeCanvas(frame: TreeFrame) {
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = TextStyle(color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)

    // Leaf-slot layout: leaves take sequential slots, parents centre over their children. Siblings
    // are ordered by `order` so left/right children keep their sides regardless of insertion order.
    val byId = frame.nodes.associateBy { it.id }
    val childrenOf = frame.nodes.filter { it.parent != null }.groupBy { it.parent!! }
        .mapValues { (_, kids) -> kids.sortedBy { it.order } }
    val depthById = HashMap<Int, Int>()
    val xById = HashMap<Int, Float>()
    var leafCursor = 0f

    fun walk(id: Int, depth: Int) {
        depthById[id] = depth
        val kids = childrenOf[id].orEmpty()
        if (kids.isEmpty()) {
            xById[id] = leafCursor
            leafCursor += 1f
        } else {
            kids.forEach { walk(it.id, depth + 1) }
            xById[id] = kids.map { xById.getValue(it.id) }.average().toFloat()
        }
    }
    frame.nodes.firstOrNull { it.parent == null }?.let { walk(it.id, 0) }

    val leafCount = leafCursor.coerceAtLeast(1f)
    val maxDepth = depthById.values.maxOrNull() ?: 0

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
            .padding(6.dp),
    ) {
        Canvas(modifier = Modifier.fillMaxWidth().height(220.dp)) {
            val padX = 30f
            val padY = 26f
            fun px(x: Float) = padX + (x + 0.5f) / leafCount * (size.width - 2 * padX)
            // Fixed row spacing (capped so deep trees still fit) rather than stretching to the
            // canvas height — otherwise a 2-level frame and a 4-level frame of the same tree would
            // render at wildly different scales as nodes get inserted.
            val rowGap = if (maxDepth == 0) 0f else minOf(56f, (size.height - 2 * padY) / maxDepth)
            fun py(depth: Int) = if (maxDepth == 0) size.height / 2f else padY + depth * rowGap

            frame.nodes.forEach { node ->
                val parent = node.parent?.let { byId[it] } ?: return@forEach
                drawLine(
                    Color(0xFFCBD0DA),
                    Offset(px(xById.getValue(parent.id)), py(depthById.getValue(parent.id))),
                    Offset(px(xById.getValue(node.id)), py(depthById.getValue(node.id))),
                    strokeWidth = 2f,
                )
            }

            frame.nodes.forEach { node ->
                val center = Offset(px(xById.getValue(node.id)), py(depthById.getValue(node.id)))
                val color = when (node.state) {
                    TreeState.Active -> ActiveColor
                    TreeState.Marked -> MarkedColor
                    TreeState.Path -> PathColor
                    TreeState.Idle -> IdleColor
                }
                val layout = textMeasurer.measure(node.label, labelStyle)
                // Multi-key B-tree nodes need a wider pill than a single digit does.
                val boxWidth = (layout.size.width + 18f).coerceAtLeast(30f)
                val boxHeight = layout.size.height + 12f
                drawRoundRect(
                    color = color,
                    topLeft = Offset(center.x - boxWidth / 2f, center.y - boxHeight / 2f),
                    size = Size(boxWidth, boxHeight),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(9f, 9f),
                )
                drawText(
                    layout,
                    topLeft = Offset(center.x - layout.size.width / 2f, center.y - layout.size.height / 2f),
                )
            }
        }
    }
}
