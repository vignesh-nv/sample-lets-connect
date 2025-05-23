package com.example.randomconnectapp.ui.auth

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.randomconnectapp.viewmodel.AuthViewModel // Will be created later

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OTPScreen(navController: NavController, authViewModel: AuthViewModel, phoneNumber: String) {
    var otp by remember { mutableStateOf("") }
    val isLoading by authViewModel.isLoading.collectAsState()
    val errorMessage by authViewModel.errorMessage.collectAsState()
    val isUserSignedIn by authViewModel.isUserSignedIn.collectAsState() // Assuming this state is exposed

    // Navigate to next screen or show error based on sign-in status
    LaunchedEffect(isUserSignedIn) {
        if (isUserSignedIn == true) {
            // Navigate to a placeholder "Home" screen or user details screen
            // For now, let's assume a "home" route exists
            navController.navigate("home") {
                // Clear back stack up to phone_number_input
                popUpTo("phone_number_input") { inclusive = true }
            }
        }
    }
    
    LaunchedEffect(Unit) {
        authViewModel.clearErrorMessage() // Clear previous errors when screen is shown
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Enter OTP", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Text("Sent to: ", style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = otp,
            onValueChange = { otp = it },
            label = { Text("OTP Code") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = {
                if (otp.isNotBlank()) {
                    authViewModel.verifyOtp(otp)
                }
            },
            enabled = !isLoading && otp.isNotBlank()
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            } else {
                Text("Verify OTP")
            }
        }
        errorMessage?.let {
            Spacer(modifier = Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error)
        }
    }
}
