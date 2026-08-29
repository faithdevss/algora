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
import androidx.compose.ui.geometry.CornerRadius
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
import kotlin.random.Random

// ── Linked-structure player ──────────────────────────────────────────────────
// Nodes in a row with pointer lanes drawn around them: forward links above, optional backward links
// below (doubly linked list), and optional express lanes stacked on top (skip list). Precomputed
// frames, same model as the other players — what each lab claims about cost is counted while the
// frames are built, not asserted in prose.

private enum class LinkMark { IDLE, PATH, ACTIVE, RESULT, GHOST }

private class LinkNode(val label: String, val mark: LinkMark = LinkMark.IDLE)

// `presence[level][i]` — is node i on that express lane? Level 0 is the base row and is always full.
private class LinkFrame(
    val nodes: List<LinkNode>,
    val status: String,
    val backward: Boolean = false,
    val presence: List<List<Boolean>> = emptyList(),
    val levelFocus: Int? = null,
    val readout: String? = null,
)

private class LinkConfig(
    val intro: String,
    val legend: List<Pair<Color, String>>,
    val build: () -> List<LinkFrame>,
)

private val LinkPath = SimColors.Blue
private val LinkActive = SimColors.Active
private val LinkResult = Color(0xFF7C3AED)
private val LinkIdle = Color(0xFF64748B)
private val LinkGhost = SimColors.Idle

// ── Doubly linked list ───────────────────────────────────────────────────────

private fun doublyLinkedFrames(): List<LinkFrame> {
    val values = listOf(10, 20, 30, 40, 50)
    val frames = mutableListOf<LinkFrame>()

    fun row(list: List<Int>, marks: Map<Int, LinkMark> = emptyMap()) =
        list.mapIndexed { i, v -> LinkNode(v.toString(), marks[i] ?: LinkMark.IDLE) }

    frames += LinkFrame(
        nodes = row(values),
        status = "Every node carries two pointers instead of one: next above, prev below. That second pointer is " +
            "the entire structural difference, and everything cheap or expensive below follows from it.",
        backward = true,
    )

    frames += LinkFrame(
        nodes = row(values, (values.indices).associateWith { LinkMark.PATH }),
        status = "Traversal works from either end. A singly linked list can only walk forward — printing it " +
            "backwards means either a stack of ${values.size} pointers or reversing the list first. Here it is " +
            "the same loop with prev instead of next.",
        backward = true,
        readout = "forward and backward: ${values.size} hops each",
    )

    val target = 3
    frames += LinkFrame(
        nodes = row(values, mapOf(target to LinkMark.ACTIVE)),
        status = "Say you already hold a pointer to ${values[target]} — a cache entry, an LRU node, a handle " +
            "returned by an earlier insert. Deleting it needs its predecessor.",
        backward = true,
    )
    frames += LinkFrame(
        nodes = row(values, mapOf(target - 1 to LinkMark.PATH, target to LinkMark.ACTIVE, target + 1 to LinkMark.PATH)),
        status = "The doubly linked list reads node.prev and node.next directly: two pointer writes " +
            "(prev.next = next, next.prev = prev) and the node is gone. No search, no matter how long the list is.",
        backward = true,
        readout = "delete given a node: 2 writes, 0 hops",
    )
    frames += LinkFrame(
        nodes = row(values, (0 until target).associateWith { LinkMark.PATH } + (target to LinkMark.ACTIVE)),
        status = "A singly linked list has no way back, so it walks from the head to find the predecessor — " +
            "$target hops here, and O(n) in general. That difference is why LRU caches, browser history and " +
            "text-editor gap buffers are all doubly linked.",
        readout = "singly: $target hops then 1 write",
    )

    // The same script, counted on both implementations.
    val deletions = listOf(4, 1, 3, 0, 2)
    var doublyWrites = 0
    var doublyHops = 0
    var singlyWrites = 0
    var singlyHops = 0
    val alive = values.indices.toMutableList()
    deletions.forEach { original ->
        val position = alive.indexOf(original)
        if (position < 0) return@forEach
        doublyWrites += if (position == 0 || position == alive.lastIndex) 1 else 2
        singlyHops += position
        singlyWrites += 1
        alive.removeAt(position)
    }
    frames += LinkFrame(
        nodes = row(values, values.indices.associateWith { LinkMark.GHOST }),
        status = "Deleting all five by handle, in the order ${deletions.joinToString(", ") { values[it].toString() }}: " +
            "the doubly linked version does $doublyWrites pointer writes and $doublyHops hops; the singly " +
            "linked one does $singlyWrites writes but $singlyHops hops to find predecessors. The prev pointer " +
            "buys those hops with memory: a node of {value, next} becomes {value, prev, next}, three words " +
            "instead of two — 50% larger, ${values.size} extra words across this list.",
        readout = "doubly $doublyWrites writes / $doublyHops hops · singly $singlyWrites writes / $singlyHops hops",
    )
    frames += LinkFrame(
        nodes = row(values, values.indices.associateWith { LinkMark.RESULT }),
        status = "So the rule is not \"doubly linked is better\". It is: pay one pointer per node, and any node " +
            "you already hold becomes deletable and traversable in both directions in constant time. If you " +
            "never hold nodes — you always start from the head — the second pointer is pure overhead.",
        backward = true,
    )
    return frames
}

