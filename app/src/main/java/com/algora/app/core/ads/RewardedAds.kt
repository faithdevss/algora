package com.algora.app.core.ads

import android.app.Activity
import android.content.Context
import com.algora.app.BuildConfig
import kotlinx.coroutines.flow.StateFlow

// Configured in app/build.gradle.kts from local.properties / -P / the environment, never hardcoded
// here — debug always gets Google's test unit, release gets the real one when it is set. The app id
// travels the same way, as a manifest placeholder. See docs/admob-setup.md.
object AdIds {
    val rewardedUnit: String = BuildConfig.ADMOB_REWARDED_UNIT_ID
}

// Rewarded video is the only ad surface in the app: it appears when a free user chooses to unlock a
// premium topic for 24h. There are no banners or interstitials anywhere.
interface RewardedAds {
    val isReady: StateFlow<Boolean>

    // Text for a stand-in overlay while a fake ad "plays" — always null for real AdMob ads, which
    // render their own full-screen activity.
    val overlay: StateFlow<String?>

    fun preload(context: Context)

    // onReward fires only when the user actually earns the reward (watched far enough).
    fun show(activity: Activity, onReward: () -> Unit, onFailed: (String) -> Unit)
}
