package com.mazeppa.secureshare.data.p2p

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.webrtc.DataChannel
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.RtpReceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import java.io.IOException
import java.nio.ByteBuffer

class SenderSession(
    private val context: Context,
    private val signalingClient: WebSocketSignalingClient,
    private val localPeerId: String,      // from /generate-pin
    private val remotePeerId: String,     // returned by /lookup-pin on receiver
    private val fileUri: Uri,             // URI of the file to send
    private val onProgress: (percent: Int) -> Unit,
    private val onComplete: () -> Unit,
    private val onError: (Exception) -> Unit
) {

    private val TAG = "SenderSession"
    private val scope = CoroutineScope(Dispatchers.IO)

    // 1) Create PeerConnection
    private val peerConnection: PeerConnection = WebRtcManager.getFactory()?.createPeerConnection(
            PeerConnection.RTCConfiguration(WebRtcManager.iceServers),
            object : PeerConnection.Observer {
                override fun onIceCandidate(candidate: IceCandidate) {
                    signalingClient.sendIceCandidate(candidate, remotePeerId)
                }

                override fun onDataChannel(dc: DataChannel) {
                    // Sender doesn't expect incoming DC
                }

                override fun onSignalingChange(newState: PeerConnection.SignalingState) {}
                override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {}
                override fun onIceConnectionReceivingChange(p0: Boolean) {
                    Log.i(TAG, "ICE connection receiving change: $p0")
                }

                override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) {}
                override fun onAddStream(stream: MediaStream) {}
                override fun onRemoveStream(stream: MediaStream) {}
                override fun onIceCandidatesRemoved(candidates: Array<IceCandidate>) {}
                override fun onRenegotiationNeeded() {
                    Log.d(TAG, "Renegotiation needed → creating offer")
                    peerConnection.createOffer(object : SdpObserver {
                        override fun onCreateSuccess(desc: SessionDescription) {
                            peerConnection.setLocalDescription(object : SdpObserver {
                                override fun onSetSuccess() {
                                    signalingClient.sendOffer(desc, remotePeerId)
                                }

                                override fun onSetFailure(err: String) =
                                    onError(Exception("setLocalDesc failed: $err"))

                                override fun onCreateSuccess(p0: SessionDescription?) {}
                                override fun onCreateFailure(p0: String?) {}
                            }, desc)
                        }

                        override fun onCreateFailure(error: String) =
                            onError(Exception("createOffer failed: $error"))

                        override fun onSetSuccess() {}
                        override fun onSetFailure(error: String) =
                            onError(Exception("onReneg set fail: $error"))
                    }, MediaConstraints())
                }

                override fun onAddTrack(receiver: RtpReceiver, streams: Array<out MediaStream>) {}
            }
        ) ?: throw IllegalStateException("Failed to create PeerConnection")

    // 2) Create DataChannel for file chunks
    private val dataChannel: DataChannel = peerConnection.createDataChannel(
        "fileChannel",
        DataChannel.Init().apply {
            ordered = true          // preserve order
            maxRetransmits = -1     // reliable
        }
    ).apply {
        registerObserver(object : DataChannel.Observer {
            override fun onBufferedAmountChange(prevAmount: Long) {}
            override fun onStateChange() {
                if (state() == DataChannel.State.OPEN) {
                    Log.d(TAG, "DataChannel open → start sending file")
                    sendFileChunks()
                }
            }

            override fun onMessage(buffer: DataChannel.Buffer) {}
        })
    }

    /** Kick off the WS + offer/answer exchange. */
    fun start() {
        // 3) Connect WebSocket and register callbacks
        signalingClient.connect()
        // When an answer arrives:
        signalingClient.apply {
            // we capture callbacks by re-wrapping into our onAnswer / onIceCandidate below
            // assume you passed lambdas that call these:
        }
    }

    /** Call this when you get the remote SDP answer JSON. */
    fun onRemoteAnswer(answer: SessionDescription) {
        peerConnection.setRemoteDescription(object : SdpObserver {
            override fun onSetSuccess() {
                Log.d(TAG, "Remote SDP set")
            }

            override fun onSetFailure(e: String) = onError(Exception("setRemoteDesc failed: $e"))
            override fun onCreateSuccess(p0: SessionDescription?) {}
            override fun onCreateFailure(p0: String?) {}
        }, answer)
    }

    /** Call this when you get an ICE candidate JSON from remote. */
    fun onRemoteIceCandidate(candidate: IceCandidate) {
        peerConnection.addIceCandidate(candidate)
    }

    /** Read the file in 16 KB chunks and send each over DataChannel. */
    private fun sendFileChunks() {
        scope.launch {
            try {
                // get file size for progress
                val projection = arrayOf(OpenableColumns.SIZE)
                val cursor = context.contentResolver
                    .query(fileUri, projection, null, null, null)
                    ?: throw IOException("Failed to open cursor for $fileUri")
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (sizeIndex < 0 || !cursor.moveToFirst()) {
                    cursor.close()
                    throw IOException("Cannot determine file size")
                }
                // now read the value
                val totalSize = cursor.getLong(sizeIndex)
                cursor.close()

//                val cursor = context.contentResolver.query(fileUri, null, null, null, null)
//                val sizeIndex = cursor?.getColumnIndexOpenableColumnsSize() ?: -1
//                val totalSize: Long = if (sizeIndex >= 0 && cursor!!.moveToFirst()) {
//                    cursor.getLong(sizeIndex).also { cursor.close() }
//                } else {
//                    cursor?.close()
//                    throw IOException("Cannot determine file size")
//                }

                val stream = context.contentResolver.openInputStream(fileUri)
                    ?: throw IOException("Failed to open file")
                val buffer = ByteArray(16 * 1024)
                var read: Int
                var sentBytes = 0L
                while (stream.read(buffer).also { read = it } > 0) {
                    val bb = ByteBuffer.wrap(buffer, 0, read)
                    dataChannel.send(DataChannel.Buffer(bb, false))
                    sentBytes += read
                    onProgress((sentBytes * 100 / totalSize).toInt())
                }
                stream.close()
                onComplete()
            } catch (e: Exception) {
                onError(e)
            }
        }
    }

    /** Clean up everything when you’re done or on error. */
    fun close() {
        dataChannel.close()
        peerConnection.close()
        signalingClient.close()
    }
}