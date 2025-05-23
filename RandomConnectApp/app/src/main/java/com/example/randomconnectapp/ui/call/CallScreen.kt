package com.example.randomconnectapp.ui.call

import android.Manifest
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView // For SurfaceViewRenderer
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.randomconnectapp.viewmodel.CallViewModel
import com.example.randomconnectapp.viewmodel.CallUiState
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import org.webrtc.EglBase
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CallScreen(
    navController: NavController,
    callId: String?, 
    targetUserId: String?, 
    offerSdpString: String? = null, 
    offerSdpType: String? = null,   
    callViewModel: CallViewModel = viewModel()
) {
    val context = LocalContext.current
    val callUiState by callViewModel.callUiState.collectAsState() // Corrected property name

    // Permissions for both Camera and Audio
    val permissionsState = rememberMultiplePermissionsState(
        permissions = listOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA)
    )

    // EGL context for rendering (should be shared if possible, but creating one per screen is simpler for now)
    // This should ideally come from WebRTCManager or be a singleton.
    // For simplicity, creating it here, but CallViewModel/WebRTCManager should provide it.
    val eglBaseContext = remember { EglBase.create().eglBaseContext }

    // SurfaceViewRenderers
    val localSurfaceViewRenderer = remember { SurfaceViewRenderer(context) }
    val remoteSurfaceViewRenderer = remember { SurfaceViewRenderer(context) }

    // Initialize renderers
    LaunchedEffect(eglBaseContext) {
        localSurfaceViewRenderer.init(eglBaseContext, null)
        localSurfaceViewRenderer.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT)
        localSurfaceViewRenderer.setMirror(true) // Local video is usually mirrored

        remoteSurfaceViewRenderer.init(eglBaseContext, null)
        remoteSurfaceViewRenderer.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
    }

    // Release renderers on dispose
    DisposableEffect(Unit) {
        onDispose {
            localSurfaceViewRenderer.release()
            remoteSurfaceViewRenderer.release()
        }
    }
    

    // Bind local video track
    LaunchedEffect(callUiState) {
        val activeState = callUiState as? CallUiState.Active
        activeState?.localVideoTrack?.let { track ->
            track.addSink(localSurfaceViewRenderer)
        }
        // Clean up if track is removed or state changes
        return@LaunchedEffect onDispose {
            activeState?.localVideoTrack?.removeSink(localSurfaceViewRenderer)
        }
    }

    // Bind remote video track
    LaunchedEffect(callUiState) {
        val activeState = callUiState as? CallUiState.Active
        activeState?.remoteVideoTrack?.let { track ->
            track.addSink(remoteSurfaceViewRenderer)
        }
        // Clean up if track is removed or state changes
        return@LaunchedEffect onDispose {
            activeState?.remoteVideoTrack?.removeSink(remoteSurfaceViewRenderer)
        }
    }


    LaunchedEffect(permissionsState.allPermissionsGranted) { 
        if (permissionsState.allPermissionsGranted) {
            if (targetUserId != null && callId == null) { 
                if (callUiState is CallUiState.Idle || (callUiState as? CallUiState.Error)?.message?.contains("User not authenticated") == true) {
                     callViewModel.startCall(targetUserId)
                }
            } else if (callId != null && offerSdpString != null && offerSdpType != null) { 
                 if (callUiState is CallUiState.Idle) { 
                    val offer = org.webrtc.SessionDescription(
                        org.webrtc.SessionDescription.Type.fromCanonicalForm(offerSdpType.toLowerCase()),
                        offerSdpString
                    )
                    val remotePartyId = targetUserId ?: "Unknown Caller" 
                    callViewModel.receiveCall(callId, remotePartyId, offer)
                 }
            }
        } else {
            // If permissions not granted yet, and not yet requested for all
            if (permissionsState.shouldShowRationale || !permissionsState.permissionRequested) {
                 // Requesting here if not all granted
                 // permissionsState.launchMultiplePermissionRequest() // This will be called by the button
            } else {
                // Permissions denied and rationale not needed, or already requested
                // Call cannot proceed.
                callViewModel.endCall() 
            }
        }
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize()) { // Use Box for layering video views
            // Remote video (fullscreen)
            AndroidView(
                factory = { remoteSurfaceViewRenderer },
                modifier = Modifier.fillMaxSize()
            )

            // Local video (picture-in-picture)
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .size(width = 100.dp, height = 150.dp) // Example PiP size
                    .fillMaxWidth() // To ensure it respects the size modifier within Box
            ) {
                AndroidView(
                    factory = { localSurfaceViewRenderer },
                    modifier = Modifier.fillMaxSize() // Fill the Box
                )
            }


            // Call controls and status overlay
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom // Controls at the bottom
            ) {
                // Display call status (simplified)
                val statusText = when (val state = callUiState) {
                    is CallUiState.Idle -> "Idle..."
                    is CallUiState.Calling -> state.message
                    is CallUiState.Ringing -> state.message
                    is CallUiState.Active -> "Status: ${state.callStatusMessage}"
                    is CallUiState.Error -> "Error: ${state.message}"
                }
                Text(statusText, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onPrimary)
                Spacer(modifier = Modifier.height(20.dp))

                if (!permissionsState.allPermissionsGranted) {
                    Text(
                        "Audio and Camera permissions are required for video calls. Please grant them.",
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    Button(onClick = { permissionsState.launchMultiplePermissionRequest() }) {
                        Text("Grant Permissions")
                    }
                }

                if (callUiState !is CallUiState.Idle && (callUiState as? CallUiState.Error)?.message?.contains("User not authenticated") != true) {
                     Button(
                        onClick = {
                            callViewModel.endCall()
                            // Consider navigation after state changes to Idle
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("End Call")
                    }
                }
            }
        }
        // Navigate back if call is truly idle (e.g., after ending)
        LaunchedEffect(callUiState, navController.currentDestination?.route) {
            if (callUiState is CallUiState.Idle && navController.currentDestination?.route?.startsWith("call_screen") == true) {
                 // Check if the call was previously active or in a state that implies it ended.
                 // This simple check might pop back prematurely if CallScreen is entered and CallViewModel is still Idle before starting.
                 // A more robust check might involve remembering if a call was active.
                 // For now, if it's idle and we are on call_screen, assume call ended or failed to start.
                 navController.popBackStack()
            }
        }
    }
}
