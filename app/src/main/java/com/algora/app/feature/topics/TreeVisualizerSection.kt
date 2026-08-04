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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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

// Aho-Corasick's failure links run between nodes that are not parent and child, and the canvas
// derives its edges from `parent` alone. Rather than give the tree model a second edge kind, a frame
// carries the links it wants drawn — same additive shape as CloudFrame's later fields.
private class TreeLink(val from: Int, val to: Int)

private class TreeFrame(
    val nodes: List<TreeNodeSpec>,
    val status: String,
    val links: List<TreeLink> = emptyList(),
)

private class TreeConfig(
    val intro: String,
    val markedLabel: String,
    val build: () -> List<TreeFrame>,
    // Non-null adds a fourth legend chip; only the configs that draw links need it.
    val linkLabel: String? = null,
)

private val PathColor = Color(0xFF3B82F6)
private val ActiveColor = Color(0xFFFACC15)
private val MarkedColor = SimColors.Green
private val IdleColor = Color(0xFF7C3AED)
private val LinkColor = Color(0xFFF97316)

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
    fun parentOf(id: Int) = nodes[id]?.parent

    fun frame(
        status: String,
        active: Set<Int> = emptySet(),
        path: Set<Int> = emptySet(),
        marked: Set<Int> = emptySet(),
        links: List<TreeLink> = emptyList(),
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
                links,
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

// ── Priority queue ADT: the contract, and why a heap and not a sorted list ───
private fun priorityQueueFrames(): List<TreeFrame> {
    val b = TreeBuilder()
    // Array-backed binary heap; ids are tracked per array slot so sift-up can swap labels in place.
    val heap = mutableListOf<Int>()
    val ids = mutableListOf<Int>()

    fun idFor(index: Int): Int {
        while (ids.size <= index) {
            val parent = if (ids.isEmpty()) null else ids[(ids.size - 1 - 1) / 2]
            ids.add(b.add("", parent, if (ids.size % 2 == 1) 0 else 1))
        }
        return ids[index]
    }
    fun sync() = heap.indices.forEach { b.relabel(idFor(it), heap[it].toString()) }

    b.frame("A priority queue promises three things: insert, peek at the highest-priority item, and remove it. " +
        "It says nothing about how the rest is arranged — that freedom is what makes it fast.")

    for (value in listOf(7, 4, 9, 2, 6)) {
        heap.add(value)
        var i = heap.lastIndex
        idFor(i)
        sync()
        b.frame("insert($value) drops it at the next free slot, which keeps the tree complete but may break the " +
            "heap order.", active = setOf(ids[i]))
        while (i > 0) {
            val parent = (i - 1) / 2
            if (heap[parent] <= heap[i]) break
            val t = heap[parent]; heap[parent] = heap[i]; heap[i] = t
            sync()
            b.frame("$value is smaller than its parent, so they swap. Sift-up only ever touches one root-to-leaf " +
                "path — at most log n steps, never the whole structure.",
                active = setOf(ids[parent]), path = setOf(ids[i]))
            i = parent
        }
    }

    sync()
    b.frame("Five inserts done. Note what is *not* true: the heap is not sorted. Only one guarantee holds — every " +
        "parent is smaller than its children, so the minimum has nowhere to hide but the root.",
        marked = setOf(ids[0]))
    b.frame("peek() reads the root, ${heap[0]}, in constant time. That single guarantee is all the ADT's contract " +
        "actually needs.", marked = setOf(ids[0]))

    val removed = heap[0]
    heap[0] = heap.removeAt(heap.lastIndex)
    b.remove(ids.removeAt(ids.size - 1))
    sync()
    b.frame("remove() takes $removed, then moves the last element to the root to keep the tree complete — which " +
        "breaks the order again, at the top this time.", active = setOf(ids[0]))

    var i = 0
    while (true) {
        val l = 2 * i + 1
        val r = 2 * i + 2
        var smallest = i
        if (l < heap.size && heap[l] < heap[smallest]) smallest = l
        if (r < heap.size && heap[r] < heap[smallest]) smallest = r
        if (smallest == i) break
        val t = heap[smallest]; heap[smallest] = heap[i]; heap[i] = t
        sync()
        b.frame("Sift-down swaps with the smaller child. Again one path, log n steps.",
            active = setOf(ids[smallest]), path = setOf(ids[i]))
        i = smallest
    }

    sync()
    b.frame("The new minimum, ${heap[0]}, is at the root. A sorted list would give the same peek in O(1) but pay " +
        "O(n) per insert to stay sorted; a heap keeps *just enough* order to answer the one question the ADT " +
        "asks, and both insert and remove cost log n. The contract is the interface — a heap, a sorted list and " +
        "a pairing heap all satisfy it at different prices.",
        marked = setOf(ids[0]))
    return b.frames
}

// ── Huffman coding: build the tree bottom-up, then read the codes off it ─────
private fun huffmanFrames(): List<TreeFrame> {
    val b = TreeBuilder()
    val freq = listOf("a" to 20, "b" to 12, "c" to 8, "d" to 5, "e" to 3)
    // Each live subtree tracks its root id, weight and the leaves under it.
    class Sub(val id: Int, val weight: Int, val leaves: List<String>)

    var live = freq.map { (ch, f) -> Sub(b.add("$ch:$f", null, 0), f, listOf(ch)) }
    b.frame("Five symbols with their frequencies. Fixed-width coding would spend ⌈log₂ 5⌉ = 3 bits on every one of " +
        "them, including the rare ones.")

    while (live.size > 1) {
        val sorted = live.sortedBy { it.weight }
        val first = sorted[0]
        val second = sorted[1]
        b.frame("The two lightest subtrees are ${first.weight} and ${second.weight}. Merging the rarest pair first " +
            "is what pushes them deepest, and depth is code length.",
            active = setOf(first.id, second.id))
        val merged = b.add((first.weight + second.weight).toString(), null, 0)
        b.reparent(first.id, merged, 0)
        b.reparent(second.id, merged, 1)
        live = live.filterNot { it === first || it === second } + Sub(merged, first.weight + second.weight, first.leaves + second.leaves)
        b.frame("They become children of a node weighing ${first.weight + second.weight}, which goes back into the " +
            "pool. ${live.size} subtree(s) left.", marked = setOf(merged))
    }

    // A symbol's code length is its depth, read off the finished tree by walking parent links.
    val total = freq.sumOf { it.second }
    val finalNodes = b.frames.last().nodes
    val codeLengths = freq.associate { (ch, _) ->
        var depth = 0
        var current = finalNodes.first { it.label.startsWith("$ch:") }.id
        var parent = finalNodes.first { it.id == current }.parent
        while (parent != null) {
            depth++
            current = parent
            parent = finalNodes.first { it.id == current }.parent
        }
        ch to depth
    }
    val huffmanBits = freq.sumOf { (ch, f) -> f * codeLengths.getValue(ch) }
    val fixedBits = total * 3

    b.frame("The tree is finished. A symbol's code is the path to it — left is 0, right is 1 — so its length is " +
        "just its depth: " + freq.joinToString(", ") { "${it.first}=${codeLengths.getValue(it.first)} bits" } + ".")
    b.frame("Weighted by frequency that is $huffmanBits bits for the whole message, against $fixedBits at a fixed " +
        "3 bits each — a ${"%.0f".format(100.0 * (fixedBits - huffmanBits) / fixedBits)}% saving. No code is a " +
        "prefix of another, because every symbol sits at a leaf, so the decoder never needs a separator.")
    return b.frames
}

// ── Disjoint set: a forest whose only job is to answer "same set?" ───────────
// Union by rank keeps the trees short; path compression rewrites the parents it walked past. Both
// costs are counted against a naive implementation over the same operation script.
private fun disjointSetFrames(): List<TreeFrame> {
    val labels = listOf("A", "B", "C", "D", "E", "F", "G", "H")
    val b = TreeBuilder()
    val nodeId = labels.mapIndexed { i, label -> b.add(label, null, i) }
    val parent = IntArray(labels.size) { it }
    val rank = IntArray(labels.size)

    b.frame("Eight elements, eight singleton sets. Each is its own root — the forest is the set " +
        "partition, nothing else is stored.")

    fun root(x: Int): Int {
        var cur = x
        while (parent[cur] != cur) cur = parent[cur]
        return cur
    }

    // Ordered so both cases appear: equal ranks (which grows the forest) and unequal (which does not).
    val unions = listOf(0 to 1, 2 to 3, 0 to 2, 4 to 5, 0 to 4, 6 to 7, 0 to 6)
    unions.forEach { (x, y) ->
        val rx = root(x)
        val ry = root(y)
        val tie = rank[rx] == rank[ry]
        val (child, newRoot) = when {
            rank[rx] < rank[ry] -> rx to ry
            rank[ry] < rank[rx] -> ry to rx
            else -> { rank[rx]++; ry to rx }
        }
        parent[child] = newRoot
        b.reparent(nodeId[child], nodeId[newRoot], child)
        b.frame(
            "union(${labels[x]}, ${labels[y]}): the roots are ${labels[rx]} (rank ${if (tie) rank[rx] - 1 else rank[rx]}) " +
                "and ${labels[ry]} (rank ${rank[ry]}). " +
                if (tie) {
                    "Equal ranks, so the choice is arbitrary — ${labels[child]} goes under ${labels[newRoot]} and " +
                        "${labels[newRoot]}'s rank goes up to ${rank[newRoot]}. This is the only case where the " +
                        "forest gets taller."
                } else {
                    "The shorter tree hangs under the taller one — ${labels[child]} now points at " +
                        "${labels[newRoot]} and no depth is added, which is the whole point of tracking rank."
                },
            active = setOf(nodeId[child]),
            marked = setOf(nodeId[newRoot]),
        )
    }

    val deepest = labels.indices.maxBy { x ->
        var d = 0
        var cur = x
        while (parent[cur] != cur) { cur = parent[cur]; d++ }
        d
    }
    val walkPath = mutableListOf<Int>()
    var cur = deepest
    while (parent[cur] != cur) { walkPath += cur; cur = parent[cur] }
    val theRoot = cur
    b.frame(
        "find(${labels[deepest]}) walks ${walkPath.size} pointers to reach ${labels[theRoot]}. Every node it " +
            "passed is in the same set as the root, so pointing them straight at it loses no information.",
        active = setOf(nodeId[deepest]),
        path = walkPath.map { nodeId[it] }.toSet(),
        marked = setOf(nodeId[theRoot]),
    )

    walkPath.forEach { node ->
        parent[node] = theRoot
        b.reparent(nodeId[node], nodeId[theRoot], node)
    }
    b.frame(
        "Path compression: every node on that walk is re-hung directly under ${labels[theRoot]}. The answer " +
            "is unchanged; the next find on any of them costs one hop.",
        marked = walkPath.map { nodeId[it] }.toSet() + nodeId[theRoot],
    )

    // Same script, two implementations, hops counted — the claim about near-constant time is measured.
    fun measure(useRank: Boolean, useCompression: Boolean): Int {
        val p = IntArray(labels.size) { it }
        val r = IntArray(labels.size)
        var hops = 0
        fun find(x: Int): Int {
            var c = x
            val seen = mutableListOf<Int>()
            while (p[c] != c) { hops++; seen += c; c = p[c] }
            if (useCompression) seen.forEach { p[it] = c }
            return c
        }
        unions.forEach { (x, y) ->
            val rx = find(x)
            val ry = find(y)
            if (rx == ry) return@forEach
            if (!useRank) { p[rx] = ry } else when {
                r[rx] < r[ry] -> p[rx] = ry
                r[ry] < r[rx] -> p[ry] = rx
                else -> { p[ry] = rx; r[rx]++ }
            }
        }
        repeat(4) { labels.indices.forEach { find(it) } }
        return hops
    }
    val naive = measure(useRank = false, useCompression = false)
    val ranked = measure(useRank = true, useCompression = false)
    val both = measure(useRank = true, useCompression = true)
    b.frame(
        "Over the same script — those ${unions.size} unions then 4 rounds of find on all 8 elements — a naive " +
            "\"point one root at the other\" walks $naive pointers, union by rank alone walks $ranked, and rank " +
            "plus compression walks $both. That is the inverse-Ackermann claim in miniature: the structure " +
            "flattens itself as you use it.",
        marked = nodeId.toSet(),
    )
    return b.frames
}

// ── Suffix tree: the suffix trie, then the same information with unary chains collapsed ──
private fun suffixTreeFrames(): List<TreeFrame> {
    val text = "aba$"
    val b = TreeBuilder()
    val root = b.add("•", null, 0)
    val byPrefix = mutableMapOf("" to root)
    val suffixes = text.indices.map { text.substring(it) }

    b.frame(
        "The text is \"$text\" with a terminator, which guarantees no suffix is a prefix of another. Its " +
            "${suffixes.size} suffixes go into a trie: ${suffixes.joinToString(", ")}.",
        marked = setOf(root),
    )

    suffixes.forEachIndexed { start, suffix ->
        var prefix = ""
        var parentNode = root
        val path = mutableSetOf(root)
        suffix.forEach { ch ->
            val next = prefix + ch
            val existing = byPrefix[next]
            parentNode = existing ?: b.add(ch.toString(), parentNode, next.length).also { byPrefix[next] = it }
            path += parentNode
            b.frame(
                if (existing != null) {
                    "Suffix \"$suffix\": '$ch' is already on this path — shared prefixes cost nothing extra."
                } else {
                    "Suffix \"$suffix\" (starts at index $start): add '$ch' for path \"$next\"."
                },
                active = setOf(parentNode),
                path = path,
            )
            prefix = next
        }
        b.relabel(parentNode, "[$start]")
        b.frame("Leaf labelled with the start index $start — that label is what a search returns.", marked = setOf(parentNode), path = path)
    }

    val trieNodes = byPrefix.size
    // Collapse every node that has exactly one child, which is what turns a trie into a suffix tree.
    fun childCount(prefix: String) = byPrefix.keys.count { it.length == prefix.length + 1 && it.startsWith(prefix) }
    val chains = byPrefix.keys.filter { it.isNotEmpty() && childCount(it) == 1 }
    b.frame(
        "The trie has $trieNodes nodes, and ${chains.size} of them have exactly one child — they carry no " +
            "decision, only a character. A suffix tree stores the whole chain on one edge instead.",
        active = chains.mapNotNull { byPrefix[it] }.toSet(),
    )

    // Rebuild as the compressed tree so the saving is shown, not just described.
    val c = TreeBuilder()
    val cRoot = c.add("•", null, 0)
    fun edgesFrom(prefix: String): List<Char> =
        byPrefix.keys.filter { it.length == prefix.length + 1 && it.startsWith(prefix) }
            .map { it.last() }
            .sorted()
    var compressedNodes = 1
    fun buildCompressed(prefix: String, parentId: Int, order: Int) {
        edgesFrom(prefix).forEachIndexed { i, ch ->
            var path = prefix + ch
            while (childCount(path) == 1) path += edgesFrom(path).first()
            val label = path.substring(prefix.length)
            val id = c.add(label, parentId, order * 10 + i)
            compressedNodes++
            buildCompressed(path, id, i)
        }
    }
    buildCompressed("", cRoot, 0)
    c.frame(
        "Same text, unary chains collapsed: $compressedNodes nodes instead of $trieNodes, and every edge now " +
            "carries a substring. Leaves still correspond one-to-one with suffixes, which is why the node " +
            "count is O(n) rather than O(n²) for a trie on a long text.",
        marked = setOf(cRoot),
    )
    c.frame(
        "Searching \"ba\" follows one edge and stops after comparing the pattern, not the text — a suffix " +
            "tree answers \"does this pattern occur\" in time proportional to the pattern length, no matter " +
            "how long the text is. Building it in O(n) (Ukkonen) is the hard part; using it is not.",
        // Only the root's own "ba$" edge — the deeper one is reached through "a", not by this search.
        active = c.frames.last().nodes
            .filter { it.parent == cRoot && it.label.startsWith("ba") }
            .map { it.id }
            .toSet(),
    )
    return b.frames + c.frames
}

// ── Lowest common ancestor: lift the deeper node, then descend together ──────

private fun lcaFrames(): List<TreeFrame> {
    val b = TreeBuilder()

    // A
    // ├── B ── D ── H
    // │   └── E
    // └── C ── F
    //     └── G
    val a = b.add("A", null, 0)
    val bb = b.add("B", a, 0)
    val c = b.add("C", a, 1)
    val d = b.add("D", bb, 0)
    val e = b.add("E", bb, 1)
    val f = b.add("F", c, 0)
    val g = b.add("G", c, 1)
    val h = b.add("H", d, 0)

    val parent = mapOf(bb to a, c to a, d to bb, e to bb, f to c, g to c, h to d)
    val depth = mapOf(a to 0, bb to 1, c to 1, d to 2, e to 2, f to 2, g to 2, h to 3)

    b.frame("Root the tree and record every node's depth. A is at depth 0, H at depth 3.", marked = setOf(a))

    // ── Query 1: H and E, one deeper than the other ──
    b.frame(
        "Query LCA(H, E). H sits at depth ${depth[h]}, E at depth ${depth[e]} — they cannot be compared until " +
            "they are level.",
        active = setOf(h, e),
    )
    b.frame(
        "Lift H by the depth difference (${depth[h]!! - depth[e]!!} level, one jump of 2⁰) to D. Both are now at " +
            "depth ${depth[e]}.",
        active = setOf(d, e),
        path = setOf(h),
    )
    b.frame(
        "D ≠ E, so step both up together: D → B and E → B. Their parents are the same node, which means the " +
            "previous step landed just below the meeting point.",
        active = setOf(bb),
        path = setOf(d, e, h),
    )
    b.frame(
        "LCA(H, E) = B. Path length between them is depth[H] + depth[E] − 2·depth[B] = 3 + 2 − 2 = 3 edges.",
        marked = setOf(bb),
        path = setOf(h, d, e),
    )

    // ── Query 2: across the two halves ──
    b.frame(
        "Query LCA(E, G). Same depth already, so no lifting is needed — jump straight to descending together.",
        active = setOf(e, g),
    )
    b.frame(
        "E ≠ G, and their parents B and C differ too, so both climb. Binary lifting would take the largest jump " +
            "whose ancestors still disagree, never overshooting the answer.",
        active = setOf(bb, c),
        path = setOf(e, g),
    )
    b.frame(
        "Now up[0][B] = up[0][C] = A, so the ancestors finally agree. The LCA is that shared parent.",
        marked = setOf(a),
        path = setOf(e, g, bb, c),
    )
    b.frame(
        "LCA(E, G) = A — the root, because the two nodes live in different halves of the tree. With the jump " +
            "table precomputed, each of these queries costs O(log n) regardless of tree height.",
        marked = setOf(a),
        path = setOf(e, g, bb, c),
    )

    // Keep the compiler honest about the unused-but-documented structure.
    check(parent.getValue(h) == d)
    return b.frames
}

// ── Tree DP: maximum-weight independent set, post-order ──────────────────────

private fun treeDpFrames(): List<TreeFrame> {
    val b = TreeBuilder()
    val weight = linkedMapOf<String, Int>("A" to 3, "B" to 2, "C" to 4, "D" to 5, "E" to 1, "F" to 6)

    val a = b.add("A·3", null, 0)
    val bb = b.add("B·2", a, 0)
    val c = b.add("C·4", a, 1)
    val d = b.add("D·5", bb, 0)
    val e = b.add("E·1", bb, 1)
    val f = b.add("F·6", c, 0)

    val ids = mapOf("A" to a, "B" to bb, "C" to c, "D" to d, "E" to e, "F" to f)
    val dp0 = mutableMapOf<String, Int>()
    val dp1 = mutableMapOf<String, Int>()

    fun show(name: String) {
        b.relabel(ids.getValue(name), "$name ${dp0[name]}/${dp1[name]}")
    }

    b.frame(
        "Maximum-weight independent set: pick nodes with the largest total weight, never two that are joined by " +
            "an edge. Labels are node·weight.",
        marked = setOf(a),
    )

    // Leaves first — post-order is what makes each parent's inputs ready.
    for (leaf in listOf("D", "E", "F")) {
        dp0[leaf] = 0
        dp1[leaf] = weight.getValue(leaf)
        show(leaf)
        b.frame(
            "Leaf $leaf: not taking it is worth 0, taking it is worth ${weight[leaf]}. Labels now read " +
                "\"not-taken / taken\".",
            active = setOf(ids.getValue(leaf)),
        )
    }

    dp1["B"] = weight.getValue("B") + dp0.getValue("D") + dp0.getValue("E")
    dp0["B"] = maxOf(dp0.getValue("D"), dp1.getValue("D")) + maxOf(dp0.getValue("E"), dp1.getValue("E"))
    show("B")
    b.frame(
        "B's children are finished, so B can be resolved. Taking B forbids D and E: 2 + 0 + 0 = ${dp1["B"]}. " +
            "Not taking B leaves each child free to do whichever is better: 5 + 1 = ${dp0["B"]}.",
        active = setOf(bb),
        path = setOf(d, e),
    )

    dp1["C"] = weight.getValue("C") + dp0.getValue("F")
    dp0["C"] = maxOf(dp0.getValue("F"), dp1.getValue("F"))
    show("C")
    b.frame(
        "Same rule at C: taking it gives 4 + 0 = ${dp1["C"]}, skipping it lets F be taken for ${dp0["C"]}. " +
            "Note the subtree already prefers the child over the parent here.",
        active = setOf(c),
        path = setOf(f),
    )

    dp1["A"] = weight.getValue("A") + dp0.getValue("B") + dp0.getValue("C")
    dp0["A"] = maxOf(dp0.getValue("B"), dp1.getValue("B")) + maxOf(dp0.getValue("C"), dp1.getValue("C"))
    show("A")
    b.frame(
        "The root combines both subtrees: take A for 3 + ${dp0["B"]} + ${dp0["C"]} = ${dp1["A"]}, or skip it for " +
            "${dp0["A"]}. Every subtree was solved exactly once, so this whole pass is O(n).",
        active = setOf(a),
        path = setOf(bb, c),
    )

    val best = maxOf(dp0.getValue("A"), dp1.getValue("A"))
    b.frame(
        "Answer ${best}: take A, D, E and F — 3 + 5 + 1 + 6. No two of them are adjacent, and the greedy " +
            "alternative of taking the heaviest node first would have blocked its neighbours for less.",
        marked = setOf(a, d, e, f),
        path = setOf(bb, c),
    )

    return b.frames
}

// ── Gradient-boosting implementations, as tree shapes ────────────────────────
// XGBoost, LightGBM and CatBoost all optimize the same objective; what distinguishes them in
// practice is how each one decides to grow. That is a tree-shape story, so it belongs here rather
// than in a scatter plot. Gains and leaf values below are computed from the real formulas.

// XGBoost's exact split gain, from the second-order Taylor expansion:
//   gain = ½[ G_L²/(H_L+λ) + G_R²/(H_R+λ) − (G_L+G_R)²/(H_L+H_R+λ) ] − γ
private fun xgbGain(gl: Double, hl: Double, gr: Double, hr: Double, lambda: Double, gamma: Double): Double =
    0.5 * (gl * gl / (hl + lambda) + gr * gr / (hr + lambda) - (gl + gr) * (gl + gr) / (hl + hr + lambda)) - gamma

private fun xgbLeaf(g: Double, h: Double, lambda: Double): Double = -g / (h + lambda)

private fun xgboostFrames(): List<TreeFrame> {
    val builder = TreeBuilder()
    val lambda = 1.0
    val gamma = 0.5

    val root = builder.add("G=−7.0  H=8.0", null, 0)
    builder.frame(
        "XGBoost is gradient boosting written as a proper regularized optimization. Each node carries two sums over the rows that reach it: G, the gradients of the loss, and H, the second derivatives. Ordinary gradient boosting uses only G.",
        active = setOf(root),
    )

    builder.relabel(root, "w* = ${"%.2f".format(xgbLeaf(-7.0, 8.0, lambda))}")
    builder.frame(
        "If this node stayed a leaf, its optimal value would be −G/(H+λ) = ${"%.2f".format(xgbLeaf(-7.0, 8.0, lambda))}. That is a closed-form Newton step, not a line search — the second-order term is what makes it exact, and λ in the denominator shrinks the leaf toward zero.",
        marked = setOf(root),
    )

    val gainA = xgbGain(-6.0, 4.0, -1.0, 4.0, lambda, gamma)
    val left = builder.add("G=−6.0  H=4.0", root, 0)
    val right = builder.add("G=−1.0  H=4.0", root, 1)
    builder.relabel(root, "gain ${"%.2f".format(gainA)}")
    builder.frame(
        "Candidate split: gain = ½[G_L²/(H_L+λ) + G_R²/(H_R+λ) − G²/(H+λ)] − γ = ${"%.2f".format(gainA)}. Positive, so it is worth taking. γ is a fixed toll charged per split — a structural penalty that has no analogue in plain GBM.",
        active = setOf(left, right),
        path = setOf(root),
    )

    val gainB = xgbGain(-0.6, 2.0, -0.4, 2.0, lambda, gamma)
    val leftLeft = builder.add("G=−0.6  H=2.0", right, 0)
    val leftRight = builder.add("G=−0.4  H=2.0", right, 1)
    builder.frame(
        "Try splitting the right child: gain = ${"%.2f".format(gainB)}. Negative, because the improvement it buys is smaller than γ. XGBoost prunes it — and note it computes this *after* growing to max depth, so a bad split whose children are excellent is not discarded prematurely.",
        active = setOf(leftLeft, leftRight),
    )

    builder.remove(leftLeft)
    builder.remove(leftRight)
    builder.relabel(left, "w = ${"%.2f".format(xgbLeaf(-6.0, 4.0, lambda))}")
    builder.relabel(right, "w = ${"%.2f".format(xgbLeaf(-1.0, 4.0, lambda))}")
    builder.frame(
        "Pruned. The two surviving leaves take their closed-form values ${"%.2f".format(xgbLeaf(-6.0, 4.0, lambda))} and ${"%.2f".format(xgbLeaf(-1.0, 4.0, lambda))}. Both are shrunk toward zero by λ — with λ = 0 they would be ${"%.2f".format(xgbLeaf(-6.0, 4.0, 0.0))} and ${"%.2f".format(xgbLeaf(-1.0, 4.0, 0.0))}.",
        marked = setOf(left, right),
    )

    builder.frame(
        "Growth here is level-wise: every node at a depth is considered before going deeper, which keeps the tree balanced and made the algorithm straightforward to parallelize. That choice is exactly what LightGBM changes.",
        marked = setOf(left, right),
        path = setOf(root),
    )
    return builder.frames
}

private fun lightgbmFrames(): List<TreeFrame> {
    val builder = TreeBuilder()

    // Both strategies get the same budget: three splits, which produces four leaves either way.
    // Each node's label is the gain that splitting THAT node would realize.
    val root = builder.add("root", null, 0)
    builder.frame("Same budget for both strategies: three splits, so four leaves either way. XGBoost's level-wise growth splits every node at a depth before descending.")

    val l = builder.add("gain 4.0", root, 0)
    val r = builder.add("gain 0.3", root, 1)
    builder.frame("Split 1 is the root, giving two children whose own split gains are very different — 4.0 and 0.3.", active = setOf(l, r))

    val ll = builder.add("gain 2.1", l, 0)
    val lr = builder.add("gain 1.8", l, 1)
    val rl = builder.add("gain 0.2", r, 0)
    val rr = builder.add("gain 0.1", r, 1)
    builder.frame(
        "Splits 2 and 3 go to both nodes at this level, because that is what level-wise means. Realized gain 4.0 + 0.3 = 4.3, and one of those splits went to a node with almost nothing left to give. Four leaves, depth 2.",
        marked = setOf(ll, lr, rl, rr),
    )

    listOf(rr, rl, lr, ll, r, l).forEach { builder.remove(it) }
    builder.frame("Now the same three splits, grown leaf-wise: each time, split whichever leaf anywhere in the tree offers the largest gain, regardless of depth.")

    val a = builder.add("gain 4.0", root, 0)
    val b = builder.add("gain 0.3", root, 1)
    builder.frame("Split 1 is identical — the strategies only diverge once there is a choice of leaf.", active = setOf(a, b))

    val c = builder.add("gain 2.1", a, 0)
    val d = builder.add("gain 1.8", a, 1)
    builder.frame("Split 2: the best available leaf is the 4.0 node, so take it. Level-wise would have made the same choice here.", active = setOf(c, d))

    builder.frame(
        "Split 3 is where they part. The candidates are now 0.3, 2.1 and 1.8, and leaf-wise takes the 2.1 — leaving the 0.3 node unsplit rather than spending a budgeted split on it. Realized gain 4.0 + 2.1 = 6.1 against level-wise's 4.3, from the same three splits and the same four leaves.",
        active = setOf(c),
        marked = setOf(b, d),
    )

    val e = builder.add("gain 1.2", c, 0)
    val f = builder.add("gain 0.9", c, 1)
    builder.frame(
        "The resulting tree is deeper and lopsided. That is the trade: more gain per leaf, and a shape that overfits small datasets readily — which is why num_leaves and min_data_in_leaf matter far more here than max_depth does in XGBoost. The other half of LightGBM's speed is histogram binning: features bucketed into ~255 bins, so split search costs O(bins) instead of sorting every value.",
        marked = setOf(b, d, e, f),
        path = setOf(root, a, c),
    )
    return builder.frames
}

private fun catboostFrames(): List<TreeFrame> {
    val builder = TreeBuilder()

    val root = builder.add("x₁ < 5?", null, 0)
    val l = builder.add("x₁ < 5?", root, 0)
    val r = builder.add("x₁ < 5?", root, 1)
    builder.frame(
        "CatBoost grows oblivious (symmetric) trees: every node at the same depth tests the identical condition. Here the root's test is reused across the whole level, which is not a coincidence but the constraint.",
        active = setOf(root),
    )

    builder.relabel(l, "x₂ < 2?")
    builder.relabel(r, "x₂ < 2?")
    builder.frame(
        "Depth 2 picks one condition and applies it to both nodes. The tree is fully described by a list of (feature, threshold) pairs — one per level — rather than a per-node structure.",
        active = setOf(l, r),
    )

    val leaves = (0 until 4).map { i ->
        builder.add("leaf $i", if (i < 2) l else r, i % 2)
    }
    builder.frame(
        "That makes prediction a bit-trick rather than a traversal: evaluate each level's condition to a 0 or 1, concatenate them into an index, and look the leaf up directly. No branching, no pointer chasing — which is why CatBoost's inference is unusually fast.",
        marked = leaves.toSet(),
    )

    builder.frame(
        "The symmetry is also a regularizer. A balanced tree forced to reuse conditions is far less expressive than LightGBM's leaf-wise growth, so it overfits less on small data — and it is a real constraint, so on large datasets with complex interactions it can cost accuracy.",
        marked = leaves.toSet(),
        path = setOf(root, l, r),
    )

    builder.frame(
        "CatBoost's other two ideas are not visible in the tree shape. Ordered target statistics encode a categorical value using the target mean over only the rows *before* it in a random permutation, so a row never contributes to its own encoding — plain target encoding leaks the label and overfits badly. Ordered boosting applies the same trick to residuals, computing each row's gradient from a model that never saw it.",
        marked = leaves.toSet(),
    )
    return builder.frames
}

// ── Divisive hierarchical clustering ─────────────────────────────────────────
// Agglomerative builds the dendrogram from the leaves up; divisive builds it from the root down.
// Same tree, opposite direction, and different things go wrong at each end.

private fun divisiveFrames(): List<TreeFrame> {
    val builder = TreeBuilder()
    val points = compactBlobs
    val splits = divisive(points, 4)

    val nodeFor = HashMap<List<Int>, Int>()
    val root = builder.add("all ${points.size}", null, 0)
    nodeFor[points.indices.toList()] = root

    builder.frame(
        "Divisive clustering starts at the opposite end from agglomerative: everything in one cluster, split downward. Both produce a dendrogram; the difference is which end of it is computed first.",
        active = setOf(root),
    )

    splits.forEachIndexed { index, step ->
        val parent = nodeFor[step.parent] ?: root
        val left = builder.add("${step.left.size} pts", parent, 0)
        val right = builder.add("${step.right.size} pts", parent, 1)
        nodeFor[step.left] = left
        nodeFor[step.right] = right
        builder.relabel(parent, "split @ ${"%.2f".format(step.diameter)}")

        builder.frame(
            "Split ${index + 1}: the least cohesive cluster is the one with the largest diameter (${"%.2f".format(step.diameter)}), so it goes first. It divides into ${step.left.size} and ${step.right.size} points.",
            active = setOf(left, right),
            path = setOf(parent),
        )
    }

    builder.frame(
        "The exact version of this is intractable — finding the best split of a cluster of n points means checking 2^(n−1) − 1 partitions. Practical implementations approximate, and this one uses 2-means on the chosen cluster, which is what DIANA-style implementations do.",
        marked = nodeFor.values.toSet() - root,
    )

    builder.frame(
        "The tradeoff against agglomerative is about where each one can go wrong. Divisive makes its most consequential decision first, with the whole dataset in view, so a good top-level split is likely — but the cost is high and a bad early split is never revisited. Agglomerative decides locally and cheaply at the bottom, where a wrong early merge is equally permanent but affects far fewer points.",
        marked = nodeFor.values.toSet() - root,
        path = setOf(root),
    )
    return builder.frames
}

// ── Aho-Corasick: the trie, its failure links, then one pass over the text ───
// The textbook dictionary, because it is the smallest one that exercises every case: "hers" fails to
// "s" in a different branch, and "she" has to report "he" through an output link.
private fun ahoCorasickFrames(): List<TreeFrame> {
    val patterns = listOf("he", "she", "his", "hers")
    val text = "ushers"
    val b = TreeBuilder()

    val root = b.add("·", null, 0)
    // Trie node id per prefix string, so failure links can be resolved by suffix lookup.
    val nodeOf = linkedMapOf("" to root)
    val terminal = mutableMapOf<Int, String>()

    b.frame("Root. Every pattern will hang off it, sharing whatever prefixes they have in common.", marked = setOf(root))

    for ((index, pattern) in patterns.withIndex()) {
        var prefix = ""
        var parent = root
        val touched = mutableSetOf(root)
        for (c in pattern) {
            val next = prefix + c
            val existing = nodeOf[next]
            parent = existing ?: b.add(c.toString(), parent, index).also { nodeOf[next] = it }
            touched += parent
            prefix = next
        }
        terminal[parent] = pattern
        b.frame(
            "Insert \"$pattern\". ${if (patterns.take(index).any { pattern.startsWith(it) || it.startsWith(pattern) }) "It shares a prefix with an earlier pattern, so those nodes are reused." else "A fresh branch."}",
            path = touched,
            marked = setOf(parent),
        )
    }

    // Failure links by BFS: a node's target is always shallower, so depth order means it is ready.
    val fail = mutableMapOf(root to root)
    val links = mutableListOf<TreeLink>()
    val byDepth = nodeOf.keys.filter { it.isNotEmpty() }.sortedBy { it.length }

    b.frame(
        "Now the failure links. A node's link points at the node for the longest proper suffix of its own string — " +
            "KMP's prefix function, except the target usually lives in a different branch.",
        marked = terminal.keys,
    )

    for (prefix in byDepth) {
        val node = nodeOf.getValue(prefix)
        // Longest proper suffix of `prefix` that is itself a node.
        val target = (1 until prefix.length).firstNotNullOfOrNull { nodeOf[prefix.substring(it)] } ?: root
        fail[node] = target
        links += TreeLink(node, target)
        b.frame(
            "\"$prefix\" → " + if (target == root) {
                "no proper suffix of it is in the trie, so it fails to the root."
            } else {
                "its longest suffix present in the trie is \"${nodeOf.entries.first { it.value == target }.key}\", so that is its failure target."
            },
            active = setOf(node),
            marked = terminal.keys,
            links = links.toList(),
        )
    }

    // Output links: a terminal reachable by following failures from here.
    val outputVia = mutableMapOf<Int, String>()
    for (prefix in byDepth) {
        val node = nodeOf.getValue(prefix)
        var f = fail.getValue(node)
        while (f != root) {
            terminal[f]?.let { outputVia[node] = it }
            if (outputVia.containsKey(node)) break
            f = fail.getValue(f)
        }
    }
    b.frame(
        "One piece left. \"she\" ends at a node whose failure target is \"he\", and \"he\" is itself a pattern — so landing on " +
            "\"she\" has also matched \"he\". Reporting every terminal up the failure chain is what catches those; without it the " +
            "automaton finds only the longest match at each position.",
        marked = terminal.keys,
        active = outputVia.keys,
        links = links.toList(),
    )

    // The scan. The text pointer never moves backwards, which is the guarantee inherited from KMP.
    var current = root
    val found = mutableListOf<String>()
    // The prefix a node stands for. Kept separate from anything display-shaped: the root's prefix is
    // the empty string, and a lookup of prefixOf(root) + c is how the first character finds its edge.
    val prefixOf: Map<Int, String> = nodeOf.entries.associate { (prefix, id) -> id to prefix }
    fun show(id: Int) = prefixOf.getValue(id).ifEmpty { "root" }

    for ((i, c) in text.withIndex()) {
        val before = current
        var walked = 0
        while (current != root && nodeOf[prefixOf.getValue(current) + c] == null) {
            current = fail.getValue(current)
            walked++
        }
        current = nodeOf[prefixOf.getValue(current) + c] ?: root

        val hits = listOfNotNull(terminal[current], outputVia[current])
        found += hits
        b.frame(
            "Text '$c' (index $i): " +
                (if (walked > 0) "no edge from \"${show(before)}\", so follow $walked failure link${if (walked == 1) "" else "s"} first, then " else "") +
                "move to \"${show(current)}\". " +
                if (hits.isEmpty()) "No pattern ends here." else "Match: ${hits.joinToString(" and ") { "\"$it\"" }}." +
                    if (hits.size > 1) " The second one came through the output link." else "",
            active = setOf(current),
            marked = terminal.keys,
            links = links.toList(),
        )
    }

    // The narration of the frames above states what the automaton finds, so pin it: "he" is only
    // reachable through the output link off "she", and it is the first thing a wrong transition drops.
    require(found == listOf("she", "he", "hers")) { "aho_corasick found $found, expected [she, he, hers]" }

    b.frame(
        "Found ${found.joinToString(", ") { "\"$it\"" }} in one pass over \"$text\". The cost is O(n + m + z) — text length, total " +
            "dictionary length, matches reported — with the number of patterns absent from the scan term entirely.",
        marked = terminal.keys,
        links = links.toList(),
    )
    return b.frames
}

