package com.algora.app.feature.premium

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.algora.app.core.analytics.paywallView
import com.algora.app.core.analytics.rememberAnalytics
import com.algora.app.core.billing.BillingEvent
import com.algora.app.core.billing.BillingProvider
import com.algora.app.core.billing.BillingStatus
import com.algora.app.core.billing.PremiumPlan
import com.algora.app.core.data.entitlement.EntitlementRepository
import com.algora.app.core.data.entitlement.entitlementDataStore
import com.algora.app.core.ui.components.ScreenHeader
import com.algora.app.core.ui.theme.Gradients
import com.algora.app.core.ui.theme.ScreenGutter
import com.algora.app.core.ui.theme.SimColors
import com.algora.app.core.ui.theme.SpaceGrotesk

// Ported from docs/design/Algora.dc.html's isPremium block (radii, gradients, paddings and the
// planCard() selected-state treatment are the mock's). Copy differs where the product does: the two
// plans are a monthly subscription and a one-time lifetime unlock, and there is no free trial.
private val PremiumFeatures = listOf(
    "Unlock every topic & category",
    "All interactive labs & simulators",
    "Multi-language code snippets",
    "Spaced-repetition review mode",
    "Offline access to all content",
    "No ads",
)

@Composable
fun PremiumScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val entitlements = remember { EntitlementRepository(context.entitlementDataStore) }
    val billing = remember { BillingProvider.get(context) }

    val owned by entitlements.isPremium.collectAsState(initial = false)
    val prices by billing.prices.collectAsState()
    var selected by remember { mutableStateOf(PremiumPlan.Monthly) }
    val status by billing.status.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    // "Nothing to restore" is only worth saying when the user asked to restore — the same query also
    // runs silently every time this screen opens.
    var restoreRequested by remember { mutableStateOf(false) }

    // Reaching the paywall is the denominator a purchase rate is measured against. Logged for
    // owners too — a buyer reopening this screen is how "restore" traffic shows up at all.
    val analytics = rememberAnalytics()
    LaunchedEffect(Unit) { analytics.paywallView() }

    // Also the refund / account-switch check: Play is asked again every time this screen opens.
    LaunchedEffect(Unit) { billing.refresh() }
    LaunchedEffect(Unit) {
        billing.events.collect { event ->
            val message = when (event) {
                BillingEvent.Purchased -> "Premium unlocked — enjoy every topic."
                BillingEvent.Cancelled -> null
                BillingEvent.Pending -> "Purchase pending — we'll unlock it once it clears."
                is BillingEvent.Restored -> when {
                    event.owned -> null
                    restoreRequested -> "No previous purchase found on this account."
                    else -> null
                }.also { restoreRequested = false }
                is BillingEvent.Failed -> event.message
            }
            message?.let { snackbarHostState.showSnackbar(it) }
        }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        // The app-level Scaffold in MainActivity already applies the system-bar insets; applying
        // them again here would push the header down by a second status-bar height.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            ScreenHeader(title = "Go Premium", onBack = onBack)

            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = ScreenGutter, vertical = 16.dp),
            ) {
                PremiumHero(owned = owned)

                Spacer(modifier = Modifier.height(20.dp))
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    PremiumFeatures.forEach { FeatureRow(it) }
                }

                Spacer(modifier = Modifier.height(20.dp))
                PlanCards(prices = prices, selected = selected, onSelect = { selected = it }, owned = owned)

                Spacer(modifier = Modifier.height(18.dp))
                when {
                    owned -> OwnedButton()
                    status == BillingStatus.Unavailable -> UnavailableNote()
                    else -> PurchaseButton(
                        plan = selected,
                        price = prices[selected],
                        enabled = activity != null && status != BillingStatus.PurchasePending,
                        onClick = { activity?.let { billing.launchPurchase(it, selected) } },
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                // Play requires the renewal terms next to the price.
                Text(
                    if (selected == PremiumPlan.Monthly) {
                        "Renews monthly until you cancel in Google Play subscriptions."
                    } else {
                        "One-time purchase. No subscription."
                    },
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Text(
                        "Restore purchase",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable {
                            restoreRequested = true
                            billing.refresh()
                        },
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun PremiumHero(owned: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.linearGradient(
                    colorStops = arrayOf(
                        0f to Gradients.PremiumHero[0],
                        0.55f to Gradients.PremiumHero[1],
                        1f to Gradients.PremiumHero[2],
                    ),
                ),
                RoundedCornerShape(22.dp),
            )
            .padding(horizontal = 20.dp, vertical = 26.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(20.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.WorkspacePremium,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(30.dp),
            )
        }
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            "AlgorAI Premium",
            color = Color.White,
            fontFamily = SpaceGrotesk,
            fontWeight = FontWeight.Bold,
            fontSize = 24.sp,
        )
        Spacer(modifier = Modifier.height(5.dp))
        Text(
            if (owned) "Premium active.\nEvery topic and lab is unlocked." else "Every topic, every interactive lab.\nLearn without limits.",
            color = Color.White.copy(alpha = 0.85f),
            fontSize = 14.sp,
            lineHeight = 21.sp,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun FeatureRow(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(14.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
            .padding(horizontal = 15.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.Check,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.size(12.dp))
        Text(text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

// The mock's two plan cards: the selected one gets planCard()'s 2dp accent border.
@Composable
private fun PlanCards(
    prices: Map<PremiumPlan, String>,
    selected: PremiumPlan,
    onSelect: (PremiumPlan) -> Unit,
    owned: Boolean,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        PlanCard(
            title = "Monthly",
            price = prices[PremiumPlan.Monthly],
            note = "per month · cancel any time",
            selected = selected == PremiumPlan.Monthly && !owned,
            badge = null,
            onClick = { onSelect(PremiumPlan.Monthly) },
            modifier = Modifier.weight(1f),
        )
        PlanCard(
            title = "Lifetime",
            price = prices[PremiumPlan.Lifetime],
            note = "one-time · yours forever",
            selected = selected == PremiumPlan.Lifetime && !owned,
            badge = "PAY ONCE",
            onClick = { onSelect(PremiumPlan.Lifetime) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun PlanCard(
    title: String,
    price: String?,
    note: String,
    selected: Boolean,
    badge: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val border = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    Box(modifier = modifier.clickable(onClick = onClick)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 9.dp)
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
                .border(if (selected) 2.dp else 1.dp, border, RoundedCornerShape(16.dp))
                .padding(horizontal = 12.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                price ?: "—",
                fontFamily = SpaceGrotesk,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
            )
            Text(
                note,
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (badge != null) {
            // Mock's green pill badge (line 334).
            Text(
                badge,
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 10.dp)
                    .background(SimColors.Green, RoundedCornerShape(99.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }
    }
}

@Composable
private fun PurchaseButton(plan: PremiumPlan, price: String?, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Brush.linearGradient(Gradients.PremiumCta), RoundedCornerShape(15.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            when {
                price == null -> "Unlock Premium"
                plan == PremiumPlan.Monthly -> "Subscribe · $price / month"
                else -> "Unlock for life · $price"
            },
            color = Color.White,
            fontFamily = SpaceGrotesk,
            fontWeight = FontWeight.Bold,
            fontSize = 15.5.sp,
        )
    }
}

@Composable
private fun OwnedButton() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), RoundedCornerShape(15.dp))
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f), RoundedCornerShape(15.dp))
            .padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "You're Premium ✓",
            color = MaterialTheme.colorScheme.primary,
            fontFamily = SpaceGrotesk,
            fontWeight = FontWeight.Bold,
            fontSize = 15.5.sp,
        )
    }
}

// Play Store missing (sideloaded build, emulator without Play services) — buying is impossible, so
// say so instead of showing a button that can only fail. The rewarded-ad path still works.
@Composable
private fun UnavailableNote() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(15.dp))
            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outline), RoundedCornerShape(15.dp))
            .padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "Google Play isn't available on this device.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
        )
    }
}
