package com.ipdial

import android.app.Application
import com.ipdial.service.SipService
import com.ipdial.service.TelecomHelper

class IPDialApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Initialize PJSIP engine early
        com.ipdial.service.SipEngine.init(this)
        // Register phone account for Telecom integration
        com.ipdial.service.TelecomHelper.registerPhoneAccount(this)
    }
}
