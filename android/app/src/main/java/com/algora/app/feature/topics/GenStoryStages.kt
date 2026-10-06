package com.algora.app.feature.topics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.algora.app.core.ui.theme.IBMPlexMono
import com.algora.app.core.ui.theme.SimColors
import kotlin.math.abs

// ── Generative-model stages ──────────────────────────────────────────────────
// The nine stages the GAN, diffusion, VAE, CycleGAN, DCGAN, Stable Diffusion, DeepFakes, StyleGAN and
// style-transfer storyboards draw inside the deep-learning card (DeepStoryLabs.kt). Frames are built in
// GenStoryFrames.kt; GenStoryStages.swift is the iOS port.

/** Real and generated histograms over x in −4..4 (bin probabilities) with the discriminator D(x) on 0..1. */
internal class DkGan(val real: List<Double>, val fake: List<Double>, val d: List<DkP>) : DkStage

/** One timestep in the strip under the diffusion plot; [ring] draws the clean data in green. */
internal class DkThumb(val t: String, val abar: String, val pts: List<DkP>, val ring: Boolean)

/**
 * The ring of data and the current sample on one plot, with a strip of timesteps under it. [links] joins
 * each data point to where its noise moved it; a [faint] ring is the manifold the reverse process aims for.
 */
internal class DkDiffusion(
    val ring: List<DkP>,
    val pts: List<DkP>,
    val links: Boolean,
    val faint: Boolean,
    val thumbs: List<DkThumb>,
    val current: Int,
    val range: Double = 2.2,
) : DkStage

/** One latent unit: its posterior mean and spread, and the sample z drawn from it (null: none yet). */
internal class DkLatent(val mu: Double, val sigma: Double, val z: Double?)

/** Inputs, one row per latent unit against the N(0,1) prior, outputs, and KL per unit. A null [x] is a prior sample. */
internal class DkVae(
    val x: List<Double>?,
    val latents: List<DkLatent>,
    val xHat: List<Double>,
    val kl: List<Double>,
    val klCaption: String,
) : DkStage

internal class DkCycleStat(val key: String, val value: String, val ink: DkInk? = null)

/** One mapping G : A → B as six links ([perm] of b for each a; null draws only the domains). */
internal class DkCyclePanel(
    val title: String,
    val titleInk: DkInk,
    val perm: List<Int>?,
    val ink: DkInk,
    val dashed: Boolean,
    // The inverse F drawn back from B to A, dashed violet under the forward links.
    val inverse: Boolean = false,
    val stats: List<DkCycleStat> = emptyList(),
)

internal class DkCycle(val panels: List<DkCyclePanel>) : DkStage

/** Writes per output position for one kernel/stride pair; the first and last [border] positions are greyed. */
internal class DkCovRow(
    val label: String,
    val note: String,
    val noteInk: DkInk,
    val counts: List<Int>,
    val border: Int,
    val uniform: Boolean,
    val selected: Boolean,
)

internal class DkCoverage(val rows: List<DkCovRow>, val maxCount: Int) : DkStage

/** A quantity in pixel space against latent space: two log-scale bars and their values. */
internal class DkShrinkRow(
    val name: String,
    val formula: String,
    val a: String,
    val aFrac: Double,
    val b: String,
    val bFrac: Double,
    val hot: Boolean = false,
)

internal class DkShrinkTile(val value: String, val caption: String, val hot: Boolean = false)

internal class DkShrink(
    val left: String,
    val right: String,
    val rows: List<DkShrinkRow>,
    val tiles: List<DkShrinkTile>,
) : DkStage

/**
 * The face-swap autoencoder: x_A and x_B into one encoder (or two, when not [shared]), a code z, and a
 * decoder per identity. A path not lit is drawn dim; [swap] draws A's code into B's decoder.
 */
internal class DkFakeArch(
    val shared: Boolean,
    val trainA: Boolean,
    val trainB: Boolean,
    val swap: Boolean,
) : DkStage

