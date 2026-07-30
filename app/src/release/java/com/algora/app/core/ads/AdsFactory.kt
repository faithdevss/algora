package com.algora.app.core.ads

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

// Release variant: real AdMob rewarded ads, and nothing else.
internal object AdsFactory {

    fun create(app: Context): RewardedAds =
        AdMobRewardedAds(app, CoroutineScope(SupervisorJob() + Dispatchers.Main))
}
