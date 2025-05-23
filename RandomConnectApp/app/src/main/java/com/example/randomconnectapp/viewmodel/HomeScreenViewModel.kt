package com.example.randomconnectapp.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.randomconnectapp.data.FirestoreService
import com.example.randomconnectapp.model.User
import com.example.randomconnectapp.webrtc.models.CallData 
import com.example.randomconnectapp.webrtc.models.CallStatus 
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect // Ensure this import is present
import kotlinx.coroutines.launch

class HomeScreenViewModel : ViewModel() {

    private val firestoreService = FirestoreService()
    private val auth = FirebaseAuth.getInstance() 

    private val _onlineUsers = MutableStateFlow<List<User>>(emptyList())
    val onlineUsers: StateFlow<List<User>> = _onlineUsers

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    private val _selectedUserForConnection = MutableStateFlow<User?>(null)
    val selectedUserForConnection: StateFlow<User?> = _selectedUserForConnection
    
    private val _infoMessage = MutableStateFlow<String?>(null)
    val infoMessage: StateFlow<String?> = _infoMessage

    private val _incomingCall = MutableStateFlow<CallData?>(null)
    val incomingCall: StateFlow<CallData?> = _incomingCall


    companion object {
        private const val TAG = "HomeScreenViewModel"
    }

    init {
        fetchOnlineUsers()
        listenForIncomingCalls() 
    }

    fun fetchOnlineUsers() {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            val currentUserId = auth.currentUser?.uid
            if (currentUserId == null) {
                _errorMessage.value = "User not authenticated."
                _isLoading.value = false
                return@launch
            }

            firestoreService.getOnlineUsers(currentUserId)
                .catch { exception ->
                    _errorMessage.value = "Error fetching users: ${exception.message}"
                    Log.e(TAG, "Error fetching online users", exception)
                    _isLoading.value = false
                }
                .collect { users ->
                    _onlineUsers.value = users
                    _isLoading.value = false
                }
        }
    }

    private fun listenForIncomingCalls() {
        val currentUserId = auth.currentUser?.uid
        if (currentUserId == null) {
            Log.w(TAG, "Cannot listen for incoming calls: User not authenticated.")
            return
        }
        Log.d(TAG, "User ${currentUserId} starting to listen for incoming calls.")
        viewModelScope.launch {
            firestoreService.listenForIncomingCallsQuery(currentUserId) 
               .catch { e -> Log.e(TAG, "Error in incoming calls listener flow", e) }
               .collect { calls ->
                   // We are interested in the first ringing call that isn't already being handled
                   // This simple implementation takes the first one from the list.
                   // A more robust system might handle multiple incoming calls or update existing ones.
                   val newRingingCall = calls.firstOrNull { it.status == CallStatus.RINGING.name }
                   if (newRingingCall != null && _incomingCall.value?.callId != newRingingCall.callId) {
                       Log.d(TAG, "Incoming call detected: ${newRingingCall.callId} from ${newRingingCall.callerId}")
                       if (newRingingCall.offerSdp == null) {
                           Log.w(TAG, "Incoming call ${newRingingCall.callId} has no offer SDP. Cannot process.")
                           return@collect
                       }
                       _incomingCall.value = newRingingCall
                   } else if (newRingingCall == null && _incomingCall.value != null) {
                       // If the current incoming call is no longer ringing or gone, clear it
                       // This could happen if the caller cancels or status changes
                       val currentCall = _incomingCall.value
                       if (calls.none { it.callId == currentCall?.callId && it.status == CallStatus.RINGING.name }) {
                           Log.d(TAG, "Current incoming call ${currentCall?.callId} seems to be resolved elsewhere. Clearing.")
                           _incomingCall.value = null
                       }
                   }
               }
        }
    }


    fun onConnectRandomlyClicked() {
        _infoMessage.value = null 
        _selectedUserForConnection.value = null 

        val currentUserId = auth.currentUser?.uid
        if (currentUserId == null) {
            _errorMessage.value = "User not authenticated. Cannot connect."
            return
        }

        val usersToConnect = _onlineUsers.value 
        
        if (usersToConnect.isEmpty()) {
             _infoMessage.value = "No one else is online to connect with right now."
            Log.d(TAG, "No other users online to connect with.")
            return
        }
        
        val selectedUser = usersToConnect.randomOrNull()

        if (selectedUser != null) {
            _selectedUserForConnection.value = selectedUser
            Log.d(TAG, "Randomly selected user: ${selectedUser.uid} for connection.")
        } else {
            _infoMessage.value = "No one else is online to connect with right now."
            Log.d(TAG, "No other users available after filtering for random connection.")
        }
    }

    fun clearConnectionStates() {
        _selectedUserForConnection.value = null
        _infoMessage.value = null
    }

    fun clearIncomingCall() {
        _incomingCall.value = null
    }

    // Function to update call status (e.g., when user declines a call from dialog)
    fun declineIncomingCall(callId: String) {
        viewModelScope.launch {
            firestoreService.updateCallStatus(callId, CallStatus.DECLINED.name)
                .onSuccess { Log.d(TAG, "Call ${callId} declined successfully.") }
                .onFailure { e -> Log.e(TAG, "Failed to decline call ${callId}", e) }
        }
        clearIncomingCall() // Clear the incoming call from UI state
    }
}
