package com.algora.app.feature.topics

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
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

// Skipped is a node the step decided not to visit (a segment-tree range outside the query): dimmer than Idle.
// Warn is a key that broke a rule (the third key in a B-tree node that holds two).
// Ghost is a node or bar that is not there yet: where an insert will land, the next bar a walk reads.
// Answer is a sparse table's result cell.
private enum class TreeState { Idle, Path, Active, Marked, Skipped, Warn, Ghost, Answer }

private class TreeNodeSpec(
    val id: Int,
    val label: String,
    val parent: Int?,
    val order: Int,
    val state: TreeState = TreeState.Idle,
    // A small caption above the label (a segment-tree node's range, "0..3").
    val sub: String? = null,
    // The substring on the edge down from the parent (a suffix tree's "ba$").
    val edge: String? = null,
    // A multi-key node (B-tree): one tile per key, padded with empty slots up to [capacity].
    val keys: List<KeyCell>? = null,
    val capacity: Int = 0,
    // Non-null draws the node as overflowing: a red outline, and this line under it.
    val overflow: String? = null,
    // A small circle on the top-right corner (visit order, heap index, balance factor), ringed in
    // [badgeTone]'s colour.
    val badge: String? = null,
    val badgeTone: TreeState = TreeState.Idle,
    // A green ring round the tile: a trie node that ends a word.
    val ring: Boolean = false,
)

private class KeyCell(val value: String, val state: TreeState = TreeState.Idle)

// Aho-Corasick's failure links run between nodes that are not parent and child, and the canvas
// derives its edges from `parent` alone. Rather than give the tree model a second edge kind, a frame
// carries the links it wants drawn — same additive shape as CloudFrame's later fields.
private class TreeLink(val from: Int, val to: Int)

private class TreeFrame(
    val nodes: List<TreeNodeSpec>,
    val status: String,
    val links: List<TreeLink> = emptyList(),
    val story: TreeStory? = null,
)

private class TreeConfig(
    val intro: String,
    val markedLabel: String,
    val build: () -> List<TreeFrame>,
    // Non-null adds a fourth legend chip; only the configs that draw links need it.
    val linkLabel: String? = null,
    // Non-null draws the step-by-step card (docs mocks) instead of the plain one.
    val storyStyle: StoryStyle? = null,
    // Tabs over the story card, each its own storyboard (B-Tree / B+ Tree). Empty means just [build].
    val variants: List<StoryVariant> = emptyList(),
    // The card header for topics without a hand-written storyboard.
    val cardTitle: String = "",
    val cardNote: String = "",
)

// One tab of a story card. [style] overrides the config's when the tab needs other legend words.
private class StoryVariant(val label: String, val build: () -> List<TreeFrame>, val style: StoryStyle? = null)

// ── Story card ───────────────────────────────────────────────────────────────
// The redesigned Union-Find / Segment Tree / LCA labs: a header naming the operation, the tree, an array
// that mirrors it cell for cell (parent[], the input, depth[]), a legend of only what is on screen, then
// value chips and a headline whose key term takes its node's colour.

private enum class LegendKey { Active, Path, Marked, Skipped, Link, Next, Overflow, Warn, Ring, Ghost, Answer }

private class StoryStyle(
    val canvasHeight: Int,
    val rowGap: Int,
    // In display order; an entry shows only while its colour is on screen.
    val legend: List<Pair<LegendKey, String>>,
    val noteMono: Boolean = false,
    // LCA's d0…dN row labels and dashed rules, so "equal depth" is something you can see.
    val depthGuides: Boolean = false,
    // Array cells wide enough for the values to breathe (4 cells) instead of 8 squares.
    val wideCells: Boolean = false,
    // "Insert 5 of 7" rather than "Step 5 of 7".
    val stepNoun: String = "Step",
)

// A blank value draws an empty slot (a heap array's unused capacity).
private class StripCell(val header: String, val value: String, val state: TreeState = TreeState.Idle)

private enum class RowState { Done, Current, Pending }

// One line of a list under the tree: "[2]  a$  inserting".
private class StoryRow(val index: String, val text: String, val status: String, val state: RowState)

private class TreeStory(
    val title: String,
    val note: String,
    val stripLabel: String?,
    val cells: List<StripCell>,
    val chips: List<Pair<String?, String>>,
    // `{…}` marks the term drawn in [emphasis]'s colour.
    val headline: String,
    val emphasis: TreeState,
    val body: String,
    val accentedChips: Set<Int> = emptySet(),
    val warnedChips: Set<Int> = emptySet(),
    val positiveChips: Set<Int> = emptySet(),
    // Children whose edge to their parent is the next move, drawn dashed in the current colour.
    val nextEdges: Set<Int> = emptySet(),
    // Children whose edge to their parent takes a state's colour, overriding the both-ends-lit rule.
    val edgeStates: Map<Int, TreeState> = emptyMap(),
    // The query range, bracketed under the array.
    val bracket: IntRange? = null,
    val focusDepth: Int? = null,
    val footnote: String? = null,
    val rows: List<StoryRow> = emptyList(),
    val rowColumns: Int = 2,
    // B+ leaves are a linked list; draw the arrows between neighbours.
    val leafChain: Boolean = false,
    // Generic stories reuse a status line, which may carry **bold** / *italic* markdown.
    val markdown: Boolean = false,
    // Words on the right of the strip's label ("3 of 6", "1 new node").
    val stripNote: String? = null,
    // Empty strip cells drawn as dashed slots still to fill (a visit order).
    val dashedEmpty: Boolean = false,
    // A label and note over [rows] ("COMPARISONS · left < node < right").
    val rowsLabel: String? = null,
    val rowsNote: String? = null,
    // The suffix list outlines its current row; the BST's comparisons only tint it.
    val rowBorder: Boolean = true,
    // Status words in the row's text colour rather than green (a comparison's "go right").
    val plainStatus: Boolean = false,
    // Lay a binary tree out by in-order position, so a lone right child still sits to the right.
    val inorder: Boolean = false,
    // One title per root, drawn side by side with an arrow between (BEFORE → AFTER).
    val panels: List<String> = emptyList(),
    // Edges stay plain whatever their ends' states (a traversal's order is on the badges).
    val plainEdges: Boolean = false,
    // Badge text in its tone's colour (a balance factor) rather than the text colour (a visit number).
    val badgeInk: Boolean = false,
    // Rows of cells laid on a column grid instead of a tree (Fenwick bars, a sparse table).
    val grid: List<StoryGridBlock> = emptyList(),
    // Per-step legend words, over the style's.
    val legendLabels: Map<LegendKey, String> = emptyMap(),
) {
    val status: String get() = HeadlineMark.replace(headline) { it.groupValues[2] } + " " + body
}

// `{…}` in a headline takes the story's emphasis colour; `{w:…}` red, `{a:…}` yellow, `{p:…}` blue and
// `{m:…}` green name the colour outright, for a headline that points at two different nodes.
private val HeadlineMark = Regex("""\{(?:([wapm]):)?(.+?)\}""")

// One cell on a grid row: [span] columns from [start], or an empty slot when [text] is blank.
private class StoryGridCell(val start: Int, val span: Int, val text: String, val state: TreeState = TreeState.Idle)

// [label] / [sub] sit in a gutter on the left ("k2" over "len 4"); [labelState] colours them.
private class StoryGridRow(
    val cells: List<StoryGridCell>,
    val label: String? = null,
    val sub: String? = null,
    val labelState: TreeState = TreeState.Idle,
)

private class StoryGridBlock(
    val title: String?,
    val columns: Int,
    val rows: List<StoryGridRow>,
    val headers: List<String>? = null,
    val rowHeight: Int = 36,
)

private val PathColor = SimColors.Blue
private val ActiveColor = SimColors.Active
private val MarkedColor = SimColors.Green
// Dark text on the yellow current pill: white on #F5C542 is unreadable.
private val OnActiveColor = Color(0xFF1F1A0A)
private val LinkColor = Color(0xFFF97316)

// Mutable tree the builders mutate; each `frame()` call snapshots it.
private class TreeBuilder {
    private class Node(val id: Int, var label: String, var parent: Int?, var order: Int, val sub: String?, var edge: String?)

    private val nodes = LinkedHashMap<Int, Node>()
    private var nextId = 0
    val frames = mutableListOf<TreeFrame>()

    fun add(label: String, parent: Int?, order: Int, sub: String? = null, edge: String? = null): Int {
        val id = nextId++
        nodes[id] = Node(id, label, parent, order, sub, edge)
        return id
    }

    fun relabel(id: Int, label: String) { nodes.getValue(id).label = label }
    fun reparent(id: Int, parent: Int?, order: Int) {
        nodes.getValue(id).parent = parent
        nodes.getValue(id).order = order
    }
    fun remove(id: Int) { nodes.remove(id) }
    fun setEdge(id: Int, edge: String?) { nodes.getValue(id).edge = edge }
    fun labelOf(id: Int) = nodes.getValue(id).label
    fun parentOf(id: Int) = nodes[id]?.parent

    fun frame(
        status: String,
        active: Set<Int> = emptySet(),
        path: Set<Int> = emptySet(),
        marked: Set<Int> = emptySet(),
        links: List<TreeLink> = emptyList(),
        skipped: Set<Int> = emptySet(),
        story: TreeStory? = null,
        ghost: Set<Int> = emptySet(),
        badges: Map<Int, Pair<String, TreeState>> = emptyMap(),
        rings: Set<Int> = emptySet(),
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
                            n.id in ghost -> TreeState.Ghost
                            n.id in active -> TreeState.Active
                            n.id in marked -> TreeState.Marked
                            n.id in path -> TreeState.Path
                            n.id in skipped -> TreeState.Skipped
                            else -> TreeState.Idle
                        },
                        sub = n.sub,
                        edge = n.edge,
                        badge = badges[n.id]?.first,
                        badgeTone = badges[n.id]?.second ?: TreeState.Idle,
                        ring = n.id in rings,
                    )
                },
                story?.status ?: status,
                links,
                story,
            ),
        )
    }
}

// ── Binary search tree: insert then search, both walking the compare path ────
private fun bstFrames(): List<TreeFrame> = bstStoryFrames(0)

// Insert, search and delete on one small tree, three steps each. The comparison list is the whole
// algorithm: one line per level, each ending in which way to go.
private fun bstStoryFrames(kind: Int): List<TreeFrame> {
    val b = TreeBuilder()
    val n50 = b.add("50", null, 0)
    val n30 = b.add("30", n50, 0)
    val n70 = b.add("70", n50, 1)
    val n20 = b.add("20", n30, 0)
    val n40 = b.add("40", n30, 1)

    fun row(text: String, status: String, current: Boolean) =
        StoryRow("", text, status, if (current) RowState.Current else RowState.Done)

    fun story(
        rows: List<StoryRow>,
        chips: List<Pair<String?, String>>,
        headline: String,
        body: String,
        emphasis: TreeState = TreeState.Active,
        next: Set<Int> = emptySet(),
    ) = TreeStory(
        title = "BINARY SEARCH TREE",
        note = "",
        stripLabel = null,
        cells = emptyList(),
        chips = chips,
        headline = headline,
        emphasis = emphasis,
        body = body,
        rows = rows,
        rowColumns = 1,
        nextEdges = next,
        rowsLabel = "COMPARISONS",
        rowsNote = "left < node < right",
        rowBorder = false,
        plainStatus = true,
        inorder = true,
    )

    when (kind) {
        0 -> {
            val r1 = row("60 > 50", "go right", false)
            b.frame(
                "", active = setOf(n50),
                story = story(
                    listOf(row("60 > 50", "go right", true)),
                    listOf("key" to "60", "depth" to "0"),
                    "60 is greater than {50}, so it goes right.",
                    "Every insert starts at the root and makes one comparison per level.",
                ),
            )
            val ghost = b.add("60", n70, 0)
            b.frame(
                "", active = setOf(n70), path = setOf(n50), ghost = setOf(ghost),
                story = story(
                    listOf(r1, row("60 < 70", "go left", true)),
                    listOf("key" to "60", "depth" to "2"),
                    "60 is less than {70}, so it goes left.",
                    "70 has no left child, so 60 is inserted there on the next step.",
                    next = setOf(ghost),
                ),
            )
            b.frame(
                "", path = setOf(n50, n70), marked = setOf(ghost),
                story = story(
                    listOf(r1, row("60 < 70", "go left", false), row("70.left is empty", "insert", true)),
                    listOf("key" to "60", "depth" to "2"),
                    "60 becomes 70's left {m:child}.",
                    "No existing node moved. The tree still reads 20, 30, 40, 50, 60, 70 in order.",
                ),
            )
        }
        1 -> {
            b.add("60", n70, 0)
            b.frame(
                "", active = setOf(n50),
                story = story(
                    listOf(row("40 < 50", "go left", true)),
                    listOf("key" to "40", "checked" to "1"),
                    "40 is less than {50}, so search left.",
                    "Everything bigger than 50 is on the right, so half the tree is ruled out at once.",
                ),
            )
            b.frame(
                "", active = setOf(n30), path = setOf(n50),
                story = story(
                    listOf(row("40 < 50", "go left", false), row("40 > 30", "go right", true)),
                    listOf("key" to "40", "checked" to "2"),
                    "40 is greater than {30}, so search right.",
                    "One comparison per level. The path only ever goes down.",
                ),
            )
            b.frame(
                "", path = setOf(n50, n30), marked = setOf(n40),
                story = story(
                    listOf(row("40 < 50", "go left", false), row("40 > 30", "go right", false), row("40 = 40", "found", true)),
                    listOf("key" to "40", "checked" to "3"),
                    "Found {m:40} after 3 comparisons.",
                    "A search looks at one node per level, so a balanced tree answers in O(log n).",
                ),
            )
        }
        else -> {
            val n60 = b.add("60", n70, 0)
            b.frame(
                "", active = setOf(n30), path = setOf(n50),
                story = story(
                    listOf(row("30 < 50", "go left", false), row("30 = 30", "found", true)),
                    listOf("delete" to "30", "children" to "2"),
                    "{30} has two children, so it can't just be removed.",
                    "Removing it would leave 20 and 40 without a parent. It is replaced by its in-order successor.",
                ),
            )
            b.frame(
                "", active = setOf(n40), path = setOf(n30),
                story = story(
                    listOf(row("30 = 30", "found", false), row("min of 30's right", "40", true)),
                    listOf("delete" to "30", "successor" to "40"),
                    "Its successor is {40}, the smallest key on its right.",
                    "The successor has at most one child, so it is easy to lift out.",
                ),
            )
            b.remove(n40)
            b.relabel(n30, "40")
            b.frame(
                "", marked = setOf(n30),
                story = story(
                    listOf(row("30 = 30", "found", false), row("min of 30's right", "40", false), row("40 replaces 30", "done", true)),
                    listOf("delete" to "30", "moved" to "1 node"),
                    "{m:40} takes 30's place, and the tree stays ordered.",
                    "In order it still reads 20, 40, 50, 60, 70. Only one key moved.",
                ),
            )
            check(n60 >= 0)
        }
    }
    return b.frames
}


