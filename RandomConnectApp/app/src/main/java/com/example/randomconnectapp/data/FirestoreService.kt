package com.example.randomconnectapp.data

import android.util.Log
import com.example.randomconnectapp.model.User
import com.example.randomconnectapp.webrtc.models.CallData 
import com.example.randomconnectapp.webrtc.models.CallStatus
import com.example.randomconnectapp.webrtc.models.IceCandidateModel 
import com.example.randomconnectapp.webrtc.models.SdpModel 
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.snapshots
import com.google.firebase.firestore.ktx.toObject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import org.webrtc.IceCandidate
import org.webrtc.SessionDescription

class FirestoreService {

    private val db = FirebaseFirestore.getInstance()
    private val usersCollection = db.collection("users")
    private val activeCallsCollection = db.collection("active_calls") 

    companion object {
        private const val TAG = "FirestoreService"
        const val CALLER_ICE_CANDIDATES_COLLECTION = "callerIceCandidates"
        const val CALLEE_ICE_CANDIDATES_COLLECTION = "calleeIceCandidates"
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

    suspend fun updateUserOnlineStatus(uid: String, isOnline: Boolean) {
        try {
            usersCollection.document(uid).update("isOnline", isOnline).await()
            Log.d(TAG, "User ${uid} online status updated to: $isOnline")
        } catch (e: Exception) {
            Log.e(TAG, "Error updating user online status for ${uid}", e)
            throw e 
        }
    }

    suspend fun createCall(callData: CallData): Result<Unit> {
        return try {
            activeCallsCollection.document(callData.callId).set(callData).await()
            Log.d(TAG, "Call document created for callId: ${callData.callId}")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error creating call document for ${callData.callId}", e)
            Result.failure(e)
        }
    }

    suspend fun sendOfferSdp(callId: String, offerSdp: SessionDescription): Result<Unit> {
        return try {
            val sdpModel = SdpModel(offerSdp.type.canonicalForm(), offerSdp.description)
            activeCallsCollection.document(callId).update("offerSdp", sdpModel).await()
            Log.d(TAG, "Offer SDP sent for callId: $callId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error sending offer SDP for ${callId}", e)
            Result.failure(e)
        }
    }

    suspend fun sendAnswerSdp(callId: String, answerSdp: SessionDescription): Result<Unit> {
        return try {
            val sdpModel = SdpModel(answerSdp.type.canonicalForm(), answerSdp.description)
            activeCallsCollection.document(callId).update("answerSdp", sdpModel).await()
            Log.d(TAG, "Answer SDP sent for callId: $callId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error sending answer SDP for ${callId}", e)
            Result.failure(e)
        }
    }

    suspend fun sendIceCandidate(callId: String, candidate: IceCandidate, isCaller: Boolean): Result<Unit> {
        return try {
            val subCollectionName = if (isCaller) CALLER_ICE_CANDIDATES_COLLECTION else CALLEE_ICE_CANDIDATES_COLLECTION
            val iceCandidateModel = IceCandidateModel(
                sdpMid = candidate.sdpMid,
                sdpMLineIndex = candidate.sdpMLineIndex,
                sdp = candidate.sdp,
                serverUrl = candidate.serverUrl
            )
            activeCallsCollection.document(callId).collection(subCollectionName).add(iceCandidateModel).await()
            Log.d(TAG, "ICE candidate sent for callId: $callId, isCaller: $isCaller")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error sending ICE candidate for ${callId}", e)
            Result.failure(e)
        }
    }
    
    fun listenToCallData(callId: String): Flow<CallData?> {
        return activeCallsCollection.document(callId).snapshots().map { documentSnapshot ->
            documentSnapshot.toObject<CallData>()
        }.catch { e ->
            Log.e(TAG, "Error listening to call data for callId ${callId}", e)
            emit(null) 
        }
    }

    // New function to listen for calls where calleeId is the current user and status is ringing
    fun listenForIncomingCallsQuery(calleeId: String): Flow<List<CallData>> {
        return activeCallsCollection
            .whereEqualTo("calleeId", calleeId)
            .whereEqualTo("status", CallStatus.RINGING.name) // Only listen for "ringing" calls
            .snapshots()
            .map { querySnapshot ->
                querySnapshot.documents.mapNotNull { document ->
                    document.toObject<CallData>()
                }
            }
            .catch { exception ->
                Log.e(TAG, "Error listening for incoming calls for calleeId ${calleeId}", exception)
                emit(emptyList<CallData>()) // Emit empty list on error
            }
    }

    fun listenForIceCandidates(callId: String, isCallerPerspective: Boolean): Flow<IceCandidateModel> {
        val targetCollection = if (isCallerPerspective) CALLEE_ICE_CANDIDATES_COLLECTION else CALLER_ICE_CANDIDATES_COLLECTION
        return activeCallsCollection.document(callId).collection(targetCollection)
            .snapshots()
            .mapNotNull { querySnapshot -> // Use mapNotNull to simplify
                querySnapshot.documentChanges.filter { it.type == com.google.firebase.firestore.DocumentChange.Type.ADDED }
                    .mapNotNull { documentChange -> documentChange.document.toObject<IceCandidateModel>() }
                    .firstOrNull() // Process one new candidate at a time from the snapshot
            }
            .catch {  e ->
                Log.e(TAG, "Error listening for ICE candidates for callId ${callId}, target: $targetCollection", e)
                // emit(emptyList()) // Should emit individual items or handle error differently
                // For a Flow<IceCandidateModel>, emitting emptyList is not type-correct if error occurs.
                // Rethrow or emit a specific error object, or ensure it's handled by the collector.
                // For now, error will propagate.
            }
    }


    suspend fun updateCallStatus(callId: String, status: String): Result<Unit> {
        return try {
            activeCallsCollection.document(callId).update("status", status).await()
            Log.d(TAG, "Call status updated to $status for callId: $callId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating call status for ${callId}", e)
            Result.failure(e)
        }
    }
    
    internal fun SdpModel.toSessionDescription(): SessionDescription { 
        return SessionDescription(SessionDescription.Type.fromCanonicalForm(type.toLowerCase()), description)
    }

    internal fun IceCandidateModel.toIceCandidate(): IceCandidate { 
        return IceCandidate(sdpMid, sdpMLineIndex, sdp)
    }
}
