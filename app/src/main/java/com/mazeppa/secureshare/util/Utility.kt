package com.mazeppa.secureshare.util

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.URL

object Utility {
    fun getLocalIpAddress(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            for (intf in interfaces) {
                val addrs = intf.inetAddresses
                for (addr in addrs) {
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        return addr.hostAddress
                    }
                }
            }
        } catch (ex: Exception) {
            Log.e("Network", "Failed to get local IP: ${ex.message}")
        }
        return null
    }

    suspend fun getPublicIpAddress(): String = withContext(Dispatchers.IO) {
        return@withContext try {
            val connection = URL("https://api.ipify.org").openConnection() as HttpURLConnection
            connection.connectTimeout = 3000
            connection.readTimeout = 3000
            connection.inputStream.bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            "Unavailable"
        }
    }
}