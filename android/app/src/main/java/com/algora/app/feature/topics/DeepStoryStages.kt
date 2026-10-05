package com.algora.app.feature.topics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.algora.app.core.ui.theme.IBMPlexMono
import com.algora.app.core.ui.theme.SimColors

// ── Deep-learning storyboard stages: grids, layer rows, segment bars ─────────
// The CNN labs' stages. Cell grids (an input, a kernel, a feature map, a pooled map, a patch grid), rows
// of per-layer bars (parameters against compute, frozen against training), and segment bars (a cost
// split into parts, a layer's input channels coloured by source).

// ── Grids ──

/** Zero is a quiet 0; Pos and Neg are blue and pink scaled by [DkCell.level]; Empty and Pad draw no
 *  number; Current is the cell being written (yellow tint and ring); Embedded and Hot fill solid. */
// Ink fills the cell with [DkCell.ink] (a segmentation label).
// Heat is violet by [DkCell.level] (an attention weight).
internal enum class DkCellTone { Zero, Pos, Neg, Empty, Pad, Current, Embedded, Hot, Ink, Heat }

internal class DkCell(val text: String, val tone: DkCellTone, val level: Float = 1f, val ink: DkInk? = null)

/** A rectangle over cells [r0..r1] × [c0..c1]: the yellow window, or a dashed grey outline. */
internal class DkBox(val r0: Int, val c0: Int, val r1: Int, val c1: Int, val dashed: Boolean = false)

internal class DkGrid(
    val title: String,
    val rows: Int,
    val cols: Int,
    val cells: List<DkCell>,
    val boxes: List<DkBox> = emptyList(),
    val maxCell: Float = 30f,
    val note: String? = null,
    // Labels left of each row and above each column (a query's tokens, a key's tokens); [hotRow] is yellow.
    val rowLabels: List<String> = emptyList(),
    val colLabels: List<String> = emptyList(),
    val hotRow: Int? = null,
)

/** Columns of grids side by side (an input beside a kernel over an output), sharing the width by [weights]. */
internal class DkGrids(
    val columns: List<List<DkGrid>>,
    val weights: List<Float>,
    // Notes in a column after the grids ("466 of 784 on"), with [sideWeight] of the width.
    val side: List<String> = emptyList(),
    val sideWeight: Float = 1f,
) : DkStage

@Composable
internal fun DkGridsView(stage: DkGrids) {
    Row(
        modifier = Modifier.fillMaxWidth().dkStage().padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        stage.columns.forEachIndexed { i, column ->
            Column(modifier = Modifier.weight(stage.weights[i]), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                column.forEach { DkGridView(it) }
            }
        }
        if (stage.side.isNotEmpty()) DkSideNotes(stage.side, Modifier.weight(stage.sideWeight))
    }
}

