package com.mazeppa.secureshare.data.p2p

import org.webrtc.MediaConstraints
import org.webrtc.PeerConnection
import org.webrtc.SessionDescription

object SenderSession {
    fun createOffer(
        peerConnection: PeerConnection,
        onOfferCreated: (SessionDescription) -> Unit
    ) {
        peerConnection.createOffer(object : SdpObserverAdapter() {
            override fun onCreateSuccess(desc: SessionDescription?) {
                desc?.let {
                    peerConnection.setLocalDescription(object : SdpObserverAdapter() {}, it)
                    onOfferCreated(it)
                }
            }
        }, MediaConstraints())
    }
}