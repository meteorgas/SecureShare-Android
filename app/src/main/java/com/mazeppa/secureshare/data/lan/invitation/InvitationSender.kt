package com.mazeppa.secureshare.data.lan.invitation

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.IOException
import org.json.JSONObject

object InvitationSender {
    fun send(
        ip: String,
        fileName: String,
        onAccepted: suspend () -> Unit,
        onRejected: () -> Unit,
        onError: (String) -> Unit
    ) {
        val json = JSONObject().apply { put("fileName", fileName) }
        val requestBody = json.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("http://$ip:5050/invite")
            .post(requestBody)
            .build()

        OkHttpClient().newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                onError("Failed: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    CoroutineScope(Dispatchers.Main).launch { onAccepted() }
                } else {
                    CoroutineScope(Dispatchers.Main).launch { onRejected() }
                }
            }
        })
    }
}