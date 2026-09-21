package com.algora.app.core.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import com.algora.app.core.data.model.Difficulty
import com.algora.app.core.ui.theme.SimColors

// Matches docs/design/Algora.dc.html's row.style/markStyle/endStyle exactly.
// Rows never show a per-topic icon (only section headers do) — confirmed from the mock markup.
private val RowCompleteGreen = SimColors.Green

@Composable
fun TopicRow(
    title: String,
    isCompleted: Boolean,
    // Premium topic the user cannot open yet. Premium owners (and topics with a live rewarded-ad
    // unlock) see the normal chevron row instead.
    isLocked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    difficulty: Difficulty? = null,
    // False for purchase-only rows (PaidOnly): the lock drops its play triangle.
    adUnlockable: Boolean = true,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .alpha(if (isLocked) 0.72f else 1f)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isCompleted) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = "Completed",
                    tint = RowCompleteGreen,
                    modifier = Modifier.size(22.dp),
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .border(2.dp, MaterialTheme.colorScheme.outline, CircleShape),
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(start = 12.dp).weight(1f),
            )
            if (difficulty != null) {
                DifficultyBadge(difficulty, modifier = Modifier.padding(end = 8.dp))
            }
            if (isLocked) {
                AdUnlockableLockIcon(size = 18.dp, adUnlockable = adUnlockable)
            } else {
                Icon(
                    imageVector = Icons.Filled.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}
