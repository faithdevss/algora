package com.algora.app.core.ads

import android.app.Activity
import android.content.Context
import kotlinx.coroutines.flow.StateFlow

// Google's public test ids. Swap both for the real AdMob unit alongside the app id in
// AndroidManifest.xml when the account exists — nothing else in the app hardcodes an ad id.
object AdIds {
    const val TEST_REWARDED_UNIT = "ca-app-pub-3940256099942544/5224354917"
    val rewardedUnit: String = TEST_REWARDED_UNIT
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
