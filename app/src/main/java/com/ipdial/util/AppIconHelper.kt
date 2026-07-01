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
        android.util.Log.d("AppIconHelper", "Setting icon to: $aliasName ($targetAlias)")

        // 1. First, ensure the target alias is enabled
        pm.setComponentEnabledSetting(
            ComponentName(packageName, targetAlias),
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP
        )

        // 2. Disable all other aliases
        ALIASES.values.forEach { alias ->
            if (alias != targetAlias) {
                pm.setComponentEnabledSetting(
                    ComponentName(packageName, alias),
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP
                )
            }
        }

        // 3. FORCE ensure MainActivity is ENABLED. 
        // This is the stable entry point for Android Studio.
        pm.setComponentEnabledSetting(
            ComponentName(packageName, MAIN_ACTIVITY),
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP
        )
    }

    /**
     * Emergency recovery to ensure the app is launchable by the IDE.
     */
    fun forceEnableMainActivity(context: Context) {
        try {
            val pm = context.packageManager
            val packageName = context.packageName
            val mainComp = ComponentName(packageName, MAIN_ACTIVITY)
            
            // Log the current state for debugging
            val state = pm.getComponentEnabledSetting(mainComp)
            android.util.Log.d("AppIconHelper", "MainActivity state was: $state")

            pm.setComponentEnabledSetting(
                mainComp,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )
            android.util.Log.d("AppIconHelper", "Forced MainActivity to ENABLED")
        } catch (e: Exception) {
            android.util.Log.e("AppIconHelper", "Emergency recovery failed", e)
        }
    }
}
