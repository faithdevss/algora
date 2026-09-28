package com.algora.app.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.algora.app.core.ui.theme.Violet

// Matches docs/design/Algora.dc.html's catBarStyle: 7.dp track, gradient fill from the theme
// accent to Violet (#7C3AED).
@Composable
fun CategoryProgressBar(progress: Float, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(7.dp)
            .background(MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(99.dp)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .background(
                    brush = Brush.horizontalGradient(listOf(MaterialTheme.colorScheme.primary, Violet)),
                    shape = RoundedCornerShape(99.dp),
                ),
        )
    }
}
