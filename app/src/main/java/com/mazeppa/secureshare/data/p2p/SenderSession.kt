package com.mazeppa.secureshare.data.p2p

import android.content.Context
import android.net.Uri
import android.util.Log
import org.webrtc.DataChannel
import org.webrtc.PeerConnection
import org.webrtc.SessionDescription

class SenderSession(
    private val context: Context,
    private val selfId: String,
    private val signalingClient: SignalingClient
) {

    private val webRtcManager = WebRtcManager.initialize(context)
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

            val init = DataChannel.Init()
            dataChannel = peerConnection?.createDataChannel("fileChannel", init)

            signalingClient.onAnswerReceived { sdp ->
                peerConnection?.setRemoteDescription(
                    SdpObserverAdapter(),
                    SessionDescription(SessionDescription.Type.ANSWER, sdp)
                )
            }

            signalingClient.onIceCandidateReceived { candidate ->
                peerConnection?.addIceCandidate(candidate)
            }

            WebRtcManager.createOffer { offer ->
                signalingClient.sendOffer(peerId, offer)
            }

            // Optional: wait until connection state is CONNECTED to send file
            dataChannel?.registerObserver(object : DataChannel.Observer {
                override fun onBufferedAmountChange(p0: Long) {
                    Log.d("WebRTC", "Buffered amount changed: $p0")
                }

                override fun onStateChange() {
                    if (dataChannel?.state() == DataChannel.State.OPEN) {
                        FileTransferUtils.sendFileOverDataChannel(context, fileUri, dataChannel!!)
                    }
                }

                override fun onMessage(buffer: DataChannel.Buffer?) {}
            })
        }
    }
}