package com.ipdial.service

import android.content.Context
import android.util.Log
import com.ipdial.data.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.pjsip.pjsua2.*

/**
 * PJSIP engine singleton.
 * Manages the Endpoint lifecycle, account registration, and call sessions.
 */
class SipLogWriter : LogWriter() {
    override fun write(entry: LogEntry) {
        val msg = entry.msg
        if (!msg.isNullOrBlank()) {
            com.ipdial.util.SipLogger.log("PJSIP", msg.trim())
        }
    }
}

object SipEngine {

    private const val TAG = "SipEngine"

    private var endpoint: Endpoint? = null
    private val accountMap = mutableMapOf<String, PjAccount>()   // accountId -> PjAccount
    private val accountConfigs = mutableMapOf<String, SipAccount>() // accountId -> SipAccount configuration
    private val callMap = mutableMapOf<Int, PjCall>()             // callId -> PjCall
    private val registeredThreads = java.util.Collections.synchronizedSet(mutableSetOf<Long>())
    
    private var udpTransportId: Int = -1
    private var tcpTransportId: Int = -1
    private var tlsTransportId: Int = -1

    private val _callSession = MutableStateFlow<CallSession?>(null)
    val callSession: StateFlow<CallSession?> = _callSession.asStateFlow()

    private val _registrationEvents = MutableStateFlow<Pair<String, RegStatus>?>(null)
    val registrationEvents: StateFlow<Pair<String, RegStatus>?> = _registrationEvents.asStateFlow()

    var onIncomingCall: ((CallSession) -> Unit)? = null

    private var recorder: AudioMediaRecorder? = null
    private var logWriter: SipLogWriter? = null

    private fun log(message: String, isError: Boolean = false) {
        if (isError) {
            Log.e(TAG, message)
            com.ipdial.util.SipLogger.log("ERROR", message)
        } else {
            Log.d(TAG, message)
            com.ipdial.util.SipLogger.log("SipEngine", message)
        }
    }

    private fun registerCurrentThread() {
        val ep = endpoint ?: return
        val threadId = Thread.currentThread().id
        if (registeredThreads.contains(threadId)) {
            return
        }
        try {
            if (!ep.libIsThreadRegistered()) {
                val threadName = Thread.currentThread().name ?: "SipEngineThread"
                ep.libRegisterThread(threadName)
            }
            registeredThreads.add(threadId)
        } catch (e: Throwable) {
            log("Failed to register thread: ${e.message}", true)
        }
    }

    fun init(context: Context) {
        if (endpoint != null) return
        try {
            System.loadLibrary("pjsua2")
            
            val writer = SipLogWriter()
            this.logWriter = writer
            
            endpoint = Endpoint().apply {
                libCreate()
                val epCfg = EpConfig().apply {
                    logConfig.level = 4
                    logConfig.consoleLevel = 4
                    logConfig.writer = writer
                    medConfig.apply {
                        ecOptions = 0
                        ecTailLen = 200
                        noVad = false
                        clockRate = 48000
                        quality = 8
                    }
                    uaConfig.apply {
                        userAgent = "IPDial/1.0 (Android)"
                        maxCalls = 4
                        stunServer.add("stun.l.google.com:19302")
                    }
                }
                libInit(epCfg)

                val sipTpCfg = TransportConfig()
                sipTpCfg.port = 0
                try {
                    udpTransportId = transportCreate(pjsip_transport_type_e.PJSIP_TRANSPORT_UDP, sipTpCfg)
                } catch (e: Exception) { log("Failed to create UDP transport: ${e.message}", true) }
                
                val tcpTpCfg = TransportConfig()
                tcpTpCfg.port = 0
                try {
                    tcpTransportId = transportCreate(pjsip_transport_type_e.PJSIP_TRANSPORT_TCP, tcpTpCfg)
                } catch (e: Exception) { log("Failed to create TCP transport: ${e.message}", true) }
                
                val tlsTpCfg = TransportConfig()
                tlsTpCfg.tlsConfig.verifyServer = false
                tlsTpCfg.tlsConfig.verifyClient = false
                try {
                    tlsTransportId = transportCreate(pjsip_transport_type_e.PJSIP_TRANSPORT_TLS, tlsTpCfg)
                } catch (e: Exception) { log("Failed to create TLS transport: ${e.message}", true) }

                libStart()
                log("PJSIP started successfully")
            }
        } catch (e: Throwable) {
            log("PJSIP init failed: ${e.message}", true)
        }
    }

