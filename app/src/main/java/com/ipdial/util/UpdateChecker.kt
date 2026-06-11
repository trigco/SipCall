package com.ipdial.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL

object UpdateChecker {
    private const val GITHUB_API_URL = "https://api.github.com/repos/nazimunaeem1/IPCall/releases/latest"
    private const val TAG = "UpdateChecker"

    data class GitHubRelease(val tag_name: String, val html_url: String, val body: String)

    suspend fun checkForUpdates(currentVersion: String): GitHubRelease? = withContext(Dispatchers.IO) {
        try {
            val json = URL(GITHUB_API_URL).readText()
            val release = Gson().fromJson(json, GitHubRelease::class.java)
            
            // Assuming version tags are like "v1.1" or "1.1"
            val latestVersion = release.tag_name.replace("v", "")
            if (isNewerVersion(currentVersion, latestVersion)) {
                return@withContext release
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check for updates: ${e.message}")
        }
        null
    }

    private fun isNewerVersion(current: String, latest: String): Boolean {
        val currParts = current.split(".").mapNotNull { it.toIntOrNull() }
        val lateParts = latest.split(".").mapNotNull { it.toIntOrNull() }
        
        for (i in 0 until minOf(currParts.size, lateParts.size)) {
            if (lateParts[i] > currParts[i]) return true
            if (lateParts[i] < currParts[i]) return false
        }
        return lateParts.size > currParts.size
    }
}
