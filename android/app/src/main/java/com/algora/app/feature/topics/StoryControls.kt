package com.algora.app.feature.topics

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.algora.app.core.ui.theme.IBMPlexMono
import com.algora.app.core.ui.theme.SimColors

// ── Story chips and parameter steppers ───────────────────────────────────────
// Pieces of the redesigned ML and deep-learning labs: value chips that can carry a series dot or read
// as good news, and the parameter bar that replaces a stack of sliders with a picker and one stepper.

/**
 * A value chip: the key muted, the value in its tone. [dot] leads with a series colour; [good] tints it
 * green; [tint] washes the chip in a state's colour and inks the key in it (the "MSE" or "τ" to watch).
 */
internal class LabChip(
    val key: String,
    val value: String,
    val tone: StoryTone = StoryTone.Idle,
    val dot: Color? = null,
    val good: Boolean = false,
    val tint: StoryTone? = null,
    // A tint colour outside the story tones (a class's pink); wins over [tint].
    val tintColor: Color? = null,
)

@Composable
internal fun LabChips(chips: List<LabChip>, modifier: Modifier = Modifier) {
    if (chips.isEmpty()) return
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        chips.forEach { chip ->
            Row(
                modifier = Modifier
                    .height(32.dp)
                    .background(
                        when {
                            chip.good -> SimColors.Green.copy(alpha = 0.18f)
                            chip.tintColor != null -> chip.tintColor.copy(alpha = 0.2f)
                            chip.tint != null -> chip.tint.color().copy(alpha = 0.2f)
                            else -> SimColors.Tint
                        },
                        RoundedCornerShape(9.dp),
                    )
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                chip.dot?.let { Box(Modifier.size(8.dp).background(it, CircleShape)) }
                Text(
                    chip.key,
                    fontFamily = IBMPlexMono,
                    fontSize = 15.sp,
                    maxLines = 1,
                    color = when {
                        chip.good -> StoryTone.Done.ink()
                        chip.tintColor != null -> chip.tintColor
                        chip.tint != null -> chip.tint.ink()
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                Text(chip.value, fontFamily = IBMPlexMono, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, color = chip.tone.ink())
            }
        }
    }
}

/** One tunable value: its picker label, its name and symbol on the stepper row, and the value as shown. */
internal class LabParam(
    val tab: String,
    val name: String,
    val symbol: String,
    val text: String,
    val canDecrease: Boolean,
    val canIncrease: Boolean,
)

/** A picker over [params] (only when there are several) above one stepper for the selected one. */
@Composable
internal fun LabParamControls(
    params: List<LabParam>,
    selected: Int,
    onSelect: (Int) -> Unit,
    onStep: (index: Int, delta: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val index = selected.coerceIn(0, params.lastIndex)
    Column(modifier = modifier.fillMaxWidth()) {
        if (params.size > 1) LabSegments(params.map { it.tab }, index, Modifier.padding(bottom = 14.dp), onSelect)
        LabParamStepper(params[index]) { onStep(index, it) }
    }
}

/** "Reach  log₁₀ γ ……… −0.8  [− | +]". */
@Composable
internal fun LabParamStepper(param: LabParam, onStep: (Int) -> Unit) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val onSurface = MaterialTheme.colorScheme.onSurface
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(param.name, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = onSurface, maxLines = 1)
        if (param.symbol.isNotEmpty()) {
            Text(param.symbol, fontFamily = IBMPlexMono, fontSize = 14.sp, color = muted, maxLines = 1, modifier = Modifier.padding(start = 8.dp))
        }
        Box(Modifier.weight(1f))
        Text(
            param.text,
            fontFamily = IBMPlexMono,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            modifier = Modifier.padding(end = 14.dp),
        )
        StepperPill(param, onStep)
    }
}

/** The − | + pill of a stepper. */
@Composable
private fun StepperPill(param: LabParam, onStep: (Int) -> Unit) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val onSurface = MaterialTheme.colorScheme.onSurface
    Row(
        modifier = Modifier.height(36.dp).background(SimColors.Tint, RoundedCornerShape(10.dp)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        listOf(-1 to "−", 1 to "+").forEachIndexed { i, (delta, glyph) ->
            val enabled = if (delta < 0) param.canDecrease else param.canIncrease
            if (i == 1) Box(Modifier.width(1.dp).height(18.dp).background(muted.copy(alpha = 0.35f)))
            Box(
                modifier = Modifier
                    .size(width = 44.dp, height = 36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(enabled = enabled) { onStep(delta) },
                contentAlignment = Alignment.Center,
            ) {
                Text(glyph, fontSize = 20.sp, fontWeight = FontWeight.Medium, color = onSurface.copy(alpha = if (enabled) 1f else 0.3f))
            }
        }
    }
}

/**
 * The picker over the parameters, then one row: the selected value, its − | + pill and the lab's action
 * ("0.35  [− | +]  [Show Best Fit]"). The picker names the parameter, so the row doesn't repeat it.
 */
@Composable
internal fun LabParamActionControls(
    params: List<LabParam>,
    selected: Int,
    onSelect: (Int) -> Unit,
    onStep: (index: Int, delta: Int) -> Unit,
    action: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val index = selected.coerceIn(0, params.lastIndex)
    val param = params[index]
    Column(modifier = modifier.fillMaxWidth()) {
        if (params.size > 1) LabSegments(params.map { it.tab }, index, Modifier.padding(bottom = 14.dp), onSelect)
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                param.text,
                fontFamily = IBMPlexMono,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                modifier = Modifier.widthIn(min = 52.dp).padding(end = 12.dp),
            )
            StepperPill(param) { onStep(index, it) }
            LabButton(action, primary = true, modifier = Modifier.padding(start = 16.dp).weight(1f), onClick = onAction)
        }
    }
}

/** A square step-back button beside the lab's labelled primary action ("‹  [Next Example]"). */
@Composable
internal fun LabBackActionRow(
    action: String,
    backEnabled: Boolean,
    onBack: () -> Unit,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(width = 56.dp, height = 52.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(SimColors.Tint)
                .clickable(enabled = backEnabled, onClickLabel = "Step back", onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = "Step back",
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = if (backEnabled) 1f else 0.3f),
                modifier = Modifier.size(30.dp),
            )
        }
        LabButton(action, primary = true, modifier = Modifier.padding(start = 12.dp).weight(1f), onClick = onAction)
    }
}
