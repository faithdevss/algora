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
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.algora.app.core.data.entitlement.EntitlementRepository
import com.algora.app.core.data.entitlement.TopicAccess
import com.algora.app.core.data.entitlement.entitlementDataStore
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.Topic
import com.algora.app.core.data.progress.ProgressRepository
import com.algora.app.core.data.progress.progressDataStore
import com.algora.app.core.ui.components.DifficultyBadge
import com.algora.app.core.ui.components.ScreenHeader
import com.algora.app.core.ui.theme.IBMPlexMono
import com.algora.app.core.ui.theme.ScreenBottomInset
import com.algora.app.core.ui.theme.ScreenGutter
import com.algora.app.feature.premium.LockedTopicBody
import com.algora.app.feature.topics.CodeBlockCard
import com.algora.app.feature.topics.DetailHeader
import kotlinx.coroutines.launch

// One problem's workspace. Everything past the prompt is revealed on demand — hints one at a time,
// then the approach, then the solution — so a learner can take the smallest nudge that unblocks them
// instead of being shown the answer by scrolling.
//
// Pattern-level paywall gate, mirroring TopicDetailScreen: the list screen already hides locked
// patterns behind a lock icon, but this is the real enforcement point for any route that lands here
// directly (deep link, search, bookmarks) without passing through that list first.
@Composable
fun ProblemDetailScreen(
    problemId: String,
    onBack: () -> Unit,
    onOpenTopic: (String) -> Unit,
    onGoPremium: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val problem = remember(problemId) { ProblemRegistry.get(problemId) }
    if (problem == null) {
        MissingProblem(onBack = onBack, modifier = modifier)
        return
    }

    val pattern = remember(problem) { ProblemRegistry.pattern(problem.patternId) }
    val context = LocalContext.current
    val entitlements = remember { EntitlementRepository(context.entitlementDataStore) }
    val access by entitlements
        .accessFor(pattern?.id ?: problem.patternId, pattern?.isPremium == true, adUnlockable = false)
        .collectAsState(initial = null)

    val resolved = access ?: return   // one frame of nothing while DataStore answers

    if (pattern != null && resolved is TopicAccess.Locked) {
        val lockedTopic = remember(pattern) {
            Topic(
                id = pattern.id,
                name = pattern.name,
                categoryId = "",
                tagline = pattern.blurb,
                description = pattern.blurb,
                iconName = "",
                accentColor = pattern.accentColor,
                isPremium = true,
            )
        }
        Column(modifier = modifier.fillMaxSize()) {
            DetailHeader(title = pattern.name, onBack = onBack)
            LockedTopicBody(topic = lockedTopic, onGoPremium = onGoPremium, adUnlockable = false)
        }
        return
    }

    val repository = remember { ProgressRepository(context.progressDataStore) }
    val solvedIds by repository.solvedProblemIds.collectAsState(initial = emptySet())
    val scope = rememberCoroutineScope()
    val solved = problem.id in solvedIds

    val accent = remember(problem) { Color(pattern?.accentColor ?: 0xFF3B82F6) }
    val patternName = remember(problem) { pattern?.name ?: problem.patternId }

    var hintsShown by remember(problemId) { mutableIntStateOf(0) }
    var approachShown by remember(problemId) { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxSize()) {
        ScreenHeader(title = problem.title, onBack = onBack)
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(
                start = ScreenGutter,
                end = ScreenGutter,
                top = 8.dp,
                bottom = ScreenBottomInset,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // The title moved up into the fixed header, so what is left here is the problem's
            // pattern and difficulty — the metadata the header has no room for.
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.background(accent.copy(alpha = 0.14f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    ) {
                        Text(patternName, style = MaterialTheme.typography.labelSmall, color = accent)
                    }
                    Spacer(modifier = Modifier.size(8.dp))
                    DifficultyBadge(problem.difficulty)
                }
            }

            item {
                SectionCard(title = "Problem", accent = accent) {
                    Text(problem.prompt, style = MaterialTheme.typography.bodyLarge)
                }
            }

            if (problem.examples.isNotEmpty()) {
                item {
                    SectionCard(title = "Examples", accent = accent) {
                        problem.examples.forEachIndexed { index, example ->
                            if (index > 0) Spacer(modifier = Modifier.size(12.dp))
                            Text(
                                "Input:  ${example.input}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontFamily = IBMPlexMono,
                            )
                            Text(
                                "Output: ${example.output}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontFamily = IBMPlexMono,
                                color = accent,
                            )
                            if (example.note.isNotEmpty()) {
                                Text(
                                    example.note,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 2.dp),
                                )
                            }
                        }
                    }
                }
            }

            // Placed before the hints on purpose: if you cannot start at all, the gap is knowledge, not
            // a missing nudge. Each row opens the topic that teaches it.
            if (problem.prerequisites.isNotEmpty()) {
                item {
                    SectionCard(title = "Knowledge you need", accent = accent) {
                        Text(
                            "Tap any of these to study it before solving.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 10.dp),
                        )
                        problem.prerequisites.forEach { prereq ->
                            PrereqRow(prereq = prereq, onClick = { onOpenTopic(prereq.topicId) })
                        }
                    }
                }
            }

            if (problem.constraints.isNotEmpty()) {
                item {
                    SectionCard(title = "Constraints", accent = accent) {
                        problem.constraints.forEach { line -> BulletLine(line) }
                    }
                }
            }

            // Hints reveal one at a time; each is a smaller nudge than the approach below it.
            item {
                SectionCard(title = "Hints", accent = accent) {
                    if (hintsShown == 0) {
                        Text(
                            "Stuck? Take the smallest nudge first — ${problem.hints.size} hints available.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    problem.hints.take(hintsShown).forEachIndexed { index, hint ->
                        Row(modifier = Modifier.padding(top = if (index == 0) 0.dp else 10.dp)) {
                            Text(
                                "${index + 1}",
                                style = MaterialTheme.typography.labelLarge,
                                color = accent,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(end = 10.dp),
                            )
                            Text(hint, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    if (hintsShown < problem.hints.size) {
                        OutlinedButton(
                            onClick = { hintsShown++ },
                            modifier = Modifier.padding(top = 10.dp),
                        ) {
                            Text(if (hintsShown == 0) "Show first hint" else "Next hint (${hintsShown + 1}/${problem.hints.size})")
                        }
                    }
                }
            }

            item {
                SectionCard(title = "Approach", accent = accent) {
                    if (approachShown) {
                        problem.approach.forEachIndexed { index, step ->
                            Row(modifier = Modifier.padding(top = if (index == 0) 0.dp else 9.dp)) {
                                Text(
                                    "${index + 1}.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = accent,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(end = 8.dp),
                                )
                                Text(step, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                        Spacer(modifier = Modifier.size(12.dp))
                        ComplexityLine("Time", problem.timeComplexity, accent)
                        ComplexityLine("Space", problem.spaceComplexity, accent)
                    } else {
                        OutlinedButton(onClick = { approachShown = true }) { Text("Show approach & complexity") }
                    }
                }
            }

            // Collapsed by default — CodeBlockCard's own expander doubles as the "show solution" gate.
            item {
                CodeBlockCard(
                    CodeBlock(
                        title = "Solution — tap to reveal",
                        accentColor = ProblemRegistry.pattern(problem.patternId)?.accentColor ?: 0xFF3B82F6,
                        code = problem.solutionCode,
                    ),
                )
            }

            item {
                Button(
                    onClick = { scope.launch { repository.setProblemSolved(problem.id, !solved) } },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (solved) MaterialTheme.colorScheme.onSurfaceVariant else accent,
                        contentColor = Color.White,
                    ),
                ) {
                    Text(if (solved) "Solved — tap to undo" else "Mark as solved")
                }
            }

            val linkedId = problem.linkedTopicId
            if (linkedId != null) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth().clickable { onOpenTopic(linkedId) },
                        shape = RoundedCornerShape(14.dp),
                        color = accent.copy(alpha = 0.10f),
                        border = BorderStroke(1.dp, accent.copy(alpha = 0.35f)),
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text("Study the pattern", style = MaterialTheme.typography.labelMedium, color = accent)
                            Text(
                                problem.linkedTopicLabel ?: linkedId,
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionCard(title: String, accent: Color, content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(modifier = Modifier.padding(15.dp)) {
            Text(
                title.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 9.dp),
            )
            content()
        }
    }
}

@Composable
private fun PrereqRow(prereq: ProblemPrereq, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.background,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(prereq.label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    prereq.why,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 1.dp),
                )
            }
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(17.dp),
            )
        }
    }
}

@Composable
private fun BulletLine(text: String) {
    Row(modifier = Modifier.padding(vertical = 2.dp)) {
        Text("·", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(end = 8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ComplexityLine(label: String, value: String, accent: Color) {
    Row(modifier = Modifier.padding(top = 3.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = accent,
            modifier = Modifier.padding(end = 8.dp),
        )
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun MissingProblem(onBack: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxSize()) {
        ScreenHeader(title = "Problem", onBack = onBack)
        Text(
            "Problem not found",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = ScreenGutter, vertical = 16.dp),
        )
    }
}
