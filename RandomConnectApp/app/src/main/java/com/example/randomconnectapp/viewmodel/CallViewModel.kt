package com.example.randomconnectapp.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.randomconnectapp.MainApplication
import com.example.randomconnectapp.agora.AgoraManager
import com.example.randomconnectapp.agora.AgoraManagerListener
import com.example.randomconnectapp.data.FirestoreService
// Import CallStatus if it's still used for Firestore call invitations, otherwise remove
// import com.example.randomconnectapp.webrtc.models.CallStatus 
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID // For generating channel names

// Sealed class for UI state - updated for Agora
sealed class CallUiState {
    object Idle : CallUiState()
    data class Calling(val message: String, val channelName: String) : CallUiState()
    data class Ringing(val message: String, val channelName: String, val callerId: String) : CallUiState()
    data class Active(
        val channelName: String,
        val localUid: Int? = null,
        val remoteUids: Set<Int> = emptySet(),
        var callStatusMessage: String = "Connecting...",
        val isLocalAudioMuted: Boolean = false,
        val isLocalVideoMuted: Boolean = false
        // Add other relevant fields if needed
    ) : CallUiState() {
        // Convenience copy function
        fun copy(
            channelName: String = this.channelName,
            localUid: Int? = this.localUid,
            remoteUids: Set<Int> = this.remoteUids,
            callStatusMessage: String = this.callStatusMessage,
            isLocalAudioMuted: Boolean = this.isLocalAudioMuted,
            isLocalVideoMuted: Boolean = this.isLocalVideoMuted
        ): Active = Active(
            channelName, localUid, remoteUids, callStatusMessage, 
            isLocalAudioMuted, isLocalVideoMuted
        )
    }
    data class Error(val message: String) : CallUiState()
}


class CallViewModel(application: Application) : AndroidViewModel(application), AgoraManagerListener {

    private val auth = FirebaseAuth.getInstance()
    private val firestoreService = FirestoreService() // Keep for new signaling (invitations)
    private lateinit var agoraManager: AgoraManager

    private val _callUiState = MutableStateFlow<CallUiState>(CallUiState.Idle)
    val callUiState: StateFlow<CallUiState> = _callUiState.asStateFlow()

    // Store current channel name, could be part of Active state too
    private var currentChannelName: String? = null 
    // private var isCaller: Boolean = false // This might be implicitly handled by state or not needed

    companion object {
        private const val TAG = "CallViewModel"
    }

    init {
        val mainApplication = application as MainApplication
        mainApplication.rtcEngine?.let {
            agoraManager = AgoraManager(application.applicationContext, it)
            agoraManager.setListener(this)
        } ?: run {
            Log.e(TAG, "RTC Engine not available from MainApplication. AgoraManager not initialized.")
            _callUiState.value = CallUiState.Error("Call functionality is unavailable (RTC Engine missing).")
        }
    }

    // --- SurfaceView Setup Methods for CallScreen ---
    fun setupLocalVideoSurface(surfaceView: android.view.SurfaceView) {
        if (::agoraManager.isInitialized) {
            agoraManager.setupLocalVideo(surfaceView)
            // Optionally start preview if not already started by joinChannel or if needed before joining
            // agoraManager.startPreview() 
        } else {
            Log.e(TAG, "setupLocalVideoSurface: AgoraManager not initialized.")
        }
    }

    fun setupRemoteVideoSurface(surfaceView: android.view.SurfaceView, remoteUid: Int) {
        if (::agoraManager.isInitialized) {
            agoraManager.setupRemoteVideo(surfaceView, remoteUid)
        } else {
            Log.e(TAG, "setupRemoteVideoSurface: AgoraManager not initialized for remote UID: $remoteUid")
        }
    }

    fun startCall(targetUserId: String) {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            _callUiState.value = CallUiState.Error("User not authenticated.")
            return
        }
        // isCaller = true // Set if needed for Firestore logic
        val channelName = UUID.randomUUID().toString()
        currentChannelName = channelName
        val localUid = 0 // Agora can auto-assign UID with 0

        _callUiState.value = CallUiState.Calling("Initiating call...", channelName)
        
