package com.example.randomconnectapp.ui.call

import android.Manifest
import android.view.SurfaceView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.randomconnectapp.viewmodel.CallViewModel
import com.example.randomconnectapp.viewmodel.CallUiState
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CallScreen(
    navController: NavController,
    targetUserIdToCall: String?, // Renamed for clarity: This is who we intend to call
    channelNameToJoin: String?,  // Renamed for clarity: This is a channel we are invited to join
    callerId: String?,           // The ID of the user who is calling us (when we are the callee)
    callViewModel: CallViewModel = viewModel()
) {
    val context = LocalContext.current
    val callUiState by callViewModel.callUiState.collectAsState()

    val permissionsState = rememberMultiplePermissionsState(
        permissions = listOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA)
    )

    // Handle call initiation or joining based on passed parameters
    LaunchedEffect(permissionsState.allPermissionsGranted, Unit) { // Relaunch if permissions change
        if (permissionsState.allPermissionsGranted) {
            if (!targetUserIdToCall.isNullOrEmpty()) {
                // We are the caller, initiate the call
                // Check current state to avoid re-initiating if already calling/active
                if (callUiState is CallUiState.Idle || (callUiState as? CallUiState.Error)?.message?.contains("User not authenticated") == true) {
                    callViewModel.startCall(targetUserIdToCall)
                }
            } else if (!channelNameToJoin.isNullOrEmpty() && !callerId.isNullOrEmpty()) {
                // We are the callee, an invitation is active for us
                // Check current state to avoid re-processing if already ringing or active
                 if (callUiState is CallUiState.Idle) { // Only process if truly idle
                    callViewModel.joinInvitedCall(channelNameToJoin, callerId)
                }
            }
        } else {
             if (permissionsState.shouldShowRationale || !permissionsState.permissionRequested) {
                // No action needed here, button below will trigger request
            } else {
                // Permissions denied, and user doesn't want to be asked again.
                // Show error or navigate back. For now, ViewModel might set an error state.
                // Or simply do nothing and let the UI show the permission request button.
                // If CallViewModel's init already set an error, that's fine.
                 if (callUiState !is CallUiState.Error) { // Avoid overwriting specific Agora init errors
                    // It's okay if this doesn't immediately trigger an error state in VM,
                    // the UI will reflect lack of permissions.
                 }
            }
        }
    }
    
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when (val state = callUiState) {
            is CallUiState.Idle -> {
                // Could be initial state, or after a call ended and cleaned up.
                // Navigate back if idle and we were on call screen.
                 LaunchedEffect(Unit) { // Use Unit to run once when Idle is reached
                    if (navController.currentDestination?.route?.startsWith("call_screen") == true) {
                       navController.popBackStack()
                    }
                }
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Call ended or not initiated.", style = MaterialTheme.typography.bodyLarge)
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { navController.popBackStack() }) {
                            Text("Go Back")
                        }
                    }
                }
            }
            is CallUiState.Error -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Error: ${state.message}", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.headlineSmall)
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { callViewModel.endCall() /* Attempt to reset */ ; navController.popBackStack()}) {
                             Text("Dismiss")
                        }
                    }
                }
            }
            is CallUiState.Calling -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(state.message, style = MaterialTheme.typography.headlineSmall)
                        Spacer(modifier = Modifier.height(20.dp))
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(20.dp))
                        Button(onClick = { callViewModel.endCall() }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                            Text("Cancel Call")
                        }
                    }
                }
            }
            is CallUiState.Ringing -> {
                 Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Text("Incoming call from ${state.callerId.take(8)}...", style = MaterialTheme.typography.headlineSmall)
                        Text("Channel: ${state.channelName.take(8)}", style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.height(30.dp))
                        Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth(0.8f)) {
                            Button(onClick = { callViewModel.acceptCall(state.channelName) }) {
                                Text("Accept")
                            }
                            Button(onClick = { callViewModel.declineCall(state.channelName) }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                                Text("Decline")
                            }
                        }
                    }
                }
            }
            is CallUiState.Active -> {
                Box(modifier = Modifier.fillMaxSize()) {
                    // Remote video (simplified: first remote user takes fullscreen)
                    state.remoteUids.firstOrNull()?.let { remoteUid ->
                        key(remoteUid) { // Important to recompose if remoteUid changes
                            AndroidView(
                                factory = { ctx ->
                                    SurfaceView(ctx).apply {
                                        // Optional: this.setZOrderMediaOverlay(true) for local on top of remote
                                        callViewModel.setupRemoteVideoSurface(this, remoteUid)
                                    }
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    } ?: Box(modifier = Modifier.fillMaxSize().background(Color.DarkGray), contentAlignment = Alignment.Center) {
                        Text("Waiting for remote user...", color = Color.White)
                    }

                    // Local video (picture-in-picture)
                    // Display local video only if not muted and local UID is available
                    if (!state.isLocalVideoMuted && state.localUid != null) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(16.dp)
                                .size(width = 120.dp, height = 180.dp)
                                .background(Color.Black) // Background for visibility
                        ) {
                            AndroidView(
                                factory = { ctx ->
                                    SurfaceView(ctx).apply {
                                        // this.setZOrderMediaOverlay(true) // If you want it on top of the remote view
                                        callViewModel.setupLocalVideoSurface(this)
                                    }
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                    
                    // Call controls and status overlay
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom
                    ) {
                        Text(state.callStatusMessage, style = MaterialTheme.typography.bodyLarge, color = Color.White)
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            IconButton(onClick = { callViewModel.toggleLocalAudioMute() }) {
                                Icon(
                                    if (state.isLocalAudioMuted) Icons.Filled.MicOff else Icons.Filled.Mic,
                                    contentDescription = "Toggle Audio",
                                    tint = Color.White,
                                    modifier = Modifier.size(48.dp)
                                )
                            }
                            IconButton(onClick = { callViewModel.toggleLocalVideoMute() }) {
                                Icon(
                                    if (state.isLocalVideoMuted) Icons.Filled.VideocamOff else Icons.Filled.Videocam,
                                    contentDescription = "Toggle Video",
                                    tint = Color.White,
                                     modifier = Modifier.size(48.dp)
                                )
                            }
                            IconButton(onClick = { callViewModel.endCall() }) {
                                Icon(
                                    Icons.Filled.CallEnd, 
                                    contentDescription = "End Call",
                                    tint = MaterialTheme.colorScheme.error, // Typically red
                                    modifier = Modifier.size(48.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Common UI for permission request if not granted
        if (!permissionsState.allPermissionsGranted && callUiState !is CallUiState.Idle && callUiState !is CallUiState.Error) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "Audio and Camera permissions are required for video calls.",
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(16.dp)
                    )
                    Button(onClick = { permissionsState.launchMultiplePermissionRequest() }) {
                        Text("Grant Permissions")
                    }
                }
            }
        }
    }
}
