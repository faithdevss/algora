package com.algora.app.core.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.algora.app.core.ui.theme.SpaceGrotesk

/**
 * A collapsible group header for long catalogs — the Simulations tab's 363 labs and the problem
 * bank's 100+ problems both need one, so the chrome lives here rather than twice.
 *
 * Deliberately not a card: a tinted strip with an accent rail reads as "the rows below belong to
 * this", where a bordered card would read as another peer row. The count sits in the mock's
 * 16%-alpha badge pill (docs/design/Algora.dc.html's badgeStyle) instead of floating loose.
 */
@Composable
fun AccordionHeader(
    title: String,
    count: Int,
    accentColor: Long,
    isExpanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    // Premium group the user hasn't unlocked. Reads like TopicRow's lock treatment: dimmed strip,
    // lock icon instead of the expand chevron. onClick still fires — the caller decides what a tap
    // on a locked header does (usually route to the paywall instead of expanding).
    locked: Boolean = false,
) {
    val accent = Color(accentColor)
    val chevronRotation by animateFloatAsState(if (isExpanded) 180f else 0f, label = "accordionChevron")
    Row(
        modifier = modifier
            .fillMaxWidth()
            .alpha(if (locked) 0.72f else 1f)
            .clip(RoundedCornerShape(12.dp))
            .background(accent.copy(alpha = if (isExpanded) 0.13f else 0.06f))
            .clickable(onClick = onClick)
            .padding(start = 10.dp, end = 10.dp, top = 9.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(modifier = Modifier.size(width = 3.dp, height = 14.dp).background(accent, RoundedCornerShape(2.dp)))
        Text(
            title,
            fontFamily = SpaceGrotesk,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            letterSpacing = 0.2.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        subtitle?.let {
            Text(it, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Box(
            modifier = Modifier
                .background(accent.copy(alpha = 0.16f), RoundedCornerShape(8.dp))
                .padding(horizontal = 7.dp, vertical = 2.dp),
        ) {
            Text(
                "$count",
                fontFamily = SpaceGrotesk,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                color = accent,
            )
        }
        if (locked) {
            AdUnlockableLockIcon(size = 17.dp)
        } else {
            Icon(
                Icons.Filled.ExpandMore,
                contentDescription = if (isExpanded) "Collapse" else "Expand",
                tint = accent.copy(alpha = 0.85f),
                modifier = Modifier.size(17.dp).rotate(chevronRotation),
            )
        }
    }
}