    fun addAccount(account: SipAccount) {
        registerCurrentThread()
        try {
            val existingConfig = accountConfigs[account.id]
            if (existingConfig != null) {
                val hasChanged = existingConfig.username != account.username ||
                        existingConfig.password != account.password ||
                        existingConfig.domain != account.domain ||
                        existingConfig.proxy != account.proxy ||
                        existingConfig.port != account.port ||
                        existingConfig.transport != account.transport ||
                        existingConfig.codec != account.codec ||
                        existingConfig.ecEnabled != account.ecEnabled ||
                        existingConfig.nsEnabled != account.nsEnabled ||
                        existingConfig.agcEnabled != account.agcEnabled

                if (!hasChanged) {
                    log("Account ${account.id} configuration unchanged, skipping addAccount")
                    return
                }
            }

            accountMap[account.id]?.let { removeAccount(account.id) }

            val acfg = AccountConfig().apply {
                idUri = "sip:${account.username}@${account.domain}"
                
                // If port is null or 0, let PJSIP handle it by not appending it
                regConfig.registrarUri = if (account.port != null && account.port > 0) {
                    "sip:${account.domain}:${account.port}"
                } else {
                    "sip:${account.domain}"
                }

                regConfig.timeoutSec = 300

                val cred = AuthCredInfo("digest", "*", account.username, 0, account.password)
                sipConfig.authCreds.add(cred)

                if (account.proxy.isNotBlank()) {
                    sipConfig.proxies.add("sip:${account.proxy}")
                }

                sipConfig.transportId = when (account.transport) {
                    Transport.TCP -> tcpTransportId
                    Transport.TLS -> tlsTransportId
                    else -> udpTransportId
                }

                mediaConfig.apply {
                    // Use OPTIONAL instead of MANDATORY for broader compatibility
                    srtpUse = if (account.transport == Transport.TLS)
                        pjmedia_srtp_use.PJMEDIA_SRTP_OPTIONAL
                    else
                        pjmedia_srtp_use.PJMEDIA_SRTP_DISABLED
                }

                // ICE can cause delays if not configured perfectly, disabling for now
                natConfig.iceEnabled = false
                natConfig.turnEnabled = false
                natConfig.sipStunUse = pjsua_stun_use.PJSUA_STUN_USE_DEFAULT
            }

            val pjAcc = PjAccount(account.id)
            try {
                pjAcc.create(acfg)
                accountMap[account.id] = pjAcc
                accountConfigs[account.id] = account
                log("Account added successfully: ${account.id} (${account.username})")
                configureCodecs(account.codec, account.ecEnabled, account.nsEnabled, account.agcEnabled)
            } catch (e: Throwable) {
                pjAcc.delete()
                throw e
            }
        } catch (e: Throwable) {
            log("addAccount failed: ${e.message}", true)
        }
    }

    fun removeAccount(accountId: String) {
        registerCurrentThread()
        try {
            accountMap[accountId]?.delete()
            accountMap.remove(accountId)
            accountConfigs.remove(accountId)
            log("Account removed: $accountId")
        } catch (e: Throwable) {
            log("removeAccount failed: ${e.message}", true)
        }
    }

