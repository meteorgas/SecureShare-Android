package com.mazeppa.secureshare.data.p2p

import android.os.Handler
import android.os.Looper
import android.util.Log
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.IOException
import org.json.JSONObject

object PinPairingService {

    private const val TAG = "PinPairingService"
    private const val BASE_URL = com.mazeppa.secureshare.util.constant.BASE_URL // Change this
    private val client = OkHttpClient()

    fun generatePin(deviceId: String, onResult: (Boolean, String?, String?) -> Unit) {
        val json = JSONObject().apply {
            put("deviceId", deviceId)
        }

        val requestBody = json.toString().toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url("$BASE_URL/generate-pin")
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                onResult(false, null, e.message)
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    val data = JSONObject(body ?: "")
                    val pin = data.getString("pin")
                    val peerId = data.getString("peerId")
                    onResult(true, pin, peerId)
                } else {
                    onResult(false, null, "Failed: ${response.code}")
                }
            }
        })
    }

    fun lookupPin(pin: String, onResult: (Boolean, String?, String?) -> Unit) {
        val json = JSONObject().apply {
            put("pin", pin)
        }

        val requestBody = json.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("$BASE_URL/lookup-pin")
            .post(requestBody)
            .build()

        OkHttpClient().newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.i(TAG, "lookupPin onFailure: ${e.message}")
                onResult(false, null, e.message)
            }

            override fun onResponse(call: Call, response: Response) {
                Log.i(TAG, "lookupPin onResponse: ${response.code} - ${response.message}")
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    val peerId = JSONObject(body ?: "").getString("peerId")
                    onResult(true, peerId, null)
                } else {
                    onResult(false, null, "Invalid or expired PIN")
                }
            }
        })
    }

    fun acceptInvite(pin: String, receiverId: String, onResult: (Boolean, String?) -> Unit) {
        val json = JSONObject().apply {
            put("pin", pin)
            put("deviceId", receiverId)
        }

        val requestBody = json.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("$BASE_URL/accept-invite")
            .post(requestBody)
            .build()

        OkHttpClient().newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                onResult(false, e.message)
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    val json = JSONObject(body ?: "")
                    val senderId = json.getString("senderId")
                    onResult(true, senderId)
                } else {
                    onResult(false, "Failed to accept invite")
                }
            }
        })
    }

    fun sendOfferToServer(pin: String, sdp: String, onComplete: (Boolean) -> Unit = {}) {
        val json = JSONObject().apply {
            put("pin", pin)
            put("sdp", sdp)
        }

        val request = Request.Builder()
            .url("$BASE_URL/offer")
            .post(json.toString().toRequestBody("application/json".toMediaType()))
            .build()

        OkHttpClient().newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "Failed to send offer: ${e.message}")
                onComplete(false)
            }

            override fun onResponse(call: Call, response: Response) {
                onComplete(response.isSuccessful)
            }
        })
    }

    fun pollForAnswer(pin: String, onAnswerReceived: (String) -> Unit) {
        val request = Request.Builder()
            .url("$BASE_URL/answer/$pin")
            .get()
            .build()

        val client = OkHttpClient()

        val handler = Handler(Looper.getMainLooper())

        lateinit var runnable: Runnable

        runnable = Runnable {
            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    Log.e("PinPairing", "Request failed: ${e.message}")
                    handler.postDelayed(runnable, 2000) // retry again
                }

                override fun onResponse(call: Call, response: Response) {
                    if (response.isSuccessful) {
                        val answer = response.body?.string() ?: ""
                        onAnswerReceived(answer)
                    } else {
                        handler.postDelayed(runnable, 2000) // keep polling
                    }
                }
            })
        }

        handler.post(runnable)
    }
}