// ── Skip list ────────────────────────────────────────────────────────────────

private fun skipListFrames(): List<LinkFrame> {
    val keys = listOf(3, 7, 12, 19, 24, 31, 38, 45, 52, 61)
    // Fixed level assignment so the picture is stable; the geometric distribution it imitates is
    // measured separately at the end.
    val levelOf = listOf(1, 2, 1, 3, 1, 2, 1, 4, 1, 2)
    val maxLevel = levelOf.max()
    val frames = mutableListOf<LinkFrame>()

    fun presence() = (0 until maxLevel).map { level -> keys.indices.map { levelOf[it] > level } }
    fun row(marks: Map<Int, LinkMark> = emptyMap()) =
        keys.mapIndexed { i, k -> LinkNode(k.toString(), marks[i] ?: LinkMark.IDLE) }

    frames += LinkFrame(
        nodes = row(),
        status = "A sorted linked list of ${keys.size} keys, plus express lanes. A node on level L appears in " +
            "every lane below L, so the bottom lane is the full list and each lane above it is a shortcut over " +
            "the one beneath.",
        presence = presence(),
        readout = "levels: ${(1..maxLevel).joinToString(", ") { l -> "L$l holds ${levelOf.count { it >= l }}" }}",
    )

    val target = 38
    var comparisons = 0
    val visited = mutableMapOf<Int, LinkMark>()
    var position = -1
    var found = false
    for (level in maxLevel - 1 downTo 0) {
        var stepped = 0
        while (!found) {
            val next = (position + 1 until keys.size).firstOrNull { levelOf[it] > level }
            if (next == null) {
                frames += LinkFrame(
                    nodes = row(visited + (position.takeIf { it >= 0 }?.let { mapOf(it to LinkMark.ACTIVE) }.orEmpty())),
                    status = "Level ${level + 1}: this lane has nothing left after " +
                        (if (position >= 0) keys[position].toString() else "the head") +
                        ", so there is nothing to compare — drop a level. Reaching the end of a lane costs " +
                        "no comparison at all.",
                    presence = presence(),
                    levelFocus = level,
                    readout = "$comparisons comparisons so far",
                )
                break
            }
            comparisons++
            if (keys[next] < target) {
                position = next
                visited[next] = LinkMark.PATH
                stepped++
                frames += LinkFrame(
                    nodes = row(visited + (next to LinkMark.ACTIVE)),
                    status = "Level ${level + 1}: ${keys[next]} is still below $target, so step onto it. " +
                        "Everything before it is now ruled out — a whole prefix skipped for one comparison.",
                    presence = presence(),
                    levelFocus = level,
                    readout = "$comparisons comparisons so far",
                )
            } else if (keys[next] == target) {
                position = next
                visited[next] = LinkMark.PATH
                found = true
            } else {
                frames += LinkFrame(
                    nodes = row(visited + (next to LinkMark.ACTIVE)),
                    status = "Level ${level + 1}: the next node on this lane is ${keys[next]}, which overshoots " +
                        "$target. Drop down a level rather than step — the overshoot is information, not a " +
                        "wasted comparison: the answer is between " +
                        (if (position >= 0) keys[position].toString() else "the head") + " and ${keys[next]}.",
                    presence = presence(),
                    levelFocus = level,
                    readout = "$comparisons comparisons so far",
                )
                break
            }
        }
        if (found) break
    }
    frames += LinkFrame(
        nodes = row(visited + (keys.indexOf(target) to LinkMark.RESULT)),
        status = "Found $target after $comparisons comparisons against a plain linked list's " +
            "${keys.indexOf(target) + 1}. At ${keys.size} keys that is barely a win, and saying otherwise " +
            "would be dishonest — the structure is asymptotic, and ten items have no asymptotics. The frame " +
            "after next measures what happens when n grows.",
        presence = presence(),
        readout = "skip list $comparisons vs linked list ${keys.indexOf(target) + 1}",
    )

    // Averaged over every key, so the win is a measurement rather than one lucky search.
    var skipTotal = 0
    var linearTotal = 0
    keys.forEachIndexed { idx, key ->
        var pos = -1
        var cmp = 0
        var hit = false
        for (level in maxLevel - 1 downTo 0) {
            while (!hit) {
                val next = (pos + 1 until keys.size).firstOrNull { levelOf[it] > level } ?: break
                cmp++
                when {
                    keys[next] < key -> pos = next
                    keys[next] == key -> { pos = next; hit = true }
                    else -> break
                }
            }
            if (hit) break
        }
        skipTotal += cmp
        linearTotal += idx + 1
    }
    frames += LinkFrame(
        nodes = row(),
        status = "Searching every one of the ${keys.size} keys: ${"%.1f".format(skipTotal.toDouble() / keys.size)} " +
            "comparisons on average against the linked list's " +
            "${"%.1f".format(linearTotal.toDouble() / keys.size)}. The lanes are doing the work a balanced " +
            "tree would do, with no rotations and no rebalancing code.",
        presence = presence(),
        readout = "avg $skipTotal/${keys.size} vs $linearTotal/${keys.size} comparisons",
    )

    // Ten keys prove nothing about O(log n), so build real skip lists at four sizes and search every
    // key in each. Levels come from actual coin flips, which is what the structure does.
    val scaling = listOf(16, 128, 1024, 8192).map { n ->
        val rng = Random(11)
        val levels = IntArray(n) { var h = 1; while (h < 20 && rng.nextBoolean()) h++; h }
        val top = levels.max()
        var total = 0L
        for (targetIndex in 0 until n) {
            var pos = -1
            for (level in top - 1 downTo 0) {
                var hit = false
                while (true) {
                    var next = pos + 1
                    while (next < n && levels[next] <= level) next++
                    if (next >= n) break
                    total++
                    if (next < targetIndex) pos = next
                    else if (next == targetIndex) { hit = true; break }
                    else break
                }
                if (hit) break
            }
        }
        Triple(n, total.toDouble() / n, (n + 1) / 2.0)
    }
    frames += LinkFrame(
        nodes = row(),
        status = "Built at four sizes with real coin flips, searching every key: " +
            scaling.joinToString("; ") { (n, skip, linear) ->
                "n=$n → ${"%.1f".format(skip)} vs ${"%.1f".format(linear)}"
            } +
            ". The linked list doubles when n doubles; the skip list adds a constant — that is log growth " +
            "measured, not assumed. At n=${scaling.last().first} it is " +
            "${"%.0f".format(scaling.last().third / scaling.last().second)}× fewer comparisons.",
        presence = presence(),
        readout = "avg comparisons: skip list vs sorted linked list",
    )

    // The structure is probabilistic: no input controls the shape, a coin does. Measure the shape.
    val rng = Random(7)
    val trials = 20000
    val heights = IntArray(8)
    var heightSum = 0
    repeat(trials) {
        var h = 1
        while (h < 8 && rng.nextBoolean()) h++
        heights[h - 1]++
        heightSum += h
    }
    val expected = (1..8).joinToString(", ") { l -> "L$l ${"%.3f".format(heights[l - 1].toDouble() / trials)}" }
    frames += LinkFrame(
        nodes = row(keys.indices.associateWith { LinkMark.GHOST }),
        status = "Real skip lists pick each node's height by flipping a coin until it comes up tails. Over " +
            "$trials draws the heights came out $expected — halving each level, exactly the 2^-L the analysis " +
            "assumes, with mean height ${"%.2f".format(heightSum.toDouble() / trials)}. " +
            "That is why the expected search is O(log n): the level count is O(log n) with high probability, " +
            "and each level is expected to take a constant number of steps.",
        presence = presence(),
        readout = "mean height ${"%.2f".format(heightSum.toDouble() / trials)} over $trials samples",
    )
    frames += LinkFrame(
        nodes = row(),
        status = "The trade against a balanced tree is honest: a skip list has no worst-case guarantee — a bad " +
            "run of coin flips gives a bad structure, it just becomes vanishingly unlikely as n grows. What it " +
            "buys is code you can read, and concurrent insertion that needs no rotation locking, which is why " +
            "it shows up in databases like LevelDB and Redis's sorted sets.",
        presence = presence(),
    )
    return frames
}