// ── FP-Growth: the tree is the algorithm ─────────────────────────────────────
// Apriori and Eclat both search a candidate space. FP-Growth does not generate candidates at all —
// it compresses the database into a prefix tree in two passes and then reads the frequent itemsets
// out of it recursively. Shown here because the compression and the header links are the whole idea,
// and neither is visible in a row of cells.

private fun fpGrowthFrames(): List<TreeFrame> {
    val builder = TreeBuilder()
    val order = fpItemOrder()
    val root = builder.add("root", null, 0)

    builder.frame(
        status = "Pass 1 counts single items and throws away everything below support ${MinSupport}: " +
            "${order.joinToString { "$it=${support(setOf(it))}" }} survive, " +
            "${basketItems.filter { it !in order }.joinToString { "$it=${support(setOf(it))}" }} does not. The " +
            "surviving items are then ordered by descending count — that order is what makes the tree compress, " +
            "because the most common items become shared prefixes.",
        active = setOf(root),
    )

    // Node ids in this builder, keyed the same way the tree in AssociationMath is.
    val ids = mutableMapOf<Pair<Int, String>, Int>()
    val counts = mutableMapOf<Int, Int>()
    val perItem = mutableMapOf<String, MutableList<Int>>()

    transactions.forEachIndexed { index, transaction ->
        val sorted = fpSortedTransaction(transaction)
        var parent = root
        val touched = mutableSetOf<Int>()
        var reusedDepth = 0
        var reusing = true
        sorted.forEachIndexed { depth, item ->
            val key = parent to item
            val existing = ids[key]
            if (existing != null) {
                counts[existing] = counts.getValue(existing) + 1
                builder.relabel(existing, "$item:${counts.getValue(existing)}")
                parent = existing
                if (reusing) reusedDepth = depth + 1
            } else {
                reusing = false
                val id = builder.add("$item:1", parent, depth)
                ids[key] = id
                counts[id] = 1
                perItem.getOrPut(item) { mutableListOf() }.add(id)
                parent = id
            }
            touched += parent
        }
        builder.frame(
            status = "Basket ${index + 1} is ${transaction.sorted().joinToString("")}" +
                (if (transaction.size != sorted.size) ", which becomes ${sorted.joinToString("")} once infrequent items are dropped" else "") +
                ", sorted into header order. " +
                if (reusedDepth > 0) {
                    "Its first $reusedDepth item${if (reusedDepth > 1) "s" else ""} already exist as a path, so those " +
                        "nodes just have their counter incremented — no new storage at all. That reuse is the " +
                        "compression."
                } else {
                    "No existing path shares its prefix, so it becomes a new branch off the root."
                },
            active = touched,
        )
    }

    val tree = buildFpTree()
    val slots = transactions.sumOf { fpSortedTransaction(it).size }
    builder.frame(
        status = "All ${transactions.size} baskets are in. They contained $slots item slots between them and the tree " +
            "holds ${tree.nodes.size} nodes — the database, complete, with every count recoverable and every " +
            "duplicate prefix stored once. On a real basket dataset that ratio is the reason this method exists.",
    )

    // Header links: the same-item chain the mining step walks.
    val target = "E"
    val chain = perItem[target].orEmpty()
    val links = chain.zipWithNext { a, b -> TreeLink(a, b) }
    builder.frame(
        status = "The header table keeps, for each item, a linked list through every node holding it. Here is $target's: " +
            "${chain.size} nodes scattered across the tree, ${chain.sumOf { counts.getValue(it) }} occurrences in " +
            "total — which matches the ${support(setOf(target))} baskets containing $target, as it must.",
        active = chain.toSet(),
        links = links,
    )

    val base = conditionalPatternBase(tree, target)
    builder.frame(
        status = "Mining $target: walk that chain and read the path above each node. The conditional pattern base is " +
            "${base.joinToString { "${it.path.joinToString("").ifEmpty { "∅" }}×${it.count}" }} — every context in " +
            "which $target occurred, with its multiplicity.",
        marked = chain.toSet(),
        path = chain.flatMap { id ->
            generateSequence(id) { current -> builder.parentOf(current) }.toList()
        }.toSet() - chain.toSet() - setOf(root),
        links = links,
    )

    val conditionalCounts = base
        .flatMap { pattern -> pattern.path.map { it to pattern.count } }
        .groupBy({ it.first }, { it.second })
        .mapValues { it.value.sum() }
        .filterValues { it >= MinSupport }
    builder.frame(
        status = "Count items within that base: " +
            "${base.flatMap { p -> p.path.map { it } }.distinct().sorted().joinToString { item ->
                "$item=${base.filter { item in it.path }.sumOf { it.count }}"
            }}. Only ${conditionalCounts.keys.joinToString().ifEmpty { "nothing" }} clears support ${MinSupport}, so " +
            "${conditionalCounts.keys.joinToString { "$it$target" }} is frequent — support " +
            "${conditionalCounts.values.firstOrNull() ?: 0}, and Apriori found exactly the same set by counting " +
            "candidates instead.",
        marked = chain.toSet(),
        links = links,
    )

    builder.frame(
        status = "Two database passes total: one to count items, one to build the tree. Everything after that is " +
            "recursion over conditional trees held in memory, with no candidate generation and no further scans. " +
            "Apriori needed a pass per level. The cost is the tree itself — on data with little shared prefix " +
            "structure it can be larger than the database, and then FP-Growth is the wrong choice.",
        marked = chain.toSet(),
    )
    return builder.frames
}

