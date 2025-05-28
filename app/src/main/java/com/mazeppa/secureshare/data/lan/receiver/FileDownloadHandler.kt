package com.mazeppa.secureshare.data.lan.receiver

import android.content.Context
import android.util.Log
import com.mazeppa.secureshare.data.client_server.FileDownloader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object FileDownloadHandler {
    fun createDownloadCallback(
        context: Context,
        onStatus: (String) -> Unit
    ): (String, String) -> Unit = { url, name ->
        Log.i("Download", "Downloading file from URL: $url")
        CoroutineScope(Dispatchers.Main).launch {
            FileDownloader.downloadFile(context, url, name) { success, message ->
                Log.i("Download", "Download result: $success, message: $message")
                onStatus(message)
            }
        }
    }
}