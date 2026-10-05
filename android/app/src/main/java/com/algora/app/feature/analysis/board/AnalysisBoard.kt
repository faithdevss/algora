package com.algora.app.feature.analysis.board

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.algora.app.core.ui.theme.IBMPlexMono
import com.algora.app.core.ui.theme.LocalDarkTheme
import com.algora.app.core.ui.theme.SimColors
import com.algora.app.core.ui.theme.SpaceGrotesk
import com.algora.app.feature.topics.LabStoryNarration
import com.algora.app.feature.topics.rbColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ── Complexity & cost tools ──────────────────────────────────────────────────
// The Analysis topics as interactive boards (docs/ios-design/Simulations iOS 11): a card of labelled
// blocks — a probe strip, big numbers, bars, columns, growth curves, a timing table, a time/space duel,
// steppers, a verdict, complexity tiers — over the step's arithmetic and a caption. The controls live in
// a bottom dock (segmented rows, a slider, run buttons) and mark-complete moves to the nav bar. Every
// count comes from running the algorithm; the two timing tools sort real arrays on this device. Labs are
// built in AnalysisBoardLabs.kt; the iOS port (AnalysisBoard.swift) mirrors both files.

/** A tool's settings: the picked options, the slider index, a seed, the Master-theorem a/b/d, sandbox runs. */
internal data class AnVals(
    val opt: Int = 0,
    val opt2: Int = 0,
    val ni: Int = 0,
    val seed: Int = 1,
    val a: Int = 2,
    val b: Int = 2,
    val d: Int = 1,
    /** Sandbox runs as (algorithm, shape, n). */
    val runs: List<Triple<Int, Int, Int>> = emptyList(),
)

/** Measured µs per sort at n = 250 … 4,000, per algorithm ("ins", "bub", "sys"). */
internal typealias AnBench = Map<String, List<Double>>

internal class AnTok(val t: String, val bg: String, val color: String)

/** A strip cell over [l, l + w] of the track (fractions), at least 3dp wide. */
internal class AnCell(val l: Double, val w: Double, val bg: String)

internal class AnBig(val v: String, val label: String, val c: String)

internal class AnRow(val w: String, val v: String, val width: Double, val bar: String, val wc: String, val mk: Double? = null)

internal class AnVBar(val h: Double, val bg: String, val ring: String = "none")

internal class AnVCol(val label: String, val v: String, val bars: List<AnVBar>)

internal class AnPath(val pts: List<Pair<Double, Double>>, val c: String)

internal class AnGrid(val y: Double, val t: String)

internal class AnTRow(val a: String, val b: String, val c: String, val d: String, val dc: String)

internal class AnDuel(val name: String, val c: String, val bg: String, val time: String, val tSub: String, val space: String, val sSub: String)

internal class AnStep(val label: String, val sub: String, val v: String, val canDec: Boolean, val canInc: Boolean, val dec: (AnVals) -> AnVals, val inc: (AnVals) -> AnVals)

internal class AnTier(val label: String, val c: String, val bg: String, val items: List<String>, val ops: String, val time: String, val tc: String)

internal sealed interface AnBlock {
    class Label(val text: String) : AnBlock
    class Chips(val items: List<AnTok>) : AnBlock
    class Strip(val cells: List<AnCell>, val lo: String, val hi: String) : AnBlock
    class Big(val items: List<AnBig>) : AnBlock
    class Bars(val rows: List<AnRow>, val labelW: Int = 84, val valueW: Int = 64) : AnBlock
    class Cols(val cols: List<AnVCol>, val gap: Int = 6, val bw: Int? = 26, val axis: String = "") : AnBlock
    /** Curves in a [w] × [h] box, gridlines at y with labels. */
    class Lines(val h: Int, val w: Double, val paths: List<AnPath>, val grid: List<AnGrid>) : AnBlock
    class Stats(val rows: List<Triple<String, String, String>>) : AnBlock
    class Table(val head: List<String>, val rows: List<AnTRow>) : AnBlock
    class Duel(val items: List<AnDuel>) : AnBlock
    class Steps(val rows: List<AnStep>) : AnBlock
    class Res(val bg: String, val c: String, val top: String, val main: String, val sub: String) : AnBlock
    class Tiers(val rows: List<AnTier>) : AnBlock
}

