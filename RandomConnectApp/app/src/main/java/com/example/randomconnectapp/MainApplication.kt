package com.example.randomconnectapp

import android.app.Application
import io.agora.rtc2.RtcEngine
import io.agora.rtc2.RtcEngineConfig

class MainApplication : Application() {

    var rtcEngine: RtcEngine? = null
        private set // Make it accessible but not settable from outside

    // Replace with your actual Agora App ID
    private val AGORA_APP_ID = "YOUR_AGORA_APP_ID" 

    override fun onCreate() {
        super.onCreate()
        try {
            val config = RtcEngineConfig()
            config.mContext = applicationContext
            config.mAppId = AGORA_APP_ID
            // Optional: config.mEventHandler = // a global event handler if needed early
            // Optional: config.mLogConfig = // custom log configuration
            
            rtcEngine = RtcEngine.create(config)
            // RtcEngine.setLogFile("/sdcard/agora_rtc.log") // Example log file setting
            
            // Optional: Enable video module (usually enabled by default)
            // rtcEngine?.enableVideo()

            // You might want to store the RtcEngine instance in a globally accessible way
            // or provide a method to access it from other parts of the app.
            // For this task, making it a public property of MainApplication is fine.

        } catch (e: Exception) {
            e.printStackTrace()
            // Handle initialization error
        }
    }

    override fun onTerminate() {
        super.onTerminate()
        RtcEngine.destroy() // Clean up Agora Engine when application terminates
        rtcEngine = null
    }
}
