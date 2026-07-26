package com.algora.app

import android.graphics.Color.TRANSPARENT
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.algora.app.core.data.settings.AccentColor
import com.algora.app.core.data.settings.SettingsRepository
import com.algora.app.core.data.settings.ThemeMode
import com.algora.app.core.data.settings.settingsDataStore
import com.algora.app.core.nav.AppMode
import com.algora.app.core.nav.FlashcardsRoute
import com.algora.app.core.nav.NavGraph
import com.algora.app.core.nav.NavTab
import com.algora.app.core.nav.ProgressRoute
import com.algora.app.core.nav.Screen
import com.algora.app.core.nav.SimulationsRoute
import com.algora.app.core.ui.components.resolveIcon
import com.algora.app.core.ui.theme.AlgoraTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val context = LocalContext.current
            val settings = remember { SettingsRepository(context.settingsDataStore) }
            val themeMode by settings.themeMode.collectAsState(initial = ThemeMode.SYSTEM)
            val accent by settings.accent.collectAsState(initial = AccentColor.DEFAULT)
            LaunchedEffect(Unit) { settings.recordActivityToday() }

            val dark = when (themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            // Re-applied on every theme change so status/nav bar icon contrast follows the in-app
            // toggle, not just the system setting.
            SideEffect {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(TRANSPARENT, TRANSPARENT) { dark },
                    navigationBarStyle = SystemBarStyle.auto(TRANSPARENT, TRANSPARENT) { dark },
                )
            }

            AlgoraTheme(darkTheme = dark, accent = accent) {
                AlgoraApp()
            }
        }
    }
}

@Composable
fun AlgoraApp() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    // Topic/sim detail keeps the bottom bar (mock shows nav on topic pages). Only Flashcards, which
    // owns its own back affordance, goes full-screen.
    val onFullScreen = currentDestination?.hierarchy?.any { it.route == FlashcardsRoute.ROUTE } == true

    var mode by remember { mutableStateOf(AppMode.DSA) }

    // Fixed four-destination bar from docs/design/Algora.dc.html (navDefs) — identical in both modes.
    fun navigate(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }
    fun onRoute(route: String) = currentDestination?.hierarchy?.any { it.route == route } == true

    Scaffold(
        bottomBar = {
            if (!onFullScreen) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    NavTab.entries.forEach { tab ->
                        val selected = when (tab) {
                            NavTab.LEARNING -> onRoute(Screen.Home.route)
                            NavTab.SIMULATIONS -> onRoute(SimulationsRoute.ROUTE)
                            NavTab.PRACTICE -> onRoute(Screen.InterviewPrep.route)
                            NavTab.PROGRESS -> onRoute(ProgressRoute.ROUTE)
                        }
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                when (tab) {
                                    NavTab.LEARNING -> navigate(Screen.Home.route)
                                    NavTab.SIMULATIONS -> navigate(SimulationsRoute.ROUTE)
                                    NavTab.PRACTICE -> {
                                        mode = AppMode.DSA
                                        navigate(Screen.InterviewPrep.route)
                                    }
                                    NavTab.PROGRESS -> navigate(ProgressRoute.ROUTE)
                                }
                            },
                            icon = { Icon(resolveIcon(tab.iconName), contentDescription = tab.label) },
                            label = { Text(tab.label) },
                            // Default M3 baseline colors ignore the accent; bind them to it.
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavGraph(
            navController = navController,
            mode = mode,
            onModeChange = { mode = it },
            modifier = Modifier.padding(innerPadding),
        )
    }
}
