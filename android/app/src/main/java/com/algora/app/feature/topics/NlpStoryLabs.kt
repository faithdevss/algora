package com.algora.app.feature.topics

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.algora.app.core.ui.theme.IBMPlexMono
import com.algora.app.core.ui.theme.SimColors
import com.algora.app.core.ui.theme.SpaceGrotesk

// ── NLP and LLM storyboards ──────────────────────────────────────────────────
// Text preprocessing, statistical NLP, embeddings, syntax, pretrained models, prompting and transformer
// internals as one card of stacked blocks — tokens, tables, bar rows, stat tiles, matrices, plots, a small
// tree or network — over the step's legend, then chips, a formula box and the headline. The dock is the
// play transport. Frames: NlpTextFrames.kt, NlpSyntaxFrames.kt and NlpModelFrames.kt; NlpStoryLabs.swift
// is the iOS port.

internal val nlpStoryTopicIds = nlpTextTopicIds + nlpSyntaxTopicIds + nlpModelTopicIds

internal fun nlpLab(topicId: String): List<NbFrame> =
    nlpTextLab(topicId) ?: nlpSyntaxLab(topicId) ?: nlpModelLab(topicId) ?: error("No NLP storyboard for $topicId")

internal enum class NbInk { Yellow, Violet, Indigo, Blue, Sky, Green, Red, Pink, Orange, Grey, Slate, Teal }

/** Token tones: plain, the window/current one (yellow), the picked one (violet), not reached, an empty
 *  slot, kept (green), removed (red, struck through), and the three entity tints. */
internal enum class NbTone { Plain, Hot, Pick, Dim, Empty, Good, Bad, Blue, Pink, Orange }

internal class NbTok(val t: String, val tone: NbTone = NbTone.Plain, val sub: String = "")

internal class NbCell(
    val text: String,
    val ink: NbInk? = null,
    val bold: Boolean = false,
    // A muted second line ("stem \"runn\" has a vowel").
    val sub: String = "",
    // A bar in place of text: the filled fraction of the cell.
    val bar: Double? = null,
    val mono: Boolean = true,
)

internal class NbRow(val cells: List<NbCell>, val ring: Boolean = false, val tint: NbInk? = null, val dim: Boolean = false)

internal class NbBar(val label: String, val value: String, val frac: Double, val ink: NbInk, val labelInk: NbInk? = null)

internal class NbTile(val big: String, val caption: String, val ink: NbInk? = null, val tint: NbInk? = null)

internal class NbBox(val title: String, val lines: List<String>, val ink: NbInk? = null, val lastInk: NbInk? = null)

internal class NbGCell(val text: String, val level: Double, val ink: NbInk = NbInk.Blue, val ring: Boolean = false)

internal class NbP(val x: Double, val y: Double)

internal class NbLine(val pts: List<NbP>, val ink: NbInk, val dashed: Boolean = false, val width: Float = 2.5f, val dots: Boolean = false)

/** A plotted point; [cat] picks a colour from the cluster palette, [text] is drawn inside, [label] beside. */
internal class NbDot(
    val p: NbP,
    val ink: NbInk = NbInk.Sky,
    val r: Float = 4f,
    val ring: Boolean = false,
    val label: String? = null,
    val text: String? = null,
    val cat: Int? = null,
    val hollow: Boolean = false,
)

internal class NbSeg(val a: NbP, val b: NbP, val ink: NbInk, val dashed: Boolean = false, val width: Float = 2f)

internal class NbNode(
    val x: Float,
    val y: Float,
    val text: String,
    val sub: String = "",
    val tone: NbTone = NbTone.Plain,
    // A line under the box ("sure"), in [belowInk].
    val below: String = "",
    val belowInk: NbInk? = null,
    // A dashed box with room for two lines.
    val dashed: Boolean = false,
    val wide: Boolean = false,
)

internal class NbEdge(val a: Int, val b: Int, val hot: Boolean = false)

internal class NbSpan(val t: String, val tone: NbTone = NbTone.Plain)

internal class NbModel(val name: String, val sub: String, val enc: Boolean, val dec: Boolean, val frac: Double, val value: String, val ring: Boolean = false)

