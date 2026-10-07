package com.algora.app.feature.practice.problems

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.algora.app.core.data.entitlement.EntitlementRepository
import com.algora.app.core.data.entitlement.entitlementDataStore
import com.algora.app.core.data.model.Difficulty
import com.algora.app.core.data.progress.ProgressRepository
import com.algora.app.core.data.progress.progressDataStore
import com.algora.app.core.ui.components.AdUnlockableLockIcon
import com.algora.app.core.ui.components.CategorySearchField
import com.algora.app.core.ui.components.DifficultyBadge
import com.algora.app.core.ui.components.ScreenHeader
import com.algora.app.core.ui.theme.IBMPlexMono
import com.algora.app.core.ui.theme.ScreenBottomInset
import com.algora.app.core.ui.theme.ScreenGutter
import com.algora.app.core.ui.theme.SimColors
import com.algora.app.core.ui.theme.SpaceGrotesk

// Problem bank grouped by pattern, easy → hard inside each group, with a solved tick per row.
// Layout: a progress summary card (solved count, overall bar, per-difficulty tallies), a pill search
// field, one row of pill chips — All/Easy/Medium/Hard pick a single difficulty, Unsolved toggles on
// top — and a card per pattern with a two-letter tile and its own progress bar. A card opens in place
// to list its problems.
@Composable
fun ProblemListScreen(
    onProblemClick: (String) -> Unit,
    onBack: () -> Unit,
    onGoPremium: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val repository = remember { ProgressRepository(context.progressDataStore) }
    val solvedIds by repository.solvedProblemIds.collectAsState(initial = emptySet())
    val entitlements = remember { EntitlementRepository(context.entitlementDataStore) }
    val isPremium by entitlements.isPremium.collectAsState(initial = false)

    val all = remember { ProblemRegistry.all }
    val solvedCount = solvedIds.count { ProblemRegistry.get(it) != null }

    var query by rememberSaveable { mutableStateOf("") }
    // Saved by enum *name*: only bundle-storable types survive process death. Null = "All".
    var difficultyName by rememberSaveable { mutableStateOf<String?>(null) }
    var unsolvedOnly by rememberSaveable { mutableStateOf(false) }
    var expanded by rememberSaveable { mutableStateOf(listOf<String>()) }

    val difficulty = difficultyName?.let { name -> Difficulty.entries.find { it.name == name } }
    val filters = ProblemFilters(
        query = query,
        difficulties = setOfNotNull(difficulty),
        unsolvedOnly = unsolvedOnly,
    )
    val groups = filterByPattern(filters, solvedIds)

    Column(modifier = modifier.fillMaxSize().imePadding()) {
        ScreenHeader(title = "Problem Solving", onBack = onBack)
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(
                start = ScreenGutter,
                end = ScreenGutter,
                top = 12.dp,
                bottom = ScreenBottomInset,
            ),
        ) {
            item {
                SummaryCard(
                    solved = solvedCount,
                    total = all.size,
                    tallies = Difficulty.entries.map { level ->
                        val inLevel = all.filter { it.difficulty == level }
                        Triple(level, inLevel.count { it.id in solvedIds }, inLevel.size)
                    },
                )
            }
            item {
                CategorySearchField(
                    query = query,
                    onQueryChange = { query = it },
                    placeholder = "Search ${all.size} problems",
                    pill = true,
                    modifier = Modifier.padding(top = 14.dp),
                )
            }
            item {
                FilterChips(
                    selected = difficulty,
                    unsolvedOnly = unsolvedOnly,
                    onSelectDifficulty = { difficultyName = it?.name },
                    onToggleUnsolved = { unsolvedOnly = !unsolvedOnly },
                )
            }

            if (groups.isEmpty()) {
                item {
                    Text(
                        "No problems match these filters.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 24.dp),
                    )
                }
            }

            groups.forEach { (pattern, problems) ->
                // Purchase-only (PaidOnly): a rewarded ad never opens a problem group.
                val isLocked = pattern.isPremium && !isPremium
                // A narrowed bank shows its hits directly — hiding them behind a tap would defeat the
                // filter that just produced them. Locked groups never auto-open, filter match or not.
                val isOpen = !isLocked && (filters.isActive || pattern.id in expanded)
                item(key = "group_${pattern.id}") {
                    PatternCard(
                        pattern = pattern,
                        problems = problems,
                        solvedIds = solvedIds,
                        isOpen = isOpen,
                        isLocked = isLocked,
                        onToggle = {
                            if (isLocked) {
                                onGoPremium()
                            } else {
                                expanded = if (pattern.id in expanded) expanded - pattern.id else expanded + pattern.id
                            }
                        },
                        onProblemClick = onProblemClick,
                    )
                }
            }
        }
    }
}

private fun difficultyStyle(difficulty: Difficulty): Pair<String, Color> = when (difficulty) {
    Difficulty.BEGINNER -> "Easy" to SimColors.Green
    Difficulty.INTERMEDIATE -> "Medium" to SimColors.Amber
    Difficulty.ADVANCED -> "Hard" to SimColors.Red
}

