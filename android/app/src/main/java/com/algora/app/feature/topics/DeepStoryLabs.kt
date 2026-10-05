package com.algora.app.feature.topics

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.algora.app.core.ui.theme.IBMPlexMono
import com.algora.app.core.ui.theme.SimColors
import kotlin.math.abs
import kotlin.math.max

// ── Deep-learning storyboards ────────────────────────────────────────────────
// The biological neuron, feedforward networks, vanishing and exploding gradients and the ten activation
// functions as step-by-step storyboards: a card with a titled stage (a plot, the softmax bars or a small
// network) over a legend, the step's arithmetic in a formula box, chips, and a headline. The controls
// are a step track with a labelled action, a step track over an "input z" stepper and Next, or a
// segmented picker over back and Next. The frames are built in DeepStoryFrames.kt.

internal val deepStoryTopicIds = setOf(
    "biological_neuron", "neural_network_basics", "vanishing_gradient", "exploding_gradient",
    "sigmoid", "tanh", "relu", "leaky_relu", "prelu", "elu", "selu", "swish", "gelu", "softmax",
) + cnnStoryTopicIds + detectStoryTopicIds + rnnStoryTopicIds + transformerStoryTopicIds + modernStoryTopicIds + optimStoryTopicIds

internal enum class DkInk { Blue, Pink, Green, Orange, Grey, Yellow, Violet, Slate, Sky }

internal class DkP(val x: Double, val y: Double)

internal class DkLine(val pts: List<DkP>, val ink: DkInk, val dashed: Boolean = false, val dots: Boolean = false)

internal class DkDot(val p: DkP, val ink: DkInk = DkInk.Yellow, val r: Float = 5f)

/** A horizontal rule across the plot (a threshold, a target). */
internal class DkRule(val y: Double, val ink: DkInk, val thin: Boolean = false)

/** A bracket over [x0, x1] at height [y], labelled under its left end ("19.9 ms"). */
internal class DkBracket(val x0: Double, val x1: Double, val y: Double, val label: String)

internal sealed interface DkStage

internal class DkPlot(
    val xr: Pair<Double, Double>,
    val yr: Pair<Double, Double>,
    val yTicks: List<Pair<Double, String>>,
    val xLeft: String,
    val xRight: String,
    val lines: List<DkLine>,
    val dots: List<DkDot> = emptyList(),
    val rules: List<DkRule> = emptyList(),
    // The vertical axis at x = 0, when 0 is on the plot.
    val axis: Boolean = true,
    // A dashed yellow line at x, labelled under the plot ("z = 3").
    val guide: Pair<Double, String>? = null,
    val bracket: DkBracket? = null,
    // Data points, small and grey.
    val scatter: List<DkP> = emptyList(),
    val height: Int = 200,
    // Bars rising from the bottom of the range (a per-timestep gradient).
    val bars: List<DkPBar> = emptyList(),
    // Labels under given x positions in place of the two end labels; a hot one is yellow ("t1").
    val xTicks: List<DkTick> = emptyList(),
    // Loss contours: ellipses around the origin with these x and y half-widths.
    val ellipses: List<Pair<Double, Double>> = emptyList(),
) : DkStage

internal class DkPBar(val x: Double, val y: Double, val ink: DkInk, val width: Double = 0.62)

internal class DkTick(val x: Double, val label: String, val hot: Boolean = false)

/** Softmax: the logits on a zero line above, a second row of bars (e^z or p) below; [top] is yellow. */
internal class DkBars(
    val logits: List<Double>,
    val lower: List<Double>,
    val lowerLabel: String,
    val lowerDigits: Int,
    val labels: List<String>,
    val top: Int?,
) : DkStage

internal enum class DkTone { Input, Hidden, Off, Current, Ghost, Output }

internal class DkNode(val x: Float, val y: Float, val text: String, val tone: DkTone, val note: String? = null)

internal enum class DkEdgeState { Faint, Lit, Silenced }

internal class DkEdge(val from: Int, val to: Int, val w: Double, val state: DkEdgeState, val label: String? = null)

