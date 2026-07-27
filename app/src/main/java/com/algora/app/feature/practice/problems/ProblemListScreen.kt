package com.algora.app.feature.practice.problems

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.algora.app.core.data.progress.ProgressRepository
import com.algora.app.core.data.progress.progressDataStore
import com.algora.app.core.ui.components.DifficultyBadge
import com.algora.app.core.ui.theme.SpaceGrotesk

// Problem bank grouped by pattern, easy → hard inside each group, with a solved tick per row.
@Composable
fun ProblemListScreen(onProblemClick: (String) -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val repository = remember { ProgressRepository(context.progressDataStore) }
    val solvedIds by repository.solvedProblemIds.collectAsState(initial = emptySet())

    val patterns = remember { ProblemRegistry.patterns }
    val total = remember { ProblemRegistry.all.size }
    val solvedCount = solvedIds.count { ProblemRegistry.get(it) != null }

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
                "$solvedCount of $total solved",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
            )
        }

        patterns.forEach { pattern ->
            val problems = ProblemRegistry.forPattern(pattern.id)
            item(key = "header_${pattern.id}") {
                PatternHeader(pattern = pattern, solvedInPattern = problems.count { it.id in solvedIds })
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

@Composable
private fun PatternHeader(pattern: ProblemPattern, solvedInPattern: Int) {
    val accent = Color(pattern.accentColor)
    Column(modifier = Modifier.padding(top = 18.dp, bottom = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(accent, RoundedCornerShape(3.dp)),
            )
            Spacer(modifier = Modifier.size(9.dp))
            Text(
                pattern.name,
                fontFamily = SpaceGrotesk,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            Text(
                "$solvedInPattern/${ProblemRegistry.forPattern(pattern.id).size}",
                style = MaterialTheme.typography.bodySmall,
                color = accent,
            )
        }
        Text(
            pattern.blurb,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 3.dp, start = 19.dp),
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