// ── General tree: depth-first then breadth-first over a fixed hierarchy ──────
private fun treeTraversalFrames(): List<TreeFrame> = traversalFrames(0)

// A small directory tree walked three ways. Each step visits one node: its badge is its place in the
// order, the strip fills left to right, and the stack chip is the path the recursion is standing on.
private fun traversalFrames(kind: Int): List<TreeFrame> {
    val b = TreeBuilder()
    val root = b.add("/", null, 0)
    val usr = b.add("usr", root, 0)
    val etc = b.add("etc", root, 1)
    val bin = b.add("bin", usr, 0)
    val lib = b.add("lib", usr, 1)
    val conf = b.add("conf", etc, 0)
    val parent = mapOf(usr to root, etc to root, bin to usr, lib to usr, conf to etc)

    val (orderChip, sequence, lines) = when (kind) {
        0 -> Triple(
            "L → N → R",
            listOf(bin, usr, lib, root, conf, etc),
            listOf(
                "Visit {bin} first: it is the leftmost node." to "Inorder goes left as far as it can before visiting anything.",
                "Visit {usr}. Its left side, bin, is finished." to "Now the node itself, then its right subtree.",
                "Visit {lib}. usr came before it because its left side was finished." to
                    "Inorder visits left, then the node, then right. On a search tree this reads out sorted order.",
                "Visit {/}. The whole left subtree is done." to "The root comes in the middle of inorder, not at the start.",
                "Visit {conf}, the leftmost node on the right side." to "etc has a left child, so conf goes before etc.",
                "Visit {etc} last." to "Left, node, right at every level gives bin, usr, lib, /, conf, etc.",
            ),
        )
        1 -> Triple(
            "N → L → R",
            listOf(root, usr, bin, lib, etc, conf),
            listOf(
                "Visit {/} first: preorder starts at the root." to
                    "Every node is visited before its children, the way a listing prints a folder before its files.",
                "Visit {usr}, then go into its children." to "Preorder follows the left branch down before looking right.",
                "Visit {bin}, a leaf." to "Nothing below it, so back up to usr's right child.",
                "Visit {lib}. usr's subtree is finished." to "Next the walk returns to the root's right side.",
                "Visit {etc}, the root's right child." to "Its child comes after it, as always in preorder.",
                "Visit {conf} last." to
                    "Preorder gave /, usr, bin, lib, etc, conf: every parent before its children. Copying a tree uses this order.",
            ),
        )
        else -> Triple(
            "L → R → N",
            listOf(bin, lib, usr, conf, etc, root),
            listOf(
                "Visit {bin} first: postorder starts at the deepest left leaf." to
                    "A node waits until all of its children are done.",
                "Visit {lib}, usr's other child." to "With both children done, usr can go next.",
                "Visit {usr} now that bin and lib are done." to "Children always come before their parent in postorder.",
                "Visit {conf}, a leaf on the right side." to "The root still waits for its whole right subtree.",
                "Visit {etc}. Its only child is done." to "Only the root is left.",
                "Visit {/} last." to
                    "Postorder gave bin, lib, usr, conf, etc, /. Deleting a folder needs this order: files before the folder.",
            ),
        )
    }

    sequence.forEachIndexed { step, current ->
        val done = sequence.take(step)
        val stack = generateSequence(current) { parent[it] }.toList().reversed().joinToString(" › ") { b.labelOf(it) }
        val (headline, body) = lines[step]
        b.frame(
            "",
            active = setOf(current),
            marked = done.toSet(),
            badges = (done.mapIndexed { i, id -> id to ("${i + 1}" to TreeState.Marked) } +
                (current to ("${step + 1}" to TreeState.Active))).toMap(),
            story = TreeStory(
                title = "TRAVERSAL",
                note = "",
                stripLabel = "VISIT ORDER",
                stripNote = "${step + 1} of ${sequence.size}",
                cells = sequence.indices.map { i ->
                    when {
                        i < step -> StripCell("", b.labelOf(sequence[i]), TreeState.Marked)
                        i == step -> StripCell("", b.labelOf(sequence[i]), TreeState.Active)
                        else -> StripCell("", "", TreeState.Skipped)
                    }
                },
                dashedEmpty = true,
                chips = listOf("order" to orderChip, "stack" to stack),
                headline = headline,
                emphasis = TreeState.Active,
                body = body,
                inorder = true,
                plainEdges = true,
            ),
        )
    }
    return b.frames
}

// ── Binary max-heap: array-backed insert with sift-up ────────────────────────
private fun heapFrames(): List<TreeFrame> = heapStoryFrames(max = true)

// One insert and its sift-up, compared a level at a time. The badges are array indices, so the tree
// and the backing array can be read against each other.
private fun heapStoryFrames(max: Boolean): List<TreeFrame> {
    val heap = if (max) mutableListOf(12, 9, 3, 5) else mutableListOf(2, 5, 4, 9)
    val value = if (max) 20 else 1
    val word = if (max) "bigger" else "smaller"
    val frames = mutableListOf<TreeFrame>()
    heap.add(value)

    fun frame(
        active: Set<Int>,
        marked: Set<Int>,
        compare: Int?,
        chips: List<Pair<String?, String>>,
        headline: String,
        body: String,
        emphasis: TreeState = TreeState.Active,
    ) {
        fun stateOf(i: Int) = when (i) {
            in active -> TreeState.Active
            in marked -> TreeState.Marked
            else -> TreeState.Idle
        }
        val nodes = heap.indices.map { i ->
            TreeNodeSpec(
                id = i,
                label = "${heap[i]}",
                parent = if (i == 0) null else (i - 1) / 2,
                order = if (i % 2 == 1) 0 else 1,
                state = stateOf(i),
                badge = if (i in active) "$i" else null,
            )
        }
        val story = TreeStory(
            title = if (max) "MAX-HEAP" else "MIN-HEAP",
            note = "",
            stripLabel = "BACKING ARRAY",
            stripNote = "parent(i) = ⌊(i−1)/2⌋",
            cells = heap.indices.map { StripCell("$it", "${heap[it]}", stateOf(it)) },
            chips = chips,
            headline = headline,
            emphasis = emphasis,
            body = body,
            edgeStates = compare?.let { mapOf(it to TreeState.Active) }.orEmpty(),
            plainEdges = compare == null,
        )
        frames += TreeFrame(nodes, story.status, story = story)
    }
    fun swap(a: Int, b: Int) { heap[a] = heap[b].also { heap[b] = heap[a] } }

    val p1 = heap[1]
    frame(setOf(4), emptySet(), null, listOf("i" to "4", "parent" to "1"),
        "Insert {$value} at the next free slot, index 4.",
        "The tree stays complete: the bottom row fills left to right. Now $value may be out of order.")
    frame(setOf(1, 4), emptySet(), 4, listOf("i" to "4", "parent" to "1"),
        "{$value} is $word than its parent {$p1}, so they swap.",
        "Then $value is compared with ${heap[0]}. Sift-up stops once the parent is ${if (max) "larger" else "smaller"}.")
    swap(1, 4)
    val p0 = heap[0]
    frame(setOf(0, 1), emptySet(), 1, listOf("i" to "1", "parent" to "0"),
        "{$value} is $word than {$p0} too, so they swap again.",
        "$value has climbed one level. The root has no parent, so this is the last comparison.")
    swap(0, 1)
    frame(emptySet(), setOf(0), null, listOf("i" to "0", "swaps" to "2"),
        "{$value} reaches the root and stops.",
        "Two swaps, one per level, so an insert costs O(log n). The ${if (max) "largest" else "smallest"} value is always at index 0.",
        TreeState.Marked)
    return frames
}

// ── Trie: insert words character by character, then look one up ──────────────
private fun trieFrames(): List<TreeFrame> {
    val b = TreeBuilder()
    val root = b.add("•", null, 0)
    val c = b.add("c", root, 0)
    val a = b.add("a", c, 0)
    val t = b.add("t", a, 0)

    fun story(
        word: String,
        cells: List<TreeState>,
        note: String,
        chips: List<Pair<String?, String>>,
        headline: String,
        body: String,
        edges: Map<Int, TreeState>,
        emphasis: TreeState = TreeState.Active,
    ) = TreeStory(
        title = "INSERT \"$word\"",
        note = "shared prefixes",
        stripLabel = "WORD",
        stripNote = note,
        cells = word.mapIndexed { i, ch -> StripCell("", "$ch", cells[i]) },
        chips = chips,
        headline = headline,
        emphasis = emphasis,
        body = body,
        edgeStates = edges,
    )

    val adding = TreeState.Active
    b.frame(
        "", active = setOf(c, a, t),
        story = story("cat", List(3) { adding }, "3 new nodes", listOf("stored" to "none", "prefix" to "\"\""),
            "The trie is empty, so every letter of \"cat\" is {new}.",
            "Each node is one character. The path from the root spells the prefix.",
            mapOf(c to adding, a to adding, t to adding)),
    )
    b.frame(
        "", rings = setOf(t),
        story = story("cat", List(3) { TreeState.Idle }, "end of word", listOf("stored" to "cat", "prefix" to "\"cat\""),
            "t is marked as the {m:end of a word}.",
            "Without the mark, \"ca\" would look like a stored word too.",
            mapOf(c to TreeState.Idle)),
    )
    b.frame(
        "", active = setOf(c), rings = setOf(t),
        story = story("car", listOf(adding, TreeState.Idle, TreeState.Idle), "following c", listOf("stored" to "cat", "prefix" to "\"c\""),
            "\"car\" starts with {c}, which already exists.",
            "Inserting walks down from the root and only creates a node when the letter is missing.",
            mapOf(c to TreeState.Path)),
    )
    val r = b.add("r", a, 1)
    b.frame(
        "", active = setOf(r), path = setOf(root, c, a), rings = setOf(t),
        story = story("car", listOf(TreeState.Path, TreeState.Path, adding), "1 new node", listOf("stored" to "cat", "prefix" to "\"ca\""),
            "\"c\" and \"a\" already exist. Only {r} is new.",
            "Words with the same prefix share nodes. Next, r gets marked as the end of a word.",
            mapOf(c to TreeState.Path, a to TreeState.Path, r to adding)),
    )
    b.frame(
        "", rings = setOf(t, r),
        story = story("car", List(3) { TreeState.Idle }, "end of word", listOf("stored" to "cat, car", "letters" to "4 of 6"),
            "Both words fit in {m:4} letter nodes instead of 6.",
            "r is marked as an end of word. A lookup for \"ca\" walks 2 nodes and finds no mark, so it is only a prefix.",
            mapOf(c to TreeState.Idle), TreeState.Marked),
    )
    return b.frames
}

// ── AVL: three ascending inserts force a left rotation ───────────────────────
private fun avlFrames(): List<TreeFrame> = balanceFrames(redBlack = false)

// Three ascending inserts, the third of which breaks the rule; that step shows the tree before and
// after its rotation side by side. The badges carry the rule itself: a balance factor, or a colour.
private fun balanceFrames(redBlack: Boolean): List<TreeFrame> {
    val frames = mutableListOf<TreeFrame>()
    val tone = mapOf("0" to TreeState.Marked, "-1" to TreeState.Idle, "-2" to TreeState.Warn, "B" to TreeState.Idle, "R" to TreeState.Warn)

    fun node(id: Int, label: String, parent: Int?, order: Int, state: TreeState, badge: String) =
        TreeNodeSpec(id, label, parent, order, state, badge = badge, badgeTone = tone.getValue(badge))

    fun frame(
        nodes: List<TreeNodeSpec>,
        chips: List<Pair<String?, String>>,
        headline: String,
        body: String,
        emphasis: TreeState = TreeState.Active,
        panels: List<String> = emptyList(),
        warned: Set<Int> = emptySet(),
        activeWord: String,
    ) {
        val story = TreeStory(
            title = if (redBlack) "RED-BLACK" else "AVL",
            note = "",
            stripLabel = null,
            cells = emptyList(),
            chips = chips,
            headline = headline,
            emphasis = emphasis,
            body = body,
            warnedChips = warned,
            inorder = true,
            panels = panels,
            plainEdges = true,
            badgeInk = true,
            legendLabels = mapOf(LegendKey.Active to activeWord),
        )
        frames += TreeFrame(nodes, story.status, story = story)
    }

    val A = TreeState.Active
    val M = TreeState.Marked
    val I = TreeState.Idle
    val W = TreeState.Warn
    if (!redBlack) {
        frame(listOf(node(0, "10", null, 0, A, "0")), listOf("bf(10)" to "0", "height" to "1"),
            "Insert {10}. A single node is balanced.",
            "A node's balance factor is its left height minus its right height.", activeWord = "Inserted")
        frame(listOf(node(0, "10", null, 0, I, "-1"), node(1, "20", 0, 1, A, "0")), listOf("bf(10)" to "-1", "height" to "2"),
            "Insert {20}. 10 now leans right by one.",
            "AVL allows balance factors of −1, 0 and +1, so nothing needs fixing yet.", activeWord = "Inserted")
        frame(
            listOf(
                node(0, "10", null, 0, W, "-2"), node(1, "20", 0, 1, A, "-1"), node(2, "30", 1, 1, I, "0"),
                node(3, "20", null, 1, M, "0"), node(4, "10", 3, 0, M, "0"), node(5, "30", 3, 1, M, "0"),
            ),
            listOf("bf(10)" to "-2", "height" to "3 → 2"),
            "{w:10} is right-heavy, so the tree rotates left around {20}.",
            "20 moves up and 10 becomes its left child. Without the rotation, the tree turns into a linked list.",
            panels = listOf("BEFORE", "AFTER"), warned = setOf(0), activeWord = "Pivot",
        )
        frame(listOf(node(0, "20", null, 0, M, "0"), node(1, "10", 0, 0, M, "0"), node(2, "30", 0, 1, M, "0")),
            listOf("bf(20)" to "0", "height" to "2"),
            "Every node is balanced again: all factors are {0}.",
            "Height went from 3 to 2. One rotation keeps lookups at O(log n).", M, activeWord = "Pivot")
    } else {
        frame(listOf(node(0, "10", null, 0, A, "B")), listOf("black height" to "1"),
            "Insert {10}. The root is always black.",
            "Each node carries one colour bit. Rules on those colours keep the tree roughly balanced.", activeWord = "Inserted")
        frame(listOf(node(0, "10", null, 0, I, "B"), node(1, "20", 0, 1, A, "R")), listOf("black height" to "1", "20" to "red"),
            "Insert {20} as red, under the black root.",
            "A new node is always red. A red child of a black parent breaks no rule.", activeWord = "Inserted")
        frame(
            listOf(
                node(0, "10", null, 0, I, "B"), node(1, "20", 0, 1, A, "R"), node(2, "30", 1, 1, W, "R"),
                node(3, "20", null, 1, M, "B"), node(4, "10", 3, 0, M, "R"), node(5, "30", 3, 1, M, "R"),
            ),
            listOf("red-red" to "20 → 30", "rotate" to "left at 10"),
            "{w:30} is red under red {20}, so rotate left and recolor.",
            "20 moves up and turns black, 10 turns red. Now no red node has a red child.",
            panels = listOf("BEFORE", "AFTER"), warned = setOf(0), activeWord = "Pivot",
        )
        frame(listOf(node(0, "20", null, 0, M, "B"), node(1, "10", 0, 0, M, "R"), node(2, "30", 0, 1, M, "R")),
            listOf("black height" to "1", "rotations" to "1"),
            "Every path from the root now passes {1} black node.",
            "Red-black trees allow more slack than AVL, so inserts rotate less often, while height stays under 2·log n.",
            M, activeWord = "Pivot")
    }
    return frames
}