    fun makeCall(accountId: String, destination: String): Boolean {
        registerCurrentThread()
        return try {
            val pjAcc = accountMap[accountId] ?: run {
                log("makeCall failed: accountId $accountId not found in accountMap. Current accounts: ${accountMap.keys}", true)
                return false
            }
            val destUri = if (destination.startsWith("sip:")) destination else "sip:$destination"
            
            log("making call to $destUri")
            val call = PjCall(pjAcc)
            
            // Set call session to CALLING before placing the call.
            // Using -1 temporarily; it gets updated either in the try block below or in onCallState.
            _callSession.value = CallSession(
                callId = -1,
                accountId = accountId,
                remoteUri = destUri,
                direction = CallDirection.OUTGOING,
                state = CallState.CALLING
            )
            
            val prm = CallOpParam(true).apply {
                opt.audioCount = 1
                opt.videoCount = 0
            }
            
            try {
                call.makeCall(destUri, prm)
                val realId = call.getId()
                callMap[realId] = call
                log("call.makeCall returned successfully. assigned call ID = $realId")
                
                // Only update if call session was not cleared/disconnected in the meantime
                _callSession.value?.let { currentSession ->
                    if (currentSession.state != CallState.DISCONNECTED) {
                        _callSession.value = currentSession.copy(callId = realId)
                    }
                }
                true
            } catch (e: Throwable) {
                call.delete()
                _callSession.value = null
                log("call.makeCall failed: ${e.message}", true)
                false
            }
        } catch (e: Throwable) {
            log("makeCall failed: ${e.message}", true)
            false
        }
    }

    fun answerCall(callId: Int) {
        registerCurrentThread()
        callMap[callId]?.let { call ->
            try {
                val prm = CallOpParam(true).apply { statusCode = pjsip_status_code.PJSIP_SC_OK }
                call.answer(prm)
            } catch (e: Throwable) { }
        }
    }

    fun hangupCall(callId: Int = -1) {
        registerCurrentThread()
        val id = if (callId >= 0) callId else _callSession.value?.callId ?: return
        val call = callMap[id]
        if (call != null) {
            try {
                val prm = CallOpParam().apply { statusCode = pjsip_status_code.PJSIP_SC_DECLINE }
                call.hangup(prm)
            } catch (e: Throwable) { }
        } else {
            _callSession.value = null
        }
    }

    fun setMute(muted: Boolean) {
        registerCurrentThread()
        _callSession.value?.let { session ->
            callMap[session.callId]?.let { call ->
                try {
                    val ci = call.info
                    if (ci.media.size > 0) {
                        val mi = ci.media.get(0)
                        if (mi.type == pjmedia_type.PJMEDIA_TYPE_AUDIO) {
                            val aud = AudioMedia.typecastFromMedia(call.getMedia(mi.index))
                            if (muted) aud.adjustTxLevel(0f) else aud.adjustTxLevel(1f)
                        }
                    }
                    _callSession.value = session.copy(isMuted = muted)
                } catch (e: Throwable) { }
            }
        }
    }

    fun setSpeaker(enabled: Boolean) {
        _callSession.value = _callSession.value?.copy(isSpeaker = enabled)
    }

    fun startRecording(filePath: String) {
        registerCurrentThread()
        try {
            recorder?.delete()
            recorder = AudioMediaRecorder()
            recorder?.createRecorder(filePath)
            
            _callSession.value?.let { session ->
                callMap[session.callId]?.let { call ->
                    val ci = call.info
                    for (i in 0 until ci.media.size) {
                        val mi = ci.media.get(i)
                        if (mi.type == pjmedia_type.PJMEDIA_TYPE_AUDIO && 
                            mi.status == pjsua_call_media_status.PJSUA_CALL_MEDIA_ACTIVE) {
                            val aud = AudioMedia.typecastFromMedia(call.getMedia(mi.index.toLong()))
                            aud.startTransmit(recorder)
                            endpoint?.audDevManager()?.captureDevMedia?.startTransmit(recorder)
                        }
                    }
                }
                _callSession.value = session.copy(isRecording = true)
            }
        } catch (e: Throwable) {
            Log.e(TAG, "startRecording failed: ${e.message}")
        }
    }

