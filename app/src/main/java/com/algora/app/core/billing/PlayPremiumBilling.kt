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

// Play Billing v8 wiring for the single lifetime IAP. queryPurchasesAsync() is treated as the source
// of truth: it runs on every connect, so refunds, reinstalls and account switches all converge.
//
// No server-side receipt validation — the roadmap defers a backend, so entitlement lives in
// DataStore and is spoofable on a rooted device. Acceptable for this app's threat model.
class PlayPremiumBilling(
    context: Context,
    private val entitlements: EntitlementRepository,
    private val scope: CoroutineScope,
) : PremiumBilling {

    private val _price = MutableStateFlow<String?>(null)
    override val price = _price.asStateFlow()

    private val _status = MutableStateFlow(BillingStatus.Connecting)
    override val status = _status.asStateFlow()

    private val _events = MutableSharedFlow<BillingEvent>(extraBufferCapacity = 8)
    override val events = _events.asSharedFlow()

    private var productDetails: ProductDetails? = null

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
        queryProduct()
        refresh()
    }

    private fun queryProduct() {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(PREMIUM_PRODUCT_ID)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build(),
                ),
            )
            .build()

        client.queryProductDetailsAsync(params) { result, queryResult ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                Log.w(TAG, "product query failed: ${result.describe()}")
                return@queryProductDetailsAsync
            }
            val details = queryResult.productDetailsList.firstOrNull { it.productId == PREMIUM_PRODUCT_ID }
            productDetails = details
            _price.value = details?.oneTimePurchaseOfferDetails?.formattedPrice
        }
    }

    override fun refresh() {
        if (!client.isReady) {
            connect()
            return
        }
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()

        client.queryPurchasesAsync(params) { result, purchases ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                _events.tryEmit(BillingEvent.Failed(result.describe()))
                return@queryPurchasesAsync
            }
            val premium = purchases.filter {
                PREMIUM_PRODUCT_ID in it.products && it.purchaseState == Purchase.PurchaseState.PURCHASED
            }
            premium.forEach { acknowledgeIfNeeded(it) }
            val owned = premium.isNotEmpty()
            scope.launch { entitlements.setPremium(owned) }
            _events.tryEmit(BillingEvent.Restored(owned))
        }
    }

    override fun launchPurchase(activity: Activity) {
        val details = productDetails
        if (details == null || !client.isReady) {
            _events.tryEmit(BillingEvent.Failed("Google Play is unavailable right now."))
            return
        }
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(details)
                        .build(),
                ),
            )
            .build()

        val result = client.launchBillingFlow(activity, params)
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            _events.tryEmit(BillingEvent.Failed(result.describe()))
        }
    }

    private fun handlePurchase(purchase: Purchase) {
        if (PREMIUM_PRODUCT_ID !in purchase.products) return
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

    // Google auto-refunds purchases that stay unacknowledged for three days.
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

private fun BillingResult.describe(): String =
    debugMessage.ifBlank { "Play billing error $responseCode" }
