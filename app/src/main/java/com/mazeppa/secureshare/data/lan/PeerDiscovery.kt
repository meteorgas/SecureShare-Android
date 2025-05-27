package com.mazeppa.secureshare.data.lan

import android.os.Build
import android.util.Log
import com.mazeppa.secureshare.util.constant.DiscoveryConfig
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException

object PeerDiscovery {

    private const val TAG = "PeerDiscovery"

    fun startPeerDiscoveryReceiver() {
        Thread {
            try {
                val socket = DatagramSocket(DiscoveryConfig.BROADCAST_PORT, InetAddress.getByName("0.0.0.0"))
                socket.broadcast = true
                val buffer = ByteArray(1024)
                Log.d(TAG, "Peer discovery receiver started on port ${DiscoveryConfig.BROADCAST_PORT}")

                while (true) {
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
                            response,
                            response.size,
                            packet.address,
                            packet.port
                        )
                        socket.send(responsePacket)
                        Log.d(TAG, "Sent response to ${packet.address.hostAddress}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Receiver error: ${e.message}", e)
            }
        }.start()
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
}
