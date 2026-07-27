package com.algora.app.feature.practice

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.algora.app.core.data.progress.ProgressRepository
import com.algora.app.core.data.progress.progressDataStore
import com.algora.app.core.data.settings.SettingsRepository
import com.algora.app.core.data.settings.settingsDataStore
import com.algora.app.core.nav.ProblemsRoute
import com.algora.app.core.nav.QuizCatalogRoute
import com.algora.app.core.nav.ReviewRoute
import com.algora.app.core.nav.Screen
import com.algora.app.core.ui.components.resolveIcon
import com.algora.app.core.ui.theme.Gradients
import com.algora.app.core.ui.theme.SpaceGrotesk
import com.algora.app.feature.interviewprep.quiz.QuizRegistry
import com.algora.app.feature.practice.problems.ProblemRegistry
import com.algora.app.feature.review.DAILY_NEW_CARD_LIMIT
import com.algora.app.feature.review.allReviewCards
import com.algora.app.feature.review.reviewCounts

// Mirrors Home's QuickCard so the two screens read as the same surface: gradient tile, white icon
// chip, title, one-line subtitle.
private data class PracticeEntry(
    val route: String,
    val title: String,
    val subtitle: String,
    val iconName: String,
    val gradient: List<Color>,
)

// The Practice tab: everything the learner *does*. Card review used to sit on Home (the Learning
// tab) where it read as reference material, and was split across an unscheduled Flashcards deck and
// a separate Spaced Repetition screen over the same content. Both are now one scheduled deck.
@Composable
fun PracticeScreen(onNavigate: (String) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val repository = remember { ProgressRepository(context.progressDataStore) }
    val solvedIds by repository.solvedProblemIds.collectAsState(initial = emptySet())

    val problemCount = remember { ProblemRegistry.all.size }
    val quizCount = remember { QuizRegistry.all.size }
    val solvedCount = solvedIds.count { id -> ProblemRegistry.get(id) != null }

    // Cards are SM-2 scheduled, so the row states what is actually waiting rather than the deck size.
    val settings = remember { SettingsRepository(context.settingsDataStore) }
    val today = System.currentTimeMillis() / 86_400_000L
    val srs by settings.srs.collectAsState(initial = null)
    val introducedToday by settings.newCardsIntroduced(today).collectAsState(initial = null)
    val allCards = remember { allReviewCards() }
    val cardSubtitle = when (val map = srs) {
        null -> "Spaced repetition — graded recall on a schedule"
        else -> {
            val remainingNew = (DAILY_NEW_CARD_LIMIT - (introducedToday ?: 0)).coerceAtLeast(0)
            val counts = reviewCounts(cards = allCards, srs = map, today = today, newAllowance = remainingNew)
            // Kept short — this sits in a half-width tile, not a full-width row.
            when {
                counts.waiting > 0 -> "${counts.waiting} to review · ${counts.new} new"
                counts.newHeldBack > 0 -> "Done for today"
                else -> "All caught up"
            }
        }
    }

    val entries = listOf(
        PracticeEntry(
            route = ProblemsRoute.ROUTE,
            title = "Problem Solving",
            subtitle = "$problemCount problems · $solvedCount solved",
            iconName = "chip",
            gradient = Gradients.Blue,
        ),
        PracticeEntry(
            route = QuizCatalogRoute.ROUTE,
            title = "Quizzes",
            subtitle = "$quizCount timed sets",
            iconName = "help",
            gradient = Gradients.Amber,
        ),
        PracticeEntry(
            route = ReviewRoute.ROUTE,
            title = "Flashcards",
            subtitle = cardSubtitle,
            iconName = "stack",
            gradient = Gradients.Violet,
        ),
        PracticeEntry(
            route = Screen.InterviewPrep.route,
            title = "Interview Prep",
            subtitle = "Patterns, company sets, mock rounds",
            iconName = "target",
            gradient = Gradients.Pink,
        ),
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp),
    ) {
        Spacer(modifier = Modifier.height(6.dp))

        Column(modifier = Modifier.padding(top = 10.dp, bottom = 14.dp)) {
            Text(
                "Practice",
                fontFamily = SpaceGrotesk,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
            )
            Text(
                "Recall, drill and solve",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 3.dp),
            )
        }

        // Same 2×2 gradient grid as Home's Quick Access block.
        Column(verticalArrangement = Arrangement.spacedBy(13.dp)) {
            entries.chunked(2).forEach { rowEntries ->
                Row(horizontalArrangement = Arrangement.spacedBy(13.dp), modifier = Modifier.fillMaxWidth()) {
                    rowEntries.forEach { entry ->
                        PracticeCard(entry, modifier = Modifier.weight(1f)) { onNavigate(entry.route) }
                    }
                    // Keeps a lone trailing card at half width instead of stretching it.
                    if (rowEntries.size == 1) Spacer(modifier = Modifier.weight(1f))
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun PracticeCard(entry: PracticeEntry, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier = modifier
            .heightIn(min = 150.dp)
            .background(Brush.linearGradient(entry.gradient), RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(Color.White.copy(alpha = 0.22f), RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(resolveIcon(entry.iconName), contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
        }
        Spacer(modifier = Modifier.weight(1f))
        Text(
            entry.title,
            color = Color.White,
            fontFamily = SpaceGrotesk,
            fontWeight = FontWeight.Bold,
            fontSize = 16.5.sp,
        )
        Text(entry.subtitle, color = Color.White.copy(alpha = 0.82f), fontSize = 12.5.sp)
    }
}