/** Points and a path on the unit square (y up), with a caption under the panel. */
internal class DkWarpPanel(val caption: String, val pts: List<DkP>, val path: List<DkP>? = null, val pathInk: DkInk = DkInk.Green)

internal class DkWarp(val left: DkWarpPanel, val right: DkWarpPanel) : DkStage

/** A labelled heatmap; [values] shows each cell's number (a Gram matrix), [track] rings a column yellow. */
internal class DkHeat(
    val title: String,
    val m: List<List<Double>>,
    val ink: DkInk,
    val values: Boolean,
    val track: Int? = null,
    val hotCell: Pair<Int, Int>? = null,
    val hotRows: Pair<Int, Int>? = null,
)

/** Feature maps over their Gram matrices, in one or two columns. */
internal class DkGram(val columns: List<List<DkHeat>>) : DkStage

// ── Shared drawing ──

private fun DrawScope.genText(
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

private val GenTick = Color(0xFF8FB6FF)
private val GenRule = Color(0xFF252A36)
private val GenBorder = Color(0xFF3A3F4C)
private val GenBox = Color(0xFF2A2E39)
private val GenLilac = Color(0xFFB3ABFF)

private fun mono(size: Float, bold: Boolean = false) =
    TextStyle(fontFamily = IBMPlexMono, fontSize = size.sp, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)

// ── GAN ──

@Composable
internal fun DkGanView(stage: DkGan) {
    val measurer = rememberTextMeasurer()
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(modifier = Modifier.fillMaxWidth().height(190.dp).dkStage()) {
        val left = 28.dp.toPx()
        val right = size.width - 8.dp.toPx()
        val top = 14.dp.toPx()
        val bottom = size.height - 30.dp.toPx()
        fun px(x: Double) = left + ((x + 4) / 8).toFloat() * (right - left)
        fun py(y: Double) = bottom - y.toFloat() * (bottom - top)
        listOf(0.0 to "0", 0.5 to ".5", 1.0 to "1").forEach { (v, s) ->
            drawLine(
                GenRule, Offset(left, py(v)), Offset(right, py(v)), 1.dp.toPx(),
                pathEffect = if (v == 0.5) PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())) else null,
            )
            genText(measurer, s, mono(9f), GenTick, Offset(left - 4.dp.toPx(), py(v)), -1)
        }
        val peak = (stage.real + stage.fake).max().coerceAtLeast(1e-9)
        val bin = (right - left) / stage.real.size
        val w = bin * 0.4f
        stage.real.indices.forEach { i ->
            listOf(stage.real[i] to DkInk.Green, stage.fake[i] to DkInk.Orange).forEachIndexed { k, (p, ink) ->
                val h = (p / peak).toFloat().coerceAtMost(1f) * (bottom - top) * 0.9f
                if (h > 0.2f) drawRoundRect(
                    dkColor(ink),
                    topLeft = Offset(left + bin * i + bin * (0.08f + 0.46f * k), bottom - h),
                    size = Size(w, h),
                    cornerRadius = CornerRadius(1.5.dp.toPx()),
                )
            }
        }
        val curve = Path()
        stage.d.forEachIndexed { i, p -> if (i == 0) curve.moveTo(px(p.x), py(p.y)) else curve.lineTo(px(p.x), py(p.y)) }
        drawPath(curve, dkColor(DkInk.Blue), style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        listOf(-4, -2, 0, 2, 4).forEach { x ->
            genText(measurer, if (x < 0) "−${-x}" else "$x", mono(9f), muted, Offset(px(x.toDouble()), bottom + 10.dp.toPx()))
        }
        genText(measurer, "D(x)", mono(9f), GenTick, Offset(left + 2.dp.toPx(), top + 10.dp.toPx()), 1)
        genText(measurer, "x", mono(9f), muted, Offset(right, bottom + 22.dp.toPx()), -1)
    }
}

// ── Diffusion ──

