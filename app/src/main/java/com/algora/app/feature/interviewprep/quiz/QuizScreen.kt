package com.algora.app.feature.interviewprep.quiz

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.algora.app.core.ads.QuizExitInterstitial
import com.algora.app.core.ads.rememberQuizExitInterstitial
import com.algora.app.core.data.entitlement.EntitlementRepository
import com.algora.app.core.data.entitlement.entitlementDataStore
import com.algora.app.core.data.model.Difficulty
import com.algora.app.core.data.settings.QuizAttempt
import com.algora.app.core.data.settings.SettingsRepository
import com.algora.app.core.data.settings.bestCorrect
import com.algora.app.core.data.settings.bestPercent
import com.algora.app.core.data.settings.settingsDataStore
import com.algora.app.core.ui.theme.SimColors
import com.algora.app.core.ui.theme.SpaceGrotesk
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val CorrectGreen = Color(0xFF16A34A)
private val WrongRed = Color(0xFFEF4444)

@Composable
fun QuizScreen(
    quizId: String,
    quiz: Quiz,
    onBack: () -> Unit,
    onTopicClick: (String) -> Unit,
    onFinish: () -> Unit,
    onGoPremium: (() -> Unit)? = null,
    // The quiz-exit interstitial (Phase 14). Off for surfaces that must never be taxed — see
    // rememberQuizExitInterstitial.
    adsEnabled: Boolean = true,
) {
    val context = LocalContext.current
    val settings = remember { SettingsRepository(context.settingsDataStore) }
    val entitlements = remember { EntitlementRepository(context.entitlementDataStore) }
    val isPremium by entitlements.isPremium.collectAsState(initial = true)
    val interstitial = rememberQuizExitInterstitial(enabled = adsEnabled)
    val scope = rememberCoroutineScope()
    val attemptsByQuiz by settings.quizAttempts.collectAsState(initial = emptyMap())
    val history = attemptsByQuiz[quizId].orEmpty()

    var attempt by remember { mutableStateOf(0) }
    // A "missed questions only" run is a subset of the real quiz, so it must never be scored against
    // it — history stays comparable ("best 8/10" always means 8 of the same 10).
    var running by remember { mutableStateOf(quiz) }
    // The run just recorded, so the results screen can show what the best *was* before it without
    // snapshotting a flow that has not emitted yet on first composition.
    var recordedAt by remember { mutableStateOf<Long?>(null) }

    val isFullQuiz = running === quiz

    // A fresh attempt rebuilds all per-run state (answers, index, timer, phase).
    key(attempt) {
        QuizRunner(
            source = running,
            priorAttempts = if (isFullQuiz) history.filter { it.atEpochSec != recordedAt } else emptyList(),
            onBack = onBack,
            onTopicClick = onTopicClick,
            // A missed-only run is a subset drill, not a quiz finish: it neither scores nor counts
            // towards the interstitial's warm-up, and leaving it is never taxed.
            exitAd = if (isFullQuiz) interstitial else null,
            onGoPremium = onGoPremium.takeIf { !isPremium },
            onComplete = { result ->
                if (isFullQuiz) {
                    recordedAt = result.atEpochSec
                    scope.launch {
                        settings.recordQuizAttempt(quizId, result)
                        settings.recordQuizFinished()
                    }
                    onFinish()
                }
            },
            onRetry = {
                running = quiz
                recordedAt = null
                attempt++
            },
            onRetryWrong = { wrongIndices ->
                running = missedOnly(quiz, wrongIndices)
                attempt++
            },
        )
    }
}

