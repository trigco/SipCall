package com.ipdial.service

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.telecom.PhoneAccount
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import com.ipdial.R

object TelecomHelper {

    private const val ACCOUNT_ID = "IPDIAL_SIP_ACCOUNT"

    fun getPhoneAccountHandle(context: Context): PhoneAccountHandle {
        val componentName = ComponentName(context, SipConnectionService::class.java)
        return PhoneAccountHandle(componentName, ACCOUNT_ID)
    }

    fun registerPhoneAccount(context: Context) {
        val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
        val handle = getPhoneAccountHandle(context)
        
        val extras = Bundle().apply {
            putBoolean(PhoneAccount.EXTRA_LOG_SELF_MANAGED_CALLS, false)
        }
        val phoneAccount = PhoneAccount.builder(handle, context.getString(R.string.app_name))
            .setCapabilities(PhoneAccount.CAPABILITY_SELF_MANAGED)
            .setShortDescription("SIP Calls via IPDial")
            .addSupportedUriScheme(PhoneAccount.SCHEME_SIP)
            .addSupportedUriScheme(PhoneAccount.SCHEME_TEL)
            .setExtras(extras)
            .build()
            
        telecomManager.registerPhoneAccount(phoneAccount)
    }

    fun reportIncomingCall(context: Context, number: String, name: String) {
        val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
        val handle = getPhoneAccountHandle(context)
        
        val extras = Bundle().apply {
            putParcelable(TelecomManager.EXTRA_INCOMING_CALL_ADDRESS, Uri.fromParts("sip", number, null))
            putString(TelecomManager.EXTRA_INCOMING_CALL_EXTRAS, name)
        }
        
        try {
            telecomManager.addNewIncomingCall(handle, extras)
        } catch (e: Exception) {
            android.util.Log.e("TelecomHelper", "Error reporting incoming call", e)
        }
    }

    fun placeOutgoingCall(context: Context, number: String, accountId: String): Boolean {
        val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
        val handle = getPhoneAccountHandle(context)
        
        val uri = if (number.startsWith("sip:")) Uri.parse(number) else Uri.fromParts("sip", number, null)
        
        // Put accountId in both the root extras and the outgoing call extras bundle for compatibility
        val extras = Bundle().apply {
            putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, handle)
            putString("com.ipdial.EXTRA_ACCOUNT_ID", accountId)
            val subExtras = Bundle().apply {
                putString("com.ipdial.EXTRA_ACCOUNT_ID", accountId)
            }
            putBundle(TelecomManager.EXTRA_OUTGOING_CALL_EXTRAS, subExtras)
        }
        
        return try {
            telecomManager.placeCall(uri, extras)
            true
        } catch (e: Exception) {
            android.util.Log.e("TelecomHelper", "Error placing call via Telecom", e)
            false
        }
    }
}
