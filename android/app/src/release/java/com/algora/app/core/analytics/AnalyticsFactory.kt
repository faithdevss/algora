package com.algora.app.core.analytics

import android.content.Context

// Release variant: events go to Firebase, and nowhere else.
internal object AnalyticsFactory {

    fun create(app: Context): Analytics = FirebaseAnalyticsClient(app)
}
