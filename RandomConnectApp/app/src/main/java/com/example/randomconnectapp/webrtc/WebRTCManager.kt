package com.example.randomconnectapp.webrtc

import android.content.Context
import android.util.Log
import org.webrtc.*
import org.webrtc.PeerConnectionFactory.InitializationOptions

class WebRTCManager(
    private val applicationContext: Context, // Renamed for clarity
    private val eglBaseContext: EglBase.Context 
) {

    companion object {
        private const val TAG = "WebRTCManager"
        private const val AUDIO_TRACK_ID = "ARDAMSa0"
        private const val VIDEO_TRACK_ID = "ARDAMSv0" // Video track ID
        private const val VIDEO_FPS = 30
        private const val VIDEO_WIDTH = 1280 // Or 640
        private const val VIDEO_HEIGHT = 720 // Or 480

        // Audio constraints
        private const val AUDIO_ECHO_CANCELLATION_CONSTRAINT = "googEchoCancellation"
        private const val AUDIO_AUTO_GAIN_CONTROL_CONSTRAINT = "googAutoGainControl"
        private const val AUDIO_HIGH_PASS_FILTER_CONSTRAINT = "googHighpassFilter"
        private const val AUDIO_NOISE_SUPPRESSION_CONSTRAINT = "googNoiseSuppression"
    }

    private val peerConnectionFactory: PeerConnectionFactory by lazy {
        initializePeerConnectionFactory()
        buildPeerConnectionFactory()
    }

    private var audioSource: AudioSource? = null
    private var localAudioTrack: AudioTrack? = null

    // Video related fields
    private var videoCapturer: VideoCapturer? = null
    private var videoSource: VideoSource? = null
    private var localVideoTrack: VideoTrack? = null
    private var surfaceTextureHelper: SurfaceTextureHelper? = null


    private fun initializePeerConnectionFactory() {
        val initializationOptions = InitializationOptions.builder(applicationContext)
            .setEnableInternalTracer(true)
            .setFieldTrials("WebRTC-H264HighProfile/Enabled/") 
            .createInitializationOptions()
        PeerConnectionFactory.initialize(initializationOptions)
        Log.d(TAG, "PeerConnectionFactory initialized.")
    }

    private fun buildPeerConnectionFactory(): PeerConnectionFactory {
        return PeerConnectionFactory.builder()
            .setOptions(PeerConnectionFactory.Options().apply {
                // disableEncryption = true // For testing if needed
                // disableNetworkMonitor = true
            })
            .setAudioDeviceModule(JavaAudioDeviceModule.builder(applicationContext)
                .setUseHardwareAcousticEchoCanceler(true) // Attempt to use hardware AEC
                .setUseHardwareNoiseSuppressor(true)   // Attempt to use hardware NS
                .createAudioDeviceModule())
            // Video encoder/decoder factories are crucial for video
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(eglBaseContext, true, true))
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(eglBaseContext))
            .createPeerConnectionFactory()
    }

    fun createLocalAudioTrack(): AudioTrack? {
        if (localAudioTrack == null) {
            val audioConstraints = MediaConstraints()
            audioConstraints.mandatory.add(MediaConstraints.KeyValuePair(AUDIO_ECHO_CANCELLATION_CONSTRAINT, "true"))
            audioConstraints.mandatory.add(MediaConstraints.KeyValuePair(AUDIO_AUTO_GAIN_CONTROL_CONSTRAINT, "true"))
            audioConstraints.mandatory.add(MediaConstraints.KeyValuePair(AUDIO_HIGH_PASS_FILTER_CONSTRAINT, "true"))
            audioConstraints.mandatory.add(MediaConstraints.KeyValuePair(AUDIO_NOISE_SUPPRESSION_CONSTRAINT, "true"))

            audioSource = peerConnectionFactory.createAudioSource(audioConstraints)
            localAudioTrack = peerConnectionFactory.createAudioTrack(AUDIO_TRACK_ID, audioSource)
            Log.d(TAG, "Local audio track created.")
        }
        return localAudioTrack
    }
    
    // Create a video capturer (camera)
    private fun createVideoCapturer(context: Context): VideoCapturer? {
        val enumerator = Camera2Enumerator(context)
        val deviceNames = enumerator.deviceNames

        // Try to find front facing camera
        for (deviceName in deviceNames) {
            if (enumerator.isFrontFacing(deviceName)) {
                Log.d(TAG, "Found front facing camera: $deviceName")
                return enumerator.createCapturer(deviceName, null)
            }
        }
        // If no front camera, try back facing camera
        for (deviceName in deviceNames) {
            if (enumerator.isBackFacing(deviceName)) {
                Log.d(TAG, "Found back facing camera: $deviceName")
                return enumerator.createCapturer(deviceName, null)
            }
        }
        Log.w(TAG, "No suitable camera found.")
        return null
    }

    // Create a local video track
    fun createLocalVideoTrack(): VideoTrack? {
        if (localVideoTrack == null) {
            videoCapturer = createVideoCapturer(applicationContext)
            if (videoCapturer == null) {
                Log.e(TAG, "Failed to create VideoCapturer.")
                return null
            }
            // SurfaceTextureHelper is needed for Camera2Capturer and some other capturers
            surfaceTextureHelper = SurfaceTextureHelper.create("VideoCapturerThread", eglBaseContext)
            videoSource = peerConnectionFactory.createVideoSource(videoCapturer!!.isScreencast)
            
            videoCapturer?.initialize(surfaceTextureHelper, applicationContext, videoSource?.capturerObserver)
            videoCapturer?.startCapture(VIDEO_WIDTH, VIDEO_HEIGHT, VIDEO_FPS)
            
            localVideoTrack = peerConnectionFactory.createVideoTrack(VIDEO_TRACK_ID, videoSource)
            Log.d(TAG, "Local video track created.")
        }
        return localVideoTrack
    }


    fun createPeerConnection(
        observer: PeerConnection.Observer,
        iceServers: List<PeerConnection.IceServer> = listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer()
        )
    ): PeerConnection? {
        val rtcConfig = PeerConnection.RTCConfiguration(iceServers)
        // Update media constraints for offer/answer to include video
        // This is generally handled by adding tracks before creating offer/answer
        return peerConnectionFactory.createPeerConnection(rtcConfig, observer)?.also {
            Log.d(TAG, "PeerConnection created.")
        }
    }
    
    open class SimpleSdpObserver : SdpObserver {
        override fun onCreateSuccess(sdp: SessionDescription?) {
            Log.d(TAG, "SdpObserver: onCreateSuccess")
        }
        override fun onSetSuccess() {
            Log.d(TAG, "SdpObserver: onSetSuccess")
        }
        override fun onCreateFailure(error: String?) {
            Log.e(TAG, "SdpObserver: onCreateFailure: $error")
        }
        override fun onSetFailure(error: String?) {
            Log.e(TAG, "SdpObserver: onSetFailure: $error")
        }
    }

    fun createOffer(peerConnection: PeerConnection, sdpObserver: SdpObserver) {
        val mediaConstraints = MediaConstraints()
        mediaConstraints.mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
        mediaConstraints.mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true")) // Offer to receive video

        peerConnection.createOffer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(sdp: SessionDescription?) {
                sdpObserver.onCreateSuccess(sdp) 
                sdp?.let {
                    Log.d(TAG, "Offer created successfully. Setting local description.")
                    peerConnection.setLocalDescription(object : SimpleSdpObserver() {
                        override fun onSetSuccess() {
                            super.onSetSuccess()
                            sdpObserver.onSetSuccess() 
                        }
                        override fun onSetFailure(error: String?) {
                            super.onSetFailure(error)
                            sdpObserver.onSetFailure(error) 
                        }
                    }, it)
                } ?: run {
                     sdpObserver.onCreateFailure("SDP was null")
                }
            }
            override fun onCreateFailure(error: String?) {
                super.onCreateFailure(error)
                sdpObserver.onCreateFailure(error) 
            }
        }, mediaConstraints)
    }

    fun createAnswer(peerConnection: PeerConnection, sdpObserver: SdpObserver) {
        val mediaConstraints = MediaConstraints()
        mediaConstraints.mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
        mediaConstraints.mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true")) // Agree to receive video

        peerConnection.createAnswer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(sdp: SessionDescription?) {
                sdpObserver.onCreateSuccess(sdp) 
                sdp?.let {
                    Log.d(TAG, "Answer created successfully. Setting local description.")
                    peerConnection.setLocalDescription(object : SimpleSdpObserver() {
                         override fun onSetSuccess() {
                            super.onSetSuccess()
                            sdpObserver.onSetSuccess()
                        }
                        override fun onSetFailure(error: String?) {
                            super.onSetFailure(error)
                            sdpObserver.onSetFailure(error)
                        }
                    }, it)
                } ?: run {
                    sdpObserver.onCreateFailure("SDP was null")
                }
            }
             override fun onCreateFailure(error: String?) {
                super.onCreateFailure(error)
                sdpObserver.onCreateFailure(error)
            }
        }, mediaConstraints)
    }

    fun setRemoteDescription(peerConnection: PeerConnection, sdp: SessionDescription, sdpObserver: SdpObserver) {
        Log.d(TAG, "Setting remote description of type: ${sdp.type}")
        peerConnection.setRemoteDescription(sdpObserver, sdp)
    }

    fun addIceCandidate(peerConnection: PeerConnection, candidate: IceCandidate?) {
        if (candidate == null) {
            Log.w(TAG, "Attempted to add null ICE candidate.")
            return
        }
        peerConnection.addIceCandidate(candidate)
        Log.d(TAG, "ICE candidate added: ${candidate.sdp}")
    }

    // SurfaceViewRenderer initialization - usually done in UI but EGL context might be needed
    fun initSurfaceViewRenderer(surfaceView: SurfaceViewRenderer) {
        surfaceView.init(eglBaseContext, null)
        Log.d(TAG, "SurfaceViewRenderer initialized.")
    }
    
    // Helper to attach a VideoTrack to a SurfaceViewRenderer
    // Note: VideoTrack itself has addSink/removeSink. This is just a convenience.
    // The UI should directly call track.addSink(surfaceViewRenderer)
    // This manager doesn't need to hold references to SurfaceViewRenderers.

    fun dispose() {
        Log.d(TAG, "Disposing WebRTCManager resources.")
        // Dispose video resources first
        localVideoTrack?.dispose()
        localVideoTrack = null
        videoCapturer?.stopCapture()
        videoCapturer?.dispose()
        videoCapturer = null
        videoSource?.dispose()
        videoSource = null
        surfaceTextureHelper?.dispose()
        surfaceTextureHelper = null
        
        // Dispose audio resources
        localAudioTrack?.dispose() // Already handled if audioSource disposes it
        localAudioTrack = null
        audioSource?.dispose()
        audioSource = null
        
        // Dispose factory
        // Note: Active PeerConnections should be closed by their users (e.g., CallViewModel) before disposing factory
        peerConnectionFactory.dispose()
        Log.d(TAG, "WebRTCManager disposed.")
    }
}
