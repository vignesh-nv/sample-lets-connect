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
fun PhoneNumberScreen(navController: NavController, authViewModel: AuthViewModel) {
    var phoneNumber by remember { mutableStateOf("") }
    val isLoading by authViewModel.isLoading.collectAsState()
    val errorMessage by authViewModel.errorMessage.collectAsState()
    val verificationId by authViewModel.verificationId.collectAsState() // Assuming verificationId is exposed

    // Navigate to OTP screen when verificationId is available
    LaunchedEffect(verificationId) {
        if (verificationId != null) {
            // Pass phone number as an argument for display on OTP screen
            navController.navigate("otp_verification/")
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Enter Phone Number", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = phoneNumber,
            onValueChange = { phoneNumber = it },
            label = { Text("Phone Number (e.g., +16505551234)") }, // Added example format
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = {
                if (phoneNumber.isNotBlank()) {
                    // Make sure to include the country code
                    authViewModel.requestOtp(phoneNumber, navController.context as android.app.Activity)
                }
            },
            enabled = !isLoading && phoneNumber.isNotBlank()
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            } else {
                Text("Send OTP")
            }
        }
        errorMessage?.let {
            Spacer(modifier = Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error)
        }
    }
}