// ── Config ───────────────────────────────────────────────────────────────────

// ── Deque: both ends are cheap, and the monotonic-deque payoff ───────────────

private fun dequeFrames(): List<LinkFrame> {
    val frames = mutableListOf<LinkFrame>()

    fun row(list: List<String>, marks: Map<Int, LinkMark> = emptyMap()) =
        list.mapIndexed { i, v -> LinkNode(v, marks[i] ?: LinkMark.IDLE) }

    val contents = mutableListOf("20", "30", "40")

    frames += LinkFrame(
        nodes = row(contents),
        status = "A deque exposes four operations instead of a queue's two: push and pop at the front, push and " +
            "pop at the back. Nothing shifts — only the head index and the size move.",
        backward = true,
        readout = "front ${contents.first()} · back ${contents.last()}",
    )

    contents.add(0, "10")
    frames += LinkFrame(
        nodes = row(contents, mapOf(0 to LinkMark.ACTIVE)),
        status = "pushFront(10): head steps backward one slot — in a circular buffer that is " +
            "head = (head − 1 + capacity) mod capacity, wrapping to the array's end rather than shifting " +
            "everything right.",
        backward = true,
        readout = "O(1) · nothing else moved",
    )

    contents.add("50")
    frames += LinkFrame(
        nodes = row(contents, mapOf(contents.lastIndex to LinkMark.ACTIVE)),
        status = "pushBack(50): written at slot (head + size) mod capacity. Both ends are symmetric, which a " +
            "plain array-backed queue is not — pushing at its front is O(n).",
        backward = true,
        readout = "O(1) · size ${contents.size}",
    )

    val poppedFront = contents.removeAt(0)
    frames += LinkFrame(
        nodes = row(contents, mapOf(0 to LinkMark.PATH)),
        status = "popFront() returns $poppedFront and advances head. Used this way the deque is a queue.",
        backward = true,
        readout = "FIFO view",
    )

    val poppedBack = contents.removeAt(contents.lastIndex)
    frames += LinkFrame(
        nodes = row(contents, mapOf(contents.lastIndex to LinkMark.PATH)),
        status = "popBack() returns $poppedBack and just decrements size. Push and pop at the same end and the " +
            "deque is a stack — one structure, both disciplines.",
        backward = true,
        readout = "LIFO view",
    )

    // The reason a deque is worth its own topic: sliding-window maximum in O(n).
    val values = listOf(1, 3, -1, -3, 5, 3, 6, 7)
    val k = 3
    val window = ArrayDeque<Int>()
    val answers = mutableListOf<Int>()

    frames += LinkFrame(
        nodes = values.map { LinkNode(it.toString(), LinkMark.GHOST) },
        status = "The payoff: sliding-window maximum over these 8 values with k = $k. The deque will hold " +
            "indices whose values strictly decrease, so its front is always the current window's maximum.",
        readout = "brute force would rescan k values per window",
    )

    for (i in values.indices) {
        if (window.isNotEmpty() && window.first() <= i - k) {
            val expired = window.removeFirst()
            frames += LinkFrame(
                nodes = values.mapIndexed { p, v ->
                    LinkNode(v.toString(), if (p == expired) LinkMark.PATH else if (p in window) LinkMark.ACTIVE else LinkMark.GHOST)
                },
                status = "Index $expired has fallen out of the window, so it leaves the front. That is one " +
                    "removal, not a rescan.",
                readout = "deque: ${window.map { values[it] }}",
            )
        }

        val dropped = mutableListOf<Int>()
        while (window.isNotEmpty() && values[window.last()] <= values[i]) dropped += window.removeLast()
        if (dropped.isNotEmpty()) {
            frames += LinkFrame(
                nodes = values.mapIndexed { p, v ->
                    LinkNode(
                        v.toString(),
                        when {
                            p == i -> LinkMark.RESULT
                            p in dropped -> LinkMark.PATH
                            p in window -> LinkMark.ACTIVE
                            else -> LinkMark.GHOST
                        },
                    )
                },
                status = "${values[i]} arrives and dominates ${dropped.joinToString(", ") { values[it].toString() }} " +
                    "— those can never be the maximum again while ${values[i]} is in the window, so they are " +
                    "popped off the back for good.",
                readout = "each index enters and leaves at most once",
            )
        }

        window.addLast(i)
        if (i >= k - 1) {
            answers += values[window.first()]
            frames += LinkFrame(
                nodes = values.mapIndexed { p, v ->
                    LinkNode(
                        v.toString(),
                        when {
                            p == window.first() -> LinkMark.RESULT
                            p in (i - k + 1)..i -> LinkMark.ACTIVE
                            else -> LinkMark.GHOST
                        },
                    )
                },
                status = "Window [${i - k + 1}..$i] — the deque's front is index ${window.first()}, so the " +
                    "maximum is ${values[window.first()]}. Read, not computed.",
                readout = "maxima so far: ${answers.joinToString(", ")}",
            )
        }
    }

    frames += LinkFrame(
        nodes = values.map { LinkNode(it.toString(), LinkMark.RESULT) },
        status = "Maxima: ${answers.joinToString(", ")}. Every index was pushed once and popped once, so the " +
            "whole sweep is O(n) — against O(n·k) for rescanning each window.",
        readout = "${values.size} pushes, ${values.size} pops, ${answers.size} answers",
    )

    return frames
}

