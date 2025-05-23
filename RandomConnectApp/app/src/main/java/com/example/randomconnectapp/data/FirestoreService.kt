package com.example.randomconnectapp.data

import android.util.Log
import com.example.randomconnectapp.model.AgoraCallInfo
import com.example.randomconnectapp.model.CallStatus
import com.example.randomconnectapp.model.User
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.snapshots
import com.google.firebase.firestore.ktx.toObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class FirestoreService {

    private val db = FirebaseFirestore.getInstance()
    private val usersCollection = db.collection("users")
    private val callInvitationsCollection = db.collection(CALL_INVITATIONS_COLLECTION)

    companion object {
        private const val TAG = "FirestoreService"
        private const val CALL_INVITATIONS_COLLECTION = "call_invitations"
    }

    suspend fun saveUserDetails(user: User): Result<Unit> {
        return try {
            usersCollection.document(user.uid).set(user).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error saving user details for ${user.uid}", e)
            Result.failure(e)
        }
    }

    fun getUserDetails(uid: String): Flow<User?> = flow {
        try {
            val documentSnapshot = usersCollection.document(uid).get().await()
            val user = documentSnapshot.toObject(User::class.java)
            emit(user)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting user details for ${uid}", e)
            emit(null)
        }
    }.catch { e ->
        Log.e(TAG, "Exception in getUserDetails flow for ${uid}", e)
        emit(null)
    }

    fun getOnlineUsers(currentUserId: String): Flow<List<User>> {
        return usersCollection
            .whereEqualTo("isOnline", true)
            .snapshots()
            .map { querySnapshot ->
                querySnapshot.documents.mapNotNull { document ->
                    document.toObject(User::class.java)
                }.filter { user ->
                    user.uid != currentUserId
                }
            }
            .catch { exception ->
                Log.e(TAG, "Error in getOnlineUsers flow", exception)
                emit(emptyList<User>())
            }
    }

    suspend fun updateUserOnlineStatus(uid: String, isOnline: Boolean): Result<Unit> {
        return try {
            usersCollection.document(uid).update("isOnline", isOnline).await()
            Log.d(TAG, "User ${uid} online status updated to: $isOnline")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating user online status for ${uid}", e)
            Result.failure(e)
        }
    }

    // New Agora-based signaling methods
    suspend fun sendCallInvitation(targetUserId: String, channelName: String, callerId: String, callerName: String?): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val callInfo = AgoraCallInfo(
                channelName = channelName,
                callerId = callerId,
                callerName = callerName,
                calleeId = targetUserId,
                status = CallStatus.RINGING.name,
                timestamp = null // Firestore will set this with @ServerTimestamp
                // token = null // Set if you have a token
            )
            // Use channelName as document ID for easy lookup and updates
            callInvitationsCollection.document(channelName).set(callInfo).await()
            Log.d(TAG, "Call invitation sent for channel: $channelName to $targetUserId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error sending call invitation for $channelName", e)
            Result.failure(e)
        }
    }

    fun listenForCallInvitations(userId: String): Flow<AgoraCallInfo?> = callbackFlow {
        val listenerRegistration = callInvitationsCollection
            .whereEqualTo("calleeId", userId)
            // .whereEqualTo("status", CallStatus.RINGING.name) // Optionally filter only for active ringing
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(5) // Example: limit to recent 5 invitations
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    Log.w(TAG, "Listen for call invitations failed for userId $userId.", e)
                    close(e) // Close the flow with error
                    return@addSnapshotListener
                }

                snapshots?.documentChanges?.forEach { documentChange ->
                     if (documentChange.type == com.google.firebase.firestore.DocumentChange.Type.ADDED ||
                         documentChange.type == com.google.firebase.firestore.DocumentChange.Type.MODIFIED) {
                        val callInfo = documentChange.document.toObject(AgoraCallInfo::class.java)
                        if (callInfo.status == CallStatus.RINGING.name) { // Process only if it's a new ringing invitation
                             Log.d(TAG, "Received call invitation/update: $callInfo for userId $userId")
                             trySend(callInfo).isSuccess
                        } else if (callInfo.status == CallStatus.CANCELLED.name) {
                            // If caller cancels an invitation that is still ringing for callee
                            Log.d(TAG, "Call invitation cancelled by caller: $callInfo")
                            trySend(callInfo).isSuccess // Notify callee so UI can be updated
                        }
                     }
                }
            }
        awaitClose {
            Log.d(TAG, "Closing listener for call invitations for userId $userId")
            listenerRegistration.remove()
        }
    }


    suspend fun updateCallStatus(channelName: String, newStatus: CallStatus): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Updating call $channelName to status ${newStatus.name}")
            val updates = hashMapOf<String, Any>(
                "status" to newStatus.name
            )
            // if (newStatus == CallStatus.ENDED || newStatus == CallStatus.MISSED) {
            //     updates["endedAt"] = FieldValue.serverTimestamp() // Example of adding ended timestamp
            // }
            callInvitationsCollection.document(channelName)
                .update(updates)
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating call status for $channelName to $newStatus", e)
            Result.failure(e)
        }
    }
    
    fun listenToCallStatus(channelName: String): Flow<AgoraCallInfo?> = callbackFlow {
        Log.d(TAG, "Listening to call status for channel: $channelName")
        val listenerRegistration = callInvitationsCollection
            .document(channelName)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.w(TAG, "Listen to call status for $channelName failed.", e)
                    close(e) // Close the flow with error
                    return@addSnapshotListener
                }
                if (snapshot != null && snapshot.exists()) {
                    val callInfo = snapshot.toObject(AgoraCallInfo::class.java)
                    Log.d(TAG, "Call status update for $channelName: $callInfo")
                    trySend(callInfo).isSuccess
                } else {
                    Log.d(TAG, "Call document for $channelName does not exist or was deleted.")
                    trySend(null).isSuccess // Document might have been deleted
                }
            }
        awaitClose {
            Log.d(TAG, "Closing listener for call status for channel $channelName")
            listenerRegistration.remove()
        }
    }

    suspend fun deleteCallInvitation(channelName: String): Result<Unit> = withContext(Dispatchers.IO) {
        return try {
            callInvitationsCollection.document(channelName).delete().await()
            Log.d(TAG, "Call invitation deleted for channel: $channelName")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting call invitation for $channelName", e)
            Result.failure(e)
        }
    }
}
