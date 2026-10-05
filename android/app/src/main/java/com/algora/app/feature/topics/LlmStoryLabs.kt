package com.algora.app.feature.topics

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.algora.app.core.ui.theme.IBMPlexMono
import com.algora.app.core.ui.theme.SimColors
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.min

// ── Fine-tuning, beyond-transformer and NLP-metric storyboards ───────────────
// Seventeen labs (full fine-tuning to MMLU) as one card of stacked blocks — a caption, token chips,
// labelled bars, a histogram, a scatter plot or key/value stats — over a legend, the step's arithmetic
// in a formula box and a headline. The dock is an optional picker over the play transport; the picker
// re-runs the same steps on another setting. Frames: TuneStoryFrames.kt (fine-tuning and efficiency)
// and BeyondStoryFrames.kt (beyond transformers and the metrics); the iOS port mirrors both.

internal val llmStoryTopicIds = tuneStoryTopicIds + beyondStoryTopicIds

internal fun llmLab(topicId: String): LsLab =
    tuneLab(topicId) ?: beyondLab(topicId) ?: error("No LLM storyboard for $topicId")

internal enum class LsInk { Blue, Pink, Green, Red, Violet, Lilac, Yellow, Slate, Grey }

/** Token chip states: plain, the one being read, done, not reached, the answer, an error. */
internal enum class LsTone { Plain, Cur, Done, Fut, Ans, Err }

/** A bar row's name: emphasised (the picked one), normal, or muted. */
internal enum class LsText { Strong, Normal, Muted }

internal class LsTok(val t: String, val tone: LsTone = LsTone.Plain, val sub: String = "")

/** A labelled bar over [from, from + width] of the track (fractions); [mid] draws a zero line at the centre. */
internal class LsRow(
    val w: String,
    val v: String,
    val from: Double,
    val width: Double,
    val ink: LsInk,
    val text: LsText = LsText.Normal,
    val mid: Boolean = false,
)

/** A scatter point at fractions of the plot (y up); a ringed big one is a training point. */
internal class LsPt(val x: Double, val y: Double, val ink: LsInk, val big: Boolean = false)

internal class LsStat(val k: String, val v: String, val ink: LsInk? = null)

internal sealed interface LsBlock {
    class Label(val text: String) : LsBlock
    class Chips(val toks: List<LsTok>) : LsBlock
    class Bars(val rows: List<LsRow>) : LsBlock
    /** Column heights as fractions of the tallest. */
    class Hist(val heights: List<Double>) : LsBlock
    class Plot(val height: Int, val pts: List<LsPt>) : LsBlock
    class Stats(val rows: List<LsStat>) : LsBlock
}

/** A formula line: [a] muted, then [b] bold in [ink] (the line's result). */
internal class LsFx(val a: String, val b: String = "", val ink: LsInk? = null)

internal class LsFrame(
    val blocks: List<LsBlock>,
    val fx: List<LsFx>,
    val headline: String,
    val body: String,
)

internal class LsLab(
    val tabs: List<String>,
    val initialTab: Int,
    val legend: List<Pair<LsInk, String>>,
    val frames: (tab: Int) -> List<LsFrame>,
)

/** Builds every frame of every tab, checking the step count holds across the picker. */
internal fun llmStoryFrameCount(topicId: String): Int {
    val lab = llmLab(topicId)
    var count = -1
    var total = 0
    for (t in 0 until maxOf(lab.tabs.size, 1)) {
        val frames = lab.frames(t)
        require(frames.isNotEmpty()) { "$topicId built no frames at tab $t" }
        require(count < 0 || frames.size == count) { "$topicId changes its step count at tab $t" }
        count = frames.size
        frames.forEachIndexed { i, f ->
            require(f.headline.isNotBlank() && f.body.isNotBlank()) { "$topicId has an empty caption at tab $t, step $i" }
            f.blocks.filterIsInstance<LsBlock.Bars>().flatMap { it.rows }.forEach { r ->
                require(r.from.isFinite() && r.width.isFinite()) { "$topicId drew a non-finite bar at tab $t, step $i" }
            }
        }
        total += frames.size
    }
    return total
}

