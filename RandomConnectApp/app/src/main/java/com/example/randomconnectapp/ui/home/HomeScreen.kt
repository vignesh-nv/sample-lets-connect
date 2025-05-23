package com.example.randomconnectapp.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController 
import com.example.randomconnectapp.model.User
import com.example.randomconnectapp.navigation.AppDestinations 
import com.example.randomconnectapp.viewmodel.HomeScreenViewModel
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import com.example.randomconnectapp.webrtc.models.CallData 

@OptIn(ExperimentalMaterial3Api::class) 
@Composable
fun HomeScreen(
    navController: NavController, 
    homeScreenViewModel: HomeScreenViewModel = viewModel()
) {
    val onlineUsers by homeScreenViewModel.onlineUsers.collectAsState()
    val isLoading by homeScreenViewModel.isLoading.collectAsState()
    val errorMessage by homeScreenViewModel.errorMessage.collectAsState()
    val selectedUserForConnection by homeScreenViewModel.selectedUserForConnection.collectAsState()
    val infoMessage by homeScreenViewModel.infoMessage.collectAsState()
    val incomingCall by homeScreenViewModel.incomingCall.collectAsState()

    var showConnectionDialog by remember { mutableStateOf(false) }
    var showInfoDialog by remember { mutableStateOf(false) }
    var showIncomingCallDialog by remember { mutableStateOf(false) } 


    LaunchedEffect(selectedUserForConnection) {
        if (selectedUserForConnection != null) {
            showConnectionDialog = true
        }
    }

    LaunchedEffect(infoMessage) {
        if (infoMessage != null) {
            showInfoDialog = true
        }
    }
    
    LaunchedEffect(incomingCall) {
        if (incomingCall != null) {
            showIncomingCallDialog = true
        }
    }

    if (showConnectionDialog && selectedUserForConnection != null) {
        AlertDialog(
            onDismissRequest = {
                showConnectionDialog = false
                homeScreenViewModel.clearConnectionStates() 
            },
            title = { Text("Connect to User?") },
            text = { Text("Do you want to start a call with User: ${selectedUserForConnection?.uid?.take(8)}...?") },
            confirmButton = {
                Button(onClick = {
                    showConnectionDialog = false
                    val targetUserId = selectedUserForConnection?.uid
                    if (targetUserId != null) {
                        navController.navigate(
                            AppDestinations.CALL_SCREEN.replace("{callId}", "null") 
                                .replace("{targetUserId}", URLEncoder.encode(targetUserId, StandardCharsets.UTF_8.name()))
                                .replace("{offerSdp}", "null")
                                .replace("{offerType}", "null")
                        )
                    }
                    homeScreenViewModel.clearConnectionStates()
                }) {
                    Text("Yes, Call")
                }
            },
            dismissButton = {
                 Button(onClick = { 
                     showConnectionDialog = false
                     homeScreenViewModel.clearConnectionStates()
                 }) {
                    Text("Cancel")
                }
            }
        )
    }
    
    if (showInfoDialog && infoMessage != null) {
        AlertDialog(
            onDismissRequest = {
                showInfoDialog = false
                homeScreenViewModel.clearConnectionStates()
            },
            title = { Text("Information") },
            text = { Text(infoMessage ?: "Unknown information") },
            confirmButton = {
                Button(onClick = {
                    showInfoDialog = false
                    homeScreenViewModel.clearConnectionStates()
                }) {
                    Text("OK")
                }
            }
        )
    }

    if (showIncomingCallDialog && incomingCall != null) {
        val callData = incomingCall!! 
        AlertDialog(
            onDismissRequest = {
                showIncomingCallDialog = false
                homeScreenViewModel.declineIncomingCall(callData.callId) // Decline if dismissed
            },
            title = { Text("Incoming Call") },
            text = { Text("You have an incoming call from ${callData.callerId.take(8)}.... Do you want to answer?") },
            confirmButton = {
                Button(onClick = {
                    showIncomingCallDialog = false
                    val offerSdpJson = callData.offerSdp?.description ?: "" 
                    val offerSdpType = callData.offerSdp?.type ?: ""
                    
                    if (offerSdpJson.isNotEmpty() && offerSdpType.isNotEmpty()) {
                        navController.navigate(
                            AppDestinations.CALL_SCREEN
                                .replace("{callId}", URLEncoder.encode(callData.callId, StandardCharsets.UTF_8.name()))
                                .replace("{targetUserId}", URLEncoder.encode(callData.callerId, StandardCharsets.UTF_8.name())) 
                                .replace("{offerSdp}", URLEncoder.encode(offerSdpJson, StandardCharsets.UTF_8.name()))
                                .replace("{offerType}", URLEncoder.encode(offerSdpType, StandardCharsets.UTF_8.name()))
                        )
                    } else {
                        // Handle missing SDP - potentially decline or show error
                        homeScreenViewModel.declineIncomingCall(callData.callId)
                    }
                    homeScreenViewModel.clearIncomingCall() // Clear after processing
                }) {
                    Text("Accept")
                }
            },
            dismissButton = {
                Button(onClick = {
                    showIncomingCallDialog = false
                    homeScreenViewModel.declineIncomingCall(callData.callId)
                }) {
                    Text("Decline")
                }
            }
        )
    }


    Scaffold( /* ... existing scaffold code ... */ 
        topBar = {
            TopAppBar(
                title = { Text("RandomConnect") }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { homeScreenViewModel.onConnectRandomlyClicked() },
                text = { Text("Connect Randomly") }
            )
        },
        floatingActionButtonPosition = FabPosition.Center
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp) 
        ) {
            if (isLoading && onlineUsers.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (errorMessage != null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "Error: $errorMessage", 
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else if (onlineUsers.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp), 
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No other users are online right now. Try again later!",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(bottom = 70.dp) 
                    )
                }
            } else {
                Text("Or, choose someone specific from the list below:", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(bottom = 8.dp))
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(onlineUsers, key = { it.uid }) { user ->
                        UserItem(user = user, onConnectClick = { userId ->
                             navController.navigate(
                                AppDestinations.CALL_SCREEN.replace("{callId}", "null")
                                .replace("{targetUserId}", URLEncoder.encode(userId, StandardCharsets.UTF_8.name()))
                                .replace("{offerSdp}", "null")
                                .replace("{offerType}", "null")
                            )
                        })
                    }
                }
            }
        }
    }
}

@Composable
fun UserItem(user: User, onConnectClick: (String) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                val displayName = if (user.phoneNumber.isNotBlank()) {
                    "User (...${user.phoneNumber.takeLast(4)})" 
                } else {
                    "User ID: ${user.uid.take(8)}..." 
                }
                Text(text = displayName, style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = "Language: ${user.language}", style = MaterialTheme.typography.bodySmall)
                Text(text = "Gender: ${user.gender}", style = MaterialTheme.typography.bodySmall)
                Text(text = "Age: ${user.age}", style = MaterialTheme.typography.bodySmall)
            }
            Button(onClick = { onConnectClick(user.uid) }) {
                Text("Connect")
            }
        }
    }
}
