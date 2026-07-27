package com.algora.app.core.ads

import android.app.Activity
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val FAKE_AD_SECONDS = 5

// Debug stand-in: a 5-second countdown overlay instead of a real video, so the unlock flow is
// walkable on an emulator with no AdMob account and no network.
class FakeRewardedAds(private val scope: CoroutineScope) : RewardedAds {

    override val isReady: StateFlow<Boolean> = MutableStateFlow(true).asStateFlow()

    private val _overlay = MutableStateFlow<String?>(null)
    override val overlay: StateFlow<String?> = _overlay.asStateFlow()

    override fun preload(context: Context) = Unit

    override fun show(activity: Activity, onReward: () -> Unit, onFailed: (String) -> Unit) {
        if (_overlay.value != null) return
        scope.launch {
            for (remaining in FAKE_AD_SECONDS downTo 1) {
                _overlay.value = "Test ad — reward in ${remaining}s"
                delay(1000)
            }
            _overlay.value = null
            onReward()
        }
    }
}
