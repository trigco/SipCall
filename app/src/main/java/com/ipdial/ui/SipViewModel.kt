package com.ipdial.ui

import android.app.Application
import android.content.Context
import android.net.*
import android.os.Bundle
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import androidx.lifecycle.AndroidViewModel
import android.widget.Toast
import androidx.lifecycle.viewModelScope
import com.ipdial.data.model.*
import com.ipdial.data.repository.AccountRepository
import com.ipdial.data.repository.CallLogRepository
import com.ipdial.data.repository.ContactsRepository
import com.ipdial.service.SipEngine
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.os.Handler
import android.os.Looper

class SipViewModel(app: Application) : AndroidViewModel(app) {

    val repo = AccountRepository(app)
    private val logRepo = CallLogRepository.getInstance(app)
    private val contactsRepo = ContactsRepository(app)

    val accounts: StateFlow<List<SipAccount>> = repo.accounts
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val globalRingtone: StateFlow<String?> = repo.globalRingtone
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val darkModeEnabled: StateFlow<Boolean> = repo.darkModeEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
        
    val callingCardsEnabled: StateFlow<Boolean> = repo.callingCardsEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)
        
    val dndEnabled: StateFlow<Boolean> = repo.dndEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val globalVibrate: StateFlow<Boolean> = repo.globalVibrate
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    fun setDarkMode(enabled: Boolean) = viewModelScope.launch { repo.setDarkMode(enabled) }
    fun setCallingCards(enabled: Boolean) = viewModelScope.launch { repo.setCallingCards(enabled) }
    fun setDnd(enabled: Boolean) = viewModelScope.launch { repo.setDnd(enabled) }
    fun setGlobalVibrate(enabled: Boolean) = viewModelScope.launch { repo.setGlobalVibrate(enabled) }

    val callLog: StateFlow<List<CallLogEntry>> = logRepo.entries
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val callSession: StateFlow<CallSession?> = SipEngine.callSession

    // Contacts state
    private val _contacts = MutableStateFlow<List<Contact>>(emptyList())
    val contacts: StateFlow<List<Contact>> = _contacts.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // Dialer state
    private val _dialString = MutableStateFlow("")
    val dialString: StateFlow<String> = _dialString.asStateFlow()

    private val _selectedAccountId = MutableStateFlow<String?>(null)
    val selectedAccountId: StateFlow<String?> = _selectedAccountId.asStateFlow()

    private val _isConnected = MutableStateFlow(true)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    val mostCalledContacts: StateFlow<List<Contact>> = combine(callLog, contacts) { logs, allContacts ->
        val frequencyMap = logs.groupingBy { 
            cleanUri(it.remoteUri)
        }.eachCount()
        
        frequencyMap.entries
            .sortedByDescending { it.value }
            .mapNotNull { entry ->
                val cleanedCallLogNumber = entry.key.filter { it.isDigit() }
                if (cleanedCallLogNumber.length < 10) { // Only consider matching if the call log number is long enough
                    null
                } else {
                    allContacts.find { contact ->
                        contact.numbers.any { num ->
                            val cleanedContactNumber = num.filter { it.isDigit() }
                            cleanedContactNumber.length >= 10 && // Contact number must also be long enough
                            (cleanedCallLogNumber.contains(cleanedContactNumber) || cleanedContactNumber.contains(cleanedCallLogNumber))
                        }
                    }
                }
            }
            .distinctBy { it.id }
            .take(5)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var searchJob: Job? = null

    init {
        val connectivityManager = app.getSystemService(Application.CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkRequest = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        
        connectivityManager.registerNetworkCallback(networkRequest, object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                _isConnected.value = true
            }

            override fun onLost(network: Network) {
                _isConnected.value = false
            }
        })

        // Auto-select default/enabled account
        viewModelScope.launch(Dispatchers.IO) {
            accounts.collectLatest { list ->
                withContext(Dispatchers.Main) {
                    val currentSelected = list.find { it.id == _selectedAccountId.value }
                    if (currentSelected == null || !currentSelected.isEnabled) {
                        _selectedAccountId.value = list.firstOrNull { it.isEnabled && it.isDefault }?.id
                            ?: list.firstOrNull { it.isEnabled }?.id
                            ?: list.firstOrNull()?.id
                    }
                }
            }
        }
        refreshContacts()

        // Clear keypad after call ends
        viewModelScope.launch {
            callSession.map { it == null }.distinctUntilChanged().collect { isNull ->
                if (isNull) {
                    _dialString.value = ""
                }
            }
        }
    }

    fun refreshContacts() {
        viewModelScope.launch {
            _contacts.value = contactsRepo.getContacts(_searchQuery.value)
        }
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(300)
            refreshContacts()
        }
    }

    fun dialPad(char: Char) {
        _dialString.value += char
        if (callSession.value?.state == CallState.CONFIRMED) {
            SipEngine.sendDtmf(char)
        }
    }

    fun backspace() {
        val s = _dialString.value
        if (s.isNotEmpty()) _dialString.value = s.dropLast(1)
    }

    fun clearDial() { _dialString.value = "" }

    fun prefillDialer(number: String) { _dialString.value = number }

    fun deleteCallLog(entry: CallLogEntry) {
        viewModelScope.launch(Dispatchers.IO) {
            logRepo.delete(entry)
        }
    }

    fun selectAccount(id: String) { _selectedAccountId.value = id }

    fun makeCall(overrideNumber: String? = null) {
        val rawInput = (overrideNumber ?: _dialString.value).trim()
        if (rawInput.isBlank()) {
            Toast.makeText(getApplication(), "Please enter a number", Toast.LENGTH_SHORT).show()
            return
        }

        // Clean formatting characters (spaces, dashes, parentheses)
        val cleanedInput = rawInput.replace(" ", "")
            .replace("-", "")
            .replace("(", "")
            .replace(")", "")

        var account = accounts.value.find { it.id == _selectedAccountId.value }
        if (account == null || !account.isEnabled) {
            account = accounts.value.firstOrNull { it.isEnabled }
            if (account != null) {
                _selectedAccountId.value = account.id
            }
        }

        if (account == null) {
            Toast.makeText(getApplication(), "No enabled SIP account configured", Toast.LENGTH_SHORT).show()
            return
        }

        if (account.regStatus != RegStatus.REGISTERED) {
            Toast.makeText(getApplication(), "Account is not registered", Toast.LENGTH_SHORT).show()
            return
        }

        if (!_isConnected.value) {
            Toast.makeText(getApplication(), "No internet connection", Toast.LENGTH_SHORT).show()
            return
        }

        if (callSession.value != null) {
            Toast.makeText(getApplication(), "A call is already in progress", Toast.LENGTH_SHORT).show()
            return
        }

        val transportSuffix = when (account.transport) {
            Transport.TCP -> ";transport=tcp"
            Transport.TLS -> ";transport=tls"
            else -> ""
        }

        val finalUri = if (cleanedInput.contains("@")) {
            val base = if (cleanedInput.startsWith("sip:")) cleanedInput else "sip:$cleanedInput"
            if (!base.contains("transport=") && transportSuffix.isNotEmpty()) {
                base + transportSuffix
            } else {
                base
            }
        } else {
            var num = cleanedInput.removePrefix("sip:")

            // BD automatic handling: 017... (11 digits) or 17... (10 digits)
            if (num.all { it.isDigit() }) {
                if (num.length == 11 && num.startsWith("0")) {
                    num = "+880${num.drop(1)}"
                } else if (num.length == 10 && num.startsWith("1")) {
                    num = "+880$num"
                }
            }

            val host = if (account.port != null && account.port > 0 && !account.domain.contains(":")) {
                "${account.domain}:${account.port}"
            } else {
                account.domain
            }
            "sip:$num@$host$transportSuffix"
        }

        android.util.Log.d("SipViewModel", "Direct Dialing: $finalUri")

        if (callSession.value == null) {
            // Use TelecomHelper to place the call for proper system integration
            val success = try {
                com.ipdial.service.TelecomHelper.placeOutgoingCall(getApplication(), finalUri, account.id)
            } catch (e: Exception) {
                android.util.Log.e("SipViewModel", "TelecomManager failure, falling back", e)
                false
            }
            if (!success) {
                android.util.Log.i("SipViewModel", "TelecomManager call failed to initiate, falling back to direct SipEngine calling")
                val engineStarted = SipEngine.makeCall(account.id, finalUri)
                if (!engineStarted) {
                    Toast.makeText(getApplication(), "Call not sent", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun cleanUri(uri: String): String {
        return uri.replace("<", "").replace(">", "").removePrefix("sip:")
            .substringBefore("@")
            .substringBefore(";")
    }
    fun answerCall() {
        val id = callSession.value?.callId ?: return
        viewModelScope.launch(Dispatchers.IO) {
            SipEngine.answerCall(id)
            withContext(Dispatchers.Main) {
                com.ipdial.service.SipConnectionService.getConnection(id)?.setActive()
            }
        }
    }

    fun hangup() { 
        val id = callSession.value?.callId ?: -1
        viewModelScope.launch(Dispatchers.IO) {
            SipEngine.hangupCall(id)
            withContext(Dispatchers.Main) {
                if (id != -1) {
                    com.ipdial.service.SipConnectionService.getConnection(id)?.let {
                        it.setDisconnected(android.telecom.DisconnectCause(android.telecom.DisconnectCause.LOCAL))
                        it.destroy()
                    }
                }
            }
        }
    }
    fun toggleMute() { SipEngine.setMute(!(callSession.value?.isMuted ?: false)) }
    fun toggleSpeaker() { SipEngine.setSpeaker(!(callSession.value?.isSpeaker ?: false)) }
    fun toggleHold() { SipEngine.holdCall(!(callSession.value?.isOnHold ?: false)) }

    fun toggleRecording() {
        val session = callSession.value ?: return
        if (session.isRecording) {
            SipEngine.stopRecording()
        } else {
            // Priority: Internal storage as requested
            val baseDir = getApplication<Application>().getExternalFilesDir(android.os.Environment.DIRECTORY_MUSIC)
            val folder = java.io.File(baseDir, "IPDialRecordings")
            try {
                if (!folder.exists()) folder.mkdirs()
                val recFile = java.io.File(folder, "IPDial_${System.currentTimeMillis()}.wav")
                // Using PJSIP internal WAV recorder (AAC natively locked by SIP mic)
                SipEngine.startRecording(recFile.absolutePath)
            } catch (e: Exception) {
                android.util.Log.e("SipViewModel", "Recording failed", e)
            }
        }
    }

    fun saveAccount(account: SipAccount) = viewModelScope.launch(Dispatchers.IO) {
        repo.saveAccount(account)
    }

    fun deleteAccount(id: String) = viewModelScope.launch(Dispatchers.IO) {
        repo.deleteAccount(id)
    }

    fun setDefaultAccount(id: String) = viewModelScope.launch { repo.setDefault(id) }

    fun toggleContactFavorite(contact: Contact) = viewModelScope.launch {
        val newFavoriteStatus = !contact.isFavorite
        _contacts.value = _contacts.value.map {
            if (it.id == contact.id) it.copy(isFavorite = newFavoriteStatus) else it
        }
        contactsRepo.toggleFavorite(contact.id, newFavoriteStatus)
    }

    fun callBack(entry: CallLogEntry) {
        val accId = entry.accountId.ifBlank {
            _selectedAccountId.value ?: accounts.value.firstOrNull { it.isEnabled }?.id ?: accounts.value.firstOrNull()?.id ?: return
        }
        _selectedAccountId.value = accId
        makeCall(cleanUri(entry.remoteUri))
    }

    fun logCall(entry: CallLogEntry) = viewModelScope.launch {
        logRepo.insert(entry)
        // Maintain a maximum of 50 entries in the call log
        val logs = logRepo.entries.first()
        if (logs.size > 50) {
            val toDelete = logs.sortedByDescending { it.timestampMs }.drop(50)
            toDelete.forEach { logEntry ->
                logRepo.delete(logEntry)
            }
        }
    }
}
