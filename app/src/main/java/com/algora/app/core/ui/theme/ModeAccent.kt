package com.algora.app.core.ui.theme

import com.algora.app.core.data.settings.AccentColor
import com.algora.app.core.nav.AppMode

/**
 * The accent a mode wears while the user leaves the accent setting on Auto.
 *
 * Values are picked to agree with the mock's per-mode topbar ramps rather than invented: DSA's
 * `TopbarDsa` starts at #4f46e5 (= [AccentColor.INDIGO]) and AI's `TopbarAi` runs #db2777 → #f97316
 * (= [AccentColor.PINK], whose `gradientEnd` is the same orange). So the shell recolours with the
 * mode without the Home header and the rest of the app disagreeing.
 */
val AppMode.accent: AccentColor
    get() = when (this) {
        AppMode.DSA -> AccentColor.INDIGO
        AppMode.AI -> AccentColor.PINK
    }
