package com.algora.app.feature.topics

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.algora.app.core.ui.theme.IBMPlexMono
import com.algora.app.core.ui.theme.SimColors

// ── List ADT lab ─────────────────────────────────────────────────────────────
// One insert(i, 5) run against an array and a linked list at three positions: the array pays in
// shifts after i, the list pays in hops before it. Front, middle and end are tabs.

private enum class AdtTone { Idle, Written, Shifted, Walked, Empty }

private class AdtCell(val text: String, val tone: AdtTone, val caption: String? = null)

private class AdtFrame(
    val array: List<AdtCell>,
    val linked: List<AdtCell>,
    // The linked arrows drawn in violet: arrow k leaves node k.
    val newArrows: Set<Int>,
    val rows: List<StoryFormulaRow>,
    val chips: List<StoryChip>,
    val headline: String,
    val body: String,
)

private fun adtFrames(p: Int): List<AdtFrame> {
    val values = listOf(10, 20, 30, 40, 50)
    val n = values.size
    val v = 5
    val shifts = n - p
    val hops = maxOf(p - 1, 0)
    val writes = if (p == n) 1 else 2
    val arrayCost = if (shifts == 0) "O(1)" else "O(n)"
    val linkedCost = if (hops == 0) "O(1)" else "O(n)"
    val call = StoryChip("call", "insert($p, $v)")
    fun arrayRow(written: Boolean) = (0..n).map { i ->
        val src = if (i < p) i else i - 1
        when {
            i == p -> if (written) AdtCell("$v", AdtTone.Written) else AdtCell("", AdtTone.Empty)
            src >= n -> AdtCell("", AdtTone.Empty)
            i > p -> AdtCell("${values[src]}", AdtTone.Shifted, "+1")
            else -> AdtCell("${values[src]}", AdtTone.Idle)
        }
    }
    val startArray = values.map { AdtCell("$it", AdtTone.Idle) } + AdtCell("", AdtTone.Empty)
    val walked = values.mapIndexed { i, x -> AdtCell("$x", if (p > 0 && i < p) AdtTone.Walked else AdtTone.Idle) }
    val inserted = values.map { AdtCell("$it", AdtTone.Idle) }.toMutableList().apply { add(p, AdtCell("$v", AdtTone.Written)) }
    val arrows = when (p) {
        0 -> setOf(0)
        n -> setOf(p - 1)
        else -> setOf(p - 1, p)
    }
    val shiftText = if (shifts == 1) "1 shift" else "$shifts shifts"
    val hopText = if (hops == 1) "1 hop" else "$hops hops"
    val room: String
    val write: String
    val why: String
    val verdict: String
    when (p) {
        0 -> {
            room = "The array moves all $n elements up a slot; the list needs no walk."
            write = "insert(0, $v): the array shifts all $n elements up a slot first."
            why = "The linked list writes new.next = ${values[0]} and head = $v. At position 0 it wins; at the end the array wins."
            verdict = "At the front the linked list wins: {v:$writes writes} against $shiftText."
        }
        n -> {
            room = "Appending needs no shifts, but the list has to walk to ${values[n - 1]}."
            write = "insert($n, $v): the array drops $v into its free slot."
            why = "The linked list walks $hopText to ${values[n - 1]}, then writes ${values[n - 1]}.next = $v. A tail pointer would skip the walk."
            verdict = "At the end the array wins: {v:no shifts} against a $hopText walk."
        }
        else -> {
            room = "The array shifts ${values.drop(p).joinToString(", ")} up; the list walks to ${values[p - 1]}."
            write = "insert($p, $v): $shiftText for the array, $hopText and $writes writes for the list."
            why = "Both costs grow with the list: the array's with what comes after i, the list's with what comes before."
            verdict = "In the middle both pay: $shiftText against {v:$hopText + $writes writes}."
        }
    }
    val linkedRow = "${if (hops > 0) "$hopText + " else ""}$writes pointer writes · $linkedCost"
    val costRows = listOf(StoryFormulaRow("array", "$shiftText · $arrayCost"), StoryFormulaRow("linked", linkedRow))
    val arrayWins = shifts == 0
    val verdictRows = listOf(
        StoryFormulaRow("array", if (arrayWins) "{v:$shiftText · $arrayCost}" else "$shiftText · $arrayCost"),
        StoryFormulaRow("linked", if (!arrayWins && p == 0) "{v:$linkedRow}" else linkedRow),
    )
    return listOf(
        AdtFrame(
            startArray, values.map { AdtCell("$it", AdtTone.Idle) }, emptySet(),
            listOf(StoryFormulaRow("array", "$n of ${n + 1} slots"), StoryFormulaRow("linked", "$n nodes from head")),
            listOf(call), "Same list, two layouts: an array of slots and a chain of nodes.",
            "Both answer insert($p, $v) with the same list. The work behind it is what differs.",
        ),
        AdtFrame(
            arrayRow(false), walked, emptySet(),
            listOf(StoryFormulaRow("array", shiftText), StoryFormulaRow("linked", hopText)),
            listOf(call, StoryChip("shifts", "$shifts"), StoryChip("hops", "$hops")), room,
            "An array keeps its elements side by side, so room has to be made. A list only has to find the node before the spot.",
        ),
        AdtFrame(arrayRow(true), inserted, arrows, costRows, listOf(call, StoryChip("shifts", "$shifts"), StoryChip("writes", "$writes")), write, why),
        AdtFrame(
            arrayRow(true), inserted, arrows, verdictRows,
            listOf(call, StoryChip("winner", if (arrayWins) "array" else if (p == 0) "linked" else "neither", StoryTone.Answer)), verdict,
            "Neither layout is faster everywhere. The list ADT hides which one you have, so the position you insert at decides the cost.",
        ),
    )
}

