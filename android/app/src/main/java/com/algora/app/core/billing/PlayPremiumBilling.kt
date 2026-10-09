package com.algora.app.core.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import com.algora.app.core.data.entitlement.EntitlementRepository
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val TAG = "PlayPremiumBilling"

// Play Billing v9 wiring for the monthly subscription (SUBS) and the lifetime unlock (INAPP).
// queryPurchasesAsync() is the source of truth for both: it runs on every connect, so refunds,
// cancellations that have run out, reinstalls and account switches all converge. A subscription's
// expiry is therefore noticed on the next launch or paywall visit, not the instant it lapses.
//
// No server-side receipt validation — the roadmap defers a backend, so entitlement lives in
// DataStore and is spoofable on a rooted device. Acceptable for this app's threat model.
class PlayPremiumBilling(
    context: Context,
    private val entitlements: EntitlementRepository,
    private val scope: CoroutineScope,
) : PremiumBilling {

    private val _prices = MutableStateFlow<Map<PremiumPlan, String>>(emptyMap())
    override val prices = _prices.asStateFlow()

    private val _status = MutableStateFlow(BillingStatus.Connecting)
    override val status = _status.asStateFlow()

    private val _events = MutableSharedFlow<BillingEvent>(extraBufferCapacity = 8)
    override val events = _events.asSharedFlow()

    // What launchBillingFlow needs for a plan: the product and, for the subscription, which offer.
    private class Offer(val details: ProductDetails, val offerToken: String?)

    private val offers = mutableMapOf<PremiumPlan, Offer>()

    private val purchasesUpdatedListener = PurchasesUpdatedListener { result, purchases ->
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> purchases.orEmpty().forEach { handlePurchase(it) }
            BillingClient.BillingResponseCode.USER_CANCELED -> _events.tryEmit(BillingEvent.Cancelled)
            // Already bought on this account (e.g. reinstall) — let the query path grant it.
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> refresh()
            else -> _events.tryEmit(BillingEvent.Failed(result.describe()))
        }
    }

    private val client: BillingClient = BillingClient.newBuilder(context.applicationContext)
        .setListener(purchasesUpdatedListener)
        // Required since Billing 7.x — omitting it throws at build() time.
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .enableAutoServiceReconnection()
        .build()

    init {
        connect()
    }

    private fun connect() {
        if (client.isReady) {
            onConnected()
            return
        }
        _status.value = BillingStatus.Connecting
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    onConnected()
                } else {
                    Log.w(TAG, "billing setup failed: ${result.describe()}")
                    _status.value = BillingStatus.Unavailable
                }
            }

            // enableAutoServiceReconnection() handles the retry; just reflect it in the UI.
            override fun onBillingServiceDisconnected() {
                _status.value = BillingStatus.Connecting
            }
        })
    }

    private fun onConnected() {
        _status.value = BillingStatus.Ready
        PremiumPlan.entries.forEach { queryPlan(it) }
        refresh()
    }

    // One query per plan: Play does not allow SUBS and INAPP products in the same request.
    private fun queryPlan(plan: PremiumPlan) {
        val type = plan.productType()
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(plan.productId)
                        .setProductType(type)
                        .build(),
                ),
            )
            .build()

        client.queryProductDetailsAsync(params) { result, queryResult ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                Log.w(TAG, "product query failed for ${plan.productId}: ${result.describe()}")
                return@queryProductDetailsAsync
            }
            val details = queryResult.productDetailsList.firstOrNull { it.productId == plan.productId }
                ?: return@queryProductDetailsAsync
            val offer = when (plan) {
                PremiumPlan.Lifetime -> details.oneTimePurchaseOfferDetails
                    ?.let { Offer(details, null) to it.formattedPrice }
                PremiumPlan.Monthly -> details.fullPriceOffer()
                    ?.let { Offer(details, it.offerToken) to it.pricingPhases.pricingPhaseList.last().formattedPrice }
            } ?: return@queryProductDetailsAsync
            offers[plan] = offer.first
            _prices.value = _prices.value + (plan to offer.second)
        }
    }

    override fun refresh() {
        if (!client.isReady) {
            connect()
            return
        }
        // Both types must answer before entitlement is written: acting on one alone would revoke
        // a lifetime buyer while the subscription query was still in flight, or the reverse.
        queryOwned(BillingClient.ProductType.SUBS) { subs ->
            queryOwned(BillingClient.ProductType.INAPP) { inapp ->
                val owned = (subs + inapp).filter { purchase ->
                    PremiumPlan.entries.any { it.productId in purchase.products } &&
                        purchase.purchaseState == Purchase.PurchaseState.PURCHASED
                }
                owned.forEach { acknowledgeIfNeeded(it) }
                val isOwned = owned.isNotEmpty()
                scope.launch { entitlements.setPremium(isOwned) }
                _events.tryEmit(BillingEvent.Restored(isOwned))
            }
        }
    }

    private fun queryOwned(type: String, onResult: (List<Purchase>) -> Unit) {
        val params = QueryPurchasesParams.newBuilder().setProductType(type).build()
        client.queryPurchasesAsync(params) { result, purchases ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                _events.tryEmit(BillingEvent.Failed(result.describe()))
                return@queryPurchasesAsync
            }
            onResult(purchases)
        }
    }

    override fun launchPurchase(activity: Activity, plan: PremiumPlan) {
        val offer = offers[plan]
        if (offer == null || !client.isReady) {
            _events.tryEmit(BillingEvent.Failed("Google Play is unavailable right now."))
            return
        }
        val productParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(offer.details)
            .apply { offer.offerToken?.let { setOfferToken(it) } }
            .build()
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productParams))
            .build()

        val result = client.launchBillingFlow(activity, params)
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            _events.tryEmit(BillingEvent.Failed(result.describe()))
        }
    }

    private fun handlePurchase(purchase: Purchase) {
        if (PremiumPlan.entries.none { it.productId in purchase.products }) return
        when (purchase.purchaseState) {
            Purchase.PurchaseState.PURCHASED -> {
                acknowledgeIfNeeded(purchase)
                scope.launch { entitlements.setPremium(true) }
                _status.value = BillingStatus.Ready
                _events.tryEmit(BillingEvent.Purchased)
            }
            // Slow payment method: nothing is granted until it clears and refresh() sees it.
            Purchase.PurchaseState.PENDING -> {
                _status.value = BillingStatus.PurchasePending
                _events.tryEmit(BillingEvent.Pending)
            }
        }
    }

    // Google auto-refunds purchases that stay unacknowledged for three days. Applies to
    // subscriptions too, on first purchase only; renewals need nothing.
    private fun acknowledgeIfNeeded(purchase: Purchase) {
        if (purchase.isAcknowledged) return
        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()
        client.acknowledgePurchase(params) { result ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                Log.w(TAG, "acknowledge failed: ${result.describe()}")
            }
        }
    }
}

private fun PremiumPlan.productType(): String = when (this) {
    PremiumPlan.Monthly -> BillingClient.ProductType.SUBS
    PremiumPlan.Lifetime -> BillingClient.ProductType.INAPP
}

// The plain recurring offer: every pricing phase costs money. This skips any trial or introductory
// offer someone later adds in Play Console, which this app deliberately does not sell.
private fun ProductDetails.fullPriceOffer(): ProductDetails.SubscriptionOfferDetails? =
    subscriptionOfferDetails.orEmpty().firstOrNull { offer ->
        offer.pricingPhases.pricingPhaseList.all { it.priceAmountMicros > 0 }
    }

private fun BillingResult.describe(): String =
    debugMessage.ifBlank { "Play billing error $responseCode" }
