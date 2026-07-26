package com.algora.app.core.nav

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.algora.app.feature.algorithms.AlgorithmsScreen
import com.algora.app.feature.analysis.AnalysisScreen
import com.algora.app.feature.datastructures.DataStructuresScreen
import com.algora.app.feature.deeplearning.DeepLearningScreen
import com.algora.app.feature.flashcards.FlashcardScreen
import com.algora.app.feature.home.HomeScreen
import com.algora.app.feature.interviewprep.InterviewPrepScreen
import com.algora.app.feature.machinelearning.MachineLearningScreen
import com.algora.app.feature.nlp.NlpScreen
import com.algora.app.feature.progress.ProgressScreen
import com.algora.app.feature.reinforcementlearning.ReinforcementLearningScreen
import com.algora.app.feature.review.ReviewScreen
import com.algora.app.feature.settings.SettingsScreen
import com.algora.app.feature.simulations.SimulationsScreen
import com.algora.app.feature.topics.TopicDetailScreen

@Composable
fun NavGraph(
    navController: NavHostController,
    mode: AppMode,
    onModeChange: (AppMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val openTopic: (String) -> Unit = { topicId -> navController.navigate(TopicDetailRoute.route(topicId)) }
    // Category browsers are entered from Home (or the Practice tab), which stays on the back stack.
    val goBack: () -> Unit = { navController.popBackStack() }

    NavHost(navController = navController, startDestination = Screen.Home.route, modifier = modifier) {
        // DSA mode
        composable(Screen.InterviewPrep.route) { InterviewPrepScreen(onTopicClick = openTopic, onBack = goBack) }
        composable(Screen.DataStructures.route) { DataStructuresScreen(onTopicClick = openTopic, onBack = goBack) }
        composable(Screen.Algorithms.route) { AlgorithmsScreen(onTopicClick = openTopic, onBack = goBack) }
        composable(Screen.Analysis.route) { AnalysisScreen(onTopicClick = openTopic, onBack = goBack) }

        // AI mode
        composable(Screen.MachineLearning.route) { MachineLearningScreen(onTopicClick = openTopic, onBack = goBack) }
        composable(Screen.DeepLearning.route) { DeepLearningScreen(onTopicClick = openTopic, onBack = goBack) }
        composable(Screen.Nlp.route) { NlpScreen(onTopicClick = openTopic, onBack = goBack) }
        composable(Screen.ReinforcementLearning.route) { ReinforcementLearningScreen(onTopicClick = openTopic, onBack = goBack) }

        // Shared dashboard — hosts the DSA/AI mode switch
        composable(Screen.Home.route) {
            HomeScreen(
                mode = mode,
                onModeChange = onModeChange,
                onNavigate = { route ->
                    navController.navigate(route) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                onTopicClick = openTopic,
            )
        }

        composable(FlashcardsRoute.ROUTE) {
            FlashcardScreen(onBack = { navController.popBackStack() })
        }

        composable(SettingsRoute.ROUTE) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }

        composable(ReviewRoute.ROUTE) {
            ReviewScreen(onBack = { navController.popBackStack() })
        }

        // Progress dashboard — mock's fixed Progress nav destination (isProgress block).
        composable(ProgressRoute.ROUTE) {
            ProgressScreen(mode = mode)
        }

        // Simulations catalog — every topic with a runnable interactive lab.
        composable(SimulationsRoute.ROUTE) {
            SimulationsScreen(onTopicClick = openTopic)
        }

        composable(
            route = TopicDetailRoute.PATTERN,
            arguments = listOf(navArgument(TopicDetailRoute.ARG) { type = NavType.StringType }),
        ) { backStackEntry ->
            val topicId = backStackEntry.arguments?.getString(TopicDetailRoute.ARG).orEmpty()
            TopicDetailScreen(
                topicId = topicId,
                onBack = { navController.popBackStack() },
                onTopicClick = openTopic,
            )
        }
    }
}
