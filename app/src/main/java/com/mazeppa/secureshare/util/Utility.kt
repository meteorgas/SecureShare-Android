package com.mazeppa.secureshare.util

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import java.net.Inet4Address
import java.net.NetworkInterface

fun getFileSize(context: Context, uri: Uri): Long {
    val cursor = context.contentResolver.query(uri, null, null, null, null)
    return cursor?.use {
        val sizeIndex = it.getColumnIndex(OpenableColumns.SIZE)
        if (it.moveToFirst()) it.getLong(sizeIndex) else 0L
    } ?: 0L
}

@SuppressLint("DefaultLocale")
fun formatSize(bytes: Long): String {
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    return if (mb >= 1) String.format("%.2f MB", mb) else String.format("%.1f KB", kb)
}

fun getFileName(context: Context, uri: Uri): String {
    val cursor = context.contentResolver.query(uri, null, null, null, null)
    return cursor?.use {
        val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (it.moveToFirst()) it.getString(nameIndex) else "Unknown"
    } ?: "Unknown"
}

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

fun getMimeType(context: Context, uri: Uri): String? {
    return context.contentResolver.getType(uri)
}