package com.algora.app.feature.topics

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.algora.app.core.data.model.Figure
import com.algora.app.core.data.model.FigureLayer
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
    // Full strength, not a faded onSurfaceVariant: every caller already fades it for fills, and the
    // same value is used for the *text* inside a muted band — which went unreadable on dark.
    FigureTone.Muted -> MaterialTheme.colorScheme.onSurfaceVariant
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
                is FigureShape.Graph -> GraphFigure(shape)
                is FigureShape.Plot -> PlotFigure(shape)
                is FigureShape.LayerStack -> LayerStackFigure(shape)
                is FigureShape.Heatmap -> HeatmapFigure(shape)
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
            arrow.label?.let { label ->
                val layout = textMeasurer.measure(label, headerStyle.copy(color = colour))
                // Perpendicular to the arrow and scaled to the cell, so the name clears both the line
                // and the values it passes over — a fixed pixel offset lands on top of them.
                val push = minOf(cellW, cellH) * 0.34f
                drawText(
                    layout,
                    topLeft = Offset(
                        (start.x + end.x) / 2f - layout.size.width / 2f + uy * push,
                        (start.y + end.y) / 2f - layout.size.height / 2f - ux * push,
                    ),
                )
            }
        }
    }
}

@Composable
private fun GraphFigure(shape: FigureShape.Graph) {
    val textMeasurer = rememberTextMeasurer()
    val onSurface = MaterialTheme.colorScheme.onSurface
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val toneFor = FigureTone.entries.associateWith { toneColor(it) }
    val nodeStyle = TextStyle(color = onSurface, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    val edgeStyle = TextStyle(color = muted, fontSize = 9.sp, fontWeight = FontWeight.Medium)

    // A U-shaped nine-node graph needs more vertical room than a three-node one, and the height is
    // what the node radius is derived from — so a dense graph gets a taller card rather than
    // circles that touch. The emulator pass found `unet` and `dependency_parsing` colliding at 132.
    val height = if (shape.nodes.size > 6) 168.dp else 132.dp

    Canvas(modifier = Modifier.fillMaxWidth().height(height)) {
        val inset = 22f
        fun centreOf(index: Int) = Offset(
            inset + shape.nodes[index].x * (size.width - 2 * inset),
            inset + shape.nodes[index].y * (size.height - 2 * inset),
        )
        // Proportional, not a fixed pixel count: a node carries a label like "A c1" or "F 17", and a
        // circle sized in raw pixels leaves that text hanging outside it on a dense screen.
        //
        // Capped by the closest pair, because a fixed fraction of the height cannot work for both a
        // three-node block diagram and `unet`'s nine-node U — and enlarging the card does not help,
        // since the radius is derived from the height it grows with. The first emulator pass found
        // four graphs drawing themselves as overlapping blobs at a flat 15%.
        val tightest = shape.nodes.indices.flatMap { a ->
            ((a + 1) until shape.nodes.size).map { b ->
                val from = centreOf(a)
                val to = centreOf(b)
                kotlin.math.hypot(from.x - to.x, from.y - to.y)
            }
        }.minOrNull() ?: Float.MAX_VALUE
        val radius = minOf(size.height * 0.15f, tightest * 0.45f).coerceAtLeast(size.height * 0.07f)

        shape.edges.forEach { edge ->
            val from = centreOf(edge.from)
            val to = centreOf(edge.to)
            val colour = toneFor.getValue(edge.tone)
            val dx = to.x - from.x
            val dy = to.y - from.y
            val length = kotlin.math.hypot(dx, dy).coerceAtLeast(1f)
            val ux = dx / length
            val uy = dy / length
            val start = Offset(from.x + ux * radius, from.y + uy * radius)
            val end = Offset(to.x - ux * radius, to.y - uy * radius)
            drawLine(colour, start, end, strokeWidth = 2f)
            if (edge.directed) {
                val head = 8f
                drawLine(colour, end, Offset(end.x - (ux + uy) * head, end.y - (uy - ux) * head), strokeWidth = 2f)
                drawLine(colour, end, Offset(end.x - (ux - uy) * head, end.y - (uy + ux) * head), strokeWidth = 2f)
            }
            edge.label?.let { label ->
                val layout = textMeasurer.measure(label, edgeStyle)
                val mid = Offset((start.x + end.x) / 2f, (start.y + end.y) / 2f)
                // Clear the line by half the text's own height, not by a fixed 9px: at 9sp the box
                // is ~24px tall, so the old nudge left every label sitting on its own edge —
                // `crop 4`, `F(x)` and all six dependency arcs came back struck through.
                val clearance = layout.size.height / 2f + 6f
                drawText(
                    layout,
                    topLeft = Offset(
                        mid.x - layout.size.width / 2f + uy * clearance,
                        mid.y - layout.size.height / 2f - ux * clearance,
                    ),
                )
            }
        }

        shape.nodes.forEachIndexed { index, node ->
            val tone = toneFor.getValue(node.tone)
            val centre = centreOf(index)
            drawCircle(tone.copy(alpha = 0.24f), radius = radius, center = centre)
            drawCircle(tone, radius = radius, center = centre, style = Stroke(width = 1.8f))
            // Shrink a long label to fit rather than letting it hang outside the circle: `conv 3×3`
            // and `chased` both overflowed at a fixed 10sp. The circle cannot grow instead — a
            // wider one collides with its neighbours, which is the failure this pass came to fix.
            val room = 2f * radius - 8f
            var layout = textMeasurer.measure(node.label, nodeStyle)
            if (layout.size.width > room) {
                layout = textMeasurer.measure(node.label, nodeStyle.copy(fontSize = 8.sp))
            }
            if (layout.size.width > room) {
                layout = textMeasurer.measure(node.label, nodeStyle.copy(fontSize = 7.sp))
            }
            drawText(
                layout,
                topLeft = Offset(centre.x - layout.size.width / 2f, centre.y - layout.size.height / 2f),
            )
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

    val nodeStyle = TextStyle(fontSize = 9.sp, fontWeight = FontWeight.Bold)
    val widestLabel = shape.nodes.maxOf { textMeasurer.measure(it.label, nodeStyle).size.width }

    Canvas(modifier = Modifier.fillMaxWidth().height(34.dp * depth + 8.dp)) {
        val rowHeight = size.height / depth
        val slotWidth = size.width / slots
        // Wide enough for the longest label — a B-tree node reads "30 | 60", and a circle sized from
        // the row height alone leaves that text hanging outside it.
        val radius = minOf(
            maxOf(rowHeight * 0.30f, widestLabel / 2f + 7f),
            slotWidth * 0.46f,
        )

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
                nodeStyle.copy(color = if (node.tone == FigureTone.Muted) onSurface else tone),
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

// The legend and the axis names are composables around the canvas rather than text inside it: a
// series label drawn at the end of its own curve lands on top of the next curve as soon as two of
// them converge, which is exactly what an optimiser comparison does.
@Composable
private fun PlotFigure(shape: FigureShape.Plot) {
    val textMeasurer = rememberTextMeasurer()
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val axisColor = muted.copy(alpha = 0.35f)
    val toneFor = FigureTone.entries.associateWith { toneColor(it) }
    val tickStyle = TextStyle(color = muted, fontSize = 9.sp, fontWeight = FontWeight.Medium)

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            shape.yLabel?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = muted)
            }
            // A FlowRow, not a Row: three series with names as long as "with momentum" overrun the
            // width left beside the y-label, and a plain Row lets the last one wrap on top of its
            // neighbour instead of moving to a line of its own.
            FlowRow(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                verticalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                shape.series.filter { it.label.isNotBlank() }.forEach { series ->
                    Text(
                        series.label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = toneFor.getValue(series.tone),
                        maxLines = 1,
                    )
                }
            }
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(124.dp)
                .padding(top = 4.dp),
        ) {
            // Bars carry their names under the axis; a curve plot gives that strip back to the plot.
            val bottomPad = if (shape.bars.isEmpty()) 6f else 24f
            val leftPad = 6f
            val plotW = size.width - leftPad - 8f
            val plotH = size.height - bottomPad - 8f
            val baseline = 8f + plotH

            fun at(x: Float, y: Float) = Offset(leftPad + x * plotW, 8f + (1f - y) * plotH)

            drawLine(axisColor, Offset(leftPad, 8f), Offset(leftPad, baseline), strokeWidth = 1.5f)
            drawLine(axisColor, Offset(leftPad, baseline), Offset(size.width, baseline), strokeWidth = 1.5f)

            shape.series.forEach { series ->
                val colour = toneFor.getValue(series.tone)
                val effect = if (series.dashed) {
                    PathEffect.dashPathEffect(floatArrayOf(9f, 7f))
                } else {
                    null
                }
                series.points.zipWithNext().forEach { (a, b) ->
                    drawLine(
                        colour,
                        at(a.x, a.y),
                        at(b.x, b.y),
                        strokeWidth = 2.4f,
                        pathEffect = effect,
                    )
                }
            }

            shape.bars.forEachIndexed { index, bar ->
                val colour = toneFor.getValue(bar.tone)
                val slot = plotW / shape.bars.size
                val width = slot * 0.56f
                val centre = leftPad + (index + 0.5f) * slot
                val height = (bar.value * plotH).coerceAtLeast(2f)
                drawRoundRect(
                    color = colour.copy(alpha = 0.85f),
                    topLeft = Offset(centre - width / 2f, baseline - height),
                    size = Size(width, height),
                    cornerRadius = CornerRadius(5f, 5f),
                )
                val layout = textMeasurer.measure(bar.label, tickStyle)
                drawText(
                    layout,
                    topLeft = Offset(centre - layout.size.width / 2f, baseline + 5f),
                )
            }

            shape.markers.forEach { point ->
                val colour = toneFor.getValue(point.tone)
                val centre = at(point.x, point.y)
                drawCircle(colour, radius = 4.5f, center = centre)
                point.label?.let { label ->
                    val layout = textMeasurer.measure(label, tickStyle.copy(color = colour))
                    // Above the point, and pulled back inside the canvas when it sits near the right
                    // edge — a called-out minimum is often the last point of the curve.
                    val x = (centre.x - layout.size.width / 2f)
                        .coerceIn(0f, (size.width - layout.size.width).coerceAtLeast(0f))
                    drawText(layout, topLeft = Offset(x, centre.y - layout.size.height - 6f))
                }
            }
        }

        shape.xLabel?.let {
            Text(
                it,
                style = MaterialTheme.typography.labelSmall,
                color = muted,
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                textAlign = TextAlign.Center,
            )
        }
    }
}

// Composables rather than a canvas: a layer block is a label over a shape line, and text laid out by
// Compose wraps and measures itself where text drawn onto a canvas has to be told how wide it may be.
@Composable
private fun LayerStackFigure(shape: FigureShape.LayerStack) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    @Composable
    fun Block(layer: FigureLayer, modifier: Modifier) {
        val tone = toneColor(layer.tone)
        Column(
            modifier = modifier
                .background(tone.copy(alpha = 0.16f), RoundedCornerShape(9.dp))
                .padding(horizontal = if (shape.horizontal) 2.dp else 8.dp, vertical = 7.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                layer.label,
                // A quarter of the card is not much room for a word like "discriminator", and Compose
                // breaks mid-word rather than overflowing — "discri / minat / or". The smaller style
                // is what keeps a horizontal block's name on one line.
                style = if (shape.horizontal) {
                    // 10sp, not labelSmall's 11: a quarter of a 1080px card leaves about 185px of
                    // usable width, and "discriminator" needs every one of them.
                    MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp)
                } else {
                    MaterialTheme.typography.labelMedium
                },
                fontWeight = FontWeight.Bold,
                color = if (layer.tone == FigureTone.Muted) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    tone
                },
                textAlign = TextAlign.Center,
            )
            layer.detail?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = muted,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }

    @Composable
    fun Flow(text: String) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = muted.copy(alpha = 0.7f))
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (shape.horizontal) {
            // IntrinsicSize.Min so every block is as tall as the tallest one. Without it each column
            // sizes to its own text and the row reads as a ragged staircase rather than a pipeline.
            Row(
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                shape.layers.forEachIndexed { index, layer ->
                    Block(layer, Modifier.weight(1f).fillMaxHeight())
                    if (index != shape.layers.lastIndex) Flow("→")
                }
            }
        } else {
            shape.layers.forEachIndexed { index, layer ->
                Block(layer, Modifier.fillMaxWidth())
                if (index != shape.layers.lastIndex) Flow("↓")
            }
        }

        // The return path is one line under the stack rather than an arrow beside it: a vertical
        // gutter costs width every figure would rather spend on the layer labels themselves.
        shape.backwardLabel?.let {
            Text(
                if (shape.horizontal) "←  $it  ←" else "↑  $it  ↑",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = toneColor(FigureTone.Accent),
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

// Square cells, not cells that stretch to the card. An attention matrix read as a rectangle
// misrepresents the symmetry of the thing — the diagonal stops being a diagonal.
@Composable
private fun HeatmapFigure(shape: FigureShape.Heatmap) {
    val textMeasurer = rememberTextMeasurer()
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val ramp = toneColor(shape.tone)
    val gridColor = muted.copy(alpha = 0.18f)
    val markColor = toneColor(FigureTone.Accent)
    val toneFor = FigureTone.entries.associateWith { toneColor(it) }
    val headerStyle = TextStyle(color = muted, fontSize = 9.sp, fontWeight = FontWeight.Bold)

    val rows = shape.values.size
    val cols = shape.values.maxOf { it.size }
    val hasRowLabels = shape.rowLabels.isNotEmpty()
    val hasColLabels = shape.colLabels.isNotEmpty()

    Column(modifier = Modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(26.dp * rows + if (hasColLabels) 16.dp else 0.dp),
        ) {
            val labelWidth = if (hasRowLabels) size.width * 0.16f else 0f
            val labelHeight = if (hasColLabels) size.height * 0.14f else 0f
            val cell = minOf((size.width - labelWidth) / cols, (size.height - labelHeight) / rows)
            // Left-aligned under the row labels rather than centred: a wide card would otherwise
            // float the matrix away from the names down its side.
            val originX = labelWidth
            val originY = labelHeight

            if (hasColLabels) {
                shape.colLabels.forEachIndexed { col, text ->
                    val layout = textMeasurer.measure(text, headerStyle)
                    drawText(
                        layout,
                        topLeft = Offset(
                            originX + (col + 0.5f) * cell - layout.size.width / 2f,
                            labelHeight / 2f - layout.size.height / 2f,
                        ),
                    )
                }
            }

            shape.values.forEachIndexed { row, cells ->
                if (hasRowLabels && row < shape.rowLabels.size) {
                    val layout = textMeasurer.measure(shape.rowLabels[row], headerStyle)
                    drawText(
                        layout,
                        topLeft = Offset(
                            labelWidth - layout.size.width - 4f,
                            originY + (row + 0.5f) * cell - layout.size.height / 2f,
                        ),
                    )
                }
                cells.forEachIndexed { col, value ->
                    val topLeft = Offset(originX + col * cell, originY + row * cell)
                    drawRect(gridColor, topLeft = topLeft, size = Size(cell - 1.5f, cell - 1.5f), style = Stroke(1f))
                    drawRect(
                        color = ramp.copy(alpha = value.coerceIn(0f, 1f) * 0.9f),
                        topLeft = topLeft,
                        size = Size(cell - 1.5f, cell - 1.5f),
                    )
                }
            }

            shape.marks.forEach { mark ->
                // Honour the mark's tone: `value_function` outlines a goal, a pit and a wall on one
                // grid, and drawing all three in the same colour made two blank cells that mean
                // different things look identical. Primary keeps the old accent so nothing else moves.
                val outline = if (mark.tone == FigureTone.Primary) markColor else toneFor.getValue(mark.tone)
                drawRect(
                    color = outline,
                    topLeft = Offset(originX + mark.col * cell, originY + mark.row * cell),
                    size = Size(cell - 1.5f, cell - 1.5f),
                    style = Stroke(width = 2f),
                )
            }
        }

        shape.legend?.let {
            Text(
                it,
                style = MaterialTheme.typography.labelSmall,
                color = muted,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}
