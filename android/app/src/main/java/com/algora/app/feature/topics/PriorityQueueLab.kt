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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.algora.app.core.ui.theme.IBMPlexMono
import com.algora.app.core.ui.theme.LocalDarkTheme
import com.algora.app.core.ui.theme.SimColors

// ── Priority queue lab ───────────────────────────────────────────────────────
// A min-heap drawn twice, as the tree and as the array that stores it, with insert (sift up) and
// extract-min (sift down) as tabs. The comparison being made rides on the edge between the two nodes.

private enum class PqTone { Idle, Active, Compared, Swapped }

private class PqFrame(
    val heap: List<Int>,
    val tones: Map<Int, PqTone>,
    val formula: String,
    val chips: List<StoryChip>,
    val headline: String,
    val body: String,
    // The edge up from this child carries the comparison ("3 < 4").
    val bubble: Pair<Int, String>? = null,
)

private const val PQ_CAPACITY = 7

private fun pqInsertFrames(): List<PqFrame> {
    val h0 = listOf(4, 7, 9, 8, 10)
    return listOf(
        PqFrame(
            h0, emptyMap(), "parent(i) = (i − 1) / 2", listOf(StoryChip("size", "5"), StoryChip("min", "4")),
            "Every parent is at most its children, so the minimum, {4}, sits at the root.",
            "The heap is a complete tree stored level by level in an array, so no pointers are needed.",
        ),
        PqFrame(
            h0 + 3, mapOf(5 to PqTone.Active), "heap[5] = {3}", listOf(StoryChip("size", "5 → 6"), StoryChip("compares", "0")),
            "Insert puts {3} in the next free slot, i5.",
            "That keeps the tree complete, but 3 may now be smaller than its parent.",
        ),
        PqFrame(
            h0 + 3, mapOf(5 to PqTone.Active, 2 to PqTone.Compared), "parent(5) = (5 − 1) / 2 = 2 → {3} < {p:9} → swap",
            listOf(StoryChip("compares", "1"), StoryChip("swaps", "0")),
            "3 is smaller than its parent 9, so they swap.",
            "Sift-up compares a node only with its parent, never with its sibling.",
            bubble = 5 to "3 < 9",
        ),
        PqFrame(
            listOf(4, 7, 3, 8, 10, 9), mapOf(2 to PqTone.Active, 5 to PqTone.Swapped), "swap(heap[2], heap[5])",
            listOf(StoryChip("compares", "1"), StoryChip("swaps", "1")),
            "{3} climbs to i2 and 9 drops to i5.", "One level up for one compare.",
        ),
        PqFrame(
            listOf(4, 7, 3, 8, 10, 9), mapOf(2 to PqTone.Active, 0 to PqTone.Compared, 5 to PqTone.Swapped),
            "parent(2) = (2 − 1) / 2 = 0 → {3} < {p:4} → swap", listOf(StoryChip("compares", "2"), StoryChip("swaps", "1")),
            "3 is smaller than its parent 4, so they swap and 3 becomes the new minimum.",
            "It already passed 9 on the way up. Sift-up is at most one compare per level, so O(log n).",
            bubble = 2 to "3 < 4",
        ),
        PqFrame(
            listOf(3, 7, 4, 8, 10, 9), mapOf(0 to PqTone.Active, 2 to PqTone.Swapped, 5 to PqTone.Swapped), "peek() = {v:3}",
            listOf(StoryChip("compares", "2"), StoryChip("swaps", "2"), StoryChip("cost", "O(log n)", StoryTone.Answer)),
            "3 reached the root, so peek() now returns {v:3}.",
            "Two compares for six elements: the work follows the height of the tree, not its size.",
        ),
    )
}

