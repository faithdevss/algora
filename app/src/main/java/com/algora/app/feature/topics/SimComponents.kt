package com.algora.app.feature.topics

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.algora.app.core.ui.theme.SimColors
import kotlinx.coroutines.delay

// Shared button-row layout for the data-structure sim widgets (LinkedList/Stack/Queue) — one row
// of equally-weighted action buttons colored via SimColors.
@Composable
fun SimButtonRow(buttons: List<Triple<String, Color, () -> Unit>>, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth()) {
        buttons.forEachIndexed { index, (label, color, onClick) ->
            if (index > 0) Box(modifier = Modifier.width(8.dp))
            Button(
                onClick = onClick,
                colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = Color.White),
                modifier = Modifier.weight(1f),
            ) { Text(label) }
        }
    }
}

/** Height shared by the sim widgets' buttons and inputs, so a row of them lines up. */
private val SimControlHeight = 40.dp

/**
 * Compact numeric input. Material3's OutlinedTextField reserves 56dp plus room for a floating
 * label, which towers over a 44dp button row; here the label sits inline and dim, ahead of the
 * value, so the whole control is one button tall.
 */
@Composable
fun SimNumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .height(SimControlHeight)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 8.dp),
        )
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * Gradient pair for a value chip. The data-structure widgets (array/linked list/stack/queue) all
 * draw the same kind of chip, so the palette lives here instead of once per file — a value looks
 * the same whichever structure is holding it.
 */
class SimChip(val top: Color, val bottom: Color)

/** Resting value. */
val ChipViolet = SimChip(Color(0xFF8B5CF6), Color(0xFF6D28D9))

/** Being looked at — cursor, peek, comparison. */
val ChipAmber = SimChip(Color(0xFFFACC15), Color(0xFFF59E0B))

/** Being moved. */
val ChipBlue = SimChip(Color(0xFF60A5FA), Color(0xFF2563EB))

/** Written or found. */
val ChipGreen = SimChip(Color(0xFF22C55E), Color(0xFF15803D))

/** A value box: gradient fill, bold white value, centred. */
@Composable
fun SimValueChip(value: String, chip: SimChip, width: Dp, height: Dp) {
    Box(
        modifier = Modifier
            .size(width = width, height = height)
            .background(Brush.verticalGradient(listOf(chip.top, chip.bottom)), RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = value,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * One action in a [SimOpRow]. An empty [label] makes the button icon-only, which is how utility
 * actions (reset) sit beside named ones without claiming a full row of their own.
 */
class SimOp(
    val label: String,
    val icon: ImageVector,
    val color: Color,
    val weight: Float = 1f,
    val enabled: Boolean = true,
    val contentDescription: String = label,
    val onClick: () -> Unit,
)

/**
 * Icon-and-label action row: the newer form of [SimButtonRow]. An icon per action lets a row hold
 * three buttons that a text-only row could not, so a widget's ops fit in fewer, shorter rows.
 */
@Composable
fun SimOpRow(ops: List<SimOp>, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ops.forEach { op ->
            Button(
                onClick = op.onClick,
                enabled = op.enabled,
                colors = ButtonDefaults.buttonColors(
                    containerColor = op.color,
                    contentColor = Color.White,
                    disabledContainerColor = op.color.copy(alpha = 0.35f),
                    disabledContentColor = Color.White.copy(alpha = 0.6f),
                ),
                shape = RoundedCornerShape(12.dp),
                // Material3's default 24dp side padding wraps the label on a three-up row.
                contentPadding = PaddingValues(horizontal = 6.dp),
                modifier = Modifier.weight(op.weight).height(SimControlHeight),
            ) {
                Icon(op.icon, contentDescription = op.contentDescription, modifier = Modifier.size(18.dp))
                if (op.label.isNotEmpty()) {
                    Text(
                        text = op.label,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        modifier = Modifier.padding(start = 5.dp),
                    )
                }
            }
        }
    }
}

// ── Shared playback transport ────────────────────────────────────────────────
// Owns the current step index + play/pause + speed for any precomputed-snapshot sim. The widget
// renders steps[state.index]; this drives auto-advance. Reused by recursion-tree / DP-grid sims.
class PlaybackState(val stepCount: Int, initialSpeedMs: Float) {
    var index by mutableStateOf(0)
    var playing by mutableStateOf(false)
    var speedMs by mutableStateOf(initialSpeedMs)

    private val lastIndex get() = (stepCount - 1).coerceAtLeast(0)

    fun reset() { playing = false; index = 0 }
    fun stepBack() { playing = false; if (index > 0) index-- }
    fun stepForward() { playing = false; if (index < lastIndex) index++ }
    fun togglePlay() {
        if (index >= lastIndex) index = 0
        playing = !playing
    }
}

@Composable
fun rememberPlaybackState(key: Any?, stepCount: Int, initialSpeedMs: Float = 700f): PlaybackState =
    remember(key) { PlaybackState(stepCount, initialSpeedMs) }

@Composable
fun PlaybackTransport(state: PlaybackState, modifier: Modifier = Modifier) {
    // Auto-advance while playing; speed is read fresh each tick so the slider takes effect live.
    LaunchedEffect(state.playing) {
        while (state.playing) {
            delay(state.speedMs.toLong())
            if (state.index < state.stepCount - 1) state.index++ else state.playing = false
        }
    }

    Column(modifier = modifier.fillMaxWidth().padding(top = 12.dp)) {
        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
            Text(
                "Step ${state.index + 1} / ${state.stepCount}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Text(
                "${state.speedMs.toInt()} ms/step",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TransportButton(Icons.Filled.Refresh, "Reset", SimColors.Grey, Modifier.weight(1f)) { state.reset() }
            TransportButton(Icons.Filled.SkipPrevious, "Step back", SimColors.Blue, Modifier.weight(1f)) { state.stepBack() }
            TransportButton(
                if (state.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                if (state.playing) "Pause" else "Play",
                SimColors.Green,
                Modifier.weight(1.4f),
                iconSize = 30.dp,
            ) { state.togglePlay() }
            TransportButton(Icons.Filled.SkipNext, "Step forward", SimColors.Blue, Modifier.weight(1f)) { state.stepForward() }
        }
        Slider(
            value = state.speedMs,
            onValueChange = { state.speedMs = it },
            valueRange = 150f..1400f,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

// Icon-only transport control. Material3's default pill shape + 24dp content padding left the glyph
// stranded in the middle of a wide button, so the shape/height/padding come from the design mock's
// `btn()` (radius 12, tight vertical padding) and the icon is sized explicitly.
@Composable
internal fun TransportButton(
    icon: ImageVector,
    contentDescription: String,
    color: Color,
    modifier: Modifier = Modifier,
    iconSize: Dp = 26.dp,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = Color.White),
        shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(0.dp),
        modifier = modifier.height(40.dp),
    ) {
        Icon(icon, contentDescription = contentDescription, modifier = Modifier.size(iconSize))
    }
}
