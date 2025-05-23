package com.example.randomconnectapp.model

enum class CallStatus {
    RINGING, // Call is ringing for the callee
    CONNECTED, // Call is active and connected
    ENDED, // Call was successfully ended by one of the participants
    DECLINED, // Callee declined the call
    MISSED, // Callee did not answer
    CANCELLED, // Caller cancelled the call before it was answered
    UNKNOWN // Initial or error state
}
