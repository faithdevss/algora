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

private val treeConfigs = mapOf(
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
