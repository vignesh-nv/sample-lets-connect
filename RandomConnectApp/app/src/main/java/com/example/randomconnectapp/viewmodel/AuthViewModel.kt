package com.example.randomconnectapp.viewmodel

import android.app.Activity
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.randomconnectapp.data.FirestoreService 
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class AuthViewModel : ViewModel() {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val firestoreService = FirestoreService() 

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    private val _verificationId = MutableStateFlow<String?>(null)
    val verificationId: StateFlow<String?> = _verificationId

    enum class AuthState {
        IDLE,
        NEEDS_DETAILS, 
        AUTHENTICATED  
    }
    private val _authState = MutableStateFlow(AuthState.IDLE)
    val authState: StateFlow<AuthState> = _authState


    private var resendingToken: PhoneAuthProvider.ForceResendingToken? = null

    companion object {
        private const val TAG = "AuthViewModel"
    }

    fun requestOtp(phoneNumber: String, activity: Activity) {
        _isLoading.value = true
        _errorMessage.value = null
        _verificationId.value = null 
        _authState.value = AuthState.IDLE


        val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                signInWithPhoneAuthCredential(credential)
            }

            override fun onVerificationFailed(e: FirebaseException) {
                _isLoading.value = false
                _errorMessage.value = "Verification failed: ${e.message}"
                _authState.value = AuthState.IDLE
                Log.e(TAG, "Verification failed", e)
            }

            override fun onCodeSent(
                verificationId: String,
                token: PhoneAuthProvider.ForceResendingToken
            ) {
                _isLoading.value = false
                this@AuthViewModel._verificationId.value = verificationId
                this@AuthViewModel.resendingToken = token
            }
        }

        val options = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(phoneNumber)       
            .setTimeout(60L, TimeUnit.SECONDS) 
            .setActivity(activity)                 
            .setCallbacks(callbacks)          
            .build()
        PhoneAuthProvider.verifyPhoneNumber(options)
    }

    fun verifyOtp(otpCode: String) {
        val currentVerificationId = _verificationId.value
        if (currentVerificationId == null) {
            _errorMessage.value = "Verification ID is missing. Please request OTP again."
            _authState.value = AuthState.IDLE
            return
        }
        _isLoading.value = true
        _errorMessage.value = null

        val credential = PhoneAuthProvider.getCredential(currentVerificationId, otpCode)
        signInWithPhoneAuthCredential(credential)
    }

    private fun signInWithPhoneAuthCredential(credential: PhoneAuthCredential) {
        viewModelScope.launch {
            try {
                val authResult = auth.signInWithCredential(credential).await()
                val firebaseUser = authResult?.user
                if (firebaseUser != null) {
                    val userDetails = firestoreService.getUserDetails(firebaseUser.uid).firstOrNull()
                    if (userDetails != null && userDetails.isProfileComplete) {
                        _authState.value = AuthState.AUTHENTICATED
                        // User is authenticated and profile is complete, set them online
                        setUserOnlineStatus(true) 
                    } else {
                        _authState.value = AuthState.NEEDS_DETAILS
                        // User might be new or profile incomplete, status will be set after details submission if needed
                        // or if they go online later. For now, if they were online, mark offline.
                        // However, this might be premature if they are just completing profile.
                        // Let's assume they are not "fully" online until profile is complete.
                        setUserOnlineStatus(false) 
                    }
                } else {
                     _errorMessage.value = "Sign-in failed: User not found."
                    _authState.value = AuthState.IDLE
                }
                _isLoading.value = false
                _errorMessage.value = null 
            } catch (e: FirebaseException) {
                _isLoading.value = false
                _errorMessage.value = "Sign-in failed: ${e.message}"
                _authState.value = AuthState.IDLE
                Log.e(TAG, "Sign-in failed", e)
            }
        }
    }
    
    fun clearErrorMessage() {
        _errorMessage.value = null
    }

    fun resetVerificationState() {
        // Before resetting, ensure user is marked offline if they were authenticated
        if (_authState.value == AuthState.AUTHENTICATED || _authState.value == AuthState.NEEDS_DETAILS) {
            // This might be called during navigation, ensure it's appropriate
            // For example, if navigating from OTP to UserDetails, we don't want to set offline yet.
            // This function is more for when user explicitly signs out or process is fully reset.
        }
        _verificationId.value = null
        _authState.value = AuthState.IDLE
        _errorMessage.value = null
        _isLoading.value = false
    }

    // Renamed for clarity and to take a boolean parameter
    fun setUserOnlineStatus(isOnline: Boolean) {
        val uid = auth.currentUser?.uid
        if (uid != null) {
            viewModelScope.launch {
                try {
                    firestoreService.updateUserOnlineStatus(uid, isOnline)
                    Log.d(TAG, "User ${uid} status set to: ${if (isOnline) "online" else "offline"}")
                } catch (e: Exception) {
                    Log.e(TAG, "Error updating user online status for ${uid}", e)
                    // Optionally, update an error state in the ViewModel
                    // _errorMessage.value = "Failed to update online status: ${e.message}"
                }
            }
        } else {
            Log.d(TAG, "Cannot set user online status: User not authenticated.")
        }
    }
}

// Ensure this extension is available
suspend fun <T> com.google.android.gms.tasks.Task<T>.await(): T? {
    return try {
        kotlinx.coroutines.tasks.await(this)
    } catch (e: Exception) {
        null // Or rethrow, depending on how you want to handle task cancellations/failures
    }
}
