package com.mazeppa.secureshare.data.p2p

import android.util.Log
import com.mazeppa.secureshare.util.constant.BASE_URL
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import org.webrtc.IceCandidate
import org.webrtc.SessionDescription
import java.io.IOException
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

    override fun lookupPin(pin: String, callback: (Boolean, String?, String?) -> Unit) {
        val request = Request.Builder()
            .url("$BASE_URL/lookup-pin")
            .post(JSONObject().apply {
                put("pin", pin)
            }.toString().toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback(false, null, e.message)
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    val peerId = JSONObject(body ?: "").optString("peerId", null)
                    callback(true, peerId, null)
                } else {
                    callback(false, null, "Invalid or expired PIN")
                }
            }
        })
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