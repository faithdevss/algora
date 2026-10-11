package com.algora.app.core.ads

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.algora.app.core.data.settings.SettingsRepository
import com.algora.app.core.data.settings.settingsDataStore
import kotlinx.coroutines.launch

/** Shown on a rewarded-ad button once [DAILY_AD_CAP] is spent. */
const val DAILY_AD_CAP_REACHED = "No more ads today — back tomorrow"

/**
 * Whether today's [DAILY_AD_CAP] is spent, for the rewarded-ad buttons. Reads as spent until
 * DataStore answers, so a tap can never slip in an ad past the cap.
 */
@Composable
fun rememberDailyAdCapReached(): Boolean {
    val context = LocalContext.current
    val settings = remember { SettingsRepository(context.settingsDataStore) }
    val today = remember { System.currentTimeMillis() / 86_400_000L }
    val shownToday by remember(today) { settings.adsShownToday(today) }.collectAsState(initial = DAILY_AD_CAP)
    return shownToday >= DAILY_AD_CAP
}

/** Charges a rewarded impression against the day's cap. App-lifetime scope: the ad outlives the screen. */
fun recordRewardedImpression(context: Context) {
    val settings = SettingsRepository(context.applicationContext.settingsDataStore)
    AdWriteScope.launch { settings.recordAdShown() }
}