// ── D2 · Dependency parsing: an arc-standard transition sequence ─────────────

private fun dependencyFrames(): List<TreeFrame> {
    val b = TreeBuilder()
    val words = DependencyLab.sentence
    val steps = DependencyLab.parse()

    // One node per word plus ROOT; arcs become parent links as the parser builds them.
    val rootId = b.add("ROOT", null, 0)
    val nodeOf = words.indices.associate { i -> (i + 1) to b.add(words[i], null, i + 1) }

    b.frame(
        "A dependency parse is a set of labelled arcs, one per word: every token gets exactly one head, and " +
            "one token — the main verb here — is headed by ROOT. No phrase nodes exist at all, which is the " +
            "difference from a constituency tree.",
        active = nodeOf.values.toSet(),
    )

    b.frame(
        "Arc-standard parsing runs a stack, a buffer and three moves. SHIFT pushes the next word; LEFT-ARC " +
            "attaches the second stack item to the top and pops it; RIGHT-ARC attaches the top to the second " +
            "and pops it. An arc is only drawn once its dependent has all of its own children, so nothing has " +
            "to be revisited.",
        marked = setOf(rootId),
    )

    steps.forEachIndexed { index, step ->
        // Re-hang every node according to the arcs built so far.
        nodeOf.forEach { (word, id) ->
            val arc = step.arcs.firstOrNull { it.second == word }
            if (arc == null) b.reparent(id, null, word)
            else b.reparent(id, if (arc.first == 0) rootId else nodeOf.getValue(arc.first), word)
        }
        step.arcs.forEach { (head, dependent, label) ->
            b.relabel(nodeOf.getValue(dependent), "${words[dependent - 1]} · $label")
        }

        val stackText = step.stack.joinToString(" ") { DependencyLab.wordAt(it) }
        val bufferText = step.buffer.joinToString(" ") { DependencyLab.wordAt(it) }
        val moveText = when (step.move) {
            DependencyLab.Move.SHIFT -> "SHIFT — push the next word; no arc yet."
            DependencyLab.Move.LEFT_ARC -> "LEFT-ARC (${step.label}) — the second stack item becomes a dependent of the top and is removed."
            DependencyLab.Move.RIGHT_ARC -> "RIGHT-ARC (${step.label}) — the top becomes a dependent of the second item and is removed."
        }
        b.frame(
            "Step ${index + 1} of ${steps.size}: $moveText  ·  stack [$stackText]  buffer [$bufferText]  ·  ${step.arcs.size} arc${if (step.arcs.size == 1) "" else "s"} built.",
            active = step.stack.mapNotNull { if (it == 0) rootId else nodeOf[it] }.toSet(),
            marked = step.arcs.map { nodeOf.getValue(it.second) }.toSet(),
        )
    }

    b.frame(
        "Done in ${DependencyLab.transitionCount()} transitions, which is exactly 2n for ${words.size} words — every word is shifted once and " +
            "attached once, so a greedy parser is linear in sentence length. Chart-based (graph) parsers are " +
            "O(n²) or O(n³) but search globally; the transition family trades that for speed and a classifier " +
            "that only has to pick the next move.",
        marked = nodeOf.values.toSet(),
        path = setOf(rootId),
    )

    b.frame(
        "Scoring is per word. UAS counts heads only; LAS also requires the relation to match. A parse with " +
            "\"small\" attached to the verb instead of the noun, and \"cat\" labelled nsubj instead of obj, " +
            "scores UAS ${"%.2f".format(DependencyLab.uas(DependencyLab.predictedHeads))} and LAS ${"%.2f".format(DependencyLab.las(DependencyLab.predictedHeads, DependencyLab.predictedLabels))} — the gap between them is entirely label errors on " +
            "attachments that were right.",
        marked = setOf(nodeOf.getValue(2), nodeOf.getValue(6)),
    )

    b.frame(
        "One structural limit: arc-standard can only build projective trees — no two arcs may cross. " +
            "\"A hearing is scheduled on the issue today\" needs a crossing arc (hearing → on crosses " +
            "scheduled → today) and is unreachable by any sequence of these three moves. Roughly 1% of English " +
            "sentences and far more in German or Czech are non-projective, which is why pseudo-projective " +
            "transforms, the swap transition and graph-based parsers exist.",
        marked = nodeOf.values.toSet(),
    )
    return b.frames
}

