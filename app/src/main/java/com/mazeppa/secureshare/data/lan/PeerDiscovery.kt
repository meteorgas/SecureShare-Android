package com.mazeppa.secureshare.data.lan

import android.os.Build
import android.util.Log
import com.mazeppa.secureshare.util.constant.DiscoveryConfig
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.IOException
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketException
import java.net.SocketTimeoutException

object PeerDiscovery {

    private const val TAG = "PeerDiscovery"
    private var receiverSocket: DatagramSocket? = null
    private var receiverThread: Thread? = null

    fun startPeerDiscoveryReceiver() {
        if (receiverSocket != null) {
            Log.w(TAG, "Receiver already running.")
            return
        }

        receiverThread = Thread {
            try {
                val socket = DatagramSocket(DiscoveryConfig.BROADCAST_PORT, InetAddress.getByName("0.0.0.0"))
                receiverSocket = socket
                socket.broadcast = true
                val buffer = ByteArray(1024)

                Log.d(TAG, "Peer discovery receiver started on port ${DiscoveryConfig.BROADCAST_PORT}")

                while (!Thread.interrupted() && !socket.isClosed) {
                    try {
                        val packet = DatagramPacket(buffer, buffer.size)
                        socket.receive(packet)

                        val message = String(packet.data, 0, packet.length)
                        Log.d(TAG, "Received discovery packet: $message from ${packet.address.hostAddress}")

                        if (message == DiscoveryConfig.BROADCAST_MESSAGE) {
                            val deviceName = Build.MODEL ?: "Unknown Device"
                            val responseJson = JSONObject().apply {
                                put("deviceName", deviceName)
                            }.toString()

                            val response = responseJson.toByteArray()
                            val responsePacket = DatagramPacket(
                                response, response.size,
                                packet.address, packet.port
                            )
                            socket.send(responsePacket)
                            Log.d(TAG, "Sent response to ${packet.address.hostAddress}")
                        }
                    } catch (e: SocketException) {
                        if (socket.isClosed) {
                            Log.i(TAG, "Socket closed, stopping receiver loop.")
                            break
                        } else {
                            Log.e(TAG, "Socket error during receive", e)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Receiver error (outer): ${e.message}", e)
            } finally {
                receiverSocket?.close()
                receiverSocket = null
            }
        }
        receiverThread?.start()
    }

    fun stopPeerDiscoveryReceiver() {
        receiverThread?.interrupt()
        receiverSocket?.close()
        receiverThread = null
        receiverSocket = null
        Log.i("PeerDiscovery", "Receiver stopped.")
    }

    fun discoverPeers(onPeerFound: (deviceName: String?, ip: String) -> Unit) {
        Thread {
            try {
                val socket = DatagramSocket()
                socket.broadcast = true

                val data = DiscoveryConfig.BROADCAST_MESSAGE.toByteArray()
                val packet = DatagramPacket(
                    data,
                    data.size,
                    InetAddress.getByName("255.255.255.255"),
                    DiscoveryConfig.BROADCAST_PORT
                )
                socket.send(packet)
                Log.d(TAG, "Broadcast discovery message sent")

                val buffer = ByteArray(1024)
                socket.soTimeout = 3000

                while (true) {
                    try {
                        val responsePacket = DatagramPacket(buffer, buffer.size)
                        socket.receive(responsePacket)
                        val message = String(responsePacket.data, 0, responsePacket.length)
                        val ip = responsePacket.address.hostAddress

                        try {
                            val json = JSONObject(message)
                            val name = json.optString("deviceName", null)
                            Log.d(TAG, "Discovered peer: $name ($ip)")
                            if (ip != null) {
                                onPeerFound(name, ip)
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to parse device name JSON: ${e.message}")
                            if (ip != null) {
                                onPeerFound(null, ip)
                            }
                        }
                    } catch (e: SocketTimeoutException) {
                        Log.d(TAG, "Discovery timeout")
                        break
                    }
                }

                socket.close()
            } catch (e: Exception) {
                Log.e(TAG, "Discovery error: ${e.message}", e)
            }
        }.start()
    }

    fun sendInvitation(ip: String, fileName: String, onResult: (Boolean) -> Unit) {
        val client = OkHttpClient()
        val json = JSONObject().apply {
            put("fileName", fileName)
        }

        val request = Request.Builder()
            .url("http://$ip:5050/invite") // you'll implement this endpoint on receiver
            .post(json.toString().toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                onResult(false)
            }

            override fun onResponse(call: Call, response: Response) {
                onResult(response.isSuccessful)
            }
        })
    }
}
