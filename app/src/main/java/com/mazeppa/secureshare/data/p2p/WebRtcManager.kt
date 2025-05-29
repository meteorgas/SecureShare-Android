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

object WebRtcManager {

    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var dataChannel: DataChannel? = null

    internal val iceServers = listOf(
        PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
        PeerConnection.IceServer.builder("turn:YOUR_TURN_SERVER:3478")
            .setUsername("turnuser")
            .setPassword("turnpass")
            .createIceServer()
    )

    fun initialize(context: Context) {
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
        val config = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        }

        val factory = peerConnectionFactory ?: throw IllegalStateException("WebRTC not initialized")
        peerConnection = factory.createPeerConnection(config, object : PeerConnection.Observer {
            override fun onIceCandidate(candidate: IceCandidate) = onIceCandidate(candidate)
            override fun onDataChannel(dc: DataChannel) {
                dataChannel = dc
                Log.d("WebRTC", "DataChannel received")
            }

            override fun onSignalingChange(newState: PeerConnection.SignalingState) {
                Log.d("WebRTC", "Signaling changed: $newState")
            }

            override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState) {
                Log.d("WebRTC", "ICE connection changed: $newState")
            }

            override fun onIceConnectionReceivingChange(receiving: Boolean) {}
            override fun onIceGatheringChange(newState: PeerConnection.IceGatheringState) {}
            override fun onIceCandidatesRemoved(candidates: Array<IceCandidate>) {}
            override fun onAddStream(stream: MediaStream) {}
            override fun onRemoveStream(stream: MediaStream) {}
            override fun onRenegotiationNeeded() {}
        }) ?: throw IllegalStateException("Failed to create PeerConnection")

        return peerConnection!!
    }

    fun createOffer(onOfferCreated: (SessionDescription) -> Unit) {
        val pc = requireNotNull(peerConnection) { "PeerConnection is null" }
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

    fun createAnswer(onAnswerCreated: (SessionDescription) -> Unit) {
        val pc = requireNotNull(peerConnection) { "PeerConnection is null" }
        pc.createAnswer(object : SdpObserver {
            override fun onCreateSuccess(desc: SessionDescription) {
                pc.setLocalDescription(object : SdpObserver {
                    override fun onSetSuccess() = onAnswerCreated(desc)
                    override fun onSetFailure(error: String?) {}
                    override fun onCreateSuccess(p0: SessionDescription?) {}
                    override fun onCreateFailure(p0: String?) {}
                }, desc)
            }

            override fun onCreateFailure(error: String?) {
                Log.e("WebRTC", "Answer creation failed: $error")
            }

            override fun onSetSuccess() {}
            override fun onSetFailure(p0: String?) {}
        }, MediaConstraints())
    }

    fun setRemoteDescription(desc: SessionDescription, onSet: () -> Unit = {}) {
        val pc = requireNotNull(peerConnection) { "PeerConnection is null" }
        pc.setRemoteDescription(object : SdpObserver {
            override fun onSetSuccess() = onSet()
            override fun onSetFailure(error: String?) {}
            override fun onCreateSuccess(p0: SessionDescription?) {}
            override fun onCreateFailure(p0: String?) {}
        }, desc)
    }

    fun createDataChannel(): DataChannel {
        val pc = requireNotNull(peerConnection) { "PeerConnection not initialized" }
        val init = DataChannel.Init()
        dataChannel = pc.createDataChannel("fileChannel", init)
        return dataChannel!!
    }

    fun dispose() {
        dataChannel?.close()
        dataChannel = null
        peerConnection?.close()
        peerConnection = null
        peerConnectionFactory?.dispose()
        peerConnectionFactory = null
    }

    fun getFactory(): PeerConnectionFactory? = peerConnectionFactory
}