internal sealed interface NbBlock {
    class Caption(val text: String, val note: String? = null) : NbBlock
    /** Tokens in a wrapping row, or a grid of [columns]; [label] sits in a left gutter ("stack"). */
    class Toks(val toks: List<NbTok>, val label: String? = null, val columns: Int? = null, val mono: Boolean = false, val start: Boolean = false) : NbBlock
    class Table(val headers: List<String>, val weights: List<Float>, val rows: List<NbRow>, val aligns: List<Int>? = null) : NbBlock
    /** Bar rows; [ticks] label the track at fractions, [ref] is a dashed line at a fraction with its label. */
    class Bars(val rows: List<NbBar>, val ticks: List<Pair<Double, String>> = emptyList(), val ref: Pair<Double, String>? = null, val labelWidth: Int = 96) : NbBlock
    class Tiles(val tiles: List<NbTile>) : NbBlock
    class Boxes(val boxes: List<NbBox>) : NbBlock
    /** Mono lines in a tinted box; `{…}` marks colour a value. [warn] tints it red. */
    class Callout(val lines: List<String>, val warn: Boolean = false) : NbBlock
    class Grid(
        val cols: List<String>,
        val rows: List<String>,
        val cells: List<List<NbGCell>>,
        val text: Boolean = true,
        val cellHeight: Int = 28,
        val scale: Pair<String, String>? = null,
        val hotRow: Int? = null,
    ) : NbBlock
    class Plot(
        val xr: Pair<Double, Double>,
        val yr: Pair<Double, Double>,
        val height: Int = 180,
        val lines: List<NbLine> = emptyList(),
        val dots: List<NbDot> = emptyList(),
        val segs: List<NbSeg> = emptyList(),
        val xTicks: List<Pair<Double, String>> = emptyList(),
        val yTicks: List<Pair<Double, String>> = emptyList(),
        // A shaded band over [from, to] on x, labelled under the axis.
        val shade: Triple<Double, Double, String>? = null,
        val xLabel: String = "",
        val yLabel: String = "",
        val grid: Boolean = true,
        val groups: List<Pair<Double, String>> = emptyList(),
    ) : NbBlock
    /** Vertical bars with their value above and a label under; [hot] columns are violet. */
    class Columns(val values: List<Double>, val labels: List<String>, val hot: Set<Int>, val top: Double) : NbBlock
    class Pipeline(val steps: List<String>, val current: Int, val done: Int = 0) : NbBlock
    class Tree(val nodes: List<NbNode>, val edges: List<NbEdge>, val height: Int = 220) : NbBlock
    /** Columns of neurons with their activations; a null value is a unit ReLU zeroed. */
    class Net(val cols: List<List<Double?>>, val inks: List<NbInk>, val footers: List<Pair<String, String>>) : NbBlock
    class Text(val spans: List<NbSpan>, val mono: Boolean = false) : NbBlock
    /** Rows of context tokens, an arrow with a caption, and the predicted token. */
    class Flow(val rows: List<List<NbTok>>, val caption: String, val target: NbTok) : NbBlock
    class Banner(val big: String, val text: String, val ink: NbInk, val bigRight: Boolean = false) : NbBlock
    class Models(val rows: List<NbModel>) : NbBlock
    class Kv(val rows: List<Pair<String, String>>) : NbBlock
}

internal class NbLegend(val ink: NbInk, val label: String, val style: SwatchStyle = SwatchStyle.Fill)

internal class NbFrame(
    val blocks: List<NbBlock>,
    val legend: List<NbLegend>,
    val headline: String,
    val body: String,
    val chips: List<DkChip> = emptyList(),
    val fx: List<String> = emptyList(),
)

/** Builds every frame of a lab, checking nothing on screen is empty or non-finite. */
internal fun nlpStoryFrameCount(topicId: String): Int {
    val frames = nlpLab(topicId)
    require(frames.isNotEmpty()) { "$topicId built no frames" }
    frames.forEachIndexed { i, f ->
        require(f.headline.isNotBlank() && f.body.isNotBlank()) { "$topicId has an empty caption at step $i" }
        require(f.blocks.isNotEmpty()) { "$topicId has an empty card at step $i" }
        f.blocks.filterIsInstance<NbBlock.Bars>().flatMap { it.rows }.forEach { require(it.frac.isFinite()) { "$topicId drew a non-finite bar at step $i" } }
        f.blocks.filterIsInstance<NbBlock.Plot>().forEach { p ->
            require((p.lines.flatMap { it.pts } + p.dots.map { it.p }).all { it.x.isFinite() && it.y.isFinite() }) { "$topicId plotted a non-finite point at step $i" }
        }
    }
    return frames.size
}

// ── Lab ──

@Composable
internal fun NlpStorySection(topicId: String) {
    // Some labs run a search or train a small model, so frames are built off the main thread.
    val built by androidx.compose.runtime.produceState<List<NbFrame>?>(null, topicId) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { nlpLab(topicId) }
    }
    val frames = built ?: run {
        Box(Modifier.fillMaxWidth().height(320.dp), contentAlignment = Alignment.Center) {
            androidx.compose.material3.CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
        return
    }
    val playback = rememberPlaybackState(key = topicId, stepCount = frames.size, initialSpeedMs = 1000f)
    val dock = LocalLabDock.current
    val at = playback.index.coerceIn(0, frames.lastIndex)
    val captions = frames.map { storyPlain(it.headline) }
    val controls: @Composable () -> Unit = { LabTransportBar(playback, captions) }

    Column(modifier = Modifier.fillMaxWidth()) {
        NbBody(frames[at])
        if (dock == null) {
            HorizontalDivider(modifier = Modifier.padding(top = 16.dp, bottom = 14.dp), color = MaterialTheme.colorScheme.outline)
            controls()
        }
    }

    if (dock != null) {
        SideEffect {
            dock.controls = controls
            dock.navAction = LabNavAction(Icons.Filled.Refresh, "Reset") { playback.reset() }
        }
        DisposableEffect(dock) {
            onDispose {
                dock.controls = null
                dock.navAction = null
            }
        }
    }
}

@Composable
private fun NbBody(frame: NbFrame) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            frame.blocks.forEach { NbBlockView(it) }
            if (frame.legend.isNotEmpty()) StoryLegendRow(frame.legend.map { Triple(nbColor(it.ink), it.style, it.label) })
        }
    }
    if (frame.fx.isNotEmpty()) NbCallout(frame.fx, false, Modifier.padding(top = 12.dp), SimColors.Tint)
    LabChips(frame.chips.map { LabChip(it.key, it.value, tint = if (it.tint) StoryTone.Answer else null) }, Modifier.padding(top = 12.dp))
    LabStoryNarration(frame.headline, frame.body, Modifier.padding(top = 16.dp))
}

