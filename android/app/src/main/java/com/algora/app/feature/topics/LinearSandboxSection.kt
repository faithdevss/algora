package com.algora.app.feature.topics

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.algora.app.core.ui.theme.IBMPlexMono
import com.algora.app.core.ui.theme.SimColors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Stack, queue, deque and singly linked list as sandboxes (docs mocks): pick an operation, set its
// value and end, see what it touches on the stage before running it with a button named after it
// ("Enqueue →", "Push back →"). The four share one state holder — they differ only in which ends are open
// and how the items are drawn. On the sim-only screen the controls are pinned to the bottom and reset
// sits in the nav bar.

private val initialItems = listOf(10, 20, 30)
private const val STEP_MS = 320L

internal enum class LinearKind(val noun: String) { Stack("stack"), Queue("queue"), Deque("deque"), List("list") }

private enum class LinearOp(val label: String) {
    Push("Push"), Pop("Pop"), Peek("Peek"),
    Enqueue("Enqueue"), Dequeue("Dequeue"),
    Insert("Insert"), Delete("Delete"), Search("Search");

    val adds get() = this == Push || this == Enqueue || this == Insert
    val removes get() = this == Pop || this == Dequeue || this == Delete
}

private enum class End(val label: String) { Front("front"), Back("back") }
private enum class ListPos { Head, Index, Tail }
private enum class LinearMark { Cursor, Write, Gone }

private class LinearSandbox(val kind: LinearKind, private val scope: CoroutineScope) {
    val ops = when (kind) {
        LinearKind.Stack, LinearKind.Deque -> listOf(LinearOp.Push, LinearOp.Pop, LinearOp.Peek)
        LinearKind.Queue -> listOf(LinearOp.Enqueue, LinearOp.Dequeue, LinearOp.Peek)
        LinearKind.List -> listOf(LinearOp.Insert, LinearOp.Delete, LinearOp.Search)
    }
    val items = mutableStateListOf<Int>().apply { addAll(initialItems) }
    var op by mutableStateOf(ops[0])
    var end by mutableStateOf(End.Back)
    var pos by mutableStateOf(ListPos.Index)
    var selected by mutableIntStateOf(1)
    var valueInput by mutableStateOf(if (kind == LinearKind.Deque) "50" else "40")
    var busy by mutableStateOf(false)
    var showingResult by mutableStateOf(false)
    var status by mutableStateOf("")
    // The finished op's cost ("O(1)") and, for a list, the hops it walked.
    var cost by mutableStateOf("")
    var hopsDone by mutableStateOf<Int?>(null)
    var markIndex by mutableStateOf<Int?>(null)
    var markKind by mutableStateOf(LinearMark.Cursor)
    private var job: Job? = null

    val maxItems = if (kind == LinearKind.Stack) 7 else 8
    val previewing get() = !busy && !showingResult
    val value get() = valueInput.ifEmpty { "?" }
    val n get() = items.size
    val full get() = n >= maxItems

    /** The index a list op targets (insert: the slot the new node takes). */
    val target: Int
        get() = when (op) {
            LinearOp.Insert -> when (pos) {
                ListPos.Head -> 0
                ListPos.Tail -> n
                ListPos.Index -> selected.coerceAtMost(n)
            }
            else -> selected.coerceAtMost((n - 1).coerceAtLeast(0))
        }

    /** For stack/queue/deque: the item index the op acts on (the open end in use). */
    val endIndex: Int?
        get() = if (n == 0) null else when (kind) {
            LinearKind.Stack -> n - 1
            LinearKind.Queue -> 0
            LinearKind.Deque -> if (end == End.Front) 0 else n - 1
            LinearKind.List -> null
        }

