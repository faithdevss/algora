package com.algora.app.core.ads

import android.content.Context
import com.algora.app.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

// App-lifetime singleton, mirroring core/billing/BillingProvider. Debug builds never touch the
// AdMob SDK at all — the fake keeps emulator runs offline-friendly.
object AdsProvider {

    @Volatile
    private var instance: RewardedAds? = null

    fun get(context: Context): RewardedAds =
        instance ?: synchronized(this) { instance ?: create(context).also { instance = it } }

    private fun create(context: Context): RewardedAds {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        return if (BuildConfig.DEBUG) {
            FakeRewardedAds(scope)
        } else {
            AdMobRewardedAds(context.applicationContext, scope)
        }
    }
}
