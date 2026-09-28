package com.algora.app.core.analytics

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

// App-lifetime singleton, mirroring core/ads/AdsProvider and core/billing/BillingProvider —
// including how the implementation is chosen: AnalyticsFactory exists once in src/debug and once in
// src/release, so emulator and CI runs cannot write into the production funnel.
object AnalyticsProvider {

    @Volatile
    private var instance: Analytics? = null

    fun get(context: Context): Analytics =
        instance ?: synchronized(this) {
            instance ?: AnalyticsFactory.create(context.applicationContext).also { instance = it }
        }
}

/** The call-site shorthand. Cheap: the provider hands back the same singleton every time. */
@Composable
fun rememberAnalytics(): Analytics {
    val context = LocalContext.current
    return remember { AnalyticsProvider.get(context) }
}