    /** Why Run can't do this op right now, in words; null when it can. */
    val blockReason: String?
        get() {
            val remover = when (kind) { LinearKind.Queue -> "Dequeue"; LinearKind.List -> "Delete"; else -> "Pop" }
            val adder = when (kind) { LinearKind.Queue -> "Enqueue"; LinearKind.List -> "Insert"; else -> "Push" }
            return when {
                op.adds && full ->
                    "No free slot. This ${kind.noun} holds $maxItems items in the lab, and all $maxItems are used. $remover something first."
                !op.adds && n == 0 ->
                    "The ${kind.noun} is empty, so there is nothing to ${op.label.lowercase()}. $adder something first."
                (op.adds || op == LinearOp.Search) && valueInput.toIntOrNull() == null ->
                    "Type a number to ${op.label.lowercase()} first."
                else -> null
            }
        }

    val canRun get() = !busy && blockReason == null

    /** Any edit drops the last result so the preview shows again. */
    fun edited() {
        if (busy) return
        showingResult = false
        cost = ""
        hopsDone = null
    }

    fun canSelect(i: Int) = kind == LinearKind.List && !busy && (if (op == LinearOp.Insert) i <= n else i < n)

    fun select(i: Int) {
        if (!canSelect(i)) return
        selected = i
        if (op == LinearOp.Insert) pos = ListPos.Index
        edited()
    }

    // ── Preview ──

    private fun show(i: Int?) = i?.let { "${items[it]}" } ?: "–"

    private val hops get() = when (op) {
        LinearOp.Insert -> target
        LinearOp.Delete -> if (n == 0) 0 else target
        else -> n
    }

    /** size, the end in use (with where it moves), and the cost; a list shows hops instead of an end. */
    val chipList: List<StoryChip>
        get() = buildList {
            val live = previewing && blockReason == null
            val size = when {
                live && op.adds -> "$n → ${n + 1}"
                live && op.removes -> "$n → ${n - 1}"
                else -> "$n"
            }
            add(StoryChip(if (kind == LinearKind.List) "length" else "size", size))
            fun moved(key: String, now: Int?, next: String) =
                add(StoryChip(key, if (live && op != LinearOp.Peek) "${show(now)} → $next" else show(now)))
            when (kind) {
                LinearKind.Stack -> moved("top", if (n > 0) n - 1 else null, if (op.adds) value else show(if (n > 1) n - 2 else null))
                LinearKind.Queue ->
                    if (op.adds && n > 0) add(StoryChip("front", show(0)))
                    else moved("front", if (n > 0) 0 else null, if (op.adds) value else show(if (n > 1) 1 else null))
                LinearKind.Deque -> {
                    val at = if (end == End.Front) 0 else n - 1
                    val inner = if (end == End.Front) 1 else n - 2
                    moved(end.label, if (n > 0) at else null, if (op.adds) value else show(if (n > 1) inner else null))
                }
                LinearKind.List -> if (live) add(StoryChip("hops", "$hops")) else hopsDone?.let { add(StoryChip("hops", "$it")) }
            }
            if (live) {
                add(
                    StoryChip(
                        "cost",
                        when {
                            kind != LinearKind.List -> "O(1)"
                            op == LinearOp.Search -> "O(n)"
                            hops == 0 -> "O(1)"
                            else -> "O(i)"
                        },
                    ),
                )
            } else if (cost.isNotEmpty()) {
                add(StoryChip("cost", cost))
            }
        }

    // The narration split for the story layout: its first sentence as the headline, the rest under it.
    val headline get() = lead(caption).first
    val body get() = lead(caption).second

    private fun lead(text: String): Pair<String, String> {
        val dot = text.indexOf(". ")
        return if (dot < 0) text to "" else text.substring(0, dot + 1) to text.substring(dot + 2)
    }

    /** What the button says it will do. */
    val actionTitle get() = if (kind == LinearKind.Deque) "${op.label} ${end.label}" else op.label

    fun stepValue(delta: Int) {
        valueInput = "${(valueInput.toIntOrNull() ?: 0) + delta}"
        edited()
    }

    fun stepIndex(delta: Int) {
        if (busy) return
        selected = (target + delta).coerceIn(0, (n - 1).coerceAtLeast(0))
        edited()
    }

