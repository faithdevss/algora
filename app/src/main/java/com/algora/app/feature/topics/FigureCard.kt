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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.algora.app.core.data.model.Figure
import com.algora.app.core.data.model.FigureShape
import com.algora.app.core.data.model.FigureTone
import com.algora.app.core.ui.theme.SimColors

// ── Figure card ──────────────────────────────────────────────────────────────
// The still picture above a topic's How-It-Works steps. Deliberately not a fifth simulation widget:
// no playback, no state, one frame. It exists because a reader arriving at a pattern page has to
// press play and watch before learning what the invariant even is.
//
// Chrome is the card shell every other section already uses (18dp corners, 1dp outline, surface fill)
// — the mock has no diagram precedent, so a figure has to read as an existing card type rather than
// as something new. Tones resolve here, against the live scheme, so the same spec reads in both
// themes.

@Composable
private fun toneColor(tone: FigureTone): Color = when (tone) {
    FigureTone.Primary -> SimColors.Blue
    FigureTone.Accent -> Color(0xFFF59E0B)
    FigureTone.Muted -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
    FigureTone.Warn -> SimColors.Red
}

@Composable
internal fun FigureCard(figure: Figure, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            when (val shape = figure.shape) {
                is FigureShape.Strip -> StripFigure(shape)
                is FigureShape.Timeline -> TimelineFigure(shape)
                is FigureShape.Grid -> GridFigure(shape)
                is FigureShape.Stacks -> StacksFigure(shape)
                is FigureShape.Tree -> TreeFigure(shape)
            }
            Text(
                figure.caption,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}

@Composable
private fun StripFigure(shape: FigureShape.Strip) {
    val count = shape.cells.size
    val bandTone = shape.bands.associate { band ->
        band to toneColor(band.tone)
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        if (shape.bands.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                var index = 0
                while (index < count) {
                    val band = shape.bands.firstOrNull { index in it.from..it.to }
                    if (band == null) {
                        Box(modifier = Modifier.weight(1f))
                        index++
                    } else {
                        val width = band.to - band.from + 1
                        Box(
                            modifier = Modifier.weight(width.toFloat()),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                band.label,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = bandTone.getValue(band),
                            )
                        }
                        index += width
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 3.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            shape.cells.forEachIndexed { i, text ->
                val band = shape.bands.firstOrNull { i in it.from..it.to }
                val fill = band?.let { bandTone.getValue(it) } ?: MaterialTheme.colorScheme.onSurfaceVariant
                val inBand = band != null
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                        .background(
                            fill.copy(alpha = if (inBand) 0.22f else 0.10f),
                            RoundedCornerShape(8.dp),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (inBand) FontWeight.Bold else FontWeight.Normal,
                        color = if (inBand) fill else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }

        if (shape.pointers.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                for (i in 0 until count) {
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        shape.pointers.firstOrNull { it.index == i }?.let { pointer ->
                            Text(
                                "▲${pointer.label}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = toneColor(pointer.tone),
                            )
                        }
                    }
                }
            }
        }

        if (shape.aux.isNotEmpty()) {
            shape.auxLabel?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 9.dp),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                shape.aux.forEach { text ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(28.dp)
                            .background(
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.10f),
                                RoundedCornerShape(7.dp),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                // Keep the aux cells the same width as the row above when it is shorter.
                repeat(count - shape.aux.size) { Box(modifier = Modifier.weight(1f)) }
            }
        }
    }
}

// Drawn on one canvas rather than as nested Rows: the arrows are the point of most of these figures,
// and an arrow between two cells of a Compose grid has nowhere to live.
@Composable
private fun GridFigure(shape: FigureShape.Grid) {
    val rows = shape.rows.size
    val cols = shape.rows.maxOf { it.size }
    val textMeasurer = rememberTextMeasurer()
    val onSurface = MaterialTheme.colorScheme.onSurface
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val idleFill = muted.copy(alpha = 0.10f)
    val toneFor = FigureTone.entries.associateWith { toneColor(it) }
    val cellStyle = TextStyle(color = onSurface, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    val headerStyle = TextStyle(color = muted, fontSize = 9.sp, fontWeight = FontWeight.Bold)

    val hasRowHeaders = shape.rowHeaders.isNotEmpty()
    val hasColHeaders = shape.colHeaders.isNotEmpty()
    // 30dp per row plus the header strip; wide tables simply get shorter cells, never a scroll.
    val height = 30.dp * rows + if (hasColHeaders) 16.dp else 0.dp

    Canvas(modifier = Modifier.fillMaxWidth().height(height)) {
        val headerWidth = if (hasRowHeaders) size.width * 0.14f else 0f
        val headerHeight = if (hasColHeaders) size.height * 0.14f else 0f
        val cellW = (size.width - headerWidth) / cols
        val cellH = (size.height - headerHeight) / rows

        fun centreOf(row: Int, col: Int) =
            Offset(headerWidth + (col + 0.5f) * cellW, headerHeight + (row + 0.5f) * cellH)

        if (hasColHeaders) {
            shape.colHeaders.forEachIndexed { col, text ->
                val layout = textMeasurer.measure(text, headerStyle)
                drawText(
                    layout,
                    topLeft = Offset(
                        headerWidth + (col + 0.5f) * cellW - layout.size.width / 2f,
                        headerHeight / 2f - layout.size.height / 2f,
                    ),
                )
            }
        }

        shape.rows.forEachIndexed { row, cells ->
            if (hasRowHeaders && row < shape.rowHeaders.size) {
                val layout = textMeasurer.measure(shape.rowHeaders[row], headerStyle)
                drawText(
                    layout,
                    topLeft = Offset(
                        headerWidth / 2f - layout.size.width / 2f,
                        headerHeight + (row + 0.5f) * cellH - layout.size.height / 2f,
                    ),
                )
            }
            cells.forEachIndexed { col, text ->
                val mark = shape.marks.firstOrNull { it.row == row && it.col == col }
                val tone = mark?.let { toneFor.getValue(it.tone) }
                val centre = centreOf(row, col)
                drawRoundRect(
                    color = tone?.copy(alpha = 0.22f) ?: idleFill,
                    topLeft = Offset(centre.x - cellW / 2f + 2f, centre.y - cellH / 2f + 2f),
                    size = Size(cellW - 4f, cellH - 4f),
                    cornerRadius = CornerRadius(6f, 6f),
                )
                val layout = textMeasurer.measure(
                    text,
                    if (tone == null) cellStyle else cellStyle.copy(color = tone, fontWeight = FontWeight.Bold),
                )
                drawText(
                    layout,
                    topLeft = Offset(centre.x - layout.size.width / 2f, centre.y - layout.size.height / 2f),
                )
            }
        }

        shape.arrows.forEach { arrow ->
            val from = centreOf(arrow.fromRow, arrow.fromCol)
            val to = centreOf(arrow.toRow, arrow.toCol)
            val colour = toneFor.getValue(arrow.tone)
            val dx = to.x - from.x
            val dy = to.y - from.y
            val length = kotlin.math.hypot(dx, dy).coerceAtLeast(1f)
            // Stop short of the target so the head sits beside the cell, not on top of its text.
            val inset = minOf(cellW, cellH) * 0.34f
            val start = Offset(from.x + dx / length * inset, from.y + dy / length * inset)
            val end = Offset(to.x - dx / length * inset, to.y - dy / length * inset)
            drawLine(colour, start, end, strokeWidth = 2f)
            val headSize = 7f
            val ux = dx / length
            val uy = dy / length
            drawLine(colour, end, Offset(end.x - (ux + uy) * headSize, end.y - (uy - ux) * headSize), strokeWidth = 2f)
            drawLine(colour, end, Offset(end.x - (ux - uy) * headSize, end.y - (uy + ux) * headSize), strokeWidth = 2f)
        }
    }
}

// Same leaf-slot layout the recursion-tree player uses: leaves take sequential x slots and every
// internal node centres over its children, so a lopsided tree still reads as one.
@Composable
private fun TreeFigure(shape: FigureShape.Tree) {
    val textMeasurer = rememberTextMeasurer()
    val edgeColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.40f)
    val onSurface = MaterialTheme.colorScheme.onSurface
    val toneFor = FigureTone.entries.associateWith { toneColor(it) }

    val childrenOf = shape.nodes.indices.groupBy { shape.nodes[it].parent }
    val depthOf = IntArray(shape.nodes.size)
    shape.nodes.forEachIndexed { index, node ->
        depthOf[index] = node.parent?.let { depthOf[it] + 1 } ?: 0
    }
    val depth = (depthOf.maxOrNull() ?: 0) + 1
    val leaves = shape.nodes.indices.filter { childrenOf[it].isNullOrEmpty() }
    val slotOf = HashMap<Int, Float>()
    leaves.forEachIndexed { slot, index -> slotOf[index] = slot.toFloat() }
    // Parents after their children: indices descend, so every child already has a slot.
    shape.nodes.indices.reversed().forEach { index ->
        val kids = childrenOf[index].orEmpty()
        if (kids.isNotEmpty()) slotOf[index] = kids.map { slotOf.getValue(it) }.average().toFloat()
    }
    val slots = leaves.size.coerceAtLeast(1)

    Canvas(modifier = Modifier.fillMaxWidth().height(34.dp * depth + 8.dp)) {
        val rowHeight = size.height / depth
        val slotWidth = size.width / slots
        val radius = minOf(rowHeight * 0.30f, slotWidth * 0.42f)

        fun centreOf(index: Int) = Offset(
            (slotOf.getValue(index) + 0.5f) * slotWidth,
            (depthOf[index] + 0.5f) * rowHeight,
        )

        shape.nodes.forEachIndexed { index, node ->
            node.parent?.let { drawLine(edgeColor, centreOf(it), centreOf(index), strokeWidth = 1.6f) }
        }
        shape.nodes.forEachIndexed { index, node ->
            val tone = toneFor.getValue(node.tone)
            val centre = centreOf(index)
            drawCircle(tone.copy(alpha = 0.22f), radius = radius, center = centre)
            drawCircle(tone, radius = radius, center = centre, style = Stroke(width = 1.6f))
            val layout = textMeasurer.measure(
                node.label,
                TextStyle(
                    color = if (node.tone == FigureTone.Muted) onSurface else tone,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
            drawText(
                layout,
                topLeft = Offset(centre.x - layout.size.width / 2f, centre.y - layout.size.height / 2f),
            )
        }
    }
}

@Composable
private fun StacksFigure(shape: FigureShape.Stacks) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        shape.columns.forEach { column ->
            val tone = toneColor(column.tone)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    column.label,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = tone,
                )
                column.entries.forEachIndexed { index, entry ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(30.dp)
                            .background(
                                tone.copy(alpha = if (index == 0) 0.26f else 0.12f),
                                RoundedCornerShape(8.dp),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            entry,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (index == 0) FontWeight.Bold else FontWeight.Normal,
                            color = if (index == 0) tone else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
                column.note?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun TimelineFigure(shape: FigureShape.Timeline) {
    val fills = shape.spans.map { toneColor(it.tone) }
    val markerColor = MaterialTheme.colorScheme.primary
    val axisColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
    val span = shape.axisMax.coerceAtLeast(1).toFloat()
    val rowHeight = 22.dp

    Column(modifier = Modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(rowHeight * shape.spans.size + 8.dp),
        ) {
            val barHeight = size.height / shape.spans.size
            shape.spans.forEachIndexed { index, spanItem ->
                val left = spanItem.start / span * size.width
                val right = spanItem.end / span * size.width
                drawRoundRect(
                    color = fills[index].copy(alpha = 0.85f),
                    topLeft = Offset(left, index * barHeight + barHeight * 0.18f),
                    size = Size((right - left).coerceAtLeast(8f), barHeight * 0.64f),
                    cornerRadius = CornerRadius(7f, 7f),
                )
            }
            drawLine(
                axisColor,
                Offset(0f, size.height - 1f),
                Offset(size.width, size.height - 1f),
                strokeWidth = 1.5f,
            )
            shape.marker?.let { at ->
                val x = at / span * size.width
                drawLine(markerColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 2.5f)
            }
        }

        Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
            shape.spans.forEach { spanItem ->
                Text(
                    spanItem.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        shape.markerLabel?.let {
            Text(
                it,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}
