package com.example.randomconnectapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels // Import viewModels delegate
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.randomconnectapp.navigation.AppNavigation 
import com.example.randomconnectapp.ui.theme.RandomConnectAppTheme
import com.example.randomconnectapp.viewmodel.AuthViewModel // Import AuthViewModel
import com.google.firebase.auth.FirebaseAuth

class MainActivity : ComponentActivity() {

    // Get a reference to the AuthViewModel
    private val authViewModel: AuthViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            RandomConnectAppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation() 
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Check if user is authenticated (e.g., by checking current user in FirebaseAuth)
        // and then set user to online. The AuthViewModel's authState can also be used.
        // For simplicity, directly checking FirebaseAuth here.
        // A more robust way would be to observe authState from AuthViewModel.
        if (FirebaseAuth.getInstance().currentUser != null) {
            authViewModel.setUserOnlineStatus(true)
        }
    }

    override fun onStop() {
        super.onStop()
        // Set user to offline when the app is stopped.
        // This is not foolproof (e.g. app crash). More robust solutions involve Firebase Realtime Database's
        // onDisconnect() or Cloud Functions with Firestore.
        if (FirebaseAuth.getInstance().currentUser != null) {
            // Only set offline if the app is actually stopping and not just a configuration change
            // or temporary pause. isFinishing can help but isn't perfect.
            // For now, we'll set offline on any onStop if user is logged in.
            authViewModel.setUserOnlineStatus(false)
        }
    }
}