    val caption get() = if (previewing) preview else status

    private val preview: String
        get() {
            val v = value
            blockReason?.let { return it }
            return when (kind) {
                LinearKind.Stack -> when (op) {
                    LinearOp.Push ->
                        if (n == 0) "Push $v onto the empty stack. It becomes the top. Only the top pointer moves."
                        else "Push $v lands on top of ${items[n - 1]}. It becomes the new top. Only the top pointer moves. Nothing below is touched."
                    LinearOp.Pop -> "Pop removes ${items[n - 1]} from the top. " +
                        (if (n == 1) "The stack becomes empty." else "${items[n - 2]} becomes the new top.") +
                        " Last in, first out: only the top is reachable."
                    else -> "Peek reads ${items[n - 1]} without removing it. The stack is unchanged, and it costs O(1)."
                }
                LinearKind.Queue -> when (op) {
                    LinearOp.Enqueue ->
                        if (n == 0) "Enqueue $v into the empty queue. It is both front and back."
                        else "Enqueue $v joins behind ${items[n - 1]}. ${items[0]} is still next out. A queue only ever touches its two ends."
                    LinearOp.Dequeue -> "Dequeue removes ${items[0]} from the front. " +
                        (if (n == 1) "The queue becomes empty." else "${items[1]} is next in line.") + " Nothing else moves."
                    else -> "Peek reads ${items[0]} at the front without removing it. Nothing moves, O(1)."
                }
                LinearKind.Deque -> when (op) {
                    LinearOp.Push -> {
                        if (n == 0) return "Push $v into the empty deque. It is both front and back."
                        val old = if (end == End.Front) items[0] else items[n - 1]
                        "Push $v at the ${end.label}. The ${end.label} moves from $old to $v and nothing in the middle shifts. " +
                            "Unlike a queue, both ends take pushes and pops."
                    }
                    LinearOp.Pop -> {
                        val gone = if (end == End.Front) items[0] else items[n - 1]
                        val next = if (n == 1) null else if (end == End.Front) items[1] else items[n - 2]
                        "Pop removes $gone from the ${end.label}. " +
                            (next?.let { "$it becomes the new ${end.label}." } ?: "The deque becomes empty.") +
                            " Either end is open, so both are O(1)."
                    }
                    else -> "Peek reads ${items[endIndex!!]} at the ${end.label} without removing it. Nothing moves, O(1)."
                }
                LinearKind.List -> {
                    val t = target
                    when (op) {
                        LinearOp.Insert -> {
                            if (t == 0) {
                                "Insert $v at the head. Link $v → ${show(if (n > 0) 0 else null)} and move head to it. No walk needed, so it is O(1)."
                            } else {
                                val after = if (t < n) "${items[t]}" else "null"
                                "Walk $t hop${if (t == 1) "" else "s"} to ${items[t - 1]}, then link ${items[t - 1]} → $v → $after. " +
                                    "Two pointer writes. The walk to the spot is what costs O(i)."
                            }
                        }
                        LinearOp.Delete -> {
                            if (t == 0) {
                                "Delete the head ${items[0]}. Head moves to ${show(if (n > 1) 1 else null)}. No walk needed, so it is O(1)."
                            } else {
                                val after = if (t + 1 < n) "${items[t + 1]}" else "null"
                                "Walk $t hop${if (t == 1) "" else "s"} to ${items[t - 1]}, then link ${items[t - 1]} → $after. " +
                                    "The removed node is simply unlinked. The walk is what costs O(i)."
                            }
                        }
                        else -> "Search walks from head, comparing each node to $v. A list has no index math, so a miss costs O(n)."
                    }
                }
            }
        }

    // ── Operations ──

    private suspend fun tick() = delay(STEP_MS)

    private fun mark(i: Int?, kind: LinearMark = LinearMark.Cursor) {
        markIndex = i
        markKind = kind
    }

