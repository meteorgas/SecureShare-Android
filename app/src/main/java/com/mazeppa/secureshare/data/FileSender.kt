package com.mazeppa.secureshare.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.DataOutputStream
import java.net.Socket

class FileSender(private val context: Context) {

    interface FileSenderListener {
        fun onStatusUpdate(message: String)
        fun onProgressUpdate(progress: Int)
        fun onComplete()
        fun onError(error: String)
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

                // Send metadata
                outputStream.writeUTF(fileName)
                outputStream.writeLong(fileSize)

                listener.onStatusUpdate("Sending $fileName ($fileSize bytes)...")

                val buffer = ByteArray(4096)
                var totalBytesSent = 0L
                var bytesRead: Int

                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                    totalBytesSent += bytesRead
                    val progress = ((totalBytesSent * 100) / fileSize).toInt()
                    listener.onProgressUpdate(progress)
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
            val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (it.moveToFirst()) it.getString(nameIndex) else null
        }
    }

    private fun getFileSize(uri: Uri): Long {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        return cursor?.use {
            val sizeIndex = it.getColumnIndex(android.provider.OpenableColumns.SIZE)
            if (it.moveToFirst()) it.getLong(sizeIndex) else 0L
        } ?: 0L
    }
}
