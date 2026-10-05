package com.algora.app.feature.practice

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.algora.app.core.data.model.Topic
import com.algora.app.core.data.settings.QuizAttempt
import com.algora.app.core.data.settings.bestCorrect
import com.algora.app.core.data.settings.bestPercent
import com.algora.app.core.data.settings.ofLength
import com.algora.app.core.ui.components.AdUnlockableLockIcon
import com.algora.app.core.ui.theme.IBMPlexMono
import com.algora.app.core.ui.theme.LocalDarkTheme
import com.algora.app.core.ui.theme.SimColors
import com.algora.app.feature.interviewprep.quiz.QuizRegistry

// The pieces the Quizzes and Interview Prep lists share: a large-title header with a back link, the
// Interview / Learn switch that decides how every set on the page runs, and a grouped card of sets, each
// with its done mark, size, best score and a play button that starts it in that mode. iOS: QuizSetList.swift.

internal fun quizScoreColor(percent: Int): Color = when {
    percent >= 80 -> SimColors.Green
    percent >= 60 -> SimColors.Amber
    else -> SimColors.Red
}

internal fun relativeQuizDay(day: Long, today: Long): String = when (val ago = today - day) {
    0L -> "today"
    1L -> "yesterday"
    in 2L..30L -> "${ago}d ago"
    else -> "a while ago"
}

/** Which mode the lists start sets in; one choice shared by both pages and kept across launches. */
@Composable
internal fun rememberQuizListLearnMode(): MutableState<Boolean> {
    val prefs = LocalContext.current.getSharedPreferences("quiz_lists", Context.MODE_PRIVATE)
    val state = remember { mutableStateOf(prefs.getBoolean("learn", false)) }
    return remember {
        object : MutableState<Boolean> by state {
            override var value: Boolean
                get() = state.value
                set(v) {
                    state.value = v
                    prefs.edit().putBoolean("learn", v).apply()
                }
        }
    }
}

/** "‹ Practice", then a 34sp title. */
@Composable
internal fun LargeTitleHeader(title: String, back: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val primary = MaterialTheme.colorScheme.primary
    Column(modifier = modifier) {
        Row(modifier = Modifier.heightIn(min = 44.dp).clickable(onClickLabel = "Back to $back", onClick = onBack), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.AutoMirrored.Filled.ArrowBackIos, contentDescription = null, tint = primary, modifier = Modifier.size(20.dp))
            Text(back, color = primary, fontSize = 17.sp)
        }
        Text(title, fontSize = 34.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 2.dp))
    }
}

/** One 44dp row: "‹ Practice" on the left, the title centred — for lists that need the room below. */
@Composable
internal fun CompactNavBar(title: String, back: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val primary = MaterialTheme.colorScheme.primary
    Box(modifier = modifier.fillMaxWidth().heightIn(min = 44.dp).padding(top = 4.dp), contentAlignment = Alignment.Center) {
        Text(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        Row(
            modifier = Modifier.align(Alignment.CenterStart).heightIn(min = 44.dp).clickable(onClickLabel = "Back to $back", onClick = onBack),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBackIos, contentDescription = null, tint = primary, modifier = Modifier.size(20.dp))
            Text(back, color = primary, fontSize = 17.sp, maxLines = 1)
        }
    }
}

/** Interview (timed, scored) or Learn (untimed, explained), with what the choice means under it. */
@Composable
internal fun QuizModeToggle(learn: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth().background(SimColors.Tint, RoundedCornerShape(14.dp)).padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            ModeSegment("Interview", Icons.Filled.Timer, MaterialTheme.colorScheme.primary, !learn, Modifier.weight(1f)) { onChange(false) }
            ModeSegment("Learn", Icons.Filled.School, SimColors.Green, learn, Modifier.weight(1f)) { onChange(true) }
        }
        Text(
            if (learn) "No timer. Each answer is checked with the reason straight away. Not added to your best score."
            else "Timed like the real thing. Answers at the end, counts toward your best score.",
            fontSize = 15.sp, color = muted, modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 10.dp),
        )
    }
}

