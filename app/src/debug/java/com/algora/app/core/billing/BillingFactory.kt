package com.algora.app.core.billing

import android.content.Context
import com.algora.app.core.data.entitlement.EntitlementRepository
import com.algora.app.core.data.entitlement.entitlementDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

// Debug variant: the paywall is walkable on an emulator with no Play Console product and no test
// account.
//
// This used to be a `if (BuildConfig.DEBUG)` branch inside BillingProvider, which left
// FakePremiumBilling — a class that grants the entitlement for free — compiled into the release
// APK, reachable by reflection, since the release build does not run R8. Selecting the
// implementation by source set instead means the release compiler never sees the fake at all, and
// the guarantee does not depend on a shrinker setting.
internal object BillingFactory {

    fun create(app: Context): PremiumBilling {
        val entitlements = EntitlementRepository(app.entitlementDataStore)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        return FakePremiumBilling(entitlements, scope)
    }
}
