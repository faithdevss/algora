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
import com.algora.app.core.billing.BillingEvent
import com.algora.app.core.billing.BillingProvider
import com.algora.app.core.billing.BillingStatus
import com.algora.app.core.data.entitlement.EntitlementRepository
import com.algora.app.core.data.entitlement.entitlementDataStore
import com.algora.app.core.ui.components.ScreenHeader
import com.algora.app.core.ui.theme.Gradients
import com.algora.app.core.ui.theme.ScreenGutter
import com.algora.app.core.ui.theme.SimColors
import com.algora.app.core.ui.theme.SpaceGrotesk

// Ported from docs/design/Algora.dc.html's isPremium block (radii, gradients, paddings and the
// planCard() selected-state treatment are the mock's). Copy differs where the product does: this is
// a one-time lifetime unlock, not the mock's monthly/yearly trial.
private val PremiumFeatures = listOf(
    "Unlock every topic & category",
    "All interactive labs & simulators",
    "Multi-language code snippets",
    "Spaced-repetition review mode",
    "Offline access to all content",
    "Ad-free, forever",
)

@Composable
fun PremiumScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val entitlements = remember { EntitlementRepository(context.entitlementDataStore) }
    val billing = remember { BillingProvider.get(context) }

    val owned by entitlements.isPremium.collectAsState(initial = false)
    val price by billing.price.collectAsState()
    val status by billing.status.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    // "Nothing to restore" is only worth saying when the user asked to restore — the same query also
    // runs silently every time this screen opens.
    var restoreRequested by remember { mutableStateOf(false) }

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
                LifetimePlanCard(price = price, owned = owned)

                Spacer(modifier = Modifier.height(18.dp))
                when {
                    owned -> OwnedButton()
                    status == BillingStatus.Unavailable -> UnavailableNote()
                    else -> PurchaseButton(
                        price = price,
                        enabled = activity != null && status != BillingStatus.PurchasePending,
                        onClick = { activity?.let { billing.launchPurchase(it) } },
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Text(
                        "One-time purchase · ",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
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
            "Algora Premium",
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

// The mock shows two plan cards (monthly/yearly); a lifetime-only product means one card, always in
// planCard()'s selected state — 2dp accent border plus the accent-tinted shadow.
@Composable
private fun LifetimePlanCard(price: String?, owned: Boolean) {
    Box(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 9.dp)
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
                .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp))
                .padding(horizontal = 12.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Lifetime", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                price ?: "—",
                fontFamily = SpaceGrotesk,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
            )
            Text(
                if (owned) "purchased" else "one-time · yours forever",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // Mock's green pill badge (line 334), repurposed for the single plan.
        Text(
            "BEST VALUE",
            color = Color.White,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = 12.dp)
                .background(SimColors.Green, RoundedCornerShape(99.dp))
                .padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

@Composable
private fun PurchaseButton(price: String?, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Brush.linearGradient(Gradients.PremiumCta), RoundedCornerShape(15.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (price != null) "Unlock Premium · $price" else "Unlock Premium",
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
