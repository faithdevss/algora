package com.algora.app.feature.topics

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.algora.app.core.ui.theme.IBMPlexMono
import com.algora.app.core.ui.theme.SimColors
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

// ── Doubly linked list story ─────────────────────────────────────────────────
// Insert, delete and reverse as three storyboards over one row of nodes. Each gap holds two arrows,
// next above and prev below; a pointer that skips a node is drawn as an arc over (next) or under
// (prev) the row, labelled with the field being written.

private enum class DllTone { Idle, Hand, Rewired, Fresh }
private enum class DllStyle { Plain, Removed, Rewired }

private class DllNode(val label: String, val tone: DllTone = DllTone.Idle, val tag: String? = null)

/** One pointer. [from] and [to] are row positions; [next] draws it above the row, prev below. */
private class DllLink(val from: Int, val to: Int, val next: Boolean, val style: DllStyle = DllStyle.Plain, val label: String? = null)

private class DllFrame(
    val nodes: List<DllNode>,
    val links: List<DllLink>,
    val formula: String,
    val chips: List<StoryChip>,
    val headline: String,
    val body: String,
)

/** Both pointers between each neighbouring pair, except the gaps a step replaces. */
private fun chain(count: Int, except: Set<Int> = emptySet()) =
    (0 until count - 1).filter { it !in except }.flatMap { listOf(DllLink(it, it + 1, true), DllLink(it + 1, it, false)) }

private fun nodes(values: List<Int>, tones: Map<Int, DllTone> = emptyMap(), tags: Map<Int, String>? = null) =
    values.mapIndexed { i, v ->
        DllNode("$v", tones[i] ?: DllTone.Idle, tags?.get(i) ?: if (tags == null) (if (i == 0) "head" else if (i == values.lastIndex) "tail" else null) else null)
    }

private fun writes(n: Int, answer: Boolean = false) =
    listOf(StoryChip("pointer writes", "$n"), StoryChip("cost", "O(1)", if (answer) StoryTone.Answer else StoryTone.Idle))

private fun dllDelete(): List<DllFrame> {
    val v = listOf(10, 20, 30, 40)
    val ends = mapOf(1 to DllTone.Rewired, 2 to DllTone.Hand, 3 to DllTone.Rewired)
    return listOf(
        DllFrame(
            nodes(v, mapOf(2 to DllTone.Hand)), chain(4), "delete(node 30)", writes(0),
            "You hold a pointer to {30}. Deleting it means joining its two neighbours.",
            "The handle could be a cache entry, or whatever an earlier insert returned.",
        ),
        DllFrame(
            nodes(v, ends), chain(4), "30.prev = {p:20}   30.next = {p:40}", writes(0),
            "30.prev and 30.next hand over both neighbours, {p:20} and {p:40}.",
            "No search: the node already knows who is on either side of it.",
        ),
        DllFrame(
            nodes(v, ends),
            chain(4, setOf(1)) + listOf(
                DllLink(1, 2, true, DllStyle.Removed), DllLink(2, 1, false),
                DllLink(1, 3, true, DllStyle.Rewired, "20.next"),
            ),
            "20.next = {p:40}", writes(1),
            "Write 20.next = {p:40}. Walking forward now skips 30.",
            "The old 20 → 30 link is dropped, but 30 still points at its neighbours.",
        ),
        DllFrame(
            nodes(v, ends),
            chain(4, setOf(1, 2)) + listOf(
                DllLink(1, 2, true, DllStyle.Removed), DllLink(2, 1, false, DllStyle.Removed),
                DllLink(2, 3, true, DllStyle.Removed), DllLink(3, 2, false, DllStyle.Removed),
                DllLink(1, 3, true, DllStyle.Rewired, "20.next"), DllLink(3, 1, false, DllStyle.Rewired, "40.prev"),
            ),
            "20.next = {p:40}   40.prev = {p:20}", writes(2),
            "Holding 30, both neighbours are one hop away, so it unlinks in 2 writes.",
            "That is what the prev pointer buys: a singly linked list has to walk from the head to find 20.",
        ),
        DllFrame(
            nodes(listOf(10, 20, 40), mapOf(1 to DllTone.Rewired, 2 to DllTone.Rewired)), chain(3), "10 ⇄ 20 ⇄ 40", writes(2, true),
            "30 is unlinked. The list reads {v:10 ⇄ 20 ⇄ 40} both ways.",
            "Nothing shifted and nothing walked, so delete-given-a-node is O(1) however long the list is.",
        ),
    )
}

