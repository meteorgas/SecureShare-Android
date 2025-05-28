package com.mazeppa.secureshare.data.p2p

import android.content.Context
import android.util.Log
import org.webrtc.DataChannel
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.SdpObserver
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

    fun start() {
        WebRtcManager.initialize(context)

        peerConnection = WebRtcManager.createPeerConnection { candidate ->
            signalingClient.sendIceCandidate(peerId, candidate)
        }

        // Register PeerConnection Observer
        val observer = object : PeerConnection.Observer {
            override fun onIceCandidate(candidate: IceCandidate) {
                signalingClient.sendIceCandidate(peerId, candidate)
            }

            override fun onDataChannel(channel: DataChannel) {
                Log.d("ReceiverSession", "DataChannel received.")
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

            override fun onSignalingChange(newState: PeerConnection.SignalingState?) {}
            override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState?) {}
            override fun onIceConnectionReceivingChange(receiving: Boolean) {}
            override fun onIceGatheringChange(newState: PeerConnection.IceGatheringState?) {}
            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>) {}
            override fun onAddStream(stream: MediaStream?) {}
            override fun onRemoveStream(stream: MediaStream?) {}
            override fun onRenegotiationNeeded() {}
        }

        // Re-create the peer connection with the proper observer
        peerConnection.close()
        peerConnection = WebRtcManager.getFactory()!!.createPeerConnection(
            WebRtcManager.iceServers,
            observer
        ) ?: throw IllegalStateException("Failed to create PeerConnection")

        // Handle offer from sender
        signalingClient.onOfferReceived { sdp ->
            val offer = SessionDescription(SessionDescription.Type.OFFER, sdp)
            peerConnection.setRemoteDescription(object : SdpObserver {
                override fun onSetSuccess() {
                    Log.d("ReceiverSession", "Remote offer set. Creating answer...")
                    peerConnection.createAnswer(object : SdpObserver {
                        override fun onCreateSuccess(answer: SessionDescription) {
                            peerConnection.setLocalDescription(object : SdpObserver {
                                override fun onSetSuccess() {
                                    signalingClient.sendAnswer(peerId, answer)
                                }

                                override fun onSetFailure(error: String?) {
                                    Log.e("ReceiverSession", "Failed to set local answer: $error")
                                }

                                override fun onCreateSuccess(p0: SessionDescription?) {}
                                override fun onCreateFailure(p0: String?) {}
                            }, answer)
                        }

                        override fun onCreateFailure(error: String?) {
                            Log.e("ReceiverSession", "Failed to create answer: $error")
                        }

                        override fun onSetSuccess() {}
                        override fun onSetFailure(p0: String?) {}
                    }, MediaConstraints())
                }

                override fun onSetFailure(error: String?) {
                    Log.e("ReceiverSession", "Failed to set remote offer: $error")
                }

                override fun onCreateSuccess(p0: SessionDescription?) {}
                override fun onCreateFailure(p0: String?) {}
            }, offer)
        }

        // Handle ICE candidates from sender
        signalingClient.onIceCandidateReceived { candidate ->
            peerConnection.addIceCandidate(candidate)
        }
    }

    fun dispose() {
        peerConnection.close()
    }
}