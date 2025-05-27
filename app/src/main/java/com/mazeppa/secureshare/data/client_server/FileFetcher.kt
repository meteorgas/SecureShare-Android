package com.mazeppa.secureshare.data.client_server

import android.util.Log
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okio.IOException
import org.json.JSONArray

object FileFetcher {

    private const val TAG = "FileFetcher"

    fun fetchFileList(serverUrl: String, callback: (List<SharedFile>?, String?) -> Unit) {
        val fullUrl = "$serverUrl/files"
        Log.d(TAG, "Fetching file list from: $fullUrl")

        val request = Request.Builder()
            .url(fullUrl)
            .build()

        OkHttpClient().newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "Request failed: ${e.message}", e)
                callback(null, "Failed to fetch files: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                Log.d(TAG, "Response received. Code: ${response.code}")

                if (!response.isSuccessful) {
                    Log.e(TAG, "Server returned error: ${response.message}")
                    callback(null, "Server error: ${response.message}")
                    return
                }

                val body = response.body?.string()
                if (body == null) {
                    Log.e(TAG, "Empty response body")
                    callback(null, "Empty response body")
                    return
                }

                Log.d(TAG, "Raw response body: $body")

                try {
                    val jsonArray = JSONArray(body)
                    val fileList = mutableListOf<SharedFile>()

                    for (i in 0 until jsonArray.length()) {
                        val obj = jsonArray.getJSONObject(i)
                        val name = obj.getString("name")
                        val url = obj.getString("url")
                        fileList.add(SharedFile(name, url))
                        Log.d(TAG, "Parsed file: $name → $url")
                    }

                    callback(fileList, null)

                } catch (e: Exception) {
                    Log.e(TAG, "Failed to parse response: ${e.message}", e)
                    callback(null, "Parsing error: ${e.message}")
                }
            }
        })
    }
}