// ── D2 · Constituency parsing: phrases, brackets and head rules ─────────────

private fun constituencyFrames(): List<TreeFrame> {
    val b = TreeBuilder()
    val gold = ConstituencyLab.gold

    // Build the gold tree once; frames differ in what they highlight and in the flat-VP variant.
    val ids = mutableMapOf<String, Int>()
    fun build(node: ConstituencyLab.Node, parent: Int?, order: Int, path: String): Int {
        val id = b.add(if (node.isLeaf) "${node.label} ${node.word}" else node.label, parent, order)
        ids[path] = id
        node.children.forEachIndexed { i, child -> build(child, id, i, "$path/$i") }
        return id
    }
    val root = build(gold, null, 0, "")

    b.frame(
        "A constituency parse groups words into nested phrases: the sentence is an NP and a VP, the NP is a " +
            "determiner, an adjective and a noun. Every internal node is a phrase, and the words only appear at " +
            "the leaves — where a dependency parse has arcs between words and no phrase nodes at all.",
        marked = setOf(root),
    )

    b.frame(
        "The units it is scored on are labelled spans: ${ConstituencyLab.spanList(gold).joinToString(", ") { "${it.first}[${it.second},${it.third})" }}. " +
            "Part-of-speech nodes are excluded by convention, because a tagger's accuracy would otherwise " +
            "inflate the parser's score.",
        marked = setOf(root, ids.getValue("/0"), ids.getValue("/1"), ids.getValue("/1/1")),
    )

    // The flat-VP variant: detach the object NP's children and drop the NP node.
    val objectNp = ids.getValue("/1/1")
    val vp = ids.getValue("/1")
    val det = ids.getValue("/1/1/0")
    val noun = ids.getValue("/1/1/1")
    b.reparent(det, vp, 1)
    b.reparent(noun, vp, 2)
    b.remove(objectNp)

    val evalb = ConstituencyLab.evalb()
    b.frame(
        "Here is a parse that misses one phrase: the object NP is flattened into the VP. Every bracket it does " +
            "predict is correct, so precision is ${"%.2f".format(evalb.precision)}, but it recovers only ${ConstituencyLab.spanList(ConstituencyLab.predicted).size} of the gold tree's ${ConstituencyLab.spanList(gold).size} " +
            "spans, so recall is ${"%.2f".format(evalb.recall)} and F1 is ${"%.2f".format(evalb.f1)}. That asymmetry is why evalb reports all three: a parser " +
            "that emits fewer, safer brackets can hold precision at 1.00 indefinitely.",
        active = setOf(det, noun),
        marked = setOf(vp),
    )

    // Restore the gold shape.
    val restoredNp = b.add("NP", vp, 1)
    b.reparent(det, restoredNp, 0)
    b.reparent(noun, restoredNp, 1)
    b.frame(
        "Restored. The two formalisms are convertible, and the conversion is a table of head rules: the head " +
            "of a VP is its verb, the head of an NP is its rightmost noun, the head of an S is its VP's head. " +
            "Percolate those upward and every phrase gets a head word.",
        marked = setOf(restoredNp, vp, ids.getValue("/0")),
    )

    val heads = ConstituencyLab.toDependencies()
    b.frame(
        "Running that conversion on this tree produces heads ${heads.joinToString(", ")} — identical to the " +
            "dependency lab's gold parse for the same sentence, computed here rather than asserted. This is how " +
            "the Penn Treebank became a dependency treebank, and why a claim that one formalism carries more " +
            "information than the other needs to be about the *annotation*, not the notation.",
        marked = setOf(root),
        path = ids.values.toSet() - root,
    )

    b.frame(
        "Which to use: constituency when the phrase itself is the object of interest — extracting noun phrases, " +
            "grammar checking, anything that asks \"is this a well-formed clause\" — and dependency when the " +
            "question is what relates to what, which is most of information extraction and nearly all " +
            "multilingual work, because dependency annotation transfers across languages with far less " +
            "redesign (that is the entire premise of Universal Dependencies).",
        marked = setOf(root),
    )
    return b.frames
}

