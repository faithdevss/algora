package com.algora.app.feature.reinforcementlearning

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.algora.app.core.data.progress.ProgressRepository
import com.algora.app.core.data.progress.progressDataStore
import com.algora.app.core.ui.components.BrowserSection
import com.algora.app.core.ui.components.CategoryBrowserScreen

@Composable
fun ReinforcementLearningScreen(onTopicClick: (String) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val repository = remember { ProgressRepository(context.progressDataStore) }
    val completedIds by repository.completedTopicIds.collectAsState(initial = emptySet())

    val sections = remember {
        ReinforcementLearningCategories.all.map { category ->
            BrowserSection(category, ReinforcementLearningTopics.topics.filter { it.categoryId == category.id })
        }
    }

    CategoryBrowserScreen(
        screenTitle = "Reinforcement Learning",
        sections = sections,
        completedIds = completedIds,
        onTopicClick = onTopicClick,
        onBack = onBack,
    )
}