@Composable
private fun ModeSegment(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color, on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val dark = LocalDarkTheme.current
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = modifier
            .heightIn(min = 46.dp)
            .then(if (on) Modifier.shadow(1.dp, RoundedCornerShape(11.dp)).background(if (dark) Color(0xFF3A3F4C) else Color.White, RoundedCornerShape(11.dp)) else Modifier)
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = if (on) tint else muted, modifier = Modifier.size(18.dp))
        Text(
            title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
            color = if (on) MaterialTheme.colorScheme.onSurface else muted, modifier = Modifier.padding(start = 8.dp),
        )
    }
}

/** One row of a set list. */
internal class QuizSetItem(
    val topicId: String,
    val title: String,
    /** "15 questions · 11 min"; null for a guide that is not a quiz. */
    val meta: String?,
    /** The guide's one-liner when there is no quiz. */
    val note: String?,
    val done: Boolean,
    val best: Pair<String, Int>?,
    val locked: Boolean,
    val adUnlockable: Boolean,
)

internal fun quizSetItem(
    topic: Topic,
    completed: Set<String>,
    attempts: Map<String, List<QuizAttempt>>,
    locked: Boolean,
    adUnlockable: Boolean,
): QuizSetItem {
    val quiz = QuizRegistry.get(topic.id)
        ?: return QuizSetItem(topic.id, topic.name, null, topic.tagline, topic.id in completed, null, locked, adUnlockable)
    val today = System.currentTimeMillis() / 86_400_000L
    val history = attempts[topic.id].orEmpty().ofLength(quiz.questions.size)
    return QuizSetItem(
        topicId = topic.id,
        title = quiz.title,
        meta = "${quiz.questions.size} questions · ${quiz.timeLimitSeconds / 60} min",
        note = null,
        done = topic.id in completed || history.isNotEmpty(),
        best = history.firstOrNull()?.let { "Best ${history.bestCorrect}/${quiz.questions.size} · ${relativeQuizDay(it.day, today)}" to history.bestPercent },
        locked = locked,
        adUnlockable = adUnlockable,
    )
}

/**
 * Rows in one card, divided. A quiz's row starts it in the list's mode; a guide, or a set still locked,
 * opens its topic page, which carries the guide or the unlock.
 */
@Composable
internal fun QuizSetCard(items: List<QuizSetItem>, learn: Boolean, onOpen: (String) -> Unit, onStart: (String, Boolean) -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column {
            items.forEachIndexed { i, item ->
                if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(start = 64.dp))
                QuizSetRow(item) { if (item.meta == null || item.locked) onOpen(item.topicId) else onStart(item.topicId, learn) }
            }
        }
    }
}

@Composable
private fun QuizSetRow(item: QuizSetItem, onClick: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = if (item.meta == null) "Open ${item.title}" else "Start ${item.title}", onClick = onClick)
            .padding(start = 18.dp, end = 16.dp, top = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .then(
                    if (item.done) Modifier.background(SimColors.Green, CircleShape)
                    else Modifier.border(2.dp, muted.copy(alpha = 0.45f), CircleShape),
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (item.done) Icon(Icons.Filled.Check, contentDescription = "Done", tint = Color.White, modifier = Modifier.size(16.dp))
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(item.title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            when {
                item.locked -> Text("Premium" + (item.meta?.let { " · $it" } ?: ""), fontSize = 14.sp, color = SimColors.Amber)
                item.meta != null -> Text(item.meta, fontFamily = IBMPlexMono, fontSize = 14.sp, color = primary)
                item.note != null -> Text(item.note, fontSize = 14.sp, color = muted, maxLines = 2)
            }
            item.best?.let { (text, percent) -> Text(text, fontSize = 15.sp, color = quizScoreColor(percent)) }
        }
        Box(
            modifier = Modifier.size(48.dp).background(primary.copy(alpha = 0.18f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            when {
                item.locked -> AdUnlockableLockIcon(size = 20.dp, adUnlockable = item.adUnlockable)
                item.meta == null -> Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = primary, modifier = Modifier.size(22.dp))
                else -> Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = primary, modifier = Modifier.size(22.dp))
            }
        }
    }
}