    private fun fail(message: String) {
        status = message
        cost = ""
        hopsDone = null
        showingResult = true
    }

    private fun run(block: suspend () -> Unit) {
        if (busy) return
        busy = true
        job = scope.launch {
            try {
                block()
            } finally {
                mark(null)
                showingResult = true
                busy = false
            }
        }
    }

    fun runOp() {
        blockReason?.let { return fail(it) }
        val v = valueInput.toIntOrNull()
        if (kind == LinearKind.List) run { runList(v ?: 0) } else run { runEnds(v ?: 0) }
    }

    /** Stack, queue and deque: every op touches one end only. */
    private suspend fun runEnds(v: Int) {
        val front = when (kind) {
            LinearKind.Queue -> op != LinearOp.Enqueue
            LinearKind.Deque -> end == End.Front
            else -> false
        }
        when (op) {
            LinearOp.Push, LinearOp.Enqueue -> {
                if (front) { items.add(0, v); mark(0, LinearMark.Write) } else { items.add(v); mark(n - 1, LinearMark.Write) }
                status = when (kind) {
                    LinearKind.Stack -> "Pushed $v. It is the new top, and nothing below moved."
                    LinearKind.Queue -> "Enqueued $v at the back. It leaves after everything already waiting."
                    else -> "Pushed $v at the ${end.label}. Nothing in the middle moved."
                }
                cost = "O(1)"
                tick(); tick()
            }
            LinearOp.Pop, LinearOp.Dequeue -> {
                val i = if (front) 0 else n - 1
                val gone = items[i]
                mark(i, LinearMark.Gone)
                tick(); tick()
                items.removeAt(i)
                mark(null)
                status = when (kind) {
                    LinearKind.Stack -> "Popped $gone. " + if (n > 0) "${items[n - 1]} is the top again." else "The stack is empty."
                    LinearKind.Queue -> "Dequeued $gone from the front. " + if (n > 0) "${items[0]} is next." else "The queue is empty."
                    else -> "Popped $gone from the ${end.label}. Only that end changed."
                }
                cost = "O(1)"
            }
            else -> {
                val i = endIndex ?: 0
                mark(i)
                val which = when (kind) {
                    LinearKind.Stack -> "Top"
                    LinearKind.Queue -> "Front"
                    else -> end.label.replaceFirstChar { it.uppercase() }
                }
                status = "$which is ${items[i]}. Peek left the ${kind.noun} unchanged."
                cost = "O(1)"
                tick(); tick(); tick()
            }
        }
    }

    /** Singly linked list: reaching position i means walking i nodes from head. */
    private suspend fun runList(v: Int) {
        val t = target
        when (op) {
            LinearOp.Insert -> {
                for (h in 0 until t) {
                    mark(h)
                    status = "Walking: hop ${h + 1} of $t, at ${items[h]}."
                    tick()
                }
                items.add(t, v)
                mark(t, LinearMark.Write)
                status = if (t == 0) "Inserted $v at the head. One link and the head pointer, no walk."
                else "Inserted $v after ${items[t - 1]} in $t hop${if (t == 1) "" else "s"}. Two pointer writes did the rest."
                cost = if (t == 0) "O(1)" else "O(i)"
                hopsDone = t
                tick(); tick()
            }
            LinearOp.Delete -> {
                for (h in 0 until t) {
                    mark(h)
                    status = "Walking: hop ${h + 1} of $t, at ${items[h]}."
                    tick()
                }
                val gone = items[t]
                mark(t, LinearMark.Gone)
                tick(); tick()
                items.removeAt(t)
                mark(null)
                status = if (t == 0) "Deleted the head $gone. The head pointer moved on, no walk."
                else "Deleted $gone. ${items[t - 1]} now links past it in one pointer write."
                cost = if (t == 0) "O(1)" else "O(i)"
                hopsDone = t
            }
            else -> {
                for (i in items.indices) {
                    mark(i)
                    status = "Comparing node $i, ${items[i]}, with $v."
                    tick()
                    if (items[i] == v) {
                        mark(i, LinearMark.Write)
                        status = "Found $v at node $i after ${i + 1} comparison${if (i == 0) "" else "s"}. A list can only be searched by walking."
                        cost = "O(n)"
                        hopsDone = i
                        tick(); tick()
                        return
                    }
                }
                status = "$v is not in the list. A miss walks every node."
                cost = "O(n)"
                hopsDone = n
            }
        }
    }

