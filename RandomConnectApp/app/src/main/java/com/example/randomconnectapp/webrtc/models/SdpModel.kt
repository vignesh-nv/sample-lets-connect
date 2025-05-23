package com.example.randomconnectapp.webrtc.models

// Represents an SDP (Session Description Protocol) message for Firestore
data class SdpModel(
    val type: String = "",       // "offer" or "answer"
    val description: String = ""
)