internal class AnFx(val a: String, val b: String = "", val c: String = "#f2f3f7")

internal class AnSeg(val labels: List<String>, val selected: Int, val pick: (AnVals, Int) -> AnVals)

internal class AnSlider(val name: String, val labels: List<String>)

internal class AnBtn(val label: String, val bench: Boolean = false, val update: (AnVals) -> AnVals = { it })

internal class AnDock(val segs: List<AnSeg> = emptyList(), val slider: AnSlider? = null, val btn: AnBtn? = null, val alt: AnBtn? = null)

internal class AnFrame(
    val title: String,
    val blocks: List<AnBlock>,
    val fx: List<AnFx>,
    val capT: String,
    val capB: String,
    val legend: List<Triple<String, String, String>>,
    val dock: AnDock,
)

/** A tool: where it starts, whether it needs the on-device benchmark, and its frame for any settings. */
internal class AnLab(val init: AnVals, val bench: Boolean = false, val build: (AnVals, AnBench?) -> AnFrame)

// ── Page ──

@Composable
internal fun AnalysisBoardPage(
    topicId: String,
    onBack: () -> Unit,
    backTitle: String,
    isBookmarked: Boolean,
    onToggleBookmark: () -> Unit,
    isCompleted: Boolean,
    onToggleCompleted: () -> Unit,
) {
    val lab = remember(topicId) { analysisBoardLab(topicId) } ?: return
    var vals by remember(topicId) { mutableStateOf(lab.init) }
    var bench by remember(topicId) { mutableStateOf<AnBench?>(null) }
    val scope = rememberCoroutineScope()
    fun runBench() {
        bench = null
        scope.launch { bench = withContext(Dispatchers.Default) { runAnBench() } }
    }
    if (lab.bench) LaunchedEffect(topicId) { runBench() }
    val frame = lab.build(vals, bench)

    Column(modifier = Modifier.fillMaxSize()) {
        AnNavBar(frame.title, backTitle, onBack, isCompleted, onToggleCompleted, isBookmarked, onToggleBookmark)
        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Column(
                modifier = Modifier
                    .padding(top = 4.dp)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(20.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                frame.blocks.forEach { AnBlockView(it) { f -> vals = f(vals) } }
                if (frame.legend.isNotEmpty()) AnLegend(frame.legend)
            }
            if (frame.fx.isNotEmpty()) AnFormula(frame.fx, Modifier.padding(top = 8.dp))
            LabStoryNarration(frame.capT, frame.capB, Modifier.padding(top = 12.dp, start = 4.dp, end = 4.dp, bottom = 20.dp))
        }
        AnDockBar(frame.dock, vals, onChange = { vals = it }, onBench = { runBench() })
    }
}

@Composable
private fun AnNavBar(
    title: String,
    back: String,
    onBack: () -> Unit,
    done: Boolean,
    onToggleDone: () -> Unit,
    bookmarked: Boolean,
    onToggleBookmark: () -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary
    Box(modifier = Modifier.fillMaxWidth().padding(top = 4.dp).heightIn(min = 44.dp), contentAlignment = Alignment.Center) {
        Text(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 96.dp))
        Row(
            modifier = Modifier.align(Alignment.CenterStart).padding(start = 10.dp).heightIn(min = 44.dp).clickable(onClickLabel = "Back to $back", onClick = onBack),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBackIos, contentDescription = null, tint = primary, modifier = Modifier.size(20.dp))
        }
        Row(modifier = Modifier.align(Alignment.CenterEnd).padding(end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onToggleDone) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .then(if (done) Modifier.background(SimColors.Green, CircleShape) else Modifier.border(1.8.dp, primary, CircleShape)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.Check, contentDescription = if (done) "Mark not complete" else "Mark complete",
                        tint = if (done) Color.White else primary.copy(alpha = 0.55f), modifier = Modifier.size(16.dp),
                    )
                }
            }
            IconButton(onClick = onToggleBookmark) {
                Icon(if (bookmarked) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder, contentDescription = if (bookmarked) "Remove bookmark" else "Bookmark", tint = primary)
            }
        }
    }
}