@Composable
private fun NbBlockView(block: NbBlock) {
    when (block) {
        is NbBlock.Caption -> NbCaption(block.text, block.note)
        is NbBlock.Toks -> NbToksView(block)
        is NbBlock.Table -> NbTableView(block)
        is NbBlock.Bars -> NbBarsView(block)
        is NbBlock.Tiles -> NbTilesView(block.tiles)
        is NbBlock.Boxes -> NbBoxesView(block.boxes)
        is NbBlock.Callout -> NbCallout(block.lines, block.warn)
        is NbBlock.Grid -> NbGridView(block)
        is NbBlock.Plot -> NbPlotView(block)
        is NbBlock.Columns -> NbColumnsView(block)
        is NbBlock.Pipeline -> NbPipelineView(block)
        is NbBlock.Tree -> NbTreeView(block)
        is NbBlock.Net -> NbNetView(block)
        is NbBlock.Text -> NbTextView(block)
        is NbBlock.Flow -> NbFlowView(block)
        is NbBlock.Banner -> NbBannerView(block)
        is NbBlock.Models -> NbModelsView(block.rows)
        is NbBlock.Kv -> NbKvView(block.rows)
    }
}

// ── Colours ──

internal fun nbColor(ink: NbInk): Color = when (ink) {
    NbInk.Yellow -> SimColors.Active
    NbInk.Violet -> Color(0xFF6366F1)
    NbInk.Indigo -> Color(0xFF818CF8)
    NbInk.Blue -> Color(0xFF3B82F6)
    NbInk.Sky -> Color(0xFF0EA5E9)
    NbInk.Green -> Color(0xFF22A06B)
    NbInk.Red -> Color(0xFFF87171)
    NbInk.Pink -> Color(0xFFEC4899)
    NbInk.Orange -> Color(0xFFF97316)
    NbInk.Grey -> Color(0xFF9AA0AE)
    NbInk.Slate -> Color(0xFF3A3F4C)
    NbInk.Teal -> Color(0xFF14B8A6)
}

/** The ink as text: green and red read lighter on the dark card. */
private fun nbText(ink: NbInk): Color = when (ink) {
    NbInk.Green -> Color(0xFF5FD49B)
    NbInk.Blue -> Color(0xFF8FB6FF)
    NbInk.Pink -> Color(0xFFF47AA8)
    NbInk.Orange -> Color(0xFFFB923C)
    else -> nbColor(ink)
}

private val nbCluster = listOf(
    0xFF0EA5E9, 0xFF22A06B, 0xFFF97316, 0xFFEC4899, 0xFF8B5CF6, 0xFFEAB308,
    0xFF14B8A6, 0xFF6366F1, 0xFFF87171, 0xFF84CC16, 0xFF06B6D4, 0xFFA855F7,
).map { Color(it) }

private val nbWell = Color.Black.copy(alpha = 0.16f)

@Composable
private fun toneColors(tone: NbTone): Pair<Color, Color> {
    val onSurface = MaterialTheme.colorScheme.onSurface
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    return when (tone) {
        NbTone.Plain -> SimColors.Tint to onSurface
        NbTone.Hot -> SimColors.Active to Color(0xFF1A1505)
        NbTone.Pick -> Color(0xFF6366F1) to Color.White
        NbTone.Dim -> nbWell to muted.copy(alpha = 0.7f)
        NbTone.Empty -> Color.Transparent to muted
        NbTone.Good -> Color(0xFF22A06B).copy(alpha = 0.28f) to Color(0xFF5FD49B)
        NbTone.Bad -> Color(0xFFF87171).copy(alpha = 0.18f) to Color(0xFFF87171)
        NbTone.Blue -> Color(0xFF3B82F6).copy(alpha = 0.32f) to Color(0xFFCFE0FF)
        NbTone.Pink -> Color(0xFFEC4899).copy(alpha = 0.3f) to Color(0xFFFBCFE8)
        NbTone.Orange -> Color(0xFFF97316).copy(alpha = 0.32f) to Color(0xFFFED7AA)
    }
}

// ── Blocks ──

@Composable
private fun NbCaption(text: String, note: String?) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Text(text, fontSize = 13.sp, lineHeight = 17.sp, fontWeight = FontWeight.SemiBold, color = muted, modifier = Modifier.weight(1f))
        note?.let { Text(it, fontFamily = IBMPlexMono, fontSize = 12.sp, color = muted, maxLines = 1, modifier = Modifier.padding(start = 10.dp)) }
    }
}

