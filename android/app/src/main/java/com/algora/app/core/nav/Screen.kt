package com.algora.app.core.nav

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.ui.graphics.vector.ImageVector

// Two top-level app modes, each with its own bottom-nav tab set (features.md §1).
enum class AppMode { DSA, AI }

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    // DSA mode
    data object InterviewPrep : Screen("interview_prep", "Interview Prep", Icons.Filled.School)
    data object DataStructures : Screen("data_structures", "Data Structures", Icons.AutoMirrored.Filled.MenuBook)
    data object Home : Screen("home", "Home", Icons.Filled.Home)
    data object Algorithms : Screen("algorithms", "Algorithms", Icons.Filled.Functions)
    data object Analysis : Screen("analysis", "Analysis", Icons.Filled.Analytics)

    // AI mode (short labels so five tabs fit a phone-width bar)
    data object MachineLearning : Screen("machine_learning", "ML", Icons.Filled.SmartToy)
    data object DeepLearning : Screen("deep_learning", "DL", Icons.Filled.Hub)
    data object Nlp : Screen("nlp", "NLP", Icons.Filled.Public)
    data object ReinforcementLearning : Screen("reinforcement_learning", "RL", Icons.Filled.SportsEsports)
}

// docs/design/Algora.dc.html's bottom bar (navDefs) is a FIXED set of four global destinations —
// identical in both DSA and AI modes, unlike the category tabs. Icons use the mock's SVG names
// (book/flask/target/chart) resolved through resolveIcon(). Tap targets are mode-aware and handled
// in MainActivity: Learning → Home, Simulations → the mode's flagship sim topic, Practice → Interview
// Prep (mock forces DSA), Progress → the progress dashboard.
enum class NavTab(val label: String, val iconName: String) {
    LEARNING("Learning", "book"),
    SIMULATIONS("Simulations", "flask"),
    PRACTICE("Practice", "target"),
    PROGRESS("Progress", "chart"),
}

object TopicDetailRoute {
    const val ARG = "topicId"
    // A quiz opened from a set list starts in the list's mode ("learn" / "interview") instead of asking.
    const val QUIZ_MODE = "quizMode"
    const val PATTERN = "topic_detail/{$ARG}?$QUIZ_MODE={$QUIZ_MODE}"
    fun route(topicId: String) = "topic_detail/$topicId"
    fun quiz(topicId: String, learn: Boolean) = "topic_detail/$topicId?$QUIZ_MODE=${if (learn) "learn" else "interview"}"
}

// The single flashcard surface, scheduled by SM-2. Replaced the old unscheduled FlashcardsRoute,
// which looped the same deck forever.
object ReviewRoute {
    const val ROUTE = "review"
}

object ProgressRoute {
    const val ROUTE = "progress"
}

// Practice tab hub — everything you *do* rather than read: flashcards, spaced repetition, quizzes,
// problem solving, interview prep. Flashcards/SR used to hang off Home (Learning); they belong here.
object PracticeRoute {
    const val ROUTE = "practice"
}

// The Patterns category of Interview Prep on its own, reached from Home's DSA Quick Access grid.
// Deliberately narrower than Screen.InterviewPrep: pattern guides only, no quizzes or mock rounds.
object PatternsRoute {
    const val ROUTE = "patterns"
}

// Catalog of every timed quiz in QuizRegistry.
object QuizCatalogRoute {
    const val ROUTE = "quizzes"
}

// A drill built from the learner's weakest patterns, opened from the quiz catalog.
object WeakSpotDrillRoute {
    const val ROUTE = "weak_spot_drill"
}

// One mixed practice session per day: recall queue, one problem, a short sampled question set.
object DailyDrillRoute {
    const val ROUTE = "daily_drill"
}

// Problem bank: list grouped by pattern, then a per-problem workspace.
object ProblemsRoute {
    const val ROUTE = "problems"
}

object ProblemDetailRoute {
    const val ARG = "problemId"
    const val PATTERN = "problem/{$ARG}"
    fun route(problemId: String) = "problem/$problemId"
}

// Appearance preferences (theme mode + accent), reached from the home topbar gear.
object SettingsRoute {
    const val ROUTE = "settings"
}

// Simulations tab lands on a catalog of every topic that ships a runnable interactive lab.
object SimulationsRoute {
    const val ROUTE = "simulations"
}

// A Simulations-tab section on its own page, by Section name.
object SimulationSectionRoute {
    const val ARG = "section"
    const val PATTERN = "simulations/section/{$ARG}"
    fun route(section: String) = "simulations/section/$section"
}

// A catalog row opens the lab alone — same topic id as TopicDetailRoute, sim-only chrome.
object SimulationDetailRoute {
    const val ARG = "topicId"
    const val PATTERN = "simulation/{$ARG}"
    fun route(topicId: String) = "simulation/$topicId"
}

// Paywall (mock's isPremium block), reached from Home's upsell button or a locked topic.
object PremiumRoute {
    const val ROUTE = "premium"
}
