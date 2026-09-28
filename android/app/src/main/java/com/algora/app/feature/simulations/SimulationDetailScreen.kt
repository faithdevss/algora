package com.algora.app.feature.simulations

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.algora.app.core.data.CategoryRegistry
import com.algora.app.core.data.TopicRegistry
import com.algora.app.core.data.entitlement.EntitlementRepository
import com.algora.app.core.data.entitlement.TopicAccess
import com.algora.app.core.data.entitlement.entitlementDataStore
import com.algora.app.core.data.settings.SettingsRepository
import com.algora.app.core.data.settings.settingsDataStore
import com.algora.app.feature.premium.LockedTopicBody
import com.algora.app.feature.topics.DetailHeader
import com.algora.app.feature.topics.LabDock
import com.algora.app.feature.topics.LabDockBar
import com.algora.app.feature.topics.LabNavAction
import com.algora.app.feature.topics.LocalLabDock
import com.algora.app.feature.topics.SimulationHost
import com.algora.app.feature.topics.content.TopicContentProvider
import kotlinx.coroutines.launch

// Tapping a Simulations catalog row lands here, not on the topic's 7-section detail page: the tab
// promises "tap to run", so the lab is the whole screen. The full write-up stays one tap away via the
// (i) sheet. Paywall gating mirrors TopicDetailScreen — a premium topic's lab is premium too.
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

    val dock = remember(topicId) { LabDock() }
    var showInfo by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        LabNavBar(
            title = topic.name,
            back = backLabel(CategoryRegistry.find(topic.categoryId)?.name),
            isBookmarked = topicId in bookmarks,
            action = dock.navAction,
            onBack = onBack,
            onInfo = { showInfo = true },
            onToggleBookmark = { scope.launch { settings.toggleBookmark(topicId) } },
        )

        // LazyColumn, not verticalScroll: the labs are written against the topic page's item slot and
        // several of them scroll or pan internally. The lab hands its transport up through the dock,
        // which pins it below in thumb reach; its intro goes behind (i).
        CompositionLocalProvider(LocalLabDock provides dock) {
            LazyColumn(modifier = Modifier.weight(1f)) {
                item {
                    Box(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 20.dp)) {
                        SimulationHost(topicId = topicId, type = content.simulation)
                    }
                }
            }
        }
        LabDockBar(dock)
    }

    if (showInfo) {
        LabInfoSheet(
            title = simLabel(content.simulation),
            intro = dock.intro,
            onDismiss = { showInfo = false },
            onReadTopic = { showInfo = false; onOpenTopic(topicId) },
        )
    }
}

/** "13 · Misc Algorithms" → "Misc Algorithms". */
private fun backLabel(category: String?): String {
    category ?: return "Back"
    val at = category.indexOf(" · ")
    return if (at > 0 && category.substring(0, at).all { it.isDigit() }) category.substring(at + 3) else category
}

/** "‹ Category" back, centred title, (i) and bookmark in the accent. */
@Composable
private fun LabNavBar(
    title: String,
    back: String,
    isBookmarked: Boolean,
    action: LabNavAction?,
    onBack: () -> Unit,
    onInfo: () -> Unit,
    onToggleBookmark: () -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 8.dp)
            .height(48.dp),
    ) {
        Text(
            title,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.Center).padding(horizontal = 112.dp),
        )
        Row(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .widthIn(max = 112.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClickLabel = "Back to $back", onClick = onBack)
                .padding(vertical = 8.dp, horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBackIos, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
            Text(back, color = accent, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Row(modifier = Modifier.align(Alignment.CenterEnd)) {
            if (action != null) {
                if (action.icon != null) {
                    IconButton(onClick = action.onClick) {
                        Icon(action.icon, contentDescription = action.label, tint = accent)
                    }
                } else {
                    Text(
                        action.label,
                        color = accent,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = action.onClick)
                            .padding(horizontal = 10.dp, vertical = 10.dp),
                    )
                }
            }
            IconButton(onClick = onInfo) {
                Icon(Icons.Outlined.Info, contentDescription = "About this lab", tint = accent)
            }
            if (action == null) {
                IconButton(onClick = onToggleBookmark) {
                    Icon(
                        if (isBookmarked) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                        contentDescription = if (isBookmarked) "Remove bookmark" else "Bookmark",
                        tint = accent,
                    )
                }
            }
        }
    }
}

/** Behind (i): what the lab shows, then the way into the full topic. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LabInfoSheet(title: String, intro: String?, onDismiss: () -> Unit, onReadTopic: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 24.dp)) {
            Text("About this lab", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.align(Alignment.CenterHorizontally))
            Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 16.dp))
            if (intro != null) {
                Text(
                    intro,
                    fontSize = 16.sp,
                    lineHeight = 23.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
            Row(
                modifier = Modifier
                    .padding(top = 24.dp)
                    .fillMaxWidth()
                    .height(50.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable(onClick = onReadTopic),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Read full topic", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.padding(start = 8.dp).size(18.dp),
                )
            }
        }
    }
}