@Composable
private fun DkGridView(grid: DkGrid) {
    val measurer = rememberTextMeasurer()
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (grid.title.isNotEmpty()) Text(grid.title, fontFamily = IBMPlexMono, fontSize = 11.sp, color = muted, maxLines = 1)
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val rowLabelW = if (grid.rowLabels.isEmpty()) 0.dp else 44.dp
            val cell = minOf(grid.maxCell.dp, (maxWidth - rowLabelW) / grid.cols)
            Column {
            if (grid.colLabels.isNotEmpty()) {
                Row(modifier = Modifier.padding(start = rowLabelW)) {
                    grid.colLabels.forEach { Text(it, fontFamily = IBMPlexMono, fontSize = 10.sp, color = muted, textAlign = TextAlign.Center, maxLines = 1, modifier = Modifier.width(cell)) }
                }
            }
            Row {
            if (grid.rowLabels.isNotEmpty()) {
                Column(modifier = Modifier.width(rowLabelW)) {
                    grid.rowLabels.forEachIndexed { i, l ->
                        Box(modifier = Modifier.width(rowLabelW).height(cell).padding(end = 6.dp), contentAlignment = Alignment.CenterEnd) {
                            Text(
                                l, fontFamily = IBMPlexMono, fontSize = 10.sp, maxLines = 1,
                                color = if (i == grid.hotRow) SimColors.Active else muted,
                                fontWeight = if (i == grid.hotRow) FontWeight.Bold else FontWeight.Normal,
                            )
                        }
                    }
                }
            }
            Canvas(modifier = Modifier.size(cell * grid.cols, cell * grid.rows)) {
                val c = cell.toPx()
                val inset = 1.5.dp.toPx()
                val radius = CornerRadius(3.dp.toPx())
                val textSize = (c * 0.4f).coerceIn(8.dp.toPx(), 15.dp.toPx()).toSp()
                for (r in 0 until grid.rows) for (k in 0 until grid.cols) {
                    val cellData = grid.cells[r * grid.cols + k]
                    val topLeft = Offset(k * c + inset, r * c + inset)
                    val size = Size(c - 2 * inset, c - 2 * inset)
                    val level = cellData.level.coerceIn(0f, 1f)
                    val fill = when (cellData.tone) {
                        DkCellTone.Zero -> Color.White.copy(alpha = 0.06f)
                        DkCellTone.Pos -> dkColor(DkInk.Blue).copy(alpha = 0.35f + 0.6f * level)
                        DkCellTone.Neg -> dkColor(DkInk.Pink).copy(alpha = 0.35f + 0.6f * level)
                        DkCellTone.Empty -> Color.White.copy(alpha = 0.05f)
                        DkCellTone.Pad -> Color.White.copy(alpha = 0.025f)
                        DkCellTone.Current -> SimColors.Active.copy(alpha = 0.3f)
                        DkCellTone.Embedded -> dkColor(DkInk.Blue).copy(alpha = 0.8f)
                        DkCellTone.Hot -> SimColors.Active
                        DkCellTone.Ink -> dkColor(cellData.ink ?: DkInk.Slate)
                        DkCellTone.Heat -> SimColors.Answer.copy(alpha = 0.12f + 0.8f * level)
                    }
                    drawRoundRect(fill, topLeft, size, radius)
                    if (cellData.tone == DkCellTone.Current) {
                        drawRoundRect(SimColors.Active, topLeft, size, radius, style = Stroke(2.dp.toPx()))
                    }
                    if (cellData.text.isNotEmpty() && cellData.tone != DkCellTone.Empty && cellData.tone != DkCellTone.Pad) {
                        // Four-character values ("0.07", "−3.8") shrink to fit their cell.
                        val size = if (cellData.text.length >= 4) minOf(textSize.value, (c * 0.27f).toSp().value).sp else textSize
                        val style = TextStyle(
                            fontFamily = IBMPlexMono, fontSize = size,
                            fontWeight = if (cellData.tone == DkCellTone.Zero) FontWeight.Normal else FontWeight.Bold,
                        )
                        val layout = measurer.measure(cellData.text, style)
                        drawText(
                            layout, color = if (cellData.tone == DkCellTone.Zero) muted.copy(alpha = 0.7f) else Color.White,
                            topLeft = Offset(k * c + (c - layout.size.width) / 2, r * c + (c - layout.size.height) / 2),
                        )
                    }
                }
                grid.boxes.forEach { b ->
                    val topLeft = Offset(b.c0 * c, b.r0 * c)
                    val size = Size((b.c1 - b.c0 + 1) * c, (b.r1 - b.r0 + 1) * c)
                    if (b.dashed) {
                        drawRoundRect(
                            muted.copy(alpha = 0.6f), topLeft, size, CornerRadius(4.dp.toPx()),
                            style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))),
                        )
                    } else {
                        drawRoundRect(SimColors.Active, topLeft, size, CornerRadius(4.dp.toPx()), style = Stroke(2.dp.toPx()))
                    }
                }
            }
            }
            }
        }
        grid.note?.let { Text(it, fontFamily = IBMPlexMono, fontSize = 11.sp, color = muted, modifier = Modifier.padding(top = 4.dp)) }
    }
}

// ── Layer rows ──

/** One bar: [frac] of the track filled in [ink]; a null [frac] draws only the label ("no params"). */
internal class DkBar(val frac: Double?, val ink: DkInk, val label: String)

/** A titled row and its bars, stacked or (when [pair]) side by side; [hot] inks the title yellow. */
internal class DkRow(
    val title: String,
    val meta: String,
    val bars: List<DkBar>,
    val hot: Boolean = false,
    val pair: Boolean = false,
    // One line: a mono label, the bar, the value ("e b ──── 0.198 kept"); [dim] mutes a pruned row.
    val inline: Boolean = false,
    val dim: Boolean = false,
)

internal class DkRows(val rows: List<DkRow>, val caption: String? = null) : DkStage

@Composable
internal fun DkRowsView(stage: DkRows) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        stage.caption?.let { Text(it, fontSize = 13.sp, color = muted) }
        stage.rows.forEach { row ->
            if (row.inline) {
                DkInlineRow(row)
                return@forEach
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        row.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
                        color = if (row.hot) SimColors.Active else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    Text(row.meta, fontFamily = IBMPlexMono, fontSize = 12.sp, color = muted, maxLines = 1)
                }
                if (row.pair) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.bars.forEach { DkBarView(it, Modifier.weight(1f)) }
                    }
                } else {
                    row.bars.forEach { DkBarView(it, Modifier.fillMaxWidth()) }
                }
            }
        }
    }
}