// ── Interview-prep pattern guide ─────────────────────────────────────────────
// An LRU cache is the canonical two-structure answer: the row is the doubly linked list ordered by
// recency, and the map (not drawn, because it holds no order) supplies the O(1) handle into it.
private fun lruCompositeFrames(): List<LinkFrame> {
    val capacity = 3
    val order = mutableListOf<String>()      // most-recent first
    val frames = mutableListOf<LinkFrame>()

    fun row(marks: Map<String, LinkMark> = emptyMap(), extra: List<LinkNode> = emptyList()) =
        order.map { LinkNode(it, marks[it] ?: LinkMark.IDLE) } + extra

    frames += LinkFrame(
        nodes = listOf(LinkNode("head", LinkMark.GHOST), LinkNode("tail", LinkMark.GHOST)),
        status = "\"get and put in O(1), evict the least recently used\" — a map alone cannot do it, because a map " +
            "has no order. Pair it with a doubly linked list: the row below is that list, most recent on the left.",
        backward = true,
        readout = "capacity $capacity · size 0",
    )

    fun put(key: String) {
        val evicted = if (key !in order && order.size == capacity) order.removeAt(order.lastIndex) else null
        order.remove(key)
        order.add(0, key)
        frames += LinkFrame(
            nodes = row(mapOf(key to LinkMark.ACTIVE)) +
                (evicted?.let { listOf(LinkNode(it, LinkMark.GHOST)) } ?: emptyList()),
            status = "put($key): splice the node in at the head. " +
                if (evicted != null) {
                    "The cache was full, so the *tail* — $evicted, least recently used by construction — is evicted. " +
                        "The list already knows which one that is; nothing had to be searched."
                } else {
                    "The map now stores a reference to this node, not the value, which is what makes every later " +
                        "touch O(1)."
                },
            backward = true,
            readout = "size ${order.size} of $capacity" + (evicted?.let { " · evicted $it" } ?: ""),
        )
    }

    fun get(key: String) {
        val hit = key in order
        if (hit) {
            order.remove(key)
            order.add(0, key)
        }
        frames += LinkFrame(
            nodes = row(if (hit) mapOf(key to LinkMark.RESULT) else emptyMap()),
            status = if (hit) {
                "get($key) hits. The map finds the node in O(1), and because the node carries both pointers it can " +
                    "unlink itself and move to the head in O(1) too — a singly linked list would have to walk to " +
                    "find its predecessor, which is the whole reason the list is doubly linked."
            } else {
                "get($key) misses — $key was evicted earlier. The miss costs one map lookup and touches the list " +
                    "not at all."
            },
            backward = true,
            readout = if (hit) "$key moved to head" else "$key not cached",
        )
    }

    put("A")
    put("B")
    put("C")
    get("A")
    put("D")
    get("B")

    frames += LinkFrame(
        nodes = row(),
        status = "Every operation touched both structures and both stayed O(1). The recipe generalises: swap the " +
            "list for a dynamic array and you get insert / delete / getRandom in O(1) (swap-and-pop into the hole); " +
            "swap it for a parallel stack of running minima and you get a min-stack.",
        backward = true,
        readout = "order: ${order.joinToString(" → ")} (most → least recent)",
    )
    return frames
}

