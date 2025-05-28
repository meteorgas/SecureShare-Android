package com.mazeppa.secureshare.util

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.mazeppa.secureshare.data.SelectedFile

object FileManager {

    fun mapUrisToFiles(context: Context, uris: List<Uri>): List<SelectedFile> {
        return uris.map {
            SelectedFile(
                name = getFileName(context, it),
                size = formatSize(getFileSize(context, it)),
                uri = it
            )
        }
    }

    fun removeUri(context: Context, uri: Uri, list: MutableList<Uri>): List<SelectedFile> {
        list.remove(uri)
        return mapUrisToFiles(context, list)
    }

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

    fun getMimeType(context: Context, uri: Uri): String? {
        return context.contentResolver.getType(uri)
    }
}