@Composable
private fun DkBarView(bar: DkBar, modifier: Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.weight(1f).height(6.dp).background(SimColors.Tint, RoundedCornerShape(3.dp))) {
            bar.frac?.let { f ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth(f.toFloat().coerceIn(0.012f, 1f))
                        .fillMaxHeight()
                        .background(dkColor(bar.ink), RoundedCornerShape(3.dp)),
                )
            }
        }
        Text(
            bar.label, fontFamily = IBMPlexMono, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End, maxLines = 2, modifier = Modifier.width(64.dp),
        )
    }
}

// ── Segment bars ──

internal class DkSeg(val units: Double, val ink: DkInk, val dim: Boolean = false)

/** A bar of [segs] on a shared scale; [label] sits in a left column, [caption] above, [value] after or inside. */
internal class DkSegRow(
    val segs: List<DkSeg>,
    val label: String? = null,
    val caption: String? = null,
    val value: String? = null,
    val hot: Boolean = false,
)

internal class DkSegs(val rows: List<DkSegRow>, val scale: Double, val notes: List<String> = emptyList(), val barHeight: Int = 22) : DkStage

@Composable
internal fun DkSegsView(stage: DkSegs) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val labelled = stage.rows.any { it.label != null }
    Column(
        modifier = Modifier.fillMaxWidth().dkStage().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        stage.rows.forEach { row ->
            row.caption?.let { Text(it, fontFamily = IBMPlexMono, fontSize = 11.sp, color = muted, modifier = Modifier.padding(top = 4.dp)) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (labelled) {
                    Text(
                        row.label ?: "", fontFamily = IBMPlexMono, fontSize = 11.sp, maxLines = 1,
                        fontWeight = if (row.hot) FontWeight.Bold else FontWeight.Normal,
                        color = if (row.hot) SimColors.Active else muted,
                        textAlign = TextAlign.End, modifier = Modifier.width(30.dp).padding(end = 6.dp),
                    )
                }
                BoxWithConstraints(modifier = Modifier.weight(1f)) {
                    val avail = maxWidth
                    val gap = 2.dp
                    val widths = row.segs.map { maxOf(3.dp, avail * (it.units / stage.scale).toFloat()) }
                    val total = widths.fold(0.dp) { a, w -> a + w } + gap * (row.segs.size - 1).coerceAtLeast(0)
                    val inside = row.value != null && row.segs.size == 1 && widths[0] > 90.dp
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Row(
                            modifier = if (row.hot) Modifier.border(1.5.dp, SimColors.Active, RoundedCornerShape(5.dp)).padding(1.5.dp) else Modifier,
                            horizontalArrangement = Arrangement.spacedBy(gap),
                        ) {
                            row.segs.forEachIndexed { i, seg ->
                                Box(
                                    modifier = Modifier
                                        .width(widths[i])
                                        .height(stage.barHeight.dp)
                                        .background(dkColor(seg.ink).copy(alpha = if (seg.dim) 0.6f else 1f), RoundedCornerShape(4.dp)),
                                    contentAlignment = Alignment.CenterEnd,
                                ) {
                                    if (inside) {
                                        Text(
                                            row.value!!, fontFamily = IBMPlexMono, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                                            color = Color.White, modifier = Modifier.padding(end = 8.dp),
                                        )
                                    }
                                }
                            }
                        }
                        if (row.value != null && !inside && total < avail - 40.dp) {
                            Text(
                                row.value, fontFamily = IBMPlexMono, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                                color = Color.White, modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                    }
                }
            }
        }
        if (stage.notes.isNotEmpty()) {
            Column(modifier = Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                stage.notes.forEach { Text(storyAnnotated(it), fontFamily = IBMPlexMono, fontSize = 11.sp, color = muted) }
            }
        }
    }
}

