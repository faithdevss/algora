package com.algora.app.feature.simulations

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.algora.app.core.data.CategoryRegistry
import com.algora.app.core.data.TopicRegistry
import com.algora.app.core.data.entitlement.EntitlementRepository
import com.algora.app.core.data.entitlement.entitlementDataStore
import com.algora.app.core.data.model.Section
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.Topic
import com.algora.app.core.ui.components.AccordionHeader
import com.algora.app.core.ui.components.AdUnlockableLockIcon
import com.algora.app.core.ui.components.CategorySearchField
import com.algora.app.core.ui.components.LockAmber
import com.algora.app.core.ui.components.resolveIcon
import com.algora.app.core.ui.theme.ScreenBottomInset
import com.algora.app.core.ui.theme.ScreenGutter
import com.algora.app.core.ui.theme.SpaceGrotesk
import com.algora.app.feature.topics.content.TopicContentProvider

private data class SimEntry(val topic: Topic, val label: String)

private data class SimSubgroup(val key: String, val name: String, val entries: List<SimEntry>)

// One collapsible block in the catalog: a whole section (Data Structures, NLP, …) whose rows are
// split again by the topic's own category. Two levels, because the flat catalog is 350+ labs and the
// biggest single section (Algorithms) is ~90 of them.
private data class SimGroup(
    val section: Section,
    val title: String,
    val iconName: String,
    val accentColor: Long,
    val subgroups: List<SimSubgroup>,
) {
    val count: Int get() = subgroups.sumOf { it.entries.size }
}

// Section chrome mirrors the mock's quick-cards (docs/design/Algora.dc.html): same label, icon and
// accent each section carries on Home, so the catalog reads as the same taxonomy.
private data class SectionMeta(val title: String, val iconName: String, val accentColor: Long)

private val sectionMeta = linkedMapOf(
    Section.DATA_STRUCTURES to SectionMeta("Data Structures", "stack", 0xFF10B981),
    Section.ALGORITHMS to SectionMeta("Algorithms", "chip", 0xFF3B82F6),
    Section.ANALYSIS to SectionMeta("Analysis", "trend", 0xFF8B5CF6),
    Section.INTERVIEW_PREP to SectionMeta("Interview Prep", "target", 0xFFF59E0B),
    Section.ML to SectionMeta("Machine Learning", "robot", 0xFF6366F1),
    Section.DL to SectionMeta("Deep Learning", "network", 0xFFEC4899),
    Section.NLP to SectionMeta("NLP", "globe", 0xFF14B8A6),
    Section.RL to SectionMeta("Reinforcement Learning", "game", 0xFFF59E0B),
)

internal fun simLabel(type: SimulationType): String = when (type) {
    SimulationType.ArrayVisualizer -> "Array visualizer"
    SimulationType.LinkedListVisualizer -> "Linked-list visualizer"
    SimulationType.StackVisualizer -> "Stack visualizer"
    SimulationType.QueueVisualizer -> "Queue visualizer"
    SimulationType.GraphVisualizer -> "Graph builder · BFS/DFS"
    SimulationType.GraphAlgorithmPlayer -> "Graph algorithm player"
    SimulationType.ArrayWalkPlayer -> "Array walk · pointer player"
    SimulationType.PointCloudPlayer -> "2D feature space player"
    SimulationType.TokenStripPlayer -> "Token strip · attention"
    SimulationType.NeuralNetPlayer -> "Network · training player"
    SimulationType.RlTrainingPlayer -> "RL training · measured runs"
    SimulationType.PolicyGradientPlayer -> "Policy gradient · variance"
    SimulationType.GameSearchPlayer -> "Search · planning player"
    SimulationType.OfflineRlPlayer -> "Offline · imitation player"
    SimulationType.MultiAgentPlayer -> "Multi-agent player"
    SimulationType.ExplorationPlayer -> "Exploration · meta-RL"
    SimulationType.LinkedStructurePlayer -> "Linked structure player"
    SimulationType.EnvironmentPlayer -> "Environment player"
    SimulationType.RegressionExplorer -> "Regression explorer"
    SimulationType.RegressionLab -> "Regression lab · estimators"
    SimulationType.DecisionSurface -> "Decision surface · boundaries"
    SimulationType.FeatureMapPlayer -> "Feature map · conv player"
    SimulationType.BitBoardPlayer -> "Bit board · per-bit player"
    SimulationType.PerceptronVisualizer -> "Perceptron playground"
    SimulationType.ClassifierPlayground -> "Classifier playground"
    SimulationType.RecursionTreeVisualizer -> "Recursion tree · call stack"
    SimulationType.DpGridVisualizer -> "DP table · animated fill"
    SimulationType.SortingVisualizer -> "Sorting visualizer"
    SimulationType.SearchVisualizer -> "Search visualizer"
    SimulationType.TreeVisualizer -> "Tree visualizer"
    SimulationType.PathfindingGrid -> "Pathfinding grid"
    SimulationType.HashingVisualizer -> "Hashing · bucket visualizer"
    SimulationType.RlGridWorld -> "Grid world · RL agent"
    SimulationType.BanditExplorer -> "Multi-armed bandit"
    SimulationType.NotYetAvailable -> "Interactive lab"
}

