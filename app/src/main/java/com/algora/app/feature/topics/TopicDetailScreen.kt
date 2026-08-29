package com.algora.app.feature.topics

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.horizontalScroll
import com.algora.app.core.data.PrerequisiteGraph
import com.algora.app.core.data.TopicRegistry
import com.algora.app.core.ui.theme.LocalDarkTheme
import com.algora.app.core.ui.theme.ScreenGutter
import com.algora.app.core.ui.theme.SimColors
import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.Topic
import com.algora.app.core.data.model.TopicContent
import com.algora.app.core.data.entitlement.EntitlementRepository
import com.algora.app.core.data.entitlement.TopicAccess
import com.algora.app.core.data.entitlement.entitlementDataStore
import com.algora.app.core.data.progress.ProgressRepository
import com.algora.app.core.data.progress.progressDataStore
import com.algora.app.core.data.settings.SettingsRepository
import com.algora.app.core.data.settings.settingsDataStore
import com.algora.app.core.ui.components.DifficultyBadge
import com.algora.app.core.ui.components.ScreenHeader
import com.algora.app.core.ui.components.resolveIcon
import com.algora.app.core.ui.theme.AlgoraCodeStyle
import com.algora.app.core.ui.theme.SpaceGrotesk
import com.algora.app.feature.analysis.tools.AnalysisToolRegistry
import com.algora.app.feature.interviewprep.behavioral.BehavioralRegistry
import com.algora.app.feature.interviewprep.behavioral.BehavioralScreen
import com.algora.app.feature.interviewprep.systemdesign.SystemDesignRegistry
import com.algora.app.feature.interviewprep.systemdesign.SystemDesignScreen
import com.algora.app.feature.interviewprep.quiz.QuizRegistry
import com.algora.app.feature.interviewprep.quiz.QuizScreen
import com.algora.app.feature.premium.LockedTopicBody
import com.algora.app.feature.topics.content.TopicContentProvider
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

// Same amber as the lock icon in core/ui/components/TopicRow.kt.
private val RowLockAmber = Color(0xFFF59E0B)

// Paywall gate (Phase 8). Every route into topic content funnels through here, so premium topics
// stay closed no matter which branch below would have rendered them — quiz, behavioral bank, system
// design primer, analysis tool or the standard 7-section page.
@Composable
fun TopicDetailScreen(
    topicId: String,
    onBack: () -> Unit,
    onTopicClick: (String) -> Unit = {},
    onGoPremium: () -> Unit = {},
) {
    val topic = remember(topicId) { TopicRegistry.find(topicId) }
    val context = LocalContext.current
    val entitlements = remember { EntitlementRepository(context.entitlementDataStore) }
    val access by entitlements
        .accessFor(topicId, topic?.isPremium == true)
        .collectAsState(initial = null)

    // One frame of nothing while DataStore answers — better than flashing paid content.
    val resolved = access ?: return

    if (topic != null && resolved is TopicAccess.Locked) {
        Column(modifier = Modifier.fillMaxSize()) {
            DetailHeader(title = topic.name, onBack = onBack)
            LockedTopicBody(topic = topic, onGoPremium = onGoPremium)
        }
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (resolved is TopicAccess.AdUnlocked) {
            AdUnlockBanner(expiresAt = resolved.expiresAt)
        }
        TopicDetailContent(
            topicId = topicId,
            onBack = onBack,
            onTopicClick = onTopicClick,
            onGoPremium = onGoPremium,
        )
    }
}

