package com.algora.app.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.algora.app.core.data.model.Category
import com.algora.app.core.data.model.Topic

data class BrowserSection(val category: Category, val topics: List<Topic>)

// Shared shape for all four DSA-mode tab screens: title -> overall progress -> search ->
// icon-badged sections of rows. Mirrors docs/design/Algora.dc.html's isBrowser block, which has
// ONE progress bar + ONE search field for the whole screen (not per-section).
@Composable
fun CategoryBrowserScreen(
    screenTitle: String,
    sections: List<BrowserSection>,
    completedIds: Set<String>,
    onTopicClick: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by remember { mutableStateOf("") }
    val allTopics = remember(sections) { sections.flatMap { it.topics } }
    val completedCount = allTopics.count { it.id in completedIds }
    val progress = if (allTopics.isEmpty()) 0f else completedCount / allTopics.size.toFloat()

    LazyColumn(modifier = modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack, modifier = Modifier.offset(x = (-12).dp)) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Text(screenTitle, style = MaterialTheme.typography.headlineMedium)
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "$completedCount of ${allTopics.size} completed",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "${(progress * 100).toInt()}%",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                CategoryProgressBar(progress = progress, modifier = Modifier.padding(bottom = 14.dp))
                CategorySearchField(
                    query = query,
                    onQueryChange = { query = it },
                    placeholder = "Search ${screenTitle.lowercase()}…",
                )
            }
        }

        sections.forEach { section ->
            val filteredTopics = section.topics.filter { it.name.contains(query, ignoreCase = true) }
            if (filteredTopics.isNotEmpty()) {
                item {
                    SectionHeader(
                        title = section.category.name,
                        iconName = section.category.iconName,
                        accentColor = section.category.accentColor,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 12.dp),
                    )
                }
                items(filteredTopics, key = { it.id }) { topic ->
                    TopicRow(
                        title = topic.name,
                        isCompleted = topic.id in completedIds,
                        isPremium = topic.isPremium,
                        onClick = { onTopicClick(topic.id) },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        difficulty = topic.difficulty,
                    )
                }
            }
        }
    }
}
