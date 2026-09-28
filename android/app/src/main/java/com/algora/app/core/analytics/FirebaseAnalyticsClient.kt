package com.algora.app.core.analytics

import android.content.Context
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics

// The real client. Lives in src/main next to the interface (as AdMobRewardedAds does) but is only
// ever constructed by the release AnalyticsFactory.
internal class FirebaseAnalyticsClient(app: Context) : Analytics {

    private val firebase = FirebaseAnalytics.getInstance(app)

    override fun log(event: String, params: Map<String, Any>) {
        firebase.logEvent(event, params.toBundle())
    }

    override fun setUserProperty(name: String, value: String) {
        firebase.setUserProperty(name, value.take(MAX_STRING_VALUE))
    }

    // Firebase silently drops a parameter whose type it does not handle, so the conversion is
    // explicit here instead: anything unexpected is stringified rather than lost.
    private fun Map<String, Any>.toBundle(): Bundle = Bundle().apply {
        forEach { (key, value) ->
            when (value) {
                is Int -> putLong(key, value.toLong())
                is Long -> putLong(key, value)
                is Double -> putDouble(key, value)
                is Float -> putDouble(key, value.toDouble())
                else -> putString(key, value.toString().take(MAX_STRING_VALUE))
            }
        }
    }

    private companion object {
        // Firebase's own cap on a string parameter value; a longer one is rejected outright.
        const val MAX_STRING_VALUE = 100
    }
}