internal class DkNet(val nodes: List<DkNode>, val edges: List<DkEdge>, val footers: List<Pair<Float, String>>) : DkStage

internal class DkLegend(val ink: DkInk, val style: SwatchStyle, val label: String)

internal class DkChip(val key: String, val value: String, val tint: Boolean = false)

internal class DkFrame(
    val header: String?,
    val stage: DkStage,
    val legend: List<DkLegend>,
    val formula: List<String>,
    val headline: String,
    val body: String,
    val chips: List<DkChip> = emptyList(),
    val action: String = "Next",
)

// StepperOnly: the stepper beside Next with no step track; Next walks the steps and wraps.
internal enum class DkControl { Track, Stepper, StepperOnly, Tabs }

internal class DkStepper(val caption: String, val values: List<Double>, val initial: Int, val format: (Double) -> String)

internal class DkLab(
    val control: DkControl,
    val tabs: List<String> = emptyList(),
    val initialTab: Int = 0,
    val stepper: DkStepper? = null,
    val frames: (tab: Int, param: Int) -> List<DkFrame>,
)

/** Builds every frame each control can reach (every tab and stepper value). */
internal fun deepStoryFrameCount(topicId: String): Int {
    val lab = deepLab(topicId)
    var total = 0
    var count = -1
    for (t in 0 until max(lab.tabs.size, 1)) for (p in 0 until (lab.stepper?.values?.size ?: 1)) {
        val frames = lab.frames(t, p)
        require(frames.isNotEmpty()) { "$topicId built no frames at tab $t, value $p" }
        // The step track is shared across stepper values and tabs, so the count can't change with them.
        require(count < 0 || frames.size == count) { "$topicId changes its step count at tab $t, value $p" }
        count = frames.size
        frames.forEach { f ->
            require(f.headline.isNotBlank() && f.body.isNotBlank() && f.action.isNotBlank()) { "$topicId has an empty step at tab $t, value $p" }
            (f.stage as? DkPlot)?.let { plot ->
                require(plot.lines.all { l -> l.pts.all { it.x.isFinite() && it.y.isFinite() } }) { "$topicId drew a non-finite point at tab $t, value $p" }
            }
        }
        total += frames.size
    }
    return total
}

// ── Lab ──

@Composable
internal fun DeepStorySection(topicId: String) {
    val lab = remember(topicId) { deepLab(topicId) }
    var tab by rememberSaveable(topicId) { mutableIntStateOf(lab.initialTab) }
    var param by rememberSaveable(topicId) { mutableIntStateOf(lab.stepper?.initial ?: 0) }
    var index by rememberSaveable(topicId) { mutableIntStateOf(0) }
    // Some labs train a small model or run a long simulation, so frames are built off the main thread.
    val built by androidx.compose.runtime.produceState<List<DkFrame>?>(null, topicId, tab, param) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { lab.frames(tab, param) }
    }
    val frames = built ?: run {
        DkLoading()
        return
    }
    val playback = rememberPlaybackState(key = topicId, stepCount = frames.size, initialSpeedMs = 1000f)
    val dock = LocalLabDock.current
    val at = (if (lab.control == DkControl.Tabs || lab.control == DkControl.StepperOnly) index else playback.index).coerceIn(0, frames.lastIndex)
    val captions = frames.map { storyPlain(it.headline) }

    if (lab.control == DkControl.Track) {
        Column(modifier = Modifier.fillMaxWidth()) {
            DkBody(frames[at])
            PlaybackTransport(playback, captions = captions, action = { frames[it.coerceIn(0, frames.lastIndex)].action })
        }
        return
    }

    val controls: @Composable () -> Unit = {
        if (lab.control == DkControl.Stepper) {
            val s = lab.stepper!!
            LabTransportBar(playback, captions, footer = {
                DkStepperNext(
                    s.caption, s.format(s.values[param]), param > 0, param < s.values.lastIndex,
                    onStep = { param = (param + it).coerceIn(0, s.values.lastIndex) },
                    onNext = { if (playback.atEnd) playback.reset() else playback.stepForward() },
                )
            })
        } else if (lab.control == DkControl.StepperOnly) {
            val s = lab.stepper!!
            DkStepperNext(
                s.caption, s.format(s.values[param]), param > 0, param < s.values.lastIndex,
                onStep = { param = (param + it).coerceIn(0, s.values.lastIndex) },
                onNext = { index = if (at >= frames.lastIndex) 0 else at + 1 },
            )
        } else {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                LabSegments(lab.tabs, tab) { tab = it }
                LabBackActionRow(
                    frames[at].action,
                    backEnabled = at > 0,
                    onBack = { index = at - 1 },
                    onAction = { index = if (at >= frames.lastIndex) 0 else at + 1 },
                )
            }
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        DkBody(frames[at])
        if (dock == null) {
            HorizontalDivider(modifier = Modifier.padding(top = 16.dp, bottom = 14.dp), color = MaterialTheme.colorScheme.outline)
            controls()
        }
    }

    if (dock != null) {
        SideEffect {
            dock.controls = controls
            dock.navAction = LabNavAction(Icons.Filled.Refresh, "Reset") {
                tab = lab.initialTab
                param = lab.stepper?.initial ?: 0
                index = 0
                playback.reset()
            }
        }
        DisposableEffect(dock) {
            onDispose {
                dock.controls = null
                dock.navAction = null
            }
        }
    }
}

