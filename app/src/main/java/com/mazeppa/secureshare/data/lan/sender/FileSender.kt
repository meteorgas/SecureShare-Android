package com.mazeppa.secureshare.data.lan.sender

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.mazeppa.secureshare.util.FileManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.DataOutputStream
import java.net.Socket

class FileSender(private val context: Context) {

    interface FileSenderListener {
        fun onStatusUpdate(message: String)
        fun onProgressUpdate(fileName: String, progress: Int)
        fun onComplete()
        fun onError(error: String)
        fun onTransferStatsUpdate(speedBytesPerSec: Double, remainingSec: Double)
    }

    suspend fun sendFile(uri: Uri, ipAddress: String, port: Int, listener: FileSenderListener) {
        withContext(Dispatchers.IO) {
            try {
                val fileName = getFileName(uri) ?: "unnamed_file"
                val fileSize = getFileSize(uri)

                listener.onStatusUpdate("Connecting to $ipAddress:$port...")
                val socket = Socket(ipAddress, port)
                val outputStream = DataOutputStream(socket.getOutputStream())
                val inputStream = context.contentResolver.openInputStream(uri)
                    ?: throw Exception("Cannot open file")

                val metadataJson = JSONObject().apply {
                    put("fileName", fileName)
                    put("fileSize", fileSize)
                    put("mimeType", FileManager.getMimeType(context, uri))
                }.toString()

                outputStream.writeUTF(metadataJson)

                listener.onStatusUpdate("Sending $fileName ($fileSize bytes)...")

                val buffer = ByteArray(4096)
                var totalBytesSent = 0L
                var bytesRead: Int

                val startTime = System.currentTimeMillis()

                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                    totalBytesSent += bytesRead

                    // Calculate progress
                    val progress = ((totalBytesSent * 100) / fileSize).toInt()
                    listener.onProgressUpdate(fileName, progress)

                    // Calculate speed and estimated time
                    val elapsedSeconds = (System.currentTimeMillis() - startTime) / 1000.0
                    if (elapsedSeconds > 0) {
                        val speedBytesPerSec = totalBytesSent / elapsedSeconds
                        val remainingBytes = fileSize - totalBytesSent
                        val estimatedRemainingSec = if (speedBytesPerSec > 0)
                            remainingBytes / speedBytesPerSec else 0.0

                        listener.onTransferStatsUpdate(speedBytesPerSec, estimatedRemainingSec)
                    }
                }

                inputStream.close()
                outputStream.flush()
                socket.close()

                listener.onComplete()
            } catch (e: Exception) {
                listener.onError(e.message ?: "Unknown error")
            }
        }
    }

    private fun getFileName(uri: Uri): String? {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        return cursor?.use {
            val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (it.moveToFirst()) it.getString(nameIndex) else null
        }
    }

    private fun getFileSize(uri: Uri): Long {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        return cursor?.use {
            val sizeIndex = it.getColumnIndex(OpenableColumns.SIZE)
            if (it.moveToFirst()) it.getLong(sizeIndex) else 0L
        } ?: 0L
    }
}