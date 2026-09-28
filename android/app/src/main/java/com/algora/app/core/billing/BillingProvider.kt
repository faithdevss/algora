package com.algora.app.core.billing

import android.content.Context

// App-lifetime singleton (no DI framework in this project — same hand-rolled style as the DataStore
// repositories). Which implementation it hands out is decided at *compile* time by BillingFactory,
// which exists once in src/debug and once in src/release — see the note there.
object BillingProvider {

    @Volatile
    private var instance: PremiumBilling? = null

    fun get(context: Context): PremiumBilling =
        instance ?: synchronized(this) {
            instance ?: BillingFactory.create(context.applicationContext).also { instance = it }
        }
}