// Two letters for a pattern's tile: the initials of its first two words, or the first two letters of
// a one-word name — "Sliding Window" → SW, "Greedy" → GR.
internal fun patternCode(name: String): String {
    val words = name.split(' ', '-', '&').filter { it.isNotBlank() && it.first().isLetter() }
    val code = if (words.size >= 2) "${words[0].first()}${words[1].first()}" else words.firstOrNull()?.take(2).orEmpty()
    return code.uppercase()
}

@Composable
private fun ProgressTrack(fraction: Float, color: Color, height: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.outline),
    ) {
        if (fraction > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction.coerceIn(0f, 1f))
                    .clip(RoundedCornerShape(50))
                    .background(color),
            )
        }
    }
}

@Composable
private fun SummaryCard(solved: Int, total: Int, tallies: List<Triple<Difficulty, Int, Int>>) {
    val primary = MaterialTheme.colorScheme.primary
    val percent = if (total == 0) 0 else solved * 100 / total
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    "$solved",
                    fontFamily = SpaceGrotesk,
                    fontWeight = FontWeight.Bold,
                    fontSize = 34.sp,
                    lineHeight = 36.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "of $total solved",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp).weight(1f),
                )
                Text(
                    "$percent%",
                    fontFamily = IBMPlexMono,
                    fontSize = 13.sp,
                    color = primary,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }
            ProgressTrack(
                fraction = if (total == 0) 0f else solved / total.toFloat(),
                color = primary,
                height = 6.dp,
                modifier = Modifier.padding(top = 14.dp, bottom = 14.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
                tallies.forEach { (level, done, count) ->
                    val (label, color) = difficultyStyle(level)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(7.dp).background(color, CircleShape))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "$done/$count",
                            fontFamily = IBMPlexMono,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }
}

// All/Easy/Medium/Hard are one choice; Unsolved stacks on top of whichever is picked. The row
// scrolls rather than wrapping so a narrow phone keeps every chip on one line.
@Composable
private fun FilterChips(
    selected: Difficulty?,
    unsolvedOnly: Boolean,
    onSelectDifficulty: (Difficulty?) -> Unit,
    onToggleUnsolved: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(top = 12.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PillChip(label = "All", selected = selected == null, onClick = { onSelectDifficulty(null) })
        Difficulty.entries.forEach { level ->
            PillChip(
                label = difficultyStyle(level).first,
                selected = selected == level,
                onClick = { onSelectDifficulty(if (selected == level) null else level) },
            )
        }
        PillChip(label = "Unsolved", selected = unsolvedOnly, onClick = onToggleUnsolved)
    }
}

@Composable
private fun PillChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    Surface(
        modifier = Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onClick),
        shape = RoundedCornerShape(50),
        color = if (selected) primary else MaterialTheme.colorScheme.surface,
        border = if (selected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Text(
            label,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun PatternCard(
    pattern: ProblemPattern,
    problems: List<PracticeProblem>,
    solvedIds: Set<String>,
    isOpen: Boolean,
    isLocked: Boolean,
    onToggle: () -> Unit,
    onProblemClick: (String) -> Unit,
) {
    val accent = Color(pattern.accentColor)
    val done = problems.count { it.id in solvedIds }
    Surface(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle)
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(accent.copy(alpha = 0.18f), RoundedCornerShape(11.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        patternCode(pattern.name),
                        fontFamily = IBMPlexMono,
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp,
                        color = accent,
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            pattern.name,
                            fontFamily = SpaceGrotesk,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "$done/${problems.size}",
                            fontFamily = IBMPlexMono,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    ProgressTrack(
                        fraction = if (problems.isEmpty()) 0f else done / problems.size.toFloat(),
                        color = accent,
                        height = 4.dp,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                if (isLocked) {
                    AdUnlockableLockIcon(size = 17.dp, adUnlockable = false)
                } else {
                    Icon(
                        if (isOpen) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                        contentDescription = if (isOpen) "Collapse" else "Expand",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
            if (isOpen) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                Text(
                    pattern.blurb,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
                )
                problems.forEach { problem ->
                    ProblemRow(
                        problem = problem,
                        accent = accent,
                        solved = problem.id in solvedIds,
                        onClick = { onProblemClick(problem.id) },
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun ProblemRow(problem: PracticeProblem, accent: Color, solved: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (solved) {
            Icon(Icons.Filled.CheckCircle, contentDescription = "Solved", tint = accent, modifier = Modifier.size(18.dp))
        } else {
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .padding(1.5.dp)
                    .border(1.5.dp, MaterialTheme.colorScheme.outline, CircleShape),
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(problem.title, style = MaterialTheme.typography.bodyLarge)
            Spacer(modifier = Modifier.height(4.dp))
            DifficultyBadge(problem.difficulty)
        }
        Icon(
            Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
    }
}
