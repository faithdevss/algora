package com.algora.app.feature.review

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.algora.app.core.data.TopicRegistry
import com.algora.app.core.data.settings.SettingsRepository
import com.algora.app.core.data.settings.SrsCard
import com.algora.app.core.data.settings.settingsDataStore
import com.algora.app.core.ui.theme.SimColors
import com.algora.app.core.ui.theme.SpaceGrotesk
import com.algora.app.feature.topics.content.TopicContentProvider
import kotlinx.coroutines.launch

// `prompt` is the front of the card. Takeaway cards have none — they ask the generic "recall a key
// takeaway" — while curated deck cards carry their own question.
data class ReviewCard(
    val key: String,
    val topicName: String,
    val takeaway: String,
    val prompt: String? = null,
)

// Every takeaway across every authored topic, keyed stably as "topicId#index", plus the curated
// interview decks from FlashcardDecks.kt.
fun allReviewCards(): List<ReviewCard> =
    TopicContentProvider.all.flatMap { (topicId, content) ->
        val name = TopicRegistry.find(topicId)?.name ?: topicId
        content.takeaways.mapIndexed { i, t -> ReviewCard("$topicId#$i", name, t) }
    } + curatedFlashcards()

// How many cards a "study ahead" session pulls forward when nothing is actually due.
private const val STUDY_AHEAD_BATCH = 20

// Never-seen cards introduced per day. Without a cap the first session queues every card in the app,
// and each one graded today comes back tomorrow — a first-day flood becomes a permanent daily load.
const val DAILY_NEW_CARD_LIMIT = 20

// Card counts for a given day, shared with the Practice hub so its row can show what is waiting.
// `new` is already capped by the day's remaining allowance; `newHeldBack` is the rest.
data class ReviewCounts(val due: Int, val new: Int, val scheduled: Int, val newHeldBack: Int = 0) {
    val waiting: Int get() = due + new
}

fun reviewCounts(
    cards: List<ReviewCard>,
    srs: Map<String, SrsCard>,
    today: Long,
    newAllowance: Int = Int.MAX_VALUE,
): ReviewCounts {
    var due = 0
    var new = 0
    for (card in cards) {
        val state = srs[card.key]
        when {
            state == null -> new++
            state.dueDay <= today -> due++
        }
    }
    val allowed = new.coerceAtMost(newAllowance.coerceAtLeast(0))
    return ReviewCounts(
        due = due,
        new = allowed,
        scheduled = cards.size - due - new,
        newHeldBack = new - allowed,
    )
}