// ── Blocks ──

private val anTrack @Composable get() = Color.Black.copy(alpha = 0.16f)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AnBlockView(block: AnBlock, onUpdate: ((AnVals) -> AnVals) -> Unit) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val onSurface = MaterialTheme.colorScheme.onSurface
    when (block) {
        is AnBlock.Label -> Text(block.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = muted)
        is AnBlock.Chips -> FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            block.items.forEach { t ->
                Box(
                    modifier = Modifier.heightIn(min = 30.dp).widthIn(min = 30.dp).background(rbColor(t.bg), RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(t.t, fontFamily = IBMPlexMono, fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold, color = rbColor(t.color))
                }
            }
        }
        is AnBlock.Strip -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth().height(28.dp).clip(RoundedCornerShape(8.dp)).background(anTrack)) {
                val w = maxWidth
                block.cells.forEach { c ->
                    Box(
                        Modifier.offset(x = w * c.l.toFloat(), y = 4.dp).width(maxOf(w * c.w.toFloat(), 3.dp)).height(20.dp)
                            .background(rbColor(c.bg), RoundedCornerShape(3.dp)),
                    )
                }
            }
            Row(Modifier.fillMaxWidth()) {
                Text(block.lo, fontFamily = IBMPlexMono, fontSize = 11.sp, color = muted.copy(alpha = 0.7f), modifier = Modifier.weight(1f))
                Text(block.hi, fontFamily = IBMPlexMono, fontSize = 11.sp, color = muted.copy(alpha = 0.7f))
            }
        }
        is AnBlock.Big -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            block.items.forEach { g ->
                Column(modifier = Modifier.weight(1f).background(anTrack, RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 10.dp)) {
                    Text(g.v, fontFamily = SpaceGrotesk, fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold, color = rbColor(g.c), maxLines = 1)
                    Text(g.label, fontSize = 12.sp, lineHeight = 16.sp, color = muted)
                }
            }
        }
        is AnBlock.Bars -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            block.rows.forEach { r ->
                Row(modifier = Modifier.fillMaxWidth().heightIn(min = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(r.w, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = rbColor(r.wc), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(block.labelW.dp))
                    BoxWithConstraints(modifier = Modifier.padding(horizontal = 8.dp).weight(1f).height(16.dp), contentAlignment = Alignment.CenterStart) {
                        val w = maxWidth
                        Box(Modifier.fillMaxWidth().height(8.dp).background(anTrack, RoundedCornerShape(4.dp)))
                        Box(Modifier.width(maxOf(w * r.width.toFloat(), 3.dp)).height(8.dp).background(rbColor(r.bar), RoundedCornerShape(4.dp)))
                        r.mk?.let { m -> Box(Modifier.offset(x = w * minOf(m, 1.0).toFloat() - 1.dp).width(2.dp).height(16.dp).background(SimColors.Active, RoundedCornerShape(1.dp))) }
                    }
                    Text(r.v, fontFamily = IBMPlexMono, fontSize = 12.sp, color = onSurface.copy(alpha = 0.8f), maxLines = 1, textAlign = TextAlign.End, modifier = Modifier.width(block.valueW.dp))
                }
            }
        }
        is AnBlock.Cols -> Column(
            modifier = Modifier.fillMaxWidth().background(anTrack, RoundedCornerShape(12.dp)).padding(start = 10.dp, end = 10.dp, top = 10.dp, bottom = 6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(block.gap.dp), verticalAlignment = Alignment.Bottom) {
                block.cols.forEach { c ->
                    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        if (c.v.isNotEmpty()) Text(c.v, fontFamily = IBMPlexMono, fontSize = 10.sp, lineHeight = 12.sp, color = onSurface.copy(alpha = 0.8f), maxLines = 1, softWrap = false)
                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.Bottom, modifier = Modifier.fillMaxWidth()) {
                            c.bars.forEach { v ->
                                val ring = v.ring != "none"
                                Box(
                                    modifier = Modifier
                                        .weight(1f, fill = block.bw == null)
                                        .then(if (block.bw != null) Modifier.widthIn(max = block.bw.dp) else Modifier)
                                        .height(v.h.dp)
                                        .background(rbColor(v.bg), RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp, bottomStart = 1.dp, bottomEnd = 1.dp))
                                        .then(if (ring) Modifier.border(1.5.dp, muted, RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp, bottomStart = 1.dp, bottomEnd = 1.dp)) else Modifier),
                                )
                            }
                        }
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            Row(horizontalArrangement = Arrangement.spacedBy(block.gap.dp)) {
                block.cols.forEach { c -> Text(c.label, fontFamily = IBMPlexMono, fontSize = 10.sp, color = muted, textAlign = TextAlign.Center, maxLines = 1, softWrap = false, modifier = Modifier.weight(1f)) }
            }
            if (block.axis.isNotEmpty()) Text(block.axis, fontSize = 11.sp, color = muted.copy(alpha = 0.7f), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
        is AnBlock.Lines -> {
            val gridColor = MaterialTheme.colorScheme.outline
            val colors = block.paths.map { rbColor(it.c) }
            BoxWithConstraints(modifier = Modifier.fillMaxWidth().height(block.h.dp).clip(RoundedCornerShape(12.dp)).background(anTrack)) {
                val sx = maxWidth.value / block.w.toFloat()
                androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                    val px = density
                    block.grid.forEach { g -> drawLine(gridColor, Offset(0f, g.y.toFloat() * px), Offset(size.width, g.y.toFloat() * px), strokeWidth = px) }
                    block.paths.forEachIndexed { i, p ->
                        val path = Path()
                        p.pts.forEachIndexed { j, (x, y) ->
                            val ox = x.toFloat() * sx * px
                            val oy = y.toFloat() * px
                            if (j == 0) path.moveTo(ox, oy) else path.lineTo(ox, oy)
                        }
                        drawPath(path, colors[i], style = Stroke(width = 2.5f * px, cap = StrokeCap.Round, join = StrokeJoin.Round))
                    }
                }
                block.grid.forEach { g ->
                    Text(g.t, fontFamily = IBMPlexMono, fontSize = 10.sp, color = muted.copy(alpha = 0.7f), modifier = Modifier.offset(x = 6.dp, y = (g.y - 13).dp))
                }
            }
        }
        is AnBlock.Stats -> Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            block.rows.forEach { (k, v, c) ->
                Row {
                    Text(k, fontSize = 14.sp, lineHeight = 22.sp, color = muted, modifier = Modifier.width(120.dp).padding(end = 8.dp))
                    Text(v, fontSize = 14.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold, color = rbColor(c))
                }
            }
        }
        is AnBlock.Table -> Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(Modifier.padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                block.head.forEachIndexed { i, h ->
                    Text(h, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = muted, textAlign = if (i == 0) TextAlign.Start else TextAlign.End, modifier = anTableCell(i))
                }
            }
            block.rows.forEach { r ->
                Row(
                    modifier = Modifier.fillMaxWidth().height(26.dp).background(SimColors.Tint.copy(alpha = 0.18f), RoundedCornerShape(7.dp)).padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(r.a, fontFamily = IBMPlexMono, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = anTableCell(0))
                    Text(r.b, fontFamily = IBMPlexMono, fontSize = 12.sp, color = rbColor("#5fd09f"), textAlign = TextAlign.End, modifier = anTableCell(1))
                    Text(r.c, fontFamily = IBMPlexMono, fontSize = 12.sp, color = onSurface.copy(alpha = 0.8f), textAlign = TextAlign.End, modifier = anTableCell(2))
                    Text(r.d, fontFamily = IBMPlexMono, fontSize = 12.sp, color = rbColor(r.dc), textAlign = TextAlign.End, modifier = anTableCell(3))
                }
            }
        }
        is AnBlock.Duel -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            block.items.forEach { u ->
                Column(modifier = Modifier.fillMaxWidth().background(rbColor(u.bg), RoundedCornerShape(14.dp)).padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(u.name, fontFamily = SpaceGrotesk, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = rbColor(u.c))
                    Row {
                        listOf(Triple("TIME", u.time, u.tSub), Triple("SPACE", u.space, u.sSub)).forEach { (k, v, sub) ->
                            Column(Modifier.weight(1f)) {
                                Text(k, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.4.sp, color = muted)
                                Text(v, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                                Text(sub, fontFamily = IBMPlexMono, fontSize = 12.sp, color = muted)
                            }
                        }
                    }
                }
            }
        }
        is AnBlock.Steps -> Column {
            block.rows.forEach { r ->
                Row(modifier = Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(r.label, fontFamily = IBMPlexMono, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    Text(r.sub, fontSize = 14.sp, color = muted, modifier = Modifier.padding(start = 8.dp).weight(1f))
                    Text(r.v, fontFamily = IBMPlexMono, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = rbColor("#b3abff"), modifier = Modifier.padding(end = 14.dp))
                    Row(modifier = Modifier.height(32.dp).width(94.dp).background(SimColors.Tint, RoundedCornerShape(9.dp)), verticalAlignment = Alignment.CenterVertically) {
                        AnStepBtn("−", r.canDec, Modifier.weight(1f)) { onUpdate(r.dec) }
                        Box(Modifier.width(1.dp).height(18.dp).background(muted.copy(alpha = 0.3f)))
                        AnStepBtn("+", r.canInc, Modifier.weight(1f)) { onUpdate(r.inc) }
                    }
                }
            }
        }
        is AnBlock.Res -> Column(
            modifier = Modifier.fillMaxWidth().background(rbColor(block.bg), RoundedCornerShape(14.dp)).padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(block.top, fontFamily = IBMPlexMono, fontSize = 12.sp, color = muted, textAlign = TextAlign.Center)
            Text(block.main, fontFamily = SpaceGrotesk, fontSize = 30.sp, fontWeight = FontWeight.Bold, color = rbColor(block.c))
            Text(block.sub, fontSize = 13.sp, color = onSurface.copy(alpha = 0.8f), textAlign = TextAlign.Center)
        }
        is AnBlock.Tiers -> Column {
            block.rows.forEachIndexed { i, t ->
                if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 0.5.dp)
                Column(modifier = Modifier.padding(vertical = 9.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).background(rbColor(t.c), CircleShape))
                        Text(t.label, fontFamily = SpaceGrotesk, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = rbColor(t.c), modifier = Modifier.padding(start = 8.dp).weight(1f))
                        Text(t.ops, fontFamily = IBMPlexMono, fontSize = 12.sp, color = muted)
                        Text(t.time, fontFamily = IBMPlexMono, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = rbColor(t.tc), textAlign = TextAlign.End, modifier = Modifier.widthIn(min = 86.dp))
                    }
                    FlowRow(modifier = Modifier.padding(start = 16.dp), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        t.items.forEach { x ->
                            Text(x, fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.background(rbColor(t.bg), RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 3.dp))
                        }
                    }
                }
            }
        }
    }
}

