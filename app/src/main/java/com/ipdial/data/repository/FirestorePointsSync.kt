package com.ipdial.data.repository

import android.util.Log
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Simple Firestore sync helper for pro points and expiration.
 * Document path: users/{deviceId}
 * Fields: deviceId, name, points (number), expiration (long), referredBy (string?)
 */
class FirestorePointsSync(private val repo: AccountRepository) {

    private val firestore = FirebaseFirestore.getInstance()
    private val scope = CoroutineScope(Dispatchers.IO)

    private val deviceName: String = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}"

    fun startListening() {
        scope.launch {
            try {
                val dId = repo.getOrCreateDeviceId()
                val docRef = firestore.collection("users").document(dId)
                
                // 1. Initial fetch to sync FROM server if data exists
                try {
                    val task = docRef.get()
                    val snapshot = com.google.android.gms.tasks.Tasks.await(task)
                    if (snapshot.exists()) {
                        val data = snapshot.data
                        val sPoints = (data?.get("points") as? Number)?.toInt()
                        val sExp = (data?.get("expiration") as? Number)?.toLong()
                        
                        if (sPoints != null) repo.setProPoints(sPoints)
                        if (sExp != null) repo.setProExpiration(sExp)
                    } else {
                        // Document doesn't exist, register local info
                        val currentPoints = repo.proPoints.first()
                        val currentExpiration = repo.proExpiration.first()
                        pushUpdate(currentPoints, currentExpiration)
                    }
                } catch (e: Exception) {
                    Log.e("FirestorePointsSync", "initial sync failed", e)
                }

                // 2. Continuous listening for changes
                docRef.addSnapshotListener { snapshot, error ->
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
    }

    fun incrementPoints(amount: Int) {
        scope.launch {
            try {
                val dId = repo.getOrCreateDeviceId()
                val doc = firestore.collection("users").document(dId)
                doc.update(
                    "points", FieldValue.increment(amount.toLong()),
                    "updatedAt", FieldValue.serverTimestamp()
                )
            } catch (e: Exception) {
                // If update fails because doc doesn't exist, push full update
                Log.w("FirestorePointsSync", "incrementPoints failed, trying pushUpdate", e)
                val currentPoints = repo.proPoints.first()
                val currentExpiration = repo.proExpiration.first()
                pushUpdate(currentPoints, currentExpiration)
            }
        }
    }

    fun pushUpdate(points: Int, expiration: Long) {
        scope.launch {
            try {
                val dId = repo.getOrCreateDeviceId()
                val doc = firestore.collection("users").document(dId)
                val map = mapOf(
                    "deviceId" to dId,
                    "shortId" to dId.take(6),
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
                val dId = repo.getOrCreateDeviceId()
                if (refCode.isBlank() || refCode == dId) {
                    withContext(Dispatchers.Main) { onComplete(false, "Invalid code") }
                    return@launch
                }

                // 1. Check if I have already claimed a referral
                val meDoc = firestore.collection("users").document(dId)
                val meSnapshot = com.google.android.gms.tasks.Tasks.await(meDoc.get())
                if (meSnapshot.exists() && meSnapshot.contains("referredBy")) {
                    withContext(Dispatchers.Main) { onComplete(false, "Referral already claimed") }
                    return@launch
                }

                // 2. Check if the referral code exists (Search by full ID first, then by short ID)
                var refDoc = firestore.collection("users").document(refCode)
                var snapshot = com.google.android.gms.tasks.Tasks.await(refDoc.get())
                
                if (!snapshot.exists()) {
                    // Try searching by shortId field
                    val query = firestore.collection("users").whereEqualTo("shortId", refCode).limit(1).get()
                    val querySnapshot = com.google.android.gms.tasks.Tasks.await(query)
                    if (!querySnapshot.isEmpty) {
                        snapshot = querySnapshot.documents[0]
                        refDoc = snapshot.reference
                    } else {
                        withContext(Dispatchers.Main) { onComplete(false, "Referral code not found") }
                        return@launch
                    }
                }

                // 3. Atomically increment both user docs by 50 using a batch
                val batch = firestore.batch()
                
                // award to referrer
                val refUpdate = mapOf(
                    "points" to FieldValue.increment(50),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                batch.update(refDoc, refUpdate)

                // award to this user and set referredBy
                val meUpdate = mapOf(
                    "points" to FieldValue.increment(50),
                    "referredBy" to snapshot.id, // Store the full ID of the referrer
                    "deviceId" to dId,
                    "shortId" to dId.take(6),
                    "name" to deviceName,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                batch.set(meDoc, meUpdate, com.google.firebase.firestore.SetOptions.merge())

                com.google.android.gms.tasks.Tasks.await(batch.commit())

                // Read fresh values from server
                val updatedMe = com.google.android.gms.tasks.Tasks.await(meDoc.get())
                val points = (updatedMe.data?.get("points") as? Number)?.toInt() ?: 0
                val expiration = (updatedMe.data?.get("expiration") as? Number)?.toLong() ?: 0L
                repo.setProPoints(points)
                repo.setProExpiration(expiration)

                withContext(Dispatchers.Main) { onComplete(true, "Referral applied: +50 points") }
            } catch (e: Exception) {
                Log.e("FirestorePointsSync", "claimReferral failed", e)
                withContext(Dispatchers.Main) { onComplete(false, "Error: ${e.message}") }
            }
        }
    }

    fun redeemPoints(cost: Int, newExpiration: Long) {
        scope.launch {
            try {
                val dId = repo.getOrCreateDeviceId()
                val doc = firestore.collection("users").document(dId)
                doc.update(
                    "points", FieldValue.increment(-cost.toLong()),
                    "expiration", newExpiration,
                    "updatedAt", FieldValue.serverTimestamp()
                )
            } catch (e: Exception) {
                Log.e("FirestorePointsSync", "redeemPoints failed", e)
            }
        }
    }
}

