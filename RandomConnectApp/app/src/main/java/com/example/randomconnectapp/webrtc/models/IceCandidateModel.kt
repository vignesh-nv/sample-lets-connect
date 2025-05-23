package com.example.randomconnectapp.webrtc.models

// Represents an ICE (Interactive Connectivity Establishment) candidate for Firestore
data class IceCandidateModel(
    val sdpMid: String = "",
    val sdpMLineIndex: Int = 0,
    val sdp: String = "",
    val serverUrl: String = "" // Optional, for debugging or specific TURN server info
)
