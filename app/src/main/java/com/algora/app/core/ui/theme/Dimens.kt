package com.algora.app.core.ui.theme

import androidx.compose.ui.unit.dp

/**
 * The horizontal gutter every screen's scrolling body sits in. The mock gives all four of its
 * screen containers the same one — `padding:Npx 18px 120px` (docs/design/Algora.dc.html lines 39,
 * 101, 142, 313, 352) — so tabbing between screens never shifts the content sideways.
 *
 * Screen chrome that is not the body has its own inset: `ScreenHeader` uses the mock's narrower
 * 14dp so the back button's own padding lands the icon on the same optical line as the text below.
 */
val ScreenGutter = 18.dp

/** Bottom inset for a screen's scrolling body, so the last row clears the nav bar. */
val ScreenBottomInset = 24.dp