private fun pqExtractFrames(): List<PqFrame> = listOf(
    PqFrame(
        listOf(3, 7, 4, 8, 10, 9), mapOf(0 to PqTone.Active), "extractMin() → {v:3}", listOf(StoryChip("size", "6"), StoryChip("min", "3")),
        "Extract-min returns the root, {3}.",
        "The hard part is filling the hole it leaves without breaking the heap.",
    ),
    PqFrame(
        listOf(9, 7, 4, 8, 10), mapOf(0 to PqTone.Active), "heap[0] = heap[5] = {9}", listOf(StoryChip("size", "6 → 5"), StoryChip("compares", "0")),
        "The last leaf, {9}, moves into the root's slot.",
        "The array shrinks by one and the tree stays complete, but 9 is far too big for the root.",
    ),
    PqFrame(
        listOf(9, 7, 4, 8, 10), mapOf(0 to PqTone.Active, 1 to PqTone.Compared, 2 to PqTone.Compared),
        "min({p:7}, {p:4}) = 4 → {9} > 4 → swap", listOf(StoryChip("compares", "2"), StoryChip("swaps", "0")),
        "9 is bigger than its smaller child, 4, so they swap.",
        "Sift-down picks the smaller child, so the new parent is at most its sibling too.",
        bubble = 2 to "4 < 9",
    ),
    PqFrame(
        listOf(4, 7, 9, 8, 10), mapOf(2 to PqTone.Active, 0 to PqTone.Swapped), "swap(heap[0], heap[2])",
        listOf(StoryChip("compares", "2"), StoryChip("swaps", "1")),
        "4 rises to the root and {9} sinks to i2.", "One level down for one pair of compares.",
    ),
    PqFrame(
        listOf(4, 7, 9, 8, 10), mapOf(2 to PqTone.Active, 0 to PqTone.Swapped), "children(2) = 5, 6 → none → stop",
        listOf(StoryChip("compares", "2"), StoryChip("swaps", "1"), StoryChip("min", "4", StoryTone.Answer)),
        "i2 has no children, so {9} stops and the heap is valid again.",
        "At most one swap per level on the way down, so extract-min is O(log n) too.",
    ),
)

@Composable
private fun pqColors(tone: PqTone?): Pair<Color, Color> {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    return when (tone ?: PqTone.Idle) {
        PqTone.Idle -> muted.copy(alpha = 0.22f) to MaterialTheme.colorScheme.onSurface
        PqTone.Active -> SimColors.Active to Color(0xFF1F1A0A)
        PqTone.Compared -> SimColors.Blue to Color.White
        PqTone.Swapped -> SimColors.Green.copy(alpha = if (LocalDarkTheme.current) 0.24f else 0.18f) to StoryTone.Done.ink()
    }
}

@Composable
internal fun PriorityQueueLabSection() {
    val tabs = remember {
        listOf(
            Triple("Insert", pqInsertFrames(), listOf("Sifting up", "Parent compared", "Swapped down")),
            Triple("Extract min", pqExtractFrames(), listOf("Sifting down", "Child compared", "Swapped up")),
        )
    }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val frames = tabs[tab].second
    val words = tabs[tab].third
    val playback = rememberPlaybackState(key = tab, stepCount = frames.size, initialSpeedMs = 1000f)
    val frame = frames[playback.index.coerceIn(0, frames.lastIndex)]
    val present = frame.tones.values.toSet()

    Column(modifier = Modifier.fillMaxWidth()) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                LabSegments(tabs.map { it.first }, tab) { tab = it }
                PqTree(
                    frame,
                    Modifier
                        .padding(top = 14.dp)
                        .fillMaxWidth()
                        .height(186.dp)
                        .background(Color.Black.copy(alpha = 0.16f), RoundedCornerShape(14.dp)),
                )
                PqStrip(frame, Modifier.padding(top = 12.dp))
                StoryFormula(frame.formula, Modifier.padding(top = 12.dp))
                StoryLegendRow(
                    listOf(
                        Triple(PqTone.Active, SimColors.Active, words[0]),
                        Triple(PqTone.Compared, SimColors.Blue, words[1]),
                        Triple(PqTone.Swapped, SimColors.Green, words[2]),
                    ).filter { it.first in present }.map { Triple(it.second, SwatchStyle.Fill, it.third) },
                    Modifier.padding(top = 14.dp),
                )
            }
        }
        StoryChips(frame.chips, Modifier.padding(top = 16.dp))
        LabStoryNarration(frame.headline, frame.body, Modifier.padding(top = 16.dp))
        PlaybackTransport(playback, captions = frames.map { storyPlain(it.headline) })
    }
}

