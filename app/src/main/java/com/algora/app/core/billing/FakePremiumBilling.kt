package com.algora.app.core.billing

import android.app.Activity
import com.algora.app.core.data.entitlement.EntitlementRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

// Debug stand-in so the whole paywall is walkable on an emulator with no Play Console product and
// no test account. Grants the same entitlement the real client would.
class FakePremiumBilling(
    private val entitlements: EntitlementRepository,
    private val scope: CoroutineScope,
) : PremiumBilling {

    override val price = MutableStateFlow<String?>("$14.99").asStateFlow()
    override val status = MutableStateFlow(BillingStatus.Ready).asStateFlow()

    private val _events = MutableSharedFlow<BillingEvent>(extraBufferCapacity = 8)
    override val events = _events.asSharedFlow()

    override fun refresh() {
        scope.launch { _events.emit(BillingEvent.Restored(entitlements.isPremium.first())) }
    }

    override fun launchPurchase(activity: Activity) {
        scope.launch {
            delay(600) // stands in for the Play purchase sheet
            entitlements.setPremium(true)
            _events.emit(BillingEvent.Purchased)
        }
    }
}
