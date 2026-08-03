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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
