package com.algora.app

import android.Manifest
import android.graphics.Color.TRANSPARENT
import android.os.Build
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.algora.app.core.analytics.UserProperty
import com.algora.app.core.analytics.reminderOpened
import com.algora.app.core.analytics.rememberAnalytics
import com.algora.app.core.analytics.streakDay
import com.algora.app.core.billing.BillingProvider
import com.algora.app.core.data.entitlement.EntitlementRepository
import com.algora.app.core.data.entitlement.entitlementDataStore
import com.algora.app.core.data.settings.SettingsRepository
import com.algora.app.core.data.settings.ThemeMode
import com.algora.app.core.data.settings.settingsDataStore
import com.algora.app.core.nav.AppMode
import com.algora.app.core.nav.NavGraph
import com.algora.app.core.nav.NavTab
import com.algora.app.core.notify.DailyReminder
import com.algora.app.core.notify.DailyReminderWorker
import com.algora.app.core.notify.StudyReminder
import com.algora.app.core.notify.StudyReminderWorker
import com.algora.app.core.nav.PracticeRoute
import com.algora.app.core.nav.ProblemDetailRoute
import com.algora.app.core.nav.ProblemsRoute
import com.algora.app.core.nav.QuizCatalogRoute
import com.algora.app.core.nav.ReviewRoute
import com.algora.app.core.nav.ProgressRoute
import com.algora.app.core.nav.Screen
import com.algora.app.core.nav.SimulationsRoute
import com.algora.app.core.ui.components.resolveIcon
import com.algora.app.core.ui.theme.AlgoraTheme
import com.algora.app.core.ui.theme.accent
import kotlinx.coroutines.flow.first

class MainActivity : ComponentActivity() {

    // A reminder's tap target, held here rather than in the composition because it arrives on the
    // Intent — on a cold start via onCreate, and on a warm one via onNewIntent, which the
    // composition never sees.
    private val pendingDeepLink = mutableStateOf<DeepLink?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pendingDeepLink.value = deepLinkOf(intent)
        setContent {
            val context = LocalContext.current
            val settings = remember { SettingsRepository(context.settingsDataStore) }
            val themeMode by settings.themeMode.collectAsState(initial = ThemeMode.SYSTEM)
            // null accent = the "Auto" setting, resolved against the live mode below. Mode is
            // hoisted this high precisely so the theme — not just the Home header — can see it.
            val accentChoice by settings.accent.collectAsState(initial = null)
            var mode by rememberSaveable { mutableStateOf(AppMode.DSA) }
            // Logged straight after the write, so the value is the streak this open just earned
            // rather than yesterday's. One event per launch; Firebase's own session handling makes
            // several launches in a day cheap to collapse.
            val analytics = rememberAnalytics()
            LaunchedEffect(Unit) {
                settings.recordActivityToday()
                analytics.streakDay(settings.streak.first())
            }
            // Stamped once, on the first launch of an install: the quiz-exit interstitial's grace
            // period needs a real install date, and the trimmed ACTIVE_DAYS window cannot give one.
            LaunchedEffect(Unit) { settings.ensureFirstOpenDay() }

            // The two splits every retention question gets asked by. Set here rather than at the
            // point of purchase or the mode switch, so a reinstall or a cleared app re-establishes
            // them on the next launch instead of waiting for the user to do something.
            val entitlements = remember { EntitlementRepository(context.entitlementDataStore) }
            val isPremium by entitlements.isPremium.collectAsState(initial = false)
            LaunchedEffect(isPremium) {
                analytics.setUserProperty(UserProperty.IS_PREMIUM, isPremium.toString())
            }
            LaunchedEffect(mode) { analytics.setUserProperty(UserProperty.APP_MODE, mode.name) }

            // Study reminder: keep the daily check enqueued while the user wants reminders, and ask
            // for the Android 13+ notification grant once — the system dialog is one-shot, so a
            // second request would be a no-op the user never sees.
            val permissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission(),
            ) { /* Either answer is fine; the worker checks the grant before posting. */ }
            LaunchedEffect(Unit) {
                StudyReminder.ensureChannel(context)
                DailyReminder.ensureChannel(context)
                val lapsedOn = settings.remindersEnabled.first()
                val dailyOn = settings.dailyReminderEnabled.first()

                if (lapsedOn) StudyReminderWorker.schedule(context) else StudyReminderWorker.cancel(context)
                // KEEP (the default): re-enqueuing on every launch would push tonight's run back by
                // however long the app was open. Settings re-schedules with replace when the time
                // itself changes.
                if (dailyOn) {
                    DailyReminderWorker.schedule(context, settings.dailyReminderMinute.first())
                } else {
                    DailyReminderWorker.cancel(context)
                }

                // One permission covers both reminders, so it is asked for if either is on.
                if ((lapsedOn || dailyOn) &&
                    !StudyReminder.hasPermission(context) &&
                    !settings.notificationPermissionAsked.first()
                ) {
                    settings.markNotificationPermissionAsked()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
            }
            // Connecting on launch re-syncs entitlement with Play, so a refund or account switch
            // takes effect without the user opening the paywall.
            LaunchedEffect(Unit) { BillingProvider.get(context).refresh() }

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

            AlgoraTheme(darkTheme = dark, accent = accentChoice ?: mode.accent) {
                AlgoraApp(
                    mode = mode,
                    onModeChange = { mode = it },
                    deepLink = pendingDeepLink.value,
                    onDeepLinkHandled = { handled ->
                        analytics.reminderOpened(handled.kind)
                        pendingDeepLink.value = null
                    },
                )
            }
        }
    }

    // The activity is launched CLEAR_TOP|SINGLE_TOP from a notification, so an app already running
    // gets the tap here instead of through onCreate.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingDeepLink.value = deepLinkOf(intent)
    }

    private fun deepLinkOf(intent: Intent?): DeepLink? {
        val route = intent?.getStringExtra(EXTRA_ROUTE) ?: return null
        return DeepLink(route, intent.getStringExtra(EXTRA_REMINDER_KIND) ?: "unknown")
    }

    companion object {
        /** Route to open on launch. Set by core/notify so a reminder lands on the work itself. */
        const val EXTRA_ROUTE = "algora.extra.route"

        /** Which nudge sent them, for the reminder_opened event. */
        const val EXTRA_REMINDER_KIND = "algora.extra.reminder_kind"
    }
}

