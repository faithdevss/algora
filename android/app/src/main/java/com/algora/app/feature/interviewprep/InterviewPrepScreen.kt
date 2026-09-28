package com.algora.app.feature.interviewprep

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
fun InterviewPrepScreen(onTopicClick: (String) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val repository = remember { ProgressRepository(context.progressDataStore) }
    val completedIds by repository.completedTopicIds.collectAsState(initial = emptySet())

    // Patterns is deliberately absent: it has its own Home Quick Access card and PatternsScreen, and
    // listing it here too would give the same category two entry points on the same surface.
    val sections = remember {
        InterviewPrepCategories.all
            .filter { it.id != InterviewPrepCategories.patterns.id }
            .map { category ->
                BrowserSection(category, InterviewPrepTopics.topics.filter { it.categoryId == category.id })
            }
    }

    CategoryBrowserScreen(
        screenTitle = "Interview Prep",
        sections = sections,
        completedIds = completedIds,
        onTopicClick = onTopicClick,
        onBack = onBack,
    )
}