/** "− input z 3.0 +" in one tinted box, beside the primary Next. */
@Composable
private fun DkStepperNext(
    caption: String,
    value: String,
    canDecrease: Boolean,
    canIncrease: Boolean,
    onStep: (Int) -> Unit,
    onNext: () -> Unit,
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val onSurface = MaterialTheme.colorScheme.onSurface
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Row(
            modifier = Modifier.weight(1f).height(52.dp).clip(RoundedCornerShape(14.dp)).background(SimColors.Tint),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            listOf(-1 to "−", 1 to "+").forEachIndexed { i, (delta, glyph) ->
                val enabled = if (delta < 0) canDecrease else canIncrease
                if (i == 1) {
                    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(caption, fontSize = 11.sp, color = muted, maxLines = 1)
                        Text(value, fontFamily = IBMPlexMono, fontSize = if (value.length > 4) 16.sp else 19.sp, fontWeight = FontWeight.Bold, color = onSurface, maxLines = 1)
                    }
                }
                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .fillMaxHeight()
                        .clickable(enabled = enabled, onClickLabel = if (delta < 0) "Decrease $caption" else "Increase $caption") { onStep(delta) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(glyph, fontSize = 24.sp, fontWeight = FontWeight.Medium, color = onSurface.copy(alpha = if (enabled) 1f else 0.3f))
                }
            }
        }
        LabButton("Next", primary = true, modifier = Modifier.padding(start = 12.dp).weight(1.35f), onClick = onNext)
    }
}

@Composable
private fun DkBody(frame: DkFrame) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            frame.header?.let {
                Text(
                    it, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2, modifier = Modifier.padding(bottom = 10.dp),
                )
            }
            when (val stage = frame.stage) {
                is DkPlot -> DkPlotView(stage)
                is DkBars -> DkBarsView(stage)
                is DkNet -> DkNetView(stage)
                is DkGrids -> DkGridsView(stage)
                is DkRows -> DkRowsView(stage)
                is DkSegs -> DkSegsView(stage)
                is DkBoxes -> DkBoxesView(stage)
                is DkUNet -> DkUNetView(stage)
                is DkTiles -> DkTilesView(stage)
                is DkPipeline -> DkPipelineView(stage)
                is DkHist -> DkHistView(stage)
                is DkTokens -> DkTokensView(stage)
                is DkGraph -> DkGraphView(stage)
            }
            if (frame.legend.isNotEmpty()) {
                StoryLegendRow(frame.legend.map { Triple(dkColor(it.ink), it.style, it.label) }, Modifier.padding(top = 14.dp))
            }
        }
    }
    if (frame.formula.isNotEmpty()) DkFormula(frame.formula, Modifier.padding(top = 12.dp))
    LabChips(
        frame.chips.map { LabChip(it.key, it.value, tint = if (it.tint) StoryTone.Answer else null) },
        Modifier.padding(top = 12.dp),
    )
    LabStoryNarration(frame.headline, frame.body, Modifier.padding(top = 16.dp))
}