private fun androidx.compose.foundation.layout.RowScope.anTableCell(i: Int): Modifier = when (i) {
    0 -> Modifier.width(52.dp)
    3 -> Modifier.width(56.dp)
    else -> Modifier.weight(1f)
}

@Composable
private fun AnStepBtn(glyph: String, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(modifier = modifier.fillMaxHeight().clickable(enabled = enabled, onClick = onClick), contentAlignment = Alignment.Center) {
        Text(glyph, fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.35f))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AnLegend(items: List<Triple<String, String, String>>) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items.forEach { (c, label, ring) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(10.dp).background(rbColor(c), RoundedCornerShape(3.dp))
                        .then(if (ring != "none") Modifier.border(1.5.dp, muted, RoundedCornerShape(3.dp)) else Modifier),
                )
                Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f), modifier = Modifier.padding(start = 6.dp))
            }
        }
    }
}

@Composable
private fun AnFormula(lines: List<AnFx>, modifier: Modifier = Modifier) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier = modifier.fillMaxWidth().background(SimColors.Tint, RoundedCornerShape(12.dp)).padding(vertical = 8.dp, horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        lines.forEach { l ->
            val ink = rbColor(l.c)
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = muted)) { append(l.a) }
                    if (l.b.isNotEmpty()) {
                        append(" ")
                        withStyle(SpanStyle(color = ink, fontWeight = FontWeight.SemiBold)) { append(l.b) }
                    }
                },
                fontFamily = IBMPlexMono, fontSize = 13.sp, lineHeight = 19.5.sp,
            )
        }
    }
}