// ── Number text shared by the frames (the design's helpers) ──

/** Fixed decimals with a real minus sign; a value that rounds to zero prints unsigned. */
internal fun lsF(x: Double, d: Int = 2): String {
    var p = 1.0
    repeat(d) { p *= 10 }
    val r = floor(abs(x) * p + 0.5)
    if (r == 0.0) return if (d == 0) "0" else "0." + "0".repeat(d)
    val whole = (r / p).toLong()
    val frac = (r - whole * p).toLong()
    val body = if (d == 0) "$whole" else "$whole." + frac.toString().padStart(d, '0')
    return if (x < 0) "−$body" else body
}

internal fun lsPct(p: Double): String = if (p == 0.0) "0%" else lsF(p * 100, if (p < 0.1) 1 else 0) + "%"

internal fun lsNum(x: Double): String {
    val r = Math.round(x)
    val s = abs(r).toString().reversed().chunked(3).joinToString(",").reversed()
    return if (r < 0) "−$s" else s
}

internal fun lsBig(n: Double): String {
    fun g(x: Double): String {
        val s = lsF(x, if (x < 10) 2 else if (x < 100) 1 else 0)
        return if (s.contains('.')) s.trimEnd('0').trimEnd('.') else s
    }
    return when {
        n >= 1e12 -> g(n / 1e12) + "T"
        n >= 1e9 -> g(n / 1e9) + "B"
        n >= 1e6 -> g(n / 1e6) + "M"
        n >= 1e3 -> g(n / 1e3) + "K"
        else -> g(n)
    }
}

/** "1.2e12": one decimal of mantissa, no plus sign. */
internal fun lsSci(x: Double): String {
    if (x == 0.0) return "0.0e0"
    var e = floor(kotlin.math.log10(abs(x))).toInt()
    var m = x / Math.pow(10.0, e.toDouble())
    if (abs(lsF(m, 1).replace("−", "").toDouble()) >= 10) { e += 1; m = x / Math.pow(10.0, e.toDouble()) }
    return lsF(m, 1) + "e" + (if (e < 0) "−${-e}" else "$e")
}

/** "2.38e-4" style with two decimals, as toExponential(2). */
internal fun lsExp(x: Double): String {
    if (x == 0.0) return "0.00e+0"
    var e = floor(kotlin.math.log10(abs(x))).toInt()
    var m = x / Math.pow(10.0, e.toDouble())
    if (abs(lsF(m, 2).replace("−", "").toDouble()) >= 10) { e += 1; m = x / Math.pow(10.0, e.toDouble()) }
    return lsF(m, 2).replace("−", "-") + "e" + (if (e < 0) "-${-e}" else "+$e")
}

// Row builders, as the design's row() and brow().
internal fun lsRow(w: String, v: String, frac: Double, ink: LsInk, text: LsText = LsText.Normal) =
    LsRow(w, v, 0.0, frac.coerceIn(0.0, 1.0), ink, text)

internal fun lsBrow(w: String, x: Double, scale: Double, v: String = lsF(x), text: LsText = LsText.Normal): LsRow {
    val a = min(abs(x) / scale, 1.0) * 0.5
    return LsRow(w, v, if (x >= 0) 0.5 else 0.5 - a, a, if (x >= 0) LsInk.Blue else LsInk.Pink, text, mid = true)
}

/** Seeded Park–Miller generator, the design's `seed = seed·16807 mod (2³¹ − 1)`. */
internal class LsRng(seed: Long) {
    private var s = seed
    fun next(): Double {
        s = (s * 16807L) % 2147483647L
        return s / 2147483647.0
    }
}

// ── Lab ──