@Composable
private fun DkFormula(lines: List<String>, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(SimColors.Tint, RoundedCornerShape(10.dp))
            .padding(vertical = 10.dp, horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        lines.forEach { line ->
            Text(
                storyAnnotated(line),
                fontFamily = IBMPlexMono,
                fontSize = 13.5.sp,
                lineHeight = 20.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
            )
        }
    }
}

// ── Rendering ──

private val DkBlue = Color(0xFF4F7FE0)
private val DkPink = Color(0xFFD6457A)
private val DkGreen = Color(0xFF4CAF6E)
private val DkOrange = Color(0xFFF08A3C)
private val DkGrey = Color(0xFF8A8F99)
private val DkOffFill = Color(0xFF3A3F4A)

internal fun dkColor(ink: DkInk): Color = when (ink) {
    DkInk.Blue -> DkBlue
    DkInk.Pink -> DkPink
    DkInk.Green -> DkGreen
    DkInk.Orange -> DkOrange
    DkInk.Grey -> DkGrey
    DkInk.Yellow -> SimColors.Active
    DkInk.Violet -> SimColors.Answer
    DkInk.Slate -> DkOffFill
    DkInk.Sky -> Color(0xFF8AA4E8)
}

internal fun Modifier.dkStage() = this.clip(RoundedCornerShape(12.dp)).background(Color.Black.copy(alpha = 0.16f))

private fun DrawScope.label(
    measurer: TextMeasurer,
    text: String,
    style: TextStyle,
    color: Color,
    at: Offset,
    // 0 centres the text on [at]; −1 ends it there; 1 starts it there.
    anchor: Int = 0,
) {
    val layout = measurer.measure(text, style)
    val x = when (anchor) {
        -1 -> at.x - layout.size.width
        1 -> at.x
        else -> at.x - layout.size.width / 2f
    }
    drawText(layout, color = color, topLeft = Offset(x, at.y - layout.size.height / 2f))
}

