package com.algora.app.core.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Amber from the mock's lock treatment (docs/design/Algora.dc.html endIcon).
val LockAmber = Color(0xFFF59E0B)

// A plain padlock says "buy to open", which is only half true here: every locked topic also opens
// for 24h after a rewarded ad. So the padlock body carries a knocked-out play triangle — locked,
// but a video is a way in.
//
// Built as a composite rather than an ImageVector because Icon() tints a whole vector with one
// color; the knockout needs to be the row's own background color.
@Composable
fun AdUnlockableLockIcon(
    size: Dp,
    modifier: Modifier = Modifier,
    tint: Color = LockAmber,
    knockoutColor: Color = MaterialTheme.colorScheme.surface,
) {
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Icon(
            imageVector = Icons.Filled.Lock,
            contentDescription = "Premium — buy or watch an ad to open",
            tint = tint,
            modifier = Modifier.size(size),
        )
        Canvas(modifier = Modifier.size(size)) {
            // Material's filled Lock puts its body between ~42% and ~83% of the viewport height;
            // this centres the triangle in that body.
            val bodyCenterY = this.size.height * 0.63f
            val triangleWidth = this.size.width * 0.24f
            val triangleHeight = this.size.height * 0.28f
            val left = (this.size.width - triangleWidth) / 2f + this.size.width * 0.02f
            val top = bodyCenterY - triangleHeight / 2f

            val play = Path().apply {
                moveTo(left, top)
                lineTo(left + triangleWidth, bodyCenterY)
                lineTo(left, top + triangleHeight)
                close()
            }
            drawPath(path = play, color = knockoutColor)
        }
    }
}
