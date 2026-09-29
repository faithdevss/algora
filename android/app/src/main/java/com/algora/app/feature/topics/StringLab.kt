package com.algora.app.feature.topics

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import com.algora.app.core.ui.theme.LocalDarkTheme
import com.algora.app.core.ui.theme.SimColors

// ── String lab ───────────────────────────────────────────────────────────────
// Strings as arrays of character codes, three ways to read them: comparison stops at the first
// difference, equality has to read everything, a substring copies its slice. Each tab is its own
// storyboard over rows of character cells.

private enum class StrTone { Idle, Active, Match, Range, Written, Missing }

private class StrCell(val text: String, val tone: StrTone)

/** A labelled row whose first cell sits under column [offset] (a substring under its source). */
private class StrRow(val label: String, val offset: Int, val cells: List<StrCell>)

private class StrFrame(
    val rows: List<StrRow>,
    val column: Int?,
    // One or two centred lines; `{…}` marks colour a term (see LabStory).
    val formula: List<String>,
    val chips: List<StoryChip>,
    val headline: String,
    val body: String,
)

private class StrVariant(
    val label: String,
    val columns: Int,
    val frames: List<StrFrame>,
    // The step the tab opens on (Compare opens on the deciding character).
    val start: Int,
    val legend: List<Pair<Color, String>>,
)

private fun code(c: Char) = c.code
private fun quoted(s: String) = "\"$s\""

private fun compareVariant(): StrVariant {
    val a = "algora"
    val b = "algebra"
    val decide = a.indices.first { a[it] != b[it] }
    val shorter = minOf(a.length, b.length)
    fun row(label: String, s: String, upTo: Int) = StrRow(
        label, 0,
        (0 until b.length).map { j ->
            if (j >= s.length) StrCell("·", StrTone.Missing)
            else StrCell("${s[j]}", if (j < upTo) StrTone.Match else if (j == upTo) StrTone.Active else StrTone.Idle)
        },
    )
    val verdict = "${quoted(a)} > ${quoted(b)}"
    val frames = mutableListOf<StrFrame>()
    for (i in 0..decide) {
        val chips = listOf(
            StoryChip("comparisons", "${i + 1} of $shorter"),
            if (i == decide) StoryChip("verdict", "a > b", StoryTone.Answer) else StoryChip("verdict", "–"),
        )
        frames += if (i < decide) {
            StrFrame(
                listOf(row("a", a, i), row("b", b, i)), i,
                listOf("'${a[i]}' (${code(a[i])}) = '${b[i]}' (${code(b[i])}) → next"), chips,
                "Index $i: '${a[i]}' matches '${b[i]}', so keep going.",
                if (i == 0) "Strings compare left to right, one character code at a time."
                else "Every equal pair pushes the decision one index further.",
            )
        } else {
            StrFrame(
                listOf(row("a", a, i), row("b", b, i)), i,
                listOf("'${a[i]}' (${code(a[i])}) > '${b[i]}' (${code(b[i])}) →", "{v:$verdict}"), chips,
                "Index $i decides: '${a[i]}' comes after '${b[i]}', so ${quoted(a)} sorts later.",
                "Comparison stops at the first difference. Only equal strings pay the full O(n).",
            )
        }
    }
    frames += StrFrame(
        listOf(row("a", a, decide), row("b", b, decide)), decide, listOf("{v:$verdict}"),
        listOf(
            StoryChip("comparisons", "${decide + 1} of $shorter"),
            StoryChip("unread", "${shorter - decide - 1}"),
            StoryChip("verdict", "a > b", StoryTone.Answer),
        ),
        "${decide + 1} of $shorter comparisons settle it: {v:$verdict}.",
        "The rest of both strings is never read. Sorting a list of strings leans on this early exit.",
    )
    return StrVariant(
        "Compare", b.length, frames, decide,
        listOf(SimColors.Active to "Deciding character", SimColors.Green to "Equal so far", SimColors.Answer to "Result"),
    )
}

