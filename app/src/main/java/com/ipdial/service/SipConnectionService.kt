package com.ipdial.service

import android.telecom.*
import android.util.Log

class SipConnectionService : ConnectionService() {

    companion object {
        private const val TAG = "SipConnectionService"
        private val activeConnections = mutableMapOf<Int, SipConnection>()

        fun getConnection(callId: Int): SipConnection? = activeConnections[callId]
        
        fun registerConnection(callId: Int, connection: SipConnection) {
            activeConnections[callId] = connection
        }
        
        fun removeConnection(callId: Int) {
            activeConnections.remove(callId)
        }
    }

    override fun onCreateOutgoingConnection(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?
    ): Connection {
        Log.d(TAG, "onCreateOutgoingConnection")
        com.ipdial.util.SipLogger.log(TAG, "onCreateOutgoingConnection called by Telecom framework")
        val connection = SipConnection()
        val address = request?.address
        address?.let { connection.setAddress(it, TelecomManager.PRESENTATION_ALLOWED) }
        connection.setInitializing()
        connection.connectionCapabilities = Connection.CAPABILITY_MUTE or Connection.CAPABILITY_SUPPORT_HOLD
        
        // Try getting accountId from root extras or nested outgoing call extras
        var accountId = request?.extras?.getString("com.ipdial.EXTRA_ACCOUNT_ID")
        if (accountId == null) {
            accountId = request?.extras?.getBundle(TelecomManager.EXTRA_OUTGOING_CALL_EXTRAS)?.getString("com.ipdial.EXTRA_ACCOUNT_ID")
        }
        
        val number = address?.schemeSpecificPart
        Log.d(TAG, "Attempting outgoing call: accountId=$accountId, number=$number")
        com.ipdial.util.SipLogger.log(TAG, "Attempting outgoing call via ConnectionService: accountId=$accountId, number=$number")
        
        if (accountId != null && number != null) {
            val success = SipEngine.makeCall(accountId, number)
            if (success) {
                val session = SipEngine.callSession.value
                if (session != null) {
                    connection.callId = session.callId
                    registerConnection(session.callId, connection)
                    connection.setDialing()
                    com.ipdial.util.SipLogger.log(TAG, "Connection registered successfully with callId=${session.callId}")
                } else {
                    Log.e(TAG, "SipEngine.makeCall succeeded but callSession is null (call failed immediately)")
                    com.ipdial.util.SipLogger.log(TAG, "SipEngine.makeCall succeeded but callSession is null (call failed immediately)")
                    connection.setDisconnected(DisconnectCause(DisconnectCause.ERROR))
                    connection.destroy()
                }
            } else {
                Log.e(TAG, "SipEngine.makeCall failed")
                com.ipdial.util.SipLogger.log(TAG, "SipEngine.makeCall failed")
                connection.setDisconnected(DisconnectCause(DisconnectCause.ERROR))
                connection.destroy()
            }
        } else {
            Log.e(TAG, "Cannot start call: accountId or number is null")
            com.ipdial.util.SipLogger.log(TAG, "Cannot start call: accountId or number is null (accountId=$accountId, number=$number)")
            connection.setDisconnected(DisconnectCause(DisconnectCause.ERROR))
            connection.destroy()
        }

        return connection
    }

    override fun onCreateIncomingConnection(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?
    ): Connection {
        Log.d(TAG, "onCreateIncomingConnection")
        com.ipdial.util.SipLogger.log(TAG, "onCreateIncomingConnection called by Telecom framework")
        val connection = SipConnection()
        request?.address?.let { connection.setAddress(it, TelecomManager.PRESENTATION_ALLOWED) }
        connection.setInitializing()
        connection.connectionCapabilities = Connection.CAPABILITY_MUTE or Connection.CAPABILITY_SUPPORT_HOLD
        
        // Linking to the incoming call. In a real app, we'd pass callId via extras.
        // For simplicity, we'll assume the most recent incoming session.
        SipEngine.callSession.value?.let { session ->
            connection.callId = session.callId
            registerConnection(session.callId, connection)
            com.ipdial.util.SipLogger.log(TAG, "Incoming Connection registered with callId=${session.callId}")
        }
        
        return connection
    }
}

class SipConnection : Connection() {
    var callId: Int = -1

    override fun onAnswer() {
        Log.d("SipConnection", "onAnswer(id=$callId)")
        com.ipdial.util.SipLogger.log("SipConnection", "onAnswer called for callId=$callId")
        setActive()
        SipEngine.answerCall(callId) 
    }

    override fun onDisconnect() {
        Log.d("SipConnection", "onDisconnect(id=$callId)")
        com.ipdial.util.SipLogger.log("SipConnection", "onDisconnect called for callId=$callId")
        setDisconnected(DisconnectCause(DisconnectCause.LOCAL))
        SipEngine.hangupCall(callId)
        SipConnectionService.removeConnection(callId)
        destroy()
    }

    override fun onAbort() {
        Log.d("SipConnection", "onAbort(id=$callId)")
        com.ipdial.util.SipLogger.log("SipConnection", "onAbort called for callId=$callId")
        setDisconnected(DisconnectCause(DisconnectCause.CANCELED))
        SipEngine.hangupCall(callId)
        SipConnectionService.removeConnection(callId)
        destroy()
    }

    override fun onHold() {
        Log.d("SipConnection", "onHold(id=$callId)")
        com.ipdial.util.SipLogger.log("SipConnection", "onHold called for callId=$callId")
        setOnHold()
        SipEngine.holdCall(true)
    }

    override fun onUnhold() {
        Log.d("SipConnection", "onUnhold(id=$callId)")
        com.ipdial.util.SipLogger.log("SipConnection", "onUnhold called for callId=$callId")
        setActive()
        SipEngine.holdCall(false)
    }

    override fun onReject() {
        Log.d("SipConnection", "onReject(id=$callId)")
        com.ipdial.util.SipLogger.log("SipConnection", "onReject called for callId=$callId")
        setDisconnected(DisconnectCause(DisconnectCause.REJECTED))
        SipEngine.hangupCall(callId)
        SipConnectionService.removeConnection(callId)
        destroy()
    }
}
