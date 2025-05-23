package com.example.randomconnectapp.model

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

data class AgoraCallInfo(
    val channelName: String = "", // Should be unique for the call session
    val callerId: String = "",
    val callerName: String? = null, // Optional: For display
    val calleeId: String = "",
    var status: String = CallStatus.UNKNOWN.name,
    @ServerTimestamp val timestamp: Date? = null, // Firestore server-side timestamp
    val token: String? = null // If generating Agora tokens per call
) {
    // No-argument constructor for Firestore deserialization
    constructor() : this("", "", null, "", CallStatus.UNKNOWN.name, null, null)
}
