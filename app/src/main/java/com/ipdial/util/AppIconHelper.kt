package com.ipdial.util

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

object AppIconHelper {
    private const val MAIN_ACTIVITY = "com.ipdial.MainActivity"
    private const val DEFAULT_ALIAS = "com.ipdial.MainActivityDefault"
    
    private val ALIASES = mapOf(
        "Default" to DEFAULT_ALIAS,
        "Green"   to "com.ipdial.MainActivityGreen",
        "Blue"    to "com.ipdial.MainActivityBlue",
        "Red"     to "com.ipdial.MainActivityRed"
    )

    fun setAppIcon(context: Context, aliasName: String) {
        val pm = context.packageManager
        val packageName = context.packageName
        
        val targetAlias = ALIASES[aliasName] ?: DEFAULT_ALIAS

        // We only want to enable/disable the LAUNCHER aliases.
        // The core MainActivity must ALWAYS be enabled.
        
        // 1. Enable the target alias
        pm.setComponentEnabledSetting(
            ComponentName(packageName, targetAlias),
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP
        )

        // 2. Disable all other launcher aliases
        ALIASES.values.forEach { alias ->
            if (alias != targetAlias) {
                pm.setComponentEnabledSetting(
                    ComponentName(packageName, alias),
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP
                )
            }
        }

        // 3. Ensure target activity itself is enabled (it doesn't have LAUNCHER category)
        pm.setComponentEnabledSetting(
            ComponentName(packageName, MAIN_ACTIVITY),
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP
        )
    }
}
