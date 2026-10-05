package com.algora.app.feature.topics

import androidx.compose.foundation.Canvas
import androidx.compose.material3.Slider
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import com.algora.app.core.ui.theme.LocalDarkTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.sp
import com.algora.app.core.ui.theme.IBMPlexMono
import com.algora.app.core.ui.theme.SimColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop

// Shared button-row layout for the data-structure sim widgets (LinkedList/Stack/Queue) — one row
// of equally-weighted action buttons colored via SimColors.
@Composable
fun SimButtonRow(buttons: List<Triple<String, Color, () -> Unit>>, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth()) {
        buttons.forEachIndexed { index, (label, color, onClick) ->
            if (index > 0) Box(modifier = Modifier.width(8.dp))
            Button(
                onClick = onClick,
                colors = ButtonDefaults.buttonColors(containerColor = color.copy(alpha = 0.16f), contentColor = color),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f).height(SimControlHeight),
            ) { Text(label, fontWeight = FontWeight.SemiBold) }
        }
    }
}

/** Height shared by the sim widgets' buttons and inputs, so a row of them lines up. */
private val SimControlHeight = 44.dp

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
    val accent = MaterialTheme.colorScheme.primary
    Row(
        // Drawn as an editable token: tinted fill with an accent underline.
        modifier = modifier
            .height(SimControlHeight)
            .clip(RoundedCornerShape(10.dp))
            .background(SimColors.Tint)
            .drawBehind {
                drawRect(accent, topLeft = Offset(6.dp.toPx(), size.height - 2.dp.toPx()), size = Size(size.width - 12.dp.toPx(), 2.dp.toPx()))
            }
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
            textStyle = TextStyle(fontFamily = IBMPlexMono, fontWeight = FontWeight.Medium, fontSize = 17.sp, color = MaterialTheme.colorScheme.onSurface),
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
val ChipViolet = SimChip(SimColors.Violet, Color(0xFF6D28D9))

/** Being looked at — cursor, peek, comparison. */
val ChipAmber = SimChip(SimColors.Active, SimColors.Amber)

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
            .background(chip.bottom, RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = value,
            color = Color.White,
            fontFamily = IBMPlexMono,
            fontWeight = FontWeight.Medium,
            fontSize = 17.sp,
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
                // Tinted rather than filled, so no one button shouts and a disabled one still reads as disabled.
                colors = ButtonDefaults.buttonColors(
                    containerColor = op.color.copy(alpha = 0.16f),
                    contentColor = op.color,
                    disabledContainerColor = op.color.copy(alpha = 0.07f),
                    disabledContentColor = op.color.copy(alpha = 0.4f),
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
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        modifier = Modifier.padding(start = 5.dp),
                    )
                }
            }
        }
    }
}

// ── Dock ─────────────────────────────────────────────────────────────────────
// Restyled to docs/ios-design/Simulations iOS.html: stage, readout chips, narration, then transport
// controls in thumb reach. What a lab hands up to the screen hosting it: the transport (so the
// screen can pin it at the bottom) and the intro (so the screen can move it behind the (i) button).
// Only the sim-only screen installs one; on the topic page a lab renders both inline.
class LabDock {
    var playback by mutableStateOf<PlaybackState?>(null)
    var captions by mutableStateOf<List<String>?>(null)
    var intro by mutableStateOf<String?>(null)

    /** A sandbox lab's own controls (pick an op, run it), pinned where the transport would be. */
    var controls by mutableStateOf<(@Composable () -> Unit)?>(null)

    /** A lab-level action for the nav bar (a sandbox's reset); it takes the bookmark's place. */
    var navAction by mutableStateOf<LabNavAction?>(null)

    /** The transport's step label ("Pull 40 of 200") and track marks, when the lab sets them. */
    var stepLabel by mutableStateOf<((Int) -> String)?>(null)
    var marks by mutableStateOf<TrackMarks?>(null)

    /** The storyboard transport's labelled action per step ("Predict"); see [LabTransportBar]. */
    var stepAction by mutableStateOf<((Int) -> String)?>(null)
}

