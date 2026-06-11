package com.ipdial.data.repository

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.ipdial.data.model.CallDirection
import com.ipdial.data.model.CallLogEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject

/**
 * Simple SharedPreferences-backed call-log repository.
 * Uses a singleton pattern to ensure all parts of the app see the same data.
 *
 * Entries are stored newest-first. Maximum 200 entries are retained.
 */
class CallLogRepository private constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("call_log", Context.MODE_PRIVATE)

    private val _entries = MutableStateFlow(load())
    val entries: Flow<List<CallLogEntry>> = _entries.asStateFlow()

    fun insert(entry: CallLogEntry) {
        _entries.update { current ->
            val newList = listOf(entry) + current.take(199)
            persist(newList)
            newList
        }
    }

    fun delete(entry: CallLogEntry) {
        _entries.update { current ->
            val newList = current.filter { it.id != entry.id }
            persist(newList)
            newList
        }
    }

    private fun load(): List<CallLogEntry> = try {
        val json = prefs.getString(KEY_LOG, null) ?: return emptyList()
        val arr  = JSONArray(json)
        val list = mutableListOf<CallLogEntry>()
        for (i in 0 until arr.length()) {
            try {
                list.add(arr.getJSONObject(i).toEntry())
            } catch (e: Exception) { /* Skip corrupted */ }
        }
        list
    } catch (e: Exception) {
        emptyList()
    }

    private fun persist(entries: List<CallLogEntry>) {
        val arr = JSONArray().apply { entries.forEach { put(it.toJson()) } }
        prefs.edit { putString(KEY_LOG, arr.toString()) }
    }

    private fun CallLogEntry.toJson() = JSONObject().apply {
        put("id",          id)
        put("accountId",   accountId)
        put("remoteUri",   remoteUri)
        put("remoteName",  remoteDisplayName)
        put("direction",   direction.name)
        put("missed",      missed)
        put("ts",          timestampMs)
        put("dur",         durationSeconds)
    }

    private fun JSONObject.toEntry() = CallLogEntry(
        id                  = optString("id", java.util.UUID.randomUUID().toString()),
        accountId           = optString("accountId"),
        remoteUri           = optString("remoteUri"),
        remoteDisplayName   = optString("remoteName"),
        direction           = try {
            CallDirection.valueOf(getString("direction"))
        } catch (e: Exception) {
            CallDirection.INCOMING
        },
        missed              = optBoolean("missed", false),
        timestampMs         = optLong("ts", System.currentTimeMillis()),
        durationSeconds     = optLong("dur", 0L),
    )

    companion object {
        private const val KEY_LOG = "entries_v2"
        
        @Volatile
        private var INSTANCE: CallLogRepository? = null

        fun getInstance(context: Context): CallLogRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: CallLogRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