    fun reset() {
        job?.cancel()
        busy = false
        items.clear()
        items.addAll(initialItems)
        mark(null)
        op = ops[0]
        end = End.Back
        pos = ListPos.Index
        selected = 1
        showingResult = false
        status = ""
        cost = ""
        hopsDone = null
    }
}

// ── Entry points ─────────────────────────────────────────────────────────────

@Composable
fun StackSimulationSection() = LinearSandboxSection(LinearKind.Stack)

@Composable
fun QueueSimulationSection() = LinearSandboxSection(LinearKind.Queue)

@Composable
fun DequeSimulationSection() = LinearSandboxSection(LinearKind.Deque)

@Composable
fun LinkedListSimulationSection() = LinearSandboxSection(LinearKind.List)

@Composable
internal fun LinearSandboxSection(kind: LinearKind) {
    val scope = rememberCoroutineScope()
    val model = remember(kind) { LinearSandbox(kind, scope) }
    val dock = LocalLabDock.current

    if (dock != null) {
        SideEffect {
            dock.controls = { LinearControls(model) }
            dock.navAction = LabNavAction(Icons.Filled.Refresh, "Reset", model::reset)
        }
        DisposableEffect(dock) {
            onDispose {
                dock.controls = null
                dock.navAction = null
            }
        }
        Column(modifier = Modifier.fillMaxWidth()) {
            LinearCard { LinearStage(model, showsReset = false) }
            LinearNarration(model, Modifier.padding(start = 4.dp, end = 4.dp, top = 14.dp))
        }
    } else {
        LinearCard {
            LinearStage(model, showsReset = true)
            LinearNarration(model, Modifier.padding(top = 14.dp))
            HorizontalDivider(modifier = Modifier.padding(top = 16.dp, bottom = 14.dp), color = MaterialTheme.colorScheme.outline)
            LinearControls(model)
        }
    }
}

@Composable
private fun LinearCard(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(modifier = Modifier.padding(16.dp)) { content() }
    }
}

@Composable
private fun LinearNarration(model: LinearSandbox, modifier: Modifier) {
    Column(modifier) {
        StoryChips(model.chipList)
        LabStoryNarration(model.headline, model.body, Modifier.padding(top = 16.dp).heightIn(min = 96.dp))
    }
}

// ── Stage ────────────────────────────────────────────────────────────────────

private class CellStyle(val fill: Color, val fg: Color, val border: Boolean)

@Composable
private fun cellStyle(model: LinearSandbox, i: Int, isEnd: Boolean): CellStyle {
    val onSurface = MaterialTheme.colorScheme.onSurface
    val dark = Color(0xFF1A1A1A)
    val leaving = if (model.previewing && model.op.removes && model.n > 0) {
        if (model.kind == LinearKind.List) model.target else model.endIndex
    } else {
        null
    }
    // The open end in use is yellow, the rest blue; while an op runs the marked cell is yellow
    // (visiting), green (written) or red (leaving).
    return when {
        i == model.markIndex -> when (model.markKind) {
            LinearMark.Cursor -> CellStyle(SimColors.Active, dark, false)
            LinearMark.Write -> CellStyle(SimColors.Green, Color.White, false)
            LinearMark.Gone -> CellStyle(SimColors.Red, Color.White, false)
        }
        i == leaving -> CellStyle(SimColors.Red.copy(alpha = 0.85f), Color.White, false)
        isEnd -> CellStyle(SimColors.Active, dark, false)
        else -> CellStyle(SimColors.Blue.copy(alpha = 0.28f), onSurface, true)
    }
}