/** Notes beside a stage, one per line; an empty string is a gap. Marks colour a value ("{m:0 px}"). */
@Composable
private fun DkSideNotes(lines: List<String>, modifier: Modifier) {
    Column(modifier = modifier.padding(top = 2.dp)) {
        lines.forEach { line ->
            if (line.isEmpty()) {
                Box(Modifier.height(10.dp))
            } else {
                Text(
                    storyAnnotated(line), fontFamily = IBMPlexMono, fontSize = 11.sp, lineHeight = 15.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ── Box scenes ──

/** A box in image pixels. [fill] washes it in its ink; [bins] draws an n×n lattice inside (an RoI's bins). */
internal class DkRect(
    val x1: Double,
    val y1: Double,
    val x2: Double,
    val y2: Double,
    val ink: DkInk,
    val dashed: Boolean = false,
    val fill: Boolean = false,
    val thin: Boolean = false,
    val label: String? = null,
    val bins: Int = 0,
)

/** An image of [size]×[size] pixels, optionally cut by a [grid]×[grid] lattice, with boxes and centre dots. */
internal class DkBoxes(
    val size: Double,
    val grid: Int,
    val rects: List<DkRect>,
    val dots: List<DkP> = emptyList(),
    val side: List<String> = emptyList(),
) : DkStage

@Composable
internal fun DkBoxesView(stage: DkBoxes) {
    val measurer = rememberTextMeasurer()
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(modifier = Modifier.fillMaxWidth().dkStage().padding(10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        BoxWithConstraints(modifier = Modifier.weight(if (stage.side.isEmpty()) 1f else 1.7f)) {
            val side = minOf(maxWidth, 260.dp)
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Canvas(modifier = Modifier.size(side)) {
                    val k = size.width / stage.size.toFloat()
                    drawRect(Color.White.copy(alpha = 0.05f))
                    if (stage.grid > 0) {
                        val step = size.width / stage.grid
                        for (i in 1 until stage.grid) {
                            drawLine(Color.White.copy(alpha = 0.09f), Offset(i * step, 0f), Offset(i * step, size.height), 1.dp.toPx())
                            drawLine(Color.White.copy(alpha = 0.09f), Offset(0f, i * step), Offset(size.width, i * step), 1.dp.toPx())
                        }
                    }
                    stage.rects.forEach { r ->
                        val color = dkColor(r.ink)
                        val topLeft = Offset((r.x1 * k).toFloat(), (r.y1 * k).toFloat())
                        val sz = Size(((r.x2 - r.x1) * k).toFloat(), ((r.y2 - r.y1) * k).toFloat())
                        if (r.fill) drawRect(color.copy(alpha = 0.35f), topLeft, sz)
                        if (r.bins > 0) {
                            for (b in 1 until r.bins) {
                                val x = topLeft.x + sz.width * b / r.bins
                                val y = topLeft.y + sz.height * b / r.bins
                                drawLine(color.copy(alpha = 0.55f), Offset(x, topLeft.y), Offset(x, topLeft.y + sz.height), 1.dp.toPx())
                                drawLine(color.copy(alpha = 0.55f), Offset(topLeft.x, y), Offset(topLeft.x + sz.width, y), 1.dp.toPx())
                            }
                        }
                        if (!r.fill || r.dashed) {
                            drawRect(
                                color.copy(alpha = if (r.thin) 0.6f else 1f), topLeft, sz,
                                style = Stroke(
                                    (if (r.thin) 1f else 2f).dp.toPx(),
                                    pathEffect = if (r.dashed) PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())) else null,
                                ),
                            )
                        }
                        r.label?.let { text ->
                            val layout = measurer.measure(text, TextStyle(fontFamily = IBMPlexMono, fontSize = 10.sp))
                            drawText(layout, color = color, topLeft = Offset(topLeft.x + 2.dp.toPx(), topLeft.y - layout.size.height - 1.dp.toPx()))
                        }
                    }
                    stage.dots.forEach { drawCircle(SimColors.Active, 4.dp.toPx(), Offset((it.x * k).toFloat(), (it.y * k).toFloat())) }
                }
            }
        }
        if (stage.side.isNotEmpty()) DkSideNotes(stage.side, Modifier.weight(1f))
    }
}

// ── U-Net ──

/** The U: encoder sides, the bottleneck, and decoder sides; [done] decoder levels are computed, [current] is one of them. */
internal class DkUNet(val encoder: List<Int>, val bottom: Int, val decoder: List<Int>, val done: Int, val current: Int?) : DkStage

@Composable
internal fun DkUNetView(stage: DkUNet) {
    val measurer = rememberTextMeasurer()
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(modifier = Modifier.fillMaxWidth().height(250.dp).dkStage()) {
        val depth = stage.encoder.size
        val cols = 2 * depth + 1
        val padX = 12.dp.toPx()
        val slot = (size.width - 2 * padX) / cols
        val barW = slot * 0.62f
        val top = 14.dp.toPx()
        val maxH = size.height * 0.62f
        val levelStep = (size.height - top - 30.dp.toPx() - maxH * stage.bottom / stage.encoder[0]) / depth
        val maxSize = stage.encoder[0].toFloat()
        fun cx(col: Int) = padX + slot * (col + 0.5f)
        val small = TextStyle(fontFamily = IBMPlexMono, fontSize = 10.sp)
        // A level's bar is centred on the level's line: top at the level's offset, height by side length.
        fun drawBar(col: Int, level: Int, side: Int, color: Color, label: String) {
            val h = maxH * side / maxSize
            val y = top + level * levelStep
            drawRoundRect(color, Offset(cx(col) - barW / 2, y), Size(barW, h.coerceAtLeast(6.dp.toPx())), CornerRadius(3.dp.toPx()))
            val layout = measurer.measure(label, small)
            drawText(layout, color = muted, topLeft = Offset(cx(col) - layout.size.width / 2f, y + h.coerceAtLeast(6.dp.toPx()) + 3.dp.toPx()))
        }
        // Skips first, under the bars: encoder level i to decoder level i, labelled with the crop.
        for (i in 0 until depth) {
            val decCol = cols - 1 - i
            val y = top + i * levelStep + 3.dp.toPx()
            val dec = depth - 1 - i
            val hot = stage.current == dec
            val color = if (hot) SimColors.Active else muted.copy(alpha = 0.5f)
            drawLine(color, Offset(cx(i) + barW / 2, y), Offset(cx(decCol) - barW / 2, y), 1.2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())))
            val up = 2 * (if (dec == 0) stage.bottom else stage.decoder[dec - 1])
            val crop = "crop ${stage.encoder[i]}→$up"
            val layout = measurer.measure(crop, small)
            drawText(layout, color = color, topLeft = Offset((cx(i) + cx(decCol)) / 2 - layout.size.width / 2f, y - layout.size.height - 1.dp.toPx()))
        }
        stage.encoder.forEachIndexed { i, side -> drawBar(i, i, side, dkColor(DkInk.Blue), "$side") }
        drawBar(depth, depth, stage.bottom, SimColors.Answer, "${stage.bottom}")
        stage.decoder.forEachIndexed { d, side ->
            val level = depth - 1 - d
            val col = depth + 1 + d
            val color = when {
                d == stage.current -> SimColors.Active
                d < stage.done -> dkColor(DkInk.Green)
                else -> dkColor(DkInk.Slate)
            }
            drawBar(col, level, side, color, "$side")
        }
    }
}

/** "e b  ────────  0.198 kept": a mono label, the bar and its value on one line. */
@Composable
private fun DkInlineRow(row: DkRow) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val ink = if (row.dim) muted else MaterialTheme.colorScheme.onSurface
    val bar = row.bars.first()
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            row.title, fontFamily = IBMPlexMono, fontSize = 14.sp, maxLines = 1, color = if (row.hot) SimColors.Active else ink,
            fontWeight = if (row.dim) FontWeight.Normal else FontWeight.Bold, modifier = Modifier.width(if (row.meta.isEmpty()) 76.dp else 52.dp),
        )
        if (row.meta.isNotEmpty()) {
            Text(row.meta, fontFamily = IBMPlexMono, fontSize = 13.sp, color = ink, textAlign = TextAlign.End, maxLines = 1, modifier = Modifier.width(52.dp).padding(end = 10.dp))
        }
        Box(modifier = Modifier.weight(1f).height(6.dp).background(SimColors.Tint, RoundedCornerShape(3.dp))) {
            Box(
                modifier = Modifier
                    .fillMaxWidth((bar.frac ?: 0.0).toFloat().coerceIn(0.012f, 1f))
                    .fillMaxHeight()
                    .background(if (row.dim) muted.copy(alpha = 0.6f) else dkColor(bar.ink), RoundedCornerShape(3.dp)),
            )
        }
        Text(bar.label, fontFamily = IBMPlexMono, fontSize = 13.sp, color = ink, textAlign = TextAlign.End, maxLines = 1, modifier = Modifier.width(if (row.meta.isEmpty()) 92.dp else 52.dp))
    }
}