// ── D1 · Probabilistic CFG: CYK over an ambiguous sentence ───────────────────

private fun pcfgProb(p: Double): String = if (p >= 0.01) "%.2f".format(p) else "%.4f".format(p)

private fun pcfgFrames(): List<TreeFrame> {
    val b = TreeBuilder()
    val words = PcfgLab.sentence

    // Both readings, scored by the math file — the labels below print what it computed.
    val vpReading = PcfgLab.vpAttachment()
    val vpTopParse = vpReading.right!!
    val vpCoreParse = vpTopParse.left!!
    val ppParse = vpTopParse.right!!
    val npReading = PcfgLab.npAttachment()
    val vpBParse = npReading.right!!
    val bigNpParse = vpBParse.right!!
    val npManParse = vpCoreParse.right!!
    val npScopeParse = ppParse.right!!

    val wordIds = words.mapIndexed { i, w -> b.add(w, null, i) }
    b.frame(
        "Seven words, and the grammar is six binary rules plus a lexicon. A PCFG is a CFG where every rule " +
            "carries a probability, and the rules sharing a left-hand side sum to 1 — so VP → V NP at 0.70 and " +
            "VP → VP PP at 0.30 are a distribution over what a verb phrase can be.",
        active = wordIds.toSet(),
    )

    // Lexical rules: the CYK diagonal.
    val preLabels = listOf(
        "NP ${pcfgProb(0.40)}", "V ${pcfgProb(1.0)}", "Det ${pcfgProb(1.0)}", "N ${pcfgProb(0.50)}",
        "P ${pcfgProb(1.0)}", "Det ${pcfgProb(1.0)}", "N ${pcfgProb(0.50)}",
    )
    val preIds = wordIds.mapIndexed { i, wordId ->
        val id = b.add(preLabels[i], null, i)
        b.reparent(wordId, id, 0)
        id
    }
    b.frame(
        "Lexical rules first — this is CYK's diagonal, the width-1 spans. \"the\" is only ever a determiner, " +
            "but a real lexicon gives most words several tags, and every one of them starts a different parse.",
        marked = preIds.toSet(),
    )

    val npMan = b.add("NP ${pcfgProb(npManParse.probability)}", null, 2)
    b.reparent(preIds[2], npMan, 0)
    b.reparent(preIds[3], npMan, 1)
    val npScope = b.add("NP ${pcfgProb(npScopeParse.probability)}", null, 5)
    b.reparent(preIds[5], npScope, 0)
    b.reparent(preIds[6], npScope, 1)
    b.frame(
        "Width-2 spans: NP → Det N fires twice, at 0.40 × 1.0 × 0.50 = ${pcfgProb(npManParse.probability)} each. A cell's probability is the " +
            "rule's probability times its children's — the parse tree's score is the product of every rule in it.",
        active = setOf(npMan, npScope),
    )

    val pp = b.add("PP ${pcfgProb(ppParse.probability)}", null, 4)
    b.reparent(preIds[4], pp, 0)
    b.reparent(npScope, pp, 1)
    val vpCore = b.add("VP ${pcfgProb(vpCoreParse.probability)}", null, 1)
    b.reparent(preIds[1], vpCore, 0)
    b.reparent(npMan, vpCore, 1)
    b.frame(
        "\"with the telescope\" becomes a PP at ${pcfgProb(ppParse.probability)}, and \"saw the man\" a VP at ${pcfgProb(vpCoreParse.probability)}. Everything so far is " +
            "forced. The ambiguity is entirely about what the PP attaches to — and both answers are grammatical.",
        active = setOf(pp, vpCore),
    )

    val vpTop = b.add("VP ${pcfgProb(vpTopParse.probability)}", null, 1)
    b.reparent(vpCore, vpTop, 0)
    b.reparent(pp, vpTop, 1)
    val sA = b.add("S ${pcfgProb(vpReading.probability)}", null, 0)
    b.reparent(preIds[0], sA, 0)
    b.reparent(vpTop, sA, 1)
    b.frame(
        "Reading 1 — VP attachment: the PP modifies the seeing, so she used the telescope to look. " +
            "VP → VP PP at 0.30 × ${pcfgProb(vpCoreParse.probability)} × ${pcfgProb(ppParse.probability)} = ${pcfgProb(vpTopParse.probability)}, and S → NP VP gives ${pcfgProb(vpReading.probability)}.",
        marked = setOf(sA, vpTop),
        path = setOf(vpCore, pp),
    )

    // Rebuild the same span the other way. The children are reused, which is the point of the chart
    // — so each node is detached from its parent before that parent is removed, and no frame is ever
    // snapshotted holding a link to a node that is gone.
    b.reparent(preIds[0], null, 0)
    b.reparent(vpTop, null, 1)
    b.remove(sA)
    b.reparent(vpCore, null, 1)
    b.reparent(pp, null, 4)
    b.remove(vpTop)
    b.reparent(preIds[1], null, 1)
    b.reparent(npMan, null, 2)
    b.remove(vpCore)
    val bigNp = b.add("NP ${pcfgProb(bigNpParse.probability)}", null, 2)
    b.reparent(npMan, bigNp, 0)
    b.reparent(pp, bigNp, 1)
    val vpB = b.add("VP ${pcfgProb(vpBParse.probability)}", null, 1)
    b.reparent(preIds[1], vpB, 0)
    b.reparent(bigNp, vpB, 1)
    val sB = b.add("S ${pcfgProb(npReading.probability)}", null, 0)
    b.reparent(preIds[0], sB, 0)
    b.reparent(vpB, sB, 1)
    b.frame(
        "Reading 2 — NP attachment: the PP modifies the man, so he was the one holding the telescope. Same " +
            "words, same grammar, different tree: NP → NP PP at 0.20 × ${pcfgProb(npManParse.probability)} × ${pcfgProb(ppParse.probability)} = ${pcfgProb(bigNpParse.probability)}, then " +
            "VP → V NP and S → NP VP for ${pcfgProb(npReading.probability)}.",
        marked = setOf(sB, bigNp),
        path = setOf(vpB),
    )

    b.reparent(preIds[0], null, 0)
    b.reparent(vpB, null, 1)
    b.remove(sB)
    b.reparent(preIds[1], null, 1)
    b.reparent(bigNp, null, 2)
    b.remove(vpB)
    b.reparent(npMan, null, 2)
    b.reparent(pp, null, 4)
    b.remove(bigNp)
    val vpCore2 = b.add("VP ${pcfgProb(vpCoreParse.probability)}", null, 1)
    b.reparent(preIds[1], vpCore2, 0)
    b.reparent(npMan, vpCore2, 1)
    val vpTop2 = b.add("VP ${pcfgProb(vpTopParse.probability)}", null, 1)
    b.reparent(vpCore2, vpTop2, 0)
    b.reparent(pp, vpTop2, 1)
    val sWinner = b.add("S ${pcfgProb(vpReading.probability)}", null, 0)
    b.reparent(preIds[0], sWinner, 0)
    b.reparent(vpTop2, sWinner, 1)
    b.frame(
        "${pcfgProb(vpReading.probability)} against ${pcfgProb(npReading.probability)} — VP attachment wins by ${"%.2f".format(PcfgLab.attachmentRatio)}×, and CYK keeps only the winner per cell " +
            "plus a backpointer, so the losing parse costs no storage. The disambiguation is the whole reason " +
            "the grammar is probabilistic: a plain CFG returns both trees and cannot rank them.",
        marked = setOf(sWinner, vpTop2),
    )

    b.frame(
        "The cost: ${PcfgLab.filledCells()} chart cells filled out of n(n+1)/2 = ${words.size * (words.size + 1) / 2} spans, over ${PcfgLab.splitsConsidered()} split points and " +
            "the whole grammar at each one — O(n³·|G|). Same table shape as matrix-chain multiplication, with " +
            "max in place of min.",
        marked = setOf(sWinner),
        path = setOf(vpTop2, vpCore2, pp, npMan, npScope),
    )

    b.frame(
        "And the limit worth remembering: the rule probabilities here are estimated from a treebank and are " +
            "context-free by construction, so this grammar prefers VP attachment for *every* sentence of this " +
            "shape — it has no idea a telescope is an instrument of seeing. Lexicalised PCFGs condition each " +
            "rule on its head word (saw … with … telescope) to fix exactly this, at the cost of a far sparser " +
            "table.",
        marked = setOf(sWinner),
    )
    return b.frames
}

