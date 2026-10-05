package com.algora.app.feature.interviewprep

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.algora.app.core.data.entitlement.EntitlementRepository
import com.algora.app.core.data.entitlement.PaidOnly
import com.algora.app.core.data.entitlement.entitlementDataStore
import com.algora.app.core.data.progress.ProgressRepository
import com.algora.app.core.data.progress.progressDataStore
import com.algora.app.core.data.settings.SettingsRepository
import com.algora.app.core.data.settings.settingsDataStore
import com.algora.app.core.ui.components.resolveIcon
import com.algora.app.core.ui.theme.ScreenBottomInset
import com.algora.app.core.ui.theme.ScreenGutter
import com.algora.app.core.ui.theme.SimColors
import com.algora.app.core.ui.theme.SpaceGrotesk
import com.algora.app.feature.practice.LargeTitleHeader
import com.algora.app.feature.practice.QuizModeToggle
import com.algora.app.feature.practice.QuizSetCard
import com.algora.app.feature.practice.quizSetItem
import com.algora.app.feature.practice.rememberQuizListLearnMode

// Interview Prep as a list of sets: overall progress, the Interview / Learn switch, then each category
// under its icon header as one card of sets that start straight away in the chosen mode. Guides that are
// not quizzes (behavioral bank, design primer) sit in the same cards and open as pages.
@Composable
fun InterviewPrepScreen(
    onTopicClick: (String) -> Unit,
    onStartQuiz: (String, Boolean) -> Unit,
    onBack: () -> Unit,
    backTitle: String = "Back",
) {
    val context = LocalContext.current
    val repository = remember { ProgressRepository(context.progressDataStore) }
    val completedIds by repository.completedTopicIds.collectAsState(initial = emptySet())
    val settings = remember { SettingsRepository(context.settingsDataStore) }
    val attempts by settings.quizAttempts.collectAsState(initial = emptyMap())
    val entitlements = remember { EntitlementRepository(context.entitlementDataStore) }
    val isPremium by entitlements.isPremium.collectAsState(initial = false)
    val adUnlocks by entitlements.adUnlocks.collectAsState(initial = emptyMap())
    var learn by rememberQuizListLearnMode()

    // Patterns is deliberately absent: it has its own Home Quick Access card and PatternsScreen, and
    // listing it here too would give the same category two entry points on the same surface.
    val sections = remember {
        InterviewPrepCategories.all
            .filter { it.id != InterviewPrepCategories.patterns.id }
            .map { category -> category to InterviewPrepTopics.topics.filter { it.categoryId == category.id } }
            .filter { it.second.isNotEmpty() }
    }
    val all = sections.flatMap { it.second }
    val done = all.count { it.id in completedIds }
    val progress = if (all.isEmpty()) 0f else done.toFloat() / all.size
    val primary = MaterialTheme.colorScheme.primary

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = ScreenGutter, end = ScreenGutter, top = 8.dp, bottom = ScreenBottomInset),
    ) {
        item { LargeTitleHeader("Interview Prep", backTitle, onBack) }
        item {
            Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Text("$done of ${all.size} completed", fontSize = 17.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                Text("${(progress * 100).toInt()}%", fontSize = 17.sp, color = primary)
            }
            Box(
                modifier = Modifier.padding(top = 8.dp, bottom = 20.dp).fillMaxWidth().height(5.dp).background(SimColors.Tint, CircleShape),
            ) {
                if (progress > 0f) Box(Modifier.fillMaxWidth(progress.coerceAtLeast(0.02f)).fillMaxHeight().background(primary, CircleShape))
            }
        }
        item { QuizModeToggle(learn, { learn = it }, Modifier.padding(bottom = 8.dp)) }
        sections.forEach { (category, topics) ->
            item(key = category.id) {
                val accent = Color(category.accentColor)
                Row(
                    modifier = Modifier.padding(top = 20.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(Brush.linearGradient(listOf(accent, accent.copy(alpha = 0.7f))), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(resolveIcon(category.iconName), contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                    Text(category.name, fontFamily = SpaceGrotesk, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                }
                QuizSetCard(
                    items = topics.map { topic ->
                        quizSetItem(
                            topic, completedIds, attempts,
                            locked = topic.isPremium && !isPremium && topic.id !in adUnlocks,
                            adUnlockable = !PaidOnly.isPaidOnlyTopic(topic.id),
                        )
                    },
                    learn = learn,
                    onOpen = onTopicClick,
                    onStart = onStartQuiz,
                )
            }
        }
        item { Spacer(Modifier.height(4.dp)) }
    }
}