// ── Fenwick tree: prefix query and point update over one array ───────────────
private val FenwickA = listOf(3, 1, 5, 4, 2, 9, 2, 6)

private fun fenwickStoryFrames(update: Boolean): List<TreeFrame> {
    val a = FenwickA.toMutableList()
    fun low(i: Int) = i and -i
    fun tree(i: Int) = (i - low(i) + 1..i).sumOf { a[it - 1] }
    val frames = mutableListOf<TreeFrame>()

    fun frame(
        states: Map<Int, TreeState>,
        cellStates: Map<Int, TreeState>,
        chips: List<Pair<String?, String>>,
        headline: String,
        body: String,
        emphasis: TreeState = TreeState.Active,
        accented: Set<Int> = emptySet(),
    ) {
        fun bar(i: Int): StoryGridCell {
            val label = if (low(i) == 1) "${tree(i)}" else "T$i = ${tree(i)}"
            return StoryGridCell(i - low(i), low(i), label, states[i] ?: TreeState.Idle)
        }
        val levels = listOf(1, 2, 4, 8).map { size -> (1..8).filter { low(it) == size } }
        val story = TreeStory(
            title = "FENWICK TREE",
            note = "",
            stripLabel = null,
            cells = emptyList(),
            chips = chips,
            headline = headline,
            emphasis = emphasis,
            body = body,
            accentedChips = accented,
            grid = listOf(
                StoryGridBlock(
                    "ARRAY A",
                    8,
                    listOf(StoryGridRow((0 until 8).map { StoryGridCell(it, 1, "${a[it]}", cellStates[it + 1] ?: TreeState.Idle) })),
                    headers = (1..8).map { "$it" },
                    rowHeight = 40,
                ),
                StoryGridBlock("TREE T · EACH BAR COVERS ITS RANGE", 8, levels.map { row -> StoryGridRow(row.map { bar(it) }) }, rowHeight = 32),
            ),
        )
        frames += TreeFrame(emptyList(), story.status, story = story)
    }

    val R = TreeState.Active
    val D = TreeState.Marked
    val N = TreeState.Ghost
    if (!update) {
        frame(mapOf(7 to R, 6 to N), emptyMap(), listOf("i" to "7", "next" to "7 − 1 = 6", "sum" to "${tree(7)}"),
            "Add {T7 = ${tree(7)}}. It covers only A[7].",
            "prefix(7) starts at i = 7. A bar's length is the lowest set bit of its index, here 1.")
        frame(mapOf(7 to D, 6 to R, 4 to N), emptyMap(), listOf("i" to "6", "next" to "6 − 2 = 4", "sum" to "${tree(7)} + ${tree(6)} = ${tree(7) + tree(6)}"),
            "Add {T6 = ${tree(6)}}. It covers A[5..6].",
            "Clearing the lowest bit takes 6 to 4. T4 covers A[1..4], so prefix(7) takes 3 reads.")
        val total = tree(7) + tree(6) + tree(4)
        frame(mapOf(7 to D, 6 to D, 4 to R), emptyMap(), listOf("i" to "4", "next" to "4 − 4 = 0", "sum" to "${tree(7) + tree(6)} + ${tree(4)} = $total"),
            "Add {T4 = ${tree(4)}}. It covers A[1..4].",
            "Clearing the lowest bit of 4 gives 0, so the walk ends here.")
        frame(mapOf(7 to D, 6 to D, 4 to D), (1..7).associateWith { TreeState.Path }, listOf("prefix(7)" to "$total", "reads" to "3"),
            "prefix(7) = {m:$total} from 3 reads, not 7.",
            "Each step clears one bit of i, so any prefix sum takes at most log n reads.", accented = setOf(0))
    } else {
        a[2] += 3
        frame(mapOf(3 to R, 4 to N), mapOf(3 to R), listOf("A[3]" to "5 + 3 = 8", "next" to "3 + 1 = 4"),
            "Adding 3 to A[3] starts at {T3}.",
            "Every bar whose range contains index 3 must change, and there are only log n of them.")
        frame(mapOf(3 to D, 4 to R, 8 to N), mapOf(3 to R), listOf("T4" to "13 + 3 = ${tree(4)}", "next" to "4 + 4 = 8"),
            "{T4} covers A[1..4], so it gains 3 too.",
            "Adding the lowest set bit jumps to the next bar that also covers index 3.")
        frame(mapOf(3 to D, 4 to D, 8 to R), mapOf(3 to R), listOf("T8" to "32 + 3 = ${tree(8)}", "next" to "8 + 8 = 16"),
            "{T8} covers everything, so it gains 3 as well.",
            "16 is past the end of the array, so the walk stops.")
        frame(mapOf(3 to D, 4 to D, 8 to D), emptyMap(), listOf("bars changed" to "3", "of" to "8"),
            "The update touched {m:3} bars out of 8.",
            "Like the query, the walk moves one bit per step, so an update costs O(log n) too.", TreeState.Marked)
    }
    return frames
}

// ── Sparse table: every power-of-two block's min, then a query from two overlapping blocks ──
private fun sparseTableFrames(): List<TreeFrame> {
    val a = listOf(5, 2, 4, 7, 1, 3, 6, 8)
    val table = mutableListOf(a)
    while (1 shl table.size <= a.size) {
        val prev = table.last()
        val half = 1 shl (table.size - 1)
        table += (0..a.size - (1 shl table.size)).map { minOf(prev[it], prev[it + half]) }
    }
    val frames = mutableListOf<TreeFrame>()

    fun frame(
        title: String,
        note: String,
        state: (Int, Int) -> TreeState,
        focusRow: Int?,
        chips: List<Pair<String?, String>>,
        headline: String,
        body: String,
        accented: Set<Int> = emptySet(),
    ) {
        val story = TreeStory(
            title = title,
            note = note,
            stripLabel = null,
            cells = emptyList(),
            chips = chips,
            headline = headline,
            emphasis = TreeState.Active,
            body = body,
            accentedChips = accented,
            grid = listOf(
                StoryGridBlock(
                    null,
                    a.size,
                    table.mapIndexed { k, row ->
                        StoryGridRow(
                            row.mapIndexed { j, v -> StoryGridCell(j, 1, "$v", state(k, j)) },
                            label = "k$k",
                            sub = "len ${1 shl k}",
                            labelState = if (k == focusRow) TreeState.Active else TreeState.Idle,
                        )
                    },
                    headers = a.indices.map { "$it" },
                    rowHeight = 38,
                ),
            ),
        )
        frames += TreeFrame(emptyList(), story.status, story = story)
    }

    val cells = table.sumOf { it.size }
    frame("SPARSE TABLE", "n log n cells", { _, _ -> TreeState.Idle }, null,
        listOf("rows" to "${table.size}", "cells" to "$cells"),
        "Row k stores the min of every {length-2ᵏ block}.",
        "Each row is built from two blocks of the row above, so the whole table costs O(n log n) once.")
    val lo = 0
    val hi = 5
    val k = 2
    frame("RANGE MIN [$lo, $hi]", "k = ⌊log₂ 6⌋ = $k", { r, j -> if (r == 0 && j in lo..hi) TreeState.Path else TreeState.Idle }, k,
        listOf("length" to "6", "k" to "$k"),
        "The range has length 6, so use row {k = $k}.",
        "2² = 4 is the largest block that fits inside the range.")
    val second = hi - (1 shl k) + 1
    val answer = minOf(table[k][lo], table[k][second])
    val at = (lo..hi).first { a[it] == answer }
    frame("RANGE MIN [$lo, $hi]", "k = ⌊log₂ 6⌋ = $k",
        { r, j ->
            when {
                r == 0 && j == at -> TreeState.Answer
                r == 0 && j in lo..hi -> TreeState.Path
                r == k && (j == lo || j == second) -> TreeState.Active
                else -> TreeState.Idle
            }
        },
        k,
        listOf("blocks" to "[$lo..${lo + (1 shl k) - 1}] [$second..$hi]", "min" to "min(${table[k][lo]}, ${table[k][second]}) = $answer"),
        "Two {length-4 blocks} cover [$lo, $hi]. They overlap, which is fine for min.",
        "The answer is the smaller of the two, $answer. Any range takes 2 reads.",
        accented = setOf(1))
    return frames
}

// ── Segment tree: bottom-up sum build, then a range query ────────────────────
private fun segmentTreeFrames(): List<TreeFrame> {
    val data = listOf(2, 1, 5, 3)
    val b = TreeBuilder()
    val root = b.add("${data.sum()}", null, 0, sub = "0..3")
    val left = b.add("${data[0] + data[1]}", root, 0, sub = "0..1")
    val right = b.add("${data[2] + data[3]}", root, 1, sub = "2..3")
    val leaves = data.mapIndexed { i, v -> b.add("$v", if (i < 2) left else right, i % 2, sub = "$i") }
    val query = 1..3

    fun story(
        cells: (Int) -> TreeState,
        chips: List<Pair<String?, String>>,
        headline: String,
        body: String,
        emphasis: TreeState = TreeState.Active,
        accented: Set<Int> = emptySet(),
    ) = TreeStory(
        title = "SUM QUERY [${query.first}..${query.last}]",
        note = "node = range sum",
        stripLabel = "ARRAY",
        cells = data.indices.map { StripCell("$it", "${data[it]}", cells(it)) },
        chips = chips,
        headline = headline,
        emphasis = emphasis,
        body = body,
        accentedChips = accented,
        bracket = query,
    )

    b.frame(
        "",
        story = story(
            cells = { TreeState.Idle },
            chips = listOf("sum" to "0", "visited" to "0 of 7"),
            headline = "Every node already stores the sum of its range.",
            body = "The root covers 0..3 and holds ${data.sum()}, the whole array. The query wants indices 1 to 3.",
        ),
    )
    b.frame(
        "",
        active = setOf(root),
        story = story(
            cells = { TreeState.Active },
            chips = listOf("sum" to "0", "visited" to "1 of 7"),
            headline = "The root {0..3} only partly overlaps the query, so split it.",
            body = "Index 0 is outside [1..3], so the root's ${data.sum()} is too much. Ask both children.",
        ),
    )
    b.frame(
        "",
        active = setOf(left),
        path = setOf(root),
        story = story(
            cells = { if (it <= 1) TreeState.Active else TreeState.Idle },
            chips = listOf("sum" to "0", "visited" to "2 of 7"),
            headline = "{0..1} straddles the edge of the query too, so go down again.",
            body = "Only a node whose range sits wholly inside the query can answer for it.",
        ),
    )
    b.frame(
        "",
        active = setOf(leaves[1]),
        path = setOf(root, left),
        skipped = setOf(leaves[0]),
        story = story(
            cells = { if (it == 0) TreeState.Skipped else if (it == 1) TreeState.Active else TreeState.Idle },
            chips = listOf("sum" to "${data[1]}", "visited" to "3 of 7"),
            headline = "Leaf 1 is inside the query, so count its value, {${data[1]}}.",
            body = "Leaf 0 is outside, so it is skipped without a visit. Next, the root's right child.",
        ),
    )
    b.frame(
        "",
        active = setOf(right),
        path = setOf(root, left),
        marked = setOf(leaves[1]),
        skipped = setOf(leaves[0], leaves[2], leaves[3]),
        story = story(
            cells = { if (it == 0) TreeState.Skipped else if (it == 1) TreeState.Marked else TreeState.Active },
            chips = listOf(
                "sum" to "${data[1]} + ${data[2] + data[3]} = ${data[1] + data[2] + data[3]}",
                "visited" to "4 of 7",
            ),
            headline = "[2..3] sits fully inside the query, so take its sum, {${data[2] + data[3]}}, without going down.",
            body = "Together with leaf 1 that gives ${data[1] + data[2] + data[3]}. Only 4 of 7 nodes were visited.",
            accented = setOf(0),
        ),
    )
    return b.frames
}

// ── B-tree of order 3: inserts until a node splits ───────────────────────────
private fun bTreeFrames(): List<TreeFrame> = bTreeStoryFrames(plus = false)

private class BNode(val keys: MutableList<Int> = mutableListOf(), val kids: MutableList<BNode> = mutableListOf()) {
    val leaf get() = kids.isEmpty()
}

