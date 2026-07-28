package com.algora.app.core.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val TAG = "AdMobRewardedAds"

// One rewarded ad kept warm at a time: loaded on first paywall view, reloaded after each dismissal
// so the next locked topic can unlock without a wait.
class AdMobRewardedAds(context: Context, private val scope: CoroutineScope) : RewardedAds {

    private val _isReady = MutableStateFlow(false)
    override val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    // Real ads render their own full-screen activity, so there is never an in-app overlay.
    override val overlay: StateFlow<String?> = MutableStateFlow<String?>(null).asStateFlow()

    private var ad: RewardedAd? = null
    private var loading = false

    init {
        // initialize() does disk and network work — Google requires it off the main thread.
        scope.launch(Dispatchers.IO) {
            // Algora's Play target audience is 13+, so no child-directed / under-age-of-consent
            // tagging (either would force non-personalized ads and cut eCPM). The content-rating cap
            // is a cheap safety net: a learning app should never serve a mature-rated ad, whatever
            // the network would otherwise pick. Set before initialize() so the first request honours
            // it. If the target audience is ever widened to include under-13s, this is the place to
            // add setTagForChildDirectedTreatment / setTagForUnderAgeOfConsent — and outbound links
            // (see core/ui/components/CrossPromo.kt) would then need a parental gate.
            MobileAds.setRequestConfiguration(
                RequestConfiguration.Builder()
                    .setMaxAdContentRating(RequestConfiguration.MAX_AD_CONTENT_RATING_T)
                    .build(),
            )
            MobileAds.initialize(context.applicationContext)
        }
    }

    override fun preload(context: Context) {
        if (ad != null || loading) return
        loading = true
        val appContext = context.applicationContext
        RewardedAd.load(
            appContext,
            AdIds.rewardedUnit,
            AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(loaded: RewardedAd) {
                    loading = false
                    ad = loaded
                    _isReady.value = true
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    loading = false
                    ad = null
                    _isReady.value = false
                    Log.w(TAG, "rewarded load failed: ${error.message}")
                }
            },
        )
    }

    override fun show(activity: Activity, onReward: () -> Unit, onFailed: (String) -> Unit) {
        val current = ad
        if (current == null) {
            preload(activity)
            onFailed("Ad not ready yet — try again in a moment.")
            return
        }

        var earned = false
        current.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                ad = null
                _isReady.value = false
                preload(activity)
                if (!earned) onFailed("Watch the full ad to unlock this topic.")
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                ad = null
                _isReady.value = false
                preload(activity)
                onFailed(error.message.ifBlank { "Ad failed to play." })
            }
        }

        current.show(activity) {
            earned = true
            onReward()
        }
    }
}
