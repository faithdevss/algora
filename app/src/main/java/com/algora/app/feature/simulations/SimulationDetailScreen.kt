package com.algora.app.feature.simulations

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.algora.app.core.data.TopicRegistry
import com.algora.app.core.data.entitlement.EntitlementRepository
import com.algora.app.core.data.entitlement.TopicAccess
import com.algora.app.core.data.entitlement.entitlementDataStore
import com.algora.app.core.data.settings.SettingsRepository
import com.algora.app.core.data.settings.settingsDataStore
import com.algora.app.core.ui.theme.ScreenGutter
import com.algora.app.core.ui.theme.SpaceGrotesk
import com.algora.app.feature.premium.LockedTopicBody
import com.algora.app.feature.topics.DetailHeader
import com.algora.app.feature.topics.SimulationHost
import com.algora.app.feature.topics.content.TopicContentProvider
import kotlinx.coroutines.launch

// Tapping a Simulations catalog row lands here, not on the topic's 7-section detail page: the tab
// promises "tap to run", so the lab is the whole screen. The full write-up stays one tap away via the
// footer button. Paywall gating mirrors TopicDetailScreen — a premium topic's lab is premium too.
@Composable
fun SimulationDetailScreen(
    topicId: String,
    onBack: () -> Unit,
    onOpenTopic: (String) -> Unit,
    onGoPremium: () -> Unit = {},
) {
    val topic = remember(topicId) { TopicRegistry.find(topicId) }
    val content = remember(topicId) { TopicContentProvider.get(topicId) }
    val context = LocalContext.current
    val entitlements = remember { EntitlementRepository(context.entitlementDataStore) }
    val access by entitlements
        .accessFor(topicId, topic?.isPremium == true)
        .collectAsState(initial = null)

    // One frame of nothing while DataStore answers — better than flashing paid content.
    val resolved = access ?: return

    if (topic == null || content == null) {
        Column(modifier = Modifier.fillMaxSize()) {
            DetailHeader(title = topic?.name ?: "Simulation", onBack = onBack)
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Simulation not available yet.", style = MaterialTheme.typography.bodyLarge)
            }
        }
        return
    }

    if (resolved is TopicAccess.Locked) {
        Column(modifier = Modifier.fillMaxSize()) {
            DetailHeader(title = topic.name, onBack = onBack)
            LockedTopicBody(topic = topic, onGoPremium = onGoPremium)
        }
        return
    }

    val settings = remember { SettingsRepository(context.settingsDataStore) }
    val bookmarks by settings.bookmarks.collectAsState(initial = emptySet())
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize()) {
        DetailHeader(
            title = topic.name,
            onBack = onBack,
            isBookmarked = topicId in bookmarks,
            onToggleBookmark = { scope.launch { settings.toggleBookmark(topicId) } },
        )

        // LazyColumn, not verticalScroll: the labs are written against the topic page's item slot and
        // several of them scroll or pan internally.
        LazyColumn(modifier = Modifier.weight(1f)) {
            item {
                Text(
                    simLabel(content.simulation),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = ScreenGutter, end = ScreenGutter, top = 12.dp, bottom = 10.dp),
                )
            }
            item {
                Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                    SimulationHost(topicId = topicId, type = content.simulation)
                }
            }
            item {
                ReadFullTopicButton(
                    accent = Color(topic.accentColor),
                    onClick = { onOpenTopic(topicId) },
                )
            }
        }
    }
}

@Composable
private fun ReadFullTopicButton(accent: Color, onClick: () -> Unit) {
    Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 20.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(accent.copy(alpha = 0.12f), RoundedCornerShape(15.dp))
                .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(15.dp))
                .clickable(onClick = onClick)
                .padding(16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Read full topic",
                color = accent,
                fontFamily = SpaceGrotesk,
                fontWeight = FontWeight.Bold,
                fontSize = 15.5.sp,
            )
            Icon(
                Icons.Filled.ArrowForward,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.padding(start = 8.dp).size(18.dp),
            )
        }
    }
}