// ── Tiles ──

/** Source and Emitted are the blue and green tokens; Current is the yellow-ringed one being worked;
 *  Empty a slot not filled yet; Plain a neutral tile; Hot the violet one that matters; Fill a dark
 *  tile with a level rising from its bottom ([DkTile.fill] of [DkTile.fillInk]). */
// Heat washes the tile violet by [DkTile.fill] (an attention weight).
internal enum class DkTileTone { Source, Emitted, Current, Empty, Plain, Hot, Fill, Heat }

internal class DkTile(
    val text: String,
    val tone: DkTileTone,
    val sub: String? = null,
    val fill: Double = 0.0,
    val fillInk: DkInk = DkInk.Blue,
    // Under the tile: a mono value, then thin state bars (null is an empty track).
    val caption: String? = null,
    val under: List<DkInk?> = emptyList(),
    // A yellow ring without the Current tint (a head's top key, the task in use).
    val ring: Boolean = false,
)

internal class DkTileRow(val tiles: List<DkTile>, val label: String? = null, val title: String? = null, val height: Int = 44)

/** Rows of tiles in shared columns; [headers] name the columns, [hot] rings a column in yellow,
 *  [pill] sits under row index first ("context · 12 numbers"), [arrows] draws ↓ between rows. */
internal class DkTiles(
    val rows: List<DkTileRow>,
    val headers: List<String> = emptyList(),
    val hot: Int? = null,
    val pill: Pair<Int, String>? = null,
    val arrows: Boolean = false,
    // Column names under the last row ("task prefix · input · target").
    val footers: List<String> = emptyList(),
) : DkStage

