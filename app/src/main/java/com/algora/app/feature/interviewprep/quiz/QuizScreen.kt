package com.algora.app.feature.interviewprep.quiz

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.algora.app.core.analytics.quizCompleted
import com.algora.app.core.analytics.rememberAnalytics
import com.algora.app.core.ads.QuizExitInterstitial
import com.algora.app.core.ads.rememberQuizExitInterstitial
import com.algora.app.core.data.entitlement.EntitlementRepository
import com.algora.app.core.data.entitlement.entitlementDataStore
import com.algora.app.core.data.model.Difficulty
import com.algora.app.core.data.settings.QuizAttempt
import com.algora.app.core.data.settings.SettingsRepository
import com.algora.app.core.data.settings.bestCorrect
import com.algora.app.core.data.settings.bestPercent
import com.algora.app.core.data.settings.ofLength
import com.algora.app.core.data.settings.questionKey
import com.algora.app.core.data.settings.settingsDataStore
import com.algora.app.core.ui.components.ScreenHeader
import com.algora.app.core.ui.theme.SimColors
import com.algora.app.core.ui.theme.SpaceGrotesk
import com.algora.app.feature.topics.FigureCard
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val CorrectGreen = SimColors.Green
private val WrongRed = SimColors.Red

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
    // Where each question came from, as questionKey(quizId, index). Defaults to this quiz's own
    // positions; the drills pass the sets their questions were sampled from, so a result lands on
    // the real question rather than on a slot in a set that exists for one sitting.
    questionKeys: List<String>? = null,
    // Ask Learn or Interview before the first run. Off for the daily drill, whose timed run is what
    // marks the step done.
    offerLearnMode: Boolean = true,
    // Record timed runs as attempts and show the best score. Off for sets rebuilt on every open,
    // where "best 8/10" would compare different questions.
    trackBest: Boolean = true,
) {
    val context = LocalContext.current
    val settings = remember { SettingsRepository(context.settingsDataStore) }
    val entitlements = remember { EntitlementRepository(context.entitlementDataStore) }
    val isPremium by entitlements.isPremium.collectAsState(initial = true)
    val interstitial = rememberQuizExitInterstitial(enabled = adsEnabled)
    val analytics = rememberAnalytics()
    val scope = rememberCoroutineScope()
    val attemptsByQuiz by settings.quizAttempts.collectAsState(initial = emptyMap())
    val history = attemptsByQuiz[quizId].orEmpty().ofLength(quiz.questions.size)

    val baseKeys = remember(quiz, quizId) {
        questionKeys ?: quiz.questions.indices.map { questionKey(quizId, it) }
    }
    var mode by remember { mutableStateOf(if (offerLearnMode) null else QuizMode.Interview) }
    // Parallel to `running`: the source key of each question in the current run, so a missed-only
    // retry still reports back to the questions it was cut from.
    var runningKeys by remember { mutableStateOf(baseKeys) }

    var attempt by remember { mutableStateOf(0) }
    // A "missed questions only" run is a subset of the real quiz, so it must never be scored against
    // it — history stays comparable ("best 8/10" always means 8 of the same 10).
    var running by remember { mutableStateOf(quiz) }
    // The run just recorded, so the results screen can show what the best *was* before it without
    // snapshotting a flow that has not emitted yet on first composition.
    var recordedAt by remember { mutableStateOf<Long?>(null) }

    val isFullQuiz = running === quiz

    val chosenMode = mode
    if (chosenMode == null) {
        QuizModeChooser(
            quiz = quiz,
            trackBest = trackBest,
            history = if (trackBest) history else emptyList(),
            onPick = { mode = it },
            onBack = onBack,
        )
        return
    }
    // Only timed full runs are scored: a learn-mode pass shows every answer as it goes, so its score
    // would not mean what "best 8/10" means.
    val scored = isFullQuiz && chosenMode == QuizMode.Interview && trackBest

    // A fresh attempt rebuilds all per-run state (answers, index, timer, phase).
    key(attempt) {
        QuizRunner(
            source = running,
            mode = chosenMode,
            priorAttempts = if (scored) history.filter { it.atEpochSec != recordedAt } else emptyList(),
            onBack = onBack,
            onTopicClick = onTopicClick,
            // A missed-only run is a subset drill, not a quiz finish: it neither scores nor counts
            // towards the interstitial's warm-up, and leaving it is never taxed.
            exitAd = if (isFullQuiz) interstitial else null,
            onGoPremium = onGoPremium.takeIf { !isPremium },
            onComplete = { result ->
                // Every run — learn or timed, full or missed-only — reports each answer to the
                // question it came from; that is what weak spots and the mistake flashcards read.
                val keys = runningKeys
                val wrong = result.wrongIndices.toSet()
                scope.launch {
                    settings.recordQuestionResults(keys.mapIndexed { i, key -> key to (i !in wrong) }.toMap())
                }
                if (isFullQuiz) {
                    scope.launch { settings.recordQuizFinished() }
                    if (scored) {
                        recordedAt = result.atEpochSec
                        scope.launch { settings.recordQuizAttempt(quizId, result) }
                        // Scored runs only, matching what gets recorded: a missed-only drill is a
                        // subset of the set and would drag the score distribution down for free.
                        analytics.quizCompleted(quizId, result.percent, result.seconds)
                    }
                    onFinish()
                }
            },
            onRetry = {
                running = quiz
                runningKeys = baseKeys
                recordedAt = null
                attempt++
            },
            onRetryWrong = { wrongIndices ->
                // Indices are positions in the run just finished, which may itself be a missed-only
                // subset — so cut from `running`, not from the full quiz.
                runningKeys = wrongIndices.map { runningKeys[it] }
                running = missedOnly(running, wrongIndices)
                attempt++
            },
        )
    }
}

