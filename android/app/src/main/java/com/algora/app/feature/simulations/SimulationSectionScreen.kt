package com.algora.app.feature.simulations

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.algora.app.core.data.entitlement.EntitlementRepository
import com.algora.app.core.data.entitlement.PaidOnly
import com.algora.app.core.data.entitlement.entitlementDataStore
import com.algora.app.core.data.model.Section
import com.algora.app.core.ui.components.AdUnlockableLockIcon
import com.algora.app.core.ui.components.CategorySearchField
import com.algora.app.core.ui.theme.IBMPlexMono
import com.algora.app.core.ui.theme.ScreenBottomInset
import com.algora.app.core.ui.theme.ScreenGutter

// One Simulations-tab section on its own page (Coding Patterns, Data Structures, …): a large title with
// its lab and category counts, a search scoped to the section, and one card of category rows. A row
// opens in place to its numbered labs; a lab row opens the lab. The iOS port is SimulationSectionScreen.swift.

/** A 5 × 5 dot glyph per lab family, lit dots in the accent over dim ones. */
internal fun simDotPattern(name: String): List<String> = when (name) {
    "Array Walk" -> listOf(".....", "..#..", "#####", ".....", ".....")
    "Recursion" -> listOf("#####", "#...#", "#...#", "#...#", "#####")
    "Tree" -> listOf("..#..", ".#.#.", "#...#", ".....", ".....")
    "Graph" -> listOf("#...#", ".#.#.", "..#..", ".#...", "#...#")
    "Grid" -> listOf("#.#.#", ".....", "#.#.#", ".....", "#.#.#")
    "DP Table" -> listOf("#####", "####.", "###..", "##...", "#....")
    "Sorting" -> listOf("....#", "...##", "..###", ".####", "#####")
    "Search" -> listOf(".###.", "#...#", "#...#", ".###.", "....#")
    "Hashing" -> listOf("##...", "..##.", "#...#", ".##..", "...##")
    "Bit Board" -> listOf("#.##.", ".#..#", "##.#.", "..##.", "#..##")
    "Linked Structure" -> listOf("#....", ".#...", "..#..", "...#.", "....#")
    "Game Search" -> listOf("..#..", ".###.", "#####", "..#..", "..#..")
    else -> {
        // Any other category: a mirrored pattern seeded by its name, so each reads as its own mark.
        var h = 2166136261L
        name.toByteArray().forEach { b -> h = ((h xor (b.toLong() and 0xFF)) * 16777619L) and 0xFFFFFFFFL }
        List(5) { r ->
            val bits = List(3) { c -> (h shr ((r * 3 + c) % 31)) and 1L == 1L }
            listOf(bits[0], bits[1], bits[2], bits[1], bits[0]).joinToString("") { if (it) "#" else "." }
        }
    }
}

private val patternSubtitles = mapOf(
    "Array Walk" to "Array · pointer player", "Recursion" to "Call tree", "Tree" to "Tree stepper", "Graph" to "Graph stepper",
    "Grid" to "Grid stepper", "DP Table" to "Table fill", "Sorting" to "Sort stepper", "Search" to "Search stepper",
    "Hashing" to "Bucket stepper", "Bit Board" to "Per-bit stepper", "Linked Structure" to "Node stepper", "Game Search" to "Game tree",
)

/** A category's subtitle: the pattern family's line, or the lab type most of its labs run. */
private fun subtitle(sub: SimSubgroup): String {
    if (sub.key.startsWith(Section.INTERVIEW_PREP.name)) patternSubtitles[sub.name]?.let { return it }
    val counts = LinkedHashMap<String, Int>()
    sub.entries.forEach { counts[it.label] = (counts[it.label] ?: 0) + 1 }
    var best = ""
    counts.forEach { (l, n) -> if (best.isEmpty() || n > counts.getValue(best)) best = l }
    return best
}

/** "02 · Trees" → "Trees". */
private fun plainName(name: String): String {
    val i = name.indexOf(" · ")
    return if (i > 0 && name.substring(0, i).all { it.isDigit() }) name.substring(i + 3) else name
}

@Composable
internal fun SimDotTile(name: String, accent: Color) {
    val rows = simDotPattern(name)
    Column(
        modifier = Modifier.size(44.dp).background(accent.copy(alpha = 0.16f), RoundedCornerShape(11.dp)),
        verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                row.forEach { ch -> Box(Modifier.size(4.5.dp).background(accent.copy(alpha = if (ch == '#') 1f else 0.22f), CircleShape)) }
            }
        }
    }
}