@Composable
private fun DkPlotView(plot: DkPlot) {
    val measurer = rememberTextMeasurer()
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(modifier = Modifier.fillMaxWidth().height(plot.height.dp).dkStage()) {
        val tickStyle = TextStyle(fontFamily = IBMPlexMono, fontSize = 10.5.sp)
        val tickW = plot.yTicks.maxOfOrNull { measurer.measure(it.second, tickStyle).size.width } ?: 0
        val left = tickW + 18.dp.toPx()
        val right = size.width - 14.dp.toPx()
        val top = 14.dp.toPx()
        val bottom = size.height - 24.dp.toPx()
        val (xLo, xHi) = plot.xr
        val (yLo, yHi) = plot.yr
        fun px(x: Double) = left + ((x - xLo) / (xHi - xLo)).toFloat() * (right - left)
        fun py(y: Double) = bottom - ((y - yLo) / (yHi - yLo)).toFloat() * (bottom - top)
        fun at(p: DkP) = Offset(px(p.x), py(p.y))
        val dash = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx()))

        plot.yTicks.forEach { (v, text) ->
            drawLine(muted.copy(alpha = 0.14f), Offset(left, py(v)), Offset(right, py(v)), 1.dp.toPx())
            label(measurer, text, tickStyle, muted, Offset(left - 8.dp.toPx(), py(v)), anchor = -1)
        }
        if (plot.axis && xLo < 0 && xHi > 0) {
            drawLine(muted.copy(alpha = 0.3f), Offset(px(0.0), top), Offset(px(0.0), bottom), 1.dp.toPx())
        }
        val xStyle = TextStyle(fontFamily = IBMPlexMono, fontSize = 10.5.sp)
        val xY = bottom + 12.dp.toPx()
        if (plot.xTicks.isEmpty()) {
            label(measurer, plot.xLeft, xStyle, muted, Offset(left, xY), anchor = 1)
            label(measurer, plot.xRight, xStyle, muted, Offset(right, xY), anchor = -1)
        } else {
            plot.xTicks.forEach { t ->
                label(measurer, t.label, if (t.hot) xStyle.copy(fontWeight = FontWeight.Bold) else xStyle, if (t.hot) SimColors.Active else muted, Offset(px(t.x), xY))
            }
        }
        plot.bars.forEach { b ->
            val x0 = px(b.x - b.width / 2)
            val x1 = px(b.x + b.width / 2)
            val y = py(b.y)
            drawRoundRect(dkColor(b.ink).copy(alpha = 0.85f), Offset(x0, y), Size(x1 - x0, bottom - y), CornerRadius(3.dp.toPx()))
        }

        plot.rules.forEach { r ->
            drawLine(
                dkColor(r.ink).copy(alpha = if (r.thin) 0.55f else 1f), Offset(left, py(r.y)), Offset(right, py(r.y)),
                (if (r.thin) 1f else 1.5f).dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())),
            )
        }
        plot.guide?.let { (x, text) ->
            drawLine(SimColors.Active, Offset(px(x), top), Offset(px(x), bottom), 1.2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx())))
            val style = TextStyle(fontFamily = IBMPlexMono, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
            val w = measurer.measure(text, style).size.width
            // Kept clear of the end labels ("−4", "+4") when it lands near an edge.
            val lw = measurer.measure(plot.xLeft, xStyle).size.width + 8.dp.toPx()
            val rw = measurer.measure(plot.xRight, xStyle).size.width + 8.dp.toPx()
            val lo = left + lw + w / 2f
            val hi = right - rw - w / 2f
            val cx = if (lo <= hi) px(x).coerceIn(lo, hi) else px(x)
            label(measurer, text, style, SimColors.Active, Offset(cx, xY))
        }
        clipRect(left, top, right, bottom) {
            plot.ellipses.forEach { (rx, ry) ->
                drawOval(muted.copy(alpha = 0.25f), topLeft = Offset(px(-rx), py(ry)), size = Size(px(rx) - px(-rx), py(-ry) - py(ry)), style = Stroke(1.dp.toPx()))
            }
        }
        plot.scatter.forEach { drawCircle(DkGrey.copy(alpha = 0.7f), 2.5.dp.toPx(), at(it)) }
        clipRect(left, top - 2.dp.toPx(), right, bottom + 2.dp.toPx()) {
            plot.lines.forEach { line ->
                if (line.pts.size < 2) return@forEach
                val path = Path().apply {
                    moveTo(at(line.pts[0]).x, at(line.pts[0]).y)
                    line.pts.drop(1).forEach { lineTo(px(it.x), py(it.y)) }
                }
                drawPath(
                    path, dkColor(line.ink),
                    style = Stroke(
                        (if (line.dashed) 1.8f else 2.5f).dp.toPx(), cap = if (line.dashed) StrokeCap.Butt else StrokeCap.Round,
                        join = StrokeJoin.Round, pathEffect = if (line.dashed) dash else null,
                    ),
                )
                if (line.dots) line.pts.forEach { drawCircle(dkColor(line.ink), 3.dp.toPx(), at(it)) }
            }
        }
        plot.bracket?.let { b ->
            val y = py(b.y)
            val tick = 5.dp.toPx()
            drawLine(SimColors.Active, Offset(px(b.x0), y), Offset(px(b.x1), y), 2.dp.toPx())
            drawLine(SimColors.Active, Offset(px(b.x0), y), Offset(px(b.x0), y + tick), 2.dp.toPx())
            drawLine(SimColors.Active, Offset(px(b.x1), y), Offset(px(b.x1), y + tick), 2.dp.toPx())
            label(
                measurer, b.label, TextStyle(fontFamily = IBMPlexMono, fontSize = 11.sp, fontWeight = FontWeight.Bold),
                SimColors.Active, Offset(px(b.x0) + 4.dp.toPx(), y + 13.dp.toPx()), anchor = 1,
            )
        }
        plot.dots.forEach { d -> drawCircle(dkColor(d.ink), d.r.dp.toPx(), at(d.p)) }
    }
}

