package com.algora.app.feature.premium

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.algora.app.core.ads.AdsProvider
import com.algora.app.core.ui.components.AdUnlockableLockIcon
import com.algora.app.core.ui.components.LockAmber
import com.algora.app.core.data.entitlement.EntitlementRepository
import com.algora.app.core.data.entitlement.entitlementDataStore
import com.algora.app.core.data.model.Topic
import com.algora.app.core.ui.theme.Gradients
import com.algora.app.core.ui.theme.SpaceGrotesk
import kotlinx.coroutines.launch

// The paywall shown in place of a premium topic's content. Two ways past it: buy premium once, or
// watch a rewarded ad for 24h access to this one topic. Granting the unlock flips the access flow
// TopicDetailScreen observes, so the real content swaps in without any navigation.
@Composable
fun LockedTopicBody(topic: Topic, onGoPremium: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val ads = remember { AdsProvider.get(context) }
    val entitlements = remember { EntitlementRepository(context.entitlementDataStore) }
    val scope = rememberCoroutineScope()

    val adReady by ads.isReady.collectAsState()
    val overlay by ads.overlay.collectAsState()
    var error by remember { mutableStateOf<String?>(null) }
    var awaitingAd by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { ads.preload(context) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(28.dp))
        Box(
            modifier = Modifier
                .size(64.dp)
                .background(LockAmber.copy(alpha = 0.14f), RoundedCornerShape(20.dp)),
            contentAlignment = Alignment.Center,
        ) {
            // Same mark the list rows use, so the row and this screen read as one idea.
            AdUnlockableLockIcon(
                size = 30.dp,
                knockoutColor = MaterialTheme.colorScheme.background,
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "${topic.name} is premium",
            fontFamily = SpaceGrotesk,
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            topic.tagline,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(24.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.linearGradient(Gradients.PremiumCta), RoundedCornerShape(15.dp))
                .clickable(onClick = onGoPremium)
                .padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "Unlock Premium",
                color = Color.White,
                fontFamily = SpaceGrotesk,
                fontWeight = FontWeight.Bold,
                fontSize = 15.5.sp,
            )
        }

        Spacer(modifier = Modifier.height(11.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(15.dp))
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(15.dp))
                .clickable(enabled = activity != null && !awaitingAd) {
                    error = null
                    val host = activity ?: return@clickable
                    awaitingAd = true
                    ads.show(
                        activity = host,
                        onReward = {
                            awaitingAd = false
                            scope.launch { entitlements.grantAdUnlock(topic.id) }
                        },
                        onFailed = { message ->
                            awaitingAd = false
                            error = message
                        },
                    )
                }
                .padding(16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (awaitingAd && overlay == null) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                Icon(Icons.Filled.PlayCircle, contentDescription = null, modifier = Modifier.size(18.dp))
            }
            Spacer(modifier = Modifier.size(10.dp))
            Text(
                "Watch ad · 24h access",
                fontFamily = SpaceGrotesk,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
            )
        }

        if (!adReady && !awaitingAd) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Loading an ad…",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        error?.let {
            Spacer(modifier = Modifier.height(10.dp))
            Text(it, fontSize = 12.5.sp, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "Premium removes ads and unlocks every locked topic, lab and analysis tool — one payment, forever.",
            fontSize = 12.5.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }

    // Only the debug/stand-in ad renders in-app; real AdMob ads own the whole screen themselves.
    overlay?.let { text ->
        AlertDialog(
            onDismissRequest = {},
            confirmButton = {
                TextButton(onClick = {}, enabled = false) { Text("Please wait") }
            },
            title = { Text("Test ad") },
            text = { Text(text) },
        )
    }
}