        viewModelScope.launch {
            // Firestore logic to send invitation to targetUserId with channelName
            // This will be detailed in FirestoreService changes. For now, assume it's:
            // firestoreService.sendCallInvitation(targetUserId, channelName, currentUser.uid)
            // For now, we'll directly join for testing this ViewModel part.
            // Replace with actual Firestore call later.
            Log.d(TAG, "Simulating sending call invitation to $targetUserId for channel $channelName")
            // Simulate success of sending invitation:
            if (::agoraManager.isInitialized) {
                agoraManager.joinChannel(channelName, localUid)
            } else {
                 _callUiState.value = CallUiState.Error("AgoraManager not initialized.")
            }
            // TODO: Replace above with actual firestoreService.sendCallInvitation call:
            /*
            firestoreService.sendCallInvitation(targetUserId, channelName, currentUser.uid).fold(
                onSuccess = {
                    Log.d(TAG, "Call invitation sent to $targetUserId for channel $channelName")
                    if (::agoraManager.isInitialized) {
                        agoraManager.joinChannel(channelName, localUid)
                    } else {
                         _callUiState.value = CallUiState.Error("AgoraManager not initialized.")
                    }
                },
                onFailure = { e ->
                    Log.e(TAG, "Failed to send call invitation", e)
                    _callUiState.value = CallUiState.Error("Failed to send call invitation: ${e.message}")
                    currentChannelName = null
                }
            )
            */
        }
    }
    
    // Called when an invitation is received (e.g., from a Firestore listener in UI/Activity)
    fun joinInvitedCall(channelName: String, callerId: String, localUid: Int = 0) {
        currentChannelName = channelName
        // isCaller = false // Set if needed
        _callUiState.value = CallUiState.Ringing("Incoming call from ${callerId.take(8)}...", channelName, callerId)
        // The UI would show ringing, and if user accepts:
        // acceptCall(channelName, localUid)
    }

    // User accepts the call from UI
    fun acceptCall(channelName: String, localUid: Int = 0) {
        if (currentChannelName != channelName && _callUiState.value !is CallUiState.Ringing) {
            _callUiState.value = CallUiState.Error("Invalid call state to accept.")
            return
        }
         _callUiState.value = CallUiState.Active(channelName, callStatusMessage = "Joining call...")
        if (::agoraManager.isInitialized) {
            agoraManager.joinChannel(channelName, localUid)
        } else {
            _callUiState.value = CallUiState.Error("AgoraManager not initialized.")
        }
    }

    // User declines the call from UI
    fun declineCall(channelName: String) {
         if (currentChannelName != channelName && (_callUiState.value as? CallUiState.Ringing)?.channelName != channelName) {
            Log.w(TAG, "Cannot decline call, not in ringing state for $channelName")
            return
        }
        // TODO: Update Firestore that call was declined.
        // firestoreService.updateCallInvitationStatus(channelName, CallStatus.DECLINED.name)
        _callUiState.value = CallUiState.Idle
        currentChannelName = null
    }


    fun endCall() {
        Log.d(TAG, "End call requested for channel: $currentChannelName")
        val activeState = _callUiState.value as? CallUiState.Active
        val callingState = _callUiState.value as? CallUiState.Calling
        
        val channelToLeave = activeState?.channelName ?: callingState?.channelName ?: currentChannelName

        if (channelToLeave == null) {
            Log.w(TAG, "No active or calling channel to end.")
            cleanUpCallState() // Ensure UI is reset
            return
        }

        if (activeState != null) {
             _callUiState.value = activeState.copy(callStatusMessage = "Ending call...")
        } else if (callingState != null) {
            // If in Calling state, means we haven't joined Agora channel yet, or join failed.
            // Need to cancel the invitation in Firestore.
             _callUiState.value = CallUiState.Idle // Go to Idle directly
        }


        if (::agoraManager.isInitialized) {
            agoraManager.leaveChannel() // This will trigger onLeaveChannelSuccess
        } else {
            Log.w(TAG, "AgoraManager not initialized, cannot leave channel via manager. Cleaning up state.")
            cleanUpCallState()
        }

        // Update Firestore status (e.g., call ended or invitation cancelled)
        // This needs to be robust, e.g. using the actual channelName from the state
        channelToLeave?.let {
            viewModelScope.launch {
                Log.d(TAG, "Updating Firestore: Call ended/cancelled for channel $it")
                // Example: firestoreService.updateCallStatus(it, CallStatus.ENDED.name)
                // Or firestoreService.cancelCallInvitation(it)
            }
        }
        // currentChannelName = null // Should be cleared in onLeaveChannel or cleanUpCallState
    }
    
    private fun cleanUpCallState() {
        Log.d(TAG, "Cleaning up call state in ViewModel.")
        _callUiState.value = CallUiState.Idle 
        currentChannelName = null
        // Other local state resets if any
    }

    fun toggleLocalAudioMute() {
        (_callUiState.value as? CallUiState.Active)?.let { activeState ->
            val newMuteState = !activeState.isLocalAudioMuted
            if (::agoraManager.isInitialized) {
                agoraManager.muteLocalAudioStream(newMuteState)
                _callUiState.value = activeState.copy(isLocalAudioMuted = newMuteState)
            }
        }
    }

    fun toggleLocalVideoMute() {
        (_callUiState.value as? CallUiState.Active)?.let { activeState ->
            val newMuteState = !activeState.isLocalVideoMuted
            if (::agoraManager.isInitialized) {
                agoraManager.muteLocalVideoStream(newMuteState)
                _callUiState.value = activeState.copy(isLocalVideoMuted = newMuteState)
                 // If unmuting video, might need to call setupLocalVideo again if it was fully disabled
                if (!newMuteState) {
                    // agoraManager.setupLocalVideo(...) // This depends on how VideoScreen handles SurfaceView
                }
            }
        }
    }


    // --- AgoraManagerListener Implementation ---
    override fun onJoinChannelSuccess(channel: String, uid: Int, elapsed: Int) {
        viewModelScope.launch {
            Log.i(TAG, "onJoinChannelSuccess: channel=$channel, uid=$uid")
            val currentState = _callUiState.value
            if (currentState is CallUiState.Calling && currentState.channelName == channel) {
                _callUiState.value = CallUiState.Active(
                    channelName = channel, 
                    localUid = uid, 
                    callStatusMessage = "Connected"
                )
            } else if (currentState is CallUiState.Active && currentState.channelName == channel) {
                // This can happen if we were already in Ringing and then accepted
                 _callUiState.value = currentState.copy(localUid = uid, callStatusMessage = "Connected")
            } else if (currentState is CallUiState.Ringing && currentState.channelName == channel) {
                 // This case should be handled by acceptCall leading to Active, then this callback
                 // However, if joinChannel was called directly after Ringing, update to Active
                 _callUiState.value = CallUiState.Active(
                    channelName = channel,
                    localUid = uid,
                    callStatusMessage = "Connected"
                 )
            }
            currentChannelName = channel // Ensure currentChannelName is set
        }
    }

    override fun onLeaveChannelSuccess() {
        viewModelScope.launch {
            Log.i(TAG, "onLeaveChannelSuccess")
            cleanUpCallState()
        }
    }

    override fun onUserJoined(uid: Int) {
        viewModelScope.launch {
            Log.i(TAG, "onUserJoined: uid=$uid")
            (_callUiState.value as? CallUiState.Active)?.let { activeState ->
                if (uid != activeState.localUid) { // Ensure not adding self
                    _callUiState.value = activeState.copy(remoteUids = activeState.remoteUids + uid)
                }
            }
        }
    }

    override fun onUserOffline(uid: Int, reason: Int) {
        viewModelScope.launch {
            Log.i(TAG, "onUserOffline: uid=$uid, reason=$reason")
            (_callUiState.value as? CallUiState.Active)?.let { activeState ->
                val updatedRemoteUids = activeState.remoteUids - uid
                _callUiState.value = activeState.copy(remoteUids = updatedRemoteUids)
                if (updatedRemoteUids.isEmpty()) {
                    // Optional: Change status or end call if no remote users left
                    // _callUiState.value = activeState.copy(callStatusMessage = "Remote user left")
                    // endCall() // or just show a message
                }
            }
        }
    }

    override fun onRemoteVideoStateChanged(uid: Int, state: Int, reason: Int, elapsed: Int) {
        viewModelScope.launch {
            Log.i(TAG, "onRemoteVideoStateChanged: uid=$uid, state=$state, reason=$reason")
            // Example: Update UI based on remote video state.
            // This might involve more complex state in CallUiState.Active if needed,
            // e.g., a map of uid to their video state.
            // For now, just logging. The VideoScreen will get this directly from AgoraManager if it
            // sets up its own SurfaceView for remote users based on onUserJoined.
        }
    }
    
    override fun onErrorOccurred(err: Int, msg: String) {
        viewModelScope.launch {
            Log.e(TAG, "AgoraManager Error: $err, Message: $msg")
            // Avoid setting generic error if already in a specific error state or idle
            if (_callUiState.value !is CallUiState.Idle && _callUiState.value !is CallUiState.Error) {
                 _callUiState.value = CallUiState.Error("Call error: $msg (code $err)")
            }
            // Consider if specific errors require leaving the channel or full cleanup
            // if (err == io.agora.rtc2.Constants.ERR_INVALID_TOKEN || err == io.agora.rtc2.Constants.ERR_TOKEN_EXPIRED) {
            //    cleanUpCallState() // e.g. token errors are critical
            // }
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        Log.d(TAG, "CallViewModel onCleared. Ensuring cleanup.")
        // If in an active call state, try to leave the channel.
        // Check currentChannelName because onLeaveChannelSuccess might have already set state to Idle.
        if (currentChannelName != null && _callUiState.value !is CallUiState.Idle) {
             Log.d(TAG, "onCleared: Still in a call (channel: $currentChannelName), attempting to leave.")
            if (::agoraManager.isInitialized) {
                agoraManager.leaveChannel() 
                // Note: leaveChannel is async. The actual cleanup of AgoraManager resources 
                // (removeHandler) should happen after this or in destroy().
            }
        }
        if (::agoraManager.isInitialized) {
            agoraManager.destroy() // Important to remove handler and release AgoraManager resources
        }
        cleanUpCallState() // Ensure UI state is reset
    }
}