private val linkConfigs = mapOf(
    "composite_design_pattern" to LinkConfig(
        intro = "An LRU cache as the two-structure recipe: a hash map for O(1) location, a doubly linked list for " +
            "the recency order the map cannot hold. The list is drawn; the map is what makes reaching into it free.",
        legend = listOf(
            LinkActive to "Just written",
            LinkResult to "Cache hit, moved to head",
            LinkGhost to "Evicted / sentinel",
        ),
        build = ::lruCompositeFrames,
    ),
    "deque" to LinkConfig(
        intro = "Four O(1) end operations first, then the reason the structure earns its keep: a monotonic deque " +
            "answering sliding-window maximum in one pass.",
        legend = listOf(
            LinkActive to "In deque",
            LinkResult to "Window max",
            LinkPath to "Popped",
        ),
        build = ::dequeFrames,
    ),
    "doubly_linked_list" to LinkConfig(
        intro = "Two pointers per node, drawn above and below the row. The lab spends its frames on the two " +
            "operations that separate it from a singly linked list, then counts both over the same script.",
        legend = listOf(
            LinkActive to "Node in hand",
            LinkPath to "Pointer touched",
            LinkResult to "Settled",
        ),
        build = ::doublyLinkedFrames,
    ),
    "skip_list" to LinkConfig(
        intro = "Express lanes over a sorted linked list. Watch the search drop a level whenever the next node " +
            "on the lane overshoots — then the last frames measure both the comparison count and the coin flips " +
            "the structure is built from.",
        legend = listOf(
            LinkActive to "Overshoot / compare",
            LinkPath to "Search path",
            LinkResult to "Found",
        ),
        build = ::skipListFrames,
    ),
)