@Composable
internal fun DkDiffusionView(stage: DkDiffusion) {
    val green = dkColor(DkInk.Green)
    val cyan = dkColor(DkInk.Cyan)
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(modifier = Modifier.fillMaxWidth()) {
        Canvas(modifier = Modifier.fillMaxWidth().height(200.dp).dkStage()) {
            val scale = (size.height / 2f - 8.dp.toPx()) / stage.range.toFloat()
            fun at(p: DkP) = Offset(size.width / 2f + p.x.toFloat() * scale, size.height / 2f - p.y.toFloat() * scale)
            if (stage.links) stage.ring.indices.forEach { i ->
                drawLine(Color(0xFF6B7180).copy(alpha = 0.6f), at(stage.ring[i]), at(stage.pts[i]), 1.dp.toPx())
            }
            stage.ring.forEach { drawCircle(green.copy(alpha = if (stage.faint) 0.35f else 1f), 2.6.dp.toPx(), at(it)) }
            stage.pts.forEach { drawCircle(cyan, 4.dp.toPx(), at(it)) }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            stage.thumbs.forEachIndexed { i, thumb ->
                val on = i == stage.current
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .dkStage()
                            .then(if (on) Modifier.border(1.5.dp, GenLilac, RoundedCornerShape(12.dp)) else Modifier),
                    ) {
                        val scale = (size.minDimension / 2f - 4.dp.toPx()) / stage.range.toFloat()
                        fun at(p: DkP) = Offset(size.width / 2f + p.x.toFloat() * scale, size.height / 2f - p.y.toFloat() * scale)
                        thumb.pts.forEach { drawCircle(if (thumb.ring) green else cyan, 1.7.dp.toPx(), at(it)) }
                    }
                    Text(
                        thumb.t, fontFamily = IBMPlexMono, fontSize = 11.sp, maxLines = 1,
                        fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                        color = if (on) GenLilac else muted, modifier = Modifier.padding(top = 5.dp),
                    )
                    Text(thumb.abar, fontFamily = IBMPlexMono, fontSize = 9.5.sp, maxLines = 1, color = muted.copy(alpha = 0.8f))
                }
            }
        }
    }
}

// ── VAE ──

private val GenSub = listOf("₁", "₂", "₃", "₄", "₅", "₆")