// Order 3 (at most 2 keys a node), ascending inserts. An overfull node is shown for one step, overfull,
// and split at the start of the next — except on the last insert, where it splits in place.
private fun bTreeStoryFrames(plus: Boolean): List<TreeFrame> {
    val maxKeys = 2
    val inserts = if (plus) listOf(10, 20, 30, 40, 50) else listOf(10, 20, 30, 40, 50, 60, 70)
    val headlines = if (plus) {
        listOf(
            "Insert {10}: in a B+ tree every key lives in a leaf." to
                "Inner nodes only hold copies that steer the search. Order 3 still means at most 2 keys per node.",
            "Insert {20}: it fits beside 10." to
                "One leaf, two keys, still within the limit.",
            "Insert {30}: the leaf holds 3 keys, one too many." to
                "The next step splits it. 20 is copied up, not moved: it stays in the right leaf as well.",
            "Insert {40}: 20 was copied up, and now 40 overfills the right leaf." to
                "Ascending keys always land in the rightmost leaf, so it keeps filling. Next, 30 is copied up.",
            "Insert {50}: the leaf splits, then the root splits too." to
                "40 is copied up and the root reaches 3 keys, so 30 moves up to a new root. Every key still sits " +
                "in a leaf, and the leaves are chained left to right for range scans.",
        )
    } else {
        listOf(
            "Insert {10}: the tree is one node with room for two keys." to
                "A B-tree of order 3 keeps at most 2 keys per node, in sorted order.",
            "Insert {20}: it fits beside 10, keys kept in order." to
                "Nothing moves. A node only reorganises once it holds too many keys.",
            "Insert {30}: the node now holds 3 keys, one too many." to
                "The next step splits it. 20 moves up into a new root, and 10 and 30 become separate leaves.",
            "Insert {40}: 20 went up, and 40 joins 30 in the right leaf." to
                "The split grew the tree upward by one level. Every leaf is still at the same depth.",
            "Insert {50}: the right leaf now holds 3 keys, one too many." to
                "The next step splits it. 40 moves up into the root, and 30 and 50 become separate leaves.",
            "Insert {60}: 40 went up into the root, and 60 joins 50." to
                "The root now holds 2 keys and 3 children. It is full, but not over.",
            "Insert {70}: two splits in a row, and the tree grows a level." to
                "60 moves up and overfills the root, so the root splits too and 40 becomes the new root. A B-tree " +
                "only ever gets taller at the top.",
        )
    }

    var root = BNode()
    fun parentOf(target: BNode, cur: BNode = root): BNode? {
        for (k in cur.kids) {
            if (k === target) return cur
            parentOf(target, k)?.let { return it }
        }
        return null
    }
    // A B+ leaf copies its middle key up and keeps it; everything else moves the median up.
    fun split(node: BNode) {
        val p = parentOf(node)
        val up = node.keys[1]
        val left = BNode(mutableListOf(node.keys[0]), node.kids.take(2).toMutableList())
        val right = if (plus && node.leaf) {
            BNode(node.keys.subList(1, 3).toMutableList())
        } else {
            BNode(mutableListOf(node.keys[2]), node.kids.drop(2).toMutableList())
        }
        if (p == null) {
            root = BNode(mutableListOf(up), mutableListOf(left, right))
        } else {
            val i = p.kids.indexOfFirst { it === node }
            p.kids[i] = left
            p.kids.add(i + 1, right)
            p.keys.add(i, up)
        }
    }
    fun resolve(node: BNode) {
        var cur: BNode? = node
        while (cur != null && cur.keys.size > maxKeys) {
            val p = parentOf(cur)
            split(cur)
            cur = p
        }
    }
    fun childIndex(n: BNode, key: Int) = if (plus) n.keys.count { it <= key } else n.keys.count { it < key }
    // The nodes a search for [key] passes through, ending at the node that holds it.
    fun descend(key: Int): Pair<List<BNode>, BNode> {
        val path = mutableListOf<BNode>()
        var n = root
        while (!n.leaf && !(!plus && key in n.keys)) {
            path += n
            n = n.kids[childIndex(n, key)]
        }
        return path to n
    }
    fun height(n: BNode = root): Int = if (n.leaf) 1 else 1 + height(n.kids[0])

    val frames = mutableListOf<TreeFrame>()
    var pending: BNode? = null
    inserts.forEachIndexed { step, key ->
        pending?.let { resolve(it) }
        pending = null
        var (path, target) = descend(key)
        target.keys.add(key)
        target.keys.sort()
        var over = target.keys.size > maxKeys
        if (over && step == inserts.lastIndex) {
            resolve(target)
            over = false
            descend(key).let { path = it.first; target = it.second }
        } else if (over) {
            pending = target
        }

        val nodes = mutableListOf<TreeNodeSpec>()
        val ids = HashMap<BNode, Int>()
        fun emit(n: BNode, parent: Int?, order: Int) {
            val id = nodes.size
            ids[n] = id
            val onPath = path.any { it === n }
            val isTarget = n === target
            nodes += TreeNodeSpec(
                id = id,
                label = "",
                parent = parent,
                order = order,
                state = if (onPath) TreeState.Path else if (isTarget) TreeState.Active else TreeState.Idle,
                keys = n.keys.map { k ->
                    KeyCell(
                        "$k",
                        when {
                            onPath -> TreeState.Path
                            isTarget && k == key -> if (over) TreeState.Warn else TreeState.Active
                            else -> TreeState.Idle
                        },
                    )
                },
                capacity = maxKeys,
                overflow = if (isTarget && over) "${n.keys.size} keys · max $maxKeys" else null,
            )
            n.kids.forEachIndexed { i, k -> emit(k, id, i) }
        }
        emit(root, null, 0)

        val (headline, body) = headlines[step]
        val chips = mutableListOf<Pair<String?, String>>("max keys" to "$maxKeys", "height" to "${height()}")
        if (over) chips += "leaf" to "overflow"
        val story = TreeStory(
            title = if (plus) "B+ TREE" else "B-TREE",
            note = "order 3",
            stripLabel = "INSERT ORDER",
            cells = inserts.mapIndexed { i, k ->
                StripCell(
                    "",
                    "$k",
                    when {
                        i < step -> TreeState.Skipped
                        i == step -> TreeState.Active
                        else -> TreeState.Idle
                    },
                )
            },
            chips = chips,
            headline = headline,
            emphasis = TreeState.Active,
            body = body,
            warnedChips = if (over) setOf(2) else emptySet(),
            edgeStates = (path.drop(1) + target).filter { it !== root }.mapNotNull { ids[it] }.associateWith { TreeState.Path },
            leafChain = plus,
        )
        frames += TreeFrame(nodes, story.status, story = story)
    }
    return frames
}

// ── Priority queue ADT: the contract, and why a heap and not a sorted list ───
private fun priorityQueueFrames(): List<TreeFrame> {
    val capacity = 7
    val heap = mutableListOf<Int>()
    val frames = mutableListOf<TreeFrame>()

    // Inserts one value with sift-up; returns the slot it came to rest in and the slots it passed through.
    fun insert(value: Int): Pair<Int, List<Int>> {
        heap.add(value)
        var i = heap.lastIndex
        val trail = mutableListOf(i)
        while (i > 0 && heap[(i - 1) / 2] > heap[i]) {
            val parent = (i - 1) / 2
            heap[parent] = heap[i].also { heap[i] = heap[parent] }
            i = parent
            trail += i
        }
        return i to trail
    }

    fun frame(
        rest: Int,
        edges: Set<Int>,
        chips: List<Pair<String?, String>>,
        headline: String,
        body: String,
        emphasis: TreeState,
        positive: Set<Int> = emptySet(),
    ) {
        fun stateOf(i: Int) = when (i) {
            0 -> TreeState.Marked
            rest -> TreeState.Active
            else -> TreeState.Idle
        }
        val nodes = heap.indices.map { i ->
            TreeNodeSpec(
                id = i,
                label = "${heap[i]}",
                parent = if (i == 0) null else (i - 1) / 2,
                order = if (i % 2 == 1) 0 else 1,
                state = stateOf(i),
                sub = "i$i",
            )
        }
        val story = TreeStory(
            title = "MIN-HEAP",
            note = "parent ≤ child",
            stripLabel = "STORED AS AN ARRAY",
            cells = (0 until capacity).map { i ->
                if (i < heap.size) StripCell("$i", "${heap[i]}", stateOf(i)) else StripCell("$i", "", TreeState.Skipped)
            },
            chips = chips,
            headline = headline,
            emphasis = emphasis,
            body = body,
            positiveChips = positive,
            edgeStates = edges.associateWith { TreeState.Active },
            footnote = "children of i → 2i+1, 2i+2",
        )
        frames += TreeFrame(nodes, story.status, story = story)
    }

    // Every edge a value crossed on its way up, named by the lower slot (a child's edge to its parent).
    fun climbed(trail: List<Int>) = trail.dropLast(1).toSet()

    var (rest, trail) = insert(7)
    frame(rest, emptySet(), listOf("size" to "1", "peek" to "7"),
        "Insert 7: it lands in slot 0 and is the {minimum} so far.",
        "A heap is a complete tree kept in an array. The root, slot 0, always holds the smallest value.",
        TreeState.Marked)

    insert(4).let { rest = it.first; trail = it.second }
    frame(rest, climbed(trail), listOf("size" to "2", "peek" to "4", "4 < 7" to "swap"),
        "Insert 4: it is smaller than its parent 7, so they {swap}.",
        "Sift-up compares a value with the parent at (i − 1) / 2 and swaps while the parent is bigger. 4 is the new root.",
        TreeState.Active)

    insert(9).let { rest = it.first; trail = it.second }
    frame(rest, setOf(rest), listOf("size" to "3", "peek" to "4", "9 ≥ 4" to "ok"),
        "Insert 9: it lands in slot 2 and stays, since {9 ≥ 4}.",
        "No swap needed. Most inserts stop after one or two comparisons.",
        TreeState.Active, positive = setOf(2))

    insert(2).let { rest = it.first; trail = it.second }
    frame(rest, climbed(trail), listOf("size" to "4", "peek" to "2", "swaps" to "${trail.size - 1}"),
        "Insert 2: two swaps carry it all the way to the {root}.",
        "2 < 7, then 2 < 4. Sift-up only walks one path, so an insert costs at most log n swaps.",
        TreeState.Marked)

    insert(6).let { rest = it.first; trail = it.second }
    frame(rest, setOf(rest), listOf("size" to "5", "peek" to "${heap[0]}", "6 ≥ 4" to "ok"),
        "Five inserts done. The minimum, {${heap[0]}}, is at the root.",
        "The array isn't sorted: 9 comes before 7. Only one rule holds, that each parent is ≤ its children.",
        TreeState.Marked, positive = setOf(2))
    return frames
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

    fun root(x: Int): Int {
        var cur = x
        while (parent[cur] != cur) cur = parent[cur]
        return cur
    }

    fun union(x: Int, y: Int): Int {
        val rx = root(x)
        val ry = root(y)
        val (child, newRoot) = when {
            rank[rx] < rank[ry] -> rx to ry
            rank[ry] < rank[rx] -> ry to rx
            else -> { rank[rx]++; ry to rx }
        }
        parent[child] = newRoot
        b.reparent(nodeId[child], nodeId[newRoot], child)
        return child
    }

    fun story(
        title: String,
        note: String,
        cells: (Int) -> TreeState,
        chips: List<Pair<String?, String>>,
        headline: String,
        body: String,
        emphasis: TreeState = TreeState.Marked,
        accented: Set<Int> = emptySet(),
    ) = TreeStory(
        title = title,
        note = note,
        stripLabel = "PARENT[]",
        cells = labels.indices.map { StripCell(labels[it], labels[parent[it]], cells(it)) },
        chips = chips,
        headline = headline,
        emphasis = emphasis,
        body = body,
        accentedChips = accented,
    )

    val roots = { x: Int -> if (parent[x] == x) TreeState.Marked else TreeState.Idle }
    b.frame(
        "",
        marked = nodeId.toSet(),
        story = story(
            title = "MAKE-SET × 8",
            note = "parent[x] = x",
            cells = roots,
            chips = listOf("sets" to "8", "height" to "0"),
            headline = "Eight elements, eight sets. Each one is its own {root}.",
            body = "The forest is the partition. Nothing else is stored, just one parent pointer per element.",
        ),
    )

    // Ordered so both cases appear: equal ranks (which grows the forest) and unequal (which does not).
    val paired = listOf(0 to 1, 2 to 3, 4 to 5, 6 to 7).map { (x, y) -> union(x, y) }
    b.frame(
        "",
        active = paired.map { nodeId[it] }.toSet(),
        marked = labels.indices.filter { parent[it] == it }.map { nodeId[it] }.toSet(),
        story = story(
            title = "UNION × 4",
            note = "by rank",
            cells = { if (it in paired) TreeState.Active else roots(it) },
            chips = listOf("sets" to "4", "height" to "1"),
            headline = "Four unions pair them off. B, D, F and H now hang under a {root}.",
            body = "Each pair tied at rank 0, so either could lead. The root's rank goes up to 1.",
        ),
    )

    val merged = listOf(0 to 2, 0 to 4, 0 to 6).map { (x, y) -> union(x, y) }
    b.frame(
        "",
        active = merged.map { nodeId[it] }.toSet(),
        marked = setOf(nodeId[0]),
        story = story(
            title = "UNION BY RANK",
            note = "shorter under taller",
            cells = { if (it in merged) TreeState.Active else roots(it) },
            chips = listOf("sets" to "1", "height" to "2", "rank A" to "${rank[0]}"),
            headline = "Three more unions join everything under {A}.",
            body = "C ties A at rank 1, so A grows to rank 2. E and G are shorter and add no depth.",
        ),
    )

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
    val walked = walkPath.drop(1)
    val start = labels[deepest]
    val rootName = labels[theRoot]
    b.frame(
        "",
        active = setOf(nodeId[deepest]),
        path = walked.map { nodeId[it] }.toSet(),
        marked = setOf(nodeId[theRoot]),
        links = listOf(TreeLink(nodeId[deepest], nodeId[theRoot])),
        story = story(
            title = "FIND($start)",
            note = "with path compression",
            cells = {
                when (it) {
                    deepest -> TreeState.Active
                    in walked -> TreeState.Path
                    theRoot -> TreeState.Marked
                    else -> TreeState.Idle
                }
            },
            chips = listOf(
                "hops" to "${walkPath.size}",
                "root" to rootName,
                "parent[$start]" to "${labels[parent[deepest]]} → $rootName",
            ),
            headline = "find($start) walks ${walkPath.size} pointers to reach {$rootName}.",
            body = "Everything on that walk is in $rootName's set, so $start can point straight at $rootName. " +
                "The next find($start) takes 1 hop.",
        ),
    )

    walkPath.forEach { node ->
        parent[node] = theRoot
        b.reparent(nodeId[node], nodeId[theRoot], node)
    }
    b.frame(
        "",
        active = setOf(nodeId[deepest]),
        marked = setOf(nodeId[theRoot]),
        story = story(
            title = "FIND($start)",
            note = "after compression",
            cells = { if (it == deepest) TreeState.Active else if (it == theRoot) TreeState.Marked else TreeState.Idle },
            chips = listOf("hops" to "1", "root" to rootName, "parent[$start]" to rootName),
            headline = "$start now points straight at {$rootName}.",
            body = "The set did not change, only the path to its root. Every later find($start) costs one hop.",
        ),
    )

    // Same script, two implementations, hops counted — the claim about near-constant time is measured.
    val unions = listOf(0 to 1, 2 to 3, 4 to 5, 6 to 7, 0 to 2, 0 to 4, 0 to 6)
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
        "",
        marked = setOf(nodeId[theRoot]),
        story = story(
            title = "TOTAL POINTER HOPS",
            note = "same unions, 4 rounds of find",
            cells = roots,
            chips = listOf("naive" to "$naive", "rank" to "$ranked", "rank + compress" to "$both"),
            headline = "Rank plus compression walks {$both} pointers. Naive walks $naive.",
            body = "Same ${unions.size} unions, then 4 rounds of find on all 8 elements. Rank alone walks $ranked. " +
                "The forest flattens itself as you use it.",
            accented = setOf(2),
        ),
    )
    return b.frames
}

// ── Suffix tree: the suffix trie, then the same information with unary chains collapsed ──
private val SuffixText = "aba$"

// The suffixes as the list under the tree shows them after suffix [current] goes in.
private fun suffixRows(current: Int): List<StoryRow> = SuffixText.indices.map { i ->
    val text = SuffixText.substring(i)
    when {
        i < current -> StoryRow("[$i]", text, "added", RowState.Done)
        i == current -> StoryRow("[$i]", text, "inserting", RowState.Current)
        i == current + 1 -> StoryRow("[$i]", text, "next", RowState.Pending)
        else -> StoryRow("[$i]", text, "", RowState.Pending)
    }
}

