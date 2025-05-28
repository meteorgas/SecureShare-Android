package com.mazeppa.secureshare.data.lan

import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.DataInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException

class FileReceiver(private val port: Int = 5051) {

    interface FileReceiverListener {
        fun onStatusUpdate(message: String)
        fun onFileReceived(path: String)
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

                    val fileName = inputStream.readUTF()
                    val fileSize = inputStream.readLong()

                    val downloads =
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    val outputFile = File(downloads, fileName)
                    val outputStream = FileOutputStream(outputFile)

                    val buffer = ByteArray(4096)
                    var totalBytesRead = 0L

                    while (totalBytesRead < fileSize) {
                        val bytesRead = inputStream.read(buffer)
                        if (bytesRead == -1) break
                        outputStream.write(buffer, 0, bytesRead)
                        totalBytesRead += bytesRead
                    }

                    outputStream.close()
                    inputStream.close()
                    socket.close()

                    listener.onFileReceived(outputFile.absolutePath)
                    listener.onStatusUpdate("Waiting for next sender...")
                }
            } catch (e: Exception) {
                if (isRunning) listener.onError(e.message ?: "Unknown error")
            } finally {
                serverSocket?.close()
                serverSocket = null
                isRunning = false
            }
        }
    }

    fun stop() {
        isRunning = false
        serverSocket?.close()  // this will unblock .accept()
        serverSocket = null
    }
}