@Composable
internal fun DkVaeView(stage: DkVae) {
    val measurer = rememberTextMeasurer()
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val onSurface = MaterialTheme.colorScheme.onSurface
    val blue = dkColor(DkInk.Blue)
    val yellow = SimColors.Active
    val violet = Color(0xFF8B5CF6)
    Column(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().dkStage().padding(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
                Text("x", fontFamily = IBMPlexMono, fontSize = 10.sp, color = muted, textAlign = TextAlign.Center, modifier = Modifier.width(44.dp))
                Text("q(z | x) per latent dimension", fontFamily = IBMPlexMono, fontSize = 10.sp, color = muted, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                Text("x̂", fontFamily = IBMPlexMono, fontSize = 10.sp, color = muted, textAlign = TextAlign.Center, modifier = Modifier.width(44.dp))
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                VaeTiles(stage.x ?: List(stage.xHat.size) { null }, blue)
                Canvas(modifier = Modifier.weight(1f).height(196.dp).padding(horizontal = 8.dp)) {
                    val axisY = size.height - 6.dp.toPx()
                    val rowH = (axisY - 14.dp.toPx()) / stage.latents.size
                    fun px(v: Double) = ((v.coerceIn(-3.0, 3.0) + 3) / 6).toFloat() * size.width
                    stage.latents.forEachIndexed { j, l ->
                        val cy = rowH * j + rowH * 0.62f
                        genText(measurer, "z${GenSub[j]}", mono(9.5f), muted, Offset(0f, cy - 13.dp.toPx()), 1)
                        genText(
                            measurer, "μ ${dkNum(l.mu, 2)} σ ${dkNum(l.sigma, 2)}", mono(9.5f), onSurface.copy(alpha = 0.8f),
                            Offset(size.width, cy - 13.dp.toPx()), -1,
                        )
                        drawLine(muted.copy(alpha = 0.25f), Offset(0f, cy), Offset(size.width, cy), 1.dp.toPx())
                        drawRoundRect(
                            Color(0xFF6B7180).copy(alpha = 0.28f), Offset(px(-1.0), cy - 7.dp.toPx()), Size(px(1.0) - px(-1.0), 14.dp.toPx()),
                            CornerRadius(3.dp.toPx()),
                        )
                        drawRoundRect(
                            blue.copy(alpha = 0.75f), Offset(px(l.mu - l.sigma), cy - 5.dp.toPx()),
                            Size((px(l.mu + l.sigma) - px(l.mu - l.sigma)).coerceAtLeast(2f), 10.dp.toPx()), CornerRadius(3.dp.toPx()),
                        )
                        drawLine(Color.White, Offset(px(l.mu), cy - 7.dp.toPx()), Offset(px(l.mu), cy + 7.dp.toPx()), 2.dp.toPx())
                        l.z?.let { drawCircle(yellow, 4.5.dp.toPx(), Offset(px(it), cy)) }
                    }
                    listOf(-3.0 to "−3", 0.0 to "0", 3.0 to "3").forEachIndexed { k, (v, s) ->
                        genText(measurer, s, mono(9f), muted, Offset(px(v), axisY), if (k == 0) 1 else if (k == 2) -1 else 0)
                    }
                }
                VaeTiles(stage.xHat, blue)
            }
        }
        StoryLegendRow(
            listOf(
                Triple(blue, SwatchStyle.Fill, "Posterior μ ± σ"),
                Triple(Color(0xFF6B7180), SwatchStyle.Fill, "Prior N(0,1) ± 1"),
                Triple(yellow, SwatchStyle.Dot, "Sample z = μ + σε"),
            ),
            Modifier.padding(top = 12.dp),
        )
        Text(stage.klCaption, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = muted, modifier = Modifier.padding(top = 14.dp, bottom = 6.dp))
        val top = stage.kl.max().coerceAtLeast(0.5)
        stage.kl.forEachIndexed { j, v ->
            Row(modifier = Modifier.fillMaxWidth().height(21.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("z${GenSub[j]}", fontFamily = IBMPlexMono, fontSize = 11.sp, color = muted, modifier = Modifier.width(30.dp))
                Box(modifier = Modifier.weight(1f).height(6.dp).background(SimColors.Tint, RoundedCornerShape(3.dp))) {
                    Box(Modifier.fillMaxWidth((v / top).toFloat().coerceIn(0.01f, 1f)).height(6.dp).background(violet, RoundedCornerShape(3.dp)))
                }
                Text(dkNum(v, 2), fontFamily = IBMPlexMono, fontSize = 12.sp, color = onSurface.copy(alpha = 0.85f), textAlign = TextAlign.End, modifier = Modifier.width(52.dp))
            }
        }
    }
}

@Composable
private fun VaeTiles(values: List<Double?>, blue: Color) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(modifier = Modifier.width(44.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        values.forEach { v ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(27.dp)
                    .then(
                        if (v == null) Modifier.dashedOutline(muted.copy(alpha = 0.45f), 6.dp)
                        else Modifier.background(blue.copy(alpha = 0.22f + 0.7f * (abs(v) / 2).toFloat().coerceIn(0f, 1f)), RoundedCornerShape(6.dp)),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    v?.let { dkNum(it, 2) } ?: "—", fontFamily = IBMPlexMono, fontSize = 11.5.sp, fontWeight = FontWeight.Bold,
                    color = if (v == null) muted else Color.White, maxLines = 1,
                )
            }
        }
    }
}

// ── CycleGAN ──

