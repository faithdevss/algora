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
        Column(modifier = Modifier.padding(15.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(accent.copy(alpha = 0.14f), RoundedCornerShape(13.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.TrackChanges, contentDescription = null, tint = accent, modifier = Modifier.size(22.dp))
                }
                Spacer(modifier = Modifier.size(13.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Your weak spots", style = MaterialTheme.typography.titleMedium)
                    Text(
                        when {
                            weakest.isNotEmpty() -> "The patterns you miss most, from $answeredTotal answers"
                            answeredTotal == 0 -> "Answer a few sets and your weakest patterns show up here"
                            else -> "No weak pattern yet — every pattern with 3+ answers is at 80% or better"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (weakest.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    weakest.forEach { stat -> WeakRow(stat) }
                }
                Spacer(modifier = Modifier.height(14.dp))
                Button(onClick = onDrill, modifier = Modifier.fillMaxWidth()) {
                    Text("Drill weak spots · $WEAK_SPOT_DRILL_SIZE questions")
                }
            }
        }
    }
}

@Composable
private fun WeakRow(stat: TagStat) {
    val color = weakColor(stat.percent)
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stat.tag, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(
                "${stat.correct}/${stat.answered} · ${stat.percent}%",
                style = MaterialTheme.typography.bodySmall,
                color = color,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .background(color.copy(alpha = 0.14f), RoundedCornerShape(3.dp)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(stat.percent / 100f)
                    .fillMaxHeight()
                    .background(color, RoundedCornerShape(3.dp)),
            )
        }
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