@Composable
fun SimulationSectionScreen(section: String, onBack: () -> Unit, onTopicClick: (String) -> Unit, modifier: Modifier = Modifier) {
    val group = remember(section) { simGroups.firstOrNull { it.section.name == section } }
    val context = LocalContext.current
    val entitlements = remember { EntitlementRepository(context.entitlementDataStore) }
    val isPremium by entitlements.isPremium.collectAsState(initial = false)
    val adUnlocks by entitlements.adUnlocks.collectAsState(initial = emptyMap())
    var query by rememberSaveable { mutableStateOf("") }
    var open by rememberSaveable { mutableStateOf(listOf<String>()) }
    val searching = query.isNotBlank()
    val shown = remember(group, query) { if (group == null) null else if (searching) group.filtered(query) else group }
    val accent = Color(group?.accentColor ?: 0xFF6366F1)
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    LazyColumn(
        modifier = modifier.fillMaxSize().imePadding(),
        contentPadding = PaddingValues(start = ScreenGutter, end = ScreenGutter, top = 8.dp, bottom = ScreenBottomInset),
    ) {
        item {
            Column {
                Row(
                    modifier = Modifier.heightIn(min = 44.dp).clickable(onClick = onBack),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBackIos, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Text("Simulations", color = MaterialTheme.colorScheme.primary, fontSize = 17.sp)
                }
                Text(group?.title ?: "Simulations", fontSize = 34.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 2.dp))
                if (group != null) {
                    Text(
                        "${group.count} labs · ${group.subgroups.size} categories",
                        fontSize = 15.sp, color = muted, modifier = Modifier.padding(top = 2.dp, bottom = 14.dp),
                    )
                    CategorySearchField(query = query, onQueryChange = { query = it }, placeholder = "Search ${group.title}")
                }
            }
        }
        item {
            if (shown == null) {
                if (group != null) Text("No labs match “$query”.", color = muted, modifier = Modifier.padding(top = 24.dp))
            } else {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                ) {
                    Column {
                        shown.subgroups.forEachIndexed { i, sub ->
                            // A search opens every category it matched.
                            val isOpen = searching || sub.key in open
                            if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                            CategoryRow(sub, accent, isOpen) { open = if (sub.key in open) open - sub.key else open + sub.key }
                            if (isOpen) {
                                sub.entries.forEachIndexed { j, entry ->
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(start = 16.dp))
                                    LabRow(
                                        number = j + 1,
                                        entry = entry,
                                        accent = accent,
                                        isLocked = entry.topic.isPremium && !isPremium && entry.topic.id !in adUnlocks,
                                        adUnlockable = !PaidOnly.isPaidOnlyTopic(entry.topic.id),
                                    ) { onTopicClick(entry.topic.id) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryRow(sub: SimSubgroup, accent: Color, expanded: Boolean, onClick: () -> Unit) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (expanded) onSurface.copy(alpha = 0.04f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SimDotTile(sub.name, accent)
        Column(modifier = Modifier.weight(1f)) {
            Text(plainName(sub.name), fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle(sub), fontSize = 14.sp, color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text("${sub.entries.size}", fontSize = 16.sp, color = muted)
        Icon(
            if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
            contentDescription = if (expanded) "Collapse" else "Expand",
            tint = if (expanded) accent else muted,
            modifier = Modifier.size(24.dp),
        )
    }
}

@Composable
private fun LabRow(number: Int, entry: SimEntry, accent: Color, isLocked: Boolean, adUnlockable: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .alpha(if (isLocked) 0.72f else 1f)
            .padding(start = 26.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            number.toString().padStart(2, '0'), fontFamily = IBMPlexMono, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
            color = accent, modifier = Modifier.width(30.dp),
        )
        Text(entry.topic.name, fontSize = 17.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Box(
            modifier = Modifier.size(40.dp).background(accent.copy(alpha = 0.2f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (isLocked) AdUnlockableLockIcon(size = 18.dp, adUnlockable = adUnlockable)
            else Icon(Icons.Filled.PlayArrow, contentDescription = "Run ${entry.topic.name}", tint = accent, modifier = Modifier.size(20.dp))
        }
    }
}