@Composable
internal fun ListAdtLabSection() {
    val tabs = remember { listOf("Front" to adtFrames(0), "Middle" to adtFrames(2), "End" to adtFrames(5)) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val frames = tabs[tab].second
    val playback = rememberPlaybackState(key = tab, stepCount = frames.size, initialSpeedMs = 1000f)
    val frame = frames[playback.index.coerceIn(0, frames.lastIndex)]
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val tones = (frame.array + frame.linked).map { it.tone }.toSet()

    Column(modifier = Modifier.fillMaxWidth()) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                LabSegments(tabs.map { it.first }, tab) { tab = it }
                SectionLabel("ARRAY", Modifier.padding(top = 14.dp))
                Row(modifier = Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    frame.array.indices.forEach {
                        Text("$it", fontFamily = IBMPlexMono, fontSize = 11.sp, color = muted, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                    }
                }
                Row(modifier = Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    frame.array.forEach { cell ->
                        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            AdtTile(cell, Modifier.fillMaxWidth())
                            Text(
                                cell.caption ?: " ",
                                fontFamily = IBMPlexMono, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = StoryTone.Path.ink(),
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                }
                SectionLabel("LINKED", Modifier.padding(top = 8.dp))
                Row(modifier = Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    frame.linked.forEachIndexed { i, cell ->
                        AdtTile(cell, Modifier.weight(1f))
                        if (i < frame.linked.lastIndex) {
                            Text(
                                "→",
                                fontFamily = IBMPlexMono, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                                color = if (i in frame.newArrows) StoryTone.Answer.ink() else muted,
                                modifier = Modifier.padding(horizontal = 2.dp),
                            )
                        }
                    }
                }
                StoryFormulaRows(frame.rows, Modifier.padding(top = 14.dp))
                StoryLegendRow(
                    listOf(
                        Triple(AdtTone.Written, SimColors.Answer, "Written"),
                        Triple(AdtTone.Shifted, SimColors.Blue, "Shifted"),
                        Triple(AdtTone.Walked, SimColors.Active, "Walked"),
                    ).filter { it.first in tones }.map { Triple(it.second, SwatchStyle.Fill, it.third) },
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
private fun SectionLabel(text: String, modifier: Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

@Composable
private fun AdtTile(cell: AdtCell, modifier: Modifier) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val onSurface = MaterialTheme.colorScheme.onSurface
    val shape = RoundedCornerShape(8.dp)
    val (fill, ink) = when (cell.tone) {
        AdtTone.Idle -> muted.copy(alpha = 0.2f) to onSurface
        AdtTone.Written -> SimColors.Answer to Color.White
        AdtTone.Shifted -> SimColors.Blue.copy(alpha = 0.3f) to onSurface
        AdtTone.Walked -> SimColors.Active to Color(0xFF1F1A0A)
        AdtTone.Empty -> muted.copy(alpha = 0.08f) to muted
    }
    Box(
        modifier = modifier
            .height(42.dp)
            .background(fill, shape)
            .then(
                when (cell.tone) {
                    AdtTone.Shifted -> Modifier.border(1.5.dp, SimColors.Blue, shape)
                    AdtTone.Empty -> Modifier.dashedOutline(muted.copy(alpha = 0.35f), 8.dp)
                    else -> Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(cell.text, fontFamily = IBMPlexMono, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = ink, maxLines = 1)
    }
}
