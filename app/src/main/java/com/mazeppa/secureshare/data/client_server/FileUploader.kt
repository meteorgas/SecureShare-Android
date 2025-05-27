package com.mazeppa.secureshare.data.client_server

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException

object FileUploader {

    fun uploadFileToServer(
        context: Context,
        uri: Uri,
        serverUrl: String,
        callback: (Boolean, String) -> Unit
    ) {
        val contentResolver = context.contentResolver
        val fileName = getFileName(context, uri)
        val fileBytes = contentResolver.openInputStream(uri)?.readBytes()

        if (fileBytes == null) {
            Log.e("Uploader", "Cannot read file")
            callback(false, "Cannot read file")
            return
        }

        Log.d("Uploader", "Uploading file: $fileName (${fileBytes.size} bytes)")
        Log.d("Uploader", "Uploading to URL: $serverUrl")

        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", fileName, fileBytes.toRequestBody(null))
            .build()

        val request = Request.Builder()
            .url(serverUrl)
            .post(requestBody)
            .build()

        OkHttpClient().newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("Uploader", "Upload failed: ${e.message}", e)
                callback(false, "Upload failed: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                val bodyText = response.body?.string()
                Log.d("Uploader", "Upload response code: ${response.code}")
                Log.d("Uploader", "Upload response body: $bodyText")

                val success = response.isSuccessful
                val msg =
                    if (success) "Upload successful!" else "Upload failed: ${response.message}"
                callback(success, msg)
            }
        })
    }

    fun getFileName(context: Context, uri: Uri): String {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        return cursor?.use {
            val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            it.moveToFirst()
            it.getString(nameIndex)
        } ?: "unknown"
    }
}