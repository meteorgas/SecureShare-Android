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
    serverUrl: String,
    private val selfId: String
) : SignalingClient {

    private var offerCallback: ((String) -> Unit)? = null
    private var answerCallback: ((String) -> Unit)? = null
    private var candidateCallback: ((IceCandidate) -> Unit)? = null

    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private var webSocket: WebSocket

    init {
        val request = Request.Builder().url(serverUrl).build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                val json = JSONObject(text)
                when (json.getString("type")) {
                    "offer" -> offerCallback?.invoke(json.getString("sdp"))
                    "answer" -> answerCallback?.invoke(json.getString("sdp"))
                    "ice-candidate" -> {
                        val candidate = IceCandidate(
                            json.getString("sdpMid"),
                            json.getInt("sdpMLineIndex"),
                            json.getString("candidate")
                        )
                        candidateCallback?.invoke(candidate)
                    }
                }
            }

            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.i("SignalingClient", "WebSocket connected")
                webSocket.send(JSONObject().apply {
                    put("type", "register")
                    put("peerId", selfId)
                }.toString())
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e("SignalingClient", "WebSocket error: ${t.message}")
            }
        })
    }

    override fun sendOffer(peerId: String, offer: SessionDescription) {
        val json = JSONObject().apply {
            put("type", "offer")
            put("to", peerId)
            put("sdp", offer.description)
        }
        webSocket.send(json.toString())
    }

    override fun sendAnswer(peerId: String, answer: SessionDescription) {
        val json = JSONObject().apply {
            put("type", "answer")
            put("to", peerId)
            put("sdp", answer.description)
        }
        webSocket.send(json.toString())
    }

    override fun sendIceCandidate(peerId: String, candidate: IceCandidate) {
        val json = JSONObject().apply {
            put("type", "ice-candidate")
            put("to", peerId)
            put("sdpMid", candidate.sdpMid)
            put("sdpMLineIndex", candidate.sdpMLineIndex)
            put("candidate", candidate.sdp)
        }
        webSocket.send(json.toString())
    }

    override fun onOfferReceived(callback: (String) -> Unit) {
        offerCallback = callback
    }

    override fun onAnswerReceived(callback: (String) -> Unit) {
        answerCallback = callback
    }

    override fun onIceCandidateReceived(callback: (IceCandidate) -> Unit) {
        candidateCallback = callback
    }
}