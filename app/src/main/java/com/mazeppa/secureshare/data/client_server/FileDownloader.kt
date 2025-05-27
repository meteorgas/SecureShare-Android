package com.mazeppa.secureshare.data.client_server

import android.app.DownloadManager
import android.content.Context
import android.os.Environment
import android.util.Log
import androidx.core.net.toUri
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okio.IOException
import java.io.File
import java.io.FileOutputStream
import java.net.URLConnection

object FileDownloader {

    fun downloadFile(context: Context, fileUrl: String, fileName: String, callback: (Boolean, String) -> Unit) {
        Log.d("Downloader", "Downloading from: $fileUrl")

        val request = Request.Builder()
            .url(fileUrl)
            .build()

        val client = OkHttpClient()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("Downloader", "Failed: ${e.message}")
                callback(false, "Failed: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    callback(false, "Server returned error: ${response.message}")
                    return
                }

                val inputStream = response.body?.byteStream()
                if (inputStream == null) {
                    callback(false, "No data")
                    return
                }

                try {
                    val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    val file = File(downloads, fileName)
                    val outputStream = FileOutputStream(file)

                    inputStream.use { input ->
                        outputStream.use { output ->
                            input.copyTo(output)
                        }
                    }

                    Log.d("Downloader", "Saved to: ${file.absolutePath}")
                    callback(true, "Saved to: ${file.absolutePath}")
                } catch (e: Exception) {
                    Log.e("Downloader", "Saving failed: ${e.message}")
                    callback(false, "Saving failed: ${e.message}")
                }
            }
        })
    }
}