package com.example.randomconnectapp.viewmodel

import android.app.Application 
import android.util.Log
import androidx.lifecycle.AndroidViewModel 
import androidx.lifecycle.viewModelScope
import com.example.randomconnectapp.data.FirestoreService
import com.example.randomconnectapp.webrtc.WebRTCManager
import com.example.randomconnectapp.webrtc.models.CallData
import com.example.randomconnectapp.webrtc.models.CallStatus
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.webrtc.*
import java.util.UUID

class CallViewModel(application: Application) : AndroidViewModel(application) {

    private val auth = FirebaseAuth.getInstance()
    private val firestoreService = FirestoreService()
    private val eglBaseContext = EglBase.create().eglBaseContext 
    // Pass application context to WebRTCManager
    private val webRTCManager = WebRTCManager(application.applicationContext, eglBaseContext)


    // CallUiState now includes video tracks
    private val _callUiState = MutableStateFlow<CallUiState>(CallUiState.Idle)
    val callUiState: StateFlow<CallUiState> = _callUiState


    private var currentCallId: String? = null
    private var localPeerConnection: PeerConnection? = null
    private var isCaller: Boolean = false

    // Local media tracks
    private var localAudioTrack: AudioTrack? = null
    private var localVideoTrack: VideoTrack? = null


    companion object {
        private const val TAG = "CallViewModel"
    }

    private val peerConnectionObserver = object : PeerConnection.Observer {
        override fun onSignalingChange(newState: PeerConnection.SignalingState?) {
            Log.d(TAG, "SignalingState changed: $newState")
            (_callUiState.value as? CallUiState.Active)?.let { currentState ->
                _callUiState.value = currentState.copy(signalingState = newState)
            }
        }

        override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState?) {
            Log.d(TAG, "IceConnectionState changed: $newState")
             (_callUiState.value as? CallUiState.Active)?.let { currentState ->
                _callUiState.value = currentState.copy(iceConnectionState = newState)
             }
            if (newState == PeerConnection.IceConnectionState.CONNECTED) {
                 (_callUiState.value as? CallUiState.Active)?.let { currentState ->
                    _callUiState.value = currentState.copy(callStatusMessage = "Connected")
                 }
            }
            if (newState == PeerConnection.IceConnectionState.FAILED || 
                newState == PeerConnection.IceConnectionState.DISCONNECTED || 
                newState == PeerConnection.IceConnectionState.CLOSED) {
                Log.w(TAG, "ICE connection disconnected/failed/closed: $newState")
                (_callUiState.value as? CallUiState.Active)?.let { currentState ->
                    _callUiState.value = currentState.copy(callStatusMessage = "Call Disconnected/Failed")
                 } ?: run { // If not in active state, maybe it's an error during setup
                     if (_callUiState.value !is CallUiState.Error && _callUiState.value !is CallUiState.Idle) {
                         _callUiState.value = CallUiState.Error("Call connection failed: $newState")
                     }
                 }
                // Consider ending the call or attempting to reconnect based on the state
                 // endCall() // This might be too abrupt, depends on strategy
            }
        }

        override fun onIceConnectionReceivingChange(receiving: Boolean) {
            Log.d(TAG, "IceConnectionReceivingChange: $receiving")
        }

        override fun onIceGatheringChange(newState: PeerConnection.IceGatheringState?) {
            Log.d(TAG, "IceGatheringState changed: $newState")
        }

        override fun onIceCandidate(candidate: IceCandidate?) {
            candidate?.let {
                Log.d(TAG, "onIceCandidate: Sending ICE candidate to remote peer")
                currentCallId?.let { callId ->
                    viewModelScope.launch {
                        firestoreService.sendIceCandidate(callId, it, isCaller)
                            .onFailure { e -> Log.e(TAG, "Error sending ICE candidate", e) }
                    }
                }
            }
        }

        override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {
            Log.d(TAG, "onIceCandidatesRemoved: $candidates")
        }

        override fun onAddStream(stream: MediaStream?) {
            // Deprecated, use onAddTrack instead
            Log.d(TAG, "onAddStream: Remote stream added (deprecated)")
        }

        override fun onRemoveStream(stream: MediaStream?) {
            Log.d(TAG, "onRemoveStream: Remote stream removed")
             (_callUiState.value as? CallUiState.Active)?.let { currentState ->
                 // If the main stream is removed, clear both tracks
                _callUiState.value = currentState.copy(remoteAudioTrack = null, remoteVideoTrack = null, callStatusMessage = "Remote stream removed")
             }
        }

        override fun onDataChannel(dataChannel: DataChannel?) {
            Log.d(TAG, "onDataChannel: $dataChannel")
        }