@Composable
private fun ValueBox(text: String, style: CellStyle, width: Dp, height: Dp, modifier: Modifier = Modifier) {
    val fill by animateColorAsState(style.fill, label = "cell")
    Box(
        modifier = modifier
            .size(width, height)
            .background(fill, RoundedCornerShape(10.dp))
            .then(if (style.border) Modifier.border(1.5.dp, SimColors.Blue, RoundedCornerShape(10.dp)) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, fontFamily = IBMPlexMono, fontWeight = FontWeight.Medium, fontSize = 18.sp, color = style.fg)
    }
}

@Composable
private fun IncomingBox(text: String, width: Dp, height: Dp) {
    val accent = MaterialTheme.colorScheme.primary
    Box(
        modifier = Modifier
            .size(width, height)
            .background(accent.copy(alpha = 0.14f), RoundedCornerShape(10.dp))
            .drawBehind {
                drawRoundRect(
                    color = accent,
                    cornerRadius = CornerRadius(10.dp.toPx()),
                    style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 7f))),
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(text, fontFamily = IBMPlexMono, fontWeight = FontWeight.Medium, fontSize = 18.sp, color = accent.copy(alpha = 0.9f))
    }
}

@Composable
private fun Tag(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(text, fontFamily = IBMPlexMono, fontWeight = FontWeight.Medium, fontSize = 13.sp, color = color, maxLines = 1, textAlign = TextAlign.Center, modifier = modifier)
}

@Composable
private fun LinearStage(model: LinearSandbox, showsReset: Boolean) {
    val incoming = model.previewing && model.op.adds && !model.full
    Column {
        Box(modifier = Modifier.fillMaxWidth()) {
            when (model.kind) {
                LinearKind.Stack -> StackStage(model, incoming)
                LinearKind.Queue, LinearKind.Deque -> RowStage(model, incoming)
                LinearKind.List -> ListStage(model, incoming)
            }
            if (showsReset) {
                IconButton(onClick = model::reset, modifier = Modifier.align(Alignment.TopEnd).size(36.dp)) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Reset", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                }
            }
        }
        LinearLegend(model, Modifier.padding(top = 16.dp))
    }
}

/** Bottom to top, a base line underneath, labels to the right. */
@Composable
private fun StackStage(model: LinearSandbox, incoming: Boolean) {
    val accent = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val leavingTop = model.previewing && model.op == LinearOp.Pop
    Column(
        modifier = Modifier.fillMaxWidth().heightIn(min = 320.dp).padding(top = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.Bottom),
    ) {
        if (incoming) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IncomingBox(model.value, 180.dp, 52.dp)
                Tag("← push", accent, Modifier.padding(start = 12.dp).width(64.dp))
            }
        }
        for (i in model.items.indices.reversed()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ValueBox("${model.items[i]}", cellStyle(model, i, isEnd = i == model.n - 1), 180.dp, 52.dp)
                Box(modifier = Modifier.padding(start = 12.dp).width(64.dp)) {
                    if (i == model.n - 1) {
                        Tag(if (leavingTop) "← pop" else "← top", if (leavingTop) SimColors.Red else SimColors.Active)
                    }
                }
            }
        }
        if (model.n == 0 && !incoming) {
            Box(modifier = Modifier.height(52.dp), contentAlignment = Alignment.Center) {
                Text("empty", fontFamily = IBMPlexMono, fontSize = 14.sp, color = muted)
            }
        }
        Box(
            modifier = Modifier
                .padding(end = 76.dp)
                .width(220.dp)
                .height(2.dp)
                .background(muted.copy(alpha = 0.5f)),
        )
    }
}

