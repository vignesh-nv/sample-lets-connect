package com.example.randomconnectapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.randomconnectapp.data.FirestoreService
import com.example.randomconnectapp.model.User
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class UserDetailsViewModel : ViewModel() {

    private val firestoreService = FirestoreService()
    private val auth = FirebaseAuth.getInstance()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    private val _saveSuccess = MutableStateFlow(false)
    val saveSuccess: StateFlow<Boolean> = _saveSuccess

    fun saveUserDetails(age: Int, gender: String, language: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            _saveSuccess.value = false

            val currentUser = auth.currentUser
            if (currentUser == null) {
                _errorMessage.value = "User not authenticated."
                _isLoading.value = false
                return@launch
            }

            val user = User(
                uid = currentUser.uid,
                phoneNumber = currentUser.phoneNumber ?: "",
                age = age,
                gender = gender,
                language = language,
                isProfileComplete = true // Mark profile as complete
            )

            val result = firestoreService.saveUserDetails(user)
            result.fold(
                onSuccess = {
                    _saveSuccess.value = true
                    _isLoading.value = false
                },
                onFailure = { exception ->
                    // Escaped the dollar sign here
                    _errorMessage.value = "Failed to save details: ${exception.message}" 
                    _isLoading.value = false
                }
            )
        }
    }
    
    fun setErrorMessage(message: String?) {
        _errorMessage.value = message
    }
}