        override fun onRenegotiationNeeded() {
            Log.d(TAG, "onRenegotiationNeeded")
        }

        override fun onAddTrack(receiver: RtpReceiver?, mediaStreams: Array<out MediaStream>?) {
            Log.d(TAG, "onAddTrack: Track added")
            receiver?.track()?.let { track ->
                (_callUiState.value as? CallUiState.Active)?.let { currentState ->
                    if (track.kind() == MediaStreamTrack.AUDIO_TRACK_KIND) {
                        Log.d(TAG, "Remote audio track received via onAddTrack")
                        _callUiState.value = currentState.copy(
                            remoteAudioTrack = track as AudioTrack,
                            callStatusMessage = "Remote audio track available"
                        )
                    } else if (track.kind() == MediaStreamTrack.VIDEO_TRACK_KIND) {
                        Log.d(TAG, "Remote video track received via onAddTrack")
                        _callUiState.value = currentState.copy(
                            remoteVideoTrack = track as VideoTrack,
                            callStatusMessage = "Remote video track available"
                        )
                    }
                }
            }
        }
    }
    
    private fun initializeLocalMedia(): Boolean {
        localAudioTrack = webRTCManager.createLocalAudioTrack()
        localVideoTrack = webRTCManager.createLocalVideoTrack() // Create video track
        
        if (localAudioTrack == null || localVideoTrack == null) {
            Log.e(TAG, "Failed to create local media tracks (audio or video).")
            _callUiState.value = CallUiState.Error("Failed to initialize microphone or camera.")
            return false
        }
        
        // Update UI state with local tracks
        // Need to transition to an Active state to hold these tracks
        if (_callUiState.value !is CallUiState.Active) { // If not already active (e.g. during call setup)
             _callUiState.value = CallUiState.Active(
                 localAudioTrack = localAudioTrack,
                 localVideoTrack = localVideoTrack,
                 callStatusMessage = "Initializing media..."
             )
        } else { // If already active, just update tracks
            (_callUiState.value as? CallUiState.Active)?.let {
                 _callUiState.value = it.copy(localAudioTrack = localAudioTrack, localVideoTrack = localVideoTrack)
            }
        }
        return true
    }

    private fun addLocalTracksToPeerConnection() {
        if (localPeerConnection == null) {
            Log.e(TAG, "PeerConnection is null, cannot add tracks.")
            return
        }
        localAudioTrack?.let { localPeerConnection?.addTrack(it) }
        localVideoTrack?.let { localPeerConnection?.addTrack(it) } // Add video track
        Log.d(TAG, "Local audio and video tracks added to PeerConnection.")
    }


    fun startCall(targetUserId: String) {
        val callerId = auth.currentUser?.uid
        if (callerId == null) {
            _callUiState.value = CallUiState.Error("User not authenticated.")
            return
        }
        isCaller = true
        currentCallId = UUID.randomUUID().toString() 
        // _callUiState.value = CallUiState.Calling("Initializing call to user...") // This will be set by Active state

        if (!initializeLocalMedia()) return // Initialize media first

        viewModelScope.launch {
            val callData = CallData(
                callId = currentCallId!!,
                callerId = callerId,
                calleeId = targetUserId,
                status = CallStatus.RINGING.name
            )
            firestoreService.createCall(callData).fold(
                onSuccess = {
                    Log.d(TAG, "Call document created successfully: $currentCallId")
                    initializePeerConnectionAndCreateOffer(currentCallId!!, targetUserId)
                    listenForRemoteData(currentCallId!!, targetUserId)
                },
                onFailure = { e ->
                    Log.e(TAG, "Failed to create call document", e)
                    _callUiState.value = CallUiState.Error("Failed to initiate call. Please try again.")
                }
            )
        }
    }
    
    private fun initializePeerConnectionAndCreateOffer(callId: String, targetUserId: String) {
        localPeerConnection = webRTCManager.createPeerConnection(peerConnectionObserver)
        if (localPeerConnection == null) {
            _callUiState.value = CallUiState.Error("Failed to create PeerConnection.")
            return
        }
        addLocalTracksToPeerConnection() // Add audio and video tracks
        
        (_callUiState.value as? CallUiState.Active)?.let {
            _callUiState.value = it.copy(callStatusMessage = "Calling ${targetUserId.take(8)}...")
        }


        webRTCManager.createOffer(localPeerConnection!!, object : WebRTCManager.SimpleSdpObserver() {
            override fun onCreateSuccess(sdp: SessionDescription?) {
                super.onCreateSuccess(sdp) 
                sdp?.let { offerSdp ->
                    Log.d(TAG, "Offer created. Sending to Firestore.")
                    viewModelScope.launch {
                        firestoreService.sendOfferSdp(callId, offerSdp).fold(
                            onSuccess = { Log.d(TAG, "Offer SDP sent successfully.") },
                            onFailure = { e ->
                                Log.e(TAG, "Failed to send Offer SDP", e)
                                _callUiState.value = CallUiState.Error("Failed to send call offer.")
                            }
                        )
                    }
                } ?: run {
                     _callUiState.value = CallUiState.Error("Offer SDP was null.")
                }
            }
            override fun onCreateFailure(error: String?) {
                super.onCreateFailure(error)
                _callUiState.value = CallUiState.Error("Failed to create offer: $error")
            }
             override fun onSetFailure(error: String?) { 
                super.onSetFailure(error)
                _callUiState.value = CallUiState.Error("Failed to set local description (offer): $error")
            }
        })
    }


    fun receiveCall(callId: String, callerId: String, offerSdp: SessionDescription) {
        val calleeId = auth.currentUser?.uid
        if (calleeId == null) {
            _callUiState.value = CallUiState.Error("User not authenticated to receive call.")
            return
        }
        isCaller = false
        currentCallId = callId
        // _callUiState.value = CallUiState.Ringing("Incoming call from ${callerId.take(8)}...") // Will be set by Active

        if (!initializeLocalMedia()) return // Initialize media first

        localPeerConnection = webRTCManager.createPeerConnection(peerConnectionObserver)
        if (localPeerConnection == null) {
            _callUiState.value = CallUiState.Error("Failed to create PeerConnection for incoming call.")
            return
        }
        addLocalTracksToPeerConnection() // Add audio and video tracks

        (_callUiState.value as? CallUiState.Active)?.let {
            _callUiState.value = it.copy(callStatusMessage = "Incoming call from ${callerId.take(8)}...")
        }


        webRTCManager.setRemoteDescription(localPeerConnection!!, offerSdp, object : WebRTCManager.SimpleSdpObserver() {
            override fun onSetSuccess() {
                super.onSetSuccess()
                Log.d(TAG, "Remote description (offer) set successfully. Creating answer.")
                webRTCManager.createAnswer(localPeerConnection!!, object : WebRTCManager.SimpleSdpObserver() {
                    override fun onCreateSuccess(sdp: SessionDescription?) {
                        super.onCreateSuccess(sdp)
                        sdp?.let { answerSdp ->
                            Log.d(TAG, "Answer created. Sending to Firestore.")
                            viewModelScope.launch {
                                firestoreService.sendAnswerSdp(callId, answerSdp).fold(
                                    onSuccess = { Log.d(TAG, "Answer SDP sent successfully.") },
                                    onFailure = { e ->
                                        Log.e(TAG, "Failed to send Answer SDP", e)
                                        _callUiState.value = CallUiState.Error("Failed to send call answer.")
                                    }
                                )
                            }
                        } ?: run {
                             _callUiState.value = CallUiState.Error("Answer SDP was null.")
                        }
                    }
                    override fun onCreateFailure(error: String?) {
                        super.onCreateFailure(error)
                        _callUiState.value = CallUiState.Error("Failed to create answer: $error")
                    }
                    override fun onSetFailure(error: String?) { 
                        super.onSetFailure(error)
                        _callUiState.value = CallUiState.Error("Failed to set local description (answer): $error")
                    }
                })
            }
            override fun onSetFailure(error: String?) {
                super.onSetFailure(error)
                _callUiState.value = CallUiState.Error("Failed to set remote description (offer): $error")
            }
        })
        listenForRemoteData(callId, callerId) 
    }
    
    private fun listenForRemoteData(callId: String, remoteUserId: String) {
        viewModelScope.launch {
            firestoreService.listenForIceCandidates(callId, !isCaller) 
                .catch { e -> Log.e(TAG, "Error listening for ICE candidates", e) }
                .collect { iceCandidateModel ->
                    Log.d(TAG, "Received remote ICE candidate: ${iceCandidateModel.sdp}")
                    localPeerConnection?.let { pc ->
                        webRTCManager.addIceCandidate(pc, iceCandidateModel.toIceCandidate())
                    } ?: Log.e(TAG, "PeerConnection null when trying to add remote ICE candidate.")
                }
        }
        
        viewModelScope.launch {
            firestoreService.listenToCallData(callId)
                .catch { e -> Log.e(TAG, "Error listening to call data updates", e) }
                .collect { callData ->
                    if (callData?.status == CallStatus.ENDED.name || callData?.status == CallStatus.DECLINED.name) {
                        Log.d(TAG, "Call ended or declined by remote user. Status: ${callData.status}")
                        (_callUiState.value as? CallUiState.Active)?.let { currentState ->
                             _callUiState.value = currentState.copy(callStatusMessage = "Call ${callData.status?.toLowerCase()}", remoteAudioTrack = null, remoteVideoTrack = null)
                        }
                        cleanUp()
                    }
                    if (isCaller && callData?.answerSdp != null && localPeerConnection?.remoteDescription == null) {
                        Log.d(TAG, "Caller received answer SDP via CallData listener.")
                        onRemoteSdpReceived(callData.answerSdp.toSessionDescription())
                    }
                }
        }
    }

    fun onRemoteSdpReceived(sdp: SessionDescription) {
        if (localPeerConnection == null) {
            Log.e(TAG, "PeerConnection not initialized when trying to set remote SDP.")
            _callUiState.value = CallUiState.Error("Call not properly initialized.")
            return
        }
        Log.d(TAG, "Received remote SDP of type: ${sdp.type}. Setting remote description.")
        webRTCManager.setRemoteDescription(localPeerConnection!!, sdp, object : WebRTCManager.SimpleSdpObserver() {
            override fun onSetSuccess() {
                super.onSetSuccess()
                Log.d(TAG, "Remote description set successfully from onRemoteSdpReceived.")
            }
            override fun onSetFailure(error: String?) {
                super.onSetFailure(error)
                Log.e(TAG, "Failed to set remote description from onRemoteSdpReceived: $error")
                _callUiState.value = CallUiState.Error("Failed to set remote description: $error")
            }
        })
    }

    fun endCall() {
        Log.d(TAG, "End call requested.")
        (_callUiState.value as? CallUiState.Active)?.let {
            _callUiState.value = it.copy(callStatusMessage = "Ending call...")
        }
        currentCallId?.let { callId ->
            viewModelScope.launch {
                firestoreService.updateCallStatus(callId, CallStatus.ENDED.name)
                    .onFailure { e -> Log.e(TAG, "Error updating call status to ENDED", e) }
            }
        }
        cleanUp()
    }
    
    private fun cleanUp() {
        Log.d(TAG, "Cleaning up WebRTC resources in ViewModel.")
        localAudioTrack?.dispose() // Explicitly dispose tracks
        localAudioTrack = null
        localVideoTrack?.dispose()
        localVideoTrack = null
        
        localPeerConnection?.close() 
        localPeerConnection = null
        
        _callUiState.value = CallUiState.Idle 
        currentCallId = null
        // WebRTCManager's own resources (factory, capturer, etc.) are disposed in onCleared()
    }

    override fun onCleared() {
        super.onCleared()
        Log.d(TAG, "CallViewModel onCleared. Ensuring cleanup.")
        if (_callUiState.value !is CallUiState.Idle) { 
            endCall() 
        }
         webRTCManager.dispose() 
    }
}

