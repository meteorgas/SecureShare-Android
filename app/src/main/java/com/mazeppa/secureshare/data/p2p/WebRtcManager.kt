package com.mazeppa.secureshare.data.p2p

import android.content.Context
import android.util.Log
import org.webrtc.DataChannel
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import java.util.concurrent.Executors

class WebRtcManager(private val context: Context) {

    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var dataChannel: DataChannel? = null
    private val executor = Executors.newSingleThreadExecutor()

    private val iceServers = listOf(
        PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer()
    )

    fun initialize() {
        if (peerConnectionFactory != null) return

        val initializationOptions = PeerConnectionFactory.InitializationOptions.builder(context)
            .setEnableInternalTracer(true)
            .createInitializationOptions()
        PeerConnectionFactory.initialize(initializationOptions)

        val options = PeerConnectionFactory.Options()
        peerConnectionFactory = PeerConnectionFactory.builder()
            .setOptions(options)
            .createPeerConnectionFactory()
    }

    fun createPeerConnection(onIceCandidate: (IceCandidate) -> Unit): PeerConnection {
        val rtcConfig = PeerConnection.RTCConfiguration(iceServers)
        rtcConfig.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN

        val factory = peerConnectionFactory ?: throw IllegalStateException("WebRTC not initialized")
        peerConnection = factory.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
            override fun onIceCandidate(candidate: IceCandidate) = onIceCandidate(candidate)
            override fun onDataChannel(dc: DataChannel) {
                dataChannel = dc
            }

            override fun onSignalingChange(newState: PeerConnection.SignalingState?) {}
            override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState?) {}
            override fun onIceConnectionReceivingChange(receiving: Boolean) {}
            override fun onIceGatheringChange(newState: PeerConnection.IceGatheringState?) {}
            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate?>?) {}
            override fun onAddStream(stream: MediaStream?) {}
            override fun onRemoveStream(stream: MediaStream?) {}
            override fun onRenegotiationNeeded() {}
        }) ?: throw IllegalStateException("Failed to create PeerConnection")

        return peerConnection!!
    }

    fun createOffer(onOfferCreated: (SessionDescription) -> Unit) {
        val pc = peerConnection ?: throw IllegalStateException("PeerConnection is null")
        pc.createOffer(object : SdpObserver {
            override fun onCreateSuccess(desc: SessionDescription) {
                pc.setLocalDescription(object : SdpObserver {
                    override fun onSetSuccess() = onOfferCreated(desc)
                    override fun onSetFailure(error: String?) {}
                    override fun onCreateSuccess(p0: SessionDescription?) {}
                    override fun onCreateFailure(p0: String?) {}
                }, desc)
            }

            override fun onCreateFailure(error: String?) {
                Log.e("WebRTC", "Offer creation failed: $error")
            }

            override fun onSetSuccess() {}
            override fun onSetFailure(error: String?) {}
        }, MediaConstraints())
    }

    fun createDataChannel(): DataChannel {
        val pc = peerConnection ?: throw IllegalStateException("PeerConnection not initialized")
        val init = DataChannel.Init()
        dataChannel = pc.createDataChannel("fileChannel", init)
        return dataChannel!!
    }

    fun getFactory(): PeerConnectionFactory {
        return peerConnectionFactory ?: throw IllegalStateException("WebRTC not initialized")
    }
}