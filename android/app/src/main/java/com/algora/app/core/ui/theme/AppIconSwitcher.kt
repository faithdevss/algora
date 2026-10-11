package com.algora.app.core.ui.theme

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.algora.app.core.data.settings.AppTheme

/**
 * Launcher icons are `<activity-alias>`es in the manifest, one per [AppTheme], each with its own
 * adaptive icon. Exactly one is enabled at a time; enabling a theme's alias and disabling the rest
 * is how the home-screen icon changes.
 *
 * Disabling the alias the running task was launched through makes the system tear that task down,
 * `DONT_KILL_APP` or not — the app would vanish the moment a theme is picked. So [request] only
 * records the pick, and the swap happens once the whole app is in the background.
 */
object AppIconSwitcher {
    private var pending: AppTheme? = null
    private var observing = false

    fun request(context: Context, theme: AppTheme) {
        val appContext = context.applicationContext
        pending = theme
        if (observing) return
        observing = true
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) {
                pending?.let { apply(appContext, it) }
                pending = null
            }
        })
    }

    private fun apply(context: Context, theme: AppTheme) {
        val pm = context.packageManager
        // Enable before disabling, so there is never a moment with no launcher entry.
        val ordered = AppTheme.entries.sortedBy { it != theme }
        for (option in ordered) {
            val state = if (option == theme) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            }
            val alias = ComponentName(context.packageName, "com.algora.app.Icon${option.label}")
            if (pm.getComponentEnabledSetting(alias) != state) {
                pm.setComponentEnabledSetting(alias, state, PackageManager.DONT_KILL_APP)
            }
        }
    }
}
