package com.algora.app.feature.practice

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.algora.app.core.data.TopicRegistry
import com.algora.app.core.data.settings.SettingsRepository
import com.algora.app.core.data.settings.bestCorrect
import com.algora.app.core.data.settings.bestPercent
import com.algora.app.core.data.settings.settingsDataStore
import com.algora.app.core.ui.components.ScreenHeader
import com.algora.app.core.ui.components.resolveIcon
import com.algora.app.core.ui.theme.ScreenBottomInset
import com.algora.app.core.ui.theme.ScreenGutter
import com.algora.app.feature.interviewprep.quiz.QuizRegistry

// Score bands mirror the results screen's 60% pass line.
private fun scoreColor(percent: Int): Color = when {
    percent >= 80 -> Color(0xFF16A34A)
    percent >= 60 -> Color(0xFFF59E0B)
    else -> Color(0xFFEF4444)
}

private fun relativeDay(day: Long, today: Long): String = when (val ago = today - day) {
    0L -> "today"
    1L -> "yesterday"
    in 2L..30L -> "${ago}d ago"
    else -> "a while ago"
}

// Every timed quiz in one list. A row opens the quiz's topic page, which renders QuizScreen — the
// same path the Interview Prep browser already uses, so premium gating stays in one place.
@Composable
fun QuizCatalogScreen(onQuizClick: (String) -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val entries = remember { QuizRegistry.all }

    // Attempt history turns the catalog from a menu into a scoreboard: a row now says whether you
    // have ever taken the set and how it went.
    val context = LocalContext.current
    val settings = remember { SettingsRepository(context.settingsDataStore) }
    val attempts by settings.quizAttempts.collectAsState(initial = emptyMap())
    val today = System.currentTimeMillis() / 86_400_000L

    Column(modifier = modifier.fillMaxSize()) {
        ScreenHeader(title = "Quizzes", onBack = onBack)
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(
                start = ScreenGutter,
                end = ScreenGutter,
                top = 8.dp,
                bottom = ScreenBottomInset,
            ),
        ) {
            item {
                Text(
                    "${entries.size} timed sets — the clock auto-submits when it runs out",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, bottom = 10.dp),
                )
            }

            items(entries.size) { index ->
                val (topicId, quiz) = entries[index]
                val locked = TopicRegistry.find(topicId)?.isPremium == true
                val history = attempts[topicId].orEmpty()
                QuizRow(
                    title = quiz.title,
                    subtitle = "${quiz.questions.size} questions · ${quiz.timeLimitSeconds / 60} min",
                    description = quiz.description,
                    locked = locked,
                    history = history.takeIf { it.isNotEmpty() }?.let { h ->
                        "Best ${h.bestCorrect}/${quiz.questions.size} · last ${relativeDay(h.first().day, today)}"
                    },
                    bestPercent = history.bestPercent,
                    onClick = { onQuizClick(topicId) },
                )
            }
        }
    }
}

@Composable
private fun QuizRow(
    title: String,
    subtitle: String,
    description: String,
    locked: Boolean,
    history: String?,
    bestPercent: Int,
    onClick: () -> Unit,
) {
    val accent = Color(0xFFF59E0B)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(accent.copy(alpha = 0.14f), RoundedCornerShape(13.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    resolveIcon(if (locked) "lock" else "help"),
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(21.dp),
                )
            }
            Spacer(modifier = Modifier.size(13.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = accent,
                )
                Text(
                    description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (history != null) {
                    Text(
                        history,
                        style = MaterialTheme.typography.bodySmall,
                        color = scoreColor(bestPercent),
                        modifier = Modifier.padding(top = 3.dp),
                    )
                }
            }
            if (history != null) {
                Box(
                    modifier = Modifier
                        .background(scoreColor(bestPercent).copy(alpha = 0.14f), RoundedCornerShape(9.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Text(
                        "$bestPercent%",
                        color = scoreColor(bestPercent),
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Spacer(modifier = Modifier.size(6.dp))
            }
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
