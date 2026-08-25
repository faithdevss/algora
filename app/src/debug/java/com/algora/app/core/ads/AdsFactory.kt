package com.algora.app.core.ads

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

// Debug variant: countdown overlays instead of real ads, so emulator runs never touch the AdMob SDK.
// Selected by source set rather than a BuildConfig.DEBUG branch — see the note in
// core/billing/BillingFactory.kt for why that distinction matters here.
internal object AdsFactory {

    fun create(app: Context): RewardedAds =
        FakeRewardedAds(CoroutineScope(SupervisorJob() + Dispatchers.Main))

    fun createInterstitial(app: Context): InterstitialAds =
        FakeInterstitialAds(CoroutineScope(SupervisorJob() + Dispatchers.Main))
}