// Reminder that this access came from a rewarded ad and will lapse.
@Composable
private fun AdUnlockBanner(expiresAt: Long) {
    // Weekday included: an unlock that spans midnight would otherwise read as already past.
    val formatter = remember { SimpleDateFormat("EEE h:mm a", Locale.getDefault()) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(RowLockAmber.copy(alpha = 0.13f))
            .padding(horizontal = ScreenGutter, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.LockOpen,
            contentDescription = null,
            tint = RowLockAmber,
            modifier = Modifier.size(15.dp),
        )
        Text(
            text = "Unlocked until ${formatter.format(Date(expiresAt))}",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

@Composable
private fun TopicDetailContent(
    topicId: String,
    onBack: () -> Unit,
    onTopicClick: (String) -> Unit,
    onGoPremium: () -> Unit,
) {
    val topic = remember(topicId) { TopicRegistry.find(topicId) }
    val content = remember(topicId) { TopicContentProvider.get(topicId) }

    val context = LocalContext.current
    val repository = remember { ProgressRepository(context.progressDataStore) }
    val completedIds by repository.completedTopicIds.collectAsState(initial = emptySet())
    val scope = rememberCoroutineScope()

    if (topic == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Content not available yet.", style = MaterialTheme.typography.bodyLarge)
        }
        return
    }

    val isCompleted = topicId in completedIds

    val settings = remember { SettingsRepository(context.settingsDataStore) }
    LaunchedEffect(topicId) { settings.setLastOpened(topicId) }
    val bookmarks by settings.bookmarks.collectAsState(initial = emptySet())
    val isBookmarked = topicId in bookmarks
    val onToggleBookmark: () -> Unit = { scope.launch { settings.toggleBookmark(topicId) } }

    val quiz = remember(topicId) { QuizRegistry.get(topicId) }
    if (quiz != null) {
        QuizScreen(
            quizId = topicId,
            quiz = quiz,
            onBack = onBack,
            onTopicClick = onTopicClick,
            onFinish = { scope.launch { repository.markCompleted(topicId) } },
            onGoPremium = onGoPremium,
        )
        return
    }

    val behavioral = remember(topicId) { BehavioralRegistry.get(topicId) }
    if (behavioral != null) {
        BehavioralScreen(
            bank = behavioral,
            onBack = onBack,
            onComplete = { scope.launch { repository.markCompleted(topicId) } },
        )
        return
    }

    val systemDesign = remember(topicId) { SystemDesignRegistry.get(topicId) }
    if (systemDesign != null) {
        SystemDesignScreen(
            primer = systemDesign,
            onBack = onBack,
            onComplete = { scope.launch { repository.markCompleted(topicId) } },
        )
        return
    }

    val toolContent = AnalysisToolRegistry.get(topicId)
    if (toolContent != null) {
        Column(modifier = Modifier.fillMaxSize()) {
            DetailHeader(title = topic.name, onBack = onBack, isBookmarked = isBookmarked, onToggleBookmark = onToggleBookmark)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            ) {
                toolContent()
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = isCompleted,
                        onCheckedChange = { checked ->
                            scope.launch {
                                if (checked) repository.markCompleted(topicId) else repository.markIncomplete(topicId)
                            }
                        },
                    )
                    Text("Mark as complete", style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
        return
    }

    if (content == null) {
        Column(modifier = Modifier.fillMaxSize()) {
            DetailHeader(title = topic.name, onBack = onBack, isBookmarked = isBookmarked, onToggleBookmark = onToggleBookmark)
            ComingSoonBody(topic = topic)
        }
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        DetailHeader(title = topic.name, onBack = onBack, isBookmarked = isBookmarked, onToggleBookmark = onToggleBookmark)

        LazyColumn(modifier = Modifier.weight(1f)) {
            item { HeroSection(topic, content) }
            item { SectionTitle("How Does It Work?") }
            content.figure?.let { figure ->
                item {
                    // bottom gap matches HowItWorksSection's 11.dp step spacing so the figure reads
                    // as the first card in that stack; the title already supplies the gap above.
                    Box(modifier = Modifier.padding(start = ScreenGutter, end = ScreenGutter, bottom = 11.dp)) {
                        FigureCard(figure)
                    }
                }
            }
            item { HowItWorksSection(content.steps) }
            item { SectionTitle("The Mathematics") }
            item { MathSection(content.formulas, content.notationKey) }
            item { SectionTitle("Technical Deep Dive") }
            items(content.codeBlocks.withIndex().toList()) { (index, block) ->
                Box(modifier = Modifier.padding(horizontal = ScreenGutter, vertical = 6.dp)) {
                    CodeBlockCard(block = block, initiallyExpanded = index == 0)
                }
            }
            item { SectionTitle("Interactive Simulation") }
            item {
                Box(modifier = Modifier.padding(horizontal = ScreenGutter)) {
                    SimulationHost(topicId = topicId, type = content.simulation)
                }
            }
            item { SectionTitle("Real-World Applications") }
            item { ApplicationsSection(content.applications) }
            item { TakeawaysSection(content.takeaways) }
            item { PrerequisitesSection(topicId, onTopicClick) }
            if (content.crossLinks.isNotEmpty()) {
                item { RelatedTopicsSection(content.crossLinks, onTopicClick) }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = isCompleted,
                        onCheckedChange = { checked ->
                            scope.launch {
                                if (checked) repository.markCompleted(topicId) else repository.markIncomplete(topicId)
                            }
                        },
                    )
                    Text("Mark as complete", style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

// Shared with the sim-only screen (feature/simulations) so both routes wear the same chrome.
@Composable
internal fun DetailHeader(
    title: String,
    onBack: () -> Unit,
    isBookmarked: Boolean? = null,
    onToggleBookmark: () -> Unit = {},
) {
    ScreenHeader(
        title = title,
        onBack = onBack,
        trailing = isBookmarked?.let {
            {
                IconButton(onClick = onToggleBookmark, modifier = Modifier.size(36.dp)) {
                    Icon(
                        if (it) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                        contentDescription = if (it) "Remove bookmark" else "Bookmark",
                        tint = if (it) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
    )
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.padding(start = ScreenGutter, end = ScreenGutter, top = 22.dp, bottom = 12.dp),
    )
}

// Shown for the ~70 topics that only have row metadata so far — full 7-section content
// authoring is a follow-up session (see docs/plan/phase-2-topic-browser-content.md).
@Composable
private fun ComingSoonBody(topic: Topic) {
    val accent = Color(topic.accentColor)
    val isDark = LocalDarkTheme.current

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = accent.copy(alpha = if (isDark) 0.14f else 0.10f),
            border = BorderStroke(1.dp, accent.copy(alpha = 0.22f)),
        ) {
            Row(modifier = Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .shadow(elevation = 8.dp, shape = RoundedCornerShape(16.dp), ambientColor = accent, spotColor = accent)
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(resolveIcon(topic.iconName), contentDescription = null, tint = accent)
                }
                Spacer(modifier = Modifier.size(14.dp))
                Column {
                    Text(topic.name, style = MaterialTheme.typography.titleLarge)
                    Text(topic.tagline, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Spacer(modifier = Modifier.size(22.dp))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) {
            Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "Full content coming soon",
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.size(6.dp))
                Text(
                    "This topic is in the taxonomy but its deep-dive content hasn't been authored yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

// Learning-path context (Phase 7): what to learn before this topic, and what it unlocks. Both are
// tappable chips that navigate. Renders nothing if the topic isn't in the curated PrerequisiteGraph.
@Composable
private fun PrerequisitesSection(topicId: String, onTopicClick: (String) -> Unit) {
    val prereqs = PrerequisiteGraph.prereqsOf(topicId).mapNotNull { TopicRegistry.find(it) }
    val unlocks = PrerequisiteGraph.unlockedBy(topicId).mapNotNull { TopicRegistry.find(it) }
    if (prereqs.isEmpty() && unlocks.isEmpty()) return

    Column(modifier = Modifier.padding(horizontal = ScreenGutter, vertical = 4.dp)) {
        Text("Learning Path", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 12.dp))
        if (prereqs.isNotEmpty()) {
            PathRow(label = "Learn first", topics = prereqs, accent = MaterialTheme.colorScheme.primary, onTopicClick = onTopicClick)
        }
        if (unlocks.isNotEmpty()) {
            PathRow(label = "Unlocks", topics = unlocks, accent = SimColors.Green, onTopicClick = onTopicClick, topPad = if (prereqs.isNotEmpty()) 10.dp else 0.dp)
        }
    }
}

@Composable
private fun PathRow(
    label: String,
    topics: List<Topic>,
    accent: Color,
    onTopicClick: (String) -> Unit,
    topPad: androidx.compose.ui.unit.Dp = 0.dp,
) {
    Column(modifier = Modifier.padding(top = topPad)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 6.dp))
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            topics.forEach { t ->
                Surface(
                    modifier = Modifier.clickable { onTopicClick(t.id) },
                    shape = RoundedCornerShape(10.dp),
                    color = accent.copy(alpha = 0.10f),
                    border = BorderStroke(1.dp, accent.copy(alpha = 0.35f)),
                ) {
                    Text(
                        t.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = accent,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }
}

// Cross-links to related topics, possibly in the other app mode (Phase 5 DSA ↔ AI bridges).
@Composable
private fun RelatedTopicsSection(links: List<CrossLink>, onTopicClick: (String) -> Unit) {
    Column(modifier = Modifier.padding(horizontal = ScreenGutter)) {
        Text("Related Topics", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            links.forEach { link ->
                Surface(
                    modifier = Modifier.fillMaxWidth().clickable { onTopicClick(link.topicId) },
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(link.label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroSection(topic: Topic, content: TopicContent) {
    val accent = Color(topic.accentColor)
    val isDark = LocalDarkTheme.current

    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = ScreenGutter),
        shape = RoundedCornerShape(20.dp),
        color = accent.copy(alpha = if (isDark) 0.14f else 0.10f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.22f)),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .shadow(elevation = 8.dp, shape = RoundedCornerShape(16.dp), ambientColor = accent, spotColor = accent)
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(resolveIcon(topic.iconName), contentDescription = null, tint = accent)
                }
                Spacer(modifier = Modifier.size(14.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // weight(fill = false) lets the badge claim its intrinsic width first, so a long
                        // title wraps instead of squeezing the pill down to one letter per line.
                        Text(
                            text = topic.name,
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        topic.difficulty?.let {
                            Spacer(modifier = Modifier.size(8.dp))
                            DifficultyBadge(it)
                        }
                    }
                    Text(topic.tagline, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(modifier = Modifier.size(14.dp))
            Text("What is ${topic.name}?", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.size(8.dp))
            content.whatIsIt.forEach { paragraph ->
                Text(
                    text = paragraph,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(bottom = 10.dp),
                )
            }
        }
    }
}

@Composable
private fun HowItWorksSection(steps: List<StepCard>) {
    Column(
        modifier = Modifier.padding(horizontal = ScreenGutter),
        verticalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        steps.forEach { step ->
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            ) {
                Row(modifier = Modifier.padding(15.dp)) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(Color(step.accentColor).copy(alpha = 0.16f), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            step.number.toString(),
                            color = Color(step.accentColor),
                            fontFamily = SpaceGrotesk,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                        )
                    }
                    Spacer(modifier = Modifier.size(13.dp))
                    Column {
                        Text(step.title, style = MaterialTheme.typography.titleMedium)
                        Text(step.body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun MathSection(formulas: List<FormulaEntry>, notationKey: List<NotationEntry>) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = ScreenGutter),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(modifier = Modifier.padding(horizontal = ScreenGutter, vertical = 6.dp)) {
            formulas.forEachIndexed { index, formula ->
                Column(modifier = Modifier.padding(vertical = 12.dp)) {
                    // The mock's maths row is one flex line, but it only ever carried formulas as
                    // short as "O(n)". Real entries run to a phrase ("emoji -1, lowercase -2, ..."),
                    // and a plain Row squeezes the label to nothing and rag-wraps the formula against
                    // it. FlowRow keeps the mock's two-column line while the pair fits and drops the
                    // formula onto a full-width line of its own when it does not. The label's end
                    // padding is the mock's 12px gap, counted into its width so the pair never touches.
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Text(
                            text = formula.label,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(end = 12.dp),
                        )
                        Text(formula.formula, style = AlgoraCodeStyle, color = MaterialTheme.colorScheme.primary)
                    }
                    Text(
                        formula.note,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                if (index < formulas.lastIndex) HorizontalDivider()
            }
            Spacer(modifier = Modifier.size(8.dp))
            Text("Notation Key", style = MaterialTheme.typography.titleMedium)
            notationKey.forEach { entry ->
                Row(modifier = Modifier.padding(vertical = 4.dp)) {
                    Text(entry.symbol, style = AlgoraCodeStyle, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.size(8.dp))
                    // weight so a long meaning wraps under itself rather than pushing the symbol out.
                    Text(entry.meaning, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ApplicationsSection(applications: List<ApplicationCard>) {
    Column(
        modifier = Modifier.padding(horizontal = ScreenGutter),
        verticalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        applications.forEach { app ->
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            ) {
                Row(modifier = Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(Color(app.accentColor).copy(alpha = 0.14f), RoundedCornerShape(13.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(resolveIcon(app.iconName), contentDescription = null, tint = Color(app.accentColor))
                    }
                    Spacer(modifier = Modifier.size(13.dp))
                    Column {
                        Text(app.title, style = MaterialTheme.typography.titleMedium)
                        Text(app.body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

private val TakeawayGreen = Color(0xFF16A34A)
private val TakeawayGreenBgLight = Color(0xFFE9F9EE)

@Composable
private fun TakeawaysSection(takeaways: List<String>) {
    val isDark = LocalDarkTheme.current
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = ScreenGutter, vertical = 22.dp),
        shape = RoundedCornerShape(20.dp),
        color = if (isDark) TakeawayGreen.copy(alpha = 0.12f) else TakeawayGreenBgLight,
        border = BorderStroke(1.dp, TakeawayGreen.copy(alpha = 0.3f)),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Lightbulb, contentDescription = null, tint = TakeawayGreen)
                Spacer(modifier = Modifier.size(10.dp))
                Text(
                    "Key Takeaways",
                    style = MaterialTheme.typography.titleLarge,
                    fontFamily = SpaceGrotesk,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(modifier = Modifier.size(14.dp))
            takeaways.forEach { takeaway ->
                Row(modifier = Modifier.padding(vertical = 5.dp)) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = TakeawayGreen,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(modifier = Modifier.size(10.dp))
                    Text(takeaway, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}
