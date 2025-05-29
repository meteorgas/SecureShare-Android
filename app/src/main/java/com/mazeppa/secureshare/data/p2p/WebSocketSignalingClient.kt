package com.mazeppa.secureshare.data.p2p

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import org.webrtc.IceCandidate
import org.webrtc.SessionDescription
import java.util.concurrent.TimeUnit

class WebSocketSignalingClient(
    private val serverUrl: String,        // e.g. "wss://your.server.com"
    private val peerId: String,           // your UUID from /generate-pin
    private val onOffer: (offer: SessionDescription, from: String) -> Unit,
    private val onAnswer: (answer: SessionDescription, from: String) -> Unit,
    private val onIceCandidate: (candidate: IceCandidate, from: String) -> Unit,
    private val onOpen: () -> Unit,
) {
    private val TAG = "WS-SignalClient"
    private val client = OkHttpClient.Builder()
        .pingInterval(20, TimeUnit.SECONDS)  // keep-alive
        .build()
    private var ws: WebSocket? = null

    /** Call once to open the socket and register yourself on the server. */
    fun connect() {
        val request = Request.Builder()
            .url(serverUrl)
            .build()

        ws = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "WS opened, registering peerId=$peerId")
                val msg = JSONObject()
                    .put("type", "register")
                    .put("peerId", peerId)
                webSocket.send(msg.toString())
                onOpen()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d(TAG, "WS recv → $text")
                val json = JSONObject(text)
                when (val type = json.getString("type")) {
                    "offer" -> {
                        val sdp = json.getString("sdp")
                        val from = json.getString("from")
                        onOffer(SessionDescription(SessionDescription.Type.OFFER, sdp), from)
                    }

                    "answer" -> {
                        val sdp = json.getString("sdp")
                        val from = json.getString("from")
                        onAnswer(SessionDescription(SessionDescription.Type.ANSWER, sdp), from)
                    }

                    "ice-candidate" -> {
                        val candidateStr = json.getString("candidate")
                        val sdpMid = json.getString("sdpMid")
                        val sdpMLineIndex = json.getInt("sdpMLineIndex")
                        val from = json.getString("from")
                        onIceCandidate(
                            IceCandidate(sdpMid, sdpMLineIndex, candidateStr),
                            from
                        )
                    }

                    else -> {
                        Log.w(TAG, "Unknown WS type: $type")
                    }
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WS failure", t)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WS closing: $code / $reason")
                webSocket.close(1000, null)
            }
        })
    }

    /** Send your SDP offer to the given peerId */
    fun sendOffer(sdp: SessionDescription, to: String) {
        val msg = JSONObject()
            .put("type", "offer")
            .put("to", to)
            .put("sdp", sdp.description)
        ws?.send(msg.toString())
        Log.d(TAG, "Sent offer → $to")
    }

    /** Send your SDP answer to the given peerId */
    fun sendAnswer(sdp: SessionDescription, to: String) {
        val msg = JSONObject()
            .put("type", "answer")
            .put("to", to)
            .put("sdp", sdp.description)
        ws?.send(msg.toString())
        Log.d(TAG, "Sent answer → $to")
    }

    /** Send an ICE candidate to the given peerId */
    fun sendIceCandidate(candidate: IceCandidate, to: String) {
        val msg = JSONObject()
            .put("type", "ice-candidate")
            .put("to", to)
            .put("candidate", candidate.sdp)
            .put("sdpMid", candidate.sdpMid)
            .put("sdpMLineIndex", candidate.sdpMLineIndex)
        ws?.send(msg.toString())
        Log.d(TAG, "Sent ICE → $to : ${candidate.sdp}")
    }

    /** Cleanly close the socket */
    fun close() {
        ws?.close(1000, "Client closing")
        ws = null
        client.dispatcher.executorService.shutdown()
    }
}