@Composable
internal fun DkCycleView(stage: DkCycle) {
    val measurer = rememberTextMeasurer()
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val labelInk = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
    val onSurface = MaterialTheme.colorScheme.onSurface
    Column(modifier = Modifier.fillMaxWidth()) {
        Canvas(modifier = Modifier.fillMaxWidth().height(172.dp).dkStage()) {
            val n = 6
            val w = size.width / stage.panels.size
            fun rowY(i: Int) = 18.dp.toPx() + (size.height - 36.dp.toPx()) * i / (n - 1)
            stage.panels.forEachIndexed { k, panel ->
                val x0 = w * k
                val ax = x0 + 32.dp.toPx()
                val bx = x0 + w - 30.dp.toPx()
                if (k > 0) drawLine(muted.copy(alpha = 0.3f), Offset(x0, 12.dp.toPx()), Offset(x0, size.height - 12.dp.toPx()), 1.dp.toPx())
                panel.perm?.let { perm ->
                    if (panel.inverse) perm.forEachIndexed { a, b ->
                        drawLine(
                            dkColor(DkInk.Violet), Offset(bx, rowY(b) + 4.dp.toPx()), Offset(ax, rowY(a) + 4.dp.toPx()), 1.5.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 3.dp.toPx())),
                        )
                    }
                    perm.forEachIndexed { a, b ->
                        drawLine(
                            dkColor(panel.ink), Offset(ax, rowY(a)), Offset(bx, rowY(b)), 2.dp.toPx(),
                            pathEffect = if (panel.dashed) PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())) else null,
                        )
                    }
                }
                repeat(n) { i ->
                    drawCircle(dkColor(DkInk.Cyan), 5.dp.toPx(), Offset(ax, rowY(i)))
                    drawCircle(dkColor(DkInk.Orange), 5.dp.toPx(), Offset(bx, rowY(i)))
                    genText(measurer, "a${i + 1}", mono(10f), labelInk, Offset(ax - 10.dp.toPx(), rowY(i)), -1)
                    genText(measurer, "b${i + 1}", mono(10f), labelInk, Offset(bx + 10.dp.toPx(), rowY(i)), 1)
                }
            }
        }
        if (stage.panels.any { it.stats.isNotEmpty() }) {
            Row(modifier = Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                stage.panels.forEach { panel ->
                    Column(modifier = Modifier.weight(1f).dkStage().padding(horizontal = 12.dp, vertical = 10.dp)) {
                        Text(panel.title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = dkColor(panel.titleInk), maxLines = 1)
                        panel.stats.forEach { s ->
                            Row(modifier = Modifier.fillMaxWidth().padding(top = 3.dp)) {
                                Text(s.key, fontSize = 13.sp, color = muted, maxLines = 1, modifier = Modifier.weight(1f))
                                Text(s.value, fontFamily = IBMPlexMono, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, color = s.ink?.let { dkColor(it) } ?: onSurface)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── DCGAN coverage ──

@Composable
internal fun DkCoverageView(stage: DkCoverage) {
    val measurer = rememberTextMeasurer()
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val onSurface = MaterialTheme.colorScheme.onSurface
    Column(modifier = Modifier.fillMaxWidth().dkStage().padding(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        stage.rows.forEach { row ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (row.selected) Modifier.border(1.5.dp, GenLilac, RoundedCornerShape(10.dp)) else Modifier)
                    .padding(horizontal = 6.dp, vertical = 5.dp),
            ) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        row.label, fontFamily = IBMPlexMono, fontSize = 10.5.sp, maxLines = 1, modifier = Modifier.weight(1f),
                        fontWeight = if (row.selected) FontWeight.Bold else FontWeight.Normal,
                        color = if (row.selected) onSurface else muted,
                    )
                    Text(row.note, fontFamily = IBMPlexMono, fontSize = 10.sp, maxLines = 1, color = dkColor(row.noteInk))
                }
                Canvas(modifier = Modifier.fillMaxWidth().height(44.dp).padding(top = 4.dp)) {
                    val n = row.counts.size
                    val slot = size.width / n
                    val barTop = 0f
                    val barBottom = size.height - 12.dp.toPx()
                    row.counts.forEachIndexed { i, c ->
                        val border = i < row.border || i >= n - row.border
                        val color = when {
                            border -> GenBorder
                            row.uniform -> dkColor(DkInk.Green)
                            else -> dkColor(DkInk.Orange)
                        }.copy(alpha = if (row.selected || border) 1f else 0.6f)
                        val h = (barBottom - barTop) * c / stage.maxCount
                        drawRoundRect(color, Offset(slot * i + slot * 0.12f, barBottom - h), Size(slot * 0.76f, h), CornerRadius(1.5.dp.toPx()))
                        genText(measurer, "$c", mono(8.5f), muted, Offset(slot * i + slot / 2f, size.height - 4.dp.toPx()))
                    }
                }
            }
        }
    }
}

// ── Stable Diffusion shrink ──

@Composable
internal fun DkShrinkView(stage: DkShrink) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val onSurface = MaterialTheme.colorScheme.onSurface
    val blue = dkColor(DkInk.Blue)
    val pink = Color(0xFFEC4899)
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(stage.left, fontFamily = IBMPlexMono, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = GenTick, modifier = Modifier.weight(1f))
            Text(stage.right, fontFamily = IBMPlexMono, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFF47AA8), modifier = Modifier.weight(1f).padding(start = 10.dp))
        }
        stage.rows.forEach { row ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(row.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, color = if (row.hot) SimColors.Active else onSurface, modifier = Modifier.weight(1f))
                    Text(row.formula, fontFamily = IBMPlexMono, fontSize = 11.sp, color = muted, maxLines = 1)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf(Triple(row.aFrac, row.a, blue), Triple(row.bFrac, row.b, pink)).forEach { (frac, value, ink) ->
                        Column(modifier = Modifier.weight(1f)) {
                            Box(modifier = Modifier.fillMaxWidth().height(6.dp).background(SimColors.Tint, RoundedCornerShape(3.dp))) {
                                Box(Modifier.fillMaxWidth(frac.toFloat().coerceIn(0.012f, 1f)).height(6.dp).background(ink, RoundedCornerShape(3.dp)))
                            }
                            Text(value, fontFamily = IBMPlexMono, fontSize = 11.sp, color = onSurface.copy(alpha = 0.8f), textAlign = TextAlign.End, maxLines = 1, modifier = Modifier.fillMaxWidth().padding(top = 3.dp))
                        }
                    }
                }
            }
        }
        if (stage.tiles.isNotEmpty()) Row(modifier = Modifier.fillMaxWidth().padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            stage.tiles.forEach { tile ->
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .height(84.dp)
                        .background(if (tile.hot) SimColors.Active.copy(alpha = 0.14f) else Color.Black.copy(alpha = 0.16f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp, vertical = 10.dp),
                ) {
                    Text(tile.value, fontSize = 21.sp, fontWeight = FontWeight.Bold, maxLines = 1, color = if (tile.hot) SimColors.Active else onSurface)
                    Text(tile.caption, fontSize = 12.sp, lineHeight = 15.sp, color = muted, maxLines = 3, modifier = Modifier.padding(top = 2.dp))
                }
            }
        }
    }
}

