package com.mazeppa.secureshare.data.p2p

import android.content.Context
import android.net.Uri
import android.util.Log
import org.webrtc.DataChannel
import java.nio.ByteBuffer

object FileTransferUtils {

    fun sendFileOverDataChannel(context: Context, uri: Uri, dataChannel: DataChannel) {
        try {
            val inputStream = context.contentResolver.openInputStream(uri)
            val buffer = ByteArray(4096)

            while (true) {
                val bytesRead = inputStream?.read(buffer) ?: -1
                if (bytesRead == -1) break

                val byteBuffer = ByteBuffer.wrap(buffer, 0, bytesRead)
                val bufferToSend = DataChannel.Buffer(byteBuffer, true)
                dataChannel.send(bufferToSend)
            }

            inputStream?.close()
            Log.i("FileTransfer", "File sent over DataChannel.")

        } catch (e: Exception) {
            Log.e("FileTransfer", "Error sending file: ${e.message}", e)
        }
    }
}