@Composable
private fun NbTokView(tok: NbTok, modifier: Modifier = Modifier, mono: Boolean = false, start: Boolean = false) {
    val (bg, fg) = toneColors(tok.tone)
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier = modifier
            .heightIn(min = 32.dp)
            .background(bg, RoundedCornerShape(8.dp))
            .then(if (tok.tone == NbTone.Empty) Modifier.dashedOutline(muted.copy(alpha = 0.5f), 8.dp) else Modifier)
            .padding(horizontal = 10.dp, vertical = 3.dp),
        horizontalAlignment = if (start) Alignment.Start else Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            tok.t, fontSize = if (mono) 13.sp else 15.sp, lineHeight = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            fontFamily = if (mono) IBMPlexMono else null,
            fontWeight = if (mono) FontWeight.Medium else FontWeight.SemiBold, color = fg,
            textDecoration = if (tok.tone == NbTone.Bad) TextDecoration.LineThrough else null,
        )
        if (tok.sub.isNotEmpty()) Text(tok.sub, fontFamily = IBMPlexMono, fontSize = 9.5.sp, lineHeight = 12.sp, maxLines = 1, color = fg.copy(alpha = 0.75f))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NbToksView(block: NbBlock.Toks) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        block.label?.let { Text(it, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = muted, modifier = Modifier.width(64.dp)) }
        val cols = block.columns
        if (cols == null) {
            FlowRow(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                block.toks.forEach { NbTokView(it, mono = block.mono) }
            }
        } else {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                block.toks.chunked(cols).forEach { line ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        line.forEach { NbTokView(it, Modifier.weight(1f), mono = block.mono, start = block.start) }
                        repeat(cols - line.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun NbTableView(block: NbBlock.Table) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val onSurface = MaterialTheme.colorScheme.onSurface
    fun align(i: Int) = when ((block.aligns ?: List(block.weights.size) { if (it == 0) 0 else 2 })[i]) {
        0 -> TextAlign.Start
        1 -> TextAlign.Center
        else -> TextAlign.End
    }
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        if (block.headers.isNotEmpty()) Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            block.headers.forEachIndexed { i, h ->
                Text(h, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = muted, maxLines = 1, textAlign = align(i), modifier = Modifier.weight(block.weights[i]))
            }
        }
        block.rows.forEach { row ->
            val shape = RoundedCornerShape(8.dp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 30.dp)
                    .background(row.tint?.let { nbColor(it).copy(alpha = 0.2f) } ?: nbWell, shape)
                    .then(if (row.ring) Modifier.border(1.5.dp, SimColors.Active, shape) else Modifier)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                row.cells.forEachIndexed { i, c ->
                    Column(modifier = Modifier.weight(block.weights[i]), horizontalAlignment = when (align(i)) {
                        TextAlign.Start -> Alignment.Start
                        TextAlign.Center -> Alignment.CenterHorizontally
                        else -> Alignment.End
                    }) {
                        if (c.bar != null) {
                            Box(Modifier.fillMaxWidth().height(8.dp).background(SimColors.Tint, RoundedCornerShape(3.dp))) {
                                Box(Modifier.fillMaxWidth(c.bar.toFloat().coerceIn(0.02f, 1f)).height(8.dp).background(nbColor(c.ink ?: NbInk.Sky), RoundedCornerShape(3.dp)))
                            }
                        } else {
                            val first = i == 0 && !c.mono
                            val color = when {
                                c.ink != null -> nbText(c.ink)
                                row.ring && i == 0 -> SimColors.Active
                                first -> onSurface
                                else -> muted
                            }.copy(alpha = if (row.dim) 0.5f else 1f)
                            Text(
                                c.text, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = align(i), color = color,
                                fontFamily = if (c.mono) IBMPlexMono else null,
                                fontSize = if (c.mono) 12.sp else 13.sp,
                                fontWeight = if (c.bold || first) FontWeight.SemiBold else FontWeight.Normal,
                            )
                            if (c.sub.isNotEmpty()) Text(c.sub, fontSize = 11.sp, lineHeight = 14.sp, color = muted, textAlign = align(i))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NbBarsView(block: NbBlock.Bars) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val onSurface = MaterialTheme.colorScheme.onSurface
    val measurer = rememberTextMeasurer()
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        block.rows.forEach { r ->
            Row(modifier = Modifier.fillMaxWidth().height(22.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    r.label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = r.labelInk?.let { nbText(it) } ?: onSurface, modifier = Modifier.width(block.labelWidth.dp),
                )
                Canvas(modifier = Modifier.weight(1f).height(22.dp).padding(horizontal = 6.dp)) {
                    val y = size.height / 2
                    drawRoundRect(Color.Black.copy(alpha = 0.22f), Offset(0f, y - 6.dp.toPx()), Size(size.width, 12.dp.toPx()), CornerRadius(3.dp.toPx()))
                    val w = size.width * r.frac.toFloat().coerceIn(0f, 1f)
                    if (w > 0) drawRoundRect(nbColor(r.ink), Offset(0f, y - 6.dp.toPx()), Size(w.coerceAtLeast(3.dp.toPx()), 12.dp.toPx()), CornerRadius(3.dp.toPx()))
                    block.ref?.let { (f, _) ->
                        val x = size.width * f.toFloat()
                        drawLine(muted.copy(alpha = 0.8f), Offset(x, 0f), Offset(x, size.height), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx())))
                    }
                }
                Text(r.value, fontFamily = IBMPlexMono, fontSize = 12.sp, color = onSurface.copy(alpha = 0.85f), maxLines = 1, textAlign = TextAlign.End, modifier = Modifier.width(62.dp))
            }
        }
        if (block.ticks.isNotEmpty() || block.ref != null) Row(modifier = Modifier.fillMaxWidth()) {
            Spacer(Modifier.width(block.labelWidth.dp))
            Canvas(modifier = Modifier.weight(1f).height(28.dp).padding(horizontal = 6.dp)) {
                block.ticks.forEach { (f, s) -> nbText(measurer, s, mono(9.5f), muted, Offset(size.width * f.toFloat(), 7.dp.toPx())) }
                block.ref?.let { (f, s) -> nbText(measurer, s, mono(9.5f), SimColors.Active, Offset(size.width * f.toFloat(), 20.dp.toPx())) }
            }
            Spacer(Modifier.width(62.dp))
        }
    }
}

@Composable
private fun NbTilesView(tiles: List<NbTile>) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val onSurface = MaterialTheme.colorScheme.onSurface
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        tiles.forEach { t ->
            Column(
                modifier = Modifier
                    .weight(1f)
                    .background(t.tint?.let { nbColor(it).copy(alpha = 0.16f) } ?: nbWell, RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Text(
                    t.big, fontFamily = SpaceGrotesk, fontWeight = FontWeight.Bold, maxLines = 1,
                    fontSize = if (t.big.length > 7) 18.sp else if (tiles.size > 3) 20.sp else 26.sp,
                    color = t.ink?.let { nbText(it) } ?: onSurface,
                )
                Text(t.caption, fontSize = 12.sp, lineHeight = 15.sp, color = muted)
            }
        }
    }
}

@Composable
private fun NbBoxesView(boxes: List<NbBox>) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val onSurface = MaterialTheme.colorScheme.onSurface
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        boxes.forEach { b ->
            Column(modifier = Modifier.weight(1f).background(nbWell, RoundedCornerShape(10.dp)).padding(horizontal = 12.dp, vertical = 9.dp)) {
                Text(b.title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = b.ink?.let { nbText(it) } ?: onSurface)
                b.lines.forEachIndexed { i, l ->
                    val last = i == b.lines.lastIndex && b.lastInk != null
                    Text(
                        l, fontSize = 12.sp, lineHeight = 16.sp,
                        fontFamily = if (last) IBMPlexMono else null,
                        color = if (last) nbText(b.lastInk!!) else muted,
                        modifier = Modifier.padding(top = if (last) 4.dp else 1.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun NbCallout(lines: List<String>, warn: Boolean, modifier: Modifier = Modifier, bg: Color = nbWell) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(if (warn) Color(0xFFF87171).copy(alpha = 0.16f) else bg, RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        lines.forEach { l ->
            Text(
                storyAnnotated(l), fontFamily = if (warn) null else IBMPlexMono, fontSize = if (warn) 13.sp else 12.5.sp, lineHeight = 18.sp,
                color = if (warn) Color(0xFFFECACA) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.88f),
            )
        }
    }
}

@Composable
private fun NbGridView(block: NbBlock.Grid) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val onSurface = MaterialTheme.colorScheme.onSurface
    val labelW = if (block.rows.isEmpty()) 0.dp else 46.dp
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        if (block.cols.isNotEmpty()) Row(modifier = Modifier.fillMaxWidth()) {
            Spacer(Modifier.width(labelW))
            block.cols.forEach { Text(it, fontFamily = IBMPlexMono, fontSize = 9.5.sp, color = muted, maxLines = 1, overflow = TextOverflow.Clip, textAlign = TextAlign.Center, modifier = Modifier.weight(1f)) }
        }
        block.cells.forEachIndexed { r, line ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
                if (block.rows.isNotEmpty()) Text(
                    block.rows[r], fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
                    color = if (block.hotRow == r) SimColors.Active else onSurface.copy(alpha = 0.85f),
                    modifier = Modifier.width(labelW - 3.dp),
                )
                line.forEach { c ->
                    val shape = RoundedCornerShape(5.dp)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(block.cellHeight.dp)
                            .background(if (c.level <= 0.0) nbWell else nbColor(c.ink).copy(alpha = (0.18f + 0.75f * c.level.toFloat()).coerceAtMost(0.95f)), shape)
                            .then(if (c.ring) Modifier.border(1.5.dp, SimColors.Active, shape) else Modifier),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (block.text) Text(
                            c.text, fontFamily = IBMPlexMono, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1,
                            color = if (c.level <= 0.0) muted.copy(alpha = 0.6f) else Color.White,
                        )
                    }
                }
            }
        }
        block.scale?.let { (lo, hi) ->
            Row(modifier = Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.width(labelW))
                Text(lo, fontFamily = IBMPlexMono, fontSize = 9.5.sp, color = muted)
                Box(
                    Modifier.weight(1f).padding(horizontal = 6.dp).height(5.dp)
                        .background(Brush.horizontalGradient(listOf(nbColor(NbInk.Violet).copy(alpha = 0.15f), nbColor(NbInk.Indigo))), RoundedCornerShape(3.dp)),
                )
                Text(hi, fontFamily = IBMPlexMono, fontSize = 9.5.sp, color = muted)
            }
        }
    }
}