// The single flashcard surface. Cards are scheduled by SM-2 rather than looped endlessly: grading a
// card pushes it days or weeks out, so a deck you know shrinks to nothing instead of repeating.
// When nothing is due, "Study ahead" pulls the soonest-scheduled cards forward on request.
@Composable
fun ReviewScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val settings = remember { SettingsRepository(context.settingsDataStore) }
    val scope = rememberCoroutineScope()
    val allCards = remember { allReviewCards() }
    val today = System.currentTimeMillis() / 86_400_000L

    // null = still loading state from DataStore.
    val srsMap by settings.srs.collectAsState(initial = null)
    val introducedToday by settings.newCardsIntroduced(today).collectAsState(initial = null)

    // Freeze the queue once, when state first loads, so grading doesn't reshuffle mid-session.
    var queue by remember { mutableStateOf<List<ReviewCard>?>(null) }
    var counts by remember { mutableStateOf(ReviewCounts(0, 0, 0)) }
    var studyingAhead by remember { mutableStateOf(false) }

    if (queue == null && srsMap != null && introducedToday != null) {
        val map = srsMap!!
        val remainingNew = (DAILY_NEW_CARD_LIMIT - introducedToday!!).coerceAtLeast(0)
        counts = reviewCounts(allCards, map, today, remainingNew)
        // Due cards first — they are the ones at risk — then as many never-seen cards as today's
        // allowance still permits.
        val due = allCards.filter { map[it.key]?.let { s -> s.dueDay <= today } == true }.shuffled()
        val fresh = allCards.filter { map[it.key] == null }.shuffled().take(remainingNew)
        queue = due + fresh
    }

    var index by remember { mutableIntStateOf(0) }
    var flipped by remember { mutableStateOf(false) }
    var reviewed by remember { mutableIntStateOf(0) }

    // Pulls the soonest-scheduled cards forward. Grading them reschedules normally from today.
    fun studyAhead() {
        val map = srsMap ?: return
        queue = allCards
            .mapNotNull { card -> map[card.key]?.let { card to it.dueDay } }
            .sortedBy { it.second }
            .take(STUDY_AHEAD_BATCH)
            .map { it.first }
        index = 0
        flipped = false
        reviewed = 0
        studyingAhead = true
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Header(onBack)

        val cards = queue
        when {
            cards == null -> CenterMessage("Loading cards…")

            cards.isEmpty() -> CaughtUp(
                message = "All caught up — nothing due right now. 🎉",
                scheduled = counts.scheduled,
                newHeldBack = counts.newHeldBack,
                onStudyAhead = ::studyAhead,
            )

            index >= cards.size -> CaughtUp(
                message = "Session complete — $reviewed card${if (reviewed == 1) "" else "s"} reviewed. 🎉",
                scheduled = if (studyingAhead) 0 else counts.scheduled,
                newHeldBack = if (studyingAhead) 0 else counts.newHeldBack,
                onStudyAhead = if (studyingAhead) null else ::studyAhead,
            )

            else -> {
                val card = cards[index]
                Text(
                    if (studyingAhead) {
                        "Studying ahead · ${index + 1} of ${cards.size}"
                    } else {
                        "${index + 1} of ${cards.size} · ${counts.due} due, ${counts.new} new"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )

                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .clickable { flipped = !flipped },
                    shape = RoundedCornerShape(20.dp),
                    color = if (flipped) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            card.topicName,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontFamily = SpaceGrotesk,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(modifier = Modifier.size(18.dp))
                        if (flipped) {
                            // A deck card keeps its question visible above the answer, so the recall
                            // being graded stays on screen.
                            if (card.prompt != null) {
                                Text(
                                    card.prompt,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                )
                                Spacer(modifier = Modifier.size(14.dp))
                            }
                            Text(card.takeaway, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
                        } else {
                            Text(
                                card.prompt ?: "Recall a key takeaway",
                                style = if (card.prompt != null) {
                                    MaterialTheme.typography.titleLarge
                                } else {
                                    MaterialTheme.typography.bodyLarge
                                },
                                color = if (card.prompt != null) {
                                    MaterialTheme.colorScheme.onSurface
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                textAlign = TextAlign.Center,
                            )
                            Spacer(modifier = Modifier.size(10.dp))
                            Text("Tap to reveal", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                if (flipped) {
                    // The grade is what sets the next due date — this is what stops a known card
                    // from coming back tomorrow.
                    fun grade(quality: Int) {
                        scope.launch { settings.reviewCard(card.key, quality) }
                        reviewed++
                        index++
                        flipped = false
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        GradeButton("Again", SimColors.Red, Modifier.weight(1f)) { grade(2) }
                        GradeButton("Good", SimColors.Blue, Modifier.weight(1f)) { grade(4) }
                        GradeButton("Easy", SimColors.Green, Modifier.weight(1f)) { grade(5) }
                    }
                    Text(
                        "Again brings it back tomorrow · Good and Easy push it further out",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        textAlign = TextAlign.Center,
                    )
                } else {
                    Text(
                        "Tap the card to reveal, then grade your recall.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun CaughtUp(message: String, scheduled: Int, newHeldBack: Int, onStudyAhead: (() -> Unit)?) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp),
        ) {
            Text(
                message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (newHeldBack > 0) {
                Spacer(modifier = Modifier.size(10.dp))
                Text(
                    "Daily limit of $DAILY_NEW_CARD_LIMIT new cards reached — $newHeldBack more unlock tomorrow.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            if (onStudyAhead != null && scheduled > 0) {
                Spacer(modifier = Modifier.size(10.dp))
                Text(
                    "$scheduled card${if (scheduled == 1) "" else "s"} scheduled for later.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.size(16.dp))
                OutlinedButton(onClick = onStudyAhead) { Text("Study ahead") }
            }
        }
    }
}

@Composable
private fun GradeButton(label: String, color: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = Color.White),
        modifier = modifier,
    ) { Text(label) }
}

@Composable
private fun CenterMessage(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(32.dp),
        )
    }
}

@Composable
private fun Header(onBack: () -> Unit) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text(
                "Flashcards",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.size(48.dp))
        }
        HorizontalDivider()
    }
}
