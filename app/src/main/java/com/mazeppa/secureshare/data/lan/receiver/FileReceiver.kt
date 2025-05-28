package com.mazeppa.secureshare.data.lan.receiver

import android.os.Environment
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.DataInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.ServerSocket
import java.net.SocketException

class FileReceiver(private val port: Int = 5051) {

    companion object {
        private const val TAG = "FileReceiver"
    }

    interface FileReceiverListener {
        fun onFileMetadataReceived(name: String, size: Long, mimeType: String)
        fun onFileProgressUpdate(name: String, progress: Int)
        fun onFileReceived(path: String)
        fun onStatusUpdate(message: String)
        fun onError(message: String)
    }

    @Volatile
    private var isRunning = false
    private var serverSocket: ServerSocket? = null

    suspend fun start(listener: FileReceiverListener) {
        withContext(Dispatchers.IO) {
            try {
                isRunning = true
                serverSocket = ServerSocket(port)
                listener.onStatusUpdate("Waiting for sender on port $port...")

                while (isRunning) {
                    val socket = try {
                        serverSocket?.accept()
                    } catch (e: SocketException) {
                        // Happens when serverSocket is closed externally
                        if (isRunning) listener.onError("Socket error: ${e.message}")
                        break
                    } ?: break

                    val inputStream = DataInputStream(socket.getInputStream())

                    val metadataJson = inputStream.readUTF()
                    val metadata = JSONObject(metadataJson)
                    val fileName = metadata.getString("fileName")
                    val fileSize = metadata.getLong("fileSize")
                    val mimeType = metadata.optString("mimeType", "application/octet-stream")

                    listener.onFileMetadataReceived(fileName, fileSize, mimeType)

                    val downloads =
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    val outputFile = generateUniqueFile(downloads, fileName)
                    val outputStream = FileOutputStream(outputFile)

                    val buffer = ByteArray(4096)
                    var totalBytesRead = 0L

                    while (totalBytesRead < fileSize) {
                        val bytesRead = inputStream.read(buffer)
                        if (bytesRead == -1) break
                        outputStream.write(buffer, 0, bytesRead)
                        totalBytesRead += bytesRead

                        val progress = ((totalBytesRead * 100) / fileSize).toInt()
                        listener.onFileProgressUpdate(fileName, progress)
                    }

                    outputStream.close()
                    inputStream.close()
                    socket.close()

                    listener.onFileReceived(outputFile.absolutePath)
                    listener.onStatusUpdate("Waiting for next sender...")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in file receiver: ${e.message}")
                if (isRunning) listener.onError(e.message ?: "Unknown error")
            } finally {
                serverSocket?.close()
                serverSocket = null
                isRunning = false
            }
        }
    }

    fun generateUniqueFile(dir: File, baseName: String): File {
        val nameWithoutExt = baseName.substringBeforeLast(".")
        val extension = baseName.substringAfterLast(".", "")
        var file = File(dir, baseName)
        var counter = 1

        while (!isFileWritable(file)) {
            val newName = "$nameWithoutExt($counter).$extension"
            file = File(dir, newName)
            counter++
        }

        return file
    }

    private fun isFileWritable(file: File): Boolean {
        return try {
            val stream = FileOutputStream(file)
            stream.close()
            file.delete() // Cleanup test file
            true
        } catch (e: IOException) {
            false
        }
    }

    fun stop() {
        isRunning = false
        serverSocket?.close()
        serverSocket = null
    }
}