@Composable
private fun QuizRunner(
    source: Quiz,
    priorAttempts: List<QuizAttempt>,
    onBack: () -> Unit,
    onTopicClick: (String) -> Unit,
    exitAd: QuizExitInterstitial?,
    onGoPremium: (() -> Unit)?,
    onComplete: (QuizAttempt) -> Unit,
    onRetry: () -> Unit,
    onRetryWrong: (List<Int>) -> Unit,
) {
    // Shuffled once per run (QuizScreen's key(attempt) rebuilds this composable for a fresh attempt),
    // so paging back to an earlier question finds the options where the learner left them. Question
    // order is untouched: wrongIndices and missedOnly both index into the source set.
    val quiz = remember(source) { source.withShuffledOptions() }

    val answers: SnapshotStateList<Int?> = remember { List<Int?>(quiz.questions.size) { null }.toMutableStateList() }
    var index by remember { mutableIntStateOf(0) }
    var remaining by remember { mutableIntStateOf(quiz.timeLimitSeconds) }
    var finished by remember { mutableStateOf(false) }

    // Countdown; expiry auto-submits.
    LaunchedEffect(finished) {
        while (!finished && remaining > 0) {
            delay(1000)
            remaining--
        }
        if (remaining <= 0) finished = true
    }

    if (finished) {
        val wrongIndices = quiz.questions.indices.filter { answers[it] != quiz.questions[it].correctIndex }
        // Recorded once per run: the timestamp is captured inside the effect so a recomposition
        // cannot log the same run twice under a new clock reading.
        LaunchedEffect(Unit) {
            onComplete(
                QuizAttempt(
                    atEpochSec = System.currentTimeMillis() / 1000L,
                    correct = quiz.questions.size - wrongIndices.size,
                    total = quiz.questions.size,
                    seconds = quiz.timeLimitSeconds - remaining,
                    wrongIndices = wrongIndices,
                ),
            )
        }
        // Every route off the results screen goes through here, system back included. Wiring the
        // ad to only the header arrow and the button would leave the back gesture as a free bypass,
        // which is most of how people actually leave a screen.
        val exitResults: () -> Unit = { if (exitAd == null) onBack() else exitAd.exit(onBack) }
        BackHandler(onBack = exitResults)

        QuizResults(
            quiz = quiz,
            answers = answers,
            wrongIndices = wrongIndices,
            priorAttempts = priorAttempts,
            timeUsed = quiz.timeLimitSeconds - remaining,
            onBack = exitResults,
            // Retrying is continued study, so it is never taxed — only leaving is.
            onRetry = onRetry,
            onRetryWrong = onRetryWrong,
            onTopicClick = onTopicClick,
            onGoPremium = onGoPremium,
        )
        return
    }

    val question = quiz.questions[index]

    Column(modifier = Modifier.fillMaxSize()) {
        QuizHeader(title = quiz.title, remaining = remaining, onBack = onBack)

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        ) {
            Text(
                "Question ${index + 1} of ${quiz.questions.size}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp, bottom = 6.dp),
            )
            LinearProgressIndicator(
                progress = { (index + 1) / quiz.questions.size.toFloat() },
                modifier = Modifier.fillMaxWidth(),
            )

            Row(modifier = Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TagChip(question.patternTag, MaterialTheme.colorScheme.primary)
                DifficultyChip(question.difficulty)
            }

            Text(
                question.prompt,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 14.dp, bottom = 4.dp),
            )

            Column(
                modifier = Modifier.padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                question.options.forEachIndexed { i, option ->
                    OptionCard(
                        text = option,
                        selected = answers[index] == i,
                        onClick = { answers[index] = i },
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (index > 0) {
                OutlinedButton(onClick = { index-- }, modifier = Modifier.weight(1f)) { Text("Previous") }
            }
            Button(
                onClick = { if (index < quiz.questions.lastIndex) index++ else finished = true },
                modifier = Modifier.weight(1f),
            ) {
                Text(if (index < quiz.questions.lastIndex) "Next" else "Finish")
            }
        }
    }
}

@Composable
private fun QuizHeader(title: String, remaining: Int, onBack: () -> Unit) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
            )
            TimerPill(remaining)
            Spacer(modifier = Modifier.size(6.dp))
        }
        HorizontalDivider()
    }
}

@Composable
private fun TimerPill(remaining: Int) {
    val urgent = remaining <= 30
    val color = if (urgent) WrongRed else MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Timer, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.size(5.dp))
        Text(
            formatTime(remaining),
            color = color,
            fontWeight = FontWeight.Bold,
            fontFamily = SpaceGrotesk,
            fontSize = 14.sp,
        )
    }
}

@Composable
private fun OptionCard(text: String, selected: Boolean, onClick: () -> Unit) {
    val border = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface,
        border = BorderStroke(if (selected) 2.dp else 1.dp, border),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(horizontal = 15.dp, vertical = 14.dp),
        )
    }
}

