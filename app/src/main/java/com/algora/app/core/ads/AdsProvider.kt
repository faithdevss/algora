package com.algora.app.core.ads

import android.content.Context

// App-lifetime singleton, mirroring core/billing/BillingProvider — including how the implementation
// is chosen: AdsFactory exists once in src/debug and once in src/release, so the fake is not
// compiled into the shipped APK.
object AdsProvider {

    @Volatile
    private var instance: RewardedAds? = null

    fun get(context: Context): RewardedAds =
        instance ?: synchronized(this) {
            instance ?: AdsFactory.create(context.applicationContext).also { instance = it }
        }
}