@Composable
internal fun DkTilesView(stage: DkTiles) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val labelled = stage.rows.any { it.label != null }
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (stage.headers.isNotEmpty()) {
            Row {
                if (labelled) Box(Modifier.width(58.dp))
                stage.headers.forEachIndexed { i, h ->
                    Text(
                        h, fontFamily = IBMPlexMono, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.weight(1f),
                        color = if (i == stage.hot) SimColors.Active else muted, fontWeight = if (i == stage.hot) FontWeight.Bold else FontWeight.Normal,
                    )
                }
            }
        }
        stage.rows.forEachIndexed { r, row ->
            row.title?.let { Text(it, fontSize = 13.sp, color = muted) }
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (labelled) {
                    Box(modifier = Modifier.width(52.dp).height(row.height.dp), contentAlignment = Alignment.CenterStart) {
                        Text(row.label ?: "", fontSize = 13.sp, color = muted, maxLines = 1)
                    }
                }
                row.tiles.forEachIndexed { c, tile -> DkTileView(tile, row.height, c == stage.hot, Modifier.weight(1f)) }
            }
            if (stage.arrows && r < stage.rows.lastIndex) {
                Row {
                    if (labelled) Box(Modifier.width(58.dp))
                    row.tiles.forEach { Text("↓", fontSize = 12.sp, color = muted, textAlign = TextAlign.Center, modifier = Modifier.weight(1f)) }
                }
            }
            if (r == stage.rows.lastIndex && stage.footers.isNotEmpty()) {
                Row {
                    if (labelled) Box(Modifier.width(58.dp))
                    stage.footers.forEach { Text(it, fontFamily = IBMPlexMono, fontSize = 11.sp, color = muted, textAlign = TextAlign.Center, modifier = Modifier.weight(1f)) }
                }
            }
            if (stage.pill?.first == r) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f).height(1.dp).background(muted.copy(alpha = 0.35f)))
                    Text(
                        stage.pill.second, fontFamily = IBMPlexMono, fontSize = 13.sp, color = StoryTone.Answer.ink(),
                        modifier = Modifier.padding(horizontal = 8.dp).background(SimColors.Answer.copy(alpha = 0.3f), RoundedCornerShape(8.dp)).padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                    Box(Modifier.weight(1f).height(1.dp).background(muted.copy(alpha = 0.35f)))
                }
            }
        }
    }
}

