package com.algora.app.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.algora.app.core.data.model.Difficulty

// Small color-coded difficulty pill (Phase 7). Green/amber/red mirror the mock's status palette.
@Composable
fun DifficultyBadge(difficulty: Difficulty, modifier: Modifier = Modifier) {
    val (label, color) = when (difficulty) {
        Difficulty.BEGINNER -> "Easy" to Color(0xFF16A34A)
        Difficulty.INTERMEDIATE -> "Medium" to Color(0xFFF59E0B)
        Difficulty.ADVANCED -> "Hard" to Color(0xFFEF4444)
    }
    Text(
        text = label,
        color = color,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        softWrap = false,
        modifier = modifier
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
            .padding(horizontal = 7.dp, vertical = 3.dp),
    )
}