/** Steps flagged on the scrub track with a yellow tick, and what a tick means ("explore"). */
// color is the tick and key colour: the active yellow by default, red for N-Queens' backtracks.
class TrackMarks(val steps: Set<Int>, val label: String, val color: Color = SimColors.Active)

/** [icon] null draws [label] as a text button ("Edit"). */
class LabNavAction(val icon: ImageVector?, val label: String, val onClick: () -> Unit)

val LocalLabDock = staticCompositionLocalOf<LabDock?> { null }

/** A lab's one-paragraph intro: behind (i) when docked, a muted lead-in otherwise. */
@Composable
fun LabIntro(text: String, modifier: Modifier = Modifier) {
    val dock = LocalLabDock.current
    if (dock != null) {
        SideEffect { dock.intro = text }
    } else {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier,
        )
    }
}

// ── Shared playback transport ────────────────────────────────────────────────
// Owns the current step index + play/pause + speed for any precomputed-snapshot sim. The widget
// renders steps[state.index]; this drives auto-advance.
// Every lab's per-step delay is stretched by this at 1×, so a step's narration can be read before the
// next one lands. The rate buttons still scale from here.
private const val PLAYBACK_PACE = 2f

class PlaybackState(val stepCount: Int, initialSpeedMs: Float) {
    var index by mutableStateOf(0)
    var playing by mutableStateOf(false)

    /** Base delay per step; [rate] scales it. */
    var speedMs by mutableStateOf(initialSpeedMs)
    var rate by mutableStateOf(1f)

    val lastIndex get() = (stepCount - 1).coerceAtLeast(0)
    val atEnd get() = index >= lastIndex

    fun reset() { playing = false; index = 0 }
    fun stepBack() { playing = false; if (index > 0) index-- }
    fun stepForward() { playing = false; if (index < lastIndex) index++ }
    fun togglePlay() {
        if (atEnd) index = 0
        playing = !playing
    }
    fun jump(to: Int) { playing = false; index = to.coerceIn(0, lastIndex) }
    fun cycleRate() {
        val i = RATES.indexOf(rate).coerceAtLeast(0)
        rate = RATES[(i + 1) % RATES.size]
    }

    companion object {
        val RATES = listOf(1f, 1.5f, 2f, 0.5f)
    }
}

@Composable
fun rememberPlaybackState(key: Any?, stepCount: Int, initialSpeedMs: Float = 700f): PlaybackState =
    remember(key) { PlaybackState(stepCount, initialSpeedMs) }

/**
 * A lab's transport. Hands itself to the dock when the screen has one, otherwise draws inline.
 * [captions] (one per step) enables the scrub preview and the All steps sheet.
 */
@Composable
fun PlaybackTransport(
    state: PlaybackState,
    modifier: Modifier = Modifier,
    captions: List<String>? = null,
    stepLabel: ((Int) -> String)? = null,
    marks: TrackMarks? = null,
    action: ((Int) -> String)? = null,
) {
    val dock = LocalLabDock.current
    if (dock != null) {
        DisposableEffect(dock, state) {
            dock.playback = state
            onDispose {
                if (dock.playback === state) {
                    dock.playback = null
                    dock.captions = null
                    dock.stepLabel = null
                    dock.marks = null
                    dock.stepAction = null
                }
            }
        }
        SideEffect {
            dock.captions = captions
            dock.stepLabel = stepLabel
            dock.marks = marks
            dock.stepAction = action
        }
    } else {
        Column(modifier = modifier.fillMaxWidth().padding(top = 16.dp)) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            LabTransportBar(state, captions, Modifier.padding(top = 14.dp), stepLabel = stepLabel, marks = marks, action = action)
        }
    }
}

/** The docked transport: pinned under the scrolling body, in thumb reach. */
@Composable
fun LabDockBar(dock: LabDock, modifier: Modifier = Modifier) {
    dock.controls?.let { controls ->
        Column(modifier = modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 0.5.dp)
            Box(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 12.dp)) { controls() }
        }
        return
    }
    val playback = dock.playback ?: return
    Column(modifier = modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 0.5.dp)
        LabTransportBar(
            playback,
            dock.captions,
            Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 12.dp),
            stepLabel = dock.stepLabel,
            marks = dock.marks,
            action = dock.stepAction,
        )
    }
}