private fun linkConfigFor(topicId: String): LinkConfig =
    linkConfigs[topicId] ?: linkConfigs.getValue("doubly_linked_list")

// Exposed so PatternCoverageTest can tell a topic that configured this widget from one that only
// inherits the fallback above.
internal val linkedStructureTopicIds: Set<String> get() = linkConfigs.keys

/** Express lanes are drawn per node, so a presence row shorter than the node row silently loses lanes. */
internal fun linkedStructureFrameCount(topicId: String): Int {
    val frames = linkConfigFor(topicId).build()
    frames.forEachIndexed { index, frame ->
        require(frame.nodes.isNotEmpty()) { "$topicId frame $index draws no nodes" }
        require(frame.status.isNotBlank()) { "$topicId frame $index has no status line" }
        frame.presence.forEachIndexed { level, row ->
            require(row.size == frame.nodes.size) {
                "$topicId frame $index level $level marks ${row.size} nodes but the row has ${frame.nodes.size}"
            }
        }
        frame.levelFocus?.let {
            require(it in frame.presence.indices) { "$topicId frame $index focuses level $it, which does not exist" }
        }
    }
    return frames.size
}

// ── UI ───────────────────────────────────────────────────────────────────────

@Composable
fun LinkedStructureSection(topicId: String) {
    val config = remember(topicId) { linkConfigFor(topicId) }
    val frames = remember(config) { config.build() }
    val playback = rememberPlaybackState(key = config, stepCount = frames.size, initialSpeedMs = 750f)
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

            LinkedCanvas(frame)

            frame.readout?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
            Text(
                frame.status,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 8.dp),
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                config.legend.forEach { (color, label) -> LinkLegend(color, label) }
            }

            PlaybackTransport(playback)
        }
    }
}