// Ukkonen-free, by hand: "aba$" is small enough that each insert's effect on the tree is scripted, and
// the rows and legend follow the same four steps.
private fun suffixTreeFrames(): List<TreeFrame> {
    val b = TreeBuilder()
    val root = b.add("•", null, 0)
    val leaf0 = b.add("[0]", root, 0, edge = "aba$")
    val leaf1 = b.add("[1]", root, 1, edge = "ba$")

    fun story(
        current: Int,
        headline: String,
        body: String,
        emphasis: TreeState = TreeState.Active,
        edges: Map<Int, TreeState>,
    ) = TreeStory(
        title = "SUFFIX TREE",
        note = "",
        stripLabel = null,
        cells = emptyList(),
        chips = emptyList(),
        headline = headline,
        emphasis = emphasis,
        body = body,
        edgeStates = edges,
        rows = suffixRows(current),
    )

    // Leaf [1] is drawn ahead of time, ghosted: its edge is where the next suffix will go.
    b.frame(
        "",
        marked = setOf(leaf0),
        skipped = setOf(leaf1),
        story = story(
            0,
            "Suffix 0 \"aba$\" is the whole text, so it hangs off the root as one {edge}.",
            "An edge can carry a whole substring. The leaf stores 0, the index where the suffix starts.",
            edges = mapOf(leaf0 to TreeState.Active),
        ),
    )
    b.frame(
        "",
        marked = setOf(leaf0, leaf1),
        story = story(
            1,
            "Suffix 1 \"ba$\" starts with b, which no edge does, so it gets a {new edge}.",
            "Nothing under the root begins with b. A fresh edge from the root, and leaf [1].",
            edges = mapOf(leaf1 to TreeState.Active),
        ),
    )

    // "a$" shares the first letter of "aba$": split that edge after "a".
    b.remove(leaf0)
    val split = b.add("•", root, 0, edge = "a")
    val leaf0b = b.add("[0]", split, 0, edge = "ba$")
    val leaf2 = b.add("[2]", split, 1, edge = "$")
    val leaf3 = b.add("[3]", root, 2, edge = "$")
    b.frame(
        "",
        active = setOf(split),
        path = setOf(root),
        marked = setOf(leaf0b, leaf1, leaf2),
        skipped = setOf(leaf3),
        story = story(
            2,
            "Suffix 2 \"a$\" matches {a}, then breaks at b.",
            "The edge \"aba$\" splits after \"a\". The new leaf [2] hangs off the split, and a search for \"a\" " +
                "now returns 0 and 2.",
            emphasis = TreeState.Path,
            edges = mapOf(split to TreeState.Path, leaf2 to TreeState.Active),
        ),
    )
    b.frame(
        "",
        marked = setOf(leaf0b, leaf1, leaf2, leaf3),
        story = story(
            3,
            "Suffix 3 \"$\" is only the terminator, so it gets its own {leaf}.",
            "4 suffixes, 4 leaves, each labelled with where it starts. Any substring of \"aba\" is now a walk " +
                "down from the root.",
            edges = mapOf(leaf3 to TreeState.Active),
        ),
    )
    return b.frames
}

// The same four suffixes kept as a sorted list of start indices: no tree, just the order.
private fun suffixArrayFrames(): List<TreeFrame> {
    val n = SuffixText.length
    val lines = listOf(
        "Suffix 0 \"aba$\" starts the {sorted} list." to
            "A suffix array keeps every suffix's start index, ordered by the suffixes themselves.",
        "Suffix 1 \"ba$\" sorts {after} \"aba$\", since b > a." to
            "Only the start index is stored. The text itself is never copied.",
        "Suffix 2 \"a$\" sorts {first}: $ comes before every letter." to
            "a$ and aba$ share the prefix \"a\", exactly where the suffix tree branched.",
        "Suffix 3 \"$\" is the smallest of all, so it takes {rank 0}." to
            "Binary search on the array finds any pattern in O(m log n), with far less memory than the tree.",
    )
    return (0 until n).map { current ->
        val sorted = (0..current).sortedBy { SuffixText.substring(it) }
        val rows = sorted.mapIndexed { rank, i ->
            if (i == current) StoryRow("[$i]", SuffixText.substring(i), "inserting", RowState.Current)
            else StoryRow("[$i]", SuffixText.substring(i), "rank $rank", RowState.Done)
        } + (current + 1 until n).map { i ->
            StoryRow("[$i]", SuffixText.substring(i), if (i == current + 1) "next" else "", RowState.Pending)
        }
        val (headline, body) = lines[current]
        val story = TreeStory(
            title = "SUFFIX ARRAY",
            note = "",
            stripLabel = "SA",
            cells = (0 until n).map { rank ->
                val i = sorted.getOrNull(rank)
                when {
                    i == null -> StripCell("$rank", "", TreeState.Skipped)
                    i == current -> StripCell("$rank", "$i", TreeState.Active)
                    else -> StripCell("$rank", "$i", TreeState.Idle)
                }
            },
            chips = listOf("SA" to "[${sorted.joinToString(", ")}]"),
            headline = headline,
            emphasis = TreeState.Active,
            body = body,
            rows = rows,
            rowColumns = 1,
        )
        TreeFrame(emptyList(), story.status, story = story)
    }
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

    val order = listOf(a, bb, c, d, e, f, g, h)
    val depth = mapOf(a to 0, bb to 1, c to 1, d to 2, e to 2, f to 2, g to 2, h to 3)
    val dist = depth.getValue(h) + depth.getValue(e) - 2 * depth.getValue(bb)

    fun story(
        note: String,
        active: Set<Int> = emptySet(),
        path: Set<Int> = emptySet(),
        marked: Set<Int> = emptySet(),
        chips: List<Pair<String?, String>>,
        headline: String,
        body: String,
        emphasis: TreeState = TreeState.Active,
        next: Set<Int> = emptySet(),
        focusDepth: Int? = null,
        accented: Set<Int> = emptySet(),
    ) = TreeStory(
        title = "LCA(H, E)",
        note = note,
        stripLabel = "DEPTH",
        cells = order.map { id ->
            StripCell(
                b.labelOf(id),
                "${depth.getValue(id)}",
                when (id) {
                    in active -> TreeState.Active
                    in marked -> TreeState.Marked
                    in path -> TreeState.Path
                    else -> TreeState.Idle
                },
            )
        },
        chips = chips,
        headline = headline,
        emphasis = emphasis,
        body = body,
        accentedChips = accented,
        nextEdges = next,
        focusDepth = focusDepth,
    )

    fun step(
        note: String,
        active: Set<Int> = emptySet(),
        path: Set<Int> = emptySet(),
        marked: Set<Int> = emptySet(),
        chips: List<Pair<String?, String>>,
        headline: String,
        body: String,
        emphasis: TreeState = TreeState.Active,
        next: Set<Int> = emptySet(),
        focusDepth: Int? = null,
        accented: Set<Int> = emptySet(),
    ) = b.frame(
        "",
        active = active,
        path = path,
        marked = marked,
        story = story(note, active, path, marked, chips, headline, body, emphasis, next, focusDepth, accented),
    )

    step(
        note = "record every depth",
        chips = listOf("u" to "H", "v" to "E"),
        headline = "One pass from the root gives every node its {depth}.",
        body = "A is at depth 0 and H is deepest at 3. That table is all the climb needs.",
    )
    step(
        note = "climb to equal depth",
        active = setOf(h, e),
        chips = listOf("u" to "H", "v" to "E", "depth" to "3 ≠ 2"),
        headline = "H sits at depth 3 and E at depth 2, so lift {H} first.",
        body = "Only the deeper node climbs, one parent at a time, until both depths match.",
        next = setOf(h),
    )
    step(
        note = "climb to equal depth",
        active = setOf(d, e),
        path = setOf(h),
        chips = listOf("u" to "H → D", "v" to "E", "depth" to "2 = 2"),
        headline = "H climbs to depth 2. Now {D} and {E} are level, but different.",
        body = "Next both climb one step together. The first node where they meet is the LCA.",
        next = setOf(d, e),
        focusDepth = 2,
    )
    step(
        note = "climb together",
        active = setOf(bb),
        path = setOf(d, e, h),
        chips = listOf("u" to "D → B", "v" to "E → B", "depth" to "1 = 1"),
        headline = "Both climb one step and land on the same node, {B}.",
        body = "u and v are now equal, so the climb stops here.",
        focusDepth = 1,
    )
    step(
        note = "answer",
        path = setOf(d, e, h),
        marked = setOf(bb),
        chips = listOf("lca" to "B", "distance" to "$dist edges"),
        headline = "LCA(H, E) = {B}.",
        body = "The path between them is depth[H] + depth[E] − 2·depth[B] = 3 + 2 − 2 = $dist edges. " +
            "With a jump table each climb is O(log n).",
        emphasis = TreeState.Marked,
        focusDepth = 1,
        accented = setOf(0),
    )
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
        cardTitle = "IN-ORDER WALK",
        cardNote = "left, node, right",
    ),
    "tree_dp_pattern" to TreeConfig(
        intro = "Diameter computed post-order, with each node relabelled by the downward path it returns. The number " +
            "it returns and the number it records are deliberately different.",
        markedLabel = "Diameter path",
        build = ::treeDpPatternFrames,
        cardTitle = "TREE DP",
        cardNote = "children first",
    ),
    "trie_prefix_pattern" to TreeConfig(
        intro = "Four words inserted into one prefix tree, then search versus startsWith on the same walk — the ● " +
            "flag is the only thing separating them.",
        markedLabel = "Word end (●)",
        build = ::triePrefixPatternFrames,
        cardTitle = "PREFIX TRIE",
        cardNote = "one node per prefix",
    ),
    "binary_lifting_pattern" to TreeConfig(
        intro = "The jump table built row by row, then spent twice: a 3rd-ancestor query decomposed into 2 + 1, and " +
            "an LCA that lifts both nodes without ever overshooting.",
        markedLabel = "Answer",
        build = ::binaryLiftingPatternFrames,
        cardTitle = "BINARY LIFTING",
        cardNote = "jumps of 2^k",
    ),
    "tree_bfs_pattern" to TreeConfig(
        intro = "Level-order traversal with the queue size frozen per level. Watch the queue hold two levels at once " +
            "mid-sweep — that is exactly what the frozen size protects against.",
        markedLabel = "Visited",
        build = ::treeBfsPatternFrames,
        cardTitle = "LEVEL ORDER",
        cardNote = "queue",
    ),
    "tree_dfs_pattern" to TreeConfig(
        intro = "Root-to-leaf paths summing to 22. The budget is carried down as an argument and the path is popped " +
            "on the way out, so siblings never inherit each other's state.",
        markedLabel = "On a matching path",
        build = ::treeDfsPatternFrames,
        cardTitle = "DEPTH-FIRST",
        cardNote = "stack",
    ),
    "tree_of_thoughts" to TreeConfig(
        intro = "Game of 24 searched for real: the greedy chain that fails, the evaluator's ranking measured " +
            "against what can actually reach 24, and the beam width that fixes it — priced against a better " +
            "evaluator.",
        markedLabel = "Reaches 24",
        build = ::treeOfThoughtsFrames,
        cardTitle = "TREE OF THOUGHTS",
        cardNote = "search over steps",
    ),
    "dependency_parsing" to TreeConfig(
        intro = "One sentence parsed by arc-standard transitions, stack and buffer replayed step by step — then " +
            "UAS against LAS on a wrong parse, and the crossing arc this transition system cannot build.",
        markedLabel = "Attached",
        build = ::dependencyFrames,
        cardTitle = "DEPENDENCY PARSE",
        cardNote = "head → dependent",
    ),
    "constituency_parsing" to TreeConfig(
        intro = "The same sentence as nested phrases: the spans evalb scores, a flattened VP costing recall, and " +
            "head rules converting the tree into the dependency parse next door.",
        markedLabel = "Constituent",
        build = ::constituencyFrames,
        cardTitle = "CONSTITUENCY PARSE",
        cardNote = "phrases nest",
    ),
    "pcfg" to TreeConfig(
        intro = "\"she saw the man with the telescope\" parsed by probabilistic CYK: the chart bottom-up, then " +
            "both attachments of the prepositional phrase scored against each other.",
        markedLabel = "Best parse",
        build = ::pcfgFrames,
        cardTitle = "PCFG PARSE",
        cardNote = "P = product of rules",
    ),
    "fp_growth" to TreeConfig(
        intro = "Ten baskets compressed into a prefix tree in two passes, then mined by walking the header chain for one item back up to the root.",
        markedLabel = "Header chain",
        linkLabel = "Header link",
        build = ::fpGrowthFrames,
        cardTitle = "FP-TREE",
        cardNote = "shared prefixes",
    ),
    "aho_corasick" to TreeConfig(
        intro = "The dictionary {he, she, his, hers} as a trie, then its failure links, then one walk over \"ushers\". " +
            "The dashed arcs are the failure links — they are the whole algorithm, and they are why the text pointer never backs up.",
        markedLabel = "Pattern ends here",
        build = ::ahoCorasickFrames,
        linkLabel = "Failure link",
        cardTitle = "AHO-CORASICK",
        cardNote = "trie + failure links",
    ),
    "hierarchical_divisive" to TreeConfig(
        intro = "The dendrogram built top-down: repeatedly split the least cohesive cluster, with the real diameters driving which one goes next.",
        markedLabel = "Cluster",
        build = ::divisiveFrames,
        cardTitle = "DIVISIVE SPLIT",
        cardNote = "top down",
    ),
    "xgboost" to TreeConfig(
        intro = "One tree built by the real second-order gain formula: G and H sums, the closed-form leaf value, and a split that γ prunes away.",
        markedLabel = "Leaf",
        build = ::xgboostFrames,
        cardTitle = "XGBOOST TREE",
        cardNote = "gain per split",
    ),
    "lightgbm" to TreeConfig(
        intro = "The same six-leaf budget spent level-wise and then leaf-wise, so the difference in shape and in captured gain is directly comparable.",
        markedLabel = "Leaf",
        build = ::lightgbmFrames,
        cardTitle = "LEAF-WISE GROWTH",
        cardNote = "best leaf first",
    ),
    "catboost" to TreeConfig(
        intro = "Oblivious trees: one condition per level, reused across the whole level, and what that buys at inference time.",
        markedLabel = "Leaf",
        build = ::catboostFrames,
        cardTitle = "OBLIVIOUS TREE",
        cardNote = "one test per level",
    ),
    "lca" to TreeConfig(
        intro = "LCA(H, E) on an 8-node tree: lift the deeper node until both are level, then climb them " +
            "together until they meet.",
        markedLabel = "LCA",
        build = ::lcaFrames,
        storyStyle = StoryStyle(
            canvasHeight = 210,
            rowGap = 58,
            legend = listOf(
                LegendKey.Active to "Current pair",
                LegendKey.Path to "Climbed",
                LegendKey.Marked to "LCA",
                LegendKey.Next to "Next climb",
            ),
            depthGuides = true,
        ),
    ),
    "tree_dp" to TreeConfig(
        intro = "Maximum-weight independent set. Labels turn into \"not-taken / taken\" as the post-order pass " +
            "resolves each subtree — children are always finished before their parent is touched.",
        markedLabel = "Chosen set",
        build = ::treeDpFrames,
        cardTitle = "MAX INDEPENDENT SET",
        cardNote = "post-order",
    ),
    "binary_search_tree" to TreeConfig(
        intro = "Inserting 50, 30, 70, 20, 40, 60, 80, then searching for 60. Every operation walks one root-to-leaf path, comparing once per level.",
        markedLabel = "Placed / found",
        build = ::bstFrames,
        cardTitle = "BINARY SEARCH TREE",
        cardNote = "left < node < right",
        storyStyle = StoryStyle(
            canvasHeight = 196,
            rowGap = 78,
            legend = listOf(
                LegendKey.Active to "Comparing",
                LegendKey.Path to "Path taken",
                LegendKey.Marked to "Result",
                LegendKey.Ghost to "Insert here",
            ),
        ),
        variants = listOf(
            StoryVariant("Insert", { bstStoryFrames(0) }),
            StoryVariant("Search", { bstStoryFrames(1) }),
            StoryVariant("Delete", { bstStoryFrames(2) }),
        ),
    ),
    "tree" to TreeConfig(
        intro = "One hierarchy, two traversal orders: depth-first goes as deep as it can before backtracking; breadth-first sweeps level by level.",
        markedLabel = "Visited",
        build = ::treeTraversalFrames,
        cardTitle = "TRAVERSAL",
        cardNote = "DFS vs BFS",
        storyStyle = StoryStyle(
            canvasHeight = 196,
            rowGap = 78,
            legend = listOf(LegendKey.Active to "Visiting", LegendKey.Marked to "Visited"),
        ),
        variants = listOf(
            StoryVariant("Inorder", { traversalFrames(0) }),
            StoryVariant("Preorder", { traversalFrames(1) }),
            StoryVariant("Postorder", { traversalFrames(2) }),
        ),
    ),
    "heap" to TreeConfig(
        intro = "A binary max-heap is a complete tree stored in an array. Each insert drops the value into the next free slot, then sifts it up while it beats its parent.",
        markedLabel = "Settled",
        build = ::heapFrames,
        cardTitle = "MAX-HEAP",
        cardNote = "parent ≥ child",
        storyStyle = StoryStyle(
            canvasHeight = 196,
            rowGap = 72,
            legend = listOf(LegendKey.Active to "Comparing", LegendKey.Marked to "Root"),
        ),
        variants = listOf(
            StoryVariant("Max-heap", { heapStoryFrames(max = true) }),
            StoryVariant("Min-heap", { heapStoryFrames(max = false) }),
        ),
    ),
    "trie" to TreeConfig(
        intro = "Inserting \"cat\", \"car\", \"dog\" — shared prefixes share nodes. Lookup cost depends on the word length, never on how many words are stored.",
        markedLabel = "Word end / found",
        build = ::trieFrames,
        cardTitle = "TRIE",
        cardNote = "shared prefixes",
        storyStyle = StoryStyle(
            canvasHeight = 214,
            rowGap = 58,
            legend = listOf(LegendKey.Active to "Adding", LegendKey.Path to "Shared prefix", LegendKey.Ring to "End of word"),
            noteMono = true,
            wideCells = true,
        ),
    ),
    "avl_red_black_tree" to TreeConfig(
        intro = "Three ascending inserts would make a plain BST degenerate into a list. A self-balancing tree detects the violation and rotates.",
        markedLabel = "Balanced",
        build = ::avlFrames,
        cardTitle = "SELF-BALANCING",
        cardNote = "rotate on imbalance",
        storyStyle = StoryStyle(
            canvasHeight = 214,
            rowGap = 76,
            legend = listOf(LegendKey.Warn to "Imbalanced", LegendKey.Active to "Pivot", LegendKey.Marked to "Balanced"),
        ),
        variants = listOf(
            StoryVariant("AVL", { balanceFrames(redBlack = false) }),
            StoryVariant(
                "Red-Black",
                { balanceFrames(redBlack = true) },
                StoryStyle(
                    canvasHeight = 214,
                    rowGap = 76,
                    legend = listOf(LegendKey.Warn to "Red-red conflict", LegendKey.Active to "Pivot", LegendKey.Marked to "Settled"),
                ),
            ),
        ),
    ),
    "segment_tree" to TreeConfig(
        intro = "A sum query over [2, 1, 5, 3]: every internal node caches its range's sum, so a range query only touches O(log n) nodes.",
        markedLabel = "Contributes to answer",
        build = ::segmentTreeFrames,
        storyStyle = StoryStyle(
            canvasHeight = 196,
            rowGap = 76,
            legend = listOf(
                LegendKey.Active to "Current",
                LegendKey.Path to "Path",
                LegendKey.Marked to "Counted",
                LegendKey.Skipped to "Skipped",
            ),
            noteMono = true,
            wideCells = true,
        ),
    ),
    "fenwick_tree" to TreeConfig(
        intro = "A prefix query and a point update over the same eight values, each walking one bit of the index per step.",
        markedLabel = "Read",
        build = { fenwickStoryFrames(update = false) },
        storyStyle = StoryStyle(
            canvasHeight = 0,
            rowGap = 0,
            legend = listOf(LegendKey.Active to "Reading", LegendKey.Marked to "Read", LegendKey.Ghost to "Next"),
        ),
        variants = listOf(
            StoryVariant("Query", { fenwickStoryFrames(update = false) }),
            StoryVariant(
                "Update",
                { fenwickStoryFrames(update = true) },
                StoryStyle(
                    canvasHeight = 0,
                    rowGap = 0,
                    legend = listOf(LegendKey.Active to "Writing", LegendKey.Marked to "Updated", LegendKey.Ghost to "Next"),
                ),
            ),
        ),
    ),
    "sparse_table" to TreeConfig(
        intro = "Every power-of-two block's minimum, then one range query answered from two overlapping blocks.",
        markedLabel = "Answer",
        build = ::sparseTableFrames,
        storyStyle = StoryStyle(
            canvasHeight = 0,
            rowGap = 0,
            legend = listOf(LegendKey.Active to "Reading", LegendKey.Path to "In range", LegendKey.Answer to "Answer"),
            noteMono = true,
        ),
    ),
    "b_tree" to TreeConfig(
        intro = "An order-3 B-tree: nodes hold multiple keys and split at the median when full, so the tree stays shallow and wide — the shape disk and database indexes want.",
        markedLabel = "Settled",
        build = ::bTreeFrames,
        storyStyle = StoryStyle(
            canvasHeight = 214,
            rowGap = 76,
            legend = listOf(
                LegendKey.Path to "Path",
                LegendKey.Active to "Inserting",
                LegendKey.Overflow to "Overflow",
            ),
            stepNoun = "Insert",
        ),
        variants = listOf(
            StoryVariant("B-Tree", { bTreeStoryFrames(plus = false) }),
            StoryVariant("B+ Tree", { bTreeStoryFrames(plus = true) }),
        ),
    ),
    "priority_queue_adt" to TreeConfig(
        intro = "The contract is insert, peek and remove-highest-priority. A binary heap keeps just enough order " +
            "to serve it — watch how little of the structure each operation has to touch.",
        markedLabel = "Root / minimum",
        build = ::priorityQueueFrames,
        storyStyle = StoryStyle(
            canvasHeight = 196,
            rowGap = 68,
            legend = listOf(LegendKey.Marked to "Root / minimum", LegendKey.Active to "Just inserted"),
            noteMono = true,
            stepNoun = "Insert",
        ),
    ),
    "huffman_coding" to TreeConfig(
        intro = "Merge the two rarest symbols, repeat, and the tree that falls out assigns short codes to common " +
            "symbols. The bit totals at the end are counted from the tree it actually built.",
        markedLabel = "Merged",
        build = ::huffmanFrames,
        cardTitle = "HUFFMAN TREE",
        cardNote = "merge two rarest",
    ),
    "disjoint_set" to TreeConfig(
        intro = "A forest where the only thing a tree means is \"these elements are in one set\". Union by rank " +
            "keeps it shallow, path compression flattens what it walks, and the final frame counts both against " +
            "the naive version.",
        markedLabel = "Root / settled",
        build = ::disjointSetFrames,
        storyStyle = StoryStyle(
            canvasHeight = 214,
            rowGap = 86,
            legend = listOf(
                LegendKey.Active to "Start",
                LegendKey.Path to "Walked",
                LegendKey.Marked to "Root",
                LegendKey.Link to "Compressed link",
            ),
        ),
    ),
    "suffix_tree" to TreeConfig(
        intro = "Every suffix of the text inserted into a trie, then the same information with one-child chains " +
            "collapsed onto single edges — the step that makes the node count linear in the text length.",
        markedLabel = "Leaf / suffix start",
        build = ::suffixTreeFrames,
        storyStyle = StoryStyle(
            canvasHeight = 206,
            rowGap = 84,
            legend = listOf(
                LegendKey.Path to "Matched",
                LegendKey.Active to "Split + new edge",
                LegendKey.Marked to "Leaf = start index",
            ),
            stepNoun = "Suffix",
        ),
        variants = listOf(
            StoryVariant("Suffix tree", ::suffixTreeFrames),
            StoryVariant(
                "Suffix array",
                ::suffixArrayFrames,
                StoryStyle(
                    canvasHeight = 0,
                    rowGap = 0,
                    legend = listOf(LegendKey.Active to "Inserting", LegendKey.Skipped to "Empty slot"),
                    stepNoun = "Suffix",
                ),
            ),
        ),
    ),
)