@Composable
private fun DkTileView(tile: DkTile, height: Int, hotColumn: Boolean, modifier: Modifier) {
    val shape = RoundedCornerShape(8.dp)
    val bg = when (tile.tone) {
        DkTileTone.Source -> dkColor(DkInk.Blue).copy(alpha = 0.3f)
        DkTileTone.Emitted -> dkColor(DkInk.Green).copy(alpha = 0.3f)
        DkTileTone.Current -> SimColors.Active.copy(alpha = 0.22f)
        DkTileTone.Empty -> Color.White.copy(alpha = 0.04f)
        DkTileTone.Plain, DkTileTone.Fill -> Color.White.copy(alpha = 0.07f)
        DkTileTone.Hot -> SimColors.Answer.copy(alpha = 0.4f)
        DkTileTone.Heat -> SimColors.Answer.copy(alpha = 0.15f + 0.75f * tile.fill.toFloat().coerceIn(0f, 1f))
    }
    val ring = tile.ring || tile.tone == DkTileTone.Current || (hotColumn && tile.tone == DkTileTone.Fill)
    val ink = if (tile.tone == DkTileTone.Current) SimColors.Active else Color.White
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(height.dp)
                .clip(shape)
                .background(bg)
                .then(if (ring) Modifier.border(1.5.dp, SimColors.Active, shape) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            if (tile.tone == DkTileTone.Fill && tile.fill > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .fillMaxHeight(tile.fill.toFloat().coerceIn(0.06f, 1f))
                        .background(dkColor(tile.fillInk)),
                )
            }
            if (tile.text.isNotEmpty()) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        tile.text, fontSize = if (tile.text.length > 12) 13.sp else 15.sp, fontWeight = FontWeight.Bold,
                        color = if (tile.ring && tile.tone != DkTileTone.Current) SimColors.Active else ink, maxLines = 2, textAlign = TextAlign.Center,
                        lineHeight = 16.sp, modifier = Modifier.padding(horizontal = 4.dp),
                    )
                    tile.sub?.let { Text(it, fontFamily = IBMPlexMono, fontSize = 11.sp, color = ink.copy(alpha = 0.75f), maxLines = 1) }
                }
            }
        }
        tile.caption?.let {
            Text(
                it, fontFamily = IBMPlexMono, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                color = if (hotColumn) SimColors.Active else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
            )
        }
        tile.under.forEach { ink2 ->
            Box(Modifier.fillMaxWidth().height(4.dp).background(ink2?.let { dkColor(it) } ?: SimColors.Tint, RoundedCornerShape(2.dp)))
        }
    }
}

// ── Pipeline ──

internal enum class DkStepTone { Done, Current, Next }

internal class DkPipeItem(val title: String, val meta: String, val tone: DkStepTone)

/** A block's stages top to bottom: done in green, the current one ringed yellow, the rest dim. */
internal class DkPipeline(val items: List<DkPipeItem>) : DkStage

@Composable
internal fun DkPipelineView(stage: DkPipeline) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        stage.items.forEach { item ->
            val shape = RoundedCornerShape(9.dp)
            val bg = when (item.tone) {
                DkStepTone.Done -> dkColor(DkInk.Green).copy(alpha = 0.2f)
                DkStepTone.Current -> SimColors.Active.copy(alpha = 0.2f)
                DkStepTone.Next -> Color.White.copy(alpha = 0.05f)
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .clip(shape)
                    .background(bg)
                    .then(if (item.tone == DkStepTone.Current) Modifier.border(1.5.dp, SimColors.Active, shape) else Modifier)
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    item.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, modifier = Modifier.weight(1f),
                    color = if (item.tone == DkStepTone.Current) SimColors.Active else MaterialTheme.colorScheme.onSurface,
                )
                Text(item.meta, fontFamily = IBMPlexMono, fontSize = 12.sp, color = muted, maxLines = 1)
            }
        }
    }
}

// ── Histograms ──

/** One row of bars over shared class labels; [hot] is drawn yellow (the hard label). */
internal class DkHistRow(val label: String, val values: List<Double>, val ink: DkInk, val hot: Int?)

internal class DkHist(val rows: List<DkHistRow>, val labels: List<String>) : DkStage

@Composable
internal fun DkHistView(stage: DkHist) {
    val measurer = rememberTextMeasurer()
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val onSurface = MaterialTheme.colorScheme.onSurface
    val rowH = 92
    Canvas(modifier = Modifier.fillMaxWidth().height((stage.rows.size * rowH + 22).dp).dkStage()) {
        val n = stage.labels.size
        val padX = 10.dp.toPx()
        val slot = (size.width - 2 * padX) / n
        val barW = slot * 0.72f
        val small = TextStyle(fontFamily = IBMPlexMono, fontSize = 10.sp)
        val top = stage.rows.maxOf { r -> r.values.max() }.coerceAtLeast(1e-9)
        stage.rows.forEachIndexed { r, row ->
            val y0 = r * rowH.dp.toPx()
            val base = y0 + rowH.dp.toPx() - 6.dp.toPx()
            val maxH = rowH.dp.toPx() - 40.dp.toPx()
            val lay = measurer.measure(row.label, TextStyle(fontSize = 12.sp))
            drawText(lay, color = muted, topLeft = Offset(padX, y0 + 6.dp.toPx()))
            drawLine(muted.copy(alpha = 0.3f), Offset(padX, base), Offset(size.width - padX, base), 1.dp.toPx())
            row.values.forEachIndexed { i, v ->
                val h = (v / top).toFloat() * maxH
                val cx = padX + slot * (i + 0.5f)
                val color = if (i == row.hot) SimColors.Active else dkColor(row.ink)
                drawRoundRect(color, Offset(cx - barW / 2, base - h), Size(barW, h.coerceAtLeast(1.5.dp.toPx())), CornerRadius(3.dp.toPx()))
                val l = measurer.measure(dkNum(v, 2), small)
                drawText(l, color = onSurface.copy(alpha = 0.8f), topLeft = Offset(cx - l.size.width / 2f, base - h - l.size.height - 2.dp.toPx()))
            }
        }
        stage.labels.forEachIndexed { i, l ->
            val lay = measurer.measure(l, small)
            drawText(lay, color = muted, topLeft = Offset(padX + slot * (i + 0.5f) - lay.size.width / 2f, size.height - 18.dp.toPx()))
        }
    }
}

