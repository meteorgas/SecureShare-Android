package com.mazeppa.secureshare.data.p2p

import android.content.Context
import android.net.Uri
import android.util.Log
import org.webrtc.DataChannel
import org.webrtc.PeerConnection
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription

class SenderSession(
    private val context: Context,
    private val selfId: String,
    private val signalingClient: SignalingClient
) {
    private var peerConnection: PeerConnection? = null
    private var dataChannel: DataChannel? = null

    fun startSession(pin: String, fileUri: Uri) {
        signalingClient.lookupPin(pin) { success, peerId, error ->
            if (!success || peerId == null) {
                Log.e("SenderSession", "PIN lookup failed: $error")
                return@lookupPin
            }

            Log.i("SenderSession", "PIN resolved to peerId: $peerId")

            peerConnection = WebRtcManager.createPeerConnection { candidate ->
                signalingClient.sendIceCandidate(peerId, candidate)
            }

            // Setup DataChannel early
            val init = DataChannel.Init()
            dataChannel = peerConnection?.createDataChannel("fileChannel", init)
            dataChannel?.registerObserver(object : DataChannel.Observer {
                override fun onBufferedAmountChange(p0: Long) {}

                override fun onStateChange() {
                    if (dataChannel?.state() == DataChannel.State.OPEN) {
                        FileTransferUtils.sendFileOverDataChannel(context, fileUri, dataChannel!!)
                    }
                }

                override fun onMessage(buffer: DataChannel.Buffer?) {}
            })

            signalingClient.onAnswerReceived { sdp ->
                peerConnection?.setRemoteDescription(object : SdpObserver {
                    override fun onSetSuccess() {
                        Log.i("SenderSession", "Remote SDP set successfully.")
                    }

                    override fun onSetFailure(error: String?) {
                        Log.e("SenderSession", "SetRemoteDescription failed: $error")
                    }

                    override fun onCreateSuccess(p0: SessionDescription?) {}
                    override fun onCreateFailure(p0: String?) {}
                }, SessionDescription(SessionDescription.Type.ANSWER, sdp))
            }

            // ICE candidates from remote
            signalingClient.onIceCandidateReceived { candidate ->
                peerConnection?.addIceCandidate(candidate)
            }

            // Create and send offer
            WebRtcManager.createOffer { offer ->
                signalingClient.sendOffer(peerId, offer)
            }
        }
    }

    fun dispose() {
        dataChannel?.close()
        dataChannel = null
        peerConnection?.close()
        peerConnection = null
    }
}