// ── DeepFakes architecture ──

@Composable
internal fun DkFakeArchView(stage: DkFakeArch) {
    val measurer = rememberTextMeasurer()
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val onSurface = MaterialTheme.colorScheme.onSurface
    val blue = dkColor(DkInk.Blue)
    val violet = Color(0xFF8B5CF6)
    val yellow = SimColors.Active
    Canvas(modifier = Modifier.fillMaxWidth().height(230.dp).dkStage()) {
        // Laid out on the design's 329 × 230 board and scaled to the width.
        val s = size.width / 329f
        val v = size.height / 230f
        fun p(x: Float, y: Float) = Offset(x * s, y * v)
        fun box(x: Float, y: Float, w: Float, h: Float, fill: Color, title: String, sub: String?) {
            drawRoundRect(fill, p(x, y), Size(w * s, h * v), CornerRadius(8.dp.toPx()))
            genText(measurer, title, mono(11f, bold = true), onSurface, p(x + w / 2, y + h / 2 - if (sub != null) 5 else 0))
            sub?.let { genText(measurer, it, mono(8f), onSurface.copy(alpha = 0.7f), p(x + w / 2, y + h / 2 + 8)) }
        }
        fun wire(points: List<Offset>, color: Color, dashed: Boolean = false) {
            val path = Path()
            points.forEachIndexed { i, o -> if (i == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y) }
            drawPath(
                path, color,
                style = Stroke(
                    2.dp.toPx(), join = StrokeJoin.Round,
                    pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())) else null,
                ),
            )
        }
        fun trapezoid(x: Float, y0: Float, y1: Float, fill: Color, title: String, sub: String) {
            // Wide on the input side, narrowing toward the code.
            val wide = Path().apply {
                moveTo(x * s, y0 * v)
                lineTo((x + 48) * s, (y0 + 18) * v)
                lineTo((x + 48) * s, (y1 - 18) * v)
                lineTo(x * s, y1 * v)
                close()
            }
            drawPath(wide, fill)
            genText(measurer, title, mono(11f, bold = true), onSurface, p(x + 24, (y0 + y1) / 2 - 5))
            genText(measurer, sub, mono(8f), onSurface.copy(alpha = 0.75f), p(x + 24, (y0 + y1) / 2 + 8))
        }
        val aInk = blue.copy(alpha = if (stage.trainA) 1f else 0.25f)
        val bInk = violet.copy(alpha = if (stage.trainB) 1f else 0.25f)

        if (stage.shared) {
            wire(listOf(p(64f, 44f), p(86f, 44f), p(86f, 100f), p(110f, 100f)), aInk)
            wire(listOf(p(64f, 183f), p(86f, 183f), p(86f, 130f), p(110f, 130f)), bInk)
            wire(listOf(p(158f, 115f), p(178f, 115f)), if (stage.trainA) aInk else bInk)
            wire(listOf(p(206f, 109f), p(222f, 109f), p(222f, 45f), p(236f, 45f)), aInk)
            wire(listOf(p(206f, 121f), p(214f, 121f), p(214f, 179f), p(236f, 179f)), bInk)
            trapezoid(110f, 82f, 148f, blue.copy(alpha = 0.6f), "E", "shared")
            genText(measurer, "one trunk", mono(8f), muted, p(134f, 162f))
            box(178f, 101f, 28f, 28f, GenBox, "z", null)
            if (stage.swap) {
                wire(listOf(p(206f, 124f), p(230f, 124f), p(230f, 172f), p(236f, 172f)), yellow, dashed = true)
                genText(measurer, "swap", mono(8.5f, bold = true), yellow, p(250f, 148f))
            }
        } else {
            wire(listOf(p(64f, 44f), p(110f, 44f)), aInk)
            wire(listOf(p(64f, 183f), p(110f, 183f)), bInk)
            trapezoid(110f, 14f, 76f, blue.copy(alpha = 0.6f), "E_A", "own")
            trapezoid(110f, 152f, 214f, violet.copy(alpha = 0.6f), "E_B", "own")
            wire(listOf(p(158f, 45f), p(178f, 45f)), aInk)
            wire(listOf(p(158f, 183f), p(178f, 183f)), bInk)
            box(178f, 31f, 28f, 28f, GenBox, "z", null)
            box(178f, 169f, 28f, 28f, GenBox, "z", null)
            wire(listOf(p(206f, 45f), p(236f, 45f)), aInk)
            wire(listOf(p(206f, 183f), p(236f, 179f)), bInk)
            if (stage.swap) {
                wire(listOf(p(192f, 59f), p(192f, 110f), p(226f, 110f), p(226f, 172f), p(236f, 172f)), yellow, dashed = true)
                genText(measurer, "swap", mono(8.5f, bold = true), yellow, p(214f, 100f))
            }
        }
        box(8f, 26f, 56f, 36f, blue.copy(alpha = 0.3f), "x_A", "face A")
        box(8f, 165f, 56f, 36f, violet.copy(alpha = 0.3f), "x_B", "face B")
        box(236f, 28f, 50f, 34f, GenBox, "D_A", null)
        box(236f, 162f, 50f, 34f, GenBox, "D_B", null)
        wire(listOf(p(286f, 45f), p(296f, 45f)), aInk)
        wire(listOf(p(286f, 179f), p(296f, 179f)), bInk)
        box(296f, 31f, 26f, 28f, GenBox, "x̂", null)
        box(296f, 165f, 26f, 28f, GenBox, "x̂", null)
        genText(measurer, "trained on A only", mono(8f), muted, p(279f, 75f))
        genText(measurer, "trained on B only", mono(8f), muted, p(279f, 209f))
    }
}

