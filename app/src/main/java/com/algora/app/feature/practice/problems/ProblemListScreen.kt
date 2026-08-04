package com.algora.app.feature.practice.problems

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.algora.app.core.data.entitlement.EntitlementRepository
import com.algora.app.core.data.entitlement.entitlementDataStore
import com.algora.app.core.data.model.Difficulty
import com.algora.app.core.data.progress.ProgressRepository
import com.algora.app.core.data.progress.progressDataStore
import com.algora.app.core.ui.components.AccordionHeader
import com.algora.app.core.ui.components.CategorySearchField
import com.algora.app.core.ui.components.DifficultyBadge

// Problem bank grouped by pattern, easy → hard inside each group, with a solved tick per row.
// 100+ problems over 23 patterns is too long to scan flat, so groups collapse and a search field
// plus difficulty/unsolved chips narrow the bank before it is opened.
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
    val adUnlocks by entitlements.adUnlocks.collectAsState(initial = emptyMap())

    val total = remember { ProblemRegistry.all.size }
    val solvedCount = solvedIds.count { ProblemRegistry.get(it) != null }

    var query by rememberSaveable { mutableStateOf("") }
    // Difficulty is saved by enum *name* rather than as a Set: only bundle-storable types survive
    // process death, and the list keeps the chip order stable.
    var difficultyNames by rememberSaveable { mutableStateOf(listOf<String>()) }
    var unsolvedOnly by rememberSaveable { mutableStateOf(false) }
    var expanded by rememberSaveable { mutableStateOf(listOf<String>()) }

    val filters = ProblemFilters(
        query = query,
        difficulties = difficultyNames.mapNotNull { name -> Difficulty.entries.find { it.name == name } }.toSet(),
        unsolvedOnly = unsolvedOnly,
    )
    val groups = filterByPattern(filters, solvedIds)
    val shownCount = groups.sumOf { it.second.size }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Text("Problem Solving", style = MaterialTheme.typography.headlineMedium)
            }
        }
        item {
            Text(
                if (filters.isActive) "$shownCount of $total shown · $solvedCount solved" else "$solvedCount of $total solved",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, bottom = 10.dp),
            )
        }
        item {
            CategorySearchField(
                query = query,
                onQueryChange = { query = it },
                placeholder = "Search problems…",
            )
        }
        item {
            FilterChips(
                selected = difficultyNames,
                unsolvedOnly = unsolvedOnly,
                onToggleDifficulty = { name ->
                    difficultyNames = if (name in difficultyNames) difficultyNames - name else difficultyNames + name
                },
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
            val isLocked = pattern.isPremium && !isPremium && pattern.id !in adUnlocks
            // A narrowed bank shows its hits directly — hiding them behind a tap would defeat the
            // filter that just produced them. Locked groups never auto-open, filter match or not.
            val isOpen = !isLocked && (filters.isActive || pattern.id in expanded)
            item(key = "header_${pattern.id}") {
                AccordionHeader(
                    title = pattern.name,
                    count = problems.size,
                    subtitle = "${problems.count { it.id in solvedIds }} solved",
                    accentColor = pattern.accentColor,
                    isExpanded = isOpen,
                    locked = isLocked,
                    modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                    onClick = {
                        if (isLocked) {
                            onGoPremium()
                        } else {
                            expanded = if (pattern.id in expanded) expanded - pattern.id else expanded + pattern.id
                        }
                    },
                )
            }
            if (isOpen) {
                item(key = "blurb_${pattern.id}") {
                    Text(
                        pattern.blurb,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 22.dp, top = 6.dp, bottom = 2.dp),
                    )
                }
                items(problems.size, key = { i -> problems[i].id }) { i ->
                    val problem = problems[i]
                    ProblemRow(
                        problem = problem,
                        accent = Color(pattern.accentColor),
                        solved = problem.id in solvedIds,
                        onClick = { onProblemClick(problem.id) },
                    )
                }
            }
        }
    }
}

// Difficulty chips reuse DifficultyBadge's vocabulary (Easy/Medium/Hard) and colors so a chip and a
// row badge for the same level never disagree.
@Composable
private fun FilterChips(
    selected: List<String>,
    unsolvedOnly: Boolean,
    onToggleDifficulty: (String) -> Unit,
    onToggleUnsolved: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Difficulty.entries.forEach { difficulty ->
            val (label, color) = when (difficulty) {
                Difficulty.BEGINNER -> "Easy" to Color(0xFF16A34A)
                Difficulty.INTERMEDIATE -> "Medium" to Color(0xFFF59E0B)
                Difficulty.ADVANCED -> "Hard" to Color(0xFFEF4444)
            }
            FilterChip(
                label = label,
                color = color,
                selected = difficulty.name in selected,
                onClick = { onToggleDifficulty(difficulty.name) },
            )
        }
        FilterChip(
            label = "Unsolved",
            color = MaterialTheme.colorScheme.primary,
            selected = unsolvedOnly,
            onClick = onToggleUnsolved,
        )
    }
}

@Composable
private fun FilterChip(label: String, color: Color, selected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        color = if (selected) color.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, if (selected) color else MaterialTheme.colorScheme.outline),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) color else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
        )
    }
}

@Composable
private fun ProblemRow(problem: PracticeProblem, accent: Color, solved: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, if (solved) accent.copy(alpha = 0.45f) else MaterialTheme.colorScheme.outline),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (solved) {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = "Solved",
                    tint = accent,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.size(10.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(problem.title, style = MaterialTheme.typography.bodyLarge)
                Spacer(modifier = Modifier.size(4.dp))
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
}