private fun mono(size: Float, bold: Boolean = false) =
    TextStyle(fontFamily = IBMPlexMono, fontSize = size.sp, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)

private fun DrawScope.nbText(measurer: TextMeasurer, text: String, style: TextStyle, color: Color, at: Offset, anchor: Int = 0) {
    val layout = measurer.measure(text, style)
    val x = when (anchor) {
        -1 -> at.x - layout.size.width
        1 -> at.x
        else -> at.x - layout.size.width / 2f
    }
    drawText(layout, color = color, topLeft = Offset(x, at.y - layout.size.height / 2f))
}

@Composable
private fun NbPlotView(plot: NbBlock.Plot) {
    val measurer = rememberTextMeasurer()
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val onSurface = MaterialTheme.colorScheme.onSurface
    Canvas(modifier = Modifier.fillMaxWidth().height(plot.height.dp).dkStage()) {
        val left = if (plot.yTicks.isEmpty()) 12.dp.toPx() else 34.dp.toPx()
        val right = size.width - 12.dp.toPx()
        val top = 14.dp.toPx()
        val bottom = size.height - (if (plot.xTicks.isEmpty()) 12.dp else if (plot.groups.isEmpty()) 24.dp else 36.dp).toPx()
        fun px(x: Double) = left + ((x - plot.xr.first) / (plot.xr.second - plot.xr.first)).toFloat() * (right - left)
        fun py(y: Double) = bottom - ((y - plot.yr.first) / (plot.yr.second - plot.yr.first)).toFloat() * (bottom - top)
        fun at(p: NbP) = Offset(px(p.x), py(p.y))
        plot.shade?.let { (a, b, _) ->
            drawRect(Color(0xFFEC4899).copy(alpha = 0.12f), Offset(px(a), top), Size(px(b) - px(a), bottom - top))
        }
        plot.yTicks.forEach { (v, s) ->
            if (plot.grid) drawLine(muted.copy(alpha = 0.15f), Offset(left, py(v)), Offset(right, py(v)), 1.dp.toPx())
            nbText(measurer, s, mono(9f), muted, Offset(left - 5.dp.toPx(), py(v)), -1)
        }
        plot.xTicks.forEach { (v, s) -> nbText(measurer, s, mono(9f), muted, Offset(px(v), bottom + 10.dp.toPx())) }
        plot.groups.forEach { (v, s) -> nbText(measurer, s, mono(9f), muted, Offset(px(v), bottom + 23.dp.toPx())) }
        plot.shade?.let { (a, b, s) -> if (plot.groups.isEmpty() && s.isNotEmpty()) nbText(measurer, s, mono(9f), nbText(NbInk.Pink), Offset((px(a) + px(b)) / 2, top + 8.dp.toPx())) }
        if (plot.xLabel.isNotEmpty()) nbText(measurer, plot.xLabel, mono(9f), muted, Offset(right, bottom - 9.dp.toPx()), -1)
        if (plot.yLabel.isNotEmpty()) nbText(measurer, plot.yLabel, mono(9f), muted, Offset(left + 4.dp.toPx(), top + 2.dp.toPx()), 1)
        plot.segs.forEach { s ->
            drawLine(
                nbColor(s.ink), at(s.a), at(s.b), s.width.dp.toPx(), cap = StrokeCap.Round,
                pathEffect = if (s.dashed) PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())) else null,
            )
        }
        plot.lines.forEach { l ->
            val path = Path()
            l.pts.forEachIndexed { i, p -> if (i == 0) path.moveTo(px(p.x), py(p.y)) else path.lineTo(px(p.x), py(p.y)) }
            drawPath(
                path, nbColor(l.ink),
                style = Stroke(
                    l.width.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round,
                    pathEffect = if (l.dashed) PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx())) else null,
                ),
            )
            if (l.dots) l.pts.forEach { drawCircle(nbColor(l.ink), 3.dp.toPx(), at(it)) }
        }
        plot.dots.forEach { d ->
            val c = d.cat?.let { nbCluster[it % nbCluster.size] } ?: nbColor(d.ink)
            val o = at(d.p)
            if (d.hollow) {
                drawCircle(Color(0xFF171A23), d.r.dp.toPx(), o)
                drawCircle(Color.White, d.r.dp.toPx(), o, style = Stroke(2.dp.toPx()))
            } else {
                drawCircle(c, d.r.dp.toPx(), o)
                if (d.ring) drawCircle(Color.White, d.r.dp.toPx(), o, style = Stroke(1.5.dp.toPx()))
            }
            d.text?.let { nbText(measurer, it, mono(8.5f, true), Color.White, o) }
            d.label?.let { nbText(measurer, it, mono(9.5f, true), c, Offset(o.x + d.r.dp.toPx() + 4.dp.toPx(), o.y - 7.dp.toPx()), 1) }
        }
    }
}