/** Queue and deque: a row, front on the left; the end labels and "open end" notes beneath. */
@Composable
private fun RowStage(model: LinearSandbox, incoming: Boolean) {
    val accent = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val isDeque = model.kind == LinearKind.Deque
    val pushFront = isDeque && model.end == End.Front
    val cells = model.n + if (incoming) 1 else 0
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val gap = 8.dp
        val slots = maxOf(cells, 4)
        // Cells shrink to fit up to a floor; past that the row scrolls sideways.
        val w = maxOf((maxWidth - gap * (slots - 1)) / slots, 52.dp)
        val scroll = rememberScrollState()
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Column(modifier = Modifier.horizontalScroll(scroll), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                if (incoming && pushFront) IncomingBox(model.value, w, 50.dp)
                model.items.indices.forEach { i ->
                    val isEnd = if (isDeque) i == model.endIndex else i == 0
                    ValueBox("${model.items[i]}", cellStyle(model, i, isEnd), w, 50.dp)
                }
                if (incoming && !pushFront) IncomingBox(model.value, w, 50.dp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                if (incoming && pushFront) Tag("new", accent, Modifier.width(w))
                model.items.indices.forEach { i ->
                    val isFront = i == 0
                    val isBack = i == model.n - 1
                    val label = when {
                        isFront && isBack -> "front·back"
                        isFront -> "front"
                        isBack -> "back"
                        else -> ""
                    }
                    // The end in use (a queue's front) is yellow, the other named end blue.
                    val lit = if (isDeque) i == model.endIndex else isFront
                    Tag(label, if (lit) StoryTone.Active.ink() else StoryTone.Path.ink(), Modifier.width(w))
                }
                if (incoming && !pushFront) Tag("new", accent, Modifier.width(w))
            }
          }
            Row(modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
                Text(if (isDeque) "⇄ open end" else "← leaves at the front", fontSize = 14.sp, color = muted)
                Spacer(Modifier.weight(1f))
                Text(if (isDeque) "open end ⇄" else "joins at the back ←", fontSize = 14.sp, color = muted)
            }
        }
    }
}

/**
 * Nodes joined by links, null at the end; the incoming node sits dashed in the slot it will take.
 * A node can be tapped to set the index.
 */
@Composable
private fun ListStage(model: LinearSandbox, incoming: Boolean) {
    val accent = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val t = model.target
    // (item index or null for the incoming node)
    val entries: List<Int?> = model.items.indices.toMutableList<Int?>().apply { if (incoming) add(t, null) }
    BoxWithConstraints(modifier = Modifier.fillMaxWidth().padding(vertical = 50.dp)) {
        val link = LinkedNodeSpec.Link
        val nullW = 44.dp
        // Every linked list in the app draws its nodes at LinkedNodeSpec's size; a list too long for
        // the screen scrolls sideways rather than shrinking them.
        val w = LinkedNodeSpec.Width
        val h = LinkedNodeSpec.Height
        val fits = (w + link) * entries.size + nullW <= maxWidth
        Row(
            modifier = if (fits) Modifier.fillMaxWidth() else Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.Top,
        ) {
            entries.forEachIndexed { k, i ->
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (i == null) {
                        IncomingBox(model.value, w, h)
                        Tag("new", accent)
                    } else {
                        val walkTo = model.previewing && model.op != LinearOp.Search && i == t - 1
                        ValueBox(
                            "${model.items[i]}",
                            cellStyle(model, i, walkTo),
                            w,
                            h,
                            Modifier.clip(RoundedCornerShape(10.dp)).clickable(enabled = model.canSelect(i)) { model.select(i) },
                        )
                        val label = when (i) {
                            0 -> "head"
                            model.n - 1 -> "tail"
                            else -> " "
                        }
                        Tag(label, if (i == 0 && (walkTo || model.markIndex == 0)) SimColors.Active else muted)
                    }
                }
                val dashed = i == null || (k + 1 < entries.size && entries[k + 1] == null)
                LinkLine(dashed, if (dashed) accent else muted, Modifier.width(link).height(h))
            }
            Box(modifier = Modifier.width(nullW).height(h), contentAlignment = Alignment.CenterStart) {
                Text("null", fontFamily = IBMPlexMono, fontSize = 14.sp, color = muted)
            }
        }
    }
}

