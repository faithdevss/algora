package com.algora.app.core.ads

import android.app.Activity
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val FAKE_AD_SECONDS = 3

// Debug stand-in, mirroring FakeRewardedAds: a short countdown overlay instead of a real ad, so the
// whole quiz-exit gate is walkable on an emulator with no AdMob account and no network.
class FakeInterstitialAds(private val scope: CoroutineScope) : InterstitialAds {

    override val isReady: StateFlow<Boolean> = MutableStateFlow(true).asStateFlow()

    private val _overlay = MutableStateFlow<String?>(null)
    override val overlay: StateFlow<String?> = _overlay.asStateFlow()

    override fun preload(context: Context) = Unit

    override fun show(activity: Activity, onClosed: (Boolean) -> Unit) {
        if (_overlay.value != null) {
            onClosed(false)
            return
        }
        scope.launch {
            for (remaining in FAKE_AD_SECONDS downTo 1) {
                _overlay.value = "Test interstitial — closing in ${remaining}s"
                delay(1000)
            }
            _overlay.value = null
            onClosed(true)
        }
    }
}
