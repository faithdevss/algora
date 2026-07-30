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
import com.algora.app.feature.home.HomeScreen
import com.algora.app.feature.interviewprep.InterviewPrepScreen
import com.algora.app.feature.interviewprep.PatternsScreen
import com.algora.app.feature.machinelearning.MachineLearningScreen
import com.algora.app.feature.nlp.NlpScreen
import com.algora.app.feature.practice.PracticeScreen
import com.algora.app.feature.practice.QuizCatalogScreen
import com.algora.app.feature.practice.daily.DailyDrillScreen
import com.algora.app.feature.practice.problems.ProblemDetailScreen
import com.algora.app.feature.practice.problems.ProblemListScreen
import com.algora.app.feature.premium.PremiumScreen
import com.algora.app.feature.progress.ProgressScreen
import com.algora.app.feature.reinforcementlearning.ReinforcementLearningScreen
import com.algora.app.feature.review.ReviewScreen
import com.algora.app.feature.settings.SettingsScreen
import com.algora.app.feature.simulations.SimulationDetailScreen
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
    val openSimulation: (String) -> Unit = { topicId -> navController.navigate(SimulationDetailRoute.route(topicId)) }
    // Category browsers are entered from Home (or the Practice tab), which stays on the back stack.
    val goBack: () -> Unit = { navController.popBackStack() }

    NavHost(navController = navController, startDestination = Screen.Home.route, modifier = modifier) {
        // DSA mode
        composable(Screen.InterviewPrep.route) { InterviewPrepScreen(onTopicClick = openTopic, onBack = goBack) }
        composable(PatternsRoute.ROUTE) { PatternsScreen(onTopicClick = openTopic, onBack = goBack) }
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

        // Practice hub — recall drills, quizzes, problem bank, interview prep.
        composable(PracticeRoute.ROUTE) {
            PracticeScreen(onNavigate = { route -> navController.navigate(route) })
        }

        composable(QuizCatalogRoute.ROUTE) {
            QuizCatalogScreen(onQuizClick = openTopic, onBack = goBack)
        }

        composable(DailyDrillRoute.ROUTE) {
            DailyDrillScreen(
                onNavigate = { route -> navController.navigate(route) },
                onProblemClick = { problemId -> navController.navigate(ProblemDetailRoute.route(problemId)) },
                onTopicClick = openTopic,
                onBack = goBack,
            )
        }

        composable(ProblemsRoute.ROUTE) {
            ProblemListScreen(
                onProblemClick = { problemId -> navController.navigate(ProblemDetailRoute.route(problemId)) },
                onBack = goBack,
            )
        }

        composable(
            route = ProblemDetailRoute.PATTERN,
            arguments = listOf(navArgument(ProblemDetailRoute.ARG) { type = NavType.StringType }),
        ) { backStackEntry ->
            val problemId = backStackEntry.arguments?.getString(ProblemDetailRoute.ARG).orEmpty()
            ProblemDetailScreen(problemId = problemId, onBack = goBack, onOpenTopic = openTopic)
        }

        composable(SettingsRoute.ROUTE) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }

        composable(ReviewRoute.ROUTE) {
            ReviewScreen(onBack = { navController.popBackStack() })
        }

        composable(PremiumRoute.ROUTE) {
            PremiumScreen(onBack = { navController.popBackStack() })
        }

        // Progress dashboard — mock's fixed Progress nav destination (isProgress block).
        composable(ProgressRoute.ROUTE) {
            // Category rows are shortcuts into that category's topic list.
            ProgressScreen(mode = mode, onCategoryClick = { navController.navigate(it) })
        }

        // Simulations catalog — every topic with a runnable interactive lab.
        composable(SimulationsRoute.ROUTE) {
            SimulationsScreen(onTopicClick = openSimulation)
        }

        // A lab on its own, without the surrounding topic write-up.
        composable(
            route = SimulationDetailRoute.PATTERN,
            arguments = listOf(navArgument(SimulationDetailRoute.ARG) { type = NavType.StringType }),
        ) { backStackEntry ->
            val topicId = backStackEntry.arguments?.getString(SimulationDetailRoute.ARG).orEmpty()
            SimulationDetailScreen(
                topicId = topicId,
                onBack = { navController.popBackStack() },
                onOpenTopic = openTopic,
                onGoPremium = { navController.navigate(PremiumRoute.ROUTE) },
            )
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
                onGoPremium = { navController.navigate(PremiumRoute.ROUTE) },
            )
        }
    }
}
