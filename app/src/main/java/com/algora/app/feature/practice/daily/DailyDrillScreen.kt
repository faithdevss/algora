package com.algora.app.feature.practice.daily

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.algora.app.core.nav.ReviewRoute
import com.algora.app.core.ui.components.resolveIcon
import com.algora.app.core.ui.theme.SpaceGrotesk
import com.algora.app.feature.interviewprep.quiz.QuizScreen

// One mixed practice session per day: clear the recall queue, solve one problem, answer a short
// question set weighted towards what you have missed before. The set is fixed for the day, so the
// screen can be left and reopened without losing (or rerolling) the work.
@Composable
fun DailyDrillScreen(
    onNavigate: (String) -> Unit,
    onProblemClick: (String) -> Unit,
    onTopicClick: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val status = rememberDrillStatus()
    var runningQuiz by remember { mutableStateOf(false) }

    // The question step runs the sampled set inline as a real quiz — same timer, same results
    // screen, and its attempt is what marks the step done.
    if (runningQuiz && status.questions.isNotEmpty()) {
        val drill = DailyDrill(day = status.day, problem = status.problem, questions = status.questions)
        QuizScreen(
            quizId = DAILY_DRILL_QUIZ_ID,
            quiz = drill.asQuiz(),
            onBack = { runningQuiz = false },
            onTopicClick = onTopicClick,
            onFinish = {},
        )
        return
    }

    Column(modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text("Daily Drill", style = MaterialTheme.typography.headlineMedium)
        }

        Column(modifier = Modifier.padding(horizontal = 18.dp)) {
            Text(
                when {
                    !status.ready -> "Building today's set…"
                    status.allDone -> "Done for today — come back tomorrow."
                    else -> "${status.doneCount} of ${status.stepCount} done"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (status.allDone) DoneGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            LinearProgressIndicator(
                progress = { status.doneCount / status.stepCount.toFloat() },
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(18.dp))

            DrillStepCard(
                index = 1,
                title = "Recall",
                subtitle = if (status.recallDone) "Nothing waiting" else "${status.cardsWaiting} cards waiting",
                iconName = "stack",
                accent = Color(0xFFC084FC),
                done = status.recallDone,
                enabled = true,
                onClick = { onNavigate(ReviewRoute.ROUTE) },
            )

            DrillStepCard(
                index = 2,
                title = "Solve",
                subtitle = when {
                    !status.ready -> "Picking a problem…"
                    status.problem == null -> "Whole bank solved"
                    else -> status.problem.title
                },
                iconName = "chip",
                accent = Color(0xFF60A5FA),
                done = status.solveDone,
                enabled = status.problem != null,
                onClick = { status.problem?.let { onProblemClick(it.id) } },
            )

            DrillStepCard(
                index = 3,
                title = "Drill",
                subtitle = if (status.questions.isEmpty()) {
                    "No questions available"
                } else {
                    "${status.questions.size} questions · ${status.questions.size * 45 / 60} min"
                },
                iconName = "help",
                accent = Color(0xFFFBBF24),
                done = status.drillDone,
                enabled = status.questions.isNotEmpty(),
                onClick = { runningQuiz = true },
            )

            Text(
                "Questions you have missed before come first; the rest are ones no attempt has covered yet.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 14.dp, bottom = 28.dp),
            )
        }
    }
}

private val DoneGreen = Color(0xFF16A34A)

@Composable
private fun DrillStepCard(
    index: Int,
    title: String,
    subtitle: String,
    iconName: String,
    accent: Color,
    done: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = if (done) DoneGreen.copy(alpha = 0.07f) else MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, if (done) DoneGreen.copy(alpha = 0.4f) else MaterialTheme.colorScheme.outline),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(15.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(accent.copy(alpha = 0.16f), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(resolveIcon(iconName), contentDescription = null, tint = accent, modifier = Modifier.size(22.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "STEP $index",
                    fontFamily = SpaceGrotesk,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(title, fontFamily = SpaceGrotesk, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (done) {
                Icon(Icons.Filled.CheckCircle, contentDescription = "Done", tint = DoneGreen, modifier = Modifier.size(24.dp))
            } else {
                Icon(
                    Icons.Filled.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}