// ── StyleGAN warp ──

@Composable
internal fun DkWarpView(stage: DkWarp) {
    val measurer = rememberTextMeasurer()
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val cyan = dkColor(DkInk.Cyan)
    Canvas(modifier = Modifier.fillMaxWidth().height(180.dp).dkStage()) {
        val gap = 8.dp.toPx()
        val pw = (size.width - gap * 3) / 2f
        val ph = size.height - 30.dp.toPx()
        listOf(stage.left, stage.right).forEachIndexed { k, panel ->
            val x0 = gap + k * (pw + gap)
            val y0 = gap
            drawRoundRect(GenBox.copy(alpha = 0.6f), Offset(x0, y0), Size(pw, ph), CornerRadius(8.dp.toPx()))
            // The unit square sits inside the panel with room for points the warp pushes past its edge.
            fun at(p: DkP) = Offset(x0 + pw * (0.16f + 0.68f * p.x.toFloat()), y0 + ph * (0.84f - 0.68f * p.y.toFloat()))
            panel.pts.forEach { drawCircle(cyan, 2.1.dp.toPx(), at(it)) }
            panel.path?.let { pts ->
                val path = Path()
                pts.forEachIndexed { i, q -> val o = at(q); if (i == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y) }
                drawPath(path, dkColor(panel.pathInk), style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            genText(measurer, panel.caption, mono(9f), muted, Offset(x0 + pw / 2, y0 + ph + 11.dp.toPx()))
        }
    }
}

// ── Style transfer Gram ──

@Composable
internal fun DkGramView(stage: DkGram) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        stage.columns.forEach { column ->
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                column.forEach { DkHeatView(it) }
            }
        }
    }
}

