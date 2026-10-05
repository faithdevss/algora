package com.algora.app.feature.practice

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.algora.app.core.data.TopicRegistry
import com.algora.app.core.data.entitlement.EntitlementRepository
import com.algora.app.core.data.entitlement.PaidOnly
import com.algora.app.core.data.entitlement.entitlementDataStore
import com.algora.app.core.data.progress.ProgressRepository
import com.algora.app.core.data.progress.progressDataStore
import com.algora.app.core.data.settings.SettingsRepository
import com.algora.app.core.data.settings.settingsDataStore
import com.algora.app.core.ui.theme.ScreenBottomInset
import com.algora.app.core.ui.theme.ScreenGutter
import com.algora.app.feature.interviewprep.quiz.QuizRegistry
import com.algora.app.feature.practice.weakspots.WeakSpotCard
import com.algora.app.feature.practice.weakspots.effectiveResults
import com.algora.app.feature.practice.weakspots.tagStats
import com.algora.app.feature.practice.weakspots.weakestTags

// Every quiz set in one list, under the weak-spots card and the Interview / Learn switch. A set's play
// button starts it in that mode through its topic page, so premium gating stays in one place.
@Composable
fun QuizCatalogScreen(
    onQuizClick: (String) -> Unit,
    onStartQuiz: (String, Boolean) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    backTitle: String = "Practice",
    onWeakSpotDrill: () -> Unit = {},
) {
    val entries = remember { QuizRegistry.all }
    val context = LocalContext.current
    val settings = remember { SettingsRepository(context.settingsDataStore) }
    val attempts by settings.quizAttempts.collectAsState(initial = emptyMap())
    val storedResults by settings.questionResults.collectAsState(initial = emptyMap())
    val entitlements = remember { EntitlementRepository(context.entitlementDataStore) }
    val isPremium by entitlements.isPremium.collectAsState(initial = false)
    val adUnlocks by entitlements.adUnlocks.collectAsState(initial = emptyMap())
    val progress = remember { ProgressRepository(context.progressDataStore) }
    val completed by progress.completedTopicIds.collectAsState(initial = emptySet())
    // Weak spots lead the catalog: before choosing a set, the learner sees where practice would help most.
    val results = remember(storedResults, attempts) { effectiveResults(storedResults, entries, attempts) }
    val weakest = remember(results) { weakestTags(tagStats(results, entries)) }
    var learn by rememberQuizListLearnMode()
    val items = entries.mapNotNull { (topicId, _) ->
        TopicRegistry.find(topicId)?.let { topic ->
            quizSetItem(
                topic, completed, attempts,
                locked = topic.isPremium && !isPremium && topicId !in adUnlocks,
                adUnlockable = !PaidOnly.isPaidOnlyTopic(topicId),
            )
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = ScreenGutter, end = ScreenGutter, top = 8.dp, bottom = ScreenBottomInset),
    ) {
        item { CompactNavBar("Quizzes", backTitle, onBack, Modifier.padding(bottom = 8.dp)) }
        item {
            WeakSpotCard(weakest = weakest, answeredTotal = results.size, onDrill = onWeakSpotDrill, modifier = Modifier.padding(bottom = 14.dp))
        }
        item { QuizModeToggle(learn, { learn = it }, Modifier.padding(bottom = 20.dp)) }
        item {
            Text(
                "${items.size} SETS", fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp, bottom = 10.dp),
            )
        }
        item { QuizSetCard(items, learn, onOpen = onQuizClick, onStart = onStartQuiz) }
    }
}
