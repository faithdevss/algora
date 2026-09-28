package com.algora.app.core.analytics

import android.util.Log

// `adb logcat -s Analytics` is the whole debug experience: one line per event, parameters inline.
internal class LogcatAnalytics : Analytics {

    override fun log(event: String, params: Map<String, Any>) {
        val rendered = params.entries.joinToString(" ") { (key, value) -> "$key=$value" }
        Log.d(TAG, if (rendered.isEmpty()) event else "$event $rendered")
    }

    override fun setUserProperty(name: String, value: String) {
        Log.d(TAG, "user-property $name=$value")
    }

    private companion object {
        const val TAG = "Analytics"
    }
}
