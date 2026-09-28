package com.algora.app.core.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val TAG = "AdMobInterstitialAds"

// One interstitial kept warm at a time, mirroring AdMobRewardedAds: preloaded when a quiz starts
// being taken seriously, reloaded after each dismissal so the next eligible exit does not wait.
class AdMobInterstitialAds(context: Context, scope: CoroutineScope) : InterstitialAds {

    private val _isReady = MutableStateFlow(false)
    override val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    // Real ads render their own full-screen activity, so there is never an in-app overlay.
    override val overlay: StateFlow<String?> = MutableStateFlow<String?>(null).asStateFlow()

    private var ad: InterstitialAd? = null
    private var loading = false

    init {
        MobileAdsInit.ensureInitialized(context, scope)
    }

    override fun preload(context: Context) {
        if (ad != null || loading) return
        loading = true
        InterstitialAd.load(
            context.applicationContext,
            AdIds.interstitialUnit,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(loaded: InterstitialAd) {
                    loading = false
                    ad = loaded
                    _isReady.value = true
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    loading = false
                    ad = null
                    _isReady.value = false
                    Log.w(TAG, "interstitial load failed: ${error.message}")
                }
            },
        )
    }

    override fun show(activity: Activity, onClosed: (Boolean) -> Unit) {
        val current = ad
        // Nothing loaded: the user is already leaving a screen, so let them go and warm one up for
        // the next time rather than stalling the exit on a network round trip.
        if (current == null) {
            preload(activity)
            onClosed(false)
            return
        }

        current.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                ad = null
                _isReady.value = false
                preload(activity)
                onClosed(true)
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                ad = null
                _isReady.value = false
                preload(activity)
                Log.w(TAG, "interstitial show failed: ${error.message}")
                onClosed(false)
            }
        }
        ad = null
        _isReady.value = false
        current.show(activity)
    }
}
