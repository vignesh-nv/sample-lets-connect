package com.example.randomconnectapp.model

data class User(
    val uid: String = "",
    val phoneNumber: String = "",
    val gender: String = "",
    val language: String = "",
    val age: Int = 0,
    val isProfileComplete: Boolean = false, // To track if user has submitted details
    val isOnline: Boolean = false // To track online status
)
