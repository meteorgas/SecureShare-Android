package com.mazeppa.secureshare.data.lan

import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.DataInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.ServerSocket
import java.net.Socket

class FileReceiver(private val port: Int = 5050) {

    interface FileReceiverListener {
        fun onStatusUpdate(message: String)
        fun onFileReceived(path: String)
        fun onError(message: String)
    }

    @Volatile
    private var isRunning = true

    suspend fun start(listener: FileReceiverListener) {
        withContext(Dispatchers.IO) {
            try {
                ServerSocket(port).use { serverSocket ->
                    listener.onStatusUpdate("Waiting for sender on port $port...")

                    while (true) { // Keep listening for multiple files
                        val socket: Socket = serverSocket.accept()
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
                }
            } catch (e: Exception) {
                listener.onError(e.message ?: "Unknown error")
            }
        }
    }
}