private fun treeConfigFor(topicId: String): TreeConfig =
    treeConfigs[topicId] ?: treeConfigs.getValue("binary_search_tree")

internal val treeVisualizerTopicIds: Set<String> get() = treeConfigs.keys

internal fun treeVisualizerFrameCount(topicId: String): Int {
    val config = treeConfigFor(topicId)
    val builds = listOf(config.build) + config.variants.map { it.build }
    return builds.sumOf { build ->
        val frames = build()
        frames.forEach { frame ->
            val ids = frame.nodes.map { it.id }.toSet()
            val orphans = frame.nodes.mapNotNull { it.parent }.filter { it !in ids }
            require(orphans.isEmpty()) { "$topicId has nodes whose parent is not in the same frame: $orphans" }
            val danglingLinks = frame.links.flatMap { listOf(it.from, it.to) }.filter { it !in ids }
            require(danglingLinks.isEmpty()) { "$topicId draws links to nodes not in the frame: $danglingLinks" }
            require(frame.status.isNotBlank()) { "$topicId has a frame with no status line" }
        }
        frames.size
    }
}

// Topics without a hand-written storyboard still get the story card: their status line split into a
// headline and a sentence under it, and the default legend words.
private fun defaultStyle(config: TreeConfig) = StoryStyle(
    canvasHeight = 240,
    rowGap = 64,
    legend = listOfNotNull(
        LegendKey.Active to "Current",
        LegendKey.Path to "Path",
        LegendKey.Marked to config.markedLabel,
        config.linkLabel?.let { LegendKey.Link to it },
    ),
    noteMono = true,
)

private fun genericStory(config: TreeConfig, status: String): TreeStory {
    val (head, tail) = LabCaptionText.split(status)
    return TreeStory(
        title = config.cardTitle,
        note = config.cardNote,
        stripLabel = null,
        cells = emptyList(),
        chips = emptyList(),
        headline = head,
        emphasis = TreeState.Active,
        body = tail.orEmpty(),
        markdown = true,
    )
}

@Composable
fun TreeVisualizerSection(topicId: String) {
    val config = remember(topicId) { treeConfigFor(topicId) }
    var tab by remember(config) { mutableIntStateOf(0) }
    val variant = config.variants.getOrNull(tab)
    val frames = remember(config, tab) { (variant?.build ?: config.build)() }
    val style = variant?.style ?: config.storyStyle ?: defaultStyle(config)
    val playback = rememberPlaybackState(key = config to tab, stepCount = frames.size)
    val frame = frames[playback.index.coerceIn(0, frames.lastIndex)]
    val story = frame.story ?: genericStory(config, frame.status)

    TreeStoryLab(
        style = style,
        frame = frame,
        story = story,
        playback = playback,
        frames = frames,
        tabs = config.variants.map { it.label },
        tab = tab,
        onTab = { tab = it },
        linkColor = if (config.storyStyle != null) ActiveColor else LinkColor,
    )
}

// ── Story card UI ────────────────────────────────────────────────────────────

@Composable
private fun storyFill(state: TreeState): Pair<Color, Color> {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    return when (state) {
        TreeState.Active -> ActiveColor to OnActiveColor
        TreeState.Marked -> MarkedColor to Color.White
        TreeState.Path -> PathColor to Color.White
        TreeState.Idle -> muted.copy(alpha = 0.2f) to MaterialTheme.colorScheme.onSurface
        TreeState.Skipped -> muted.copy(alpha = 0.07f) to muted.copy(alpha = 0.5f)
        TreeState.Warn -> SimColors.Red to Color.White
        TreeState.Ghost -> Color.Transparent to ActiveColor
        TreeState.Answer -> SimColors.Answer to Color.White
    }
}

