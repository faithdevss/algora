package com.algora.app.feature.progress

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.algora.app.core.ads.AdsProvider
import com.algora.app.core.data.model.Topic
import com.algora.app.core.data.progress.ProgressRepository
import com.algora.app.core.data.progress.progressDataStore
import com.algora.app.core.data.settings.MAX_STREAK_FREEZES
import com.algora.app.core.data.settings.SettingsRepository
import com.algora.app.core.data.settings.settingsDataStore
import com.algora.app.core.nav.AppMode
import com.algora.app.core.nav.PatternsRoute
import com.algora.app.core.nav.Screen
import com.algora.app.core.playreview.AppReviewPrompt
import com.algora.app.core.ui.components.resolveIcon
import com.algora.app.core.ui.theme.ScreenBottomInset
import com.algora.app.core.ui.theme.ScreenGutter
import com.algora.app.core.ui.theme.SimColors
import com.algora.app.core.ui.theme.SpaceGrotesk
import kotlinx.coroutines.launch
import com.algora.app.feature.algorithms.AlgorithmsTopics
import com.algora.app.feature.analysis.AnalysisTopics
import com.algora.app.feature.datastructures.DataStructuresTopics
import com.algora.app.feature.deeplearning.DeepLearningTopics
import com.algora.app.feature.interviewprep.InterviewPrepCategories
import com.algora.app.feature.interviewprep.InterviewPrepTopics
import com.algora.app.feature.machinelearning.MachineLearningTopics
import com.algora.app.feature.nlp.NlpTopics
import com.algora.app.feature.practice.problems.ProblemRegistry
import com.algora.app.feature.reinforcementlearning.ReinforcementLearningTopics

// Category-group definition for the progress dashboard. Titles/icons/colors come straight from
// docs/design/Algora.dc.html's isProgress block (gcols / gicons maps).
private data class ProgressGroup(
    val title: String,
    val iconName: String,
    val color: Color,
    val topics: List<Topic>,
    val route: String,
)

private val patternTopics = InterviewPrepTopics.topics.filter { it.categoryId == InterviewPrepCategories.patterns.id }
private val interviewPrepTopics = InterviewPrepTopics.topics - patternTopics.toSet()

private val dsaGroups = listOf(
    ProgressGroup("Data Structures", "stack", Color(0xFF10B981), DataStructuresTopics.topics, Screen.DataStructures.route),
    ProgressGroup("Algorithms", "chip", SimColors.Blue, AlgorithmsTopics.topics, Screen.Algorithms.route),
    // Split to match where each topic is actually reachable: Patterns has its own screen now, so its
    // rows must not deep-link into Interview Prep, which no longer lists them.
    ProgressGroup("Patterns", "help", SimColors.Amber, patternTopics, PatternsRoute.ROUTE),
    ProgressGroup("Interview Prep", "mic", Color(0xFFEC4899), interviewPrepTopics, Screen.InterviewPrep.route),
    ProgressGroup("Analysis", "trend", SimColors.Violet, AnalysisTopics.topics, Screen.Analysis.route),
)

private val aiGroups = listOf(
    ProgressGroup("Machine Learning", "robot", Color(0xFF6366F1), MachineLearningTopics.topics, Screen.MachineLearning.route),
    ProgressGroup("Deep Learning", "network", Color(0xFFEC4899), DeepLearningTopics.topics, Screen.DeepLearning.route),
    ProgressGroup("NLP", "globe", Color(0xFF14B8A6), NlpTopics.topics, Screen.Nlp.route),
    ProgressGroup("Reinforcement Learning", "game", Color(0xFFF97316), ReinforcementLearningTopics.topics, Screen.ReinforcementLearning.route),
)

// Unlock ladder. Targets are counts of completed topics; the "halfway"/"finish" rungs scale with the
// track so the ladder means the same thing in both modes.
private data class Milestone(val title: String, val iconName: String, val color: Color, val target: Int)