/**
 * Controller 2c: a thick segmented step track with a 28dp thumb (drag previews each step), then
 * speed, back, a 56dp play, forward and reset.
 */
@Composable
fun LabTransportBar(
    state: PlaybackState,
    captions: List<String>?,
    modifier: Modifier = Modifier,
    stepLabel: ((Int) -> String)? = null,
    /** A text action at the right of the step label, in place of All steps ("New Data"). */
    trailing: Pair<String, () -> Unit>? = null,
    marks: TrackMarks? = null,
    /**
     * The storyboard transport: the step's labelled action ("Predict", "Score “great”") beside a square
     * step-back button, in place of speed, play and reset. Pressing it advances one step; on the last
     * step it starts over.
     */
    action: ((Int) -> String)? = null,
    /** Drawn under the step label in place of the play or action row (a stepper that re-runs the story). */
    footer: (@Composable () -> Unit)? = null,
) {
    // Auto-advance while playing; the rate is read fresh each tick so a change takes effect live.
    LaunchedEffect(state, state.playing) {
        while (state.playing) {
            delay((state.speedMs * PLAYBACK_PACE / state.rate).toLong())
            if (state.index < state.stepCount - 1) state.index++ else state.playing = false
        }
    }
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(state) {
        snapshotFlow { state.index }.drop(1).collect { haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick) }
    }
    var showSteps by remember { mutableStateOf(false) }
    val accent = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            StepTrack(state, captions, marks?.steps.orEmpty(), marks?.color ?: SimColors.Active)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stepLabel?.invoke(state.index) ?: "Step ${state.index + 1} of ${state.stepCount}",
                    fontSize = 13.sp,
                    color = muted,
                )
                if (marks != null) {
                    Box(modifier = Modifier.padding(start = 10.dp).width(3.dp).height(11.dp).background(marks.color, RoundedCornerShape(1.dp)))
                    Text(marks.label, fontSize = 13.sp, color = muted, modifier = Modifier.padding(start = 5.dp))
                }
                Box(modifier = Modifier.weight(1f))
                if (trailing != null) {
                    Text(
                        trailing.first,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = accent,
                        modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = trailing.second).padding(4.dp),
                    )
                } else if (captions != null) {
                    Text(
                        "All steps",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = accent,
                        modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { showSteps = true }.padding(4.dp),
                    )
                }
            }
        }
        if (footer != null) {
            footer()
        } else if (action != null) {
            LabBackActionRow(
                action = action(state.index),
                backEnabled = state.index > 0,
                onBack = { state.stepBack() },
                onAction = { if (state.atEnd) state.reset() else state.stepForward() },
            )
        } else Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(SimColors.Tint)
                    .clickable(onClickLabel = "Change speed") { state.cycleRate() }
                    .height(32.dp)
                    .widthIn(min = 44.dp)
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(rateLabel(state.rate), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
            GlyphButton(Icons.Filled.SkipPrevious, "Step back", enabled = state.index > 0) { state.stepBack() }
            Box(
                modifier = Modifier.size(56.dp).clip(CircleShape).background(accent).clickable { state.togglePlay() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    when {
                        state.playing -> Icons.Filled.Pause
                        state.atEnd -> Icons.Filled.Replay
                        else -> Icons.Filled.PlayArrow
                    },
                    contentDescription = when {
                        state.playing -> "Pause"
                        state.atEnd -> "Replay"
                        else -> "Play"
                    },
                    tint = Color.White,
                    modifier = Modifier.size(30.dp),
                )
            }
            GlyphButton(Icons.Filled.SkipNext, "Step forward", enabled = !state.atEnd) { state.stepForward() }
            IconButton(onClick = { state.reset() }) {
                Icon(Icons.Filled.Refresh, contentDescription = "Reset", tint = muted)
            }
        }
    }

    if (showSteps && captions != null) StepsSheet(state, captions) { showSteps = false }
}

