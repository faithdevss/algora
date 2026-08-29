package com.algora.app.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.algora.app.core.data.entitlement.EntitlementRepository
import com.algora.app.core.data.entitlement.entitlementDataStore
import com.algora.app.core.data.model.Category
import com.algora.app.core.data.model.Topic
import com.algora.app.core.ui.theme.ScreenBottomInset
import com.algora.app.core.ui.theme.ScreenGutter

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
    // Entitlement is read here rather than threaded through all eight category screens: the lock
    // icon must disappear the moment premium is bought or an ad unlock is granted.
    val context = LocalContext.current
    val entitlements = remember { EntitlementRepository(context.entitlementDataStore) }
    val isPremium by entitlements.isPremium.collectAsState(initial = false)
    val adUnlocks by entitlements.adUnlocks.collectAsState(initial = emptyMap())

    var query by remember { mutableStateOf("") }
    val allTopics = remember(sections) { sections.flatMap { it.topics } }
    val completedCount = allTopics.count { it.id in completedIds }
    val progress = if (allTopics.isEmpty()) 0f else completedCount / allTopics.size.toFloat()

    Column(modifier = modifier.fillMaxSize()) {
        ScreenHeader(title = screenTitle, onBack = onBack)
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(bottom = ScreenBottomInset),
        ) {
            item {
                Column(modifier = Modifier.padding(horizontal = ScreenGutter, vertical = 8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
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
                            modifier = Modifier.padding(
                                start = ScreenGutter,
                                end = ScreenGutter,
                                top = 18.dp,
                                bottom = 12.dp,
                            ),
                        )
                    }
                    items(filteredTopics, key = { it.id }) { topic ->
                        TopicRow(
                            title = topic.name,
                            isCompleted = topic.id in completedIds,
                            isLocked = topic.isPremium && !isPremium && topic.id !in adUnlocks,
                            onClick = { onTopicClick(topic.id) },
                            modifier = Modifier.padding(horizontal = ScreenGutter, vertical = 4.dp),
                            difficulty = topic.difficulty,
                        )
                    }
                }
            }
        }
    }
}