/** A notification tap, carried from the Intent to the nav graph. */
data class DeepLink(val route: String, val kind: String)

@Composable
fun AlgoraApp(
    mode: AppMode,
    onModeChange: (AppMode) -> Unit,
    deepLink: DeepLink? = null,
    onDeepLinkHandled: (DeepLink) -> Unit = {},
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    // Topic/sim detail keeps the bottom bar (mock shows nav on topic pages). Only the flashcard
    // review screen, which owns its own back affordance, goes full-screen.
    val onFullScreen = currentDestination?.hierarchy?.any { it.route == ReviewRoute.ROUTE } == true

    // Fixed four-destination bar from docs/design/Algora.dc.html (navDefs) — identical in both modes.
    // A tab tap discards whatever was stacked above Home — including the tab's own previous
    // sub-stack — so every tab always opens at its root rather than resuming mid-flow. Home itself
    // stays at the bottom, so back from any tab returns there instead of leaving the app.
    fun navigate(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = false }
            launchSingleTop = true
            restoreState = false
        }
    }
    fun onRoute(route: String) = currentDestination?.hierarchy?.any { it.route == route } == true

    // A reminder tap, applied once the graph exists. It goes through the same navigate() the bottom
    // bar uses, so arriving from a notification leaves the same back stack as arriving by tap —
    // back from the flashcards lands on Home, not outside the app.
    LaunchedEffect(deepLink) {
        val link = deepLink ?: return@LaunchedEffect
        navigate(link.route)
        onDeepLinkHandled(link)
    }

    Scaffold(
        bottomBar = {
            if (!onFullScreen) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    NavTab.entries.forEach { tab ->
                        val selected = when (tab) {
                            NavTab.LEARNING -> onRoute(Screen.Home.route)
                            NavTab.SIMULATIONS -> onRoute(SimulationsRoute.ROUTE)
                            // Practice stays lit while inside any of its sub-surfaces.
                            NavTab.PRACTICE -> onRoute(PracticeRoute.ROUTE) ||
                                onRoute(ProblemsRoute.ROUTE) ||
                                onRoute(ProblemDetailRoute.PATTERN) ||
                                onRoute(QuizCatalogRoute.ROUTE) ||
                                onRoute(Screen.InterviewPrep.route)
                            NavTab.PROGRESS -> onRoute(ProgressRoute.ROUTE)
                        }
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                when (tab) {
                                    NavTab.LEARNING -> navigate(Screen.Home.route)
                                    NavTab.SIMULATIONS -> navigate(SimulationsRoute.ROUTE)
                                    NavTab.PRACTICE -> {
                                        onModeChange(AppMode.DSA)
                                        navigate(PracticeRoute.ROUTE)
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
            onModeChange = onModeChange,
            modifier = Modifier.padding(innerPadding),
        )
    }
}
