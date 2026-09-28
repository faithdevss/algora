package com.algora.app.core.ads

import android.content.Context

// App-lifetime singletons, mirroring core/billing/BillingProvider — including how the implementation
// is chosen: AdsFactory exists once in src/debug and once in src/release, so the fakes are not
// compiled into the shipped APK.
object AdsProvider {

    @Volatile
    private var rewarded: RewardedAds? = null

    @Volatile
    private var interstitial: InterstitialAds? = null

    fun get(context: Context): RewardedAds =
        rewarded ?: synchronized(this) {
            rewarded ?: AdsFactory.create(context.applicationContext).also { rewarded = it }
        }

    fun getInterstitial(context: Context): InterstitialAds =
        interstitial ?: synchronized(this) {
            interstitial ?: AdsFactory.createInterstitial(context.applicationContext).also { interstitial = it }
        }
}
