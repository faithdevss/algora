package com.algora.app.feature.topics

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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
private fun TransportButton(
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
        modifier = modifier.height(44.dp),
    ) {
        Icon(icon, contentDescription = contentDescription, modifier = Modifier.size(iconSize))
    }
}
