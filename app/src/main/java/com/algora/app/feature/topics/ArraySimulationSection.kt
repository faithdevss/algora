package com.algora.app.feature.topics

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FirstPage
import androidx.compose.material.icons.filled.LastPage
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.algora.app.core.ui.theme.IBMPlexMono
import com.algora.app.core.ui.theme.SimColors
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// An array is a *fixed-size* contiguous block: the widget always draws `capacity` slots, filled
// ones and free ones alike, so the size/capacity split stays visible. Every write that moves an
// element is animated one slot per tick — that is the whole reason insert/delete are O(n) here
// while an index read is O(1), and the reason a full array must be reallocated and copied.
private const val INITIAL_CAPACITY = 8
private const val MAX_CAPACITY = 16
private val initialValues = listOf(4, 8, 15, 16, 23)

// Inputs start pre-filled so every button does something meaningful on first tap.
private const val DEFAULT_VALUE_INPUT = "42"
private const val DEFAULT_INDEX_INPUT = "2"

private val cellSize = 56.dp
private val cellGap = 8.dp

// What the currently marked slot is doing — drives the cell's accent color.
private enum class Mark { Cursor, Move, Write }

// Filled cells are the shared value chips drawn on a Canvas rather than as composables, so they use
// the SimChip palette directly: a used slot is legible at a glance and matches the other widgets.
private val SlotFilled = ChipViolet
private val SlotCursor = ChipAmber
private val SlotMove = ChipBlue
private val SlotWrite = ChipGreen

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
    var statusMessage by remember { mutableStateOf("Array of ${initialValues.size}, capacity $INITIAL_CAPACITY") }
    var costMessage by remember { mutableStateOf("") }
    var valueInput by remember { mutableStateOf(DEFAULT_VALUE_INPUT) }
    var indexInput by remember { mutableStateOf(DEFAULT_INDEX_INPUT) }
    var stepMs by remember { mutableStateOf(260f) }
    var busy by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    var job by remember { mutableStateOf<Job?>(null) }

    fun parsedValue(): Int? = valueInput.toIntOrNull()
    fun parsedIndex(): Int? = indexInput.toIntOrNull()

    suspend fun tick() = delay(stepMs.toLong())

    fun mark(index: Int?, kind: Mark = Mark.Cursor) {
        markIndex = index
        markKind = kind
    }

    // Serializes every animated op: one job at a time, buttons disabled while it runs. Without this
    // a queued op would index into a list another op has already shifted.
    fun launchOp(block: suspend () -> Unit) {
        if (busy) return
        busy = true
        job = scope.launch {
            try {
                block()
            } finally {
                mark(null)
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
            statusMessage = "Capacity $capacity is full and cannot grow past $MAX_CAPACITY here"
            costMessage = ""
            return false
        }
        statusMessage = "Full — allocating a new block of $newCapacity and copying $size elements"
        repeat(newCapacity - capacity) { slots.add(null) }
        for (i in 0 until size) {
            mark(i, Mark.Move)
            tick()
        }
        costMessage = "Copies: $size · resize is O(n), amortized O(1) per append"
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
            statusMessage = "Shifting index $j → ${j + 1}"
            tick()
        }
        slots[at] = value
        size++
        mark(at, Mark.Write)
        statusMessage = "Inserted $value at index $at"
        costMessage = "Shifts: $shifts · O(n) — ${if (at == size - 1) "tail insert moves nothing" else "every later element moved"}"
        tick()
    }

    fun onAccess() = launchOp {
        val i = parsedIndex()
        when {
            i == null -> { statusMessage = "Enter an index first"; costMessage = "" }
            i !in 0 until size -> { statusMessage = "Index $i out of bounds (size $size)"; costMessage = "" }
            else -> {
                mark(i, Mark.Cursor)
                statusMessage = "a[$i] = ${slots[i]}"
                costMessage = "1 step · O(1) — address = base + $i × element size, no scanning"
                tick()
                tick()
            }
        }
    }

    fun onSearch() = launchOp {
        val v = parsedValue()
        if (v == null) {
            statusMessage = "Enter a value first"
            costMessage = ""
            return@launchOp
        }
        var comparisons = 0
        for (i in 0 until size) {
            mark(i, Mark.Cursor)
            comparisons++
            statusMessage = "Comparing a[$i] = ${slots[i]} with $v"
            tick()
            if (slots[i] == v) {
                mark(i, Mark.Write)
                statusMessage = "Found $v at index $i"
                costMessage = "Comparisons: $comparisons · O(n) — unsorted array has no shortcut"
                tick()
                return@launchOp
            }
        }
        statusMessage = "$v not found"
        costMessage = "Comparisons: $comparisons · O(n) — a miss always scans the whole array"
    }

    fun onDelete() = launchOp {
        val i = parsedIndex()
        if (i == null) {
            statusMessage = "Enter an index first"
            costMessage = ""
            return@launchOp
        }
        if (i !in 0 until size) {
            statusMessage = "Index $i out of bounds (size $size)"
            costMessage = ""
            return@launchOp
        }
        val removed = slots[i]
        mark(i, Mark.Write)
        statusMessage = "Removing $removed at index $i"
        tick()
        // Close the hole: each later element slides one slot left.
        var shifts = 0
        for (j in i until size - 1) {
            slots[j] = slots[j + 1]
            slots[j + 1] = null
            mark(j, Mark.Move)
            shifts++
            statusMessage = "Shifting index ${j + 1} → $j"
            tick()
        }
        slots[size - 1] = null
        size--
        statusMessage = "Deleted $removed"
        costMessage = "Shifts: $shifts · O(n) — an array has no holes, so the gap must be closed"
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "size = $size    capacity = ${slots.size}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                fontFamily = IBMPlexMono,
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = 8.dp),
            ) {
                ArrayCanvas(slots = slots, size = size, markIndex = markIndex, markKind = markKind)
            }

            if (statusMessage.isNotEmpty()) {
                Text(
                    text = statusMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            if (costMessage.isNotEmpty()) {
                Text(
                    text = costMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SimNumberField(
                    value = valueInput,
                    // Keep a single leading minus so negative values are expressible.
                    onValueChange = { input ->
                        val sign = if (input.startsWith("-")) "-" else ""
                        valueInput = sign + input.filter(Char::isDigit)
                    },
                    label = "Value",
                    modifier = Modifier.weight(1f),
                )
                SimNumberField(
                    value = indexInput,
                    onValueChange = { indexInput = it.filter(Char::isDigit) },
                    label = "Index",
                    modifier = Modifier.weight(1f),
                )
            }

            // Two tiers: what a raw fixed array can do by itself, and what a dynamic array
            // (ArrayList) layers on top of it by shifting elements around.
            OpGroupLabel("Array primitives")
            SimOpRow(
                modifier = Modifier.padding(top = 8.dp),
                ops = listOf(
                    SimOp("Access At", Icons.Filled.MyLocation, SimColors.Green, enabled = !busy) { onAccess() },
                    SimOp("Search", Icons.Filled.Search, SimColors.Amber, enabled = !busy) { onSearch() },
                ),
            )
            OpGroupLabel("Dynamic array ops — built on shifting")
            SimOpRow(
                modifier = Modifier.padding(top = 8.dp),
                ops = listOf(
                    SimOp("Insert Head", Icons.Filled.FirstPage, SimColors.Blue, enabled = !busy) {
                        val v = parsedValue()
                        if (v == null) { statusMessage = "Enter a value first"; costMessage = "" } else launchOp { insertAt(0, v) }
                    },
                    SimOp("Insert Tail", Icons.Filled.LastPage, SimColors.Blue, enabled = !busy) {
                        val v = parsedValue()
                        if (v == null) { statusMessage = "Enter a value first"; costMessage = "" } else launchOp { insertAt(size, v) }
                    },
                ),
            )
            // Reset rides along as an icon-only button instead of claiming a fourth full-width row —
            // six stacked pills read as a wall of equally important actions, which they are not.
            SimOpRow(
                modifier = Modifier.padding(top = 8.dp),
                ops = listOf(
                    SimOp("Insert At", Icons.Filled.Add, SimColors.Violet, enabled = !busy) {
                        val v = parsedValue()
                        val i = parsedIndex()
                        when {
                            v == null || i == null -> { statusMessage = "Enter both value and index"; costMessage = "" }
                            i !in 0..size -> { statusMessage = "Index $i out of bounds (size $size)"; costMessage = "" }
                            else -> launchOp { insertAt(i, v) }
                        }
                    },
                    SimOp("Delete At", Icons.Filled.Delete, SimColors.Red, enabled = !busy) { onDelete() },
                    SimOp("", Icons.Filled.Refresh, SimColors.Grey, weight = 0.55f, contentDescription = "Reset") {
                        job?.cancel()
                        busy = false
                        slots.clear()
                        repeat(INITIAL_CAPACITY) { slots.add(null) }
                        initialValues.forEachIndexed { i, v -> slots[i] = v }
                        size = initialValues.size
                        mark(null)
                        statusMessage = "Reset to initial state"
                        costMessage = ""
                    },
                ),
            )

            Text(
                text = "${stepMs.toInt()} ms/step",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp),
            )
            Slider(value = stepMs, onValueChange = { stepMs = it }, valueRange = 80f..900f)
        }
    }
}