@Composable
private fun DkBarsView(bars: DkBars) {
    val measurer = rememberTextMeasurer()
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val onSurface = MaterialTheme.colorScheme.onSurface
    Canvas(modifier = Modifier.fillMaxWidth().height(206.dp).dkStage()) {
        val n = bars.logits.size
        val padX = 12.dp.toPx()
        val slot = (size.width - 2 * padX) / n
        val barW = slot * 0.62f
        fun cx(i: Int) = padX + slot * (i + 0.5f)
        val small = TextStyle(fontFamily = IBMPlexMono, fontSize = 11.sp)
        val bold = small.copy(fontWeight = FontWeight.Bold)

        // Upper: the logits from a zero line, negative ones hanging below it.
        label(measurer, "logits z", small, muted, Offset(padX, 12.dp.toPx()), anchor = 1)
        val zero = 72.dp.toPx()
        val unit = 22.dp.toPx() / bars.logits.maxOf { abs(it) }.coerceAtLeast(1e-9).toFloat()
        drawLine(muted.copy(alpha = 0.3f), Offset(padX, zero), Offset(size.width - padX, zero), 1.dp.toPx())
        bars.logits.forEachIndexed { i, v ->
            val h = (v * unit).toFloat()
            val y = if (h >= 0) zero - h else zero
            drawRoundRect(DkGrey, Offset(cx(i) - barW / 2, y), Size(barW, abs(h).coerceAtLeast(2.dp.toPx())), CornerRadius(3.dp.toPx()))
            val ly = if (h >= 0) zero - h - 9.dp.toPx() else zero - h + 9.dp.toPx()
            label(measurer, fx1(v), small, onSurface.copy(alpha = 0.8f), Offset(cx(i), ly))
        }

        // Lower: e^z or the probabilities, on a shared baseline over the class names.
        label(measurer, bars.lowerLabel, small, muted, Offset(padX, 104.dp.toPx()), anchor = 1)
        val base = size.height - 22.dp.toPx()
        val maxH = 46.dp.toPx()
        val top = bars.lower.max().coerceAtLeast(1e-9)
        drawLine(muted.copy(alpha = 0.3f), Offset(padX, base), Offset(size.width - padX, base), 1.dp.toPx())
        bars.lower.forEachIndexed { i, v ->
            val h = (v / top).toFloat() * maxH
            val hot = i == bars.top
            drawRoundRect(if (hot) SimColors.Active else DkBlue, Offset(cx(i) - barW / 2, base - h), Size(barW, h.coerceAtLeast(2.dp.toPx())), CornerRadius(3.dp.toPx()))
            label(
                measurer, dkNum(v, bars.lowerDigits), if (hot) bold else small,
                if (hot) SimColors.Active else onSurface.copy(alpha = 0.8f), Offset(cx(i), base - h - 9.dp.toPx()),
            )
            label(measurer, bars.labels[i], small, muted, Offset(cx(i), base + 11.dp.toPx()))
        }
    }
}

