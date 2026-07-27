package com.algora.app.core.billing

import android.content.Context
import com.algora.app.BuildConfig
import com.algora.app.core.data.entitlement.EntitlementRepository
import com.algora.app.core.data.entitlement.entitlementDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

// App-lifetime singleton (no DI framework in this project — same hand-rolled style as the DataStore
// repositories). Debug builds get the fake so the paywall is testable without a Play Console;
// release builds talk to real Play Billing.
object BillingProvider {

    @Volatile
    private var instance: PremiumBilling? = null

    fun get(context: Context): PremiumBilling =
        instance ?: synchronized(this) { instance ?: create(context).also { instance = it } }

    private fun create(context: Context): PremiumBilling {
        val app = context.applicationContext
        val entitlements = EntitlementRepository(app.entitlementDataStore)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        return if (BuildConfig.DEBUG) {
            FakePremiumBilling(entitlements, scope)
        } else {
            PlayPremiumBilling(app, entitlements, scope)
        }
    }
}