@Composable
internal fun LlmStorySection(topicId: String) {
    val lab = remember(topicId) { llmLab(topicId) }
    val all = remember(topicId) { List(maxOf(lab.tabs.size, 1)) { lab.frames(it) } }
    var tab by rememberSaveable(topicId) { mutableIntStateOf(lab.initialTab) }
    val frames = all[tab.coerceIn(0, all.lastIndex)]
    val playback = rememberPlaybackState(key = topicId, stepCount = frames.size, initialSpeedMs = 1000f)
    val dock = LocalLabDock.current
    val at = playback.index.coerceIn(0, frames.lastIndex)
    val captions = frames.map { storyPlain(it.headline) }

    val controls: @Composable () -> Unit = {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (lab.tabs.isNotEmpty()) LabSegments(lab.tabs, tab, Modifier.padding(bottom = 14.dp)) { tab = it }
            LabTransportBar(playback, captions)
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        LsBody(frames[at], lab.legend)
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

@Composable
private fun LsBody(frame: LsFrame, legend: List<Pair<LsInk, String>>) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            frame.blocks.forEach { block ->
                when (block) {
                    is LsBlock.Label -> Text(block.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    is LsBlock.Chips -> LsChipsView(block.toks)
                    is LsBlock.Bars -> LsBarsView(block.rows)
                    is LsBlock.Hist -> LsHistView(block.heights)
                    is LsBlock.Plot -> LsPlotView(block)
                    is LsBlock.Stats -> LsStatsView(block.rows)
                }
            }
            if (legend.isNotEmpty()) StoryLegendRow(legend.map { Triple(lsColor(it.first), SwatchStyle.Fill, it.second) })
        }
    }
    if (frame.fx.isNotEmpty()) LsFormula(frame.fx, Modifier.padding(top = 12.dp))
    LabStoryNarration(frame.headline, frame.body, Modifier.padding(top = 16.dp))
}

@Composable
private fun LsFormula(lines: List<LsFx>, modifier: Modifier = Modifier) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val onSurface = MaterialTheme.colorScheme.onSurface
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(SimColors.Tint, RoundedCornerShape(12.dp))
            .padding(vertical = 8.dp, horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        lines.forEach { l ->
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = muted)) { append(l.a) }
                    if (l.b.isNotEmpty()) {
                        append(" ")
                        withStyle(SpanStyle(color = l.ink?.let { lsColor(it) } ?: onSurface, fontWeight = FontWeight.SemiBold)) { append(l.b) }
                    }
                },
                fontFamily = IBMPlexMono,
                fontSize = 13.sp,
                lineHeight = 19.5.sp,
            )
        }
    }
}

// ── Rendering ──

internal fun lsColor(ink: LsInk): Color = when (ink) {
    LsInk.Blue -> SimColors.Blue
    LsInk.Pink -> Color(0xFFE5337A)
    LsInk.Green -> SimColors.Green
    LsInk.Red -> Color(0xFFE5484D)
    LsInk.Violet -> Color(0xFF6D5DFC)
    LsInk.Lilac -> Color(0xFF8F84FF)
    LsInk.Yellow -> SimColors.Active
    LsInk.Slate -> Color(0xFF6B7180).copy(alpha = 0.55f)
    LsInk.Grey -> Color(0xFF9AA0AE)
}

private val lsTrack @Composable get() = Color.Black.copy(alpha = 0.16f)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LsChipsView(toks: List<LsTok>) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        toks.forEach { t ->
            val (bg, fg) = when (t.tone) {
                LsTone.Cur -> SimColors.Active to Color(0xFF1B1D24)
                LsTone.Done -> SimColors.Green.copy(alpha = 0.18f) to Color(0xFF5FD49B)
                LsTone.Fut -> SimColors.Tint.copy(alpha = 0.12f) to muted.copy(alpha = 0.7f)
                LsTone.Ans -> Color(0xFF6D5DFC) to Color.White
                LsTone.Err -> Color(0xFFE5484D).copy(alpha = 0.2f) to Color(0xFFFF8A8D)
                LsTone.Plain -> SimColors.Tint to onSurface
            }
            Column(
                modifier = Modifier
                    .heightIn(min = 30.dp)
                    .background(bg, RoundedCornerShape(8.dp))
                    .then(if (t.tone == LsTone.Fut) Modifier.border(1.dp, SimColors.Tint, RoundedCornerShape(8.dp)) else Modifier)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(t.t, fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold, color = fg, maxLines = 1)
                if (t.sub.isNotEmpty()) Text(t.sub, fontFamily = IBMPlexMono, fontSize = 10.sp, lineHeight = 12.sp, color = fg, maxLines = 1)
            }
        }
    }
}