// ── Token chips ──

/** Whole is a learned piece (violet); Part a short fragment (yellow); Plain an ordinary token. */
internal enum class DkTokTone { Whole, Part, Plain, Masked }

internal class DkTokens(val tokens: List<Pair<String, DkTokTone>>, val notes: List<String> = emptyList()) : DkStage

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun DkTokensView(stage: DkTokens) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            stage.tokens.forEach { (text, tone) ->
                val (bg, ink) = when (tone) {
                    DkTokTone.Whole -> SimColors.Answer.copy(alpha = 0.35f) to Color.White
                    DkTokTone.Part -> SimColors.Active.copy(alpha = 0.25f) to SimColors.Active
                    DkTokTone.Plain -> Color.White.copy(alpha = 0.08f) to Color.White
                    DkTokTone.Masked -> SimColors.Active.copy(alpha = 0.25f) to SimColors.Active
                }
                Text(
                    text, fontFamily = IBMPlexMono, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = ink,
                    modifier = Modifier.background(bg, RoundedCornerShape(7.dp)).padding(horizontal = 9.dp, vertical = 6.dp),
                )
            }
        }
        if (stage.notes.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                stage.notes.forEach { Text(storyAnnotated(it), fontFamily = IBMPlexMono, fontSize = 11.sp, color = muted) }
            }
        }
    }
}

// ── Graphs ──

/** A node at (x, y) in 0..1 of the stage; [ink] fills it (null: plain), [value] sits under it, [ring] marks the centre. */
internal class DkGNode(val x: Float, val y: Float, val label: String, val value: String? = null, val ink: DkInk? = null, val ring: Boolean = false)

/** An edge; [hot] draws it yellow, [weight] (0..1) sets its width when hot. */
internal class DkGEdge(val a: Int, val b: Int, val hot: Boolean = false, val weight: Double = 0.5)

internal class DkGraph(val nodes: List<DkGNode>, val edges: List<DkGEdge>, val valueInk: DkInk = DkInk.Violet) : DkStage

@Composable
internal fun DkGraphView(stage: DkGraph) {
    val measurer = rememberTextMeasurer()
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val valueInk = StoryTone.Answer.ink()
    Canvas(modifier = Modifier.fillMaxWidth().height(230.dp).dkStage()) {
        val r = 18.dp.toPx()
        val padX = r + 28.dp.toPx()
        val padTop = r + 14.dp.toPx()
        val padBottom = r + 28.dp.toPx()
        fun at(n: DkGNode) = Offset(padX + n.x * (size.width - 2 * padX), padTop + n.y * (size.height - padTop - padBottom))
        stage.edges.sortedBy { it.hot }.forEach { e ->
            val a = at(stage.nodes[e.a])
            val b = at(stage.nodes[e.b])
            if (e.hot) drawLine(SimColors.Active, a, b, (1.5 + 5 * e.weight).toFloat().dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
            else drawLine(muted.copy(alpha = 0.45f), a, b, 1.5.dp.toPx())
        }
        val labelStyle = TextStyle(fontFamily = IBMPlexMono, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        val valueStyle = TextStyle(fontFamily = IBMPlexMono, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
        stage.nodes.forEach { n ->
            val c = at(n)
            drawCircle(Color(0xFF2A2F3A), r, c)
            n.ink?.let { drawCircle(dkColor(it).copy(alpha = 0.6f), r, c) }
            drawCircle(if (n.ring) SimColors.Active else muted.copy(alpha = 0.5f), r - 1.dp.toPx(), c, style = Stroke((if (n.ring) 2.5f else 1.2f).dp.toPx()))
            val l = measurer.measure(n.label, labelStyle)
            drawText(l, color = Color.White, topLeft = Offset(c.x - l.size.width / 2f, c.y - l.size.height / 2f))
            n.value?.let { v ->
                val lv = measurer.measure(v, valueStyle)
                drawText(lv, color = valueInk, topLeft = Offset(c.x - lv.size.width / 2f, c.y + r + 4.dp.toPx()))
            }
        }
    }
}