private fun milestonesFor(total: Int): List<Milestone> = listOf(
    Milestone("First Steps", "check", Color(0xFF10B981), 1),
    Milestone("Explorer", "map", SimColors.Blue, 10),
    Milestone("Momentum", "trend", SimColors.Amber, 25),
    Milestone("Halfway", "target", SimColors.Violet, (total + 1) / 2),
    Milestone("Track Complete", "crown", Color(0xFFEC4899), total),
).filter { it.target in 1..total }.distinctBy { it.target }.sortedBy { it.target }

// Monday-first weekday index. Epoch day 0 was a Thursday, so +3 shifts the week origin to Monday.
private fun mondayIndex(epochDay: Long): Int = ((epochDay + 3) % 7).toInt()

private val weekLetters = listOf("M", "T", "W", "T", "F", "S", "S")

// Ported from docs/design/Algora.dc.html's isProgress block, then extended: the overall ring and the
// per-category cards are the mock's; the week strip and milestone ladder answer "am I moving?",
// which a static percentage cannot. Grouping follows the active mode's four categories (DSA vs AI).
@Composable
fun ProgressScreen(
    mode: AppMode,
    onCategoryClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val progressRepo = remember { ProgressRepository(context.progressDataStore) }
    val settings = remember { SettingsRepository(context.settingsDataStore) }
    val completedIds by progressRepo.completedTopicIds.collectAsState(initial = emptySet())
    val solvedIds by progressRepo.solvedProblemIds.collectAsState(initial = emptySet())
    val completionsByDay by progressRepo.completionsByDay.collectAsState(initial = emptyMap())
    val streak by settings.streak.collectAsState(initial = 0)
    val activeDays by settings.activeDays.collectAsState(initial = emptySet())

    val groups = if (mode == AppMode.DSA) dsaGroups else aiGroups
    val overallTotal = groups.sumOf { it.topics.size }
    val overallDone = groups.sumOf { g -> g.topics.count { it.id in completedIds } }
    val overallPct = if (overallTotal == 0) 0 else (overallDone * 100 / overallTotal)
    val modeLabel = if (mode == AppMode.DSA) "DSA TRACK" else "AI SIMULATION TRACK"
    val solvedCount = solvedIds.count { ProblemRegistry.get(it) != null }
    val milestones = remember(overallTotal) { milestonesFor(overallTotal) }

    // Ask for a Play rating once the track is past the threshold. This screen is the natural place:
    // the user opened it to look at their own progress, so the prompt lands on a good moment rather
    // than interrupting a lesson or a timed quiz. -1 is the still-loading value — asking before the
    // stored day arrives would re-prompt someone who was already asked.
    val activity = LocalActivity.current
    val reviewPromptedDay by settings.reviewPromptedDay.collectAsState(initial = -1L)
    LaunchedEffect(activity, overallPct, reviewPromptedDay) {
        val host = activity ?: return@LaunchedEffect
        if (reviewPromptedDay < 0L) return@LaunchedEffect
        AppReviewPrompt.maybeAsk(
            activity = host,
            settings = settings,
            progressPercent = overallPct,
            promptedDay = reviewPromptedDay.takeIf { it > 0L },
        )
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = ScreenGutter,
            end = ScreenGutter,
            top = 14.dp,
            bottom = ScreenBottomInset,
        ),
    ) {
        item {
            Text(
                "Your Progress",
                fontFamily = SpaceGrotesk,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                modifier = Modifier.padding(bottom = 14.dp),
            )
        }

        item {
            HeroCard(
                modeLabel = modeLabel,
                pct = overallPct,
                done = overallDone,
                total = overallTotal,
                // The problem bank is DSA-only, so on the AI track that cell would report someone
                // else's work; show what is left of this track instead.
                middleStat = if (mode == AppMode.DSA) {
                    "$solvedCount" to "Problems"
                } else {
                    "${overallTotal - overallDone}" to "Remaining"
                },
                streak = streak.coerceAtLeast(1),
            )
        }

        item {
            WeekCard(
                activeDays = activeDays,
                completionsByDay = completionsByDay,
                streak = streak.coerceAtLeast(1),
                settings = settings,
            )
        }

        item { SectionLabel("Milestones", Modifier.padding(top = 22.dp, bottom = 10.dp)) }

        item { MilestoneCard(milestones = milestones, done = overallDone) }

        item { SectionLabel("By category", Modifier.padding(top = 22.dp, bottom = 10.dp)) }

        items(groups) { group ->
            val total = group.topics.size
            val done = group.topics.count { it.id in completedIds }
            val pct = if (total == 0) 0 else (done * 100 / total)
            CategoryProgressCard(
                group = group,
                done = done,
                total = total,
                pct = pct,
                onClick = { onCategoryClick(group.route) },
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

@Composable
private fun CardSurface(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        content = content,
    )
}

// One card carries the whole headline: ring, track label, count, and the three numbers that used to
// each need their own tile. Completed/remaining are dropped — the ring and "x of y" already say it.
@Composable
private fun HeroCard(
    modeLabel: String,
    pct: Int,
    done: Int,
    total: Int,
    middleStat: Pair<String, String>,
    streak: Int,
) {
    CardSurface {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                OverallRing(pct = pct)
                Column(modifier = Modifier.weight(1f)) {
                    TrackChip(modeLabel)
                    Row(
                        modifier = Modifier.padding(top = 8.dp),
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        Text(
                            "$done",
                            fontFamily = SpaceGrotesk,
                            fontWeight = FontWeight.Bold,
                            fontSize = 28.sp,
                        )
                        Text(
                            " / $total",
                            fontFamily = SpaceGrotesk,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 2.dp),
                        )
                    }
                    Text(
                        "topics completed",
                        fontSize = 12.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.outline),
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatCell("$done", "Topics", Modifier.weight(1f))
                StatDivider()
                StatCell(middleStat.first, middleStat.second, Modifier.weight(1f))
                StatDivider()
                StatCell("$streak", "Day streak", Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun TrackChip(label: String) {
    val accent = MaterialTheme.colorScheme.primary
    Text(
        label,
        color = accent,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.1.sp,
        modifier = Modifier
            .background(accent.copy(alpha = 0.14f), RoundedCornerShape(8.dp))
            .padding(horizontal = 9.dp, vertical = 4.dp),
    )
}

@Composable
private fun StatCell(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontFamily = SpaceGrotesk, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text(
            label,
            fontSize = 11.5.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

@Composable
private fun StatDivider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(26.dp)
            .background(MaterialTheme.colorScheme.outline),
    )
}

// Accent→tertiary sweep so the ring reads as filled even at low percentages; the canvas is rotated
// rather than the gradient so 0% starts at 12 o'clock.
@Composable
private fun OverallRing(pct: Int) {
    val accent = MaterialTheme.colorScheme.primary
    val accent2 = MaterialTheme.colorScheme.tertiary
    val track = MaterialTheme.colorScheme.outlineVariant
    val sweep by animateFloatAsState(targetValue = pct * 3.6f, label = "overallSweep")

    Box(modifier = Modifier.size(104.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize().rotate(-90f)) {
            val stroke = 10.dp.toPx()
            val inset = stroke / 2
            val arcSize = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke)
            val topLeft = androidx.compose.ui.geometry.Offset(inset, inset)
            drawArc(
                color = track,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke),
            )
            if (sweep > 0f) {
                drawArc(
                    brush = Brush.sweepGradient(listOf(accent, accent2, accent)),
                    startAngle = 0f,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "$pct%",
                fontFamily = SpaceGrotesk,
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp,
            )
            Text(
                "COMPLETE",
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// Mon–Sun. A cell shows the topics finished that day (real completion dates); a day that was opened
// without finishing anything gets a tinted check, an untouched day stays hollow, today is ringed.
@Composable
private fun WeekCard(
    activeDays: Set<Long>,
    completionsByDay: Map<Long, Int>,
    streak: Int,
    settings: SettingsRepository,
) {
    val today = remember { System.currentTimeMillis() / 86_400_000L }
    val weekStart = today - mondayIndex(today)
    val accent = MaterialTheme.colorScheme.primary
    // A streak of n means the n days ending today were active by definition. Unioning them in keeps
    // the strip consistent with the streak label on installs that predate the day-history key.
    val days = remember(activeDays, streak, today) {
        activeDays + (0 until streak).map { today - it }
    }
    val weekTotal = (0..6).sumOf { completionsByDay[weekStart + it] ?: 0 }

    CardSurface(modifier = Modifier.padding(top = 12.dp)) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 15.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("This week", fontWeight = FontWeight.Bold, fontSize = 14.5.sp)
                    Text(
                        "  $weekTotal ${if (weekTotal == 1) "topic" else "topics"}",
                        fontSize = 12.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.LocalFireDepartment,
                        contentDescription = null,
                        tint = Color(0xFFFB923C),
                        modifier = Modifier.size(15.dp),
                    )
                    Text(
                        " $streak ${if (streak == 1) "day" else "days"} in a row",
                        fontSize = 12.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                weekLetters.forEachIndexed { index, letter ->
                    val day = weekStart + index
                    val isToday = day == today
                    val finished = completionsByDay[day] ?: 0
                    val isActive = day in days
                    val future = day > today
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(width = 30.dp, height = 34.dp)
                                .background(
                                    when {
                                        finished > 0 -> accent
                                        isActive -> accent.copy(alpha = 0.18f)
                                        else -> MaterialTheme.colorScheme.outlineVariant
                                    },
                                    RoundedCornerShape(10.dp),
                                )
                                .then(
                                    if (isToday && finished == 0) {
                                        Modifier.border(1.5.dp, accent, RoundedCornerShape(10.dp))
                                    } else {
                                        Modifier
                                    },
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            when {
                                finished > 0 -> Text(
                                    "$finished",
                                    fontFamily = SpaceGrotesk,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                )
                                isActive -> Icon(
                                    Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = accent,
                                    modifier = Modifier.size(14.dp),
                                )
                            }
                        }
                        Text(
                            letter,
                            fontSize = 11.5.sp,
                            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                            color = when {
                                isToday -> accent
                                future -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.padding(top = 7.dp),
                        )
                    }
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp)
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.outline),
            )
            StreakFreezeRow(settings = settings, modifier = Modifier.padding(top = 12.dp))
        }
    }
}

// A missed day would otherwise reset the streak counter above to 1; watching an ad here banks a
// freeze that recordActivityToday() spends automatically on the next single-day gap. Self-contained
// like LockedTopicBody's ad row — owns its own Activity/ads/scope rather than threading them through
// ProgressScreen.
@Composable
private fun StreakFreezeRow(settings: SettingsRepository, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val ads = remember { AdsProvider.get(context) }
    val scope = rememberCoroutineScope()
    val freezes by settings.streakFreezes.collectAsState(initial = 0)
    val adReady by ads.isReady.collectAsState()
    var awaitingAd by remember { mutableStateOf(false) }
    val full = freezes >= MAX_STREAK_FREEZES

    LaunchedEffect(Unit) { ads.preload(context) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = activity != null && !awaitingAd && !full) {
                val host = activity ?: return@clickable
                awaitingAd = true
                ads.show(
                    activity = host,
                    onReward = {
                        awaitingAd = false
                        scope.launch { settings.addStreakFreeze() }
                    },
                    onFailed = { awaitingAd = false },
                )
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.AcUnit,
            contentDescription = null,
            tint = Color(0xFF38BDF8),
            modifier = Modifier.size(15.dp),
        )
        Spacer(modifier = Modifier.size(6.dp))
        Text(
            "$freezes/$MAX_STREAK_FREEZES streak freezes",
            fontSize = 12.5.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        when {
            awaitingAd -> CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
            full -> Text("Full", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            else -> Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.PlayCircle,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = if (adReady) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.size(4.dp))
                Text(
                    "Watch ad",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (adReady) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// One stacked card instead of a horizontally clipped carousel: every rung is visible, and the rung
// actually in play carries the bar and the "n to go" count.
@Composable
private fun MilestoneCard(milestones: List<Milestone>, done: Int) {
    val nextTarget = milestones.firstOrNull { done < it.target }?.target

    CardSurface {
        Column(modifier = Modifier.padding(horizontal = 15.dp, vertical = 6.dp)) {
            milestones.forEachIndexed { index, milestone ->
                val unlocked = done >= milestone.target
                val isNext = milestone.target == nextTarget
                if (index > 0) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(MaterialTheme.colorScheme.outline),
                    )
                }
                MilestoneRow(milestone = milestone, done = done, unlocked = unlocked, isNext = isNext)
            }
        }
    }
}

@Composable
private fun MilestoneRow(milestone: Milestone, done: Int, unlocked: Boolean, isNext: Boolean) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(
                    if (unlocked) {
                        Brush.linearGradient(listOf(milestone.color, milestone.color.copy(alpha = 0.6f)))
                    } else {
                        Brush.linearGradient(
                            listOf(
                                MaterialTheme.colorScheme.outlineVariant,
                                MaterialTheme.colorScheme.outlineVariant,
                            ),
                        )
                    },
                    RoundedCornerShape(12.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (unlocked) resolveIcon(milestone.iconName) else Icons.Filled.Lock,
                contentDescription = null,
                tint = if (unlocked) Color.White else muted,
                modifier = Modifier.size(if (unlocked) 19.dp else 15.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                milestone.title,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = if (unlocked) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                },
            )
            Text(
                "Finish ${milestone.target} ${if (milestone.target == 1) "topic" else "topics"}",
                fontSize = 11.5.sp,
                color = muted,
                modifier = Modifier.padding(top = 1.dp),
            )
            if (isNext) {
                ProgressBar(
                    fraction = done.toFloat() / milestone.target,
                    color = milestone.color,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
        if (unlocked) {
            Icon(
                Icons.Filled.Check,
                contentDescription = "Unlocked",
                tint = milestone.color,
                modifier = Modifier.size(18.dp),
            )
        } else {
            Text(
                "${milestone.target - done} to go",
                fontSize = 11.5.sp,
                fontWeight = if (isNext) FontWeight.Bold else FontWeight.Normal,
                color = if (isNext) milestone.color else muted,
            )
        }
    }
}

@Composable
private fun CategoryProgressCard(
    group: ProgressGroup,
    done: Int,
    total: Int,
    pct: Int,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(bottom = 11.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .background(
                        Brush.linearGradient(listOf(group.color, group.color.copy(alpha = 0.6f))),
                        RoundedCornerShape(12.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    resolveIcon(group.iconName),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 7.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(group.title, fontWeight = FontWeight.Bold, fontSize = 14.5.sp)
                    Text("$pct%", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = group.color)
                }
                ProgressBar(fraction = if (total == 0) 0f else done.toFloat() / total, color = group.color)
                Text(
                    "$done / $total topics",
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            // The row opens the category, so it carries the mock's chevron affordance.
            Icon(
                resolveIcon("chev"),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

// Shared 7dp track+fill so the hero, milestone and category bars stay identical.
@Composable
private fun ProgressBar(fraction: Float, color: Color, modifier: Modifier = Modifier) {
    val animated by animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        label = "progressBar",
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(7.dp)
            .background(MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(99.dp)),
    ) {
        if (animated > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(animated)
                    .background(color, RoundedCornerShape(99.dp)),
            )
        }
    }
}