    fun stopRecording() {
        registerCurrentThread()
        try {
            recorder?.delete()
            recorder = null
            _callSession.value = _callSession.value?.copy(isRecording = false)
        } catch (e: Throwable) { }
    }

    fun sendDtmf(digit: Char) {
        registerCurrentThread()
        _callSession.value?.let { session ->
            callMap[session.callId]?.let { call ->
                try { call.dialDtmf(digit.toString()) } catch (e: Throwable) { }
            }
        }
    }

    fun holdCall(onHold: Boolean) {
        registerCurrentThread()
        _callSession.value?.let { session ->
            callMap[session.callId]?.let { call ->
                try {
                    val prm = CallOpParam()
                    if (onHold) call.setHold(prm) else call.reinvite(prm)
                    _callSession.value = session.copy(isOnHold = onHold)
                } catch (e: Throwable) { }
            }
        }
    }

    private fun configureCodecs(preferred: PreferredCodec, ecEnabled: Boolean, nsEnabled: Boolean, agcEnabled: Boolean) {
        val ep = endpoint ?: return
        try {
            val codecs = ep.codecEnum2()
            for (i in 0 until codecs.size) {
                val codec = codecs.get(i)
                val codecId = codec.codecId
                val name = codecId.lowercase()
                
                // Keep only G722, PCMA, PCMU, and OPUS. Disable all others (priority = 0)
                val isPreferred = when (preferred) {
                    PreferredCodec.OPUS -> name.contains("opus")
                    PreferredCodec.G722 -> name.contains("g722") && !name.contains("g7221")
                    PreferredCodec.G711U -> name.contains("pcmu")
                    PreferredCodec.G711A -> name.contains("pcma")
                }
                
                val keep = when (preferred) {
                    PreferredCodec.OPUS -> name.contains("opus") || name.contains("pcma") || name.contains("pcmu")
                    PreferredCodec.G722 -> (name.contains("g722") && !name.contains("g7221")) || name.contains("pcma") || name.contains("pcmu")
                    PreferredCodec.G711U, PreferredCodec.G711A -> name.contains("pcmu") || name.contains("pcma")
                }
                
                if (keep) {
                    val priority = if (isPreferred) 250 else 150
                    ep.codecSetPriority(codecId, priority.toShort())
                } else {
                    ep.codecSetPriority(codecId, 0.toShort())
                }
            }
        } catch (e: Throwable) {
            log("Error configuring codecs: ${e.message}", true)
        }
    }

    fun destroy() {
        try {
            registerCurrentThread()
            callMap.values.forEach { it.delete() }
            callMap.clear()
            accountMap.values.forEach { it.delete() }
            accountMap.clear()
            
            recorder?.delete()
            recorder = null
            
            endpoint?.libDestroy()
            endpoint?.delete()
            endpoint = null
            
            logWriter?.delete()
            logWriter = null
        } catch (e: Throwable) { }
    }

    class PjAccount(private val accountId: String) : Account() {
        override fun onRegState(prm: OnRegStateParam) {
            try {
                val ai = info
                val status = when {
                    ai.regIsActive -> RegStatus.REGISTERED
                    ai.regStatus >= 300 -> RegStatus.ERROR
                    else -> RegStatus.UNREGISTERED
                }
                log("Account $accountId reg status: $status (code=${ai.regStatus}, reason=${ai.regStatusText})")
                _registrationEvents.value = Pair(accountId, status)
            } catch (e: Throwable) { }
        }