// ── D5 · Tree of Thoughts ────────────────────────────────────────────────────
// A real search over Game of 24, not a drawing of one. The greedy path, the beam and the exhaustive
// count all come out of `TotLab`, and so does the evaluator's ranking of the frontier — which is the
// thing the batch measured and did not expect.

private fun chainFrames(builder: TreeBuilder, trace: List<String>, root: Int): List<Int> {
    var parent = root
    return trace.map { step ->
        val id = builder.add(step, parent, 0)
        parent = id
        id
    }
}

private fun treeOfThoughtsFrames(): List<TreeFrame> {
    val frames = mutableListOf<TreeFrame>()
    val (expanded, solutions) = TotLab.exhaustive()

    // Act 1 — the problem and its real size.
    val intro = TreeBuilder()
    val introRoot = intro.add(TotLab.label(TotLab.puzzle), null, 0)
    intro.frame(
        "Game of 24: combine ${TotLab.label(TotLab.puzzle)} with + − × ÷ until one number is left and it is 24. " +
            "Each operation replaces two numbers with one, so the search is exactly three moves deep. Expanded " +
            "exhaustively this tree visits $expanded states and ends at 24 along $solutions of them — small " +
            "enough to check every claim below by brute force.",
        active = setOf(introRoot),
    )
    frames += intro.frames

    // Act 2 — the greedy path, which is what a single chain of thought is.
    val greedy = TotLab.beam(1, depth = 1)
    val greedyBuilder = TreeBuilder()
    val greedyRoot = greedyBuilder.add(TotLab.label(TotLab.puzzle), null, 0)
    val greedyNodes = chainFrames(greedyBuilder, greedy.trace, greedyRoot)
    greedyNodes.forEachIndexed { index, id ->
        greedyBuilder.frame(
            when (index) {
                0 -> "A chain of thought is beam width 1: pick the best-looking next step and commit. The " +
                    "evaluator here is the cheap one — \"could one more operation on some pair land on 24?\" — " +
                    "which is the stand-in for the paper's value prompt, and it likes ${greedy.trace[0]}."
                1 -> "Second move, still no way back. The state is now three numbers and the same evaluator " +
                    "picks ${greedy.trace[1]}. Nothing has gone visibly wrong yet, which is the problem: a " +
                    "single chain gives no signal that it is already in a dead branch."
                else -> "Third move, and the chain lands on ${greedy.trace.last().substringAfter("= ")} rather " +
                    "than 24. It cannot backtrack, because it never kept an alternative. Width 1 fails on this " +
                    "puzzle for a reason that has nothing to do with arithmetic."
            },
            active = setOf(id),
            path = greedyNodes.take(index).toSet() + greedyRoot,
        )
    }
    frames += greedyBuilder.frames

    // Act 3 — how bad the evaluator actually is, measured.
    val ranked = TotLab.rankedFrontier(depth = 1)
    val (firstSolvableRank, solvableInTopFive, frontierSize) = TotLab.evaluatorQuality(5, depth = 1)
    val rankBuilder = TreeBuilder()
    val rankRoot = rankBuilder.add(TotLab.label(TotLab.puzzle), null, 0)
    val shown = ranked.take(5).mapIndexed { index, (state, score, _) ->
        rankBuilder.add("${TotLab.label(state)}  (${"%.1f".format(score)})", rankRoot, index)
    }
    val best = ranked[firstSolvableRank - 1]
    val bestNode = rankBuilder.add("${TotLab.label(best.first)}  (${"%.1f".format(best.second)})", rankRoot, 5)
    rankBuilder.frame(
        "So before widening the search, measure the evaluator. It ranks $frontierSize distinct first moves, and " +
            "the highest-ranked move that can still reach 24 is its ${firstSolvableRank}th — the five it likes " +
            "best contain $solvableInTopFive that can. This is not a bad implementation; it is what a cheap " +
            "one-step heuristic is worth on a four-number state, because it ignores the numbers left over.",
        active = shown.toSet(),
        marked = setOf(bestNode),
    )
    frames += rankBuilder.frames

    // Act 4 — width substitutes for evaluator quality.
    val narrow = TotLab.beam(5, depth = 1)
    val needed = TotLab.widthNeeded(1)
    val winner = TotLab.beam(needed, depth = 1)
    val widthBuilder = TreeBuilder()
    val widthRoot = widthBuilder.add(TotLab.label(TotLab.puzzle), null, 0)
    val keptFive = ranked.take(5).mapIndexed { index, (state, _, _) ->
        widthBuilder.add(TotLab.label(state), widthRoot, index)
    }
    widthBuilder.frame(
        "Beam width 5 keeps the evaluator's top five and drops the other ${frontierSize - 5}. Since none of " +
            "those five can reach 24, the beam is already lost at depth 1 and spends the rest of the search " +
            "confirming it: ${narrow.expanded} states expanded, best result " +
            "${narrow.trace.last().substringAfter("= ")}.",
        active = keptFive.toSet(),
    )
    val extra = ranked.subList(5, needed).mapIndexed { index, (state, _, solvable) ->
        widthBuilder.add(TotLab.label(state), widthRoot, 5 + index) to solvable
    }
    widthBuilder.frame(
        "Width $needed is where it turns over — the smallest beam that keeps a state the puzzle can be solved " +
            "from. Nothing about the evaluator changed. The search simply stopped trusting it enough to throw " +
            "the answer away, which is the entire argument for Tree of Thoughts.",
        active = extra.filter { it.second }.map { it.first }.toSet(),
        path = keptFive.toSet(),
    )
    val winPath = chainFrames(widthBuilder, winner.trace, extra.first { it.second }.first)
    widthBuilder.frame(
        "The solution the surviving branch reaches: ${winner.trace.joinToString(", ")}. It cost " +
            "${winner.expanded} expanded states and ${winner.evaluatorCalls} evaluator calls against " +
            "${greedy.expanded} and ${greedy.evaluatorCalls} for the greedy chain — roughly " +
            "${"%.0f".format(winner.evaluatorCalls.toDouble() / greedy.evaluatorCalls)}× the evaluation, for " +
            "the difference between failing and finishing.",
        marked = winPath.toSet(),
        path = setOf(widthRoot) + extra.first { it.second }.first,
    )
    frames += widthBuilder.frames

    // Act 5 — the other axis, and the trade the numbers actually show.
    val deep = TotLab.beam(1, depth = 2)
    val deepWidth = TotLab.widthNeeded(2)
    val (deepRank, deepTopFive, _) = TotLab.evaluatorQuality(5, depth = 2)
    val deepBuilder = TreeBuilder()
    val deepRoot = deepBuilder.add(TotLab.label(TotLab.puzzle), null, 0)
    val deepNodes = chainFrames(deepBuilder, deep.trace, deepRoot)
    deepBuilder.frame(
        "Width is not the only axis. Give the evaluator one more operation of lookahead and its ranking " +
            "changes completely: the best-ranked solvable state is now its ${deepRank}th and $deepTopFive of " +
            "its top five can reach 24. Width $deepWidth is then enough — a single chain solves the puzzle, " +
            "because the evaluator no longer throws the answer away.",
        marked = deepNodes.toSet(),
        path = setOf(deepRoot),
    )
    deepBuilder.frame(
        "But price the two. The cheap evaluator at width $needed spends ${winner.evaluatorCalls} evaluator " +
            "calls; the deep evaluator at width $deepWidth spends ${deep.evaluatorCalls}. The better evaluator " +
            "is ${"%.1f".format(deep.evaluatorCalls.toDouble() / winner.evaluatorCalls)}× *more* expensive " +
            "here, not less. Width and evaluator quality are substitutes, and which one is cheaper is a " +
            "measurement, not a principle — with an LLM as the evaluator, each of those calls is a request.",
        marked = deepNodes.toSet(),
    )
    deepBuilder.frame(
        "The rest of Tree of Thoughts is bookkeeping on top of this: a frontier instead of a single state, an " +
            "evaluator that scores partial states, and pruning that is allowed to be wrong because the beam " +
            "keeps alternatives. Exhaustive search over this puzzle visits $expanded states and finds " +
            "$solutions solution paths — the beam found one of them after " +
            "${winner.expanded} expansions, which is ${"%.1f".format(expanded.toDouble() / winner.expanded)}× " +
            "less of the tree.",
        marked = deepNodes.toSet(),
    )
    frames += deepBuilder.frames
    return frames
}

// ── Interview-prep pattern trees ─────────────────────────────────────────────

// One value tree, built once, so the two pattern topics differ only in how they walk it.
private class PatternTree(val b: TreeBuilder) {
    val root = b.add("5", null, 0)
    val n4 = b.add("4", root, 0)
    val n8 = b.add("8", root, 1)
    val n11 = b.add("11", n4, 0)
    val n13 = b.add("13", n8, 0)
    val n4b = b.add("4", n8, 1)
    val n7 = b.add("7", n11, 0)
    val n2 = b.add("2", n11, 1)
    val n1 = b.add("1", n4b, 0)

    val children = mapOf(
        root to listOf(n4, n8),
        n4 to listOf(n11),
        n8 to listOf(n13, n4b),
        n11 to listOf(n7, n2),
        n4b to listOf(n1),
    )

    fun valueOf(id: Int) = b.labelOf(id).toInt()
    fun childrenOf(id: Int) = children[id].orEmpty()
    fun isLeaf(id: Int) = childrenOf(id).isEmpty()
}

// Level order, with the queue size frozen at the top of each level — the one detail that separates a
// level-grouped answer from a flat traversal.
private fun treeBfsPatternFrames(): List<TreeFrame> {
    val b = TreeBuilder()
    val tree = PatternTree(b)
    b.frame("Level order needs the nodes grouped by depth, not just visited in the right sequence. The queue holds one contiguous frontier.")

    val queue = ArrayDeque(listOf(tree.root))
    val done = mutableSetOf<Int>()
    var depth = 0
    while (queue.isNotEmpty()) {
        val size = queue.size                     // frozen: exactly this level
        val level = mutableListOf<Int>()
        b.frame(
            "Level $depth starts. Freeze size = $size before touching the queue — those $size node(s) are this level, " +
                "and everything pushed from here belongs to the next one.",
            active = queue.toSet(),
            marked = done.toSet(),
        )
        repeat(size) {
            val id = queue.removeFirst()
            level += id
            done += id
            tree.childrenOf(id).forEach { queue.addLast(it) }
            b.frame(
                "Pop ${b.labelOf(id)} and enqueue its ${tree.childrenOf(id).size} child(ren). The queue now mixes " +
                    "level $depth leftovers with level ${depth + 1} — which is why the size was frozen.",
                active = setOf(id),
                path = queue.toSet(),
                marked = done - id,
            )
        }
        b.frame(
            "Level $depth = [${level.joinToString(", ") { b.labelOf(it) }}].",
            marked = done.toSet(),
        )
        depth++
    }

    b.frame(
        "$depth levels, ${done.size} nodes, each enqueued and dequeued exactly once — O(n) time, and peak memory is " +
            "the widest level rather than the height.",
        marked = done.toSet(),
    )
    return b.frames
}

