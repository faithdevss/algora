package com.algora.app.feature.practice.weakspots

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.foundation.clickable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.algora.app.core.data.TopicRegistry
import com.algora.app.core.data.entitlement.EntitlementRepository
import com.algora.app.core.data.entitlement.entitlementDataStore
import com.algora.app.core.data.settings.SettingsRepository
import com.algora.app.core.data.settings.questionKey
import com.algora.app.core.data.settings.settingsDataStore
import com.algora.app.core.ui.components.ScreenHeader
import com.algora.app.core.ui.theme.SimColors
import com.algora.app.feature.interviewprep.quiz.Quiz
import com.algora.app.feature.interviewprep.quiz.QuizRegistry
import com.algora.app.feature.interviewprep.quiz.QuizScreen
import com.algora.app.feature.practice.daily.DrillQuestion
import kotlinx.coroutines.flow.first

// Same bands as the quiz catalog's score pills, so a percentage reads the same everywhere.
private fun weakColor(percent: Int): Color = when {
    percent >= 80 -> SimColors.Green
    percent >= 60 -> SimColors.Amber
    else -> SimColors.Red
}

/**
 * The card at the top of the quiz catalog: the weakest patterns with a bar each, and one button that
 * builds a drill from them. Before there is enough evidence it says what unlocks it rather than
 * hiding — the card is also how a learner finds out the feature exists.
 */
/** The weakest patterns in one compact card: a header row with the drill button, then a pill per pattern. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun WeakSpotCard(
    weakest: List<TagStat>,
    answeredTotal: Int,
    onDrill: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = SimColors.Red
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(36.dp).background(accent.copy(alpha = 0.16f), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.TrackChanges, contentDescription = null, tint = accent, modifier = Modifier.size(19.dp))
                }
                Spacer(modifier = Modifier.size(11.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Your weak spots", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        when {
                            weakest.isNotEmpty() -> "Missed most · $answeredTotal answers"
                            answeredTotal == 0 -> "Answer a few sets to see them here"
                            else -> "None yet — every pattern is at 80%+"
                        },
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                if (weakest.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .height(36.dp)
                            .background(Color(0xFF3E8E5E), androidx.compose.foundation.shape.CircleShape)
                            .clickable(onClickLabel = "Drill weak spots, $WEAK_SPOT_DRILL_SIZE questions", onClick = onDrill)
                            .padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Text(
                            "Drill $WEAK_SPOT_DRILL_SIZE", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(start = 6.dp),
                        )
                    }
                }
            }
            if (weakest.isNotEmpty()) {
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    weakest.forEach { stat -> WeakPill(stat) }
                }
            }
        }
    }
}

@Composable
private fun WeakPill(stat: TagStat) {
    val color = weakColor(stat.percent)
    Row(
        modifier = Modifier
            .background(color.copy(alpha = 0.14f), androidx.compose.foundation.shape.CircleShape)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stat.tag, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        Text(
            "${stat.correct}/${stat.answered}", fontFamily = com.algora.app.core.ui.theme.IBMPlexMono, fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold, color = color, modifier = Modifier.padding(start = 6.dp),
        )
    }
}

/**
 * The drill itself. Built once from settled values — like the daily drill, rebuilding from live
 * flows would reroll the set the moment an answer was recorded — then run through QuizScreen with
 * every question reporting back to the set it came from.
 */
@Composable
fun WeakSpotDrillScreen(onBack: () -> Unit, onTopicClick: (String) -> Unit, onGoPremium: () -> Unit) {
    val context = LocalContext.current
    val settings = remember { SettingsRepository(context.settingsDataStore) }
    val entitlements = remember { EntitlementRepository(context.entitlementDataStore) }

    var built by remember { mutableStateOf<Pair<Quiz, List<DrillQuestion>>?>(null) }
    var empty by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val isPremium = entitlements.isPremium.first()
        val all = QuizRegistry.all
        val results = effectiveResults(settings.questionResults.first(), all, settings.quizAttempts.first())
        val tags = weakestTags(tagStats(results, all)).map { it.tag }
        // Interview practice is purchase-only (PaidOnly), so a locked set is never sampled — the
        // question would open content the learner has not bought.
        val accessible = all.filter { (id, _) -> TopicRegistry.find(id)?.isPremium != true || isPremium }
        val picked = pickWeakSpotQuestions(tags.toSet(), accessible, results)
        if (picked.isEmpty()) empty = true else built = weakSpotQuiz(picked, tags) to picked
    }

    val ready = built
    when {
        ready != null -> QuizScreen(
            quizId = WEAK_SPOT_QUIZ_ID,
            quiz = ready.first,
            onBack = onBack,
            onTopicClick = onTopicClick,
            onFinish = {},
            onGoPremium = onGoPremium,
            questionKeys = ready.second.map { questionKey(it.quizId, it.index) },
            // A new set every time, so there is no best score to compare against.
            trackBest = false,
        )

        else -> Column(modifier = Modifier.fillMaxSize()) {
            ScreenHeader(title = "Weak-spot drill", onBack = onBack)
            Text(
                if (empty) "No questions to drill yet — finish a few sets first." else "Building your drill…",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}