@Composable
private fun NbColumnsView(block: NbBlock.Columns) {
    val measurer = rememberTextMeasurer()
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val onSurface = MaterialTheme.colorScheme.onSurface
    Canvas(modifier = Modifier.fillMaxWidth().height(130.dp)) {
        val n = block.values.size
        val slot = size.width / n
        val bottom = size.height - 18.dp.toPx()
        val top = 16.dp.toPx()
        drawLine(muted.copy(alpha = 0.3f), Offset(0f, bottom), Offset(size.width, bottom), 1.dp.toPx())
        block.values.forEachIndexed { i, v ->
            val hot = i in block.hot
            val h = ((v / block.top).toFloat().coerceIn(0f, 1f) * (bottom - top)).coerceAtLeast(2.dp.toPx())
            drawRoundRect(
                if (hot) nbColor(NbInk.Violet) else Color(0xFF3A3F4C), Offset(slot * i + slot * 0.15f, bottom - h), Size(slot * 0.7f, h),
                CornerRadius(3.dp.toPx()),
            )
            nbText(measurer, dkNum(v, 2), mono(9f), if (hot) onSurface else muted, Offset(slot * i + slot / 2, bottom - h - 8.dp.toPx()))
            nbText(measurer, block.labels[i], mono(9.5f, hot), if (hot) onSurface else muted, Offset(slot * i + slot / 2, bottom + 9.dp.toPx()))
        }
    }
}

@Composable
private fun NbPipelineView(block: NbBlock.Pipeline) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        block.steps.forEachIndexed { i, s ->
            val bg = when {
                i == block.current -> nbColor(NbInk.Violet)
                i < block.done -> nbColor(NbInk.Violet).copy(alpha = 0.3f)
                else -> nbWell
            }
            Box(modifier = Modifier.weight(1f).height(32.dp).background(bg, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                Text(
                    s, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
                    color = if (i == block.current) Color.White else if (i < block.done) Color(0xFFC7D2FE) else muted,
                )
            }
        }
    }
}

