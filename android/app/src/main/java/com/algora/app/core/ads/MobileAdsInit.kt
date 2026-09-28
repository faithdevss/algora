package com.algora.app.core.ads

import android.content.Context
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

// SDK bootstrap, shared by every ad format.
//
// This used to live in AdMobRewardedAds' init block, which was fine while rewarded video was the
// only surface: that object is constructed by AdsProvider, whose only caller was the locked-topic
// paywall, so the SDK came up the first time a free user met a lock. An interstitial can be the
// first ad surface a user ever reaches (finish four quizzes, never open a locked topic), so the
// bootstrap has to be reachable independently of which format asks first.
object MobileAdsInit {

    private val started = AtomicBoolean(false)

    // Idempotent: the first caller starts the SDK, later callers return immediately.
    fun ensureInitialized(context: Context, scope: CoroutineScope) {
        if (!started.compareAndSet(false, true)) return
        val appContext = context.applicationContext
        // initialize() does disk and network work — Google requires it off the main thread.
        scope.launch(Dispatchers.IO) {
            // Algora's Play target audience is 13+, so no child-directed / under-age-of-consent
            // tagging (either would force non-personalized ads and cut eCPM). The content-rating cap
            // is a cheap safety net: a learning app should never serve a mature-rated ad, whatever
            // the network would otherwise pick. Set before initialize() so the first request of
            // either format honours it. If the target audience is ever widened to include under-13s,
            // this is the place to add setTagForChildDirectedTreatment /
            // setTagForUnderAgeOfConsent — and outbound links (see core/ui/components/CrossPromo.kt)
            // would then need a parental gate.
            MobileAds.setRequestConfiguration(
                RequestConfiguration.Builder()
                    .setMaxAdContentRating(RequestConfiguration.MAX_AD_CONTENT_RATING_T)
                    .build(),
            )
            MobileAds.initialize(appContext)
        }
    }
}
