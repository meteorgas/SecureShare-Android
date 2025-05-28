package com.mazeppa.secureshare.util

import android.util.Log
import java.net.Inet4Address
import java.net.NetworkInterface

object Utility {
    fun formatTime(seconds: Double): String {
        val mins = (seconds / 60).toInt()
        val secs = (seconds % 60).toInt()
        return if (mins > 0) "$mins min $secs sec" else "$secs sec"
    }

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
}