// ── Dock ──

@Composable
private fun AnDockBar(dock: AnDock, vals: AnVals, onChange: (AnVals) -> Unit, onBench: () -> Unit) {
    val dark = LocalDarkTheme.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .navigationBarsPadding()
            .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        dock.segs.forEach { sg ->
            Row(
                modifier = Modifier.fillMaxWidth().height(32.dp).background(SimColors.Tint, RoundedCornerShape(9.dp)).padding(2.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                sg.labels.forEachIndexed { i, l ->
                    val on = i == sg.selected
                    Box(
                        modifier = Modifier.weight(1f).fillMaxHeight()
                            .then(if (on) Modifier.background(if (dark) Color(0xFF636366) else Color.White, RoundedCornerShape(7.dp)) else Modifier)
                            .clip(RoundedCornerShape(7.dp))
                            .clickable { onChange(sg.pick(vals, i)) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(l, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, color = if (on) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        dock.slider?.let { sl ->
            Column {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(sl.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    Text(sl.labels[vals.ni], fontFamily = IBMPlexMono, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                }
                Slider(
                    value = vals.ni.toFloat(),
                    onValueChange = { v -> val i = Math.round(v); if (i != vals.ni) onChange(vals.copy(ni = i)) },
                    valueRange = 0f..(sl.labels.size - 1).toFloat(),
                    steps = (sl.labels.size - 2).coerceAtLeast(0),
                    colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.height(30.dp),
                )
            }
        }
        if (dock.btn != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.weight(1f).height(50.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.primary)
                        .clickable { if (dock.btn.bench) onBench() else onChange(dock.btn.update(vals)) },
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(dock.btn.label, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                }
                dock.alt?.let { alt ->
                    Box(
                        modifier = Modifier.height(50.dp).clip(RoundedCornerShape(14.dp)).background(SimColors.Tint).clickable { onChange(alt.update(vals)) }.padding(horizontal = 20.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(alt.label, color = MaterialTheme.colorScheme.primary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

// ── The on-device benchmark ──

private val BENCH_NS = listOf(250, 500, 1000, 2000, 4000)

/** Times insertion sort, bubble sort and the built-in sort at five sizes; the median of three, in µs per sort. */
internal fun runAnBench(): AnBench {
    val rnd = java.util.Random()
    fun ins(a: DoubleArray) {
        for (i in 1 until a.size) {
            val k = a[i]
            var j = i - 1
            while (j >= 0 && a[j] > k) { a[j + 1] = a[j]; j-- }
            a[j + 1] = k
        }
    }
    fun bub(a: DoubleArray) {
        val n = a.size
        for (i in 0 until n - 1) for (j in 0 until n - 1 - i) if (a[j] > a[j + 1]) { val t = a[j]; a[j] = a[j + 1]; a[j + 1] = t }
    }
    val f: Map<String, (DoubleArray) -> Unit> = linkedMapOf("ins" to ::ins, "bub" to ::bub, "sys" to { a -> a.sort() })
    val cost: Map<String, (Int) -> Double> = mapOf("ins" to { n -> n * n / 4.0 }, "bub" to { n -> n * n / 2.0 }, "sys" to { n -> n * (Math.log(n.toDouble()) / Math.log(2.0)) * 4 })
    return f.mapValues { (alg, fn) ->
        repeat(3) { fn(DoubleArray(600) { rnd.nextDouble() }) }
        BENCH_NS.map { n ->
            val base = DoubleArray(n) { rnd.nextDouble() }
            val m = maxOf(1, Math.round(1.5e6 / cost.getValue(alg)(n)).toInt())
            val ts = (0 until 3).map {
                val cp = Array(m) { base.copyOf() }
                val t0 = System.nanoTime()
                for (q in 0 until m) fn(cp[q])
                (System.nanoTime() - t0) / 1000.0 / m
            }.sorted()
            ts[1]
        }
    }
}