@Composable
private fun QuizRunner(
    source: Quiz,
    mode: QuizMode,
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
    var elapsed by remember { mutableIntStateOf(0) }
    var finished by remember { mutableStateOf(false) }
    // Learn mode: which questions have been checked. A checked answer is locked — the first pick is
    // the one that counts, or seeing the answer and switching would score every question correct.
    val checked: SnapshotStateList<Boolean> = remember { List(quiz.questions.size) { false }.toMutableStateList() }
    val learning = mode == QuizMode.Learn

    // Countdown in interview mode (expiry auto-submits); learn mode has no clock, it only counts up
    // so the results can say how long the pass took.
    LaunchedEffect(finished) {
        while (!finished && (learning || remaining > 0)) {
            delay(1000)
            elapsed++
            if (!learning) remaining--
        }
        if (!learning && remaining <= 0) finished = true
    }
    val timeUsed = if (learning) elapsed else quiz.timeLimitSeconds - remaining

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
                    seconds = timeUsed,
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
            timeUsed = timeUsed,
            mode = mode,
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
    // A picture question stacks a figure above four options, which outgrows a small phone. Keyed on
    // the index so each question opens scrolled to the top.
    val scroll = key(index) { rememberScrollState() }
    // After Check, the explanation lands under the options — below the fold on any question with a
    // figure. Bring it into view, or the learner taps Next without ever reading why.
    val feedbackInView = key(index) { remember { BringIntoViewRequester() } }
    val justChecked = learning && checked[index]
    LaunchedEffect(index, justChecked) {
        if (justChecked) {
            withFrameNanos { } // let the card lay out first, so there is something to bring into view
            feedbackInView.bringIntoView()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        QuizHeader(title = quiz.title, remaining = remaining.takeUnless { learning }, onBack = onBack)

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(scroll)
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

            question.story?.let { StoryCard(it, modifier = Modifier.padding(top = 14.dp)) }

            // In a story round the figure belongs to the story, so it sits between the story and the
            // question; everywhere else it follows the prompt it illustrates.
            if (question.story != null) {
                question.figure?.let { FigureCard(it, modifier = Modifier.padding(top = 8.dp)) }
            }

            Text(
                question.prompt,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 14.dp, bottom = 4.dp),
            )

            if (question.story == null) {
                question.figure?.let { FigureCard(it, modifier = Modifier.padding(top = 8.dp)) }
            }

            Column(
                modifier = Modifier.padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                val isChecked = learning && checked[index]
                question.options.forEachIndexed { i, option ->
                    OptionCard(
                        text = option,
                        selected = answers[index] == i,
                        // Once checked, the right option shows green and a wrong pick shows red.
                        result = when {
                            !isChecked -> null
                            i == question.correctIndex -> true
                            answers[index] == i -> false
                            else -> null
                        },
                        onClick = { if (!isChecked) answers[index] = i },
                    )
                }
            }

            if (learning && checked[index]) {
                LearnFeedback(
                    question = question,
                    correct = answers[index] == question.correctIndex,
                    onTopicClick = onTopicClick,
                    modifier = Modifier
                        .padding(top = 14.dp, bottom = 8.dp)
                        .bringIntoViewRequester(feedbackInView),
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (index > 0) {
                OutlinedButton(onClick = { index-- }, modifier = Modifier.weight(1f)) { Text("Previous") }
            }
            if (learning && !checked[index]) {
                Button(
                    onClick = { checked[index] = true },
                    enabled = answers[index] != null,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Check")
                }
            } else {
                Button(
                    onClick = { if (index < quiz.questions.lastIndex) index++ else finished = true },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(if (index < quiz.questions.lastIndex) "Next" else "Finish")
                }
            }
        }
    }
}

// remaining = null is learn mode: no clock to show, so the pill names the mode instead.
@Composable
private fun QuizHeader(title: String, remaining: Int?, onBack: () -> Unit) {
    ScreenHeader(
        title = title,
        onBack = onBack,
        trailing = { if (remaining != null) TimerPill(remaining) else LearnPill() },
    )
}

@Composable
private fun LearnPill() {
    val color = CorrectGreen
    Row(
        modifier = Modifier
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.School, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.size(5.dp))
        Text("Learn", color = color, fontWeight = FontWeight.Bold, fontFamily = SpaceGrotesk, fontSize = 14.sp)
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

// result: null while unanswered or in interview mode; true / false once learn mode has checked it.
@Composable
private fun OptionCard(text: String, selected: Boolean, onClick: () -> Unit, result: Boolean? = null) {
    val accent = when (result) {
        true -> CorrectGreen
        false -> WrongRed
        null -> MaterialTheme.colorScheme.primary
    }
    val emphasised = selected || result != null
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = if (emphasised) accent.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface,
        border = BorderStroke(if (emphasised) 2.dp else 1.dp, if (emphasised) accent else MaterialTheme.colorScheme.outline),
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
        Difficulty.BEGINNER -> "Easy" to SimColors.Green
        Difficulty.INTERMEDIATE -> "Medium" to SimColors.Amber
        Difficulty.ADVANCED -> "Hard" to SimColors.Red
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
    mode: QuizMode,
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
        ScreenHeader(title = "Results", onBack = onBack)

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
                        if (mode == QuizMode.Learn) {
                            Text(
                                "Learn mode — not added to your best score. Missed questions are now in your flashcards.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        } else if (wrongIndices.isNotEmpty()) {
                            Text(
                                "Missed questions are now in your flashcards.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        }
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

// The narrated set-up of a story round. Card treatment matches the step/list cards (radius 16,
// surface, 1dp outline, 15dp padding); the title is tinted like a tag so it reads as a label, not
// as a second question.
@Composable
private fun StoryCard(story: QuizStory, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(modifier = Modifier.padding(15.dp)) {
            Text(
                story.title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.size(6.dp))
            Text(story.text, style = MaterialTheme.typography.bodyMedium)
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
            question.story?.let {
                Text(
                    it.title,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
            Text(question.prompt, style = MaterialTheme.typography.titleMedium)
            question.figure?.let { FigureCard(it, modifier = Modifier.padding(top = 8.dp)) }
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
            ExplanationBlock(question, onTopicClick)
        }
    }
}

// The explanation and its "Study:" link — shared by the results review and learn mode's instant
// feedback, so a learner reads the same words whichever mode they used.
@Composable
private fun ExplanationBlock(question: QuizQuestion, onTopicClick: (String) -> Unit) {
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

// Learn mode's answer card, shown under the options once a question is checked. Tinted by the
// outcome with the same alpha treatment as the results summary, so right and wrong read at a glance.
@Composable
private fun LearnFeedback(
    question: QuizQuestion,
    correct: Boolean,
    onTopicClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tone = if (correct) CorrectGreen else WrongRed
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = tone.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, tone.copy(alpha = 0.3f)),
    ) {
        Column(modifier = Modifier.padding(15.dp)) {
            Text(
                if (correct) "Correct" else "Not quite — the answer is: ${question.options[question.correctIndex]}",
                style = MaterialTheme.typography.titleSmall,
                color = tone,
                fontWeight = FontWeight.Bold,
            )
            ExplanationBlock(question, onTopicClick)
        }
    }
}

// Shown before the first run. Two cards in the list-card treatment (radius 16, surface, 1dp outline,
// 15dp padding, a 44dp tinted icon tile) — a choice between two ways to use the same set.
@Composable
private fun QuizModeChooser(
    quiz: Quiz,
    trackBest: Boolean,
    history: List<QuizAttempt>,
    onPick: (QuizMode) -> Unit,
    onBack: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        ScreenHeader(title = quiz.title, onBack = onBack)
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Text(
                quiz.description,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = 16.dp),
            )
            Text(
                "${quiz.questions.size} questions · ${quiz.timeLimitSeconds / 60} min timed",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
            if (history.isNotEmpty()) {
                Text(
                    "Best so far: ${history.bestCorrect}/${quiz.questions.size}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = CorrectGreen,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Spacer(modifier = Modifier.size(18.dp))
            ModeCard(
                icon = Icons.Filled.Timer,
                tint = MaterialTheme.colorScheme.primary,
                title = "Interview mode",
                body = if (trackBest) {
                    "Timed like the real thing. Answers and explanations at the end. Counts toward your best score."
                } else {
                    "Timed like the real thing. Answers and explanations at the end."
                },
                onClick = { onPick(QuizMode.Interview) },
            )
            Spacer(modifier = Modifier.size(10.dp))
            ModeCard(
                icon = Icons.Filled.School,
                tint = CorrectGreen,
                title = "Learn mode",
                body = if (trackBest) {
                    "No timer. Check each answer and read why straight away. Not added to your best score."
                } else {
                    "No timer. Check each answer and read why straight away."
                },
                onClick = { onPick(QuizMode.Learn) },
            )
        }
    }
}

@Composable
private fun ModeCard(
    icon: ImageVector,
    tint: Color,
    title: String,
    body: String,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Row(modifier = Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(tint.copy(alpha = 0.14f), RoundedCornerShape(13.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
            }
            Spacer(modifier = Modifier.size(13.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        }
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
