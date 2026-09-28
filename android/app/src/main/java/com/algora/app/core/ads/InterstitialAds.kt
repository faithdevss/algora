package com.algora.app.core.ads

import android.app.Activity
import android.content.Context
import kotlinx.coroutines.flow.StateFlow

// The app's one non-opt-in ad surface: a full-screen ad on the way out of a finished quiz. Who sees
// it and how often is InterstitialGate.kt's decision, not this interface's — everything here assumes
// that call was already made.
interface InterstitialAds {
    val isReady: StateFlow<Boolean>

    // Text for a stand-in overlay while a fake ad "plays" — always null for real AdMob ads, which
    // render their own full-screen activity.
    val overlay: StateFlow<String?>

    fun preload(context: Context)

    /**
     * Shows the ad if one is loaded, then calls [onClosed].
     *
     * [onClosed] fires exactly once on every path — shown and dismissed, failed to show, or nothing
     * loaded — with `shown` saying whether an impression actually happened, so the caller knows
     * whether to charge it against the daily cap. The caller is mid-navigation, so this must never
     * block waiting for a load: no ad means `onClosed(false)` immediately and a preload for next
     * time.
     */
    fun show(activity: Activity, onClosed: (shown: Boolean) -> Unit)
}
