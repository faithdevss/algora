package com.algora.app.feature.nlp

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
fun NlpScreen(onTopicClick: (String) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val repository = remember { ProgressRepository(context.progressDataStore) }
    val completedIds by repository.completedTopicIds.collectAsState(initial = emptySet())

    val sections = remember {
        NlpCategories.all.map { category ->
            BrowserSection(category, NlpTopics.topics.filter { it.categoryId == category.id })
        }
    }

    CategoryBrowserScreen(
        screenTitle = "NLP",
        sections = sections,
        completedIds = completedIds,
        onTopicClick = onTopicClick,
        onBack = onBack,
    )
}
