package com.algora.app.core.billing

import android.content.Context
import com.algora.app.core.data.entitlement.EntitlementRepository
import com.algora.app.core.data.entitlement.entitlementDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

// Release variant: real Play Billing, and nothing else. The debug counterpart in src/debug is the
// only place FakePremiumBilling is named, so it cannot reach a release build.
internal object BillingFactory {

    fun create(app: Context): PremiumBilling {
        val entitlements = EntitlementRepository(app.entitlementDataStore)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        return PlayPremiumBilling(app, entitlements, scope)
    }
}
