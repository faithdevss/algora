package com.algora.app.core.billing

import android.app.Activity
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

// The two ways to hold Premium. Either grants the same entitlement; Monthly lapses when the
// subscription does, Lifetime never does (it is also what pre-subscription buyers own).
enum class PremiumPlan(val productId: String) {
    // Auto-renewing SUBS product. No free trial or introductory offer: the product is content.
    Monthly("algora_premium_monthly"),
    // Non-consumable INAPP product.
    Lifetime("algora_premium_lifetime"),
}

enum class BillingStatus {
    Connecting,
    Ready,
    // Play billing is missing or refused to connect — the paywall hides its buy button and only
    // offers the rewarded-ad path.
    Unavailable,
    // Purchase accepted but not yet cleared (slow payment methods); entitlement waits.
    PurchasePending,
}

sealed interface BillingEvent {
    data object Purchased : BillingEvent
    data object Cancelled : BillingEvent
    data object Pending : BillingEvent
    // refresh() finished: `owned` says whether Play knows about a purchase for this account.
    data class Restored(val owned: Boolean) : BillingEvent
    data class Failed(val message: String) : BillingEvent
}

// Entitlement itself is never exposed here — every implementation writes the result into
// EntitlementRepository, which the UI observes. This interface only drives the store interaction.
interface PremiumBilling {
    // Localized store price per plan; a plan is absent until the store has returned it.
    val prices: StateFlow<Map<PremiumPlan, String>>
    val status: StateFlow<BillingStatus>
    val events: SharedFlow<BillingEvent>

    // Re-asks Play what this account owns — an active subscription or the lifetime purchase. Doubles as "restore purchase" and as the refund/
    // account-switch check that runs on every app start.
    fun refresh()

    fun launchPurchase(activity: Activity, plan: PremiumPlan)
}