@Composable
private fun DkHeatView(heat: DkHeat) {
    val measurer = rememberTextMeasurer()
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val base = if (heat.ink == DkInk.Violet) Color(0xFF8B5CF6) else dkColor(heat.ink)
    val top = heat.m.flatten().max().coerceAtLeast(1e-9)
    val rows = heat.m.size
    val cols = heat.m[0].size
    Column {
        Text(heat.title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = muted, maxLines = 1, modifier = Modifier.padding(bottom = 6.dp))
        Canvas(modifier = Modifier.fillMaxWidth().aspectRatio(cols.toFloat() / rows * if (heat.values) 1.25f else 1f)) {
            val gap = 2.dp.toPx()
            val cw = (size.width - gap * (cols - 1)) / cols
            val ch = (size.height - gap * (rows - 1)) / rows
            for (r in 0 until rows) for (c in 0 until cols) {
                val v = heat.m[r][c]
                val o = Offset(c * (cw + gap), r * (ch + gap))
                val inRows = heat.hotRows?.let { r == it.first || r == it.second } ?: false
                drawRoundRect(base.copy(alpha = (0.12f + 0.8f * (v / top).toFloat()).coerceIn(0.1f, 0.95f)), o, Size(cw, ch), CornerRadius(2.5.dp.toPx()))
                if (heat.values) {
                    val s = dkNum(v, 2).removePrefix("0")
                    genText(measurer, s, mono(8.5f, bold = true), Color(0xFFF2F3F7), Offset(o.x + cw / 2, o.y + ch / 2))
                }
                if (heat.track == c || heat.hotCell == (r to c) || inRows) {
                    drawRoundRect(
                        SimColors.Active, o + Offset(0.75.dp.toPx(), 0.75.dp.toPx()), Size(cw - 1.5.dp.toPx(), ch - 1.5.dp.toPx()),
                        CornerRadius(2.5.dp.toPx()), style = Stroke(1.5.dp.toPx()),
                    )
                }
            }
        }
    }
}
