package com.example.randomconnectapp.agora

import android.content.Context
import android.util.Log
import android.view.SurfaceView
import io.agora.rtc2.ChannelMediaOptions
import io.agora.rtc2.Constants
import io.agora.rtc2.IRtcEngineEventHandler
import io.agora.rtc2.RtcEngine
import io.agora.rtc2.video.VideoCanvas

interface AgoraManagerListener {
    fun onUserJoined(uid: Int)
    fun onUserOffline(uid: Int, reason: Int)
    fun onRemoteVideoStateChanged(uid: Int, state: Int, reason: Int, elapsed: Int)
    fun onJoinChannelSuccess(channel: String, uid: Int, elapsed: Int)
    fun onLeaveChannelSuccess()
    fun onErrorOccurred(err: Int, msg: String) // Added msg for better error reporting
}

class AgoraManager(
    private val context: Context,
    private val rtcEngine: RtcEngine // Pass RtcEngine instance
) {
    companion object {
        private const val TAG = "AgoraManager"
    }

    private var listener: AgoraManagerListener? = null
    private var currentChannel: String? = null

    private val eventHandler = object : IRtcEngineEventHandler() {
        override fun onJoinChannelSuccess(channel: String?, uid: Int, elapsed: Int) {
            Log.i(TAG, "onJoinChannelSuccess: channel=$channel, uid=$uid, elapsed=$elapsed")
            currentChannel = channel
            listener?.onJoinChannelSuccess(channel ?: "", uid, elapsed)
        }

        override fun onLeaveChannel(stats: RtcStats?) {
            Log.i(TAG, "onLeaveChannel: stats=$stats")
            currentChannel = null
            listener?.onLeaveChannelSuccess()
        }

        override fun onUserJoined(uid: Int, elapsed: Int) {
            Log.i(TAG, "onUserJoined: uid=$uid, elapsed=$elapsed")
            listener?.onUserJoined(uid)
        }

        override fun onUserOffline(uid: Int, reason: Int) {
            Log.i(TAG, "onUserOffline: uid=$uid, reason=$reason")
            listener?.onUserOffline(uid, reason)
        }

        override fun onRemoteVideoStateChanged(uid: Int, state: Int, reason: Int, elapsed: Int) {
            Log.i(TAG, "onRemoteVideoStateChanged: uid=$uid, state=$state, reason=$reason")
            // Example: state can be REMOTE_VIDEO_STATE_STARTING, REMOTE_VIDEO_STATE_PLAYING, etc.
            listener?.onRemoteVideoStateChanged(uid, state, reason, elapsed)
        }
        
        override fun onError(err: Int) {
            super.onError(err)
            Log.e(TAG, "Agora RTC Error: $err, message: ${RtcEngine.getErrorDescription(err)}")
            listener?.onErrorOccurred(err, RtcEngine.getErrorDescription(err) ?: "Unknown Agora Error")
        }
    }

    init {
        rtcEngine.addHandler(eventHandler) // Add event handler during init
    }

    fun setListener(listener: AgoraManagerListener) {
        this.listener = listener
    }

    fun joinChannel(channelName: String, localUid: Int, token: String? = null) {
        Log.d(TAG, "Attempting to join channel: $channelName with uid: $localUid")
        // Enable video module before joining channel
        rtcEngine.enableVideo()
        // Enable audio module (usually enabled by default)
        rtcEngine.enableAudio()

        val options = ChannelMediaOptions()
        options.channelProfile = Constants.CHANNEL_PROFILE_LIVE_BROADCASTING // Or CHANNEL_PROFILE_COMMUNICATION
        options.clientRoleType = Constants.CLIENT_ROLE_BROADCASTER // Set role, can be AUDIENCE too

        // For video calls, it's common to publish both audio and video
        options.publishMicrophoneTrack = true
        options.publishCameraTrack = true
        // Auto-subscribe to audio and video (can be configured based on needs)
        options.autoSubscribeAudio = true
        options.autoSubscribeVideo = true


        // Token can be null for testing if your Agora project doesn't require it.
        // For production, always use a token.
        val result = rtcEngine.joinChannel(token, channelName, localUid, options)
        if (result == 0) {
            Log.i(TAG, "Join channel call success.")
        } else {
            Log.e(TAG, "Join channel call failed. Error code: $result")
            listener?.onErrorOccurred(result, "Failed to join channel: ${RtcEngine.getErrorDescription(result)}")
        }
    }

    fun leaveChannel() {
        if (currentChannel == null) {
            Log.w(TAG, "Not in any channel, cannot leave.")
            return
        }
        Log.d(TAG, "Leaving channel: $currentChannel")
        val result = rtcEngine.leaveChannel()
        if (result == 0) {
            Log.i(TAG, "Leave channel call success.")
        } else {
            Log.e(TAG, "Leave channel call failed. Error code: $result")
        }
        // Reset currentChannel in onLeaveChannel callback
    }

    fun setupLocalVideo(surfaceView: SurfaceView) {
        Log.d(TAG, "Setting up local video.")
        // Ensure video is enabled
        rtcEngine.enableVideo()
        // Start local preview before or after joining channel
        // rtcEngine.startPreview() // Often called before joinChannel if you want preview immediately

        val canvas = VideoCanvas(surfaceView, VideoCanvas.RENDER_MODE_HIDDEN, 0)
        // UID 0 means local user
        val result = rtcEngine.setupLocalVideo(canvas) 
        if (result == 0) {
            Log.i(TAG, "Local video setup success.")
        } else {
            Log.e(TAG, "Local video setup failed. Error code: $result")
        }
    }

    fun setupRemoteVideo(surfaceView: SurfaceView, remoteUid: Int) {
        Log.d(TAG, "Setting up remote video for uid: $remoteUid")
        val canvas = VideoCanvas(surfaceView, VideoCanvas.RENDER_MODE_HIDDEN, remoteUid)
        val result = rtcEngine.setupRemoteVideo(canvas)
         if (result == 0) {
            Log.i(TAG, "Remote video setup success for uid $remoteUid.")
        } else {
            Log.e(TAG, "Remote video setup failed for uid $remoteUid. Error code: $result")
        }
    }

    fun muteLocalAudioStream(muted: Boolean) {
        Log.d(TAG, "Muting local audio stream: $muted")
        val result = rtcEngine.muteLocalAudioStream(muted)
        if (result != 0) {
            Log.e(TAG, "Mute local audio stream failed. Error code: $result")
        }
    }

    fun muteLocalVideoStream(muted: Boolean) {
        Log.d(TAG, "Muting local video stream: $muted")
        val result = rtcEngine.muteLocalVideoStream(muted)
         if (result != 0) {
            Log.e(TAG, "Mute local video stream failed. Error code: $result")
        }
        // If unmuting, you might also need to re-enable camera or ensure it's publishing
        // rtcEngine.enableLocalVideo(!muted) // This might be needed depending on Agora's behavior
    }
    
    // Call this when AgoraManager is no longer needed to remove the handler.
    // The RtcEngine itself is destroyed in MainApplication.onTerminate()
    fun destroy() {
        Log.d(TAG, "Destroying AgoraManager, removing event handler.")
        rtcEngine.removeHandler(eventHandler)
        // If local preview was started explicitly and not stopped by leaveChannel
        // rtcEngine.stopPreview() 
        currentChannel = null 
        listener = null
    }
}
