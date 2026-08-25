package com.algora.app.core.ads

import android.app.Activity
import android.content.Context
import com.algora.app.BuildConfig
import kotlinx.coroutines.flow.StateFlow

// Configured in app/build.gradle.kts from local.properties / -P / the environment, never hardcoded
// here — debug always gets Google's test units, release gets the real ones when they are set. The
// app id travels the same way, as a manifest placeholder. See docs/admob-setup.md.
object AdIds {
    val rewardedUnit: String = BuildConfig.ADMOB_REWARDED_UNIT_ID
    val interstitialUnit: String = BuildConfig.ADMOB_INTERSTITIAL_UNIT_ID
}

// The app has exactly two ad surfaces, both full-screen and both only for non-premium users:
//
//  - Rewarded video (this file) — always opt-in. A free user taps it to unlock one premium topic for
//    6h, or to bank a streak freeze. Nothing shows it automatically.
//  - Interstitial (InterstitialAds.kt) — the one non-opt-in surface, shown on the way out of a
//    finished quiz, behind the caps in InterstitialGate.kt.
//
// There are no banners anywhere, and nothing renders an ad over content the user is still reading.
interface RewardedAds {
    val isReady: StateFlow<Boolean>

    // Text for a stand-in overlay while a fake ad "plays" — always null for real AdMob ads, which
    // render their own full-screen activity.
    val overlay: StateFlow<String?>

    fun preload(context: Context)

    // onReward fires only when the user actually earns the reward (watched far enough).
    fun show(activity: Activity, onReward: () -> Unit, onFailed: (String) -> Unit)
}