private fun stateColor(state: TreeState): Color? = when (state) {
    TreeState.Active -> ActiveColor
    TreeState.Path -> PathColor
    TreeState.Marked -> MarkedColor
    TreeState.Warn -> SimColors.Red
    TreeState.Answer -> SimColors.Answer
    else -> null
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TreeStoryLab(
    style: StoryStyle,
    frame: TreeFrame,
    story: TreeStory,
    playback: PlaybackState,
    frames: List<TreeFrame>,
    tabs: List<String>,
    tab: Int,
    onTab: (Int) -> Unit,
    linkColor: Color,
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    // The mock's yellow is for a dark card; on white it needs to be darker to stay legible as text.
    val emphasis = when (story.emphasis) {
        TreeState.Active -> if (dark) ActiveColor else Color(0xFFB7791F)
        TreeState.Path -> PathColor
        else -> MarkedColor
    }
    val states = buildSet {
        frame.nodes.forEach { n ->
            add(n.state)
            n.keys?.forEach { add(it.state) }
        }
        story.cells.forEach { if (it.value.isNotEmpty() || it.state != TreeState.Skipped) add(it.state) }
        story.cells.forEach { if (it.value.isEmpty()) add(TreeState.Skipped) }
        addAll(story.edgeStates.values)
        story.rows.forEach { if (it.state == RowState.Current) add(TreeState.Active) }
        story.grid.forEach { block -> block.rows.forEach { row -> row.cells.forEach { add(it.state) } } }
    }
    fun present(key: LegendKey) = when (key) {
        LegendKey.Active -> TreeState.Active in states
        LegendKey.Path -> TreeState.Path in states
        LegendKey.Marked -> TreeState.Marked in states
        LegendKey.Skipped -> TreeState.Skipped in states
        LegendKey.Link -> frame.links.isNotEmpty()
        LegendKey.Next -> story.nextEdges.isNotEmpty()
        LegendKey.Overflow -> frame.nodes.any { it.overflow != null }
        LegendKey.Warn -> TreeState.Warn in states
        LegendKey.Ring -> frame.nodes.any { it.ring }
        LegendKey.Ghost -> TreeState.Ghost in states
        LegendKey.Answer -> TreeState.Answer in states
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    if (tabs.isNotEmpty()) {
                        StoryTabs(tabs, tab, onTab)
                        Spacer(Modifier.weight(1f))
                    } else {
                        Text(
                            story.title,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.8.sp,
                            color = muted,
                            maxLines = 1,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (story.note.isNotEmpty()) {
                        Text(
                            story.note,
                            fontSize = 13.sp,
                            fontFamily = if (style.noteMono) FontFamily.Monospace else null,
                            color = muted,
                            maxLines = 1,
                            modifier = Modifier.padding(start = 12.dp),
                        )
                    }
                }

                if (frame.nodes.isNotEmpty()) {
                    StoryCanvas(style, frame, story, linkColor, Modifier.padding(top = 12.dp))
                }

                story.grid.forEachIndexed { i, block ->
                    StoryGrid(block, Modifier.padding(top = if (i == 0) 12.dp else 16.dp))
                }

                if (story.rows.isNotEmpty()) {
                    story.rowsLabel?.let { StorySectionLabel(it, story.rowsNote) }
                    StoryRows(story, Modifier.padding(top = if (story.rowsLabel == null) 12.dp else 0.dp))
                }

                if (story.cells.isNotEmpty()) {
                    story.stripLabel?.let { StorySectionLabel(it, story.stripNote) }
                    StoryStrip(story, wide = style.wideCells)
                }
                story.footnote?.let {
                    Text(it, fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = muted, modifier = Modifier.padding(top = 10.dp))
                }

                val legend = style.legend.filter { present(it.first) }
                if (legend.isNotEmpty()) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        legend.forEach { (key, styleLabel) ->
                            val label = story.legendLabels[key] ?: styleLabel
                            when (key) {
                                LegendKey.Active -> StoryLegendItem(ActiveColor, label)
                                LegendKey.Path -> StoryLegendItem(PathColor, label)
                                LegendKey.Marked -> StoryLegendItem(MarkedColor, label)
                                LegendKey.Skipped -> StoryLegendItem(muted.copy(alpha = 0.2f), label)
                                LegendKey.Link -> StoryLegendItem(linkColor, label, dash = true)
                                LegendKey.Next -> StoryLegendItem(ActiveColor, label, dash = true)
                                LegendKey.Overflow -> StoryLegendItem(SimColors.Red, label)
                                LegendKey.Warn -> StoryLegendItem(SimColors.Red, label)
                                LegendKey.Ring -> StoryLegendItem(MarkedColor, label, ring = true)
                                LegendKey.Ghost -> StoryLegendItem(ActiveColor, label, dash = true)
                                LegendKey.Answer -> StoryLegendItem(SimColors.Answer, label)
                            }
                        }
                    }
                }
            }
        }

        if (story.chips.isNotEmpty()) {
            ReadoutChips(
                story.chips,
                Modifier.padding(top = 16.dp),
                accented = story.accentedChips,
                warned = story.warnedChips,
                positive = story.positiveChips,
            )
        }

        Text(
            if (story.markdown) {
                inlineMarkdown(story.headline)
            } else {
                buildAnnotatedString {
                    var at = 0
                    HeadlineMark.findAll(story.headline).forEach { m ->
                        append(story.headline.substring(at, m.range.first))
                        val color = when (m.groupValues[1]) {
                            "w" -> SimColors.Red
                            "a" -> if (dark) ActiveColor else Color(0xFFB7791F)
                            "p" -> PathColor
                            "m" -> MarkedColor
                            else -> emphasis
                        }
                        withStyle(SpanStyle(color = color)) { append(m.groupValues[2]) }
                        at = m.range.last + 1
                    }
                    append(story.headline.substring(at))
                }
            },
            fontSize = 20.sp,
            lineHeight = 26.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 16.dp),
        )
        if (story.body.isNotEmpty()) {
            Text(
                if (story.markdown) inlineMarkdown(story.body) else AnnotatedString(story.body),
                fontSize = 15.sp,
                lineHeight = 22.sp,
                color = muted,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        PlaybackTransport(
            playback,
            captions = frames.map { it.status },
            stepLabel = { "${style.stepNoun} ${it + 1} of ${frames.size}" },
        )
    }
}

