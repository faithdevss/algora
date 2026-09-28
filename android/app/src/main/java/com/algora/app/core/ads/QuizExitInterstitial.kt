package com.algora.app.core.ads

import androidx.activity.compose.LocalActivity
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.algora.app.core.data.entitlement.EntitlementRepository
import com.algora.app.core.data.entitlement.entitlementDataStore
import com.algora.app.core.data.settings.SettingsRepository
import com.algora.app.core.data.settings.settingsDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * The one interstitial placement in the app: on the way *out* of a finished quiz.
 *
 * [exit] is what every route off the results screen calls instead of navigating directly — the back
 * arrow, the "Back to Prep" button and the system back gesture alike. It shows an ad if the gate
 * allows one, and then runs `onDone`; if the gate refuses, or nothing is loaded, `onDone` runs
 * immediately. Either way `onDone` runs exactly once, so an exit can never be swallowed by the ad
 * layer.
 */
@Stable
class QuizExitInterstitial internal constructor(private val onExit: (() -> Unit) -> Unit) {
    fun exit(onDone: () -> Unit) = onExit(onDone)
}

/**
 * Builds the controller and renders the debug stand-in overlay when one is playing.
 *
 * Pass `enabled = false` for a surface that must never be taxed — currently the daily drill, whose
 * inline quiz is a once-a-day habit loop rather than a sit-down quiz.
 */
@Composable
fun rememberQuizExitInterstitial(enabled: Boolean): QuizExitInterstitial {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val settings = remember { SettingsRepository(context.settingsDataStore) }
    val entitlements = remember { EntitlementRepository(context.entitlementDataStore) }
    val ads = remember { AdsProvider.getInterstitial(context) }

    // Pinned for the life of the screen: a quiz is minutes long, so rolling over midnight mid-run
    // would at worst mis-attribute one impression to the previous day's cap.
    val today = remember { System.currentTimeMillis() / 86_400_000L }

    val stateFlow = remember(today) {
        val fromSettings = combine(
            settings.quizzesFinished,
            settings.firstOpenDay,
            settings.interstitialsShownToday(today),
            settings.interstitialLastShownAtMs,
            settings.reviewPromptedDay,
        ) { finished, firstOpen, shownToday, lastShownAtMs, reviewDay ->
            InterstitialState(
                quizzesFinished = finished,
                // An install that predates FIRST_OPEN_DAY reads as brand new, which grants one extra
                // grace period rather than skipping it.
                daysSinceInstall = firstOpen?.let { today - it } ?: 0L,
                shownToday = shownToday,
                lastShownAtMs = lastShownAtMs,
                reviewPromptedToday = reviewDay == today,
            )
        }
        combine(entitlements.isPremium, fromSettings) { premium, rest -> rest.copy(isPremium = premium) }
    }

    // Until DataStore answers, the state reads premium — the safe default, since guessing wrong the
    // other way would show an ad to a paying user.
    val state by stateFlow.collectAsState(initial = InterstitialState(isPremium = true))
    val overlay by ads.overlay.collectAsState()
    var showing by remember { mutableStateOf(false) }

    // Warmed while the quiz is still being taken, so an eligible exit does not wait on the network.
    // Gated on the same rule the exit itself checks: requesting an ad the gate is going to refuse
    // anyway (too early in install life, under the free-quiz count, cap already hit, review just
    // asked) burns a request with no chance of an impression.
    LaunchedEffect(enabled, state) {
        if (enabled && shouldShowInterstitial(state, System.currentTimeMillis())) ads.preload(context)
    }

    val controller = remember(enabled, state, activity, showing) {
        QuizExitInterstitial { onDone ->
            val host = activity
            val eligible = enabled && host != null && !showing && shouldShowInterstitial(state, System.currentTimeMillis())
            if (!eligible) {
                onDone()
            } else {
                showing = true
                ads.show(host) { shown ->
                    showing = false
                    if (shown) AdWriteScope.launch { settings.recordInterstitialShown() }
                    onDone()
                }
            }
        }
    }

    // Only the debug stand-in renders in-app; real AdMob ads own the whole screen themselves.
    overlay?.let { text ->
        AlertDialog(
            onDismissRequest = {},
            confirmButton = { TextButton(onClick = {}, enabled = false) { Text("Please wait") } },
            title = { Text("Test interstitial") },
            text = { Text(text, style = MaterialTheme.typography.bodyMedium) },
        )
    }

    return controller
}

// The ad callback fires after the caller has navigated away, so the composition that started it is
// already gone. A rememberCoroutineScope() write would be cancelled with it and the impression would
// never be charged against the daily cap — hence an app-lifetime scope for this one write.
private val AdWriteScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