@Composable
private fun NbTreeView(block: NbBlock.Tree) {
    val measurer = rememberTextMeasurer()
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val tones = block.nodes.map { toneColors(it.tone) }
    Canvas(modifier = Modifier.fillMaxWidth().height(block.height.dp).dkStage()) {
        fun c(n: NbNode) = Offset(size.width * n.x, size.height * n.y)
        block.edges.forEach { e ->
            drawLine(
                if (e.hot) nbColor(NbInk.Blue) else muted.copy(alpha = 0.6f), c(block.nodes[e.a]), c(block.nodes[e.b]),
                (if (e.hot) 1.8f else 1.2f).dp.toPx(),
            )
        }
        block.nodes.forEachIndexed { i, n ->
            val (bg, fg) = tones[i]
            val main = measurer.measure(n.text, mono(if (n.dashed) 10f else 11.5f, !n.dashed))
            val sub = if (n.sub.isNotEmpty()) measurer.measure(n.sub, mono(8.5f)) else null
            val w = maxOf(main.size.width, sub?.size?.width ?: 0) + 16.dp.toPx()
            val h = main.size.height + (sub?.size?.height ?: 0) + 8.dp.toPx()
            val o = c(n)
            val tl = Offset(o.x - w / 2, o.y - h / 2)
            if (n.dashed) {
                drawRoundRect(muted.copy(alpha = 0.6f), tl, Size(w, h), CornerRadius(7.dp.toPx()), style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))))
            } else {
                drawRoundRect(treeFill(n.tone, bg), tl, Size(w, h), CornerRadius(7.dp.toPx()))
            }
            val txt = if (n.dashed) muted else treeInk(n.tone, fg)
            if (sub == null) {
                drawText(main, color = txt, topLeft = Offset(o.x - main.size.width / 2f, o.y - main.size.height / 2f))
            } else if (n.dashed) {
                drawText(main, color = txt, topLeft = Offset(o.x - main.size.width / 2f, tl.y + 4.dp.toPx()))
                drawText(sub, color = nbText(NbInk.Green), topLeft = Offset(o.x - sub.size.width / 2f, tl.y + 4.dp.toPx() + main.size.height))
            } else {
                drawText(sub, color = txt.copy(alpha = 0.75f), topLeft = Offset(o.x - sub.size.width / 2f, tl.y + 3.dp.toPx()))
                drawText(main, color = txt, topLeft = Offset(o.x - main.size.width / 2f, tl.y + 3.dp.toPx() + sub.size.height))
            }
            if (n.below.isNotEmpty()) nbText(measurer, n.below, mono(9f), n.belowInk?.let { nbText(it) } ?: muted, Offset(o.x, tl.y + h + 9.dp.toPx()))
        }
    }
}

// Tree nodes are solid: a phrase in green, the current one yellow, a failure pink.
private fun treeFill(tone: NbTone, tint: Color): Color = when (tone) {
    NbTone.Plain -> Color(0xFF2A2E39)
    NbTone.Good -> nbColor(NbInk.Green)
    NbTone.Bad -> Color(0xFFEC4899).copy(alpha = 0.3f)
    else -> tint
}

private fun treeInk(tone: NbTone, ink: Color): Color = when (tone) {
    NbTone.Plain, NbTone.Good -> Color(0xFFF2F3F7)
    NbTone.Bad -> Color(0xFFF47AA8)
    else -> ink
}

@Composable
private fun NbNetView(block: NbBlock.Net) {
    val measurer = rememberTextMeasurer()
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(modifier = Modifier.fillMaxWidth().height(240.dp).dkStage()) {
        val bottomPad = 30.dp.toPx()
        val n = block.cols.size
        fun cx(k: Int) = size.width * (0.14f + 0.72f * k / (n - 1))
        fun cy(k: Int, i: Int): Float {
            val count = block.cols[k].size
            val top = 18.dp.toPx()
            val bot = size.height - bottomPad - 8.dp.toPx()
            return if (count == 1) (top + bot) / 2 else top + (bot - top) * i / (count - 1)
        }
        for (k in 0 until n - 1) for (i in block.cols[k].indices) for (j in block.cols[k + 1].indices) {
            val off = block.cols[k][i] == null || block.cols[k + 1][j] == null
            drawLine(nbColor(NbInk.Sky).copy(alpha = if (off) 0.05f else 0.18f), Offset(cx(k), cy(k, i)), Offset(cx(k + 1), cy(k + 1, j)), 0.8.dp.toPx())
        }
        block.cols.forEachIndexed { k, col ->
            col.forEachIndexed { i, v ->
                val o = Offset(cx(k), cy(k, i))
                val r = if (col.size > 5) 10.dp.toPx() else 14.dp.toPx()
                drawCircle(if (v == null) Color(0xFF2A2E39) else nbColor(block.inks[k]), r, o)
                nbText(measurer, v?.let { dkNum(it, 2) } ?: "0.00", mono(if (col.size > 5) 8f else 9f, true), if (v == null) muted.copy(alpha = 0.6f) else Color.White, o)
            }
            val (a, b) = block.footers[k]
            nbText(measurer, a, mono(9.5f, true), Color(0xFFF2F3F7), Offset(cx(k), size.height - 20.dp.toPx()))
            nbText(measurer, b, mono(8.5f), muted, Offset(cx(k), size.height - 9.dp.toPx()))
        }
    }
}