@Composable
private fun LinkLine(dashed: Boolean, color: Color, modifier: Modifier) {
    Canvas(modifier) {
        drawLine(
            color = color,
            start = Offset(2.dp.toPx(), size.height / 2),
            end = Offset(size.width - 2.dp.toPx(), size.height / 2),
            strokeWidth = 2.dp.toPx(),
            pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(8f, 7f)) else null,
        )
    }
}

@Composable
private fun LinearLegend(model: LinearSandbox, modifier: Modifier) {
    val accent = MaterialTheme.colorScheme.primary
    val endLabel = when (model.kind) {
        LinearKind.Stack -> "Top"
        LinearKind.Queue -> "Next out"
        LinearKind.Deque -> "End in use"
        LinearKind.List -> "Walk to"
    }
    val bodyLabel = when (model.kind) {
        LinearKind.Stack -> "In stack"
        LinearKind.Queue -> "Waiting"
        LinearKind.Deque -> "In deque"
        LinearKind.List -> "Node"
    }
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        LegendEntry(SimColors.Active, endLabel, dashed = false)
        LegendEntry(SimColors.Blue, bodyLabel, dashed = false)
        if (model.op.removes) LegendEntry(SimColors.Red, "Leaving", dashed = false) else LegendEntry(accent, "Preview", dashed = true)
    }
}

@Composable
private fun LegendEntry(color: Color, label: String, dashed: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier.size(10.dp).drawBehind {
                if (dashed) {
                    drawRoundRect(
                        color = color,
                        cornerRadius = CornerRadius(3.dp.toPx()),
                        style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f))),
                    )
                } else {
                    drawRoundRect(color = color, cornerRadius = CornerRadius(3.dp.toPx()))
                }
            },
        )
        Text(
            label,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}

// ── Controls ─────────────────────────────────────────────────────────────────

/** The op picker, the end or position as an inline segmented row, then the value and a button named after the op. */
@Composable
private fun LinearControls(model: LinearSandbox) {
    // The keyboard pans the whole screen up, hiding the stage; running closes it so the result is visible.
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val hasField = model.op.adds || model.op == LinearOp.Search || (model.kind == LinearKind.List && model.op == LinearOp.Delete)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        model.blockReason?.takeIf { !model.busy }?.let { LabNotice(it) }
        LabSegments(model.ops.map { it.label }, model.ops.indexOf(model.op)) {
            if (!model.busy) { model.op = model.ops[it]; model.edited() }
        }
        if (model.kind == LinearKind.Deque) {
            LabOptionRow("End", listOf("Front", "Back"), if (model.end == End.Front) 0 else 1, enabled = !model.busy) {
                model.end = if (it == 0) End.Front else End.Back
                model.edited()
            }
        }
        if (model.kind == LinearKind.List && model.op == LinearOp.Insert) {
            LabOptionRow(
                "At",
                listOf("Head", "Index ${model.selected.coerceAtMost(model.n)}", "Tail"),
                model.pos.ordinal,
                enabled = !model.busy,
            ) {
                model.pos = ListPos.entries[it]
                model.edited()
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (model.op.adds || model.op == LinearOp.Search) {
                LabValueStepper("Value", model.valueInput, { model.valueInput = it; model.edited() }, Modifier.weight(1f)) { model.stepValue(it) }
            } else if (model.kind == LinearKind.List && model.op == LinearOp.Delete) {
                LabValueStepper(
                    "Index", "${model.target}", {}, Modifier.weight(1f),
                    canDecrease = model.target > 0, canIncrease = model.target < model.n - 1, editable = false,
                ) { model.stepIndex(it) }
            }
            LabActionButton(model.actionTitle, if (hasField) Modifier else Modifier.weight(1f), enabled = model.canRun) {
                if (!model.busy) { focus.clearFocus(); keyboard?.hide(); model.runOp() }
            }
        }
    }
}
