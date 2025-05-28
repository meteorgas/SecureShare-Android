package com.mazeppa.secureshare.data.p2p

import android.content.Context
import android.util.Log
import com.mazeppa.secureshare.data.p2p.WebRtcManager
import org.webrtc.DataChannel
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.SessionDescription
import java.nio.ByteBuffer

class ReceiverSession(
    private val context: Context,
    private val signalingClient: SignalingClient,
    private val peerId: String,
    private val onConnected: () -> Unit,
    private val onDataReceived: (ByteBuffer) -> Unit
) {
    private lateinit var peerConnection: PeerConnection
    private lateinit var factory: PeerConnectionFactory
    private val iceCandidates = mutableListOf<IceCandidate>()

    fun start() {
        WebRtcManager.initialize(context)
        factory = WebRtcManager.getFactory() ?: return

        peerConnection = factory.createPeerConnection(
            WebRtcManager.iceServers,
            object : PeerConnection.Observer {
                override fun onIceCandidate(candidate: IceCandidate) {
                    signalingClient.sendIceCandidate(peerId, candidate)
                }

                override fun onDataChannel(channel: DataChannel) {
                    channel.registerObserver(object : DataChannel.Observer {
                        override fun onMessage(buffer: DataChannel.Buffer) {
                            onDataReceived(buffer.data)
                        }

                        override fun onStateChange() {
                            if (channel.state() == DataChannel.State.OPEN) {
                                onConnected()
                            }
                        }

                        override fun onBufferedAmountChange(p0: Long) {}
                    })
                }

                override fun onSignalingChange(p0: PeerConnection.SignalingState?) {}
                override fun onIceConnectionChange(p0: PeerConnection.IceConnectionState?) {}
                override fun onIceConnectionReceivingChange(p0: Boolean) {}
                override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) {}
                override fun onIceCandidatesRemoved(p0: Array<out IceCandidate?>?) {}
                override fun onAddStream(p0: MediaStream?) {}
                override fun onRemoveStream(p0: MediaStream?) {}
                override fun onRenegotiationNeeded() {}
            }
        ) ?: return

        listenForOffer()
    }

    private fun listenForOffer() {
        signalingClient.onOfferReceived { sdp ->
            val offer = SessionDescription(SessionDescription.Type.OFFER, sdp)
            peerConnection.setRemoteDescription(
                object : SdpObserverAdapter() {
                    override fun onSetSuccess() {
                        createAndSendAnswer()
                    }
                },
                offer
            )
        }

        signalingClient.onIceCandidateReceived { candidate ->
            peerConnection.addIceCandidate(candidate)
        }
    }

    private fun createAndSendAnswer() {
        peerConnection.createAnswer(object : SdpObserverAdapter() {
            override fun onCreateSuccess(desc: SessionDescription?) {
                peerConnection.setLocalDescription(object : SdpObserverAdapter() {
                    override fun onSetSuccess() {
                        desc?.let { signalingClient.sendAnswer(peerId, it) }
                    }
                }, desc)
            }
        }, MediaConstraints())
    }

    fun connectAsReceiver(pin: String, signalingClient: SignalingClient, webRtcManager: WebRtcManager) {
        signalingClient.lookupPin(pin) { success, peerId, errorMessage ->
            if (!success || peerId == null) {
                Log.e("ReceiverSession", "PIN lookup failed: $errorMessage")
                return@lookupPin
            }

            val peerConnection = webRtcManager.createPeerConnection { candidate ->
                signalingClient.sendIceCandidate(peerId, candidate)
            }

            signalingClient.onOfferReceived { sdp ->
                val offer = SessionDescription(SessionDescription.Type.OFFER, sdp)
                peerConnection.setRemoteDescription(object : SdpObserverAdapter() {
                    override fun onSetSuccess() {
                        peerConnection.createAnswer(object : SdpObserverAdapter() {
                            override fun onCreateSuccess(answer: SessionDescription?) {
                                peerConnection.setLocalDescription(SdpObserverAdapter(), answer)
                                answer?.let { signalingClient.sendAnswer(peerId, it) }
                            }
                        }, MediaConstraints())
                    }
                }, offer)
            }

            signalingClient.onIceCandidateReceived { candidate ->
                peerConnection.addIceCandidate(candidate)
            }
        }
    }
}