package com.algora.app.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.algora.app.core.ui.theme.SpaceGrotesk

/**
 * The back button's touch target, and the width reserved opposite it when there is no trailing action.
 *
 * The mock draws a 36px button inside a 14px gutter, putting the icon's centre 32px from the edge.
 * A 36dp target is under the 48dp minimum for a tappable control, so the button is widened to 48dp
 * and the row's start padding narrowed to 8dp to compensate: the icon lands on the same 32dp line
 * the mock puts it on, with a touch target that can actually be hit.
 */
private val BackButtonSize = 48.dp

/** Start inset, chosen so the widened back button still centres its icon on the mock's 32dp line. */
private val HeaderStartPadding = 8.dp

/** The mock's own gutter, kept for the trailing edge where there is no oversized touch target. */
private val HeaderEndPadding = 14.dp

/**
 * The one screen header, ported from the mock's shared `headerStyle` (docs/design/Algora.dc.html
 * line 601) and the back-button/title markup every screen that uses it repeats (lines 95, 136,
 * 309, 348): a back button, a left-aligned 17sp Space Grotesk title, and a bottom hairline in
 * `--border`.
 *
 * It is deliberately not part of the scrolling body — the mock pins it with `position:sticky`, so
 * the title and the way back stay put while the content moves under them.
 *
 * `trailing` hangs an action off the right edge (the topic page's bookmark toggle, the quiz
 * timer). It replaces the empty spacer that otherwise holds the right edge.
 */
@Composable
fun ScreenHeader(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: @Composable (RowScope.() -> Unit)? = null,
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .padding(
                    start = HeaderStartPadding,
                    end = HeaderEndPadding,
                    top = 8.dp,
                    bottom = 12.dp,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // The arrow and the title are one back control: tapping the title goes back too, as it
            // does on iOS. The group wraps its content, so empty header space stays inert.
            Row(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .clip(RoundedCornerShape(BackButtonSize / 2))
                        .clickable(onClickLabel = "Back", role = Role.Button, onClick = onBack),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(modifier = Modifier.size(BackButtonSize), contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Text(
                        text = title,
                        fontFamily = SpaceGrotesk,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Start,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(end = 12.dp),
                    )
                }
            }
            if (trailing != null) trailing() else Box(modifier = Modifier.size(BackButtonSize))
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
    }
}
