package com.mazeppa.secureshare.data.p2p

import android.content.Context
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory

object WebRtcManager {

    private var peerConnectionFactory: PeerConnectionFactory? = null

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

    fun getFactory(): PeerConnectionFactory? = peerConnectionFactory
}