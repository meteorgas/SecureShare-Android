package com.mazeppa.secureshare.data.p2p

import org.webrtc.IceCandidate
import org.webrtc.SessionDescription

interface SignalingClient {
    fun sendOffer(peerId: String, offer: SessionDescription)
    fun sendAnswer(peerId: String, answer: SessionDescription)
    fun sendIceCandidate(peerId: String, candidate: IceCandidate)
    fun onOfferReceived(callback: (String) -> Unit)
    fun onAnswerReceived(callback: (String) -> Unit)
    fun onIceCandidateReceived(callback: (IceCandidate) -> Unit)
}