package com.algora.app.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.algora.app.core.data.TopicRegistry
import com.algora.app.core.data.settings.SettingsRepository
import com.algora.app.core.data.settings.settingsDataStore
import com.algora.app.core.nav.AppMode
import com.algora.app.core.nav.FlashcardsRoute
import com.algora.app.core.nav.ReviewRoute
import com.algora.app.core.nav.Screen
import com.algora.app.core.nav.SettingsRoute
import com.algora.app.core.ui.components.resolveIcon
import com.algora.app.core.ui.theme.Gradients
import com.algora.app.core.ui.theme.SpaceGrotesk

private data class QuickCard(
    val route: String,
    val title: String,
    val sub: String,
    val iconName: String,
    val gradient: List<Color>,
)

private data class Featured(
    val topicId: String,
    val name: String,
    val sub: String,
    val iconName: String,
)

// Ported from docs/design/Algora.dc.html's isHome block: gradient topbar with the DSA / AI Simulation
// mode switch, a Featured Lab card, and a 2×2 Quick Access grid that deep-links into the active
// mode's four category tabs.
@Composable
fun HomeScreen(
    mode: AppMode,
    onModeChange: (AppMode) -> Unit,
    onNavigate: (String) -> Unit,
    onTopicClick: (String) -> Unit,
) {
    val context = LocalContext.current
    val settings = remember { SettingsRepository(context.settingsDataStore) }
    val streak by settings.streak.collectAsState(initial = 0)
    val bookmarks by settings.bookmarks.collectAsState(initial = emptySet())
    val lastOpened by settings.lastOpened.collectAsState(initial = null)

    val cards = if (mode == AppMode.DSA) dsaCards else aiCards
    val featured = if (mode == AppMode.DSA) dsaFeatured else aiFeatured
    val topbar = if (mode == AppMode.DSA) {
        Gradients.TopbarDsa
    } else {
        Gradients.TopbarAi
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp),
    ) {
        Spacer(modifier = Modifier.height(6.dp))

        // Topbar
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp)
                .background(Brush.linearGradient(topbar), RoundedCornerShape(22.dp))
                .padding(18.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Welcome back", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                    Text(
                        "Algora",
                        color = Color.White,
                        fontFamily = SpaceGrotesk,
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp,
                    )
                }
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .background(Color.White.copy(alpha = 0.16f), RoundedCornerShape(12.dp))
                        .clickable { onNavigate(SettingsRoute.ROUTE) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.Settings,
                        contentDescription = "Settings",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Spacer(modifier = Modifier.size(9.dp))
                Row(
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.18f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.LocalFireDepartment, contentDescription = null, tint = Color(0xFFFB923C), modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.size(5.dp))
                    Text(
                        "${streak.coerceAtLeast(1)} Day${if (streak > 1) "s" else ""}",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.2f), RoundedCornerShape(14.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                ModeTab("DSA", selected = mode == AppMode.DSA, modifier = Modifier.weight(1f)) { onModeChange(AppMode.DSA) }
                ModeTab("AI Simulation", selected = mode == AppMode.AI, modifier = Modifier.weight(1f)) { onModeChange(AppMode.AI) }
            }
        }

        // Featured lab
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 14.dp)
                .background(
                    Brush.linearGradient(
                        if (mode == AppMode.DSA) Gradients.FeaturedDsa
                        else Gradients.FeaturedAi,
                    ),
                    RoundedCornerShape(22.dp),
                )
                .clickable { onTopicClick(featured.topicId) }
                .padding(18.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "FEATURED LAB",
                        color = Color.White.copy(alpha = 0.75f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp,
                    )
                    Text(
                        featured.name,
                        color = Color.White,
                        fontFamily = SpaceGrotesk,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        modifier = Modifier.padding(top = 3.dp),
                    )
                    Text(featured.sub, color = Color.White.copy(alpha = 0.82f), fontSize = 13.sp)
                    Row(
                        modifier = Modifier
                            .padding(top = 13.dp)
                            .background(Color.White, RoundedCornerShape(11.dp))
                            .padding(horizontal = 15.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Open interactive", color = Color(0xFF161A22), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = Color(0xFF161A22), modifier = Modifier.size(18.dp))
                    }
                }
                Box(
                    modifier = Modifier
                        .size(74.dp)
                        .background(Color.White.copy(alpha = 0.16f), RoundedCornerShape(20.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(resolveIcon(featured.iconName), contentDescription = null, tint = Color.White, modifier = Modifier.size(36.dp))
                }
            }
        }

        val continueTopic = lastOpened?.let { TopicRegistry.find(it) }
        if (continueTopic != null) {
            SectionTitle("Jump Back In")
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onTopicClick(continueTopic.id) },
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(15.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(Color(continueTopic.accentColor).copy(alpha = 0.14f), RoundedCornerShape(13.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(resolveIcon(continueTopic.iconName), contentDescription = null, tint = Color(continueTopic.accentColor))
                    }
                    Spacer(modifier = Modifier.size(13.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(continueTopic.name, style = MaterialTheme.typography.titleMedium)
                        Text(continueTopic.tagline, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        Text(
            "Quick Access",
            fontFamily = SpaceGrotesk,
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            modifier = Modifier.padding(top = 22.dp, bottom = 14.dp),
        )

        // 2×2 grid
        Column(verticalArrangement = Arrangement.spacedBy(13.dp)) {
            cards.chunked(2).forEach { rowCards ->
                Row(horizontalArrangement = Arrangement.spacedBy(13.dp), modifier = Modifier.fillMaxWidth()) {
                    rowCards.forEach { card ->
                        QuickAccessCard(card, modifier = Modifier.weight(1f)) { onNavigate(card.route) }
                    }
                }
            }
        }

        // Flashcard review entry
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 15.dp)
                .clickable { onNavigate(FlashcardsRoute.ROUTE) },
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
        ) {
            Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Style, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.size(13.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Review Flashcards", style = MaterialTheme.typography.titleMedium)
                    Text("Auto-built from every topic's key takeaways", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
        }

        // Spaced-repetition review entry (Phase 7 SM-2 scheduler)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp)
                .clickable { onNavigate(ReviewRoute.ROUTE) },
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
        ) {
            Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.size(13.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Spaced Repetition", style = MaterialTheme.typography.titleMedium)
                    Text("Review due cards on an SM-2 schedule", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
        }

        val bookmarkedTopics = bookmarks.mapNotNull { TopicRegistry.find(it) }
        if (bookmarkedTopics.isNotEmpty()) {
            SectionTitle("Bookmarks")
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                bookmarkedTopics.forEach { t ->
                    Surface(
                        modifier = Modifier.fillMaxWidth().clickable { onTopicClick(t.id) },
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    ) {
                        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Bookmark, contentDescription = null, tint = Color(t.accentColor), modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.size(12.dp))
                            Text(t.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        fontFamily = SpaceGrotesk,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        modifier = Modifier.padding(top = 22.dp, bottom = 12.dp),
    )
}

@Composable
private fun ModeTab(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .background(if (selected) Color.White else Color.Transparent, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (selected) Color(0xFF161A22) else Color.White.copy(alpha = 0.85f),
            fontWeight = FontWeight.Bold,
            fontSize = 13.5.sp,
        )
    }
}

@Composable
private fun QuickAccessCard(card: QuickCard, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier = modifier
            .heightIn(min = 150.dp)
            .background(Brush.linearGradient(card.gradient), RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(Color.White.copy(alpha = 0.22f), RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(resolveIcon(card.iconName), contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
        }
        Spacer(modifier = Modifier.weight(1f))
        Text(
            card.title,
            color = Color.White,
            fontFamily = SpaceGrotesk,
            fontWeight = FontWeight.Bold,
            fontSize = 16.5.sp,
        )
        Text(card.sub, color = Color.White.copy(alpha = 0.82f), fontSize = 12.5.sp)
    }
}

private val dsaCards = listOf(
    QuickCard(Screen.DataStructures.route, "Data Structures", "Build your foundation", "stack", Gradients.Green),
    QuickCard(Screen.Algorithms.route, "Algorithms", "Master problem solving", "chip", Gradients.Blue),
    QuickCard(Screen.InterviewPrep.route, "Interview Prep", "Practice for interviews", "help", Gradients.Amber),
    QuickCard(Screen.Analysis.route, "Analysis", "Time & Space efficiency", "trend", Gradients.Violet),
)

private val aiCards = listOf(
    QuickCard(Screen.MachineLearning.route, "Machine Learning", "Learn from data", "robot", Gradients.Indigo),
    QuickCard(Screen.DeepLearning.route, "Deep Learning", "Neural networks", "network", Gradients.Pink),
    QuickCard(Screen.Nlp.route, "NLP", "Language & text", "globe", Gradients.Teal),
    QuickCard(Screen.ReinforcementLearning.route, "Reinforcement Learning", "Trial & error", "game", Gradients.Orange),
)

private val dsaFeatured = Featured("singly_linked_list", "Singly Linked List", "Chains of Data", "link")
private val aiFeatured = Featured("linear_regression", "Linear Regression", "Fitting a Line to Data", "trend")