@Composable
private fun TagChip(text: String, color: Color) {
    Text(
        text,
        color = color,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

@Composable
private fun DifficultyChip(difficulty: Difficulty) {
    val (label, color) = when (difficulty) {
        Difficulty.BEGINNER -> "Easy" to Color(0xFF16A34A)
        Difficulty.INTERMEDIATE -> "Medium" to Color(0xFFF59E0B)
        Difficulty.ADVANCED -> "Hard" to Color(0xFFEF4444)
    }
    TagChip(label, color)
}

@Composable
private fun QuizResults(
    quiz: Quiz,
    answers: List<Int?>,
    wrongIndices: List<Int>,
    priorAttempts: List<QuizAttempt>,
    timeUsed: Int,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onRetryWrong: (List<Int>) -> Unit,
    onTopicClick: (String) -> Unit,
    onGoPremium: (() -> Unit)?,
) {
    val total = quiz.questions.size
    val correct = total - wrongIndices.size
    val pct = if (total == 0) 0 else correct * 100 / total

    Column(modifier = Modifier.fillMaxSize()) {
        QuizHeaderStatic(title = "Results", onBack = onBack)

        LazyColumn(modifier = Modifier.weight(1f).padding(horizontal = 16.dp)) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                    shape = RoundedCornerShape(20.dp),
                    color = if (pct >= 60) CorrectGreen.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    border = BorderStroke(1.dp, if (pct >= 60) CorrectGreen.copy(alpha = 0.3f) else MaterialTheme.colorScheme.outline),
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "$correct / $total",
                            fontFamily = SpaceGrotesk,
                            fontWeight = FontWeight.Bold,
                            fontSize = 40.sp,
                            color = if (pct >= 60) CorrectGreen else MaterialTheme.colorScheme.onSurface,
                        )
                        Text("$pct% correct · finished in ${formatTime(timeUsed)}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (priorAttempts.isNotEmpty()) {
                            val best = priorAttempts.bestPercent
                            val bestLine = "Best before: ${priorAttempts.bestCorrect}/$total · ${priorAttempts.size} past ${if (priorAttempts.size == 1) "attempt" else "attempts"}"
                            Text(
                                text = if (pct > best) "$bestLine — new best" else bestLine,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (pct > best) CorrectGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                    }
                }
            }

            item {
                Text(
                    "Review",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(top = 22.dp, bottom = 8.dp),
                )
            }

            items(quiz.questions.size) { i ->
                ReviewCard(
                    question = quiz.questions[i],
                    chosen = answers[i],
                    onTopicClick = onTopicClick,
                )
            }

            item {
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp)) {
                    // Drilling only what you missed is the point of keeping the wrong indices; it is
                    // the primary action whenever there is anything to redo.
                    if (wrongIndices.isNotEmpty()) {
                        Button(
                            onClick = { onRetryWrong(wrongIndices) },
                            colors = ButtonDefaults.buttonColors(containerColor = SimColors.Green),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Retry ${wrongIndices.size} missed ${if (wrongIndices.size == 1) "question" else "questions"}")
                        }
                        Spacer(modifier = Modifier.size(10.dp))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) { Text("Back to Prep") }
                        OutlinedButton(onClick = onRetry, modifier = Modifier.weight(1f)) { Text("Retry all") }
                    }
                    // Free users only. The ad on the way out creates the itch; this is the one place
                    // it has somewhere to go, on the very next tap.
                    onGoPremium?.let { goPremium ->
                        Text(
                            "Studying ad-free? Unlock everything — one payment, forever.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 14.dp)
                                .clickable(onClick = goPremium),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReviewCard(question: QuizQuestion, chosen: Int?, onTopicClick: (String) -> Unit) {
    val isCorrect = chosen == question.correctIndex
    Surface(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(modifier = Modifier.padding(15.dp)) {
            Text(question.prompt, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.size(8.dp))
            Text(
                text = "Your answer: ${chosen?.let { question.options[it] } ?: "— (skipped)"}",
                style = MaterialTheme.typography.bodyMedium,
                color = if (isCorrect) CorrectGreen else WrongRed,
                fontWeight = FontWeight.SemiBold,
            )
            if (!isCorrect) {
                Text(
                    "Correct: ${question.options[question.correctIndex]}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = CorrectGreen,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Text(
                question.explanation,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
            if (question.linkedTopicId != null) {
                Spacer(modifier = Modifier.size(10.dp))
                Row(
                    modifier = Modifier
                        .clickable { onTopicClick(question.linkedTopicId) }
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Study: ${question.linkedTopicLabel ?: "Related topic"}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun QuizHeaderStatic(title: String, onBack: () -> Unit) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.size(48.dp))
        }
        HorizontalDivider()
    }
}

// A derived quiz holding only the questions this run got wrong. The time budget shrinks with it —
// Quiz derives the limit from the question count, so a shorter list is a shorter clock with no
// arithmetic here — and the id is suffixed so nothing can mistake a subset run for a real attempt at
// the full set.
private fun missedOnly(quiz: Quiz, wrongIndices: List<Int>): Quiz = quiz.copy(
    id = "${quiz.id}_missed",
    title = "${quiz.title} · missed",
    questions = wrongIndices.mapNotNull { quiz.questions.getOrNull(it) },
)

private fun formatTime(seconds: Int): String {
    val s = seconds.coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}