@Composable
private fun DkNetView(net: DkNet) {
    val measurer = rememberTextMeasurer()
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val stageColor = MaterialTheme.colorScheme.surface
    Canvas(modifier = Modifier.fillMaxWidth().height(236.dp).dkStage()) {
        val r = 22.dp.toPx()
        val padX = r + 22.dp.toPx()
        val top = r + 12.dp.toPx()
        val bottom = size.height - r - 24.dp.toPx()
        fun at(n: DkNode) = Offset(padX + n.x * (size.width - 2 * padX), top + n.y * (bottom - top))
        val labelStyle = TextStyle(fontFamily = IBMPlexMono, fontSize = 12.sp, fontWeight = FontWeight.Medium)

        net.edges.sortedBy { it.state == DkEdgeState.Lit }.forEach { e ->
            val a = at(net.nodes[e.from])
            val b = at(net.nodes[e.to])
            val base = if (e.w >= 0) DkBlue else DkOrange
            val width = (1.2 + 1.6 * abs(e.w)).toFloat().dp.toPx()
            when (e.state) {
                DkEdgeState.Silenced -> drawLine(
                    DkGrey.copy(alpha = 0.7f), a, b, 2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 4.dp.toPx())),
                )
                DkEdgeState.Faint -> drawLine(base.copy(alpha = 0.45f), a, b, width, cap = StrokeCap.Round)
                DkEdgeState.Lit -> drawLine(base, a, b, width + 0.8.dp.toPx(), cap = StrokeCap.Round)
            }
        }
        net.edges.filter { it.label != null }.forEach { e ->
            val a = at(net.nodes[e.from])
            val b = at(net.nodes[e.to])
            val m = Offset(a.x + (b.x - a.x) * 0.5f, a.y + (b.y - a.y) * 0.5f)
            val layout = measurer.measure(e.label!!, labelStyle)
            drawRoundRect(
                stageColor,
                topLeft = Offset(m.x - layout.size.width / 2f - 4.dp.toPx(), m.y - layout.size.height / 2f),
                size = Size(layout.size.width + 8.dp.toPx(), layout.size.height.toFloat()),
                cornerRadius = CornerRadius(4.dp.toPx()),
            )
            drawText(
                layout, color = if (e.state == DkEdgeState.Silenced) muted else Color.White,
                topLeft = Offset(m.x - layout.size.width / 2f, m.y - layout.size.height / 2f),
            )
        }
        val nodeStyle = TextStyle(fontFamily = IBMPlexMono, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        net.nodes.forEach { n ->
            val c = at(n)
            when (n.tone) {
                DkTone.Input -> drawCircle(DkBlue, r, c)
                DkTone.Hidden -> drawCircle(DkGreen, r, c)
                DkTone.Output -> drawCircle(SimColors.Answer, r, c)
                DkTone.Off -> drawCircle(DkOffFill, r, c)
                DkTone.Current -> {
                    drawCircle(stageColor, r, c)
                    drawCircle(SimColors.Active, r - 1.25.dp.toPx(), c, style = Stroke(2.5.dp.toPx()))
                }
                DkTone.Ghost -> {
                    drawCircle(stageColor, r, c)
                    drawCircle(
                        muted.copy(alpha = 0.6f), r - 0.75.dp.toPx(), c,
                        style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))),
                    )
                }
            }
            val ink = when (n.tone) {
                DkTone.Off, DkTone.Ghost -> muted
                DkTone.Current -> SimColors.Active
                else -> Color.White
            }
            label(measurer, n.text, nodeStyle, ink, c)
            n.note?.let {
                label(
                    measurer, it, TextStyle(fontFamily = IBMPlexMono, fontSize = 11.sp), DkOrange,
                    Offset(c.x + r + 4.dp.toPx(), c.y + r * 0.45f), anchor = 1,
                )
            }
        }
        val footStyle = TextStyle(fontSize = 12.sp)
        net.footers.forEach { (x, text) ->
            label(measurer, text, footStyle, muted, Offset(padX + x * (size.width - 2 * padX), size.height - 12.dp.toPx()))
        }
    }
}

// ── Number text shared with the frames ──

/** Fixed decimals with a real minus sign, rounded half away from zero on both platforms. */
internal fun dkNum(v: Double, d: Int): String {
    var p = 1L
    repeat(d) { p *= 10 }
    val r = kotlin.math.floor(abs(v) * p + 0.5).toLong()
    val body = if (d == 0) "${r / p}" else "${r / p}." + (r % p).toString().padStart(d, '0')
    return if (v < 0 && r != 0L) "−$body" else body
}

private fun fx1(v: Double) = dkNum(v, 1)

/** The card's place while a lab's frames are computed. */
@Composable
private fun DkLoading() {
    Surface(
        modifier = Modifier.fillMaxWidth().height(320.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Box(contentAlignment = Alignment.Center) {
            androidx.compose.material3.CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.5.dp)
        }
    }
}
