package com.ipdial

import android.app.Application
import com.ipdial.service.SipService
import com.ipdial.service.TelecomHelper
import com.startapp.sdk.adsbase.StartAppAd
import com.startapp.sdk.adsbase.StartAppSDK

class IPDialApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Initialize Start.io SDK
        StartAppSDK.init(this, "205857982", true)
        // Enable test ads to verify integration
        StartAppSDK.setTestAdsEnabled(false)
        // Disable splash screen ads if desired
        StartAppAd.disableSplash()

        // Initialize PJSIP engine early
        com.ipdial.service.SipEngine.init(this)
        // Register phone account for Telecom integration
        com.ipdial.service.TelecomHelper.registerPhoneAccount(this)
    }
}