private fun equalsVariant(): StrVariant {
    val a = "algora"
    val n = a.length
    fun row(label: String, upTo: Int) = StrRow(
        label, 0,
        a.indices.map { StrCell("${a[it]}", if (it < upTo) StrTone.Match else if (it == upTo) StrTone.Active else StrTone.Idle) },
    )
    val frames = mutableListOf(
        StrFrame(
            listOf(row("a", -1), row("b", -1)), null, listOf("length $n = length $n → read the characters"),
            listOf(StoryChip("length", "$n = $n"), StoryChip("verdict", "–")),
            "Both strings have $n characters, so the loop has to run.",
            "Different lengths would answer false here without reading a single character.",
        ),
    )
    for (i in 0 until n) {
        frames += StrFrame(
            listOf(row("a", i), row("b", i)), i, listOf("'${a[i]}' (${code(a[i])}) = '${a[i]}' (${code(a[i])})"),
            listOf(StoryChip("comparisons", "${i + 1} of $n"), StoryChip("verdict", "–")),
            if (i == n - 1) "Index $i matches too, the last character." else "Index $i matches. Equal so far, keep reading.",
            "A match cannot end the loop. Only a difference, or running out of characters, can.",
        )
    }
    frames += StrFrame(
        listOf(row("a", n), row("b", n)), null, listOf("$n of $n equal →", "{v:${quoted(a)} == ${quoted(a)}}"),
        listOf(StoryChip("comparisons", "$n of $n"), StoryChip("verdict", "equal", StoryTone.Answer)),
        "Every character matched, so the strings are {v:equal}.",
        "Equal strings are the worst case: all n characters are read, O(n).",
    )
    return StrVariant(
        "Equals", n, frames, 0,
        listOf(SimColors.Active to "Comparing", SimColors.Green to "Equal so far", SimColors.Answer to "Result"),
    )
}

private fun substringVariant(): StrVariant {
    val s = "algora"
    val from = 2
    val to = 5
    val k = to - from
    val slice = s.substring(from, to)
    fun rows(copied: Int, current: Int?): List<StrRow> {
        val src = s.indices.map { j ->
            val tone = when {
                current != null && j == from + current -> StrTone.Active
                j in from until to -> StrTone.Range
                else -> StrTone.Idle
            }
            StrCell("${s[j]}", tone)
        }
        val dst = (0 until k).map { j ->
            when {
                j < copied -> StrCell("${s[from + j]}", StrTone.Written)
                j == current -> StrCell("${s[from + j]}", StrTone.Active)
                else -> StrCell("", StrTone.Missing)
            }
        }
        return listOf(StrRow("s", 0, src), StrRow("sub", from, dst))
    }
    val frames = mutableListOf(
        StrFrame(
            rows(0, null), null, listOf("s.substring($from, $to)"),
            listOf(StoryChip("range", "$from..<$to"), StoryChip("copied", "0 of $k")),
            "substring($from, $to) asks for indices $from up to $to.",
            "The end index is exclusive, so the slice is $k characters long.",
        ),
    )
    for (j in 0 until k) {
        frames += StrFrame(
            rows(j, j), from + j, listOf("sub[$j] = s[${from + j}] = '${s[from + j]}'"),
            listOf(StoryChip("range", "$from..<$to"), StoryChip("copied", "${j + 1} of $k")),
            "Copy '${s[from + j]}' from s[${from + j}] into the new string.",
            if (j == 0) "The slice gets fresh storage. The original string is left untouched."
            else "One write per character, so the copy grows with the slice, not with s.",
        )
    }
    frames += StrFrame(
        rows(k, null), null, listOf("{v:${quoted(slice)}}"),
        listOf(StoryChip("copied", "$k of $k"), StoryChip("cost", "O(k)", StoryTone.Answer)),
        "The result is a new string, {v:${quoted(slice)}}.",
        "Most languages copy the slice, so a substring of k characters costs O(k), not O(1).",
    )
    return StrVariant(
        "Substring", s.length, frames, 0,
        listOf(SimColors.Active to "Copying", SimColors.Blue to "In range", SimColors.Green to "Copied", SimColors.Answer to "Result"),
    )
}

