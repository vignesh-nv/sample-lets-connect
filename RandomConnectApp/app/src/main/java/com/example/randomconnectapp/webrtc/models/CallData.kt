package com.example.randomconnectapp.webrtc.models

// Represents the main document for a call in Firestore
data class CallData(
    val callId: String = "",
    val callerId: String = "",
    val calleeId: String = "",
    var offerSdp: SdpModel? = null,
    var answerSdp: SdpModel? = null,
    var status: String = CallStatus.RINGING.name // e.g., "ringing", "connected", "declined", "ended"
)

enum class CallStatus {
    RINGING,
    CONNECTING, // Added for clarity during setup
    CONNECTED,
    DECLINED,
    ENDED,
    ERROR
}
