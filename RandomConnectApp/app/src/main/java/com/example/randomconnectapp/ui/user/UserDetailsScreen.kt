package com.example.randomconnectapp.ui.user

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.randomconnectapp.viewmodel.UserDetailsViewModel // Will be created later

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserDetailsScreen(navController: NavController, userDetailsViewModel: UserDetailsViewModel = viewModel()) {
    var age by remember { mutableStateOf("") }
    var selectedGender by remember { mutableStateOf("") }
    var language by remember { mutableStateOf("") }
    val genderOptions = listOf("Male", "Female", "Other", "Prefer not to say")
    var genderDropdownExpanded by remember { mutableStateOf(false) }

    val isLoading by userDetailsViewModel.isLoading.collectAsState()
    val errorMessage by userDetailsViewModel.errorMessage.collectAsState()
    val saveSuccess by userDetailsViewModel.saveSuccess.collectAsState()

    LaunchedEffect(saveSuccess) {
        if (saveSuccess) {
            navController.navigate("home") { // Navigate to home after successful save
                popUpTo("user_details_input") { inclusive = true } // Clear this screen from backstack
                popUpTo("otp_verification") { inclusive = true } // Also clear OTP screen
                popUpTo("phone_number_input") { inclusive = true } // Also clear phone input
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Complete Your Profile", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = age,
            onValueChange = { age = it.filter { char -> char.isDigit() } },
            label = { Text("Age") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))

        // Gender Dropdown
        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = selectedGender,
                onValueChange = { }, // Not directly editable
                label = { Text("Gender") },
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = genderDropdownExpanded) },
                modifier = Modifier.fillMaxWidth()
            )
            DropdownMenu(
                expanded = genderDropdownExpanded,
                onDismissRequest = { genderDropdownExpanded = false },
                modifier = Modifier.fillMaxWidth()
            ) {
                genderOptions.forEach { gender ->
                    DropdownMenuItem(
                        text = { Text(gender) },
                        onClick = {
                            selectedGender = gender
                            genderDropdownExpanded = false
                        }
                    )
                }
            }
            // Clickable overlay to expand dropdown
            Box(modifier = Modifier.matchParentSize().wrapContentSize(Alignment.TopStart).fillMaxHeight().fillMaxWidth().clickable { genderDropdownExpanded = !genderDropdownExpanded })
        }
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = language,
            onValueChange = { language = it },
            label = { Text("Preferred Language") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                val ageInt = age.toIntOrNull()
                if (ageInt != null && selectedGender.isNotBlank() && language.isNotBlank()) {
                    userDetailsViewModel.saveUserDetails(ageInt, selectedGender, language)
                } else {
                    // Handle validation error, e.g., show a Toast or update errorMessage
                    userDetailsViewModel.setErrorMessage("Please fill all fields correctly.")
                }
            },
            enabled = !isLoading && age.isNotBlank() && selectedGender.isNotBlank() && language.isNotBlank()
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            } else {
                Text("Submit Details")
            }
        }
        errorMessage?.let {
            Spacer(modifier = Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error)
        }
    }
}
