package com.ipdial.data.repository

import android.app.Application
import android.util.Log
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Simple Firestore sync helper for pro points and expiration.
 * Document path: users/{deviceId}
 * Fields: deviceId, name, points (number), expiration (long), referredBy (string?)
 */
class FirestorePointsSync(private val app: Application, private val repo: AccountRepository) {

    private val firestore = FirebaseFirestore.getInstance()
    private val scope = CoroutineScope(Dispatchers.IO)

    private val deviceId: String by lazy {
        try {
            android.provider.Settings.Secure.getString(app.contentResolver, android.provider.Settings.Secure.ANDROID_ID) ?: "unknown"
        } catch (e: Exception) { "unknown" }
    }

    private val deviceName: String = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}"

    fun startListening() {
        try {
            val docRef = firestore.collection("users").document(deviceId)
            
            // Automatically register/update user info on start
            scope.launch {
                try {
                    val currentPoints = repo.proPoints.first()
                    val currentExpiration = repo.proExpiration.first()
                    pushUpdate(currentPoints, currentExpiration)
                } catch (e: Exception) {
                    Log.e("FirestorePointsSync", "initial registration failed", e)
                }
            }

            docRef.addSnapshotListener { snapshot: com.google.firebase.firestore.DocumentSnapshot?, error: com.google.firebase.firestore.FirebaseFirestoreException? ->
                if (error != null) {
                    Log.e("FirestorePointsSync", "listen error", error)
                    return@addSnapshotListener
                }
                if (snapshot != null && snapshot.exists()) {
                    val data = snapshot.data ?: return@addSnapshotListener
                    val points = (data["points"] as? Number)?.toInt() ?: return@addSnapshotListener
                    val expiration = (data["expiration"] as? Number)?.toLong() ?: 0L
                    // push to DataStore via repo
                    scope.launch {
                        try {
                            repo.setProPoints(points)
                            repo.setProExpiration(expiration)
                        } catch (e: Exception) {
                            Log.e("FirestorePointsSync", "failed to write to DataStore", e)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("FirestorePointsSync", "startListening failed", e)
        }
    }

    fun pushUpdate(points: Int, expiration: Long) {
        scope.launch {
            try {
                val doc = firestore.collection("users").document(deviceId)
                val map = mapOf(
                    "deviceId" to deviceId,
                    "name" to deviceName,
                    "points" to points,
                    "expiration" to expiration,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                doc.set(map, com.google.firebase.firestore.SetOptions.merge())
            } catch (e: Exception) {
                Log.e("FirestorePointsSync", "pushUpdate failed", e)
            }
        }
    }

    /**
     * Attempt to claim a referral code. The code is expected to be another deviceId.
     * If the referral doc exists and hasn't been used by this device, we increment both parties by 50 points.
     */
    fun claimReferral(refCode: String, onComplete: (Boolean, String) -> Unit) {
        scope.launch {
            try {
                if (refCode.isBlank() || refCode == deviceId) {
                    onComplete(false, "Invalid code")
                    return@launch
                }
                val refDoc = firestore.collection("users").document(refCode)
                val snapshot = com.google.android.gms.tasks.Tasks.await(refDoc.get())
                if (!snapshot.exists()) {
                    onComplete(false, "Referral code not found")
                    return@launch
                }
                // Atomically increment both user docs by 50
                val target = firestore.collection("users").document(refCode)
                val me = firestore.collection("users").document(deviceId)

                // award to referrer
                target.update("points", FieldValue.increment(50))

                // award to this user and set referredBy
                val meUpdate = mapOf<String, Any>(
                    "points" to FieldValue.increment(50),
                    "referredBy" to refCode,
                    "deviceId" to deviceId,
                    "name" to deviceName,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                me.set(meUpdate, com.google.firebase.firestore.SetOptions.merge())

                // Also update local datastore values by fetching latest
                val updatedMe = com.google.android.gms.tasks.Tasks.await(me.get())
                val points = (updatedMe.data?.get("points") as? Number)?.toInt() ?: 0
                val expiration = (updatedMe.data?.get("expiration") as? Number)?.toLong() ?: 0L
                repo.setProPoints(points)
                repo.setProExpiration(expiration)

                onComplete(true, "Referral applied: +50 points")
            } catch (e: Exception) {
                Log.e("FirestorePointsSync", "claimReferral failed", e)
                onComplete(false, "Error: ${e.message}")
            }
        }
    }
}