@Composable
private fun OpGroupLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontFamily = IBMPlexMono,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 14.dp),
    )
}

@Composable
private fun ArrayCanvas(slots: List<Int?>, size: Int, markIndex: Int?, markKind: Mark) {
    val textMeasurer = rememberTextMeasurer()
    val cellFill = MaterialTheme.colorScheme.surface
    val cellBorder = MaterialTheme.colorScheme.outline
    val indexTextColor = MaterialTheme.colorScheme.onSurfaceVariant

    val valueStyle = TextStyle(fontFamily = IBMPlexMono, fontWeight = FontWeight.Bold, fontSize = 18.sp, textAlign = TextAlign.Center)
    val indexStyle = TextStyle(fontFamily = IBMPlexMono, fontWeight = FontWeight.Normal, fontSize = 12.sp, textAlign = TextAlign.Center)

    val capacity = slots.size
    val totalWidth = cellSize * capacity + cellGap * (capacity - 1).coerceAtLeast(0)

    Canvas(
        modifier = Modifier
            .width(totalWidth.coerceAtLeast(cellSize))
            .height(cellSize + 24.dp),
    ) {
        val cellPx = cellSize.toPx()
        val gapPx = cellGap.toPx()
        val corner = CornerRadius(12f, 12f)
        // Free slots get a dashed outline so "allocated but unused" reads differently from "in use".
        val dashed = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))

        slots.forEachIndexed { index, value ->
            val left = index * (cellPx + gapPx)
            val inUse = index < size
            val accent = if (index == markIndex) {
                when (markKind) {
                    Mark.Cursor -> SlotCursor
                    Mark.Move -> SlotMove
                    Mark.Write -> SlotWrite
                }
            } else {
                null
            }

            // A slot holding a value gets the same solid gradient chip the linked-list nodes use, so
            // "in use" reads from the fill itself instead of from a border that differs from an
            // empty slot's only by alpha.
            val gradient = when {
                accent != null -> accent
                inUse -> SlotFilled
                else -> null
            }

            if (gradient == null) {
                drawRoundRect(
                    color = cellFill,
                    topLeft = Offset(left, 0f),
                    size = Size(cellPx, cellPx),
                    cornerRadius = corner,
                )
                drawRoundRect(
                    color = cellBorder.copy(alpha = 0.5f),
                    topLeft = Offset(left, 0f),
                    size = Size(cellPx, cellPx),
                    cornerRadius = corner,
                    style = Stroke(width = 2f, pathEffect = dashed),
                )
            } else {
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        listOf(gradient.top, gradient.bottom),
                        startY = 0f,
                        endY = cellPx,
                    ),
                    topLeft = Offset(left, 0f),
                    size = Size(cellPx, cellPx),
                    cornerRadius = corner,
                )
                // Marked slots keep a bright rim so the cursor is findable in a row of filled chips.
                if (accent != null) {
                    drawRoundRect(
                        color = Color.White,
                        topLeft = Offset(left, 0f),
                        size = Size(cellPx, cellPx),
                        cornerRadius = corner,
                        style = Stroke(width = 4f),
                    )
                }
            }

            if (value != null) {
                val valueLayout = textMeasurer.measure(value.toString(), valueStyle)
                drawText(
                    valueLayout,
                    color = Color.White,
                    topLeft = Offset(left + (cellPx - valueLayout.size.width) / 2f, (cellPx - valueLayout.size.height) / 2f),
                )
            }

            val indexLayout = textMeasurer.measure(index.toString(), indexStyle)
            drawText(
                indexLayout,
                color = if (inUse) indexTextColor else indexTextColor.copy(alpha = 0.45f),
                topLeft = Offset(left + (cellPx - indexLayout.size.width) / 2f, cellPx + 4f),
            )
        }
    }
}
