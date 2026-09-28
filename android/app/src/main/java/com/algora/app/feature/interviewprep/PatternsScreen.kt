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

// Home's DSA Quick Access "Patterns" card lands here: the Patterns category alone, on the same
// browser chrome as every other category screen. InterviewPrepScreen still shows all five
// categories — this one exists so the pattern guides are one tap from Home rather than three.
@Composable
fun PatternsScreen(onTopicClick: (String) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val repository = remember { ProgressRepository(context.progressDataStore) }
    val completedIds by repository.completedTopicIds.collectAsState(initial = emptySet())

    val sections = remember {
        val patterns = InterviewPrepCategories.patterns
        listOf(BrowserSection(patterns, InterviewPrepTopics.topics.filter { it.categoryId == patterns.id }))
    }

    CategoryBrowserScreen(
        screenTitle = "Patterns",
        sections = sections,
        completedIds = completedIds,
        onTopicClick = onTopicClick,
        onBack = onBack,
    )
}