// Root-to-leaf path sum: state pushed down as an argument, un-chosen on the way out.
private fun treeDfsPatternFrames(): List<TreeFrame> {
    val b = TreeBuilder()
    val tree = PatternTree(b)
    val target = 22
    val path = mutableListOf<Int>()
    val found = mutableSetOf<Int>()

    b.frame("Find every root-to-leaf path summing to $target. The running total travels down as an argument; nothing has to be returned up.")

    fun walk(id: Int, remaining: Int) {
        path.add(id)
        val left = remaining - tree.valueOf(id)
        if (tree.isLeaf(id)) {
            val hit = left == 0
            if (hit) found.addAll(path)
            b.frame(
                "Leaf ${b.labelOf(id)}: budget after subtracting is $left. " +
                    if (hit) "Exactly spent — the path ${path.joinToString(" → ") { b.labelOf(it) }} sums to $target."
                    else "Not zero, so this path misses. Pop back up.",
                active = setOf(id),
                path = path.toSet() - id,
                marked = found.toSet(),
            )
        } else {
            b.frame(
                "At ${b.labelOf(id)}: $remaining − ${tree.valueOf(id)} = $left left for the subtree below.",
                active = setOf(id),
                path = path.toSet() - id,
                marked = found.toSet(),
            )
            tree.childrenOf(id).forEach { walk(it, left) }
        }
        path.removeAt(path.lastIndex)             // un-choose, so the sibling starts clean
    }

    walk(tree.root, target)

    b.frame(
        "Every node was entered once — O(n) — and the only extra memory was the call stack plus the current path, " +
            "so O(h). A skewed tree, not a wide one, is this pattern's expensive case.",
        marked = found.toSet(),
    )
    return b.frames
}

// In-order with an explicit stack: the left spine is pushed, the top is always the next key.
private fun bstInorderPatternFrames(): List<TreeFrame> {
    val b = TreeBuilder()
    val n8 = b.add("8", null, 0)
    val n4 = b.add("4", n8, 0)
    val n12 = b.add("12", n8, 1)
    val n2 = b.add("2", n4, 0)
    val n6 = b.add("6", n4, 1)
    val n10 = b.add("10", n12, 0)
    val n14 = b.add("14", n12, 1)
    val n1 = b.add("1", n2, 0)
    val n3 = b.add("3", n2, 1)
    val left = mapOf(n8 to n4, n4 to n2, n12 to n10, n2 to n1)
    val right = mapOf(n8 to n12, n4 to n6, n12 to n14, n2 to n3)

    val stack = ArrayDeque<Int>()
    val emitted = mutableListOf<Int>()
    val k = 4

    b.frame(
        "In-order on a BST emits its keys in sorted order — that one fact answers k-th smallest, validation, " +
            "successor and range counting. Done iteratively, it can also stop early.",
    )

    var cur: Int? = n8
    while (cur != null || stack.isNotEmpty()) {
        val spine = mutableListOf<Int>()
        while (cur != null) {
            stack.addLast(cur)
            spine += cur
            cur = left[cur]
        }
        if (spine.isNotEmpty()) {
            b.frame(
                "Push the left spine ${spine.joinToString(" → ") { b.labelOf(it) }}. Nothing smaller than the stack " +
                    "top can still be unvisited, so the top is always the next key in order.",
                active = spine.toSet(),
                path = stack.toSet() - spine.toSet(),
                marked = emitted.toSet(),
            )
        }
        val node = stack.removeLast()
        emitted += node
        b.frame(
            "Pop ${b.labelOf(node)} — key ${emitted.size} of the sorted order. " +
                (if (right[node] != null) "Now move right to ${b.labelOf(right.getValue(node))} and push its left spine."
                else "No right child, so the next key is already waiting on the stack.") +
                if (emitted.size == k) " A k-th-smallest query with k = $k stops right here: O(height + k), and the " +
                    "right half of the tree is never touched."
                else "",
            active = setOf(node),
            path = stack.toSet(),
            marked = emitted.toSet() - node,
        )
        cur = right[node]
    }

    b.frame(
        "${emitted.joinToString(", ") { b.labelOf(it) }} — sorted, from a structure that was never sorted. Every " +
            "node was pushed and popped exactly once: O(n) time, O(height) space, versus O(n) for a recursive " +
            "traversal that materialises the whole list.",
        marked = emitted.toSet(),
    )
    return b.frames
}

// Diameter: what a node returns to its parent is not what the node itself records.
private fun treeDpPatternFrames(): List<TreeFrame> {
    val b = TreeBuilder()
    val tree = PatternTree(b)
    val down = mutableMapOf<Int, Int>()
    var best = 0
    var bestAt = tree.root

    b.frame(
        "Diameter of this tree, post-order. Each node returns its longest *downward* path; the best path *through* " +
            "the node combines two children and is recorded, never returned. Mixing those up is the classic bug.",
    )

    fun walk(id: Int) {
        tree.childrenOf(id).forEach { walk(it) }
        val childDowns = tree.childrenOf(id).map { down.getValue(it) }.sortedDescending()
        val through = 1 + childDowns.take(2).sum()
        val returned = 1 + (childDowns.firstOrNull() ?: 0)
        down[id] = returned
        if (through > best) {
            best = through
            bestAt = id
        }
        b.relabel(id, "${b.labelOf(id).substringBefore('·')}·↓$returned")
        b.frame(
            if (tree.isLeaf(id)) {
                "Leaf ${b.labelOf(id)}: nothing below, so it returns a downward path of 1 and combines to 1."
            } else {
                "${b.labelOf(id)} sees child paths ${childDowns.joinToString(" and ")}. Through it: " +
                    "1 + ${childDowns.take(2).joinToString(" + ")} = $through nodes — recorded, because a parent " +
                    "cannot use a path that bends here. Returned upward: 1 + ${childDowns.first()} = $returned."
            },
            active = setOf(id),
            path = tree.childrenOf(id).toSet(),
            marked = if (through == best) setOf(id) else emptySet(),
        )
    }

    walk(tree.root)

    fun deepest(id: Int): List<Int> {
        val child = tree.childrenOf(id).maxByOrNull { down.getValue(it) } ?: return listOf(id)
        return listOf(id) + deepest(child)
    }

    val branches = tree.childrenOf(bestAt).sortedByDescending { down.getValue(it) }.take(2)
    val diameterPath = (branches.flatMap { deepest(it) } + bestAt).toSet()

    b.frame(
        "Diameter = $best nodes, bending at ${b.labelOf(bestAt)}. One post-order pass, O(n) time and O(height) " +
            "stack — and the answer never travelled upward, which is why `best` lives outside the recursion.",
        marked = diameterPath,
        active = setOf(bestAt),
    )
    return b.frames
}

// Prefix tree: insert, then the search / startsWith distinction that is the whole point.
private fun triePrefixPatternFrames(): List<TreeFrame> {
    val b = TreeBuilder()
    val root = b.add("·", null, 0)
    val children = mutableMapOf<Pair<Int, Char>, Int>()
    val terminal = mutableSetOf<Int>()
    val prefixCount = mutableMapOf<Int, Int>()

    fun childOf(node: Int, c: Char) = children[node to c]

    fun insert(word: String) {
        var node = root
        val path = mutableListOf(root)
        var created = 0
        word.forEach { c ->
            val existing = childOf(node, c)
            // Siblings are laid out by `order`, so it counts children of this parent — not the depth.
            val slot = children.keys.count { it.first == node }
            node = existing ?: b.add(c.toString(), node, slot).also {
                children[node to c] = it
                created++
            }
            prefixCount[node] = (prefixCount[node] ?: 0) + 1
            path += node
        }
        terminal += node
        b.relabel(node, "${b.labelOf(node)}●")
        b.frame(
            "Insert \"$word\": " + (if (created == 0) "every character already had a node — only the word-end mark is new."
            else "$created new node(s), the rest shared with words already stored.") +
                " The ● marks a word end; without it the trie cannot tell a stored word from a passing prefix.",
            active = setOf(node),
            path = path.toSet() - node,
            marked = terminal - node,
        )
    }

    b.frame("A trie stores one node per character and one path per word, so words sharing a prefix share nodes.")
    listOf("app", "apple", "apt", "bat").forEach { insert(it) }

    fun walk(query: String): List<Int>? {
        var node = root
        val path = mutableListOf(root)
        query.forEach { c ->
            node = childOf(node, c) ?: return null
            path += node
        }
        return path
    }

    val appPath = walk("app")!!
    b.frame(
        "search(\"app\") walks a-p-p and finds ● on the last node — a stored word. Cost is O(3), the length of the " +
            "query, no matter how many words the trie holds.",
        active = setOf(appPath.last()),
        path = appPath.toSet() - appPath.last(),
        marked = terminal,
    )

    val applPath = walk("appl")!!
    b.frame(
        "search(\"appl\") walks the same way and the path exists — but the last node has no ●, so \"appl\" is not a " +
            "stored word. startsWith(\"appl\") is true on that same walk. Same traversal, different acceptance test.",
        active = setOf(applPath.last()),
        path = applPath.toSet() - applPath.last(),
        marked = terminal,
    )

    val apPath = walk("ap")!!
    b.frame(
        "A counter per node turns the trie into an index: the \"ap\" node was crossed by " +
            "${prefixCount.getValue(apPath.last())} insertions, so \"ap\" has that many completions — answered " +
            "without visiting any of them. Autocomplete then DFSes only what hangs below this node.",
        active = setOf(apPath.last()),
        path = apPath.toSet() - apPath.last(),
        marked = terminal,
    )
    return b.frames
}

// Binary lifting: the jump table built level by level, then spent on a k-th ancestor and an LCA.
private fun binaryLiftingPatternFrames(): List<TreeFrame> {
    val b = TreeBuilder()
    val a = b.add("A", null, 0)
    val bb = b.add("B", a, 0)
    val f = b.add("F", a, 1)
    val c = b.add("C", bb, 0)
    val g = b.add("G", f, 0)
    val d = b.add("D", c, 0)
    val e = b.add("E", d, 0)

    val parent = mapOf(bb to a, f to a, c to bb, g to f, d to c, e to d)
    val depth = mapOf(a to 0, bb to 1, f to 1, c to 2, g to 2, d to 3, e to 4)
    val levels = 3
    // up[j][v] — the 2^j-th ancestor, or null when the jump runs off the root.
    val up = Array(levels) { mutableMapOf<Int, Int?>() }
    parent.forEach { (child, p) -> up[0][child] = p }
    for (j in 1 until levels) {
        (parent.keys + a).forEach { v -> up[j][v] = up[j - 1][v]?.let { up[j - 1][it] } }
    }

    fun name(id: Int?) = id?.let { b.labelOf(it) } ?: "root sentinel"

    b.frame(
        "A rooted tree with depths 0 to ${depth.getValue(e)}. Answering \"the k-th ancestor of E\" by walking up k " +
            "times is O(k) per query; binary lifting pays O(n log n) once and answers in O(log n) forever after.",
        marked = setOf(a),
        path = setOf(e, d, c, bb),
    )
    b.frame(
        "up[0][v] is just the parent — the table's base row, free to fill.",
        active = setOf(e),
        path = setOf(d),
    )
    b.frame(
        "up[1][v] = up[0][up[0][v]]: a 2-step is two 1-steps, so the second row is built from the first. " +
            "up[1][E] = ${name(up[1][e])}.",
        active = setOf(e),
        path = setOf(c),
    )
    b.frame(
        "up[2][v] = up[1][up[1][v]] — a 4-step is two 2-steps. up[2][E] = ${name(up[2][e])}. Each row costs one pass " +
            "over the nodes, and there are log n rows.",
        active = setOf(e),
        path = setOf(a),
    )

    val k = 3
    val afterTwo = up[1][e]
    val afterOne = afterTwo?.let { up[0][it] }
    b.frame(
        "Query: the ${k}rd ancestor of E. $k in binary is 11, so take the 2-jump and then the 1-jump — never $k " +
            "single steps. First: E → ${name(afterTwo)}.",
        active = setOf(e),
        path = setOfNotNull(afterTwo),
    )
    b.frame(
        "Then the 1-jump: ${name(afterTwo)} → ${name(afterOne)}. Two jumps instead of three steps here; for k in the " +
            "millions it is still at most log k jumps.",
        active = setOfNotNull(afterOne),
        path = setOfNotNull(afterTwo, e),
        marked = setOfNotNull(afterOne),
    )

    b.frame(
        "LCA(E, G) reuses the same table. E is at depth ${depth.getValue(e)}, G at ${depth.getValue(g)} — lift E by " +
            "the difference (${depth.getValue(e) - depth.getValue(g)}, one 2-jump) to ${name(up[1][e])} so both sit " +
            "at the same depth.",
        active = setOf(c, g),
        path = setOf(e, d),
    )
    b.frame(
        "Now jump both by the largest power whose ancestors still *differ*: up[0][C] = ${name(up[0][c])} and " +
            "up[0][G] = ${name(up[0][g])} differ, so both move. Stopping while they differ is what keeps the answer " +
            "one step above.",
        active = setOf(bb, f),
        path = setOf(c, g, e, d),
    )
    b.frame(
        "up[0][B] = up[0][F] = A: the ancestors finally agree, so A is the LCA. Distance = depth[E] + depth[G] − " +
            "2·depth[A] = ${depth.getValue(e)} + ${depth.getValue(g)} − 0 = ${depth.getValue(e) + depth.getValue(g)} " +
            "edges — the whole path query answered from depths alone.",
        marked = setOf(a),
        path = setOf(e, d, c, bb, g, f),
    )
    return b.frames
}