private fun dllInsert(): List<DllFrame> {
    // The new node sits in its slot from the start; the old 20 ⇄ 30 pair arcs around it until rewired.
    val v = listOf(10, 20, 25, 30, 40)
    val tags = mapOf(0 to "head", 2 to "new", 4 to "tail")
    val hold = mapOf(1 to DllTone.Hand, 2 to DllTone.Fresh)
    val rest = chain(5, setOf(1, 2))
    val outward = listOf(DllLink(2, 1, false, DllStyle.Rewired), DllLink(2, 3, true, DllStyle.Rewired))
    return listOf(
        DllFrame(
            nodes(v, hold, tags), rest + listOf(DllLink(1, 3, true), DllLink(3, 1, false)), "insertAfter(node 20, 25)", writes(0),
            "Insert {25} after 20, with 20 already in hand.",
            "The new node is allocated on its own. Nothing in the list moves to make room.",
        ),
        DllFrame(
            nodes(v, hold, tags), rest + listOf(DllLink(1, 3, true), DllLink(3, 1, false)) + outward,
            "25.prev = {p:20}   25.next = {p:30}", writes(2),
            "First the new node points outward: 25.prev = {p:20}, 25.next = {p:30}.",
            "Setting its own fields first means the list is never broken halfway through.",
        ),
        DllFrame(
            nodes(v, hold, tags),
            rest + listOf(DllLink(1, 3, true, DllStyle.Removed), DllLink(3, 1, false)) + outward + DllLink(1, 2, true, DllStyle.Rewired),
            "20.next = {p:25}", writes(3),
            "Then 20.next = {p:25}. Walking forward now reaches the new node.",
            "The old 20 → 30 pointer is replaced, not deleted separately.",
        ),
        DllFrame(
            nodes(v, hold, tags),
            rest + listOf(DllLink(1, 3, true, DllStyle.Removed), DllLink(3, 1, false, DllStyle.Removed)) + outward +
                listOf(DllLink(1, 2, true, DllStyle.Rewired), DllLink(3, 2, false, DllStyle.Rewired)),
            "30.prev = {p:25}", writes(4),
            "Last, 30.prev = {p:25}: four writes and the node is in.",
            "A singly linked list needs two writes here but cannot insert before a node without walking to it.",
        ),
        DllFrame(
            nodes(v, mapOf(2 to DllTone.Rewired), tags), chain(5), "10 ⇄ 20 ⇄ 25 ⇄ 30 ⇄ 40", writes(4, true),
            "25 is linked both ways: {v:10 ⇄ 20 ⇄ 25 ⇄ 30 ⇄ 40}.",
            "O(1) once you hold 20. Finding 20 in the first place is still a walk, O(n).",
        ),
    )
}

private fun dllReverse(): List<DllFrame> {
    val v = listOf(10, 20, 30, 40)
    val frames = mutableListOf(
        DllFrame(
            nodes(v), chain(4), "reverse()", listOf(StoryChip("swapped", "0 of 4"), StoryChip("cost", "O(n)")),
            "Reversing swaps next and prev inside every node.",
            "No node moves in memory. Only the two pointers in each node trade places.",
        ),
    )
    for (i in v.indices) {
        val tones = (0 until i).associateWith { DllTone.Rewired } + (i to DllTone.Hand)
        frames += DllFrame(
            nodes(v, tones), chain(4), "swap(${v[i]}.next, ${v[i]}.prev)",
            listOf(StoryChip("swapped", "${i + 1} of ${v.size}"), StoryChip("pointer writes", "${2 * (i + 1)}")),
            "Swap {${v[i]}}'s pointers: next becomes prev, prev becomes next.",
            when (i) {
                0 -> "The old head's prev was null, so its next is now null: it will be the tail."
                v.lastIndex -> "The old tail's next was null, so it becomes the new head."
                else -> "Each node takes two writes, so the pass is O(n)."
            },
        )
    }
    val r = v.reversed()
    frames += DllFrame(
        nodes(r, r.indices.associateWith { DllTone.Rewired }), chain(4), "head = {v:40}   tail = {v:10}",
        listOf(StoryChip("pointer writes", "8"), StoryChip("cost", "O(n)", StoryTone.Answer)),
        "Swap head and tail, and the list reads {v:40 ⇄ 30 ⇄ 20 ⇄ 10}.",
        "Two writes per node plus the two ends. The same loop over a singly linked list has to carry a prev variable along.",
    )
    return frames
}