@Composable
internal fun StringLabSection() {
    val variants = remember { listOf(compareVariant(), equalsVariant(), substringVariant()) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val variant = variants[tab]
    val playback = rememberPlaybackState(key = tab, stepCount = variant.frames.size)
    // Each tab opens on its own first step; a fresh state per tab starts there once.
    remember(playback) { playback.index = variant.start; true }
    val frame = variant.frames[playback.index.coerceIn(0, variant.frames.lastIndex)]
    val onSurface = MaterialTheme.colorScheme.onSurface

    Column(modifier = Modifier.fillMaxWidth()) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                LabSegments(variants.map { it.label }, tab) { tab = it }
                StrGrid(variant, frame, Modifier.padding(top = 14.dp))
                Column(
                    modifier = Modifier
                        .padding(top = 12.dp)
                        .fillMaxWidth()
                        .background(SimColors.Tint, RoundedCornerShape(10.dp))
                        .padding(vertical = 12.dp, horizontal = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    frame.formula.forEachIndexed { i, line ->
                        Text(
                            storyAnnotated(line),
                            fontFamily = IBMPlexMono,
                            fontSize = 14.sp,
                            fontWeight = if (frame.formula.size > 1 && i == frame.formula.lastIndex) FontWeight.Bold else FontWeight.Normal,
                            color = onSurface.copy(alpha = 0.8f),
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                        )
                    }
                }
                StoryLegendRow(variant.legend.map { Triple(it.first, SwatchStyle.Fill, it.second) }, Modifier.padding(top = 14.dp))
            }
        }
        StoryChips(frame.chips, Modifier.padding(top = 16.dp))
        LabStoryNarration(frame.headline, frame.body, Modifier.padding(top = 16.dp))
        PlaybackTransport(playback, captions = variant.frames.map { storyPlain(it.headline) })
    }
}

@Composable
private fun StrGrid(variant: StrVariant, frame: StrFrame, modifier: Modifier = Modifier) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val gutter = 22.dp
    val gap = 6.dp
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
            Spacer(Modifier.width(gutter))
            for (c in 0 until variant.columns) {
                val on = c == frame.column
                Text(
                    "$c",
                    fontFamily = IBMPlexMono,
                    fontSize = 12.sp,
                    fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                    color = if (on) StoryTone.Active.ink() else muted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        frame.rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(gap), verticalAlignment = Alignment.CenterVertically) {
                Text(row.label, fontFamily = IBMPlexMono, fontSize = 13.sp, color = muted, maxLines = 1, modifier = Modifier.width(gutter))
                for (c in 0 until variant.columns) {
                    val cell = row.cells.getOrNull(c - row.offset)
                    if (cell == null || c < row.offset) Spacer(Modifier.weight(1f).height(44.dp)) else StrCellView(cell, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun StrCellView(cell: StrCell, modifier: Modifier) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val onSurface = MaterialTheme.colorScheme.onSurface
    val dark = LocalDarkTheme.current
    val shape = RoundedCornerShape(8.dp)
    val (fill, ink) = when (cell.tone) {
        StrTone.Idle -> muted.copy(alpha = 0.2f) to onSurface
        StrTone.Active -> SimColors.Active to Color(0xFF1F1A0A)
        StrTone.Match -> SimColors.Green.copy(alpha = if (dark) 0.22f else 0.16f) to StoryTone.Done.ink()
        StrTone.Range -> SimColors.Blue.copy(alpha = 0.28f) to onSurface
        StrTone.Written -> SimColors.Green to Color.White
        StrTone.Missing -> muted.copy(alpha = 0.08f) to muted.copy(alpha = 0.6f)
    }
    Box(
        modifier = modifier
            .height(44.dp)
            .background(fill, shape)
            .then(if (cell.tone == StrTone.Range) Modifier.border(1.5.dp, SimColors.Blue, shape) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Text(cell.text, fontFamily = IBMPlexMono, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = ink)
    }
}
