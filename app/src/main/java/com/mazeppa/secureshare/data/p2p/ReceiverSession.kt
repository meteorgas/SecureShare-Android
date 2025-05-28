package com.mazeppa.secureshare.data.p2p

import org.webrtc.MediaConstraints
import org.webrtc.PeerConnection
import org.webrtc.SessionDescription

object ReceiverSession {

    fun handleOfferAndCreateAnswer(
        peerConnection: PeerConnection,
        offer: SessionDescription,
        onAnswerCreated: (SessionDescription) -> Unit
    ) {
        peerConnection.setRemoteDescription(object : SdpObserverAdapter() {}, offer)

        peerConnection.createAnswer(object : SdpObserverAdapter() {
            override fun onCreateSuccess(desc: SessionDescription?) {
                desc?.let {
                    peerConnection.setLocalDescription(object : SdpObserverAdapter() {}, it)
                    onAnswerCreated(it)
                }
            }
        }, MediaConstraints())
    }
}