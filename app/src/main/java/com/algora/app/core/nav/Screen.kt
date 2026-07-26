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
    const val PATTERN = "topic_detail/{$ARG}"
    fun route(topicId: String) = "topic_detail/$topicId"
}

object FlashcardsRoute {
    const val ROUTE = "flashcards"
}

object ReviewRoute {
    const val ROUTE = "review"
}

object ProgressRoute {
    const val ROUTE = "progress"
}

// Appearance preferences (theme mode + accent), reached from the home topbar gear.
object SettingsRoute {
    const val ROUTE = "settings"
}

// Simulations tab lands on a catalog of every topic that ships a runnable interactive lab.
object SimulationsRoute {
    const val ROUTE = "simulations"
}