private val treeConfigs = mapOf(
    "bst_inorder_pattern" to TreeConfig(
        intro = "Iterative in-order over a nine-key BST. The stack holds a left spine, never the whole tree, and the " +
            "fourth pop is where a k-th-smallest query would stop.",
        markedLabel = "Emitted in order",
        build = ::bstInorderPatternFrames,
    ),
    "tree_dp_pattern" to TreeConfig(
        intro = "Diameter computed post-order, with each node relabelled by the downward path it returns. The number " +
            "it returns and the number it records are deliberately different.",
        markedLabel = "Diameter path",
        build = ::treeDpPatternFrames,
    ),
    "trie_prefix_pattern" to TreeConfig(
        intro = "Four words inserted into one prefix tree, then search versus startsWith on the same walk — the ● " +
            "flag is the only thing separating them.",
        markedLabel = "Word end (●)",
        build = ::triePrefixPatternFrames,
    ),
    "binary_lifting_pattern" to TreeConfig(
        intro = "The jump table built row by row, then spent twice: a 3rd-ancestor query decomposed into 2 + 1, and " +
            "an LCA that lifts both nodes without ever overshooting.",
        markedLabel = "Answer",
        build = ::binaryLiftingPatternFrames,
    ),
    "tree_bfs_pattern" to TreeConfig(
        intro = "Level-order traversal with the queue size frozen per level. Watch the queue hold two levels at once " +
            "mid-sweep — that is exactly what the frozen size protects against.",
        markedLabel = "Visited",
        build = ::treeBfsPatternFrames,
    ),
    "tree_dfs_pattern" to TreeConfig(
        intro = "Root-to-leaf paths summing to 22. The budget is carried down as an argument and the path is popped " +
            "on the way out, so siblings never inherit each other's state.",
        markedLabel = "On a matching path",
        build = ::treeDfsPatternFrames,
    ),
    "tree_of_thoughts" to TreeConfig(
        intro = "Game of 24 searched for real: the greedy chain that fails, the evaluator's ranking measured " +
            "against what can actually reach 24, and the beam width that fixes it — priced against a better " +
            "evaluator.",
        markedLabel = "Reaches 24",
        build = ::treeOfThoughtsFrames,
    ),
    "dependency_parsing" to TreeConfig(
        intro = "One sentence parsed by arc-standard transitions, stack and buffer replayed step by step — then " +
            "UAS against LAS on a wrong parse, and the crossing arc this transition system cannot build.",
        markedLabel = "Attached",
        build = ::dependencyFrames,
    ),
    "constituency_parsing" to TreeConfig(
        intro = "The same sentence as nested phrases: the spans evalb scores, a flattened VP costing recall, and " +
            "head rules converting the tree into the dependency parse next door.",
        markedLabel = "Constituent",
        build = ::constituencyFrames,
    ),
    "pcfg" to TreeConfig(
        intro = "\"she saw the man with the telescope\" parsed by probabilistic CYK: the chart bottom-up, then " +
            "both attachments of the prepositional phrase scored against each other.",
        markedLabel = "Best parse",
        build = ::pcfgFrames,
    ),
    "fp_growth" to TreeConfig(
        intro = "Ten baskets compressed into a prefix tree in two passes, then mined by walking the header chain for one item back up to the root.",
        markedLabel = "Header chain",
        linkLabel = "Header link",
        build = ::fpGrowthFrames,
    ),
    "aho_corasick" to TreeConfig(
        intro = "The dictionary {he, she, his, hers} as a trie, then its failure links, then one walk over \"ushers\". " +
            "The dashed arcs are the failure links — they are the whole algorithm, and they are why the text pointer never backs up.",
        markedLabel = "Pattern ends here",
        build = ::ahoCorasickFrames,
        linkLabel = "Failure link",
    ),
    "hierarchical_divisive" to TreeConfig(
        intro = "The dendrogram built top-down: repeatedly split the least cohesive cluster, with the real diameters driving which one goes next.",
        markedLabel = "Cluster",
        build = ::divisiveFrames,
    ),
    "xgboost" to TreeConfig(
        intro = "One tree built by the real second-order gain formula: G and H sums, the closed-form leaf value, and a split that γ prunes away.",
        markedLabel = "Leaf",
        build = ::xgboostFrames,
    ),
    "lightgbm" to TreeConfig(
        intro = "The same six-leaf budget spent level-wise and then leaf-wise, so the difference in shape and in captured gain is directly comparable.",
        markedLabel = "Leaf",
        build = ::lightgbmFrames,
    ),
    "catboost" to TreeConfig(
        intro = "Oblivious trees: one condition per level, reused across the whole level, and what that buys at inference time.",
        markedLabel = "Leaf",
        build = ::catboostFrames,
    ),
    "lca" to TreeConfig(
        intro = "Two LCA queries on an 8-node tree: one where the nodes sit at different depths and must be " +
            "levelled first, one where they are already level and only need to climb together.",
        markedLabel = "LCA",
        build = ::lcaFrames,
    ),
    "tree_dp" to TreeConfig(
        intro = "Maximum-weight independent set. Labels turn into \"not-taken / taken\" as the post-order pass " +
            "resolves each subtree — children are always finished before their parent is touched.",
        markedLabel = "Chosen set",
        build = ::treeDpFrames,
    ),
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
    "priority_queue_adt" to TreeConfig(
        intro = "The contract is insert, peek and remove-highest-priority. A binary heap keeps just enough order " +
            "to serve it — watch how little of the structure each operation has to touch.",
        markedLabel = "Root / minimum",
        build = ::priorityQueueFrames,
    ),
    "huffman_coding" to TreeConfig(
        intro = "Merge the two rarest symbols, repeat, and the tree that falls out assigns short codes to common " +
            "symbols. The bit totals at the end are counted from the tree it actually built.",
        markedLabel = "Merged",
        build = ::huffmanFrames,
    ),
    "disjoint_set" to TreeConfig(
        intro = "A forest where the only thing a tree means is \"these elements are in one set\". Union by rank " +
            "keeps it shallow, path compression flattens what it walks, and the final frame counts both against " +
            "the naive version.",
        markedLabel = "Root / settled",
        build = ::disjointSetFrames,
    ),
    "suffix_tree" to TreeConfig(
        intro = "Every suffix of the text inserted into a trie, then the same information with one-child chains " +
            "collapsed onto single edges — the step that makes the node count linear in the text length.",
        markedLabel = "Leaf / suffix start",
        build = ::suffixTreeFrames,
    ),
)

private fun treeConfigFor(topicId: String): TreeConfig =
    treeConfigs[topicId] ?: treeConfigs.getValue("binary_search_tree")

internal val treeVisualizerTopicIds: Set<String> get() = treeConfigs.keys

internal fun treeVisualizerFrameCount(topicId: String): Int {
    val frames = treeConfigFor(topicId).build()
    frames.forEach { frame ->
        val ids = frame.nodes.map { it.id }.toSet()
        val orphans = frame.nodes.mapNotNull { it.parent }.filter { it !in ids }
        require(orphans.isEmpty()) { "$topicId has nodes whose parent is not in the same frame: $orphans" }
        val danglingLinks = frame.links.flatMap { listOf(it.from, it.to) }.filter { it !in ids }
        require(danglingLinks.isEmpty()) { "$topicId draws links to nodes not in the frame: $danglingLinks" }
    }
    return frames.size
}

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
                config.linkLabel?.let { TreeLegend(LinkColor, it) }
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
    // Every parentless node is a root: a disjoint-set forest starts as eight of them and merges down
    // to one, so the layout cannot assume a single tree.
    frame.nodes.filter { it.parent == null }.sortedBy { it.order }.forEach { walk(it.id, 0) }

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
            // All of these are dp, not raw canvas pixels: as constants in pixels the whole layout
            // shrank by the screen density, so on a 3x device a 56px row gap was a ~19dp step and
            // the tree huddled in the top third of a 220dp canvas.
            val padX = 12.dp.toPx()
            val padY = 10.dp.toPx()
            fun px(x: Float) = padX + (x + 0.5f) / leafCount * (size.width - 2 * padX)
            // Fixed row spacing (capped so deep trees still fit) rather than stretching to the
            // canvas height — otherwise a 2-level frame and a 4-level frame of the same tree would
            // render at wildly different scales as nodes get inserted.
            val rowGap = if (maxDepth == 0) 0f else minOf(64.dp.toPx(), (size.height - 2 * padY) / maxDepth)
            fun py(depth: Int) = if (maxDepth == 0) size.height / 2f else padY + depth * rowGap

            frame.nodes.forEach { node ->
                val parent = node.parent?.let { byId[it] } ?: return@forEach
                drawLine(
                    Color(0xFFCBD0DA),
                    Offset(px(xById.getValue(parent.id)), py(depthById.getValue(parent.id))),
                    Offset(px(xById.getValue(node.id)), py(depthById.getValue(node.id))),
                    // In dp, not raw pixels: a fixed 3.5px edge is a ~1dp hairline on a 3x screen.
                    strokeWidth = 3.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }

            // Failure links, under the node pills so an arrow never covers a label. Drawn as a
            // dashed arc rather than a straight line, because a link back to the root would
            // otherwise lie exactly on top of the parent edges it crosses.
            frame.links.forEach { link ->
                val from = byId[link.from] ?: return@forEach
                val to = byId[link.to] ?: return@forEach
                val start = Offset(px(xById.getValue(from.id)), py(depthById.getValue(from.id)))
                val end = Offset(px(xById.getValue(to.id)), py(depthById.getValue(to.id)))
                val control = Offset((start.x + end.x) / 2f, minOf(start.y, end.y) - 14.dp.toPx())
                drawPath(
                    path = Path().apply {
                        moveTo(start.x, start.y)
                        quadraticTo(control.x, control.y, end.x, end.y)
                    },
                    color = LinkColor,
                    style = Stroke(
                        width = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(
                            floatArrayOf(3.dp.toPx(), 2.5.dp.toPx()),
                        ),
                    ),
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
                val boxWidth = (layout.size.width + 12.dp.toPx()).coerceAtLeast(26.dp.toPx())
                val boxHeight = layout.size.height + 8.dp.toPx()
                drawRoundRect(
                    color = color,
                    topLeft = Offset(center.x - boxWidth / 2f, center.y - boxHeight / 2f),
                    size = Size(boxWidth, boxHeight),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(6.dp.toPx(), 6.dp.toPx()),
                )
                drawText(
                    layout,
                    topLeft = Offset(center.x - layout.size.width / 2f, center.y - layout.size.height / 2f),
                )
            }
        }
    }
}
