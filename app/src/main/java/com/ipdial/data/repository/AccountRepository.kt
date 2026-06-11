package com.ipdial.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.ipdial.data.model.SipAccount
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "ipdial_accounts")

class AccountRepository(private val context: Context) {

    private val gson = Gson()
    private val ACCOUNTS_KEY = stringPreferencesKey("accounts")
    private val RINGTONE_KEY = stringPreferencesKey("global_ringtone")

    val accounts: Flow<List<SipAccount>> = context.dataStore.data.map { prefs ->
        val json = prefs[ACCOUNTS_KEY] ?: return@map emptyList()
        val type = object : TypeToken<List<SipAccount>>() {}.type
        gson.fromJson(json, type) ?: emptyList()
    }

    val globalRingtone: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[RINGTONE_KEY]
    }

    suspend fun setGlobalRingtone(uri: String?) {
        context.dataStore.edit { prefs ->
            if (uri == null) prefs.remove(RINGTONE_KEY)
            else prefs[RINGTONE_KEY] = uri
        }
    }

    suspend fun saveAccount(account: SipAccount) {
        context.dataStore.edit { prefs ->
            val current = getAccountsList(prefs).toMutableList()
            val idx = current.indexOfFirst { it.id == account.id }
            if (idx >= 0) current[idx] = account else current.add(account)
            prefs[ACCOUNTS_KEY] = gson.toJson(current)
        }
    }

    suspend fun deleteAccount(accountId: String) {
        context.dataStore.edit { prefs ->
            val current = getAccountsList(prefs).filter { it.id != accountId }
            prefs[ACCOUNTS_KEY] = gson.toJson(current)
        }
    }

    suspend fun setDefault(accountId: String) {
        context.dataStore.edit { prefs ->
            val current = getAccountsList(prefs).map { acc ->
                acc.copy(isDefault = acc.id == accountId)
            }
            prefs[ACCOUNTS_KEY] = gson.toJson(current)
        }
    }

    suspend fun updateRegStatus(accountId: String, status: com.ipdial.data.model.RegStatus, text: String = "") {
        context.dataStore.edit { prefs ->
            val current = getAccountsList(prefs).map { acc ->
                if (acc.id == accountId) acc.copy(regStatus = status, regStatusText = text) else acc
            }
            prefs[ACCOUNTS_KEY] = gson.toJson(current)
        }
    }

    private fun getAccountsList(prefs: Preferences): List<SipAccount> {
        val json = prefs[ACCOUNTS_KEY] ?: return emptyList()
        val type = object : TypeToken<List<SipAccount>>() {}.type
        return gson.fromJson(json, type) ?: emptyList()
    }
}