private class DllTab(val label: String, val frames: List<DllFrame>, val legend: List<Triple<Color, SwatchStyle, String>>)

@Composable
internal fun DoublyLinkedLabSection() {
    val tabs = remember {
        listOf(
            DllTab(
                "Insert", dllInsert(),
                listOf(
                    Triple(SimColors.Active, SwatchStyle.Fill, "Node in hand"),
                    Triple(SimColors.Answer, SwatchStyle.Dashed, "New node"),
                    Triple(SimColors.Blue, SwatchStyle.Fill, "Pointer rewired"),
                    Triple(SimColors.Red, SwatchStyle.Fill, "Link removed"),
                ),
            ),
            DllTab(
                "Delete", dllDelete(),
                listOf(
                    Triple(SimColors.Active, SwatchStyle.Fill, "Node in hand"),
                    Triple(SimColors.Blue, SwatchStyle.Fill, "Pointer rewired"),
                    Triple(SimColors.Red, SwatchStyle.Fill, "Link removed"),
                ),
            ),
            DllTab(
                "Reverse", dllReverse(),
                listOf(Triple(SimColors.Active, SwatchStyle.Fill, "Swapping"), Triple(SimColors.Blue, SwatchStyle.Fill, "Swapped")),
            ),
        )
    }
    var tab by rememberSaveable { mutableIntStateOf(1) }
    val frames = tabs[tab].frames
    val playback = rememberPlaybackState(key = tab, stepCount = frames.size, initialSpeedMs = 900f)
    val frame = frames[playback.index.coerceIn(0, frames.lastIndex)]

    Column(modifier = Modifier.fillMaxWidth()) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                LabSegments(tabs.map { it.label }, tab) { tab = it }
                DllStage(
                    frame,
                    Modifier
                        .padding(top = 14.dp)
                        .fillMaxWidth()
                        .height(170.dp)
                        .background(Color.Black.copy(alpha = 0.16f), RoundedCornerShape(14.dp)),
                )
                StoryFormula(frame.formula, Modifier.padding(top = 12.dp))
                StoryLegendRow(tabs[tab].legend, Modifier.padding(top = 14.dp))
            }
        }
        StoryChips(frame.chips, Modifier.padding(top = 16.dp))
        LabStoryNarration(frame.headline, frame.body, Modifier.padding(top = 16.dp))
        PlaybackTransport(playback, captions = frames.map { storyPlain(it.headline) })
    }
}

