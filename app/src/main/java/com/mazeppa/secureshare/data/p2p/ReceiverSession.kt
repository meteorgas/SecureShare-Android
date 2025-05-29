package com.mazeppa.secureshare.data.p2p

import android.content.Context
import android.net.Uri
import android.util.Log
import org.webrtc.DataChannel
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.RtpReceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import java.io.IOException
import java.io.OutputStream

class ReceiverSession(
    private val context: Context,
    private val signalingClient: WebSocketSignalingClient,
    private val remotePeerId: String,    // sender’s peerId (from lookup-pin)
    private val targetFileUri: Uri,      // where to save incoming bytes
    private val onProgress: (bytesReceived: Long) -> Unit,
    private val onComplete: () -> Unit,
    private val onError: (Exception) -> Unit
) {
    private val TAG = "ReceiverSession"
    private var fileOut: OutputStream? = null
    private var peerConnection: PeerConnection? = null
    private var bytesReceived = 0L

    /** Start the WebSocket & wait for the offer to arrive. */
    fun start() {
        peerConnection = WebRtcManager.getFactory()?.createPeerConnection(
            PeerConnection.RTCConfiguration(WebRtcManager.iceServers),
            object : PeerConnection.Observer {
                override fun onIceConnectionReceivingChange(p0: Boolean) {
                    Log.i(TAG, "ICE connection receiving change: $p0")
                }

                override fun onSignalingChange(newState: PeerConnection.SignalingState) {
                    Log.d(TAG, "onSignalingChange → $newState")
                }

                override fun onIceGatheringChange(newState: PeerConnection.IceGatheringState) {
                    Log.d(TAG, "onIceGatheringChange → $newState")
                }

                override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState) {
                    Log.d(TAG, "onIceConnectionChange → $newState")
                }

                override fun onIceCandidatesRemoved(candidates: Array<IceCandidate>) {}
                override fun onAddStream(stream: MediaStream) {}
                override fun onRemoveStream(stream: MediaStream) {}
                override fun onAddTrack(receiver: RtpReceiver, streams: Array<out MediaStream>) {}
                override fun onRenegotiationNeeded() {}
                override fun onIceCandidate(candidate: IceCandidate) {
                    // forward our ICE to the sender
                    signalingClient.sendIceCandidate(candidate, remotePeerId)
                }

                override fun onDataChannel(dc: DataChannel) {
                    Log.d(TAG, "Incoming DataChannel")
                    dc.registerObserver(object : DataChannel.Observer {
                        override fun onBufferedAmountChange(prev: Long) {}

                        override fun onStateChange() {
                            Log.d(TAG, "DataChannel state → ${dc.state()}")
                            when (dc.state()) {
                                DataChannel.State.OPEN -> {
                                    Log.d(TAG, "DataChannel is OPEN — starting file I/O")
                                    try {
                                        fileOut = context.contentResolver
                                            .openOutputStream(targetFileUri)
                                            ?: throw IOException("Cannot open $targetFileUri")
                                    } catch (e: Exception) {
                                        onError(e)
                                    }
                                }

                                DataChannel.State.CLOSED -> {
                                    Log.d(TAG, "DC CLOSED – finishing write")
                                    fileOut?.close()
                                    onComplete()
                                }

                                else -> {}
                            }
                        }

                        override fun onMessage(buffer: DataChannel.Buffer) {
                            try {
                                val bytes = ByteArray(buffer.data.remaining())
                                buffer.data.get(bytes)
                                fileOut?.write(bytes)
                                bytesReceived += bytes.size
                                onProgress(bytesReceived)
                            } catch (e: Exception) {
                                onError(e)
                            }
                        }
                    })
                }
            }
        ) ?: throw IllegalStateException("Failed to create PeerConnection")
    }

    /** Call when your WebSocket client hands you a remote OFFER. */
    fun onRemoteOffer(offer: SessionDescription) {
        peerConnection?.setRemoteDescription(object : SdpObserver {
            override fun onSetSuccess() {
                Log.d(TAG, "Remote SDP set → creating ANSWER")
                // Generate our answer
                peerConnection?.createAnswer(object : SdpObserver {
                    override fun onCreateSuccess(answer: SessionDescription) {
                        peerConnection?.setLocalDescription(object : SdpObserver {
                            override fun onSetSuccess() {
                                signalingClient.sendAnswer(answer, remotePeerId)
                            }

                            override fun onSetFailure(err: String) = onError(Exception(err))
                            override fun onCreateSuccess(p0: SessionDescription?) {}
                            override fun onCreateFailure(p0: String?) {}
                        }, answer)
                    }

                    override fun onCreateFailure(err: String) = onError(Exception(err))
                    override fun onSetSuccess() {}
                    override fun onSetFailure(err: String) = onError(Exception(err))
                }, MediaConstraints())
            }

            override fun onSetFailure(err: String) = onError(Exception(err))
            override fun onCreateSuccess(p0: SessionDescription?) {}
            override fun onCreateFailure(p0: String?) {}
        }, offer)
    }

    /** Call when you get an ICE‐candidate from the sender. */
    fun onRemoteIceCandidate(candidate: IceCandidate) {
        peerConnection?.addIceCandidate(candidate)
    }

    /** Tear down when you’re done. */
    fun close() {
        peerConnection?.close()
        fileOut?.close()
        signalingClient.close()
    }
}