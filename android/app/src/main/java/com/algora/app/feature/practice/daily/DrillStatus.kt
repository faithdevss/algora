package com.algora.app.feature.practice.daily

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.algora.app.core.data.TopicRegistry
import com.algora.app.core.data.entitlement.EntitlementRepository
import com.algora.app.core.data.entitlement.entitlementDataStore
import com.algora.app.core.data.progress.ProgressRepository
import com.algora.app.core.data.progress.progressDataStore
import com.algora.app.core.data.settings.SettingsRepository
import com.algora.app.core.data.settings.settingsDataStore
import com.algora.app.feature.interviewprep.quiz.QuizRegistry
import com.algora.app.feature.practice.problems.PracticeProblem
import com.algora.app.feature.practice.problems.ProblemRegistry
import com.algora.app.feature.review.DAILY_NEW_CARD_LIMIT
import com.algora.app.feature.review.allReviewCards
import com.algora.app.feature.review.reviewDeck
import com.algora.app.feature.review.reviewCounts
import kotlinx.coroutines.flow.first

/**
 * Today's drill and how far through it the learner is.
 *
 * Every step's completion is *derived* from state the app already keeps — the SM-2 queue, the solved
 * set, the quiz attempt log — rather than from a separate "step done" flag. Nothing can drift out of
 * sync with the real work, and closing the app mid-drill loses nothing.
 */
data class DrillStatus(
    val day: Long,
    val problem: PracticeProblem?,
    val questions: List<DrillQuestion>,
    val cardsWaiting: Int,
    val recallDone: Boolean,
    val solveDone: Boolean,
    val drillDone: Boolean,
    // False until the one-shot reads behind the drill have landed; the UI shows a resting state
    // rather than claiming "0 of 3 done" against a plan it has not loaded yet.
    val ready: Boolean,
) {
    val stepCount: Int get() = 3
    val doneCount: Int get() = listOf(recallDone, solveDone, drillDone).count { it }
    val allDone: Boolean get() = doneCount == stepCount
}

@Composable
fun rememberDrillStatus(): DrillStatus {
    val context = LocalContext.current
    val settings = remember { SettingsRepository(context.settingsDataStore) }
    val progress = remember { ProgressRepository(context.progressDataStore) }
    val entitlements = remember { EntitlementRepository(context.entitlementDataStore) }

    val today = System.currentTimeMillis() / 86_400_000L
    val allCards = remember { allReviewCards() }

    val solvedIds by progress.solvedProblemIds.collectAsState(initial = emptySet())
    val attempts by settings.quizAttempts.collectAsState(initial = emptyMap())
    val srs by settings.srs.collectAsState(initial = null)
    val introducedToday by settings.newCardsIntroduced(today).collectAsState(initial = 0)

    var problem by remember { mutableStateOf<PracticeProblem?>(null) }
    var questions by remember { mutableStateOf<List<DrillQuestion>>(emptyList()) }
    var ready by remember { mutableStateOf(false) }

    // One-shot reads (`first()`), not collected state: the drill must be built from settled values.
    // Rebuilding it from live flows would reroll the set the moment a step was completed.
    LaunchedEffect(today) {
        val isPremium = entitlements.isPremium.first()
        val unlocks = entitlements.adUnlocks.first()
        // A locked quiz's questions would dead-end at the paywall, so the drill never samples them.
        val accessible = QuizRegistry.all.filter { (topicId, _) ->
            TopicRegistry.find(topicId)?.isPremium != true || isPremium || topicId in unlocks
        }
        questions = pickQuestions(today, accessible, settings.quizAttempts.first())

        val stored = settings.drillProblem.first()
        problem = if (stored != null && stored.first == today) {
            ProblemRegistry.get(stored.second)
        } else {
            pickProblem(today, ProblemRegistry.all, progress.solvedProblemIds.first())
                ?.also { settings.setDrillProblem(today, it.id) }
        }
        ready = true
    }

    val counts = srs?.let {
        reviewCounts(
            cards = reviewDeck(allCards, it),
            srs = it,
            today = today,
            newAllowance = (DAILY_NEW_CARD_LIMIT - introducedToday).coerceAtLeast(0),
        )
    }

    return DrillStatus(
        day = today,
        problem = problem,
        questions = questions,
        cardsWaiting = counts?.waiting ?: 0,
        // "Nothing waiting" is the same bar the Practice hub already calls "All caught up".
        recallDone = counts != null && counts.waiting == 0,
        // A drill with no problem left to give (whole bank solved) counts as done, not as blocked.
        solveDone = ready && (problem == null || problem?.id in solvedIds),
        drillDone = (ready && questions.isEmpty()) ||
            attempts[DAILY_DRILL_QUIZ_ID].orEmpty().any { it.day == today },
        ready = ready,
    )
}
