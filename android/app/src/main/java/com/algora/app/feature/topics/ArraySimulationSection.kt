package com.algora.app.feature.topics

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.algora.app.core.ui.theme.IBMPlexMono
import com.algora.app.core.ui.theme.LocalDarkTheme
import com.algora.app.core.ui.theme.SimColors
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// An array is a *fixed-size* contiguous block: the widget always draws `capacity` slots, filled
// ones and free ones alike, so the size/capacity split stays visible. Every write that moves an
// element is animated one slot per tick — that is the whole reason insert/delete are O(n) here
// while an index read is O(1), and the reason a full array must be reallocated and copied.
//
// Laid out as the sandbox in docs/ios-design/Simulations iOS.html (screen 04): pick an operation,
// read it as a sentence ("Insert [42] at [index 2 ⌄]"), see its cost previewed on the stage, run.
private const val INITIAL_CAPACITY = 8
private const val MAX_CAPACITY = 16
private val initialValues = listOf(4, 8, 15, 16, 23)
private const val STEP_MS = 300L
private const val DEFAULT_SELECTED = 2

// What the currently marked slot is doing — drives the cell's fill.
private enum class Mark { Cursor, Move, Write }

private enum class ArrayOp(val label: String, val verb: String) {
    Insert("Insert", "Insert"),
    Delete("Delete", "Delete"),
    Access("Access", "Read"),
    Search("Search", "Find"),
}

/** Where an insert lands. Head and Tail stay pinned as the array changes size. */
private enum class InsertPos { Head, Index, Tail }