@Composable
private fun LinkLegend(color: Color, label: String) {
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
private fun LinkedCanvas(frame: LinkFrame) {
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = TextStyle(color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    val laneStyle = TextStyle(color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 9.sp, fontWeight = FontWeight.Bold)
    val laneColor = MaterialTheme.colorScheme.onSurfaceVariant
    val surfaceTint = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    // Express lanes stack above the base row; the backward lane needs one row's worth of space below.
    val laneCount = frame.presence.size.coerceAtLeast(1)
    val height = (60 + (laneCount - 1) * 34 + if (frame.backward) 30 else 0).dp

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp)
            .background(surfaceTint, RoundedCornerShape(14.dp))
            .padding(6.dp),
    ) {
        Canvas(modifier = Modifier.fillMaxWidth().height(height)) {
            val count = frame.nodes.size
            if (count == 0) return@Canvas
            val padX = 14.dp.toPx()
            val slot = (size.width - 2 * padX) / count
            // Leave real space between boxes — the pointers are the subject here, so the arrows need
            // room to be seen rather than being squeezed into a hairline.
            val boxW = (slot - 14.dp.toPx()).coerceAtLeast(16.dp.toPx())
            val boxH = 24.dp.toPx()
            fun cx(i: Int) = padX + slot * (i + 0.5f)
            val bottomPad = if (frame.backward) 34.dp.toPx() else 16.dp.toPx()
            val baseY = size.height - bottomPad
            val laneGap = 34.dp.toPx()

            fun arrow(from: Offset, to: Offset, color: Color, width: Float) {
                drawLine(color, from, to, strokeWidth = width)
                val dx = to.x - from.x
                val dy = to.y - from.y
                val len = kotlin.math.hypot(dx, dy).coerceAtLeast(1f)
                val ux = dx / len
                val uy = dy / len
                val head = 6.dp.toPx()
                listOf(2.6f, -2.6f).forEach { spread ->
                    val angle = kotlin.math.atan2(uy, ux) + spread
                    drawLine(
                        color,
                        to,
                        Offset(to.x + head * kotlin.math.cos(angle), to.y + head * kotlin.math.sin(angle)),
                        strokeWidth = width,
                    )
                }
            }

            // Express lanes, top-down: level 1 sits just above the base row.
            frame.presence.drop(1).forEachIndexed { index, lane ->
                val level = index + 1
                val y = baseY - laneGap * level
                val focused = frame.levelFocus == level
                val present = lane.withIndex().filter { it.value }.map { it.index }
                present.zipWithNext().forEach { (a, b) ->
                    arrow(
                        Offset(cx(a) + boxW / 2f, y),
                        Offset(cx(b) - boxW / 2f, y),
                        if (focused) LinkActive else laneColor.copy(alpha = 0.45f),
                        if (focused) 4f else 2f,
                    )
                }
                present.forEach { i ->
                    val mark = frame.nodes[i].mark
                    drawRoundRect(
                        color = if (mark == LinkMark.IDLE) LinkIdle.copy(alpha = 0.5f) else markColor(mark),
                        topLeft = Offset(cx(i) - boxW / 4f, y - boxH / 4f),
                        size = Size(boxW / 2f, boxH / 2f),
                        cornerRadius = CornerRadius(4f, 4f),
                    )
                }
                val lay = textMeasurer.measure("L${level + 1}", laneStyle)
                drawText(lay, topLeft = Offset(2f, y - lay.size.height / 2f))
            }

            // Base row.
            frame.nodes.forEachIndexed { i, node ->
                if (i < count - 1) {
                    arrow(
                        Offset(cx(i) + boxW / 2f, baseY - 4f),
                        Offset(cx(i + 1) - boxW / 2f, baseY - 4f),
                        if (node.mark == LinkMark.PATH) LinkPath else laneColor.copy(alpha = 0.5f),
                        if (node.mark == LinkMark.PATH) 4f else 2f,
                    )
                    if (frame.backward) {
                        arrow(
                            Offset(cx(i + 1) - boxW / 2f, baseY + boxH + 4f),
                            Offset(cx(i) + boxW / 2f, baseY + boxH + 4f),
                            laneColor.copy(alpha = 0.5f),
                            2f,
                        )
                    }
                }
                drawRoundRect(
                    color = markColor(node.mark),
                    topLeft = Offset(cx(i) - boxW / 2f, baseY - boxH / 2f),
                    size = Size(boxW, boxH),
                    cornerRadius = CornerRadius(7f, 7f),
                )
                val lay = textMeasurer.measure(node.label, labelStyle)
                drawText(
                    lay,
                    topLeft = Offset(cx(i) - lay.size.width / 2f, baseY - lay.size.height / 2f),
                )
            }
        }
    }
}

private fun markColor(mark: LinkMark): Color = when (mark) {
    LinkMark.IDLE -> LinkIdle
    LinkMark.PATH -> LinkPath
    LinkMark.ACTIVE -> LinkActive
    LinkMark.RESULT -> LinkResult
    LinkMark.GHOST -> LinkGhost
}