@Composable
private fun DllStage(frame: DllFrame, modifier: Modifier) {
    val measurer = rememberTextMeasurer()
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val onSurface = MaterialTheme.colorScheme.onSurface
    val surface = MaterialTheme.colorScheme.surface
    val pathInk = StoryTone.Path.ink()
    val freshInk = StoryTone.Answer.ink()
    val nodeStyle = TextStyle(fontFamily = IBMPlexMono, fontWeight = FontWeight.Bold, fontSize = 17.sp)
    val labelStyle = TextStyle(fontFamily = IBMPlexMono, fontWeight = FontWeight.Bold, fontSize = 12.sp)
    val tagStyle = TextStyle(fontFamily = IBMPlexMono, fontSize = 11.sp)
    Canvas(modifier) {
        val n = frame.nodes.size
        val w = 50.dp.toPx()
        val h = 44.dp.toPx()
        val pad = 12.dp.toPx()
        val step = (size.width - 2 * pad - w) / maxOf(n - 1, 1)
        val cy = size.height / 2 - 6.dp.toPx()
        fun cx(i: Int) = pad + w / 2 + i * step
        fun color(s: DllStyle) = when (s) {
            DllStyle.Removed -> SimColors.Red
            DllStyle.Rewired -> SimColors.Blue
            DllStyle.Plain -> muted.copy(alpha = 0.7f)
        }
        fun DrawScope.head(tip: Offset, angle: Float, c: Color) {
            val len = 7.dp.toPx()
            val p = Path().apply {
                moveTo(tip.x, tip.y)
                lineTo(tip.x - len * cos(angle - 0.45f), tip.y - len * sin(angle - 0.45f))
                lineTo(tip.x - len * cos(angle + 0.45f), tip.y - len * sin(angle + 0.45f))
                close()
            }
            drawPath(p, c)
        }
        // Links first, so the tiles sit on top of them.
        frame.links.forEach { link ->
            val c = color(link.style)
            val effect = if (link.style == DllStyle.Removed) PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())) else null
            val width = if (link.style == DllStyle.Plain) 1.5.dp.toPx() else 2.dp.toPx()
            val dir = if (link.to > link.from) 1f else -1f
            if (abs(link.to - link.from) == 1) {
                val y = cy + if (link.next) -7.dp.toPx() else 7.dp.toPx()
                val a = Offset(cx(link.from) + dir * (w / 2 + 3.dp.toPx()), y)
                val b = Offset(cx(link.to) - dir * (w / 2 + 3.dp.toPx()), y)
                drawLine(c, a, b, width, pathEffect = effect)
                head(b, if (dir > 0) 0f else Math.PI.toFloat(), c)
            } else {
                // A pointer that skips nodes arcs over (next) or under (prev) the row.
                val sign = if (link.next) -1f else 1f
                val a = Offset(cx(link.from), cy + sign * h / 2)
                val b = Offset(cx(link.to), cy + sign * h / 2)
                val ctrl = Offset((a.x + b.x) / 2, cy + sign * (h / 2 + 52.dp.toPx()))
                val p = Path().apply { moveTo(a.x, a.y); quadraticTo(ctrl.x, ctrl.y, b.x, b.y) }
                drawPath(p, c, style = Stroke(width, pathEffect = effect))
                head(b, atan2(b.y - ctrl.y, b.x - ctrl.x), c)
                link.label?.let { text ->
                    val layout = measurer.measure(text, labelStyle.copy(color = pathInk))
                    val apexY = (a.y + 2 * ctrl.y + b.y) / 4 + sign * 10.dp.toPx()
                    drawText(layout, topLeft = Offset(ctrl.x - layout.size.width / 2f, apexY - layout.size.height / 2f))
                }
            }
        }
        frame.nodes.forEachIndexed { i, node ->
            val topLeft = Offset(cx(i) - w / 2, cy - h / 2)
            val box = Size(w, h)
            val radius = CornerRadius(9.dp.toPx())
            val (fill, ink) = when (node.tone) {
                DllTone.Idle -> muted.copy(alpha = 0.22f) to onSurface
                DllTone.Hand -> SimColors.Active to Color(0xFF1F1A0A)
                DllTone.Rewired -> SimColors.Blue.copy(alpha = 0.3f) to onSurface
                DllTone.Fresh -> SimColors.Answer.copy(alpha = 0.2f) to freshInk
            }
            drawRoundRect(surface, topLeft, box, radius)
            drawRoundRect(fill, topLeft, box, radius)
            if (node.tone == DllTone.Rewired) drawRoundRect(SimColors.Blue, topLeft, box, radius, style = Stroke(1.5.dp.toPx()))
            if (node.tone == DllTone.Fresh) {
                drawRoundRect(
                    SimColors.Answer, topLeft, box, radius,
                    style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))),
                )
            }
            val label = measurer.measure(node.label, nodeStyle.copy(color = ink))
            drawText(label, topLeft = Offset(cx(i) - label.size.width / 2f, cy - label.size.height / 2f))
            node.tag?.let { tag ->
                val layout = measurer.measure(tag, tagStyle.copy(color = if (node.tone == DllTone.Fresh) freshInk else muted))
                drawText(layout, topLeft = Offset(cx(i) - layout.size.width / 2f, cy + h / 2 + 5.dp.toPx()))
            }
        }
    }
}