@Composable
private fun PqTree(frame: PqFrame, modifier: Modifier) {
    val measurer = rememberTextMeasurer()
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val surface = MaterialTheme.colorScheme.surface
    val colors = frame.heap.indices.map { pqColors(frame.tones[it]) }
    val valueStyle = TextStyle(fontFamily = IBMPlexMono, fontWeight = FontWeight.Bold, fontSize = 16.sp)
    val indexStyle = TextStyle(fontFamily = IBMPlexMono, fontSize = 10.sp, color = muted)
    val bubbleStyle = TextStyle(fontFamily = IBMPlexMono, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF1F1A0A))
    Canvas(modifier) {
        val w = 44.dp.toPx()
        val h = 34.dp.toPx()
        fun at(i: Int): Offset {
            val depth = 31 - Integer.numberOfLeadingZeros(i + 1)
            val first = (1 shl depth) - 1
            val slots = (1 shl depth).toFloat()
            return Offset((i - first + 0.5f) * size.width / slots, 30.dp.toPx() + depth * 62.dp.toPx())
        }
        for (i in 1 until frame.heap.size) {
            val lit = frame.bubble?.first == i
            drawLine(
                if (lit) SimColors.Active else muted.copy(alpha = 0.45f), at((i - 1) / 2), at(i),
                if (lit) 2.5.dp.toPx() else 1.5.dp.toPx(),
            )
        }
        frame.heap.forEachIndexed { i, v ->
            val c = at(i)
            val topLeft = Offset(c.x - w / 2, c.y - h / 2)
            val radius = CornerRadius(8.dp.toPx())
            val (fill, ink) = colors[i]
            drawRoundRect(surface, topLeft, Size(w, h), radius)
            drawRoundRect(fill, topLeft, Size(w, h), radius)
            val label = measurer.measure("$v", valueStyle.copy(color = ink))
            drawText(label, topLeft = Offset(c.x - label.size.width / 2f, c.y - label.size.height / 2f))
            val index = measurer.measure("i$i", indexStyle)
            drawText(index, topLeft = Offset(c.x + w / 2 + 3.dp.toPx(), topLeft.y - index.size.height / 2f + 2.dp.toPx()))
        }
        frame.bubble?.let { (child, text) ->
            val a = at((child - 1) / 2)
            val b = at(child)
            val mid = Offset((a.x + b.x) / 2 + if (b.x > a.x) 10.dp.toPx() else -10.dp.toPx(), (a.y + b.y) / 2)
            val layout = measurer.measure(text, bubbleStyle)
            val padX = 6.dp.toPx()
            val padY = 3.dp.toPx()
            drawRoundRect(
                SimColors.Active,
                Offset(mid.x - layout.size.width / 2f - padX, mid.y - layout.size.height / 2f - padY),
                Size(layout.size.width + 2 * padX, layout.size.height + 2 * padY),
                CornerRadius(6.dp.toPx()),
            )
            drawText(layout, topLeft = Offset(mid.x - layout.size.width / 2f, mid.y - layout.size.height / 2f))
        }
    }
}

@Composable
private fun PqStrip(frame: PqFrame, modifier: Modifier) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            for (i in 0 until PQ_CAPACITY) {
                val tone = frame.tones[i]
                Text(
                    "$i",
                    fontFamily = IBMPlexMono,
                    fontSize = 12.sp,
                    fontWeight = if (tone == PqTone.Active || tone == PqTone.Compared) FontWeight.Bold else FontWeight.Normal,
                    color = when (tone) {
                        PqTone.Active -> StoryTone.Active.ink()
                        PqTone.Compared -> StoryTone.Path.ink()
                        else -> muted
                    },
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            for (i in 0 until PQ_CAPACITY) {
                val filled = i < frame.heap.size
                val (fill, ink) = if (filled) pqColors(frame.tones[i]) else muted.copy(alpha = 0.08f) to muted.copy(alpha = 0.6f)
                Box(
                    modifier = Modifier.weight(1f).height(40.dp).background(fill, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(if (filled) "${frame.heap[i]}" else "·", fontFamily = IBMPlexMono, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = ink)
                }
            }
        }
    }
}