// Sealed class for UI state - updated for video
sealed class CallUiState {
    object Idle : CallUiState()
    // Calling and Ringing can be part of Active with a specific status message
    // For simplicity, keeping them separate if they have distinct UI representations beyond a message.
    data class Calling(val message: String) : CallUiState() // Could be merged into Active
    data class Ringing(val message: String) : CallUiState() // Could be merged into Active
    
    data class Active(
        val callStatusMessage: String = "Connecting...",
        val localAudioTrack: AudioTrack? = null,
        val remoteAudioTrack: AudioTrack? = null,
        val localVideoTrack: VideoTrack? = null,    // Added for local video
        val remoteVideoTrack: VideoTrack? = null,   // Added for remote video
        val signalingState: PeerConnection.SignalingState? = null,
        val iceConnectionState: PeerConnection.IceConnectionState? = null
    ) : CallUiState() {
        // Convenience copy function
        fun copy(
            callStatusMessage: String = this.callStatusMessage,
            localAudioTrack: AudioTrack? = this.localAudioTrack,
            remoteAudioTrack: AudioTrack? = this.remoteAudioTrack,
            localVideoTrack: VideoTrack? = this.localVideoTrack,
            remoteVideoTrack: VideoTrack? = this.remoteVideoTrack,
            signalingState: PeerConnection.SignalingState? = this.signalingState,
            iceConnectionState: PeerConnection.IceConnectionState? = this.iceConnectionState
        ): Active = Active(
            callStatusMessage, localAudioTrack, remoteAudioTrack, 
            localVideoTrack, remoteVideoTrack, 
            signalingState, iceConnectionState
        )
    }
    data class Error(val message: String) : CallUiState()
}
