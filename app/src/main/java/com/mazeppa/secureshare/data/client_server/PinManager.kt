package com.mazeppa.secureshare.data.client_server

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.IOException
import org.json.JSONObject

object PinManager {

    @SuppressLint("HardwareIds")
    fun generatePin(
        context: Context,
        serverUrl: String,
        callback: (pin: String?, error: String?) -> Unit
    ) {
        val deviceId =
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)

        val json = JSONObject().put("deviceId", deviceId).toString()
        val requestBody = json.toRequestBody("application/json".toMediaTypeOrNull())

        val request = Request.Builder()
            .url("$serverUrl/generate-pin")
            .post(requestBody)
            .build()

        OkHttpClient().newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback(null, "Network error: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    callback(null, "Server error: ${response.message}")
                    return
                }

                val body = response.body?.string()
                val pin = try {
                    JSONObject(body ?: "").getString("pin")
                } catch (e: Exception) {
                    null
                }

                if (pin != null) {
                    callback(pin, null)
                } else {
                    callback(null, "Invalid response")
                }
            }
        })
    }
}