// The Simulations tab: a catalog of every topic that ships a runnable interactive lab, grouped into
// collapsible sections (and category subheaders inside them) because the flat list is 150+ rows.
// Tapping a row opens that topic's detail page, whose Interactive Simulation section hosts the lab.
@Composable
fun SimulationsScreen(onTopicClick: (String) -> Unit, modifier: Modifier = Modifier) {
    val groups = remember { buildGroups() }
    val total = remember(groups) { groups.sumOf { it.count } }

    // A premium topic's lab is premium too (SimulationDetailScreen enforces it) — the catalog row
    // has to say so up front, and the padlock must vanish the moment premium or an ad unlock lands.
    val context = LocalContext.current
    val entitlements = remember { EntitlementRepository(context.entitlementDataStore) }
    val isPremium by entitlements.isPremium.collectAsState(initial = false)
    val adUnlocks by entitlements.adUnlocks.collectAsState(initial = emptyMap())

    var query by rememberSaveable { mutableStateOf("") }
    // Section names and category keys, not enum ordinals or list indices, so the saved expansion
    // survives a reordered Section enum or a new category landing mid-list.
    var expandedSections by rememberSaveable { mutableStateOf(listOf<String>()) }
    var expandedCategories by rememberSaveable { mutableStateOf(listOf<String>()) }

    val searching = query.isNotBlank()
    val visibleGroups = remember(groups, query) { if (searching) groups.mapNotNull { it.filtered(query) } else groups }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = ScreenGutter,
            end = ScreenGutter,
            top = 8.dp,
            bottom = ScreenBottomInset,
        ),
    ) {
        item {
            Column(modifier = Modifier.padding(bottom = 10.dp)) {
                Text("Simulations", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "$total interactive labs — pick a section, then a lab",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
                )
                CategorySearchField(query = query, onQueryChange = { query = it }, placeholder = "Search labs…")
            }
        }

        if (visibleGroups.isEmpty()) {
            item {
                Text(
                    "No labs match “$query”.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 24.dp),
                )
            }
        }

        visibleGroups.forEach { group ->
            // A search shows its hits directly — collapsing them behind two taps would hide the answer.
            val sectionOpen = searching || group.section.name in expandedSections
            item(key = "header_${group.section.name}") {
                SimGroupHeader(
                    group = group,
                    isExpanded = sectionOpen,
                    onClick = {
                        expandedSections = if (group.section.name in expandedSections) {
                            expandedSections - group.section.name
                        } else {
                            expandedSections + group.section.name
                        }
                    },
                )
            }

            if (sectionOpen) {
                group.subgroups.forEach { subgroup ->
                    val categoryOpen = searching || subgroup.key in expandedCategories
                    item(key = "cat_${subgroup.key}") {
                        AccordionHeader(
                            title = subgroup.name,
                            count = subgroup.entries.size,
                            accentColor = group.accentColor,
                            isExpanded = categoryOpen,
                            modifier = Modifier.padding(start = 8.dp, top = 5.dp, bottom = 1.dp),
                            onClick = {
                                expandedCategories = if (subgroup.key in expandedCategories) {
                                    expandedCategories - subgroup.key
                                } else {
                                    expandedCategories + subgroup.key
                                }
                            },
                        )
                    }
                    if (categoryOpen) {
                        items(subgroup.entries, key = { "${subgroup.key}_${it.topic.id}" }) { entry ->
                            SimulationRow(
                                entry = entry,
                                isLocked = entry.topic.isPremium && !isPremium && entry.topic.id !in adUnlocks,
                                onClick = { onTopicClick(entry.topic.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}

// Sections keep sectionMeta's order; categories and rows keep the order the content provider and the
// category lists already publish, so the catalog matches the browser tabs row for row.
private fun buildGroups(): List<SimGroup> {
    val entriesBySection = LinkedHashMap<Section, LinkedHashMap<String, MutableList<SimEntry>>>()

    TopicContentProvider.runnableSimulations.forEach { (topicId, type) ->
        val topic = TopicRegistry.find(topicId) ?: return@forEach
        val category = CategoryRegistry.find(topic.categoryId)
        val section = category?.section ?: Section.ALGORITHMS
        val categoryName = category?.name ?: "Other"
        entriesBySection
            .getOrPut(section) { LinkedHashMap() }
            .getOrPut(categoryName) { mutableListOf() }
            .add(SimEntry(topic, simLabel(type)))
    }

    return sectionMeta.mapNotNull { (section, meta) ->
        val byCategory = entriesBySection[section] ?: return@mapNotNull null
        SimGroup(
            section = section,
            title = meta.title,
            iconName = meta.iconName,
            accentColor = meta.accentColor,
            subgroups = byCategory.map { (name, entries) ->
                SimSubgroup(key = "${section.name}|$name", name = name, entries = entries.toList())
            },
        )
    }
}

// Matches on topic name, lab label and category name, so "sort", "visualizer" and "Clustering" all
// find their labs. Returns null when nothing in the section matches.
private fun SimGroup.filtered(query: String): SimGroup? {
    val hits = subgroups.mapNotNull { subgroup ->
        val entries = subgroup.entries.filter {
            it.topic.name.contains(query, ignoreCase = true) ||
                it.label.contains(query, ignoreCase = true) ||
                subgroup.name.contains(query, ignoreCase = true)
        }
        if (entries.isEmpty()) null else subgroup.copy(entries = entries)
    }
    return if (hits.isEmpty()) null else copy(subgroups = hits)
}

@Composable
private fun SimGroupHeader(group: SimGroup, isExpanded: Boolean, onClick: () -> Unit) {
    val accent = Color(group.accentColor)
    val chevronRotation by animateFloatAsState(if (isExpanded) 180f else 0f, label = "chevron")
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 3.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = if (isExpanded) accent.copy(alpha = 0.07f) else MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, if (isExpanded) accent.copy(alpha = 0.45f) else MaterialTheme.colorScheme.outline),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            // Same 34.dp gradient badge SectionHeader uses on the browser screens.
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .background(
                        brush = Brush.linearGradient(listOf(accent, accent.copy(alpha = 0.6f))),
                        shape = RoundedCornerShape(11.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(resolveIcon(group.iconName), contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(group.title, fontFamily = SpaceGrotesk, fontWeight = FontWeight.Bold, fontSize = 15.5.sp)
                Text(
                    "${group.count} labs · ${group.subgroups.size} categories",
                    fontSize = 12.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                Icons.Filled.ExpandMore,
                contentDescription = if (isExpanded) "Collapse" else "Expand",
                tint = accent,
                modifier = Modifier.size(22.dp).rotate(chevronRotation),
            )
        }
    }
}

@Composable
private fun SimulationRow(entry: SimEntry, isLocked: Boolean, onClick: () -> Unit) {
    val accent = Color(entry.topic.accentColor)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, top = 4.dp, bottom = 4.dp)
            .alpha(if (isLocked) 0.72f else 1f)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(accent.copy(alpha = 0.14f), RoundedCornerShape(13.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(resolveIcon(entry.topic.iconName), contentDescription = null, tint = accent, modifier = Modifier.size(22.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(entry.topic.name, fontFamily = SpaceGrotesk, fontWeight = FontWeight.Bold, fontSize = 15.5.sp)
                Text(
                    if (isLocked) "Premium · ${entry.label}" else entry.label,
                    fontSize = 12.5.sp,
                    color = if (isLocked) LockAmber else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (isLocked) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .background(LockAmber.copy(alpha = 0.16f), RoundedCornerShape(11.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    AdUnlockableLockIcon(size = 20.dp)
                }
            } else {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .background(accent, RoundedCornerShape(11.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = "Run", tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}