private val spelled = listOf("Zero", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine", "Ten")

private fun spell(k: Int) = spelled.getOrElse(k) { "$k" }

@Composable
fun ArraySimulationSection() {
    // `null` = free slot. List length is the capacity; `size` is how many slots are in use.
    val slots = remember {
        mutableStateListOf<Int?>().apply {
            repeat(INITIAL_CAPACITY) { add(null) }
            initialValues.forEachIndexed { i, v -> this[i] = v }
        }
    }
    var size by remember { mutableIntStateOf(initialValues.size) }
    var markIndex by remember { mutableStateOf<Int?>(null) }
    var markKind by remember { mutableStateOf(Mark.Cursor) }
    var status by remember { mutableStateOf("") }
    var cost by remember { mutableStateOf("") }
    var op by remember { mutableStateOf(ArrayOp.Insert) }
    var pos by remember { mutableStateOf(InsertPos.Index) }
    var selected by remember { mutableIntStateOf(DEFAULT_SELECTED) }
    var valueInput by remember { mutableStateOf("42") }
    var busy by remember { mutableStateOf(false) }
    // Set when an op finishes, so its outcome stays on screen until the next edit.
    var showingResult by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    var job by remember { mutableStateOf<Job?>(null) }

    val full = size == slots.size
    val target = when (op) {
        ArrayOp.Insert -> when (pos) {
            InsertPos.Head -> 0
            InsertPos.Tail -> size
            InsertPos.Index -> selected.coerceAtMost(size)
        }
        ArrayOp.Delete, ArrayOp.Access -> selected.coerceAtMost((size - 1).coerceAtLeast(0))
        ArrayOp.Search -> 0
    }
    val previewing = !busy && !showingResult

    // Why Run can't do this op right now, in words; null when it can.
    val blockReason: String? = when {
        op == ArrayOp.Insert && full && slots.size * 2 > MAX_CAPACITY ->
            "No free slot. All ${slots.size} slots are used and this array can't grow past $MAX_CAPACITY. Delete something first."
        (op == ArrayOp.Delete || op == ArrayOp.Access) && size == 0 ->
            "The array is empty, so there is nothing to ${if (op == ArrayOp.Delete) "delete" else "read"}. Insert something first."
        (op == ArrayOp.Insert || op == ArrayOp.Search) && valueInput.toIntOrNull() == null ->
            "Type a number to ${if (op == ArrayOp.Insert) "insert" else "find"} first."
        else -> null
    }
    // A heads-up that the op costs more than it looks: a full block has to be reallocated first.
    val growNotice: String? =
        if (op == ArrayOp.Insert && full && blockReason == null) {
            "All ${slots.size} slots are used. Inserting copies every element into a new ${slots.size * 2}-slot block first."
        } else {
            null
        }

    fun clearResult() {
        if (busy) return
        showingResult = false
        cost = ""
    }

    suspend fun tick() = delay(STEP_MS)

    fun mark(index: Int?, kind: Mark = Mark.Cursor) {
        markIndex = index
        markKind = kind
    }

    // Serializes every animated op: one job at a time, Run disabled while it runs. Without this
    // a queued op would index into a list another op has already shifted.
    fun launchOp(block: suspend () -> Unit) {
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

    // A static array cannot grow. A dynamic array (ArrayList/vector) allocates a bigger block and
    // copies every element across — O(n), but doubling makes appends O(1) amortized.
    suspend fun grow(): Boolean {
        val capacity = slots.size
        val newCapacity = capacity * 2
        if (newCapacity > MAX_CAPACITY) {
            status = "Capacity $capacity is full. This lab stops growing at $MAX_CAPACITY."
            cost = ""
            return false
        }
        status = "Full: allocating a block of $newCapacity and copying $size elements."
        repeat(newCapacity - capacity) { slots.add(null) }
        for (i in 0 until size) {
            mark(i, Mark.Move)
            tick()
        }
        cost = "copies = $size"
        return true
    }

    // Insert at `at`: everything from `at` up shifts one slot right, highest index first, so no
    // live element is overwritten. Cost = size - at moves.
    suspend fun insertAt(at: Int, value: Int) {
        if (size == slots.size && !grow()) return
        var shifts = 0
        for (j in size - 1 downTo at) {
            slots[j + 1] = slots[j]
            slots[j] = null
            mark(j + 1, Mark.Move)
            shifts++
            status = "Shifting index $j → ${j + 1}."
            tick()
        }
        slots[at] = value
        size++
        mark(at, Mark.Write)
        status = "Inserted $value at index $at. " +
            if (shifts == 0) "A tail insert moves nothing." else "Every later element moved one slot right."
        cost = "shifts = $shifts · cost = ${if (shifts == 0) "O(1)" else "O(n−i)"}"
        tick()
    }

    fun onAccess() = launchOp {
        val i = target
        if (i !in 0 until size) {
            status = "The array is empty."
            cost = ""
            return@launchOp
        }
        mark(i, Mark.Cursor)
        status = "a[$i] = ${slots[i]}. One address computation, no scanning."
        cost = "steps = 1 · cost = O(1)"
        tick()
        tick()
    }

    fun onSearch() = launchOp {
        val v = valueInput.toIntOrNull()
        if (v == null) {
            status = "Enter a value first."
            cost = ""
            return@launchOp
        }
        var comparisons = 0
        for (i in 0 until size) {
            mark(i, Mark.Cursor)
            comparisons++
            status = "Comparing a[$i] = ${slots[i]} with $v."
            tick()
            if (slots[i] == v) {
                mark(i, Mark.Write)
                status = "Found $v at index $i after $comparisons comparison${if (comparisons == 1) "" else "s"}. " +
                    "An unsorted array has no shortcut."
                cost = "compares = $comparisons · cost = O(n)"
                tick()
                return@launchOp
            }
        }
        status = "$v is not in the array. A miss always scans every element."
        cost = "compares = $comparisons · cost = O(n)"
    }

    fun onDelete() = launchOp {
        val i = target
        if (i !in 0 until size) {
            status = "The array is empty."
            cost = ""
            return@launchOp
        }
        val removed = slots[i]
        mark(i, Mark.Write)
        status = "Removing $removed at index $i."
        tick()
        // Close the hole: each later element slides one slot left.
        var shifts = 0
        for (j in i until size - 1) {
            slots[j] = slots[j + 1]
            slots[j + 1] = null
            mark(j, Mark.Move)
            shifts++
            status = "Shifting index ${j + 1} → $j."
            tick()
        }
        slots[size - 1] = null
        size--
        status = "Deleted $removed. An array has no holes, so every later element closed the gap."
        cost = "shifts = $shifts · cost = O(n−i)"
    }

    fun runOp() {
        if (blockReason != null) {
            status = blockReason
            cost = ""
            showingResult = true
            return
        }
        when (op) {
            ArrayOp.Insert -> {
                val v = valueInput.toIntOrNull()
                if (v == null) {
                    status = "Enter a value first."
                    cost = ""
                    showingResult = true
                } else {
                    val at = target
                    launchOp { insertAt(at, v) }
                }
            }
            ArrayOp.Delete -> onDelete()
            ArrayOp.Access -> onAccess()
            ArrayOp.Search -> onSearch()
        }
    }

    fun reset() {
        job?.cancel()
        busy = false
        slots.clear()
        repeat(INITIAL_CAPACITY) { slots.add(null) }
        initialValues.forEachIndexed { i, v -> slots[i] = v }
        size = initialValues.size
        mark(null)
        selected = DEFAULT_SELECTED
        pos = InsertPos.Index
        showingResult = false
        status = ""
        cost = ""
    }

    // ── Preview: the cost of the op, before it runs ──
    val value = valueInput.ifEmpty { "?" }
    val chips = buildList {
        add("size = $size / ${slots.size}")
        if (!previewing) {
            if (cost.isNotEmpty()) add(cost)
        } else {
            when (op) {
                ArrayOp.Insert -> {
                    val shifts = size - target
                    add("shifts = $shifts")
                    add("cost = ${if (shifts == 0 && !full) "O(1)" else "O(n−i)"}")
                }
                ArrayOp.Delete -> { add("shifts = ${(size - 1 - target).coerceAtLeast(0)}"); add("cost = O(n−i)") }
                ArrayOp.Access -> { add("steps = 1"); add("cost = O(1)") }
                ArrayOp.Search -> { add("worst = $size compares"); add("cost = O(n)") }
            }
        }
    }.joinToString(" · ")
    val moves = { k: Int -> if (k == 1) "One element moves" else "${spell(k)} elements move" }
    val preview = blockReason ?: when (op) {
        ArrayOp.Insert -> {
            val shifts = size - target
            val grow = if (full) " The block is full, so it first doubles to ${slots.size * 2} and copies all $size." else ""
            if (shifts == 0) "Appending at index $target. Nothing moves, $value lands in free capacity.$grow"
            else "Index $target is selected. ${moves(shifts)} right before $value lands.$grow"
        }
        ArrayOp.Delete -> when {
            size == 0 -> "The array is empty. Insert something first."
            size - 1 - target == 0 -> "Removing the last element at index $target. Nothing needs to move."
            else -> "Index $target is selected. ${moves(size - 1 - target)} left to close the gap."
        }
        ArrayOp.Access ->
            if (size == 0) "The array is empty. Insert something first."
            else "Reading index $target is one address computation. Base + $target × element size, no scanning."
        ArrayOp.Search -> "Looking for $value means checking cells left to right. An unsorted array has no shortcut."
    }

    val dock = LocalLabDock.current

    val stage: @Composable () -> Unit = {
        ArrayStage(
            slots = slots,
            size = size,
            markIndex = markIndex,
            markKind = markKind,
            target = if (previewing && op != ArrayOp.Search) target else null,
            willShift = { i ->
                previewing && when (op) {
                    ArrayOp.Insert -> i in target until size
                    ArrayOp.Delete -> i in (target + 1) until size
                    else -> false
                }
            },
            incoming = if (previewing && op == ArrayOp.Insert) value else null,
            insertMode = op == ArrayOp.Insert,
            onSelect = { i ->
                if (!busy) {
                    selected = i
                    if (op == ArrayOp.Insert) pos = InsertPos.Index
                    clearResult()
                }
            },
            onReset = if (dock == null) ::reset else null,
        )
        Row(
            modifier = Modifier.padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LegendSwatchRow(SimColors.Blue, if (op == ArrayOp.Delete) "Will shift left" else "Will shift right", dashed = false)
            LegendSwatchRow(MaterialTheme.colorScheme.onSurfaceVariant, "Free capacity", dashed = true)
        }
    }

    // Readout chips over the narration. Two caption lines are reserved so nothing below jumps while
    // an op narrates.
    val narration: @Composable (Modifier) -> Unit = { modifier ->
        Column(modifier) {
            ReadoutChips(chips)
            LabCaption(if (previewing) preview else status, Modifier.padding(top = 14.dp).heightIn(min = 78.dp))
        }
    }

    val controls: @Composable () -> Unit = {
        Column {
            if (!busy) {
                when {
                    blockReason != null -> LabNotice(blockReason, Modifier.padding(bottom = 12.dp))
                    growNotice != null -> LabNotice(growNotice, Modifier.padding(bottom = 12.dp), blocked = false)
                }
            }
            OpSegments(op) { op = it; clearResult() }
            SentenceRow(
                op = op,
                value = valueInput,
                onValueChange = { input ->
                    // Keep a single leading minus so negative values are expressible.
                    val sign = if (input.startsWith("-")) "-" else ""
                    valueInput = sign + input.filter(Char::isDigit).take(4)
                    clearResult()
                },
                positionLabel = when {
                    op == ArrayOp.Insert && pos == InsertPos.Head -> "head"
                    op == ArrayOp.Insert && pos == InsertPos.Tail -> "tail"
                    else -> "index $target"
                },
                menuItems = if (op == ArrayOp.Insert) {
                    listOf(
                        Triple("Head", pos == InsertPos.Head) { pos = InsertPos.Head; clearResult() },
                        Triple("Index ${selected.coerceAtMost(size)}", pos == InsertPos.Index) { pos = InsertPos.Index; clearResult() },
                        Triple("Tail", pos == InsertPos.Tail) { pos = InsertPos.Tail; clearResult() },
                    )
                } else {
                    (0 until size.coerceAtLeast(1)).map { i -> Triple("Index $i", target == i) { selected = i; clearResult() } }
                },
                busy = busy,
                dimmed = blockReason != null,
                onRun = ::runOp,
            )
            Text(
                if (op == ArrayOp.Insert || op == ArrayOp.Search) "Tap a cell to change the index. Tap $value to type a value."
                else "Tap a cell to change the index.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 12.dp),
            )
        }
    }

    if (dock != null) {
        // Docked (sim-only screen): stage card, readout and narration on the page, the controls
        // pinned to the bottom and reset in the nav bar. Re-handed every composition, since the
        // controls close over this composition's derived values (target, labels).
        SideEffect {
            dock.controls = controls
            dock.navAction = LabNavAction(Icons.Filled.Refresh, "Reset array", ::reset)
        }
        DisposableEffect(dock) {
            onDispose {
                dock.controls = null
                dock.navAction = null
            }
        }
        Column(modifier = Modifier.fillMaxWidth()) {
            StageCard { stage() }
            narration(Modifier.padding(start = 4.dp, end = 4.dp, top = 14.dp))
        }
    } else {
        StageCard {
            stage()
            narration(Modifier.padding(top = 14.dp))
            HorizontalDivider(modifier = Modifier.padding(top = 16.dp, bottom = 14.dp), color = MaterialTheme.colorScheme.outline)
            controls()
        }
    }
}

@Composable
private fun StageCard(content: @Composable () -> Unit) {
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
private fun LegendSwatchRow(color: Color, label: String, dashed: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier.size(10.dp).drawBehind {
                if (dashed) {
                    drawRoundRect(
                        color = color,
                        cornerRadius = CornerRadius(3.dp.toPx()),
                        style = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f))),
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

/** Capacity cells across the full width, the incoming value floating over its landing slot. */
@Composable
private fun ArrayStage(
    slots: List<Int?>,
    size: Int,
    markIndex: Int?,
    markKind: Mark,
    target: Int?,
    willShift: (Int) -> Boolean,
    incoming: String?,
    insertMode: Boolean,
    onSelect: (Int) -> Unit,
    onReset: (() -> Unit)?,
) {
    val accent = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val idle = if (LocalDarkTheme.current) Color(0xFF2A2E3A) else Color(0xFFE3E6EC)
    val dense = slots.size > 8
    val gap = if (dense) 3.dp else 5.dp

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val stageW = maxWidth
        val cellW = (stageW - gap * (slots.size - 1)) / slots.size
        Column {
            Box(modifier = Modifier.fillMaxWidth().height(36.dp)) {
                if (incoming != null && target != null) {
                    val x = (cellW + gap) * target + cellW / 2
                    Row(
                        modifier = Modifier
                            .offset(x = (x - 32.dp).coerceIn(0.dp, stageW - 108.dp))
                            .align(Alignment.CenterStart)
                            .height(30.dp)
                            .background(accent.copy(alpha = 0.2f), RoundedCornerShape(9.dp))
                            .border(1.5.dp, accent, RoundedCornerShape(9.dp))
                            .padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Text(incoming, fontFamily = IBMPlexMono, fontWeight = FontWeight.Medium, fontSize = 15.sp)
                        Icon(Icons.Filled.ArrowDownward, contentDescription = null, tint = accent, modifier = Modifier.size(12.dp))
                    }
                }
                if (onReset != null) {
                    IconButton(onClick = onReset, modifier = Modifier.align(Alignment.CenterEnd).size(36.dp)) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Reset array", tint = muted, modifier = Modifier.size(20.dp))
                    }
                }
            }
            Row(modifier = Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(gap)) {
                slots.forEachIndexed { i, v ->
                    val inUse = v != null
                    val marked = i == markIndex
                    val isTarget = target == i && (if (insertMode) i <= size else inUse)
                    val shift = willShift(i)
                    val fill = when {
                        marked -> when (markKind) {
                            Mark.Cursor -> SimColors.Active
                            Mark.Move -> SimColors.Blue
                            Mark.Write -> SimColors.Green
                        }
                        shift -> SimColors.Blue.copy(alpha = 0.25f)
                        inUse -> idle
                        else -> Color.Transparent
                    }
                    val selectable = if (insertMode) i <= size else inUse
                    Column(
                        modifier = Modifier
                            .width(cellW)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(enabled = selectable) { onSelect(i) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp)
                                .then(if (isTarget) Modifier.border(2.dp, accent, RoundedCornerShape(12.dp)) else Modifier)
                                .padding(if (isTarget) 3.dp else 2.dp)
                                .drawBehind {
                                    val r = CornerRadius(10.dp.toPx())
                                    drawRoundRect(color = fill, cornerRadius = r)
                                    if (shift) drawRoundRect(color = SimColors.Blue, cornerRadius = r, style = Stroke(1.5.dp.toPx()))
                                    if (!inUse && !marked) {
                                        drawRoundRect(
                                            color = muted.copy(alpha = 0.6f),
                                            cornerRadius = r,
                                            style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 7f))),
                                        )
                                    }
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (v != null) {
                                Text(
                                    "$v",
                                    fontFamily = IBMPlexMono,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = if (dense) 13.sp else 17.sp,
                                    color = if (marked && markKind == Mark.Cursor) Color(0xFF1A1A1A)
                                    else if (marked) Color.White
                                    else MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                        Text(
                            "$i",
                            fontFamily = IBMPlexMono,
                            fontSize = 12.sp,
                            fontWeight = if (isTarget) FontWeight.Medium else FontWeight.Normal,
                            color = if (isTarget) accent else muted.copy(alpha = if (inUse) 1f else 0.5f),
                        )
                    }
                }
            }
        }
    }
}

/** Insert / Delete / Access / Search as a segmented control. */
@Composable
private fun OpSegments(selected: ArrayOp, onSelect: (ArrayOp) -> Unit) {
    val dark = LocalDarkTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(34.dp)
            .background(SimColors.Tint, RoundedCornerShape(9.dp))
            .padding(2.dp),
    ) {
        ArrayOp.entries.forEach { op ->
            val on = op == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .height(30.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(if (on) (if (dark) Color(0xFF636366) else Color.White) else Color.Transparent)
                    .clickable { onSelect(op) },
                contentAlignment = Alignment.Center,
            ) {
                Text(op.label, fontSize = 14.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium)
            }
        }
    }
}

/** "Insert [42] at [index 2 ⌄]" with a compact Run button. */
@Composable
private fun SentenceRow(
    op: ArrayOp,
    value: String,
    onValueChange: (String) -> Unit,
    positionLabel: String,
    menuItems: List<Triple<String, Boolean, () -> Unit>>,
    busy: Boolean,
    dimmed: Boolean,
    onRun: () -> Unit,
) {
    // The keyboard pans the whole screen up, hiding the stage; Run and Done close it so the result
    // is visible.
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val dismissKeyboard = { focus.clearFocus(); keyboard?.hide(); Unit }
    val accent = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .padding(top = 12.dp)
            .fillMaxWidth()
            .height(60.dp)
            .background(SimColors.Tint.copy(alpha = 0.14f), RoundedCornerShape(16.dp))
            .padding(start = 16.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(op.verb, fontSize = 17.sp, color = muted)
        if (op == ArrayOp.Insert || op == ArrayOp.Search) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(
                    fontFamily = IBMPlexMono,
                    fontWeight = FontWeight.Medium,
                    fontSize = 17.sp,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                cursorBrush = SolidColor(accent),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { dismissKeyboard() }),
                modifier = Modifier
                    .width(56.dp)
                    .height(36.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(SimColors.Tint)
                    .drawBehind {
                        drawRect(accent, topLeft = Offset(4.dp.toPx(), size.height - 2.dp.toPx()), size = Size(size.width - 8.dp.toPx(), 2.dp.toPx()))
                    }
                    .padding(top = 7.dp),
            )
        }
        if (op == ArrayOp.Insert) Text("at", fontSize = 17.sp, color = muted)
        if (op != ArrayOp.Search) {
            Box {
                Row(
                    modifier = Modifier
                        .height(36.dp)
                        .widthIn(min = 44.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(accent.copy(alpha = 0.18f))
                        .clickable(enabled = !busy) { menuOpen = true }
                        .padding(start = 12.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(positionLabel, color = accent, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    Icon(Icons.Filled.UnfoldMore, contentDescription = null, tint = accent, modifier = Modifier.padding(start = 4.dp).size(16.dp))
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    menuItems.forEach { (label, checked, onClick) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            trailingIcon = if (checked) ({ Icon(Icons.Filled.Check, contentDescription = null) }) else null,
                            onClick = { onClick(); menuOpen = false },
                        )
                    }
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(accent.copy(alpha = if (busy || dimmed) 0.4f else 1f))
                .clickable(enabled = !busy, onClickLabel = "Run") { dismissKeyboard(); onRun() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.PlayArrow, contentDescription = "Run", tint = Color.White, modifier = Modifier.size(24.dp))
        }
    }
}
