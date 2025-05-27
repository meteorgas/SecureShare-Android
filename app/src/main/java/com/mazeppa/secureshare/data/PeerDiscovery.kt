package com.mazeppa.secureshare.data

import com.mazeppa.secureshare.util.constant.DiscoveryConfig
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException

object PeerDiscovery {

    fun startPeerDiscoveryReceiver(onDiscovered: (InetAddress) -> Unit) {
        Thread {
            try {
                val socket = DatagramSocket(DiscoveryConfig.BROADCAST_PORT, InetAddress.getByName("0.0.0.0"))
                socket.broadcast = true
                val buffer = ByteArray(1024)

                while (true) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    socket.receive(packet)

                    val message = String(packet.data, 0, packet.length)
                    if (message == DiscoveryConfig.BROADCAST_MESSAGE) {
                        val response = DiscoveryConfig.RESPONSE_MESSAGE.toByteArray()
                        val responsePacket = DatagramPacket(
                            response,
                            response.size,
                            packet.address,
                            packet.port
                        )
                        socket.send(responsePacket)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
    }

    fun discoverPeers(onPeerFound: (InetAddress) -> Unit) {
        Thread {
            try {
                val socket = DatagramSocket()
                socket.broadcast = true

                val data = DiscoveryConfig.BROADCAST_MESSAGE.toByteArray()
                val packet = DatagramPacket(
                    data,
                    data.size,
                    InetAddress.getByName("255.255.255.255"),  // Use local subnet broadcast if needed
                    DiscoveryConfig.BROADCAST_PORT
                )
                socket.send(packet)

                val buffer = ByteArray(1024)
                socket.soTimeout = 3000 // wait for responses (3s)

                while (true) {
                    val responsePacket = DatagramPacket(buffer, buffer.size)
                    try {
                        socket.receive(responsePacket)
                        val message = String(responsePacket.data, 0, responsePacket.length)
                        if (message == DiscoveryConfig.RESPONSE_MESSAGE) {
                            onPeerFound(responsePacket.address)
                        }
                    } catch (e: SocketTimeoutException) {
                        break // done waiting
                    }
                }

                socket.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
    }
}