// The compact segmented control in the card's corner (B-Tree / B+ Tree).
@Composable
private fun StoryTabs(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    Row(
        modifier = Modifier
            .background(SimColors.Tint, RoundedCornerShape(9.dp))
            .padding(2.dp),
    ) {
        labels.forEachIndexed { i, label ->
            val on = i == selected
            Box(
                modifier = Modifier
                    .height(28.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(if (on) (if (dark) Color(0xFF636366) else Color.White) else Color.Transparent)
                    .clickable { onSelect(i) }
                    .padding(horizontal = 14.dp),
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

// A caps label over a block of the card, with an optional note on its right ("3 of 6").
@Composable
private fun StorySectionLabel(label: String, note: String?) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(modifier = Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.sp,
            color = muted,
            modifier = Modifier.weight(1f),
        )
        note?.let { Text(it, fontSize = 13.sp, color = muted, maxLines = 1) }
    }
}

@Composable
private fun StoryLegendItem(color: Color, label: String, dash: Boolean = false, ring: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (ring) {
            Box(modifier = Modifier.size(10.dp).border(1.5.dp, color, RoundedCornerShape(3.dp)))
        } else if (dash) {
            Box(modifier = Modifier.size(width = 12.dp, height = 2.dp).background(color, RoundedCornerShape(1.dp)))
        } else {
            Box(modifier = Modifier.size(10.dp).background(color, RoundedCornerShape(3.dp)))
        }
        Text(
            label,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}

// The list under a suffix tree: one line per suffix, marked added / inserting / next.
@Composable
private fun StoryRows(story: TreeStory, modifier: Modifier = Modifier) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val yellow = if (dark) ActiveColor else Color(0xFFB7791F)
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        story.rows.chunked(story.rowColumns).forEach { line ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                line.forEach { row ->
                    val shape = RoundedCornerShape(10.dp)
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .then(
                                when (row.state) {
                                    RowState.Done -> Modifier.background(muted.copy(alpha = 0.10f), shape)
                                    RowState.Current -> Modifier
                                        .background(ActiveColor.copy(alpha = if (story.rowBorder) 0.10f else 0.16f), shape)
                                        .then(if (story.rowBorder) Modifier.border(1.5.dp, ActiveColor, shape) else Modifier)
                                    RowState.Pending -> Modifier.border(1.dp, muted.copy(alpha = 0.18f), shape)
                                },
                            )
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val ink = when (row.state) {
                            RowState.Done -> MaterialTheme.colorScheme.onSurface
                            RowState.Current -> yellow
                            RowState.Pending -> muted.copy(alpha = 0.5f)
                        }
                        if (row.index.isNotEmpty()) {
                            Text(row.index, fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = if (row.state == RowState.Current) yellow else muted)
                        }
                        Text(
                            row.text,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 14.sp,
                            fontWeight = if (story.plainStatus) FontWeight.Medium else FontWeight.Bold,
                            color = ink,
                            maxLines = 1,
                            modifier = Modifier.padding(start = if (row.index.isEmpty()) 0.dp else 12.dp).weight(1f),
                        )
                        Text(
                            row.status,
                            fontSize = if (story.plainStatus) 14.sp else 12.sp,
                            fontFamily = if (story.plainStatus) FontFamily.Monospace else null,
                            fontWeight = FontWeight.SemiBold,
                            color = when (row.state) {
                                RowState.Done -> if (story.plainStatus) MaterialTheme.colorScheme.onSurface else MarkedColor
                                RowState.Current -> yellow
                                RowState.Pending -> muted.copy(alpha = 0.6f)
                            },
                            maxLines = 1,
                        )
                    }
                }
                repeat(story.rowColumns - line.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

// The array the tree stands for, one cell per element: a letter or index above, the value in a tile
// coloured like its node. A blank value is an empty slot; a query range gets a bracket underneath.
@Composable
private fun StoryStrip(story: TreeStory, wide: Boolean) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val gap = if (wide) 8.dp else 6.dp
    Column(modifier = Modifier.fillMaxWidth()) {
        if (story.cells.any { it.header.isNotEmpty() }) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(gap),
            ) {
                story.cells.forEach { cell ->
                    Text(
                        cell.header,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = muted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
            story.cells.forEach { cell ->
                val (fill, ink) = storyFill(cell.state)
                val shape = RoundedCornerShape(if (wide) 10.dp else 8.dp)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(if (wide) 46.dp else 40.dp)
                        .background(fill, shape)
                        .then(
                            when {
                                cell.value.isEmpty() && story.dashedEmpty -> Modifier.drawBehind {
                                    drawRoundRect(
                                        muted.copy(alpha = 0.35f),
                                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(8.dp.toPx()),
                                        style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))),
                                    )
                                }
                                cell.state == TreeState.Skipped -> Modifier.border(1.dp, muted.copy(alpha = 0.12f), shape)
                                else -> Modifier
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(cell.value, fontFamily = FontFamily.Monospace, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = ink)
                }
            }
        }
        story.bracket?.let { range ->
            val count = story.cells.size
            Canvas(modifier = Modifier.fillMaxWidth().height(14.dp)) {
                val g = gap.toPx()
                val cell = (size.width - g * (count - 1)) / count
                val left = range.first * (cell + g) + cell * 0.1f
                val right = range.last * (cell + g) + cell * 0.9f
                val y = size.height - 2.dp.toPx()
                val top = 5.dp.toPx()
                val stroke = 1.5.dp.toPx()
                val color = muted.copy(alpha = 0.6f)
                drawLine(color, Offset(left, top), Offset(left, y), stroke, StrokeCap.Round)
                drawLine(color, Offset(left, y), Offset(right, y), stroke, StrokeCap.Round)
                drawLine(color, Offset(right, y), Offset(right, top), stroke, StrokeCap.Round)
            }
        }
    }
}

// Rows of cells on a shared column grid: a sparse table's k-rows, or Fenwick bars that each span the
// range they cover. A row's label and sub sit in a gutter on the left.
@Composable
private fun StoryGrid(block: StoryGridBlock, modifier: Modifier = Modifier) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val gutter = if (block.rows.any { it.label != null }) 44.dp else 0.dp
    val gap = 6.dp
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val yellow = if (dark) ActiveColor else Color(0xFFB7791F)
    Column(modifier = modifier.fillMaxWidth()) {
        block.title?.let {
            Text(
                it,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.sp,
                color = muted,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        block.headers?.let { headers ->
            Row(modifier = Modifier.fillMaxWidth().padding(start = gutter, bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(gap)) {
                headers.forEach { h ->
                    Text(h, fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = muted, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            block.rows.forEach { row ->
                Row(modifier = Modifier.fillMaxWidth().height(block.rowHeight.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (gutter > 0.dp) {
                        Column(modifier = Modifier.width(gutter)) {
                            val tint = if (row.labelState == TreeState.Active) yellow else muted
                            row.label?.let { Text(it, fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 14.sp, fontWeight = FontWeight.SemiBold, color = tint) }
                            row.sub?.let { Text(it, fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 13.sp, color = tint.copy(alpha = 0.8f)) }
                        }
                    }
                    BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        val cell = (maxWidth - gap * (block.columns - 1)) / block.columns
                        row.cells.forEach { c ->
                            val (fill, ink) = storyFill(c.state)
                            val shape = RoundedCornerShape(8.dp)
                            Box(
                                modifier = Modifier
                                    .offset(x = (cell + gap) * c.start)
                                    .width(cell * c.span + gap * (c.span - 1))
                                    .fillMaxHeight()
                                    .background(fill, shape)
                                    .then(
                                        if (c.state == TreeState.Ghost) {
                                            Modifier.drawBehind {
                                                drawRoundRect(
                                                    ActiveColor,
                                                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(8.dp.toPx()),
                                                    style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))),
                                                )
                                            }
                                        } else {
                                            Modifier
                                        },
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    c.text,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (c.state == TreeState.Ghost) yellow else ink,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StoryCanvas(style: StoryStyle, frame: TreeFrame, story: TreeStory, linkColor: Color, modifier: Modifier = Modifier) {
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    val keyStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    val subStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 9.sp, fontWeight = FontWeight.Medium)
    val edgeStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    val guideStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    val captionStyle = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    val badgeStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val onSurface = MaterialTheme.colorScheme.onSurface
    val surface = MaterialTheme.colorScheme.surface
    val fills = TreeState.entries.associateWith { storyFill(it) }

    val byId = frame.nodes.associateBy { it.id }
    val childrenOf = frame.nodes.filter { it.parent != null }.groupBy { it.parent!! }
        .mapValues { (_, kids) -> kids.sortedBy { it.order } }
    val roots = frame.nodes.filter { it.parent == null }.sortedBy { it.order }
    val depthById = HashMap<Int, Int>()
    fun depthWalk(id: Int, depth: Int) {
        depthById[id] = depth
        childrenOf[id].orEmpty().forEach { depthWalk(it.id, depth + 1) }
    }
    roots.forEach { depthWalk(it.id, 0) }
    val maxDepth = depthById.values.maxOrNull() ?: 0
    val hasSub = frame.nodes.any { it.sub != null }
    val hasOverflow = frame.nodes.any { it.overflow != null }

    Canvas(modifier = modifier.fillMaxWidth().height(style.canvasHeight.dp)) {
        val nodeH = (if (hasSub) 40.dp else 34.dp).toPx()
        // Chained B+ leaves need room between them for the arrows.
        val slot = (if (story.leafChain) 30.dp else 34.dp).toPx()
        val keyGap = 4.dp.toPx()
        val outlinePad = 4.dp.toPx()
        val gutter = if (style.depthGuides) 30.dp.toPx() else 0f
        val padX = 6.dp.toPx()
        val available = size.width - gutter - 2 * padX

        fun keySlots(n: TreeNodeSpec) = maxOf(n.capacity, n.keys!!.size)
        fun width(n: TreeNodeSpec): Float = when {
            n.keys != null -> keySlots(n) * slot + (keySlots(n) - 1) * keyGap + if (n.overflow != null) 2 * outlinePad else 0f
            n.label == "•" -> 26.dp.toPx()
            else -> (textMeasurer.measure(n.label, labelStyle).size.width + 18.dp.toPx())
                .coerceAtLeast((if (hasSub) 44.dp else 34.dp).toPx())
        }
        val widthById = frame.nodes.associate { it.id to width(it) }

        // Width-aware leaf slots: each leaf gets its own width plus an equal share of what is left, and
        // parents centre over their children. Equal-width leaves come out evenly spaced.
        fun leavesUnder(id: Int): List<Int> =
            childrenOf[id].orEmpty().let { kids -> if (kids.isEmpty()) listOf(id) else kids.flatMap { leavesUnder(it.id) } }
        val leaves = roots.flatMap { leavesUnder(it.id) }
        val total = leaves.sumOf { widthById.getValue(it).toDouble() }.toFloat()
        val share = if (total <= available) (available - total) / leaves.size else 0f
        val scale = if (total > available) available / total else 1f
        val xById = HashMap<Int, Float>()
        var cursor = gutter + padX
        leaves.forEach { id ->
            val w = widthById.getValue(id) * scale + share
            xById[id] = cursor + w / 2f
            cursor += w
        }
        fun place(id: Int): Float = xById.getOrPut(id) { childrenOf.getValue(id).map { place(it.id) }.average().toFloat() }
        roots.forEach { place(it.id) }

        // In-order layout: each root gets its own region (BEFORE | AFTER), and inside it a node's x is its
        // in-order rank, so a lone right child still sits to the right of its parent. Order 0 is a left
        // child, anything else a right one.
        val arrowGap = 44.dp.toPx()
        val regionW = (available - (roots.size - 1) * arrowGap) / roots.size
        fun regionLeft(i: Int) = gutter + padX + i * (regionW + arrowGap)
        if (story.inorder) {
            roots.forEachIndexed { i, root ->
                val order = mutableListOf<Int>()
                fun visit(id: Int) {
                    val kids = childrenOf[id].orEmpty()
                    kids.filter { it.order == 0 }.forEach { visit(it.id) }
                    order += id
                    kids.filter { it.order != 0 }.forEach { visit(it.id) }
                }
                visit(root.id)
                order.forEachIndexed { rank, id -> xById[id] = regionLeft(i) + (rank + 0.5f) / order.size * regionW }
            }
        }

        val captionSpace = if (hasOverflow) 20.dp.toPx() else 0f
        val panelSpace = if (story.panels.isNotEmpty()) 22.dp.toPx() else 0f
        val rowGap = if (maxDepth == 0) 0f else minOf(style.rowGap.dp.toPx(), (size.height - nodeH - captionSpace - panelSpace - 8.dp.toPx()) / maxDepth)
        // Centre the tree's block vertically, so a shallow frame (eight singletons) is not stuck to the top.
        val top = panelSpace + (size.height - panelSpace - maxDepth * rowGap - captionSpace) / 2f

        story.panels.forEachIndexed { i, title ->
            val layout = textMeasurer.measure(title, TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp))
            drawText(layout, color = muted, topLeft = Offset(regionLeft(i) + regionW / 2f - layout.size.width / 2f, 0f))
            if (i > 0) {
                val y = top + maxDepth * rowGap / 2f
                val x0 = regionLeft(i) - arrowGap + 10.dp.toPx()
                val x1 = regionLeft(i) - 10.dp.toPx()
                val color = muted.copy(alpha = 0.7f)
                drawLine(color, Offset(x0, y), Offset(x1, y), 1.5.dp.toPx(), StrokeCap.Round)
                drawLine(color, Offset(x1, y), Offset(x1 - 6.dp.toPx(), y - 5.dp.toPx()), 1.5.dp.toPx(), StrokeCap.Round)
                drawLine(color, Offset(x1, y), Offset(x1 - 6.dp.toPx(), y + 5.dp.toPx()), 1.5.dp.toPx(), StrokeCap.Round)
            }
        }
        fun py(depth: Int) = top + depth * rowGap
        fun at(id: Int) = Offset(xById.getValue(id), py(depthById.getValue(id)))

        if (style.depthGuides) {
            for (level in 0..maxDepth) {
                val y = py(level)
                val label = textMeasurer.measure("d$level", guideStyle)
                drawText(
                    label,
                    color = if (level == story.focusDepth) ActiveColor else muted.copy(alpha = 0.6f),
                    topLeft = Offset(0f, y - label.size.height / 2f),
                )
                drawLine(
                    muted.copy(alpha = 0.18f),
                    Offset(gutter, y),
                    Offset(size.width, y),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 4.dp.toPx())),
                )
            }
        }

        fun lit(state: TreeState) = state != TreeState.Idle && state != TreeState.Skipped
        // A multi-key parent sends child i down from the gap between its keys i-1 and i.
        fun anchor(parent: TreeNodeSpec, child: TreeNodeSpec): Offset {
            val c = at(parent.id)
            if (parent.keys == null) return c
            val i = childrenOf.getValue(parent.id).indexOfFirst { it.id == child.id }
            val inner = widthById.getValue(parent.id) - if (parent.overflow != null) 2 * outlinePad else 0f
            val left = c.x - inner / 2f
            val x = (left + i * (slot + keyGap) - keyGap / 2f).coerceIn(left + 6.dp.toPx(), left + inner - 6.dp.toPx())
            return Offset(x, c.y + nodeH / 2f)
        }

        frame.nodes.forEach { node ->
            val parent = node.parent?.let { byId[it] } ?: return@forEach
            val next = node.id in story.nextEdges
            val explicit = story.edgeStates[node.id]?.let { stateColor(it) }
            // A story that names its coloured edges leaves every other edge plain.
            val onPath = !story.plainEdges && story.edgeStates.isEmpty() && lit(node.state) && lit(parent.state)
            val color = when {
                next -> ActiveColor
                explicit != null -> explicit
                node.state == TreeState.Skipped -> muted.copy(alpha = 0.22f)
                onPath -> PathColor
                else -> muted.copy(alpha = 0.4f)
            }
            val from = anchor(parent, node)
            val to = if (parent.keys != null) at(node.id) - Offset(0f, nodeH / 2f + if (node.overflow != null) outlinePad else 0f) else at(node.id)
            drawLine(
                color,
                from,
                to,
                strokeWidth = (if (onPath || next || explicit != null) 2.5.dp else 1.5.dp).toPx(),
                cap = StrokeCap.Round,
                pathEffect = if (next) PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx())) else null,
            )
            node.edge?.let { text ->
                val layout = textMeasurer.measure(text, edgeStyle)
                val mid = (from + to) / 2f
                val gapX = 7.dp.toPx()
                // Beside the line: a first child's label on its left, every other one on its right, so two
                // sibling labels never meet in the gap between their edges.
                val first = childrenOf.getValue(parent.id).first().id == node.id
                val x = if (first) mid.x - gapX - layout.size.width else mid.x + gapX
                drawText(
                    layout,
                    color = when {
                        explicit != null -> explicit
                        node.state == TreeState.Skipped -> muted.copy(alpha = 0.5f)
                        else -> onSurface.copy(alpha = 0.85f)
                    },
                    topLeft = Offset(x, mid.y - layout.size.height / 2f),
                )
            }
        }

        // B+ leaves are chained: a short arrow from each leaf to the next one along.
        if (story.leafChain) {
            val chain = leaves.filter { depthById[it] == maxDepth }
            chain.zipWithNext().forEach { (a, b) ->
                val y = at(a).y
                val start = Offset(at(a).x + widthById.getValue(a) * scale / 2f + 3.dp.toPx(), y)
                val end = Offset(at(b).x - widthById.getValue(b) * scale / 2f - 3.dp.toPx(), y)
                if (end.x - start.x < 5.dp.toPx()) return@forEach
                val color = muted.copy(alpha = 0.55f)
                drawLine(color, start, end, 1.5.dp.toPx(), StrokeCap.Round)
                drawLine(color, end, end + Offset(-4.dp.toPx(), -3.dp.toPx()), 1.5.dp.toPx(), StrokeCap.Round)
                drawLine(color, end, end + Offset(-4.dp.toPx(), 3.dp.toPx()), 1.5.dp.toPx(), StrokeCap.Round)
            }
        }

        // Links, with an arrowhead where they land. Union-Find's compressed pointer bows out to the left
        // of the walk it replaces; anything else arcs over the tree edges it would otherwise lie on.
        frame.links.forEach { link ->
            val from = byId[link.from] ?: return@forEach
            val to = byId[link.to] ?: return@forEach
            val a = at(from.id)
            val z = at(to.id)
            val path = Path()
            val c2: Offset
            val end: Offset
            if (linkColor == ActiveColor && depthById.getValue(from.id) - depthById.getValue(to.id) >= 2) {
                val halfW = widthById.getValue(from.id) / 2f
                val start = Offset(a.x - halfW, a.y - nodeH * 0.15f)
                end = Offset(z.x - widthById.getValue(to.id) / 2f - 3.dp.toPx(), z.y + nodeH * 0.1f)
                val bow = 46.dp.toPx()
                val c1 = Offset(start.x - bow, start.y - (start.y - end.y) * 0.3f)
                c2 = Offset(minOf(start.x, end.x) - bow * 0.8f, end.y + (start.y - end.y) * 0.1f)
                path.moveTo(start.x, start.y)
                path.cubicTo(c1.x, c1.y, c2.x, c2.y, end.x, end.y)
            } else {
                c2 = Offset((a.x + z.x) / 2f, minOf(a.y, z.y) - 22.dp.toPx())
                val dir = (z - c2).let { it / it.getDistance() }
                end = z - dir * (nodeH / 2f + 2.dp.toPx())
                path.moveTo(a.x, a.y - nodeH / 2f)
                path.quadraticTo(c2.x, c2.y, end.x, end.y)
            }
            drawPath(
                path,
                color = linkColor,
                style = Stroke(width = 1.8.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.5.dp.toPx()))),
            )
            val dir = (end - c2).let { it / it.getDistance() }
            val normal = Offset(-dir.y, dir.x)
            val head = 7.dp.toPx()
            drawLine(linkColor, end, end - dir * head + normal * (head * 0.55f), 1.8.dp.toPx(), StrokeCap.Round)
            drawLine(linkColor, end, end - dir * head - normal * (head * 0.55f), 1.8.dp.toPx(), StrokeCap.Round)
        }

        val radius = androidx.compose.ui.geometry.CornerRadius(8.dp.toPx())
        frame.nodes.forEach { node ->
            val center = at(node.id)
            val w = widthById.getValue(node.id) * scale
            val keys = node.keys
            if (keys != null) {
                val slots = keySlots(node)
                val inner = slots * slot + (slots - 1) * keyGap
                val left = center.x - inner / 2f
                node.overflow?.let { caption ->
                    drawRoundRect(
                        SimColors.Red,
                        Offset(left - outlinePad, center.y - nodeH / 2f - outlinePad),
                        Size(inner + 2 * outlinePad, nodeH + 2 * outlinePad),
                        androidx.compose.ui.geometry.CornerRadius(11.dp.toPx()),
                        style = Stroke(1.5.dp.toPx()),
                    )
                    val layout = textMeasurer.measure(caption, captionStyle)
                    drawText(
                        layout,
                        color = Color(0xFFF87171),
                        topLeft = Offset(center.x - layout.size.width / 2f, center.y + nodeH / 2f + outlinePad + 4.dp.toPx()),
                    )
                }
                for (i in 0 until slots) {
                    val tl = Offset(left + i * (slot + keyGap), center.y - nodeH / 2f)
                    val cell = keys.getOrNull(i)
                    drawRoundRect(surface, tl, Size(slot, nodeH), radius)
                    if (cell == null) {
                        drawRoundRect(muted.copy(alpha = 0.06f), tl, Size(slot, nodeH), radius)
                        drawRoundRect(muted.copy(alpha = 0.28f), tl, Size(slot, nodeH), radius, style = Stroke(1.dp.toPx()))
                    } else {
                        // An overfull key is tinted, not filled: the red outline already says "overflow".
                        val (fill, ink) = if (cell.state == TreeState.Warn) SimColors.Red.copy(alpha = 0.2f) to Color(0xFFFCA5A5) else fills.getValue(cell.state)
                        drawRoundRect(fill, tl, Size(slot, nodeH), radius)
                        if (cell.state == TreeState.Warn) {
                            drawRoundRect(SimColors.Red.copy(alpha = 0.7f), tl, Size(slot, nodeH), radius, style = Stroke(1.dp.toPx()))
                        }
                        val layout = textMeasurer.measure(cell.value, keyStyle)
                        drawText(layout, color = ink, topLeft = tl + Offset((slot - layout.size.width) / 2f, (nodeH - layout.size.height) / 2f))
                    }
                }
                return@forEach
            }

            val (fill, ink) = fills.getValue(node.state)
            val topLeft = Offset(center.x - w / 2f, center.y - nodeH / 2f)
            val box = if (node.label == "•") Size(w, w).also { } else Size(w, nodeH)
            val boxTopLeft = if (node.label == "•") Offset(center.x - w / 2f, center.y - w / 2f) else topLeft
            // Idle tiles are translucent, so they sit on an opaque base; edges must not show through.
            drawRoundRect(surface, boxTopLeft, box, radius)
            drawRoundRect(fill, boxTopLeft, box, radius)
            if (node.state == TreeState.Skipped) {
                drawRoundRect(muted.copy(alpha = 0.16f), boxTopLeft, box, radius, style = Stroke(1.dp.toPx()))
            }
            if (node.state == TreeState.Ghost) {
                drawRoundRect(
                    ActiveColor, boxTopLeft, box, radius,
                    style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))),
                )
            }
            if (node.ring) {
                drawRoundRect(MarkedColor, boxTopLeft, box, radius, style = Stroke(2.dp.toPx()))
            }
            val label = textMeasurer.measure(node.label, labelStyle)
            val sub = node.sub
            if (sub == null) {
                drawText(label, color = ink, topLeft = Offset(center.x - label.size.width / 2f, center.y - label.size.height / 2f))
            } else {
                val subLayout = textMeasurer.measure(sub, subStyle)
                val stack = subLayout.size.height + label.size.height - 2.dp.toPx()
                val y0 = center.y - stack / 2f
                drawText(subLayout, color = ink.copy(alpha = 0.7f), topLeft = Offset(center.x - subLayout.size.width / 2f, y0))
                drawText(
                    label,
                    color = ink,
                    topLeft = Offset(center.x - label.size.width / 2f, y0 + subLayout.size.height - 2.dp.toPx()),
                )
            }
            // The badge sits on the tile's top-right corner, half outside it.
            node.badge?.let { text ->
                val ring = stateColor(node.badgeTone) ?: muted.copy(alpha = 0.7f)
                val c = Offset(boxTopLeft.x + box.width - 1.dp.toPx(), boxTopLeft.y + 1.dp.toPx())
                val layout = textMeasurer.measure(text, badgeStyle)
                val r = maxOf(9.dp.toPx(), layout.size.width / 2f + 4.dp.toPx())
                drawRoundRect(surface, Offset(c.x - r, c.y - 9.dp.toPx()), Size(2 * r, 18.dp.toPx()), androidx.compose.ui.geometry.CornerRadius(9.dp.toPx()))
                drawRoundRect(ring, Offset(c.x - r, c.y - 9.dp.toPx()), Size(2 * r, 18.dp.toPx()), androidx.compose.ui.geometry.CornerRadius(9.dp.toPx()), style = Stroke(1.5.dp.toPx()))
                drawText(
                    layout,
                    color = if (story.badgeInk) ring else onSurface,
                    topLeft = Offset(c.x - layout.size.width / 2f, c.y - layout.size.height / 2f),
                )
            }
        }
    }
}