private fun rateLabel(r: Float): String = (if (r % 1f == 0f) r.toInt().toString() else r.toString()) + "×"

@Composable
private fun GlyphButton(icon: ImageVector, description: String, enabled: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled) {
        Icon(
            icon,
            contentDescription = description,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.35f),
            modifier = Modifier.size(32.dp),
        )
    }
}

/** Segments (one per step) when they fit, a continuous bar when they don't. Drag or tap to scrub. */
@Composable
private fun StepTrack(state: PlaybackState, captions: List<String>?, marks: Set<Int> = emptySet(), markColor: Color = SimColors.Active) {
    val accent = MaterialTheme.colorScheme.primary
    var dragging by remember { mutableStateOf(false) }
    val n = state.stepCount.coerceAtLeast(1)
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp)
            .pointerInput(state) {
                detectTapGestures { state.jump((it.x / size.width * n).toInt()) }
            }
            .pointerInput(state) {
                detectHorizontalDragGestures(
                    onDragStart = { dragging = true; state.jump((it.x / size.width * n).toInt()) },
                    onDragEnd = { dragging = false },
                    onDragCancel = { dragging = false },
                ) { change, _ ->
                    val i = (change.position.x / size.width * n).toInt().coerceIn(0, n - 1)
                    if (i != state.index) state.jump(i)
                }
            }
            .semantics {
                contentDescription = "Step"
                stateDescription = "${state.index + 1} of ${state.stepCount}"
            },
    ) {
        val w = maxWidth
        val thumbX = if (n == 1) w / 2 else w * ((state.index + 0.5f) / n)
        val segmented = w >= 6.dp * n
        if (segmented) {
            Row(
                modifier = Modifier.fillMaxWidth().height(8.dp).align(Alignment.CenterStart),
                horizontalArrangement = Arrangement.spacedBy(if (n > 24) 2.dp else 4.dp),
            ) {
                for (i in 0 until n) {
                    val c = when {
                        i < state.index -> accent
                        i == state.index -> accent.copy(alpha = 0.55f)
                        else -> SimColors.Tint
                    }
                    Box(modifier = Modifier.weight(1f).fillMaxHeight().background(c, RoundedCornerShape(4.dp)))
                }
            }
        } else {
            Box(modifier = Modifier.fillMaxWidth().height(8.dp).align(Alignment.CenterStart).background(SimColors.Tint, CircleShape))
            Box(modifier = Modifier.width(thumbX).height(8.dp).align(Alignment.CenterStart).background(accent, CircleShape))
        }
        // Flagged steps: a tick through the track at each one's centre.
        marks.filter { it in 0 until n }.forEach { i ->
            Box(
                modifier = Modifier
                    .offset(x = w * ((i + 0.5f) / n) - 1.5.dp)
                    .align(Alignment.CenterStart)
                    .width(3.dp)
                    .height(12.dp)
                    .background(markColor, RoundedCornerShape(1.dp)),
            )
        }
        val thumbSize = if (dragging) 31.dp else 28.dp
        Box(
            modifier = Modifier
                .offset(x = (thumbX - thumbSize / 2).coerceIn(0.dp, w - thumbSize))
                .align(Alignment.CenterStart)
                .size(thumbSize)
                .shadow(5.dp, CircleShape)
                .background(Color.White, CircleShape),
        )
        if (dragging) {
            val caption = captions?.getOrNull(state.index)?.let { LabCaptionText.headline(it) }
            val bubbleW = minOf(w, 260.dp)
            Row(
                modifier = Modifier
                    .offset(x = (thumbX - bubbleW / 2).coerceIn(0.dp, w - bubbleW), y = (-44).dp)
                    .width(bubbleW)
                    .height(34.dp)
                    .shadow(9.dp, RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(10.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("${state.index + 1}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = accent)
                if (caption != null) Text(caption, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** Every caption as a readable transcript; tap one to jump to it. The stage stays visible above. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StepsSheet(state: PlaybackState, captions: List<String>, onDismiss: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (state.index - 2).coerceAtLeast(0))
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.background) {
        Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(44.dp)) {
            Text("All steps", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.align(Alignment.Center))
            Text(
                "Done",
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = accent,
                modifier = Modifier.align(Alignment.CenterEnd).clickable(onClick = onDismiss).padding(4.dp),
            )
        }
        LazyColumn(
            state = listState,
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surface),
        ) {
            items(captions.size) { i ->
                val current = i == state.index
                val seen = i <= state.index
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (current) accent.copy(alpha = 0.18f) else Color.Transparent)
                        .clickable { state.jump(i) }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .background(
                                when {
                                    current -> accent
                                    seen -> accent.copy(alpha = 0.35f)
                                    else -> SimColors.Tint
                                },
                                CircleShape,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "${i + 1}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (seen) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        captions[i],
                        fontSize = 15.sp,
                        lineHeight = 20.sp,
                        color = if (seen) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (i < captions.lastIndex) {
                    HorizontalDivider(modifier = Modifier.padding(start = 50.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline)
                }
            }
        }
    }
}

// ── Narration ────────────────────────────────────────────────────────────────

object LabCaptionText {
    fun headline(text: String): String = split(text).first

    // Abbreviations whose full stop does not end a sentence.
    private val abbreviations = setOf("e.g", "i.e", "vs", "cf", "approx", "etc", "Fig", "fig", "No", "no")
    private val stop = Regex("""[.!?]\s+""")

    /**
     * Index just past the first real sentence end, or -1. A break needs the next sentence to start
     * with a capital, a digit, a quote, a bracket or markdown — or, 20+ characters in, with a lowercase
     * code name ("lo is a max-heap"). "0.867" never qualifies (no space), nor does "e.g. x".
     */
    private fun sentenceEnd(text: String): Int {
        for (m in stop.findAll(text)) {
            val next = text.getOrNull(m.range.last + 1) ?: return -1
            val word = text.substring(0, m.range.first).takeLastWhile { !it.isWhitespace() }
            if (word in abbreviations) continue
            val ok = next.isUpperCase() || next.isDigit() || next in "\"'([*`∞" ||
                (next.isLowerCase() && m.range.first >= 20)
            if (ok) return m.range.first + 1
        }
        return -1
    }

    fun split(text: String): Pair<String, String?> {
        val end = sentenceEnd(text)
        var head = if (end < 0) text else text.substring(0, end)
        var rest = if (end < 0) null else text.substring(end).trim().ifEmpty { null }
        if (rest == null) {
            val dash = text.indexOf(" — ")
            if (dash >= 8) {
                head = text.substring(0, dash) + "."
                rest = text.substring(dash + 3).trim().ifEmpty { null }?.replaceFirstChar { it.uppercase() }
            }
        }
        if (head.length > 140) {
            val cut = listOf(": ", " — ")
                .mapNotNull { mark -> head.indexOf(mark, 40).takeIf { it in 40..140 }?.let { it to mark } }
                .minByOrNull { it.first }
            if (cut != null) {
                val (at, mark) = cut
                val tail = head.substring(at + mark.length).replaceFirstChar { it.uppercase() }
                head = head.substring(0, at) + if (mark == ": ") ":" else "."
                rest = listOfNotNull(tail, rest).joinToString(" ")
            }
        }
        return head to rest
    }
}

// Status lines use **bold** for the claim a step proves and *italics* for a stressed word; both render
// here instead of showing their asterisks. Anything unmatched is left as written.
private val markdownSpan = Regex("""\*\*(.+?)\*\*|\*(\S(?:.*?\S)?)\*""")

internal fun inlineMarkdown(text: String): AnnotatedString = buildAnnotatedString {
    var at = 0
    for (m in markdownSpan.findAll(text)) {
        append(text.substring(at, m.range.first))
        val bold = m.groups[1]
        if (bold != null) {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(bold.value) }
        } else {
            withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(m.groups[2]!!.value) }
        }
        at = m.range.last + 1
    }
    append(text.substring(at))
}

/**
 * Narration under a lab's card (docs/ios-design/Simulations iOS.html): the step's headline large and
 * bold, the explanation muted beneath it. [text] is split by [LabCaptionText.split].
 */
@Composable
fun LabNarration(text: String, modifier: Modifier = Modifier) {
    val (head, tail) = LabCaptionText.split(text)
    Column(modifier = modifier.fillMaxWidth()) {
        Text(inlineMarkdown(head), fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        if (tail != null) {
            Text(
                inlineMarkdown(tail),
                fontSize = 15.sp,
                lineHeight = 22.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

/** Narration: what happened (bold) over why it matters (muted). */
@Composable
fun LabCaption(text: String, modifier: Modifier = Modifier) {
    val (head, tail) = LabCaptionText.split(text)
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(head, fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
        if (tail != null) {
            Text(tail, fontSize = 15.sp, lineHeight = 21.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * A readout ("sum = 9 · best = 9") as mono value chips. Keys that name the answer get the accent,
 * as do the chips at [accented] (the lab's headline number).
 */
@Composable
fun ReadoutChips(text: String, modifier: Modifier = Modifier, accented: Set<Int> = emptySet()) {
    ReadoutChips(readoutParts(text), modifier, accented)
}

/** [parts] as (key, value) chips; a null key shows the value alone ("y = 0.35x + 1.5"). */
@Composable
fun ReadoutChips(
    parts: List<Pair<String?, String>>,
    modifier: Modifier = Modifier,
    accented: Set<Int> = emptySet(),
    /** Chips drawn as a warning, in red (a nonzero unknown-token count). */
    warned: Set<Int> = emptySet(),
    /** Chips drawn as good news, in green (cache hits). */
    positive: Set<Int> = emptySet(),
    /** A lab-specific chip after the rest (a share bar), in the same flow. */
    trailing: (@Composable () -> Unit)? = null,
) {
    val accent = MaterialTheme.colorScheme.primary
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        parts.forEachIndexed { i, (key, value) ->
            val warn = i in warned
            val good = i in positive
            val strong = i in accented ||
                (key != null && listOf("best", "answer", "result", "final").any { key.lowercase().startsWith(it) })
            Row(
                modifier = Modifier
                    .height(32.dp)
                    .background(
                        when {
                            warn -> SimColors.Red.copy(alpha = 0.18f)
                            good -> SimColors.Green.copy(alpha = 0.18f)
                            strong -> accent.copy(alpha = 0.22f)
                            else -> SimColors.Tint
                        },
                        RoundedCornerShape(9.dp),
                    )
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (key != null) {
                    Text(key, fontFamily = IBMPlexMono, fontSize = 15.sp, maxLines = 1, color = if (warn) SimColors.Red else if (good) SimColors.Green else if (strong) accent else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(value, fontFamily = IBMPlexMono, fontWeight = FontWeight.Medium, fontSize = 15.sp, maxLines = 1, color = if (warn) SimColors.Red else Color.Unspecified)
            }
        }
        trailing?.invoke()
    }
}

internal fun readoutParts(text: String): List<Pair<String?, String>> = text.split(" · ").map { chunk ->
    for (sep in listOf(" = ", ": ")) {
        val at = chunk.indexOf(sep)
        if (at in 1..22) return@map chunk.substring(0, at) to chunk.substring(at + sep.length)
    }
    null to chunk
}

/** A lab's legend row: 10dp swatches, 13sp labels. */
@Composable
fun LabLegend(items: List<Pair<Color, String>>, modifier: Modifier = Modifier) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items.forEach { (color, label) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(10.dp).background(color, RoundedCornerShape(3.dp)))
                Text(
                    label,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
        }
    }
}

// Icon-only control, kept for the graph page's run/reset row. Tinted like [SimOpRow].
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
        colors = ButtonDefaults.buttonColors(containerColor = color.copy(alpha = 0.16f), contentColor = color),
        shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(0.dp),
        modifier = modifier.height(SimControlHeight),
    ) {
        Icon(icon, contentDescription = contentDescription, modifier = Modifier.size(iconSize))
    }
}

// ── Linked-list nodes ────────────────────────────────────────────────────────

/**
 * One size for every linked-list node in the app (the singly linked list sandbox, the doubly linked
 * list / skip list / LRU players), so moving between labs never rescales the same structure. A row
 * too long for the screen scrolls sideways instead of shrinking its nodes.
 */
object LinkedNodeSpec {
    val Width = 44.dp
    val Height = 52.dp
    val Radius = 10.dp
    val FontSize = 18.sp
    /** The gap between nodes, where the pointer arrows are drawn. */
    val Link = 22.dp
}

// ── Segments & parameter sliders ─────────────────────────────────────────────

/** A lab's variant switch (AND / OR / XOR, Hard / PA-I / PA-II) as an iOS-style segmented control. */
@Composable
fun LabSegments(labels: List<String>, selected: Int, modifier: Modifier = Modifier, onSelect: (Int) -> Unit) {
    val dark = LocalDarkTheme.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(34.dp)
            .background(SimColors.Tint, RoundedCornerShape(9.dp))
            .padding(2.dp),
    ) {
        labels.forEachIndexed { i, label ->
            val on = i == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(30.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(if (on) (if (dark) Color(0xFF636366) else Color.White) else Color.Transparent)
                    .clickable { onSelect(i) },
                contentAlignment = Alignment.Center,
            ) {
                Text(label, fontSize = 14.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium, maxLines = 1)
            }
        }
    }
}

/** "Weight w₁ ……… 1.0" over a thin accent track with a white 28dp thumb. [symbol] is set in mono. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LabParamSlider(
    name: String,
    symbol: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    format: (Float) -> String = { String.format("%.1f", it) },
    onChange: (Float) -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                buildAnnotatedString {
                    append(name)
                    if (symbol.isNotEmpty()) {
                        append(" ")
                        withStyle(SpanStyle(fontFamily = IBMPlexMono, fontWeight = FontWeight.Normal, color = MaterialTheme.colorScheme.onSurfaceVariant)) {
                            append(symbol)
                        }
                    }
                },
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Text(format(value), fontFamily = IBMPlexMono, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = accent)
        }
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            modifier = Modifier.height(36.dp),
            thumb = {
                Box(modifier = Modifier.size(28.dp).shadow(3.dp, CircleShape).background(Color.White, CircleShape))
            },
            track = {
                val fraction = ((value - range.start) / (range.endInclusive - range.start)).coerceIn(0f, 1f)
                Canvas(modifier = Modifier.fillMaxWidth().height(4.dp)) {
                    val y = size.height / 2
                    drawLine(SimColors.Tint, Offset(0f, y), Offset(size.width, y), strokeWidth = size.height, cap = StrokeCap.Round)
                    drawLine(accent, Offset(0f, y), Offset(size.width * fraction, y), strokeWidth = size.height, cap = StrokeCap.Round)
                }
            },
        )
    }
}

/** A full-width 52dp button: accent-filled when [primary], tinted otherwise. */
@Composable
fun LabButton(label: String, primary: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .height(52.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (primary) MaterialTheme.colorScheme.primary else SimColors.Tint)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (primary) Color.White else MaterialTheme.colorScheme.onSurface,
        )
    }
}

// ── Notice ───────────────────────────────────────────────────────────────────

/**
 * Why a sandbox op can't run (no free slot, nothing to remove, no value), or a heads-up about what it
 * will cost. Sits at the top of the controls, so it stays in view when the caption doesn't.
 */
@Composable
fun LabNotice(text: String, modifier: Modifier = Modifier, blocked: Boolean = true) {
    val color = if (blocked) SimColors.Red else MaterialTheme.colorScheme.primary
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(color.copy(alpha = 0.14f), RoundedCornerShape(12.dp))
            .border(1.dp, color.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            if (blocked) Icons.Filled.Warning else Icons.Filled.Info,
            contentDescription = null,
            tint = color,
            modifier = Modifier.padding(top = 2.dp).size(16.dp),
        )
        Text(text, fontSize = 14.sp, lineHeight = 19.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
    }
}