@Composable
private fun LsBarsView(rows: List<LsRow>) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val track = lsTrack
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        rows.forEach { r ->
            Row(modifier = Modifier.fillMaxWidth().height(20.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    r.w, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = when (r.text) {
                        LsText.Strong -> onSurface
                        LsText.Normal -> onSurface.copy(alpha = 0.8f)
                        LsText.Muted -> muted
                    },
                    modifier = Modifier.width(84.dp),
                )
                BoxWithConstraints(modifier = Modifier.padding(horizontal = 8.dp).weight(1f).height(14.dp)) {
                    val w = maxWidth
                    Box(Modifier.align(Alignment.CenterStart).fillMaxWidth().height(8.dp).background(track, RoundedCornerShape(4.dp)))
                    if (r.mid) Box(Modifier.offset(x = w / 2).width(1.dp).fillMaxHeight().background(muted.copy(alpha = 0.5f)))
                    if (r.width > 0) Box(
                        Modifier
                            .align(Alignment.CenterStart)
                            .offset(x = w * r.from.toFloat())
                            .width(w * r.width.toFloat())
                            .height(8.dp)
                            .background(lsColor(r.ink), RoundedCornerShape(4.dp)),
                    )
                }
                Text(
                    r.v, fontFamily = IBMPlexMono, fontSize = 12.sp, color = onSurface.copy(alpha = 0.8f), maxLines = 1,
                    textAlign = TextAlign.End, modifier = Modifier.widthIn(min = 60.dp),
                )
            }
        }
    }
}

@Composable
private fun LsHistView(heights: List<Double>) {
    val track = lsTrack
    Row(modifier = Modifier.fillMaxWidth().height(64.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        heights.forEach { h ->
            Box(
                modifier = Modifier.weight(1f).fillMaxHeight().background(track, RoundedCornerShape(6.dp)).padding(bottom = 2.dp),
                contentAlignment = Alignment.BottomCenter,
            ) {
                Box(Modifier.width(9.dp).height((58 * h).dp).background(lsColor(LsInk.Grey), RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp, bottomStart = 1.dp, bottomEnd = 1.dp)))
            }
        }
    }
}

@Composable
private fun LsPlotView(plot: LsBlock.Plot) {
    val ring = MaterialTheme.colorScheme.onSurface
    BoxWithConstraints(modifier = Modifier.fillMaxWidth().height(plot.height.dp).background(lsTrack, RoundedCornerShape(12.dp))) {
        val w = maxWidth
        val h = maxHeight
        plot.pts.forEach { p ->
            // A training point is 10dp inside a 2dp ring.
            val sz = if (p.big) 14.dp else 6.dp
            Box(
                modifier = Modifier
                    .offset(x = w * p.x.toFloat() - sz / 2, y = h * (1 - p.y).toFloat() - sz / 2)
                    .size(sz)
                    .then(if (p.big) Modifier.border(2.dp, ring, CircleShape).padding(2.dp) else Modifier)
                    .background(lsColor(p.ink), CircleShape),
            )
        }
    }
}

@Composable
private fun LsStatsView(rows: List<LsStat>) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
        rows.forEach { s ->
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(s.k, fontSize = 14.sp, lineHeight = 22.sp, color = muted, modifier = Modifier.width(130.dp).padding(end = 8.dp))
                Text(s.v, fontSize = 14.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold, color = s.ink?.let { lsColor(it) } ?: onSurface, modifier = Modifier.weight(1f))
            }
        }
    }
}
