package com.algora.app.core.analytics

import android.content.Context

// Debug variant: Logcat only. A developer can still watch the event stream while walking a screen,
// but nothing a workstation or an emulator does lands in the production funnel — the same reason
// core/ads/AdsFactory swaps in fakes here rather than branching on BuildConfig.DEBUG.
internal object AnalyticsFactory {

    fun create(app: Context): Analytics = LogcatAnalytics()
}