        override fun onIncomingCall(prm: OnIncomingCallParam) {
            try {
                val call = PjCall(this, prm.callId)
                callMap[prm.callId] = call
                
                val opPrm = CallOpParam().apply { statusCode = pjsip_status_code.PJSIP_SC_RINGING }
                try {
                    call.answer(opPrm)
                } catch (e: Throwable) {
                    call.delete()
                    callMap.remove(prm.callId)
                    throw e
                }

                val ci = call.info
                val session = CallSession(
                    callId = prm.callId,
                    accountId = accountId,
                    remoteUri = ci.remoteUri ?: "",
                    remoteDisplayName = ci.remoteContact ?: ci.remoteUri ?: "",
                    direction = CallDirection.INCOMING,
                    state = CallState.INCOMING
                )
                _callSession.value = session
                onIncomingCall?.invoke(session)
            } catch (e: Throwable) { }
        }
    }

    class PjCall(acct: Account, callId: Int = -1) : Call(acct, callId) {
        override fun onCallState(prm: OnCallStateParam) {
            try {
                val currentCallId = getId()
                val ci = info
                log("Call $currentCallId state changed to ${ci.stateText} (code=${ci.lastStatusCode}, reason=${ci.lastReason})")
                val newState = when (ci.state) {
                    pjsip_inv_state.PJSIP_INV_STATE_CALLING -> CallState.CALLING
                    pjsip_inv_state.PJSIP_INV_STATE_INCOMING -> CallState.INCOMING
                    pjsip_inv_state.PJSIP_INV_STATE_EARLY -> CallState.EARLY
                    pjsip_inv_state.PJSIP_INV_STATE_CONNECTING -> CallState.CONNECTING
                    pjsip_inv_state.PJSIP_INV_STATE_CONFIRMED -> CallState.CONFIRMED
                    pjsip_inv_state.PJSIP_INV_STATE_DISCONNECTED -> CallState.DISCONNECTED
                    else -> CallState.IDLE
                }
                
                if (newState == CallState.DISCONNECTED) {
                    log("Call $currentCallId DISCONNECTED (code=${ci.lastStatusCode}, reason=${ci.lastReason})")
                    callMap.remove(currentCallId)
                    _callSession.value = null
                    
                    SipConnectionService.getConnection(currentCallId)?.let { conn ->
                        conn.setDisconnected(android.telecom.DisconnectCause(android.telecom.DisconnectCause.REMOTE))
                        conn.destroy()
                        SipConnectionService.removeConnection(currentCallId)
                    }
                    
                    val callToDelete = this
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        try {
                            callToDelete.delete()
                        } catch (e: Throwable) {
                            Log.e("SipEngine", "Failed to delete call on main loop", e)
                        }
                    }
                } else {
                    _callSession.value = _callSession.value?.copy(state = newState, callId = currentCallId)
                    
                    SipConnectionService.getConnection(currentCallId)?.let { conn ->
                        when (newState) {
                            CallState.CONFIRMED -> conn.setActive()
                            CallState.EARLY -> if (_callSession.value?.direction == CallDirection.OUTGOING) {
                                conn.setRinging()
                            }
                            CallState.CONNECTING -> conn.setDialing()
                            else -> {}
                        }
                    }
                }
            } catch (e: Throwable) { }
        }

        override fun onCallMediaState(prm: OnCallMediaStateParam) {
            try {
                val ci = info
                for (i in 0 until ci.media.size) {
                    val mi = ci.media.get(i)
                    if (mi.type == pjmedia_type.PJMEDIA_TYPE_AUDIO &&
                        mi.status == pjsua_call_media_status.PJSUA_CALL_MEDIA_ACTIVE) {
                        val aud = AudioMedia.typecastFromMedia(getMedia(mi.index.toLong()))
                        aud.startTransmit(endpoint?.audDevManager()?.playbackDevMedia)
                        endpoint?.audDevManager()?.captureDevMedia?.startTransmit(aud)
                        
                        recorder?.let { 
                            aud.startTransmit(it)
                            endpoint?.audDevManager()?.captureDevMedia?.startTransmit(it)
                        }
                    }
                }
            } catch (e: Throwable) { }
        }
    }
}