@Composable
private fun NbTextView(block: NbBlock.Text) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val styles = NbTone.entries.associateWith { toneColors(it) }
    Box(modifier = Modifier.fillMaxWidth().background(nbWell, RoundedCornerShape(10.dp)).padding(horizontal = 12.dp, vertical = 10.dp)) {
        Text(
            buildAnnotatedString {
                block.spans.forEach { s ->
                    val (bg, fg) = styles.getValue(s.tone)
                    when (s.tone) {
                        NbTone.Plain -> withStyle(SpanStyle(color = onSurface)) { append(s.t) }
                        NbTone.Dim -> withStyle(SpanStyle(color = muted.copy(alpha = 0.6f))) { append(s.t) }
                        else -> withStyle(SpanStyle(background = bg, color = if (s.tone == NbTone.Hot) Color(0xFF1A1505) else fg)) { append(s.t) }
                    }
                }
            },
            fontFamily = if (block.mono) IBMPlexMono else null,
            fontSize = 15.sp, lineHeight = 28.sp,
        )
    }
}

@Composable
private fun NbFlowView(block: NbBlock.Flow) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            block.rows.forEach { line -> Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { line.forEach { NbTokView(it) } } }
        }
        Text("→", fontSize = 20.sp, color = muted, modifier = Modifier.padding(horizontal = 8.dp))
        Text(block.caption, fontSize = 11.sp, lineHeight = 14.sp, color = muted, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
        Text("→", fontSize = 20.sp, color = muted, modifier = Modifier.padding(horizontal = 8.dp))
        NbTokView(block.target)
    }
}

@Composable
private fun NbBannerView(block: NbBlock.Banner) {
    val ink = nbText(block.ink)
    Row(
        modifier = Modifier.fillMaxWidth().background(nbColor(block.ink).copy(alpha = 0.16f), RoundedCornerShape(12.dp)).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (block.bigRight) {
            Text(block.text, fontFamily = IBMPlexMono, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
            Text(block.big, fontFamily = SpaceGrotesk, fontWeight = FontWeight.Bold, fontSize = 28.sp, color = ink)
        } else {
            Text(block.big, fontFamily = SpaceGrotesk, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 26.sp, color = ink, modifier = Modifier.weight(0.45f))
            Text(block.text, fontSize = 13.sp, lineHeight = 17.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f), modifier = Modifier.weight(0.55f).padding(start = 10.dp))
        }
    }
}

@Composable
private fun NbModelsView(rows: List<NbModel>) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val onSurface = MaterialTheme.colorScheme.onSurface
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        rows.forEach { m ->
            val shape = RoundedCornerShape(10.dp)
            Row(
                modifier = Modifier.fillMaxWidth().background(nbWell, shape)
                    .then(if (m.ring) Modifier.border(1.5.dp, SimColors.Active, shape) else Modifier)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.width(92.dp)) {
                    Text(m.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = onSurface, maxLines = 1)
                    Text(m.sub, fontSize = 11.sp, color = muted, maxLines = 1)
                }
                listOf("enc" to m.enc, "dec" to m.dec).forEachIndexed { i, (s, on) ->
                    Box(
                        modifier = Modifier.padding(start = 4.dp).width(34.dp).height(22.dp)
                            .background(if (!on) nbWell else if (i == 0) nbColor(NbInk.Blue).copy(alpha = 0.55f) else nbColor(NbInk.Violet).copy(alpha = 0.7f), RoundedCornerShape(5.dp)),
                        contentAlignment = Alignment.Center,
                    ) { Text(s, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (on) Color.White else muted.copy(alpha = 0.5f)) }
                }
                Box(Modifier.weight(1f).padding(horizontal = 8.dp).height(6.dp).background(SimColors.Tint, RoundedCornerShape(3.dp))) {
                    Box(Modifier.fillMaxWidth(m.frac.toFloat().coerceIn(0.02f, 1f)).height(6.dp).background(if (m.ring) SimColors.Active else Color(0xFF6B7180), RoundedCornerShape(3.dp)))
                }
                Text(m.value, fontFamily = IBMPlexMono, fontSize = 12.sp, color = onSurface.copy(alpha = 0.85f), maxLines = 1)
            }
        }
    }
}

@Composable
private fun NbKvView(rows: List<Pair<String, String>>) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        rows.forEach { (k, v) ->
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(k, fontSize = 13.sp, color = muted, modifier = Modifier.width(130.dp))
                Text(v, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
            }
        }
    }
}

// ── Frame builders shared by the three frame files ──

internal fun nbTok(t: String, tone: NbTone = NbTone.Plain, sub: String = "") = NbTok(t, tone, sub)
internal fun nbLegend(ink: NbInk, label: String, style: SwatchStyle = SwatchStyle.Fill) = NbLegend(ink, label, style)
internal fun nbChip(key: String, value: String, tint: Boolean = false) = DkChip(key, value, tint)
internal fun nbCell(text: String, ink: NbInk? = null, bold: Boolean = false, sub: String = "", mono: Boolean = true) = NbCell(text, ink, bold, sub, null, mono)
internal fun nbName(text: String, ink: NbInk? = null, sub: String = "") = NbCell(text, ink, true, sub, null, false)
internal fun nbF(v: Double, d: Int = 2) = dkNum(v, d)

/** ".95" style: two decimals with the leading zero dropped. */
internal fun nbDot2(v: Double): String = dkNum(v, 2).let { if (it.startsWith("0.")) it.substring(1) else if (it.startsWith("−0.")) "−" + it.substring(2) else it }

internal fun nbGrouped(v: Long): String = v.toString().reversed().chunked(3).joinToString(",").reversed()

