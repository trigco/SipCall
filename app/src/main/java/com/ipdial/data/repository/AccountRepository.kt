package com.ipdial.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.ipdial.data.model.KeypadDesign
import com.ipdial.data.model.SipAccount
import com.ipdial.data.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "ipdial_accounts")

class AccountRepository(private val context: Context) {

    private val gson = Gson()
    private val ACCOUNTS_KEY = stringPreferencesKey("accounts")
    private val RINGTONE_KEY = stringPreferencesKey("global_ringtone")
    private val DND_KEY = booleanPreferencesKey("dnd_enabled")
    private val VIBRATE_KEY = booleanPreferencesKey("global_vibrate")
    private val THEME_KEY = stringPreferencesKey("theme_mode")
    private val CALLING_CARDS_KEY = booleanPreferencesKey("calling_cards")
    private val FONT_SIZE_KEY = stringPreferencesKey("font_size_multiplier")
    private val APP_ICON_KEY = stringPreferencesKey("app_icon_alias")
    private val KEYPAD_DESIGN_KEY = stringPreferencesKey("keypad_design")
    private val DEFAULT_DOMAIN_KEY = stringPreferencesKey("default_domain")
    private val LAST_DIALED_KEY = stringPreferencesKey("last_dialed")
    private val ADS_ENABLED_KEY = booleanPreferencesKey("ads_enabled")

    val accounts: Flow<List<SipAccount>> = context.dataStore.data.map { prefs ->
        val json = prefs[ACCOUNTS_KEY] ?: return@map emptyList()
        val type = object : TypeToken<List<SipAccount>>() {}.type
        gson.fromJson(json, type) ?: emptyList()
    }

    val globalRingtone: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[RINGTONE_KEY] ?: "android.resource://${context.packageName}/raw/ipdial_ringtone"
    }

    val themeMode: Flow<ThemeMode> = context.dataStore.data.map { prefs -> 
        try { ThemeMode.valueOf(prefs[THEME_KEY] ?: "System") } catch (e: Exception) { ThemeMode.System }
    }
    val callingCardsEnabled: Flow<Boolean> = context.dataStore.data.map { prefs -> prefs[CALLING_CARDS_KEY] ?: true }
    val dndEnabled: Flow<Boolean> = context.dataStore.data.map { prefs -> prefs[DND_KEY] ?: false }
    val globalVibrate: Flow<Boolean> = context.dataStore.data.map { prefs -> prefs[VIBRATE_KEY] ?: true }

    val fontSizeMultiplier: Flow<Float> = context.dataStore.data.map { prefs -> 
        prefs[FONT_SIZE_KEY]?.toFloatOrNull() ?: 1.0f 
    }
    
    val appIconAlias: Flow<String> = context.dataStore.data.map { prefs -> 
        prefs[APP_ICON_KEY] ?: "Default"
    }

    val keypadDesign: Flow<KeypadDesign> = context.dataStore.data.map { prefs -> 
        try { KeypadDesign.valueOf(prefs[KEYPAD_DESIGN_KEY] ?: "Grid") } catch (e: Exception) { KeypadDesign.Grid }
    }

    val defaultDomain: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[DEFAULT_DOMAIN_KEY] ?: "sip.amarip.net"
    }

    val lastDialedNumber: Flow<String?> = context.dataStore.data.map { it[LAST_DIALED_KEY] }

    val adsEnabled: Flow<Boolean> = context.dataStore.data.map { it[ADS_ENABLED_KEY] ?: true }

    suspend fun setThemeMode(mode: ThemeMode) = context.dataStore.edit { it[THEME_KEY] = mode.name }
    suspend fun setCallingCards(enabled: Boolean) = context.dataStore.edit { it[CALLING_CARDS_KEY] = enabled }
    suspend fun setDnd(enabled: Boolean) = context.dataStore.edit { it[DND_KEY] = enabled }
    suspend fun setGlobalVibrate(enabled: Boolean) = context.dataStore.edit { it[VIBRATE_KEY] = enabled }
    
    suspend fun setFontSizeMultiplier(multiplier: Float) = context.dataStore.edit { it[FONT_SIZE_KEY] = multiplier.toString() }
    suspend fun setAppIconAlias(alias: String) = context.dataStore.edit { it[APP_ICON_KEY] = alias }
    suspend fun setKeypadDesign(design: KeypadDesign) = context.dataStore.edit { it[KEYPAD_DESIGN_KEY] = design.name }
    suspend fun setDefaultDomain(domain: String) = context.dataStore.edit { it[DEFAULT_DOMAIN_KEY] = domain }
    suspend fun setLastDialedNumber(number: String) = context.dataStore.edit { it[LAST_DIALED_KEY] = number }
    suspend fun setAdsEnabled(enabled: Boolean) = context.dataStore.edit { it[ADS_ENABLED_KEY] = enabled }

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
