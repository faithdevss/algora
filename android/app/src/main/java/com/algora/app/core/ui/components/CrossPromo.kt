package com.algora.app.core.ui.components

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.algora.app.R
import com.algora.app.core.ui.theme.SpaceGrotesk

/**
 * Cross-promo for the developer's other apps.
 *
 * Deliberately labelled "More from the developer" and never dressed up as Algora content — Play's
 * ads policy requires app promos be distinguishable from the app itself. Placement is end-of-content
 * only (after a primer, or in Settings), never interstitial, so it can't block anything.
 */
object CrossPromoApp {
    val Systa = CrossPromo(
        packageName = "com.saimum.systa",
        title = "Systa: Learn System Design",
        pitch = "This primer covers the framework and the building blocks. Systa goes deeper — a " +
            "full system design curriculum in the same hands-on style.",
        shortPitch = "The developer's system design companion app",
        iconRes = R.drawable.ic_systa_icon,
    )
}

data class CrossPromo(
    val packageName: String,
    val title: String,
    val pitch: String,
    val shortPitch: String,
    // The promoted app's real listing icon — a stand-in glyph would misrepresent what the user lands
    // on in Play.
    @param:DrawableRes val iconRes: Int,
)

/** The icon tile, sized for whichever surface hosts it. Clipped so it reads as an app icon. */
@Composable
private fun PromoIcon(promo: CrossPromo, size: Dp) {
    Image(
        painter = painterResource(promo.iconRes),
        contentDescription = null,
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(size / 3.4f)),
    )
}

/**
 * Opens the Play listing. Tries the Play app first, then the web listing — `market://` resolves to
 * nothing on devices without Play Store (and on most emulator images), and an unhandled implicit
 * intent would otherwise crash. Neither path needs a `<queries>` entry: an implicit ACTION_VIEW is
 * exempt from Android 11 package visibility filtering.
 */
fun openPlayListing(context: Context, packageName: String) {
    val playApp = Intent(Intent.ACTION_VIEW, "market://details?id=$packageName".toUri())
    try {
        context.startActivity(playApp)
    } catch (_: ActivityNotFoundException) {
        val web = Intent(
            Intent.ACTION_VIEW,
            "https://play.google.com/store/apps/details?id=$packageName".toUri(),
        )
        // A device with neither Play nor a browser is possible; swallowing beats crashing on a promo.
        try {
            context.startActivity(web)
        } catch (_: ActivityNotFoundException) {
            // No handler at all — nothing useful to do.
        }
    }
}

/** Full-width promo card for the end of a content screen. */
@Composable
fun CrossPromoCard(promo: CrossPromo, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        onClick = { openPlayListing(context, promo.packageName) },
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "MORE FROM THE DEVELOPER",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.4.sp,
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                PromoIcon(promo, size = 46.dp)
                Column(modifier = Modifier.padding(start = 12.dp)) {
                    Text(
                        promo.title,
                        fontFamily = SpaceGrotesk,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        "Google Play",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                    )
                }
            }
            Text(
                promo.pitch,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 12.dp),
            )
            Row(
                modifier = Modifier
                    .padding(top = 14.dp)
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(11.dp))
                    .clickable { openPlayListing(context, promo.packageName) }
                    .padding(horizontal = 15.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Text("View on Google Play", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Icon(
                    Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

/** Compact one-line variant for the Settings list. */
@Composable
fun CrossPromoRow(promo: CrossPromo, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { openPlayListing(context, promo.packageName) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PromoIcon(promo, size = 40.dp)
        Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
            Text(
                promo.title,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                promo.shortPitch,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.OpenInNew,
            contentDescription = "Open on Google Play",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
    }
}
